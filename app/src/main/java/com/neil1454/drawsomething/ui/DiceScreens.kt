package com.neil1454.drawsomething.ui

import com.neil1454.drawsomething.core.Sound
import com.neil1454.drawsomething.sound.Sfx
import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.tween
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableLongStateOf
import androidx.compose.runtime.mutableStateListOf
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
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.neil1454.drawsomething.core.DiceEngine
import com.neil1454.drawsomething.core.DiceState
import com.neil1454.drawsomething.core.GPhase
import kotlinx.coroutines.delay
import kotlin.random.Random

@Composable
fun DiceHelp() {
    Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
        Text("Push your luck! On your turn keep rolling to pile up points, then BANK them to keep them.")
        Text("• Roll a 1 and you lose everything you were holding, and your turn.")
        Text("• Two-dice mode: any single 1 is a bust, DOUBLE 1s wipes your WHOLE banked score, and doubles count double!")
        Text("• First to the target score (banked) wins.")
    }
}

@Composable
fun DiceScreen(
    s: DiceState,
    engine: DiceEngine,
    onPlayAgain: () -> Unit,
    onBackToGames: () -> Unit,
    onLeave: () -> Unit,
) {
    val over = s.phase == GPhase.OVER
    val popups = remember { mutableStateListOf<Popup>() }
    var popupId by remember { mutableLongStateOf(0L) }
    var bad by remember { mutableIntStateOf(0) }
    var rolling by remember { mutableStateOf(false) }
    var shown by remember { mutableStateOf(listOf(1, 1)) }
    val shake = rememberBurst(bad, 600)
    val flash = rememberBurst(bad, 700)

    LaunchedEffect(s.moves) { if (s.moves > 0) Sfx.play(Sound.ROLL) }
    // Tumble the dice, then settle on the real roll; then show what it meant.
    LaunchedEffect(s.moves) {
        if (s.moves == 0 || s.dice.isEmpty()) {
            popups.clear(); shown = if (s.two) listOf(1, 1) else listOf(1)
            return@LaunchedEffect
        }
        if (s.event == "bank") {
            popups.add(Popup(popupId++, "🏦 +${s.eventPoints}", if (s.eventByMe) Funk.Sun else Funk.Cyan, 0.5f, 0.42f))
            return@LaunchedEffect
        }
        rolling = true
        repeat(9) {
            shown = List(s.dice.size) { Random.nextInt(1, 7) }
            delay(65)
        }
        shown = s.dice
        rolling = false
        when (s.event) {
            "bust" -> { bad++; popups.add(Popup(popupId++, "💥 BUST! −${s.eventPoints}", Funk.Red, 0.5f, 0.42f)) }
            "snake" -> { bad++; popups.add(Popup(popupId++, "🐍 SNAKE EYES! −${s.eventPoints}", Funk.Red, 0.5f, 0.42f)) }
            "doubles" -> popups.add(Popup(popupId++, "✨ DOUBLES +${s.eventPoints}", Funk.Sun, 0.5f, 0.42f))
            "roll" -> popups.add(Popup(popupId++, "+${s.eventPoints}", Funk.Lime, 0.5f, 0.42f))
        }
    }

    Box(Modifier.fillMaxSize().shaking(shake)) {
        if (flash < 1f) Box(Modifier.fillMaxSize().background(Funk.Red.copy(alpha = 0.35f * (1f - flash))))
        Column(
            Modifier.fillMaxSize().padding(12.dp),
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.spacedBy(10.dp),
        ) {
            Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                PlayerPill("You", s.myScore, Funk.Lime, s.myTurn && !over, Modifier.weight(1f))
                PlayerPill(s.theirName, s.theirScore, Funk.Cyan, !s.myTurn && !over, Modifier.weight(1f))
            }
            TargetBar("You", s.myScore, s.target, Funk.Lime)
            TargetBar(s.theirName, s.theirScore, s.target, Funk.Cyan)
            TurnText(
                when {
                    over -> "GAME OVER"
                    s.myTurn -> "👉  YOUR ROLL  👈"
                    else -> "⏳ ${s.theirName} is rolling…"
                },
                s.myTurn && !over,
            )
            Spacer(Modifier.weight(1f))
            Box(contentAlignment = Alignment.Center) {
                Row(horizontalArrangement = Arrangement.spacedBy(18.dp)) {
                    val faces = if (shown.size == (if (s.two) 2 else 1)) shown else List(if (s.two) 2 else 1) { 1 }
                    faces.forEach { Die(it, rolling) }
                }
            }
            Text("HOLDING", fontSize = 12.sp, color = Color.White.copy(alpha = 0.8f))
            GradientText("${s.held}", fontSize = 56.sp, colors = listOf(Funk.Sun, Funk.Orange, Funk.Pink))
            if (s.myTurn && !over) {
                Text(
                    "Bank it before you roll a 1!" + if (s.held + s.myScore >= s.target) "  You can WIN now!" else "",
                    fontSize = 13.sp, color = Funk.Sun, textAlign = TextAlign.Center,
                )
            }
            Spacer(Modifier.weight(1f))
            Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                FunkyButton("🎲  ROLL", { engine.roll() }, Modifier.weight(1f), listOf(Funk.Lime, Funk.Mint), enabled = s.myTurn && !over && !rolling, fontSize = 22.sp)
                FunkyButton("🏦  BANK", { engine.bank() }, Modifier.weight(1f), listOf(Funk.Sun, Funk.Orange), enabled = s.myTurn && !over && !rolling && s.held > 0, fontSize = 22.sp)
            }
        }
        PopupLayer(popups, { id -> popups.removeAll { it.id == id } })
        if (over) {
            EndOverlay(
                title = if (s.result == 1) "🏆 YOU WIN!" else "🏆 ${s.theirName.uppercase()} WINS!",
                subtitle = "You ${s.myScore}  ·  ${s.theirName} ${s.theirScore}",
                win = s.result == 1, isHost = engine.isHost, theirName = s.theirName,
                playAgainLabel = "Play again", colors = listOf(Funk.Mint, Funk.Cyan),
                onPlayAgain = onPlayAgain, onBackToGames = onBackToGames, onLeave = onLeave,
            )
        }
    }
}

