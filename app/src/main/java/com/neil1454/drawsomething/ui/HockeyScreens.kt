package com.neil1454.drawsomething.ui

import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.tween
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.gestures.awaitEachGesture
import androidx.compose.foundation.gestures.awaitFirstDown
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableLongStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.runtime.withFrameMillis
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.drawscope.DrawScope
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.drawscope.withTransform
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.drawText
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.rememberTextMeasurer
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.neil1454.drawsomething.core.GPhase
import com.neil1454.drawsomething.core.HockeyEngine
import com.neil1454.drawsomething.core.HockeyRules
import com.neil1454.drawsomething.core.HockeyState
import com.neil1454.drawsomething.core.Sound
import com.neil1454.drawsomething.sound.Sfx
import kotlinx.coroutines.delay
import kotlin.math.abs
import kotlin.math.min
import kotlin.math.sin

@Composable
fun HockeyHelp() {
    Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
        Text("A real-time duel. You are always at the bottom of your screen; your rival is at the top.")
        Text("• Air Hockey: slide your finger around the pad under the table to move your mallet round your half. Whack the puck into their goal (the gap at the top) and guard your own.")
        Text("• Pong: slide your finger left and right along the pad to move your paddle. Bounce the ball back; it gets faster with every hit. Miss it and they score.")
        Text("• First to the target score wins. The host picks the game, the target (5, 7 or 10) in the lobby.")
    }
}

private val Mine = Color(0xFF22D3EE)
private val Theirs = Color(0xFFF472B6)

