package com.neil1454.drawsomething.ui

import android.app.Activity
import android.content.pm.ActivityInfo
import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.foundation.gestures.detectTransformGestures
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.ui.geometry.Rect
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.drawscope.DrawScope
import androidx.compose.ui.graphics.drawscope.withTransform
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.layout.onSizeChanged
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.TextMeasurer
import kotlinx.coroutines.launch
import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.animateIntAsState
import androidx.compose.runtime.mutableLongStateOf
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.animation.core.LinearEasing
import androidx.compose.animation.core.tween
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.FilterChip
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateListOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.drawText
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.rememberTextMeasurer
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.Constraints
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.neil1454.drawsomething.core.GPhase
import com.neil1454.drawsomething.core.MonopolyBoard
import com.neil1454.drawsomething.core.MonopolyEngine
import com.neil1454.drawsomething.core.MonopolyRules
import com.neil1454.drawsomething.core.MonopolyState
import com.neil1454.drawsomething.core.Sound
import com.neil1454.drawsomething.core.SqKind
import com.neil1454.drawsomething.core.TradeOffer
import com.neil1454.drawsomething.sound.Sfx
import kotlinx.coroutines.delay

@Composable
fun MonopolyHelp() {
    Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
        Text("Monopoly on the UK (London) board, for two. Everyone starts with £1500 and collects £200 each time they pass GO.")
        Text("• To start, each player rolls once and the highest total goes first (a tie is rolled again).")
        Text("• Roll the dice and move. Buy the property you land on, or let it go to auction (either player can bid, from £1). Land on someone else's and pay rent.")
        Text("• Official rules: you can buy from your very first go. The lobby has a popular HOUSE RULE, on by default, that you must go all the way round the board once (collecting from GO) before you can buy. Switch it off for the book rules.")
        Text("• Own every street of a colour to build houses (evenly), then hotels. Rent shoots up. Stations and utilities pay more the more you own.")
        Text("• Chance and Community Chest cards, Income Tax, Super Tax, and Jail (pay £50, use a card, or roll a double). Three doubles in a row sends you to Jail.")
        Text("• Short of cash? Mortgage property (half its price) or sell buildings back for half. Lifting a mortgage costs the loan plus 10%. You can also trade properties and cash; whoever receives a mortgaged property pays the bank 10% interest at once.")
        Text("• In jail you can still collect rent, build, trade and mortgage. Get out by paying £50 or using a card before you roll, or by rolling a double; after three failed tries you pay £50 and move. Free Parking is just a rest.")
        Text("• If you can't pay what you owe, you're bankrupt and the other player wins. Shorter games end after a set number of turns: the richest player wins.")
        Text("• Tap any square on the board to read its deed card.")
    }
}

private val HostCol = Color(0xFF3B82F6)
private val GuestCol = Color(0xFFF59E0B)
private val BoardGreen = Color(0xFFCDE6D0)

private fun groupColour(g: Int): Color = Color(MonopolyBoard.GROUP_COLOURS[g.coerceIn(0, 7)])
private fun money(n: Int) = "£" + java.text.NumberFormat.getIntegerInstance().format(n)
private fun die(n: Int) = "⚀⚁⚂⚃⚄⚅".getOrElse(n - 1) { '·' }.toString()

// ============================================================================ geometry
//
// The board is drawn in a fixed 1000 x 1000 space and scaled to fit. The corners are 1.5 times the size of the other
// squares, like the real board. Every square is drawn in its own little frame in which "up" points at the middle of the
// board, so the names read the right way for whichever side you are looking at.

private const val BS = 1000f
private const val UNIT = BS / 12f
private const val CORNER = UNIT * 1.5f

/** 0 bottom row, 1 left column, 2 top row, 3 right column, -1 corner. */
private fun sideOf(i: Int): Int = when {
    i % 10 == 0 -> -1
    i < 10 -> 0
    i < 20 -> 1
    i < 30 -> 2
    else -> 3
}

private fun cellRect(i: Int): Rect = when {
    i == 0 -> Rect(BS - CORNER, BS - CORNER, BS, BS)
    i < 10 -> Rect(BS - CORNER - i * UNIT, BS - CORNER, BS - CORNER - (i - 1) * UNIT, BS)
    i == 10 -> Rect(0f, BS - CORNER, CORNER, BS)
    i < 20 -> Rect(0f, BS - CORNER - (i - 10) * UNIT, CORNER, BS - CORNER - (i - 11) * UNIT)
    i == 20 -> Rect(0f, 0f, CORNER, CORNER)
    i < 30 -> Rect(CORNER + (i - 21) * UNIT, 0f, CORNER + (i - 20) * UNIT, CORNER)
    i == 30 -> Rect(BS - CORNER, 0f, BS, CORNER)
    else -> Rect(BS - CORNER, CORNER + (i - 31) * UNIT, BS, CORNER + (i - 30) * UNIT)
}

private fun cellCentre(i: Int): Offset = cellRect(i).center

/** Where a token stands on square [i], nudged so two tokens on one square don't hide each other. */
private fun tokenSpot(pos: Float, nudge: Float): Offset {
    val a = pos.toInt() % MonopolyBoard.SIZE
    val b = (a + 1) % MonopolyBoard.SIZE
    val f = pos - pos.toInt()
    val ca = cellCentre(a)
    val cb = cellCentre(b)
    return Offset(ca.x + (cb.x - ca.x) * f + nudge, ca.y + (cb.y - ca.y) * f + nudge * 0.6f)
}

@Composable
fun MonopolyScreen(
    s: MonopolyState,
    engine: MonopolyEngine,
    onPlayAgain: () -> Unit,
    onBackToGames: () -> Unit,
    onLeave: () -> Unit,
) {
    val over = s.phase == GPhase.OVER
    val myCol = if (s.iAmHost) HostCol else GuestCol
    val theirCol = if (s.iAmHost) GuestCol else HostCol
    val myToken = if (s.iAmHost) "🎩" else "🚗"
    val theirToken = if (s.iAmHost) "🚗" else "🎩"
    var deedSq by remember { mutableIntStateOf(-1) }
    var showManage by remember { mutableStateOf(false) }
    var showTrade by remember { mutableStateOf(false) }
    var confirmBankrupt by remember { mutableStateOf(false) }
    var cardShown by remember { mutableIntStateOf(-1) }
    var buildSq by remember { mutableIntStateOf(-1) }

    // Monopoly is a landscape game: the board needs the width. Turn the phone and everything resizes to fit.
    val activity = LocalContext.current as? Activity
    DisposableEffect(activity) {
        activity?.requestedOrientation = ActivityInfo.SCREEN_ORIENTATION_SENSOR_LANDSCAPE
        onDispose { activity?.requestedOrientation = ActivityInfo.SCREEN_ORIENTATION_UNSPECIFIED }
    }

    // sounds and the card pop-up
    LaunchedEffect(s.seq) {
        if (s.seq == 0) return@LaunchedEffect
        when (s.ev) {
            "roll" -> Sfx.play(Sound.ROLL)
            "buy" -> Sfx.play(Sound.COIN)
            "rent" -> Sfx.play(if (s.evByMe) Sound.BAD else Sound.COIN)
            "tax" -> Sfx.play(Sound.BAD)
            "go" -> Sfx.play(Sound.CHIME)
            "card" -> { Sfx.play(Sound.CARD); cardShown = s.seq }
            "jail" -> Sfx.play(Sound.BUZZ)
            "build" -> Sfx.play(Sound.DROP)
            "sell", "mortgage" -> Sfx.play(Sound.TAP)
            "auction" -> Sfx.play(Sound.POP)
            "trade" -> Sfx.play(Sound.GOOD)
            "bankrupt" -> Sfx.play(Sound.EXPLODE)
        }
        if (s.ev == "card") {
            delay(4200)
            if (cardShown == s.seq) cardShown = -1
        }
    }
    LaunchedEffect(s.trade) { if (s.trade != null && s.trade.from == 2) showTrade = false }

    BoxWithConstraints(Modifier.fillMaxSize().background(Brush.verticalGradient(listOf(Color(0xFF0E2A1F), Color(0xFF071810))))) {
        // the board takes the full height; the controls get the rest of the width
        val boardSide = minOf(maxHeight - 12.dp, maxWidth * 0.62f)
        Row(Modifier.fillMaxSize().padding(6.dp), horizontalArrangement = Arrangement.spacedBy(10.dp)) {
            Box(Modifier.size(boardSide).align(Alignment.CenterVertically)) {
                MonopolyBoardView(s, over, myCol, theirCol, myToken, theirToken, onRoll = { Sfx.play(Sound.TAP); engine.roll() }) { sq ->
                    if (s.owner[sq] == 1 && MonopolyBoard.squares[sq].kind == SqKind.PROPERTY && !over) buildSq = sq else deedSq = sq
                }
                EventStage(s, Modifier.align(Alignment.TopCenter))
                DeedOffer(s, engine, Modifier.align(Alignment.Center))
                SetBanner(s, Modifier.align(Alignment.TopCenter))
                if (buildSq >= 0 && s.owner[buildSq] == 1 && !over) {
                    BuildCard(s, engine, buildSq, onClose = { buildSq = -1 }, onDeed = { deedSq = buildSq; buildSq = -1 }, modifier = Modifier.align(Alignment.Center))
                }
                RentReceipt(s, myCol, theirCol, myToken, theirToken, Modifier.fillMaxSize())
                if (cardShown == s.seq && s.cardText.isNotBlank() && s.ev == "card") {
                    CardPopup(s, Modifier.align(Alignment.Center).clickable { cardShown = -1 })
                }
            }
            Column(
                Modifier.weight(1f).fillMaxSize().padding(top = 30.dp).verticalScroll(rememberScrollState()),
                verticalArrangement = Arrangement.spacedBy(8.dp), horizontalAlignment = Alignment.CenterHorizontally,
            ) {
                PlayerRow(s, myCol, theirCol, myToken, theirToken)
                ActionBar(s, engine, over, onManage = { showManage = true }, onTrade = { showTrade = true }, onBankrupt = { confirmBankrupt = true })
                AdviceChip(s, onManage = { showManage = true }, onTrade = { if (s.canTrade) showTrade = true })
                LogBox(s)
                DeedTracker(s, myCol, theirCol, myToken, theirToken) { deedSq = it }
                Text("Pinch the board to zoom · drag to move · double-tap to zoom in or out · tap a square for its deed", fontSize = 10.sp, color = Color.White.copy(alpha = 0.55f), textAlign = TextAlign.Center)
                Spacer(Modifier.height(16.dp))
            }
        }

        // ---- dialogs
        if (deedSq >= 0) DeedDialog(s, deedSq, myCol, theirCol) { deedSq = -1 }
        if (showManage && !over) ManageDialog(s, engine) { showManage = false }
        if (showTrade && !over && s.canTrade) TradeDialog(s, engine) { showTrade = false }
        if (s.trade != null && !over) TradeResponse(s, engine)
        if (s.step == "AUCTION" && !over) AuctionDialog(s, engine)
        if (confirmBankrupt) {
            AlertDialog(
                onDismissRequest = { confirmBankrupt = false },
                title = { Text("Declare bankruptcy?") },
                text = { Text("You will lose the game. Everything you own goes to ${if (s.debtTo == 2) s.theirName else "the bank"}.") },
                confirmButton = { TextButton(onClick = { confirmBankrupt = false; engine.declareBankrupt() }) { Text("Yes, I'm bankrupt") } },
                dismissButton = { TextButton(onClick = { confirmBankrupt = false }) { Text("Keep fighting") } },
            )
        }
        if (over) {
            val iWon = s.result == 1
            EndOverlay(
                title = when (s.result) { 1 -> "🏆 YOU WIN!"; 2 -> "🏆 ${s.theirName.uppercase()} WINS!"; else -> "🤝 IT'S A DRAW!" },
                subtitle = "Worth: you ${money(s.myWorth)}  ·  ${s.theirName} ${money(s.theirWorth)}",
                how = if (s.why == "bankrupt")
                    (if (iWon) s.theirName else "You") + " couldn't pay what was owed and went bankrupt, so " + (if (iWon) "you take" else "${s.theirName} takes") + " the win."
                else "After ${s.maxTurns} turns each, " + (if (s.result == 3) "you were exactly level on cash, property and buildings."
                else (if (iWon) "you" else s.theirName) + " were worth the most (cash, property and buildings): ${money(maxOf(s.myWorth, s.theirWorth))} against ${money(minOf(s.myWorth, s.theirWorth))}."),
                win = iWon, isHost = engine.isHost, theirName = s.theirName,
                playAgainLabel = "Play again", colors = listOf(Color(0xFFC62828), Color(0xFF2E7D32)),
                onPlayAgain = onPlayAgain, onBackToGames = onBackToGames, onLeave = onLeave,
            )
        }
    }
}

// ============================================================================ the board

