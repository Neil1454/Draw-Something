package com.neil1454.drawsomething.core

import org.json.JSONObject
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test
import kotlin.random.Random

class KalahRulesTest {
    @Test fun startingBoardHasSeedsInEveryPit() {
        val b = KalahRules.start(4)
        assertEquals(48, b.sum())
        assertEquals(0, b[6]); assertEquals(0, b[13])
        assertTrue((0..5).all { b[it] == 4 } && (7..12).all { b[it] == 4 })
    }

    @Test fun sowingGoesCounterClockwiseAndSkipsTheRivalsStore() {
        val b = IntArray(14); b[5] = 9
        val mv = KalahRules.apply(b, 1, 5)
        assertEquals(listOf(6, 7, 8, 9, 10, 11, 12, 0, 1), mv.path) // 13 (their store) is skipped
        assertEquals(0, mv.board[13])
        assertEquals(9, mv.board.sum())
        // the ninth seed landed in the empty pit 1 with a seed opposite (pit 11): that is a capture of 2, one dropped in the store before it
        assertEquals(11, mv.capturedFrom)
        assertEquals(3, mv.board[6])
    }

    @Test fun endingInYourOwnStoreGivesAnotherTurn() {
        val b = IntArray(14) { 4 }; b[6] = 0; b[13] = 0
        val mv = KalahRules.apply(b, 1, 2) // pit 2 holds 4: 3,4,5,6
        assertTrue(mv.extraTurn)
        assertEquals(1, mv.nextPlayer)
        val other = KalahRules.apply(b, 1, 0)
        assertFalse(other.extraTurn)
        assertEquals(2, other.nextPlayer)
    }

    @Test fun landingInAnEmptyPitCapturesTheOppositeSeeds() {
        val b = IntArray(14); b[0] = 2; b[2] = 0; b[10] = 5; b[3] = 1; b[12] = 1
        // pit 0 has 2 seeds: lands in 1 then 2. Pit 2 is empty, opposite is 10 (5 seeds)
        val mv = KalahRules.apply(b, 1, 0)
        assertEquals(10, mv.capturedFrom)
        assertEquals(6, mv.capturedCount)
        assertEquals(6, mv.board[6])
        assertEquals(0, mv.board[2]); assertEquals(0, mv.board[10])
    }

    @Test fun whenOneSideIsEmptyTheOtherSideKeepsTheirSeedsAndTheGameEnds() {
        val b = IntArray(14); b[5] = 1; b[8] = 3; b[10] = 2
        val mv = KalahRules.apply(b, 1, 5) // the last seed goes into the store: my side is now empty
        assertTrue(mv.over)
        assertEquals(1, mv.board[6])
        assertEquals(5, mv.board[13])
        assertEquals(6, mv.board.sum())
        assertEquals(2, KalahRules.winner(mv.board))
    }

    @Test fun seedsAreAlwaysConservedOverRandomGames() {
        val r = Random(5)
        repeat(60) {
            var b = KalahRules.start(KalahRules.seedsFor(it % 3))
            val total = b.sum()
            var p = 1
            var guard = 0
            while (guard++ < 400) {
                val legal = KalahRules.legal(b, p)
                if (legal.isEmpty()) break
                val mv = KalahRules.apply(b, p, legal[r.nextInt(legal.size)])
                b = mv.board
                assertEquals(total, b.sum())
                if (mv.over) break
                p = mv.nextPlayer
            }
        }
    }

    @Test fun theSearchTakesAFreeExtraTurnAndBeatsRandom() {
        val b = IntArray(14) { 4 }; b[6] = 0; b[13] = 0
        assertEquals(2, KalahRules.best(b, 1, 3, Random(1)))  // pit 2 ends in the store
        var wins = 0
        for (g in 0 until 20) {
            var board = KalahRules.start(4)
            var p = 1
            val r = Random(g)
            var guard = 0
            while (guard++ < 400) {
                val legal = KalahRules.legal(board, p)
                if (legal.isEmpty()) break
                val pit = if (p == 1) KalahRules.best(board, 1, 4, r) else legal[r.nextInt(legal.size)]
                val mv = KalahRules.apply(board, p, pit)
                board = mv.board
                if (mv.over) break
                p = mv.nextPlayer
            }
            if (KalahRules.winner(board) == 1) wins++
        }
        assertTrue("the search won $wins of 20", wins >= 16)
    }
}

class KalahGameTest {
    private fun pair(seed: Int = 1): SyncPair<KalahEngine> {
        val p = SyncPair({ n, o, h, s, r, c -> KalahEngine(n, o, h, s, r, c) }, seed)
        p.host.start(JSONObject().put("seeds", 1))
        p.pump()
        return p
    }

    @Test fun bothSeeTheirOwnPitsAtTheBottomWithTheSameSeeds() {
        val p = pair()
        for (e in listOf(p.host, p.client)) {
            assertEquals(GPhase.PLAYING, e.state.phase)
            assertEquals(4, e.state.myPits[0]); assertEquals(4, e.state.theirPits[5])
            assertEquals(0, e.state.myStore)
        }
        assertTrue(p.host.state.myTurn != p.client.state.myTurn)
    }

    @Test fun aMoveIsMirroredAndAnimatableOnBothPhones() {
        val p = pair(seed = 2)
        val mover = if (p.host.state.myTurn) p.host else p.client
        val other = if (mover === p.host) p.client else p.host
        mover.move(2); p.pump()
        assertEquals(0, mover.state.myPits[2])
        assertEquals(0, other.state.theirPits[2])
        assertEquals(mover.state.myStore, other.state.theirStore)
        assertEquals(mover.state.lastPath.size, 4)
        assertTrue(mover.state.lastByMe); assertFalse(other.state.lastByMe)
        // wrong player or an empty pit is ignored
        other.move(0); p.pump()
        assertEquals(1, p.host.state.moves)
    }

    @Test fun twoComputerPlayersFinishAGameWithAWinnerOrDraw() {
        for (seed in 1..5) {
            val p = pair(seed)
            val a = KalahBrain(p.host, Skill.HARD, Random(seed))
            val b = KalahBrain(p.client, Skill.NORMAL, Random(seed + 9))
            var guard = 0
            while (p.host.state.phase == GPhase.PLAYING && guard++ < 6000) { a.think(p.now); b.think(p.now); p.advance(100) }
            assertEquals(GPhase.OVER, p.host.state.phase)
            assertEquals(48, p.host.state.myStore + p.host.state.theirStore)
            val hr = p.host.state.result
            assertEquals(if (hr == 3) 3 else if (hr == 1) 2 else 1, p.client.state.result)
        }
    }
}