@Composable
fun HockeyScreen(s: HockeyState, engine: HockeyEngine, onPlayAgain: () -> Unit, onBackToGames: () -> Unit, onLeave: () -> Unit) {
    val frameMs = remember { mutableLongStateOf(0L) }
    val stamp = remember { LongArray(1) }
    var ctl by remember { mutableStateOf(Offset(50f, 124f)) }
    var touching by remember { mutableStateOf(false) }
    val measurer = rememberTextMeasurer()
    val banner = remember { Animatable(0f) }
    LaunchedEffect(Unit) { while (true) withFrameMillis { frameMs.longValue = it } }
    LaunchedEffect(s.receivedAt, s.puckX, s.puckY) { stamp[0] = frameMs.longValue }
    LaunchedEffect(s.phase) {
        var sx = -1f; var sy = -1f; var quiet = 0
        while (s.phase == GPhase.PLAYING) {
            delay(30)
            quiet++
            if (abs(ctl.x - sx) > 0.2f || abs(ctl.y - sy) > 0.2f || quiet > 10) { sx = ctl.x; sy = ctl.y; quiet = 0; engine.input(ctl.x, ctl.y) }
        }
    }
    LaunchedEffect(s.hits) { if (s.hits > 0) Sfx.play(Sound.POP) }
    LaunchedEffect(s.walls) { if (s.walls > 0) Sfx.play(Sound.TICK) }
    LaunchedEffect(s.goals) {
        if (s.goals > 0) {
            Sfx.play(if (s.lastScorer == 1) Sound.GOOD else Sound.BAD)
            banner.snapTo(0f); banner.animateTo(1f, tween(260)); delay(900); banner.animateTo(0f, tween(300))
        }
    }

    BoxWithConstraints(Modifier.fillMaxSize().background(Brush.verticalGradient(listOf(Color(0xFF04101F), Color(0xFF0B2545), Color(0xFF13315C))))) {
        val padH = if (s.pong) 74.dp else 150.dp
        val headH = 58.dp
        val availH = maxHeight - headH - padH - 22.dp
        val fieldW = min(availH.value * (100f / 140f), maxWidth.value - 12f).dp
        val fieldH = fieldW * (140f / 100f)
        Column(Modifier.fillMaxSize().padding(top = 8.dp), horizontalAlignment = Alignment.CenterHorizontally) {
            Text(
                if (s.pong) "🏓 PONG" else "🏒 AIR HOCKEY",
                color = Color(0xFFFFD23F), fontWeight = FontWeight.Black, fontSize = 18.sp, letterSpacing = 3.sp,
            )
            Text("First to ${s.target}  ·  you ${s.myScore} – ${s.theirScore} ${engine.theirName}", color = Color.White.copy(alpha = 0.85f), fontSize = 13.sp, modifier = Modifier.padding(bottom = 6.dp))
            Box(Modifier.size(fieldW, fieldH).clip(RoundedCornerShape(14.dp)).border(2.dp, Color.White.copy(alpha = 0.5f), RoundedCornerShape(14.dp))) {
                Canvas(Modifier.fillMaxSize()) {
                    val k = size.width / 100f
                    val age = ((frameMs.longValue - stamp[0]) / 1000f).coerceIn(0f, 0.06f)
                    withTransform({ scale(k, k, Offset.Zero) }) {
                        if (s.pong) drawPongTable() else drawRink()
                        // big faint scores either side of the middle
                        drawScore(measurer, s.theirScore, 36f, k)
                        drawScore(measurer, s.myScore, 104f, k)
                        val bx = s.puckX + s.puckVx * age; val by = s.puckY + s.puckVy * age
                        if (s.pong) {
                            drawPaddle(s.theirX, HockeyRules.PADDLE_Y, Theirs)
                            drawPaddle(s.myX, HockeyRules.H - HockeyRules.PADDLE_Y, Mine)
                            drawBall(bx, by)
                        } else {
                            drawPuck(bx, by)
                            drawMallet(s.theirX, s.theirY, Theirs)
                            drawMallet(s.myX, s.myY, Mine)
                        }
                    }
                    // countdown or "GOAL"
                    if (s.stage == "READY") {
                        val n = (s.readyMs / 700 + 1).coerceIn(1, 3)
                        val t = measurer.measure(if (s.readyMs > 0) "$n" else "GO!", TextStyle(fontSize = (size.width * 0.28f).toSp(), color = Color.White, fontWeight = FontWeight.Black))
                        drawText(t, topLeft = Offset((size.width - t.size.width) / 2f, (size.height - t.size.height) / 2f))
                    }
                    if (banner.value > 0.01f && s.lastScorer != 0) {
                        val txt = if (s.lastScorer == 1) "GOAL!" else "THEY SCORE"
                        val t = measurer.measure(txt, TextStyle(fontSize = (size.width * 0.17f * (0.6f + 0.4f * banner.value)).toSp(), color = (if (s.lastScorer == 1) Color(0xFFFFD23F) else Color(0xFFFCA5A5)).copy(alpha = banner.value), fontWeight = FontWeight.Black))
                        drawText(t, topLeft = Offset((size.width - t.size.width) / 2f, (size.height - t.size.height) / 2f))
                    }
                }
            }
            Box(
                Modifier.padding(top = 10.dp).size(fieldW, padH).clip(RoundedCornerShape(18.dp))
                    .background(Brush.verticalGradient(listOf(Mine.copy(alpha = if (touching) 0.34f else 0.18f), Color(0xFF0B1030))))
                    .border(if (touching) 3.dp else 2.dp, Mine.copy(alpha = if (touching) 0.95f else 0.55f), RoundedCornerShape(18.dp))
                    .pointerInput(s.pong) {
                        awaitEachGesture {
                            val down = awaitFirstDown(requireUnconsumed = false)
                            touching = true
                            fun at(p: Offset) = Offset(
                                (p.x / size.width).coerceIn(0f, 1f) * 100f,
                                if (s.pong) 124f else 70f + (p.y / size.height).coerceIn(0f, 1f) * 70f,
                            )
                            ctl = at(down.position)
                            do {
                                val ev = awaitPointerEvent()
                                val ch = ev.changes.firstOrNull() ?: break
                                ctl = at(ch.position)
                                ch.consume()
                            } while (ev.changes.any { it.pressed })
                            touching = false
                        }
                    },
            ) {
                Canvas(Modifier.fillMaxSize()) {
                    val x = (s.myX / 100f).coerceIn(0f, 1f) * size.width
                    val y = if (s.pong) size.height * 0.5f else ((s.myY - 70f) / 70f).coerceIn(0f, 1f) * size.height
                    for (i in 0..24) drawLine(Color.White.copy(alpha = if (i % 6 == 0) 0.28f else 0.10f), Offset(size.width * i / 24f, size.height * 0.9f), Offset(size.width * i / 24f, size.height * (if (i % 6 == 0) 0.74f else 0.82f)), 2f)
                    drawCircle(Mine.copy(alpha = 0.35f), size.height * 0.20f, Offset(x, y))
                    drawCircle(Mine, size.height * 0.11f, Offset(x, y))
                }
                Text(if (s.pong) "Slide your finger here" else "Slide your finger here to move your mallet", color = Color.White.copy(alpha = 0.55f), fontSize = 11.sp, modifier = Modifier.align(Alignment.TopCenter).padding(top = 4.dp))
            }
        }
        if (s.phase == GPhase.OVER) {
            EndOverlay(
                title = when (s.result) { 1 -> "🏆 YOU WIN!"; 2 -> "🏆 ${engine.theirName.uppercase()} WINS!"; else -> "🤝 IT'S A DRAW!" },
                subtitle = "${s.myScore} – ${s.theirScore}",
                win = s.result == 1, isHost = engine.isHost, theirName = engine.theirName,
                playAgainLabel = "Play again", colors = listOf(Color(0xFF0B2545), Color(0xFF22D3EE)),
                onPlayAgain = onPlayAgain, onBackToGames = onBackToGames, onLeave = onLeave,
                how = if (s.result == 3) "Neck and neck when time ran out." else "First to ${s.target}.",
            )
        }
    }
}

