package com.neil1454.drawsomething.ui

import androidx.compose.animation.animateColorAsState
import androidx.compose.animation.core.animateIntAsState
import androidx.compose.animation.core.tween
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.gestures.awaitEachGesture
import androidx.compose.foundation.gestures.awaitFirstDown
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableLongStateOf
import androidx.compose.runtime.mutableStateListOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.runtime.withFrameNanos
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.hapticfeedback.HapticFeedbackType
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.layout.onSizeChanged
import androidx.compose.ui.platform.LocalHapticFeedback
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.neil1454.drawsomething.core.GPhase
import com.neil1454.drawsomething.core.Sound
import com.neil1454.drawsomething.core.TetrisEngine
import com.neil1454.drawsomething.core.TetrisMode
import com.neil1454.drawsomething.core.TetrisRules
import com.neil1454.drawsomething.core.TetrisState
import com.neil1454.drawsomething.core.TetrisTouch
import com.neil1454.drawsomething.sound.Sfx
import kotlinx.coroutines.isActive
import kotlin.math.sin

@Composable
fun TetrisHelp() {
    Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
        Text("Tetris, head to head! You each play your own board and see each other's live, side by side.")
        Text("• Slide your finger to move the piece, tap the left or right half of the screen to turn it, drag down to fall faster, flick down to drop it, flick up to hold. The whole screen is the control: no buttons to miss. Settings (⚙) lets you pick a Relaxed, Normal or Quick touch feel.")
        Text("• Complete rows to clear them. Every cleared line charges your GIFT meter: a single line is a half, a double is one and a half, and four lines is five gift pieces. Combos and back-to-back four-line clears add more, and a perfect clear (empty board) is a big bonus.")
        Text("• Gifts are sent to your rival as gold wrapped pieces made from the blocks you cleared. They wait 3 seconds in a meter (clear 2+ rows to cancel them), then join the NEXT queue for your rival to place like any other piece: never two in a row.")
        Text("• Modes: Battle (last stack standing), Score attack, Sprint (first to clear the lines), Bomb Blitz (bombs blast holes and send the blown-up blocks to your rival), Rising Tide (junk rows rise on both boards), Turbo (it keeps speeding up), Power Surge (clear a row with a star block to dizzy, speed up or fog your rival).")
        Text("• Four block styles (Glass, Neon, Candy, Classic), optional music and vibration are in Settings.")
    }
}

// ------------------------------------------------------------------ small pieces of the design

private val Palettes = listOf(
    Color(0xFF0B1436) to Color(0xFF04071A),
    Color(0xFF1C1040) to Color(0xFF08051C),
    Color(0xFF0A2E3E) to Color(0xFF03111C),
    Color(0xFF2A1238) to Color(0xFF12061C),
    Color(0xFF14301C) to Color(0xFF05140A),
    Color(0xFF34230E) to Color(0xFF160C04),
    Color(0xFF3A0F1C) to Color(0xFF14040A),
)

private val Gold = Color(0xFFFFC53D)
private val Mist = Color(0xFF7C8CFF)

@Composable
private fun ScoreCard(name: String, score: Int, lines: Int, level: Int, accent: Color, alive: Boolean, endAligned: Boolean, modifier: Modifier) {
    val shown by animateIntAsState(score, tween(500), label = "score")
    val shape = RoundedCornerShape(14.dp)
    Column(
        modifier
            .clip(shape)
            .background(Brush.horizontalGradient(listOf(accent.copy(alpha = 0.22f), Color(0x99080C26))))
            .border(1.dp, (if (alive) accent else Color(0xFFFF4D5E)).copy(alpha = 0.6f), shape)
            .padding(horizontal = 10.dp, vertical = 5.dp),
        horizontalAlignment = if (endAligned) Alignment.End else Alignment.Start,
    ) {
        Text(name.uppercase(), fontSize = 10.sp, fontWeight = FontWeight.Bold, letterSpacing = 1.6.sp, color = accent, maxLines = 1)
        Text("$shown", fontSize = 21.sp, fontWeight = FontWeight.Black, color = Color.White, maxLines = 1)
        Text("LV $level · $lines LINES", fontSize = 9.sp, letterSpacing = 1.sp, color = Color.White.copy(alpha = 0.65f), maxLines = 1)
    }
}

