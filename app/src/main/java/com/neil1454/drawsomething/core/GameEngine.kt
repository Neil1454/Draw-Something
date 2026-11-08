package com.neil1454.drawsomething.core

import kotlin.random.Random

enum class Phase {
    LOBBY,
    PICKING,          // I'm the drawer, choosing a word
    WAITING_FOR_WORD, // I'm the guesser, partner is choosing
    DRAWING,          // I'm the drawer, clock running
    GUESSING,         // I'm the guesser, clock running
    RESULT,
    GAME_OVER,
}

/** What happened in one round, from this phone's point of view. */
data class RoundOutcome(
    val round: Int,
    val word: String,
    val category: String,
    val correct: Boolean,
    val drawerIsMe: Boolean,
    val myDelta: Int,
    val theirDelta: Int,
    val elapsedMs: Long,
    val wrongGuesses: Int,
    val skips: Int,
    val multiplier: Double,
    val ruleBreaks: Int = 0,
)

data class GameState(
    val isHost: Boolean,
    val myName: String,
    val theirName: String,
    val phase: Phase = Phase.LOBBY,
    val round: Int = 0,
    val totalRounds: Int = 6,
    val roundSeconds: Int = 90,
    /** Lowest word difficulty the host allowed (e.g. HARD = no easy or medium words). */
    val minDifficulty: Difficulty = Difficulty.EASY,
    val iAgreed: Boolean = false,       // I've agreed to the rules
    val partnerAgreed: Boolean = false,
    val iAmDrawer: Boolean = false,
    val myScore: Int = 0,
    val theirScore: Int = 0,
    val options: List<Word> = emptyList(),
    val word: Word? = null,            // drawer only
    val category: String = "",
    val pattern: String = "",
    val remainingSec: Int = 0,
    val skips: Int = 0,
    val wrongGuesses: Int = 0,
    val ruleBreaks: Int = 0,           // rule breaks the drawer has agreed to this round
    val reportsUsed: Int = 0,          // reports the guesser has sent this round
    val pendingGuess: String? = null,  // drawer: a guess waiting for my verdict
    val pendingReport: RuleBreak? = null, // drawer: a rule-break report waiting for my answer
    val awaitingVerdict: Boolean = false, // guesser: sent a guess, waiting
    val awaitingReport: Boolean = false,  // guesser: sent a report, waiting
    val declined: List<String> = emptyList(),
    val note: String? = null,          // a short message to show (report outcomes)
    val outcome: RoundOutcome? = null,
    val history: List<RoundOutcome> = emptyList(),
    /** On the result screen: my partner has already tapped Next. */
    val partnerReady: Boolean = false,
)

/**
 * One engine runs on each phone. The DRAWER is authoritative for a round (word, clock,
 * verdicts, score); the guesser just displays what it is told. Pure Kotlin so it can be
 * unit-tested without a device. Call everything from a single thread (the UI thread).
 */
