@file:OptIn(ExperimentalMaterial3Api::class)

package com.neil1454.drawsomething.ui

import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.AnimationVector1D
import androidx.compose.animation.core.LinearEasing
import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.animateFloat
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
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableLongStateOf
import androidx.compose.runtime.mutableStateListOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.drawscope.DrawScope
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.neil1454.drawsomething.core.SeaEngine
import com.neil1454.drawsomething.core.SeaPhase
import com.neil1454.drawsomething.core.SeaState
import com.neil1454.drawsomething.core.SeaWeapon
import kotlin.math.PI
import kotlin.math.abs
import kotlin.math.cos
import kotlin.math.sin
import kotlinx.coroutines.launch

typealias ActSea = (SeaEngine.() -> Unit) -> Unit

private val WaterA = listOf(Color(0xFF1E88E5), Color(0xFF1565C0))
private val WaterB = listOf(Color(0xFF2196F3), Color(0xFF1976D2))
private val FogA = listOf(Color(0xFF123E8F), Color(0xFF0B2A6B))
private val FogB = listOf(Color(0xFF16489F), Color(0xFF0E3279))
private val Steel = listOf(Color(0xFFB0BEC5), Color(0xFF546E7A))
private val Radar = Color(0xFFFFC400)

@Composable
fun SeaHelp() {
    Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
        Text("Each of you has a hidden fleet. Take turns firing at the other's grid.")
        Text("• 🎯 Shot: one square. A HIT means you fire again!")
        Text("• 📡 Radar (once): pick a spot and it counts the ship squares in the 3×3 area around it. It uses your turn.")
        Text("• 💥 Cluster bomb (once): hits a plus-shaped area of 5 squares. It uses your turn.")
        Text("Sink every enemy ship to win. Your fleet is placed for you; shuffle until you like it.")
    }
}

// ------------------------------------------------------------------ effects

private class SeaFxAnim(val kind: Int, val mine: Boolean, val cell: Int, val anim: Animatable<Float, AnimationVector1D>)

private const val FX_SPLASH = 0
private const val FX_HIT = 1
private const val FX_SUNK = 2
private const val FX_RADAR = 3

private fun DrawScope.drawRadarSweep(center: Offset, maxR: Float, p: Float) {
    if (p <= 0f || p >= 1f) return
    val fade = 1f - p
    for (k in 0..2) {
        val q = (p * 1.2f - k * 0.18f).coerceIn(0f, 1f)
        drawCircle(Funk.Cyan.copy(alpha = fade * (0.8f - k * 0.2f)), radius = maxR * q * 1.6f, center = center, style = Stroke(width = maxR * 0.07f + 1f))
    }
    val ang = p * 2f * PI.toFloat() * 1.5f
    drawLine(
        Funk.Lime.copy(alpha = fade), center,
        Offset(center.x + cos(ang) * maxR * 1.5f, center.y + sin(ang) * maxR * 1.5f),
        strokeWidth = maxR * 0.1f + 1f,
    )
}

// ------------------------------------------------------------------ one square

@Composable
private fun SeaCell(ch: Char, mine: Boolean, checker: Boolean, ring: Boolean, canTap: Boolean, sp: Float, onTap: () -> Unit) {
    val pulse = rememberInfiniteTransition(label = "seapulse")
    val throb by pulse.animateFloat(0.4f, 1f, infiniteRepeatable(tween(700), RepeatMode.Reverse), label = "throb")
    val shape = RoundedCornerShape(4.dp)
    val bg: Brush = when (ch) {
        'S' -> Brush.verticalGradient(Steel)
        'x' -> Brush.verticalGradient(listOf(Funk.Orange, Funk.Red))
        '#' -> Brush.verticalGradient(listOf(Color(0xFF8E1B1B), Color(0xFF3B0A0A)))
        'o' -> Brush.verticalGradient(listOf(Color(0xFF64B5F6), Color(0xFF42A5F5)))
        else -> Brush.verticalGradient(
            if (mine) (if (checker) WaterA else WaterB) else (if (checker) FogA else FogB),
        )
    }
    val border = when {
        ring -> Radar
        ch == 'x' -> Funk.Sun.copy(alpha = throb)
        ch == '#' -> Funk.Red.copy(alpha = throb)
        ch == 'S' -> Color.White.copy(alpha = 0.5f)
        else -> Color.White.copy(alpha = 0.12f)
    }
    Box(
        Modifier
            .fillMaxSize()
            .padding(1.dp)
            .clip(shape)
            .background(bg)
            .border(if (ring || ch == 'x' || ch == '#') 2.dp else 1.dp, border, shape)
            .clickable(enabled = canTap && ch == '.', onClick = onTap),
        contentAlignment = Alignment.Center,
    ) {
        when (ch) {
            'x' -> Text("🔥", fontSize = (sp * 0.95f).sp)
            '#' -> Text("☠️", fontSize = (sp * 0.95f).sp)
            'o' -> Text("◦", fontSize = (sp * 1.2f).sp, color = Color.White, fontWeight = FontWeight.Black)
            '.' -> if (!mine) Text("≈", fontSize = (sp * 0.8f).sp, color = Color.White.copy(alpha = 0.16f))
            else -> Unit
        }
    }
}

