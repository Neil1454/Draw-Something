package com.neil1454.drawsomething.core

import org.json.JSONArray
import org.json.JSONObject
import kotlin.random.Random

// ====================================================================================
// Higher or Lower, in the spirit of the TV game show. Each turn you get a row of seven cards:
// the first is face up and you guess whether the NEXT card is higher or lower. Every right guess
// climbs the prize ladder (10, 20, 30 ... 60 points) and clear the whole row for a bonus. A wrong
// guess, or a card of the SAME value, ends your row (you keep what you won). Then your partner gets
// a fresh row. Aces are high. You may CHANGE the face-up card once per game.
// The deck and the face-down cards live only on the host's phone.
// ====================================================================================

enum class HiLoRows(val label: String, val rows: Int) {
    SHORT("3 rows each", 3),
    NORMAL("5 rows each", 5),
    LONG("7 rows each", 7),
}

object HiLoRules {
    const val ROW_LENGTH = 7        // cards in a row; the first is face up, so there are 6 guesses
    const val STEP_POINTS = 10      // guess number k is worth k * 10
    const val FULL_ROW_BONUS = 50
    const val SHOW_MS = 2200L       // how long a finished row stays on show

    /** 2..14, ace high. */
    fun rank(card: Int) = card % 13 + 2

    /** 0 spades, 1 hearts, 2 diamonds, 3 clubs. */
    fun suit(card: Int) = card / 13

    fun rankLabel(card: Int) = when (val r = rank(card)) { 11 -> "J"; 12 -> "Q"; 13 -> "K"; 14 -> "A"; else -> "$r" }
    fun suitSymbol(card: Int) = "♠♥♦♣"[suit(card)].toString()
    fun cardName(card: Int) = rankLabel(card) + suitSymbol(card)
    fun isRed(card: Int) = suit(card) == 1 || suit(card) == 2
}

data class HiLoState(
    val myName: String,
    val theirName: String,
    val phase: GPhase = GPhase.LOBBY,
    val rowsEach: Int = 5,
    val showOdds: Boolean = true,
    /** Seven entries: a card (0..51) if face up, -1 if face down. */
    val row: List<Int> = emptyList(),
    /** The face-up card you are guessing from. */
    val idx: Int = 0,
    /** It's my row. */
    val myTurn: Boolean = false,
    val myScore: Int = 0,
    val theirScore: Int = 0,
    /** Points won on the current row so far. */
    val run: Int = 0,
    val guesses: Int = 0,
    /** GUESS (waiting for Higher/Lower) or SHOW (the row has ended). */
    val step: String = "",
    /** hit, full, miss or same (for the latest guess). */
    val result: String = "",
    val resultByMe: Boolean = false,
    val myChange: Boolean = true,
    val theirChange: Boolean = true,
    /** Chance (percent) the next card is higher / lower, from the cards still unseen; -1 if odds are off. */
    val pHigher: Int = -1,
    val pLower: Int = -1,
    val myRowsDone: Int = 0,
    val theirRowsDone: Int = 0,
    val moves: Int = 0,
    /** 0 playing, 1 I won, 2 they won, 3 draw. */
    val matchResult: Int = 0,
)

