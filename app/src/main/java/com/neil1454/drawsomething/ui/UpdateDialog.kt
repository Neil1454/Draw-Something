package com.neil1454.drawsomething.ui

import com.neil1454.drawsomething.update.UpdateFile
import androidx.compose.material3.MaterialTheme
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.neil1454.drawsomething.UpdateUi

/** The whole "Check for updates" conversation, one dialog that changes as it goes. */
@Composable
fun UpdateDialog(
    state: UpdateUi,
    currentVersion: String,
    onDownload: () -> Unit,
    onOpenSettings: () -> Unit,
    onRetryInstall: () -> Unit,
    onClose: () -> Unit,
) {
    val busy = state is UpdateUi.Checking || state is UpdateUi.Downloading
    AlertDialog(
        onDismissRequest = { if (!busy) onClose() },
        title = {
            Text(
                when (state) {
                    is UpdateUi.Checking -> "Checking for updates…"
                    is UpdateUi.UpToDate -> "✅ You're up to date"
                    is UpdateUi.Available -> "🎉 Update available"
                    is UpdateUi.Downloading -> "Downloading…"
                    is UpdateUi.NeedPermission -> "One quick permission"
                    is UpdateUi.Installing -> "Installing"
                    is UpdateUi.Failed -> "Couldn't update"
                    UpdateUi.Idle -> ""
                },
            )
        },
        text = {
            Column(Modifier.verticalScroll(rememberScrollState()), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                when (state) {
                    is UpdateUi.Checking -> {
                        CircularProgressIndicator(Modifier.size(32.dp))
                        Text("Asking GitHub for the latest version. This needs the internet.")
                    }
                    is UpdateUi.UpToDate -> Text("You have the latest version (${state.version}).")
                    is UpdateUi.Available -> {
                        Text("Version ${state.info.version} is ready (you have $currentVersion).", fontWeight = FontWeight.Bold)
                        if (state.info.sizeBytes > 0) Text("Download size: about ${state.info.sizeBytes / 1_000_000} MB.")
                        if (state.info.notes.isNotBlank()) Text(state.info.notes, style = androidx.compose.material3.MaterialTheme.typography.bodySmall)
                        Text(
                            "It downloads from this project's GitHub page, is checked, and then Android asks you to tap Install. " +
                                "Your name is kept.",
                            style = androidx.compose.material3.MaterialTheme.typography.bodySmall,
                        )
                    }
                    is UpdateUi.Downloading -> {
                        LinearProgressIndicator(progress = { state.progress }, Modifier.fillMaxWidth())
                        Text("${(state.progress * 100).toInt()}% of version ${state.info.version}")
                    }
                    is UpdateUi.NeedPermission -> {
                        Text(
                            "Android wants your OK before this app can install an update, so nothing has been downloaded yet. " +
                                "Tap “Open settings”, switch on “Allow from this source”, press back, then tap “Done, download now”.",
                        )
                        Text(
                            "If Android restarts the app when you switch it on, just tap Check for updates again: it will download once.",
                            style = MaterialTheme.typography.bodySmall,
                        )
                        if (state.stillBlocked) {
                            Text("Android still says no. Switch on “Allow from this source” first.", color = MaterialTheme.colorScheme.error)
                        }
                    }
                    is UpdateUi.Installing -> Text(
                        "Handing over to Android's installer. Tap Install on the screen that appears. The app will restart on version ${state.info.version}.",
                    )
                    is UpdateUi.Failed -> Text(state.message)
                    UpdateUi.Idle -> Unit
                }
            }
        },
        confirmButton = {
            when (state) {
                is UpdateUi.Available -> TextButton(onClick = onDownload) { Text("Download & install") }
                is UpdateUi.NeedPermission -> TextButton(onClick = onOpenSettings) { Text("Open settings") }
                is UpdateUi.Checking, is UpdateUi.Downloading -> Unit
                else -> TextButton(onClick = onClose) { Text("OK") }
            }
        },
        dismissButton = {
            when (state) {
                is UpdateUi.Available -> TextButton(onClick = onClose) { Text("Not now") }
                is UpdateUi.NeedPermission -> TextButton(onClick = onRetryInstall) { Text("Done, download now") }
                else -> Unit
            }
        },
    )
}

/** Shows exactly which update files are on the phone (normally none), and what the last tidy-up did. */
@Composable
fun UpdateFilesDialog(files: List<UpdateFile>, tidyNote: String, onClear: () -> Unit, onClose: () -> Unit) {
    AlertDialog(
        onDismissRequest = onClose,
        confirmButton = { TextButton(onClick = onClose) { Text("Close") } },
        dismissButton = { if (files.isNotEmpty()) TextButton(onClick = onClear) { Text("Delete all") } },
        title = { Text("Update files on this phone") },
        text = {
            Column(Modifier.verticalScroll(rememberScrollState()), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                if (files.isEmpty()) {
                    Text("None. Nothing is stored, which is how it should be.")
                } else {
                    files.forEach { f ->
                        Text("• ${f.name}\n  ${"%.1f".format(f.sizeBytes / 1_048_576.0)} MB · ${f.verdict}")
                    }
                }
                if (tidyNote.isNotBlank()) Text("Last tidy-up: $tidyNote", style = MaterialTheme.typography.bodySmall)
                Text(
                    "Every time the app starts or comes back on screen it reads the version inside each downloaded file, compares it " +
                        "with the version you have installed, and deletes anything that isn't needed.",
                    style = MaterialTheme.typography.bodySmall,
                )
                Text(
                    "These files live in the app's private storage, so a file manager cannot open them (that is normal on Android). " +
                        "To check from outside the app, go to Settings → Apps → this app → Storage: the Cache size should be tiny.",
                    style = MaterialTheme.typography.bodySmall,
                )
            }
        },
    )
}
