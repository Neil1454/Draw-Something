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
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
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
import androidx.compose.runtime.withFrameMillis
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.drawscope.DrawScope
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.drawscope.rotate
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.text.TextMeasurer
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.drawText
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.rememberTextMeasurer
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
import kotlinx.coroutines.delay
import kotlin.math.PI
import kotlin.math.abs
import kotlin.math.cos
import kotlin.math.min
import kotlin.math.sin

@Composable
fun DealNoDealHelp() {
    Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
        Text("The UK TV game, for two. 22 sealed boxes hold prizes from 1p up to £250,000.")
        Text("• First, each of you picks ONE box to keep. Nobody knows what is inside.")
        Text("• Then you take turns opening the other boxes. Each box comes up on the stage, shakes, and bursts open to show its prize. That prize is out of the game, so it can't be in either of your boxes.")
        Text("• After each round the phone rings: it's the Banker! Pick up to hear his offer for each of you. Secretly choose DEAL (take the money and leave the game) or NO DEAL (play on). Both answers are shown together. Take a deal and your own box is opened so you can see what you gave up.")
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

/** Something to show in the middle of the stage, one at a time. */
private class RevealItem(
    /** open, pick, deal, nodeal, mybox, theirbox */
    val kind: String,
    val box: Int,
    val value: Long,
    /** 1 me, 2 them, 0 nobody in particular */
    val by: Int,
    val amount: Long = 0L,
)

@Composable
fun DealNoDealScreen(s: DndState, engine: DealNoDealEngine, onPlayAgain: () -> Unit, onBackToGames: () -> Unit, onLeave: () -> Unit) {
    val pulse by rememberInfiniteTransition(label = "dnd").animateFloat(0f, 1f, infiniteRepeatable(tween(800), RepeatMode.Reverse), label = "p")
    val queue = remember { mutableStateListOf<RevealItem>() }
    var current by remember { mutableStateOf<RevealItem?>(null) }
    val firstMoves = remember { s.moves }
    var finalQueued by remember { mutableStateOf(false) }
    val them = engine.theirName
    val frame = remember { mutableLongStateOf(0L) }
    LaunchedEffect(Unit) { while (true) withFrameMillis { frame.longValue = it } }
    // a quiet, tense game-show tune under the whole game
    androidx.compose.runtime.DisposableEffect(Unit) {
        Sfx.startMusic(Sfx.MusicTrack.DEAL_OR_NO_DEAL)
        onDispose { Sfx.stopMusic() }
    }

    // each happening at the table is queued up and shown in the middle of the stage, one after another
    LaunchedEffect(s.moves) {
        if (s.moves <= firstMoves) return@LaunchedEffect
        when (s.evType) {
            "open" -> queue.add(RevealItem("open", s.evBox, s.evValue, s.evBy))
            "pick" -> queue.add(RevealItem("pick", s.evBox, -1L, s.evBy))
            "deal" -> queue.add(RevealItem("deal", s.evBox, s.evValue, s.evBy, if (s.evBy == 1) s.myDealAmount else s.theirDealAmount))
            "nodeal" -> queue.add(RevealItem("nodeal", 0, -1L, s.evBy))
            "swap", "keep" -> queue.add(RevealItem(s.evType, 0, -1L, 0))
        }
    }
    LaunchedEffect(s.stage) {
        if (s.stage == "REVEAL" && !finalQueued) {
            finalQueued = true
            if (!s.myDealt && s.myBox != 0) queue.add(RevealItem("mybox", s.myBox, s.myBoxValue, 1))
            if (!s.theirDealt && s.theirBox != 0) queue.add(RevealItem("theirbox", s.theirBox, s.theirBoxValue, 2))
        }
    }
    LaunchedEffect(Unit) {
        while (true) {
            if (current == null && queue.isNotEmpty()) current = queue.removeAt(0)
            delay(50)
        }
    }
    val busy = current != null || queue.isNotEmpty()
    val unseen = s.unseen
    val unseenSet = unseen.toSet()

    fun who(by: Int) = if (by == 1) "You" else if (by == 2) them else "Both of you"
    val headline = when (s.stage) {
        "PICK" -> if (s.myBox == 0) "📦 Choose YOUR box to keep (tap one)" else "⏳ $them is choosing a box…"
        "OPEN" -> if (s.myTurn) "👉 Your turn: open a box (${s.opensLeft} left this round)" else if (s.myDealt) "You took the deal. Watching… (${s.opensLeft} left)" else "⏳ $them is opening a box… (${s.opensLeft} left)"
        "OFFER" -> if (s.myDealt) "📞 The Banker is talking to $them…" else if (s.myAnswered) "⏳ Waiting for $them to decide…" else "📞 The Banker is calling!"
        "SWAP" -> "🔁 Swap boxes?"
        else -> "🎁 The boxes open…"
    }

    Box(Modifier.fillMaxSize()) {
        StageBackdrop(frame.longValue, pulse)
        Column(Modifier.fillMaxSize().verticalScroll(rememberScrollState()).padding(horizontal = 10.dp, vertical = 10.dp), horizontalAlignment = Alignment.CenterHorizontally, verticalArrangement = Arrangement.spacedBy(8.dp)) {
            Text("DEAL OR NO DEAL", color = Gold, fontWeight = FontWeight.Black, fontSize = 22.sp, letterSpacing = 3.sp, style = TextStyle(shadow = androidx.compose.ui.graphics.Shadow(Color(0xFFB45309), Offset(0f, 4f), 8f)))
            Text("Round ${s.round} of ${s.rounds}", color = Color.White.copy(alpha = 0.7f), fontSize = 12.sp)
            Text(headline, color = Color.White, fontWeight = FontWeight.Bold, fontSize = 15.sp, textAlign = TextAlign.Center)

            // the two prize boards, lit up like the studio's
            Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                for (half in listOf(DndRules.PRIZES.toList().subList(0, 11), DndRules.PRIZES.toList().subList(11, 22))) {
                    Column(Modifier.weight(1f).clip(RoundedCornerShape(12.dp)).background(Color(0xFF0B1030)).border(2.dp, Gold.copy(alpha = 0.55f), RoundedCornerShape(12.dp)).padding(5.dp), verticalArrangement = Arrangement.spacedBy(3.dp)) {
                        for (v in half) PrizePlaque(v, v in unseenSet, pulse)
                    }
                }
            }

            if (s.stage == "SWAP") SwapPanel(s, engine, them)
            if (s.stage == "OFFER" && (s.myDealt || s.myAnswered)) WaitingPanel(s, them)

            // the boxes
            Text("YOUR BOX: ${if (s.myBox == 0) "–" else s.myBox.toString()}     ${them.uppercase()}'S BOX: ${if (s.theirBox == 0) "–" else s.theirBox.toString()}", color = Color.White.copy(alpha = 0.75f), fontSize = 11.sp, fontWeight = FontWeight.Bold, letterSpacing = 1.sp)
            Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
                for (row in s.boxes.chunked(6)) {
                    Row(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                        for (b in row) CaseCell(b, s, pulse, !busy) {
                            when {
                                s.phase != GPhase.PLAYING || busy -> Unit
                                s.stage == "PICK" && s.myBox == 0 && b.number != s.theirBox -> engine.pick(b.number)
                                s.stage == "OPEN" && s.myTurn && b.status == 0 -> engine.open(b.number)
                            }
                        }
                    }
                }
            }
            if (s.myDealt || s.theirDealt) Text(
                (if (s.myDealt) "You banked ${DndRules.money(s.myDealAmount)}.  " else "") + (if (s.theirDealt) "$them banked ${DndRules.money(s.theirDealAmount)}." else ""),
                color = Gold, fontSize = 13.sp, fontWeight = FontWeight.Bold,
            )
        }

        // the Banker's phone call
        if (s.stage == "OFFER" && !s.myDealt && !s.myAnswered && !busy && s.phase == GPhase.PLAYING) BankerCall(s, engine, them, frame.longValue)

        if (s.phase == GPhase.OVER && !busy) {
            EndOverlay(
                title = when (s.result) { 1 -> "🏆 YOU WIN!"; 2 -> "🏆 ${them.uppercase()} WINS!"; else -> "🤝 IT'S A DRAW!" },
                subtitle = "You ${DndRules.money(s.myFinal)}  ·  $them ${DndRules.money(s.theirFinal)}",
                win = s.result == 1, isHost = engine.isHost, theirName = them,
                playAgainLabel = "Play again", colors = listOf(Color(0xFF1B1464), Color(0xFFFFD23F)),
                onPlayAgain = onPlayAgain, onBackToGames = onBackToGames, onLeave = onLeave,
                how = (if (s.myDealt) "You dealt for ${DndRules.money(s.myDealAmount)}; your box held ${DndRules.money(s.myBoxValue)}. " else "Your box held ${DndRules.money(s.myBoxValue)}. ") +
                    (if (s.theirDealt) "$them dealt for ${DndRules.money(s.theirDealAmount)}; their box held ${DndRules.money(s.theirBoxValue)}." else "$them's box held ${DndRules.money(s.theirBoxValue)}.") +
                    if (s.swapped) " (You swapped boxes.)" else "",
                revealMs = 1500L,
            )
        }

        // the box on stage, in the middle of the screen
        current?.let { item -> RevealStage(item, them, frame.longValue) { current = null } }
    }
}