/** The board with pinch-to-zoom and drag. The board itself and the animated tokens are two layers sharing one transform. */
@Composable
private fun MonopolyBoardView(s: MonopolyState, over: Boolean, myCol: Color, theirCol: Color, myToken: String, theirToken: String, onRoll: () -> Unit, onTapSquare: (Int) -> Unit) {
    val measurer = rememberTextMeasurer(cacheSize = 320)
    var zoom by remember { mutableFloatStateOf(1f) }
    var pan by remember { mutableStateOf(Offset.Zero) }
    var viewPx by remember { mutableFloatStateOf(1f) }
    var hints by remember { mutableStateOf(true) }
    val shape = RoundedCornerShape(14.dp)

    fun clamp(p: Offset, z: Float): Offset = Offset(p.x.coerceIn(viewPx * (1f - z), 0f), p.y.coerceIn(viewPx * (1f - z), 0f))

    Box(
        Modifier.fillMaxSize().clip(shape).border(3.dp, Color(0xFF3E2A14), shape)
            .onSizeChanged { viewPx = it.width.toFloat().coerceAtLeast(1f) }
            .pointerInput(Unit) {
                detectTransformGestures { centroid, panChange, zoomChange, _ ->
                    val z = (zoom * zoomChange).coerceIn(1f, 4f)
                    val anchored = centroid - (centroid - pan) * (z / zoom)
                    zoom = z
                    pan = clamp(anchored + panChange, z)
                }
            }
            .pointerInput(s.canRoll || s.canFirstRoll) {
                detectTapGestures(
                    onDoubleTap = { tap ->
                        val z = if (zoom > 1.2f) 1f else 2.4f
                        val anchored = tap - (tap - pan) * (z / zoom)
                        zoom = z
                        pan = clamp(anchored, z)
                    },
                    onTap = { tap ->
                        val k = viewPx / BS
                        val bx = (tap.x - pan.x) / zoom / k
                        val by = (tap.y - pan.y) / zoom / k
                        val dx = bx - BS / 2f
                        val dy = by - (BS / 2f + 150f)
                        if ((s.canRoll || s.canFirstRoll) && dx * dx + dy * dy < 160f * 160f) {
                            onRoll()
                        } else {
                            val i = (0 until MonopolyBoard.SIZE).firstOrNull { cellRect(it).contains(Offset(bx, by)) }
                            if (i != null) onTapSquare(i)
                        }
                    },
                )
            },
    ) {
        Canvas(Modifier.fillMaxSize()) {
            val k = size.width / BS
            withTransform({ translate(pan.x, pan.y); scale(zoom * k, zoom * k, Offset.Zero) }) {
                drawBoardBase(measurer)
                for (i in 0 until MonopolyBoard.SIZE) drawCell(s, i, measurer, myCol, theirCol, myToken, theirToken)
                drawCentre(s, over, measurer)
            }
        }
        TokensLayer(s, myToken, theirToken, myCol, theirCol, zoom, pan, viewPx, measurer, hints)
        Box(
            Modifier.align(Alignment.TopEnd).padding(6.dp).clip(RoundedCornerShape(10.dp)).background(Color(0xAA000000))
                .clickable { hints = !hints }.padding(horizontal = 10.dp, vertical = 5.dp),
        ) { Text(if (hints) "🎯 Set hints: ON" else "🎯 Set hints: OFF", color = Color.White, fontSize = 11.sp, fontWeight = FontWeight.Bold) }
        if (zoom > 1.05f) {
            Box(
                Modifier.align(Alignment.TopStart).padding(6.dp).clip(RoundedCornerShape(10.dp)).background(Color(0xAA000000))
                    .clickable { zoom = 1f; pan = Offset.Zero }.padding(horizontal = 10.dp, vertical = 5.dp),
            ) { Text("⤢ fit", color = Color.White, fontSize = 12.sp, fontWeight = FontWeight.Bold) }
        }
    }
}

private val Felt1 = Color(0xFF1F6B45)
private val Felt2 = Color(0xFF134A2F)
private val CardCream = Color(0xFFF7F2E1)
private val Ink = Color(0xFF1A1A1A)

private fun DrawScope.drawBoardBase(m: TextMeasurer) {
    drawRect(Color(0xFF2B1B0E))
    drawRect(Color(0xFF3E2A14), Offset(4f, 4f), Size(BS - 8f, BS - 8f))
    drawRect(Brush.radialGradient(listOf(Felt1, Felt2), Offset(BS / 2, BS / 2), BS * 0.6f), Offset(CORNER, CORNER), Size(BS - 2 * CORNER, BS - 2 * CORNER))
}

private fun DrawScope.text(m: TextMeasurer, s: String, centre: Offset, size: Float, colour: Color = Ink, weight: FontWeight = FontWeight.Bold, maxW: Float = 0f, maxLines: Int = 3, topAligned: Boolean = false) {
    val c = if (maxW > 0f) Constraints(maxWidth = maxW.toInt().coerceAtLeast(4)) else Constraints()
    val r = m.measure(s, TextStyle(fontSize = size.toSp(), fontWeight = weight, color = colour, textAlign = TextAlign.Center, lineHeight = (size * 1.08f).toSp()), maxLines = maxLines, overflow = TextOverflow.Ellipsis, constraints = c)
    drawText(r, topLeft = Offset(centre.x - r.size.width / 2f, if (topAligned) centre.y else centre.y - r.size.height / 2f))
}

/** One square, drawn in its own frame so the writing faces the right way. */
private fun DrawScope.drawCell(s: MonopolyState, i: Int, m: TextMeasurer, myCol: Color, theirCol: Color, myTok: String, theirTok: String) {
    val sq = MonopolyBoard.squares[i]
    val side = sideOf(i)
    val r = cellRect(i)
    val deg = when (side) { 1 -> 90f; 2 -> 180f; 3 -> 270f; else -> 0f }
    val w = if (side == -1) CORNER else UNIT
    val h = CORNER
    withTransform({ translate(r.center.x, r.center.y); rotate(deg, Offset.Zero) }) {
        val left = -w / 2f
        val top = -h / 2f
        drawRect(CardCream, Offset(left, top), Size(w, h))
        if (side == -1) {
            drawCorner(i, w, m)
        } else {
            // the colour band, with houses or a hotel sitting on it
            if (sq.kind == SqKind.PROPERTY) {
                val bh = h * 0.2f
                drawRect(groupColour(sq.group), Offset(left, top), Size(w, bh))
                drawRect(Color.Black.copy(alpha = 0.55f), Offset(left, top + bh - 1.5f), Size(w, 1.5f))
                val hs = s.houses[i]
                if (hs in 1..4) for (k in 0 until hs) drawHouse(Offset(left + w * (0.14f + 0.24f * k), top + bh * 0.52f), bh * 0.36f)
                else if (hs == 5) drawHotel(Offset(0f, top + bh * 0.52f), bh * 0.4f)
            }
            val areaTop = top + (if (sq.kind == SqKind.PROPERTY) h * 0.22f else h * 0.06f)
            val icon = when (sq.kind) {
                SqKind.STATION -> "🚂"
                SqKind.UTILITY -> if (sq.name.startsWith("Electric")) "💡" else "🚰"
                SqKind.TAX -> if (sq.name.startsWith("Income")) "💰" else "💎"
                SqKind.CHANCE -> "❓"
                SqKind.CHEST -> "🎁"
                else -> ""
            }
            val name = when (sq.kind) {
                SqKind.CHANCE -> "CHANCE"
                SqKind.CHEST -> "COMMUNITY CHEST"
                else -> sq.name.uppercase()
            }
            text(m, name, Offset(0f, areaTop), w * 0.155f, if (sq.kind == SqKind.CHANCE) Color(0xFFD97706) else Ink, maxW = w * 0.94f, maxLines = 4, topAligned = true)
            val owned = s.owner[i] != 0
            if (icon.isNotEmpty()) text(m, icon, Offset(0f, top + h * (if (owned) 0.5f else 0.6f)), w * 0.52f)
            if (sq.kind == SqKind.TAX) text(m, "PAY £${sq.rents[0]}", Offset(0f, top + h * 0.84f), w * 0.15f)
            if (sq.price > 0) text(m, "£${sq.price}", Offset(0f, top + h * (if (owned) 0.7f else 0.88f)), w * 0.17f, Color(0xFF3A3A3A), FontWeight.SemiBold)
            // owner: the whole square is washed in their colour, with a thick border and a badge showing their token
            val o = s.owner[i]
            if (o != 0) {
                val col = if (o == 1) myCol else theirCol
                drawRect(col.copy(alpha = 0.2f), Offset(left, top), Size(w, h))
                val bh = h * 0.2f
                drawRect(col, Offset(left, top + h - bh), Size(w, bh))
                text(m, if (o == 1) myTok else theirTok, Offset(0f, top + h - bh / 2f), bh * 0.78f)
                drawRect(col, Offset(left + 2.5f, top + 2.5f), Size(w - 5f, h - 5f), style = Stroke(5f))
            }
            if (s.mortgaged[i]) {
                drawRect(Color(0xCC4B5563), Offset(left, top), Size(w, h))
                text(m, "MORTGAGED", Offset(0f, 0f), w * 0.15f, Color.White)
            }
        }
        drawRect(Color(0xFF2F4F3F), Offset(left, top), Size(w, h), style = Stroke(1.6f))
    }
}

private fun DrawScope.drawHouse(c: Offset, size: Float) {
    drawRect(Color(0xFF1B8A3A), Offset(c.x - size * 0.45f, c.y - size * 0.1f), Size(size * 0.9f, size * 0.7f))
    val roof = Path().apply { moveTo(c.x - size * 0.6f, c.y - size * 0.05f); lineTo(c.x, c.y - size * 0.65f); lineTo(c.x + size * 0.6f, c.y - size * 0.05f); close() }
    drawPath(roof, Color(0xFF0F5C25))
    drawRect(Color(0xFFFFF3B0), Offset(c.x - size * 0.1f, c.y + size * 0.1f), Size(size * 0.2f, size * 0.25f))
}

private fun DrawScope.drawHotel(c: Offset, size: Float) {
    drawRoundRect(Color(0xFFD62828), Offset(c.x - size * 1.2f, c.y - size * 0.5f), Size(size * 2.4f, size * 1.1f), CornerRadius(size * 0.2f))
    for (k in 0 until 4) drawRect(Color(0xFFFFF3B0), Offset(c.x - size * 0.95f + k * size * 0.5f, c.y - size * 0.25f), Size(size * 0.28f, size * 0.3f))
}

private fun DrawScope.drawCorner(i: Int, w: Float, m: TextMeasurer) {
    val left = -w / 2f
    val top = -w / 2f
    when (i) {
        0 -> {
            drawRect(Brush.linearGradient(listOf(Color(0xFFFDF2F2), Color(0xFFF8D4D4)), Offset(left, top), Offset(left + w, top + w)), Offset(left, top), Size(w, w))
            text(m, "COLLECT £200 SALARY AS YOU PASS", Offset(0f, top + w * 0.08f), w * 0.1f, Ink, maxW = w * 0.9f, topAligned = true)
            text(m, "GO", Offset(0f, w * 0.02f), w * 0.42f, Color(0xFFD62828), FontWeight.Black)
            val arrow = Path().apply {
                moveTo(left + w * 0.12f, top + w * 0.82f); lineTo(left + w * 0.34f, top + w * 0.7f); lineTo(left + w * 0.34f, top + w * 0.78f)
                lineTo(left + w * 0.88f, top + w * 0.78f); lineTo(left + w * 0.88f, top + w * 0.86f); lineTo(left + w * 0.34f, top + w * 0.86f)
                lineTo(left + w * 0.34f, top + w * 0.94f); close()
            }
            drawPath(arrow, Color(0xFFD62828))
        }
        10 -> {
            drawRect(Color(0xFFFFE0B2), Offset(left, top), Size(w, w))
            drawRect(Color(0xFFF59E0B), Offset(left + w * 0.05f, top + w * 0.05f), Size(w * 0.58f, w * 0.58f))
            for (k in 1..4) drawRect(Color(0xFF3B2A12), Offset(left + w * (0.05f + 0.115f * k), top + w * 0.05f), Size(w * 0.03f, w * 0.58f))
            text(m, "IN JAIL", Offset(left + w * 0.34f, top + w * 0.33f), w * 0.11f, Color.White)
            text(m, "JUST\nVISITING", Offset(left + w * 0.5f, top + w * 0.8f), w * 0.12f, Ink, maxW = w * 0.9f)
        }
        20 -> {
            drawRect(Color(0xFFFFF1F1), Offset(left, top), Size(w, w))
            text(m, "FREE", Offset(0f, top + w * 0.05f), w * 0.2f, Color(0xFFD62828), FontWeight.Black, topAligned = true)
            text(m, "🚗", Offset(0f, 0f), w * 0.4f)
            text(m, "PARKING", Offset(0f, top + w * 0.72f), w * 0.2f, Color(0xFFD62828), FontWeight.Black, topAligned = true)
        }
        else -> {
            drawRect(Color(0xFFE3F0FF), Offset(left, top), Size(w, w))
            text(m, "GO TO", Offset(0f, top + w * 0.05f), w * 0.18f, Color(0xFF1E4DB7), FontWeight.Black, topAligned = true)
            text(m, "👮", Offset(0f, 0f), w * 0.4f)
            text(m, "JAIL", Offset(0f, top + w * 0.72f), w * 0.22f, Color(0xFF1E4DB7), FontWeight.Black, topAligned = true)
        }
    }
}

