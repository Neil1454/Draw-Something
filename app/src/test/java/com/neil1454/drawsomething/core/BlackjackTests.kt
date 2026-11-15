package com.neil1454.drawsomething.core

import org.json.JSONObject
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test
import kotlin.random.Random

class BlackjackMathTest {
    // card = suit * 13 + rank (0 = ace ... 9 = ten, 10 J, 11 Q, 12 K)
    private fun c(rank: Int, suit: Int = 0) = suit * 13 + rank

    @Test fun totalsHandleSoftAcesAndBlackjack() {
        assertEquals(21 to true, BjMath.total(listOf(c(0), c(12))))
        assertEquals(12 to true, BjMath.total(listOf(c(0), c(0))))            // A + A = soft 12
        assertEquals(26 to false, BjMath.total(listOf(c(0), c(5), c(8), c(9)))) // A counts 1: 1+6+9+10
        assertTrue(BjMath.isBlackjack(listOf(c(0), c(9))))
        assertFalse(BjMath.isBlackjack(listOf(c(4), c(5), c(9))))
        assertTrue(BjMath.isBust(listOf(c(9), c(8), c(5))))
        assertEquals(18 to true, BjMath.total(listOf(c(0), c(6))))
    }

    @Test fun bustChanceIsRight() {
        assertEquals(62, BjMath.bustChance(listOf(c(9), c(5))))   // hard 16
        assertEquals(31, BjMath.bustChance(listOf(c(9), c(1))))    // hard 12: only a 10-value card busts (4 in 13)
        assertEquals(0, BjMath.bustChance(listOf(c(0), c(5))))     // soft 17 can't bust on one card
        assertEquals(100, BjMath.bustChance(listOf(9, 8, 5)))                 // already bust
    }

    @Test fun namesAreReadable() {
        assertEquals("A♠", BjMath.name(0))
        assertEquals("K♣", BjMath.name(3 * 13 + 12))
        assertEquals("10♥", BjMath.name(13 + 9))
    }
}

class BlackjackStrategyTest {
    private fun c(rank: Int) = rank
    private val ten = 9
    private val six = 5
    private val two = 1

    @Test fun classicBasicStrategyDecisions() {
        assertEquals("hit", BjStrategy.advise(listOf(c(ten), c(six)), 9, true).action)   // 16 vs 10
        assertEquals("stand", BjStrategy.advise(listOf(c(ten), c(1)), six, true).action) // 12 vs 6
        assertEquals("double", BjStrategy.advise(listOf(c(5), c(4)), six, true).action)   // 11 vs 6
        assertEquals("hit", BjStrategy.advise(listOf(c(5), c(4)), six, false).action)     // can't double
        assertEquals("stand", BjStrategy.advise(listOf(c(ten), c(ten)), six, true).action) // 20
        assertEquals("hit", BjStrategy.advise(listOf(c(0), c(6)), 8, true).action)         // soft 18 vs 9
        assertEquals("stand", BjStrategy.advise(listOf(c(0), c(6)), 6, false).action)      // soft 18 vs 8 stands
        assertEquals("hit", BjStrategy.advise(listOf(c(1), c(2)), 4, true).action)         // 5
    }

    @Test fun everyTipHasAReason() {
        for (a in 0 until 13) for (b in 0 until 13) for (up in 0 until 13) {
            val adv = BjStrategy.advise(listOf(a, b), up, true)
            assertTrue(adv.action in listOf("hit", "stand", "double"))
            assertTrue(adv.reason.length > 10)
        }
    }
}

class BlackjackGameTest {
    private fun pair(seed: Int = 1, rounds: Int = 0, tips: Int = 0): SyncPair<BlackjackEngine> {
        val p = SyncPair({ n, o, h, s, r, c -> BlackjackEngine(n, o, h, s, r, c) }, seed)
        p.host.start(JSONObject().put("rounds", rounds).put("tips", tips))
        p.pump()
        return p
    }

    private fun dealRound(p: SyncPair<BlackjackEngine>) {
        p.host.deal(); p.client.deal(); p.pump()
    }

    @Test fun bothStartWithChipsAndATableToBetAt() {
        val p = pair()
        for (e in listOf(p.host, p.client)) {
            assertEquals(GPhase.PLAYING, e.state.phase)
            assertEquals("BET", e.state.stage)
            assertEquals(500, e.state.myChips)
            assertEquals(25, e.state.mine.bet)
            assertEquals(5, e.state.rounds)
        }
    }

