package com.neil1454.drawsomething.ui

import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.animateIntAsState
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.tween
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp

/** A player's score pill: name, animated score, optional extra line, glowing when it's their go. */
@Composable
fun PlayerPill(
    name: String,
    score: Int?,
    accent: Color,
    active: Boolean,
    modifier: Modifier = Modifier,
    extra: String = "",
    unit: String = "",
) {
    val shown by animateIntAsState(score ?: 0, tween(600), label = "pill")
    val shape = RoundedCornerShape(18.dp)
    Row(
        modifier
            .clip(shape)
            .background(Brush.horizontalGradient(listOf(accent.copy(alpha = if (active) 0.55f else 0.22f), Color(0x66000000))))
            .border(if (active) 3.dp else 1.dp, accent.copy(alpha = if (active) 1f else 0.4f), shape)
            .padding(horizontal = 12.dp, vertical = 6.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(8.dp),
    ) {
        Column(Modifier.weight(1f)) {
            Text(name, fontSize = if (score == null) 16.sp else 12.sp, maxLines = 1, fontWeight = if (score == null) FontWeight.Bold else FontWeight.Normal, color = Color.White.copy(alpha = 0.9f))
            if (score != null) Text("$shown$unit", fontSize = 24.sp, fontWeight = FontWeight.Black, color = Color.White)
        }
        if (extra.isNotEmpty()) Text(extra, fontSize = 16.sp)
    }
}

/** "YOUR TURN" pulses; the other player's turn is calmer. */
@Composable
fun TurnText(text: String, mine: Boolean, modifier: Modifier = Modifier) {
    val pulse = rememberInfiniteTransition(label = "turntext")
    val glow by pulse.animateFloat(0.55f, 1f, infiniteRepeatable(tween(800), RepeatMode.Reverse), label = "glow")
    Text(
        text,
        modifier,
        fontSize = 18.sp,
        fontWeight = FontWeight.Black,
        textAlign = TextAlign.Center,
        color = (if (mine) Funk.Lime else Funk.Cyan).copy(alpha = if (mine) glow else 0.9f),
    )
}

/** Full-screen results card with confetti for a win. */
@Composable
fun EndOverlay(
    title: String,
    subtitle: String,
    win: Boolean,
    isHost: Boolean,
    theirName: String,
    playAgainLabel: String,
    colors: List<Color>,
    onPlayAgain: () -> Unit,
    onBackToGames: () -> Unit,
    onLeave: () -> Unit,
) {
    var confetti by remember { mutableIntStateOf(0) }
    LaunchedEffect(win) { if (win) confetti++ }
    Box(Modifier.fillMaxSize().background(Color(0xD90B0624)), contentAlignment = Alignment.Center) {
        ConfettiBurst(confetti)
        Column(
            Modifier.padding(24.dp),
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.spacedBy(12.dp),
        ) {
            GradientText(title, fontSize = 36.sp, colors = if (win) listOf(Funk.Sun, Funk.Lime, Funk.Cyan) else listOf(Funk.Pink, Funk.Violet, Funk.Blue))
            Text(subtitle, fontSize = 18.sp, fontWeight = FontWeight.Bold, textAlign = TextAlign.Center)
            if (isHost) {
                FunkyButton("🔁  $playAgainLabel", onPlayAgain, Modifier.fillMaxWidth(), colors)
            } else {
                Text("$theirName can start a rematch…", color = MaterialTheme.colorScheme.onSurfaceVariant, textAlign = TextAlign.Center)
            }
            FunkyButton("🎮  Choose another game", onBackToGames, Modifier.fillMaxWidth(), listOf(Funk.Violet, Funk.Pink))
            TextButton(onClick = onLeave) { Text("Leave") }
        }
    }
}
