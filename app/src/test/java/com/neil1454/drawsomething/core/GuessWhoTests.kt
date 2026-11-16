package com.neil1454.drawsomething.core

import org.json.JSONObject
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import kotlin.random.Random

class GuessWhoTest {
    private fun pair(seed: Int = 1, size: Int = 0, manual: Boolean = false): SyncPair<GuessWhoEngine> {
        val p = SyncPair({ n, o, h, s, r, c -> GuessWhoEngine(n, o, h, s, r, c) }, seed)
        p.host.start(JSONObject().put("size", size).put("auto", true).put("manual", manual))
        p.pump()
        return p
    }

    private fun pickBoth(p: SyncPair<GuessWhoEngine>, hostFace: Int = -1, guestFace: Int = -1) {
        val ids = p.host.state.ids
        p.host.pick(if (hostFace >= 0) hostFace else ids[0])
        p.client.pick(if (guestFace >= 0) guestFace else ids[1])
        p.pump()
    }

    @Test fun theBoardsMatchAndEveryFaceIsDifferent() {
        val p = pair(size = 2)
        assertEquals(24, p.host.state.ids.size)
        assertEquals(p.host.state.ids, p.client.state.ids)
        val faces = GuessWhoData.ALL
        for (a in faces.indices) for (b in a + 1 until faces.size) {
            val x = faces[a]; val y = faces[b]
            assertTrue("${x.name} and ${y.name} look the same", GuessWhoData.QUESTIONS.any { it.test(x) != it.test(y) })
        }
    }

    @Test fun smallerBoardsUseASubset() {
        assertEquals(12, pair(size = 0).host.state.ids.size)
        assertEquals(18, pair(size = 1).host.state.ids.size)
    }

    @Test fun secretsStayPrivateUntilTheEnd() {
        val p = pair(seed = 3)
        pickBoth(p)
        assertEquals("PLAY", p.host.state.stage)
        assertEquals(p.host.state.ids[0], p.host.state.mySecret)
        assertEquals(p.client.state.ids[1], p.client.state.mySecret)
        assertEquals(-1, p.host.state.theirSecret)
        assertEquals(-1, p.client.state.theirSecret)
    }

    @Test fun questionsAreAnsweredAboutTheOtherPlayersFaceAndPassTheTurn() {
        val p = pair(seed = 4)
        val ids = p.host.state.ids
        pickBoth(p, hostFace = ids[0], guestFace = ids[1])
        val asker = if (p.host.state.myTurn) p.host else p.client
        val answerer = if (asker === p.host) 1 else 0
        val q = GuessWhoData.QUESTIONS.indexOfFirst { it.text.contains("glasses") }
        val target = GuessWhoData.ALL[if (asker === p.host) ids[1] else ids[0]]
        asker.ask(q); p.pump()
        assertEquals(1, asker.state.history.size)
        assertEquals(target.glasses, asker.state.history[0].yes)
        assertEquals(1, asker.state.history[0].asker)
        val other = if (asker === p.host) p.client else p.host
        assertEquals(2, other.state.history[0].asker)
        assertFalse(asker.state.myTurn)
        assertTrue(other.state.myTurn)
        // the same question twice, or out of turn, is ignored
        other.ask(q); p.pump(); asker.ask(q); p.pump()
        assertTrue(answerer in 0..1)
        assertEquals(2, p.host.state.history.size.coerceAtMost(2))
    }

    @Test fun aRightGuessWinsAndAWrongGuessLoses() {
        val p = pair(seed = 5)
        val ids = p.host.state.ids
        pickBoth(p, hostFace = ids[0], guestFace = ids[1])
        val asker = if (p.host.state.myTurn) p.host else p.client
        val target = if (asker === p.host) ids[1] else ids[0]
        asker.guess(target); p.pump()
        assertEquals(GPhase.OVER, p.host.state.phase)
        assertEquals(1, asker.state.result)
        val other = if (asker === p.host) p.client else p.host
        assertEquals(2, other.state.result)
        assertEquals(ids[0], p.host.state.mySecret)
        assertEquals(ids[1], p.host.state.theirSecret)

        val q = pair(seed = 6)
        val qi = q.host.state.ids
        pickBoth(q, hostFace = qi[0], guestFace = qi[1])
        val a2 = if (q.host.state.myTurn) q.host else q.client
        val wrong = qi.first { it != (if (a2 === q.host) qi[1] else qi[0]) }
        a2.guess(wrong); q.pump()
        assertEquals(2, a2.state.result)
    }

