package com.neil1454.drawsomething.ui

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.drawscope.DrawScope
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.drawText
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.rememberTextMeasurer
import androidx.compose.ui.unit.dp
import com.neil1454.drawsomething.core.Arcade
import com.neil1454.drawsomething.core.ArkanoidEngine
import com.neil1454.drawsomething.core.ArkanoidRules
import com.neil1454.drawsomething.core.ArkanoidState
import com.neil1454.drawsomething.core.BallView
import com.neil1454.drawsomething.core.DropView
import com.neil1454.drawsomething.core.ShipView
import com.neil1454.drawsomething.core.Sound
import com.neil1454.drawsomething.sound.Sfx
import kotlin.math.cos
import kotlin.math.sin

@Composable
fun ArkanoidHelp() {
    Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
        Text("You each have a paddle along the bottom. Keep the ball in play and smash the wall of bricks together!")
        Text("• Slide your finger left and right to move your paddle. Hit the ball with the edge of the paddle to send it off at an angle.")
        Text("• Whoever touched the ball last scores the points for every brick it breaks.")
        Text("• Grey bricks take two or three hits, gold bricks can't be broken, and red bricks explode the bricks around them.")
        Text("• Bricks drop power-ups: catch them with a paddle. W wide paddle, L lasers (keep your finger down), M three balls, S slow motion, F fireball that burns straight through, ♥ an extra life for the team.")
        Text("• The lives are shared. You only lose one when the last ball drops. Clear every level to win together, and the higher score wins the bragging rights.")
    }
}

private val RowColours = listOf(
    Color(0xFFEF4444), Color(0xFFF97316), Color(0xFFEAB308), Color(0xFF84CC16), Color(0xFF22C55E),
    Color(0xFF06B6D4), Color(0xFF3B82F6), Color(0xFF8B5CF6), Color(0xFFEC4899), Color(0xFF14B8A6), Color(0xFFF43F5E),
)

private fun rowColour(y: Float): Color =
    RowColours[((y - ArkanoidRules.TOP) / ArkanoidRules.BH).toInt().coerceIn(0, RowColours.lastIndex)]

private fun dropColour(k: Char): Color = when (k) {
    'W' -> Color(0xFF38BDF8); 'L' -> Color(0xFFEF4444); 'M' -> Color(0xFF22C55E)
    'S' -> Color(0xFFA855F7); 'F' -> Color(0xFFF97316); else -> Color(0xFFF472B6)
}

/** Every level has its own look: the sky, the glow and the floor change. */
private class Theme(val top: Color, val mid: Color, val bottom: Color, val accent: Color)

private val Themes = listOf(
    Theme(Color(0xFF050B24), Color(0xFF0A1B4A), Color(0xFF14083A), Color(0xFF38BDF8)),   // deep space
    Theme(Color(0xFF1A0524), Color(0xFF3B0764), Color(0xFF0B1030), Color(0xFFF472B6)),   // neon city
    Theme(Color(0xFF1F0603), Color(0xFF4A1208), Color(0xFF0C0402), Color(0xFFFB923C)),   // volcano
    Theme(Color(0xFF031A22), Color(0xFF0B3B4A), Color(0xFF020B12), Color(0xFFA5F3FC)),   // arctic
    Theme(Color(0xFF03180B), Color(0xFF0B3D1E), Color(0xFF020A05), Color(0xFFA3E635)),   // jungle
)

