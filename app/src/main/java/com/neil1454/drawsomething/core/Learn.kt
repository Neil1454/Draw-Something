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
        private val SEA_TIPS = listOf(
            "When you hit, you fire again, so a hit near a hit is a good bet.",
            "Ships run in straight lines, so line up your shots once you find one.",
            "Each player also has one radar scan and one cluster bomb.",
        )

        fun intro(kind: GameKind): String = when (kind) {
            GameKind.DRAW -> "🎨 Draw Something: one player draws a word, the other guesses it. You are watching a full round."
            GameKind.MINES -> "💣 Bomb Find: take turns revealing squares on a shared minefield. You are Player 1. Watch how the numbers guide the choices."
            GameKind.SEA -> "🚢 Sea Battle: hide your fleet and sink theirs. You are Player 1."
            GameKind.DROP -> "🔴 Drop Four: take turns dropping discs. The first to connect four wins. You are Player 1."
            GameKind.MEMORY -> "🃏 Memory Match: find the matching pairs. You are Player 1."
            GameKind.QUICK -> "⚡ Quick Draw: a reflex duel. Watch the screen and learn when to tap."
            GameKind.DICE -> "🎲 Lucky Dice: roll to build points, then bank them before you bust. You are Player 1."
            GameKind.FLIP -> "⚫ Flip Fight: trap the other player's discs to flip them. You are Player 1 (black)."
        }

        fun outro(kind: GameKind): String = "🎉 That's ${kind.label}! Tap Play again to watch another game, or go back and try it for real."
    }
}
