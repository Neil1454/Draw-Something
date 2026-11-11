package com.neil1454.drawsomething.core

import org.json.JSONObject
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test
import kotlin.random.Random

/** Two sync engines of the same game joined by in-memory queues, over the real wire format. */
class SyncPair<E : SyncEngine>(
    make: (name: String, other: String, host: Boolean, send: (GMsg) -> Unit, random: Random, clock: () -> Long) -> E,
    seed: Int = 1,
) {
    var now = 0L
    private val toClient = ArrayDeque<ByteArray>()
    private val toHost = ArrayDeque<ByteArray>()
    val host: E = make("Hannah", "Gus", true, { toClient.add(it.encode()) }, Random(seed)) { now }
    val client: E = make("Gus", "Hannah", false, { toHost.add(it.encode()) }, Random(seed + 500)) { now }

    fun pump() {
        while (toClient.isNotEmpty() || toHost.isNotEmpty()) {
            toClient.removeFirstOrNull()?.let { b -> (Packet.decode(b) as Packet.Sync).let { client.onMessage(it.msg) } }
            toHost.removeFirstOrNull()?.let { b -> (Packet.decode(b) as Packet.Sync).let { host.onMessage(it.msg) } }
        }
    }

    fun advance(ms: Long) {
        now += ms
        host.tick(); client.tick(); pump()
    }
}

// ====================================================================================== Connect 4

class DropTest {
    private fun pair(power: Boolean = false, size: Int = 0, seed: Int = 1): SyncPair<DropEngine> {
        val p = SyncPair({ n, o, h, s, r, c -> DropEngine(n, o, h, s, r, c) }, seed)
        p.host.start(JSONObject().put("size", size).put("power", power))
        p.pump()
        return p
    }

    private val SyncPair<DropEngine>.mover get() = if (host.state.myTurn) host else client
    private val SyncPair<DropEngine>.waiter get() = if (host.state.myTurn) client else host

    private fun SyncPair<DropEngine>.play(col: Int, power: Int = 0) {
        mover.drop(col, power); pump()
    }

    @Test fun bothPhonesSeeTheSameEmptyBoardAndOppositeTurns() {
        val p = pair()
        assertEquals(GPhase.PLAYING, p.host.state.phase)
        assertEquals(GPhase.PLAYING, p.client.state.phase)
        assertEquals(42, p.host.state.grid.length)
        assertTrue(p.host.state.grid.all { it == '.' })
        assertTrue(p.host.state.myTurn != p.client.state.myTurn)
        assertEquals(p.host.state.grid, p.client.state.grid)
    }

    @Test fun discsStackFromTheBottomAndTurnsAlternate() {
        val p = pair()
        val first = p.mover
        val second = p.waiter
        p.play(3)
        assertEquals('.', p.host.state.grid[3]) // top of the column is empty
        assertEquals(first.state.myDisc, p.host.state.grid[5 * 7 + 3]) // bottom row
        assertTrue(p.mover === second)
        p.play(3)
        assertEquals(second.state.myDisc, p.host.state.grid[4 * 7 + 3]) // stacked on top
        assertTrue(p.mover === first)
        assertEquals(p.host.state.grid, p.client.state.grid)
    }

    @Test fun cannotPlayOutOfTurnIntoAFullColumnOrOffTheBoard() {
        val p = pair()
        p.waiter.drop(0); p.pump()
        assertTrue(p.host.state.grid.all { it == '.' })
        p.play(99); p.play(-1)
        assertTrue(p.host.state.grid.all { it == '.' })
        repeat(6) { p.play(0) }
        val before = p.host.state.grid
        p.play(0) // the column is full
        assertEquals(before, p.host.state.grid)
    }

