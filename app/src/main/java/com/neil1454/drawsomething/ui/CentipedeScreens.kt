package com.neil1454.drawsomething.ui

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.drawscope.DrawScope
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.unit.dp
import com.neil1454.drawsomething.core.Arcade
import com.neil1454.drawsomething.core.CentipedeEngine
import com.neil1454.drawsomething.core.CentipedeRules
import com.neil1454.drawsomething.core.CentipedeState
import com.neil1454.drawsomething.core.Sound
import com.neil1454.drawsomething.sound.Sfx
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import kotlin.math.cos
import kotlin.math.sin

@Composable
fun CentipedeHelp() {
    Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
        Text("A giant centipede winds down through a field of mushrooms, and you both have to stop it!")
        Text("• Slide your finger to fly. Hold it down to fire. You can put your thumb anywhere on the screen.")
        Text("• Shoot a segment and it turns into a mushroom and splits the centipede in two. Heads are worth 100, other segments 10.")
        Text("• Mushrooms make the centipede turn and drop a row. Shooting them takes a few hits.")
        Text("• Watch out for the spider that zig-zags along the bottom (worth more the closer you shoot it) and the flea that drops mushrooms. The spider drops a power-up when it's hit.")
        Text("• Each level the centipede is shorter, but more single heads arrive. If you are out, you come back when the level is cleared.")
    }
}

private fun DrawScope.drawMushroom(col: Int, row: Int, hp: Int) {
    val cx = col * CentipedeRules.CELL + 2f
    val cy = row * CentipedeRules.CELL + 2f
    val cap = when (hp) { 4 -> Color(0xFFEF4444); 3 -> Color(0xFFF97316); 2 -> Color(0xFFEAB308); else -> Color(0xFFA8A29E) }
    drawRect(Color(0xFFE7E5E4), Offset(cx - 0.6f, cy + 0.2f), Size(1.2f, 1.7f))
    drawArc(cap, 180f, 180f, true, Offset(cx - 1.9f, cy - 1.9f), Size(3.8f, 3.4f))
    drawArc(Color.White.copy(alpha = 0.25f), 180f, 180f, false, Offset(cx - 1.9f, cy - 1.9f), Size(3.8f, 3.4f), style = Stroke(0.35f))
    if (hp >= 3) { drawCircle(Color.White, 0.42f, Offset(cx - 0.8f, cy - 0.9f)); drawCircle(Color.White, 0.34f, Offset(cx + 0.9f, cy - 0.6f)) }
    if (hp <= 2) drawLine(Color.Black.copy(alpha = 0.5f), Offset(cx - 0.5f, cy - 1.6f), Offset(cx + 0.4f, cy + 0.2f), 0.3f)
}

private fun DrawScope.drawSegment(x: Float, y: Float, head: Boolean, dir: Int, now: Long, id: Int) {
    val r = if (head) 2.3f else 1.95f
    val step = sin(now / 70f + id * 0.9f)
    // legs
    drawLine(Color(0xFF86EFAC), Offset(x - 0.8f, y - r), Offset(x - 0.8f + step * 0.9f, y - r - 1.2f), 0.45f)
    drawLine(Color(0xFF86EFAC), Offset(x + 0.8f, y + r), Offset(x + 0.8f - step * 0.9f, y + r + 1.2f), 0.45f)
    drawCircle(Brush.radialGradient(listOf(Color(0xFF4ADE80).copy(alpha = 0.35f), Color.Transparent), Offset(x, y), r * 2f), r * 2f, Offset(x, y))
    drawCircle(Brush.radialGradient(listOf(if (head) Color(0xFFBEF264) else Color(0xFF86EFAC), Color(0xFF15803D)), Offset(x - r * 0.3f, y - r * 0.3f), r * 1.4f), r, Offset(x, y))
    drawCircle(Color(0xFF14532D), r, Offset(x, y), style = Stroke(0.35f))
    if (head) {
        drawCircle(Color(0xFFDC2626), 0.55f, Offset(x + dir * 0.9f, y - 0.9f))
        drawCircle(Color(0xFFDC2626), 0.55f, Offset(x + dir * 0.9f, y + 0.9f))
        drawLine(Color(0xFFBEF264), Offset(x + dir * 1.5f, y - 1.6f), Offset(x + dir * 3.1f, y - 2.6f), 0.4f)
        drawLine(Color(0xFFBEF264), Offset(x + dir * 1.5f, y + 1.6f), Offset(x + dir * 3.1f, y + 2.6f), 0.4f)
    }
}