    @Test fun theDealersHoleCardStaysHiddenUntilTheDealerPlays() {
        val p = pair(seed = 3)
        dealRound(p)
        for (e in listOf(p.host, p.client)) {
            assertTrue(e.state.stage == "PLAY" || e.state.stage == "DEALER")
            if (e.state.stage == "PLAY") {
                assertEquals(2, e.state.dealer.size)
                assertTrue(e.state.dealer[0] in 0..51)
                assertEquals("hole card must be face down", -1, e.state.dealer[1])
            }
            assertEquals(2, e.state.mine.cards.size)
            assertEquals(2, e.state.theirs.cards.size)
        }
        assertEquals(p.host.state.mine.cards, p.client.state.theirs.cards)
        assertEquals("the 25 bet came off my chips when the cards were dealt", 475, p.host.state.myChips)
    }

    @Test fun actingPlaysTheHandAndTheRoundSettlesOnBothPhones() {
        val p = pair(seed = 5)
        dealRound(p)
        var guard = 0
        while (p.host.state.stage == "PLAY" && guard++ < 50) {
            for (e in listOf(p.host, p.client)) if (e.state.canAct) e.stand()
            p.pump()
        }
        // let the dealer finish and the round settle
        guard = 0
        while (p.host.state.stage != "RESULT" && p.host.state.stage != "BET" && guard++ < 200) p.advance(200)
        assertTrue(p.host.state.stage == "RESULT" || p.host.state.stage == "BET")
        assertEquals(p.host.state.myChips, p.client.state.theirChips)
        assertEquals(p.host.state.theirChips, p.client.state.myChips)
        assertTrue(p.host.state.mine.outcome in listOf("win", "lose", "push", "bj", "bust") || p.host.state.stage == "BET")
        assertFalse(p.host.state.dealer.contains(-1))
    }

    @Test fun twoComputerPlayersFinishAFullGame() {
        val p = pair(seed = 7, rounds = 0)
        val a = BlackjackBrain(p.host, Skill.NORMAL, Random(1))
        val b = BlackjackBrain(p.client, Skill.HARD, Random(2))
        var guard = 0
        while (p.host.state.phase == GPhase.PLAYING && guard++ < 20_000) {
            a.think(p.now); b.think(p.now)
            p.advance(100)
        }
        assertEquals(GPhase.OVER, p.host.state.phase)
        assertEquals(GPhase.OVER, p.client.state.phase)
        assertEquals("the two phones agree on who won", p.host.state.result.let { if (it == 1) 2 else if (it == 2) 1 else it }, p.client.state.result)
        assertTrue(p.host.state.log.isNotEmpty())
    }

    @Test fun tipsAreLimitedByTheAllowanceAndOffMeansOff() {
        val off = pair(seed = 2, tips = 0)
        dealRound(off)
        off.host.tip(); off.pump()
        assertNull(off.host.state.tip)

        val three = pair(seed = 2, tips = 1)
        assertEquals(3, three.host.state.tipsLeft)
        dealRound(three)
        if (three.host.state.canAct) {
            three.host.tip(); three.pump()
            assertNotNull(three.host.state.tip)
            assertEquals(2, three.host.state.tipsLeft)
            assertEquals("the other player's tips are their own", 3, three.client.state.tipsLeft)
            assertNull(three.client.state.tip)
            three.host.hit(); three.pump() // acting clears the tip
            assertNull(three.host.state.tip)
        }

        val unlimited = pair(seed = 2, tips = 3)
        dealRound(unlimited)
        if (unlimited.host.state.canAct) {
            repeat(6) { unlimited.host.tip(); unlimited.pump() }
            assertNotNull(unlimited.host.state.tip)
        }
    }

    @Test fun messagesAreRobust() {
        val p = pair()
        p.host.onMessage(GMsg.Act("BLACKJACK", JSONObject().put("a", "bet").put("v", -50)))
        p.host.onMessage(GMsg.Act("BLACKJACK", JSONObject().put("a", "nonsense")))
        p.host.onMessage(GMsg.Snap("BLACKJACK", JSONObject().put("h1", org.json.JSONArray(listOf(99, -3, 5)))))
        assertEquals(GPhase.PLAYING, p.host.state.phase)
    }
}
