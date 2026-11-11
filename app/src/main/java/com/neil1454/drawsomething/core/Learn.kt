package com.neil1454.drawsomething.core

import org.json.JSONObject

// ====================================================================================
// Learn mode: watch a real game play itself while captions explain what is happening.
// Two computer players play each other on a virtual clock (so Pause really pauses), and a
// narrator turns each move into a plain-English caption, with a few tips along the way.
// You are "You" (Player 1) in the demo; the other side is "Player 2".
// ====================================================================================

class LearnSession(val kind: GameKind, val seed: Int) {
    val duel = Duel(seed, Skill.NORMAL, if (kind == GameKind.DRAW) Skill.HARD else Skill.NORMAL)

    var caption: String = intro(kind)
        private set

    /** Changes whenever the caption does, so the screen can animate it. */
    var captionId = 0
        private set

    /** The demo game has ended. */
    var finished = false
        private set

    private var started = false
    private var last = -1
    private var lastSeq = -1
    private var lastGift = 0
    private var finishedAt = 0L

    /** Move the demo forward by [ms] (virtual milliseconds). */
    fun advance(ms: Long) {
        if (!started) {
            started = true
            begin()
        }
        // while the pretend guesser "thinks" in the Draw Something demo, skip ahead
        val boost = if (kind == GameKind.DRAW && drawStep == 2 && duel.host.draw.state.pendingGuess == null) 6 else 1
        duel.advance(ms * boost)
        narrate()
    }

    private fun say(text: String) {
        caption = text
        captionId++
    }

    private fun finish(text: String) {
        if (finished) return
        finished = true
        finishedAt = duel.now
        say(text)
    }

    private fun deltaText(delta: Int, choice: Int, correct: Int) = when {
        choice == -1 -> "didn't answer in time"
        choice == correct -> "got it right (+$delta)"
        else -> "got it wrong ($delta)"
    }

    private fun who(mine: Boolean) = if (mine) "You" else "Player 2"

    private fun tip(tips: List<String>, n: Int): String = tips.getOrNull(n - 1)?.let { "\n💡 $it" } ?: ""

    // ---------------------------------------------------------------- starting the demo

    private fun begin() {
        val h = duel.host
        when (kind) {
            GameKind.DRAW -> h.draw.hostStart(1, 60, Difficulty.EASY, solo = true)
            GameKind.MINES -> h.mines.start(MineSize.SMALL)
            GameKind.SEA -> h.sea.start(SeaSize.QUICK)
            GameKind.DROP -> h.drop.start(JSONObject().put("size", 0).put("power", false).put("theme", 0))
            GameKind.MEMORY -> h.memory.start(JSONObject().put("size", 0).put("theme", 0))
            GameKind.QUICK -> h.quick.start(JSONObject().put("match", 0).put("fake", true))
            GameKind.DICE -> h.dice.start(JSONObject().put("target", 0).put("two", false))
            GameKind.FLIP -> h.flip.start(JSONObject().put("size", 1))
            GameKind.HILO -> h.hilo.start(JSONObject().put("rows", 0).put("odds", true))
            GameKind.TETRIS -> h.tetris.start(JSONObject().put("mode", 1).put("lvl", 0).put("gifts", true).put("mins", 0))
            GameKind.HOLIDAY -> h.holiday.start(JSONObject().put("len", 0).put("secs", 30))
            GameKind.DRAUGHTS -> h.draughts.start(JSONObject())
            GameKind.LUDO -> h.ludo.start(JSONObject())
            GameKind.MONOPOLY -> h.monopoly.start(JSONObject().put("len", 2))
        }
    }

    // ---------------------------------------------------------------- captions

