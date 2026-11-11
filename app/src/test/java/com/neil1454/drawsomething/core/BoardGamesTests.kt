package com.neil1454.drawsomething.core

import org.json.JSONObject
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertTrue
import org.junit.Test
import kotlin.random.Random

// ====================================================================================== Draughts

class DraughtsRulesTest {
    private fun grid(vararg pieces: Pair<Int, Int>): IntArray {
        val g = IntArray(64)
        for ((i, v) in pieces) g[i] = v
        return g
    }

    private fun sq(x: Int, y: Int) = y * 8 + x

    @Test fun startingPositionHasTwelveEachAndSevenOpeningMoves() {
        val g = Draughts.start()
        assertEquals(12, Draughts.count(g, 1))
        assertEquals(12, Draughts.count(g, 2))
        assertEquals(7, Draughts.moves(g, 1).size)
        assertTrue(g.indices.all { g[it] == 0 || Draughts.isDark(it) })
    }

    @Test fun capturesAreCompulsory() {
        // host man at (3,4) can jump the guest man at (4,3) -> lands (5,2); another host man could make a plain step but may not
        val g = grid(sq(3, 4) to Draughts.HOST_MAN, sq(4, 3) to Draughts.GUEST_MAN, sq(0, 7) to Draughts.HOST_MAN, sq(7, 0) to Draughts.GUEST_MAN)
        val ms = Draughts.moves(g, 1)
        assertTrue(ms.isNotEmpty())
        assertTrue("only jumps are legal", ms.all { it.cap >= 0 })
        assertEquals(sq(5, 2), ms[0].to)
    }

    @Test fun menCannotJumpOrMoveBackwardsButKingsCan() {
        val man = grid(sq(3, 4) to Draughts.HOST_MAN, sq(2, 5) to Draughts.GUEST_MAN, sq(1, 6) to 0)
        assertTrue("a man can't capture backwards", Draughts.moves(man, 1).none { it.cap >= 0 })
        val king = grid(sq(3, 4) to Draughts.HOST_KING, sq(2, 5) to Draughts.GUEST_MAN)
        assertTrue("a king can", Draughts.moves(king, 1).any { it.cap == sq(2, 5) && it.to == sq(1, 6) })
    }

    @Test fun multipleJumpsMustBeContinuedByTheSamePiece() {
        val g = grid(sq(1, 6) to Draughts.HOST_MAN, sq(2, 5) to Draughts.GUEST_MAN, sq(4, 3) to Draughts.GUEST_MAN, sq(7, 0) to Draughts.GUEST_MAN)
        val first = Draughts.moves(g, 1).single()
        val r = Draughts.apply(g, 1, first)
        assertTrue("another jump is available", r.continues)
        val next = Draughts.moves(r.grid, 1, first.to)
        assertEquals(1, next.size)
        assertEquals(sq(5, 2), next[0].to)
        val r2 = Draughts.apply(r.grid, 1, next[0])
        assertFalse(r2.continues)
        assertEquals(1, Draughts.count(r2.grid, 2))
    }

    @Test fun reachingTheFarRowCrownsAndEndsTheTurn() {
        val g = grid(sq(2, 1) to Draughts.HOST_MAN, sq(3, 0) to Draughts.GUEST_MAN, sq(5, 2) to Draughts.GUEST_MAN, sq(7, 6) to Draughts.GUEST_MAN)
        val step = Draughts.moves(g, 1).first { it.to == sq(1, 0) }
        val r = Draughts.apply(g, 1, step)
        assertTrue(r.promoted)
        assertEquals(Draughts.HOST_KING, r.grid[sq(1, 0)])
        // a jump that lands on the far row also crowns and does not continue even if more jumps exist
        val g2 = grid(sq(2, 2) to Draughts.HOST_MAN, sq(3, 1) to Draughts.GUEST_MAN, sq(5, 1) to Draughts.GUEST_MAN, sq(7, 7) to Draughts.GUEST_MAN)
        val jump = Draughts.moves(g2, 1).single()
        val r2 = Draughts.apply(g2, 1, jump)
        assertTrue(r2.promoted)
        assertFalse(r2.continues)
    }

