package com.neil1454.drawsomething.core

import org.json.JSONArray
import org.json.JSONObject
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertTrue
import org.junit.Test

class StakesTest {
    @Test fun stakesAreCleanedAndCapped() {
        val list = StakeWire.cleanList(List(9) { Stake("  a\u0007b   c $it ", it % 2 == 0) } + Stake("   ", true))
        assertEquals(StakeWire.MAX, list.size)
        assertEquals("ab c 0", list[0].text)
        assertTrue(StakeWire.clean("x".repeat(500)).length <= StakeWire.MAX_LEN)
    }

    @Test fun hiddenStakesNeverLeaveThePhoneUntilRevealed() {
        val mine = listOf(Stake("a coffee", true), Stake("do the washing up", false))
        val hidden = Packet.decode(StakeWire.encode(mine, false)) as Packet.Stakes
        assertEquals(2, hidden.list.size)
        assertEquals("a coffee", hidden.list[0].text)
        assertEquals("", hidden.list[1].text)
        assertFalse(hidden.list[1].known)
        val revealed = Packet.decode(StakeWire.encode(mine, true)) as Packet.Stakes
        assertTrue(revealed.reveal)
        assertEquals("do the washing up", revealed.list[1].text)
        assertTrue(revealed.list[1].known)
        assertFalse(String(StakeWire.encode(mine, false)).contains("washing"))
    }

    @Test fun moneyAmountsAreAddedUp() {
        assertEquals(17.5, StakeWire.money(listOf("£5", "a lift to work", "$12.50")), 0.001)
        assertEquals(0.0, StakeWire.money(listOf("a coffee")), 0.001)
        assertEquals("£5", StakeWire.moneyText(5.0))
        assertEquals("£5.50", StakeWire.moneyText(5.5))
    }
}

class ShowdownTest {
    private val launched = ArrayList<GameKind>()

    private fun pair(seed: Int = 1, mine: List<Pair<String, Boolean>>, theirs: List<Pair<String, Boolean>>): SyncPair<ShowdownEngine> {
        val p = SyncPair({ n, o, h, s, r, c -> ShowdownEngine(n, o, h, s, r, c) }, seed)
        p.host.launch = { launched.add(it) }
        p.host.localStakes = mine.map { Stake(it.first, it.second) }
        p.client.localStakes = theirs.map { Stake(it.first, it.second) }
        fun arr(l: List<Pair<String, Boolean>>, hideText: Boolean) = JSONArray().also { a -> l.forEach { a.put(JSONArray().put(if (hideText && !it.second) "" else it.first).put(if (it.second) 1 else 0)) } }
        p.host.start(JSONObject().put("mine", arr(mine, false)).put("theirs", arr(theirs, true)))
        p.pump()
        return p
    }

    private fun tickAll(p: SyncPair<ShowdownEngine>) { repeat(3) { p.advance(100) } }

    @Test fun everyStakeBecomesARoundAndTheChallengerPicks() {
        launched.clear()
        val p = pair(mine = listOf("£5" to true, "a back rub" to true), theirs = listOf("a coffee" to true))
        for (e in listOf(p.host, p.client)) {
            assertEquals(GPhase.PLAYING, e.state.phase)
            assertEquals(3, e.state.total)
            assertEquals("PICK", e.state.stage)
            assertEquals(1, e.state.pot.size)
            assertEquals(ShowdownRules.OPTIONS_PER_ROUND, e.state.options.size)
        }
        // the player who did not put the stake up is the one who chooses
        val owner = p.host.state.pot[0].mine
        assertEquals(!owner, p.host.state.chooserMe)
        assertEquals(owner, p.client.state.chooserMe)
    }

