package com.neil1454.drawsomething

import android.app.Application
import android.content.Context
import android.os.SystemClock
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import com.neil1454.drawsomething.core.Difficulty
import com.neil1454.drawsomething.core.ReleaseInfo
import com.neil1454.drawsomething.update.UpdateCheck
import com.neil1454.drawsomething.update.UpdateManager
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.io.File
import com.neil1454.drawsomething.core.DiceEngine
import com.neil1454.drawsomething.core.DropEngine
import com.neil1454.drawsomething.core.FlipEngine
import com.neil1454.drawsomething.core.GMsg
import com.neil1454.drawsomething.core.MemoryEngine
import com.neil1454.drawsomething.core.QuickEngine
import com.neil1454.drawsomething.core.SyncEngine
import org.json.JSONObject
import com.neil1454.drawsomething.core.GameEngine
import com.neil1454.drawsomething.core.GameKind
import com.neil1454.drawsomething.core.GameState
import com.neil1454.drawsomething.core.LobbyMsg
import com.neil1454.drawsomething.core.MineMsg
import com.neil1454.drawsomething.core.MineSize
import com.neil1454.drawsomething.core.MinesEngine
import com.neil1454.drawsomething.core.MinesPhase
import com.neil1454.drawsomething.core.MinesState
import com.neil1454.drawsomething.core.Msg
import com.neil1454.drawsomething.core.Packet
import com.neil1454.drawsomething.core.PlayerSide
import com.neil1454.drawsomething.core.Skill
import com.neil1454.drawsomething.core.Phase
import com.neil1454.drawsomething.core.SeaEngine
import com.neil1454.drawsomething.core.SeaMsg
import com.neil1454.drawsomething.core.SeaSize
import com.neil1454.drawsomething.core.SeaPhase
import com.neil1454.drawsomething.core.SeaState
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

/** The "Check for updates" journey. */
sealed class UpdateUi {
    data object Idle : UpdateUi()
    data object Checking : UpdateUi()
    data class UpToDate(val version: String) : UpdateUi()
    data class Available(val info: ReleaseInfo) : UpdateUi()
    data class Downloading(val info: ReleaseInfo, val progress: Float) : UpdateUi()
    data class NeedPermission(val info: ReleaseInfo) : UpdateUi()
    data class Installing(val info: ReleaseInfo) : UpdateUi()
    data class Failed(val message: String) : UpdateUi()
}

data class NearbyHost(val id: String, val name: String)

/** A device trying to pair, with the 4-digit code both screens must show. */
data class PairRequest(val id: String, val name: String, val code: String)

/** The host's game setup. Kept in the view model so it survives from the waiting screen into the lobby. */
data class LobbySettings(
    val rounds: Int = 6,
    val seconds: Int = 90,
    val difficulty: Int = 0, // index into the lobby's difficulty choices
    val minesSize: Int = 1,  // index into MineSize.values()
    val seaSize: Int = 1,    // index into SeaSize.values()
    val dropSize: Int = 0,
    val dropPower: Boolean = false,
    val dropTheme: Int = 0,
    val memorySize: Int = 0,
    val memoryTheme: Int = 0,
    val quickMatch: Int = 1,
    val quickFake: Boolean = true,
    val diceTarget: Int = 1,
    val diceTwo: Boolean = false,
    val flipSize: Int = 0,
)

