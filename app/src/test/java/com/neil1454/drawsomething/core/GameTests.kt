package com.neil1454.drawsomething.core

import org.json.JSONObject
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test
import kotlin.random.Random

// ====================================================================================== Bomb Find

/** Host + partner engines joined by in-memory queues, using the real wire encoding. */
class MinesPair(seed: Int, size: MineSize = MineSize.LARGE) {
    private val toClient = ArrayDeque<ByteArray>()
    private val toHost = ArrayDeque<ByteArray>()
    val host = MinesEngine("Hannah", "Gus", true, { toClient.add(it.encode()) }, Random(seed))
    val client = MinesEngine("Gus", "Hannah", false, { toHost.add(it.encode()) }, Random(seed + 1000))

    init {
        host.start(size)
        pump()
    }

    fun pump() {
        while (toClient.isNotEmpty() || toHost.isNotEmpty()) {
            toClient.removeFirstOrNull()?.let { b -> (Packet.decode(b) as Packet.Mines).let { client.onMessage(it.msg) } }
            toHost.removeFirstOrNull()?.let { b -> (Packet.decode(b) as Packet.Mines).let { host.onMessage(it.msg) } }
        }
    }

    val mover get() = if (host.state.myTurn) host else client
    val waiter get() = if (host.state.myTurn) client else host
    val size get() = host.state.size

    fun play(cell: Int) {
        mover.pick(cell % size.w, cell / size.w)
        pump()
    }

    fun adj(cell: Int): Int {
        val mines = host.secretMines().toSet()
        val x = cell % size.w
        val y = cell / size.w
        var n = 0
        for (dy in -1..1) for (dx in -1..1) {
            if (dx == 0 && dy == 0) continue
            val nx = x + dx
            val ny = y + dy
            if (nx in 0 until size.w && ny in 0 until size.h && (ny * size.w + nx) in mines) n++
        }
        return n
    }

    val center get() = (size.h / 2) * size.w + size.w / 2

    /** True if the last move only revealed squares (no power-up fired), so turns simply alternate. */
    fun lastMoveWasPlain() = host.state.notes.size == 1 && host.state.notes[0].kind == "safe"

    companion object {
        /** A game whose first move (in the centre) was plain: no shield, double, extra turn, etc. */
        fun plainStart(size: MineSize = MineSize.LARGE): MinesPair {
            for (seed in 0 until 500) {
                val p = MinesPair(seed, size)
                p.play(p.center)
                if (p.lastMoveWasPlain()) return p
            }
            error("no plain start found")
        }
    }
}

class MinesTest {
    /** A board where, after a first move in the centre, an unrevealed numbered square holds [item]. */
    private fun withItem(item: MineItem, mustNotBeSafeAfter: Boolean = false): Pair<MinesPair, Int> {
        for (seed in 0 until 3000) {
            val p = MinesPair(seed)
            p.play(p.center)
            if (!p.lastMoveWasPlain()) continue
            val cell = p.host.secretSafe().firstOrNull { p.host.secretItem(it) == item && p.adj(it) > 0 }
            if (cell != null) return p to cell
        }
        error("no board found with $item")
    }

    @Test fun bothPhonesStartWithTheSameEmptyBoardAndOppositeTurns() {
        val p = MinesPair(1)
        assertEquals(MinesPhase.PLAYING, p.host.state.phase)
        assertEquals(MinesPhase.PLAYING, p.client.state.phase)
        assertTrue(p.host.state.myTurn != p.client.state.myTurn)
        assertEquals(100, p.host.state.view.length)
        assertTrue(p.client.state.view.all { it == '.' })
        assertEquals(MineSize.LARGE, p.client.state.size)
    }

