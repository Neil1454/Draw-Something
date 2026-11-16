package com.neil1454.drawsomething.core

import org.json.JSONArray
import org.json.JSONObject
import kotlin.math.abs
import kotlin.math.ceil
import kotlin.math.cos
import kotlin.math.hypot
import kotlin.math.max
import kotlin.math.min
import kotlin.math.sin
import kotlin.random.Random

// ====================================================================================
// Arkanoid for two. A wall of bricks, a bouncing ball, and a paddle each along the bottom. Keep the ball in play and smash
// the bricks: whoever last touched the ball gets the points. Bricks drop power-ups: a wider paddle, lasers, a split into
// three balls, slow motion, a fireball that burns straight through, and an extra life for the team. The lives are shared:
// lose the last ball and the team loses one. Slide a finger left and right to move your paddle.
// ====================================================================================

object ArkanoidRules {
    const val COLS = 10
    const val ROWS = 11
    const val BW = 10f
    const val BH = 4.6f
    const val TOP = 12f
    const val PADDLE_Y = 130f
    const val PADDLE_W = 16f
    const val PADDLE_WIDE = 27f
    const val PADDLE_NARROW = 9.5f
    /** The ball gets this much faster after every paddle hit in one rally, up to the cap. */
    const val RALLY_STEP = 0.015f
    const val RALLY_CAP = 0.5f
    const val BALL_R = 1.6f
    const val PADDLE_SPEED = 160f
    const val DROP_SPEED = 30f
    const val TIME_CAP_MS = 15 * 60_000L
    const val MAX_BALLS = 6

    fun ballSpeed(level: Int): Float = min(104f, 62f + 6f * (level - 1))
    fun idx(col: Int, row: Int) = row * COLS + col

    /** The brick layout for a level (1 upwards): '.' empty, '1' '2' '3' = hits to break, '9' unbreakable gold, 'E' explosive. */
    fun layout(level: Int): String {
        val a = CharArray(COLS * ROWS) { '.' }
        fun put(c: Int, r: Int, ch: Char) { if (c in 0 until COLS && r in 0 until ROWS) a[idx(c, r)] = ch }
        when ((level - 1) % 5) {
            0 -> for (r in 1..5) for (c in 0 until COLS) put(c, r, '1')
            1 -> for (r in 1..6) for (c in 0 until COLS) if ((c + r) % 2 == 0) put(c, r, if (r == 1) '2' else '1')
            2 -> for (r in 1..6) for (c in (5 - r)..(4 + r)) put(c, r, if ((c + r) % 3 == 0) '2' else '1')
            3 -> {
                for (r in 1..6) for (c in 0 until COLS) put(c, r, if (r == 2) '2' else '1')
                for (r in 3..6) { put(0, r, '9'); put(COLS - 1, r, '9') }
                put(4, 3, 'E'); put(5, 3, 'E')
            }
            else -> {
                for (r in 1..8) for (c in 0 until COLS) put(c, r, if (r <= 2) '3' else if (r % 3 == 0) '2' else '1')
                for ((c, r) in listOf(2 to 5, 7 to 5, 3 to 6, 6 to 6)) put(c, r, '9')
                for ((c, r) in listOf(1 to 4, 8 to 4, 4 to 7, 5 to 7)) put(c, r, 'E')
            }
        }
        if (level > 5) for (r in 1..2) for (c in 0 until COLS) if (a[idx(c, r)] == '1') a[idx(c, r)] = '2'
        return String(a)
    }

    /** How many bricks still have to be broken (gold ones never do). */
    fun remaining(bricks: CharArray): Int = bricks.count { it != '.' && it != '9' }
}

class BallView(val x: Float, val y: Float, val vx: Float, val vy: Float, val owner: Int, val fire: Boolean)
class DropView(val id: Int, val x: Float, val y: Float, val kind: Char)

data class ArkanoidState(
    val c: ArcadeCommon = ArcadeCommon(),
    val bricks: String = "",
    val balls: List<BallView> = emptyList(),
    val drops: List<DropView> = emptyList(),
    val slow: Boolean = false,
    val fireball: Boolean = false,
    /** A bad power-up is making the ball faster. */
    val fast: Boolean = false,
    /** How much faster than the level's starting speed the ball is now (1.0 at the start of a rally). */
    val speed: Float = 1f,
    val remaining: Int = 0,
) {
    val phase get() = c.phase
    val myScore get() = c.mine.score
    val theirScore get() = c.theirs.score
    val result get() = c.result
}

