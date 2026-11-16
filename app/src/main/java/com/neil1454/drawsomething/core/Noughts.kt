package com.neil1454.drawsomething.core

import org.json.JSONArray
import org.json.JSONObject
import kotlin.random.Random

// ====================================================================================
// Noughts and Crosses (Tic-Tac-Toe), two ways:
//   • Classic: one 3 x 3 grid. Three in a row wins.
//   • Ultimate: nine small grids make one big grid. Where you play in a small grid decides which small grid your rival must play
//     in next. Win small grids to claim their squares on the big grid, and win three small grids in a row to win.
// The host can play a single game, or a best-of-three or best-of-five. The player who goes first swaps every game.
// Board numbers: classic 0-8; ultimate sub-grid * 9 + square (0-80). In the engine 1 is the host's mark and 2 the guest's.
// ====================================================================================

object NoughtsRules {
    val LINES = listOf(
        intArrayOf(0, 1, 2), intArrayOf(3, 4, 5), intArrayOf(6, 7, 8),
        intArrayOf(0, 3, 6), intArrayOf(1, 4, 7), intArrayOf(2, 5, 8),
        intArrayOf(0, 4, 8), intArrayOf(2, 4, 6),
    )

    /** Games in a series, by lobby choice. */
    val SERIES = listOf(1, 3, 5)

    /** The player (1 or 2) who has three in a row in these nine squares, 0 for nobody (3 is never returned). */
    fun lineWinner(c: IntArray, off: Int = 0): Int {
        for (l in LINES) {
            val v = c[off + l[0]]
            if (v in 1..2 && v == c[off + l[1]] && v == c[off + l[2]]) return v
        }
        return 0
    }

    fun winningLine(c: IntArray, off: Int = 0): IntArray? = LINES.firstOrNull { l ->
        val v = c[off + l[0]]; v in 1..2 && v == c[off + l[1]] && v == c[off + l[2]]
    }

    fun full(c: IntArray, off: Int = 0) = (0 until 9).all { c[off + it] != 0 }

    // ------------------------------------------------------------------ the classic game

    /** Every empty square of a classic board. */
    fun classicMoves(c: IntArray) = (0 until 9).filter { c[it] == 0 }

    /** The best square for [p] (perfect play) by a full search, picking at random between equally good ones. */
    fun classicBest(c: IntArray, p: Int, random: Random): Int {
        val moves = classicMoves(c)
        var best = Int.MIN_VALUE
        val picks = ArrayList<Int>()
        for (m in moves) {
            c[m] = p
            val v = -negamax(c, 3 - p, 1)
            c[m] = 0
            if (v > best) { best = v; picks.clear(); picks.add(m) } else if (v == best) picks.add(m)
        }
        return picks[random.nextInt(picks.size)]
    }

    /** Score for the player to move: positive is good for them. */
    private fun negamax(c: IntArray, p: Int, depth: Int): Int {
        val w = lineWinner(c)
        if (w != 0) return -(10 - depth) // the previous player just won, so this player lost
        if (full(c)) return 0
        var best = Int.MIN_VALUE
        for (m in 0 until 9) if (c[m] == 0) {
            c[m] = p
            best = maxOf(best, -negamax(c, 3 - p, depth + 1))
            c[m] = 0
        }
        return best
    }

    // ------------------------------------------------------------------ ultimate

    /** What a small grid is: 0 still open, 1 or 2 won by that player, 3 full with no winner. */
    fun subResult(b: IntArray, sub: Int): Int {
        val w = lineWinner(b, sub * 9)
        return if (w != 0) w else if (full(b, sub * 9)) 3 else 0
    }

    fun subResults(b: IntArray) = IntArray(9) { subResult(b, it) }

    /** Who has won the big grid: 1 or 2, 3 for a finished grid with equal small grids won, 0 while it is still going. */
    fun bigResult(sub: IntArray): Int {
        val grid = IntArray(9) { if (sub[it] in 1..2) sub[it] else 0 }
        val w = lineWinner(grid)
        if (w != 0) return w
        if (sub.none { it == 0 }) {
            val a = sub.count { it == 1 }; val b = sub.count { it == 2 }
            return if (a > b) 1 else if (b > a) 2 else 3
        }
        return 0
    }

