package com.neil1454.drawsomething.ui

import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.tween
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.drawscope.DrawScope
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.drawText
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.rememberTextMeasurer
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.neil1454.drawsomething.core.GPhase
import com.neil1454.drawsomething.core.Ludo
import com.neil1454.drawsomething.core.LudoEngine
import com.neil1454.drawsomething.core.LudoState
import com.neil1454.drawsomething.core.Sound
import com.neil1454.drawsomething.core.WinStory
import com.neil1454.drawsomething.sound.Sfx
import kotlinx.coroutines.delay
import kotlin.math.PI
import kotlin.math.cos
import kotlin.math.hypot
import kotlin.math.sin

@Composable
fun LudoHelp() {
    Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
        Text("Ludo (called Frustration in the UK when it has the pop-up dice). Each player has four pieces and the aim is to get them all home first.")
        Text("• Tap the dice in the middle of the board to roll it. You need a 6 to bring a piece out, and a 6 gives you another go (three 6s in a row lose the turn).")
        Text("• Move once round the board, then up your own coloured home column.")
        Text("• Land exactly on a lone enemy piece to send it back to its base. Stars and start squares are safe.")
        Text("• To get a piece HOME you need the EXACT number. Roll too much and that piece can't move.")
    }
}

private val RedC = Color(0xFFE53935)
private val BlueC = Color(0xFF1E88E5)
private val GreenC = Color(0xFF43A047)
private val YellowC = Color(0xFFFDD835)

private fun baseSpot(who: Int, i: Int): Pair<Float, Float> {
    val ox = if (who == 1) 1.5f else 10.5f
    val oy = if (who == 1) 1.5f else 10.5f
    return (ox + (i % 2) * 2f + 0.5f) to (oy + (i / 2) * 2f + 0.5f)
}

