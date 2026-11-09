package com.neil1454.drawsomething.ui

import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.tween
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableLongStateOf
import androidx.compose.runtime.mutableStateListOf
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
import com.neil1454.drawsomething.core.GPhase
import com.neil1454.drawsomething.core.HiLoEngine
import com.neil1454.drawsomething.core.HiLoRules
import com.neil1454.drawsomething.core.HiLoState

@Composable
fun HiLoHelp() {
    Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
        Text("In the spirit of the TV game show. You get a row of seven cards; the first is face up. Guess whether the NEXT card is higher or lower.")
        Text("• Each right guess is worth more than the last: 10, 20, 30, 40, 50, 60 points. Clear the whole row for a ${HiLoRules.FULL_ROW_BONUS}-point bonus!")
        Text("• A wrong guess, or a card of the SAME value, ends your row. You keep the points you've won.")
        Text("• Then your partner gets a fresh row. Aces are high.")
        Text("• Once per game you can CHANGE the face-up card. The percentages (if switched on) are the odds from the cards still unseen.")
    }
}

private val Felt = Brush.radialGradient(listOf(Color(0xFF138A58), Color(0xFF0A4A31), Color(0xFF052B1D)))

@Composable
fun HiLoScreen(
    s: HiLoState,
    engine: HiLoEngine,
    onPlayAgain: () -> Unit,
    onBackToGames: () -> Unit,
    onLeave: () -> Unit,
) {
    val over = s.phase == GPhase.OVER
    val canGuess = s.myTurn && s.step == "GUESS" && !over && s.idx < HiLoRules.ROW_LENGTH - 1 && s.row.isNotEmpty()
    val popups = remember { mutableStateListOf<Popup>() }
    var popupId by remember { mutableLongStateOf(0L) }
    var bad by remember { mutableIntStateOf(0) }
    var party by remember { mutableIntStateOf(0) }
    val shake = rememberBurst(bad, 600)
    val flash = rememberBurst(bad, 700)

    LaunchedEffect(s.moves) {
        if (s.moves == 0) { popups.clear(); return@LaunchedEffect }
        val colour = if (s.resultByMe) Funk.Lime else Funk.Cyan
        when (s.result) {
            "hit" -> popups.add(Popup(popupId++, "✅ +${s.guesses * HiLoRules.STEP_POINTS}", colour, 0.5f, 0.38f))
            "full" -> { party++; popups.add(Popup(popupId++, "🏆 FULL ROW! +${HiLoRules.FULL_ROW_BONUS}", Funk.Sun, 0.5f, 0.38f)) }
            "miss" -> { bad++; popups.add(Popup(popupId++, "❌ WRONG", Funk.Red, 0.5f, 0.38f)) }
            "same" -> { bad++; popups.add(Popup(popupId++, "🟰 SAME VALUE!", Funk.Red, 0.5f, 0.38f)) }
            else -> popups.add(Popup(popupId++, "🔄 CHANGED", Funk.Sun, 0.5f, 0.38f))
        }
    }

    BoxWithConstraints(Modifier.fillMaxSize().background(Felt).shaking(shake)) {
        val landscape = maxWidth > maxHeight
        if (flash < 1f) Box(Modifier.fillMaxSize().background(Funk.Red.copy(alpha = 0.3f * (1f - flash))))
        val turnText = when {
            over -> "GAME OVER"
            s.step == "SHOW" -> if (s.myTurn) "Your row is over" else "${s.theirName}'s row is over"
            s.myTurn -> "👉  YOUR ROW  👈"
            else -> "⏳ ${s.theirName}'s row"
        }
        val pills: @Composable (Modifier) -> Unit = { m ->
            Row(m, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                PlayerPill("You", s.myScore, Funk.Lime, s.myTurn && !over, Modifier.weight(1f), extra = if (s.myTurn && s.run > 0) "+${s.run}" else "")
                PlayerPill(s.theirName, s.theirScore, Funk.Cyan, !s.myTurn && !over, Modifier.weight(1f), extra = if (!s.myTurn && s.run > 0) "+${s.run}" else "")
            }
        }
        val progress = "Your rows ${s.myRowsDone}/${s.rowsEach} · ${s.theirName} ${s.theirRowsDone}/${s.rowsEach}"

        if (!landscape) {
            Column(
                Modifier.fillMaxSize().padding(horizontal = 8.dp, vertical = 8.dp),
                verticalArrangement = Arrangement.spacedBy(8.dp),
                horizontalAlignment = Alignment.CenterHorizontally,
            ) {
                pills(Modifier.fillMaxWidth())
                TurnText(turnText, s.myTurn && !over)
                Text(progress, fontSize = 11.sp, color = Color.White.copy(alpha = 0.8f))
                Ladder(s)
                CardRow(s, Modifier.fillMaxWidth())
                Box(Modifier.weight(1f).fillMaxWidth(), contentAlignment = Alignment.Center) { BigCard(s, Modifier.fillMaxSize()) }
                Controls(s, canGuess, engine, Modifier.fillMaxWidth())
            }
        } else {
            Row(Modifier.fillMaxSize().padding(8.dp), horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                Column(Modifier.weight(1f).fillMaxSize(), verticalArrangement = Arrangement.spacedBy(8.dp), horizontalAlignment = Alignment.CenterHorizontally) {
                    CardRow(s, Modifier.fillMaxWidth())
                    Box(Modifier.weight(1f).fillMaxWidth(), contentAlignment = Alignment.Center) { BigCard(s, Modifier.fillMaxSize()) }
                    Controls(s, canGuess, engine, Modifier.fillMaxWidth())
                }
                Column(Modifier.width(230.dp).fillMaxSize(), verticalArrangement = Arrangement.spacedBy(10.dp, Alignment.CenterVertically), horizontalAlignment = Alignment.CenterHorizontally) {
                    pills(Modifier.fillMaxWidth())
                    TurnText(turnText, s.myTurn && !over)
                    Text(progress, fontSize = 11.sp, color = Color.White.copy(alpha = 0.8f), textAlign = TextAlign.Center)
                    Ladder(s)
                }
            }
        }
        PopupLayer(popups, { id -> popups.removeAll { it.id == id } })
        ConfettiBurst(party)
        if (over) {
            EndOverlay(
                title = when (s.matchResult) { 1 -> "🏆 YOU WIN!"; 2 -> "🏆 ${s.theirName.uppercase()} WINS!"; else -> "🤝 IT'S A DRAW!" },
                subtitle = "You ${s.myScore}  ·  ${s.theirName} ${s.theirScore}",
                win = s.matchResult == 1, isHost = engine.isHost, theirName = s.theirName,
                playAgainLabel = "Play again", colors = listOf(Color(0xFF0E8A56), Funk.Sun),
                onPlayAgain = onPlayAgain, onBackToGames = onBackToGames, onLeave = onLeave,
            )
        }
    }
}

