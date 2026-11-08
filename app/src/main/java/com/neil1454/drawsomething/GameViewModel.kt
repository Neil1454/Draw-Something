package com.neil1454.drawsomething

import android.app.Application
import android.content.Context
import android.os.SystemClock
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import com.neil1454.drawsomething.core.GameEngine
import com.neil1454.drawsomething.core.GameState
import com.neil1454.drawsomething.core.Msg
import com.neil1454.drawsomething.core.Phase
import com.neil1454.drawsomething.core.StrokeBoard
import com.neil1454.drawsomething.net.NearbyTransport
import com.neil1454.drawsomething.net.TransportEvent
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch

enum class Stage { HOME, HOSTING, SEARCHING, LOBBY, PLAYING }

data class NearbyHost(val id: String, val name: String)

data class UiState(
    val stage: Stage = Stage.HOME,
    val name: String = "",
    val hosts: List<NearbyHost> = emptyList(),
    val joinRequest: NearbyHost? = null,   // host side: someone wants to join
    val connectingTo: String? = null,      // joiner side: waiting for the host to accept
    val isHost: Boolean = false,
    val partnerName: String = "",
    val game: GameState? = null,
    val board: StrokeBoard? = null,
    val boardVersion: Int = 0,
    val notice: String? = null,
)

class GameViewModel(app: Application) : AndroidViewModel(app) {
    private val prefs = app.getSharedPreferences("draw_something", Context.MODE_PRIVATE)
    private val _ui = MutableStateFlow(UiState(name = prefs.getString("name", "").orEmpty()))
    val ui: StateFlow<UiState> = _ui

    private val transport = NearbyTransport(app) { onTransportEvent(it) }
    private var engine: GameEngine? = null
    private var tickJob: Job? = null

    // ---------------------------------------------------------------- menu actions

    fun setName(raw: String) {
        val name = raw.take(MAX_NAME)
        prefs.edit().putString("name", name).apply()
        _ui.update { it.copy(name = name, notice = null) }
    }

    fun clearNotice() = _ui.update { it.copy(notice = null) }
    fun showNotice(text: String) = _ui.update { it.copy(notice = text) }

    private fun cleanName(): String? {
        val n = _ui.value.name.trim()
        if (n.isEmpty()) {
            showNotice("Type your name first.")
            return null
        }
        return n
    }

    fun host() {
        val name = cleanName() ?: return
        teardown()
        _ui.update { UiState(stage = Stage.HOSTING, name = it.name, isHost = true) }
        transport.startHosting(name)
    }

    fun join() {
        val name = cleanName() ?: return
        teardown()
        _ui.update { UiState(stage = Stage.SEARCHING, name = it.name, isHost = false) }
        transport.startDiscovery(name)
    }

    fun requestJoin(host: NearbyHost) {
        _ui.update { it.copy(connectingTo = host.name) }
        transport.connect(host.id)
    }

    fun acceptJoin() {
        val req = _ui.value.joinRequest ?: return
        transport.accept(req.id)
        _ui.update { it.copy(joinRequest = null) }
    }

    fun declineJoin() {
        val req = _ui.value.joinRequest ?: return
        transport.reject(req.id)
        _ui.update { it.copy(joinRequest = null) }
    }

    /** Back to the menu from anywhere (also disconnects). */
    fun leave() {
        teardown()
        _ui.update { UiState(name = it.name) }
    }

    /** Run something on the game engine (draw, guess, judge...). */
    fun game(block: GameEngine.() -> Unit) {
        engine?.block()
    }

    // ---------------------------------------------------------------- connection events

    private fun onTransportEvent(e: TransportEvent) {
        when (e) {
            is TransportEvent.HostFound -> _ui.update { s ->
                s.copy(hosts = s.hosts.filter { it.id != e.id } + NearbyHost(e.id, e.name))
            }

            is TransportEvent.HostLost -> _ui.update { s -> s.copy(hosts = s.hosts.filter { it.id != e.id }) }

            is TransportEvent.ConnectionRequest ->
                if (e.incoming) _ui.update { it.copy(joinRequest = NearbyHost(e.id, e.name)) }
                else transport.accept(e.id) // we asked to join; the host still has to say yes

            is TransportEvent.Connected -> startSession(e.name)

            is TransportEvent.Disconnected -> {
                val s = _ui.value
                if (s.stage == Stage.LOBBY || s.stage == Stage.PLAYING) {
                    val who = s.partnerName.ifBlank { "Your partner" }
                    teardown()
                    _ui.update { UiState(name = it.name, notice = "$who disconnected.") }
                } else if (s.connectingTo != null) {
                    _ui.update { it.copy(connectingTo = null, notice = "The host said no, or the connection dropped.") }
                }
            }

            is TransportEvent.Failure -> _ui.update { it.copy(connectingTo = null, notice = e.message) }

            is TransportEvent.Received -> Msg.decode(e.bytes)?.let { engine?.onMessage(it) }
        }
    }

    private fun startSession(partner: String) {
        transport.stopLooking()
        val s = _ui.value
        val e = GameEngine(
            myName = s.name.trim(),
            theirName = partner,
            isHost = s.isHost,
            send = { transport.send(it.encode()) },
            clock = { SystemClock.elapsedRealtime() },
        )
        engine = e
        e.onChange = { publish(e) }
        tickJob?.cancel()
        tickJob = viewModelScope.launch {
            while (isActive) {
                delay(TICK_MS)
                e.tick()
            }
        }
        _ui.update {
            it.copy(
                stage = Stage.LOBBY, partnerName = partner, joinRequest = null, connectingTo = null,
                hosts = emptyList(), notice = null, game = e.state, board = e.board, boardVersion = e.board.version,
            )
        }
    }

    private fun publish(e: GameEngine) {
        _ui.update {
            if (it.stage != Stage.LOBBY && it.stage != Stage.PLAYING) it
            else it.copy(
                stage = if (e.state.phase == Phase.LOBBY) Stage.LOBBY else Stage.PLAYING,
                game = e.state,
                boardVersion = e.board.version,
            )
        }
    }

    private fun teardown() {
        tickJob?.cancel()
        tickJob = null
        engine?.onChange = {}
        engine = null
        transport.stopAll()
    }

    override fun onCleared() {
        teardown()
    }

    private companion object {
        const val MAX_NAME = 20
        const val TICK_MS = 100L
    }
}
