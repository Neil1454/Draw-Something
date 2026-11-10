package com.neil1454.drawsomething.core

import org.json.JSONArray
import org.json.JSONObject
import kotlin.random.Random

// ====================================================================================
// Tetris, head to head, side by side.
//
// Both phones play their OWN board (so the controls feel instant) from the same piece sequence,
// and show each other's board live. Clear lines and the pieces you "blow up" are sent to your
// rival as GIFT PIECES: they join the rival's queue, in a different colour so they
// can see where they came from. There are seven game modes (see [TetrisMode]).
// ====================================================================================

/** The game modes. The number is what travels over the wire and is stored in settings. */
object TetrisMode {
    const val BATTLE = 0
    const val SCORE = 1
    const val SPRINT = 2
    const val BOMB = 3
    const val TIDE = 4
    const val TURBO = 5
    const val SURGE = 6
    const val COUNT = 7

    class Info(val id: Int, val emoji: String, val label: String, val blurb: String)

    val ALL = listOf(
        Info(BATTLE, "⚔️", "Battle", "Last stack standing wins. Cleared pieces are sent to your rival as gift pieces."),
        Info(SCORE, "⏱", "Score attack", "Best score when the clock runs out wins. Gifts optional."),
        Info(SPRINT, "🏁", "Sprint", "Race to clear the lines first. A pure speed duel."),
        Info(BOMB, "💣", "Bomb Blitz", "Every 6th piece is a BOMB. Its blast clears blocks and the blown-up blocks are sent to your rival."),
        Info(TIDE, "🌊", "Rising Tide", "Junk rows rise from the bottom on both boards. Clear faster than the water climbs."),
        Info(TURBO, "🔥", "Turbo", "The game speeds up every 25 seconds, whatever you do. How long can you hang on?"),
        Info(SURGE, "⚡", "Power Surge", "Star blocks! Clear a row with a star to launch a power: dizzy, turbo or fog your rival, send double gifts, or clean up."),
    )

    fun info(id: Int): Info = ALL[id.coerceIn(0, COUNT - 1)]

    /** Modes where the host chooses whether gift pieces are on. The others always have them. */
    fun giftsOptional(id: Int) = id == SCORE || id == SPRINT
}

object TetrisRules {
    const val W = 10
    const val H = 20
    const val HIDDEN = 2
    const val ROWS = H + HIDDEN

    /** Piece type 7 is the bomb (Bomb Blitz). It is never written to the board. */
    const val BOMB_TYPE = 7

    /** Cell codes: 1..7 pieces, 8 junk (Rising Tide), 9 gift, 17..23 a piece with a star. */
    const val JUNK = 8
    const val GIFT = 9
    const val STAR = 16

    const val LOCK_DELAY_MS = 500L
    /**
     * Gifts are ordinary pieces that you place yourself. After they are sent they wait in a meter for this long
     * (you can cancel them by clearing lines), then they join your next-piece queue one at a time.
     */
    const val GIFT_DELAY_MS = 3_000L
    const val MAX_LOCK_RESETS = 15
    const val COUNTDOWN_MS = 3200L
    const val SEND_EVERY_MS = 110L
    const val RESOLVE_GRACE_MS = 600L
    const val MAX_INCOMING = 12

    const val BOMB_EVERY = 6
    const val BLAST_RADIUS = 2
    const val STAR_EVERY = 5
    const val TIDE_MS = 12_000L
    const val TURBO_MS = 25_000L

    /** Overtime ("margin time" in other versus games): after this long the game speeds up every few seconds, so a match always ends. */
    const val OVERTIME_START_MS = 5 * 60_000L
    const val OVERTIME_STEP_MS = 20_000L

    /** Curses travel as "gifts" with these types. */
    const val CURSE_DIZZY = 10
    const val CURSE_TURBO = 11
    const val CURSE_FOG = 12
    const val DIZZY_MS = 8_000L
    const val TURBO_CURSE_MS = 8_000L
    const val FOG_MS = 10_000L

    /** Power names, for the power-up popup. */
    val POWERS = listOf("DOUBLE GIFT", "DIZZY", "TURBO CURSE", "FOG", "CLEAN UP")

    /** Starting speeds offered in the lobby. */
    val LEVELS = listOf(1, 5, 9)
    val LEVEL_LABELS = listOf("Gentle", "Fast", "Frantic")

    /** Match lengths for score attack, in minutes. */
    val MINUTES = listOf(2, 3, 5)

    /** Lines to race to in Sprint. */
    val LINE_TARGETS = listOf(20, 40, 60)

    private class Def(val n: Int, val cells: List<Pair<Int, Int>>)

    private val DEFS = listOf(
        Def(4, listOf(0 to 1, 1 to 1, 2 to 1, 3 to 1)), // I
        Def(2, listOf(0 to 0, 1 to 0, 0 to 1, 1 to 1)), // O
        Def(3, listOf(1 to 0, 0 to 1, 1 to 1, 2 to 1)), // T
        Def(3, listOf(1 to 0, 2 to 0, 0 to 1, 1 to 1)), // S
        Def(3, listOf(0 to 0, 1 to 0, 1 to 1, 2 to 1)), // Z
        Def(3, listOf(0 to 0, 0 to 1, 1 to 1, 2 to 1)), // J
        Def(3, listOf(2 to 0, 0 to 1, 1 to 1, 2 to 1)), // L
        Def(1, listOf(0 to 0, 0 to 0, 0 to 0, 0 to 0)), // the bomb: one square
    )

    /** SHAPE[type][rotation] = x0,y0,x1,y1,x2,y2,x3,y3 inside the piece's box. */
    val SHAPE: Array<Array<IntArray>> = Array(DEFS.size) { t ->
        val d = DEFS[t]
        var cur = d.cells
        Array(4) {
            val out = IntArray(8)
            cur.forEachIndexed { i, (x, y) -> out[i * 2] = x; out[i * 2 + 1] = y }
            cur = cur.map { (x, y) -> (d.n - 1 - y) to x } // rotate clockwise inside the box
            out
        }
    }

    fun boxSize(type: Int) = DEFS[type].n

    fun minX(type: Int, rot: Int): Int = (0 until 4).minOf { SHAPE[type][rot and 3][it * 2] }
    fun maxX(type: Int, rot: Int): Int = (0 until 4).maxOf { SHAPE[type][rot and 3][it * 2] }

    /** Milliseconds for a piece to fall one row. */
    fun gravityMs(level: Int): Long {
        val table = longArrayOf(800, 720, 630, 550, 470, 380, 300, 220, 140, 100, 80, 70, 60, 50, 42, 36)
        return table[(level - 1).coerceIn(0, table.size - 1)]
    }

    fun lineScore(lines: Int): Int = when (lines) { 1 -> 100; 2 -> 300; 3 -> 500; else -> 800 }

