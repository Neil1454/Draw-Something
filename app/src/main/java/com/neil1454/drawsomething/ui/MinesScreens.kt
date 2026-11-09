@file:OptIn(ExperimentalMaterial3Api::class)

package com.neil1454.drawsomething.ui

import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.AnimationVector1D
import androidx.compose.animation.core.LinearEasing
import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.animateIntAsState
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.tween
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
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
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableLongStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.runtime.mutableStateListOf
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.neil1454.drawsomething.core.MineItem
import com.neil1454.drawsomething.core.MineNote
import com.neil1454.drawsomething.core.MineRules
import com.neil1454.drawsomething.core.MinesEngine
import com.neil1454.drawsomething.core.MinesPhase
import com.neil1454.drawsomething.core.MinesState
import kotlinx.coroutines.launch

typealias ActMines = (MinesEngine.() -> Unit) -> Unit

private val CandyA = listOf(Color(0xFF8A6BFF), Color(0xFF5A3BDB))
private val CandyB = listOf(Color(0xFFA77CFF), Color(0xFF6B47EA))
private val NumberColours = listOf(
    Color.Transparent, Funk.Cyan, Funk.Lime, Funk.Pink, Funk.Sun,
    Funk.Orange, Funk.Mint, Funk.Violet, Color.White,
)

@Composable
fun MinesHelp() {
    Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
        Text("Take turns revealing squares. A number says how many bombs touch that square.")
        Text("• Safe square: +${MineRules.SAFE_POINTS} each (an empty square opens its neighbours too).")
        Text("• Bomb: −${MineRules.MINE_PENALTY} and your turn ends. The game goes on.")
        Text("• The game ends when every safe square is open. Highest score wins.")
        Text("Hidden power-ups, triggered when you reveal them:", fontWeight = FontWeight.Bold)
        MineItem.values().forEach { Text("${it.emoji} ${it.label}: ${it.blurb}") }
    }
}

/** A running animation drawn on top of the grid (an explosion, sparkle, ...). */
private class CellFx(val kind: Int, val cell: Int, val anim: Animatable<Float, AnimationVector1D>)

private const val FX_BOOM = 0
private const val FX_GOOD = 1
private const val FX_OPEN = 2