private fun DrawScope.drawBackdrop(th: Theme, now: Long, danger: Boolean) {
    val t = now / 1000f
    drawRect(Brush.verticalGradient(listOf(th.top, th.mid, th.bottom), 0f, Arcade.H), Offset.Zero, Size(Arcade.W, Arcade.H))
    // slow drifting clouds of colour
    for (i in 0 until 3) {
        val cx = 50f + sin(t * 0.13f + i * 2.1f) * 38f
        val cy = 28f + i * 34f + cos(t * 0.1f + i) * 10f
        drawCircle(Brush.radialGradient(listOf(th.accent.copy(alpha = 0.16f), Color.Transparent), Offset(cx, cy), 46f), 46f, Offset(cx, cy))
    }
    // two layers of stars
    for (layer in 0 until 2) for (i in 0 until 22) {
        val sx = ((i * 7919 + layer * 104729) % 1000) / 10f
        val sy = (((i * 6007 + layer * 15485863) % 1400) / 10f + t * (2.5f + layer * 4f)) % Arcade.H
        val tw = 0.5f + 0.5f * sin(t * (1.4f + layer) + i)
        drawCircle(Color.White.copy(alpha = (0.12f + layer * 0.16f) * tw), 0.2f + layer * 0.14f, Offset(sx, sy))
    }
    // a perspective floor under the paddles
    val horizon = 96f
    for (k in 0..7) {
        val y = horizon + (k * k) * 0.75f
        if (y < Arcade.H + 2f) drawLine(th.accent.copy(alpha = 0.05f + k * 0.012f), Offset(0f, y), Offset(Arcade.W, y), 0.3f)
    }
    for (i in -7..7) drawLine(th.accent.copy(alpha = 0.09f), Offset(50f + i * 2.4f, horizon), Offset(50f + i * 19f, Arcade.H + 2f), 0.3f)
    drawRect(Brush.verticalGradient(listOf(Color.Transparent, th.accent.copy(alpha = 0.10f)), horizon, Arcade.H), Offset(0f, horizon), Size(Arcade.W, Arcade.H - horizon))
    // glowing side rails with lights running up them
    drawRect(Brush.horizontalGradient(listOf(th.accent.copy(alpha = 0.40f), Color.Transparent), 0f, 3.4f), Offset.Zero, Size(3.4f, Arcade.H))
    drawRect(Brush.horizontalGradient(listOf(Color.Transparent, th.accent.copy(alpha = 0.40f)), Arcade.W - 3.4f, Arcade.W), Offset(Arcade.W - 3.4f, 0f), Size(3.4f, Arcade.H))
    for (k in 0 until 9) {
        val y = ((t * 26f + k * 16f) % Arcade.H)
        drawCircle(Color.White.copy(alpha = 0.5f), 0.55f, Offset(0.9f, y)); drawCircle(Color.White.copy(alpha = 0.5f), 0.55f, Offset(Arcade.W - 0.9f, Arcade.H - y))
    }
    // the ceiling bar
    drawRect(Brush.verticalGradient(listOf(th.accent.copy(alpha = 0.45f), Color.Transparent), 0f, 3f), Offset.Zero, Size(Arcade.W, 3f))
    drawLine(th.accent.copy(alpha = 0.7f), Offset(0f, ArkanoidRules.PADDLE_Y + 5.4f), Offset(Arcade.W, ArkanoidRules.PADDLE_Y + 5.4f), 0.45f)
    if (danger) drawRect(Brush.verticalGradient(listOf(Color.Transparent, Color(0xFFEF4444).copy(alpha = 0.18f + 0.12f * sin(t * 9f))), 100f, Arcade.H), Offset(0f, 100f), Size(Arcade.W, Arcade.H - 100f))
}

