package com.ratoooooo.perguntaoluso.game

import com.ratoooooo.perguntaoluso.data.Profile
import com.ratoooooo.perguntaoluso.data.categoriaSlug
import com.ratoooooo.perguntaoluso.game.avatar.PortugueseSymbol

/**
 * A conquista, bound purely to fields already aggregated in `/jogadores/{uid}`.
 * [progress] reads the current value; [goal] is the threshold to unlock.
 */
data class Achievement(
    val id: String,
    val title: String,
    val desc: String,
    val symbol: PortugueseSymbol,
    val goal: Int,
    /** Nome do que se está a contar ("jogos", "vitórias"), para o progresso se ler como frase. */
    val unidade: String,
    val progress: (Profile) -> Int
) {
    fun value(p: Profile): Int = progress(p).coerceAtMost(goal)
    fun unlocked(p: Profile): Boolean = progress(p) >= goal
    fun progressText(p: Profile): String = "${value(p)} de $goal $unidade"

    /** Quanto falta para desbloquear. `0` quando já está feita. */
    fun falta(p: Profile): Int = (goal - progress(p)).coerceAtLeast(0)

    /** 0f..1f, para a barra de progresso. */
    fun fracao(p: Profile): Float =
        if (goal <= 0) 1f else (value(p).toFloat() / goal).coerceIn(0f, 1f)
}

private fun catWins(p: Profile, cat: String) = p.categoriaVitorias[categoriaSlug(cat)] ?: 0

/**
 * Symbol ↔ achievement mapping (chosen so each category master gets a distinct, thematically
 * fitting symbol; other achievements reuse the 10-symbol set):
 *  - Cultura Geral → Os Lusíadas (livro/cultura)   - Geografia → Caravela (descobrimentos)
 *  - História → Azulejo (azulejos narram história) - Desporto → Sardinha (arraiais/festa)
 *  - Gentílicos → Calçada (lugares/ruas)
 */
val ACHIEVEMENTS: List<Achievement> = listOf(
    Achievement("primeira_vitoria", "Primeira Vitória", "Vence a tua primeira partida.", PortugueseSymbol.CORACAO, 1, "vitória") { it.vitorias },
    Achievement("sequencia", "Em Chamas", "Acerta 5 respostas seguidas.", PortugueseSymbol.GALO, 5, "respostas seguidas") { it.maxStreak },
    Achievement("perfeita", "Partida Perfeita", "Acerta todas as respostas de uma partida.", PortugueseSymbol.NATA, 1, "partida perfeita") { it.partidasPerfeitas },
    // category masters (win 3 games in the category)
    Achievement("mestre_cultura", "Mestre de Cultura Geral", "Vence 3 partidas de Cultura Geral.", PortugueseSymbol.LUSIADAS, 3, "vitórias") { catWins(it, "Cultura Geral") },
    Achievement("mestre_geografia", "Mestre de Geografia", "Vence 3 partidas de Geografia.", PortugueseSymbol.CARAVELA, 3, "vitórias") { catWins(it, "Geografia") },
    Achievement("mestre_historia", "Mestre de História", "Vence 3 partidas de História.", PortugueseSymbol.AZULEJO, 3, "vitórias") { catWins(it, "História") },
    Achievement("mestre_desporto", "Mestre de Desporto", "Vence 3 partidas de Desporto.", PortugueseSymbol.SARDINHA, 3, "vitórias") { catWins(it, "Desporto") },
    Achievement("mestre_gentilicos", "Mestre de Gentílicos", "Vence 3 partidas de Gentílicos.", PortugueseSymbol.CALCADA, 3, "vitórias") { catWins(it, "Gentílicos") },
    // games played milestones
    Achievement("jogos_10", "Veterano", "Joga 10 partidas.", PortugueseSymbol.GUITARRA, 10, "jogos") { it.jogos },
    Achievement("jogos_50", "Dedicado", "Joga 50 partidas.", PortugueseSymbol.GUITARRA, 50, "jogos") { it.jogos },
    Achievement("jogos_100", "Lendário", "Joga 100 partidas.", PortugueseSymbol.GUITARRA, 100, "jogos") { it.jogos },
    // multiplayer wins (per format) — enabled by Part 1
    Achievement("mp_1x1", "Duelista", "Vence um 1x1.", PortugueseSymbol.CARAVELA, 1, "vitória") { it.multiVitorias["1x1"] ?: 0 },
    Achievement("mp_2x2", "Companheiro", "Vence um 2x2.", PortugueseSymbol.GALO, 1, "vitória") { it.multiVitorias["2x2"] ?: 0 },
    Achievement("mp_grupo", "Rei do Grupo", "Vence uma partida de Grupo.", PortugueseSymbol.CALCADA, 1, "vitória") { it.multiVitorias["grupo"] ?: 0 },
    // level / XP milestone
    Achievement("nivel_5", "Nível 5", "Alcança o nível 5.", PortugueseSymbol.FAROL, 5, "níveis") { it.nivel }
)
