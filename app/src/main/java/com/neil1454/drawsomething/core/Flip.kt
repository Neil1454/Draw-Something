package com.neil1454.drawsomething.core

import org.json.JSONArray
import org.json.JSONObject
import kotlin.random.Random

// ====================================================================================
// Flip Fight (Reversi / Othello). Place a disc so that it traps a line of the other player's
// discs between your new disc and another of yours: every trapped disc flips to your colour.
// If you can't move you pass; when nobody can move, most discs wins.
// ====================================================================================

enum class FlipSize(val label: String, val n: Int) {
    CLASSIC("Classic 8×8", 8),
    QUICK("Quick 6×6", 6),
}

private val Directions = listOf(-1 to -1, 0 to -1, 1 to -1, -1 to 0, 1 to 0, -1 to 1, 0 to 1, 1 to 1)

/** Squares [who] (1 or 2) may play on [grid] (n x n, 0 empty), with the discs each would flip. */
fun reversiMoves(grid: IntArray, n: Int, who: Int): Map<Int, List<Int>> {
    val out = LinkedHashMap<Int, List<Int>>()
    for (y in 0 until n) for (x in 0 until n) {
        if (grid[y * n + x] != 0) continue
        val flips = ArrayList<Int>()
        for ((dx, dy) in Directions) {
            val line = ArrayList<Int>()
            var cx = x + dx
            var cy = y + dy
            while (cx in 0 until n && cy in 0 until n && grid[cy * n + cx] == 3 - who) {
                line += cy * n + cx
                cx += dx; cy += dy
            }
            if (line.isNotEmpty() && cx in 0 until n && cy in 0 until n && grid[cy * n + cx] == who) flips += line
        }
        if (flips.isNotEmpty()) out[y * n + x] = flips
    }
    return out
}

data class FlipState(
    val myName: String,
    val theirName: String,
    val phase: GPhase = GPhase.LOBBY,
    val n: Int = 8,
    /** Row by row: '.' empty, '1' host (black), '2' partner (white). */
    val grid: String = "",
    val myDisc: Char = '1',
    val myTurn: Boolean = false,
    /** Squares I may play right now (empty unless it's my turn). */
    val legal: List<Int> = emptyList(),
    val last: Int = -1,
    val flipped: List<Int> = emptyList(),
    /** Someone had to pass on the latest move: 0 nobody, 1 me, 2 them. */
    val passed: Int = 0,
    val myCount: Int = 2,
    val theirCount: Int = 2,
    val moves: Int = 0,
    /** 0 playing, 1 I won, 2 they won, 3 draw. */
    val result: Int = 0,
)

class FlipEngine(
    myName: String,
    theirName: String,
    isHost: Boolean,
    send: (GMsg) -> Unit,
    random: Random = Random.Default,
    clock: () -> Long = { System.currentTimeMillis() },
) : SyncEngine(GameKind.FLIP, myName, theirName, isHost, send, random, clock) {

    var state = FlipState(myName, theirName, myDisc = if (isHost) '1' else '2')
        private set
    override val stateAny: Any get() = state
    override val phase get() = state.phase

    private var n = 8
    private var grid = IntArray(0)
    private var turn = 1
    private var last = -1
    private var flipped: List<Int> = emptyList()
    private var passed = 0 // 1 host passed, 2 partner passed
    private var over = false
    private var moves = 0

    override fun hostBegin(opts: JSONObject) {
        n = FlipSize.values()[opts.optInt("size", 0).coerceIn(0, FlipSize.values().size - 1)].n
        grid = IntArray(n * n)
        val a = n / 2 - 1
        val b = n / 2
        grid[a * n + a] = 2; grid[a * n + b] = 1; grid[b * n + a] = 1; grid[b * n + b] = 2
        turn = 1 // black always starts
        last = -1; flipped = emptyList(); passed = 0; over = false; moves = 0
    }

    fun place(index: Int) = act(JSONObject().put("i", index))

    override fun hostHandle(fromHost: Boolean, a: JSONObject) {
        if (state.phase != GPhase.PLAYING || over) return
        val who = if (fromHost) 1 else 2
        if (turn != who) return
        val i = a.getInt("i")
        val flips = reversiMoves(grid, n, who)[i] ?: return
        grid[i] = who
        flips.forEach { grid[it] = who }
        last = i; flipped = flips; moves++
        passed = 0
        val other = 3 - who
        when {
            reversiMoves(grid, n, other).isNotEmpty() -> turn = other
            reversiMoves(grid, n, who).isNotEmpty() -> { turn = who; passed = other }
            else -> over = true
        }
        publish()
    }

    override fun hostSnapshot(): JSONObject {
        val sb = StringBuilder(grid.size)
        for (v in grid) sb.append(if (v == 0) '.' else ('0' + v))
        return JSONObject().put("n", n).put("g", sb.toString()).put("turn", turn).put("last", last)
            .put("fl", JSONArray(flipped)).put("pass", passed).put("mv", moves).put("over", over)
    }

    override fun adopt(s: JSONObject) {
        val nn = s.getInt("n").coerceIn(4, 12)
        val g = s.getString("g")
        if (g.length != nn * nn || g.any { it !in ".12" }) return
        val me = if (isHost) 1 else 2
        val over = s.getBoolean("over")
        val cells = IntArray(g.length) { if (g[it] == '.') 0 else g[it] - '0' }
        val myTurn = !over && s.getInt("turn") == me
        val mine = g.count { it == '0' + me }
        val theirs = g.count { it == '0' + (3 - me) }
        state = state.copy(
            phase = if (over) GPhase.OVER else GPhase.PLAYING, n = nn, grid = g, myTurn = myTurn,
            legal = if (myTurn) reversiMoves(cells, nn, me).keys.toList() else emptyList(),
            last = s.optInt("last", -1).coerceIn(-1, nn * nn - 1),
            flipped = s.ints("fl", 40).filter { it in 0 until nn * nn },
            passed = when (s.optInt("pass", 0)) { 0 -> 0; me -> 1; else -> 2 },
            myCount = mine, theirCount = theirs, moves = s.optInt("mv", 0).coerceIn(0, 100_000),
            result = if (!over) 0 else if (mine > theirs) 1 else if (mine < theirs) 2 else 3,
        )
    }

    override fun reset() {
        state = state.copy(phase = GPhase.LOBBY, grid = "", legal = emptyList(), flipped = emptyList(), result = 0, moves = 0)
    }
}
