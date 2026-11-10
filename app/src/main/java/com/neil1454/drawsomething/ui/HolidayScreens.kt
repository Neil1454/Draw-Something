package com.neil1454.drawsomething.ui

import androidx.compose.animation.animateColorAsState
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
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
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.neil1454.drawsomething.core.GPhase
import com.neil1454.drawsomething.core.HolidayEngine
import com.neil1454.drawsomething.core.HolidayRules
import com.neil1454.drawsomething.core.HolidayState
import com.neil1454.drawsomething.core.Sound
import com.neil1454.drawsomething.sound.Sfx

private val Sunset = Brush.verticalGradient(listOf(Color(0xFF0B3C5D), Color(0xFF14677D), Color(0xFF8A4B2A), Color(0xFF2B1236)))
private val Teal = Color(0xFF00B3A4)
private val Coral = Color(0xFFFF8A3D)

@Composable
fun HolidayHelp() {
    Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
        Text("A quiz about where you're going! You both see the same question and four answers at the same time.")
        Text("• The FIRST correct answer wins ${HolidayRules.CORRECT} points, plus up to ${HolidayRules.SPEED_BONUS} more for being quick.")
        Text("• If you're also right but slower, you still get ${HolidayRules.ALSO_RIGHT}.")
        Text("• A wrong answer costs ${-HolidayRules.WRONG} points, so think before you tap!")
        Text("• After each question you'll see the right answer and a 'did you know' fact.")
        Text("• The host picks the holiday and how many questions. More holidays can be added to the app.")
    }
}

@Composable
fun HolidayScreen(
    s: HolidayState,
    engine: HolidayEngine,
    onPlayAgain: () -> Unit,
    onBackToGames: () -> Unit,
    onLeave: () -> Unit,
) {
    val over = s.phase == GPhase.OVER
    val revealed = s.step == "REVEAL"
    var party by remember { mutableIntStateOf(0) }
    var bad by remember { mutableIntStateOf(0) }
    val shake = rememberBurst(bad, 500)

    // sounds and celebrations when the answer is revealed
    LaunchedEffect(s.seq, s.step) {
        if (s.step == "REVEAL" && s.moves > 0) {
            when {
                s.myDelta > 0 -> { party++; Sfx.play(Sound.CHIME) }
                s.myDelta < 0 -> { bad++; Sfx.play(Sound.BUZZ) }
                s.theirDelta > 0 -> Sfx.play(Sound.POP)
                else -> Sfx.play(Sound.TICK)
            }
        } else if (s.step == "ASK") {
            Sfx.play(Sound.WHOOSH)
        }
    }
    LaunchedEffect(s.secsLeft) {
        if (s.step == "ASK" && s.secsLeft in 1..3 && s.myChoice == -1) Sfx.play(Sound.TICK)
    }

    Box(Modifier.fillMaxSize().background(Sunset).shaking(shake)) {
        Column(
            Modifier.fillMaxSize().padding(horizontal = 10.dp, vertical = 8.dp),
            verticalArrangement = Arrangement.spacedBy(8.dp),
            horizontalAlignment = Alignment.CenterHorizontally,
        ) {
            Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                PlayerPill(
                    "You", s.myScore, Funk.Lime, s.myChoice != -1 && !revealed, Modifier.weight(1f),
                    extra = if (revealed && s.myDelta != 0) signed(s.myDelta) else if (s.myChoice != -1) "🔒" else "",
                )
                PlayerPill(
                    s.theirName, s.theirScore, Funk.Cyan, s.theyAnswered && !revealed, Modifier.weight(1f),
                    extra = if (revealed && s.theirDelta != 0) signed(s.theirDelta) else if (s.theyAnswered) "🔒" else "",
                )
            }
            Text(
                "${s.packEmoji} ${s.packTitle} · Question ${s.index} of ${s.total}" + if (s.category.isNotBlank()) " · ${s.category}" else "",
                fontSize = 12.sp, color = Color.White.copy(alpha = 0.85f), textAlign = TextAlign.Center,
            )
            TimerBar(s, revealed)

            // the question
            Box(
                Modifier.fillMaxWidth()
                    .clip(RoundedCornerShape(22.dp))
                    .background(Brush.horizontalGradient(listOf(Teal.copy(alpha = 0.85f), Color(0xFF0E5C73))))
                    .border(2.dp, Color.White.copy(alpha = 0.4f), RoundedCornerShape(22.dp))
                    .padding(16.dp),
                contentAlignment = Alignment.Center,
            ) {
                Text(s.question, fontSize = 21.sp, fontWeight = FontWeight.ExtraBold, color = Color.White, textAlign = TextAlign.Center)
            }

            // the answers
            Column(Modifier.weight(1f).fillMaxWidth().verticalScroll(rememberScrollState()), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                s.options.forEachIndexed { i, text ->
                    AnswerButton(i, text, s, revealed) {
                        Sfx.play(Sound.TAP)
                        engine.pick(i)
                    }
                }
                if (!revealed) {
                    val note = when {
                        s.myChoice != -1 && !s.theyAnswered -> "🔒 Locked in. Waiting to see if ${s.theirName} is right…"
                        s.myChoice != -1 -> "🔒 Locked in. Revealing…"
                        s.theyAnswered -> "⚡ ${s.theirName} has already answered. Be quick!"
                        else -> "Tap your answer. The quickest right answer scores most."
                    }
                    Text(note, fontSize = 14.sp, fontWeight = FontWeight.SemiBold, color = Color.White, textAlign = TextAlign.Center, modifier = Modifier.fillMaxWidth().padding(top = 4.dp))
                } else {
                    RevealPanel(s)
                }
                Spacer(Modifier.height(6.dp))
            }
        }
        ConfettiBurst(party)
        if (over) {
            EndOverlay(
                title = when (s.result) { 1 -> "🏆 YOU WIN!"; 2 -> "🏆 ${s.theirName.uppercase()} WINS!"; else -> "🤝 IT'S A DRAW!" },
                subtitle = "You ${s.myScore} (${s.myRight} right)  ·  ${s.theirName} ${s.theirScore} (${s.theirRight} right)",
                win = s.result == 1, isHost = engine.isHost, theirName = s.theirName,
                playAgainLabel = "Another quiz", colors = listOf(Teal, Coral),
                onPlayAgain = onPlayAgain, onBackToGames = onBackToGames, onLeave = onLeave,
            )
        }
    }
}