private fun DrawScope.drawBrick(col: Int, row: Int, ch: Char, now: Long, accent: Color) {
    val x = col * ArkanoidRules.BW + 0.45f
    val y = ArkanoidRules.TOP + row * ArkanoidRules.BH + 0.4f
    val w = ArkanoidRules.BW - 0.9f
    val h = ArkanoidRules.BH - 0.8f
    val t = now / 1000f
    val base = when (ch) {
        '2' -> Color(0xFFCBD5E1)
        '3' -> Color(0xFF64748B)
        '9' -> Color(0xFFEAB308)
        'E' -> Color(0xFFDC2626)
        else -> RowColours[row.coerceIn(0, RowColours.lastIndex)]
    }
    val r = CornerRadius(0.75f)
    // a soft shadow under the brick, then the body with a top-to-bottom shine
    drawRoundRect(Color.Black.copy(alpha = 0.35f), Offset(x + 0.25f, y + 0.55f), Size(w, h), r)
    drawRoundRect(Brush.verticalGradient(listOf(lerp(base, Color.White, 0.30f), base, lerp(base, Color.Black, 0.38f)), y, y + h), Offset(x, y), Size(w, h), r)
    // a bevel: light on the top and left, dark on the bottom and right
    drawLine(Color.White.copy(alpha = 0.65f), Offset(x + 0.5f, y + 0.25f), Offset(x + w - 0.5f, y + 0.25f), 0.35f)
    drawLine(Color.White.copy(alpha = 0.30f), Offset(x + 0.25f, y + 0.5f), Offset(x + 0.25f, y + h - 0.5f), 0.3f)
    drawLine(Color.Black.copy(alpha = 0.45f), Offset(x + 0.5f, y + h - 0.2f), Offset(x + w - 0.3f, y + h - 0.2f), 0.4f)
    drawLine(Color.Black.copy(alpha = 0.30f), Offset(x + w - 0.2f, y + 0.5f), Offset(x + w - 0.2f, y + h - 0.4f), 0.3f)
    drawRoundRect(Color.White.copy(alpha = 0.26f), Offset(x + 0.8f, y + 0.5f), Size(w * 0.55f, h * 0.28f), CornerRadius(0.5f))
    // a bright band that sweeps across the whole wall now and then
    val sweep = ((t * 22f) % 190f) - 45f
    val near = 1f - (kotlin.math.abs((x + y * 0.8f) - sweep) / 11f)
    if (near > 0f) drawRoundRect(Color.White.copy(alpha = 0.40f * near), Offset(x, y), Size(w, h), r)
    when (ch) {
        '2' -> for (k in 1..2) drawLine(Color.White.copy(alpha = 0.35f), Offset(x + 0.6f, y + k * h / 3f), Offset(x + w - 0.6f, y + k * h / 3f), 0.2f)
        '3' -> {
            for ((rx, ry) in listOf(0.8f to 0.8f, w - 0.8f to 0.8f, 0.8f to h - 0.8f, w - 0.8f to h - 0.8f)) {
                drawCircle(Color(0xFFE2E8F0), 0.34f, Offset(x + rx, y + ry)); drawCircle(Color.Black.copy(alpha = 0.4f), 0.34f, Offset(x + rx, y + ry), style = Stroke(0.12f))
            }
            drawLine(Color.Black.copy(alpha = 0.3f), Offset(x + w * 0.5f, y + 0.5f), Offset(x + w * 0.5f, y + h - 0.5f), 0.25f)
        }
        '9' -> {
            val glint = 0.5f + 0.5f * sin(t * 3.2f + col * 1.3f + row)
            drawCircle(Color.White.copy(alpha = 0.85f * glint), 0.55f, Offset(x + w * (0.25f + 0.5f * glint), y + h * 0.4f))
            drawLine(Color.White.copy(alpha = 0.7f * glint), Offset(x + w * (0.25f + 0.5f * glint) - 1.2f, y + h * 0.4f), Offset(x + w * (0.25f + 0.5f * glint) + 1.2f, y + h * 0.4f), 0.22f)
        }
        'E' -> {
            // warning stripes, and a pulsing core
            for (k in 0..4) drawLine(Color(0xFFFDE047).copy(alpha = 0.45f), Offset(x + k * 2.1f, y + h), Offset(x + k * 2.1f + h, y), 0.55f)
            val c = Offset(x + w / 2f, y + h / 2f)
            val pulse = 0.8f + 0.3f * sin(t * 7f)
            drawCircle(Brush.radialGradient(listOf(Color(0xFFFDE047), Color(0xFFDC2626), Color.Transparent), c, 2.2f * pulse), 2.2f * pulse, c)
            drawCircle(Color(0xFF450A0A), 0.65f, c)
        }
        else -> {}
    }
    // the glow of the level's accent on the very top row
    if (row == 1) drawLine(accent.copy(alpha = 0.25f), Offset(x, y - 0.3f), Offset(x + w, y - 0.3f), 0.5f)
}

private fun lerp(a: Color, b: Color, t: Float) = Color(a.red + (b.red - a.red) * t, a.green + (b.green - a.green) * t, a.blue + (b.blue - a.blue) * t, a.alpha)

