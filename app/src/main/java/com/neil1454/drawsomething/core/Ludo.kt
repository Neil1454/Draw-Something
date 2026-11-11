package com.neil1454.drawsomething.core

import org.json.JSONArray
import org.json.JSONObject
import kotlin.random.Random

// ====================================================================================
// Ludo (known in the UK as Frustration, or Trouble in the USA, when it comes with the pop-up dice).
// Each player has four pieces that start in their base.
//  - You need a 6 to bring a piece out onto the track. A 6 also gives you another go (three 6s in a row lose the turn).
//  - Move a piece the number rolled, once round the track and then up your own home column.
//  - Land on a lone opposing piece and it goes back to its base (not on a star or a starting square, which are safe).
//  - To get a piece HOME you must roll the EXACT number needed. If the roll is too big that piece can't move.
//  - First to get all four pieces home wins.
// The host plays red and starts at the left, the guest plays blue on the opposite side.
// ====================================================================================

object Ludo {
    const val PIECES = 4
    const val RING = 52
    const val HOME = 56        // progress value of a finished piece
    const val LAST_RING = 50   // last progress value on the shared track; 51..55 is the private home column
    const val BASE = -1
    const val MAX_SIXES = 3

    /** Squares where nobody can be captured: each start square and the star eight steps on from it. */
    val SAFE = setOf(0, 8, 13, 21, 26, 34, 39, 47)

    /** The track as (column, row) on a 15x15 grid, clockwise from red's start square. */
    val RING_XY: List<Pair<Int, Int>> = buildList {
        for (x in 1..5) add(x to 6)
        for (y in 5 downTo 0) add(6 to y)
        add(7 to 0); add(8 to 0)
        for (y in 1..5) add(8 to y)
        for (x in 9..14) add(x to 6)
        add(14 to 7); add(14 to 8)
        for (x in 13 downTo 9) add(x to 8)
        for (y in 9..14) add(8 to y)
        add(7 to 14); add(6 to 14)
        for (y in 13 downTo 9) add(6 to y)
        for (x in 5 downTo 0) add(x to 8)
        add(0 to 7); add(0 to 6)
    }

    fun startIndex(who: Int) = if (who == 1) 0 else 26

    /** The shared-track square (0..51) for progress [p] (0..50), or -1 if the piece isn't on the shared track. */
    fun ringIndex(who: Int, p: Int): Int = if (p in 0..LAST_RING) (startIndex(who) + p) % RING else -1

    /** (column, row) on the 15x15 board for a piece of [who] with progress [p]; base spots are handled by the screen. */
    fun xy(who: Int, p: Int): Pair<Int, Int>? = when {
        p in 0..LAST_RING -> RING_XY[ringIndex(who, p)]
        p in 51..55 -> (if (who == 1) p - 50 else 14 - (p - 50)) to 7
        p == HOME -> (if (who == 1) 6 else 8) to 7
        else -> null
    }

    /** Which pieces of [who] may move with [die] (indexes 0..3). */
    fun movable(mine: IntArray, theirs: IntArray, who: Int, die: Int): List<Int> {
        val out = ArrayList<Int>()
        for (i in 0 until PIECES) {
            val p = mine[i]
            if (p == BASE) {
                if (die == 6) out += i
            } else if (p < HOME && p + die <= HOME) {
                out += i
            }
        }
        return out
    }

    class Moved(val to: Int, val captured: Int, val capturedAt: Int)

    /** Moves piece [i] of [who] by [die]; sends home any lone opposing pieces it lands on (unless the square is safe). */
    fun move(mine: IntArray, theirs: IntArray, who: Int, i: Int, die: Int): Moved {
        val to = if (mine[i] == BASE) 0 else mine[i] + die
        mine[i] = to
        var caught = 0
        var at = -1
        val ring = ringIndex(who, to)
        if (ring >= 0 && ring !in SAFE) {
            for (j in 0 until PIECES) {
                if (ringIndex(3 - who, theirs[j]) == ring) { theirs[j] = BASE; caught++; at = ring }
            }
        }
        return Moved(to, caught, at)
    }
}