    @Test fun horizontalWinEndsTheGameForBoth() {
        val p = pair()
        val a = p.mover
        val b = p.waiter
        // a: 0,1,2,3 on the bottom row; b: 0,1,2 on top of them
        p.play(0); p.play(0); p.play(1); p.play(1); p.play(2); p.play(2); p.play(3)
        assertEquals(GPhase.OVER, p.host.state.phase)
        assertEquals(GPhase.OVER, p.client.state.phase)
        assertEquals(1, a.state.result)
        assertEquals(2, b.state.result)
        assertEquals(listOf(35, 36, 37, 38), a.state.line.sorted())
        assertFalse(a.state.myTurn || b.state.myTurn)
        p.play(4) // no more moves
        assertEquals('.', p.host.state.grid[5 * 7 + 4])
    }

    @Test fun verticalAndDiagonalWinsAreDetected() {
        val v = pair()
        val a = v.mover
        v.play(0); v.play(1); v.play(0); v.play(1); v.play(0); v.play(1); v.play(0)
        assertEquals(1, a.state.result)

        val d = pair(seed = 2)
        val x = d.mover
        // build a rising diagonal for x: cells (col0,row5) (col1,row4) (col2,row3) (col3,row2)
        d.play(0)           // x
        d.play(1)           // o  col1 row5
        d.play(1)           // x  col1 row4
        d.play(2)           // o  col2 row5
        d.play(3)           // x  col3 row5 (filler)
        d.play(2)           // o  col2 row4
        d.play(2)           // x  col2 row3
        d.play(3)           // o  col3 row4
        d.play(4)           // x  filler
        d.play(3)           // o  col3 row3
        d.play(3)           // x  col3 row2 -> diagonal complete
        assertEquals(1, x.state.result)
        assertEquals(4, x.state.line.size)
    }

    @Test fun megaBoardNeedsFiveInARow() {
        val p = pair(size = 1)
        assertEquals(63, p.host.state.grid.length)
        val a = p.mover
        p.play(0); p.play(0); p.play(1); p.play(1); p.play(2); p.play(2); p.play(3); p.play(3)
        assertEquals(0, a.state.result) // four in a row is not enough here
        p.play(4)
        assertEquals(1, a.state.result)
    }

    @Test fun bombBlastsNeighboursAndColumnsSettle() {
        val p = pair(power = true)
        assertTrue(p.host.state.myBomb && p.client.state.myBomb)
        val a = p.mover
        val b = p.waiter
        p.play(3); p.play(3); p.play(4) // a bottom col3, b above it, then b? alternate: a, b, a
        // Now b's turn: b drops a bomb in column 2: lands at bottom (col2,row5), blasting neighbours
        p.play(2, 1)
        assertFalse(b.state.myBomb)
        assertTrue(a.state.theirBomb.not())
        val removed = p.host.state.removed
        assertTrue(removed.isNotEmpty())
        // after blasting, nothing floats: each column is packed to the bottom
        val g = p.host.state.grid
        for (col in 0 until 7) {
            var seenEmptyBelow = false
            for (row in 5 downTo 0) {
                val v = g[row * 7 + col]
                if (v == '.') seenEmptyBelow = true else assertFalse("floating disc at col $col", seenEmptyBelow)
            }
        }
        assertEquals(p.host.state.grid, p.client.state.grid)
        assertEquals(b.state.myDisc, g[p.host.state.last]) // the bomb disc itself survives
    }

    @Test fun doubleDiscGivesAnotherGoOnlyOnce() {
        val p = pair(power = true)
        val a = p.mover
        p.play(0, 2)
        assertTrue(a.state.myTurn) // again
        assertFalse(a.state.myDouble)
        p.play(1, 2) // second double is refused: none left to use in this turn? (one per game)
        assertTrue(a.state.myTurn) // refused move leaves the turn unchanged
        p.play(1)
        assertFalse(a.state.myTurn)
    }

    @Test fun powerDiscsRefusedWhenPowerModeIsOff() {
        val p = pair(power = false)
        val a = p.mover
        p.play(0, 1)
        assertTrue(p.host.state.grid.all { it == '.' })
        assertTrue(a.state.myTurn)
    }