@Composable
private fun TargetBar(label: String, score: Int, target: Int, colour: Color) {
    val fraction by animateFloatAsState((score / target.toFloat()).coerceIn(0f, 1f), tween(600), label = "target")
    Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
        Text(label, Modifier.padding(end = 2.dp).fillMaxWidth(0.22f), fontSize = 12.sp, maxLines = 1)
        Box(Modifier.weight(1f).height(12.dp).clip(RoundedCornerShape(6.dp)).background(Color(0x33FFFFFF))) {
            Box(Modifier.fillMaxHeight().fillMaxWidth(fraction).clip(RoundedCornerShape(6.dp)).background(Brush.horizontalGradient(listOf(colour, colour.copy(alpha = 0.6f)))))
        }
        Text("$target", fontSize = 12.sp, fontWeight = FontWeight.Bold)
    }
}

/** A die with real pips; wobbles while tumbling. A rolled 1 shows a big red pip. */
@Composable
private fun Die(value: Int, rolling: Boolean) {
    val wobble = remember { Animatable(0f) }
    LaunchedEffect(rolling) {
        if (rolling) {
            wobble.animateTo(360f, tween(600, easing = androidx.compose.animation.core.LinearEasing))
            wobble.snapTo(0f)
        } else {
            wobble.snapTo(0f)
        }
    }
    val shape = RoundedCornerShape(22.dp)
    Box(
        Modifier
            .size(104.dp)
            .graphicsLayer {
                rotationZ = wobble.value
                scaleX = if (rolling) 1.08f else 1f
                scaleY = if (rolling) 1.08f else 1f
            }
            .clip(shape)
            .background(Brush.linearGradient(listOf(Color.White, Color(0xFFD8D2FF))))
            .border(3.dp, Color.White.copy(alpha = 0.7f), shape),
    ) {
        Canvas(Modifier.fillMaxSize().padding(14.dp)) {
            val cell = size.width / 3f
            val r = cell * 0.30f
            val spots = when (value) {
                1 -> listOf(1 to 1)
                2 -> listOf(0 to 0, 2 to 2)
                3 -> listOf(0 to 0, 1 to 1, 2 to 2)
                4 -> listOf(0 to 0, 2 to 0, 0 to 2, 2 to 2)
                5 -> listOf(0 to 0, 2 to 0, 1 to 1, 0 to 2, 2 to 2)
                else -> listOf(0 to 0, 2 to 0, 0 to 1, 2 to 1, 0 to 2, 2 to 2)
            }
            spots.forEach { (cx, cy) ->
                drawCircle(
                    if (value == 1) Funk.Red else Color(0xFF241A5E),
                    radius = if (value == 1) r * 1.5f else r,
                    center = Offset(cell * (cx + 0.5f), cell * (cy + 0.5f)),
                )
            }
        }
    }
}
