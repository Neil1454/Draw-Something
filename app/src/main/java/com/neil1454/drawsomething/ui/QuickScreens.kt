package com.neil1454.drawsomething.ui

import com.neil1454.drawsomething.core.Sound
import com.neil1454.drawsomething.sound.Sfx
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.tween
import androidx.compose.foundation.background
import androidx.compose.foundation.gestures.awaitEachGesture
import androidx.compose.foundation.gestures.awaitFirstDown
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.neil1454.drawsomething.core.GPhase
import com.neil1454.drawsomething.core.QuickEngine
import com.neil1454.drawsomething.core.QuickState

@Composable
fun QuickHelp() {
    Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
        Text("A reflex duel. The screen says WAIT, then flips to a bright GREEN “TAP!” at a random moment. First to tap wins the round.")
        Text("• Tap too early and you lose the round.")
        Text("• With fake-outs on, sometimes the screen flashes a decoy instead (yellow/orange). Only GREEN counts: tap on a decoy and you lose.")
        Text("• Each phone times your reaction from when YOUR screen changed, so a slow connection never costs you a win.")
    }
}

@Composable
fun QuickScreen(
    s: QuickState,
    engine: QuickEngine,
    onPlayAgain: () -> Unit,
    onBackToGames: () -> Unit,
    onLeave: () -> Unit,
) {
    val over = s.phase == GPhase.OVER
    LaunchedEffect(s.seq, s.step) {
        when (s.step) {
            "GO" -> Sfx.play(Sound.GO)
            "FAKE" -> Sfx.play(Sound.TICK)
            "RESULT" -> Sfx.play(if (s.roundWin == 1) Sound.GOOD else if (s.roundWin == 2) Sound.BUZZ else Sound.POP)
        }
    }
    val pulse = rememberInfiniteTransition(label = "quick")
    val beat by pulse.animateFloat(0.92f, 1.08f, infiniteRepeatable(tween(700), RepeatMode.Reverse), label = "beat")
    val colours = when (s.step) {
        "GO" -> listOf(Color(0xFF00E676), Color(0xFF00C853))
        "FAKE" -> listOf(Funk.Sun, Funk.Orange)
        "RESULT" -> when (s.roundWin) {
            1 -> listOf(Color(0xFF1B5E20), Color(0xFF2E7D32))
            2 -> listOf(Color(0xFF7A1F3A), Color(0xFF4A0D26))
            else -> listOf(Color(0xFF3A2A85), Color(0xFF241A5E))
        }
        else -> listOf(Color(0xFF3B0A5C), Color(0xFF14083A))
    }
    Box(
        Modifier
            .fillMaxSize()
            .background(Brush.verticalGradient(colours))
            .pointerInput(s.seq, s.step) {
                awaitEachGesture {
                    awaitFirstDown()
                    engine.tap()
                }
            },
    ) {
        Column(
            Modifier.fillMaxSize().padding(12.dp),
            horizontalAlignment = Alignment.CenterHorizontally,
        ) {
            Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                PlayerPill("You", s.myScore, Funk.Lime, false, Modifier.weight(1f), extra = dots(s.myScore, s.target))
                PlayerPill(s.theirName, s.theirScore, Funk.Cyan, false, Modifier.weight(1f), extra = dots(s.theirScore, s.target))
            }
            Text("Round ${s.round} · first to ${s.target}", fontSize = 12.sp, color = Color.White.copy(alpha = 0.8f))
            Spacer(Modifier.weight(1f))
            when (s.step) {
                "GO" -> {
                    Text("⚡", fontSize = 90.sp)
                    Text("TAP!", fontSize = 84.sp, fontWeight = FontWeight.Black, color = Color.White)
                    Text(if (s.iTapped) "Locked in…" else "NOW!", fontSize = 20.sp, fontWeight = FontWeight.Bold, color = Color.White)
                }
                "FAKE" -> {
                    Text("🙂", fontSize = 90.sp)
                    Text("TAP!", fontSize = 84.sp, fontWeight = FontWeight.Black, color = Color.White)
                    Text(if (s.iTapped) "…" else "(or is it?)", fontSize = 18.sp, color = Color.White.copy(alpha = 0.85f))
                }
                "RESULT" -> {
                    Text(
                        when (s.roundWin) { 1 -> "🏆"; 2 -> "😖"; else -> "🤝" },
                        fontSize = 90.sp,
                    )
                    Text(
                        when (s.roundWin) { 1 -> "YOU WON IT!"; 2 -> "${s.theirName.uppercase()} WON IT"; else -> "NO POINT" },
                        fontSize = 30.sp, fontWeight = FontWeight.Black, color = Color.White, textAlign = TextAlign.Center,
                    )
                    Text(s.why, fontSize = 16.sp, color = Color.White.copy(alpha = 0.92f), textAlign = TextAlign.Center)
                    if (s.myMs > 0 || s.theirMs > 0) {
                        Spacer(Modifier.height(8.dp))
                        Text(
                            "You ${if (s.myMs > 0) "${s.myMs} ms" else "—"}    ${s.theirName} ${if (s.theirMs > 0) "${s.theirMs} ms" else "—"}",
                            fontSize = 18.sp, fontWeight = FontWeight.Bold, color = Funk.Sun,
                        )
                    }
                }
                else -> {
                    Text("🤠", Modifier.graphicsLayer { scaleX = beat; scaleY = beat }, fontSize = 90.sp)
                    Text("WAIT…", fontSize = 56.sp, fontWeight = FontWeight.Black, color = Color.White)
                    Text("Tap only when the screen turns GREEN", fontSize = 16.sp, color = Color.White.copy(alpha = 0.85f), textAlign = TextAlign.Center)
                    if (s.fakeouts) Text("Beware of fake-outs!", fontSize = 14.sp, color = Funk.Sun, fontWeight = FontWeight.Bold)
                    if (s.iTapped) Text("🙈 Too early!", fontSize = 22.sp, color = Funk.Red, fontWeight = FontWeight.Black)
                }
            }
            Spacer(Modifier.weight(1f))
        }
        if (over) {
            EndOverlay(
                title = if (s.result == 1) "🏆 QUICKEST ON THE DRAW!" else "🏆 ${s.theirName.uppercase()} WINS!",
                subtitle = "You ${s.myScore}  ·  ${s.theirName} ${s.theirScore}",
                how = (if (s.result == 1) "You" else s.theirName) + " won the race to ${s.target} rounds, " +
                    "${maxOf(s.myScore, s.theirScore)} to ${minOf(s.myScore, s.theirScore)}. Each round goes to the fastest tap on GO; tapping too early loses the round.",
                win = s.result == 1, isHost = engine.isHost, theirName = s.theirName,
                playAgainLabel = "Rematch", colors = listOf(Funk.Lime, Funk.Mint),
                onPlayAgain = onPlayAgain, onBackToGames = onBackToGames, onLeave = onLeave,
            )
        }
    }
}

/** Little round markers: filled for rounds won. */
private fun dots(score: Int, target: Int) = "●".repeat(score.coerceAtMost(target)) + "○".repeat((target - score).coerceAtLeast(0))
