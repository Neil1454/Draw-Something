package com.neil1454.drawsomething.core

import org.json.JSONObject

/** The games in the app. The host picks one in the lobby. */
enum class GameKind(val label: String, val emoji: String, val blurb: String) {
    DRAW("Draw Something", "🎨", "One draws, the other guesses. Fast guesses score big."),
    MINES("Bomb Find", "💣", "Take turns clearing a minefield. Dodge bombs, grab power-ups."),
    SEA("Sea Battle", "🚢", "Hide your fleet, hunt theirs. Hit a ship and fire again."),
    DROP("Drop Four", "🔴", "The classic four-in-a-row. Drop discs, block, win. Power discs blow things up."),
    MEMORY("Memory Match", "🃏", "Flip cards, find pairs, build streaks."),
    QUICK("Quick Draw", "⚡", "Wait for it… then tap first. Don't fall for the fake-out."),
    DICE("Lucky Dice", "🎲", "Push your luck. Bank it or bust."),
    FLIP("Flip Fight", "⚫", "Outflank and flip their discs. Easy to learn, hard to master."),
    HILO("Higher or Lower", "🎴", "Will the next card be higher or lower? Climb the prize ladder."),
    TETRIS("Tetris", "🧱", "Falling blocks, side by side. Clear lines to send gift pieces to your rival."),
    HOLIDAY("Do you know your Holiday?", "🏝️", "A head-to-head quiz about where you're going. Quickest correct answer wins."),
}

/** Games that are switched on. The holiday quiz can be switched off in one place: see [Features]. */
fun GameKind.isAvailable(): Boolean = this != GameKind.HOLIDAY || Features.HOLIDAY

/** Every game that should appear in menus. */
fun availableGames(): List<GameKind> = GameKind.values().filter { it.isAvailable() }

/** Host -> partner: "I've picked this game" (so both lobbies show the same thing). */
object LobbyMsg {
    fun encodePick(kind: GameKind): ByteArray =
        JSONObject().put("t", "l_pick").put("g", kind.name).toString().toByteArray(Charsets.UTF_8)
}

/**
 * Everything that can arrive from the partner, whichever game it belongs to. Each game's
 * messages have their own prefix on the "t" field ("m_" Bomb Find, "s_" Sea Battle, "l_" lobby,
 * anything else is Draw Something). Unreadable or implausible packets decode to null.
 */
sealed class Packet {
    data class Lobby(val game: GameKind) : Packet()
    data class Draw(val msg: Msg) : Packet()
    data class Mines(val msg: MineMsg) : Packet()
    data class Sea(val msg: SeaMsg) : Packet()
    data class Sync(val msg: GMsg) : Packet()

    companion object {
        fun decode(bytes: ByteArray): Packet? = try {
            if (bytes.size > Msg.MAX_BYTES) {
                null
            } else {
                val o = JSONObject(String(bytes, Charsets.UTF_8))
                val t = o.getString("t")
                when {
                    t == "l_pick" -> GameKind.values().firstOrNull { it.name == o.getString("g") }?.let { Lobby(it) }
                    t.startsWith("m_") -> MineMsg.decode(o)?.let { Mines(it) }
                    t.startsWith("s_") -> SeaMsg.decode(o)?.let { Sea(it) }
                    t.startsWith("g_") -> GMsg.decode(o)?.let { Sync(it) }
                    else -> Msg.decodeJson(o)?.let { Draw(it) }
                }
            }
        } catch (e: Exception) {
            null
        }
    }
}
