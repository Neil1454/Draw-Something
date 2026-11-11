package com.neil1454.drawsomething.ui

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.gestures.awaitEachGesture
import androidx.compose.foundation.gestures.awaitFirstDown
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableLongStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.drawscope.DrawScope
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.drawscope.clipRect
import androidx.compose.ui.graphics.drawscope.withTransform
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.neil1454.drawsomething.core.Arcade
import com.neil1454.drawsomething.core.ArcadeCommon
import com.neil1454.drawsomething.core.BulletView
import com.neil1454.drawsomething.core.GPhase
import com.neil1454.drawsomething.core.ShipView
import com.neil1454.drawsomething.core.Sound
import com.neil1454.drawsomething.sound.Sfx
import kotlinx.coroutines.delay
import kotlin.math.PI
import kotlin.math.abs
import kotlin.math.cos
import kotlin.math.min
import kotlin.math.sin

// ====================================================================================
// What the two arcade games (Space Invaders and Centipede) share on screen: the touch controls, the space-scape,
// ships, bullets, particles, screen shake, the HUD and the countdown. Each game draws its own enemies on top.
// Everything is drawn in "field units" (100 wide, 140 tall) and scaled to fit, in portrait or landscape.
// ====================================================================================

val HostShip = Color(0xFF38BDF8)
val GuestShip = Color(0xFFFB923C)

/** Per-frame facts handed to the games' drawing code. */
class ArcadeFrame(val nowMs: Long, val dt: Float, val ageSec: Float, val iAmHost: Boolean) {
    val mineCol get() = if (iAmHost) HostShip else GuestShip
    val theirCol get() = if (iAmHost) GuestShip else HostShip
}

private class Spark(val x: Float, val y: Float, val vx: Float, val vy: Float, val born: Long, val life: Long, val col: Color, val size: Float)
private class Ring(val x: Float, val y: Float, val born: Long, val life: Long, val col: Color, val maxR: Float)

/** Explosions, glints and screen shake. */
class Fx {
    private val sparks = ArrayList<Spark>()
    private val rings = ArrayList<Ring>()
    private var shakeStart = 0L
    private var shakeMs = 0L
    private var shakeAmp = 0f

    fun explode(x: Float, y: Float, now: Long, col: Color, count: Int = 14, power: Float = 1f) {
        for (i in 0 until count) {
            val a = (i.toFloat() / count) * 2f * PI.toFloat() + (i % 3) * 0.4f
            val sp = (14f + (i * 7919 % 23)) * power
            sparks.add(Spark(x, y, cos(a) * sp, sin(a) * sp, now, 420L + (i * 37 % 260), if (i % 3 == 0) Color.White else col, 0.7f + (i % 3) * 0.3f))
        }
        rings.add(Ring(x, y, now, 380L, col, 6f * power))
        while (sparks.size > 300) sparks.removeAt(0)
    }

    fun glow(x: Float, y: Float, now: Long, col: Color) {
        rings.add(Ring(x, y, now, 700L, col, 12f))
        rings.add(Ring(x, y, now + 120L, 700L, Color.White, 8f))
    }

    fun shake(now: Long, amp: Float, ms: Long) {
        shakeStart = now; shakeMs = ms; shakeAmp = amp
    }

    fun shakeOffset(now: Long): Offset {
        val t = now - shakeStart
        if (t >= shakeMs || shakeMs == 0L) return Offset.Zero
        val f = 1f - t / shakeMs.toFloat()
        return Offset(sin(t * 0.09f) * shakeAmp * f, cos(t * 0.11f) * shakeAmp * f * 0.7f)
    }

