package com.neil1454.drawsomething.core

import org.json.JSONObject
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class SaveFormatTest {
    @Test fun onlyTheLongGamesCanBeSaved() {
        assertEquals(setOf(GameKind.MONOPOLY, GameKind.LUDO, GameKind.DRAUGHTS), GameKind.values().filter { it.canSave }.toSet())
    }

    @Test fun aSaveSurvivesEncodingAndDecoding() {
        val g = SavedGame(GameKind.MONOPOLY, 1234L, "Neil", "Teri", "Turn 3", JSONObject().put("x", 1))
        val back = SaveFormat.decode(SaveFormat.encode(g))!!
        assertEquals(GameKind.MONOPOLY, back.kind)
        assertEquals("Neil", back.host)
        assertEquals("Teri", back.guest)
        assertEquals(1234L, back.savedAt)
        assertEquals(1, back.model.getInt("x"))
    }

    @Test fun damagedOrUnknownSavesAreRejected() {
        assertNull(SaveFormat.decode("not json"))
        assertNull(SaveFormat.decode("""{"v":99,"kind":"MONOPOLY","at":1,"host":"a","guest":"b","summary":"","model":{}}"""))
        assertNull(SaveFormat.decode("""{"v":1,"kind":"TETRIS","at":1,"host":"a","guest":"b","summary":"","model":{}}"""))
        assertNull(SaveFormat.decode("""{"v":1,"kind":"MONOPOLY","at":1,"host":"a","guest":"b","summary":""}"""))
        assertNull(SaveFormat.decode("x".repeat(SaveFormat.MAX_CHARS + 1)))
    }

    @Test fun agoIsPlainEnglish() {
        assertEquals("just now", SaveFormat.ago(1000, 1000))
        assertEquals("5 minutes ago", SaveFormat.ago(0, 5 * 60_000L))
        assertEquals("1 hour ago", SaveFormat.ago(0, 60 * 60_000L))
        assertEquals("3 days ago", SaveFormat.ago(0, 3 * 24 * 60 * 60_000L))
    }
}

class ResumeTest {
    // ---- Monopoly
    private fun monoPair(): SyncPair<MonopolyEngine> {
        val p = SyncPair({ n, o, h, s, r, c -> MonopolyEngine(n, o, h, s, r, c) }, 4)
        p.host.start(JSONObject().put("len", 0)); p.pump()
        return p
    }

    @Test fun aMonopolyGameResumesExactlyWhereItWasLeft() {
        val p = monoPair()
        p.host.forcedDice.add(1 to 2); p.host.roll(); p.pump()
        p.host.buy(); p.pump()
        p.host.mortgage(3); p.pump()
        p.host.endTurn(); p.pump()
        val saved = p.host.saveModel()!!
        val summary = p.host.saveSummary()
        assertTrue(summary.contains("Turn"))
        // a brand-new pair of phones (the app was closed and reopened)
        val q = monoPair2()
        assertTrue(q.host.resume(JSONObject(saved.toString()))); q.pump()
        val a = p.host.state
        val b = q.host.state
        assertEquals(a.myCash, b.myCash)
        assertEquals(a.myPos, b.myPos)
        assertEquals(a.owner, b.owner)
        assertEquals(a.mortgaged, b.mortgaged)
        assertEquals(a.step, b.step)
        assertEquals(GPhase.PLAYING, q.client.state.phase)
        assertEquals("the partner sees the restored position too", a.theirCash, q.client.state.myCash)
        assertEquals(2, q.client.state.owner[3])
        assertTrue("it is the guest's go, as when saved", q.client.state.canRoll)
        // and play carries on
        q.host.forcedDice.add(1 to 2)
        q.client.roll(); q.pump()
        assertEquals("square 3 was mortgaged when saved, so no rent is due", 1500, q.client.state.myCash)
        assertEquals("END", q.client.state.step)
    }

    private fun monoPair2() = monoPair()

