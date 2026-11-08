@file:OptIn(ExperimentalMaterial3Api::class)

package com.neil1454.drawsomething.ui

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FilledTonalButton
import androidx.compose.material3.FilterChip
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.neil1454.drawsomething.NearbyHost
import com.neil1454.drawsomething.UiState
import com.neil1454.drawsomething.core.Scoring

@Composable
fun NoticeCard(text: String, onDismiss: (() -> Unit)? = null) {
    Card(
        Modifier.fillMaxWidth(),
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.errorContainer),
    ) {
        Row(Modifier.padding(12.dp), verticalAlignment = Alignment.CenterVertically) {
            Text(text, Modifier.weight(1f), color = MaterialTheme.colorScheme.onErrorContainer)
            if (onDismiss != null) TextButton(onClick = onDismiss) { Text("OK") }
        }
    }
}

@Composable
fun HomeScreen(
    ui: UiState,
    onName: (String) -> Unit,
    onHost: () -> Unit,
    onJoin: () -> Unit,
    onDismissNotice: () -> Unit,
) {
    var help by rememberSaveable { mutableStateOf(false) }
    Page {
        Column(
            Modifier.fillMaxWidth(),
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.spacedBy(14.dp),
        ) {
            Spacer(Modifier.height(24.dp))
            Text("🎨", fontSize = 64.sp)
            Text(
                "Draw Something",
                style = MaterialTheme.typography.displaySmall,
                fontWeight = FontWeight.Bold,
                color = MaterialTheme.colorScheme.primary,
                textAlign = TextAlign.Center,
            )
            Text(
                "Draw it. Guess it. Two players, no internet needed.",
                textAlign = TextAlign.Center,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
            Spacer(Modifier.height(8.dp))
            OutlinedTextField(
                value = ui.name,
                onValueChange = onName,
                label = { Text("Your name") },
                singleLine = true,
                modifier = Modifier.fillMaxWidth(),
            )
            ui.notice?.let { NoticeCard(it, onDismissNotice) }
            Button(onClick = onHost, Modifier.fillMaxWidth().height(56.dp)) { Text("Host a game", fontSize = 18.sp) }
            FilledTonalButton(onClick = onJoin, Modifier.fillMaxWidth().height(56.dp)) { Text("Join a game", fontSize = 18.sp) }
            TextButton(onClick = { help = true }) { Text("How to play & scoring") }
            Text(
                "Both devices just need Bluetooth and/or Wi-Fi switched on. No internet or mobile signal " +
                    "is needed, so it works on a plane (turn Bluetooth back on after enabling airplane mode). " +
                    "On Android 11 or older, Location must also be on.",
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                textAlign = TextAlign.Center,
            )
        }
    }
    if (help) HowToPlayDialog { help = false }
}

@Composable
fun HowToPlayDialog(onClose: () -> Unit) {
    AlertDialog(
        onDismissRequest = onClose,
        confirmButton = { TextButton(onClick = onClose) { Text("Got it") } },
        title = { Text("How to play") },
        text = {
            Column(Modifier.verticalScroll(rememberScrollState()), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                Text("1. One of you hosts, the other joins. They connect directly over Bluetooth/Wi-Fi.")
                Text("2. You take turns. The drawer picks one of 5 words, or taps Surprise me, then draws it.")
                Text("3. The guesser watches it appear live, types a guess and sends it.")
                Text("4. The drawer taps Correct or Not quite. The faster it's right, the more points.")
                Text("Scoring", fontWeight = FontWeight.Bold)
                Text(
                    "• A correct guess is worth up to ${Scoring.BASE} points, falling to ${(Scoring.MIN_SHARE * 100).toInt()}% " +
                        "by the end of the clock. BOTH of you get those points, so a clear drawing and a sharp eye both pay.",
                )
                Text("• Harder words are worth more (×1.25 medium, ×1.5 hard).")
                Text("• Each wrong guess the drawer rejects costs the guesser ${Scoring.WRONG_GUESS_PENALTY}.")
                Text("• Skipping a word costs the drawer ${Scoring.SKIP_PENALTY}.")
                Text("• Run out of time and nobody scores. Roles swap every round so it's always even.")
            }
        },
    )
}

@Composable
fun HostingScreen(
    ui: UiState,
    onAccept: () -> Unit,
    onDecline: () -> Unit,
    onCancel: () -> Unit,
    onDismissNotice: () -> Unit,
) {
    Page {
        Column(
            Modifier.fillMaxWidth(),
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.spacedBy(16.dp),
        ) {
            Spacer(Modifier.height(24.dp))
            Text("Hosting as ${ui.name}", style = MaterialTheme.typography.headlineSmall, fontWeight = FontWeight.Bold)
            ui.notice?.let { NoticeCard(it, onDismissNotice) }
            val req = ui.joinRequest
            if (req == null) {
                CircularProgressIndicator()
                Text(
                    "Waiting for your partner…\nOn their device tap “Join a game” and pick your name.",
                    textAlign = TextAlign.Center,
                )
            } else {
                Card(Modifier.fillMaxWidth()) {
                    Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
                        Text("${req.name} wants to join your game", style = MaterialTheme.typography.titleMedium)
                        Row(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                            OutlinedButton(onClick = onDecline, Modifier.weight(1f)) { Text("Decline") }
                            Button(onClick = onAccept, Modifier.weight(1f)) { Text("Accept") }
                        }
                    }
                }
            }
            TextButton(onClick = onCancel) { Text("Cancel") }
        }
    }
}