    @Test fun encodeAndDecodeAreInverse() {
        val g = Draughts.start()
        g[sq(1, 4)] = Draughts.GUEST_KING
        assertTrue(Draughts.decode(Draughts.encode(g))!!.contentEquals(g))
        assertEquals(null, Draughts.decode("nonsense"))
    }
}

class DraughtsEngineTest {
    private fun pair(): SyncPair<DraughtsEngine> {
        val p = SyncPair({ n, o, h, s, r, c -> DraughtsEngine(n, o, h, s, r, c) })
        p.host.start()
        p.pump()
        return p
    }

    @Test fun hostMovesFirstAndBothPhonesAgree() {
        val p = pair()
        assertTrue(p.host.state.myTurn)
        assertFalse(p.client.state.myTurn)
        assertEquals(p.host.state.grid, p.client.state.grid)
        assertEquals(7, p.host.state.legal.size)
        assertTrue(p.client.state.legal.isEmpty())
    }

    @Test fun illegalAndOutOfTurnMovesAreIgnored() {
        val p = pair()
        val before = p.host.state.grid
        p.client.move(2 * 8 + 1, 3 * 8 + 0); p.pump() // not their turn
        p.host.move(0, 63); p.pump()                 // nonsense
        assertEquals(before, p.host.state.grid)
        val first = p.host.state.legal.first()
        p.host.move(first / 64, first % 64); p.pump()
        assertTrue(p.client.state.myTurn)
        assertTrue(p.client.state.legal.isNotEmpty())
        assertEquals(1, p.host.state.moves)
    }

    @Test fun theComputerPlaysAWholeGameAgainstItself() {
        for (seed in 1..3) {
            val d = Duel(seed, Skill.NORMAL, Skill.EASY)
            d.host.draughts.start()
            var guard = 0
            while (d.host.draughts.state.phase != GPhase.OVER && guard++ < 6000) d.advance(250)
            val s = d.host.draughts.state
            assertEquals("seed $seed should finish", GPhase.OVER, s.phase)
            assertTrue(s.result in 1..3)
            assertTrue(s.moves > 10)
        }
    }

    @Test fun aStrongPlayerBeatsAWeakOne() {
        var strongWins = 0
        for (seed in 1..4) {
            val d = Duel(seed, Skill.HARD, Skill.EASY)
            d.host.draughts.start()
            var guard = 0
            while (d.host.draughts.state.phase != GPhase.OVER && guard++ < 8000) d.advance(250)
            if (d.host.draughts.state.result == 1) strongWins++
        }
        assertTrue("hard should win most games against easy (won $strongWins of 4)", strongWins >= 3)
    }
}

// ====================================================================================== Ludo

class LudoRulesTest {
    @Test fun theTrackIsAClosedLoopOf52SquaresAndColumnsDontOverlap() {
        assertEquals(52, Ludo.RING_XY.size)
        assertEquals(52, Ludo.RING_XY.toSet().size)
        val cells = HashSet<Pair<Int, Int>>(Ludo.RING_XY)
        for (who in 1..2) for (p in 51..56) {
            val xy = Ludo.xy(who, p)!!
            assertTrue("column cell $xy is not on the ring", xy !in cells || p == Ludo.HOME && false)
        }
        assertEquals(Ludo.RING_XY[0], Ludo.xy(1, 0))
        assertEquals(Ludo.RING_XY[26], Ludo.xy(2, 0))
    }

    @Test fun youNeedASixToLeaveBaseAndAnExactRollToFinish() {
        val mine = intArrayOf(-1, -1, 54, 56)
        val theirs = IntArray(4) { -1 }
        assertEquals(emptyList<Int>(), Ludo.movable(mine, theirs, 1, 3).filter { it < 2 })
        assertEquals(listOf(0, 1), Ludo.movable(mine, theirs, 1, 6).filter { it < 2 })
        assertTrue("2 takes the piece on 54 exactly home", 2 in Ludo.movable(mine, theirs, 1, 2))
        assertFalse("3 would overshoot", 2 in Ludo.movable(mine, theirs, 1, 3))
        assertFalse("a finished piece never moves", 3 in Ludo.movable(mine, theirs, 1, 1))
    }

