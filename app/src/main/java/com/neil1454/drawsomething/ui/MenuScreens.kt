@file:OptIn(ExperimentalMaterial3Api::class)

package com.neil1454.drawsomething.ui

import com.neil1454.drawsomething.core.TetrisMode
import com.neil1454.drawsomething.core.TetrisRules
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
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
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
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import org.json.JSONObject
import com.neil1454.drawsomething.LobbySettings
import com.neil1454.drawsomething.NearbyHost
import com.neil1454.drawsomething.UiState
import com.neil1454.drawsomething.core.Difficulty
import com.neil1454.drawsomething.core.DiceTarget
import com.neil1454.drawsomething.core.DropSize
import com.neil1454.drawsomething.core.FlipSize
import com.neil1454.drawsomething.core.HiLoRows
import com.neil1454.drawsomething.core.HolidayLength
import com.neil1454.drawsomething.core.HolidayPacks
import com.neil1454.drawsomething.core.HolidayRules
import com.neil1454.drawsomething.core.availableGames
import com.neil1454.drawsomething.core.MemorySize
import com.neil1454.drawsomething.core.MemoryThemes
import com.neil1454.drawsomething.core.QuickMatch
import com.neil1454.drawsomething.core.GameEngine
import com.neil1454.drawsomething.core.GameKind
import com.neil1454.drawsomething.core.MineSize
import com.neil1454.drawsomething.core.SeaSize
import com.neil1454.drawsomething.core.GameState
import com.neil1454.drawsomething.core.Rules
import com.neil1454.drawsomething.core.Scoring
import com.neil1454.drawsomething.core.Skill

