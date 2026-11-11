package com.neil1454.drawsomething.core

import org.json.JSONArray
import org.json.JSONObject
import kotlin.math.abs
import kotlin.math.ceil
import kotlin.math.max
import kotlin.math.min
import kotlin.random.Random

// ====================================================================================
// Centipede for two. A centipede winds down through a field of mushrooms towards you. Shoot it: every segment you hit
// turns into a mushroom and splits it in two. Shoot the head for the big points. Both players share the bottom of the
// screen, so watch out for the spider, the dropping flea, and each other. Five levels, each with a shorter centipede
// that is joined by more single heads. Hold your finger down to fire, and move it to fly.
// ====================================================================================

object CentipedeRules {
    const val COLS = 25
    const val ROWS = 35
    const val CELL = 4f
    const val ZONE_ROW = 27           // the bottom rows where the ships (and the pests) roam
    const val TOTAL_SEGS = 12
    const val SHIP_TOP = 110f
    const val SHIP_BOTTOM = 136f
    const val TIME_CAP_MS = 15 * 60_000L

    fun stepMs(level: Int): Int = max(52, 124 - 12 * (level - 1))
    fun idx(col: Int, row: Int) = row * COLS + col
}

class SegView(val id: Int, val col: Int, val row: Int, val head: Boolean, val dir: Int)

data class CentipedeState(
    val c: ArcadeCommon = ArcadeCommon(),
    val segs: List<SegView> = emptyList(),
    /** 25 x 35 digits, row by row: 0 = nothing, 1-4 = a mushroom (4 is fresh). */
    val mush: String = "",
    val spider: Pair<Float, Float>? = null,
    val flea: Triple<Float, Float, Int>? = null,
    val stepMs: Int = 120,
) {
    val phase get() = c.phase
    val myScore get() = c.mine.score
    val theirScore get() = c.theirs.score
    val result get() = c.result
}

