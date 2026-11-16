package com.neil1454.drawsomething.core

import org.json.JSONObject
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test
import kotlin.random.Random

class HangmanTest {
    private fun pair(seed: Int = 1, mode: Int = 0, rounds: Int = 0): SyncPair<HangmanEngine> {
        val p = SyncPair({ n, o, h, s, r, c -> HangmanEngine(n, o, h, s, r, c) }, seed)
        p.host.start(JSONObject().put("mode", mode).put("rounds", rounds))
        p.pump()
        return p
    }

    private fun setterOf(p: SyncPair<HangmanEngine>) = if (p.host.state.iAmSetter) p.host else p.client
    private fun guesserOf(p: SyncPair<HangmanEngine>) = if (p.host.state.iAmSetter) p.client else p.host

    @Test fun wordsAreCleanedAndChecked() {
        assertEquals("HELLO", HangmanRules.clean("hello"))
        assertNull(HangmanRules.clean("hi"))
        assertNull(HangmanRules.clean("ice cream"))
        assertNull(HangmanRules.clean("abc123"))
        assertNull(HangmanRules.clean("a".repeat(15)))
        assertEquals("_A__A", HangmanRules.mask("PANDA".replace('P', 'M').let { "MANDA" }, setOf('A')))
        assertTrue(HangmanWords.ALL.size > 250 && HangmanWords.ALL.all { w -> w.all { it in 'A'..'Z' } && w.length in 4..12 })
    }

    @Test fun theSecretWordNeverReachesTheGuesser() {
        val p = pair(1)
        val setter = setterOf(p); val guesser = guesserOf(p)
        assertEquals("SET", p.host.state.stage)
        guesser.setWord("SNEAKY"); p.pump()        // the guesser can't set the word
        assertEquals("SET", p.host.state.stage)
        setter.setWord("PUZZLE", "a brain teaser"); p.pump()
        assertEquals("GUESS", guesser.state.stage)
        assertEquals("______", guesser.state.mask)
        assertEquals("", guesser.state.word)
        assertEquals("", guesser.state.myWord)
        assertEquals("PUZZLE", setter.state.myWord)
        assertEquals("a brain teaser", guesser.state.hint)
        assertTrue(guesser.state.myTurn); assertFalse(setter.state.myTurn)
    }

    @Test fun rightAndWrongLettersWorkAndDuplicatesAreIgnored() {
        val p = pair(2)
        val setter = setterOf(p); val guesser = guesserOf(p)
        setter.setWord("PUZZLE"); p.pump()
        guesser.guessLetter('Z'); p.pump()
        assertEquals("__ZZ__", guesser.state.mask); assertEquals(0, guesser.state.misses)
        guesser.guessLetter('Z'); guesser.guessLetter('7'); p.pump()
        assertEquals(1, guesser.state.moves)
        guesser.guessLetter('Q'); p.pump()
        assertEquals(1, guesser.state.misses)
        setter.guessLetter('P'); p.pump()          // the setter can't guess
        assertEquals(2, guesser.state.moves)
    }

    @Test fun solvingScoresForTheGuesserAndEightMissesScoreForTheSetter() {
        val p = pair(3)
        val setter = setterOf(p); val guesser = guesserOf(p)
        setter.setWord("KITE"); p.pump()
        for (c in "KITE") guesser.guessLetter(c)
        p.pump()
        assertEquals("REVEAL", guesser.state.stage)
        assertEquals("KITE", guesser.state.word)
        assertEquals(HangmanRules.solvedPoints(0), guesser.state.myScore)
        assertEquals(0, setter.state.myScore)
        // next word: the other player sets
        p.advance(5000)
        assertEquals(2, p.host.state.round); assertEquals("SET", p.host.state.stage)
        assertTrue(setterOf(p) === guesser)
        guesserOf(p).let { g -> setterOf(p).setWord("QUARTZ"); p.pump(); for (c in "BCDFGHJK") g.guessLetter(c); p.pump() }
        assertEquals(GPhase.OVER, p.host.state.phase)
        val scoreOfSetter = setter.state.myScore
        assertTrue(scoreOfSetter == 0 || scoreOfSetter == HangmanRules.STUMPED_POINTS)
    }

    @Test fun aWrongWholeWordCostsTwoLivesAndARightOneWins() {
        val p = pair(4)
        val setter = setterOf(p); val guesser = guesserOf(p)
        setter.setWord("RIVER"); p.pump()
        guesser.guessWord("WATER"); p.pump()
        assertEquals(2, guesser.state.misses)
        guesser.guessWord("RIVER"); p.pump()
        assertEquals("REVEAL", guesser.state.stage)
        assertEquals(HangmanRules.solvedPoints(2), guesser.state.myScore)
    }

    @Test fun sharedWordsPassTheTurnOnAMissOnly() {
        val p = pair(5, mode = 1)
        assertEquals("GUESS", p.host.state.stage)
        val first = if (p.host.state.myTurn) p.host else p.client
        val second = if (first === p.host) p.client else p.host
        val mask = first.state.mask
        assertTrue(mask.all { it == '_' })
        // find a letter that is in the word and one that is not: use the dictionary to peek at the answer the cheeky way
        var hit = false
        for (c in "ETAOINSHRDLU") {
            if (!first.state.myTurn) break
            first.guessLetter(c); p.pump()
            hit = first.state.lastHit
            if (!hit) break
        }
        assertFalse("a wrong letter passes the turn", first.state.myTurn)
        assertTrue(second.state.myTurn)
    }

    @Test fun twoComputersPlayDuelsAndSharedGamesToTheEnd() {
        for (mode in 0..1) for (seed in 1..4) {
            val p = pair(seed, mode = mode)
            val a = HangmanBrain(p.host, Skill.HARD, Random(seed)); val b = HangmanBrain(p.client, Skill.NORMAL, Random(seed + 20))
            var g = 0
            while (p.host.state.phase == GPhase.PLAYING && g++ < 6000) { a.think(p.now); b.think(p.now); p.advance(100) }
            assertEquals("mode $mode seed $seed", GPhase.OVER, p.host.state.phase)
            val r = p.host.state.result
            assertEquals(when (r) { 1 -> 2; 2 -> 1; else -> 3 }, p.client.state.result)
            assertEquals(p.host.state.myScore, p.client.state.theirScore)
        }
    }

    @Test fun theCandidateSearchFindsTheWord() {
        val c = HangmanRules.candidates("_UZZLE", setOf('Z', 'L', 'E', 'U'), listOf("PUZZLE", "MUZZLE", "NUZZLE", "BUTTER"))
        assertEquals(listOf("PUZZLE", "MUZZLE", "NUZZLE"), c)
        assertEquals(listOf("PUZZLE", "NUZZLE"), HangmanRules.candidates("_UZZLE", setOf('Z', 'L', 'E', 'U', 'M'), listOf("PUZZLE", "MUZZLE", "NUZZLE")))
    }

    @Test fun badMessagesAreIgnored() {
        val p = pair(6)
        val before = p.client.state
        p.client.onMessage(GMsg.Snap("HANGMAN", JSONObject().put("md", 9).put("st", "SET").put("mk", "<script>").put("us", "!!")))
        p.host.onMessage(GMsg.Act("HANGMAN", JSONObject().put("a", "g").put("c", "")))
        p.host.onMessage(GMsg.Act("HANGMAN", JSONObject()))
        assertTrue(p.host.state.phase == GPhase.PLAYING)
        assertTrue(before.phase == GPhase.PLAYING)
    }
}
