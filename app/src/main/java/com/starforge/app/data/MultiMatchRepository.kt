package com.starforge.app.data

import com.google.firebase.database.DataSnapshot
import com.google.firebase.database.DatabaseError
import com.google.firebase.database.FirebaseDatabase
import com.google.firebase.database.MutableData
import com.google.firebase.database.ServerValue
import com.google.firebase.database.Transaction
import com.google.firebase.database.ValueEventListener
import com.starforge.app.game.multi.MatchFormat
import kotlinx.coroutines.channels.awaitClose
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.callbackFlow
import kotlinx.coroutines.suspendCancellableCoroutine
import kotlinx.coroutines.tasks.await
import kotlin.coroutines.resume
import kotlin.coroutines.resumeWithException

// ---- Models ----

data class MultiPlayer(
    val uid: String = "",
    val nome: String = "",
    val pontuacao: Int = 0,
    val respostasCertas: Int = 0,
    val estado: String = "on",       // on | off | terminado
    val desistiu: Boolean = false,
    val answered: Set<Int> = emptySet()
)

data class MultiRoom(
    val salaId: String,
    val hostUid: String,
    val format: MatchFormat,
    val categoria: String,
    val modo: String,
    val membros: List<String>,
    val membrosNomes: Map<String, String>,
    val equipas: Map<String, List<String>>,   // "A"/"B" -> uids (2x2 only)
    val perguntas: List<Question>,
    val jogadores: Map<String, MultiPlayer>,
    val perguntaInicios: Map<Int, Long>,
    val pontuacoes: Map<String, Int>
)

sealed class FormResult {
    object Waiting : FormResult()
    /** This client became host of a match with these members (uid to nome), including itself. */
    data class Host(val membros: List<Pair<String, String>>) : FormResult()
}

class MultiMatchRepository {

    private val db get() = FirebaseDatabase.getInstance()
    private fun room(salaId: String) = db.getReference("multisalas").child(salaId)
    private fun pending(queueKey: String) = db.getReference("matchmakingN").child(queueKey).child("pending")
    private fun notify(queueKey: String) = db.getReference("matchmakingN").child(queueKey).child("notify")

    suspend fun serverTimeOffset(): Long {
        val snap = db.getReference(".info/serverTimeOffset").get().await()
        return snap.getValue(Long::class.java) ?: 0L
    }

    /**
     * Generalized version of the 1x1 single-slot pairing: an atomic transaction on a
     * `pending` list that accumulates waiting players and, once it reaches [format].players,
     * atomically claims the first N as a match (this client becomes host).
     */
    suspend fun joinQueue(format: MatchFormat, queueKey: String, uid: String, nome: String): FormResult =
        suspendCancellableCoroutine { cont ->
            val now = System.currentTimeMillis()
            var result: FormResult = FormResult.Waiting
            pending(queueKey).runTransaction(object : Transaction.Handler {
                override fun doTransaction(data: MutableData): Transaction.Result {
                    val entries = data.children.map { c ->
                        Triple(
                            c.key ?: "",
                            c.child("nome").getValue(String::class.java) ?: "",
                            c.child("ts").getValue(Long::class.java) ?: 0L
                        )
                    }.toMutableList()
                    entries.removeAll { it.first == uid }
                    entries.add(Triple(uid, nome, now))
                    entries.sortBy { it.third }

                    if (entries.size >= format.players) {
                        val chosen = entries.take(format.players)
                        val remaining = entries.drop(format.players)
                        data.value = if (remaining.isEmpty()) null
                        else remaining.associate { it.first to mapOf("nome" to it.second, "ts" to it.third) }
                        result = if (chosen.any { it.first == uid }) {
                            FormResult.Host(chosen.map { it.first to it.second })
                        } else {
                            FormResult.Waiting
                        }
                    } else {
                        data.value = entries.associate { it.first to mapOf("nome" to it.second, "ts" to it.third) }
                        result = FormResult.Waiting
                    }
                    return Transaction.success(data)
                }

                override fun onComplete(error: DatabaseError?, committed: Boolean, snapshot: DataSnapshot?) {
                    if (error != null) cont.resumeWithException(error.toException())
                    else if (!committed) cont.resumeWithException(IllegalStateException("Matchmaking ocupado, tenta de novo"))
                    else cont.resume(result)
                }
            })
        }

