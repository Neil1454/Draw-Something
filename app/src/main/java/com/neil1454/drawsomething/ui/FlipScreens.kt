package com.neil1454.drawsomething.ui

import com.neil1454.drawsomething.core.WinStory
import com.neil1454.drawsomething.core.Sound
import com.neil1454.drawsomething.sound.Sfx
import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.tween
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.neil1454.drawsomething.core.FlipEngine
import com.neil1454.drawsomething.core.FlipState
import com.neil1454.drawsomething.core.GPhase

@Composable
fun FlipHelp() {
    Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
        Text("Place a disc so that it traps a line of the other player's discs between your new disc and another of yours. Every trapped disc flips to your colour!")
        Text("• You must flip at least one disc. The glowing dots show where you can play.")
        Text("• No legal move? You pass. When nobody can move, the most discs wins.")
        Text("• Corners can never be flipped, so they're gold.")
    }
}

private val Felt = listOf(Color(0xFF0E7A4B), Color(0xFF0A5C39))

@Composable
fun FlipScreen(
    s: FlipState,
    engine: FlipEngine,
    onPlayAgain: () -> Unit,
    onBackToGames: () -> Unit,
    onLeave: () -> Unit,
) {
    val over = s.phase == GPhase.OVER
    LaunchedEffect(s.moves) { if (s.moves > 0) Sfx.play(Sound.FLIP) }
    val theirDisc = if (s.myDisc == '1') '2' else '1'
    BoxWithConstraints(Modifier.fillMaxSize()) {
        val landscape = maxWidth > maxHeight
        val turnText = when {
            over -> "GAME OVER"
            s.myTurn -> "👉  YOUR MOVE  👈"
            else -> "⏳ ${s.theirName} is thinking…"
        }
        val pills: @Composable (Modifier) -> Unit = { m ->
            Row(m, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                PlayerPill("You ${if (s.myDisc == '1') "⚫" else "⚪"}", s.myCount, Funk.Lime, s.myTurn && !over, Modifier.weight(1f))
                PlayerPill("${s.theirName} ${if (theirDisc == '1') "⚫" else "⚪"}", s.theirCount, Funk.Cyan, !s.myTurn && !over, Modifier.weight(1f))
            }
        }
        val passNote: @Composable () -> Unit = {
            if (s.passed != 0 && !over) {
                Text(
                    if (s.passed == 1) "😴 You had no move: passed" else "😴 ${s.theirName} had no move: passed",
                    fontSize = 13.sp, color = Funk.Sun, fontWeight = androidx.compose.ui.text.font.FontWeight.Bold,
                )
            }
        }
        val board: @Composable (Modifier) -> Unit = { m ->
            BoxWithConstraints(m) {
                val cell = minOf(maxWidth, maxHeight) / s.n
                val shape = RoundedCornerShape(14.dp)
                Box(
                    Modifier
                        .size(cell * s.n, cell * s.n)
                        .align(Alignment.Center)
                        .clip(shape)
                        .background(Brush.verticalGradient(Felt))
                        .border(4.dp, Color(0xFF3E2A14), shape),
                ) {
                    Column(Modifier.fillMaxSize()) {
                        for (y in 0 until s.n) {
                            Row(Modifier.weight(1f).fillMaxWidth()) {
                                for (x in 0 until s.n) {
                                    val i = y * s.n + x
                                    val corner = (x == 0 || x == s.n - 1) && (y == 0 || y == s.n - 1)
                                    FlipSquare(
                                        ch = s.grid.getOrElse(i) { '.' },
                                        flipped = i in s.flipped,
                                        isLast = i == s.last,
                                        legal = i in s.legal,
                                        corner = corner,
                                        moves = s.moves,
                                        modifier = Modifier.weight(1f),
                                    ) { if (s.myTurn && !over) engine.place(i) }
                                }
                            }
                        }
                    }
                }
            }
        }
        if (!landscape) {
            Column(Modifier.fillMaxSize().padding(8.dp), verticalArrangement = Arrangement.spacedBy(8.dp), horizontalAlignment = Alignment.CenterHorizontally) {
                pills(Modifier.fillMaxWidth())
                TurnText(turnText, s.myTurn && !over)
                passNote()
                board(Modifier.weight(1f).fillMaxWidth())
            }
        } else {
            Row(Modifier.fillMaxSize().padding(8.dp), horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                board(Modifier.weight(1f).fillMaxSize())
                Column(Modifier.width(230.dp).fillMaxSize(), verticalArrangement = Arrangement.spacedBy(8.dp), horizontalAlignment = Alignment.CenterHorizontally) {
                    pills(Modifier.fillMaxWidth())
                    TurnText(turnText, s.myTurn && !over)
                    passNote()
                }
            }
        }
        if (over) {
            EndOverlay(
                title = when (s.result) { 1 -> "🏆 YOU WIN!"; 2 -> "🏆 ${s.theirName.uppercase()} WINS!"; else -> "🤝 DRAW!" },
                subtitle = "You ${s.myCount}  ·  ${s.theirName} ${s.theirCount}",
                how = if (s.result == 3) "Both players finished with ${s.myCount} discs."
                else (if (s.result == 1) "You" else s.theirName) + " finished with the most discs, winning " +
                    WinStory.by(maxOf(s.myCount, s.theirCount), minOf(s.myCount, s.theirCount), "disc") +
                    if (s.myCount + s.theirCount >= s.n * s.n) ". The board filled up." else ". Neither player could make another move.",
                win = s.result == 1, isHost = engine.isHost, theirName = s.theirName,
                playAgainLabel = "Play again", colors = listOf(Funk.Violet, Funk.Blue),
                onPlayAgain = onPlayAgain, onBackToGames = onBackToGames, onLeave = onLeave,
            )
        }
    }
}

@Composable
private fun FlipSquare(
    ch: Char,
    flipped: Boolean,
    isLast: Boolean,
    legal: Boolean,
    corner: Boolean,
    moves: Int,
    modifier: Modifier,
    onTap: () -> Unit,
) {
    val spin = remember { Animatable(1f) }
    LaunchedEffect(moves, flipped, isLast, ch) {
        if (moves > 0 && (flipped || isLast) && ch != '.') {
            spin.snapTo(if (isLast) 0.2f else 0f)
            spin.animateTo(1f, tween(if (isLast) 350 else 480))
        } else {
            spin.snapTo(1f)
        }
    }
    Box(
        modifier
            .fillMaxSize()
            .border(0.5.dp, Color.Black.copy(alpha = 0.35f))
            .background(if (corner) Color(0x33FFD23F) else Color.Transparent)
            .clickable(enabled = legal, onClick = onTap),
        contentAlignment = Alignment.Center,
    ) {
        if (ch != '.') {
            val brush = if (ch == '1') Brush.radialGradient(listOf(Color(0xFF5A5A82), Color(0xFF0A0A14)))
            else Brush.radialGradient(listOf(Color.White, Color(0xFFC9D2FF)))
            Box(
                Modifier
                    .fillMaxSize(0.82f)
                    .graphicsLayer {
                        scaleX = spin.value
                        scaleY = if (isLast) spin.value else 1f
                    }
                    .clip(CircleShape)
                    .background(brush)
                    .border(if (isLast) 3.dp else 1.5.dp, if (isLast) Funk.Sun else Color.White.copy(alpha = 0.35f), CircleShape),
            )
        } else if (legal) {
            Box(Modifier.fillMaxSize(0.3f).clip(CircleShape).background(Funk.Lime.copy(alpha = 0.75f)))
        }
    }
}
