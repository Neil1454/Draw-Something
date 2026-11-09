@file:OptIn(ExperimentalMaterial3Api::class)

package com.neil1454.drawsomething.ui

import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.animateIntAsState
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.tween
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
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateListOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.input.KeyboardCapitalization
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.neil1454.drawsomething.core.Difficulty
import com.neil1454.drawsomething.core.GameEngine
import com.neil1454.drawsomething.core.GameState
import com.neil1454.drawsomething.core.GuessMatcher
import com.neil1454.drawsomething.core.Palette
import com.neil1454.drawsomething.core.Phase
import com.neil1454.drawsomething.core.RoundOutcome
import com.neil1454.drawsomething.core.RuleBreak
import com.neil1454.drawsomething.core.Rules
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
        ScoreChip("You", g.myScore, drawingPhase && g.iAmDrawer, Funk.Lime, Modifier.weight(1f))
        ScoreChip(g.theirName, g.theirScore, drawingPhase && !g.iAmDrawer, Funk.Cyan, Modifier.weight(1f))
    }
}

@Composable
private fun ScoreChip(name: String, score: Int, drawing: Boolean, accent: Color, modifier: Modifier) {
    val shown by animateIntAsState(score, tween(600), label = "score")
    val shape = RoundedCornerShape(16.dp)
    Column(
        modifier
            .clip(shape)
            .background(Brush.horizontalGradient(listOf(accent.copy(alpha = if (drawing) 0.5f else 0.2f), Color(0x55000000))))
            .border(if (drawing) 2.5.dp else 1.dp, accent.copy(alpha = if (drawing) 1f else 0.4f), shape)
            .padding(horizontal = 12.dp, vertical = 6.dp),
    ) {
        Text((if (drawing) "✏️ " else "") + name, style = MaterialTheme.typography.labelMedium, maxLines = 1)
        Text("$shown", fontSize = 22.sp, fontWeight = FontWeight.Black)
    }
}

@Composable
fun TimerBar(g: GameState) {
    val total = max(1, g.roundSeconds).toFloat()
    val fraction by animateFloatAsState((g.remainingSec / total).coerceIn(0f, 1f), label = "timer")
    val urgent = g.remainingSec <= 10 && (g.phase == Phase.DRAWING || g.phase == Phase.GUESSING)
    val pulse = rememberInfiniteTransition(label = "tick")
    val beat by pulse.animateFloat(1f, if (urgent) 1.28f else 1f, infiniteRepeatable(tween(450), RepeatMode.Reverse), label = "beat")
    val colour = when {
        fraction > 0.5f -> Funk.Lime
        fraction > 0.25f -> Funk.Sun
        else -> Funk.Red
    }
    Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
        Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween, verticalAlignment = Alignment.CenterVertically) {
            Text("Round ${g.round + 1} of ${g.totalRounds}", style = MaterialTheme.typography.labelLarge)
            Text(
                "⏱ ${g.remainingSec}s",
                Modifier.graphicsLayer {
                    scaleX = beat
                    scaleY = beat
                },
                fontSize = 20.sp,
                fontWeight = FontWeight.Black,
                color = colour,
            )
        }
        Box(Modifier.fillMaxWidth().height(12.dp).clip(RoundedCornerShape(6.dp)).background(Color(0x33FFFFFF))) {
            Box(
                Modifier.fillMaxHeight().fillMaxWidth(fraction).clip(RoundedCornerShape(6.dp))
                    .background(Brush.horizontalGradient(listOf(colour, colour.copy(alpha = 0.65f)))),
            )
        }
    }
}

/**
 * Portrait: [top], the square board (fills what's left), then [bottom].
 * Landscape/tablet: board on the left, [top] and [bottom] stacked in a side panel.
 */
