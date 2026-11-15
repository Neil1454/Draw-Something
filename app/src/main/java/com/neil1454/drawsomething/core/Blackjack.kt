package com.neil1454.drawsomething.core

import org.json.JSONArray
import org.json.JSONObject
import kotlin.random.Random

// ====================================================================================
// Blackjack for two. You both play against the same dealer, each with your own chips and your own hand, at the same time.
// Beat the dealer without going over 21. Blackjack pays 3 to 2, you can double down on your first two cards, and the dealer
// stands on every 17. After the chosen number of rounds the player with the most chips wins.
// The host's phone runs the dealer and the shoe, so the dealer's face-down card is never sent until it is turned over.
// An optional coach gives basic-strategy tips: the host chooses how many tips each player gets (or unlimited).
// ====================================================================================

object BjMath {
    private val RANKS = listOf("A", "2", "3", "4", "5", "6", "7", "8", "9", "10", "J", "Q", "K")
    private val SUITS = listOf("♠", "♥", "♦", "♣")

    fun rank(c: Int) = c % 13
    fun suit(c: Int) = c / 13
    fun isRed(c: Int) = suit(c) == 1 || suit(c) == 2
    fun name(c: Int) = RANKS[rank(c)] + SUITS[suit(c)]
    fun rankName(c: Int) = RANKS[rank(c)]

    /** Face value: ace counts 11 here (the total works out the soft/hard part). */
    fun value(c: Int): Int = when (val r = rank(c)) { 0 -> 11; in 9..12 -> 10; else -> r + 1 }

    /** The best total not over 21, and whether an ace is still counting as 11 (a "soft" hand). */
    fun total(cards: List<Int>): Pair<Int, Boolean> {
        var sum = cards.sumOf { value(it) }
        var aces = cards.count { rank(it) == 0 }
        while (sum > 21 && aces > 0) { sum -= 10; aces-- }
        return sum to (aces > 0)
    }

    fun isBlackjack(cards: List<Int>) = cards.size == 2 && total(cards).first == 21
    fun isBust(cards: List<Int>) = total(cards).first > 21

    /** Chance the next card busts this hand (an endless deck: a 10-value card is 4 in 13). */
    fun bustChance(cards: List<Int>): Int {
        val (t, soft) = total(cards)
        if (t >= 21) return if (soft) 0 else 100
        if (soft) return 0
        // a card of value v busts if t + v > 21
        var bust = 0
        for (r in 0..12) { val v = if (r == 0) 1 else if (r >= 9) 10 else r + 1; if (t + v > 21) bust++ }
        return Math.round(bust * 100f / 13f)
    }

    fun newShoe(random: Random, decks: Int = 4): ArrayList<Int> {
        val l = ArrayList<Int>(52 * decks)
        repeat(decks) { for (c in 0 until 52) l.add(c) }
        l.shuffle(random)
        return l
    }
}

data class BjAdvice(val action: String, val reason: String)

