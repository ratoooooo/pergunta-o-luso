package com.ratoooooo.perguntaoluso.data

import com.google.firebase.database.DataSnapshot
import com.google.firebase.database.DatabaseError
import com.google.firebase.database.FirebaseDatabase
import com.google.firebase.database.MutableData
import com.google.firebase.database.ServerValue
import com.google.firebase.database.Transaction
import com.google.firebase.database.ValueEventListener
import com.ratoooooo.perguntaoluso.game.multi.MatchFormat
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

data class OpenLobby(
    val id: String = "",
    val codigo: String? = null,
    val hostUid: String = "",
    val hostNome: String = "",
    val format: String = "",
    val categoria: String = "",
    val modo: String = "",
    val membrosCount: Int = 0,
    val estado: String = "waiting"
)

data class LobbyData(
    val lobbyId: String = "",
    val hostUid: String = "",
    val hostNome: String = "",
    val format: MatchFormat = MatchFormat.GRUPO,
    val categoria: String = "",
    val modo: String = "classico",
    val estado: String = "waiting",
    val salaId: String? = null,
    val membros: List<Pair<String, String>> = emptyList()
)

/** Tecto de perguntas numa sala privada, imposto pelos limites de pontuação nas rules. */
const val MAX_PERGUNTAS_SALA = 10

class MultiMatchRepository {

    private val db get() = FirebaseDatabase.getInstance()
    private fun room(salaId: String) = db.getReference("multisalas").child(salaId)
    private fun pending(queueKey: String) = db.getReference("matchmakingN").child(queueKey).child("pending")
    private fun notify(queueKey: String) = db.getReference("matchmakingN").child(queueKey).child("notify")
    private fun lobbiesRef(formatId: String) = db.getReference("lobbies").child(formatId)

    suspend fun serverTimeOffset(): Long {
        val snap = db.getReference(".info/serverTimeOffset").get().await()
        return snap.getValue(Long::class.java) ?: 0L
    }

    suspend fun findOrCreateLobby(
        format: MatchFormat,
        categoria: String,
        modo: String,
        uid: String,
        nome: String
    ): Pair<String, Boolean> = suspendCancellableCoroutine { cont ->
        val ref = lobbiesRef(format.id)
        ref.runTransaction(object : Transaction.Handler {
            var assignedLobbyId: String = ""
            var isHost: Boolean = false

            override fun doTransaction(data: MutableData): Transaction.Result {
                val now = System.currentTimeMillis()
                var openLobbyChild: MutableData? = null
                for (child in data.children) {
                    val estado = child.child("estado").getValue(String::class.java) ?: "waiting"
                    val cat = child.child("categoria").getValue(String::class.java) ?: ""
                    val mode = child.child("modo").getValue(String::class.java) ?: "classico"
                    val membrosCount = child.child("membros").childrenCount.toInt()
                    if (estado == "waiting" && membrosCount < format.players && cat == categoria && mode == modo) {
                        openLobbyChild = child
                        break
                    }
                }

                if (openLobbyChild != null) {
                    assignedLobbyId = openLobbyChild.key ?: ""
                    isHost = openLobbyChild.child("hostUid").getValue(String::class.java) == uid
                    openLobbyChild.child("membros").child(uid).child("nome").value = nome
                    openLobbyChild.child("membros").child(uid).child("ts").value = now
                } else {
                    val newKey = ref.push().key ?: "lobby_${now}"
                    assignedLobbyId = newKey
                    isHost = true
                    val newLobby = data.child(newKey)
                    newLobby.child("hostUid").value = uid
                    newLobby.child("hostNome").value = nome
                    newLobby.child("format").value = format.id
                    newLobby.child("categoria").value = categoria
                    newLobby.child("modo").value = modo
                    newLobby.child("estado").value = "waiting"
                    newLobby.child("criadoEm").value = now
                    newLobby.child("membros").child(uid).child("nome").value = nome
                    newLobby.child("membros").child(uid).child("ts").value = now
                }
                return Transaction.success(data)
            }

            override fun onComplete(error: DatabaseError?, committed: Boolean, snapshot: DataSnapshot?) {
                if (error != null) cont.resumeWithException(error.toException())
                else cont.resume(assignedLobbyId to isHost)
            }
        })
    }