    @Test fun colourThemeReachesBothPhonesAndIsClamped() {
        for (t in 0..2) {
            val p = SyncPair({ n, o, h, sd, r, c -> DropEngine(n, o, h, sd, r, c) }, 4)
            p.host.start(JSONObject().put("theme", t)); p.pump()
            assertEquals(t, p.host.state.theme)
            assertEquals(t, p.client.state.theme)
        }
        val q = SyncPair({ n, o, h, sd, r, c -> DropEngine(n, o, h, sd, r, c) }, 4)
        q.host.start(JSONObject().put("theme", 99)); q.pump()
        assertEquals(2, q.client.state.theme)
    }

    @Test fun rematchResets() {
        val p = pair()
        p.play(0); p.play(0); p.play(1); p.play(1); p.play(2); p.play(2); p.play(3)
        assertEquals(GPhase.OVER, p.client.state.phase)
        p.host.start(JSONObject().put("size", 1)); p.pump()
        assertEquals(GPhase.PLAYING, p.client.state.phase)
        assertEquals(63, p.client.state.grid.length)
        assertTrue(p.client.state.grid.all { it == '.' })
        assertEquals(0, p.client.state.result)
    }

    @Test fun clientCannotStartAndHostileMessagesAreIgnored() {
        val p = pair()
        p.client.start(JSONObject().put("size", 1)); p.pump()
        assertEquals(42, p.host.state.grid.length)
        // garbage and out-of-range snapshots / actions do nothing
        p.client.onMessage(GMsg.Snap("DROP", JSONObject().put("w", 7)))
        p.client.onMessage(GMsg.Snap("DROP", JSONObject("""{"w":7,"h":6,"n":4,"pw":false,"g":"short","turn":1,"res":0,"b1":false,"b2":false,"d1":false,"d2":false}""")))
        p.host.onMessage(GMsg.Act("DROP", JSONObject().put("c", "x")))
        p.host.onMessage(GMsg.Act("DROP", JSONObject().put("c", 1_000_000)))
        assertEquals(42, p.client.state.grid.length)
        assertTrue(p.host.state.grid.all { it == '.' })
    }

    @Test fun messagesRoundTripAndBadOnesAreRejected() {
        val setup = GMsg.Setup("DROP", JSONObject().put("size", 1))
        val back = (Packet.decode(setup.encode()) as Packet.Sync).msg
        assertEquals("DROP", back.game)
        assertEquals(1, (back as GMsg.Setup).opts.getInt("size"))
        assertNull(Packet.decode("""{"t":"g_act","g":"NOPE","b":{}}""".toByteArray()))
        assertNull(Packet.decode("""{"t":"g_act","g":"DROP"}""".toByteArray()))
        assertNull(Packet.decode("""{"t":"g_zzz","g":"DROP","b":{}}""".toByteArray()))
    }
}

// ====================================================================================== Pairs

class MemoryTest {
    private fun pair(size: Int = 0, seed: Int = 3): SyncPair<MemoryEngine> {
        val p = SyncPair({ n, o, h, s, r, c -> MemoryEngine(n, o, h, s, r, c) }, seed)
        p.host.start(JSONObject().put("size", size).put("theme", 1))
        p.pump()
        return p
    }

    private val SyncPair<MemoryEngine>.mover get() = if (host.state.myTurn) host else client
    private val SyncPair<MemoryEngine>.waiter get() = if (host.state.myTurn) client else host

    /** Cheat for tests: find two cards that match (needs a peek at the host's secret layout). */
    private fun SyncPair<MemoryEngine>.layout(): IntArray {
        val f = MemoryEngine::class.java.getDeclaredField("cards")
        f.isAccessible = true
        return f.get(host) as IntArray
    }

    private fun SyncPair<MemoryEngine>.flip(i: Int) {
        mover.flip(i); pump()
    }

