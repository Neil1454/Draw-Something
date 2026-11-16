package com.neil1454.drawsomething.core

import org.json.JSONArray
import org.json.JSONObject
import kotlin.random.Random

// ====================================================================================
// The Slot Machine, for two. A five-reel, three-row machine with nine paylines.
//   • You each start with 1,000 credits and take turns on the same machine. Pick your bet per line (1, 2, 5 or 10; a spin costs nine
//     lines' worth), pull the lever, and the host's phone spins the reels.
//   • Matching symbols from the left pay along a line. ⭐ WILD stands in for any symbol; three 💰 SCATTERS anywhere pay a prize and start FREE SPINS
//     (every win is doubled). Five 7s on a line is the JACKPOT.
//   • After a win you can COLLECT it, or GAMBLE it: guess the colour of a hidden card, win and the prize doubles (up to three times), lose and
//     it's gone.
//   • When you've both used your spins, the player with the most credits wins.
// All the random numbers are drawn by the host's phone, so neither of you can cheat the machine.
// ====================================================================================

object SlotRules {
    /** The symbols, by letter: Cherry, Lemon, Orange, Grape, Bell, Diamond, Seven, Wild and the money-bag Scatter. */
    const val SYMBOLS = "CLOGBDSW$"
    val EMOJI = mapOf('C' to "🍒", 'L' to "🍋", 'O' to "🍊", 'G' to "🍇", 'B' to "🔔", 'D' to "💎", 'S' to "7️⃣", 'W' to "⭐", '$' to "💰")
    const val REELS = 5
    const val ROWS = 3
    const val LINES = 9
    const val START_CREDITS = 1000
    val BETS = intArrayOf(1, 2, 5, 10)
    val SPINS = intArrayOf(10, 15, 25)
    const val MAX_GAMBLES = 3
    const val FREE_MULT = 2

    /** For each payline, the row (0 top, 1 middle, 2 bottom) it passes through on each reel. */
    val PAYLINES = arrayOf(
        intArrayOf(1, 1, 1, 1, 1), intArrayOf(0, 0, 0, 0, 0), intArrayOf(2, 2, 2, 2, 2),
        intArrayOf(0, 1, 2, 1, 0), intArrayOf(2, 1, 0, 1, 2),
        intArrayOf(0, 0, 1, 2, 2), intArrayOf(2, 2, 1, 0, 0),
        intArrayOf(1, 0, 0, 0, 1), intArrayOf(1, 2, 2, 2, 1),
    )

    /** How often each symbol lands, out of the total (the same on every reel). */
    private val WEIGHTS = mapOf('C' to 24, 'L' to 21, 'O' to 18, 'G' to 14, 'B' to 10, 'D' to 5, 'S' to 3, 'W' to 4, '$' to 3)
    private val TOTAL = WEIGHTS.values.sum()

    /** What a line of 3, 4 or 5 pays, as a multiple of the bet per line. */
    private val PAYS = mapOf(
        'C' to intArrayOf(4, 12, 40), 'L' to intArrayOf(6, 16, 50), 'O' to intArrayOf(8, 20, 60), 'G' to intArrayOf(10, 30, 100),
        'B' to intArrayOf(16, 50, 200), 'D' to intArrayOf(30, 100, 400), 'S' to intArrayOf(50, 150, 500), 'W' to intArrayOf(50, 150, 500),
    )
    /** What [n] (3 to 5) of [symbol] on a line pays, as a multiple of the line bet (0 if it pays nothing). */
    fun pay(symbol: Char, n: Int): Int = if (n in 3..5) (PAYS[symbol]?.get(n - 3) ?: 0) else 0

    /** A pair of cherries on the left of a line is worth this many bets. */
    const val CHERRY_PAIR = 2

    /** Scatter prizes, as a multiple of the whole stake (all nine lines), and the free spins that come with them: for 3, 4 and 5 scatters. */
    val SCATTER_PAY = intArrayOf(3, 15, 75)
    val FREE_SPINS = intArrayOf(8, 12, 20)

    fun cost(bet: Int) = bet * LINES