class ArkanoidEngine(
    myName: String,
    theirName: String,
    isHost: Boolean,
    send: (GMsg) -> Unit,
    random: Random = Random.Default,
    clock: () -> Long = { System.currentTimeMillis() },
) : SyncEngine(GameKind.ARKANOID, myName, theirName, isHost, send, random, clock) {

    var state = ArkanoidState(ArcadeCommon(iAmHost = isHost))
        private set
    override val stateAny: Any get() = state
    override val phase get() = state.c.phase

    private val R = ArkanoidRules

    private class Ball(var x: Float, var y: Float, var vx: Float, var vy: Float, var owner: Int)
    private class Drop(val id: Int, var x: Float, var y: Float, val kind: Char)

    private val ships = arrayOf(Ship(0f, 0f), Ship(30f, R.PADDLE_Y), Ship(70f, R.PADDLE_Y))
    private val bricks = CharArray(R.COLS * R.ROWS) { '.' }
    private val balls = ArrayList<Ball>()
    private val drops = ArrayList<Drop>()
    private val lasers = ArrayList<Bullet>()
    private val bursts = ArrayList<Burst>()
    private var burstId = 0
    private var dropId = 0
    private var lives = Arcade.START_LIVES
    private var level = 1
    private var levels = 5
    private var stage = "READY"
    private var stageUntil = 0L
    private var t = 0L
    private var lastAt = 0L
    private var elapsed = 0L
    private var serveAt = -1L
    private var serveOwner = 1
    private var lastOwner = 1
    private var slowUntil = 0L
    private var fireUntil = 0L
    private var fastUntil = 0L
    private var rally = 0
    private var over = false
    private var why = ""

    /** My finger's x (0 to 100), and whether it is touching the screen (which fires lasers when you have them). */
    fun input(x: Float, fire: Boolean) {
        act(JSONObject().put("a", "in").put("x", Arcade.r1(x)).put("f", if (fire) 1 else 0))
    }

    private fun halfWidth(p: Int) = (if (t < ships[p].narrowUntil) R.PADDLE_NARROW else if (t < ships[p].tripleUntil) R.PADDLE_WIDE else R.PADDLE_W) / 2f

    private fun addScore(p: Int, pts: Int) { ships[p].score += if (t < ships[p].doubleUntil) pts * 2 else pts }

    override fun hostBegin(opts: JSONObject) {
        levels = opts.optInt("waves", 5).coerceIn(1, 9)
        lives = opts.optInt("lives", Arcade.START_LIVES).coerceIn(1, 5)
        ships[1].reset(30f, R.PADDLE_Y); ships[2].reset(70f, R.PADDLE_Y)
        level = 1; t = 0L; elapsed = 0L; over = false; why = ""; bursts.clear(); burstId = 0; dropId = 0
        lastOwner = 1; rally = 0
        startLevel()
        lastAt = clock()
    }

    private fun startLevel() {
        val l = R.layout(level)
        for (i in bricks.indices) bricks[i] = l[i]
        balls.clear(); drops.clear(); lasers.clear()
        serveAt = -1L; slowUntil = 0L; fireUntil = 0L; fastUntil = 0L; rally = 0
        serveOwner = if (level % 2 == 1) 1 else 2
        stage = "READY"; stageUntil = t + Arcade.READY_MS
    }

    override fun hostHandle(fromHost: Boolean, a: JSONObject) {
        if (state.c.phase != GPhase.PLAYING || over) return
        if (a.getString("a") != "in") return
        val s = ships[if (fromHost) 1 else 2]
        s.tx = a.optDouble("x", s.x.toDouble()).toFloat().coerceIn(3f, Arcade.W - 3f)
        s.firing = a.optInt("f", 0) == 1
    }

    override fun hostTick(now: Long) {
        if (over) return
        var dt = (now - lastAt).coerceIn(0L, 250L)
        lastAt = now
        if (dt == 0L) return
        while (dt > 0L) {
            val d = min(dt, Arcade.STEP_MS)
            dt -= d
            t += d
            step(d)
            if (over) break
        }
        publish()
    }

    private fun burst(x: Float, y: Float, kind: String) {
        bursts.add(Burst(++burstId, x, y, kind, t))
        while (bursts.size > 20) bursts.removeAt(0)
    }

    private fun step(dMs: Long) {
        val dt = dMs / 1000f
        for (p in 1..2) {
            val s = ships[p]
            val hw = halfWidth(p)
            val move = R.PADDLE_SPEED * dt
            s.x = (s.x + (s.tx - s.x).coerceIn(-move, move)).coerceIn(hw, Arcade.W - hw)
            s.y = R.PADDLE_Y
        }
        when (stage) {
            "READY" -> if (t >= stageUntil) stage = "PLAY"
            "WAVE" -> if (t >= stageUntil) { level++; startLevel() }
            else -> play(dMs)
        }
        bursts.removeAll { t - it.at > 900L }
    }

    private fun play(dMs: Long) {
        val dt = dMs / 1000f
        elapsed += dMs
        // lasers
        for (p in 1..2) {
            val s = ships[p]
            s.cooldown -= dt
            if (t < s.rapidUntil && s.firing && s.cooldown <= 0f && lasers.size < 10) {
                s.cooldown = 0.35f
                val hw = halfWidth(p)
                lasers.add(Bullet(s.x - hw + 2f, R.PADDLE_Y - 3f, 0f, -150f, p))
                lasers.add(Bullet(s.x + hw - 2f, R.PADDLE_Y - 3f, 0f, -150f, p))
            }
        }
        // serving a new ball
        if (balls.isEmpty()) {
            if (serveAt < 0L) serveAt = t + 900L
            else if (t >= serveAt) {
                val a = (random.nextFloat() - 0.5f) * 0.9f
                val sp = R.ballSpeed(level)
                balls.add(Ball(ships[serveOwner].x, R.PADDLE_Y - 3f, sp * sin(a), -sp * cos(a), serveOwner))
                serveAt = -1L
            }
        }
        moveLasers(dt)
        moveBalls(dt)
        moveDrops(dt)
        if (R.remaining(bricks) == 0) {
            for (p in 1..2) ships[p].score += 100 * level
            if (level >= levels) finish("victory")
            else { stage = "WAVE"; stageUntil = t + 2400L; balls.clear(); drops.clear(); lasers.clear() }
            return
        }
        if (elapsed > R.TIME_CAP_MS) finish("time")
    }

    private fun finish(reason: String) { over = true; why = reason; stage = "END" }

    private fun brickAt(x: Float, y: Float): Int {
        val col = (x / R.BW).toInt()
        val row = ((y - R.TOP) / R.BH).toInt()
        if (x < 0f || y < R.TOP || col !in 0 until R.COLS || row !in 0 until R.ROWS) return -1
        return if (bricks[R.idx(col, row)] == '.') -1 else R.idx(col, row)
    }

    private fun moveBalls(dt: Float) {
        val slow = t < slowUntil
        val fire = t < fireUntil
        val sp = min(150f, R.ballSpeed(level) * (1f + min(R.RALLY_CAP, rally * R.RALLY_STEP)) * (if (slow) 0.7f else 1f) * (if (t < fastUntil) 1.35f else 1f))
        var anyLost = false
        val it = balls.iterator()
        while (it.hasNext()) {
            val b = it.next()
            // keep the speed steady, and never let a ball run nearly flat (it would take forever)
            val cur = hypot(b.vx, b.vy).coerceAtLeast(0.01f)
            b.vx = b.vx / cur * sp; b.vy = b.vy / cur * sp
            if (abs(b.vy) < sp * 0.25f) { b.vy = (if (b.vy < 0f) -1f else 1f) * sp * 0.25f; val r = sqrtSafe(sp * sp - b.vy * b.vy); b.vx = (if (b.vx < 0f) -1f else 1f) * r }
            val n = max(1, ceil(sp * dt / 1.0f).toInt())
            var lost = false
            for (k in 0 until n) {
                val px = b.x; val py = b.y
                b.x += b.vx * dt / n
                b.y += b.vy * dt / n
                if (b.x < R.BALL_R) { b.x = R.BALL_R; b.vx = abs(b.vx) }
                if (b.x > Arcade.W - R.BALL_R) { b.x = Arcade.W - R.BALL_R; b.vx = -abs(b.vx) }
                if (b.y < R.BALL_R) { b.y = R.BALL_R; b.vy = abs(b.vy) }
                // the paddles
                if (b.vy > 0f) for (p in 1..2) {
                    val s = ships[p]
                    val hw = halfWidth(p)
                    if (b.y + R.BALL_R >= R.PADDLE_Y - 1.5f && py + R.BALL_R <= R.PADDLE_Y + 3f && abs(b.x - s.x) <= hw + R.BALL_R * 0.7f) {
                        val rel = ((b.x - s.x) / hw).coerceIn(-1f, 1f)
                        val ang = rel * 1.05f
                        b.vx = sp * sin(ang); b.vy = -sp * cos(ang)
                        b.y = R.PADDLE_Y - 1.5f - R.BALL_R
                        b.owner = p; lastOwner = p; rally++
                        burst(b.x, R.PADDLE_Y, "pd")
                        break
                    }
                }
                // the bricks: look at the centre and the leading edges
                val sx = if (b.vx >= 0f) 1f else -1f
                val sy = if (b.vy >= 0f) 1f else -1f
                val hitH = brickAt(b.x + sx * R.BALL_R, b.y)
                val hitV = brickAt(b.x, b.y + sy * R.BALL_R)
                val hitC = brickAt(b.x, b.y)
                val hit = if (hitC >= 0) hitC else if (hitV >= 0) hitV else hitH
                if (hit >= 0) {
                    val gold = bricks[hit] == '9'
                    val pierce = fire && !gold
                    if (!pierce) {
                        if (hit == hitH && hitV < 0 && hitC < 0) b.vx = -b.vx else b.vy = -b.vy
                        b.x = px; b.y = py
                    }
                    hitBrick(hit, b.owner, true)
                }
                if (b.y > Arcade.H + 3f) { lost = true; break }
            }
            if (lost) { lastOwner = b.owner; anyLost = true; it.remove() }
        }
        if (anyLost && balls.isEmpty() && !over && stage == "PLAY") {
            lives--; rally = 0
            burst(ships[lastOwner].x, R.PADDLE_Y, "pl")
            serveOwner = lastOwner
            serveAt = -1L
            if (lives <= 0) finish("lives")
        }
    }

    private fun sqrtSafe(v: Float): Float = kotlin.math.sqrt(max(0f, v))

    private fun moveLasers(dt: Float) {
        val it = lasers.iterator()
        while (it.hasNext()) {
            val b = it.next()
            b.y += b.vy * dt
            if (b.y < 0f) { it.remove(); continue }
            val hit = brickAt(b.x, b.y)
            if (hit >= 0) { hitBrick(hit, b.owner, false); it.remove() }
        }
    }

    private fun moveDrops(dt: Float) {
        val it = drops.iterator()
        while (it.hasNext()) {
            val d = it.next()
            d.y += R.DROP_SPEED * dt
            var caught = false
            for (p in 1..2) {
                val s = ships[p]
                if (d.y >= R.PADDLE_Y - 3f && d.y <= R.PADDLE_Y + 3f && abs(d.x - s.x) <= halfWidth(p) + 2f) {
                    power(d.kind, p); caught = true; break
                }
            }
            if (caught || d.y > Arcade.H + 4f) it.remove()
        }
    }

    private fun power(kind: Char, p: Int) {
        val s = ships[p]
        addScore(p, 25)
        when (kind) {
            'W' -> s.tripleUntil = t + Arcade.POWER_MS
            'L' -> s.rapidUntil = t + Arcade.POWER_MS
            'S' -> slowUntil = t + 8_000L
            'F' -> fireUntil = t + 7_000L
            'X' -> s.doubleUntil = t + 10_000L
            'N' -> { s.narrowUntil = t + 9_000L; s.tripleUntil = 0L }
            'Z' -> fastUntil = t + 8_000L
            'H' -> if (lives < Arcade.MAX_LIVES) lives++ else s.score += 100
            'M' -> {
                val src = ArrayList(balls)
                for (b in src) for (turn in listOf(0.55f, -0.55f)) {
                    if (balls.size >= R.MAX_BALLS) break
                    val c = cos(turn); val sn = sin(turn)
                    balls.add(Ball(b.x, b.y, b.vx * c - b.vy * sn, b.vx * sn + b.vy * c, b.owner))
                }
            }
        }
        burst(s.x, R.PADDLE_Y - 6f, "pw")
    }

    /** A ball or a laser hit brick [i]. */
    private fun hitBrick(i: Int, owner: Int, byBall: Boolean) {
        val col = i % R.COLS
        val row = i / R.COLS
        val cx = col * R.BW + R.BW / 2f
        val cy = R.TOP + row * R.BH + R.BH / 2f
        when (bricks[i]) {
            '9' -> burst(cx, cy, "gd")
            '2', '3' -> { bricks[i] = bricks[i] - 1; addScore(owner, 5); burst(cx, cy, "hp") }
            'E' -> explode(col, row, owner)
            '1' -> destroy(i, owner, true)
        }
    }

    private fun destroy(i: Int, owner: Int, mayDrop: Boolean) {
        val col = i % R.COLS
        val row = i / R.COLS
        bricks[i] = '.'
        addScore(owner, 10 + 2 * (R.ROWS - row))
        burst(col * R.BW + R.BW / 2f, R.TOP + row * R.BH + R.BH / 2f, "bk")
        if (mayDrop && random.nextInt(100) < 15) {
            // good ones most of the time, with a few that make life harder (N shrinks the catcher's paddle, Z speeds the ball up)
            val kinds = if (lives >= Arcade.MAX_LIVES) "WWMMLLSFFXXNZ" else "WWMMLLSFFHXXNZ"
            drops.add(Drop(++dropId, col * R.BW + R.BW / 2f, R.TOP + row * R.BH + R.BH, kinds[random.nextInt(kinds.length)]))
        }
    }

    private fun explode(col: Int, row: Int, owner: Int) {
        val cx = col * R.BW + R.BW / 2f
        val cy = R.TOP + row * R.BH + R.BH / 2f
        bricks[R.idx(col, row)] = '.'
        addScore(owner, 30)
        burst(cx, cy, "bm")
        for (dr in -1..1) for (dc in -1..1) {
            val c = col + dc; val r = row + dr
            if (c !in 0 until R.COLS || r !in 0 until R.ROWS || (dc == 0 && dr == 0)) continue
            val j = R.idx(c, r)
            when (bricks[j]) {
                'E' -> explode(c, r, owner)
                '.', '9' -> {}
                else -> destroy(j, owner, false)
            }
        }
    }

    // ------------------------------------------------------------------ the picture sent to the other phone

    override fun hostSnapshot(): JSONObject {
        ships[1].lives = lives; ships[2].lives = lives
        val bl = JSONArray()
        for (b in balls) bl.put(JSONArray().put(Arcade.r1(b.x)).put(Arcade.r1(b.y)).put(Arcade.r1(b.vx)).put(Arcade.r1(b.vy)).put(b.owner).put(if (t < fireUntil) 1 else 0))
        val dr = JSONArray()
        for (d in drops.take(20)) dr.put(JSONArray().put(d.id).put(Arcade.r1(d.x)).put(Arcade.r1(d.y)).put(d.kind.code))
        return JSONObject()
            .put("st", stage).put("w", level).put("W", levels)
            .put("br", String(bricks)).put("bl", bl).put("dr", dr)
            .put("b", ArcadeCodec.bullets(lasers))
            .put("s1", ArcadeCodec.ship(ships[1], t)).put("s2", ArcadeCodec.ship(ships[2], t))
            .put("ev", ArcadeCodec.bursts(bursts, t))
            .put("rd", max(0L, stageUntil - t).toInt()).put("tm", elapsed)
            .put("sl", t < slowUntil).put("fb", t < fireUntil).put("fz", t < fastUntil).put("ry", rally)
            .put("ov", over).put("why", why)
    }

    override fun adopt(s: JSONObject) {
        val br = s.optString("br", "")
        if (br.length != R.COLS * R.ROWS || br.any { it !in ".1239E" }) return
        val ov = s.optBoolean("ov", false)
        val a = ArcadeCodec.readShip(s.optJSONArray(if (isHost) "s1" else "s2"))
        val b = ArcadeCodec.readShip(s.optJSONArray(if (isHost) "s2" else "s1"))
        val result = if (!ov) 0 else if (a.score > b.score) 1 else if (a.score < b.score) 2 else 3
        val bl = ArrayList<BallView>()
        s.optJSONArray("bl")?.let { arr ->
            for (i in 0 until minOf(arr.length(), 8)) {
                val e = arr.optJSONArray(i) ?: continue
                if (e.length() < 6) continue
                bl += BallView(
                    e.optDouble(0).toFloat().coerceIn(-5f, 105f), e.optDouble(1).toFloat().coerceIn(-10f, 150f),
                    e.optDouble(2).toFloat().coerceIn(-300f, 300f), e.optDouble(3).toFloat().coerceIn(-300f, 300f),
                    e.optInt(4, 1).coerceIn(1, 2), e.optInt(5, 0) == 1,
                )
            }
        }
        val dv = ArrayList<DropView>()
        s.optJSONArray("dr")?.let { arr ->
            for (i in 0 until minOf(arr.length(), 24)) {
                val e = arr.optJSONArray(i) ?: continue
                val k = e.optInt(3, 0).toChar()
                if (k !in "WMLSFHXNZ") continue
                dv += DropView(e.optInt(0), e.optDouble(1).toFloat().coerceIn(0f, 100f), e.optDouble(2).toFloat().coerceIn(0f, 150f), k)
            }
        }
        state = ArkanoidState(
            c = ArcadeCommon(iAmHost = isHost).copy(
                phase = if (ov) GPhase.OVER else GPhase.PLAYING, stage = s.optString("st", "PLAY").take(6),
                level = s.optInt("w", 1).coerceIn(1, 9), levels = s.optInt("W", 5).coerceIn(1, 9), mine = a, theirs = b,
                bullets = ArcadeCodec.readBullets(s.optJSONArray("b")), bursts = ArcadeCodec.readBursts(s.optJSONArray("ev")),
                readyMs = s.optInt("rd", 0).coerceIn(0, 10_000), elapsedMs = s.optLong("tm", 0L).coerceIn(0L, 10_000_000L),
                receivedAt = clock(), result = result, why = s.optString("why", "").take(8),
            ),
            bricks = br, balls = bl, drops = dv, slow = s.optBoolean("sl", false), fireball = s.optBoolean("fb", false),
            fast = s.optBoolean("fz", false), speed = 1f + min(R.RALLY_CAP, s.optInt("ry", 0).coerceIn(0, 200) * R.RALLY_STEP),
            remaining = br.count { it != '.' && it != '9' },
        )
    }

    override fun reset() {
        state = ArkanoidState(ArcadeCommon(iAmHost = isHost))
    }
}

