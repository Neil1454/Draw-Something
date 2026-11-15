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
import androidx.compose.foundation.gestures.detectDragGestures
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxHeight
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
import androidx.compose.runtime.MutableLongState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableLongStateOf
import androidx.compose.runtime.mutableStateListOf
import androidx.compose.runtime.mutableStateMapOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Rect
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.drawscope.DrawScope
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.layout.boundsInRoot
import androidx.compose.ui.layout.onGloballyPositioned
import androidx.compose.ui.layout.positionInRoot
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.drawText
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.rememberTextMeasurer
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.neil1454.drawsomething.core.SeaEngine
import com.neil1454.drawsomething.core.SeaPhase
import com.neil1454.drawsomething.core.SeaShip
import com.neil1454.drawsomething.core.SeaState
import com.neil1454.drawsomething.core.SeaWeapon
import com.neil1454.drawsomething.core.Sound
import com.neil1454.drawsomething.sound.Sfx
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import kotlin.math.PI
import kotlin.math.abs
import kotlin.math.cos
import kotlin.math.sin

typealias ActSea = (SeaEngine.() -> Unit) -> Unit

private val Radar = Color(0xFFFFC400)

@Composable
fun SeaHelp() {
    Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
        Text("Each of you has a hidden fleet and places it yourself: drag each ship onto your sea (or tap to put it down), turn it with the turn button, and tap Ready. The lobby has a switch for random fleets instead.")
        Text("Then take turns firing at the other's sea. Tap a square to aim, then press FIRE and watch the bomb drop in.")
        Text("• 🎯 Shot: one square. A HIT means you fire again!")
        Text("• 📡 Radar (once): a buoy drops and counts the ship squares in the 3×3 area around it. It uses your turn.")
        Text("• 💥 Cluster bomb (once): splits into five bombs that hit a plus-shaped area. It uses your turn.")
        Text("Sink every enemy ship to win. Sunk ships appear on the enemy sea so you can see what you sank.")
    }
}

// ------------------------------------------------------------------ the clock and the geometry

/** Milliseconds of animation time, ticking every frame. Read it inside a draw block so only the drawing redraws. */
@Composable
private fun rememberFrameClock(): MutableLongState {
    val clock = remember { mutableLongStateOf(0L) }
    LaunchedEffect(Unit) { while (true) androidx.compose.runtime.withFrameMillis { clock.longValue = it } }
    return clock
}

private const val MARGIN = 0.07f
private fun cellPx(side: Float, n: Int) = side * (1f - MARGIN) / n
private fun cellCentre(left: Float, top: Float, side: Float, n: Int, cell: Int): Offset {
    val c = cellPx(side, n)
    return Offset(left + side * MARGIN + (cell % n + 0.5f) * c, top + side * MARGIN + (cell / n + 0.5f) * c)
}

private fun cellName(cell: Int, n: Int) = "${'A' + cell % n}${cell / n + 1}"

private fun shipCentre(ship: SeaShip, side: Float, n: Int): Offset {
    val a = cellCentre(0f, 0f, side, n, ship.cells.first())
    val b = cellCentre(0f, 0f, side, n, ship.cells.last())
    return Offset((a.x + b.x) / 2f, (a.y + b.y) / 2f)
}

/** Draws the right kind of ship for [ship]: a submarine looks different from the surface ships. */
private fun DrawScope.drawShipSprite(ship: SeaShip, centre: Offset, c: Float, pal: ShipPalette, t: Float, sink: Float, lift: Float, phase: Float) {
    if (ship.name == "Submarine") drawSubmarine(centre, c, ship.horizontal, pal, t, phase, sink, lift)
    else drawWarship(ship.len, centre, c, ship.horizontal, pal, t, phase, sink, lift)
}

// ------------------------------------------------------------------ effects

private class SeaFxAnim(val kind: Int, val mine: Boolean, val cell: Int, val anim: Animatable<Float, AnimationVector1D>)

/** Something falling from the top of the screen onto [target]: kind 0 bomb, 1 cluster bomb (splits), 2 radar buoy. */
private class Proj(val mine: Boolean, val kind: Int, val target: Int, val extra: List<Int>, val anim: Animatable<Float, AnimationVector1D>)

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

// ------------------------------------------------------------------ one ocean board

/**
 * One sea: animated water, the ships, fire and smoke on hits, ripples on misses, the aiming marker, and the transient
 * effects, all in one canvas. [hidden] squares are drawn as they were before the latest bomb landed (it is still falling).
 */