    /** Two charge units make one gift piece. Whatever is left over stays in the meter for next time. */
    const val UNITS_PER_GIFT = 2

    /** Most gift pieces a single clear can send. */
    const val MAX_GIFTS_PER_CLEAR = 8

    /**
     * Charge a clear puts in your gift meter: a single line 1, a double 3, a triple 5, four lines 10; each step of a
     * combo adds one more and a back-to-back four-line clear adds two. So even singles send gifts, every other line.
     */
    fun chargeFor(lines: Int, combo: Int, backToBack: Boolean): Int {
        val base = when (lines) { 0 -> 0; 1 -> 1; 2 -> 3; 3 -> 5; else -> 10 }
        if (lines <= 0) return 0
        return base + combo.coerceAtLeast(0) + if (backToBack) 2 else 0
    }

    /** The piece shape (0..6) a cell code belongs to, or -1 for junk and gift blocks. */
    fun kindOf(code: Int): Int = when (code) {
        in 1..7 -> code - 1
        in 17..23 -> code - 17
        else -> -1
    }

    fun isStar(code: Int) = code in 17..23

    /** One character per cell, for sending a board: '.' empty, '1'-'7', '8' junk, '9' gift, 'a'-'g' a piece with a star. */
    fun charOf(code: Int): Char = when (code) {
        0 -> '.'
        in 1..9 -> '0' + code
        in 17..23 -> 'a' + (code - 17)
        else -> '.'
    }

    fun codeOf(c: Char): Int = when (c) {
        in '1'..'9' -> c - '0'
        in 'a'..'g' -> 17 + (c - 'a')
        else -> 0
    }

    fun validBoardChar(c: Char) = c == '.' || c in '1'..'9' || c in 'a'..'g'
}

/** A gift piece: its shape, its turn, and the column of its leftmost block. Types 10+ are curses. */
data class Gift(val type: Int, val rot: Int, val col: Int)

