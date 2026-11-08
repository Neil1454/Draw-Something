package com.neil1454.drawsomething.core

import kotlin.math.roundToInt

/**
 * Scoring rules.
 *
 * Both players are paid from the SAME clock: the faster the guess is confirmed, the more
 * points the round is worth, and the drawer and the guesser both earn them. That ties the
 * drawer's reward to how clear the drawing is and the guesser's reward to how quickly they
 * read it, and nobody gains by sabotaging the other.
 *
 * The only things that separate the two players are their own mistakes:
 *  - the guesser loses [WRONG_GUESS_PENALTY] for each guess the drawer rejects
 *  - the drawer loses [SKIP_PENALTY] for each word they skip
 *  - the drawer loses [RULE_BREAK_PENALTY] for each rule break they agree to (see [Rules])
 *
 * Roles swap every round, so both players draw and guess equally often.
 */
object Scoring {
    const val BASE = 100
    /** Even the slowest correct guess earns this share of the base. */
    const val MIN_SHARE = 0.30
    const val WRONG_GUESS_PENALTY = 5
    const val SKIP_PENALTY = 15
    /** Charged to the drawer each time they agree they broke a rule (worse than skipping). */
    const val RULE_BREAK_PENALTY = 20

    /** Points the round is worth (before penalties) for a correct guess at [elapsedMs]. */
    fun basePoints(elapsedMs: Long, roundMs: Long, multiplier: Double): Int {
        val t = (elapsedMs.toDouble() / roundMs.toDouble()).coerceIn(0.0, 1.0)
        val share = MIN_SHARE + (1.0 - MIN_SHARE) * (1.0 - t)
        return (BASE * share * multiplier).roundToInt()
    }

    fun drawerDelta(base: Int, skips: Int, ruleBreaks: Int = 0): Int =
        base - skips * SKIP_PENALTY - ruleBreaks * RULE_BREAK_PENALTY

    fun guesserDelta(base: Int, wrongGuesses: Int): Int =
        (base - wrongGuesses * WRONG_GUESS_PENALTY).coerceAtLeast(0)
}
