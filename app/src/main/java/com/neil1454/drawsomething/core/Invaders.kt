package com.neil1454.drawsomething.core

import org.json.JSONArray
import org.json.JSONObject
import kotlin.math.abs
import kotlin.math.ceil
import kotlin.math.max
import kotlin.math.min
import kotlin.math.pow
import kotlin.random.Random

// ====================================================================================
// Space Invaders for two. Both players fly around the bottom of the same screen at the same time and shoot the
// invaders as they march down. Hold your finger down to fire, move it to fly. Shields protect you (and crumble),
// the mystery ship drops a power-up (rapid fire, triple shot or an extra life), and five waves get faster and meaner.
// You score separately, so you are partners against the aliens and rivals for the high score.
// ====================================================================================

object InvadersRules {
    const val COLS = 10
    const val ROWS = 5
    const val TOTAL = COLS * ROWS
    const val SPACING_X = 8f
    const val SPACING_Y = 7f
    const val SHIELDS = 4
    const val SHIELD_W = 12
    const val SHIELD_H = 8
    const val CELL = 1.2f
    const val SHIELD_TOP = 100f
    const val ZONE_TOP = 112f
    const val ZONE_BOTTOM = 134f
    const val INVADE_Y = 110f
    const val TIME_CAP_MS = 15 * 60_000L
    val POINTS = intArrayOf(30, 20, 20, 10, 10)

    fun shieldLeft(i: Int) = 12.5f + i * 25f - SHIELD_W * CELL / 2f

    /** The classic bunker shape: a rounded top and a notch cut out of the bottom. */
    val SHAPE: List<String> = listOf(
        "..########..",
        ".##########.",
        "############",
        "############",
        "############",
        "############",
        "####....####",
        "###......###",
    )
}

data class InvadersState(
    val c: ArcadeCommon = ArcadeCommon(),
    /** Fifty characters, row by row from the top: '1' = that invader is alive. */
    val alive: String = "",
    val fx: Float = 8f,
    val fy: Float = 14f,
    val frame: Int = 0,
    val bombs: List<BulletView> = emptyList(),
    /** Four shields, 96 cells each, as a string of '0' and '1'. */
    val shields: String = "",
    /** Where the mystery ship is, or -100 when it isn't about. */
    val ufoX: Float = -100f,
) {
    val phase get() = c.phase
    val myScore get() = c.mine.score
    val theirScore get() = c.theirs.score
    val result get() = c.result
    val remaining get() = alive.count { it == '1' }
}

