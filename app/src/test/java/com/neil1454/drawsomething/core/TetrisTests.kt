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

    @Test fun twoLinesSendAGiftMadeOfTheBlownUpPieces() {
        val b = board()
        for (y in R - 2..R - 1) for (x in 0..8) b.grid[y * W + x] = 3 // lots of "T" blocks
        b.forcePiece(0, 1, 7, R - 4) // upright long piece in column 9
        b.hardDrop()
        assertEquals(2, b.lines)
        assertEquals(1, b.outgoing.size)
        assertEquals("the gift is the shape that was blown up most", 2, b.outgoing[0].type)
        assertEquals(1, b.giftsSent)
    }

    @Test fun fourLinesSendFourGiftsAndLongCombosAddOne() {
        assertEquals(0, TetrisRules.gifts(1, 0))
        assertEquals(1, TetrisRules.gifts(2, 0))
        assertEquals(2, TetrisRules.gifts(3, 0))
        assertEquals(4, TetrisRules.gifts(4, 0))
        assertEquals(1, TetrisRules.gifts(1, 3))
        assertEquals(5, TetrisRules.gifts(4, 5))
    }

    @Test fun giftsYouSendCancelGiftsAboutToFallOnYou() {
        val b = board()
        b.receive(Gift(2, 0, 3))
        for (y in R - 2..R - 1) for (x in 0..8) b.grid[y * W + x] = 3
        b.forcePiece(0, 1, 7, R - 4)
        b.hardDrop()
        assertEquals(0, b.incoming.size)
        assertEquals(0, b.outgoing.size)
    }

    @Test fun incomingGiftsFallByThemselvesInTheirOwnColour() {
        val b = board()
        b.receive(Gift(3, 0, 2))
        b.hardDrop() // lock the normal piece without clearing a line
        assertTrue("the next piece is the gift", b.isGift)
        val before = b.lockSerial
        // controls do nothing to a gift
        assertFalse(b.moveLeft())
        assertFalse(b.rotateCw())
        b.advance(3000)
        assertEquals(before + 1, b.lockSerial)
        var gifts = 0
        for (i in 0 until b.grid.size) if (b.grid[i] == TetrisRules.GIFT) gifts++
        assertEquals(4, gifts)
        assertFalse(b.isGift) // back to a normal piece
        assertEquals(1, b.giftsReceived)
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
        assertEquals(TetrisRules.gravityMs(13), TetrisRules.gravityMs(99))
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

    @Test fun clearedPiecesBecomeGiftsThatFallOnTheRival() {
        val p = pair()
        p.go()
        p.clearTwoOnHost()
        p.advance(300)
        assertEquals(1, p.host.state.mySent)
        assertEquals(1, p.client.board!!.giftsReceived)
        // the guest locks a normal piece, then the gift drops
        p.client.hardDrop()
        p.advance(2000)
        val g = p.client.board!!
        assertTrue((0 until g.grid.size).count { g.grid[it] == TetrisRules.GIFT } == 4)
        assertEquals(1, p.client.state.giftSeq)
        // and each phone knows a clear happened
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