class CentipedeEngine(
    myName: String,
    theirName: String,
    isHost: Boolean,
    send: (GMsg) -> Unit,
    random: Random = Random.Default,
    clock: () -> Long = { System.currentTimeMillis() },
) : SyncEngine(GameKind.CENTIPEDE, myName, theirName, isHost, send, random, clock) {

    var state = CentipedeState(ArcadeCommon(iAmHost = isHost))
        private set
    override val stateAny: Any get() = state
    override val phase get() = state.c.phase

    private val R = CentipedeRules

    private class Seg(val id: Int, var col: Int, var row: Int)
    private class Chain(val segs: ArrayList<Seg>, var dirX: Int, var dirY: Int)

    private val ships = arrayOf(Ship(0f, 0f), Ship(30f, 130f), Ship(70f, 130f))
    private val mush = IntArray(R.COLS * R.ROWS)
    private val chains = ArrayList<Chain>()
    private val bullets = ArrayList<Bullet>()
    private val bursts = ArrayList<Burst>()
    private var burstId = 0
    private var segId = 0
    private var level = 1
    private var levels = 5
    private var stage = "READY"
    private var stageUntil = 0L
    private var t = 0L
    private var lastAt = 0L
    private var elapsed = 0L
    private var stepAcc = 0L
    private var mushVersion = 0
    private var sentVersion = -1
    private var spawnTimes = ArrayList<Long>()   // single heads still to come
    private var spiderX = -100f
    private var spiderY = 0f
    private var spiderVx = 0f
    private var spiderVy = 0f
    private var spiderNext = 0L
    private var fleaX = -100f
    private var fleaY = 0f
    private var fleaHp = 0
    private var fleaNext = 0L
    private var over = false
    private var why = ""

    fun input(x: Float, y: Float, fire: Boolean) {
        act(JSONObject().put("a", "in").put("x", Arcade.r1(x)).put("y", Arcade.r1(y)).put("f", if (fire) 1 else 0))
    }

    override fun hostBegin(opts: JSONObject) {
        levels = opts.optInt("waves", 5).coerceIn(1, 9)
        val lives = opts.optInt("lives", Arcade.START_LIVES).coerceIn(1, 5)
        ships[1].reset(30f, 130f); ships[2].reset(70f, 130f)
        ships[1].lives = lives; ships[2].lives = lives
        level = 1; t = 0L; elapsed = 0L; over = false; why = ""; bursts.clear(); burstId = 0; segId = 0
        mush.fill(0)
        repeat(34) {
            val c = random.nextInt(R.COLS)
            val r = random.nextInt(2, R.ZONE_ROW - 2)
            mush[R.idx(c, r)] = 4
        }
        mushVersion++
        startLevel()
        lastAt = clock()
    }

    private fun startLevel() {
        chains.clear(); bullets.clear(); spawnTimes.clear()
        val heads = level  // one long centipede plus (level - 1) single heads
        val longLen = max(2, R.TOTAL_SEGS - (level - 1) * 2)
        val segs = ArrayList<Seg>()
        for (i in 0 until longLen) segs.add(Seg(++segId, -i, 0)) // starts off the left edge and walks in
        chains.add(Chain(segs, 1, 1))
        for (k in 1 until heads) spawnTimes.add(t + Arcade.READY_MS + 2500L * k)
        spiderX = -100f; fleaX = -100f
        spiderNext = t + random.nextLong(7_000, 12_000)
        fleaNext = t + random.nextLong(10_000, 18_000)
        stage = "READY"; stageUntil = t + Arcade.READY_MS
        stepAcc = 0L
    }

    override fun hostHandle(fromHost: Boolean, a: JSONObject) {
        if (state.c.phase != GPhase.PLAYING || over) return
        if (a.getString("a") != "in") return
        val s = ships[if (fromHost) 1 else 2]
        s.tx = a.optDouble("x", s.x.toDouble()).toFloat().coerceIn(3f, Arcade.W - 3f)
        s.ty = a.optDouble("y", s.y.toDouble()).toFloat().coerceIn(R.SHIP_TOP, R.SHIP_BOTTOM)
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
        while (bursts.size > 16) bursts.removeAt(0)
    }

    private fun step(dMs: Long) {
        val dt = dMs / 1000f
        for (p in 1..2) if (ships[p].alive) ships[p].fly(dt, R.SHIP_TOP, R.SHIP_BOTTOM)
        respawns()
        when (stage) {
            "READY" -> if (t >= stageUntil) stage = "PLAY"
            "WAVE" -> if (t >= stageUntil) { level++; startLevel() }
            else -> play(dMs)
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

    private fun play(dMs: Long) {
        val dt = dMs / 1000f
        elapsed += dMs
        // firing
        for (p in 1..2) {
            val s = ships[p]
            if (!s.alive) continue
            s.cooldown -= dt
            val rapid = t < s.rapidUntil
            if (s.firing && s.cooldown <= 0f && bullets.count { it.owner == p } < (if (rapid) 6 else 3)) {
                s.cooldown = if (rapid) 0.13f else 0.3f
                bullets.add(Bullet(s.x, s.y - 4f, 0f, -140f, p))
                if (t < s.tripleUntil) {
                    bullets.add(Bullet(s.x - 1.5f, s.y - 3f, -20f, -140f, p))
                    bullets.add(Bullet(s.x + 1.5f, s.y - 3f, 20f, -140f, p))
                }
            }
        }
        moveBullets(dt)
        // the centipede walks one square at a time
        stepAcc += dMs
        val every = R.stepMs(level)
        while (stepAcc >= every) { stepAcc -= every; stepChains() }
        // single heads arriving
        val due = spawnTimes.filter { t >= it }
        if (due.isNotEmpty()) {
            spawnTimes.removeAll(due.toSet())
            for (x in due) chains.add(Chain(arrayListOf(Seg(++segId, R.COLS - 1, 0)), -1, 1))
        }
        pests(dt)
        collisions()
        if (chains.isEmpty() && spawnTimes.isEmpty()) {
            for (p in 1..2) ships[p].score += 100 * level
            if (level >= levels) finish("victory")
            else {
                stage = "WAVE"; stageUntil = t + 2400L; bullets.clear(); spiderX = -100f; fleaX = -100f
                for (p in 1..2) if (ships[p].lives == 0) { ships[p].lives = 1; ships[p].alive = false; ships[p].respawnAt = t + 600L }
            }
            return
        }
        if (elapsed > R.TIME_CAP_MS) finish("time")
        if (ships.drop(1).all { it.lives == 0 && !it.alive }) finish("lives")
    }

    private fun finish(reason: String) { over = true; why = reason; stage = "END" }

    private fun stepChains() {
        for (ch in chains) {
            val head = ch.segs[0]
            // the squares behind follow the one in front
            val oldC = IntArray(ch.segs.size) { ch.segs[it].col }
            val oldR = IntArray(ch.segs.size) { ch.segs[it].row }
            var nc = head.col + ch.dirX
            var nr = head.row
            val blocked = if (nc < 0) head.col >= 0 else (nc >= R.COLS || mush[R.idx(nc, head.row)] > 0)
            if (blocked) {
                ch.dirX = -ch.dirX
                nc = head.col
                nr = head.row + ch.dirY
                if (ch.dirY > 0 && nr >= R.ROWS) { ch.dirY = -1; nr = head.row - 1 }
                else if (ch.dirY < 0 && nr < R.ZONE_ROW) { ch.dirY = 1; nr = head.row + 1 }
                nr = nr.coerceIn(0, R.ROWS - 1)
                // can't turn back into a wall: step the other way next time
                if (nc !in 0 until R.COLS) nc = nc.coerceIn(0, R.COLS - 1)
            }
            head.col = nc; head.row = nr
            for (i in 1 until ch.segs.size) { ch.segs[i].col = oldC[i - 1]; ch.segs[i].row = oldR[i - 1] }
        }
    }

    private fun moveBullets(dt: Float) {
        val it = bullets.iterator()
        while (it.hasNext()) {
            val b = it.next()
            val n = max(1, ceil(abs(b.vy) * dt / 1.5f).toInt())
            var removed = false
            for (k in 0 until n) {
                b.x += b.vx * dt / n
                b.y += b.vy * dt / n
                if (b.y < -2f || b.x < -2f || b.x > Arcade.W + 2f) { removed = true; break }
                if (hitSomething(b)) { removed = true; break }
            }
            if (removed) it.remove()
        }
    }

    private fun hitSomething(b: Bullet): Boolean {
        val owner = ships[b.owner]
        val col = (b.x / R.CELL).toInt()
        val row = (b.y / R.CELL).toInt()
        if (col in 0 until R.COLS && row in 0 until R.ROWS) {
            val i = R.idx(col, row)
            if (mush[i] > 0) {
                mush[i]--
                mushVersion++
                owner.score += if (mush[i] == 0) 5 else 1
                if (mush[i] == 0) burst(col * R.CELL + 2f, row * R.CELL + 2f, "mu")
                return true
            }
        }
        // the centipede
        for (ch in chains) {
            for ((k, sg) in ch.segs.withIndex()) {
                if (sg.col < 0) continue
                val cx = sg.col * R.CELL + 2f
                val cy = sg.row * R.CELL + 2f
                if (abs(b.x - cx) < 2.9f && abs(b.y - cy) < 2.9f) {
                    killSegment(ch, k, owner)
                    return true
                }
            }
        }
        // the spider
        if (spiderX > -50f && abs(b.x - spiderX) < 4.6f && abs(b.y - spiderY) < 3.4f) {
            val near = ships.drop(1).filter { it.alive }.minOfOrNull { abs(it.x - spiderX) + abs(it.y - spiderY) } ?: 60f
            owner.score += if (near < 22f) 900 else if (near < 44f) 600 else 300
            burst(spiderX, spiderY, "sp")
            spiderX = -100f
            spiderNext = t + random.nextLong(8_000, 13_000)
            when (random.nextInt(3)) {
                0 -> owner.rapidUntil = t + Arcade.POWER_MS
                1 -> owner.tripleUntil = t + Arcade.POWER_MS
                else -> if (owner.lives < Arcade.MAX_LIVES) owner.lives++ else owner.rapidUntil = t + Arcade.POWER_MS
            }
            burst(owner.x, owner.y - 6f, "pw")
            return true
        }
        // the flea
        if (fleaX > -50f && abs(b.x - fleaX) < 3.6f && abs(b.y - fleaY) < 3.6f) {
            fleaHp--
            if (fleaHp <= 0) {
                owner.score += 200
                burst(fleaX, fleaY, "fl")
                fleaX = -100f
                fleaNext = t + random.nextLong(12_000, 20_000)
            }
            return true
        }
        return false
    }

    private fun killSegment(ch: Chain, k: Int, owner: Ship) {
        val sg = ch.segs[k]
        owner.score += if (k == 0) 100 else 10
        burst(sg.col * R.CELL + 2f, sg.row * R.CELL + 2f, "cs")
        if (sg.row < R.ROWS) { mush[R.idx(sg.col, sg.row)] = 4; mushVersion++ }
        val front = ArrayList(ch.segs.subList(0, k))
        val back = ArrayList(ch.segs.subList(k + 1, ch.segs.size))
        chains.remove(ch)
        if (front.isNotEmpty()) chains.add(Chain(front, ch.dirX, ch.dirY))
        if (back.isNotEmpty()) chains.add(Chain(back, ch.dirX, ch.dirY))
    }

    private fun pests(dt: Float) {
        // the spider bounces around the bottom of the screen
        if (spiderX < -50f) {
            if (t >= spiderNext) {
                val fromLeft = random.nextBoolean()
                spiderX = if (fromLeft) -4f else 104f
                spiderY = random.nextFloat() * 18f + 112f
                spiderVx = (if (fromLeft) 1f else -1f) * (26f + 4f * level)
                spiderVy = if (random.nextBoolean()) 22f else -22f
            }
        } else {
            spiderX += spiderVx * dt
            spiderY += spiderVy * dt
            if (spiderY < R.SHIP_TOP - 2f) { spiderY = R.SHIP_TOP - 2f; spiderVy = abs(spiderVy) }
            if (spiderY > R.SHIP_BOTTOM + 2f) { spiderY = R.SHIP_BOTTOM + 2f; spiderVy = -abs(spiderVy) }
            if (random.nextFloat() < dt * 1.2f) spiderVy = -spiderVy
            if (spiderX < -8f || spiderX > 108f) { spiderX = -100f; spiderNext = t + random.nextLong(8_000, 13_000) }
        }
        // the flea drops straight down leaving mushrooms
        if (fleaX < -50f) {
            if (level >= 2 && t >= fleaNext) { fleaX = (random.nextInt(R.COLS) * R.CELL) + 2f; fleaY = 0f; fleaHp = 2 }
        } else {
            val prevRow = (fleaY / R.CELL).toInt()
            fleaY += (52f + 6f * level) * dt
            val row = (fleaY / R.CELL).toInt()
            if (row != prevRow && row in 1 until R.ZONE_ROW && random.nextInt(100) < 30) {
                val i = R.idx((fleaX / R.CELL).toInt().coerceIn(0, R.COLS - 1), row)
                if (mush[i] == 0) { mush[i] = 4; mushVersion++ }
            }
            if (fleaY > Arcade.H + 4f) { fleaX = -100f; fleaNext = t + random.nextLong(12_000, 20_000) }
        }
    }

    private fun collisions() {
        for (p in 1..2) {
            val s = ships[p]
            if (!s.alive || t < s.invulnUntil) continue
            var hit = false
            for (ch in chains) for (sg in ch.segs) {
                if (sg.col < 0) continue
                if (abs(sg.col * R.CELL + 2f - s.x) < 5f && abs(sg.row * R.CELL + 2f - s.y) < 5f) hit = true
            }
            if (spiderX > -50f && abs(spiderX - s.x) < 5.5f && abs(spiderY - s.y) < 5f) hit = true
            if (fleaX > -50f && abs(fleaX - s.x) < 4.5f && abs(fleaY - s.y) < 4.5f) hit = true
            if (hit) {
                s.lives--; s.alive = false; s.rapidUntil = 0L; s.tripleUntil = 0L
                s.respawnAt = t + Arcade.RESPAWN_MS
                burst(s.x, s.y, "pl")
                spiderX = -100f; fleaX = -100f
                spiderNext = t + random.nextLong(6_000, 10_000)
                fleaNext = t + random.nextLong(10_000, 16_000)
            }
        }
    }

    // ------------------------------------------------------------------ the picture sent to the other phone

    override fun hostSnapshot(): JSONObject {
        val segs = JSONArray()
        for (ch in chains) for ((k, sg) in ch.segs.withIndex()) segs.put(JSONArray().put(sg.id).put(sg.col).put(sg.row).put(if (k == 0) 1 else 0).put(ch.dirX))
        val o = JSONObject()
            .put("st", stage).put("w", level).put("W", levels)
            .put("sg", segs).put("sm", R.stepMs(level)).put("mv", mushVersion)
            .put("b", ArcadeCodec.bullets(bullets))
            .put("s1", ArcadeCodec.ship(ships[1], t)).put("s2", ArcadeCodec.ship(ships[2], t))
            .put("ev", ArcadeCodec.bursts(bursts, t))
            .put("rd", max(0L, stageUntil - t).toInt()).put("tm", elapsed)
            .put("ov", over).put("why", why)
        // the mushroom field is big, so it is only sent when it has changed (and now and then, to be safe)
        if (sentVersion != mushVersion || (t / 3000L) != lastMushSendSlot) {
            o.put("mu", String(CharArray(mush.size) { '0' + mush[it] }))
            sentVersion = mushVersion
            lastMushSendSlot = t / 3000L
        }
        if (spiderX > -50f) o.put("sp", JSONArray().put(Arcade.r1(spiderX)).put(Arcade.r1(spiderY)))
        if (fleaX > -50f) o.put("fl", JSONArray().put(Arcade.r1(fleaX)).put(Arcade.r1(fleaY)).put(fleaHp))
        return o
    }

    private var lastMushSendSlot = -1L

    override fun adopt(s: JSONObject) {
        val mu = if (s.has("mu")) s.getString("mu") else state.mush
        if (mu.isNotEmpty() && (mu.length != R.COLS * R.ROWS || mu.any { it !in "01234" })) return
        val ov = s.optBoolean("ov", false)
        val me1 = isHost
        val a = ArcadeCodec.readShip(s.optJSONArray(if (me1) "s1" else "s2"))
        val b = ArcadeCodec.readShip(s.optJSONArray(if (me1) "s2" else "s1"))
        val result = if (!ov) 0 else if (a.score > b.score) 1 else if (a.score < b.score) 2 else 3
        val segArr = s.optJSONArray("sg")
        val segs = ArrayList<SegView>()
        if (segArr != null) for (i in 0 until minOf(segArr.length(), 60)) {
            val e = segArr.optJSONArray(i) ?: continue
            segs += SegView(e.optInt(0), e.optInt(1).coerceIn(-30, R.COLS), e.optInt(2).coerceIn(0, R.ROWS), e.optInt(3) == 1, if (e.optInt(4, 1) < 0) -1 else 1)
        }
        val sp = s.optJSONArray("sp")
        val fl = s.optJSONArray("fl")
        state = CentipedeState(
            c = ArcadeCommon(iAmHost = isHost).copy(
                phase = if (ov) GPhase.OVER else GPhase.PLAYING, stage = s.optString("st", "PLAY").take(6),
                level = s.optInt("w", 1).coerceIn(1, 9), levels = s.optInt("W", 5).coerceIn(1, 9), mine = a, theirs = b,
                bullets = ArcadeCodec.readBullets(s.optJSONArray("b")), bursts = ArcadeCodec.readBursts(s.optJSONArray("ev")),
                readyMs = s.optInt("rd", 0).coerceIn(0, 10_000), elapsedMs = s.optLong("tm", 0L).coerceIn(0L, 10_000_000L),
                receivedAt = clock(), result = result, why = s.optString("why", "").take(8),
            ),
            segs = segs, mush = mu,
            spider = if (sp != null && sp.length() >= 2) sp.optDouble(0).toFloat() to sp.optDouble(1).toFloat() else null,
            flea = if (fl != null && fl.length() >= 3) Triple(fl.optDouble(0).toFloat(), fl.optDouble(1).toFloat(), fl.optInt(2).coerceIn(0, 3)) else null,
            stepMs = s.optInt("sm", 120).coerceIn(30, 400),
        )
    }

    override fun reset() {
        state = CentipedeState(ArcadeCommon(iAmHost = isHost))
    }
}

// ====================================================================== the computer player

class CentipedeBrain(private val e: CentipedeEngine, skill: Skill, random: Random) : ArcadeBrainBase(skill, random) {
    private var wobble = 0f
    private var wobbleAt = 0L

    override fun think(now: Long) {
        val s = e.state
        if (s.c.phase != GPhase.PLAYING) return
        val me = s.c.mine
        if (!me.alive) return
        if (now > wobbleAt) { wobble = random.nextFloat() * 2f - 1f; wobbleAt = now + random.nextLong(500, 1500) }
        // aim at the segment that is lowest on the screen (the most dangerous), or the spider when it is close
        val targets = ArrayList<Pair<Float, Float>>()
        for (g in s.segs) if (g.col >= 0) targets += (g.col * CentipedeRules.CELL + 2f) to (g.row * CentipedeRules.CELL + 2f)
        s.spider?.let { if (skill != Skill.EASY) targets += it }
        val lowest = targets.maxByOrNull { it.second }
        var x = lowest?.first ?: me.x
        var y = 128f
        // keep clear of anything about to touch us
        val threat = targets.filter { abs(it.first - me.x) < 9f && it.second > me.y - 14f }.minByOrNull { abs(it.first - me.x) }
        if (threat != null && skill != Skill.EASY) {
            x = if (threat.first >= me.x) me.x - 12f else me.x + 12f
            y = if (threat.second > me.y - 6f) max(CentipedeRules.SHIP_TOP + 2f, me.y - 10f) else y
        } else {
            x += wobble * (if (skill == Skill.EASY) 10f else if (skill == Skill.NORMAL) 4f else 1.5f)
        }
        e.input(x.coerceIn(5f, 95f), y, true)
    }
}