    fun draw(s: DrawScope, now: Long) {
        sparks.removeAll { now - it.born > it.life }
        rings.removeAll { now - it.born > it.life }
        for (r in rings) {
            val f = ((now - r.born) / r.life.toFloat()).coerceIn(0f, 1f)
            if (now >= r.born) s.drawCircle(r.col.copy(alpha = (1f - f) * 0.9f), r.maxR * f + 1f, Offset(r.x, r.y), style = Stroke(0.9f + (1f - f) * 1.2f))
        }
        for (p in sparks) {
            val t = (now - p.born) / 1000f
            val f = ((now - p.born) / p.life.toFloat()).coerceIn(0f, 1f)
            s.drawCircle(p.col.copy(alpha = 1f - f), p.size * (1f - f * 0.5f), Offset(p.x + p.vx * t, p.y + p.vy * t + 18f * t * t))
        }
    }
}

/** Smooths values from the 30-a-second pictures so everything glides at 60 frames a second. */
class Smooth {
    private val vals = HashMap<String, Float>()
    fun follow(key: String, target: Float, dt: Float, rate: Float = 22f): Float {
        val cur = vals[key] ?: target
        val next = if (abs(target - cur) > 40f) target else cur + (target - cur) * (1f - kotlin.math.exp(-rate * dt))
        vals[key] = next
        return next
    }
    fun reset() = vals.clear()
}

/**
 * The shared screen. [onInput] is told where my ship should go and whether I am firing. [world] draws the game's own
 * things (enemies, shields...) in field units.
 */