    @Test fun partnerNeverSeesFaceDownCards() {
        val p = pair()
        assertEquals(16, p.client.state.view.length)
        assertTrue(p.client.state.view.all { it == '.' })
        assertTrue(p.host.state.view.all { it == '.' })
        p.flip(0)
        assertEquals(1, p.client.state.view.count { it != '.' })
        assertEquals(p.host.state.view, p.client.state.view)
    }

    @Test fun matchingPairScoresAndKeepsTheTurn() {
        val p = pair()
        val cards = p.layout()
        val a = cards.indexOf(cards[0]).let { 0 }
        val b = (1 until cards.size).first { cards[it] == cards[0] }
        val first = p.mover
        p.flip(a); p.flip(b)
        assertEquals(MemoryRules.PAIR_POINTS, first.state.myScore)
        assertEquals(first.state.myScore, p.waiter.state.theirScore)
        assertTrue(first.state.myTurn) // goes again
        assertEquals("match", first.state.event)
        assertEquals(1, first.state.myStreak)
        assertEquals(if (first === p.host) '1' else '2', p.host.state.owners[a])
        assertEquals(p.host.state.owners, p.client.state.owners)
    }

    @Test fun missHidesAgainAfterAPauseAndPassesTheTurn() {
        val p = pair()
        val cards = p.layout()
        val other = (1 until cards.size).first { cards[it] != cards[0] }
        val first = p.mover
        val second = p.waiter
        p.flip(0); p.flip(other)
        assertEquals("miss", first.state.event)
        assertEquals(2, first.state.up.size)
        assertTrue(first.state.myTurn) // still showing the miss
        p.flip(3); // ignored while the miss is on show
        assertEquals(2, first.state.up.size)
        p.advance(MemoryRules.PEEK_MS + 100)
        assertTrue(p.host.state.up.isEmpty())
        assertTrue(p.host.state.view.all { it == '.' })
        assertFalse(first.state.myTurn)
        assertTrue(second.state.myTurn)
    }

    @Test fun streaksPayMoreAndMissesResetThem() {
        val p = pair()
        val cards = p.layout()
        val pairs = (0 until 8).map { id -> cards.indices.filter { cards[it] == id } }
        val first = p.mover
        p.flip(pairs[0][0]); p.flip(pairs[0][1])
        p.flip(pairs[1][0]); p.flip(pairs[1][1])
        assertEquals(MemoryRules.PAIR_POINTS * 2 + MemoryRules.STREAK_BONUS, first.state.myScore)
        assertEquals(2, first.state.myStreak)
        p.flip(pairs[2][0]); p.flip(pairs[3][0]) // miss
        p.advance(MemoryRules.PEEK_MS + 100)
        assertEquals(0, first.state.myStreak)
    }

    @Test fun cannotFlipOutOfTurnTwiceOrMatchedCards() {
        val p = pair()
        p.waiter.flip(0); p.pump()
        assertTrue(p.host.state.view.all { it == '.' })
        val cards = p.layout()
        val b = (1 until cards.size).first { cards[it] == cards[0] }
        p.flip(0); p.flip(0) // same card twice is ignored
        assertEquals(1, p.host.state.up.size)
        p.flip(b)
        p.flip(0) // already matched
        p.flip(999); p.flip(-5)
        assertEquals(2, p.host.state.view.count { it != '.' })
    }

    @Test fun clearingTheBoardEndsTheGameWithAWinner() {
        for (size in MemorySize.values().indices) {
            val p = pair(size)
            val cards = p.layout()
            val pairs = cards.indices.groupBy { cards[it] }.values
            var turns = 0
            for (pr in pairs) { p.flip(pr[0]); p.flip(pr[1]); turns++ }
            assertEquals(GPhase.OVER, p.host.state.phase)
            assertEquals(GPhase.OVER, p.client.state.phase)
            assertTrue(p.host.state.view.none { it == '.' })
            assertEquals(1, p.host.state.result.let { if (it == 1) 1 else if (it == 2) 1 else 0 }) // someone won
            assertEquals(p.host.state.myScore, p.client.state.theirScore)
            assertTrue(p.host.state.result != p.client.state.result || p.host.state.result == 3)
        }
    }

