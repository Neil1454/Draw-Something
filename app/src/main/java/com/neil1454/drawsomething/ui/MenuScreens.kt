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
import com.neil1454.drawsomething.core.Difficulty
import com.neil1454.drawsomething.core.GameEngine
import com.neil1454.drawsomething.core.GameState
import com.neil1454.drawsomething.core.Rules
import com.neil1454.drawsomething.core.Scoring

const val CREATOR = "Neil Sampson"

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
            Text(
                "Made by $CREATOR",
                style = MaterialTheme.typography.labelLarge,
                fontWeight = FontWeight.Bold,
                color = MaterialTheme.colorScheme.secondary,
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
            Text(
                "© $CREATOR",
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
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
                Text("• Harder words are worth more (×1.25 medium, ×1.5 hard, ×2 extra hard).")
                Text("• Each wrong guess the drawer rejects costs the guesser ${Scoring.WRONG_GUESS_PENALTY}.")
                Text("• Skipping a word costs the drawer ${Scoring.SKIP_PENALTY}.")
                Text("• Breaking a rule (like writing text) costs the drawer ${Scoring.RULE_BREAK_PENALTY} once they agree to a report.")
                Text("• Run out of time and nobody scores. Roles swap every round so it's always even.")
                Text("Draw Something was created by $CREATOR.", fontWeight = FontWeight.Bold)
            }
        },
    )
}

@Composable
private fun PairingCode(code: String) {
    Text(
        code,
        style = MaterialTheme.typography.displayMedium,
        fontWeight = FontWeight.ExtraBold,
        letterSpacing = 8.sp,
        color = MaterialTheme.colorScheme.primary,
    )
}

