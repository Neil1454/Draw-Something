package com.neil1454.drawsomething.core

import org.json.JSONArray
import org.json.JSONObject
import kotlin.random.Random

// ====================================================================================
// Memory Match: flip two cards; a pair scores and you go again, a miss passes the turn.
// Streaks of pairs are worth more. Card positions live ONLY on the host's phone; the partner's
// phone is sent just the cards that are face up.
// ====================================================================================

enum class MemorySize(val label: String, val w: Int, val h: Int) {
    SMALL("Small 4×4", 4, 4),
    MEDIUM("Medium 5×4", 5, 4),
    BIG("Big 6×6", 6, 6),
}

/** Symbol sets. Each has at least 18 symbols (the biggest board needs 18 pairs). */
object MemoryThemes {
    val names = listOf("Animals", "Food", "Space & sky", "Faces")
    val symbols: List<List<String>> = listOf(
        listOf("🐶", "🐱", "🦊", "🐼", "🐸", "🐙", "🦁", "🐵", "🐧", "🦄", "🐢", "🦋", "🐝", "🐬", "🦉", "🐞", "🐳", "🦒"),
        listOf("🍎", "🍌", "🍓", "🍕", "🍔", "🍩", "🍉", "🍇", "🥑", "🌮", "🍪", "🍒", "🥕", "🧁", "🍍", "🌽", "🍿", "🥐"),
        listOf("🚀", "🌙", "⭐", "🪐", "☀️", "🌈", "☁️", "⚡", "🔥", "❄️", "🌍", "☄️", "🛸", "🌊", "🌸", "🍀", "🎈", "💎"),
        listOf("😀", "😎", "🥳", "😍", "🤩", "😴", "🤯", "😱", "🥶", "🤠", "👻", "🤖", "👽", "😈", "🥸", "🤡", "😇", "🫠"),
    )
}

object MemoryRules {
    const val PAIR_POINTS = 10
    const val STREAK_BONUS = 5 // extra for each consecutive pair after the first
    const val PEEK_MS = 1300L  // how long a mismatch stays visible
}

data class MemoryState(
    val myName: String,
    val theirName: String,
    val phase: GPhase = GPhase.LOBBY,
    val w: Int = 4,
    val h: Int = 4,
    val theme: Int = 0,
    /** Per card: '.' face down, otherwise a symbol (0-9, a-z). */
    val view: String = "",
    /** Per card: '.' unmatched, '1' host got it, '2' partner got it. */
    val owners: String = "",
    val up: List<Int> = emptyList(),
    val myTurn: Boolean = false,
    val myScore: Int = 0,
    val theirScore: Int = 0,
    val myStreak: Int = 0,
    val theirStreak: Int = 0,
    /** "match", "miss" or "". */
    val event: String = "",
    val eventByMe: Boolean = false,
    val eventPoints: Int = 0,
    val pairsLeft: Int = 0,
    val moves: Int = 0,
    /** 0 playing, 1 I won, 2 they won, 3 draw. */
    val result: Int = 0,
)

