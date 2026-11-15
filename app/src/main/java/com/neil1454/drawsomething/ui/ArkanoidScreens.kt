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

private fun DrawScope.drawBrick(col: Int, row: Int, ch: Char, now: Long) {
    val x = col * ArkanoidRules.BW + 0.5f
    val y = ArkanoidRules.TOP + row * ArkanoidRules.BH + 0.4f
    val w = ArkanoidRules.BW - 1f
    val h = ArkanoidRules.BH - 0.8f
    val base = when (ch) {
        '2' -> Color(0xFFCBD5E1)
        '3' -> Color(0xFF64748B)
        '9' -> Color(0xFFEAB308)
        'E' -> Color(0xFFDC2626)
        else -> RowColours[row.coerceIn(0, RowColours.lastIndex)]
    }
    drawRoundRect(Brush.verticalGradient(listOf(base.copy(alpha = 1f), base.copy(alpha = 0.72f)), y, y + h), Offset(x, y), Size(w, h), CornerRadius(0.7f))
    drawRoundRect(Color.White.copy(alpha = 0.38f), Offset(x + 0.3f, y + 0.25f), Size(w - 0.6f, h * 0.32f), CornerRadius(0.5f))
    drawRoundRect(Color.Black.copy(alpha = 0.4f), Offset(x, y), Size(w, h), CornerRadius(0.7f), style = Stroke(0.28f))
    when (ch) {
        '3' -> { drawCircle(Color(0xFFCBD5E1), 0.4f, Offset(x + 0.9f, y + h / 2f)); drawCircle(Color(0xFFCBD5E1), 0.4f, Offset(x + w - 0.9f, y + h / 2f)) }
        '9' -> { val sh = 0.5f + 0.5f * sin(now / 300f + col); drawRoundRect(Color.White.copy(alpha = 0.35f * sh), Offset(x + 1f, y + 0.4f), Size(2.2f, h - 0.8f), CornerRadius(0.4f)) }
        'E' -> {
            val c = Offset(x + w / 2f, y + h / 2f)
            val pulse = 0.8f + 0.25f * sin(now / 140f)
            drawCircle(Color(0xFFFDE047), 1.15f * pulse, c)
            drawCircle(Color(0xFF7F1D1D), 0.55f, c)
        }
    }
}

private fun DrawScope.drawPaddle(v: ShipView, smooth: Smooth, key: String, col: Color, now: Long, dt: Float, mine: Boolean) {
    val x = smooth.follow("$key-x", v.x, dt, 30f)
    val w = if (v.triple) ArkanoidRules.PADDLE_WIDE else ArkanoidRules.PADDLE_W
    val y = ArkanoidRules.PADDLE_Y
    drawOval(Brush.radialGradient(listOf(col.copy(alpha = 0.35f), Color.Transparent), Offset(x, y + 1f), w * 0.8f), Offset(x - w * 0.8f, y - 6f), Size(w * 1.6f, 14f))
    drawRoundRect(Brush.verticalGradient(listOf(Color.White, col, col.copy(alpha = 0.6f)), y - 2f, y + 2.6f), Offset(x - w / 2f, y - 1.6f), Size(w, 3.6f), CornerRadius(1.8f))
    drawRoundRect(Color.Black.copy(alpha = 0.4f), Offset(x - w / 2f, y - 1.6f), Size(w, 3.6f), CornerRadius(1.8f), style = Stroke(0.3f))
    drawRoundRect(Color.White.copy(alpha = 0.55f), Offset(x - w / 2f + 1.5f, y - 1.2f), Size(w - 3f, 0.9f), CornerRadius(0.5f))
    if (v.rapid) {
        // laser cannons on each end
        for (sx in listOf(x - w / 2f + 1.2f, x + w / 2f - 1.2f)) {
            drawRect(Color(0xFFEF4444), Offset(sx - 0.5f, y - 4.2f), Size(1f, 2.8f))
            drawCircle(Color(0xFFFCA5A5).copy(alpha = 0.6f + 0.4f * sin(now / 90f)), 0.8f, Offset(sx, y - 4.4f))
        }
    }
    if (mine) {
        val tri = androidx.compose.ui.graphics.Path().apply { moveTo(x, y + 4.4f); lineTo(x - 1.6f, y + 7f); lineTo(x + 1.6f, y + 7f); close() }
        drawPath(tri, col.copy(alpha = 0.85f))
    }
}

