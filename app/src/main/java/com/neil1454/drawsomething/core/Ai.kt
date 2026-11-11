package com.neil1454.drawsomething.core

import kotlin.math.max
import kotlin.math.min
import kotlin.random.Random

// ====================================================================================
// The computer player. Each brain drives one game's engine exactly as a human on the other
// phone would: it only looks at the same state the screen shows (no peeking at hidden cards,
// ships or mines) and acts through the same calls. Pacing is human-like so you can follow it.
// ====================================================================================

enum class Skill(val label: String, val emoji: String) {
    EASY("Easy", "🙂"),
    NORMAL("Normal", "😎"),
    HARD("Hard", "🤖"),
}

abstract class Brain(protected val skill: Skill, protected val random: Random) {
    private var armedFor: Any? = null
    private var nextAt = 0L

    /** Called every ~100 ms with the current time. */
    abstract fun think(now: Long)

    /**
     * True once a "thinking" pause (min..max ms) has passed since [key] last changed. After it
     * returns true the pause is consumed, so the next decision gets a fresh pause.
     */
    protected fun ready(now: Long, key: Any?, minMs: Int, maxMs: Int): Boolean {
        if (armedFor != key) {
            armedFor = key
            nextAt = now + random.nextInt(minMs, maxMs + 1)
        }
        if (now < nextAt) return false
        armedFor = null
        return true
    }

    protected fun pct(p: Int) = random.nextInt(100) < p
}

// ====================================================================== Connect 4

class DropBrain(private val e: DropEngine, skill: Skill, random: Random) : Brain(skill, random) {
    override fun think(now: Long) {
        val s = e.state
        if (s.phase != GPhase.PLAYING || !s.myTurn) return
        if (!ready(now, s.moves, 700, 1500)) return
        e.drop(DropAi.choose(s, skill, random), 0)
    }
}

object DropAi {
    fun choose(s: DropState, skill: Skill, random: Random): Int {
        val w = s.w
        val cells = IntArray(w * s.h) { if (s.grid[it] == '.') 0 else if (s.grid[it] == s.myDisc) 1 else 2 }
        val legal = (0 until w).filter { cells[it] == 0 }
        if (legal.isEmpty()) return 0
        val rules = Rules4(w, s.h, s.connect)
        // always take a win; always block a loss (Easy only notices half the time)
        legal.firstOrNull { rules.winsAt(cells, it, 1) }?.let { return it }
        if (skill != Skill.EASY || random.nextBoolean()) legal.firstOrNull { rules.winsAt(cells, it, 2) }?.let { return it }
        if (skill == Skill.EASY && random.nextInt(100) < 35) return legal.random(random)
        val depth = when (skill) {
            Skill.EASY -> 2
            Skill.NORMAL -> 4
            Skill.HARD -> if (w * s.h > 45) 4 else 6
        }
        val order = legal.sortedBy { kotlin.math.abs(it - (w - 1) / 2.0).toInt() * 2 + (if (random.nextBoolean()) 0 else 1) }
        var best = Int.MIN_VALUE
        val bestCols = ArrayList<Int>()
        for (col in order) {
            val r = rules.row(cells, col)
            cells[r * w + col] = 1
            val score = if (rules.wins(cells, r, col)) 100_000 else rules.search(cells, depth - 1, Int.MIN_VALUE + 1, Int.MAX_VALUE - 1, false)
            cells[r * w + col] = 0
            if (score > best) { best = score; bestCols.clear() }
            if (score == best) bestCols += col
        }
        return bestCols.random(random)
    }

    private class Rules4(val w: Int, val h: Int, val n: Int) {
        private val dirs = listOf(1 to 0, 0 to 1, 1 to 1, 1 to -1)

        fun row(c: IntArray, col: Int): Int {
            for (r in h - 1 downTo 0) if (c[r * w + col] == 0) return r
            return -1
        }

        fun wins(c: IntArray, r: Int, col: Int): Boolean {
            val p = c[r * w + col]
            for ((dx, dy) in dirs) {
                var count = 1
                for (sign in intArrayOf(1, -1)) {
                    var x = col + dx * sign
                    var y = r + dy * sign
                    while (x in 0 until w && y in 0 until h && c[y * w + x] == p) {
                        count++; x += dx * sign; y += dy * sign
                    }
                }
                if (count >= n) return true
            }
            return false
        }

