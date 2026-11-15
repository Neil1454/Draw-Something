package com.neil1454.drawsomething.ui

import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.tween
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.rememberScrollState
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
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.neil1454.drawsomething.core.GPhase
import com.neil1454.drawsomething.core.LiarDiceEngine
import com.neil1454.drawsomething.core.LiarMath
import com.neil1454.drawsomething.core.LiarState
import com.neil1454.drawsomething.core.Sound
import com.neil1454.drawsomething.sound.Sfx
import kotlin.math.sin

@Composable
fun LiarDiceHelp() {
    Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
        Text("A game of bluff. You each shake your own dice under a cup, so only you can see them.")
        Text("• Take turns bidding on ALL the dice on the table, yours and theirs: \"four 5s\". Each bid must be higher: more dice, or the same number of a higher face.")
        Text("• Or call \"LIAR!\" Everyone's dice are revealed. If there are at least as many as the bid, the bidder was telling the truth and the caller loses a die. If not, the bidder loses one.")
        Text("• The loser starts the next round. Lose all your dice and you're out. (With \"ones are wild\" on, 1s count as every face.)")
    }
}

/** One die, with its pips. */
@Composable
private fun Die(value: Int, size: Dp, glow: Boolean = false, faded: Boolean = false, shake: Float = 0f) {
    Canvas(Modifier.size(size).graphicsLayer { rotationZ = shake; alpha = if (faded) 0.35f else 1f }) {
        val s = this.size.minDimension
        drawRoundRect(Color.Black.copy(alpha = 0.3f), Offset(s * 0.05f, s * 0.08f), Size(s * 0.92f, s * 0.92f), CornerRadius(s * 0.2f))
        drawRoundRect(Brush.linearGradient(listOf(Color.White, Color(0xFFE2E8F0)), Offset.Zero, Offset(s, s)), Offset.Zero, Size(s * 0.94f, s * 0.94f), CornerRadius(s * 0.2f))
        drawRoundRect(if (glow) Color(0xFFFFD23F) else Color(0xFF94A3B8), Offset.Zero, Size(s * 0.94f, s * 0.94f), CornerRadius(s * 0.2f), style = Stroke(if (glow) s * 0.08f else s * 0.03f))
        val d = s * 0.94f * 0.25f
        val c = Offset(s * 0.47f, s * 0.47f)
        val pips = when (value) {
            1 -> listOf(0 to 0)
            2 -> listOf(-1 to -1, 1 to 1)
            3 -> listOf(-1 to -1, 0 to 0, 1 to 1)
            4 -> listOf(-1 to -1, 1 to -1, -1 to 1, 1 to 1)
            5 -> listOf(-1 to -1, 1 to -1, 0 to 0, -1 to 1, 1 to 1)
            else -> listOf(-1 to -1, 1 to -1, -1 to 0, 1 to 0, -1 to 1, 1 to 1)
        }
        for ((px, py) in pips) drawCircle(if (value == 1) Color(0xFFDC2626) else Color(0xFF1E293B), s * 0.075f, Offset(c.x + px * d, c.y + py * d))
    }
}

/** A dice cup, for the dice you can't see. */
@Composable
private fun Cup(size: Dp, lost: Boolean = false) {
    Canvas(Modifier.size(size).graphicsLayer { alpha = if (lost) 0.25f else 1f }) {
        val s = this.size.minDimension
        val p = androidx.compose.ui.graphics.Path().apply {
            moveTo(s * 0.2f, s * 0.1f); lineTo(s * 0.8f, s * 0.1f); lineTo(s * 0.7f, s * 0.9f); lineTo(s * 0.3f, s * 0.9f); close()
        }
        drawPath(p, Brush.horizontalGradient(listOf(Color(0xFF7F1D1D), Color(0xFFDC2626), Color(0xFF7F1D1D)), s * 0.2f, s * 0.8f))
        drawRoundRect(Color(0xFFFCA5A5).copy(alpha = 0.7f), Offset(s * 0.2f, s * 0.1f), Size(s * 0.6f, s * 0.08f), CornerRadius(s * 0.04f))
        drawPath(p, Color.Black.copy(alpha = 0.4f), style = Stroke(s * 0.03f))
    }
}

