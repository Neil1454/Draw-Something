package com.neil1454.drawsomething.ui

import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.tween
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
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.neil1454.drawsomething.UiState
import com.neil1454.drawsomething.core.GPhase
import com.neil1454.drawsomething.core.GameKind
import com.neil1454.drawsomething.core.SdItemView
import com.neil1454.drawsomething.core.ShowdownEngine
import com.neil1454.drawsomething.core.ShowdownRules
import com.neil1454.drawsomething.core.ShowdownState
import com.neil1454.drawsomething.core.Sound
import com.neil1454.drawsomething.core.StakeWire
import com.neil1454.drawsomething.sound.Sfx

@Composable
fun ShowdownHelp() {
    Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
        Text("Every stake you each typed in is up for grabs, one at a time, and each one is its own contest!")
        Text("• The next stake is turned over. The player who did NOT put it up is the challenger, and picks the contest from four options. Each option says what you need to do to win.")
        Text("• The contest is a real game from the app: Connect 4, Pairs, Pig, Blackjack, Minesweeper, Battleships and more. Win it and you claim that stake.")
        Text("• A tie rolls the stake over: the next stake joins the pot and the winner takes both.")
        Text("• Mystery stakes stay face-down until their round comes up, then flip over.")
        Text("• When every stake is settled, whoever claimed the most wins (money amounts break a tie). You each need at least one stake to start.")
    }
}

private val Gold = Color(0xFFFFD23F)

private fun stakeLine(v: SdItemView) = if (v.mystery) "🎁 Mystery stake" else v.text

/** A stake card: face-down for a mystery until it flips over. */
@Composable
private fun StakeCard(v: SdItemView, ownerName: String, highlight: Boolean) {
    val flip = remember(v.index) { Animatable(if (v.mystery) 0f else 1f) }
    LaunchedEffect(v.mystery) { if (!v.mystery && flip.value < 1f) { Sfx.play(Sound.FLIP); flip.animateTo(1f, tween(600)) } }
    val pulse by rememberInfiniteTransition(label = "stake").animateFloat(0.4f, 1f, infiniteRepeatable(tween(800), RepeatMode.Reverse), label = "stakePulse")
    val shape = RoundedCornerShape(18.dp)
    val face = flip.value >= 0.5f
    Column(
        Modifier.fillMaxWidth().graphicsLayer { scaleX = kotlin.math.abs(flip.value * 2f - 1f).coerceAtLeast(0.03f) }
            .clip(shape).background(if (face) Brush.linearGradient(listOf(Color(0xFF7C2D12), Gold)) else Brush.linearGradient(listOf(Color(0xFF312E81), Color(0xFF7C3AED))))
            .border(if (highlight) 3.dp else 1.5.dp, Gold.copy(alpha = if (highlight) pulse else 0.7f), shape).padding(16.dp),
        horizontalAlignment = Alignment.CenterHorizontally, verticalArrangement = Arrangement.spacedBy(4.dp),
    ) {
        Text(if (face) "💰" else "🎁", fontSize = 34.sp)
        Text(if (face) stakeLine(v) else "MYSTERY STAKE", color = Color.White, fontWeight = FontWeight.Black, fontSize = 22.sp, textAlign = TextAlign.Center)
        Text("offered by $ownerName", color = Color.White.copy(alpha = 0.85f), fontSize = 12.sp)
    }
}

@Composable
private fun ProgressDots(s: ShowdownState) {
    Row(horizontalArrangement = Arrangement.spacedBy(6.dp), verticalAlignment = Alignment.CenterVertically) {
        s.items.forEach { v ->
            val col = when {
                v.claimedBy == 1 -> Color(0xFF4ADE80)
                v.claimedBy == 2 -> Color(0xFFFB923C)
                s.pot.any { it.index == v.index } -> Gold
                else -> Color.White.copy(alpha = 0.25f)
            }
            Box(Modifier.size(if (s.pot.any { it.index == v.index }) 16.dp else 12.dp).clip(CircleShape).background(col))
        }
    }
}