/** Basic strategy for the rules used here (dealer stands on all 17s, no splitting), with a short reason a beginner can learn from. */
object BjStrategy {
    fun advise(hand: List<Int>, dealerUp: Int, canDouble: Boolean): BjAdvice {
        val (t, soft) = BjMath.total(hand)
        val u = BjMath.value(dealerUp)
        val upName = BjMath.rankName(dealerUp)
        val weak = u in 4..6 // the dealer is likely to bust
        fun dbl(reason: String, otherwise: String, why2: String) =
            if (canDouble) BjAdvice("double", reason) else BjAdvice(otherwise, why2)
        if (t >= 21) return BjAdvice("stand", "You have 21: you can't do better.")
        if (!soft) {
            return when {
                t <= 8 -> BjAdvice("hit", "$t is too low to stand on. You can't bust, so take a card.")
                t == 9 -> if (u in 3..6) dbl("The dealer shows $upName, a weak card, and 9 is a good number to double on.", "hit", "Take a card: 9 is low.") else BjAdvice("hit", "9 is low, but the dealer's $upName is too strong to double against. Just hit.")
                t == 10 -> if (u in 2..9) dbl("10 is a great hand to double: any 10-value card makes 20, and the dealer only shows $upName.", "hit", "Take a card: any 10-value card makes 20.") else BjAdvice("hit", "The dealer shows $upName, so don't risk a double. Just hit.")
                t == 11 -> if (u <= 10) dbl("11 is the best hand to double: any 10-value card makes 21.", "hit", "Take a card: any 10-value card makes 21.") else BjAdvice("hit", "11 against an ace: take a card, but doubling is too risky.")
                t == 12 -> if (u in 4..6) BjAdvice("stand", "The dealer shows $upName and will probably bust, so stand on 12 and let them.") else BjAdvice("hit", "12 is weak against a $upName. The dealer isn't likely to bust, so take a card.")
                t in 13..16 -> if (u in 2..6) BjAdvice("stand", "The dealer shows $upName, a bust card. Stand on $t and let the dealer take the risk.") else BjAdvice("hit", "The dealer shows $upName and probably has 17 or more. $t will lose if you stand, so you have to take a card.")
                else -> BjAdvice("stand", "$t is a strong hand. Stand.")
            }
        }
        // soft hands (an ace counting as 11 can't bust on one more card)
        return when {
            t <= 14 -> if (u in 5..6) dbl("A soft $t is safe to double against the dealer's $upName: you can't bust.", "hit", "A soft $t can't bust on one card, so take one.") else BjAdvice("hit", "Soft $t can't bust on one card, so it's free to take another.")
            t <= 16 -> if (u in 4..6) dbl("A soft $t against the weak $upName is a good double: the ace keeps you safe.", "hit", "A soft $t can't bust on one card, so take one.") else BjAdvice("hit", "Soft $t is weak. The ace protects you from busting, so hit.")
            t == 17 -> if (u in 3..6) dbl("Soft 17 against a $upName is a good double.", "hit", "Soft 17 is weak, so hit.") else BjAdvice("hit", "Soft 17 is weak: the ace means you can't bust, so hit.")
            t == 18 -> when {
                u in 3..6 -> dbl("Soft 18 against a $upName is a good double.", "stand", "Stand on soft 18.")
                u in 2..8 -> BjAdvice("stand", "Soft 18 beats a dealer $upName more often than not. Stand.")
                else -> BjAdvice("hit", "The dealer's $upName is strong. Soft 18 is likely to lose, and you can't bust, so hit.")
            }
            else -> BjAdvice("stand", "Soft $t is strong. Stand.")
        }
    }
}

class BjHandView(val cards: List<Int> = emptyList(), val bet: Int = 0, val done: Boolean = false, val doubled: Boolean = false, val outcome: String = "")

data class BlackjackState(
    val phase: GPhase = GPhase.LOBBY,
    /** BET, PLAY, DEALER, RESULT or END. */
    val stage: String = "BET",
    val round: Int = 0,
    val rounds: Int = 10,
    val myChips: Int = BlackjackEngine.START_CHIPS,
    val theirChips: Int = BlackjackEngine.START_CHIPS,
    val mine: BjHandView = BjHandView(),
    val theirs: BjHandView = BjHandView(),
    val myLocked: Boolean = false,
    val theirLocked: Boolean = false,
    /** The dealer's cards; -1 is the card that is still face down. */
    val dealer: List<Int> = emptyList(),
    /** Coach: how many tips each player may use (-1 unlimited, 0 coach off), what I have left, and the tip I am looking at. */
    val tipAllowance: Int = 0,
    val tipsLeft: Int = 0,
    val tip: BjAdvice? = null,
    val log: List<String> = emptyList(),
    val seq: Int = 0,
    /** 0 playing, 1 I won, 2 they won, 3 draw. */
    val result: Int = 0,
    val why: String = "",
    val iAmHost: Boolean = true,
) {
    val coach get() = tipAllowance != 0
    val myScore get() = myChips
    val theirScore get() = theirChips
    val canAct get() = phase == GPhase.PLAYING && stage == "PLAY" && !mine.done
    val canDouble get() = canAct && mine.cards.size == 2 && !mine.doubled && myChips >= mine.bet
    val dealerUp get() = dealer.firstOrNull() ?: -1
}