private fun DrawScope.drawPaddle(v: ShipView, smooth: Smooth, key: String, col: Color, now: Long, dt: Float, mine: Boolean) {
    val x = smooth.follow("$key-x", v.x, dt, 30f)
    val w = smooth.follow("$key-w", if (v.triple) ArkanoidRules.PADDLE_WIDE else ArkanoidRules.PADDLE_W, dt, 14f)
    val y = ArkanoidRules.PADDLE_Y
    val t = now / 1000f
    val left = x - w / 2f
    // engine glow and a small reflection on the floor
    val flame = 1.6f + 0.7f * sin(t * 40f + x)
    drawOval(Brush.radialGradient(listOf(col.copy(alpha = 0.55f), Color.Transparent), Offset(x, y + 3f), w * 0.7f), Offset(x - w * 0.7f, y - 2f), Size(w * 1.4f, 12f))
    for (ex in listOf(left + w * 0.22f, x + w * 0.28f)) {
        val f = androidx.compose.ui.graphics.Path().apply { moveTo(ex - 1.1f, y + 2f); lineTo(ex, y + 2f + flame * 1.9f); lineTo(ex + 1.1f, y + 2f); close() }
        drawPath(f, Color(0xFFFFB020).copy(alpha = 0.9f)); drawPath(f, Color.White.copy(alpha = 0.7f), style = Stroke(0.2f))
    }
    drawRoundRect(Color.Black.copy(alpha = 0.4f), Offset(left + 0.5f, y - 0.6f), Size(w, 4f), CornerRadius(2f))
    // the hull: a metallic body with bright end caps in the player's colour
    drawRoundRect(Brush.verticalGradient(listOf(Color(0xFFF8FAFC), Color(0xFF94A3B8), Color(0xFF334155)), y - 2f, y + 2.6f), Offset(left, y - 1.8f), Size(w, 4f), CornerRadius(2f))
    drawRoundRect(Brush.horizontalGradient(listOf(col, Color.Transparent, Color.Transparent, col), left, left + w), Offset(left, y - 1.8f), Size(w, 4f), CornerRadius(2f))
    drawRoundRect(Color.White.copy(alpha = 0.6f), Offset(left + 1.8f, y - 1.45f), Size(w - 3.6f, 0.8f), CornerRadius(0.4f))
    drawRoundRect(Color.Black.copy(alpha = 0.55f), Offset(left, y - 1.8f), Size(w, 4f), CornerRadius(2f), style = Stroke(0.3f))
    // a light strip that runs along the middle
    val lights = (w / 2.4f).toInt().coerceAtLeast(3)
    for (k in 0 until lights) {
        val lx = left + 1.6f + (w - 3.2f) * (k + 0.5f) / lights
        val on = 0.4f + 0.6f * sin(t * 6f - k * 0.9f).coerceAtLeast(0f)
        drawCircle(col.copy(alpha = on), 0.32f, Offset(lx, y + 0.1f))
    }
    if (v.rapid) {
        // laser cannons, with a muzzle flash
        for (sx in listOf(left + 1.4f, left + w - 1.4f)) {
            drawRoundRect(Color(0xFF475569), Offset(sx - 0.7f, y - 4.8f), Size(1.4f, 3.2f), CornerRadius(0.4f))
            drawCircle(Color(0xFFFCA5A5).copy(alpha = 0.5f + 0.5f * sin(t * 18f)), 0.9f, Offset(sx, y - 5f))
            drawCircle(Color(0xFFEF4444).copy(alpha = 0.35f), 2.2f, Offset(sx, y - 5f))
        }
    }
    if (v.triple) drawRoundRect(Color(0xFF38BDF8).copy(alpha = 0.35f + 0.2f * sin(t * 8f)), Offset(left - 0.4f, y - 2.2f), Size(w + 0.8f, 4.8f), CornerRadius(2.4f), style = Stroke(0.45f))
    if (mine) {
        val tri = androidx.compose.ui.graphics.Path().apply { moveTo(x, y + 5.2f); lineTo(x - 1.7f, y + 7.8f); lineTo(x + 1.7f, y + 7.8f); close() }
        drawPath(tri, col.copy(alpha = 0.9f))
    }
}

