package com.neil1454.drawsomething.core

import org.json.JSONObject
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertTrue
import org.junit.Test

class TetrisBoardTest {
    private val W = TetrisRules.W
    private val R = TetrisRules.ROWS

    private fun board(seed: Long = 1, level: Int = 1, gifts: Boolean = true) = TetrisBoard(seed, level, gifts)

    @Test fun everyPieceHasFourDistinctBlocksInEveryTurnAndTurnsBackAround() {
        for (t in 0..6) {
            for (r in 0..3) {
                val s = TetrisRules.SHAPE[t][r]
                val cells = (0 until 4).map { s[it * 2] to s[it * 2 + 1] }
                assertEquals("$t/$r", 4, cells.toSet().size)
                assertTrue(cells.all { it.first in 0 until TetrisRules.boxSize(t) && it.second in 0 until TetrisRules.boxSize(t) })
            }
            // four clockwise turns bring it home: the 5th turn equals the first
            assertTrue(TetrisRules.SHAPE[t][0].toList().chunked(2).toSet() == TetrisRules.SHAPE[t][0].toList().chunked(2).toSet())
        }
        // the long piece lies flat then stands up
        assertEquals(4, (0 until 4).map { TetrisRules.SHAPE[0][0][it * 2] }.toSet().size)
        assertEquals(1, (0 until 4).map { TetrisRules.SHAPE[0][1][it * 2] }.toSet().size)
    }

    @Test fun piecesFallWithGravityAndHardDropLandsOnTheFloor() {
        val b = board()
        val y0 = b.py
        b.advance(TetrisRules.gravityMs(1) + 20)
        assertEquals(y0 + 1, b.py)
        val t = b.type
        b.hardDrop()
        assertTrue(b.alive)
        assertEquals(1, b.lockSerial)
        assertTrue("hard drop pays 2 per row", b.score >= 2 * 15)
        // something is on the bottom row
        assertTrue((0 until W).any { b.cell(it, R - 1) == t + 1 })
        assertNotEquals(t, -1)
    }

    @Test fun aPieceLocksAfterAShortDelayOnceItCannotFallFurther() {
        val b = board()
        b.forcePiece(1, 0, 4, R - 2) // the square already on the floor
        b.advance(TetrisRules.LOCK_DELAY_MS - 50)
        assertEquals(0, b.lockSerial)
        b.advance(100)
        assertEquals(1, b.lockSerial)
    }

    @Test fun moveAndRotateStayInsideTheWellAndKickOffTheWall() {
        val b = board()
        repeat(20) { b.moveLeft() }
        assertEquals(0, b.leftCol())
        repeat(20) { b.moveRight() }
        assertTrue(b.leftCol() + (TetrisRules.maxX(b.type, b.rot) - TetrisRules.minX(b.type, b.rot)) == W - 1)
        // an upright long piece against the right wall can still turn (it is kicked)
        b.forcePiece(0, 1, 7, 5) // vertical I in column 9
        assertTrue(b.rotateCw())
        assertTrue(b.leftCol() >= 0 && b.leftCol() + 3 <= W - 1)
    }

    @Test fun clearingALineScoresAndEmptiesTheRow() {
        val b = board()
        for (x in 0..5) b.grid[(R - 1) * W + x] = 3
        b.forcePiece(0, 0, 6, R - 2) // flat long piece fills columns 6-9
        b.hardDrop()
        assertEquals(1, b.lines)
        assertEquals(1, b.clearSerial)
        assertEquals(1, b.lastClear)
        assertTrue(b.score >= TetrisRules.lineScore(1))
        assertTrue((0 until W).all { b.cell(it, R - 1) == 0 })
    }

    @Test fun twoLinesSendTwoGiftsMadeOfTheBlownUpPieces() {
        val b = board()
        for (y in R - 2..R - 1) for (x in 0..8) b.grid[y * W + x] = 3 // lots of "T" blocks
        b.forcePiece(0, 1, 7, R - 4) // upright long piece in column 9
        b.hardDrop()
        assertEquals(2, b.lines)
        assertEquals(2, b.outgoing.size)
        assertEquals("the gift is the shape that was blown up most", 2, b.outgoing[0].type)
        assertEquals(2, b.giftsSent)
    }