@Composable
private fun ModeChip(label: String, timer: String, modifier: Modifier) {
    Column(modifier, horizontalAlignment = Alignment.CenterHorizontally) {
        Text(label.uppercase(), fontSize = 8.sp, fontWeight = FontWeight.Bold, letterSpacing = 1.4.sp, color = Gold, maxLines = 1)
        Text(timer, fontSize = 14.sp, fontWeight = FontWeight.Black, color = Color.White, maxLines = 1)
    }
}

/** The gift meter on the left of the well: gifts in the queue are bright, gifts still cancellable are dim. */
@Composable
private fun GiftMeter(pending: Int, queued: Int, modifier: Modifier) {
    Canvas(modifier) {
        val seg = size.height / TetrisRules.MAX_INCOMING
        drawRoundRect(color = Color(0x40000000), cornerRadius = CornerRadius(size.width / 2))
        for (i in 0 until minOf(queued + pending, TetrisRules.MAX_INCOMING)) {
            val c = if (i < queued) Gold else Gold.copy(alpha = 0.35f)
            drawRoundRect(color = c, topLeft = Offset(1f, size.height - (i + 1) * seg + 1f), size = Size(size.width - 2f, seg - 2f), cornerRadius = CornerRadius(3f))
        }
    }
}

@Composable
private fun MiniBar(fraction: Float, colour: Color, modifier: Modifier, pips: Int = 0) {
    Canvas(modifier) {
        drawRoundRect(color = Color(0x40000000), cornerRadius = CornerRadius(size.height / 2))
        val f = fraction.coerceIn(0f, 1f)
        if (f > 0f) drawRoundRect(color = colour, size = Size(size.width * f, size.height), cornerRadius = CornerRadius(size.height / 2))
        for (i in 1 until pips) {
            val x = size.width * i / pips
            drawLine(Color(0xFF0B1030), Offset(x, 0f), Offset(x, size.height), strokeWidth = 2f)
        }
    }
}

@Composable
private fun GestureLegend() {
    val rows = listOf(
        "↔" to "Slide to move",
        "↺ ↻" to "Tap left or right to turn",
        "↓" to "Drag down to fall · flick down to drop",
        "↑" to "Flick up to hold",
    )
    Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
        rows.forEach { (glyph, text) ->
            Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                Box(
                    Modifier.width(44.dp).clip(RoundedCornerShape(10.dp)).background(Color(0x33FFFFFF)).padding(vertical = 4.dp),
                    contentAlignment = Alignment.Center,
                ) { Text(glyph, fontSize = 14.sp, fontWeight = FontWeight.Bold, color = Color.White) }
                Text(text, fontSize = 13.sp, color = Color.White.copy(alpha = 0.85f))
            }
        }
    }
}

// ------------------------------------------------------------------ the touch surface

/**
 * The whole screen is the control. All the recognising is done by [TetrisTouch]; this only feeds it the finger.
 */