    @Test fun badSnapshotsAreIgnored() {
        val p = pair()
        p.client.onMessage(GMsg.Snap("MEMORY", JSONObject("""{"w":4,"h":4,"th":0,"v":"abc","o":"...","up":[],"turn":1,"s1":0,"s2":0,"k1":0,"k2":0,"ev":"","by":0,"pt":0,"mv":0,"over":false}""")))
        assertEquals(16, p.client.state.view.length)
    }
}

// ====================================================================================== Pig

class DiceTest {
    private fun pair(two: Boolean = false, target: Int = 1, seed: Int = 5): SyncPair<DiceEngine> {
        val p = SyncPair({ n, o, h, s, r, c -> DiceEngine(n, o, h, s, r, c) }, seed)
        p.host.start(JSONObject().put("target", target).put("two", two))
        p.pump()
        return p
    }

    private val SyncPair<DiceEngine>.mover get() = if (host.state.myTurn) host else client

    @Test fun rollingAddsToHeldPointsUntilABust() {
        val p = pair()
        val a = p.mover
        var guard = 0
        while (a.state.myTurn && guard++ < 100) {
            val before = a.state.held
            a.roll(); p.pump()
            if (a.state.event == "bust") {
                assertEquals(0, a.state.held)
                assertEquals(1, a.state.dice.single())
                assertFalse(a.state.myTurn) // turn passed
                assertEquals(0, a.state.myScore) // nothing banked
            } else {
                assertTrue(a.state.dice.single() in 2..6)
                assertEquals(before + a.state.dice.single(), a.state.held)
            }
        }
        assertFalse(a.state.myTurn)
        assertEquals(p.host.state.dice, p.client.state.dice)
    }

    @Test fun bankingSavesPointsAndPassesTheTurn() {
        val p = pair()
        val a = p.mover
        while (a.state.held == 0) {
            a.roll(); p.pump()
            if (!a.state.myTurn) return // busted straight away: another seed would be needed; rule covered above
        }
        val held = a.state.held
        a.bank(); p.pump()
        assertEquals(held, a.state.myScore)
        assertEquals(held, p.client.state.theirScore.let { if (a === p.client) p.host.state.theirScore else p.client.state.theirScore })
        assertEquals(0, a.state.held)
        assertFalse(a.state.myTurn)
        assertEquals("bank", a.state.event)
    }

    @Test fun cannotBankNothingOrActOutOfTurn() {
        val p = pair()
        val a = p.mover
        a.bank(); p.pump()
        assertTrue(a.state.myTurn)
        val other = if (a === p.host) p.client else p.host
        other.roll(); other.bank(); p.pump()
        assertEquals(0, p.host.state.moves)
    }

    @Test fun reachingTheTargetWinsForBoth() {
        // Play full random games until someone banks past 50 (quick target).
        repeat(20) { seed ->
            val p = pair(target = 0, seed = seed)
            var guard = 0
            while (p.host.state.phase == GPhase.PLAYING && guard++ < 5000) {
                val m = p.mover
                // simple strategy: bank once holding 10+
                if (m.state.held >= 10) m.bank() else m.roll()
                p.pump()
            }
            assertEquals(GPhase.OVER, p.host.state.phase)
            assertEquals(GPhase.OVER, p.client.state.phase)
            val winner = if (p.host.state.result == 1) p.host else p.client
            assertTrue(winner.state.myScore >= 50)
            assertEquals(1, listOf(p.host.state.result, p.client.state.result).count { it == 1 })
            assertEquals(1, listOf(p.host.state.result, p.client.state.result).count { it == 2 })
        }
    }

