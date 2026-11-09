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
import androidx.compose.foundation.gestures.awaitEachGesture
import androidx.compose.foundation.gestures.awaitFirstDown
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.State
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
import androidx.compose.ui.draw.drawBehind
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.neil1454.drawsomething.core.DropEngine
import com.neil1454.drawsomething.core.DropState
import com.neil1454.drawsomething.core.GPhase
import kotlinx.coroutines.launch

private class DropFx(val cell: Int, val anim: Animatable<Float, AnimationVector1D>)

private class DiscStyle(val light: Color, val base: Color)

/** Disc colours for each theme: classic, neon, sunset. */
private fun discStyle(disc: Char, theme: Int): DiscStyle {
    val first = disc == '1'
    return when (theme) {
        1 -> if (first) DiscStyle(Color(0xFFFFB3DC), Funk.Pink) else DiscStyle(Color(0xFFB8F8FF), Funk.Cyan)
        2 -> if (first) DiscStyle(Color(0xFFFFCB94), Funk.Orange) else DiscStyle(Color(0xFFD9C4FF), Funk.Violet)
        else -> if (first) DiscStyle(Color(0xFFFF9A9A), Funk.Red) else DiscStyle(Color(0xFFFFF3A8), Funk.Sun)
    }
}

/** Board frame gradient and edge colour for each theme. */
private fun frame(theme: Int): Pair<List<Color>, Color> = when (theme) {
    1 -> listOf(Color(0xFF4B1B9A), Color(0xFF2A0E66)) to Funk.Pink
    2 -> listOf(Color(0xFF3B2A8C), Color(0xFF1F1457)) to Funk.Orange
    else -> listOf(Funk.Blue, Color(0xFF2A3FC4)) to Color.White.copy(alpha = 0.5f)
}