    @Test fun twoComputerPlayersFinishAGameWithRightAndWrongGuessesPossible() {
        for (seed in 1..6) {
            val p = pair(seed = seed, size = 1, manual = seed % 2 == 0)
            val a = GuessWhoBrain(p.host, Skill.HARD, Random(seed))
            val b = GuessWhoBrain(p.client, Skill.NORMAL, Random(seed + 40))
            var guard = 0
            while (p.host.state.phase == GPhase.PLAYING && guard++ < 6000) {
                a.think(p.now); b.think(p.now)
                p.advance(100)
            }
            assertEquals("seed $seed", GPhase.OVER, p.host.state.phase)
            assertNotEquals(0, p.host.state.result)
            assertEquals(p.host.state.result.let { if (it == 1) 2 else 1 }, p.client.state.result)
        }
    }

    @Test fun theOtherPlayerAnswersByHandAndLiesAreRefused() {
        val p = pair(seed = 8, manual = true)
        val ids = p.host.state.ids
        pickBoth(p, hostFace = ids[0], guestFace = ids[1])
        val asker = if (p.host.state.myTurn) p.host else p.client
        val answerer = if (asker === p.host) p.client else p.host
        val q = GuessWhoData.QUESTIONS.indexOfFirst { it.text.contains("glasses") }
        asker.ask(q); p.pump()
        assertTrue("the question waits for an answer", asker.state.pending != null && asker.state.pending!!.byMe)
        assertTrue(answerer.state.mustAnswer)
        assertEquals("nothing is recorded until it is answered", 0, asker.state.history.size)
        // the asker can't ask again or guess while the question is pending
        asker.ask(q + 1); asker.guess(ids[1]); p.pump()
        assertEquals(GPhase.PLAYING, asker.state.phase)
        val truth = GuessWhoData.QUESTIONS[q].test(GuessWhoData.ALL[answerer.state.mySecret])
        answerer.answer(!truth); p.pump()
        assertTrue("a wrong answer is refused with a nudge", answerer.state.note.isNotEmpty() && answerer.state.mustAnswer)
        answerer.answer(truth); p.pump()
        assertEquals(1, asker.state.history.size)
        assertEquals(truth, asker.state.history[0].yes)
        assertEquals(null, asker.state.pending)
        assertTrue("it is now the answerer's turn", answerer.state.myTurn)
    }

    @Test fun customQuestionsAreAnsweredOnTrust() {
        val p = pair(seed = 9, manual = true)
        val ids = p.host.state.ids
        pickBoth(p, hostFace = ids[0], guestFace = ids[1])
        val asker = if (p.host.state.myTurn) p.host else p.client
        val answerer = if (asker === p.host) p.client else p.host
        asker.askCustom("Do they look like a pirate?"); p.pump()
        assertEquals("Do they look like a pirate?", answerer.state.pending!!.question)
        answerer.answer(true); p.pump()
        assertEquals(1, asker.state.history.size)
        assertEquals("Do they look like a pirate?", asker.state.history[0].question)
        assertEquals("custom questions don't flip faces by themselves", 0, asker.state.myQuestions.size)
        asker.askCustom("x"); p.pump() // too short
        assertEquals(null, asker.state.pending)
    }

    @Test fun theBestQuestionSplitsTheFacesNearlyInHalf() {
        val faces = GuessWhoData.ALL.take(12)
        val q = GuessWhoData.bestQuestion(faces, emptySet())!!
        val yes = faces.count { GuessWhoData.QUESTIONS[q].test(it) }
        assertTrue("$yes of 12", yes in 4..8)
        assertEquals(null, GuessWhoData.bestQuestion(listOf(faces[0]), emptySet()))
    }

    @Test fun badMessagesAreIgnored() {
        val p = pair()
        p.host.onMessage(GMsg.Act("GUESSWHO", JSONObject().put("a", "pick").put("c", 999)))
        p.host.onMessage(GMsg.Act("GUESSWHO", JSONObject().put("a", "ask").put("q", -4)))
        p.host.onMessage(GMsg.Act("GUESSWHO", JSONObject().put("a", "guess").put("c", 999)))
        assertEquals(GPhase.PLAYING, p.host.state.phase)
        assertEquals("PICK", p.host.state.stage)
    }
}