    @Test fun landingOnALonePieceSendsItBackUnlessTheSquareIsSafe() {
        val mine = intArrayOf(3, -1, -1, -1)
        val theirs = intArrayOf(-1, -1, -1, -1)
        // guest piece with progress p sits on ring (26 + p) % 52; host piece at progress 7 is on ring 7
        theirs[0] = 7 - 26 + 52 // ring 7 for the guest = progress 33
        val r = Ludo.move(mine, theirs, 1, 0, 4)
        assertEquals(7, mine[0])
        assertEquals(1, r.captured)
        assertEquals(Ludo.BASE, theirs[0])
        // ring 8 is a star: safe
        val mine2 = intArrayOf(4, -1, -1, -1)
        val theirs2 = intArrayOf(8 - 26 + 52, -1, -1, -1)
        val r2 = Ludo.move(mine2, theirs2, 1, 0, 4)
        assertEquals(0, r2.captured)
        assertEquals(34, theirs2[0])
    }
}

class LudoEngineTest {
    private fun pair(): SyncPair<LudoEngine> {
        val p = SyncPair({ n, o, h, s, r, c -> LudoEngine(n, o, h, s, r, c) })
        p.host.start()
        p.pump()
        return p
    }

    @Test fun aSixBringsAPieceOutAndGivesAnotherGo() {
        val p = pair()
        p.host.forcedRolls.addAll(listOf(6, 2))
        p.host.roll(); p.pump()
        assertEquals(6, p.host.state.die)
        assertEquals(listOf(0, 1, 2, 3), p.host.state.legal)
        p.host.movePiece(0); p.pump()
        assertEquals(0, p.host.state.mine[0])
        assertEquals("a 6 means the same player rolls again", true, p.host.state.canRoll)
        assertTrue(p.client.state.theirs[0] == 0)
    }

    @Test fun noMovePassesTheTurnAfterAShortPause() {
        val p = pair()
        p.host.forcedRolls.add(3)
        p.host.roll(); p.pump()
        assertEquals("stuck", p.host.state.event)
        assertTrue(p.host.state.legal.isEmpty())
        p.advance(2000)
        assertTrue("now it's the guest's go", p.client.state.canRoll)
    }

    @Test fun rollingTooMuchToFinishLeavesThePieceWhereItIs() {
        val p = pair()
        // get piece 0 out then walk it to 54 by hand using forced rolls: 6 out, then 6,6,6,6,6,6,6,6,6 would give extra turns; use small steps instead
        p.host.forcedRolls.addAll(listOf(6, 5)) // out, then 5 (turn passes)
        p.host.roll(); p.pump(); p.host.movePiece(0); p.pump()
        p.host.roll(); p.pump(); p.host.movePiece(0); p.pump()
        assertEquals(5, p.host.state.mine[0])
        // jump straight to near the end via the pure rule instead
        val mine = intArrayOf(55, -1, -1, -1)
        assertTrue(0 in Ludo.movable(mine, IntArray(4) { -1 }, 1, 1))
        assertFalse(0 in Ludo.movable(mine, IntArray(4) { -1 }, 1, 2))
    }

    @Test fun threeSixesInARowLoseTheTurn() {
        val p = pair()
        p.host.forcedRolls.addAll(listOf(6, 6, 6))
        p.host.roll(); p.pump(); p.host.movePiece(0); p.pump()
        p.host.roll(); p.pump(); p.host.movePiece(1); p.pump()
        p.host.roll(); p.pump()
        assertEquals("triple", p.host.state.event)
        p.advance(2000)
        assertTrue(p.client.state.canRoll)
    }

    @Test fun theComputerPlaysAWholeGame() {
        for (seed in 1..3) {
            val d = Duel(seed, Skill.NORMAL, Skill.NORMAL)
            d.host.ludo.start()
            var guard = 0
            while (d.host.ludo.state.phase != GPhase.OVER && guard++ < 20000) d.advance(250)
            val s = d.host.ludo.state
            assertEquals("seed $seed should finish", GPhase.OVER, s.phase)
            assertTrue(s.result == 1 || s.result == 2)
            assertEquals(4, maxOf(s.myHome, s.theirHome))
        }
    }
}

