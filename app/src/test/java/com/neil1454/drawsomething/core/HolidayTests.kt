package com.neil1454.drawsomething.core

import org.json.JSONObject
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import java.io.File
import kotlin.random.Random

/** Finds the holiday packs bundled with the app, wherever the tests are run from. */
object PackFiles {
    private val dirs = listOf(
        "src/main/assets/holidays",
        "app/src/main/assets/holidays",
        "/home/user/draw-something/app/src/main/assets/holidays",
    )

    fun all(): List<HolidayPack> {
        val dir = dirs.map { File(it) }.firstOrNull { it.isDirectory } ?: error("holiday packs not found")
        return dir.listFiles { f -> f.name.endsWith(".json") }!!.mapNotNull { HolidayPack.parse(it.readText()) }
    }
}

class HolidayPackTest {
    @Test fun everyBundledPackIsValid() {
        val packs = PackFiles.all()
        assertTrue(packs.isNotEmpty())
        for (p in packs) {
            assertTrue("${p.id} needs a good number of questions", p.questions.size >= 30)
            for (q in p.questions) {
                assertEquals("${q.text} should have four answers", 4, q.options.size)
                assertEquals(4, q.options.toSet().size)
                assertTrue(q.correct in 0..3)
                assertTrue(q.text.endsWith("?") || q.text.endsWith("…"))
            }
            assertEquals("questions must be unique", p.questions.size, p.questions.map { it.text }.toSet().size)
        }
    }

    @Test fun answersAreNotAlwaysInTheSamePlaceOrTheLongestOne() {
        val q = PackFiles.all().first().questions
        val counts = IntArray(4)
        q.forEach { counts[it.correct]++ }
        assertTrue("correct answers should be spread across A-D: ${counts.toList()}", counts.all { it >= q.size / 8 })
        val longest = q.count { it.options[it.correct].length == it.options.maxOf { o -> o.length } }
        assertTrue("the longest option shouldn't give the answer away ($longest of ${q.size})", longest < q.size * 0.45)
    }

    @Test fun badPacksAreRejectedAndBadQuestionsSkipped() {
        assertNull(HolidayPack.parse("not json"))
        assertNull(HolidayPack.parse("""{"id":"x","title":"T","questions":[]}"""))
        assertNull(HolidayPack.parse("""{"id":"","title":"T","questions":[{"q":"?","a":["a","b","c","d"],"c":0}]}"""))
        val mixed = """{"id":"Test Pack!","title":"T","questions":[
            {"q":"ok?","a":["a","b","c","d"],"c":2,"fact":"f"},
            {"q":"bad index?","a":["a","b","c","d"],"c":9},
            {"q":"too few?","a":["a","b"],"c":0},
            {"q":"dupes?","a":["a","a","c","d"],"c":0}
        ]}"""
        val p = HolidayPack.parse(mixed)!!
        assertEquals("testpack", p.id)
        assertEquals(1, p.questions.size)
        assertEquals("c", p.questions[0].options[p.questions[0].correct])
    }
}

class HolidayQuizTest {
    @Before fun installPacks() = HolidayPacks.install(PackFiles.all())

    private fun pair(len: Int = 0, secs: Int = 10, seed: Int = 1): SyncPair<HolidayEngine> {
        val p = SyncPair({ n, o, h, s, r, c -> HolidayEngine(n, o, h, s, r, c) }, seed)
        p.host.start(JSONObject().put("pack", "cape_verde_sal").put("len", len).put("secs", secs))
        p.pump()
        return p
    }

    private fun SyncPair<HolidayEngine>.rightAnswer() = host.secretCorrect()!!
    private fun SyncPair<HolidayEngine>.wrongAnswer() = (0..3).first { it != rightAnswer() }

    @Test fun bothPhonesSeeTheSameQuestion() {
        val p = pair()
        val h = p.host.state
        val c = p.client.state
        assertEquals(GPhase.PLAYING, c.phase)
        assertEquals(8, h.total)
        assertEquals(h.question, c.question)
        assertEquals(h.options, c.options)
        assertEquals(4, c.options.size)
        assertEquals(-1, c.correct)
    }

    @Test fun thePartnerNeverReceivesTheAnswerBeforeItIsRevealed() {
        val sent = ArrayList<String>()
        val e = HolidayEngine("A", "B", true, { sent.add(String(it.encode())) }, Random(3)) { 0L }
        e.start(JSONObject().put("pack", "cape_verde_sal").put("len", 0))
        assertTrue(sent.isNotEmpty())
        sent.forEach { assertFalse("answer leaked: $it", it.contains("\"cr\"") || it.contains("\"c1\"")) }
    }

    @Test fun fastestRightAnswerWinsMostAndWrongCosts() {
        val p = pair()
        val right = p.rightAnswer()
        p.host.pick(right, forcedMs = 2000)
        p.client.pick(p.wrongAnswer(), forcedMs = 2500)
        p.pump()
        // both answered -> resolved at once
        val h = p.host.state
        assertEquals("REVEAL", h.step)
        assertEquals(right, h.correct)
        assertTrue("first right answer earns base + speed bonus", h.myDelta >= HolidayRules.CORRECT + 50)
        assertEquals(HolidayRules.WRONG, h.theirDelta)
        assertEquals(0, p.client.state.myScore) // can't go below zero
        assertEquals(h.myDelta, p.client.state.theirDelta)
        assertTrue(p.client.state.fact.isNotBlank() || p.client.state.correct >= 0)
    }

