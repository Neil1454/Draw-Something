package com.neil1454.drawsomething.ui

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.gestures.detectDragGestures
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
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
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.rememberUpdatedState
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
import androidx.compose.ui.graphics.drawscope.DrawScope
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.drawscope.clipRect
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.neil1454.drawsomething.core.GPhase
import com.neil1454.drawsomething.core.Sound
import com.neil1454.drawsomething.core.TetrisEngine
import com.neil1454.drawsomething.core.TetrisMode
import com.neil1454.drawsomething.core.TetrisRules
import com.neil1454.drawsomething.core.TetrisState
import com.neil1454.drawsomething.sound.Sfx
import kotlinx.coroutines.delay
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch
import kotlin.math.abs
import kotlin.math.sin

private val BlockColours = listOf(0xFF00E5FF, 0xFFFFD600, 0xFFB44CFF, 0xFF3DDC84, 0xFFFF4D5E, 0xFF3D7BFF, 0xFFFF9E2C).map { Color(it) }
private val GiftGold = Color(0xFFFFC857)
private val GiftPink = Color(0xFFFF3D9A)
private val JunkGrey = Color(0xFF6D6A80)
private val WellBackground = Color(0xFF0B0A1E)

@Composable
fun TetrisHelp() {
    Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
        Text("Tetris, head to head! You each play your own board and see each other's live, side by side.")
        Text("• Turn and slide the falling piece to complete rows. A full row disappears.")
        Text("• 🎁 Blow up 2 or more rows at once and the pieces you cleared are sent to your rival as GIFT PIECES. They fall onto their stack by themselves, in gold striped blocks, so they can see they came from you. 2 rows send 1, 3 rows send 2, 4 rows send 4. Gifts you send first cancel gifts waiting to fall on you.")
        Text("• Bonuses: back-to-back four-line clears send an extra gift, and a perfect clear (empty board) sends four.")
        Text("• Modes: ⚔️ Battle (last stack standing), ⏱ Score attack, 🏁 Sprint (first to clear the lines), 💣 Bomb Blitz (bomb pieces blast holes and send the blown-up blocks to your rival), 🌊 Rising Tide (junk rows rise on both boards), 🔥 Turbo (it keeps speeding up), ⚡ Power Surge (clear a row with a star block to dizzy, speed up or fog your rival).")
        Text("• Controls: ◀ ▶ slide, ⟲ ⟳ turn (or tap the board), ⬇ hold to fall faster, ⏬ drop instantly, HOLD saves a piece. You can also swipe on your board: sideways to slide, down to fall faster, a quick flick down to drop.")
    }
}

// ------------------------------------------------------------------ drawing

private fun cellColour(code: Int): Color = when {
    code == TetrisRules.GIFT -> GiftGold
    code == TetrisRules.JUNK -> JunkGrey
    else -> BlockColours[((if (code > TetrisRules.STAR) code - TetrisRules.STAR else code) - 1).coerceIn(0, 6)]
}

private fun DrawScope.starMark(left: Float, top: Float, s: Float, alpha: Float) {
    val c = Offset(left + s / 2, top + s / 2)
    val r = s * 0.30f
    val w = s * 0.09f
    val col = Color.White.copy(alpha = alpha)
    drawLine(col, Offset(c.x - r, c.y), Offset(c.x + r, c.y), strokeWidth = w)
    drawLine(col, Offset(c.x, c.y - r), Offset(c.x, c.y + r), strokeWidth = w)
    val d = r * 0.65f
    drawLine(col, Offset(c.x - d, c.y - d), Offset(c.x + d, c.y + d), strokeWidth = w * 0.7f)
    drawLine(col, Offset(c.x - d, c.y + d), Offset(c.x + d, c.y - d), strokeWidth = w * 0.7f)
    drawCircle(color = Color(0xFFFFE066).copy(alpha = alpha), radius = r * 0.35f, center = c)
}

