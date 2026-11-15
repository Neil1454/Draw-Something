package com.neil1454.drawsomething.ui

import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.LinearEasing
import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.tween
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.drawscope.DrawScope
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.drawText
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.rememberTextMeasurer
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.neil1454.drawsomething.core.GPhase
import com.neil1454.drawsomething.core.KalahEngine
import com.neil1454.drawsomething.core.KalahState
import com.neil1454.drawsomething.core.Sound
import com.neil1454.drawsomething.sound.Sfx
import kotlinx.coroutines.delay
import kotlin.math.PI
import kotlin.math.cos
import kotlin.math.sin
import kotlin.math.sqrt

@Composable
fun KalahHelp() {
    Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
        Text("A game that is thousands of years old. You each have six pits and a store. Pick a pit and sow its seeds, one in each pit, round the board.")
        Text("• Tap one of YOUR pits (the bottom row) to pick up its seeds. They go round counter-clockwise, through your store but skipping your rival's.")
        Text("• Last seed in your store? Take another turn!")
        Text("• Last seed in an empty pit of yours with seeds opposite? You capture them all into your store.")
        Text("• When one side runs out of seeds, the other player keeps their remaining seeds. The bigger store wins.")
    }
}

private val Wood1 = Color(0xFF8B5A2B)
private val Wood2 = Color(0xFF5C3A1A)
private val SeedColours = listOf(Color(0xFFEF4444), Color(0xFF3B82F6), Color(0xFFFACC15), Color(0xFF22C55E), Color(0xFFA855F7), Color(0xFFF97316), Color(0xFF14B8A6))

private class Geo(val w: Float, val h: Float) {
    val storeW = w * 0.14f
    val pad = w * 0.02f
    val cell = (w - 2 * storeW - 2 * pad) / 6f
    val pitR = minOf(cell * 0.43f, h * 0.2f)
    val topY = h * 0.3f
    val botY = h * 0.7f
    fun center(v: Int): Offset = when (v) {
        in 0..5 -> Offset(storeW + pad + (v + 0.5f) * cell, botY)
        6 -> Offset(w - storeW / 2f, h / 2f)
        13 -> Offset(storeW / 2f, h / 2f)
        else -> Offset(storeW + pad + ((12 - v) + 0.5f) * cell, topY)
    }
}

private fun DrawScope.drawSeeds(c: Offset, r: Float, count: Int, seedKey: Int, wobble: Float = 0f) {
    val n = minOf(count, 16)
    for (k in 0 until n) {
        val ang = k * 2.4f + seedKey
        val rad = r * 0.68f * sqrt((k + 0.5f) / maxOf(n, 3))
        val p = Offset(c.x + cos(ang) * rad + wobble, c.y + sin(ang) * rad)
        val col = SeedColours[(k + seedKey) % SeedColours.size]
        drawCircle(Color.Black.copy(alpha = 0.35f), r * 0.17f, Offset(p.x + 1.2f, p.y + 1.6f))
        drawCircle(col, r * 0.17f, p)
        drawCircle(Color.White.copy(alpha = 0.55f), r * 0.06f, Offset(p.x - r * 0.05f, p.y - r * 0.06f))
    }
}

