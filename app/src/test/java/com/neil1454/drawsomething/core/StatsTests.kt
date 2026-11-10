package com.neil1454.drawsomething.core

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class StatsTest {
    private fun win(kind: GameKind, mine: Int, theirs: Int) = Outcome(kind, mine, theirs, 1)

    @Test fun aWinAndALossAreRecordedForBothPlayers() {
        val b = StatsBook()
        b.record("Neil", "Teri", win(GameKind.DROP, 1, 0))
        val board = b.board(GameKind.DROP)
        assertEquals(listOf("Neil", "Teri"), board.map { it.name })
        assertEquals(1, board[0].won); assertEquals(0, board[0].lost)
        assertEquals(1, board[1].lost)
        assertEquals(100, board[0].winPercent)
        assertTrue(b.board(GameKind.SEA).isEmpty())
    }

    @Test fun mostWinsRanksFirstAndDrawsCountAsDraws() {
        val b = StatsBook()
        repeat(3) { b.record("Teri", "Neil", win(GameKind.QUICK, 3, 1)) }
        b.record("Neil", "Teri", win(GameKind.QUICK, 3, 2))
        b.record("Neil", "Teri", Outcome(GameKind.QUICK, 2, 2, 3))
        val board = b.board(GameKind.QUICK)
        assertEquals("Teri", board[0].name)
        assertEquals(3, board[0].won)
        assertEquals(1, board[0].lost)
        assertEquals(1, board[0].drawn)
        assertEquals(5, board[0].played)
    }

    @Test fun dominatorCombinesEveryGame() {
        val b = StatsBook()
        // Neil wins lots of different games; Teri wins one game many times
        for (k in listOf(GameKind.DROP, GameKind.FLIP, GameKind.HILO, GameKind.DICE)) b.record("Neil", "Teri", win(k, 50, 10))
        repeat(3) { b.record("Teri", "Neil", win(GameKind.MEMORY, 8, 2)) }
        val dom = b.dominator()
        assertEquals("Neil", dom[0].name)
        assertEquals(7, dom[0].played)
        assertEquals(4, dom[0].won)
        assertTrue(dom[0].rating > dom[1].rating)
        // Neil: four wins (100 + 5 each) and three losses (10 each)
        assertEquals(4 * 105 + 3 * 10, dom[0].rating)
    }

    @Test fun ratingFormula() {
        assertEquals(100, StatsRules.rating(0, 1))
        assertEquals(125, StatsRules.rating(5000, 1)) // bonus is capped
        assertEquals(40, StatsRules.rating(0, 3))
        assertEquals(12, StatsRules.rating(20, 2))
    }

    @Test fun computerGamesOnlyCreditThePlayer() {
        val b = StatsBook()
        b.record("Neil", "Computer", win(GameKind.SEA, 5, 0), recordOpponent = false)
        assertEquals(listOf("Neil"), b.dominator().map { it.name })
    }

    @Test fun namesIgnoreCaseAndSpaces() {
        val b = StatsBook()
        b.record("neil", "Teri", win(GameKind.DROP, 1, 0))
        b.record("  NEIL ", "Teri", win(GameKind.DROP, 1, 0))
        assertEquals(1, b.board(GameKind.DROP).count { it.name.equals("neil", true) })
        assertEquals(2, b.board(GameKind.DROP).first { it.name.equals("neil", true) }.won)
    }

    @Test fun savesAndLoadsAndSurvivesRubbish() {
        val b = StatsBook()
        b.record("Neil", "Teri", win(GameKind.HOLIDAY, 400, 250))
        b.record("Neil", "Teri", Outcome(GameKind.MINES, 30, 30, 3))
        val again = StatsBook.fromJson(b.toJson())
        assertEquals(b.dominator().map { it.name to it.rating }, again.dominator().map { it.name to it.rating })
        assertEquals(2, again.recent.size)
        assertEquals(GameKind.MINES, again.recent[0].kind)
        for (junk in listOf(null, "", "garbage", "{}", """{"p":[{"n":"x","g":{"DROP":[1]}}]}""", """{"p":"no","r":5}""", "[".repeat(5000))) {
            assertNotNull(StatsBook.fromJson(junk))
        }
        assertTrue(StatsBook.fromJson("garbage").isEmpty())
    }

    @Test fun recentGamesAreCappedAndNewestFirst() {
        val b = StatsBook()
        repeat(60) { b.record("Neil", "Teri", win(GameKind.DICE, it, 0), at = it.toLong()) }
        assertEquals(StatsRules.MAX_RECENT, b.recent.size)
        assertEquals(59L, b.recent[0].at)
    }

    @Test fun cannotPlayYourself() {
        val b = StatsBook()
        b.record("Neil", "neil", win(GameKind.DROP, 1, 0))
        assertTrue(b.isEmpty())
    }

    @Test fun outcomesComeFromFinishedGameStates() {
        assertNull(Outcomes.of(DropState("a", "b", GPhase.PLAYING, result = 0)))
        assertEquals(1, Outcomes.of(DropState("a", "b", GPhase.OVER, result = 1))!!.result)
        assertEquals(3, Outcomes.of(FlipState("a", "b", GPhase.OVER, myCount = 32, theirCount = 32, result = 3))!!.result)
        val q = Outcomes.of(QuickState("a", "b", GPhase.OVER, myScore = 1, theirScore = 3, result = 2))!!
        assertEquals(GameKind.QUICK, q.kind); assertEquals(2, q.result); assertEquals(1, q.myPoints)
        val h = Outcomes.of(HolidayState("a", "b", GPhase.OVER, myScore = 500, theirScore = 300, result = 1))!!
        assertEquals(GameKind.HOLIDAY, h.kind); assertEquals(500, h.myPoints)
        assertNull(Outcomes.of("nonsense"))
        assertNull(Outcomes.of(null))
    }
}

