package com.neil1454.drawsomething.ui

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.border
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
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.neil1454.drawsomething.core.GPhase
import com.neil1454.drawsomething.core.Sound
import com.neil1454.drawsomething.core.TetrisEngine
import com.neil1454.drawsomething.core.TetrisRules
import com.neil1454.drawsomething.core.TetrisState
import com.neil1454.drawsomething.sound.Sfx
import kotlinx.coroutines.delay
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch

private val BlockColours = listOf(0xFF00E5FF, 0xFFFFD600, 0xFFB44CFF, 0xFF3DDC84, 0xFFFF4D5E, 0xFF3D7BFF, 0xFFFF9E2C).map { Color(it) }
private val GiftGold = Color(0xFFFFC857)
private val GiftPink = Color(0xFFFF3D9A)
private val WellBackground = Color(0xFF0B0A1E)

@Composable
fun TetrisHelp() {
    Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
        Text("Falling blocks, side by side! You each play your own board and see each other's live.")
        Text("• Turn and slide the falling piece to complete rows. A full row disappears.")
        Text("• 🎁 Blow up 2 or more rows at once and the pieces you cleared are sent to your rival as GIFT PIECES. They fall onto their stack by themselves, in gold striped blocks, so they can see they came from you.")
        Text("• 2 rows send 1 gift, 3 rows send 2, 4 rows send 4. A long run of clears adds one more.")
        Text("• Gifts you send first cancel any gifts waiting to fall on you. Gifts only land when you didn't just clear a row.")
        Text("• Battle: the last stack standing wins. Score attack: the best score when time runs out wins.")
        Text("• Controls: ◀ ▶ slide, ⟲ ⟳ turn (or tap the board), ⬇ hold to fall faster, ⏬ drop it instantly, HOLD saves a piece for later.")
    }
}

// ------------------------------------------------------------------ drawing

private fun cellColour(code: Int): Color = if (code == TetrisRules.GIFT) GiftGold else BlockColours[(code - 1).coerceIn(0, 6)]

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
    }
}

/** One playing well. [cellAt] gives the code at a visible column/row; the falling piece and ghost are drawn on top. */
private fun DrawScope.well(
    cellAt: (Int, Int) -> Int,
    type: Int, rot: Int, px: Int, py: Int, gift: Boolean,
    ghostY: Int?,
    dim: Boolean,
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
    if (type in 0..6) {
        val s = TetrisRules.SHAPE[type][rot and 3]
        if (ghostY != null && ghostY != py) {
            for (i in 0 until 4) {
                val yy = ghostY + s[i * 2 + 1] - TetrisRules.HIDDEN
                if (yy >= 0) {
                    drawRoundRect(
                        Color.White.copy(alpha = 0.35f), Offset((px + s[i * 2]) * cs + cs * 0.1f, yy * cs + cs * 0.1f),
                        Size(cs * 0.8f, cs * 0.8f), CornerRadius(cs * 0.15f), style = Stroke(width = cs * 0.08f),
                    )
                }
            }
        }
        for (i in 0 until 4) {
            val yy = py + s[i * 2 + 1] - TetrisRules.HIDDEN
            if (yy >= 0) block((px + s[i * 2]) * cs, yy * cs, cs, if (gift) TetrisRules.GIFT else type + 1, a)
        }
    }
    drawRect(Color.White.copy(alpha = 0.35f), style = Stroke(width = cs * 0.08f))
}

