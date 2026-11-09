package com.neil1454.drawsomething.core

import org.json.JSONArray
import org.json.JSONObject
import kotlin.random.Random

// ====================================================================================
// Drop Four: connect four. Take turns dropping a disc into a column; first to line up the
// required number in a row, column or diagonal wins.
//
// POWER mode gives each player two one-shot power discs:
//   💣 Bomb   - after landing it blasts every disc around it (3x3), then the columns settle.
//   ⏩ Double - you drop again straight away.
// ====================================================================================

enum class DropSize(val label: String, val w: Int, val h: Int, val connect: Int) {
    CLASSIC("Classic 7×6 · connect 4", 7, 6, 4),
    MEGA("Mega 9×7 · connect 5", 9, 7, 5),
}

data class DropState(
    val myName: String,
    val theirName: String,
    val phase: GPhase = GPhase.LOBBY,
    val w: Int = 7,
    val h: Int = 6,
    val connect: Int = 4,
    val power: Boolean = false,
    /** Disc colour theme: 0 classic, 1 neon, 2 sunset (cosmetic). */
    val theme: Int = 0,
    /** Row by row from the top: '.' empty, '1' host's disc, '2' partner's disc. */
    val grid: String = "",
    val myDisc: Char = '1',
    val myTurn: Boolean = false,
    /** 0 playing, 1 I won, 2 they won, 3 draw. */
    val result: Int = 0,
    val line: List<Int> = emptyList(),
    val last: Int = -1,
    val removed: List<Int> = emptyList(),
    val myBomb: Boolean = false,
    val myDouble: Boolean = false,
    val theirBomb: Boolean = false,
    val theirDouble: Boolean = false,
    val moves: Int = 0,
)

