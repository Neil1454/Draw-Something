package com.neil1454.drawsomething.ui

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
import androidx.compose.ui.graphics.drawscope.scale
import androidx.compose.ui.graphics.drawscope.translate
import androidx.compose.ui.graphics.drawscope.withTransform
import kotlin.math.PI
import kotlin.math.cos
import kotlin.math.sin

// ====================================================================================
// Battleships artwork: the sea, the ships, the fire, the splashes and the falling bombs. All drawn in code, in pixels,
// from a cell size `c`, so it scales with the board.
// ====================================================================================

class ShipPalette(val hull: Color, val hullLight: Color, val deck: Color, val trim: Color, val accent: Color)

val FriendlyShip = ShipPalette(Color(0xFF475569), Color(0xFFA7B3C4), Color(0xFF7B8794), Color.White, Color(0xFFDC2626))
val WreckShip = ShipPalette(Color(0xFF3B1D1D), Color(0xFF6B3A3A), Color(0xFF4A2A2A), Color(0xFFB45454), Color(0xFF7F1D1D))
val EnemyShip = ShipPalette(Color(0xFF5B2D2D), Color(0xFFB07A7A), Color(0xFF7D4D4D), Color(0xFFFFD6D6), Color(0xFFFACC15))

/** The animated ocean: a gradient, rolling wave lines and twinkling glints. */
fun DrawScope.drawOcean(w: Float, h: Float, t: Float, enemy: Boolean) {
    val top = if (enemy) Color(0xFF0B2A57) else Color(0xFF1E88E5)
    val bottom = if (enemy) Color(0xFF05152E) else Color(0xFF0A3D91)
    drawRect(Brush.verticalGradient(listOf(top, bottom), 0f, h), Offset.Zero, Size(w, h))
    for (k in 0 until 7) {
        val base = h * (0.08f + k * 0.14f)
        val amp = h * 0.012f
        val path = Path()
        var x = 0f
        path.moveTo(0f, base)
        while (x <= w + 10f) {
            path.lineTo(x, base + sin(x / w * 7f + t * (0.9f + k * 0.13f) + k * 1.7f) * amp)
            x += 10f
        }
        drawPath(path, Color.White.copy(alpha = if (enemy) 0.05f else 0.1f), style = Stroke(width = h * 0.004f + 1f))
    }
    for (i in 0 until 26) {
        val gx = ((i * 7919) % 1000) / 1000f * w
        val gy = ((i * 6007) % 1000) / 1000f * h
        val tw = (sin(t * (1.5f + (i % 5) * 0.4f) + i) + 1f) / 2f
        drawCircle(Color.White.copy(alpha = (if (enemy) 0.05f else 0.12f) * tw), 1.2f + (i % 3), Offset(gx, gy))
    }
}

/** A flame with smoke: for a burning square. [size] is about one cell. */
fun DrawScope.drawFire(c: Offset, size: Float, t: Float, seed: Int) {
    drawCircle(Brush.radialGradient(listOf(Color(0xAAFF9A1F), Color.Transparent), c, size * 0.9f), size * 0.9f, c)
    for (k in 0 until 3) {
        val flick = 0.75f + 0.25f * sin(t * 11f + seed * 1.3f + k * 2.1f)
        val h = size * (0.62f - k * 0.13f) * flick
        val wdt = size * (0.3f - k * 0.07f)
        val xo = (k - 1) * size * 0.1f
        val p = Path().apply {
            moveTo(c.x + xo - wdt, c.y + size * 0.2f)
            quadraticBezierTo(c.x + xo - wdt * 0.4f, c.y - h * 0.4f, c.x + xo, c.y - h)
            quadraticBezierTo(c.x + xo + wdt * 0.4f, c.y - h * 0.4f, c.x + xo + wdt, c.y + size * 0.2f)
            close()
        }
        drawPath(p, listOf(Color(0xFFEF4444), Color(0xFFF97316), Color(0xFFFDE047))[k])
    }
    for (k in 0 until 3) {
        val ph = (t * 0.55f + seed * 0.37f + k / 3f) % 1f
        drawCircle(Color(0xFF111827).copy(alpha = (1f - ph) * 0.4f), size * (0.12f + ph * 0.22f), Offset(c.x + sin(ph * 6f + k) * size * 0.12f, c.y - size * 0.3f - ph * size * 1.5f))
    }
}