private fun DrawScope.drawSpider(x: Float, y: Float, now: Long) {
    for (k in 0 until 4) {
        val a = sin(now / 80f + k * 1.3f) * 0.5f
        drawLine(Color(0xFFC084FC), Offset(x, y), Offset(x - 4.6f - k * 0.2f, y - 2.8f + k * 1.9f + a * 2f), 0.5f)
        drawLine(Color(0xFFC084FC), Offset(x, y), Offset(x + 4.6f + k * 0.2f, y - 2.8f + k * 1.9f - a * 2f), 0.5f)
    }
    drawOval(Brush.radialGradient(listOf(Color(0xFFE9D5FF), Color(0xFF7E22CE)), Offset(x, y - 0.8f), 5f), Offset(x - 3.4f, y - 2.3f), Size(6.8f, 4.6f))
    drawCircle(Color(0xFFFDE047), 0.65f, Offset(x - 1.1f, y - 0.6f))
    drawCircle(Color(0xFFFDE047), 0.65f, Offset(x + 1.1f, y - 0.6f))
}

private fun DrawScope.drawFlea(x: Float, y: Float, hp: Int, now: Long) {
    drawCircle(Brush.radialGradient(listOf(Color(0x66FB923C), Color.Transparent), Offset(x, y), 6f), 6f, Offset(x, y))
    drawOval(Brush.verticalGradient(listOf(Color(0xFFFDE68A), Color(0xFFF97316)), y - 3f, y + 3f), Offset(x - 2f, y - 3f), Size(4f, 6f))
    drawCircle(Color(0xFF1F2937), 0.5f, Offset(x - 0.7f, y - 1f)); drawCircle(Color(0xFF1F2937), 0.5f, Offset(x + 0.7f, y - 1f))
    val legs = sin(now / 50f) * 0.8f
    drawLine(Color(0xFFFB923C), Offset(x - 1.2f, y + 3f), Offset(x - 2.4f, y + 4.6f + legs), 0.4f)
    drawLine(Color(0xFFFB923C), Offset(x + 1.2f, y + 3f), Offset(x + 2.4f, y + 4.6f - legs), 0.4f)
    if (hp == 1) drawCircle(Color.White.copy(alpha = 0.5f), 3.4f, Offset(x, y), style = Stroke(0.4f))
}

