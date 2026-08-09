package com.ratoooooo.perguntaoluso.data

import com.google.firebase.database.DataSnapshot
import com.google.firebase.database.DatabaseError
import com.google.firebase.database.FirebaseDatabase
import com.google.firebase.database.ServerValue
import com.google.firebase.database.ValueEventListener
import kotlinx.coroutines.channels.awaitClose
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.callbackFlow
import kotlinx.coroutines.tasks.await

/** One entry in a friend list / request list. [nome] is a denormalised label for fast display. */
data class FriendRef(val uid: String, val nome: String, val ts: Long)

/** Everything under `/amigos/{uid}` for the signed-in player. */
data class FriendsState(
    val lista: List<FriendRef> = emptyList(),
    val recebidos: List<FriendRef> = emptyList(),
    val enviados: List<FriendRef> = emptyList()
)

/**
 * Friends, stored per-player under `/amigos/{uid}`:
 *
 * ```
 * /amigos/{uid}/pedidosEnviados/{outroUid}   { nome, ts }   // waiting for them
 * /amigos/{uid}/pedidosRecebidos/{outroUid}  { nome, ts }   // waiting for me
 * /amigos/{uid}/lista/{outroUid}             { nome, ts }   // accepted, written on BOTH sides
 * ```
 *
 * A pending request lives in two places (sender's `pedidosEnviados` + receiver's
 * `pedidosRecebidos`); accepting moves it to `lista` on both sides. Every transition is a single
 * **atomic multi-path update from the root**, so the two sides can never disagree — the RTDB
 * rules validate each leaf path independently (see `database.rules.json`).
 *
 * Player search reuses the existing `/jogadores` node (`nomeBusca`); no new search index.
 */
class FriendsRepository {

    private val db get() = FirebaseDatabase.getInstance()
    private fun root() = db.getReference()
    private fun mine(uid: String) = db.getReference("amigos").child(uid)

    private fun entry(nome: String) = mapOf("nome" to nome, "ts" to ServerValue.TIMESTAMP)

    /** Sender creates the pending request on both sides. */
    suspend fun sendRequest(fromUid: String, fromNome: String, toUid: String, toNome: String) {
        root().updateChildren(
            mapOf(
                "amigos/$fromUid/pedidosEnviados/$toUid" to entry(toNome),
                "amigos/$toUid/pedidosRecebidos/$fromUid" to entry(fromNome)
            )
        ).await()
    }

    /** Sender withdraws their own pending request. */
    suspend fun cancelRequest(fromUid: String, toUid: String) {
        root().updateChildren(
            mapOf(
                "amigos/$fromUid/pedidosEnviados/$toUid" to null,
                "amigos/$toUid/pedidosRecebidos/$fromUid" to null
            )
        ).await()
    }

    /** Receiver accepts: the request is cleared and both friend lists gain the other player. */
    suspend fun acceptRequest(myUid: String, myNome: String, fromUid: String, fromNome: String) {
        root().updateChildren(
            mapOf(
                "amigos/$myUid/lista/$fromUid" to entry(fromNome),
                "amigos/$fromUid/lista/$myUid" to entry(myNome),
                "amigos/$myUid/pedidosRecebidos/$fromUid" to null,
                "amigos/$fromUid/pedidosEnviados/$myUid" to null
            )
        ).await()
    }

    /** Receiver declines: the request is cleared on both sides, nothing else changes. */
    suspend fun declineRequest(myUid: String, fromUid: String) {
        root().updateChildren(
            mapOf(
                "amigos/$myUid/pedidosRecebidos/$fromUid" to null,
                "amigos/$fromUid/pedidosEnviados/$myUid" to null
            )
        ).await()
    }

    /** Live view of my friends + both request queues. */
    fun observe(uid: String): Flow<FriendsState> = callbackFlow {
        val ref = mine(uid)
        val listener = object : ValueEventListener {
            override fun onDataChange(snapshot: DataSnapshot) { trySend(snapshot.toState()) }
            override fun onCancelled(error: DatabaseError) { close(error.toException()) }
        }
        ref.addValueEventListener(listener)
        awaitClose { ref.removeEventListener(listener) }
    }

    private fun DataSnapshot.toState() = FriendsState(
        lista = child("lista").toRefs(),
        recebidos = child("pedidosRecebidos").toRefs(),
        enviados = child("pedidosEnviados").toRefs()
    )

    private fun DataSnapshot.toRefs(): List<FriendRef> = children.mapNotNull { c ->
        val uid = c.key ?: return@mapNotNull null
        FriendRef(
            uid = uid,
            nome = c.child("nome").getValue(String::class.java) ?: "Jogador",
            ts = c.child("ts").getValue(Long::class.java) ?: 0L
        )
    }.sortedByDescending { it.ts }
}