/** The middle of the board: title banner, the two card piles and the latest news. The dice live here too. */
private fun DrawScope.drawCentre(s: MonopolyState, over: Boolean, m: TextMeasurer) {
    val c = Offset(BS / 2, BS / 2)
    val inner = BS - 2 * CORNER
    // card piles
    withTransform({ translate(CORNER + inner * 0.2f, CORNER + inner * 0.2f); rotate(135f, Offset.Zero) }) {
        for (k in 2 downTo 0) drawRoundRect(Color(0xFFB45309).copy(alpha = 0.5f + 0.25f * (2 - k)), Offset(-120f + k * 3f, -70f - k * 3f), Size(240f, 140f), CornerRadius(14f))
        drawRoundRect(Color(0xFFFB923C), Offset(-120f, -70f), Size(240f, 140f), CornerRadius(14f))
        drawRoundRect(Color(0xFF7C2D12), Offset(-120f, -70f), Size(240f, 140f), CornerRadius(14f), style = Stroke(4f))
        text(m, "CHANCE", Offset(0f, -30f), 34f, Color(0xFF7C2D12), FontWeight.Black)
        text(m, "?", Offset(0f, 22f), 70f, Color(0xFF7C2D12), FontWeight.Black)
    }
    withTransform({ translate(CORNER + inner * 0.8f, CORNER + inner * 0.8f); rotate(-45f, Offset.Zero) }) {
        for (k in 2 downTo 0) drawRoundRect(Color(0xFF0369A1).copy(alpha = 0.5f + 0.25f * (2 - k)), Offset(-120f + k * 3f, -70f - k * 3f), Size(240f, 140f), CornerRadius(14f))
        drawRoundRect(Color(0xFF38BDF8), Offset(-120f, -70f), Size(240f, 140f), CornerRadius(14f))
        drawRoundRect(Color(0xFF0C4A6E), Offset(-120f, -70f), Size(240f, 140f), CornerRadius(14f), style = Stroke(4f))
        text(m, "COMMUNITY\nCHEST", Offset(0f, -22f), 28f, Color(0xFF0C4A6E), FontWeight.Black)
        text(m, "🎁", Offset(0f, 38f), 44f)
    }
    // the banner
    withTransform({ translate(c.x, c.y - inner * 0.1f); rotate(-35f, Offset.Zero) }) {
        drawRoundRect(Color(0xFF7F1010), Offset(-300f, -62f), Size(600f, 132f), CornerRadius(14f))
        drawRoundRect(Color(0xFFD62828), Offset(-296f, -66f), Size(600f, 132f), CornerRadius(14f))
        drawRoundRect(Color.White, Offset(-282f, -52f), Size(572f, 104f), CornerRadius(10f), style = Stroke(5f))
        text(m, "MONOPOLY", Offset(0f, 0f), 84f, Color.White, FontWeight.Black)
    }
    text(m, "LONDON EDITION", Offset(c.x, c.y + inner * 0.03f), 22f, Color.White.copy(alpha = 0.8f))
    // the latest news
    val line = s.log.lastOrNull().orEmpty()
    if (line.isNotBlank()) {
        drawRoundRect(Color(0x66000000), Offset(c.x - inner * 0.42f, c.y + inner * 0.34f), Size(inner * 0.84f, 70f), CornerRadius(14f))
        text(m, line, Offset(c.x, c.y + inner * 0.34f + 35f), 24f, Color.White, FontWeight.SemiBold, maxW = inner * 0.8f, maxLines = 2)
    }
}

private fun DrawScope.drawDice(c: Offset, size: Float, value: Int, angle: Float, lift: Float) {
    withTransform({ translate(c.x, c.y - lift); rotate(angle, Offset.Zero) }) {
        drawRoundRect(Color.Black.copy(alpha = 0.35f), Offset(-size / 2 + 4f, -size / 2 + 8f + lift * 0.6f), Size(size, size), CornerRadius(size * 0.2f))
        drawRoundRect(Brush.linearGradient(listOf(Color.White, Color(0xFFD9DEEA)), Offset(-size / 2, -size / 2), Offset(size / 2, size / 2)), Offset(-size / 2, -size / 2), Size(size, size), CornerRadius(size * 0.2f))
        drawRoundRect(Color(0xFF8B94AB), Offset(-size / 2, -size / 2), Size(size, size), CornerRadius(size * 0.2f), style = Stroke(3f))
        val d = size * 0.25f
        val pips = when (value) {
            1 -> listOf(0 to 0)
            2 -> listOf(-1 to -1, 1 to 1)
            3 -> listOf(-1 to -1, 0 to 0, 1 to 1)
            4 -> listOf(-1 to -1, 1 to -1, -1 to 1, 1 to 1)
            5 -> listOf(-1 to -1, 1 to -1, 0 to 0, -1 to 1, 1 to 1)
            else -> listOf(-1 to -1, 1 to -1, -1 to 0, 1 to 0, -1 to 1, 1 to 1)
        }
        for ((px, py) in pips) drawCircle(if (value == 1) Color(0xFFD62828) else Color(0xFF1B2440), size * 0.09f, Offset(px * d, py * d))
    }
}

private class BoardFx(val kind: String, val start: Long, val from: Offset, val to: Offset, val col: Color, val text: String = "")
private class Seen(var owner: List<Int>, var houses: List<Int>)

@Composable
private fun TokensLayer(s: MonopolyState, myToken: String, theirToken: String, myCol: Color, theirCol: Color, zoom: Float, pan: Offset, viewPx: Float, m: TextMeasurer, showHints: Boolean) {
    // squares I could build on right now, and (for set hints) the squares I still need or that are blocked by the other player
    val buildable = remember(s.owner, s.houses, s.mortgaged, s.myCash, s.step, s.myTurn, s.housesLeft, s.hotelsLeft) {
        if (s.phase != GPhase.PLAYING || !s.canManage || s.step == "DEBT") emptyList()
        else (0 until MonopolyBoard.SIZE).filter { MonopolyRules.canBuild(s.ownerArr, s.housesArr, s.mortArr, 1, it, s.myCash, s.housesLeft, s.hotelsLeft) }
    }
    val needed = remember(s.owner) { setNeeds(s, 0) }
    val blocked = remember(s.owner) { setNeeds(s, 2) }
    val clock = remember { mutableLongStateOf(0L) }
    LaunchedEffect(Unit) { while (true) androidx.compose.runtime.withFrameMillis { clock.longValue = it } }
    val fx = remember { mutableStateListOf<BoardFx>() }
    val seen = remember { Seen(s.owner, s.houses) }
    LaunchedEffect(s.owner, s.houses) {
        val t = clock.longValue
        for (i in 0 until MonopolyBoard.SIZE) {
            val c = cellCentre(i)
            if (s.owner[i] != 0 && s.owner[i] != seen.owner[i]) fx.add(BoardFx("claim", t, c, c, if (s.owner[i] == 1) myCol else theirCol))
            else if (s.houses[i] > seen.houses[i] && s.owner[i] != 0) fx.add(BoardFx("build", t, c, c, Color(0xFFFFD23F)))
        }
        seen.owner = s.owner; seen.houses = s.houses
    }
    LaunchedEffect(s.seq) {
        if (s.seq == 0) return@LaunchedEffect
        val t = clock.longValue
        val me = cellCentre(s.myPos)
        val them = cellCentre(s.theirPos)
        val payer = if (s.evByMe) me else them
        val payee = if (s.evByMe) them else me
        val centre = Offset(BS / 2, BS / 2)
        val red = Color(0xFFF87171)
        val green = Color(0xFF4ADE80)
        val amt = kotlin.math.abs(s.evAmount)
        when (s.ev) {
            "rent" -> {
                fx.add(BoardFx("coins", t, payer, payee, Color.Unspecified))
                fx.add(BoardFx("float", t, payer, payer, red, "−£$amt"))
                fx.add(BoardFx("float", t + 500, payee, payee, green, "+£$amt"))
            }
            "tax" -> {
                fx.add(BoardFx("coins", t, payer, centre, Color.Unspecified))
                fx.add(BoardFx("float", t, payer, payer, red, "−£$amt"))
            }
            "go" -> {
                fx.add(BoardFx("confetti", t, cellCentre(0), cellCentre(0), Color.Unspecified))
                fx.add(BoardFx("float", t, payer, payer, green, "+£${if (amt > 0) amt else 200}"))
            }
            "buy" -> {
                if (s.evSq >= 0) fx.add(BoardFx("coins", t, payer, cellCentre(s.evSq), Color.Unspecified))
                if (amt > 0) fx.add(BoardFx("float", t, payer, payer, red, "−£$amt"))
            }
            "jail" -> fx.add(BoardFx("flash", t, cellCentre(10), cellCentre(10), Color(0xFF3B82F6)))
            "bankrupt" -> fx.add(BoardFx("bang", t, payer, payer, Color.Unspecified))
        }
    }
    LaunchedEffect(fx.size) {
        delay(3000)
        val t = clock.longValue
        fx.removeAll { t - it.start > 2800 }
    }
    val mine = remember { Animatable(s.myPos.toFloat()) }
    val theirs = remember { Animatable(s.theirPos.toFloat()) }
    var landed by remember { mutableIntStateOf(0) }
    val ring = remember { Animatable(1f) }
    LaunchedEffect(s.myPos) { hop(mine, s.myPos) { landed++ } }
    LaunchedEffect(s.theirPos) { hop(theirs, s.theirPos) { landed++ } }
    LaunchedEffect(landed) { if (landed > 0) { ring.snapTo(0f); ring.animateTo(1f, tween(700)) } }
    // dice: tumble for a moment, then settle
    val rollP = remember { Animatable(1f) }
    var d1 by remember { mutableIntStateOf(s.d1) }
    var d2 by remember { mutableIntStateOf(s.d2) }
    LaunchedEffect(s.moves, s.d1, s.d2, s.seq) {
        if (s.d1 > 0 && s.ev == "roll") {
            rollP.snapTo(0f)
            val job = launch { rollP.animateTo(1f, tween(900, easing = LinearEasing)) }
            repeat(9) { d1 = (1..6).random(); d2 = (1..6).random(); delay(80) }
            job.join()
        }
        d1 = s.d1; d2 = s.d2
    }
    val bob = rememberInfiniteTransition(label = "bob").animateFloat(0f, 1f, infiniteRepeatable(tween(900), RepeatMode.Reverse), label = "bobv")
    Canvas(Modifier.fillMaxSize()) {
        val k = size.width / BS
        withTransform({ translate(pan.x, pan.y); scale(zoom * k, zoom * k, Offset.Zero) }) {
            val c = Offset(BS / 2, BS / 2)
            // dice in the middle of the board
            val rollable = s.canRoll || s.canFirstRoll
            if (d1 > 0 || rollable) {
                val t = rollP.value
                val bounce = if (t < 1f) kotlin.math.abs(kotlin.math.sin(t * 3.1416f * 3f)) * (1f - t) * 90f else 0f
                val idle = if (rollable && t >= 1f) bob.value else 0f
                drawDice(Offset(c.x - 70f, c.y + 150f), 92f, if (d1 > 0) d1 else 5, (1f - t) * 380f + (idle - 0.5f) * 12f, bounce + idle * 16f)
                drawDice(Offset(c.x + 70f, c.y + 150f), 92f, if (d2 > 0) d2 else 2, -(1f - t) * 300f - (idle - 0.5f) * 12f, bounce + (1f - idle) * 16f)
                if (rollable) text(m, "👆 TAP THE DICE TO ROLL", Offset(c.x, c.y + 245f), 26f, Color.White.copy(alpha = 0.6f + 0.4f * bob.value), FontWeight.Black)
            }
            // where the player to move is about to land / has landed
            val lit = cellRect(if (s.myTurn) s.myPos else s.theirPos)
            val pulse = 0.35f + 0.25f * bob.value
            drawRect((if (s.myTurn) myCol else theirCol).copy(alpha = pulse), lit.topLeft, lit.size)
            if (ring.value < 1f) {
                val where = cellCentre(if (landed % 2 == 0) s.theirPos else s.myPos)
                drawCircle(Color.White.copy(alpha = 1f - ring.value), 20f + ring.value * 70f, where, style = Stroke(6f))
            }
            fun token(pos: Float, emoji: String, col: Color, nudge: Float, active: Boolean, moving: Boolean) {
                val spot = tokenSpot(pos, nudge)
                val frac = pos - pos.toInt()
                val hopY = if (moving) -kotlin.math.sin(frac * 3.1416f) * 34f else 0f
                val p = Offset(spot.x, spot.y + hopY)
                drawOval(Color.Black.copy(alpha = 0.35f), Offset(spot.x - 24f, spot.y + 18f), Size(48f, 16f))
                drawCircle(col, 31f, p)
                drawCircle(Color.White, 31f, p, style = Stroke(5f))
                val r = m.measure(emoji, TextStyle(fontSize = 34f.toSp()))
                drawText(r, topLeft = Offset(p.x - r.size.width / 2f, p.y - r.size.height / 2f))
                if (active) {
                    val a = Path().apply { moveTo(p.x, p.y - 44f - bob.value * 8f); lineTo(p.x - 12f, p.y - 62f - bob.value * 8f); lineTo(p.x + 12f, p.y - 62f - bob.value * 8f); close() }
                    drawPath(a, Color.White)
                }
            }
            token(theirs.value, theirToken, theirCol, 16f, !s.myTurn, theirs.isRunning)
            token(mine.value, myToken, myCol, -16f, s.myTurn, mine.isRunning)
            val now = clock.longValue
            // chasing bulbs round the MONOPOLY banner
            val inner = BS - 2 * CORNER
            withTransform({ translate(c.x, c.y - inner * 0.1f); rotate(-35f, Offset.Zero) }) {
                for (k in 0..14) {
                    val on = (k + (now / 220L).toInt()) % 2 == 0
                    val colour = if (on) Color(0xFFFFE066) else Color(0xFF7A5B00)
                    drawCircle(colour, 6f, Offset(-282f + k * 40f, -66f))
                    drawCircle(colour, 6f, Offset(-282f + k * 40f, 66f))
                }
            }
            // build badges and set hints, drawn just outside the inner edge of each square
            val beat = 0.5f + 0.5f * kotlin.math.sin(now / 260f)
            for (i in buildable) {
                val hs = s.houses[i]
                inCell(i) { w, h ->
                    val cy = -h / 2f - 30f - beat * 8f
                    drawCircle(Color(0xFF16A34A).copy(alpha = 0.35f + 0.3f * beat), 34f + beat * 6f, Offset(0f, cy))
                    drawCircle(Color(0xFF22C55E), 26f, Offset(0f, cy))
                    drawCircle(Color.White, 26f, Offset(0f, cy), style = Stroke(4f))
                    text(m, if (hs == 4) "🏨" else "🔨", Offset(0f, cy), 30f)
                    drawRect(Color(0xFF22C55E).copy(alpha = 0.25f + 0.35f * beat), Offset(-w / 2f, -h / 2f), Size(w, h), style = Stroke(8f))
                }
            }
            if (showHints) {
                for (i in needed) inCell(i) { w, h ->
                    drawRect(Color(0xFFFFD23F).copy(alpha = 0.3f + 0.6f * beat), Offset(-w / 2f + 3f, -h / 2f + 3f), Size(w - 6f, h - 6f), style = Stroke(7f))
                    val cy = -h / 2f - 26f - beat * 6f
                    drawCircle(Color(0xFFFFD23F), 22f, Offset(0f, cy))
                    text(m, "🎯", Offset(0f, cy), 26f)
                }
                for (i in blocked) inCell(i) { w, h ->
                    drawRect(Color(0xFFEF4444).copy(alpha = 0.25f + 0.45f * beat), Offset(-w / 2f + 3f, -h / 2f + 3f), Size(w - 6f, h - 6f), style = Stroke(6f))
                    val cy = -h / 2f - 24f
                    drawCircle(Color(0xFFEF4444), 20f, Offset(0f, cy))
                    text(m, "🔒", Offset(0f, cy), 22f)
                }
            }
            // one-off effects: coins in flight, floating money, claim bursts, confetti
            for (f in fx) {
                val age = (now - f.start) / 1000f
                if (age < 0f) continue
                when (f.kind) {
                    "coins" -> for (k in 0 until 7) {
                        val t = ((age - k * 0.09f) / 0.8f)
                        if (t <= 0f || t >= 1f) continue
                        val e = t * t * (3f - 2f * t)
                        val p = Offset(f.from.x + (f.to.x - f.from.x) * e, f.from.y + (f.to.y - f.from.y) * e - kotlin.math.sin(t * 3.1416f) * 70f)
                        drawCircle(Color(0x55000000), 16f, Offset(p.x + 3f, p.y + 6f))
                        drawCircle(Color(0xFFFFD23F), 16f, p)
                        drawCircle(Color(0xFFFFF3B0), 8f, Offset(p.x - 3f, p.y - 3f))
                        drawCircle(Color(0xFFB8860B), 16f, p, style = Stroke(3f))
                    }
                    "float" -> {
                        val a = (1f - age / 1.7f).coerceIn(0f, 1f)
                        if (a > 0f) text(m, f.text, Offset(f.from.x, f.from.y - 50f - age * 80f), 50f, f.col.copy(alpha = a), FontWeight.Black)
                    }
                    "claim", "build" -> {
                        val dur = if (f.kind == "claim") 1.0f else 0.7f
                        val t = age / dur
                        if (t in 0f..1f) {
                            drawCircle(f.col.copy(alpha = 1f - t), 25f + t * 150f, f.from, style = Stroke(9f * (1f - t) + 1f))
                            for (k in 0 until 8) {
                                val ang = k * 0.785f + t
                                drawCircle(Color.White.copy(alpha = 1f - t), 7f * (1f - t) + 1f, Offset(f.from.x + kotlin.math.cos(ang) * (20f + t * 130f), f.from.y + kotlin.math.sin(ang) * (20f + t * 130f)))
                            }
                        }
                    }
                    "confetti" -> {
                        val t = age / 1.8f
                        if (t in 0f..1f) {
                            val palette = listOf(Color(0xFFFFD23F), Color(0xFFFF4D4D), Color(0xFF4ADE80), Color(0xFF38BDF8), Color(0xFFF472B6))
                            for (k in 0 until 40) {
                                val ang = k * 2.399f
                                val v = 160f + (k % 6) * 45f
                                val pos = Offset(f.from.x - 60f + kotlin.math.cos(ang) * v * age, f.from.y - 60f + kotlin.math.sin(ang) * v * age + 260f * age * age)
                                drawRect(palette[k % palette.size].copy(alpha = 1f - t), pos, Size(12f, 8f))
                            }
                        }
                    }
                    "flash" -> {
                        val a = (1f - age / 1.3f).coerceIn(0f, 1f) * kotlin.math.abs(kotlin.math.sin(age * 12f)) * 0.8f
                        val r = cellRect(10)
                        drawRect(f.col.copy(alpha = a), r.topLeft, r.size)
                    }
                    "bang" -> if (age < 1.1f) drawExplosion(f.from, 190f, (age / 1.1f).coerceAtMost(0.999f))
                }
            }
        }
    }
}

