package com.neil1454.drawsomething.ui

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
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.neil1454.drawsomething.core.Draughts
import com.neil1454.drawsomething.core.DraughtsEngine
import com.neil1454.drawsomething.core.DraughtsState
import com.neil1454.drawsomething.core.GPhase
import com.neil1454.drawsomething.core.Sound
import com.neil1454.drawsomething.core.WinStory
import com.neil1454.drawsomething.sound.Sfx

@Composable
fun DraughtsHelp() {
    Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
        Text("Draughts (also called Checkers). Move your pieces diagonally forward, one square at a time, on the dark squares.")
        Text("• Jump over an enemy piece to capture it. Captures are COMPULSORY, and if you can keep jumping with the same piece you must.")
        Text("• Reach the far side and your piece is crowned a king: kings move and jump forwards and backwards (one square at a time).")
        Text("• You win when the other player has no pieces or no legal move. If nothing is captured for a long time it's a draw.")
        Text("• Tap one of your pieces to see where it can go, then tap the glowing square.")
    }
}

private val Light = Color(0xFFF1D9B0)
private val Dark = Color(0xFF8B5A2B)

@Composable
fun DraughtsScreen(
    s: DraughtsState,
    engine: DraughtsEngine,
    onPlayAgain: () -> Unit,
    onBackToGames: () -> Unit,
    onLeave: () -> Unit,
) {
    val over = s.phase == GPhase.OVER
    var selected by remember { mutableIntStateOf(-1) }
    LaunchedEffect(s.moves) {
        selected = -1
        if (s.moves > 0) Sfx.play(if (s.captured >= 0) Sound.POP else Sound.TAP)
    }
    // pieces that can move now (all of them if there is no forced jump chain)
    val movable = s.legal.map { it / 64 }.toSet()
    val dests = if (selected >= 0) s.legal.filter { it / 64 == selected }.map { it % 64 } else emptyList()
    LaunchedEffect(s.chain, s.myTurn) { if (s.chain >= 0) selected = s.chain }
    val flip = !s.iAmHost // each player sees their own pieces at the bottom
    val myMan = if (s.iAmHost) 'a' else 'b'

    BoxWithConstraints(Modifier.fillMaxSize()) {
        val landscape = maxWidth > maxHeight
        val turnText = when {
            over -> "GAME OVER"
            s.myTurn && s.chain >= 0 -> "👉  KEEP JUMPING!  👈"
            s.myTurn && s.mustCapture -> "👉  YOU MUST JUMP  👈"
            s.myTurn -> "👉  YOUR MOVE  👈"
            else -> "⏳ ${s.theirName} is thinking…"
        }
        val pills: @Composable (Modifier) -> Unit = { m ->
            Row(m, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                PlayerPill("You ${if (s.iAmHost) "⚪" else "🔴"}", s.myCount, Funk.Lime, s.myTurn && !over, Modifier.weight(1f))
                PlayerPill("${s.theirName} ${if (s.iAmHost) "🔴" else "⚪"}", s.theirCount, Funk.Cyan, !s.myTurn && !over, Modifier.weight(1f))
            }
        }
        val board: @Composable (Modifier) -> Unit = { m ->
            BoxWithConstraints(m) {
                val cell = minOf(maxWidth, maxHeight) / 8
                Box(
                    Modifier.size(cell * 8, cell * 8).align(Alignment.Center).clip(RoundedCornerShape(10.dp))
                        .border(4.dp, Color(0xFF3E2A14), RoundedCornerShape(10.dp)),
                ) {
                    Column(Modifier.fillMaxSize()) {
                        for (row in 0 until 8) {
                            Row(Modifier.weight(1f).fillMaxWidth()) {
                                for (col in 0 until 8) {
                                    val y = if (flip) 7 - row else row
                                    val x = if (flip) 7 - col else col
                                    val i = y * 8 + x
                                    val ch = s.grid.getOrElse(i) { '.' }
                                    DraughtsSquare(
                                        dark = Draughts.isDark(i), ch = ch, mine = ch.lowercaseChar() == myMan,
                                        selected = i == selected, canPick = s.myTurn && !over && i in movable,
                                        isDest = i in dests, last = i == s.last || i == s.lastFrom,
                                        moves = s.moves, modifier = Modifier.weight(1f),
                                    ) {
                                        if (!s.myTurn || over) return@DraughtsSquare
                                        when {
                                            i in dests -> { engine.move(selected, i); selected = -1 }
                                            i in movable && s.chain < 0 -> selected = if (selected == i) -1 else i
                                            i in movable -> selected = i
                                            else -> if (s.chain < 0) selected = -1
                                        }
                                    }
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
                board(Modifier.weight(1f).fillMaxWidth())
            }
        } else {
            Row(Modifier.fillMaxSize().padding(8.dp), horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                board(Modifier.weight(1f).fillMaxSize())
                Column(Modifier.width(230.dp).fillMaxSize(), verticalArrangement = Arrangement.spacedBy(8.dp), horizontalAlignment = Alignment.CenterHorizontally) {
                    pills(Modifier.fillMaxWidth())
                    TurnText(turnText, s.myTurn && !over)
                }
            }
        }
        if (over) {
            EndOverlay(
                title = when (s.result) { 1 -> "🏆 YOU WIN!"; 2 -> "🏆 ${s.theirName.uppercase()} WINS!"; else -> "🤝 DRAW!" },
                subtitle = "You ${s.myCount} pieces  ·  ${s.theirName} ${s.theirCount}",
                how = when {
                    s.result == 3 -> "Neither side captured a piece or moved a man for 40 moves each, so it's a draw."
                    s.reason == "none" -> (if (s.result == 1) "You" else s.theirName) + " captured every one of the other player's pieces."
                    else -> (if (s.result == 1) "You" else s.theirName) + " blocked the other player in: they had no legal move, with ${if (s.result == 1) s.myCount else s.theirCount} pieces left against ${if (s.result == 1) s.theirCount else s.myCount} (" +
                        WinStory.by(maxOf(s.myCount, s.theirCount), minOf(s.myCount, s.theirCount), "piece") + ")."
                },
                win = s.result == 1, isHost = engine.isHost, theirName = s.theirName,
                playAgainLabel = "Play again", colors = listOf(Color(0xFF3B2A1A), Color(0xFFC0392B)),
                onPlayAgain = onPlayAgain, onBackToGames = onBackToGames, onLeave = onLeave,
            )
        }
    }
}

@Composable
private fun DraughtsSquare(
    dark: Boolean,
    ch: Char,
    mine: Boolean,
    selected: Boolean,
    canPick: Boolean,
    isDest: Boolean,
    last: Boolean,
    moves: Int,
    modifier: Modifier,
    onTap: () -> Unit,
) {
    val pop = remember { Animatable(1f) }
    LaunchedEffect(moves, last) {
        if (moves > 0 && last && ch != '.') {
            pop.snapTo(0.6f)
            pop.animateTo(1f, tween(300))
        } else {
            pop.snapTo(1f)
        }
    }
    Box(
        modifier.fillMaxSize()
            .background(if (dark) Dark else Light)
            .background(if (last && dark) Color(0x33FFE066) else Color.Transparent)
            .clickable(enabled = dark, onClick = onTap),
        contentAlignment = Alignment.Center,
    ) {
        if (ch != '.') {
            val host = ch == 'a' || ch == 'A'
            val brush = if (host) Brush.radialGradient(listOf(Color.White, Color(0xFFD9C9A8)))
            else Brush.radialGradient(listOf(Color(0xFFFF6B5E), Color(0xFF9E1B12)))
            Box(
                Modifier.fillMaxSize(0.8f)
                    .graphicsLayer { scaleX = pop.value; scaleY = pop.value }
                    .clip(CircleShape).background(brush)
                    .border(
                        if (selected) 4.dp else if (canPick) 3.dp else 1.5.dp,
                        if (selected) Funk.Sun else if (canPick) Funk.Lime else Color.Black.copy(alpha = 0.35f), CircleShape,
                    ),
                contentAlignment = Alignment.Center,
            ) {
                Box(Modifier.fillMaxSize(0.62f).clip(CircleShape).border(1.5.dp, Color.Black.copy(alpha = 0.2f), CircleShape))
                if (ch == 'A' || ch == 'B') Text("♛", fontSize = 22.sp, fontWeight = FontWeight.Black, color = if (host) Color(0xFFB8860B) else Color(0xFFFFE066))
            }
        } else if (isDest) {
            Box(Modifier.fillMaxSize(0.34f).clip(CircleShape).background(Funk.Lime.copy(alpha = 0.85f)))
        }
    }
}