    suspend fun cancelQueue(queueKey: String, uid: String) {
        suspendCancellableCoroutine<Unit> { cont ->
            pending(queueKey).runTransaction(object : Transaction.Handler {
                override fun doTransaction(data: MutableData): Transaction.Result {
                    data.child(uid).value = null
                    return Transaction.success(data)
                }
                override fun onComplete(e: DatabaseError?, c: Boolean, s: DataSnapshot?) { cont.resume(Unit) }
            })
        }
        notify(queueKey).child(uid).removeValue().await()
    }

    fun listenForMatch(queueKey: String, uid: String): Flow<String> = callbackFlow {
        val ref = notify(queueKey).child(uid)
        val listener = object : ValueEventListener {
            override fun onDataChange(snapshot: DataSnapshot) {
                snapshot.getValue(String::class.java)?.let { trySend(it) }
            }
            override fun onCancelled(error: DatabaseError) { close(error.toException()) }
        }
        ref.addValueEventListener(listener)
        awaitClose { ref.removeEventListener(listener) }
    }

    suspend fun clearNotify(queueKey: String, uid: String) {
        notify(queueKey).child(uid).removeValue().await()
    }

    /**
     * Host writes immutable match data under `meta` (create-once) + its own player entry, then
     * notifies the other queued players. Used by random matchmaking.
     */
    suspend fun createRoom(
        format: MatchFormat,
        queueKey: String,
        hostUid: String,
        membros: List<Pair<String, String>>,
        questions: List<Question>,
        categoria: String,
        modo: String
    ): String {
        val salaId = createRoomDirect(format, hostUid, membros, questions, categoria, modo)
        for ((uid, _) in membros) {
            if (uid != hostUid) notify(queueKey).child(uid).setValue(salaId).await()
        }
        return salaId
    }

    /**
     * Creates a room without touching the matchmaking queue — used by **direct friend
     * challenges**, where the invited player is told the `salaId` through `/convites` instead.
     */
    suspend fun createRoomDirect(
        format: MatchFormat,
        hostUid: String,
        membros: List<Pair<String, String>>,
        questions: List<Question>,
        categoria: String,
        modo: String
    ): String {
        val ref = db.getReference("multisalas").push()
        val salaId = ref.key!!
        val uids = membros.map { it.first }
        val nomes = membros.associate { it.first to it.second }
        val meta = mutableMapOf<String, Any>(
            "hostUid" to hostUid,
            "format" to format.id,
            "categoria" to categoria,
            "modo" to modo,
            "criadoEm" to ServerValue.TIMESTAMP,
            "membros" to uids,
            "membrosNomes" to nomes,
            "perguntas" to questions.map { it.toMap() }
        )
        if (format.teamBased) {
            meta["equipas"] = mapOf("A" to uids.take(2), "B" to uids.drop(2).take(2))
        }
        ref.child("meta").setValue(meta).await()
        val hostNome = nomes[hostUid] ?: "Jogador"
        ref.child("jogadores").child(hostUid).setValue(playerMap(hostNome)).await()
        return salaId
    }

    suspend fun joinRoom(salaId: String, uid: String, nome: String) {
        room(salaId).child("jogadores").child(uid).setValue(playerMap(nome)).await()
    }

    fun setupDisconnect(salaId: String, uid: String) {
        room(salaId).child("jogadores").child(uid).child("estado").onDisconnect().setValue("off")
    }

    fun cancelDisconnect(salaId: String, uid: String) {
        room(salaId).child("jogadores").child(uid).child("estado").onDisconnect().cancel()
    }

    fun observeRoom(salaId: String): Flow<MultiRoom> = callbackFlow {
        val ref = room(salaId)
        val listener = object : ValueEventListener {
            override fun onDataChange(snapshot: DataSnapshot) {
                if (snapshot.exists()) trySend(snapshot.toRoom(salaId))
            }
            override fun onCancelled(error: DatabaseError) { close(error.toException()) }
        }
        ref.addValueEventListener(listener)
        awaitClose { ref.removeEventListener(listener) }
    }

