package com.neil1454.drawsomething.core

import org.json.JSONArray
import org.json.JSONObject
import kotlin.random.Random

// ====================================================================================
// Minesweeper: minesweeper for two. Players take turns revealing squares on a shared grid.
//
// - A safe square scores points (a "0" square opens its neighbours too, so a lucky open earns a lot).
// - A bomb costs points and ends your turn, but the game goes on.
// - Some safe squares hide power-ups that trigger when revealed.
// - The game ends when every safe square is revealed. Highest score wins.
//
// The HOST's phone holds the real minefield and is the referee. The partner's phone only ever
// receives what has been revealed, so there is nothing to peek at.
// ====================================================================================

enum class MineSize(val label: String, val w: Int, val h: Int, val mines: Int, val items: Int) {
    SMALL("Small 6×6", 6, 6, 6, 3),
    MEDIUM("Medium 8×8", 8, 8, 12, 5),
    LARGE("Large 10×10", 10, 10, 20, 8),
    HUGE("Huge 12×12", 12, 12, 32, 12),
}

enum class MineItem(val code: Char, val emoji: String, val label: String, val blurb: String) {
    BONUS('B', "🎁", "Bonus", "+30 points"),
    SHIELD('S', "🛡️", "Shield", "blocks your next bomb"),
    DOUBLE('D', "✨", "Double", "your next reveal scores double"),
    EXTRA('E', "⏩", "Extra turn", "go again"),
    STEAL('T', "🧲", "Steal", "take 20 points from your opponent"),
    RADAR('R', "📡", "Radar", "flags a hidden bomb for both of you");

    companion object {
        fun of(c: Char): MineItem? = values().firstOrNull { it.code == c }
    }
}

object MineRules {
    const val SAFE_POINTS = 10   // per square revealed
    const val MINE_PENALTY = 25  // for hitting a bomb (never takes you below 0)
    const val DEFUSE_BONUS = 10  // for a bomb blocked by a shield
    const val BONUS_POINTS = 30
    const val STEAL_POINTS = 20
}

/** Something that happened on a move. Kinds: safe, safe2 (doubled), boom, block, item_<code>. */
data class MineEvent(val kind: String, val byHost: Boolean, val value: Int = 0, val count: Int = 0, val cell: Int = -1)

sealed class MineMsg {
    /** Host starts (or restarts) Minesweeper. */
    data class Setup(val size: Int, val hostFirst: Boolean) : MineMsg()

    /** Partner asks to reveal a square on their turn. */
    data class Pick(val x: Int, val y: Int) : MineMsg()

    /** The host's complete picture of the board after every move. */
    data class State(
        val view: String,    // per square: . hidden, 0-8 revealed, X exploded, x shield-blocked, F radar flag, M bomb (game over)
        val items: String,   // per square: . or a MineItem code on a revealed square
        val hostScore: Int,
        val clientScore: Int,
        val hostTurn: Boolean,
        val hostShield: Boolean,
        val clientShield: Boolean,
        val hostDouble: Boolean,
        val clientDouble: Boolean,
        val over: Boolean,
        val safeLeft: Int,
        val minesLeft: Int,
        val events: List<MineEvent>,
        val moves: Int = 0,                  // counts up with every move, so screens can animate each one once
        val fresh: List<Int> = emptyList(),  // squares that changed on this move
    ) : MineMsg()

    fun encode(): ByteArray = toJson().toString().toByteArray(Charsets.UTF_8)

    private fun toJson(): JSONObject = when (this) {
        is Setup -> JSONObject().put("t", "m_setup").put("size", size).put("hf", hostFirst)
        is Pick -> JSONObject().put("t", "m_pick").put("x", x).put("y", y)
        is State -> {
            val ev = JSONArray()
            events.forEach {
                ev.put(JSONObject().put("k", it.kind).put("h", it.byHost).put("v", it.value).put("c", it.count).put("ce", it.cell))
            }
            JSONObject().put("t", "m_state").put("mv", moves).put("fresh", JSONArray(fresh)).put("view", view).put("items", items)
                .put("hs", hostScore).put("cs", clientScore).put("ht", hostTurn)
                .put("hsh", hostShield).put("csh", clientShield).put("hd", hostDouble).put("cd", clientDouble)
                .put("over", over).put("left", safeLeft).put("mines", minesLeft).put("ev", ev)
        }
    }

