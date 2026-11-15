package com.neil1454.drawsomething.core

import org.json.JSONArray
import org.json.JSONObject
import kotlin.math.pow
import kotlin.random.Random

// ====================================================================================
// Liar's Dice. You each shake your own dice under a cup, so only you can see them. Take turns making a bid about ALL the dice on
// the table ("four 5s"), each bid higher than the last (more dice, or the same number of a higher face). Instead of bidding you can
// call "LIAR!". Everyone's dice are revealed: if there are at least as many as the bid said, the bidder was right and the caller
// loses a die; if not, the bidder loses one. Lose all your dice and you're out.
// Each phone rolls its own dice, so your dice never leave your phone until somebody calls liar.
// ====================================================================================

object LiarMath {
    const val FACES = 6
    val DICE_CHOICES = listOf(3, 5)

    /** How many dice of [face] are in [dice] (ones count for everything when [wild] is on). */
    fun count(dice: List<Int>, face: Int, wild: Boolean) = dice.count { it == face || (wild && face != 1 && it == 1) }

    /** The chance that at least [need] of [n] unknown dice show a given face (each with chance [p]). */
    fun atLeast(need: Int, n: Int, p: Double): Double {
        if (need <= 0) return 1.0
        if (need > n) return 0.0
        var sum = 0.0
        for (k in need..n) sum += choose(n, k) * p.pow(k) * (1 - p).pow(n - k)
        return sum
    }

    private fun choose(n: Int, k: Int): Double {
        var r = 1.0
        for (i in 1..k) r = r * (n - k + i) / i
        return r
    }

    /** The chance for each unknown die to count towards [face]. */
    fun chance(face: Int, wild: Boolean) = if (wild && face != 1) 1.0 / 3.0 else 1.0 / 6.0

    /** Is (q, f) a higher bid than (cq, cf)? */
    fun higher(q: Int, f: Int, cq: Int, cf: Int) = q > cq || (q == cq && f > cf)
}

class LiarBid(val qty: Int, val face: Int, /** 1 me, 2 them. */ val by: Int)

data class LiarState(
    val phase: GPhase = GPhase.LOBBY,
    /** BID (a round is on), REVEAL (waiting for the dice), SHOW (the dice are out), END. */
    val stage: String = "BID",
    val round: Int = 0,
    val myCount: Int = 5,
    val theirCount: Int = 5,
    val myDice: List<Int> = emptyList(),
    /** Their dice, only once somebody has called liar. */
    val theirDice: List<Int> = emptyList(),
    val bid: LiarBid? = null,
    val myTurn: Boolean = false,
    val wild: Boolean = false,
    val history: List<String> = emptyList(),
    /** After a call: how many dice really matched, whether the bid was good, and who lost a die (1 me, 2 them). */
    val actual: Int = 0,
    val bidGood: Boolean = false,
    val loser: Int = 0,
    val caller: Int = 0,
    val myAck: Boolean = false,
    val theirAck: Boolean = false,
    val result: Int = 0,
    val iAmHost: Boolean = true,
) {
    val myScore get() = myCount
    val theirScore get() = theirCount
    val total get() = myCount + theirCount
}