        /** Would [player] win by dropping in [col]? */
        fun winsAt(c: IntArray, col: Int, player: Int): Boolean {
            val r = row(c, col)
            if (r < 0) return false
            c[r * w + col] = player
            val win = wins(c, r, col)
            c[r * w + col] = 0
            return win
        }

        /** Score for player 1 (the computer), searching [depth] more moves. [maxTurn] = it's player 1's move. */
        fun search(c: IntArray, depth: Int, alpha0: Int, beta0: Int, maxTurn: Boolean): Int {
            if (depth <= 0) return eval(c)
            var alpha = alpha0
            var beta = beta0
            var best = if (maxTurn) Int.MIN_VALUE else Int.MAX_VALUE
            var any = false
            for (col in 0 until w) {
                val r = row(c, col)
                if (r < 0) continue
                any = true
                val player = if (maxTurn) 1 else 2
                c[r * w + col] = player
                val v = if (wins(c, r, col)) (if (maxTurn) 50_000 + depth else -50_000 - depth) else search(c, depth - 1, alpha, beta, !maxTurn)
                c[r * w + col] = 0
                if (maxTurn) { best = max(best, v); alpha = max(alpha, best) } else { best = min(best, v); beta = min(beta, best) }
                if (alpha >= beta) break
            }
            return if (any) best else 0
        }

        /** Count open lines: more of mine (and none of theirs) is good. */
        fun eval(c: IntArray): Int {
            var score = 0
            for (y in 0 until h) {
                if (c[y * w + w / 2] == 1) score += 3
            }
            for ((dx, dy) in dirs) {
                for (y in 0 until h) for (x in 0 until w) {
                    val ex = x + dx * (n - 1)
                    val ey = y + dy * (n - 1)
                    if (ex !in 0 until w || ey !in 0 until h) continue
                    var mine = 0
                    var theirs = 0
                    for (k in 0 until n) {
                        when (c[(y + dy * k) * w + x + dx * k]) { 1 -> mine++; 2 -> theirs++ }
                    }
                    if (mine > 0 && theirs == 0) score += weight(mine)
                    if (theirs > 0 && mine == 0) score -= weight(theirs) + (if (theirs == n - 1) 4 else 0)
                }
            }
            return score
        }

        private fun weight(count: Int) = when (count) { 1 -> 1; 2 -> 6; 3 -> 30; else -> 200 }
    }
}

// ====================================================================== Othello (Reversi)

class FlipBrain(private val e: FlipEngine, skill: Skill, random: Random) : Brain(skill, random) {
    override fun think(now: Long) {
        val s = e.state
        if (s.phase != GPhase.PLAYING || !s.myTurn || s.legal.isEmpty()) return
        if (!ready(now, s.moves, 800, 1600)) return
        e.place(FlipAi.choose(s, skill, random))
    }
}

object FlipAi {
    /** Corners are gold, squares touching corners are poison, edges are decent. */
    fun weights(n: Int): IntArray = IntArray(n * n) { i ->
        val x = i % n
        val y = i / n
        val ex = x == 0 || x == n - 1
        val ey = y == 0 || y == n - 1
        val nearX = x == 1 || x == n - 2
        val nearY = y == 1 || y == n - 2
        when {
            ex && ey -> 100
            (ex && nearY) || (ey && nearX) -> -20
            nearX && nearY -> -50
            ex || ey -> 10
            else -> 1
        }
    }

    fun choose(s: FlipState, skill: Skill, random: Random): Int {
        val n = s.n
        val me = s.myDisc - '0'
        val grid = IntArray(n * n) { if (s.grid[it] == '.') 0 else s.grid[it] - '0' }
        val moves = reversiMoves(grid, n, me)
        if (moves.isEmpty()) return s.legal.first()
        val w = weights(n)
        return when (skill) {
            Skill.EASY -> moves.keys.random(random)
            Skill.NORMAL -> moves.entries.maxByOrNull { (i, flips) -> w[i] * 3 + flips.size + random.nextInt(3) }!!.key
            Skill.HARD -> {
                var best = Int.MIN_VALUE
                var pick = moves.keys.first()
                for ((i, flips) in moves) {
                    val g = grid.copyOf()
                    g[i] = me
                    flips.forEach { g[it] = me }
                    val v = search(g, n, w, 3 - me, me, 2, Int.MIN_VALUE + 1, Int.MAX_VALUE - 1) + random.nextInt(2)
                    if (v > best) { best = v; pick = i }
                }
                pick
            }
        }
    }

