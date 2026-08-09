package com.ratoooooo.perguntaoluso.data

import com.google.firebase.database.FirebaseDatabase
import kotlinx.coroutines.tasks.await

/**
 * Self-service account deletion — required by Google Play for any app that lets users create
 * an account.
 *
 * ### Why the purge is one atomic multi-path update
 *
 * Every path is collected first and then written in a **single** `updateChildren` from the root.
 * RTDB validates each leaf independently, so either the whole erasure lands or none of it does —
 * there is no state where the profile is gone but the friend edges still point at it.
 *
 * ### Why `/amigos/{uid}` and `/convites/{uid}` are removed edge by edge
 *
 * Neither node has a `.write` rule at its own level: the rules only grant writes on the
 * sub-paths (`lista/$outro`, `pedidosEnviados/$outro`, …). A `"amigos/$uid" to null` would be
 * denied outright. Each edge is deleted individually instead, and RTDB drops the parent once
 * its last child is gone.
 *
 * ### Why the other player's side can be cleaned without reading it
 *
 * `/amigos/{outro}` is readable only by its owner, so the friend's node cannot be enumerated.
 * It does not need to be: **my** lists already name every counterpart, and the rules let the
 * other party delete themselves out of my structures and vice-versa — e.g.
 * `amigos/$uid/lista/$outro` allows `auth.uid === $outro && !newData.exists()`. Deleting a path
 * that does not exist is a no-op, so blind-deleting all three friend edges per counterpart is
 * safe and covers list + both request queues in one pass.
 *
 * Without this the friend keeps a dead entry: a name in their list pointing at a uid that no
 * longer has a profile.
 */
class AccountDeletionRepository {

    private val db get() = FirebaseDatabase.getInstance()

    /**
     * Erases every trace of [uid] from the database. Idempotent — running it twice is harmless,
     * which matters because a failed `FirebaseUser.delete()` sends the caller back through here.
     *
     * Returns a summary for logging/verification.
     */
    suspend fun purge(uid: String): PurgeReport {
        val updates = mutableMapOf<String, Any?>()

        // --- own aggregate + presence (both have a `.write` rule at their own level) ---
        updates["jogadores/$uid"] = null
        updates["presenca/$uid"] = null

        // --- friends: my side and the mirror entry on every counterpart ---
        val amigos = db.getReference("amigos").child(uid).get().await()
        val amigoUids = listOf("lista", "pedidosEnviados", "pedidosRecebidos")
            .flatMap { bucket -> amigos.child(bucket).children.mapNotNull { it.key } }
            .toSet()
        for (bucket in listOf("lista", "pedidosEnviados", "pedidosRecebidos")) {
            for (outro in amigos.child(bucket).children.mapNotNull { it.key }) {
                updates["amigos/$uid/$bucket/$outro"] = null
            }
        }
        for (outro in amigoUids) {
            updates["amigos/$outro/lista/$uid"] = null
            updates["amigos/$outro/pedidosEnviados/$uid"] = null
            updates["amigos/$outro/pedidosRecebidos/$uid"] = null
        }

        // --- direct challenges: same shape, same bidirectional treatment ---
        val convites = db.getReference("convites").child(uid).get().await()
        val conviteUids = listOf("enviados", "recebidos")
            .flatMap { bucket -> convites.child(bucket).children.mapNotNull { it.key } }
            .toSet()
        for (bucket in listOf("enviados", "recebidos")) {
            for (outro in convites.child(bucket).children.mapNotNull { it.key }) {
                updates["convites/$uid/$bucket/$outro"] = null
            }
        }
        for (outro in conviteUids) {
            updates["convites/$outro/enviados/$uid"] = null
            updates["convites/$outro/recebidos/$uid"] = null
        }

        // --- score history: no index on `uid`, so filter client-side exactly like loadMyScores ---
        val scores = db.getReference("scores").get().await()
        val myScoreKeys = scores.children.mapNotNull { child ->
            child.key?.takeIf { child.child("uid").getValue(String::class.java) == uid }
        }
        for (key in myScoreKeys) updates["scores/$key"] = null

        // --- community quizzes: deleted outright, see the note in GAME_DESIGN.md (Fase 23) ---
        val quizzes = db.getReference("categorias_comunitarias").get().await()
        val myQuizIds = quizzes.children.mapNotNull { child ->
            child.key?.takeIf { child.child("criadorUid").getValue(String::class.java) == uid }
        }
        for (id in myQuizIds) updates["categorias_comunitarias/$id"] = null

        db.getReference().updateChildren(updates).await()

        return PurgeReport(
            amigos = amigoUids.size,
            convites = conviteUids.size,
            scores = myScoreKeys.size,
            quizzes = myQuizIds.size,
            caminhos = updates.size
        )
    }
}

data class PurgeReport(
    val amigos: Int,
    val convites: Int,
    val scores: Int,
    val quizzes: Int,
    val caminhos: Int
)
