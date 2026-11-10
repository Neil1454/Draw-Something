package com.neil1454.drawsomething.ui

import com.neil1454.drawsomething.core.Sound
import com.neil1454.drawsomething.sound.Sfx
import androidx.compose.animation.core.animateFloatAsState
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
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableLongStateOf
import androidx.compose.runtime.mutableStateListOf
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
import com.neil1454.drawsomething.core.GPhase
import com.neil1454.drawsomething.core.MemoryEngine
import com.neil1454.drawsomething.core.MemoryRules
import com.neil1454.drawsomething.core.MemoryState
import com.neil1454.drawsomething.core.MemoryThemes

@Composable
fun MemoryHelp() {
    Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
        Text("Flip two cards. If they match, you score and go again; if not, they flip back and it's the other player's turn.")
        Text("• A pair is worth ${MemoryRules.PAIR_POINTS} points, +${MemoryRules.STREAK_BONUS} more for each pair in a row (a streak).")
        Text("• The cards are only on the host's phone until they're flipped, so remember what you see!")
        Text("• When every pair is found, the highest score wins.")
    }
}

@Composable
fun MemoryScreen(
    s: MemoryState,
    engine: MemoryEngine,
    onPlayAgain: () -> Unit,
    onBackToGames: () -> Unit,
    onLeave: () -> Unit,
) {
    val over = s.phase == GPhase.OVER
    val popups = remember { mutableStateListOf<Popup>() }
    var popupId by remember { mutableLongStateOf(0L) }
    LaunchedEffect(s.moves) { if (s.moves > 0) Sfx.play(Sound.CARD) }
    LaunchedEffect(s.moves) {
        if (s.moves == 0) { popups.clear(); return@LaunchedEffect }
        when (s.event) {
            "match" -> {
                val streak = if (s.eventByMe) s.myStreak else s.theirStreak
                popups.add(Popup(popupId++, "+${s.eventPoints}" + if (streak >= 2) "  🔥×$streak" else "  ✨", if (s.eventByMe) Funk.Lime else Funk.Cyan, 0.5f, 0.4f))
            }
            "miss" -> popups.add(Popup(popupId++, "💨 no match", Funk.Pink, 0.5f, 0.4f))
        }
    }
    val turnText = when {
        over -> "GAME OVER"
        s.myTurn -> "👉  YOUR TURN  👈"
        else -> "⏳ ${s.theirName}'s turn"
    }
    BoxWithConstraints(Modifier.fillMaxSize()) {
        val landscape = maxWidth > maxHeight
        val pills: @Composable (Modifier) -> Unit = { m ->
            Row(m, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                PlayerPill("You", s.myScore, Funk.Lime, s.myTurn && !over, Modifier.weight(1f), extra = if (s.myStreak >= 2) "🔥${s.myStreak}" else "")
                PlayerPill(s.theirName, s.theirScore, Funk.Cyan, !s.myTurn && !over, Modifier.weight(1f), extra = if (s.theirStreak >= 2) "🔥${s.theirStreak}" else "")
            }
        }
        val board: @Composable (Modifier) -> Unit = { m ->
            BoxWithConstraints(m) {
                val cell = minOf(maxWidth / s.w, maxHeight / s.h)
                Box(Modifier.size(cell * s.w, cell * s.h).align(Alignment.Center)) {
                    Column(Modifier.fillMaxSize()) {
                        for (r in 0 until s.h) {
                            Row(Modifier.weight(1f).fillMaxWidth()) {
                                for (c in 0 until s.w) {
                                    val i = r * s.w + c
                                    MemoryCard(
                                        ch = s.view.getOrElse(i) { '.' },
                                        owner = s.owners.getOrElse(i) { '.' },
                                        mine = s.owners.getOrElse(i) { '.' } == (if (engine.isHost) '1' else '2'),
                                        theme = s.theme,
                                        sp = cell.value * 0.5f,
                                        canTap = s.myTurn && !over && s.up.size < 2,
                                        modifier = Modifier.weight(1f),
                                    ) { engine.flip(i) }
                                }
                            }
                        }
                    }
                    PopupLayer(popups, { id -> popups.removeAll { it.id == id } })
                }
            }
        }
        if (!landscape) {
            Column(Modifier.fillMaxSize().padding(8.dp), verticalArrangement = Arrangement.spacedBy(8.dp), horizontalAlignment = Alignment.CenterHorizontally) {
                pills(Modifier.fillMaxWidth())
                TurnText(turnText, s.myTurn && !over)
                Text("🃏 ${s.pairsLeft} pairs left", fontSize = 12.sp, color = Color.White.copy(alpha = 0.8f))
                board(Modifier.weight(1f).fillMaxWidth())
            }
        } else {
            Row(Modifier.fillMaxSize().padding(8.dp), horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                board(Modifier.weight(1f).fillMaxSize())
                Column(Modifier.width(230.dp).fillMaxSize(), verticalArrangement = Arrangement.spacedBy(8.dp), horizontalAlignment = Alignment.CenterHorizontally) {
                    pills(Modifier.fillMaxWidth())
                    TurnText(turnText, s.myTurn && !over)
                    Text("🃏 ${s.pairsLeft} pairs left", fontSize = 12.sp, color = Color.White.copy(alpha = 0.8f))
                }
            }
        }
        if (over) {
            EndOverlay(
                title = when (s.result) { 1 -> "🏆 YOU WIN!"; 2 -> "🏆 ${s.theirName.uppercase()} WINS!"; else -> "🤝 IT'S A DRAW!" },
                subtitle = "You ${s.myScore}  ·  ${s.theirName} ${s.theirScore}",
                how = run {
                    val mine = s.owners.count { it == if (engine.isHost) '1' else '2' }
                    val theirs = s.owners.count { it == if (engine.isHost) '2' else '1' }
                    if (s.result == 3) "You each found $mine pairs, and scored the same."
                    else "Pairs found: you $mine, ${s.theirName} $theirs. Streaks of matches earn bonus points, so the winner scored " +
                        "${maxOf(s.myScore, s.theirScore)} to ${minOf(s.myScore, s.theirScore)}."
                },
                win = s.result == 1, isHost = engine.isHost, theirName = s.theirName,
                playAgainLabel = "Play again", colors = listOf(Funk.Violet, Funk.Pink),
                onPlayAgain = onPlayAgain, onBackToGames = onBackToGames, onLeave = onLeave,
            )
        }
    }
}

