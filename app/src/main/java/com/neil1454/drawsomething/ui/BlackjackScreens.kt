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
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
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
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.neil1454.drawsomething.core.BjAdvice
import com.neil1454.drawsomething.core.BjHandView
import com.neil1454.drawsomething.core.BjMath
import com.neil1454.drawsomething.core.BlackjackEngine
import com.neil1454.drawsomething.core.BlackjackState
import com.neil1454.drawsomething.core.GPhase
import com.neil1454.drawsomething.core.Sound
import com.neil1454.drawsomething.sound.Sfx
import kotlinx.coroutines.delay
import kotlin.math.abs
import kotlin.math.sin

@Composable
fun BlackjackHelp() {
    androidx.compose.foundation.layout.Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
        Text("You both play against the same dealer, at the same time, with your own chips and your own cards. Get closer to 21 than the dealer without going over!")
        Text("• Number cards are worth their number, J Q K are 10, and an ace is 1 or 11 (whichever helps).")
        Text("• HIT takes a card, STAND keeps your hand, DOUBLE DOWN doubles your bet for exactly one more card (on your first two cards only).")
        Text("• A blackjack (an ace and a 10-value card) pays 3 to 2. The dealer draws to 16 and stands on every 17. A tie gives your bet back.")
        Text("• After the rounds are played, whoever has the most chips wins. If a player runs out of chips, the game ends.")
        Text("• Coach mode (if the host switches it on) lets you ask for a tip that says what to do and why, using proper basic strategy, and shows your chance of going bust.")
    }
}

private val Felt1 = Color(0xFF0E6B35)
private val Felt2 = Color(0xFF07381C)
private val Gold = Color(0xFFFFD23F)

private fun chips(n: Int) = "£" + java.text.NumberFormat.getIntegerInstance().format(n)

private fun handText(h: BjHandView): String {
    if (h.cards.isEmpty()) return ""
    val (t, soft) = BjMath.total(h.cards)
    return when {
        BjMath.isBlackjack(h.cards) -> "BLACKJACK!"
        t > 21 -> "BUST $t"
        soft && t < 21 -> "Soft $t"
        else -> "$t"
    }
}

/** One playing card: it slides in from the shoe (top right), and a face-down card turns over when it is revealed. */
@Composable
private fun PlayingCard(card: Int, width: Dp, delayMs: Int) {
    val appear = remember { Animatable(0f) }
    val face = remember { Animatable(if (card >= 0) 1f else 0f) }
    LaunchedEffect(Unit) {
        delay(delayMs.toLong())
        Sfx.play(Sound.CARD)
        appear.animateTo(1f, tween(380))
    }
    LaunchedEffect(card) {
        val target = if (card >= 0) 1f else 0f
        if (face.value != target) { Sfx.play(Sound.FLIP); face.animateTo(target, tween(480)) }
    }
    val shape = RoundedCornerShape(width / 9)
    val showFace = face.value >= 0.5f
    val squash = abs(face.value * 2f - 1f)
    Box(
        Modifier.width(width).height(width * 1.4f).graphicsLayer {
            val a = appear.value
            translationX = (1f - a) * 160f * density
            translationY = (1f - a) * -190f * density
            rotationZ = (1f - a) * -28f
            alpha = a.coerceAtLeast(0.001f)
            scaleX = squash.coerceAtLeast(0.02f)
        }.clip(shape),
    ) {
        if (showFace && card >= 0) {
            val red = BjMath.isRed(card)
            val col = if (red) Color(0xFFD32F2F) else Color(0xFF111827)
            Box(Modifier.fillMaxSize().background(Color(0xFFFFFDF5)).border(1.5.dp, Color(0xFF1F2937), shape)) {
                Column(Modifier.padding(start = width / 12, top = width / 16), horizontalAlignment = Alignment.CenterHorizontally) {
                    Text(BjMath.rankName(card), color = col, fontWeight = FontWeight.Black, fontSize = (width.value * 0.3f).sp, lineHeight = (width.value * 0.3f).sp)
                    Text(BjMath.name(card).takeLast(1), color = col, fontSize = (width.value * 0.24f).sp, lineHeight = (width.value * 0.24f).sp)
                }
                Text(BjMath.name(card).takeLast(1), color = col.copy(alpha = 0.9f), fontSize = (width.value * 0.62f).sp, modifier = Modifier.align(Alignment.Center))
            }
        } else {
            Box(
                Modifier.fillMaxSize().background(Brush.linearGradient(listOf(Color(0xFF1E3A8A), Color(0xFF3B82F6))))
                    .border(2.dp, Color.White, shape).padding(width / 10),
            ) {
                Box(Modifier.fillMaxSize().border(1.5.dp, Color.White.copy(alpha = 0.6f), RoundedCornerShape(4.dp)).background(Color(0x22FFFFFF)))
                Text("♠", color = Color.White.copy(alpha = 0.5f), fontSize = (width.value * 0.5f).sp, modifier = Modifier.align(Alignment.Center))
            }
        }
    }
}

