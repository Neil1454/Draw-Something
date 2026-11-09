package com.neil1454.drawsomething.core

import org.json.JSONArray
import org.json.JSONObject
import kotlin.random.Random

// ====================================================================================
// Lucky Dice: push your luck. On your turn keep rolling to build up points, then BANK them
// to keep them. Roll a 1 and you lose everything you were holding and your turn.
// Two-dice mode: double 1s wipes your WHOLE score, one 1 is a bust, doubles count double.
// First to the target (banked) wins.
// ====================================================================================

enum class DiceTarget(val label: String, val points: Int) {
    QUICK("Quick: 50", 50),
    NORMAL("Normal: 100", 100),
    LONG("Long: 150", 150),
}

data class DiceState(
    val myName: String,
    val theirName: String,
    val phase: GPhase = GPhase.LOBBY,
    val target: Int = 100,
    val two: Boolean = false,
    val myScore: Int = 0,
    val theirScore: Int = 0,
    /** Points held this turn but not banked yet. */
    val held: Int = 0,
    val myTurn: Boolean = false,
    val dice: List<Int> = emptyList(),
    /** roll, doubles, bust, snake, bank, "". */
    val event: String = "",
    val eventByMe: Boolean = false,
    val eventPoints: Int = 0,
    val moves: Int = 0,
    /** 0 playing, 1 I won, 2 they won. */
    val result: Int = 0,
)

class DiceEngine(
    myName: String,
    theirName: String,
    isHost: Boolean,
    send: (GMsg) -> Unit,
    random: Random = Random.Default,
    clock: () -> Long = { System.currentTimeMillis() },
) : SyncEngine(GameKind.DICE, myName, theirName, isHost, send, random, clock) {

    var state = DiceState(myName, theirName)
        private set
    override val stateAny: Any get() = state
    override val phase get() = state.phase

    private var target = 100
    private var two = false
    private val score = IntArray(3)
    private var held = 0
    private var turn = 1
    private var dice = listOf(1, 1)
    private var event = ""
    private var eventBy = 0
    private var eventPoints = 0
    private var moves = 0
    private var winner = 0

    override fun hostBegin(opts: JSONObject) {
        target = DiceTarget.values()[opts.optInt("target", 1).coerceIn(0, DiceTarget.values().size - 1)].points
        two = opts.optBoolean("two", false)
        score.fill(0); held = 0
        turn = if (random.nextBoolean()) 1 else 2
        dice = listOf(1, 1); event = ""; eventBy = 0; eventPoints = 0; moves = 0; winner = 0
    }

    fun roll() = act(JSONObject().put("a", "roll"))
    fun bank() = act(JSONObject().put("a", "bank"))

    override fun hostHandle(fromHost: Boolean, a: JSONObject) {
        if (state.phase != GPhase.PLAYING || winner != 0) return
        val who = if (fromHost) 1 else 2
        if (turn != who) return
        when (a.getString("a")) {
            "roll" -> {
                val d1 = random.nextInt(1, 7)
                val d2 = if (two) random.nextInt(1, 7) else 0
                dice = if (two) listOf(d1, d2) else listOf(d1)
                moves++
                when {
                    two && d1 == 1 && d2 == 1 -> {
                        val lost = score[who]
                        score[who] = 0; held = 0; turn = 3 - who
                        event = "snake"; eventPoints = lost
                    }
                    d1 == 1 || (two && d2 == 1) -> {
                        val lost = held
                        held = 0; turn = 3 - who
                        event = "bust"; eventPoints = lost
                    }
                    else -> {
                        val sum = d1 + d2
                        val gain = if (two && d1 == d2) sum * 2 else sum
                        held += gain
                        event = if (two && d1 == d2) "doubles" else "roll"
                        eventPoints = gain
                    }
                }
                eventBy = who
                publish()
            }
            "bank" -> {
                if (held == 0) return
                score[who] += held
                eventPoints = held
                held = 0
                event = "bank"; eventBy = who
                moves++
                if (score[who] >= target) winner = who else turn = 3 - who
                publish()
            }
        }
    }

    override fun hostSnapshot(): JSONObject =
        JSONObject().put("tg", target).put("two", two).put("s1", score[1]).put("s2", score[2]).put("held", held)
            .put("turn", turn).put("d", JSONArray(dice)).put("ev", event).put("by", eventBy).put("pt", eventPoints)
            .put("mv", moves).put("win", winner)

    override fun adopt(s: JSONObject) {
        val me = if (isHost) 1 else 2
        val win = s.getInt("win").coerceIn(0, 2)
        state = state.copy(
            phase = if (win == 0) GPhase.PLAYING else GPhase.OVER,
            target = s.getInt("tg").coerceIn(10, 1000), two = s.getBoolean("two"),
            myScore = s.getInt(if (isHost) "s1" else "s2").coerceIn(0, 100_000),
            theirScore = s.getInt(if (isHost) "s2" else "s1").coerceIn(0, 100_000),
            held = s.getInt("held").coerceIn(0, 100_000),
            myTurn = win == 0 && s.getInt("turn") == me,
            dice = s.ints("d", 2).map { it.coerceIn(1, 6) },
            event = s.optString("ev", "").take(8), eventByMe = s.optInt("by", 0) == me,
            eventPoints = s.optInt("pt", 0).coerceIn(0, 100_000), moves = s.optInt("mv", 0).coerceIn(0, 100_000),
            result = when (win) { 0 -> 0; me -> 1; else -> 2 },
        )
    }

    override fun reset() {
        state = state.copy(phase = GPhase.LOBBY, result = 0, held = 0, dice = emptyList(), event = "", moves = 0)
    }
}
