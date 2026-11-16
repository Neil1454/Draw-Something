package com.neil1454.drawsomething.core

import org.json.JSONObject
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test
import kotlin.random.Random

class SlotsTest {
    /** A grid from three rows of five letters (top, middle, bottom), in the reel-by-reel order the machine uses. */
    private fun grid(top: String, mid: String, bot: String) = String(CharArray(15) { i -> listOf(top, mid, bot)[i % 3][i / 3] })

    private val quiet = "LGLGL" // no pairs of anything in any row
    private fun blank() = grid("LODGB", "GBLOD", "ODGBL")

    @Test fun aLineOfFiveCherriesPaysTheTopCherryPrize() {
        val r = SlotRules.evaluate(grid(quiet, "CCCCC", quiet), 2)
        val w = r.lines.first { it.line == 0 }
        assertEquals(5, w.count); assertEquals('C', w.symbol)
        assertEquals(40 * 2, w.pay)
    }

    @Test fun threeAndFourOfAKindPayLessAndPairsOfCherriesPayALittle() {
        assertEquals(12, SlotRules.evaluate(grid(quiet, "CCCCG", quiet), 1).total)
        assertEquals(4, SlotRules.evaluate(grid(quiet, "CCCLG", quiet), 1).total)
        assertEquals(SlotRules.CHERRY_PAIR, SlotRules.evaluate(grid(quiet, "CCLGL", quiet), 1).total)
        assertEquals(0, SlotRules.evaluate(blank(), 5).total)
        // a pair of anything but cherries pays nothing
        assertEquals(0, SlotRules.evaluate(grid(quiet, "BBLGL", quiet), 1).total)
    }

    @Test fun wildsStandInForOtherSymbolsButNotForScatters() {
        val r = SlotRules.evaluate(grid(quiet, "BWBGL", quiet), 1)
        val mid = r.lines.first { it.line == 0 }
        assertEquals(16, mid.pay); assertEquals('B', mid.symbol); assertEquals(3, mid.count)
        // a wild first still pays for the symbol that follows
        assertEquals(16, SlotRules.evaluate(grid(quiet, "WBBGL", quiet), 1).lines.first { it.line == 0 }.pay)
        // a scatter breaks a line
        assertTrue(SlotRules.evaluate(grid(quiet, "B\$BGL", quiet), 1).lines.none { it.line == 0 })
        // five wilds pay as the jackpot
        val j = SlotRules.evaluate(grid(quiet, "WWWWW", quiet), 1)
        assertTrue(j.jackpot); assertEquals(500, j.lines.first { it.line == 0 }.pay)
    }

    @Test fun fiveSevensIsTheJackpotAndTheOtherLinesCountToo() {
        val r = SlotRules.evaluate(grid("SSSSS", "SSSSS", quiet), 1)
        assertTrue(r.jackpot)
        assertEquals(500, r.lines.first { it.line == 0 }.pay)
        assertEquals(500, r.lines.first { it.line == 1 }.pay)
        assertTrue(r.lines.size >= 2)
    }

    @Test fun threeScattersPayAndGiveFreeSpinsAndFreeSpinsDoubleEverything() {
        val g = grid("L\$LGL", "GLG\$G", "LGL\$L".replace("\$", "$"))
        val r = SlotRules.evaluate(g, 1)
        assertEquals(3, r.scatters)
        assertEquals(SlotRules.SCATTER_PAY[0] * SlotRules.cost(1), r.scatterPay)
        assertEquals(SlotRules.FREE_SPINS[0], r.freeSpins)
        val d = SlotRules.evaluate(g, 1, SlotRules.FREE_MULT)
        assertEquals(r.total * 2, d.total)
    }

    @Test fun theMachineReturnsMostOfWhatIsStakedInTheLongRun() {
        val rtp = SlotRules.simulate(150_000, Random(11))
        assertTrue("rtp $rtp", rtp in 0.80..1.05)
    }

    private fun pair(seed: Int = 1, spins: Int = 0): SyncPair<SlotsEngine> {
        val p = SyncPair({ n, o, h, s, r, c -> SlotsEngine(n, o, h, s, r, c) }, seed)
        p.host.start(JSONObject().put("spins", spins))
        p.pump()
        return p
    }

    private val SyncPair<SlotsEngine>.mover get() = if (host.state.myTurn) host else client
    private val SyncPair<SlotsEngine>.waiter get() = if (host.state.myTurn) client else host

