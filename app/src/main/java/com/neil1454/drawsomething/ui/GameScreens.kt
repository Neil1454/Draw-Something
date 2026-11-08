@file:OptIn(ExperimentalMaterial3Api::class)

package com.neil1454.drawsomething.ui

import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxScope
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.input.KeyboardCapitalization
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.neil1454.drawsomething.core.GameEngine
import com.neil1454.drawsomething.core.GameState
import com.neil1454.drawsomething.core.GuessMatcher
import com.neil1454.drawsomething.core.Palette
import com.neil1454.drawsomething.core.Phase
import com.neil1454.drawsomething.core.RoundOutcome
import com.neil1454.drawsomething.core.Scoring
import com.neil1454.drawsomething.core.StrokeBoard
import com.neil1454.drawsomething.core.Word
import kotlin.math.max

/** Lets screens run something on the game engine (draw, guess, judge...). */
typealias Act = (GameEngine.() -> Unit) -> Unit

private fun String.cap() = replaceFirstChar { it.uppercase() }
private fun signed(n: Int) = if (n > 0) "+$n" else "$n"

// ============================================================================ shared bits

@Composable
fun ScoreStrip(g: GameState) {
    val drawingPhase = g.phase in setOf(Phase.PICKING, Phase.DRAWING, Phase.WAITING_FOR_WORD, Phase.GUESSING)
    Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
        ScoreChip("You", g.myScore, drawingPhase && g.iAmDrawer, Modifier.weight(1f))
        ScoreChip(g.theirName, g.theirScore, drawingPhase && !g.iAmDrawer, Modifier.weight(1f))
    }
}

@Composable
private fun ScoreChip(name: String, score: Int, drawing: Boolean, modifier: Modifier) {
    Card(
        modifier,
        colors = CardDefaults.cardColors(
            containerColor = if (drawing) MaterialTheme.colorScheme.primary.copy(alpha = 0.15f)
            else MaterialTheme.colorScheme.surfaceVariant,
        ),
    ) {
        Column(Modifier.padding(horizontal = 12.dp, vertical = 6.dp)) {
            Text(
                (if (drawing) "✏️ " else "") + name,
                style = MaterialTheme.typography.labelMedium,
                maxLines = 1,
            )
            Text("$score", style = MaterialTheme.typography.titleLarge, fontWeight = FontWeight.Bold)
        }
    }
}

@Composable
fun TimerBar(g: GameState) {
    val total = max(1, g.roundSeconds).toFloat()
    val fraction by animateFloatAsState((g.remainingSec / total).coerceIn(0f, 1f), label = "timer")
    Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
        Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
            Text("Round ${g.round + 1} of ${g.totalRounds}", style = MaterialTheme.typography.labelLarge)
            Text(
                "⏱ ${g.remainingSec}s",
                style = MaterialTheme.typography.labelLarge,
                fontWeight = FontWeight.Bold,
                color = if (g.remainingSec <= 10) MaterialTheme.colorScheme.error else MaterialTheme.colorScheme.onSurface,
            )
        }
        LinearProgressIndicator(progress = { fraction }, Modifier.fillMaxWidth())
    }
}

/**
 * Portrait: [top], the square board (fills what's left), then [bottom].
 * Landscape/tablet: board on the left, [top] and [bottom] stacked in a side panel.
 */
@Composable
private fun TwoPane(
    top: @Composable ColumnScope.() -> Unit,
    board: @Composable BoxScope.() -> Unit,
    bottom: @Composable ColumnScope.() -> Unit,
) {
    BoxWithConstraints(Modifier.fillMaxSize().padding(12.dp)) {
        if (maxWidth > maxHeight) {
            Row(Modifier.fillMaxSize(), horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                Box(Modifier.weight(1f).fillMaxHeight(), content = board)
                Column(
                    Modifier.width(300.dp).fillMaxHeight().verticalScroll(rememberScrollState()),
                    verticalArrangement = Arrangement.spacedBy(10.dp),
                ) {
                    top()
                    bottom()
                }
            }
        } else {
            Column(Modifier.fillMaxSize(), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                top()
                Box(Modifier.weight(1f).fillMaxWidth(), content = board)
                bottom()
            }
        }
    }
}