@Composable
private fun HandRow(cards: List<Int>, width: Dp, stageKey: Any, firstDeal: Boolean, who: Int) {
    Row(horizontalArrangement = Arrangement.spacedBy(-(width.value * 0.42f).dp), verticalAlignment = Alignment.CenterVertically) {
        cards.forEachIndexed { i, c ->
            androidx.compose.runtime.key(stageKey, i) { PlayingCard(c, width, if (firstDeal && i < 2) (i * 2 + who) * 230 else 0) }
        }
    }
}

@Composable
private fun OutcomeTag(h: BjHandView) {
    val (text, col) = when (h.outcome) {
        "bj" -> "🂡 BLACKJACK +${chips(h.bet * 3 / 2)}" to Gold
        "win" -> "WIN +${chips(h.bet)}" to Color(0xFF4ADE80)
        "push" -> "PUSH" to Color(0xFFCBD5E1)
        "bust" -> "BUST −${chips(h.bet)}" to Color(0xFFF87171)
        "lose" -> "LOSE −${chips(h.bet)}" to Color(0xFFF87171)
        else -> return
    }
    val pop = remember(h.outcome) { Animatable(0f) }
    LaunchedEffect(h.outcome) { pop.animateTo(1f, tween(450)) }
    Text(
        text, color = Color.Black, fontWeight = FontWeight.Black, fontSize = 14.sp,
        modifier = Modifier.graphicsLayer { val s = 0.6f + 0.4f * pop.value; scaleX = s; scaleY = s; alpha = pop.value }
            .clip(RoundedCornerShape(10.dp)).background(col).padding(horizontal = 10.dp, vertical = 3.dp),
    )
}

@Composable
private fun SeatHeader(name: String, chipsNow: Int, bet: Int, locked: Boolean, accent: Color, active: Boolean) {
    val shape = RoundedCornerShape(14.dp)
    Row(
        Modifier.fillMaxWidth().clip(shape).background(Color(0x66000000)).border(if (active) 2.dp else 1.dp, if (active) accent else accent.copy(alpha = 0.4f), shape).padding(horizontal = 12.dp, vertical = 6.dp),
        verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(10.dp),
    ) {
        Text(name, color = accent, fontWeight = FontWeight.Bold, fontSize = 14.sp, maxLines = 1, modifier = Modifier.weight(1f))
        if (bet > 0) Text("🪙 ${chips(bet)}${if (locked) " ✓" else ""}", color = Gold, fontWeight = FontWeight.Bold, fontSize = 13.sp)
        Text(chips(chipsNow), color = Color.White, fontWeight = FontWeight.Black, fontSize = 18.sp)
    }
}

