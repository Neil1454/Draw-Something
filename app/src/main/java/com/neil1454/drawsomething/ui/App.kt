package com.neil1454.drawsomething.ui

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
import com.neil1454.drawsomething.core.DiceEngine
import com.neil1454.drawsomething.core.DiceState
import com.neil1454.drawsomething.core.DropEngine
import com.neil1454.drawsomething.core.DropState
import com.neil1454.drawsomething.core.FlipEngine
import com.neil1454.drawsomething.core.FlipState
import com.neil1454.drawsomething.core.GameKind
import com.neil1454.drawsomething.core.MemoryEngine
import com.neil1454.drawsomething.core.MemoryState
import com.neil1454.drawsomething.core.MinesPhase
import com.neil1454.drawsomething.core.QuickEngine
import com.neil1454.drawsomething.core.QuickState
import com.neil1454.drawsomething.core.SyncEngine
import org.json.JSONObject
import com.neil1454.drawsomething.core.SeaPhase
import com.neil1454.drawsomething.Stage

@Composable
fun DrawSomethingApp(vm: GameViewModel, onHost: () -> Unit, onJoin: () -> Unit) {
    val ui by vm.ui.collectAsStateWithLifecycle()
    val act: Act = { vm.game(it) }
    var confirmLeave by remember { mutableStateOf(false) }
    var showSplash by rememberSaveable { mutableStateOf(true) }

    BackHandler(enabled = ui.stage != Stage.HOME) {
        if (ui.stage == Stage.PLAYING) confirmLeave = true else vm.leave()
    }

    ImmersiveMode(ui.stage == Stage.PLAYING)

    if (showSplash && ui.stage == Stage.HOME) {
        SplashScreen { showSplash = false }
        return
    }

    when (ui.stage) {
        Stage.HOME -> HomeScreen(ui, vm::setName, onHost, onJoin, vm::clearNotice)
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
        Stage.PLAYING -> {
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
        }
    }

    if (confirmLeave) {
        AlertDialog(
            onDismissRequest = { confirmLeave = false },
            title = { Text("Leave the game?") },
            text = { Text("This ends the game for both of you.") },
            confirmButton = { TextButton(onClick = { confirmLeave = false; vm.leave() }) { Text("Leave") } },
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
    }
}
