package com.neil1454.drawsomething.core

import org.json.JSONArray
import org.json.JSONObject
import kotlin.random.Random

// ====================================================================================
// Sea Battle: battleships for two.
//
// Each player has a hidden fleet (placed randomly, with a shuffle button). Take turns firing at
// the other player's grid. A HIT lets you fire again. Each player also gets one Radar scan
// (counts ship squares in a 3x3 area, uses your turn) and one Cluster bomb (hits a plus-shaped
// area of 5 squares, uses your turn). Sink every enemy ship to win.
//
// Each phone keeps its OWN fleet secret and answers the other's shots, so fleets never travel.
// ====================================================================================

enum class SeaSize(val label: String, val w: Int, val h: Int, val fleet: List<Int>) {
    QUICK("Quick 8×8", 8, 8, listOf(4, 3, 3, 2)),
    CLASSIC("Classic 10×10", 10, 10, listOf(5, 4, 3, 3, 2)),
}

enum class SeaWeapon(val label: String, val emoji: String, val blurb: String) {
    SHOT("Shot", "🎯", "one square. A hit means fire again"),
    RADAR("Radar", "📡", "once: counts ship squares in a 3×3 area"),
    BOMB("Cluster bomb", "💥", "once: hits a plus-shaped area of 5 squares"),
}

sealed class SeaMsg {
    data class Setup(val size: Int, val hostFirst: Boolean) : SeaMsg()
    data object Ready : SeaMsg()
    data class Shoot(val weapon: String, val x: Int, val y: Int) : SeaMsg()

    /** [cells] is a flat list of x, y, outcome (0 miss, 1 hit); [sunk] the squares of ships just sunk. */
    data class Result(
        val weapon: String,
        val x: Int,
        val y: Int,
        val cells: List<Int>,
        val sunk: List<Int>,
        val sunkCount: Int,
        val radar: Int,
        val over: Boolean,
    ) : SeaMsg()

    fun encode(): ByteArray = toJson().toString().toByteArray(Charsets.UTF_8)

    private fun toJson(): JSONObject = when (this) {
        is Setup -> JSONObject().put("t", "s_setup").put("size", size).put("hf", hostFirst)
        Ready -> JSONObject().put("t", "s_ready")
        is Shoot -> JSONObject().put("t", "s_shoot").put("w", weapon).put("x", x).put("y", y)
        is Result -> JSONObject().put("t", "s_result").put("w", weapon).put("x", x).put("y", y)
            .put("cells", JSONArray(cells)).put("sunk", JSONArray(sunk)).put("sc", sunkCount)
            .put("radar", radar).put("over", over)
    }

    companion object {
        private fun weaponName(s: String) = SeaWeapon.values().firstOrNull { it.name == s }?.name ?: SeaWeapon.SHOT.name

        fun decode(o: JSONObject): SeaMsg? = try {
            when (o.getString("t")) {
                "s_setup" -> Setup(o.getInt("size").coerceIn(0, SeaSize.values().size - 1), o.getBoolean("hf"))
                "s_ready" -> Ready
                "s_shoot" -> Shoot(weaponName(o.getString("w")), o.getInt("x").coerceIn(0, 99), o.getInt("y").coerceIn(0, 99))
                "s_result" -> {
                    val cells = o.getJSONArray("cells")
                    val sunk = o.getJSONArray("sunk")
                    Result(
                        weaponName(o.getString("w")), o.getInt("x").coerceIn(0, 99), o.getInt("y").coerceIn(0, 99),
                        List((minOf(cells.length(), 30) / 3) * 3) { cells.getInt(it).coerceIn(0, 99) },
                        List(minOf(sunk.length(), 200)) { sunk.getInt(it).coerceIn(0, 399) },
                        o.getInt("sc").coerceIn(0, 10), o.getInt("radar").coerceIn(0, 9), o.getBoolean("over"),
                    )
                }
                else -> null
            }
        } catch (e: Exception) {
            null
        }
    }
}

enum class SeaPhase { LOBBY, PLACING, BATTLE, OVER }