@Composable
fun MinesScreen(
    s: MinesState,
    act: ActMines,
    onPlayAgain: () -> Unit,
    onBackToGames: () -> Unit,
    onLeave: () -> Unit,
) {
    var help by rememberSaveable { mutableStateOf(false) }
    var details by rememberSaveable { mutableStateOf(false) }
    val over = s.phase == MinesPhase.OVER
    val w = s.size.w
    val h = s.size.h

    val fx = remember { mutableStateListOf<CellFx>() }
    val popups = remember { mutableStateListOf<Popup>() }
    val log = remember { mutableStateListOf<String>() }
    var boom by remember { mutableIntStateOf(0) }
    var win by remember { mutableIntStateOf(0) }
    var popupId by remember { mutableLongStateOf(0L) }
    val scope = rememberCoroutineScope()
    val shake = rememberBurst(boom, 650)
    val redFlash = rememberBurst(boom, 700)

    fun spawn(kind: Int, cell: Int, millis: Int) {
        if (cell < 0) return
        val a = Animatable(0f)
        val f = CellFx(kind, cell, a)
        fx.add(f)
        scope.launch {
            a.animateTo(1f, tween(millis, easing = LinearEasing))
            fx.remove(f)
        }
    }

    fun pop(text: String, color: Color, cell: Int) {
        val i = if (cell >= 0) cell else (w * h) / 2
        popups.add(Popup(popupId++, text, color, ((i % w) + 0.5f) / w, ((i / w) + 0.5f) / h))
    }

    // Every move: explosions, sparkles and flashing scores for whatever just happened.
    LaunchedEffect(s.moves) {
        if (s.moves == 0) {
            fx.clear(); popups.clear(); log.clear()
            return@LaunchedEffect
        }
        s.notes.forEach { n ->
            log.add(0, noteText(n, s.theirName))
            val mine = if (n.byMe) Funk.Lime else Funk.Cyan
            when {
                n.kind == "boom" -> {
                    spawn(FX_BOOM, n.cell, 1000)
                    boom++
                    pop(if (n.value > 0) "💥 −${n.value}" else "💥 BOOM!", Funk.Red, n.cell)
                }
                n.kind == "block" -> {
                    spawn(FX_GOOD, n.cell, 900)
                    pop("🛡️ BLOCKED +${n.value}", Funk.Mint, n.cell)
                }
                n.kind == "safe" || n.kind == "safe2" -> {
                    spawn(FX_OPEN, n.cell, 700)
                    pop((if (n.kind == "safe2") "✨ ×2  " else "") + "+${n.value}", mine, n.cell)
                }
                n.kind == "item_B" -> { spawn(FX_GOOD, n.cell, 900); pop("🎁 BONUS +${n.value}", Funk.Sun, n.cell) }
                n.kind == "item_S" -> { spawn(FX_GOOD, n.cell, 900); pop("🛡️ SHIELD!", Funk.Mint, n.cell) }
                n.kind == "item_D" -> { spawn(FX_GOOD, n.cell, 900); pop("✨ DOUBLE NEXT!", Funk.Sun, n.cell) }
                n.kind == "item_E" -> { spawn(FX_GOOD, n.cell, 900); pop("⏩ EXTRA TURN!", Funk.Pink, n.cell) }
                n.kind == "item_T" -> { spawn(FX_GOOD, n.cell, 900); pop("🧲 STOLE ${n.value}", Funk.Violet, n.cell) }
                n.kind == "item_R" -> { spawn(FX_GOOD, n.cell, 900); pop("📡 RADAR!", Funk.Cyan, n.cell) }
            }
        }
        while (log.size > 40) log.removeAt(log.lastIndex)
    }
    LaunchedEffect(over) {
        if (over && s.myScore > s.theirScore) win++
    }

    val lastMineColour = if (s.notes.firstOrNull()?.byMe == true) Funk.Lime else Funk.Cyan

    BoxWithConstraints(Modifier.fillMaxSize().shaking(shake)) {
        val landscape = maxWidth > maxHeight
        if (redFlash < 1f) Box(Modifier.fillMaxSize().background(Funk.Red.copy(alpha = 0.38f * (1f - redFlash))))

        // ---- the map: as big as the screen allows
        BoxWithConstraints(
            if (landscape) Modifier.fillMaxSize().padding(start = 190.dp, end = 190.dp, top = 4.dp, bottom = 4.dp)
            else Modifier.fillMaxSize().padding(start = 4.dp, end = 4.dp, top = 104.dp, bottom = 52.dp),
            contentAlignment = Alignment.Center,
        ) {
            val side = minOf(maxWidth, maxHeight)
            Box(Modifier.size(side)) {
                SquareGrid(w, h, Modifier.fillMaxSize()) { x, y, cellDp ->
                    val i = y * w + x
                    MineCell(
                        ch = s.view.getOrElse(i) { '.' },
                        item = s.items.getOrElse(i) { '.' },
                        fresh = i in s.fresh,
                        moves = s.moves,
                        outline = lastMineColour,
                        checker = (x + y) % 2 == 0,
                        canTap = s.myTurn && !over,
                        sp = cellDp * 0.55f,
                        onTap = { act { pick(x, y) } },
                    )
                }
                Canvas(Modifier.matchParentSize()) {
                    val cw = size.width / w
                    val ch = size.height / h
                    fx.forEach { f ->
                        val p = f.anim.value
                        val c = Offset((f.cell % w + 0.5f) * cw, (f.cell / w + 0.5f) * ch)
                        when (f.kind) {
                            FX_BOOM -> drawExplosion(c, cw * 1.6f, p)
                            FX_GOOD -> drawSparkle(c, cw * 1.3f, p, listOf(Funk.Sun, Funk.Pink, Funk.Cyan, Funk.Lime))
                            else -> drawSparkle(c, cw * 0.9f, p, listOf(Funk.Lime, Funk.Cyan))
                        }
                    }
                }
                PopupLayer(popups, { id -> popups.removeAll { it.id == id } })
            }
        }

        val pulse = rememberInfiniteTransition(label = "turn")
        val glow by pulse.animateFloat(0.55f, 1f, infiniteRepeatable(tween(800), RepeatMode.Reverse), label = "glow")
        val turnText = when {
            over -> "GAME OVER"
            s.myTurn -> "👉  YOUR TURN  👈"
            else -> "⏳ ${s.theirName}'s turn"
        }
        val turnColour = (if (s.myTurn && !over) Funk.Lime else Funk.Cyan).copy(alpha = if (s.myTurn && !over) glow else 0.9f)

        if (!landscape) {
            // ---- top HUD: scores and the details icon
            Column(
                Modifier.align(Alignment.TopCenter).fillMaxWidth().padding(horizontal = 8.dp, vertical = 8.dp),
                verticalArrangement = Arrangement.spacedBy(6.dp),
                horizontalAlignment = Alignment.CenterHorizontally,
            ) {
                Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    HudChip("You", s.myScore, s.myShield, s.myDouble, !over && s.myTurn, Funk.Lime, Modifier.weight(1f))
                    RoundIconButton("📊", { details = true })
                    HudChip(s.theirName, s.theirScore, s.theirShield, s.theirDouble, !over && !s.myTurn, Funk.Cyan, Modifier.weight(1f))
                }
                Text(turnText, fontSize = 18.sp, fontWeight = FontWeight.Black, color = turnColour)
            }
            Row(
                Modifier.align(Alignment.BottomCenter).fillMaxWidth().padding(horizontal = 12.dp, vertical = 8.dp),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.SpaceBetween,
            ) {
                Text("💣 ${s.minesLeft}   ⬜ ${s.safeLeft}", fontWeight = FontWeight.Bold, color = Color.White)
                RoundIconButton("❓", { help = true })
            }
        } else {
            // ---- landscape: scores down the left and right edges so the map keeps the full height
            Column(
                Modifier.align(Alignment.CenterStart).padding(8.dp).width(176.dp),
                verticalArrangement = Arrangement.spacedBy(8.dp),
                horizontalAlignment = Alignment.CenterHorizontally,
            ) {
                HudChip("You", s.myScore, s.myShield, s.myDouble, !over && s.myTurn, Funk.Lime, Modifier.fillMaxWidth())
                Text(turnText, fontSize = 15.sp, fontWeight = FontWeight.Black, color = turnColour, textAlign = TextAlign.Center)
                RoundIconButton("📊", { details = true })
            }
            Column(
                Modifier.align(Alignment.CenterEnd).padding(8.dp).width(176.dp),
                verticalArrangement = Arrangement.spacedBy(8.dp),
                horizontalAlignment = Alignment.CenterHorizontally,
            ) {
                HudChip(s.theirName, s.theirScore, s.theirShield, s.theirDouble, !over && !s.myTurn, Funk.Cyan, Modifier.fillMaxWidth())
                Text("💣 ${s.minesLeft}   ⬜ ${s.safeLeft}", fontWeight = FontWeight.Bold, color = Color.White)
                RoundIconButton("❓", { help = true })
            }
        }

        if (over) MinesOverlay(s, win, onPlayAgain, onBackToGames, onLeave)
    }

    if (details) MinesDetails(s, log) { details = false }
    if (help) {
        AlertDialog(
            onDismissRequest = { help = false },
            confirmButton = { TextButton(onClick = { help = false }) { Text("Got it") } },
            title = { Text("💣 Bomb Find") },
            text = { Column(Modifier.verticalScroll(rememberScrollState())) { MinesHelp() } },
        )
    }
}