@Composable
private fun SeaBoardView(
    s: SeaState,
    mine: Boolean,
    clock: MutableLongState,
    grid: String,
    ships: List<SeaShip>,
    hidden: List<Int>,
    sinkStart: Map<Int, Long>,
    placedAt: Map<Int, Long>,
    fx: List<SeaFxAnim>,
    popups: List<Popup>,
    onPopupDone: (Long) -> Unit,
    aim: Int,
    weapon: SeaWeapon?,
    preview: List<Int>,
    previewOk: Boolean,
    modifier: Modifier,
    onBounds: (Rect) -> Unit = {},
    onTap: ((Int, Int) -> Unit)? = null,
    onDragStart: ((Int, Int) -> Unit)? = null,
    onDrag: ((Int, Int) -> Unit)? = null,
    onDragEnd: (() -> Unit)? = null,
) {
    val n = s.size.w
    val measurer = rememberTextMeasurer()
    BoxWithConstraints(modifier) {
        val side = minOf(maxWidth, maxHeight)
        Box(Modifier.size(side).align(Alignment.Center)) {
            Canvas(
                Modifier.fillMaxSize()
                    .onGloballyPositioned { onBounds(it.boundsInRoot()) }
                    .pointerInput(onTap != null, n) {
                        if (onTap == null) return@pointerInput
                        detectTapGestures { p ->
                            val m = size.width * MARGIN
                            val c = (size.width - m) / n
                            val x = ((p.x - m) / c).toInt()
                            val y = ((p.y - m) / c).toInt()
                            if (x in 0 until n && y in 0 until n) onTap(x, y)
                        }
                    }
                    .pointerInput(onDragStart != null, n) {
                        if (onDragStart == null) return@pointerInput
                        fun cellOf(p: Offset): Pair<Int, Int>? {
                            val m = size.width * MARGIN
                            val c = (size.width - m) / n
                            val x = ((p.x - m) / c).toInt()
                            val y = ((p.y - m) / c).toInt()
                            return if (x in 0 until n && y in 0 until n) x to y else null
                        }
                        detectDragGestures(
                            onDragStart = { p -> cellOf(p)?.let { onDragStart(it.first, it.second) } },
                            onDrag = { change, _ -> cellOf(change.position)?.let { onDrag?.invoke(it.first, it.second) } },
                            onDragEnd = { onDragEnd?.invoke() },
                            onDragCancel = { onDragEnd?.invoke() },
                        )
                    },
            ) {
                val t = clock.longValue / 1000f
                val now = clock.longValue
                val m = size.width * MARGIN
                val c = (size.width - m) / n
                fun centreOf(cell: Int) = Offset(m + (cell % n + 0.5f) * c, m + (cell / n + 0.5f) * c)

                drawOcean(size.width, size.height, t, enemy = !mine)
                // the grid
                for (i in 0..n) {
                    drawLine(Color.White.copy(alpha = 0.13f), Offset(m + i * c, m), Offset(m + i * c, m + n * c), 1f)
                    drawLine(Color.White.copy(alpha = 0.13f), Offset(m, m + i * c), Offset(m + n * c, m + i * c), 1f)
                }
                for (i in 0 until n) {
                    val st = TextStyle(fontSize = (c * 0.38f).toSp(), color = Color.White.copy(alpha = 0.7f), fontWeight = FontWeight.Bold)
                    val a = measurer.measure("${'A' + i}", st)
                    drawText(a, topLeft = Offset(m + (i + 0.5f) * c - a.size.width / 2f, m / 2f - a.size.height / 2f))
                    val b = measurer.measure("${i + 1}", st)
                    drawText(b, topLeft = Offset(m / 2f - b.size.width / 2f, m + (i + 0.5f) * c - b.size.height / 2f))
                }
                // squares that were hit or missed (fire, smoke and ripples)
                val sunkCells = HashSet<Int>()
                ships.forEach { sh -> if (sh.sunk && sh.cells.none { it in hidden }) sunkCells.addAll(sh.cells) }
                for (cell in 0 until n * n) {
                    var ch = grid.getOrElse(cell) { '.' }
                    if (cell in hidden) ch = if (mine && ch == 'x') 'S' else '.'
                    val cc = centreOf(cell)
                    when (ch) {
                        'o' -> {
                            for (k in 0..1) {
                                val ph = (t * 0.55f + cell * 0.17f + k * 0.5f) % 1f
                                drawCircle(Color.White.copy(alpha = (1f - ph) * 0.45f), c * (0.1f + ph * 0.34f), cc, style = Stroke(c * 0.04f + 1f))
                            }
                            drawCircle(Color.White.copy(alpha = 0.55f), c * 0.06f, cc)
                        }
                        '#' -> if (mine || ships.none { cell in it.cells }) drawCircle(Color(0xFF450A0A).copy(alpha = 0.5f), c * 0.3f, cc)
                    }
                }
                // ships
                ships.forEachIndexed { idx, ship ->
                    if (ship.cells.isEmpty()) return@forEachIndexed
                    val key = ship.cells.min()
                    val waiting = ship.sunk && ship.cells.any { it in hidden }
                    if (!mine && waiting) return@forEachIndexed
                    val sinkP = if (ship.sunk && !waiting) (sinkStart[key]?.let { ((now - it) / 2400f).coerceIn(0f, 1f) } ?: 1f) else 0f
                    val dropP = placedAt[key]?.let { ((now - it) / 480f).coerceIn(0f, 1f) } ?: 1f
                    val lift = (1f - dropP) * (1f - dropP) * c * 3.4f
                    val centre = shipCentre(ship, size.width, n)
                    val pal = when {
                        !mine && sinkP >= 0.99f -> WreckShip
                        !mine -> EnemyShip
                        sinkP >= 0.99f -> WreckShip
                        else -> FriendlyShip
                    }
                    drawShipSprite(ship, centre, c, pal, t, sinkP, lift, idx.toFloat())
                    if (dropP >= 1f) {
                        val since = now - (placedAt[key] ?: 0L)
                        if (placedAt[key] != null && since < 900L) {
                            val q = ((since - 480L) / 420f).coerceIn(0f, 1f)
                            drawOval(Color.White.copy(alpha = (1f - q) * 0.7f), Offset(centre.x - ship.len * c * (0.3f + q * 0.35f), centre.y - c * (0.2f + q * 0.35f)), Size(ship.len * c * (0.6f + q * 0.7f), c * (0.4f + q * 0.7f)), style = Stroke(c * 0.05f + 1f))
                        }
                    }
                    // a sinking ship: bubbles and an oil slick
                    if (sinkP > 0f) {
                        for (k in 0 until 6) {
                            val ph = (t * 0.8f + k * 0.17f) % 1f
                            drawCircle(Color.White.copy(alpha = (1f - ph) * 0.6f * (1f - sinkP * 0.5f)), c * (0.05f + 0.06f * (k % 3)), Offset(centre.x + sin(k * 2.1f + t) * ship.len * c * 0.3f, centre.y - ph * c * 0.9f))
                        }
                        if (sinkP >= 0.99f) drawOval(Color(0xFF111827).copy(alpha = 0.35f), Offset(centre.x - ship.len * c * 0.6f, centre.y - c * 0.3f), Size(ship.len * c * 1.2f, c * 0.6f))
                    }
                }
                // fire on the squares that were hit and are not yet sunk
                for (cell in 0 until n * n) {
                    var ch = grid.getOrElse(cell) { '.' }
                    if (cell in hidden) ch = '.'
                    if (ch == 'x' && cell !in sunkCells) drawFire(centreOf(cell), c * 0.95f, t, cell)
                }
                // the radar result stays on the enemy sea
                if (!mine && s.radarCount >= 0) {
                    val x0 = (s.radarX - 1).coerceAtLeast(0)
                    val y0 = (s.radarY - 1).coerceAtLeast(0)
                    val x1 = (s.radarX + 1).coerceAtMost(n - 1)
                    val y1 = (s.radarY + 1).coerceAtMost(n - 1)
                    val glow = 0.6f + 0.4f * sin(t * 4f)
                    drawRect(Radar.copy(alpha = 0.1f), Offset(m + x0 * c, m + y0 * c), Size((x1 - x0 + 1) * c, (y1 - y0 + 1) * c))
                    drawRect(Radar.copy(alpha = glow), Offset(m + x0 * c, m + y0 * c), Size((x1 - x0 + 1) * c, (y1 - y0 + 1) * c), style = Stroke(c * 0.06f + 1f))
                    val txt = measurer.measure("${s.radarCount}", TextStyle(fontSize = (c * 1.1f).toSp(), color = Radar, fontWeight = FontWeight.Black))
                    drawText(txt, topLeft = Offset(m + (s.radarX + 0.5f) * c - txt.size.width / 2f, m + (s.radarY + 0.5f) * c - txt.size.height / 2f))
                }
                // where you are aiming
                if (aim >= 0 && weapon != null && !mine) {
                    val ax = aim % n
                    val ay = aim / n
                    val cells = when (weapon) {
                        SeaWeapon.SHOT -> listOf(ax to ay)
                        SeaWeapon.BOMB -> listOf(ax to ay, ax - 1 to ay, ax + 1 to ay, ax to ay - 1, ax to ay + 1)
                        SeaWeapon.RADAR -> (-1..1).flatMap { dy -> (-1..1).map { dx -> ax + dx to ay + dy } }
                    }.filter { (px, py) -> px in 0 until n && py in 0 until n }
                    val tint = if (weapon == SeaWeapon.RADAR) Radar else Funk.Red
                    for ((px, py) in cells) drawRect(tint.copy(alpha = 0.2f + 0.1f * sin(t * 6f)), Offset(m + px * c, m + py * c), Size(c, c))
                    drawReticle(centreOf(aim), c * 1.1f, t, tint)
                }
                // a ship being placed
                if (preview.isNotEmpty()) {
                    val tint = if (previewOk) Funk.Lime else Funk.Red
                    for (cell in preview) drawRect(tint.copy(alpha = 0.35f), Offset(m + (cell % n) * c, m + (cell / n) * c), Size(c, c))
                    drawRect(tint, Offset(m + (preview.minOf { it % n }) * c, m + (preview.minOf { it / n }) * c), Size((preview.maxOf { it % n } - preview.minOf { it % n } + 1) * c, (preview.maxOf { it / n } - preview.minOf { it / n } + 1) * c), style = Stroke(c * 0.06f + 1f))
                }
                // the one-off effects
                fx.forEach { f ->
                    if (f.mine != mine) return@forEach
                    val p = f.anim.value
                    val cc = centreOf(f.cell)
                    when (f.kind) {
                        FX_SPLASH -> drawWaterSpout(cc, c * 1.1f, p)
                        FX_HIT -> { drawExplosion(cc, c * 1.5f, p); drawWaterSpout(cc, c * 0.7f, (p * 1.3f).coerceAtMost(0.99f)) }
                        FX_SUNK -> { drawExplosion(cc, c * 2.4f, p); drawWaterSpout(cc, c * 1.5f, p) }
                        else -> drawRadarSweep(cc, c * 1.4f, p)
                    }
                }
            }
            PopupLayer(popups, onPopupDone)
        }
    }
}

