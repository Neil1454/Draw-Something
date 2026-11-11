package com.neil1454.drawsomething.ui

import com.neil1454.drawsomething.core.WinStory
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
import androidx.compose.runtime.mutableStateOf
import androidx.compose.ui.graphics.graphicsLayer
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

/** Each player has their own colour on both phones: the host is blue, the guest is orange. */
private val HostBlue = Color(0xFF3B82F6)
private val GuestOrange = Color(0xFFF59E0B)

@Composable
fun HolidayHelp() {
    Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
        Text("A quiz about where you're going! You both see the same question at the same time. Some have pictures, and some are maps: tap a dot to choose it, then tap it again (or press Lock in) to answer.")
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
    val locked = s.step == "LOCK"
    val myCol = if (engine.isHost) HostBlue else GuestOrange
    val theirCol = if (engine.isHost) GuestOrange else HostBlue
    var party by remember { mutableIntStateOf(0) }
    var bad by remember { mutableIntStateOf(0) }
    val shake = rememberBurst(bad, 500)
    var bannerKey by remember { mutableIntStateOf(0) }
    var bannerText by remember { mutableStateOf("") }
    var bannerColour by remember { mutableStateOf(Color.White) }
    val bannerP = rememberBurst(bannerKey, 1900)

    // sounds and celebrations when the answer is revealed
    LaunchedEffect(s.seq, s.step) {
        if (s.step == "REVEAL" && s.moves > 0) {
            when {
                s.myDelta > 0 -> {
                    party++; Sfx.play(Sound.CHIME)
                    bannerText = if (s.theirDelta > 0) "🎉 BOTH RIGHT!" else "🎉 CORRECT!  +${s.myDelta}"
                    bannerColour = Color(0xFF4ADE80); bannerKey++
                }
                s.myDelta < 0 -> {
                    bad++; Sfx.play(Sound.BUZZ)
                    bannerText = "❌ NOT QUITE"; bannerColour = Color(0xFFF87171); bannerKey++
                }
                s.theirDelta > 0 -> {
                    Sfx.play(Sound.POP)
                    bannerText = "${s.theirName.uppercase()} GOT IT"; bannerColour = theirCol; bannerKey++
                }
                else -> { Sfx.play(Sound.TICK); bannerText = "⏱ NO ONE GOT IT"; bannerColour = Color.White; bannerKey++ }
            }
        } else if (s.step == "LOCK") {
            Sfx.play(Sound.CARD)
        } else if (s.step == "ASK") {
            Sfx.play(Sound.WHOOSH)
        }
    }
    LaunchedEffect(s.secsLeft) {
        if (s.step == "ASK" && s.limitSecs > 0 && s.secsLeft in 1..3 && s.myChoice == -1) Sfx.play(Sound.TICK)
    }

    Box(Modifier.fillMaxSize().background(Sunset).shaking(shake)) {
        if (revealed && s.myDelta > 0 && bannerP < 1f) {
            Box(Modifier.fillMaxSize().background(Color(0xFF22C55E).copy(alpha = 0.28f * (1f - bannerP))))
        }
        Column(
            Modifier.fillMaxSize().padding(horizontal = 10.dp, vertical = 8.dp),
            verticalArrangement = Arrangement.spacedBy(8.dp),
            horizontalAlignment = Alignment.CenterHorizontally,
        ) {
            Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                PlayerPill(
                    "You", s.myScore, myCol, s.myChoice != -1 && !revealed, Modifier.weight(1f),
                    extra = if (revealed && s.myDelta != 0) signed(s.myDelta) else if (s.myChoice != -1) "🔒" else "",
                )
                PlayerPill(
                    s.theirName, s.theirScore, theirCol, (s.theyAnswered || locked) && !revealed, Modifier.weight(1f),
                    extra = if (revealed && s.theirDelta != 0) signed(s.theirDelta) else if (s.theyAnswered || (locked && s.theirChoice != -1)) "🔒" else "",
                )
            }
            Text(
                "${s.packEmoji} ${s.packTitle} · Question ${s.index} of ${s.total}" + if (s.category.isNotBlank()) " · ${s.category}" else "",
                fontSize = 12.sp, color = Color.White.copy(alpha = 0.85f), textAlign = TextAlign.Center,
            )
            TimerBar(s, revealed || locked)

            val isMap = s.art.startsWith("map:")
            if (s.art.startsWith("pic:")) HolidayPicture(s.art)
            // the question
            Box(
                Modifier.fillMaxWidth()
                    .clip(RoundedCornerShape(22.dp))
                    .background(Brush.horizontalGradient(listOf(Teal.copy(alpha = 0.85f), Color(0xFF0E5C73))))
                    .border(2.dp, Color.White.copy(alpha = 0.4f), RoundedCornerShape(22.dp))
                    .padding(16.dp),
                contentAlignment = Alignment.Center,
            ) {
                Text(s.question, fontSize = if (isMap || s.art.isNotEmpty()) 18.sp else 21.sp, fontWeight = FontWeight.ExtraBold, color = Color.White, textAlign = TextAlign.Center)
            }

            // the answers
            Column(Modifier.weight(1f).fillMaxWidth().verticalScroll(rememberScrollState()), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                if (isMap) {
                    HolidayMap(
                        kind = s.art.removePrefix("map:"), options = s.options, questionKey = s.seq, revealed = revealed, correct = s.correct,
                        myChoice = s.myChoice, theirChoice = s.theirChoice, theirName = s.theirName,
                        enabled = !revealed && !locked && s.myChoice == -1 && s.phase == GPhase.PLAYING,
                        locked = locked, myCol = myCol, theirCol = theirCol,
                    ) { i ->
                        Sfx.play(Sound.TAP)
                        engine.pick(i)
                    }
                } else s.options.forEachIndexed { i, text ->
                    AnswerButton(i, text, s, revealed, locked, myCol, theirCol) {
                        Sfx.play(Sound.TAP)
                        engine.pick(i)
                    }
                }
                if (locked) {
                    Column(
                        Modifier.fillMaxWidth().clip(RoundedCornerShape(18.dp)).background(Color(0x88000000)).padding(12.dp),
                        verticalArrangement = Arrangement.spacedBy(4.dp), horizontalAlignment = Alignment.CenterHorizontally,
                    ) {
                        Text("🔒 ANSWERS LOCKED IN", fontSize = 16.sp, fontWeight = FontWeight.Black, color = Color.White, letterSpacing = 1.sp)
                        fun who(name: String, c: Int) = if (c in s.options.indices) (if (isMap) s.options[c] else "${"ABCD"[c.coerceIn(0, 3)]}: ${s.options[c]}") else "no answer"
                        Text("You picked ${who("You", s.myChoice)}", fontWeight = FontWeight.Bold, color = myCol)
                        Text("${s.theirName} picked ${who(s.theirName, s.theirChoice)}", fontWeight = FontWeight.Bold, color = theirCol)
                        Text("And the right answer is…", fontSize = 13.sp, color = Color.White.copy(alpha = 0.8f))
                    }
                } else if (!revealed) {
                    val hurry = s.limitSecs > 0 && s.secsLeft in 1..HolidayRules.HURRY_SECS && s.myChoice == -1
                    val note = when {
                        s.myChoice != -1 && !s.theyAnswered -> "🔒 Locked in. Waiting for ${s.theirName}… (their answer stays hidden)"
                        s.myChoice != -1 -> "🔒 Locked in. Revealing…"
                        hurry -> "⏰ HURRY UP! Only ${s.secsLeft} second${if (s.secsLeft == 1) "" else "s"} left!"
                        s.theyAnswered -> "⚡ ${s.theirName} has locked in an answer. Hurry up!"
                        else -> if (s.limitSecs == 0) "Tap your answer. The quickest right answer still scores most." else "Tap your answer. The quickest right answer scores most."
                    }
                    val urgent = hurry || (s.theyAnswered && s.myChoice == -1)
                    val beat = bobbing(amount = 1f, millis = 260)
                    val pulse = if (urgent) 1f + 0.05f * beat else 1f
                    Text(
                        note, fontSize = if (hurry) 19.sp else 14.sp, fontWeight = if (urgent) FontWeight.Black else FontWeight.SemiBold,
                        color = if (hurry) Color(0xFFFF6B6B) else if (urgent) Funk.Sun else Color.White, textAlign = TextAlign.Center,
                        modifier = Modifier.fillMaxWidth().padding(top = 4.dp).graphicsLayer { scaleX = pulse; scaleY = pulse },
                    )
                } else {
                    RevealPanel(s)
                }
                Spacer(Modifier.height(6.dp))
            }
        }
        ConfettiBurst(party)
        if (bannerKey > 0 && bannerP < 1f && revealed) {
            Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                val grow = minOf(1f, bannerP * 4f)
                val sc = 0.4f + 0.75f * (1f - (1f - grow) * (1f - grow)) + 0.05f * kotlin.math.sin(bannerP * 18f) * (1f - bannerP)
                Text(
                    bannerText, fontSize = 40.sp, fontWeight = FontWeight.Black, color = bannerColour, textAlign = TextAlign.Center,
                    style = androidx.compose.ui.text.TextStyle(shadow = androidx.compose.ui.graphics.Shadow(Color.Black.copy(alpha = 0.7f), androidx.compose.ui.geometry.Offset(0f, 4f), 14f)),
                    modifier = Modifier.padding(24.dp).graphicsLayer {
                        scaleX = sc; scaleY = sc
                        alpha = 1f - ((bannerP - 0.7f) / 0.3f).coerceIn(0f, 1f)
                    },
                )
            }
        }
        if (over) {
            EndOverlay(
                title = when (s.result) { 1 -> "🏆 YOU WIN!"; 2 -> "🏆 ${s.theirName.uppercase()} WINS!"; else -> "🤝 IT'S A DRAW!" },
                subtitle = "You ${s.myScore} (${s.myRight} right)  ·  ${s.theirName} ${s.theirScore} (${s.theirRight} right)",
                how = if (s.result == 3) "After ${s.total} questions you were level on points."
                else "Over ${s.total} questions, " + (if (s.result == 1) "you" else s.theirName) + " scored more by answering quicker and more accurately, winning " +
                    WinStory.by(maxOf(s.myScore, s.theirScore), minOf(s.myScore, s.theirScore), "point") + ".",
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
    if (s.limitSecs == 0) {
        Text("⏳ No timer: take your time", fontSize = 13.sp, fontWeight = FontWeight.SemiBold, color = Color.White.copy(alpha = 0.85f))
        return
    }
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
        Text(if (revealed) "—" else "${s.secsLeft.coerceAtLeast(0)}s", fontSize = 16.sp, fontWeight = FontWeight.Black, color = Color.White)
    }
}

