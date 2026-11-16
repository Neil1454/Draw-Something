package com.neil1454.drawsomething.ui

import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.LinearEasing
import androidx.compose.animation.core.Spring
import androidx.compose.animation.core.spring
import androidx.compose.animation.core.tween
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableLongStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.draw.scale
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import kotlinx.coroutines.delay
import androidx.compose.runtime.withFrameNanos
import kotlin.math.cos
import kotlin.math.sin

/**
 * A big celebration across the middle of the screen when a game has a clear winner: a ribbon sweeps in with a bouncing
 * trophy, confetti rains down and the whole thing glows, then it fades away by itself. It never blocks touches.
 * [result]: 1 I won, 2 they won, 3 a draw.
 */
@Composable
fun WinnerBanner(result: Int, partnerName: String, solo: Boolean, modifier: Modifier = Modifier) {
    val enter = remember(result) { Animatable(0f) }
    val fade = remember(result) { Animatable(1f) }
    var clock by remember(result) { mutableLongStateOf(0L) }
    LaunchedEffect(result) {
        enter.animateTo(1f, spring(dampingRatio = 0.45f, stiffness = Spring.StiffnessLow))
    }
    LaunchedEffect(result) {
        val start = withFrameNanos { it }
        while (true) { clock = (withFrameNanos { it } - start) / 1_000_000L }
    }
    LaunchedEffect(result) {
        delay(if (result == 3) 3200L else 4800L)
        fade.animateTo(0f, tween(700, easing = LinearEasing))
    }
    if (fade.value <= 0.01f) return
    val t = clock / 1000f
    val opp = if (solo || partnerName.isBlank()) "The computer" else partnerName
    val (title, sub, top, bottom, emoji) = when (result) {
        1 -> Five("YOU WIN!", "What a game!", Color(0xFFFFD23F), Color(0xFFFF7A2F), "🏆")
        2 -> Five("${opp.uppercase()} WINS!", "So close. Fancy a rematch?", Color(0xFF3D8BFF), Color(0xFF8B5CF6), "👑")
        else -> Five("IT'S A DRAW!", "Evenly matched.", Color(0xFF2DE2A6), Color(0xFF1EE6FF), "🤝")
    }
    Box(modifier.fillMaxSize().alpha(fade.value)) {
        // confetti falls over the whole screen
        Canvas(Modifier.fillMaxSize()) {
            val n = 70
            val colours = listOf(Color(0xFFFFD23F), Color(0xFFFF3D9A), Color(0xFF1EE6FF), Color(0xFFB6FF3B), Color(0xFF8B5CF6), Color(0xFFFF7A2F))
            for (i in 0 until n) {
                val seed = i * 37.7f
                val x = ((sin(seed) * 0.5f + 0.5f) * size.width + sin(t * 2f + seed) * 18f)
                val fall = (t * (0.18f + (i % 5) * 0.05f) + (i % 11) / 11f) % 1f
                val y = fall * (size.height + 40f) - 20f
                val w = 7f + (i % 4) * 3f
                val spin = t * (3f + i % 4) + seed
                val c = colours[i % colours.size].copy(alpha = if (result == 2) 0.55f else 0.95f)
                graphicsLayerRect(x, y, w, spin, c)
            }
        }
        Column(
            Modifier.align(Alignment.Center).fillMaxWidth().graphicsLayer {
                scaleX = enter.value.coerceIn(0f, 1.25f); scaleY = enter.value.coerceIn(0f, 1.25f); rotationZ = (1f - enter.value) * -8f
            },
            horizontalAlignment = Alignment.CenterHorizontally,
        ) {
            Text0(emoji, 76f, Modifier.scale(1f + 0.12f * sin(t * 5f)).graphicsLayer { translationY = -32f * kotlin.math.abs(sin(t * 4f)) })
            Box(
                Modifier.fillMaxWidth().padding(vertical = 4.dp)
                    .background(Brush.horizontalGradient(listOf(Color.Transparent, top.copy(alpha = 0.95f), bottom.copy(alpha = 0.95f), top.copy(alpha = 0.95f), Color.Transparent)))
                    .padding(vertical = 14.dp),
                contentAlignment = Alignment.Center,
            ) {
                // a bright glint sweeps across the ribbon
                Canvas(Modifier.matchParentSize()) {
                    val sweep = ((t * 0.7f) % 1.6f - 0.3f) * size.width
                    drawRect(Brush.horizontalGradient(listOf(Color.Transparent, Color.White.copy(alpha = 0.55f), Color.Transparent), sweep - 80f, sweep + 80f), Offset.Zero, Size(size.width, size.height))
                }
                Column(horizontalAlignment = Alignment.CenterHorizontally) {
                    Text0(title, 40f + 2f * sin(t * 6f), Modifier, Color(0xFF140B33), FontWeight.Black, 2f)
                    Text0(sub, 15f, Modifier, Color(0xFF140B33).copy(alpha = 0.8f), FontWeight.Bold)
                }
            }
        }
    }
}

private data class Five(val a: String, val b: String, val c: Color, val d: Color, val e: String)

private fun androidx.compose.ui.graphics.drawscope.DrawScope.graphicsLayerRect(x: Float, y: Float, w: Float, spin: Float, c: Color) {
    val h = w * (0.4f + 0.6f * kotlin.math.abs(cos(spin)))
    drawRoundRect(c, Offset(x - w / 2f, y - h / 2f), Size(w, h), androidx.compose.ui.geometry.CornerRadius(2f))
}

@Composable
private fun Text0(text: String, size: Float, modifier: Modifier, color: Color = Color.White, weight: FontWeight = FontWeight.Normal, spacing: Float = 0f) {
    androidx.compose.material3.Text(text, modifier, color = color, fontSize = size.sp, fontWeight = weight, letterSpacing = spacing.sp, textAlign = TextAlign.Center)
}