@Composable
fun LudoScreen(
    s: LudoState,
    engine: LudoEngine,
    onPlayAgain: () -> Unit,
    onBackToGames: () -> Unit,
    onLeave: () -> Unit,
) {
    val over = s.phase == GPhase.OVER
    val myC = if (s.iAmHost) RedC else BlueC
    val theirC = if (s.iAmHost) BlueC else RedC
    var shown by remember { mutableIntStateOf(0) }
    LaunchedEffect(s.rolls, s.die) {
        if (s.die > 0 && s.rolls > 0) {
            Sfx.play(Sound.ROLL)
            repeat(7) { shown = (1..6).random(); delay(55) }
        }
        shown = s.die
    }
    LaunchedEffect(s.moves) {
        if (s.moves > 0) Sfx.play(when (s.event) { "capture" -> Sound.EXPLODE; "home" -> Sound.CHIME; "out" -> Sound.POP; else -> Sound.TAP })
    }
    val status = when {
        over -> "GAME OVER"
        s.myTurn && s.canRoll -> "👉  TAP THE DICE TO ROLL  👈"
        s.myTurn && s.legal.isNotEmpty() -> "👉  Rolled ${s.die}: tap a glowing piece"
        s.myTurn -> "Rolled ${s.die}: nothing can move…"
        s.die > 0 -> "${s.theirName} rolled ${s.die}…"
        else -> "⏳ ${s.theirName} is rolling…"
    }
    val note = when (s.event) {
        "capture" -> if (s.eventByMe) "💥 You sent a piece back to base!" else "💥 ${s.theirName} sent your piece back to base!"
        "home" -> if (s.eventByMe) "🏠 One of yours is home!" else "🏠 ${s.theirName} got a piece home"
        "out" -> if (s.eventByMe) "🚀 You brought a piece out" else "🚀 ${s.theirName} brought a piece out"
        "triple" -> "🎲 Three 6s in a row: turn lost"
        "stuck" -> if (s.eventByMe) "No piece can use that roll" else "${s.theirName} can't move"
        else -> if (s.die == 6 && !over) "A 6! Roll again after moving" else ""
    }
    BoxWithConstraints(Modifier.fillMaxSize()) {
        val landscape = maxWidth > maxHeight
        val pills: @Composable (Modifier) -> Unit = { m ->
            Row(m, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                PlayerPill("You", s.myHome, myC, s.myTurn && !over, Modifier.weight(1f), unit = "/4 home")
                PlayerPill(s.theirName, s.theirHome, theirC, !s.myTurn && !over, Modifier.weight(1f), unit = "/4 home")
            }
        }
        val board: @Composable (Modifier) -> Unit = { m ->
            BoxWithConstraints(m) {
                val side = minOf(maxWidth, maxHeight)
                Box(Modifier.size(side).align(Alignment.Center)) {
                    LudoBoard(s, shown, engine, myC, theirC)
                }
            }
        }
        if (!landscape) {
            Column(Modifier.fillMaxSize().padding(8.dp), verticalArrangement = Arrangement.spacedBy(8.dp), horizontalAlignment = Alignment.CenterHorizontally) {
                pills(Modifier.fillMaxWidth())
                TurnText(status, s.myTurn && !over)
                if (note.isNotEmpty()) Text(note, fontSize = 14.sp, fontWeight = FontWeight.Bold, color = Funk.Sun)
                board(Modifier.weight(1f).fillMaxWidth())
            }
        } else {
            Row(Modifier.fillMaxSize().padding(8.dp), horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                board(Modifier.weight(1f).fillMaxSize())
                Column(Modifier.width(230.dp).fillMaxSize(), verticalArrangement = Arrangement.spacedBy(8.dp), horizontalAlignment = Alignment.CenterHorizontally) {
                    pills(Modifier.fillMaxWidth())
                    TurnText(status, s.myTurn && !over)
                    if (note.isNotEmpty()) Text(note, fontSize = 14.sp, fontWeight = FontWeight.Bold, color = Funk.Sun)
                }
            }
        }
        if (over) {
            EndOverlay(
                title = when (s.result) { 1 -> "🏆 YOU WIN!"; else -> "🏆 ${s.theirName.uppercase()} WINS!" },
                subtitle = "Pieces home: you ${s.myHome}/4  ·  ${s.theirName} ${s.theirHome}/4",
                how = (if (s.result == 1) "You" else s.theirName) + " got all four pieces home first, using the exact roll for the last one" +
                    ", after ${s.moves} moves. The other player had ${if (s.result == 1) s.theirHome else s.myHome} home (" +
                    WinStory.by(4, if (s.result == 1) s.theirHome else s.myHome, "piece") + ").",
                win = s.result == 1, isHost = engine.isHost, theirName = s.theirName,
                playAgainLabel = "Play again", colors = listOf(RedC, BlueC),
                onPlayAgain = onPlayAgain, onBackToGames = onBackToGames, onLeave = onLeave,
            )
        }
    }
}

/** Where a piece is drawn, in board cells (the middle of its square). */
private fun spot(who: Int, i: Int, p: Int): Pair<Float, Float> {
    if (p == Ludo.BASE) return baseSpot(who, i)
    val xy = Ludo.xy(who, p) ?: return baseSpot(who, i)
    return (xy.first + 0.5f) to (xy.second + 0.5f)
}

