package com.neil1454.drawsomething.core

/** Plain-English "how and where" text for the end-of-game screens. Pure Kotlin so it can be tested. */
object WinStory {
    private val numberWords = mapOf(2 to "Two", 3 to "Three", 4 to "Four", 5 to "Five", 6 to "Six", 7 to "Seven")

    /**
     * Where a Drop Four line is, e.g. "Four in a row across row 2 from the bottom, columns 3 to 6".
     * [line] holds the winning cells (row by row from the top-left) and [w]/[h] the board size.
     */
    fun dropLine(line: List<Int>, w: Int, h: Int): String {
        if (line.size < 2 || w <= 0 || h <= 0) return ""
        val cells = line.filter { it in 0 until w * h }.sorted()
        if (cells.size < 2) return ""
        val first = cells.first()
        val last = cells.last()
        val r1 = first / w
        val r2 = last / w
        val c1 = first % w
        val c2 = last % w
        val count = numberWords[cells.size] ?: "${cells.size}"
        fun fromBottom(row: Int) = h - row
        return when {
            r1 == r2 -> "$count in a row across row ${fromBottom(r1)} from the bottom, columns ${minOf(c1, c2) + 1} to ${maxOf(c1, c2) + 1}"
            c1 == c2 -> "$count in a row straight up column ${c1 + 1}, from row ${fromBottom(r2)} to row ${fromBottom(r1)} (counting from the bottom)"
            else -> {
                // the cells run from the top (first) to the bottom (last)
                val dir = if (c2 > c1) "sloping down to the right" else "sloping down to the left"
                "$count in a row diagonally $dir, from column ${c1 + 1} (row ${fromBottom(r1)}) to column ${c2 + 1} (row ${fromBottom(r2)})"
            }
        }
    }

    /** "by 12 points" (or "by 1 point"). */
    fun by(winner: Int, loser: Int, unit: String): String {
        val d = kotlin.math.abs(winner - loser)
        return "by $d $unit" + if (d == 1) "" else "s"
    }

    /** Who the headline is about, for text like "You won" / "Teri won". */
    fun who(result: Int, theirName: String): String = when (result) { 1 -> "You"; 2 -> theirName; else -> "Nobody" }
}