/** A column of water thrown up by a bomb that missed. p goes 0 to 1. */
fun DrawScope.drawWaterSpout(c: Offset, size: Float, p: Float) {
    if (p <= 0f || p >= 1f) return
    val rise = if (p < 0.35f) p / 0.35f else 1f - (p - 0.35f) / 0.65f
    val h = size * 2.1f * rise.coerceIn(0f, 1f)
    val wBase = size * 0.34f
    val col = Path().apply {
        moveTo(c.x - wBase, c.y)
        quadraticBezierTo(c.x - wBase * 0.5f, c.y - h * 0.6f, c.x - wBase * 0.35f, c.y - h)
        quadraticBezierTo(c.x, c.y - h * 1.08f, c.x + wBase * 0.35f, c.y - h)
        quadraticBezierTo(c.x + wBase * 0.5f, c.y - h * 0.6f, c.x + wBase, c.y)
        close()
    }
    drawPath(col, Brush.verticalGradient(listOf(Color.White.copy(alpha = 0.95f * (1f - p * 0.5f)), Color(0xFFBFE3FF).copy(alpha = 0.8f * (1f - p))), c.y - h, c.y))
    for (k in 0 until 3) {
        val q = (p * 1.1f - k * 0.12f).coerceIn(0f, 1f)
        drawOval(Color.White.copy(alpha = (1f - q) * 0.9f), Offset(c.x - size * (0.3f + q * 0.9f), c.y - size * (0.1f + q * 0.28f) / 2f), Size(size * (0.6f + q * 1.8f), size * (0.2f + q * 0.56f) / 2f), style = Stroke(size * 0.05f + 1f))
    }
    for (i in 0 until 9) {
        val a = -PI.toFloat() * (0.15f + 0.7f * i / 8f)
        val sp = size * (1.1f + (i % 3) * 0.35f)
        val tt = p * 1.3f
        val x = c.x + cos(a) * sp * tt * 0.8f
        val y = c.y + sin(a) * sp * tt * 1.4f + size * 1.6f * tt * tt
        drawCircle(Color.White.copy(alpha = (1f - p).coerceIn(0f, 1f) * 0.9f), size * 0.06f + 1f, Offset(x, y))
    }
}

/** The target marker: turning brackets and a pulsing dot. */
fun DrawScope.drawReticle(c: Offset, size: Float, t: Float, colour: Color) {
    val pulse = 0.85f + 0.15f * sin(t * 8f)
    rotate(t * 70f, c) {
        for (k in 0 until 4) {
            val r = size * 0.52f * pulse
            drawArc(colour, k * 90f - 20f, 40f, false, Offset(c.x - r, c.y - r), Size(r * 2, r * 2), style = Stroke(size * 0.07f + 1f, cap = StrokeCap.Round))
        }
    }
    drawCircle(colour.copy(alpha = 0.35f + 0.25f * sin(t * 8f)), size * 0.1f, c)
    drawLine(colour, Offset(c.x - size * 0.62f, c.y), Offset(c.x - size * 0.3f, c.y), size * 0.045f + 1f)
    drawLine(colour, Offset(c.x + size * 0.3f, c.y), Offset(c.x + size * 0.62f, c.y), size * 0.045f + 1f)
    drawLine(colour, Offset(c.x, c.y - size * 0.62f), Offset(c.x, c.y - size * 0.3f), size * 0.045f + 1f)
    drawLine(colour, Offset(c.x, c.y + size * 0.3f), Offset(c.x, c.y + size * 0.62f), size * 0.045f + 1f)
}

/**
 * A warship lying along a square-sided grid. [centre] is the middle of the ship, [c] a cell, [len] how many cells long.
 * [sink] 0..1 tilts it and slips it under the waves; at 1 it is a dark wreck. [lift] raises it (for the drop-in animation).
 */