@Composable
private fun LudoBoard(s: LudoState, shownDie: Int, engine: LudoEngine, myC: Color, theirC: Color) {
    val measurer = rememberTextMeasurer()
    val pulse by rememberInfiniteTransition(label = "ludo").animateFloat(
        0.55f, 1f, infiniteRepeatable(tween(650), RepeatMode.Reverse), label = "pulse",
    )
    // my pieces are at absolute colour (host red / guest blue); the board is drawn the same way round for both players
    val me = if (s.iAmHost) 1 else 2
    val they = 3 - me
    Canvas(
        Modifier.fillMaxSize().pointerInput(s.legal, s.canRoll, s.die, s.mine) {
            detectTapGestures { tap ->
                val cell = size.width / 15f
                val cx = tap.x / cell
                val cy = tap.y / cell
                if (s.myTurn && s.canRoll && hypot(cx - 7.5f, cy - 7.5f) < 1.7f) {
                    engine.roll()
                    return@detectTapGestures
                }
                if (s.myTurn && s.legal.isNotEmpty()) {
                    var best = -1
                    var bestD = 1.6f
                    for (i in s.legal) {
                        val (px, py) = spot(me, i, s.mine[i])
                        val d = hypot(cx - px, cy - py)
                        if (d < bestD) { bestD = d; best = i }
                    }
                    if (best >= 0) engine.movePiece(best)
                }
            }
        },
    ) {
        val cell = size.width / 15f
        drawBoard(cell)
        // pieces: theirs first, then mine on top
        fun drawSet(who: Int, list: List<Int>, colour: Color, mineSet: Boolean) {
            val seen = HashMap<String, Int>()
            for (i in 0 until 4) {
                val (px, py) = spot(who, i, list[i])
                val key = "${px}_$py"
                val n = seen.getOrDefault(key, 0)
                seen[key] = n + 1
                val c = Offset(px * cell + n * cell * 0.16f, py * cell - n * cell * 0.16f)
                val legal = mineSet && i in s.legal
                if (legal) drawCircle(Color.White.copy(alpha = 0.55f * pulse), cell * (0.62f + 0.12f * pulse), c)
                drawCircle(Color.Black.copy(alpha = 0.35f), cell * 0.42f, c + Offset(0f, cell * 0.05f))
                drawCircle(Brush.radialGradient(listOf(colour.lighten(0.45f), colour), c - Offset(cell * 0.12f, cell * 0.14f), cell * 0.5f), cell * 0.4f, c)
                drawCircle(Color.White.copy(alpha = if (s.lastPiece == i && s.eventByMe == mineSet) 1f else 0.6f), cell * 0.4f, c, style = Stroke(if (legal) 4f else 2f))
                if (list[i] == Ludo.HOME) drawCircle(Color.White, cell * 0.12f, c)
            }
        }
        drawSet(they, s.theirs, theirC, false)
        drawSet(me, s.mine, myC, true)
        // the dice dome in the middle
        val centre = Offset(cell * 7.5f, cell * 7.5f)
        val r = cell * 1.5f
        drawCircle(Color.Black.copy(alpha = 0.3f), r + 5f, centre + Offset(0f, 6f))
        drawCircle(Brush.radialGradient(listOf(Color(0xFFFFFFFF), Color(0xFFCDD6E8)), centre - Offset(r * 0.3f, r * 0.3f), r * 1.3f), r, centre)
        val glow = s.myTurn && s.canRoll
        drawCircle(if (glow) Funk.Lime.copy(alpha = pulse) else Color(0xFF8795B5), r, centre, style = Stroke(if (glow) 7f else 3f))
        if (shownDie > 0) drawDie(centre, r * 1.05f, shownDie) else {
            val t = measurer.measure(if (s.myTurn) "ROLL" else "…", TextStyle(fontSize = 18.sp, fontWeight = FontWeight.Black, color = Color(0xFF2A3350)))
            drawText(t, topLeft = centre - Offset(t.size.width / 2f, t.size.height / 2f))
        }
    }
}

private fun DrawScope.drawDie(c: Offset, r: Float, v: Int) {
    val h = r * 0.62f
    drawRoundRect(Color.White, c - Offset(h, h), Size(h * 2, h * 2), CornerRadius(h * 0.3f))
    drawRoundRect(Color(0xFF445070), c - Offset(h, h), Size(h * 2, h * 2), CornerRadius(h * 0.3f), style = Stroke(3f))
    val d = h * 0.5f
    val pips = when (v) {
        1 -> listOf(0 to 0)
        2 -> listOf(-1 to -1, 1 to 1)
        3 -> listOf(-1 to -1, 0 to 0, 1 to 1)
        4 -> listOf(-1 to -1, 1 to -1, -1 to 1, 1 to 1)
        5 -> listOf(-1 to -1, 1 to -1, 0 to 0, -1 to 1, 1 to 1)
        else -> listOf(-1 to -1, 1 to -1, -1 to 0, 1 to 0, -1 to 1, 1 to 1)
    }
    for ((px, py) in pips) drawCircle(if (v == 1) Color(0xFFE53935) else Color(0xFF1B2440), h * 0.15f, c + Offset(px * d, py * d))
}