// ------------------------------------------------------------------ the falling bombs (drawn over the whole screen)

@Composable
private fun ProjectileLayer(projs: List<Proj>, enemyRect: Rect?, mineRect: Rect?, n: Int, origin: Offset) {
    Canvas(Modifier.fillMaxSize()) {
        for (p in projs) {
            val rect = (if (p.mine) mineRect else enemyRect) ?: continue
            val left = rect.left - origin.x
            val top = rect.top - origin.y
            val side = rect.width
            val c = cellPx(side, n)
            val target = cellCentre(left, top, side, n, p.target)
            val q = p.anim.value
            val bomb = c * 1.5f
            when (p.kind) {
                0 -> {
                    val e = q * q
                    val y = -bomb * 2f + (target.y + bomb * 2f) * e
                    drawFallShadow(target, c * 1.2f, e)
                    drawFallingBomb(Offset(target.x + sin(q * 9f) * c * 0.1f * (1f - q), y), bomb * (0.7f + 0.5f * e), p.mine, c * 3.2f * q.coerceAtMost(0.8f))
                }
                1 -> {
                    val split = 0.55f
                    val splitY = target.y - c * 4.5f
                    if (q < split) {
                        val e = (q / split) * (q / split)
                        val y = -bomb * 3f + (splitY + bomb * 3f) * e
                        drawFallingBomb(Offset(target.x, y), bomb * 1.7f * (0.7f + 0.3f * e), p.mine, c * 4f * (q / split))
                    } else {
                        val e = ((q - split) / (1f - split))
                        // the big bomb opens up and five small ones fan out
                        drawCircle(Color.White.copy(alpha = (1f - e) * 0.8f), c * (0.6f + e * 1.6f), Offset(target.x, splitY), style = Stroke(c * 0.12f))
                        for (cell in p.extra) {
                            val dest = cellCentre(left, top, side, n, cell)
                            val start = Offset(target.x, splitY)
                            val ee = e * e
                            drawFallShadow(dest, c, ee)
                            drawFallingBomb(Offset(start.x + (dest.x - start.x) * e, start.y + (dest.y - start.y) * ee), bomb * 0.8f, p.mine, c * 1.8f * e)
                        }
                    }
                }
                else -> {
                    val e = 1f - (1f - q) * (1f - q)
                    val y = -bomb * 2f + (target.y + bomb * 2f) * e
                    drawFallShadow(target, c, e)
                    drawParachute(Offset(target.x + sin(q * 6f) * c * 0.35f * (1f - q), y), c * 1.5f)
                }
            }
        }
    }
}

