package com.neil1454.drawsomething.ui

import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.LinearEasing
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
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
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Surface
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.window.DialogProperties
import androidx.compose.foundation.layout.fillMaxHeight
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
import com.neil1454.drawsomething.core.GwQA
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
        Text("Each of you secretly picks one card from the same board: people, animals or everyday items, whichever set the host chose. Then take turns asking yes/no questions about the other player's face.")
        Text("• Pick your secret face first. Your partner can't see which one you chose.")
        Text("• On your turn, ask one question from the list (or type your own). Your partner looks at their face and taps Yes or No, then it's their turn to ask you.")
        Text("• When you are asked something, answer about YOUR secret face. The app makes sure answers to the listed questions are true.")
        Text("• Tap faces to flip them down once you know they can't be it. With auto-flip on, the app flips the impossible faces for you.")
        Text("• When you're sure, tap \"Make a guess\" and tap a face. Get it right and you win. Get it wrong and you lose on the spot!")
    }
}

private val Skin = listOf(Color(0xFFF5D0B0), Color(0xFFE8B88A), Color(0xFFC68642), Color(0xFF8D5524), Color(0xFFF1C27D))
private val Hair = listOf(Color(0xFF1F1F1F), Color(0xFF6B4423), Color(0xFFE6C35C), Color(0xFFB7410E), Color(0xFF9CA3AF))
private val Eyes = listOf(Color(0xFF5B3A1A), Color(0xFF3B82F6), Color(0xFF22A06B))
private val Shirts = listOf(Color(0xFFEF4444), Color(0xFF3B82F6), Color(0xFF22C55E), Color(0xFFF59E0B), Color(0xFF8B5CF6), Color(0xFFEC4899))
private val Hats = listOf(Color(0xFF1E3A8A), Color(0xFFB91C1C), Color(0xFF166534), Color(0xFF7C2D12))

