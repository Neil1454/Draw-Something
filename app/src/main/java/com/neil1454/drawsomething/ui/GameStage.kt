package com.neil1454.drawsomething.ui

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.mutableLongStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.withFrameMillis
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.drawscope.DrawScope
import androidx.compose.ui.graphics.drawscope.Stroke
import com.neil1454.drawsomething.core.GameKind
import kotlin.math.PI
import kotlin.math.cos
import kotlin.math.sin

// ====================================================================================
// Every game gets a place of its own to be played in, instead of all sharing one purple background: dice on a green felt table under a lamp,
// draughts on a wooden table, battleships out at sea, the quiz in a television studio, quick draw in a neon arcade, and so on.
// Each stage is drawn in code and gently animated. The games that already paint their own scene (the arcade games, Tetris, Monopoly, Deal or No
// Deal, the slot machine...) simply cover it.
// ====================================================================================

private enum class Scene { FELT_GREEN, FELT_RED, WOOD, SEA, RADAR, NEON, PLAYROOM, ARCADE_BLUE, BEACH, STUDIO, SKETCH, NONE }

private fun GameKind.scene(): Scene = when (this) {
    GameKind.DICE -> Scene.FELT_GREEN
    GameKind.HILO -> Scene.FELT_RED
    GameKind.DRAUGHTS, GameKind.FLIP, GameKind.LUDO -> Scene.WOOD
    GameKind.SEA -> Scene.SEA
    GameKind.MINES -> Scene.RADAR
    GameKind.QUICK -> Scene.NEON
    GameKind.MEMORY -> Scene.PLAYROOM
    GameKind.DROP -> Scene.ARCADE_BLUE
    GameKind.HOLIDAY -> Scene.BEACH
    GameKind.TRIVIA -> Scene.STUDIO
    GameKind.DRAW -> Scene.SKETCH
    else -> Scene.NONE
}

/** The backdrop for [kind], filling the screen behind the game. Draws nothing for games that bring their own scene. */
@Composable
fun GameStage(kind: GameKind) {
    val scene = kind.scene()
    if (scene == Scene.NONE) return
    val clock = remember { mutableLongStateOf(0L) }
    LaunchedEffect(Unit) { while (true) withFrameMillis { clock.longValue = it } }
    Canvas(Modifier.fillMaxSize()) {
        val t = clock.longValue / 1000f
        when (scene) {
            Scene.FELT_GREEN -> felt(t, Color(0xFF0E5C36), Color(0xFF06301C))
            Scene.FELT_RED -> felt(t, Color(0xFF7A1528), Color(0xFF3A0812))
            Scene.WOOD -> wood(t)
            Scene.SEA -> sea(t)
            Scene.RADAR -> radar(t)
            Scene.NEON -> neon(t)
            Scene.PLAYROOM -> playroom(t)
            Scene.ARCADE_BLUE -> arcadeBlue(t)
            Scene.BEACH -> beach(t)
            Scene.STUDIO -> studio(t)
            Scene.SKETCH -> sketch(t)
            Scene.NONE -> Unit
        }
    }
}

private fun DrawScope.vignette(strength: Float = 0.6f) {
    drawRect(Brush.radialGradient(listOf(Color.Transparent, Color.Black.copy(alpha = strength)), Offset(size.width / 2f, size.height * 0.45f), size.maxDimension * 0.75f))
}