@Composable
fun ArcadeScreen(
    c: ArcadeCommon,
    theirName: String,
    isHost: Boolean,
    title: String,
    hint: String,
    endTitle: String,
    endHow: String,
    colors: List<Color>,
    onInput: (Float, Float, Boolean) -> Unit,
    onPlayAgain: () -> Unit,
    onBackToGames: () -> Unit,
    onLeave: () -> Unit,
    onBurst: (kind: String, x: Float, y: Float, fx: Fx, now: Long, mineCol: Color) -> Unit,
    background: DrawScope.(ArcadeFrame) -> Unit,
    world: DrawScope.(ArcadeFrame, Smooth) -> Unit,
) {
    val over = c.phase == GPhase.OVER
    val frameMs = remember { mutableLongStateOf(0L) }
    var ctl by remember { mutableStateOf(Offset(c.mine.x, c.mine.y.coerceIn(112f, 134f))) }
    var fire by remember { mutableStateOf(false) }
    val fx = remember { Fx() }
    val smooth = remember { Smooth() }
    val seen = remember { HashSet<Int>() }
    val mineCol = if (isHost) HostShip else GuestShip
    val theirCol = if (isHost) GuestShip else HostShip

    LaunchedEffect(Unit) { while (true) androidx.compose.runtime.withFrameMillis { frameMs.longValue = it } }
    // tell the host where my finger is, about 25 times a second
    LaunchedEffect(c.phase) {
        var sentX = -1f; var sentY = -1f; var sentF = false; var quiet = 0
        while (c.phase == GPhase.PLAYING) {
            delay(40)
            quiet++
            if (abs(ctl.x - sentX) > 0.2f || abs(ctl.y - sentY) > 0.2f || fire != sentF || quiet > 12) {
                sentX = ctl.x; sentY = ctl.y; sentF = fire; quiet = 0
                onInput(ctl.x, ctl.y, fire)
            }
        }
    }
    // my ship takes the finger's place again whenever I come back from the dead
    LaunchedEffect(c.mine.alive, c.stage) { if (c.stage == "READY" || !c.mine.alive) ctl = Offset(c.mine.x, c.mine.y.coerceIn(112f, 134f)) }
    // new explosions and glints: spawn once, with sound
    LaunchedEffect(c.bursts) {
        val now = frameMs.longValue
        for (b in c.bursts) if (seen.add(b.id)) onBurst(b.kind, b.x, b.y, fx, now, mineCol)
        if (seen.size > 400) seen.clear()
    }

    Box(
        Modifier.fillMaxSize().background(Brush.verticalGradient(listOf(Color(0xFF05061A), Color(0xFF0B0F33), Color(0xFF1A0B3A))))
            .pointerInput(Unit) {
                awaitEachGesture {
                    val down = awaitFirstDown(requireUnconsumed = false)
                    fire = true
                    var last = down.position
                    val unit = size.height / 160f
                    do {
                        val ev = awaitPointerEvent()
                        val ch = ev.changes.firstOrNull() ?: break
                        val d = ch.position - last
                        last = ch.position
                        ctl = Offset((ctl.x + d.x / unit * 1.1f).coerceIn(3f, 97f), (ctl.y + d.y / unit * 1.1f).coerceIn(112f, 134f))
                        ch.consume()
                    } while (ev.changes.any { it.pressed })
                    fire = false
                }
            },
    ) {
        BoxWithConstraints(Modifier.fillMaxSize()) {
            val landscape = maxWidth > maxHeight
            val fieldH = if (landscape) maxHeight - 10.dp else (maxHeight - 64.dp)
            val fieldW = minOf(fieldH * (100f / 140f), maxWidth - 8.dp)
            val fieldHeight = fieldW * (140f / 100f)
            val hud: @Composable (Modifier) -> Unit = { m -> Hud(c, theirName, isHost, title, m) }
            if (landscape) {
                Row(Modifier.fillMaxSize().padding(4.dp), horizontalArrangement = Arrangement.Center, verticalAlignment = Alignment.CenterVertically) {
                    Column(Modifier.weight(1f).fillMaxHeight().padding(8.dp), verticalArrangement = Arrangement.Center, horizontalAlignment = Alignment.CenterHorizontally) {
                        SidePill("You", c.mine, mineCol, true)
                        PowerChips(c.mine)
                    }
                    Field(c, isHost, fieldW, fieldHeight, ctl, fx, smooth, frameMs, background, world, hint)
                    Column(Modifier.weight(1f).fillMaxHeight().padding(8.dp), verticalArrangement = Arrangement.Center, horizontalAlignment = Alignment.CenterHorizontally) {
                        SidePill(theirName, c.theirs, theirCol, false)
                        Text(title, fontSize = 12.sp, color = Color.White.copy(alpha = 0.6f), modifier = Modifier.padding(top = 10.dp))
                        Text("WAVE ${c.level}/${c.levels}", fontSize = 15.sp, fontWeight = FontWeight.Black, color = Color.White)
                    }
                }
            } else {
                Column(Modifier.fillMaxSize().padding(4.dp), horizontalAlignment = Alignment.CenterHorizontally) {
                    hud(Modifier.fillMaxWidth().padding(top = 2.dp))
                    Box(Modifier.weight(1f).fillMaxWidth(), contentAlignment = Alignment.Center) {
                        Field(c, isHost, fieldW, fieldHeight, ctl, fx, smooth, frameMs, background, world, hint)
                    }
                }
            }
        }
        if (over) {
            EndOverlay(
                title = endTitle,
                subtitle = "You ${c.mine.score}  ·  $theirName ${c.theirs.score}",
                how = endHow,
                win = c.result == 1, isHost = isHost, theirName = theirName,
                playAgainLabel = "Play again", colors = colors,
                onPlayAgain = onPlayAgain, onBackToGames = onBackToGames, onLeave = onLeave,
            )
        }
    }
}

