package com.neil1454.drawsomething.core

import org.json.JSONArray
import org.json.JSONObject
import kotlin.random.Random

// ====================================================================================
// Battleships: battleships for two.
//
// Each player has a hidden fleet (placed randomly, with a shuffle button). Take turns firing at
// the other player's grid. A HIT lets you fire again. Each player also gets one Radar scan
// (counts ship squares in a 3x3 area, uses your turn) and one Cluster bomb (hits a plus-shaped
// area of 5 squares, uses your turn). Sink every enemy ship to win.
//
// Each phone keeps its OWN fleet secret and answers the other's shots, so fleets never travel.
// ====================================================================================

enum class SeaSize(val label: String, val w: Int, val h: Int, val fleet: List<Int>, val names: List<String>) {
    QUICK("Quick 8×8", 8, 8, listOf(4, 3, 3, 2), listOf("Battleship", "Cruiser", "Submarine", "Destroyer")),
    CLASSIC("Classic 10×10", 10, 10, listOf(5, 4, 3, 3, 2), listOf("Aircraft carrier", "Battleship", "Cruiser", "Submarine", "Destroyer")),
}

/** A ship as the screens draw it: which squares, which way, whether it is lost. Empty [cells] = not placed yet. */
data class SeaShip(val len: Int, val name: String, val cells: List<Int>, val horizontal: Boolean, val sunk: Boolean)

enum class SeaWeapon(val label: String, val emoji: String, val blurb: String) {
    SHOT("Shot", "🎯", "one square. A hit means fire again"),
    RADAR("Radar", "📡", "once: counts ship squares in a 3×3 area"),
    BOMB("Cluster bomb", "💥", "once: hits a plus-shaped area of 5 squares"),
}