class SynthTest {
    @Test fun everySoundRendersAndIsAudibleButNotClipped() {
        for (s in Sound.values()) {
            val pcm = Synth.render(s)
            assertTrue("$s should have samples", pcm.size > Synth.RATE / 50)
            val peak = pcm.maxOf { kotlin.math.abs(it.toInt()) }
            assertTrue("$s is silent", peak > 2000)
            assertTrue("$s is too loud", peak <= 32767)
            assertTrue("$s should be under 1.2 seconds", Synth.durationMs(s) < 1200)
        }
    }

    @Test fun soundsAreRepeatable() {
        assertTrue(Synth.render(Sound.EXPLODE).contentEquals(Synth.render(Sound.EXPLODE)))
    }
}

class WinStoryTest {
    // a 7x6 board: cell = row * 7 + col, row 0 is the top
    @Test fun describesAHorizontalLine() {
        val line = listOf(5 * 7 + 2, 5 * 7 + 3, 5 * 7 + 4, 5 * 7 + 5) // bottom row, columns 3-6
        assertEquals("Four in a row across row 1 from the bottom, columns 3 to 6", WinStory.dropLine(line, 7, 6))
    }

    @Test fun describesAVerticalLine() {
        val line = listOf(2 * 7 + 4, 3 * 7 + 4, 4 * 7 + 4, 5 * 7 + 4)
        assertEquals("Four in a row straight up column 5, from row 1 to row 4 (counting from the bottom)", WinStory.dropLine(line, 7, 6))
    }

    @Test fun describesBothDiagonals() {
        val down = listOf(0 * 7 + 0, 1 * 7 + 1, 2 * 7 + 2, 3 * 7 + 3)
        assertTrue(WinStory.dropLine(down, 7, 6).contains("sloping down to the right"))
        val left = listOf(0 * 7 + 3, 1 * 7 + 2, 2 * 7 + 1, 3 * 7 + 0)
        assertTrue(WinStory.dropLine(left, 7, 6).contains("sloping down to the left"))
        assertTrue(WinStory.dropLine(left, 7, 6).startsWith("Four in a row diagonally"))
    }

    @Test fun fiveInARowAndJunkAreHandled() {
        val five = (0..4).map { 3 * 9 + 2 + it }
        assertTrue(WinStory.dropLine(five, 9, 7).startsWith("Five in a row across row 4"))
        assertEquals("", WinStory.dropLine(emptyList(), 7, 6))
        assertEquals("", WinStory.dropLine(listOf(1000, 2000), 7, 6))
        assertEquals("", WinStory.dropLine(listOf(1, 2), 0, 0))
    }

    @Test fun marginsReadNaturally() {
        assertEquals("by 12 points", WinStory.by(50, 38, "point"))
        assertEquals("by 1 point", WinStory.by(7, 6, "point"))
        assertEquals("You", WinStory.who(1, "Teri"))
        assertEquals("Teri", WinStory.who(2, "Teri"))
    }
}