private fun Modifier.tetrisTouch(
    engine: TetrisEngine,
    feel: Int,
    cellPx: () -> Float,
    onFeel: (Boolean) -> Unit,
): Modifier = pointerInput(engine, feel) {
    val touch = TetrisTouch(feel)
    fun run(acts: List<TetrisTouch.Act>) {
        for (a in acts) {
            when (a) {
                TetrisTouch.Act.Left -> if (!engine.moveLeft()) touch.blocked(false)
                TetrisTouch.Act.Right -> if (!engine.moveRight()) touch.blocked(true)
                TetrisTouch.Act.RotateCw -> if (engine.rotate()) onFeel(false)
                TetrisTouch.Act.RotateCcw -> if (engine.rotateBack()) onFeel(false)
                TetrisTouch.Act.HardDrop -> { engine.hardDrop(); Sfx.play(Sound.DROP); onFeel(true) }
                TetrisTouch.Act.Hold -> if (engine.hold()) onFeel(false)
                is TetrisTouch.Act.Soft -> engine.setSoft(a.on)
            }
        }
    }
    awaitEachGesture {
        val down = awaitFirstDown(requireUnconsumed = false)
        touch.down(down.position.x, down.position.y, down.uptimeMillis, cellPx(), size.width.toFloat())
        try {
            while (true) {
                val event = awaitPointerEvent()
                val change = event.changes.firstOrNull { it.id == down.id } ?: break
                if (!change.pressed) {
                    run(touch.up(change.position.x, change.position.y, change.uptimeMillis))
                    break
                }
                val acts = touch.move(change.position.x, change.position.y, change.uptimeMillis)
                if (acts.isNotEmpty()) {
                    run(acts)
                    change.consume()
                }
            }
        } finally {
            run(touch.cancel())
        }
    }
}

// ------------------------------------------------------------------ the screen