class TetrisBoard(
    private val seed: Long,
    val startLevel: Int,
    private val giftsOn: Boolean,
    val mode: Int = TetrisMode.BATTLE,
) {
    val grid = IntArray(TetrisRules.ROWS * TetrisRules.W)

    var type = -1
        private set
    var rot = 0
        private set
    var px = 0
        private set
    var py = 0
        private set
    var isGift = false
        private set

    /** Which of the falling piece's four blocks carries a star (Power Surge), or -1. */
    var starCell = -1
        private set
    var hold = -1
        private set
    private var holdStar = -1
    var canHold = true
        private set
    var soft = false
    var alive = true
        private set

    var score = 0
        private set
    var lines = 0
        private set
    var combo = -1
        private set
    var maxCombo = 0
        private set
    var tetrises = 0
        private set
    var perfectClears = 0
        private set
    var piecesPlaced = 0
        private set
    var elapsedMs = 0L
        private set

    /** The level: from the start choice, from lines cleared, and (Turbo) from time. */
    val level: Int
        get() = startLevel + lines / 10 +
            (if (mode == TetrisMode.TURBO) (elapsedMs / TetrisRules.TURBO_MS).toInt() else 0) +
            overtimeSteps

    /** True once a no-clock match has gone on so long that the game starts speeding up on its own. */
    val overtime: Boolean get() = mode != TetrisMode.SCORE && elapsedMs >= TetrisRules.OVERTIME_START_MS

    private val overtimeSteps: Int
        get() = if (overtime) ((elapsedMs - TetrisRules.OVERTIME_START_MS) / TetrisRules.OVERTIME_STEP_MS).toInt() + 1 else 0

    // Counters that go up once per event, so screens and brains react exactly once.
    var lockSerial = 0
        private set
    var pieceSerial = 0
        private set
    var clearSerial = 0
        private set
    var lastClear = 0
        private set

    /** The visible rows (0 = top) that the last clear removed, for the flash. */
    var lastClearRows: List<Int> = emptyList()
        private set
    var b2bSerial = 0
        private set
    var perfectSerial = 0
        private set
    var blastSerial = 0
        private set
    var blastX = 0
        private set
    var blastY = 0
        private set
    var powerSerial = 0
        private set
    var lastPower = -1
        private set
    var fxSerial = 0
        private set
    var lastFx = -1
        private set
    var giftsSent = 0
        private set
    var giftsReceived = 0
        private set
    var tideRows = 0
        private set

    /** The gift meter: how many charge units you have towards the next gift piece (0 until [TetrisRules.UNITS_PER_GIFT]). */
    var charge = 0
        private set

    /** A block that was just cleared (visible position and what it was), so the screen can burst it into pieces. */
    class ClearedBlock(val x: Int, val y: Int, val code: Int)

    var lastClearBlocks: List<ClearedBlock> = emptyList()
        private set

    /** The last hard drop: columns x0..x1, from row y0 down to row y1 (visible rows), for the trail. */
    var dropSerial = 0
        private set
    var dropX0 = 0
        private set
    var dropX1 = 0
        private set
    var dropY0 = 0
        private set
    var dropY1 = 0
        private set

    // Curses currently on me (milliseconds left).
    var dizzyMs = 0L
        private set
    var turboMs = 0L
        private set
    var fogMs = 0L
        private set

    private var tideAcc = 0L
    val tideLeftMs: Long get() = if (mode == TetrisMode.TIDE) TetrisRules.TIDE_MS - tideAcc else 0L

    /** Gifts that are in the meter: they can still be cancelled by clearing lines. Each has the time it becomes "next". */
    val pendingGifts = ArrayDeque<Pair<Gift, Long>>()

    /** Gifts that have arrived: they are in the queue and will appear as your next pieces (one every other piece). */
    val incoming = ArrayDeque<Gift>()

    /** Everything on its way to me. */
    val waiting: Int get() = pendingGifts.size + incoming.size

    /** Gifts and curses earned, waiting for the engine to send them. */
    val outgoing = ArrayList<Gift>()

    private val bag = Random(seed)
    private val giftRandom = Random(seed xor 0x5EEDL)
    private val tideRandom = Random(seed xor 0x71DEL)
    private val powerRandom = Random(seed xor 0x90E4L)
    private val queue = ArrayDeque<Int>()
    private val bagLeft = ArrayList<Int>()
    private var generated = 0
    private var spawned = 0
    private var fallAcc = 0L
    private var lockAcc = 0L
    private var lockResets = 0
    private var holdGifts = false
    private var lastSpawnWasGift = false
    private var lastWasTetris = false
    private val kicks = listOf(0 to 0, -1 to 0, 1 to 0, 0 to -1, -2 to 0, 2 to 0, 0 to -2)

    init {
        spawn()
    }

    // ------------------------------------------------------------------ looking

    private fun nextFromBag(): Int {
        if (bagLeft.isEmpty()) bagLeft.addAll((0..6).shuffled(bag))
        return bagLeft.removeAt(0)
    }

    private fun refill() {
        while (queue.size < 8) {
            val n = generated++
            queue.addLast(if (mode == TetrisMode.BOMB && n % TetrisRules.BOMB_EVERY == TetrisRules.BOMB_EVERY - 1) TetrisRules.BOMB_TYPE else nextFromBag())
        }
    }

    /** The next [n] pieces (7 means a bomb). */
    fun next(n: Int = 3): List<Int> {
        refill()
        return queue.take(n)
    }

    /** The next [n] pieces as the player will actually get them, with gifts in their place (type, isGift). */
    fun upcoming(n: Int = 3): List<Pair<Int, Boolean>> {
        refill()
        val normals = queue.toList()
        val gifts = incoming.toList()
        val out = ArrayList<Pair<Int, Boolean>>()
        var gi = 0
        var ni = 0
        var lastGift = lastSpawnWasGift
        while (out.size < n) {
            if (gi < gifts.size && !lastGift && !holdGifts) {
                out.add(gifts[gi++].type to true)
                lastGift = true
            } else {
                if (ni >= normals.size) break
                out.add(normals[ni++] to false)
                lastGift = false
            }
        }
        return out
    }

    fun cell(x: Int, y: Int) = grid[y * TetrisRules.W + x]

    private fun fits(t: Int, r: Int, x: Int, y: Int): Boolean {
        val s = TetrisRules.SHAPE[t][r and 3]
        for (i in 0 until 4) {
            val cx = x + s[i * 2]
            val cy = y + s[i * 2 + 1]
            if (cx < 0 || cx >= TetrisRules.W || cy < 0 || cy >= TetrisRules.ROWS) return false
            if (grid[cy * TetrisRules.W + cx] != 0) return false
        }
        return true
    }

    private fun grounded() = type < 0 || !fits(type, rot, px, py + 1)

    /** Where the piece would land if dropped now (the "ghost"). */
    fun ghostY(): Int {
        if (type < 0) return py
        var y = py
        while (fits(type, rot, px, y + 1)) y++
        return y
    }

    /** The leftmost block's column. */
    fun leftCol(): Int = if (type < 0) 0 else px + TetrisRules.minX(type, rot)

    /** The strongest curse still on me: 0 dizzy, 1 turbo, 2 fog, or -1. */
    fun activeCurse(): Int = when {
        dizzyMs > 0 -> 0
        turboMs > 0 -> 1
        fogMs > 0 -> 2
        else -> -1
    }

    // ------------------------------------------------------------------ spawning

    private fun place(t: Int, r: Int, x: Int, gift: Boolean, star: Int = -1) {
        type = t; rot = r; px = x; py = 0; isGift = gift; starCell = star
        fallAcc = 0; lockAcc = 0; lockResets = 0
        pieceSerial++
        if (!fits(t, r, px, py)) {
            alive = false
        }
    }

    private fun spawn() {
        if (!alive) return
        // A gift is played like any other piece (same speed, full control), at most every other piece.
        val g = if (incoming.isNotEmpty() && !holdGifts && !lastSpawnWasGift) incoming.removeFirst() else null
        lastSpawnWasGift = g != null
        if (g != null) {
            place(g.type, 0, (TetrisRules.W - TetrisRules.boxSize(g.type)) / 2, gift = true)
        } else {
            refill()
            val t = queue.removeFirst()
            val k = spawned++
            val star = if (mode == TetrisMode.SURGE && t != TetrisRules.BOMB_TYPE && k % TetrisRules.STAR_EVERY == TetrisRules.STAR_EVERY - 1) {
                Random((seed xor 0x57A7L) + k).nextInt(4)
            } else -1
            place(t, 0, (TetrisRules.W - TetrisRules.boxSize(t)) / 2, gift = false, star = star)
        }
    }

    // ------------------------------------------------------------------ moving

    private fun bumped() {
        if (grounded() && lockResets < TetrisRules.MAX_LOCK_RESETS) {
            lockAcc = 0
            lockResets++
        }
    }

    private fun controllable() = alive && type >= 0

    fun moveLeft(): Boolean = move(if (dizzyMs > 0) 1 else -1)
    fun moveRight(): Boolean = move(if (dizzyMs > 0) -1 else 1)

    private fun move(dx: Int): Boolean {
        if (!controllable() || !fits(type, rot, px + dx, py)) return false
        px += dx
        bumped()
        return true
    }

    fun rotateCw() = rotate(1)
    fun rotateCcw() = rotate(3)

    private fun rotate(by: Int): Boolean {
        if (!controllable()) return false
        if (type == TetrisRules.BOMB_TYPE) return true // a bomb looks the same any way up
        val r = (rot + by) and 3
        for ((dx, dy) in kicks) {
            if (fits(type, r, px + dx, py + dy)) {
                rot = r; px += dx; py += dy
                bumped()
                return true
            }
        }
        return false
    }

    fun hardDrop() {
        if (!controllable()) return
        val s = TetrisRules.SHAPE[type][rot]
        val topBefore = py + (0 until 4).minOf { s[it * 2 + 1] } - TetrisRules.HIDDEN
        var d = 0
        while (fits(type, rot, px, py + 1)) { py++; d++ }
        score += 2 * d
        if (d > 0) {
            dropX0 = px + TetrisRules.minX(type, rot)
            dropX1 = px + TetrisRules.maxX(type, rot)
            dropY0 = topBefore
            dropY1 = py + (0 until 4).maxOf { s[it * 2 + 1] } - TetrisRules.HIDDEN
            dropSerial++
        }
        lock()
    }

    /** 0..1: how close the landed piece is to locking in place (it glows as this grows). */
    val lockFraction: Float
        get() = if (type >= 0 && grounded()) (lockAcc.toFloat() / TetrisRules.LOCK_DELAY_MS).coerceIn(0f, 1f) else 0f

    fun holdPiece() {
        if (!controllable() || !canHold || type == TetrisRules.BOMB_TYPE || isGift) return
        val cur = type
        val curStar = starCell
        canHold = false
        val t: Int
        val star: Int
        if (hold < 0) {
            refill()
            t = queue.removeFirst()
            star = -1
        } else {
            t = hold
            star = holdStar
        }
        hold = cur
        holdStar = curStar
        place(t, 0, (TetrisRules.W - TetrisRules.boxSize(t)) / 2, gift = false, star = star)
    }

    /** Another player's gift pieces (or a curse) arrive. */
    fun receive(g: Gift) {
        if (!alive) return
        when (g.type) {
            TetrisRules.CURSE_DIZZY -> { dizzyMs = TetrisRules.DIZZY_MS; lastFx = 0; fxSerial++ }
            TetrisRules.CURSE_TURBO -> { turboMs = TetrisRules.TURBO_CURSE_MS; lastFx = 1; fxSerial++ }
            TetrisRules.CURSE_FOG -> { fogMs = TetrisRules.FOG_MS; lastFx = 2; fxSerial++ }
            in 0..6 -> {
                if (waiting >= TetrisRules.MAX_INCOMING) return
                pendingGifts.addLast(Gift(g.type, g.rot and 3, g.col.coerceIn(0, TetrisRules.W - 1)) to (elapsedMs + TetrisRules.GIFT_DELAY_MS))
                giftsReceived++
            }
            else -> Unit
        }
    }

    // ------------------------------------------------------------------ time

    /** Move the game forward by [dtMs]. Safe to call with any chunk size. */
    fun advance(dtMs: Long) {
        var left = dtMs.coerceIn(0, 3000)
        while (left > 0 && alive) {
            val step = minOf(left, 10L)
            left -= step
            elapsedMs += step
            if (dizzyMs > 0) dizzyMs = maxOf(0, dizzyMs - step)
            if (turboMs > 0) turboMs = maxOf(0, turboMs - step)
            if (fogMs > 0) fogMs = maxOf(0, fogMs - step)
            if (mode == TetrisMode.TIDE) {
                tideAcc += step
                if (tideAcc >= TetrisRules.TIDE_MS) {
                    tideAcc -= TetrisRules.TIDE_MS
                    rise()
                    if (!alive) break
                }
            }
            while (pendingGifts.isNotEmpty() && pendingGifts.first().second <= elapsedMs) incoming.addLast(pendingGifts.removeFirst().first)
            if (type < 0) continue
            if (grounded()) {
                lockAcc += step
                if (lockAcc >= TetrisRules.LOCK_DELAY_MS) lock()
            } else {
                lockAcc = 0
                fallAcc += step
                val base = TetrisRules.gravityMs(level)
                val interval = when {
                    soft -> maxOf(25L, base / 15)
                    turboMs > 0 -> maxOf(20L, base / 3)
                    else -> base
                }
                while (fallAcc >= interval && fits(type, rot, px, py + 1)) {
                    fallAcc -= interval
                    py++
                    if (soft) score++
                }
                if (fallAcc >= interval) fallAcc = 0
            }
        }
    }

    /** Rising Tide: the whole stack moves up one row and a junk row with one gap appears at the bottom. */
    private fun rise() {
        val w = TetrisRules.W
        if ((0 until w).any { grid[it] != 0 }) {
            alive = false
            return
        }
        for (y in 0 until TetrisRules.ROWS - 1) for (x in 0 until w) grid[y * w + x] = grid[(y + 1) * w + x]
        val gap = tideRandom.nextInt(w)
        for (x in 0 until w) grid[(TetrisRules.ROWS - 1) * w + x] = if (x == gap) 0 else TetrisRules.JUNK
        tideRows++
        if (type >= 0 && !fits(type, rot, px, py)) {
            var tries = 0
            while (tries < 3 && py > 0 && !fits(type, rot, px, py)) { py--; tries++ }
            if (!fits(type, rot, px, py)) alive = false
        }
        lockAcc = 0
    }

    private fun lock() {
        val wasGift = isGift
        val isBomb = type == TetrisRules.BOMB_TYPE
        var anyVisible = true
        var bombBlown: List<Int> = emptyList()
        if (isBomb) {
            bombBlown = explode()
        } else {
            val s = TetrisRules.SHAPE[type][rot]
            anyVisible = false
            for (i in 0 until 4) {
                val cx = px + s[i * 2]
                val cy = py + s[i * 2 + 1]
                var code = if (wasGift) TetrisRules.GIFT else type + 1
                if (!wasGift && i == starCell) code += TetrisRules.STAR
                grid[cy * TetrisRules.W + cx] = code
                if (cy >= TetrisRules.HIDDEN) anyVisible = true
            }
        }
        lockSerial++
        piecesPlaced++
        soft = false
        canHold = true
        val cleared = clearFullRows()
        val n = cleared.rows.size
        if (n > 0) {
            combo++
            if (combo > maxCombo) maxCombo = combo
            var pts = TetrisRules.lineScore(n) * level + 50 * combo * level
            var backToBack = false
            val tetris = n == 4
            if (tetris && lastWasTetris) {
                pts = pts * 3 / 2
                backToBack = true
                b2bSerial++
            }
            lastWasTetris = tetris
            if (tetris) tetrises++
            score += pts
            lines += n
            lastClear = n
            lastClearRows = cleared.rows.map { it - TetrisRules.HIDDEN }.filter { it >= 0 }
            lastClearBlocks = buildList {
                cleared.rows.forEachIndexed { i, row ->
                    val y = row - TetrisRules.HIDDEN
                    if (y >= 0) for (x in 0 until TetrisRules.W) add(ClearedBlock(x, y, cleared.codes[i * TetrisRules.W + x]))
                }
            }
            clearSerial++
            if (giftsOn) addCharge(TetrisRules.chargeFor(n, combo, backToBack), cleared.codes)
            if (grid.all { it == 0 }) {
                perfectClears++
                perfectSerial++
                score += 1000 * level
                if (giftsOn) addCharge(8, cleared.codes)
            }
            if (mode == TetrisMode.SURGE) {
                repeat(minOf(2, cleared.codes.count { TetrisRules.isStar(it) })) { earnPower() }
            }
        } else if (n == 0) {
            combo = -1
        }
        if (isBomb && bombBlown.isNotEmpty() && giftsOn) {
            makeGifts(minOf(6, bombBlown.count { TetrisRules.kindOf(it) >= 0 } / 3), bombBlown)
        }
        // Gifts only arrive when you did NOT just clear lines (or while a run of gifts is still landing).
        holdGifts = n > 0
        type = -1
        if (!anyVisible) {
            alive = false
            return
        }
        spawn()
    }

    /** The bomb lands: everything within the blast is removed, then blocks above fall into the gaps. */
    private fun explode(): List<Int> {
        val w = TetrisRules.W
        val r = TetrisRules.BLAST_RADIUS
        val blown = ArrayList<Int>()
        for (dy in -r..r) for (dx in -r..r) {
            if (kotlin.math.abs(dx) + kotlin.math.abs(dy) > r) continue
            val x = px + dx
            val y = py + dy
            if (x < 0 || x >= w || y < 0 || y >= TetrisRules.ROWS) continue
            val v = grid[y * w + x]
            if (v != 0) {
                blown.add(v)
                grid[y * w + x] = 0
            }
        }
        blastX = px
        blastY = py - TetrisRules.HIDDEN
        blastSerial++
        score += 10 * blown.size
        for (x in 0 until w) {
            var write = TetrisRules.ROWS - 1
            for (y in TetrisRules.ROWS - 1 downTo 0) {
                val v = grid[y * w + x]
                if (v != 0) {
                    grid[write * w + x] = v
                    if (write != y) grid[y * w + x] = 0
                    write--
                }
            }
        }
        return blown
    }

    private class Cleared(val codes: List<Int>, val rows: List<Int>)

    /** Removes complete rows and reports which rows went and every block that was blown up. */
    private fun clearFullRows(): Cleared {
        val w = TetrisRules.W
        val removed = ArrayList<Int>()
        val rows = ArrayList<Int>()
        var write = TetrisRules.ROWS - 1
        for (read in TetrisRules.ROWS - 1 downTo 0) {
            val full = (0 until w).all { grid[read * w + it] != 0 }
            if (full) {
                rows.add(read)
                for (x in 0 until w) removed.add(grid[read * w + x])
            } else {
                if (write != read) for (x in 0 until w) grid[write * w + x] = grid[read * w + x]
                write--
            }
        }
        while (write >= 0) {
            for (x in 0 until w) grid[write * w + x] = 0
            write--
        }
        return Cleared(removed, rows)
    }

    /** Adds to the gift meter and sends a gift for every full [TetrisRules.UNITS_PER_GIFT] units. */
    private fun addCharge(units: Int, blownUp: List<Int>) {
        charge += units
        val gifts = charge / TetrisRules.UNITS_PER_GIFT
        charge -= gifts * TetrisRules.UNITS_PER_GIFT
        if (gifts > 0) makeGifts(minOf(gifts, TetrisRules.MAX_GIFTS_PER_CLEAR), blownUp)
    }

    /** The pieces blown up become the gifts: the most common blown-up shapes are sent first. */
    private fun makeGifts(wanted: Int, blownUp: List<Int>) {
        var count = wanted
        // gifts still in your meter are cancelled first (the oldest first), as in other versus games
        while (count > 0 && pendingGifts.isNotEmpty()) {
            pendingGifts.removeFirst()
            count--
        }
        if (count <= 0) return
        val ranking = blownUp.map { TetrisRules.kindOf(it) }.filter { it >= 0 }.groupingBy { it }.eachCount()
            .entries.sortedWith(compareByDescending<Map.Entry<Int, Int>> { it.value }.thenBy { it.key }).map { it.key }
        repeat(count) { i ->
            val t = if (ranking.isEmpty()) giftRandom.nextInt(7) else ranking[i % ranking.size]
            val r = giftRandom.nextInt(4)
            val width = TetrisRules.maxX(t, r) - TetrisRules.minX(t, r) + 1
            outgoing.add(Gift(t, r, giftRandom.nextInt(TetrisRules.W - width + 1)))
        }
        giftsSent += count
    }

    /** Power Surge: a row with a star was cleared. */
    private fun earnPower() {
        val roll = powerRandom.nextInt(100)
        val power = when {
            roll < 25 -> 0
            roll < 50 -> 1
            roll < 70 -> 2
            roll < 85 -> 3
            else -> 4
        }
        lastPower = power
        powerSerial++
        when (power) {
            0 -> makeGifts(2, emptyList())
            1 -> outgoing.add(Gift(TetrisRules.CURSE_DIZZY, 0, 0))
            2 -> outgoing.add(Gift(TetrisRules.CURSE_TURBO, 0, 0))
            3 -> outgoing.add(Gift(TetrisRules.CURSE_FOG, 0, 0))
            else -> cleanUp(2)
        }
    }

    /** Remove the bottom [rows] rows and let everything above settle down. */
    private fun cleanUp(rows: Int) {
        val w = TetrisRules.W
        for (y in TetrisRules.ROWS - 1 downTo rows) for (x in 0 until w) grid[y * w + x] = grid[(y - rows) * w + x]
        for (y in 0 until rows) for (x in 0 until w) grid[y * w + x] = 0
    }

    // ------------------------------------------------------------------ showing

    /** For tests: put a particular piece at a particular place. */
    internal fun forcePiece(t: Int, r: Int, x: Int, y: Int, gift: Boolean = false, star: Int = -1) {
        place(t, r, x, gift, star)
        py = y
    }

    /** For tests: pretend lines have been cleared. */
    internal fun debugSetLines(n: Int) { lines = n }

    /** The 20 visible rows as one string (see [TetrisRules.charOf]). */
    fun visibleRows(): String {
        val sb = StringBuilder(TetrisRules.H * TetrisRules.W)
        for (i in TetrisRules.HIDDEN * TetrisRules.W until grid.size) sb.append(TetrisRules.charOf(grid[i]))
        return sb.toString()
    }

    fun copyGrid(): IntArray = grid.copyOf()
}