private fun signed(n: Int) = if (n > 0) "+$n" else "$n"

@Composable
private fun TimerBar(s: HolidayState, revealed: Boolean) {
    val frac by animateFloatAsState(
        if (revealed) 0f else (s.secsLeft.toFloat() / s.limitSecs.coerceAtLeast(1)).coerceIn(0f, 1f),
        label = "timer",
    )
    val colour = when {
        frac > 0.5f -> Funk.Lime
        frac > 0.25f -> Funk.Sun
        else -> Funk.Red
    }
    Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
        Box(Modifier.weight(1f).height(12.dp).clip(RoundedCornerShape(6.dp)).background(Color.White.copy(alpha = 0.18f))) {
            Box(Modifier.fillMaxWidth(frac).height(12.dp).clip(RoundedCornerShape(6.dp)).background(colour))
        }
        Text(if (revealed) "—" else "${s.secsLeft}s", fontSize = 16.sp, fontWeight = FontWeight.Black, color = Color.White)
    }
}

@Composable
private fun AnswerButton(index: Int, text: String, s: HolidayState, revealed: Boolean, onTap: () -> Unit) {
    val mine = s.myChoice == index
    val theirs = revealed && s.theirChoice == index
    val isRight = revealed && s.correct == index
    val isWrongPick = revealed && !isRight && (mine || theirs)
    val target = when {
        isRight -> Color(0xFF1FA85A)
        isWrongPick -> Color(0xFFC62E4B)
        mine -> Color(0xFF3A66D6)
        revealed -> Color.White.copy(alpha = 0.08f)
        else -> Color.White.copy(alpha = 0.16f)
    }
    val bg by animateColorAsState(target, label = "answer")
    val enabled = !revealed && s.myChoice == -1 && s.phase == GPhase.PLAYING
    val shape = RoundedCornerShape(18.dp)
    Row(
        Modifier.fillMaxWidth()
            .clip(shape)
            .background(bg)
            .border(if (isRight || mine) 3.dp else 1.dp, if (isRight) Funk.Lime else Color.White.copy(alpha = if (mine) 0.9f else 0.35f), shape)
            .clickable(enabled = enabled, onClick = onTap)
            .padding(horizontal = 12.dp, vertical = 12.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(12.dp),
    ) {
        Box(Modifier.size(34.dp).clip(CircleShape).background(Color.White.copy(alpha = 0.22f)), contentAlignment = Alignment.Center) {
            Text("ABCD"[index.coerceIn(0, 3)].toString(), fontWeight = FontWeight.Black, color = Color.White)
        }
        Text(text, Modifier.weight(1f), fontSize = 17.sp, fontWeight = FontWeight.SemiBold, color = Color.White.copy(alpha = if (revealed && !isRight && !isWrongPick) 0.6f else 1f))
        val tags = buildString {
            if (mine) append("YOU ")
            if (theirs) append(s.theirName.take(8).uppercase())
        }.trim()
        if (tags.isNotEmpty()) Text(tags, fontSize = 11.sp, fontWeight = FontWeight.Black, color = Color.White)
        if (isRight) Text("✅", fontSize = 20.sp)
    }
}

@Composable
private fun RevealPanel(s: HolidayState) {
    Column(
        Modifier.fillMaxWidth().clip(RoundedCornerShape(18.dp)).background(Color(0x66000000)).padding(12.dp),
        verticalArrangement = Arrangement.spacedBy(6.dp),
    ) {
        fun line(name: String, choice: Int, delta: Int, ms: Int): String = when {
            choice == -1 -> "$name: no answer"
            choice == s.correct -> "$name: ✅ right in ${"%.1f".format(ms / 1000f)}s  (${signed(delta)})"
            else -> "$name: ❌ wrong  (${signed(delta)})"
        }
        Text(line("You", s.myChoice, s.myDelta, s.myMs), fontWeight = FontWeight.Bold, color = Color.White)
        Text(line(s.theirName, s.theirChoice, s.theirDelta, s.theirMs), fontWeight = FontWeight.Bold, color = Color.White)
        if (s.fact.isNotBlank()) Text("💡 ${s.fact}", fontSize = 15.sp, color = Funk.Sun)
    }
}
