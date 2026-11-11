package com.neil1454.drawsomething.ui

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
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
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.drawscope.DrawScope
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.drawscope.rotate
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.drawText
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.rememberTextMeasurer
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import kotlin.math.PI
import kotlin.math.cos
import kotlin.math.hypot
import kotlin.math.sin

// ====================================================================================
// Pictures and maps for the holiday quiz. Everything is drawn in code, so there are no image files.
// A question's "art" is "pic:<name>[:<extra>]" (a picture above the question) or "map:<name>" (tap the answer on a map).
// ====================================================================================

private class Island(val name: String, val x: Float, val y: Float, val rx: Float, val ry: Float, val rot: Float)

/** Roughly to scale and in the right places (x: west to east, y: north to south), but a little bigger so small islands can be tapped. */
private val islands = listOf(
    Island("Santo Antão", 0.133f, 0.096f, 0.085f, 0.05f, 25f),
    Island("São Vicente", 0.183f, 0.173f, 0.05f, 0.036f, -15f),
    Island("Santa Luzia", 0.250f, 0.204f, 0.022f, 0.017f, 0f),
    Island("São Nicolau", 0.400f, 0.269f, 0.085f, 0.04f, -10f),
    Island("Sal", 0.867f, 0.212f, 0.028f, 0.07f, 12f),
    Island("Boa Vista", 0.900f, 0.462f, 0.065f, 0.058f, 0f),
    Island("Maio", 0.770f, 0.808f, 0.04f, 0.045f, 0f),
    Island("Santiago", 0.633f, 0.865f, 0.04f, 0.085f, -20f),
    Island("Fogo", 0.367f, 0.904f, 0.045f, 0.045f, 0f),
    Island("Brava", 0.267f, 0.942f, 0.026f, 0.026f, 0f),
)

/** A stylised Sal (not to scale). Points run clockwise from the north tip. */
private val salOutline = listOf(
    0.50f to 0.02f, 0.62f to 0.08f, 0.66f to 0.22f, 0.70f to 0.34f, 0.74f to 0.46f, 0.70f to 0.58f, 0.62f to 0.70f,
    0.60f to 0.82f, 0.55f to 0.94f, 0.46f to 0.97f, 0.40f to 0.90f, 0.36f to 0.78f, 0.28f to 0.66f, 0.30f to 0.52f,
    0.33f to 0.42f, 0.34f to 0.30f, 0.38f to 0.16f,
)
private val salPlaces = mapOf(
    "Buracona" to Offset(0.40f, 0.12f),
    "Palmeira" to Offset(0.34f, 0.40f),
    "Espargos" to Offset(0.52f, 0.46f),
    "Pedra de Lume" to Offset(0.69f, 0.38f),
    "Santa Maria" to Offset(0.50f, 0.93f),
)

private fun mapPoints(kind: String): Map<String, Offset> = when (kind) {
    "islands" -> islands.associate { it.name to Offset(0.06f + 0.88f * it.x, 0.05f + 0.9f * it.y) }
    "sal" -> salPlaces.mapValues { (_, v) -> Offset(0.06f + 0.88f * v.x, 0.04f + 0.92f * v.y) }
    else -> emptyMap()
}

private val Sea = Brush.verticalGradient(listOf(Color(0xFF0E6E9E), Color(0xFF0A4A78)))
private val Sand = Color(0xFFE6D3A0)
private val SandEdge = Color(0xFFB79A55)

/**
 * A map you answer by tapping. Tap a place to pick it (its name appears), then "Lock in" (or tap it again) to answer.
 * Names stay hidden until the answer is revealed, so you have to know where things are.
 */
