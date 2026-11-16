package com.neil1454.drawsomething.core

import org.json.JSONArray
import org.json.JSONObject
import kotlin.random.Random

// ====================================================================================
// Stakes Showdown. Every stake you each typed in is up for grabs, one at a time, as its own contest.
//
//   • The next stake is turned over. The player who did NOT put it up is the challenger and picks the contest from four
//     options (each one says in plain words what you need to do to win).
//   • The contest is played as a real game from the app: Connect 4, Pairs, Pig, Blackjack, Minesweeper... Win it and you
//     claim that stake. A tie rolls the stake over: the next stake joins the pot and the winner takes both.
//   • Mystery stakes stay face-down until their round comes up, then flip over.
//   • When every stake is settled, the most stakes claimed wins (money amounts break a tie).
//
// The host's phone runs the showdown and starts each contest; the app tells it who won each one.
// ====================================================================================

object ShowdownRules {
    class Contest(val kind: GameKind, val goal: String, val time: String)

    val CONTESTS = listOf(
        Contest(GameKind.DROP, "Connect four discs in a row before your rival does.", "about 3 min"),
        Contest(GameKind.MEMORY, "Find more matching pairs than your rival.", "about 3 min"),
        Contest(GameKind.QUICK, "Win more reflex duels: tap first, but never on a fake-out.", "about 2 min"),
        Contest(GameKind.DICE, "Be first to bank the target score without busting.", "about 4 min"),
        Contest(GameKind.FLIP, "Own more discs than your rival when the board is full.", "about 5 min"),
        Contest(GameKind.HILO, "Climb higher up the prize ladder than your rival.", "about 3 min"),
        Contest(GameKind.BLACKJACK, "Finish five rounds with more chips than your rival.", "about 5 min"),
        Contest(GameKind.KALAH, "End with more seeds in your store than your rival.", "about 4 min"),
        Contest(GameKind.LIARSDICE, "Bluff your rival out of all their dice before they do it to you.", "about 4 min"),
        Contest(GameKind.HOCKEY, "Win at air hockey: first to five goals.", "about 3 min"),
        Contest(GameKind.HANGMAN, "Score more points guessing words in hangman.", "about 4 min"),
        Contest(GameKind.NOUGHTS, "Win the noughts and crosses: three in a row before your rival.", "about 2 min"),
        Contest(GameKind.GUESSWHO, "Work out your rival's secret face before they work out yours.", "about 5 min"),
        Contest(GameKind.MINES, "Score more by clearing squares and dodging the bombs.", "about 4 min"),
        Contest(GameKind.SEA, "Sink the whole enemy fleet before they sink yours.", "about 5 min"),
        Contest(GameKind.INVADERS, "Shoot down more invaders than your partner (highest score).", "about 4 min"),
        Contest(GameKind.CENTIPEDE, "Blast more of the centipede than your partner (highest score).", "about 4 min"),
        Contest(GameKind.ARKANOID, "Smash more bricks than your partner (highest score).", "about 4 min"),
    )

    fun contest(kind: GameKind): Contest? = CONTESTS.firstOrNull { it.kind == kind }

    /** Quick settings for each contest, so a showdown keeps moving. (Minesweeper and Battleships are started by the app itself.) */
    fun options(kind: GameKind): JSONObject = when (kind) {
        GameKind.DROP -> JSONObject().put("size", 0).put("power", false).put("theme", 0)
        GameKind.MEMORY -> JSONObject().put("size", 0).put("theme", 0)
        GameKind.QUICK -> JSONObject().put("match", 0).put("fake", true)
        GameKind.DICE -> JSONObject().put("target", 0).put("two", false)
        GameKind.FLIP -> JSONObject().put("size", 1)
        GameKind.HILO -> JSONObject().put("rows", 0).put("odds", true)
        GameKind.BLACKJACK -> JSONObject().put("rounds", 0).put("tips", 0)
        GameKind.GUESSWHO -> JSONObject().put("deck", 0).put("size", 0).put("auto", true)
        GameKind.KALAH -> JSONObject().put("seeds", 0)
        GameKind.LIARSDICE -> JSONObject().put("dice", 0).put("wild", false)
        GameKind.HOCKEY -> JSONObject().put("mode", 0).put("target", 0)
        GameKind.HANGMAN -> JSONObject().put("mode", 1).put("rounds", 0)
        GameKind.NOUGHTS -> JSONObject().put("mode", 0).put("series", 0)
        GameKind.INVADERS, GameKind.CENTIPEDE -> JSONObject().put("waves", 3).put("lives", 3)
        GameKind.ARKANOID -> JSONObject().put("waves", 2).put("lives", 3)
        else -> JSONObject()
    }