@Composable
fun ShowdownScreen(
    s: ShowdownState,
    engine: ShowdownEngine,
    ui: UiState,
    onPlayAgain: () -> Unit,
    onBackToGames: () -> Unit,
    onLeave: () -> Unit,
) {
    val me = ui.name.ifBlank { "You" }
    val them = ui.partnerName
    var confetti by remember { mutableIntStateOf(0) }
    LaunchedEffect(s.stage, s.played) {
        if (s.stage == "CLAIM") { if (s.lastWinner == 1) { confetti++; Sfx.play(Sound.WIN) } else if (s.lastWinner == 2) Sfx.play(Sound.LOSE) else Sfx.play(Sound.BUZZ) }
        if (s.stage == "PICK" && s.played > 0) Sfx.play(Sound.CARD)
    }
    Box(Modifier.fillMaxSize().background(Brush.verticalGradient(listOf(Color(0xFF1E1B4B), Color(0xFF3B0764), Color(0xFF111827))))) {
        Column(
            Modifier.fillMaxSize().verticalScroll(rememberScrollState()).padding(horizontal = 14.dp, vertical = 12.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp), horizontalAlignment = Alignment.CenterHorizontally,
        ) {
            Text("🎰 STAKES SHOWDOWN", color = Gold, fontWeight = FontWeight.Black, fontSize = 20.sp, letterSpacing = 2.sp)
            ProgressDots(s)
            // the scoreboard
            Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                ScoreBox("You", s.myClaims.size, s.myMoney, Color(0xFF4ADE80), Modifier.weight(1f))
                ScoreBox(them, s.theirClaims.size, s.theirMoney, Color(0xFFFB923C), Modifier.weight(1f))
            }

            when (s.stage) {
                "PICK" -> PickStage(s, engine, me, them)
                "PLAY" -> {
                    Text("🔥 UP FOR GRABS", color = Gold, fontWeight = FontWeight.Black, letterSpacing = 3.sp)
                    s.pot.forEach { StakeCard(it, if (it.mine) "you" else them, true) }
                    val c = s.picked?.let { ShowdownRules.contest(it) }
                    if (c != null) {
                        Text("${c.kind.emoji} ${c.kind.label}", color = Color.White, fontWeight = FontWeight.Black, fontSize = 20.sp)
                        Text("🎯 To win: ${c.goal}", color = Color.White, textAlign = TextAlign.Center)
                    }
                    Text("The contest is on. Finish it, then come back here.", color = Color.White.copy(alpha = 0.7f), textAlign = TextAlign.Center)
                }
                "CLAIM" -> ClaimStage(s, engine, me, them)
                else -> FinalStage(s, me, them, onPlayAgain, onBackToGames, onLeave)
            }
            if (s.stage != "FINAL") s.log.takeLast(2).forEach { Text(it, color = Color.White.copy(alpha = 0.55f), fontSize = 11.sp, textAlign = TextAlign.Center) }
        }
        ConfettiBurst(confetti, Modifier.fillMaxSize())
    }
}

@Composable
private fun ScoreBox(name: String, stakes: Int, money: Double, accent: Color, modifier: Modifier) {
    val shape = RoundedCornerShape(14.dp)
    Column(modifier.clip(shape).background(Color(0x55000000)).border(1.5.dp, accent.copy(alpha = 0.7f), shape).padding(10.dp), horizontalAlignment = Alignment.CenterHorizontally) {
        Text(name, color = accent, fontWeight = FontWeight.Bold, fontSize = 13.sp, maxLines = 1)
        Text("$stakes won", color = Color.White, fontWeight = FontWeight.Black, fontSize = 22.sp)
        if (money > 0) Text(StakeWire.moneyText(money), color = Gold, fontWeight = FontWeight.Bold, fontSize = 13.sp)
    }
}

@Composable
private fun PickStage(s: ShowdownState, engine: ShowdownEngine, me: String, them: String) {
    Text("🔥 UP FOR GRABS${if (s.pot.size > 1) " (rolled over!)" else ""}", color = Gold, fontWeight = FontWeight.Black, letterSpacing = 3.sp)
    s.pot.forEach { StakeCard(it, if (it.mine) "you" else them, true) }
    if (s.chooserMe) {
        Text("🎯 Your pick! Choose the contest", color = Color.White, fontWeight = FontWeight.Black, fontSize = 18.sp)
        Text("Win it and you claim ${if (s.pot.size > 1) "everything in the pot" else "this stake"}.", color = Color.White.copy(alpha = 0.8f), fontSize = 13.sp)
    } else {
        Text("⏳ $them is choosing the contest…", color = Color.White, fontWeight = FontWeight.Bold, fontSize = 16.sp)
        Text("These are the options they can pick from:", color = Color.White.copy(alpha = 0.7f), fontSize = 12.sp)
    }
    s.options.forEach { k ->
        val c = ShowdownRules.contest(k) ?: return@forEach
        val shape = RoundedCornerShape(16.dp)
        Row(
            Modifier.fillMaxWidth().clip(shape).background(Color(if (s.chooserMe) 0x44FFFFFF else 0x22FFFFFF)).border(1.5.dp, Color.White.copy(alpha = if (s.chooserMe) 0.6f else 0.25f), shape)
                .clickable(enabled = s.chooserMe) { Sfx.play(Sound.GO); engine.pick(k) }.padding(12.dp),
            verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(12.dp),
        ) {
            GameIcon(k, 44.dp)
            Column(Modifier.weight(1f)) {
                Text(c.kind.label, color = Color.White, fontWeight = FontWeight.Black, fontSize = 17.sp)
                Text("🎯 To win: ${c.goal}", color = Color.White.copy(alpha = 0.9f), fontSize = 13.sp)
                Text("⏱ ${c.time}", color = Color.White.copy(alpha = 0.55f), fontSize = 11.sp)
            }
        }
    }
}

