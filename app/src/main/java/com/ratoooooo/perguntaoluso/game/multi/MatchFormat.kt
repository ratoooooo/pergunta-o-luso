package com.ratoooooo.perguntaoluso.game.multi

/**
 * Multiplayer formats handled by the generalized N-player match system.
 * 1x1 is modelled here too (players = 2, no teams); the head-to-head podium wording
 * is special-cased for it.
 */
enum class MatchFormat(
    val id: String,
    val displayName: String,
    val players: Int,
    val teamBased: Boolean
) {
    ONE_V_ONE(id = "1x1", displayName = "1x1", players = 2, teamBased = false),
    TWO_V_TWO(id = "2x2", displayName = "2x2", players = 4, teamBased = true),
    GRUPO(id = "grupo", displayName = "Grupo", players = 4, teamBased = false);

    companion object {
        fun fromId(id: String?): MatchFormat = entries.firstOrNull { it.id == id } ?: GRUPO

        /**
         * Matchmaking queue key — players are grouped by format **and** category **and** mode,
         * so everyone in a queue is playing the exact same game. RTDB keys forbid . $ # [ ] /,
         * which are stripped from the (accent-safe) category name.
         */
        fun queueKey(format: MatchFormat, categoria: String, modo: String): String {
            val catSafe = categoria.replace(Regex("[.$#\\[\\]/]"), "").replace(" ", "_")
            return "${format.id}__${catSafe}__$modo"
        }
    }
}