private fun DrawScope.drawScore(m: androidx.compose.ui.text.TextMeasurer, v: Int, y: Float, @Suppress("UNUSED_PARAMETER") k: Float) {
    // this is drawn inside the scaled field, so sizes are in field units
    val t = m.measure("$v", TextStyle(fontSize = 24f.toSp(), color = Color.White.copy(alpha = 0.13f), fontWeight = FontWeight.Black))
    drawText(t, topLeft = Offset(8f, y - t.size.height / 2f))
}

private fun DrawScope.drawRink() {
    drawRect(Brush.verticalGradient(listOf(Color(0xFFDDF3FF), Color(0xFFBFE5FA), Color(0xFFDDF3FF))), Offset.Zero, Size(HockeyRules.W, HockeyRules.H))
    // lines on the ice
    drawLine(Color(0xFFEF4444).copy(alpha = 0.7f), Offset(0f, 70f), Offset(100f, 70f), 1.2f)
    drawCircle(Color(0xFF3B82F6).copy(alpha = 0.6f), 14f, Offset(50f, 70f), style = Stroke(1f))
    drawCircle(Color(0xFF3B82F6).copy(alpha = 0.6f), 1.6f, Offset(50f, 70f))
    for (top in booleanArrayOf(true, false)) {
        val y = if (top) 0f else HockeyRules.H
        drawArc(Color(0xFFEF4444).copy(alpha = 0.55f), if (top) 0f else 180f, 180f, false, Offset(50f - 24f, y - 24f), Size(48f, 48f), style = Stroke(1f))
        // the goal slot
        drawRect(Color(0xFF111827), Offset(50f - HockeyRules.GOAL_HALF, if (top) -2f else HockeyRules.H - 1f), Size(HockeyRules.GOAL_HALF * 2f, 3f))
        drawRect(if (top) Theirs.copy(alpha = 0.9f) else Mine.copy(alpha = 0.9f), Offset(50f - HockeyRules.GOAL_HALF, if (top) 0f else HockeyRules.H - 1.6f), Size(HockeyRules.GOAL_HALF * 2f, 1.6f))
    }
}

