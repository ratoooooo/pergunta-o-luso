package com.starforge.app.data

import com.google.firebase.database.DataSnapshot
import com.google.firebase.database.DatabaseError
import com.google.firebase.database.FirebaseDatabase
import com.google.firebase.database.ServerValue
import com.google.firebase.database.ValueEventListener
import kotlinx.coroutines.channels.awaitClose
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.callbackFlow
import kotlinx.coroutines.tasks.await

/** How long a direct challenge stays open before both sides drop it. */
const val CONVITE_TTL_MS = 45_000L

const val CONVITE_PENDENTE = "pendente"
const val CONVITE_ACEITE = "aceite"
const val CONVITE_RECUSADO = "recusado"

/** One direct challenge. [salaId] is the room the challenger already created for both players. */
data class Convite(
    val outroUid: String = "",
    val nome: String = "",
    val formato: String = "1x1",
    val categoria: String = "",
    val modo: String = "classico",
    val salaId: String = "",
    val ts: Long = 0L,
    val estado: String = CONVITE_PENDENTE
)

data class ConvitesState(
    val recebidos: List<Convite> = emptyList(),
    val enviados: List<Convite> = emptyList()
)

/**
 * Direct friend challenges, stored per-player under `/convites/{uid}` — same shape as `/amigos`:
 *
 * ```
 * /convites/{uid}/enviados/{outroUid}   { nome, formato, categoria, modo, salaId, ts, estado }
 * /convites/{uid}/recebidos/{outroUid}  { nome, formato, categoria, modo, salaId, ts, estado }
 * ```
 *
 * The challenger creates the multiplayer room **first**, so `salaId` travels inside the invite and
 * accepting is just "join this room" — the whole MultiMatch pipeline (lockstep sync, podium,
 * profile aggregation) is reused untouched. The receiver answers by writing `estado` on the
 * challenger's `enviados` entry (only legal while the matching `recebidos` entry exists) and
 * deleting its own `recebidos` entry, so both sides always learn the outcome.
 */
class ChallengeRepository {

    private val db get() = FirebaseDatabase.getInstance()
    private fun root() = db.getReference()

    suspend fun serverTimeOffset(): Long =
        db.getReference(".info/serverTimeOffset").get().await().getValue(Long::class.java) ?: 0L

    /** Challenger opens the invite on both sides (atomic). */
    suspend fun send(
        fromUid: String, fromNome: String, toUid: String, toNome: String,
        formato: String, categoria: String, modo: String, salaId: String
    ) {
        fun body(nome: String, estado: String) = mapOf(
            "nome" to nome, "formato" to formato, "categoria" to categoria, "modo" to modo,
            "salaId" to salaId, "ts" to ServerValue.TIMESTAMP, "estado" to estado
        )
        root().updateChildren(
            mapOf(
                "convites/$fromUid/enviados/$toUid" to body(toNome, CONVITE_PENDENTE),
                "convites/$toUid/recebidos/$fromUid" to body(fromNome, CONVITE_PENDENTE)
            )
        ).await()
    }

    /** Receiver answers: flags the challenger's entry and clears its own inbox entry. */
    private suspend fun answer(myUid: String, fromUid: String, estado: String) {
        root().updateChildren(
            mapOf(
                "convites/$fromUid/enviados/$myUid/estado" to estado,
                "convites/$myUid/recebidos/$fromUid" to null
            )
        ).await()
    }

    suspend fun accept(myUid: String, fromUid: String) = answer(myUid, fromUid, CONVITE_ACEITE)

    suspend fun decline(myUid: String, fromUid: String) = answer(myUid, fromUid, CONVITE_RECUSADO)

    /** Challenger withdraws / cleans up (cancel, timeout, or after reading the answer). */
    suspend fun clear(fromUid: String, toUid: String) {
        root().updateChildren(
            mapOf(
                "convites/$fromUid/enviados/$toUid" to null,
                "convites/$toUid/recebidos/$fromUid" to null
            )
        ).await()
    }

    fun observe(uid: String): Flow<ConvitesState> = callbackFlow {
        val ref = db.getReference("convites").child(uid)
        val listener = object : ValueEventListener {
            override fun onDataChange(snapshot: DataSnapshot) {
                trySend(
                    ConvitesState(
                        recebidos = snapshot.child("recebidos").toConvites(),
                        enviados = snapshot.child("enviados").toConvites()
                    )
                )
            }
            override fun onCancelled(error: DatabaseError) { close(error.toException()) }
        }
        ref.addValueEventListener(listener)
        awaitClose { ref.removeEventListener(listener) }
    }

    private fun DataSnapshot.toConvites(): List<Convite> = children.mapNotNull { c ->
        val uid = c.key ?: return@mapNotNull null
        Convite(
            outroUid = uid,
            nome = c.child("nome").getValue(String::class.java) ?: "Jogador",
            formato = c.child("formato").getValue(String::class.java) ?: "1x1",
            categoria = c.child("categoria").getValue(String::class.java) ?: "",
            modo = c.child("modo").getValue(String::class.java) ?: "classico",
            salaId = c.child("salaId").getValue(String::class.java) ?: "",
            ts = c.child("ts").getValue(Long::class.java) ?: 0L,
            estado = c.child("estado").getValue(String::class.java) ?: CONVITE_PENDENTE
        )
    }
}