class LiarDiceEngine(
    myName: String,
    theirName: String,
    isHost: Boolean,
    send: (GMsg) -> Unit,
    random: Random = Random.Default,
    clock: () -> Long = { System.currentTimeMillis() },
) : SyncEngine(GameKind.LIARSDICE, myName, theirName, isHost, send, random, clock) {

    var state = LiarState(iAmHost = isHost)
        private set
    override val stateAny: Any get() = state
    override val phase get() = state.phase

    fun bid(qty: Int, face: Int) = act(JSONObject().put("a", "bid").put("q", qty).put("f", face))
    fun callLiar() = act(JSONObject().put("a", "call"))
    fun ack() = act(JSONObject().put("a", "ack"))

    // ---- on every phone: my own dice, rolled here and never sent until they are asked for
    private var myDice: List<Int> = emptyList()
    private var rolledFor = -1
    private var rolledGame = -1
    private var sentFor = -1

    // ---- the host's game
    private val counts = IntArray(3)
    private var wild = false
    private var round = 0
    private var gameId = 0
    private var stage = "BID"
    private var turn = 1
    private var bid: LiarBid? = null     // by = 1 host, 2 guest
    private val hands = arrayOf<List<Int>?>(null, null, null)
    private val acks = BooleanArray(3)
    private var actual = 0
    private var bidGood = false
    private var loser = 0
    private var caller = 0
    private var over = false
    private var result = 0
    private val log = ArrayList<String>()

    private fun nameOf(p: Int) = if (p == 1) (if (isHost) myName else theirName) else (if (isHost) theirName else myName)
    private fun say(s: String) { log.add(s); while (log.size > 8) log.removeAt(0) }

    override fun hostBegin(opts: JSONObject) {
        val n = LiarMath.DICE_CHOICES.getOrElse(opts.optInt("dice", 1)) { 5 }
        wild = opts.optBoolean("wild", false)
        counts[1] = n; counts[2] = n
        round = 1; gameId++; stage = "BID"; bid = null; acks.fill(false); hands.fill(null); over = false; result = 0; loser = 0; caller = 0
        turn = if (random.nextBoolean()) 1 else 2
        log.clear()
        say("Round 1: ${nameOf(turn)} bids first.")
    }

    override fun hostHandle(fromHost: Boolean, a: JSONObject) {
        if (state.phase != GPhase.PLAYING || over) return
        val p = if (fromHost) 1 else 2
        when (a.getString("a")) {
            "bid" -> if (stage == "BID" && turn == p) {
                val q = a.optInt("q", 0)
                val f = a.optInt("f", 0)
                val total = counts[1] + counts[2]
                if (q < 1 || q > total || f !in 1..LiarMath.FACES) return
                val cur = bid
                if (cur != null && !LiarMath.higher(q, f, cur.qty, cur.face)) return
                bid = LiarBid(q, f, p)
                turn = 3 - p
                say("${nameOf(p)} bids $q × $f.")
                publish()
            }
            "call" -> if (stage == "BID" && turn == p && bid != null) {
                caller = p; stage = "REVEAL"; hands.fill(null)
                say("${nameOf(p)} calls LIAR!")
                publish()
            }
            "dice" -> if (stage == "REVEAL") {
                val d = a.optJSONArray("d") ?: return
                val list = List(minOf(d.length(), 5)) { d.optInt(it, 1).coerceIn(1, 6) }
                if (list.size != counts[p]) return
                hands[p] = list
                if (hands[1] != null && hands[2] != null) settle() else publish()
            }
            "ack" -> if (stage == "SHOW") {
                acks[p] = true
                if (acks[1] && acks[2]) nextRound() else publish()
            }
        }
    }

    private fun settle() {
        val b = bid ?: return
        actual = LiarMath.count(hands[1]!!, b.face, wild) + LiarMath.count(hands[2]!!, b.face, wild)
        bidGood = actual >= b.qty
        loser = if (bidGood) caller else b.by
        counts[loser]--
        say("There were $actual × ${b.face}: the bid was ${if (bidGood) "good" else "a bluff"}. ${nameOf(loser)} loses a die.")
        stage = "SHOW"; acks.fill(false)
        if (counts[loser] <= 0) { over = true; result = if (loser == 1) 2 else 1; stage = "END"; say("${nameOf(3 - loser)} wins!") }
        publish()
    }

    private fun nextRound() {
        round++; bid = null; hands.fill(null); stage = "BID"; acks.fill(false)
        turn = loser
        say("Round $round: ${nameOf(turn)} bids first.")
        publish()
    }

    override fun hostSnapshot(): JSONObject {
        fun arr(l: List<Int>?) = JSONArray().also { a -> l?.forEach { a.put(it) } }
        val show = stage == "SHOW" || stage == "END"
        val bj = bid?.let { JSONArray().put(it.qty).put(it.face).put(it.by) }
        return JSONObject()
            .put("st", stage).put("rd", round).put("gid", gameId).put("c", arr(listOf(counts[1], counts[2]))).put("w", wild)
            .put("tn", turn).put("bd", bj ?: JSONArray()).put("cl", caller)
            .put("h1", arr(if (show) hands[1] else null)).put("h2", arr(if (show) hands[2] else null))
            .put("ac", actual).put("bg", bidGood).put("ls", loser).put("ak", JSONArray().put(acks[1]).put(acks[2]))
            .put("lg", JSONArray(log)).put("ov", over).put("res", result)
    }

    private fun ints(a: JSONArray?, max: Int = 6): List<Int> {
        if (a == null) return emptyList()
        return List(minOf(a.length(), max)) { a.optInt(it, 1).coerceIn(1, 6) }
    }

    override fun adopt(s: JSONObject) {
        val me = if (isHost) 1 else 2
        val c = s.optJSONArray("c")
        val myC = (c?.optInt(me - 1, 5) ?: 5).coerceIn(0, 5)
        val theirC = (c?.optInt(2 - me, 5) ?: 5).coerceIn(0, 5)
        val gid = s.optInt("gid", 0)
        if (gid != rolledGame) { rolledGame = gid; rolledFor = -1; sentFor = -1; myDice = emptyList() }
        val bd = s.optJSONArray("bd")
        val bid = if (bd != null && bd.length() >= 3) LiarBid(bd.optInt(0, 1).coerceIn(1, 10), bd.optInt(1, 1).coerceIn(1, 6), if (bd.optInt(2, 1) == me) 1 else 2) else null
        val ak = s.optJSONArray("ak")
        val ov = s.optBoolean("ov", false)
        val stage = s.optString("st", "BID").take(6)
        val res = s.optInt("res", 0).coerceIn(0, 2)
        val ls = s.optInt("ls", 0).coerceIn(0, 2)
        val cl = s.optInt("cl", 0).coerceIn(0, 2)
        val logArr = s.optJSONArray("lg")
        state = LiarState(
            phase = if (ov) GPhase.OVER else GPhase.PLAYING, stage = stage, round = s.optInt("rd", 1).coerceIn(1, 200),
            myCount = myC, theirCount = theirC,
            myDice = if (rolledFor == s.optInt("rd", 1) || stage == "SHOW" || stage == "END") myDice else emptyList(),
            theirDice = ints(s.optJSONArray(if (isHost) "h2" else "h1")),
            bid = bid, myTurn = s.optInt("tn", 1) == me && !ov, wild = s.optBoolean("w", false),
            history = if (logArr == null) emptyList() else List(minOf(logArr.length(), 8)) { logArr.optString(it, "").take(140) },
            actual = s.optInt("ac", 0).coerceIn(0, 12), bidGood = s.optBoolean("bg", false),
            loser = if (ls == 0) 0 else if (ls == me) 1 else 2, caller = if (cl == 0) 0 else if (cl == me) 1 else 2,
            myAck = ak?.optBoolean(me - 1, false) ?: false, theirAck = ak?.optBoolean(2 - me, false) ?: false,
            result = if (!ov) 0 else if (res == me) 1 else 2, iAmHost = isHost,
        )
    }

    /** Shake my dice at the start of every round, and hand them over when somebody calls liar. */
    override fun localTick(now: Long) {
        val st = state
        if (st.phase != GPhase.PLAYING) return
        if (st.stage == "BID" && rolledFor != st.round && st.myCount > 0) {
            myDice = List(st.myCount) { random.nextInt(1, 7) }
            rolledFor = st.round
            // show them straight away
            state = st.copy(myDice = myDice)
            onChange()
        }
        if (st.stage == "REVEAL" && sentFor != st.round && myDice.size == st.myCount) {
            sentFor = st.round
            act(JSONObject().put("a", "dice").put("d", JSONArray(myDice)))
        }
    }

    override fun reset() {
        state = LiarState(iAmHost = isHost)
        myDice = emptyList(); rolledFor = -1; sentFor = -1; rolledGame = -1
    }
}

