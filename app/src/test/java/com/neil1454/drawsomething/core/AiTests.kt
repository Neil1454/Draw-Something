package com.neil1454.drawsomething.core

import org.json.JSONObject
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test
import kotlin.random.Random

class AiTest {
    // ------------------------------------------------------------------ whole games, computer vs computer

    private fun runUntil(d: Duel, maxMs: Long, done: () -> Boolean) {
        var t = 0L
        while (t < maxMs && !done()) { d.advance(100, 50); t += 100 }
    }

    @Test fun dropFourFinishesAtEverySkill() {
        for (skill in Skill.values()) {
            val d = Duel(3, skill, skill)
            d.host.drop.start(JSONObject().put("size", 0).put("power", false))
            runUntil(d, 600_000) { d.host.drop.state.phase == GPhase.OVER }
            assertEquals("$skill", GPhase.OVER, d.host.drop.state.phase)
            assertEquals(GPhase.OVER, d.guest.drop.state.phase)
            assertEquals(d.host.drop.state.grid, d.guest.drop.state.grid)
        }
    }

    @Test fun flipFightFinishesAtEverySkill() {
        for (skill in Skill.values()) for (size in 0..1) {
            val d = Duel(4 + size, skill, skill)
            d.host.flip.start(JSONObject().put("size", size))
            runUntil(d, 900_000) { d.host.flip.state.phase == GPhase.OVER }
            assertEquals(GPhase.OVER, d.host.flip.state.phase)
            assertEquals(d.host.flip.state.myCount, d.guest.flip.state.theirCount)
        }
    }

    @Test fun memoryDiceAndQuickFinish() {
        val m = Duel(5)
        m.host.memory.start(JSONObject().put("size", 0))
        runUntil(m, 900_000) { m.host.memory.state.phase == GPhase.OVER }
        assertEquals(GPhase.OVER, m.host.memory.state.phase)

        val dice = Duel(6)
        dice.host.dice.start(JSONObject().put("target", 0).put("two", true))
        runUntil(dice, 900_000) { dice.host.dice.state.phase == GPhase.OVER }
        assertEquals(GPhase.OVER, dice.host.dice.state.phase)
        assertEquals(GPhase.OVER, dice.guest.dice.state.phase)

        val q = Duel(7)
        q.host.quick.start(JSONObject().put("match", 0).put("fake", true))
        runUntil(q, 900_000) { q.host.quick.state.phase == GPhase.OVER }
        assertEquals(GPhase.OVER, q.host.quick.state.phase)
        assertEquals(GPhase.OVER, q.guest.quick.state.phase)
    }

    @Test fun bombFindAndSeaBattleFinish() {
        for (size in MineSize.values()) {
            val d = Duel(8 + size.ordinal)
            d.host.mines.start(size)
            runUntil(d, 3_600_000) { d.host.mines.state.phase == MinesPhase.OVER }
            assertEquals("$size", MinesPhase.OVER, d.host.mines.state.phase)
            assertEquals(d.host.mines.state.myScore, d.guest.mines.state.theirScore)
        }
        for (size in SeaSize.values()) {
            val d = Duel(20 + size.ordinal)
            d.host.sea.start(size)
            runUntil(d, 3_600_000) { d.host.sea.state.phase == SeaPhase.OVER }
            assertEquals("$size", SeaPhase.OVER, d.host.sea.state.phase)
            assertTrue(d.host.sea.state.iWon != d.guest.sea.state.iWon)
        }
    }

    @Test fun soloDrawSomethingKeepsYouAsTheDrawerAndTheComputerGuesses() {
        val d = Duel(9, Skill.NORMAL, Skill.HARD)
        d.host.draw.agreeRules()
        d.advance(500)
        d.host.draw.hostStart(2, 60, Difficulty.EASY, solo = true)
        d.advance(300)
        // round 1: host (the human) is the drawer
        assertTrue(d.host.draw.state.iAmDrawer)
        assertFalse(d.guest.draw.state.iAmDrawer)
        d.host.draw.chooseWord(d.host.draw.state.options.first())
        d.advance(300)
        d.host.draw.beginStroke(0, 0.01f, 0.1f, 0.1f); d.host.draw.extendStroke(0.5f, 0.5f); d.host.draw.endStroke()
        // wait for the computer to "guess", then judge it
        var guard = 0
        while (d.host.draw.state.pendingGuess == null && guard++ < 1000) d.advance(100)
        val guess = d.host.draw.state.pendingGuess
        assertNotNull(guess)
        d.host.draw.judge(guess == d.host.draw.state.word!!.text)
        guard = 0
        while (d.host.draw.state.phase != Phase.RESULT && guard++ < 1000) {
            d.host.draw.state.pendingGuess?.let { g -> d.host.draw.judge(g == d.host.draw.state.word!!.text) }
            d.advance(100)
        }
        assertEquals(Phase.RESULT, d.host.draw.state.phase)
        d.host.draw.ready()
        guard = 0
        while (d.host.draw.state.phase == Phase.RESULT && guard++ < 200) d.advance(100)
        // round 2: still the human drawing (the roles do NOT swap in solo)
        assertEquals(1, d.host.draw.state.round)
        assertTrue(d.host.draw.state.iAmDrawer)
        assertEquals(Phase.PICKING, d.host.draw.state.phase)
        assertTrue(d.host.draw.state.solo && d.guest.draw.state.solo)
    }