    @Test fun twoDiceModeHasSnakeEyesDoublesAndSingleOnes() {
        val seen = mutableSetOf<String>()
        repeat(300) { seed ->
            val p = pair(two = true, seed = seed)
            val a = p.mover
            repeat(30) {
                if (a.state.myTurn && p.host.state.phase == GPhase.PLAYING) {
                    a.roll(); p.pump()
                    seen += a.state.event
                    val d = a.state.dice
                    assertEquals(2, d.size)
                    when (a.state.event) {
                        "snake" -> assertEquals(listOf(1, 1), d)
                        "bust" -> assertTrue(d.contains(1) && d != listOf(1, 1))
                        "doubles" -> { assertEquals(d[0], d[1]); assertEquals(d[0] * 4, a.state.eventPoints) }
                        "roll" -> { assertTrue(d.none { it == 1 }); assertEquals(d.sum(), a.state.eventPoints) }
                    }
                }
            }
        }
        assertTrue(seen.containsAll(listOf("snake", "bust", "doubles", "roll")))
    }

    @Test fun snakeEyesWipesTheBankedScore() {
        for (seed in 0 until 500) {
            val p = pair(two = true, seed = seed)
            val a = p.mover
            var bankedSome = false
            var guard = 0
            while (a.state.myTurn && guard++ < 50) {
                a.roll(); p.pump()
                if (a.state.event == "snake") return@snakeEyesWipesTheBankedScore
                if (a.state.held >= 12) { a.bank(); p.pump(); bankedSome = true; break }
            }
            if (!bankedSome) continue
            // now the other player has a turn; skip over it by letting them bank/bust, then roll on until snake
            val b = if (a === p.host) p.client else p.host
            var g2 = 0
            while (g2++ < 200 && p.host.state.phase == GPhase.PLAYING) {
                val m = p.mover
                if (m === a) {
                    val before = a.state.myScore
                    m.roll(); p.pump()
                    if (m.state.event == "snake") {
                        assertEquals(0, a.state.myScore)
                        assertEquals(0, b.state.theirScore)
                        assertTrue(before > 0)
                        return@snakeEyesWipesTheBankedScore
                    }
                    if (m.state.held > 0 && m.state.myTurn) { m.bank(); p.pump() }
                } else {
                    m.roll(); p.pump(); if (m.state.myTurn && m.state.held > 0) { m.bank(); p.pump() }
                }
            }
        }
    }
}

// ====================================================================================== Othello

class FlipTest {
    private fun pair(size: Int = 0): SyncPair<FlipEngine> {
        val p = SyncPair({ n, o, h, s, r, c -> FlipEngine(n, o, h, s, r, c) }, 9)
        p.host.start(JSONObject().put("size", size))
        p.pump()
        return p
    }

    private val SyncPair<FlipEngine>.mover get() = if (host.state.myTurn) host else client

    @Test fun startsWithTheFourCentreDiscsAndBlackToMove() {
        val p = pair()
        val g = p.host.state.grid
        assertEquals(64, g.length)
        assertEquals(2, g.count { it == '1' })
        assertEquals(2, g.count { it == '2' })
        assertEquals('2', g[3 * 8 + 3]); assertEquals('1', g[3 * 8 + 4])
        assertEquals('1', g[4 * 8 + 3]); assertEquals('2', g[4 * 8 + 4])
        assertTrue(p.host.state.myTurn) // black = host starts
        assertEquals(4, p.host.state.legal.size)
        assertTrue(p.client.state.legal.isEmpty())
    }

    @Test fun aLegalMoveFlipsDiscsAndPassesTheTurn() {
        val p = pair()
        val move = p.host.state.legal.first()
        p.host.place(move); p.pump()
        assertEquals('1', p.client.state.grid[move])
        assertEquals(1, p.host.state.flipped.size)
        assertEquals(4, p.host.state.myCount)
        assertEquals(1, p.host.state.theirCount)
        assertEquals(p.host.state.grid, p.client.state.grid)
        assertTrue(p.client.state.myTurn)
        assertFalse(p.host.state.myTurn)
        assertTrue(p.client.state.legal.isNotEmpty())
    }

