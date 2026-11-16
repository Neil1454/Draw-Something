package com.neil1454.drawsomething.core

import org.json.JSONArray
import org.json.JSONObject
import kotlin.math.abs
import kotlin.random.Random

// ====================================================================================
// Deal or No Deal (the UK show), for two players.
//   • 22 sealed boxes hold the UK prize amounts, from 1p up to £250,000. You each choose ONE box to keep.
//   • The other twenty boxes are opened, a few at a time, with the two of you taking turns to choose which ones. Every box opened shows a
//     prize that is NOT in either of your boxes, which changes what the Banker thinks your boxes are worth.
//   • After each round the Banker rings with an offer for each of you. Each of you secretly chooses DEAL (take the money and leave the
//     game) or NO DEAL (play on); both answers are shown together. If you take the deal, your own box is opened to show what you gave up.
//   • At the end, if you are both still in, you may SWAP boxes. A swap only happens if you BOTH say yes.
//   • Whoever walks away with more money wins.
// The prizes in the boxes are never sent to the other phone until they are opened.
// ====================================================================================

object DndRules {
    /** The 22 UK prizes, in pence. */
    val PRIZES = longArrayOf(
        1, 10, 50, 100, 500, 1_000, 5_000, 10_000, 25_000, 50_000, 75_000, 100_000,
        300_000, 500_000, 1_000_000, 1_500_000, 2_000_000, 3_500_000, 5_000_000, 7_500_000, 10_000_000, 25_000_000,
    )
    const val BOXES = 22

    /** How many boxes are opened in each round (twenty in all: your two boxes are never opened). */
    val OPENS = intArrayOf(4, 4, 3, 3, 2, 2, 1, 1)
    val ROUNDS get() = OPENS.size

    /** How much of the fair value (the average of what is left) the Banker offers after each round. */
    private val FACTOR = doubleArrayOf(0.20, 0.28, 0.38, 0.50, 0.63, 0.76, 0.88, 0.97)

    /** The offer for a player: the Banker's fair value of what could be left, shaved by how early it is, with a little mood. */
    fun offer(remaining: List<Long>, round: Int, mood: Double): Long {
        if (remaining.isEmpty()) return 0L
        val ev = remaining.average()
        val raw = ev * FACTOR[(round - 1).coerceIn(0, FACTOR.lastIndex)] * (1.0 + mood)
        return nice(raw.toLong().coerceAtLeast(1L))
    }

    /** Rounded to the sort of number the Banker would say. */
    fun nice(pence: Long): Long {
        val step = when {
            pence < 1_000L -> 1L            // under £10: to the penny
            pence < 10_000L -> 100L         // under £100: to the pound
            pence < 100_000L -> 1_000L      // under £1,000: to £10
            pence < 1_000_000L -> 5_000L    // under £10,000: to £50
            pence < 5_000_000L -> 10_000L   // under £50,000: to £100
            else -> 50_000L                 // above: to £500
        }
        return maxOf(step, (pence + step / 2) / step * step).coerceAtLeast(1L)
    }

    fun money(pence: Long): String = when {
        pence < 100L -> "${pence}p"
        pence % 100L == 0L -> "£" + "%,d".format(pence / 100L)
        else -> "£" + "%,d.%02d".format(pence / 100L, pence % 100L)
    }
}

/** What the screens see of one box. [value] is -1 until it is known. */
class DndBox(val number: Int, val value: Long, /** 0 closed, 1 opened, 2 mine, 3 theirs */ val status: Int)

