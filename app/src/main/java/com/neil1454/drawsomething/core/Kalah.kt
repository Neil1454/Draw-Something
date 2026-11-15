package com.neil1454.drawsomething.core

import org.json.JSONArray
import org.json.JSONObject
import kotlin.random.Random

// ====================================================================================
// Mancala (the Kalah rules). Each player has six pits and a store. Pick up every seed in one of your pits and sow them one by one
// round the board, counter-clockwise, dropping one in each pit (and in your own store, but never your rival's).
//   • Your last seed in your own store: take another turn.
//   • Your last seed in an empty pit of yours, with seeds opposite: capture them (and your last seed) into your store.
//   • When one side is empty the game ends, the other player keeps what is left on their side, and the bigger store wins.
// Board numbers: 0-5 are the host's pits, 6 the host's store, 7-12 the guest's pits, 13 the guest's store.
// ====================================================================================

object KalahRules {
    const val PITS = 6
    const val SIZE = 14
    const val SEED_CHOICES = 3

    fun seedsFor(index: Int) = listOf(3, 4, 6).getOrElse(index) { 4 }

    fun start(seeds: Int): IntArray = IntArray(SIZE) { if (it == 6 || it == 13) 0 else seeds }

    fun store(p: Int) = if (p == 1) 6 else 13
    fun pits(p: Int) = if (p == 1) 0..5 else 7..12
    fun owns(p: Int, i: Int) = i in pits(p)

    fun legal(board: IntArray, p: Int): List<Int> = pits(p).filter { board[it] > 0 }

    class Move(
        val board: IntArray,
        /** Every pit or store a seed was dropped into, in order. */
        val path: List<Int>,
        val extraTurn: Boolean,
        /** The pit whose seeds were captured (-1 for none) and how many went to the store, counting the capturing seed. */
        val capturedFrom: Int,
        val capturedCount: Int,
        val over: Boolean,
        val nextPlayer: Int,
    )

    fun apply(start: IntArray, p: Int, pit: Int): Move {
        val b = start.copyOf()
        var seeds = b[pit]
        b[pit] = 0
        var i = pit
        val skip = store(3 - p)
        val path = ArrayList<Int>()
        while (seeds > 0) {
            i = (i + 1) % SIZE
            if (i == skip) continue
            b[i]++; seeds--
            path.add(i)
        }
        var extra = false
        var capFrom = -1
        var capCount = 0
        if (i == store(p)) extra = true
        else if (owns(p, i) && b[i] == 1 && b[12 - i] > 0) {
            capFrom = 12 - i
            capCount = b[12 - i] + 1
            b[store(p)] += capCount
            b[i] = 0; b[12 - i] = 0
        }
        val over = pits(1).all { b[it] == 0 } || pits(2).all { b[it] == 0 }
        if (over) {
            for (q in 1..2) { for (k in pits(q)) { b[store(q)] += b[k]; b[k] = 0 } }
        }
        return Move(b, path, extra && !over, capFrom, capCount, over, if (extra) p else 3 - p)
    }

    /** Who won once the game is over: 1, 2 or 3 for a draw. */
    fun winner(b: IntArray) = if (b[6] > b[13]) 1 else if (b[13] > b[6]) 2 else 3

    /** A search for the computer player: the best pit for [p] looking [depth] moves ahead (a move that gives another turn counts as one). */
    fun best(board: IntArray, p: Int, depth: Int, random: Random): Int {
        val moves = legal(board, p)
        var bestScore = Int.MIN_VALUE
        var bestPits = ArrayList<Int>()
        for (m in moves) {
            val mv = apply(board, p, m)
            val score = search(mv, p, depth - 1, Int.MIN_VALUE + 1, Int.MAX_VALUE - 1)
            if (score > bestScore) { bestScore = score; bestPits = arrayListOf(m) } else if (score == bestScore) bestPits.add(m)
        }
        return bestPits[random.nextInt(bestPits.size)]
    }

    private fun eval(b: IntArray, me: Int) = b[store(me)] - b[store(3 - me)]

    private fun search(mv: Move, me: Int, depth: Int, alphaIn: Int, betaIn: Int): Int {
        if (mv.over || depth <= 0) return eval(mv.board, me)
        val next = mv.nextPlayer
        val moves = legal(mv.board, next)
        if (moves.isEmpty()) return eval(mv.board, me)
        var alpha = alphaIn
        var beta = betaIn
        return if (next == me) {
            var v = Int.MIN_VALUE + 1
            for (m in moves) {
                v = maxOf(v, search(apply(mv.board, next, m), me, depth - 1, alpha, beta))
                alpha = maxOf(alpha, v)
                if (alpha >= beta) break
            }
            v
        } else {
            var v = Int.MAX_VALUE - 1
            for (m in moves) {
                v = minOf(v, search(apply(mv.board, next, m), me, depth - 1, alpha, beta))
                beta = minOf(beta, v)
                if (alpha >= beta) break
            }
            v
        }
    }
}

data class KalahState(
    val phase: GPhase = GPhase.LOBBY,
    /** The board from MY point of view: 0-5 my pits (left to right), 6 my store, 7-12 their pits (right to left across the top), 13 their store. */
    val board: List<Int> = List(14) { 0 },
    val myTurn: Boolean = false,
    /** The last move, in the same numbering: the pit it started from, the squares the seeds dropped into, and any capture. */
    val lastPit: Int = -1,
    val lastPath: List<Int> = emptyList(),
    val lastExtra: Boolean = false,
    val capturedFrom: Int = -1,
    val capturedCount: Int = 0,
    val lastByMe: Boolean = false,
    /** Counts up with every move so screens can animate each one once. */
    val moves: Int = 0,
    val result: Int = 0,
    val iAmHost: Boolean = true,
) {
    val myStore get() = board[6]
    val theirStore get() = board[13]
    val myScore get() = myStore
    val theirScore get() = theirStore
    val myPits get() = board.subList(0, 6)
    val theirPits get() = board.subList(7, 13)
}