private fun DrawScope.drawBall(b: BallView, frame: ArcadeFrame, fire: Boolean) {
    val age = minOf(frame.ageSec, 0.06f)
    val x = (b.x + b.vx * age).coerceIn(1.6f, 98.4f)
    val y = (b.y + b.vy * age).coerceAtMost(142f)
    val col = if (b.owner == 1) (if (frame.iAmHost) HostShip else GuestShip) else (if (frame.iAmHost) GuestShip else HostShip)
    val len = kotlin.math.hypot(b.vx, b.vy).coerceAtLeast(1f)
    for (k in 1..5) {
        val tx = x - b.vx / len * k * 1.8f
        val ty = y - b.vy / len * k * 1.8f
        drawCircle((if (fire) Color(0xFFF97316) else col).copy(alpha = 0.32f - k * 0.055f), 1.6f - k * 0.18f, Offset(tx, ty))
    }
    if (fire) {
        drawCircle(Brush.radialGradient(listOf(Color(0xCCFFB020), Color.Transparent), Offset(x, y), 6f), 6f, Offset(x, y))
        drawCircle(Color(0xFFFFF3B0), 1.9f, Offset(x, y))
        drawCircle(Color(0xFFF97316), 1.9f, Offset(x, y), style = Stroke(0.5f))
    } else {
        drawCircle(Brush.radialGradient(listOf(col.copy(alpha = 0.45f), Color.Transparent), Offset(x, y), 5f), 5f, Offset(x, y))
        drawCircle(Color.White, 1.6f, Offset(x, y))
        drawCircle(col, 1.6f, Offset(x, y), style = Stroke(0.45f))
    }
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
        hint = "Slide your finger left and right to move your paddle",
        endTitle = when (c.result) { 1 -> "🏆 YOU WIN!"; 2 -> "🏆 ${engine.theirName.uppercase()} WINS!"; else -> "🤝 IT'S A DRAW!" },
        endHow = how, colors = listOf(Color(0xFF0D47A1), Color(0xFFFF6D00)),
        onInput = { x, _, f -> engine.input(x, f) },
        onPlayAgain = onPlayAgain, onBackToGames = onBackToGames, onLeave = onLeave,
        drawShips = false,
        words = ArcadeWords(level = "LEVEL", rapid = "🔫 LASERS", triple = "↔ WIDE"),
        onBurst = { kind, x, y, fx, now, _ ->
            if (!arcadeBurst(kind, x, y, fx, now, Color.White)) when (kind) {
                "bk" -> { fx.explode(x, y, now, rowColour(y), 9, 0.75f); Sfx.play(Sound.POP) }
                "hp" -> { fx.explode(x, y, now, Color(0xFFCBD5E1), 4, 0.4f); Sfx.play(Sound.TICK) }
                "gd" -> { fx.glow(x, y, now, Color(0xFFEAB308)); Sfx.play(Sound.TAP) }
                "bm" -> { fx.explode(x, y, now, Color(0xFFF97316), 28, 1.6f); fx.shake(now, 1.8f, 380); Sfx.play(Sound.EXPLODE) }
                "pd" -> { fx.glow(x, y, now, Color.White); Sfx.play(Sound.DROP) }
            }
        },
        background = { f ->
            drawRect(Brush.verticalGradient(listOf(Color(0xFF050B24), Color(0xFF0A1B4A), Color(0xFF14083A)), 0f, Arcade.H), Offset.Zero, Size(Arcade.W, Arcade.H))
            val t = f.nowMs / 1000f
            for (i in 0 until 26) {
                val gx = ((i * 7919) % 1000) / 10f
                val gy = (((i * 6007) % 1400) / 10f + t * (2f + i % 3)) % Arcade.H
                drawCircle(Color.White.copy(alpha = 0.14f), 0.25f, Offset(gx, gy))
            }
            // glowing side rails and a neon floor line
            drawRect(Brush.horizontalGradient(listOf(Color(0x5538BDF8), Color.Transparent), 0f, 3f), Offset.Zero, Size(3f, Arcade.H))
            drawRect(Brush.horizontalGradient(listOf(Color.Transparent, Color(0x5538BDF8)), Arcade.W - 3f, Arcade.W), Offset(Arcade.W - 3f, 0f), Size(3f, Arcade.H))
            drawLine(Color(0x44FF6D00), Offset(0f, ArkanoidRules.PADDLE_Y + 5f), Offset(Arcade.W, ArkanoidRules.PADDLE_Y + 5f), 0.4f)
            if (s.slow) drawRect(Color(0x2238BDF8), Offset.Zero, Size(Arcade.W, Arcade.H))
        },
        world = { f, smooth ->
            val cols = ArkanoidRules.COLS
            for (i in s.bricks.indices) {
                val ch = s.bricks[i]
                if (ch != '.') drawBrick(i % cols, i / cols, ch, f.nowMs)
            }
            // lasers
            for (b in s.c.bullets) {
                val y = b.y + b.vy * minOf(f.ageSec, 0.1f)
                drawLine(Color(0xFFEF4444).copy(alpha = 0.35f), Offset(b.x, y + 5f), Offset(b.x, y), 1.4f)
                drawLine(Color(0xFFFCA5A5), Offset(b.x, y + 3f), Offset(b.x, y), 0.7f)
            }
            // falling power-ups
            for (d in s.drops) drawDrop(d, f, measurer)
            for (b in s.balls) drawBall(b, f, s.fireball)
            drawPaddle(c.theirs, smooth, "theirs", f.theirCol, f.nowMs, f.dt, false)
            drawPaddle(c.mine, smooth, "mine", f.mineCol, f.nowMs, f.dt, true)
            // team power-ups that are running
            var tx = 2f
            if (s.slow) { drawText(measurer.measure("🐢 SLOW", TextStyle(fontSize = 3.4f.toSp(), color = Color(0xFF7DD3FC), fontWeight = FontWeight.Black)), topLeft = Offset(tx, 1.2f)); tx += 22f }
            if (s.fireball) drawText(measurer.measure("🔥 FIREBALL", TextStyle(fontSize = 3.4f.toSp(), color = Color(0xFFFDBA74), fontWeight = FontWeight.Black)), topLeft = Offset(tx, 1.2f))
        },
    )
}