// ====================================================================== the computer player

class ArkanoidBrain(private val e: ArkanoidEngine, skill: Skill, random: Random) : ArcadeBrainBase(skill, random) {
    private var wobble = 0f
    private var wobbleAt = 0L

    override fun think(now: Long) {
        val s = e.state
        if (s.c.phase != GPhase.PLAYING) return
        val me = s.c.mine
        val partner = s.c.theirs
        if (now > wobbleAt) { wobble = random.nextFloat() * 2f - 1f; wobbleAt = now + random.nextLong(300, 900) }
        val py = ArkanoidRules.PADDLE_Y
        // where will each falling ball cross the paddle line? (bouncing off the side walls on the way)
        fun landing(b: BallView): Float {
            val tt = (py - b.y) / b.vy
            var x = b.x + b.vx * tt
            val w = Arcade.W
            x = ((x % (2 * w)) + 2 * w) % (2 * w)
            return if (x > w) 2 * w - x else x
        }
        val falling = s.balls.filter { it.vy > 0f && it.y < py + 2f }
        // each ball is the job of whichever paddle is nearer to where it will land
        val mine = falling.filter { abs(landing(it) - me.x) <= abs(landing(it) - partner.x) + 1f }
        val ball = (if (mine.isNotEmpty()) mine else falling).maxByOrNull { it.y }
        var target = me.x
        if (ball != null) {
            target = landing(ball)
            // catch it off-centre so it comes back at an angle, steering towards the middle of the bricks
            val aim = (50f - target) * 0.08f
            target += aim + wobble * (if (skill == Skill.EASY) 7f else if (skill == Skill.NORMAL) 3f else 1f)
        } else {
            val drop = s.drops.filter { it.y > 60f && it.kind != 'N' && it.kind != 'Z' }.minByOrNull { abs(it.x - me.x) }
            target = drop?.x ?: if (s.balls.isEmpty()) me.x else 50f
        }
        // power-ups are worth a detour when no ball is about to arrive
        if (ball == null || ball.y < 70f) s.drops.filter { it.y > 40f && it.kind != 'N' && it.kind != 'Z' }.minByOrNull { abs(it.x - me.x) }?.let { target = it.x }
        e.input(target.coerceIn(5f, 95f), true)
    }
}