    private fun narrate() {
        when (kind) {
            GameKind.DRAW -> drawScript()
            GameKind.DROP -> {
                val s = duel.host.drop.state
                if (s.moves != last) {
                    last = s.moves
                    if (s.moves > 0) {
                        val mine = s.grid.getOrNull(s.last) == s.myDisc
                        val col = (s.last % s.w) + 1
                        say(
                            when (s.result) {
                                1, 2 -> "${who(s.result == 1)} drop in column $col and make FOUR IN A ROW! ${who(s.result == 1)} win${if (s.result == 1) "" else "s"}!"
                                3 -> "The board is full: it's a draw."
                                else -> "${who(mine)} drop${if (mine) "" else "s"} a disc in column $col." + tip(DROP_TIPS, s.moves)
                            },
                        )
                    }
                }
                if (s.phase == GPhase.OVER) finish(outro(kind))
            }
            GameKind.FLIP -> {
                val s = duel.host.flip.state
                if (s.moves != last) {
                    last = s.moves
                    if (s.moves > 0) {
                        val mine = s.grid.getOrNull(s.last) == s.myDisc
                        val corner = s.last in listOf(0, s.n - 1, s.n * (s.n - 1), s.n * s.n - 1)
                        val n = s.flipped.size
                        say(
                            "${who(mine)} place${if (mine) "" else "s"} a disc and flip${if (mine) "" else "s"} $n of the other colour." +
                                (if (corner) " A CORNER! Corners can never be flipped." else "") +
                                (if (s.passed != 0) " ${if (s.passed == 1) "You have" else "Player 2 has"} no legal move, so that player passes." else "") +
                                tip(FLIP_TIPS, s.moves),
                        )
                    }
                }
                if (s.phase == GPhase.OVER) finish(outro(kind))
            }
            GameKind.MEMORY -> {
                val s = duel.host.memory.state
                if (s.moves != last) {
                    last = s.moves
                    if (s.moves > 0) {
                        val mine = s.myTurn
                        say(
                            when {
                                s.event == "match" -> "A MATCH! ${who(mine)} score${if (mine) "" else "s"} ${s.eventPoints} points and go${if (mine) "" else "es"} again." +
                                    (if ((if (mine) s.myStreak else s.theirStreak) >= 2) " That's a streak: bonus points!" else "")
                                s.event == "miss" -> "No match. The cards flip back, so remember where they were!"
                                else -> "${who(mine)} turn${if (mine) "" else "s"} over a card. Where is its twin?"
                            } + tip(MEMORY_TIPS, s.moves),
                        )
                    }
                }
                if (s.phase == GPhase.OVER) finish(outro(kind))
            }
            GameKind.QUICK -> {
                val s = duel.host.quick.state
                val marker = s.seq * 10 + when (s.step) { "WAIT" -> 1; "FAKE" -> 2; "GO" -> 3; "RESULT" -> 4; else -> 0 }
                if (marker != last) {
                    last = marker
                    say(
                        when (s.step) {
                            "WAIT" -> "Wait for it… don't tap yet! Tapping early loses the round."
                            "FAKE" -> "A DECOY! This isn't green, so tapping now would lose. Only GREEN counts."
                            "GO" -> "GREEN! Tap as fast as you can."
                            "RESULT" -> s.why.ifBlank { "Round over." } + "\n💡 Fastest correct tap wins the round. First to ${s.target} rounds wins."
                            else -> caption
                        },
                    )
                }
                if (s.phase == GPhase.OVER) finish(outro(kind))
            }
            GameKind.DICE -> {
                val s = duel.host.dice.state
                if (s.moves != last) {
                    last = s.moves
                    if (s.moves > 0) {
                        val mine = s.eventByMe
                        say(
                            when (s.event) {
                                "roll", "doubles" -> "${who(mine)} roll${if (mine) "" else "s"} ${s.dice.joinToString(" + ")}: +${s.eventPoints}. Holding ${s.held}. Roll again or bank?"
                                "bust" -> "A 1! BUST. ${who(mine)} lose${if (mine) "" else "s"} the ${s.eventPoints} held and the turn ends."
                                "bank" -> "${who(mine)} BANK${if (mine) "" else "s"} ${s.eventPoints}. Those points are safe now."
                                else -> caption
                            } + tip(DICE_TIPS, s.moves),
                        )
                    }
                }
                if (s.phase == GPhase.OVER) finish(outro(kind))
            }
            GameKind.HILO -> {
                val s = duel.host.hilo.state
                if (s.moves != last) {
                    last = s.moves
                    if (s.moves > 0 && s.row.isNotEmpty()) {
                        val mine = s.resultByMe
                        val card = HiLoRules.cardName(s.row[s.idx])
                        say(
                            when (s.result) {
                                "hit" -> "${who(mine)} guessed right! The card was $card. The next guess will be worth ${(s.guesses + 1) * HiLoRules.STEP_POINTS}."
                                "full" -> "A FULL ROW! ${who(mine)} cleared all seven cards for a ${HiLoRules.FULL_ROW_BONUS}-point bonus!"
                                "miss" -> "Wrong! The card was $card. ${who(mine)}'s row ends, but the points already won are kept."
                                "same" -> "Same value ($card)! A tie counts as wrong, so that row ends."
                                else -> "${who(s.myTurn)} swapped the face-up card. Change can only be used once per game."
                            } + tip(HILO_TIPS, s.moves),
                        )
                    }
                }
                if (s.phase == GPhase.OVER) finish(outro(kind))
            }
            GameKind.TETRIS -> {
                val s = duel.host.tetris.state
                if (s.phase == GPhase.OVER) {
                    finish(outro(kind))
                } else if (s.countdownMs == 0) {
                    if (last == -1) {
                        last = 0
                        say("Blocks fall from the top. Your board is on the LEFT and your rival's is on the RIGHT, live. Fill complete rows to clear them." + tip(TETRIS_TIPS, 1))
                    } else if (s.clearSeq != lastSeq && s.lastClear > 0) {
                        lastSeq = s.clearSeq
                        val n = s.lastClear
                        say(
                            "${who(s.lastClearByMe)} clear${if (s.lastClearByMe) "" else "s"} $n line${if (n == 1) "" else "s"}!" +
                                (if (n >= 2) " The pieces blown up are sent to ${who(!s.lastClearByMe)} as GIFT PIECES (gold, striped). They join the queue to be placed like any other piece." else " Clear 2 or more lines at once to send gift pieces.") +
                                tip(TETRIS_TIPS, 1 + s.clearSeq),
                        )
                    } else if (s.giftSeq != lastGift && s.giftSeq > 0) {
                        lastGift = s.giftSeq
                        say("🎁 Gift pieces are on their way! They wait a few seconds (clearing rows cancels them), then arrive in your NEXT queue as gold pieces you place yourself.")
                    }
                }
            }
            GameKind.DRAUGHTS -> {
                val s = duel.host.draughts.state
                if (s.moves != last) {
                    last = s.moves
                    if (s.moves > 0) {
                        val mine = s.grid.getOrNull(s.last)?.let { it == 'a' || it == 'A' } == true
                        val files = "abcdefgh"
                        fun name(i: Int) = "${files[i % 8]}${8 - i / 8}"
                        say(
                            if (s.result != 0) {
                                if (s.result == 3) "No captures for a long time: it's a draw." else "${who(s.result == 1)} win${if (s.result == 1) "" else "s"}: the other side has no moves left!"
                            } else {
                                "${who(mine)} move${if (mine) "" else "s"} ${name(s.lastFrom)} to ${name(s.last)}" +
                                    (if (s.captured >= 0) " and JUMP a piece! Captures are compulsory." else ".") +
                                    (if (s.crowned) " It reaches the far side and becomes a KING (it can now move backwards too)." else "") +
                                    tip(DRAUGHTS_TIPS, s.moves)
                            },
                        )
                    }
                }
                if (s.phase == GPhase.OVER) finish(outro(kind))
            }
            GameKind.LUDO -> {
                val s = duel.host.ludo.state
                val key = s.rolls * 1000 + s.moves
                if (key != last) {
                    last = key
                    val mine = s.eventByMe
                    say(
                        if (s.result != 0) "${who(s.result == 1)} get${if (s.result == 1) "" else "s"} all four pieces home and win!"
                        else when (s.event) {
                            "capture" -> "${who(mine)} land${if (mine) "" else "s"} on a lone piece and send it back to base!"
                            "home" -> "${who(mine)} reach${if (mine) "" else "es"} home with an exact roll."
                            "out" -> "${who(mine)} roll${if (mine) "" else "s"} a 6 and bring a piece out onto the track."
                            "stuck" -> "Rolled ${s.die}: no piece can use it (you need a 6 to start, and an exact roll to finish)."
                            "triple" -> "Three 6s in a row lose the turn!"
                            else -> "Rolled ${s.die}: a piece moves ${s.die} steps." + tip(LUDO_TIPS, s.moves)
                        },
                    )
                }
                if (s.phase == GPhase.OVER) finish(outro(kind))
            }
            GameKind.MONOPOLY -> {
                val s = duel.host.monopoly.state
                if (s.seq != last) {
                    last = s.seq
                    s.log.lastOrNull()?.let { say(it + tip(MONOPOLY_TIPS, s.seq / 4)) }
                }
                if (s.phase == GPhase.OVER) finish(outro(kind))
            }
            GameKind.HOLIDAY -> {
                val s = duel.host.holiday.state
                if (s.phase == GPhase.OVER) {
                    finish(outro(kind))
                } else if (s.step == "ASK" && s.seq != lastSeq) {
                    lastSeq = s.seq
                    say("Question ${s.index} of ${s.total} · ${s.category.ifEmpty { "Holiday" }}\n${s.question}\nPick the right answer before time runs out. Quickest correct answer scores the most." + tip(HOLIDAY_TIPS, s.index))
                } else if (s.step == "REVEAL" && s.moves != last) {
                    last = s.moves
                    val right = s.options.getOrNull(s.correct) ?: "?"
                    say(
                        "The answer: $right.\n" +
                            "You ${deltaText(s.myDelta, s.myChoice, s.correct)}; Player 2 ${deltaText(s.theirDelta, s.theirChoice, s.correct)}." +
                            (if (s.fact.isNotBlank()) "\n💡 ${s.fact}" else ""),
                    )
                }
            }
            GameKind.MINES -> {
                val s = duel.host.mines.state
                if (s.moves != last) {
                    last = s.moves
                    val n = s.notes.firstOrNull()
                    if (s.moves > 0 && n != null) {
                        val w = who(n.byMe)
                        say(
                            when {
                                n.kind.startsWith("safe") -> "$w open${if (n.byMe) "" else "s"} ${n.count} square${if (n.count == 1) "" else "s"}: +${n.value}. Numbers show how many bombs touch a square."
                                n.kind == "boom" -> "💥 $w hit a bomb! −${n.value} and the turn ends. The game goes on."
                                n.kind == "block" -> "🛡️ $w's shield blocked a bomb!"
                                else -> "$w found a power-up! (🎁 bonus, 🛡️ shield, ✨ double, ⏩ extra turn, 🧲 steal, 📡 radar)"
                            } + tip(MINES_TIPS, s.moves),
                        )
                    }
                }
                if (s.phase == MinesPhase.OVER) finish(outro(kind))
            }
            GameKind.SEA -> {
                val s = duel.host.sea.state
                val marker = s.moves * 10 + (if (s.phase == SeaPhase.PLACING) 1 else 2)
                if (marker != last) {
                    last = marker
                    when {
                        s.phase == SeaPhase.PLACING -> say("Both players secretly place a fleet of ships. Shuffle if you don't like the layout, then press Ready.")
                        s.moves == 0 -> say("Battle! Take turns firing at the enemy grid. A hit means you fire again.")
                        else -> say((s.note ?: "Shot fired.") + tip(SEA_TIPS, s.moves))
                    }
                }
                if (s.phase == SeaPhase.OVER) finish(outro(kind))
            }
        }
    }

