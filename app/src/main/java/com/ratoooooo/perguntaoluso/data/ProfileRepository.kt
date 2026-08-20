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
    val categoria: String = "",
    /**
     * Dia civil de Lisboa em que a partida acabou (`"AAAA-MM-DD"`), para a sequência de dias.
     * Calculado **uma vez** por quem cria o resultado: a transação pode repetir, e recalcular a
     * data lá dentro daria respostas diferentes numa retentativa que atravessasse a meia-noite.
     */
    val hoje: String = StreakDiario.hoje()
)

val MODE_IDS = listOf("classico", "caotico", "eliminatorias")

/** Hard cap on a display name. Mirrored by `jogadores/$uid/nome` `.validate` in the rules. */
const val NOME_MAX_LEN = 40

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

    suspend fun addXp(uid: String, xp: Int) {
        if (xp <= 0) return
        suspendCancellableCoroutine<Unit> { cont ->
            ref(uid).child("xpTotal").runTransaction(object : Transaction.Handler {
                override fun doTransaction(data: MutableData): Transaction.Result {
                    val current = (data.value as? Number)?.toInt() ?: 0
                    data.value = current + xp
                    return Transaction.success(data)
                }
                override fun onComplete(error: com.google.firebase.database.DatabaseError?, committed: Boolean, snapshot: DataSnapshot?) {
                    if (error != null) cont.resumeWithException(error.toException())
                    else cont.resume(Unit)
                }
            })
        }
    }

    /**
     * Writes the player's chosen name, creating the profile node if needed.
     * Also writes [nomeBusca] (lower-cased name) in the same update, so the search index can
     * never drift from the display name — every name write goes through here.
     */
    suspend fun setNome(uid: String, nome: String) {
        // Truncated to match the `.validate` cap in database.rules.json — the name is shown
        // publicly in the ranking, so an over-long one is rejected server-side anyway; cutting
        // it here turns that rejection into a clean save instead of a thrown write.
        val limpo = nome.trim().take(NOME_MAX_LEN)
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
        // Sequência de dias — corre aqui e não no ViewModel para apanhar **todas** as partidas,
        // solo e multijogador, que passam as duas por esta transação.
        aplicarStreak(data, r.hoje)

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

    /**
     * Avança (ou reinicia) a sequência de dias dentro da transação do perfil.
     *
     * [hoje] vem de fora, já calculado, e **não** de `LocalDate.now()` aqui: o handler de uma
     * transação pode correr várias vezes, e recalcular a data a meio de uma retentativa que
     * atravessasse a meia-noite dava dois resultados diferentes para a mesma partida.
     */
    private fun aplicarStreak(data: MutableData, hoje: String) {
        val anterior = StreakDiario.Estado(
            diasSeguidos = intAt(data, "diasSeguidos"),
            ultimoDiaJogado = data.child("ultimoDiaJogado").value as? String ?: "",
            maiorSequenciaDias = intAt(data, "maiorSequenciaDias"),
            protecoes = if (data.child("protecoesStreak").value != null)
                intAt(data, "protecoesStreak") else StreakDiario.MAX_PROTECOES,
            protecaoUsadaEm = data.child("protecaoUsadaEm").value as? String ?: ""
        )
        val r = StreakDiario.avaliar(anterior, hoje)
        data.child("diasSeguidos").value = r.estado.diasSeguidos
        data.child("maiorSequenciaDias").value = r.estado.maiorSequenciaDias
        // Só se escreve uma data se ela existir. As rules exigem `AAAA-MM-DD` nestes dois
        // campos, e escrever "" faria a validação falhar — mas a validação é sobre o nó inteiro,
        // por isso não perdia só a sequência: **rejeitava a transação toda** e o jogador perdia
        // os pontos e o XP da partida. Um campo em falta é infinitamente melhor do que isso.
        if (r.estado.ultimoDiaJogado.isNotBlank()) {
            data.child("ultimoDiaJogado").value = r.estado.ultimoDiaJogado
        }
        data.child("protecoesStreak").value = r.estado.protecoes
        if (r.estado.protecaoUsadaEm.isNotBlank()) {
            data.child("protecaoUsadaEm").value = r.estado.protecaoUsadaEm
        }
        // XP fixo por dia novo de sequência, nunca escalado pelo tamanho dela — ver a Fase 20
        // (Roda da Sorte) para o que acontece quando se mete XP sem tecto na curva.
        if (r.xp > 0) bump(data.child("xpTotal"), r.xp)
    }

    private fun intAt(data: MutableData, path: String): Int =
        (data.child(path).value as? Number)?.toInt() ?: 0

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

    /**
     * Live view of `/jogadores/{uid}` — o **próprio** perfil, não o de outro jogador.
     *
     * Existe porque uma leitura pontual (`loadProfile`) só reflecte quem estava a chamá-la: o
     * Início lia-a uma vez ao arrancar e nunca mais, por isso pontos/XP/avatar só se
     * actualizavam se o jogador passasse pelo Perfil (que força uma releitura). No multijogador
     * o problema era pior — `MultiMatchViewModel` agrega o perfil escrevendo directamente na
     * RTDB, sem qualquer forma de avisar o `GameViewModel` de que os dados mudaram, por isso o
     * Início ficava sempre com o valor anterior à partida. Mesmo padrão do bug do `friendsJob`
     * (Fase 14): estado preso a um momento antigo em vez de reagir à mudança real.
     *
     * A correcção é ligar aqui, tal como já se faz para `/amigos/{uid}` (`FriendsRepository.observe`)
     * e `/presenca` (`PresenceRepository`): um listener vivo é a fonte de verdade contínua,
     * qualquer que seja quem escreveu — solo, multijogador, ou o que vier a seguir.
     */
    fun observe(uid: String): Flow<Profile> = callbackFlow {
        val reference = ref(uid)
        val listener = object : ValueEventListener {
            override fun onDataChange(snapshot: DataSnapshot) { trySend(snapshot.toProfile(uid)) }
            override fun onCancelled(error: DatabaseError) { close(error.toException()) }
        }
        reference.addValueEventListener(listener)
        awaitClose { reference.removeEventListener(listener) }
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
            }.toMap(),
            diasSeguidos = readInt("diasSeguidos"),
            ultimoDiaJogado = child("ultimoDiaJogado").getValue(String::class.java) ?: "",
            maiorSequenciaDias = readInt("maiorSequenciaDias"),
            // Um perfil antigo não tem o campo. Ler 0 aí dava um jogador sem escudo nenhum logo
            // à entrada; quem nunca gastou nenhum começa com o máximo.
            protecoesStreak = if (hasChild("protecoesStreak")) readInt("protecoesStreak")
                else StreakDiario.MAX_PROTECOES,
            protecaoUsadaEm = child("protecaoUsadaEm").getValue(String::class.java) ?: ""
        )
    }
}
