package com.neil1454.drawsomething.ui

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.drawscope.DrawScope
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.drawscope.withTransform
import androidx.compose.ui.unit.dp
import com.neil1454.drawsomething.core.Arcade
import com.neil1454.drawsomething.core.BulletView
import com.neil1454.drawsomething.core.GPhase
import com.neil1454.drawsomething.core.InvadersEngine
import com.neil1454.drawsomething.core.InvadersRules
import com.neil1454.drawsomething.core.InvadersState
import com.neil1454.drawsomething.core.Sound
import com.neil1454.drawsomething.sound.Sfx
import kotlin.math.sin

@Composable
fun InvadersHelp() {
    Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
        Text("Two ships, one sky! You both fly at the bottom of the same screen and shoot the invaders as they march down.")
        Text("• Slide your finger to fly. Hold it down to fire. You can put your thumb anywhere on the screen.")
        Text("• Shoot the mystery ship at the top for big points and a power-up: rapid fire, triple shot or an extra life.")
        Text("• The four shields crumble when anything hits them, including your own shots. Bombs from the invaders cost a life.")
        Text("• Clear a wave and the next one is faster. If a ship is out, it is back when the wave is cleared. If the invaders reach the ground, it's over for both of you.")
        Text("• You are partners against the aliens and rivals for the top score.")
    }
}

private val SQUID = listOf(
    listOf("...##...", "..####..", ".######.", "##.##.##", "########", "..#..#..", ".#.##.#.", "#.#..#.#"),
    listOf("...##...", "..####..", ".######.", "##.##.##", "########", ".#.##.#.", "#......#", ".#....#."),
)
private val CRAB = listOf(
    listOf("..#.....#..", "...#...#...", "..#######..", ".##.###.##.", "###########", "#.#######.#", "#.#.....#.#", "...##.##..."),
    listOf("..#.....#..", "#..#...#..#", "#.#######.#", "###.###.###", "###########", ".#########.", "..#.....#..", ".#.......#."),
)
private val OCTOPUS = listOf(
    listOf("....####....", ".##########.", "############", "###..##..###", "############", "...##..##...", "..##.##.##..", "##........##"),
    listOf("....####....", ".##########.", "############", "###..##..###", "############", "..###..###..", ".##..##..##.", "..##....##.."),
)

private val pathCache = HashMap<List<String>, Path>()
private fun spritePath(rows: List<String>): Path = pathCache.getOrPut(rows) {
    Path().apply {
        for ((y, row) in rows.withIndex()) for ((x, ch) in row.withIndex()) if (ch == '#') addRect(androidx.compose.ui.geometry.Rect(x.toFloat(), y.toFloat(), x + 1f, y + 1f))
    }
}

private val rowColours = listOf(Color(0xFF67E8F9), Color(0xFFF0ABFC), Color(0xFFF0ABFC), Color(0xFFA3E635), Color(0xFFA3E635))

private fun DrawScope.drawInvader(cx: Float, cy: Float, row: Int, frame: Int, col: Color, pulse: Float) {
    val sprites = when (row) { 0 -> SQUID; 1, 2 -> CRAB; else -> OCTOPUS }
    val rows = sprites[frame % 2]
    val ps = 0.55f
    val w = rows[0].length * ps
    val h = rows.size * ps
    withTransform({ translate(cx - w / 2f, cy - h / 2f); scale(ps, ps, Offset.Zero) }) {
        val p = spritePath(rows)
        drawPath(p, col.copy(alpha = 0.22f * pulse), style = Stroke(width = 1.8f))
        drawPath(p, col)
    }
}

private fun DrawScope.drawUfo(x: Float, now: Long) {
    val y = 9f
    drawOval(Color(0x55F472B6), Offset(x - 8f, y - 3.5f), Size(16f, 7f))
    drawOval(Color(0xFFE11D74), Offset(x - 5.5f, y - 1.4f), Size(11f, 3.8f))
    drawArc(Color(0xFFFDA4CF), 180f, 180f, true, Offset(x - 3f, y - 3.6f), Size(6f, 4.6f))
    for (k in -2..2) drawCircle(if (((now / 140L) + k) % 2L == 0L) Color(0xFFFDE047) else Color(0xFFFFFFFF), 0.5f, Offset(x + k * 2.1f, y + 0.6f))
}

private fun DrawScope.drawBomb(b: BulletView, now: Long) {
    val col = when (b.kind) { 0 -> Color(0xFFFDE047); 1 -> Color(0xFFFB7185); else -> Color(0xFFFFFFFF) }
    val wob = sin(now / 60f + b.y * 0.6f) * 0.9f
    val p = Path().apply {
        moveTo(b.x + wob, b.y - 3.2f); lineTo(b.x - wob, b.y - 1.2f); lineTo(b.x + wob, b.y + 0.8f); lineTo(b.x - wob, b.y + 2.8f)
    }
    drawPath(p, col.copy(alpha = 0.3f), style = Stroke(2.4f))
    drawPath(p, col, style = Stroke(1.0f))
}

