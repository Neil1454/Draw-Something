package com.neil1454.drawsomething.ui

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
private const val SPIN_MS = 2100L
private fun reelStop(r: Int) = 650L + r * 330L

@Composable
fun SlotsScreen(s: SlotsState, engine: SlotsEngine, onPlayAgain: () -> Unit, onBackToGames: () -> Unit, onLeave: () -> Unit) {
    val frame = remember { mutableLongStateOf(0L) }
    val spinStart = remember { mutableLongStateOf(-100_000L) }
    val seenSpin = remember { mutableIntStateOf(s.spinNo) }
    var done by remember { mutableStateOf(true) }
    var payDialog by remember { mutableStateOf(false) }
    var banner by remember { mutableStateOf("") }
    val measurer = rememberTextMeasurer()
    val them = engine.theirName
    LaunchedEffect(Unit) { while (true) withFrameMillis { frame.longValue = it } }

    // each new spin: the lever, the whirring reels stopping one by one, then the result
    LaunchedEffect(s.spinNo) {
        if (s.spinNo <= seenSpin.intValue) { seenSpin.intValue = s.spinNo; return@LaunchedEffect }
        seenSpin.intValue = s.spinNo
        done = false; banner = ""
        spinStart.longValue = frame.longValue
        Sfx.play(Sound.LEVER)
        var t = 0L
        var next = 0
        while (t < SPIN_MS) {
            delay(55); t += 55
            if (next < SlotRules.REELS && t >= reelStop(next)) { Sfx.play(Sound.STOP); next++ } else if (t % 110L == 0L) Sfx.play(Sound.REEL)
        }
        done = true
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

    Box(Modifier.fillMaxSize()) {
        Canvas(Modifier.fillMaxSize()) {
            drawRect(Brush.verticalGradient(listOf(Color(0xFF1A0505), Color(0xFF4A0D14), Color(0xFF1A0505))))
            // velvet stripes
            var x = 0f
            while (x < size.width) { drawRect(Color.White.copy(alpha = 0.025f), Offset(x, 0f), Size(18f, size.height)); x += 56f }
        }
        Column(Modifier.fillMaxSize().verticalScroll(rememberScrollState()).padding(horizontal = 10.dp, vertical = 10.dp), horizontalAlignment = Alignment.CenterHorizontally, verticalArrangement = Arrangement.spacedBy(8.dp)) {
            // the sign
            Box(Modifier.fillMaxWidth().height(46.dp).clip(RoundedCornerShape(14.dp)).background(Brush.horizontalGradient(listOf(Color(0xFF7F1D1D), Color(0xFFB91C1C), Color(0xFF7F1D1D)))).border(2.dp, Gold, RoundedCornerShape(14.dp)), contentAlignment = Alignment.Center) {
                Canvas(Modifier.fillMaxSize()) {
                    val n = 22
                    for (i in 0 until n) {
                        val on = (i + (frame.longValue / 150L).toInt()) % 2 == 0
                        val cx = size.width * (i + 0.5f) / n
                        drawCircle(if (on) Gold else Gold.copy(alpha = 0.25f), 3.4f, Offset(cx, 6f)); drawCircle(if (on) Gold else Gold.copy(alpha = 0.25f), 3.4f, Offset(cx, size.height - 6f))
                    }
                }
                Text("🎰  LUCKY SEVENS  🎰", color = Gold, fontWeight = FontWeight.Black, fontSize = 20.sp, letterSpacing = 3.sp)
            }
            // the two scores
            Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                Credit("YOU", myCredits, s.mySpins, s.myTurn && s.phase == GPhase.PLAYING, Color(0xFF22D3EE), Modifier.weight(1f))
                Credit(them.uppercase(), theirCredits, s.theirSpins, !s.myTurn && s.phase == GPhase.PLAYING, Color(0xFFF472B6), Modifier.weight(1f))
            }
            // the machine
            Box(Modifier.fillMaxWidth().aspectRatio(5f / 3f + 0.22f)) {
                Canvas(Modifier.fillMaxSize()) { drawMachine(s, frame.longValue, spinStart.longValue, done, measurer) }
                if (banner.isNotEmpty()) {
                    val big = banner == "JACKPOT!" || banner == "BIG WIN!"
                    Text(
                        banner, color = Gold, fontWeight = FontWeight.Black, fontSize = if (big) 40.sp else 30.sp, textAlign = TextAlign.Center,
                        modifier = Modifier.align(Alignment.Center).clip(RoundedCornerShape(20.dp)).background(Color(0xE60B0624)).border(3.dp, Gold, RoundedCornerShape(20.dp)).padding(horizontal = 24.dp, vertical = 12.dp),
                    )
                }
            }
            // what the last spin did
            Text(
                when {
                    !done -> "🎰 Spinning…"
                    s.stage == "WIN" && s.total > 0 && s.card == 0 -> "${if (s.lastBy == 1) "You" else them} won ${s.total} credits${if (s.lastFree) " (free spin: doubled)" else ""}!"
                    s.stage == "WIN" -> "Prize on the table: ${s.pending}"
                    s.stage == "LOST" -> "💥 The gamble lost!"
                    s.spinNo == 0 -> "Place your bet and pull the lever!"
                    s.total > 0 -> "${if (s.lastBy == 1) "You" else them} won ${s.total}"
                    else -> "No win that time"
                },
                color = Gold, fontWeight = FontWeight.Bold, fontSize = 15.sp, textAlign = TextAlign.Center,
            )
            // the controls
            if (s.phase == GPhase.PLAYING) when {
                !done -> Unit
                s.stage == "BET" && s.myTurn -> BetPanel(s, engine)
                s.stage == "WIN" && s.myTurn -> WinPanel(s, engine)
                s.stage == "BET" -> Text("⏳ $them is on the machine…", color = Color.White.copy(alpha = 0.85f))
                s.stage == "WIN" -> Text("⏳ $them is deciding whether to gamble…", color = Color.White.copy(alpha = 0.85f))
            }
            if (s.freeSpins > 0 && s.phase == GPhase.PLAYING) Text("⭐ ${s.freeSpins} FREE SPINS LEFT (wins doubled)", color = Color(0xFF86EFAC), fontWeight = FontWeight.Black)
            TextButton(onClick = { payDialog = true }) { Text("📋 Paytable", color = Gold) }
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
private fun Credit(label: String, credits: Int, spins: Int, active: Boolean, col: Color, modifier: Modifier) {
    Column(
        modifier.clip(RoundedCornerShape(12.dp)).background(Color(0xFF0B0F1E)).border(if (active) 3.dp else 1.5.dp, if (active) col else col.copy(alpha = 0.35f), RoundedCornerShape(12.dp)).padding(8.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
    ) {
        Text(label, color = col, fontSize = 11.sp, fontWeight = FontWeight.Black, letterSpacing = 1.sp, maxLines = 1)
        Text("$credits", color = Color(0xFF86EFAC), fontSize = 26.sp, fontWeight = FontWeight.Black, style = TextStyle(fontFamily = androidx.compose.ui.text.font.FontFamily.Monospace))
        Text("$spins spins left", color = Color.White.copy(alpha = 0.6f), fontSize = 10.sp)
    }
}

@Composable
private fun BetPanel(s: SlotsState, engine: SlotsEngine) {
    Column(horizontalAlignment = Alignment.CenterHorizontally, verticalArrangement = Arrangement.spacedBy(8.dp)) {
        if (s.freeSpins == 0) {
            Text("BET PER LINE   ·   a spin costs ${SlotRules.cost(s.bet)}", color = Color.White.copy(alpha = 0.85f), fontSize = 12.sp, fontWeight = FontWeight.Bold)
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                for ((i, b) in SlotRules.BETS.withIndex()) {
                    val ok = SlotRules.cost(b) <= s.myCredits
                    val sel = i == s.betIndex
                    Box(
                        Modifier.size(width = 62.dp, height = 46.dp).clip(RoundedCornerShape(12.dp))
                            .background(if (sel) Brush.verticalGradient(listOf(Gold, Color(0xFFD97706))) else Brush.verticalGradient(listOf(Color(0xFF1F2937), Color(0xFF111827))))
                            .border(2.dp, if (sel) Color.White else Gold.copy(alpha = if (ok) 0.6f else 0.15f), RoundedCornerShape(12.dp))
                            .clickable(enabled = ok) { Sfx.play(Sound.TICK); engine.setBet(i) },
                        contentAlignment = Alignment.Center,
                    ) { Text("$b", color = if (sel) Color(0xFF111827) else if (ok) Gold else Color.White.copy(alpha = 0.25f), fontWeight = FontWeight.Black, fontSize = 20.sp) }
                }
            }
        }
        FunkyButton(if (s.freeSpins > 0) "⭐ FREE SPIN (${s.freeSpins} left)" else "🎰  SPIN  (${SlotRules.cost(s.bet)})", { engine.spin() }, Modifier.fillMaxWidth(), listOf(Color(0xFFDC2626), Color(0xFFF59E0B)), fontSize = 22.sp)
    }
}

@Composable
private fun WinPanel(s: SlotsState, engine: SlotsEngine) {
    Column(horizontalAlignment = Alignment.CenterHorizontally, verticalArrangement = Arrangement.spacedBy(8.dp)) {
        Text("🏆 ${s.pending} credits to collect", color = Gold, fontWeight = FontWeight.Black, fontSize = 18.sp)
        FunkyButton("💰  COLLECT ${s.pending}", { engine.collect() }, Modifier.fillMaxWidth(), listOf(Color(0xFF16A34A), Color(0xFF0E9F6E)), fontSize = 20.sp)
        if (s.gambles < SlotRules.MAX_GAMBLES) {
            Text("or GAMBLE it: guess the colour of the hidden card to double it (${SlotRules.MAX_GAMBLES - s.gambles} tries left) – get it wrong and it's gone!", color = Color.White.copy(alpha = 0.8f), fontSize = 12.sp, textAlign = TextAlign.Center)
            Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                FunkyButton("♥ RED  x2", { engine.gamble(true) }, Modifier.weight(1f), listOf(Color(0xFFDC2626), Color(0xFF991B1B)), fontSize = 18.sp)
                FunkyButton("♠ BLACK  x2", { engine.gamble(false) }, Modifier.weight(1f), listOf(Color(0xFF1F2937), Color(0xFF475569)), fontSize = 18.sp)
            }
        }
        if (s.card != 0 && s.cardWon) Text("The card was ${if (s.card == 1) "RED ♥" else "BLACK ♠"}: you doubled it!", color = Color(0xFF86EFAC), fontWeight = FontWeight.Bold)
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

private fun DrawScope.drawMachine(s: SlotsState, nowMs: Long, spinStart: Long, done: Boolean, m: TextMeasurer) {
    val w = size.width; val h = size.height
    // the cabinet
    drawRoundRect(Brush.verticalGradient(listOf(Color(0xFFFDE68A), Color(0xFFB45309), Color(0xFF78350F))), Offset.Zero, size, CornerRadius(34f))
    val inset = w * 0.035f
    val gx = inset; val gy = inset
    val gw = w - 2 * inset; val gh = h - 2 * inset
    drawRoundRect(Color(0xFF0A0A14), Offset(gx, gy), Size(gw, gh), CornerRadius(22f))
    // chasing bulbs round the window
    val bulbs = 40
    for (i in 0 until bulbs) {
        val f = i.toFloat() / bulbs
        val perim = 2 * (gw + gh)
        var d = f * perim
        val p = when {
            d < gw -> Offset(gx + d, gy)
            d < gw + gh -> Offset(gx + gw, gy + (d - gw))
            d < 2 * gw + gh -> Offset(gx + gw - (d - gw - gh), gy + gh)
            else -> Offset(gx, gy + gh - (d - 2 * gw - gh))
        }
        val on = (i + (nowMs / 120L).toInt()) % 3 == 0
        drawCircle(if (on) Color(0xFFFEF08A) else Color(0xFF92400E), 3.2f, p)
    }
    // the reels
    val rx = gx + gw * 0.04f; val ry = gy + gh * 0.07f
    val rw = gw * 0.92f; val rh = gh * 0.86f
    val cw = rw / SlotRules.REELS; val ch = rh / SlotRules.ROWS
    val t = (nowMs - spinStart)
    val shownGrid = if (s.spinNo == 0) PREVIEW else s.grid
    val cellStyle = TextStyle(fontSize = (minOf(cw, ch) * 0.52f).toSp())
    val glyph = SlotRules.EMOJI.mapValues { (_, e) -> m.measure(e, cellStyle) }
    fun put(sym: Char, x0: Float, y: Float, alpha: Float = 1f) {
        val g = glyph.getValue(sym)
        drawText(g, topLeft = Offset(x0 + (cw - g.size.width) / 2f, y + (ch - g.size.height) / 2f), alpha = alpha)
    }
    for (r in 0 until SlotRules.REELS) {
        val x0 = rx + r * cw
        drawRect(Brush.horizontalGradient(listOf(Color(0xFFD1D5DB), Color.White, Color(0xFFE5E7EB), Color(0xFF9CA3AF)), x0, x0 + cw), Offset(x0, ry), Size(cw, rh))
        clipRect(x0, ry, x0 + cw, ry + rh) {
            val stopAt = reelStop(r)
            if (!done && t in 0 until stopAt) {
                // whirring: the strip scrolls down, blurred with ghost copies
                val pos = t / 1000f * 18f
                val base = floor(pos).toInt(); val frac = pos - base
                for (j in -1..3) {
                    val sym = hashSym(r, base - j)
                    val y = ry + (j + frac) * ch
                    put(sym, x0, y, 0.6f)
                }
                drawRect(Brush.verticalGradient(listOf(Color.White.copy(alpha = 0.0f), Color.White.copy(alpha = 0.35f), Color.White.copy(alpha = 0.0f)), ry, ry + rh), Offset(x0, ry), Size(cw, rh))
            } else {
                // stopped: the three symbols, with a little bounce as the reel lands
                val since = if (done || t < 0) 99_999L else t - stopAt
                val bounce = if (since in 0..600) (ch * 0.16f * exp(-since / 140f) * sin(since / 32f)) else 0f
                for (row in 0 until SlotRules.ROWS) {
                    val sym = shownGrid[r * SlotRules.ROWS + row]
                    val y = ry + row * ch + bounce
                    put(sym, x0, y)
                }
            }
        }
        // glass shine and shading at top and bottom of each reel
        drawRect(Brush.verticalGradient(listOf(Color.Black.copy(alpha = 0.45f), Color.Transparent, Color.Transparent, Color.Black.copy(alpha = 0.45f)), ry, ry + rh), Offset(x0, ry), Size(cw, rh))
        drawLine(Color.Black.copy(alpha = 0.6f), Offset(x0, ry), Offset(x0, ry + rh), 3f)
    }
    drawLine(Color.Black.copy(alpha = 0.6f), Offset(rx + rw, ry), Offset(rx + rw, ry + rh), 3f)
    for (row in 1 until SlotRules.ROWS) drawLine(Color.Black.copy(alpha = 0.18f), Offset(rx, ry + row * ch), Offset(rx + rw, ry + row * ch), 2f)
    // the winning lines, once the reels have stopped
    if (done && s.stage != "BET" && s.lines.isNotEmpty()) {
        val flash = 0.55f + 0.45f * sin(nowMs / 140f)
        for (win in s.lines) {
            val col = LineColours[win.line % LineColours.size]
            val path = Path()
            val pts = ArrayList<Offset>()
            for (r in 0 until win.count) {
                val row = SlotRules.PAYLINES[win.line][r]
                val c = Offset(rx + r * cw + cw / 2f, ry + row * ch + ch / 2f)
                pts.add(c)
                if (r == 0) path.moveTo(c.x, c.y) else path.lineTo(c.x, c.y)
                drawRoundRect(col.copy(alpha = 0.22f * flash), Offset(rx + r * cw + 3f, ry + row * ch + 3f), Size(cw - 6f, ch - 6f), CornerRadius(10f))
                drawRoundRect(col.copy(alpha = flash), Offset(rx + r * cw + 3f, ry + row * ch + 3f), Size(cw - 6f, ch - 6f), CornerRadius(10f), style = Stroke(3.4f))
            }
            drawPath(path, col.copy(alpha = 0.85f * flash), style = Stroke(5f, cap = StrokeCap.Round, join = StrokeJoin.Round))
        }
    }
    // scatters glow when they have paid
    if (done && s.scatters >= 3 && s.stage != "BET") for (r in 0 until SlotRules.REELS) for (row in 0 until SlotRules.ROWS) if (s.grid[r * SlotRules.ROWS + row] == '$') {
        drawCircle(Color(0xFF86EFAC).copy(alpha = 0.35f + 0.3f * sin(nowMs / 120f)), minOf(cw, ch) * 0.5f, Offset(rx + r * cw + cw / 2f, ry + row * ch + ch / 2f), style = Stroke(5f))
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
