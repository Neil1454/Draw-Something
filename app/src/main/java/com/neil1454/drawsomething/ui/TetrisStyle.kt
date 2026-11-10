package com.neil1454.drawsomething.ui

import android.content.Context
import android.content.SharedPreferences
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.runtime.Composable
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.drawscope.DrawScope
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.lerp
import com.neil1454.drawsomething.core.TetrisBoard
import com.neil1454.drawsomething.core.TetrisRules
import kotlin.math.max
import kotlin.math.min

// ====================================================================================
// How Tetris looks: the player's choices, the four brick styles, the well and its effects.
// ====================================================================================

/** The player's own choices for Tetris (kept on this phone). */
object TetrisPrefs {
    private var prefs: SharedPreferences? = null
    private val skinState = mutableIntStateOf(0)
    private val feelState = mutableIntStateOf(1)
    private val hapticsState = mutableStateOf(true)
    private val musicState = mutableStateOf(false)

    val skinNames = listOf("Glass", "Neon", "Candy", "Classic")
    val feelNames = listOf("Relaxed", "Normal", "Quick")
    val feelNotes = listOf(
        "Bigger, slower movements. Hardest to trigger by accident.",
        "A good balance.",
        "Small, snappy movements for fast fingers.",
    )

    fun init(context: Context) {
        val p = context.applicationContext.getSharedPreferences("draw_something", Context.MODE_PRIVATE)
        prefs = p
        skinState.intValue = p.getInt("t_skin", 0).coerceIn(0, 3)
        feelState.intValue = p.getInt("t_feel", 1).coerceIn(0, 2)
        hapticsState.value = p.getBoolean("t_haptics", true)
        musicState.value = p.getBoolean("t_music", false)
    }

    var skin: Int
        get() = skinState.intValue
        set(v) { skinState.intValue = v.coerceIn(0, 3); prefs?.edit()?.putInt("t_skin", skinState.intValue)?.apply() }

    var feel: Int
        get() = feelState.intValue
        set(v) { feelState.intValue = v.coerceIn(0, 2); prefs?.edit()?.putInt("t_feel", feelState.intValue)?.apply() }

    var haptics: Boolean
        get() = hapticsState.value
        set(v) { hapticsState.value = v; prefs?.edit()?.putBoolean("t_haptics", v)?.apply() }

    var music: Boolean
        get() = musicState.value
        set(v) { musicState.value = v; prefs?.edit()?.putBoolean("t_music", v)?.apply() }
}

private val PieceColours = listOf(0xFF22D3EE, 0xFFFACC15, 0xFFA855F7, 0xFF4ADE80, 0xFFF43F5E, 0xFF3B82F6, 0xFFFB923C).map { Color(it) }
private val GiftGold = Color(0xFFFFC53D)
private val GiftRibbon = Color(0xFFFF3D8B)
private val JunkSlate = Color(0xFF64688A)

fun Color.lighten(f: Float): Color = lerp(this, Color.White, f)
fun Color.darken(f: Float): Color = lerp(this, Color.Black, f)

/** The colour of a block: its piece colour, gold for a gift, slate for tide junk. */
fun pieceColour(code: Int): Color = when {
    code == TetrisRules.GIFT -> GiftGold
    code == TetrisRules.JUNK -> JunkSlate
    else -> PieceColours[((if (code > TetrisRules.STAR) code - TetrisRules.STAR else code) - 1).coerceIn(0, 6)]
}

// ------------------------------------------------------------------ the bricks

/** One brick, drawn in the chosen style (0 Glass, 1 Neon, 2 Candy, 3 Classic). */
fun DrawScope.brick(left: Float, top: Float, size: Float, code: Int, skin: Int, alpha: Float = 1f) {
    val pad = size * 0.045f
    val s = size - 2 * pad
    val x = left + pad
    val y = top + pad
    when {
        code == TetrisRules.GIFT -> giftBrick(x, y, s, alpha)
        code == TetrisRules.JUNK -> junkBrick(x, y, s, alpha)
        else -> {
            val base = pieceColour(code)
            when (skin) {
                1 -> neonBrick(x, y, s, base, alpha)
                2 -> candyBrick(x, y, s, base, alpha)
                3 -> classicBrick(x, y, s, base, alpha)
                else -> glassBrick(x, y, s, base, alpha)
            }
            if (TetrisRules.isStar(code)) starSpark(left, top, size, alpha)
        }
    }
}