@Composable
fun HostingScreen(
    ui: UiState,
    onAccept: (String) -> Unit,
    onDecline: (String) -> Unit,
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
            val accepted = ui.hostAccepted
            if (accepted != null) {
                CircularProgressIndicator()
                Text("Connecting to $accepted…", textAlign = TextAlign.Center)
            } else if (ui.joinRequests.isEmpty()) {
                CircularProgressIndicator()
                Text(
                    "Waiting for your partner…\nOn their device tap “Join a game” and pick your name.",
                    textAlign = TextAlign.Center,
                )
                Text(
                    "🔒 Only you and your partner can play: you'll both check a pairing code before anything connects.",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    textAlign = TextAlign.Center,
                )
            } else {
                ui.joinRequests.forEach { req ->
                    Card(Modifier.fillMaxWidth()) {
                        Column(
                            Modifier.padding(16.dp).fillMaxWidth(),
                            horizontalAlignment = Alignment.CenterHorizontally,
                            verticalArrangement = Arrangement.spacedBy(10.dp),
                        ) {
                            Text("${req.name} wants to join your game", style = MaterialTheme.typography.titleMedium)
                            Text("Pairing code", style = MaterialTheme.typography.labelLarge)
                            PairingCode(req.code)
                            Text(
                                "Only accept if this exact code is on ${req.name}'s screen too. " +
                                    "If it isn't, or you don't know them, decline.",
                                style = MaterialTheme.typography.bodySmall,
                                textAlign = TextAlign.Center,
                            )
                            Row(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                                OutlinedButton(onClick = { onDecline(req.id) }, Modifier.weight(1f)) { Text("Decline") }
                                Button(onClick = { onAccept(req.id) }, Modifier.weight(1f)) { Text("Codes match") }
                            }
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
    onConfirmPairing: () -> Unit,
    onCancelPairing: () -> Unit,
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
            val pairing = ui.pairing
            val waitingFor = ui.connectingTo
            if (pairing != null) {
                Card(Modifier.fillMaxWidth()) {
                    Column(
                        Modifier.padding(16.dp).fillMaxWidth(),
                        horizontalAlignment = Alignment.CenterHorizontally,
                        verticalArrangement = Arrangement.spacedBy(10.dp),
                    ) {
                        Text("Pairing with ${pairing.name}", style = MaterialTheme.typography.titleMedium)
                        Text("Pairing code", style = MaterialTheme.typography.labelLarge)
                        PairingCode(pairing.code)
                        if (!ui.pairingConfirmed) {
                            Text(
                                "Check this exact code is showing on ${pairing.name}'s screen. " +
                                    "If it isn't, cancel: you may have reached the wrong device.",
                                style = MaterialTheme.typography.bodySmall,
                                textAlign = TextAlign.Center,
                            )
                            Row(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                                OutlinedButton(onClick = onCancelPairing, Modifier.weight(1f)) { Text("Cancel") }
                                Button(onClick = onConfirmPairing, Modifier.weight(1f)) { Text("Codes match") }
                            }
                        } else {
                            CircularProgressIndicator(Modifier.size(28.dp))
                            Text("Waiting for ${pairing.name} to accept…", textAlign = TextAlign.Center)
                        }
                    }
                }
            } else if (waitingFor != null) {
                CircularProgressIndicator()
                Text("Contacting $waitingFor…")
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
fun RulesList() {
    Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
        Rules.list.forEachIndexed { i, rule ->
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                Text("${i + 1}.", fontWeight = FontWeight.Bold)
                Text(rule)
            }
        }
        Text(
            "Break one and your partner can report it. If you agree, you lose " +
                "${Scoring.RULE_BREAK_PENALTY} points and your drawing is cleared.",
            style = MaterialTheme.typography.bodySmall,
            fontWeight = FontWeight.Bold,
        )
    }
}

@Composable
fun RulesDialog(onClose: () -> Unit) {
    AlertDialog(
        onDismissRequest = onClose,
        confirmButton = { TextButton(onClick = onClose) { Text("Got it") } },
        title = { Text("📜 Rules") },
        text = { Column(Modifier.verticalScroll(rememberScrollState())) { RulesList() } },
    )
}

private val DifficultyChoices = listOf(
    Difficulty.EASY to "Everything",
    Difficulty.MEDIUM to "No easy words",
    Difficulty.HARD to "Hard",
    Difficulty.EXTREME to "Extra hard",
)

@Composable
fun LobbyScreen(
    ui: UiState,
    game: GameState,
    act: (GameEngine.() -> Unit) -> Unit,
    onStart: (rounds: Int, seconds: Int, minDifficulty: Difficulty) -> Unit,
    onLeave: () -> Unit,
) {
    var rounds by rememberSaveable { mutableStateOf(6) }
    var seconds by rememberSaveable { mutableStateOf(90) }
    var diffIdx by rememberSaveable { mutableStateOf(0) }
    val bothAgreed = game.iAgreed && game.partnerAgreed
    Page {
        Column(
            Modifier.fillMaxWidth(),
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.spacedBy(14.dp),
        ) {
            Spacer(Modifier.height(16.dp))
            Text("🤝", fontSize = 48.sp)
            Text(
                "${ui.name}  ×  ${ui.partnerName}",
                style = MaterialTheme.typography.headlineSmall,
                fontWeight = FontWeight.Bold,
                textAlign = TextAlign.Center,
            )
            Text("Connected!", color = MaterialTheme.colorScheme.tertiary, fontWeight = FontWeight.Bold)

            Card(Modifier.fillMaxWidth()) {
                Column(Modifier.padding(14.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {
                    Text("📜 Rules", style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.Bold)
                    RulesList()
                    Button(
                        onClick = { act { agreeRules() } },
                        enabled = !game.iAgreed,
                        modifier = Modifier.fillMaxWidth().height(48.dp),
                    ) { Text(if (game.iAgreed) "✓ You agreed" else "I agree to the rules") }
                    Text(
                        if (game.partnerAgreed) "✓ ${ui.partnerName} agreed" else "Waiting for ${ui.partnerName} to agree…",
                        color = if (game.partnerAgreed) MaterialTheme.colorScheme.tertiary else MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }
            }

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
                Text("Words to draw", style = MaterialTheme.typography.titleSmall)
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    DifficultyChoices.forEachIndexed { i, (_, label) ->
                        FilterChip(selected = diffIdx == i, onClick = { diffIdx = i }, label = { Text(label) })
                    }
                }
                Text(
                    when (DifficultyChoices[diffIdx].first) {
                        Difficulty.EASY -> "A mix of easy to hard, with the odd extra-hard one."
                        Difficulty.MEDIUM -> "No easy words. Medium, hard and extra hard."
                        Difficulty.HARD -> "Hard and extra-hard words only."
                        Difficulty.EXTREME -> "Extra hard only: scenes, actions and ideas. Worth double points!"
                    },
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    textAlign = TextAlign.Center,
                )
                Button(
                    onClick = { onStart(rounds, seconds, DifficultyChoices[diffIdx].first) },
                    enabled = bothAgreed,
                    modifier = Modifier.fillMaxWidth().height(56.dp),
                ) { Text(if (bothAgreed) "Start game" else "Waiting for everyone to agree…", fontSize = 18.sp) }
            } else {
                CircularProgressIndicator(Modifier.size(32.dp))
                Text(
                    if (bothAgreed) "Waiting for ${ui.partnerName} to start the game…"
                    else "Agree to the rules, then ${ui.partnerName} can start.",
                    textAlign = TextAlign.Center,
                )
            }
            TextButton(onClick = onLeave) { Text("Leave") }
        }
    }
}