fun DrawScope.drawWarship(
    len: Int, centre: Offset, c: Float, horizontal: Boolean, pal: ShipPalette, t: Float, phase: Float,
    sink: Float = 0f, lift: Float = 0f, alpha: Float = 1f,
) {
    val L = len * c
    val w = c * 0.78f
    val a = alpha * (1f - 0.35f * sink)
    fun col(x: Color) = x.copy(alpha = x.alpha * a)
    val bob = sin(t * 1.7f + phase) * c * 0.022f * (1f - sink)
    val roll = sin(t * 1.25f + phase) * 1.4f * (1f - sink) + sink * (if (phase.toInt() % 2 == 0) 24f else -24f)
    withTransform({
        translate(centre.x, centre.y + bob + sink * c * 0.22f - lift)
        rotate((if (horizontal) 0f else 90f) + roll, Offset.Zero)
        scale(1f - 0.12f * sink + lift / (c * 8f), 1f - 0.12f * sink + lift / (c * 8f), Offset.Zero)
    }) {
        val hl = L / 2f
        // shadow on the water
        drawOval(Color.Black.copy(alpha = 0.22f * a), Offset(-hl, -w * 0.5f + c * 0.06f), Size(L, w))
        // foam and wake
        if (sink < 0.3f) {
            for (k in 0 until 4) {
                val ph = (t * 0.9f + k * 0.25f + phase) % 1f
                drawOval(Color.White.copy(alpha = (1f - ph) * 0.35f), Offset(-hl - c * (0.1f + ph * 0.9f), -w * (0.2f + ph * 0.1f)), Size(c * 0.5f, w * (0.4f + ph * 0.2f)))
            }
            drawLine(Color.White.copy(alpha = 0.5f * a), Offset(hl - w, -w * 0.5f), Offset(hl + c * 0.18f, 0f), c * 0.05f + 1f)
            drawLine(Color.White.copy(alpha = 0.5f * a), Offset(hl - w, w * 0.5f), Offset(hl + c * 0.18f, 0f), c * 0.05f + 1f)
        }
        drawHullFor(len, pal, ::col, hl, w, c, t, phase)
        // a shine along the top
        drawRoundRect(Color.White.copy(alpha = 0.10f * a), Offset(-hl + c * 0.1f, -w * 0.46f), Size(L - c * 0.4f, w * 0.18f), CornerRadius(w * 0.1f))
    }
}

/** The ship's own shape, by length: 5 carrier, 4 battleship, 3 cruiser/submarine, 2 destroyer. */
private fun DrawScope.drawHullFor(len: Int, pal: ShipPalette, col: (Color) -> Color, hl: Float, w: Float, c: Float, t: Float, phase: Float) {
    val L = hl * 2f
    val bow = w * 1.15f
    val hull = Path().apply {
        moveTo(-hl, -w * 0.46f)
        lineTo(hl - bow, -w * 0.5f)
        quadraticBezierTo(hl - bow * 0.25f, -w * 0.46f, hl, 0f)
        quadraticBezierTo(hl - bow * 0.25f, w * 0.46f, hl - bow, w * 0.5f)
        lineTo(-hl, w * 0.46f)
        quadraticBezierTo(-hl - w * 0.14f, 0f, -hl, -w * 0.46f)
        close()
    }
    drawPath(hull, Brush.verticalGradient(listOf(col(pal.hullLight), col(pal.hull)), -w * 0.5f, w * 0.5f))
    drawPath(hull, col(pal.trim), style = Stroke(c * 0.035f + 1f))
    // the deck, a little smaller than the hull
    val deck = Path().apply {
        moveTo(-hl + c * 0.08f, -w * 0.34f)
        lineTo(hl - bow * 0.95f, -w * 0.36f)
        quadraticBezierTo(hl - bow * 0.35f, -w * 0.3f, hl - c * 0.16f, 0f)
        quadraticBezierTo(hl - bow * 0.35f, w * 0.3f, hl - bow * 0.95f, w * 0.36f)
        lineTo(-hl + c * 0.08f, w * 0.34f)
        close()
    }
    drawPath(deck, col(pal.deck))
    // planking
    var x = -hl + c * 0.25f
    while (x < hl - bow) { drawLine(Color.Black.copy(alpha = 0.13f), Offset(x, -w * 0.32f), Offset(x, w * 0.32f), 1f); x += c * 0.28f }

    fun turret(px: Float, r: Float, dir: Float) {
        drawCircle(col(Color(0xFF334155)), r * 1.1f, Offset(px, 0f))
        drawCircle(col(pal.hullLight), r, Offset(px, 0f))
        drawLine(col(Color(0xFF1F2937)), Offset(px, -r * 0.35f), Offset(px + dir * r * 2.1f, -r * 0.35f), r * 0.32f, cap = StrokeCap.Round)
        drawLine(col(Color(0xFF1F2937)), Offset(px, r * 0.35f), Offset(px + dir * r * 2.1f, r * 0.35f), r * 0.32f, cap = StrokeCap.Round)
    }
    fun funnel(px: Float, r: Float) {
        drawRoundRect(col(Color(0xFF374151)), Offset(px - r, -r * 0.8f), Size(r * 2f, r * 1.6f), CornerRadius(r * 0.5f))
        drawRect(col(pal.accent), Offset(px - r, -r * 0.18f), Size(r * 2f, r * 0.36f))
        val ph = (t * 0.6f + phase) % 1f
        drawCircle(Color(0xFF9CA3AF).copy(alpha = (1f - ph) * 0.35f), r * (0.5f + ph * 0.6f), Offset(px - r * (0.6f + ph * 1.6f), 0f))
    }
    fun bridge(px: Float, wd: Float, ht: Float) {
        drawRoundRect(col(pal.hullLight), Offset(px - wd / 2f, -ht / 2f), Size(wd, ht), CornerRadius(ht * 0.18f))
        drawRoundRect(col(Color(0xFF93C5FD)), Offset(px - wd * 0.35f, -ht * 0.3f), Size(wd * 0.7f, ht * 0.22f), CornerRadius(2f))
        drawRoundRect(col(pal.trim), Offset(px - wd / 2f, -ht / 2f), Size(wd, ht), CornerRadius(ht * 0.18f), style = Stroke(1.2f))
    }
    when (len) {
        5 -> { // aircraft carrier: a long flat flight deck, an island and aircraft
            drawRect(col(Color(0xFF374151)), Offset(-hl + c * 0.18f, -w * 0.3f), Size(L - bow * 1.15f, w * 0.6f))
            var dx = -hl + c * 0.3f
            while (dx < hl - bow * 1.2f) { drawLine(Color.White.copy(alpha = 0.8f), Offset(dx, 0f), Offset(dx + c * 0.2f, 0f), 2.4f); dx += c * 0.42f }
            bridge(-hl * 0.1f, c * 0.5f, w * 0.32f).also { }
            for (k in 0 until 3) {
                val px = -hl + c * (0.55f + k * 0.55f)
                val plane = Path().apply { moveTo(px + c * 0.18f, 0f); lineTo(px - c * 0.1f, -c * 0.2f); lineTo(px - c * 0.05f, 0f); lineTo(px - c * 0.1f, c * 0.2f); close() }
                drawPath(plane, col(Color(0xFFE5E7EB)))
            }
        }
        4 -> { // battleship: big guns, a tall bridge and a funnel
            turret(hl - bow - c * 0.15f, w * 0.2f, 1f)
            turret(hl - bow - c * 0.85f, w * 0.2f, 1f)
            turret(-hl + c * 0.55f, w * 0.2f, -1f)
            funnel(-hl * 0.18f, w * 0.17f)
            bridge(hl * 0.05f, c * 0.55f, w * 0.42f)
        }
        3 -> { // cruiser (or submarine-sized): two turrets, a bridge and a funnel
            turret(hl - bow - c * 0.1f, w * 0.19f, 1f)
            turret(-hl + c * 0.42f, w * 0.19f, -1f)
            bridge(-hl * 0.02f, c * 0.5f, w * 0.38f)
            funnel(-hl * 0.42f, w * 0.15f)
        }
        else -> { // destroyer: small and quick
            turret(hl - bow - c * 0.05f, w * 0.17f, 1f)
            bridge(-hl * 0.15f, c * 0.4f, w * 0.34f)
            funnel(-hl * 0.55f, w * 0.13f)
        }
    }
}