data class DndState(
    val phase: GPhase = GPhase.LOBBY,
    /** PICK (choose your own box), OPEN (open boxes), OFFER (the Banker rings), SWAP (offer to swap boxes) or REVEAL (the final boxes open). */
    val stage: String = "PICK",
    val round: Int = 1,
    val rounds: Int = DndRules.ROUNDS,
    val boxes: List<DndBox> = List(DndRules.BOXES) { DndBox(it + 1, -1, 0) },
    val myBox: Int = 0,
    val theirBox: Int = 0,
    /** Who opens the next box, and how many are still to open this round. */
    val myTurn: Boolean = false,
    val opensLeft: Int = 0,
    /** The Banker's offers, in pence, and whether each of us has answered (neither answer is shown until both are in). */
    val myOffer: Long = 0L,
    val theirOffer: Long = 0L,
    val myAnswered: Boolean = false,
    val theirAnswered: Boolean = false,
    /** Who has taken a deal so far, and for how much. */
    val myDealt: Boolean = false,
    val theirDealt: Boolean = false,
    val myDealAmount: Long = 0L,
    val theirDealAmount: Long = 0L,
    /** The swap: have I / they answered, and did it happen. */
    val swapSaid: Boolean = false,
    val theirSwapSaid: Boolean = false,
    val swapped: Boolean = false,
    /** The prizes in the two boxes, once known, and the final winnings. */
    val myBoxValue: Long = -1L,
    val theirBoxValue: Long = -1L,
    val myFinal: Long = 0L,
    val theirFinal: Long = 0L,
    /** The latest happening, so the screen can announce it: type (open, deal, nodeal, swap, keep), by whom (1 me, 2 them), box number, value. */
    val evType: String = "",
    val evBy: Int = 0,
    val evBox: Int = 0,
    val evValue: Long = -1L,
    val moves: Int = 0,
    val result: Int = 0,
    val iAmHost: Boolean = true,
) {
    val myScore get() = (myFinal / 100L).toInt()
    val theirScore get() = (theirFinal / 100L).toInt()

    /** The prizes nobody has seen yet (they could be in a closed box or in either held box). */
    val unseen: List<Long> get() {
        val seen = boxes.filter { it.value >= 0 }.map { it.value }.toMutableList()
        val all = DndRules.PRIZES.toMutableList()
        for (v in seen) all.remove(v)
        return all
    }
    val closedCount get() = boxes.count { it.status == 0 }
}