    /** The small grid the next player has to play in, or -1 for "anywhere". [lastSquare] is the square just played (0-8). */
    fun forcedFor(sub: IntArray, lastSquare: Int): Int = if (lastSquare in 0..8 && sub[lastSquare] == 0) lastSquare else -1

    fun ultimateMoves(b: IntArray, sub: IntArray, forced: Int): List<Int> {
        val out = ArrayList<Int>()
        for (s in 0 until 9) {
            if (sub[s] != 0 || (forced >= 0 && s != forced)) continue
            for (c in 0 until 9) if (b[s * 9 + c] == 0) out.add(s * 9 + c)
        }
        return out
    }

    /** A quick judgement of a move in the ultimate game, for the computer player (higher is better). */
    fun ultimateScore(b: IntArray, sub: IntArray, forced: Int, move: Int, p: Int, hard: Boolean): Int {
        val s = move / 9; val c = move % 9
        val o = 3 - p
        var score = 0
        b[move] = p
        val subNow = subResult(b, s)
        if (subNow == p) {
            score += 40
            val s2 = sub.copyOf(); s2[s] = p
            if (bigResult(s2) == p) score += 1000
        }
        // does this square stop the rival winning this grid?
        b[move] = o
        if (lineWinner(b, s * 9) == o) score += 25
        b[move] = p
        // where the rival must go next
        val s2 = sub.copyOf(); if (subNow != 0) s2[s] = subNow
        val next = forcedFor(s2, c)
        if (next >= 0) {
            // can they win that grid straight away?
            var theyWin = false; var iWinThere = false
            for (k in 0 until 9) if (b[next * 9 + k] == 0) {
                b[next * 9 + k] = o; if (lineWinner(b, next * 9) == o) theyWin = true; b[next * 9 + k] = 0
                b[next * 9 + k] = p; if (lineWinner(b, next * 9) == p) iWinThere = true; b[next * 9 + k] = 0
            }
            if (theyWin) score -= if (hard) 35 else 15
            if (iWinThere) score += 4
        } else score -= if (hard) 6 else 2 // the rival gets a free choice
        // the centre of a grid and the centre grid are worth a little
        if (c == 4) score += 3
        if (s == 4) score += 2
        if (c in intArrayOf(0, 2, 6, 8)) score += 1
        b[move] = 0
        return score
    }
}

data class NoughtsState(
    val phase: GPhase = GPhase.LOBBY,
    val ultimate: Boolean = false,
    /** Marks from MY point of view: 0 empty, 1 mine, 2 theirs. 9 squares (classic) or 81 (ultimate). */
    val board: List<Int> = List(9) { 0 },
    /** The ultimate game's small grids: 0 open, 1 mine, 2 theirs, 3 full. */
    val sub: List<Int> = List(9) { 0 },
    /** The small grid the player to move has to use, or -1 for any. */
    val forced: Int = -1,
    val myTurn: Boolean = false,
    /** True if my mark is the cross (the cross always goes first). */
    val iAmCross: Boolean = true,
    /** The squares of the line that won the game just played (classic: squares; ultimate: small grids). */
    val winLine: List<Int> = emptyList(),
    val lastMove: Int = -1,
    val moves: Int = 0,
    val round: Int = 1,
    val rounds: Int = 1,
    val myWins: Int = 0,
    val theirWins: Int = 0,
    val draws: Int = 0,
    /** How the game just played ended: 0 still going, 1 I won it, 2 they won it, 3 drawn. In a series the next one starts by itself. */
    val roundResult: Int = 0,
    /** 0 while the series goes on, otherwise 1 I won, 2 they won, 3 a draw. */
    val result: Int = 0,
    val iAmHost: Boolean = true,
) {
    val myScore get() = myWins
    val theirScore get() = theirWins
    val inSeries get() = rounds > 1
}

