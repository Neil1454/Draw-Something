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

    if (showSplash && ui.stage == Stage.HOME) {
        SplashScreen { showSplash = false }
        return
    }

    when (ui.stage) {
        Stage.HOME -> HomeScreen(ui, vm::setName, onHost, onJoin, vm::clearNotice)
        Stage.HOSTING -> HostingScreen(ui, vm::acceptJoin, vm::declineJoin, vm::leave, vm::clearNotice)
        Stage.SEARCHING -> SearchingScreen(ui, vm::requestJoin, vm::confirmPairing, vm::cancelPairing, vm::leave, vm::clearNotice)
        Stage.LOBBY -> ui.game?.let { game ->
            LobbyScreen(
                ui, game, act,
                onStart = { rounds, secs, min -> act { hostStart(rounds, secs, min) } },
                onLeave = vm::leave,
            )
        }
        Stage.PLAYING -> {
            val game = ui.game
            val board = ui.board
            if (game != null && board != null) GameRoot(game, board, ui.boardVersion, act, vm::leave)
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