    const val OPTIONS_PER_ROUND = 4
}

/** One stake as a screen sees it. [text] is blank for a mystery that has not flipped yet. */
class SdItemView(
    val index: Int,
    val ownIdx: Int,
    val text: String,
    val mystery: Boolean,
    val mine: Boolean,
    /** 0 not yet claimed, 1 claimed by me, 2 claimed by them. */
    val claimedBy: Int,
)

data class ShowdownState(
    val phase: GPhase = GPhase.LOBBY,
    /** PICK (the challenger chooses), PLAY (a contest is on), CLAIM (somebody just won a stake), FINAL. */
    val stage: String = "PICK",
    /** Contests played so far, and how many stakes there are. */
    val played: Int = 0,
    val total: Int = 0,
    val items: List<SdItemView> = emptyList(),
    /** The stakes up for grabs this round (more than one after a tie). */
    val pot: List<SdItemView> = emptyList(),
    val chooserMe: Boolean = false,
    val options: List<GameKind> = emptyList(),
    val picked: GameKind? = null,
    /** How the last contest went: 0 none yet, 1 I won, 2 they won, 3 a tie (the stake rolled over). */
    val lastWinner: Int = 0,
    val lastKind: GameKind? = null,
    val myAck: Boolean = false,
    val theirAck: Boolean = false,
    val result: Int = 0,
    val why: String = "",
    val log: List<String> = emptyList(),
    val myMoney: Double = 0.0,
    val theirMoney: Double = 0.0,
    val iAmHost: Boolean = true,
) {
    val myClaims get() = items.filter { it.claimedBy == 1 }
    val theirClaims get() = items.filter { it.claimedBy == 2 }
    val potValueIsMoney get() = pot.isNotEmpty()
}