private fun DrawScope.block(left: Float, top: Float, size: Float, code: Int, alpha: Float = 1f) {
    val pad = size * 0.06f
    val inner = size - 2 * pad
    val corner = CornerRadius(size * 0.18f)
    drawRoundRect(cellColour(code).copy(alpha = alpha), Offset(left + pad, top + pad), Size(inner, inner), corner)
    drawRoundRect(Color.White.copy(alpha = 0.30f * alpha), Offset(left + pad, top + pad), Size(inner, inner * 0.38f), corner)
    if (code == TetrisRules.GIFT) {
        clipRect(left + pad, top + pad, left + size - pad, top + size - pad) {
            val step = size * 0.36f
            var k = -size
            while (k < size * 1.5f) {
                drawLine(GiftPink.copy(alpha = alpha), Offset(left + k, top + size), Offset(left + k + size, top), strokeWidth = size * 0.15f)
                k += step
            }
        }
        drawRoundRect(Color.White.copy(alpha = alpha), Offset(left + pad, top + pad), Size(inner, inner), corner, style = Stroke(width = size * 0.08f))
    } else {
        drawRoundRect(Color.Black.copy(alpha = 0.28f * alpha), Offset(left + pad, top + pad), Size(inner, inner), corner, style = Stroke(width = size * 0.04f))
        if (code == TetrisRules.JUNK) {
            drawLine(Color.Black.copy(alpha = 0.35f * alpha), Offset(left + size * 0.2f, top + size * 0.7f), Offset(left + size * 0.55f, top + size * 0.3f), strokeWidth = size * 0.05f)
        }
    }
    if (TetrisRules.isStar(code)) starMark(left, top, size, alpha)
}

private fun DrawScope.bomb(left: Float, top: Float, s: Float, alpha: Float = 1f, spark: Float = 1f) {
    val r = s * 0.40f
    val c = Offset(left + s / 2, top + s * 0.58f)
    drawCircle(
        brush = Brush.radialGradient(listOf(Color(0xFF8A8A9A), Color(0xFF1A1A26)), center = Offset(c.x - r * 0.35f, c.y - r * 0.35f), radius = r * 1.5f),
        radius = r, center = c, alpha = alpha,
    )
    drawCircle(color = Color.White.copy(alpha = 0.55f * alpha), radius = r * 0.18f, center = Offset(c.x - r * 0.4f, c.y - r * 0.4f))
    drawLine(Color(0xFFD9A441).copy(alpha = alpha), Offset(c.x + r * 0.35f, c.y - r * 0.85f), Offset(c.x + r * 0.75f, c.y - r * 1.3f), strokeWidth = s * 0.07f)
    drawCircle(color = Color(0xFFFF6A00).copy(alpha = alpha * spark), radius = s * 0.10f, center = Offset(c.x + r * 0.8f, c.y - r * 1.35f))
    drawCircle(color = Color(0xFFFFEE88).copy(alpha = alpha * spark), radius = s * 0.05f, center = Offset(c.x + r * 0.8f, c.y - r * 1.35f))
    drawCircle(color = Color(0xFFFF3B3B).copy(alpha = 0.8f * alpha), radius = r, center = c, style = Stroke(width = s * 0.05f))
}