/** A card table: baize under a hanging lamp, with a stitched gold line round the edge and dust drifting in the light. */
private fun DrawScope.felt(t: Float, mid: Color, edge: Color) {
    drawRect(Brush.radialGradient(listOf(mid, edge), Offset(size.width / 2f, size.height * 0.42f), size.maxDimension * 0.8f))
    // the weave of the cloth
    var y = 0f
    while (y < size.height) { drawLine(Color.White.copy(alpha = 0.018f), Offset(0f, y), Offset(size.width, y), 1f); y += 5f }
    // the pool of light from the lamp sways a little
    val lx = size.width * (0.5f + 0.03f * sin(t * 0.6f))
    drawCircle(Brush.radialGradient(listOf(Color(0xFFFFF3C4).copy(alpha = 0.20f), Color.Transparent), Offset(lx, size.height * 0.38f), size.minDimension * 0.75f), size.minDimension * 0.75f, Offset(lx, size.height * 0.38f))
    // a stitched border
    val m = size.minDimension * 0.035f
    drawRoundRect(Color(0xFFE7C873).copy(alpha = 0.55f), Offset(m, m), Size(size.width - 2 * m, size.height - 2 * m), CornerRadius(m * 2), style = Stroke(2.5f, pathEffect = androidx.compose.ui.graphics.PathEffect.dashPathEffect(floatArrayOf(14f, 9f))))
    // dust motes in the lamplight
    for (i in 0 until 26) {
        val sx = (sin(i * 12.9f) * 0.5f + 0.5f) * size.width + sin(t * 0.3f + i) * 30f
        val sy = ((i * 0.071f + t * (0.006f + (i % 5) * 0.002f)) % 1f) * size.height
        drawCircle(Color.White.copy(alpha = 0.05f + 0.05f * sin(t + i)), 1.6f + (i % 3), Offset(sx, sy))
    }
    vignette(0.55f)
}

/** A wooden table top: planks with grain, knots and a warm lamp. */
private fun DrawScope.wood(t: Float) {
    drawRect(Brush.verticalGradient(listOf(Color(0xFF5B3A1E), Color(0xFF3E2612), Color(0xFF2A190B))))
    val plank = size.height / 7f
    for (p in 0 until 8) {
        val y0 = p * plank
        drawRect(Color(if (p % 2 == 0) 0xFF6A4423 else 0xFF573619).copy(alpha = 0.55f), Offset(0f, y0), Size(size.width, plank - 3f))
        drawLine(Color.Black.copy(alpha = 0.45f), Offset(0f, y0 + plank - 2f), Offset(size.width, y0 + plank - 2f), 3f)
        // grain
        for (g in 0 until 5) {
            val gy = y0 + plank * (g + 1) / 6f
            val path = Path().apply {
                moveTo(0f, gy)
                var x = 0f
                while (x <= size.width) { lineTo(x, gy + sin(x * 0.012f + p * 1.7f + g) * 5f); x += 24f }
            }
            drawPath(path, Color(0xFF2A190B).copy(alpha = 0.22f), style = Stroke(1.4f))
        }
        // a knot
        val kx = (sin(p * 7.3f) * 0.5f + 0.5f) * size.width
        drawOval(Color(0xFF2A190B).copy(alpha = 0.35f), Offset(kx - 16f, y0 + plank * 0.4f), Size(32f, 14f))
        drawOval(Color(0xFF2A190B).copy(alpha = 0.3f), Offset(kx - 26f, y0 + plank * 0.4f - 5f), Size(52f, 24f), style = Stroke(1.5f))
    }
    val lx = size.width * (0.5f + 0.02f * sin(t * 0.5f))
    drawCircle(Brush.radialGradient(listOf(Color(0xFFFFE2A8).copy(alpha = 0.22f), Color.Transparent), Offset(lx, size.height * 0.4f), size.minDimension * 0.8f), size.minDimension * 0.8f, Offset(lx, size.height * 0.4f))
    vignette(0.6f)
}