class InvadersEngine(
    myName: String,
    theirName: String,
    isHost: Boolean,
    send: (GMsg) -> Unit,
    random: Random = Random.Default,
    clock: () -> Long = { System.currentTimeMillis() },
) : SyncEngine(GameKind.INVADERS, myName, theirName, isHost, send, random, clock) {

    var state = InvadersState(ArcadeCommon(iAmHost = isHost))
        private set
    override val stateAny: Any get() = state
    override val phase get() = state.c.phase

    private val R = InvadersRules
    private val ships = arrayOf(Ship(0f, 0f), Ship(30f, 130f), Ship(70f, 130f))
    private val alive = BooleanArray(R.TOTAL)
    private val shields = BooleanArray(R.SHIELDS * R.SHIELD_W * R.SHIELD_H)
    private val bullets = ArrayList<Bullet>()
    private val bombs = ArrayList<Bullet>()
    private val bursts = ArrayList<Burst>()
    private var burstId = 0
    private var fx = 8f
    private var fy = 14f
    private var dir = 1f
    private var moveAcc = 0f
    private var wave = 1
    private var waves = 5
    private var stage = "READY"
    private var stageUntil = 0L
    private var t = 0L          // the game's own clock, in milliseconds of play
    private var lastAt = 0L
    private var elapsed = 0L
    private var ufoX = -100f
    private var ufoDir = 1f
    private var ufoNext = 0L
    private var over = false
    private var why = ""

    /** Where my finger is: send this about 20 times a second. */
    fun input(x: Float, y: Float, fire: Boolean) {
        act(JSONObject().put("a", "in").put("x", Arcade.r1(x)).put("y", Arcade.r1(y)).put("f", if (fire) 1 else 0))
    }

    override fun hostBegin(opts: JSONObject) {
        waves = opts.optInt("waves", 5).coerceIn(1, 9)
        val lives = opts.optInt("lives", Arcade.START_LIVES).coerceIn(1, 5)
        ships[1].reset(30f, 130f); ships[2].reset(70f, 130f)
        ships[1].lives = lives; ships[2].lives = lives
        wave = 1; t = 0L; elapsed = 0L; over = false; why = ""
        bursts.clear(); burstId = 0
        for (i in shields.indices) shields[i] = false
        resetShields()
        startWave()
        lastAt = clock()
    }

    private fun resetShields() {
        for (s in 0 until R.SHIELDS) for (y in 0 until R.SHIELD_H) for (x in 0 until R.SHIELD_W) shields[s * 96 + y * 12 + x] = R.SHAPE[y][x] == '#'
    }

    private fun startWave() {
        alive.fill(true)
        fx = 8f; fy = 14f + min(wave - 1, 4) * 3f; dir = 1f; moveAcc = 0f
        bullets.clear(); bombs.clear()
        ufoX = -100f; ufoNext = t + random.nextLong(12_000, 22_000)
        stage = "READY"; stageUntil = t + Arcade.READY_MS
        if (wave > 1 && wave % 2 == 1) resetShields()
    }

    override fun hostHandle(fromHost: Boolean, a: JSONObject) {
        if (state.c.phase != GPhase.PLAYING || over) return
        if (a.getString("a") != "in") return
        val s = ships[if (fromHost) 1 else 2]
        s.tx = a.optDouble("x", s.x.toDouble()).toFloat().coerceIn(3f, Arcade.W - 3f)
        s.ty = a.optDouble("y", s.y.toDouble()).toFloat().coerceIn(R.ZONE_TOP, R.ZONE_BOTTOM)
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
            step(d / 1000f)
            if (over) break
        }
        publish()
    }

    private fun burst(x: Float, y: Float, kind: String) {
        bursts.add(Burst(++burstId, x, y, kind, t))
        while (bursts.size > 16) bursts.removeAt(0)
    }

    private fun step(dt: Float) {
        for (p in 1..2) if (ships[p].alive) ships[p].fly(dt, R.ZONE_TOP, R.ZONE_BOTTOM)
        when (stage) {
            "READY" -> {
                if (t >= stageUntil) stage = "PLAY"
                respawns()
            }
            "WAVE" -> {
                if (t >= stageUntil) { wave++; startWave() }
                respawns()
            }
            else -> play(dt)
        }
        bursts.removeAll { t - it.at > 900L }
    }

    private fun respawns() {
        for (p in 1..2) {
            val s = ships[p]
            if (!s.alive && s.lives > 0 && t >= s.respawnAt) {
                s.alive = true; s.invulnUntil = t + Arcade.INVULN_MS
                s.tx = if (p == 1) 30f else 70f; s.ty = 130f; s.x = s.tx; s.y = s.ty
            }
        }
    }

    private fun play(dt: Float) {
        elapsed += (dt * 1000f).toLong()
        respawns()
        // firing
        for (p in 1..2) {
            val s = ships[p]
            if (!s.alive) continue
            s.cooldown -= dt
            val rapid = t < s.rapidUntil
            val maxOwn = if (rapid) 6 else 3
            if (s.firing && s.cooldown <= 0f && bullets.count { it.owner == p } < maxOwn) {
                s.cooldown = if (rapid) 0.15f else 0.36f
                bullets.add(Bullet(s.x, s.y - 4f, 0f, -Arcade.BULLET_SPEED, p))
                if (t < s.tripleUntil) {
                    bullets.add(Bullet(s.x - 1.5f, s.y - 3f, -22f, -Arcade.BULLET_SPEED, p))
                    bullets.add(Bullet(s.x + 1.5f, s.y - 3f, 22f, -Arcade.BULLET_SPEED, p))
                }
            }
        }
        moveBullets(dt)
        moveBombs(dt)
        moveFormation(dt)
        dropBombs(dt)
        moveUfo(dt)
        // wave cleared?
        if (alive.none { it }) {
            for (p in 1..2) ships[p].score += 50 * wave
            if (wave >= waves) {
                finish("victory")
            } else {
                stage = "WAVE"; stageUntil = t + 2400L
                bullets.clear(); bombs.clear()
                for (p in 1..2) if (ships[p].lives == 0) { ships[p].lives = 1; ships[p].alive = false; ships[p].respawnAt = t + 600L }
            }
            return
        }
        // the invasion
        val lowest = lowestY()
        if (lowest >= R.INVADE_Y) {
            burst(50f, lowest, "inv")
            finish("invaded")
            return
        }
        if (elapsed > R.TIME_CAP_MS) finish("time")
        if (ships.drop(1).all { it.lives == 0 && !it.alive }) finish("lives")
    }

    private fun finish(reason: String) {
        over = true; why = reason; stage = "END"
    }

    private fun iX(c: Int) = fx + c * R.SPACING_X + 3f
    private fun iY(r: Int) = fy + r * R.SPACING_Y + 2.25f

    private fun lowestY(): Float {
        var y = -100f
        for (r in 0 until R.ROWS) for (c in 0 until R.COLS) if (alive[r * R.COLS + c]) y = max(y, iY(r) + 2.25f)
        return y
    }

    private fun moveBullets(dt: Float) {
        val it = bullets.iterator()
        while (it.hasNext()) {
            val b = it.next()
            val dist = max(abs(b.vy), abs(b.vx)) * dt
            val n = max(1, ceil(dist / 1.4f).toInt())
            var removed = false
            for (k in 0 until n) {
                b.x += b.vx * dt / n
                b.y += b.vy * dt / n
                if (b.y < -3f || b.x < -3f || b.x > Arcade.W + 3f) { removed = true; break }
                if (hitShield(b.x, b.y)) { removed = true; break }
                if (hitUfo(b)) { removed = true; break }
                if (hitInvader(b)) { removed = true; break }
            }
            if (removed) it.remove()
        }
    }

    private fun hitInvader(b: Bullet): Boolean {
        for (r in 0 until R.ROWS) for (c in 0 until R.COLS) {
            val i = r * R.COLS + c
            if (!alive[i]) continue
            if (abs(b.x - iX(c)) < 3.5f && abs(b.y - iY(r)) < 3.1f) {
                alive[i] = false
                ships[b.owner].score += R.POINTS[r] * (1 + (wave - 1) / 2)
                burst(iX(c), iY(r), "ex$r")
                return true
            }
        }
        return false
    }

    private fun hitUfo(b: Bullet): Boolean {
        if (ufoX < -50f || abs(b.x - ufoX) > 5.5f || abs(b.y - 9f) > 3.2f) return false
        val owner = ships[b.owner]
        owner.score += intArrayOf(50, 100, 150, 300)[random.nextInt(4)]
        burst(ufoX, 9f, "ufo")
        ufoX = -100f
        ufoNext = t + random.nextLong(14_000, 24_000)
        when (random.nextInt(3)) {
            0 -> owner.rapidUntil = t + Arcade.POWER_MS
            1 -> owner.tripleUntil = t + Arcade.POWER_MS
            else -> if (owner.lives < Arcade.MAX_LIVES) owner.lives++ else owner.rapidUntil = t + Arcade.POWER_MS
        }
        burst(owner.x, owner.y - 6f, "pw")
        return true
    }

    /** Chips a hole in a shield where a bullet or bomb hit. */
    private fun hitShield(x: Float, y: Float): Boolean {
        if (y < R.SHIELD_TOP - 1f || y > R.SHIELD_TOP + R.SHIELD_H * R.CELL + 1f) return false
        for (s in 0 until R.SHIELDS) {
            val cx = ((x - R.shieldLeft(s)) / R.CELL).toInt().let { if (x < R.shieldLeft(s)) -1 else it }
            val cy = ((y - R.SHIELD_TOP) / R.CELL).toInt().let { if (y < R.SHIELD_TOP) -1 else it }
            if (cx !in 0 until R.SHIELD_W || cy !in 0 until R.SHIELD_H) continue
            if (!shields[s * 96 + cy * 12 + cx]) continue
            for (dx in -1..1) for (dy in -1..1) {
                val nx = cx + dx
                val ny = cy + dy
                if (nx !in 0 until R.SHIELD_W || ny !in 0 until R.SHIELD_H) continue
                if ((dx == 0 && dy == 0) || random.nextInt(100) < 65) shields[s * 96 + ny * 12 + nx] = false
            }
            return true
        }
        return false
    }

    private fun moveBombs(dt: Float) {
        val it = bombs.iterator()
        while (it.hasNext()) {
            val b = it.next()
            val n = max(1, ceil(abs(b.vy) * dt / 1.4f).toInt())
            var removed = false
            for (k in 0 until n) {
                b.y += b.vy * dt / n
                b.x += b.vx * dt / n
                if (b.y > Arcade.H + 2f) { removed = true; break }
                if (hitShield(b.x, b.y)) { removed = true; break }
                for (p in 1..2) {
                    val s = ships[p]
                    if (s.alive && t >= s.invulnUntil && abs(b.x - s.x) < 4f && abs(b.y - s.y) < 3.6f) {
                        hurt(p)
                        removed = true
                        break
                    }
                }
                if (removed) break
            }
            if (removed) it.remove()
        }
    }

    private fun hurt(p: Int) {
        val s = ships[p]
        s.lives--
        s.alive = false
        s.rapidUntil = 0L; s.tripleUntil = 0L
        s.respawnAt = t + Arcade.RESPAWN_MS
        burst(s.x, s.y, "pl")
    }

    private fun moveFormation(dt: Float) {
        val a = alive.count { it }
        if (a == 0) return
        val speed = (7f + 24f * (1f - a / R.TOTAL.toFloat()).pow(1.6f)) * (1f + 0.16f * (wave - 1))
        val dx = dir * speed * dt
        fx += dx
        moveAcc += abs(dx)
        var left = 99
        var right = -1
        for (r in 0 until R.ROWS) for (c in 0 until R.COLS) if (alive[r * R.COLS + c]) { left = min(left, c); right = max(right, c) }
        val leftEdge = fx + left * R.SPACING_X
        val rightEdge = fx + right * R.SPACING_X + 6f
        if (dir > 0 && rightEdge >= Arcade.W - 2f) { dir = -1f; fy += 4f; fx -= rightEdge - (Arcade.W - 2f) }
        else if (dir < 0 && leftEdge <= 2f) { dir = 1f; fy += 4f; fx += 2f - leftEdge }
        // invaders that reach the shields eat them
        val low = lowestY()
        if (low >= R.SHIELD_TOP) {
            for (s in 0 until R.SHIELDS) for (y in 0 until R.SHIELD_H) for (x in 0 until R.SHIELD_W) {
                val px = R.shieldLeft(s) + x * R.CELL
                val py = R.SHIELD_TOP + y * R.CELL
                for (r in 0 until R.ROWS) for (c in 0 until R.COLS) if (alive[r * R.COLS + c] && abs(px - iX(c)) < 4f && abs(py - iY(r)) < 3.4f) shields[s * 96 + y * 12 + x] = false
            }
        }
    }

    private fun dropBombs(dt: Float) {
        val a = alive.count { it }
        val perSec = 0.9f + 0.5f * wave + (1f - a / R.TOTAL.toFloat()) * 1.5f
        if (random.nextFloat() >= perSec * dt || bombs.size >= 4 + wave) return
        val cols = (0 until R.COLS).filter { c -> (0 until R.ROWS).any { alive[it * R.COLS + c] } }
        if (cols.isEmpty()) return
        val c = cols[random.nextInt(cols.size)]
        val r = (R.ROWS - 1 downTo 0).first { alive[it * R.COLS + c] }
        bombs.add(Bullet(iX(c), iY(r) + 3f, 0f, 32f + 5f * wave, 0, random.nextInt(3)))
    }

    private fun moveUfo(dt: Float) {
        if (ufoX < -50f) {
            if (t >= ufoNext && stage == "PLAY") {
                ufoDir = if (random.nextBoolean()) 1f else -1f
                ufoX = if (ufoDir > 0) -6f else 106f
            }
            return
        }
        ufoX += ufoDir * 24f * dt
        if (ufoX < -8f || ufoX > 108f) { ufoX = -100f; ufoNext = t + random.nextLong(14_000, 24_000) }
    }

    // ------------------------------------------------------------------ the picture sent to the other phone

    override fun hostSnapshot(): JSONObject {
        val o = JSONObject()
            .put("st", stage).put("w", wave).put("W", waves)
            .put("fx", Arcade.r1(fx)).put("fy", Arcade.r1(fy)).put("fr", (moveAcc / 2.5f).toInt() % 2)
            .put("al", String(CharArray(R.TOTAL) { if (alive[it]) '1' else '0' }))
            .put("sh", hex())
            .put("b", ArcadeCodec.bullets(bullets)).put("bm", ArcadeCodec.bullets(bombs, 24))
            .put("s1", ArcadeCodec.ship(ships[1], t)).put("s2", ArcadeCodec.ship(ships[2], t))
            .put("ev", ArcadeCodec.bursts(bursts, t))
            .put("rd", max(0L, stageUntil - t).toInt()).put("tm", elapsed)
            .put("ov", over).put("why", why)
        if (ufoX > -50f) o.put("u", Arcade.r1(ufoX))
        return o
    }

    private fun hex(): String {
        val sb = StringBuilder(96)
        var i = 0
        while (i < shields.size) {
            var v = 0
            for (k in 0 until 4) if (shields[i + k]) v = v or (1 shl k)
            sb.append("0123456789abcdef"[v])
            i += 4
        }
        return sb.toString()
    }

    override fun adopt(s: JSONObject) {
        val al = s.getString("al")
        val hx = s.getString("sh")
        if (al.length != R.TOTAL || al.any { it !in "01" } || hx.length != 96 || hx.any { it !in "0123456789abcdef" }) return
        val sh = StringBuilder(384)
        for (ch in hx) {
            val v = Character.digit(ch, 16)
            for (k in 0 until 4) sh.append(if (v and (1 shl k) != 0) '1' else '0')
        }
        val me1 = isHost
        val ov = s.optBoolean("ov", false)
        val mine = ArcadeCommon(iAmHost = isHost)
        val a = ArcadeCodec.readShip(s.optJSONArray(if (me1) "s1" else "s2"))
        val b = ArcadeCodec.readShip(s.optJSONArray(if (me1) "s2" else "s1"))
        val result = if (!ov) 0 else if (a.score > b.score) 1 else if (a.score < b.score) 2 else 3
        state = InvadersState(
            c = mine.copy(
                phase = if (ov) GPhase.OVER else GPhase.PLAYING, stage = s.optString("st", "PLAY").take(6),
                level = s.optInt("w", 1).coerceIn(1, 9), levels = s.optInt("W", 5).coerceIn(1, 9),
                mine = a, theirs = b,
                bullets = ArcadeCodec.readBullets(s.optJSONArray("b")),
                bursts = ArcadeCodec.readBursts(s.optJSONArray("ev")),
                readyMs = s.optInt("rd", 0).coerceIn(0, 10_000), elapsedMs = s.optLong("tm", 0L).coerceIn(0L, 10_000_000L),
                receivedAt = clock(), result = result, why = s.optString("why", "").take(8),
            ),
            alive = al, fx = s.optDouble("fx", 8.0).toFloat().coerceIn(-20f, 120f), fy = s.optDouble("fy", 14.0).toFloat().coerceIn(0f, 140f),
            frame = s.optInt("fr", 0).coerceIn(0, 1),
            bombs = ArcadeCodec.readBullets(s.optJSONArray("bm"), 30), shields = sh.toString(),
            ufoX = if (s.has("u")) s.optDouble("u", -100.0).toFloat().coerceIn(-20f, 120f) else -100f,
        )
    }

    override fun reset() {
        state = InvadersState(ArcadeCommon(iAmHost = isHost))
    }
}

