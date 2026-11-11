package com.neil1454.drawsomething.ui

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.layout.size
import androidx.compose.runtime.Composable
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
import androidx.compose.ui.graphics.drawscope.rotate
import androidx.compose.ui.graphics.drawscope.withTransform
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import com.neil1454.drawsomething.core.GameKind
import kotlin.math.PI
import kotlin.math.cos
import kotlin.math.sin

// ====================================================================================
// A drawn icon for each game, instead of an emoji: they all share one style (bold shapes on a 100 x 100 grid, a little
// shading, white outlines) so the menu looks like a set, and they stay sharp at any size.
// ====================================================================================

@Composable
fun GameIcon(kind: GameKind, size: Dp = 48.dp, modifier: Modifier = Modifier) {
    Canvas(modifier.size(size)) {
        val u = this.size.minDimension / 100f
        withTransform({ scale(u, u, Offset.Zero) }) { drawGameIcon(kind) }
    }
}

private val Ink = Color(0xFF14142B)

private fun DrawScope.drawGameIcon(kind: GameKind) {
    when (kind) {
        GameKind.DRAW -> pencil()
        GameKind.MINES -> mine()
        GameKind.SEA -> ship()
        GameKind.DROP -> connect4()
        GameKind.MEMORY -> cards()
        GameKind.QUICK -> stopwatch()
        GameKind.DICE -> pig()
        GameKind.FLIP -> reversi()
        GameKind.HILO -> hiLo()
        GameKind.TETRIS -> blocks()
        GameKind.HOLIDAY -> palm()
        GameKind.DRAUGHTS -> draughts()
        GameKind.LUDO -> ludo()
        GameKind.MONOPOLY -> hat()
        GameKind.INVADERS -> invader()
        GameKind.CENTIPEDE -> centipede()
    }
}

private fun DrawScope.outline(p: Path, fill: Color) {
    drawPath(p, fill)
    drawPath(p, Color.White.copy(alpha = 0.9f), style = Stroke(2.4f))
}

private fun DrawScope.pencil() {
    // a squiggle being drawn
    val s = Path().apply { moveTo(10f, 78f); cubicTo(22f, 52f, 34f, 92f, 46f, 68f); cubicTo(54f, 52f, 62f, 66f, 70f, 56f) }
    drawPath(s, Color.White.copy(alpha = 0.95f), style = Stroke(5f, cap = StrokeCap.Round))
    rotate(-45f, Offset(62f, 40f)) {
        drawRoundRect(Color(0xFFFBBF24), Offset(52f, 8f), Size(20f, 56f), CornerRadius(3f))
        drawRect(Color(0xFFF59E0B), Offset(58f, 8f), Size(8f, 56f))
        drawRoundRect(Color(0xFFF472B6), Offset(52f, 4f), Size(20f, 12f), CornerRadius(4f))
        drawRect(Color(0xFFD1D5DB), Offset(52f, 15f), Size(20f, 4f))
        val tip = Path().apply { moveTo(52f, 64f); lineTo(72f, 64f); lineTo(62f, 84f); close() }
        drawPath(tip, Color(0xFFFDE7C0))
        val lead = Path().apply { moveTo(58f, 76f); lineTo(66f, 76f); lineTo(62f, 84f); close() }
        drawPath(lead, Ink)
        drawRoundRect(Color.White.copy(alpha = 0.9f), Offset(52f, 4f), Size(20f, 60f), CornerRadius(3f), style = Stroke(2f))
    }
}