private fun DrawScope.drawBall(b: BallView, frame: ArcadeFrame, fire: Boolean) {
    val age = minOf(frame.ageSec, 0.06f)
    val x = (b.x + b.vx * age).coerceIn(1.6f, 98.4f)
    val y = (b.y + b.vy * age).coerceAtMost(142f)
    val col = if (b.owner == 1) (if (frame.iAmHost) HostShip else GuestShip) else (if (frame.iAmHost) GuestShip else HostShip)
    val len = kotlin.math.hypot(b.vx, b.vy).coerceAtLeast(1f)
    val ux = b.vx / len
    val uy = b.vy / len
    val tint = if (fire) Color(0xFFF97316) else col
    // a tapering trail behind the ball
    for (k in 1..9) {
        val f = k / 9f
        val tx = x - ux * k * 1.5f
        val ty = y - uy * k * 1.5f
        drawCircle(tint.copy(alpha = (1f - f) * 0.34f), (1.7f * (1f - f * 0.75f)), Offset(tx, ty))
    }
    if (fire) {
        // licking flames
        for (k in 0 until 7) {
            val a = frame.nowMs / 70f + k * 0.9f
            val fx = x - ux * (1.5f + k * 0.8f) + kotlin.math.cos(a) * 0.8f
            val fy = y - uy * (1.5f + k * 0.8f) + kotlin.math.sin(a) * 0.8f
            drawCircle(Color(0xFFFDE047).copy(alpha = 0.55f - k * 0.07f), 1.2f - k * 0.12f, Offset(fx, fy))
        }
        drawCircle(Brush.radialGradient(listOf(Color(0xCCFFB020), Color.Transparent), Offset(x, y), 7f), 7f, Offset(x, y))
        drawCircle(Brush.radialGradient(listOf(Color.White, Color(0xFFFDE047), Color(0xFFF97316)), Offset(x, y), 2f), 2f, Offset(x, y))
    } else {
        drawCircle(Brush.radialGradient(listOf(col.copy(alpha = 0.55f), Color.Transparent), Offset(x, y), 6f), 6f, Offset(x, y))
        drawCircle(Brush.radialGradient(listOf(Color.White, Color(0xFFE2E8F0), col.copy(alpha = 0.9f)), Offset(x - 0.4f, y - 0.4f), 2f), 1.7f, Offset(x, y))
        drawCircle(Color.White.copy(alpha = 0.95f), 0.5f, Offset(x - 0.55f, y - 0.6f))
        drawCircle(col, 1.7f, Offset(x, y), style = Stroke(0.35f))
    }
}

private fun DrawScope.drawDrop(d: DropView, f: ArcadeFrame, m: androidx.compose.ui.text.TextMeasurer) {
    val y = d.y + ArkanoidRules.DROP_SPEED * minOf(f.ageSec, 0.1f)
    val col = dropColour(d.kind)
    val t = f.nowMs / 1000f
    val spin = kotlin.math.abs(kotlin.math.cos(t * 4f + d.id)) * 0.35f + 0.65f   // the capsule turns as it falls
    // a trail of sparkles
    for (k in 1..5) drawCircle(col.copy(alpha = 0.4f - k * 0.07f), 0.7f - k * 0.08f, Offset(d.x + sin(t * 9f + k + d.id) * 1.2f, y - k * 2.1f))
    drawCircle(Brush.radialGradient(listOf(col.copy(alpha = 0.55f), Color.Transparent), Offset(d.x, y), 8f), 8f, Offset(d.x, y))
    val w = 8.2f * spin
    drawRoundRect(Color.Black.copy(alpha = 0.4f), Offset(d.x - w / 2f + 0.3f, y - 1.7f + 0.5f), Size(w, 4.2f), CornerRadius(2.1f))
    drawRoundRect(Brush.verticalGradient(listOf(lerp(col, Color.White, 0.5f), col, lerp(col, Color.Black, 0.4f)), y - 2f, y + 2.2f), Offset(d.x - w / 2f, y - 2f), Size(w, 4.2f), CornerRadius(2.1f))
    drawRoundRect(Color.White.copy(alpha = 0.55f), Offset(d.x - w / 2f + 0.8f, y - 1.6f), Size(w - 1.6f, 1.1f), CornerRadius(0.6f))
    drawRoundRect(Color.White, Offset(d.x - w / 2f, y - 2f), Size(w, 4.2f), CornerRadius(2.1f), style = Stroke(0.3f))
    val label = if (d.kind == 'H') "♥" else d.kind.toString()
    val r = m.measure(label, TextStyle(fontSize = 3.3f.toSp(), color = Color.White, fontWeight = FontWeight.Black))
    if (spin > 0.75f) drawText(r, topLeft = Offset(d.x - r.size.width / 2f, y - r.size.height / 2f + 0.1f))
}

