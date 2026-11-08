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