    companion object {
        private const val VIEW_CHARS = ".012345678XxFM"
        private const val ITEM_CHARS = ".BSDETR"
        private const val MAX_CELLS = 400

        /** Null for anything implausible. Numbers are clamped; strings must use only the known characters. */
        fun decode(o: JSONObject): MineMsg? = try {
            when (o.getString("t")) {
                "m_setup" -> Setup(o.getInt("size").coerceIn(0, MineSize.values().size - 1), o.getBoolean("hf"))
                "m_pick" -> Pick(o.getInt("x").coerceIn(0, 99), o.getInt("y").coerceIn(0, 99))
                "m_state" -> {
                    val view = o.getString("view")
                    val items = o.getString("items")
                    if (view.length > MAX_CELLS || items.length != view.length ||
                        view.any { it !in VIEW_CHARS } || items.any { it !in ITEM_CHARS }
                    ) {
                        null
                    } else {
                        val arr = o.getJSONArray("ev")
                        val events = List(minOf(arr.length(), 30)) {
                            val e = arr.getJSONObject(it)
                            MineEvent(
                                e.getString("k").filter { c -> c.isLetterOrDigit() || c == '_' }.take(12),
                                e.getBoolean("h"), e.getInt("v").coerceIn(-5000, 5000), e.getInt("c").coerceIn(0, 1000),
                                e.optInt("ce", -1).coerceIn(-1, MAX_CELLS - 1),
                            )
                        }
                        val freshArr = o.optJSONArray("fresh")
                        val fresh = if (freshArr == null) emptyList() else List(minOf(freshArr.length(), 200)) {
                            freshArr.getInt(it).coerceIn(0, MAX_CELLS - 1)
                        }
                        State(
                            view, items, o.getInt("hs").coerceIn(0, 100_000), o.getInt("cs").coerceIn(0, 100_000),
                            o.getBoolean("ht"), o.getBoolean("hsh"), o.getBoolean("csh"),
                            o.getBoolean("hd"), o.getBoolean("cd"), o.getBoolean("over"),
                            o.getInt("left").coerceIn(0, MAX_CELLS), o.getInt("mines").coerceIn(0, MAX_CELLS), events,
                            o.optInt("mv", 0).coerceIn(0, 1_000_000), fresh,
                        )
                    }
                }
                else -> null
            }
        } catch (e: Exception) {
            null
        }
    }
}

enum class MinesPhase { LOBBY, PLAYING, OVER }

/** An event from my own point of view. */
data class MineNote(val byMe: Boolean, val kind: String, val value: Int, val count: Int, val cell: Int = -1)

data class MinesState(
    val isHost: Boolean,
    val myName: String,
    val theirName: String,
    val phase: MinesPhase = MinesPhase.LOBBY,
    val size: MineSize = MineSize.MEDIUM,
    val view: String = "",
    val items: String = "",
    val myScore: Int = 0,
    val theirScore: Int = 0,
    val myTurn: Boolean = false,
    val myShield: Boolean = false,
    val theirShield: Boolean = false,
    val myDouble: Boolean = false,
    val theirDouble: Boolean = false,
    val safeLeft: Int = 0,
    val minesLeft: Int = 0,
    val notes: List<MineNote> = emptyList(),
    /** Counts up with every move; animate when it changes. */
    val moves: Int = 0,
    /** Squares that changed on the latest move (opened, exploded, shield-blocked). */
    val fresh: List<Int> = emptyList(),
)

/**
 * One engine per phone. The host's engine also keeps the secret minefield and referees every
 * move; the partner's engine just sends its picks and shows what it is told.
 */