    @Test fun illegalMovesAreIgnored() {
        val p = pair()
        val before = p.host.state.grid
        p.host.place(0); p.host.place(27); p.host.place(-3); p.host.place(500); p.pump() // far corner, occupied, off-board
        assertEquals(before, p.host.state.grid)
        p.client.place(p.host.state.legal.first()); p.pump() // not their turn
        assertEquals(before, p.host.state.grid)
    }

    @Test fun reversiRulesMatchHandCheckedPositions() {
        // 4x4 row:  . 1 2 .   white (2) can play at index 0? needs a black disc trapped: 0 . ,1 black,2 white -> yes
        val g = intArrayOf(0, 1, 2, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0)
        val moves = reversiMoves(g, 4, 2)
        assertEquals(listOf(1), moves[0])
        assertEquals(1, moves.size)
        // black (1) can play at index 3, flipping the white disc at index 2
        assertEquals(listOf(2), reversiMoves(g, 4, 1)[3])
    }

    @Test fun fullGamesFinishWithConsistentCounts() {
        for (size in FlipSize.values().indices) repeat(10) { seed ->
            val p = SyncPair({ n, o, h, s, r, c -> FlipEngine(n, o, h, s, r, c) }, seed).also {
                it.host.start(JSONObject().put("size", size)); it.pump()
            }
            val rnd = Random(seed)
            var guard = 0
            while (p.host.state.phase == GPhase.PLAYING && guard++ < 500) {
                val m = p.mover
                m.place(m.state.legal.random(rnd)); p.pump()
            }
            assertEquals(GPhase.OVER, p.host.state.phase)
            assertEquals(GPhase.OVER, p.client.state.phase)
            val n = p.host.state.n
            assertEquals(p.host.state.myCount + p.host.state.theirCount, p.host.state.grid.count { it != '.' })
            assertTrue(p.host.state.myCount + p.host.state.theirCount in 5..n * n)
            assertEquals(p.host.state.myCount, p.client.state.theirCount)
            assertTrue(p.host.state.result == p.client.state.result || p.host.state.result + p.client.state.result == 3)
            assertEquals(p.host.state.grid, p.client.state.grid)
            // nobody can move any more
            assertTrue(p.host.state.legal.isEmpty() && p.client.state.legal.isEmpty())
        }
    }
}

// ====================================================================================== Quick Draw

class QuickTest {
    private fun pair(fake: Boolean = false, match: Int = 0, seed: Int = 11): SyncPair<QuickEngine> {
        val p = SyncPair({ n, o, h, s, r, c -> QuickEngine(n, o, h, s, r, c) }, seed)
        p.host.start(JSONObject().put("fake", fake).put("match", match))
        p.pump()
        return p
    }

    /** Move time forward until the screens say GO. */
    private fun SyncPair<QuickEngine>.untilGo() {
        var guard = 0
        while (host.state.step != "GO" && guard++ < 200) advance(100)
        assertEquals("GO", host.state.step)
        assertEquals("GO", client.state.step)
    }

    @Test fun startsWithWaitAndNobodyTapped() {
        val p = pair()
        assertEquals("WAIT", p.host.state.step)
        assertEquals("WAIT", p.client.state.step)
        assertFalse(p.host.state.iTapped)
        assertEquals(2, p.host.state.target)
    }

    @Test fun fasterTapWinsTheRoundAndBothSeeTheTimes() {
        val p = pair()
        p.untilGo()
        p.advance(200); p.client.tap(); p.pump()    // client reacts in ~200 ms
        p.advance(100); p.host.tap(); p.pump()      // host in ~300 ms
        assertEquals("RESULT", p.host.state.step)
        assertEquals(1, p.client.state.roundWin) // 1 = I won
        assertEquals(2, p.host.state.roundWin)
        assertEquals(1, p.client.state.myScore)
        assertEquals(1, p.host.state.theirScore)
        assertEquals(200, p.client.state.myMs)
        assertEquals(300, p.host.state.myMs)
        assertEquals(300, p.client.state.theirMs)
        assertTrue(p.host.state.why.contains("200"))
    }

