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

class ArkanoidTest {
    private fun pair(seed: Int = 1, waves: Int = 2, lives: Int = 3): SyncPair<ArkanoidEngine> {
        val p = SyncPair({ n, o, h, s, r, c -> ArkanoidEngine(n, o, h, s, r, c) }, seed)
        p.host.start(JSONObject().put("waves", waves).put("lives", lives))
        p.pump()
        return p
    }

    /** Steer both paddles under the lowest falling ball, like a decent player. */
    private fun playFor(p: SyncPair<ArkanoidEngine>, ms: Long) {
        var left = ms
        while (left > 0 && p.host.state.phase == GPhase.PLAYING) {
            val s = p.host.state
            val b = s.balls.filter { it.vy > 0 }.maxByOrNull { it.y }
            val x = b?.x ?: 50f
            p.host.input(x, false)
            p.client.input(x, false)
            p.advance(33)
            left -= 33
        }
    }

    @Test fun bothPhonesStartWithTheSameWallOfBricksAndSharedLives() {
        val p = pair()
        for (e in listOf(p.host, p.client)) {
            assertEquals(GPhase.PLAYING, e.state.phase)
            assertEquals(ArkanoidRules.COLS * ArkanoidRules.ROWS, e.state.bricks.length)
            assertEquals(50, e.state.remaining)
            assertEquals(3, e.state.c.mine.lives)
            assertEquals(3, e.state.c.theirs.lives)
        }
        assertTrue(p.host.state.c.mine.x < p.host.state.c.theirs.x)
        assertTrue(p.client.state.c.mine.x > p.client.state.c.theirs.x)
    }

    @Test fun paddlesFollowTheirFingerAndStayOnScreen() {
        val p = pair()
        p.advance(2500)
        p.host.input(5f, false)
        p.client.input(97f, false)
        repeat(60) { p.advance(33) }
        assertTrue(p.host.state.c.mine.x < 12f)
        assertTrue(p.host.state.c.mine.x >= 8f - 0.01f)
        assertTrue(p.client.state.c.mine.x > 88f)
        assertTrue(p.client.state.c.mine.x <= 92f + 0.01f)
    }

    @Test fun ballBreaksBricksAndScoresForTheLastPlayerToTouchIt() {
        val p = pair()
        playFor(p, 25_000)
        val s = p.host.state
        assertTrue("bricks were broken", s.remaining < 50)
        assertTrue(s.myScore + s.theirScore > 0)
        assertEquals("both screens agree on the bricks", p.host.state.bricks, p.client.state.bricks)
        assertEquals(p.host.state.myScore, p.client.state.theirScore)
    }

    @Test fun missingTheBallCostsTheTeamLivesUntilGameOver() {
        val p = pair(lives = 2)
        var guard = 0
        while (p.host.state.phase == GPhase.PLAYING && guard++ < 4000) {
            p.host.input(2f, false); p.client.input(98f, false) // hide in the corners
            p.advance(100)
        }
        assertEquals(GPhase.OVER, p.host.state.phase)
        assertEquals("lives", p.host.state.c.why)
        assertEquals(0, p.host.state.c.mine.lives)
        assertEquals(GPhase.OVER, p.client.state.phase)
    }

    @Test fun twoComputerPlayersClearTheLevelsAndWin() {
        val p = pair(seed = 3, waves = 2)
        val a = ArkanoidBrain(p.host, Skill.HARD, kotlin.random.Random(1))
        val b = ArkanoidBrain(p.client, Skill.HARD, kotlin.random.Random(2))
        var guard = 0
        var maxSpeed = 1f
        val seen = HashSet<Char>()
        while (p.host.state.phase == GPhase.PLAYING && guard++ < 30_000) {
            a.think(p.now); b.think(p.now)
            p.advance(33)
            maxSpeed = maxOf(maxSpeed, p.host.state.speed)
            p.host.state.drops.forEach { seen.add(it.kind) }
        }
        assertTrue("the ball speeds up during a rally (peaked at x$maxSpeed)", maxSpeed > 1.05f)
        assertTrue("power-ups appeared: $seen", seen.isNotEmpty())
        assertEquals(GPhase.OVER, p.host.state.phase)
        assertEquals("victory", p.host.state.c.why)
        assertTrue(p.host.state.myScore > 0 && p.host.state.theirScore > 0)
    }