@Composable
fun BlackjackScreen(
    s: BlackjackState,
    engine: BlackjackEngine,
    onPlayAgain: () -> Unit,
    onBackToGames: () -> Unit,
    onLeave: () -> Unit,
) {
    val over = s.phase == GPhase.OVER
    val mineCol = Color(0xFF7DD3FC)
    val theirCol = Color(0xFFFDBA74)
    val width = 62.dp
    // sounds and shakes for how the round went
    var hurt by remember { mutableIntStateOf(0) }
    LaunchedEffect(s.round, s.stage, s.mine.outcome) {
        if (s.stage != "RESULT") return@LaunchedEffect
        when (s.mine.outcome) {
            "bj" -> { Sfx.play(Sound.WIN); Sfx.play(Sound.COIN) }
            "win" -> { Sfx.play(Sound.GOOD); Sfx.play(Sound.COIN) }
            "bust", "lose" -> { Sfx.play(Sound.LOSE); hurt++ }
            "push" -> Sfx.play(Sound.TAP)
        }
    }
    val shake = rememberBurst(hurt, 600)
    val dealt = s.stage == "PLAY" || s.stage == "DEALER" || s.stage == "RESULT"
    val firstDeal = s.stage == "PLAY"
    val roundKey = s.round

    Box(Modifier.fillMaxSize().background(Brush.radialGradient(listOf(Felt1, Felt2), radius = 1400f))) {
        Column(
            Modifier.fillMaxSize().shaking(shake).verticalScroll(rememberScrollState()).padding(horizontal = 10.dp, vertical = 8.dp),
            verticalArrangement = Arrangement.spacedBy(8.dp), horizontalAlignment = Alignment.CenterHorizontally,
        ) {
            Text("ROUND ${minOf(s.round + 1, s.rounds)} OF ${s.rounds}", color = Color.White.copy(alpha = 0.8f), fontSize = 12.sp, fontWeight = FontWeight.Bold, letterSpacing = 2.sp)

            // the other player
            SeatHeader(engine.theirName, s.theirChips, s.theirs.bet, s.theirLocked, theirCol, false)
            if (dealt) {
                Row(horizontalArrangement = Arrangement.spacedBy(12.dp), verticalAlignment = Alignment.CenterVertically) {
                    HandRow(s.theirs.cards, 44.dp, roundKey, firstDeal, 1)
                    Column(horizontalAlignment = Alignment.CenterHorizontally) {
                        Text(handText(s.theirs), color = Color.White, fontWeight = FontWeight.Bold, fontSize = 13.sp)
                        if (s.theirs.done && s.stage == "PLAY") Text("done", color = Color.White.copy(alpha = 0.6f), fontSize = 11.sp)
                        OutcomeTag(s.theirs)
                    }
                }
            } else if (s.stage == "BET") {
                Text(if (s.theirLocked) "${engine.theirName} has bet ✓" else "${engine.theirName} is choosing a bet…", color = Color.White.copy(alpha = 0.7f), fontSize = 12.sp)
            }

            // the dealer
            Column(horizontalAlignment = Alignment.CenterHorizontally, modifier = Modifier.padding(vertical = 4.dp)) {
                Text("🎩 DEALER", color = Gold, fontWeight = FontWeight.Black, fontSize = 13.sp, letterSpacing = 2.sp)
                if (dealt) {
                    HandRow(s.dealer, width, roundKey, firstDeal, 0)
                    val shown = s.dealer.filter { it >= 0 }
                    val (t, soft) = if (shown.isEmpty()) 0 to false else BjMath.total(shown)
                    val hidden = s.dealer.contains(-1)
                    Text(
                        if (hidden) "showing $t" else if (t > 21) "BUST $t" else if (BjMath.isBlackjack(s.dealer)) "BLACKJACK!" else if (soft && t < 21) "Soft $t" else "$t",
                        color = Color.White, fontWeight = FontWeight.Black, fontSize = 16.sp,
                    )
                } else {
                    Box(Modifier.height(width.value.dp * 1.4f), contentAlignment = Alignment.Center) { Text("🂠", fontSize = 50.sp, color = Color.White.copy(alpha = 0.3f)) }
                }
            }

            // me
            if (dealt) {
                Row(horizontalArrangement = Arrangement.spacedBy(14.dp), verticalAlignment = Alignment.CenterVertically) {
                    HandRow(s.mine.cards, width, roundKey, firstDeal, 1)
                    Column(horizontalAlignment = Alignment.CenterHorizontally) {
                        Text(handText(s.mine), color = Color.White, fontWeight = FontWeight.Black, fontSize = 20.sp)
                        if (s.mine.doubled) Text("doubled ×2", color = Gold, fontSize = 11.sp, fontWeight = FontWeight.Bold)
                        OutcomeTag(s.mine)
                    }
                }
            }
            SeatHeader("You", s.myChips, s.mine.bet, s.myLocked || dealt, mineCol, s.canAct || (s.stage == "BET" && !s.myLocked))

            // what to do
            when (s.stage) {
                "BET" -> BetControls(s, engine)
                "PLAY" -> {
                    if (s.mine.done) {
                        Text(
                            if (BjMath.isBlackjack(s.mine.cards)) "🂡 Blackjack! Waiting for ${engine.theirName}…" else "You're done on ${BjMath.total(s.mine.cards).first}. Waiting for ${engine.theirName}…",
                            color = Color.White, fontWeight = FontWeight.Bold, textAlign = TextAlign.Center,
                        )
                    } else ActionButtons(s, engine)
                }
                "DEALER" -> Text("The dealer plays…", color = Color.White, fontWeight = FontWeight.Bold)
                "RESULT" -> Text(s.log.lastOrNull().orEmpty(), color = Color.White.copy(alpha = 0.85f), fontSize = 12.sp, textAlign = TextAlign.Center)
            }
            if (s.coach && !over) CoachPanel(s, engine)
            if (s.stage != "RESULT") s.log.takeLast(2).forEach { Text(it, color = Color.White.copy(alpha = 0.6f), fontSize = 11.sp, textAlign = TextAlign.Center) }
        }
        if (over) {
            EndOverlay(
                title = when (s.result) { 1 -> "🏆 YOU WIN!"; 2 -> "🏆 ${engine.theirName.uppercase()} WINS!"; else -> "🤝 IT'S A DRAW!" },
                subtitle = "You ${chips(s.myChips)}  ·  ${engine.theirName} ${chips(s.theirChips)}",
                win = s.result == 1, isHost = engine.isHost, theirName = engine.theirName,
                playAgainLabel = "Play again", colors = listOf(Felt1, Color(0xFF111827)),
                onPlayAgain = onPlayAgain, onBackToGames = onBackToGames, onLeave = onLeave,
                how = (if (s.why == "broke") "Someone ran out of chips, so the game ended with " else "After ${s.rounds} rounds the chips stood at ") +
                    "${chips(s.myChips)} to ${chips(s.theirChips)}. The player with the most chips wins.",
            )
        }
    }
}

