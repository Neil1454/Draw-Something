package com.neil1454.drawsomething.core

import org.json.JSONObject
import kotlin.math.abs
import kotlin.math.atan2
import kotlin.math.cos
import kotlin.math.hypot
import kotlin.math.max
import kotlin.math.min
import kotlin.math.sin
import kotlin.random.Random

// ====================================================================================
// Air Hockey and Pong, head to head, in real time. The host's phone runs the table and sends the picture ~30 times a second.
// Both players see themselves at the BOTTOM of their own screen (the guest's picture is turned the other way up).
//   • Air Hockey: drag your mallet around your half of the table and smash the puck into the other player's goal.
//   • Pong: slide your paddle along your end. The ball speeds up with every hit.
// The first to the target score wins (or whoever leads after eight minutes).
// Field numbers are the HOST's view: 100 wide and 140 tall, the host at the bottom (large y), the guest at the top.
// ====================================================================================

object HockeyRules {
    const val W = 100f
    const val H = 140f
    const val STEP_MS = 16L
    const val GOAL_HALF = 17f
    const val PUCK_R = 3.3f
    const val MALLET_R = 7f
    const val MALLET_SPEED = 260f
    const val PUCK_MAX = 210f
    const val PADDLE_HW = 11f
    const val PADDLE_SPEED = 135f
    const val PADDLE_Y = 6f
    const val BALL_R = 2.2f
    const val BALL_START = 70f
    const val BALL_CAP = 200f
    const val READY_MS = 2000L
    const val SERVE_MS = 1300L
    const val TIME_CAP_MS = 8 * 60_000L
    val TARGETS = listOf(5, 7, 10)

    /** Where a player's finger puts their mallet (y is limited to their own half). */
    fun clampMallet(x: Float, y: Float): Pair<Float, Float> =
        x.coerceIn(MALLET_R, W - MALLET_R) to y.coerceIn(H / 2f + MALLET_R * 0.3f, H - MALLET_R)
}

data class HockeyState(
    val phase: GPhase = GPhase.LOBBY,
    /** 0 Air Hockey, 1 Pong. */
    val mode: Int = 0,
    val target: Int = 5,
    /** All positions are in MY view: I am at the bottom. */
    val puckX: Float = 50f,
    val puckY: Float = 70f,
    val puckVx: Float = 0f,
    val puckVy: Float = 0f,
    val myX: Float = 50f,
    val myY: Float = 124f,
    val theirX: Float = 50f,
    val theirY: Float = 16f,
    val myScore: Int = 0,
    val theirScore: Int = 0,
    /** READY (a countdown before play), PLAY, SERVE (a moment after a goal) or END. */
    val stage: String = "READY",
    val readyMs: Int = 0,
    /** Count up whenever a goal / a hit / a wall bounce happens, so the screen can play each sound and effect once. */
    val goals: Int = 0,
    val hits: Int = 0,
    val walls: Int = 0,
    /** Who scored the last goal: 1 me, 2 them, 0 none yet. */
    val lastScorer: Int = 0,
    val elapsedMs: Long = 0L,
    val receivedAt: Long = 0L,
    val result: Int = 0,
    val iAmHost: Boolean = true,
) {
    val pong get() = mode == 1
}

