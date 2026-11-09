package com.neil1454.drawsomething.ui

import android.app.Activity
import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.LinearEasing
import androidx.compose.animation.core.LinearOutSlowInEasing
import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.tween
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.interaction.collectIsPressedAsState
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxScope
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.defaultMinSize
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.key
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Shadow
import androidx.compose.ui.graphics.drawscope.DrawScope
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.drawscope.rotate
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.platform.LocalView
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.TextUnit
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.core.view.WindowCompat
import androidx.core.view.WindowInsetsCompat
import androidx.core.view.WindowInsetsControllerCompat
import com.neil1454.drawsomething.core.GameKind
import kotlin.math.PI
import kotlin.math.cos
import kotlin.math.sin

// ======================================================================== background

/** Deep-indigo gradient with big soft colour blobs that slowly drift. */
@Composable
fun FunkyBackground(modifier: Modifier = Modifier, content: @Composable BoxScope.() -> Unit) {
    val transition = rememberInfiniteTransition(label = "bg")
    val t by transition.animateFloat(
        initialValue = 0f, targetValue = 1f,
        animationSpec = infiniteRepeatable(tween(30_000, easing = LinearEasing)),
        label = "bgt",
    )
    Box(modifier.background(Brush.verticalGradient(listOf(Funk.Ink, Funk.Night, Funk.Grape)))) {
        Canvas(Modifier.fillMaxSize()) {
            val w = size.width
            val h = size.height
            fun blob(color: Color, cx: Float, cy: Float, r: Float, phase: Float) {
                val a = (t + phase) * 2f * PI.toFloat()
                val c = Offset(cx * w + sin(a) * w * 0.08f, cy * h + cos(a * 0.8f) * h * 0.05f)
                drawCircle(
                    Brush.radialGradient(listOf(color.copy(alpha = 0.34f), Color.Transparent), center = c, radius = r),
                    r, c,
                )
            }
            blob(Funk.Pink, 0.05f, 0.12f, w * 0.75f, 0f)
            blob(Funk.Cyan, 1.0f, 0.45f, w * 0.7f, 0.33f)
            blob(Funk.Violet, 0.3f, 1.0f, w * 0.85f, 0.66f)
        }
        content()
    }
}

// ======================================================================== buttons and tiles

@Composable
fun FunkyButton(
    text: String,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    colors: List<Color> = listOf(Funk.Pink, Funk.Orange),
    enabled: Boolean = true,
    fontSize: TextUnit = 18.sp,
) {
    val source = remember { MutableInteractionSource() }
    val pressed by source.collectIsPressedAsState()
    val scale by animateFloatAsState(if (pressed) 0.95f else 1f, label = "press")
    val shape = RoundedCornerShape(22.dp)
    Box(
        modifier
            .graphicsLayer {
                scaleX = scale
                scaleY = scale
                alpha = if (enabled) 1f else 0.45f
            }
            .clip(shape)
            .background(Brush.horizontalGradient(if (enabled) colors else listOf(Color(0xFF6A5FA8), Color(0xFF55498F))))
            .border(2.dp, Color.White.copy(alpha = 0.38f), shape)
            .clickable(interactionSource = source, indication = null, enabled = enabled, onClick = onClick)
            .defaultMinSize(minHeight = 54.dp)
            .padding(horizontal = 22.dp, vertical = 12.dp),
        contentAlignment = Alignment.Center,
    ) {
        Text(
            text,
            fontSize = fontSize,
            fontWeight = FontWeight.ExtraBold,
            color = Color.White,
            textAlign = TextAlign.Center,
            style = TextStyle(shadow = Shadow(Color(0x66000000), Offset(0f, 3f), 6f)),
        )
    }
}

/** Gradient used for each game's tile, header and effects. */
fun GameKind.colors(): List<Color> = when (this) {
    GameKind.DRAW -> listOf(Funk.Pink, Funk.Orange)
    GameKind.MINES -> listOf(Funk.Orange, Funk.Red)
    GameKind.SEA -> listOf(Funk.Cyan, Funk.Blue)
    GameKind.DROP -> listOf(Funk.Red, Color(0xFFE6A100))
    GameKind.MEMORY -> listOf(Funk.Violet, Funk.Pink)
    GameKind.QUICK -> listOf(Color(0xFF7BD100), Color(0xFF1FA85A))
    GameKind.DICE -> listOf(Funk.Mint, Color(0xFF0E8F9F))
    GameKind.FLIP -> listOf(Color(0xFF4B35B0), Funk.Blue)
    GameKind.HILO -> listOf(Color(0xFF0E8A56), Color(0xFFB88A00))
}

