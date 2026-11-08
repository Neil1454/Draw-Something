package com.neil1454.drawsomething.core

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test
import kotlin.random.Random

class ScoringTest {
    private val round = 90_000L

    @Test fun instantGuessIsWorthFullBase() {
        assertEquals(100, Scoring.basePoints(0, round, 1.0))
    }

    @Test fun slowestGuessStillEarnsMinimumShare() {
        assertEquals(30, Scoring.basePoints(round, round, 1.0))
        assertEquals(30, Scoring.basePoints(round * 5, round, 1.0)) // clamped
    }

    @Test fun fasterIsAlwaysWorthMore() {
        var prev = Int.MAX_VALUE
        for (s in 0..90 step 5) {
            val p = Scoring.basePoints(s * 1000L, round, 1.0)
            assertTrue(p <= prev)
            prev = p
        }
    }

    @Test fun difficultyMultiplierApplies() {
        assertEquals(150, Scoring.basePoints(0, round, Difficulty.HARD.multiplier))
        assertEquals(125, Scoring.basePoints(0, round, Difficulty.MEDIUM.multiplier))
    }

    @Test fun penalties() {
        assertEquals(70, Scoring.drawerDelta(100, 2))
        assertEquals(90, Scoring.guesserDelta(100, 2))
        assertEquals(0, Scoring.guesserDelta(10, 9)) // never below zero for the round
        assertEquals(-30, Scoring.drawerDelta(0, 2)) // skipped twice, then timed out
    }
}

class GuessMatcherTest {
    @Test fun matches() {
        assertTrue(GuessMatcher.isMatch("Tree", "tree"))
        assertTrue(GuessMatcher.isMatch("  a tree! ", "tree"))
        assertTrue(GuessMatcher.isMatch("trees", "tree"))
        assertTrue(GuessMatcher.isMatch("ICE CREAM", "ice cream"))
        assertTrue(GuessMatcher.isMatch("icecream", "ice cream"))
        assertFalse(GuessMatcher.isMatch("bush", "tree"))
        assertFalse(GuessMatcher.isMatch("", "tree"))
    }

    @Test fun close() {
        assertTrue(GuessMatcher.isClose("elephent", "elephant")) // one wrong letter
        assertTrue(GuessMatcher.isClose("elphant", "elephant")) // one missing letter
        assertFalse(GuessMatcher.isClose("elefant", "elephant")) // two edits: not close
        assertFalse(GuessMatcher.isClose("elephant", "elephant")) // that's a match, not "close"
        assertFalse(GuessMatcher.isClose("cap", "cat")) // too short to call close
        assertFalse(GuessMatcher.isClose("giraffe", "elephant"))
    }
}

class WordsTest {
    @Test fun wordsAreCleanAndUnique() {
        assertEquals(Words.all.size, Words.all.map { it.text }.toSet().size)
        Words.all.forEach { w -> assertTrue(w.text, w.text.all { it in 'a'..'z' || it == ' ' }) }
        Difficulty.values().forEach { d -> assertTrue(Words.all.count { it.difficulty == d } >= 6) }
    }

    @Test fun fiveOptionsMixedDifficultyNoDuplicates() {
        val opts = Words.pickOptions(Random(1), emptySet())
        assertEquals(5, opts.size)
        assertEquals(5, opts.map { it.text }.toSet().size)
        assertEquals(2, opts.count { it.difficulty == Difficulty.EASY })
        assertEquals(2, opts.count { it.difficulty == Difficulty.MEDIUM })
        assertEquals(1, opts.count { it.difficulty == Difficulty.HARD })
    }

    @Test fun usedWordsAreExcluded() {
        val used = Words.all.map { it.text }.take(30).toSet()
        repeat(50) {
            val opts = Words.pickOptions(Random(it), used)
            assertTrue(opts.none { o -> o.text in used })
            assertTrue(Words.random(Random(it), used).text !in used)
        }
    }

    @Test fun patternHidesLettersKeepsSpaces() {
        assertEquals("___ _____", Words.pattern("ice cream"))
    }
}

class MsgTest {
    private fun roundTrip(m: Msg) = Msg.decode(m.encode())

    @Test fun roundTrips() {
        val all = listOf(
            Msg.Setup(6, 90, true), Msg.Choosing(3), Msg.RoundStart(2, "Animal", "___"),
            Msg.StrokePart(4, 0xFFE53935.toInt(), 0.013f, listOf(0.1f, 0.25f, 0.5f, 1f)),
            Msg.Undo, Msg.Clear, Msg.Skipped, Msg.Guess("a tree"), Msg.GuessDeclined("bush"),
            Msg.RoundResult(1, "tree", "Nature", true, 80, 75, 12_345L, 1, 0, 1.25), Msg.Ready(5),
        )
        all.forEach { assertEquals(it, roundTrip(it)) }
    }