/** Out at sea: deep water with rolling swell, shafts of light and a few gulls. */
private fun DrawScope.sea(t: Float) {
    drawRect(Brush.verticalGradient(listOf(Color(0xFF0B3A5E), Color(0xFF06243F), Color(0xFF031425))))
    for (k in 0 until 4) {
        val x = size.width * (0.15f + 0.25f * k) + sin(t * 0.25f + k) * 40f
        val beam = Path().apply { moveTo(x - 20f, 0f); lineTo(x + 20f, 0f); lineTo(x + 140f, size.height); lineTo(x - 60f, size.height); close() }
        drawPath(beam, Brush.verticalGradient(listOf(Color.White.copy(alpha = 0.07f), Color.Transparent)))
    }
    for (band in 0 until 9) {
        val y = size.height * (band + 0.5f) / 9f
        val path = Path().apply {
            moveTo(0f, y)
            var x = 0f
            while (x <= size.width + 20f) { lineTo(x, y + sin(x * 0.012f + t * (0.8f + band * 0.07f) + band) * 9f); x += 20f }
        }
        drawPath(path, Color(0xFF7DD3FC).copy(alpha = 0.10f), style = Stroke(2.2f, cap = StrokeCap.Round))
    }
    vignette(0.5f)
}

/** A bomb-disposal screen: dark olive with a sweeping radar and grid rings. */
private fun DrawScope.radar(t: Float) {
    drawRect(Brush.verticalGradient(listOf(Color(0xFF14210F), Color(0xFF0B1409), Color(0xFF050A05))))
    val c = Offset(size.width / 2f, size.height * 0.45f)
    val r = size.maxDimension * 0.7f
    for (k in 1..5) drawCircle(Color(0xFF4ADE80).copy(alpha = 0.07f), r * k / 5f, c, style = Stroke(1.5f))
    for (k in 0 until 8) { val a = k * PI.toFloat() / 4f; drawLine(Color(0xFF4ADE80).copy(alpha = 0.05f), c, Offset(c.x + cos(a) * r, c.y + sin(a) * r), 1.2f) }
    val ang = (t * 40f) % 360f
    drawArc(Brush.sweepGradient(listOf(Color.Transparent, Color(0xFF4ADE80).copy(alpha = 0.16f), Color.Transparent), c), ang - 60f, 60f, true, Offset(c.x - r, c.y - r), Size(r * 2, r * 2))
    vignette(0.65f)
}

/** A neon arcade: a glowing grid running off to the horizon under a striped sun. */
private fun DrawScope.neon(t: Float) {
    drawRect(Brush.verticalGradient(listOf(Color(0xFF0B0221), Color(0xFF240046), Color(0xFF0B0221))))
    val horizon = size.height * 0.46f
    val sun = Offset(size.width / 2f, horizon - size.width * 0.05f)
    drawCircle(Brush.verticalGradient(listOf(Color(0xFFFFD23F), Color(0xFFFF3D9A)), sun.y - size.width * 0.3f, sun.y + size.width * 0.3f), size.width * 0.3f, sun, alpha = 0.55f)
    for (k in 0 until 6) drawRect(Color(0xFF0B0221), Offset(0f, sun.y + k * size.width * 0.05f), Size(size.width, size.width * (0.008f + k * 0.004f)))
    drawRect(Color(0xFF0B0221), Offset(0f, horizon), Size(size.width, size.height - horizon))
    val pink = Color(0xFFFF3D9A)
    for (k in -8..8) drawLine(pink.copy(alpha = 0.35f), Offset(size.width / 2f + k * size.width * 0.02f, horizon), Offset(size.width / 2f + k * size.width * 0.22f, size.height), 1.6f)
    for (k in 0 until 10) {
        val f = ((k + (t * 0.6f) % 1f) / 10f)
        val y = horizon + (size.height - horizon) * f * f
        drawLine(pink.copy(alpha = 0.12f + 0.3f * f), Offset(0f, y), Offset(size.width, y), 1.6f)
    }
    vignette(0.5f)
}