class HockeyEngine(
    myName: String,
    theirName: String,
    isHost: Boolean,
    send: (GMsg) -> Unit,
    random: Random = Random.Default,
    clock: () -> Long = { System.currentTimeMillis() },
) : SyncEngine(GameKind.HOCKEY, myName, theirName, isHost, send, random, clock) {

    var state = HockeyState(iAmHost = isHost)
        private set
    override val stateAny: Any get() = state
    override val phase get() = state.phase

    /** My finger, in MY view of the table (I am at the bottom). */
    fun input(x: Float, y: Float) {
        act(JSONObject().put("a", "in").put("x", Arcade.r1(x)).put("y", Arcade.r1(y)))
    }

    private val R = HockeyRules

    // ---- the host's table (host view)
    private var mode = 0
    private var target = 5
    private var px = 50f; private var py = 70f; private var pvx = 0f; private var pvy = 0f
    private val mx = floatArrayOf(0f, 50f, 50f)
    private val my = floatArrayOf(0f, 124f, 16f)
    private val tx = floatArrayOf(0f, 50f, 50f)
    private val ty = floatArrayOf(0f, 124f, 16f)
    private val mvx = FloatArray(3)
    private val mvy = FloatArray(3)
    private val score = IntArray(3)
    private var stage = "READY"
    private var stageUntil = 0L
    private var serveTo = 1
    private var goals = 0
    private var hits = 0
    private var walls = 0
    private var lastScorer = 0
    private var ballSpeed = R.BALL_START
    private var stuckMs = 0L
    private var idleMs = 0L
    private var t = 0L
    private var lastAt = 0L
    private var elapsed = 0L
    private var over = false
    private var result = 0

    override fun hostBegin(opts: JSONObject) {
        mode = opts.optInt("mode", 0).coerceIn(0, 1)
        target = R.TARGETS.getOrElse(opts.optInt("target", 0)) { 5 }
        score.fill(0); goals = 0; hits = 0; walls = 0; lastScorer = 0; over = false; result = 0; t = 0L; elapsed = 0L
        mx[1] = 50f; my[1] = 124f; mx[2] = 50f; my[2] = 16f
        tx[1] = 50f; ty[1] = 124f; tx[2] = 50f; ty[2] = 16f
        serveTo = if (random.nextBoolean()) 1 else 2
        placeForServe()
        stage = "READY"; stageUntil = R.READY_MS
        lastAt = clock()
    }

    private fun placeForServe() {
        pvx = 0f; pvy = 0f; px = 50f
        ballSpeed = R.BALL_START
        py = if (mode == 0) (if (serveTo == 1) 100f else 40f) else 70f
    }

    override fun hostHandle(fromHost: Boolean, a: JSONObject) {
        if (state.phase != GPhase.PLAYING || over) return
        if (a.getString("a") != "in") return
        val p = if (fromHost) 1 else 2
        var x = a.optDouble("x", 50.0).toFloat().coerceIn(0f, R.W)
        var y = a.optDouble("y", 124.0).toFloat().coerceIn(0f, R.H)
        if (!fromHost) { x = R.W - x; y = R.H - y }
        if (mode == 0) {
            // my own half: for the host the bottom, for the guest the top (already turned into host numbers)
            if (p == 1) { val c = R.clampMallet(x, y); tx[1] = c.first; ty[1] = c.second }
            else { val c = R.clampMallet(R.W - x, R.H - y); tx[2] = R.W - c.first; ty[2] = R.H - c.second }
        } else {
            tx[p] = x.coerceIn(R.PADDLE_HW, R.W - R.PADDLE_HW)
        }
    }

    override fun hostTick(now: Long) {
        if (over) return
        var dt = (now - lastAt).coerceIn(0L, 200L)
        lastAt = now
        if (dt == 0L) return
        while (dt > 0L) {
            val d = min(dt, R.STEP_MS)
            dt -= d
            t += d
            step(d)
            if (over) break
        }
        publish()
    }

    private fun step(dMs: Long) {
        val dt = dMs / 1000f
        // the mallets and paddles glide towards the fingers, no faster than a hand can move
        for (p in 1..2) {
            val ox = mx[p]; val oy = my[p]
            if (mode == 0) {
                val dx = tx[p] - mx[p]; val dy = ty[p] - my[p]
                val dist = hypot(dx, dy)
                val maxMove = R.MALLET_SPEED * dt
                if (dist > maxMove && dist > 0f) { mx[p] += dx / dist * maxMove; my[p] += dy / dist * maxMove } else { mx[p] = tx[p]; my[p] = ty[p] }
            } else {
                val dx = (tx[p] - mx[p]).coerceIn(-R.PADDLE_SPEED * dt, R.PADDLE_SPEED * dt)
                mx[p] += dx
                my[p] = if (p == 1) R.H - R.PADDLE_Y else R.PADDLE_Y
            }
            mvx[p] = (mx[p] - ox) / dt; mvy[p] = (my[p] - oy) / dt
        }
        when (stage) {
            "READY" -> if (t >= stageUntil) { stage = "PLAY"; launchBall() }
            "SERVE" -> if (t >= stageUntil) { stage = "PLAY"; launchBall() }
            else -> { elapsed += dMs; if (mode == 0) hockeyStep(dt) else pongStep(dt); if (elapsed > R.TIME_CAP_MS && !over) finish() }
        }
    }

    private fun launchBall() {
        if (mode == 1) {
            val ang = (random.nextFloat() - 0.5f) * 0.9f
            val dir = if (serveTo == 1) 1f else -1f   // towards the player who is serving to... who was scored on
            pvx = ballSpeed * sin(ang); pvy = dir * ballSpeed * cos(ang)
        }
    }

    // ------------------------------------------------------------------ air hockey

    private fun hockeyStep(dt: Float) {
        val speed0 = hypot(pvx, pvy)
        val n = max(1, kotlin.math.ceil(max(speed0, 60f) * dt / 1.2f).toInt())
        val h = dt / n
        for (k in 0 until n) {
            px += pvx * h; py += pvy * h
            // the side walls
            if (px < R.PUCK_R) { px = R.PUCK_R; pvx = abs(pvx) * 0.94f; walls++ }
            if (px > R.W - R.PUCK_R) { px = R.W - R.PUCK_R; pvx = -abs(pvx) * 0.94f; walls++ }
            // the end walls, with a goal in the middle of each
            val inGoal = abs(px - 50f) < R.GOAL_HALF
            if (py < R.PUCK_R && !inGoal) { py = R.PUCK_R; pvy = abs(pvy) * 0.94f; walls++ }
            if (py > R.H - R.PUCK_R && !inGoal) { py = R.H - R.PUCK_R; pvy = -abs(pvy) * 0.94f; walls++ }
            if (py < -R.PUCK_R * 1.5f) { scored(1); return }
            if (py > R.H + R.PUCK_R * 1.5f) { scored(2); return }
            // the goal posts: the ends of the walls are round
            if (py < R.PUCK_R * 1.2f || py > R.H - R.PUCK_R * 1.2f) for (postX in floatArrayOf(50f - R.GOAL_HALF, 50f + R.GOAL_HALF)) {
                val postY = if (py < R.H / 2f) 0f else R.H
                val dx = px - postX; val dy = py - postY
                val dist = hypot(dx, dy)
                if (dist < R.PUCK_R && dist > 0.01f) { val nx = dx / dist; val ny = dy / dist; val vn = pvx * nx + pvy * ny; if (vn < 0f) { pvx -= 1.9f * vn * nx; pvy -= 1.9f * vn * ny }; px = postX + nx * R.PUCK_R; py = postY + ny * R.PUCK_R }
            }
            // the mallets
            for (p in 1..2) {
                val dx = px - mx[p]; val dy = py - my[p]
                val dist = hypot(dx, dy)
                val minDist = R.MALLET_R + R.PUCK_R
                if (dist < minDist) {
                    val nx = if (dist > 0.001f) dx / dist else 0f
                    val ny = if (dist > 0.001f) dy / dist else (if (p == 1) -1f else 1f)
                    px = mx[p] + nx * minDist; py = my[p] + ny * minDist
                    val rvx = pvx - mvx[p]; val rvy = pvy - mvy[p]
                    val vn = rvx * nx + rvy * ny
                    if (vn < 0f) {
                        pvx -= (1f + 0.92f) * vn * nx; pvy -= (1f + 0.92f) * vn * ny
                        hits++
                    }
                    // a puck that is only nudged still gets a little push
                    val sp = hypot(pvx, pvy)
                    if (sp < 26f) { pvx += nx * 18f; pvy += ny * 18f }
                }
            }
        }
        val sp = hypot(pvx, pvy)
        if (sp > R.PUCK_MAX) { pvx *= R.PUCK_MAX / sp; pvy *= R.PUCK_MAX / sp }
        // a puck jammed in a corner, or sitting still for ages, is put back in the middle of the half it is in
        val inCorner = (px < 13f || px > 87f) && (py < 13f || py > R.H - 13f)
        stuckMs = if (inCorner && sp < 45f) stuckMs + (dt * 1000f).toLong() else 0L
        idleMs = if (sp < 6f) idleMs + (dt * 1000f).toLong() else 0L
        if (stuckMs > 2200L || idleMs > 6500L) {
            val mine = py > R.H / 2f
            px = 50f; py = if (mine) 100f else 40f; pvx = 0f; pvy = 0f; stuckMs = 0L; idleMs = 0L
        }
        // a little friction, so a slow puck settles
        val f = 1f - 0.28f * dt
        pvx *= f; pvy *= f
        if (hypot(pvx, pvy) < 2.5f) { pvx = 0f; pvy = 0f }
    }

    // ------------------------------------------------------------------ pong

    private fun pongStep(dt: Float) {
        val n = max(1, kotlin.math.ceil(ballSpeed * dt / 1.2f).toInt())
        val h = dt / n
        for (k in 0 until n) {
            val oy = py
            px += pvx * h; py += pvy * h
            if (px < R.BALL_R) { px = R.BALL_R; pvx = abs(pvx); walls++ }
            if (px > R.W - R.BALL_R) { px = R.W - R.BALL_R; pvx = -abs(pvx); walls++ }
            for (p in 1..2) {
                val paddleY = if (p == 1) R.H - R.PADDLE_Y else R.PADDLE_Y
                val moving = if (p == 1) pvy > 0f else pvy < 0f
                val crossed = if (p == 1) py + R.BALL_R >= paddleY - 1.6f && oy + R.BALL_R <= paddleY + 3f else py - R.BALL_R <= paddleY + 1.6f && oy - R.BALL_R >= paddleY - 3f
                if (moving && crossed && abs(px - mx[p]) <= R.PADDLE_HW + R.BALL_R) {
                    val rel = ((px - mx[p]) / R.PADDLE_HW).coerceIn(-1f, 1f)
                    val ang = rel * 1.05f
                    ballSpeed = min(R.BALL_CAP, ballSpeed * 1.045f + 1.2f)
                    val dir = if (p == 1) -1f else 1f
                    pvx = ballSpeed * sin(ang); pvy = dir * ballSpeed * cos(ang)
                    py = if (p == 1) paddleY - 1.6f - R.BALL_R else paddleY + 1.6f + R.BALL_R
                    hits++
                }
            }
            if (py > R.H + 4f) { scored(2); return }
            if (py < -4f) { scored(1); return }
        }
    }

    // ------------------------------------------------------------------ scoring

    private fun scored(p: Int) {
        score[p]++; goals++; lastScorer = p
        pvx = 0f; pvy = 0f
        serveTo = 3 - p                       // the player who was scored on gets the next ball
        if (score[p] >= target) { finish(); return }
        placeForServe()
        stage = "SERVE"; stageUntil = t + R.SERVE_MS
    }

    private fun finish() {
        over = true; stage = "END"
        result = if (score[1] > score[2]) 1 else if (score[2] > score[1]) 2 else 3
    }

    override fun hostSnapshot(): JSONObject = JSONObject()
        .put("md", mode).put("tg", target).put("st", stage).put("rd", max(0L, stageUntil - t).toInt())
        .put("p", org.json.JSONArray().put(Arcade.r1(px)).put(Arcade.r1(py)).put(Arcade.r1(pvx)).put(Arcade.r1(pvy)))
        .put("m1", org.json.JSONArray().put(Arcade.r1(mx[1])).put(Arcade.r1(my[1])))
        .put("m2", org.json.JSONArray().put(Arcade.r1(mx[2])).put(Arcade.r1(my[2])))
        .put("s1", score[1]).put("s2", score[2]).put("gl", goals).put("ht", hits).put("wl", walls).put("ls", lastScorer)
        .put("tm", elapsed).put("ov", over).put("res", result)

    override fun adopt(s: JSONObject) {
        val p = s.optJSONArray("p") ?: return
        val a = s.optJSONArray("m1") ?: return
        val b = s.optJSONArray("m2") ?: return
        if (p.length() < 4 || a.length() < 2 || b.length() < 2) return
        val me = if (isHost) 1 else 2
        // my view: the guest's picture is turned half way round
        fun x(v: Double) = if (isHost) v.toFloat().coerceIn(-10f, 110f) else (R.W - v.toFloat()).coerceIn(-10f, 110f)
        fun y(v: Double) = if (isHost) v.toFloat().coerceIn(-15f, 155f) else (R.H - v.toFloat()).coerceIn(-15f, 155f)
        fun v(v: Double) = (if (isHost) v.toFloat() else -v.toFloat()).coerceIn(-400f, 400f)
        val ov = s.optBoolean("ov", false)
        val res = s.optInt("res", 0).coerceIn(0, 3)
        val ls = s.optInt("ls", 0).coerceIn(0, 2)
        val mine = if (isHost) a else b
        val theirs = if (isHost) b else a
        state = HockeyState(
            phase = if (ov) GPhase.OVER else GPhase.PLAYING, mode = s.optInt("md", 0).coerceIn(0, 1), target = s.optInt("tg", 5).coerceIn(1, 20),
            puckX = x(p.optDouble(0)), puckY = y(p.optDouble(1)), puckVx = v(p.optDouble(2)), puckVy = v(p.optDouble(3)),
            myX = x(mine.optDouble(0)), myY = y(mine.optDouble(1)), theirX = x(theirs.optDouble(0)), theirY = y(theirs.optDouble(1)),
            myScore = (if (isHost) s.optInt("s1", 0) else s.optInt("s2", 0)).coerceIn(0, 30),
            theirScore = (if (isHost) s.optInt("s2", 0) else s.optInt("s1", 0)).coerceIn(0, 30),
            stage = s.optString("st", "PLAY").take(5), readyMs = s.optInt("rd", 0).coerceIn(0, 10_000),
            goals = s.optInt("gl", 0).coerceIn(0, 100), hits = s.optInt("ht", 0).coerceIn(0, 100_000), walls = s.optInt("wl", 0).coerceIn(0, 100_000),
            lastScorer = if (ls == 0) 0 else if (ls == me) 1 else 2, elapsedMs = s.optLong("tm", 0L).coerceIn(0L, 10_000_000L),
            receivedAt = clock(), result = if (!ov) 0 else if (res == 3) 3 else if (res == me) 1 else 2, iAmHost = isHost,
        )
    }

    override fun reset() {
        state = HockeyState(iAmHost = isHost)
    }
}