sealed class SeaMsg {
    /** [auto] = fleets are placed at random for both players; otherwise each player places their own ships. */
    data class Setup(val size: Int, val hostFirst: Boolean, val auto: Boolean = true) : SeaMsg()
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
        /** For each ship just sunk: its length, its first square and 1 if it lies across (so the other screen can show the ship). */
        val sunkShips: List<Int> = emptyList(),
    ) : SeaMsg()

    fun encode(): ByteArray = toJson().toString().toByteArray(Charsets.UTF_8)

    private fun toJson(): JSONObject = when (this) {
        is Setup -> JSONObject().put("t", "s_setup").put("size", size).put("hf", hostFirst).put("auto", auto)
        Ready -> JSONObject().put("t", "s_ready")
        is Shoot -> JSONObject().put("t", "s_shoot").put("w", weapon).put("x", x).put("y", y)
        is Result -> JSONObject().put("t", "s_result").put("w", weapon).put("x", x).put("y", y)
            .put("cells", JSONArray(cells)).put("sunk", JSONArray(sunk)).put("sc", sunkCount)
            .put("radar", radar).put("over", over).put("ss", JSONArray(sunkShips))
    }

    companion object {
        private fun weaponName(s: String) = SeaWeapon.values().firstOrNull { it.name == s }?.name ?: SeaWeapon.SHOT.name

        fun decode(o: JSONObject): SeaMsg? = try {
            when (o.getString("t")) {
                "s_setup" -> Setup(o.getInt("size").coerceIn(0, SeaSize.values().size - 1), o.getBoolean("hf"), o.optBoolean("auto", true))
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
                        o.optJSONArray("ss").let { a -> if (a == null) emptyList() else List((minOf(a.length(), 15) / 3) * 3) { a.optInt(it, 0).coerceIn(0, 399) } },
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

/** One thing that happened on the latest shot, for the screen to animate. [mine] = on MY board. */
data class SeaFx(val mine: Boolean, val cell: Int, val hit: Boolean, val sunk: Boolean, val radar: Boolean = false)

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
    /** Counts up with every shot (either direction); animate when it changes. */
    val moves: Int = 0,
    val fx: List<SeaFx> = emptyList(),
    /** My ships, with where they are (placed ones only have squares). */
    val myShips: List<SeaShip> = emptyList(),
    /** Enemy ships I have sunk, so they can be shown on their board. */
    val enemySunk: List<SeaShip> = emptyList(),
    val allPlaced: Boolean = false,
    /** True when the fleets are placed at random for both players. */
    val auto: Boolean = true,
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
    private var fleet: MutableList<List<Int>> = ArrayList()   // per ship: its squares (empty until placed)
    private var incoming = BooleanArray(0)      // squares already shot at on MY board
    private var sunk = BooleanArray(0)          // per ship: sunk?

    // ------------------------------------------------------------------ setup

    /** Host only: start (or restart) a game. */
    fun start(size: SeaSize, auto: Boolean = true) {
        if (!isHost) return
        val first = random.nextBoolean()
        send(SeaMsg.Setup(size.ordinal, first, auto))
        beginPlacing(size, first, auto)
    }

    private fun beginPlacing(size: SeaSize, first: Boolean, auto: Boolean) {
        hostFirst = first
        val n = size.w * size.h
        incoming = BooleanArray(n)
        fleet = size.fleet.map { emptyList<Int>() }.toMutableList()
        shipOf = IntArray(n) { -1 }
        sunk = BooleanArray(fleet.size)
        update {
            copy(
                phase = SeaPhase.PLACING, size = size, enemyGrid = ".".repeat(n),
                iAmReady = false, theyAreReady = false, myTurn = false,
                radarLeft = true, bombLeft = true, weapon = SeaWeapon.SHOT, awaiting = false,
                shots = 0, hits = 0, theirShipsLeft = size.fleet.size, note = null,
                radarX = -1, radarY = -1, radarCount = -1, iWon = null, moves = 0, fx = emptyList(),
                enemySunk = emptyList(), auto = auto,
            )
        }
        if (auto) placeFleet() else rebuild()
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
        fleet = placed.toMutableList()
        sunk = BooleanArray(placed.size)
        rebuild()
    }

    /** Work out everything the screens need from the current ship positions. */
    private fun rebuild() {
        val size = state.size
        shipOf = IntArray(size.w * size.h) { -1 }
        fleet.forEachIndexed { i, cells -> cells.forEach { shipOf[it] = i } }
        if (sunk.size != fleet.size) sunk = BooleanArray(fleet.size)
        update {
            copy(
                myGrid = buildMyGrid(), myShips = buildShips(), allPlaced = fleet.all { it.isNotEmpty() },
                myShipsLeft = fleet.indices.count { fleet[it].isNotEmpty() && !sunk[it] },
            )
        }
    }

    private fun buildShips(): List<SeaShip> = fleet.mapIndexed { i, cells ->
        SeaShip(state.size.fleet[i], state.size.names[i], cells, cells.size < 2 || cells[1] - cells[0] == 1, sunk.getOrElse(i) { false })
    }

    /** Placing: put ship [i] with its first square at (x, y), lying across or down. False if it doesn't fit. */
    fun placeShip(i: Int, x: Int, y: Int, horizontal: Boolean): Boolean {
        val s = state
        if (s.phase != SeaPhase.PLACING || s.iAmReady || i !in fleet.indices) return false
        val len = s.size.fleet[i]
        if (x < 0 || y < 0 || (if (horizontal) x + len > s.size.w || y >= s.size.h else y + len > s.size.h || x >= s.size.w)) return false
        val cells = List(len) { k -> if (horizontal) y * s.size.w + x + k else (y + k) * s.size.w + x }
        if (cells.any { shipOf[it] >= 0 && shipOf[it] != i }) return false
        fleet[i] = cells
        rebuild()
        return true
    }

    /** Placing: pick a ship back up. */
    fun removeShip(i: Int) {
        if (state.phase != SeaPhase.PLACING || state.iAmReady || i !in fleet.indices) return
        fleet[i] = emptyList()
        rebuild()
    }

    /** Placing: pick every ship up. */
    fun clearFleet() {
        if (state.phase != SeaPhase.PLACING || state.iAmReady) return
        for (i in fleet.indices) fleet[i] = emptyList()
        rebuild()
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
        if (state.phase != SeaPhase.PLACING || state.iAmReady || !state.allPlaced) return
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
            is SeaMsg.Setup -> if (!isHost) beginPlacing(SeaSize.values()[msg.size], msg.hostFirst, msg.auto)
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
        val sunkInfo = ArrayList<Int>()
        var sunkNow = 0
        fleet.forEachIndexed { i, cells ->
            if (cells.isNotEmpty() && !sunk[i] && cells.all { incoming[it] }) {
                sunk[i] = true
                sunkNow++
                sunkCells += cells
                sunkInfo += cells.size; sunkInfo += cells.min(); sunkInfo += if (cells.size < 2 || cells[1] - cells[0] == 1) 1 else 0
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
        val shipsLeft = fleet.indices.count { fleet[it].isNotEmpty() && !sunk[it] }
        val over = shipsLeft == 0
        send(SeaMsg.Result(weapon.name, m.x, m.y, out, sunkCells, sunkNow, radar, over, sunkInfo))
        val shooterAgain = weapon == SeaWeapon.SHOT && anyHit && !over
        val effects = ArrayList<SeaFx>()
        var k = 0
        while (k + 2 < out.size) {
            val cell = out[k + 1] * s.size.w + out[k]
            effects += SeaFx(mine = true, cell = cell, hit = out[k + 2] == 1, sunk = cell in sunkCells)
            k += 3
        }
        if (weapon == SeaWeapon.RADAR) effects += SeaFx(true, m.y * s.size.w + m.x, hit = false, sunk = false, radar = true)
        update {
            copy(
                moves = moves + 1, fx = effects,
                myGrid = buildMyGrid(), myShips = buildShips(), myShipsLeft = shipsLeft,
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
        val revealed = ArrayList<SeaShip>(s.enemySunk)
        var q = 0
        while (q + 2 < m.sunkShips.size) {
            val len = m.sunkShips[q]
            val start = m.sunkShips[q + 1]
            val across = m.sunkShips[q + 2] == 1
            val step = if (across) 1 else s.size.w
            val last = start + (len - 1) * step
            val fits = len in 1..5 && start in 0 until n && last in 0 until n &&
                (if (across) start % s.size.w + len <= s.size.w else true)
            if (fits) {
                val used = revealed.map { it.name }
                val name = s.size.names.filterIndexed { idx, _ -> s.size.fleet[idx] == len }.firstOrNull { it !in used }
                    ?: s.size.names.firstOrNull { s.size.fleet[s.size.names.indexOf(it)] == len } ?: "Ship"
                revealed += SeaShip(len, name, List(len) { start + it * step }, across, true)
            }
            q += 3
        }
        val weapon = SeaWeapon.valueOf(m.weapon)
        val again = weapon == SeaWeapon.SHOT && anyHit && !m.over
        val left = (s.theirShipsLeft - m.sunkCount).coerceAtLeast(0)
        val effects = ArrayList<SeaFx>()
        var k = 0
        while (k + 2 < m.cells.size) {
            val cx = m.cells[k]
            val cy = m.cells[k + 1]
            if (cx < s.size.w && cy < s.size.h) {
                val cell = cy * s.size.w + cx
                effects += SeaFx(mine = false, cell = cell, hit = m.cells[k + 2] == 1, sunk = cell in m.sunk)
            }
            k += 3
        }
        if (weapon == SeaWeapon.RADAR && m.x < s.size.w && m.y < s.size.h) {
            effects += SeaFx(false, m.y * s.size.w + m.x, hit = false, sunk = false, radar = true)
        }
        update {
            copy(
                moves = moves + 1, fx = effects,
                enemyGrid = String(grid), awaiting = false, enemySunk = revealed,
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