@Composable
private fun MemoryCard(
    ch: Char,
    owner: Char,
    mine: Boolean,
    theme: Int,
    sp: Float,
    canTap: Boolean,
    modifier: Modifier,
    onTap: () -> Unit,
) {
    val faceUp = ch != '.'
    val matched = owner != '.'
    val rot by animateFloatAsState(if (faceUp) 180f else 0f, tween(380), label = "flip")
    val front = rot > 90f
    val symbol = if (faceUp) MemoryThemes.symbols[theme].getOrElse(Character.digit(ch, 36)) { "?" } else ""
    val accent = if (mine) Funk.Lime else Funk.Cyan
    Box(
        modifier
            .fillMaxSize()
            .padding(3.dp)
            .graphicsLayer {
                rotationY = rot
                cameraDistance = 14f * density
                alpha = if (matched) 0.85f else 1f
            }
            .clip(RoundedCornerShape(12.dp))
            .background(
                if (front) Brush.verticalGradient(listOf(Color(0xFFFFFFFF), Color(0xFFE6E0FF)))
                else Brush.linearGradient(listOf(Funk.Violet, Funk.Pink)),
            )
            .border(if (matched) 3.dp else 1.5.dp, if (matched) accent else Color.White.copy(alpha = 0.45f), RoundedCornerShape(12.dp))
            .clickable(enabled = !faceUp && canTap, onClick = onTap),
        contentAlignment = Alignment.Center,
    ) {
        if (front) {
            Text(symbol, Modifier.graphicsLayer { rotationY = 180f }, fontSize = sp.sp)
        } else {
            Text("?", fontSize = (sp * 0.8f).sp, fontWeight = FontWeight.Black, color = Color.White.copy(alpha = 0.8f))
        }
    }
}