private fun DrawScope.glassBrick(x: Float, y: Float, s: Float, base: Color, a: Float) {
    val r = CornerRadius(s * 0.2f)
    drawRoundRect(
        brush = Brush.verticalGradient(listOf(base.lighten(0.40f), base, base.darken(0.38f)), startY = y, endY = y + s),
        topLeft = Offset(x, y), size = Size(s, s), cornerRadius = r, alpha = a,
    )
    drawRoundRect(
        color = Color.White.copy(alpha = 0.22f * a), topLeft = Offset(x + s * 0.07f, y + s * 0.07f), size = Size(s * 0.86f, s * 0.86f),
        cornerRadius = CornerRadius(s * 0.16f), style = Stroke(width = s * 0.05f),
    )
    drawRoundRect(
        brush = Brush.verticalGradient(listOf(Color.White.copy(alpha = 0.60f * a), Color.Transparent), startY = y + s * 0.08f, endY = y + s * 0.42f),
        topLeft = Offset(x + s * 0.14f, y + s * 0.08f), size = Size(s * 0.72f, s * 0.34f), cornerRadius = CornerRadius(s * 0.14f),
    )
    drawRoundRect(
        brush = Brush.verticalGradient(listOf(Color.Transparent, Color.Black.copy(alpha = 0.30f * a)), startY = y + s * 0.6f, endY = y + s),
        topLeft = Offset(x, y + s * 0.6f), size = Size(s, s * 0.4f), cornerRadius = CornerRadius(s * 0.2f),
    )
    drawRoundRect(color = Color.Black.copy(alpha = 0.40f * a), topLeft = Offset(x, y), size = Size(s, s), cornerRadius = r, style = Stroke(width = s * 0.045f))
}

private fun DrawScope.neonBrick(x: Float, y: Float, s: Float, base: Color, a: Float) {
    val r = CornerRadius(s * 0.22f)
    drawRoundRect(color = base.copy(alpha = 0.16f * a), topLeft = Offset(x, y), size = Size(s, s), cornerRadius = r)
    for ((w, al) in listOf(0.22f to 0.10f, 0.14f to 0.18f, 0.08f to 0.34f)) {
        drawRoundRect(color = base.copy(alpha = al * a), topLeft = Offset(x, y), size = Size(s, s), cornerRadius = r, style = Stroke(width = s * w))
    }
    drawRoundRect(
        color = base.lighten(0.55f).copy(alpha = 0.95f * a), topLeft = Offset(x + s * 0.05f, y + s * 0.05f), size = Size(s * 0.9f, s * 0.9f),
        cornerRadius = CornerRadius(s * 0.18f), style = Stroke(width = s * 0.05f),
    )
    drawRoundRect(color = base.copy(alpha = 0.35f * a), topLeft = Offset(x + s * 0.28f, y + s * 0.28f), size = Size(s * 0.44f, s * 0.44f), cornerRadius = CornerRadius(s * 0.1f))
}

private fun DrawScope.candyBrick(x: Float, y: Float, s: Float, base: Color, a: Float) {
    val r = CornerRadius(s * 0.34f)
    drawRoundRect(
        brush = Brush.radialGradient(listOf(base.lighten(0.55f), base, base.darken(0.30f)), center = Offset(x + s * 0.34f, y + s * 0.30f), radius = s * 0.95f),
        topLeft = Offset(x, y), size = Size(s, s), cornerRadius = r, alpha = a,
    )
    drawRoundRect(color = Color.Black.copy(alpha = 0.18f * a), topLeft = Offset(x, y), size = Size(s, s), cornerRadius = r, style = Stroke(width = s * 0.05f))
    drawOval(color = Color.White.copy(alpha = 0.75f * a), topLeft = Offset(x + s * 0.16f, y + s * 0.12f), size = Size(s * 0.34f, s * 0.2f))
    drawCircle(color = Color.White.copy(alpha = 0.55f * a), radius = s * 0.05f, center = Offset(x + s * 0.72f, y + s * 0.30f))
}