/** A person, drawn in a 100 x 100 box. */
private fun DrawScope.drawFace(c: GwChar) {
    val l = c.look ?: return
    val skin = Skin[l.skin % Skin.size]
    val hair = Hair[l.hair]
    val dark = Color(0xFF111827)
    val rr = androidx.compose.ui.geometry.CornerRadius(4f)
    drawRect(Brush.verticalGradient(listOf(Color(0xFFDBEAFE), Color(0xFFBFDBFE))), Offset.Zero, Size(100f, 100f))
    drawOval(Shirts[l.shirt % Shirts.size], Offset(14f, 76f), Size(72f, 40f))
    drawRect(skin, Offset(43f, 66f), Size(14f, 14f))
    if (l.style == 1) drawRoundRect(hair, Offset(20f, 24f), Size(60f, 62f), androidx.compose.ui.geometry.CornerRadius(26f))
    if (l.style == 2) for ((hx, hy) in listOf(24f to 34f, 76f to 34f, 20f to 48f, 80f to 48f, 28f to 24f, 72f to 24f)) drawCircle(hair, 11f, Offset(hx, hy))
    if (l.scarf) { drawRoundRect(Color(0xFFDC2626), Offset(33f, 68f), Size(34f, 9f), androidx.compose.ui.geometry.CornerRadius(4f)); drawRect(Color(0xFFDC2626), Offset(56f, 72f), Size(7f, 14f)) }
    drawOval(skin, Offset(26f, 22f), Size(48f, 56f))
    drawOval(Color.Black.copy(alpha = 0.12f), Offset(26f, 22f), Size(48f, 56f), style = Stroke(1.2f))
    drawCircle(skin, 5f, Offset(25f, 50f)); drawCircle(skin, 5f, Offset(75f, 50f))
    if (l.earrings) { drawCircle(Color(0xFFFFD23F), 3.5f, Offset(25f, 58f)); drawCircle(Color(0xFFFFD23F), 3.5f, Offset(75f, 58f)) }
    when (l.style) {
        0 -> { drawArc(hair, 180f, 180f, true, Offset(25f, 14f), Size(50f, 40f)); drawRect(hair, Offset(25f, 34f), Size(6f, 10f)); drawRect(hair, Offset(69f, 34f), Size(6f, 10f)) }
        1 -> drawArc(hair, 180f, 180f, true, Offset(24f, 14f), Size(52f, 38f))
        2 -> for ((hx, hy) in listOf(34f to 24f, 50f to 18f, 66f to 24f, 42f to 20f, 58f to 20f)) drawCircle(hair, 10f, Offset(hx, hy))
        4 -> { drawArc(hair, 180f, 180f, true, Offset(25f, 14f), Size(50f, 40f)); drawCircle(hair, 9f, Offset(50f, 10f)); drawCircle(Color(0xFFEC4899), 3f, Offset(50f, 19f)) }
        else -> drawOval(Color.White.copy(alpha = 0.35f), Offset(40f, 26f), Size(14f, 8f))
    }
    if (l.hat) {
        val col = Hats[l.hatColour % Hats.size]
        drawArc(col, 180f, 180f, true, Offset(24f, 6f), Size(52f, 40f))
        drawRoundRect(col, Offset(18f, 28f), Size(64f, 8f), androidx.compose.ui.geometry.CornerRadius(4f))
    }
    for (ex in listOf(39f, 61f)) {
        drawOval(Color.White, Offset(ex - 6f, 44f), Size(12f, 9f))
        drawCircle(Eyes[l.eyes], 3.6f, Offset(ex, 48.5f))
        drawCircle(Color.Black, 1.6f, Offset(ex, 48.5f))
    }
    val brow = if (l.style == 3 || l.hair == 4) Color(0xFF6B7280) else hair
    val bw = if (l.brows) 4.4f else 1.4f
    drawLine(brow, Offset(32f, 41f), Offset(45f, if (l.brows) 38.5f else 40f), bw); drawLine(brow, Offset(55f, if (l.brows) 38.5f else 40f), Offset(68f, 41f), bw)
    if (l.rosy) { drawCircle(Color(0xFFF87171).copy(alpha = 0.6f), 6f, Offset(34f, 62f)); drawCircle(Color(0xFFF87171).copy(alpha = 0.6f), 6f, Offset(66f, 62f)) }
    if (l.freckles) for ((fx, fy) in listOf(36f to 56f, 41f to 59f, 33f to 60f, 64f to 56f, 59f to 59f, 67f to 60f)) drawCircle(Color(0xFF9A5B2B), 1.5f, Offset(fx, fy))
    if (l.bigNose) drawOval(skin.copy(alpha = 1f).let { Color(0xFFB45F3A).copy(alpha = 0.55f) }, Offset(43f, 50f), Size(14f, 14f))
    else drawLine(Color(0x55000000), Offset(50f, 50f), Offset(48f, 60f), 1.4f)
    if (l.smile) {
        drawArc(Color(0xFF7F1D1D), 0f, 180f, true, Offset(38f, 62f), Size(24f, 13f))
        drawRect(Color.White, Offset(41f, 62.5f), Size(18f, 3.5f))
    } else drawArc(Color(0xFF7F1D1D), 20f, 140f, false, Offset(40f, 60f), Size(20f, 12f), style = Stroke(2f))
    val facial = if (l.hair == 4 || l.style == 3) Hair[l.hair] else hair
    if (l.moustache) drawRoundRect(facial, Offset(39f, 58f), Size(22f, 5f), rr)
    if (l.beard) {
        val path = Path().apply { moveTo(27f, 58f); quadraticBezierTo(50f, 98f, 73f, 58f); quadraticBezierTo(50f, 72f, 27f, 58f); close() }
        drawPath(path, facial)
    }
    if (l.bowtie) { drawCircle(dark, 1f, Offset(50f, 82f)); drawRect(Color(0xFFDC2626), Offset(40f, 78f), Size(8f, 8f)); drawRect(Color(0xFFDC2626), Offset(52f, 78f), Size(8f, 8f)); drawCircle(Color(0xFF7F1D1D), 2.5f, Offset(50f, 82f)) }
    if (l.glasses) {
        drawCircle(dark, 8.5f, Offset(39f, 48.5f), style = Stroke(2f)); drawCircle(dark, 8.5f, Offset(61f, 48.5f), style = Stroke(2f))
        drawLine(dark, Offset(47.5f, 48.5f), Offset(52.5f, 48.5f), 2f)
    }
}

