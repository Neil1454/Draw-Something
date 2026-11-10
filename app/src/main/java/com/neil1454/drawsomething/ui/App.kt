package com.neil1454.drawsomething.ui

import com.neil1454.drawsomething.core.TetrisEngine
import com.neil1454.drawsomething.core.TetrisState
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.activity.compose.BackHandler
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.neil1454.drawsomething.GameViewModel
import com.neil1454.drawsomething.UiState
import com.neil1454.drawsomething.UpdateUi
import com.neil1454.drawsomething.core.DiceEngine
import com.neil1454.drawsomething.core.DiceState
import com.neil1454.drawsomething.core.DropEngine
import com.neil1454.drawsomething.core.DropState
import com.neil1454.drawsomething.core.FlipEngine
import com.neil1454.drawsomething.core.FlipState
import com.neil1454.drawsomething.core.GameKind
import com.neil1454.drawsomething.core.HiLoEngine
import com.neil1454.drawsomething.core.HiLoState
import com.neil1454.drawsomething.core.HolidayEngine
import com.neil1454.drawsomething.core.HolidayRules
import com.neil1454.drawsomething.core.HolidayState
import com.neil1454.drawsomething.core.MemoryEngine
import com.neil1454.drawsomething.core.MemoryState
import com.neil1454.drawsomething.core.MinesPhase
import com.neil1454.drawsomething.core.QuickEngine
import com.neil1454.drawsomething.core.QuickState
import com.neil1454.drawsomething.core.SyncEngine
import com.neil1454.drawsomething.core.Sound
import com.neil1454.drawsomething.sound.Sfx
import org.json.JSONObject
import com.neil1454.drawsomething.core.SeaPhase
import com.neil1454.drawsomething.Stage

@Composable
fun DrawSomethingApp(vm: GameViewModel, onHost: () -> Unit, onJoin: () -> Unit) {
    val ui by vm.ui.collectAsStateWithLifecycle()
    val act: Act = { vm.game(it) }
    var confirmLeave by remember { mutableStateOf(false) }
    var showSplash by rememberSaveable { mutableStateOf(true) }
    var showBoards by rememberSaveable { mutableStateOf(false) }
    var soundOn by remember { mutableStateOf(Sfx.enabled) }
    val statsVersion by vm.stats.version.collectAsStateWithLifecycle()
    val myRecord = remember(statsVersion, ui.name) {
        val rows = vm.stats.book.dominator()
        val i = rows.indexOfFirst { it.name.equals(ui.name.trim(), ignoreCase = true) }
        if (i < 0) null else "🏆 Rank ${i + 1} of ${rows.size} · ${rows[i].won} wins · ${rows[i].rating} Dominator points"
    }

    BackHandler(enabled = ui.stage != Stage.HOME) {
        if (ui.stage == Stage.PLAYING) confirmLeave = true else vm.leave()
    }

    var learnMode by rememberSaveable { mutableStateOf(false) }
    var learnKind by rememberSaveable { mutableStateOf<GameKind?>(null) }
    ImmersiveMode(ui.stage == Stage.PLAYING || (learnMode && learnKind != null))

    if (showSplash && ui.stage == Stage.HOME) {
        SplashScreen { showSplash = false }
        return
    }

    // Back steps out of Learn mode (declared after the handler above, so it takes priority)
    BackHandler(enabled = learnMode && ui.stage == Stage.HOME) {
        if (learnKind != null) learnKind = null else learnMode = false
    }
    BackHandler(enabled = showBoards && ui.stage == Stage.HOME) { showBoards = false }
    if (showBoards && ui.stage == Stage.HOME) {
        LeaderboardScreen(vm.stats, ui.name, onBack = { showBoards = false })
        return
    }
    if (learnMode && ui.stage == Stage.HOME) {
        val kind = learnKind
        if (kind == null) LearnMenu(onPick = { learnKind = it }, onBack = { learnMode = false })
        else LearnScreen(kind, onBack = { learnKind = null })
        return
    }

    when (ui.stage) {
        Stage.HOME -> HomeScreen(
            ui, vm::setName, onHost, onJoin, vm::clearNotice, vm.appVersion, vm::checkForUpdate, vm::startSolo,
            onLearn = { learnMode = true }, onBoards = { showBoards = true }, myRecord = myRecord,
            soundOn = soundOn, onSound = { soundOn = it; Sfx.setEnabled(it); if (it) Sfx.play(Sound.CHIME) },
        )
        Stage.HOSTING -> HostingScreen(
            ui, vm::acceptJoin, vm::declineJoin, vm::leave, vm::clearNotice,
            browse = { BrowseGames(ui, vm::pickGame, vm::setSettings) },
        )
        Stage.SEARCHING -> SearchingScreen(
            ui, vm::requestJoin, vm::confirmPairing, vm::cancelPairing, vm::leave, vm::clearNotice,
            browse = { BrowseGames(ui, vm::pickGame, vm::setSettings) },
        )
        Stage.LOBBY -> ui.game?.let { game ->
            LobbyScreen(
                ui, game, act,
                onPickGame = vm::pickGame,
                onSettings = vm::setSettings,
                onStartDraw = vm::startDraw,
                onStartMines = vm::startMines,
                onStartSea = vm::startSea,
                onStartSync = vm::startSync,
                onLeave = vm::leave,
            )
        }
        Stage.PLAYING -> Box(Modifier.fillMaxSize()) {
            val game = ui.game
            val board = ui.board
            val mines = ui.mines
            val sea = ui.sea
            val activeSync = ui.syncEngines.values.firstOrNull { it.isActive }
            when {
                activeSync != null -> SyncGameRoot(activeSync, ui.sync[activeSync.kind], ui, vm)
                mines != null && mines.phase != MinesPhase.LOBBY ->
                    MinesScreen(
                        mines, { vm.mines(it) },
                        onPlayAgain = { vm.startMines(mines.size) },
                        onBackToGames = vm::backToGames,
                        onLeave = vm::leave,
                    )
                sea != null && sea.phase != SeaPhase.LOBBY ->
                    SeaRoot(
                        sea, { vm.sea(it) },
                        onPlayAgain = { vm.startSea(sea.size) },
                        onBackToGames = vm::backToGames,
                        onLeave = vm::leave,
                    )
                game != null && board != null ->
                    GameRoot(
                        game, board, ui.boardVersion, act,
                        onPlayAgain = { vm.startDraw(game.totalRounds, game.roundSeconds, game.minDifficulty) },
                        onBackToGames = vm::backToGames,
                        onLeave = vm::leave,
                    )
            }
            // A small, always-visible way out of the game.
            LeaveCorner(Modifier.align(Alignment.TopEnd)) { confirmLeave = true }
        }
    }

    val update by vm.updateUi.collectAsStateWithLifecycle()
    if (update != UpdateUi.Idle) {
        UpdateDialog(
            state = update, currentVersion = vm.appVersion,
            onDownload = vm::startUpdate, onOpenSettings = vm::openInstallPermissionScreen,
            onRetryInstall = vm::retryInstall, onClose = vm::dismissUpdate,
        )
    }

    if (confirmLeave) {
        AlertDialog(
            onDismissRequest = { confirmLeave = false },
            title = { Text("Leave the game?") },
            text = { Text("This ends the game for both of you and takes you back to the main menu.") },
            confirmButton = { TextButton(onClick = { confirmLeave = false; vm.leave() }) { Text("Leave game") } },
            dismissButton = { TextButton(onClick = { confirmLeave = false }) { Text("Stay") } },
        )
    }
}