const val CREATOR = "Neil Sampson"
const val APP_NAME = "Neil & Teri's Time Killer Games"
const val APP_TAGLINE = "Ideal for those long flights where internet is miles away."

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
    appVersion: String,
    onCheckUpdate: () -> Unit,
    onSolo: (Skill) -> Unit,
    onLearn: () -> Unit,
    onBoards: () -> Unit,
    myRecord: String?,
    soundOn: Boolean,
    onSound: (Boolean) -> Unit,
) {
    var help by rememberSaveable { mutableStateOf(false) }
    var pickSkill by rememberSaveable { mutableStateOf(false) }
    var info by rememberSaveable { mutableStateOf<GameKind?>(null) }
    Page {
        Column(
            Modifier.fillMaxWidth(),
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.spacedBy(14.dp),
        ) {
            Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
                Text("v$appVersion", style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                Spacer(Modifier.weight(1f))
                RoundIconButton(if (soundOn) "🔊" else "🔇", { onSound(!soundOn) })
            }
            // ---- hero
            Row(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                availableGames().forEachIndexed { i, k ->
                    val b = bobbing(phase = if (i % 2 == 0) 0f else 1f, amount = 6f, millis = 1100 + i * 90)
                    Text(k.emoji, Modifier.graphicsLayer { translationY = b * density }, fontSize = 24.sp)
                }
            }
            GradientText(APP_NAME, fontSize = 34.sp)
            Text(
                "✈️ $APP_TAGLINE",
                textAlign = TextAlign.Center,
                fontWeight = FontWeight.SemiBold,
                color = Funk.Sun,
                fontSize = 14.sp,
            )

            // ---- who are you
            GlassCard(Modifier.fillMaxWidth()) {
                Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                        Box(
                            Modifier.size(46.dp).clip(CircleShape).background(Brush.linearGradient(listOf(Funk.Pink, Funk.Orange))),
                            contentAlignment = Alignment.Center,
                        ) {
                            Text(ui.name.trim().take(1).uppercase().ifEmpty { "?" }, fontSize = 22.sp, fontWeight = FontWeight.Black, color = Color.White)
                        }
                        OutlinedTextField(
                            value = ui.name,
                            onValueChange = onName,
                            label = { Text("Your name") },
                            singleLine = true,
                            modifier = Modifier.weight(1f),
                        )
                    }
                    if (myRecord != null) {
                        Text(myRecord, fontSize = 13.sp, fontWeight = FontWeight.SemiBold, color = Funk.Sun)
                    }
                }
            }
            ui.notice?.let { NoticeCard(it, onDismissNotice) }

            // ---- play
            Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                ActionCard("🎮", "Host", "Start a game for a friend nearby", listOf(Funk.Pink, Funk.Orange), onHost, Modifier.weight(1f))
                ActionCard("🔎", "Join", "Find a friend's game", listOf(Funk.Cyan, Funk.Blue), onJoin, Modifier.weight(1f))
            }
            Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                ActionCard("🤖", "Solo", "Play the computer", listOf(Funk.Mint, Color(0xFF0E8F9F)), { pickSkill = true }, Modifier.weight(1f), big = false)
                ActionCard("📖", "Learn", "Watch & learn", listOf(Funk.Sun, Funk.Orange), onLearn, Modifier.weight(1f), big = false)
                ActionCard("🏆", "Scores", "Leaderboards", listOf(Funk.Violet, Funk.Pink), onBoards, Modifier.weight(1f), big = false)
            }

            // ---- the games
            Text("The games", style = MaterialTheme.typography.titleLarge, modifier = Modifier.padding(top = 6.dp))
            Text(
                "Tap one to see how it works.",
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
            GameGrid(availableGames(), selected = null, enabled = true) { info = it }
            TextButton(onClick = { help = true }) { Text("How to play & scoring") }

            // ---- footer
            GlassCard(Modifier.fillMaxWidth()) {
                Column(horizontalAlignment = Alignment.CenterHorizontally, verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    FunkyButton("🔄  Check for updates", onCheckUpdate, Modifier.fillMaxWidth(), listOf(Funk.Violet, Funk.Blue), fontSize = 16.sp)
                    Text(
                        "Checking for updates is the only time this app uses the internet; the games never do. " +
                            "Both phones just need Bluetooth and/or Wi-Fi on, so it works on a plane (turn Bluetooth back on after " +
                            "enabling airplane mode). On Android 11 or older, Location must also be on.",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        textAlign = TextAlign.Center,
                    )
                }
            }
            Text(
                "© $CREATOR",
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }
    }
    if (help) HowToPlayDialog { help = false }
    if (pickSkill) {
        AlertDialog(
            onDismissRequest = { pickSkill = false },
            confirmButton = {},
            dismissButton = { TextButton(onClick = { pickSkill = false }) { Text("Cancel") } },
            title = { Text("🤖 Play against the computer") },
            text = {
                Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
                    Text("Great for getting a feel for a game, or when you're on your own. Choose how clever the computer is:")
                    Skill.values().forEach { sk ->
                        FunkyButton(
                            "${sk.emoji}  ${sk.label}",
                            { pickSkill = false; onSolo(sk) },
                            Modifier.fillMaxWidth(),
                            when (sk) {
                                Skill.EASY -> listOf(Funk.Mint, Funk.Cyan)
                                Skill.NORMAL -> listOf(Funk.Violet, Funk.Blue)
                                Skill.HARD -> listOf(Funk.Pink, Funk.Red)
                            },
                            fontSize = 17.sp,
                        )
                    }
                    Text(
                        "Draw Something is practice only: the computer can't read drawings, so it pretends to guess.",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }
            },
        )
    }
    info?.let { GameInfoDialog(it) { info = null } }
}

/** A short explainer for one game, shown from the home screen. */
@Composable
fun GameInfoDialog(kind: GameKind, onClose: () -> Unit) {
    AlertDialog(
        onDismissRequest = onClose,
        confirmButton = { TextButton(onClick = onClose) { Text("Got it") } },
        title = { Text("${kind.emoji} ${kind.label}") },
        text = {
            Column(Modifier.verticalScroll(rememberScrollState()), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                when (kind) {
                    GameKind.DRAW -> {
                        Text(kind.blurb)
                        RulesList()
                    }
                    GameKind.MINES -> MinesHelp()
                    GameKind.SEA -> SeaHelp()
                    GameKind.DROP -> DropHelp()
                    GameKind.MEMORY -> MemoryHelp()
                    GameKind.QUICK -> QuickHelp()
                    GameKind.DICE -> DiceHelp()
                    GameKind.FLIP -> FlipHelp()
                    GameKind.HILO -> HiLoHelp()
                    GameKind.HOLIDAY -> HolidayHelp()
                    GameKind.TETRIS -> TetrisHelp()
                }
            }
        },
    )
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
                Text("$APP_NAME was created by $CREATOR.", fontWeight = FontWeight.Bold)
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
    browse: @Composable () -> Unit,
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
            BrowseHeader("While you wait, pick your game and set it up")
            browse()
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
    browse: @Composable () -> Unit,
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
            BrowseHeader("While you search, have a look at the games")
            browse()
        }
    }
}