/** The computer's way of choosing where a piece goes. */
object TetrisAi {
    class Plan(val rot: Int, val col: Int, val value: Double)

    private fun fitsOn(g: IntArray, t: Int, r: Int, x: Int, y: Int): Boolean {
        val s = TetrisRules.SHAPE[t][r and 3]
        for (i in 0 until 4) {
            val cx = x + s[i * 2]
            val cy = y + s[i * 2 + 1]
            if (cx < 0 || cx >= TetrisRules.W || cy < 0 || cy >= TetrisRules.ROWS) return false
            if (g[cy * TetrisRules.W + cx] != 0) return false
        }
        return true
    }

    /** Every sensible placement for a normal piece, best first. */
    fun rank(grid: IntArray, type: Int): List<Plan> {
        val w = TetrisRules.W
        val plans = ArrayList<Plan>()
        val seen = HashSet<String>()
        for (r in 0 until 4) {
            val s = TetrisRules.SHAPE[type][r]
            val key = (0 until 4).map { s[it * 2] to s[it * 2 + 1] }.sortedBy { it.first * 10 + it.second }.toString()
            if (!seen.add(key)) continue
            val lo = TetrisRules.minX(type, r)
            val hi = TetrisRules.maxX(type, r)
            for (px in -lo..(w - 1 - hi)) {
                var y = 0
                if (!fitsOn(grid, type, r, px, y)) continue
                while (fitsOn(grid, type, r, px, y + 1)) y++
                val g = grid.copyOf()
                for (i in 0 until 4) g[(y + s[i * 2 + 1]) * w + px + s[i * 2]] = 1
                plans.add(Plan(r, px + lo, evaluate(g)))
            }
        }
        return plans.sortedByDescending { it.value }
    }