    @Test fun garbageIsIgnored() {
        assertNull(Msg.decode("not json".toByteArray()))
        assertNull(Msg.decode("""{"t":"nope"}""".toByteArray()))
        assertNull(Msg.decode("""{"t":"guess"}""".toByteArray())) // missing field
    }
}

/** Two engines talking to each other through in-memory queues, like two phones. */
class Pair2(seed: Int = 7) {
    var now = 0L
    private val toGuest = ArrayDeque<Msg>()
    private val toHost = ArrayDeque<Msg>()
    val host = GameEngine("Hannah", "Gus", true, { toGuest.add(it) }, { now }, Random(seed))
    val guest = GameEngine("Gus", "Hannah", false, { toHost.add(it) }, { now }, Random(seed + 1))

    /** Deliver everything in flight, through the real wire encoding. */
    fun pump() {
        while (toGuest.isNotEmpty() || toHost.isNotEmpty()) {
            toGuest.removeFirstOrNull()?.let { guest.onMessage(Msg.decode(it.encode())!!) }
            toHost.removeFirstOrNull()?.let { host.onMessage(Msg.decode(it.encode())!!) }
        }
    }

    fun advance(ms: Long) { now += ms; host.tick(); guest.tick(); pump() }

    val drawer get() = if (host.state.iAmDrawer) host else guest
    val guesser get() = if (host.state.iAmDrawer) guest else host
    fun engineNamed(n: String) = if (host.state.myName == n) host else guest
}

class GameFlowTest {
    private fun start(rounds: Int = 4, secs: Int = 90): Pair2 {
        val p = Pair2()
        p.host.hostStart(rounds, secs)
        p.pump()
        return p
    }

    private fun draw(e: GameEngine, p: Pair2) {
        e.beginStroke(Palette.colors[2], 0.013f, 0.2f, 0.2f)
        e.extendStroke(0.5f, 0.5f)
        e.extendStroke(0.8f, 0.3f)
        e.endStroke()
        p.pump()
    }

    @Test fun setupPutsOnePlayerDrawingAndOtherWaiting() {
        val p = start()
        assertEquals(Phase.PICKING, p.drawer.state.phase)
        assertEquals(5, p.drawer.state.options.size)
        assertEquals(Phase.WAITING_FOR_WORD, p.guesser.state.phase)
        assertTrue(p.drawer.state.iAmDrawer)
        assertFalse(p.guesser.state.iAmDrawer)
        assertEquals(4, p.guesser.state.totalRounds)
    }

    @Test fun guesserNeverReceivesTheWord() {
        val p = start()
        val word = p.drawer.state.options.first()
        p.drawer.chooseWord(word)
        p.pump()
        assertNull(p.guesser.state.word)
        assertEquals(word.category, p.guesser.state.category)
        assertEquals(Words.pattern(word.text), p.guesser.state.pattern)
        assertEquals(Phase.GUESSING, p.guesser.state.phase)
        assertEquals(Phase.DRAWING, p.drawer.state.phase)
    }

    @Test fun drawingIsMirroredToGuesser() {
        val p = start()
        p.drawer.chooseWord(p.drawer.state.options.first())
        p.pump()
        draw(p.drawer, p)
        p.advance(150)
        assertEquals(1, p.guesser.board.strokes.size)
        assertEquals(p.drawer.board.strokes[0].pts, p.guesser.board.strokes[0].pts)
        assertEquals(Palette.colors[2], p.guesser.board.strokes[0].color)

        draw(p.drawer, p)
        p.drawer.undo(); p.pump()
        assertEquals(1, p.guesser.board.strokes.size)
        p.drawer.clearBoard(); p.pump()
        assertEquals(0, p.guesser.board.strokes.size)
    }

    @Test fun guessersCannotDraw() {
        val p = start()
        p.drawer.chooseWord(p.drawer.state.options.first())
        p.pump()
        p.guesser.beginStroke(1, 0.01f, 0.1f, 0.1f)
        assertEquals(0, p.guesser.board.strokes.size)
    }