class MemoryEngine(
    myName: String,
    theirName: String,
    isHost: Boolean,
    send: (GMsg) -> Unit,
    random: Random = Random.Default,
    clock: () -> Long = { System.currentTimeMillis() },
) : SyncEngine(GameKind.MEMORY, myName, theirName, isHost, send, random, clock) {

    var state = MemoryState(myName, theirName)
        private set
    override val stateAny: Any get() = state
    override val phase get() = state.phase

    // ---- host-only model
    private var w = 4
    private var h = 4
    private var theme = 0
    private var cards = IntArray(0)
    private var owner = IntArray(0)
    private var up = ArrayList<Int>()
    private var turn = 1
    private val score = IntArray(3)
    private val streak = IntArray(3)
    private var hideAt = 0L
    private var event = ""
    private var eventBy = 0
    private var eventPoints = 0
    private var moves = 0

    override fun hostBegin(opts: JSONObject) {
        val size = MemorySize.values()[opts.optInt("size", 0).coerceIn(0, MemorySize.values().size - 1)]
        theme = opts.optInt("theme", 0).coerceIn(0, MemoryThemes.symbols.size - 1)
        w = size.w; h = size.h
        val pairs = w * h / 2
        cards = (0 until pairs).flatMap { listOf(it, it) }.shuffled(random).toIntArray()
        owner = IntArray(cards.size)
        up = ArrayList()
        turn = if (random.nextBoolean()) 1 else 2
        score.fill(0); streak.fill(0)
        hideAt = 0L; event = ""; eventBy = 0; eventPoints = 0; moves = 0
    }

    fun flip(index: Int) {
        act(JSONObject().put("i", index))
    }

    override fun hostHandle(fromHost: Boolean, a: JSONObject) {
        if (state.phase != GPhase.PLAYING || hideAt != 0L) return
        val who = if (fromHost) 1 else 2
        if (turn != who) return
        val i = a.getInt("i")
        if (i !in cards.indices || owner[i] != 0 || i in up || up.size >= 2) return
        up += i
        moves++
        event = ""; eventPoints = 0
        if (up.size == 2) {
            if (cards[up[0]] == cards[up[1]]) {
                streak[who]++
                val pts = MemoryRules.PAIR_POINTS + MemoryRules.STREAK_BONUS * (streak[who] - 1)
                score[who] += pts
                owner[up[0]] = who; owner[up[1]] = who
                up.clear()
                event = "match"; eventBy = who; eventPoints = pts
            } else {
                streak[who] = 0
                event = "miss"; eventBy = who
                hideAt = clock() + MemoryRules.PEEK_MS
            }
        }
        publish()
    }

    override fun hostTick(now: Long) {
        if (hideAt != 0L && now >= hideAt) {
            hideAt = 0L
            up.clear()
            turn = 3 - turn
            event = ""
            publish()
        }
    }

    private fun sym(c: Int): Char = Character.forDigit(c, 36)

    override fun hostSnapshot(): JSONObject {
        val v = StringBuilder()
        val o = StringBuilder()
        for (i in cards.indices) {
            v.append(if (owner[i] != 0 || i in up) sym(cards[i]) else '.')
            o.append(if (owner[i] == 0) '.' else ('0' + owner[i]))
        }
        val over = owner.all { it != 0 }
        return JSONObject().put("w", w).put("h", h).put("th", theme).put("v", v.toString()).put("o", o.toString())
            .put("up", JSONArray(up)).put("turn", turn).put("s1", score[1]).put("s2", score[2])
            .put("k1", streak[1]).put("k2", streak[2]).put("ev", event).put("by", eventBy).put("pt", eventPoints)
            .put("mv", moves).put("over", over)
    }

    override fun adopt(s: JSONObject) {
        val nw = s.getInt("w").coerceIn(2, 8)
        val nh = s.getInt("h").coerceIn(2, 8)
        val v = s.getString("v")
        val o = s.getString("o")
        if (v.length != nw * nh || o.length != nw * nh || o.any { it !in ".12" }) return
        if (v.any { it != '.' && !it.isLetterOrDigit() }) return
        val me = if (isHost) 1 else 2
        val over = s.getBoolean("over")
        val mine = s.getInt(if (isHost) "s1" else "s2")
        val theirs = s.getInt(if (isHost) "s2" else "s1")
        val by = s.optInt("by", 0)
        state = state.copy(
            phase = if (over) GPhase.OVER else GPhase.PLAYING,
            w = nw, h = nh, theme = s.getInt("th").coerceIn(0, MemoryThemes.symbols.size - 1),
            view = v, owners = o, up = s.ints("up", 4).filter { it in 0 until nw * nh },
            myTurn = !over && s.getInt("turn") == me,
            myScore = mine.coerceIn(0, 100_000), theirScore = theirs.coerceIn(0, 100_000),
            myStreak = s.getInt(if (isHost) "k1" else "k2").coerceIn(0, 100),
            theirStreak = s.getInt(if (isHost) "k2" else "k1").coerceIn(0, 100),
            event = s.optString("ev", "").take(8), eventByMe = by == me, eventPoints = s.optInt("pt", 0).coerceIn(0, 10_000),
            pairsLeft = o.count { it == '.' } / 2, moves = s.optInt("mv", 0).coerceIn(0, 100_000),
            result = if (!over) 0 else if (mine > theirs) 1 else if (mine < theirs) 2 else 3,
        )
    }

    override fun reset() {
        state = state.copy(phase = GPhase.LOBBY, view = "", owners = "", up = emptyList(), result = 0, moves = 0, event = "")
    }
}