    /** The best column to drop a bomb: the one whose blast would remove the most blocks. */
    fun bombColumn(grid: IntArray): Int {
        val w = TetrisRules.W
        var best = w / 2
        var bestCount = -1
        for (x in 0 until w) {
            var y = 0
            if (grid[y * w + x] != 0) continue
            while (y + 1 < TetrisRules.ROWS && grid[(y + 1) * w + x] == 0) y++
            var count = 0
            val r = TetrisRules.BLAST_RADIUS
            for (dy in -r..r) for (dx in -r..r) {
                if (kotlin.math.abs(dx) + kotlin.math.abs(dy) > r) continue
                val cx = x + dx
                val cy = y + dy
                if (cx in 0 until w && cy in 0 until TetrisRules.ROWS && grid[cy * w + cx] != 0) count++
            }
            if (count > bestCount) { bestCount = count; best = x }
        }
        return best
    }

    private fun evaluate(g: IntArray): Double {
        val w = TetrisRules.W
        val rows = TetrisRules.ROWS
        var full = 0
        val rest = ArrayList<IntArray>()
        for (y in 0 until rows) {
            val row = IntArray(w) { g[y * w + it] }
            if (row.all { it != 0 }) full++ else rest.add(row)
        }
        val heights = IntArray(w)
        var holes = 0
        for (x in 0 until w) {
            var seenBlock = false
            for (i in rest.indices) {
                val filled = rest[i][x] != 0
                if (filled && !seenBlock) {
                    seenBlock = true
                    heights[x] = rows - full - i
                } else if (!filled && seenBlock) {
                    holes++
                }
            }
        }
        var bump = 0
        for (x in 0 until w - 1) bump += kotlin.math.abs(heights[x] - heights[x + 1])
        return -0.51 * heights.sum() + 0.76 * full - 0.36 * holes - 0.18 * bump
    }
}

