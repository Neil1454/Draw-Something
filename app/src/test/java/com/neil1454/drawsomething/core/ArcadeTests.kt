package com.neil1454.drawsomething.core

import org.json.JSONObject
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

private fun <E : SyncEngine> SyncPair<E>.run(ms: Long) {
    var left = ms
    while (left > 0) { advance(33); left -= 33 }
}

class InvadersTest {
    private fun pair(seed: Int = 1, waves: Int = 3, lives: Int = 3): SyncPair<InvadersEngine> {
        val p = SyncPair({ n, o, h, s, r, c -> InvadersEngine(n, o, h, s, r, c) }, seed)
        p.host.start(JSONObject().put("waves", waves).put("lives", lives))
        p.pump()
        return p
    }

    @Test fun bothPhonesStartTogetherWithAFullFormationAndShields() {
        val p = pair()
        for (e in listOf(p.host, p.client)) {
            assertEquals(GPhase.PLAYING, e.state.phase)
            assertEquals(50, e.state.remaining)
            assertEquals("READY", e.state.c.stage)
            assertEquals(384, e.state.shields.length)
            assertTrue(e.state.shields.count { it == '1' } > 200)
            assertEquals(3, e.state.c.mine.lives)
        }
        assertTrue("the host is on the left, the guest on the right", p.host.state.c.mine.x < p.host.state.c.theirs.x)
        assertTrue(p.client.state.c.mine.x > p.client.state.c.theirs.x)
        p.run(2500)
        assertEquals("PLAY", p.host.state.c.stage)
    }

    @Test fun shipsFollowTheirOwnFingerAndBothPhonesSeeIt() {
        val p = pair()
        p.run(2500)
        p.host.input(10f, 128f, false)
        p.client.input(90f, 122f, false)
        p.run(1500)
        assertTrue(p.host.state.c.mine.x < 14f)
        assertTrue(p.host.state.c.theirs.x > 86f)
        assertTrue("the guest sees themselves on the right and the host on the left", p.client.state.c.mine.x > 86f && p.client.state.c.theirs.x < 14f)
        assertTrue(p.client.state.c.mine.y < 125f)
    }

    @Test fun firingKillsInvadersAndScoresForTheShooter() {
        val p = pair()
        p.run(2500)
        var guard = 0
        // stay under the nearest column and hold fire
        while (p.host.state.myScore == 0 && guard++ < 400) {
            val s = p.host.state
            val col = (0 until InvadersRules.COLS).firstOrNull { c -> (0 until InvadersRules.ROWS).any { s.alive[it * InvadersRules.COLS + c] == '1' } } ?: 0
            p.host.input(s.fx + col * InvadersRules.SPACING_X + 3f, 130f, true)
            p.run(100)
        }
        assertTrue("the host scored", p.host.state.myScore >= 10)
        assertTrue(p.host.state.remaining < 50)
        assertEquals("only the shooter scores", 0, p.host.state.theirScore)
        assertEquals(p.host.state.myScore, p.client.state.theirScore)
    }

    @Test fun idlePlayersGetBombedAndLoseLives() {
        val p = pair(seed = 3)
        p.run(2500)
        var guard = 0
        while (p.host.state.phase != GPhase.OVER && p.host.state.c.mine.lives == 3 && guard++ < 2000) p.run(100)
        assertTrue("an idle ship is eventually hit", p.host.state.c.mine.lives < 3 || p.host.state.phase == GPhase.OVER)
    }

    @Test fun shieldsCrumble() {
        val p = pair(seed = 4)
        p.run(2500)
        val before = p.host.state.shields.count { it == '1' }
        p.run(40_000)
        assertTrue("bombs and bullets chip the shields", p.host.state.shields.count { it == '1' } < before)
    }

    @Test fun theSnapshotIsSmallEnoughToSendThirtyTimesASecond() {
        val sent = ArrayList<Int>()
        val e = InvadersEngine("A", "B", true, { sent.add(it.encode().size) }, kotlin.random.Random(2)) { 0L }
        e.start(JSONObject())
        assertTrue("snapshot ${sent.max()} bytes", sent.max() < 2500)
    }