    @Test fun firstSquareIsAlwaysSafeAndOpensUp() {
        repeat(40) { seed ->
            val p = MinesPair(seed)
            val first = p.mover
            val second = p.waiter
            p.play(p.center)
            assertTrue("seed $seed", p.center !in p.host.secretMines())
            assertTrue(p.host.state.view[p.center] == '0') // neighbours are bomb-free too
            assertTrue(p.host.state.view.count { it != '.' } > 1)
            assertTrue(first.state.myScore > 0)
            assertEquals(first.state.myScore, second.state.theirScore)
        }
    }

    @Test fun turnsAlternateAndOnlyTheCurrentPlayerMayPick() {
        val q = MinesPair(3)
        q.waiter.pick(0, 0); q.pump() // not their turn
        assertTrue(q.host.state.view.all { it == '.' })

        val p = MinesPair.plainStart()
        val first = p.waiter // just moved
        val second = p.mover
        assertTrue(second.state.myTurn)
        assertFalse(first.state.myTurn)
        // picking a square that's already open does nothing
        second.pick(p.size.w / 2, p.size.h / 2); p.pump()
        assertTrue(second.state.myTurn)
    }

    @Test fun clientCanOnlyActViaTheHostAndSeesNoSecrets() {
        val p = MinesPair(4)
        // Whatever the partner knows is only the revealed view: no 'M' before the end.
        p.play(p.center)
        if (!p.host.state.myTurn) p.play(p.host.secretSafe().first { p.adj(it) > 0 })
        assertTrue(p.client.state.view.none { it == 'M' })
        assertEquals(p.host.state.view, p.client.state.view)
    }

    @Test fun hittingABombCostsPointsEndsTurnButNotTheGame() {
        val p = MinesPair.plainStart()
        val a = p.waiter // made the first move, has points
        val b = p.mover
        // b makes a plain safe move first, then it's a's turn
        p.play(p.host.secretSafe().first { p.adj(it) > 0 && p.host.secretItem(it) == null })
        assertTrue(a.state.myTurn)
        val before = a.state.myScore
        val minesBefore = a.state.minesLeft
        val bomb = p.host.secretMines().first()
        p.play(bomb)
        assertEquals(before - MineRules.MINE_PENALTY, a.state.myScore)
        assertEquals('X', p.host.state.view[bomb])
        assertEquals(MinesPhase.PLAYING, p.host.state.phase)
        assertTrue(b.state.myTurn) // turn passed
        assertEquals("boom", a.state.notes.first().kind)
        assertEquals(minesBefore - 1, a.state.minesLeft)
        assertEquals(a.state.myScore, b.state.theirScore)
    }

    @Test fun scoreCanNeverGoBelowZero() {
        val p = MinesPair.plainStart()
        val victim = p.mover // hasn't scored yet
        assertEquals(0, victim.state.myScore)
        p.play(p.host.secretMines().first())
        assertEquals(0, victim.state.myScore)
        assertEquals("boom", victim.state.notes.first().kind)
        assertEquals(0, victim.state.notes.first().value) // nothing to lose
    }

    @Test fun bonusItemGivesBonusPoints() {
        val (p, cell) = withItem(MineItem.BONUS)
        val actor = p.mover
        val before = actor.state.myScore
        p.play(cell)
        assertEquals(before + MineRules.SAFE_POINTS + MineRules.BONUS_POINTS, actor.state.myScore)
        assertTrue(p.host.state.items[cell] == 'B')
        assertTrue(actor.state.notes.any { it.kind == "item_B" && it.byMe })
    }

    @Test fun extraTurnLetsTheSamePlayerGoAgain() {
        val (p, cell) = withItem(MineItem.EXTRA)
        val actor = p.mover
        p.play(cell)
        assertTrue(actor.state.myTurn)
        assertFalse(p.waiter.state.myTurn)
    }

    @Test fun stealTakesPointsFromTheOpponent() {
        val (p, cell) = withItem(MineItem.STEAL)
        val actor = p.mover
        val victim = p.waiter
        val a0 = actor.state.myScore
        val v0 = victim.state.myScore
        p.play(cell)
        val taken = minOf(v0, MineRules.STEAL_POINTS)
        assertEquals(v0 - taken, victim.state.myScore)
        assertEquals(a0 + MineRules.SAFE_POINTS + taken, actor.state.myScore)
    }