private fun DrawScope.mine() {
    val c = Offset(50f, 56f)
    for (k in 0 until 8) {
        val a = k * PI.toFloat() / 4f
        drawLine(Ink, Offset(c.x + cos(a) * 20f, c.y + sin(a) * 20f), Offset(c.x + cos(a) * 36f, c.y + sin(a) * 36f), 7f, cap = StrokeCap.Round)
    }
    drawCircle(Brush.radialGradient(listOf(Color(0xFF6B7280), Ink), Offset(42f, 48f), 34f), 27f, c)
    drawCircle(Color.White.copy(alpha = 0.9f), 27f, c, style = Stroke(2.4f))
    drawCircle(Color.White.copy(alpha = 0.55f), 5f, Offset(40f, 46f))
    val fuse = Path().apply { moveTo(58f, 32f); cubicTo(62f, 22f, 70f, 24f, 72f, 14f) }
    drawPath(fuse, Color(0xFFD1D5DB), style = Stroke(3.4f, cap = StrokeCap.Round))
    for (k in 0 until 8) {
        val a = k * PI.toFloat() / 4f
        drawLine(Color(0xFFFDE047), Offset(72f, 12f), Offset(72f + cos(a) * 8f, 12f + sin(a) * 8f), 2.4f, cap = StrokeCap.Round)
    }
    drawCircle(Color(0xFFFB923C), 3.5f, Offset(72f, 12f))
}

private fun DrawScope.ship() {
    // waves
    val w = Path().apply {
        moveTo(0f, 74f); cubicTo(12f, 66f, 24f, 82f, 36f, 74f); cubicTo(48f, 66f, 60f, 82f, 72f, 74f); cubicTo(84f, 66f, 92f, 78f, 100f, 72f)
        lineTo(100f, 100f); lineTo(0f, 100f); close()
    }
    drawPath(w, Color(0xFF38BDF8).copy(alpha = 0.55f))
    val hull = Path().apply { moveTo(10f, 52f); lineTo(90f, 52f); lineTo(80f, 72f); lineTo(24f, 72f); close() }
    outline(hull, Color(0xFF94A3B8))
    drawRect(Color(0xFFCBD5E1), Offset(34f, 36f), Size(32f, 16f))
    drawRect(Color.White.copy(alpha = 0.9f), Offset(34f, 36f), Size(32f, 16f), style = Stroke(2f))
    drawRect(Color(0xFF64748B), Offset(44f, 24f), Size(12f, 12f))
    drawLine(Ink, Offset(56f, 44f), Offset(92f, 34f), 5f, cap = StrokeCap.Round)
    drawLine(Ink, Offset(22f, 46f), Offset(8f, 40f), 4.4f, cap = StrokeCap.Round)
    // target ring
    drawCircle(Color(0xFFEF4444), 12f, Offset(80f, 20f), style = Stroke(3f))
    drawLine(Color(0xFFEF4444), Offset(80f, 4f), Offset(80f, 36f), 2.4f)
    drawLine(Color(0xFFEF4444), Offset(64f, 20f), Offset(96f, 20f), 2.4f)
}

private fun DrawScope.connect4() {
    drawRoundRect(Color(0xFF1D4ED8), Offset(6f, 10f), Size(88f, 78f), CornerRadius(10f))
    drawRoundRect(Color.White.copy(alpha = 0.9f), Offset(6f, 10f), Size(88f, 78f), CornerRadius(10f), style = Stroke(2.4f))
    val grid = listOf(
        "...R", "..RY", ".RYY", "RYYR",
    )
    for (r in 0 until 4) for (c in 0 until 4) {
        val ch = grid[r][c]
        val col = when (ch) { 'R' -> Color(0xFFEF4444); 'Y' -> Color(0xFFFACC15); else -> Color(0xFF0B1B5E) }
        drawCircle(col, 8.6f, Offset(22f + c * 19f, 25f + r * 19f))
    }
    // four in a row, diagonally
    for (k in 0 until 4) drawCircle(Color.White, 10.6f, Offset(22f + k * 19f, 25f + (3 - k) * 19f), style = Stroke(2.2f))
}

private fun DrawScope.cards() {
    rotate(-14f, Offset(36f, 56f)) {
        drawRoundRect(Color(0xFF2563EB), Offset(14f, 18f), Size(44f, 62f), CornerRadius(7f))
        drawRoundRect(Color.White.copy(alpha = 0.9f), Offset(14f, 18f), Size(44f, 62f), CornerRadius(7f), style = Stroke(2.4f))
        for (k in 0 until 3) drawCircle(Color.White.copy(alpha = 0.35f), 6f + k * 5f, Offset(36f, 49f), style = Stroke(1.8f))
    }
    rotate(10f, Offset(66f, 54f)) {
        drawRoundRect(Color.White, Offset(44f, 16f), Size(44f, 62f), CornerRadius(7f))
        drawRoundRect(Color(0xFF9CA3AF), Offset(44f, 16f), Size(44f, 62f), CornerRadius(7f), style = Stroke(2f))
        val heart = Path().apply {
            moveTo(66f, 62f); cubicTo(46f, 48f, 54f, 30f, 66f, 42f); cubicTo(78f, 30f, 86f, 48f, 66f, 62f); close()
        }
        drawPath(heart, Color(0xFFEF4444))
    }
}

