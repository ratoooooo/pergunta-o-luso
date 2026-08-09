package com.ratoooooo.perguntaoluso.data

import com.google.firebase.database.DataSnapshot
import com.google.firebase.database.FirebaseDatabase
import com.google.firebase.database.MutableData
import com.google.firebase.database.Transaction
import kotlinx.coroutines.suspendCancellableCoroutine
import kotlin.coroutines.resume
import kotlin.coroutines.resumeWithException
import kotlin.math.max

/** Result of finishing one game, fed into the aggregated profile. */
data class GameResult(
    val modo: String,
    val score: Int,
    val correctCount: Int,
    val total: Int,
    val won: Boolean,
    val maxStreak: Int,
    /** Multiplayer format id ("1x1"/"2x2"/"grupo"); null for solo. Drives per-format win counters. */
    val formato: String? = null,
    /** Category display name; drives per-category mastery counters. Blank = skip. */
    val categoria: String = ""
)

val MODE_IDS = listOf("classico", "caotico", "eliminatorias")

/** Search key for a display name: trimmed + lower-cased (stored at /jogadores/{uid}/nomeBusca). */
fun buscaKey(nome: String): String = nome.trim().lowercase()

/** Accent-safe RTDB-key slug for a category name ("Cultura Geral" -> "cultura_geral"). */
fun categoriaSlug(cat: String): String =
    java.text.Normalizer.normalize(cat, java.text.Normalizer.Form.NFD)
        .replace(Regex("\\p{Mn}+"), "")
        .lowercase()
        .replace(Regex("[^a-z0-9]+"), "_")
        .trim('_')

class ProfileRepository {

    private fun ref(uid: String) = FirebaseDatabase.getInstance().getReference("jogadores").child(uid)

    /**
     * Writes the player's chosen name, creating the profile node if needed.
     * Also writes [nomeBusca] (lower-cased name) in the same update, so the search index can
     * never drift from the display name — every name write goes through here.
     */
    suspend fun setNome(uid: String, nome: String) {
        val limpo = nome.trim()
        suspendCancellableCoroutine<Unit> { cont ->
            ref(uid).updateChildren(mapOf("nome" to limpo, "nomeBusca" to buscaKey(limpo)))
                .addOnSuccessListener { cont.resume(Unit) }
                .addOnFailureListener { cont.resumeWithException(it) }
        }
    }

    /**
     * Prefix search over `nomeBusca` (`orderByChild` + `startAt`/`endAt` with the high sentinel
     * ``). Skips the caller's own profile and any profile without a name (anonymous
     * players never get a `nomeBusca`, so they are invisible to search).
     */
    suspend fun searchByNome(prefix: String, myUid: String, limit: Int = 20): List<Profile> {
        val q = buscaKey(prefix)
        if (q.isBlank()) return emptyList()
        val snap = suspendCancellableCoroutine<DataSnapshot> { cont ->
            FirebaseDatabase.getInstance().getReference("jogadores")
                .orderByChild("nomeBusca").startAt(q).endAt(q + "").limitToFirst(limit)
                .get()
                .addOnSuccessListener { cont.resume(it) }
                .addOnFailureListener { cont.resumeWithException(it) }
        }
        return snap.children.mapNotNull { child ->
            val uid = child.key ?: return@mapNotNull null
            if (uid == myUid) return@mapNotNull null
            val p = child.toProfile(uid)
            if (p.temNome) p else null
        }
    }

    /** Writes the player's chosen avatar symbol id. */
    suspend fun setAvatar(uid: String, avatar: String) {
        suspendCancellableCoroutine<Unit> { cont ->
            ref(uid).child("avatar").setValue(avatar)
                .addOnSuccessListener { cont.resume(Unit) }
                .addOnFailureListener { cont.resumeWithException(it) }
        }
    }

    /** Atomically folds one game's result into the aggregated stats (global + per-mode). */
    suspend fun updateAfterGame(uid: String, result: GameResult) {
        suspendCancellableCoroutine<Unit> { cont ->
            ref(uid).runTransaction(object : Transaction.Handler {
                override fun doTransaction(current: MutableData): Transaction.Result {
                    accumulate(current, result)
                    return Transaction.success(current)
                }

                override fun onComplete(error: com.google.firebase.database.DatabaseError?, committed: Boolean, snapshot: DataSnapshot?) {
                    if (error != null) cont.resumeWithException(error.toException())
                    else cont.resume(Unit)
                }
            })
        }
    }