@Composable
fun DropHelp() {
    Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
        Text("The classic four-in-a-row. Take turns dropping a disc into a column; it falls to the bottom of the stack.")
        Text("• Line up 4 (or 5 on the Mega board) in a row, column or diagonal to win.")
        Text("• Touch and slide to choose a column, then let go to drop. The ghost disc shows where it will land.")
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
    var hover by remember { mutableIntStateOf(-1) }
    val over = s.phase == GPhase.OVER
    val canPlay = s.myTurn && !over
    val fx = remember { mutableStateListOf<DropFx>() }
    val popups = remember { mutableStateListOf<Popup>() }
    var popupId by remember { mutableLongStateOf(0L) }
    var boom by remember { mutableIntStateOf(0) }
    val scope = rememberCoroutineScope()
    val shake = rememberBurst(boom, 600)

    LaunchedEffect(s.moves) {
        if (s.moves == 0) {
            fx.clear(); popups.clear(); power = 0; hover = -1
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
            popups.add(Popup(popupId++, "💥 BOOM!", Funk.Orange, 0.5f, 0.4f))
        }
        if (s.result == 1) popups.add(Popup(popupId++, "🏆 FOUR!", Funk.Lime, 0.5f, 0.4f))
        if (s.result == 2) popups.add(Popup(popupId++, "😬 THEIR FOUR", Funk.Pink, 0.5f, 0.4f))
    }

    BoxWithConstraints(Modifier.fillMaxSize().shaking(shake)) {
        val landscape = maxWidth > maxHeight
        val theirDisc = if (s.myDisc == '1') '2' else '1'
        val turnText = when {
            over -> "GAME OVER"
            s.myTurn -> "👉  YOUR TURN  👈"
            else -> "⏳ ${s.theirName}'s turn"
        }
        val pills: @Composable (Modifier) -> Unit = { m ->
            Row(m, horizontalArrangement = Arrangement.spacedBy(8.dp), verticalAlignment = Alignment.CenterVertically) {
                PlayerPill("You", null, discStyle(s.myDisc, s.theme).base, canPlay, Modifier.weight(1f), dot = discStyle(s.myDisc, s.theme).base)
                PlayerPill(s.theirName, null, discStyle(theirDisc, s.theme).base, !s.myTurn && !over, Modifier.weight(1f), dot = discStyle(theirDisc, s.theme).base)
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
            DropBoard(
                s = s, hover = hover, fx = fx, popups = popups,
                onPopupDone = { id -> popups.removeAll { it.id == id } },
                canPlay = canPlay, modifier = m,
                onHover = { hover = it },
                onDrop = { col ->
                    engine.drop(col, power)
                    power = 0
                },
            )
        }

        if (!landscape) {
            Column(
                Modifier.fillMaxSize().padding(horizontal = 6.dp, vertical = 8.dp),
                verticalArrangement = Arrangement.spacedBy(6.dp),
                horizontalAlignment = Alignment.CenterHorizontally,
            ) {
                pills(Modifier.fillMaxWidth())
                TurnText(turnText, canPlay)
                board(Modifier.weight(1f).fillMaxWidth())
                powers()
            }
        } else {
            Row(Modifier.fillMaxSize().padding(8.dp), horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                board(Modifier.weight(1f).fillMaxSize())
                Column(
                    Modifier.width(220.dp).fillMaxSize(),
                    verticalArrangement = Arrangement.spacedBy(10.dp, Alignment.CenterVertically),
                    horizontalAlignment = Alignment.CenterHorizontally,
                ) {
                    pills(Modifier.fillMaxWidth())
                    TurnText(turnText, canPlay)
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

/** A shiny disc. */
@Composable
private fun Disc(style: DiscStyle, modifier: Modifier) {
    Box(
        modifier.drawBehind {
            val r = size.minDimension / 2f
            drawCircle(
                Brush.radialGradient(
                    listOf(style.light, style.base),
                    center = Offset(size.width * 0.35f, size.height * 0.3f),
                    radius = size.width * 0.85f,
                ),
                r,
            )
            drawCircle(Color.White.copy(alpha = 0.30f), radius = r * 0.6f, style = Stroke(width = r * 0.14f))
            drawCircle(Color.Black.copy(alpha = 0.28f), radius = r * 0.97f, style = Stroke(width = r * 0.06f))
            drawCircle(Color.White.copy(alpha = 0.5f), radius = r * 0.13f, center = Offset(size.width * 0.32f, size.height * 0.27f))
        },
    )
}

/**
 * The drop zone, the board and its feet. Touch and slide to pick a column; let go to drop.
 * The pointer handler covers the whole stack so you can aim from above the board.
 */
@Composable
private fun DropBoard(
    s: DropState,
    hover: Int,
    fx: List<DropFx>,
    popups: List<Popup>,
    onPopupDone: (Long) -> Unit,
    canPlay: Boolean,
    modifier: Modifier,
    onHover: (Int) -> Unit,
    onDrop: (Int) -> Unit,
) {
    val (frameColours, edge) = frame(s.theme)
    val myStyle = discStyle(s.myDisc, s.theme)
    BoxWithConstraints(modifier) {
        val zone = 1.25f
        val cell: Dp = minOf(maxWidth / s.w, maxHeight / (s.h + zone + 0.45f))
        val boardW = cell * s.w
        val shape = RoundedCornerShape(18.dp)
        Column(
            Modifier
                .align(Alignment.Center)
                .width(boardW)
                .pointerInput(canPlay, s.w) {
                    awaitEachGesture {
                        val down = awaitFirstDown()
                        if (!canPlay) return@awaitEachGesture
                        var col = (down.position.x / size.width * s.w).toInt().coerceIn(0, s.w - 1)
                        onHover(col)
                        do {
                            val event = awaitPointerEvent()
                            val change = event.changes.firstOrNull() ?: break
                            col = (change.position.x / size.width * s.w).toInt().coerceIn(0, s.w - 1)
                            onHover(col)
                        } while (event.changes.any { it.pressed })
                        onHover(-1)
                        onDrop(col)
                    }
                },
            horizontalAlignment = Alignment.CenterHorizontally,
        ) {
            // ---- drop zone: the floating disc follows your finger
            Box(Modifier.fillMaxWidth().height(cell * zone)) {
                if (canPlay && hover in 0 until s.w) {
                    val bob = rememberInfiniteTransition(label = "hoverbob")
                    val lift: State<Float> = bob.animateFloat(0f, 6f, infiniteRepeatable(tween(450), RepeatMode.Reverse), label = "lift")
                    Disc(
                        myStyle,
                        Modifier
                            .offset(x = cell * hover + cell * 0.08f, y = cell * 0.2f)
                            .size(cell * 0.84f)
                            .graphicsLayer { translationY = lift.value * density },
                    )
                } else if (canPlay) {
                    Text(
                        "Touch & slide, then let go to drop",
                        Modifier.align(Alignment.Center),
                        fontSize = 13.sp,
                        color = Color.White.copy(alpha = 0.7f),
                    )
                }
            }

            // ---- the board
            Box(
                Modifier
                    .fillMaxWidth()
                    .height(cell * s.h)
                    .clip(shape)
                    .background(Brush.verticalGradient(frameColours))
                    .border(3.dp, edge, shape),
            ) {
                Column(Modifier.fillMaxSize()) {
                    for (r in 0 until s.h) {
                        Row(Modifier.weight(1f).fillMaxWidth()) {
                            for (c in 0 until s.w) {
                                val i = r * s.w + c
                                DropCell(
                                    ch = s.grid.getOrElse(i) { '.' },
                                    style = discStyle(s.grid.getOrElse(i) { '1' }, s.theme),
                                    isLast = i == s.last,
                                    moves = s.moves,
                                    row = r,
                                    inLine = i in s.line,
                                    highlightColumn = canPlay && c == hover,
                                    modifier = Modifier.weight(1f),
                                )
                            }
                        }
                    }
                }
                val glow = rememberInfiniteTransition(label = "winline")
                    .animateFloat(0.35f, 1f, infiniteRepeatable(tween(500), RepeatMode.Reverse), label = "wl")
                Canvas(Modifier.matchParentSize()) {
                    val cw = size.width / s.w
                    val ch = size.height / s.h
                    fun centre(i: Int) = Offset((i % s.w + 0.5f) * cw, (i / s.w + 0.5f) * ch)
                    // the ghost: where your disc would land
                    if (canPlay && hover in 0 until s.w) {
                        val row = (s.h - 1 downTo 0).firstOrNull { s.grid.getOrElse(it * s.w + hover) { '1' } == '.' }
                        if (row != null) {
                            drawCircle(myStyle.base.copy(alpha = 0.38f), radius = cw * 0.38f, center = centre(row * s.w + hover))
                            drawCircle(Color.White.copy(alpha = 0.6f), radius = cw * 0.38f, center = centre(row * s.w + hover), style = Stroke(width = cw * 0.04f))
                        }
                    }
                    fx.forEach { f -> drawExplosion(centre(f.cell), cw * 1.1f, f.anim.value) }
                    if (s.line.size >= 2) {
                        drawLine(
                            Color.White.copy(alpha = glow.value), centre(s.line.first()), centre(s.line.last()),
                            strokeWidth = cw * 0.2f, cap = StrokeCap.Round,
                        )
                    }
                }
                PopupLayer(popups, onPopupDone)
            }

            // ---- the stand
            Row(Modifier.fillMaxWidth().padding(horizontal = cell * 0.4f), horizontalArrangement = Arrangement.SpaceBetween) {
                repeat(2) {
                    Box(
                        Modifier
                            .width(cell * 0.7f)
                            .height(cell * 0.35f)
                            .clip(RoundedCornerShape(bottomStart = 10.dp, bottomEnd = 10.dp))
                            .background(Brush.verticalGradient(frameColours)),
                    )
                }
            }
        }
    }
}

@Composable
private fun DropCell(
    ch: Char,
    style: DiscStyle,
    isLast: Boolean,
    moves: Int,
    row: Int,
    inLine: Boolean,
    highlightColumn: Boolean,
    modifier: Modifier,
) {
    val fall = remember { Animatable(0f) }
    LaunchedEffect(moves, isLast, ch) {
        if (isLast && moves > 0 && ch != '.') {
            fall.snapTo(-(row + 1.5f))
            fall.animateTo(0f, spring(dampingRatio = Spring.DampingRatioMediumBouncy, stiffness = Spring.StiffnessLow))
        } else {
            fall.snapTo(0f)
        }
    }
    Box(modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
        // the hole, with a little depth
        Box(
            Modifier
                .fillMaxSize(0.84f)
                .clip(CircleShape)
                .background(if (highlightColumn) Color(0xFF1B2780) else Color(0xFF0B1244))
                .border(2.dp, Color.Black.copy(alpha = 0.35f), CircleShape),
        )
        if (ch != '.') {
            Disc(
                style,
                Modifier
                    .fillMaxSize(0.84f)
                    .graphicsLayer { translationY = fall.value * size.height }
                    .then(if (inLine) Modifier.border(3.dp, Color.White, CircleShape) else Modifier),
            )
        }
    }
}
