package com.neil1454.drawsomething.ui

import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.animateIntAsState
import androidx.compose.animation.core.tween
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.IntrinsicSize
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.gestures.detectVerticalDragGestures
import androidx.compose.foundation.interaction.collectIsPressedAsState
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.input.pointer.pointerInput
import kotlinx.coroutines.launch
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableLongStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.runtime.withFrameMillis
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.StrokeJoin
import androidx.compose.ui.graphics.drawscope.DrawScope
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.drawscope.clipRect
import androidx.compose.ui.graphics.drawscope.withTransform
import androidx.compose.ui.text.TextMeasurer
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.drawText
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.rememberTextMeasurer
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.neil1454.drawsomething.core.GPhase
import com.neil1454.drawsomething.core.Sound
import com.neil1454.drawsomething.core.SlotRules
import com.neil1454.drawsomething.core.SlotsEngine
import com.neil1454.drawsomething.core.SlotsState
import com.neil1454.drawsomething.sound.Sfx
import kotlinx.coroutines.delay
import kotlin.math.PI
import kotlin.math.abs
import kotlin.math.cos
import kotlin.math.exp
import kotlin.math.floor
import kotlin.math.sin

@Composable
fun SlotsHelp() {
    Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
        Text("A five-reel slot machine with nine paylines, and you take turns on it. Most credits after all the spins wins.")
        Text("• Choose your bet per line (1, 2, 5 or 10). A spin costs nine lines' worth. Then pull the lever (tap SPIN).")
        Text("• Matching symbols from the left on a payline win. ⭐ WILD stands in for any symbol. Two cherries on the left pay a little too.")
        Text("• Three or more 💰 SCATTERS anywhere pay a prize and win FREE SPINS, where every win is doubled.")
        Text("• Five 7️⃣ (or five ⭐) on a line is the JACKPOT!")
        Text("• After a win, COLLECT it or GAMBLE it: guess red or black on a hidden card. Right and it doubles (up to three times in a row); wrong and you lose it.")
        Text("• Tap 📋 Paytable to see what every symbol pays.")
    }
}

private val Gold = Color(0xFFFFD23F)
private val LineColours = listOf(
    Color(0xFFFFD23F), Color(0xFF22D3EE), Color(0xFFF472B6), Color(0xFF86EFAC), Color(0xFFFB923C),
    Color(0xFFA78BFA), Color(0xFF60A5FA), Color(0xFFFDA4AF), Color(0xFF5EEAD4),
)
private const val PREVIEW = "LGOOBDDLGGOBBDL"

/** True when the first three reels already show two scatters: the last two reels then creep to a stop with a flashing frame, as the real machines do. */
private fun anticipates(grid: String): Boolean = grid.length == 15 && (0 until 9).count { grid[it] == '$' } >= 2

/** When each reel stops, in milliseconds after the lever is pulled. */
private fun reelStops(grid: String): LongArray {
    val st = LongArray(SlotRules.REELS) { 650L + it * 330L }
    if (anticipates(grid)) { st[3] += 700L; st[4] += 1700L }
    return st
}