@Composable
fun HolidayMap(
    kind: String,
    options: List<String>,
    questionKey: Int,
    revealed: Boolean,
    correct: Int,
    myChoice: Int,
    theirChoice: Int,
    theirName: String,
    enabled: Boolean,
    locked: Boolean = false,
    myCol: Color = Color(0xFF3B82F6),
    theirCol: Color = Color(0xFFF59E0B),
    onLock: (Int) -> Unit,
) {
    val points = remember(kind) { mapPoints(kind) }
    var sel by remember(questionKey) { mutableIntStateOf(-1) }
    val measurer = rememberTextMeasurer()
    val ratio = if (kind == "sal") 0.85f else 1.08f
    Column(Modifier.fillMaxWidth(), verticalArrangement = Arrangement.spacedBy(6.dp), horizontalAlignment = Alignment.CenterHorizontally) {
        Canvas(
            Modifier.fillMaxWidth().aspectRatio(ratio).clip(RoundedCornerShape(20.dp)).border(2.dp, Color.White.copy(alpha = 0.4f), RoundedCornerShape(20.dp))
                .pointerInput(kind, options, enabled, sel) {
                    if (!enabled) return@pointerInput
                    detectTapGestures { tap ->
                        var best = -1
                        var bestD = Float.MAX_VALUE
                        options.forEachIndexed { i, name ->
                            val p = points[name] ?: return@forEachIndexed
                            val d = hypot(tap.x - p.x * size.width, tap.y - p.y * size.height)
                            if (d < bestD) { bestD = d; best = i }
                        }
                        if (best >= 0 && bestD < size.width * 0.22f) {
                            if (best == sel) onLock(best) else sel = best
                        }
                    }
                },
        ) {
            drawRect(Sea)
            // soft waves
            for (k in 0 until 6) {
                val yy = size.height * (0.12f + 0.16f * k)
                drawLine(Color.White.copy(alpha = 0.06f), Offset(0f, yy), Offset(size.width, yy + 14f), strokeWidth = 3f)
            }
            if (kind == "islands") {
                islands.forEach { isl ->
                    val c = Offset((0.06f + 0.88f * isl.x) * size.width, (0.05f + 0.9f * isl.y) * size.height)
                    val w = isl.rx * 0.88f * size.width * 2
                    val h = isl.ry * 0.9f * size.height * 2
                    rotate(isl.rot, c) {
                        drawOval(SandEdge, Offset(c.x - w / 2 - 3f, c.y - h / 2 - 3f), Size(w + 6f, h + 6f))
                        drawOval(Sand, Offset(c.x - w / 2, c.y - h / 2), Size(w, h))
                        drawOval(Color(0xFF9BBE6A).copy(alpha = 0.65f), Offset(c.x - w * 0.3f, c.y - h * 0.3f), Size(w * 0.6f, h * 0.6f))
                    }
                }
                drawNorth()
            } else if (kind == "sal") {
                val path = Path()
                salOutline.forEachIndexed { i, (x, y) ->
                    val px = (0.06f + 0.88f * x) * size.width
                    val py = (0.04f + 0.92f * y) * size.height
                    if (i == 0) path.moveTo(px, py) else path.lineTo(px, py)
                }
                path.close()
                drawPath(path, SandEdge, style = Stroke(width = 14f))
                drawPath(path, Sand)
                drawPath(path, Color(0xFFC9A961).copy(alpha = 0.35f), style = Stroke(width = 40f))
                drawNorth()
            }
            // markers
            options.forEachIndexed { i, name ->
                val p = points[name] ?: return@forEachIndexed
                val c = Offset(p.x * size.width, p.y * size.height)
                val isCorrect = revealed && i == correct
                val shown = revealed || locked
                val ring = when {
                    isCorrect -> Color(0xFF22C55E)
                    shown && i == myChoice -> myCol
                    shown && i == theirChoice -> theirCol
                    !revealed && i == sel -> Color(0xFFFACC15)
                    else -> Color.White
                }
                val big = isCorrect || (!revealed && i == sel) || (shown && (i == myChoice || i == theirChoice))
                if (big) drawCircle(ring.copy(alpha = 0.35f), radius = 26f)
                drawCircle(Color(0xFF0B1B33), radius = 11f)
                drawCircle(ring, radius = if (big) 9f else 7f)
                val tags = buildList {
                    if (shown && i == myChoice) add("YOU")
                    if (shown && i == theirChoice) add(theirName.take(8).uppercase())
                }
                val label = when {
                    revealed || (locked && tags.isNotEmpty()) -> name + if (tags.isNotEmpty()) "  (" + tags.joinToString(" & ") + ")" else ""
                    i == sel -> name
                    else -> ""
                }
                if (label.isNotEmpty()) {
                    val r = measurer.measure(label, TextStyle(fontSize = 11.sp, fontWeight = FontWeight.Bold, color = Color.White))
                    val tx = (c.x - r.size.width / 2f).coerceIn(4f, size.width - r.size.width - 4f)
                    val ty = if (c.y + 18f + r.size.height < size.height) c.y + 16f else c.y - 16f - r.size.height
                    drawRoundRect(Color(0xCC050816), Offset(tx - 5f, ty - 2f), Size(r.size.width + 10f, r.size.height + 4f), CornerRadius(8f))
                    drawText(r, topLeft = Offset(tx, ty))
                }
            }
        }
        if (!revealed && !locked) {
            val label = if (sel >= 0) "Lock in: ${options[sel]}" else "Tap a dot on the map"
            Box(
                Modifier.fillMaxWidth().clip(RoundedCornerShape(16.dp))
                    .background(if (sel >= 0 && enabled) Color(0xFF1FA85A) else Color.White.copy(alpha = 0.16f))
                    .clickable(enabled = sel >= 0 && enabled) { onLock(sel) }
                    .padding(vertical = 11.dp),
                contentAlignment = Alignment.Center,
            ) { Text(label, fontSize = 17.sp, fontWeight = FontWeight.ExtraBold, color = Color.White) }
        }
    }
}

