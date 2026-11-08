package com.neil1454.drawsomething.core

/**
 * Only used to give the drawer a hint ("looks right" / "very close").
 * The drawer always makes the final call by tapping Correct or Not quite.
 */
object GuessMatcher {
    private val article = Regex("^(a|an|the)\\s+")

    fun normalize(s: String): String =
        s.lowercase().trim().replace(article, "").filter { it.isLetter() }

    fun isMatch(guess: String, word: String): Boolean {
        val g = normalize(guess)
        val w = normalize(word)
        if (g.isEmpty()) return false
        return g == w || g == w + "s" || g + "s" == w
    }

    /** Off by a single letter (typo) on a word of 4+ letters. */
    fun isClose(guess: String, word: String): Boolean {
        val g = normalize(guess)
        val w = normalize(word)
        if (w.length < 4 || g.isEmpty() || isMatch(guess, word)) return false
        return editDistance(g, w) <= 1
    }

    private fun editDistance(a: String, b: String): Int {
        var prev = IntArray(b.length + 1) { it }
        for (i in 1..a.length) {
            val cur = IntArray(b.length + 1)
            cur[0] = i
            for (j in 1..b.length) {
                val cost = if (a[i - 1] == b[j - 1]) 0 else 1
                cur[j] = minOf(prev[j] + 1, cur[j - 1] + 1, prev[j - 1] + cost)
            }
            prev = cur
        }
        return prev[b.length]
    }
}