    @Test fun doubleItemDoublesTheNextReveal() {
        val (p, cell) = withItem(MineItem.DOUBLE)
        val actor = p.mover
        p.play(cell)
        assertTrue("double armed", actor.state.myDouble)
        assertTrue("opponent can see it", p.mover.state.theirDouble)
        // other player moves, then actor reveals a numbered square: 10 doubled to 20
        p.play(p.host.secretSafe().first { p.adj(it) > 0 && p.host.secretItem(it) == null })
        assertTrue("turn back to actor", actor === p.mover)
        val before = actor.state.myScore
        p.play(p.host.secretSafe().first { p.adj(it) > 0 && p.host.secretItem(it) == null })
        assertEquals(before + 2 * MineRules.SAFE_POINTS, actor.state.myScore)
        assertFalse(actor.state.myDouble)
        assertEquals("safe2", actor.state.notes.first().kind)
    }

    @Test fun shieldBlocksTheNextBombOnce() {
        val (p, cell) = withItem(MineItem.SHIELD)
        val actor = p.mover
        p.play(cell)
        assertTrue(actor.state.myShield)
        p.play(p.host.secretSafe().first { p.adj(it) > 0 && p.host.secretItem(it) == null })
        assertTrue(actor === p.mover)
        val before = actor.state.myScore
        val bomb = p.host.secretMines().first()
        p.play(bomb)
        assertEquals(before + MineRules.DEFUSE_BONUS, actor.state.myScore)
        assertEquals('x', p.host.state.view[bomb])
        assertFalse(actor.state.myShield)
        assertEquals("block", actor.state.notes.first().kind)
    }

    @Test fun radarFlagsAHiddenBombForBothPlayersAndItCannotBePicked() {
        val (p, cell) = withItem(MineItem.RADAR)
        p.play(cell)
        val flagged = p.host.state.view.indexOf('F')
        assertTrue(flagged >= 0)
        assertEquals(p.host.state.view, p.client.state.view)
        assertTrue(flagged in p.host.secretMines())
        val turnBefore = p.host.state.myTurn
        p.mover.pick(flagged % p.size.w, flagged / p.size.w); p.pump() // ignored
        assertEquals(turnBefore, p.host.state.myTurn)
    }

    @Test fun fullGameEndsWhenEverySafeSquareIsOpenAndBombsAreRevealed() {
        for (size in MineSize.values()) {
            val p = MinesPair(11, size)
            p.play(p.center)
            var guard = 0
            while (p.host.state.phase == MinesPhase.PLAYING && guard++ < 1000) {
                p.play(p.host.secretSafe().first())
            }
            assertEquals(MinesPhase.OVER, p.host.state.phase)
            assertEquals(MinesPhase.OVER, p.client.state.phase)
            assertEquals(0, p.host.state.safeLeft)
            assertEquals(p.host.state.view, p.client.state.view)
            assertEquals(size.mines, p.host.state.view.count { it == 'M' || it == 'F' })
            assertEquals(p.host.state.myScore, p.client.state.theirScore)
            assertEquals(p.host.state.theirScore, p.client.state.myScore)
            assertTrue(p.host.state.myScore + p.host.state.theirScore > 0)
            // no more moves once it's over
            val before = p.host.state.view
            p.mover.pick(0, 0); p.pump()
            assertEquals(before, p.host.state.view)
        }
    }

    @Test fun rematchStartsFresh() {
        val p = MinesPair(12, MineSize.SMALL)
        p.play(p.center)
        while (p.host.state.phase == MinesPhase.PLAYING) p.play(p.host.secretSafe().first())
        p.host.start(MineSize.MEDIUM); p.pump()
        assertEquals(MinesPhase.PLAYING, p.client.state.phase)
        assertEquals(MineSize.MEDIUM, p.client.state.size)
        assertEquals(0, p.client.state.myScore)
        assertTrue(p.client.state.view.all { it == '.' })
    }