/** One playing well. [cellAt] gives the code at a visible column/row; the falling piece and ghost are drawn on top. */
private fun DrawScope.well(
    cellAt: (Int, Int) -> Int,
    type: Int, rot: Int, px: Int, py: Int, gift: Boolean, star: Int,
    ghostY: Int?,
    dim: Boolean,
    fog: Boolean = false,
    flashRows: List<Int> = emptyList(), flashP: Float = 1f,
    blastX: Int = -1, blastY: Int = -1, blastP: Float = 1f,
    pulse: Float = 1f,
) {
    val cs = size.width / TetrisRules.W
    drawRect(WellBackground)
    for (x in 0..TetrisRules.W) drawLine(Color.White.copy(alpha = 0.05f), Offset(x * cs, 0f), Offset(x * cs, size.height))
    for (y in 0..TetrisRules.H) drawLine(Color.White.copy(alpha = 0.05f), Offset(0f, y * cs), Offset(size.width, y * cs))
    val a = if (dim) 0.45f else 1f
    for (y in 0 until TetrisRules.H) for (x in 0 until TetrisRules.W) {
        val c = cellAt(x, y)
        if (c != 0) block(x * cs, y * cs, cs, c, a)
    }
    if (type in 0..7) {
        val s = TetrisRules.SHAPE[type][rot and 3]
        if (ghostY != null && ghostY != py) {
            for (i in 0 until (if (type == TetrisRules.BOMB_TYPE) 1 else 4)) {
                val yy = ghostY + s[i * 2 + 1] - TetrisRules.HIDDEN
                if (yy >= 0) {
                    drawRoundRect(
                        Color.White.copy(alpha = 0.35f), Offset((px + s[i * 2]) * cs + cs * 0.1f, yy * cs + cs * 0.1f),
                        Size(cs * 0.8f, cs * 0.8f), CornerRadius(cs * 0.15f), style = Stroke(width = cs * 0.08f),
                    )
                }
            }
        }
        if (type == TetrisRules.BOMB_TYPE) {
            val yy = py - TetrisRules.HIDDEN
            if (yy >= 0) bomb(px * cs, yy * cs, cs, a, pulse)
        } else {
            for (i in 0 until 4) {
                val yy = py + s[i * 2 + 1] - TetrisRules.HIDDEN
                if (yy >= 0) {
                    val code = if (gift) TetrisRules.GIFT else type + 1 + (if (i == star) TetrisRules.STAR else 0)
                    block((px + s[i * 2]) * cs, yy * cs, cs, code, a)
                }
            }
        }
    }
    // the white flash where lines just disappeared
    if (flashP < 1f) {
        for (row in flashRows) {
            drawRect(Color.White.copy(alpha = 0.85f * (1f - flashP)), Offset(0f, row * cs), Size(size.width, cs))
        }
    }
    // the bomb blast
    if (blastP < 1f && blastX >= 0) {
        drawExplosion(Offset((blastX + 0.5f) * cs, (blastY + 0.5f) * cs), cs * 3.6f, blastP)
    }
    if (fog) {
        drawRect(
            Brush.verticalGradient(listOf(Color(0xF2C9D6F2), Color(0xB3C9D6F2), Color.Transparent), 0f, size.height * 0.62f),
        )
    }
    drawRect(Color.White.copy(alpha = 0.35f), style = Stroke(width = cs * 0.08f))
}

@Composable
private fun PiecePreview(type: Int, modifier: Modifier = Modifier, dim: Boolean = false) {
    Canvas(modifier.aspectRatio(1f)) {
        drawRoundRect(Color(0x33000000), cornerRadius = CornerRadius(size.width * 0.12f))
        if (type !in 0..7) return@Canvas
        if (type == TetrisRules.BOMB_TYPE) {
            bomb(size.width * 0.1f, size.height * 0.1f, size.width * 0.8f, if (dim) 0.4f else 1f)
            return@Canvas
        }
        val s = TetrisRules.SHAPE[type][0]
        val xs = (0 until 4).map { s[it * 2] }
        val ys = (0 until 4).map { s[it * 2 + 1] }
        val cs = size.width / 5f
        val ox = (size.width - (xs.max() - xs.min() + 1) * cs) / 2f - xs.min() * cs
        val oy = (size.height - (ys.max() - ys.min() + 1) * cs) / 2f - ys.min() * cs
        for (i in 0 until 4) block(ox + s[i * 2] * cs, oy + s[i * 2 + 1] * cs, cs, type + 1, if (dim) 0.4f else 1f)
    }
}

// ------------------------------------------------------------------ controls

