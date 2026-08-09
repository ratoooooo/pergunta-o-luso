package com.ratoooooo.perguntaoluso.game.multi

/**
 * Multiplayer formats handled by the generalized N-player match system.
 * 1x1 is modelled here too (players = 2, no teams); the head-to-head podium wording
 * is special-cased for it.
 */
enum class MatchFormat(
    val id: String,
    val displayName: String,
    /** Lugares na sala — a **capacidade máxima**. Enche aqui e a partida arranca sozinha. */
    val players: Int,
    /**
     * Jogadores necessários para a partida **poder** começar.
     *
     * Igual a [players] em 1x1 e 2x2: um duelo precisa dos dois, e o 2x2 precisa de quatro
     * porque o anfitrião divide os membros em duas equipas de dois — com três, uma equipa
     * ficava com um jogador só.
     *
     * No Grupo é **4 de um máximo de 10**: esperar por dez pessoas na mesma categoria e no
     * mesmo modo ao mesmo tempo tornava o formato injogável na prática (ver Fase 30). Quatro
     * é o mínimo em que "todos contra todos" ainda significa alguma coisa.
     */
    val minPlayers: Int,
    val teamBased: Boolean
) {
    ONE_V_ONE(id = "1x1", displayName = "1x1", players = 2, minPlayers = 2, teamBased = false),
    TWO_V_TWO(id = "2x2", displayName = "2x2", players = 4, minPlayers = 4, teamBased = true),
    GRUPO(id = "grupo", displayName = "Grupo", players = 10, minPlayers = 4, teamBased = false);

    /** Há um intervalo de lugares (só o Grupo) em vez de um número fixo. */
    val hasFlexibleSize: Boolean get() = minPlayers < players

    /** "2 jogadores" ou "4 a 10 jogadores". */
    val sizeLabel: String
        get() = if (hasFlexibleSize) "$minPlayers a $players jogadores" else "$players jogadores"

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
