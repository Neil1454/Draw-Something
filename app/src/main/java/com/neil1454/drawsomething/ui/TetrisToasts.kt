package com.neil1454.drawsomething.ui

import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.LinearEasing
import androidx.compose.animation.core.tween
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.key
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Shadow
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp

/**
 * The in-game announcements. They replace the old emoji pop-ups with calm, readable banners:
 *  - a BIG banner (a four-line clear, a perfect clear, a curse) sits across the middle of the board;
 *  - SMALL pills (a single, a combo, gifts coming) stack under the scoreboard and never cover the pieces.
 */
class Toast(
    val id: Long,
    val title: String,
    val sub: String,
    val accent: Color,
    val big: Boolean,
)

private fun smooth(x: Float): Float = x * x * (3f - 2f * x)

@Composable
fun ToastHost(toasts: List<Toast>, onDone: (Long) -> Unit, modifier: Modifier = Modifier) {
    Box(modifier) {
        // big banners: the newest one in the middle
        toasts.filter { it.big }.takeLast(2).forEachIndexed { index, t ->
            key(t.id) {
                Box(Modifier.align(Alignment.Center).padding(top = (index * 56).dp)) { BigToast(t, onDone) }
            }
        }
        // small pills: a short stack at the top
        Column(
            Modifier.align(Alignment.TopCenter).padding(top = 6.dp),
            verticalArrangement = Arrangement.spacedBy(6.dp),
            horizontalAlignment = Alignment.CenterHorizontally,
        ) {
            toasts.filter { !it.big }.takeLast(3).forEach { t -> key(t.id) { SmallToast(t, onDone) } }
        }
    }
}

@Composable
private fun BigToast(t: Toast, onDone: (Long) -> Unit) {
    val p = remember { Animatable(0f) }
    LaunchedEffect(t.id) {
        p.animateTo(1f, tween(1700, easing = LinearEasing))
        onDone(t.id)
    }
    val v = p.value
    val fadeIn = smooth((v / 0.10f).coerceIn(0f, 1f))
    val fadeOut = 1f - smooth(((v - 0.72f) / 0.28f).coerceIn(0f, 1f))
    val alpha = fadeIn * fadeOut
    val scale = 0.92f + 0.08f * fadeIn
    val underline = 40f + 120f * smooth((v / 0.35f).coerceIn(0f, 1f))
    Column(
        Modifier
            .graphicsLayer {
                this.alpha = alpha
                scaleX = scale
                scaleY = scale
                translationY = -18f * smooth(((v - 0.6f) / 0.4f).coerceIn(0f, 1f)) * density
            }
            .fillMaxWidth()
            .background(Brush.horizontalGradient(listOf(Color.Transparent, Color(0xCC050816), Color(0xCC050816), Color.Transparent)))
            .padding(vertical = 10.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.spacedBy(5.dp),
    ) {
        Text(
            t.title,
            style = TextStyle(
                color = Color.White, fontSize = 32.sp, fontWeight = FontWeight.Black, letterSpacing = 5.sp,
                textAlign = TextAlign.Center, shadow = Shadow(t.accent.copy(alpha = 0.9f), Offset(0f, 0f), 22f),
            ),
        )
        Box(
            Modifier.width(underline.dp).height(3.dp).clip(RoundedCornerShape(2.dp))
                .background(Brush.horizontalGradient(listOf(Color.Transparent, t.accent, Color.Transparent))),
        )
        if (t.sub.isNotBlank()) {
            Text(
                t.sub, fontSize = 12.sp, fontWeight = FontWeight.SemiBold, letterSpacing = 1.6.sp,
                color = t.accent.lighten(0.45f), textAlign = TextAlign.Center,
            )
        }
    }
}

@Composable
private fun SmallToast(t: Toast, onDone: (Long) -> Unit) {
    val p = remember { Animatable(0f) }
    LaunchedEffect(t.id) {
        p.animateTo(1f, tween(2100, easing = LinearEasing))
        onDone(t.id)
    }
    val v = p.value
    val fadeIn = smooth((v / 0.10f).coerceIn(0f, 1f))
    val fadeOut = 1f - smooth(((v - 0.78f) / 0.22f).coerceIn(0f, 1f))
    val shape = RoundedCornerShape(14.dp)
    Row(
        Modifier
            .graphicsLayer {
                alpha = fadeIn * fadeOut
                translationY = -10f * (1f - fadeIn) * density
            }
            .clip(shape)
            .background(Brush.horizontalGradient(listOf(Color(0xE60B1030), Color(0xE6141A44))))
            .border(1.dp, t.accent.copy(alpha = 0.65f), shape)
            .padding(horizontal = 12.dp, vertical = 6.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(8.dp),
    ) {
        Box(Modifier.size(8.dp).clip(CircleShape).background(t.accent))
        Column {
            Text(t.title, fontSize = 12.sp, fontWeight = FontWeight.ExtraBold, letterSpacing = 1.2.sp, color = Color.White)
            if (t.sub.isNotBlank()) Text(t.sub, fontSize = 10.sp, color = Color.White.copy(alpha = 0.72f))
        }
    }
}
