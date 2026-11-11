package com.neil1454.drawsomething.ui

import androidx.compose.animation.core.Animatable
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
import androidx.compose.ui.text.TextMeasurer
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
        Text("• Roll the dice and move. Buy the property you land on, or let it go to auction. Land on someone else's and pay rent.")
        Text("• Own every street of a colour to build houses (evenly), then hotels. Rent shoots up. Stations and utilities pay more the more you own.")
        Text("• Chance and Community Chest cards, Income Tax, Super Tax, and Jail (pay £50, use a card, or roll a double). Three doubles in a row sends you to Jail.")
        Text("• Short of cash? Mortgage property or sell buildings. You can also trade properties and cash with the other player.")
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

/** Grid cell (column, row) of square [i] on the 11x11 board; GO is at the bottom right, going clockwise. */
private fun gridXY(i: Int): Pair<Int, Int> = when {
    i <= 10 -> (10 - i) to 10
    i <= 20 -> 0 to (20 - i)
    i <= 30 -> (i - 20) to 0
    else -> 10 to (i - 30)
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

    BoxWithConstraints(Modifier.fillMaxSize()) {
        val landscape = maxWidth > maxHeight
        val boardSide = if (landscape) minOf(maxHeight - 16.dp, maxWidth * 0.55f) else minOf(maxWidth - 12.dp, maxHeight * 0.58f)

        val board: @Composable () -> Unit = {
            Box(Modifier.size(boardSide)) {
                MonopolyBoardView(s, myCol, theirCol, myToken, theirToken) { deedSq = it }
                // the middle of the board
                Box(Modifier.align(Alignment.Center).size(boardSide * 0.8f), contentAlignment = Alignment.Center) {
                    BoardCentre(s, over, boardSide * 0.8f)
                }
                if (cardShown == s.seq && s.cardText.isNotBlank() && s.ev == "card") {
                    CardPopup(s, Modifier.align(Alignment.Center).clickable { cardShown = -1 })
                }
            }
        }
        val panel: @Composable (Modifier) -> Unit = { m ->
            Column(m, verticalArrangement = Arrangement.spacedBy(8.dp), horizontalAlignment = Alignment.CenterHorizontally) {
                PlayerRow(s, myCol, theirCol, myToken, theirToken)
                ActionBar(
                    s, engine, over,
                    onManage = { showManage = true }, onTrade = { showTrade = true }, onBankrupt = { confirmBankrupt = true },
                )
                LogBox(s)
            }
        }
        if (!landscape) {
            Column(Modifier.fillMaxSize().verticalScroll(rememberScrollState()).padding(6.dp), horizontalAlignment = Alignment.CenterHorizontally, verticalArrangement = Arrangement.spacedBy(8.dp)) {
                board()
                panel(Modifier.fillMaxWidth())
                Spacer(Modifier.height(24.dp))
            }
        } else {
            Row(Modifier.fillMaxSize().padding(8.dp), horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                Box(Modifier.align(Alignment.CenterVertically)) { board() }
                panel(Modifier.weight(1f).fillMaxSize().verticalScroll(rememberScrollState()))
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

private fun cellBox(i: Int, c: Float): Pair<Offset, Size> {
    val (gx, gy) = gridXY(i)
    return Offset(gx * c, gy * c) to Size(c, c)
}

@Composable
private fun MonopolyBoardView(s: MonopolyState, myCol: Color, theirCol: Color, myToken: String, theirToken: String, onTapSquare: (Int) -> Unit) {
    val measurer = rememberTextMeasurer()
    Box(Modifier.fillMaxSize()) {
        Canvas(
            Modifier.fillMaxSize().pointerInput(Unit) {
                detectTapGestures { tap ->
                    val c = size.width / 11f
                    val gx = (tap.x / c).toInt().coerceIn(0, 10)
                    val gy = (tap.y / c).toInt().coerceIn(0, 10)
                    val i = (0 until MonopolyBoard.SIZE).firstOrNull { gridXY(it) == (gx to gy) }
                    if (i != null) onTapSquare(i)
                }
            },
        ) {
            val c = size.width / 11f
            drawRoundRect(Color(0xFF1B5E3A), Offset.Zero, size, CornerRadius(c * 0.3f))
            drawRect(BoardGreen, Offset(c, c), Size(9 * c, 9 * c))
            for (i in 0 until MonopolyBoard.SIZE) drawSquare(s, i, c, measurer, myCol, theirCol)
        }
        // tokens live on their own layer so moving them doesn't redraw the board
        TokensLayer(s, myToken, theirToken, myCol, theirCol)
    }
}

private fun androidx.compose.ui.graphics.drawscope.DrawScope.drawSquare(
    s: MonopolyState, i: Int, c: Float, m: TextMeasurer, myCol: Color, theirCol: Color,
) {
    val sq = MonopolyBoard.squares[i]
    val (o, sz) = cellBox(i, c)
    val corner = i % 10 == 0
    drawRect(Color(0xFFF6F2E4), o, sz)
    drawRect(Color(0xFF2F4F3F), o, sz, style = Stroke(1.2f))
    val (gx, gy) = gridXY(i)
    // which side faces the middle
    val inward = when {
        gy == 10 -> 0 // bottom row: inner side is up
        gx == 0 -> 1  // left column: inner side is right
        gy == 0 -> 2  // top row: inner side is down
        else -> 3     // right column: inner side is left
    }
    if (sq.kind == SqKind.PROPERTY && !corner) {
        val t = c * 0.24f
        val band = groupColour(sq.group)
        val (bo, bs) = when (inward) {
            0 -> Offset(o.x, o.y) to Size(c, t)
            1 -> Offset(o.x + c - t, o.y) to Size(t, c)
            2 -> Offset(o.x, o.y + c - t) to Size(c, t)
            else -> Offset(o.x, o.y) to Size(t, c)
        }
        drawRect(band, bo, bs)
        drawRect(Color.Black.copy(alpha = 0.4f), bo, bs, style = Stroke(1f))
        // buildings sit on the band
        val h = s.houses[i]
        if (h in 1..4) {
            val w = t * 0.7f
            for (k in 0 until h) {
                val p = when (inward) {
                    0 -> Offset(o.x + c * 0.1f + k * (c * 0.8f / 4f), o.y + (t - w) / 2f)
                    2 -> Offset(o.x + c * 0.1f + k * (c * 0.8f / 4f), o.y + c - t + (t - w) / 2f)
                    1 -> Offset(o.x + c - t + (t - w) / 2f, o.y + c * 0.1f + k * (c * 0.8f / 4f))
                    else -> Offset(o.x + (t - w) / 2f, o.y + c * 0.1f + k * (c * 0.8f / 4f))
                }
                drawRect(Color(0xFF1B8A3A), p, Size(w, w))
            }
        } else if (h == 5) {
            val p = when (inward) {
                0 -> Offset(o.x + c * 0.25f, o.y + t * 0.12f)
                2 -> Offset(o.x + c * 0.25f, o.y + c - t + t * 0.12f)
                1 -> Offset(o.x + c - t + t * 0.12f, o.y + c * 0.25f)
                else -> Offset(o.x + t * 0.12f, o.y + c * 0.25f)
            }
            val hs = if (inward == 0 || inward == 2) Size(c * 0.5f, t * 0.76f) else Size(t * 0.76f, c * 0.5f)
            drawRect(Color(0xFFD62828), p, hs)
        }
    }
    val label = when (sq.kind) {
        SqKind.GO -> "GO"
        SqKind.JAIL -> "JAIL"
        SqKind.PARKING -> "FREE\nPARKING"
        SqKind.GOTOJAIL -> "GO TO\nJAIL"
        SqKind.CHANCE -> "CHANCE"
        SqKind.CHEST -> "CHEST"
        else -> sq.short
    }
    val icon = when (sq.kind) {
        SqKind.GO -> "➡️"
        SqKind.JAIL -> "🚔"
        SqKind.PARKING -> "🅿️"
        SqKind.GOTOJAIL -> "👮"
        SqKind.CHANCE -> "❓"
        SqKind.CHEST -> "📦"
        SqKind.STATION -> "🚂"
        SqKind.UTILITY -> if (sq.name.startsWith("Electric")) "💡" else "🚰"
        SqKind.TAX -> "💰"
        else -> ""
    }
    val bandT = if (sq.kind == SqKind.PROPERTY) c * 0.24f else 0f
    // the free area of the square, away from the colour band
    val areaX = when (inward) { 3 -> o.x + bandT; else -> o.x }
    val areaW = if (inward == 1 || inward == 3) c - bandT else c
    val areaTop = if (inward == 0) o.y + bandT else o.y
    val areaH = if (inward == 0 || inward == 2) c - bandT else c
    val cx = areaX + areaW / 2f
    val fs = (c / 6.2f).coerceAtLeast(4.5f)
    val style = TextStyle(fontSize = (fs / density).sp, fontWeight = FontWeight.Bold, color = Color(0xFF1A1A1A), textAlign = TextAlign.Center)
    val r = m.measure(label, style, overflow = TextOverflow.Ellipsis, maxLines = 3, constraints = Constraints(maxWidth = (areaW * 0.96f).toInt().coerceAtLeast(8)))
    drawText(r, topLeft = Offset(cx - r.size.width / 2f, areaTop + c * 0.04f))
    if (icon.isNotEmpty()) {
        val ir = m.measure(icon, TextStyle(fontSize = (c / 3.4f / density).sp))
        drawText(ir, topLeft = Offset(cx - ir.size.width / 2f, areaTop + areaH * 0.52f - ir.size.height / 2f + c * 0.06f))
    }
    if (sq.price > 0 && s.owner[i] == 0) {
        val pr = m.measure("£${sq.price}", TextStyle(fontSize = (c / 6.0f / density).sp, color = Color(0xFF444444)))
        drawText(pr, topLeft = Offset(cx - pr.size.width / 2f, areaTop + areaH - pr.size.height - c * 0.03f))
    }
    // owner stripe along the outer edge, and a grey wash if mortgaged
    if (s.owner[i] != 0) {
        val col = if (s.owner[i] == 1) myCol else theirCol
        val t = c * 0.12f
        val (eo, es) = when (inward) {
            0 -> Offset(o.x, o.y + c - t) to Size(c, t)
            1 -> Offset(o.x, o.y) to Size(t, c)
            2 -> Offset(o.x, o.y) to Size(c, t)
            else -> Offset(o.x + c - t, o.y) to Size(t, c)
        }
        drawRect(col, eo, es)
    }
    if (s.mortgaged[i]) {
        drawRect(Color(0xAA6B7280), o, sz)
        val mr = m.measure("M", TextStyle(fontSize = (c / 2.2f / density).sp, fontWeight = FontWeight.Black, color = Color.White))
        drawText(mr, topLeft = Offset(o.x + c / 2f - mr.size.width / 2f, o.y + c / 2f - mr.size.height / 2f))
    }
}

@Composable
private fun TokensLayer(s: MonopolyState, myToken: String, theirToken: String, myCol: Color, theirCol: Color) {
    val measurer = rememberTextMeasurer()
    val mine = remember { Animatable(s.myPos.toFloat()) }
    val theirs = remember { Animatable(s.theirPos.toFloat()) }
    LaunchedEffect(s.myPos) { hop(mine, s.myPos) }
    LaunchedEffect(s.theirPos) { hop(theirs, s.theirPos) }
    Canvas(Modifier.fillMaxSize()) {
        val c = size.width / 11f
        fun place(pos: Float, token: String, col: Color, dx: Float) {
            val a = pos.toInt() % MonopolyBoard.SIZE
            val b = (a + 1) % MonopolyBoard.SIZE
            val f = pos - pos.toInt()
            val (ga, gb) = gridXY(a) to gridXY(b)
            val x = (ga.first + (gb.first - ga.first) * f + 0.5f) * c + dx * c
            val y = (ga.second + (gb.second - ga.second) * f + 0.5f) * c + dx * c * 0.4f + c * 0.18f
            drawCircle(col.copy(alpha = 0.9f), c * 0.3f, Offset(x, y))
            drawCircle(Color.White, c * 0.3f, Offset(x, y), style = Stroke(2.5f))
            val r = measurer.measure(token, TextStyle(fontSize = (c * 0.42f / density).sp))
            drawText(r, topLeft = Offset(x - r.size.width / 2f, y - r.size.height / 2f))
        }
        place(theirs.value, theirToken, theirCol, 0.18f)
        place(mine.value, myToken, myCol, -0.18f)
    }
}

private suspend fun hop(a: Animatable<Float, androidx.compose.animation.core.AnimationVector1D>, target: Int) {
    val cur = a.value % MonopolyBoard.SIZE
    val fwd = (target - cur + MonopolyBoard.SIZE) % MonopolyBoard.SIZE
    if (fwd == 0f) { a.snapTo(target.toFloat()); return }
    if (fwd > 12f) {
        a.snapTo(target.toFloat())
        return
    }
    a.snapTo(cur)
    a.animateTo(cur + fwd, tween((fwd * 110).toInt().coerceIn(150, 1300), easing = LinearEasing))
    a.snapTo(target.toFloat())
}

@Composable
private fun BoardCentre(s: MonopolyState, over: Boolean, side: androidx.compose.ui.unit.Dp) {
    var shown1 by remember { mutableIntStateOf(s.d1) }
    var shown2 by remember { mutableIntStateOf(s.d2) }
    LaunchedEffect(s.moves, s.d1, s.d2) {
        if (s.d1 > 0 && s.moves > 0 && s.ev == "roll") {
            repeat(6) { shown1 = (1..6).random(); shown2 = (1..6).random(); delay(60) }
        }
        shown1 = s.d1; shown2 = s.d2
    }
    Column(horizontalAlignment = Alignment.CenterHorizontally, verticalArrangement = Arrangement.spacedBy(4.dp)) {
        Box(
            Modifier.clip(RoundedCornerShape(6.dp)).background(Color(0xFFD62828)).padding(horizontal = 14.dp, vertical = 3.dp),
        ) { Text("MONOPOLY", fontSize = 16.sp, fontWeight = FontWeight.Black, color = Color.White, letterSpacing = 3.sp) }
        Text("London edition", fontSize = 9.sp, color = Color(0xFF2F4F3F))
        if (shown1 > 0) {
            Row(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                Text(die(shown1), fontSize = 40.sp, color = Color(0xFF1A1A1A))
                Text(die(shown2), fontSize = 40.sp, color = Color(0xFF1A1A1A))
            }
            if (s.d1 == s.d2 && s.d1 > 0 && !over) Text("DOUBLE!", fontSize = 12.sp, fontWeight = FontWeight.Black, color = Color(0xFFD62828))
        }
        val line = s.log.lastOrNull().orEmpty()
        if (line.isNotBlank()) Text(line, fontSize = 11.sp, color = Color(0xFF1A1A1A), textAlign = TextAlign.Center, maxLines = 3, modifier = Modifier.width(side * 0.9f))
    }
}

@Composable
private fun CardPopup(s: MonopolyState, modifier: Modifier) {
    val chance = s.cardDeck == 0
    Column(
        modifier.width(210.dp).clip(RoundedCornerShape(14.dp))
            .background(if (chance) Color(0xFFFFB74D) else Color(0xFF81D4FA))
            .border(3.dp, Color(0xFF1A1A1A), RoundedCornerShape(14.dp)).padding(14.dp),
        horizontalAlignment = Alignment.CenterHorizontally, verticalArrangement = Arrangement.spacedBy(6.dp),
    ) {
        Text(if (chance) "❓ CHANCE" else "📦 COMMUNITY CHEST", fontWeight = FontWeight.Black, fontSize = 15.sp, color = Color(0xFF1A1A1A))
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
    if (s.maxTurns > 0 && !over) {
        Text("Turn ${minOf(s.myTurnsTaken, s.theirTurnsTaken) + 1} of ${s.maxTurns} · richest wins when time is up", fontSize = 12.sp, color = Color.White.copy(alpha = 0.8f))
    }
}

@Composable
private fun PlayerBox(name: String, cash: Int, props: Int, jail: Boolean, cards: Int, col: Color, active: Boolean, modifier: Modifier) {
    val shape = RoundedCornerShape(16.dp)
    Column(
        modifier.clip(shape).background(Color(0x66000000)).border(if (active) 3.dp else 1.dp, if (active) col else col.copy(alpha = 0.4f), shape).padding(10.dp),
        verticalArrangement = Arrangement.spacedBy(2.dp),
    ) {
        Text(name, fontWeight = FontWeight.Bold, color = col, maxLines = 1, fontSize = 14.sp)
        Text(money(cash), fontWeight = FontWeight.Black, color = Color.White, fontSize = 22.sp)
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
                        for (inc in listOf(10, 50, 100)) {
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