@Composable
fun GameTile(kind: GameKind, selected: Boolean, enabled: Boolean, modifier: Modifier = Modifier, onClick: () -> Unit) {
    val scale by animateFloatAsState(if (selected) 1.04f else 1f, label = "tile")
    val shape = RoundedCornerShape(24.dp)
    Box(
        modifier
            .graphicsLayer {
                scaleX = scale
                scaleY = scale
            }
            .clip(shape)
            .background(Brush.linearGradient(kind.colors()))
            .border(if (selected) 4.dp else 1.5.dp, if (selected) Color.White else Color.White.copy(alpha = 0.35f), shape)
            .clickable(enabled = enabled, onClick = onClick)
            .heightIn(min = 138.dp)
            .padding(14.dp),
    ) {
        Column {
            Text(kind.emoji, fontSize = 40.sp)
            Text(kind.label, fontSize = 17.sp, fontWeight = FontWeight.Black, color = Color.White)
            Text(kind.blurb, fontSize = 11.sp, color = Color.White.copy(alpha = 0.92f), lineHeight = 14.sp)
        }
        if (selected) {
            Text("✓", Modifier.align(Alignment.TopEnd), fontSize = 22.sp, fontWeight = FontWeight.Black, color = Color.White)
        }
    }
}

/** All games as a two-column grid of colourful tiles. */
@Composable
fun GameGrid(
    kinds: List<GameKind>,
    selected: GameKind?,
    enabled: Boolean,
    onClick: (GameKind) -> Unit,
) {
    Column(Modifier.fillMaxWidth(), verticalArrangement = Arrangement.spacedBy(10.dp)) {
        kinds.chunked(2).forEach { row ->
            Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                row.forEach { k ->
                    GameTile(k, selected == k, enabled, Modifier.weight(1f)) { onClick(k) }
                }
                if (row.size == 1) Box(Modifier.weight(1f))
            }
        }
    }
}

/** Text filled with a colour gradient (titles). */
@Composable
fun GradientText(text: String, modifier: Modifier = Modifier, fontSize: TextUnit = 34.sp, colors: List<Color> = listOf(Funk.Sun, Funk.Pink, Funk.Cyan)) {
    Text(
        text,
        modifier,
        textAlign = TextAlign.Center,
        style = TextStyle(
            brush = Brush.horizontalGradient(colors),
            fontSize = fontSize,
            fontWeight = FontWeight.Black,
            lineHeight = fontSize * 1.1f,
            shadow = Shadow(Color(0x55000000), Offset(0f, 4f), 10f),
        ),
    )
}

// ======================================================================== effects

/** A burst: bright flash, shock rings and flying sparks. [p] runs 0..1. */
fun DrawScope.drawExplosion(center: Offset, maxR: Float, p: Float) {
    if (p <= 0f || p >= 1f) return
    val fade = 1f - p
    val flashR = maxR * (0.5f + p)
    drawCircle(
        Brush.radialGradient(
            listOf(Color.White.copy(alpha = fade), Funk.Sun.copy(alpha = fade * 0.9f), Funk.Orange.copy(alpha = fade * 0.5f), Color.Transparent),
            center = center, radius = flashR,
        ),
        flashR, center,
    )
    drawCircle(Funk.Sun.copy(alpha = fade), radius = maxR * p * 1.5f, center = center, style = Stroke(width = maxR * 0.14f * fade + 1f))
    drawCircle(Funk.Red.copy(alpha = fade * 0.85f), radius = maxR * p * 1.05f, center = center, style = Stroke(width = maxR * 0.1f * fade + 1f))
    val sparkColors = listOf(Funk.Sun, Funk.Orange, Funk.Red, Color.White)
    for (i in 0 until 16) {
        val ang = i * 2f * PI.toFloat() / 16f + 0.35f * i
        val dist = maxR * (0.3f + 1.5f * p) * (0.65f + 0.35f * (((i * 37) % 10) / 10f))
        val pos = center + Offset(cos(ang) * dist, sin(ang) * dist)
        drawCircle(sparkColors[i % sparkColors.size].copy(alpha = fade), radius = maxR * 0.09f * fade + 1f, center = pos)
    }
}