class HiLoEngine(
    myName: String,
    theirName: String,
    isHost: Boolean,
    send: (GMsg) -> Unit,
    random: Random = Random.Default,
    clock: () -> Long = { System.currentTimeMillis() },
) : SyncEngine(GameKind.HILO, myName, theirName, isHost, send, random, clock) {

    var state = HiLoState(myName, theirName)
        private set
    override val stateAny: Any get() = state
    override val phase get() = state.phase

    // ---- host-only model
    private val deck = ArrayList<Int>()
    private var row = IntArray(HiLoRules.ROW_LENGTH)
    private var idx = 0
    private var turn = 1
    private val score = IntArray(3)
    private val rowsDone = IntArray(3)
    private val changeLeft = BooleanArray(3)
    private var rows = 5
    private var odds = true
    private var run = 0
    private var guesses = 0
    private var step = "GUESS"
    private var result = ""
    private var resultBy = 0
    private var showUntil = 0L
    private var moves = 0
    private var over = false

    override fun hostBegin(opts: JSONObject) {
        rows = HiLoRows.values()[opts.optInt("rows", 1).coerceIn(0, HiLoRows.values().size - 1)].rows
        odds = opts.optBoolean("odds", true)
        score.fill(0); rowsDone.fill(0); changeLeft.fill(true)
        turn = if (random.nextBoolean()) 1 else 2
        moves = 0; over = false
        deck.clear()
        dealRow()
    }

    private fun draw(): Int {
        if (deck.isEmpty()) deck.addAll((0 until 52).shuffled(random))
        return deck.removeAt(deck.lastIndex)
    }

    private fun dealRow() {
        row = IntArray(HiLoRules.ROW_LENGTH) { draw() }
        idx = 0; run = 0; guesses = 0; step = "GUESS"; result = ""; resultBy = 0
    }

    fun guess(higher: Boolean) = act(JSONObject().put("a", if (higher) "higher" else "lower"))

    /** Swap the face-up card for a new one (once per game). */
    fun change() = act(JSONObject().put("a", "change"))

    override fun hostHandle(fromHost: Boolean, a: JSONObject) {
        if (state.phase != GPhase.PLAYING || over || step != "GUESS") return
        val who = if (fromHost) 1 else 2
        if (turn != who) return
        when (a.getString("a")) {
            "change" -> {
                if (!changeLeft[who]) return
                changeLeft[who] = false
                row[idx] = draw()
                moves++
                result = ""; resultBy = 0
                publish()
            }
            "higher", "lower" -> {
                val higher = a.getString("a") == "higher"
                val before = HiLoRules.rank(row[idx])
                val next = HiLoRules.rank(row[idx + 1])
                val correct = if (higher) next > before else next < before
                idx++
                moves++
                resultBy = who
                if (correct) {
                    guesses++
                    val won = HiLoRules.STEP_POINTS * guesses
                    run += won; score[who] += won
                    if (idx == HiLoRules.ROW_LENGTH - 1) {
                        run += HiLoRules.FULL_ROW_BONUS; score[who] += HiLoRules.FULL_ROW_BONUS
                        result = "full"; step = "SHOW"; showUntil = clock() + HiLoRules.SHOW_MS
                    } else {
                        result = "hit"
                    }
                } else {
                    result = if (next == before) "same" else "miss"
                    step = "SHOW"; showUntil = clock() + HiLoRules.SHOW_MS
                }
                publish()
            }
        }
    }

    override fun hostTick(now: Long) {
        if (step == "SHOW" && !over && now >= showUntil) {
            rowsDone[turn]++
            if (rowsDone[1] >= rows && rowsDone[2] >= rows) {
                over = true
            } else {
                turn = 3 - turn
                dealRow()
            }
            publish()
        }
    }

    /** Percent chance the next card is higher / lower, from every card not yet seen. */
    private fun oddsNow(): Pair<Int, Int> {
        if (!odds || step != "GUESS" || idx >= HiLoRules.ROW_LENGTH - 1) return -1 to -1
        val unseen = ArrayList<Int>(deck)
        for (i in idx + 1 until HiLoRules.ROW_LENGTH) unseen += row[i]
        if (unseen.isEmpty()) return -1 to -1
        val r = HiLoRules.rank(row[idx])
        val higher = unseen.count { HiLoRules.rank(it) > r }
        val lower = unseen.count { HiLoRules.rank(it) < r }
        return Math.round(100.0 * higher / unseen.size).toInt() to Math.round(100.0 * lower / unseen.size).toInt()
    }

    override fun hostSnapshot(): JSONObject {
        val shown = JSONArray()
        for (i in 0 until HiLoRules.ROW_LENGTH) shown.put(if (i <= idx) row[i] else -1)
        val (ph, pl) = oddsNow()
        return JSONObject().put("rows", rows).put("odds", odds).put("row", shown).put("idx", idx).put("turn", turn)
            .put("s1", score[1]).put("s2", score[2]).put("run", run).put("g", guesses).put("step", step)
            .put("res", result).put("by", resultBy).put("c1", changeLeft[1]).put("c2", changeLeft[2])
            .put("ph", ph).put("pl", pl).put("d1", rowsDone[1]).put("d2", rowsDone[2]).put("mv", moves).put("over", over)
    }

    override fun adopt(s: JSONObject) {
        val me = if (isHost) 1 else 2
        val row = s.ints("row", HiLoRules.ROW_LENGTH)
        if (row.size != HiLoRules.ROW_LENGTH || row.any { it !in -1..51 }) return
        val over = s.getBoolean("over")
        val mine = s.getInt(if (isHost) "s1" else "s2").coerceIn(0, 100_000)
        val theirs = s.getInt(if (isHost) "s2" else "s1").coerceIn(0, 100_000)
        state = state.copy(
            phase = if (over) GPhase.OVER else GPhase.PLAYING,
            rowsEach = s.getInt("rows").coerceIn(1, 20), showOdds = s.getBoolean("odds"),
            row = row, idx = s.getInt("idx").coerceIn(0, HiLoRules.ROW_LENGTH - 1),
            myTurn = !over && s.getInt("turn") == me,
            myScore = mine, theirScore = theirs,
            run = s.getInt("run").coerceIn(0, 100_000), guesses = s.getInt("g").coerceIn(0, 20),
            step = s.getString("step").take(6), result = s.optString("res", "").take(6),
            resultByMe = s.optInt("by", 0) == me,
            myChange = s.getBoolean(if (isHost) "c1" else "c2"), theirChange = s.getBoolean(if (isHost) "c2" else "c1"),
            pHigher = s.optInt("ph", -1).coerceIn(-1, 100), pLower = s.optInt("pl", -1).coerceIn(-1, 100),
            myRowsDone = s.getInt(if (isHost) "d1" else "d2").coerceIn(0, 20),
            theirRowsDone = s.getInt(if (isHost) "d2" else "d1").coerceIn(0, 20),
            moves = s.optInt("mv", 0).coerceIn(0, 100_000),
            matchResult = if (!over) 0 else if (mine > theirs) 1 else if (mine < theirs) 2 else 3,
        )
    }

    override fun reset() {
        state = state.copy(phase = GPhase.LOBBY, row = emptyList(), step = "", result = "", matchResult = 0, moves = 0)
    }

    // ---- for tests only
    internal fun secretRow(): List<Int> = row.toList()
}