@Composable
fun TetrisScreen(
    s0: TetrisState,
    engine: TetrisEngine,
    onPlayAgain: () -> Unit,
    onBackToGames: () -> Unit,
    onLeave: () -> Unit,
) {
    var frame by remember { mutableIntStateOf(0) }
    LaunchedEffect(engine) {
        while (isActive) {
            withFrameNanos { }
            engine.frame()
            frame++
        }
    }
    val s = engine.state
    val mine = engine.board
    val rival = engine.opp
    val over = s.phase == GPhase.OVER
    val info = TetrisMode.info(s.mode)
    val skin = TetrisPrefs.skin
    val haptic = LocalHapticFeedback.current
    val toasts = remember { mutableStateListOf<Toast>() }
    var toastId by remember { mutableLongStateOf(0L) }
    var showSettings by remember { mutableStateOf(false) }
    var soundOn by remember { mutableStateOf(Sfx.enabled) }
    val cellPx = remember { mutableFloatStateOf(24f) }

    fun announce(title: String, sub: String, accent: Color, big: Boolean, sound: Sound? = null) {
        toasts.add(Toast(toastId++, title, sub, accent, big))
        if (sound != null) Sfx.play(sound)
    }

    // optional music while a match is on
    val wantMusic = TetrisPrefs.music && s.phase == GPhase.PLAYING
    DisposableEffect(wantMusic) {
        if (wantMusic) Sfx.startMusic()
        onDispose { Sfx.stopMusic() }
    }

    // ---- things that happened on MY board (read from the live board each frame)
    val clearSerial = mine?.clearSerial ?: 0
    val clearP = rememberBurst(clearSerial, 750)
    val blastSerial = mine?.blastSerial ?: 0
    val blastP = rememberBurst(blastSerial, 650)
    val trailSerial = mine?.dropSerial ?: 0
    val trailP = rememberBurst(trailSerial, 280)
    val shakeTrigger = blastSerial * 3 + (mine?.fxSerial ?: 0) * 5 + (if ((mine?.lastClear ?: 0) >= 4) clearSerial * 7 else 0)
    val shakeP = rememberBurst(shakeTrigger, 450)
    var prevSent by remember { mutableIntStateOf(0) }
    var prevB2b by remember { mutableIntStateOf(0) }

    LaunchedEffect(clearSerial) {
        val b = mine ?: return@LaunchedEffect
        if (clearSerial == 0 || b.lastClear == 0) return@LaunchedEffect
        val n = b.lastClear
        val sentNow = b.giftsSent - prevSent
        prevSent = b.giftsSent
        val parts = ArrayList<String>()
        if (b.combo >= 1) parts.add("COMBO ×${b.combo + 1}")
        if (b.b2bSerial != prevB2b) { prevB2b = b.b2bSerial; parts.add("BACK-TO-BACK") }
        if (sentNow > 0) parts.add("$sentNow GIFT${if (sentNow == 1) "" else "S"} SENT")
        val title = listOf("", "SINGLE", "DOUBLE", "TRIPLE", "TETRIS")[n.coerceIn(0, 4)]
        val sound = when (n) { 1 -> Sound.POP; 2 -> Sound.GOOD; 3 -> Sound.COIN; else -> Sound.WIN }
        announce(title, parts.joinToString("  ·  "), if (n >= 4) Gold else if (n == 3) Color(0xFF4ADE80) else Color(0xFF22D3EE), big = n >= 3, sound = sound)
        if (n >= 3 && TetrisPrefs.haptics) haptic.performHapticFeedback(HapticFeedbackType.LongPress)
    }
    LaunchedEffect(mine?.perfectSerial ?: 0) {
        val b = mine ?: return@LaunchedEffect
        if (b.perfectSerial > 0) announce("PERFECT CLEAR", "EMPTY BOARD · BIG BONUS", Gold, big = true, sound = Sound.WIN)
    }
    LaunchedEffect(blastSerial) {
        if (blastSerial > 0) announce("BLAST", "", Color(0xFFFB923C), big = false, sound = Sound.EXPLODE)
    }
    LaunchedEffect(mine?.powerSerial ?: 0) {
        val b = mine ?: return@LaunchedEffect
        if (b.powerSerial > 0 && b.lastPower in TetrisRules.POWERS.indices) {
            announce(TetrisRules.POWERS[b.lastPower], if (b.lastPower in 1..3) "SENT TO ${s.theirName.uppercase()}" else "POWER UP", Color(0xFFA855F7), big = true, sound = Sound.CHIME)
        }
    }
    LaunchedEffect(mine?.fxSerial ?: 0) {
        val b = mine ?: return@LaunchedEffect
        if (b.fxSerial > 0) {
            val title = when (b.lastFx) { 0 -> "DIZZY"; 1 -> "TURBO CURSE"; else -> "FOG" }
            announce(title, "FROM ${s.theirName.uppercase()}", Color(0xFFF43F5E), big = true, sound = Sound.BUZZ)
        }
    }
    // ---- things that happened on the RIVAL's board, and arrivals
    LaunchedEffect(s.clearSeq) {
        if (s.clearSeq == 0 || s.lastClear == 0 || s.lastClearByMe) return@LaunchedEffect
        val names = listOf("", "SINGLE", "DOUBLE", "TRIPLE", "TETRIS")
        announce(s.theirName.uppercase(), names[s.lastClear.coerceIn(0, 4)], Color(0xFF60A5FA), big = false)
    }
    LaunchedEffect(s.giftSeq) {
        if (s.giftSeq > 0) announce("GIFTS INCOMING", "CLEAR 2+ ROWS TO CANCEL", Gold, big = false, sound = Sound.WHOOSH)
    }
    LaunchedEffect(s.myLevel) {
        if (mine != null && s.myLevel > mine.startLevel) announce("LEVEL ${s.myLevel}", "", Color(0xFF22D3EE), big = false, sound = Sound.CHIME)
    }

    val curse = mine?.activeCurse() ?: -1
    val fog = (mine?.fogMs ?: 0L) > 0
    val dizzy = (mine?.dizzyMs ?: 0L) > 0
    val spark = 0.6f + 0.4f * sin(frame / 5f)
    val palette = Palettes[((s.myLevel - 1) / 2).coerceAtLeast(0) % Palettes.size]
    val bg1 by animateColorAsState(palette.first, tween(1500), label = "bg1")
    val bg2 by animateColorAsState(palette.second, tween(1500), label = "bg2")
    val myDim = !s.myAlive && !over
    val theirDim = !s.theirAlive && !over

    Box(Modifier.fillMaxSize().background(Brush.verticalGradient(listOf(bg1, bg2)))) {
        // slowly drifting specks
        Canvas(Modifier.fillMaxSize()) {
            for (i in 0 until 26) {
                val x = ((i * 0.618034f) % 1f) * size.width + sin(frame / 90f + i) * 12f
                val y = size.height - ((frame * (0.35f + (i % 5) * 0.2f) + i * 97f) % (size.height + 40f))
                drawCircle(color = Color.White.copy(alpha = 0.04f + (i % 4) * 0.02f), radius = 1.5f + (i % 3), center = Offset(x, y))
            }
        }
        // the touch surface sits under everything else
        Box(Modifier.fillMaxSize().tetrisTouch(engine, TetrisPrefs.feel, { cellPx.floatValue }) { strong ->
            if (TetrisPrefs.haptics) haptic.performHapticFeedback(if (strong) HapticFeedbackType.LongPress else HapticFeedbackType.TextHandleMove)
        })

        BoxWithConstraints(Modifier.fillMaxSize().shaking(shakeP, 5f)) {
            val landscape = maxWidth > maxHeight
            val skinNow = skin

            val fxMine = WellFx(
                dim = myDim, fog = fog, lockGlow = mine?.lockFraction ?: 0f, pulse = spark,
                flashRows = mine?.lastClearRows ?: emptyList(), cleared = mine?.lastClearBlocks ?: emptyList(), clearP = clearP,
                trailX0 = mine?.dropX0 ?: 0, trailX1 = mine?.dropX1 ?: 0, trailY0 = mine?.dropY0 ?: 0, trailY1 = mine?.dropY1 ?: 0, trailP = trailP,
                blastX = mine?.blastX ?: -1, blastY = mine?.blastY ?: -1, blastP = blastP,
                accent = if (curse >= 0) Color(0xFFF43F5E) else Mist,
            )
            val fxRival = WellFx(dim = theirDim, accent = Color(0xFF60A5FA))

            val myWell: @Composable (Modifier) -> Unit = { m ->
                Box(m) {
                    Canvas(Modifier.fillMaxWidth().aspectRatio(0.5f).onSizeChanged { cellPx.floatValue = it.width / TetrisRules.W.toFloat() }) {
                        if (mine != null) {
                            tetrisWell(
                                { x, y -> mine.cell(x, y + TetrisRules.HIDDEN) },
                                mine.type, mine.rot, mine.px, mine.py, mine.isGift, mine.starCell,
                                if (mine.alive && !fog) mine.ghostY() else null, fxMine, skinNow,
                            )
                        } else {
                            drawRoundRect(color = Color(0xFF0B0F2B), cornerRadius = CornerRadius(12f))
                        }
                    }
                    val banner = when {
                        dizzy -> "DIZZY · LEFT AND RIGHT SWAPPED · ${(mine?.dizzyMs ?: 0L) / 1000 + 1}s"
                        (mine?.turboMs ?: 0L) > 0 -> "TURBO CURSE · ${(mine?.turboMs ?: 0L) / 1000 + 1}s"
                        fog -> "FOG · ${(mine?.fogMs ?: 0L) / 1000 + 1}s"
                        mine != null && mine.overtime -> "OVERTIME · SPEEDING UP"
                        s.mode == TetrisMode.TIDE && mine != null && mine.tideLeftMs < 3000 -> "THE TIDE IS RISING"
                        else -> null
                    }
                    if (banner != null) {
                        Text(
                            banner, fontSize = 9.sp, fontWeight = FontWeight.Bold, letterSpacing = 1.2.sp, color = Color.White, textAlign = TextAlign.Center,
                            modifier = Modifier.align(Alignment.TopCenter).padding(top = 6.dp).clip(RoundedCornerShape(8.dp))
                                .background(Color(0xCCF43F5E)).padding(horizontal = 8.dp, vertical = 3.dp),
                        )
                    }
                }
            }
            val rivalWell: @Composable (Modifier) -> Unit = { m ->
                Canvas(m.aspectRatio(0.5f)) {
                    if (rival != null) {
                        tetrisWell(
                            { x, y -> TetrisRules.codeOf(rival.grid[y * TetrisRules.W + x]) },
                            rival.type, rival.rot, rival.x, rival.y, rival.gift, -1, null, fxRival, skinNow,
                        )
                    } else {
                        drawRoundRect(color = Color(0xFF0B0F2B), cornerRadius = CornerRadius(12f))
                    }
                }
            }
            val timer = when (s.mode) {
                TetrisMode.SCORE -> if (s.secsLeft >= 0) "${s.secsLeft / 60}:${"%02d".format(s.secsLeft % 60)}" else "--:--"
                TetrisMode.SPRINT -> "${maxOf(s.myLines, s.theirLines)}/${s.target}"
                TetrisMode.TIDE -> "${((mine?.tideLeftMs ?: 0L) / 1000 + 1)}s"
                TetrisMode.TURBO -> "LV ${s.myLevel}"
                else -> info.label
            }
            val gear: @Composable () -> Unit = {
                Box(
                    Modifier.size(30.dp).clip(CircleShape).background(Color(0x55000000)).border(1.dp, Color.White.copy(alpha = 0.25f), CircleShape)
                        .clickable { showSettings = true },
                    contentAlignment = Alignment.Center,
                ) { Text("⚙", fontSize = 15.sp, color = Color.White.copy(alpha = 0.85f)) }
            }
            val hold: @Composable () -> Unit = {
                Column(horizontalAlignment = Alignment.CenterHorizontally, modifier = Modifier.width(52.dp)) {
                    Text("HOLD", fontSize = 9.sp, letterSpacing = 1.4.sp, color = Color.White.copy(alpha = 0.7f), fontWeight = FontWeight.Bold)
                    PieceSwatch(if (fog) -1 else (mine?.hold ?: -1), Modifier.fillMaxWidth(), skinNow, dim = mine?.canHold == false)
                }
            }
            val nextRow: @Composable (Int, Modifier) -> Unit = { count, m ->
                Column(m, horizontalAlignment = Alignment.CenterHorizontally) {
                    Text("NEXT", fontSize = 9.sp, letterSpacing = 1.4.sp, color = Color.White.copy(alpha = 0.7f), fontWeight = FontWeight.Bold)
                    Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(5.dp)) {
                        val list = if (fog) List(count) { -1 to false } else (mine?.upcoming(count) ?: emptyList())
                        list.forEach { (t, g) -> PieceSwatch(t, Modifier.weight(1f), skinNow, gift = g) }
                        repeat(count - list.size) { Spacer(Modifier.weight(1f)) }
                    }
                }
            }
            val meters: @Composable (Modifier) -> Unit = { m ->
                Column(m, verticalArrangement = Arrangement.spacedBy(3.dp)) {
                    val charge = mine?.charge ?: 0
                    Text("GIFT CHARGE", fontSize = 9.sp, letterSpacing = 1.4.sp, color = Gold, fontWeight = FontWeight.Bold)
                    MiniBar(charge / TetrisRules.UNITS_PER_GIFT.toFloat(), Gold, Modifier.fillMaxWidth().height(8.dp), pips = TetrisRules.UNITS_PER_GIFT)
                    Text("NEXT LEVEL", fontSize = 9.sp, letterSpacing = 1.4.sp, color = Color(0xFF22D3EE), fontWeight = FontWeight.Bold)
                    MiniBar(((mine?.lines ?: 0) % 10) / 10f, Color(0xFF22D3EE), Modifier.fillMaxWidth().height(8.dp))
                }
            }
            val giftText: @Composable () -> Unit = {
                val pending = (s.myIncoming - s.myQueued).coerceAtLeast(0)
                val text = when {
                    pending > 0 && s.myQueued > 0 -> "$pending coming (clear 2+ rows to cancel) · ${s.myQueued} next"
                    pending > 0 -> "$pending gift${if (pending == 1) "" else "s"} coming: clear 2+ rows to cancel"
                    s.myQueued > 0 -> "${s.myQueued} gold gift${if (s.myQueued == 1) "" else "s"} in your queue"
                    else -> "sent ${s.mySent} gift${if (s.mySent == 1) "" else "s"}"
                }
                Text(text, fontSize = 10.sp, fontWeight = FontWeight.SemiBold, color = if (s.myIncoming > 0) Gold else Color.White.copy(alpha = 0.6f), textAlign = TextAlign.Center, modifier = Modifier.fillMaxWidth())
            }
            val giftLine: @Composable () -> Unit = {
                ToastStrip(toasts, { id -> toasts.removeAll { it.id == id } }, Modifier.fillMaxWidth().height(34.dp), idle = { giftText() })
            }
            val rivalColumn: @Composable (Dp, Dp) -> Unit = { colW, wellW ->
                Column(Modifier.width(colW), horizontalAlignment = Alignment.CenterHorizontally, verticalArrangement = Arrangement.spacedBy(3.dp)) {
                    Text(s.theirName.uppercase(), fontSize = 10.sp, fontWeight = FontWeight.Bold, letterSpacing = 1.4.sp, color = Color(0xFF60A5FA), maxLines = 1)
                    rivalWell(Modifier.width(wellW))
                    Text(
                        if (s.theirIncoming > 0) "${s.theirIncoming} gift${if (s.theirIncoming == 1) "" else "s"} coming" else "sent ${s.theirSent}",
                        fontSize = 9.sp, color = if (s.theirIncoming > 0) Gold else Color.White.copy(alpha = 0.6f), textAlign = TextAlign.Center,
                    )
                }
            }
            val youCard: @Composable (Modifier) -> Unit = { m -> ScoreCard("You", s.myScore, s.myLines, s.myLevel, Color(0xFF4ADE80), s.myAlive, false, m) }
            val themCard: @Composable (Modifier) -> Unit = { m -> ScoreCard(s.theirName, s.theirScore, s.theirLines, s.theirLevel, Color(0xFF60A5FA), s.theirAlive, true, m) }

            if (!landscape) {
                val hudH = 58.dp
                val bottomH = 84.dp
                val availH = (maxHeight - hudH - bottomH - 60.dp).coerceAtLeast(160.dp)
                val myW = minOf(maxWidth * 0.68f, availH / 2f)
                val rightW = (maxWidth - myW - 10.dp - 16.dp - 12.dp).coerceAtLeast(56.dp)
                val rivalW = minOf(rightW, ((availH - 44.dp) / 2f).coerceAtLeast(56.dp))
                Column(
                    Modifier.fillMaxSize().padding(horizontal = 8.dp, vertical = 6.dp),
                    verticalArrangement = Arrangement.spacedBy(6.dp),
                    horizontalAlignment = Alignment.CenterHorizontally,
                ) {
                    Row(Modifier.fillMaxWidth().height(hudH), horizontalArrangement = Arrangement.spacedBy(6.dp), verticalAlignment = Alignment.CenterVertically) {
                        gear()
                        youCard(Modifier.weight(1f))
                        ModeChip(info.label, timer, Modifier.width(66.dp))
                        themCard(Modifier.weight(1f))
                        Spacer(Modifier.width(34.dp))
                    }
                    Row(Modifier.fillMaxWidth().weight(1f), horizontalArrangement = Arrangement.spacedBy(6.dp), verticalAlignment = Alignment.Top) {
                        GiftMeter(s.myIncoming - s.myQueued, s.myQueued, Modifier.width(10.dp).height(myW * 2f))
                        Column(Modifier.width(myW)) {
                            myWell(Modifier.width(myW))
                            giftLine()
                        }
                        rivalColumn(rightW, rivalW)
                    }
                    Row(Modifier.fillMaxWidth().height(bottomH), horizontalArrangement = Arrangement.spacedBy(10.dp), verticalAlignment = Alignment.Top) {
                        hold()
                        nextRow(4, Modifier.weight(1f))
                        meters(Modifier.width(92.dp))
                    }
                }
            } else {
                val wellH = (maxHeight - 16.dp).coerceAtLeast(160.dp)
                val myW = wellH / 2f
                Row(
                    Modifier.fillMaxSize().padding(8.dp),
                    horizontalArrangement = Arrangement.spacedBy(8.dp),
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    GiftMeter(s.myIncoming - s.myQueued, s.myQueued, Modifier.width(10.dp).height(wellH))
                    Column(Modifier.width(myW)) { myWell(Modifier.width(myW)) }
                    Column(Modifier.weight(1f).fillMaxHeight(), verticalArrangement = Arrangement.spacedBy(8.dp, Alignment.CenterVertically), horizontalAlignment = Alignment.CenterHorizontally) {
                        Row(horizontalArrangement = Arrangement.spacedBy(6.dp), verticalAlignment = Alignment.CenterVertically) {
                            gear()
                            ModeChip(info.label, timer, Modifier)
                        }
                        youCard(Modifier.fillMaxWidth())
                        themCard(Modifier.fillMaxWidth())
                        Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                            hold()
                            nextRow(3, Modifier.weight(1f))
                        }
                        meters(Modifier.fillMaxWidth())
                        giftLine()
                    }
                    rivalColumn(myW * 0.5f, myW * 0.5f)
                }
            }
        }

        if (!s.myAlive && !over) {
            Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                Column(
                    Modifier.clip(RoundedCornerShape(16.dp)).background(Color(0xE60B0F2B)).border(1.dp, Color(0xFFF43F5E), RoundedCornerShape(16.dp)).padding(horizontal = 22.dp, vertical = 14.dp),
                    horizontalAlignment = Alignment.CenterHorizontally,
                ) {
                    Text("STACK FULL", fontSize = 22.sp, fontWeight = FontWeight.Black, letterSpacing = 4.sp, color = Color.White)
                    if (s.mode == TetrisMode.SCORE) Text("WAITING FOR THE CLOCK", fontSize = 10.sp, letterSpacing = 2.sp, color = Color(0xFFF43F5E))
                }
            }
        }

        if (s.countdownMs > 0) {
            Box(Modifier.fillMaxSize().background(Color(0xE6050816)), contentAlignment = Alignment.Center) {
                Column(horizontalAlignment = Alignment.CenterHorizontally, verticalArrangement = Arrangement.spacedBy(10.dp), modifier = Modifier.padding(24.dp)) {
                    Text(info.label.uppercase(), fontSize = 14.sp, fontWeight = FontWeight.Bold, letterSpacing = 6.sp, color = Gold)
                    Text("${(s.countdownMs + 999) / 1000}", fontSize = 110.sp, fontWeight = FontWeight.Black, color = Color.White)
                    Text(info.blurb, fontSize = 13.sp, color = Color.White.copy(alpha = 0.75f), textAlign = TextAlign.Center)
                    Spacer(Modifier.height(6.dp))
                    GestureLegend()
                }
            }
        }

        if (showSettings) {
            SettingsDialog(soundOn = soundOn, onSound = { soundOn = it; Sfx.setEnabled(it) }, onClose = { showSettings = false })
        }

        if (over) {
            EndOverlay(
                title = when (s.result) { 1 -> "YOU WIN!"; 2 -> "${s.theirName.uppercase()} WINS!"; else -> "IT'S A DRAW!" },
                subtitle = "You ${s.myScore}  ·  ${s.theirName} ${s.theirScore}",
                win = s.result == 1, isHost = engine.isHost, theirName = s.theirName,
                playAgainLabel = "Rematch", colors = listOf(Color(0xFF6A3DE8), Color(0xFF00B8D9)),
                onPlayAgain = onPlayAgain, onBackToGames = onBackToGames, onLeave = onLeave,
                how = s.how,
            )
        }
    }
}
