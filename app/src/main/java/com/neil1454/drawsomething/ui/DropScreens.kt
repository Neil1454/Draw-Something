package com.neil1454.drawsomething.ui

import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.AnimationVector1D
import androidx.compose.animation.core.LinearEasing
import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.Spring
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.spring
import androidx.compose.animation.core.tween
import androidx.compose.foundation.Canvas
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
import androidx.compose.runtime.mutableLongStateOf
import androidx.compose.runtime.mutableStateListOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.neil1454.drawsomething.core.DropEngine
import com.neil1454.drawsomething.core.DropState
import com.neil1454.drawsomething.core.GPhase
import kotlinx.coroutines.launch

private class DropFx(val cell: Int, val anim: Animatable<Float, AnimationVector1D>)

private fun discColours(ch: Char): Pair<Color, Color> =
    if (ch == '1') Color(0xFFFF9A9A) to Funk.Red else Color(0xFFFFF3A8) to Funk.Sun

@Composable
fun DropHelp() {
    Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
        Text("Take turns dropping a disc into a column. It falls to the bottom of the stack.")
        Text("• Line up 4 (or 5 on the Mega board) in a row, column or diagonal to win.")
        Text("• Power mode: each player has one 💣 Bomb disc (blasts the 8 discs around where it lands, then the columns settle) and one ⏩ Double disc (drop again straight away).")
    }
}

@Composable
fun DropScreen(
    s: DropState,
    engine: DropEngine,
    onPlayAgain: () -> Unit,
    onBackToGames: () -> Unit,
    onLeave: () -> Unit,
) {
    var power by remember { mutableIntStateOf(0) }
    val over = s.phase == GPhase.OVER
    val fx = remember { mutableStateListOf<DropFx>() }
    val popups = remember { mutableStateListOf<Popup>() }
    var popupId by remember { mutableLongStateOf(0L) }
    var boom by remember { mutableIntStateOf(0) }
    val scope = rememberCoroutineScope()
    val shake = rememberBurst(boom, 600)

    LaunchedEffect(s.moves) {
        if (s.moves == 0) {
            fx.clear(); popups.clear(); power = 0
            return@LaunchedEffect
        }
        if (s.removed.isNotEmpty()) {
            boom++
            s.removed.forEach { c ->
                val a = Animatable(0f)
                val f = DropFx(c, a)
                fx.add(f)
                scope.launch {
                    a.animateTo(1f, tween(900, easing = LinearEasing))
                    fx.remove(f)
                }
            }
            popups.add(Popup(popupId++, "💥 BOOM!", Funk.Orange, 0.5f, 0.35f))
        }
        if (s.result == 1) popups.add(Popup(popupId++, "🏆 FOUR!", Funk.Lime, 0.5f, 0.3f))
    }

    BoxWithConstraints(Modifier.fillMaxSize().shaking(shake)) {
        val landscape = maxWidth > maxHeight
        val turnText = when {
            over -> "GAME OVER"
            s.myTurn -> "👉  YOUR TURN  👈"
            else -> "⏳ ${s.theirName}'s turn"
        }
        val pills: @Composable (Modifier) -> Unit = { m ->
            Row(m, horizontalArrangement = Arrangement.spacedBy(8.dp), verticalAlignment = Alignment.CenterVertically) {
                DiscPill("You", s.myDisc, s.myTurn && !over, Modifier.weight(1f))
                DiscPill(s.theirName, if (s.myDisc == '1') '2' else '1', !s.myTurn && !over, Modifier.weight(1f))
            }
        }
        val powers: @Composable () -> Unit = {
            if (s.power && !over) {
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp), modifier = Modifier.fillMaxWidth()) {
                    PowerChip("Normal", power == 0, true, Modifier.weight(1f)) { power = 0 }
                    PowerChip("💣 Bomb", power == 1, s.myBomb, Modifier.weight(1f)) { power = 1 }
                    PowerChip("⏩ Double", power == 2, s.myDouble, Modifier.weight(1f)) { power = 2 }
                }
            }
        }
        val board: @Composable (Modifier) -> Unit = { m ->
            DropBoard(s, fx, popups, { id -> popups.removeAll { it.id == id } }, m) { col ->
                if (s.myTurn && !over) {
                    engine.drop(col, power)
                    power = 0
                }
            }
        }

        if (!landscape) {
            Column(Modifier.fillMaxSize().padding(8.dp), verticalArrangement = Arrangement.spacedBy(8.dp), horizontalAlignment = Alignment.CenterHorizontally) {
                pills(Modifier.fillMaxWidth())
                TurnText(turnText, s.myTurn && !over)
                board(Modifier.weight(1f).fillMaxWidth())
                powers()
            }
        } else {
            Row(Modifier.fillMaxSize().padding(8.dp), horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                board(Modifier.weight(1f).fillMaxSize())
                Column(Modifier.width(230.dp).fillMaxSize(), verticalArrangement = Arrangement.spacedBy(8.dp), horizontalAlignment = Alignment.CenterHorizontally) {
                    pills(Modifier.fillMaxWidth())
                    TurnText(turnText, s.myTurn && !over)
                    powers()
                }
            }
        }

        if (over) {
            EndOverlay(
                title = when (s.result) { 1 -> "🏆 YOU WIN!"; 2 -> "🏆 ${s.theirName.uppercase()} WINS!"; else -> "🤝 DRAW!" },
                subtitle = if (s.result == 3) "The board is full." else "Four in a line!",
                win = s.result == 1, isHost = engine.isHost, theirName = s.theirName,
                playAgainLabel = "Play again", colors = listOf(Funk.Red, Funk.Sun),
                onPlayAgain = onPlayAgain, onBackToGames = onBackToGames, onLeave = onLeave,
            )
        }
    }
}