class DealNoDealEngine(
    myName: String,
    theirName: String,
    isHost: Boolean,
    send: (GMsg) -> Unit,
    random: Random = Random.Default,
    clock: () -> Long = { System.currentTimeMillis() },
) : SyncEngine(GameKind.DEALNODEAL, myName, theirName, isHost, send, random, clock) {

    var state = DndState(iAmHost = isHost)
        private set
    override val stateAny: Any get() = state
    override val phase get() = state.phase

    /** Choose box [n] (1-22) as my own to keep. */
    fun pick(n: Int) = act(JSONObject().put("a", "pick").put("b", n))
    /** Open box [n]. */
    fun open(n: Int) = act(JSONObject().put("a", "open").put("b", n))
    /** Answer the Banker: true is DEAL, false is NO DEAL. */
    fun answer(deal: Boolean) = act(JSONObject().put("a", "deal").put("d", if (deal) 1 else 0))
    /** At the end: do I want to swap boxes? (It only happens if we both do.) */
    fun swap(yes: Boolean) = act(JSONObject().put("a", "swap").put("s", if (yes) 1 else 0))

    // ---- the host's secret game
    private val value = LongArray(DndRules.BOXES + 1)       // value[box number]
    private val opened = BooleanArray(DndRules.BOXES + 1)
    private val held = IntArray(3)                          // the box each player keeps (0 = not yet)
    private val revealedHeld = BooleanArray(3)
    private var stage = "PICK"
    private var round = 1
    private var opensLeft = 0
    private var opener = 1
    private val offers = LongArray(3)
    private val answered = BooleanArray(3)
    private val dealWanted = BooleanArray(3)
    private val dealt = BooleanArray(3)
    private val dealAmount = LongArray(3)
    private val swapAnswered = BooleanArray(3)
    private val swapWanted = BooleanArray(3)
    private var swapped = false
    private val finalAmount = LongArray(3)
    private var evType = ""
    private var evBy = 0
    private var evBox = 0
    private var evValue = -1L
    private var moves = 0
    private var over = false
    private var result = 0

    override fun hostBegin(opts: JSONObject) {
        val shuffled = DndRules.PRIZES.toMutableList().also { it.shuffle(random) }
        for (i in 1..DndRules.BOXES) value[i] = shuffled[i - 1]
        opened.fill(false); held.fill(0); revealedHeld.fill(false)
        stage = "PICK"; round = 1; opensLeft = 0; opener = if (random.nextBoolean()) 1 else 2
        offers.fill(0L); answered.fill(false); dealWanted.fill(false); dealt.fill(false); dealAmount.fill(0L)
        swapAnswered.fill(false); swapWanted.fill(false); swapped = false; finalAmount.fill(0L)
        evType = ""; evBy = 0; evBox = 0; evValue = -1L; moves = 0; over = false; result = 0
    }

    private fun active(p: Int) = !dealt[p]

    /** Prizes not yet seen by anybody: not in an opened box, and not in a held box that has been shown. */
    private fun unseenValues(): List<Long> {
        val seen = ArrayList<Long>()
        for (b in 1..DndRules.BOXES) if (opened[b]) seen.add(value[b])
        for (p in 1..2) if (held[p] != 0 && revealedHeld[p]) seen.add(value[held[p]])
        val all = DndRules.PRIZES.toMutableList()
        for (v in seen) all.remove(v)
        return all
    }

    override fun hostHandle(fromHost: Boolean, a: JSONObject) {
        if (state.phase != GPhase.PLAYING || over) return
        val p = if (fromHost) 1 else 2
        when (a.getString("a")) {
            "pick" -> if (stage == "PICK" && held[p] == 0) {
                val b = a.optInt("b", 0)
                if (b !in 1..DndRules.BOXES || held[1] == b || held[2] == b) return
                held[p] = b
                event("pick", p, b, -1L)
                if (held[1] != 0 && held[2] != 0) { stage = "OPEN"; startRound() }
                publish()
            }
            "open" -> if (stage == "OPEN" && opener == p && active(p)) {
                val b = a.optInt("b", 0)
                if (b !in 1..DndRules.BOXES || opened[b] || held[1] == b || held[2] == b) return
                opened[b] = true
                event("open", p, b, value[b])
                opensLeft--
                if (opensLeft <= 0) endOfRound() else nextOpener(p)
                publish()
            }
            "deal" -> if (stage == "OFFER" && active(p) && !answered[p]) {
                answered[p] = true; dealWanted[p] = a.optInt("d", 0) == 1
                if ((1..2).all { !active(it) || answered[it] }) resolveOffers()
                publish()
            }
            "swap" -> if (stage == "SWAP" && active(p) && !swapAnswered[p]) {
                swapAnswered[p] = true; swapWanted[p] = a.optInt("s", 0) == 1
                if (swapAnswered[1] && swapAnswered[2]) {
                    swapped = swapWanted[1] && swapWanted[2]
                    if (swapped) { val t = held[1]; held[1] = held[2]; held[2] = t }
                    event(if (swapped) "swap" else "keep", 0, 0, -1L)
                    toReveal()
                }
                publish()
            }
        }
    }

    private fun event(type: String, by: Int, box: Int, v: Long) { evType = type; evBy = by; evBox = box; evValue = v; moves++ }

    private fun startRound() {
        opensLeft = DndRules.OPENS[round - 1]
        // the players take turns opening; whoever did not start the last round starts this one
        opener = if (round % 2 == 1) 1 else 2
        if (!active(opener)) opener = 3 - opener
    }

    private fun nextOpener(p: Int) { opener = if (active(3 - p)) 3 - p else p }

    private fun endOfRound() {
        // the Banker rings: an offer for each player still in the game
        val pool = unseenValues()
        for (p in 1..2) if (active(p)) {
            val mood = (random.nextDouble() * 2.0 - 1.0) * 0.08
            offers[p] = DndRules.offer(pool, round, mood)
            answered[p] = false; dealWanted[p] = false
        }
        stage = "OFFER"
    }

    private fun resolveOffers() {
        for (p in 1..2) if (active(p) && dealWanted[p]) {
            dealt[p] = true; dealAmount[p] = offers[p]
            revealedHeld[p] = true
            event("deal", p, held[p], value[held[p]])
        }
        for (p in 1..2) if (!dealt[p] && answered[p]) event("nodeal", p, 0, -1L)
        offers.fill(0L)
        when {
            !active(1) && !active(2) -> toReveal()
            round >= DndRules.ROUNDS -> { if (active(1) && active(2)) { stage = "SWAP" } else toReveal() }
            else -> { round++; stage = "OPEN"; startRound() }
        }
    }

    private fun toReveal() {
        stage = "REVEAL"
        for (p in 1..2) {
            if (dealt[p]) finalAmount[p] = dealAmount[p] else { finalAmount[p] = value[held[p]]; revealedHeld[p] = true }
        }
        over = true
        result = if (finalAmount[1] > finalAmount[2]) 1 else if (finalAmount[2] > finalAmount[1]) 2 else 3
    }

    override fun hostSnapshot(): JSONObject {
        // a prize is only sent once it has been opened (or shown)
        val vals = JSONArray()
        for (b in 1..DndRules.BOXES) {
            val shown = opened[b] || (1..2).any { held[it] == b && revealedHeld[it] }
            vals.put(if (shown) value[b] else -1L)
        }
        val inGame = (1..2).filter { active(it) }
        val resolved = stage != "OFFER"
        return JSONObject()
            .put("st", stage).put("rd", round).put("vl", vals).put("hb", JSONArray().put(held[1]).put(held[2])).put("ol", opensLeft).put("op", opener)
            .put("of", JSONArray().put(if (stage == "OFFER" && active(1)) offers[1] else 0L).put(if (stage == "OFFER" && active(2)) offers[2] else 0L))
            .put("an", JSONArray().put(answered[1] && stage == "OFFER").put(answered[2] && stage == "OFFER"))
            .put("dl", JSONArray().put(dealt[1]).put(dealt[2])).put("da", JSONArray().put(dealAmount[1]).put(dealAmount[2]))
            .put("sa", JSONArray().put(swapAnswered[1] && stage == "SWAP").put(swapAnswered[2] && stage == "SWAP")).put("sw", swapped)
            .put("fa", JSONArray().put(if (over) finalAmount[1] else 0L).put(if (over) finalAmount[2] else 0L))
            .put("ev", JSONArray().put(evType).put(evBy).put(evBox).put(evValue)).put("mv", moves)
            .put("ov", over).put("res", result).put("ig", inGame.size).put("rs", resolved)
    }

    override fun adopt(s: JSONObject) {
        val vl = s.optJSONArray("vl") ?: return
        val hb = s.optJSONArray("hb") ?: return
        if (vl.length() != DndRules.BOXES || hb.length() != 2) return
        val me = if (isHost) 1 else 2
        fun pair(key: String): JSONArray? = s.optJSONArray(key)?.takeIf { it.length() >= 2 }
        fun mineIdx() = if (isHost) 0 else 1
        fun theirIdx() = if (isHost) 1 else 0
        val myBox = hb.optInt(mineIdx(), 0).coerceIn(0, DndRules.BOXES)
        val theirBox = hb.optInt(theirIdx(), 0).coerceIn(0, DndRules.BOXES)
        val boxes = List(DndRules.BOXES) { i ->
            val n = i + 1
            val v = vl.optLong(i, -1L).let { if (it in DndRules.PRIZES.toList()) it else -1L }
            DndBox(n, v, when { n == myBox && myBox != 0 -> 2; n == theirBox && theirBox != 0 -> 3; v >= 0L -> 1; else -> 0 })
        }
        // an opened box is shown opened even if it is a held box that has been revealed
        val of = pair("of"); val an = pair("an"); val dl = pair("dl"); val da = pair("da"); val sa = pair("sa"); val fa = pair("fa")
        val ev = s.optJSONArray("ev")
        val evBy = ev?.optInt(1, 0)?.coerceIn(0, 2) ?: 0
        val ov = s.optBoolean("ov", false)
        val res = s.optInt("res", 0).coerceIn(0, 3)
        val stage = s.optString("st", "PICK").take(6)
        fun money(a: JSONArray?, i: Int) = (a?.optLong(i, 0L) ?: 0L).coerceIn(0L, 25_000_000L)
        state = DndState(
            phase = if (ov) GPhase.OVER else GPhase.PLAYING, stage = stage, round = s.optInt("rd", 1).coerceIn(1, DndRules.ROUNDS), boxes = boxes,
            myBox = myBox, theirBox = theirBox,
            myTurn = stage == "OPEN" && s.optInt("op", 1) == me && dl?.optBoolean(mineIdx(), false) != true,
            opensLeft = s.optInt("ol", 0).coerceIn(0, 10),
            myOffer = money(of, mineIdx()), theirOffer = money(of, theirIdx()),
            myAnswered = an?.optBoolean(mineIdx(), false) ?: false, theirAnswered = an?.optBoolean(theirIdx(), false) ?: false,
            myDealt = dl?.optBoolean(mineIdx(), false) ?: false, theirDealt = dl?.optBoolean(theirIdx(), false) ?: false,
            myDealAmount = money(da, mineIdx()), theirDealAmount = money(da, theirIdx()),
            swapSaid = sa?.optBoolean(mineIdx(), false) ?: false, theirSwapSaid = sa?.optBoolean(theirIdx(), false) ?: false, swapped = s.optBoolean("sw", false),
            myBoxValue = if (myBox != 0) boxes[myBox - 1].value else -1L, theirBoxValue = if (theirBox != 0) boxes[theirBox - 1].value else -1L,
            myFinal = money(fa, mineIdx()), theirFinal = money(fa, theirIdx()),
            evType = ev?.optString(0, "")?.take(8) ?: "", evBy = if (evBy == 0) 0 else if (evBy == me) 1 else 2,
            evBox = ev?.optInt(2, 0)?.coerceIn(0, DndRules.BOXES) ?: 0, evValue = ev?.optLong(3, -1L) ?: -1L,
            moves = s.optInt("mv", 0).coerceIn(0, 1000),
            result = if (!ov) 0 else if (res == 3) 3 else if (res == me) 1 else 2, iAmHost = isHost,
        )
    }

    override fun reset() {
        state = DndState(iAmHost = isHost)
    }
}