    @Test fun correctGuessScoresBothFromTheSameClock() {
        val p = start()
        val word = p.drawer.state.options.first { it.difficulty == Difficulty.EASY }
        p.drawer.chooseWord(word); p.pump()
        draw(p.drawer, p)
        p.advance(9_000) // 9s of a 90s round -> 90% of the time left
        p.guesser.submitGuess(word.text); p.pump()
        assertEquals(word.text, p.drawer.state.pendingGuess)
        assertTrue(p.guesser.state.awaitingVerdict)
        p.advance(20_000) // the drawer dawdles over the verdict: must NOT cost anyone points
        p.drawer.judge(true); p.pump()

        val expected = Scoring.basePoints(9_000, 90_000, 1.0)
        assertEquals(expected, p.drawer.state.myScore)
        assertEquals(expected, p.guesser.state.myScore)
        assertEquals(p.drawer.state.myScore, p.guesser.state.theirScore)
        assertEquals(p.guesser.state.myScore, p.drawer.state.theirScore)
        assertEquals(Phase.RESULT, p.drawer.state.phase)
        assertEquals(Phase.RESULT, p.guesser.state.phase)
        assertEquals(word.text, p.guesser.state.outcome?.word) // word revealed only now
    }

    @Test fun declinedGuessCostsGuesserOnlyAndCanTryAgain() {
        val p = start()
        val word = p.drawer.state.options.first()
        p.drawer.chooseWord(word); p.pump()
        p.advance(5_000)
        p.guesser.submitGuess("banana-ish"); p.pump()
        p.drawer.judge(false); p.pump()
        assertFalse(p.guesser.state.awaitingVerdict)
        assertEquals(listOf("banana-ish"), p.guesser.state.declined)
        assertNull(p.drawer.state.pendingGuess)

        p.advance(5_000)
        p.guesser.submitGuess(word.text); p.pump()
        p.drawer.judge(true); p.pump()

        val base = Scoring.basePoints(10_000, 90_000, word.difficulty.multiplier)
        assertEquals(base, p.drawer.state.myScore)
        assertEquals(base - Scoring.WRONG_GUESS_PENALTY, p.guesser.state.myScore)
        assertEquals(1, p.drawer.state.outcome?.wrongGuesses)
    }

    @Test fun cannotSendASecondGuessUntilTheFirstIsJudged() {
        val p = start()
        p.drawer.chooseWord(p.drawer.state.options.first()); p.pump()
        p.guesser.submitGuess("one"); p.pump()
        p.guesser.submitGuess("two"); p.pump()
        assertEquals("one", p.drawer.state.pendingGuess)
    }

    @Test fun skippingCostsDrawerPointsAndNeverRepeatsWord() {
        val p = start()
        val first = p.drawer.state.options.first()
        p.drawer.chooseWord(first); p.pump()
        draw(p.drawer, p); p.advance(150)
        p.drawer.skipWord(); p.pump()

        assertEquals(Phase.PICKING, p.drawer.state.phase)
        assertEquals(Phase.WAITING_FOR_WORD, p.guesser.state.phase)
        assertEquals(0, p.guesser.board.strokes.size) // the abandoned drawing is gone
        assertTrue(p.drawer.state.options.none { it.text == first.text })

        val second = p.drawer.state.options.first { it.difficulty == Difficulty.EASY }
        p.drawer.chooseWord(second); p.pump()
        p.guesser.submitGuess(second.text); p.pump()
        p.drawer.judge(true); p.pump()

        val base = Scoring.basePoints(0, 90_000, 1.0)
        assertEquals(base - Scoring.SKIP_PENALTY, p.drawer.state.myScore)
        assertEquals(base, p.guesser.state.myScore) // guesser is not punished for the drawer's skip
        assertEquals(1, p.guesser.state.outcome?.skips)
    }

    @Test fun timeoutGivesNoPointsExceptSkipPenaltiesClampedAtZero() {
        val p = start()
        p.drawer.chooseWord(p.drawer.state.options.first()); p.pump()
        p.advance(90_000)
        assertEquals(Phase.RESULT, p.drawer.state.phase)
        assertEquals(Phase.RESULT, p.guesser.state.phase)
        assertFalse(p.guesser.state.outcome!!.correct)
        assertEquals(0, p.drawer.state.myScore)
        assertEquals(0, p.guesser.state.myScore)
    }

    @Test fun skipThenTimeoutCannotGoBelowZero() {
        val p = start()
        p.drawer.chooseWord(p.drawer.state.options.first()); p.pump()
        p.drawer.skipWord(); p.pump()
        p.drawer.chooseRandom(); p.pump()
        p.advance(90_000)
        assertEquals(0, p.drawer.state.myScore)
        assertEquals(0, p.guesser.state.theirScore)
    }