class GameEngine(
    myName: String,
    theirName: String,
    private val isHost: Boolean,
    private val send: (Msg) -> Unit,
    private val clock: () -> Long,
    private val random: Random = Random.Default,
) {
    val board = StrokeBoard()

    var state = GameState(isHost = isHost, myName = myName, theirName = theirName)
        private set

    /** Called after every change to [state] or [board]. */
    var onChange: () -> Unit = {}

    private var hostDrawsFirst = true
    private var roundStartAt = 0L
    private var pendingGuessElapsed = 0L
    private val usedWords = mutableSetOf<String>()

    private var iReadyRound = -1
    private var theyReadyRound = -1

    // Live stroke being drawn by me, and the points not yet sent.
    private var strokeSeq = 0
    private var active: StrokeData? = null
    private val outbox = ArrayList<Float>()

    private fun update(f: GameState.() -> GameState) {
        state = state.f()
        onChange()
    }

    private fun drawerIsHost(round: Int) = hostDrawsFirst == (round % 2 == 0)
    private fun iDrawIn(round: Int) = drawerIsHost(round) == isHost
    private val roundMs get() = state.roundSeconds * 1000L

    // ------------------------------------------------------------------ rules + game flow

    /** "I agree to the rules." Both players must agree before the host can start. */
    fun agreeRules() {
        if (state.iAgreed) return
        send(Msg.RulesAgreed)
        update { copy(iAgreed = true) }
    }

    val canStart get() = isHost && state.phase.let { it == Phase.LOBBY || it == Phase.GAME_OVER } &&
        state.iAgreed && state.partnerAgreed

    /** Host only: begin a fresh game. Can be called again for a rematch. */
    fun hostStart(rounds: Int, seconds: Int, minDifficulty: Difficulty = Difficulty.EASY) {
        if (!canStart) return
        hostDrawsFirst = random.nextBoolean()
        send(Msg.Setup(rounds, seconds, hostDrawsFirst, minDifficulty.ordinal))
        newGame(rounds, seconds, minDifficulty)
    }

    private fun newGame(rounds: Int, seconds: Int, minDifficulty: Difficulty) {
        usedWords.clear()
        iReadyRound = -1
        theyReadyRound = -1
        state = state.copy(
            totalRounds = rounds, roundSeconds = seconds, minDifficulty = minDifficulty,
            myScore = 0, theirScore = 0, history = emptyList(), outcome = null,
        )
        beginRound(0)
    }

    private fun beginRound(round: Int) {
        board.clear()
        active = null
        outbox.clear()
        val iDraw = iDrawIn(round)
        update {
            copy(
                phase = if (iDraw) Phase.PICKING else Phase.WAITING_FOR_WORD,
                round = round, iAmDrawer = iDraw,
                options = if (iDraw) Words.pickOptions(random, usedWords, minDifficulty) else emptyList(),
                word = null, category = "", pattern = "", remainingSec = 0,
                skips = 0, wrongGuesses = 0, ruleBreaks = 0, reportsUsed = 0,
                pendingGuess = null, pendingReport = null,
                awaitingVerdict = false, awaitingReport = false, declined = emptyList(),
                note = null, outcome = null, partnerReady = false,
            )
        }
        if (iDraw) send(Msg.Choosing(round))
    }

    /** Drawer: pick one of the offered words. */
    fun chooseWord(word: Word) {
        if (state.phase != Phase.PICKING || !state.iAmDrawer) return
        startDrawing(word)
    }

    /** Drawer: "Surprise me". */
    fun chooseRandom() {
        if (state.phase != Phase.PICKING || !state.iAmDrawer) return
        startDrawing(Words.random(random, usedWords, state.minDifficulty))
    }

    private fun startDrawing(word: Word) {
        usedWords += word.text
        board.clear()
        active = null
        outbox.clear()
        strokeSeq = 0
        roundStartAt = clock()
        val pattern = Words.pattern(word.text)
        update {
            copy(
                phase = Phase.DRAWING, word = word, category = word.category, pattern = pattern,
                remainingSec = roundSeconds, wrongGuesses = 0, pendingGuess = null, pendingReport = null,
                declined = emptyList(),
            )
        }
        send(Msg.RoundStart(state.round, word.category, pattern))
    }

    /** Drawer: give up on this word. Costs [Scoring.SKIP_PENALTY]; a new set of choices appears. */
    fun skipWord() {
        if (state.phase != Phase.DRAWING || !state.iAmDrawer) return
        active = null
        outbox.clear()
        board.clear()
        send(Msg.Skipped)
        update {
            copy(
                phase = Phase.PICKING, skips = skips + 1, word = null, category = "", pattern = "",
                pendingGuess = null, pendingReport = null, declined = emptyList(), wrongGuesses = 0,
                options = Words.pickOptions(random, usedWords, minDifficulty),
            )
        }
    }

    // ------------------------------------------------------------------ drawing (drawer)

    fun beginStroke(color: Int, width: Float, x: Float, y: Float) {
        if (state.phase != Phase.DRAWING || !state.iAmDrawer) return
        flushStroke()
        val s = StrokeData(++strokeSeq, color, width)
        s.pts.add(x); s.pts.add(y)
        board.add(s)
        active = s
        outbox.clear()
        outbox.add(x); outbox.add(y)
        onChange()
    }

    fun extendStroke(x: Float, y: Float) {
        val s = active ?: return
        s.pts.add(x); s.pts.add(y)
        outbox.add(x); outbox.add(y)
        board.touch()
        onChange()
    }

    fun endStroke() {
        if (active == null) return
        flushStroke()
        active = null
    }

    fun undo() {
        if (state.phase != Phase.DRAWING || !state.iAmDrawer) return
        endStroke()
        board.undo()
        send(Msg.Undo)
        onChange()
    }

    fun clearBoard() {
        if (state.phase != Phase.DRAWING || !state.iAmDrawer) return
        endStroke()
        board.clear()
        send(Msg.Clear)
        onChange()
    }

    private fun flushStroke() {
        val s = active ?: return
        if (outbox.isEmpty()) return
        send(Msg.StrokePart(s.id, s.color, s.width, outbox.toList()))
        outbox.clear()
    }

    // ------------------------------------------------------------------ guessing

    /** Guesser: send a guess for the drawer to accept or decline. */
    fun submitGuess(text: String) {
        val t = text.trim().take(40)
        if (state.phase != Phase.GUESSING || state.iAmDrawer || state.awaitingVerdict || t.isEmpty()) return
        send(Msg.Guess(t))
        update { copy(awaitingVerdict = true) }
    }

    /** Drawer: accept ([correct] = true) or decline the pending guess. */
    fun judge(correct: Boolean) {
        val guess = state.pendingGuess ?: return
        if (state.phase != Phase.DRAWING || !state.iAmDrawer) return
        if (state.pendingReport != null) return // answer the rule-break report first
        if (correct) {
            finishRound(true, pendingGuessElapsed)
        } else {
            send(Msg.GuessDeclined(guess))
            update { copy(pendingGuess = null, wrongGuesses = wrongGuesses + 1, declined = declined + guess) }
            if (timeIsUp()) finishRound(false, roundMs)
        }
    }

    // ------------------------------------------------------------------ rule breaks

    /** Guesser: "that drawing breaks a rule". The drawer must agree for it to count. */
    fun reportRuleBreak(rule: RuleBreak) {
        if (state.phase != Phase.GUESSING || state.iAmDrawer) return
        if (state.awaitingReport || state.reportsUsed >= Rules.MAX_REPORTS_PER_ROUND) return
        send(Msg.RuleReport(rule.name))
        update { copy(awaitingReport = true, reportsUsed = reportsUsed + 1, note = null) }
    }

    /**
     * Drawer: answer a report. Agreeing costs [Scoring.RULE_BREAK_PENALTY], clears the drawing
     * (so it has to be redrawn without the offending part) and cancels any guess that was
     * waiting, without a wrong-guess penalty for the guesser. Disagreeing costs nobody anything.
     */
    fun respondToReport(accept: Boolean) {
        if (state.pendingReport == null || state.phase != Phase.DRAWING || !state.iAmDrawer) return
        if (accept) {
            active = null
            outbox.clear()
            board.clear()
            send(Msg.Clear)
            send(Msg.ReportResponse(true))
            update {
                copy(
                    pendingReport = null, pendingGuess = null, ruleBreaks = ruleBreaks + 1,
                    note = "Rule break agreed: −${Scoring.RULE_BREAK_PENALTY} points. Your drawing was cleared, so redraw it without that.",
                )
            }
        } else {
            send(Msg.ReportResponse(false))
            update { copy(pendingReport = null) }
        }
    }

    fun dismissNote() {
        if (state.note != null) update { copy(note = null) }
    }

    // ------------------------------------------------------------------ round end

    private fun timeIsUp() = clock() - roundStartAt >= roundMs

    private fun finishRound(correct: Boolean, elapsedMs: Long) {
        val word = state.word ?: return
        val mult = word.difficulty.multiplier
        val base = if (correct) Scoring.basePoints(elapsedMs, roundMs, mult) else 0
        val result = Msg.RoundResult(
            round = state.round, word = word.text, category = word.category, correct = correct,
            drawerDelta = Scoring.drawerDelta(base, state.skips, state.ruleBreaks),
            guesserDelta = if (correct) Scoring.guesserDelta(base, state.wrongGuesses) else 0,
            elapsedMs = elapsedMs, wrongGuesses = state.wrongGuesses, skips = state.skips,
            multiplier = mult, breaks = state.ruleBreaks,
        )
        active = null
        outbox.clear()
        send(result)
        applyResult(result)
    }

    private fun applyResult(r: Msg.RoundResult) {
        val iDraw = iDrawIn(r.round)
        val myDelta = if (iDraw) r.drawerDelta else r.guesserDelta
        val theirDelta = if (iDraw) r.guesserDelta else r.drawerDelta
        val outcome = RoundOutcome(
            r.round, r.word, r.category, r.correct, iDraw, myDelta, theirDelta,
            r.elapsedMs, r.wrongGuesses, r.skips, r.multiplier, r.breaks,
        )
        update {
            copy(
                phase = Phase.RESULT, outcome = outcome, history = history + outcome,
                myScore = (myScore + myDelta).coerceAtLeast(0),
                theirScore = (theirScore + theirDelta).coerceAtLeast(0),
                pendingGuess = null, pendingReport = null, awaitingVerdict = false, awaitingReport = false,
                remainingSec = 0, note = null,
                partnerReady = theyReadyRound == r.round,
            )
        }
    }

    /** Either player: "I'm done looking at the result." The next round starts when both are ready. */
    fun ready() {
        if (state.phase != Phase.RESULT || iReadyRound == state.round) return
        iReadyRound = state.round
        send(Msg.Ready(state.round))
        maybeAdvance()
    }

    private fun maybeAdvance() {
        if (state.phase != Phase.RESULT) return
        if (iReadyRound != state.round || theyReadyRound != state.round) return
        val next = state.round + 1
        when {
            next >= state.totalRounds -> update { copy(phase = Phase.GAME_OVER) }
            iDrawIn(next) -> beginRound(next)
            // otherwise the next drawer's Choosing message moves us on
        }
    }

    // ------------------------------------------------------------------ clock

    /** Call roughly every 100 ms. Also flushes drawn points to the partner. */
    fun tick() {
        flushStroke()
        if (state.phase != Phase.DRAWING && state.phase != Phase.GUESSING) return
        val remaining = (roundMs - (clock() - roundStartAt)).coerceAtLeast(0)
        val sec = ((remaining + 999) / 1000).toInt()
        if (sec != state.remainingSec) update { copy(remainingSec = sec) }
        if (state.phase == Phase.DRAWING && remaining == 0L &&
            state.pendingGuess == null && state.pendingReport == null
        ) {
            finishRound(false, roundMs)
        }
    }

    // ------------------------------------------------------------------ incoming

    fun onMessage(msg: Msg) {
        when (msg) {
            is Msg.Setup -> if (!isHost) {
                hostDrawsFirst = msg.hostDrawsFirst
                val min = Difficulty.values().getOrElse(msg.minDifficulty) { Difficulty.EASY }
                newGame(msg.rounds, msg.seconds, min)
            }

            Msg.RulesAgreed -> update { copy(partnerAgreed = true) }

            is Msg.Choosing -> {
                val already = state.phase == Phase.WAITING_FOR_WORD && state.round == msg.round
                if (!already && !iDrawIn(msg.round)) beginRound(msg.round)
            }

            is Msg.RoundStart -> if (!iDrawIn(msg.round)) {
                board.clear()
                roundStartAt = clock()
                update {
                    copy(
                        phase = Phase.GUESSING, round = msg.round, iAmDrawer = false,
                        category = msg.category, pattern = msg.pattern, remainingSec = roundSeconds,
                        awaitingVerdict = false, awaitingReport = false, declined = emptyList(), wrongGuesses = 0,
                    )
                }
            }

            is Msg.StrokePart -> if (!state.iAmDrawer) {
                board.applyPart(msg.id, msg.color, msg.width, msg.pts)
                onChange()
            }

            Msg.Undo -> if (!state.iAmDrawer) { board.undo(); onChange() }
            Msg.Clear -> if (!state.iAmDrawer) { board.clear(); onChange() }

            Msg.Skipped -> if (!state.iAmDrawer) {
                board.clear()
                update {
                    copy(
                        phase = Phase.WAITING_FOR_WORD, skips = skips + 1, awaitingVerdict = false,
                        awaitingReport = false, declined = emptyList(), category = "", pattern = "",
                    )
                }
            }

            is Msg.Guess -> if (state.phase == Phase.DRAWING && state.iAmDrawer && state.pendingGuess == null) {
                pendingGuessElapsed = (clock() - roundStartAt).coerceIn(0, roundMs)
                update { copy(pendingGuess = msg.text) }
            }

            is Msg.GuessDeclined -> if (state.phase == Phase.GUESSING) {
                update { copy(awaitingVerdict = false, declined = declined + msg.text, wrongGuesses = wrongGuesses + 1) }
            }

            is Msg.RuleReport -> if (state.phase == Phase.DRAWING && state.iAmDrawer && state.pendingReport == null) {
                val rule = RuleBreak.values().firstOrNull { it.name == msg.rule }
                if (rule != null) update { copy(pendingReport = rule) }
            }

            is Msg.ReportResponse -> if (state.phase == Phase.GUESSING && state.awaitingReport) {
                update {
                    if (msg.accepted) {
                        copy(
                            awaitingReport = false, awaitingVerdict = false, ruleBreaks = ruleBreaks + 1,
                            note = "$theirName agreed it broke a rule (−${Scoring.RULE_BREAK_PENALTY} for them). Their drawing was cleared, so watch for the redraw.",
                        )
                    } else {
                        copy(awaitingReport = false, note = "$theirName didn't think that broke a rule. Keep guessing!")
                    }
                }
            }

            is Msg.RoundResult -> if (state.phase == Phase.GUESSING || state.phase == Phase.WAITING_FOR_WORD) {
                applyResult(msg)
            }

            is Msg.Ready -> {
                theyReadyRound = msg.round
                if (state.phase == Phase.RESULT && state.round == msg.round) update { copy(partnerReady = true) }
                maybeAdvance()
            }
        }
    }
}
