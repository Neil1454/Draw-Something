package com.neil1454.drawsomething.ui

import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
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
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.drawscope.DrawScope
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.neil1454.drawsomething.core.DealNoDealEngine
import com.neil1454.drawsomething.core.DndBox
import com.neil1454.drawsomething.core.DndRules
import com.neil1454.drawsomething.core.DndState
import com.neil1454.drawsomething.core.GPhase
import com.neil1454.drawsomething.core.Sound
import com.neil1454.drawsomething.sound.Sfx

@Composable
fun DealNoDealHelp() {
    Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
        Text("The UK TV game, for two. 22 sealed boxes hold prizes from 1p up to £250,000.")
        Text("• First, each of you picks ONE box to keep. Nobody knows what is inside.")
        Text("• Then you take turns opening the other boxes. Every prize you open is out of the game, so it can't be in either of your boxes.")
        Text("• After each round the Banker rings with an offer for each of you. Secretly choose DEAL (take the money and leave the game) or NO DEAL (play on). Both answers are shown together. Take a deal and your own box is opened so you can see what you gave up.")
        Text("• At the very end, if you are both still in, you can offer to SWAP boxes. It only happens if you both say yes.")
        Text("• Whoever walks away with more money wins!")
    }
}

private val Blue = Color(0xFF2563EB)
private val Red = Color(0xFFDC2626)
private val Gold = Color(0xFFFFD23F)
private val MineCol = Color(0xFF22D3EE)
private val TheirCol = Color(0xFFF472B6)

private fun isHigh(v: Long) = v >= 100_000L // £1,000 and over are the red prizes