// ============================================================================ routing

@Composable
fun GameRoot(g: GameState, board: StrokeBoard, version: Int, act: Act, onLeave: () -> Unit) {
    when (g.phase) {
        Phase.PICKING -> PickScreen(g, act)
        Phase.WAITING_FOR_WORD -> WaitingScreen(g)
        Phase.DRAWING -> DrawScreen(g, board, version, act)
        Phase.GUESSING -> GuessScreen(g, board, version, act)
        Phase.RESULT -> ResultScreen(g, board, version, act)
        Phase.GAME_OVER -> GameOverScreen(g, act, onLeave)
        Phase.LOBBY -> Unit
    }
}

// ============================================================================ picking a word

@Composable
fun PickScreen(g: GameState, act: Act) {
    Page {
        Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
            ScoreStrip(g)
            Text("Round ${g.round + 1} of ${g.totalRounds} · your turn to draw", style = MaterialTheme.typography.labelLarge)
            Text("Pick something to draw", style = MaterialTheme.typography.headlineSmall, fontWeight = FontWeight.Bold)
            g.options.forEach { w -> WordCard(w) { act { chooseWord(w) } } }
            Button(onClick = { act { chooseRandom() } }, Modifier.fillMaxWidth().height(52.dp)) {
                Text("🎲 Surprise me", fontSize = 18.sp)
            }
            Text(
                "Harder words score more. Once you've picked, skipping costs you ${Scoring.SKIP_PENALTY} points." +
                    if (g.skips > 0) "\nSkipped so far this round: ${g.skips}" else "",
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }
    }
}

@Composable
private fun WordCard(w: Word, onClick: () -> Unit) {
    Card(onClick = onClick, Modifier.fillMaxWidth()) {
        Row(Modifier.padding(16.dp), verticalAlignment = Alignment.CenterVertically) {
            Column(Modifier.weight(1f)) {
                Text(w.text.cap(), style = MaterialTheme.typography.titleLarge, fontWeight = FontWeight.Bold)
                Text(w.category, style = MaterialTheme.typography.bodySmall)
            }
            Text(
                "${w.difficulty.label}  ×${w.difficulty.multiplier}",
                style = MaterialTheme.typography.labelLarge,
                color = when (w.difficulty.multiplier) {
                    1.0 -> MaterialTheme.colorScheme.tertiary
                    1.25 -> MaterialTheme.colorScheme.secondary
                    else -> MaterialTheme.colorScheme.error
                },
            )
        }
    }
}

