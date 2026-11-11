package com.neil1454.drawsomething.core

import org.json.JSONArray
import org.json.JSONObject
import kotlin.random.Random

// ====================================================================================
// Monopoly (UK / London board) for two players.
//  - Start with £1500, collect £200 passing GO. Roll two dice, buy what you land on or put it up for AUCTION.
//  - Own a whole colour set to build houses (evenly), then hotels. Rent goes up steeply with buildings.
//  - Stations and utilities, Chance and Community Chest, Income Tax and Super Tax, Jail (pay £50, use a card or roll a double),
//    three doubles in a row sends you to Jail. Mortgage property to raise cash, and TRADE with the other player.
//  - If you can't pay what you owe even after selling and mortgaging, you are bankrupt and the other player wins.
//  - Short games: the richest player (cash + property + buildings) wins after a set number of turns each.
// The host plays first. Everything is decided on the host's phone; the guest sees the result.
// ====================================================================================

enum class MonopolyLength(val label: String, val turns: Int) {
    FULL("Full game", 0),
    SHORT("Short: 30 turns each", 30),
    QUICK("Quick: 15 turns each", 15),
}

class TradeOffer(val from: Int, val giveSq: List<Int>, val giveCash: Int, val getSq: List<Int>, val getCash: Int)

data class MonopolyState(
    val myName: String,
    val theirName: String,
    val phase: GPhase = GPhase.LOBBY,
    val iAmHost: Boolean = true,
    val myCash: Int = MonopolyBoard.START_CASH,
    val theirCash: Int = MonopolyBoard.START_CASH,
    val myPos: Int = 0,
    val theirPos: Int = 0,
    val myJail: Boolean = false,
    val theirJail: Boolean = false,
    val myJailTries: Int = 0,
    val myFreeCards: Int = 0,
    val theirFreeCards: Int = 0,
    /** Per square: 0 nobody, 1 me, 2 them. */
    val owner: List<Int> = List(MonopolyBoard.SIZE) { 0 },
    /** 0..4 houses, 5 = hotel. */
    val houses: List<Int> = List(MonopolyBoard.SIZE) { 0 },
    val mortgaged: List<Boolean> = List(MonopolyBoard.SIZE) { false },
    val myTurn: Boolean = false,
    /** ROLL, BUY, AUCTION, DEBT, END or OVER. */
    val step: String = "ROLL",
    val d1: Int = 0,
    val d2: Int = 0,
    val doubles: Int = 0,
    val housesLeft: Int = MonopolyBoard.HOUSES,
    val hotelsLeft: Int = MonopolyBoard.HOTELS,
    val log: List<String> = emptyList(),
    /** Goes up with every notable event so screens can react once. */
    val seq: Int = 0,
    /** What the latest event was: roll, buy, rent, tax, card, jail, go, build, sell, mortgage, auction, trade, bankrupt. */
    val ev: String = "",
    val evByMe: Boolean = false,
    val evSq: Int = -1,
    val evAmount: Int = 0,
    /** The card that was just drawn (text), with which deck it came from (0 Chance, 1 Community Chest). */
    val cardText: String = "",
    val cardDeck: Int = 0,
    // money owed
    val debt: Int = 0,
    /** Who is owed: 0 the bank, 1 me, 2 them. */
    val debtTo: Int = 0,
    val debtWhy: String = "",
    // auction
    val auctionSq: Int = -1,
    val auctionBid: Int = 0,
    /** 0 nobody yet, 1 me, 2 them. */
    val auctionLeader: Int = 0,
    val myBidTurn: Boolean = false,
    // trade
    val trade: TradeOffer? = null,
    val maxTurns: Int = 0,
    val myTurnsTaken: Int = 0,
    val theirTurnsTaken: Int = 0,
    val myWorth: Int = MonopolyBoard.START_CASH,
    val theirWorth: Int = MonopolyBoard.START_CASH,
    val moves: Int = 0,
    /** 0 playing, 1 I won, 2 they won, 3 draw. */
    val result: Int = 0,
    /** "bankrupt" or "worth". */
    val why: String = "",
    /** The opening roll (0 = not rolled yet), and whether it was a tie that has to be rolled again. */
    val firstMine: Int = 0,
    val firstTheirs: Int = 0,
    val firstTie: Boolean = false,
    /** House rule: you can't buy until you have been round the board once (collected from GO). */
    val lapRule: Boolean = false,
    val myPassedGo: Boolean = true,
    val theirPassedGo: Boolean = true,
) {
    val total get() = d1 + d2
    val ownerArr get() = IntArray(owner.size) { owner[it] }
    val housesArr get() = IntArray(houses.size) { houses[it] }
    val mortArr get() = BooleanArray(mortgaged.size) { mortgaged[it] }
    val canRoll get() = myTurn && step == "ROLL"
    /** The opening roll to decide who goes first: highest total wins. */
    val canFirstRoll get() = step == "FIRST" && (firstMine == 0 || firstTie)
    val canManage get() = myTurn && (step == "ROLL" || step == "END" || step == "DEBT")
    val canTrade get() = myTurn && (step == "ROLL" || step == "END") && trade == null
    fun owns(sq: Int) = owner[sq] == 1
}