@Composable
fun InvadersScreen(
    s: InvadersState,
    engine: InvadersEngine,
    onPlayAgain: () -> Unit,
    onBackToGames: () -> Unit,
    onLeave: () -> Unit,
) {
    val c = s.c
    var lastShots by remember { mutableIntStateOf(0) }
    val shots = c.bullets.count { it.owner == 1 }
    LaunchedEffect(shots) { if (shots > lastShots) Sfx.play(Sound.ZAP); lastShots = shots }
    LaunchedEffect(s.frame) { if (c.stage == "PLAY" && c.phase == GPhase.PLAYING) Sfx.play(Sound.THUD) }
    val how = when (c.why) {
        "victory" -> "Together you held off all ${c.levels} waves and Earth was saved!"
        "invaded" -> "The invaders reached the ground on wave ${c.level}."
        "lives" -> "Both ships were destroyed on wave ${c.level}."
        else -> "Time ran out on wave ${c.level}."
    } + when (c.result) {
        1 -> " You finished top of the leaderboard with ${c.mine.score} points to ${c.theirs.score}."
        2 -> " Your partner took the top score: ${c.theirs.score} to your ${c.mine.score}."
        else -> " You finished level on ${c.mine.score} points each."
    }
    ArcadeScreen(
        c = c, theirName = engine.theirName, isHost = engine.isHost, title = "Space Invaders",
        hint = "Slide your finger to fly · hold it down to fire",
        endTitle = when (c.result) { 1 -> "🏆 YOU WIN!"; 2 -> "🏆 ${engine.theirName.uppercase()} WINS!"; else -> "🤝 IT'S A DRAW!" },
        endHow = how, colors = listOf(Color(0xFF1B1464), Color(0xFF00E5A0)),
        onInput = { x, y, f -> engine.input(x, y, f) },
        onPlayAgain = onPlayAgain, onBackToGames = onBackToGames, onLeave = onLeave,
        onBurst = { kind, x, y, fx, now, mine ->
            if (!arcadeBurst(kind, x, y, fx, now, mine) && kind.startsWith("ex")) {
                val row = kind.removePrefix("ex").toIntOrNull() ?: 0
                fx.explode(x, y, now, rowColours[row.coerceIn(0, 4)], 12, 0.9f)
                Sfx.play(Sound.POP)
            }
        },
        background = { f -> drawSpace(f) },
        world = { f, smooth ->
            // ground line and a faint "danger" line
            drawRect(Color(0x3322D3EE), Offset(0f, 137.5f), Size(Arcade.W, 0.5f))
            drawRect(Color(0x22F87171), Offset(0f, InvadersRules.INVADE_Y), Size(Arcade.W, 0.25f))
            // shields
            for (sh in 0 until InvadersRules.SHIELDS) {
                for (y in 0 until InvadersRules.SHIELD_H) for (x in 0 until InvadersRules.SHIELD_W) {
                    if (s.shields.getOrNull(sh * 96 + y * 12 + x) != '1') continue
                    val px = InvadersRules.shieldLeft(sh) + x * InvadersRules.CELL
                    val py = InvadersRules.SHIELD_TOP + y * InvadersRules.CELL
                    drawRect(Color(0xFF22C55E).copy(alpha = 0.9f - y * 0.04f), Offset(px, py), Size(InvadersRules.CELL + 0.05f, InvadersRules.CELL + 0.05f))
                }
            }
            // the invaders
            val fx = smooth.follow("fx", s.fx, f.dt, 30f)
            val fy = smooth.follow("fy", s.fy, f.dt, 30f)
            val pulse = 0.7f + 0.3f * sin(f.nowMs / 200f)
            for (r in 0 until InvadersRules.ROWS) for (col in 0 until InvadersRules.COLS) {
                if (s.alive.getOrNull(r * InvadersRules.COLS + col) != '1') continue
                drawInvader(fx + col * InvadersRules.SPACING_X + 3f, fy + r * InvadersRules.SPACING_Y + 2.25f, r, s.frame, rowColours[r], pulse)
            }
            for (b in s.bombs) drawBomb(b.copy(y = b.y + b.vy * minOf(f.ageSec, 0.12f)), f.nowMs)
            if (s.ufoX > -50f) drawUfo(smooth.follow("ufo", s.ufoX, f.dt, 40f), f.nowMs)
        },
    )
}