    @Test fun midAuctionAndDebtSurviveASave() {
        val p = monoPair()
        p.host.forcedDice.add(1 to 2); p.host.roll(); p.pump()
        p.host.declineToAuction(); p.pump()
        p.client.bid(100); p.pump()
        assertEquals("AUCTION", p.host.state.step)
        val saved = p.host.saveModel()!!
        val q = monoPair()
        assertTrue(q.host.resume(JSONObject(saved.toString()))); q.pump()
        assertEquals("AUCTION", q.host.state.step)
        assertEquals(100, q.host.state.auctionBid)
        assertTrue(q.host.state.myBidTurn)
        q.host.passBid(); q.pump()
        assertTrue(q.client.state.owns(3))
    }

    @Test fun nonsenseIsNotRestored() {
        val q = monoPair()
        assertFalse(q.host.resume(JSONObject()))
        assertFalse(q.host.resume(JSONObject("""{"cash":[0,1,2]}""")))
        val good = monoPair().host.saveModel()!!
        val bad = JSONObject(good.toString()).put("turn", 7)
        assertFalse(q.host.resume(bad))
        val bad2 = JSONObject(good.toString()).put("own", SaveFormat.ints(IntArray(40) { 9 }))
        assertFalse(q.host.resume(bad2))
        assertFalse("a guest can never resume", monoPair().client.resume(good))
    }

    @Test fun aFinishedGameCannotBeSaved() {
        val d = Duel(2, Skill.NORMAL, Skill.NORMAL)
        d.host.monopoly.start(JSONObject().put("len", 2))
        var guard = 0
        while (d.host.monopoly.state.phase != GPhase.OVER && guard++ < 40000) d.advance(250)
        assertNull(d.host.monopoly.saveModel())
    }

    @Test fun theComputerFinishesAResumedMonopolyGame() {
        val d = Duel(5, Skill.NORMAL, Skill.NORMAL)
        d.host.monopoly.start(JSONObject().put("len", 2))
        d.advance(120_000)
        val saved = d.host.monopoly.saveModel()
        assertNotNull(saved)
        val d2 = Duel(9, Skill.NORMAL, Skill.NORMAL)
        assertTrue(d2.host.monopoly.resume(JSONObject(saved.toString())))
        var guard = 0
        while (d2.host.monopoly.state.phase != GPhase.OVER && guard++ < 40000) d2.advance(250)
        assertEquals(GPhase.OVER, d2.host.monopoly.state.phase)
        assertEquals(GPhase.OVER, d2.guest.monopoly.state.phase)
    }

    // ---- Ludo
    @Test fun aLudoGameResumes() {
        val d = Duel(3, Skill.NORMAL, Skill.NORMAL)
        d.host.ludo.start()
        d.advance(90_000)
        val saved = d.host.ludo.saveModel()!!
        val before = d.host.ludo.state
        val d2 = Duel(8, Skill.NORMAL, Skill.NORMAL)
        assertTrue(d2.host.ludo.resume(JSONObject(saved.toString())))
        d2.advance(0)
        assertEquals(before.mine, d2.host.ludo.state.mine)
        assertEquals(before.theirs, d2.host.ludo.state.theirs)
        var guard = 0
        while (d2.host.ludo.state.phase != GPhase.OVER && guard++ < 20000) d2.advance(250)
        assertEquals(GPhase.OVER, d2.host.ludo.state.phase)
        assertFalse(SyncPair({ n, o, h, s, r, c -> LudoEngine(n, o, h, s, r, c) }).host.resume(JSONObject("""{"p1":[1,2,3],"p2":[0,0,0,0],"turn":1}""")))
    }

    // ---- Draughts
    @Test fun aDraughtsGameResumes() {
        val d = Duel(3, Skill.NORMAL, Skill.NORMAL)
        d.host.draughts.start()
        d.advance(60_000)
        val saved = d.host.draughts.saveModel()!!
        val grid = d.host.draughts.state.grid
        val d2 = Duel(8, Skill.NORMAL, Skill.NORMAL)
        assertTrue(d2.host.draughts.resume(JSONObject(saved.toString())))
        d2.advance(0)
        assertEquals(grid, d2.host.draughts.state.grid)
        assertEquals(grid, d2.guest.draughts.state.grid)
        var guard = 0
        while (d2.host.draughts.state.phase != GPhase.OVER && guard++ < 8000) d2.advance(250)
        assertEquals(GPhase.OVER, d2.host.draughts.state.phase)
    }
}