    @Test fun badMessagesAreRejected() {
        fun d(json: String) = Packet.decode(json.toByteArray())
        assertNull(d("""{"t":"m_state","view":"..","items":".","hs":0,"cs":0,"ht":true,"hsh":false,"csh":false,"hd":false,"cd":false,"over":false,"left":1,"mines":1,"ev":[]}"""))
        assertNull(d("""{"t":"m_state","view":"<<","items":"..","hs":0,"cs":0,"ht":true,"hsh":false,"csh":false,"hd":false,"cd":false,"over":false,"left":1,"mines":1,"ev":[]}"""))
        val ok = d("""{"t":"m_state","view":"..","items":"..","hs":-5,"cs":999999999,"ht":true,"hsh":false,"csh":false,"hd":false,"cd":false,"over":false,"left":1,"mines":1,"ev":[]}""")
        assertEquals(0, ((ok as Packet.Mines).msg as MineMsg.State).hostScore)
        assertEquals(100_000, (ok.msg as MineMsg.State).clientScore)
        assertEquals(MineMsg.Pick(99, 0), (d("""{"t":"m_pick","x":500,"y":-4}""") as Packet.Mines).msg)
        // wrong-size state from a bad host is ignored by the engine
        val e = MinesEngine("a", "b", false, {}, Random(1))
        e.onMessage(MineMsg.Setup(0, true))
        e.onMessage(MineMsg.State("....", "....", 5, 5, true, false, false, false, false, false, 1, 1, emptyList()))
        assertEquals(0, e.state.myScore)
    }

    @Test fun clientCannotStartAGameOrMoveForTheHost() {
        val p = MinesPair(13)
        p.client.start(MineSize.SMALL); p.pump()
        assertEquals(MineSize.LARGE, p.host.state.size)
        // a forged Pick from the partner on the host's turn is ignored
        val hostTurn = p.host.state.myTurn
        if (hostTurn) {
            p.host.onMessage(MineMsg.Pick(0, 0))
            assertTrue(p.host.state.view.all { it == '.' })
        }
    }
}

// ====================================================================================== Sea Battle

class SeaPair(seed: Int, size: SeaSize = SeaSize.CLASSIC) {
    private val toClient = ArrayDeque<ByteArray>()
    private val toHost = ArrayDeque<ByteArray>()
    val host = SeaEngine("Hannah", "Gus", true, { toClient.add(it.encode()) }, Random(seed))
    val client = SeaEngine("Gus", "Hannah", false, { toHost.add(it.encode()) }, Random(seed + 77))

    init {
        host.start(size)
        pump()
    }

    fun pump() {
        while (toClient.isNotEmpty() || toHost.isNotEmpty()) {
            toClient.removeFirstOrNull()?.let { b -> (Packet.decode(b) as Packet.Sea).let { client.onMessage(it.msg) } }
            toHost.removeFirstOrNull()?.let { b -> (Packet.decode(b) as Packet.Sea).let { host.onMessage(it.msg) } }
        }
    }

    fun battle(): SeaPair {
        host.ready(); client.ready(); pump()
        return this
    }

    val shooter get() = if (host.state.myTurn) host else client
    val target get() = if (host.state.myTurn) client else host
}

class SeaTest {
    private fun cell(e: SeaEngine, c: Int) = (c % e.state.size.w) to (c / e.state.size.w)

