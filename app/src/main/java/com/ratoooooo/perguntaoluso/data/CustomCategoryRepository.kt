package com.ratoooooo.perguntaoluso.data

import com.google.firebase.database.DataSnapshot
import com.google.firebase.database.DatabaseError
import com.google.firebase.database.FirebaseDatabase
import com.google.firebase.database.MutableData
import com.google.firebase.database.Transaction
import com.google.firebase.database.ValueEventListener
import kotlinx.coroutines.channels.awaitClose
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.callbackFlow
import kotlinx.coroutines.suspendCancellableCoroutine
import kotlinx.coroutines.tasks.await
import kotlin.coroutines.resume
import kotlin.coroutines.resumeWithException

class CustomCategoryRepository {

    private val db get() = FirebaseDatabase.getInstance()
    private val ref get() = db.getReference("categorias_comunitarias")

    fun observePublicCategories(): Flow<List<CustomCategory>> = callbackFlow {
        val listener = object : ValueEventListener {
            override fun onDataChange(snapshot: DataSnapshot) {
                val list = snapshot.children.mapNotNull { c ->
                    parseCategory(c)
                }.filter { it.publica && it.perguntas.isNotEmpty() }
                    .sortedByDescending { it.mediaClassificacao }
                trySend(list)
            }

            override fun onCancelled(error: DatabaseError) { close(error.toException()) }
        }
        ref.addValueEventListener(listener)
        awaitClose { ref.removeEventListener(listener) }
    }

    fun observeMyCategories(uid: String): Flow<List<CustomCategory>> = callbackFlow {
        val listener = object : ValueEventListener {
            override fun onDataChange(snapshot: DataSnapshot) {
                val list = snapshot.children.mapNotNull { c ->
                    parseCategory(c)
                }.filter { it.criadorUid == uid }
                    .sortedByDescending { it.criadoEm }
                trySend(list)
            }

            override fun onCancelled(error: DatabaseError) { close(error.toException()) }
        }
        ref.addValueEventListener(listener)
        awaitClose { ref.removeEventListener(listener) }
    }

    /**
     * @throws ConteudoImproprioException se o [ProfanityFilter] recusar algum dos textos. A
     * verificação é feita aqui, e não só no ecrã, para que nenhum caminho de gravação a
     * consiga contornar por engano.
     */
    suspend fun saveCategory(
        id: String?,
        titulo: String,
        descricao: String,
        criadorUid: String,
        criadorNome: String,
        publica: Boolean,
        perguntas: List<Question>
    ): String {
        ProfanityFilter.primeiraPalavraBloqueadaNoQuiz(titulo, descricao, perguntas)
            ?.let { throw ConteudoImproprioException(it) }

        val nodeRef = if (id.isNullOrBlank()) ref.push() else ref.child(id)
        val catId = nodeRef.key!!
        val now = System.currentTimeMillis()

        val map = mapOf(
            "id" to catId,
            "titulo" to titulo,
            "descricao" to descricao,
            "criadorUid" to criadorUid,
            "criadorNome" to criadorNome,
            "publica" to publica,
            "criadoEm" to now,
            "perguntas" to perguntas.map { it.toMap() }
        )
        nodeRef.updateChildren(map).await()
        return catId
    }

    suspend fun togglePublicStatus(catId: String, currentPublic: Boolean) {
        ref.child(catId).child("publica").setValue(!currentPublic).await()
    }

    /**
     * Writes this player's vote, then recomputes the aggregates inside a transaction (Fase 22).
     *
     * The previous version read the votes, averaged them in memory and wrote
     * `mediaClassificacao` and `totalVotos` in two separate `setValue` calls: two people voting
     * at the same time lost one of the votes, and the average could end up disagreeing with the
     * vote list. The transaction derives both aggregates from the `votos` in the snapshot it is
     * committing against, so a conflicting write just retries against fresh data.
     */
    suspend fun rateCategory(catId: String, uid: String, rating: Int) {
        // The vote goes in inside a transaction on the whole `votos` map, so the snapshot the
        // aggregates are computed from is the same one the vote committed against.
        val votes = suspendCancellableCoroutine<List<Int>> { cont ->
            ref.child(catId).child("votos").runTransaction(object : Transaction.Handler {
                override fun doTransaction(data: MutableData): Transaction.Result {
                    data.child(uid).value = rating.toLong()
                    return Transaction.success(data)
                }

                override fun onComplete(error: DatabaseError?, committed: Boolean, snapshot: DataSnapshot?) {
                    when {
                        error != null -> cont.resumeWithException(error.toException())
                        else -> cont.resume(
                            snapshot?.children?.mapNotNull { it.getValue(Int::class.java) } ?: emptyList()
                        )
                    }
                }
            })
        }
        if (votes.isEmpty()) return

        // Multi-path update: RTDB validates each leaf independently, so this needs write
        // permission on the two aggregate fields only — not on the quiz node, which stays
        // reserved for its owner.
        ref.child(catId).updateChildren(
            mapOf("mediaClassificacao" to votes.average(), "totalVotos" to votes.size)
        ).await()
    }

