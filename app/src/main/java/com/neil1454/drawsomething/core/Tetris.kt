package com.neil1454.drawsomething.core

import org.json.JSONArray
import org.json.JSONObject
import kotlin.random.Random

// ====================================================================================
// Block Battle: falling blocks, side by side against a friend.
//
// Both phones play their OWN board (so the controls feel instant) from the same piece sequence,
// and show each other's board live. Clear lines and the pieces you "blow up" are sent to your
// rival as GIFT PIECES: they fall onto their stack by themselves, in a different colour so they
// can see where they came from. Last stack standing wins (or the best score in a timed match).
// ====================================================================================

object TetrisRules {
    const val W = 10
    const val H = 20
    const val HIDDEN = 2
    const val ROWS = H + HIDDEN

    /** Cell code for a block that was sent by the other player. Normal blocks are 1..7. */
    const val GIFT = 9

    const val LOCK_DELAY_MS = 500L
    const val GIFT_LOCK_MS = 130L
    const val GIFT_FALL_MS = 38L
    const val MAX_LOCK_RESETS = 15
    const val COUNTDOWN_MS = 3200L
    const val SEND_EVERY_MS = 110L
    const val RESOLVE_GRACE_MS = 600L
    const val MAX_INCOMING = 24

    /** Starting speeds offered in the lobby. */
    val LEVELS = listOf(1, 5, 9)
    val LEVEL_LABELS = listOf("Gentle", "Fast", "Frantic")

    /** Match lengths for score attack, in minutes. */
    val MINUTES = listOf(2, 3, 5)

    const val NAMES = "IOTSZJL"

    private class Def(val n: Int, val cells: List<Pair<Int, Int>>)

    private val DEFS = listOf(
        Def(4, listOf(0 to 1, 1 to 1, 2 to 1, 3 to 1)), // I
        Def(2, listOf(0 to 0, 1 to 0, 0 to 1, 1 to 1)), // O
        Def(3, listOf(1 to 0, 0 to 1, 1 to 1, 2 to 1)), // T
        Def(3, listOf(1 to 0, 2 to 0, 0 to 1, 1 to 1)), // S
        Def(3, listOf(0 to 0, 1 to 0, 1 to 1, 2 to 1)), // Z
        Def(3, listOf(0 to 0, 0 to 1, 1 to 1, 2 to 1)), // J
        Def(3, listOf(2 to 0, 0 to 1, 1 to 1, 2 to 1)), // L
    )

    /** SHAPE[type][rotation] = x0,y0,x1,y1,x2,y2,x3,y3 inside the piece's box. */
    val SHAPE: Array<Array<IntArray>> = Array(7) { t ->
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
        val table = longArrayOf(800, 720, 630, 550, 470, 380, 300, 220, 140, 100, 80, 70, 60)
        return table[(level - 1).coerceIn(0, table.size - 1)]
    }

    fun lineScore(lines: Int): Int = when (lines) { 1 -> 100; 2 -> 300; 3 -> 500; else -> 800 }

    /** How many gift pieces a clear sends: 1 line none, 2 lines one, 3 lines two, 4 lines four; long combos add one. */
    fun gifts(lines: Int, combo: Int): Int {
        val base = when (lines) { 0, 1 -> 0; 2 -> 1; 3 -> 2; else -> 4 }
        return base + if (lines >= 1 && combo >= 3) 1 else 0
    }
}

/** A gift piece: its shape, its turn, and the column of its leftmost block. */
data class Gift(val type: Int, val rot: Int, val col: Int)

class TetrisBoard(seed: Long, val startLevel: Int, private val giftsOn: Boolean) {
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
    var hold = -1
        private set
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
    val level get() = startLevel + lines / 10

    /** Counts up each time a piece lands / a new piece appears / lines are cleared, so screens and brains can react once. */
    var lockSerial = 0
        private set
    var pieceSerial = 0
        private set
    var clearSerial = 0
        private set
    var lastClear = 0
        private set
    var giftsSent = 0
        private set
    var giftsReceived = 0
        private set

    val incoming = ArrayDeque<Gift>()

    /** Gifts earned by line clears, waiting for the engine to send them. */
    val outgoing = ArrayList<Gift>()

    private val bag = Random(seed)
    private val giftRandom = Random(seed xor 0x5EEDL)
    private val queue = ArrayDeque<Int>()
    private var fallAcc = 0L
    private var lockAcc = 0L
    private var lockResets = 0
    private var holdGifts = false

    init {
        spawn()
    }

    // ------------------------------------------------------------------ looking

    private fun refill() {
        while (queue.size < 8) queue.addAll((0..6).shuffled(bag))
    }

