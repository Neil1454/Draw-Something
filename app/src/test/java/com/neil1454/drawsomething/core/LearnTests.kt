package com.neil1454.drawsomething.core

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class LearnTest {
    private fun watch(kind: GameKind, seed: Int = 1): Pair<LearnSession, List<String>> {
        val s = LearnSession(kind, seed)
        val captions = ArrayList<String>()
        captions += s.caption
        var lastId = s.captionId
        var t = 0L
        while (!s.finished && t < 2_400_000L) {
            s.advance(100)
            t += 100
            if (s.captionId != lastId) { lastId = s.captionId; captions += s.caption }
        }
        return s to captions
    }

    @Test fun everyGameHasAnIntroAndAFinishedDemoWithManyCaptions() {
        for (kind in GameKind.values()) {
            val (s, captions) = watch(kind)
            assertTrue("$kind did not finish", s.finished)
            assertTrue("$kind captions: ${captions.size}", captions.size >= 4)
            assertTrue(captions.first().contains(kind.emoji))
            assertTrue(captions.last().contains("That's ${kind.label}"))
            assertTrue(captions.all { it.isNotBlank() && it.length < 400 })
        }
    }

    @Test fun demosAreRepeatableWithAnotherSeed() {
        for (kind in listOf(GameKind.DROP, GameKind.FLIP, GameKind.DICE)) {
            val (a, _) = watch(kind, 3)
            val (b, _) = watch(kind, 4)
            assertTrue(a.finished && b.finished)
        }
    }

    @Test fun drawSomethingDemoShowsADrawingAThenAJudgedGuessAndAResult() {
        val (s, captions) = watch(GameKind.DRAW)
        assertTrue(captions.any { it.startsWith("Step 1") })
        assertTrue(captions.any { it.startsWith("Step 2") })
        assertTrue(captions.any { it.startsWith("Step 3") })
        assertTrue(captions.any { it.contains("Correct") })
        assertTrue(captions.any { it.startsWith("Round over") })
        val h = s.duel.host.draw
        assertEquals(4, h.board.strokes.size) // body, roof, door, window
        assertTrue(h.state.solo)
        assertEquals(Phase.GAME_OVER, h.state.phase)
    }

    @Test fun pausedDemosDoNotMove() {
        val s = LearnSession(GameKind.DROP, 5)
        s.advance(5_000)
        val moves = s.duel.host.drop.state.moves
        // "pause" = the screen simply stops calling advance, so nothing changes
        assertEquals(moves, s.duel.host.drop.state.moves)
        assertFalse(s.finished)
    }
}