@Composable
private fun Field(
    c: ArcadeCommon, isHost: Boolean, w: androidx.compose.ui.unit.Dp, h: androidx.compose.ui.unit.Dp, ctl: Offset, fx: Fx, smooth: Smooth,
    frameMs: androidx.compose.runtime.MutableLongState, background: DrawScope.(ArcadeFrame) -> Unit, world: DrawScope.(ArcadeFrame, Smooth) -> Unit, hint: String,
) {
    val shape = RoundedCornerShape(10.dp)
    val lastRef = remember { LongArray(1) }
    Box(Modifier.size(w, h).clip(shape).border(2.dp, Color(0x6638BDF8), shape)) {
        Canvas(Modifier.fillMaxSize()) {
            val now = frameMs.longValue
            val dt = if (lastRef[0] == 0L) 0.016f else ((now - lastRef[0]) / 1000f).coerceIn(0.001f, 0.1f)
            lastRef[0] = now
            val k = size.width / Arcade.W
            val frame = ArcadeFrame(now, dt, ((now - c.receivedAt).coerceAtLeast(0L)) / 1000f, isHost)
            val shake = fx.shakeOffset(now)
            withTransform({ translate(shake.x * k, shake.y * k); scale(k, k, Offset.Zero) }) {
                clipRect(0f, 0f, Arcade.W, Arcade.H) {
                    background(frame)
                    world(frame, smooth)
                    drawShip(c.theirs, smooth, "theirs", frame.theirCol, now, dt, false)
                    drawShip(c.mine, smooth, "mine", frame.mineCol, now, dt, true)
                    drawBullets(c.bullets, frame)
                    fx.draw(this, now)
                }
            }
            // a faint scan-line screen and a dark vignette give it the arcade-cabinet look
            var y = 0f
            while (y < size.height) { drawRect(Color.Black.copy(alpha = 0.10f), Offset(0f, y), Size(size.width, 1.2f)); y += 3.5f }
            drawRect(Brush.radialGradient(listOf(Color.Transparent, Color.Black.copy(alpha = 0.45f)), center = Offset(size.width / 2, size.height / 2), radius = size.height * 0.75f))
        }
        // get ready / wave cleared / hint
        val banner = when (c.stage) {
            "READY" -> "WAVE ${c.level}\nGET READY"
            "WAVE" -> "WAVE CLEARED!"
            else -> ""
        }
        if (banner.isNotEmpty() && c.phase == GPhase.PLAYING) {
            Column(Modifier.align(Alignment.Center), horizontalAlignment = Alignment.CenterHorizontally) {
                Text(banner, fontSize = 30.sp, fontWeight = FontWeight.Black, color = Color.White, textAlign = TextAlign.Center, letterSpacing = 3.sp)
                if (c.stage == "READY" && c.level == 1) Text(hint, fontSize = 13.sp, color = Color(0xFF7DD3FC), textAlign = TextAlign.Center, modifier = Modifier.padding(top = 10.dp, start = 16.dp, end = 16.dp))
            }
        }
        if (c.phase == GPhase.PLAYING && !c.mine.alive && c.mine.lives == 0) {
            Text("You're out! Your partner can still win it for you.", Modifier.align(Alignment.BottomCenter).padding(10.dp), fontSize = 12.sp, color = Color.White.copy(alpha = 0.8f), textAlign = TextAlign.Center)
        }
    }
}

@Composable
private fun Hud(c: ArcadeCommon, theirName: String, isHost: Boolean, title: String, modifier: Modifier) {
    val mine = if (isHost) HostShip else GuestShip
    val theirs = if (isHost) GuestShip else HostShip
    Row(modifier, horizontalArrangement = Arrangement.spacedBy(6.dp), verticalAlignment = Alignment.CenterVertically) {
        Box(Modifier.weight(1f)) { SidePill("You", c.mine, mine, true) }
        Column(horizontalAlignment = Alignment.CenterHorizontally) {
            Text(title.uppercase(), fontSize = 9.sp, color = Color.White.copy(alpha = 0.6f), letterSpacing = 1.sp)
            Text("WAVE ${c.level}/${c.levels}", fontSize = 13.sp, fontWeight = FontWeight.Black, color = Color.White)
            PowerChips(c.mine)
        }
        Box(Modifier.weight(1f)) { SidePill(theirName, c.theirs, theirs, false) }
    }
}