/** A submarine (3 squares): a dark rounded hull with a conning tower and periscope. */
fun DrawScope.drawSubmarine(centre: Offset, c: Float, horizontal: Boolean, pal: ShipPalette, t: Float, phase: Float, sink: Float = 0f, lift: Float = 0f, alpha: Float = 1f) {
    val L = 3 * c
    val w = c * 0.62f
    val a = alpha * (1f - 0.35f * sink)
    fun col(x: Color) = x.copy(alpha = x.alpha * a)
    withTransform({
        translate(centre.x, centre.y + sin(t * 1.5f + phase) * c * 0.02f * (1f - sink) + sink * c * 0.2f - lift)
        rotate((if (horizontal) 0f else 90f) + sink * 20f, Offset.Zero)
    }) {
        val hl = L / 2f
        drawOval(Color.Black.copy(alpha = 0.22f * a), Offset(-hl, -w * 0.5f + c * 0.07f), Size(L, w))
        drawRoundRect(Brush.verticalGradient(listOf(col(pal.hullLight), col(pal.hull)), -w * 0.5f, w * 0.5f), Offset(-hl, -w * 0.5f), Size(L, w), CornerRadius(w * 0.5f))
        drawRoundRect(col(pal.trim), Offset(-hl, -w * 0.5f), Size(L, w), CornerRadius(w * 0.5f), style = Stroke(c * 0.035f + 1f))
        drawRoundRect(col(pal.hullLight), Offset(-c * 0.18f, -w * 0.34f), Size(c * 0.62f, w * 0.68f), CornerRadius(w * 0.2f))
        drawLine(col(Color(0xFF1F2937)), Offset(c * 0.1f, 0f), Offset(c * 0.1f, 0f), 1f)
        drawLine(col(Color(0xFF1F2937)), Offset(c * 0.28f, -w * 0.05f), Offset(c * 0.28f, -w * 0.5f), c * 0.045f + 1f)
        drawLine(col(Color(0xFF1F2937)), Offset(c * 0.28f, -w * 0.5f), Offset(c * 0.4f, -w * 0.5f), c * 0.045f + 1f)
        drawCircle(col(Color(0xFF93C5FD)), c * 0.05f, Offset(c * 0.1f, 0f))
        drawLine(col(pal.accent), Offset(-hl + c * 0.2f, 0f), Offset(-hl + c * 0.6f, 0f), c * 0.06f)
        for (k in 0 until 3) {
            val ph = (t * 0.7f + k / 3f + phase) % 1f
            drawCircle(Color.White.copy(alpha = (1f - ph) * 0.4f * a), c * (0.03f + ph * 0.06f), Offset(-hl - c * (0.1f + ph * 0.5f), sin(ph * 9f) * w * 0.15f))
        }
    }
}