@Composable
private fun AnswerButton(index: Int, text: String, s: HolidayState, revealed: Boolean, locked: Boolean, myCol: Color, theirCol: Color, onTap: () -> Unit) {
    val mine = s.myChoice == index
    val theirs = (revealed || locked) && s.theirChoice == index
    val isRight = revealed && s.correct == index
    val isWrongPick = revealed && !isRight && (mine || theirs)
    val target = when {
        isRight -> Color(0xFF1FA85A)
        isWrongPick -> Color(0xFFC62E4B)
        locked && mine -> myCol.copy(alpha = 0.75f)
        locked && theirs -> theirCol.copy(alpha = 0.75f)
        mine -> Color(0xFF3A66D6)
        revealed || locked -> Color.White.copy(alpha = 0.08f)
        else -> Color.White.copy(alpha = 0.16f)
    }
    val bg by animateColorAsState(target, label = "answer")
    val enabled = !revealed && !locked && s.myChoice == -1 && s.phase == GPhase.PLAYING
    val shape = RoundedCornerShape(18.dp)
    Row(
        Modifier.fillMaxWidth()
            .clip(shape)
            .background(bg)
            .border(
                if (isRight || mine || theirs) 3.dp else 1.dp,
                when {
                    isRight -> Funk.Lime
                    mine && (revealed || locked) -> myCol
                    theirs -> theirCol
                    else -> Color.White.copy(alpha = if (mine) 0.9f else 0.35f)
                },
                shape,
            )
            .clickable(enabled = enabled, onClick = onTap)
            .padding(horizontal = 12.dp, vertical = 12.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(12.dp),
    ) {
        Box(Modifier.size(34.dp).clip(CircleShape).background(Color.White.copy(alpha = 0.22f)), contentAlignment = Alignment.Center) {
            Text("ABCD"[index.coerceIn(0, 3)].toString(), fontWeight = FontWeight.Black, color = Color.White)
        }
        Text(text, Modifier.weight(1f), fontSize = 17.sp, fontWeight = FontWeight.SemiBold, color = Color.White.copy(alpha = if (revealed && !isRight && !isWrongPick) 0.6f else 1f))
        Column(horizontalAlignment = Alignment.End, verticalArrangement = Arrangement.spacedBy(3.dp)) {
            if (mine && (revealed || locked)) Text("YOU", Modifier.clip(RoundedCornerShape(8.dp)).background(myCol).padding(horizontal = 7.dp, vertical = 2.dp), fontSize = 11.sp, fontWeight = FontWeight.Black, color = Color.White)
            if (theirs) Text(s.theirName.take(10).uppercase(), Modifier.clip(RoundedCornerShape(8.dp)).background(theirCol).padding(horizontal = 7.dp, vertical = 2.dp), fontSize = 11.sp, fontWeight = FontWeight.Black, color = Color.White)
        }
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