private fun DrawScope.stopwatch() {
    drawRoundRect(Color(0xFFE5E7EB), Offset(42f, 4f), Size(16f, 10f), CornerRadius(3f))
    drawCircle(Color.White, 38f, Offset(50f, 56f))
    drawCircle(Color(0xFF1F2937), 38f, Offset(50f, 56f), style = Stroke(4f))
    for (k in 0 until 12) {
        val a = k * PI.toFloat() / 6f
        drawLine(Color(0xFF6B7280), Offset(50f + cos(a) * 31f, 56f + sin(a) * 31f), Offset(50f + cos(a) * 35f, 56f + sin(a) * 35f), 2.2f)
    }
    val bolt = Path().apply { moveTo(56f, 28f); lineTo(36f, 60f); lineTo(48f, 60f); lineTo(42f, 86f); lineTo(66f, 50f); lineTo(53f, 50f); close() }
    outline(bolt, Color(0xFFFACC15))
}

private fun DrawScope.pig() {
    val ear = Path().apply { moveTo(20f, 40f); lineTo(24f, 12f); lineTo(46f, 28f); close() }
    val ear2 = Path().apply { moveTo(80f, 40f); lineTo(76f, 12f); lineTo(54f, 28f); close() }
    outline(ear, Color(0xFFF9A8D4)); outline(ear2, Color(0xFFF9A8D4))
    drawCircle(Brush.radialGradient(listOf(Color(0xFFFBCFE8), Color(0xFFF472B6)), Offset(42f, 44f), 48f), 38f, Offset(50f, 56f))
    drawCircle(Color.White.copy(alpha = 0.9f), 38f, Offset(50f, 56f), style = Stroke(2.4f))
    drawOval(Color(0xFFEC4899), Offset(32f, 54f), Size(36f, 28f))
    drawOval(Color.White.copy(alpha = 0.9f), Offset(32f, 54f), Size(36f, 28f), style = Stroke(2f))
    drawOval(Ink, Offset(40f, 62f), Size(6f, 11f)); drawOval(Ink, Offset(54f, 62f), Size(6f, 11f))
    drawCircle(Ink, 4.6f, Offset(36f, 46f)); drawCircle(Ink, 4.6f, Offset(64f, 46f))
    drawCircle(Color.White, 1.5f, Offset(37.2f, 44.8f)); drawCircle(Color.White, 1.5f, Offset(65.2f, 44.8f))
}

private fun DrawScope.reversi() {
    drawCircle(Color.White, 38f, Offset(50f, 50f))
    val half = Path().apply { moveTo(50f, 12f); arcTo(androidx.compose.ui.geometry.Rect(12f, 12f, 88f, 88f), -90f, -180f, false); close() }
    drawPath(half, Color(0xFF111827))
    drawCircle(Color.White.copy(alpha = 0.95f), 38f, Offset(50f, 50f), style = Stroke(3f))
    drawArc(Color(0xFFFACC15), 200f, 110f, false, Offset(24f, 24f), Size(52f, 52f), style = Stroke(5f, cap = StrokeCap.Round))
    val a = Path().apply { moveTo(66f, 18f); lineTo(80f, 30f); lineTo(60f, 34f); close() }
    drawPath(a, Color(0xFFFACC15))
    drawArc(Color(0xFFFACC15), 20f, 110f, false, Offset(24f, 24f), Size(52f, 52f), style = Stroke(5f, cap = StrokeCap.Round))
    val b = Path().apply { moveTo(34f, 82f); lineTo(20f, 70f); lineTo(40f, 66f); close() }
    drawPath(b, Color(0xFFFACC15))
}