@Composable
fun WaitingScreen(g: GameState) {
    Page {
        Column(
            Modifier.fillMaxWidth(),
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.spacedBy(16.dp),
        ) {
            ScoreStrip(g)
            Text("Round ${g.round + 1} of ${g.totalRounds}", style = MaterialTheme.typography.labelLarge)
            Spacer(Modifier.height(24.dp))
            CircularProgressIndicator()
            Text(
                "✏️ ${g.theirName} is choosing something to draw…",
                style = MaterialTheme.typography.titleMedium,
                textAlign = TextAlign.Center,
            )
            if (g.skips > 0) {
                Text(
                    "${g.theirName} skipped a word (−${Scoring.SKIP_PENALTY} for them).",
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
        }
    }
}

// ============================================================================ drawer

@Composable
fun DrawScreen(g: GameState, board: StrokeBoard, version: Int, act: Act) {
    var color by rememberSaveable { mutableIntStateOf(Palette.colors[0]) }
    var widthIdx by rememberSaveable { mutableIntStateOf(1) }
    var confirmSkip by remember { mutableStateOf(false) }
    val width = if (color == Palette.ERASER) max(Palette.widths[widthIdx], Palette.ERASER_MIN_WIDTH) else Palette.widths[widthIdx]
    val word = g.word

    TwoPane(
        top = {
            TimerBar(g)
            Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
                Column(Modifier.weight(1f)) {
                    Text("Draw this (${word?.category.orEmpty()})", style = MaterialTheme.typography.labelMedium)
                    Text(
                        word?.text.orEmpty().uppercase(),
                        style = MaterialTheme.typography.headlineMedium,
                        fontWeight = FontWeight.ExtraBold,
                        color = MaterialTheme.colorScheme.primary,
                    )
                }
                TextButton(onClick = { confirmSkip = true }) { Text("Skip (−${Scoring.SKIP_PENALTY})") }
            }
            ScoreStrip(g)
        },
        board = {
            SquareBoard(
                board, version, Modifier.fillMaxSize(),
                canDraw = true, color = color, width = width,
                onBegin = { x, y -> act { beginStroke(color, width, x, y) } },
                onMove = { x, y -> act { extendStroke(x, y) } },
                onEnd = { act { endStroke() } },
            )
            g.pendingGuess?.let { guess ->
                JudgeCard(
                    guess = guess, word = word?.text.orEmpty(), theirName = g.theirName,
                    onJudge = { ok -> act { judge(ok) } },
                    modifier = Modifier.align(Alignment.BottomCenter).padding(8.dp),
                )
            }
        },
        bottom = {
            DrawTools(
                color = color, widthIdx = widthIdx,
                onColor = { color = it }, onWidth = { widthIdx = it },
                onUndo = { act { undo() } }, onClear = { act { clearBoard() } },
            )
        },
    )

    if (confirmSkip) {
        AlertDialog(
            onDismissRequest = { confirmSkip = false },
            title = { Text("Skip this word?") },
            text = { Text("You'll lose ${Scoring.SKIP_PENALTY} points and pick a new word. The drawing so far is cleared.") },
            confirmButton = { TextButton(onClick = { confirmSkip = false; act { skipWord() } }) { Text("Skip it") } },
            dismissButton = { TextButton(onClick = { confirmSkip = false }) { Text("Keep drawing") } },
        )
    }
}

@Composable
private fun JudgeCard(guess: String, word: String, theirName: String, onJudge: (Boolean) -> Unit, modifier: Modifier) {
    val match = GuessMatcher.isMatch(guess, word)
    val close = GuessMatcher.isClose(guess, word)
    Card(
        modifier.fillMaxWidth(),
        elevation = CardDefaults.cardElevation(defaultElevation = 8.dp),
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
    ) {
        Column(Modifier.padding(14.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
            Text("$theirName guesses:", style = MaterialTheme.typography.labelLarge)
            Text("“$guess”", style = MaterialTheme.typography.headlineSmall, fontWeight = FontWeight.Bold)
            if (match) Text("✔ Matches your word", color = MaterialTheme.colorScheme.tertiary, fontWeight = FontWeight.Bold)
            else if (close) Text("Very close. Your call.", color = MaterialTheme.colorScheme.secondary)
            Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                OutlinedButton(onClick = { onJudge(false) }, Modifier.weight(1f).height(48.dp)) { Text("✗ Not quite") }
                Button(onClick = { onJudge(true) }, Modifier.weight(1f).height(48.dp)) { Text("✓ Correct!") }
            }
        }
    }
}

@Composable
private fun DrawTools(
    color: Int,
    widthIdx: Int,
    onColor: (Int) -> Unit,
    onWidth: (Int) -> Unit,
    onUndo: () -> Unit,
    onClear: () -> Unit,
) {
    Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
        Row(
            Modifier.fillMaxWidth().horizontalScroll(rememberScrollState()),
            horizontalArrangement = Arrangement.spacedBy(8.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Palette.colors.forEach { c -> Swatch(Color(c), selected = c == color, label = "") { onColor(c) } }
            Swatch(Color.White, selected = color == Palette.ERASER, label = "⌫") { onColor(Palette.ERASER) }
        }
        Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(6.dp)) {
            val dots = listOf(6.dp, 10.dp, 16.dp, 24.dp)
            Palette.widths.indices.forEach { i ->
                Box(
                    Modifier
                        .size(44.dp)
                        .clip(CircleShape)
                        .background(if (i == widthIdx) MaterialTheme.colorScheme.primary.copy(alpha = 0.18f) else Color.Transparent)
                        .clickable { onWidth(i) },
                    contentAlignment = Alignment.Center,
                ) {
                    Box(Modifier.size(dots[i]).clip(CircleShape).background(MaterialTheme.colorScheme.onSurface))
                }
            }
            Spacer(Modifier.weight(1f))
            OutlinedButton(onClick = onUndo) { Text("↶ Undo") }
            OutlinedButton(onClick = onClear) { Text("🗑 Clear") }
        }
    }
}