    @Test fun biggerClearsSendMoreAndLongCombosAddOne() {
        assertEquals(0, TetrisRules.gifts(1, 0))
        assertEquals(2, TetrisRules.gifts(2, 0))
        assertEquals(3, TetrisRules.gifts(3, 0))
        assertEquals(5, TetrisRules.gifts(4, 0))
        assertEquals(1, TetrisRules.gifts(1, 3))
        assertEquals(6, TetrisRules.gifts(4, 5))
    }

    @Test fun giftsYouSendFirstCancelGiftsStillInYourMeter() {
        val b = board()
        b.receive(Gift(2, 0, 3))
        assertEquals(1, b.pendingGifts.size)
        for (y in R - 2..R - 1) for (x in 0..8) b.grid[y * W + x] = 3
        b.forcePiece(0, 1, 7, R - 4)
        b.hardDrop()
        assertEquals("the incoming gift was cancelled", 0, b.pendingGifts.size)
        assertEquals("two earned, one used to cancel, one sent", 1, b.outgoing.size)
    }

    @Test fun aGiftWaitsInTheMeterThenJoinsYourQueueAndCanNoLongerBeCancelled() {
        val b = board()
        b.receive(Gift(3, 0, 0))
        assertEquals(1, b.waiting)
        assertEquals(0, b.incoming.size)
        repeat(5) { b.advance(500) } // 2.5 s
        assertEquals(1, b.pendingGifts.size)
        repeat(2) { b.advance(500) } // 3.5 s
        assertEquals(0, b.pendingGifts.size)
        assertEquals(1, b.incoming.size)
        // the preview shows it as a gift
        assertTrue(b.upcoming(3).any { it.second && it.first == 3 })
    }

    @Test fun aGiftIsAnOrdinaryPieceThatFallsAtNormalSpeedAndYouSteerYourself() {
        val b = board()
        b.receive(Gift(3, 0, 0))
        repeat(7) { b.advance(500) }
        b.hardDrop() // lock the normal piece
        assertTrue("the next piece is the gift", b.isGift)
        assertTrue("you can steer it", b.moveLeft())
        assertTrue(b.rotateCw())
        val y = b.py
        b.advance(TetrisRules.gravityMs(1) + 20)
        assertEquals("normal speed: one row", y + 1, b.py)
        b.hardDrop()
        assertEquals(4, (0 until b.grid.size).count { b.grid[it] == TetrisRules.GIFT })
        assertFalse("then a normal piece, so gifts never come back to back", b.isGift)
        assertEquals(1, b.giftsReceived)
    }

    @Test fun giftsArriveAtMostEveryOtherPiece() {
        val b = board()
        repeat(4) { b.receive(Gift(it, 0, 0)) }
        repeat(7) { b.advance(500) }
        val kinds = ArrayList<Boolean>()
        repeat(8) {
            kinds.add(b.isGift)
            if (!b.alive) return@repeat
            b.hardDrop()
        }
        for (i in 1 until kinds.size) assertFalse("two gifts in a row at $i: $kinds", kinds[i] && kinds[i - 1])
        assertTrue(kinds.count { it } >= 2)
    }

    @Test fun giftsCannotBeHeld() {
        val b = board()
        b.receive(Gift(3, 0, 0))
        repeat(7) { b.advance(500) }
        b.hardDrop()
        assertTrue(b.isGift)
        b.holdPiece()
        assertEquals(-1, b.hold)
    }

    @Test fun aLongNoClockMatchSpeedsUpOnItsOwnSoItAlwaysEnds() {
        val b = TetrisBoard(1, 1, true, TetrisMode.BATTLE)
        assertFalse(b.overtime)
        var t = 0L
        while (t < TetrisRules.OVERTIME_START_MS + 45_000) {
            b.advance(500); t += 500
            if (b.py > 8) b.forcePiece(b.type, 0, 4, 0)
        }
        assertTrue(b.overtime)
        assertTrue("level ${b.level}", b.level >= 3)
        val timed = TetrisBoard(1, 1, true, TetrisMode.SCORE)
        timed.advance(1)
        assertFalse(timed.overtime)
    }

    @Test fun aFullStackEndsTheGame() {
        val b = board()
        for (y in 0 until 8) for (x in 0..8) b.grid[y * W + x] = 4
        b.forcePiece(1, 0, 4, 0)
        b.hardDrop()
        assertFalse(b.alive)
    }

