package com.neil1454.drawsomething.ui

import androidx.compose.animation.core.LinearEasing
import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.tween
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.neil1454.drawsomething.core.Sound
import com.neil1454.drawsomething.sound.Sfx

private val SplashEmoji = listOf("🎨", "💣", "🚢", "🔴", "🃏", "⚡", "🎲", "🎴", "🏝️")

/** Shown when the app opens. It stays until the player taps the screen. */
@Composable
fun SplashScreen(onDone: () -> Unit) {
    val t = rememberInfiniteTransition(label = "splash")
    val wave by t.animateFloat(0f, 1f, infiniteRepeatable(tween(1400, easing = LinearEasing), RepeatMode.Restart), label = "wave")
    val pulse by t.animateFloat(0.96f, 1.06f, infiniteRepeatable(tween(1100), RepeatMode.Reverse), label = "pulse")
    val blink by t.animateFloat(0.35f, 1f, infiniteRepeatable(tween(900), RepeatMode.Reverse), label = "blink")
    Box(Modifier.fillMaxSize().clickable { Sfx.play(Sound.WHOOSH); onDone() }, contentAlignment = Alignment.Center) {
        Column(
            Modifier.padding(24.dp),
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.spacedBy(10.dp),
        ) {
            Row(horizontalArrangement = Arrangement.spacedBy(2.dp)) {
                SplashEmoji.forEachIndexed { i, e ->
                    val phase = (wave + i / SplashEmoji.size.toFloat()) % 1f
                    val bounce = kotlin.math.sin(phase * 2f * Math.PI.toFloat())
                    Text(e, Modifier.graphicsLayer { translationY = -10f * density * bounce }, fontSize = 26.sp)
                }
            }
            Spacer(Modifier.height(6.dp))
            GradientText(
                APP_NAME,
                Modifier.graphicsLayer {
                    scaleX = pulse
                    scaleY = pulse
                },
                fontSize = 38.sp,
            )
            Text(
                "Made by $CREATOR",
                style = MaterialTheme.typography.titleLarge,
                fontWeight = FontWeight.Bold,
                color = Color.White,
            )
            Spacer(Modifier.height(12.dp))
            Text(
                APP_TAGLINE,
                style = MaterialTheme.typography.titleMedium,
                fontWeight = FontWeight.SemiBold,
                color = Funk.Sun,
                textAlign = TextAlign.Center,
            )
            Spacer(Modifier.height(10.dp))
            Text(
                "✈️ Made to keep us busy on the 4-hour flight",
                style = MaterialTheme.typography.titleMedium,
                color = Color.White,
                textAlign = TextAlign.Center,
            )
            Text(
                "☀️ Holiday mode: on 🌴",
                style = MaterialTheme.typography.titleMedium,
                fontWeight = FontWeight.Bold,
                color = Funk.Cyan,
                textAlign = TextAlign.Center,
            )
            Text(
                "Sketch pads ready.",
                style = MaterialTheme.typography.bodyMedium,
                color = Color.White.copy(alpha = 0.85f),
                textAlign = TextAlign.Center,
            )
            Spacer(Modifier.height(28.dp))
            Text(
                "👆 Tap anywhere to start",
                style = MaterialTheme.typography.titleMedium,
                fontWeight = FontWeight.Bold,
                color = Color.White.copy(alpha = blink),
            )
        }
    }
}