data class UiState(
    val stage: Stage = Stage.HOME,
    val name: String = "",
    val hosts: List<NearbyHost> = emptyList(),
    val joinRequests: List<PairRequest> = emptyList(), // host side: devices asking to join
    val connectingTo: String? = null,      // joiner side: name of the host we're connecting to
    val pairing: PairRequest? = null,      // joiner side: code to compare with the host's screen
    val pairingConfirmed: Boolean = false, // joiner side: I tapped "codes match"
    val hostAccepted: String? = null,      // host side: name of the device I accepted
    val isHost: Boolean = false,
    /** Playing against the computer on this one phone. */
    val solo: Boolean = false,
    val soloSkill: Skill? = null,
    val partnerName: String = "",
    val lobbyGame: GameKind = GameKind.DRAW, // the game the host has picked (before or after pairing)
    val settings: LobbySettings = LobbySettings(),
    val game: GameState? = null,             // Draw Something
    val mines: MinesState? = null,           // Bomb Find
    val sea: SeaState? = null,               // Sea Battle
    val syncEngines: Map<GameKind, SyncEngine> = emptyMap(), // Drop Four, Memory Match, Quick Draw, Lucky Dice, Flip Fight
    val sync: Map<GameKind, Any> = emptyMap(),               // their latest states (changes when anything changes)
    val board: StrokeBoard? = null,
    val boardVersion: Int = 0,
    val notice: String? = null,
)

class GameViewModel(app: Application) : AndroidViewModel(app) {
    private val prefs = app.getSharedPreferences("draw_something", Context.MODE_PRIVATE)
    private val _ui = MutableStateFlow(UiState(name = prefs.getString("name", "").orEmpty()))
    val ui: StateFlow<UiState> = _ui

    // ---------------------------------------------------------------- updates (the only use of the internet)

    private val updater = UpdateManager(app)
    private val _update = MutableStateFlow<UpdateUi>(UpdateUi.Idle)
    val updateUi: StateFlow<UpdateUi> = _update
    val appVersion: String = updater.currentVersion
    private var downloaded: File? = null

    /** Asks GitHub for the newest release. Only ever runs when the player taps the button. */
    fun checkForUpdate() {
        if (_update.value is UpdateUi.Checking || _update.value is UpdateUi.Downloading) return
        _update.value = UpdateUi.Checking
        viewModelScope.launch {
            _update.value = try {
                when (val r = updater.check()) {
                    is UpdateCheck.Available -> UpdateUi.Available(r.info)
                    is UpdateCheck.UpToDate -> UpdateUi.UpToDate(r.version)
                }
            } catch (e: Exception) {
                UpdateUi.Failed(e.message ?: "Couldn't check for updates.")
            }
        }
    }

    /** Download, check, and hand to Android's installer. */
    fun startUpdate() {
        val info = (_update.value as? UpdateUi.Available)?.info ?: return
        _update.value = UpdateUi.Downloading(info, 0f)
        viewModelScope.launch {
            try {
                val file = updater.download(info) { p -> _update.value = UpdateUi.Downloading(info, p) }
                val problem = withContext(Dispatchers.IO) { updater.problemWith(file) }
                if (problem != null) {
                    file.delete()
                    _update.value = UpdateUi.Failed(problem)
                } else {
                    downloaded = file
                    proceedToInstall(info)
                }
            } catch (e: Exception) {
                _update.value = UpdateUi.Failed(e.message ?: "The download failed.")
            }
        }
    }

    private fun proceedToInstall(info: ReleaseInfo) {
        val file = downloaded ?: return
        if (!updater.canInstall()) {
            _update.value = UpdateUi.NeedPermission(info)
            return
        }
        _update.value = UpdateUi.Installing(info)
        try {
            updater.install(file)
        } catch (e: Exception) {
            _update.value = UpdateUi.Failed("Couldn't open Android's installer: ${e.message}")
        }
    }

    fun openInstallPermissionScreen() = updater.openInstallPermissionScreen()

    /** After the player has allowed installs from this app. */
    fun retryInstall() {
        (_update.value as? UpdateUi.NeedPermission)?.info?.let { proceedToInstall(it) }
    }

    fun dismissUpdate() {
        if (_update.value !is UpdateUi.Downloading) _update.value = UpdateUi.Idle
    }

    private val transport = NearbyTransport(app) { onTransportEvent(it) }
    private var engine: GameEngine? = null
    private var minesEngine: MinesEngine? = null
    private var seaEngine: SeaEngine? = null
    private var syncEngines: Map<GameKind, SyncEngine> = emptyMap()
    private var computer: PlayerSide? = null // the pretend second phone in solo play