/** One ocean board: the squares, the animations drawn over them, and score popups. */
@Composable
private fun SeaBoard(
    s: SeaState,
    grid: String,
    mine: Boolean,
    fx: List<SeaFxAnim>,
    popups: List<Popup>,
    onPopupDone: (Long) -> Unit,
    canTap: Boolean,
    modifier: Modifier,
    onTap: (Int, Int) -> Unit = { _, _ -> },
) {
    val w = s.size.w
    val h = s.size.h
    BoxWithConstraints(modifier) {
        val side = minOf(maxWidth, maxHeight)
        Box(Modifier.size(side).align(Alignment.Center)) {
            SquareGrid(w, h, Modifier.fillMaxSize()) { x, y, cellDp ->
                SeaCell(
                    ch = grid.getOrElse(y * w + x) { '.' },
                    mine = mine,
                    checker = (x + y) % 2 == 0,
                    ring = !mine && s.radarCount >= 0 && abs(x - s.radarX) <= 1 && abs(y - s.radarY) <= 1,
                    canTap = canTap,
                    sp = cellDp * 0.55f,
                    onTap = { onTap(x, y) },
                )
            }
            Canvas(Modifier.matchParentSize()) {
                val cw = size.width / w
                val ch = size.height / h
                fx.forEach { f ->
                    if (f.mine != mine) return@forEach
                    val p = f.anim.value
                    val c = Offset((f.cell % w + 0.5f) * cw, (f.cell / w + 0.5f) * ch)
                    when (f.kind) {
                        FX_SPLASH -> drawSplash(c, cw * 1.3f, p)
                        FX_HIT -> drawExplosion(c, cw * 1.4f, p)
                        FX_SUNK -> drawExplosion(c, cw * 2.2f, p)
                        else -> drawRadarSweep(c, cw * 1.4f, p)
                    }
                }
            }
            PopupLayer(popups, onPopupDone)
        }
    }
}

// ------------------------------------------------------------------ placing the fleet

@Composable
fun SeaPlacingScreen(s: SeaState, act: ActSea, onLeave: () -> Unit) {
    Page {
        Column(
            Modifier.fillMaxWidth(),
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.spacedBy(12.dp),
        ) {
            GradientText("🚢 Your fleet", fontSize = 30.sp, colors = listOf(Funk.Cyan, Funk.Blue, Funk.Mint))
            Text(
                "Only you can see this. Shuffle until you like it, then tap Ready.",
                textAlign = TextAlign.Center, style = MaterialTheme.typography.bodyMedium,
            )
            SeaBoard(
                s, s.myGrid, mine = true, fx = emptyList(), popups = emptyList(), onPopupDone = {},
                canTap = false, modifier = Modifier.fillMaxWidth().aspectRatio(1f),
            )
            Row(horizontalArrangement = Arrangement.spacedBy(12.dp), modifier = Modifier.fillMaxWidth()) {
                FunkyButton("🎲 Shuffle", { act { shuffleFleet() } }, Modifier.weight(1f), listOf(Funk.Violet, Funk.Blue), enabled = !s.iAmReady)
                FunkyButton(if (s.iAmReady) "✓ Ready" else "Ready!", { act { ready() } }, Modifier.weight(1f), listOf(Funk.Mint, Funk.Cyan), enabled = !s.iAmReady)
            }
            if (s.iAmReady && !s.theyAreReady) {
                CircularProgressIndicator(Modifier.size(28.dp))
                Text("Waiting for ${s.theirName} to get ready…")
            } else if (!s.iAmReady && s.theyAreReady) {
                Text("${s.theirName} is ready and waiting for you!", fontWeight = FontWeight.Bold, color = Funk.Sun)
            }
            TextButton(onClick = onLeave) { Text("Leave") }
        }
    }
}