/** Shows the screen for whichever turn-based game is running. */
@Composable
private fun SyncGameRoot(engine: SyncEngine, state: Any?, ui: UiState, vm: GameViewModel) {
    val again: () -> Unit = {
        val set = ui.settings
        val options = when (engine.kind) {
            GameKind.DROP -> JSONObject().put("size", set.dropSize).put("power", set.dropPower).put("theme", set.dropTheme)
            GameKind.MEMORY -> JSONObject().put("size", set.memorySize).put("theme", set.memoryTheme)
            GameKind.QUICK -> JSONObject().put("match", set.quickMatch).put("fake", set.quickFake)
            GameKind.DICE -> JSONObject().put("target", set.diceTarget).put("two", set.diceTwo)
            GameKind.HILO -> JSONObject().put("rows", set.hiloRows).put("odds", set.hiloOdds)
            GameKind.TETRIS -> JSONObject().put("mode", set.tetrisMode).put("lvl", set.tetrisLevel)
                .put("mins", set.tetrisMins).put("gifts", set.tetrisGifts)
            GameKind.HOLIDAY -> JSONObject().put("pack", set.holidayPack).put("len", set.holidayLen)
                .put("secs", HolidayRules.SECONDS[set.holidaySecs.coerceIn(0, HolidayRules.SECONDS.lastIndex)])
            else -> JSONObject().put("size", set.flipSize)
        }
        vm.startSync(engine.kind, options)
    }
    when {
        engine is DropEngine && state is DropState -> DropScreen(state, engine, again, vm::backToGames, vm::leave)
        engine is MemoryEngine && state is MemoryState -> MemoryScreen(state, engine, again, vm::backToGames, vm::leave)
        engine is QuickEngine && state is QuickState -> QuickScreen(state, engine, again, vm::backToGames, vm::leave)
        engine is DiceEngine && state is DiceState -> DiceScreen(state, engine, again, vm::backToGames, vm::leave)
        engine is FlipEngine && state is FlipState -> FlipScreen(state, engine, again, vm::backToGames, vm::leave)
        engine is HiLoEngine && state is HiLoState -> HiLoScreen(state, engine, again, vm::backToGames, vm::leave)
        engine is HolidayEngine && state is HolidayState -> HolidayScreen(state, engine, again, vm::backToGames, vm::leave)
        engine is TetrisEngine && state is TetrisState -> TetrisScreen(state, engine, again, vm::backToGames, vm::leave)
    }
}

/** A small round ✕ in the corner of every game screen. */
@Composable
private fun LeaveCorner(modifier: Modifier, onClick: () -> Unit) {
    Box(
        modifier
            .padding(top = 6.dp, end = 6.dp)
            .size(34.dp)
            .alpha(0.7f)
            .clip(CircleShape)
            .background(Color(0x99000000))
            .border(1.5.dp, Color.White.copy(alpha = 0.5f), CircleShape)
            .clickable(onClick = { Sfx.play(Sound.TAP); onClick() }),
        contentAlignment = Alignment.Center,
    ) { Text("✕", fontSize = 15.sp, color = Color.White, fontWeight = FontWeight.Bold) }
}
