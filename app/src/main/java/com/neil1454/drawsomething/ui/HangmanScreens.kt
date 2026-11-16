package com.neil1454.drawsomething.ui

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.drawscope.DrawScope
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.drawscope.withTransform
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardCapitalization
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.neil1454.drawsomething.core.GPhase
import com.neil1454.drawsomething.core.HangmanEngine
import com.neil1454.drawsomething.core.HangmanRules
import com.neil1454.drawsomething.core.HangmanState
import com.neil1454.drawsomething.core.HangmanWords
import com.neil1454.drawsomething.core.Sound
import com.neil1454.drawsomething.sound.Sfx

@Composable
fun HangmanHelp() {
    Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
        Text("Guess the secret word one letter at a time before the drawing is finished. Eight wrong guesses and it's game over for the poor stick figure!")
        Text("• Duel: you take turns being the setter. The setter secretly types a word (letters only, 3 to 14) and can add a short hint. The other player guesses letters, or risks the whole word (a wrong word guess costs two lives).")
        Text("• Shared: the app picks a random word and you take turns guessing on the same board. A right letter lets you go again; a wrong one costs you both a life and passes the turn. Whoever finishes the word scores.")
        Text("• Solving a word scores more the fewer mistakes were made. In a duel, a word that stumps the guesser scores for the setter. Most points after all the words wins.")
    }
}

private val Chalk = Color(0xFFF1F5F9)
private val Rope = Color(0xFFD6A24A)

/** The gallows and the stick figure, [misses] parts drawn, in a 100 x 100 box. */
private fun DrawScope.drawGallows(misses: Int) {
    val wood = Color(0xFF8B5A2B)
    drawRoundRect(wood, Offset(6f, 90f), Size(60f, 6f), androidx.compose.ui.geometry.CornerRadius(3f))
    drawRoundRect(wood, Offset(20f, 8f), Size(6f, 84f), androidx.compose.ui.geometry.CornerRadius(3f))
    drawRoundRect(wood, Offset(20f, 8f), Size(46f, 6f), androidx.compose.ui.geometry.CornerRadius(3f))
    drawLine(wood, Offset(20f, 30f), Offset(36f, 14f), 4f, StrokeCap.Round)
    drawLine(Rope, Offset(62f, 14f), Offset(62f, 26f), 2.6f, StrokeCap.Round)
    val body = if (misses >= HangmanRules.MAX_MISSES) Color(0xFFFCA5A5) else Chalk
    val w = 3.2f
    if (misses >= 1) drawCircle(body, 8f, Offset(62f, 34f), style = Stroke(w))
    if (misses >= 2) drawLine(body, Offset(62f, 42f), Offset(62f, 66f), w, StrokeCap.Round)
    if (misses >= 3) drawLine(body, Offset(62f, 48f), Offset(50f, 58f), w, StrokeCap.Round)
    if (misses >= 4) drawLine(body, Offset(62f, 48f), Offset(74f, 58f), w, StrokeCap.Round)
    if (misses >= 5) drawLine(body, Offset(62f, 66f), Offset(52f, 82f), w, StrokeCap.Round)
    if (misses >= 6) drawLine(body, Offset(62f, 66f), Offset(72f, 82f), w, StrokeCap.Round)
    if (misses >= 7) {
        for (ex in floatArrayOf(58.5f, 65.5f)) { drawLine(body, Offset(ex - 1.5f, 32f), Offset(ex + 1.5f, 35f), 1.6f, StrokeCap.Round); drawLine(body, Offset(ex + 1.5f, 32f), Offset(ex - 1.5f, 35f), 1.6f, StrokeCap.Round) }
    }
    if (misses >= 8) drawArc(body, 200f, 140f, false, Offset(57f, 36f), Size(10f, 8f), style = Stroke(1.8f, cap = StrokeCap.Round))
}