/** The prize ladder: 10, 20 ... 60 and the trophy, lighting up as the row is cleared. */
@Composable
private fun Ladder(s: HiLoState) {
    Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(4.dp)) {
        for (k in 1..7) {
            val trophy = k == 7
            val won = if (trophy) s.result == "full" else s.guesses >= k
            val next = !trophy && s.step == "GUESS" && s.guesses + 1 == k
            Box(
                Modifier
                    .weight(1f)
                    .height(30.dp)
                    .clip(RoundedCornerShape(8.dp))
                    .background(
                        when {
                            won -> Brush.verticalGradient(listOf(Funk.Sun, Funk.Orange))
                            next -> Brush.verticalGradient(listOf(Color(0xFF2E7D5A), Color(0xFF1B5A40)))
                            else -> Brush.verticalGradient(listOf(Color(0x33000000), Color(0x55000000)))
                        },
                    )
                    .border(if (next) 2.dp else 1.dp, if (next) Funk.Lime else Color.White.copy(alpha = 0.3f), RoundedCornerShape(8.dp)),
                contentAlignment = Alignment.Center,
            ) {
                Text(
                    if (trophy) "🏆" else "${k * HiLoRules.STEP_POINTS}",
                    fontSize = 12.sp, fontWeight = FontWeight.Black, color = if (won) Color(0xFF3B2A00) else Color.White,
                )
            }
        }
    }
}

/** All seven cards in a row, so you can see how far you've got. */
@Composable
private fun CardRow(s: HiLoState, modifier: Modifier) {
    Row(modifier, horizontalArrangement = Arrangement.spacedBy(4.dp)) {
        for (i in 0 until HiLoRules.ROW_LENGTH) {
            PlayingCard(
                card = s.row.getOrElse(i) { -1 },
                modifier = Modifier.weight(1f).aspectRatio(0.68f),
                highlight = i == s.idx && s.step == "GUESS",
                small = true,
            )
        }
    }
}

/** The card you are guessing from, big. */
@Composable
private fun BigCard(s: HiLoState, modifier: Modifier) {
    BoxWithConstraints(modifier, contentAlignment = Alignment.Center) {
        val w: Dp = minOf(maxWidth, maxHeight * 0.68f)
        PlayingCard(
            card = s.row.getOrElse(s.idx) { -1 },
            modifier = Modifier.size(w, w / 0.68f),
            highlight = s.step == "GUESS",
            small = false,
        )
    }
}

