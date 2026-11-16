package com.neil1454.drawsomething.core

import org.json.JSONObject
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test
import kotlin.random.Random

class DealNoDealTest {
    private fun pair(seed: Int = 1): SyncPair<DealNoDealEngine> {
        val p = SyncPair({ n, o, h, s, r, c -> DealNoDealEngine(n, o, h, s, r, c) }, seed)
        p.host.start(JSONObject())
        p.pump()
        return p
    }

    private fun SyncPair<DealNoDealEngine>.pickBoth(h: Int = 3, g: Int = 9) { host.pick(h); pump(); client.pick(g); pump() }

    /** Opens whatever boxes are asked of the players until the Banker rings (or the game ends). */
    private fun SyncPair<DealNoDealEngine>.openUntilOffer() {
        var guard = 0
        while (host.state.stage == "OPEN" && guard++ < 40) {
            val e = if (host.state.myTurn) host else client
            val b = e.state.boxes.first { it.status == 0 }
            e.open(b.number); pump()
        }
    }

    @Test fun thePrizesAreTheTwentyTwoUkAmounts() {
        assertEquals(22, DndRules.PRIZES.size)
        assertEquals(22, DndRules.PRIZES.toSet().size)
        assertEquals(1L, DndRules.PRIZES.first()); assertEquals(25_000_000L, DndRules.PRIZES.last())
        assertEquals(20, DndRules.OPENS.sum())
        assertEquals("£250,000", DndRules.money(25_000_000L)); assertEquals("1p", DndRules.money(1)); assertEquals("50p", DndRules.money(50)); assertEquals("£1", DndRules.money(100)); assertEquals("£3,000", DndRules.money(300_000L))
    }

    @Test fun theBankersOffersStartLowAndRiseTowardsTheAverage() {
        val pool = DndRules.PRIZES.toList()
        val avg = pool.average()
        var last = 0L
        for (r in 1..DndRules.ROUNDS) {
            val o = DndRules.offer(pool, r, 0.0)
            assertTrue("round $r offer $o rises", o > last)
            assertTrue(o <= avg * 1.01)
            last = o
        }
        assertTrue(DndRules.offer(pool, 1, 0.0) < avg * 0.3)
        assertTrue(DndRules.offer(listOf(100L, 25_000_000L), 8, 0.0) > 11_000_000L)
        assertEquals(0L, DndRules.offer(emptyList(), 1, 0.0))
        assertTrue(DndRules.nice(123_456L) % 5_000L == 0L)
    }

    @Test fun eachPlayerPicksADifferentBoxAndOpeningTakesTurns() {
        val p = pair(1)
        p.host.pick(5); p.pump()
        p.client.pick(5); p.pump()               // taken: refused
        assertEquals(0, p.client.state.myBox)
        assertEquals(5, p.client.state.theirBox)
        p.client.pick(99); p.pump()              // not a box
        assertEquals(0, p.client.state.myBox)
        p.client.pick(8); p.pump()
        assertEquals("OPEN", p.host.state.stage)
        assertEquals(DndRules.OPENS[0], p.host.state.opensLeft)
        val first = if (p.host.state.myTurn) p.host else p.client
        val second = if (first === p.host) p.client else p.host
        second.open(1); p.pump()                 // not their turn
        assertEquals(DndRules.OPENS[0], p.host.state.opensLeft)
        first.open(5); p.pump()                  // a held box can't be opened
        first.open(2); p.pump()
        assertEquals(DndRules.OPENS[0] - 1, p.host.state.opensLeft)
        assertTrue(second.state.myTurn)
        second.open(2); p.pump()                 // already open
        assertEquals(DndRules.OPENS[0] - 1, p.host.state.opensLeft)
        // both phones see the prize that was opened, and it is the same prize
        val a = first.state.boxes[1].value; val b = second.state.boxes[1].value
        assertTrue(a >= 0 && a == b && a in DndRules.PRIZES.toList())
    }

    @Test fun theHeldBoxesStaySecretUntilTheyAreShown() {
        val p = pair(2)
        p.pickBoth(3, 9)
        p.openUntilOffer()
        for (e in listOf(p.host, p.client)) {
            assertEquals(-1L, e.state.myBoxValue); assertEquals(-1L, e.state.theirBoxValue)
            assertEquals(4, e.state.boxes.count { it.value >= 0 }.let { if (it == DndRules.OPENS[0]) 4 else it })
            // the prizes still out there include the ones in the two boxes: 22 minus the opened ones
            assertEquals(22 - DndRules.OPENS[0], e.state.unseen.size)
        }
        assertEquals("OFFER", p.host.state.stage)
    }