    fun symbol(random: Random): Char {
        var r = random.nextInt(TOTAL)
        for ((c, w) in WEIGHTS) { if (r < w) return c; r -= w }
        return 'C'
    }

    /** A grid as 15 letters, reel by reel (the first three are the first reel, top to bottom). */
    fun spin(random: Random): String = String(CharArray(REELS * ROWS) { symbol(random) })

    fun at(grid: String, reel: Int, row: Int) = grid[reel * ROWS + row]

    class LineWin(val line: Int, val count: Int, val symbol: Char, val pay: Int)
    class Result(val lines: List<LineWin>, val scatters: Int, val scatterPay: Int, val freeSpins: Int, val total: Int, val jackpot: Boolean)

    /** What a grid wins at [bet] per line, with every prize multiplied by [mult]. */
    fun evaluate(grid: String, bet: Int, mult: Int = 1): Result {
        val wins = ArrayList<LineWin>()
        for ((li, line) in PAYLINES.withIndex()) {
            val syms = CharArray(REELS) { at(grid, it, line[it]) }
            var best: LineWin? = null
            // read the line as whatever its first real symbol is, with wilds standing in; or as a line of pure wilds
            val firstReal = syms.firstOrNull { it != 'W' }
            val bases = ArrayList<Char>()
            if (firstReal != null && firstReal != '$') bases.add(firstReal)
            if (syms[0] == 'W') bases.add('W')
            for (base in bases) {
                var n = 0
                while (n < REELS && (syms[n] == base || (syms[n] == 'W'))) n++
                val pay = when {
                    n >= 3 -> (PAYS[base] ?: continue)[n - 3] * bet
                    n == 2 && base == 'C' -> CHERRY_PAIR * bet
                    else -> 0
                }
                if (pay > 0 && (best == null || pay > best.pay)) best = LineWin(li, n, base, pay * mult)
            }
            best?.let { wins.add(it) }
        }
        val scat = grid.count { it == '$' }
        val scatPay = if (scat >= 3) SCATTER_PAY[minOf(scat, 5) - 3] * cost(bet) * mult else 0
        val free = if (scat >= 3) FREE_SPINS[minOf(scat, 5) - 3] else 0
        val jackpot = wins.any { it.symbol == 'S' && it.count == 5 } || wins.any { it.symbol == 'W' && it.count == 5 }
        return Result(wins, scat, scatPay, free, wins.sumOf { it.pay } + scatPay, jackpot)
    }

    /** The average return over [n] paid spins (free spins won are played out too, doubled): for tuning and tests. */
    fun simulate(n: Int, random: Random, bet: Int = 1): Double {
        var staked = 0L; var won = 0L
        repeat(n) {
            staked += cost(bet)
            var r = evaluate(spin(random), bet); won += r.total
            var free = r.freeSpins
            var guard = 0
            while (free > 0 && guard++ < 200) { free--; r = evaluate(spin(random), bet, FREE_MULT); won += r.total; free += r.freeSpins }
        }
        return won.toDouble() / staked
    }
}

data class SlotsState(
    val phase: GPhase = GPhase.LOBBY,
    /** BET (the player picks their bet and pulls the lever), REELS (spinning), WIN (collect or gamble) or END. */
    val stage: String = "BET",
    val myTurn: Boolean = false,
    val grid: String = "CCCCCCCCCCCCCCC",
    val lines: List<SlotRules.LineWin> = emptyList(),
    val scatters: Int = 0,
    val scatterPay: Int = 0,
    val jackpot: Boolean = false,
    /** The prize waiting to be collected (or gambled), and how many times it has been gambled. */
    val pending: Int = 0,
    val gambles: Int = 0,
    /** The hidden card from the last gamble: 0 none, 1 red, 2 black, and whether the gambler (who) won it. */
    val card: Int = 0,
    val cardWon: Boolean = false,
    val betIndex: Int = 1,
    val myCredits: Int = SlotRules.START_CREDITS,
    val theirCredits: Int = SlotRules.START_CREDITS,
    val mySpins: Int = 0,
    val theirSpins: Int = 0,
    /** Free spins left for the player whose turn it is. */
    val freeSpins: Int = 0,
    /** Counts up every spin so the screen can animate each one once. */
    val spinNo: Int = 0,
    /** Whether the spin on screen was a free one, and who spun it (1 me, 2 them). */
    val lastFree: Boolean = false,
    val lastBy: Int = 0,
    val lastBet: Int = 0,
    val total: Int = 0,
    val result: Int = 0,
    val iAmHost: Boolean = true,
) {
    val myScore get() = myCredits
    val theirScore get() = theirCredits
    val bet get() = SlotRules.BETS[betIndex.coerceIn(0, SlotRules.BETS.lastIndex)]
}

