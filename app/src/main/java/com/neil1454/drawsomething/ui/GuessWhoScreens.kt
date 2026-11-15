package com.neil1454.drawsomething.ui

import androidx.compose.animation.core.animateFloatAsState
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
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.runtime.mutableStateListOf
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.drawscope.DrawScope
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.drawscope.withTransform
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.neil1454.drawsomething.core.GPhase
import com.neil1454.drawsomething.core.GuessWhoData
import com.neil1454.drawsomething.core.GuessWhoEngine
import com.neil1454.drawsomething.core.GuessWhoState
import com.neil1454.drawsomething.core.GwChar
import com.neil1454.drawsomething.core.Sound
import com.neil1454.drawsomething.sound.Sfx
import kotlinx.coroutines.delay

@Composable
fun GuessWhoHelp() {
    Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
        Text("Each of you secretly picks one face from the same board. Then take turns asking yes/no questions about the other player's face.")
        Text("• Pick your secret face first. Your partner can't see which one you chose.")
        Text("• On your turn, ask one question (like \"Do they wear glasses?\"). The answer comes back straight away, and then it's their turn.")
        Text("• Tap faces to flip them down once you know they can't be it. With auto-flip on, the app flips the impossible faces for you.")
        Text("• When you're sure, tap \"Make a guess\" and tap a face. Get it right and you win. Get it wrong and you lose on the spot!")
    }
}

private val Skin = listOf(Color(0xFFF5D0B0), Color(0xFFE8B88A), Color(0xFFC68642), Color(0xFF8D5524), Color(0xFFF1C27D))
private val Hair = listOf(Color(0xFF1F1F1F), Color(0xFF6B4423), Color(0xFFE6C35C), Color(0xFFB7410E), Color(0xFF9CA3AF))
private val Eyes = listOf(Color(0xFF5B3A1A), Color(0xFF3B82F6), Color(0xFF22A06B))
private val Shirts = listOf(Color(0xFFEF4444), Color(0xFF3B82F6), Color(0xFF22C55E), Color(0xFFF59E0B), Color(0xFF8B5CF6), Color(0xFFEC4899))
private val Hats = listOf(Color(0xFF1E3A8A), Color(0xFFB91C1C), Color(0xFF166534), Color(0xFF7C2D12))

/** A face, drawn in a 100 x 100 box. */
private fun DrawScope.drawFace(c: GwChar) {
    val skin = Skin[c.id % Skin.size]
    val hair = Hair[c.hair]
    // background and shoulders
    drawRect(Brush.verticalGradient(listOf(Color(0xFFDBEAFE), Color(0xFFBFDBFE))), Offset.Zero, Size(100f, 100f))
    drawOval(Shirts[c.id % Shirts.size], Offset(14f, 76f), Size(72f, 40f))
    drawRect(skin, Offset(43f, 66f), Size(14f, 14f))
    // long hair behind the head
    if (c.style == 1) drawRoundRect(hair, Offset(20f, 24f), Size(60f, 62f), androidx.compose.ui.geometry.CornerRadius(26f))
    if (c.style == 2) for ((hx, hy) in listOf(24f to 34f, 76f to 34f, 20f to 48f, 80f to 48f, 28f to 24f, 72f to 24f)) drawCircle(hair, 11f, Offset(hx, hy))
    // the face
    drawOval(skin, Offset(26f, 22f), Size(48f, 56f))
    drawOval(Color.Black.copy(alpha = 0.12f), Offset(26f, 22f), Size(48f, 56f), style = Stroke(1.2f))
    // ears (and earrings)
    drawCircle(skin, 5f, Offset(25f, 50f)); drawCircle(skin, 5f, Offset(75f, 50f))
    if (c.earrings) { drawCircle(Color(0xFFFFD23F), 3f, Offset(25f, 57f)); drawCircle(Color(0xFFFFD23F), 3f, Offset(75f, 57f)) }
    // hair on top
    when (c.style) {
        0 -> { drawArc(hair, 180f, 180f, true, Offset(25f, 14f), Size(50f, 40f)); drawRect(hair, Offset(25f, 34f), Size(6f, 10f)); drawRect(hair, Offset(69f, 34f), Size(6f, 10f)) }
        1 -> drawArc(hair, 180f, 180f, true, Offset(24f, 14f), Size(52f, 38f))
        2 -> for ((hx, hy) in listOf(34f to 24f, 50f to 18f, 66f to 24f, 42f to 20f, 58f to 20f)) drawCircle(hair, 10f, Offset(hx, hy))
        else -> drawOval(Color.White.copy(alpha = 0.35f), Offset(40f, 26f), Size(14f, 8f))
    }
    if (c.hat) {
        val col = Hats[c.id % Hats.size]
        drawArc(col, 180f, 180f, true, Offset(24f, 6f), Size(52f, 40f))
        drawRoundRect(col, Offset(18f, 28f), Size(64f, 8f), androidx.compose.ui.geometry.CornerRadius(4f))
    }
    // eyes
    for (ex in listOf(39f, 61f)) {
        drawOval(Color.White, Offset(ex - 6f, 44f), Size(12f, 9f))
        drawCircle(Eyes[c.eyes], 3.4f, Offset(ex, 48.5f))
        drawCircle(Color.Black, 1.6f, Offset(ex, 48.5f))
    }
    drawLine(hair.copy(alpha = 0.9f), Offset(33f, 41f), Offset(45f, 40f), 1.6f); drawLine(hair.copy(alpha = 0.9f), Offset(55f, 40f), Offset(67f, 41f), 1.6f)
    // nose and mouth
    drawLine(skin.copy(alpha = 0.4f).let { Color(0x55000000) }, Offset(50f, 50f), Offset(48f, 60f), 1.4f)
    drawArc(Color(0xFF7F1D1D), 20f, 140f, false, Offset(40f, 60f), Size(20f, 12f), style = Stroke(2f))
    if (c.moustache) drawRoundRect(hair, Offset(40f, 59f), Size(20f, 4f), androidx.compose.ui.geometry.CornerRadius(2f))
    if (c.beard) {
        val p = Path().apply { moveTo(27f, 58f); quadraticBezierTo(50f, 98f, 73f, 58f); quadraticBezierTo(50f, 72f, 27f, 58f); close() }
        drawPath(p, hair)
    }
    if (c.glasses) {
        drawCircle(Color(0xFF111827), 8.5f, Offset(39f, 48.5f), style = Stroke(2f)); drawCircle(Color(0xFF111827), 8.5f, Offset(61f, 48.5f), style = Stroke(2f))
        drawLine(Color(0xFF111827), Offset(47.5f, 48.5f), Offset(52.5f, 48.5f), 2f)
    }
}