    private fun accumulate(data: MutableData, r: GameResult) {
        // global
        bump(data.child("jogos"), 1)
        bump(data.child("pontos"), r.score)
        bump(data.child("respostasCertas"), r.correctCount)
        bump(data.child("respostasTotais"), r.total)
        bump(data.child("vitorias"), if (r.won) 1 else 0)
        setMax(data.child("recorde"), r.score)
        setMax(data.child("maxStreak"), r.maxStreak)
        bump(data.child("xpTotal"), Progressao.xpGanho(r.modo, r.correctCount, r.won))
        data.child("atualizadoEm").value = System.currentTimeMillis()

        // per-mode
        val m = data.child("modos").child(r.modo)
        bump(m.child("jogos"), 1)
        bump(m.child("pontos"), r.score)
        bump(m.child("respostasCertas"), r.correctCount)
        bump(m.child("respostasTotais"), r.total)
        bump(m.child("vitorias"), if (r.won) 1 else 0)
        setMax(m.child("recorde"), r.score)

        // Per-format multiplayer win counter (for the multiplayer-win achievements).
        if (r.formato != null) {
            bump(data.child("multiJogos").child(r.formato), 1)
            if (r.won) bump(data.child("multiVitorias").child(r.formato), 1)
        }
        // Perfect-game counter (all answers correct) — for the "partida perfeita" achievement.
        if (r.total > 0 && r.correctCount >= r.total) bump(data.child("partidasPerfeitas"), 1)
        // Per-category counters — for the "mestre de categoria" achievements.
        if (r.categoria.isNotBlank()) {
            val slug = categoriaSlug(r.categoria)
            bump(data.child("categorias").child(slug).child("jogos"), 1)
            if (r.won) bump(data.child("categorias").child(slug).child("vitorias"), 1)
        }
    }

    private fun bump(node: MutableData, delta: Int) {
        val cur = (node.value as? Number)?.toLong() ?: 0L
        node.value = cur + delta
    }

    private fun setMax(node: MutableData, candidate: Int) {
        val cur = (node.value as? Number)?.toLong() ?: 0L
        node.value = max(cur, candidate.toLong())
    }

    suspend fun loadProfile(uid: String): Profile {
        val snap = getSnapshot(ref(uid))
        return snap.toProfile(uid)
    }

    suspend fun loadAllProfiles(): List<Profile> {
        val snap = getSnapshot(FirebaseDatabase.getInstance().getReference("jogadores"))
        return snap.children.mapNotNull { child ->
            val uid = child.key ?: return@mapNotNull null
            child.toProfile(uid)
        }
    }

    private suspend fun getSnapshot(reference: com.google.firebase.database.DatabaseReference): DataSnapshot =
        suspendCancellableCoroutine { cont ->
            reference.get()
                .addOnSuccessListener { cont.resume(it) }
                .addOnFailureListener { cont.resumeWithException(it) }
        }

    private fun DataSnapshot.readInt(path: String): Int =
        child(path).getValue(Long::class.java)?.toInt()
            ?: child(path).getValue(Int::class.java) ?: 0

    private fun DataSnapshot.toModeStats(): ModeStats = ModeStats(
        jogos = readInt("jogos"),
        pontos = readInt("pontos"),
        respostasCertas = readInt("respostasCertas"),
        respostasTotais = readInt("respostasTotais"),
        vitorias = readInt("vitorias"),
        recorde = readInt("recorde")
    )

    private fun DataSnapshot.readIntMap(path: String): Map<String, Int> =
        child(path).children.mapNotNull { c -> (c.key ?: return@mapNotNull null) to (c.getValue(Long::class.java)?.toInt() ?: 0) }.toMap()

    private fun DataSnapshot.toProfile(uid: String): Profile {
        val modosSnap = child("modos")
        val modos = MODE_IDS.associateWith { id -> modosSnap.child(id).toModeStats() }
        return Profile(
            uid = uid,
            nome = child("nome").getValue(String::class.java) ?: "",
            jogos = readInt("jogos"),
            pontos = readInt("pontos"),
            respostasCertas = readInt("respostasCertas"),
            respostasTotais = readInt("respostasTotais"),
            vitorias = readInt("vitorias"),
            recorde = readInt("recorde"),
            maxStreak = readInt("maxStreak"),
            xpTotal = readInt("xpTotal"),
            avatar = child("avatar").getValue(String::class.java) ?: "",
            partidasPerfeitas = readInt("partidasPerfeitas"),
            modos = modos,
            multiVitorias = readIntMap("multiVitorias"),
            multiJogos = readIntMap("multiJogos"),
            categoriaVitorias = child("categorias").children.mapNotNull { c ->
                (c.key ?: return@mapNotNull null) to (c.child("vitorias").getValue(Long::class.java)?.toInt() ?: 0)
            }.toMap(),
            categoriaJogos = child("categorias").children.mapNotNull { c ->
                (c.key ?: return@mapNotNull null) to (c.child("jogos").getValue(Long::class.java)?.toInt() ?: 0)
            }.toMap()
        )
    }
}