// ====================================================================================
// Between the two phones
// ====================================================================================

/** What the other player's board looks like right now. */
class OppView(
    val grid: String,
    val type: Int,
    val rot: Int,
    val x: Int,
    val y: Int,
    val gift: Boolean,
    val hold: Int,
    val next: List<Int>,
    val alive: Boolean,
)

data class TetrisState(
    val myName: String,
    val theirName: String,
    val phase: GPhase = GPhase.LOBBY,
    /** See [TetrisMode]. */
    val mode: Int = 0,
    val giftsOn: Boolean = true,
    /** Sprint: the number of lines to race to (0 in other modes). */
    val target: Int = 0,
    val countdownMs: Int = 0,
    /** Seconds left in score attack; -1 otherwise. */
    val secsLeft: Int = -1,
    val myScore: Int = 0,
    val theirScore: Int = 0,
    val myLines: Int = 0,
    val theirLines: Int = 0,
    val myLevel: Int = 1,
    val theirLevel: Int = 1,
    val myAlive: Boolean = true,
    val theirAlive: Boolean = true,
    /** Gift pieces waiting to fall on me / on them. */
    val myIncoming: Int = 0,
    val theirIncoming: Int = 0,
    /** How many of mine have already arrived in my queue (the rest are still in the meter and can be cancelled). */
    val myQueued: Int = 0,
    val mySent: Int = 0,
    val theirSent: Int = 0,
    /** Count of line clears; changes when anyone clears lines. */
    val clearSeq: Int = 0,
    val lastClear: Int = 0,
    val lastClearByMe: Boolean = true,
    /** Gifts I have received so far (changes when a new one arrives). */
    val giftSeq: Int = 0,
    val result: Int = 0,
    val how: String = "",
)

