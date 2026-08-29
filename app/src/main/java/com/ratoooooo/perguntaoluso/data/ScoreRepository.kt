package com.ratoooooo.perguntaoluso.data

import com.google.firebase.auth.FirebaseAuth
import com.google.firebase.database.DataSnapshot
import com.google.firebase.database.DatabaseError
import com.google.firebase.database.FirebaseDatabase
import com.google.firebase.database.ValueEventListener
import kotlinx.coroutines.channels.awaitClose
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.callbackFlow
import kotlinx.coroutines.suspendCancellableCoroutine
import kotlin.coroutines.resume
import kotlin.coroutines.resumeWithException

data class ScoreEntry(
    val uid: String = "",
    val modo: String = "",
    val categoria: String = "",
    val formato: String = "solo",
    val score: Int = 0,
    val correctCount: Int = 0,
    val total: Int = 0,
    val timestamp: Long = 0L
)

private const val TOP_SCORES_LIMIT = 5

open class ScoreRepository {

    open suspend fun saveScore(modo: String, categoria: String, score: Int, correctCount: Int, total: Int, formato: String = "solo") {
        val uid = FirebaseAuth.getInstance().currentUser?.uid
            ?: error("saveScore called without a signed-in user")
        val entry = mapOf(
            "uid" to uid,
            "modo" to modo,
            "categoria" to categoria,
            "formato" to formato,
            "score" to score,
            "correctCount" to correctCount,
            "total" to total,
            "timestamp" to System.currentTimeMillis()
        )
        suspendCancellableCoroutine<Unit> { continuation ->
            FirebaseDatabase.getInstance().getReference("scores").push().setValue(entry)
                .addOnSuccessListener { continuation.resume(Unit) }
                .addOnFailureListener { error -> continuation.resumeWithException(error) }
        }
    }

    /**
     * Live stream of top scores from `/scores`.
     *
     * Substitui a leitura pontual [loadTopScores]: um listener contínuo garante que
     * pontuações novas — escritas no solo pelo próprio jogador, no multijogador pelo servidor,
     * ou por outro jogador em simultâneo — reflectem de imediato no pódio sem esperar por
     * uma nova partida.
     */
    open fun observeTopScores(limit: Int = TOP_SCORES_LIMIT): Flow<List<ScoreEntry>> = callbackFlow {
        val query = FirebaseDatabase.getInstance().getReference("scores")
            .orderByChild("score")
            .limitToLast(limit)
        val listener = object : ValueEventListener {
            override fun onDataChange(snapshot: DataSnapshot) {
                val entries = snapshot.children.mapNotNull { it.toScoreEntry() }.reversed()
                trySend(entries)
            }

            override fun onCancelled(error: DatabaseError) {
                close(error.toException())
            }
        }
        query.addValueEventListener(listener)
        awaitClose { query.removeEventListener(listener) }
    }

    open suspend fun loadTopScores(limit: Int = TOP_SCORES_LIMIT): List<ScoreEntry> {
        val snapshot = suspendCancellableCoroutine<DataSnapshot> { continuation ->
            FirebaseDatabase.getInstance().getReference("scores")
                .orderByChild("score")
                .limitToLast(limit)
                .get()
                .addOnSuccessListener { continuation.resume(it) }
                .addOnFailureListener { error -> continuation.resumeWithException(error) }
        }
        return snapshot.children.mapNotNull { it.toScoreEntry() }.reversed()
    }

    /** This player's own games, most recent first. RTDB has no compound query, so we
     *  filter by uid client-side (score history is small). */
    open suspend fun loadMyScores(uid: String, limit: Int = 30): List<ScoreEntry> {
        val snapshot = suspendCancellableCoroutine<DataSnapshot> { continuation ->
            FirebaseDatabase.getInstance().getReference("scores")
                .get()
                .addOnSuccessListener { continuation.resume(it) }
                .addOnFailureListener { error -> continuation.resumeWithException(error) }
        }
        return snapshot.children.mapNotNull { it.toScoreEntry() }
            .filter { it.uid == uid }
            .sortedByDescending { it.timestamp }
            .take(limit)
    }

    private fun DataSnapshot.toScoreEntry(): ScoreEntry? {
        val score = child("score").getValue(Int::class.java) ?: return null
        val total = child("total").getValue(Int::class.java) ?: return null
        val correctCount = child("correctCount").getValue(Int::class.java) ?: 0
        val categoria = child("categoria").getValue(String::class.java) ?: ""
        val modo = child("modo").getValue(String::class.java) ?: ""
        val formato = child("formato").getValue(String::class.java) ?: "solo"
        val timestamp = child("timestamp").getValue(Long::class.java) ?: 0L
        val uid = child("uid").getValue(String::class.java) ?: ""
        return ScoreEntry(
            uid = uid,
            modo = modo,
            categoria = categoria,
            formato = formato,
            score = score,
            correctCount = correctCount,
            total = total,
            timestamp = timestamp
        )
    }
}