    // ---- Denúncias (moderação mínima viável — ver Fase 25 no GAME_DESIGN.md) ----

    /** Já denunciei este quiz? Só o próprio consegue ler a sua denúncia; ninguém vê as dos outros. */
    suspend fun jaDenunciei(catId: String, uid: String): Boolean =
        db.getReference("denuncias").child(catId).child(uid).get().await().exists()

    /**
     * Regista a denúncia e actualiza o contador. Uma denúncia por pessoa por quiz — as rules
     * impedem reescrever a própria (`!data.exists()`), por isso não há como inflacionar sozinho.
     *
     * O contador é incrementado por transacção (mesma disciplina do `rateCategory`): duas
     * denúncias em simultâneo não se perdem uma à outra. Ao atingir [DENUNCIAS_PARA_OCULTAR] o
     * quiz é despublicado — **não apagado**: o autor continua a vê-lo em "As Minhas" e o nó fica
     * na base de dados para revisão manual na consola.
     *
     * @return o total de denúncias depois desta.
     */
    suspend fun denunciar(catId: String, uid: String, motivo: String): Int {
        db.getReference("denuncias").child(catId).child(uid)
            .setValue(mapOf("motivo" to motivo.take(200), "ts" to System.currentTimeMillis()))
            .await()

        val total = suspendCancellableCoroutine<Int> { cont ->
            ref.child(catId).child("totalDenuncias").runTransaction(object : Transaction.Handler {
                override fun doTransaction(data: MutableData): Transaction.Result {
                    data.value = ((data.value as? Number)?.toLong() ?: 0L) + 1L
                    return Transaction.success(data)
                }

                override fun onComplete(error: DatabaseError?, committed: Boolean, snapshot: DataSnapshot?) {
                    if (error != null) cont.resumeWithException(error.toException())
                    else cont.resume(snapshot?.getValue(Int::class.java) ?: 0)
                }
            })
        }

        if (total >= DENUNCIAS_PARA_OCULTAR) {
            runCatching { ref.child(catId).child("publica").setValue(false).await() }
        }
        return total
    }

    private fun parseCategory(c: DataSnapshot): CustomCategory? {
        val id = c.child("id").getValue(String::class.java) ?: c.key ?: return null
        val titulo = c.child("titulo").getValue(String::class.java) ?: return null
        val desc = c.child("descricao").getValue(String::class.java) ?: ""
        val uid = c.child("criadorUid").getValue(String::class.java) ?: ""
        val nome = c.child("criadorNome").getValue(String::class.java) ?: "Anónimo"
        val publica = c.child("publica").getValue(Boolean::class.java) ?: true
        val criadoEm = c.child("criadoEm").getValue(Long::class.java) ?: 0L
        val media = c.child("mediaClassificacao").getValue(Double::class.java) ?: 0.0
        val totalVotos = c.child("totalVotos").getValue(Int::class.java) ?: 0
        val totalDenuncias = c.child("totalDenuncias").getValue(Int::class.java) ?: 0

        val preguntasSnap = c.child("perguntas")
        val questionsList = preguntasSnap.children.mapNotNull { qSnap ->
            val pText = qSnap.child("pergunta").getValue(String::class.java) ?: return@mapNotNull null
            val respCorr = qSnap.child("respostaCorreta").getValue(String::class.java) ?: return@mapNotNull null
            val dif = qSnap.child("dificuldade").getValue(String::class.java) ?: "medio"
            val opcs = qSnap.child("opcoes").children.mapNotNull { it.getValue(String::class.java) }
            if (opcs.size < 2) return@mapNotNull null
            Question(pergunta = pText, opcoes = opcs, respostaCorreta = respCorr, dificuldade = dif)
        }

        return CustomCategory(
            id = id,
            titulo = titulo,
            descricao = desc,
            criadorUid = uid,
            criadorNome = nome,
            publica = publica,
            criadoEm = criadoEm,
            mediaClassificacao = media,
            totalVotos = totalVotos,
            totalDenuncias = totalDenuncias,
            perguntas = questionsList
        )
    }
}