private fun DrawScope.drawPongTable() {
    drawRect(Brush.verticalGradient(listOf(Color(0xFF020617), Color(0xFF0F172A))), Offset.Zero, Size(HockeyRules.W, HockeyRules.H))
    var y = 4f
    while (y < HockeyRules.H) { drawRect(Color.White.copy(alpha = 0.25f), Offset(49f, y), Size(2f, 5f)); y += 10f }
    drawRect(Color.White.copy(alpha = 0.18f), Offset(0f, 0f), Size(100f, 1f)); drawRect(Color.White.copy(alpha = 0.18f), Offset(0f, HockeyRules.H - 1f), Size(100f, 1f))
}

private fun DrawScope.drawPuck(x: Float, y: Float) {
    drawCircle(Color.Black.copy(alpha = 0.25f), HockeyRules.PUCK_R + 0.8f, Offset(x + 0.8f, y + 1.1f))
    drawCircle(Brush.radialGradient(listOf(Color(0xFF475569), Color(0xFF0F172A)), Offset(x - 1f, y - 1f), HockeyRules.PUCK_R * 1.3f), HockeyRules.PUCK_R, Offset(x, y))
    drawCircle(Color(0xFFFACC15), HockeyRules.PUCK_R, Offset(x, y), style = Stroke(0.8f))
}

private fun DrawScope.drawBall(x: Float, y: Float) {
    drawCircle(Color.White.copy(alpha = 0.25f), HockeyRules.BALL_R * 2.2f, Offset(x, y))
    drawCircle(Color.White, HockeyRules.BALL_R, Offset(x, y))
}

private fun DrawScope.drawPaddle(x: Float, y: Float, col: Color) {
    drawRoundRect(col.copy(alpha = 0.3f), Offset(x - HockeyRules.PADDLE_HW - 1.5f, y - 3.6f), Size(HockeyRules.PADDLE_HW * 2f + 3f, 7.2f), CornerRadius(3.6f))
    drawRoundRect(col, Offset(x - HockeyRules.PADDLE_HW, y - 1.6f), Size(HockeyRules.PADDLE_HW * 2f, 3.2f), CornerRadius(1.6f))
    drawRoundRect(Color.White.copy(alpha = 0.6f), Offset(x - HockeyRules.PADDLE_HW + 1.5f, y - 1.2f), Size(HockeyRules.PADDLE_HW * 2f - 3f, 0.8f), CornerRadius(0.4f))
}

private fun DrawScope.drawMallet(x: Float, y: Float, col: Color) {
    val r = HockeyRules.MALLET_R
    drawCircle(Color.Black.copy(alpha = 0.25f), r, Offset(x + 1.2f, y + 1.8f))
    drawCircle(Brush.radialGradient(listOf(Color.White, col, col.copy(alpha = 0.7f)), Offset(x - r * 0.3f, y - r * 0.3f), r * 1.4f), r, Offset(x, y))
    drawCircle(Color.Black.copy(alpha = 0.35f), r, Offset(x, y), style = Stroke(0.8f))
    drawCircle(Color.White.copy(alpha = 0.55f), r * 0.45f, Offset(x, y), style = Stroke(0.9f))
    drawCircle(col, r * 0.28f, Offset(x, y))
}

internal fun DrawScope.hockeyIcon() {
    drawLine(Color.White.copy(alpha = 0.9f), Offset(50f, 10f), Offset(50f, 90f), 3f, StrokeCap.Round)
    drawCircle(Color.White.copy(alpha = 0.9f), 16f, Offset(50f, 50f), style = Stroke(3f))
    drawCircle(Color(0xFF22D3EE), 14f, Offset(50f, 78f)); drawCircle(Color.White, 5f, Offset(50f, 78f))
    drawCircle(Color(0xFFF472B6), 14f, Offset(50f, 22f)); drawCircle(Color.White, 5f, Offset(50f, 22f))
    drawCircle(Color(0xFF111827), 6f, Offset(66f, 50f)); drawCircle(Color(0xFFFACC15), 6f, Offset(66f, 50f), style = Stroke(2f))
}
