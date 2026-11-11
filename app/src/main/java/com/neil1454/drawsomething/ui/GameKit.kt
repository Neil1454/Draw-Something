package com.neil1454.drawsomething.ui

import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.runtime.mutableStateOf
import com.neil1454.drawsomething.core.Sound
import com.neil1454.drawsomething.sound.Sfx
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
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import kotlinx.coroutines.delay
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
    dot: Color? = null,
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
        if (dot != null) {
            Box(Modifier.size(24.dp).clip(CircleShape).background(dot).border(2.dp, Color.White.copy(alpha = 0.7f), CircleShape))
        }
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

/**
 * Full-screen results card with confetti for a win. [how] explains how the game was won in plain words,
 * and "See the board" tucks the card away so the finished board (winning line, final squares) can be looked at.
 */
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
    how: String = "",
    /** How long the finished board is shown on its own, with just a banner saying how it was won, before the results card. */
    revealMs: Long = 3500L,
) {
    var confetti by remember { mutableIntStateOf(0) }
    var peek by remember { mutableStateOf(false) }
    var revealing by remember { mutableStateOf(revealMs > 0L) }
    LaunchedEffect(Unit) {
        if (revealing) {
            delay(revealMs)
            revealing = false
        }
    }
    LaunchedEffect(win, revealing) { if (!revealing) { if (win) confetti++ else Sfx.play(Sound.LOSE) } }
    if (revealing) {
        // The game has just ended: leave the board in plain view (winning line, last move) with a banner saying how.
        Box(Modifier.fillMaxSize(), contentAlignment = Alignment.BottomCenter) {
            Row(
                Modifier.fillMaxWidth().background(Color(0xE60B0624)).padding(horizontal = 12.dp, vertical = 10.dp),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(10.dp),
            ) {
                Column(Modifier.weight(1f)) {
                    Text(title, fontSize = 18.sp, fontWeight = FontWeight.Black, color = if (win) Funk.Sun else Funk.Pink, maxLines = 1)
                    if (how.isNotBlank()) Text(how, fontSize = 13.sp, color = Color.White.copy(alpha = 0.92f), maxLines = 4)
                }
                FunkyButton("Skip", { revealing = false }, Modifier, listOf(Funk.Violet, Funk.Pink), fontSize = 13.sp)
            }
        }
        return
    }
    if (peek) {
        // Just a slim bar, so the board underneath is fully visible.
        Box(Modifier.fillMaxSize(), contentAlignment = Alignment.BottomCenter) {
            Row(
                Modifier.fillMaxWidth().background(Color(0xE60B0624)).padding(horizontal = 12.dp, vertical = 8.dp),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(10.dp),
            ) {
                Column(Modifier.weight(1f)) {
                    Text(title, fontSize = 15.sp, fontWeight = FontWeight.Black, color = Color.White, maxLines = 1)
                    if (how.isNotBlank()) Text(how, fontSize = 12.sp, color = Color.White.copy(alpha = 0.85f), maxLines = 3)
                }
                FunkyButton("Results", { peek = false }, Modifier, listOf(Funk.Violet, Funk.Pink), fontSize = 14.sp)
            }
        }
        return
    }
    Box(Modifier.fillMaxSize().background(Color(0xD90B0624)), contentAlignment = Alignment.Center) {
        ConfettiBurst(confetti)
        Column(
            Modifier.padding(24.dp).verticalScroll(rememberScrollState()),
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.spacedBy(12.dp),
        ) {
            GradientText(title, fontSize = 36.sp, colors = if (win) listOf(Funk.Sun, Funk.Lime, Funk.Cyan) else listOf(Funk.Pink, Funk.Violet, Funk.Blue))
            Text(subtitle, fontSize = 18.sp, fontWeight = FontWeight.Bold, textAlign = TextAlign.Center)
            if (how.isNotBlank()) {
                Text(
                    how,
                    Modifier.fillMaxWidth().clip(RoundedCornerShape(16.dp)).background(Color(0x33FFFFFF)).padding(12.dp),
                    fontSize = 15.sp, color = Funk.Sun, textAlign = TextAlign.Center, fontWeight = FontWeight.SemiBold,
                )
            }
            FunkyButton("👀  See the board", { peek = true }, Modifier.fillMaxWidth(), listOf(Funk.Cyan, Funk.Blue))
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