    @Test fun pickingStartsAContestAndTheWinnerClaimsTheStake() {
        launched.clear()
        val p = pair(seed = 2, mine = listOf("£5" to true), theirs = listOf("a coffee" to true))
        val chooser = if (p.host.state.chooserMe) p.host else p.client
        val kind = chooser.state.options[0]
        chooser.pick(kind); p.pump()
        assertEquals("PLAY", p.host.state.stage)
        assertEquals(listOf(kind), launched)
        // a pick from the wrong player or for a game that was not offered is ignored
        val other = if (chooser === p.host) p.client else p.host
        other.pick(GameKind.DROP); p.pump()
        assertEquals(1, launched.size)

        p.host.contestFinished(kind, 1) // the host wins
        p.pump()
        assertEquals("CLAIM", p.host.state.stage)
        assertEquals(1, p.host.state.lastWinner)
        assertEquals(2, p.client.state.lastWinner)
        assertEquals(1, p.host.state.myClaims.size)
        assertEquals(1, p.client.state.theirClaims.size)
    }

    @Test fun aTieRollsTheStakeIntoTheNextRound() {
        launched.clear()
        val p = pair(seed = 3, mine = listOf("A" to true), theirs = listOf("B" to true))
        val c1 = if (p.host.state.chooserMe) p.host else p.client
        val k1 = c1.state.options[0]
        c1.pick(k1); p.pump()
        p.host.contestFinished(k1, 3); p.pump()
        assertEquals(3, p.host.state.lastWinner)
        p.host.ack(); p.client.ack(); p.pump()
        assertEquals("PICK", p.host.state.stage)
        assertEquals("both stakes are up for grabs now", 2, p.host.state.pot.size)
        assertTrue("the next contest is a different game", k1 !in p.host.state.options)
    }

    @Test fun aFullShowdownEndsWithTheMostStakesWinning() {
        launched.clear()
        val p = pair(seed = 4, mine = listOf("£5" to true, "£3" to true), theirs = listOf("a coffee" to true))
        var guard = 0
        while (p.host.state.phase == GPhase.PLAYING && guard++ < 20) {
            val c = if (p.host.state.chooserMe) p.host else p.client
            val k = c.state.options.first()
            c.pick(k); p.pump()
            p.host.contestFinished(k, 1) // the host wins everything
            p.pump()
            p.host.ack(); p.client.ack(); p.pump()
        }
        assertEquals(GPhase.OVER, p.host.state.phase)
        assertEquals(GPhase.OVER, p.client.state.phase)
        assertEquals(1, p.host.state.result)
        assertEquals(2, p.client.state.result)
        assertEquals(3, p.host.state.myClaims.size)
        assertEquals(8.0, p.host.state.myMoney, 0.001)
    }

    @Test fun mysteryStakesStayFaceDownUntilTheirRoundThenFlip() {
        launched.clear()
        // both have one mystery; whoever's comes first flips at once, the other's stays hidden
        val p = pair(seed = 5, mine = listOf("host secret" to false), theirs = listOf("guest secret" to false))
        val first = p.host.state.pot[0]
        val rest = p.host.state.items.filter { it.index != first.index }[0]
        assertTrue("the stake not in play yet is still a mystery on the host screen", rest.mystery)
        assertTrue(rest.text.isEmpty())
        assertTrue("the guest never sees the host's mystery text", p.client.state.items.none { it.mystery && it.text.isNotEmpty() })
        // the guest's own mystery flips when its round comes up (the guest phone reports its text)
        tickAll(p)
        if (!first.mine && p.host.state.pot[0].mine.not()) {
            // the first stake is the guest's: after a tick it is no longer a mystery for the host
            assertTrue(!p.host.state.pot[0].mystery)
            assertEquals("guest secret", p.host.state.pot[0].text)
        } else {
            assertTrue(!p.client.state.pot[0].mystery)
            assertEquals("host secret", p.client.state.pot[0].text)
        }
    }

    @Test fun abortingEndsItForBothAndBadMessagesAreIgnored() {
        launched.clear()
        val p = pair(mine = listOf("A" to true), theirs = listOf("B" to true))
        p.host.onMessage(GMsg.Act("SHOWDOWN", JSONObject().put("a", "rv").put("i", 99).put("t", "x")))
        p.host.onMessage(GMsg.Act("SHOWDOWN", JSONObject().put("a", "pick").put("k", "NONSENSE")))
        assertEquals("PICK", p.host.state.stage)
        p.host.abort(); p.pump()
        assertEquals(GPhase.OVER, p.client.state.phase)
        assertEquals("quit", p.client.state.why)
        assertNotNull(p.client.state.items)
    }
}