// ------------------------------------------------------------------ placing the fleet

/** The squares ship [len] would cover with its first square at (x, y), moved to fit inside the sea. */
private fun shipCells(n: Int, len: Int, x: Int, y: Int, horizontal: Boolean): List<Int> {
    val px = if (horizontal) x.coerceIn(0, n - len) else x.coerceIn(0, n - 1)
    val py = if (horizontal) y.coerceIn(0, n - 1) else y.coerceIn(0, n - len)
    return List(len) { k -> if (horizontal) py * n + px + k else (py + k) * n + px }
}

@Composable
private fun ShipCard(ship: SeaShip, selected: Boolean, clock: MutableLongState, idx: Int, onClick: () -> Unit) {
    val placed = ship.cells.isNotEmpty()
    val shape = RoundedCornerShape(14.dp)
    Column(
        Modifier.clip(shape)
            .background(if (selected) Funk.Lime.copy(alpha = 0.22f) else Color.White.copy(alpha = if (placed) 0.05f else 0.12f))
            .border(if (selected) 2.5.dp else 1.dp, if (selected) Funk.Lime else Color.White.copy(alpha = 0.3f), shape)
            .clickable(onClick = onClick).padding(horizontal = 8.dp, vertical = 6.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
    ) {
        Canvas(Modifier.size(width = (ship.len * 20).dp, height = 28.dp)) {
            val c = size.width / ship.len
            drawOcean(size.width, size.height, clock.longValue / 1000f, false)
            drawShipSprite(ship, Offset(size.width / 2f, size.height / 2f), minOf(c, size.height / 0.9f), FriendlyShip, clock.longValue / 1000f, 0f, 0f, idx.toFloat())
        }
        Text(ship.name, fontSize = 11.sp, fontWeight = FontWeight.Bold, maxLines = 1, color = Color.White.copy(alpha = if (placed) 0.6f else 1f))
        Text(if (placed) "✓ placed" else "${ship.len} squares", fontSize = 10.sp, color = if (placed) Funk.Lime else Color.White.copy(alpha = 0.7f))
    }
}

@Composable
fun SeaPlacingScreen(s: SeaState, act: ActSea, onLeave: () -> Unit) {
    val clock = rememberFrameClock()
    val n = s.size.w
    var selected by remember { mutableIntStateOf(-1) }
    var horizontal by remember { mutableStateOf(true) }
    var previewCell by remember { mutableIntStateOf(-1) }
    var message by remember { mutableStateOf("") }
    val placedAt = remember { mutableStateMapOf<Int, Long>() }
    val lastCells = remember { mutableStateMapOf<Int, List<Int>>() }

    // the first ship waiting to be placed is selected for you
    LaunchedEffect(s.phase, s.size) {
        selected = s.myShips.indexOfFirst { it.cells.isEmpty() }
        lastCells.clear(); placedAt.clear()
    }
    // a ship that has just been put down drops in with a splash
    LaunchedEffect(s.myShips) {
        s.myShips.forEachIndexed { i, ship ->
            val before = lastCells[i] ?: emptyList()
            if (ship.cells.isNotEmpty() && ship.cells != before) placedAt[ship.cells.min()] = clock.longValue
            lastCells[i] = ship.cells
        }
    }

    val others = s.myShips.filterIndexed { i, sh -> i != selected && sh.cells.isNotEmpty() }.flatMap { it.cells }.toSet()
    fun nextUnplaced(except: Int) = s.myShips.indices.firstOrNull { it != except && s.myShips[it].cells.isEmpty() } ?: -1

    fun place(x: Int, y: Int) {
        if (selected < 0) return
        val len = s.size.fleet[selected]
        val cells = shipCells(n, len, x, y, horizontal)
        if (cells.any { it in others }) {
            message = "Ships can't overlap: try another spot."
            Sfx.play(Sound.BUZZ)
            return
        }
        val first = cells.first()
        val idx = selected
        act { placeShip(idx, first % n, first / n, horizontal) }
        Sfx.play(Sound.DROP)
        message = ""
        selected = nextUnplaced(idx)
        previewCell = -1
    }
    fun pickUp(cell: Int): Boolean {
        val i = s.myShips.indexOfFirst { cell in it.cells }
        if (i < 0) return false
        horizontal = s.myShips[i].horizontal
        act { removeShip(i) }
        selected = i
        Sfx.play(Sound.TAP)
        return true
    }

    val previewCells = if (selected >= 0 && previewCell >= 0) shipCells(n, s.size.fleet[selected], previewCell % n, previewCell / n, horizontal) else emptyList()
    val previewOk = previewCells.none { it in others }

    BoxWithConstraints(Modifier.fillMaxSize()) {
        val landscape = maxWidth > maxHeight
        val boardSide = maxWidth - 20.dp
        val board: @Composable (Modifier) -> Unit = { m ->
            SeaBoardView(
                s, mine = true, clock = clock, grid = "", ships = s.myShips, hidden = emptyList(), sinkStart = emptyMap(), placedAt = placedAt,
                fx = emptyList(), popups = emptyList(), onPopupDone = {}, aim = -1, weapon = null,
                preview = if (s.iAmReady) emptyList() else previewCells, previewOk = previewOk, modifier = m,
                onTap = { x, y ->
                    if (s.iAmReady) return@SeaBoardView
                    val cell = y * n + x
                    val hasShip = s.myShips.any { cell in it.cells }
                    if (hasShip && (selected < 0 || cell in others)) pickUp(cell)
                    else if (selected >= 0) place(x, y)
                    else if (!hasShip) selected = s.myShips.indexOfFirst { it.cells.isEmpty() }
                },
                onDragStart = { x, y ->
                    if (s.iAmReady) return@SeaBoardView
                    val cell = y * n + x
                    if (s.myShips.any { cell in it.cells } && (selected < 0 || cell in others)) pickUp(cell)
                    previewCell = cell
                },
                onDrag = { x, y -> previewCell = y * n + x },
                onDragEnd = {
                    if (previewCell >= 0 && selected >= 0 && !s.iAmReady) place(previewCell % n, previewCell / n)
                    previewCell = -1
                },
            )
        }
        val controls: @Composable () -> Unit = {
            Column(Modifier.fillMaxWidth(), verticalArrangement = Arrangement.spacedBy(8.dp), horizontalAlignment = Alignment.CenterHorizontally) {
                s.myShips.indices.toList().chunked(3).forEach { rowIdx ->
                    Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        rowIdx.forEach { i ->
                            ShipCard(s.myShips[i], selected == i, clock, i) {
                                if (s.iAmReady) return@ShipCard
                                if (s.myShips[i].cells.isNotEmpty()) { horizontal = s.myShips[i].horizontal; act { removeShip(i) } }
                                selected = i
                            }
                        }
                    }
                }
                if (message.isNotEmpty()) Text(message, color = Funk.Pink, fontSize = 13.sp, fontWeight = FontWeight.Bold)
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp), modifier = Modifier.fillMaxWidth()) {
                    FunkyButton(if (horizontal) "↔ Across" else "↕ Down", { horizontal = !horizontal; Sfx.play(Sound.TAP) }, Modifier.weight(1f), listOf(Funk.Orange, Funk.Pink), enabled = !s.iAmReady, fontSize = 14.sp)
                    FunkyButton("🎲 Random", { act { shuffleFleet() }; selected = -1; Sfx.play(Sound.POP) }, Modifier.weight(1f), listOf(Funk.Violet, Funk.Blue), enabled = !s.iAmReady, fontSize = 14.sp)
                    FunkyButton("🧹 Clear", { act { clearFleet() }; selected = 0; Sfx.play(Sound.TAP) }, Modifier.weight(1f), listOf(Funk.Violet, Funk.Blue), enabled = !s.iAmReady && s.myShips.any { it.cells.isNotEmpty() }, fontSize = 14.sp)
                }
                FunkyButton(
                    if (s.iAmReady) "✓ Ready" else if (s.allPlaced) "⚓ READY FOR BATTLE!" else "Place all your ships first",
                    { act { ready() }; Sfx.play(Sound.GO) }, Modifier.fillMaxWidth(), listOf(Funk.Mint, Funk.Cyan), enabled = s.allPlaced && !s.iAmReady,
                )
                if (s.iAmReady && !s.theyAreReady) {
                    CircularProgressIndicator(Modifier.size(24.dp))
                    Text("Waiting for ${s.theirName} to place their fleet…")
                } else if (!s.iAmReady && s.theyAreReady) {
                    Text("${s.theirName} is ready and waiting for you!", fontWeight = FontWeight.Bold, color = Funk.Sun)
                }
                TextButton(onClick = onLeave) { Text("Leave") }
            }
        }
        if (!landscape) {
            Column(Modifier.fillMaxSize().padding(10.dp).verticalScroll(rememberScrollState()), horizontalAlignment = Alignment.CenterHorizontally, verticalArrangement = Arrangement.spacedBy(8.dp)) {
                GradientText("🚢 Place your fleet", fontSize = 26.sp, colors = listOf(Funk.Cyan, Funk.Blue, Funk.Mint))
                Text(
                    "Tap a ship below, then tap or drag it onto the sea. Tap a placed ship to pick it up again. Only you can see this.",
                    textAlign = TextAlign.Center, style = MaterialTheme.typography.bodySmall,
                )
                board(Modifier.fillMaxWidth().height(boardSide))
                controls()
            }
        } else {
            Row(Modifier.fillMaxSize().padding(8.dp), horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                board(Modifier.weight(1f).fillMaxHeight())
                Column(Modifier.width(300.dp).fillMaxHeight().verticalScroll(rememberScrollState()), verticalArrangement = Arrangement.spacedBy(8.dp), horizontalAlignment = Alignment.CenterHorizontally) {
                    GradientText("🚢 Place your fleet", fontSize = 22.sp, colors = listOf(Funk.Cyan, Funk.Blue, Funk.Mint))
                    Text("Tap a ship, then tap or drag it onto the sea. Tap a placed ship to pick it up.", textAlign = TextAlign.Center, style = MaterialTheme.typography.bodySmall)
                    controls()
                }
            }
        }
    }
}