data class LudoState(
    val myName: String,
    val theirName: String,
    val phase: GPhase = GPhase.LOBBY,
    val iAmHost: Boolean = true,
    /** Progress of my four pieces: -1 in base, 0..50 on the track, 51..55 in my home column, 56 home. */
    val mine: List<Int> = List(4) { -1 },
    val theirs: List<Int> = List(4) { -1 },
    val myTurn: Boolean = false,
    /** 0 = not rolled yet this go. */
    val die: Int = 0,
    val canRoll: Boolean = false,
    /** The pieces I may move now. */
    val legal: List<Int> = emptyList(),
    /** What just happened: "", out, capture, home, stuck, triple, six. */
    val event: String = "",
    val eventByMe: Boolean = false,
    val lastPiece: Int = -1,
    val sixes: Int = 0,
    val moves: Int = 0,
    val rolls: Int = 0,
    val myHome: Int = 0,
    val theirHome: Int = 0,
    /** 0 playing, 1 I won, 2 they won. */
    val result: Int = 0,
)

class LudoEngine(
    myName: String,
    theirName: String,
    isHost: Boolean,
    send: (GMsg) -> Unit,
    random: Random = Random.Default,
    clock: () -> Long = { System.currentTimeMillis() },
) : SyncEngine(GameKind.LUDO, myName, theirName, isHost, send, random, clock) {

    var state = LudoState(myName, theirName, iAmHost = isHost)
        private set
    override val stateAny: Any get() = state
    override val phase get() = state.phase

    /** Tests can queue the numbers the dice will show. */
    val forcedRolls = ArrayDeque<Int>()

    private val pos = arrayOf(IntArray(4), IntArray(4)) // [0] host, [1] guest
    private var turn = 1
    private var die = 0
    private var sixes = 0
    private var event = ""
    private var eventBy = 0
    private var lastPiece = -1
    private var moves = 0
    private var rolls = 0
    private var over = false
    private var winner = 0
    private var autoAt = -1L

    override fun hostBegin(opts: JSONObject) {
        pos[0].fill(Ludo.BASE); pos[1].fill(Ludo.BASE)
        turn = 1; die = 0; sixes = 0; event = ""; eventBy = 0; lastPiece = -1
        moves = 0; rolls = 0; over = false; winner = 0; autoAt = -1L
    }

    fun roll() = act(JSONObject().put("a", "roll"))
    fun movePiece(i: Int) = act(JSONObject().put("a", "move").put("i", i))

    override fun hostHandle(fromHost: Boolean, a: JSONObject) {
        if (state.phase != GPhase.PLAYING || over) return
        val who = if (fromHost) 1 else 2
        if (turn != who) return
        val me = pos[who - 1]
        val them = pos[2 - who]
        when (a.getString("a")) {
            "roll" -> {
                if (die != 0) return
                die = forcedRolls.removeFirstOrNull() ?: random.nextInt(1, 7)
                rolls++
                event = ""; eventBy = who; lastPiece = -1
                if (die == 6) sixes++
                if (die == 6 && sixes >= Ludo.MAX_SIXES) {
                    event = "triple"
                    autoAt = clock() + 1500L
                } else if (Ludo.movable(me, them, who, die).isEmpty()) {
                    event = "stuck"
                    autoAt = clock() + 1400L
                }
                publish()
            }
            "move" -> {
                if (die == 0 || autoAt >= 0) return
                val i = a.getInt("i")
                if (i !in 0 until Ludo.PIECES || i !in Ludo.movable(me, them, who, die)) return
                val wasBase = me[i] == Ludo.BASE
                val r = Ludo.move(me, them, who, i, die)
                moves++
                lastPiece = i; eventBy = who
                event = when {
                    r.captured > 0 -> "capture"
                    r.to == Ludo.HOME -> "home"
                    wasBase -> "out"
                    else -> ""
                }
                if (me.all { it == Ludo.HOME }) {
                    over = true; winner = who
                } else {
                    nextTurn()
                }
                publish()
            }
        }
    }

    /** After a move (or a pass): a 6 means another go, otherwise it's the other player's. */
    private fun nextTurn() {
        if (die == 6 && sixes < Ludo.MAX_SIXES) {
            // same player rolls again
        } else {
            turn = 3 - turn
            sixes = 0
        }
        die = 0
        autoAt = -1L
    }

    override fun hostTick(now: Long) {
        if (over || autoAt < 0 || now < autoAt) return
        nextTurn()
        publish()
    }

    override fun saveModel(): JSONObject? {
        if (state.phase != GPhase.PLAYING || over) return null
        return JSONObject().put("p1", SaveFormat.ints(pos[0])).put("p2", SaveFormat.ints(pos[1])).put("turn", turn).put("die", die)
            .put("six", sixes).put("ev", event).put("by", eventBy).put("lp", lastPiece).put("mv", moves).put("rl", rolls).put("auto", autoAt >= 0)
    }

    override fun saveSummary(): String = "${nameOf1()}: ${pos[0].count { it == Ludo.HOME }} home · ${nameOf2()}: ${pos[1].count { it == Ludo.HOME }} home · ${moves} moves"

    private fun nameOf1() = if (isHost) myName else theirName
    private fun nameOf2() = if (isHost) theirName else myName

    override fun hostRestore(model: JSONObject): Boolean {
        val a = SaveFormat.readInts(model, "p1", 4, Ludo.BASE..Ludo.HOME) ?: return false
        val b = SaveFormat.readInts(model, "p2", 4, Ludo.BASE..Ludo.HOME) ?: return false
        val t = model.optInt("turn", 0)
        if (t !in 1..2) return false
        a.copyInto(pos[0]); b.copyInto(pos[1])
        turn = t; die = model.optInt("die", 0).coerceIn(0, 6); sixes = model.optInt("six", 0).coerceIn(0, 3)
        event = model.optString("ev", "").take(8); eventBy = model.optInt("by", 0).coerceIn(0, 2); lastPiece = model.optInt("lp", -1).coerceIn(-1, 3)
        moves = model.optInt("mv", 0).coerceIn(0, 1_000_000); rolls = model.optInt("rl", 0).coerceIn(0, 1_000_000)
        over = false; winner = 0
        // a roll that was waiting to be passed on is passed on shortly after resuming
        autoAt = if (model.optBoolean("auto", false)) clock() + 1200L else -1L
        // a piece still to be chosen: make sure that is still possible, otherwise start the go again
        if (die != 0 && autoAt < 0 && Ludo.movable(pos[turn - 1], pos[2 - turn], turn, die).isEmpty()) { die = 0 }
        forcedRolls.clear()
        return true
    }

    override fun hostSnapshot(): JSONObject = JSONObject()
        .put("p1", JSONArray(pos[0].toList())).put("p2", JSONArray(pos[1].toList()))
        .put("turn", turn).put("die", die).put("six", sixes).put("ev", event).put("by", eventBy).put("lp", lastPiece)
        .put("mv", moves).put("rl", rolls).put("over", over).put("win", winner).put("auto", autoAt >= 0)

    override fun adopt(s: JSONObject) {
        val p1 = s.ints("p1", 4).map { it.coerceIn(-1, Ludo.HOME) }
        val p2 = s.ints("p2", 4).map { it.coerceIn(-1, Ludo.HOME) }
        if (p1.size != 4 || p2.size != 4) return
        val me = if (isHost) 1 else 2
        val mine = if (isHost) p1 else p2
        val theirs = if (isHost) p2 else p1
        val over = s.getBoolean("over")
        val turn = s.getInt("turn")
        val die = s.optInt("die", 0).coerceIn(0, 6)
        val auto = s.optBoolean("auto", false)
        val myTurn = !over && turn == me
        val legal = if (myTurn && die > 0 && !auto) Ludo.movable(mine.toIntArray(), theirs.toIntArray(), me, die) else emptyList()
        val win = s.optInt("win", 0)
        state = state.copy(
            phase = if (over) GPhase.OVER else GPhase.PLAYING, iAmHost = isHost,
            mine = mine, theirs = theirs, myTurn = myTurn, die = die,
            canRoll = myTurn && die == 0, legal = legal,
            event = s.optString("ev", "").take(8), eventByMe = s.optInt("by", 0) == me, lastPiece = s.optInt("lp", -1).coerceIn(-1, 3),
            sixes = s.optInt("six", 0).coerceIn(0, 3),
            moves = s.optInt("mv", 0).coerceIn(0, 100_000), rolls = s.optInt("rl", 0).coerceIn(0, 100_000),
            myHome = mine.count { it == Ludo.HOME }, theirHome = theirs.count { it == Ludo.HOME },
            result = if (!over) 0 else if (win == me) 1 else 2,
        )
    }

    override fun reset() {
        state = state.copy(phase = GPhase.LOBBY, mine = List(4) { -1 }, theirs = List(4) { -1 }, die = 0, legal = emptyList(), result = 0, moves = 0, rolls = 0, event = "")
    }
}