private fun DrawScope.drawDrop(d: DropView, f: ArcadeFrame, m: androidx.compose.ui.text.TextMeasurer) {
    val y = d.y + ArkanoidRules.DROP_SPEED * minOf(f.ageSec, 0.1f)
    val col = dropColour(d.kind)
    val pulse = 0.85f + 0.15f * sin(f.nowMs / 120f + d.id)
    drawCircle(Brush.radialGradient(listOf(col.copy(alpha = 0.45f), Color.Transparent), Offset(d.x, y), 7f), 7f, Offset(d.x, y))
    drawRoundRect(col, Offset(d.x - 3.8f * pulse, y - 2f), Size(7.6f * pulse, 4f), CornerRadius(2f))
    drawRoundRect(Color.White.copy(alpha = 0.45f), Offset(d.x - 3.4f * pulse, y - 1.7f), Size(6.8f * pulse, 1.2f), CornerRadius(1f))
    drawRoundRect(Color.White, Offset(d.x - 3.8f * pulse, y - 2f), Size(7.6f * pulse, 4f), CornerRadius(2f), style = Stroke(0.35f))
    val label = if (d.kind == 'H') "♥" else d.kind.toString()
    val r = m.measure(label, TextStyle(fontSize = 3.3f.toSp(), color = Color.White, fontWeight = FontWeight.Black))
    drawText(r, topLeft = Offset(d.x - r.size.width / 2f, y - r.size.height / 2f))
}