@Composable
private fun Controls(s: HiLoState, canGuess: Boolean, engine: HiLoEngine, modifier: Modifier) {
    Column(modifier, verticalArrangement = Arrangement.spacedBy(8.dp), horizontalAlignment = Alignment.CenterHorizontally) {
        Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(12.dp)) {
            Column(Modifier.weight(1f), horizontalAlignment = Alignment.CenterHorizontally) {
                FunkyButton("⬆  HIGHER", { engine.guess(true) }, Modifier.fillMaxWidth(), listOf(Funk.Lime, Funk.Mint), enabled = canGuess, fontSize = 20.sp)
                if (s.pHigher >= 0) Text("${s.pHigher}%", fontSize = 13.sp, fontWeight = FontWeight.Bold, color = Funk.Lime)
            }
            Column(Modifier.weight(1f), horizontalAlignment = Alignment.CenterHorizontally) {
                FunkyButton("⬇  LOWER", { engine.guess(false) }, Modifier.fillMaxWidth(), listOf(Funk.Pink, Funk.Red), enabled = canGuess, fontSize = 20.sp)
                if (s.pLower >= 0) Text("${s.pLower}%", fontSize = 13.sp, fontWeight = FontWeight.Bold, color = Funk.Pink)
            }
        }
        FunkyButton(
            if (s.myChange) "🔄  Change card (1 left)" else "🔄  Change used",
            { engine.change() }, Modifier.fillMaxWidth(0.7f), listOf(Funk.Violet, Funk.Blue),
            enabled = canGuess && s.myChange, fontSize = 14.sp,
        )
    }
}

/** A playing card that flips over when it turns face up. */
@Composable
private fun PlayingCard(card: Int, modifier: Modifier, highlight: Boolean, small: Boolean) {
    val faceUp = card >= 0
    val rot by animateFloatAsState(if (faceUp) 0f else 180f, tween(450), label = "cardflip")
    val showFace = rot < 90f
    val shape = RoundedCornerShape(if (small) 6.dp else 14.dp)
    val ink = if (faceUp && HiLoRules.isRed(card)) Color(0xFFD32F2F) else Color(0xFF1A1A2E)
    Box(
        modifier
            .graphicsLayer {
                rotationY = rot
                cameraDistance = 16f * density
                if (highlight) {
                    scaleX = 1.04f
                    scaleY = 1.04f
                }
            }
            .clip(shape)
            .background(
                if (showFace) Brush.verticalGradient(listOf(Color.White, Color(0xFFEDEBFF)))
                else Brush.linearGradient(listOf(Funk.Violet, Funk.Pink, Funk.Violet)),
            )
            .border(if (highlight) 3.dp else 1.5.dp, if (highlight) Funk.Sun else Color.White.copy(alpha = 0.6f), shape),
        contentAlignment = Alignment.Center,
    ) {
        if (showFace && faceUp) {
            if (small) {
                Column(horizontalAlignment = Alignment.CenterHorizontally) {
                    Text(HiLoRules.rankLabel(card), fontSize = 15.sp, fontWeight = FontWeight.Black, color = ink)
                    Text(HiLoRules.suitSymbol(card), fontSize = 14.sp, color = ink)
                }
            } else {
                Box(Modifier.fillMaxSize().padding(10.dp)) {
                    Column(Modifier.align(Alignment.TopStart), horizontalAlignment = Alignment.CenterHorizontally) {
                        Text(HiLoRules.rankLabel(card), fontSize = 34.sp, fontWeight = FontWeight.Black, color = ink)
                        Text(HiLoRules.suitSymbol(card), fontSize = 28.sp, color = ink)
                    }
                    Text(HiLoRules.suitSymbol(card), Modifier.align(Alignment.Center), fontSize = 84.sp, color = ink)
                    Column(Modifier.align(Alignment.BottomEnd).graphicsLayer { rotationZ = 180f }, horizontalAlignment = Alignment.CenterHorizontally) {
                        Text(HiLoRules.rankLabel(card), fontSize = 34.sp, fontWeight = FontWeight.Black, color = ink)
                        Text(HiLoRules.suitSymbol(card), fontSize = 28.sp, color = ink)
                    }
                }
            }
        } else if (!showFace) {
            // the back of the card (un-mirrored, because the card is turned past 90 degrees)
            Text("?", Modifier.graphicsLayer { rotationY = 180f }, fontSize = if (small) 18.sp else 64.sp, fontWeight = FontWeight.Black, color = Color.White.copy(alpha = 0.85f))
        }
    }
}