    /** Everything the engines say goes to the real partner, or to the computer in solo play. */
    private fun sendBytes(bytes: ByteArray) {
        val c = computer
        if (c != null) c.receive(bytes) else transport.send(bytes)
    }

    /** Play against the computer. You host; the computer joins instantly as a partner. */
    fun startSolo(skill: Skill) {
        val name = cleanName() ?: return
        teardown()
        _ui.update { UiState(name = it.name, isHost = true, solo = true, soloSkill = skill) }
        computer = PlayerSide(
            isHost = false, myName = COMPUTER_NAME, theirName = name, skill = skill,
            sendBytes = { bytes -> onTransportEvent(TransportEvent.Received(bytes)) },
            secretWord = { engine?.state?.word?.text },
            clock = { SystemClock.elapsedRealtime() },
        )
        startSession(COMPUTER_NAME)
        computer?.start()
    }
    private var tickJob: Job? = null
    private var acceptedId: String? = null // host: the one device I accepted

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
        _ui.update { it.copy(connectingTo = host.name, pairing = null, pairingConfirmed = false) }
        transport.connect(host.id)
    }

    /** Joiner: "the code on my screen matches the host's". */
    fun confirmPairing() {
        val p = _ui.value.pairing ?: return
        transport.accept(p.id)
        _ui.update { it.copy(pairingConfirmed = true) }
    }

    /** Joiner: the codes don't match (or I changed my mind). */
    fun cancelPairing() {
        _ui.value.pairing?.let { transport.reject(it.id) }
        _ui.update { it.copy(pairing = null, pairingConfirmed = false, connectingTo = null) }
    }

    /** Host: accept the one device whose code matches, and turn everyone else away. */
    fun acceptJoin(id: String) {
        val s = _ui.value
        val req = s.joinRequests.firstOrNull { it.id == id } ?: return
        s.joinRequests.filter { it.id != id }.forEach { transport.reject(it.id) }
        acceptedId = id
        transport.accept(id)
        _ui.update { it.copy(joinRequests = emptyList(), hostAccepted = req.name) }
    }

    fun declineJoin(id: String) {
        transport.reject(id)
        _ui.update { s -> s.copy(joinRequests = s.joinRequests.filter { it.id != id }) }
    }

    /** Back to the menu from anywhere (also disconnects). */
    fun leave() {
        teardown()
        _ui.update { UiState(name = it.name) }
    }

    /** Run something on the Draw Something engine (draw, guess, judge...). */
    fun game(block: GameEngine.() -> Unit) {
        engine?.block()
    }

    /** Run something on the Bomb Find engine. */
    fun mines(block: MinesEngine.() -> Unit) {
        minesEngine?.block()
    }

    /** Run something on the Sea Battle engine. */
    fun sea(block: SeaEngine.() -> Unit) {
        seaEngine?.block()
    }

    // Starting one game sends the others back to the lobby, so only one is ever on screen.

    private fun resetOthers(keep: GameKind) {
        if (keep != GameKind.DRAW) engine?.backToLobby()
        if (keep != GameKind.MINES) minesEngine?.backToLobby()
        if (keep != GameKind.SEA) seaEngine?.backToLobby()
        syncEngines.forEach { (k, e) -> if (k != keep) e.backToLobby() }
    }

    fun startDraw(rounds: Int, seconds: Int, minDifficulty: Difficulty) {
        resetOthers(GameKind.DRAW)
        engine?.hostStart(rounds, seconds, minDifficulty, solo = _ui.value.solo)
    }

    fun startMines(size: MineSize) {
        resetOthers(GameKind.MINES)
        minesEngine?.start(size)
    }

    fun startSea(size: SeaSize) {
        resetOthers(GameKind.SEA)
        seaEngine?.start(size)
    }

    /** Start one of the turn-based games (Drop Four, Memory Match, Quick Draw, Lucky Dice, Flip Fight). */
    fun startSync(kind: GameKind, options: JSONObject) {
        resetOthers(kind)
        syncEngines[kind]?.start(options)
    }

    /** Host: choose which game to play. Works before anyone has joined; the partner follows once paired. */
    fun pickGame(kind: GameKind) {
        if (!_ui.value.isHost) return
        _ui.update { it.copy(lobbyGame = kind) }
        sendBytes(LobbyMsg.encodePick(kind)) // does nothing until a partner is connected
    }

    fun setSettings(settings: LobbySettings) = _ui.update { it.copy(settings = settings) }

    /** After any game: back to the lobby (still connected) to pick again. */
    fun backToGames() {
        engine?.backToLobby()
        minesEngine?.backToLobby()
        seaEngine?.backToLobby()
        syncEngines.values.forEach { it.backToLobby() }
        publishAll()
    }

    // ---------------------------------------------------------------- connection events

    private fun onTransportEvent(e: TransportEvent) {
        when (e) {
            is TransportEvent.HostFound -> _ui.update { s ->
                s.copy(hosts = s.hosts.filter { it.id != e.id } + NearbyHost(e.id, e.name))
            }

            is TransportEvent.HostLost -> _ui.update { s -> s.copy(hosts = s.hosts.filter { it.id != e.id }) }

            is TransportEvent.ConnectionRequest -> {
                val s = _ui.value
                when {
                    // Host: collect requests so the right one can be picked by name + code.
                    e.incoming && s.stage == Stage.HOSTING && s.hostAccepted == null &&
                        s.joinRequests.size < MAX_PENDING_REQUESTS ->
                        _ui.update { it.copy(joinRequests = it.joinRequests.filter { r -> r.id != e.id } + PairRequest(e.id, e.name, e.code)) }
                    // Joiner: show the code; nothing connects until the player confirms it matches.
                    !e.incoming && s.stage == Stage.SEARCHING && s.connectingTo != null ->
                        _ui.update { it.copy(pairing = PairRequest(e.id, e.name, e.code), pairingConfirmed = false) }
                    // Anything else (not hosting, already accepted someone, too many): turned away.
                    else -> transport.reject(e.id)
                }
            }

            is TransportEvent.ConnectionFailed -> {
                val s = _ui.value
                val wasMine = s.pairing?.id == e.id
                val acceptedFailed = e.id == acceptedId
                if (acceptedFailed) acceptedId = null
                _ui.update { st ->
                    st.copy(
                        joinRequests = st.joinRequests.filter { it.id != e.id },
                        pairing = if (wasMine) null else st.pairing,
                        pairingConfirmed = if (wasMine) false else st.pairingConfirmed,
                        connectingTo = if (wasMine) null else st.connectingTo,
                        hostAccepted = if (acceptedFailed) null else st.hostAccepted,
                        notice = if (wasMine || acceptedFailed) {
                            "That connection was declined or didn't work. Check Bluetooth/Wi-Fi are on and try again."
                        } else st.notice,
                    )
                }
            }

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

            is TransportEvent.Failure -> _ui.update { it.copy(connectingTo = null, pairing = null, pairingConfirmed = false, notice = e.message) }

            is TransportEvent.Received -> when (val p = Packet.decode(e.bytes)) {
                is Packet.Lobby -> _ui.update { it.copy(lobbyGame = p.game) }
                is Packet.Draw -> {
                    if (p.msg is Msg.Setup) resetOthers(GameKind.DRAW)
                    engine?.onMessage(p.msg)
                }
                is Packet.Mines -> {
                    if (p.msg is MineMsg.Setup) resetOthers(GameKind.MINES)
                    minesEngine?.onMessage(p.msg)
                }
                is Packet.Sea -> {
                    if (p.msg is SeaMsg.Setup) resetOthers(GameKind.SEA)
                    seaEngine?.onMessage(p.msg)
                }
                is Packet.Sync -> {
                    val kind = GameKind.values().firstOrNull { it.name == p.msg.game }
                    if (kind != null) {
                        if (p.msg is GMsg.Setup) resetOthers(kind)
                        syncEngines[kind]?.onMessage(p.msg)
                    }
                }
                null -> Unit
            }
        }
    }

    private fun startSession(partner: String) {
        if (computer == null) transport.stopLooking()
        val s = _ui.value
        val e = GameEngine(
            myName = s.name.trim(),
            theirName = partner,
            isHost = s.isHost,
            send = { sendBytes(it.encode()) },
            clock = { SystemClock.elapsedRealtime() },
        )
        engine = e
        e.onChange = { publishAll() }
        val m = MinesEngine(s.name.trim(), partner, s.isHost, { sendBytes(it.encode()) })
        m.onChange = { publishAll() }
        minesEngine = m
        val sb = SeaEngine(s.name.trim(), partner, s.isHost, { sendBytes(it.encode()) })
        sb.onChange = { publishAll() }
        seaEngine = sb
        val me = s.name.trim()
        val host = s.isHost
        val sendG: (GMsg) -> Unit = { sendBytes(it.encode()) }
        val clock: () -> Long = { SystemClock.elapsedRealtime() }
        val syncList = listOf(
            DropEngine(me, partner, host, sendG, clock = clock),
            MemoryEngine(me, partner, host, sendG, clock = clock),
            QuickEngine(me, partner, host, sendG, clock = clock),
            DiceEngine(me, partner, host, sendG, clock = clock),
            FlipEngine(me, partner, host, sendG, clock = clock),
        )
        syncList.forEach { it.onChange = { publishAll() } }
        syncEngines = syncList.associateBy { it.kind }
        tickJob?.cancel()
        tickJob = viewModelScope.launch {
            while (isActive) {
                delay(TICK_MS)
                e.tick()
                computer?.tick(SystemClock.elapsedRealtime())
                syncEngines.values.forEach { it.tick() }
            }
        }
        _ui.update {
            it.copy(
                stage = Stage.LOBBY, partnerName = partner, joinRequests = emptyList(), connectingTo = null,
                pairing = null, pairingConfirmed = false, hostAccepted = null,
                hosts = emptyList(), notice = null,
                game = e.state, mines = m.state, sea = sb.state, board = e.board, boardVersion = e.board.version,
                syncEngines = syncEngines, sync = syncEngines.mapValues { it.value.stateAny },
            )
        }
        // The host may already have picked a game while waiting: let the partner's lobby show it.
        if (s.isHost) sendBytes(LobbyMsg.encodePick(_ui.value.lobbyGame))
    }

    /** Copy every engine's state into the UI. We are "playing" if any game is under way. */
    private fun publishAll() {
        val d = engine ?: return
        val m = minesEngine
        val sb = seaEngine
        _ui.update {
            if (it.stage != Stage.LOBBY && it.stage != Stage.PLAYING) {
                it
            } else {
                val playing = d.state.phase != Phase.LOBBY ||
                    (m != null && m.state.phase != MinesPhase.LOBBY) ||
                    (sb != null && sb.state.phase != SeaPhase.LOBBY) ||
                    syncEngines.values.any { e -> e.isActive }
                it.copy(
                    stage = if (playing) Stage.PLAYING else Stage.LOBBY,
                    game = d.state, mines = m?.state, sea = sb?.state, boardVersion = d.board.version,
                    sync = syncEngines.mapValues { e -> e.value.stateAny },
                )
            }
        }
    }

    private fun teardown() {
        computer = null
        acceptedId = null
        tickJob?.cancel()
        tickJob = null
        engine?.onChange = {}
        engine = null
        minesEngine?.onChange = {}
        minesEngine = null
        seaEngine?.onChange = {}
        seaEngine = null
        syncEngines.values.forEach { it.onChange = {} }
        syncEngines = emptyMap()
        transport.stopAll()
    }

    override fun onCleared() {
        teardown()
    }

    private companion object {
        const val MAX_NAME = 20
        const val COMPUTER_NAME = "Computer 🤖"
        const val MAX_PENDING_REQUESTS = 5
        const val TICK_MS = 100L
    }
}