/** A playroom: soft purple with card suits and stars floating gently upward. */
private fun DrawScope.playroom(t: Float) {
    drawRect(Brush.verticalGradient(listOf(Color(0xFF3B1D6E), Color(0xFF5B2A86), Color(0xFF2A1250))))
    val cols = listOf(Color(0xFFFF7EB6), Color(0xFF7DD3FC), Color(0xFFFDE68A), Color(0xFF86EFAC))
    for (i in 0 until 22) {
        val x = (sin(i * 9.7f) * 0.5f + 0.5f) * size.width + sin(t * 0.5f + i) * 16f
        val y = size.height - (((i * 0.047f + t * (0.012f + (i % 4) * 0.004f)) % 1f) * (size.height + 80f)) + 40f
        val r = 10f + (i % 4) * 5f
        val c = cols[i % cols.size].copy(alpha = 0.16f)
        when (i % 3) {
            0 -> drawCircle(c, r, Offset(x, y))
            1 -> drawRoundRect(c, Offset(x - r * 0.7f, y - r), Size(r * 1.4f, r * 2f), CornerRadius(4f))
            else -> { val p = Path().apply { moveTo(x, y - r); lineTo(x + r * 0.8f, y); lineTo(x, y + r); lineTo(x - r * 0.8f, y); close() }; drawPath(p, c) }
        }
    }
    vignette(0.45f)
}

/** A bright arcade cabinet blue, with soft red and yellow lights drifting like the counters of the game. */
private fun DrawScope.arcadeBlue(t: Float) {
    drawRect(Brush.verticalGradient(listOf(Color(0xFF0B2A6B), Color(0xFF0A1F52), Color(0xFF061236))))
    for (i in 0 until 14) {
        val x = (sin(i * 5.3f) * 0.5f + 0.5f) * size.width + sin(t * 0.4f + i * 1.3f) * 30f
        val y = (cos(i * 3.1f) * 0.5f + 0.5f) * size.height + cos(t * 0.3f + i) * 30f
        val r = size.minDimension * (0.06f + (i % 3) * 0.03f)
        val c = if (i % 2 == 0) Color(0xFFEF4444) else Color(0xFFFACC15)
        drawCircle(Brush.radialGradient(listOf(c.copy(alpha = 0.16f), Color.Transparent), Offset(x, y), r), r, Offset(x, y))
    }
    vignette(0.5f)
}

/** A beach at sunset: a glowing sky, the sun sinking into a shimmering sea, and palm trees. */
private fun DrawScope.beach(t: Float) {
    val horizon = size.height * 0.34f
    drawRect(Brush.verticalGradient(listOf(Color(0xFF2B1055), Color(0xFFB5367A), Color(0xFFFF8A3D), Color(0xFFFFD27A)), 0f, horizon), Offset.Zero, Size(size.width, horizon))
    val sun = Offset(size.width * 0.68f, horizon - size.width * 0.02f)
    drawCircle(Brush.radialGradient(listOf(Color(0xFFFFF3B0), Color(0xFFFFB03A).copy(alpha = 0.0f)), sun, size.width * 0.4f), size.width * 0.4f, sun)
    drawCircle(Color(0xFFFFF3B0), size.width * 0.09f, sun)
    drawRect(Brush.verticalGradient(listOf(Color(0xFF0E5E7A), Color(0xFF07304A), Color(0xFF041B2C)), horizon, size.height), Offset(0f, horizon), Size(size.width, size.height - horizon))
    // the sun's path on the water
    for (k in 0 until 16) {
        val y = horizon + (size.height - horizon) * 0.5f * k / 16f
        val w = size.width * (0.05f + 0.012f * k) * (0.7f + 0.3f * sin(t * 2.2f + k))
        drawLine(Color(0xFFFFD27A).copy(alpha = 0.35f - k * 0.018f), Offset(sun.x - w, y), Offset(sun.x + w, y), 3f, StrokeCap.Round)
    }
    // a palm tree leaning in from the left
    val base = Offset(size.width * 0.08f, horizon + size.height * 0.06f)
    val top = Offset(size.width * 0.20f, horizon - size.height * 0.16f)
    val trunk = Path().apply { moveTo(base.x, base.y); quadraticBezierTo(size.width * 0.10f, horizon - size.height * 0.05f, top.x, top.y) }
    drawPath(trunk, Color(0xFF12060F), style = Stroke(size.width * 0.022f, cap = StrokeCap.Round))
    for (k in 0 until 7) {
        val a = -PI.toFloat() * 0.95f + k * PI.toFloat() * 0.95f / 6f + sin(t * 0.8f + k) * 0.05f
        val len = size.width * 0.19f
        val end = Offset(top.x + cos(a) * len, top.y + sin(a) * len * 0.75f + len * 0.25f)
        val leaf = Path().apply { moveTo(top.x, top.y); quadraticBezierTo(top.x + cos(a) * len * 0.6f, top.y + sin(a) * len * 0.75f - len * 0.15f, end.x, end.y) }
        drawPath(leaf, Color(0xFF12060F), style = Stroke(size.width * 0.016f, cap = StrokeCap.Round))
    }
    drawRect(Color.Black.copy(alpha = 0.35f), Offset(0f, horizon), Size(size.width, size.height - horizon))
    vignette(0.4f)
}