    @Test fun jumpingTheGunLosesTheRound() {
        val p = pair()
        p.advance(300)
        assertEquals("WAIT", p.host.state.step)
        p.client.tap(); p.pump()
        assertEquals("RESULT", p.host.state.step)
        assertEquals(2, p.client.state.roundWin) // they won
        assertEquals(1, p.host.state.roundWin)
        assertEquals(1, p.host.state.myScore)
        assertTrue(p.host.state.why.contains("jumped the gun"))
    }

    @Test fun onlyOneTapPerAttempt() {
        val p = pair()
        p.untilGo()
        p.advance(250); p.host.tap(); p.host.tap(); p.pump()
        assertTrue(p.host.state.iTapped)
        p.advance(700) // the other never taps -> host wins when the window closes
        assertEquals("RESULT", p.host.state.step)
        assertEquals(1, p.host.state.roundWin)
        assertEquals(1, p.host.state.myScore)
    }

    @Test fun nobodyTappingReplaysWithoutAPoint() {
        val p = pair()
        p.untilGo()
        p.advance(QuickRules.GO_TIMEOUT_MS + 200)
        assertEquals("RESULT", p.host.state.step)
        assertEquals(0, p.host.state.roundWin)
        assertEquals(0, p.host.state.myScore + p.host.state.theirScore)
        val seq = p.host.state.seq
        p.advance(QuickRules.RESULT_MS + 200)
        assertEquals("WAIT", p.host.state.step)
        assertEquals(seq + 1, p.host.state.seq)
        assertFalse(p.host.state.iTapped)
    }

    @Test fun fakeOutsAppearAndTappingOnThemLoses() {
        var found = false
        for (seed in 0 until 60) {
            val p = pair(fake = true, seed = seed)
            var guard = 0
            while (p.host.state.step == "WAIT" && guard++ < 200) p.advance(100)
            if (p.host.state.step == "FAKE") {
                assertEquals("FAKE", p.client.state.step)
                p.client.tap(); p.pump()
                assertEquals("RESULT", p.host.state.step)
                assertEquals(1, p.host.state.myScore)
                assertTrue(p.host.state.why.contains("fake-out"))
                found = true
                break
            }
        }
        assertTrue(found)
    }

    @Test fun untappedFakeOutsJustReplay() {
        for (seed in 0 until 60) {
            val p = pair(fake = true, seed = seed)
            var guard = 0
            while (p.host.state.step == "WAIT" && guard++ < 200) p.advance(100)
            if (p.host.state.step == "FAKE") {
                p.advance(QuickRules.FAKE_MS + 100)
                assertEquals("RESULT", p.host.state.step)
                assertEquals(0, p.host.state.roundWin)
                p.advance(2000)
                assertEquals("WAIT", p.host.state.step)
                return
            }
        }
    }

    @Test fun firstToTheTargetWinsTheMatch() {
        val p = pair(match = 0) // best of 3 -> first to 2
        repeat(2) {
            p.untilGo()
            p.advance(150); p.host.tap(); p.pump()
            p.advance(120); p.client.tap(); p.pump()
            if (p.host.state.phase == GPhase.PLAYING) p.advance(QuickRules.RESULT_MS + 200)
        }
        assertEquals(GPhase.OVER, p.host.state.phase)
        assertEquals(GPhase.OVER, p.client.state.phase)
        assertEquals(1, p.host.state.result)
        assertEquals(2, p.client.state.result)
        assertEquals(2, p.host.state.myScore)
    }

    @Test fun staleTapsFromAnOlderAttemptAreIgnored() {
        val p = pair()
        p.untilGo()
        p.advance(100); p.host.tap(); p.pump(); p.advance(700) // host wins attempt 1
        val score = p.host.state.myScore
        p.host.onMessage(GMsg.Act("QUICK", JSONObject().put("a", "tap").put("sq", 1).put("ph", "go").put("ms", 50)))
        assertEquals(score, p.host.state.myScore)
    }
}