// ------------------------------------------------------------------ battle

@Composable
fun SeaBattleScreen(s: SeaState, act: ActSea) {
    var help by rememberSaveable { mutableStateOf(false) }
    var details by rememberSaveable { mutableStateOf(false) }
    val canFire = s.myTurn && !s.awaiting
    val clock = rememberFrameClock()
    val fx = remember { mutableStateListOf<SeaFxAnim>() }
    val projs = remember { mutableStateListOf<Proj>() }
    val hiddenEnemy = remember { mutableStateListOf<Int>() }
    val hiddenMine = remember { mutableStateListOf<Int>() }
    val sinkStartEnemy = remember { mutableStateMapOf<Int, Long>() }
    val sinkStartMine = remember { mutableStateMapOf<Int, Long>() }
    val enemyPop = remember { mutableStateListOf<Popup>() }
    val minePop = remember { mutableStateListOf<Popup>() }
    val log = remember { mutableStateListOf<String>() }
    var hurt by remember { mutableIntStateOf(0) }
    var popupId by remember { mutableLongStateOf(0L) }
    var aim by remember { mutableIntStateOf(-1) }
    var enemyRect by remember { mutableStateOf<Rect?>(null) }
    var mineRect by remember { mutableStateOf<Rect?>(null) }
    var origin by remember { mutableStateOf(Offset.Zero) }
    var banner by remember { mutableStateOf("") }
    val scope = rememberCoroutineScope()
    val shake = rememberBurst(hurt, 650)
    val flash = rememberBurst(hurt, 700)
    val n = s.size.w

    fun spawn(kind: Int, mine: Boolean, cell: Int, millis: Int) {
        val a = Animatable(0f)
        val f = SeaFxAnim(kind, mine, cell, a)
        fx.add(f)
        scope.launch {
            a.animateTo(1f, tween(millis, easing = LinearEasing))
            fx.remove(f)
        }
    }

    // "BATTLE STATIONS!" when the fighting begins
    LaunchedEffect(s.phase) {
        if (s.phase == SeaPhase.BATTLE && s.moves == 0) {
            banner = if (s.myTurn) "BATTLE STATIONS!\nYou fire first" else "BATTLE STATIONS!\n${s.theirName} fires first"
            Sfx.play(Sound.GO)
            delay(2200)
            banner = ""
        }
    }
    // my aim only makes sense on my turn, and on a square that is still unknown
    LaunchedEffect(s.moves, s.myTurn, s.weapon) {
        if (!s.myTurn || (aim >= 0 && s.enemyGrid.getOrElse(aim) { '.' } != '.')) aim = -1
    }

    LaunchedEffect(s.moves) {
        if (s.moves == 0) {
            fx.clear(); projs.clear(); hiddenEnemy.clear(); hiddenMine.clear(); enemyPop.clear(); minePop.clear(); log.clear(); sinkStartEnemy.clear(); sinkStartMine.clear()
            return@LaunchedEffect
        }
        s.note?.let { log.add(0, it) }
        while (log.size > 40) log.removeAt(log.lastIndex)
        val w = s.size.w
        for (onMine in listOf(false, true)) {
            val list = s.fx.filter { it.mine == onMine }
            if (list.isEmpty()) continue
            val cells = list.map { it.cell }
            val radar = list.any { it.radar }
            val kind = if (radar) 2 else if (list.size > 1) 1 else 0
            val hidden = if (onMine) hiddenMine else hiddenEnemy
            val shipsHere = if (onMine) s.myShips.filter { it.sunk } else s.enemySunk
            val sunkShips = shipsHere.filter { sh -> sh.cells.any { it in cells } }
            val holdCells = (cells + sunkShips.flatMap { it.cells }).distinct()
            hidden.addAll(holdCells)
            val centre = if (radar) list.first { it.radar }.cell else cells.minByOrNull { c -> cells.sumOf { abs(it % w - c % w) + abs(it / w - c / w) } } ?: cells.first()
            val targets = if (kind == 1) cells else emptyList()
            val duration = when (kind) { 1 -> 950; 2 -> 900; else -> 720 }
            val first = list.first()
            scope.launch {
                val proj = Proj(onMine, kind, centre, targets, Animatable(0f))
                projs.add(proj)
                Sfx.play(Sound.WHOOSH)
                proj.anim.animateTo(1f, tween(duration, easing = LinearEasing))
                projs.remove(proj)
                // impact
                hidden.removeAll(holdCells.toSet())
                sunkShips.forEach { (if (onMine) sinkStartMine else sinkStartEnemy)[it.cells.min()] = clock.longValue }
                list.forEach { e ->
                    when {
                        e.radar -> spawn(FX_RADAR, e.mine, e.cell, 1300)
                        e.sunk -> spawn(FX_SUNK, e.mine, e.cell, 1100)
                        e.hit -> spawn(FX_HIT, e.mine, e.cell, 900)
                        else -> spawn(FX_SPLASH, e.mine, e.cell, 1000)
                    }
                }
                when {
                    list.any { it.sunk } -> { Sfx.play(Sound.EXPLODE); delay(250); Sfx.play(Sound.EXPLODE) }
                    list.any { it.hit } -> Sfx.play(Sound.EXPLODE)
                    radar -> Sfx.play(Sound.CHIME)
                    else -> Sfx.play(Sound.SPLASH)
                }
                if (onMine && list.any { it.hit }) hurt++
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
    }

    val enemyBoard: @Composable (Modifier) -> Unit = { m ->
        SeaBoardView(
            s, mine = false, clock = clock, grid = s.enemyGrid, ships = s.enemySunk, hidden = hiddenEnemy, sinkStart = sinkStartEnemy, placedAt = emptyMap(),
            fx = fx, popups = enemyPop, onPopupDone = { id -> enemyPop.removeAll { it.id == id } },
            aim = if (canFire) aim else -1, weapon = s.weapon, preview = emptyList(), previewOk = true, modifier = m,
            onBounds = { enemyRect = it },
            onTap = { x, y ->
                if (!canFire) return@SeaBoardView
                val cell = y * n + x
                if (s.enemyGrid.getOrElse(cell) { '.' } != '.' && s.weapon != SeaWeapon.RADAR) return@SeaBoardView
                if (aim == cell) { act { fire(x, y) }; aim = -1 } else { aim = cell; Sfx.play(Sound.TICK) }
            },
        )
    }
    val myBoard: @Composable (Modifier) -> Unit = { m ->
        SeaBoardView(
            s, mine = true, clock = clock, grid = s.myGrid, ships = s.myShips, hidden = hiddenMine, sinkStart = sinkStartMine, placedAt = emptyMap(),
            fx = fx, popups = minePop, onPopupDone = { id -> minePop.removeAll { it.id == id } },
            aim = -1, weapon = null, preview = emptyList(), previewOk = true, modifier = m, onBounds = { mineRect = it },
        )
    }
    val fireRow: @Composable () -> Unit = {
        FunkyButton(
            when {
                !s.myTurn -> "⏳ ${s.theirName} is aiming…"
                s.awaiting -> "💨 Bomb away…"
                aim >= 0 -> "🔥 FIRE at ${cellName(aim, n)}!"
                else -> "Tap the enemy sea to aim"
            },
            { if (aim >= 0) { val a = aim; act { fire(a % n, a / n) }; aim = -1 } },
            Modifier.fillMaxWidth(), listOf(Funk.Red, Funk.Orange), enabled = canFire && aim >= 0, fontSize = 16.sp,
        )
    }

    Box(Modifier.fillMaxSize().onGloballyPositioned { origin = it.positionInRoot() }) {
        BoxWithConstraints(Modifier.fillMaxSize().shaking(shake)) {
            val landscape = maxWidth > maxHeight
            if (flash < 1f) Box(Modifier.fillMaxSize().background(Funk.Red.copy(alpha = 0.35f * (1f - flash))))

            if (!landscape) {
                Column(Modifier.fillMaxSize().padding(8.dp), verticalArrangement = Arrangement.spacedBy(6.dp)) {
                    SeaHud(s, canFire, onDetails = { details = true }, onHelp = { help = true })
                    enemyBoard(Modifier.weight(1f).fillMaxWidth())
                    WeaponRow(s, canFire, act)
                    fireRow()
                    Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(10.dp), verticalAlignment = Alignment.CenterVertically) {
                        myBoard(Modifier.size(132.dp))
                        Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(2.dp)) {
                            Text("YOUR FLEET", fontSize = 12.sp, fontWeight = FontWeight.Black, color = Funk.Cyan)
                            s.note?.let { Text(it, fontWeight = FontWeight.Bold, fontSize = 13.sp) }
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
                        fireRow()
                        s.note?.let { Text(it, fontWeight = FontWeight.Bold) }
                        Text("YOUR FLEET", fontSize = 12.sp, fontWeight = FontWeight.Black, color = Funk.Cyan)
                        myBoard(Modifier.size(150.dp))
                    }
                }
            }
        }
        // the bombs fall over the top of everything, from the top edge of the screen
        ProjectileLayer(projs, enemyRect, mineRect, n, origin)
        if (banner.isNotEmpty()) {
            Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                Text(
                    banner, fontSize = 30.sp, fontWeight = FontWeight.Black, color = Color.White, textAlign = TextAlign.Center, letterSpacing = 2.sp,
                    modifier = Modifier.clip(RoundedCornerShape(18.dp)).background(Color(0xCC050B1E)).padding(horizontal = 24.dp, vertical = 14.dp),
                )
            }
        }
    }
    if (details) SeaDetails(s, log) { details = false }
    if (help) {
        AlertDialog(
            onDismissRequest = { help = false },
            confirmButton = { TextButton(onClick = { help = false }) { Text("Got it") } },
            title = { Text("🚢 Battleships") },
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
    val total = s.size.fleet.size
    val sunk = total - s.theirShipsLeft
    val lost = total - s.myShipsLeft
    val acc = if (s.shots > 0) (100 * s.hits / s.shots) else 0
    Box(Modifier.fillMaxSize()) {
        // the finished boards stay underneath, so "See the board" shows where the ships went down
        SeaBattleScreen(s) { }
        EndOverlay(
            title = if (won) "🏆 VICTORY!" else "💀 ${s.theirName.uppercase()} WINS",
            subtitle = "Ships sunk: you $sunk of $total  ·  ${s.theirName} $lost of $total",
            win = won, isHost = s.isHost, theirName = s.theirName,
            playAgainLabel = "Play again (${s.size.label})", colors = listOf(Funk.Cyan, Funk.Blue),
            onPlayAgain = onPlayAgain, onBackToGames = onBackToGames, onLeave = onLeave,
            how = if (won) "You sank the last enemy ship after ${s.shots} shots: ${s.hits} hits, $acc% accuracy. The finished boards show where every ship went down."
            else "${s.theirName} sank your last ship. You had sunk $sunk of their $total with ${s.hits} hits from ${s.shots} shots.",
        )
    }
}

/** Picks the right Battleships screen for the current phase. */
@Composable
fun SeaRoot(s: SeaState, act: ActSea, onPlayAgain: () -> Unit, onBackToGames: () -> Unit, onLeave: () -> Unit) {
    when (s.phase) {
        SeaPhase.PLACING -> SeaPlacingScreen(s, act, onLeave)
        SeaPhase.BATTLE -> SeaBattleScreen(s, act)
        SeaPhase.OVER -> SeaOverScreen(s, onPlayAgain, onBackToGames, onLeave)
        SeaPhase.LOBBY -> Unit
    }
}
