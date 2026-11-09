package com.neil1454.drawsomething.core

import org.json.JSONObject
import kotlin.random.Random

// ====================================================================================
// Quick Draw: a reflex duel. The screen says WAIT, then flips to GO at a random moment: tap
// first! Tap early and you lose the round. With fake-outs on, sometimes it flashes a decoy
// instead: tap on that and you lose.
//
// Fairness: each phone times its OWN reaction from the moment ITS screen showed GO, so a slow
// connection can't cost anyone a win. The host compares the two times.
// ====================================================================================

enum class QuickMatch(val label: String, val target: Int) {
    BEST3("Best of 3", 2),
    BEST5("Best of 5", 3),
    BEST7("Best of 7", 4),
}

object QuickRules {
    const val MIN_WAIT_MS = 1500L
    const val MAX_WAIT_MS = 5000L
    const val GO_TIMEOUT_MS = 3500L
    const val SECOND_TAP_WINDOW_MS = 600L // how long to wait for the other player's time after the first arrives
    const val FAKE_MS = 1800L
    const val RESULT_MS = 2600L
    const val DECOY_PERCENT = 35
}

data class QuickState(
    val myName: String,
    val theirName: String,
    val phase: GPhase = GPhase.LOBBY,
    /** WAIT, GO, FAKE or RESULT. */
    val step: String = "",
    /** Changes with every new attempt, so screens can tell them apart. */
    val seq: Int = 0,
    val round: Int = 1,
    val target: Int = 2,
    val fakeouts: Boolean = false,
    val myScore: Int = 0,
    val theirScore: Int = 0,
    /** Reaction times in ms; -1 means none recorded. */
    val myMs: Int = -1,
    val theirMs: Int = -1,
    /** Who won the latest attempt: 1 me, 2 them, 0 nobody. */
    val roundWin: Int = 0,
    val why: String = "",
    val iTapped: Boolean = false,
    val moves: Int = 0,
    /** 0 playing, 1 I won, 2 they won. */
    val result: Int = 0,
)