@Composable
fun SlotsScreen(s: SlotsState, engine: SlotsEngine, onPlayAgain: () -> Unit, onBackToGames: () -> Unit, onLeave: () -> Unit) {
    val frame = remember { mutableLongStateOf(0L) }
    val spinStart = remember { mutableLongStateOf(-100_000L) }
    val seenSpin = remember { mutableIntStateOf(s.spinNo) }
    var done by remember { mutableStateOf(true) }
    var payDialog by remember { mutableStateOf(false) }
    var banner by remember { mutableStateOf("") }
    var stops by remember { mutableStateOf(reelStops(PREVIEW)) }
    val quickAt = remember { mutableLongStateOf(0L) }
    val leverPull = remember { androidx.compose.animation.core.Animatable(0f) }
    val resultAt = remember { mutableLongStateOf(0L) }
    val measurer = rememberTextMeasurer()
    val them = engine.theirName
    LaunchedEffect(Unit) { while (true) withFrameMillis { frame.longValue = it } }

    // each new spin: the lever, the whirring reels stopping one by one (a near-miss on the scatters makes the last ones creep), then the result
    LaunchedEffect(s.spinNo) {
        if (s.spinNo <= seenSpin.intValue) { seenSpin.intValue = s.spinNo; return@LaunchedEffect }
        seenSpin.intValue = s.spinNo
        done = false; banner = ""; quickAt.longValue = 0L
        val st = reelStops(s.grid)
        stops = st
        spinStart.longValue = frame.longValue
        Sfx.play(Sound.LEVER)
        launch { leverPull.animateTo(1f, tween(200)); leverPull.animateTo(0f, tween(520)) }
        var t = 0L
        var next = 0
        var teased = false
        val end = st.last() + 100L
        while (t < end && quickAt.longValue == 0L) {
            delay(55); t += 55
            if (next < SlotRules.REELS && t >= st[next]) { Sfx.play(Sound.STOP); next++ } else if (t % 110L == 0L) Sfx.play(Sound.REEL)
            if (!teased && next >= 3 && anticipates(s.grid) && t < st[4]) { teased = true; Sfx.play(Sound.SUSPENSE) }
        }
        if (quickAt.longValue != 0L) { Sfx.play(Sound.STOP); delay(450) }
        done = true
        resultAt.longValue = frame.longValue
        val bet = if (s.lastBet > 0) s.lastBet else 1
        when {
            s.jackpot -> { banner = "JACKPOT!"; Sfx.play(Sound.JACKPOT); delay(500); Sfx.play(Sound.CASH) }
            s.scatters >= 3 -> { banner = "FREE SPINS!"; Sfx.play(Sound.SPARKLE); delay(400); Sfx.play(Sound.WIN) }
            s.total >= bet * SlotRules.LINES * 8 -> { banner = "BIG WIN!"; Sfx.play(Sound.WIN); Sfx.play(Sound.CASH) }
            s.total >= bet * SlotRules.LINES * 2 -> { banner = "NICE WIN!"; Sfx.play(Sound.CASH) }
            s.total > 0 -> Sfx.play(Sound.COIN)
        }
        if (banner.isNotEmpty()) { delay(2400); banner = "" }
    }
    // a gamble's hidden card turns over
    LaunchedEffect(s.gambles, s.stage) { if (s.card != 0) Sfx.play(if (s.cardWon) Sound.GOOD else Sound.BAD) }
    val myCredits by animateIntAsState(s.myCredits, tween(700), label = "me")
    val theirCredits by animateIntAsState(s.theirCredits, tween(700), label = "them")
    // the win meter counts up, like the one on a real machine
    val shownWin by animateIntAsState(if (done && s.stage != "BET") (if (s.stage == "WIN") s.pending else s.total) else 0, tween(900), label = "win")

    val canPlay = s.phase == GPhase.PLAYING && s.myTurn && done
    val canSpin = canPlay && s.stage == "BET"
    val canCollect = canPlay && s.stage == "WIN"
    val free = s.freeSpins > 0
    val affordable = SlotRules.BETS.indices.filter { SlotRules.cost(SlotRules.BETS[it]) <= s.myCredits }
    val canGamble = canCollect && s.gambles < SlotRules.MAX_GAMBLES && s.pending > 0

    // ---- the pieces, arranged differently for portrait and landscape
    val sign: @Composable () -> Unit = {
        Box(Modifier.fillMaxWidth().height(40.dp).clip(RoundedCornerShape(14.dp)).background(Brush.horizontalGradient(listOf(Color(0xFF7F1D1D), Color(0xFFB91C1C), Color(0xFF7F1D1D)))).border(2.dp, Gold, RoundedCornerShape(14.dp)), contentAlignment = Alignment.Center) {
            Canvas(Modifier.fillMaxSize()) {
                val n = 22
                for (i in 0 until n) {
                    val on = (i + (frame.longValue / 150L).toInt()) % 2 == 0
                    val cx = size.width * (i + 0.5f) / n
                    drawCircle(if (on) Gold else Gold.copy(alpha = 0.25f), 3.4f, Offset(cx, 6f)); drawCircle(if (on) Gold else Gold.copy(alpha = 0.25f), 3.4f, Offset(cx, size.height - 6f))
                }
            }
            Text("🎰  LUCKY SEVENS  🎰", color = Gold, fontWeight = FontWeight.Black, fontSize = 18.sp, letterSpacing = 3.sp)
        }
    }
    val scores: @Composable (Boolean) -> Unit = { compact ->
        Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            Credit("YOU", myCredits, s.mySpins, s.myTurn && s.phase == GPhase.PLAYING, Color(0xFF22D3EE), Modifier.weight(1f), compact)
            Credit(them.uppercase(), theirCredits, s.theirSpins, !s.myTurn && s.phase == GPhase.PLAYING, Color(0xFFF472B6), Modifier.weight(1f), compact)
        }
    }
    /** The cabinet, drawn at exactly [w] x [h], with its lever beside it. */
    val machine: @Composable (androidx.compose.ui.unit.Dp, androidx.compose.ui.unit.Dp) -> Unit = { w, h ->
        Row(verticalAlignment = Alignment.CenterVertically) {
            Box(Modifier.size(w, h)) {
                Canvas(Modifier.fillMaxSize()) { drawMachine(s, frame.longValue, spinStart.longValue, done, measurer, stops, quickAt.longValue, if (done) frame.longValue - resultAt.longValue else 0L) }
                if (banner.isNotEmpty()) {
                    val big = banner == "JACKPOT!" || banner == "BIG WIN!"
                    val pop = 1f + 0.06f * sin(frame.longValue / 110f)
                    Text(
                        banner, color = Gold, fontWeight = FontWeight.Black, fontSize = if (big) 40.sp else 30.sp, textAlign = TextAlign.Center,
                        modifier = Modifier.align(Alignment.Center).graphicsLayer { scaleX = pop; scaleY = pop }.clip(RoundedCornerShape(20.dp)).background(Color(0xE60B0624)).border(3.dp, Gold, RoundedCornerShape(20.dp)).padding(horizontal = 24.dp, vertical = 12.dp),
                    )
                }
            }
            Lever(leverPull.value, canSpin, Modifier.width(46.dp).height(h)) { engine.spin() }
        }
    }
    val tray: @Composable () -> Unit = { CoinTray(s.myCredits, if (done && s.stage != "BET" && s.total > 0) frame.longValue - resultAt.longValue else -1L, frame) }
    val meters: @Composable (Boolean) -> Unit = { compact ->
        Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(6.dp)) {
            Led("CREDITS", "$myCredits", Modifier.weight(1.3f), compact = compact)
            Led("BET / LINE", "${s.bet}", Modifier.weight(1f), compact = compact)
            Led("TOTAL BET", "${SlotRules.cost(s.bet)}", Modifier.weight(1f), compact = compact)
            Led("WIN", "$shownWin", Modifier.weight(1f), win = done && s.total > 0 && s.stage != "BET", compact = compact)
        }
    }
    val status: @Composable () -> Unit = {
        Text(
            when {
                !done -> "🎰 Spinning…"
                s.stage == "WIN" && s.total > 0 && s.card == 0 -> "${if (s.lastBy == 1) "You" else them} won ${s.total} credits${if (s.lastFree) " (free spin: doubled)" else ""}!"
                s.stage == "WIN" -> "Prize on the table: ${s.pending}"
                s.stage == "LOST" -> "💥 The gamble lost!"
                s.spinNo == 0 -> "Place your bet and pull the lever!"
                s.total > 0 -> "${if (s.lastBy == 1) "You" else them} won ${s.total}"
                else -> "No win that time"
            } + if (s.phase == GPhase.PLAYING && !s.myTurn && done) "   ·   ⏳ $them's turn" else "",
            color = Gold, fontWeight = FontWeight.Bold, fontSize = 14.sp, textAlign = TextAlign.Center,
        )
    }
    val deck: @Composable (Boolean) -> Unit = { compact ->
        Column(verticalArrangement = Arrangement.spacedBy(if (compact) 5.dp else 8.dp), horizontalAlignment = Alignment.CenterHorizontally) {
            Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(8.dp), verticalAlignment = Alignment.CenterVertically) {
                GoldButton("BET\n${s.bet}", { if (affordable.isNotEmpty()) engine.setBet(affordable[(affordable.indexOf(s.betIndex) + 1).mod(affordable.size)]) }, Modifier.weight(1f), enabled = canSpin && !free && affordable.size > 1, compact = compact)
                GoldButton("MAX\nBET", { if (affordable.isNotEmpty()) { engine.setBet(affordable.last()); engine.spin() } }, Modifier.weight(1f), enabled = canSpin && !free && affordable.isNotEmpty(), compact = compact)
                // while the reels are spinning the SPIN button becomes STOP, for impatient players
                GoldButton(if (!done) "STOP" else if (free) "FREE\nSPIN" else "SPIN", { if (!done) { if (quickAt.longValue == 0L) quickAt.longValue = frame.longValue } else engine.spin() }, Modifier.weight(1.5f), enabled = canSpin || (!done && quickAt.longValue == 0L), big = true, red = true, lit = canSpin, compact = compact)
                GoldButton("COLLECT", { engine.collect() }, Modifier.weight(1.2f), enabled = canCollect, lit = canCollect, compact = compact)
            }
            Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                GoldButton("♥ GAMBLE RED  ×2", { engine.gamble(true) }, Modifier.weight(1f), enabled = canGamble, red = true, compact = compact)
                GoldButton("♠ GAMBLE BLACK  ×2", { engine.gamble(false) }, Modifier.weight(1f), enabled = canGamble, dark = true, compact = compact)
            }
            if (canGamble && !compact) Text("Gamble: guess the colour of the hidden card to double your win (${SlotRules.MAX_GAMBLES - s.gambles} left), or COLLECT it.", color = Color.White.copy(alpha = 0.75f), fontSize = 11.sp, textAlign = TextAlign.Center)
            if (s.card != 0 && s.cardWon && canCollect) Text("The card was ${if (s.card == 1) "RED ♥" else "BLACK ♠"}: doubled!", color = Color(0xFF86EFAC), fontWeight = FontWeight.Bold)
            if (s.freeSpins > 0 && s.phase == GPhase.PLAYING) Text("⭐ ${s.freeSpins} FREE SPINS LEFT (wins doubled)", color = Color(0xFF86EFAC), fontWeight = FontWeight.Black)
            TextButton(onClick = { payDialog = true }, contentPadding = androidx.compose.foundation.layout.PaddingValues(horizontal = 8.dp, vertical = 0.dp)) { Text("📋 Paytable", color = Gold, fontSize = if (compact) 12.sp else 14.sp) }
        }
    }

    BoxWithConstraints(Modifier.fillMaxSize()) {
        val screenW = maxWidth
        val screenH = maxHeight
        val landscape = screenW > screenH
        Canvas(Modifier.fillMaxSize()) {
            drawRect(Brush.verticalGradient(listOf(Color(0xFF1A0505), Color(0xFF4A0D14), Color(0xFF1A0505))))
            // velvet stripes
            var x = 0f
            while (x < size.width) { drawRect(Color.White.copy(alpha = 0.025f), Offset(x, 0f), Size(18f, size.height)); x += 56f }
            // the light spilling out of the machine
            drawCircle(Brush.radialGradient(listOf(Color(0xFFFFD23F).copy(alpha = 0.10f + 0.04f * sin(frame.longValue / 400f)), Color.Transparent), Offset(size.width * (if (landscape) 0.32f else 0.5f), size.height * 0.4f), size.minDimension * 0.9f), size.minDimension * 0.9f, Offset(size.width * (if (landscape) 0.32f else 0.5f), size.height * 0.4f))
        }
        if (!landscape) {
            Column(Modifier.fillMaxSize().verticalScroll(rememberScrollState()).padding(horizontal = 10.dp, vertical = 10.dp), horizontalAlignment = Alignment.CenterHorizontally, verticalArrangement = Arrangement.spacedBy(8.dp)) {
                sign(); scores(false)
                val mw = screenW - 20.dp - 46.dp
                machine(mw, mw / 1.36f)
                tray(); meters(false); status(); deck(false)
            }
        } else {
            // on its side the machine is as big as the screen allows, and everything else is in a column beside it
            Row(Modifier.fillMaxSize().padding(8.dp), horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                Column(Modifier.weight(1.75f).fillMaxHeight(), horizontalAlignment = Alignment.CenterHorizontally, verticalArrangement = Arrangement.Center) {
                    val availW = screenW * 0.62f - 46.dp - 8.dp
                    val availH = screenH - 16.dp - 64.dp
                    val mw = minOf(availW, availH * 1.36f)
                    machine(mw, mw / 1.36f)
                    tray()
                }
                Column(Modifier.weight(1f).fillMaxHeight().verticalScroll(rememberScrollState()), horizontalAlignment = Alignment.CenterHorizontally, verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    // everything the player presses fits on the screen without scrolling
                    scores(true); meters(true); status(); deck(true)
                }
            }
        }
        // a rain of coins for a jackpot
        if (banner == "JACKPOT!" || banner == "BIG WIN!") Canvas(Modifier.fillMaxSize()) {
            val t = frame.longValue / 1000f
            for (i in 0 until 60) {
                val seed = i * 37.7f
                val x = (sin(seed) * 0.5f + 0.5f) * size.width
                val y = ((t * (0.35f + (i % 5) * 0.08f) + (i % 13) / 13f) % 1f) * (size.height + 40f) - 20f
                drawCircle(Gold, 7f + (i % 3) * 2f, Offset(x + sin(t * 3f + seed) * 14f, y)); drawCircle(Color(0xFFB45309), 7f + (i % 3) * 2f, Offset(x + sin(t * 3f + seed) * 14f, y), style = Stroke(1.6f))
            }
        }
        if (payDialog) PayDialog { payDialog = false }
        if (s.phase == GPhase.OVER && done) {
            EndOverlay(
                title = when (s.result) { 1 -> "🏆 YOU WIN!"; 2 -> "🏆 ${them.uppercase()} WINS!"; else -> "🤝 IT'S A DRAW!" },
                subtitle = "You ${s.myCredits} credits  ·  $them ${s.theirCredits}",
                win = s.result == 1, isHost = engine.isHost, theirName = them,
                playAgainLabel = "Play again", colors = listOf(Color(0xFF7F1D1D), Color(0xFFFACC15)),
                onPlayAgain = onPlayAgain, onBackToGames = onBackToGames, onLeave = onLeave,
                how = "You started with ${SlotRules.START_CREDITS} each.",
            )
        }
    }
}