/** A softer burst for good things: sparkles in the given colours. */
fun DrawScope.drawSparkle(center: Offset, maxR: Float, p: Float, colors: List<Color>) {
    if (p <= 0f || p >= 1f) return
    val fade = 1f - p
    drawCircle(colors[0].copy(alpha = fade * 0.55f), radius = maxR * (0.4f + p), center = center, style = Stroke(width = maxR * 0.1f * fade + 1f))
    for (i in 0 until 10) {
        val ang = i * 2f * PI.toFloat() / 10f
        val dist = maxR * (0.2f + 1.1f * p)
        val pos = center + Offset(cos(ang) * dist, sin(ang) * dist)
        drawCircle(colors[i % colors.size].copy(alpha = fade), radius = maxR * 0.07f * fade + 1f, center = pos)
    }
}

/** A water splash: concentric ripples. */
fun DrawScope.drawSplash(center: Offset, maxR: Float, p: Float) {
    if (p <= 0f || p >= 1f) return
    val fade = 1f - p
    drawCircle(Color.White.copy(alpha = fade * 0.9f), radius = maxR * (0.3f + 1.2f * p), center = center, style = Stroke(width = maxR * 0.1f * fade + 1f))
    drawCircle(Funk.Cyan.copy(alpha = fade * 0.8f), radius = maxR * (0.15f + 0.8f * p), center = center, style = Stroke(width = maxR * 0.08f * fade + 1f))
    for (i in 0 until 8) {
        val ang = i * 2f * PI.toFloat() / 8f
        val dist = maxR * (0.2f + 0.9f * p)
        drawCircle(Color.White.copy(alpha = fade), radius = maxR * 0.06f * fade + 1f, center = center + Offset(cos(ang) * dist, sin(ang) * dist))
    }
}

private val ConfettiColours = listOf(Funk.Pink, Funk.Sun, Funk.Cyan, Funk.Lime, Funk.Orange, Funk.Violet, Color.White)

fun DrawScope.drawConfetti(p: Float) {
    if (p <= 0f || p >= 1f) return
    val w = size.width
    val h = size.height
    for (i in 0 until 90) {
        val seedX = (i * 0.61803f) % 1f
        val seedS = ((i * 0.37f) % 1f)
        val x = seedX * w + sin(p * 9f + i) * w * 0.03f
        val y = -30f + p * (h + 90f) * (0.6f + 0.7f * seedS)
        val piece = 9f + 10f * seedS
        rotate(degrees = p * 540f * (0.4f + seedS) + i * 20f, pivot = Offset(x, y)) {
            drawRect(
                ConfettiColours[i % ConfettiColours.size].copy(alpha = (1f - p * p).coerceIn(0f, 1f)),
                topLeft = Offset(x - piece / 2, y - piece / 4), size = androidx.compose.ui.geometry.Size(piece, piece / 2),
            )
        }
    }
}

/** Full-screen confetti that plays each time [trigger] changes (and is above zero). */
@Composable
fun ConfettiBurst(trigger: Int, modifier: Modifier = Modifier) {
    val p = remember { Animatable(1f) }
    LaunchedEffect(trigger) {
        if (trigger > 0) {
            p.snapTo(0f)
            p.animateTo(1f, tween(2800, easing = LinearEasing))
        }
    }
    Canvas(modifier.fillMaxSize()) { drawConfetti(p.value) }
}

/** 0..1 animation value that restarts every time [trigger] changes (and is above zero). Rests at 1. */
@Composable
fun rememberBurst(trigger: Int, millis: Int = 900): Float {
    val a = remember { Animatable(1f) }
    LaunchedEffect(trigger) {
        if (trigger > 0) {
            a.snapTo(0f)
            a.animateTo(1f, tween(millis, easing = LinearOutSlowInEasing))
        }
    }
    return a.value
}

