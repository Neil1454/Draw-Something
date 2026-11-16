package com.neil1454.drawsomething.core

import org.json.JSONObject

/** The games in the app. The host picks one in the lobby. */
enum class GameKind(val label: String, val emoji: String, val blurb: String) {
    DRAW("Pictionary", "🎨", "One draws, the other guesses. Fast guesses score big."),
    MINES("Minesweeper", "💣", "Take turns clearing a minefield. Dodge bombs, grab power-ups."),
    SEA("Battleships", "🚢", "Hide your fleet, hunt theirs. Hit a ship and fire again."),
    DROP("Connect 4", "🔴", "The classic four-in-a-row. Drop discs, block, win. Power discs blow things up."),
    MEMORY("Pairs", "🃏", "Flip cards, find pairs, build streaks."),
    QUICK("Quick Draw", "⚡", "Wait for it… then tap first. Don't fall for the fake-out."),
    DICE("Pig", "🎲", "Push your luck: keep rolling to build points, but roll a 1 and you lose the lot."),
    FLIP("Othello", "⚫", "Also called Reversi. Outflank and flip their discs. Easy to learn, hard to master."),
    HILO("Higher or Lower", "🎴", "Will the next card be higher or lower? Climb the prize ladder."),
    TETRIS("Tetris", "🧱", "Falling blocks, side by side. Clear lines to send gift pieces to your rival."),
    HOLIDAY("Do you know your Holiday?", "🏝️", "A head-to-head quiz about where you're going. Quickest correct answer wins."),
    DRAUGHTS("Draughts", "♟️", "Also called Checkers. Jump their pieces, crown your kings. Captures are compulsory."),
    LUDO("Ludo", "🏠", "Also called Frustration. Roll a 6 to start, race round the board, and get home with the exact roll."),
    MONOPOLY("Monopoly", "🎩", "The UK property game on the London board: buy, build, trade and bankrupt your rival."),
    INVADERS("Space Invaders", "👾", "Two ships, one sky. Fly and fire together as the invaders march down. Five waves, shields, a mystery ship."),
    CENTIPEDE("Centipede", "🐛", "Blast the centipede as it winds down through the mushrooms. Watch out for the spider and the flea."),
    LIARSDICE("Liar's Dice", "🎭", "Bluff with hidden dice. Bid higher, or call your rival a liar."),
    KALAH("Mancala", "🌰", "Sow seeds round the board, grab extra turns and capture your rival's pits. Ancient and addictive."),
    GUESSWHO("Guess Who?", "🕵️", "Each pick a secret face, then take turns asking yes/no questions. First to name the other's face wins."),
    SHOWDOWN("Stakes Showdown", "🎰", "Play for the stakes you each put up. Every stake is its own contest, picked on turn, and the winner claims it."),
    BLACKJACK("Blackjack", "🂡", "Beat the dealer to 21. Double down, win 3 to 2 on blackjack, and switch on the coach for tips."),
    ARKANOID("Arkanoid", "🧱", "Bounce the ball and smash the bricks together. Catch power-ups: lasers, multi-ball, fireball."),
    HANGMAN("Hangman", "🪢", "Guess the secret word before the drawing is finished. Set words for each other, or race on a shared word."),
    NOUGHTS("Noughts and Crosses", "❌", "Three in a row wins. Play the classic grid, or Ultimate with nine boards in one. Best of 1, 3 or 5."),
}

/** Games that are switched on. The holiday quiz can be switched off in one place: see [Features]. */
fun GameKind.isAvailable(): Boolean = this != GameKind.HOLIDAY || Features.HOLIDAY

/** Needs a real partner (it wraps the other games), so it isn't offered in solo practice, Learn mode or the leaderboards. */
val GameKind.needsPartner: Boolean get() = this == GameKind.SHOWDOWN

/** Every game that should appear in menus. */
fun availableGames(): List<GameKind> = GameKind.values().filter { it.isAvailable() && !it.needsPartner }

/** The games the host can pick in the lobby (everything, including the ones that need a partner). */
fun lobbyGames(): List<GameKind> = GameKind.values().filter { it.isAvailable() }

/** Host -> partner: "I've picked this game" (so both lobbies show the same thing). */
object LobbyMsg {
    fun encodePick(kind: GameKind): ByteArray =
        JSONObject().put("t", "l_pick").put("g", kind.name).toString().toByteArray(Charsets.UTF_8)
}

/**
 * Everything that can arrive from the partner, whichever game it belongs to. Each game's
 * messages have their own prefix on the "t" field ("m_" Minesweeper, "s_" Battleships, "l_" lobby,
 * anything else is Draw Something). Unreadable or implausible packets decode to null.
 */
sealed class Packet {
    data class Lobby(val game: GameKind) : Packet()
    data class Draw(val msg: Msg) : Packet()
    data class Mines(val msg: MineMsg) : Packet()
    data class Sea(val msg: SeaMsg) : Packet()
    data class Sync(val msg: GMsg) : Packet()

    /** The other player's stakes (a hidden stake's words are blank until [reveal] is true). */
    data class Stakes(val list: List<TheirStake>, val reveal: Boolean) : Packet()

    companion object {
        fun decode(bytes: ByteArray): Packet? = try {
            if (bytes.size > Msg.MAX_BYTES) {
                null
            } else {
                val o = JSONObject(String(bytes, Charsets.UTF_8))
                val t = o.getString("t")
                when {
                    t == "l_stakes" || t == "l_reveal" -> StakeWire.decode(o, t == "l_reveal")
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