@Composable
private fun BrowseHeader(text: String) {
    Column(horizontalAlignment = Alignment.CenterHorizontally, verticalArrangement = Arrangement.spacedBy(2.dp)) {
        Text("🎮", fontSize = 28.sp)
        Text(text, style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.Bold, textAlign = TextAlign.Center)
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

/**
 * The game picker plus the selected game's rules and setup. Used on the waiting screens (so you can
 * browse and prepare before anyone has joined: [game] is null) and in the lobby once paired.
 *
 * - Host: can pick a game and set it up at any time; Start unlocks once paired (and, for Draw
 *   Something, once both players have agreed to the rules).
 * - Joiner before pairing: can look through every game's rules and how-to-play.
 * - Joiner once paired: sees the host's pick.
 */
@Composable
fun GameBrowser(
    ui: UiState,
    game: GameState?,
    act: (GameEngine.() -> Unit) -> Unit,
    selected: GameKind,
    canPick: Boolean,
    onSelect: (GameKind) -> Unit,
    onSettings: (LobbySettings) -> Unit,
    onStartDraw: (rounds: Int, seconds: Int, minDifficulty: Difficulty) -> Unit,
    onStartMines: (MineSize) -> Unit,
    onStartSea: (SeaSize) -> Unit,
    onStartSync: (GameKind, JSONObject) -> Unit,
) {
    val connected = game != null
    Column(
        Modifier.fillMaxWidth(),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.spacedBy(12.dp),
    ) {
        GameGrid(availableGames(), selected = selected, enabled = canPick) { onSelect(it) }
        when (selected) {
            GameKind.DRAW -> DrawLobby(ui, game, act, onSettings, onStartDraw)
            GameKind.MINES -> MinesLobby(ui, connected, onSettings, onStartMines)
            GameKind.SEA -> SeaLobby(ui, connected, onSettings, onStartSea)
            GameKind.DROP -> DropLobby(ui, connected, onSettings, onStartSync)
            GameKind.MEMORY -> MemoryLobby(ui, connected, onSettings, onStartSync)
            GameKind.QUICK -> QuickLobby(ui, connected, onSettings, onStartSync)
            GameKind.DICE -> DiceLobby(ui, connected, onSettings, onStartSync)
            GameKind.FLIP -> FlipLobby(ui, connected, onSettings, onStartSync)
            GameKind.HILO -> HiLoLobby(ui, connected, onSettings, onStartSync)
            GameKind.HOLIDAY -> HolidayLobby(ui, connected, onSettings, onStartSync)
            GameKind.TETRIS -> TetrisLobby(ui, connected, onSettings, onStartSync)
        }
    }
}

/** What the waiting screens show: the host's own pick, or (for a joiner) whatever they feel like reading. */
@Composable
fun BrowseGames(
    ui: UiState,
    onPickGame: (GameKind) -> Unit,
    onSettings: (LobbySettings) -> Unit,
) {
    var viewGame by rememberSaveable { mutableStateOf(GameKind.DRAW) }
    GameBrowser(
        ui = ui,
        game = null,
        act = {},
        selected = if (ui.isHost) ui.lobbyGame else viewGame,
        canPick = true,
        onSelect = { if (ui.isHost) onPickGame(it) else viewGame = it },
        onSettings = onSettings,
        onStartDraw = { _, _, _ -> },
        onStartMines = {},
        onStartSea = {},
        onStartSync = { _, _ -> },
    )
}

@Composable
fun LobbyScreen(
    ui: UiState,
    game: GameState,
    act: (GameEngine.() -> Unit) -> Unit,
    onPickGame: (GameKind) -> Unit,
    onSettings: (LobbySettings) -> Unit,
    onStartDraw: (rounds: Int, seconds: Int, minDifficulty: Difficulty) -> Unit,
    onStartMines: (MineSize) -> Unit,
    onStartSea: (SeaSize) -> Unit,
    onStartSync: (GameKind, JSONObject) -> Unit,
    onLeave: () -> Unit,
) {
    Page {
        Column(
            Modifier.fillMaxWidth(),
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.spacedBy(14.dp),
        ) {
            Spacer(Modifier.height(8.dp))
            Text(
                "🤝  ${ui.name}  ×  ${ui.partnerName}",
                style = MaterialTheme.typography.headlineSmall,
                fontWeight = FontWeight.Bold,
                textAlign = TextAlign.Center,
            )
            Text(
                if (ui.solo) "Solo practice (${ui.soloSkill?.label ?: ""}). Pick any game and press Start."
                else "Connected! Your link is private and encrypted.",
                color = MaterialTheme.colorScheme.tertiary, fontWeight = FontWeight.Bold, textAlign = TextAlign.Center,
            )
            Text(
                if (ui.isHost) "Choose a game" else "${ui.partnerName} is choosing the game…",
                style = MaterialTheme.typography.titleMedium,
            )
            GameBrowser(
                ui = ui, game = game, act = act,
                selected = ui.lobbyGame, canPick = ui.isHost, onSelect = onPickGame,
                onSettings = onSettings,
                onStartDraw = onStartDraw, onStartMines = onStartMines, onStartSea = onStartSea,
                onStartSync = onStartSync,
            )
            TextButton(onClick = onLeave) { Text("Leave") }
        }
    }
}

@Composable
private fun WaitingForHost(ui: UiState, text: String) {
    CircularProgressIndicator(Modifier.size(28.dp))
    Text(text.replace("{name}", ui.partnerName), textAlign = TextAlign.Center)
}

@Composable
private fun DrawLobby(
    ui: UiState,
    game: GameState?,
    act: (GameEngine.() -> Unit) -> Unit,
    onSettings: (LobbySettings) -> Unit,
    onStart: (Int, Int, Difficulty) -> Unit,
) {
    val set = ui.settings
    val bothAgreed = game != null && game.iAgreed && game.partnerAgreed
    Column(Modifier.fillMaxWidth(), verticalArrangement = Arrangement.spacedBy(12.dp), horizontalAlignment = Alignment.CenterHorizontally) {
        Card(Modifier.fillMaxWidth()) {
            Column(Modifier.padding(14.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {
                Text("📜 Rules", style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.Bold)
                RulesList()
                if (ui.solo) {
                    Text(
                        "Solo: you draw every round and the computer pretends to guess, so you can practise the drawing tools and the scoring.",
                        style = MaterialTheme.typography.bodySmall, fontWeight = FontWeight.Bold,
                    )
                }
                if (game == null) {
                    Text(
                        "Once you're connected you'll both tap “I agree” before the game starts.",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                } else {
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
        }
        if (ui.isHost) {
            Text("Rounds (you each draw half)", style = MaterialTheme.typography.titleSmall)
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                listOf(4, 6, 8, 10).forEach { n ->
                    FilterChip(selected = set.rounds == n, onClick = { onSettings(set.copy(rounds = n)) }, label = { Text("$n") })
                }
            }
            Text("Time per drawing", style = MaterialTheme.typography.titleSmall)
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                listOf(60, 90, 120).forEach { n ->
                    FilterChip(selected = set.seconds == n, onClick = { onSettings(set.copy(seconds = n)) }, label = { Text("${n}s") })
                }
            }
            Text("Words to draw", style = MaterialTheme.typography.titleSmall)
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                DifficultyChoices.forEachIndexed { i, (_, label) ->
                    FilterChip(selected = set.difficulty == i, onClick = { onSettings(set.copy(difficulty = i)) }, label = { Text(label) })
                }
            }
            Text(
                when (DifficultyChoices[set.difficulty].first) {
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
                onClick = { onStart(set.rounds, set.seconds, DifficultyChoices[set.difficulty].first) },
                enabled = bothAgreed,
                modifier = Modifier.fillMaxWidth().height(56.dp),
            ) {
                Text(
                    when {
                        game == null -> "Start (waiting for your partner…)"
                        bothAgreed -> "Start game"
                        else -> "Waiting for everyone to agree…"
                    },
                    fontSize = 18.sp,
                )
            }
        } else if (game != null) {
            WaitingForHost(
                ui,
                if (bothAgreed) "Waiting for {name} to start the game…" else "Agree to the rules, then {name} can start.",
            )
        }
    }
}

@Composable
private fun MinesLobby(ui: UiState, connected: Boolean, onSettings: (LobbySettings) -> Unit, onStart: (MineSize) -> Unit) {
    val set = ui.settings
    Column(Modifier.fillMaxWidth(), verticalArrangement = Arrangement.spacedBy(12.dp), horizontalAlignment = Alignment.CenterHorizontally) {
        Card(Modifier.fillMaxWidth()) {
            Column(Modifier.padding(14.dp)) { MinesHelp() }
        }
        if (ui.isHost) {
            Text("Grid size", style = MaterialTheme.typography.titleSmall)
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                MineSize.values().forEachIndexed { i, size ->
                    FilterChip(
                        selected = set.minesSize == i,
                        onClick = { onSettings(set.copy(minesSize = i)) },
                        label = { Text(size.label.substringBefore(' ') + "\n" + size.label.substringAfter(' ')) },
                    )
                }
            }
            val size = MineSize.values()[set.minesSize]
            Text(
                "${size.w}×${size.h} grid · ${size.mines} bombs · ${size.items} power-ups",
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
            Button(onClick = { onStart(size) }, enabled = connected, modifier = Modifier.fillMaxWidth().height(56.dp)) {
                Text(if (connected) "Start Bomb Find" else "Start (waiting for your partner…)", fontSize = 18.sp)
            }
        } else if (connected) {
            WaitingForHost(ui, "Waiting for {name} to start the game…")
        }
    }
}

@Composable
private fun SeaLobby(ui: UiState, connected: Boolean, onSettings: (LobbySettings) -> Unit, onStart: (SeaSize) -> Unit) {
    val set = ui.settings
    Column(Modifier.fillMaxWidth(), verticalArrangement = Arrangement.spacedBy(12.dp), horizontalAlignment = Alignment.CenterHorizontally) {
        Card(Modifier.fillMaxWidth()) {
            Column(Modifier.padding(14.dp)) { SeaHelp() }
        }
        if (ui.isHost) {
            Text("Sea size", style = MaterialTheme.typography.titleSmall)
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                SeaSize.values().forEachIndexed { i, size ->
                    FilterChip(selected = set.seaSize == i, onClick = { onSettings(set.copy(seaSize = i)) }, label = { Text(size.label) })
                }
            }
            val size = SeaSize.values()[set.seaSize]
            Text(
                "${size.fleet.size} ships each: " + size.fleet.joinToString(", ") { "$it long" },
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
            Button(onClick = { onStart(size) }, enabled = connected, modifier = Modifier.fillMaxWidth().height(56.dp)) {
                Text(if (connected) "Start Sea Battle" else "Start (waiting for your partner…)", fontSize = 18.sp)
            }
        } else if (connected) {
            WaitingForHost(ui, "Waiting for {name} to start the game…")
        }
    }
}

// ---------------------------------------------------------------------------- the turn-based games

/** The same shape for every sync game: how-to-play card, the host's options, and a Start button. */
@Composable
private fun SyncLobby(
    ui: UiState,
    connected: Boolean,
    kind: GameKind,
    help: @Composable () -> Unit,
    options: @Composable () -> Unit,
    onStart: () -> Unit,
) {
    Column(Modifier.fillMaxWidth(), verticalArrangement = Arrangement.spacedBy(12.dp), horizontalAlignment = Alignment.CenterHorizontally) {
        Card(Modifier.fillMaxWidth()) { Column(Modifier.padding(14.dp)) { help() } }
        if (ui.isHost) {
            options()
            FunkyButton(
                if (connected) "${kind.emoji}  Start ${kind.label}" else "Start (waiting for your partner…)",
                onStart, Modifier.fillMaxWidth(), kind.colors(), enabled = connected, fontSize = 18.sp,
            )
        } else if (connected) {
            WaitingForHost(ui, "Waiting for {name} to start the game…")
        }
    }
}

@Composable
private fun ChipRow(title: String, labels: List<String>, selected: Int, onSelect: (Int) -> Unit) {
    Column(verticalArrangement = Arrangement.spacedBy(6.dp), horizontalAlignment = Alignment.CenterHorizontally) {
        Text(title, style = MaterialTheme.typography.titleSmall)
        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            labels.forEachIndexed { i, l -> FilterChip(selected = selected == i, onClick = { onSelect(i) }, label = { Text(l) }) }
        }
    }
}

@Composable
private fun ToggleChip(label: String, on: Boolean, onChange: (Boolean) -> Unit) {
    FilterChip(selected = on, onClick = { onChange(!on) }, label = { Text((if (on) "✓ " else "") + label) })
}

@Composable
private fun DropLobby(ui: UiState, connected: Boolean, onSettings: (LobbySettings) -> Unit, onStart: (GameKind, JSONObject) -> Unit) {
    val set = ui.settings
    SyncLobby(ui, connected, GameKind.DROP, { DropHelp() }, {
        ChipRow("Board", DropSize.values().map { it.label.substringBefore(" ·") }, set.dropSize) { onSettings(set.copy(dropSize = it)) }
        Text(DropSize.values()[set.dropSize].label.substringAfter("· "), style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
        ChipRow("Disc colours", listOf("Classic 🔴🟡", "Neon 🩷🩵", "Sunset 🟠🟣"), set.dropTheme) { onSettings(set.copy(dropTheme = it)) }
        ToggleChip("💣 Power discs (bomb and double)", set.dropPower) { onSettings(set.copy(dropPower = it)) }
    }) { onStart(GameKind.DROP, JSONObject().put("size", set.dropSize).put("power", set.dropPower).put("theme", set.dropTheme)) }
}

@Composable
private fun MemoryLobby(ui: UiState, connected: Boolean, onSettings: (LobbySettings) -> Unit, onStart: (GameKind, JSONObject) -> Unit) {
    val set = ui.settings
    SyncLobby(ui, connected, GameKind.MEMORY, { MemoryHelp() }, {
        ChipRow("Board", MemorySize.values().map { it.label.substringAfter(' ') }, set.memorySize) { onSettings(set.copy(memorySize = it)) }
        ChipRow("Cards", MemoryThemes.names, set.memoryTheme) { onSettings(set.copy(memoryTheme = it)) }
        Text(MemoryThemes.symbols[set.memoryTheme].take(8).joinToString(" "), fontSize = 22.sp)
    }) { onStart(GameKind.MEMORY, JSONObject().put("size", set.memorySize).put("theme", set.memoryTheme)) }
}

@Composable
private fun QuickLobby(ui: UiState, connected: Boolean, onSettings: (LobbySettings) -> Unit, onStart: (GameKind, JSONObject) -> Unit) {
    val set = ui.settings
    SyncLobby(ui, connected, GameKind.QUICK, { QuickHelp() }, {
        ChipRow("Match", QuickMatch.values().map { it.label }, set.quickMatch) { onSettings(set.copy(quickMatch = it)) }
        ToggleChip("🙂 Fake-outs (decoy flashes)", set.quickFake) { onSettings(set.copy(quickFake = it)) }
    }) { onStart(GameKind.QUICK, JSONObject().put("match", set.quickMatch).put("fake", set.quickFake)) }
}

@Composable
private fun DiceLobby(ui: UiState, connected: Boolean, onSettings: (LobbySettings) -> Unit, onStart: (GameKind, JSONObject) -> Unit) {
    val set = ui.settings
    SyncLobby(ui, connected, GameKind.DICE, { DiceHelp() }, {
        ChipRow("Play to", DiceTarget.values().map { it.label.substringAfter(": ") }, set.diceTarget) { onSettings(set.copy(diceTarget = it)) }
        ToggleChip("🎲🎲 Two dice (snake eyes wipes your score!)", set.diceTwo) { onSettings(set.copy(diceTwo = it)) }
    }) { onStart(GameKind.DICE, JSONObject().put("target", set.diceTarget).put("two", set.diceTwo)) }
}

@Composable
private fun FlipLobby(ui: UiState, connected: Boolean, onSettings: (LobbySettings) -> Unit, onStart: (GameKind, JSONObject) -> Unit) {
    val set = ui.settings
    SyncLobby(ui, connected, GameKind.FLIP, { FlipHelp() }, {
        ChipRow("Board", FlipSize.values().map { it.label }, set.flipSize) { onSettings(set.copy(flipSize = it)) }
    }) { onStart(GameKind.FLIP, JSONObject().put("size", set.flipSize)) }
}

@Composable
private fun HiLoLobby(ui: UiState, connected: Boolean, onSettings: (LobbySettings) -> Unit, onStart: (GameKind, JSONObject) -> Unit) {
    val set = ui.settings
    SyncLobby(ui, connected, GameKind.HILO, { HiLoHelp() }, {
        ChipRow("Rows each", HiLoRows.values().map { it.rows.toString() }, set.hiloRows) { onSettings(set.copy(hiloRows = it)) }
        ToggleChip("📊 Show the odds", set.hiloOdds) { onSettings(set.copy(hiloOdds = it)) }
    }) { onStart(GameKind.HILO, JSONObject().put("rows", set.hiloRows).put("odds", set.hiloOdds)) }
}

@Composable
private fun HolidayLobby(ui: UiState, connected: Boolean, onSettings: (LobbySettings) -> Unit, onStart: (GameKind, JSONObject) -> Unit) {
    val set = ui.settings
    val packs = HolidayPacks.all()
    val packIdx = packs.indexOfFirst { it.id == set.holidayPack }.coerceAtLeast(0)
    val secsIdx = set.holidaySecs.coerceIn(0, HolidayRules.SECONDS.lastIndex)
    SyncLobby(ui, connected && packs.isNotEmpty(), GameKind.HOLIDAY, { HolidayHelp() }, {
        if (packs.isEmpty()) {
            Text("No holidays are installed in this version.", color = MaterialTheme.colorScheme.onSurfaceVariant)
        } else {
            if (packs.size > 1) {
                ChipRow("Holiday", packs.map { "${it.emoji} ${it.title}" }, packIdx) { onSettings(set.copy(holidayPack = packs[it].id)) }
            } else {
                Text("${packs[0].emoji} ${packs[0].title}  ·  ${packs[0].questions.size} questions to draw from", fontWeight = FontWeight.SemiBold)
            }
            packs.getOrNull(packIdx)?.blurb?.takeIf { it.isNotBlank() }?.let {
                Text(it, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant, textAlign = TextAlign.Center)
            }
            ChipRow("Questions", HolidayLength.values().map { it.label }, set.holidayLen) { onSettings(set.copy(holidayLen = it)) }
            ChipRow("Time per question", HolidayRules.SECONDS.map { if (it == 0) "No timer" else "${it}s" }, secsIdx) { onSettings(set.copy(holidaySecs = it)) }
        }
    }) {
        onStart(
            GameKind.HOLIDAY,
            JSONObject().put("pack", packs.getOrNull(packIdx)?.id ?: "").put("len", set.holidayLen).put("secs", HolidayRules.SECONDS[secsIdx]),
        )
    }
}

@Composable
private fun TetrisLobby(ui: UiState, connected: Boolean, onSettings: (LobbySettings) -> Unit, onStart: (GameKind, JSONObject) -> Unit) {
    val set = ui.settings
    val mode = set.tetrisMode.coerceIn(0, TetrisMode.COUNT - 1)
    val info = TetrisMode.info(mode)
    SyncLobby(ui, connected, GameKind.TETRIS, { TetrisHelp() }, {
        Text("Game mode", style = MaterialTheme.typography.titleSmall)
        TetrisMode.ALL.chunked(2).forEach { pair ->
            Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                pair.forEach { m ->
                    FilterChip(
                        selected = mode == m.id,
                        onClick = { onSettings(set.copy(tetrisMode = m.id)) },
                        label = { Text("${m.emoji} ${m.label}", maxLines = 1) },
                        modifier = Modifier.weight(1f),
                    )
                }
                if (pair.size == 1) Spacer(Modifier.weight(1f))
            }
        }
        Text(info.blurb, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant, textAlign = TextAlign.Center)
        ChipRow("Start speed", TetrisRules.LEVEL_LABELS, set.tetrisLevel) { onSettings(set.copy(tetrisLevel = it)) }
        if (mode == TetrisMode.SCORE) {
            ChipRow("Match length", TetrisRules.MINUTES.map { "$it min" }, set.tetrisMins) { onSettings(set.copy(tetrisMins = it)) }
        }
        if (mode == TetrisMode.SPRINT) {
            ChipRow("Race to", TetrisRules.LINE_TARGETS.map { "$it lines" }, set.tetrisLines) { onSettings(set.copy(tetrisLines = it)) }
        }
        if (TetrisMode.giftsOptional(mode)) {
            ToggleChip("🎁 Gift pieces", set.tetrisGifts) { onSettings(set.copy(tetrisGifts = it)) }
        }
    }) {
        onStart(
            GameKind.TETRIS,
            JSONObject().put("mode", mode).put("lvl", set.tetrisLevel).put("mins", set.tetrisMins)
                .put("gifts", set.tetrisGifts).put("tl", set.tetrisLines),
        )
    }
}