class BlackjackEngine(
    myName: String,
    theirName: String,
    isHost: Boolean,
    send: (GMsg) -> Unit,
    random: Random = Random.Default,
    clock: () -> Long = { System.currentTimeMillis() },
) : SyncEngine(GameKind.BLACKJACK, myName, theirName, isHost, send, random, clock) {

    companion object {
        const val START_CHIPS = 500
        const val MIN_BET = 10
        val ROUND_CHOICES = listOf(5, 10, 15)
        val TIP_CHOICES = listOf(0, 3, 5, -1)
    }

    var state = BlackjackState(iAmHost = isHost)
        private set
    override val stateAny: Any get() = state
    override val phase get() = state.phase

    // ---- sending my choices
    fun bet(amount: Int) = act(JSONObject().put("a", "bet").put("v", amount))
    fun deal() = act(JSONObject().put("a", "lock"))
    fun hit() = act(JSONObject().put("a", "hit"))
    fun stand() = act(JSONObject().put("a", "stand"))
    fun double() = act(JSONObject().put("a", "double"))
    fun tip() = act(JSONObject().put("a", "tip"))

    // ---- the host's private game
    private val hands = Array(3) { ArrayList<Int>() }
    private val bets = IntArray(3)
    private val chips = IntArray(3)
    private val locked = BooleanArray(3)
    private val done = BooleanArray(3)
    private val doubled = BooleanArray(3)
    private val outcome = Array(3) { "" }
    private val tipsLeft = IntArray(3)
    private val tipAdvice = arrayOfNulls<BjAdvice>(3)
    private val dealerCards = ArrayList<Int>()
    private var holeHidden = true
    private var shoe = ArrayList<Int>()
    private var stage = "BET"
    private var nextAt = 0L
    private var round = 0
    private var rounds = 10
    private var allowance = 0
    private var seq = 0
    private val log = ArrayList<String>()
    private var over = false
    private var why = ""

    private fun say(line: String) { log.add(line); while (log.size > 8) log.removeAt(0); seq++ }
    private fun nameOf(p: Int) = if (p == 1) (if (isHost) myName else theirName) else (if (isHost) theirName else myName)

    private fun draw(): Int {
        if (shoe.size < 20) shoe = BjMath.newShoe(random)
        return shoe.removeAt(shoe.size - 1)
    }

    override fun hostBegin(opts: JSONObject) {
        rounds = ROUND_CHOICES.getOrElse(opts.optInt("rounds", 1)) { 10 }
        allowance = TIP_CHOICES.getOrElse(opts.optInt("tips", 0)) { 0 }
        chips[1] = START_CHIPS; chips[2] = START_CHIPS
        tipsLeft[1] = if (allowance > 0) allowance else 0; tipsLeft[2] = tipsLeft[1]
        shoe = BjMath.newShoe(random)
        round = 0; over = false; why = ""; log.clear(); seq = 0
        beginRound()
    }

    private fun beginRound() {
        for (p in 1..2) { hands[p].clear(); bets[p] = 0; locked[p] = false; done[p] = false; doubled[p] = false; outcome[p] = ""; tipAdvice[p] = null }
        dealerCards.clear(); holeHidden = true
        if (chips[1] < MIN_BET || chips[2] < MIN_BET) {
            finish("broke"); return
        }
        bets[1] = minOf(25, chips[1]); bets[2] = minOf(25, chips[2])
        stage = "BET"
        say("Round ${round + 1} of $rounds: place your bets.")
    }

    override fun hostHandle(fromHost: Boolean, a: JSONObject) {
        if (state.phase != GPhase.PLAYING || over) return
        val p = if (fromHost) 1 else 2
        when (a.getString("a")) {
            "bet" -> if (stage == "BET" && !locked[p]) {
                val v = a.optInt("v", 0).coerceIn(0, chips[p])
                bets[p] = v - v % 5
                publish()
            }
            "lock" -> if (stage == "BET" && !locked[p] && bets[p] in MIN_BET..chips[p]) {
                locked[p] = true
                say("${nameOf(p)} bets £${bets[p]}.")
                if (locked[1] && locked[2]) dealCards() else publish()
            }
            "hit" -> if (stage == "PLAY" && !done[p]) {
                val c = draw(); hands[p].add(c); tipAdvice[p] = null
                val (t, _) = BjMath.total(hands[p])
                say("${nameOf(p)} hits: ${BjMath.name(c)} (${describe(hands[p])}).")
                if (t >= 21) done[p] = true
                afterAction()
            }
            "stand" -> if (stage == "PLAY" && !done[p]) {
                done[p] = true; tipAdvice[p] = null
                say("${nameOf(p)} stands on ${BjMath.total(hands[p]).first}.")
                afterAction()
            }
            "double" -> if (stage == "PLAY" && !done[p] && hands[p].size == 2 && !doubled[p] && chips[p] >= bets[p]) {
                chips[p] -= bets[p]; bets[p] *= 2; doubled[p] = true; tipAdvice[p] = null
                val c = draw(); hands[p].add(c); done[p] = true
                say("${nameOf(p)} doubles down and gets ${BjMath.name(c)} (${describe(hands[p])}).")
                afterAction()
            }
            "tip" -> if (stage == "PLAY" && !done[p] && allowance != 0 && dealerCards.isNotEmpty()) {
                if (allowance > 0 && tipsLeft[p] <= 0) return
                if (allowance > 0) tipsLeft[p]--
                tipAdvice[p] = BjStrategy.advise(hands[p], dealerCards[0], hands[p].size == 2 && chips[p] >= bets[p])
                publish()
            }
        }
    }

    private fun describe(h: List<Int>): String {
        val (t, soft) = BjMath.total(h)
        return if (t > 21) "bust with $t" else if (soft) "soft $t" else "$t"
    }

    private fun dealCards() {
        for (p in 1..2) { chips[p] -= bets[p] }
        for (k in 0 until 2) { for (p in 1..2) hands[p].add(draw()); dealerCards.add(draw()) }
        stage = "PLAY"
        say("Cards are dealt. The dealer shows ${BjMath.name(dealerCards[0])}.")
        for (p in 1..2) if (BjMath.isBlackjack(hands[p])) { done[p] = true; say("${nameOf(p)} has BLACKJACK!") }
        // the dealer peeks when showing an ace or a ten
        if (BjMath.value(dealerCards[0]) >= 10 && BjMath.isBlackjack(dealerCards)) {
            say("The dealer turns over ${BjMath.name(dealerCards[1])}: blackjack!")
            toDealer(peek = true)
            return
        }
        afterAction()
    }

    private fun afterAction() {
        if (stage == "PLAY" && done[1] && done[2]) toDealer(peek = false) else publish()
    }

    private fun toDealer(peek: Boolean) {
        stage = "DEALER"; holeHidden = false
        nextAt = clock() + 1100L
        if (!peek) say("The dealer turns over ${BjMath.name(dealerCards[1])}.")
        publish()
    }

    override fun hostTick(now: Long) {
        if (over) return
        when (stage) {
            "DEALER" -> if (now >= nextAt) {
                val live = (1..2).any { !BjMath.isBust(hands[it]) && !BjMath.isBlackjack(hands[it]) }
                if (live && BjMath.total(dealerCards).first < 17) {
                    val c = draw(); dealerCards.add(c)
                    say("The dealer draws ${BjMath.name(c)} (${describe(dealerCards)}).")
                    nextAt = now + 950L
                    publish()
                } else settle(now)
            }
            "RESULT" -> if (now >= nextAt) {
                round++
                if (round >= rounds) finish("rounds") else { beginRound(); publish() }
            }
        }
    }

    private fun settle(now: Long) {
        val dt = BjMath.total(dealerCards).first
        val dealerBj = BjMath.isBlackjack(dealerCards)
        val dealerBust = dt > 21
        for (p in 1..2) {
            val h = hands[p]
            val t = BjMath.total(h).first
            val bj = BjMath.isBlackjack(h)
            val bet = bets[p]
            outcome[p] = when {
                t > 21 -> "bust"
                bj && dealerBj -> "push"
                bj -> "bj"
                dealerBj -> "lose"
                dealerBust -> "win"
                t > dt -> "win"
                t == dt -> "push"
                else -> "lose"
            }
            when (outcome[p]) {
                "bj" -> chips[p] += bet * 5 / 2
                "win" -> chips[p] += bet * 2
                "push" -> chips[p] += bet
            }
            say(when (outcome[p]) {
                "bj" -> "${nameOf(p)} wins £${bet * 3 / 2} with blackjack!"
                "win" -> "${nameOf(p)} beats the dealer and wins £$bet."
                "push" -> "${nameOf(p)} ties with the dealer: bet back."
                "bust" -> "${nameOf(p)} busted and loses £$bet."
                else -> "${nameOf(p)} loses £$bet."
            })
        }
        stage = "RESULT"
        nextAt = now + 4200L
        publish()
    }

    private fun finish(reason: String) {
        over = true; why = reason; stage = "END"
        say(if (reason == "broke") "Somebody is out of chips: that's the game." else "That was the last round.")
        publish()
    }

    // ------------------------------------------------------------------ the picture sent to the other phone

    override fun hostSnapshot(): JSONObject {
        val dealerJson = JSONArray()
        for ((i, c) in dealerCards.withIndex()) dealerJson.put(if (i == 1 && holeHidden) -1 else c)
        fun arr(l: List<Int>) = JSONArray().also { a -> l.forEach { a.put(it) } }
        val tipJ = JSONArray()
        for (p in 1..2) tipJ.put(tipAdvice[p]?.let { JSONArray().put(it.action).put(it.reason) } ?: JSONArray())
        return JSONObject()
            .put("st", stage).put("rd", round).put("R", rounds).put("ch", arr(listOf(chips[1], chips[2])))
            .put("bt", arr(listOf(bets[1], bets[2]))).put("lk", arr(listOf(if (locked[1]) 1 else 0, if (locked[2]) 1 else 0)))
            .put("h1", arr(hands[1])).put("h2", arr(hands[2])).put("dl", dealerJson)
            .put("dn", arr(listOf(if (done[1]) 1 else 0, if (done[2]) 1 else 0))).put("db", arr(listOf(if (doubled[1]) 1 else 0, if (doubled[2]) 1 else 0)))
            .put("oc", JSONArray().put(outcome[1]).put(outcome[2]))
            .put("tp", allowance).put("tl", arr(listOf(tipsLeft[1], tipsLeft[2]))).put("tt", tipJ)
            .put("lg", JSONArray(log)).put("sq", seq).put("ov", over).put("why", why)
    }

    private fun cards(a: JSONArray?, allowHidden: Boolean = false): List<Int> {
        if (a == null) return emptyList()
        val out = ArrayList<Int>()
        for (i in 0 until minOf(a.length(), 12)) {
            val c = a.optInt(i, -2)
            if (c in 0..51 || (allowHidden && c == -1)) out.add(c)
        }
        return out
    }

    override fun adopt(s: JSONObject) {
        val me = if (isHost) 0 else 1
        val them = 1 - me
        val ch = s.optJSONArray("ch")
        val bt = s.optJSONArray("bt")
        val lk = s.optJSONArray("lk")
        val dn = s.optJSONArray("dn")
        val db = s.optJSONArray("db")
        val oc = s.optJSONArray("oc")
        val tl = s.optJSONArray("tl")
        val tt = s.optJSONArray("tt")
        fun hand(i: Int) = BjHandView(
            cards = cards(s.optJSONArray(if (i == 0) "h1" else "h2")),
            bet = (bt?.optInt(i, 0) ?: 0).coerceIn(0, 100_000),
            done = (dn?.optInt(i, 0) ?: 0) == 1, doubled = (db?.optInt(i, 0) ?: 0) == 1,
            outcome = (oc?.optString(i, "") ?: "").take(5),
        )
        val ov = s.optBoolean("ov", false)
        val myC = (ch?.optInt(me, 0) ?: 0).coerceIn(0, 1_000_000)
        val theirC = (ch?.optInt(them, 0) ?: 0).coerceIn(0, 1_000_000)
        val tipArr = tt?.optJSONArray(me)
        val tip = if (tipArr != null && tipArr.length() >= 2) BjAdvice(tipArr.optString(0, "").take(8), tipArr.optString(1, "").take(240)) else null
        val logArr = s.optJSONArray("lg")
        state = BlackjackState(
            phase = if (ov) GPhase.OVER else GPhase.PLAYING,
            stage = s.optString("st", "BET").take(6), round = s.optInt("rd", 0).coerceIn(0, 100), rounds = s.optInt("R", 10).coerceIn(1, 100),
            myChips = myC, theirChips = theirC, mine = hand(me), theirs = hand(them),
            myLocked = (lk?.optInt(me, 0) ?: 0) == 1, theirLocked = (lk?.optInt(them, 0) ?: 0) == 1,
            dealer = cards(s.optJSONArray("dl"), allowHidden = true),
            tipAllowance = s.optInt("tp", 0).coerceIn(-1, 50), tipsLeft = (tl?.optInt(me, 0) ?: 0).coerceIn(0, 50), tip = tip,
            log = if (logArr == null) emptyList() else List(minOf(logArr.length(), 8)) { logArr.optString(it, "").take(120) },
            seq = s.optInt("sq", 0).coerceIn(0, 1_000_000),
            result = if (!ov) 0 else if (myC > theirC) 1 else if (myC < theirC) 2 else 3,
            why = s.optString("why", "").take(8), iAmHost = isHost,
        )
    }

    override fun reset() {
        state = BlackjackState(iAmHost = isHost)
    }
}