private fun DrawScope.hiLo() {
    drawRoundRect(Color.White, Offset(24f, 8f), Size(52f, 76f), CornerRadius(8f))
    drawRoundRect(Color(0xFF9CA3AF), Offset(24f, 8f), Size(52f, 76f), CornerRadius(8f), style = Stroke(2f))
    val up = Path().apply { moveTo(50f, 16f); lineTo(66f, 38f); lineTo(34f, 38f); close() }
    val down = Path().apply { moveTo(50f, 76f); lineTo(66f, 54f); lineTo(34f, 54f); close() }
    drawPath(up, Color(0xFF22C55E)); drawPath(down, Color(0xFFEF4444))
    drawCircle(Color(0xFFFACC15), 8f, Offset(50f, 46f)); drawCircle(Ink, 8f, Offset(50f, 46f), style = Stroke(2f))
    // a second card peeking out
    rotate(16f, Offset(70f, 60f)) { drawRoundRect(Color(0xFF1D4ED8), Offset(60f, 22f), Size(30f, 56f), CornerRadius(6f)); drawRoundRect(Color.White.copy(alpha = 0.8f), Offset(60f, 22f), Size(30f, 56f), CornerRadius(6f), style = Stroke(2f)) }
    drawRoundRect(Color.White, Offset(24f, 8f), Size(52f, 76f), CornerRadius(8f))
    drawRoundRect(Color(0xFF9CA3AF), Offset(24f, 8f), Size(52f, 76f), CornerRadius(8f), style = Stroke(2f))
    drawPath(up, Color(0xFF22C55E)); drawPath(down, Color(0xFFEF4444))
    drawCircle(Color(0xFFFACC15), 8f, Offset(50f, 46f)); drawCircle(Ink, 8f, Offset(50f, 46f), style = Stroke(2f))
}

private fun DrawScope.block(x: Float, y: Float, s: Float, c: Color) {
    drawRoundRect(c, Offset(x, y), Size(s, s), CornerRadius(3f))
    drawRoundRect(Color.White.copy(alpha = 0.35f), Offset(x + 2f, y + 2f), Size(s - 4f, (s - 4f) * 0.35f), CornerRadius(2f))
    drawRoundRect(Color.White.copy(alpha = 0.9f), Offset(x, y), Size(s, s), CornerRadius(3f), style = Stroke(2f))
}

private fun DrawScope.blocks() {
    val s = 20f
    for ((x, y) in listOf(0 to 0, 1 to 0, 2 to 0, 1 to 1)) block(14f + x * s, 12f + y * s, s, Color(0xFFA855F7))
    for ((x, y) in listOf(0 to 0, 0 to 1, 0 to 2, 1 to 2)) block(14f + x * s, 40f + y * s, s, Color(0xFFF97316))
    for ((x, y) in listOf(0 to 0, 1 to 0, 0 to 1, 1 to 1)) block(60f + x * s, 54f + y * s, s, Color(0xFFFACC15))
    for ((x, y) in listOf(0 to 0, 0 to 1, 1 to 1, 1 to 2)) block(60f + x * s, 12f + y * s, s, Color(0xFF22D3EE))
}