// ---------------------------------------------------------------------------- things that fall

/** A bomb dropping towards the sea. [size] is its length in pixels; the nose points down. */
fun DrawScope.drawFallingBomb(c: Offset, size: Float, enemy: Boolean, trail: Float) {
    // the streak of its fall
    drawLine(Brush.verticalGradient(listOf(Color.Transparent, Color(0xFFFFE08A).copy(alpha = 0.7f)), c.y - trail, c.y - size * 0.4f), Offset(c.x, c.y - trail), Offset(c.x, c.y - size * 0.4f), size * 0.18f, cap = StrokeCap.Round)
    withTransform({ translate(c.x, c.y) }) {
        val bw = size * 0.34f
        // fins
        val finL = Path().apply { moveTo(-bw * 0.4f, -size * 0.5f); lineTo(-bw * 1.2f, -size * 0.78f); lineTo(-bw * 0.4f, -size * 0.28f); close() }
        val finR = Path().apply { moveTo(bw * 0.4f, -size * 0.5f); lineTo(bw * 1.2f, -size * 0.78f); lineTo(bw * 0.4f, -size * 0.28f); close() }
        drawPath(finL, Color(0xFF1F2937)); drawPath(finR, Color(0xFF1F2937))
        // body
        drawRoundRect(Brush.horizontalGradient(listOf(Color(0xFF9CA3AF), Color(0xFF374151), Color(0xFF111827)), -bw, bw), Offset(-bw * 0.5f, -size * 0.6f), Size(bw, size * 0.85f), CornerRadius(bw * 0.4f))
        val nose = Path().apply { moveTo(-bw * 0.5f, size * 0.22f); quadraticBezierTo(0f, size * 0.7f, bw * 0.5f, size * 0.22f); close() }
        drawPath(nose, if (enemy) Color(0xFFEF4444) else Color(0xFFFACC15))
        drawRect(Color(0xFFFACC15), Offset(-bw * 0.5f, -size * 0.1f), Size(bw, size * 0.08f))
        drawCircle(Color.White.copy(alpha = 0.45f), bw * 0.1f, Offset(-bw * 0.22f, -size * 0.35f))
    }
}

/** A small parachute with a radar buoy. */
fun DrawScope.drawParachute(c: Offset, size: Float) {
    val dome = Path().apply { moveTo(c.x - size * 0.5f, c.y - size * 0.55f); quadraticBezierTo(c.x, c.y - size * 1.25f, c.x + size * 0.5f, c.y - size * 0.55f); close() }
    drawPath(dome, Color(0xFFFDE047))
    drawPath(dome, Color(0xFFB45309), style = Stroke(1.6f))
    drawLine(Color(0xFFB45309), Offset(c.x - size * 0.5f, c.y - size * 0.55f), Offset(c.x, c.y), 1.4f)
    drawLine(Color(0xFFB45309), Offset(c.x + size * 0.5f, c.y - size * 0.55f), Offset(c.x, c.y), 1.4f)
    drawLine(Color(0xFFB45309), Offset(c.x, c.y - size * 0.8f), Offset(c.x, c.y), 1.4f)
    drawRoundRect(Color(0xFF334155), Offset(c.x - size * 0.16f, c.y), Size(size * 0.32f, size * 0.38f), CornerRadius(3f))
    drawCircle(Color(0xFF22D3EE), size * 0.07f, Offset(c.x, c.y + size * 0.1f))
}

/** A dark shadow growing on the water as something falls towards it. */
fun DrawScope.drawFallShadow(c: Offset, size: Float, p: Float) {
    val r = size * (0.2f + 0.45f * p)
    drawOval(Color.Black.copy(alpha = 0.12f + 0.3f * p), Offset(c.x - r, c.y - r * 0.5f), Size(r * 2f, r))
}
