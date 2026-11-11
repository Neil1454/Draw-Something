package com.neil1454.drawsomething.core

import org.json.JSONArray
import org.json.JSONObject
import kotlin.math.abs
import kotlin.random.Random

// ====================================================================================
// Shared pieces for the two-player arcade games (Space Invaders and Centipede).
// Both players are on the same screen at the same time. The host's phone runs the whole game about 30 times a second and
// sends the picture to the other phone; the other player only sends where their finger is and whether it is firing.
// The field is 100 wide and 140 tall; the players' ships live at the bottom.
// ====================================================================================

object Arcade {
    const val W = 100f
    const val H = 140f
    const val STEP_MS = 33L
    const val SHIP_SPEED = 90f
    const val BULLET_SPEED = 125f
    const val MAX_LIVES = 5
    const val START_LIVES = 3
    const val RESPAWN_MS = 1500L
    const val INVULN_MS = 2600L
    const val POWER_MS = 9000L
    const val READY_MS = 2200L

    fun r1(v: Float): Double = Math.round(v * 10f) / 10.0
}

/** One player's ship. Player 1 is the host. */
class Ship(var x: Float, var y: Float) {
    var tx = x
    var ty = y
    var firing = false
    var lives = Arcade.START_LIVES
    var score = 0
    var alive = true
    var respawnAt = 0L
    var invulnUntil = 0L
    var rapidUntil = 0L
    var tripleUntil = 0L
    var cooldown = 0f

    fun reset(x: Float, y: Float) {
        this.x = x; this.y = y; tx = x; ty = y; firing = false; lives = Arcade.START_LIVES; score = 0; alive = true
        respawnAt = 0L; invulnUntil = 0L; rapidUntil = 0L; tripleUntil = 0L; cooldown = 0f
    }

    /** Glide towards where the finger is, no faster than the ship can fly, staying in the allowed band. */
    fun fly(dt: Float, minY: Float, maxY: Float) {
        val step = Arcade.SHIP_SPEED * dt
        val dx = tx - x
        val dy = ty.coerceIn(minY, maxY) - y
        x += dx.coerceIn(-step, step)
        y += dy.coerceIn(-step, step)
        x = x.coerceIn(3f, Arcade.W - 3f)
        y = y.coerceIn(minY, maxY)
    }

    fun flags(now: Long): Int =
        (if (alive) 1 else 0) or (if (now < invulnUntil) 2 else 0) or (if (now < rapidUntil) 4 else 0) or (if (now < tripleUntil) 8 else 0)
}

class Bullet(var x: Float, var y: Float, var vx: Float, var vy: Float, val owner: Int, var kind: Int = 0)

/** Something that happened a moment ago (an explosion, a power-up...), sent so both screens can show it once. */
class Burst(val id: Int, val x: Float, val y: Float, val kind: String, val at: Long)

/** A ship as the screens see it. */
data class ShipView(
    val x: Float = 50f,
    val y: Float = 130f,
    val lives: Int = Arcade.START_LIVES,
    val score: Int = 0,
    val alive: Boolean = true,
    val invulnerable: Boolean = false,
    val rapid: Boolean = false,
    val triple: Boolean = false,
)

data class BulletView(val x: Float, val y: Float, val vx: Float, val vy: Float, val owner: Int, val kind: Int)
data class BurstView(val id: Int, val x: Float, val y: Float, val kind: String)

object ArcadeCodec {
    fun ship(s: Ship, now: Long): JSONArray = JSONArray()
        .put(Arcade.r1(s.x)).put(Arcade.r1(s.y)).put(s.lives).put(s.score).put(s.flags(now))

    fun readShip(a: JSONArray?): ShipView {
        if (a == null || a.length() < 5) return ShipView()
        val f = a.optInt(4, 1)
        return ShipView(
            x = a.optDouble(0, 50.0).toFloat().coerceIn(0f, Arcade.W), y = a.optDouble(1, 130.0).toFloat().coerceIn(0f, Arcade.H),
            lives = a.optInt(2, 0).coerceIn(0, 9), score = a.optInt(3, 0).coerceIn(0, 10_000_000),
            alive = f and 1 != 0, invulnerable = f and 2 != 0, rapid = f and 4 != 0, triple = f and 8 != 0,
        )
    }

    fun bullets(list: List<Bullet>, limit: Int = 60): JSONArray {
        val a = JSONArray()
        for (b in list.take(limit)) a.put(JSONArray().put(Arcade.r1(b.x)).put(Arcade.r1(b.y)).put(Arcade.r1(b.vx)).put(Arcade.r1(b.vy)).put(b.owner).put(b.kind))
        return a
    }

    fun readBullets(a: JSONArray?, limit: Int = 80): List<BulletView> {
        if (a == null) return emptyList()
        val out = ArrayList<BulletView>()
        for (i in 0 until minOf(a.length(), limit)) {
            val b = a.optJSONArray(i) ?: continue
            if (b.length() < 6) continue
            out += BulletView(
                b.optDouble(0).toFloat().coerceIn(-5f, 105f), b.optDouble(1).toFloat().coerceIn(-10f, 150f),
                b.optDouble(2).toFloat().coerceIn(-300f, 300f), b.optDouble(3).toFloat().coerceIn(-300f, 300f),
                b.optInt(4, 1).coerceIn(0, 2), b.optInt(5, 0).coerceIn(0, 9),
            )
        }
        return out
    }

    fun bursts(list: List<Burst>, now: Long): JSONArray {
        val a = JSONArray()
        for (e in list) if (now - e.at < 700L) a.put(JSONArray().put(e.id).put(Arcade.r1(e.x)).put(Arcade.r1(e.y)).put(e.kind))
        return a
    }

    fun readBursts(a: JSONArray?): List<BurstView> {
        if (a == null) return emptyList()
        val out = ArrayList<BurstView>()
        for (i in 0 until minOf(a.length(), 24)) {
            val b = a.optJSONArray(i) ?: continue
            out += BurstView(b.optInt(0), b.optDouble(1).toFloat().coerceIn(0f, 100f), b.optDouble(2).toFloat().coerceIn(0f, 140f), b.optString(3, "ex").take(6))
        }
        return out
    }
}

/**
 * The state every arcade game shares: who is where, scores, what just exploded. Each game adds its own enemies on top.
 */
data class ArcadeCommon(
    val phase: GPhase = GPhase.LOBBY,
    /** READY (get set), PLAY, WAVE (a level was cleared), END. */
    val stage: String = "READY",
    val level: Int = 1,
    val levels: Int = 5,
    val iAmHost: Boolean = true,
    val mine: ShipView = ShipView(),
    val theirs: ShipView = ShipView(),
    val bullets: List<BulletView> = emptyList(),
    val bursts: List<BurstView> = emptyList(),
    val readyMs: Int = 0,
    val elapsedMs: Long = 0,
    /** When this picture arrived (this phone's clock), so bullets can be drawn where they have got to since. */
    val receivedAt: Long = 0,
    /** 0 playing, 1 I won, 2 they won, 3 draw. */
    val result: Int = 0,
    /** Why it ended: victory, invaded, lives, time. */
    val why: String = "",
)

/** Shared behaviour for the computer player. */
abstract class ArcadeBrainBase(skill: Skill, random: Random) : Brain(skill, random) {
    protected fun pickNear(options: List<Float>, from: Float): Float? = options.minByOrNull { abs(it - from) }
}