@Composable
fun CentipedeScreen(
    s: CentipedeState,
    engine: CentipedeEngine,
    onPlayAgain: () -> Unit,
    onBackToGames: () -> Unit,
    onLeave: () -> Unit,
) {
    val c = s.c
    var lastShots by remember { mutableIntStateOf(0) }
    val shots = c.bullets.count { it.owner == 1 }
    LaunchedEffect(shots) { if (shots > lastShots) Sfx.play(Sound.ZAP); lastShots = shots }
    val how = when (c.why) {
        "victory" -> "Together you cleared all ${c.levels} levels and the garden is safe!"
        "lives" -> "Both ships were caught on level ${c.level}."
        else -> "Time ran out on level ${c.level}."
    } + when (c.result) {
        1 -> " You finished top of the leaderboard with ${c.mine.score} points to ${c.theirs.score}."
        2 -> " Your partner took the top score: ${c.theirs.score} to your ${c.mine.score}."
        else -> " You finished level on ${c.mine.score} points each."
    }
    ArcadeScreen(
        c = c, theirName = engine.theirName, isHost = engine.isHost, title = "Centipede",
        hint = "Slide your finger to fly · hold it down to fire",
        endTitle = when (c.result) { 1 -> "🏆 YOU WIN!"; 2 -> "🏆 ${engine.theirName.uppercase()} WINS!"; else -> "🤝 IT'S A DRAW!" },
        endHow = how, colors = listOf(Color(0xFF2E7D32), Color(0xFFE91E63)),
        onInput = { x, y, f -> engine.input(x, y, f) },
        onPlayAgain = onPlayAgain, onBackToGames = onBackToGames, onLeave = onLeave,
        onBurst = { kind, x, y, fx, now, mine ->
            if (!arcadeBurst(kind, x, y, fx, now, mine)) when (kind) {
                "cs" -> { fx.explode(x, y, now, Color(0xFF86EFAC), 12, 0.9f); Sfx.play(Sound.POP) }
                "mu" -> { fx.explode(x, y, now, Color(0xFFEF4444), 6, 0.6f); Sfx.play(Sound.TICK) }
                "sp" -> { fx.explode(x, y, now, Color(0xFFC084FC), 22, 1.3f); Sfx.play(Sound.WIN) }
                "fl" -> { fx.explode(x, y, now, Color(0xFFFB923C), 14, 1f); Sfx.play(Sound.CHIME) }
            }
        },
        background = { f ->
            drawRect(Brush.verticalGradient(listOf(Color(0xFF050F0A), Color(0xFF0A2016), Color(0xFF0E1A22)), 0f, Arcade.H), Offset.Zero, Size(Arcade.W, Arcade.H))
            for (i in 0 until 40) {
                val gx = ((i * 7919) % 1000) / 10f
                val gy = ((i * 6007) % 1400) / 10f
                drawCircle(Color(0x2234D399), 0.25f + (i % 3) * 0.1f, Offset(gx, gy))
            }
            // the zone where you fly
            drawRect(Color(0x2238BDF8), Offset(0f, CentipedeRules.ZONE_ROW * CentipedeRules.CELL), Size(Arcade.W, Arcade.H - CentipedeRules.ZONE_ROW * CentipedeRules.CELL))
            drawLine(Color(0x5538BDF8), Offset(0f, CentipedeRules.ZONE_ROW * CentipedeRules.CELL), Offset(Arcade.W, CentipedeRules.ZONE_ROW * CentipedeRules.CELL), 0.3f)
            val t = f.nowMs / 1000f
            for (i in 0 until 18) {
                val sx = ((i * 104729) % 1000) / 10f
                val sy = (((i * 15485863) % 1400) / 10f + t * 4f) % Arcade.H
                drawCircle(Color.White.copy(alpha = 0.18f), 0.2f, Offset(sx, sy))
            }
        },
        world = { f, smooth ->
            for (i in s.mush.indices) {
                val hp = s.mush[i] - '0'
                if (hp > 0) drawMushroom(i % CentipedeRules.COLS, i / CentipedeRules.COLS, hp)
            }
            val rate = (3200f / s.stepMs).coerceIn(14f, 46f)
            for (sg in s.segs.reversed()) {
                val x = smooth.follow("sx${sg.id}", sg.col * CentipedeRules.CELL + 2f, f.dt, rate)
                val y = smooth.follow("sy${sg.id}", sg.row * CentipedeRules.CELL + 2f, f.dt, rate)
                drawSegment(x, y, sg.head, sg.dir, f.nowMs, sg.id)
            }
            s.spider?.let { drawSpider(smooth.follow("spx", it.first, f.dt, 40f), smooth.follow("spy", it.second, f.dt, 40f), f.nowMs) }
            s.flea?.let { drawFlea(it.first, smooth.follow("fly", it.second, f.dt, 40f), it.third, f.nowMs) }
        },
    )
}