    // ------------------------------------------------------------------ how good is it?

    private fun randomOpponentWins(play: (Random) -> Int): Int = (0 until 40).count { play(Random(it)) == 1 }

    @Test fun hardDropFourBeatsARandomPlayerAlmostAlways() {
        var computerWins = 0
        repeat(30) { seed ->
            val p = SyncPair({ n, o, h, s, r, c -> DropEngine(n, o, h, s, r, c) }, seed)
            p.host.start(JSONObject().put("size", 0)); p.pump()
            val rnd = Random(seed)
            var guard = 0
            while (p.host.state.phase == GPhase.PLAYING && guard++ < 100) {
                val engine = if (p.host.state.myTurn) p.host else p.client
                if (engine === p.client) {
                    engine.drop(DropAi.choose(engine.state, Skill.HARD, rnd), 0)
                } else {
                    engine.drop(rnd.nextInt(7), 0)
                }
                p.pump()
            }
            if (p.client.state.result == 1) computerWins++
        }
        assertTrue("won $computerWins of 30", computerWins >= 27)
    }

    @Test fun dropFourTakesAWinAndBlocksALoss() {
        // host (1) has three in the bottom row at columns 0,1,2: the computer (2) must block at column 3
        val p = SyncPair({ n, o, h, s, r, c -> DropEngine(n, o, h, s, r, c) }, 31)
        p.host.start(JSONObject().put("size", 0)); p.pump()
        fun move(col: Int) { (if (p.host.state.myTurn) p.host else p.client).drop(col, 0); p.pump() }
        // make sure the host moves first by retrying seeds: instead just play accordingly
        val hostFirst = p.host.state.myTurn
        if (hostFirst) { move(0); move(6); move(1); move(6); move(2) } else { move(6); move(0); move(6); move(1); move(6); move(2) }
        assertTrue(p.client.state.myTurn) // now it is the computer's (client's) turn
        if (hostFirst) {
            // the host has 0,1,2 along the bottom: the computer must block column 3
            assertEquals(3, DropAi.choose(p.client.state, Skill.NORMAL, Random(1)))
        } else {
            // the computer built three in column 6 itself: it takes the win instead
            assertEquals(6, DropAi.choose(p.client.state, Skill.NORMAL, Random(1)))
        }
    }

    @Test fun hardFlipFightBeatsRandomMostOfTheTime() {
        var wins = 0
        repeat(16) { seed ->
            val p = SyncPair({ n, o, h, s, r, c -> FlipEngine(n, o, h, s, r, c) }, seed)
            p.host.start(JSONObject().put("size", 0)); p.pump()
            val rnd = Random(seed)
            var guard = 0
            while (p.host.state.phase == GPhase.PLAYING && guard++ < 200) {
                if (p.client.state.myTurn) p.client.place(FlipAi.choose(p.client.state, Skill.HARD, rnd))
                else p.host.place(p.host.state.legal.random(rnd))
                p.pump()
            }
            if (p.client.state.result == 1) wins++
        }
        assertTrue("won $wins of 16", wins >= 13)
    }

    @Test fun flipAiLikesCornersAndAvoidsGivingThemAway() {
        val w = FlipAi.weights(8)
        assertTrue(w[0] > 50 && w[7] > 50 && w[56] > 50 && w[63] > 50)
        assertTrue(w[9] < 0 && w[1] < 0 && w[8] < 0)
    }