class ShowdownEngine(
    myName: String,
    theirName: String,
    isHost: Boolean,
    send: (GMsg) -> Unit,
    random: Random = Random.Default,
    clock: () -> Long = { System.currentTimeMillis() },
) : SyncEngine(GameKind.SHOWDOWN, myName, theirName, isHost, send, random, clock) {

    var state = ShowdownState(iAmHost = isHost)
        private set
    override val stateAny: Any get() = state
    override val phase get() = state.phase

    /** Host: how to start a contest (the app sets this). */
    var launch: (GameKind) -> Unit = {}

    /** This phone's own stakes, so a mystery can flip over when its round comes up. */
    var localStakes: List<Stake> = emptyList()

    fun pick(kind: GameKind) = act(JSONObject().put("a", "pick").put("k", kind.name))
    fun ack() = act(JSONObject().put("a", "ack"))

    // ---- the host's private game
    private class Item(val owner: Int, val ownIdx: Int, var text: String, val shown: Boolean, var revealed: Boolean, var claimedBy: Int = 0)

    private val items = ArrayList<Item>()
    private val order = ArrayList<Int>()
    private val pot = ArrayList<Int>()
    private var cursor = 0
    private var stage = "PICK"
    private var played = 0
    private var options = ArrayList<GameKind>()
    private var picked: GameKind? = null
    private var lastWinner = 0
    private var lastKind: GameKind? = null
    private val acks = BooleanArray(3)
    private var drawRetries = 0
    private var over = false
    private var result = 0
    private var why = ""
    private val log = ArrayList<String>()
    private val sentReveal = HashSet<Int>()

    private fun nameOf(p: Int) = if (p == 1) (if (isHost) myName else theirName) else (if (isHost) theirName else myName)
    private fun say(s: String) { log.add(s); while (log.size > 6) log.removeAt(0) }

    override fun hostBegin(opts: JSONObject) {
        items.clear(); order.clear(); pot.clear(); log.clear(); sentReveal.clear()
        fun load(key: String, owner: Int): List<Item> {
            val a = opts.optJSONArray(key) ?: return emptyList()
            val out = ArrayList<Item>()
            for (i in 0 until minOf(a.length(), StakeWire.MAX)) {
                val e = a.optJSONArray(i) ?: continue
                val text = StakeWire.clean(e.optString(0, ""))
                val shown = e.optInt(1, 1) == 1
                if (text.isEmpty() && shown) continue
                out += Item(owner, out.size, text, shown, shown)
            }
            return out
        }
        val a = load("mine", 1)
        val b = load("theirs", 2)
        // take it in turns, starting with a random player
        val first = if (random.nextBoolean()) a else b
        val second = if (first === a) b else a
        val all = ArrayList<Item>()
        for (k in 0 until maxOf(first.size, second.size)) { first.getOrNull(k)?.let { all.add(it) }; second.getOrNull(k)?.let { all.add(it) } }
        items.addAll(all)
        for (i in items.indices) order.add(i)
        cursor = 0; played = 0; drawRetries = 0; lastWinner = 0; lastKind = null; over = false; result = 0; why = ""
        if (items.isEmpty()) { finish("empty"); return }
        pot.add(order[cursor++])
        say("Showdown! ${items.size} stakes are up for grabs.")
        newRound()
    }

    private fun newRound() {
        stage = "PICK"; picked = null; acks.fill(false)
        for (i in pot) if (items[i].owner == 1) items[i].revealed = true
        val pool = ShowdownRules.CONTESTS.map { it.kind }.filter { it != lastKind }.shuffled(random)
        options = ArrayList(pool.take(ShowdownRules.OPTIONS_PER_ROUND))
    }

    private fun chooser(): Int = 3 - items[pot.last()].owner

    override fun hostHandle(fromHost: Boolean, a: JSONObject) {
        if (state.phase != GPhase.PLAYING || over) return
        val p = if (fromHost) 1 else 2
        when (a.getString("a")) {
            "pick" -> if (stage == "PICK" && p == chooser()) {
                val k = GameKind.values().firstOrNull { it.name == a.optString("k") } ?: return
                if (k !in options) return
                picked = k; stage = "PLAY"
                say("${nameOf(p)} picks ${k.label}.")
                publish()
                launch(k)
            }
            "ack" -> if (stage == "CLAIM") {
                acks[p] = true
                if (acks[1] && acks[2]) advance() else publish()
            }
            "rv" -> if (!fromHost) {
                val i = a.optInt("i", -1)
                if (i in items.indices && items[i].owner == 2 && !items[i].revealed && i in pot) {
                    items[i].text = StakeWire.clean(a.optString("t", ""))
                    items[i].revealed = items[i].text.isNotEmpty()
                    publish()
                }
            }
        }
    }

    /** Host: the contest that was started has ended. [result] is from the host's point of view: 1 the host won, 2 the guest won, 3 a tie. */
    fun contestFinished(kind: GameKind, result: Int) {
        if (!isHost || state.phase != GPhase.PLAYING || stage != "PLAY" || kind != picked) return
        played++
        lastKind = kind
        when (result) {
            1, 2 -> {
                for (i in pot) items[i].claimedBy = result
                lastWinner = result
                drawRetries = 0
                say("${nameOf(result)} wins ${kind.label} and claims ${pot.joinToString(" + ") { shownText(items[it]) }}!")
            }
            else -> {
                lastWinner = 3
                say("${kind.label} is a tie: the stake rolls over.")
            }
        }
        stage = "CLAIM"; acks.fill(false)
        publish()
    }

    private fun shownText(it: Item) = if (it.shown || it.revealed) it.text else "a mystery stake"

    private fun advance() {
        if (lastWinner == 3) {
            if (cursor < order.size) pot.add(order[cursor++])
            else { drawRetries++; if (drawRetries >= 2) { finish("tied"); return } }
        } else {
            pot.clear()
            if (cursor >= order.size) { finish("done"); return }
            pot.add(order[cursor++])
        }
        newRound()
        publish()
    }

    /** Host: stop the showdown (somebody went back to the menu). */
    fun abort() {
        if (!isHost || state.phase != GPhase.PLAYING || over) return
        finish("quit")
    }

    private fun finish(reason: String) {
        over = true; why = reason; stage = "FINAL"
        for (it in items) it.revealed = true
        val h = items.count { it.claimedBy == 1 }
        val g = items.count { it.claimedBy == 2 }
        val hm = StakeWire.money(items.filter { it.claimedBy == 1 }.map { it.text })
        val gm = StakeWire.money(items.filter { it.claimedBy == 2 }.map { it.text })
        result = when {
            h > g -> 1
            g > h -> 2
            hm > gm -> 1
            gm > hm -> 2
            else -> 3
        }
        say(when (result) { 1, 2 -> "${nameOf(result)} wins the showdown!"; else -> "The showdown ends level." })
        publish()
    }

    // ------------------------------------------------------------------ the picture sent to the other phone

    override fun hostSnapshot(): JSONObject {
        val it = JSONArray()
        for (i in items) {
            val visible = i.shown || i.revealed || over
            it.put(JSONArray().put(i.owner).put(if (visible) i.text else "").put(if (i.shown) 1 else 0).put(if (i.revealed || over) 1 else 0).put(i.claimedBy).put(i.ownIdx))
        }
        val opts = JSONArray(); options.forEach { opts.put(it.name) }
        return JSONObject()
            .put("st", stage).put("pl", played).put("it", it).put("po", JSONArray(pot))
            .put("ch", if (stage == "FINAL") 0 else chooser()).put("op", opts).put("pk", picked?.name ?: "")
            .put("lw", lastWinner).put("lk", lastKind?.name ?: "").put("ak", JSONArray().put(acks[1]).put(acks[2]))
            .put("ov", over).put("res", result).put("why", why).put("lg", JSONArray(log))
    }

    override fun adopt(s: JSONObject) {
        val me = if (isHost) 1 else 2
        fun flip(v: Int) = if (v == 1 || v == 2) (if (v == me) 1 else 2) else v
        val arr = s.optJSONArray("it")
        val views = ArrayList<SdItemView>()
        if (arr != null) for (i in 0 until minOf(arr.length(), StakeWire.MAX * 2)) {
            val e = arr.optJSONArray(i) ?: continue
            val owner = e.optInt(0, 1).coerceIn(1, 2)
            val text = StakeWire.clean(e.optString(1, ""))
            val shown = e.optInt(2, 1) == 1
            val revealed = e.optInt(3, 0) == 1
            views += SdItemView(i, e.optInt(5, 0).coerceIn(0, 10), text, mystery = !shown && !revealed, mine = owner == me, claimedBy = flip(e.optInt(4, 0).coerceIn(0, 2)))
        }
        val potIdx = ArrayList<Int>()
        s.optJSONArray("po")?.let { for (i in 0 until minOf(it.length(), 12)) { val v = it.optInt(i, -1); if (v in views.indices) potIdx.add(v) } }
        val ov = s.optBoolean("ov", false)
        val opts = ArrayList<GameKind>()
        s.optJSONArray("op")?.let { a -> for (i in 0 until minOf(a.length(), 8)) GameKind.values().firstOrNull { it.name == a.optString(i) }?.let { k -> if (ShowdownRules.contest(k) != null) opts.add(k) } }
        val ak = s.optJSONArray("ak")
        val myAckV = ak?.optBoolean(me - 1, false) ?: false
        val theirAckV = ak?.optBoolean(2 - me, false) ?: false
        val logArr = s.optJSONArray("lg")
        val claimedMine = views.filter { it.claimedBy == 1 && !it.mystery }.map { it.text }
        val claimedTheirs = views.filter { it.claimedBy == 2 && !it.mystery }.map { it.text }
        state = ShowdownState(
            phase = if (ov) GPhase.OVER else GPhase.PLAYING,
            stage = s.optString("st", "PICK").take(5), played = s.optInt("pl", 0).coerceIn(0, 100), total = views.size,
            items = views, pot = potIdx.map { views[it] },
            chooserMe = s.optInt("ch", 0) == me, options = opts,
            picked = GameKind.values().firstOrNull { it.name == s.optString("pk") },
            lastWinner = flip(s.optInt("lw", 0).coerceIn(0, 3)).let { if (s.optInt("lw", 0) == 3) 3 else it },
            lastKind = GameKind.values().firstOrNull { it.name == s.optString("lk") },
            myAck = myAckV, theirAck = theirAckV,
            result = if (!ov) 0 else flip(s.optInt("res", 0).coerceIn(0, 3)).let { if (s.optInt("res", 0) == 3) 3 else it },
            why = s.optString("why", "").take(8),
            log = if (logArr == null) emptyList() else List(minOf(logArr.length(), 6)) { logArr.optString(it, "").take(140) },
            myMoney = StakeWire.money(claimedMine), theirMoney = StakeWire.money(claimedTheirs), iAmHost = isHost,
        )
    }

    override fun localTick(now: Long) {
        // a mystery stake of mine flips over when its round comes up
        if (isHost || state.phase != GPhase.PLAYING) return
        for (v in state.pot) {
            if (v.mine && v.mystery && sentReveal.add(v.index)) {
                val text = localStakes.getOrNull(v.ownIdx)?.text.orEmpty()
                if (text.isNotBlank()) act(JSONObject().put("a", "rv").put("i", v.index).put("t", text))
            }
        }
    }

    override fun reset() {
        state = ShowdownState(iAmHost = isHost)
        sentReveal.clear()
    }
}