    private fun eval(g: IntArray, n: Int, w: IntArray, me: Int): Int {
        var v = 0
        for (i in g.indices) if (g[i] == me) v += w[i] else if (g[i] != 0) v -= w[i]
        v += 4 * (reversiMoves(g, n, me).size - reversiMoves(g, n, 3 - me).size)
        return v
    }

    private fun search(g: IntArray, n: Int, w: IntArray, turn: Int, me: Int, depth: Int, alpha0: Int, beta0: Int): Int {
        if (depth == 0) return eval(g, n, w, me)
        val moves = reversiMoves(g, n, turn)
        if (moves.isEmpty()) {
            if (reversiMoves(g, n, 3 - turn).isEmpty()) return (g.count { it == me } - g.count { it == 3 - me }) * 50
            return search(g, n, w, 3 - turn, me, depth - 1, alpha0, beta0)
        }
        var alpha = alpha0
        var beta = beta0
        var best = if (turn == me) Int.MIN_VALUE else Int.MAX_VALUE
        for ((i, flips) in moves) {
            val g2 = g.copyOf()
            g2[i] = turn
            flips.forEach { g2[it] = turn }
            val v = search(g2, n, w, 3 - turn, me, depth - 1, alpha, beta)
            if (turn == me) { best = max(best, v); alpha = max(alpha, best) } else { best = min(best, v); beta = min(beta, best) }
            if (alpha >= beta) break
        }
        return best
    }
}

// ====================================================================== Pairs

class MemoryBrain(private val e: MemoryEngine, skill: Skill, random: Random) : Brain(skill, random) {
    private val known = HashMap<Int, Char>() // card -> symbol I remember seeing
    private val seen = HashSet<Int>()
    private var gameKey = -1

    private val recall get() = when (skill) { Skill.EASY -> 35; Skill.NORMAL -> 70; Skill.HARD -> 100 }

    override fun think(now: Long) {
        val s = e.state
        if (s.phase != GPhase.PLAYING) { known.clear(); seen.clear(); return }
        if (s.moves == 0) { known.clear(); seen.clear() }
        // look at the table: remember (some of) what's face up, forget what's been matched
        for (i in s.view.indices) {
            val c = s.view[i]
            if (c == '.') continue
            if (s.owners[i] != '.') { known.remove(i); continue }
            if (seen.add(i) && pct(recall)) known[i] = c
        }
        if (!s.myTurn || s.up.size >= 2) return
        if (!ready(now, s.moves * 10 + s.up.size, 700, 1300)) return

        val hidden = s.view.indices.filter { s.view[it] == '.' && s.owners[it] == '.' }
        if (hidden.isEmpty()) return
        val unknown = hidden.filter { it !in known }
        if (s.up.isEmpty()) {
            // a remembered pair? go for it
            val pair = known.entries.groupBy({ it.value }, { it.key }).values.firstOrNull { it.size >= 2 }
            e.flip(pair?.first() ?: (unknown.ifEmpty { hidden }).random(random))
        } else {
            val first = s.up[0]
            val sym = s.view[first]
            val partner = known.entries.firstOrNull { it.value == sym && it.key != first && s.owners[it.key] == '.' }?.key
            val options = (unknown.ifEmpty { hidden }).filter { it != first }
            e.flip(partner ?: options.ifEmpty { hidden.filter { it != first } }.random(random))
        }
    }
}

// ====================================================================== Pig