@Composable
private fun BetControls(s: BlackjackState, engine: BlackjackEngine) {
    if (s.myLocked) {
        Text("Bet placed: ${chips(s.mine.bet)}. Waiting for ${engine.theirName}…", color = Color.White, fontWeight = FontWeight.Bold)
        return
    }
    val cap = s.myChips
    Column(horizontalAlignment = Alignment.CenterHorizontally, verticalArrangement = Arrangement.spacedBy(8.dp)) {
        Text("Place your bet", color = Color.White, fontWeight = FontWeight.Bold)
        Text("🪙 ${chips(s.mine.bet)}", color = Gold, fontWeight = FontWeight.Black, fontSize = 30.sp)
        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            listOf(10 to Color(0xFF3B82F6), 25 to Color(0xFF22C55E), 50 to Color(0xFFEF4444), 100 to Color(0xFF111827)).forEach { (v, c) ->
                val ok = s.mine.bet + v <= cap
                Box(
                    Modifier.size(54.dp).clip(androidx.compose.foundation.shape.CircleShape).background(if (ok) c else Color(0xFF6B7280))
                        .border(3.dp, Color.White, androidx.compose.foundation.shape.CircleShape)
                        .clickableIf(ok) { Sfx.play(Sound.COIN); engine.bet(s.mine.bet + v) },
                    contentAlignment = Alignment.Center,
                ) { Text("+$v", color = Color.White, fontWeight = FontWeight.Black, fontSize = 14.sp) }
            }
        }
        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            FunkyButton("Clear", { engine.bet(0) }, Modifier.width(110.dp), listOf(Color(0xFF475569), Color(0xFF1E293B)), fontSize = 14.sp)
            FunkyButton("All in", { engine.bet(cap - cap % 5) }, Modifier.width(110.dp), listOf(Color(0xFFB45309), Color(0xFF7C2D12)), enabled = cap >= BlackjackEngine.MIN_BET, fontSize = 14.sp)
        }
        FunkyButton(
            if (s.mine.bet >= BlackjackEngine.MIN_BET) "🃏  DEAL  ${chips(s.mine.bet)}" else "Minimum bet ${chips(BlackjackEngine.MIN_BET)}",
            { engine.deal() }, Modifier.fillMaxWidth(), listOf(Color(0xFF16A34A), Color(0xFF065F46)), enabled = s.mine.bet in BlackjackEngine.MIN_BET..cap, fontSize = 18.sp,
        )
    }
}