class QuickEngine(
    myName: String,
    theirName: String,
    isHost: Boolean,
    send: (GMsg) -> Unit,
    random: Random = Random.Default,
    clock: () -> Long = { System.currentTimeMillis() },
) : SyncEngine(GameKind.QUICK, myName, theirName, isHost, send, random, clock) {

    var state = QuickState(myName, theirName)
        private set
    override val stateAny: Any get() = state
    override val phase get() = state.phase

    // ---- both phones: my own timing
    private var goLocal = 0L
    private var tapped = false

    // ---- host-only model
    private var step = "WAIT"
    private var seq = 0
    private var round = 1
    private var target = 2
    private var fakeouts = false
    private val score = IntArray(3)
    private val reaction = IntArray(3) { -1 }
    private var goAt = 0L
    private var fakeUntil = 0L
    private var timeoutAt = 0L
    private var firstAt = 0L
    private var nextAt = 0L
    private var decoy = false
    private var win = 0
    private var why = ""
    private var moves = 0
    private var champion = 0

    override fun hostBegin(opts: JSONObject) {
        target = QuickMatch.values()[opts.optInt("match", 1).coerceIn(0, QuickMatch.values().size - 1)].target
        fakeouts = opts.optBoolean("fake", true)
        score.fill(0); round = 1; seq = 0; moves = 0; champion = 0; win = 0; why = ""
        beginAttempt(clock(), publishNow = false)
    }

    private fun beginAttempt(now: Long, publishNow: Boolean = true) {
        seq++
        step = "WAIT"
        goAt = now + random.nextLong(QuickRules.MIN_WAIT_MS, QuickRules.MAX_WAIT_MS)
        decoy = fakeouts && random.nextInt(100) < QuickRules.DECOY_PERCENT
        reaction.fill(-1)
        firstAt = 0L
        win = 0; why = ""
        if (publishNow) publish()
    }

    /** The player tapped. Early taps (WAIT or a decoy) lose the round; a tap on GO records a time. */
    fun tap() {
        val s = state
        if (s.phase != GPhase.PLAYING || tapped) return
        val a = JSONObject().put("a", "tap").put("sq", s.seq)
        when (s.step) {
            "WAIT", "FAKE" -> a.put("ph", "early").put("ms", -1)
            "GO" -> a.put("ph", "go").put("ms", (clock() - goLocal).toInt().coerceIn(1, 5000))
            else -> return
        }
        tapped = true
        state = state.copy(iTapped = true)
        onChange()
        act(a)
    }

    override fun hostHandle(fromHost: Boolean, a: JSONObject) {
        if (state.phase != GPhase.PLAYING || champion != 0) return
        if (a.getString("a") != "tap" || a.getInt("sq") != seq) return
        if (step != "WAIT" && step != "FAKE" && step != "GO") return
        val who = if (fromHost) 1 else 2
        val now = clock()
        if (a.getString("ph") == "early") {
            val fell = if (step == "FAKE") "fell for the fake-out" else "jumped the gun"
            finish(now, winner = 3 - who, why = "${nameOf(who)} $fell!")
            return
        }
        if (step != "GO" || reaction[who] != -1) return
        reaction[who] = a.getInt("ms").coerceIn(1, 5000)
        if (firstAt == 0L) firstAt = now
        if (reaction[1] != -1 && reaction[2] != -1) resolveGo(now) else publish()
    }

    private fun nameOf(who: Int) = if ((who == 1) == isHost) myName else theirName

    override fun hostTick(now: Long) {
        when (step) {
            "WAIT" -> if (now >= goAt) {
                if (decoy) {
                    step = "FAKE"; fakeUntil = now + QuickRules.FAKE_MS
                } else {
                    step = "GO"; timeoutAt = now + QuickRules.GO_TIMEOUT_MS
                }
                publish()
            }
            "FAKE" -> if (now >= fakeUntil) {
                // Nobody fell for it: show a brief message, then try again.
                step = "RESULT"; win = 0; why = "Fake-out! Nobody fell for it."; nextAt = now + 1600
                publish()
            }
            "GO" -> {
                if (firstAt != 0L && now >= firstAt + QuickRules.SECOND_TAP_WINDOW_MS) resolveGo(now)
                else if (now >= timeoutAt) resolveGo(now)
            }
            "RESULT" -> if (champion == 0 && now >= nextAt) beginAttempt(now)
        }
    }

    private fun resolveGo(now: Long) {
        val r1 = reaction[1]
        val r2 = reaction[2]
        when {
            r1 == -1 && r2 == -1 -> finish(now, 0, "Too slow, both of you! Go again.")
            r2 == -1 -> finish(now, 1, "${nameOf(1)} ${r1} ms, ${nameOf(2)} too slow!")
            r1 == -1 -> finish(now, 2, "${nameOf(2)} ${r2} ms, ${nameOf(1)} too slow!")
            r1 == r2 -> finish(now, 0, "Dead heat at $r1 ms! Go again.")
            r1 < r2 -> finish(now, 1, "${nameOf(1)} $r1 ms beat ${nameOf(2)} $r2 ms")
            else -> finish(now, 2, "${nameOf(2)} $r2 ms beat ${nameOf(1)} $r1 ms")
        }
    }

    private fun finish(now: Long, winner: Int, why: String) {
        step = "RESULT"
        win = winner
        this.why = why
        moves++
        nextAt = now + QuickRules.RESULT_MS
        if (winner != 0) {
            score[winner]++
            round++
            if (score[winner] >= target) champion = winner
        }
        publish()
    }

    override fun hostSnapshot(): JSONObject =
        JSONObject().put("st", step).put("sq", seq).put("rd", round).put("tg", target).put("fk", fakeouts)
            .put("s1", score[1]).put("s2", score[2]).put("r1", reaction[1]).put("r2", reaction[2])
            .put("win", win).put("why", why).put("mv", moves).put("ch", champion)

    override fun adopt(s: JSONObject) {
        val me = if (isHost) 1 else 2
        val step = s.getString("st").take(8)
        val seq = s.getInt("sq").coerceIn(0, 1_000_000)
        val fresh = seq != state.seq
        if (fresh) tapped = false
        if (step == "GO" && (fresh || state.step != "GO")) goLocal = clock() // time my reaction from NOW
        val ch = s.optInt("ch", 0).coerceIn(0, 2)
        val win = s.optInt("win", 0)
        state = state.copy(
            phase = if (ch == 0) GPhase.PLAYING else GPhase.OVER,
            step = step, seq = seq, round = s.getInt("rd").coerceIn(1, 100), target = s.getInt("tg").coerceIn(1, 10),
            fakeouts = s.getBoolean("fk"),
            myScore = s.getInt(if (isHost) "s1" else "s2").coerceIn(0, 100),
            theirScore = s.getInt(if (isHost) "s2" else "s1").coerceIn(0, 100),
            myMs = s.getInt(if (isHost) "r1" else "r2").coerceIn(-1, 5000),
            theirMs = s.getInt(if (isHost) "r2" else "r1").coerceIn(-1, 5000),
            roundWin = when (win) { 0 -> 0; me -> 1; else -> 2 },
            why = s.optString("why", "").filter { !it.isISOControl() }.take(80),
            iTapped = if (fresh) false else (tapped || state.iTapped),
            moves = s.optInt("mv", 0).coerceIn(0, 100_000),
            result = when (ch) { 0 -> 0; me -> 1; else -> 2 },
        )
    }

    override fun reset() {
        tapped = false
        state = state.copy(phase = GPhase.LOBBY, step = "", result = 0, iTapped = false, why = "")
    }
}