@Composable
fun ArkanoidScreen(
    s: ArkanoidState,
    engine: ArkanoidEngine,
    onPlayAgain: () -> Unit,
    onBackToGames: () -> Unit,
    onLeave: () -> Unit,
) {
    val c = s.c
    val measurer = rememberTextMeasurer()
    val th = Themes[(c.level - 1).coerceAtLeast(0) % Themes.size]
    val how = when (c.why) {
        "victory" -> "Together you smashed through all ${c.levels} levels!"
        "lives" -> "The last ball dropped on level ${c.level}."
        else -> "Time ran out on level ${c.level}."
    } + when (c.result) {
        1 -> " You finished top of the leaderboard with ${c.mine.score} points to ${c.theirs.score}."
        2 -> " Your partner took the top score: ${c.theirs.score} to your ${c.mine.score}."
        else -> " You finished level on ${c.mine.score} points each."
    }
    ArcadeScreen(
        c = c, theirName = engine.theirName, isHost = engine.isHost, title = "Arkanoid",
        hint = "Slide your finger along the pad under the screen to move your paddle",
        endTitle = when (c.result) { 1 -> "🏆 YOU WIN!"; 2 -> "🏆 ${engine.theirName.uppercase()} WINS!"; else -> "🤝 IT'S A DRAW!" },
        endHow = how, colors = listOf(Color(0xFF0D47A1), Color(0xFFFF6D00)),
        onInput = { x, _, f -> engine.input(x, f) },
        onPlayAgain = onPlayAgain, onBackToGames = onBackToGames, onLeave = onLeave,
        drawShips = false,
        words = ArcadeWords(level = "LEVEL", rapid = "🔫 LASERS", triple = "↔ WIDE", pad = "SLIDE HERE TO MOVE YOUR PADDLE", padAxis = 0),
        onBurst = { kind, x, y, fx, now, _ ->
            if (!arcadeBurst(kind, x, y, fx, now, Color.White)) when (kind) {
                "bk" -> { val col = rowColour(y); fx.explode(x, y, now, col, 14, 1.0f); fx.glow(x, y, now, col); Sfx.play(Sound.POP) }
                "hp" -> { fx.explode(x, y, now, Color(0xFFE2E8F0), 6, 0.55f); Sfx.play(Sound.TICK) }
                "gd" -> { fx.glow(x, y, now, Color(0xFFEAB308)); fx.explode(x, y, now, Color(0xFFFDE047), 4, 0.4f); Sfx.play(Sound.TAP) }
                "bm" -> { fx.explode(x, y, now, Color(0xFFF97316), 34, 1.9f); fx.explode(x, y, now, Color(0xFFFDE047), 18, 1.2f); fx.shake(now, 2.0f, 420); fx.text(x, y - 3f, now, "BOOM!", Color(0xFFFDBA74)); Sfx.play(Sound.EXPLODE) }
                "pd" -> { fx.glow(x, y, now, th.accent); fx.explode(x, y, now, Color.White, 5, 0.5f); Sfx.play(Sound.DROP) }
            }
        },
        background = { f -> drawBackdrop(th, f.nowMs, c.mine.lives <= 1 && s.balls.any { it.y > 108f }) },
        world = { f, smooth ->
            val cols = ArkanoidRules.COLS
            for (i in s.bricks.indices) {
                val ch = s.bricks[i]
                if (ch != '.') drawBrick(i % cols, i / cols, ch, f.nowMs, th.accent)
            }
            // laser beams
            for (b in s.c.bullets) {
                val y = b.y + b.vy * minOf(f.ageSec, 0.1f)
                drawLine(Color(0xFFEF4444).copy(alpha = 0.25f), Offset(b.x, y + 7f), Offset(b.x, y), 2.4f)
                drawLine(Color(0xFFFCA5A5).copy(alpha = 0.8f), Offset(b.x, y + 5f), Offset(b.x, y), 0.9f)
                drawLine(Color.White, Offset(b.x, y + 2.4f), Offset(b.x, y), 0.45f)
                drawCircle(Color(0xFFFCA5A5).copy(alpha = 0.7f), 0.9f, Offset(b.x, y))
            }
            for (d in s.drops) drawDrop(d, f, measurer)
            for (b in s.balls) drawBall(b, f, s.fireball)
            drawPaddle(c.theirs, smooth, "theirs", f.theirCol, f.nowMs, f.dt, false)
            drawPaddle(c.mine, smooth, "mine", f.mineCol, f.nowMs, f.dt, true)
            // team power-ups that are running
            var tx = 4f
            if (s.slow) { drawText(measurer.measure("🐢 SLOW", TextStyle(fontSize = 3.4f.toSp(), color = Color(0xFF7DD3FC), fontWeight = FontWeight.Black)), topLeft = Offset(tx, 4f)); tx += 22f }
            if (s.fireball) drawText(measurer.measure("🔥 FIREBALL", TextStyle(fontSize = 3.4f.toSp(), color = Color(0xFFFDBA74), fontWeight = FontWeight.Black)), topLeft = Offset(tx, 4f))
        },
    )
}