@Composable
private fun Swatch(colour: Color, selected: Boolean, label: String, onClick: () -> Unit) {
    Box(
        Modifier
            .size(38.dp)
            .clip(CircleShape)
            .background(colour)
            .border(
                if (selected) 3.dp else 1.dp,
                if (selected) MaterialTheme.colorScheme.primary else Color(0x33000000),
                CircleShape,
            )
            .clickable(onClick = onClick),
        contentAlignment = Alignment.Center,
    ) {
        if (label.isNotEmpty()) Text(label, fontSize = 16.sp, color = Color.Black)
    }
}

// ============================================================================ guesser

@Composable
fun GuessScreen(g: GameState, board: StrokeBoard, version: Int, act: Act) {
    var text by rememberSaveable { mutableStateOf("") }
    fun send() {
        if (text.isNotBlank() && !g.awaitingVerdict) {
            act { submitGuess(text) }
            text = ""
        }
    }
    TwoPane(
        top = {
            TimerBar(g)
            Text(
                "${g.theirName} is drawing · ${g.category}",
                style = MaterialTheme.typography.labelLarge,
            )
            Text(
                g.pattern.map { if (it == ' ') "   " else "$it " }.joinToString("").trim(),
                style = MaterialTheme.typography.headlineMedium,
                fontWeight = FontWeight.Bold,
                color = MaterialTheme.colorScheme.primary,
            )
            ScoreStrip(g)
        },
        board = { SquareBoard(board, version, Modifier.fillMaxSize()) },
        bottom = {
            if (g.declined.isNotEmpty()) {
                Text(
                    "Not quite: " + g.declined.joinToString(", "),
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.error,
                )
            }
            if (g.awaitingVerdict) {
                Text("Waiting for ${g.theirName} to check your guess…", style = MaterialTheme.typography.bodyMedium)
            }
            Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                OutlinedTextField(
                    value = text,
                    onValueChange = { text = it.take(40) },
                    enabled = !g.awaitingVerdict,
                    singleLine = true,
                    label = { Text("Your guess") },
                    modifier = Modifier.weight(1f),
                    keyboardOptions = KeyboardOptions(capitalization = KeyboardCapitalization.None, imeAction = ImeAction.Send),
                    keyboardActions = KeyboardActions(onSend = { send() }),
                )
                Button(onClick = { send() }, enabled = text.isNotBlank() && !g.awaitingVerdict, modifier = Modifier.height(56.dp)) {
                    Text("Send")
                }
            }
        },
    )
}

// ============================================================================ result