    // ---------------------------------------------------------------- Draw Something: a scripted drawing

    private var drawStep = 0
    private var strokeIdx = 0
    private var pointIdx = -1
    private var nextAt = 0L
    private var judged = false

    private fun drawScript() {
        val h = duel.host.draw
        val s = h.state
        when (drawStep) {
            0 -> if (s.phase == Phase.PICKING) {
                say("Step 1 · The drawer picks a word to draw. Here it's “house”. (Harder words are worth more points.)")
                h.chooseWord(Words.all.first { it.text == "house" })
                drawStep = 1
                nextAt = duel.now + 3000
            }
            1 -> if (s.phase == Phase.DRAWING && duel.now >= nextAt) {
                if (strokeIdx == 0 && pointIdx < 0) say("Step 2 · Draw it with your finger! Only drawings: no letters or numbers. The other player sees every stroke live.")
                val stroke = HOUSE[strokeIdx]
                if (pointIdx < 0) {
                    h.beginStroke(stroke.color, 0.014f, stroke.pts[0].first, stroke.pts[0].second)
                    pointIdx = 1
                    nextAt = duel.now + 150
                } else if (pointIdx < stroke.pts.size) {
                    h.extendStroke(stroke.pts[pointIdx].first, stroke.pts[pointIdx].second)
                    pointIdx++
                    nextAt = duel.now + 130
                } else {
                    h.endStroke()
                    strokeIdx++
                    pointIdx = -1
                    nextAt = duel.now + 500
                    if (strokeIdx >= HOUSE.size) {
                        drawStep = 2
                        say("Step 3 · Player 2 is working out what it is. The clock is ticking: a faster guess is worth more points.")
                    }
                }
            }
            2 -> {
                val guess = s.pendingGuess
                if (guess != null && !judged) {
                    val ok = guess == s.word?.text
                    say(
                        if (ok) "Player 2 guesses “$guess”. The drawer taps ✓ Correct!"
                        else "Player 2 guesses “$guess”. The drawer taps ✗ Not quite, and they try again (wrong guesses cost 5 points).",
                    )
                    judged = ok
                    h.judge(ok)
                }
                if (s.phase == Phase.RESULT) {
                    drawStep = 3
                    nextAt = duel.now + 6000
                    say("Round over! The word is revealed. Both players score from the same clock: clear drawings and quick guesses win. Skipping a word costs the drawer 15.")
                }
            }
            3 -> if (duel.now >= nextAt) {
                h.ready()
                drawStep = 4
            }
            4 -> if (s.phase == Phase.GAME_OVER) finish(outro(kind))
        }
    }