    @Test fun fleetsAreValidAndHidden() {
        for (size in SeaSize.values()) repeat(30) { seed ->
            val p = SeaPair(seed, size)
            for (e in listOf(p.host, p.client)) {
                assertEquals(SeaPhase.PLACING, e.state.phase)
                val fleet = e.fleetCells()
                assertEquals(size.fleet.sorted(), fleet.map { it.size }.sorted())
                val all = fleet.flatten()
                assertEquals(all.size, all.toSet().size) // no overlaps
                assertTrue(all.all { it in 0 until size.w * size.h })
                fleet.forEach { ship -> // straight lines
                    val xs = ship.map { it % size.w }.toSet()
                    val ys = ship.map { it / size.w }.toSet()
                    assertTrue(xs.size == 1 || ys.size == 1)
                }
                assertEquals(all.size, e.state.myGrid.count { it == 'S' })
                assertTrue(e.state.enemyGrid.all { it == '.' }) // nothing known about the enemy
            }
        }
    }

    @Test fun shuffleChangesFleetUntilReady() {
        val p = SeaPair(1)
        val before = p.host.fleetCells()
        p.host.shuffleFleet()
        assertTrue(before != p.host.fleetCells())
        p.host.ready(); p.pump()
        val locked = p.host.fleetCells()
        p.host.shuffleFleet()
        assertEquals(locked, p.host.fleetCells())
        assertEquals(SeaPhase.PLACING, p.host.state.phase) // partner isn't ready yet
        p.client.ready(); p.pump()
        assertEquals(SeaPhase.BATTLE, p.host.state.phase)
        assertEquals(SeaPhase.BATTLE, p.client.state.phase)
        assertTrue(p.host.state.myTurn != p.client.state.myTurn)
    }

    @Test fun hitLetsYouFireAgainAndMissPassesTheTurn() {
        val p = SeaPair(2).battle()
        val a = p.shooter
        val b = p.target
        val shipCell = b.fleetCells().first().first()
        val (hx, hy) = cell(b, shipCell)
        a.fire(hx, hy); p.pump()
        assertEquals('x', a.state.enemyGrid[shipCell])
        assertEquals('x', b.state.myGrid[shipCell])
        assertTrue(a.state.myTurn)
        assertFalse(b.state.myTurn)
        assertEquals(1, a.state.hits)

        val water = (0 until a.state.size.w * a.state.size.h).first { c -> b.fleetCells().none { c in it } }
        val (wx, wy) = cell(b, water)
        a.fire(wx, wy); p.pump()
        assertEquals('o', a.state.enemyGrid[water])
        assertEquals('o', b.state.myGrid[water])
        assertFalse(a.state.myTurn)
        assertTrue(b.state.myTurn)
        assertEquals(2, a.state.shots)
    }

    @Test fun cannotFireOutOfTurnTwiceAtTheSameSquareOrBeforeBattle() {
        val p0 = SeaPair(3)
        p0.host.fire(0, 0); p0.pump()
        assertEquals(0, p0.host.state.shots)

        val p = SeaPair(3).battle()
        val a = p.shooter
        val b = p.target
        b.fire(0, 0); p.pump()
        assertEquals(0, b.state.shots)
        a.fire(0, 0); p.pump()
        assertEquals(1, a.state.shots)
        if (a.state.myTurn) { // it was a hit: the same square again is refused
            a.fire(0, 0); p.pump()
            assertEquals(1, a.state.shots)
        }
        a.fire(-1, 0); a.fire(0, 99); p.pump()
        assertEquals(1, a.state.shots)
    }

    @Test fun sinkingAShipMarksItAndCountsDown() {
        val p = SeaPair(4).battle()
        val a = p.shooter
        val b = p.target
        val ship = b.fleetCells().minByOrNull { it.size }!!
        ship.forEach { c -> val (x, y) = cell(b, c); a.fire(x, y); p.pump() }
        assertTrue(ship.all { a.state.enemyGrid[it] == '#' })
        assertEquals(b.state.size.fleet.size - 1, a.state.theirShipsLeft)
        assertEquals(b.state.size.fleet.size - 1, b.state.myShipsLeft)
        assertTrue(a.state.myTurn) // all hits: still their turn
        assertEquals("You sank a ship!", a.state.note)
        assertTrue(b.state.note!!.contains("sank"))
    }