// ============================================================ the stage

@Composable
private fun StageBackdrop(nowMs: Long, pulse: Float) {
    Canvas(Modifier.fillMaxSize()) {
        drawRect(Brush.verticalGradient(listOf(Color(0xFF050818), Color(0xFF14104A), Color(0xFF3B1470))))
        // searchlight beams sweeping the ceiling
        val t = nowMs / 1000f
        for (k in 0 until 3) {
            val x = size.width * (0.2f + 0.3f * k)
            val sway = sin(t * (0.7f + 0.2f * k) + k) * size.width * 0.18f
            val p = Path().apply {
                moveTo(x - 10f, 0f); lineTo(x + 10f, 0f); lineTo(x + sway + size.width * 0.14f, size.height * 0.75f); lineTo(x + sway - size.width * 0.14f, size.height * 0.75f); close()
            }
            drawPath(p, Brush.verticalGradient(listOf(Color.White.copy(alpha = 0.10f), Color.Transparent)))
        }
        // chasing lights round the edge of the screen
        val n = 26
        for (i in 0 until n) {
            val on = ((i + (nowMs / 140L).toInt()) % 3 == 0)
            val y = size.height * (i + 0.5f) / n
            val c = if (on) Gold else Color(0xFF7C3AED)
            drawCircle(c.copy(alpha = if (on) 0.9f else 0.35f), 4f, Offset(6f, y))
            drawCircle(c.copy(alpha = if (on) 0.9f else 0.35f), 4f, Offset(size.width - 6f, y))
        }
        drawRect(Brush.verticalGradient(listOf(Color.Transparent, Color(0xFFFFD23F).copy(alpha = 0.06f + 0.04f * pulse))), Offset(0f, size.height * 0.7f), Size(size.width, size.height * 0.3f))
    }
}

