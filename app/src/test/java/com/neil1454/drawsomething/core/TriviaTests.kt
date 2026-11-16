package com.neil1454.drawsomething.core

import org.json.JSONObject
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import kotlin.random.Random

class TriviaTest {
    @Before fun installPacks() { HolidayPacks.install(PackFiles.all()); TriviaPacks.install(PackFiles.trivia()) }

    @Test fun everyBundledTriviaPackIsValidAndBigEnough() {
        val packs = PackFiles.trivia()
        assertTrue("at least four topics", packs.size >= 4)
        for (p in packs) {
            assertTrue("${p.id} has ${p.questions.size} questions", p.questions.size >= 24)
            for (q in p.questions) {
                assertEquals("four answers: ${q.text}", 4, q.options.size)
                assertTrue(q.correct in 0..3)
                assertTrue("a fact for: ${q.text}", q.fact.isNotBlank())
                assertEquals("no two answers alike: ${q.text}", 4, q.options.toSet().size)
            }
            assertEquals("no repeated questions in ${p.id}", p.questions.size, p.questions.map { it.text }.toSet().size)
        }
        // the correct answers are spread around, not always the same slot
        val slots = packs.flatMap { it.questions }.groupingBy { it.correct }.eachCount()
        assertTrue(slots.size == 4 && slots.values.all { it > 10 })
    }

    private fun pair(seed: Int, pack: String): SyncPair<HolidayEngine> {
        val p = SyncPair({ n, o, h, s, r, c -> HolidayEngine(n, o, h, s, r, c, GameKind.TRIVIA) }, seed)
        p.host.start(JSONObject().put("pack", pack).put("len", 0).put("secs", 15).put("bonus", true))
        p.pump()
        return p
    }

    @Test fun triviaUsesItsOwnPacksAndIsTaggedAsTrivia() {
        val p = pair(1, "uk_quiz")
        assertEquals(GameKind.TRIVIA, p.host.kind)
        assertEquals(GameKind.TRIVIA, p.host.state.kind)
        assertEquals("The UK Quiz", p.host.state.packTitle)
        assertEquals("The UK Quiz", p.client.state.packTitle)
        assertTrue(p.host.state.question.isNotBlank() && p.host.state.options.size == 4)
        // the holiday quiz is a different game with its own packs
        val h = SyncPair({ n, o, hh, s, r, c -> HolidayEngine(n, o, hh, s, r, c) }, 1)
        h.host.start(JSONObject().put("len", 0)); h.pump()
        assertEquals(GameKind.HOLIDAY, h.host.state.kind)
        assertTrue(h.host.state.packTitle != "The UK Quiz")
    }

    @Test fun twoComputersFinishATriviaQuizAndTheOutcomeIsTrivia() {
        for (seed in 1..3) {
            val p = pair(seed, "science_nature")
            val a = HolidayBrain(p.host, true, { null }, Skill.HARD, Random(seed))
            val b = HolidayBrain(p.client, false, { p.host.secretCorrect(2) }, Skill.NORMAL, Random(seed + 7))
            var g = 0
            while (p.host.state.phase == GPhase.PLAYING && g++ < 20_000) { a.think(p.now); b.think(p.now); p.advance(100) }
            assertEquals("seed $seed", GPhase.OVER, p.host.state.phase)
            val o = Outcomes.of(p.host.state)!!
            assertEquals(GameKind.TRIVIA, o.kind)
            assertEquals(p.host.state.myScore, p.client.state.theirScore)
        }
    }
}