@Composable
fun ResultScreen(g: GameState, board: StrokeBoard, version: Int, act: Act) {
    val o = g.outcome ?: return
    var pressed by remember(g.round) { mutableStateOf(false) }
    val last = g.round + 1 >= g.totalRounds
    TwoPane(
        top = {
            Text(
                if (o.correct) "✅ Got it!" else "⏱ Time's up!",
                style = MaterialTheme.typography.headlineMedium,
                fontWeight = FontWeight.Bold,
            )
            Text("The word was ${o.word.uppercase()}", style = MaterialTheme.typography.titleLarge, color = MaterialTheme.colorScheme.primary)
        },
        board = { SquareBoard(board, version, Modifier.fillMaxSize()) },
        bottom = {
            OutcomeBreakdown(o, g)
            ScoreStrip(g)
            Button(
                onClick = { pressed = true; act { ready() } },
                enabled = !pressed,
                modifier = Modifier.fillMaxWidth().height(52.dp),
            ) {
                Text(
                    when {
                        pressed -> "Waiting for ${g.theirName}…"
                        last -> "See final scores"
                        else -> "Next round"
                    },
                    fontSize = 18.sp,
                )
            }
        },
    )
}

@Composable
private fun OutcomeBreakdown(o: RoundOutcome, g: GameState) {
    Card(Modifier.fillMaxWidth()) {
        Column(Modifier.padding(12.dp), verticalArrangement = Arrangement.spacedBy(2.dp)) {
            Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
                Text("You ${if (o.drawerIsMe) "(drew)" else "(guessed)"}", fontWeight = FontWeight.Bold)
                Text(signed(o.myDelta), fontWeight = FontWeight.Bold)
            }
            Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
                Text("${g.theirName} ${if (o.drawerIsMe) "(guessed)" else "(drew)"}", fontWeight = FontWeight.Bold)
                Text(signed(o.theirDelta), fontWeight = FontWeight.Bold)
            }
            if (o.correct) {
                Text("Guessed in ${"%.1f".format(o.elapsedMs / 1000.0)}s · difficulty ×${o.multiplier}", style = MaterialTheme.typography.bodySmall)
            }
            if (o.wrongGuesses > 0) {
                Text("${o.wrongGuesses} wrong guess(es): −${Scoring.WRONG_GUESS_PENALTY} each for the guesser", style = MaterialTheme.typography.bodySmall)
            }
            if (o.skips > 0) {
                Text("${o.skips} skipped word(s): −${Scoring.SKIP_PENALTY} each for the drawer", style = MaterialTheme.typography.bodySmall)
            }
        }
    }
}

// ============================================================================ game over

@Composable
fun GameOverScreen(g: GameState, act: Act, onLeave: () -> Unit) {
    val title = when {
        g.myScore > g.theirScore -> "🏆 You win!"
        g.myScore < g.theirScore -> "🏆 ${g.theirName} wins!"
        else -> "🤝 It's a draw!"
    }
    Page {
        Column(
            Modifier.fillMaxWidth(),
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.spacedBy(12.dp),
        ) {
            Spacer(Modifier.height(16.dp))
            Text(title, style = MaterialTheme.typography.displaySmall, fontWeight = FontWeight.Bold, textAlign = TextAlign.Center)
            ScoreStrip(g)
            Card(Modifier.fillMaxWidth()) {
                Column(Modifier.padding(12.dp), verticalArrangement = Arrangement.spacedBy(6.dp)) {
                    Text("Rounds", fontWeight = FontWeight.Bold)
                    g.history.forEach { o ->
                        Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
                            Text("${o.round + 1}. ${o.word.cap()} ${if (o.correct) "✅" else "⏱"}", Modifier.weight(1f))
                            Text("You ${signed(o.myDelta)} · ${g.theirName} ${signed(o.theirDelta)}", style = MaterialTheme.typography.bodySmall)
                        }
                    }
                }
            }
            if (g.isHost) {
                Button(
                    onClick = { act { hostStart(g.totalRounds, g.roundSeconds) } },
                    Modifier.fillMaxWidth().height(52.dp),
                ) { Text("Play again", fontSize = 18.sp) }
            } else {
                Text("${g.theirName} can start a rematch…", color = MaterialTheme.colorScheme.onSurfaceVariant)
            }
            TextButton(onClick = onLeave) { Text("Back to menu") }
        }
    }
}