class DiceBrain(private val e: DiceEngine, skill: Skill, random: Random) : Brain(skill, random) {
    override fun think(now: Long) {
        val s = e.state
        if (s.phase != GPhase.PLAYING || !s.myTurn) return
        if (!ready(now, s.moves, 700, 1300)) return
        val base = when (skill) {
            Skill.EASY -> if (s.two) 14 else 10
            Skill.NORMAL -> if (s.two) 22 else 18
            Skill.HARD -> if (s.two) 26 else 20
        }
        var threshold = base + (if (skill == Skill.EASY) random.nextInt(-4, 5) else 0)
        // behind and they're close to winning: take more risks
        if (skill != Skill.EASY && s.theirScore >= s.target - 20 && s.myScore < s.theirScore) threshold += 12
        val winNow = s.myScore + s.held >= s.target
        if (s.held > 0 && (winNow || s.held >= threshold)) e.bank() else e.roll()
    }
}

// ====================================================================== Quick Draw

class QuickBrain(private val e: QuickEngine, skill: Skill, random: Random) : Brain(skill, random) {
    private var seq = -1
    private var step = ""
    private var plan = -1L
    private var planMs = -1

    override fun think(now: Long) {
        val s = e.state
        if (s.phase != GPhase.PLAYING) return
        if (s.seq != seq || s.step != step) {
            seq = s.seq; step = s.step; plan = -1L; planMs = -1
            when (s.step) {
                "WAIT" -> if (skill == Skill.EASY && pct(6)) plan = now + random.nextInt(300, 1200)
                "FAKE" -> if (pct(when (skill) { Skill.EASY -> 15; Skill.NORMAL -> 3; Skill.HARD -> 0 })) plan = now + random.nextInt(250, 500)
                "GO" -> {
                    planMs = when (skill) {
                        Skill.EASY -> random.nextInt(450, 750)
                        Skill.NORMAL -> random.nextInt(260, 420)
                        Skill.HARD -> random.nextInt(190, 290)
                    }
                    plan = now + planMs
                }
            }
        }
        if (plan > 0 && now >= plan && !s.iTapped) {
            plan = -1L
            if (planMs > 0) e.tap(forcedMs = planMs) else e.tap()
        }
    }
}

// ====================================================================== Minesweeper

class MinesBrain(private val e: MinesEngine, skill: Skill, random: Random) : Brain(skill, random) {
    override fun think(now: Long) {
        val s = e.state
        if (s.phase != MinesPhase.PLAYING || !s.myTurn) return
        if (!ready(now, s.moves, 900, 1600)) return
        val cell = MinesAi.choose(s.view, s.size.w, s.size.h, s.minesLeft, skill, random) ?: return
        e.pick(cell % s.size.w, cell / s.size.w)
    }
}

object MinesAi {
    /** A hidden square to reveal, using only what is visible: logic first, best odds otherwise. */
    fun choose(view: String, w: Int, h: Int, minesLeft: Int, skill: Skill, random: Random): Int? {
        val n = w * h
        val hidden = (0 until n).filter { view[it] == '.' }
        if (hidden.isEmpty()) return null
        val sloppy = when (skill) { Skill.EASY -> 35; Skill.NORMAL -> 10; Skill.HARD -> 0 }
        if (random.nextInt(100) < sloppy) return hidden.random(random)

        fun neighbours(i: Int): List<Int> {
            val x = i % w
            val y = i / w
            val out = ArrayList<Int>(8)
            for (dy in -1..1) for (dx in -1..1) {
                if (dx == 0 && dy == 0) continue
                val nx = x + dx
                val ny = y + dy
                if (nx in 0 until w && ny in 0 until h) out += ny * w + nx
            }
            return out
        }

        val knownMine = BooleanArray(n) { view[it] == 'X' || view[it] == 'x' || view[it] == 'F' }
        val inferredMine = BooleanArray(n)
        val safe = BooleanArray(n)

        var changed = true
        while (changed) {
            changed = false
            for (c in 0 until n) {
                if (view[c] !in '1'..'8') continue
                var known = 0
                val unknown = ArrayList<Int>()
                for (j in neighbours(c)) {
                    if (knownMine[j] || inferredMine[j]) known++ else if (view[j] == '.' && !safe[j]) unknown += j
                }
                if (unknown.isEmpty()) continue
                val remaining = (view[c] - '0') - known
                if (remaining <= 0) { unknown.forEach { safe[it] = true }; changed = true }
                else if (remaining == unknown.size) { unknown.forEach { inferredMine[it] = true }; changed = true }
            }
        }

        val sure = hidden.filter { safe[it] }
        if (sure.isNotEmpty()) return sure.random(random)

        // no certainty: estimate each square's chance of being a bomb
        val local = DoubleArray(n) { -1.0 }
        for (c in 0 until n) {
            if (view[c] !in '1'..'8') continue
            var known = 0
            val unknown = ArrayList<Int>()
            for (j in neighbours(c)) {
                if (knownMine[j] || inferredMine[j]) known++ else if (view[j] == '.') unknown += j
            }
            val remaining = (view[c] - '0') - known
            if (unknown.isNotEmpty() && remaining > 0) {
                val p = min(1.0, remaining.toDouble() / unknown.size)
                for (j in unknown) local[j] = max(local[j], p)
            }
        }
        val flagged = view.count { it == 'F' }
        val unaccounted = max(0, minesLeft - flagged - inferredMine.count { it })
        val free = hidden.count { local[it] < 0 && !inferredMine[it] }
        val pFree = if (free == 0) 1.0 else min(1.0, unaccounted.toDouble() / free)
        val candidates = hidden.filter { !inferredMine[it] }.ifEmpty { hidden }
        fun risk(i: Int) = if (local[i] >= 0) local[i] else pFree
        val lowest = candidates.minOf { risk(it) }
        return candidates.filter { risk(it) <= lowest + 1e-9 }.random(random)
    }
}