// ====================================================================== the computer player

class LiarDiceBrain(private val e: LiarDiceEngine, skill: Skill, random: Random) : Brain(skill, random) {
    override fun think(now: Long) {
        val s = e.state
        if (s.phase != GPhase.PLAYING) return
        when (s.stage) {
            "SHOW" -> if (!s.myAck && ready(now, "liarack${s.round}", 1800, 2800)) e.ack()
            "BID" -> if (s.myTurn && s.myDice.size == s.myCount && ready(now, "liarbid${s.round}${s.bid?.qty}${s.bid?.face}", 1400, 3000)) decide(s)
        }
    }

    private fun decide(s: LiarState) {
        val wild = s.wild
        val unknown = s.theirCount
        fun prob(q: Int, f: Int) = LiarMath.atLeast(q - LiarMath.count(s.myDice, f, wild), unknown, LiarMath.chance(f, wild))
        val cur = s.bid
        val callBelow = when (skill) { Skill.EASY -> 0.12; Skill.NORMAL -> 0.28; Skill.HARD -> 0.36 }
        if (cur != null && prob(cur.qty, cur.face) < callBelow && !(skill == Skill.EASY && pct(25))) { e.callLiar(); return }
        // every legal raise, the safest first
        val total = s.total
        val options = ArrayList<Triple<Int, Int, Double>>()
        for (q in 1..total) for (f in 1..LiarMath.FACES) {
            if (cur != null && !LiarMath.higher(q, f, cur.qty, cur.face)) continue
            options += Triple(q, f, prob(q, f))
        }
        if (options.isEmpty()) { if (cur != null) e.callLiar(); return }
        // prefer the cheapest raise that is still likely to be true; sometimes a bluff
        val good = options.filter { it.third >= 0.5 }.sortedWith(compareBy({ it.first }, { it.second }))
        val pick = when {
            good.isNotEmpty() && !(skill != Skill.EASY && pct(12)) -> good[minOf(good.size - 1, random.nextInt(2))]
            else -> options.sortedByDescending { it.third }.take(3).let { it[random.nextInt(it.size)] }
        }
        // if even the best raise is hopeless, call instead
        if (cur != null && pick.third < 0.12 && skill != Skill.EASY) { e.callLiar(); return }
        e.bid(pick.first, pick.second)
    }
}