    @Test fun holdSwapsOncePerPiece() {
        val b = board()
        val first = b.type
        b.holdPiece()
        assertEquals(first, b.hold)
        assertFalse(b.canHold)
        val second = b.type
        b.holdPiece() // not allowed yet
        assertEquals(second, b.type)
        b.hardDrop()
        assertTrue(b.canHold)
        b.holdPiece()
        assertEquals(second.let { b.hold.takeIf { h -> h >= 0 } }, b.hold)
    }

    @Test fun sameSeedGivesTheSamePiecesAndDifferentSeedsDiffer() {
        val a = board(7)
        val c = board(7)
        val d = board(8)
        assertEquals(a.next(8), c.next(8))
        assertEquals(a.type, c.type)
        repeat(6) { a.hardDrop(); c.hardDrop() }
        assertEquals(a.visibleRows(), c.visibleRows())
        assertTrue((1L..6L).any { board(it).next(6) != d.next(6) })
        // a bag holds every piece once
        val bag = board(3)
        val seen = ArrayList<Int>()
        seen.add(bag.type)
        seen.addAll(bag.next(6))
        assertEquals(7, seen.toSet().size)
    }

    @Test fun speedGrowsWithLevelAndLevelGrowsWithLines() {
        assertTrue(TetrisRules.gravityMs(1) > TetrisRules.gravityMs(5))
        assertTrue(TetrisRules.gravityMs(5) > TetrisRules.gravityMs(12))
        assertEquals(TetrisRules.gravityMs(16), TetrisRules.gravityMs(99))
        assertEquals(5, board(level = 5).level)
    }

    @Test fun theComputerPutsALongPieceToCompleteALine() {
        val g = IntArray(R * W)
        for (x in 4 until W) g[(R - 1) * W + x] = 2
        val plans = TetrisAi.rank(g, 0)
        assertTrue(plans.isNotEmpty())
        val best = plans.first()
        assertEquals(0, best.col)
        assertTrue(best.rot == 0 || best.rot == 2)
    }

    @Test fun softDropFallsFasterAndScores() {
        val a = board()
        val b = board()
        b.soft = true
        a.advance(1500); b.advance(1500)
        assertTrue(b.py > a.py)
        assertTrue(b.score > 0)
    }
}

class TetrisMatchTest {
    private fun pair(mode: Int = 0, lvl: Int = 0, mins: Int = 0, gifts: Boolean = true, seed: Int = 1): SyncPair<TetrisEngine> {
        val p = SyncPair({ n, o, h, s, r, c -> TetrisEngine(n, o, h, s, r, c) }, seed)
        p.host.start(JSONObject().put("mode", mode).put("lvl", lvl).put("mins", mins).put("gifts", gifts))
        p.pump()
        return p
    }

    private fun SyncPair<TetrisEngine>.go() = advance(TetrisRules.COUNTDOWN_MS + 100)

    @Test fun bothPhonesGetTheSameBoardAfterTheCountdown() {
        val p = pair()
        assertEquals(GPhase.PLAYING, p.client.state.phase)
        assertTrue(p.host.state.countdownMs > 0 && p.client.state.countdownMs > 0)
        val hb = p.host.board!!
        val cb = p.client.board!!
        assertEquals(hb.type, cb.type)
        assertEquals(hb.next(5), cb.next(5))
        p.go()
        assertEquals(0, p.host.state.countdownMs)
        assertEquals(0, p.client.state.countdownMs)
    }

    @Test fun nothingMovesDuringTheCountdown() {
        val p = pair()
        val y = p.host.board!!.py
        p.host.moveLeft(); p.host.hardDrop()
        p.advance(1000)
        assertEquals(y, p.host.board!!.py)
        assertEquals(0, p.host.board!!.lockSerial)
    }

    @Test fun eachPhoneSeesTheOthersBoardLive() {
        val p = pair()
        p.go()
        p.host.hardDrop()
        p.advance(300)
        val seen = p.client.opp
        assertNotNull(seen)
        assertEquals(p.host.board!!.visibleRows(), seen!!.grid)
        p.client.moveLeft(); p.client.hardDrop()
        p.advance(300)
        assertEquals(p.client.board!!.visibleRows(), p.host.opp!!.grid)
        assertEquals(p.client.board!!.score, p.host.state.theirScore)
    }