    fun observeOpenLobbies(formatId: String): Flow<List<LobbyData>> = callbackFlow {
        val ref = lobbiesRef(formatId)
        val listener = object : ValueEventListener {
            override fun onDataChange(snapshot: DataSnapshot) {
                val list = snapshot.children.mapNotNull { child ->
                    val estado = child.child("estado").getValue(String::class.java) ?: "waiting"
                    if (estado != "waiting") return@mapNotNull null
                    val lobbyId = child.key ?: return@mapNotNull null
                    val hostUid = child.child("hostUid").getValue(String::class.java) ?: ""
                    val hostNome = child.child("hostNome").getValue(String::class.java) ?: ""
                    val cat = child.child("categoria").getValue(String::class.java) ?: ""
                    val mode = child.child("modo").getValue(String::class.java) ?: "classico"
                    val salaId = child.child("salaId").getValue(String::class.java)
                    val membros = child.child("membros").children.mapNotNull { c ->
                        val uid = c.key ?: return@mapNotNull null
                        val name = c.child("nome").getValue(String::class.java) ?: "Jogador"
                        val ts = c.child("ts").getValue(Long::class.java) ?: 0L
                        Triple(uid, name, ts)
                    }.sortedBy { it.third }.map { it.first to it.second }

                    LobbyData(
                        lobbyId = lobbyId,
                        hostUid = hostUid,
                        hostNome = hostNome,
                        format = MatchFormat.fromId(formatId),
                        categoria = cat,
                        modo = mode,
                        estado = estado,
                        salaId = salaId,
                        membros = membros
                    )
                }
                trySend(list)
            }
            override fun onCancelled(error: DatabaseError) { close(error.toException()) }
        }
        ref.addValueEventListener(listener)
        awaitClose { ref.removeEventListener(listener) }
    }

    suspend fun joinLobbyById(formatId: String, lobbyId: String, uid: String, nome: String): Boolean =
        suspendCancellableCoroutine { cont ->
            val ref = lobbiesRef(formatId).child(lobbyId)
            ref.runTransaction(object : Transaction.Handler {
                var success = false
                override fun doTransaction(data: MutableData): Transaction.Result {
                    val estado = data.child("estado").getValue(String::class.java) ?: "waiting"
                    val fmtId = data.child("format").getValue(String::class.java) ?: formatId
                    val format = MatchFormat.fromId(fmtId)
                    val count = data.child("membros").childrenCount.toInt()
                    if (estado == "waiting" && count < format.players) {
                        val now = System.currentTimeMillis()
                        data.child("membros").child(uid).child("nome").value = nome
                        data.child("membros").child(uid).child("ts").value = now
                        success = true
                    }
                    return Transaction.success(data)
                }

                override fun onComplete(error: DatabaseError?, committed: Boolean, snapshot: DataSnapshot?) {
                    if (error != null) cont.resumeWithException(error.toException())
                    else cont.resume(success)
                }
            })
        }

    fun observeLobby(formatId: String, lobbyId: String): Flow<LobbyData?> = callbackFlow {
        val ref = lobbiesRef(formatId).child(lobbyId)
        val listener = object : ValueEventListener {
            override fun onDataChange(snapshot: DataSnapshot) {
                if (!snapshot.exists()) {
                    trySend(null)
                    return
                }
                val hostUid = snapshot.child("hostUid").getValue(String::class.java) ?: ""
                val hostNome = snapshot.child("hostNome").getValue(String::class.java) ?: ""
                val cat = snapshot.child("categoria").getValue(String::class.java) ?: ""
                val mode = snapshot.child("modo").getValue(String::class.java) ?: "classico"
                val estado = snapshot.child("estado").getValue(String::class.java) ?: "waiting"
                val salaId = snapshot.child("salaId").getValue(String::class.java)
                val membros = snapshot.child("membros").children.mapNotNull { c ->
                    val uid = c.key ?: return@mapNotNull null
                    val name = c.child("nome").getValue(String::class.java) ?: "Jogador"
                    val ts = c.child("ts").getValue(Long::class.java) ?: 0L
                    Triple(uid, name, ts)
                }.sortedBy { it.third }.map { it.first to it.second }

                trySend(
                    LobbyData(
                        lobbyId = lobbyId,
                        hostUid = hostUid,
                        hostNome = hostNome,
                        format = MatchFormat.fromId(formatId),
                        categoria = cat,
                        modo = mode,
                        estado = estado,
                        salaId = salaId,
                        membros = membros
                    )
                )
            }
            override fun onCancelled(error: DatabaseError) { close(error.toException()) }
        }
        ref.addValueEventListener(listener)
        awaitClose { ref.removeEventListener(listener) }
    }

    suspend fun leaveLobby(formatId: String, lobbyId: String, uid: String) {
        val ref = lobbiesRef(formatId).child(lobbyId)
        suspendCancellableCoroutine<Unit> { cont ->
            ref.runTransaction(object : Transaction.Handler {
                override fun doTransaction(data: MutableData): Transaction.Result {
                    data.child("membros").child(uid).value = null
                    val remaining = data.child("membros").children.mapNotNull { c ->
                        val mUid = c.key ?: return@mapNotNull null
                        val mNome = c.child("nome").getValue(String::class.java) ?: "Jogador"
                        val mTs = c.child("ts").getValue(Long::class.java) ?: 0L
                        Triple(mUid, mNome, mTs)
                    }.sortedBy { it.third }

                    if (remaining.isEmpty()) {
                        data.value = null
                    } else {
                        val hostUid = data.child("hostUid").getValue(String::class.java)
                        if (uid == hostUid) {
                            val newHost = remaining.first()
                            data.child("hostUid").value = newHost.first
                            data.child("hostNome").value = newHost.second
                        }
                    }
                    return Transaction.success(data)
                }
                override fun onComplete(e: DatabaseError?, c: Boolean, s: DataSnapshot?) { cont.resume(Unit) }
            })
        }
    }