    @Test fun guessArrivingBeforeTimeoutIsStillJudgedAfterIt() {
        val p = start()
        val word = p.drawer.state.options.first()
        p.drawer.chooseWord(word); p.pump()
        p.advance(89_000)
        p.guesser.submitGuess(word.text); p.pump()
        p.advance(5_000) // time runs out while the drawer is thinking
        assertEquals(Phase.DRAWING, p.drawer.state.phase) // waits for the verdict
        p.drawer.judge(true); p.pump()
        assertTrue(p.guesser.state.outcome!!.correct)
        assertEquals(1_000L, p.guesser.state.outcome!!.elapsedMs.let { 90_000L - it })
    }

    @Test fun lastGuessDeclinedAfterTimeEndsTheRound() {
        val p = start()
        p.drawer.chooseWord(p.drawer.state.options.first()); p.pump()
        p.advance(89_000)
        p.guesser.submitGuess("nope"); p.pump()
        p.advance(5_000)
        p.drawer.judge(false); p.pump()
        assertEquals(Phase.RESULT, p.drawer.state.phase)
        assertEquals(Phase.RESULT, p.guesser.state.phase)
        assertFalse(p.drawer.state.outcome!!.correct)
    }

    @Test fun fullGameAlternatesRolesAndEndsWithMatchingScores() {
        val p = start(rounds = 4)
        val drawersSeen = mutableListOf<String>()
        repeat(4) { r ->
            assertEquals(r, p.drawer.state.round)
            drawersSeen += p.drawer.state.myName
            val d = p.drawer
            val g = p.guesser
            val w = d.state.options.first()
            d.chooseWord(w); p.pump()
            draw(d, p)
            p.advance(10_000L * (r + 1))
            g.submitGuess(w.text); p.pump()
            d.judge(true); p.pump()
            assertEquals(Phase.RESULT, d.state.phase)
            // Both ready -> next round (or game over)
            d.ready(); p.pump()
            assertEquals(Phase.RESULT, g.state.phase) // not until BOTH are ready
            g.ready(); p.pump()
        }
        assertEquals(Phase.GAME_OVER, p.host.state.phase)
        assertEquals(Phase.GAME_OVER, p.guest.state.phase)
        assertEquals(drawersSeen[0], drawersSeen[2])
        assertEquals(drawersSeen[1], drawersSeen[3])
        assertTrue(drawersSeen[0] != drawersSeen[1])
        assertEquals(p.host.state.myScore, p.guest.state.theirScore)
        assertEquals(p.host.state.theirScore, p.guest.state.myScore)
        assertEquals(4, p.host.state.history.size)
        // Each player drew twice and guessed twice with identical speeds -> identical totals.
        assertEquals(p.host.state.myScore, p.host.state.theirScore)
    }

    @Test fun rematchResetsEverything() {
        val p = start(rounds = 2)
        repeat(2) {
            val w = p.drawer.state.options.first()
            p.drawer.chooseWord(w); p.pump()
            p.guesser.submitGuess(w.text); p.pump()
            p.drawer.judge(true); p.pump()
            p.drawer.ready(); p.guesser.ready(); p.pump()
        }
        assertEquals(Phase.GAME_OVER, p.guest.state.phase)
        assertTrue(p.host.state.myScore > 0)

        p.host.hostStart(2, 60); p.pump()
        assertEquals(0, p.host.state.myScore)
        assertEquals(0, p.guest.state.myScore)
        assertEquals(60, p.guest.state.roundSeconds)
        assertEquals(0, p.host.state.history.size)
        assertNotNull(p.drawer.state.options.firstOrNull())
        assertEquals(Phase.WAITING_FOR_WORD, p.guesser.state.phase)
    }

    @Test fun surpriseMeStartsARound() {
        val p = start()
        p.drawer.chooseRandom(); p.pump()
        assertEquals(Phase.DRAWING, p.drawer.state.phase)
        assertNotNull(p.drawer.state.word)
        assertEquals(Phase.GUESSING, p.guesser.state.phase)
    }

    @Test fun onlyHostCanStart() {
        val p = Pair2()
        p.guest.hostStart(4, 90); p.pump()
        assertEquals(Phase.LOBBY, p.host.state.phase)
        assertEquals(Phase.LOBBY, p.guest.state.phase)
    }

    @Test fun strayMessagesInTheWrongPhaseAreHarmless() {
        val p = Pair2()
        p.host.onMessage(Msg.Guess("x"))
        p.host.onMessage(Msg.RoundResult(0, "a", "b", true, 1, 1, 1, 0, 0, 1.0))
        p.guest.onMessage(Msg.GuessDeclined("x"))
        p.guest.onMessage(Msg.Ready(9))
        p.guest.onMessage(Msg.StrokePart(1, 0, 0.1f, listOf(0f, 0f)))
        assertEquals(Phase.LOBBY, p.host.state.phase)
        assertEquals(Phase.LOBBY, p.guest.state.phase)
    }
}