@Composable
private fun Credit(label: String, credits: Int, spins: Int, active: Boolean, col: Color, modifier: Modifier, compact: Boolean = false) {
    Column(
        modifier.clip(RoundedCornerShape(12.dp)).background(Color(0xFF0B0F1E)).border(if (active) 3.dp else 1.5.dp, if (active) col else col.copy(alpha = 0.35f), RoundedCornerShape(12.dp)).padding(if (compact) 4.dp else 8.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
    ) {
        Text(label, color = col, fontSize = 11.sp, fontWeight = FontWeight.Black, letterSpacing = 1.sp, maxLines = 1)
        Text("$credits", color = Color(0xFF86EFAC), fontSize = if (compact) 18.sp else 26.sp, fontWeight = FontWeight.Black, style = TextStyle(fontFamily = androidx.compose.ui.text.font.FontFamily.Monospace))
        if (!compact) Text("$spins spins left", color = Color.White.copy(alpha = 0.6f), fontSize = 10.sp) else Text("$spins left", color = Color.White.copy(alpha = 0.6f), fontSize = 9.sp)
    }
}

@Composable
private fun GoldButton(text: String, onClick: () -> Unit, modifier: Modifier = Modifier, enabled: Boolean = true, big: Boolean = false, red: Boolean = false, dark: Boolean = false, lit: Boolean = false, compact: Boolean = false) {
    val source = remember { androidx.compose.foundation.interaction.MutableInteractionSource() }
    val pressed by source.collectIsPressedAsState()
    val pulse = androidx.compose.animation.core.rememberInfiniteTransition(label = "gold").animateFloat(0f, 1f, androidx.compose.animation.core.infiniteRepeatable(tween(700), androidx.compose.animation.core.RepeatMode.Reverse), label = "g").value
    val shape = RoundedCornerShape(if (big) 40.dp else 16.dp)
    val face = when {
        !enabled -> listOf(Color(0xFF6B5A3A), Color(0xFF4A3D26))
        red -> listOf(Color(0xFFFF6B5A), Color(0xFFC81E1E), Color(0xFF7F1010))
        dark -> listOf(Color(0xFF6B7280), Color(0xFF1F2937), Color(0xFF030712))
        else -> listOf(Color(0xFFFFF0A0), Color(0xFFF2B705), Color(0xFFB8860B))
    }
    val textColour = when { !enabled -> Color(0xFF8B7B58); red || dark -> Color.White; else -> Color(0xFF4A2A00) }
    Box(
        modifier.heightIn(min = if (compact) (if (big) 56.dp else 42.dp) else if (big) 76.dp else 56.dp)
            .graphicsLayer { val k = if (pressed && enabled) 0.94f else 1f; scaleX = k; scaleY = k }
            .then(if (enabled && lit) Modifier.border(3.dp, Color(0xFFFFF59D).copy(alpha = 0.35f + 0.65f * pulse), shape) else Modifier)
            .clip(shape)
            .background(Brush.verticalGradient(face))
            .border(2.dp, if (enabled) Color(0xFFFFFBEB).copy(alpha = 0.85f) else Color(0xFF3B2F1B), shape)
            .clickable(interactionSource = source, indication = null, enabled = enabled) { Sfx.play(Sound.TICK); onClick() }
            .padding(horizontal = 6.dp, vertical = if (compact) 4.dp else 8.dp),
        contentAlignment = Alignment.Center,
    ) {
        // the bright bevel along the top
        Box(Modifier.fillMaxWidth(0.82f).height(if (compact) 6.dp else if (big) 14.dp else 8.dp).align(Alignment.TopCenter).clip(RoundedCornerShape(50)).background(Color.White.copy(alpha = if (enabled) 0.35f else 0.08f)))
        Text(text, color = textColour, fontWeight = FontWeight.Black, fontSize = if (big) (if (compact) 17.sp else 20.sp) else (if (compact) 11.sp else 13.sp), textAlign = TextAlign.Center, lineHeight = if (big) 20.sp else 12.sp)
    }
}

/** A glowing red digital readout, like the meters on a real machine. */
@Composable
private fun Led(label: String, value: String, modifier: Modifier = Modifier, win: Boolean = false, compact: Boolean = false) {
    Column(
        modifier.clip(RoundedCornerShape(10.dp)).background(Color(0xFF120404)).border(2.dp, Color(0xFFB8860B), RoundedCornerShape(10.dp)).padding(horizontal = 6.dp, vertical = if (compact) 2.dp else 5.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
    ) {
        Text(label, color = Color(0xFFFDE68A), fontSize = 8.sp, fontWeight = FontWeight.Black, letterSpacing = 0.5.sp, maxLines = 1)
        Text(
            value, color = if (win) Color(0xFFFFE066) else Color(0xFFFF3B2F), fontSize = if (compact) 15.sp else 20.sp, fontWeight = FontWeight.Black, maxLines = 1,
            style = TextStyle(fontFamily = androidx.compose.ui.text.font.FontFamily.Monospace, shadow = androidx.compose.ui.graphics.Shadow(if (win) Color(0xFFFFD23F) else Color(0xFFFF3B2F), Offset.Zero, 14f)),
        )
    }
}

/** The pull-down arm on the side of the machine, with its red ball. Tap it or drag it down to spin. */
@Composable
private fun Lever(pull: Float, enabled: Boolean, modifier: Modifier, onPull: () -> Unit) {
    Canvas(
        modifier
            .pointerInput(enabled) { detectTapGestures { if (enabled) onPull() } }
            .pointerInput(enabled) {
                var dragged = 0f
                detectVerticalDragGestures(onDragStart = { dragged = 0f }, onDragEnd = { if (enabled && dragged > 40f) onPull() }) { _, dy -> dragged += dy }
            },
    ) {
        val cx = size.width * 0.5f
        val baseY = size.height * 0.62f
        val travel = size.height * 0.30f
        val ballY = size.height * 0.16f + pull * travel
        // the slot the arm moves in, and the mounting plate
        drawRoundRect(Color(0xFF1F2937), Offset(cx - size.width * 0.12f, size.height * 0.10f), Size(size.width * 0.24f, size.height * 0.62f), CornerRadius(12f))
        drawRoundRect(Brush.verticalGradient(listOf(Color(0xFFFDE68A), Color(0xFFB45309))), Offset(cx - size.width * 0.32f, baseY - 4f), Size(size.width * 0.64f, size.height * 0.16f), CornerRadius(14f))
        // the arm itself: a steel shaft from the pivot up to the ball
        drawLine(Color(0xFF475569), Offset(cx, baseY + 4f), Offset(cx, ballY), size.width * 0.16f, StrokeCap.Round)
        drawLine(Color(0xFFE2E8F0), Offset(cx - size.width * 0.03f, baseY), Offset(cx - size.width * 0.03f, ballY), size.width * 0.045f, StrokeCap.Round)
        val r = size.width * 0.34f
        drawCircle(Color.Black.copy(alpha = 0.3f), r, Offset(cx + 3f, ballY + 5f))
        drawCircle(Brush.radialGradient(listOf(Color(0xFFFFB4A8), Color(0xFFDC2626), Color(0xFF7F1D1D)), Offset(cx - r * 0.3f, ballY - r * 0.3f), r * 1.4f), r, Offset(cx, ballY))
        if (enabled) drawCircle(Color(0xFFFFF59D).copy(alpha = 0.55f), r * 1.18f, Offset(cx, ballY), style = Stroke(3f))
    }
}

/** The metal tray under the machine where the coins land; the pile grows with your credits and coins rattle in when you win. */
@Composable
private fun CoinTray(credits: Int, sinceWin: Long, frame: androidx.compose.runtime.MutableLongState) {
    Canvas(Modifier.fillMaxWidth().height(58.dp)) {
        val w = size.width; val h = size.height
        val tray = Path().apply { moveTo(w * 0.08f, h * 0.28f); lineTo(w * 0.92f, h * 0.28f); lineTo(w * 0.98f, h * 0.96f); lineTo(w * 0.02f, h * 0.96f); close() }
        drawPath(tray, Brush.verticalGradient(listOf(Color(0xFF9CA3AF), Color(0xFF4B5563), Color(0xFF1F2937))))
        drawPath(tray, Color.White.copy(alpha = 0.4f), style = Stroke(2f))
        drawRoundRect(Color(0xFF111827), Offset(w * 0.12f, h * 0.36f), Size(w * 0.76f, h * 0.5f), CornerRadius(10f))
        // the pile of coins
        val coins = (credits / 40).coerceIn(2, 46)
        for (i in 0 until coins) {
            val col = i % 14; val row = i / 14
            val x = w * 0.16f + col * (w * 0.68f / 14f) + (row % 2) * 6f
            val y = h * 0.80f - row * 9f - (if (col % 3 == 0) 3f else 0f)
            drawOval(Color(0xFFB8860B), Offset(x, y), Size(18f, 9f)); drawOval(Color(0xFFFFD23F), Offset(x, y - 2f), Size(18f, 8f)); drawOval(Color(0xFFFFF3A0), Offset(x + 3f, y), Size(8f, 3f))
        }
        // coins rattling in after a win
        if (sinceWin in 0..1600) for (k in 0 until 18) {
            val t = ((sinceWin - k * 60L) / 700f).coerceIn(0f, 1f)
            if (t <= 0f || t >= 1f) continue
            val x = w * (0.2f + 0.6f * ((k * 37) % 100) / 100f)
            drawOval(Color(0xFFFFD23F), Offset(x, -h * 0.2f + t * h * 1.0f), Size(18f, 10f)); drawOval(Color(0xFFB8860B), Offset(x, -h * 0.2f + t * h * 1.0f), Size(18f, 10f), style = Stroke(1.5f))
        }
        @Suppress("UNUSED_VARIABLE") val unused = frame.longValue
    }
}

@Composable
private fun PayDialog(onClose: () -> Unit) {
    AlertDialog(
        onDismissRequest = onClose,
        confirmButton = { TextButton(onClick = onClose) { Text("Close") } },
        title = { Text("Paytable (× your bet per line)") },
        text = {
            Column(Modifier.verticalScroll(rememberScrollState()), verticalArrangement = Arrangement.spacedBy(4.dp)) {
                for (c in "SWDBGOLC") {
                    Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        Text(SlotRules.EMOJI.getValue(c), fontSize = 24.sp)
                        Text("3: ${SlotRules.pay(c, 3)}   4: ${SlotRules.pay(c, 4)}   5: ${SlotRules.pay(c, 5)}" + if (c == 'C') "   (2: ${SlotRules.CHERRY_PAIR})" else if (c == 'W') "   (wild: stands in for any symbol)" else if (c == 'S') "   (5 = JACKPOT)" else "")
                    }
                }
                Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    Text("💰", fontSize = 24.sp)
                    Text("Scatter, anywhere: 3 pays ${SlotRules.SCATTER_PAY[0]}x, 4 pays ${SlotRules.SCATTER_PAY[1]}x, 5 pays ${SlotRules.SCATTER_PAY[2]}x your whole stake, and wins ${SlotRules.FREE_SPINS[0]}, ${SlotRules.FREE_SPINS[1]} or ${SlotRules.FREE_SPINS[2]} free spins (wins doubled).")
                }
                Text("Nine paylines: across (3), V and upside-down V, two zig-zags and two bumps. Symbols must start on the left reel.", fontSize = 12.sp)
            }
        },
    )
}