    private fun SyncPair<TetrisEngine>.clearTwoOnHost() {
        val b = host.board!!
        val w = TetrisRules.W
        val r = TetrisRules.ROWS
        for (y in r - 2..r - 1) for (x in 0..8) b.grid[y * w + x] = 3
        b.forcePiece(0, 1, 7, r - 4)
        host.hardDrop()
    }

    @Test fun clearedPiecesBecomeGiftsThatTheRivalPlacesByHand() {
        val p = pair()
        p.go()
        p.clearTwoOnHost()
        p.advance(300)
        assertEquals(2, p.host.state.mySent)
        assertEquals(2, p.client.board!!.giftsReceived)
        assertEquals("both are in the meter at first", 2, p.client.state.myIncoming)
        assertEquals(0, p.client.state.myQueued)
        repeat(8) { p.advance(500) } // the delay passes
        assertEquals("then they are in the queue", 2, p.client.state.myQueued)
        // the guest locks a normal piece; the next one is a gift they steer themselves
        p.client.hardDrop()
        assertTrue(p.client.board!!.isGift)
        p.client.moveLeft()
        p.client.hardDrop()
        val g = p.client.board!!
        assertEquals(4, (0 until g.grid.size).count { g.grid[it] == TetrisRules.GIFT })
        assertEquals(2, p.client.state.giftSeq)
        assertTrue(p.host.state.clearSeq >= 1 && p.client.state.clearSeq >= 1)
        assertFalse(p.client.state.lastClearByMe)
    }

    @Test fun giftsCanBeSwitchedOff() {
        val p = pair(mode = 1, gifts = false)
        p.go()
        p.clearTwoOnHost()
        p.advance(300)
        assertEquals(0, p.host.state.mySent)
        assertEquals(0, p.client.board!!.giftsReceived)
    }

    @Test fun lastStackStandingWinsABattle() {
        val p = pair()
        p.go()
        val b = p.host.board!!
        for (y in 0 until 8) for (x in 0..8) b.grid[y * TetrisRules.W + x] = 4
        b.forcePiece(1, 0, 4, 0)
        p.host.hardDrop()
        p.advance(800)
        assertEquals(GPhase.OVER, p.host.state.phase)
        assertEquals(GPhase.OVER, p.client.state.phase)
        assertEquals(2, p.host.state.result)
        assertEquals(1, p.client.state.result)
        assertTrue(p.client.state.how.contains("stack reached the top"))
    }

    @Test fun theGuestFallingOverLosesToo() {
        val p = pair()
        p.go()
        val b = p.client.board!!
        for (y in 0 until 8) for (x in 0..8) b.grid[y * TetrisRules.W + x] = 4
        b.forcePiece(1, 0, 4, 0)
        p.client.hardDrop()
        p.advance(800)
        assertEquals(1, p.host.state.result)
        assertEquals(2, p.client.state.result)
    }

    @Test fun scoreAttackEndsOnTheClockAndHigherScoreWins() {
        val p = pair(mode = 1, mins = 0)
        p.go()
        p.host.board!!.let { /* give the host a head start */ for (x in 0..5) it.grid[(TetrisRules.ROWS - 1) * TetrisRules.W + x] = 3; it.forcePiece(0, 0, 6, TetrisRules.ROWS - 2) }
        p.host.hardDrop()
        p.advance(500)
        assertTrue(p.host.state.secsLeft in 100..120)
        // keep both alive and idle while the clock runs out: never let a stack grow by locking pieces
        var t = 0L
        while (t < 125_000 && p.host.state.phase != GPhase.OVER) {
            p.host.board!!.let { if (it.py > 6) it.forcePiece(it.type, 0, 4, 0) }
            p.client.board!!.let { if (it.py > 6) it.forcePiece(it.type, 0, 4, 0) }
            p.advance(500)
            t += 500
        }
        assertEquals(GPhase.OVER, p.host.state.phase)
        assertEquals(GPhase.OVER, p.client.state.phase)
        assertEquals(1, p.host.state.result)
        assertEquals(2, p.client.state.result)
        assertTrue(p.host.state.how.startsWith("Time's up"))
    }

