package com.neil1454.drawsomething.ui

import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.tween
import androidx.compose.animation.core.LinearEasing
import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.drawscope.DrawScope
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.neil1454.drawsomething.core.GPhase
import com.neil1454.drawsomething.core.NoughtsEngine
import com.neil1454.drawsomething.core.NoughtsState
import com.neil1454.drawsomething.core.Sound
import com.neil1454.drawsomething.sound.Sfx
import androidx.compose.runtime.LaunchedEffect
import kotlin.math.sin

@Composable
fun NoughtsHelp() {
    Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
        Text("Take turns putting your mark on the grid. The cross (✕) always goes first, and who goes first swaps every game.")
        Text("• Classic: three of your marks in a row (up, across or diagonally) wins. If the grid fills up, it's a draw. Played right, nobody can ever win!")
        Text("• Ultimate: nine small grids make one big grid. Wherever you play inside a small grid decides which small grid your rival must play in next. Win a small grid to claim it, and claim three in a row on the big grid to win. If you are sent to a grid that is already finished, you can play anywhere.")
        Text("• You can play a single game, or a best of three or five.")
    }
}

private val MineCol = Color(0xFF22D3EE)
private val TheirCol = Color(0xFFF472B6)

/** A cross or a nought, drawing itself in as [p] goes from 0 to 1. */
private fun DrawScope.drawMark(cross: Boolean, c: Offset, r: Float, col: Color, p: Float, width: Float) {
    if (cross) {
        val a = (p * 2f).coerceIn(0f, 1f); val b = (p * 2f - 1f).coerceIn(0f, 1f)
        drawLine(col, Offset(c.x - r, c.y - r), Offset(c.x - r + 2 * r * a, c.y - r + 2 * r * a), width, StrokeCap.Round)
        if (b > 0f) drawLine(col, Offset(c.x + r, c.y - r), Offset(c.x + r - 2 * r * b, c.y - r + 2 * r * b), width, StrokeCap.Round)
    } else {
        drawArc(col, -90f, 360f * p, false, Offset(c.x - r, c.y - r), Size(2 * r, 2 * r), style = Stroke(width, cap = StrokeCap.Round))
    }
}