private fun DrawScope.drawNorth() {
    val x = size.width - 26f
    val y = 36f
    val p = Path().apply { moveTo(x, y - 16f); lineTo(x + 7f, y + 8f); lineTo(x, y + 3f); lineTo(x - 7f, y + 8f); close() }
    drawPath(p, Color.White.copy(alpha = 0.85f))
}

// ------------------------------------------------------------------ pictures

/** A picture shown above a question. Unknown names draw nothing. */
@Composable
fun HolidayPicture(art: String, modifier: Modifier = Modifier) {
    val parts = art.removePrefix("pic:").split(":")
    val name = parts.firstOrNull().orEmpty()
    val extra = parts.drop(1).joinToString(":")
    val shape = RoundedCornerShape(18.dp)
    val measurer = rememberTextMeasurer()
    Box(modifier.fillMaxWidth().height(150.dp).clip(shape).border(2.dp, Color.White.copy(alpha = 0.4f), shape), contentAlignment = Alignment.Center) {
        Canvas(Modifier.fillMaxWidth().height(150.dp)) {
            when (name) {
                "flag" -> drawFlag()
                "plug" -> drawPlug()
                "clock" -> drawClock(extra, measurer)
                "tag" -> drawTag(extra.replace('_', ' '), measurer)
                "turtle" -> drawTurtle()
                "kite" -> drawKite()
                "beachflag" -> drawBeachFlag(extra)
                else -> drawRect(Sea)
            }
        }
    }
}

private fun starPath(c: Offset, r: Float): Path {
    val p = Path()
    for (k in 0 until 10) {
        val rad = if (k % 2 == 0) r else r * 0.42f
        val a = -PI / 2 + k * PI / 5
        val pt = Offset(c.x + (rad * cos(a)).toFloat(), c.y + (rad * sin(a)).toFloat())
        if (k == 0) p.moveTo(pt.x, pt.y) else p.lineTo(pt.x, pt.y)
    }
    p.close()
    return p
}