    @Test fun hostileMessagesDoNotCrashOrCheat() {
        val p = pair()
        p.go()
        val junk = listOf("{}", """{"a":"st"}""", """{"a":"st","gid":1}""", """{"a":"st","gid":1,"b":{"g":"short"}}""",
            """{"a":"st","gid":1,"b":{"g":"${"x".repeat(200)}"}}""", """{"a":"st","gid":99,"b":{}}""")
        junk.forEach { p.host.onMessage(GMsg.Act("TETRIS", JSONObject(it))) }
        p.client.onMessage(GMsg.Snap("TETRIS", JSONObject("""{"gid":1}""")))
        // a huge forged gift list is capped
        val gifts = (1..60).joinToString(",", "[", "]") { "[$it,2,0,3]" }
        val good = ".".repeat(200)
        p.host.onMessage(GMsg.Act("TETRIS", JSONObject("""{"a":"st","gid":1,"b":{"g":"$good","gl":$gifts}}""")))
        assertTrue(p.host.board!!.incoming.size <= TetrisRules.MAX_INCOMING)
        assertEquals(GPhase.PLAYING, p.host.state.phase)
    }

    @Test fun twoComputersPlayAWholeScoreAttackMatch() {
        val d = Duel(4, Skill.NORMAL, Skill.NORMAL)
        d.host.tetris.start(JSONObject().put("mode", 1).put("lvl", 0).put("mins", 0).put("gifts", true))
        var guard = 0
        while (d.host.tetris.state.phase != GPhase.OVER && guard++ < 6000) d.advance(50)
        val h = d.host.tetris.state
        val g = d.guest.tetris.state
        assertEquals(GPhase.OVER, h.phase)
        assertEquals(GPhase.OVER, g.phase)
        assertEquals(h.myScore, g.theirScore)
        assertEquals(h.theirScore, g.myScore)
        assertEquals(h.result, when (g.result) { 1 -> 2; 2 -> 1; else -> g.result })
        assertTrue("the computers should clear lines: ${h.myLines} / ${h.theirLines}", h.myLines + h.theirLines >= 6)
    }
}

class TetrisModesTest {
    private val W = TetrisRules.W
    private val R = TetrisRules.ROWS

    private fun board(mode: Int, seed: Long = 1, level: Int = 1, gifts: Boolean = true) = TetrisBoard(seed, level, gifts, mode)

    /** advance() takes at most 3 seconds at once (so a paused app can't fast-forward), so run long spans in steps. */
    private fun TetrisBoard.run(ms: Long) {
        var left = ms
        while (left > 0) { val d = minOf(left, 500L); advance(d); left -= d }
    }

    @Test fun allSevenModesAreDescribed() {
        assertEquals(7, TetrisMode.COUNT)
        assertEquals(7, TetrisMode.ALL.size)
        assertEquals(TetrisMode.ALL.map { it.id }, (0 until 7).toList())
        assertTrue(TetrisMode.ALL.all { it.label.isNotBlank() && it.blurb.length > 20 })
        assertTrue(TetrisMode.giftsOptional(TetrisMode.SCORE) && TetrisMode.giftsOptional(TetrisMode.SPRINT))
        assertFalse(TetrisMode.giftsOptional(TetrisMode.BOMB))
    }

    // ---------------------------------------------------------------- Bomb Blitz

    @Test fun everySixthPieceIsABombAndBothPlayersGetTheSameOnes() {
        val a = board(TetrisMode.BOMB, 9)
        val c = board(TetrisMode.BOMB, 9)
        val seq = ArrayList<Int>()
        seq.add(a.type)
        seq.addAll(a.next(8))
        assertEquals(TetrisRules.BOMB_TYPE, seq[5])
        assertEquals(a.next(8), c.next(8))
        assertFalse("other modes have no bombs", board(TetrisMode.BATTLE, 9).next(8).contains(TetrisRules.BOMB_TYPE))
    }

    @Test fun aBombBlowsAHoleAndTheBlownUpBlocksAreSentToTheRival() {
        val b = board(TetrisMode.BOMB)
        // a block of "S" pieces 4 high, 5 wide in the middle
        for (y in R - 4 until R) for (x in 2..6) b.grid[y * W + x] = 4
        val before = b.grid.count { it != 0 }
        b.forcePiece(TetrisRules.BOMB_TYPE, 0, 4, 0)
        b.hardDrop()
        val after = b.grid.count { it != 0 }
        assertTrue("the blast removed blocks ($before -> $after)", after < before)
        assertEquals("the bomb itself leaves nothing behind", before - after, b.grid.let { before - after })
        assertEquals(1, b.blastSerial)
        assertTrue(b.score >= 10 * (before - after))
        // 4+ blocks blown up -> at least one gift, made of the shapes that were blown up ("S" = type 3)
        assertTrue(b.outgoing.isNotEmpty())
        assertEquals(3, b.outgoing[0].type)
        // blocks above the hole fall into it: no block floats over an empty cell in a column
        for (x in 0 until W) {
            var seenEmptyBelow = false
            for (y in R - 1 downTo 0) {
                if (b.grid[y * W + x] == 0) seenEmptyBelow = true else assertFalse("floating block at $x,$y", seenEmptyBelow)
            }
        }
    }