// ------------------------------------------------------------------ battle

@Composable
fun SeaBattleScreen(s: SeaState, act: ActSea) {
    var help by rememberSaveable { mutableStateOf(false) }
    var details by rememberSaveable { mutableStateOf(false) }
    val canFire = s.myTurn && !s.awaiting
    val fx = remember { mutableStateListOf<SeaFxAnim>() }
    val enemyPop = remember { mutableStateListOf<Popup>() }
    val minePop = remember { mutableStateListOf<Popup>() }
    val log = remember { mutableStateListOf<String>() }
    var hurt by remember { mutableIntStateOf(0) }
    var popupId by remember { mutableLongStateOf(0L) }
    val scope = rememberCoroutineScope()
    val shake = rememberBurst(hurt, 650)
    val flash = rememberBurst(hurt, 700)

    fun spawn(kind: Int, mine: Boolean, cell: Int, millis: Int) {
        val a = Animatable(0f)
        val f = SeaFxAnim(kind, mine, cell, a)
        fx.add(f)
        scope.launch {
            a.animateTo(1f, tween(millis, easing = LinearEasing))
            fx.remove(f)
        }
    }

    LaunchedEffect(s.moves) {
        if (s.moves == 0) {
            fx.clear(); enemyPop.clear(); minePop.clear(); log.clear()
            return@LaunchedEffect
        }
        s.note?.let { log.add(0, it) }
        while (log.size > 40) log.removeAt(log.lastIndex)
        val w = s.size.w
        for (onMine in listOf(false, true)) {
            val list = s.fx.filter { it.mine == onMine }
            if (list.isEmpty()) continue
            list.forEach { e ->
                when {
                    e.radar -> spawn(FX_RADAR, e.mine, e.cell, 1300)
                    e.sunk -> spawn(FX_SUNK, e.mine, e.cell, 1100)
                    e.hit -> spawn(FX_HIT, e.mine, e.cell, 900)
                    else -> spawn(FX_SPLASH, e.mine, e.cell, 900)
                }
            }
            if (onMine && list.any { it.hit }) hurt++
            val first = list.first()
            val text: String
            val colour: Color
            when {
                first.radar -> { text = "📡 SCANNED"; colour = Funk.Cyan }
                list.any { it.sunk } -> { text = if (onMine) "☠️ SHIP LOST!" else "☠️ SUNK!"; colour = Funk.Red }
                list.any { it.hit } -> { text = if (onMine) "😱 HIT!" else "💥 HIT!"; colour = Funk.Orange }
                else -> { text = "💦 MISS"; colour = Funk.Cyan }
            }
            val target = if (onMine) minePop else enemyPop
            target.add(Popup(popupId++, text, colour, ((first.cell % w) + 0.5f) / w, ((first.cell / w) + 0.5f) / w))
        }
    }

    val enemyBoard: @Composable (Modifier) -> Unit = { m ->
        SeaBoard(
            s, s.enemyGrid, mine = false, fx = fx, popups = enemyPop, onPopupDone = { id -> enemyPop.removeAll { it.id == id } },
            canTap = canFire, modifier = m, onTap = { x, y -> act { fire(x, y) } },
        )
    }
    val myBoard: @Composable (Modifier) -> Unit = { m ->
        SeaBoard(
            s, s.myGrid, mine = true, fx = fx, popups = minePop, onPopupDone = { id -> minePop.removeAll { it.id == id } },
            canTap = false, modifier = m,
        )
    }

    BoxWithConstraints(Modifier.fillMaxSize().shaking(shake)) {
        val landscape = maxWidth > maxHeight
        if (flash < 1f) Box(Modifier.fillMaxSize().background(Funk.Red.copy(alpha = 0.35f * (1f - flash))))

        if (!landscape) {
            Column(Modifier.fillMaxSize().padding(8.dp), verticalArrangement = Arrangement.spacedBy(6.dp)) {
                SeaHud(s, canFire, onDetails = { details = true }, onHelp = { help = true })
                enemyBoard(Modifier.weight(1f).fillMaxWidth())
                WeaponRow(s, canFire, act)
                Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(10.dp), verticalAlignment = Alignment.CenterVertically) {
                    myBoard(Modifier.size(132.dp))
                    Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(2.dp)) {
                        Text("YOUR FLEET", fontSize = 12.sp, fontWeight = FontWeight.Black, color = Funk.Cyan)
                        s.note?.let { Text(it, fontWeight = FontWeight.Bold) }
                        Text(s.weapon.emoji + " " + s.weapon.blurb, style = MaterialTheme.typography.bodySmall)
                    }
                }
            }
        } else {
            Row(Modifier.fillMaxSize().padding(8.dp), horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                enemyBoard(Modifier.weight(1f).fillMaxSize())
                Column(Modifier.width(240.dp).fillMaxSize().verticalScroll(rememberScrollState()), verticalArrangement = Arrangement.spacedBy(6.dp)) {
                    SeaHud(s, canFire, onDetails = { details = true }, onHelp = { help = true })
                    WeaponRow(s, canFire, act)
                    s.note?.let { Text(it, fontWeight = FontWeight.Bold) }
                    Text("YOUR FLEET", fontSize = 12.sp, fontWeight = FontWeight.Black, color = Funk.Cyan)
                    myBoard(Modifier.size(150.dp))
                }
            }
        }
    }
    if (details) SeaDetails(s, log) { details = false }
    if (help) {
        AlertDialog(
            onDismissRequest = { help = false },
            confirmButton = { TextButton(onClick = { help = false }) { Text("Got it") } },
            title = { Text("🚢 Sea Battle") },
            text = { Column(Modifier.verticalScroll(rememberScrollState())) { SeaHelp() } },
        )
    }
}