class SlotsEngine(
    myName: String,
    theirName: String,
    isHost: Boolean,
    send: (GMsg) -> Unit,
    random: Random = Random.Default,
    clock: () -> Long = { System.currentTimeMillis() },
) : SyncEngine(GameKind.SLOTS, myName, theirName, isHost, send, random, clock) {

    var state = SlotsState(iAmHost = isHost)
        private set
    override val stateAny: Any get() = state
    override val phase get() = state.phase

    /** Choose my bet per line (index into [SlotRules.BETS]). */
    fun setBet(i: Int) = act(JSONObject().put("a", "bet").put("i", i))
    /** Pull the lever. */
    fun spin() = act(JSONObject().put("a", "spin"))
    fun collect() = act(JSONObject().put("a", "collect"))
    /** Gamble the waiting prize on a hidden card: true guesses red, false black. */
    fun gamble(red: Boolean) = act(JSONObject().put("a", "gamble").put("red", red))

    // ---- the host's machine
    private val credits = IntArray(3)
    private val spinsLeft = IntArray(3)
    private var turn = 1
    private var stage = "BET"
    private var betIndex = 1
    private var grid = "CCCCCCCCCCCCCCC"
    private var result: SlotRules.Result? = null
    private var pending = 0
    private var gambles = 0
    private var card = 0
    private var cardWon = false
    private var free = 0
    private var freeBet = 0
    private var spinNo = 0
    private var lastFree = false
    private var lastBy = 0
    private var lastBet = 0
    private var total = 0
    private var reelsUntil = 0L
    private var over = false
    private var finalResult = 0

    override fun hostBegin(opts: JSONObject) {
        val spins = SlotRules.SPINS.getOrElse(opts.optInt("spins", 0)) { 10 }
        credits[1] = SlotRules.START_CREDITS; credits[2] = SlotRules.START_CREDITS
        spinsLeft[1] = spins; spinsLeft[2] = spins
        turn = if (random.nextBoolean()) 1 else 2
        stage = "BET"; betIndex = 1; grid = "CCCCCCCCCCCCCCC"; result = null; pending = 0; gambles = 0; card = 0; cardWon = false
        free = 0; freeBet = 0; spinNo = 0; lastFree = false; lastBy = 0; lastBet = 0; total = 0; over = false; finalResult = 0
        clampBet()
    }

    /** The highest bet the player to move can afford (at least the lowest one if they can afford anything at all). */
    private fun affordable(p: Int) = SlotRules.BETS.indices.filter { SlotRules.cost(SlotRules.BETS[it]) <= credits[p] }

    private fun clampBet() {
        val ok = affordable(turn)
        if (ok.isNotEmpty() && betIndex !in ok) betIndex = ok.last()
    }

    override fun hostHandle(fromHost: Boolean, a: JSONObject) {
        if (state.phase != GPhase.PLAYING || over) return
        val p = if (fromHost) 1 else 2
        if (turn != p) return
        when (a.getString("a")) {
            "bet" -> if (stage == "BET" && free == 0) {
                val i = a.optInt("i", -1)
                if (i in SlotRules.BETS.indices && i in affordable(p)) { betIndex = i; publish() }
            }
            "spin" -> if (stage == "BET") doSpin(p)
            "collect" -> if (stage == "WIN") { credits[p] += pending; endOfSpin(); publish() }
            "gamble" -> if (stage == "WIN" && gambles < SlotRules.MAX_GAMBLES && pending > 0) {
                val red = a.optBoolean("red", true)
                val c = if (random.nextBoolean()) 1 else 2
                card = c; cardWon = (c == 1) == red; gambles++
                if (cardWon) pending *= 2 else { pending = 0; stage = "LOST"; reelsUntil = clock() + 1800L }
                publish()
            }
        }
    }

    private fun doSpin(p: Int) {
        val isFree = free > 0
        val bet = if (isFree) freeBet else SlotRules.BETS[betIndex]
        if (!isFree) {
            if (spinsLeft[p] <= 0 || credits[p] < SlotRules.cost(bet)) return
            credits[p] -= SlotRules.cost(bet); spinsLeft[p]--
        } else free--
        grid = SlotRules.spin(random)
        val r = SlotRules.evaluate(grid, bet, if (isFree) SlotRules.FREE_MULT else 1)
        result = r; total = r.total; lastFree = isFree; lastBy = p; lastBet = bet; spinNo++
        pending = r.total; gambles = 0; card = 0; cardWon = false
        if (r.freeSpins > 0) { free += r.freeSpins; freeBet = bet }
        stage = "REELS"; reelsUntil = clock() + 2800L
        publish()
    }

    /** After the prize is collected (or lost): next free spin, next player, or the end. */
    private fun endOfSpin() {
        pending = 0
        if (free > 0) { stage = "BET"; return }
        nextPlayer()
    }

    private fun nextPlayer() {
        free = 0
        // anybody who cannot afford another spin is out of the game
        for (q in 1..2) if (spinsLeft[q] > 0 && affordable(q).isEmpty()) spinsLeft[q] = 0
        val other = 3 - turn
        turn = if (spinsLeft[other] > 0) other else if (spinsLeft[turn] > 0) turn else { finish(); return }
        stage = "BET"; clampBet()
    }

    private fun finish() {
        over = true; stage = "END"
        finalResult = if (credits[1] > credits[2]) 1 else if (credits[2] > credits[1]) 2 else 3
    }

    override fun hostTick(now: Long) {
        if (over) return
        if (stage == "REELS" && now >= reelsUntil) {
            val r = result
            if (pending > 0 && r != null) { stage = "WIN" } else { endOfSpin() }
            publish()
        } else if (stage == "LOST" && now >= reelsUntil) {
            endOfSpin(); publish()
        }
    }

    override fun hostSnapshot(): JSONObject {
        val r = result
        val ln = JSONArray()
        if (r != null && stage != "BET") for (w in r.lines) ln.put(JSONArray().put(w.line).put(w.count).put(w.symbol.toString()).put(w.pay))
        return JSONObject()
            .put("st", stage).put("tn", turn).put("g", grid).put("ln", ln).put("sc", r?.scatters ?: 0).put("sp", r?.scatterPay ?: 0).put("jp", r?.jackpot ?: false)
            .put("pd", pending).put("gb", gambles).put("cd", card).put("cw", cardWon).put("bi", betIndex)
            .put("cr", JSONArray().put(credits[1]).put(credits[2])).put("sl", JSONArray().put(spinsLeft[1]).put(spinsLeft[2]))
            .put("fr", free).put("sn", spinNo).put("lf", lastFree).put("lb", lastBy).put("bt", lastBet).put("tt", total)
            .put("ov", over).put("res", finalResult)
    }

    override fun adopt(s: JSONObject) {
        val g = s.optString("g", "")
        if (g.length != SlotRules.REELS * SlotRules.ROWS || g.any { it !in SlotRules.SYMBOLS }) return
        val cr = s.optJSONArray("cr") ?: return
        val sl = s.optJSONArray("sl") ?: return
        if (cr.length() < 2 || sl.length() < 2) return
        val me = if (isHost) 1 else 2
        val mi = me - 1; val ti = 2 - me
        val lines = ArrayList<SlotRules.LineWin>()
        s.optJSONArray("ln")?.let { a ->
            for (i in 0 until minOf(a.length(), SlotRules.LINES)) {
                val e = a.optJSONArray(i) ?: continue
                val li = e.optInt(0, -1); val n = e.optInt(1, 0); val sym = e.optString(2, "").firstOrNull() ?: continue
                if (li !in 0 until SlotRules.LINES || n !in 2..5 || sym !in SlotRules.SYMBOLS) continue
                lines.add(SlotRules.LineWin(li, n, sym, e.optInt(3, 0).coerceIn(0, 1_000_000)))
            }
        }
        val ov = s.optBoolean("ov", false)
        val res = s.optInt("res", 0).coerceIn(0, 3)
        val lb = s.optInt("lb", 0).coerceIn(0, 2)
        val stage = s.optString("st", "BET").take(5)
        state = SlotsState(
            phase = if (ov) GPhase.OVER else GPhase.PLAYING, stage = stage, myTurn = !ov && s.optInt("tn", 1) == me,
            grid = g, lines = lines, scatters = s.optInt("sc", 0).coerceIn(0, 15), scatterPay = s.optInt("sp", 0).coerceIn(0, 10_000_000), jackpot = s.optBoolean("jp", false),
            pending = s.optInt("pd", 0).coerceIn(0, 10_000_000), gambles = s.optInt("gb", 0).coerceIn(0, SlotRules.MAX_GAMBLES), card = s.optInt("cd", 0).coerceIn(0, 2), cardWon = s.optBoolean("cw", false),
            betIndex = s.optInt("bi", 1).coerceIn(0, SlotRules.BETS.lastIndex),
            myCredits = cr.optInt(mi, 0).coerceIn(0, 100_000_000), theirCredits = cr.optInt(ti, 0).coerceIn(0, 100_000_000),
            mySpins = sl.optInt(mi, 0).coerceIn(0, 100), theirSpins = sl.optInt(ti, 0).coerceIn(0, 100),
            freeSpins = s.optInt("fr", 0).coerceIn(0, 200), spinNo = s.optInt("sn", 0).coerceIn(0, 100_000), lastFree = s.optBoolean("lf", false),
            lastBy = if (lb == 0) 0 else if (lb == me) 1 else 2, lastBet = s.optInt("bt", 0).coerceIn(0, 10), total = s.optInt("tt", 0).coerceIn(0, 100_000_000),
            result = if (!ov) 0 else if (res == 3) 3 else if (res == me) 1 else 2, iAmHost = isHost,
        )
    }

    override fun reset() {
        state = SlotsState(iAmHost = isHost)
    }
}