// ====================================================================================== Monopoly

class MonopolyBoardTest {
    @Test fun theBoardMatchesTheUkEdition() {
        val b = MonopolyBoard.squares
        assertEquals(40, b.size)
        assertEquals("GO", b[0].name)
        assertEquals("Old Kent Road", b[1].name)
        assertEquals("Mayfair", b[39].name)
        assertEquals(400, b[39].price)
        assertEquals(28, b.count { it.ownable })
        assertEquals(22, b.count { it.kind == SqKind.PROPERTY })
        assertEquals(4, b.count { it.kind == SqKind.STATION })
        assertEquals(2, b.count { it.kind == SqKind.UTILITY })
        assertEquals(listOf(2, 3, 3, 3, 3, 3, 3, 2), MonopolyBoard.GROUPS.map { it.size })
        assertEquals(3, b.count { it.kind == SqKind.CHANCE })
        assertEquals(3, b.count { it.kind == SqKind.CHEST })
        assertEquals(200, b[4].rents[0])   // income tax
        assertEquals(100, b[38].rents[0])  // super tax
        assertEquals(10, MonopolyBoard.JAIL)
        assertEquals(MonopolyBoard.JAIL, b.indexOfFirst { it.kind == SqKind.JAIL })
        assertEquals(30, b.indexOfFirst { it.kind == SqKind.GOTOJAIL })
        // every street has six rent levels, and rents rise
        for (s in b.filter { it.kind == SqKind.PROPERTY }) {
            assertEquals(6, s.rents.size)
            assertTrue(s.rents.toList() == s.rents.sorted())
            assertTrue(s.houseCost > 0)
        }
    }

    @Test fun thereAreSixteenCardsInEachPile() {
        assertEquals(16, MonopolyCards.chance.size)
        assertEquals(16, MonopolyCards.chest.size)
        assertEquals(1, MonopolyCards.chance.count { it.type == "free" })
        assertEquals(1, MonopolyCards.chest.count { it.type == "free" })
    }

    @Test fun rentRules() {
        val owner = IntArray(40); val houses = IntArray(40); val mort = BooleanArray(40)
        owner[1] = 1
        assertEquals(2, MonopolyRules.rent(owner, houses, mort, 1, 7))
        owner[3] = 1
        assertEquals("a full unbuilt set pays double", 4, MonopolyRules.rent(owner, houses, mort, 1, 7))
        houses[1] = 2
        assertEquals(30, MonopolyRules.rent(owner, houses, mort, 1, 7))
        mort[1] = true
        assertEquals(0, MonopolyRules.rent(owner, houses, mort, 1, 7))
        // stations
        owner[5] = 2; assertEquals(25, MonopolyRules.rent(owner, houses, mort, 5, 7))
        owner[15] = 2; owner[25] = 2; assertEquals(100, MonopolyRules.rent(owner, houses, mort, 5, 7))
        assertEquals(200, MonopolyRules.rent(owner, houses, mort, 5, 7, stationDouble = true))
        // utilities
        owner[12] = 1; assertEquals(28, MonopolyRules.rent(owner, houses, mort, 12, 7))
        owner[28] = 1; assertEquals(70, MonopolyRules.rent(owner, houses, mort, 12, 7))
    }

    @Test fun buildingMustBeEvenAndNeedsAFullSet() {
        val owner = IntArray(40); val houses = IntArray(40); val mort = BooleanArray(40)
        owner[1] = 1
        assertFalse(MonopolyRules.canBuild(owner, houses, mort, 1, 1, 1000, 32, 12))
        owner[3] = 1
        assertTrue(MonopolyRules.canBuild(owner, houses, mort, 1, 1, 1000, 32, 12))
        houses[1] = 1
        assertFalse("must build evenly", MonopolyRules.canBuild(owner, houses, mort, 1, 1, 1000, 32, 12))
        assertTrue(MonopolyRules.canBuild(owner, houses, mort, 1, 3, 1000, 32, 12))
        assertFalse("not enough cash", MonopolyRules.canBuild(owner, houses, mort, 1, 3, 10, 32, 12))
        assertFalse("no houses left", MonopolyRules.canBuild(owner, houses, mort, 1, 3, 1000, 0, 12))
        assertFalse("can't sell from the lighter street", MonopolyRules.canSell(owner, houses, 1, 3, 31))
        assertTrue(MonopolyRules.canSell(owner, houses, 1, 1, 31))
        assertFalse("no mortgage with buildings in the set", MonopolyRules.canMortgage(owner, houses, mort, 1, 3))
        assertEquals(33, MonopolyRules.unmortgageCost(1) .let { it }) // 30 + 10% = 33
    }
}

