@file:OptIn(ExperimentalMaterial3Api::class)

package com.neil1454.drawsomething.ui

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FilterChip
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.neil1454.drawsomething.core.SeaEngine
import com.neil1454.drawsomething.core.SeaPhase
import com.neil1454.drawsomething.core.SeaState
import com.neil1454.drawsomething.core.SeaWeapon
import kotlin.math.abs

typealias ActSea = (SeaEngine.() -> Unit) -> Unit

private val Water = Color(0xFFBBDEFB)
private val ShipGrey = Color(0xFF546E7A)
private val Radar = Color(0xFFFFB300)

@Composable
fun SeaHelp() {
    Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
        Text("Each of you has a hidden fleet. Take turns firing at the other's grid.")
        Text("• 🎯 Shot: one square. A HIT means you fire again!")
        Text("• 📡 Radar (once): pick a spot and it counts the ship squares in the 3×3 area around it. It uses your turn.")
        Text("• 💥 Cluster bomb (once): hits a plus-shaped area of 5 squares. It uses your turn.")
        Text("Sink every enemy ship to win. Your fleet is placed for you; shuffle until you like it.")
    }
}

@Composable
private fun SeaCell(ch: Char, own: Boolean, radar: Boolean, canTap: Boolean, sp: Float, onTap: () -> Unit) {
    val bg = when (ch) {
        'S' -> ShipGrey
        'x' -> Color(0xFFEF5350)
        '#' -> Color(0xFF7B1F1F)
        'o' -> Color(0xFFE3F2FD)
        else -> Water
    }
    Box(
        Modifier
            .fillMaxSize()
            .padding(1.dp)
            .clip(RoundedCornerShape(3.dp))
            .background(bg)
            .let { if (radar) it.border(2.dp, Radar, RoundedCornerShape(3.dp)) else it }
            .clickable(enabled = canTap && ch == '.', onClick = onTap),
        contentAlignment = Alignment.Center,
    ) {
        when (ch) {
            'x' -> Text("💥", fontSize = (sp * 0.8f).sp)
            '#' -> Text("☠️", fontSize = (sp * 0.8f).sp)
            'o' -> Text("•", fontSize = sp.sp, color = Color(0xFF64B5F6))
            else -> Unit
        }
    }
}

// ---------------------------------------------------------------- placing the fleet

@Composable
fun SeaPlacingScreen(s: SeaState, act: ActSea, onLeave: () -> Unit) {
    Page {
        Column(
            Modifier.fillMaxWidth(),
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.spacedBy(12.dp),
        ) {
            Text("🚢 Your fleet", style = MaterialTheme.typography.headlineSmall, fontWeight = FontWeight.Bold)
            Text(
                "Only you can see this. Shuffle until you like it, then tap Ready.",
                textAlign = TextAlign.Center, style = MaterialTheme.typography.bodyMedium,
            )
            SquareGrid(s.size.w, s.size.h, Modifier.fillMaxWidth().aspectRatio(1f)) { x, y, cellDp ->
                SeaCell(s.myGrid.getOrElse(y * s.size.w + x) { '.' }, own = true, radar = false, canTap = false, sp = cellDp * 0.5f) {}
            }
            Row(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                OutlinedButton(onClick = { act { shuffleFleet() } }, enabled = !s.iAmReady) { Text("🎲 Shuffle fleet") }
                Button(onClick = { act { ready() } }, enabled = !s.iAmReady) {
                    Text(if (s.iAmReady) "✓ Ready" else "Ready!")
                }
            }
            if (s.iAmReady && !s.theyAreReady) {
                CircularProgressIndicator(Modifier.size(28.dp))
                Text("Waiting for ${s.theirName} to get ready…")
            } else if (!s.iAmReady && s.theyAreReady) {
                Text("${s.theirName} is ready and waiting for you!", fontWeight = FontWeight.Bold)
            }
            TextButton(onClick = onLeave) { Text("Leave") }
        }
    }
}

// ---------------------------------------------------------------- battle

