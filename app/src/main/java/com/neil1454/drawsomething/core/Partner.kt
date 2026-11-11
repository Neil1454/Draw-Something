package com.neil1454.drawsomething.core

import kotlin.random.Random

/**
 * One phone's worth of game engines plus a computer brain for each, talking through the same messages
 * a real phone would. In SOLO play one of these is the opponent (the guest side); in LEARN mode two of
 * them play each other. Messages are queued and handled on [tick] so nothing ever runs in the middle
 * of another engine's update.
 */
class PlayerSide(
    val isHost: Boolean,
    val myName: String,
    theirName: String,
    val skill: Skill,
    private val sendBytes: (ByteArray) -> Unit,
    private val secretWord: () -> String?,
    private val clock: () -> Long,
    random: Random = Random.Default,
    /** Quiz only: the right answer to the question on screen, as known by the host's phone. */
    holidayKey: () -> Int? = { null },
) {
    private val inbox = ArrayDeque<ByteArray>()

    val draw = GameEngine(myName, theirName, isHost, { sendBytes(it.encode()) }, clock, random)
    val mines = MinesEngine(myName, theirName, isHost, { sendBytes(it.encode()) }, random)
    val sea = SeaEngine(myName, theirName, isHost, { sendBytes(it.encode()) }, random)
    private val send: (GMsg) -> Unit = { sendBytes(it.encode()) }
    val drop = DropEngine(myName, theirName, isHost, send, random, clock)
    val memory = MemoryEngine(myName, theirName, isHost, send, random, clock)
    val quick = QuickEngine(myName, theirName, isHost, send, random, clock)
    val dice = DiceEngine(myName, theirName, isHost, send, random, clock)
    val flip = FlipEngine(myName, theirName, isHost, send, random, clock)
    val hilo = HiLoEngine(myName, theirName, isHost, send, random, clock)
    val holiday = HolidayEngine(myName, theirName, isHost, send, random, clock)
    val tetris = TetrisEngine(myName, theirName, isHost, send, random, clock)
    val draughts = DraughtsEngine(myName, theirName, isHost, send, random, clock)
    val ludo = LudoEngine(myName, theirName, isHost, send, random, clock)
    val monopoly = MonopolyEngine(myName, theirName, isHost, send, random, clock)

    private val syncByKind: Map<GameKind, SyncEngine> = mapOf(
        GameKind.DROP to drop, GameKind.MEMORY to memory, GameKind.QUICK to quick,
        GameKind.DICE to dice, GameKind.FLIP to flip, GameKind.HILO to hilo, GameKind.HOLIDAY to holiday, GameKind.TETRIS to tetris,
        GameKind.DRAUGHTS to draughts, GameKind.LUDO to ludo, GameKind.MONOPOLY to monopoly,
    )

    /** The Draw Something guesser only makes sense on the guest side (the host is the drawer). */
    private val brains: List<Brain> = listOfNotNull(
        if (!isHost) DrawBrain(draw, secretWord, skill, random) else null,
        MinesBrain(mines, skill, random),
        SeaBrain(sea, skill, random),
        DropBrain(drop, skill, random),
        MemoryBrain(memory, skill, random),
        QuickBrain(quick, skill, random),
        DiceBrain(dice, skill, random),
        FlipBrain(flip, skill, random),
        HiLoBrain(hilo, skill, random),
        HolidayBrain(holiday, isHost, holidayKey, skill, random),
        TetrisBrain(tetris, skill, random),
        DraughtsBrain(draughts, skill, random),
        LudoBrain(ludo, skill, random),
        MonopolyBrain(monopoly, skill, random),
    )

    /** The computer agrees to the Draw Something rules straight away. */
    fun start() {
        draw.agreeRules()
    }

    /** A message from the other phone. */
    fun receive(bytes: ByteArray) {
        inbox.add(bytes)
    }

    /** Handle waiting messages, then let each brain think. Call about every 100 ms. */
    fun tick(now: Long) {
        while (inbox.isNotEmpty()) route(inbox.removeFirst())
        syncByKind.values.forEach { it.tick() }
        brains.forEach { it.think(now) }
    }

    fun engine(kind: GameKind): Any = when (kind) {
        GameKind.DRAW -> draw
        GameKind.MINES -> mines
        GameKind.SEA -> sea
        else -> syncByKind.getValue(kind)
    }

    private fun resetOthers(keep: GameKind) {
        if (keep != GameKind.DRAW) draw.backToLobby()
        if (keep != GameKind.MINES) mines.backToLobby()
        if (keep != GameKind.SEA) sea.backToLobby()
        syncByKind.forEach { (k, e) -> if (k != keep) e.backToLobby() }
    }

    private fun route(bytes: ByteArray) {
        when (val p = Packet.decode(bytes) ?: return) {
            is Packet.Lobby -> Unit
            is Packet.Draw -> {
                if (p.msg is Msg.Setup) resetOthers(GameKind.DRAW)
                draw.onMessage(p.msg)
            }
            is Packet.Mines -> {
                if (p.msg is MineMsg.Setup) resetOthers(GameKind.MINES)
                mines.onMessage(p.msg)
            }
            is Packet.Sea -> {
                if (p.msg is SeaMsg.Setup) resetOthers(GameKind.SEA)
                sea.onMessage(p.msg)
            }
            is Packet.Sync -> {
                val kind = GameKind.values().firstOrNull { it.name == p.msg.game } ?: return
                if (p.msg is GMsg.Setup) resetOthers(kind)
                syncByKind[kind]?.onMessage(p.msg)
            }
        }
    }
}

/** Two computer-controlled phones playing each other on a virtual clock (tests, and Learn mode). */
class Duel(seed: Int, hostSkill: Skill = Skill.NORMAL, guestSkill: Skill = Skill.NORMAL) {
    var now = 0L
        private set
    private val random = Random(seed)
    lateinit var host: PlayerSide
        private set
    lateinit var guest: PlayerSide
        private set

    init {
        host = PlayerSide(true, "Player 1", "Player 2", hostSkill, { guest.receive(it) }, { guest.draw.board.strokes.size.let { null } }, { now }, Random(seed), holidayKey = { null })
        guest = PlayerSide(false, "Player 2", "Player 1", guestSkill, { host.receive(it) }, { host.draw.state.word?.text }, { now }, Random(seed + 1), holidayKey = { host.holiday.secretCorrect(2) })
        guest.start()
        host.start()
        advance(0)
    }

    /** Move virtual time forward, letting both phones think and exchange messages. */
    fun advance(ms: Long, step: Long = 50) {
        var left = ms
        do {
            val d = minOf(step, left)
            now += d
            left -= d
            host.tick(now)
            guest.tick(now)
        } while (left > 0)
    }
}