class NoughtsEngine(
    myName: String,
    theirName: String,
    isHost: Boolean,
    send: (GMsg) -> Unit,
    random: Random = Random.Default,
    clock: () -> Long = { System.currentTimeMillis() },
) : SyncEngine(GameKind.NOUGHTS, myName, theirName, isHost, send, random, clock) {

    var state = NoughtsState(iAmHost = isHost)
        private set
    override val stateAny: Any get() = state
    override val phase get() = state.phase

    /** Play in square [i] (classic 0-8, ultimate 0-80). */
    fun play(i: Int) = act(JSONObject().put("a", "play").put("i", i))

    private var ultimate = false
    private var board = IntArray(9)
    private var sub = IntArray(9)
    private var forced = -1
    private var turn = 1
    private var crossPlayer = 1
    private var rounds = 1
    private var round = 1
    private val wins = IntArray(3)
    private var draws = 0
    private var roundResult = 0
    private var winLine: List<Int> = emptyList()
    private var lastMove = -1
    private var moves = 0
    private var nextRoundAt = 0L
    private var over = false
    private var result = 0

    override fun hostBegin(opts: JSONObject) {
        ultimate = opts.optInt("mode", 0) == 1
        rounds = NoughtsRules.SERIES.getOrElse(opts.optInt("series", 0)) { 1 }
        round = 1; wins.fill(0); draws = 0; over = false; result = 0
        crossPlayer = if (random.nextBoolean()) 1 else 2
        newGame()
    }

    private fun newGame() {
        board = IntArray(if (ultimate) 81 else 9); sub = IntArray(9); forced = -1
        turn = crossPlayer; roundResult = 0; winLine = emptyList(); lastMove = -1; moves = 0; nextRoundAt = 0L
    }

    override fun hostHandle(fromHost: Boolean, a: JSONObject) {
        if (state.phase != GPhase.PLAYING || over || roundResult != 0) return
        val p = if (fromHost) 1 else 2
        if (turn != p || a.getString("a") != "play") return
        val i = a.optInt("i", -1)
        if (i !in board.indices || board[i] != 0) return
        if (ultimate) {
            if (i !in NoughtsRules.ultimateMoves(board, sub, forced)) return
            board[i] = p; lastMove = i; moves++
            val s = i / 9
            sub[s] = NoughtsRules.subResult(board, s)
            val big = NoughtsRules.bigResult(sub)
            if (big != 0) finishRound(big, bigLine()) else { forced = NoughtsRules.forcedFor(sub, i % 9); turn = 3 - p }
        } else {
            board[i] = p; lastMove = i; moves++
            val line = NoughtsRules.winningLine(board)
            if (line != null) finishRound(p, line.toList())
            else if (NoughtsRules.full(board)) finishRound(3, emptyList())
            else turn = 3 - p
        }
        publish()
    }

    private fun bigLine(): List<Int> {
        val grid = IntArray(9) { if (sub[it] in 1..2) sub[it] else 0 }
        return NoughtsRules.winningLine(grid)?.toList() ?: emptyList()
    }

    private fun finishRound(winner: Int, line: List<Int>) {
        roundResult = winner; winLine = line
        if (winner in 1..2) wins[winner]++ else draws++
        val need = rounds / 2 + 1
        val done = wins[1] >= need || wins[2] >= need || round >= rounds
        if (done) {
            over = true
            result = if (wins[1] > wins[2]) 1 else if (wins[2] > wins[1]) 2 else 3
        } else nextRoundAt = clock() + 2600L
    }

    override fun hostTick(now: Long) {
        if (!over && roundResult != 0 && nextRoundAt > 0L && now >= nextRoundAt) {
            round++
            crossPlayer = 3 - crossPlayer
            newGame()
            publish()
        }
    }

    override fun hostSnapshot(): JSONObject = JSONObject()
        .put("u", ultimate).put("b", board.joinToString("")).put("sb", sub.joinToString("")).put("fc", forced)
        .put("tn", turn).put("xp", crossPlayer).put("rt", rounds).put("rd", round)
        .put("w1", wins[1]).put("w2", wins[2]).put("dr", draws).put("rr", roundResult)
        .put("wl", JSONArray(winLine)).put("lm", lastMove).put("mv", moves).put("ov", over).put("res", result)

    override fun adopt(s: JSONObject) {
        val ult = s.optBoolean("u", false)
        val bs = s.optString("b", "")
        val ss = s.optString("sb", "")
        if (bs.length != (if (ult) 81 else 9) || ss.length != 9 || bs.any { it !in '0'..'2' } || ss.any { it !in '0'..'3' }) return
        val me = if (isHost) 1 else 2
        fun mark(c: Char) = when (c - '0') { 0 -> 0; me -> 1; else -> 2 }
        fun sres(c: Char) = when (c - '0') { 0 -> 0; 3 -> 3; me -> 1; else -> 2 }
        fun who(v: Int) = when (v) { 0 -> 0; 3 -> 3; me -> 1; else -> 2 }
        val ov = s.optBoolean("ov", false)
        val wl = ArrayList<Int>()
        s.optJSONArray("wl")?.let { for (i in 0 until minOf(it.length(), 9)) { val v = it.optInt(i, -1); if (v in 0..8) wl.add(v) } }
        val rounds = s.optInt("rt", 1).coerceIn(1, 5)
        state = NoughtsState(
            phase = if (ov) GPhase.OVER else GPhase.PLAYING, ultimate = ult,
            board = bs.map { mark(it) }, sub = ss.map { sres(it) }, forced = s.optInt("fc", -1).coerceIn(-1, 8),
            myTurn = s.optInt("tn", 1) == me && !ov && s.optInt("rr", 0) == 0,
            iAmCross = s.optInt("xp", 1) == me, winLine = wl, lastMove = s.optInt("lm", -1).coerceIn(-1, 80),
            moves = s.optInt("mv", 0).coerceIn(0, 100), round = s.optInt("rd", 1).coerceIn(1, 5), rounds = rounds,
            myWins = (if (isHost) s.optInt("w1", 0) else s.optInt("w2", 0)).coerceIn(0, 5),
            theirWins = (if (isHost) s.optInt("w2", 0) else s.optInt("w1", 0)).coerceIn(0, 5),
            draws = s.optInt("dr", 0).coerceIn(0, 5), roundResult = who(s.optInt("rr", 0).coerceIn(0, 3)),
            result = if (!ov) 0 else who(s.optInt("res", 0).coerceIn(0, 3)), iAmHost = isHost,
        )
    }

    override fun reset() {
        state = NoughtsState(iAmHost = isHost)
    }
}

