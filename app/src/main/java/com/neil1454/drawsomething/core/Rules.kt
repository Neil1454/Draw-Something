package com.neil1454.drawsomething.core

/** The ways the guesser can report the drawer for breaking the rules. */
enum class RuleBreak(val label: String) {
    TEXT("Used letters, words or numbers"),
    LABEL("Added a label or caption"),
    SPELLING("Spelled it out or drew initials"),
    HINTS("Gave spoken or acted-out hints"),
}

/** The house rules both players agree to before a game. Kept short on purpose. */
object Rules {
    val list: List<String> = listOf(
        "Drawing only. No letters, words or numbers in your picture, and no labels or captions. " +
            "(Numbers that are part of the object itself, like a clock face, are fine. If in doubt, leave it out.)",
        "Don't spell it out: no initials, no first letter, no letters hidden in the drawing.",
        "No spoken or acted-out hints. Let the drawing do the talking.",
        "Guessers: no peeking at the drawer's screen.",
        "Be fair. Accept right guesses, decline wrong ones, and only report a genuine rule break.",
    )

    /** Each player can report this many times per round, so reports can't be spammed. */
    const val MAX_REPORTS_PER_ROUND = 3
}

/** The "hint" helper: a few per game, shown for a few seconds. Pure so it can be tested. */
object Hints {
    const val PER_GAME = 3
    const val SHOW_MS = 6000

    /**
     * The word as underscores with some letters shown. Hint 1 shows the first letter of each word, hint 2 adds the last
     * letters, hint 3 adds roughly every third letter in between. Spaces stay spaces.
     */
    fun reveal(word: String, hintNumber: Int): String {
        val n = hintNumber.coerceIn(1, PER_GAME)
        val out = StringBuilder()
        var start = 0
        val parts = word.split(' ')
        parts.forEachIndexed { pi, part ->
            if (pi > 0) out.append(' ')
            part.forEachIndexed { i, ch ->
                val show = (i == 0) || (n >= 2 && i == part.length - 1) || (n >= 3 && i % 3 == 0)
                out.append(if (show && ch in 'a'..'z') ch else '_')
            }
            start += part.length + 1
        }
        return out.toString()
    }
}