    @Test fun slowerRightAnswerStillGetsAConsolation() {
        val p = pair()
        val right = p.rightAnswer()
        p.client.pick(right, forcedMs = 1500)
        p.pump()
        p.host.pick(right, forcedMs = 4000)
        p.pump()
        assertEquals(HolidayRules.ALSO_RIGHT, p.host.state.myDelta)
        assertTrue(p.client.state.myDelta > HolidayRules.CORRECT)
    }

    @Test fun theOtherPlayerGetsAChanceAfterAWrongAnswer() {
        val p = pair()
        p.host.pick(p.wrongAnswer(), forcedMs = 1000)
        p.pump()
        assertEquals("ASK", p.host.state.step)
        assertTrue(p.client.state.theyAnswered)
        p.client.pick(p.rightAnswer(), forcedMs = 3000)
        p.pump()
        assertEquals("REVEAL", p.host.state.step)
        assertTrue(p.client.state.myDelta > 0)
    }

    @Test fun aRightAnswerWaitsBrieflyForAFasterRivalThenResolves() {
        val p = pair()
        p.host.pick(p.rightAnswer(), forcedMs = 3000)
        p.pump()
        assertEquals("ASK", p.host.state.step)
        p.advance(HolidayRules.GRACE_MS + 200)
        assertEquals("REVEAL", p.host.state.step)
        assertEquals(0, p.client.state.myDelta) // didn't answer
    }

    @Test fun runningOutOfTimeScoresNothing() {
        val p = pair(secs = 10)
        p.advance(10_200)
        assertEquals("REVEAL", p.host.state.step)
        assertEquals(0, p.host.state.myDelta)
        assertEquals(0, p.client.state.theirScore)
    }

    @Test fun cannotAnswerTwiceOrWithABogusPosition() {
        val p = pair()
        p.host.pick(p.wrongAnswer(), forcedMs = 1000)
        val before = p.host.state.myChoice
        p.host.pick(p.rightAnswer(), forcedMs = 1000)
        assertEquals(before, p.host.state.myChoice)
        p.client.pick(7)
        p.client.pick(-1)
        assertEquals(-1, p.client.state.myChoice)
        p.client.act(JSONObject().put("a", "pick").put("i", 99).put("ms", 5).put("sq", 1))
        p.client.act(JSONObject().put("a", "pick").put("i", 1).put("ms", 5).put("sq", 77))
        p.pump()
        assertFalse(p.host.state.theyAnswered)
    }

    @Test fun aWholeQuizPlaysToTheEndAndBothPhonesAgree() {
        val p = pair(len = 0, secs = 10, seed = 5)
        var guard = 0
        while (p.host.state.phase != GPhase.OVER && guard++ < 4000) {
            val h = p.host.state
            if (h.step == "ASK") {
                val key = p.host.secretCorrect()
                if (key != null) {
                    if (h.myChoice == -1) p.host.pick(key, forcedMs = 2500)
                    if (p.client.state.myChoice == -1 && h.index % 2 == 0) p.client.pick(key, forcedMs = 1800)
                }
            }
            p.advance(300)
        }
        assertEquals(GPhase.OVER, p.host.state.phase)
        assertEquals(GPhase.OVER, p.client.state.phase)
        assertEquals(p.host.state.myScore, p.client.state.theirScore)
        assertEquals(p.host.state.theirScore, p.client.state.myScore)
        assertEquals(8, p.host.state.myRight)
        assertEquals(1, p.host.state.result)
        assertEquals(2, p.client.state.result)
    }

    @Test fun correctAnswerPositionIsSpreadOverAllFourSlots() {
        val counts = IntArray(4)
        for (seed in 1..160) {
            val e = HolidayEngine("A", "B", true, { }, Random(seed)) { 0L }
            e.start(JSONObject().put("pack", "cape_verde_sal"))
            counts[e.secretCorrect()!!]++
        }
        assertTrue("positions ${counts.toList()}", counts.all { it in 18..65 })
    }

    @Test fun theComputerPlaysAWholeQuiz() {
        val d = Duel(3, Skill.NORMAL, Skill.NORMAL)
        d.host.holiday.start(JSONObject().put("pack", "cape_verde_sal").put("len", 0).put("secs", 10))
        var guard = 0
        while (d.host.holiday.state.phase != GPhase.OVER && guard++ < 4000) d.advance(250)
        val h = d.host.holiday.state
        val g = d.guest.holiday.state
        assertEquals(GPhase.OVER, h.phase)
        assertEquals(h.myScore, g.theirScore)
        assertTrue("the brains should know some answers", h.myRight + h.theirRight >= 6)
    }

    @Test fun hostileMessagesDoNotCrash() {
        val p = pair()
        val junk = listOf("{}", """{"a":"pick"}""", """{"a":"pick","i":"x","ms":"y","sq":"z"}""", """{"a":"explode"}""")
        junk.forEach { p.host.onMessage(GMsg.Act("HOLIDAY", JSONObject(it))) }
        p.client.onMessage(GMsg.Snap("HOLIDAY", JSONObject("""{"st":"ASK"}""")))
        assertNotNull(p.host.state)
        assertEquals(GPhase.PLAYING, p.host.state.phase)
    }
}