    @Test fun offersAreShownToBothAndAnswersStayHiddenUntilBothAreIn() {
        val p = pair(3)
        p.pickBoth(); p.openUntilOffer()
        assertTrue(p.host.state.myOffer > 0 && p.host.state.theirOffer > 0)
        assertEquals(p.host.state.myOffer, p.client.state.theirOffer)
        p.host.answer(true); p.pump()
        assertTrue(p.client.state.theirAnswered)
        assertFalse("their answer is not revealed yet", p.client.state.theirDealt)
        assertEquals("OFFER", p.client.state.stage)
        p.host.answer(false); p.pump()           // can't change your mind
        p.client.answer(false); p.pump()
        assertTrue(p.host.state.myDealt); assertTrue(p.client.state.theirDealt)
        assertFalse(p.client.state.myDealt)
        assertEquals(p.host.state.myDealAmount, p.client.state.theirDealAmount)
        // the dealer's box was opened for everyone to see
        assertTrue(p.client.state.theirBoxValue >= 0)
        assertEquals(p.host.state.myBoxValue, p.client.state.theirBoxValue)
        assertEquals("OPEN", p.host.state.stage)
        assertEquals(2, p.host.state.round)
        // from now on the player who is still in opens every box
        assertTrue(p.client.state.myTurn && !p.host.state.myTurn)
    }

    @Test fun twoDealsEndTheGameAndTheHigherAmountWins() {
        val p = pair(4)
        p.pickBoth(); p.openUntilOffer()
        p.host.answer(true); p.client.answer(true); p.pump()
        assertEquals(GPhase.OVER, p.host.state.phase)
        assertEquals(p.host.state.myDealAmount, p.host.state.myFinal)
        assertEquals(p.client.state.theirFinal, p.host.state.myFinal)
        val r = p.host.state.result
        val expect = if (p.host.state.myFinal > p.host.state.theirFinal) 1 else if (p.host.state.myFinal < p.host.state.theirFinal) 2 else 3
        assertEquals(expect, r)
        assertEquals(when (r) { 1 -> 2; 2 -> 1; else -> 3 }, p.client.state.result)
    }

    @Test fun swappingNeedsBothPlayersToAgree() {
        for (case in 0..3) {
            val p = pair(10 + case)
            p.pickBoth(4, 7)
            var guard = 0
            while (p.host.state.stage != "SWAP" && guard++ < 60) {
                when (p.host.state.stage) {
                    "OPEN" -> p.openUntilOffer()
                    "OFFER" -> { p.host.answer(false); p.client.answer(false); p.pump() }
                }
            }
            assertEquals("SWAP", p.host.state.stage)
            val hostYes = case and 1 == 1; val guestYes = case and 2 == 2
            p.host.swap(hostYes); p.pump()
            assertTrue(p.client.state.theirSwapSaid)
            assertEquals(GPhase.PLAYING, p.host.state.phase)
            p.client.swap(guestYes); p.pump()
            assertEquals(GPhase.OVER, p.host.state.phase)
            assertEquals("case $case", hostYes && guestYes, p.host.state.swapped)
            assertEquals(p.host.state.swapped, p.client.state.swapped)
            // the final amounts are the two prizes left, and each is one of the boxes' prizes
            val hv = p.host.state.myBoxValue; val gv = p.client.state.myBoxValue
            assertTrue(hv >= 0 && gv >= 0 && hv != gv)
            assertEquals(hv, p.host.state.myFinal); assertEquals(gv, p.client.state.myFinal)
            // after a swap, "my box" holds what the other player's box did
            assertEquals(if (p.host.state.swapped) 7 else 4, p.host.state.myBox)
        }
    }

    @Test fun computersPlayWholeGames() {
        for (seed in 1..8) {
            val p = pair(seed)
            val a = DealNoDealBrain(p.host, Skill.HARD, Random(seed)); val b = DealNoDealBrain(p.client, Skill.NORMAL, Random(seed + 33))
            var g = 0
            while (p.host.state.phase == GPhase.PLAYING && g++ < 4000) { a.think(p.now); b.think(p.now); p.advance(100) }
            assertEquals("seed $seed", GPhase.OVER, p.host.state.phase)
            assertEquals(p.host.state.myFinal, p.client.state.theirFinal)
            assertEquals(p.host.state.theirFinal, p.client.state.myFinal)
            assertTrue(p.host.state.myFinal > 0 && p.host.state.theirFinal > 0)
            val r = p.host.state.result
            assertEquals(when (r) { 1 -> 2; 2 -> 1; else -> 3 }, p.client.state.result)
        }
    }

    @Test fun badMessagesAreIgnored() {
        val p = pair(5)
        val before = p.client.state
        p.client.onMessage(GMsg.Snap("DEALNODEAL", JSONObject().put("vl", org.json.JSONArray().put(5)).put("hb", org.json.JSONArray())))
        p.host.onMessage(GMsg.Act("DEALNODEAL", JSONObject()))
        p.host.onMessage(GMsg.Act("DEALNODEAL", JSONObject().put("a", "open").put("b", "x")))
        assertEquals(before.stage, p.client.state.stage)
        assertEquals(GPhase.PLAYING, p.host.state.phase)
    }
}