    suspend fun startLobbyRoom(
        formatId: String,
        lobbyId: String,
        hostUid: String,
        membros: List<Pair<String, String>>,
        questions: List<Question>,
        categoria: String,
        modo: String
    ): String {
        val salaId = createRoomDirect(MatchFormat.fromId(formatId), hostUid, membros, questions, categoria, modo)
        val ref = lobbiesRef(formatId).child(lobbyId)
        ref.child("salaId").setValue(salaId).await()
        ref.child("estado").setValue("started").await()
        return salaId
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

    suspend fun createPrivateRoomWithCode(
        format: MatchFormat,
        hostUid: String,
        hostNome: String,
        categoria: String,
        modo: String,
        questions: List<Question>
    ): Pair<String, String> {
        val lobbyRef = db.getReference("lobbies").child(format.id).push()
        val lobbyId = lobbyRef.key!!
        val now = System.currentTimeMillis()

        // 1) O lobby primeiro. As rules de `/salas_privadas` exigem que quem regista o código
        //    seja o anfitrião do lobby referido, por isso o lobby tem de já existir em `root`.
        lobbyRef.setValue(
            mapOf(
                "id" to lobbyId,
                "hostUid" to hostUid,
                "hostNome" to hostNome,
                "format" to format.id,
                "categoria" to categoria,
                "modo" to modo,
                "estado" to "waiting",
                "criadoEm" to now,
                "membros" to mapOf(hostUid to mapOf("nome" to hostNome, "ts" to now))
            )
        ).await()

        // 2) Reserva do código, com retentativa. A entrada é create-once nas rules, por isso uma
        //    colisão (só há 9000 códigos de 4 dígitos) falha em vez de repontar silenciosamente
        //    o código de outra sala — que era o que aconteceria sem a regra.
        val code = reserveRoomCode(lobbyId, format.id)
        lobbyRef.child("codigo").setValue(code).await()

        // 3) A sala em si. `membrosNomes` começa só com o anfitrião; quem entrar pelo código
        //    acrescenta-se a si próprio (ver `joinPrivateRoomWithCode`).
        db.getReference("multisalas").child(lobbyId).child("meta").setValue(
            mapOf(
                "hostUid" to hostUid,
                "format" to format.id,
                "categoria" to categoria,
                "modo" to modo,
                "criadoEm" to now,
                "membros" to listOf(hostUid),
                "membrosNomes" to mapOf(hostUid to hostNome),
                // Limitado a MAX_PERGUNTAS_SALA: as rules travam `respostasCertas <= 10` e
                // `total <= 20` nas pontuações, por isso um quiz maior fazia a gravação do
                // resultado ser recusada no fim da partida — depois de já se ter jogado.
                "perguntas" to questions.take(MAX_PERGUNTAS_SALA).map { it.toMap() }
            )
        ).await()

        return lobbyId to code
    }

    /** Tenta códigos de 4 dígitos até um vingar. Lança se não conseguir em [tentativas]. */
    private suspend fun reserveRoomCode(lobbyId: String, formatId: String, tentativas: Int = 12): String {
        val payload = mapOf("lobbyId" to lobbyId, "format" to formatId)
        repeat(tentativas) {
            val code = (1000..9999).random().toString()
            val ok = runCatching {
                db.getReference("salas_privadas").child(code).setValue(payload).await()
            }.isSuccess
            if (ok) return code
        }
        error("Não foi possível gerar um código de sala livre")
    }

    suspend fun joinPrivateRoomWithCode(code: String, uid: String, nome: String): Pair<String, String>? {
        val snap = db.getReference("salas_privadas").child(code.trim()).get().await()
        val lobbyId = snap.child("lobbyId").getValue(String::class.java) ?: return null
        val formatId = snap.child("format").getValue(String::class.java) ?: "grupo"

        val lobbyRef = db.getReference("lobbies").child(formatId).child(lobbyId)
        if (!lobbyRef.get().await().exists()) return null

        // Entrar no lobby primeiro: é a pertença ao lobby que as rules exigem para deixar
        // alguém inscrever-se em `meta/membrosNomes`.
        lobbyRef.child("membros").child(uid)
            .setValue(mapOf("nome" to nome, "ts" to System.currentTimeMillis())).await()

        // E só então na sala. Sem este passo o jogador entrava no lobby mas não conseguia LER a
        // multisala (a regra de leitura exige constar de `membrosNomes`), e o jogo ficava preso.
        db.getReference("multisalas").child(lobbyId)
            .child("meta").child("membrosNomes").child(uid).setValue(nome).await()

        return formatId to lobbyId
    }
}