// ====================================================================== Battleships

class SeaBrain(private val e: SeaEngine, skill: Skill, random: Random) : Brain(skill, random) {
    override fun think(now: Long) {
        val s = e.state
        when (s.phase) {
            SeaPhase.PLACING -> if (!s.iAmReady && ready(now, "place", 1500, 3000)) e.ready()
            SeaPhase.BATTLE -> {
                if (!s.myTurn || s.awaiting) return
                if (!ready(now, s.moves, 900, 1600)) return
                val (weapon, x, y) = SeaAi.choose(s, skill, random)
                e.selectWeapon(weapon)
                e.fire(x, y)
            }
            else -> Unit
        }
    }
}

object SeaAi {
    fun choose(s: SeaState, skill: Skill, random: Random): Triple<SeaWeapon, Int, Int> {
        val w = s.size.w
        val h = s.size.h
        val g = s.enemyGrid
        val unknown = (0 until w * h).filter { g[it] == '.' }
        val hits = (0 until w * h).filter { g[it] == 'x' }
        fun xy(i: Int) = i % w to i / w

        if (skill == Skill.EASY) {
            if (hits.isNotEmpty() && random.nextInt(100) < 70) {
                val near = hits.flatMap { adjacent(it, w, h) }.filter { g[it] == '.' }
                if (near.isNotEmpty()) {
                    val pick = near.random(random)
                    return Triple(SeaWeapon.SHOT, pick % w, pick / w)
                }
            }
            val pick = unknown.random(random)
            return Triple(SeaWeapon.SHOT, pick % w, pick / w)
        }

        val density = density(s)
        // cluster bomb: once, when nothing is wounded and it covers the most promising ground
        if (s.bombLeft && hits.isEmpty() && s.shots >= 6 && random.nextInt(100) < 35) {
            var bestCell = -1
            var bestSum = -1
            for (c in 0 until w * h) {
                val (cx, cy) = xy(c)
                val plus = listOf(cx to cy, cx - 1 to cy, cx + 1 to cy, cx to cy - 1, cx to cy + 1)
                    .filter { (px, py) -> px in 0 until w && py in 0 until h }
                val sum = plus.sumOf { (px, py) -> if (g[py * w + px] == '.') density[py * w + px] else 0 }
                if (sum > bestSum) { bestSum = sum; bestCell = c }
            }
            if (bestCell >= 0) return Triple(SeaWeapon.BOMB, bestCell % w, bestCell / w)
        }
        val top = unknown.maxOf { density[it] }
        // Normal sometimes just fires somewhere sensible; Hard always takes the best square
        val pool = if (skill == Skill.NORMAL && hits.isEmpty() && random.nextInt(100) < 25) unknown.filter { ((it % w) + (it / w)) % 2 == 0 }.ifEmpty { unknown }
        else unknown.filter { density[it] >= top }
        val pick = pool.random(random)
        return Triple(SeaWeapon.SHOT, pick % w, pick / w)
    }