/** The picture on a card: a drawn person, or a big emoji for animals and items. */
@Composable
private fun CardArt(c: GwChar, modifier: Modifier) {
    if (c.look != null) {
        Canvas(modifier.clip(RoundedCornerShape(8.dp))) {
            val k = size.width / 100f
            withTransform({ scale(k, k, Offset.Zero) }) { drawFace(c) }
        }
    } else {
        BoxWithConstraints(modifier.clip(RoundedCornerShape(8.dp)).background(Brush.verticalGradient(if (c.kind == 1) listOf(Color(0xFFDCFCE7), Color(0xFFBBF7D0)) else listOf(Color(0xFFFEF3C7), Color(0xFFFDE68A)))), contentAlignment = Alignment.Center) {
            Text(c.emoji, fontSize = (maxWidth.value * 0.62f).sp)
        }
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
            CardArt(c, Modifier.fillMaxWidth().aspectRatio(1f))
        }
        Text(c.name, fontSize = 11.sp, fontWeight = FontWeight.Bold, color = if (down >= 0.5f) Color(0xFF94A3B8) else Color(0xFF0F172A), maxLines = 1)
    }
}

@Composable
private fun SmallFace(c: GwChar, label: String, size: Dp) {
    Column(horizontalAlignment = Alignment.CenterHorizontally) {
        Text(label, color = Color.White.copy(alpha = 0.8f), fontSize = 11.sp)
        CardArt(c, Modifier.size(size).border(2.dp, Color(0xFFFFD23F), RoundedCornerShape(10.dp)))
        Text(c.name, color = Color.White, fontSize = 12.sp, fontWeight = FontWeight.Bold)
    }
}

@Composable
private fun AnswerCard(s: GuessWhoState, name: String, myFace: GwChar?, onAnswer: (Boolean) -> Unit) {
    val q = s.pending ?: return
    val shape = RoundedCornerShape(20.dp)
    val shake = remember(s.note) { Animatable(0f) }
    LaunchedEffect(s.note) { if (s.note.isNotEmpty()) { Sfx.play(Sound.BUZZ); shake.snapTo(1f); shake.animateTo(0f, tween(500)) } }
    Column(
        Modifier.fillMaxWidth().graphicsLayer { translationX = kotlin.math.sin(shake.value * 40f) * 18f * shake.value }
            .clip(shape).background(Brush.verticalGradient(listOf(Color(0xFF312E81), Color(0xFF1E1B4B)))).border(3.dp, Color(0xFFFFD23F), shape).padding(14.dp),
        horizontalAlignment = Alignment.CenterHorizontally, verticalArrangement = Arrangement.spacedBy(8.dp),
    ) {
        Text("🕵️ $name asks you:", color = Color(0xFFFFD23F), fontWeight = FontWeight.Bold, fontSize = 14.sp)
        Text(q.question, color = Color.White, fontWeight = FontWeight.Black, fontSize = 22.sp, textAlign = TextAlign.Center)
        if (myFace != null) {
            Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                SmallFace(myFace, "Your face", 76.dp)
                Text("Look at your own face, then answer honestly.", color = Color.White.copy(alpha = 0.8f), fontSize = 13.sp, modifier = Modifier.weight(1f, fill = false))
            }
        }
        if (s.note.isNotEmpty()) Text("⚠️ ${s.note}", color = Color(0xFFFCA5A5), fontWeight = FontWeight.Bold, textAlign = TextAlign.Center)
        Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(10.dp)) {
            FunkyButton("✅  YES", { Sfx.play(Sound.GOOD); onAnswer(true) }, Modifier.weight(1f), listOf(Color(0xFF16A34A), Color(0xFF65A30D)), fontSize = 22.sp)
            FunkyButton("❌  NO", { Sfx.play(Sound.TAP); onAnswer(false) }, Modifier.weight(1f), listOf(Color(0xFFDC2626), Color(0xFFEA580C)), fontSize = 22.sp)
        }
    }
}