class MinesEngine(
    myName: String,
    theirName: String,
    private val isHost: Boolean,
    private val send: (MineMsg) -> Unit,
    private val random: Random = Random.Default,
) {
    var state = MinesState(isHost = isHost, myName = myName, theirName = theirName)
        private set

    var onChange: () -> Unit = {}

    private fun update(f: MinesState.() -> MinesState) {
        state = state.f()
        onChange()
    }

    // ---- host-only secret state
    private var size = MineSize.MEDIUM
    private var mines = BooleanArray(0)
    private var itemAt = arrayOfNulls<MineItem>(0)
    private var revealed = BooleanArray(0)
    private var exploded = BooleanArray(0)
    private var defused = BooleanArray(0)
    private var flagged = BooleanArray(0)
    private var generated = false
    private val score = IntArray(2) // 0 = host, 1 = partner
    private val shield = BooleanArray(2)
    private val doubled = BooleanArray(2)
    private var hostTurn = true
    private var revealedSafe = 0
    private var spentMines = 0 // exploded + defused
    private var moveNo = 0

    // ------------------------------------------------------------------ starting

    /** Host only: start a new game (also used for a rematch). */
    fun start(size: MineSize) {
        if (!isHost) return
        val hostFirst = random.nextBoolean()
        send(MineMsg.Setup(size.ordinal, hostFirst))
        initHost(size, hostFirst)
    }

    private fun initHost(newSize: MineSize, hostFirst: Boolean) {
        size = newSize
        val n = size.w * size.h
        mines = BooleanArray(n)
        itemAt = arrayOfNulls(n)
        revealed = BooleanArray(n)
        exploded = BooleanArray(n)
        defused = BooleanArray(n)
        flagged = BooleanArray(n)
        generated = false
        score.fill(0); shield.fill(false); doubled.fill(false)
        hostTurn = hostFirst
        revealedSafe = 0
        spentMines = 0
        moveNo = 0
        update {
            copy(
                phase = MinesPhase.PLAYING, size = newSize, view = ".".repeat(n), items = ".".repeat(n),
                moves = 0, fresh = emptyList(),
                myScore = 0, theirScore = 0, myTurn = hostFirst,
                myShield = false, theirShield = false, myDouble = false, theirDouble = false,
                safeLeft = n - newSize.mines, minesLeft = newSize.mines, notes = emptyList(),
            )
        }
    }

    private fun initClient(newSize: MineSize, hostFirst: Boolean) {
        val n = newSize.w * newSize.h
        update {
            copy(
                phase = MinesPhase.PLAYING, size = newSize, view = ".".repeat(n), items = ".".repeat(n),
                moves = 0, fresh = emptyList(),
                myScore = 0, theirScore = 0, myTurn = !hostFirst,
                myShield = false, theirShield = false, myDouble = false, theirDouble = false,
                safeLeft = n - newSize.mines, minesLeft = newSize.mines, notes = emptyList(),
            )
        }
    }

    fun backToLobby() {
        update { copy(phase = MinesPhase.LOBBY, notes = emptyList()) }
    }

    // ------------------------------------------------------------------ playing

    /** Reveal a square. Only works on my turn, on a hidden square. */
    fun pick(x: Int, y: Int) {
        val s = state
        if (s.phase != MinesPhase.PLAYING || !s.myTurn) return
        if (x !in 0 until s.size.w || y !in 0 until s.size.h) return
        if (s.view.getOrNull(y * s.size.w + x) != '.') return
        if (isHost) hostApply(byHost = true, x = x, y = y) else send(MineMsg.Pick(x, y))
    }

    fun onMessage(msg: MineMsg) {
        when (msg) {
            is MineMsg.Setup -> if (!isHost) initClient(MineSize.values()[msg.size], msg.hostFirst)
            is MineMsg.Pick -> if (isHost) hostApply(byHost = false, x = msg.x, y = msg.y)
            is MineMsg.State -> if (!isHost) applyState(msg)
        }
    }

    // ------------------------------------------------------------------ host: the rules

    private fun idx(x: Int, y: Int) = y * size.w + x

    private fun neighbours(i: Int): List<Int> {
        val x = i % size.w
        val y = i / size.w
        val out = ArrayList<Int>(8)
        for (dy in -1..1) for (dx in -1..1) {
            if (dx == 0 && dy == 0) continue
            val nx = x + dx
            val ny = y + dy
            if (nx in 0 until size.w && ny in 0 until size.h) out += idx(nx, ny)
        }
        return out
    }

    private fun adjacent(i: Int) = neighbours(i).count { mines[it] }

    /** Lay the mines AFTER the first pick so the first square (and its neighbours) is always safe. */
    private fun generate(firstX: Int, firstY: Int) {
        val first = idx(firstX, firstY)
        val forbidden = neighbours(first).toSet() + first
        val candidates = mines.indices.filter { it !in forbidden }.shuffled(random)
        candidates.take(size.mines).forEach { mines[it] = true }
        val safe = mines.indices.filter { !mines[it] }.shuffled(random)
        val kinds = MineItem.values().toList().shuffled(random)
        safe.take(size.items).forEachIndexed { n, cell -> itemAt[cell] = kinds[n % kinds.size] }
        generated = true
    }

    private fun totalSafe() = size.w * size.h - size.mines

    /** Reveal [start] and, if it is a "0", everything that opens up around it. */
    private fun flood(start: Int): List<Int> {
        val out = ArrayList<Int>()
        val queue = ArrayDeque<Int>()
        revealed[start] = true
        queue.add(start)
        while (queue.isNotEmpty()) {
            val c = queue.removeFirst()
            out += c
            if (adjacent(c) == 0) {
                for (n in neighbours(c)) {
                    if (!revealed[n] && !mines[n]) {
                        revealed[n] = true
                        queue.add(n)
                    }
                }
            }
        }
        return out
    }

    private fun hostApply(byHost: Boolean, x: Int, y: Int) {
        if (state.phase != MinesPhase.PLAYING || hostTurn != byHost) return
        if (x !in 0 until size.w || y !in 0 until size.h) return
        val i = idx(x, y)
        if (!generated) generate(x, y)
        if (revealed[i] || exploded[i] || defused[i] || flagged[i]) return

        val a = if (byHost) 0 else 1
        val o = 1 - a
        val events = ArrayList<MineEvent>()
        val fresh = ArrayList<Int>()
        var extraTurn = false

        if (mines[i]) {
            if (shield[a]) {
                shield[a] = false
                defused[i] = true
                spentMines++
                score[a] += MineRules.DEFUSE_BONUS
                events += MineEvent("block", byHost, MineRules.DEFUSE_BONUS, 0, i)
                fresh += i
            } else {
                exploded[i] = true
                spentMines++
                val penalty = minOf(score[a], MineRules.MINE_PENALTY)
                score[a] -= penalty
                events += MineEvent("boom", byHost, penalty, 0, i)
                fresh += i
            }
        } else {
            val opened = flood(i)
            revealedSafe += opened.size
            var points = opened.size * MineRules.SAFE_POINTS
            val wasDoubled = doubled[a]
            if (wasDoubled) {
                points *= 2
                doubled[a] = false
            }
            score[a] += points
            events += MineEvent(if (wasDoubled) "safe2" else "safe", byHost, points, opened.size, i)
            fresh += opened

            for (cell in opened) {
                when (itemAt[cell] ?: continue) {
                    MineItem.BONUS -> {
                        score[a] += MineRules.BONUS_POINTS
                        events += MineEvent("item_B", byHost, MineRules.BONUS_POINTS, 0, cell)
                    }
                    MineItem.SHIELD -> {
                        shield[a] = true
                        events += MineEvent("item_S", byHost, 0, 0, cell)
                    }
                    MineItem.DOUBLE -> {
                        doubled[a] = true
                        events += MineEvent("item_D", byHost, 0, 0, cell)
                    }
                    MineItem.EXTRA -> {
                        extraTurn = true
                        events += MineEvent("item_E", byHost, 0, 0, cell)
                    }
                    MineItem.STEAL -> {
                        val taken = minOf(score[o], MineRules.STEAL_POINTS)
                        score[o] -= taken
                        score[a] += taken
                        events += MineEvent("item_T", byHost, taken, 0, cell)
                    }
                    MineItem.RADAR -> {
                        val hidden = mines.indices.filter { mines[it] && !exploded[it] && !defused[it] && !flagged[it] }
                        var flaggedCell = -1
                        if (hidden.isNotEmpty()) {
                            flaggedCell = hidden.random(random)
                            flagged[flaggedCell] = true
                            fresh += flaggedCell
                        }
                        events += MineEvent("item_R", byHost, 0, 0, flaggedCell.takeIf { it >= 0 } ?: cell)
                    }
                }
            }
        }

        val over = revealedSafe >= totalSafe()
        if (!extraTurn) hostTurn = !hostTurn
        broadcast(over, events, fresh)
    }

    private fun broadcast(over: Boolean, events: List<MineEvent>, fresh: List<Int>) {
        val n = size.w * size.h
        val view = StringBuilder(n)
        val items = StringBuilder(n)
        for (c in 0 until n) {
            view.append(
                when {
                    revealed[c] -> '0' + adjacent(c)
                    exploded[c] -> 'X'
                    defused[c] -> 'x'
                    flagged[c] -> 'F'
                    over && mines[c] -> 'M'
                    else -> '.'
                },
            )
            items.append(if (revealed[c]) itemAt[c]?.code ?: '.' else '.')
        }
        val msg = MineMsg.State(
            view.toString(), items.toString(), score[0], score[1], hostTurn, shield[0], shield[1],
            doubled[0], doubled[1], over, totalSafe() - revealedSafe, size.mines - spentMines, events,
            ++moveNo, fresh.distinct().take(200),
        )
        send(msg)
        applyState(msg)
    }

    /** Used by both phones (the host applies its own broadcast, so both see identical data). */
    private fun applyState(m: MineMsg.State) {
        val n = state.size.w * state.size.h
        if (m.view.length != n) return
        update {
            copy(
                phase = if (m.over) MinesPhase.OVER else MinesPhase.PLAYING,
                view = m.view, items = m.items,
                myScore = if (isHost) m.hostScore else m.clientScore,
                theirScore = if (isHost) m.clientScore else m.hostScore,
                myTurn = m.hostTurn == isHost,
                myShield = if (isHost) m.hostShield else m.clientShield,
                theirShield = if (isHost) m.clientShield else m.hostShield,
                myDouble = if (isHost) m.hostDouble else m.clientDouble,
                theirDouble = if (isHost) m.clientDouble else m.hostDouble,
                safeLeft = m.safeLeft, minesLeft = m.minesLeft,
                notes = m.events.map { MineNote(it.byHost == isHost, it.kind, it.value, it.count, it.cell) },
                moves = m.moves, fresh = m.fresh,
            )
        }
    }

    // ---- for tests only: the host's secret board
    internal fun secretMines(): List<Int> = mines.indices.filter { mines[it] }
    internal fun secretSafe(): List<Int> = mines.indices.filter { !mines[it] && !revealed[it] }
    internal fun secretItem(cell: Int): MineItem? = itemAt[cell]
}