private suspend fun hop(a: Animatable<Float, androidx.compose.animation.core.AnimationVector1D>, target: Int, onLand: () -> Unit) {
    val cur = a.value % MonopolyBoard.SIZE
    val fwd = (target - cur + MonopolyBoard.SIZE) % MonopolyBoard.SIZE
    if (fwd == 0f) { a.snapTo(target.toFloat()); return }
    if (fwd > 12f) {
        a.snapTo(target.toFloat())
        onLand()
        return
    }
    a.snapTo(cur)
    // each square is a hop: speed up a little for long moves
    a.animateTo(cur + fwd, tween((fwd * 170).toInt().coerceIn(220, 1500), easing = LinearEasing))
    a.snapTo(target.toFloat())
    onLand()
}

@Composable
private fun CardPopup(s: MonopolyState, modifier: Modifier) {
    val chance = s.cardDeck == 0
    val grow = remember { Animatable(0.6f) }
    LaunchedEffect(s.seq) { grow.snapTo(0.6f); grow.animateTo(1f, tween(350)) }
    Column(
        modifier.graphicsLayer { scaleX = grow.value; scaleY = grow.value }.width(230.dp).clip(RoundedCornerShape(14.dp))
            .background(if (chance) Color(0xFFFFB74D) else Color(0xFF81D4FA))
            .border(3.dp, Color(0xFF1A1A1A), RoundedCornerShape(14.dp)).padding(14.dp),
        horizontalAlignment = Alignment.CenterHorizontally, verticalArrangement = Arrangement.spacedBy(6.dp),
    ) {
        Text(if (chance) "❓ CHANCE" else "🎁 COMMUNITY CHEST", fontWeight = FontWeight.Black, fontSize = 15.sp, color = Color(0xFF1A1A1A))
        Text(s.cardText, fontSize = 14.sp, color = Color(0xFF1A1A1A), textAlign = TextAlign.Center, fontWeight = FontWeight.SemiBold)
    }
}

// ============================================================================ panels

@Composable
private fun PlayerRow(s: MonopolyState, myCol: Color, theirCol: Color, myToken: String, theirToken: String) {
    val over = s.phase == GPhase.OVER
    Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
        PlayerBox("You $myToken", s.myCash, s.owner.count { it == 1 }, s.myJail, s.myFreeCards, myCol, s.myTurn && !over, Modifier.weight(1f))
        PlayerBox("${s.theirName} $theirToken", s.theirCash, s.owner.count { it == 2 }, s.theirJail, s.theirFreeCards, theirCol, !s.myTurn && !over, Modifier.weight(1f))
    }
    if (s.lapRule && !over && s.step != "FIRST" && (!s.myPassedGo || !s.theirPassedGo)) {
        Text(
            (if (!s.myPassedGo) "🏁 You can start buying once you've passed GO. " else "") +
                (if (!s.theirPassedGo) "${s.theirName} can't buy until they've passed GO." else ""),
            fontSize = 11.sp, color = Funk.Sun, textAlign = TextAlign.Center,
        )
    }
    if (s.maxTurns > 0 && !over) {
        Text("Turn ${minOf(s.myTurnsTaken, s.theirTurnsTaken) + 1} of ${s.maxTurns} · richest wins when time is up", fontSize = 12.sp, color = Color.White.copy(alpha = 0.8f))
    }
}

@Composable
private fun PlayerBox(name: String, cash: Int, props: Int, jail: Boolean, cards: Int, col: Color, active: Boolean, modifier: Modifier) {
    val shape = RoundedCornerShape(16.dp)
    // a little "+£200" / "−£50" that floats up whenever the money changes
    val shown by animateIntAsState(cash, tween(900), label = "cash")
    var last by remember { mutableIntStateOf(cash) }
    var delta by remember { mutableIntStateOf(0) }
    val fade = remember { Animatable(1f) }
    LaunchedEffect(cash) {
        if (cash != last) {
            delta = cash - last
            last = cash
            fade.snapTo(0f)
            fade.animateTo(1f, tween(1700))
        }
    }
    Column(
        modifier.clip(shape).background(Color(0x66000000)).border(if (active) 3.dp else 1.dp, if (active) col else col.copy(alpha = 0.4f), shape).padding(10.dp),
        verticalArrangement = Arrangement.spacedBy(2.dp),
    ) {
        Text(name, fontWeight = FontWeight.Bold, color = col, maxLines = 1, fontSize = 14.sp)
        Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            Text(money(shown), fontWeight = FontWeight.Black, color = Color.White, fontSize = 24.sp)
            if (delta != 0 && fade.value < 1f) {
                Text(
                    (if (delta > 0) "+" else "−") + money(kotlin.math.abs(delta)), fontWeight = FontWeight.Black, fontSize = 16.sp,
                    color = if (delta > 0) Color(0xFF4ADE80) else Color(0xFFF87171),
                    modifier = Modifier.graphicsLayer { alpha = 1f - fade.value; translationY = -14f * fade.value * density },
                )
            }
        }
        Text(
            "$props propert${if (props == 1) "y" else "ies"}" + (if (jail) " · 🚔 JAIL" else "") + (if (cards > 0) " · 🔓×$cards" else ""),
            fontSize = 11.sp, color = Color.White.copy(alpha = 0.8f),
        )
    }
}

@Composable
private fun BigAction(label: String, colors: List<Color>, enabled: Boolean = true, modifier: Modifier = Modifier, onClick: () -> Unit) {
    FunkyButton(label, onClick, modifier, colors, enabled = enabled, fontSize = 17.sp)
}

@Composable
private fun SmallAction(label: String, enabled: Boolean = true, modifier: Modifier = Modifier, onClick: () -> Unit) {
    val shape = RoundedCornerShape(14.dp)
    Box(
        modifier.clip(shape).background(Color.White.copy(alpha = if (enabled) 0.18f else 0.07f)).border(1.dp, Color.White.copy(alpha = 0.4f), shape)
            .clickable(enabled = enabled, onClick = { Sfx.play(Sound.TAP); onClick() }).padding(horizontal = 12.dp, vertical = 10.dp),
        contentAlignment = Alignment.Center,
    ) { Text(label, color = Color.White.copy(alpha = if (enabled) 1f else 0.5f), fontWeight = FontWeight.Bold, fontSize = 14.sp) }
}