private fun DrawScope.drawFlag() {
    drawRect(Brush.verticalGradient(listOf(Color(0xFF14456E), Color(0xFF0B2A47))))
    val fw = minOf(size.width * 0.8f, size.height * 1.6f)
    val fh = fw * 0.62f
    val left = (size.width - fw) / 2
    val top = (size.height - fh) / 2
    drawRect(Color(0xFF003893), Offset(left, top), Size(fw, fh))
    drawRect(Color.White, Offset(left, top + fh * 0.55f), Size(fw, fh * 0.2f))
    drawRect(Color(0xFFCF2027), Offset(left, top + fh * 0.60f), Size(fw, fh * 0.1f))
    val c = Offset(left + fw * 0.38f, top + fh * 0.65f)
    val ringR = fh * 0.26f
    for (k in 0 until 10) {
        val a = -PI / 2 + k * 2 * PI / 10
        val sc = Offset(c.x + (ringR * cos(a)).toFloat(), c.y + (ringR * sin(a)).toFloat())
        drawPath(starPath(sc, fh * 0.065f), Color(0xFFFFCE00))
    }
    drawRect(Color.White.copy(alpha = 0.5f), Offset(left, top), Size(fw, fh), style = Stroke(2f))
}

private fun DrawScope.drawPlug() {
    drawRect(Brush.verticalGradient(listOf(Color(0xFFCFC7B8), Color(0xFFB4AB99))))
    val s = size.height * 0.78f
    val c = Offset(size.width / 2, size.height / 2)
    drawRoundRect(Color(0xFFF4F4F0), Offset(c.x - s / 2, c.y - s / 2), Size(s, s), CornerRadius(s * 0.18f))
    drawRoundRect(Color(0xFFBFBFB8), Offset(c.x - s / 2, c.y - s / 2), Size(s, s), CornerRadius(s * 0.18f), style = Stroke(3f))
    drawCircle(Color(0xFFE2E2DC), s * 0.38f, c)
    drawCircle(Color(0xFFBFBFB8), s * 0.38f, c, style = Stroke(3f))
    for (dx in listOf(-1f, 1f)) {
        drawCircle(Color(0xFF2A2A2A), s * 0.075f, Offset(c.x + dx * s * 0.17f, c.y))
        drawCircle(Color(0xFF9A9A92), s * 0.075f, Offset(c.x + dx * s * 0.17f, c.y), style = Stroke(2f))
    }
    // the two earth clips at the sides
    for (dx in listOf(-1f, 1f)) {
        drawRoundRect(Color(0xFF8E8E86), Offset(c.x + dx * s * 0.36f - s * 0.02f, c.y - s * 0.09f), Size(s * 0.04f, s * 0.18f), CornerRadius(4f))
    }
}

private fun DrawScope.drawClock(time: String, measurer: androidx.compose.ui.text.TextMeasurer) {
    drawRect(Brush.verticalGradient(listOf(Color(0xFF14456E), Color(0xFF0B2A47))))
    val hm = time.split(":")
    val h = hm.getOrNull(0)?.toIntOrNull()?.coerceIn(0, 23) ?: 12
    val m = hm.getOrNull(1)?.toIntOrNull()?.coerceIn(0, 59) ?: 0
    val r = size.height * 0.42f
    val c = Offset(size.width * 0.34f, size.height / 2)
    drawCircle(Color(0xFFFDF8EA), r, c)
    drawCircle(Color(0xFF8A4B2A), r, c, style = Stroke(6f))
    for (k in 0 until 12) {
        val a = -PI / 2 + k * PI / 6
        val a0 = Offset(c.x + (r * 0.84f * cos(a)).toFloat(), c.y + (r * 0.84f * sin(a)).toFloat())
        val a1 = Offset(c.x + (r * 0.95f * cos(a)).toFloat(), c.y + (r * 0.95f * sin(a)).toFloat())
        drawLine(Color(0xFF2B2B2B), a0, a1, strokeWidth = if (k % 3 == 0) 5f else 2.5f)
    }
    val ha = -PI / 2 + ((h % 12) + m / 60.0) * PI / 6
    val ma = -PI / 2 + m * PI / 30
    drawLine(Color(0xFF111111), c, Offset(c.x + (r * 0.5f * cos(ha)).toFloat(), c.y + (r * 0.5f * sin(ha)).toFloat()), strokeWidth = 8f)
    drawLine(Color(0xFF111111), c, Offset(c.x + (r * 0.78f * cos(ma)).toFloat(), c.y + (r * 0.78f * sin(ma)).toFloat()), strokeWidth = 5f)
    drawCircle(Color(0xFFCF2027), 6f, c)
    val txt = "%d:%02d".format(h, m)
    val big = measurer.measure(txt, TextStyle(fontSize = 38.sp, fontWeight = FontWeight.Black, color = Color.White))
    drawText(big, topLeft = Offset(size.width * 0.66f - big.size.width / 2f, size.height * 0.5f - big.size.height / 2f))
}