class TetrisEngine(
    myName: String,
    theirName: String,
    isHost: Boolean,
    send: (GMsg) -> Unit,
    random: Random = Random.Default,
    clock: () -> Long = { System.currentTimeMillis() },
) : SyncEngine(GameKind.TETRIS, myName, theirName, isHost, send, random, clock) {

    var state = TetrisState(myName, theirName)
        private set
    override val stateAny: Any get() = state
    override val phase get() = state.phase

    /** My live board (null before a game starts). Screens draw it every frame. */
    var board: TetrisBoard? = null
        private set

    /** The other player's board as of their last update. */
    var opp: OppView? = null
        private set

    // ---- both phones
    private var gid = 0
    private var seed = 0L
    private var mode = 0
    private var level = 1
    private var giftsOn = true
    private var target = 0
    private var totalMs = 0L
    private var startAt = 0L
    private var lastAdvance = 0L
    private var over = false
    private var lastSig = 0
    private var lastSent = -1_000_000L
    private val sentGifts = ArrayList<IntArray>() // id, type, rot, col (what I have sent)
    private var sentCount = 0
    private var seenGiftId = 0 // highest gift id received from the rival
    private var oppScore = 0
    private var oppLines = 0
    private var oppLevel = 1
    private var oppIncoming = 0
    private var oppSent = 0
    private var oppClearSerial = 0
    private var oppLastClear = 0
    private var clearSeq = 0
    private var lastClearShown = 0
    private var lastClearByMe = true
    private var mySerialSeen = 0

    // ---- host-only
    private var result = 0 // 1 host won, 2 guest won, 3 draw
    private var reason = ""

    private val playing get() = state.phase == GPhase.PLAYING && !over

    // ------------------------------------------------------------------ controls (the screen / the brain)

    private fun live(): TetrisBoard? = board.takeIf { playing && clock() >= startAt && it?.alive == true }

    /** These return true when the piece really moved or turned. */
    fun moveLeft(): Boolean = (live()?.moveLeft() == true).also { if (it) touched() }
    fun moveRight(): Boolean = (live()?.moveRight() == true).also { if (it) touched() }
    fun rotate(): Boolean = (live()?.rotateCw() == true).also { if (it) touched() }
    fun rotateBack(): Boolean = (live()?.rotateCcw() == true).also { if (it) touched() }

    /** Called by the screen every frame (and by the 100 ms tick) so falling is smooth. */
    fun frame() {
        try {
            if (isActive) localTick(clock())
        } catch (e: Exception) {
            // never let a bad moment crash the game
        }
    }

    fun hardDrop() { live()?.let { it.hardDrop(); afterBoardChange(clock()) } }
    fun hold(): Boolean = live()?.let { it.holdPiece(); touched(); true } ?: false
    fun setSoft(on: Boolean) { board?.soft = on && live() != null }

    private fun touched() { lastSig = 0 } // the next tick sends the new position

    // ------------------------------------------------------------------ starting

    override fun hostBegin(opts: JSONObject) {
        mode = opts.optInt("mode", 0).coerceIn(0, TetrisMode.COUNT - 1)
        level = TetrisRules.LEVELS[opts.optInt("lvl", 0).coerceIn(0, TetrisRules.LEVELS.size - 1)]
        giftsOn = if (TetrisMode.giftsOptional(mode)) opts.optBoolean("gifts", mode == TetrisMode.SCORE) else true
        totalMs = if (mode == TetrisMode.SCORE) TetrisRules.MINUTES[opts.optInt("mins", 1).coerceIn(0, TetrisRules.MINUTES.size - 1)] * 60_000L else 0L
        target = if (mode == TetrisMode.SPRINT) TetrisRules.LINE_TARGETS[opts.optInt("tl", 1).coerceIn(0, TetrisRules.LINE_TARGETS.size - 1)] else 0
        gid++
        seed = random.nextLong()
        result = 0; reason = ""
        newGame(clock() + TetrisRules.COUNTDOWN_MS)
    }

    private fun newGame(startsAt: Long) {
        board = TetrisBoard(seed, level, giftsOn, mode)
        opp = null
        over = false
        startAt = startsAt
        lastAdvance = startsAt
        lastSig = 0; lastSent = -1_000_000L
        sentGifts.clear(); sentCount = 0; seenGiftId = 0
        oppScore = 0; oppLines = 0; oppLevel = level; oppIncoming = 0; oppSent = 0; oppClearSerial = 0; oppLastClear = 0
        clearSeq = 0; lastClearShown = 0; lastClearByMe = true; mySerialSeen = 0
    }

    // ------------------------------------------------------------------ the clock

    override fun localTick(now: Long) {
        val b = board ?: return
        if (over) return
        if (now < startAt) {
            refreshState(now)
            return
        }
        val endAt = if (totalMs > 0) startAt + totalMs else Long.MAX_VALUE
        if (b.alive && lastAdvance < endAt) {
            val upTo = minOf(now, endAt)
            if (upTo > lastAdvance) {
                b.advance(upTo - lastAdvance)
                lastAdvance = upTo
            }
        }
        afterBoardChange(now)
        if (isHost) maybeResolve(now, endAt)
    }

    /** After anything happened on my board: collect gifts, tell the rival, update the screen state. */
    private fun afterBoardChange(now: Long) {
        val b = board ?: return
        if (b.outgoing.isNotEmpty()) {
            for (g in b.outgoing) {
                sentCount++
                sentGifts.add(intArrayOf(sentCount, g.type, g.rot, g.col))
            }
            b.outgoing.clear()
            while (sentGifts.size > 16) sentGifts.removeAt(0)
            lastSig = 0
        }
        if (b.clearSerial != mySerialSeen) {
            mySerialSeen = b.clearSerial
            clearSeq++
            lastClearShown = b.lastClear
            lastClearByMe = true
        }
        val sig = ((((b.type * 31 + b.rot) * 31 + b.px) * 31 + b.py) * 31 + b.lockSerial * 7 + b.score + (if (b.alive) 1 else 0) + b.waiting * 13 + b.tideRows * 17)
        if (sig != lastSig && now - lastSent >= TetrisRules.SEND_EVERY_MS) {
            lastSig = sig
            lastSent = now
            if (isHost) publish() else act(JSONObject().put("a", "st").put("gid", gid).put("b", boardJson(b)))
        } else if (!b.alive && now - lastSent >= TetrisRules.SEND_EVERY_MS * 4) {
            lastSent = now
            if (isHost) publish() else act(JSONObject().put("a", "st").put("gid", gid).put("b", boardJson(b)))
        }
        refreshState(now)
    }

    private fun refreshState(now: Long) {
        val b = board ?: return
        val o = opp
        val countdown = (startAt - now).coerceAtLeast(0).toInt()
        val secsLeft = if (totalMs > 0) ((startAt + totalMs - now).coerceAtLeast(0) / 1000).toInt() else -1
        val s = state
        val ns = s.copy(
            phase = if (over) GPhase.OVER else GPhase.PLAYING,
            mode = mode, giftsOn = giftsOn, target = target, countdownMs = countdown, secsLeft = secsLeft,
            myScore = b.score, myLines = b.lines, myLevel = b.level, myAlive = b.alive,
            myIncoming = b.waiting, myQueued = b.incoming.size, mySent = b.giftsSent,
            theirScore = oppScore, theirLines = oppLines, theirLevel = oppLevel, theirAlive = o?.alive ?: true,
            theirIncoming = oppIncoming, theirSent = oppSent,
            clearSeq = clearSeq, lastClear = lastClearShown, lastClearByMe = lastClearByMe,
            giftSeq = b.giftsReceived,
        )
        if (ns != s) {
            state = ns
            onChange()
        }
    }

    // ------------------------------------------------------------------ talking to the rival

    private fun boardJson(b: TetrisBoard): JSONObject {
        val gifts = JSONArray()
        sentGifts.forEach { gifts.put(JSONArray(it.toList())) }
        return JSONObject()
            .put("g", b.visibleRows()).put("t", b.type).put("r", b.rot).put("x", b.px).put("y", b.py)
            .put("gf", b.isGift).put("h", b.hold).put("n", JSONArray(b.next(3)))
            .put("sc", b.score).put("ln", b.lines).put("lv", b.level).put("al", b.alive)
            .put("pi", b.waiting).put("gs", b.giftsSent)
            .put("cs", b.clearSerial).put("lc", b.lastClear).put("gl", gifts)
    }

    /** Reads the rival's board message (from either phone). */
    private fun readRival(o: JSONObject) {
        val g = o.getString("g")
        if (g.length != TetrisRules.H * TetrisRules.W || !g.all { TetrisRules.validBoardChar(it) }) return
        val next = o.optJSONArray("n")?.let { a -> List(minOf(a.length(), 4)) { a.optInt(it, 0).coerceIn(0, 7) } } ?: emptyList()
        opp = OppView(
            grid = g,
            type = o.optInt("t", -1).coerceIn(-1, 7), rot = o.optInt("r", 0) and 3,
            x = o.optInt("x", 0).coerceIn(-4, 14), y = o.optInt("y", 0).coerceIn(-4, 30),
            gift = o.optBoolean("gf", false), hold = o.optInt("h", -1).coerceIn(-1, 6), next = next,
            alive = o.optBoolean("al", true),
        )
        oppScore = o.optInt("sc", 0).coerceIn(0, 100_000_000)
        oppLines = o.optInt("ln", 0).coerceIn(0, 100_000)
        oppLevel = o.optInt("lv", 1).coerceIn(1, 100)
        oppIncoming = o.optInt("pi", 0).coerceIn(0, TetrisRules.MAX_INCOMING)
        oppSent = o.optInt("gs", 0).coerceIn(0, 100_000)
        val cs = o.optInt("cs", 0)
        if (cs != oppClearSerial) {
            oppClearSerial = cs
            oppLastClear = o.optInt("lc", 0).coerceIn(0, 4)
            if (cs > 0) {
                clearSeq++
                lastClearShown = oppLastClear
                lastClearByMe = false
            }
        }
        // gifts and curses the rival sent me: apply each new one once
        val arr = o.optJSONArray("gl")
        if (arr != null) {
            for (i in 0 until minOf(arr.length(), 16)) {
                val e = arr.optJSONArray(i) ?: continue
                val id = e.optInt(0)
                if (id > seenGiftId && e.length() >= 4) {
                    seenGiftId = id
                    board?.receive(Gift(e.optInt(1, 0).coerceIn(0, 12), e.optInt(2, 0), e.optInt(3, 0)))
                }
            }
        }
    }

    // ------------------------------------------------------------------ the host's job

    override fun hostHandle(fromHost: Boolean, a: JSONObject) {
        if (fromHost || a.getString("a") != "st" || a.getInt("gid") != gid || over) return
        readRival(a.getJSONObject("b"))
        val now = clock()
        refreshState(now)
        maybeResolve(now, if (totalMs > 0) startAt + totalMs else Long.MAX_VALUE)
    }

    private fun maybeResolve(now: Long, endAt: Long) {
        val b = board ?: return
        val o = opp
        val guestDead = o != null && !o.alive
        val hostDead = !b.alive
        val byScore = if (b.score > oppScore) 1 else if (b.score < oppScore) 2 else 3
        when (mode) {
            TetrisMode.SCORE -> {
                // A stack that fills up just stops scoring; the match runs to the clock.
                val bothDead = hostDead && guestDead
                val timeUp = now >= endAt + TetrisRules.RESOLVE_GRACE_MS
                if (!bothDead && !timeUp) return
                result = byScore
                reason = if (bothDead) "top" else "time"
            }
            TetrisMode.SPRINT -> {
                val hostDone = b.lines >= target
                val guestDone = oppLines >= target
                if (!hostDone && !guestDone && !hostDead && !guestDead) return
                result = when {
                    hostDone && guestDone -> byScore
                    hostDone -> 1
                    guestDone -> 2
                    hostDead && guestDead -> byScore
                    hostDead -> 2
                    else -> 1
                }
                reason = if (hostDone || guestDone) "lines" else "top"
            }
            else -> {
                if (!hostDead && !guestDead) return
                result = when {
                    hostDead && guestDead -> byScore
                    hostDead -> 2
                    else -> 1
                }
                reason = "top"
            }
        }
        over = true
        publish()
    }

    override fun hostSnapshot(): JSONObject {
        val b = board
        val now = clock()
        val o = JSONObject()
            .put("gid", gid).put("sd", seed).put("md", mode).put("lv", level).put("go", giftsOn).put("tm", totalMs).put("tg", target)
            .put("cd", (startAt - now).coerceAtLeast(0)).put("ov", over).put("rs", result).put("why", reason)
        if (b != null) o.put("b", boardJson(b))
        return o
    }

    // ------------------------------------------------------------------ the guest's job

    override fun adopt(s: JSONObject) {
        val newGid = s.getInt("gid")
        if (!isHost) {
            if (newGid == gid && board == null) return // a stale message from a game I have already left
            if (newGid != gid) {
                gid = newGid
                seed = s.getLong("sd")
                mode = s.optInt("md", 0).coerceIn(0, TetrisMode.COUNT - 1)
                level = s.optInt("lv", 1).coerceIn(1, 20)
                giftsOn = s.optBoolean("go", true)
                totalMs = s.optLong("tm", 0).coerceIn(0, 3_600_000)
                target = s.optInt("tg", 0).coerceIn(0, 200)
                newGame(clock() + s.optLong("cd", 0).coerceIn(0, 10_000))
            }
            s.optJSONObject("b")?.let { readRival(it) }
            if (s.optBoolean("ov", false)) {
                over = true
                result = s.optInt("rs", 0).coerceIn(0, 3)
                reason = s.optString("why", "").take(8)
            }
        }
        if (board == null) return
        val now = clock()
        refreshState(now)
        if (over) {
            val mine = if (isHost) result else when (result) { 1 -> 2; 2 -> 1; else -> result }
            state = state.copy(phase = GPhase.OVER, result = mine, how = howText(mine))
        }
    }

    private fun howText(mine: Int): String {
        val st = state
        val b = board
        val extras = if (b != null) " Best combo ${if (b.lines > 0) b.maxCombo + 1 else 0}, ${b.tetrises} four-line clears, ${b.perfectClears} perfect clears." else ""
        val stats = "You scored ${st.myScore} with ${st.myLines} lines and sent ${st.mySent} gift pieces; ${st.theirName} scored ${st.theirScore} with ${st.theirLines} lines and sent ${st.theirSent}.$extras"
        val modeName = TetrisMode.info(mode).label
        return when {
            reason == "lines" -> (if (mine == 1) "You were" else "${st.theirName} was") + " first to clear $target lines in $modeName! $stats"
            reason == "time" && mine == 3 -> "Time's up and the scores were level. $stats"
            reason == "time" -> "Time's up! " + (if (mine == 1) "You" else st.theirName) + " had the higher score. $stats"
            mine == 1 -> "${st.theirName}'s stack reached the top in $modeName! $stats"
            mine == 2 -> "Your stack reached the top in $modeName. $stats"
            else -> "Both stacks reached the top, and the scores were level. $stats"
        }
    }

    override fun reset() {
        board = null
        opp = null
        over = false
        state = state.copy(phase = GPhase.LOBBY, result = 0, how = "", countdownMs = 0)
    }
}