@Composable
private fun SidePill(name: String, s: ShipView, col: Color, mineSide: Boolean) {
    val shape = RoundedCornerShape(14.dp)
    Column(
        Modifier.clip(shape).background(Color(0x66000000)).border(2.dp, col.copy(alpha = if (s.alive || s.lives > 0) 0.9f else 0.3f), shape).padding(horizontal = 10.dp, vertical = 6.dp),
        horizontalAlignment = if (mineSide) Alignment.Start else Alignment.End,
    ) {
        Text(name, fontSize = 11.sp, color = col, fontWeight = FontWeight.Bold, maxLines = 1)
        Text("%,d".format(s.score), fontSize = 20.sp, fontWeight = FontWeight.Black, color = Color.White)
        Text(if (s.lives > 0) "♥".repeat(s.lives.coerceAtMost(6)) else "out", fontSize = 12.sp, color = if (s.lives > 0) Color(0xFFF87171) else Color.White.copy(alpha = 0.5f))
    }
}

@Composable
private fun PowerChips(s: ShipView) {
    if (s.rapid || s.triple) {
        Row(horizontalArrangement = Arrangement.spacedBy(4.dp)) {
            if (s.rapid) Text("⚡ RAPID", fontSize = 10.sp, fontWeight = FontWeight.Black, color = Color(0xFFFDE047))
            if (s.triple) Text("🔱 TRIPLE", fontSize = 10.sp, fontWeight = FontWeight.Black, color = Color(0xFF86EFAC))
        }
    }
}

// ---------------------------------------------------------------------------- drawing helpers

/** A deep-space backdrop: gradient, three layers of stars drifting down, a glow of planet at the bottom. */
fun DrawScope.drawSpace(frame: ArcadeFrame, tint: Color = Color(0xFF38BDF8)) {
    drawRect(Brush.verticalGradient(listOf(Color(0xFF04051A), Color(0xFF0A0E33), Color(0xFF1B0B3D)), 0f, Arcade.H), Offset.Zero, Size(Arcade.W, Arcade.H))
    drawCircle(Brush.radialGradient(listOf(Color(0x55A855F7), Color.Transparent), Offset(22f, 38f), 46f), 46f, Offset(22f, 38f))
    drawCircle(Brush.radialGradient(listOf(Color(0x4438BDF8), Color.Transparent), Offset(84f, 78f), 40f), 40f, Offset(84f, 78f))
    val t = frame.nowMs / 1000f
    for (layer in 0 until 3) {
        val speed = 3f + layer * 6f
        val n = 22 + layer * 6
        for (i in 0 until n) {
            val sx = ((i * 7919 + layer * 104729) % 1000) / 10f
            val sy0 = ((i * 6007 + layer * 15485863) % 1400) / 10f
            val sy = (sy0 + t * speed) % Arcade.H
            val tw = 0.55f + 0.45f * sin(t * (1.2f + layer) + i)
            drawCircle(Color.White.copy(alpha = (0.25f + layer * 0.22f) * tw), 0.22f + layer * 0.14f, Offset(sx, sy))
        }
    }
    // the planet's edge
    drawOval(Brush.verticalGradient(listOf(tint.copy(alpha = 0.0f), tint.copy(alpha = 0.28f)), 124f, 150f), Offset(-30f, 132f), Size(160f, 40f))
}