/** A television quiz studio: sweeping spotlights, a starry backcloth and big faint question marks. */
private fun DrawScope.studio(t: Float) {
    drawRect(Brush.verticalGradient(listOf(Color(0xFF12063A), Color(0xFF2A0F6B), Color(0xFF0B0424))))
    for (i in 0 until 40) {
        val x = (sin(i * 12.3f) * 0.5f + 0.5f) * size.width
        val y = (cos(i * 7.7f) * 0.5f + 0.5f) * size.height * 0.6f
        drawCircle(Color.White.copy(alpha = 0.15f + 0.25f * (0.5f + 0.5f * sin(t * 2f + i))), 1.6f, Offset(x, y))
    }
    for (k in 0 until 4) {
        val x = size.width * (0.1f + 0.27f * k)
        val sway = sin(t * (0.5f + 0.13f * k) + k * 1.7f) * size.width * 0.22f
        val beam = Path().apply { moveTo(x - 8f, 0f); lineTo(x + 8f, 0f); lineTo(x + sway + size.width * 0.16f, size.height); lineTo(x + sway - size.width * 0.16f, size.height); close() }
        val c = listOf(Color(0xFFF59E0B), Color(0xFF22D3EE), Color(0xFFEC4899), Color(0xFFA78BFA))[k]
        drawPath(beam, Brush.verticalGradient(listOf(c.copy(alpha = 0.16f), Color.Transparent)))
    }
    vignette(0.5f)
}

/** A sketchbook on a desk: dark slate with chalky doodles drifting about. */
private fun DrawScope.sketch(t: Float) {
    drawRect(Brush.verticalGradient(listOf(Color(0xFF1F2937), Color(0xFF111827), Color(0xFF0B1120))))
    val chalk = listOf(Color(0xFFFDE68A), Color(0xFFF9A8D4), Color(0xFF93C5FD), Color(0xFF86EFAC))
    for (i in 0 until 12) {
        val x = (sin(i * 4.9f) * 0.5f + 0.5f) * size.width + sin(t * 0.3f + i) * 14f
        val y = (cos(i * 6.1f) * 0.5f + 0.5f) * size.height + cos(t * 0.25f + i * 2f) * 14f
        val c = chalk[i % chalk.size].copy(alpha = 0.13f)
        val s = 26f + (i % 3) * 14f
        when (i % 4) {
            0 -> drawCircle(c, s, Offset(x, y), style = Stroke(3f))
            1 -> { val p = Path().apply { moveTo(x - s, y); cubicTo(x - s / 2, y - s, x + s / 2, y + s, x + s, y) }; drawPath(p, c, style = Stroke(3f, cap = StrokeCap.Round)) }
            2 -> { val p = Path().apply { moveTo(x, y - s); lineTo(x + s, y + s * 0.8f); lineTo(x - s, y + s * 0.8f); close() }; drawPath(p, c, style = Stroke(3f)) }
            else -> for (k in 0 until 5) { val a = k * 2f * PI.toFloat() / 5f + t * 0.2f; drawLine(c, Offset(x, y), Offset(x + cos(a) * s, y + sin(a) * s), 3f, StrokeCap.Round) }
        }
    }
    vignette(0.5f)
}