// ====================================================================== the computer player

class BlackjackBrain(private val e: BlackjackEngine, skill: Skill, random: Random) : Brain(skill, random) {
    override fun think(now: Long) {
        val s = e.state
        if (s.phase != GPhase.PLAYING) return
        when (s.stage) {
            "BET" -> if (!s.myLocked && ready(now, "bet${s.round}", 700, 1800)) {
                val unit = when (skill) { Skill.EASY -> listOf(25, 50, 100); Skill.NORMAL -> listOf(25, 50); Skill.HARD -> listOf(25, 50, 75) }
                val want = unit[random.nextInt(unit.size)].coerceAtMost(s.myChips)
                e.bet(want - want % 5)
                e.deal()
            }
            "PLAY" -> if (!s.mine.done && s.dealer.isNotEmpty() && ready(now, "act${s.round}${s.mine.cards.size}", 900, 2200)) {
                val adv = BjStrategy.advise(s.mine.cards, s.dealer[0], s.canDouble)
                var action = adv.action
                // an easy player sometimes gets it wrong
                if (skill == Skill.EASY && pct(25)) action = if (action == "stand") "hit" else "stand"
                else if (skill == Skill.NORMAL && pct(8)) action = if (action == "stand") "hit" else "stand"
                when (action) {
                    "double" -> if (s.canDouble) e.double() else e.hit()
                    "hit" -> e.hit()
                    else -> e.stand()
                }
            }
        }
    }
}