@Composable
fun SearchingScreen(
    ui: UiState,
    onPick: (NearbyHost) -> Unit,
    onCancel: () -> Unit,
    onDismissNotice: () -> Unit,
) {
    Page {
        Column(
            Modifier.fillMaxWidth(),
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.spacedBy(16.dp),
        ) {
            Spacer(Modifier.height(24.dp))
            Text("Games nearby", style = MaterialTheme.typography.headlineSmall, fontWeight = FontWeight.Bold)
            ui.notice?.let { NoticeCard(it, onDismissNotice) }
            val waitingFor = ui.connectingTo
            if (waitingFor != null) {
                CircularProgressIndicator()
                Text("Waiting for $waitingFor to accept…")
            } else {
                if (ui.hosts.isEmpty()) {
                    CircularProgressIndicator(Modifier.size(32.dp))
                    Text(
                        "Looking for a game…\nAsk your partner to tap “Host a game”.",
                        textAlign = TextAlign.Center,
                    )
                }
                ui.hosts.forEach { host ->
                    Button(onClick = { onPick(host) }, Modifier.fillMaxWidth().height(56.dp)) {
                        Text("Join ${host.name}’s game", fontSize = 18.sp)
                    }
                }
            }
            TextButton(onClick = onCancel) { Text("Cancel") }
        }
    }
}

@Composable
fun LobbyScreen(
    ui: UiState,
    onStart: (rounds: Int, seconds: Int) -> Unit,
    onLeave: () -> Unit,
) {
    var rounds by rememberSaveable { mutableStateOf(6) }
    var seconds by rememberSaveable { mutableStateOf(90) }
    Page {
        Column(
            Modifier.fillMaxWidth(),
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.spacedBy(16.dp),
        ) {
            Spacer(Modifier.height(24.dp))
            Text("🤝", fontSize = 56.sp)
            Text(
                "${ui.name}  ×  ${ui.partnerName}",
                style = MaterialTheme.typography.headlineSmall,
                fontWeight = FontWeight.Bold,
                textAlign = TextAlign.Center,
            )
            Text("Connected!", color = MaterialTheme.colorScheme.tertiary, fontWeight = FontWeight.Bold)
            if (ui.isHost) {
                Text("Rounds (you each draw half)", style = MaterialTheme.typography.titleSmall)
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    listOf(4, 6, 8, 10).forEach { n ->
                        FilterChip(selected = rounds == n, onClick = { rounds = n }, label = { Text("$n") })
                    }
                }
                Text("Time per drawing", style = MaterialTheme.typography.titleSmall)
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    listOf(60, 90, 120).forEach { n ->
                        FilterChip(selected = seconds == n, onClick = { seconds = n }, label = { Text("${n}s") })
                    }
                }
                Button(onClick = { onStart(rounds, seconds) }, Modifier.fillMaxWidth().height(56.dp)) {
                    Text("Start game", fontSize = 18.sp)
                }
            } else {
                CircularProgressIndicator(Modifier.size(32.dp))
                Text("Waiting for ${ui.partnerName} to start the game…")
            }
            TextButton(onClick = onLeave) { Text("Leave") }
        }
    }
}