@Composable
private fun PrizePlaque(v: Long, live: Boolean, pulse: Float) {
    val col = if (isHigh(v)) Red else Blue
    Box(
        Modifier.fillMaxWidth().clip(RoundedCornerShape(7.dp))
            .background(if (live) Brush.verticalGradient(listOf(col, Color(col.red * 0.55f, col.green * 0.55f, col.blue * 0.55f))) else Brush.verticalGradient(listOf(Color(0xFF1F2937), Color(0xFF111827))))
            .border(1.2.dp, if (live) Color.White.copy(alpha = 0.55f) else Color.White.copy(alpha = 0.08f), RoundedCornerShape(7.dp))
            .padding(vertical = 3.dp),
        contentAlignment = Alignment.Center,
    ) {
        Text(
            DndRules.money(v), color = if (live) Color.White else Color.White.copy(alpha = 0.18f), fontWeight = FontWeight.Black, fontSize = 12.sp, maxLines = 1,
            style = if (live && v >= 25_000_000L) TextStyle(shadow = androidx.compose.ui.graphics.Shadow(Gold.copy(alpha = 0.5f + 0.5f * pulse), Offset.Zero, 14f)) else TextStyle.Default,
        )
    }
}

@Composable
private fun SwapPanel(s: DndState, engine: DealNoDealEngine, them: String) {
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

@Composable
private fun WaitingPanel(s: DndState, them: String) {
    Column(
        Modifier.fillMaxWidth().clip(RoundedCornerShape(16.dp)).background(Color(0xFF0F172A)).border(2.dp, Gold.copy(alpha = 0.6f), RoundedCornerShape(16.dp)).padding(12.dp),
        horizontalAlignment = Alignment.CenterHorizontally, verticalArrangement = Arrangement.spacedBy(4.dp),
    ) {
        if (s.myDealt) Text("You dealt for ${DndRules.money(s.myDealAmount)}", color = Gold, fontWeight = FontWeight.Black, fontSize = 18.sp)
        else Text("Your answer is locked in", color = Gold, fontWeight = FontWeight.Black, fontSize = 18.sp)
        Text("Waiting for $them to decide…  (both answers are revealed together)", color = Color.White.copy(alpha = 0.8f), fontSize = 12.sp, textAlign = TextAlign.Center)
    }
}

// ============================================================ the boxes on the board

@Composable
private fun CaseCell(b: DndBox, s: DndState, pulse: Float, enabled: Boolean, onClick: () -> Unit) {
    val mine = b.status == 2
    val theirs = b.status == 3
    val open = b.status == 1 || b.value >= 0L
    val tappable = enabled && ((s.stage == "PICK" && s.myBox == 0 && b.status == 0 && b.number != s.theirBox) || (s.stage == "OPEN" && s.myTurn && b.status == 0))
    val measurer = rememberTextMeasurer()
    Canvas(
        Modifier.size(width = 50.dp, height = 58.dp).clip(RoundedCornerShape(8.dp)).clickable(enabled = tappable, onClick = onClick),
    ) {
        val w = size.width; val h = size.height
        if (open && !mine && !theirs) {
            // a box that has been opened: dark, with its prize
            val col = if (isHigh(b.value)) Red else Blue
            drawRoundRect(col.copy(alpha = 0.30f), Offset.Zero, size, CornerRadius(10f))
            drawRoundRect(Color.Black.copy(alpha = 0.45f), Offset(2f, 2f), Size(w - 4f, h - 4f), CornerRadius(8f))
            drawRoundRect(col.copy(alpha = 0.65f), Offset.Zero, size, CornerRadius(10f), style = Stroke(2f))
            val t = measurer.measure(DndRules.money(b.value), TextStyle(fontSize = 10.sp, color = Color.White.copy(alpha = 0.9f), fontWeight = FontWeight.Black))
            drawText(t, topLeft = Offset((w - t.size.width) / 2f, h * 0.52f))
            val nn = measurer.measure("${b.number}", TextStyle(fontSize = 15.sp, color = Gold, fontWeight = FontWeight.Black))
            drawText(nn, topLeft = Offset((w - nn.size.width) / 2f, h * 0.10f))
            return@Canvas
        }
        drawCase(Offset(w / 2f, h / 2f + 2f), w * 0.92f, h * 0.84f, 0f, if (mine) MineCol else if (theirs) TheirCol else null)
        // the number plate
        val plate = Size(w * 0.44f, h * 0.36f)
        val pc = Offset(w / 2f - plate.width / 2f, h * 0.38f)
        drawRoundRect(Color(0xFFFEF3C7), pc, plate, CornerRadius(5f))
        drawRoundRect(Color(0xFF92400E), pc, plate, CornerRadius(5f), style = Stroke(1.5f))
        val num = measurer.measure("${b.number}", TextStyle(fontSize = (if (mine || theirs) 13 else 15).sp, color = Color(0xFF111827), fontWeight = FontWeight.Black))
        drawText(num, topLeft = Offset(pc.x + (plate.width - num.size.width) / 2f, pc.y + (plate.height - num.size.height) / 2f))
        if (mine || theirs) {
            val tag = measurer.measure(if (mine) "YOU" else "THEM", TextStyle(fontSize = 8.sp, color = Color.White, fontWeight = FontWeight.Black))
            drawRoundRect(if (mine) MineCol else TheirCol, Offset(w / 2f - tag.size.width / 2f - 4f, h - tag.size.height - 3f), Size(tag.size.width + 8f, tag.size.height + 2f), CornerRadius(4f))
            drawText(tag, topLeft = Offset(w / 2f - tag.size.width / 2f, h - tag.size.height - 2f))
        }
        if (tappable) drawRoundRect(Color.White.copy(alpha = 0.35f + 0.45f * pulse), Offset.Zero, size, CornerRadius(10f), style = Stroke(3f))
    }
}

/** A suitcase-style box seen from the front, with its lid shut ([lid] = 0) or swung open ([lid] up to 1), centred on [c]. */
private fun DrawScope.drawCase(c: Offset, w: Float, h: Float, lid: Float, accent: Color?) {
    val left = c.x - w / 2f
    val top = c.y - h / 2f
    val bodyTop = top + h * 0.20f
    val shape = CornerRadius(w * 0.10f)
    // the body
    drawRoundRect(Color.Black.copy(alpha = 0.35f), Offset(left + 2f, bodyTop + 4f), Size(w, h * 0.80f), shape)
    drawRoundRect(Brush.verticalGradient(listOf(Color(0xFFEF4444), Color(0xFF991B1B), Color(0xFF450A0A)), bodyTop, bodyTop + h * 0.80f), Offset(left, bodyTop), Size(w, h * 0.80f), shape)
    drawRoundRect(Color.White.copy(alpha = 0.22f), Offset(left + w * 0.08f, bodyTop + h * 0.04f), Size(w * 0.84f, h * 0.08f), CornerRadius(4f))
    drawRoundRect(accent ?: Color(0xFFFFD23F), Offset(left, bodyTop), Size(w, h * 0.80f), shape, style = Stroke(if (accent != null) 4f else 2.4f))
    // the metal corners and the latch
    for (cx in floatArrayOf(left + w * 0.10f, left + w * 0.90f)) {
        drawCircle(Color(0xFFFDE68A), w * 0.035f, Offset(cx, bodyTop + h * 0.12f)); drawCircle(Color(0xFFFDE68A), w * 0.035f, Offset(cx, bodyTop + h * 0.72f))
    }
    drawRoundRect(Color(0xFFFDE68A), Offset(c.x - w * 0.09f, bodyTop - 2f), Size(w * 0.18f, h * 0.12f), CornerRadius(3f))
    // the lid hinges up from the back edge
    rotate(-110f * lid, Offset(left + w * 0.04f, bodyTop)) {
        drawRoundRect(Brush.verticalGradient(listOf(Color(0xFFF87171), Color(0xFFB91C1C)), top, bodyTop), Offset(left, top), Size(w, h * 0.22f), CornerRadius(w * 0.08f))
        drawRoundRect(accent ?: Color(0xFFFFD23F), Offset(left, top), Size(w, h * 0.22f), CornerRadius(w * 0.08f), style = Stroke(2.2f))
        drawRoundRect(Color.White.copy(alpha = 0.3f), Offset(left + w * 0.06f, top + h * 0.03f), Size(w * 0.88f, h * 0.04f), CornerRadius(3f))
    }
}

// ============================================================ the box on stage

/**
 * One happening shown big in the middle of the screen, like the studio: the box slides up, shakes with a drum roll, the lid flies open in a burst
 * of light and the prize card rises out and counts up. Tap to skip ahead.
 */
@Composable
private fun RevealStage(item: RevealItem, them: String, frameMs: Long, onDone: () -> Unit) {
    val total = when (item.kind) { "pick" -> 1900L; "nodeal" -> 1700L; "swap", "keep" -> 1800L; else -> 5200L }
    var ms by remember(item) { mutableLongStateOf(0L) }
    var skip by remember(item) { mutableStateOf(false) }
    val measurer = rememberTextMeasurer()
    // the timeline, with a sound at each beat
    LaunchedEffect(item) {
        val start = withFrameMillis { it }
        var s1 = false; var s2 = false; var s3 = false; var s4 = false
        when (item.kind) {
            "open", "deal", "mybox", "theirbox" -> { Sfx.play(Sound.KNOCK); delay(260); Sfx.play(Sound.BOX_CREAK) }
            "pick" -> Sfx.play(Sound.KNOCK)
            "nodeal" -> { Sfx.play(Sound.BANKER); Sfx.play(Sound.CHEER) }
            "swap" -> Sfx.play(Sound.WHOOSH)
            else -> Sfx.play(Sound.TAP)
        }
        val tiny = item.value in 0..1000L
        while (true) {
            val now = withFrameMillis { it } - start
            ms = if (skip) maxOf(now, total - 400L) else now
            val box = item.kind in setOf("open", "deal", "mybox", "theirbox")
            if (box && !s1 && ms >= 500L) { s1 = true; Sfx.play(Sound.DRUMROLL) }
            if (box && !s2 && ms >= 1550L) { s2 = true; Sfx.play(Sound.BOX_OPEN) }
            if (box && !s3 && ms >= 1750L) { s3 = true; Sfx.play(if (isHigh(item.value)) Sound.GASP else Sound.SPARKLE) }
            if (box && !s4 && ms >= 2050L) {
                s4 = true
                when {
                    item.kind == "deal" -> { Sfx.play(Sound.CASH); Sfx.play(Sound.APPLAUSE) }
                    isHigh(item.value) -> Sfx.play(Sound.GROAN)
                    tiny -> Sfx.play(Sound.CHEER)
                    else -> Sfx.play(Sound.APPLAUSE)
                }
            }
            if (ms >= total) break
        }
        onDone()
    }
    val isBox = item.kind in setOf("open", "deal", "mybox", "theirbox")
    val high = isHigh(item.value)
    val glow = if (high) Red else Blue
    Box(Modifier.fillMaxSize().pointerInput(item) { detectTapGestures { skip = true } }) {
        Canvas(Modifier.fillMaxSize()) {
            val fade = when {
                ms < 250L -> ms / 250f
                ms > total - 350L -> ((total - ms) / 350f).coerceIn(0f, 1f)
                else -> 1f
            }
            drawRect(Color(0xFF020617).copy(alpha = 0.84f * fade))
            val cx = size.width / 2f
            val cy = size.height * 0.46f
            val bw = min(size.width * 0.58f, size.height * 0.34f)
            val bh = bw * 0.82f
            if (isBox) {
                val appear = easeOutBack((ms / 450f).coerceIn(0f, 1f))
                val shaking = ms in 500L..1550L
                val shake = if (shaking) sin(ms * 0.09f) * (4f + (ms - 500L) / 60f) else 0f
                val lid = ((ms - 1550L) / 380f).coerceIn(0f, 1f)
                val rise = ((ms - 1900L) / 700f).coerceIn(0f, 1f)
                val scale = appear * (1f + 0.04f * sin(ms * 0.02f).coerceAtLeast(0f) * (if (shaking) 1f else 0f))
                // the burst of light behind the box
                if (lid > 0f) {
                    val rays = 14
                    for (k in 0 until rays) {
                        val a = (k * 2f * PI.toFloat() / rays) + ms / 1800f
                        val len = size.width * (0.35f + 0.5f * lid)
                        val p = Path().apply {
                            moveTo(cx, cy - bh * 0.2f)
                            lineTo(cx + cos(a - 0.08f) * len, cy - bh * 0.2f + sin(a - 0.08f) * len)
                            lineTo(cx + cos(a + 0.08f) * len, cy - bh * 0.2f + sin(a + 0.08f) * len)
                            close()
                        }
                        drawPath(p, glow.copy(alpha = 0.20f * lid * fade))
                    }
                    drawCircle(Brush.radialGradient(listOf(glow.copy(alpha = 0.75f * lid), Color.Transparent), Offset(cx, cy - bh * 0.1f), bw * 1.3f), bw * 1.3f, Offset(cx, cy - bh * 0.1f))
                }
                // a spotlight on the stage floor
                drawOval(Brush.radialGradient(listOf(Color.White.copy(alpha = 0.22f * fade), Color.Transparent), Offset(cx, cy + bh * 0.6f), bw), Offset(cx - bw, cy + bh * 0.3f), Size(bw * 2f, bh * 0.6f))
                val accent = when (item.by) { 1 -> MineCol; 2 -> TheirCol; else -> null }
                // the prize card rises out of the open box
                if (rise > 0f) {
                    val cardW = bw * 0.9f; val cardH = bh * 0.5f
                    val cardY = cy - bh * 0.1f - rise * bh * 0.95f
                    drawRoundRect(Brush.verticalGradient(listOf(Color.White, Color(0xFFFEF3C7)), cardY, cardY + cardH), Offset(cx - cardW / 2f + shake, cardY), Size(cardW, cardH), CornerRadius(14f))
                    drawRoundRect(Gold, Offset(cx - cardW / 2f + shake, cardY), Size(cardW, cardH), CornerRadius(14f), style = Stroke(4f))
                }
                drawCase(Offset(cx + shake, cy), bw * scale, bh * scale, lid, accent)
                // the box's number on its plate
                if (lid < 0.6f) {
                    val plate = Size(bw * scale * 0.40f, bh * scale * 0.34f)
                    val pc = Offset(cx + shake - plate.width / 2f, cy - bh * scale * 0.5f + bh * scale * 0.42f)
                    drawRoundRect(Color(0xFFFEF3C7), pc, plate, CornerRadius(8f)); drawRoundRect(Color(0xFF92400E), pc, plate, CornerRadius(8f), style = Stroke(3f))
                    val num = measurer.measure("${item.box}", TextStyle(fontSize = (bw * 0.20f).toSp(), color = Color(0xFF111827), fontWeight = FontWeight.Black))
                    drawText(num, topLeft = Offset(pc.x + (plate.width - num.size.width) / 2f, pc.y + (plate.height - num.size.height) / 2f))
                }
                // the box's number, large, so you always know which one it is
                val tagTxt = measurer.measure("BOX ${item.box}", TextStyle(fontSize = (bw * 0.16f).toSp(), color = Gold, fontWeight = FontWeight.Black))
                drawText(tagTxt, topLeft = Offset(cx - tagTxt.size.width / 2f, cy - bh * 0.5f - tagTxt.size.height - 8f - (if (rise > 0f) rise * bh * 0.55f else 0f)))
                // the prize, counting up on the card
                if (rise > 0.35f) {
                    val shown = (item.value * min(1f, (ms - 2100L) / 650f).coerceIn(0f, 1f)).toLong()
                    val txt = DndRules.money(if (ms >= 2750L) item.value else DndRules.nice(shown.coerceAtLeast(1L)).coerceAtMost(item.value))
                    val t = measurer.measure(txt, TextStyle(fontSize = (bw * 0.19f).toSp(), color = if (high) Color(0xFFB91C1C) else Color(0xFF1D4ED8), fontWeight = FontWeight.Black))
                    val cardY = cy - bh * 0.1f - rise * bh * 0.95f
                    drawText(t, topLeft = Offset(cx - t.size.width / 2f + shake, cardY + (bh * 0.5f - t.size.height) / 2f))
                }
                // coins and stars bursting out for a small prize, sparks for a big one
                if (lid > 0.2f) for (k in 0 until 26) {
                    val seed = k * 61.3f
                    val a = seed % (2f * PI.toFloat())
                    val tt = ((ms - 1500L) / 1400f + (k % 5) * 0.03f).coerceIn(0f, 1.2f)
                    val dist = size.width * 0.7f * tt * (0.5f + (k % 7) / 10f)
                    val px = cx + cos(a) * dist
                    val py = cy - bh * 0.2f + sin(a) * dist * 0.8f + tt * tt * size.height * 0.25f
                    drawCircle((if (high) Color(0xFFFCA5A5) else Gold).copy(alpha = (1f - tt / 1.2f).coerceIn(0f, 1f) * fade), 4f + (k % 3) * 2f, Offset(px, py))
                }
            } else {
                // a box being chosen, a "no deal", or a swap: a headline in lights
                val text = when (item.kind) {
                    "pick" -> if (item.by == 1) "YOU CHOSE\nBOX ${item.box}" else "${them.uppercase()} CHOSE\nBOX ${item.box}"
                    "nodeal" -> if (item.by == 1) "NO DEAL!" else "${them.uppercase()}:\nNO DEAL!"
                    "swap" -> "SWAPPED!"
                    else -> "NO SWAP"
                }
                val bounce = easeOutBack((ms / 400f).coerceIn(0f, 1f))
                if (item.kind == "pick") drawCase(Offset(cx, cy + bw * 0.4f), bw * 0.62f * bounce, bw * 0.5f * bounce, 0f, if (item.by == 1) MineCol else TheirCol)
                val t = measurer.measure(text, TextStyle(fontSize = (bw * 0.22f * bounce).coerceAtLeast(1f).toSp(), color = if (item.kind == "nodeal") Color(0xFFFCA5A5) else Gold, fontWeight = FontWeight.Black, textAlign = TextAlign.Center, lineHeight = (bw * 0.24f).toSp()))
                drawText(t, topLeft = Offset(cx - t.size.width / 2f, cy - bw * 0.35f - t.size.height / 2f))
            }
        }
        // the words under the box
        val caption = when (item.kind) {
            "open" -> if (item.by == 1) "You opened box ${item.box}…" else "$them opened box ${item.box}…"
            "mybox" -> "Your box, number ${item.box}…"
            "theirbox" -> "$them's box, number ${item.box}…"
            "deal" -> if (item.by == 1) "You took the deal for ${DndRules.money(item.amount)}. Your box held…" else "$them took the deal for ${DndRules.money(item.amount)}. Their box held…"
            else -> ""
        }
        if (caption.isNotEmpty()) Text(caption, color = Color.White, fontWeight = FontWeight.Bold, fontSize = 16.sp, textAlign = TextAlign.Center, modifier = Modifier.align(Alignment.BottomCenter).padding(bottom = 90.dp, start = 24.dp, end = 24.dp))
        if (isBox && ms > 2300L) {
            val v = item.value
            val (line, colour) = when {
                item.kind == "mybox" -> "YOUR BOX HELD ${DndRules.money(v)}!" to Gold
                item.kind == "theirbox" -> "${them.uppercase()}'S BOX HELD ${DndRules.money(v)}!" to Gold
                item.kind == "deal" -> "DEAL!" to Color(0xFF86EFAC)
                v >= 1_000_000L -> "OUCH! A BIG ONE GONE!" to Color(0xFFFCA5A5)
                v >= 100_000L -> "OH NO! A RED ONE!" to Color(0xFFFCA5A5)
                v >= 5_000L -> "PHEW! A BLUE ONE!" to Color(0xFF93C5FD)
                else -> "BRILLIANT! A TINY ONE OUT!" to Color(0xFF86EFAC)
            }
            val pop = min(1f, (ms - 2300L) / 260f)
            val shake = if (isHigh(v) && ms < 3100L) sin(ms * 0.08f) * 7f else 0f
            Text(
                line, color = colour, fontWeight = FontWeight.Black, fontSize = (30f * (0.7f + 0.3f * pop)).sp, textAlign = TextAlign.Center, lineHeight = 34.sp,
                modifier = Modifier.align(Alignment.BottomCenter).padding(bottom = 128.dp, start = 18.dp, end = 18.dp).graphicsLayer { translationX = shake; scaleX = pop; scaleY = pop },
            )
            if (item.kind == "open") Text(
                if (isHigh(v)) "That's a prize the Banker was hoping you'd keep out of play…" else "That prize is out of the game, so it isn't in either of your boxes.",
                color = Color.White.copy(alpha = 0.85f), fontSize = 13.sp, textAlign = TextAlign.Center, modifier = Modifier.align(Alignment.BottomCenter).padding(bottom = 76.dp, start = 24.dp, end = 24.dp),
            )
        }
        Text("tap to skip", color = Color.White.copy(alpha = 0.4f), fontSize = 11.sp, modifier = Modifier.align(Alignment.BottomCenter).padding(bottom = 16.dp))
    }
}

private fun easeOutBack(x: Float): Float {
    val c1 = 1.70158f; val c3 = c1 + 1f
    return 1f + c3 * Math.pow((x - 1f).toDouble(), 3.0).toFloat() + c1 * Math.pow((x - 1f).toDouble(), 2.0).toFloat()
}

// ============================================================ the Banker's call

/**
 * The phone rings (and keeps ringing) until you pick it up. Then the Banker's menacing little tune plays, a drum roll builds, and his offer lights up.
 * Only then can you choose DEAL or NO DEAL.
 */
@Composable
private fun BankerCall(s: DndState, engine: DealNoDealEngine, them: String, frameMs: Long) {
    var step by remember(s.round) { mutableIntStateOf(0) }
    val measurer = rememberTextMeasurer()
    // ring, ring…
    LaunchedEffect(s.round, step) {
        if (step == 0) { while (true) { Sfx.play(Sound.RING); delay(1500) } }
        // you lift the receiver and the Banker talks (a muffled voice down a bad line), then a drum roll and his offer
        if (step == 1) { Sfx.play(Sound.PICKUP); delay(350); Sfx.play(Sound.VOICE); delay(2400); step = 2 }
        if (step == 2) { Sfx.play(Sound.DRUMROLL); delay(1150); Sfx.play(Sound.BANKER); step = 3 }
    }
    val unseen = s.unseen
    val average = if (unseen.isEmpty()) 0L else unseen.average().toLong()
    Box(Modifier.fillMaxSize().background(Color(0xF2020617)).pointerInput(Unit) { detectTapGestures { } }) {
        Canvas(Modifier.fillMaxSize()) {
            val t = frameMs / 1000f
            val cx = size.width / 2f
            // the Banker: a silhouette in a pool of light
            drawCircle(Brush.radialGradient(listOf(Color(0xFFFFD23F).copy(alpha = 0.22f + 0.05f * sin(t * 2f)), Color.Transparent), Offset(cx, size.height * 0.22f), size.width * 0.7f), size.width * 0.7f, Offset(cx, size.height * 0.22f))
            val hy = size.height * 0.15f
            val sh = Path().apply {
                moveTo(cx - size.width * 0.28f, size.height * 0.34f); quadraticBezierTo(cx - size.width * 0.26f, size.height * 0.22f, cx - size.width * 0.09f, size.height * 0.21f)
                lineTo(cx + size.width * 0.09f, size.height * 0.21f); quadraticBezierTo(cx + size.width * 0.26f, size.height * 0.22f, cx + size.width * 0.28f, size.height * 0.34f); close()
            }
            drawPath(sh, Color(0xFF0B0F1E)); drawPath(sh, Color(0xFF334155), style = Stroke(2f))
            drawCircle(Color(0xFF0B0F1E), size.width * 0.085f, Offset(cx, hy)); drawCircle(Color(0xFF334155), size.width * 0.085f, Offset(cx, hy), style = Stroke(2f))
            // a tie, and eyes that glint
            val tie = Path().apply { moveTo(cx - 8f, size.height * 0.215f); lineTo(cx + 8f, size.height * 0.215f); lineTo(cx + 12f, size.height * 0.30f); lineTo(cx, size.height * 0.315f); lineTo(cx - 12f, size.height * 0.30f); close() }
            drawPath(tie, Color(0xFFB91C1C))
            if (step >= 2) { drawCircle(Gold.copy(alpha = 0.5f + 0.5f * sin(t * 5f)), 3.2f, Offset(cx - size.width * 0.03f, hy)); drawCircle(Gold.copy(alpha = 0.5f + 0.5f * sin(t * 5f)), 3.2f, Offset(cx + size.width * 0.03f, hy)) }
            // the desk and the old-fashioned phone
            val deskY = size.height * 0.52f
            drawRect(Brush.verticalGradient(listOf(Color(0xFF451A03), Color(0xFF1C0A02)), deskY, size.height), Offset(0f, deskY), Size(size.width, size.height - deskY))
            val ringing = step == 0
            val shake = if (ringing) sin(frameMs * 0.045f) * 3f else 0f
            rotate(shake, Offset(cx, deskY)) {
                val pw = size.width * 0.5f
                drawRoundRect(Color(0xFF111827), Offset(cx - pw / 2f, deskY - pw * 0.30f), Size(pw, pw * 0.32f), CornerRadius(26f))
                drawRoundRect(Color(0xFFDC2626), Offset(cx - pw / 2f + 5f, deskY - pw * 0.30f + 5f), Size(pw - 10f, pw * 0.32f - 10f), CornerRadius(22f))
                drawCircle(Color(0xFF111827), pw * 0.10f, Offset(cx, deskY - pw * 0.13f)); drawCircle(Color(0xFFFDE68A), pw * 0.06f, Offset(cx, deskY - pw * 0.13f))
                for (k in 0 until 8) { val a = k * PI.toFloat() / 4f; drawCircle(Color.White, 3.2f, Offset(cx + cos(a) * pw * 0.08f, deskY - pw * 0.13f + sin(a) * pw * 0.08f)) }
                // the handset on top
                val hs = Path().apply { moveTo(cx - pw * 0.45f, deskY - pw * 0.34f); quadraticBezierTo(cx, deskY - pw * 0.55f, cx + pw * 0.45f, deskY - pw * 0.34f) }
                drawPath(hs, Color(0xFF0F172A), style = Stroke(pw * 0.09f, cap = StrokeCap.Round))
                drawPath(hs, Color(0xFFDC2626), style = Stroke(pw * 0.055f, cap = StrokeCap.Round))
            }
            if (ringing) for (k in 0 until 3) {
                val ph = ((frameMs / 900f) + k / 3f) % 1f
                drawArc(Gold.copy(alpha = (1f - ph) * 0.8f), 210f, 120f, false, Offset(cx - size.width * (0.18f + 0.25f * ph), deskY - size.width * (0.40f + 0.25f * ph)), Size(size.width * (0.36f + 0.5f * ph), size.width * (0.36f + 0.5f * ph)), style = Stroke(5f, cap = StrokeCap.Round))
            }
        }
        Column(Modifier.fillMaxSize().padding(horizontal = 18.dp), horizontalAlignment = Alignment.CenterHorizontally, verticalArrangement = Arrangement.Bottom) {
            when (step) {
                0 -> {
                    Text("☎️  THE BANKER IS CALLING…", color = Gold, fontWeight = FontWeight.Black, fontSize = 22.sp, textAlign = TextAlign.Center)
                    Text("Round ${s.round} of ${s.rounds}", color = Color.White.copy(alpha = 0.6f), fontSize = 12.sp, modifier = Modifier.padding(bottom = 12.dp))
                    FunkyButton("📞  PICK UP THE PHONE", { step = 1 }, Modifier.fillMaxWidth().padding(bottom = 36.dp), listOf(Color(0xFF16A34A), Color(0xFF0E9F6E)), fontSize = 20.sp)
                }
                1 -> {
                    val dots = ".".repeat(1 + ((frameMs / 300L) % 3).toInt())
                    Text("“Hello, it's the Banker$dots”", color = Color.White, fontWeight = FontWeight.Black, fontSize = 26.sp, textAlign = TextAlign.Center)
                    Text("🗣️  he's talking…", color = Color.White.copy(alpha = 0.6f), fontSize = 13.sp, modifier = Modifier.padding(bottom = 110.dp, top = 6.dp))
                }
                2 -> {
                    Text("The Banker's offer is…", color = Color.White, fontWeight = FontWeight.Bold, fontSize = 20.sp)
                    Text("🥁", fontSize = 40.sp, modifier = Modifier.padding(bottom = 90.dp))
                }
                else -> {
                    Text("The Banker offers you", color = Color.White.copy(alpha = 0.85f), fontSize = 14.sp)
                    val pulse = 0.5f + 0.5f * sin(frameMs / 160f)
                    Text(DndRules.money(s.myOffer), color = Gold, fontWeight = FontWeight.Black, fontSize = 52.sp, style = TextStyle(shadow = androidx.compose.ui.graphics.Shadow(Gold.copy(alpha = 0.4f + 0.4f * pulse), Offset.Zero, 28f)))
                    Text("$them is offered ${if (s.theirDealt) "(already dealt)" else DndRules.money(s.theirOffer)}", color = TheirCol, fontSize = 13.sp, fontWeight = FontWeight.Bold)
                    Text("The average of the prizes still in play: ${DndRules.money(average)}", color = Color.White.copy(alpha = 0.55f), fontSize = 11.sp, modifier = Modifier.padding(top = 2.dp, bottom = 10.dp))
                    Row(Modifier.fillMaxWidth().padding(bottom = 30.dp), horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                        FunkyButton("DEAL", { engine.answer(true) }, Modifier.weight(1f), listOf(Color(0xFF16A34A), Color(0xFF0E9F6E)), fontSize = 24.sp)
                        FunkyButton("NO DEAL", { engine.answer(false) }, Modifier.weight(1f), listOf(Color(0xFFDC2626), Color(0xFFEA580C)), fontSize = 24.sp)
                    }
                }
            }
        }
    }
}

internal fun DrawScope.dealIcon() {
    drawCase(Offset(50f, 56f), 70f, 62f, 0.35f, null)
    drawCircle(Color.White.copy(alpha = 0.9f), 6f, Offset(32f, 22f)); drawCircle(Color.White.copy(alpha = 0.9f), 4f, Offset(70f, 26f)); drawCircle(Color(0xFFFFD23F), 5f, Offset(52f, 14f))
}
