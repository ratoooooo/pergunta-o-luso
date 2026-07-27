package com.starforge.app.game

import com.starforge.app.data.Profile
import com.starforge.app.data.categoriaSlug
import com.starforge.app.game.avatar.PortugueseSymbol

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
    val progress: (Profile) -> Int
) {
    fun value(p: Profile): Int = progress(p).coerceAtMost(goal)
    fun unlocked(p: Profile): Boolean = progress(p) >= goal
    fun progressText(p: Profile): String = "${value(p)} / $goal"
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
    Achievement("primeira_vitoria", "Primeira Vitória", "Vence a tua primeira partida.", PortugueseSymbol.CORACAO, 1) { it.vitorias },
    Achievement("sequencia", "Em Chamas", "Acerta 5 respostas seguidas.", PortugueseSymbol.GALO, 5) { it.maxStreak },
    Achievement("perfeita", "Partida Perfeita", "Acerta todas as respostas de uma partida.", PortugueseSymbol.NATA, 1) { it.partidasPerfeitas },
    // category masters (win 3 games in the category)
    Achievement("mestre_cultura", "Mestre de Cultura Geral", "Vence 3 partidas de Cultura Geral.", PortugueseSymbol.LUSIADAS, 3) { catWins(it, "Cultura Geral") },
    Achievement("mestre_geografia", "Mestre de Geografia", "Vence 3 partidas de Geografia.", PortugueseSymbol.CARAVELA, 3) { catWins(it, "Geografia") },
    Achievement("mestre_historia", "Mestre de História", "Vence 3 partidas de História.", PortugueseSymbol.AZULEJO, 3) { catWins(it, "História") },
    Achievement("mestre_desporto", "Mestre de Desporto", "Vence 3 partidas de Desporto.", PortugueseSymbol.SARDINHA, 3) { catWins(it, "Desporto") },
    Achievement("mestre_gentilicos", "Mestre de Gentílicos", "Vence 3 partidas de Gentílicos.", PortugueseSymbol.CALCADA, 3) { catWins(it, "Gentílicos") },
    // games played milestones
    Achievement("jogos_10", "Veterano", "Joga 10 partidas.", PortugueseSymbol.GUITARRA, 10) { it.jogos },
    Achievement("jogos_50", "Dedicado", "Joga 50 partidas.", PortugueseSymbol.GUITARRA, 50) { it.jogos },
    Achievement("jogos_100", "Lendário", "Joga 100 partidas.", PortugueseSymbol.GUITARRA, 100) { it.jogos },
    // multiplayer wins (per format) — enabled by Part 1
    Achievement("mp_1x1", "Duelista", "Vence um 1x1.", PortugueseSymbol.CARAVELA, 1) { it.multiVitorias["1x1"] ?: 0 },
    Achievement("mp_2x2", "Companheiro", "Vence um 2x2.", PortugueseSymbol.GALO, 1) { it.multiVitorias["2x2"] ?: 0 },
    Achievement("mp_grupo", "Rei do Grupo", "Vence uma partida de Grupo.", PortugueseSymbol.CALCADA, 1) { it.multiVitorias["grupo"] ?: 0 },
    // level / XP milestone
    Achievement("nivel_5", "Nível 5", "Alcança o nível 5.", PortugueseSymbol.FAROL, 5) { it.nivel }
)