@Composable
fun HangmanScreen(s: HangmanState, engine: HangmanEngine, onPlayAgain: () -> Unit, onBackToGames: () -> Unit, onLeave: () -> Unit) {
    var typed by remember(s.round, s.stage) { mutableStateOf("") }
    var hintText by remember(s.round, s.stage) { mutableStateOf("") }
    var wordDialog by remember { mutableStateOf(false) }
    var guess by remember { mutableStateOf("") }
    LaunchedEffect(s.moves, s.round) { if (s.moves > 0 && s.lastLetter != ' ') Sfx.play(if (s.lastHit) Sound.GOOD else Sound.BAD) }
    LaunchedEffect(s.stage, s.round) { if (s.stage == "REVEAL") Sfx.play(if (s.roundOutcome == 1 && s.roundScorer == 1) Sound.WIN else Sound.TAP) }

    val reveal = s.stage == "REVEAL" || s.stage == "END"
    val shown = when {
        reveal -> s.word.map { it }.joinToString("")
        s.iAmSetter && s.myWord.isNotEmpty() -> s.myWord
        else -> s.mask
    }
    val usedSet = s.used.toSet()

    Box(Modifier.fillMaxSize().background(Brush.verticalGradient(listOf(Color(0xFF0F172A), Color(0xFF1F2937), Color(0xFF3B2A1A))))) {
        Column(Modifier.fillMaxSize().verticalScroll(rememberScrollState()).padding(horizontal = 14.dp, vertical = 12.dp), horizontalAlignment = Alignment.CenterHorizontally) {
            Text("🪢 HANGMAN", color = Color(0xFFFFD23F), fontWeight = FontWeight.Black, fontSize = 20.sp, letterSpacing = 3.sp)
            Text("Word ${s.round} of ${s.rounds}  ·  You ${s.myScore}  –  ${s.theirScore} ${engine.theirName}", color = Color.White.copy(alpha = 0.85f), fontSize = 13.sp, modifier = Modifier.padding(top = 2.dp))
            Text(
                when {
                    s.phase == GPhase.OVER -> "Finished"
                    s.stage == "SET" && s.iAmSetter -> "✍️ Your turn to choose a secret word"
                    s.stage == "SET" -> "⏳ ${engine.theirName} is choosing a secret word…"
                    reveal && s.roundOutcome == 1 -> if (s.roundScorer == 1) "🎉 Solved! +${s.roundPoints} points to you" else "${engine.theirName} solved it: +${s.roundPoints}"
                    reveal -> if (s.mode == 1) "💀 Nobody got it. The word was shown above." else if (s.roundScorer == 1) "😈 You stumped them! +${s.roundPoints} points" else "💀 Hanged! +${s.roundPoints} to ${engine.theirName}"
                    s.iAmSetter -> "⏳ ${engine.theirName} is guessing your word…"
                    s.shared && s.myTurn -> "👉 Your turn: pick a letter"
                    s.shared -> "⏳ ${engine.theirName}'s turn to guess"
                    else -> "👉 Guess a letter"
                },
                color = Color.White, fontWeight = FontWeight.Bold, modifier = Modifier.padding(vertical = 8.dp), textAlign = TextAlign.Center,
            )
            Row(Modifier.fillMaxWidth().height(170.dp), verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.Center) {
                Canvas(Modifier.size(170.dp)) {
                    val k = size.width / 100f
                    withTransform({ scale(k, k, Offset.Zero) }) { drawGallows(s.misses) }
                }
                Column(Modifier.padding(start = 10.dp)) {
                    Text("Lives", color = Color.White.copy(alpha = 0.7f), fontSize = 12.sp)
                    Text("❤️".repeat((HangmanRules.MAX_MISSES - s.misses).coerceAtLeast(0)) + "🖤".repeat(s.misses.coerceAtMost(HangmanRules.MAX_MISSES)), fontSize = 13.sp)
                }
            }
            if (s.stage != "SET" || !s.iAmSetter) {
                // the word, one box per letter
                Row(Modifier.padding(vertical = 10.dp), horizontalArrangement = Arrangement.spacedBy(5.dp)) {
                    for (c in shown) {
                        val missing = c == '_'
                        Box(
                            Modifier.size(width = 26.dp, height = 38.dp).clip(RoundedCornerShape(6.dp))
                                .background(if (missing) Color(0xFF334155) else if (reveal && s.roundOutcome == 2) Color(0xFF7F1D1D) else Color(0xFF16A34A))
                                .border(1.5.dp, Color.White.copy(alpha = 0.5f), RoundedCornerShape(6.dp)),
                            contentAlignment = Alignment.Center,
                        ) { if (!missing) Text("$c", color = Color.White, fontWeight = FontWeight.Black, fontSize = 20.sp) }
                    }
                }
                if (s.hint.isNotBlank()) Text("💡 Hint: ${s.hint}", color = Color(0xFFFDE68A), fontSize = 14.sp)
            }
            if (s.stage == "SET" && s.iAmSetter) {
                Text("Type a word that isn't too easy! Letters only, 3 to 14, no spaces.", color = Color.White.copy(alpha = 0.8f), fontSize = 13.sp, textAlign = TextAlign.Center, modifier = Modifier.padding(top = 8.dp))
                OutlinedTextField(
                    value = typed, onValueChange = { typed = it.filter { c -> c.isLetter() }.take(HangmanRules.MAX_LEN).uppercase() }, singleLine = true,
                    label = { Text("Your secret word") }, modifier = Modifier.fillMaxWidth().padding(top = 6.dp),
                    keyboardOptions = KeyboardOptions(capitalization = KeyboardCapitalization.Characters),
                )
                OutlinedTextField(
                    value = hintText, onValueChange = { hintText = it.take(30) }, singleLine = true,
                    label = { Text("A hint (optional), e.g. \"an animal\"") }, modifier = Modifier.fillMaxWidth().padding(top = 6.dp),
                )
                Row(Modifier.fillMaxWidth().padding(top = 8.dp), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    FunkyButton("🎲 Pick one for me", { typed = HangmanWords.ALL.random() }, Modifier.weight(1f), listOf(Color(0xFF7C3AED), Color(0xFFEC4899)), fontSize = 14.sp)
                    FunkyButton("Lock it in", { engine.setWord(typed, hintText) }, Modifier.weight(1f), listOf(Color(0xFF16A34A), Color(0xFF0E9F6E)), enabled = HangmanRules.clean(typed) != null, fontSize = 14.sp)
                }
            }
            if (s.stage == "GUESS" || reveal) {
                Column(Modifier.padding(top = 8.dp), verticalArrangement = Arrangement.spacedBy(6.dp), horizontalAlignment = Alignment.CenterHorizontally) {
                    for (row in listOf("QWERTYUIOP", "ASDFGHJKL", "ZXCVBNM")) {
                        Row(horizontalArrangement = Arrangement.spacedBy(5.dp)) {
                            for (c in row) {
                                val isUsed = c in usedSet
                                val hit = isUsed && (if (s.word.isNotEmpty()) s.word.contains(c) else s.mask.contains(c))
                                val enabled = s.stage == "GUESS" && s.myTurn && !isUsed
                                Box(
                                    Modifier.size(width = 31.dp, height = 42.dp).clip(RoundedCornerShape(8.dp))
                                        .background(
                                            when {
                                                isUsed && hit -> Color(0xFF16A34A)
                                                isUsed -> Color(0xFF7F1D1D)
                                                enabled -> Color(0xFF475569)
                                                else -> Color(0xFF1E293B)
                                            },
                                        )
                                        .clickable(enabled = enabled) { engine.guessLetter(c) },
                                    contentAlignment = Alignment.Center,
                                ) { Text("$c", color = if (isUsed || enabled) Color.White else Color.White.copy(alpha = 0.35f), fontWeight = FontWeight.Bold, fontSize = 16.sp) }
                            }
                        }
                    }
                }
                if (s.stage == "GUESS" && s.myTurn) {
                    TextButton(onClick = { guess = ""; wordDialog = true }) { Text("🎯 Guess the whole word (a miss costs 2 lives)") }
                }
            }
        }
        if (wordDialog) {
            AlertDialog(
                onDismissRequest = { wordDialog = false },
                title = { Text("Guess the whole word") },
                text = {
                    OutlinedTextField(value = guess, onValueChange = { guess = it.filter { c -> c.isLetter() }.take(HangmanRules.MAX_LEN).uppercase() }, singleLine = true, label = { Text("${s.mask.length} letters") })
                },
                confirmButton = { TextButton(onClick = { wordDialog = false; engine.guessWord(guess) }, enabled = guess.length == s.mask.length) { Text("Guess it") } },
                dismissButton = { TextButton(onClick = { wordDialog = false }) { Text("Cancel") } },
            )
        }
        if (s.phase == GPhase.OVER) {
            EndOverlay(
                title = when (s.result) { 1 -> "🏆 YOU WIN!"; 2 -> "🏆 ${engine.theirName.uppercase()} WINS!"; else -> "🤝 IT'S A DRAW!" },
                subtitle = "Points: you ${s.myScore} · ${engine.theirName} ${s.theirScore}",
                win = s.result == 1, isHost = engine.isHost, theirName = engine.theirName,
                playAgainLabel = "Play again", colors = listOf(Color(0xFF3B2A1A), Color(0xFFF59E0B)),
                onPlayAgain = onPlayAgain, onBackToGames = onBackToGames, onLeave = onLeave,
                how = if (s.word.isNotEmpty()) "The last word was ${s.word}." else "",
            )
        }
    }
}

internal fun DrawScope.hangmanIcon() {
    val white = Color.White.copy(alpha = 0.95f)
    drawLine(white, Offset(20f, 88f), Offset(70f, 88f), 6f, StrokeCap.Round)
    drawLine(white, Offset(32f, 88f), Offset(32f, 14f), 6f, StrokeCap.Round)
    drawLine(white, Offset(32f, 14f), Offset(66f, 14f), 6f, StrokeCap.Round)
    drawLine(Color(0xFFFBBF24), Offset(66f, 14f), Offset(66f, 28f), 4f, StrokeCap.Round)
    drawCircle(white, 9f, Offset(66f, 38f), style = Stroke(4.5f))
    drawLine(white, Offset(66f, 47f), Offset(66f, 68f), 4.5f, StrokeCap.Round)
    drawLine(white, Offset(66f, 54f), Offset(55f, 63f), 4f, StrokeCap.Round)
    drawLine(white, Offset(66f, 54f), Offset(77f, 63f), 4f, StrokeCap.Round)
    drawLine(white, Offset(66f, 68f), Offset(57f, 82f), 4f, StrokeCap.Round)
}