@Composable
private fun QuestionChip(q: com.neil1454.drawsomething.core.GwQuestion, done: GwQA?, best: Boolean, counts: Pair<Int, Int>?, modifier: Modifier, onClick: () -> Unit) {
    val shape = RoundedCornerShape(16.dp)
    val asked = done != null
    Row(
        modifier.clip(shape)
            .background(Brush.verticalGradient(if (asked) listOf(Color(0x22FFFFFF), Color(0x22FFFFFF)) else listOf(Color(0xFF3B3F8F), Color(0xFF272A66))))
            .border(if (best) 3.dp else 1.dp, if (best) Color(0xFFFFD23F) else Color.White.copy(alpha = if (asked) 0.15f else 0.35f), shape)
            .clickable(enabled = !asked, onClick = onClick).padding(horizontal = 10.dp, vertical = 10.dp),
        verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp),
    ) {
        Text(q.emoji, fontSize = 24.sp)
        Column(Modifier.weight(1f)) {
            Text(q.short, color = if (asked) Color.White.copy(alpha = 0.5f) else Color.White, fontWeight = FontWeight.Bold, fontSize = 15.sp, maxLines = 1)
            when {
                asked -> Text(if (done!!.yes) "✅ Yes" else "❌ No", color = if (done.yes) Color(0xFF86EFAC) else Color(0xFFFCA5A5), fontSize = 12.sp, fontWeight = FontWeight.Bold)
                counts != null -> Text("${counts.first} yes · ${counts.second} no", color = Color.White.copy(alpha = 0.7f), fontSize = 11.sp)
            }
        }
        if (best) Text("💡", fontSize = 16.sp)
    }
}

@Composable
private fun QuestionPicker(s: GuessWhoState, remaining: List<GwChar>, onAsk: (Int) -> Unit, onCustom: (String) -> Unit, onClose: () -> Unit) {
    val used = s.myQuestions.associateBy { it.q }
    val best = if (s.smart) GuessWhoData.bestQuestion(remaining, used.keys) else null
    val useful = remember(s.ids) { GuessWhoData.usefulQuestions(s.ids.map { GuessWhoData.ALL[it] }).toSet() }
    var custom by remember { mutableStateOf("") }
    Dialog(onDismissRequest = onClose, properties = DialogProperties(usePlatformDefaultWidth = false)) {
        Surface(shape = RoundedCornerShape(26.dp), color = Color(0xFF0F172A), modifier = Modifier.fillMaxWidth(0.95f).fillMaxHeight(0.9f)) {
            Column(Modifier.padding(14.dp)) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Column(Modifier.weight(1f)) {
                        Text("❓ Ask a question", color = Color.White, fontWeight = FontWeight.Black, fontSize = 20.sp)
                        Text(if (s.smart) "${remaining.size} faces left in play. The numbers show how many would be Yes and No." else "${remaining.size} faces left in play.", color = Color.White.copy(alpha = 0.65f), fontSize = 12.sp)
                    }
                    Text("✕", color = Color.White, fontSize = 22.sp, modifier = Modifier.clickable(onClick = onClose).padding(10.dp))
                }
                if (best != null) {
                    val bq = GuessWhoData.QUESTIONS[best]
                    FunkyButton("💡 Best question: ${bq.emoji} ${bq.short}", { onAsk(best) }, Modifier.fillMaxWidth().padding(vertical = 8.dp), listOf(Color(0xFFB45309), Color(0xFFF59E0B)), fontSize = 15.sp)
                }
                Column(Modifier.weight(1f).verticalScroll(rememberScrollState()), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    GuessWhoData.QUESTIONS.withIndex().filter { it.index in useful }.map { it.value.group }.distinct().forEach { gname ->
                        Text(gname, color = Color(0xFFFFD23F), fontWeight = FontWeight.Black, fontSize = 14.sp, modifier = Modifier.padding(top = 6.dp))
                        GuessWhoData.QUESTIONS.withIndex().filter { it.value.group == gname && it.index in useful }.chunked(2).forEach { row ->
                            Row(horizontalArrangement = Arrangement.spacedBy(8.dp), modifier = Modifier.fillMaxWidth()) {
                                row.forEach { (qi, q) ->
                                    val counts = if (s.smart) remaining.count { q.test(it) }.let { y -> y to (remaining.size - y) } else null
                                    QuestionChip(q, used[qi], best == qi, counts, Modifier.weight(1f)) { onAsk(qi) }
                                }
                                if (row.size == 1) Box(Modifier.weight(1f))
                            }
                        }
                    }
                    if (s.manual) {
                        Text("✏️ Ask your own", color = Color(0xFFFFD23F), fontWeight = FontWeight.Black, fontSize = 14.sp, modifier = Modifier.padding(top = 10.dp))
                        Text("Anything you like. Your partner answers Yes or No, but the app can't check it, so it's on trust (and it won't flip faces for you).", color = Color.White.copy(alpha = 0.6f), fontSize = 11.sp)
                        Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                            OutlinedTextField(value = custom, onValueChange = { custom = it.take(80) }, singleLine = true, label = { Text("e.g. Do they look friendly?") }, modifier = Modifier.weight(1f))
                            FunkyButton("Ask", { onCustom(custom.trim()) }, Modifier, listOf(Color(0xFF7C3AED), Color(0xFFEC4899)), enabled = custom.trim().length >= 3, fontSize = 15.sp)
                        }
                    }
                }
            }
        }
    }
}

