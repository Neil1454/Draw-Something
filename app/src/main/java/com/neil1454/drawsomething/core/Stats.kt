package com.neil1454.drawsomething.core

import org.json.JSONArray
import org.json.JSONObject

// ====================================================================================
// Score tracking, per-game leaderboards and the overall "Dominator" ranking.
// Pure Kotlin so it can be tested; StatsStore (Android) just saves/loads the JSON.
//
// Both phones record every finished game for BOTH players (by name), so the boards agree.
// ====================================================================================

/** How a finished game went, from this phone's point of view. [result]: 1 I won, 2 I lost, 3 draw. */
data class Outcome(val kind: GameKind, val myPoints: Int, val theirPoints: Int, val result: Int)

object Outcomes {
    /** The outcome if [state] is a finished game of any kind, otherwise null. */
    fun of(state: Any?): Outcome? = when (state) {
        is GameState -> if (state.phase == Phase.GAME_OVER) by(GameKind.DRAW, state.myScore, state.theirScore) else null
        is MinesState -> if (state.phase == MinesPhase.OVER) by(GameKind.MINES, state.myScore, state.theirScore) else null
        is SeaState -> if (state.phase == SeaPhase.OVER && state.iWon != null) {
            Outcome(GameKind.SEA, state.hits, 0, if (state.iWon) 1 else 2)
        } else null
        is DropState -> sync(state.phase, GameKind.DROP, if (state.result == 1) 1 else 0, if (state.result == 2) 1 else 0, state.result)
        is FlipState -> sync(state.phase, GameKind.FLIP, state.myCount, state.theirCount, state.result)
        is MemoryState -> sync(state.phase, GameKind.MEMORY, state.myScore, state.theirScore, state.result)
        is QuickState -> sync(state.phase, GameKind.QUICK, state.myScore, state.theirScore, state.result)
        is DiceState -> sync(state.phase, GameKind.DICE, state.myScore, state.theirScore, state.result)
        is HiLoState -> sync(state.phase, GameKind.HILO, state.myScore, state.theirScore, state.matchResult)
        is HolidayState -> sync(state.phase, GameKind.HOLIDAY, state.myScore, state.theirScore, state.result)
        else -> null
    }

    private fun by(kind: GameKind, mine: Int, theirs: Int) =
        Outcome(kind, mine, theirs, if (mine > theirs) 1 else if (mine < theirs) 2 else 3)

    private fun sync(phase: GPhase, kind: GameKind, mine: Int, theirs: Int, result: Int): Outcome? =
        if (phase == GPhase.OVER && result in 1..3) Outcome(kind, mine, theirs, result) else null
}

/** One player's record in one game. */
class Rec {
    var played = 0
    var won = 0
    var lost = 0
    var drawn = 0
    var points = 0
    var rating = 0

    fun add(points: Int, result: Int) {
        played++
        when (result) {
            1 -> won++
            2 -> lost++
            else -> drawn++
        }
        this.points += points.coerceIn(0, StatsRules.MAX_POINTS)
        rating += StatsRules.rating(points, result)
    }

    fun mergeFrom(o: Rec) {
        played += o.played; won += o.won; lost += o.lost; drawn += o.drawn; points += o.points; rating += o.rating
    }
}

object StatsRules {
    const val WIN = 100
    const val DRAW = 40
    const val LOSS = 10
    const val POINT_BONUS_CAP = 25
    const val MAX_POINTS = 100_000
    const val MAX_PLAYERS = 100
    const val MAX_RECENT = 40
    const val MAX_NAME = 20

    /** The Dominator rating a single game earns: result + a little for the points you scored. */
    fun rating(points: Int, result: Int): Int {
        val base = when (result) { 1 -> WIN; 3 -> DRAW; else -> LOSS }
        return base + (points / 10).coerceIn(0, POINT_BONUS_CAP)
    }
}

/** One row of a leaderboard. */
data class BoardRow(
    val name: String,
    val played: Int,
    val won: Int,
    val drawn: Int,
    val lost: Int,
    val points: Int,
    val rating: Int,
) {
    val winPercent get() = if (played == 0) 0 else (won * 100 + played / 2) / played
}

/** A finished game, for the "recent games" list. */
data class Recent(val kind: GameKind, val me: String, val opp: String, val result: Int, val myPoints: Int, val theirPoints: Int, val at: Long)

class StatsBook {
    private class Player(var display: String) {
        val games = HashMap<GameKind, Rec>()
        fun rec(kind: GameKind) = games.getOrPut(kind) { Rec() }
    }

    private val players = LinkedHashMap<String, Player>()
    private val recentList = ArrayList<Recent>()

    val recent: List<Recent> get() = recentList.toList()
    val playerCount get() = players.size

    private fun key(name: String) = name.trim().take(StatsRules.MAX_NAME).lowercase()

    private fun player(name: String): Player? {
        val n = name.trim().take(StatsRules.MAX_NAME)
        if (n.isEmpty()) return null
        val k = n.lowercase()
        players[k]?.let { it.display = n; return it }
        if (players.size >= StatsRules.MAX_PLAYERS) return null
        return Player(n).also { players[k] = it }
    }