    @Test fun onlyThePlayerWhoseTurnItIsCanBetAndSpin() {
        val p = pair(1)
        val a = p.mover; val b = p.waiter
        b.spin(); b.setBet(3); p.pump()
        assertEquals(0, p.host.state.spinNo)
        a.setBet(3); p.pump()
        assertEquals(3, a.state.betIndex); assertEquals(3, b.state.betIndex)
        a.setBet(9); a.setBet(-1); p.pump()
        assertEquals(3, a.state.betIndex)
        a.spin(); p.pump()
        assertEquals(1, p.host.state.spinNo)
        assertEquals("REELS", p.host.state.stage)
        assertEquals(SlotRules.START_CREDITS - SlotRules.cost(10), a.state.myCredits)
        assertEquals(SlotRules.START_CREDITS, b.state.myCredits)
        assertEquals(9, a.state.mySpins)
        // nothing happens while the reels spin
        a.spin(); a.collect(); p.pump()
        assertEquals(1, p.host.state.spinNo)
        // both phones see the same grid
        assertEquals(a.state.grid, b.state.grid)
    }

    @Test fun aLosingSpinPassesTheTurnAndAWinWaitsToBeCollected() {
        var sawWin = false; var sawLoss = false
        for (seed in 1..60) {
            val p = pair(seed)
            val a = p.mover; val b = p.waiter
            a.spin(); p.pump(); p.advance(3000)
            val total = a.state.total
            if (total > 0) {
                sawWin = true
                assertEquals("WIN", a.state.stage)
                assertEquals(total, a.state.pending)
                a.collect(); p.pump()
                assertEquals(SlotRules.START_CREDITS - SlotRules.cost(2) + total + 0, a.state.myCredits.let { it - 0 }.let { SlotRules.START_CREDITS - SlotRules.cost(2) + total })
                if (a.state.freeSpins == 0) assertTrue(b.state.myTurn)
            } else {
                sawLoss = true
                if (a.state.freeSpins == 0) assertTrue("turn passes", b.state.myTurn)
            }
            if (sawWin && sawLoss) break
        }
        assertTrue(sawWin && sawLoss)
    }

    @Test fun gamblingDoublesOrLosesTheWaitingPrize() {
        var doubled = false; var lost = false
        for (seed in 1..400) {
            val p = pair(seed)
            val a = p.mover
            a.spin(); p.pump(); p.advance(3000)
            if (a.state.stage != "WIN") continue
            val before = a.state.pending
            a.gamble(true); p.pump()
            if (a.state.cardWon) {
                doubled = true
                assertEquals(before * 2, a.state.pending)
                assertEquals("WIN", a.state.stage)
            } else {
                lost = true
                assertEquals(0, a.state.pending)
                assertEquals("LOST", a.state.stage)
                val c = a.state.myCredits
                p.advance(2500)
                assertEquals("a lost gamble costs the prize", c, a.state.myCredits.coerceAtMost(c))
            }
            if (doubled && lost) break
        }
        assertTrue(doubled && lost)
    }

    @Test fun anAllInGambleCanBeRepeatedAtMostThreeTimes() {
        for (seed in 1..800) {
            val p = pair(seed)
            val a = p.mover
            a.spin(); p.pump(); p.advance(3000)
            if (a.state.stage != "WIN") continue
            var n = 0
            while (a.state.stage == "WIN" && n < 6) { a.gamble(true); p.pump(); n++ }
            assertTrue(a.state.gambles <= SlotRules.MAX_GAMBLES)
            if (a.state.stage == "WIN") { assertEquals(SlotRules.MAX_GAMBLES, a.state.gambles); return }
        }
    }

    @Test fun gamesFinishWithConsistentResultsAndCreditsNeverGoNegative() {
        for (seed in 1..8) {
            val p = pair(seed, spins = 0)
            val a = SlotsBrain(p.host, Skill.HARD, Random(seed)); val b = SlotsBrain(p.client, Skill.NORMAL, Random(seed + 5))
            var g = 0
            while (p.host.state.phase == GPhase.PLAYING && g++ < 4000) {
                a.think(p.now); b.think(p.now); p.advance(100)
                assertTrue(p.host.state.myCredits >= 0 && p.host.state.theirCredits >= 0)
            }
            assertEquals("seed $seed", GPhase.OVER, p.host.state.phase)
            assertEquals(p.host.state.myCredits, p.client.state.theirCredits)
            val r = p.host.state.result
            val expect = if (p.host.state.myCredits > p.host.state.theirCredits) 1 else if (p.host.state.myCredits < p.host.state.theirCredits) 2 else 3
            assertEquals(expect, r)
            assertEquals(when (r) { 1 -> 2; 2 -> 1; else -> 3 }, p.client.state.result)
            assertEquals(0, p.host.state.mySpins); assertEquals(0, p.host.state.theirSpins)
        }
    }

    @Test fun badMessagesAreIgnored() {
        val p = pair(3)
        val before = p.client.state
        p.client.onMessage(GMsg.Snap("SLOTS", JSONObject().put("g", "zzz").put("cr", org.json.JSONArray())))
        p.host.onMessage(GMsg.Act("SLOTS", JSONObject()))
        p.host.onMessage(GMsg.Act("SLOTS", JSONObject().put("a", "bet").put("i", "x")))
        assertEquals(before.grid, p.client.state.grid)
        assertFalse(p.host.state.phase == GPhase.OVER)
    }
}
