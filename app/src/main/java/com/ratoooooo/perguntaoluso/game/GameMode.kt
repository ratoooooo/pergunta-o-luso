package com.ratoooooo.perguntaoluso.game

/**
 * Solo game modes adapted from BrainBrawl (pt.perguntaoluso.app) solo behaviour.
 * Multiplayer variants (1x1/2x2) are intentionally not ported — Pergunta ó Luso is solo only.
 */
enum class GameMode(
    val id: String,
    val displayName: String,
    val tagline: String,
    val questionCount: Int,
    val endsOnFirstWrong: Boolean,
    val hasChaoticEvents: Boolean
) {
    CLASSICO(
        id = "classico",
        displayName = "Clássico",
        tagline = "10 perguntas, pontos por rapidez e sequência",
        questionCount = 10,
        endsOnFirstWrong = false,
        hasChaoticEvents = false
    ),
    CAOTICO(
        id = "caotico",
        displayName = "Caótico",
        tagline = "10 perguntas com eventos surpresa a cada ronda",
        questionCount = 10,
        endsOnFirstWrong = false,
        hasChaoticEvents = true
    ),
    ELIMINATORIAS(
        id = "eliminatorias",
        displayName = "Eliminatórias",
        tagline = "Erras uma e acabou. Até onde aguentas?",
        questionCount = 20,
        endsOnFirstWrong = true,
        hasChaoticEvents = false
    );

    companion object {
        fun displayNameForId(id: String): String =
            entries.firstOrNull { it.id == id }?.displayName ?: id.ifBlank { "—" }
    }
}
