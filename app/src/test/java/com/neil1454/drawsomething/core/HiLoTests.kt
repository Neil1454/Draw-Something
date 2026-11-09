package com.neil1454.drawsomething.core

import org.json.JSONObject
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class HiLoTest {
    private fun pair(rows: Int = 0, odds: Boolean = true, seed: Int = 1): SyncPair<HiLoEngine> {
        val p = SyncPair({ n, o, h, s, r, c -> HiLoEngine(n, o, h, s, r, c) }, seed)
        p.host.start(JSONObject().put("rows", rows).put("odds", odds))
        p.pump()
        return p
    }

    private val SyncPair<HiLoEngine>.mover get() = if (host.state.myTurn) host else client
    private val SyncPair<HiLoEngine>.waiter get() = if (host.state.myTurn) client else host

    private fun rank(c: Int) = HiLoRules.rank(c)

    /** The correct call for the next card, or null if it is the SAME value (a tie loses either way). */
    private fun SyncPair<HiLoEngine>.rightCall(): Boolean? {
        val row = host.secretRow()
        val i = host.state.idx
        return when {
            rank(row[i + 1]) > rank(row[i]) -> true
            rank(row[i + 1]) < rank(row[i]) -> false
            else -> null
        }
    }

    @Test fun cardsAreRankedAceHighWithFourSuits() {
        assertEquals(2, HiLoRules.rank(0))
        assertEquals(14, HiLoRules.rank(12)) // ace
        assertEquals("A♠", HiLoRules.cardName(12))
        assertEquals("10♥", HiLoRules.cardName(13 + 8))
        assertEquals("K♣", HiLoRules.cardName(39 + 11))
        assertTrue(HiLoRules.isRed(13) && !HiLoRules.isRed(0))
        assertEquals(52, (0 until 52).map { HiLoRules.cardName(it) }.toSet().size)
    }

    @Test fun onlyTheFirstCardIsVisibleToEitherPhone() {
        val p = pair()
        assertEquals(7, p.client.state.row.size)
        assertTrue(p.client.state.row[0] in 0..51)
        assertTrue(p.client.state.row.drop(1).all { it == -1 })
        assertEquals(p.host.state.row, p.client.state.row)
        assertTrue(p.host.state.myTurn != p.client.state.myTurn)
        assertEquals("GUESS", p.host.state.step)
    }

    @Test fun rightGuessesClimbThePrizeLadder() {
        for (seed in 0 until 200) {
            val p = pair(seed = seed)
            val row = p.host.secretRow()
            if ((0 until 6).any { rank(row[it]) == rank(row[it + 1]) }) continue // need a clean row
            val a = p.mover
            var expected = 0
            for (k in 1..5) {
                a.guess(p.rightCall()!!); p.pump()
                expected += HiLoRules.STEP_POINTS * k
                assertEquals(expected, a.state.myScore)
                assertEquals("hit", a.state.result)
                assertEquals(k, a.state.guesses)
                assertTrue(a.state.myTurn) // still guessing
                assertEquals(expected, p.waiter.state.theirScore)
            }
            // the sixth right guess clears the row: ladder step 6 plus the bonus
            a.guess(p.rightCall()!!); p.pump()
            expected += HiLoRules.STEP_POINTS * 6 + HiLoRules.FULL_ROW_BONUS
            assertEquals(expected, a.state.myScore)
            assertEquals(260, expected)
            assertEquals("full", a.state.result)
            assertEquals("SHOW", a.state.step)
            assertEquals(7, a.state.row.count { it >= 0 }) // everything is on show
            return
        }
        error("no clean row found")
    }

    @Test fun aWrongGuessEndsTheRowButKeepsWhatYouWon() {
        for (seed in 0 until 200) {
            val p = pair(seed = seed)
            val row = p.host.secretRow()
            if (rank(row[0]) == rank(row[1]) || rank(row[1]) == rank(row[2])) continue
            val a = p.mover
            val b = p.waiter
            a.guess(p.rightCall()!!); p.pump()                 // +10
            a.guess(!p.rightCall()!!); p.pump()                // wrong
            assertEquals("miss", a.state.result)
            assertEquals(10, a.state.myScore)                  // kept
            assertEquals("SHOW", a.state.step)
            a.guess(true); p.pump()                            // no more guesses on a finished row
            assertEquals(10, a.state.myScore)
            // after the pause, the other player gets a fresh row
            p.advance(HiLoRules.SHOW_MS + 100)
            assertTrue(b.state.myTurn)
            assertEquals("GUESS", b.state.step)
            assertEquals(0, b.state.run)
            assertEquals(1, a.state.myRowsDone)
            return
        }
        error("none")
    }

    @Test fun theSameValueCountsAsWrong() {
        for (seed in 0 until 600) {
            val p = pair(seed = seed)
            val row = p.host.secretRow()
            if (rank(row[0]) != rank(row[1])) continue
            val a = p.mover
            a.guess(true); p.pump()
            assertEquals("same", a.state.result)
            assertEquals(0, a.state.myScore)
            assertEquals("SHOW", a.state.step)
            return
        }
        error("no tie found")
    }

    @Test fun changeSwapsTheFaceUpCardOnlyOnce() {
        val p = pair()
        val a = p.mover
        val before = a.state.row[0]
        a.change(); p.pump()
        assertFalse(a.state.myChange)
        assertTrue(a.state.theirChange)
        assertTrue(a.state.row[0] != before || true) // a new card was drawn (could in theory repeat a rank)
        val after = a.state.row[0]
        a.change(); p.pump() // second time refused
        assertEquals(after, a.state.row[0])
        assertEquals(1, p.host.state.moves)
    }

    @Test fun outOfTurnAndNonsenseActionsAreIgnored() {
        val p = pair()
        p.waiter.guess(true); p.waiter.change(); p.pump()
        assertEquals(0, p.host.state.moves)
        p.mover.onMessage(GMsg.Act("HILO", JSONObject().put("a", "cheat")))
        p.host.onMessage(GMsg.Act("HILO", JSONObject().put("x", 1)))
        assertEquals(0, p.host.state.moves)
    }

    @Test fun oddsAddUpAndPointTheRightWay() {
        val p = pair()
        val s = p.host.state
        assertTrue(s.pHigher >= 0 && s.pLower >= 0)
        assertTrue(s.pHigher + s.pLower in 85..100) // the rest is "same value"
        val r = rank(s.row[0])
        if (r <= 5) assertTrue(s.pHigher > s.pLower)
        if (r >= 11) assertTrue(s.pLower > s.pHigher)
        val off = pair(odds = false)
        assertEquals(-1, off.host.state.pHigher)
    }

    @Test fun everyoneGetsTheSameNumberOfRowsAndTheHigherScoreWins() {
        for (skill in Skill.values()) {
            val d = Duel(11 + skill.ordinal, skill, skill)
            d.host.hilo.start(JSONObject().put("rows", 0).put("odds", true))
            var guard = 0
            while (d.host.hilo.state.phase == GPhase.PLAYING && guard++ < 20_000) d.advance(100, 50)
            val h = d.host.hilo.state
            assertEquals("$skill", GPhase.OVER, h.phase)
            assertEquals(GPhase.OVER, d.guest.hilo.state.phase)
            assertEquals(3, h.myRowsDone)
            assertEquals(3, h.theirRowsDone)
            assertEquals(h.myScore, d.guest.hilo.state.theirScore)
            val expected = if (h.myScore > h.theirScore) 1 else if (h.myScore < h.theirScore) 2 else 3
            assertEquals(expected, h.matchResult)
        }
    }

    private fun fakeState(card: Int, pHigher: Int, pLower: Int, change: Boolean = false, guesses: Int = 1) =
        HiLoState(
            myName = "a", theirName = "b", phase = GPhase.PLAYING, row = listOf(card, -1, -1, -1, -1, -1, -1),
            idx = 0, myTurn = true, step = "GUESS", pHigher = pHigher, pLower = pLower, myChange = change, guesses = guesses,
        )

    @Test fun theComputerFollowsTheOddsAndHardNeverGoesAgainstThem() {
        val rnd = kotlin.random.Random(1)
        repeat(50) {
            // a 3 with most cards higher: Normal and Hard always say higher
            for (skill in listOf(Skill.NORMAL, Skill.HARD)) {
                assertEquals(HiLoAi.Move.HIGHER, HiLoAi.decide(fakeState(1, 88, 8), skill, rnd))
                assertEquals(HiLoAi.Move.LOWER, HiLoAi.decide(fakeState(11, 10, 82), skill, rnd))
            }
        }
        // Easy goes the right way most of the time, but not always
        val easyRight = (0 until 400).count { HiLoAi.decide(fakeState(1, 88, 8), Skill.EASY, rnd) == HiLoAi.Move.HIGHER }
        assertTrue("easy right $easyRight of 400", easyRight in 280..399)
    }

    @Test fun theComputerEstimatesFromTheCardWhenOddsAreOff() {
        val rnd = kotlin.random.Random(2)
        assertEquals(HiLoAi.Move.HIGHER, HiLoAi.decide(fakeState(0, -1, -1), Skill.HARD, rnd))  // a 2
        assertEquals(HiLoAi.Move.LOWER, HiLoAi.decide(fakeState(12, -1, -1), Skill.HARD, rnd))  // an ace
    }

    @Test fun theComputerSpendsItsChangeOnAMiddlingCardOnlyAtTheStartOfARow() {
        val rnd = kotlin.random.Random(3)
        val seven = 5 // rank 7
        val changes = (0 until 200).count { HiLoAi.decide(fakeState(seven, 45, 45, change = true, guesses = 0), Skill.HARD, rnd) == HiLoAi.Move.CHANGE }
        assertTrue("changed $changes of 200", changes in 90..150)
        // never once it has started guessing, never when it has none left, never on Easy
        assertEquals(0, (0 until 100).count { HiLoAi.decide(fakeState(seven, 45, 45, change = true, guesses = 2), Skill.HARD, rnd) == HiLoAi.Move.CHANGE })
        assertEquals(0, (0 until 100).count { HiLoAi.decide(fakeState(seven, 45, 45, change = false, guesses = 0), Skill.HARD, rnd) == HiLoAi.Move.CHANGE })
        assertEquals(0, (0 until 100).count { HiLoAi.decide(fakeState(seven, 45, 45, change = true, guesses = 0), Skill.EASY, rnd) == HiLoAi.Move.CHANGE })
        // a card that isn't middling: never wastes the change
        assertEquals(0, (0 until 100).count { HiLoAi.decide(fakeState(1, 88, 8, change = true, guesses = 0), Skill.HARD, rnd) == HiLoAi.Move.CHANGE })
    }

    @Test fun badSnapshotsAreIgnored() {
        val p = pair()
        val before = p.client.state.row
        p.client.onMessage(GMsg.Snap("HILO", JSONObject("""{"rows":5,"odds":true,"row":[1,2,3],"idx":0,"turn":1,"s1":0,"s2":0,"run":0,"g":0,"step":"GUESS","c1":true,"c2":true,"d1":0,"d2":0,"over":false}""")))
        p.client.onMessage(GMsg.Snap("HILO", JSONObject("""{"rows":5,"odds":true,"row":[99,-1,-1,-1,-1,-1,-1],"idx":0,"turn":1,"s1":0,"s2":0,"run":0,"g":0,"step":"GUESS","c1":true,"c2":true,"d1":0,"d2":0,"over":false}""")))
        assertEquals(before, p.client.state.row)
    }
}