// ============================================================ the machine

private fun hashSym(reel: Int, i: Int): Char = SlotRules.SYMBOLS[(((reel * 31 + i * 17 + (i * i) % 7) % 9) + 9) % 9]

private fun DrawScope.drawStar(c: Offset, r: Float, col: Color, rot: Float) {
    val p = Path()
    for (k in 0 until 8) {
        val a = rot + k * PI.toFloat() / 4f
        val rr = if (k % 2 == 0) r else r * 0.28f
        val pt = Offset(c.x + cos(a) * rr, c.y + sin(a) * rr)
        if (k == 0) p.moveTo(pt.x, pt.y) else p.lineTo(pt.x, pt.y)
    }
    p.close()
    drawPath(p, col)
}

private fun DrawScope.drawMachine(
    s: SlotsState, nowMs: Long, spinStart: Long, done: Boolean, m: TextMeasurer,
    stops: LongArray, quickAt: Long, sinceResult: Long,
) {
    val w = size.width; val h = size.height
    val paying = done && s.stage != "BET" && (s.lines.isNotEmpty() || s.scatters >= 3)
    // the cabinet: gold with a bevel, chrome rails down both sides, and rivets
    drawRoundRect(Brush.verticalGradient(listOf(Color(0xFFFFF3B0), Color(0xFFE0A81C), Color(0xFF8A5A0B), Color(0xFF5A3705))), Offset.Zero, size, CornerRadius(34f))
    drawRoundRect(Color.White.copy(alpha = 0.35f), Offset(3f, 3f), Size(w - 6f, h - 6f), CornerRadius(32f), style = Stroke(2f))
    val inset = w * 0.035f
    val gx = inset; val gy = inset
    val gw = w - 2 * inset; val gh = h - 2 * inset
    for (rail in 0..1) {
        val rx0 = if (rail == 0) 3f else w - inset + 1f
        drawRect(Brush.horizontalGradient(listOf(Color(0xFF6B7280), Color(0xFFF3F4F6), Color(0xFF9CA3AF), Color(0xFF4B5563)), rx0, rx0 + inset - 4f), Offset(rx0, gy + 14f), Size(inset - 4f, gh - 28f))
    }
    for (k in 0 until 7) { val y = gy + gh * (k + 0.5f) / 7f; drawCircle(Color(0xFFFFE9A0), 2.6f, Offset(inset * 0.5f, y)); drawCircle(Color(0xFFFFE9A0), 2.6f, Offset(w - inset * 0.5f, y)) }
    drawRoundRect(Color(0xFF0A0A14), Offset(gx, gy), Size(gw, gh), CornerRadius(22f))
    // the bulbs round the window: slow chase when idle, fast while spinning, flashing when you win
    val bulbs = 44
    val perim = 2 * (gw + gh)
    for (i in 0 until bulbs) {
        val d = i.toFloat() / bulbs * perim
        val p = when {
            d < gw -> Offset(gx + d, gy)
            d < gw + gh -> Offset(gx + gw, gy + (d - gw))
            d < 2 * gw + gh -> Offset(gx + gw - (d - gw - gh), gy + gh)
            else -> Offset(gx, gy + gh - (d - 2 * gw - gh))
        }
        val on = when {
            paying && sinceResult < 4000L -> ((nowMs / 130L).toInt() + i % 2) % 2 == 0
            !done -> (i + (nowMs / 55L).toInt()) % 3 == 0
            else -> (i + (nowMs / 140L).toInt()) % 4 == 0
        }
        val col = if (paying && on && (i % 2 == 0)) Color(0xFFFFFFFF) else Color(0xFFFEF08A)
        if (on) drawCircle(Color(0xFFFFE066).copy(alpha = 0.35f), 6.5f, p)
        drawCircle(if (on) col else Color(0xFF92400E), 3.2f, p)
    }
    // the reels
    val rx = gx + gw * 0.09f; val ry = gy + gh * 0.22f
    val rw = gw * 0.82f; val rh = gh * 0.73f
    val cw = rw / SlotRules.REELS; val ch = rh / SlotRules.ROWS
    val t = (nowMs - spinStart)
    val shownGrid = if (s.spinNo == 0) PREVIEW else s.grid
    val cellStyle = TextStyle(fontSize = (minOf(cw, ch) * 0.52f).toSp())
    val glyph = SlotRules.EMOJI.mapValues { (_, e) -> m.measure(e, cellStyle) }
    fun put(sym: Char, x0: Float, y: Float, alpha: Float = 1f, scale: Float = 1f) {
        val g = glyph.getValue(sym)
        val tl = Offset(x0 + (cw - g.size.width) / 2f, y + (ch - g.size.height) / 2f)
        if (scale == 1f) drawText(g, topLeft = tl, alpha = alpha)
        else withTransform({ scale(scale, scale, Offset(x0 + cw / 2f, y + ch / 2f)) }) { drawText(g, topLeft = tl, alpha = alpha) }
    }
    // which squares are part of a win (they pop, the rest dim a little)
    val winCells = HashSet<Int>()
    if (paying) {
        for (win in s.lines) for (r in 0 until win.count) winCells.add(r * SlotRules.ROWS + SlotRules.PAYLINES[win.line][r])
        if (s.scatters >= 3) for (i in 0 until 15) if (s.grid[i] == '$') winCells.add(i)
    }
    val popClock = 1f + 0.2f * (0.5f + 0.5f * sin(nowMs / 150f))
    for (r in 0 until SlotRules.REELS) {
        val x0 = rx + r * cw
        drawRect(Brush.horizontalGradient(listOf(Color(0xFFD1D5DB), Color.White, Color(0xFFE5E7EB), Color(0xFF9CA3AF)), x0, x0 + cw), Offset(x0, ry), Size(cw, rh))
        val stopAt = if (quickAt > 0L) minOf(stops[r], quickAt - spinStart) else stops[r]
        val spinning = !done && t in 0 until stopAt
        clipRect(x0, ry, x0 + cw, ry + rh) {
            if (spinning) {
                // whirring: the strip scrolls down, smeared with ghost copies
                val creeping = r >= 3 && anticipates(s.grid) && quickAt == 0L && t > stops[2]
                val speed = if (creeping) 7f else 18f
                val pos = t / 1000f * speed
                val base = floor(pos).toInt(); val frac = pos - base
                for (j in -1..3) {
                    val sym = hashSym(r, base - j)
                    val y = ry + (j + frac) * ch
                    put(sym, x0, y - ch * 0.14f, 0.20f); put(sym, x0, y + ch * 0.14f, 0.20f)
                    put(sym, x0, y, 0.75f)
                }
                drawRect(Brush.verticalGradient(listOf(Color.White.copy(alpha = 0.0f), Color.White.copy(alpha = 0.35f), Color.White.copy(alpha = 0.0f)), ry, ry + rh), Offset(x0, ry), Size(cw, rh))
            } else {
                // stopped: the three symbols, with a bounce as the reel lands; the winners pulse
                val since = if (done || t < 0) 99_999L else t - stopAt
                val bounce = if (since in 0..650) (ch * 0.22f * exp(-since / 130f) * sin(since / 30f)) else 0f
                for (row in 0 until SlotRules.ROWS) {
                    val sym = shownGrid[r * SlotRules.ROWS + row]
                    val y = ry + row * ch + bounce
                    val cell = r * SlotRules.ROWS + row
                    if (paying && cell in winCells) {
                        val gl = Brush.radialGradient(listOf(Color(0xFFFFE066).copy(alpha = 0.55f), Color.Transparent), Offset(x0 + cw / 2f, y + ch / 2f), ch * 0.7f)
                        drawCircle(gl, ch * 0.7f, Offset(x0 + cw / 2f, y + ch / 2f))
                        put(sym, x0, y, 1f, popClock)
                    } else put(sym, x0, y, if (paying) 0.55f else 1f)
                }
            }
        }
        // glass shine and shading at top and bottom of each reel
        drawRect(Brush.verticalGradient(listOf(Color.Black.copy(alpha = 0.50f), Color.Transparent, Color.Transparent, Color.Black.copy(alpha = 0.50f)), ry, ry + rh), Offset(x0, ry), Size(cw, rh))
        drawLine(Color.Black.copy(alpha = 0.6f), Offset(x0, ry), Offset(x0, ry + rh), 3f)
        // the "will it be the third scatter?" frame on a reel that is still creeping
        if (spinning && r >= 3 && anticipates(s.grid) && quickAt == 0L && t > stops[2]) {
            val fl = 0.5f + 0.5f * sin(nowMs / 90f)
            drawRect(Color(0xFFFFE066).copy(alpha = 0.12f + 0.14f * fl), Offset(x0, ry), Size(cw, rh))
            drawRect(Color(0xFFFFE066).copy(alpha = 0.55f + 0.45f * fl), Offset(x0 + 2f, ry + 2f), Size(cw - 4f, rh - 4f), style = Stroke(4f))
        }
    }
    drawLine(Color.Black.copy(alpha = 0.6f), Offset(rx + rw, ry), Offset(rx + rw, ry + rh), 3f)
    for (row in 1 until SlotRules.ROWS) drawLine(Color.Black.copy(alpha = 0.18f), Offset(rx, ry + row * ch), Offset(rx + rw, ry + row * ch), 2f)
    // a pane of glass over the reels, with a bright glint that sweeps across now and then
    run {
        val cyc = (nowMs % 4200L) / 800f
        if (cyc in 0f..1f) {
            val x = rx - rw * 0.25f + cyc * rw * 1.5f
            val band = Path().apply { moveTo(x, ry); lineTo(x + rw * 0.10f, ry); lineTo(x + rw * 0.10f - rh * 0.25f, ry + rh); lineTo(x - rh * 0.25f, ry + rh); close() }
            clipRect(rx, ry, rx + rw, ry + rh) { drawPath(band, Color.White.copy(alpha = 0.10f)) }
        }
        drawRoundRect(Color.Black.copy(alpha = 0.55f), Offset(rx - 2f, ry - 2f), Size(rw + 4f, rh + 4f), CornerRadius(6f), style = Stroke(5f))
    }
    // the pay table across the top of the machine, with the symbols that have just paid lit up
    run {
        val px = gx + gw * 0.03f; val py = gy + gh * 0.025f; val pw = gw * 0.94f; val ph = gh * 0.165f
        drawRoundRect(Color(0xFF1A0A05), Offset(px, py), Size(pw, ph), CornerRadius(14f))
        drawRoundRect(Color(0xFFD4A017), Offset(px, py), Size(pw, ph), CornerRadius(14f), style = Stroke(3f))
        val lit = if (done && s.stage != "BET") s.lines.map { it.symbol }.toSet() else emptySet()
        val syms = "SWDBGOLC"
        val cellW = pw / syms.length
        val small = TextStyle(fontSize = (ph * 0.36f).toSp())
        val num = TextStyle(fontSize = (ph * 0.24f).toSp(), color = Color(0xFFFDE68A), fontWeight = FontWeight.Black)
        for ((i, c) in syms.withIndex()) {
            val cx = px + cellW * (i + 0.5f)
            val on = c in lit
            val flash = if (on) 0.6f + 0.4f * sin(nowMs / 120f) else 1f
            if (on) drawRoundRect(Color(0xFFFFD23F).copy(alpha = 0.30f * flash), Offset(px + cellW * i + 2f, py + 3f), Size(cellW - 4f, ph - 6f), CornerRadius(10f))
            val g = m.measure(SlotRules.EMOJI.getValue(c), small)
            drawText(g, topLeft = Offset(cx - g.size.width / 2f, py + ph * 0.06f), alpha = if (on) 1f else 0.85f)
            val label = m.measure(if (c == 'W') "WILD" else "×${SlotRules.pay(c, 5)}", num)
            drawText(label, topLeft = Offset(cx - label.size.width / 2f, py + ph * 0.60f), alpha = if (on) 1f else 0.8f)
        }
    }
    // the payline numbers down both sides: a line lights up when it pays
    run {
        val payingLines = if (done && s.stage != "BET") s.lines.map { it.line }.toSet() else emptySet()
        val tabR = minOf(cw * 0.15f, ch * 0.14f)
        val numStyle = TextStyle(fontSize = (tabR * 1.25f).toSp(), color = Color(0xFF111827), fontWeight = FontWeight.Black)
        for (side in 0..1) {
            val reel = if (side == 0) 0 else SlotRules.REELS - 1
            for (row in 0 until SlotRules.ROWS) {
                val here = (0 until SlotRules.LINES).filter { SlotRules.PAYLINES[it][reel] == row }
                for ((k, li) in here.withIndex()) {
                    val x = if (side == 0) gx + gw * 0.045f else gx + gw * 0.955f
                    val y = ry + row * ch + ch * (k + 1f) / (here.size + 1f)
                    val col = LineColours[li % LineColours.size]
                    val on = li in payingLines
                    val flash = if (on) 0.6f + 0.4f * sin(nowMs / 130f) else 0.45f
                    if (on) drawCircle(col.copy(alpha = 0.4f * flash), tabR * 1.7f, Offset(x, y))
                    drawCircle(col.copy(alpha = flash), tabR, Offset(x, y))
                    drawCircle(Color(0xFF111827).copy(alpha = 0.6f), tabR, Offset(x, y), style = Stroke(1.6f))
                    val g = m.measure("${li + 1}", numStyle)
                    drawText(g, topLeft = Offset(x - g.size.width / 2f, y - g.size.height / 2f))
                }
            }
        }
    }
    // the winning lines draw themselves from the left, one by one
    if (done && s.stage != "BET" && s.lines.isNotEmpty()) {
        val flash = 0.55f + 0.45f * sin(nowMs / 140f)
        for ((wi, win) in s.lines.withIndex()) {
            val col = LineColours[win.line % LineColours.size]
            val pts = List(win.count) { r -> Offset(rx + r * cw + cw / 2f, ry + SlotRules.PAYLINES[win.line][r] * ch + ch / 2f) }
            val reveal = ((sinceResult - wi * 260L) / 520f).coerceIn(0f, 1f)
            if (reveal <= 0f) continue
            val segs = pts.size - 1
            val upTo = reveal * segs
            val path = Path().apply { moveTo(pts[0].x, pts[0].y) }
            for (k in 1 until pts.size) {
                val f = (upTo - (k - 1)).coerceIn(0f, 1f)
                if (f <= 0f) break
                val a0 = pts[k - 1]; val b0 = pts[k]
                path.lineTo(a0.x + (b0.x - a0.x) * f, a0.y + (b0.y - a0.y) * f)
            }
            for ((r, c0) in pts.withIndex()) {
                if (r > upTo + 0.01f) break
                val row = SlotRules.PAYLINES[win.line][r]
                drawRoundRect(col.copy(alpha = 0.22f * flash), Offset(rx + r * cw + 3f, ry + row * ch + 3f), Size(cw - 6f, ch - 6f), CornerRadius(10f))
                drawRoundRect(col.copy(alpha = flash), Offset(rx + r * cw + 3f, ry + row * ch + 3f), Size(cw - 6f, ch - 6f), CornerRadius(10f), style = Stroke(3.4f))
                // twinkling stars round each winning symbol
                for (k in 0 until 3) {
                    val a = nowMs / 400f + k * 2.1f + r
                    val tw = 0.5f + 0.5f * sin(nowMs / 120f + k * 1.7f + r)
                    drawStar(Offset(c0.x + cos(a) * cw * 0.42f, c0.y + sin(a) * ch * 0.38f), minOf(cw, ch) * 0.10f * (0.6f + tw), Color.White.copy(alpha = 0.4f + 0.6f * tw), a)
                }
            }
            drawPath(path, col.copy(alpha = 0.9f * flash), style = Stroke(5f, cap = StrokeCap.Round, join = StrokeJoin.Round))
        }
    }
    // scatters glow when they have paid
    if (done && s.scatters >= 3 && s.stage != "BET") for (r in 0 until SlotRules.REELS) for (row in 0 until SlotRules.ROWS) if (s.grid[r * SlotRules.ROWS + row] == '$') {
        drawCircle(Color(0xFF86EFAC).copy(alpha = 0.35f + 0.3f * sin(nowMs / 120f)), minOf(cw, ch) * 0.5f, Offset(rx + r * cw + cw / 2f, ry + row * ch + ch / 2f), style = Stroke(5f))
    }
    // a jackpot lights up the whole cabinet
    if (done && s.jackpot && s.stage != "BET" && sinceResult in 0..3500L) {
        drawRoundRect(Color.White.copy(alpha = 0.22f * (0.5f + 0.5f * sin(nowMs / 90f))), Offset.Zero, size, CornerRadius(34f))
    }
}

internal fun DrawScope.slotsIcon() {
    drawRoundRect(Color(0xFFB91C1C), Offset(14f, 18f), Size(72f, 62f), CornerRadius(10f))
    drawRoundRect(Color(0xFFFFD23F), Offset(14f, 18f), Size(72f, 62f), CornerRadius(10f), style = Stroke(4f))
    drawRoundRect(Color.White, Offset(22f, 30f), Size(56f, 28f), CornerRadius(5f))
    for (i in 1..2) drawLine(Color(0xFF9CA3AF), Offset(22f + 56f * i / 3f, 30f), Offset(22f + 56f * i / 3f, 58f), 2f)
    drawCircle(Color(0xFFDC2626), 5f, Offset(31f, 44f)); drawCircle(Color(0xFF111827), 5f, Offset(50f, 44f)); drawCircle(Color(0xFFFACC15), 5f, Offset(69f, 44f))
    drawLine(Color(0xFFFFD23F), Offset(88f, 34f), Offset(88f, 16f), 5f, StrokeCap.Round); drawCircle(Color(0xFFDC2626), 6f, Offset(88f, 14f))
}