@Composable
fun LiarDiceScreen(
    s: LiarState,
    engine: LiarDiceEngine,
    onPlayAgain: () -> Unit,
    onBackToGames: () -> Unit,
    onLeave: () -> Unit,
) {
    val faceNow = s.bid?.face ?: 0
    var qty by remember { mutableIntStateOf(1) }
    var face by remember { mutableIntStateOf(1) }
    val roll = remember(s.round) { Animatable(1f) }
    LaunchedEffect(s.round, s.myDice.isEmpty()) {
        if (s.myDice.isNotEmpty() && s.stage == "BID") { roll.snapTo(0f); Sfx.play(Sound.ROLL); roll.animateTo(1f, tween(900)) }
    }
    // a sensible starting bid every time the table changes
    LaunchedEffect(s.bid?.qty, s.bid?.face, s.round) {
        val b = s.bid
        if (b == null) { qty = 1; face = 1 } else if (b.face < 6) { qty = b.qty; face = b.face + 1 } else { qty = b.qty + 1; face = 1 }
        qty = qty.coerceAtMost(maxOf(1, s.total))
    }
    LaunchedEffect(s.stage) {
        if (s.stage == "SHOW" || s.stage == "END") Sfx.play(if (s.loser == 1) Sound.BAD else Sound.GOOD)
    }
    val valid = s.bid == null || LiarMath.higher(qty, face, s.bid.qty, s.bid.face)
    val canBid = s.myTurn && s.stage == "BID" && s.myDice.isNotEmpty()
    val showing = s.stage == "SHOW" || s.stage == "END"

    Box(Modifier.fillMaxSize().background(Brush.verticalGradient(listOf(Color(0xFF450A0A), Color(0xFF1C1917), Color(0xFF0C0A09))))) {
        Column(
            Modifier.fillMaxSize().verticalScroll(rememberScrollState()).padding(horizontal = 14.dp, vertical = 12.dp),
            verticalArrangement = Arrangement.spacedBy(10.dp), horizontalAlignment = Alignment.CenterHorizontally,
        ) {
            Text("🎭 LIAR'S DICE · Round ${s.round}", color = Color(0xFFFFD23F), fontWeight = FontWeight.Black, fontSize = 16.sp, letterSpacing = 2.sp)
            // their dice
            Text("${engine.theirName}  ·  ${s.theirCount} ${if (s.theirCount == 1) "die" else "dice"}", color = Color(0xFFFDBA74), fontWeight = FontWeight.Bold)
            Row(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                if (showing && s.theirDice.isNotEmpty()) s.theirDice.forEach { d ->
                    val hit = s.bid != null && (d == faceNow || (s.wild && d == 1 && faceNow != 1))
                    Die(d, 46.dp, glow = hit, faded = !hit)
                } else repeat(s.theirCount) { Cup(46.dp) }
            }
            // the bid on the table
            val b = s.bid
            val shape = RoundedCornerShape(18.dp)
            Column(
                Modifier.fillMaxWidth().clip(shape).background(Color(0x66000000)).border(2.dp, Color(0xFFFFD23F).copy(alpha = 0.6f), shape).padding(12.dp),
                horizontalAlignment = Alignment.CenterHorizontally,
            ) {
                if (b == null) Text(if (s.myTurn) "You open the bidding" else "${engine.theirName} opens the bidding", color = Color.White.copy(alpha = 0.85f))
                else {
                    Text(if (b.by == 1) "You bid" else "${engine.theirName} bids", color = Color.White.copy(alpha = 0.8f), fontSize = 13.sp)
                    Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                        Text("${b.qty} ×", color = Color(0xFFFFD23F), fontWeight = FontWeight.Black, fontSize = 40.sp)
                        Die(b.face, 52.dp, glow = true)
                    }
                }
                Text("${s.total} dice on the table${if (s.wild) " · ones are wild" else ""}", color = Color.White.copy(alpha = 0.6f), fontSize = 12.sp)
            }
            // the result of a call
            if (showing) {
                Text(
                    "There ${if (s.actual == 1) "was" else "were"} ${s.actual} × $faceNow: the bid was ${if (s.bidGood) "GOOD" else "a BLUFF"}!",
                    color = if (s.bidGood) Color(0xFF4ADE80) else Color(0xFFF87171), fontWeight = FontWeight.Black, fontSize = 18.sp, textAlign = TextAlign.Center,
                )
                Text(if (s.loser == 1) "😬 You lose a die" else "🎉 ${engine.theirName} loses a die", color = Color.White, fontWeight = FontWeight.Bold)
            }
            // my dice
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                s.myDice.forEachIndexed { i, d ->
                    val hit = showing && s.bid != null && (d == faceNow || (s.wild && d == 1 && faceNow != 1))
                    val wob = if (roll.value < 1f) sin((roll.value * 18f) + i) * 25f * (1f - roll.value) else 0f
                    Die(d, 58.dp, glow = hit, faded = showing && !hit, shake = wob)
                }
            }
            Text("You  ·  ${s.myCount} ${if (s.myCount == 1) "die" else "dice"}${if (s.myDice.isEmpty() && s.stage == "BID") "  (shaking…)" else ""}", color = Color(0xFF7DD3FC), fontWeight = FontWeight.Bold)

            // what to do
            when {
                s.stage == "BID" && s.myTurn -> {
                    Text("Your bid: $qty × $face", color = Color.White, fontWeight = FontWeight.Black, fontSize = 18.sp)
                    Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                        StepButton("−", enabled = qty > 1) { qty-- }
                        Text("$qty", color = Color(0xFFFFD23F), fontWeight = FontWeight.Black, fontSize = 30.sp)
                        StepButton("+", enabled = qty < s.total) { qty++ }
                    }
                    Row(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                        for (f in 1..6) {
                            val sel = f == face
                            Box(Modifier.clip(RoundedCornerShape(10.dp)).border(if (sel) 3.dp else 1.dp, if (sel) Color(0xFFFFD23F) else Color.White.copy(alpha = 0.3f), RoundedCornerShape(10.dp)).clickable { face = f; Sfx.play(Sound.TICK) }.padding(3.dp)) { Die(f, 38.dp) }
                        }
                    }
                    FunkyButton(if (valid) "🎲  BID  $qty × $face" else "Bid higher than ${b?.qty} × ${b?.face}", { Sfx.play(Sound.COIN); engine.bid(qty, face) }, Modifier.fillMaxWidth(), listOf(Color(0xFF16A34A), Color(0xFF065F46)), enabled = canBid && valid, fontSize = 18.sp)
                    if (b != null) FunkyButton("☝️  LIAR!", { Sfx.play(Sound.BUZZ); engine.callLiar() }, Modifier.fillMaxWidth(), listOf(Color(0xFFDC2626), Color(0xFF7F1D1D)), enabled = canBid, fontSize = 20.sp)
                }
                s.stage == "BID" -> Text("⏳ ${engine.theirName} is thinking…", color = Color.White.copy(alpha = 0.8f), fontWeight = FontWeight.Bold)
                s.stage == "REVEAL" -> Text("☝️ ${if (s.caller == 1) "You call" else "${engine.theirName} calls"} LIAR! Lifting the cups…", color = Color(0xFFFFD23F), fontWeight = FontWeight.Black)
                s.stage == "SHOW" -> FunkyButton(if (s.myAck) "Waiting for ${engine.theirName}…" else "Next round ▶", { engine.ack() }, Modifier.fillMaxWidth(), listOf(Color(0xFF7C3AED), Color(0xFFF59E0B)), enabled = !s.myAck, fontSize = 18.sp)
            }
            s.history.takeLast(3).reversed().forEach { Text(it, color = Color.White.copy(alpha = 0.55f), fontSize = 11.sp, textAlign = TextAlign.Center) }
        }
        if (s.phase == GPhase.OVER) {
            EndOverlay(
                title = if (s.result == 1) "🏆 YOU WIN!" else "😬 ${engine.theirName.uppercase()} WINS",
                subtitle = if (s.result == 1) "${engine.theirName} is out of dice" else "You're out of dice",
                win = s.result == 1, isHost = engine.isHost, theirName = engine.theirName,
                playAgainLabel = "Play again", colors = listOf(Color(0xFF7F1D1D), Color(0xFF1E293B)),
                onPlayAgain = onPlayAgain, onBackToGames = onBackToGames, onLeave = onLeave,
                how = "The last round: there ${if (s.actual == 1) "was" else "were"} ${s.actual} × $faceNow, so the bid was ${if (s.bidGood) "good" else "a bluff"}.", revealMs = 2500L,
            )
        }
    }
}

@Composable
private fun StepButton(label: String, enabled: Boolean, onClick: () -> Unit) {
    Box(
        Modifier.size(46.dp).clip(RoundedCornerShape(23.dp)).background(if (enabled) Color(0xFF334155) else Color(0xFF1E293B)).border(2.dp, Color.White.copy(alpha = 0.4f), RoundedCornerShape(23.dp))
            .clickable(enabled = enabled) { Sfx.play(Sound.TICK); onClick() },
        contentAlignment = Alignment.Center,
    ) { Text(label, color = Color.White, fontWeight = FontWeight.Black, fontSize = 24.sp) }
}