private fun DrawScope.drawTag(text: String, measurer: androidx.compose.ui.text.TextMeasurer) {
    drawRect(Brush.verticalGradient(listOf(Color(0xFF14456E), Color(0xFF0B2A47))))
    val w = size.width * 0.62f
    val h = size.height * 0.62f
    val left = (size.width - w) / 2
    val top = (size.height - h) / 2
    val p = Path().apply {
        moveTo(left + h * 0.4f, top); lineTo(left + w, top); lineTo(left + w, top + h); lineTo(left + h * 0.4f, top + h); lineTo(left, top + h / 2); close()
    }
    drawPath(p, Color(0xFFFFE9A8))
    drawPath(p, Color(0xFFB8892B), style = Stroke(4f))
    drawCircle(Color(0xFF0B2A47), h * 0.07f, Offset(left + h * 0.4f, top + h / 2))
    val r = measurer.measure(text, TextStyle(fontSize = 30.sp, fontWeight = FontWeight.Black, color = Color(0xFF3B2A06), textAlign = TextAlign.Center))
    drawText(r, topLeft = Offset(left + h * 0.5f + (w - h * 0.5f - r.size.width) / 2f, top + (h - r.size.height) / 2f))
}

private fun DrawScope.drawTurtle() {
    drawRect(Brush.verticalGradient(listOf(Color(0xFF0B1B33), Color(0xFF1B2F4F), Color(0xFFC9B27A))))
    drawCircle(Color.White.copy(alpha = 0.85f), size.height * 0.08f, Offset(size.width * 0.82f, size.height * 0.2f))
    val c = Offset(size.width / 2, size.height * 0.58f)
    val sc = size.height / 150f
    val skin = Color(0xFF8A9A5B)
    // flippers
    rotate(-35f, Offset(c.x - 34 * sc, c.y - 14 * sc)) { drawOval(skin, Offset(c.x - 74 * sc, c.y - 24 * sc), Size(56 * sc, 18 * sc)) }
    rotate(35f, Offset(c.x + 34 * sc, c.y - 14 * sc)) { drawOval(skin, Offset(c.x + 18 * sc, c.y - 24 * sc), Size(56 * sc, 18 * sc)) }
    rotate(25f, Offset(c.x - 30 * sc, c.y + 18 * sc)) { drawOval(skin, Offset(c.x - 64 * sc, c.y + 12 * sc), Size(40 * sc, 14 * sc)) }
    rotate(-25f, Offset(c.x + 30 * sc, c.y + 18 * sc)) { drawOval(skin, Offset(c.x + 24 * sc, c.y + 12 * sc), Size(40 * sc, 14 * sc)) }
    drawOval(skin, Offset(c.x - 14 * sc, c.y - 62 * sc), Size(28 * sc, 34 * sc)) // head
    drawCircle(Color.Black, 2.5f * sc, Offset(c.x - 6 * sc, c.y - 50 * sc))
    drawCircle(Color.Black, 2.5f * sc, Offset(c.x + 6 * sc, c.y - 50 * sc))
    drawOval(Color(0xFF7A4A24), Offset(c.x - 36 * sc, c.y - 34 * sc), Size(72 * sc, 82 * sc)) // shell
    drawOval(Color(0xFF9B6232), Offset(c.x - 28 * sc, c.y - 24 * sc), Size(56 * sc, 62 * sc))
    drawOval(Color(0xFF3F2410), Offset(c.x - 36 * sc, c.y - 34 * sc), Size(72 * sc, 82 * sc), style = Stroke(3f))
    for (k in -1..1) drawLine(Color(0xFF3F2410), Offset(c.x - 24 * sc, c.y + k * 18 * sc), Offset(c.x + 24 * sc, c.y + k * 18 * sc), strokeWidth = 2.5f)
    drawLine(Color(0xFF3F2410), Offset(c.x, c.y - 30 * sc), Offset(c.x, c.y + 42 * sc), strokeWidth = 2.5f)
}