@Composable
private fun DiscPill(name: String, disc: Char, active: Boolean, modifier: Modifier) {
    val (_, base) = discColours(disc)
    PlayerPill(name, null, base, active, modifier, extra = if (disc == '1') "🔴" else "🟡")
}

@Composable
private fun PowerChip(text: String, selected: Boolean, available: Boolean, modifier: Modifier, onClick: () -> Unit) {
    FunkyButton(
        text = text + if (!available) " ✓" else "",
        onClick = onClick,
        modifier = modifier,
        colors = if (selected) listOf(Funk.Lime, Funk.Mint) else listOf(Funk.Violet, Funk.Blue),
        enabled = available,
        fontSize = 14.sp,
    )
}

@Composable
private fun DropBoard(
    s: DropState,
    fx: List<DropFx>,
    popups: List<Popup>,
    onPopupDone: (Long) -> Unit,
    modifier: Modifier,
    onColumn: (Int) -> Unit,
) {
    BoxWithConstraints(modifier) {
        val cell = minOf(maxWidth / s.w, maxHeight / s.h)
        val shape = RoundedCornerShape(18.dp)
        Box(
            Modifier
                .size(cell * s.w, cell * s.h)
                .align(Alignment.Center)
                .clip(shape)
                .background(Brush.verticalGradient(listOf(Funk.Blue, Color(0xFF2A3FC4))))
                .border(3.dp, Color.White.copy(alpha = 0.4f), shape),
        ) {
            Column(Modifier.fillMaxSize()) {
                for (r in 0 until s.h) {
                    Row(Modifier.weight(1f).fillMaxWidth()) {
                        for (c in 0 until s.w) {
                            val i = r * s.w + c
                            DropCell(
                                ch = s.grid.getOrElse(i) { '.' },
                                isLast = i == s.last,
                                moves = s.moves,
                                row = r,
                                inLine = i in s.line,
                                modifier = Modifier.weight(1f),
                            ) { onColumn(c) }
                        }
                    }
                }
            }
            Canvas(Modifier.matchParentSize()) {
                val cw = size.width / s.w
                val ch = size.height / s.h
                fx.forEach { f ->
                    drawExplosion(Offset((f.cell % s.w + 0.5f) * cw, (f.cell / s.w + 0.5f) * ch), cw * 1.1f, f.anim.value)
                }
            }
            PopupLayer(popups, onPopupDone)
        }
    }
}

@Composable
private fun DropCell(ch: Char, isLast: Boolean, moves: Int, row: Int, inLine: Boolean, modifier: Modifier, onTap: () -> Unit) {
    val fall = remember { Animatable(0f) }
    LaunchedEffect(moves, isLast, ch) {
        if (isLast && moves > 0 && ch != '.') {
            fall.snapTo(-(row + 1.5f))
            fall.animateTo(0f, spring(dampingRatio = Spring.DampingRatioMediumBouncy, stiffness = Spring.StiffnessLow))
        } else {
            fall.snapTo(0f)
        }
    }
    val pulse = rememberInfiniteTransition(label = "linepulse")
    val glow by pulse.animateFloat(0.4f, 1f, infiniteRepeatable(tween(500), RepeatMode.Reverse), label = "glow")
    Box(modifier.fillMaxSize().clickable(onClick = onTap), contentAlignment = Alignment.Center) {
        Box(Modifier.fillMaxSize(0.84f).clip(CircleShape).background(Color(0xFF0E1654)))
        if (ch != '.') {
            val (light, base) = discColours(ch)
            Box(
                Modifier
                    .fillMaxSize(0.84f)
                    .graphicsLayer { translationY = fall.value * size.height }
                    .clip(CircleShape)
                    .background(Brush.radialGradient(listOf(light, base)))
                    .border(if (inLine) 4.dp else 2.dp, if (inLine) Funk.Lime.copy(alpha = glow) else Color.White.copy(alpha = 0.5f), CircleShape),
            )
        }
    }
}