    private class Line(val color: Int, val pts: List<Pair<Float, Float>>)

    companion object {
        private fun line(color: Int, vararg corners: Pair<Float, Float>): Line {
            val pts = ArrayList<Pair<Float, Float>>()
            pts += corners[0]
            for (i in 1 until corners.size) {
                val (x0, y0) = corners[i - 1]
                val (x1, y1) = corners[i]
                for (k in 1..8) pts += (x0 + (x1 - x0) * k / 8f) to (y0 + (y1 - y0) * k / 8f)
            }
            return Line(color, pts)
        }

        private val HOUSE: List<Line> = listOf(
            line(Palette.colors[0], 0.25f to 0.5f, 0.75f to 0.5f, 0.75f to 0.85f, 0.25f to 0.85f, 0.25f to 0.5f),
            line(Palette.colors[2], 0.2f to 0.5f, 0.5f to 0.2f, 0.8f to 0.5f),
            line(Palette.colors[10], 0.45f to 0.85f, 0.45f to 0.65f, 0.55f to 0.65f, 0.55f to 0.85f),
            line(Palette.colors[7], 0.32f to 0.58f, 0.4f to 0.58f, 0.4f to 0.66f, 0.32f to 0.66f, 0.32f to 0.58f),
        )

        private val DROP_TIPS = listOf(
            "Each disc falls to the lowest empty spot in its column.",
            "The middle column is the most useful: it is part of the most possible lines.",
            "Aim for four in a row: across, up and down, or diagonally.",
            "Watch your opponent too. Block their threes before they become fours!",
            "In power mode you also get a bomb disc that blasts discs away.",
        )
        private val FLIP_TIPS = listOf(
            "Place a disc so it traps a line of the other colour between two of yours. Glowing dots show where you can play.",
            "Every trapped disc flips to your colour.",
            "Corners can't be flipped, so they're very valuable.",
            "If you have no legal move you pass. When nobody can move, the most discs wins.",
        )
        private val MEMORY_TIPS = listOf(
            "Flip two cards. If they match you score and go again.",
            "A miss passes the turn, so try to remember every card you see.",
            "Pairs in a row build a streak worth bonus points.",
        )
        private val DICE_TIPS = listOf(
            "Keep rolling to build up points, but rolling a 1 loses everything you're holding.",
            "BANK to keep your points safe and pass the turn.",
            "Bank too late and you lose it all; bank too early and you fall behind. Push your luck!",
        )
        private val MINES_TIPS = listOf(
            "The first square is always safe. Numbers show how many bombs touch a square.",
            "An empty square opens all its neighbours at once, which can score a lot.",
            "Hidden power-ups fire when you reveal them. Use the numbers to avoid bombs!",
        )
        private val HILO_TIPS = listOf(
            "Guess whether the next card is higher or lower than the one showing. Aces are high.",
            "Each right guess is worth more than the last: 10, 20, 30, up to 60 points.",
            "The percentages are the odds from the cards still unseen. Pick the bigger one!",
            "A card of the SAME value counts as wrong, so middling cards are risky.",
            "You can Change the face-up card once per game. It's handy when it's a 7, 8 or 9.",
        )
        private val HOLIDAY_TIPS = listOf(
            "Both players see the same question at the same time.",
            "The first correct answer scores 100 plus up to 100 for speed. A slower right answer still earns 40.",
            "A wrong answer costs 25 points, so don't just guess wildly!",
            "After each answer a 'did you know' fact appears: great for learning before you land.",
        )
        private val DRAUGHTS_TIPS = listOf(
            "Move diagonally forward onto an empty dark square.",
            "If you can jump, you MUST jump. Setting up a forced jump is how you win pieces.",
            "Reach the far row to be crowned a king: kings move and jump both ways.",
            "Keep your back row home for a while: it stops the other side getting kings.",
        )
        private val LUDO_TIPS = listOf(
            "Roll a 6 to bring a piece out. A 6 also gives you another go.",
            "Land on a lone opposing piece and it goes back to its base. Stars and start squares are safe.",
            "To get home you must roll the exact number needed, so don't leave it too late.",
        )
        private val MONOPOLY_TIPS = listOf(
            "Buy what you land on when you can: owning a whole colour set lets you build.",
            "Houses must be built evenly across a set. Hotels need four houses first.",
            "Short of cash? Mortgage property for half its price, or sell buildings.",
            "Three doubles in a row sends you to jail. In jail you can pay £50, use a card, or roll a double.",
        )
        private val TETRIS_TIPS = listOf(
            "Move and turn the falling piece to fill gaps. Keep the stack low and flat.",
            "A full row disappears. The more rows at once, the bigger the score.",
            "Clear 2 rows for 2 gift pieces, 3 for 3, and 4 at once (a 'Tetris') sends 5!",
            "Gifts you send first cancel gifts still waiting in your meter. Gold pieces then arrive one at a time in your NEXT queue.",
            "HOLD saves a piece for later, and the ghost outline shows where a piece will land.",
            "There are seven modes: Battle, Score attack, Sprint, Bomb Blitz, Rising Tide, Turbo and Power Surge. Try them all in the lobby!",
            "Clear four lines twice in a row for a back-to-back bonus, or empty the whole board for a perfect clear.",
        )
        private val SEA_TIPS = listOf(
            "When you hit, you fire again, so a hit near a hit is a good bet.",
            "Ships run in straight lines, so line up your shots once you find one.",
            "Each player also has one radar scan and one cluster bomb.",
        )

        fun intro(kind: GameKind): String = when (kind) {
            GameKind.DRAW -> "🎨 Pictionary: one player draws a word, the other guesses it. You are watching a full round."
            GameKind.MINES -> "💣 Minesweeper: take turns revealing squares on a shared minefield. You are Player 1. Watch how the numbers guide the choices."
            GameKind.SEA -> "🚢 Battleships: hide your fleet and sink theirs. You are Player 1."
            GameKind.DROP -> "🔴 Connect 4: take turns dropping discs. The first to connect four wins. You are Player 1."
            GameKind.MEMORY -> "🃏 Pairs: find the matching pairs. You are Player 1."
            GameKind.QUICK -> "⚡ Quick Draw: a reflex duel. Watch the screen and learn when to tap."
            GameKind.DICE -> "🎲 Pig: roll to build points, then bank them before you bust. You are Player 1."
            GameKind.FLIP -> "⚫ Othello: trap the other player's discs to flip them. You are Player 1 (black)."
            GameKind.HILO -> "🎴 Higher or Lower: guess whether each next card is higher or lower, and climb the prize ladder. You are Player 1."
            GameKind.TETRIS -> "🧱 Tetris: falling blocks, side by side. You are Player 1 (the left board). Clear lines and the pieces you blow up are sent to your rival."
            GameKind.DRAUGHTS -> "♟️ Draughts: jump the other player's pieces. Captures are compulsory. You are Player 1 (the bottom pieces)."
            GameKind.LUDO -> "🏠 Ludo: roll a 6 to bring a piece out, race round the board, and get all four home with exact rolls. You are Player 1 (red)."
            GameKind.MONOPOLY -> "🎩 Monopoly (UK): buy streets, collect rent, build houses and hotels. You are Player 1. This is a quick 15-turn demo."
            GameKind.HOLIDAY -> "🏝️ Do you know your Holiday? A quiz about where you're going. You are Player 1; the quickest correct answer wins the most points."
        }

        fun outro(kind: GameKind): String = "🎉 That's ${kind.label}! Tap Play again to watch another game, or go back and try it for real."
    }
}