private fun Modifier.clickableIf(enabled: Boolean, onClick: () -> Unit): Modifier =
    if (enabled) this.then(Modifier.clickable(onClick = onClick)) else this

@Composable
private fun ActionButtons(s: BlackjackState, engine: BlackjackEngine) {
    val pulse by rememberInfiniteTransition(label = "tip").animateFloat(0.3f, 1f, infiniteRepeatable(tween(600), RepeatMode.Reverse), label = "tipPulse")
    val rec = s.tip?.action
    @Composable
    fun androidx.compose.foundation.layout.RowScope.Wrapped(action: String, content: @Composable () -> Unit) {
        Box(
            Modifier.weight(1f).clip(RoundedCornerShape(26.dp))
                .border(if (rec == action) 3.dp else 0.dp, Gold.copy(alpha = if (rec == action) pulse else 0f), RoundedCornerShape(26.dp)),
        ) { content() }
    }
    Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
        Wrapped("hit") { FunkyButton("HIT", { engine.hit() }, Modifier.fillMaxWidth(), listOf(Color(0xFF16A34A), Color(0xFF65A30D)), fontSize = 18.sp) }
        Wrapped("stand") { FunkyButton("STAND", { engine.stand() }, Modifier.fillMaxWidth(), listOf(Color(0xFFDC2626), Color(0xFFEA580C)), fontSize = 18.sp) }
        Wrapped("double") { FunkyButton("DOUBLE", { engine.double() }, Modifier.fillMaxWidth(), listOf(Color(0xFFB45309), Color(0xFFFFD23F)), enabled = s.canDouble, fontSize = 16.sp) }
    }
}

@Composable
private fun CoachPanel(s: BlackjackState, engine: BlackjackEngine) {
    val shape = RoundedCornerShape(14.dp)
    val unlimited = s.tipAllowance < 0
    Column(
        Modifier.fillMaxWidth().clip(shape).background(Color(0x66000000)).border(1.5.dp, Gold.copy(alpha = 0.6f), shape).padding(10.dp),
        verticalArrangement = Arrangement.spacedBy(6.dp),
    ) {
        Text("💡 COACH", color = Gold, fontWeight = FontWeight.Black, fontSize = 12.sp, letterSpacing = 2.sp)
        if (s.canAct && s.mine.cards.isNotEmpty()) {
            val (t, soft) = BjMath.total(s.mine.cards)
            val risk = BjMath.bustChance(s.mine.cards)
            Text(
                "${if (soft) "Soft" else "Hard"} $t · if you hit there is a $risk% chance you go bust" + if (s.dealer.isNotEmpty()) " · dealer shows ${BjMath.rankName(s.dealer[0])}" else "",
                color = Color.White, fontSize = 12.sp,
            )
            val tip: BjAdvice? = s.tip
            if (tip != null) {
                Text(
                    "👉 ${tip.action.uppercase()}: ${tip.reason}", color = Gold, fontWeight = FontWeight.Bold, fontSize = 13.sp,
                    modifier = Modifier.fillMaxWidth().clip(RoundedCornerShape(10.dp)).background(Color(0x33FFD23F)).padding(8.dp),
                )
            } else {
                val canAsk = unlimited || s.tipsLeft > 0
                FunkyButton(
                    if (canAsk) "💡 Ask for a tip (${if (unlimited) "∞" else "${s.tipsLeft} left"})" else "No tips left",
                    { engine.tip() }, Modifier.fillMaxWidth(), listOf(Color(0xFFB45309), Color(0xFF92400E)), enabled = canAsk, fontSize = 14.sp,
                )
            }
        } else {
            Text(
                if (unlimited) "Tips: unlimited. The coach helps on your turn." else "Tips left: ${s.tipsLeft}. The coach helps on your turn.",
                color = Color.White.copy(alpha = 0.7f), fontSize = 12.sp,
            )
        }
    }
}