    private fun adjacent(i: Int, w: Int, h: Int): List<Int> {
        val x = i % w
        val y = i / w
        return listOf(x - 1 to y, x + 1 to y, x to y - 1, x to y + 1)
            .filter { (px, py) -> px in 0 until w && py in 0 until h }.map { (px, py) -> py * w + px }
    }

    /** How many ways could the ships still afloat fit through each unknown square (hits count extra). */
    fun density(s: SeaState): IntArray {
        val w = s.size.w
        val h = s.size.h
        val g = s.enemyGrid
        val remaining = s.size.fleet.sortedDescending().toMutableList()
        // work out which ships are already sunk from the connected '#' groups
        val seen = BooleanArray(w * h)
        for (start in 0 until w * h) {
            if (g[start] != '#' || seen[start]) continue
            var size = 0
            val stack = ArrayDeque<Int>()
            stack.add(start); seen[start] = true
            while (stack.isNotEmpty()) {
                val c = stack.removeLast()
                size++
                for (nb in adjacent(c, w, h)) if (g[nb] == '#' && !seen[nb]) { seen[nb] = true; stack.add(nb) }
            }
            if (!remaining.remove(size)) {
                // two sunk ships touching: take off the biggest ship that fits
                remaining.firstOrNull { it <= size }?.let { remaining.remove(it) }
            }
        }
        val anyHit = g.any { it == 'x' }
        val d = IntArray(w * h)
        for (len in remaining) {
            for (horizontal in listOf(true, false)) {
                val maxX = if (horizontal) w - len else w - 1
                val maxY = if (horizontal) h - 1 else h - len
                for (y in 0..maxY) for (x in 0..maxX) {
                    var ok = true
                    var covered = 0
                    for (k in 0 until len) {
                        val c = if (horizontal) y * w + x + k else (y + k) * w + x
                        if (g[c] == 'o' || g[c] == '#') { ok = false; break }
                        if (g[c] == 'x') covered++
                    }
                    if (!ok) continue
                    val weight = if (anyHit) (if (covered > 0) 25 * covered else 1) else 1
                    for (k in 0 until len) {
                        val c = if (horizontal) y * w + x + k else (y + k) * w + x
                        if (g[c] == '.') d[c] += weight
                    }
                }
            }
        }
        return d
    }
}

// ====================================================================== Draw Something (pretend guesser)

/**
 * In solo practice the computer plays the guesser. It can't read a drawing, so it pretends: a while
 * after you start drawing it makes a few wrong guesses (fewer on Hard), then names the word.
 */
class DrawBrain(
    private val e: GameEngine,
    private val secret: () -> String?,
    skill: Skill,
    random: Random,
) : Brain(skill, random) {
    private var startedAt = 0L
    private var guessAt = 0L
    private var wrongLeft = 0

    override fun think(now: Long) {
        val s = e.state
        when (s.phase) {
            Phase.RESULT -> if (ready(now, "r${s.round}", 1500, 3500)) e.ready()
            Phase.GUESSING -> {
                if (e.board.strokes.isEmpty()) return
                if (startedAt == 0L) {
                    startedAt = now
                    wrongLeft = when (skill) { Skill.EASY -> 2; Skill.NORMAL -> 1; Skill.HARD -> 0 }
                    guessAt = now + when (skill) {
                        Skill.EASY -> random.nextInt(30_000, 50_000)
                        Skill.NORMAL -> random.nextInt(18_000, 32_000)
                        Skill.HARD -> random.nextInt(10_000, 20_000)
                    }
                }
                if (s.awaitingVerdict || now < guessAt) return
                val word = secret() ?: return
                if (wrongLeft > 0) {
                    wrongLeft--
                    val wrong = Words.all.filter { it.category == s.category && it.text != word }.ifEmpty { Words.all }.random(random)
                    e.submitGuess(wrong.text)
                    guessAt = now + random.nextInt(4_000, 8_000)
                } else {
                    e.submitGuess(word)
                }
            }
            else -> startedAt = 0L
        }
    }
}
