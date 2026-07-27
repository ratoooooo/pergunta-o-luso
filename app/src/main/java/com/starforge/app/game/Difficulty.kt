package com.starforge.app.game

/**
 * Wraps the per-question "dificuldade" field (facil/medio/dificil) from the RTDB data.
 * Drives both question ordering (progression ramp) and a points multiplier bonus.
 */
enum class Difficulty(
    val id: String,
    val displayName: String,
    val rank: Int,
    val pointsMultiplier: Double
) {
    FACIL("facil", "Fácil", 0, 1.0),
    MEDIO("medio", "Médio", 1, 1.5),
    DIFICIL("dificil", "Difícil", 2, 2.0);

    companion object {
        fun fromId(raw: String?): Difficulty = when (raw?.trim()?.lowercase()) {
            "medio", "médio" -> MEDIO
            "dificil", "difícil" -> DIFICIL
            else -> FACIL
        }
    }
}