    @Test fun radarCountsShipSquaresOnceAndCostsTheTurn() {
        val p = SeaPair(5).battle()
        val a = p.shooter
        val b = p.target
        val ship = b.fleetCells().first()
        val (cx, cy) = cell(b, ship[ship.size / 2])
        val size = b.state.size
        var expected = 0
        for (dy in -1..1) for (dx in -1..1) {
            val x = cx + dx; val y = cy + dy
            if (x in 0 until size.w && y in 0 until size.h && b.fleetCells().any { (y * size.w + x) in it }) expected++
        }
        a.selectWeapon(SeaWeapon.RADAR)
        a.fire(cx, cy); p.pump()
        assertEquals(expected, a.state.radarCount)
        assertEquals(cx, a.state.radarX)
        assertFalse(a.state.radarLeft)
        assertEquals(0, a.state.shots) // radar doesn't fire
        assertTrue(a.state.enemyGrid.all { it == '.' })
        assertFalse(a.state.myTurn)
        assertTrue(b.state.myTurn)
        // can't pick it again
        b.fire(0, 0); p.pump() // b's turn, normal shot
        a.selectWeapon(SeaWeapon.RADAR)
        assertEquals(SeaWeapon.SHOT, a.state.weapon)
    }

    @Test fun clusterBombHitsAPlusShapeOnceAndAlwaysPassesTheTurn() {
        val p = SeaPair(6).battle()
        val a = p.shooter
        val b = p.target
        val size = b.state.size
        // aim at the middle of the longest ship so there are hits in the pattern
        val ship = b.fleetCells().maxByOrNull { it.size }!!
        val centre = ship[ship.size / 2]
        val (cx, cy) = cell(b, centre)
        a.selectWeapon(SeaWeapon.BOMB)
        a.fire(cx, cy); p.pump()
        val plus = listOf(cx to cy, cx - 1 to cy, cx + 1 to cy, cx to cy - 1, cx to cy + 1)
            .filter { (x, y) -> x in 0 until size.w && y in 0 until size.h }
        assertEquals(plus.size, a.state.shots)
        plus.forEach { (x, y) -> assertTrue(a.state.enemyGrid[y * size.w + x] in "xo#") }
        assertTrue(a.state.hits >= 1)
        assertFalse(a.state.bombLeft)
        assertFalse(a.state.myTurn) // even with hits
        assertTrue(b.state.myTurn)
    }

    @Test fun fullGamesFinishAndBothPhonesAgree() {
        for (size in SeaSize.values()) repeat(15) { seed ->
            val p = SeaPair(seed, size).battle()
            val rnd = Random(seed)
            var guard = 0
            while (p.host.state.phase == SeaPhase.BATTLE && guard++ < 2000) {
                val a = p.shooter
                // use specials sometimes
                if (a.state.radarLeft && rnd.nextInt(10) == 0) a.selectWeapon(SeaWeapon.RADAR)
                else if (a.state.bombLeft && rnd.nextInt(10) == 0) a.selectWeapon(SeaWeapon.BOMB)
                val unknown = a.state.enemyGrid.indices.filter { a.state.enemyGrid[it] == '.' }
                val c = unknown.random(rnd)
                a.fire(c % size.w, c / size.w); p.pump()
            }
            assertEquals(SeaPhase.OVER, p.host.state.phase)
            assertEquals(SeaPhase.OVER, p.client.state.phase)
            assertTrue(p.host.state.iWon != p.client.state.iWon)
            val winner = if (p.host.state.iWon == true) p.host else p.client
            val loser = if (winner === p.host) p.client else p.host
            assertEquals(0, loser.state.myShipsLeft)
            assertEquals(0, winner.state.theirShipsLeft)
            assertTrue(winner.state.myShipsLeft > 0 || loser.state.myShipsLeft == 0)
            assertEquals(loser.fleetCells().flatten().size, winner.state.enemyGrid.count { it == '#' })
            // nothing more can be fired
            val shots = winner.state.shots
            winner.fire(0, 0); loser.fire(0, 0); p.pump()
            assertEquals(shots, winner.state.shots)
        }
    }