// ====================================================================== the computer player

class DealNoDealBrain(private val e: DealNoDealEngine, skill: Skill, random: Random) : Brain(skill, random) {
    override fun think(now: Long) {
        val s = e.state
        if (s.phase != GPhase.PLAYING) return
        when (s.stage) {
            "PICK" -> if (s.myBox == 0 && ready(now, "dp", 900, 2200)) {
                val free = (1..DndRules.BOXES).filter { it != s.theirBox }
                e.pick(free[random.nextInt(free.size)])
            }
            "OPEN" -> if (s.myTurn && ready(now, "do${s.moves}", 800, 1900)) {
                val closed = s.boxes.filter { it.status == 0 }
                if (closed.isNotEmpty()) e.open(closed[random.nextInt(closed.size)].number)
            }
            "OFFER" -> if (!s.myDealt && !s.myAnswered && ready(now, "dd${s.round}", 1400, 3200)) {
                val unseen = s.unseen
                val ev = if (unseen.isEmpty()) 0.0 else unseen.average()
                // how greedy to be: the careful player takes a good offer, the bold one plays on while the big prizes are still out there
                val threshold = when (skill) { Skill.EASY -> 0.55; Skill.NORMAL -> 0.85; Skill.HARD -> 0.97 }
                var deal = s.myOffer >= ev * threshold
                // beating a rival who has already dealt is the aim: only chance it if the offer is behind theirs
                if (s.theirDealt && s.myOffer > s.theirDealAmount && skill != Skill.EASY) deal = true
                if (skill == Skill.EASY && random.nextInt(100) < 15) deal = !deal
                e.answer(deal)
            }
            "SWAP" -> if (!s.swapSaid && ready(now, "ds", 1200, 2600)) e.swap(random.nextInt(100) < 40)
        }
    }
}