// ====================================================================== the computer player

class InvadersBrain(private val e: InvadersEngine, skill: Skill, random: Random) : ArcadeBrainBase(skill, random) {
    private var lastX = -1f
    private var wobble = 0f
    private var wobbleAt = 0L

    override fun think(now: Long) {
        val s = e.state
        if (s.c.phase != GPhase.PLAYING) return
        val me = s.c.mine
        if (!me.alive) return
        if (now > wobbleAt) { wobble = random.nextFloat() * 2f - 1f; wobbleAt = now + random.nextLong(400, 1400) }
        val aim = aimX(s, me.x)
        var target = aim
        // get out of the way of bombs
        val danger = s.bombs.filter { it.owner == 0 && it.vy > 0 && abs(it.x - me.x) < (if (skill == Skill.HARD) 8f else 6f) && it.y > me.y - 38f && it.y < me.y + 2f }.minByOrNull { me.y - it.y }
        if (danger != null && skill != Skill.EASY) {
            target = if (danger.x >= me.x) danger.x - 9f else danger.x + 9f
            if (target < 6f) target = danger.x + 9f
            if (target > 94f) target = danger.x - 9f
        } else {
            target += wobble * (if (skill == Skill.EASY) 9f else if (skill == Skill.NORMAL) 4f else 1.5f)
        }
        target = target.coerceIn(5f, 95f)
        if (abs(target - lastX) > 0.4f || s.c.stage != "PLAY") {
            lastX = target
        }
        e.input(target, 130f, true)
    }

    /** Line up under the invader that is lowest, preferring one nearest to this ship. */
    private fun aimX(s: InvadersState, from: Float): Float {
        val xs = ArrayList<Float>()
        val rows = InvadersRules.ROWS
        val cols = InvadersRules.COLS
        for (c in 0 until cols) {
            val r = (rows - 1 downTo 0).firstOrNull { s.alive.getOrNull(it * cols + c) == '1' } ?: continue
            xs += s.fx + c * InvadersRules.SPACING_X + 3f
        }
        if (s.ufoX > -50f && skill != Skill.EASY) xs += s.ufoX
        return pickNear(xs, from) ?: 50f
    }
}