@Composable
private fun SeaHud(s: SeaState, canFire: Boolean, onDetails: () -> Unit, onHelp: () -> Unit) {
    val pulse = rememberInfiniteTransition(label = "seaturn")
    val glow by pulse.animateFloat(0.55f, 1f, infiniteRepeatable(tween(800), RepeatMode.Reverse), label = "glow")
    Column(verticalArrangement = Arrangement.spacedBy(4.dp), horizontalAlignment = Alignment.CenterHorizontally, modifier = Modifier.fillMaxWidth()) {
        Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            ShipsChip("Enemy", s.theirShipsLeft, Funk.Red, Modifier.weight(1f))
            RoundIconButton("📊", onDetails)
            RoundIconButton("❓", onHelp)
            ShipsChip("You", s.myShipsLeft, Funk.Mint, Modifier.weight(1f))
        }
        Text(
            when {
                s.awaiting -> "💨 Waiting for the result…"
                s.myTurn -> "👉  YOUR SHOT  👈"
                else -> "⏳ ${s.theirName} is aiming…"
            },
            fontSize = 17.sp,
            fontWeight = FontWeight.Black,
            color = (if (canFire) Funk.Lime else Funk.Cyan).copy(alpha = if (canFire) glow else 0.9f),
        )
        if (s.radarCount >= 0) {
            Text("📡 Last radar: ${s.radarCount} ship square${if (s.radarCount == 1) "" else "s"} in the gold area", fontSize = 12.sp, color = Funk.Sun)
        }
    }
}

@Composable
private fun ShipsChip(label: String, ships: Int, accent: Color, modifier: Modifier) {
    val shape = RoundedCornerShape(16.dp)
    Row(
        modifier.clip(shape).background(accent.copy(alpha = 0.22f)).border(1.5.dp, accent.copy(alpha = 0.7f), shape).padding(horizontal = 10.dp, vertical = 6.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(6.dp),
    ) {
        Text("🚢", fontSize = 16.sp)
        Column {
            Text(label, fontSize = 11.sp, color = Color.White.copy(alpha = 0.85f))
            Text("$ships left", fontWeight = FontWeight.Black, fontSize = 15.sp)
        }
    }
}

@Composable
private fun WeaponRow(s: SeaState, canFire: Boolean, act: ActSea) {
    Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(6.dp)) {
        SeaWeapon.values().forEach { w ->
            val available = when (w) {
                SeaWeapon.SHOT -> true
                SeaWeapon.RADAR -> s.radarLeft
                SeaWeapon.BOMB -> s.bombLeft
            }
            val selected = s.weapon == w
            FunkyButton(
                text = "${w.emoji} ${if (w == SeaWeapon.BOMB) "Bomb" else w.label}" + if (!available) " ✓" else "",
                onClick = { act { selectWeapon(w) } },
                modifier = Modifier.weight(1f),
                colors = if (selected) listOf(Funk.Lime, Funk.Mint) else listOf(Funk.Violet, Funk.Blue),
                enabled = available && canFire,
                fontSize = 13.sp,
            )
        }
    }
}