private fun DrawScope.classicBrick(x: Float, y: Float, s: Float, base: Color, a: Float) {
    drawRect(color = base.copy(alpha = a), topLeft = Offset(x, y), size = Size(s, s))
    val b = s * 0.14f
    drawRect(color = Color.White.copy(alpha = 0.45f * a), topLeft = Offset(x, y), size = Size(s, b))
    drawRect(color = Color.White.copy(alpha = 0.25f * a), topLeft = Offset(x, y), size = Size(b, s))
    drawRect(color = Color.Black.copy(alpha = 0.28f * a), topLeft = Offset(x, y + s - b), size = Size(s, b))
    drawRect(color = Color.Black.copy(alpha = 0.20f * a), topLeft = Offset(x + s - b, y), size = Size(b, s))
    drawRect(color = Color.Black.copy(alpha = 0.35f * a), topLeft = Offset(x, y), size = Size(s, s), style = Stroke(width = s * 0.04f))
}

/** A gift block looks like a wrapped present: gold with a pink ribbon. */
private fun DrawScope.giftBrick(x: Float, y: Float, s: Float, a: Float) {
    val r = CornerRadius(s * 0.16f)
    drawRoundRect(
        brush = Brush.verticalGradient(listOf(GiftGold.lighten(0.35f), GiftGold, GiftGold.darken(0.30f)), startY = y, endY = y + s),
        topLeft = Offset(x, y), size = Size(s, s), cornerRadius = r, alpha = a,
    )
    val band = s * 0.24f
    drawRect(color = GiftRibbon.copy(alpha = a), topLeft = Offset(x + (s - band) / 2, y), size = Size(band, s))
    drawRect(color = GiftRibbon.copy(alpha = a), topLeft = Offset(x, y + (s - band) / 2), size = Size(s, band))
    drawRect(color = Color.White.copy(alpha = 0.35f * a), topLeft = Offset(x + (s - band) / 2, y), size = Size(band * 0.32f, s))
    drawRoundRect(color = Color.White.copy(alpha = 0.85f * a), topLeft = Offset(x, y), size = Size(s, s), cornerRadius = r, style = Stroke(width = s * 0.06f))
}

private fun DrawScope.junkBrick(x: Float, y: Float, s: Float, a: Float) {
    val r = CornerRadius(s * 0.12f)
    drawRoundRect(
        brush = Brush.verticalGradient(listOf(JunkSlate.lighten(0.15f), JunkSlate.darken(0.30f)), startY = y, endY = y + s),
        topLeft = Offset(x, y), size = Size(s, s), cornerRadius = r, alpha = a,
    )
    drawLine(Color.Black.copy(alpha = 0.30f * a), Offset(x + s * 0.15f, y + s * 0.8f), Offset(x + s * 0.8f, y + s * 0.15f), strokeWidth = s * 0.06f)
    drawLine(Color.White.copy(alpha = 0.18f * a), Offset(x + s * 0.25f, y + s * 0.9f), Offset(x + s * 0.9f, y + s * 0.25f), strokeWidth = s * 0.04f)
    drawRoundRect(color = Color.Black.copy(alpha = 0.4f * a), topLeft = Offset(x, y), size = Size(s, s), cornerRadius = r, style = Stroke(width = s * 0.04f))
}

