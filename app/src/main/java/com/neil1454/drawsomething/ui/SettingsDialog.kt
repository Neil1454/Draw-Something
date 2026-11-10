package com.neil1454.drawsomething.ui

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.FilterChip
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp

@Composable
private fun SettingSwitch(title: String, note: String, on: Boolean, onChange: (Boolean) -> Unit) {
    Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(12.dp)) {
        Column(Modifier.weight(1f)) {
            Text(title, fontWeight = FontWeight.SemiBold)
            Text(note, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
        }
        Switch(checked = on, onCheckedChange = onChange)
    }
}

/** Sound, music, vibration, and how Tetris looks and feels. Opened from the home screen and from inside Tetris. */
@Composable
fun SettingsDialog(soundOn: Boolean, onSound: (Boolean) -> Unit, onClose: () -> Unit) {
    AlertDialog(
        onDismissRequest = onClose,
        confirmButton = { TextButton(onClick = onClose) { Text("Done") } },
        title = { Text("Settings") },
        text = {
            Column(Modifier.verticalScroll(rememberScrollState()), verticalArrangement = Arrangement.spacedBy(14.dp)) {
                SettingSwitch("Sound effects", "Taps, clears, explosions and fanfares.", soundOn, onSound)
                SettingSwitch("Tetris music", "A looping tune while you play Tetris. Off by default.", TetrisPrefs.music) { TetrisPrefs.music = it }
                SettingSwitch("Vibration", "A light buzz when you turn or drop a piece.", TetrisPrefs.haptics) { TetrisPrefs.haptics = it }

                Text("Tetris touch feel", fontWeight = FontWeight.SemiBold)
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    TetrisPrefs.feelNames.forEachIndexed { i, n ->
                        FilterChip(selected = TetrisPrefs.feel == i, onClick = { TetrisPrefs.feel = i }, label = { Text(n) })
                    }
                }
                Text(
                    TetrisPrefs.feelNotes[TetrisPrefs.feel] + " Slide your finger to move, tap the left or right side to turn, " +
                        "drag down to fall faster, flick down to drop, flick up to hold.",
                    style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant,
                )

                Text("Block style", fontWeight = FontWeight.SemiBold)
                TetrisPrefs.skinNames.chunked(2).forEachIndexed { row, names ->
                    Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        names.forEachIndexed { j, n ->
                            val i = row * 2 + j
                            FilterChip(selected = TetrisPrefs.skin == i, onClick = { TetrisPrefs.skin = i }, label = { Text(n) })
                        }
                    }
                }
                SkinPreview(TetrisPrefs.skin, Modifier.fillMaxWidth())
            }
        },
    )
}
