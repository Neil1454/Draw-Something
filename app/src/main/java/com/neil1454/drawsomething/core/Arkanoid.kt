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
// the bricks: whoever last touched the ball gets the points. The classic power-up capsules fall from broken bricks:
//   E expand · L laser · C catch (sticky paddle) · D disruption (three balls) · S slow · B break (an exit opens: reach it to skip the round)
//   P player (extra life) · plus F fireball, X double points, and two bad ones: N shrink and Z fast ball.
// Wandering enemies drift down and bounce the ball about; shoot or hit them for points. The last round has a boss (DOH) that
// drops bombs on you. The ball speeds up the longer a rally goes on. The lives are shared: lose the last ball and the team loses one.
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
    const val BOSS_HP = 16
    const val BOSS_Y = 14f

    /** The score for breaking a brick: the classic colour values 50 to 120 (by row), silver 50 and steel 80 times the round. */
    fun brickScore(armour: Int, row: Int, level: Int) = when (armour) { 1 -> 50 * level; 2 -> 80 * level; else -> 50 + 10 * (row % 8) }

    fun ballSpeed(level: Int): Float = min(104f, 62f + 6f * (level - 1))
    /** Paddle size choices (Normal, Small, Tiny) and ball pace choices (Normal, Fast, Frantic). */
    val BAT_SCALE = floatArrayOf(1f, 0.72f, 0.52f)
    val PACE_MUL = floatArrayOf(1f, 1.2f, 1.42f)
    /** How long between the wall creeping down a row (when that option is on), by pace. */
    val WALL_MS = longArrayOf(30_000L, 24_000L, 18_000L)
    fun idx(col: Int, row: Int) = row * COLS + col

    /** The brick layout for a level (1 upwards): '.' empty, '1' '2' '3' = hits to break, '9' unbreakable gold, 'E' explosive. */
    fun layout(level: Int): String {
        val a = CharArray(COLS * ROWS) { '.' }
        fun put(c: Int, r: Int, ch: Char) { if (c in 0 until COLS && r in 0 until ROWS) a[idx(c, r)] = ch }
        when ((level - 1) % 9) {
            0 -> for (r in 1..5) for (c in 0 until COLS) put(c, r, '1')
            1 -> for (r in 1..6) for (c in 0 until COLS) if ((c + r) % 2 == 0) put(c, r, if (r == 1) '2' else '1')
            2 -> for (r in 1..6) for (c in (5 - r)..(4 + r)) put(c, r, if ((c + r) % 3 == 0) '2' else '1')
            3 -> {
                for (r in 1..6) for (c in 0 until COLS) put(c, r, if (r == 2) '2' else '1')
                for (r in 3..6) { put(0, r, '9'); put(COLS - 1, r, '9') }
                put(4, 3, 'E'); put(5, 3, 'E')
            }
            5 -> {
                // a diamond with a mystery brick at its heart
                for (r in 1..8) { val half = if (r <= 4) r else 9 - r; for (c in (5 - half)..(4 + half)) put(c, r, if (c == 5 - half || c == 4 + half) '2' else '1') }
                put(4, 4, 'Q'); put(5, 4, 'Q'); put(4, 5, 'Q'); put(5, 5, 'Q')
            }
            6 -> {
                // stripes between gold pillars, with mystery bricks along the top
                for (r in 1..7) for (c in 0 until COLS) if (c % 2 == 0) put(c, r, if (r % 3 == 0) '2' else '1')
                for (r in 3..7) { put(2, r, '9'); put(7, r, '9') }
                for (c in 0 until COLS step 3) put(c, 1, 'Q')
                put(4, 8, 'E'); put(6, 8, 'E')
            }
            7 -> {
                // a space invader made of bricks
                val rows = listOf("..1....1..", "...1..1...", "..111111..", ".11Q11Q11.", "1111111111", "1.111111.1", "1.1....1.1", "...11.11..")
                for ((r, line) in rows.withIndex()) for ((c, ch) in line.withIndex()) if (ch != '.') put(c, r + 1, if (ch == '1' && (r == 4)) '2' else ch)
                put(0, 5, '9'); put(9, 5, '9')
            }
            8 -> {
                // a fortress: steel walls, silver inside, explosive towers and a mystery core
                for (r in 1..8) for (c in 0 until COLS) put(c, r, if (r == 1 || r == 8 || c == 0 || c == COLS - 1) '3' else '2')
                for (r in 3..6) for (c in 3..6) put(c, r, '1')
                put(4, 4, 'Q'); put(5, 4, 'Q'); put(4, 5, 'Q'); put(5, 5, 'Q')
                put(1, 2, 'E'); put(8, 2, 'E'); put(1, 7, 'E'); put(8, 7, 'E')
                for (c in 2..7 step 5) put(c, 8, '9')
            }
            else -> {
                for (r in 1..8) for (c in 0 until COLS) put(c, r, if (r <= 2) '3' else if (r % 3 == 0) '2' else '1')
                for ((c, r) in listOf(2 to 5, 7 to 5, 3 to 6, 6 to 6)) put(c, r, '9')
                for ((c, r) in listOf(1 to 4, 8 to 4, 4 to 7, 5 to 7)) put(c, r, 'E')
            }
        }
        if (level > 9) for (r in 1..2) for (c in 0 until COLS) if (a[idx(c, r)] == '1') a[idx(c, r)] = '2'
        return String(a)
    }

    /** How many bricks still have to be broken (gold ones never do). */
    fun remaining(bricks: CharArray): Int = bricks.count { it != '.' && it != '9' }
}

