package com.neil1454.drawsomething.ui

import androidx.compose.animation.Crossfade
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.neil1454.drawsomething.core.GameKind
import com.neil1454.drawsomething.core.availableGames
import com.neil1454.drawsomething.core.LearnSession
import kotlinx.coroutines.delay
import kotlinx.coroutines.isActive

/** Pick a game to watch and learn. */
@Composable
fun LearnMenu(onPick: (GameKind) -> Unit, onBack: () -> Unit) {
    Page {
        Column(
            Modifier.fillMaxWidth(),
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.spacedBy(12.dp),
        ) {
            Spacer(Modifier.height(12.dp))
            GradientText("📖 Learn to play", fontSize = 34.sp)
            Text(
                "Pick a game and watch it play itself, with captions that explain every step. No reading the rules needed. " +
                    "Nobody else has to be there and nothing needs connecting.",
                textAlign = TextAlign.Center,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
            GameGrid(availableGames(), selected = null, enabled = true, onClick = onPick)
            TextButton(onClick = onBack) { Text("Back") }
        }
    }
}

/**
 * The real game screen, playing itself, with a caption panel. Touches on the board are blocked
 * while it plays, so you can't accidentally interfere; you control it with the buttons.
 */
@Composable
fun LearnScreen(kind: GameKind, onBack: () -> Unit) {
    var seed by remember(kind) { mutableIntStateOf(1) }
    val session = remember(kind, seed) { LearnSession(kind, seed) }
    var playing by remember { mutableStateOf(true) }
    var speed by remember { mutableIntStateOf(1) }
    var frame by remember { mutableIntStateOf(0) }

    LaunchedEffect(session, playing, speed) {
        while (isActive) {
            delay(50)
            if (playing && !session.finished) {
                session.advance(50L * speed)
                frame++
            }
        }
    }

    val restart: () -> Unit = { seed++ ; playing = true }
    val step: () -> Unit = {
        val id = session.captionId
        var guard = 0
        while (session.captionId == id && !session.finished && guard++ < 400) session.advance(50)
        frame++
    }

    BoxWithConstraints(Modifier.fillMaxSize()) {
        val landscape = maxWidth > maxHeight
        val game: @Composable (Modifier) -> Unit = { m ->
            Box(m) {
                LearnGameView(kind, session, frame, restart, onBack)
                if (!session.finished) {
                    Box(
                        Modifier.matchParentSize().pointerInput(Unit) {
                            awaitPointerEventScope {
                                while (true) awaitPointerEvent().changes.forEach { it.consume() }
                            }
                        },
                    )
                }
            }
        }
        val panel: @Composable (Modifier) -> Unit = { m ->
            CaptionPanel(
                session = session, tick = frame, playing = playing, speed = speed,
                onTogglePlay = { playing = !playing },
                onSpeed = { speed = if (speed >= 4) 1 else speed * 2 },
                onStep = step, onExit = onBack, modifier = m,
            )
        }
        if (!landscape) {
            Column(Modifier.fillMaxSize()) {
                game(Modifier.weight(1f).fillMaxWidth())
                panel(Modifier.fillMaxWidth().heightIn(max = 260.dp))
            }
        } else {
            Row(Modifier.fillMaxSize()) {
                game(Modifier.weight(1f).fillMaxHeight())
                panel(Modifier.width(300.dp).fillMaxHeight())
            }
        }
    }
}

@Composable
private fun CaptionPanel(
    session: LearnSession,
    tick: Int,
    playing: Boolean,
    speed: Int,
    onTogglePlay: () -> Unit,
    onSpeed: () -> Unit,
    onStep: () -> Unit,
    onExit: () -> Unit,
    modifier: Modifier,
) {
    @Suppress("UNUSED_VARIABLE") val redraw = tick // caption text lives in plain fields, so redraw on every tick
    val shape = RoundedCornerShape(topStart = 22.dp, topEnd = 22.dp)
    Column(
        modifier
            .clip(shape)
            .background(Color(0xE60B0624))
            .border(2.dp, Funk.Cyan.copy(alpha = 0.6f), shape)
            .padding(14.dp),
        verticalArrangement = Arrangement.spacedBy(10.dp),
    ) {
        Text("📖 LEARN · ${session.kind.label}", fontSize = 12.sp, fontWeight = FontWeight.Black, color = Funk.Cyan)
        Box(Modifier.weight(1f, fill = false).verticalScroll(rememberScrollState())) {
            Crossfade(targetState = session.captionId, label = "caption") {
                Text(session.caption, fontSize = 17.sp, fontWeight = FontWeight.SemiBold, color = Color.White)
            }
        }
        Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            FunkyButton(if (playing) "⏸ Pause" else "▶ Play", onTogglePlay, Modifier.weight(1f), listOf(Funk.Violet, Funk.Blue), fontSize = 14.sp)
            FunkyButton("⏭ Next", onStep, Modifier.weight(1f), listOf(Funk.Mint, Funk.Cyan), fontSize = 14.sp)
            FunkyButton("${speed}×", onSpeed, Modifier.width(64.dp), listOf(Funk.Orange, Funk.Pink), fontSize = 14.sp)
        }
        TextButton(onClick = onExit, Modifier.align(Alignment.CenterHorizontally)) { Text("✕ Finish learning") }
    }
}

/** The ordinary screen for [kind], fed by the demo's first computer player. */
@Composable
private fun LearnGameView(kind: GameKind, session: LearnSession, tick: Int, restart: () -> Unit, onBack: () -> Unit) {
    @Suppress("UNUSED_VARIABLE") val redraw = tick // the demo's state is plain objects, so this tick is what makes the screen redraw
    val host = session.duel.host
    when (kind) {
        GameKind.DRAW -> GameRoot(host.draw.state, host.draw.board, host.draw.board.version, { }, restart, onBack, onBack)
        GameKind.MINES -> MinesScreen(host.mines.state, { }, restart, onBack, onBack)
        GameKind.SEA -> SeaRoot(host.sea.state, { }, restart, onBack, onBack)
        GameKind.DROP -> DropScreen(host.drop.state, host.drop, restart, onBack, onBack)
        GameKind.MEMORY -> MemoryScreen(host.memory.state, host.memory, restart, onBack, onBack)
        GameKind.QUICK -> QuickScreen(host.quick.state, host.quick, restart, onBack, onBack)
        GameKind.DICE -> DiceScreen(host.dice.state, host.dice, restart, onBack, onBack)
        GameKind.FLIP -> FlipScreen(host.flip.state, host.flip, restart, onBack, onBack)
        GameKind.HILO -> HiLoScreen(host.hilo.state, host.hilo, restart, onBack, onBack)
        GameKind.HOLIDAY -> HolidayScreen(host.holiday.state, host.holiday, restart, onBack, onBack)
        GameKind.TETRIS -> TetrisScreen(host.tetris.state, host.tetris, restart, onBack, onBack)
        GameKind.DRAUGHTS -> DraughtsScreen(host.draughts.state, host.draughts, restart, onBack, onBack)
        GameKind.LUDO -> LudoScreen(host.ludo.state, host.ludo, restart, onBack, onBack)
        GameKind.MONOPOLY -> MonopolyScreen(host.monopoly.state, host.monopoly, restart, onBack, onBack)
    }
}