class NoughtsBrain(private val e: NoughtsEngine, skill: Skill, random: Random) : Brain(skill, random) {
    override fun think(now: Long) {
        val s = e.state
        if (s.phase != GPhase.PLAYING || !s.myTurn) return
        if (!ready(now, "nc${s.round}-${s.moves}", 600, 1500)) return
        val b = IntArray(s.board.size) { s.board[it] }
        if (!s.ultimate) {
            val moves = NoughtsRules.classicMoves(b)
            if (moves.isEmpty()) return
            val pick = when (skill) {
                Skill.HARD -> NoughtsRules.classicBest(b, 1, random)
                Skill.NORMAL -> if (pct(75)) NoughtsRules.classicBest(b, 1, random) else moves[random.nextInt(moves.size)]
                Skill.EASY -> sensible(b, moves)
            }
            e.play(pick)
        } else {
            val sub = IntArray(9) { s.sub[it] }
            val moves = NoughtsRules.ultimateMoves(b, sub, s.forced)
            if (moves.isEmpty()) return
            val pick = if (skill == Skill.EASY && pct(45)) moves[random.nextInt(moves.size)]
            else moves.maxByOrNull { NoughtsRules.ultimateScore(b, sub, s.forced, it, 1, skill == Skill.HARD) + random.nextFloat() * (if (skill == Skill.HARD) 0.5f else 4f) }!!
            e.play(pick)
        }
    }

    /** Takes a win or blocks a loss if it sees one, otherwise plays anywhere. */
    private fun sensible(b: IntArray, moves: List<Int>): Int {
        for (p in intArrayOf(1, 2)) for (m in moves) { b[m] = p; val w = NoughtsRules.lineWinner(b); b[m] = 0; if (w == p && pct(70)) return m }
        return moves[random.nextInt(moves.size)]
    }
}
