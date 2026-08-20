package com.ratoooooo.perguntaoluso.game

/**
 * Solo game modes adapted from BrainBrawl (pt.perguntaoluso.app) solo behaviour.
 * Multiplayer variants (1x1/2x2) are intentionally not ported — Pergunta ó Luso is solo only.
 */
enum class GameMode(
    val id: String,
    val displayName: String,
    val tagline: String,
    /**
     * Quantas perguntas se carregam de uma vez. No Clássico e no Caótico é o jogo inteiro; nas
     * Eliminatórias é só o **lote inicial** — o modo não tem fim, ver [semLimiteDePerguntas].
     */
    val questionCount: Int,
    /**
     * Vidas antes de ser eliminado. `0` = o modo não elimina, corre as [questionCount] até ao fim.
     *
     * Era um `endsOnFirstWrong: Boolean`. Três vidas em vez de uma porque um erro logo na
     * primeira pergunta acabava a partida em vinte segundos, e o modo vive de sequências longas.
     */
    val vidas: Int,
    val hasChaoticEvents: Boolean
) {
    CLASSICO(
        id = "classico",
        displayName = "Clássico",
        tagline = "10 perguntas, pontos por rapidez e sequência",
        questionCount = 10,
        vidas = 0,
        hasChaoticEvents = false
    ),
    CAOTICO(
        id = "caotico",
        displayName = "Caótico",
        tagline = "10 perguntas com eventos surpresa a cada ronda",
        questionCount = 10,
        vidas = 0,
        hasChaoticEvents = true
    ),
    ELIMINATORIAS(
        id = "eliminatorias",
        displayName = "Eliminatórias",
        tagline = "Três vidas. Até onde aguentas?",
        questionCount = 20,
        vidas = 3,
        hasChaoticEvents = false
    );

    /** `true` quando o modo elimina por vidas em vez de acabar num número fixo de perguntas. */
    val semLimiteDePerguntas: Boolean get() = vidas > 0

    companion object {
        fun displayNameForId(id: String): String =
            entries.firstOrNull { it.id == id }?.displayName ?: id.ifBlank { "—" }
    }
}