@Composable
private fun HudChip(name: String, score: Int, shield: Boolean, dbl: Boolean, active: Boolean, accent: Color, modifier: Modifier) {
    val shown by animateIntAsState(score, tween(600), label = "score")
    val shape = RoundedCornerShape(18.dp)
    Row(
        modifier
            .clip(shape)
            .background(Brush.horizontalGradient(listOf(accent.copy(alpha = if (active) 0.55f else 0.22f), Color(0x66000000))))
            .border(if (active) 3.dp else 1.dp, accent.copy(alpha = if (active) 1f else 0.4f), shape)
            .padding(horizontal = 12.dp, vertical = 6.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(8.dp),
    ) {
        Column(Modifier.weight(1f)) {
            Text(name, fontSize = 12.sp, maxLines = 1, color = Color.White.copy(alpha = 0.9f))
            Text("$shown", fontSize = 24.sp, fontWeight = FontWeight.Black, color = Color.White)
        }
        if (shield) Text("🛡️", fontSize = 18.sp)
        if (dbl) Text("✨", fontSize = 18.sp)
    }
}

private fun noteText(n: MineNote, theirName: String): String {
    val who = if (n.byMe) "You" else theirName
    val squares = if (n.count == 1) "1 square" else "${n.count} squares"
    return when {
        n.kind == "safe" -> "$who opened $squares (+${n.value})"
        n.kind == "safe2" -> "✨ $who opened $squares, doubled! (+${n.value})"
        n.kind == "boom" -> if (n.value > 0) "💥 $who hit a bomb! −${n.value}" else "💥 $who hit a bomb!"
        n.kind == "block" -> "🛡️ $who's shield blocked a bomb! +${n.value}"
        n.kind == "item_B" -> "🎁 $who found a bonus! +${n.value}"
        n.kind == "item_S" -> "🛡️ $who found a shield"
        n.kind == "item_D" -> "✨ $who found double points for the next reveal"
        n.kind == "item_E" -> "⏩ $who gets an extra turn"
        n.kind == "item_T" -> "🧲 $who stole ${n.value} points"
        n.kind == "item_R" -> "📡 Radar flagged a hidden bomb 🚩"
        else -> ""
    }
}

/** The scoring detail panel opened by the 📊 icon. */
@Composable
private fun MinesDetails(s: MinesState, log: List<String>, onClose: () -> Unit) {
    AlertDialog(
        onDismissRequest = onClose,
        confirmButton = { TextButton(onClick = onClose) { Text("Close") } },
        title = { Text("📊 Scores") },
        text = {
            Column(Modifier.verticalScroll(rememberScrollState()), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                DetailRow("You", s.myScore, s.myShield, s.myDouble, Funk.Lime)
                DetailRow(s.theirName, s.theirScore, s.theirShield, s.theirDouble, Funk.Cyan)
                Text("💣 ${s.minesLeft} bombs left · ⬜ ${s.safeLeft} safe squares left · move ${s.moves}")
                Text("Points: safe square +${MineRules.SAFE_POINTS} · bomb −${MineRules.MINE_PENALTY} · bonus +${MineRules.BONUS_POINTS} · steal ${MineRules.STEAL_POINTS}", style = MaterialTheme.typography.bodySmall)
                Text("Recent moves", fontWeight = FontWeight.Bold)
                if (log.isEmpty()) Text("Nothing yet. Tap a square!", style = MaterialTheme.typography.bodySmall)
                log.take(25).forEach { Text(it, style = MaterialTheme.typography.bodySmall) }
            }
        },
    )
}

@Composable
private fun DetailRow(name: String, score: Int, shield: Boolean, dbl: Boolean, accent: Color) {
    Row(
        Modifier.fillMaxWidth().clip(RoundedCornerShape(14.dp)).background(accent.copy(alpha = 0.18f)).padding(12.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(10.dp),
    ) {
        Text(name, Modifier.weight(1f), fontWeight = FontWeight.Bold, maxLines = 1)
        if (shield) Text("🛡️ shield")
        if (dbl) Text("✨ double")
        Text("$score", fontSize = 26.sp, fontWeight = FontWeight.Black, color = accent)
    }
}

@Composable
private fun MineCell(
    ch: Char,
    item: Char,
    fresh: Boolean,
    moves: Int,
    outline: Color,
    checker: Boolean,
    canTap: Boolean,
    sp: Float,
    onTap: () -> Unit,
) {
    val glow = remember { Animatable(0f) }
    LaunchedEffect(moves, fresh) {
        if (fresh && moves > 0) {
            glow.snapTo(1f)
            glow.animateTo(0f, tween(1100, easing = LinearEasing))
        } else {
            glow.snapTo(0f)
        }
    }
    val exploded = ch == 'X'
    val pulse = rememberInfiniteTransition(label = "cellpulse")
    val throb by pulse.animateFloat(0.5f, 1f, infiniteRepeatable(tween(650), RepeatMode.Reverse), label = "throb")

    val shape = RoundedCornerShape(5.dp)
    val bg: Brush = when (ch) {
        '.' -> Brush.verticalGradient(if (checker) CandyA else CandyB)
        'X' -> Brush.verticalGradient(listOf(Funk.Orange, Funk.Red))
        'x' -> Brush.verticalGradient(listOf(Funk.Mint, Color(0xFF16A37A)))
        'M' -> Brush.verticalGradient(listOf(Funk.Pink, Color(0xFFB0206B)))
        'F' -> Brush.verticalGradient(listOf(Funk.Sun, Funk.Orange))
        '0' -> Brush.verticalGradient(listOf(Color(0xFF1A1245), Color(0xFF140D38)))
        else -> Brush.verticalGradient(listOf(Color(0xFF2A1F6B), Color(0xFF1D1450)))
    }
    val numberColour = if (ch in '1'..'8') NumberColours[ch - '0'] else Color.Transparent
    val ring = when {
        glow.value > 0f -> Color.White.copy(alpha = glow.value)
        fresh -> outline.copy(alpha = 0.9f)
        exploded -> Funk.Sun.copy(alpha = throb)
        ch in '1'..'8' -> numberColour.copy(alpha = 0.35f)
        else -> Color.White.copy(alpha = 0.18f)
    }
    Box(
        Modifier
            .fillMaxSize()
            .padding(1.5.dp)
            .clip(shape)
            .background(bg)
            .border(if (glow.value > 0f || fresh || exploded) 2.5.dp else 1.dp, ring, shape)
            .clickable(enabled = ch == '.' && canTap, onClick = onTap),
        contentAlignment = Alignment.Center,
    ) {
        if (glow.value > 0f) Box(Modifier.fillMaxSize().background(Color.White.copy(alpha = 0.55f * glow.value)))
        val itemEmoji = MineItem.of(item)?.emoji
        when (ch) {
            'X' -> Text("💥", fontSize = (sp * (0.9f + 0.2f * throb)).sp)
            'x' -> Text("🛡️", fontSize = sp.sp)
            'F' -> Text("🚩", fontSize = sp.sp)
            'M' -> Text("💣", fontSize = sp.sp)
            in '1'..'8' -> {
                Text("$ch", fontSize = sp.sp, fontWeight = FontWeight.Black, color = numberColour)
                if (itemEmoji != null) Text(itemEmoji, fontSize = (sp * 0.62f).sp, modifier = Modifier.align(Alignment.TopEnd))
            }
            '0' -> if (itemEmoji != null) Text(itemEmoji, fontSize = sp.sp)
            else -> Unit
        }
    }
}

/** Shown over the map when the game ends. */
@Composable
private fun MinesOverlay(s: MinesState, win: Int, onPlayAgain: () -> Unit, onBackToGames: () -> Unit, onLeave: () -> Unit) {
    val title = when {
        s.myScore > s.theirScore -> "🏆 YOU WIN!"
        s.myScore < s.theirScore -> "🏆 ${s.theirName.uppercase()} WINS!"
        else -> "🤝 IT'S A DRAW!"
    }
    Box(Modifier.fillMaxSize().background(Color(0xCC0B0624)), contentAlignment = Alignment.Center) {
        ConfettiBurst(win)
        Column(
            Modifier.padding(24.dp),
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.spacedBy(12.dp),
        ) {
            GradientText(title, fontSize = 36.sp)
            Text("You ${s.myScore}   ·   ${s.theirName} ${s.theirScore}", fontSize = 20.sp, fontWeight = FontWeight.Bold)
            if (s.isHost) {
                FunkyButton("🔁  Play again (${s.size.label})", onPlayAgain, Modifier.fillMaxWidth(), listOf(Funk.Orange, Funk.Red))
            } else {
                Text("${s.theirName} can start a rematch…", color = MaterialTheme.colorScheme.onSurfaceVariant, textAlign = TextAlign.Center)
            }
            FunkyButton("🎮  Choose another game", onBackToGames, Modifier.fillMaxWidth(), listOf(Funk.Violet, Funk.Pink))
            TextButton(onClick = onLeave) { Text("Leave") }
        }
    }
}