    @Test fun rematchResetsAndPartnerCannotStart() {
        val p = SeaPair(8, SeaSize.QUICK).battle()
        p.client.start(SeaSize.CLASSIC); p.pump()
        assertEquals(SeaSize.QUICK, p.host.state.size)
        p.host.start(SeaSize.CLASSIC); p.pump()
        assertEquals(SeaPhase.PLACING, p.client.state.phase)
        assertEquals(SeaSize.CLASSIC, p.client.state.size)
        assertFalse(p.client.state.iAmReady)
        assertEquals(0, p.client.state.shots)
        assertNotNull(p.client.fleetCells().firstOrNull())
    }

    @Test fun badMessagesAreRejected() {
        fun d(json: String) = Packet.decode(json.toByteArray())
        val r = (d("""{"t":"s_result","w":"EVIL","x":-3,"y":500,"cells":[1,2,1,3],"sunk":[],"sc":99,"radar":50,"over":false}""") as Packet.Sea).msg as SeaMsg.Result
        assertEquals("SHOT", r.weapon)
        assertEquals(3, r.cells.size) // only whole x,y,outcome triples
        assertEquals(10, r.sunkCount)
        assertEquals(9, r.radar)
        assertNull(d("""{"t":"s_nope"}"""))
        // an out-of-range shot from a hostile partner is ignored and does not crash
        val p = SeaPair(9).battle()
        p.target.onMessage(SeaMsg.Shoot("SHOT", 50, 50))
        p.shooter.onMessage(SeaMsg.Result("SHOT", 0, 0, listOf(90, 90, 1), listOf(5000), 1, 0, false))
    }
}

class RouterTest {
    @Test fun packetsRouteToTheRightGame() {
        assertEquals(Packet.Lobby(GameKind.SEA), Packet.decode(LobbyMsg.encodePick(GameKind.SEA)))
        assertTrue(Packet.decode(Msg.Guess("tree").encode()) is Packet.Draw)
        assertTrue(Packet.decode(MineMsg.Pick(1, 2).encode()) is Packet.Mines)
        assertTrue(Packet.decode(SeaMsg.Ready.encode()) is Packet.Sea)
        assertNull(Packet.decode("""{"t":"l_pick","g":"HACK"}""".toByteArray()))
        assertNull(Packet.decode(ByteArray(Msg.MAX_BYTES + 1)))
        assertNull(Packet.decode("garbage".toByteArray()))
        // every message type survives a round trip
        val mine = MineMsg.State("..", "..", 1, 2, true, false, true, false, true, false, 3, 4, listOf(MineEvent("safe", true, 10, 1)))
        assertEquals(Packet.Mines(mine), Packet.decode(mine.encode()))
        val res = SeaMsg.Result("BOMB", 1, 2, listOf(1, 2, 1), listOf(4, 5), 1, 0, false)
        assertEquals(Packet.Sea(res), Packet.decode(res.encode()))
        assertEquals(Packet.Sea(SeaMsg.Setup(1, true)), Packet.decode(SeaMsg.Setup(1, true).encode()))
        assertEquals(Packet.Sea(SeaMsg.Shoot("RADAR", 3, 4)), Packet.decode(SeaMsg.Shoot("RADAR", 3, 4).encode()))
        assertEquals(Packet.Mines(MineMsg.Setup(2, false)), Packet.decode(MineMsg.Setup(2, false).encode()))
        assertEquals(JSONObject(String(LobbyMsg.encodePick(GameKind.MINES))).getString("g"), "MINES")
    }
}