class BallView(val x: Float, val y: Float, val vx: Float, val vy: Float, val owner: Int, val fire: Boolean, val stuck: Boolean = false)
class EnemyView(val id: Int, val x: Float, val y: Float, val kind: Int)
class BossView(val x: Float, val hp: Int, val max: Int)
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
    /** The paddle size option (1 normal, smaller is harder), the shield under the paddles, and whether my paddle / their paddle has reversed controls. */
    val bat: Float = 1f,
    val shield: Boolean = false,
    val myReversed: Boolean = false,
    val theirReversed: Boolean = false,
    /** Seconds until the wall creeps down (the Falling wall option), or -1 when that is off. */
    val wallIn: Int = -1,
    /** The exit (from a Break capsule) is open, the wandering enemies, and the boss and its bombs on the last round. */
    val exitOpen: Boolean = false,
    val enemies: List<EnemyView> = emptyList(),
    val boss: BossView? = null,
    val bombs: List<BulletView> = emptyList(),
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

    private class Ball(var x: Float, var y: Float, var vx: Float, var vy: Float, var owner: Int) {
        var stuck = 0
        var stuckOff = 0f
        var stuckAt = 0L
    }
    private class Enemy(val id: Int, var x: Float, var y: Float, val x0: Float, val kind: Int, val phase: Float)
    private class Drop(val id: Int, var x: Float, var y: Float, val kind: Char)

    private val ships = arrayOf(Ship(0f, 0f), Ship(30f, R.PADDLE_Y), Ship(70f, R.PADDLE_Y))
    private val bricks = CharArray(R.COLS * R.ROWS) { '.' }
    private val balls = ArrayList<Ball>()
    private val drops = ArrayList<Drop>()
    private val lasers = ArrayList<Bullet>()
    private val armour = IntArray(R.COLS * R.ROWS)
    private val enemies = ArrayList<Enemy>()
    private var enemyId = 0
    private var nextEnemy = 0L
    private var breakUntil = 0L
    private var bossHp = 0
    private var bossOn = false
    private var bossX = 50f
    private var nextBomb = 0L
    private val bombs = ArrayList<Bullet>()
    private val wasFiring = BooleanArray(3)
    private val tapped = BooleanArray(3)
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
    private var batScale = 1f
    private var pace = 0
    private var wall = false
    private var nextShift = 0L
    private var shield = false
    private val rawX = floatArrayOf(0f, 30f, 70f)
    private val reverseUntil = LongArray(3)
    private var over = false
    private var why = ""

    /** My finger's x (0 to 100), and whether it is touching the screen (which fires lasers when you have them). */
    fun input(x: Float, fire: Boolean) {
        act(JSONObject().put("a", "in").put("x", Arcade.r1(x)).put("f", if (fire) 1 else 0))
    }

    private fun halfWidth(p: Int) = (if (t < ships[p].narrowUntil) R.PADDLE_NARROW else if (t < ships[p].tripleUntil) R.PADDLE_WIDE else R.PADDLE_W) * batScale / 2f

    private fun addScore(p: Int, pts: Int) { ships[p].score += if (t < ships[p].doubleUntil) pts * 2 else pts }

    override fun hostBegin(opts: JSONObject) {
        levels = opts.optInt("waves", 5).coerceIn(1, 9)
        lives = opts.optInt("lives", Arcade.START_LIVES).coerceIn(1, 5)
        batScale = R.BAT_SCALE[opts.optInt("bat", 0).coerceIn(0, 2)]
        pace = opts.optInt("pace", 0).coerceIn(0, 2)
        wall = opts.optBoolean("wall", false)
        rawX[1] = 30f; rawX[2] = 70f; reverseUntil.fill(0L); shield = false
        ships[1].reset(30f, R.PADDLE_Y); ships[2].reset(70f, R.PADDLE_Y)
        level = opts.optInt("from", 1).coerceIn(1, levels); t = 0L; elapsed = 0L; over = false; why = ""; bursts.clear(); burstId = 0; dropId = 0
        lastOwner = 1; rally = 0; enemyId = 0
        startLevel()
        lastAt = clock()
    }

    private fun startLevel() {
        val l = R.layout(level)
        bossOn = levels >= 3 && level == levels
        for (i in bricks.indices) {
            bricks[i] = if (bossOn) '.' else l[i]
            armour[i] = if (bricks[i] == '2') 1 else if (bricks[i] == '3') 2 else 0
        }
        // the last round is the boss: a couple of gold bumpers and DOH himself
        if (bossOn) { bricks[R.idx(0, 6)] = '9'; bricks[R.idx(R.COLS - 1, 6)] = '9'; bossHp = R.BOSS_HP + 2 * (levels - 3); bossX = 50f; nextBomb = t + Arcade.READY_MS + 3000L }
        balls.clear(); drops.clear(); lasers.clear(); enemies.clear(); bombs.clear()
        nextEnemy = t + Arcade.READY_MS + 9000L; breakUntil = 0L
        serveAt = -1L; slowUntil = 0L; fireUntil = 0L; fastUntil = 0L; rally = 0; shield = false; reverseUntil.fill(0L)
        nextShift = t + Arcade.READY_MS + R.WALL_MS[pace]
        serveOwner = if (level % 2 == 1) 1 else 2
        stage = "READY"; stageUntil = t + Arcade.READY_MS
    }

    override fun hostHandle(fromHost: Boolean, a: JSONObject) {
        if (state.c.phase != GPhase.PLAYING || over) return
        if (a.getString("a") != "in") return
        val s = ships[if (fromHost) 1 else 2]
        val p = if (fromHost) 1 else 2
        rawX[p] = a.optDouble("x", s.x.toDouble()).toFloat().coerceIn(3f, Arcade.W - 3f)
        s.tx = if (t < reverseUntil[p]) Arcade.W - rawX[p] else rawX[p]
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
            s.tx = if (t < reverseUntil[p]) Arcade.W - rawX[p] else rawX[p]
            s.x = (s.x + (s.tx - s.x).coerceIn(-move, move)).coerceIn(hw, Arcade.W - hw)
            s.y = R.PADDLE_Y
            tapped[p] = s.firing && !wasFiring[p]
            wasFiring[p] = s.firing
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
                val sp = R.ballSpeed(level) * R.PACE_MUL[pace]
                balls.add(Ball(ships[serveOwner].x, R.PADDLE_Y - 3f, sp * sin(a), -sp * cos(a), serveOwner))
                serveAt = -1L
            }
        }
        if (wall && !bossOn && t >= nextShift) { nextShift = t + R.WALL_MS[pace]; shiftWall(); if (over) return }
        moveLasers(dt)
        moveBalls(dt)
        moveDrops(dt)
        moveEnemies(dt)
        if (bossOn) moveBoss(dt)
        // the exit from a Break capsule: slide a paddle off the right edge to skip the round
        if (t < breakUntil) for (p in 1..2) if (ships[p].x >= Arcade.W - halfWidth(p) - 0.6f) {
            addScore(p, 500 * level)
            for (q in 1..2) ships[q].score += 100 * level
            breakUntil = 0L
            burst(Arcade.W - 3f, R.PADDLE_Y, "wp")
            if (level >= levels) finish("victory") else { stage = "WAVE"; stageUntil = t + 2400L; balls.clear(); drops.clear(); lasers.clear(); enemies.clear(); bombs.clear() }
            return
        }
        if (R.remaining(bricks) == 0 && !(bossOn && bossHp > 0)) {
            for (p in 1..2) ships[p].score += 100 * level
            if (level >= levels) finish("victory")
            else { stage = "WAVE"; stageUntil = t + 2400L; balls.clear(); drops.clear(); lasers.clear(); enemies.clear(); bombs.clear() }
            return
        }
        if (elapsed > R.TIME_CAP_MS) finish("time")
    }

    /** The wall creeps down one row. Anything that reaches the bottom row and gets pushed off costs the team a life. */
    private fun shiftWall() {
        var hit = false
        for (c in 0 until R.COLS) { val i = R.idx(c, R.ROWS - 1); if (bricks[i] != '.') { if (bricks[i] != '9') hit = true; burst(c * R.BW + R.BW / 2f, R.TOP + (R.ROWS - 1) * R.BH, "bm") } }
        for (r in R.ROWS - 1 downTo 1) for (c in 0 until R.COLS) { bricks[R.idx(c, r)] = bricks[R.idx(c, r - 1)]; armour[R.idx(c, r)] = armour[R.idx(c, r - 1)] }
        for (c in 0 until R.COLS) { bricks[R.idx(c, 0)] = '.'; armour[R.idx(c, 0)] = 0 }
        burst(50f, R.TOP, "bm")
        if (hit) { lives--; burst(ships[1].x, R.PADDLE_Y, "pl"); if (lives <= 0) finish("lives") }
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
        val sp = min(170f, R.ballSpeed(level) * R.PACE_MUL[pace] * (1f + min(R.RALLY_CAP, rally * R.RALLY_STEP * (1f + 0.4f * pace))) * (if (slow) 0.7f else 1f) * (if (t < fastUntil) 1.35f else 1f))
        var anyLost = false
        val it = balls.iterator()
        while (it.hasNext()) {
            val b = it.next()
            // a ball stuck to a sticky paddle rides along, and is let go when its player taps (or after a few seconds)
            if (b.stuck != 0) {
                val sp0 = ships[b.stuck]
                val hw0 = halfWidth(b.stuck)
                b.x = (sp0.x + b.stuckOff).coerceIn(R.BALL_R, Arcade.W - R.BALL_R); b.y = R.PADDLE_Y - 1.5f - R.BALL_R
                if (tapped[b.stuck] || t - b.stuckAt > 4000L || t >= sp0.catchUntil) {
                    val rel = (b.stuckOff / hw0).coerceIn(-1f, 1f)
                    val ang = rel * 1.05f
                    b.vx = sp * sin(ang); b.vy = -sp * cos(ang); b.owner = b.stuck; b.stuck = 0
                    burst(b.x, R.PADDLE_Y, "pd")
                }
                continue
            }
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
                        if (t < s.catchUntil) {
                            // a sticky paddle holds on to the ball
                            b.stuck = p; b.stuckOff = (b.x - s.x).coerceIn(-hw, hw); b.stuckAt = t; b.owner = p; lastOwner = p
                            burst(b.x, R.PADDLE_Y, "ct")
                            break
                        }
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
                // wandering enemies knock the ball about
                for (en in enemies) {
                    val dx = b.x - en.x; val dy = b.y - en.y
                    if (dx * dx + dy * dy < 14f) {
                        val d = hypot(dx, dy).coerceAtLeast(0.1f)
                        b.vx = dx / d * sp; b.vy = dy / d * sp
                        killEnemy(en, b.owner)
                        break
                    }
                }
                enemies.removeAll { it.y < -50f }
                // the boss
                if (bossOn && bossHp > 0 && abs(b.x - bossX) < 10.5f + R.BALL_R && b.y - R.BALL_R < R.BOSS_Y + 7f && b.y + R.BALL_R > R.BOSS_Y - 7f) {
                    if (py - R.BALL_R >= R.BOSS_Y + 6f) b.vy = abs(b.vy) else if (py + R.BALL_R <= R.BOSS_Y - 6f) b.vy = -abs(b.vy) else b.vx = -b.vx
                    b.x = px; b.y = py
                    hitBoss(b.owner, 1)
                }
                if (shield && b.vy > 0f && b.y + R.BALL_R >= Arcade.H - 3f) {
                    shield = false; b.vy = -abs(b.vy); b.y = Arcade.H - 3f - R.BALL_R
                    burst(b.x, Arcade.H - 3f, "pw")
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
            val en = enemies.firstOrNull { abs(it.x - b.x) < 3f && abs(it.y - b.y) < 3.2f }
            if (en != null) { killEnemy(en, b.owner); it.remove(); continue }
            if (bossOn && bossHp > 0 && abs(b.x - bossX) < 10.5f && b.y < R.BOSS_Y + 7f) { hitBoss(b.owner, 1); it.remove(); continue }
            val hit = brickAt(b.x, b.y)
            if (hit >= 0) { hitBrick(hit, b.owner, false); it.remove() }
        }
    }

    // ------------------------------------------------------------------ enemies and the boss

    private fun killEnemy(en: Enemy, owner: Int) {
        if (!enemies.remove(en)) return
        addScore(owner, 100)
        burst(en.x, en.y, "en")
        if (random.nextInt(100) < 30) dropCapsule(en.x, en.y)
    }

    private fun moveEnemies(dt: Float) {
        if (!bossOn && t >= nextEnemy && enemies.size < 2) {
            enemies.add(Enemy(++enemyId, 12f + random.nextFloat() * 76f, 5f, 12f + random.nextFloat() * 76f, random.nextInt(3), random.nextFloat() * 6f))
            nextEnemy = t + random.nextLong(11_000, 18_000)
        }
        val it = enemies.iterator()
        while (it.hasNext()) {
            val e = it.next()
            e.y += (8f + level * 1.2f) * dt
            e.x = (e.x0 + sin(t / 700f + e.phase) * 14f).coerceIn(4f, Arcade.W - 4f)
            var gone = e.y > R.PADDLE_Y + 4f
            for (p in 1..2) if (!gone && e.y > R.PADDLE_Y - 3f && abs(e.x - ships[p].x) < halfWidth(p) + 2f) { burst(e.x, e.y, "en"); gone = true }
            if (gone) it.remove()
        }
    }

    private fun hitBoss(owner: Int, dmg: Int) {
        bossHp -= dmg
        addScore(owner, 200)
        burst(bossX, R.BOSS_Y + 6f, "bh")
        if (bossHp <= 0) {
            bossOn = true
            bossHp = 0
            addScore(owner, 2500)
            burst(bossX, R.BOSS_Y, "bm"); burst(bossX - 6f, R.BOSS_Y + 2f, "bm"); burst(bossX + 6f, R.BOSS_Y - 2f, "bm")
            bombs.clear()
        }
    }

    private fun moveBoss(dt: Float) {
        if (bossHp <= 0) return
        bossX = 50f + sin(t / 1100f) * 30f
        if (t >= nextBomb) {
            bombs.add(Bullet(bossX + (random.nextFloat() - 0.5f) * 12f, R.BOSS_Y + 7f, 0f, 38f + level * 2f, 0))
            nextBomb = t + random.nextLong(2200, 3600) - (R.BOSS_HP - bossHp) * 40L
        }
        val it = bombs.iterator()
        while (it.hasNext()) {
            val b = it.next()
            b.y += b.vy * dt
            var hit = false
            for (p in 1..2) if (b.y >= R.PADDLE_Y - 2f && b.y <= R.PADDLE_Y + 3f && abs(b.x - ships[p].x) <= halfWidth(p) + 1f) { hit = true; break }
            if (hit) {
                lives--
                burst(b.x, R.PADDLE_Y, "pl")
                it.remove()
                if (lives <= 0) { finish("lives"); return }
            } else if (b.y > Arcade.H + 3f) it.remove()
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
        // the paddle powers (expand, laser, catch) replace each other, as in the arcade original
        fun clearPaddle() { s.tripleUntil = 0L; s.rapidUntil = 0L; s.catchUntil = 0L; s.narrowUntil = 0L }
        when (kind) {
            'E' -> { clearPaddle(); s.tripleUntil = t + Arcade.POWER_MS }
            'L' -> { clearPaddle(); s.rapidUntil = t + Arcade.POWER_MS }
            'C' -> { clearPaddle(); s.catchUntil = t + 20_000L }
            'N' -> { clearPaddle(); s.narrowUntil = t + 9_000L }
            'S' -> { slowUntil = t + 8_000L; fastUntil = 0L }
            'Z' -> { fastUntil = t + 8_000L; slowUntil = 0L }
            'F' -> fireUntil = t + 7_000L
            'X' -> s.doubleUntil = t + 10_000L
            'B' -> breakUntil = t + 12_000L
            'H' -> shield = true
            'R' -> reverseUntil[p] = t + 7_000L
            'P' -> if (lives < Arcade.MAX_LIVES) lives++ else s.score += 100
            'D' -> {
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
            'Q' -> destroy(i, owner, true)
            'E' -> explode(col, row, owner)
            '1' -> destroy(i, owner, true)
        }
    }

    private fun destroy(i: Int, owner: Int, mayDrop: Boolean) {
        val col = i % R.COLS
        val row = i / R.COLS
        val mystery = bricks[i] == 'Q'
        bricks[i] = '.'
        addScore(owner, R.brickScore(armour[i], row, level) + if (mystery) 100 else 0)
        burst(col * R.BW + R.BW / 2f, R.TOP + row * R.BH + R.BH / 2f, "bk")
        if (mystery || (mayDrop && random.nextInt(100) < 16)) dropCapsule(col * R.BW + R.BW / 2f, R.TOP + row * R.BH + R.BH)
    }

    /** A power-up capsule starts to fall. Mostly helpful, with a few that make life harder (N shrinks the catcher's paddle, Z speeds the ball up). */
    private fun dropCapsule(x: Float, y: Float) {
        val sb = StringBuilder("EEDDLLSSCCFXX")
        if (!bossOn) sb.append("BB")
        if (lives < Arcade.MAX_LIVES) sb.append("PP")
        sb.append("NZRH")
        drops.add(Drop(++dropId, x, y, sb[random.nextInt(sb.length)]))
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
        for (b in balls) bl.put(JSONArray().put(Arcade.r1(b.x)).put(Arcade.r1(b.y)).put(Arcade.r1(b.vx)).put(Arcade.r1(b.vy)).put(b.owner).put(if (t < fireUntil) 1 else 0).put(if (b.stuck != 0) 1 else 0))
        val en = JSONArray()
        for (e in enemies) en.put(JSONArray().put(e.id).put(Arcade.r1(e.x)).put(Arcade.r1(e.y)).put(e.kind))
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
            .put("ex", t < breakUntil).put("en", en)
            .put("bt", batScale.toDouble()).put("pc", pace).put("sd", shield)
            .put("rv", JSONArray().put(t < reverseUntil[1]).put(t < reverseUntil[2]))
            .put("wl", if (wall && !bossOn) ((nextShift - t).coerceAtLeast(0L) / 1000L).toInt() else -1)
            .put("bs", if (bossOn) JSONArray().put(Arcade.r1(bossX)).put(bossHp).put(R.BOSS_HP + 2 * (levels - 3)) else JSONArray())
            .put("bm", ArcadeCodec.bullets(bombs))
            .put("ov", over).put("why", why)
    }

    override fun adopt(s: JSONObject) {
        val br = s.optString("br", "")
        if (br.length != R.COLS * R.ROWS || br.any { it !in ".1239EQ" }) return
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
                    e.optInt(4, 1).coerceIn(1, 2), e.optInt(5, 0) == 1, e.optInt(6, 0) == 1,
                )
            }
        }
        val dv = ArrayList<DropView>()
        s.optJSONArray("dr")?.let { arr ->
            for (i in 0 until minOf(arr.length(), 24)) {
                val e = arr.optJSONArray(i) ?: continue
                val k = e.optInt(3, 0).toChar()
                if (k !in "EDLSCBPFXNZHR") continue
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
            fast = s.optBoolean("fz", false), exitOpen = s.optBoolean("ex", false),
            enemies = buildList {
                s.optJSONArray("en")?.let { arr -> for (i in 0 until minOf(arr.length(), 6)) { val e = arr.optJSONArray(i) ?: continue; add(EnemyView(e.optInt(0), e.optDouble(1).toFloat().coerceIn(0f, 100f), e.optDouble(2).toFloat().coerceIn(-10f, 150f), e.optInt(3, 0).coerceIn(0, 2))) } }
            },
            boss = s.optJSONArray("bs")?.takeIf { it.length() >= 3 }?.let { BossView(it.optDouble(0).toFloat().coerceIn(0f, 100f), it.optInt(1).coerceIn(0, 60), it.optInt(2, R.BOSS_HP).coerceIn(1, 60)) },
            bombs = ArcadeCodec.readBullets(s.optJSONArray("bm"), 8), speed = R.PACE_MUL[s.optInt("pc", 0).coerceIn(0, 2)] * (1f + min(R.RALLY_CAP, s.optInt("ry", 0).coerceIn(0, 200) * R.RALLY_STEP * (1f + 0.4f * s.optInt("pc", 0).coerceIn(0, 2)))),
            remaining = br.count { it != '.' && it != '9' },
            bat = s.optDouble("bt", 1.0).toFloat().coerceIn(0.4f, 1f), shield = s.optBoolean("sd", false),
            myReversed = s.optJSONArray("rv")?.optBoolean(if (isHost) 0 else 1, false) ?: false,
            theirReversed = s.optJSONArray("rv")?.optBoolean(if (isHost) 1 else 0, false) ?: false,
            wallIn = s.optInt("wl", -1).coerceIn(-1, 300),
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
    private var flip = false

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
            val drop = s.drops.filter { it.y > 60f && it.kind != 'N' && it.kind != 'Z' && it.kind != 'R' }.minByOrNull { abs(it.x - me.x) }
            target = drop?.x ?: if (s.balls.isEmpty()) me.x else 50f
        }
        // power-ups are worth a detour when no ball is about to arrive
        if (ball == null || ball.y < 70f) s.drops.filter { it.y > 40f && it.kind != 'N' && it.kind != 'Z' && it.kind != 'R' }.minByOrNull { abs(it.x - me.x) }?.let { target = it.x }
        // dodge the boss's bombs when no ball needs me
        val bomb = s.bombs.filter { it.y > 85f && abs(it.x - me.x) < 9f }.maxByOrNull { it.y }
        if (bomb != null && (ball == null || abs(landing(ball) - me.x) > 14f || ball.y < 80f)) target = if (bomb.x >= me.x) bomb.x - 13f else bomb.x + 13f
        // a sticky paddle is holding a ball: lift and tap to let it go
        val holding = s.balls.any { it.stuck }
        flip = !flip
        val aim = target.coerceIn(5f, 95f)
        e.input(if (s.myReversed) Arcade.W - aim else aim, !(holding && flip))
    }
}