@Composable
private fun Bubbles(s: GuessWhoState, theirName: String) {
    Column(Modifier.fillMaxWidth(), verticalArrangement = Arrangement.spacedBy(5.dp)) {
        s.history.takeLast(6).forEach { qa ->
            val mine = qa.asker == 1
            Row(Modifier.fillMaxWidth(), horizontalArrangement = if (mine) Arrangement.End else Arrangement.Start) {
                Row(
                    Modifier.clip(RoundedCornerShape(16.dp)).background(if (mine) Color(0x553B82F6) else Color(0x55FB923C)).padding(horizontal = 10.dp, vertical = 6.dp),
                    verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp),
                ) {
                    Column(Modifier.weight(1f, fill = false)) {
                        Text(if (mine) "You asked" else "$theirName asked", color = Color.White.copy(alpha = 0.6f), fontSize = 10.sp)
                        Text(qa.question, color = Color.White, fontSize = 13.sp)
                    }
                    Text(if (qa.yes) "✅ YES" else "❌ NO", color = if (qa.yes) Color(0xFF86EFAC) else Color(0xFFFCA5A5), fontWeight = FontWeight.Black, fontSize = 13.sp)
                }
            }
        }
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
    val remaining = faces.filter { !flipped(it.id) }
    // a big answer when a question comes back
    LaunchedEffect(s.history.size) {
        val last = s.history.lastOrNull() ?: return@LaunchedEffect
        popup = (if (last.asker == 1) "You: " else "They asked: ") + last.question + "\n" + (if (last.yes) "✅ YES!" else "❌ NO!")
        Sfx.play(if (last.yes) Sound.GOOD else Sound.TAP)
        delay(2200)
        popup = null
    }
    LaunchedEffect(s.pending?.question) { if (s.mustAnswer) Sfx.play(Sound.CARD) }
    val myFace = s.mySecret.takeIf { it >= 0 }?.let { GuessWhoData.ALL[it] }
    val waiting = s.pending != null && s.pending.byMe

    Box(Modifier.fillMaxSize().background(Brush.verticalGradient(listOf(Color(0xFF0F172A), Color(0xFF1E3A8A), Color(0xFF3B0764))))) {
        Column(
            Modifier.fillMaxSize().verticalScroll(rememberScrollState()).padding(horizontal = 10.dp, vertical = 10.dp),
            verticalArrangement = Arrangement.spacedBy(8.dp), horizontalAlignment = Alignment.CenterHorizontally,
        ) {
            Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                if (myFace != null && !s.mustAnswer) SmallFace(myFace, "Your face", 54.dp)
                Column(Modifier.weight(1f), horizontalAlignment = Alignment.CenterHorizontally) {
                    Text("🕵️ GUESS WHO?", color = Color(0xFFFFD23F), fontWeight = FontWeight.Black, fontSize = 18.sp, letterSpacing = 2.sp)
                    Text(
                        when {
                            s.stage == "PICK" && !s.myPicked -> "Tap a face to choose it as YOUR secret face"
                            s.stage == "PICK" -> "Waiting for ${engine.theirName} to choose…"
                            s.mustAnswer -> "${engine.theirName} is waiting for your answer"
                            waiting -> "Waiting for ${engine.theirName} to answer…"
                            s.myTurn && guessMode -> "Tap the face you think is theirs"
                            s.myTurn -> "Your turn: ask a question, or make a guess"
                            else -> "${engine.theirName} is thinking…"
                        },
                        color = Color.White, fontWeight = FontWeight.Bold, textAlign = TextAlign.Center, fontSize = 14.sp,
                    )
                }
            }
            // somebody asked me something: it needs my answer before anything else
            if (s.mustAnswer && s.phase == GPhase.PLAYING) AnswerCard(s, engine.theirName, myFace) { engine.answer(it) }
            if (waiting) {
                val dots by rememberInfiniteTransition(label = "dots").animateFloat(0f, 3.99f, infiniteRepeatable(tween(1400, easing = LinearEasing)), label = "dotsv")
                Text("⏳ ${s.pending!!.question}" + ".".repeat(dots.toInt() + 1), color = Color(0xFFFFD23F), fontWeight = FontWeight.Bold, textAlign = TextAlign.Center)
            }
            // the board
            BoxWithConstraints(Modifier.fillMaxWidth()) {
                val cols = if (faces.size > 24) 6 else 4
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
                                            s.stage == "PLAY" && s.myTurn && guessMode && s.pending == null -> pending = f.id
                                            s.stage == "PLAY" -> { if (f.id in manual) manual.remove(f.id) else manual.add(f.id); Sfx.play(Sound.FLIP) }
                                        }
                                    },
                                    modifier = Modifier.size(cell, cell * (if (cols > 4) 1.25f else 1.2f)),
                                )
                            }
                        }
                    }
                }
            }
            if (s.stage == "PLAY") {
                Text("🔎 ${remaining.size} of ${faces.size} faces still in play", color = Color.White.copy(alpha = 0.75f), fontSize = 12.sp, fontWeight = FontWeight.Bold)
                if (s.myTurn && s.pending == null) {
                    Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        FunkyButton("❓ Ask a question", { guessMode = false; askOpen = true }, Modifier.weight(1f), listOf(Funk.Violet, Funk.Blue), fontSize = 15.sp)
                        FunkyButton(if (guessMode) "✖ Cancel guess" else "🎯 Make a guess", { guessMode = !guessMode }, Modifier.weight(1f), if (guessMode) listOf(Funk.Pink, Funk.Red) else listOf(Funk.Orange, Funk.Red), fontSize = 15.sp)
                    }
                } else if (!s.myTurn && s.pending == null) {
                    Text("⏳ Waiting for ${engine.theirName}…", color = Color.White.copy(alpha = 0.8f))
                }
                if (!s.autoFlip) Text("Tap faces to flip them down as you rule them out.", color = Color.White.copy(alpha = 0.6f), fontSize = 11.sp)
                Bubbles(s, engine.theirName)
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
                    s.guessBy == 1 && s.result == 1 -> "You named ${theirFace?.name} correctly after ${s.history.count { it.asker == 1 }} questions."
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
        QuestionPicker(
            s, remaining,
            onAsk = { askOpen = false; Sfx.play(Sound.TICK); engine.ask(it) },
            onCustom = { askOpen = false; Sfx.play(Sound.TICK); engine.askCustom(it) },
            onClose = { askOpen = false },
        )
    }
}