class HiLoBrain(private val e: HiLoEngine, skill: Skill, random: Random) : Brain(skill, random) {
    override fun think(now: Long) {
        val s = e.state
        if (s.phase != GPhase.PLAYING || !s.myTurn || s.step != "GUESS" || s.row.isEmpty()) return
        if (!ready(now, s.moves, 1000, 1800)) return
        when (HiLoAi.decide(s, skill, random)) {
            HiLoAi.Move.CHANGE -> e.change()
            HiLoAi.Move.HIGHER -> e.guess(true)
            HiLoAi.Move.LOWER -> e.guess(false)
        }
    }
}

object HiLoAi {
    enum class Move { HIGHER, LOWER, CHANGE }

    fun decide(s: HiLoState, skill: Skill, random: Random): Move {
        val r = HiLoRules.rank(s.row[s.idx])
        // a middling card is a coin-flip: use the one-off Change if we still have it
        if (s.myChange && skill != Skill.EASY && s.guesses == 0 && r in 7..9 && random.nextInt(100) < 60) return Move.CHANGE
        // use the odds if the screen shows them, otherwise estimate from the card alone
        val higher = if (s.pHigher >= 0) s.pHigher.toDouble() else (14 - r) * 4 / 51.0 * 100
        val lower = if (s.pLower >= 0) s.pLower.toDouble() else (r - 2) * 4 / 51.0 * 100
        val goHigher = if (skill == Skill.EASY && random.nextInt(100) < 25) random.nextBoolean() else higher >= lower
        return if (goHigher) Move.HIGHER else Move.LOWER
    }
}