@Composable
private fun PiecePreview(type: Int, modifier: Modifier = Modifier, dim: Boolean = false) {
    Canvas(modifier.aspectRatio(1f)) {
        drawRoundRect(Color(0x33000000), cornerRadius = CornerRadius(size.width * 0.12f))
        if (type !in 0..6) return@Canvas
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
    @Suppress("UNUSED_VARIABLE") val redraw = frame // every frame redraws the live boards
    val s = engine.state
    val mine = engine.board
    val rival = engine.opp
    val over = s.phase == GPhase.OVER
    val popups = remember { mutableStateListOf<Popup>() }
    var popupId by remember { mutableLongStateOf(0L) }

    LaunchedEffect(s.clearSeq) {
        if (s.clearSeq == 0 || s.lastClear == 0) return@LaunchedEffect
        val names = listOf("", "SINGLE", "DOUBLE", "TRIPLE", "TETRIS!")
        val text = names[s.lastClear.coerceIn(0, 4)]
        if (s.lastClearByMe) {
            popups.add(Popup(popupId++, text, if (s.lastClear >= 4) Funk.Sun else Funk.Lime, 0.3f, 0.4f))
        } else {
            popups.add(Popup(popupId++, "${s.theirName}: ${text.lowercase()}", Funk.Cyan, 0.7f, 0.4f))
        }
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

    Box(Modifier.fillMaxSize().background(Brush.verticalGradient(listOf(Color(0xFF140B3A), Color(0xFF06041A))))) {
        BoxWithConstraints(Modifier.fillMaxSize()) {
            val landscape = maxWidth > maxHeight
            val myDim = !s.myAlive && !over
            val theirDim = !s.theirAlive && !over

            val myWell: @Composable (Modifier) -> Unit = { m ->
                Canvas(
                    m.aspectRatio(0.5f).pointerInput(engine) { detectTapGestures(onTap = { engine.rotate() }) },
                ) {
                    if (mine != null) {
                        well(
                            { x, y -> mine.cell(x, y + TetrisRules.HIDDEN) },
                            mine.type, mine.rot, mine.px, mine.py, mine.isGift,
                            if (mine.alive && !mine.isGift) mine.ghostY() else null, myDim,
                        )
                    } else {
                        drawRect(WellBackground)
                    }
                }
            }
            val rivalWell: @Composable (Modifier) -> Unit = { m ->
                Canvas(m.aspectRatio(0.5f)) {
                    if (rival != null) {
                        well(
                            { x, y -> val c = rival.grid[y * TetrisRules.W + x]; if (c == '.') 0 else c - '0' },
                            rival.type, rival.rot, rival.x, rival.y, rival.gift, null, theirDim,
                        )
                    } else {
                        drawRect(WellBackground)
                    }
                }
            }
            val timer = if (s.mode == 1 && s.secsLeft >= 0) "⏱ ${s.secsLeft / 60}:${"%02d".format(s.secsLeft % 60)}" else "⚔ Battle"
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
                        PiecePreview(mine?.hold ?: -1, Modifier.fillMaxWidth(), dim = mine?.canHold == false)
                    }
                    Column(horizontalAlignment = Alignment.CenterHorizontally, modifier = Modifier.width(50.dp)) {
                        Text("NEXT", fontSize = 10.sp, color = Color.White.copy(alpha = 0.8f), fontWeight = FontWeight.Bold)
                        (mine?.next(3) ?: emptyList()).forEach { PiecePreview(it, Modifier.fillMaxWidth()) }
                    }
                }
            }

            if (!landscape) {
                val availH = (maxHeight - 214.dp).coerceAtLeast(160.dp)
                val myW = minOf(maxWidth * 0.56f, availH / 2f)
                val rivalW = minOf(maxWidth * 0.30f, ((availH - 120.dp) / 2f).coerceAtLeast(60.dp))
                Column(
                    Modifier.fillMaxSize().padding(horizontal = 8.dp, vertical = 6.dp),
                    verticalArrangement = Arrangement.spacedBy(6.dp),
                    horizontalAlignment = Alignment.CenterHorizontally,
                ) {
                    pills(Modifier.fillMaxWidth())
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
                Text("${(s.countdownMs + 999) / 1000}", fontSize = 96.sp, fontWeight = FontWeight.Black, color = Color.White)
            }
        }
        if (!s.myAlive && !over) {
            Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                Text(
                    if (s.mode == 1) "💥 STACK FULL\nWaiting for the clock…" else "💥 STACK FULL",
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