class KalahEngine(
    myName: String,
    theirName: String,
    isHost: Boolean,
    send: (GMsg) -> Unit,
    random: Random = Random.Default,
    clock: () -> Long = { System.currentTimeMillis() },
) : SyncEngine(GameKind.KALAH, myName, theirName, isHost, send, random, clock) {

    var state = KalahState(iAmHost = isHost)
        private set
    override val stateAny: Any get() = state
    override val phase get() = state.phase

    /** Pick up the seeds in my pit [mine] (0 is my leftmost). */
    fun move(mine: Int) = act(JSONObject().put("a", "move").put("p", mine))

    private val board = IntArray(14)
    private var turn = 1
    private var over = false
    private var result = 0
    private var lastPit = -1
    private var lastPath: List<Int> = emptyList()
    private var lastExtra = false
    private var capFrom = -1
    private var capCount = 0
    private var lastBy = 0
    private var moves = 0

    override fun hostBegin(opts: JSONObject) {
        val b = KalahRules.start(KalahRules.seedsFor(opts.optInt("seeds", 1)))
        b.copyInto(board)
        turn = if (random.nextBoolean()) 1 else 2
        over = false; result = 0; lastPit = -1; lastPath = emptyList(); lastExtra = false; capFrom = -1; capCount = 0; lastBy = 0; moves = 0
    }

    override fun hostHandle(fromHost: Boolean, a: JSONObject) {
        if (state.phase != GPhase.PLAYING || over) return
        val p = if (fromHost) 1 else 2
        if (turn != p || a.getString("a") != "move") return
        val mine = a.optInt("p", -1)
        if (mine !in 0..5) return
        val pit = if (p == 1) mine else 7 + mine
        if (board[pit] == 0) return
        val mv = KalahRules.apply(board, p, pit)
        mv.board.copyInto(board)
        lastPit = pit; lastPath = mv.path; lastExtra = mv.extraTurn; capFrom = mv.capturedFrom; capCount = mv.capturedCount; lastBy = p; moves++
        turn = mv.nextPlayer
        if (mv.over) { over = true; result = KalahRules.winner(board) }
        publish()
    }

    override fun hostSnapshot(): JSONObject = JSONObject()
        .put("b", JSONArray(board.toList())).put("tn", turn).put("lp", lastPit).put("pa", JSONArray(lastPath))
        .put("ex", lastExtra).put("cf", capFrom).put("cc", capCount).put("lb", lastBy).put("mv", moves).put("ov", over).put("res", result)

    override fun adopt(s: JSONObject) {
        val a = s.optJSONArray("b") ?: return
        if (a.length() != 14) return
        val real = IntArray(14) { a.optInt(it, 0).coerceIn(0, 200) }
        val me = if (isHost) 1 else 2
        // from my point of view my pits come first: the guest's board is turned half way round
        fun view(i: Int) = if (isHost) i else (i + 7) % 14
        val b = IntArray(14)
        for (i in 0 until 14) b[view(i)] = real[i]
        val path = ArrayList<Int>()
        s.optJSONArray("pa")?.let { for (i in 0 until minOf(it.length(), 120)) { val v = it.optInt(i, -1); if (v in 0..13) path.add(view(v)) } }
        val ov = s.optBoolean("ov", false)
        val lp = s.optInt("lp", -1)
        val cf = s.optInt("cf", -1)
        val res = s.optInt("res", 0).coerceIn(0, 3)
        state = KalahState(
            phase = if (ov) GPhase.OVER else GPhase.PLAYING, board = b.toList(),
            myTurn = s.optInt("tn", 1) == me && !ov,
            lastPit = if (lp in 0..13) view(lp) else -1, lastPath = path, lastExtra = s.optBoolean("ex", false),
            capturedFrom = if (cf in 0..13) view(cf) else -1, capturedCount = s.optInt("cc", 0).coerceIn(0, 100),
            lastByMe = s.optInt("lb", 0) == me, moves = s.optInt("mv", 0).coerceIn(0, 1000),
            result = if (!ov) 0 else if (res == 3) 3 else if (res == me) 1 else 2, iAmHost = isHost,
        )
    }

    override fun reset() {
        state = KalahState(iAmHost = isHost)
    }
}

class KalahBrain(private val e: KalahEngine, skill: Skill, random: Random) : Brain(skill, random) {
    override fun think(now: Long) {
        val s = e.state
        if (s.phase != GPhase.PLAYING || !s.myTurn) return
        if (!ready(now, "kalah${s.moves}", 900, 1900)) return
        val board = IntArray(14) { s.board[it] }
        // the search works in "host" numbering with me as player 1 (my view already puts my pits first)
        val legal = KalahRules.legal(board, 1)
        if (legal.isEmpty()) return
        val depth = when (skill) { Skill.EASY -> 1; Skill.NORMAL -> 4; Skill.HARD -> 7 }
        val pit = if (skill == Skill.EASY && pct(35)) legal[random.nextInt(legal.size)] else KalahRules.best(board, 1, depth, random)
        e.move(pit)
    }
}