@Composable
private fun ActionBar(s: MonopolyState, engine: MonopolyEngine, over: Boolean, onManage: () -> Unit, onTrade: () -> Unit, onBankrupt: () -> Unit) {
    val green = listOf(Color(0xFF2E7D32), Color(0xFF66BB6A))
    val red = listOf(Color(0xFFC62828), Color(0xFFEF5350))
    Column(Modifier.fillMaxWidth(), verticalArrangement = Arrangement.spacedBy(8.dp), horizontalAlignment = Alignment.CenterHorizontally) {
        if (over) return@Column
        if (s.step == "AUCTION") {
            Text("🔨 Auction in progress…", color = Color.White, fontWeight = FontWeight.Bold)
            return@Column
        }
        if (s.step == "FIRST") {
            val mine = if (s.firstMine > 0) "You rolled ${s.firstMine}" else "You haven't rolled"
            val theirs = if (s.firstTheirs > 0) "${s.theirName} rolled ${s.firstTheirs}" else "${s.theirName} hasn't rolled"
            Text("🎲 Roll to see who goes first: the highest total wins.", color = Color.White, fontWeight = FontWeight.Bold, textAlign = TextAlign.Center)
            Text("$mine · $theirs", color = Funk.Sun, fontWeight = FontWeight.SemiBold, fontSize = 14.sp)
            if (s.firstTie) Text("A tie! Roll again.", color = Funk.Pink, fontWeight = FontWeight.Black)
            BigAction(if (s.canFirstRoll) "🎲  ROLL FOR FIRST GO" else "Waiting for ${s.theirName} to roll…", green, s.canFirstRoll, Modifier.fillMaxWidth()) { engine.roll() }
            return@Column
        }
        if (!s.myTurn) {
            Text(
                if (s.step == "DEBT") "💸 ${s.theirName} owes ${money(s.debt)} and is raising cash…" else "⏳ ${s.theirName} is playing…",
                color = Color.White, fontWeight = FontWeight.Bold, fontSize = 15.sp,
            )
            return@Column
        }
        when (s.step) {
            "ROLL" -> {
                if (s.myJail) {
                    Text("🚔 You're in jail (try ${s.myJailTries + 1} of 3). Roll a double to get out, or:", color = Color.White, fontSize = 13.sp)
                    Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        SmallAction("Pay £${MonopolyBoard.JAIL_FINE}", s.myCash >= MonopolyBoard.JAIL_FINE) { engine.payJail() }
                        SmallAction("Use card 🔓", s.myFreeCards > 0) { engine.useJailCard() }
                    }
                }
                BigAction("🎲  ROLL THE DICE", green, modifier = Modifier.fillMaxWidth()) { engine.roll() }
                if (s.doubles in 1..2 && !s.myJail) Text("You rolled a double: go again!", color = Funk.Sun, fontSize = 13.sp, fontWeight = FontWeight.Bold)
            }
            "BUY" -> {
                val sq = MonopolyBoard.squares[s.myPos]
                Text("You landed on ${sq.name}", color = Color.White, fontWeight = FontWeight.Bold)
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp), modifier = Modifier.fillMaxWidth()) {
                    BigAction("Buy ${money(sq.price)}", green, s.myCash >= sq.price, Modifier.weight(1f)) { engine.buy() }
                    BigAction("Auction", red, true, Modifier.weight(1f)) { engine.declineToAuction() }
                }
                if (s.myCash < sq.price) Text("Not enough cash to buy: it goes to auction.", color = Color.White.copy(alpha = 0.8f), fontSize = 12.sp)
            }
            "DEBT" -> {
                val to = if (s.debtTo == 2) s.theirName else "the bank"
                Text("💸 You owe ${money(s.debt)} to $to (${s.debtWhy}). You have ${money(s.myCash)}.", color = Funk.Sun, fontWeight = FontWeight.Bold, textAlign = TextAlign.Center)
                BigAction("Pay ${money(s.debt)}", green, s.myCash >= s.debt, Modifier.fillMaxWidth()) { engine.payDebt() }
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    SmallAction("Sell / mortgage…") { onManage() }
                    SmallAction("Declare bankruptcy") { onBankrupt() }
                }
            }
            "END" -> {
                BigAction("End turn", green, modifier = Modifier.fillMaxWidth()) { engine.endTurn() }
            }
        }
        if (s.step == "ROLL" || s.step == "END") {
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                SmallAction("🏠 Properties") { onManage() }
                SmallAction("🤝 Trade", s.canTrade) { onTrade() }
            }
        }
    }
}

@Composable
private fun LogBox(s: MonopolyState) {
    val items = s.log.takeLast(5).reversed()
    if (items.isEmpty()) return
    Column(
        Modifier.fillMaxWidth().clip(RoundedCornerShape(12.dp)).background(Color(0x55000000)).padding(10.dp),
        verticalArrangement = Arrangement.spacedBy(3.dp),
    ) {
        Text("What's happened", fontSize = 11.sp, fontWeight = FontWeight.Bold, color = Color.White.copy(alpha = 0.7f))
        items.forEachIndexed { k, t -> Text(t, fontSize = 12.sp, color = Color.White.copy(alpha = if (k == 0) 1f else 0.7f)) }
    }
}

// ============================================================================ dialogs

@Composable
private fun DeedDialog(s: MonopolyState, sq: Int, myCol: Color, theirCol: Color, onClose: () -> Unit) {
    val q = MonopolyBoard.squares[sq]
    AlertDialog(
        onDismissRequest = onClose,
        confirmButton = { TextButton(onClick = onClose) { Text("Close") } },
        title = { Text(q.name) },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
                if (q.kind == SqKind.PROPERTY) {
                    Box(Modifier.fillMaxWidth().height(14.dp).clip(RoundedCornerShape(4.dp)).background(groupColour(q.group)))
                    Text("${MonopolyBoard.GROUP_NAMES[q.group]} set", fontWeight = FontWeight.SemiBold)
                    val labels = listOf("Rent", "With 1 house", "With 2 houses", "With 3 houses", "With 4 houses", "With a hotel")
                    q.rents.forEachIndexed { k, r -> Text("${labels[k]}: ${money(r)}") }
                    Text("Houses cost ${money(q.houseCost)} each. A hotel costs ${money(q.houseCost)} plus four houses.", fontSize = 12.sp)
                    Text("Rent is doubled on an unbuilt street if one player owns the whole set.", fontSize = 12.sp)
                } else if (q.kind == SqKind.STATION) {
                    listOf("1 station" to 25, "2 stations" to 50, "3 stations" to 100, "4 stations" to 200).forEach { (l, r) -> Text("Rent with $l: ${money(r)}") }
                } else if (q.kind == SqKind.UTILITY) {
                    Text("One utility owned: rent is 4× the dice.")
                    Text("Both utilities owned: rent is 10× the dice.")
                } else if (q.kind == SqKind.TAX) {
                    Text("Pay ${money(q.rents[0])} to the bank.")
                } else {
                    Text(when (q.kind) {
                        SqKind.GO -> "Collect £200 every time you pass or land on GO."
                        SqKind.JAIL -> "Just visiting, unless you were sent here."
                        SqKind.PARKING -> "A rest. Nothing happens."
                        SqKind.GOTOJAIL -> "Go directly to jail. Do not pass GO."
                        SqKind.CHANCE -> "Draw a Chance card."
                        SqKind.CHEST -> "Draw a Community Chest card."
                        else -> ""
                    })
                }
                if (q.ownable) {
                    Text("Price: ${money(q.price)}   Mortgage value: ${money(q.mortgageValue)}", fontWeight = FontWeight.SemiBold)
                    val o = s.owner[sq]
                    Text(
                        when (o) { 1 -> "Owned by you"; 2 -> "Owned by ${s.theirName}"; else -> "Not owned yet" } +
                            (if (s.mortgaged[sq]) " (mortgaged)" else "") +
                            (if (s.houses[sq] in 1..4) " · ${s.houses[sq]} house${if (s.houses[sq] == 1) "" else "s"}" else if (s.houses[sq] == 5) " · hotel" else ""),
                        color = if (o == 1) myCol else if (o == 2) theirCol else MaterialTheme.colorScheme.onSurface, fontWeight = FontWeight.Bold,
                    )
                    if (o != 0 && !s.mortgaged[sq]) {
                        val rent = MonopolyRules.rent(s.ownerArr, s.housesArr, s.mortArr, sq, 7)
                        Text("Rent right now: ${if (q.kind == SqKind.UTILITY) "${money(rent)} (on a 7)" else money(rent)}", fontSize = 12.sp)
                    }
                }
            }
        },
    )
}

@Composable
private fun ManageDialog(s: MonopolyState, engine: MonopolyEngine, onClose: () -> Unit) {
    val owner = s.ownerArr; val houses = s.housesArr; val mort = s.mortArr
    val mine = (0 until MonopolyBoard.SIZE).filter { s.owner[it] == 1 }
    val inDebt = s.step == "DEBT"
    AlertDialog(
        onDismissRequest = onClose,
        confirmButton = { TextButton(onClick = onClose) { Text("Done") } },
        title = { Text("Your properties · ${money(s.myCash)}") },
        text = {
            Column(Modifier.verticalScroll(rememberScrollState()), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                if (mine.isEmpty()) Text("You don't own anything yet. Buy what you land on!")
                if (inDebt) Text("You owe ${money(s.debt)}. Sell buildings or mortgage property to raise the cash, then pay.", fontWeight = FontWeight.SemiBold)
                if (!s.canManage) Text("You can build, sell and mortgage on your own turn.", fontSize = 12.sp)
                mine.forEach { sq ->
                    val q = MonopolyBoard.squares[sq]
                    Column(Modifier.fillMaxWidth().clip(RoundedCornerShape(10.dp)).border(1.dp, MaterialTheme.colorScheme.outline.copy(alpha = 0.4f), RoundedCornerShape(10.dp)).padding(8.dp)) {
                        Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                            Box(Modifier.size(14.dp).clip(CircleShape).background(if (q.group >= 0) groupColour(q.group) else Color.Gray))
                            Text(q.name, Modifier.weight(1f), fontWeight = FontWeight.Bold, fontSize = 14.sp)
                            Text(
                                (if (s.mortgaged[sq]) "mortgaged" else if (houses[sq] == 5) "hotel" else if (houses[sq] > 0) "🏠×${houses[sq]}" else ""),
                                fontSize = 12.sp,
                            )
                        }
                        val can = s.canManage
                        if (q.kind == SqKind.PROPERTY) {
                            Row(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                                MiniBtn("Build ${money(q.houseCost)}", can && !inDebt && MonopolyRules.canBuild(owner, houses, mort, 1, sq, s.myCash, s.housesLeft, s.hotelsLeft)) { engine.build(sq) }
                                MiniBtn("Sell +${money(q.houseCost / 2)}", can && MonopolyRules.canSell(owner, houses, 1, sq, s.housesLeft)) { engine.sellHouse(sq) }
                            }
                        }
                        if (s.mortgaged[sq]) MiniBtn("Lift mortgage ${money(MonopolyRules.unmortgageCost(sq))}", can && !inDebt && MonopolyRules.canUnmortgage(owner, mort, 1, sq, s.myCash)) { engine.unmortgage(sq) }
                        else MiniBtn("Mortgage +${money(q.mortgageValue)}", can && MonopolyRules.canMortgage(owner, houses, mort, 1, sq)) { engine.mortgage(sq) }
                    }
                }
                Text("Houses left: ${s.housesLeft} · Hotels left: ${s.hotelsLeft}", fontSize = 11.sp)
            }
        },
    )
}

@Composable
private fun MiniBtn(label: String, enabled: Boolean, onClick: () -> Unit) {
    TextButton(onClick = onClick, enabled = enabled) { Text(label, fontSize = 12.sp) }
}