data class SeaState(
    val isHost: Boolean,
    val myName: String,
    val theirName: String,
    val phase: SeaPhase = SeaPhase.LOBBY,
    val size: SeaSize = SeaSize.CLASSIC,
    /** My own board: . water, S my ship, x hit on me, o miss. */
    val myGrid: String = "",
    /** The enemy board as I know it: . unknown, o miss, x hit, # sunk ship. */
    val enemyGrid: String = "",
    val myTurn: Boolean = false,
    val iAmReady: Boolean = false,
    val theyAreReady: Boolean = false,
    val radarLeft: Boolean = true,
    val bombLeft: Boolean = true,
    val weapon: SeaWeapon = SeaWeapon.SHOT,
    val awaiting: Boolean = false,
    val shots: Int = 0,
    val hits: Int = 0,
    val myShipsLeft: Int = 0,
    val theirShipsLeft: Int = 0,
    val note: String? = null,
    val radarX: Int = -1,
    val radarY: Int = -1,
    val radarCount: Int = -1,
    val iWon: Boolean? = null,
)

class SeaEngine(
    myName: String,
    theirName: String,
    private val isHost: Boolean,
    private val send: (SeaMsg) -> Unit,
    private val random: Random = Random.Default,
) {
    var state = SeaState(isHost = isHost, myName = myName, theirName = theirName)
        private set

    var onChange: () -> Unit = {}

    private fun update(f: SeaState.() -> SeaState) {
        state = state.f()
        onChange()
    }

    private var hostFirst = true
    private var shipOf = IntArray(0)            // per square: ship index or -1
    private var fleet: List<List<Int>> = emptyList()
    private var incoming = BooleanArray(0)      // squares already shot at on MY board
    private var sunk = BooleanArray(0)          // per ship: sunk?

    // ------------------------------------------------------------------ setup

    /** Host only: start (or restart) a game. */
    fun start(size: SeaSize) {
        if (!isHost) return
        val first = random.nextBoolean()
        send(SeaMsg.Setup(size.ordinal, first))
        beginPlacing(size, first)
    }

    private fun beginPlacing(size: SeaSize, first: Boolean) {
        hostFirst = first
        val n = size.w * size.h
        incoming = BooleanArray(n)
        update {
            copy(
                phase = SeaPhase.PLACING, size = size, enemyGrid = ".".repeat(n),
                iAmReady = false, theyAreReady = false, myTurn = false,
                radarLeft = true, bombLeft = true, weapon = SeaWeapon.SHOT, awaiting = false,
                shots = 0, hits = 0, theirShipsLeft = size.fleet.size, note = null,
                radarX = -1, radarY = -1, radarCount = -1, iWon = null,
            )
        }
        placeFleet()
    }

    private fun placeFleet() {
        val size = state.size
        val w = size.w
        val h = size.h
        var placed: List<List<Int>> = emptyList()
        do {
            val occupied = BooleanArray(w * h)
            val ships = ArrayList<List<Int>>()
            var ok = true
            for (len in size.fleet) {
                var cells: List<Int>? = null
                repeat(300) {
                    if (cells != null) return@repeat
                    val horizontal = random.nextBoolean()
                    val x = random.nextInt(if (horizontal) w - len + 1 else w)
                    val y = random.nextInt(if (horizontal) h else h - len + 1)
                    val candidate = List(len) { k -> if (horizontal) y * w + x + k else (y + k) * w + x }
                    if (candidate.none { occupied[it] }) cells = candidate
                }
                val c = cells
                if (c == null) {
                    ok = false
                    break
                }
                c.forEach { occupied[it] = true }
                ships += c
            }
            placed = if (ok) ships else emptyList()
        } while (!ok)
        fleet = placed
        shipOf = IntArray(w * h) { -1 }
        placed.forEachIndexed { s, cells -> cells.forEach { shipOf[it] = s } }
        sunk = BooleanArray(placed.size)
        update { copy(myGrid = buildMyGrid(), myShipsLeft = placed.size) }
    }

    private fun buildMyGrid(): String {
        val sb = StringBuilder(shipOf.size)
        for (c in shipOf.indices) {
            sb.append(
                when {
                    shipOf[c] >= 0 -> if (incoming[c]) 'x' else 'S'
                    incoming[c] -> 'o'
                    else -> '.'
                },
            )
        }
        return sb.toString()
    }

    /** Placing: lay the fleet out again. */
    fun shuffleFleet() {
        if (state.phase != SeaPhase.PLACING || state.iAmReady) return
        placeFleet()
    }

    /** Placing: "my fleet is ready". Battle starts when both are. */
    fun ready() {
        if (state.phase != SeaPhase.PLACING || state.iAmReady) return
        send(SeaMsg.Ready)
        update { copy(iAmReady = true) }
        maybeBegin()
    }

    private fun maybeBegin() {
        val s = state
        if (s.phase == SeaPhase.PLACING && s.iAmReady && s.theyAreReady) {
            update { copy(phase = SeaPhase.BATTLE, myTurn = hostFirst == isHost) }
        }
    }

    fun backToLobby() {
        update { copy(phase = SeaPhase.LOBBY, note = null) }
    }

    // ------------------------------------------------------------------ firing

    fun selectWeapon(w: SeaWeapon) {
        val s = state
        val allowed = when (w) {
            SeaWeapon.SHOT -> true
            SeaWeapon.RADAR -> s.radarLeft
            SeaWeapon.BOMB -> s.bombLeft
        }
        if (allowed && s.phase == SeaPhase.BATTLE) update { copy(weapon = w) }
    }

    private fun targetsOf(weapon: SeaWeapon, x: Int, y: Int, size: SeaSize): List<Int> = when (weapon) {
        SeaWeapon.SHOT -> listOf(y * size.w + x)
        SeaWeapon.RADAR -> emptyList()
        SeaWeapon.BOMB -> listOf(x to y, x - 1 to y, x + 1 to y, x to y - 1, x to y + 1)
            .filter { (px, py) -> px in 0 until size.w && py in 0 until size.h }
            .map { (px, py) -> py * size.w + px }
    }

    /** Fire the selected weapon at (x, y) on the enemy grid. */
    fun fire(x: Int, y: Int) {
        val s = state
        if (s.phase != SeaPhase.BATTLE || !s.myTurn || s.awaiting) return
        if (x !in 0 until s.size.w || y !in 0 until s.size.h) return
        val unknown = { cell: Int -> s.enemyGrid[cell] == '.' }
        when (s.weapon) {
            SeaWeapon.SHOT -> if (!unknown(y * s.size.w + x)) return
            SeaWeapon.BOMB -> if (targetsOf(SeaWeapon.BOMB, x, y, s.size).none(unknown)) return
            SeaWeapon.RADAR -> Unit
        }
        send(SeaMsg.Shoot(s.weapon.name, x, y))
        update {
            copy(
                awaiting = true, note = null,
                radarLeft = radarLeft && weapon != SeaWeapon.RADAR,
                bombLeft = bombLeft && weapon != SeaWeapon.BOMB,
                weapon = SeaWeapon.SHOT,
            )
        }
    }

    // ------------------------------------------------------------------ incoming

    fun onMessage(msg: SeaMsg) {
        when (msg) {
            is SeaMsg.Setup -> if (!isHost) beginPlacing(SeaSize.values()[msg.size], msg.hostFirst)
            SeaMsg.Ready -> {
                update { copy(theyAreReady = true) }
                maybeBegin()
            }
            is SeaMsg.Shoot -> defend(msg)
            is SeaMsg.Result -> applyResult(msg)
        }
    }

    /** I'm being shot at: work out what happened on MY board and answer honestly. */
    private fun defend(m: SeaMsg.Shoot) {
        val s = state
        if (s.phase != SeaPhase.BATTLE || s.myTurn) return // not their turn
        if (m.x !in 0 until s.size.w || m.y !in 0 until s.size.h) return
        val weapon = SeaWeapon.valueOf(m.weapon)
        val out = ArrayList<Int>()
        var anyHit = false
        for (t in targetsOf(weapon, m.x, m.y, s.size)) {
            if (incoming[t]) continue
            incoming[t] = true
            val hit = shipOf[t] >= 0
            anyHit = anyHit || hit
            out += t % s.size.w; out += t / s.size.w; out += if (hit) 1 else 0
        }
        val sunkCells = ArrayList<Int>()
        var sunkNow = 0
        fleet.forEachIndexed { i, cells ->
            if (!sunk[i] && cells.all { incoming[it] }) {
                sunk[i] = true
                sunkNow++
                sunkCells += cells
            }
        }
        var radar = 0
        if (weapon == SeaWeapon.RADAR) {
            for (dy in -1..1) for (dx in -1..1) {
                val px = m.x + dx
                val py = m.y + dy
                if (px in 0 until s.size.w && py in 0 until s.size.h) {
                    val c = py * s.size.w + px
                    if (shipOf[c] >= 0 && !incoming[c]) radar++
                }
            }
        }
        val shipsLeft = sunk.count { !it }
        val over = shipsLeft == 0
        send(SeaMsg.Result(weapon.name, m.x, m.y, out, sunkCells, sunkNow, radar, over))
        val shooterAgain = weapon == SeaWeapon.SHOT && anyHit && !over
        update {
            copy(
                myGrid = buildMyGrid(), myShipsLeft = shipsLeft,
                myTurn = !shooterAgain && !over,
                phase = if (over) SeaPhase.OVER else phase,
                iWon = if (over) false else iWon,
                note = describeIncoming(weapon, anyHit, sunkNow, radar, over),
            )
        }
    }

    private fun describeIncoming(w: SeaWeapon, hit: Boolean, sunkNow: Int, radar: Int, over: Boolean): String = when {
        over -> "${state.theirName} sank your whole fleet!"
        sunkNow > 0 -> "${state.theirName} sank one of your ships!"
        w == SeaWeapon.RADAR -> "${state.theirName} scanned your waters with radar."
        w == SeaWeapon.BOMB -> if (hit) "${state.theirName}'s cluster bomb hit you!" else "${state.theirName}'s cluster bomb missed."
        hit -> "${state.theirName} hit your ship and fires again!"
        else -> "${state.theirName} missed."
    }

    /** I fired and this is the answer. */
    private fun applyResult(m: SeaMsg.Result) {
        val s = state
        if (s.phase != SeaPhase.BATTLE || !s.awaiting) return
        val n = s.size.w * s.size.h
        val grid = s.enemyGrid.toCharArray()
        var anyHit = false
        var hitCount = 0
        var i = 0
        while (i + 2 < m.cells.size) {
            val cx = m.cells[i]
            val cy = m.cells[i + 1]
            val hit = m.cells[i + 2] == 1
            val c = cy * s.size.w + cx
            if (cx < s.size.w && cy < s.size.h && c in 0 until n) {
                grid[c] = if (hit) 'x' else 'o'
                if (hit) {
                    anyHit = true
                    hitCount++
                }
            }
            i += 3
        }
        m.sunk.forEach { if (it in 0 until n) grid[it] = '#' }
        val weapon = SeaWeapon.valueOf(m.weapon)
        val again = weapon == SeaWeapon.SHOT && anyHit && !m.over
        val left = (s.theirShipsLeft - m.sunkCount).coerceAtLeast(0)
        update {
            copy(
                enemyGrid = String(grid), awaiting = false,
                shots = shots + m.cells.size / 3, hits = hits + hitCount, theirShipsLeft = left,
                myTurn = again,
                phase = if (m.over) SeaPhase.OVER else phase,
                iWon = if (m.over) true else iWon,
                radarX = if (weapon == SeaWeapon.RADAR) m.x else radarX,
                radarY = if (weapon == SeaWeapon.RADAR) m.y else radarY,
                radarCount = if (weapon == SeaWeapon.RADAR) m.radar else radarCount,
                note = when {
                    m.over -> "You sank the whole enemy fleet!"
                    m.sunkCount > 0 -> "You sank a ship!"
                    weapon == SeaWeapon.RADAR -> "Radar: ${m.radar} ship square${if (m.radar == 1) "" else "s"} in that area."
                    weapon == SeaWeapon.BOMB -> if (anyHit) "Cluster bomb: $hitCount hit${if (hitCount == 1) "" else "s"}!" else "Cluster bomb missed."
                    anyHit -> "Hit! Fire again."
                    else -> "Miss."
                },
            )
        }
    }

    // ---- for tests only
    internal fun fleetCells(): List<List<Int>> = fleet
}
