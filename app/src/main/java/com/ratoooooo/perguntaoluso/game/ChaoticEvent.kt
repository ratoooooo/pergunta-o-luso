package com.ratoooooo.perguntaoluso.game

/**
 * Caótico events, ported from BrainBrawl ChaoticEventService behaviour.
 * Max one event per question, assigned deterministically by question index.
 */
enum class ChaoticEvent(
    val id: String,
    val displayName: String,
    val description: String
) {
    PERGUNTA_DUPLA(
        id = "pergunta_dupla",
        displayName = "Pergunta Dupla",
        description = "Pontos base a dobrar!"
    ),
    VELOCIDADE_MAXIMA(
        id = "velocidade_maxima",
        displayName = "Velocidade Máxima",
        description = "Metade do tempo!"
    ),
    ROUBO(
        id = "roubo",
        displayName = "Roubo",
        description = "+50 pontos se acertares"
    ),
    TUDO_OU_NADA(
        id = "tudo_ou_nada",
        displayName = "Tudo ou Nada",
        description = "+100 se acertas, -50 se erras"
    );

    companion object {
        private val ORDER = listOf(PERGUNTA_DUPLA, VELOCIDADE_MAXIMA, ROUBO, TUDO_OU_NADA)

        /** Deterministic by question index so a given round always yields the same event. */
        fun forIndex(index: Int): ChaoticEvent = ORDER[index % ORDER.size]
    }
}