@Composable
fun KalahScreen(
    s: KalahState,
    engine: KalahEngine,
    onPlayAgain: () -> Unit,
    onBackToGames: () -> Unit,
    onLeave: () -> Unit,
) {
    val measurer = rememberTextMeasurer()
    var display by remember { mutableStateOf(s.board) }
    var lastBoard by remember { mutableStateOf(s.board) }
    var hot by remember { mutableStateOf(-1) }       // the pit a seed has just dropped into
    var flash by remember { mutableStateOf(-1) }     // a pit that was captured
    var busy by remember { mutableStateOf(false) }
    var banner by remember { mutableStateOf("") }
    val fly = remember { Animatable(1f) }
    var flyFrom by remember { mutableStateOf(-1) }
    var flyTo by remember { mutableStateOf(-1) }
    val pulse by rememberInfiniteTransition(label = "kalah").animateFloat(0.4f, 1f, infiniteRepeatable(tween(800), RepeatMode.Reverse), label = "kalahPulse")

    // sow the seeds one by one whenever a move arrives
    LaunchedEffect(s.moves) {
        if (s.moves == 0 || s.lastPath.isEmpty() || s.lastPit !in 0..13) {
            display = s.board; lastBoard = s.board; return@LaunchedEffect
        }
        busy = true
        val b = lastBoard.toMutableList()
        val seeds = b[s.lastPit]
        if (seeds != s.lastPath.size) { display = s.board; lastBoard = s.board; busy = false; return@LaunchedEffect }
        b[s.lastPit] = 0
        display = b.toList()
        Sfx.play(Sound.TICK)
        delay(260)
        var from = s.lastPit
        for (idx in s.lastPath) {
            flyFrom = from; flyTo = idx
            fly.snapTo(0f)
            fly.animateTo(1f, tween(190, easing = LinearEasing))
            b[idx] = b[idx] + 1
            display = b.toList()
            hot = idx
            Sfx.play(if (idx == 6 || idx == 13) Sound.COIN else Sound.DROP)
            from = idx
            delay(70)
        }
        flyFrom = -1; hot = -1
        if (s.capturedCount > 0 && s.capturedFrom >= 0) {
            flash = s.capturedFrom
            banner = if (s.lastByMe) "🎯 You captured ${s.capturedCount}!" else "😬 ${engine.theirName} captured ${s.capturedCount}!"
            Sfx.play(Sound.WIN)
            delay(650)
            flash = -1
        } else if (s.lastExtra) {
            banner = if (s.lastByMe) "✨ Extra turn!" else "${engine.theirName} goes again"
            Sfx.play(Sound.CHIME)
        }
        display = s.board; lastBoard = s.board
        busy = false
        delay(1200)
        banner = ""
    }

    Box(Modifier.fillMaxSize().background(Brush.verticalGradient(listOf(Color(0xFF2B1A0C), Color(0xFF4A2C14), Color(0xFF1B1008))))) {
        Column(Modifier.fillMaxSize().padding(10.dp), horizontalAlignment = Alignment.CenterHorizontally, verticalArrangement = Arrangement.Center) {
            Text("🌰 MANCALA", color = Color(0xFFFFD23F), fontWeight = FontWeight.Black, fontSize = 20.sp, letterSpacing = 3.sp)
            Text(
                when {
                    s.phase == GPhase.OVER -> "Game over"
                    busy -> "Sowing…"
                    s.myTurn -> "👉 Your turn: tap one of your pits"
                    else -> "⏳ ${engine.theirName} is thinking…"
                },
                color = Color.White, fontWeight = FontWeight.Bold, modifier = Modifier.padding(vertical = 6.dp),
            )
            Text("${engine.theirName}  ·  ${s.theirStore}", color = Color(0xFFFDBA74), fontWeight = FontWeight.Bold, modifier = Modifier.padding(bottom = 4.dp))
            Canvas(
                Modifier.fillMaxWidth().aspectRatio(2.1f)
                    .pointerInput(s.myTurn, busy, s.moves) {
                        detectTapGestures { tap ->
                            if (!s.myTurn || busy || s.phase != GPhase.PLAYING) return@detectTapGestures
                            val g = Geo(size.width.toFloat(), size.height.toFloat())
                            for (v in 0..5) {
                                val c = g.center(v)
                                if ((tap - c).getDistance() <= g.pitR * 1.25f && s.board[v] > 0) { Sfx.play(Sound.TAP); engine.move(v); return@detectTapGestures }
                            }
                        }
                    },
            ) {
                val g = Geo(size.width, size.height)
                drawRoundRect(Brush.verticalGradient(listOf(Wood1, Wood2)), Offset.Zero, size, CornerRadius(size.height * 0.2f))
                drawRoundRect(Color.Black.copy(alpha = 0.4f), Offset.Zero, size, CornerRadius(size.height * 0.2f), style = Stroke(5f))
                // the two stores
                for (v in listOf(6, 13)) {
                    val c = g.center(v)
                    val sw = g.storeW * 0.8f
                    val sh = size.height * 0.78f
                    drawRoundRect(Color(0xFF2A1708), Offset(c.x - sw / 2, c.y - sh / 2), Size(sw, sh), CornerRadius(sw / 2))
                    drawRoundRect(Color(0xFFFFD23F).copy(alpha = if (hot == v) 0.9f else 0.18f), Offset(c.x - sw / 2, c.y - sh / 2), Size(sw, sh), CornerRadius(sw / 2), style = Stroke(3f))
                    val n = display[v]
                    for (k in 0 until minOf(n, 30)) {
                        val col = SeedColours[(k + v) % SeedColours.size]
                        val p = Offset(c.x + cos(k * 2.4f) * sw * 0.26f * sqrt((k + 0.5f) / 30f * 4f).coerceAtMost(1.4f), c.y + sh * 0.4f - (k / 30f) * sh * 0.75f + sin(k * 1.7f) * 6f)
                        drawCircle(col, g.pitR * 0.17f, p)
                    }
                    val t = measurer.measure("$n", TextStyle(fontSize = (g.pitR * 0.9f).toSp(), color = Color.White, fontWeight = FontWeight.Black))
                    drawText(t, topLeft = Offset(c.x - t.size.width / 2f, c.y - t.size.height / 2f))
                }
                // the twelve pits
                for (v in (0..5) + (7..12)) {
                    val c = g.center(v)
                    val mine = v <= 5
                    drawCircle(Brush.radialGradient(listOf(Color(0xFF1B0F05), Color(0xFF3A210E)), c, g.pitR), g.pitR, c)
                    drawCircle(Color.Black.copy(alpha = 0.5f), g.pitR, c, style = Stroke(3f))
                    if (mine && s.myTurn && !busy && display[v] > 0) drawCircle(Color(0xFFFFD23F).copy(alpha = 0.25f + 0.5f * pulse), g.pitR * 1.08f, c, style = Stroke(4f))
                    if (hot == v) drawCircle(Color.White.copy(alpha = 0.85f), g.pitR * 1.12f, c, style = Stroke(4f))
                    if (flash == v) drawCircle(Color(0xFFEF4444).copy(alpha = 0.55f), g.pitR * 1.3f, c)
                    drawSeeds(c, g.pitR, display[v], v)
                    val txt = measurer.measure("${display[v]}", TextStyle(fontSize = (g.pitR * 0.55f).toSp(), color = Color(0xFFFDE68A), fontWeight = FontWeight.Bold))
                    val ly = if (mine) c.y + g.pitR + txt.size.height * 0.15f else c.y - g.pitR - txt.size.height * 1.05f
                    drawText(txt, topLeft = Offset(c.x - txt.size.width / 2f, ly))
                }
                // the seed in flight
                if (flyFrom >= 0 && flyTo >= 0) {
                    val a = g.center(flyFrom); val b = g.center(flyTo)
                    val p = fly.value
                    val pos = Offset(a.x + (b.x - a.x) * p, a.y + (b.y - a.y) * p - sin(p * PI.toFloat()) * g.pitR * 0.9f)
                    drawCircle(Color.Black.copy(alpha = 0.3f), g.pitR * 0.22f, Offset(pos.x + 3f, pos.y + 8f))
                    drawCircle(Color(0xFFFFD23F), g.pitR * 0.22f, pos)
                    drawCircle(Color.White, g.pitR * 0.22f, pos, style = Stroke(2f))
                }
            }
            Text("You  ·  ${s.myStore}", color = Color(0xFF7DD3FC), fontWeight = FontWeight.Bold, modifier = Modifier.padding(top = 4.dp))
            if (banner.isNotEmpty()) Text(banner, color = Color(0xFFFFD23F), fontWeight = FontWeight.Black, fontSize = 20.sp, modifier = Modifier.padding(top = 10.dp))
        }
        if (s.phase == GPhase.OVER && !busy) {
            EndOverlay(
                title = when (s.result) { 1 -> "🏆 YOU WIN!"; 2 -> "🏆 ${engine.theirName.uppercase()} WINS!"; else -> "🤝 IT'S A DRAW!" },
                subtitle = "Stores: you ${s.myStore} · ${engine.theirName} ${s.theirStore}",
                win = s.result == 1, isHost = engine.isHost, theirName = engine.theirName,
                playAgainLabel = "Play again", colors = listOf(Color(0xFF7C4A1E), Color(0xFFE0A33A)),
                onPlayAgain = onPlayAgain, onBackToGames = onBackToGames, onLeave = onLeave,
                how = "When one side ran out of seeds, the other player kept what was left on their side. ${s.myStore} seeds to ${s.theirStore}.",
            )
        }
    }
}