class MonopolyEngineTest {
    private fun pair(len: Int = 0, seed: Int = 1, lap: Boolean = false, hostFirst: Boolean = true): SyncPair<MonopolyEngine> {
        val p = SyncPair({ n, o, h, s, r, c -> MonopolyEngine(n, o, h, s, r, c) }, seed)
        p.host.start(JSONObject().put("len", len).put("lap", lap))
        p.pump()
        // the opening roll: highest total goes first
        p.host.forcedDice.add((if (hostFirst) 6 else 1) to (if (hostFirst) 5 else 2)); p.host.roll(); p.pump()
        p.host.forcedDice.add((if (hostFirst) 1 else 6) to (if (hostFirst) 2 else 5)); p.client.roll(); p.pump()
        return p
    }

    /** The player whose go it is rolls the given dice. */
    private fun SyncPair<MonopolyEngine>.roll(a: Int, b: Int) {
        host.forcedDice.add(a to b)
        (if (host.state.myTurn) host else client).roll(); pump()
    }

    private val SyncPair<MonopolyEngine>.me get() = if (host.state.myTurn) host else client
    private val SyncPair<MonopolyEngine>.s get() = host.state

    /** Both bid-or-pass so that nobody buys. */
    private fun SyncPair<MonopolyEngine>.nobodyWants() {
        me.declineToAuction(); pump()
        (if (host.state.myBidTurn) host else client).passBid(); pump()
        (if (host.state.myBidTurn) host else client).passBid(); pump()
    }

    @Test fun everyoneStartsWith1500AndTheHostGoesFirst() {
        val p = pair()
        assertEquals(1500, p.host.state.myCash)
        assertEquals(1500, p.client.state.myCash)
        assertTrue(p.host.state.canRoll)
        assertFalse(p.client.state.canRoll)
        assertEquals(40, p.client.state.owner.size)
    }

    @Test fun theHighestOpeningRollGoesFirst() {
        val a = pair(hostFirst = true)
        assertTrue(a.host.state.canRoll)
        assertFalse(a.client.state.canRoll)
        val b = pair(hostFirst = false)
        assertTrue(b.client.state.canRoll)
        assertFalse(b.host.state.canRoll)
        assertEquals("ROLL", b.host.state.step)
    }

    @Test fun aTiedOpeningRollIsRolledAgain() {
        val p = SyncPair({ n, o, h, s, r, c -> MonopolyEngine(n, o, h, s, r, c) }, 1)
        p.host.start(JSONObject().put("lap", false)); p.pump()
        assertEquals("FIRST", p.host.state.step)
        assertTrue(p.host.state.canFirstRoll && p.client.state.canFirstRoll)
        p.host.forcedDice.add(3 to 4); p.host.roll(); p.pump()
        assertFalse("you only roll once", p.host.state.canFirstRoll)
        assertEquals(7, p.client.state.firstTheirs)
        p.host.forcedDice.add(2 to 5); p.client.roll(); p.pump()
        assertTrue(p.host.state.firstTie)
        assertEquals("FIRST", p.host.state.step)
        assertTrue("both can roll again", p.host.state.canFirstRoll)
        p.host.forcedDice.add(6 to 6); p.host.roll(); p.pump()
        p.host.forcedDice.add(1 to 1); p.client.roll(); p.pump()
        assertEquals("ROLL", p.host.state.step)
        assertTrue(p.host.state.canRoll)
    }

