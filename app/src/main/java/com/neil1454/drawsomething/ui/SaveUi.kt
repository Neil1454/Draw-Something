package com.neil1454.drawsomething.ui

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.runtime.staticCompositionLocalOf
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.neil1454.drawsomething.core.GameKind
import com.neil1454.drawsomething.core.SaveFormat
import com.neil1454.drawsomething.core.SavedGame

/** What the screens need to show and use saved games, handed down from the activity. */
class SaveActions(
    val saves: Map<GameKind, SavedGame>,
    val resume: (GameKind) -> Unit,
    val discard: (GameKind) -> Unit,
)

val LocalSaves = staticCompositionLocalOf { SaveActions(emptyMap(), {}, {}) }

/** In a game's lobby (host only): the saved game, with Resume and Discard. */
@Composable
fun SavedGameCard(kind: GameKind, saved: SavedGame, connected: Boolean, onResume: () -> Unit, onDiscard: () -> Unit) {
    var confirmDiscard by remember { mutableStateOf(false) }
    val shape = RoundedCornerShape(18.dp)
    Column(
        Modifier.fillMaxWidth().clip(shape).background(Color(0x3322C55E)).border(2.dp, Color(0xFF22C55E), shape).padding(14.dp),
        verticalArrangement = Arrangement.spacedBy(6.dp),
    ) {
        Text("💾 Saved ${kind.label} game", fontWeight = FontWeight.Black, fontSize = 17.sp)
        Text(saved.summary, fontSize = 14.sp)
        Text(
            "Saved ${SaveFormat.ago(saved.savedAt, System.currentTimeMillis())}" + if (saved.guest.isNotBlank()) ", playing with ${saved.guest}" else "",
            fontSize = 12.sp, color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
        Row(horizontalArrangement = Arrangement.spacedBy(8.dp), modifier = Modifier.fillMaxWidth()) {
            Button(onClick = onResume, enabled = connected, modifier = Modifier.weight(1f)) {
                Text(if (connected) "▶ Resume" else "Resume (waiting for your partner…)", textAlign = TextAlign.Center)
            }
            OutlinedButton(onClick = { confirmDiscard = true }) { Text("Discard") }
        }
        if (!connected) Text("Pair up the normal way first; your partner just joins and the game carries on where you left off.", fontSize = 12.sp)
    }
    if (confirmDiscard) {
        AlertDialog(
            onDismissRequest = { confirmDiscard = false },
            title = { Text("Delete this saved game?") },
            text = { Text("${saved.summary}\n\nThis can't be undone.") },
            confirmButton = { TextButton(onClick = { confirmDiscard = false; onDiscard() }) { Text("Delete it") } },
            dismissButton = { TextButton(onClick = { confirmDiscard = false }) { Text("Keep it") } },
        )
    }
}

/** Starting a new game when one is already saved would replace it: ask first. */
@Composable
fun ReplaceSaveDialog(kind: GameKind, saved: SavedGame, onReplace: () -> Unit, onCancel: () -> Unit) {
    AlertDialog(
        onDismissRequest = onCancel,
        title = { Text("Replace your saved game?") },
        text = { Text("You have a saved ${kind.label} game (${saved.summary}). Starting a new one will delete it.") },
        confirmButton = { TextButton(onClick = onReplace) { Text("Start new game") } },
        dismissButton = { TextButton(onClick = onCancel) { Text("Keep the saved game") } },
    )
}

/**
 * Leaving a long game. The host chooses to save it (to finish later) or not. Choosing "don't save" asks a second time,
 * because everything would be lost for both players.
 */
@Composable
fun LeaveLongGameDialog(kind: GameKind, summary: String, isHost: Boolean, onSaveAndLeave: () -> Unit, onDiscardAndLeave: () -> Unit, onStay: () -> Unit) {
    var sure by remember { mutableStateOf(false) }
    if (!isHost) {
        AlertDialog(
            onDismissRequest = onStay,
            title = { Text("Leave the game?") },
            text = { Text("This ends the ${kind.label} game for now. The host keeps the progress, so you can rejoin and carry on later.") },
            confirmButton = { TextButton(onClick = onSaveAndLeave) { Text("Leave") } },
            dismissButton = { TextButton(onClick = onStay) { Text("Stay") } },
        )
        return
    }
    if (!sure) {
        AlertDialog(
            onDismissRequest = onStay,
            title = { Text("Save your ${kind.label} game?") },
            text = {
                Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
                    if (summary.isNotBlank()) Text(summary, fontWeight = FontWeight.SemiBold)
                    Text("Save it and you can carry on later, even if you close the app. Your partner just joins again the normal way and you press Resume.")
                    Button(onClick = onSaveAndLeave, modifier = Modifier.fillMaxWidth()) { Text("💾  Save and leave") }
                    OutlinedButton(onClick = { sure = true }, modifier = Modifier.fillMaxWidth()) { Text("Don't save") }
                    TextButton(onClick = onStay, modifier = Modifier.fillMaxWidth()) { Text("Keep playing") }
                }
            },
            confirmButton = {},
        )
    } else {
        AlertDialog(
            onDismissRequest = { sure = false },
            title = { Text("Lose all your progress?") },
            text = { Text("If you don't save, this ${kind.label} game is deleted for good, for both of you. There is no way to get it back.") },
            confirmButton = { TextButton(onClick = onDiscardAndLeave) { Text("Yes, delete it and leave") } },
            dismissButton = { TextButton(onClick = { sure = false }) { Text("Go back") } },
        )
    }
}