    @Test fun bombFindSolverNeverStepsOnABombItCanSee() {
        // 4x1 strip view:  1 . . .   ->  the '1' at 0 has only one hidden neighbour (index 1): it MUST be a bomb
        val view = "1..." + "...."
        // a 4x2 board: row0 = "1 . . .", row1 = ". . . ."  -> square 0's hidden neighbours are 1, 4, 5 (three), not forced
        val unforced = MinesAi.choose(view, 4, 2, 2, Skill.HARD, Random(1))
        assertNotNull(unforced)

        // a forced case: corner '1' with two neighbours already revealed as safe numbers, leaving one hidden
        //  col: 0 1 2        row0: 1 2 .
        //                    row1: 1 1 .
        val forced = "12." + "11."
        // square (0,0)=1 touches (1,0)=2,(0,1)=1,(1,1)=1: all revealed -> no info; (1,0)=2 touches (2,0),(2,1) hidden -> both mines
        // (1,1)=1 touches (2,0),(2,1),(0,..): one of them... so with the two-mine deduction there is no safe hidden square.
        // The solver must still return one of the (only) hidden squares.
        val pick = MinesAi.choose(forced, 3, 2, 2, Skill.HARD, Random(2))
        assertTrue(pick == 2 || pick == 5)

        // an obviously safe square: a '0' needs nothing; a '1' with its bomb already exploded next to it makes the rest safe
        //   X 1 .
        //   . . .   (X at 0 is an exploded bomb; '1' at 1 touches it, so every other neighbour of 1 is safe)
        val v = "X1." + "..."
        repeat(20) { seed ->
            val c = MinesAi.choose(v, 3, 2, 3, Skill.HARD, Random(seed))!!
            assertTrue("picked $c", c in listOf(2, 3, 4, 5)) // never index 3?? (3 is below X, touches 1 -> safe)
        }
    }

    @Test fun bombFindSolverFindsForcedSafeSquares() {
        // 1 . .      the '1' at (0,0) touches hidden (1,0),(0,1),(1,1); the '1' at (1,0)... build an unambiguous case:
        // row0: F 1 .     F = flagged bomb next to the '1' at index 1 -> its other neighbours (2, 3?, 4, 5) are safe
        // row1: . . .
        val view = "F1." + "..."
        val safe = setOf(2, 3, 4, 5)
        repeat(30) { seed ->
            val c = MinesAi.choose(view, 3, 2, 1, Skill.HARD, Random(seed))!!
            assertTrue("picked $c", c in safe)
        }
        assertNull(MinesAi.choose("123" + "456", 3, 2, 1, Skill.HARD, Random(1))) // nothing hidden
    }

    @Test fun seaBattleHuntsNearAHitAndAvoidsSunkAndMissedSquares() {
        val p = SeaPair(40, SeaSize.QUICK).battle()
        val a = p.shooter
        // fake a position: one unsunk hit in the middle, a miss and a sunk ship elsewhere
        val s = a.state
        val w = s.size.w
        val grid = CharArray(w * s.size.h) { '.' }
        grid[3 * w + 3] = 'x'
        grid[0] = 'o'
        grid[1] = '#'; grid[2] = '#'
        val fake = s.copy(enemyGrid = String(grid), bombLeft = false)
        repeat(20) { seed ->
            val (weapon, x, y) = SeaAi.choose(fake, Skill.HARD, Random(seed))
            assertEquals(SeaWeapon.SHOT, weapon)
            val idx = y * w + x
            assertEquals('.', grid[idx])
            // right beside the wounded ship (a hit means a ship runs through it)
            assertTrue("fired at ($x,$y)", kotlin.math.abs(x - 3) + kotlin.math.abs(y - 3) == 1)
        }
    }

    @Test fun diceComputerBanksSensibly() {
        val d = Duel(50)
        d.host.dice.start(JSONObject().put("target", 1).put("two", false))
        runUntil(d, 600_000) { d.host.dice.state.phase == GPhase.OVER }
        assertEquals(GPhase.OVER, d.host.dice.state.phase)
        // somebody banked a decent amount at some point: final winner reached the target legitimately
        val winner = if (d.host.dice.state.result == 1) d.host.dice.state.myScore else d.host.dice.state.theirScore
        assertTrue(winner >= 100)
    }

    @Test fun quickDrawComputerReactsSlowerOnEasyThanHard() {
        fun averageComputerMs(skill: Skill): Double {
            val times = ArrayList<Int>()
            repeat(6) { seed ->
                val d = Duel(60 + seed, Skill.NORMAL, skill)
                d.host.quick.start(JSONObject().put("match", 0).put("fake", false))
                var guard = 0
                while (d.host.quick.state.phase == GPhase.PLAYING && guard++ < 2000) {
                    d.advance(100, 50)
                    val ms = d.guest.quick.state.myMs
                    if (ms > 0) times += ms
                }
            }
            return times.average()
        }
        val easy = averageComputerMs(Skill.EASY)
        val hard = averageComputerMs(Skill.HARD)
        assertTrue("easy $easy hard $hard", easy > hard + 100)
    }
}