/** A four-pointed sparkle on a star block. */
fun DrawScope.starSpark(left: Float, top: Float, size: Float, alpha: Float) {
    val cx = left + size / 2
    val cy = top + size / 2
    val r = size * 0.34f
    val k = r * 0.28f
    val path = Path().apply {
        moveTo(cx, cy - r); lineTo(cx + k, cy - k); lineTo(cx + r, cy); lineTo(cx + k, cy + k)
        lineTo(cx, cy + r); lineTo(cx - k, cy + k); lineTo(cx - r, cy); lineTo(cx - k, cy - k); close()
    }
    drawCircle(color = Color(0xFFFFF3B0).copy(alpha = 0.35f * alpha), radius = r * 1.05f, center = Offset(cx, cy))
    drawPath(path, Color.White.copy(alpha = 0.95f * alpha))
}

/** The bomb piece: a glossy black bomb with a lit fuse. */
fun DrawScope.bombSprite(left: Float, top: Float, s: Float, alpha: Float = 1f, spark: Float = 1f) {
    val r = s * 0.40f
    val c = Offset(left + s / 2, top + s * 0.58f)
    drawCircle(color = Color(0xFFFF3B3B).copy(alpha = 0.18f * alpha), radius = r * 1.35f, center = c)
    drawCircle(
        brush = Brush.radialGradient(listOf(Color(0xFF9A9AAE), Color(0xFF22222E), Color(0xFF0C0C14)), center = Offset(c.x - r * 0.35f, c.y - r * 0.35f), radius = r * 1.5f),
        radius = r, center = c, alpha = alpha,
    )
    drawCircle(color = Color.White.copy(alpha = 0.6f * alpha), radius = r * 0.18f, center = Offset(c.x - r * 0.4f, c.y - r * 0.4f))
    drawLine(Color(0xFFD9A441).copy(alpha = alpha), Offset(c.x + r * 0.35f, c.y - r * 0.85f), Offset(c.x + r * 0.75f, c.y - r * 1.3f), strokeWidth = s * 0.07f)
    drawCircle(color = Color(0xFFFF6A00).copy(alpha = alpha * spark), radius = s * 0.11f, center = Offset(c.x + r * 0.8f, c.y - r * 1.35f))
    drawCircle(color = Color(0xFFFFEE88).copy(alpha = alpha * spark), radius = s * 0.055f, center = Offset(c.x + r * 0.8f, c.y - r * 1.35f))
    drawCircle(color = Color(0xFFFF3B3B).copy(alpha = 0.85f * alpha), radius = r, center = c, style = Stroke(width = s * 0.045f))
}

// ------------------------------------------------------------------ the well

/** Everything that animates on a board. All progress values run 0..1 and rest at 1. */
class WellFx(
    val dim: Boolean = false,
    val fog: Boolean = false,
    val lockGlow: Float = 0f,
    val pulse: Float = 1f,
    val flashRows: List<Int> = emptyList(),
    val cleared: List<TetrisBoard.ClearedBlock> = emptyList(),
    val clearP: Float = 1f,
    val trailX0: Int = 0,
    val trailX1: Int = 0,
    val trailY0: Int = 0,
    val trailY1: Int = 0,
    val trailP: Float = 1f,
    val blastX: Int = -1,
    val blastY: Int = -1,
    val blastP: Float = 1f,
    val accent: Color = Color(0xFF7C8CFF),
)

private fun hash01(a: Int, b: Int, c: Int): Float {
    var h = a * 73856093 xor b * 19349663 xor c * 83492791
    h = h xor (h ushr 13)
    h *= 1274126177
    h = h xor (h ushr 16)
    return ((h and 0xFFFF) / 65535f)
}

/**
 * One playing well. [cellAt] gives the code at a visible column/row; the ghost, the falling piece and all the
 * effects (flash, bursting blocks, drop trail, blast) are drawn on top.
 */