private fun DrawScope.drawBoard(cell: Float) {
    drawRoundRect(Color(0xFFF7F1E3), Offset.Zero, size, CornerRadius(cell * 0.4f))
    fun rect(x: Int, y: Int, w: Int, h: Int, colour: Color) = drawRect(colour, Offset(x * cell, y * cell), Size(w * cell, h * cell))
    // bases (red top-left and blue bottom-right are the players; the other two corners are just decoration)
    rect(0, 0, 6, 6, RedC.copy(alpha = 0.9f)); rect(9, 9, 6, 6, BlueC.copy(alpha = 0.9f))
    rect(9, 0, 6, 6, GreenC.copy(alpha = 0.35f)); rect(0, 9, 6, 6, YellowC.copy(alpha = 0.35f))
    for ((bx, by, col) in listOf(Triple(1, 1, RedC), Triple(10, 10, BlueC), Triple(10, 1, GreenC), Triple(1, 10, YellowC))) {
        drawRoundRect(Color(0xFFFFFBF0), Offset(bx * cell, by * cell), Size(4 * cell, 4 * cell), CornerRadius(cell * 0.5f))
        for (k in 0 until 4) {
            val c = Offset((bx + 0.5f + (k % 2) * 2f + 0.5f) * cell, (by + 0.5f + (k / 2) * 2f + 0.5f) * cell)
            drawCircle(col.copy(alpha = 0.22f), cell * 0.5f, c)
        }
    }
    // the ring
    for ((i, xy) in Ludo.RING_XY.withIndex()) {
        val x = xy.first; val y = xy.second
        val colour = when (i) { 0 -> RedC.copy(alpha = 0.85f); 26 -> BlueC.copy(alpha = 0.85f); else -> Color.White }
        rect(x, y, 1, 1, colour)
        drawRect(Color(0xFF9AA3B8), Offset(x * cell, y * cell), Size(cell, cell), style = Stroke(1.5f))
        if (i in Ludo.SAFE && i != 0 && i != 26) drawStar(Offset((x + 0.5f) * cell, (y + 0.5f) * cell), cell * 0.32f, Color(0xFFFFB300))
        if (i == 0 || i == 26) drawStar(Offset((x + 0.5f) * cell, (y + 0.5f) * cell), cell * 0.3f, Color.White)
    }
    // home columns
    for (k in 1..5) {
        rect(k, 7, 1, 1, RedC.copy(alpha = 0.55f)); rect(14 - k, 7, 1, 1, BlueC.copy(alpha = 0.55f))
        rect(7, k, 1, 1, GreenC.copy(alpha = 0.3f)); rect(7, 14 - k, 1, 1, YellowC.copy(alpha = 0.3f))
        for ((x, y) in listOf(k to 7, 14 - k to 7, 7 to k, 7 to 14 - k)) drawRect(Color(0xFF9AA3B8), Offset(x * cell, y * cell), Size(cell, cell), style = Stroke(1.5f))
    }
    // the middle: four triangles
    fun tri(a: Offset, b: Offset, c: Offset, col: Color) {
        val p = Path().apply { moveTo(a.x, a.y); lineTo(b.x, b.y); lineTo(c.x, c.y); close() }
        drawPath(p, col)
    }
    val tl = Offset(6 * cell, 6 * cell); val tr = Offset(9 * cell, 6 * cell); val bl = Offset(6 * cell, 9 * cell); val br = Offset(9 * cell, 9 * cell)
    val mid = Offset(7.5f * cell, 7.5f * cell)
    tri(tl, bl, mid, RedC); tri(tr, br, mid, BlueC); tri(tl, tr, mid, GreenC.copy(alpha = 0.6f)); tri(bl, br, mid, YellowC.copy(alpha = 0.6f))
}

private fun DrawScope.drawStar(c: Offset, r: Float, colour: Color) {
    val p = Path()
    for (k in 0 until 10) {
        val rad = if (k % 2 == 0) r else r * 0.45f
        val a = -PI / 2 + k * PI / 5
        val pt = Offset(c.x + (rad * cos(a)).toFloat(), c.y + (rad * sin(a)).toFloat())
        if (k == 0) p.moveTo(pt.x, pt.y) else p.lineTo(pt.x, pt.y)
    }
    p.close()
    drawPath(p, colour)
}