/** A player's ship: a sleek arrowhead with wings, a canopy and a flickering engine. */
fun DrawScope.drawShip(s: ShipView, smooth: Smooth, key: String, col: Color, now: Long, dt: Float, mine: Boolean) {
    if (!s.alive) return
    val x = smooth.follow("$key-x", s.x, dt, 28f)
    val y = smooth.follow("$key-y", s.y, dt, 28f)
    // blinking while invulnerable
    if (s.invulnerable && (now / 90L) % 2L == 0L) return
    val flame = 2.2f + sin(now / 40f) * 0.8f + (if (s.rapid) 1.2f else 0f)
    val fl = Path().apply { moveTo(x - 1.4f, y + 3f); lineTo(x, y + 3f + flame * 1.6f); lineTo(x + 1.4f, y + 3f); close() }
    drawPath(fl, Color(0xFFFFB020).copy(alpha = 0.95f))
    val fl2 = Path().apply { moveTo(x - 0.7f, y + 3f); lineTo(x, y + 3f + flame); lineTo(x + 0.7f, y + 3f); close() }
    drawPath(fl2, Color.White)
    // glow
    drawCircle(Brush.radialGradient(listOf(col.copy(alpha = 0.38f), Color.Transparent), Offset(x, y), 9f), 9f, Offset(x, y))
    val body = Path().apply {
        moveTo(x, y - 5f)
        lineTo(x + 1.6f, y - 1f); lineTo(x + 5.2f, y + 2.6f); lineTo(x + 5.2f, y + 4f); lineTo(x + 2.2f, y + 3f)
        lineTo(x + 1.2f, y + 3.6f); lineTo(x - 1.2f, y + 3.6f); lineTo(x - 2.2f, y + 3f)
        lineTo(x - 5.2f, y + 4f); lineTo(x - 5.2f, y + 2.6f); lineTo(x - 1.6f, y - 1f); close()
    }
    drawPath(body, Brush.verticalGradient(listOf(Color.White, col, col.copy(alpha = 0.7f)), y - 5f, y + 4f))
    drawPath(body, col.copy(alpha = 0.9f), style = Stroke(0.45f))
    drawOval(Color(0xFF0B1B33), Offset(x - 0.9f, y - 2.4f), Size(1.8f, 3.4f))
    drawOval(Color(0xFF7DD3FC), Offset(x - 0.5f, y - 2.0f), Size(0.9f, 1.6f))
    if (s.invulnerable) drawCircle(Color.White.copy(alpha = 0.25f + 0.2f * sin(now / 70f)), 8f, Offset(x, y), style = Stroke(0.7f))
    if (mine) {
        // a small marker so you can always find your own ship
        val tri = Path().apply { moveTo(x, y + 9.5f); lineTo(x - 1.6f, y + 12f); lineTo(x + 1.6f, y + 12f); close() }
        drawPath(tri, col.copy(alpha = 0.8f))
    }
}

fun DrawScope.drawBullets(list: List<BulletView>, frame: ArcadeFrame) {
    for (b in list) {
        val age = min(frame.ageSec, 0.12f)
        val x = b.x + b.vx * age
        val y = b.y + b.vy * age
        val col = if (b.owner == 1) (if (frame.iAmHost) HostShip else GuestShip) else (if (frame.iAmHost) GuestShip else HostShip)
        val tail = if (b.vy < 0) 4.2f else -4.2f
        drawLine(col.copy(alpha = 0.25f), Offset(x - b.vx * 0.03f, y + tail * 1.6f), Offset(x, y), 1.8f, cap = androidx.compose.ui.graphics.StrokeCap.Round)
        drawLine(col, Offset(x - b.vx * 0.02f, y + tail), Offset(x, y), 0.95f, cap = androidx.compose.ui.graphics.StrokeCap.Round)
        drawCircle(Color.White, 0.55f, Offset(x, y))
    }
}

/** Sounds and sparks for the things both games share. */
fun arcadeBurst(kind: String, x: Float, y: Float, fx: Fx, now: Long, mineCol: Color): Boolean {
    when (kind) {
        "pl" -> { fx.explode(x, y, now, Color(0xFFFF8A3D), 26, 1.6f); fx.shake(now, 1.6f, 380); Sfx.play(Sound.EXPLODE) }
        "ufo" -> { fx.explode(x, y, now, Color(0xFFF472B6), 22, 1.3f); Sfx.play(Sound.WIN) }
        "pw" -> { fx.glow(x, y, now, Color(0xFFFDE047)); Sfx.play(Sound.CHIME) }
        "inv" -> { fx.explode(x, y, now, Color(0xFFF87171), 34, 2f); fx.shake(now, 2.4f, 700); Sfx.play(Sound.EXPLODE) }
        else -> return false
    }
    return true
}