private fun DrawScope.palm() {
    drawCircle(Brush.radialGradient(listOf(Color(0xFFFEF08A), Color(0xFFFACC15)), Offset(78f, 22f), 20f), 16f, Offset(78f, 22f))
    for (k in 0 until 8) {
        val a = k * PI.toFloat() / 4f
        drawLine(Color(0xFFFDE047), Offset(78f + cos(a) * 20f, 22f + sin(a) * 20f), Offset(78f + cos(a) * 26f, 22f + sin(a) * 26f), 2.6f, cap = StrokeCap.Round)
    }
    val trunk = Path().apply { moveTo(40f, 90f); cubicTo(44f, 70f, 42f, 50f, 34f, 34f) }
    drawPath(trunk, Color(0xFF92400E), style = Stroke(7f, cap = StrokeCap.Round))
    val leaves = listOf(
        Triple(34f, 34f, -150f), Triple(34f, 34f, -110f), Triple(34f, 34f, -70f), Triple(34f, 34f, -30f), Triple(34f, 34f, 10f),
    )
    for ((x, y, deg) in leaves) {
        val a = deg * PI.toFloat() / 180f
        val p = Path().apply {
            moveTo(x, y)
            cubicTo(x + cos(a) * 14f, y + sin(a) * 14f - 12f, x + cos(a) * 26f, y + sin(a) * 26f - 8f, x + cos(a) * 32f, y + sin(a) * 32f + 8f)
        }
        drawPath(p, Color(0xFF22C55E), style = Stroke(6f, cap = StrokeCap.Round))
    }
    val sea = Path().apply { moveTo(0f, 86f); cubicTo(14f, 78f, 28f, 94f, 42f, 86f); cubicTo(56f, 78f, 70f, 94f, 84f, 86f); cubicTo(92f, 82f, 96f, 84f, 100f, 84f); lineTo(100f, 100f); lineTo(0f, 100f); close() }
    drawPath(sea, Color(0xFF38BDF8).copy(alpha = 0.85f))
}

private fun DrawScope.disc(c: Offset, r: Float, fill: Color) {
    drawOval(Color.Black.copy(alpha = 0.25f), Offset(c.x - r, c.y + r * 0.5f), Size(r * 2, r * 0.8f))
    drawOval(fill.copy(alpha = 0.75f), Offset(c.x - r, c.y - r * 0.5f + 5f), Size(r * 2, r * 1.2f))
    drawOval(fill, Offset(c.x - r, c.y - r * 0.5f), Size(r * 2, r * 1.2f))
    drawOval(Color.White.copy(alpha = 0.85f), Offset(c.x - r, c.y - r * 0.5f), Size(r * 2, r * 1.2f), style = Stroke(2f))
}

private fun DrawScope.draughts() {
    disc(Offset(50f, 78f), 26f, Color(0xFFDC2626))
    disc(Offset(50f, 62f), 26f, Color(0xFFEF4444))
    disc(Offset(50f, 46f), 26f, Color(0xFFF87171))
    val crown = Path().apply { moveTo(34f, 40f); lineTo(34f, 22f); lineTo(42f, 32f); lineTo(50f, 18f); lineTo(58f, 32f); lineTo(66f, 22f); lineTo(66f, 40f); close() }
    outline(crown, Color(0xFFFACC15))
    disc(Offset(80f, 86f), 14f, Color(0xFFF3F4F6))
}

private fun DrawScope.ludo() {
    val pawn = Path().apply {
        moveTo(26f, 90f); cubicTo(26f, 70f, 34f, 62f, 38f, 52f); lineTo(34f, 52f); lineTo(34f, 46f); lineTo(46f, 46f)
        lineTo(46f, 52f); cubicTo(50f, 62f, 58f, 70f, 58f, 90f); close()
    }
    outline(pawn, Color(0xFFEF4444))
    drawCircle(Brush.radialGradient(listOf(Color(0xFFFCA5A5), Color(0xFFDC2626)), Offset(38f, 28f), 22f), 17f, Offset(40f, 32f))
    drawCircle(Color.White.copy(alpha = 0.9f), 17f, Offset(40f, 32f), style = Stroke(2.2f))
    // a die
    rotate(12f, Offset(76f, 66f)) {
        drawRoundRect(Color.White, Offset(60f, 50f), Size(32f, 32f), CornerRadius(7f))
        drawRoundRect(Color(0xFF2563EB), Offset(60f, 50f), Size(32f, 32f), CornerRadius(7f), style = Stroke(2.6f))
        for ((px, py) in listOf(66f to 56f, 86f to 56f, 76f to 66f, 66f to 76f, 86f to 76f)) drawCircle(Ink, 3.2f, Offset(px, py))
    }
}