@Composable
fun DealNoDealScreen(s: DndState, engine: DealNoDealEngine, onPlayAgain: () -> Unit, onBackToGames: () -> Unit, onLeave: () -> Unit) {
    val pulse by rememberInfiniteTransition(label = "dnd").animateFloat(0f, 1f, infiniteRepeatable(tween(800), RepeatMode.Reverse), label = "p")
    LaunchedEffect(s.moves) {
        if (s.moves == 0) return@LaunchedEffect
        when (s.evType) {
            "open" -> Sfx.play(if (isHigh(s.evValue)) Sound.BAD else Sound.GOOD)
            "deal" -> Sfx.play(Sound.COIN)
            "pick" -> Sfx.play(Sound.CARD)
            "swap", "keep" -> Sfx.play(Sound.WHOOSH)
        }
    }
    LaunchedEffect(s.stage) { if (s.stage == "OFFER") Sfx.play(Sound.CHIME) }
    val unseen = s.unseen
    val unseenSet = unseen.toSet()
    val them = engine.theirName

    fun who(by: Int) = if (by == 1) "You" else if (by == 2) them else "Both of you"
    val headline = when (s.stage) {
        "PICK" -> if (s.myBox == 0) "📦 Choose YOUR box to keep (tap one)" else "⏳ $them is choosing a box…"
        "OPEN" -> if (s.myTurn) "👉 Your turn: open a box (${s.opensLeft} left this round)" else if (s.myDealt) "You took the deal. Watching… (${s.opensLeft} left)" else "⏳ $them is opening a box… (${s.opensLeft} left)"
        "OFFER" -> "📞 The Banker is calling!"
        "SWAP" -> "🔁 Swap boxes?"
        else -> "🎁 The boxes open…"
    }
    val eventText = when (s.evType) {
        "open" -> "${who(s.evBy)} opened box ${s.evBox}: ${DndRules.money(s.evValue)}"
        "deal" -> "${who(s.evBy)} took the deal and walked away. The box held ${DndRules.money(s.evValue)}!"
        "nodeal" -> ""
        "swap" -> "🔁 You both agreed: the boxes have been swapped!"
        "keep" -> "No swap: you each keep your own box."
        "pick" -> "${who(s.evBy)} chose box ${s.evBox}"
        else -> ""
    }

    Box(Modifier.fillMaxSize().background(Brush.verticalGradient(listOf(Color(0xFF0B1030), Color(0xFF1B1464), Color(0xFF4C1D95))))) {
        Column(Modifier.fillMaxSize().verticalScroll(rememberScrollState()).padding(horizontal = 10.dp, vertical = 10.dp), horizontalAlignment = Alignment.CenterHorizontally, verticalArrangement = Arrangement.spacedBy(8.dp)) {
            Text("🎁 DEAL OR NO DEAL", color = Gold, fontWeight = FontWeight.Black, fontSize = 19.sp, letterSpacing = 2.sp)
            Text("Round ${s.round} of ${s.rounds}", color = Color.White.copy(alpha = 0.7f), fontSize = 12.sp)
            Text(headline, color = Color.White, fontWeight = FontWeight.Bold, fontSize = 15.sp, textAlign = TextAlign.Center)
            if (eventText.isNotEmpty()) Text(eventText, color = Gold, fontWeight = FontWeight.Bold, fontSize = 13.sp, textAlign = TextAlign.Center)

            // the prizes still in play
            Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                for (half in listOf(DndRules.PRIZES.toList().subList(0, 11), DndRules.PRIZES.toList().subList(11, 22))) {
                    Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(3.dp)) {
                        for (v in half) {
                            val live = v in unseenSet
                            val col = if (isHigh(v)) Red else Blue
                            Box(
                                Modifier.fillMaxWidth().clip(RoundedCornerShape(6.dp)).background(if (live) col else Color(0xFF1F2937)).padding(vertical = 3.dp),
                                contentAlignment = Alignment.Center,
                            ) { Text(DndRules.money(v), color = if (live) Color.White else Color.White.copy(alpha = 0.25f), fontWeight = FontWeight.Bold, fontSize = 12.sp, maxLines = 1) }
                        }
                    }
                }
            }

            // the banker's phone call
            if (s.stage == "OFFER") {
                Column(
                    Modifier.fillMaxWidth().clip(RoundedCornerShape(18.dp)).background(Color(0xFF0F172A)).border(2.dp, Gold.copy(alpha = 0.5f + 0.5f * pulse), RoundedCornerShape(18.dp)).padding(14.dp),
                    horizontalAlignment = Alignment.CenterHorizontally, verticalArrangement = Arrangement.spacedBy(6.dp),
                ) {
                    Text("☎️", fontSize = (34 + 4 * pulse).sp)
                    Text("The Banker offers", color = Color.White.copy(alpha = 0.8f), fontSize = 13.sp)
                    if (!s.myDealt) Text(DndRules.money(s.myOffer), color = Gold, fontWeight = FontWeight.Black, fontSize = 38.sp)
                    else Text("You dealt for ${DndRules.money(s.myDealAmount)}", color = Gold, fontWeight = FontWeight.Black, fontSize = 20.sp)
                    if (!s.theirDealt) Text("$them is offered ${DndRules.money(s.theirOffer)}", color = TheirCol, fontSize = 14.sp, fontWeight = FontWeight.Bold)
                    else Text("$them dealt for ${DndRules.money(s.theirDealAmount)}", color = TheirCol, fontSize = 14.sp, fontWeight = FontWeight.Bold)
                    if (s.myDealt) Text("Waiting for $them to decide…", color = Color.White.copy(alpha = 0.7f), fontSize = 13.sp)
                    else if (s.myAnswered) Text("Your answer is locked in. Waiting for $them…", color = Color.White.copy(alpha = 0.8f), fontSize = 13.sp)
                    else Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                        FunkyButton("DEAL", { engine.answer(true) }, Modifier.weight(1f), listOf(Color(0xFF16A34A), Color(0xFF0E9F6E)), fontSize = 20.sp)
                        FunkyButton("NO DEAL", { engine.answer(false) }, Modifier.weight(1f), listOf(Color(0xFFDC2626), Color(0xFFEA580C)), fontSize = 20.sp)
                    }
                    Text("Both answers are revealed together.", color = Color.White.copy(alpha = 0.5f), fontSize = 11.sp)
                }
            }
            if (s.stage == "SWAP") {
                Column(
                    Modifier.fillMaxWidth().clip(RoundedCornerShape(18.dp)).background(Color(0xFF0F172A)).border(2.dp, MineCol, RoundedCornerShape(18.dp)).padding(14.dp),
                    horizontalAlignment = Alignment.CenterHorizontally, verticalArrangement = Arrangement.spacedBy(8.dp),
                ) {
                    Text("Only two boxes are left: yours (${s.myBox}) and $them's (${s.theirBox}).", color = Color.White, fontSize = 14.sp, textAlign = TextAlign.Center)
                    Text("Do you want to swap boxes? It only happens if you BOTH say yes.", color = Color.White.copy(alpha = 0.8f), fontSize = 13.sp, textAlign = TextAlign.Center)
                    if (s.swapSaid) Text("Locked in. Waiting for $them…", color = Gold, fontWeight = FontWeight.Bold)
                    else Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                        FunkyButton("🔁 SWAP", { engine.swap(true) }, Modifier.weight(1f), listOf(Color(0xFF7C3AED), Color(0xFFEC4899)), fontSize = 18.sp)
                        FunkyButton("KEEP", { engine.swap(false) }, Modifier.weight(1f), listOf(Color(0xFF334155), Color(0xFF475569)), fontSize = 18.sp)
                    }
                }
            }

            // the boxes
            Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                for (row in s.boxes.chunked(6)) {
                    Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        for (b in row) BoxCell(b, s, pulse) {
                            when {
                                s.phase != GPhase.PLAYING -> Unit
                                s.stage == "PICK" && s.myBox == 0 && b.number != s.theirBox -> engine.pick(b.number)
                                s.stage == "OPEN" && s.myTurn && b.status == 0 -> engine.open(b.number)
                            }
                        }
                    }
                }
            }
            Row(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                Text("🟦 You: box ${if (s.myBox == 0) "–" else s.myBox.toString()}", color = MineCol, fontSize = 13.sp, fontWeight = FontWeight.Bold)
                Text("🟪 $them: box ${if (s.theirBox == 0) "–" else s.theirBox.toString()}", color = TheirCol, fontSize = 13.sp, fontWeight = FontWeight.Bold)
            }
            if (s.myDealt || s.theirDealt) Text(
                (if (s.myDealt) "You: ${DndRules.money(s.myDealAmount)} banked.  " else "") + (if (s.theirDealt) "$them: ${DndRules.money(s.theirDealAmount)} banked." else ""),
                color = Color.White.copy(alpha = 0.8f), fontSize = 12.sp,
            )
        }
        if (s.phase == GPhase.OVER) {
            EndOverlay(
                title = when (s.result) { 1 -> "🏆 YOU WIN!"; 2 -> "🏆 ${them.uppercase()} WINS!"; else -> "🤝 IT'S A DRAW!" },
                subtitle = "You ${DndRules.money(s.myFinal)}  ·  $them ${DndRules.money(s.theirFinal)}",
                win = s.result == 1, isHost = engine.isHost, theirName = them,
                playAgainLabel = "Play again", colors = listOf(Color(0xFF1B1464), Color(0xFFFFD23F)),
                onPlayAgain = onPlayAgain, onBackToGames = onBackToGames, onLeave = onLeave,
                how = (if (s.myDealt) "You dealt for ${DndRules.money(s.myDealAmount)}; your box held ${DndRules.money(s.myBoxValue)}. " else "Your box held ${DndRules.money(s.myBoxValue)}. ") +
                    (if (s.theirDealt) "$them dealt for ${DndRules.money(s.theirDealAmount)}; their box held ${DndRules.money(s.theirBoxValue)}." else "$them's box held ${DndRules.money(s.theirBoxValue)}.") +
                    if (s.swapped) " (You swapped boxes.)" else "",
                revealMs = 4500L,
            )
        }
    }
}