/** The computer's falling-block player. */
class TetrisBrain(
    private val e: TetrisEngine,
    skill: Skill,
    random: Random,
) : Brain(skill, random) {
    private var serial = -1
    private var plan: TetrisAi.Plan? = null
    private var actAt = 0L

    override fun think(now: Long) {
        val s = e.state
        val b = e.board ?: return
        if (s.phase != GPhase.PLAYING || s.countdownMs > 0 || !b.alive) return
        if (b.pieceSerial != serial) {
            serial = b.pieceSerial
            plan = null
            if (b.type == TetrisRules.BOMB_TYPE) {
                plan = TetrisAi.Plan(0, TetrisAi.bombColumn(b.copyGrid()), 0.0)
            } else if (b.type >= 0) {
                val ranked = TetrisAi.rank(b.copyGrid(), b.type)
                if (ranked.isNotEmpty()) {
                    val slip = when (skill) { Skill.EASY -> 28; Skill.NORMAL -> 8; Skill.HARD -> 0 }
                    val top = when (skill) { Skill.EASY -> 6; Skill.NORMAL -> 3; Skill.HARD -> 1 }
                    plan = if (pct(slip)) ranked[random.nextInt(minOf(top, ranked.size))] else ranked[0]
                }
            }
            actAt = now + when (skill) {
                Skill.EASY -> random.nextInt(550, 1000)
                Skill.NORMAL -> random.nextInt(220, 450)
                Skill.HARD -> random.nextInt(60, 160)
            }
        }
        if (plan == null || now < actAt || b.type < 0) return
        val actions = when (skill) { Skill.EASY -> 1; Skill.NORMAL -> 2; Skill.HARD -> 3 }
        repeat(actions) {
            val target = plan ?: return
            when {
                b.rot != target.rot -> e.rotate()
                b.leftCol() < target.col -> e.moveRight()
                b.leftCol() > target.col -> e.moveLeft()
                else -> { e.hardDrop(); plan = null; return }
            }
        }
        actAt = now + when (skill) { Skill.EASY -> 200; Skill.NORMAL -> 100; Skill.HARD -> 40 }
    }
}