private fun DrawScope.drawKite() {
    drawRect(Brush.verticalGradient(listOf(Color(0xFF6EC6F5), Color(0xFFBFE8FA), Color(0xFF14A0C8), Color(0xFF0B6E96))))
    drawCircle(Color(0xFFFFE27A), size.height * 0.1f, Offset(size.width * 0.14f, size.height * 0.2f))
    // waves
    for (k in 0 until 5) {
        val yy = size.height * (0.66f + 0.07f * k)
        drawLine(Color.White.copy(alpha = 0.35f), Offset(size.width * (0.05f + 0.2f * k), yy), Offset(size.width * (0.2f + 0.2f * k), yy), strokeWidth = 4f)
    }
    val rider = Offset(size.width * 0.42f, size.height * 0.66f)
    val kite = Offset(size.width * 0.6f, size.height * 0.2f)
    // lines
    drawLine(Color(0xFF334155), Offset(rider.x, rider.y - 30f), kite, strokeWidth = 2f)
    // the kite: a curved crescent
    val p = Path().apply {
        moveTo(kite.x - 62f, kite.y + 8f)
        quadraticBezierTo(kite.x, kite.y - 34f, kite.x + 62f, kite.y + 8f)
        quadraticBezierTo(kite.x, kite.y - 12f, kite.x - 62f, kite.y + 8f)
        close()
    }
    drawPath(p, Color(0xFFFF5A36))
    drawPath(p, Color(0xFFB02E12), style = Stroke(3f))
    // the rider
    drawLine(Color(0xFF1F2937), Offset(rider.x - 36f, rider.y + 24f), Offset(rider.x + 36f, rider.y + 12f), strokeWidth = 9f)
    drawLine(Color(0xFF1F2937), Offset(rider.x - 6f, rider.y + 16f), Offset(rider.x - 6f, rider.y - 8f), strokeWidth = 7f)
    drawLine(Color(0xFF1F2937), Offset(rider.x + 8f, rider.y + 14f), Offset(rider.x + 8f, rider.y - 8f), strokeWidth = 7f)
    drawLine(Color(0xFF1F2937), Offset(rider.x, rider.y - 8f), Offset(rider.x, rider.y - 30f), strokeWidth = 9f)
    drawCircle(Color(0xFFF1C27D), 9f, Offset(rider.x, rider.y - 40f))
}

private fun DrawScope.drawBeachFlag(colour: String) {
    drawRect(Brush.verticalGradient(listOf(Color(0xFF7FD0F2), Color(0xFFBFE8FA), Color(0xFF1599C4), Color(0xFFE6D3A0))))
    val flag = when (colour) {
        "yellow" -> Color(0xFFFFD21F)
        "green" -> Color(0xFF22A855)
        else -> Color(0xFFE02424)
    }
    val x = size.width * 0.42f
    drawRect(Color(0xFF6B4A2B), Offset(x, size.height * 0.12f), Size(7f, size.height * 0.84f))
    val p = Path().apply {
        moveTo(x + 7f, size.height * 0.14f)
        quadraticBezierTo(x + 60f, size.height * 0.08f, x + 110f, size.height * 0.16f)
        quadraticBezierTo(x + 60f, size.height * 0.26f, x + 110f, size.height * 0.36f)
        quadraticBezierTo(x + 60f, size.height * 0.30f, x + 7f, size.height * 0.40f)
        close()
    }
    drawPath(p, flag)
    drawPath(p, Color.Black.copy(alpha = 0.25f), style = Stroke(2f))
    for (k in 0 until 3) {
        val yy = size.height * (0.52f + 0.05f * k)
        drawLine(Color.White.copy(alpha = 0.7f), Offset(size.width * (0.62f + 0.04f * k), yy), Offset(size.width * (0.84f + 0.03f * k), yy), strokeWidth = 4f)
    }
}
