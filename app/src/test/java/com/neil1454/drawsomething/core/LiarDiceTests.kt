package com.neil1454.drawsomething.core

import org.json.JSONObject
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test
import kotlin.random.Random

class LiarMathTest {
    @Test fun countingAndWildOnes() {
        assertEquals(3, LiarMath.count(listOf(5, 5, 2, 5), 5, false))
        assertEquals(4, LiarMath.count(listOf(5, 1, 2, 5, 1), 5, true))
        assertEquals(2, LiarMath.count(listOf(5, 1, 2, 5, 1), 1, true))
    }

    @Test fun probabilitiesMakeSense() {
        assertEquals(1.0, LiarMath.atLeast(0, 5, 1.0 / 6), 1e-9)
        assertEquals(0.0, LiarMath.atLeast(6, 5, 1.0 / 6), 1e-9)
        assertEquals(1 - Math.pow(5.0 / 6, 5.0), LiarMath.atLeast(1, 5, 1.0 / 6), 1e-9)
        assertTrue(LiarMath.atLeast(1, 5, 1.0 / 3) > LiarMath.atLeast(1, 5, 1.0 / 6))
    }

    @Test fun higherBids() {
        assertTrue(LiarMath.higher(3, 2, 2, 6))
        assertTrue(LiarMath.higher(2, 5, 2, 4))
        assertFalse(LiarMath.higher(2, 4, 2, 4))
        assertFalse(LiarMath.higher(1, 6, 2, 1))
    }
}

class LiarDiceGameTest {
    private fun pair(seed: Int = 1, dice: Int = 0, wild: Boolean = false): SyncPair<LiarDiceEngine> {
        val p = SyncPair({ n, o, h, s, r, c -> LiarDiceEngine(n, o, h, s, r, c) }, seed)
        p.host.start(JSONObject().put("dice", dice).put("wild", wild))
        p.pump()
        p.advance(100)
        return p
    }

    @Test fun eachPhoneRollsItsOwnDiceAndNeverSeesTheOthers() {
        val p = pair()
        for (e in listOf(p.host, p.client)) {
            assertEquals(3, e.state.myDice.size)
            assertTrue(e.state.myDice.all { it in 1..6 })
            assertTrue(e.state.theirDice.isEmpty())
            assertEquals("BID", e.state.stage)
        }
        assertTrue(p.host.state.myTurn != p.client.state.myTurn)
    }

    @Test fun bidsMustGoUpAndPassTheTurn() {
        val p = pair(seed = 2)
        val a = if (p.host.state.myTurn) p.host else p.client
        val b = if (a === p.host) p.client else p.host
        a.bid(2, 4); p.pump()
        assertEquals(2, a.state.bid!!.qty); assertEquals(1, a.state.bid!!.by)
        assertEquals(2, b.state.bid!!.by)
        assertTrue(b.state.myTurn)
        b.bid(2, 3); p.pump() // not higher: ignored
        assertEquals(4, b.state.bid!!.face)
        b.bid(2, 5); p.pump()
        assertEquals(5, a.state.bid!!.face)
        a.bid(99, 1); p.pump() // more dice than exist
        assertEquals(5, a.state.bid!!.face)
    }

    @Test fun callingLiarRevealsBothHandsAndTheLoserLosesADie() {
        val p = pair(seed = 3)
        val a = if (p.host.state.myTurn) p.host else p.client
        val b = if (a === p.host) p.client else p.host
        a.bid(6, 6); p.pump() // all six dice showing sixes: nearly impossible
        b.callLiar(); p.pump()
        p.advance(100); p.advance(100)
        assertEquals("SHOW", p.host.state.stage)
        assertEquals(3, p.host.state.theirDice.size)
        assertEquals(3, p.client.state.theirDice.size)
        val actual = LiarMath.count(p.host.state.myDice, 6, false) + LiarMath.count(p.client.state.myDice, 6, false)
        assertEquals(actual, p.host.state.actual)
        val bidderLost = actual < 6
        val loserCount = (if (bidderLost) a else b).state.myCount
        assertEquals(2, loserCount)
        assertEquals(3, (if (bidderLost) b else a).state.myCount)
        // both ack: next round, the loser bids first
        p.host.ack(); p.client.ack(); p.pump(); p.advance(100)
        assertEquals("BID", p.host.state.stage)
        assertEquals(2, p.host.state.round)
        assertTrue((if (bidderLost) a else b).state.myTurn)
        assertNull(p.host.state.bid)
    }

    @Test fun twoComputerPlayersFinishAFullGame() {
        for (seed in 1..6) {
            val p = pair(seed = seed, dice = 0, wild = seed % 2 == 0)
            val a = LiarDiceBrain(p.host, Skill.HARD, Random(seed))
            val b = LiarDiceBrain(p.client, Skill.NORMAL, Random(seed + 30))
            var guard = 0
            while (p.host.state.phase == GPhase.PLAYING && guard++ < 20_000) { a.think(p.now); b.think(p.now); p.advance(100) }
            assertEquals("seed $seed", GPhase.OVER, p.host.state.phase)
            assertEquals(GPhase.OVER, p.client.state.phase)
            val hr = p.host.state.result
            assertEquals(if (hr == 1) 2 else 1, p.client.state.result)
            assertTrue(p.host.state.myCount == 0 || p.host.state.theirCount == 0)
        }
    }

    @Test fun badMessagesAreIgnored() {
        val p = pair()
        p.host.onMessage(GMsg.Act("LIARSDICE", JSONObject().put("a", "bid").put("q", -3).put("f", 9)))
        p.host.onMessage(GMsg.Act("LIARSDICE", JSONObject().put("a", "call")))
        p.host.onMessage(GMsg.Act("LIARSDICE", JSONObject().put("a", "dice").put("d", org.json.JSONArray(listOf(9, 9, 9, 9, 9, 9, 9)))))
        assertEquals(GPhase.PLAYING, p.host.state.phase)
        assertEquals("BID", p.host.state.stage)
    }
}