    @Test fun theGoRoundOnceHouseRuleStopsBuyingUntilYouPassGo() {
        val p = pair(lap = true)
        p.roll(1, 2) // square 3, unowned
        assertEquals("END", p.s.step)
        assertEquals(0, p.s.owner[3])
        assertFalse(p.s.myPassedGo)
        p.host.endTurn(); p.pump()
        p.guestIdles(); 
        p.host.debugSetPos(1, 38)
        p.roll(1, 2) // passes GO and lands on Old Kent Road
        assertTrue(p.s.myPassedGo)
        assertEquals("BUY", p.s.step)
        // rent is still due before you have passed GO
    }

    @Test fun withoutTheHouseRuleYouMayBuyFromTheFirstGo() {
        val p = pair(lap = false)
        p.roll(1, 2)
        assertEquals("BUY", p.s.step)
    }

    @Test fun mortgagedPropertyInATradeCostsTheReceiverTenPercentInterest() {
        val p = pair()
        p.roll(1, 2); p.host.buy(); p.pump()
        p.host.mortgage(3); p.pump()
        val hostCash = p.host.state.myCash
        val guestCash = p.client.state.myCash
        p.host.offerTrade(listOf(3), 0, emptyList(), 0); p.pump()
        p.client.acceptTrade(); p.pump()
        assertEquals(2, p.host.state.owner[3].let { if (it == 2) 2 else it })
        assertEquals(guestCash - 3, p.client.state.myCash)
        assertEquals(hostCash, p.host.state.myCash)
        assertTrue(p.client.state.mortgaged[3])
    }

    @Test fun anyAmountFromOnePoundCanBeBidAtAuction() {
        val p = pair()
        p.roll(1, 2)
        p.host.declineToAuction(); p.pump()
        p.client.bid(1); p.pump()
        assertEquals(1, p.host.state.auctionBid)
        p.host.bid(2); p.pump()
        p.client.passBid(); p.pump()
        assertTrue(p.host.state.owns(3))
        assertEquals(1498 + 0, p.host.state.myCash.let { it })
    }

    @Test fun landingOnAnUnownedStreetOffersItAndBuyingChargesTheCash() {
        val p = pair()
        p.roll(1, 2) // 3 -> Whitechapel Road £60
        assertEquals("BUY", p.s.step)
        assertEquals(3, p.s.myPos)
        p.host.buy(); p.pump()
        assertEquals(1440, p.s.myCash)
        assertTrue(p.s.owns(3))
        assertEquals(2, p.client.state.owner[3])
        assertEquals("END", p.s.step)
        p.host.endTurn(); p.pump()
        assertTrue(p.client.state.canRoll)
    }

    @Test fun rentIsPaidToTheOwner() {
        val p = pair()
        p.roll(1, 2); p.host.buy(); p.pump(); p.host.endTurn(); p.pump()
        p.roll(1, 2) // the guest lands on square 3 too
        assertEquals(1500 - 4, p.client.state.myCash)
        assertEquals(1440 + 4, p.host.state.myCash)
        assertEquals("rent", p.client.state.ev)
    }

    @Test fun passingGoPays200() {
        val p = pair()
        p.host.debugSetPos(1, 38)
        p.roll(1, 2) // 38 + 3 = square 1, passing GO
        assertEquals(1, p.s.myPos)
        assertEquals(1700, p.s.myCash)
    }

    @Test fun declinedStreetsGoToAuctionAndTheLeaderPays() {
        val p = pair()
        p.roll(1, 2)
        p.host.declineToAuction(); p.pump()
        assertEquals("AUCTION", p.s.step)
        assertTrue("the other player bids first", p.client.state.myBidTurn)
        p.client.bid(40); p.pump()
        assertTrue(p.host.state.myBidTurn)
        p.host.bid(50); p.pump()
        p.client.passBid(); p.pump()
        assertTrue(p.host.state.owns(3))
        assertEquals(1450, p.host.state.myCash)
        assertEquals("END", p.host.state.step)
    }

    @Test fun nobodyBiddingLeavesTheStreetUnsold() {
        val p = pair()
        p.roll(1, 2)
        p.nobodyWants()
        assertEquals(0, p.s.owner[3])
        assertEquals("END", p.s.step)
    }