@Composable
private fun FaceCard(c: GwChar, flipped: Boolean, highlight: Boolean, mine: Boolean, onClick: () -> Unit, modifier: Modifier = Modifier) {
    val down by animateFloatAsState(if (flipped) 1f else 0f, tween(350), label = "flip")
    val shape = RoundedCornerShape(12.dp)
    Column(
        modifier.graphicsLayer { scaleX = kotlin.math.abs(1f - 2f * down).coerceAtLeast(0.04f) }
            .clip(shape).background(if (down >= 0.5f) Color(0xFF334155) else Color(0xFFF8FAFC))
            .border(if (highlight) 3.dp else 1.5.dp, if (highlight) Color(0xFFFFD23F) else if (mine) Color(0xFF22C55E) else Color(0xFF94A3B8), shape)
            .clickable(onClick = onClick).padding(4.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
    ) {
        if (down >= 0.5f) {
            Box(Modifier.fillMaxWidth().aspectRatio(1f), contentAlignment = Alignment.Center) { Text("✖", color = Color(0xFF94A3B8), fontSize = 28.sp) }
        } else {
            Canvas(Modifier.fillMaxWidth().aspectRatio(1f).clip(RoundedCornerShape(8.dp))) {
                val k = size.width / 100f
                withTransform({ scale(k, k, Offset.Zero) }) { drawFace(c) }
            }
        }
        Text(c.name, fontSize = 12.sp, fontWeight = FontWeight.Bold, color = if (down >= 0.5f) Color(0xFF94A3B8) else Color(0xFF0F172A), maxLines = 1)
    }
}

@Composable
private fun SmallFace(c: GwChar, label: String, size: Dp) {
    Column(horizontalAlignment = Alignment.CenterHorizontally) {
        Text(label, color = Color.White.copy(alpha = 0.8f), fontSize = 11.sp)
        Canvas(Modifier.size(size).clip(RoundedCornerShape(10.dp)).border(2.dp, Color(0xFFFFD23F), RoundedCornerShape(10.dp))) {
            val k = this.size.width / 100f
            withTransform({ scale(k, k, Offset.Zero) }) { drawFace(c) }
        }
        Text(c.name, color = Color.White, fontSize = 12.sp, fontWeight = FontWeight.Bold)
    }
}

@Composable
fun GuessWhoScreen(
    s: GuessWhoState,
    engine: GuessWhoEngine,
    onPlayAgain: () -> Unit,
    onBackToGames: () -> Unit,
    onLeave: () -> Unit,
) {
    val faces = s.ids.map { GuessWhoData.ALL[it] }
    val manual = remember { mutableStateListOf<Int>() }
    var pending by remember { mutableIntStateOf(-1) }
    var guessMode by remember { mutableStateOf(false) }
    var askOpen by remember { mutableStateOf(false) }
    var popup by remember { mutableStateOf<String?>(null) }
    LaunchedEffect(s.stage, s.ids) { if (s.stage == "PICK") { manual.clear(); guessMode = false } }
    // the faces my answers rule out
    val auto = if (s.autoFlip) faces.filter { f -> s.myQuestions.any { GuessWhoData.QUESTIONS[it.q].test(f) != it.yes } }.map { it.id }.toSet() else emptySet()
    fun flipped(id: Int) = (id in auto) != (id in manual)
    // a big answer when a question comes back
    LaunchedEffect(s.history.size) {
        val last = s.history.lastOrNull() ?: return@LaunchedEffect
        popup = (if (last.asker == 1) "You: " else "They asked: ") + GuessWhoData.QUESTIONS[last.q].text + "\n" + (if (last.yes) "✅ YES!" else "❌ NO!")
        Sfx.play(if (last.yes) Sound.GOOD else Sound.TAP)
        delay(2200)
        popup = null
    }
    val myFace = s.mySecret.takeIf { it >= 0 }?.let { GuessWhoData.ALL[it] }

    Box(Modifier.fillMaxSize().background(Brush.verticalGradient(listOf(Color(0xFF0F172A), Color(0xFF1E3A8A), Color(0xFF3B0764))))) {
        Column(
            Modifier.fillMaxSize().verticalScroll(rememberScrollState()).padding(horizontal = 10.dp, vertical = 10.dp),
            verticalArrangement = Arrangement.spacedBy(8.dp), horizontalAlignment = Alignment.CenterHorizontally,
        ) {
            Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                if (myFace != null) SmallFace(myFace, "Your face", 54.dp)
                Column(Modifier.weight(1f), horizontalAlignment = Alignment.CenterHorizontally) {
                    Text("🕵️ GUESS WHO?", color = Color(0xFFFFD23F), fontWeight = FontWeight.Black, fontSize = 18.sp, letterSpacing = 2.sp)
                    Text(
                        when {
                            s.stage == "PICK" && !s.myPicked -> "Tap a face to choose it as YOUR secret face"
                            s.stage == "PICK" -> "Waiting for ${engine.theirName} to choose…"
                            s.myTurn && guessMode -> "Tap the face you think is theirs"
                            s.myTurn -> "Your turn: ask a question, or make a guess"
                            else -> "${engine.theirName} is thinking…"
                        },
                        color = Color.White, fontWeight = FontWeight.Bold, textAlign = TextAlign.Center, fontSize = 14.sp,
                    )
                }
            }
            // the board
            BoxWithConstraints(Modifier.fillMaxWidth()) {
                val cols = 4
                val gap = 6.dp
                val cell = (maxWidth - gap * (cols - 1)) / cols
                Column(verticalArrangement = Arrangement.spacedBy(gap)) {
                    faces.chunked(cols).forEach { row ->
                        Row(horizontalArrangement = Arrangement.spacedBy(gap)) {
                            row.forEach { f ->
                                FaceCard(
                                    f, flipped = flipped(f.id) && s.stage == "PLAY", highlight = f.id == s.mySecret, mine = f.id == s.mySecret,
                                    onClick = {
                                        when {
                                            s.phase != GPhase.PLAYING -> Unit
                                            s.stage == "PICK" && !s.myPicked -> pending = f.id
                                            s.stage == "PLAY" && s.myTurn && guessMode -> pending = f.id
                                            s.stage == "PLAY" -> { if (f.id in manual) manual.remove(f.id) else manual.add(f.id); Sfx.play(Sound.FLIP) }
                                        }
                                    },
                                    modifier = Modifier.size(cell, cell * 1.2f),
                                )
                            }
                        }
                    }
                }
            }
            // what to do
            if (s.stage == "PLAY") {
                if (s.myTurn) {
                    Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        FunkyButton("❓ Ask a question", { guessMode = false; askOpen = true }, Modifier.weight(1f), listOf(Funk.Violet, Funk.Blue), fontSize = 15.sp)
                        FunkyButton(if (guessMode) "✖ Cancel guess" else "🎯 Make a guess", { guessMode = !guessMode }, Modifier.weight(1f), if (guessMode) listOf(Funk.Pink, Funk.Red) else listOf(Funk.Orange, Funk.Red), fontSize = 15.sp)
                    }
                } else {
                    Text("⏳ Waiting for ${engine.theirName}…", color = Color.White.copy(alpha = 0.8f))
                }
                if (!s.autoFlip) Text("Tap faces to flip them down as you rule them out.", color = Color.White.copy(alpha = 0.6f), fontSize = 11.sp)
                // what has been asked so far
                s.history.takeLast(5).reversed().forEach { qa ->
                    Text(
                        (if (qa.asker == 1) "You asked" else "${engine.theirName} asked") + ": " + GuessWhoData.QUESTIONS[qa.q].text + "  " + (if (qa.yes) "✅" else "❌"),
                        color = Color.White.copy(alpha = if (qa.asker == 1) 1f else 0.7f), fontSize = 12.sp,
                    )
                }
            }
        }
        popup?.let {
            Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                Text(
                    it, color = Color.White, fontWeight = FontWeight.Black, fontSize = 22.sp, textAlign = TextAlign.Center,
                    modifier = Modifier.clip(RoundedCornerShape(20.dp)).background(Color(0xE60B0624)).border(2.dp, Color(0xFFFFD23F), RoundedCornerShape(20.dp)).padding(horizontal = 22.dp, vertical = 16.dp),
                )
            }
        }
        if (s.phase == GPhase.OVER) {
            val theirFace = s.theirSecret.takeIf { it >= 0 }?.let { GuessWhoData.ALL[it] }
            EndOverlay(
                title = if (s.result == 1) "🏆 YOU WIN!" else "😬 ${engine.theirName.uppercase()} WINS",
                subtitle = "Faces: yours was ${myFace?.name ?: "?"}, theirs was ${theirFace?.name ?: "?"}",
                win = s.result == 1, isHost = engine.isHost, theirName = engine.theirName,
                playAgainLabel = "Play again", colors = listOf(Color(0xFF1D4ED8), Color(0xFFEC4899)),
                onPlayAgain = onPlayAgain, onBackToGames = onBackToGames, onLeave = onLeave,
                how = when {
                    s.guessBy == 1 && s.result == 1 -> "You named ${theirFace?.name} correctly after ${s.myQuestions.size} questions."
                    s.guessBy == 1 -> "You guessed ${GuessWhoData.ALL.getOrNull(s.guessId)?.name ?: "a face"} but it was ${theirFace?.name}. A wrong guess loses!"
                    s.result == 2 -> "${engine.theirName} named your face, ${myFace?.name}, correctly."
                    else -> "${engine.theirName} guessed ${GuessWhoData.ALL.getOrNull(s.guessId)?.name ?: "a face"} by mistake, so you win!"
                },
            )
            if (myFace != null && theirFace != null) {
                Row(Modifier.align(Alignment.TopCenter).padding(top = 40.dp), horizontalArrangement = Arrangement.spacedBy(16.dp)) {
                    SmallFace(myFace, "Yours", 70.dp)
                    SmallFace(theirFace, "Theirs", 70.dp)
                }
            }
        }
    }

    if (pending >= 0) {
        val f = GuessWhoData.ALL[pending]
        val picking = s.stage == "PICK"
        AlertDialog(
            onDismissRequest = { pending = -1 },
            title = { Text(if (picking) "Make ${f.name} your secret face?" else "Is it ${f.name}?") },
            text = { Text(if (picking) "Your partner won't know. You can't change it later." else "If you're wrong you lose straight away!") },
            confirmButton = {
                TextButton(onClick = {
                    val id = pending; pending = -1
                    if (picking) { Sfx.play(Sound.CARD); engine.pick(id) } else { guessMode = false; engine.guess(id) }
                }) { Text(if (picking) "Yes, that's mine" else "Yes, guess ${f.name}") }
            },
            dismissButton = { TextButton(onClick = { pending = -1 }) { Text("No") } },
        )
    }
    if (askOpen) {
        val used = s.myQuestions.map { it.q }.toSet()
        AlertDialog(
            onDismissRequest = { askOpen = false },
            title = { Text("Ask a question") },
            text = {
                Column(Modifier.verticalScroll(rememberScrollState()), verticalArrangement = Arrangement.spacedBy(2.dp)) {
                    GuessWhoData.QUESTIONS.forEachIndexed { i, q ->
                        val done = i in used
                        Text(
                            (if (done) "✔ " else "") + q.text, fontSize = 15.sp,
                            color = if (done) Color.Gray else Color.Unspecified,
                            modifier = Modifier.fillMaxWidth().clickable(enabled = !done) { askOpen = false; Sfx.play(Sound.TICK); engine.ask(i) }.padding(vertical = 8.dp),
                        )
                    }
                }
            },
            confirmButton = { TextButton(onClick = { askOpen = false }) { Text("Close") } },
        )
    }
}
