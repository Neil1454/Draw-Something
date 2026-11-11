package com.neil1454.drawsomething.core

import org.json.JSONArray
import org.json.JSONObject
import kotlin.random.Random

// ====================================================================================
// Draughts (English draughts / American checkers), 8x8.
//  - Men move one step diagonally forward onto an empty dark square; kings move one step in any diagonal direction.
//  - Jump over an enemy piece to capture it. Captures are COMPULSORY, and if you can keep jumping with the same
//    piece you must (a "multiple jump").
//  - A man reaching the far row becomes a king, and its turn ends.
//  - You win when the other player cannot move (no pieces left, or all blocked).
//    If 80 half-moves pass with no capture and no man moved, it's a draw.
// The host is the "white" player at the bottom (rows 5 to 7) and moves first. Guest pieces start at the top.
// ====================================================================================

object Draughts {
    const val N = 8
    const val DRAW_PLIES = 80
    const val EMPTY = 0
    const val HOST_MAN = 1
    const val GUEST_MAN = 2
    const val HOST_KING = 3
    const val GUEST_KING = 4

    fun owner(v: Int) = when (v) { HOST_MAN, HOST_KING -> 1; GUEST_MAN, GUEST_KING -> 2; else -> 0 }
    fun isKing(v: Int) = v == HOST_KING || v == GUEST_KING
    fun isDark(i: Int) = (i % N + i / N) % 2 == 1

    fun start(): IntArray {
        val g = IntArray(N * N)
        for (i in g.indices) if (isDark(i)) {
            val y = i / N
            if (y <= 2) g[i] = GUEST_MAN else if (y >= 5) g[i] = HOST_MAN
        }
        return g
    }

    /** One step of a move: from -> to, capturing the piece at [cap] (or -1 for a plain step). */
    data class Move(val from: Int, val to: Int, val cap: Int = -1)

    private val DIAGS = listOf(-1 to -1, 1 to -1, -1 to 1, 1 to 1)

    private fun dirs(v: Int): List<Pair<Int, Int>> =
        if (isKing(v)) DIAGS else if (owner(v) == 1) DIAGS.filter { it.second < 0 } else DIAGS.filter { it.second > 0 }

    private fun jumpsFrom(g: IntArray, i: Int): List<Move> {
        val v = g[i]
        val who = owner(v)
        val x = i % N
        val y = i / N
        val out = ArrayList<Move>(2)
        for ((dx, dy) in dirs(v)) {
            val mx = x + dx; val my = y + dy; val lx = x + 2 * dx; val ly = y + 2 * dy
            if (lx !in 0 until N || ly !in 0 until N) continue
            val mid = my * N + mx
            if (owner(g[mid]) == 3 - who && g[ly * N + lx] == EMPTY) out += Move(i, ly * N + lx, mid)
        }
        return out
    }

    /**
     * Every legal single step for [who]. If any jump exists only jumps are legal; [must] (>= 0) is the square of a piece that
     * has just jumped and has to carry on jumping.
     */
    fun moves(g: IntArray, who: Int, must: Int = -1): List<Move> {
        val jumps = ArrayList<Move>()
        if (must >= 0) {
            if (owner(g[must]) == who) jumps += jumpsFrom(g, must)
            return jumps
        }
        for (i in g.indices) if (owner(g[i]) == who) jumps += jumpsFrom(g, i)
        if (jumps.isNotEmpty()) return jumps
        val steps = ArrayList<Move>()
        for (i in g.indices) {
            val v = g[i]
            if (owner(v) != who) continue
            val x = i % N
            val y = i / N
            for ((dx, dy) in dirs(v)) {
                val nx = x + dx; val ny = y + dy
                if (nx in 0 until N && ny in 0 until N && g[ny * N + nx] == EMPTY) steps += Move(i, ny * N + nx)
            }
        }
        return steps
    }

    class Result(val grid: IntArray, val promoted: Boolean, val continues: Boolean)

    /** Plays [m] for [who] on a copy of [g]. */
    fun apply(g: IntArray, who: Int, m: Move): Result {
        val out = g.copyOf()
        val v = out[m.from]
        out[m.from] = EMPTY
        if (m.cap >= 0) out[m.cap] = EMPTY
        val row = m.to / N
        val promote = !isKing(v) && ((who == 1 && row == 0) || (who == 2 && row == N - 1))
        out[m.to] = if (promote) (if (who == 1) HOST_KING else GUEST_KING) else v
        val more = m.cap >= 0 && !promote && jumpsFrom(out, m.to).isNotEmpty()
        return Result(out, promote, more)
    }

    fun count(g: IntArray, who: Int) = g.count { owner(it) == who }