@Composable
fun SeaBattleScreen(s: SeaState, act: ActSea) {
    var help by rememberSaveable { mutableStateOf(false) }
    val canFire = s.myTurn && !s.awaiting
    TwoPane(
        top = {
            Text(
                when {
                    s.awaiting -> "💨 Waiting for the result…"
                    s.myTurn -> "👉 Your turn: choose a weapon, tap the enemy grid"
                    else -> "⏳ ${s.theirName} is aiming…"
                },
                style = MaterialTheme.typography.titleMedium,
                fontWeight = FontWeight.Bold,
                color = if (canFire) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.onSurface,
            )
            Text(
                "Enemy ships left: ${s.theirShipsLeft} · Yours: ${s.myShipsLeft}",
                style = MaterialTheme.typography.bodySmall,
            )
            s.note?.let { Text(it, style = MaterialTheme.typography.bodyMedium, fontWeight = FontWeight.Bold) }
            if (s.radarCount >= 0) {
                Text(
                    "📡 Last radar: ${s.radarCount} ship square${if (s.radarCount == 1) "" else "s"} in the orange area",
                    style = MaterialTheme.typography.bodySmall,
                )
            }
        },
        board = {
            SquareGrid(s.size.w, s.size.h, Modifier.fillMaxSize()) { x, y, cellDp ->
                SeaCell(
                    ch = s.enemyGrid.getOrElse(y * s.size.w + x) { '.' },
                    own = false,
                    radar = s.radarCount >= 0 && abs(x - s.radarX) <= 1 && abs(y - s.radarY) <= 1,
                    canTap = canFire,
                    sp = cellDp * 0.5f,
                    onTap = { act { fire(x, y) } },
                )
            }
        },
        bottom = {
            Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                SeaWeapon.values().forEach { w ->
                    val available = when (w) {
                        SeaWeapon.SHOT -> true
                        SeaWeapon.RADAR -> s.radarLeft
                        SeaWeapon.BOMB -> s.bombLeft
                    }
                    FilterChip(
                        selected = s.weapon == w,
                        onClick = { act { selectWeapon(w) } },
                        enabled = available && canFire,
                        label = { Text("${w.emoji} ${if (w == SeaWeapon.BOMB) "Bomb" else w.label}") },
                    )
                }
            }
            Text(s.weapon.emoji + " " + s.weapon.blurb, style = MaterialTheme.typography.bodySmall)
            Text("Your fleet", style = MaterialTheme.typography.labelMedium)
            SquareGrid(s.size.w, s.size.h, Modifier.size(150.dp)) { x, y, cellDp ->
                SeaCell(s.myGrid.getOrElse(y * s.size.w + x) { '.' }, own = true, radar = false, canTap = false, sp = cellDp * 0.5f) {}
            }
            TextButton(onClick = { help = true }) { Text("❓ How to play") }
        },
    )
    if (help) {
        AlertDialog(
            onDismissRequest = { help = false },
            confirmButton = { TextButton(onClick = { help = false }) { Text("Got it") } },
            title = { Text("🚢 Sea Battle") },
            text = { Column(Modifier.verticalScroll(rememberScrollState())) { SeaHelp() } },
        )
    }
}

// ---------------------------------------------------------------- game over

@Composable
fun SeaOverScreen(s: SeaState, onPlayAgain: () -> Unit, onBackToGames: () -> Unit, onLeave: () -> Unit) {
    val won = s.iWon == true
    Page {
        Column(
            Modifier.fillMaxWidth(),
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.spacedBy(12.dp),
        ) {
            Spacer(Modifier.height(16.dp))
            Text(
                if (won) "🏆 You win!" else "💀 ${s.theirName} wins!",
                style = MaterialTheme.typography.displaySmall, fontWeight = FontWeight.Bold, textAlign = TextAlign.Center,
            )
            s.note?.let { Text(it, textAlign = TextAlign.Center) }
            Card(Modifier.fillMaxWidth(), colors = CardDefaults.cardColors()) {
                Column(Modifier.padding(14.dp), verticalArrangement = Arrangement.spacedBy(4.dp)) {
                    val acc = if (s.shots > 0) (100 * s.hits / s.shots) else 0
                    Text("Your shots: ${s.shots} · hits: ${s.hits} · accuracy: $acc%")
                    Text("Enemy ships you sank: ${s.size.fleet.size - s.theirShipsLeft} of ${s.size.fleet.size}")
                    Text("Your ships left: ${s.myShipsLeft} of ${s.size.fleet.size}")
                }
            }
            if (s.isHost) {
                Button(onClick = onPlayAgain, Modifier.fillMaxWidth().height(52.dp)) { Text("Play again (${s.size.label})", fontSize = 18.sp) }
            } else {
                Text("${s.theirName} can start a rematch…", color = MaterialTheme.colorScheme.onSurfaceVariant)
            }
            TextButton(onClick = onBackToGames) { Text("Choose another game") }
            TextButton(onClick = onLeave) { Text("Leave") }
        }
    }
}

/** Picks the right Sea Battle screen for the current phase. */
@Composable
fun SeaRoot(s: SeaState, act: ActSea, onPlayAgain: () -> Unit, onBackToGames: () -> Unit, onLeave: () -> Unit) {
    when (s.phase) {
        SeaPhase.PLACING -> SeaPlacingScreen(s, act, onLeave)
        SeaPhase.BATTLE -> SeaBattleScreen(s, act)
        SeaPhase.OVER -> SeaOverScreen(s, onPlayAgain, onBackToGames, onLeave)
        SeaPhase.LOBBY -> Unit
    }
}