// ====================================================================== the computer player

class SlotsBrain(private val e: SlotsEngine, skill: Skill, random: Random) : Brain(skill, random) {
    override fun think(now: Long) {
        val s = e.state
        if (s.phase != GPhase.PLAYING || !s.myTurn) return
        when (s.stage) {
            "BET" -> if (ready(now, "sb${s.spinNo}", 1100, 2200)) {
                if (s.freeSpins == 0) {
                    val ok = SlotRules.BETS.indices.filter { SlotRules.cost(SlotRules.BETS[it]) <= s.myCredits }
                    if (ok.isEmpty()) return
                    val behind = s.myCredits < s.theirCredits
                    val want = when (skill) {
                        Skill.EASY -> ok[random.nextInt(ok.size)]
                        Skill.NORMAL -> if (behind && s.mySpins <= 4) ok.last() else ok[minOf(1, ok.lastIndex)]
                        // the sharp player plays safe when it is ahead and goes big when it needs to catch up, more so the fewer spins are left
                        Skill.HARD -> if (!behind && s.myCredits > s.theirCredits + 100) ok.first() else if (behind && s.mySpins <= 5) ok.last() else ok[minOf(2, ok.lastIndex)]
                    }
                    if (want != s.betIndex) { e.setBet(want); return }
                }
                e.spin()
            }
            "WIN" -> if (ready(now, "sw${s.spinNo}-${s.gambles}", 1400, 2800)) {
                val behind = s.myCredits + s.pending < s.theirCredits
                val gambleChance = when (skill) { Skill.EASY -> 30; Skill.NORMAL -> 12; Skill.HARD -> if (behind && s.mySpins <= 3) 60 else 4 }
                if (s.gambles < SlotRules.MAX_GAMBLES && s.pending > 0 && pct(gambleChance)) e.gamble(random.nextBoolean()) else e.collect()
            }
        }
    }
}