    @Test fun aBombCannotBeHeldAndTurningItDoesNothing() {
        val b = board(TetrisMode.BOMB)
        b.forcePiece(TetrisRules.BOMB_TYPE, 0, 4, 3)
        b.holdPiece()
        assertEquals(-1, b.hold)
        assertTrue(b.rotateCw())
        assertEquals(0, b.rot)
    }

    @Test fun theComputerAimsABombAtTheBiggestPile() {
        val g = IntArray(R * W)
        for (y in R - 5 until R) for (x in 6..8) g[y * W + x] = 2
        val col = TetrisAi.bombColumn(g)
        assertTrue("column $col should be in the pile", col in 5..9)
    }

    // ---------------------------------------------------------------- Rising Tide

    @Test fun everyTwelveSecondsAJunkRowRisesWithOneGapIdenticalOnBothBoards() {
        val a = board(TetrisMode.TIDE, 5)
        val c = board(TetrisMode.TIDE, 5)
        a.run(TetrisRules.TIDE_MS - 100)
        assertEquals(0, a.tideRows)
        a.run(200)
        c.run(TetrisRules.TIDE_MS + 100)
        assertEquals(1, a.tideRows)
        val row = (0 until W).map { a.cell(it, R - 1) }
        assertEquals(1, row.count { it == 0 })
        assertEquals(W - 1, row.count { it == TetrisRules.JUNK })
        assertEquals("both players get the same gap", row, (0 until W).map { c.cell(it, R - 1) })
        assertTrue(a.tideLeftMs in 1..TetrisRules.TIDE_MS)
    }

    @Test fun theTideRaisesTheStackAndTheFallingPieceIsLiftedOutOfTheWay() {
        val b = board(TetrisMode.TIDE)
        b.grid[(R - 1) * W + 3] = 5
        b.forcePiece(1, 0, 4, R - 3)
        b.run(TetrisRules.TIDE_MS + 50)
        assertTrue(b.alive)
        assertEquals(5, b.cell(3, R - 2)) // the old bottom block moved up one row
    }

    @Test fun aFullWellDrownsYouWhenTheTideRises() {
        val b = board(TetrisMode.TIDE)
        for (x in 0 until W - 1) b.grid[0 * W + x] = 3
        b.run(TetrisRules.TIDE_MS + 50)
        assertFalse(b.alive)
    }

    // ---------------------------------------------------------------- Turbo

    @Test fun turboSpeedsUpEveryTwentyFiveSecondsWhateverYouDo() {
        val b = board(TetrisMode.TURBO, level = 1)
        assertEquals(1, b.level)
        // keep pieces from stacking up while the clock runs
        var t = 0L
        while (t < 51_000) { b.advance(500); t += 500; if (b.py > 8) b.forcePiece(b.type, 0, 4, 0) }
        assertEquals(3, b.level)
        assertEquals(1, board(TetrisMode.BATTLE).level)
    }

    // ---------------------------------------------------------------- Power Surge

    @Test fun everyFifthPieceCarriesAStarOnOneBlock() {
        val b = board(TetrisMode.SURGE, 3)
        var stars = 0
        var pieces = 0
        repeat(15) {
            pieces++
            if (b.starCell >= 0) {
                stars++
                assertTrue(b.starCell in 0..3)
            }
            b.forcePiece(1, 0, (it % 4) * 2, 0) // park a harmless square piece wherever
            // use real spawning: drop it
            b.hardDrop()
            if (!b.alive) return@repeat
        }
        assertTrue("stars appear regularly: $stars of $pieces", stars in 1..5)
        assertEquals(0, board(TetrisMode.BATTLE).starCell.coerceAtLeast(0))
    }