@Composable
private fun BoxCell(b: DndBox, s: DndState, pulse: Float, onClick: () -> Unit) {
    val mine = b.status == 2
    val theirs = b.status == 3
    val open = b.status == 1 || (b.value >= 0L)
    val tappable = (s.stage == "PICK" && s.myBox == 0 && b.status == 0 && b.number != s.theirBox) || (s.stage == "OPEN" && s.myTurn && b.status == 0)
    val shape = RoundedCornerShape(10.dp)
    val bg = when {
        open && !mine && !theirs -> if (isHigh(b.value)) Brush.verticalGradient(listOf(Color(0xFF991B1B), Color(0xFF7F1D1D))) else Brush.verticalGradient(listOf(Color(0xFF1D4ED8), Color(0xFF1E3A8A)))
        mine -> Brush.verticalGradient(listOf(Color(0xFF0E7490), Color(0xFF164E63)))
        theirs -> Brush.verticalGradient(listOf(Color(0xFFBE185D), Color(0xFF831843)))
        else -> Brush.verticalGradient(listOf(Color(0xFFF59E0B), Color(0xFFB45309)))
    }
    Box(
        Modifier.size(width = 50.dp, height = 56.dp).clip(shape).background(bg)
            .border(if (tappable) 3.dp else 2.dp, if (mine) MineCol else if (theirs) TheirCol else if (tappable) Color.White.copy(alpha = 0.5f + 0.5f * pulse) else Color.White.copy(alpha = 0.35f), shape)
            .clickable(enabled = tappable, onClick = onClick),
        contentAlignment = Alignment.Center,
    ) {
        Column(horizontalAlignment = Alignment.CenterHorizontally) {
            if (open && (mine || theirs)) Text(DndRules.money(b.value), color = Color.White, fontSize = 9.sp, fontWeight = FontWeight.Black, maxLines = 1)
            else if (open) Text(DndRules.money(b.value), color = Color.White, fontSize = 10.sp, fontWeight = FontWeight.Black, maxLines = 1)
            else Text("${b.number}", color = Color(0xFF111827), fontSize = 20.sp, fontWeight = FontWeight.Black)
            if (mine) Text("YOU", color = Color.White, fontSize = 8.sp, fontWeight = FontWeight.Black)
            if (theirs) Text("THEM", color = Color.White, fontSize = 8.sp, fontWeight = FontWeight.Black)
            if (!open && (mine || theirs)) Text("${b.number}", color = Color.White, fontSize = 16.sp, fontWeight = FontWeight.Black)
        }
    }
}

internal fun DrawScope.dealIcon() {
    drawRoundRect(Color(0xFFF59E0B), Offset(14f, 38f), Size(72f, 50f), CornerRadius(8f))
    drawRoundRect(Color.White.copy(alpha = 0.9f), Offset(14f, 38f), Size(72f, 50f), CornerRadius(8f), style = Stroke(3f))
    drawRoundRect(Color(0xFFB45309), Offset(10f, 26f), Size(80f, 16f), CornerRadius(6f))
    drawRoundRect(Color.White.copy(alpha = 0.9f), Offset(10f, 26f), Size(80f, 16f), CornerRadius(6f), style = Stroke(3f))
    drawRect(Color.White.copy(alpha = 0.9f), Offset(46f, 26f), Size(8f, 62f))
    drawCircle(Color.White.copy(alpha = 0.9f), 8f, Offset(42f, 20f), style = Stroke(3f))
    drawCircle(Color.White.copy(alpha = 0.9f), 8f, Offset(58f, 20f), style = Stroke(3f))
}