@Composable
private fun TradeDialog(s: MonopolyState, engine: MonopolyEngine, onClose: () -> Unit) {
    val houses = s.housesArr
    fun tradeable(sq: Int): Boolean {
        val g = MonopolyBoard.squares[sq].group
        return MonopolyBoard.squares[sq].ownable && (g < 0 || MonopolyBoard.GROUPS[g].none { houses[it] > 0 })
    }
    val mineList = (0 until MonopolyBoard.SIZE).filter { s.owner[it] == 1 && tradeable(it) }
    val theirList = (0 until MonopolyBoard.SIZE).filter { s.owner[it] == 2 && tradeable(it) }
    val give = remember { mutableStateListOf<Int>() }
    val get = remember { mutableStateListOf<Int>() }
    var giveCash by remember { mutableIntStateOf(0) }
    var getCash by remember { mutableIntStateOf(0) }
    val valid = (give.isNotEmpty() || get.isNotEmpty() || giveCash > 0 || getCash > 0) && giveCash <= s.myCash && getCash <= s.theirCash
    AlertDialog(
        onDismissRequest = onClose,
        title = { Text("Trade with ${s.theirName}") },
        confirmButton = {
            TextButton(enabled = valid, onClick = { engine.offerTrade(give.toList(), giveCash, get.toList(), getCash); onClose() }) { Text("Offer deal") }
        },
        dismissButton = { TextButton(onClick = onClose) { Text("Cancel") } },
        text = {
            Column(Modifier.verticalScroll(rememberScrollState()), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                Text("You give:", fontWeight = FontWeight.Bold)
                if (mineList.isEmpty()) Text("(no properties to trade: ones with buildings can't be traded)", fontSize = 12.sp)
                mineList.forEach { sq -> TradeChip(sq, sq in give, s.mortgaged[sq]) { if (sq in give) give.remove(sq) else give.add(sq) } }
                CashStepper("Cash you give", giveCash, s.myCash) { giveCash = it }
                Text("You get:", fontWeight = FontWeight.Bold)
                if (theirList.isEmpty()) Text("(${s.theirName} has nothing tradeable)", fontSize = 12.sp)
                theirList.forEach { sq -> TradeChip(sq, sq in get, s.mortgaged[sq]) { if (sq in get) get.remove(sq) else get.add(sq) } }
                CashStepper("Cash you ask for", getCash, s.theirCash) { getCash = it }
                Text("The other player can accept or decline. Mortgaged properties stay mortgaged.", fontSize = 11.sp)
            }
        },
    )
}

@Composable
private fun TradeChip(sq: Int, selected: Boolean, mortgaged: Boolean, onClick: () -> Unit) {
    val q = MonopolyBoard.squares[sq]
    FilterChip(
        selected = selected, onClick = onClick,
        label = { Text(q.name + " (" + money(q.price) + ")" + if (mortgaged) " · mortgaged" else "", fontSize = 12.sp) },
        leadingIcon = { Box(Modifier.size(12.dp).clip(CircleShape).background(if (q.group >= 0) groupColour(q.group) else Color.Gray)) },
    )
}

@Composable
private fun CashStepper(label: String, value: Int, max: Int, onChange: (Int) -> Unit) {
    Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(4.dp)) {
        Text("$label:", fontSize = 13.sp, modifier = Modifier.weight(1f))
        TextButton(onClick = { onChange((value - 50).coerceAtLeast(0)) }, enabled = value > 0) { Text("−50") }
        Text(money(value), fontWeight = FontWeight.Bold)
        TextButton(onClick = { onChange((value + 50).coerceAtMost(max)) }, enabled = value + 1 <= max) { Text("+50") }
    }
}

@Composable
private fun TradeResponse(s: MonopolyState, engine: MonopolyEngine) {
    val t: TradeOffer = s.trade ?: return
    fun list(sqs: List<Int>, cash: Int): String {
        val parts = sqs.map { MonopolyBoard.squares[it].name }.toMutableList()
        if (cash > 0) parts.add(money(cash))
        return if (parts.isEmpty()) "nothing" else parts.joinToString(", ")
    }
    if (t.from == 1) {
        // my own offer, waiting for their answer
        AlertDialog(
            onDismissRequest = {},
            title = { Text("Waiting for ${s.theirName}…") },
            text = { Text("You offered ${list(t.giveSq, t.giveCash)} for ${list(t.getSq, t.getCash)}.") },
            confirmButton = { TextButton(onClick = { engine.cancelTrade() }) { Text("Withdraw offer") } },
        )
    } else {
        AlertDialog(
            onDismissRequest = {},
            title = { Text("${s.theirName} offers a trade") },
            text = {
                Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
                    Text("You get: ${list(t.giveSq, t.giveCash)}", fontWeight = FontWeight.Bold)
                    Text("You give: ${list(t.getSq, t.getCash)}", fontWeight = FontWeight.Bold)
                    if (t.getCash > s.myCash) Text("You don't have enough cash for this deal.", color = MaterialTheme.colorScheme.error)
                    val fee = MonopolyRules.tradeInterest(t.giveSq, s.mortArr)
                    if (fee > 0) Text("Some of it is mortgaged, so you pay the bank ${money(fee)} interest (10%) straight away.", fontSize = 12.sp)
                }
            },
            confirmButton = { TextButton(enabled = t.getCash <= s.myCash, onClick = { engine.acceptTrade() }) { Text("Accept") } },
            dismissButton = { TextButton(onClick = { engine.declineTrade() }) { Text("Decline") } },
        )
    }
}

@Composable
private fun AuctionDialog(s: MonopolyState, engine: MonopolyEngine) {
    if (s.auctionSq < 0) return
    val q = MonopolyBoard.squares[s.auctionSq]
    val leader = when (s.auctionLeader) { 1 -> "you"; 2 -> s.theirName; else -> "nobody yet" }
    AlertDialog(
        onDismissRequest = {},
        title = { Text("🔨 Auction: ${q.name}") },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
                if (q.group >= 0) Box(Modifier.fillMaxWidth().height(10.dp).clip(RoundedCornerShape(4.dp)).background(groupColour(q.group)))
                Text("List price ${money(q.price)}. Current bid: ${if (s.auctionBid > 0) money(s.auctionBid) else "none"} (leader: $leader).", fontWeight = FontWeight.SemiBold)
                Text("You have ${money(s.myCash)}.", fontSize = 12.sp)
                if (!s.myBidTurn) Text("Waiting for ${s.theirName}…")
            }
        },
        confirmButton = {
            if (s.myBidTurn) {
                Column(horizontalAlignment = Alignment.End) {
                    val base = maxOf(s.auctionBid, 0)
                    Row {
                        for (inc in listOf(1, 10, 50, 100)) {
                            val amt = if (base == 0) inc else base + inc
                            TextButton(enabled = amt <= s.myCash, onClick = { engine.bid(amt) }) { Text(money(amt)) }
                        }
                    }
                    TextButton(onClick = { engine.passBid() }) { Text(if (s.auctionLeader == 1) "Stay (no more bids)" else "Pass") }
                }
            }
        },
    )
}

// ============================================================================ banner and ownership tracker


// ---- the big on-board moments: rent receipt, deed offer and event stage

private fun ease(t: Float) = t * t * (3f - 2f * t)

/** Lands on somebody's property: a rent receipt slides in, the amount counts up and a PAID stamp slams down. Tap to dismiss. */
@Composable
private fun RentReceipt(s: MonopolyState, myCol: Color, theirCol: Color, myToken: String, theirToken: String, modifier: Modifier) {
    class Receipt(val sq: Int, val amount: Int, val payerMe: Boolean, val reason: String, val ownerName: String, val ownerTok: String, val ownerCol: Color)
    var rec by remember { mutableStateOf<Receipt?>(null) }
    val p = remember { Animatable(1f) }
    LaunchedEffect(s.seq) {
        if (s.seq == 0 || s.ev != "rent" || s.evSq < 0) return@LaunchedEffect
        val q = MonopolyBoard.squares[s.evSq]
        val ownerIdx = if (s.evByMe) 2 else 1
        val h = s.houses[s.evSq]
        val reason = when (q.kind) {
            SqKind.PROPERTY -> when {
                h == 5 -> "🏨 A hotel stands here!"
                h > 0 -> "🏠 × $h house${if (h == 1) "" else "s"}"
                MonopolyBoard.ownsGroup(s.ownerArr, ownerIdx, q.group) -> "⭐ Full set owned: rent doubled!"
                else -> "Standard rent"
            }
            SqKind.STATION -> {
                val n = (0 until MonopolyBoard.SIZE).count { MonopolyBoard.squares[it].kind == SqKind.STATION && s.owner[it] == ownerIdx }
                "🚂 $n station${if (n == 1) "" else "s"} owned"
            }
            SqKind.UTILITY -> {
                val n = (0 until MonopolyBoard.SIZE).count { MonopolyBoard.squares[it].kind == SqKind.UTILITY && s.owner[it] == ownerIdx }
                "🎲 Dice ${s.d1}+${s.d2} = ${s.total} × ${if (n == 2) 10 else 4}"
            }
            else -> ""
        }
        rec = Receipt(
            s.evSq, kotlin.math.abs(s.evAmount), s.evByMe, reason,
            if (s.evByMe) s.theirName else "You", if (s.evByMe) theirToken else myToken, if (s.evByMe) theirCol else myCol,
        )
        p.snapTo(0f)
        p.animateTo(1f, tween(4200, easing = LinearEasing))
    }
    val stamped = rec != null && p.value > 0.3f
    LaunchedEffect(rec) { if (rec != null) { delay(1300); Sfx.play(Sound.THUD) } }
    val r = rec
    if (r != null && p.value < 1f) {
        val t = p.value
        val tint = if (r.payerMe) Color(0xFFEF4444) else Color(0xFF22C55E)
        val q = MonopolyBoard.squares[r.sq]
        Box(modifier.clickable { rec = null }, contentAlignment = Alignment.Center) {
            // the edges of the board flash red when you pay, green when you are paid
            val flash = (1f - t * 6f).coerceAtLeast(0f)
            if (flash > 0f) Box(Modifier.fillMaxSize().background(Brush.radialGradient(listOf(Color.Transparent, tint.copy(alpha = 0.55f * flash)))))
            val enter = ease((t / 0.12f).coerceIn(0f, 1f))
            val exit = ((1f - t) / 0.12f).coerceIn(0f, 1f)
            val shape = RoundedCornerShape(14.dp)
            Box(
                Modifier.width(250.dp).graphicsLayer {
                    translationY = (1f - enter) * -260f * density
                    rotationZ = (1f - enter) * -8f + kotlin.math.sin(t * 40f) * (1f - enter) * 3f
                    alpha = exit
                    val sc = 0.85f + 0.15f * enter
                    scaleX = sc; scaleY = sc
                }.clip(shape).background(CardCream).border(3.dp, Color(0xFF1A1A1A), shape),
            ) {
                Column(horizontalAlignment = Alignment.CenterHorizontally) {
                    Box(
                        Modifier.fillMaxWidth().background(if (q.kind == SqKind.PROPERTY) groupColour(q.group) else Color(0xFF475569)).padding(vertical = 6.dp),
                        contentAlignment = Alignment.Center,
                    ) { Text("🧾 RENT DUE", color = Color.White, fontWeight = FontWeight.Black, fontSize = 15.sp, letterSpacing = 2.sp) }
                    Column(Modifier.padding(horizontal = 14.dp, vertical = 8.dp), horizontalAlignment = Alignment.CenterHorizontally, verticalArrangement = Arrangement.spacedBy(3.dp)) {
                        Text(q.name.uppercase(), color = Ink, fontWeight = FontWeight.Black, fontSize = 17.sp, textAlign = TextAlign.Center)
                        Text("Owned by ${r.ownerTok} ${r.ownerName}", color = r.ownerCol, fontWeight = FontWeight.Bold, fontSize = 13.sp)
                        if (r.reason.isNotEmpty()) Text(r.reason, color = Color(0xFF444444), fontSize = 12.sp, textAlign = TextAlign.Center)
                        // coins slide from the payer to the owner
                        val slide = (t / 0.5f).coerceIn(0f, 1f)
                        Box(Modifier.fillMaxWidth().height(26.dp)) {
                            Text(if (r.payerMe) "You" else "Them", fontSize = 11.sp, color = Color(0xFF555555), modifier = Modifier.align(Alignment.CenterStart))
                            Text(r.ownerName.take(8), fontSize = 11.sp, color = Color(0xFF555555), modifier = Modifier.align(Alignment.CenterEnd))
                            for (k in 0 until 3) {
                                val tk = ((slide * 1.5f) - k * 0.2f).coerceIn(0f, 1f)
                                Text("🪙", fontSize = 16.sp, modifier = Modifier.align(Alignment.CenterStart).graphicsLayer { translationX = tk * 190f * density; alpha = if (tk in 0.01f..0.99f) 1f else 0f })
                            }
                        }
                        val shown = (r.amount * ease((t / 0.22f).coerceIn(0f, 1f))).toInt()
                        Text((if (r.payerMe) "−" else "+") + money(shown), color = tint, fontWeight = FontWeight.Black, fontSize = 38.sp)
                        Text(
                            if (r.payerMe) "You have ${money(s.myCash)} left" else "You now have ${money(s.myCash)}",
                            color = Color(0xFF555555), fontSize = 11.sp,
                        )
                    }
                }
                if (stamped) {
                    val st = ((t - 0.3f) / 0.07f).coerceIn(0f, 1f)
                    Box(
                        Modifier.align(Alignment.Center).graphicsLayer {
                            val sc = 2.8f - 1.8f * ease(st)
                            scaleX = sc; scaleY = sc; rotationZ = -14f; alpha = 0.25f + 0.75f * st
                        }.border(4.dp, tint, RoundedCornerShape(8.dp)).background(Color.White.copy(alpha = 0.6f * st)).padding(horizontal = 12.dp, vertical = 2.dp),
                    ) { Text(if (r.payerMe) "PAID" else "RECEIVED", color = tint, fontWeight = FontWeight.Black, fontSize = 30.sp) }
                }
            }
        }
    }
}