class LudoBrain(private val e: LudoEngine, skill: Skill, random: Random) : Brain(skill, random) {
    override fun think(now: Long) {
        val s = e.state
        if (s.phase != GPhase.PLAYING || !s.myTurn) return
        if (s.canRoll) {
            if (ready(now, "roll${s.moves}${s.rolls}", 700, 1400)) e.roll()
        } else if (s.legal.isNotEmpty()) {
            if (ready(now, "move${s.moves}${s.rolls}", 800, 1500)) e.movePiece(LudoAi.choose(s, skill, random))
        }
    }
}

object LudoAi {
    fun choose(s: LudoState, skill: Skill, random: Random): Int {
        if (s.legal.size == 1) return s.legal[0]
        if (skill == Skill.EASY && random.nextInt(100) < 40) return s.legal.random(random)
        val me = if (s.iAmHost) 1 else 2
        val die = s.die
        var best = s.legal[0]
        var bestScore = Int.MIN_VALUE
        for (i in s.legal) {
            val from = s.mine[i]
            val to = if (from == Ludo.BASE) 0 else from + die
            var score = to // advancing is good
            val ring = Ludo.ringIndex(me, to)
            if (to == Ludo.HOME) score += 120
            if (from == Ludo.BASE) score += 60
            if (ring >= 0) {
                val enemyHere = s.theirs.count { Ludo.ringIndex(3 - me, it) == ring }
                if (enemyHere > 0 && ring !in Ludo.SAFE) score += 100 + 8 * (s.theirs.maxOf { if (Ludo.ringIndex(3 - me, it) == ring) it else 0 } / 4)
                if (ring in Ludo.SAFE) score += 25
                score -= danger(s, me, ring) * 28
            }
            if (from in 0..Ludo.LAST_RING) {
                val r0 = Ludo.ringIndex(me, from)
                if (r0 !in Ludo.SAFE) score += danger(s, me, r0) * 22 // escaping a threat
            }
            if (to in 51..55) score += 20
            if (skill == Skill.HARD) score += random.nextInt(0, 3) else score += random.nextInt(0, 12)
            if (score > bestScore) { bestScore = score; best = i }
        }
        return best
    }

    /** How many enemy pieces sit 1..6 squares behind [ring] and could hit it. */
    private fun danger(s: LudoState, me: Int, ring: Int): Int {
        if (ring in Ludo.SAFE) return 0
        var d = 0
        for (t in s.theirs) {
            val tr = Ludo.ringIndex(3 - me, t)
            if (tr < 0) continue
            val gap = (ring - tr + Ludo.RING) % Ludo.RING
            if (gap in 1..6) d++
        }
        // a piece waiting in the enemy base can hit a piece on the enemy start square with a 6
        return d
    }
}
