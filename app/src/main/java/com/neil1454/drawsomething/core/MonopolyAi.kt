package com.neil1454.drawsomething.core

import kotlin.random.Random

// ====================================================================================
// The computer's Monopoly player. It buys sensibly, bids at auctions, builds on complete sets, raises cash when it
// owes money, uses jail cards, and judges trade offers (it never proposes trades itself).
// ====================================================================================

class MonopolyBrain(private val e: MonopolyEngine, skill: Skill, random: Random) : Brain(skill, random) {

    override fun think(now: Long) {
        val s = e.state
        if (s.phase != GPhase.PLAYING) return

        val t = s.trade
        if (t != null && t.from == 2) {
            if (ready(now, "trade${s.seq}", 1500, 3000)) {
                if (MonopolyAi.acceptTrade(s, skill)) e.acceptTrade() else e.declineTrade()
            }
            return
        }
        if (s.step == "AUCTION") {
            if (s.myBidTurn && ready(now, "auction${s.auctionSq}${s.auctionBid}${s.auctionLeader}", 900, 1800)) {
                val max = MonopolyAi.maxBid(s, skill)
                val next = if (s.auctionBid == 0) 10 else s.auctionBid + maxOf(10, (MonopolyBoard.squares[s.auctionSq].price / 12) / 10 * 10)
                if (s.auctionLeader != 1 && next <= minOf(max, s.myCash)) e.bid(next)
                else if (s.auctionLeader != 1 && s.auctionBid + 10 <= minOf(max, s.myCash)) e.bid(s.auctionBid + 10)
                else e.passBid()
            }
            return
        }
        if (!s.myTurn) return
        val key = "${s.step}${s.seq}${s.myCash}${s.moves}${s.houses.sum()}${s.mortgaged.count { it }}"
        when (s.step) {
            "ROLL" -> if (ready(now, key, 900, 1800)) {
                if (s.myJail) {
                    when {
                        s.myFreeCards > 0 -> { e.useJailCard(); return }
                        s.myCash > 600 && s.myJailTries == 0 && MonopolyAi.ownedCount(s) >= 3 -> { e.payJail(); return }
                    }
                }
                val m = MonopolyAi.manage(s, skill)
                if (m != null) m.apply(e) else e.roll()
            }
            "BUY" -> if (ready(now, key, 1000, 2000)) {
                if (MonopolyAi.shouldBuy(s, skill)) e.buy() else e.declineToAuction()
            }
            "DEBT" -> if (ready(now, key, 800, 1500)) {
                if (s.myCash >= s.debt) { e.payDebt(); return }
                val m = MonopolyAi.raiseCash(s)
                if (m != null) m.apply(e) else e.declareBankrupt()
            }
            "END" -> if (ready(now, key, 800, 1500)) {
                val m = MonopolyAi.manage(s, skill)
                if (m != null) m.apply(e) else e.endTurn()
            }
        }
    }
}

object MonopolyAi {
    class Act(private val kind: String, private val sq: Int) {
        fun apply(e: MonopolyEngine) {
            when (kind) {
                "build" -> e.build(sq)
                "sell" -> e.sellHouse(sq)
                "mortgage" -> e.mortgage(sq)
                "unmortgage" -> e.unmortgage(sq)
            }
        }
    }

    fun ownedCount(s: MonopolyState) = s.owner.count { it == 1 }

    private fun reserve(s: MonopolyState, skill: Skill): Int {
        // keep a cushion against rent; the more the other player owns, the bigger it needs to be
        val threat = s.owner.indices.count { s.owner[it] == 2 && s.houses[it] > 0 } * 40
        return (when (skill) { Skill.EASY -> 120; Skill.NORMAL -> 200; Skill.HARD -> 150 }) + threat
    }

    /** Would owning [sq] complete one of my colour sets (or stop the other player completing theirs)? */
    private fun setValue(s: MonopolyState, sq: Int): Int {
        val sqr = MonopolyBoard.squares[sq]
        if (sqr.group < 0) return 0
        val grp = MonopolyBoard.GROUPS[sq.let { sqr.group }]
        val mine = grp.count { s.owner[it] == 1 }
        val theirs = grp.count { s.owner[it] == 2 }
        return when {
            mine == grp.size - 1 && theirs == 0 -> 2 // completes my set
            theirs == grp.size - 1 && mine == 0 -> 1 // blocks theirs
            else -> 0
        }
    }

    fun shouldBuy(s: MonopolyState, skill: Skill): Boolean {
        val sq = s.myPos
        val price = MonopolyBoard.squares[sq].price
        if (s.myCash < price) return false
        val sv = setValue(s, sq)
        if (sv > 0) return s.myCash - price >= 0
        val kind = MonopolyBoard.squares[sq].kind
        val bonus = if (kind == SqKind.STATION) 80 else 0
        return s.myCash - price >= reserve(s, skill) - bonus
    }

