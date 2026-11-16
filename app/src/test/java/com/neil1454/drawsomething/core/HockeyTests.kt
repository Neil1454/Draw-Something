package com.neil1454.drawsomething.core

import org.json.JSONObject
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import kotlin.random.Random

class HockeyTest {
    private fun pair(seed: Int = 1, mode: Int = 0, target: Int = 0): SyncPair<HockeyEngine> {
        val p = SyncPair({ n, o, h, s, r, c -> HockeyEngine(n, o, h, s, r, c) }, seed)
        p.host.start(JSONObject().put("mode", mode).put("target", target))
        p.pump()
        return p
    }

    private fun SyncPair<HockeyEngine>.run(ms: Int, step: Int = 33, each: () -> Unit = {}) {
        var t = 0
        while (t < ms) { each(); advance(step.toLong()); t += step }
    }

    @Test fun bothPhonesSeeThemselvesAtTheBottom() {
        val p = pair()
        p.run(100)
        assertTrue(p.host.state.myY > 100f && p.client.state.myY > 100f)
        assertTrue(p.host.state.theirY < 40f && p.client.state.theirY < 40f)
        // the puck is on one half, and the two views of it are mirror images
        assertEquals(p.host.state.puckX, 100f - p.client.state.puckX, 0.3f)
        assertEquals(p.host.state.puckY, 140f - p.client.state.puckY, 0.3f)
        assertEquals(GPhase.PLAYING, p.client.state.phase)
    }

    @Test fun hittingThePuckIntoTheGoalScoresForTheRightPlayer() {
        // find a seed where the host serves (the puck starts in the host's half)
        for (seed in 1..8) {
            val p = pair(seed)
            p.run(2100)
            if (p.host.state.puckY < 70f) continue
            // charge up the middle: the mallet whacks the puck towards the top goal
            p.run(5000) { p.host.input(p.host.state.puckX, 60f); p.client.input(10f, 124f) }
            assertTrue("seed $seed host goals", p.host.state.myScore == 1)
            assertEquals(1, p.client.state.theirScore)
            assertEquals(1, p.host.state.lastScorer); assertEquals(2, p.client.state.lastScorer)
            return
        }
        throw AssertionError("no seed served to the host")
    }

    @Test fun malletsStayInTheirOwnHalf() {
        val p = pair(3)
        p.run(2100)
        p.run(1500) { p.host.input(50f, 0f); p.client.input(50f, 0f) }
        assertTrue(p.host.state.myY >= 70f - 0.1f)
        assertTrue(p.client.state.myY >= 70f - 0.1f)
        assertTrue(p.host.state.theirY <= 70f + 0.1f)
    }

    @Test fun pongPaddlesFollowTheFingerAndTheBallSpeedsUp() {
        val p = pair(4, mode = 1)
        p.run(2100)
        p.run(1500) { p.host.input(20f, 124f); p.client.input(80f, 124f) }
        assertEquals(20f, p.host.state.myX, 1.5f)
        assertEquals(80f, p.client.state.myX, 1.5f)
        // the other paddle is drawn mirrored on each screen
        assertEquals(20f, 100f - p.client.state.theirX, 1.5f)
    }

    @Test fun badInputIsIgnoredOrClamped() {
        val p = pair(5)
        p.run(2100)
        p.client.act(JSONObject().put("a", "in").put("x", 9999).put("y", -500))
        p.client.act(JSONObject().put("a", "nonsense"))
        p.host.onMessage(GMsg.Act("HOCKEY", JSONObject()))
        p.run(500)
        assertTrue(p.host.state.theirX in 0f..100f && p.host.state.theirY in 0f..140f)
        p.client.onMessage(GMsg.Snap("HOCKEY", JSONObject().put("p", org.json.JSONArray().put(1))))
        assertEquals(GPhase.PLAYING, p.client.state.phase)
    }

    @Test fun twoComputersFinishBothGames() {
        for (mode in 0..1) for (seed in 1..3) {
            val p = pair(seed, mode = mode)
            val a = HockeyBrain(p.host, Skill.HARD, Random(seed)); val b = HockeyBrain(p.client, Skill.NORMAL, Random(seed + 9))
            var g = 0
            while (p.host.state.phase == GPhase.PLAYING && g++ < 20_000) { a.think(p.now); b.think(p.now); p.advance(33) }
            assertEquals("mode $mode seed $seed", GPhase.OVER, p.host.state.phase)
            assertEquals(p.host.state.myScore, p.client.state.theirScore)
            val r = p.host.state.result
            assertEquals(when (r) { 1 -> 2; 2 -> 1; else -> 3 }, p.client.state.result)
            assertTrue("a game was played ($mode/$seed): ${p.host.state.myScore}-${p.host.state.theirScore}", p.host.state.myScore + p.host.state.theirScore >= 1)
        }
    }
}