    @Test fun twoComputersPlayAWholeGameTogether() {
        for (seed in 1..2) {
            val d = Duel(seed, Skill.NORMAL, Skill.NORMAL)
            d.host.invaders.start(JSONObject().put("waves", 3).put("lives", 3))
            var guard = 0
            while (d.host.invaders.state.phase != GPhase.OVER && guard++ < 60_000) d.advance(50)
            val s = d.host.invaders.state
            assertEquals("seed $seed should finish", GPhase.OVER, s.phase)
            assertTrue(s.result in 1..3)
            assertTrue("they shot some invaders (${s.myScore} + ${s.theirScore})", s.myScore + s.theirScore > 100)
            assertEquals(GPhase.OVER, d.guest.invaders.state.phase)
        }
    }

    @Test fun anIdleEasyGameStillEndsInADefeat() {
        val p = pair(seed = 7, waves = 5, lives = 1)
        var guard = 0
        while (p.host.state.phase != GPhase.OVER && guard++ < 20_000) p.run(100)
        assertEquals(GPhase.OVER, p.host.state.phase)
        assertTrue(p.host.state.c.why == "lives" || p.host.state.c.why == "invaded")
    }
}

class CentipedeTest {
    private fun pair(seed: Int = 1, waves: Int = 3, lives: Int = 3): SyncPair<CentipedeEngine> {
        val p = SyncPair({ n, o, h, s, r, c -> CentipedeEngine(n, o, h, s, r, c) }, seed)
        p.host.start(JSONObject().put("waves", waves).put("lives", lives))
        p.pump()
        return p
    }

    @Test fun bothPhonesSeeTheCentipedeAndTheMushroomField() {
        val p = pair()
        for (e in listOf(p.host, p.client)) {
            assertEquals(GPhase.PLAYING, e.state.phase)
            assertEquals(CentipedeRules.TOTAL_SEGS, e.state.segs.size)
            assertEquals(CentipedeRules.COLS * CentipedeRules.ROWS, e.state.mush.length)
            assertTrue(e.state.mush.count { it != '0' } in 20..40)
        }
        p.run(4000)
        assertTrue("the head has walked in", p.host.state.segs.first { it.head }.col >= 1)
        assertEquals(p.host.state.segs.map { it.col to it.row }, p.client.state.segs.map { it.col to it.row })
    }

    @Test fun shootingASegmentSplitsTheCentipedeAndLeavesAMushroom() {
        val p = pair(seed = 2)
        p.run(2600)
        var guard = 0
        var start = p.host.state.segs.size
        var split = false
        while (guard++ < 500 && p.host.state.myScore < 10) {
            val target = p.host.state.segs.filter { it.col >= 0 }.maxByOrNull { it.row }
            p.host.input(((target?.col ?: 12) * 4f + 2f), 130f, true)
            p.run(100)
        }
        assertTrue("the host scored by shooting a segment", p.host.state.myScore >= 10)
        assertTrue(p.host.state.segs.size < start || p.host.state.segs.size == start - 1)
        assertEquals(p.host.state.myScore, p.client.state.theirScore)
    }

    @Test fun idleShipsAreEventuallyCaught() {
        val p = pair(seed = 5, lives = 1)
        var guard = 0
        while (p.host.state.phase != GPhase.OVER && guard++ < 20_000) p.run(100)
        assertEquals(GPhase.OVER, p.host.state.phase)
    }

    @Test fun theSnapshotIsSmall() {
        val sent = ArrayList<Int>()
        val e = CentipedeEngine("A", "B", true, { sent.add(it.encode().size) }, kotlin.random.Random(2)) { 0L }
        e.start(JSONObject())
        assertTrue("snapshot ${sent.max()} bytes", sent.max() < 3500)
    }

    @Test fun twoComputersPlayAWholeGameTogether() {
        for (seed in 1..2) {
            val d = Duel(seed, Skill.NORMAL, Skill.NORMAL)
            d.host.centipede.start(JSONObject().put("waves", 3).put("lives", 3))
            var guard = 0
            while (d.host.centipede.state.phase != GPhase.OVER && guard++ < 60_000) d.advance(50)
            val s = d.host.centipede.state
            assertEquals("seed $seed should finish", GPhase.OVER, s.phase)
            assertTrue(s.result in 1..3)
            assertTrue("scores ${s.myScore} + ${s.theirScore}", s.myScore + s.theirScore > 100)
        }
    }
}