class MonopolyEngine(
    myName: String,
    theirName: String,
    isHost: Boolean,
    send: (GMsg) -> Unit,
    random: Random = Random.Default,
    clock: () -> Long = { System.currentTimeMillis() },
) : SyncEngine(GameKind.MONOPOLY, myName, theirName, isHost, send, random, clock) {

    var state = MonopolyState(myName, theirName, iAmHost = isHost)
        private set
    override val stateAny: Any get() = state
    override val phase get() = state.phase

    /** Tests can queue dice (pairs) to be thrown instead of random ones. */
    val forcedDice = ArrayDeque<Pair<Int, Int>>()

    // ---- host-only model (players are 1 = host, 2 = guest)
    private val cash = IntArray(3)
    private val pos = IntArray(3)
    private val jailed = BooleanArray(3)
    private val jailTries = IntArray(3)
    private val freeCards = Array(3) { ArrayList<Int>() } // deck index of each held card (0 Chance, 1 Chest)
    private val owner = IntArray(MonopolyBoard.SIZE)
    private val houses = IntArray(MonopolyBoard.SIZE)
    private val mortgaged = BooleanArray(MonopolyBoard.SIZE)
    private var chance = ArrayDeque<Int>()
    private var chest = ArrayDeque<Int>()
    private var turn = 1
    private var step = "ROLL"
    private var d1 = 0
    private var d2 = 0
    private var doubles = 0
    private var noExtra = false
    private val log = ArrayList<String>()
    private var seq = 0
    private var ev = ""
    private var evBy = 0
    private var evSq = -1
    private var evAmount = 0
    private var cardText = ""
    private var cardDeck = 0
    private var debt = 0
    private var debtTo = 0
    private var debtWhy = ""
    private var resumeSteps = 0
    private var aSq = -1
    private var aBid = 0
    private var aLeader = 0
    private var aTurn = 0
    private var aPasses = 0
    private var tradeOffer: TradeOffer? = null
    private var maxTurns = 0
    private val firstRoll = IntArray(3)
    private var firstTie = false
    private var lapRule = false
    private val passedGo = BooleanArray(3)
    private val turnsTaken = IntArray(3)
    private var moves = 0
    private var over = false
    private var winner = 0
    private var why = ""

    override fun hostBegin(opts: JSONObject) {
        val len = MonopolyLength.values()[opts.optInt("len", 0).coerceIn(0, MonopolyLength.values().size - 1)]
        maxTurns = len.turns
        cash.fill(MonopolyBoard.START_CASH); cash[0] = 0
        pos.fill(0); jailed.fill(false); jailTries.fill(0)
        freeCards.forEach { it.clear() }
        owner.fill(0); houses.fill(0); mortgaged.fill(false)
        chance = ArrayDeque((0 until MonopolyCards.chance.size).shuffled(random))
        chest = ArrayDeque((0 until MonopolyCards.chest.size).shuffled(random))
        lapRule = opts.optBoolean("lap", true)
        passedGo.fill(false)
        firstRoll.fill(0); firstTie = false
        turn = 0; step = "FIRST"; d1 = 0; d2 = 0; doubles = 0; noExtra = false
        log.clear(); seq = 0; ev = ""; evBy = 0; evSq = -1; evAmount = 0; cardText = ""
        debt = 0; debtTo = 0; debtWhy = ""; resumeSteps = 0
        aSq = -1; aBid = 0; aLeader = 0; aTurn = 0; aPasses = 0
        tradeOffer = null
        turnsTaken.fill(0); moves = 0; over = false; winner = 0; why = ""
        say("Roll the dice: the highest total goes first.")
    }

    // ------------------------------------------------------------------ what the screens call
    fun roll() = act(JSONObject().put("a", "roll"))
    fun buy() = act(JSONObject().put("a", "buy"))
    fun declineToAuction() = act(JSONObject().put("a", "auction"))
    fun bid(amount: Int) = act(JSONObject().put("a", "bid").put("n", amount))
    fun passBid() = act(JSONObject().put("a", "pass"))
    fun endTurn() = act(JSONObject().put("a", "end"))
    fun payJail() = act(JSONObject().put("a", "payjail"))
    fun useJailCard() = act(JSONObject().put("a", "usecard"))
    fun build(sq: Int) = act(JSONObject().put("a", "build").put("sq", sq))
    fun sellHouse(sq: Int) = act(JSONObject().put("a", "sell").put("sq", sq))
    fun mortgage(sq: Int) = act(JSONObject().put("a", "mortgage").put("sq", sq))
    fun unmortgage(sq: Int) = act(JSONObject().put("a", "unmortgage").put("sq", sq))
    fun payDebt() = act(JSONObject().put("a", "paydebt"))
    fun declareBankrupt() = act(JSONObject().put("a", "bankrupt"))
    fun offerTrade(giveSq: List<Int>, giveCash: Int, getSq: List<Int>, getCash: Int) =
        act(JSONObject().put("a", "offer").put("gs", JSONArray(giveSq)).put("gc", giveCash).put("ws", JSONArray(getSq)).put("wc", getCash))
    fun acceptTrade() = act(JSONObject().put("a", "accept"))
    fun declineTrade() = act(JSONObject().put("a", "decline"))
    fun cancelTrade() = act(JSONObject().put("a", "cancel"))

    /** For tests: put a player's token on a square (host only; shown at the next update). */
    fun debugSetPos(who: Int, sq: Int) { if (isHost) pos[who] = sq.coerceIn(0, MonopolyBoard.SIZE - 1) }

    private fun nameOf(who: Int) = if ((who == 1) == isHost) myName else theirName

    private fun say(text: String) {
        log.add(text)
        while (log.size > 14) log.removeAt(0)
    }

    private fun event(type: String, by: Int, sq: Int = -1, amount: Int = 0) {
        seq++; ev = type; evBy = by; evSq = sq; evAmount = amount
    }

    // ------------------------------------------------------------------ rules
    override fun hostHandle(fromHost: Boolean, a: JSONObject) {
        if (state.phase != GPhase.PLAYING || over) return
        val who = if (fromHost) 1 else 2
        val action = a.getString("a")
        // trade replies come from the player who was offered the deal, whoever's turn it is
        when (action) {
            "accept", "decline" -> { respondTrade(who, action == "accept"); publish(); return }
            "cancel" -> { if (tradeOffer?.from == who) { tradeOffer = null; say("${nameOf(who)} withdrew the offer."); publish() }; return }
        }
        // an auction is for both players
        if (step == "AUCTION") {
            if (who != aTurn) return
            when (action) {
                "bid" -> auctionBid(who, a.getInt("n"))
                "pass" -> auctionPass(who)
                else -> return
            }
            publish()
            return
        }
        if (step == "FIRST") {
            if (action == "roll" && doFirstRoll(who)) publish()
            return
        }
        if (who != turn) return
        val ok = when (action) {
            "roll" -> doRoll(who)
            "buy" -> doBuy(who)
            "auction" -> if (step == "BUY") { startAuction(pos[who]); true } else false
            "end" -> doEnd(who)
            "payjail" -> doPayJail(who)
            "usecard" -> doUseCard(who)
            "build" -> manage(who) { doBuild(who, a.getInt("sq")) }
            "sell" -> manage(who) { doSell(who, a.getInt("sq")) }
            "mortgage" -> manage(who) { doMortgage(who, a.getInt("sq")) }
            "unmortgage" -> manage(who) { doUnmortgage(who, a.getInt("sq")) }
            "paydebt" -> doPayDebt(who)
            "bankrupt" -> if (step == "DEBT") { goBankrupt(who); true } else false
            "offer" -> doOffer(who, a)
            else -> false
        }
        if (ok) publish()
    }

    private fun manage(who: Int, block: () -> Boolean): Boolean =
        if (step == "ROLL" || step == "END" || step == "DEBT") block() else false

    // ---- dice and movement
    private fun throwDice() {
        val f = forcedDice.removeFirstOrNull()
        if (f != null) { d1 = f.first; d2 = f.second } else { d1 = random.nextInt(1, 7); d2 = random.nextInt(1, 7) }
    }

    /** The opening roll: each player rolls once, the highest total goes first, a tie is rolled again. */
    private fun doFirstRoll(who: Int): Boolean {
        if (firstTie) { firstRoll.fill(0); firstTie = false }
        if (firstRoll[who] != 0) return false
        throwDice()
        firstRoll[who] = d1 + d2
        event("roll", who)
        say("${nameOf(who)} rolls $d1 + $d2 = ${d1 + d2}.")
        if (firstRoll[1] != 0 && firstRoll[2] != 0) {
            if (firstRoll[1] == firstRoll[2]) {
                firstTie = true
                say("A tie! Roll again.")
            } else {
                turn = if (firstRoll[1] > firstRoll[2]) 1 else 2
                step = "ROLL"
                say("${nameOf(turn)} rolled highest and goes first!")
            }
        }
        return true
    }

    private fun doRoll(who: Int): Boolean {
        if (step != "ROLL") return false
        throwDice()
        moves++
        event("roll", who)
        val isDouble = d1 == d2
        if (jailed[who]) {
            if (isDouble) {
                jailed[who] = false; jailTries[who] = 0; noExtra = true
                say("${nameOf(who)} rolled a double ($d1,$d2) and gets out of jail!")
                advance(who, d1 + d2)
            } else {
                jailTries[who]++
                if (jailTries[who] >= 3) {
                    say("${nameOf(who)} rolled $d1 and $d2: no double. Third try, so pay the £${MonopolyBoard.JAIL_FINE} fine and move.")
                    resumeSteps = d1 + d2
                    noExtra = true
                    if (charge(who, MonopolyBoard.JAIL_FINE, 0, "jail fine")) {
                        jailed[who] = false; jailTries[who] = 0
                        val n = resumeSteps; resumeSteps = 0
                        advance(who, n)
                    }
                } else {
                    say("${nameOf(who)} rolled $d1 and $d2: no double, still in jail (try ${jailTries[who]} of 3).")
                    step = "END"
                }
            }
            return true
        }
        if (isDouble) doubles++ else doubles = 0
        if (doubles >= 3) {
            say("${nameOf(who)} rolled three doubles in a row: straight to jail!")
            sendToJail(who)
            return true
        }
        say("${nameOf(who)} rolled $d1 + $d2 = ${d1 + d2}${if (isDouble) " (double!)" else ""}.")
        advance(who, d1 + d2)
        return true
    }

    private fun advance(who: Int, steps: Int) {
        val from = pos[who]
        val to = (from + steps) % MonopolyBoard.SIZE
        if (from + steps >= MonopolyBoard.SIZE) collectGo(who)
        pos[who] = to
        land(who)
    }

    private fun collectGo(who: Int) {
        passedGo[who] = true
        cash[who] += MonopolyBoard.GO_SALARY
        say("${nameOf(who)} passes GO and collects £${MonopolyBoard.GO_SALARY}.")
        event("go", who, 0, MonopolyBoard.GO_SALARY)
    }

    private fun moveTo(who: Int, sq: Int, collect: Boolean = true) {
        if (collect && sq < pos[who]) collectGo(who)
        pos[who] = sq
    }

    private fun sendToJail(who: Int) {
        pos[who] = MonopolyBoard.JAIL
        jailed[who] = true; jailTries[who] = 0
        doubles = 0
        event("jail", who, MonopolyBoard.JAIL)
        step = "END"
    }

    private fun land(who: Int) {
        val sq = pos[who]
        val s = MonopolyBoard.squares[sq]
        when (s.kind) {
            SqKind.PROPERTY, SqKind.STATION, SqKind.UTILITY -> landOnProperty(who, sq, false, false)
            SqKind.TAX -> {
                say("${nameOf(who)} pays ${s.name}: £${s.rents[0]}.")
                event("tax", who, sq, s.rents[0])
                if (charge(who, s.rents[0], 0, s.name)) finishMove(who)
            }
            SqKind.CHANCE -> draw(who, 0)
            SqKind.CHEST -> draw(who, 1)
            SqKind.GOTOJAIL -> {
                say("${nameOf(who)} goes to jail!")
                sendToJail(who)
            }
            else -> finishMove(who)
        }
    }

    private fun landOnProperty(who: Int, sq: Int, stationDouble: Boolean, utilityTen: Boolean) {
        val s = MonopolyBoard.squares[sq]
        val o = owner[sq]
        when {
            o == 0 && lapRule && !passedGo[who] -> {
                say("${nameOf(who)} lands on ${s.name} but can't buy yet: house rule, go round the board once first.")
                finishMove(who)
            }
            o == 0 -> {
                step = "BUY"
                say("${nameOf(who)} lands on ${s.name} (£${s.price}), which is for sale.")
            }
            o == who -> {
                say("${nameOf(who)} lands on their own ${s.name}.")
                finishMove(who)
            }
            mortgaged[sq] -> {
                say("${nameOf(who)} lands on ${s.name}, which is mortgaged. No rent.")
                finishMove(who)
            }
            else -> {
                val rent = MonopolyRules.rent(owner, houses, mortgaged, sq, d1 + d2, stationDouble, utilityTen)
                say("${nameOf(who)} pays ${nameOf(o)} £$rent rent for ${s.name}.")
                event("rent", who, sq, rent)
                if (charge(who, rent, o, "rent for ${s.name}")) finishMove(who)
            }
        }
    }

    /** Pay [amount] to [to] (0 = the bank). If they can't afford it right now the game waits in the DEBT step. */
    private fun charge(who: Int, amount: Int, to: Int, why: String): Boolean {
        if (amount <= 0) return true
        if (cash[who] >= amount) {
            cash[who] -= amount
            if (to != 0) cash[to] += amount
            return true
        }
        debt = amount; debtTo = to; debtWhy = why
        step = "DEBT"
        say("${nameOf(who)} owes £$amount ($why) but only has £${cash[who]}. Sell or mortgage something!")
        return false
    }

    private fun finishMove(who: Int) {
        step = if (!jailed[who] && !noExtra && d1 == d2 && doubles in 1..2 && d1 > 0) "ROLL" else "END"
        if (step == "ROLL") say("${nameOf(who)} rolled a double, so rolls again.")
    }

    // ---- buying and auctions
    private fun doBuy(who: Int): Boolean {
        if (step != "BUY") return false
        val sq = pos[who]
        val s = MonopolyBoard.squares[sq]
        if (cash[who] < s.price) return false
        cash[who] -= s.price
        owner[sq] = who
        say("${nameOf(who)} buys ${s.name} for £${s.price}.")
        event("buy", who, sq, s.price)
        finishMove(who)
        return true
    }

    private fun startAuction(sq: Int) {
        step = "AUCTION"
        aSq = sq; aBid = 0; aLeader = 0; aPasses = 0
        aTurn = 3 - turn
        say("${nameOf(turn)} passes on ${MonopolyBoard.squares[sq].name}. It goes to auction!")
        event("auction", turn, sq)
    }

    private fun auctionBid(who: Int, amount: Int) {
        if (amount <= aBid || amount < 1 || amount > cash[who]) return
        aBid = amount; aLeader = who; aTurn = 3 - who; aPasses = 0
        say("${nameOf(who)} bids £$amount.")
    }

    private fun auctionPass(who: Int) {
        val s = MonopolyBoard.squares[aSq]
        if (aLeader != 0) {
            // somebody is ahead and the other has passed: sold
            cash[aLeader] -= aBid
            owner[aSq] = aLeader
            say("${nameOf(aLeader)} wins ${s.name} at auction for £$aBid.")
            event("buy", aLeader, aSq, aBid)
            aSq = -1; aBid = 0
            finishMove(turn)
            return
        }
        aPasses++
        if (aPasses >= 2) {
            say("Nobody bids. ${s.name} stays with the bank.")
            aSq = -1
            finishMove(turn)
        } else {
            aTurn = 3 - who
        }
    }

    // ---- ending a turn
    private fun doEnd(who: Int): Boolean {
        if (step != "END") return false
        turnsTaken[who]++
        tradeOffer = null
        doubles = 0; noExtra = false; d1 = 0; d2 = 0
        if (maxTurns > 0 && turnsTaken[1] >= maxTurns && turnsTaken[2] >= maxTurns) {
            over = true; step = "OVER"; why = "worth"
            val w1 = MonopolyRules.netWorth(owner, houses, mortgaged, 1, cash[1])
            val w2 = MonopolyRules.netWorth(owner, houses, mortgaged, 2, cash[2])
            winner = if (w1 > w2) 1 else if (w2 > w1) 2 else 3
            say("Time! ${nameOf(1)} is worth £$w1 and ${nameOf(2)} is worth £$w2.")
            return true
        }
        turn = 3 - who
        step = "ROLL"
        say("${nameOf(turn)}'s turn.")
        return true
    }

    // ---- jail
    private fun doPayJail(who: Int): Boolean {
        if (step != "ROLL" || !jailed[who] || cash[who] < MonopolyBoard.JAIL_FINE) return false
        cash[who] -= MonopolyBoard.JAIL_FINE
        jailed[who] = false; jailTries[who] = 0
        say("${nameOf(who)} pays £${MonopolyBoard.JAIL_FINE} to get out of jail.")
        return true
    }

    private fun doUseCard(who: Int): Boolean {
        if (step != "ROLL" || !jailed[who] || freeCards[who].isEmpty()) return false
        val deck = freeCards[who].removeAt(0)
        // the card goes back to the bottom of its pile
        val idx = (if (deck == 0) MonopolyCards.chance else MonopolyCards.chest).indexOfFirst { it.type == "free" }
        (if (deck == 0) chance else chest).addLast(idx)
        jailed[who] = false; jailTries[who] = 0
        say("${nameOf(who)} uses a Get Out of Jail Free card.")
        return true
    }

    // ---- cards
    private fun draw(who: Int, deck: Int) {
        val pile = if (deck == 0) chance else chest
        val cards = if (deck == 0) MonopolyCards.chance else MonopolyCards.chest
        val idx = pile.removeFirst()
        val c = cards[idx]
        cardText = c.text; cardDeck = deck
        say("${nameOf(who)} draws ${if (deck == 0) "Chance" else "Community Chest"}: ${c.text}")
        event("card", who, pos[who])
        if (c.type == "free") freeCards[who].add(deck) else pile.addLast(idx)
        when (c.type) {
            "move" -> {
                moveTo(who, c.a)
                land(who)
            }
            "back" -> {
                pos[who] = (pos[who] - c.a + MonopolyBoard.SIZE) % MonopolyBoard.SIZE
                land(who)
            }
            "station" -> {
                moveTo(who, MonopolyBoard.nextStation(pos[who]))
                landOnProperty(who, pos[who], stationDouble = true, utilityTen = false)
            }
            "utility" -> {
                moveTo(who, MonopolyBoard.nextUtility(pos[who]))
                landOnProperty(who, pos[who], stationDouble = false, utilityTen = true)
            }
            "cash" -> {
                if (c.a >= 0) { cash[who] += c.a; finishMove(who) }
                else if (charge(who, -c.a, 0, "card")) finishMove(who)
            }
            "each" -> {
                val other = 3 - who
                if (c.a > 0) {
                    val take = minOf(c.a, cash[other])
                    // if the other player can't pay, they settle what they can (keeps the game moving)
                    cash[other] -= take; cash[who] += take
                    finishMove(who)
                } else if (charge(who, -c.a, other, "card")) finishMove(who)
            }
            "repairs" -> {
                var total = 0
                for (i in 0 until MonopolyBoard.SIZE) if (owner[i] == who) total += if (houses[i] == 5) c.b else houses[i] * c.a
                if (total == 0) { say("${nameOf(who)} has no buildings: nothing to pay."); finishMove(who) }
                else { say("${nameOf(who)} pays £$total for repairs."); if (charge(who, total, 0, "repairs")) finishMove(who) }
            }
            "jail" -> sendToJail(who)
            "free" -> finishMove(who)
        }
    }

    // ---- buildings and mortgages
    private fun housesLeft() = MonopolyBoard.HOUSES - MonopolyRules.housesUsed(houses)
    private fun hotelsLeft() = MonopolyBoard.HOTELS - MonopolyRules.hotelsUsed(houses)

    private fun doBuild(who: Int, sq: Int): Boolean {
        if (step == "DEBT" || sq !in 0 until MonopolyBoard.SIZE) return false
        if (!MonopolyRules.canBuild(owner, houses, mortgaged, who, sq, cash[who], housesLeft(), hotelsLeft())) return false
        val s = MonopolyBoard.squares[sq]
        cash[who] -= s.houseCost
        houses[sq]++
        say("${nameOf(who)} builds ${if (houses[sq] == 5) "a hotel" else "a house"} on ${s.name}.")
        event("build", who, sq, s.houseCost)
        return true
    }

    private fun doSell(who: Int, sq: Int): Boolean {
        if (sq !in 0 until MonopolyBoard.SIZE) return false
        if (!MonopolyRules.canSell(owner, houses, who, sq, housesLeft())) return false
        val s = MonopolyBoard.squares[sq]
        cash[who] += s.houseCost / 2
        houses[sq]--
        say("${nameOf(who)} sells a building on ${s.name} for £${s.houseCost / 2}.")
        event("sell", who, sq, s.houseCost / 2)
        return true
    }

    private fun doMortgage(who: Int, sq: Int): Boolean {
        if (sq !in 0 until MonopolyBoard.SIZE) return false
        if (!MonopolyRules.canMortgage(owner, houses, mortgaged, who, sq)) return false
        mortgaged[sq] = true
        cash[who] += MonopolyBoard.squares[sq].mortgageValue
        say("${nameOf(who)} mortgages ${MonopolyBoard.squares[sq].name} for £${MonopolyBoard.squares[sq].mortgageValue}.")
        event("mortgage", who, sq, MonopolyBoard.squares[sq].mortgageValue)
        return true
    }

    private fun doUnmortgage(who: Int, sq: Int): Boolean {
        if (step == "DEBT" || sq !in 0 until MonopolyBoard.SIZE) return false
        if (!MonopolyRules.canUnmortgage(owner, mortgaged, who, sq, cash[who])) return false
        val cost = MonopolyRules.unmortgageCost(sq)
        cash[who] -= cost
        mortgaged[sq] = false
        say("${nameOf(who)} pays £$cost to lift the mortgage on ${MonopolyBoard.squares[sq].name}.")
        event("mortgage", who, sq, -cost)
        return true
    }

    // ---- debts and bankruptcy
    private fun doPayDebt(who: Int): Boolean {
        if (step != "DEBT" || cash[who] < debt) return false
        cash[who] -= debt
        if (debtTo != 0) cash[debtTo] += debt
        say("${nameOf(who)} pays the £$debt ($debtWhy).")
        debt = 0; debtTo = 0; debtWhy = ""
        if (resumeSteps > 0) {
            jailed[who] = false; jailTries[who] = 0
            val n = resumeSteps; resumeSteps = 0
            advance(who, n)
        } else {
            finishMove(who)
        }
        return true
    }

    private fun goBankrupt(who: Int) {
        val creditor = debtTo
        // buildings are sold back to the bank first
        for (i in 0 until MonopolyBoard.SIZE) if (owner[i] == who && houses[i] > 0) {
            cash[who] += houses[i] * (MonopolyBoard.squares[i].houseCost / 2)
            houses[i] = 0
        }
        if (creditor != 0) {
            cash[creditor] += cash[who]
            for (i in 0 until MonopolyBoard.SIZE) if (owner[i] == who) owner[i] = creditor
            freeCards[creditor].addAll(freeCards[who])
        } else {
            for (i in 0 until MonopolyBoard.SIZE) if (owner[i] == who) { owner[i] = 0; mortgaged[i] = false }
        }
        cash[who] = 0
        freeCards[who].clear()
        say("${nameOf(who)} is BANKRUPT! ${nameOf(3 - who)} wins!")
        event("bankrupt", who)
        over = true; winner = 3 - who; why = "bankrupt"; step = "OVER"
    }

    // ---- trading
    private fun tradeValid(t: TradeOffer): Boolean {
        val from = t.from
        val to = 3 - from
        if (t.giveCash < 0 || t.getCash < 0 || t.giveCash > cash[from] || t.getCash > cash[to]) return false
        if (t.giveSq.isEmpty() && t.getSq.isEmpty() && t.giveCash == 0 && t.getCash == 0) return false
        if (t.giveSq.size + t.getSq.size > 28) return false
        if (t.giveSq.toSet().size != t.giveSq.size || t.getSq.toSet().size != t.getSq.size) return false
        for (sq in t.giveSq) if (sq !in 0 until MonopolyBoard.SIZE || owner[sq] != from || !MonopolyBoard.squares[sq].ownable || groupHasBuildings(sq)) return false
        for (sq in t.getSq) if (sq !in 0 until MonopolyBoard.SIZE || owner[sq] != to || !MonopolyBoard.squares[sq].ownable || groupHasBuildings(sq)) return false
        // each side must also be able to pay the 10% interest on any mortgaged property they receive
        if (cash[from] - t.giveCash + t.getCash - MonopolyRules.tradeInterest(t.getSq, mortgaged) < 0) return false
        if (cash[to] + t.giveCash - t.getCash - MonopolyRules.tradeInterest(t.giveSq, mortgaged) < 0) return false
        return true
    }

    private fun groupHasBuildings(sq: Int): Boolean {
        val g = MonopolyBoard.squares[sq].group
        return g >= 0 && MonopolyBoard.GROUPS[g].any { houses[it] > 0 }
    }

    private fun doOffer(who: Int, a: JSONObject): Boolean {
        if ((step != "ROLL" && step != "END") || tradeOffer != null) return false
        val t = TradeOffer(who, a.ints("gs", 28), a.optInt("gc", 0), a.ints("ws", 28), a.optInt("wc", 0))
        if (!tradeValid(t)) return false
        tradeOffer = t
        say("${nameOf(who)} offers ${nameOf(3 - who)} a trade.")
        event("trade", who)
        return true
    }

    private fun respondTrade(who: Int, accept: Boolean) {
        val t = tradeOffer ?: return
        if (who == t.from) return
        tradeOffer = null
        if (!accept) {
            say("${nameOf(who)} turns down the trade.")
            return
        }
        if (!tradeValid(t)) { say("The trade is no longer possible."); return }
        val from = t.from
        val to = 3 - from
        val feeFrom = MonopolyRules.tradeInterest(t.getSq, mortgaged)
        val feeTo = MonopolyRules.tradeInterest(t.giveSq, mortgaged)
        cash[from] += t.getCash - t.giveCash - feeFrom
        cash[to] += t.giveCash - t.getCash - feeTo
        if (feeFrom + feeTo > 0) say("Mortgaged property changed hands, so the new owner pays the bank 10% interest (£$feeFrom and £$feeTo).")
        t.giveSq.forEach { owner[it] = to }
        t.getSq.forEach { owner[it] = from }
        say("Trade done! ${nameOf(from)} gets ${describe(t.getSq, t.getCash)}; ${nameOf(to)} gets ${describe(t.giveSq, t.giveCash)}.")
        event("trade", who)
    }

    private fun describe(sqs: List<Int>, cashAmt: Int): String {
        val parts = ArrayList<String>()
        sqs.forEach { parts += MonopolyBoard.squares[it].short }
        if (cashAmt > 0) parts += "£$cashAmt"
        return if (parts.isEmpty()) "nothing" else parts.joinToString(", ")
    }

    // ------------------------------------------------------------------ saving
    override fun saveModel(): JSONObject? {
        if (state.phase != GPhase.PLAYING || over || step == "FIRST") return null
        val o = JSONObject()
        o.put("lap", lapRule).put("pg", JSONArray(passedGo.map { if (it) 1 else 0 }))
        o.put("cash", SaveFormat.ints(cash)).put("pos", SaveFormat.ints(pos)).put("jt", SaveFormat.ints(jailTries))
            .put("jl", JSONArray(jailed.map { if (it) 1 else 0 }))
            .put("fc", JSONArray(freeCards.map { JSONArray(it) }))
            .put("own", SaveFormat.ints(owner)).put("hs", SaveFormat.ints(houses))
            .put("mt", JSONArray(mortgaged.map { if (it) 1 else 0 }))
            .put("chance", JSONArray(chance.toList())).put("chest", JSONArray(chest.toList()))
            .put("turn", turn).put("step", step).put("d1", d1).put("d2", d2).put("dbl", doubles).put("ne", noExtra)
            .put("log", JSONArray(log)).put("seq", seq).put("ev", ev).put("by", evBy).put("esq", evSq).put("eam", evAmount)
            .put("ct", cardText).put("cd", cardDeck)
            .put("debt", debt).put("dto", debtTo).put("dwhy", debtWhy).put("rs", resumeSteps)
            .put("as", aSq).put("ab", aBid).put("al", aLeader).put("at", aTurn).put("ap", aPasses)
            .put("max", maxTurns).put("tt", SaveFormat.ints(turnsTaken)).put("mv", moves)
        tradeOffer?.let {
            o.put("tf", it.from).put("tgs", JSONArray(it.giveSq)).put("tgc", it.giveCash).put("tws", JSONArray(it.getSq)).put("twc", it.getCash)
        }
        return o
    }

    override fun saveSummary(): String {
        val turn = (turnsTaken[1] + turnsTaken[2]) / 2 + 1
        return "Turn $turn · ${nameOf(1)} £${cash[1]} · ${nameOf(2)} £${cash[2]}" + if (maxTurns > 0) " · ${maxTurns}-turn game" else ""
    }

    override fun hostRestore(model: JSONObject): Boolean {
        val n = MonopolyBoard.SIZE
        val c = SaveFormat.readInts(model, "cash", 3, 0..10_000_000) ?: return false
        val p = SaveFormat.readInts(model, "pos", 3, 0..n - 1) ?: return false
        val jt = SaveFormat.readInts(model, "jt", 3, 0..3) ?: return false
        val jl = SaveFormat.readInts(model, "jl", 3, 0..1) ?: return false
        val own = SaveFormat.readInts(model, "own", n, 0..2) ?: return false
        val hs = SaveFormat.readInts(model, "hs", n, 0..5) ?: return false
        val mt = SaveFormat.readInts(model, "mt", n, 0..1) ?: return false
        val tt = SaveFormat.readInts(model, "tt", 3, 0..100_000) ?: return false
        val fcArr = model.optJSONArray("fc") ?: return false
        if (fcArr.length() != 3) return false
        val cards = List(3) { i -> val a = fcArr.optJSONArray(i) ?: JSONArray(); List(minOf(a.length(), 4)) { a.optInt(it, 0).coerceIn(0, 1) } }
        fun deck(key: String, size: Int): List<Int>? {
            val a = model.optJSONArray(key) ?: return null
            val l = List(a.length()) { a.optInt(it, -1) }
            return if (l.any { it !in 0 until size } || l.size > size) null else l
        }
        val ch = deck("chance", MonopolyCards.chance.size) ?: return false
        val cc = deck("chest", MonopolyCards.chest.size) ?: return false
        lapRule = model.optBoolean("lap", false)
        val pgArr = model.optJSONArray("pg")
        for (i in 0..2) passedGo[i] = pgArr == null || pgArr.optInt(i, 1) == 1
        firstRoll.fill(0); firstTie = false
        val turn0 = model.optInt("turn", 0)
        val step0 = model.optString("step", "")
        if (turn0 !in 1..2 || step0 !in listOf("ROLL", "BUY", "AUCTION", "DEBT", "END")) return false
        for (i in 0 until n) if (own[i] != 0 && !MonopolyBoard.squares[i].ownable) return false
        cash.fill(0); pos.fill(0); jailTries.fill(0); jailed.fill(false)
        for (i in 0..2) { cash[i] = c[i]; pos[i] = p[i]; jailTries[i] = jt[i]; jailed[i] = jl[i] == 1; turnsTaken[i] = tt[i]; freeCards[i].clear(); freeCards[i].addAll(cards[i]) }
        for (i in 0 until n) { owner[i] = own[i]; houses[i] = hs[i]; mortgaged[i] = mt[i] == 1 }
        chance = ArrayDeque(ch); chest = ArrayDeque(cc)
        turn = turn0; step = step0
        d1 = model.optInt("d1", 0).coerceIn(0, 6); d2 = model.optInt("d2", 0).coerceIn(0, 6)
        doubles = model.optInt("dbl", 0).coerceIn(0, 3); noExtra = model.optBoolean("ne", false)
        log.clear()
        model.optJSONArray("log")?.let { a -> for (i in 0 until minOf(a.length(), 14)) log.add(a.optString(i, "").filter { ch2 -> !ch2.isISOControl() }.take(160)) }
        seq = model.optInt("seq", 0).coerceIn(0, 1_000_000); ev = model.optString("ev", "").take(10)
        evBy = model.optInt("by", 0).coerceIn(0, 2); evSq = model.optInt("esq", -1).coerceIn(-1, n - 1); evAmount = model.optInt("eam", 0)
        cardText = model.optString("ct", "").filter { !it.isISOControl() }.take(160); cardDeck = model.optInt("cd", 0).coerceIn(0, 1)
        debt = model.optInt("debt", 0).coerceIn(0, 10_000_000); debtTo = model.optInt("dto", 0).coerceIn(0, 2)
        debtWhy = model.optString("dwhy", "").filter { !it.isISOControl() }.take(40); resumeSteps = model.optInt("rs", 0).coerceIn(0, 12)
        aSq = model.optInt("as", -1).coerceIn(-1, n - 1); aBid = model.optInt("ab", 0).coerceIn(0, 10_000_000)
        aLeader = model.optInt("al", 0).coerceIn(0, 2); aTurn = model.optInt("at", 0).coerceIn(0, 2); aPasses = model.optInt("ap", 0).coerceIn(0, 2)
        if (step == "AUCTION" && (aSq < 0 || aTurn == 0)) return false
        val tf = model.optInt("tf", 0)
        tradeOffer = if (tf in 1..2) TradeOffer(
            tf, model.ints("tgs", 28).filter { it in 0 until n }, model.optInt("tgc", 0).coerceIn(0, 10_000_000),
            model.ints("tws", 28).filter { it in 0 until n }, model.optInt("twc", 0).coerceIn(0, 10_000_000),
        ) else null
        maxTurns = model.optInt("max", 0).coerceIn(0, 200); moves = model.optInt("mv", 0).coerceIn(0, 1_000_000)
        over = false; winner = 0; why = ""
        forcedDice.clear()
        return true
    }

    // ------------------------------------------------------------------ sync
    override fun hostSnapshot(): JSONObject {
        val o = JSONObject()
        o.put("c1", cash[1]).put("c2", cash[2]).put("p1", pos[1]).put("p2", pos[2])
            .put("j1", if (jailed[1]) 1 + jailTries[1] else 0).put("j2", if (jailed[2]) 1 + jailTries[2] else 0)
            .put("f1", freeCards[1].size).put("f2", freeCards[2].size)
            .put("own", owner.joinToString("")).put("hs", houses.joinToString(""))
            .put("mt", String(CharArray(mortgaged.size) { if (mortgaged[it]) '1' else '0' }))
            .put("turn", turn).put("st", if (over) "OVER" else step).put("d1", d1).put("d2", d2).put("dbl", doubles)
            .put("log", JSONArray(log.takeLast(10)))
            .put("seq", seq).put("ev", ev).put("by", evBy).put("esq", evSq).put("eam", evAmount)
            .put("ct", cardText).put("cd", cardDeck)
            .put("debt", debt).put("dto", debtTo).put("dwhy", debtWhy)
            .put("as", aSq).put("ab", aBid).put("al", aLeader).put("at", aTurn)
            .put("max", maxTurns).put("t1", turnsTaken[1]).put("t2", turnsTaken[2])
            .put("mv", moves).put("over", over).put("win", winner).put("why", why)
            .put("fr1", firstRoll[1]).put("fr2", firstRoll[2]).put("ft", firstTie)
            .put("lap", lapRule).put("pg1", passedGo[1]).put("pg2", passedGo[2])
        tradeOffer?.let {
            o.put("tf", it.from).put("tgs", JSONArray(it.giveSq)).put("tgc", it.giveCash).put("tws", JSONArray(it.getSq)).put("twc", it.getCash)
        }
        return o
    }

    override fun adopt(s: JSONObject) {
        val ownS = s.getString("own")
        val hsS = s.getString("hs")
        val mtS = s.getString("mt")
        val n = MonopolyBoard.SIZE
        if (ownS.length != n || hsS.length != n || mtS.length != n) return
        if (ownS.any { it !in "012" } || hsS.any { it !in "012345" } || mtS.any { it !in "01" }) return
        val me = if (isHost) 1 else 2
        fun rel(v: Int) = if (v == 0) 0 else if (v == me) 1 else 2
        val over = s.getBoolean("over")
        val turn = s.getInt("turn")
        val houses = IntArray(n) { hsS[it] - '0' }
        val ownerAbs = IntArray(n) { ownS[it] - '0' }
        val mort = BooleanArray(n) { mtS[it] == '1' }
        val c1 = s.getInt("c1").coerceIn(0, 1_000_000)
        val c2 = s.getInt("c2").coerceIn(0, 1_000_000)
        val myC = if (isHost) c1 else c2
        val thC = if (isHost) c2 else c1
        val ownerRel = List(n) { rel(ownerAbs[it]) }
        val logArr = s.optJSONArray("log")
        val logList = if (logArr == null) emptyList() else List(minOf(logArr.length(), 14)) { logArr.optString(it, "").filter { c -> !c.isISOControl() }.take(160) }
        val tf = s.optInt("tf", 0)
        val trade = if (tf in 1..2) TradeOffer(
            rel(tf), s.ints("tgs", 28).filter { it in 0 until n }, s.optInt("tgc", 0).coerceIn(0, 1_000_000),
            s.ints("tws", 28).filter { it in 0 until n }, s.optInt("twc", 0).coerceIn(0, 1_000_000),
        ) else null
        val st = s.getString("st").take(8)
        val hl = MonopolyBoard.HOUSES - MonopolyRules.housesUsed(houses)
        val tl = MonopolyBoard.HOTELS - MonopolyRules.hotelsUsed(houses)
        val win = s.optInt("win", 0)
        val aTurnAbs = s.optInt("at", 0)
        val myWorth = MonopolyRules.netWorth(IntArray(n) { if (ownerAbs[it] == me) 1 else if (ownerAbs[it] == 0) 0 else 2 }, houses, mort, 1, myC)
        val thWorth = MonopolyRules.netWorth(IntArray(n) { if (ownerAbs[it] == me) 1 else if (ownerAbs[it] == 0) 0 else 2 }, houses, mort, 2, thC)
        state = state.copy(
            phase = if (over) GPhase.OVER else GPhase.PLAYING, iAmHost = isHost,
            myCash = myC, theirCash = thC,
            myPos = (if (isHost) s.getInt("p1") else s.getInt("p2")).coerceIn(0, n - 1),
            theirPos = (if (isHost) s.getInt("p2") else s.getInt("p1")).coerceIn(0, n - 1),
            myJail = (if (isHost) s.getInt("j1") else s.getInt("j2")) > 0,
            theirJail = (if (isHost) s.getInt("j2") else s.getInt("j1")) > 0,
            myJailTries = ((if (isHost) s.getInt("j1") else s.getInt("j2")) - 1).coerceIn(0, 3),
            myFreeCards = (if (isHost) s.getInt("f1") else s.getInt("f2")).coerceIn(0, 2),
            theirFreeCards = (if (isHost) s.getInt("f2") else s.getInt("f1")).coerceIn(0, 2),
            owner = ownerRel, houses = houses.toList(), mortgaged = mort.toList(),
            myTurn = !over && turn == me, step = st,
            d1 = s.optInt("d1", 0).coerceIn(0, 6), d2 = s.optInt("d2", 0).coerceIn(0, 6), doubles = s.optInt("dbl", 0).coerceIn(0, 3),
            housesLeft = hl, hotelsLeft = tl, log = logList,
            seq = s.optInt("seq", 0).coerceIn(0, 1_000_000), ev = s.optString("ev", "").take(10),
            evByMe = s.optInt("by", 0) == me, evSq = s.optInt("esq", -1).coerceIn(-1, n - 1), evAmount = s.optInt("eam", 0).coerceIn(-100_000, 100_000),
            cardText = s.optString("ct", "").filter { !it.isISOControl() }.take(160), cardDeck = s.optInt("cd", 0).coerceIn(0, 1),
            debt = s.optInt("debt", 0).coerceIn(0, 1_000_000), debtTo = rel(s.optInt("dto", 0).coerceIn(0, 2)),
            debtWhy = s.optString("dwhy", "").filter { !it.isISOControl() }.take(40),
            auctionSq = s.optInt("as", -1).coerceIn(-1, n - 1), auctionBid = s.optInt("ab", 0).coerceIn(0, 1_000_000),
            auctionLeader = rel(s.optInt("al", 0).coerceIn(0, 2)), myBidTurn = st == "AUCTION" && aTurnAbs == me,
            trade = trade,
            maxTurns = s.optInt("max", 0).coerceIn(0, 200),
            myTurnsTaken = (if (isHost) s.getInt("t1") else s.getInt("t2")).coerceIn(0, 100_000),
            theirTurnsTaken = (if (isHost) s.getInt("t2") else s.getInt("t1")).coerceIn(0, 100_000),
            firstMine = s.optInt(if (isHost) "fr1" else "fr2", 0).coerceIn(0, 12), firstTheirs = s.optInt(if (isHost) "fr2" else "fr1", 0).coerceIn(0, 12),
            firstTie = s.optBoolean("ft", false), lapRule = s.optBoolean("lap", false),
            myPassedGo = s.optBoolean(if (isHost) "pg1" else "pg2", true), theirPassedGo = s.optBoolean(if (isHost) "pg2" else "pg1", true),
            myWorth = myWorth, theirWorth = thWorth,
            moves = s.optInt("mv", 0).coerceIn(0, 1_000_000),
            result = if (!over) 0 else if (win == 3) 3 else if (win == me) 1 else 2,
            why = s.optString("why", "").take(8),
        )
    }

    override fun reset() {
        state = MonopolyState(myName, theirName, iAmHost = isHost)
    }
}