    /** The next [n] normal pieces. */
    fun next(n: Int = 3): List<Int> {
        refill()
        return queue.take(n)
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

    // ------------------------------------------------------------------ spawning

    private fun place(t: Int, r: Int, x: Int, gift: Boolean) {
        type = t; rot = r; px = x; py = 0; isGift = gift
        fallAcc = 0; lockAcc = 0; lockResets = 0
        pieceSerial++
        if (!fits(t, r, px, py)) {
            alive = false
        }
    }

    private fun spawn() {
        if (!alive) return
        val g = if (incoming.isNotEmpty() && !holdGifts) incoming.removeFirst() else null
        if (g != null) {
            val x = (g.col - TetrisRules.minX(g.type, g.rot)).coerceIn(
                -TetrisRules.minX(g.type, g.rot),
                TetrisRules.W - 1 - TetrisRules.maxX(g.type, g.rot),
            )
            place(g.type, g.rot, x, gift = true)
        } else {
            refill()
            val t = queue.removeFirst()
            place(t, 0, (TetrisRules.W - TetrisRules.boxSize(t)) / 2, gift = false)
        }
    }

    // ------------------------------------------------------------------ moving

    private fun bumped() {
        if (grounded() && lockResets < TetrisRules.MAX_LOCK_RESETS) {
            lockAcc = 0
            lockResets++
        }
    }

    private fun controllable() = alive && type >= 0 && !isGift

    fun moveLeft(): Boolean = move(-1)
    fun moveRight(): Boolean = move(1)

    private fun move(dx: Int): Boolean {
        if (!controllable() || !fits(type, rot, px + dx, py)) return false
        px += dx
        bumped()
        return true
    }

    fun rotateCw() = rotate(1)
    fun rotateCcw() = rotate(3)

    private val kicks = listOf(0 to 0, -1 to 0, 1 to 0, 0 to -1, -2 to 0, 2 to 0, 0 to -2)

    private fun rotate(by: Int): Boolean {
        if (!controllable()) return false
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
        var d = 0
        while (fits(type, rot, px, py + 1)) { py++; d++ }
        score += 2 * d
        lock()
    }

    fun holdPiece() {
        if (!controllable() || !canHold) return
        val cur = type
        canHold = false
        if (hold < 0) {
            hold = cur
            refill()
            val t = queue.removeFirst()
            place(t, 0, (TetrisRules.W - TetrisRules.boxSize(t)) / 2, gift = false)
        } else {
            val t = hold
            hold = cur
            place(t, 0, (TetrisRules.W - TetrisRules.boxSize(t)) / 2, gift = false)
        }
    }

    /** Another player's gift pieces join the queue of things that will fall on this board. */
    fun receive(g: Gift) {
        if (!alive || incoming.size >= TetrisRules.MAX_INCOMING) return
        if (g.type !in 0..6) return
        incoming.addLast(Gift(g.type, g.rot and 3, g.col.coerceIn(0, TetrisRules.W - 1)))
        giftsReceived++
    }

    // ------------------------------------------------------------------ time

    /** Move the game forward by [dtMs]. Safe to call with any chunk size. */
    fun advance(dtMs: Long) {
        var left = dtMs.coerceIn(0, 3000)
        while (left > 0 && alive && type >= 0) {
            val step = minOf(left, 10L)
            left -= step
            if (grounded()) {
                lockAcc += step
                val limit = if (isGift) TetrisRules.GIFT_LOCK_MS else TetrisRules.LOCK_DELAY_MS
                if (lockAcc >= limit) lock()
            } else {
                lockAcc = 0
                fallAcc += step
                val interval = when {
                    isGift -> TetrisRules.GIFT_FALL_MS
                    soft -> maxOf(25L, TetrisRules.gravityMs(level) / 15)
                    else -> TetrisRules.gravityMs(level)
                }
                while (fallAcc >= interval && fits(type, rot, px, py + 1)) {
                    fallAcc -= interval
                    py++
                    if (soft && !isGift) score++
                }
                if (fallAcc >= interval) fallAcc = 0
            }
        }
    }

    private fun lock() {
        val s = TetrisRules.SHAPE[type][rot]
        val wasGift = isGift
        val code = if (wasGift) TetrisRules.GIFT else type + 1
        var anyVisible = false
        for (i in 0 until 4) {
            val cx = px + s[i * 2]
            val cy = py + s[i * 2 + 1]
            grid[cy * TetrisRules.W + cx] = code
            if (cy >= TetrisRules.HIDDEN) anyVisible = true
        }
        lockSerial++
        soft = false
        canHold = true
        val cleared = clearFullRows()
        val n = cleared.size / TetrisRules.W
        if (n > 0 && !wasGift) {
            combo++
            score += TetrisRules.lineScore(n) * level + 50 * combo * level
            lines += n
            lastClear = n
            clearSerial++
            if (giftsOn) makeGifts(n, cleared)
        } else if (n == 0 && !wasGift) {
            combo = -1
        }
        // Gifts only arrive when you did NOT just clear lines (or while a run of gifts is still landing).
        holdGifts = n > 0 && !wasGift
        type = -1
        if (!anyVisible) {
            alive = false
            return
        }
        spawn()
    }

    /** Removes complete rows and returns the codes of every block that was blown up. */
    private fun clearFullRows(): List<Int> {
        val w = TetrisRules.W
        val removed = ArrayList<Int>()
        var write = TetrisRules.ROWS - 1
        for (read in TetrisRules.ROWS - 1 downTo 0) {
            val full = (0 until w).all { grid[read * w + it] != 0 }
            if (full) {
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
        return removed
    }

    /** The pieces blown up become the gifts: the most common blown-up shapes are sent first. */
    private fun makeGifts(lines: Int, blownUp: List<Int>) {
        var count = TetrisRules.gifts(lines, combo)
        // your own pending incoming gifts are cancelled first
        while (count > 0 && incoming.isNotEmpty()) {
            incoming.removeLast()
            count--
        }
        if (count <= 0) return
        val ranking = blownUp.filter { it in 1..7 }.groupingBy { it - 1 }.eachCount()
            .entries.sortedWith(compareByDescending<Map.Entry<Int, Int>> { it.value }.thenBy { it.key }).map { it.key }
        repeat(count) { i ->
            val t = if (ranking.isEmpty()) giftRandom.nextInt(7) else ranking[i % ranking.size]
            val r = giftRandom.nextInt(4)
            val width = TetrisRules.maxX(t, r) - TetrisRules.minX(t, r) + 1
            outgoing.add(Gift(t, r, giftRandom.nextInt(TetrisRules.W - width + 1)))
        }
        giftsSent += count
    }

    // ------------------------------------------------------------------ showing

    /** For tests: put a particular piece at a particular place. */
    internal fun forcePiece(t: Int, r: Int, x: Int, y: Int, gift: Boolean = false) {
        place(t, r, x, gift)
        py = y
    }

    /** The 20 visible rows as one string: '.' empty, '1'..'7' a piece, '9' a gift block. */
    fun visibleRows(): String {
        val sb = StringBuilder(TetrisRules.H * TetrisRules.W)
        for (i in TetrisRules.HIDDEN * TetrisRules.W until grid.size) {
            val c = grid[i]
            sb.append(if (c == 0) '.' else ('0' + c))
        }
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

    /** Every sensible placement for [type], best first. */
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

    private fun evaluate(g: IntArray): Double {
        val w = TetrisRules.W
        val rows = TetrisRules.ROWS
        // remove complete lines
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
    /** 0 battle (last stack standing), 1 score attack (timed). */
    val mode: Int = 0,
    val giftsOn: Boolean = true,
    val countdownMs: Int = 0,
    /** Seconds left in score attack; -1 in battle. */
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
    private var lastClearByMe = true
    private var clearSeq = 0
    private var lastClearShown = 0
    private var mySerialSeen = 0

    // ---- host-only
    private var result = 0 // 1 host won, 2 guest won, 3 draw
    private var reason = ""

    private val playing get() = state.phase == GPhase.PLAYING && !over

    // ------------------------------------------------------------------ controls (the screen / the brain)

    private fun live(): TetrisBoard? = board.takeIf { playing && clock() >= startAt && it?.alive == true }

    fun moveLeft() { if (live()?.moveLeft() == true) touched() }
    fun moveRight() { if (live()?.moveRight() == true) touched() }
    fun rotate() { if (live()?.rotateCw() == true) touched() }
    fun rotateBack() { if (live()?.rotateCcw() == true) touched() }

    /** Called by the screen every frame (and by the 100 ms tick) so falling is smooth. */
    fun frame() {
        try {
            if (isActive) localTick(clock())
        } catch (e: Exception) {
            // never let a bad moment crash the game
        }
    }
    fun hardDrop() { live()?.let { it.hardDrop(); afterBoardChange(clock()) } }
    fun hold() { live()?.let { it.holdPiece(); touched() } }
    fun setSoft(on: Boolean) { board?.soft = on && live() != null }

    private fun touched() { lastSig = 0 } // the next tick sends the new position

    // ------------------------------------------------------------------ starting

    override fun hostBegin(opts: JSONObject) {
        mode = opts.optInt("mode", 0).coerceIn(0, 1)
        level = TetrisRules.LEVELS[opts.optInt("lvl", 0).coerceIn(0, TetrisRules.LEVELS.size - 1)]
        giftsOn = mode == 0 || opts.optBoolean("gifts", true)
        totalMs = if (mode == 1) TetrisRules.MINUTES[opts.optInt("mins", 1).coerceIn(0, TetrisRules.MINUTES.size - 1)] * 60_000L else 0L
        gid++
        seed = random.nextLong()
        result = 0; reason = ""
        newGame(clock() + TetrisRules.COUNTDOWN_MS)
    }

    private fun newGame(startsAt: Long) {
        board = TetrisBoard(seed, level, giftsOn)
        opp = null
        over = false
        startAt = startsAt
        lastAdvance = startsAt
        lastSig = 0; lastSent = -1_000_000L
        sentGifts.clear(); sentCount = 0; seenGiftId = 0
        oppScore = 0; oppLines = 0; oppLevel = level; oppIncoming = 0; oppSent = 0; oppClearSerial = 0; oppLastClear = 0
        clearSeq = 0; lastClearShown = 0; mySerialSeen = 0; lastClearByMe = true
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
        val sig = (((b.type * 31 + b.rot) * 31 + b.px) * 31 + b.py) * 31 + b.lockSerial * 7 + b.score + (if (b.alive) 1 else 0) + b.incoming.size * 13
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
            mode = mode, giftsOn = giftsOn, countdownMs = countdown, secsLeft = secsLeft,
            myScore = b.score, myLines = b.lines, myLevel = b.level, myAlive = b.alive,
            myIncoming = b.incoming.size, mySent = b.giftsSent,
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
            .put("pi", b.incoming.size).put("gs", b.giftsSent)
            .put("cs", b.clearSerial).put("lc", b.lastClear).put("gl", gifts)
    }

    /** Reads the rival's board message (from either phone). */
    private fun readRival(o: JSONObject) {
        val g = o.getString("g")
        if (g.length != TetrisRules.H * TetrisRules.W || g.any { it != '.' && it !in '1'..'7' && it != '9' }) return
        val next = o.optJSONArray("n")?.let { a -> List(minOf(a.length(), 4)) { a.optInt(it, 0).coerceIn(0, 6) } } ?: emptyList()
        opp = OppView(
            grid = g,
            type = o.optInt("t", -1).coerceIn(-1, 6), rot = o.optInt("r", 0) and 3,
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
        // gifts the rival sent me: apply each new one once
        val arr = o.optJSONArray("gl")
        if (arr != null) {
            for (i in 0 until minOf(arr.length(), 16)) {
                val e = arr.optJSONArray(i) ?: continue
                val id = e.optInt(0)
                if (id > seenGiftId && e.length() >= 4) {
                    seenGiftId = id
                    board?.receive(Gift(e.optInt(1, 0).coerceIn(0, 6), e.optInt(2, 0), e.optInt(3, 0)))
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
        if (mode == 0) {
            if (!hostDead && !guestDead) return
            result = when {
                hostDead && guestDead -> byScore
                hostDead -> 2
                else -> 1
            }
            reason = "top"
        } else {
            // Score attack: a stack that fills up just stops scoring; the match runs to the clock.
            val bothDead = hostDead && guestDead
            val timeUp = now >= endAt + TetrisRules.RESOLVE_GRACE_MS
            if (!bothDead && !timeUp) return
            result = byScore
            reason = if (bothDead) "top" else "time"
        }
        over = true
        publish()
    }

    override fun hostSnapshot(): JSONObject {
        val b = board
        val now = clock()
        val o = JSONObject()
            .put("gid", gid).put("sd", seed).put("md", mode).put("lv", level).put("go", giftsOn).put("tm", totalMs)
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
                mode = s.optInt("md", 0).coerceIn(0, 1)
                level = s.optInt("lv", 1).coerceIn(1, 20)
                giftsOn = s.optBoolean("go", true)
                totalMs = s.optLong("tm", 0).coerceIn(0, 3_600_000)
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
        val stats = "You scored ${st.myScore} with ${st.myLines} lines and sent ${st.mySent} gift pieces; ${st.theirName} scored ${st.theirScore} with ${st.theirLines} lines and sent ${st.theirSent}."
        return when {
            reason == "time" && mine == 3 -> "Time's up and the scores were level. $stats"
            reason == "time" -> "Time's up! " + (if (mine == 1) "You" else st.theirName) + " had the higher score. $stats"
            mine == 1 -> "${st.theirName}'s stack reached the top! $stats"
            mine == 2 -> "Your stack reached the top. $stats"
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
            if (!b.isGift && b.type >= 0) {
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
        if (plan == null || now < actAt || b.isGift || b.type < 0) return
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