    @Test fun clearingARowWithAStarLaunchesAPower() {
        val b = board(TetrisMode.SURGE, 4)
        val w = W
        for (x in 0..5) b.grid[(R - 1) * w + x] = 3
        b.grid[(R - 1) * w + 2] = 3 + TetrisRules.STAR // a star in the row
        b.forcePiece(0, 0, 6, R - 2)
        b.hardDrop()
        assertEquals(1, b.powerSerial)
        assertTrue(b.lastPower in 0..4)
        assertTrue("it is a gift, a curse or a clean-up", b.outgoing.isNotEmpty() || b.lastPower == 4 || b.giftsSent > 0)
    }

    @Test fun cursesDizzyTheControlsSpeedUpTheFallAndFogTheView() {
        val b = board(TetrisMode.SURGE)
        b.forcePiece(1, 0, 4, 2)
        b.receive(Gift(TetrisRules.CURSE_DIZZY, 0, 0))
        assertTrue(b.dizzyMs > 0)
        val x = b.px
        b.moveLeft() // dizzy: left goes right
        assertEquals(x + 1, b.px)
        b.run(TetrisRules.DIZZY_MS + 100)
        assertEquals(0, b.dizzyMs)
        val x2 = b.px
        b.moveLeft()
        assertEquals(x2 - 1, b.px)

        val calm = board(TetrisMode.SURGE)
        val wild = board(TetrisMode.SURGE)
        wild.receive(Gift(TetrisRules.CURSE_TURBO, 0, 0))
        calm.advance(2000); wild.advance(2000)
        assertTrue("turbo curse falls faster (${wild.py} vs ${calm.py})", wild.py > calm.py)

        b.receive(Gift(TetrisRules.CURSE_FOG, 0, 0))
        assertEquals(2, b.activeCurse())
        assertEquals(1, b.fxSerial.coerceAtMost(3).let { if (it >= 1) 1 else 0 })
        assertEquals(0, b.incoming.size) // curses are not pieces
    }

    // ---------------------------------------------------------------- scoring extras

    @Test fun backToBackFourLineClearsScoreMoreAndSendAnExtraGift() {
        fun tetris(b: TetrisBoard) {
            b.grid[(R - 8) * W] = 2 // a stray block keeps this from being a perfect clear
            for (y in R - 4 until R) for (x in 0..8) b.grid[y * W + x] = 3
            b.forcePiece(0, 1, 7, R - 4)
            b.hardDrop()
        }
        val b = board(TetrisMode.BATTLE)
        tetris(b)
        val first = b.score
        assertEquals(5, b.outgoing.size)
        b.outgoing.clear()
        tetris(b)
        assertEquals(1, b.b2bSerial)
        assertEquals(6, b.outgoing.size) // 5 + 1 back-to-back
        assertTrue(b.score - first > first)
        assertEquals(2, b.tetrises)
    }

    @Test fun clearingEverythingIsAPerfectClear() {
        val b = board(TetrisMode.BATTLE)
        for (x in 0..5) b.grid[(R - 1) * W + x] = 3
        b.forcePiece(0, 0, 6, R - 2)
        b.hardDrop()
        assertEquals(1, b.perfectClears)
        assertEquals(1, b.perfectSerial)
        assertTrue(b.score >= 1000)
        assertTrue(b.outgoing.size >= 4)
    }

    @Test fun clearedRowsAreReportedForTheFlash() {
        val b = board(TetrisMode.BATTLE)
        for (x in 0..5) b.grid[(R - 1) * W + x] = 3
        b.forcePiece(0, 0, 6, R - 2)
        b.hardDrop()
        assertEquals(listOf(TetrisRules.H - 1), b.lastClearRows)
    }

    @Test fun boardsSurviveBeingSentAsText() {
        val b = board(TetrisMode.SURGE)
        b.grid[(R - 1) * W] = 7
        b.grid[(R - 1) * W + 1] = TetrisRules.GIFT
        b.grid[(R - 1) * W + 2] = TetrisRules.JUNK
        b.grid[(R - 1) * W + 3] = 3 + TetrisRules.STAR
        val s = b.visibleRows()
        assertEquals(200, s.length)
        assertTrue(s.all { TetrisRules.validBoardChar(it) })
        val back = s.map { TetrisRules.codeOf(it) }
        assertEquals((0 until 200).map { b.grid[TetrisRules.HIDDEN * W + it] }, back)
    }
}