    fun encode(g: IntArray): String = String(CharArray(g.size) { ".abAB"[g[it]] })
    fun decode(s: String): IntArray? =
        if (s.length != N * N || s.any { it !in ".abAB" }) null else IntArray(s.length) { ".abAB".indexOf(s[it]) }
}

data class DraughtsState(
    val myName: String,
    val theirName: String,
    val phase: GPhase = GPhase.LOBBY,
    /** 64 squares row by row from the top: '.' empty, a/A host man/king, b/B guest man/king. */
    val grid: String = "",
    val iAmHost: Boolean = true,
    val myTurn: Boolean = false,
    /** Legal steps for me right now, each as from*64+to (empty when it isn't my turn). */
    val legal: List<Int> = emptyList(),
    /** True when my only legal moves are captures (they are compulsory). */
    val mustCapture: Boolean = false,
    /** The square of the piece that must keep jumping, or -1. */
    val chain: Int = -1,
    val last: Int = -1,
    val lastFrom: Int = -1,
    val captured: Int = -1,
    val crowned: Boolean = false,
    val myCount: Int = 12,
    val theirCount: Int = 12,
    val moves: Int = 0,
    /** 0 playing, 1 I won, 2 they won, 3 draw. */
    val result: Int = 0,
    /** Why it ended: "blocked", "none" (no pieces left) or "draw". */
    val reason: String = "",
)

class DraughtsEngine(
    myName: String,
    theirName: String,
    isHost: Boolean,
    send: (GMsg) -> Unit,
    random: Random = Random.Default,
    clock: () -> Long = { System.currentTimeMillis() },
) : SyncEngine(GameKind.DRAUGHTS, myName, theirName, isHost, send, random, clock) {

    var state = DraughtsState(myName, theirName, iAmHost = isHost)
        private set
    override val stateAny: Any get() = state
    override val phase get() = state.phase

    private var grid = Draughts.start()
    private var turn = 1
    private var must = -1
    private var lastFrom = -1
    private var lastTo = -1
    private var captured = -1
    private var crowned = false
    private var moves = 0
    private var quiet = 0
    private var over = false
    private var winner = 0 // 1 host, 2 guest, 3 draw
    private var reason = ""

    override fun hostBegin(opts: JSONObject) {
        grid = Draughts.start()
        turn = 1; must = -1; lastFrom = -1; lastTo = -1; captured = -1; crowned = false
        moves = 0; quiet = 0; over = false; winner = 0; reason = ""
    }

    fun move(from: Int, to: Int) = act(JSONObject().put("f", from).put("t", to))

    override fun hostHandle(fromHost: Boolean, a: JSONObject) {
        if (state.phase != GPhase.PLAYING || over) return
        val who = if (fromHost) 1 else 2
        if (turn != who) return
        val from = a.getInt("f")
        val to = a.getInt("t")
        val m = Draughts.moves(grid, who, must).firstOrNull { it.from == from && it.to == to } ?: return
        val wasMan = !Draughts.isKing(grid[from])
        val r = Draughts.apply(grid, who, m)
        grid = r.grid
        lastFrom = from; lastTo = to; captured = m.cap; crowned = r.promoted; moves++
        quiet = if (m.cap >= 0 || wasMan) 0 else quiet + 1
        if (r.continues) {
            must = to
        } else {
            must = -1
            turn = 3 - who
            if (Draughts.moves(grid, turn).isEmpty()) {
                over = true; winner = who
                reason = if (Draughts.count(grid, turn) == 0) "none" else "blocked"
            } else if (quiet >= Draughts.DRAW_PLIES) {
                over = true; winner = 3; reason = "draw"
            }
        }
        publish()
    }

    override fun hostSnapshot(): JSONObject = JSONObject()
        .put("g", Draughts.encode(grid)).put("turn", turn).put("must", must).put("lf", lastFrom).put("lt", lastTo)
        .put("cap", captured).put("kg", crowned).put("mv", moves).put("over", over).put("win", winner).put("why", reason)

    override fun adopt(s: JSONObject) {
        val g = Draughts.decode(s.getString("g")) ?: return
        val me = if (isHost) 1 else 2
        val over = s.getBoolean("over")
        val turn = s.getInt("turn")
        val must = s.optInt("must", -1).coerceIn(-1, 63)
        val myTurn = !over && turn == me
        val legal = if (myTurn) Draughts.moves(g, me, must) else emptyList()
        val win = s.optInt("win", 0)
        state = state.copy(
            phase = if (over) GPhase.OVER else GPhase.PLAYING,
            grid = Draughts.encode(g), iAmHost = isHost, myTurn = myTurn,
            legal = legal.map { it.from * 64 + it.to },
            mustCapture = legal.any { it.cap >= 0 },
            chain = if (myTurn) must else -1,
            last = s.optInt("lt", -1).coerceIn(-1, 63), lastFrom = s.optInt("lf", -1).coerceIn(-1, 63),
            captured = s.optInt("cap", -1).coerceIn(-1, 63), crowned = s.optBoolean("kg", false),
            myCount = Draughts.count(g, me), theirCount = Draughts.count(g, 3 - me),
            moves = s.optInt("mv", 0).coerceIn(0, 100_000),
            result = if (!over) 0 else if (win == 3) 3 else if (win == me) 1 else 2,
            reason = s.optString("why", "").take(8),
        )
    }

    override fun reset() {
        state = state.copy(phase = GPhase.LOBBY, grid = "", legal = emptyList(), result = 0, moves = 0, last = -1, lastFrom = -1, captured = -1)
    }
}