// ====================================================================== the computer player

class HockeyBrain(private val e: HockeyEngine, skill: Skill, random: Random) : Brain(skill, random) {
    private var wobble = 0f
    private var wobbleAt = 0L
    private var lastX = 50f
    private var lastY = 124f
    private var aimOffset = 0f
    private var mistake = 0f
    private var lastDir = 0

    override fun think(now: Long) {
        val s = e.state
        if (s.phase != GPhase.PLAYING) return
        if (now > wobbleAt) { wobble = random.nextFloat() * 2f - 1f; wobbleAt = now + random.nextLong(250, 700) }
        val err = when (skill) { Skill.EASY -> 9f; Skill.NORMAL -> 4f; Skill.HARD -> 1.2f } * wobble
        if (s.pong) {
            var target = 50f
            val dir = if (s.puckVy > 5f) 1 else if (s.puckVy < -5f) -1 else 0
            if (dir != lastDir) {
                lastDir = dir
                if (dir == 1) {
                    // a new ball is coming: choose where on the paddle to meet it (that decides the angle it goes back at), and whether to slip up
                    aimOffset = (random.nextFloat() * 2f - 1f) * 0.85f * HockeyRules.PADDLE_HW
                    val missPct = when (skill) { Skill.EASY -> 30; Skill.NORMAL -> 12; Skill.HARD -> 4 }
                    mistake = if (pct(missPct)) (if (pct(50)) 1f else -1f) * (HockeyRules.PADDLE_HW * (1.6f + random.nextFloat() * 1.5f)) else 0f
                }
            }
            // where the ball will cross my end (bouncing off the side walls on the way)
            if (s.puckVy > 5f) {
                val tt = (HockeyRules.H - HockeyRules.PADDLE_Y - s.puckY) / s.puckVy
                var xx = s.puckX + s.puckVx * tt
                val w = HockeyRules.W
                xx = ((xx % (2 * w)) + 2 * w) % (2 * w)
                target = if (xx > w) 2 * w - xx else xx
            } else target = 50f + (s.puckX - 50f) * 0.3f
            val lag = if (skill == Skill.EASY && s.puckY < 55f) 0.4f else 1f
            val aim = if (s.puckVy > 5f) target - aimOffset + mistake else target
            lastX += (aim + err - lastX) * lag.coerceAtMost(1f) * (if (skill == Skill.EASY) 0.25f else 0.6f)
            e.input(lastX, 124f)
            return
        }
        // air hockey: strike the puck towards their goal when it is on my side, otherwise guard my goal
        val goalX = 50f
        var tx: Float; var ty: Float
        val onMySide = s.puckY > 66f
        if (onMySide) {
            val dx = s.puckX - goalX; val dy = s.puckY - 0f
            val d = hypot(dx, dy).coerceAtLeast(1f)
            // stand behind the puck on the line from their goal, then push through it
            val behind = if (s.puckY > 118f || abs(s.puckVy) > 80f) 0f else HockeyRules.MALLET_R + HockeyRules.PUCK_R + 2f
            tx = s.puckX + dx / d * behind
            ty = s.puckY + dy / d * behind
            val close = hypot(s.myX - tx, s.myY - ty) < 6f
            if (close || behind == 0f) { tx = s.puckX; ty = s.puckY - 3f }
        } else {
            // guard: sit between the puck and my goal
            tx = 50f + (s.puckX - 50f) * 0.55f
            ty = 124f
        }
        val fast = when (skill) { Skill.EASY -> 0.55f; Skill.NORMAL -> 0.8f; Skill.HARD -> 1f }
        lastX += (tx + err - lastX) * fast; lastY += (ty + err - lastY) * fast
        val (cx, cy) = HockeyRules.clampMallet(lastX, lastY)
        e.input(cx, cy)
    }
}