@Composable
private fun PadButton(
    label: String,
    modifier: Modifier,
    colours: List<Color>,
    repeat: Boolean = false,
    onHold: ((Boolean) -> Unit)? = null,
    onFire: () -> Unit = {},
) {
    val scope = rememberCoroutineScope()
    val fire by rememberUpdatedState(onFire)
    val hold by rememberUpdatedState(onHold)
    var pressed by remember { mutableStateOf(false) }
    val shape = RoundedCornerShape(18.dp)
    Box(
        modifier
            .clip(shape)
            .background(Brush.verticalGradient(if (pressed) colours.map { it.copy(alpha = 0.75f) } else colours))
            .border(2.dp, Color.White.copy(alpha = if (pressed) 0.8f else 0.35f), shape)
            .pointerInput(Unit) {
                detectTapGestures(
                    onPress = {
                        pressed = true
                        hold?.invoke(true)
                        fire()
                        val job = if (repeat) scope.launch {
                            delay(170)
                            while (isActive) {
                                fire()
                                delay(55)
                            }
                        } else null
                        try {
                            tryAwaitRelease()
                        } finally {
                            job?.cancel()
                            hold?.invoke(false)
                            pressed = false
                        }
                    },
                )
            },
        contentAlignment = Alignment.Center,
    ) { Text(label, fontSize = 20.sp, fontWeight = FontWeight.Black, color = Color.White, textAlign = TextAlign.Center) }
}

