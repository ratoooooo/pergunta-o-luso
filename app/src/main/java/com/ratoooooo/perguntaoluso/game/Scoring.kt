package com.ratoooooo.perguntaoluso.game

import kotlin.math.max

/**
 * Pure scoring engine adapted from BrainBrawl solo ScoreService + ChaoticEventService.
 *
 * Correct answer:
 *   base = remainingSeconds * 10
 *   Caótico pergunta_dupla doubles base
 *   difficulty multiplier applied to base (facil x1, medio x1.5, dificil x2)
 *   streak bonus added (2 -> +50, 3 -> +75, 4+ -> +100)
 *   Caótico roubo -> +50, tudo_ou_nada -> +100
 *
 * Wrong answer / timeout:
 *   no points, except Caótico tudo_ou_nada -> -50
 *
 * Running total is floored at 0.
 */
object Scoring {

    private const val POINTS_PER_SECOND = 10

    fun streakBonus(streak: Int): Int = when {
        streak >= 4 -> 100
        streak == 3 -> 75
        streak == 2 -> 50
        else -> 0
    }

    /**
     * Points delta for a single answer.
     * @param streakAfter the correct-streak count *after* this answer (1 for the first correct, etc.)
     */
    fun pointsForAnswer(
        isCorrect: Boolean,
        remainingSeconds: Int,
        difficulty: Difficulty,
        event: ChaoticEvent?,
        streakAfter: Int
    ): Int {
        if (!isCorrect) {
            return if (event == ChaoticEvent.TUDO_OU_NADA) -50 else 0
        }

        var base = max(0, remainingSeconds) * POINTS_PER_SECOND
        if (event == ChaoticEvent.PERGUNTA_DUPLA) base *= 2

        var points = (base * difficulty.pointsMultiplier).toInt()
        points += streakBonus(streakAfter)

        when (event) {
            ChaoticEvent.ROUBO -> points += 50
            ChaoticEvent.TUDO_OU_NADA -> points += 100
            else -> Unit
        }
        return points
    }

    fun clampTotal(total: Int): Int = max(0, total)
}