/** You landed on an unowned property: its title deed flips up on the board with Buy and Auction buttons. */
@Composable
private fun DeedOffer(s: MonopolyState, engine: MonopolyEngine, modifier: Modifier) {
    if (s.step != "BUY" || s.phase != GPhase.PLAYING) return
    val pos = if (s.myTurn) s.myPos else s.theirPos
    val q = MonopolyBoard.squares[pos]
    if (!q.ownable) return
    val key = "${s.myTurn}$pos${s.moves}"
    val flip = remember(key) { Animatable(0f) }
    LaunchedEffect(key) { flip.animateTo(1f, tween(650)) }
    val pulse = rememberInfiniteTransition(label = "offer").animateFloat(0.96f, 1.04f, infiniteRepeatable(tween(700), RepeatMode.Reverse), label = "offerPulse")
    val shape = RoundedCornerShape(12.dp)
    Column(
        modifier.width(220.dp).graphicsLayer {
            rotationY = (1f - flip.value) * 90f
            cameraDistance = 14f * density
            alpha = flip.value.coerceIn(0f, 1f)
        }.clip(shape).background(CardCream).border(3.dp, Ink, shape),
        horizontalAlignment = Alignment.CenterHorizontally,
    ) {
        Box(
            Modifier.fillMaxWidth().background(if (q.kind == SqKind.PROPERTY) groupColour(q.group) else Color(0xFF475569)).padding(vertical = 8.dp),
            contentAlignment = Alignment.Center,
        ) { Text("TITLE DEED", color = Color.White, fontWeight = FontWeight.Black, fontSize = 11.sp, letterSpacing = 2.sp) }
        Column(Modifier.padding(horizontal = 12.dp, vertical = 8.dp), horizontalAlignment = Alignment.CenterHorizontally, verticalArrangement = Arrangement.spacedBy(3.dp)) {
            Text(q.name.uppercase(), color = Ink, fontWeight = FontWeight.Black, fontSize = 17.sp, textAlign = TextAlign.Center)
            when (q.kind) {
                SqKind.PROPERTY -> {
                    Text("Rent ${money(q.rents[0])} · with a hotel ${money(q.rents[5])}", color = Color(0xFF444444), fontSize = 11.sp)
                    Text("Houses ${money(q.houseCost)} each", color = Color(0xFF444444), fontSize = 11.sp)
                }
                SqKind.STATION -> Text("Rent £25 for one station, up to £200 for all four", color = Color(0xFF444444), fontSize = 11.sp, textAlign = TextAlign.Center)
                else -> Text("Rent is 4× the dice (10× with both utilities)", color = Color(0xFF444444), fontSize = 11.sp, textAlign = TextAlign.Center)
            }
            Text(money(q.price), color = Color(0xFF166534), fontWeight = FontWeight.Black, fontSize = 30.sp, modifier = Modifier.graphicsLayer { if (s.myTurn) { scaleX = pulse.value; scaleY = pulse.value } })
            if (s.myTurn) {
                Row(horizontalArrangement = Arrangement.spacedBy(6.dp), modifier = Modifier.fillMaxWidth()) {
                    val can = s.myCash >= q.price
                    Box(
                        Modifier.weight(1f).clip(RoundedCornerShape(10.dp)).background(if (can) Color(0xFF2E7D32) else Color(0xFF9CA3AF))
                            .clickable(enabled = can) { engine.buy() }.padding(vertical = 10.dp),
                        contentAlignment = Alignment.Center,
                    ) { Text("BUY", color = Color.White, fontWeight = FontWeight.Black, fontSize = 16.sp) }
                    Box(
                        Modifier.weight(1f).clip(RoundedCornerShape(10.dp)).background(Color(0xFFC62828))
                            .clickable { engine.declineToAuction() }.padding(vertical = 10.dp),
                        contentAlignment = Alignment.Center,
                    ) { Text("AUCTION", color = Color.White, fontWeight = FontWeight.Black, fontSize = 14.sp) }
                }
                if (s.myCash < q.price) Text("Not enough cash: it goes to auction", color = Color(0xFF9A3412), fontSize = 10.sp)
            } else {
                Text("🤔 ${s.theirName} is deciding…", color = Color(0xFF555555), fontWeight = FontWeight.Bold, fontSize = 12.sp)
            }
        }
    }
}

private class Stage(val emoji: String, val title: String, val sub: String, val stamp: String, val tint: Color)

/** A little show for everything else that happens: a bouncing emoji, a headline, and a stamp that slams down. Tap to dismiss. */
@Composable
private fun EventStage(s: MonopolyState, modifier: Modifier) {
    var stage by remember { mutableStateOf<Stage?>(null) }
    val a = remember { Animatable(1f) }
    LaunchedEffect(s.seq) {
        if (s.seq == 0) return@LaunchedEffect
        val who = if (s.evByMe) "You" else s.theirName
        val name = if (s.evSq in 0 until MonopolyBoard.SIZE) MonopolyBoard.squares[s.evSq].name else ""
        val amt = kotlin.math.abs(s.evAmount)
        val green = Color(0xFF4ADE80)
        val red = Color(0xFFF87171)
        val gold = Color(0xFFFFD23F)
        val st: Stage? = when (s.ev) {
            "buy" -> Stage("🏠", "SOLD!", "$who bought $name${if (amt > 0) " for ${money(amt)}" else ""}", "SOLD", green)
            "build" -> {
                val h = if (s.evSq >= 0) s.houses[s.evSq] else 0
                Stage("🔨", if (h == 5) "HOTEL!" else "BUILT!", "$who built on $name · ${if (h == 5) "now a hotel" else "$h house${if (h == 1) "" else "s"}"}", "", Color(0xFFFB923C))
            }
            "tax" -> Stage("🧾", "TAXMAN!", "$who paid ${money(amt)} to the bank", "PAID", red)
            "go" -> Stage("🏁", "PASSED GO!", "$who collected ${money(if (amt > 0) amt else 200)}", "", gold)
            "jail" -> Stage("🚔", "BUSTED!", "$who went straight to jail", "JAILED", Color(0xFF60A5FA))
            "auction" -> Stage("🔨", "AUCTION!", "Bidding starts for $name", "", gold)
            "trade" -> Stage("🤝", "DEAL DONE!", "Properties and cash changed hands", "", green)
            "mortgage" -> Stage("🏦", "MORTGAGED", "$who mortgaged $name", "MORTGAGED", Color(0xFF9CA3AF))
            "sell" -> Stage("💷", "SOLD A HOUSE", "$who sold a house on $name", "", Color(0xFFFB923C))
            "bankrupt" -> Stage("💥", "BANKRUPT!", "$who couldn't pay what was owed", "GAME OVER", red)
            "roll" -> if (s.d1 > 0 && s.d1 == s.d2) Stage("🎲", "DOUBLES!", "$who rolled ${s.d1} and ${s.d2}: roll again!", "", gold) else null
            else -> null
        }
        if (st == null) return@LaunchedEffect
        stage = st
        a.snapTo(0f)
        a.animateTo(1f, tween(3000, easing = LinearEasing))
    }
    val st = stage
    if (st != null && a.value < 1f) {
        val p = a.value
        LaunchedEffect(st) { if (st.stamp.isNotEmpty()) { delay(600); Sfx.play(Sound.THUD) } }
        val enter = ease((p / 0.1f).coerceIn(0f, 1f))
        val exit = ((1f - p) / 0.15f).coerceIn(0f, 1f)
        val shape = RoundedCornerShape(18.dp)
        Box(
            modifier.padding(top = 10.dp).graphicsLayer {
                scaleX = 0.5f + 0.5f * enter; scaleY = 0.5f + 0.5f * enter; alpha = exit
                translationY = (1f - enter) * -60f * density
            }.clip(shape).background(Color(0xEE0B1220)).border(2.5.dp, st.tint, shape).clickable { stage = null },
        ) {
            Column(Modifier.padding(horizontal = 18.dp, vertical = 8.dp), horizontalAlignment = Alignment.CenterHorizontally) {
                Text(
                    st.emoji, fontSize = 38.sp,
                    modifier = Modifier.graphicsLayer {
                        val b = 1f + kotlin.math.abs(kotlin.math.sin(p * 18f)) * 0.25f * (1f - p)
                        scaleX = b; scaleY = b; rotationZ = kotlin.math.sin(p * 14f) * 12f * (1f - p)
                    },
                )
                Text(st.title, color = st.tint, fontWeight = FontWeight.Black, fontSize = 22.sp, letterSpacing = 1.sp)
                Text(st.sub, color = Color.White, fontSize = 12.sp, textAlign = TextAlign.Center)
            }
            if (st.stamp.isNotEmpty() && p > 0.2f) {
                val sp = ((p - 0.2f) / 0.06f).coerceIn(0f, 1f)
                Box(
                    Modifier.align(Alignment.CenterEnd).padding(end = 6.dp).graphicsLayer {
                        val sc = 2.6f - 1.6f * ease(sp)
                        scaleX = sc; scaleY = sc; rotationZ = -16f; alpha = sp
                    }.border(3.dp, st.tint, RoundedCornerShape(6.dp)).padding(horizontal = 6.dp, vertical = 1.dp),
                ) { Text(st.stamp, color = st.tint, fontWeight = FontWeight.Black, fontSize = 14.sp) }
            }
        }
    }
}
/** Every property at a glance, grouped by colour, with a ring and token showing who owns it. Tap one for its deed. */
@Composable
private fun DeedTracker(s: MonopolyState, myCol: Color, theirCol: Color, myToken: String, theirToken: String, onTap: (Int) -> Unit) {
    val rows = buildList {
        for (g in 0..7) add(Triple(MonopolyBoard.GROUP_NAMES[g], groupColour(g), MonopolyBoard.GROUPS[g]))
        add(Triple("Stations", Color(0xFF475569), (0 until MonopolyBoard.SIZE).filter { MonopolyBoard.squares[it].kind == SqKind.STATION }))
        add(Triple("Utilities", Color(0xFF94A3B8), (0 until MonopolyBoard.SIZE).filter { MonopolyBoard.squares[it].kind == SqKind.UTILITY }))
    }
    Column(
        Modifier.fillMaxWidth().clip(RoundedCornerShape(12.dp)).background(Color(0x55000000)).padding(10.dp),
        verticalArrangement = Arrangement.spacedBy(5.dp),
    ) {
        Text("Who owns what", fontSize = 11.sp, fontWeight = FontWeight.Bold, color = Color.White.copy(alpha = 0.7f))
        Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
            Text("$myToken You", fontSize = 11.sp, color = myCol, fontWeight = FontWeight.Bold)
            Text("$theirToken ${s.theirName}", fontSize = 11.sp, color = theirCol, fontWeight = FontWeight.Bold, maxLines = 1)
        }
        rows.forEach { (label, colour, squares) ->
            val mineAll = squares.all { s.owner[it] == 1 }
            val theirsAll = squares.all { s.owner[it] == 2 }
            Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(4.dp)) {
                Text(label, fontSize = 10.sp, color = Color.White.copy(alpha = 0.85f), modifier = Modifier.width(62.dp), maxLines = 1)
                squares.forEach { sq ->
                    val o = s.owner[sq]
                    val ring = when (o) { 1 -> myCol; 2 -> theirCol; else -> Color.White.copy(alpha = 0.25f) }
                    val shape = RoundedCornerShape(6.dp)
                    Box(
                        Modifier.size(30.dp).clip(shape).background(colour.copy(alpha = if (o == 0) 0.35f else if (s.mortgaged[sq]) 0.45f else 1f))
                            .border(if (o == 0) 1.dp else 3.dp, ring, shape).clickable { onTap(sq) },
                        contentAlignment = Alignment.Center,
                    ) {
                        if (o != 0) Text(if (o == 1) myToken else theirToken, fontSize = 13.sp)
                        val h = s.houses[sq]
                        if (h > 0) Text(if (h == 5) "🏨" else "🏠$h", fontSize = 8.sp, modifier = Modifier.align(Alignment.BottomEnd))
                        if (s.mortgaged[sq]) Text("M", fontSize = 9.sp, fontWeight = FontWeight.Black, color = Color.White, modifier = Modifier.align(Alignment.TopStart).padding(2.dp))
                    }
                }
                if (mineAll || theirsAll) Text("★ SET", fontSize = 10.sp, fontWeight = FontWeight.Black, color = if (mineAll) myCol else theirCol)
            }
        }
    }
}

// ============================================================================ building and sets