    /**
     * Record a finished game between [me] and [opp]. With [recordOpponent] false (playing the
     * computer) only my side is kept, so the computer never appears on the boards.
     */
    fun record(me: String, opp: String, o: Outcome, at: Long = 0L, recordOpponent: Boolean = true) {
        if (key(me).isEmpty() || key(me) == key(opp)) return
        player(me)?.rec(o.kind)?.add(o.myPoints, o.result)
        if (recordOpponent) {
            val flipped = when (o.result) { 1 -> 2; 2 -> 1; else -> 3 }
            player(opp)?.rec(o.kind)?.add(o.theirPoints, flipped)
        }
        recentList.add(0, Recent(o.kind, me.trim().take(StatsRules.MAX_NAME), opp.trim().take(StatsRules.MAX_NAME), o.result, o.myPoints, o.theirPoints, at))
        while (recentList.size > StatsRules.MAX_RECENT) recentList.removeAt(recentList.size - 1)
    }

    private fun row(p: Player, rec: Rec) = BoardRow(p.display, rec.played, rec.won, rec.drawn, rec.lost, rec.points, rec.rating)

    /** Best first: most wins, then best rating, then points, then name. */
    private val order = compareByDescending<BoardRow> { it.won }
        .thenByDescending { it.rating }
        .thenByDescending { it.points }
        .thenBy { it.name.lowercase() }

    fun board(kind: GameKind): List<BoardRow> =
        players.values.mapNotNull { p -> p.games[kind]?.takeIf { it.played > 0 }?.let { row(p, it) } }.sortedWith(order)

    /** Everyone, ranked by Dominator rating across every game. */
    fun dominator(): List<BoardRow> =
        players.values.mapNotNull { p ->
            val all = Rec()
            p.games.values.forEach { all.mergeFrom(it) }
            if (all.played > 0) row(p, all) else null
        }.sortedWith(
            compareByDescending<BoardRow> { it.rating }.thenByDescending { it.won }.thenByDescending { it.points }.thenBy { it.name.lowercase() },
        )

    fun isEmpty() = players.isEmpty()

    fun clear() {
        players.clear()
        recentList.clear()
    }

    // ------------------------------------------------------------------ saving

    fun toJson(): String {
        val ps = JSONArray()
        players.values.forEach { p ->
            val g = JSONObject()
            p.games.forEach { (k, r) -> g.put(k.name, JSONArray(listOf(r.played, r.won, r.lost, r.drawn, r.points, r.rating))) }
            ps.put(JSONObject().put("n", p.display).put("g", g))
        }
        val rs = JSONArray()
        recentList.forEach { r ->
            rs.put(JSONArray(listOf(r.kind.name, r.me, r.opp, r.result, r.myPoints, r.theirPoints, r.at)))
        }
        return JSONObject().put("v", 1).put("p", ps).put("r", rs).toString()
    }

    companion object {
        /** Never throws: damaged or hostile data gives an empty (or partly filled) book. */
        fun fromJson(text: String?): StatsBook {
            val book = StatsBook()
            if (text.isNullOrBlank() || text.length > 2_000_000) return book
            try {
                val o = JSONObject(text)
                val ps = o.optJSONArray("p") ?: JSONArray()
                for (i in 0 until minOf(ps.length(), StatsRules.MAX_PLAYERS)) {
                    val po = ps.optJSONObject(i) ?: continue
                    val p = book.player(po.optString("n", "")) ?: continue
                    val g = po.optJSONObject("g") ?: continue
                    for (kind in GameKind.values()) {
                        val a = g.optJSONArray(kind.name) ?: continue
                        if (a.length() < 6) continue
                        val r = p.rec(kind)
                        r.played = a.optInt(0).coerceIn(0, 1_000_000)
                        r.won = a.optInt(1).coerceIn(0, 1_000_000)
                        r.lost = a.optInt(2).coerceIn(0, 1_000_000)
                        r.drawn = a.optInt(3).coerceIn(0, 1_000_000)
                        r.points = a.optInt(4).coerceIn(0, 100_000_000)
                        r.rating = a.optInt(5).coerceIn(0, 100_000_000)
                    }
                }
                val rs = o.optJSONArray("r") ?: JSONArray()
                for (i in 0 until minOf(rs.length(), StatsRules.MAX_RECENT)) {
                    val a = rs.optJSONArray(i) ?: continue
                    val kind = GameKind.values().firstOrNull { it.name == a.optString(0) } ?: continue
                    book.recentList.add(
                        Recent(
                            kind, a.optString(1, "").take(StatsRules.MAX_NAME), a.optString(2, "").take(StatsRules.MAX_NAME),
                            a.optInt(3).coerceIn(1, 3), a.optInt(4).coerceIn(0, 100_000), a.optInt(5).coerceIn(0, 100_000), a.optLong(6),
                        ),
                    )
                }
            } catch (e: Exception) {
                // keep whatever was read
            }
            return book
        }
    }
}