@Composable
private fun SeaDetails(s: SeaState, log: List<String>, onClose: () -> Unit) {
    val acc = if (s.shots > 0) (100 * s.hits / s.shots) else 0
    AlertDialog(
        onDismissRequest = onClose,
        confirmButton = { TextButton(onClick = onClose) { Text("Close") } },
        title = { Text("📊 Battle report") },
        text = {
            Column(Modifier.verticalScroll(rememberScrollState()), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                Text("Enemy ships left: ${s.theirShipsLeft} of ${s.size.fleet.size}", fontWeight = FontWeight.Bold)
                Text("Your ships left: ${s.myShipsLeft} of ${s.size.fleet.size}", fontWeight = FontWeight.Bold)
                Text("Your shots: ${s.shots} · hits: ${s.hits} · accuracy: $acc%")
                Text("📡 Radar: ${if (s.radarLeft) "ready" else "used"}   💥 Cluster bomb: ${if (s.bombLeft) "ready" else "used"}")
                Text("Recent action", fontWeight = FontWeight.Bold)
                if (log.isEmpty()) Text("No shots yet.", style = MaterialTheme.typography.bodySmall)
                log.take(25).forEach { Text(it, style = MaterialTheme.typography.bodySmall) }
            }
        },
    )
}

// ------------------------------------------------------------------ game over

@Composable
fun SeaOverScreen(s: SeaState, onPlayAgain: () -> Unit, onBackToGames: () -> Unit, onLeave: () -> Unit) {
    val won = s.iWon == true
    var win by remember { mutableIntStateOf(0) }
    LaunchedEffect(won) { if (won) win++ }
    Box(Modifier.fillMaxSize()) {
        ConfettiBurst(win)
        Page {
            Column(
                Modifier.fillMaxWidth(),
                horizontalAlignment = Alignment.CenterHorizontally,
                verticalArrangement = Arrangement.spacedBy(12.dp),
            ) {
                Spacer(Modifier.height(16.dp))
                GradientText(
                    if (won) "🏆 VICTORY!" else "💀 ${s.theirName.uppercase()} WINS",
                    fontSize = 36.sp,
                    colors = if (won) listOf(Funk.Sun, Funk.Lime, Funk.Cyan) else listOf(Funk.Red, Funk.Pink, Funk.Violet),
                )
                s.note?.let { Text(it, textAlign = TextAlign.Center) }
                Column(
                    Modifier.fillMaxWidth().clip(RoundedCornerShape(20.dp)).background(Color(0x33FFFFFF)).padding(16.dp),
                    verticalArrangement = Arrangement.spacedBy(4.dp),
                ) {
                    val acc = if (s.shots > 0) (100 * s.hits / s.shots) else 0
                    Text("Your shots: ${s.shots} · hits: ${s.hits} · accuracy: $acc%")
                    Text("Enemy ships you sank: ${s.size.fleet.size - s.theirShipsLeft} of ${s.size.fleet.size}")
                    Text("Your ships left: ${s.myShipsLeft} of ${s.size.fleet.size}")
                }
                if (s.isHost) {
                    FunkyButton("🔁  Play again (${s.size.label})", onPlayAgain, Modifier.fillMaxWidth(), listOf(Funk.Cyan, Funk.Blue))
                } else {
                    Text("${s.theirName} can start a rematch…", color = MaterialTheme.colorScheme.onSurfaceVariant)
                }
                FunkyButton("🎮  Choose another game", onBackToGames, Modifier.fillMaxWidth(), listOf(Funk.Violet, Funk.Pink))
                TextButton(onClick = onLeave) { Text("Leave") }
            }
        }
    }
}

/** Picks the right Sea Battle screen for the current phase. */
@Composable
fun SeaRoot(s: SeaState, act: ActSea, onPlayAgain: () -> Unit, onBackToGames: () -> Unit, onLeave: () -> Unit) {
    when (s.phase) {
        SeaPhase.PLACING -> SeaPlacingScreen(s, act, onLeave)
        SeaPhase.BATTLE -> SeaBattleScreen(s, act)
        SeaPhase.OVER -> SeaOverScreen(s, onPlayAgain, onBackToGames, onLeave)
        SeaPhase.LOBBY -> Unit
    }
}