/** Draws in a square's own frame (origin at its centre, 'up' = towards the middle of the board), like [drawCell]. */
private fun DrawScope.inCell(i: Int, block: DrawScope.(Float, Float) -> Unit) {
    val side = sideOf(i)
    if (side == -1) return
    val r = cellRect(i)
    val deg = when (side) { 1 -> 90f; 2 -> 180f; 3 -> 270f; else -> 0f }
    withTransform({ translate(r.center.x, r.center.y); rotate(deg, Offset.Zero) }) { block(UNIT, CORNER) }
}

/**
 * For each colour set where I own some squares but not all: the squares I still need. [from] 0 = nobody owns them (a place I
 * could land and buy), 2 = the other player owns them (a trade would be needed).
 */
private fun setNeeds(s: MonopolyState, from: Int): List<Int> {
    val out = ArrayList<Int>()
    for (g in 0..7) {
        val sqs = MonopolyBoard.GROUPS[g]
        val mine = sqs.count { s.owner[it] == 1 }
        if (mine == 0 || mine == sqs.size) continue
        // only worth highlighting when it is realistic: I own at least half of the set
        if (mine * 2 < sqs.size) continue
        sqs.filter { s.owner[it] == from }.forEach { out.add(it) }
    }
    return out
}

private class Advice(val text: String, val action: String)

/** One helpful, specific nudge about building or completing a set. */
private fun adviceFor(s: MonopolyState): Advice? {
    if (s.phase != GPhase.PLAYING || s.step == "FIRST" || s.step == "AUCTION") return null
    val owner = s.ownerArr; val houses = s.housesArr; val mort = s.mortArr
    val buildable = (0 until MonopolyBoard.SIZE).filter { MonopolyRules.canBuild(owner, houses, mort, 1, it, s.myCash, s.housesLeft, s.hotelsLeft) }
    if (s.canManage && s.step != "DEBT" && buildable.isNotEmpty()) {
        val q = MonopolyBoard.squares[buildable.minByOrNull { houses[it] }!!]
        return Advice("🔨 You can build! Tap a glowing green square. ${q.name} costs ${money(q.houseCost)} per house.", "build")
    }
    for (g in 0..7) {
        if (!MonopolyBoard.ownsGroup(owner, 1, g)) continue
        val sqs = MonopolyBoard.GROUPS[g]
        if (sqs.all { houses[it] == 5 }) continue
        if (sqs.any { mort[it] }) return Advice("🏦 Lift the mortgage on your ${MonopolyBoard.GROUP_NAMES[g]} set so you can build on it.", "build")
        val cost = MonopolyBoard.squares[sqs[0]].houseCost
        return Advice("⭐ You own the whole ${MonopolyBoard.GROUP_NAMES[g]} set! Houses cost ${money(cost)}: " + (if (s.myCash < cost) "save up ${money(cost - s.myCash)} more to build." else "build evenly on every street."), "build")
    }
    var best: Advice? = null
    var bestMine = 0
    for (g in 0..7) {
        val sqs = MonopolyBoard.GROUPS[g]
        val mine = sqs.count { owner[it] == 1 }
        val missing = sqs.filter { owner[it] != 1 }
        if (mine == 0 || missing.size != 1 || mine < bestMine) continue
        val m = missing[0]
        val name = MonopolyBoard.squares[m].name
        bestMine = mine
        best = if (owner[m] == 0) Advice("🎯 Just one ${MonopolyBoard.GROUP_NAMES[g]} street left to complete your set: $name. Land on it to buy!", "")
        else Advice("🤝 ${s.theirName} owns $name. Trade for it to complete your ${MonopolyBoard.GROUP_NAMES[g]} set!", "trade")
    }
    if (best != null) return best
    for (g in 0..7) {
        val sqs = MonopolyBoard.GROUPS[g]
        val theirs = sqs.count { owner[it] == 2 }
        val missing = sqs.filter { owner[it] != 2 }
        if (theirs > 0 && missing.size == 1 && owner[missing[0]] == 0) return Advice("⚠️ ${s.theirName} needs only ${MonopolyBoard.squares[missing[0]].name} for the ${MonopolyBoard.GROUP_NAMES[g]} set. Grab it first!", "")
    }
    return null
}

/** A pulsing coach tip in the side panel; tap to act on it. */
@Composable
private fun AdviceChip(s: MonopolyState, onManage: () -> Unit, onTrade: () -> Unit) {
    val adv = adviceFor(s) ?: return
    val glow by rememberInfiniteTransition(label = "advice").animateFloat(0.35f, 1f, infiniteRepeatable(tween(900), RepeatMode.Reverse), label = "adviceGlow")
    val shape = RoundedCornerShape(12.dp)
    Text(
        adv.text, color = Color.White, fontSize = 12.sp, fontWeight = FontWeight.SemiBold,
        modifier = Modifier.fillMaxWidth().clip(shape).background(Color(0x66000000)).border(2.dp, Color(0xFFFFD23F).copy(alpha = glow), shape)
            .clickable { if (adv.action == "build") onManage() else if (adv.action == "trade") onTrade() }.padding(10.dp),
    )
}

/** "SET COMPLETE!" when somebody gets the last street of a colour. */
@Composable
private fun SetBanner(s: MonopolyState, modifier: Modifier) {
    class Done(val text: String, val col: Color)
    var done by remember { mutableStateOf<Done?>(null) }
    val a = remember { Animatable(1f) }
    val before = remember { Seen(s.owner, s.houses) }
    LaunchedEffect(s.owner) {
        var found: Done? = null
        for (g in 0..7) {
            val sqs = MonopolyBoard.GROUPS[g]
            for (who in 1..2) {
                val now = sqs.all { s.owner[it] == who }
                val was = sqs.all { before.owner[it] == who }
                if (now && !was) found = Done(
                    "⭐ SET COMPLETE!\n${if (who == 1) "You own" else "${s.theirName} owns"} all the ${MonopolyBoard.GROUP_NAMES[g]} streets: " + (if (who == 1) "build houses now!" else "watch out for their houses!"),
                    if (who == 1) Color(0xFF4ADE80) else Color(0xFFF87171),
                )
            }
        }
        before.owner = s.owner
        val f = found ?: return@LaunchedEffect
        Sfx.play(Sound.WIN)
        done = f
        a.snapTo(0f)
        a.animateTo(1f, tween(3400, easing = LinearEasing))
    }
    val d = done
    if (d != null && a.value < 1f) {
        val p = a.value
        val enter = ease((p / 0.1f).coerceIn(0f, 1f))
        val exit = ((1f - p) / 0.12f).coerceIn(0f, 1f)
        val shape = RoundedCornerShape(16.dp)
        Text(
            d.text, color = Color.White, fontWeight = FontWeight.Black, fontSize = 14.sp, textAlign = TextAlign.Center,
            modifier = modifier.padding(top = 120.dp).graphicsLayer {
                val sc = 0.6f + 0.4f * enter + kotlin.math.sin(p * 30f) * 0.03f * (1f - p)
                scaleX = sc; scaleY = sc; alpha = exit
            }.clip(shape).background(Color(0xEE0B1220)).border(3.dp, d.col, shape).padding(horizontal = 16.dp, vertical = 10.dp),
        )
    }
}

/** Tap one of your streets and this opens: its houses, what the next one costs and adds, and why you can't if you can't. */
@Composable
private fun BuildCard(s: MonopolyState, engine: MonopolyEngine, sq: Int, onClose: () -> Unit, onDeed: () -> Unit, modifier: Modifier) {
    val q = MonopolyBoard.squares[sq]
    val owner = s.ownerArr; val houses = s.housesArr; val mort = s.mortArr
    val h = houses[sq]
    val grp = MonopolyBoard.GROUPS[q.group]
    val canBuild = s.canManage && s.step != "DEBT" && MonopolyRules.canBuild(owner, houses, mort, 1, sq, s.myCash, s.housesLeft, s.hotelsLeft)
    val canSell = s.canManage && MonopolyRules.canSell(owner, houses, 1, sq, s.housesLeft)
    val reason = when {
        !s.canManage -> "You can build on your own turn."
        s.step == "DEBT" -> "Pay your debt first."
        h >= 5 -> "Already a hotel: the most you can have."
        !MonopolyBoard.ownsGroup(owner, 1, q.group) -> "Own all ${grp.size} ${MonopolyBoard.GROUP_NAMES[q.group]} streets to build (you have ${grp.count { owner[it] == 1 }})."
        grp.any { mort[it] } -> "Lift the mortgage on this set first."
        grp.any { houses[it] < h } -> "Build evenly: add to the other ${MonopolyBoard.GROUP_NAMES[q.group]} streets first."
        s.myCash < q.houseCost -> "You need ${money(q.houseCost - s.myCash)} more cash."
        h == 4 && s.hotelsLeft == 0 -> "No hotels left in the bank."
        h < 4 && s.housesLeft == 0 -> "No houses left in the bank."
        else -> ""
    }
    val nextRent = if (h < 5) {
        val copy = houses.copyOf(); copy[sq] = h + 1
        MonopolyRules.rent(owner, copy, mort, sq, 7)
    } else 0
    val nowRent = MonopolyRules.rent(owner, houses, mort, sq, 7)
    val pop = remember(sq) { Animatable(0f) }
    LaunchedEffect(sq) { pop.animateTo(1f, tween(300)) }
    val shape = RoundedCornerShape(14.dp)
    Column(
        modifier.width(240.dp).graphicsLayer { val sc = 0.7f + 0.3f * pop.value; scaleX = sc; scaleY = sc; alpha = pop.value }
            .clip(shape).background(CardCream).border(3.dp, Ink, shape),
        horizontalAlignment = Alignment.CenterHorizontally,
    ) {
        Box(Modifier.fillMaxWidth().background(groupColour(q.group)).padding(vertical = 7.dp), contentAlignment = Alignment.Center) {
            Text(q.name.uppercase(), color = Color.White, fontWeight = FontWeight.Black, fontSize = 14.sp, textAlign = TextAlign.Center)
        }
        Column(Modifier.padding(horizontal = 12.dp, vertical = 8.dp), horizontalAlignment = Alignment.CenterHorizontally, verticalArrangement = Arrangement.spacedBy(4.dp)) {
            // four house slots and a hotel slot
            Row(horizontalArrangement = Arrangement.spacedBy(4.dp), verticalAlignment = Alignment.CenterVertically) {
                for (k in 1..4) Text(if (h >= k || h == 5) "🏠" else "▫️", fontSize = 22.sp)
                Text(if (h == 5) "🏨" else "▫️", fontSize = 26.sp)
            }
            Text("Rent now ${money(nowRent)}" + if (h < 5) "  →  ${money(nextRent)}" else "", color = Ink, fontWeight = FontWeight.Black, fontSize = 15.sp)
            Text(
                "${MonopolyBoard.GROUP_NAMES[q.group]} set: " + grp.joinToString(" ") { if (owner[it] == 1) "🟩" else if (owner[it] == 2) "🟥" else "⬜" } +
                    "   ·   ${houses.let { hs -> grp.sumOf { hs[it] } }} houses in the set",
                color = Color(0xFF444444), fontSize = 10.sp, textAlign = TextAlign.Center,
            )
            if (reason.isNotEmpty()) Text(reason, color = Color(0xFF9A3412), fontSize = 11.sp, fontWeight = FontWeight.SemiBold, textAlign = TextAlign.Center)
            Row(horizontalArrangement = Arrangement.spacedBy(6.dp), modifier = Modifier.fillMaxWidth()) {
                Box(
                    Modifier.weight(1f).clip(RoundedCornerShape(10.dp)).background(if (canBuild) Color(0xFF2E7D32) else Color(0xFF9CA3AF))
                        .clickable(enabled = canBuild) { engine.build(sq) }.padding(vertical = 9.dp),
                    contentAlignment = Alignment.Center,
                ) { Text(if (h == 4) "🏨 HOTEL ${money(q.houseCost)}" else "🔨 BUILD ${money(q.houseCost)}", color = Color.White, fontWeight = FontWeight.Black, fontSize = 12.sp) }
                Box(
                    Modifier.weight(0.7f).clip(RoundedCornerShape(10.dp)).background(if (canSell) Color(0xFFD97706) else Color(0xFFD1D5DB))
                        .clickable(enabled = canSell) { engine.sellHouse(sq) }.padding(vertical = 9.dp),
                    contentAlignment = Alignment.Center,
                ) { Text("SELL +${money(q.houseCost / 2)}", color = Color.White, fontWeight = FontWeight.Black, fontSize = 11.sp) }
            }
            Row(horizontalArrangement = Arrangement.spacedBy(14.dp)) {
                Text("Deed", color = Color(0xFF1D4ED8), fontWeight = FontWeight.Bold, fontSize = 12.sp, modifier = Modifier.clickable { onDeed() }.padding(4.dp))
                Text("Close", color = Color(0xFF555555), fontWeight = FontWeight.Bold, fontSize = 12.sp, modifier = Modifier.clickable { onClose() }.padding(4.dp))
            }
        }
    }
}