    @Test fun threeDoublesInARowSendYouToJail() {
        val p = pair()
        p.roll(2, 2)           // 4 Income Tax
        assertEquals("ROLL", p.s.step)
        p.roll(3, 3)           // 10 just visiting
        assertEquals("ROLL", p.s.step)
        p.roll(4, 4)           // third double
        assertTrue(p.s.myJail)
        assertEquals(MonopolyBoard.JAIL, p.s.myPos)
        assertEquals("END", p.s.step)
        assertEquals(1300, p.s.myCash) // the tax
    }

    @Test fun goToJailThenPayToLeave() {
        val p = pair()
        p.host.debugSetPos(1, 24)
        p.roll(2, 4) // 30: Go to Jail
        assertTrue(p.s.myJail)
        assertEquals(10, p.s.myPos)
        p.host.endTurn(); p.pump()
        p.guestIdles()
        assertTrue(p.s.canRoll)
        val cash = p.s.myCash
        p.host.payJail(); p.pump()
        assertFalse(p.s.myJail)
        assertEquals(cash - 50, p.s.myCash)
    }

    @Test fun aDoubleLeavesJailAndMovesButGivesNoExtraGo() {
        val p = pair()
        p.host.debugSetPos(1, 24)
        p.roll(2, 4); p.host.endTurn(); p.pump(); p.guestIdles()
        p.roll(3, 3) // a double in jail: out, and move 6 from the jail square
        assertFalse(p.s.myJail)
        assertEquals(16, p.s.myPos)
        assertTrue(p.s.step == "BUY" || p.s.step == "END")
    }

    @Test fun threeFailedJailRollsForceThePaymentAndMove() {
        val p = pair()
        p.host.debugSetPos(1, 24)
        p.roll(2, 4); p.host.endTurn(); p.pump(); p.guestIdles()
        p.roll(1, 2); assertTrue(p.s.myJail); p.host.endTurn(); p.pump(); p.guestIdles()
        p.roll(1, 2); assertTrue(p.s.myJail); p.host.endTurn(); p.pump(); p.guestIdles()
        val cash = p.s.myCash
        p.roll(1, 2) // third failure: pay £50 and move 3 (from 10 to 13)
        assertFalse(p.s.myJail)
        assertEquals(13, p.s.myPos)
        assertEquals(cash - 50, p.s.myCash.let { c -> if (p.s.step == "BUY") c else c })
    }

    @Test fun cardsAreDrawn() {
        val p = pair(seed = 7)
        p.roll(3, 4) // 7: Chance
        assertTrue(MonopolyCards.chance.any { it.text == p.s.cardText })
        assertEquals(p.s.cardText, p.client.state.cardText)
        assertEquals(0, p.s.cardDeck)
    }

    @Test fun incomeTaxIsPaidToTheBank() {
        val p = pair()
        p.roll(2, 2) // 4 Income Tax, a double
        assertEquals(1300, p.s.myCash)
    }

    @Test fun mortgagingAndLiftingCostTenPercent() {
        val p = pair()
        p.roll(1, 2); p.host.buy(); p.pump()
        p.host.mortgage(3); p.pump()
        assertTrue(p.s.mortgaged[3])
        assertEquals(1440 + 30, p.s.myCash)
        p.host.unmortgage(3); p.pump()
        assertFalse(p.s.mortgaged[3])
        assertEquals(1440 + 30 - 33, p.s.myCash)
    }

    /** The guest spends nearly everything at an auction, so the next bill is a problem. */
    private fun SyncPair<MonopolyEngine>.guestSpends(bid: Int) {
        roll(1, 2)                       // host lands on 3 and puts it up for auction
        host.declineToAuction(); pump()
        client.bid(bid); pump()
        host.passBid(); pump()
        host.endTurn(); pump()
    }