private val CtlBlue = listOf(Color(0xFF4B6BFF), Color(0xFF2B3FB8))
private val CtlViolet = listOf(Color(0xFFB44CFF), Color(0xFF6A2BBF))
private val CtlOrange = listOf(Color(0xFFFF8A3D), Color(0xFFD14B1F))
private val CtlTeal = listOf(Color(0xFF16C7B0), Color(0xFF0B7F8F))

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
    val popups = remember { mutableStateListOf<Popup>() }
    var popupId by remember { mutableLongStateOf(0L) }
    val info = TetrisMode.info(s.mode)

    // ---- things that happened on MY board (read from the live board each frame)
    val clearSerial = mine?.clearSerial ?: 0
    val flashP = rememberBurst(clearSerial, 420)
    val blastSerial = mine?.blastSerial ?: 0
    val blastP = rememberBurst(blastSerial, 650)
    LaunchedEffect(clearSerial) {
        val b = mine ?: return@LaunchedEffect
        if (clearSerial == 0 || b.lastClear == 0) return@LaunchedEffect
        val names = listOf("", "SINGLE", "✨ DOUBLE", "✨ TRIPLE", "🏆 TETRIS!")
        popups.add(Popup(popupId++, names[b.lastClear.coerceIn(0, 4)], if (b.lastClear >= 4) Funk.Sun else Funk.Lime, 0.3f, 0.42f))
        if (b.combo >= 1) popups.add(Popup(popupId++, "COMBO ×${b.combo + 1}", Funk.Cyan, 0.3f, 0.55f))
    }
    LaunchedEffect(mine?.b2bSerial ?: 0) { if ((mine?.b2bSerial ?: 0) > 0) popups.add(Popup(popupId++, "🔥 BACK-TO-BACK!", Funk.Sun, 0.3f, 0.3f)) }
    LaunchedEffect(mine?.perfectSerial ?: 0) { if ((mine?.perfectSerial ?: 0) > 0) popups.add(Popup(popupId++, "🏆 PERFECT CLEAR!", Funk.Sun, 0.3f, 0.2f)) }
    LaunchedEffect(blastSerial) { if (blastSerial > 0) popups.add(Popup(popupId++, "💥 BOOM!", Funk.Orange, 0.3f, 0.36f)) }
    LaunchedEffect(mine?.powerSerial ?: 0) {
        val b = mine ?: return@LaunchedEffect
        if (b.powerSerial > 0 && b.lastPower in TetrisRules.POWERS.indices) popups.add(Popup(popupId++, "⚡ ${TetrisRules.POWERS[b.lastPower]}!", Funk.Violet, 0.3f, 0.25f))
    }
    LaunchedEffect(mine?.fxSerial ?: 0) {
        val b = mine ?: return@LaunchedEffect
        if (b.fxSerial > 0) {
            val text = when (b.lastFx) { 0 -> "🌀 DIZZY!"; 1 -> "💨 TURBO CURSE!"; else -> "🌫️ FOG!" }
            popups.add(Popup(popupId++, "$text from ${s.theirName}", Funk.Red, 0.3f, 0.34f))
        }
    }
    // ---- things that happened on the RIVAL's board
    LaunchedEffect(s.clearSeq) {
        if (s.clearSeq == 0 || s.lastClear == 0 || s.lastClearByMe) return@LaunchedEffect
        popups.add(Popup(popupId++, "${s.theirName}: ${s.lastClear} line${if (s.lastClear == 1) "" else "s"}", Funk.Cyan, 0.72f, 0.4f))
    }
    LaunchedEffect(s.giftSeq) {
        if (s.giftSeq > 0) {
            popups.add(Popup(popupId++, "🎁 GIFT LANDING!", Funk.Pink, 0.3f, 0.3f))
            Sfx.play(Sound.BUZZ)
        }
    }
    LaunchedEffect(s.mySent) {
        if (s.mySent > 0) popups.add(Popup(popupId++, "🎁 SENT ${s.mySent}", Funk.Sun, 0.3f, 0.5f))
    }
    LaunchedEffect(s.myLevel) {
        if (mine != null && s.myLevel > mine.startLevel) {
            popups.add(Popup(popupId++, "⬆ LEVEL ${s.myLevel}", Funk.Cyan, 0.5f, 0.2f))
            Sfx.play(Sound.CHIME)
        }
    }

    val curse = mine?.activeCurse() ?: -1
    val fog = curse == 2 || (mine?.fogMs ?: 0L) > 0
    val dizzy = (mine?.dizzyMs ?: 0L) > 0
    val wobble = if (dizzy) sin(frame / 7f) * 2.5f else 0f
    val spark = 0.6f + 0.4f * sin(frame / 5f)

    Box(Modifier.fillMaxSize().background(Brush.verticalGradient(listOf(Color(0xFF140B3A), Color(0xFF06041A))))) {
        BoxWithConstraints(Modifier.fillMaxSize()) {
            val landscape = maxWidth > maxHeight
            val myDim = !s.myAlive && !over
            val theirDim = !s.theirAlive && !over

            val myWell: @Composable (Modifier) -> Unit = { m ->
                Box(m.graphicsLayer { rotationZ = wobble }) {
                    Canvas(
                        Modifier.fillMaxWidth().aspectRatio(0.5f)
                            .pointerInput(engine) { detectTapGestures(onTap = { engine.rotate() }) }
                            .pointerInput(engine) {
                                var acc = 0f
                                var totalDx = 0f
                                var totalDy = 0f
                                var t0 = 0L
                                detectDragGestures(
                                    onDragStart = { acc = 0f; totalDx = 0f; totalDy = 0f; t0 = System.nanoTime() },
                                    onDragEnd = {
                                        val cell = size.width / TetrisRules.W
                                        val ms = (System.nanoTime() - t0) / 1_000_000L
                                        engine.setSoft(false)
                                        if (totalDy > cell * 4 && ms < 450 && abs(totalDx) < cell * 3) {
                                            engine.hardDrop()
                                            Sfx.play(Sound.DROP)
                                        }
                                    },
                                    onDragCancel = { engine.setSoft(false) },
                                    onDrag = { change, drag ->
                                        change.consume()
                                        val cell = size.width / TetrisRules.W
                                        acc += drag.x; totalDx += drag.x; totalDy += drag.y
                                        while (acc > cell) { engine.moveRight(); acc -= cell }
                                        while (acc < -cell) { engine.moveLeft(); acc += cell }
                                        engine.setSoft(drag.y > 1.5f || (totalDy > cell && drag.y >= 0f))
                                    },
                                )
                            },
                    ) {
                        if (mine != null) {
                            well(
                                { x, y -> mine.cell(x, y + TetrisRules.HIDDEN) },
                                mine.type, mine.rot, mine.px, mine.py, mine.isGift, mine.starCell,
                                if (mine.alive && !mine.isGift && !fog) mine.ghostY() else null, myDim, fog,
                                mine.lastClearRows, flashP, mine.blastX, mine.blastY, blastP, spark,
                            )
                        } else {
                            drawRect(WellBackground)
                        }
                    }
                    val banner = when {
                        curse == 0 -> "🌀 DIZZY ${(mine?.dizzyMs ?: 0L) / 1000 + 1}s: left and right swapped!"
                        curse == 1 -> "💨 TURBO CURSE ${(mine?.turboMs ?: 0L) / 1000 + 1}s"
                        curse == 2 -> "🌫️ FOG ${(mine?.fogMs ?: 0L) / 1000 + 1}s"
                        s.mode == TetrisMode.TIDE && mine != null && mine.tideLeftMs < 3000 -> "🌊 THE TIDE IS RISING…"
                        else -> null
                    }
                    if (banner != null) {
                        Text(
                            banner, fontSize = 11.sp, fontWeight = FontWeight.Black, color = Color.White, textAlign = TextAlign.Center,
                            modifier = Modifier.align(Alignment.TopCenter).fillMaxWidth()
                                .background(Funk.Red.copy(alpha = 0.75f)).padding(vertical = 3.dp),
                        )
                    }
                    if (curse == 1) {
                        Box(Modifier.matchParentSize().border(3.dp, Funk.Red.copy(alpha = 0.6f + 0.4f * sin(frame / 4f))))
                    }
                }
            }
            val rivalWell: @Composable (Modifier) -> Unit = { m ->
                Canvas(m.aspectRatio(0.5f)) {
                    if (rival != null) {
                        well(
                            { x, y -> TetrisRules.codeOf(rival.grid[y * TetrisRules.W + x]) },
                            rival.type, rival.rot, rival.x, rival.y, rival.gift, -1, null, theirDim,
                        )
                    } else {
                        drawRect(WellBackground)
                    }
                }
            }
            val timer = when (s.mode) {
                TetrisMode.SCORE -> if (s.secsLeft >= 0) "⏱ ${s.secsLeft / 60}:${"%02d".format(s.secsLeft % 60)}" else "⏱"
                TetrisMode.SPRINT -> "🏁 ${maxOf(s.myLines, s.theirLines)}/${s.target}"
                TetrisMode.TIDE -> "🌊 ${((mine?.tideLeftMs ?: 0L) / 1000 + 1)}s"
                TetrisMode.TURBO -> "🔥 Lv${s.myLevel}"
                TetrisMode.BOMB -> "💣 Blitz"
                TetrisMode.SURGE -> "⚡ Surge"
                else -> "⚔ Battle"
            }
            val pills: @Composable (Modifier) -> Unit = { m ->
                Row(m, horizontalArrangement = Arrangement.spacedBy(6.dp), verticalAlignment = Alignment.CenterVertically) {
                    PlayerPill("You", s.myScore, Funk.Lime, s.myAlive && !over, Modifier.weight(1f), extra = "Lv${s.myLevel}")
                    Text(timer, fontSize = 15.sp, fontWeight = FontWeight.Black, color = Color.White, textAlign = TextAlign.Center)
                    PlayerPill(s.theirName, s.theirScore, Funk.Cyan, s.theirAlive && !over, Modifier.weight(1f), extra = "Lv${s.theirLevel}")
                }
            }
            val incoming: @Composable () -> Unit = {
                val text = when {
                    s.myIncoming > 0 -> "🎁 ×${s.myIncoming} incoming"
                    mine != null && mine.giftsReceived > 0 -> "Gold blocks = gifts from ${s.theirName}"
                    else -> "${s.myLines} lines · sent ${s.mySent} 🎁"
                }
                Text(
                    text, fontSize = 12.sp, fontWeight = FontWeight.Bold, textAlign = TextAlign.Center,
                    color = if (s.myIncoming > 0) Funk.Pink else Color.White.copy(alpha = 0.85f),
                    modifier = Modifier.fillMaxWidth().padding(top = 4.dp),
                )
            }
            val holdAndNext: @Composable () -> Unit = {
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp), verticalAlignment = Alignment.Top) {
                    Column(horizontalAlignment = Alignment.CenterHorizontally, modifier = Modifier.width(50.dp)) {
                        Text("HOLD", fontSize = 10.sp, color = Color.White.copy(alpha = 0.8f), fontWeight = FontWeight.Bold)
                        PiecePreview(if (fog) -1 else (mine?.hold ?: -1), Modifier.fillMaxWidth(), dim = mine?.canHold == false)
                    }
                    Column(horizontalAlignment = Alignment.CenterHorizontally, modifier = Modifier.width(50.dp)) {
                        Text("NEXT", fontSize = 10.sp, color = Color.White.copy(alpha = 0.8f), fontWeight = FontWeight.Bold)
                        (if (fog) listOf(-1, -1, -1) else (mine?.next(3) ?: emptyList())).forEach { PiecePreview(it, Modifier.fillMaxWidth()) }
                    }
                }
            }
            val modeLine: @Composable () -> Unit = {
                Text(
                    "${info.emoji} ${info.label}" + if (s.giftsOn) "" else " · no gifts",
                    fontSize = 11.sp, fontWeight = FontWeight.Bold, color = Funk.Sun, textAlign = TextAlign.Center,
                )
            }

            if (!landscape) {
                val availH = (maxHeight - 230.dp).coerceAtLeast(160.dp)
                val myW = minOf(maxWidth * 0.56f, availH / 2f)
                val rivalW = minOf(maxWidth * 0.30f, ((availH - 120.dp) / 2f).coerceAtLeast(60.dp))
                Column(
                    Modifier.fillMaxSize().padding(horizontal = 8.dp, vertical = 6.dp),
                    verticalArrangement = Arrangement.spacedBy(4.dp),
                    horizontalAlignment = Alignment.CenterHorizontally,
                ) {
                    pills(Modifier.fillMaxWidth())
                    modeLine()
                    Row(Modifier.weight(1f).fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                        Column(Modifier.width(myW)) {
                            myWell(Modifier.width(myW))
                            incoming()
                        }
                        Column(Modifier.weight(1f), horizontalAlignment = Alignment.CenterHorizontally, verticalArrangement = Arrangement.spacedBy(4.dp)) {
                            Text(s.theirName, fontSize = 12.sp, fontWeight = FontWeight.Bold, color = Funk.Cyan, maxLines = 1)
                            rivalWell(Modifier.width(rivalW))
                            Text(
                                if (s.theirIncoming > 0) "🎁 ×${s.theirIncoming} coming" else "${s.theirLines} lines · sent ${s.theirSent} 🎁",
                                fontSize = 10.sp, color = Color.White.copy(alpha = 0.85f), textAlign = TextAlign.Center,
                            )
                            holdAndNext()
                        }
                    }
                    Column(Modifier.fillMaxWidth(), verticalArrangement = Arrangement.spacedBy(6.dp)) {
                        Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                            PadButton("◀", Modifier.weight(1f).height(56.dp), CtlBlue, repeat = true) { engine.moveLeft() }
                            PadButton("⟲", Modifier.weight(1f).height(56.dp), CtlViolet) { engine.rotateBack() }
                            PadButton("⟳", Modifier.weight(1f).height(56.dp), CtlViolet) { engine.rotate() }
                            PadButton("▶", Modifier.weight(1f).height(56.dp), CtlBlue, repeat = true) { engine.moveRight() }
                        }
                        Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                            PadButton("⬇ FAST", Modifier.weight(1f).height(56.dp), CtlTeal, onHold = { engine.setSoft(it) })
                            PadButton("⏬ DROP", Modifier.weight(1.3f).height(56.dp), CtlOrange) { engine.hardDrop(); Sfx.play(Sound.DROP) }
                            PadButton("HOLD", Modifier.weight(1f).height(56.dp), CtlViolet) { engine.hold() }
                        }
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
                    Column(Modifier.width(92.dp).fillMaxHeight(), verticalArrangement = Arrangement.spacedBy(10.dp, Alignment.CenterVertically)) {
                        PadButton("◀", Modifier.fillMaxWidth().height(64.dp), CtlBlue, repeat = true) { engine.moveLeft() }
                        PadButton("▶", Modifier.fillMaxWidth().height(64.dp), CtlBlue, repeat = true) { engine.moveRight() }
                        PadButton("⬇", Modifier.fillMaxWidth().height(64.dp), CtlTeal, onHold = { engine.setSoft(it) })
                    }
                    Column(Modifier.width(myW)) {
                        myWell(Modifier.width(myW))
                    }
                    Column(Modifier.weight(1f).fillMaxHeight(), verticalArrangement = Arrangement.spacedBy(6.dp), horizontalAlignment = Alignment.CenterHorizontally) {
                        pills(Modifier.fillMaxWidth())
                        modeLine()
                        holdAndNext()
                        incoming()
                    }
                    Column(Modifier.width(myW * 0.62f), horizontalAlignment = Alignment.CenterHorizontally, verticalArrangement = Arrangement.spacedBy(4.dp)) {
                        Text(s.theirName, fontSize = 12.sp, fontWeight = FontWeight.Bold, color = Funk.Cyan, maxLines = 1)
                        rivalWell(Modifier.width(myW * 0.62f))
                    }
                    Column(Modifier.width(92.dp).fillMaxHeight(), verticalArrangement = Arrangement.spacedBy(8.dp, Alignment.CenterVertically)) {
                        PadButton("⟲", Modifier.fillMaxWidth().height(52.dp), CtlViolet) { engine.rotateBack() }
                        PadButton("⟳", Modifier.fillMaxWidth().height(52.dp), CtlViolet) { engine.rotate() }
                        PadButton("⏬", Modifier.fillMaxWidth().height(52.dp), CtlOrange) { engine.hardDrop(); Sfx.play(Sound.DROP) }
                        PadButton("HOLD", Modifier.fillMaxWidth().height(52.dp), CtlViolet) { engine.hold() }
                    }
                }
            }
        }

        if (s.countdownMs > 0) {
            Box(Modifier.fillMaxSize().background(Color(0x99000000)), contentAlignment = Alignment.Center) {
                Column(horizontalAlignment = Alignment.CenterHorizontally) {
                    Text("${info.emoji} ${info.label}", fontSize = 24.sp, fontWeight = FontWeight.Black, color = Funk.Sun)
                    Text("${(s.countdownMs + 999) / 1000}", fontSize = 96.sp, fontWeight = FontWeight.Black, color = Color.White)
                    Text(info.blurb, fontSize = 14.sp, color = Color.White.copy(alpha = 0.9f), textAlign = TextAlign.Center, modifier = Modifier.padding(horizontal = 32.dp))
                }
            }
        }
        if (!s.myAlive && !over) {
            Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                Text(
                    if (s.mode == TetrisMode.SCORE) "💥 STACK FULL\nWaiting for the clock…" else "💥 STACK FULL",
                    fontSize = 24.sp, fontWeight = FontWeight.Black, color = Funk.Red, textAlign = TextAlign.Center,
                    modifier = Modifier.background(Color(0xAA000000), RoundedCornerShape(16.dp)).padding(16.dp),
                )
            }
        }
        PopupLayer(popups, { id -> popups.removeAll { it.id == id } })
        if (over) {
            EndOverlay(
                title = when (s.result) { 1 -> "🏆 YOU WIN!"; 2 -> "🏆 ${s.theirName.uppercase()} WINS!"; else -> "🤝 IT'S A DRAW!" },
                subtitle = "You ${s.myScore}  ·  ${s.theirName} ${s.theirScore}",
                win = s.result == 1, isHost = engine.isHost, theirName = s.theirName,
                playAgainLabel = "Rematch", colors = listOf(Color(0xFF6A3DE8), Color(0xFF00B8D9)),
                onPlayAgain = onPlayAgain, onBackToGames = onBackToGames, onLeave = onLeave,
                how = s.how,
            )
        }
    }
}