@Composable
internal fun TwoPane(
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
fun GameRoot(
    g: GameState,
    board: StrokeBoard,
    version: Int,
    act: Act,
    onPlayAgain: () -> Unit,
    onBackToGames: () -> Unit,
    onLeave: () -> Unit,
) {
    when (g.phase) {
        Phase.PICKING -> PickScreen(g, act)
        Phase.WAITING_FOR_WORD -> WaitingScreen(g)
        Phase.DRAWING -> DrawScreen(g, board, version, act)
        Phase.GUESSING -> GuessScreen(g, board, version, act)
        Phase.RESULT -> ResultScreen(g, board, version, act)
        Phase.GAME_OVER -> GameOverScreen(g, onPlayAgain, onBackToGames, onLeave)
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
            GradientText("Pick something to draw", fontSize = 28.sp)
            g.options.forEach { w -> WordCard(w) { act { chooseWord(w) } } }
            FunkyButton("🎲  Surprise me", { act { chooseRandom() } }, Modifier.fillMaxWidth(), listOf(Funk.Violet, Funk.Pink), fontSize = 20.sp)
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
    val colours = when (w.difficulty) {
        Difficulty.EASY -> listOf(Funk.Mint, Funk.Cyan)
        Difficulty.MEDIUM -> listOf(Funk.Sun, Funk.Orange)
        Difficulty.HARD -> listOf(Funk.Pink, Funk.Red)
        Difficulty.EXTREME -> listOf(Funk.Violet, Funk.Pink)
    }
    val shape = RoundedCornerShape(20.dp)
    Row(
        Modifier.fillMaxWidth().clip(shape).background(Brush.horizontalGradient(colours))
            .border(2.dp, Color.White.copy(alpha = 0.4f), shape).clickable(onClick = onClick).padding(16.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Column(Modifier.weight(1f)) {
            Text(w.text.cap(), fontSize = 24.sp, fontWeight = FontWeight.Black, color = Color.White)
            Text(w.category, fontSize = 13.sp, color = Color.White.copy(alpha = 0.92f))
        }
        Column(horizontalAlignment = Alignment.End) {
            Text(w.difficulty.label, fontWeight = FontWeight.Bold, color = Color.White, fontSize = 13.sp)
            Text("×${w.difficulty.multiplier}", fontSize = 22.sp, fontWeight = FontWeight.Black, color = Color.White)
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
    var showRules by remember { mutableStateOf(false) }
    val width = if (color == Palette.ERASER) max(Palette.widths[widthIdx], Palette.ERASER_MIN_WIDTH) else Palette.widths[widthIdx]
    val word = g.word

    TwoPane(
        top = {
            TimerBar(g)
            Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
                Column(Modifier.weight(1f), horizontalAlignment = Alignment.CenterHorizontally) {
                    Text("DRAW THIS · ${word?.category.orEmpty()}", style = MaterialTheme.typography.labelMedium, color = Funk.Cyan)
                    GradientText(word?.text.orEmpty().uppercase(), fontSize = 30.sp)
                }
                Column(horizontalAlignment = Alignment.End) {
                    TextButton(onClick = { confirmSkip = true }) { Text("Skip (−${Scoring.SKIP_PENALTY})") }
                    TextButton(onClick = { showRules = true }) { Text("📜 Rules") }
                }
            }
            ScoreStrip(g)
            g.note?.let { NoticeCard(it) { act { dismissNote() } } }
        },
        board = {
            SquareBoard(
                board, version, Modifier.fillMaxSize(),
                canDraw = true, color = color, width = width,
                onBegin = { x, y -> act { beginStroke(color, width, x, y) } },
                onMove = { x, y -> act { extendStroke(x, y) } },
                onEnd = { act { endStroke() } },
            )
            val report = g.pendingReport
            val guess = g.pendingGuess
            if (report != null) {
                ReportCard(
                    rule = report, theirName = g.theirName,
                    onAnswer = { agree -> act { respondToReport(agree) } },
                    modifier = Modifier.align(Alignment.BottomCenter).padding(8.dp),
                )
            } else if (guess != null) {
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

    if (showRules) RulesDialog { showRules = false }
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
private fun ReportCard(rule: RuleBreak, theirName: String, onAnswer: (Boolean) -> Unit, modifier: Modifier) {
    Card(
        modifier.fillMaxWidth(),
        elevation = CardDefaults.cardElevation(defaultElevation = 8.dp),
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.errorContainer),
    ) {
        Column(Modifier.padding(14.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
            Text("⚑ $theirName reported a rule break", style = MaterialTheme.typography.labelLarge)
            Text("“${rule.label}”", style = MaterialTheme.typography.titleLarge, fontWeight = FontWeight.Bold)
            Text(
                "If you agree, you lose ${Scoring.RULE_BREAK_PENALTY} points and your drawing is cleared.",
                style = MaterialTheme.typography.bodySmall,
            )
            Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                FunkyButton("I disagree", { onAnswer(false) }, Modifier.weight(1f), listOf(Funk.Violet, Funk.Blue), fontSize = 15.sp)
                FunkyButton("They're right", { onAnswer(true) }, Modifier.weight(1f), listOf(Funk.Pink, Funk.Red), fontSize = 15.sp)
            }
        }
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
                FunkyButton("✗ Not quite", { onJudge(false) }, Modifier.weight(1f), listOf(Funk.Violet, Funk.Blue), fontSize = 15.sp)
                FunkyButton("✓ Correct!", { onJudge(true) }, Modifier.weight(1f), listOf(Funk.Mint, Funk.Lime), fontSize = 15.sp)
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
    val pop by animateFloatAsState(if (selected) 1.22f else 1f, label = "swatch")
    Box(
        Modifier
            .size(38.dp)
            .graphicsLayer {
                scaleX = pop
                scaleY = pop
            }
            .clip(CircleShape)
            .background(colour)
            .border(
                if (selected) 3.5.dp else 1.5.dp,
                if (selected) Color.White else Color.White.copy(alpha = 0.35f),
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
    var showRules by remember { mutableStateOf(false) }
    var showReport by remember { mutableStateOf(false) }
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
            Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
                TextButton(onClick = { showRules = true }) { Text("📜 Rules") }
                Spacer(Modifier.weight(1f))
                val left = Rules.MAX_REPORTS_PER_ROUND - g.reportsUsed
                OutlinedButton(onClick = { showReport = true }, enabled = !g.awaitingReport && left > 0) {
                    Text(if (g.awaitingReport) "Report sent…" else "⚑ Rule break ($left left)")
                }
            }
            ScoreStrip(g)
            g.note?.let { NoticeCard(it) { act { dismissNote() } } }
        },
        board = { SquareBoard(board, version, Modifier.fillMaxSize()) },
        bottom = {
            if (g.awaitingReport) {
                Text("Waiting for ${g.theirName} to answer your rule-break report…", style = MaterialTheme.typography.bodyMedium)
            }
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
    if (showRules) RulesDialog { showRules = false }
    if (showReport) {
        ReportDialog(
            theirName = g.theirName,
            onPick = { rule -> showReport = false; act { reportRuleBreak(rule) } },
            onClose = { showReport = false },
        )
    }
}

@Composable
private fun ReportDialog(theirName: String, onPick: (RuleBreak) -> Unit, onClose: () -> Unit) {
    AlertDialog(
        onDismissRequest = onClose,
        title = { Text("⚑ Report a rule break") },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                Text("What did $theirName do? They'll be asked whether they agree.")
                RuleBreak.values().forEach { rule ->
                    OutlinedButton(onClick = { onPick(rule) }, Modifier.fillMaxWidth()) { Text(rule.label) }
                }
            }
        },
        confirmButton = {},
        dismissButton = { TextButton(onClick = onClose) { Text("Cancel") } },
    )
}

// ============================================================================ result

@Composable
fun ResultScreen(g: GameState, board: StrokeBoard, version: Int, act: Act) {
    val o = g.outcome ?: return
    val popups = remember { mutableStateListOf<Popup>() }
    var confetti by remember { mutableIntStateOf(0) }
    LaunchedEffect(o.round, g.history.size) {
        popups.clear()
        popups.add(
            Popup(
                System.nanoTime(),
                if (o.myDelta >= 0) "+${o.myDelta}" else "${o.myDelta}",
                if (o.myDelta >= 0) Funk.Lime else Funk.Red,
                0.5f, 0.35f,
            ),
        )
        if (o.correct) confetti++
    }
    Box(Modifier.fillMaxSize()) {
        ResultContent(g, board, version, act)
        ConfettiBurst(confetti)
        PopupLayer(popups, { id -> popups.removeAll { it.id == id } })
    }
}

@Composable
private fun ResultContent(g: GameState, board: StrokeBoard, version: Int, act: Act) {
    val o = g.outcome ?: return
    var pressed by remember(g.round) { mutableStateOf(false) }
    val last = g.round + 1 >= g.totalRounds
    TwoPane(
        top = {
            GradientText(
                if (o.correct) "✅ GOT IT!" else "⏱ TIME'S UP!",
                fontSize = 30.sp,
                colors = if (o.correct) listOf(Funk.Lime, Funk.Mint, Funk.Cyan) else listOf(Funk.Sun, Funk.Orange, Funk.Red),
            )
            Text("The word was", style = MaterialTheme.typography.labelMedium)
            GradientText(o.word.uppercase(), fontSize = 28.sp)
        },
        board = { SquareBoard(board, version, Modifier.fillMaxSize()) },
        bottom = {
            OutcomeBreakdown(o, g)
            ScoreStrip(g)
            if (g.partnerReady && !pressed) {
                Card(
                    Modifier.fillMaxWidth(),
                    colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.tertiary.copy(alpha = 0.18f)),
                ) {
                    Text(
                        "👋 ${g.theirName} is ready " + if (last) "to see the final scores." else "for the next round.",
                        Modifier.padding(12.dp),
                        fontWeight = FontWeight.Bold,
                    )
                }
            }
            FunkyButton(
                when {
                    pressed -> "Waiting for ${g.theirName}…"
                    last -> "🏁  See final scores"
                    g.partnerReady -> "▶  Start next round"
                    else -> "▶  Next round"
                },
                { pressed = true; act { ready() } },
                Modifier.fillMaxWidth(),
                listOf(Funk.Mint, Funk.Cyan),
                enabled = !pressed,
            )
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
            if (o.ruleBreaks > 0) {
                Text("${o.ruleBreaks} rule break(s): −${Scoring.RULE_BREAK_PENALTY} each for the drawer", style = MaterialTheme.typography.bodySmall)
            }
        }
    }
}

// ============================================================================ game over

@Composable
fun GameOverScreen(g: GameState, onPlayAgain: () -> Unit, onBackToGames: () -> Unit, onLeave: () -> Unit) {
    var confetti by remember { mutableIntStateOf(0) }
    LaunchedEffect(g.myScore > g.theirScore) { if (g.myScore > g.theirScore) confetti++ }
    Box(Modifier.fillMaxSize()) {
        GameOverContent(g, onPlayAgain, onBackToGames, onLeave)
        ConfettiBurst(confetti)
    }
}

@Composable
private fun GameOverContent(g: GameState, onPlayAgain: () -> Unit, onBackToGames: () -> Unit, onLeave: () -> Unit) {
    val title = when {
        g.myScore > g.theirScore -> "🏆 YOU WIN!"
        g.myScore < g.theirScore -> "🏆 ${g.theirName.uppercase()} WINS!"
        else -> "🤝 IT'S A DRAW!"
    }
    Page {
        Column(
            Modifier.fillMaxWidth(),
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.spacedBy(12.dp),
        ) {
            Spacer(Modifier.height(16.dp))
            GradientText(title, fontSize = 38.sp)
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
                FunkyButton("🔁  Play again", onPlayAgain, Modifier.fillMaxWidth(), listOf(Funk.Pink, Funk.Orange))
            } else {
                Text("${g.theirName} can start a rematch…", color = MaterialTheme.colorScheme.onSurfaceVariant)
            }
            FunkyButton("🎮  Choose another game", onBackToGames, Modifier.fillMaxWidth(), listOf(Funk.Violet, Funk.Blue))
            TextButton(onClick = onLeave) { Text("Leave") }
        }
    }
}