// ====================================================================== the computer player

class DraughtsBrain(private val e: DraughtsEngine, skill: Skill, random: Random) : Brain(skill, random) {
    override fun think(now: Long) {
        val s = e.state
        if (s.phase != GPhase.PLAYING || !s.myTurn || s.legal.isEmpty()) return
        if (!ready(now, s.moves, 700, 1500)) return
        val pick = DraughtsAi.choose(s, skill, random)
        e.move(pick / 64, pick % 64)
    }
}

object DraughtsAi {
    private const val WIN = 100_000

    private fun eval(g: IntArray, me: Int): Int {
        var score = 0
        for (i in g.indices) {
            val v = g[i]
            if (v == Draughts.EMPTY) continue
            val who = Draughts.owner(v)
            val x = i % Draughts.N
            val y = i / Draughts.N
            var p = if (Draughts.isKing(v)) 170 else 100
            if (!Draughts.isKing(v)) {
                val advance = if (who == 1) 7 - y else y // how far towards crowning
                p += advance * 4
                if ((who == 1 && y == 7) || (who == 2 && y == 0)) p += 6 // back row guards
            } else {
                p += 6 - (kotlin.math.abs(x - 3.5) + kotlin.math.abs(y - 3.5)).toInt() // kings like the middle
            }
            if (x == 0 || x == Draughts.N - 1) p -= 2
            score += if (who == me) p else -p
        }
        return score
    }

    private fun search(g: IntArray, turn: Int, must: Int, depth: Int, alpha0: Int, beta: Int, me: Int): Int {
        val ms = Draughts.moves(g, turn, must)
        if (ms.isEmpty()) return if (turn == me) -WIN - depth else WIN + depth
        if (depth <= 0 && ms.none { it.cap >= 0 }) return eval(g, me)
        if (depth <= -6) return eval(g, me)
        var alpha = alpha0
        var best = if (turn == me) Int.MIN_VALUE else Int.MAX_VALUE
        var b = beta
        for (m in ms) {
            val r = Draughts.apply(g, turn, m)
            val v = if (r.continues) search(r.grid, turn, m.to, depth - 1, alpha, b, me)
            else search(r.grid, 3 - turn, -1, depth - 1, alpha, b, me)
            if (turn == me) {
                if (v > best) best = v
                if (best > alpha) alpha = best
            } else {
                if (v < best) best = v
                if (best < b) b = best
            }
            if (alpha >= b) break
        }
        return best
    }

    /** The best step (from*64+to) for the player to move in [s]; weaker skills look fewer moves ahead and sometimes slip. */
    fun choose(s: DraughtsState, skill: Skill, random: Random): Int {
        val g = Draughts.decode(s.grid)!!
        val me = if (s.iAmHost) 1 else 2
        val ms = Draughts.moves(g, me, s.chain)
        if (ms.size == 1) return ms[0].from * 64 + ms[0].to
        val depth = when (skill) { Skill.EASY -> 1; Skill.NORMAL -> 4; Skill.HARD -> 6 }
        if (skill == Skill.EASY && random.nextInt(100) < 30) return ms.random(random).let { it.from * 64 + it.to }
        var bestScore = Int.MIN_VALUE
        val best = ArrayList<Draughts.Move>()
        for (m in ms) {
            val r = Draughts.apply(g, me, m)
            val v = if (r.continues) search(r.grid, me, m.to, depth, Int.MIN_VALUE + 1, Int.MAX_VALUE, me)
            else search(r.grid, 3 - me, -1, depth, Int.MIN_VALUE + 1, Int.MAX_VALUE, me)
            if (v > bestScore) { bestScore = v; best.clear(); best += m } else if (v == bestScore) best += m
        }
        val m = best.random(random)
        return m.from * 64 + m.to
    }
}
