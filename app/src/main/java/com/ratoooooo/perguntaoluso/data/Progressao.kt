package com.ratoooooo.perguntaoluso.data

/**
 * XP + level progression. The curve and reward shape are ported from the original
 * BrainBrawl (`ProgressaoService` + `ProgressionRewardPolicy`) — behaviour, not code:
 *
 *  - Per-level cost grows linearly: `300 + (nivel-1)*150` (300, 450, 600, …).
 *  - XP earned per finished game = base + performance + victory bonus:
 *      base        = 50 (Clássico/Caótico), 40 (Eliminatórias)
 *      performance = respostasCertas * 10
 *      victory     = 100 (or 80 in Eliminatórias) when the game was won, else 0
 *
 * Divergence from BrainBrawl (documented in GAME_DESIGN.md): BrainBrawl zeroes the victory
 * bonus for the SOLO mode (it only rewards multiplayer wins). In Pergunta ó Luso only the
 * solo path currently folds into the aggregated `/jogadores/{uid}` profile, and a solo win
 * (accuracy ≥ threshold / surviving Eliminatórias) is the meaningful win signal, so the
 * victory bonus is granted for any won game regardless of format.
 *
 * Only `xpTotal` is persisted; the level and the within-level split are always **derived**
 * from it here, so stored data can never drift out of sync with the curve.
 */
object Progressao {

    data class Estado(
        val xpTotal: Int,
        val nivel: Int,
        val xpNoNivelAtual: Int,
        val xpNecessarioProximoNivel: Int
    ) {
        /** 0f..1f fill of the current level's XP bar. */
        val fracao: Float
            get() = if (xpNecessarioProximoNivel > 0)
                (xpNoNivelAtual.toFloat() / xpNecessarioProximoNivel).coerceIn(0f, 1f) else 0f
    }

    fun xpNecessarioParaProximoNivel(nivel: Int): Int {
        val n = nivel.coerceAtLeast(1)
        return 300 + (n - 1) * 150
    }

    fun estado(xpTotal: Int): Estado {
        var restante = xpTotal.coerceAtLeast(0)
        var nivel = 1
        var necessario = xpNecessarioParaProximoNivel(nivel)
        while (restante >= necessario) {
            restante -= necessario
            nivel += 1
            necessario = xpNecessarioParaProximoNivel(nivel)
        }
        return Estado(xpTotal.coerceAtLeast(0), nivel, restante, necessario)
    }

    /** Convenience: the level a given total XP maps to. */
    fun nivel(xpTotal: Int): Int = estado(xpTotal).nivel

    fun xpGanho(modo: String, respostasCertas: Int, venceu: Boolean): Int {
        val eliminatorias = modo == "eliminatorias"
        val base = if (eliminatorias) 40 else 50
        val performance = respostasCertas.coerceAtLeast(0) * 10
        val bonusVitoria = if (venceu) (if (eliminatorias) 80 else 100) else 0
        return base + performance + bonusVitoria
    }
}