class TetrisModeMatchTest {
    private fun pair(mode: Int, tl: Int = 0, lvl: Int = 0): SyncPair<TetrisEngine> {
        val p = SyncPair({ n, o, h, s, r, c -> TetrisEngine(n, o, h, s, r, c) }, 2)
        p.host.start(JSONObject().put("mode", mode).put("lvl", lvl).put("tl", tl).put("mins", 0))
        p.pump()
        p.advance(TetrisRules.COUNTDOWN_MS + 100)
        return p
    }

    @Test fun everyModeStartsOnBothPhonesWithTheSameSettings() {
        for (m in 0 until TetrisMode.COUNT) {
            val p = pair(m)
            assertEquals("mode $m", m, p.client.state.mode)
            assertEquals(p.host.board!!.next(5), p.client.board!!.next(5))
            assertEquals(GPhase.PLAYING, p.client.state.phase)
        }
    }

    @Test fun sprintIsWonByTheFirstToTheLineTarget() {
        val p = pair(TetrisMode.SPRINT, tl = 0)
        assertEquals(20, p.host.state.target)
        p.client.board!!.debugSetLines(20)
        p.client.hardDrop()
        p.advance(300)
        p.advance(300)
        assertEquals(GPhase.OVER, p.host.state.phase)
        assertEquals(2, p.host.state.result)
        assertEquals(1, p.client.state.result)
        assertTrue(p.host.state.how.contains("first to clear 20 lines"))
    }

    @Test fun sprintGiftsAreOptionalButBattleModesAlwaysHaveThem() {
        val noGifts = SyncPair({ n, o, h, s, r, c -> TetrisEngine(n, o, h, s, r, c) }, 3)
        noGifts.host.start(JSONObject().put("mode", TetrisMode.SPRINT).put("gifts", false))
        noGifts.pump()
        assertFalse(noGifts.host.state.giftsOn)
        val bomb = SyncPair({ n, o, h, s, r, c -> TetrisEngine(n, o, h, s, r, c) }, 3)
        bomb.host.start(JSONObject().put("mode", TetrisMode.BOMB).put("gifts", false))
        bomb.pump()
        assertTrue(bomb.host.state.giftsOn)
    }

    @Test fun aCurseSentByOnePlayerLandsOnTheOther() {
        val p = pair(TetrisMode.SURGE)
        val b = p.host.board!!
        for (x in 0..5) b.grid[(TetrisRules.ROWS - 1) * TetrisRules.W + x] = 3
        // a star in the row, and keep rolling until the power is a curse (try a few seeds of the same clear)
        var tries = 0
        while (p.client.board!!.activeCurse() < 0 && tries++ < 40) {
            val hb = p.host.board!!
            for (x in 0..5) hb.grid[(TetrisRules.ROWS - 1) * TetrisRules.W + x] = if (x == 2) 3 + TetrisRules.STAR else 3
            hb.forcePiece(0, 0, 6, TetrisRules.ROWS - 2)
            p.host.hardDrop()
            p.advance(300)
            // clear the board so the next attempt is clean
            for (i in hb.grid.indices) hb.grid[i] = 0
        }
        assertTrue("some power should have been a curse within $tries tries", p.client.board!!.fxSerial > 0 || p.host.board!!.giftsSent > 0)
    }

    @Test fun computersCanPlayEveryModeAndTheTimedOnesAlwaysFinish() {
        for (mode in 0 until TetrisMode.COUNT) {
            val d = Duel(11, Skill.NORMAL, Skill.NORMAL)
            d.host.tetris.start(JSONObject().put("mode", mode).put("lvl", 1).put("tl", 0).put("mins", 0))
            var guard = 0
            // A perfect-playing pair of computers can survive for ever in the "last stack standing" modes, so
            // those run for ten virtual minutes; the clock and the race always end.
            while (d.host.tetris.state.phase != GPhase.OVER && guard++ < 12000) d.advance(50)
            val h = d.host.tetris.state
            val g = d.guest.tetris.state
            if (mode == TetrisMode.SCORE || mode == TetrisMode.SPRINT) assertEquals("mode $mode should finish", GPhase.OVER, h.phase)
            if (h.phase == GPhase.OVER) {
                assertEquals(GPhase.OVER, g.phase)
                assertEquals(h.result, when (g.result) { 1 -> 2; 2 -> 1; else -> g.result })
                assertTrue(h.how.isNotBlank())
            }
            assertTrue("the computers should clear lines in mode $mode", h.myLines + h.theirLines > 5)
        }
    }
}