    suspend fun syncQuestionStart(salaId: String, index: Int): Long =
        suspendCancellableCoroutine { cont ->
            val ref = room(salaId).child("perguntaInicios").child(index.toString())
            ref.runTransaction(object : Transaction.Handler {
                override fun doTransaction(data: MutableData): Transaction.Result {
                    if (data.value == null) data.value = ServerValue.TIMESTAMP
                    return Transaction.success(data)
                }
                override fun onComplete(error: DatabaseError?, committed: Boolean, snapshot: DataSnapshot?) {
                    if (error != null) cont.resumeWithException(error.toException())
                    else cont.resume(snapshot?.getValue(Long::class.java) ?: System.currentTimeMillis())
                }
            })
        }

    suspend fun writeAnswer(salaId: String, uid: String, index: Int, correct: Boolean, pontuacao: Int, respostasCertas: Int) {
        room(salaId).child("jogadores").child(uid).updateChildren(
            mapOf("pontuacao" to pontuacao, "respostasCertas" to respostasCertas, "answered/$index" to correct)
        ).await()
    }

    suspend fun writeFinal(salaId: String, uid: String, pontuacao: Int) {
        room(salaId).child("jogadores").child(uid).child("estado").setValue("terminado").await()
        room(salaId).child("pontuacoes").child(uid).setValue(pontuacao).await()
    }

    suspend fun leaveRoom(salaId: String, uid: String) {
        room(salaId).child("jogadores").child(uid).updateChildren(
            mapOf("estado" to "off", "desistiu" to true)
        ).await()
    }

    // ---- Mapping ----

    private fun playerMap(nome: String) = mapOf(
        "nome" to nome, "pontuacao" to 0, "respostasCertas" to 0, "estado" to "on"
    )

    private fun Question.toMap() = mapOf(
        "pergunta" to pergunta,
        "opcoes" to opcoes,
        "respostaCorreta" to respostaCorreta,
        "dificuldade" to dificuldade
    )

    private fun DataSnapshot.toRoom(salaId: String): MultiRoom {
        val meta = child("meta")
        val perguntas = meta.child("perguntas").children.mapNotNull { q ->
            val pergunta = q.child("pergunta").getValue(String::class.java) ?: return@mapNotNull null
            val resposta = q.child("respostaCorreta").getValue(String::class.java) ?: return@mapNotNull null
            val opcoes = q.child("opcoes").children.mapNotNull { it.getValue(String::class.java) }
            Question(
                pergunta = pergunta,
                opcoes = opcoes,
                respostaCorreta = resposta,
                dificuldade = q.child("dificuldade").getValue(String::class.java) ?: ""
            )
        }
        val membros = meta.child("membros").children.mapNotNull { it.getValue(String::class.java) }
        val membrosNomes = meta.child("membrosNomes").children.associate { (it.key ?: "") to (it.getValue(String::class.java) ?: "") }
        val equipas = meta.child("equipas").children.associate { team ->
            (team.key ?: "") to team.children.mapNotNull { it.getValue(String::class.java) }
        }
        val jogadores = child("jogadores").children.associate { j ->
            val uid = j.key ?: ""
            uid to MultiPlayer(
                uid = uid,
                nome = j.child("nome").getValue(String::class.java) ?: "",
                pontuacao = j.child("pontuacao").getValue(Int::class.java) ?: 0,
                respostasCertas = j.child("respostasCertas").getValue(Int::class.java) ?: 0,
                estado = j.child("estado").getValue(String::class.java) ?: "on",
                desistiu = j.child("desistiu").getValue(Boolean::class.java) ?: false,
                answered = j.child("answered").children.mapNotNull { it.key?.toIntOrNull() }.toSet()
            )
        }
        val inicios = child("perguntaInicios").children.mapNotNull { i ->
            val idx = i.key?.toIntOrNull() ?: return@mapNotNull null
            idx to (i.getValue(Long::class.java) ?: return@mapNotNull null)
        }.toMap()
        val pontuacoes = child("pontuacoes").children.associate { (it.key ?: "") to (it.getValue(Int::class.java) ?: 0) }
        return MultiRoom(
            salaId = salaId,
            hostUid = meta.child("hostUid").getValue(String::class.java) ?: "",
            format = MatchFormat.fromId(meta.child("format").getValue(String::class.java)),
            categoria = meta.child("categoria").getValue(String::class.java) ?: "",
            modo = meta.child("modo").getValue(String::class.java) ?: "classico",
            membros = membros,
            membrosNomes = membrosNomes,
            equipas = equipas,
            perguntas = perguntas,
            jogadores = jogadores,
            perguntaInicios = inicios,
            pontuacoes = pontuacoes
        )
    }
}