    @Test fun cantPayMeansDebtThenMortgageThenPay() {
        val p = pair()
        p.guestSpends(1320) // guest has 180 and owns Whitechapel
        p.roll(2, 2)        // guest lands on Income Tax (£200)
        assertEquals("DEBT", p.client.state.step)
        assertEquals(200, p.client.state.debt)
        p.client.payDebt(); p.pump()
        assertEquals("can't pay yet", "DEBT", p.client.state.step)
        p.client.mortgage(3); p.pump() // +30 -> 210
        p.client.payDebt(); p.pump()
        assertEquals(10, p.client.state.myCash)
        assertTrue(p.client.state.step == "ROLL" || p.client.state.step == "END")
    }

    @Test fun bankruptcyEndsTheGameAndTheCreditorWins() {
        val p = pair()
        p.guestSpends(1490) // guest has 10
        p.roll(2, 2)        // £200 income tax
        assertEquals("DEBT", p.client.state.step)
        p.client.declareBankrupt(); p.pump()
        assertEquals(GPhase.OVER, p.host.state.phase)
        assertEquals(1, p.host.state.result)
        assertEquals(2, p.client.state.result)
        assertEquals("bankrupt", p.host.state.why)
    }

    @Test fun tradingSwapsPropertiesAndCash() {
        val p = pair()
        p.roll(1, 2); p.host.buy(); p.pump(); p.host.endTurn(); p.pump()   // host owns 3
        p.roll(2, 3); p.client.buy(); p.pump()                              // guest owns 5 (King's Cross)
        p.client.endTurn(); p.pump()
        p.host.offerTrade(listOf(3), 0, listOf(5), 50); p.pump()
        assertNotNull(p.client.state.trade)
        assertEquals(2, p.client.state.trade!!.from)
        p.client.acceptTrade(); p.pump()
        assertEquals(2, p.host.state.owner[3])
        assertEquals(1, p.host.state.owner[5])
        assertEquals(1440 + 50, p.host.state.myCash)
        assertEquals(1500 - 200 - 50, p.client.state.myCash)
    }

    @Test fun badOrDeclinedTradesChangeNothing() {
        val p = pair()
        p.roll(1, 2); p.host.buy(); p.pump(); p.host.endTurn(); p.pump()
        p.guestIdles()
        p.host.offerTrade(listOf(5), 0, emptyList(), 10); p.pump() // square 5 isn't theirs to give
        assertEquals(null, p.client.state.trade)
        p.host.offerTrade(listOf(3), 0, emptyList(), 10); p.pump()
        assertNotNull(p.client.state.trade)
        p.client.declineTrade(); p.pump()
        assertEquals(null, p.host.state.trade)
        assertEquals(1, p.host.state.owner[3])
    }

    @Test fun shortGamesEndOnWorthAndTheRicherPlayerWins() {
        val p = pair(len = 2) // 15 turns each
        var guard = 0
        while (p.s.phase != GPhase.OVER && guard++ < 400) {
            val e = if (p.host.state.myTurn) p.host else p.client
            val st = e.state
            when {
                p.host.state.myBidTurn -> p.host.passBid()
                p.client.state.myBidTurn -> p.client.passBid()
                st.step == "ROLL" -> e.roll()
                st.step == "BUY" -> e.declineToAuction()
                st.step == "END" -> e.endTurn()
                st.step == "DEBT" -> e.declareBankrupt()
                else -> break
            }
            p.pump()
        }
        assertEquals(GPhase.OVER, p.s.phase)
        assertTrue(p.s.result in 1..3)
    }

    @Test fun theComputerPlaysAWholeQuickGame() {
        for (seed in 1..3) {
            val d = Duel(seed, Skill.NORMAL, Skill.NORMAL)
            d.host.monopoly.start(JSONObject().put("len", 2))
            var guard = 0
            while (d.host.monopoly.state.phase != GPhase.OVER && guard++ < 40000) d.advance(250)
            val s = d.host.monopoly.state
            assertEquals("seed $seed should finish", GPhase.OVER, s.phase)
            assertTrue(s.result in 1..3)
            assertNotNull(s.log.lastOrNull())
        }
    }
}

/** The guest takes a harmless turn (lands on Jail, just visiting) and ends it. */
private fun SyncPair<MonopolyEngine>.guestIdles() {
    host.forcedDice.add(4 to 6)
    client.roll(); pump()
    if (client.state.step == "END") { client.endTurn(); pump() }
}
