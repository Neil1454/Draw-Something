package com.neil1454.drawsomething.core

import org.json.JSONObject
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import kotlin.random.Random

class NoughtsTest {
    private fun pair(seed: Int = 1, mode: Int = 0, series: Int = 0): SyncPair<NoughtsEngine> {
        val p = SyncPair({ n, o, h, s, r, c -> NoughtsEngine(n, o, h, s, r, c) }, seed)
        p.host.start(JSONObject().put("mode", mode).put("series", series))
        p.pump()
        return p
    }

    private val SyncPair<NoughtsEngine>.mover get() = if (host.state.myTurn) host else client
    private val SyncPair<NoughtsEngine>.waiter get() = if (host.state.myTurn) client else host

    private fun SyncPair<NoughtsEngine>.play(i: Int) { mover.play(i); pump() }

    @Test fun bothPhonesSeeTheSameBoardFromTheirOwnPointOfView() {
        val p = pair()
        val first = p.mover
        p.play(4)
        // the mover sees the mark as theirs-mine swapped on the other phone
        val second = if (first === p.host) p.client else p.host
        assertEquals(1, first.state.board[4])
        assertEquals(2, second.state.board[4])
        assertTrue(second.state.myTurn)
        assertTrue(first.state.iAmCross != second.state.iAmCross)
    }

    @Test fun illegalAndOutOfTurnMovesAreIgnored() {
        val p = pair(2)
        p.play(0)
        p.waiter.play(0); p.pump()               // on top of a mark
        p.mover.play(0); p.pump()
        p.waiter.play(99); p.waiter.play(-3); p.pump()
        assertEquals(1, p.host.state.moves)
        p.waiter.act(JSONObject().put("a", "nonsense"))
        p.waiter.act(JSONObject().put("a", "play"))
        p.pump()
        assertEquals(1, p.host.state.moves)
    }

    @Test fun threeInARowWinsAndShowsTheLine() {
        val p = pair(3)
        val a = p.mover; val b = p.waiter
        // a takes the top row, b plays harmlessly underneath
        a.play(0); p.pump(); b.play(3); p.pump(); a.play(1); p.pump(); b.play(4); p.pump(); a.play(2); p.pump()
        assertEquals(GPhase.OVER, p.host.state.phase)
        assertEquals(1, a.state.result); assertEquals(2, b.state.result)
        assertEquals(listOf(0, 1, 2), a.state.winLine)
    }

    @Test fun aFullGridWithNoLineIsADraw() {
        val p = pair(4)
        // X O X / X O O / O X X  (cross moves first)
        val order = listOf(0, 1, 2, 4, 3, 5, 7, 6, 8)
        for (i in order) p.play(i)
        assertEquals(GPhase.OVER, p.host.state.phase)
        assertEquals(3, p.host.state.result); assertEquals(3, p.client.state.result)
    }

    @Test fun perfectPlayersAlwaysDraw() {
        for (seed in 1..6) {
            val p = pair(seed)
            val a = NoughtsBrain(p.host, Skill.HARD, Random(seed)); val b = NoughtsBrain(p.client, Skill.HARD, Random(seed + 9))
            var g = 0
            while (p.host.state.phase == GPhase.PLAYING && g++ < 800) { a.think(p.now); b.think(p.now); p.advance(100) }
            assertEquals(GPhase.OVER, p.host.state.phase)
            assertEquals("seed $seed", 3, p.host.state.result)
        }
    }

    @Test fun theHardComputerNeverLosesToAnEasyOne() {
        for (seed in 1..8) {
            val p = pair(seed)
            val a = NoughtsBrain(p.host, Skill.HARD, Random(seed)); val b = NoughtsBrain(p.client, Skill.EASY, Random(seed + 9))
            var g = 0
            while (p.host.state.phase == GPhase.PLAYING && g++ < 800) { a.think(p.now); b.think(p.now); p.advance(100) }
            assertNotEquals("seed $seed", 2, p.host.state.result)
        }
    }