@Composable
fun NoughtsScreen(s: NoughtsState, engine: NoughtsEngine, onPlayAgain: () -> Unit, onBackToGames: () -> Unit, onLeave: () -> Unit) {
    // the newest mark draws itself in
    val drawInAnim = remember { Animatable(1f) }
    LaunchedEffect(s.moves) { drawInAnim.snapTo(0f); drawInAnim.animateTo(1f, tween(380, easing = LinearEasing)) }
    val drawIn = drawInAnim.value
    val pulse by rememberInfiniteTransition(label = "pulse").animateFloat(0f, 1f, infiniteRepeatable(tween(900), RepeatMode.Reverse), label = "pv")
    LaunchedEffect(s.moves) { if (s.moves > 0) Sfx.play(Sound.TAP) }
    LaunchedEffect(s.roundResult, s.round) { if (s.roundResult == 1) Sfx.play(Sound.GOOD) else if (s.roundResult == 2) Sfx.play(Sound.BAD) }
    val myCross = s.iAmCross
    val n = if (s.ultimate) 9 else 3

    Box(Modifier.fillMaxSize().background(Brush.verticalGradient(listOf(Color(0xFF0B1220), Color(0xFF1E1B4B), Color(0xFF312E81))))) {
        Column(Modifier.fillMaxSize().padding(horizontal = 14.dp, vertical = 10.dp), horizontalAlignment = Alignment.CenterHorizontally, verticalArrangement = Arrangement.Center) {
            Text(if (s.ultimate) "⭕ ULTIMATE NOUGHTS & CROSSES ❌" else "❌ NOUGHTS & CROSSES ⭕", color = Color(0xFFFFD23F), fontWeight = FontWeight.Black, fontSize = 17.sp, letterSpacing = 1.sp)
            if (s.inSeries) Text("Game ${s.round} of ${s.rounds}  ·  You ${s.myWins} – ${s.theirWins} ${engine.theirName}" + if (s.draws > 0) "  ·  ${s.draws} drawn" else "", color = Color.White.copy(alpha = 0.85f), fontSize = 13.sp)
            Text(
                when {
                    s.phase == GPhase.OVER -> "Finished"
                    s.roundResult == 1 -> "🎉 You won that game!"
                    s.roundResult == 2 -> "${engine.theirName} won that game"
                    s.roundResult == 3 -> "🤝 A draw. Next game coming up…"
                    s.myTurn && s.ultimate && s.forced >= 0 -> "👉 Your turn: play in the glowing grid"
                    s.myTurn && s.ultimate -> "👉 Your turn: play in any open grid"
                    s.myTurn -> "👉 Your turn"
                    else -> "⏳ ${engine.theirName} is thinking…"
                },
                color = Color.White, fontWeight = FontWeight.Bold, modifier = Modifier.padding(vertical = 8.dp),
            )
            Text("You are ${if (myCross) "✕" else "◯"}  ·  ${engine.theirName} is ${if (myCross) "◯" else "✕"}", color = Color.White.copy(alpha = 0.7f), fontSize = 12.sp, modifier = Modifier.padding(bottom = 8.dp))
            Canvas(
                Modifier.fillMaxWidth().aspectRatio(1f).pointerInput(s.myTurn, s.moves, s.roundResult, s.ultimate, s.forced) {
                    detectTapGestures { tap ->
                        if (!s.myTurn || s.phase != GPhase.PLAYING || s.roundResult != 0) return@detectTapGestures
                        val side = size.width.toFloat()
                        val cell = side / n
                        val col = (tap.x / cell).toInt().coerceIn(0, n - 1)
                        val row = (tap.y / cell).toInt().coerceIn(0, n - 1)
                        val index = if (s.ultimate) ((row / 3) * 3 + col / 3) * 9 + (row % 3) * 3 + col % 3 else row * 3 + col
                        if (s.board[index] == 0) engine.play(index)
                    }
                },
            ) {
                val side = size.width
                val cell = side / n
                drawRoundRect(Color(0xFF0F172A), Offset.Zero, size, CornerRadius(24f))
                // position of an index on the screen
                fun centre(index: Int): Offset = if (s.ultimate) {
                    val sub = index / 9; val sq = index % 9
                    Offset(((sub % 3) * 3 + sq % 3 + 0.5f) * cell, ((sub / 3) * 3 + sq / 3 + 0.5f) * cell)
                } else Offset((index % 3 + 0.5f) * cell, (index / 3 + 0.5f) * cell)
                // which squares can be played right now
                fun playable(index: Int): Boolean {
                    if (!s.myTurn || s.roundResult != 0 || s.board[index] != 0) return false
                    if (!s.ultimate) return true
                    val sub = index / 9
                    return s.sub[sub] == 0 && (s.forced < 0 || s.forced == sub)
                }
                if (s.ultimate) {
                    // the glow on the grid you must use
                    for (sub in 0 until 9) {
                        val x = (sub % 3) * 3 * cell; val y = (sub / 3) * 3 * cell
                        val open = s.sub[sub] == 0
                        if (s.myTurn && open && (s.forced < 0 || s.forced == sub) && s.roundResult == 0) {
                            drawRoundRect(Color(0xFFFFD23F).copy(alpha = 0.10f + 0.12f * pulse), Offset(x + 3f, y + 3f), Size(3 * cell - 6f, 3 * cell - 6f), CornerRadius(14f))
                            drawRoundRect(Color(0xFFFFD23F).copy(alpha = 0.5f + 0.4f * pulse), Offset(x + 3f, y + 3f), Size(3 * cell - 6f, 3 * cell - 6f), CornerRadius(14f), style = Stroke(4f))
                        }
                    }
                }
                // grid lines
                for (k in 1 until n) {
                    val thick = s.ultimate && k % 3 == 0
                    val w = if (thick) 6f else if (s.ultimate) 1.6f else 7f
                    val col = if (thick) Color(0xFFA5B4FC) else if (s.ultimate) Color(0xFF475569) else Color(0xFFA5B4FC)
                    drawLine(col, Offset(k * cell, 0f), Offset(k * cell, side), w, StrokeCap.Round)
                    drawLine(col, Offset(0f, k * cell), Offset(side, k * cell), w, StrokeCap.Round)
                }
                // the marks
                for (i in s.board.indices) {
                    val v = s.board[i]
                    val c = centre(i)
                    if (v == 0) {
                        if (playable(i)) drawCircle(Color.White.copy(alpha = 0.07f + 0.06f * pulse), cell * 0.12f, c)
                        continue
                    }
                    val mine = v == 1
                    val cross = if (mine) myCross else !myCross
                    val p = if (i == s.lastMove) drawIn else 1f
                    drawMark(cross, c, cell * (if (s.ultimate) 0.27f else 0.27f), if (mine) MineCol else TheirCol, p, if (s.ultimate) cell * 0.09f else cell * 0.1f)
                }
                // won small grids get a big mark over them
                if (s.ultimate) for (sub in 0 until 9) {
                    val r = s.sub[sub]
                    if (r == 0) continue
                    val x = (sub % 3) * 3 * cell; val y = (sub / 3) * 3 * cell
                    if (r == 3) { drawRoundRect(Color.Black.copy(alpha = 0.35f), Offset(x + 3f, y + 3f), Size(3 * cell - 6f, 3 * cell - 6f), CornerRadius(14f)); continue }
                    val mine = r == 1
                    val cross = if (mine) myCross else !myCross
                    drawRoundRect((if (mine) MineCol else TheirCol).copy(alpha = 0.18f), Offset(x + 3f, y + 3f), Size(3 * cell - 6f, 3 * cell - 6f), CornerRadius(14f))
                    drawRoundRect(Color(0xFF0F172A).copy(alpha = 0.55f), Offset(x + 3f, y + 3f), Size(3 * cell - 6f, 3 * cell - 6f), CornerRadius(14f))
                    drawMark(cross, Offset(x + 1.5f * cell, y + 1.5f * cell), cell * 1.05f, if (mine) MineCol else TheirCol, 1f, cell * 0.28f)
                }
                // the winning line
                if (s.winLine.size == 3) {
                    val a = s.winLine.first(); val b = s.winLine.last()
                    fun spot(k: Int): Offset = if (s.ultimate) Offset(((k % 3) * 3 + 1.5f) * cell, ((k / 3) * 3 + 1.5f) * cell) else centre(k)
                    drawLine(Color(0xFFFFD23F), spot(a), spot(b), cell * (if (s.ultimate) 0.22f else 0.12f), StrokeCap.Round)
                    drawLine(Color.White.copy(alpha = 0.7f), spot(a), spot(b), cell * (if (s.ultimate) 0.07f else 0.04f), StrokeCap.Round)
                }
            }
        }
        if (s.phase == GPhase.OVER) {
            EndOverlay(
                title = when (s.result) { 1 -> "🏆 YOU WIN!"; 2 -> "🏆 ${engine.theirName.uppercase()} WINS!"; else -> "🤝 IT'S A DRAW!" },
                subtitle = if (s.inSeries) "Games won: you ${s.myWins} · ${engine.theirName} ${s.theirWins}" else if (s.ultimate) "A big-grid battle" else "Three in a row",
                win = s.result == 1, isHost = engine.isHost, theirName = engine.theirName,
                playAgainLabel = "Play again", colors = listOf(Color(0xFF312E81), Color(0xFFEC4899)),
                onPlayAgain = onPlayAgain, onBackToGames = onBackToGames, onLeave = onLeave,
                how = when {
                    s.result == 3 -> "Nobody could get ahead. Evenly matched!"
                    s.inSeries -> "Won the series ${if (s.result == 1) s.myWins else s.theirWins} games to ${if (s.result == 1) s.theirWins else s.myWins}."
                    s.ultimate -> "Won the big grid."
                    else -> "Got three in a row."
                },
            )
        }
    }
}

internal fun DrawScope.noughtsIcon() {
    val line = Color.White.copy(alpha = 0.95f)
    for (k in intArrayOf(1, 2)) {
        drawLine(line, Offset(100f * k / 3f, 12f), Offset(100f * k / 3f, 88f), 4f, StrokeCap.Round)
        drawLine(line, Offset(12f, 100f * k / 3f), Offset(88f, 100f * k / 3f), 4f, StrokeCap.Round)
    }
    drawMark(true, Offset(33f / 2f + 6f, 33f / 2f + 6f), 8f, Color(0xFF22D3EE), 1f, 5f)
    drawMark(false, Offset(50f, 50f), 8f, Color(0xFFF472B6), 1f, 5f)
    drawMark(true, Offset(83f - 6f, 83f - 6f), 8f, Color(0xFF22D3EE), 1f, 5f)
}