@Composable
private fun ClaimStage(s: ShowdownState, engine: ShowdownEngine, me: String, them: String) {
    val pop = remember(s.played) { Animatable(0f) }
    LaunchedEffect(s.played) { pop.animateTo(1f, tween(600)) }
    val kind = s.lastKind
    Text(
        when (s.lastWinner) { 1 -> "🏆 YOU WIN IT!"; 2 -> "😬 $them WINS IT"; else -> "🤝 A TIE!" },
        color = if (s.lastWinner == 1) Color(0xFF4ADE80) else if (s.lastWinner == 2) Color(0xFFFB923C) else Gold,
        fontWeight = FontWeight.Black, fontSize = 28.sp,
        modifier = Modifier.graphicsLayer { val sc = 0.5f + 0.5f * pop.value; scaleX = sc; scaleY = sc; alpha = pop.value },
    )
    if (kind != null) Text("${kind.emoji} ${kind.label}", color = Color.White.copy(alpha = 0.8f))
    if (s.lastWinner == 3) {
        Text("Nobody takes the stake: it rolls over into the next round, and the next stake joins it.", color = Color.White, textAlign = TextAlign.Center)
        s.pot.forEach { StakeCard(it, if (it.mine) "you" else them, false) }
    } else {
        Text(if (s.lastWinner == 1) "You claim:" else "$them claims:", color = Gold, fontWeight = FontWeight.Bold)
        val claimed = s.items.filter { it.claimedBy == s.lastWinner }.takeLast(s.pot.size.coerceAtLeast(1))
        s.pot.forEach { StakeCard(it, if (it.mine) "you" else them, false) }
        if (claimed.isEmpty()) Text("")
    }
    FunkyButton(
        if (s.myAck) "Waiting for $them…" else "Next stake ▶", { Sfx.play(Sound.TAP); engine.ack() }, Modifier.fillMaxWidth(),
        listOf(Color(0xFF7C3AED), Color(0xFFF59E0B)), enabled = !s.myAck, fontSize = 18.sp,
    )
}

@Composable
private fun FinalStage(s: ShowdownState, me: String, them: String, onPlayAgain: () -> Unit, onBackToGames: () -> Unit, onLeave: () -> Unit) {
    val won = s.result == 1
    fun list(items: List<SdItemView>) = if (items.isEmpty()) "nothing" else items.joinToString(", ") { stakeLine(it) }
    EndOverlay(
        title = if (s.why == "quit") "Showdown ended" else when (s.result) { 1 -> "🏆 YOU WIN THE SHOWDOWN!"; 2 -> "🏆 ${them.uppercase()} WINS THE SHOWDOWN!"; else -> "🤝 THE SHOWDOWN IS LEVEL!" },
        subtitle = "You won ${s.myClaims.size} · $them won ${s.theirClaims.size}",
        win = won, isHost = s.iAmHost, theirName = them,
        playAgainLabel = "Another showdown", colors = listOf(Color(0xFF7C3AED), Color(0xFFF59E0B)),
        onPlayAgain = onPlayAgain, onBackToGames = onBackToGames, onLeave = onLeave,
        how = "You claimed: ${list(s.myClaims)}.\n$them claimed: ${list(s.theirClaims)}." +
            (if (s.myMoney > 0 || s.theirMoney > 0) "\nMoney: you ${StakeWire.moneyText(s.myMoney)} · $them ${StakeWire.moneyText(s.theirMoney)}." else "") +
            "\nSettle up outside the app!",
        revealMs = 0L,
    )
}