    @Test fun aSeriesGoesOnWithTheOtherPlayerStartingTheNextGame() {
        val p = pair(5, series = 1)
        assertEquals(3, p.host.state.rounds)
        val firstCross = p.host.state.iAmCross
        val a = p.mover; val b = p.waiter
        a.play(0); p.pump(); b.play(3); p.pump(); a.play(1); p.pump(); b.play(4); p.pump(); a.play(2); p.pump()
        assertEquals(GPhase.PLAYING, p.host.state.phase)
        assertTrue(p.host.state.roundResult != 0)
        assertEquals(1, if (a.state.roundResult == 1) 1 else 0)
        // nothing can be played while the game result is on show
        a.play(5); p.pump()
        assertEquals(5, p.host.state.moves)
        p.advance(3000)
        assertEquals(2, p.host.state.round)
        assertEquals(0, p.host.state.roundResult)
        assertTrue(p.host.state.board.all { it == 0 })
        assertEquals(!firstCross, p.host.state.iAmCross)
        // the player who won is one up
        assertEquals(1, a.state.myWins)
    }

    @Test fun aSeriesEndsAsSoonAsSomeoneHasTheMajority() {
        val p = pair(6, series = 1)
        val a = NoughtsBrain(p.host, Skill.HARD, Random(1)); val b = NoughtsBrain(p.client, Skill.EASY, Random(2))
        var g = 0
        while (p.host.state.phase == GPhase.PLAYING && g++ < 4000) { a.think(p.now); b.think(p.now); p.advance(100) }
        assertEquals(GPhase.OVER, p.host.state.phase)
        assertTrue(p.host.state.myWins + p.host.state.theirWins + p.host.state.draws in 2..3)
    }

    @Test fun ultimateForcesTheNextGridAndClaimsSmallGrids() {
        val p = pair(7, mode = 1)
        assertEquals(81, p.host.state.board.size)
        val first = p.mover
        p.play(4 * 9 + 2)                      // grid 4, square 2: the other player must play in grid 2
        assertEquals(2, p.host.state.forced); assertEquals(2, p.client.state.forced)
        p.mover.play(5 * 9 + 5); p.pump()      // wrong grid: ignored
        assertEquals(1, p.host.state.moves)
        p.play(2 * 9 + 4)                      // grid 2, square 4: back to grid 4 for the first player
        assertEquals(4, p.host.state.forced)
        assertTrue(first.state.myTurn)
    }

    @Test fun ultimateGamesFinishWithConsistentResults() {
        for (seed in 1..5) {
            val p = pair(seed, mode = 1)
            val a = NoughtsBrain(p.host, Skill.HARD, Random(seed)); val b = NoughtsBrain(p.client, Skill.NORMAL, Random(seed + 7))
            var g = 0
            while (p.host.state.phase == GPhase.PLAYING && g++ < 3000) { a.think(p.now); b.think(p.now); p.advance(100) }
            assertEquals("seed $seed finished", GPhase.OVER, p.host.state.phase)
            val r = p.host.state.result
            assertEquals(when (r) { 1 -> 2; 2 -> 1; else -> 3 }, p.client.state.result)
        }
    }

    @Test fun smallGridRulesAreRight() {
        val b = IntArray(81)
        for (i in 0..2) b[9 + i] = 1             // grid 1 row 0 all mine -> won by 1
        assertEquals(1, NoughtsRules.subResult(b, 1))
        assertEquals(0, NoughtsRules.subResult(b, 0))
        for (i in 0 until 9) b[27 + i] = if (i % 2 == 0) 1 else 2
        b[27 + 4] = 2; b[27 + 2] = 2; b[27 + 6] = 2   // fill grid 3 without a line
        val sub = NoughtsRules.subResults(b)
        assertFalse(NoughtsRules.subResult(b, 3) == 0)
        // a won grid (sub 1) is never forced; a full grid sends you anywhere
        assertEquals(-1, NoughtsRules.forcedFor(sub, 1))
        assertEquals(5, NoughtsRules.forcedFor(sub, 5))
    }

    @Test fun badSnapshotsAreIgnored() {
        val p = pair()
        val before = p.client.state
        p.client.onMessage(GMsg.Snap("NOUGHTS", JSONObject().put("b", "xyz").put("sb", "0")))
        p.client.onMessage(GMsg.Snap("NOUGHTS", JSONObject().put("b", "999999999").put("sb", "000000000")))
        assertEquals(before, p.client.state)
    }
}