fun DrawScope.tetrisWell(
    cellAt: (Int, Int) -> Int,
    type: Int, rot: Int, px: Int, py: Int, gift: Boolean, star: Int,
    ghostY: Int?,
    fx: WellFx,
    skin: Int,
) {
    val cs = size.width / TetrisRules.W
    val w = size.width
    val h = size.height
    // glass panel
    drawRoundRect(
        brush = Brush.verticalGradient(listOf(Color(0xFF111638), Color(0xFF070A1D))),
        size = size, cornerRadius = CornerRadius(cs * 0.35f),
    )
    for (gx in 1 until TetrisRules.W) for (gy in 1 until TetrisRules.H) {
        drawCircle(color = Color.White.copy(alpha = 0.07f), radius = cs * 0.045f, center = Offset(gx * cs, gy * cs))
    }
    val a = if (fx.dim) 0.45f else 1f
    var highest = TetrisRules.H
    for (y in 0 until TetrisRules.H) for (x in 0 until TetrisRules.W) {
        val c = cellAt(x, y)
        if (c != 0) {
            if (y < highest) highest = y
            brick(x * cs, y * cs, cs, c, skin, a)
        }
    }
    // the danger glow when the stack gets close to the top
    if (highest < 6) {
        val danger = (6 - highest) / 6f
        drawRect(Brush.verticalGradient(listOf(Color(0xFFFF2D55).copy(alpha = 0.40f * danger), Color.Transparent), startY = 0f, endY = cs * 5f), size = Size(w, cs * 5f))
    }
    // the drop trail
    if (fx.trailP < 1f && fx.trailY1 > fx.trailY0) {
        val top = fx.trailY0 * cs
        val bottom = (fx.trailY1 + 1) * cs
        drawRect(
            brush = Brush.verticalGradient(listOf(Color.Transparent, Color.White.copy(alpha = 0.34f * (1f - fx.trailP))), startY = top, endY = bottom),
            topLeft = Offset(fx.trailX0 * cs, top), size = Size((fx.trailX1 - fx.trailX0 + 1) * cs, bottom - top),
        )
    }
    if (type in 0..7) {
        val s = TetrisRules.SHAPE[type][rot and 3]
        val count = if (type == TetrisRules.BOMB_TYPE) 1 else 4
        if (ghostY != null && ghostY != py) {
            val gc = if (type == TetrisRules.BOMB_TYPE) Color(0xFFFF6A6A) else pieceColour(if (gift) TetrisRules.GIFT else type + 1)
            for (i in 0 until count) {
                val yy = ghostY + s[i * 2 + 1] - TetrisRules.HIDDEN
                if (yy >= 0) {
                    val gx = (px + s[i * 2]) * cs
                    val gy = yy * cs
                    drawRoundRect(color = gc.copy(alpha = 0.10f), topLeft = Offset(gx + cs * 0.08f, gy + cs * 0.08f), size = Size(cs * 0.84f, cs * 0.84f), cornerRadius = CornerRadius(cs * 0.2f))
                    drawRoundRect(
                        color = gc.copy(alpha = 0.60f), topLeft = Offset(gx + cs * 0.08f, gy + cs * 0.08f), size = Size(cs * 0.84f, cs * 0.84f),
                        cornerRadius = CornerRadius(cs * 0.2f), style = Stroke(width = cs * 0.07f),
                    )
                }
            }
        }
        if (type == TetrisRules.BOMB_TYPE) {
            val yy = py - TetrisRules.HIDDEN
            if (yy >= 0) bombSprite(px * cs, yy * cs, cs, a, fx.pulse)
        } else {
            for (i in 0 until 4) {
                val yy = py + s[i * 2 + 1] - TetrisRules.HIDDEN
                if (yy >= 0) {
                    val code = if (gift) TetrisRules.GIFT else type + 1 + (if (i == star) TetrisRules.STAR else 0)
                    brick((px + s[i * 2]) * cs, yy * cs, cs, code, skin, a)
                    if (fx.lockGlow > 0f) {
                        drawRoundRect(
                            color = Color.White.copy(alpha = 0.35f * fx.lockGlow), topLeft = Offset((px + s[i * 2]) * cs + cs * 0.05f, yy * cs + cs * 0.05f),
                            size = Size(cs * 0.9f, cs * 0.9f), cornerRadius = CornerRadius(cs * 0.2f),
                        )
                    }
                }
            }
        }
    }
    // cleared lines: a bright sweep along each row, and the blocks themselves burst apart
    if (fx.clearP < 1f) {
        val p = fx.clearP
        for (row in fx.flashRows) {
            drawRect(Color.White.copy(alpha = 0.55f * (1f - p) * (1f - p)), Offset(0f, row * cs), Size(w, cs))
            val cx = -w * 0.2f + p * w * 1.5f
            drawRect(
                brush = Brush.horizontalGradient(listOf(Color.Transparent, Color.White.copy(alpha = 0.9f * (1f - p)), Color.Transparent), startX = cx - w * 0.25f, endX = cx + w * 0.25f),
                topLeft = Offset(0f, row * cs), size = Size(w, cs),
            )
        }
        for (b in fx.cleared) {
            val col = pieceColour(b.code).lighten(0.25f)
            for (k in 0 until 3) {
                val r1 = hash01(b.x, b.y, k * 3 + 1)
                val r2 = hash01(b.x, b.y, k * 3 + 2)
                val r3 = hash01(b.x, b.y, k * 3 + 3)
                val dx = (r1 - 0.5f) * 7f * p
                val dy = (-r2 * 3.5f) * p + 7f * p * p
                val sz = cs * (0.12f + 0.16f * r3) * (1f - p * 0.6f)
                drawRoundRect(
                    color = col.copy(alpha = ((1f - p) * (1f - p)).coerceIn(0f, 1f)),
                    topLeft = Offset((b.x + 0.5f + dx) * cs - sz / 2, (b.y + 0.5f + dy) * cs - sz / 2), size = Size(sz, sz), cornerRadius = CornerRadius(sz * 0.3f),
                )
            }
        }
    }
    if (fx.blastP < 1f && fx.blastX >= 0) {
        drawExplosion(Offset((fx.blastX + 0.5f) * cs, (fx.blastY + 0.5f) * cs), cs * 3.6f, fx.blastP)
    }
    if (fx.fog) {
        drawRect(Brush.verticalGradient(listOf(Color(0xF2C9D6F2), Color(0xB3C9D6F2), Color.Transparent), 0f, h * 0.62f))
    }
    drawRoundRect(color = fx.accent.copy(alpha = 0.55f), size = size, cornerRadius = CornerRadius(cs * 0.35f), style = Stroke(width = max(2f, cs * 0.08f)))
}

