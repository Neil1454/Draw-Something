package com.neil1454.drawsomething.core

import org.json.JSONObject
import kotlin.random.Random

/**
 * The shared plumbing for the turn-based "sync" games (Drop Four, Memory Match, Quick Draw,
 * Lucky Dice, Flip Fight).
 *
 * The HOST's phone is the referee: it holds the real game, applies every move (its own and the
 * partner's), and broadcasts a full snapshot after each change. The partner's phone only sends
 * its moves and displays the latest snapshot. That keeps the two screens identical, keeps hidden
 * information (Memory Match's cards) off the partner's phone, and means the rules live in one place.
 */
sealed class GMsg {
    abstract val game: String

    /** Host starts a game with these options. */
    class Setup(override val game: String, val opts: JSONObject) : GMsg()

    /** Partner asks to do something on their turn. */
    class Act(override val game: String, val a: JSONObject) : GMsg()

    /** The host's complete picture of the game. */
    class Snap(override val game: String, val s: JSONObject) : GMsg()

    fun encode(): ByteArray {
        val (t, body) = when (this) {
            is Setup -> "g_setup" to opts
            is Act -> "g_act" to a
            is Snap -> "g_snap" to s
        }
        return JSONObject().put("t", t).put("g", game).put("b", body).toString().toByteArray(Charsets.UTF_8)
    }

    companion object {
        /** Null unless it names a real game and carries a JSON body. */
        fun decode(o: JSONObject): GMsg? = try {
            val game = o.getString("g")
            if (GameKind.values().none { it.name == game }) {
                null
            } else {
                val body = o.getJSONObject("b")
                when (o.getString("t")) {
                    "g_setup" -> Setup(game, body)
                    "g_act" -> Act(game, body)
                    "g_snap" -> Snap(game, body)
                    else -> null
                }
            }
        } catch (e: Exception) {
            null
        }
    }
}

enum class GPhase { LOBBY, PLAYING, OVER }

abstract class SyncEngine(
    val kind: GameKind,
    val myName: String,
    val theirName: String,
    val isHost: Boolean,
    private val send: (GMsg) -> Unit,
    protected val random: Random = Random.Default,
    protected val clock: () -> Long = { System.currentTimeMillis() },
) {
    /** Called after anything changes. */
    var onChange: () -> Unit = {}

    /** The current immutable state, for the screens. */
    abstract val stateAny: Any
    abstract val phase: GPhase
    val isActive get() = phase != GPhase.LOBBY

    // ---- what each game provides
    protected abstract fun hostBegin(opts: JSONObject)
    protected abstract fun hostHandle(fromHost: Boolean, a: JSONObject)
    protected abstract fun hostSnapshot(): JSONObject
    protected abstract fun adopt(s: JSONObject)
    protected abstract fun reset()
    protected open fun hostTick(now: Long) {}

    /** Host only: start (or restart) a game. */
    fun start(opts: JSONObject = JSONObject()) {
        if (!isHost) return
        send(GMsg.Setup(kind.name, opts))
        hostBegin(opts)
        publish()
    }

    /** Send the world to the partner and show it here too. */
    protected fun publish() {
        val snap = hostSnapshot()
        send(GMsg.Snap(kind.name, snap))
        adopt(snap)
        onChange()
    }

    /** Do something (play a move). The host applies it directly; the partner asks the host. */
    fun act(a: JSONObject) {
        if (isHost) guarded { hostHandle(true, a) } else send(GMsg.Act(kind.name, a))
    }

    /** Called about ten times a second. */
    fun tick() {
        if (isHost && isActive) guarded { hostTick(clock()) }
    }

    fun backToLobby() {
        reset()
        onChange()
    }

    fun onMessage(m: GMsg) {
        if (m.game != kind.name) return
        guarded {
            when (m) {
                is GMsg.Setup -> Unit // the snapshot follows
                is GMsg.Act -> if (isHost) hostHandle(false, m.a)
                is GMsg.Snap -> if (!isHost) {
                    adopt(m.s)
                    onChange()
                }
            }
        }
    }

    /** A damaged or hostile message must never crash the app. */
    private fun guarded(block: () -> Unit) {
        try {
            block()
        } catch (e: Exception) {
            // ignore: bad JSON, out-of-range numbers, etc.
        }
    }

    protected fun JSONObject.ints(key: String, max: Int = 1000): List<Int> {
        val arr = optJSONArray(key) ?: return emptyList()
        return List(minOf(arr.length(), max)) { arr.optInt(it, 0) }
    }
}