/** Screen shake while [p] runs from 0 to 1. */
fun Modifier.shaking(p: Float, amplitude: Float = 12f): Modifier = graphicsLayer {
    if (p < 1f) {
        translationX = sin(p * 38f) * (1f - p) * amplitude * density
        translationY = cos(p * 31f) * (1f - p) * amplitude * 0.6f * density
    }
}

// ---------------------------------------------------------------------- flashing score popups

data class Popup(val id: Long, val text: String, val color: Color, val xFrac: Float = 0.5f, val yFrac: Float = 0.45f)

/** Text that pops, floats up and fades. Call [onDone] to remove it from your list. */
@Composable
fun PopupLayer(popups: List<Popup>, onDone: (Long) -> Unit, modifier: Modifier = Modifier) {
    BoxWithConstraints(modifier.fillMaxSize()) {
        popups.forEach { p -> key(p.id) { PopupText(p, maxWidth, maxHeight, onDone) } }
    }
}

@Composable
private fun PopupText(p: Popup, w: Dp, h: Dp, onDone: (Long) -> Unit) {
    val prog = remember { Animatable(0f) }
    LaunchedEffect(p.id) {
        prog.animateTo(1f, tween(1500, easing = LinearOutSlowInEasing))
        onDone(p.id)
    }
    val a = prog.value
    val pop = if (a < 0.15f) 0.6f + (a / 0.15f) * 0.6f else 1.2f - (a - 0.15f) * 0.25f
    Text(
        p.text,
        Modifier
            .offset(x = w * p.xFrac - 90.dp, y = h * p.yFrac - (110f * a).dp)
            .width(180.dp)
            .graphicsLayer {
                scaleX = pop
                scaleY = pop
                alpha = (1f - a * a * a).coerceIn(0f, 1f)
            },
        textAlign = TextAlign.Center,
        style = TextStyle(
            color = p.color,
            fontSize = 30.sp,
            fontWeight = FontWeight.Black,
            shadow = Shadow(Color(0xCC000000), Offset(0f, 4f), 8f),
        ),
    )
}

// ======================================================================== full-screen mode

/** Hides the status and navigation bars while [enabled] (swipe from an edge to peek). */
@Composable
fun ImmersiveMode(enabled: Boolean) {
    val view = LocalView.current
    DisposableEffect(enabled) {
        val window = (view.context as? Activity)?.window
        if (window != null) {
            val controller = WindowCompat.getInsetsController(window, view)
            if (enabled) {
                controller.systemBarsBehavior = WindowInsetsControllerCompat.BEHAVIOR_SHOW_TRANSIENT_BARS_BY_SWIPE
                controller.hide(WindowInsetsCompat.Type.systemBars())
            } else {
                controller.show(WindowInsetsCompat.Type.systemBars())
            }
        }
        onDispose {
            window?.let { WindowCompat.getInsetsController(it, view).show(WindowInsetsCompat.Type.systemBars()) }
        }
    }
}

/** A small round icon button used on in-game overlays. */
@Composable
fun RoundIconButton(label: String, onClick: () -> Unit, modifier: Modifier = Modifier) {
    Box(
        modifier
            .clip(RoundedCornerShape(50))
            .background(Color(0x66000000))
            .border(1.5.dp, Color.White.copy(alpha = 0.4f), RoundedCornerShape(50))
            .clickable(onClick = onClick)
            .padding(horizontal = 14.dp, vertical = 8.dp),
        contentAlignment = Alignment.Center,
    ) { Text(label, fontSize = 20.sp, color = Color.White, fontWeight = FontWeight.Bold) }
}

/** Gentle bobbing used for emoji and mascots. */
@Composable
fun bobbing(phase: Float = 0f, amount: Float = 8f, millis: Int = 1600): Float {
    val t = rememberInfiniteTransition(label = "bob")
    val v by t.animateFloat(
        initialValue = -amount, targetValue = amount,
        animationSpec = infiniteRepeatable(tween(millis, easing = androidx.compose.animation.core.FastOutSlowInEasing), RepeatMode.Reverse),
        label = "bobv",
    )
    return v * (if (phase == 0f) 1f else -1f)
}