private fun DrawScope.hat() {
    drawOval(Color.Black.copy(alpha = 0.3f), Offset(8f, 76f), Size(84f, 18f))
    drawOval(Color(0xFF111827), Offset(6f, 66f), Size(88f, 22f))
    drawOval(Color.White.copy(alpha = 0.85f), Offset(6f, 66f), Size(88f, 22f), style = Stroke(2.2f))
    drawRoundRect(Color(0xFF111827), Offset(24f, 14f), Size(52f, 62f), CornerRadius(6f))
    drawOval(Color(0xFF1F2937), Offset(24f, 8f), Size(52f, 14f))
    drawOval(Color.White.copy(alpha = 0.85f), Offset(24f, 8f), Size(52f, 14f), style = Stroke(2f))
    drawRect(Color(0xFFDC2626), Offset(24f, 54f), Size(52f, 14f))
    drawRoundRect(Color.White.copy(alpha = 0.85f), Offset(24f, 14f), Size(52f, 62f), CornerRadius(6f), style = Stroke(2.2f))
    drawCircle(Color(0xFFFACC15), 11f, Offset(80f, 30f)); drawCircle(Color(0xFFB45309), 11f, Offset(80f, 30f), style = Stroke(2.4f))
    drawLine(Color(0xFFB45309), Offset(80f, 24f), Offset(80f, 36f), 2.6f)
}

private val invaderRows = listOf("..#.....#..", "...#...#...", "..#######..", ".##.###.##.", "###########", "#.#######.#", "#.#.....#.#", "...##.##...")

private fun DrawScope.invader() {
    val ps = 6.6f
    withTransform({ translate(13.5f, 6f); scale(ps, ps, Offset.Zero) }) {
        val p = Path()
        for ((y, row) in invaderRows.withIndex()) for ((x, ch) in row.withIndex()) if (ch == '#') p.addRect(androidx.compose.ui.geometry.Rect(x.toFloat(), y.toFloat(), x + 1f, y + 1f))
        drawPath(p, Color(0xFF86EFAC).copy(alpha = 0.3f), style = Stroke(0.9f))
        drawPath(p, Color(0xFF4ADE80))
        drawRect(Ink, Offset(3f, 3f), Size(1.2f, 1.2f)); drawRect(Ink, Offset(6.8f, 3f), Size(1.2f, 1.2f))
    }
    drawLine(Color(0xFF7DD3FC), Offset(50f, 66f), Offset(50f, 78f), 3.2f, cap = StrokeCap.Round)
    val ship = Path().apply { moveTo(50f, 78f); lineTo(60f, 94f); lineTo(40f, 94f); close() }
    outline(ship, Color(0xFF38BDF8))
}

private fun DrawScope.centipede() {
    val pts = listOf(Offset(14f, 70f), Offset(30f, 78f), Offset(46f, 70f), Offset(62f, 78f), Offset(78f, 66f), Offset(84f, 48f))
    for (i in pts.indices.reversed()) {
        val p = pts[i]
        drawLine(Color(0xFF86EFAC), Offset(p.x - 3f, p.y - 10f), Offset(p.x - 3f, p.y + 10f), 3f, cap = StrokeCap.Round)
        drawCircle(Brush.radialGradient(listOf(if (i == pts.lastIndex) Color(0xFFBEF264) else Color(0xFF86EFAC), Color(0xFF15803D)), Offset(p.x - 3f, p.y - 3f), 14f), 10.5f, p)
        drawCircle(Color.White.copy(alpha = 0.85f), 10.5f, p, style = Stroke(2f))
    }
    val h = pts.last()
    drawCircle(Color(0xFFDC2626), 2.8f, Offset(h.x - 3f, h.y - 4f)); drawCircle(Color(0xFFDC2626), 2.8f, Offset(h.x + 4f, h.y - 4f))
    drawLine(Color(0xFFBEF264), Offset(h.x - 4f, h.y - 10f), Offset(h.x - 9f, h.y - 18f), 2.4f); drawLine(Color(0xFFBEF264), Offset(h.x + 4f, h.y - 10f), Offset(h.x + 9f, h.y - 18f), 2.4f)
    // a mushroom
    drawRect(Color(0xFFE7E5E4), Offset(26f, 28f), Size(9f, 16f))
    drawArc(Color(0xFFEF4444), 180f, 180f, true, Offset(14f, 12f), Size(33f, 30f))
    drawCircle(Color.White, 3.2f, Offset(24f, 24f)); drawCircle(Color.White, 2.6f, Offset(37f, 27f))
}