/** A small preview of one piece (hold and next). */
@Composable
fun PieceSwatch(type: Int, modifier: Modifier = Modifier, skin: Int, gift: Boolean = false, dim: Boolean = false) {
    Canvas(modifier.aspectRatio(1f)) {
        drawRoundRect(color = Color(0x40000000), cornerRadius = CornerRadius(size.width * 0.16f))
        if (type !in 0..7) return@Canvas
        val alpha = if (dim) 0.4f else 1f
        if (type == TetrisRules.BOMB_TYPE) {
            bombSprite(size.width * 0.1f, size.height * 0.1f, size.width * 0.8f, alpha)
            return@Canvas
        }
        val s = TetrisRules.SHAPE[type][0]
        val xs = (0 until 4).map { s[it * 2] }
        val ys = (0 until 4).map { s[it * 2 + 1] }
        val cs = size.width / 5f
        val ox = (size.width - (xs.max() - xs.min() + 1) * cs) / 2f - xs.min() * cs
        val oy = (size.height - (ys.max() - ys.min() + 1) * cs) / 2f - ys.min() * cs
        for (i in 0 until 4) brick(ox + s[i * 2] * cs, oy + s[i * 2 + 1] * cs, cs, if (gift) TetrisRules.GIFT else type + 1, skin, alpha)
    }
}

/** A row of sample bricks, so the style can be judged in the settings. */
@Composable
fun SkinPreview(skin: Int, modifier: Modifier = Modifier) {
    Canvas(modifier.aspectRatio(9f)) {
        val cs = min(size.height, size.width / 9f)
        val codes = listOf(1, 2, 3, 4, 5, 6, 7, TetrisRules.GIFT, 3 + TetrisRules.STAR)
        codes.forEachIndexed { i, c -> brick(i * cs, 0f, cs, c, skin) }
    }
}