    fun maxBid(s: MonopolyState, skill: Skill): Int {
        val sq = s.auctionSq
        val p = MonopolyBoard.squares[sq].price
        var v = when (skill) { Skill.EASY -> p * 0.7; Skill.NORMAL -> p * 0.95; Skill.HARD -> p * 1.1 }
        v += when (setValue(s, sq)) { 2 -> p * 0.6; 1 -> p * 0.3; else -> 0.0 }
        return minOf(v.toInt(), s.myCash - 20).coerceAtLeast(0)
    }

    /** One tidy-up action (lift a mortgage or build), or null if there is nothing sensible to do. */
    fun manage(s: MonopolyState, skill: Skill): Act? {
        val owner = s.ownerArr; val houses = s.housesArr; val mort = s.mortArr
        val res = reserve(s, skill)
        // lift mortgages when comfortably rich
        val lift = (0 until MonopolyBoard.SIZE).filter { MonopolyRules.canUnmortgage(owner, mort, 1, it, s.myCash) && s.myCash - MonopolyRules.unmortgageCost(it) >= res + 100 }
            .maxByOrNull { MonopolyBoard.squares[it].price }
        if (lift != null) return Act("unmortgage", lift)
        // build on the best set we can afford, keeping a cushion
        val order = listOf(3, 4, 2, 1, 5, 6, 7, 0)
        for (g in order) {
            val sq = MonopolyBoard.GROUPS[g].filter {
                MonopolyRules.canBuild(owner, houses, mort, 1, it, s.myCash, s.housesLeft, s.hotelsLeft) &&
                    s.myCash - MonopolyBoard.squares[it].houseCost >= res
            }.minByOrNull { houses[it] }
            if (sq != null) return Act("build", sq)
        }
        return null
    }

    /** Something to sell or mortgage to find cash for a debt, or null if nothing is left. */
    fun raiseCash(s: MonopolyState): Act? {
        val owner = s.ownerArr; val houses = s.housesArr; val mort = s.mortArr
        val sell = (0 until MonopolyBoard.SIZE).filter { MonopolyRules.canSell(owner, houses, 1, it, s.housesLeft) }
            .maxByOrNull { houses[it] * 10 + MonopolyBoard.squares[it].houseCost }
        // mortgage the least useful property first: not part of a set we're building, cheapest first
        val mortgage = (0 until MonopolyBoard.SIZE).filter { MonopolyRules.canMortgage(owner, houses, mort, 1, it) }
            .sortedWith(compareBy({ MonopolyBoard.squares[it].group >= 0 && MonopolyBoard.ownsGroup(owner, 1, MonopolyBoard.squares[it].group) }, { MonopolyBoard.squares[it].price }))
            .firstOrNull()
        return when {
            mortgage != null && houses.all { it == 0 } -> Act("mortgage", mortgage)
            sell != null -> Act("sell", sell)
            mortgage != null -> Act("mortgage", mortgage)
            else -> null
        }
    }

    private fun worth(s: MonopolyState, sq: Int, forMe: Boolean): Double {
        val p = MonopolyBoard.squares[sq].price.toDouble()
        val sv = if (forMe) setValue(s, sq) else 0
        return p * (1.0 + (if (sv == 2) 0.8 else if (sv == 1) 0.2 else 0.0)) * (if (s.mortgaged[sq]) 0.6 else 1.0)
    }

    /** Take a deal if what I get is worth clearly more than what I give, and it doesn't hand over a set cheaply. */
    fun acceptTrade(s: MonopolyState, skill: Skill): Boolean {
        val t = s.trade ?: return false
        // the offerer is the other player: they give t.giveSq/t.giveCash to me and want t.getSq/t.getCash from me
        if (t.getCash > s.myCash) return false
        var gain = t.giveCash.toDouble()
        var give = t.getCash.toDouble()
        t.giveSq.forEach { gain += worth(s, it, true) }
        var handsOverSet = false
        t.getSq.forEach { sq ->
            give += worth(s, sq, false)
            val g = MonopolyBoard.squares[sq].group
            if (g >= 0) {
                val grp = MonopolyBoard.GROUPS[g]
                val theirs = grp.count { s.owner[it] == 2 } + t.giveSq.count { it in grp }
                if (theirs + 1 == grp.size && t.giveSq.none { it in grp } ) handsOverSet = true
            }
        }
        // would the deal complete one of THEIR sets with the squares I hand over?
        for (g in 0..7) {
            val grp = MonopolyBoard.GROUPS[g]
            val after = grp.count { (s.owner[it] == 2 && it !in t.giveSq) || it in t.getSq }
            val before = grp.count { s.owner[it] == 2 }
            if (after == grp.size && before < grp.size) handsOverSet = true
        }
        // and one of MINE completes
        var completesMine = false
        for (g in 0..7) {
            val grp = MonopolyBoard.GROUPS[g]
            val after = grp.count { (s.owner[it] == 1 && it !in t.getSq) || it in t.giveSq }
            val before = grp.count { s.owner[it] == 1 }
            if (after == grp.size && before < grp.size) completesMine = true
        }
        val margin = when (skill) { Skill.EASY -> 0.9; Skill.NORMAL -> 1.15; Skill.HARD -> 1.3 }
        val need = if (handsOverSet && !completesMine) give * margin + 200 else give * margin
        return gain >= need
    }
}