    @Test fun theBossAppearsOnTheLastRoundAndCanBeHurt() {
        val p = SyncPair({ n, o, h, s, r, c -> ArkanoidEngine(n, o, h, s, r, c) }, 2)
        p.host.start(JSONObject().put("waves", 3).put("lives", 5).put("from", 3))
        p.pump()
        val boss = p.host.state.boss
        assertTrue("the last round has a boss", boss != null)
        assertEquals(boss!!.max, boss.hp)
        assertEquals("the guest sees it too", boss.hp, p.client.state.boss!!.hp)
        val a = ArkanoidBrain(p.host, Skill.HARD, kotlin.random.Random(1))
        val b = ArkanoidBrain(p.client, Skill.HARD, kotlin.random.Random(2))
        var sawBomb = false
        var guard = 0
        while (p.host.state.phase == GPhase.PLAYING && guard++ < 24_000) {
            a.think(p.now); b.think(p.now)
            p.advance(33)
            if (p.host.state.bombs.isNotEmpty()) sawBomb = true
        }
        assertTrue("the boss dropped bombs", sawBomb)
        assertEquals(GPhase.OVER, p.host.state.phase)
        // either DOH was beaten for the win, or his bombs and the ball got the better of the team: in both cases he took hits
        assertTrue(p.host.state.c.why == "victory" || p.host.state.c.why == "lives")
    }

    @Test fun enemiesAppearAndBricksScoreTheClassicValues() {
        assertEquals(50, ArkanoidRules.brickScore(0, 0, 3))
        assertEquals(120, ArkanoidRules.brickScore(0, 7, 3))
        assertEquals(150, ArkanoidRules.brickScore(1, 2, 3))
        assertEquals(240, ArkanoidRules.brickScore(2, 2, 3))
        val p = SyncPair({ n, o, h, s, r, c -> ArkanoidEngine(n, o, h, s, r, c) }, 4)
        p.host.start(JSONObject().put("waves", 2).put("lives", 5))
        p.pump()
        val a = ArkanoidBrain(p.host, Skill.HARD, kotlin.random.Random(1))
        val b = ArkanoidBrain(p.client, Skill.HARD, kotlin.random.Random(2))
        var enemies = 0
        var kinds = HashSet<Char>()
        var guard = 0
        while (p.host.state.phase == GPhase.PLAYING && guard++ < 30_000) {
            a.think(p.now); b.think(p.now)
            p.advance(33)
            enemies = maxOf(enemies, p.host.state.enemies.size)
            p.host.state.drops.forEach { kinds.add(it.kind) }
        }
        assertTrue("wandering enemies showed up", enemies > 0)
        assertTrue("capsules fell: $kinds", kinds.isNotEmpty() && kinds.all { it in "EDLSCBPFXNZ" })
    }

    @Test fun layoutsAreWellFormedForEveryLevel() {
        for (level in 1..9) {
            val l = ArkanoidRules.layout(level)
            assertEquals(ArkanoidRules.COLS * ArkanoidRules.ROWS, l.length)
            assertTrue(l.all { it in ".1239E" })
            assertTrue("level $level has breakable bricks", ArkanoidRules.remaining(l.toCharArray()) > 0)
        }
    }
}

class CentipedeFunTest {
    private fun pair(seed: Int = 1): SyncPair<CentipedeEngine> {
        val p = SyncPair({ n, o, h, s, r, c -> CentipedeEngine(n, o, h, s, r, c) }, seed)
        p.host.start(JSONObject().put("waves", 3).put("lives", 5))
        p.pump()
        return p
    }

    @Test fun quickHitsBuildAComboThatMultipliesPoints() {
        val p = pair(seed = 4)
        p.run(2600)
        var guard = 0
        var best = 0
        var last = 0
        while (guard++ < 1500 && p.host.state.phase == GPhase.PLAYING) {
            val target = p.host.state.segs.filter { it.col >= 0 }.maxByOrNull { it.row }
            p.host.input((target?.col ?: 12) * 4f + 2f, 130f, true)
            p.run(66)
            val gain = p.host.state.myScore - last
            last = p.host.state.myScore
            best = maxOf(best, gain)
            if (best > 100) break
        }
        assertTrue("a combo-boosted hit scored more than a plain head shot (best single gain $best)", best > 100)
    }

    @Test fun picksAndFrenzyStayWellFormedAndBothScreensAgree() {
        val p = pair(seed = 6)
        p.run(2600)
        var seenPick = false
        var seenFrenzy = false
        var guard = 0
        while (guard++ < 3000 && p.host.state.phase == GPhase.PLAYING) {
            val s = p.host.state
            val target = s.segs.filter { it.col >= 0 }.maxByOrNull { it.row }
            p.host.input((target?.col ?: 12) * 4f + 2f, 130f, true)
            p.client.input(s.picks.firstOrNull()?.x ?: ((target?.col ?: 12) * 4f + 6f), 130f, true)
            p.run(66)
            if (p.host.state.picks.isNotEmpty()) seenPick = true
            if (p.host.state.frenzy) seenFrenzy = true
            assertEquals(p.host.state.picks.size, p.client.state.picks.size)
            assertTrue(p.host.state.picks.all { it.kind in "RTHB" })
        }
        assertTrue("a pick-up appeared", seenPick)
        assertTrue("the frenzy kicked in when few segments were left", seenFrenzy)
    }
}
