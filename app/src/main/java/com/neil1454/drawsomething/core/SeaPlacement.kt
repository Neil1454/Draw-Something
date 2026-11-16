package com.neil1454.drawsomething.core

import kotlin.math.max

/**
 * The rules for moving and turning ships while you arrange your fleet, kept apart from the screen so they can be tested.
 * A ship is described by its first square (x, y), its length and whether it lies across (horizontal) or down; squares are numbered row by row.
 */
object SeaPlacement {
    /** The squares a ship would cover with its first square at (x, y), pushed back inside the sea if it would stick out. */
    fun cells(n: Int, len: Int, x: Int, y: Int, horizontal: Boolean): List<Int> {
        val px = if (horizontal) x.coerceIn(0, n - len) else x.coerceIn(0, n - 1)
        val py = if (horizontal) y.coerceIn(0, n - 1) else y.coerceIn(0, n - len)
        return List(len) { k -> if (horizontal) py * n + px + k else (py + k) * n + px }
    }

    private fun anchorOf(cells: List<Int>, n: Int) = cells.first() % n to cells.first() / n

    /** Whether a ship fully on the board with its first square at (x, y) avoids every occupied square. */
    fun fits(n: Int, len: Int, x: Int, y: Int, horizontal: Boolean, occupied: Set<Int>): Boolean {
        if (x < 0 || y < 0) return false
        if (horizontal) { if (x + len > n || y >= n) return false } else { if (y + len > n || x >= n) return false }
        return List(len) { k -> if (horizontal) y * n + x + k else (y + k) * n + x }.none { it in occupied }
    }

    /**
     * The free spot nearest to where the ship was dropped: the ship is first pushed inside the sea, and if that spot is taken it slides to the closest
     * place (same direction) where it doesn't overlap anything. Null if nowhere within [maxRadius] squares is free.
     */
    fun nearest(n: Int, len: Int, x: Int, y: Int, horizontal: Boolean, occupied: Set<Int>, maxRadius: Int = n): Pair<Int, Int>? {
        val (cx, cy) = anchorOf(cells(n, len, x, y, horizontal), n)
        for (r in 0..maxRadius) {
            var best: Pair<Int, Int>? = null
            var bestD = Int.MAX_VALUE
            for (dy in -r..r) for (dx in -r..r) {
                if (max(kotlin.math.abs(dx), kotlin.math.abs(dy)) != r) continue
                val ax = cx + dx; val ay = cy + dy
                if (!fits(n, len, ax, ay, horizontal, occupied)) continue
                val d = dx * dx + dy * dy
                if (d < bestD) { bestD = d; best = ax to ay }
            }
            if (best != null) return best
        }
        return null
    }

    /**
     * Turning a ship a quarter turn about its middle: the first square of the turned ship, and its new direction. If the turned ship would
     * overlap another or leave the sea it slides to the nearest spot where it fits, and null means there is no room.
     */
    fun turned(n: Int, len: Int, headX: Int, headY: Int, horizontal: Boolean, occupied: Set<Int>): Triple<Int, Int, Boolean>? {
        val mid = (len - 1) / 2
        val nh = !horizontal
        // the middle square stays where it is
        val cx = if (horizontal) headX + mid else headX
        val cy = if (horizontal) headY else headY + mid
        val spot = nearest(n, len, if (nh) cx - mid else cx, if (nh) cy else cy - mid, nh, occupied, 3) ?: return null
        return Triple(spot.first, spot.second, nh)
    }

    /** Moving a ship one square at a time: its new first square, or null if that would run into something or leave the sea. */
    fun nudged(n: Int, len: Int, headX: Int, headY: Int, horizontal: Boolean, dx: Int, dy: Int, occupied: Set<Int>): Pair<Int, Int>? {
        val nx = headX + dx; val ny = headY + dy
        return if (fits(n, len, nx, ny, horizontal, occupied)) nx to ny else null
    }
}