class DropEngine(
    myName: String,
    theirName: String,
    isHost: Boolean,
    send: (GMsg) -> Unit,
    random: Random = Random.Default,
    clock: () -> Long = { System.currentTimeMillis() },
) : SyncEngine(GameKind.DROP, myName, theirName, isHost, send, random, clock) {

    var state = DropState(myName, theirName, myDisc = if (isHost) '1' else '2')
        private set
    override val stateAny: Any get() = state
    override val phase get() = state.phase

    // ---- host-only model
    private var w = 7
    private var h = 6
    private var connect = 4
    private var power = false
    private var theme = 0
    private var grid = IntArray(0)
    private var turn = 1 // 1 = host, 2 = partner
    private val bombs = BooleanArray(3)
    private val doubles = BooleanArray(3)
    private var result = 0 // 0 playing, 1 host won, 2 partner won, 3 draw
    private var line: List<Int> = emptyList()
    private var last = -1
    private var removed: List<Int> = emptyList()
    private var moves = 0

    override fun hostBegin(opts: JSONObject) {
        val size = DropSize.values()[opts.optInt("size", 0).coerceIn(0, DropSize.values().size - 1)]
        w = size.w; h = size.h; connect = size.connect
        power = opts.optBoolean("power", false)
        theme = opts.optInt("theme", 0).coerceIn(0, 2)
        grid = IntArray(w * h)
        turn = if (random.nextBoolean()) 1 else 2
        bombs.fill(power); doubles.fill(power)
        result = 0; line = emptyList(); last = -1; removed = emptyList(); moves = 0
    }

    /** Pick a column (and optionally a power disc). [power]: 0 normal, 1 bomb, 2 double. */
    fun drop(column: Int, powerDisc: Int = 0) {
        act(JSONObject().put("c", column).put("p", powerDisc))
    }

    override fun hostHandle(fromHost: Boolean, a: JSONObject) {
        if (result != 0 || state.phase != GPhase.PLAYING) return
        val who = if (fromHost) 1 else 2
        if (turn != who) return
        val c = a.getInt("c")
        val p = a.optInt("p", 0)
        if (c !in 0 until w) return
        if (p == 1 && !(power && bombs[who])) return
        if (p == 2 && !(power && doubles[who])) return
        if (p !in 0..2) return
        // lowest empty row in the column
        var r = -1
        for (row in h - 1 downTo 0) if (grid[row * w + c] == 0) { r = row; break }
        if (r < 0) return

        grid[r * w + c] = who
        var landed = r * w + c
        removed = emptyList()
        if (p == 1) {
            bombs[who] = false
            val gone = ArrayList<Int>()
            for (dy in -1..1) for (dx in -1..1) {
                val nx = c + dx
                val ny = r + dy
                if ((dx != 0 || dy != 0) && nx in 0 until w && ny in 0 until h && grid[ny * w + nx] != 0) {
                    gone += ny * w + nx
                    grid[ny * w + nx] = 0
                }
            }
            removed = gone
            landed = settle(landed)
        }
        if (p == 2) doubles[who] = false
        last = landed
        moves++

        // winner?
        val mine = findLine(who)
        val theirs = findLine(3 - who)
        when {
            mine.isNotEmpty() -> { result = who; line = mine }
            theirs.isNotEmpty() -> { result = 3 - who; line = theirs }
            grid.none { it == 0 } -> { result = 3; line = emptyList() }
            else -> line = emptyList()
        }
        if (result == 0 && p != 2) turn = 3 - who
        publish()
    }

    /** Let discs fall after a blast. Returns where the disc that was at [track] ended up. */
    private fun settle(track: Int): Int {
        var tracked = track
        for (col in 0 until w) {
            var write = h - 1
            for (row in h - 1 downTo 0) {
                val v = grid[row * w + col]
                if (v != 0) {
                    if (row * w + col == track) tracked = write * w + col
                    grid[row * w + col] = 0
                    grid[write * w + col] = v
                    write--
                }
            }
        }
        return tracked
    }

    /** The cells of a winning line for [who], or empty. */
    private fun findLine(who: Int): List<Int> {
        val dirs = listOf(1 to 0, 0 to 1, 1 to 1, 1 to -1)
        for (y in 0 until h) for (x in 0 until w) {
            if (grid[y * w + x] != who) continue
            for ((dx, dy) in dirs) {
                val cells = ArrayList<Int>()
                var cx = x
                var cy = y
                while (cx in 0 until w && cy in 0 until h && grid[cy * w + cx] == who) {
                    cells += cy * w + cx
                    cx += dx; cy += dy
                }
                if (cells.size >= connect) return cells
            }
        }
        return emptyList()
    }

    override fun hostSnapshot(): JSONObject {
        val sb = StringBuilder(w * h)
        for (v in grid) sb.append(if (v == 0) '.' else ('0' + v))
        return JSONObject().put("w", w).put("h", h).put("n", connect).put("pw", power).put("th", theme)
            .put("g", sb.toString()).put("turn", turn).put("res", result)
            .put("line", JSONArray(line)).put("last", last).put("rem", JSONArray(removed))
            .put("b1", bombs[1]).put("b2", bombs[2]).put("d1", doubles[1]).put("d2", doubles[2]).put("mv", moves)
    }

    override fun adopt(s: JSONObject) {
        val nw = s.getInt("w").coerceIn(3, 12)
        val nh = s.getInt("h").coerceIn(3, 12)
        val g = s.getString("g")
        if (g.length != nw * nh || g.any { it !in ".12" }) return
        val me = if (isHost) 1 else 2
        val res = s.getInt("res").coerceIn(0, 3)
        val t = s.getInt("turn")
        state = state.copy(
            phase = if (res == 0) GPhase.PLAYING else GPhase.OVER,
            w = nw, h = nh, connect = s.getInt("n").coerceIn(3, 8), power = s.getBoolean("pw"),
            theme = s.optInt("th", 0).coerceIn(0, 2),
            grid = g, myTurn = res == 0 && t == me,
            result = when (res) { 0 -> 0; 3 -> 3; me -> 1; else -> 2 },
            line = s.ints("line", 12).filter { it in 0 until nw * nh },
            last = s.optInt("last", -1).coerceIn(-1, nw * nh - 1),
            removed = s.ints("rem", 12).filter { it in 0 until nw * nh },
            myBomb = s.getBoolean(if (isHost) "b1" else "b2"), theirBomb = s.getBoolean(if (isHost) "b2" else "b1"),
            myDouble = s.getBoolean(if (isHost) "d1" else "d2"), theirDouble = s.getBoolean(if (isHost) "d2" else "d1"),
            moves = s.optInt("mv", 0).coerceIn(0, 100_000),
        )
    }

    override fun reset() {
        state = state.copy(phase = GPhase.LOBBY, grid = "", result = 0, line = emptyList(), removed = emptyList(), moves = 0)
    }
}
