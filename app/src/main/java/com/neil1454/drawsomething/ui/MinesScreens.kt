@file:OptIn(ExperimentalMaterial3Api::class)

package com.neil1454.drawsomething.ui

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.MaterialTheme
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
import com.neil1454.drawsomething.core.MineItem
import com.neil1454.drawsomething.core.MineNote
import com.neil1454.drawsomething.core.MineRules
import com.neil1454.drawsomething.core.MinesEngine
import com.neil1454.drawsomething.core.MinesPhase
import com.neil1454.drawsomething.core.MinesState

typealias ActMines = (MinesEngine.() -> Unit) -> Unit

private val Hidden = Color(0xFF9FA8DA)
private val Open = Color(0xFFF1F3FF)
private val NumberColours = listOf(
    Color.Transparent, Color(0xFF1976D2), Color(0xFF388E3C), Color(0xFFD32F2F), Color(0xFF512DA8),
    Color(0xFF8D4B2F), Color(0xFF00796B), Color(0xFF212121), Color(0xFF757575),
)

@Composable
fun MinesHelp() {
    Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
        Text("Take turns revealing squares. A number says how many bombs touch that square.")
        Text("• Safe square: +${MineRules.SAFE_POINTS} each (an empty square opens its neighbours too).")
        Text("• Bomb: −${MineRules.MINE_PENALTY} and your turn ends. The game goes on.")
        Text("• The game ends when every safe square is open. Highest score wins.")
        Text("Hidden power-ups, triggered when you reveal them:", fontWeight = FontWeight.Bold)
        MineItem.values().forEach { Text("${it.emoji} ${it.label}: ${it.blurb}") }
    }
}

@Composable
fun MinesScreen(
    s: MinesState,
    act: ActMines,
    onPlayAgain: () -> Unit,
    onBackToGames: () -> Unit,
    onLeave: () -> Unit,
) {
    var help by rememberSaveable { mutableStateOf(false) }
    val over = s.phase == MinesPhase.OVER
    TwoPane(
        top = {
            Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                MineScoreChip("You", s.myScore, s.myShield, s.myDouble, !over && s.myTurn, Modifier.weight(1f))
                MineScoreChip(s.theirName, s.theirScore, s.theirShield, s.theirDouble, !over && !s.myTurn, Modifier.weight(1f))
            }
            Text(
                when {
                    over -> "Game over"
                    s.myTurn -> "👉 Your turn: tap a square"
                    else -> "⏳ ${s.theirName}'s turn…"
                },
                style = MaterialTheme.typography.titleMedium,
                fontWeight = FontWeight.Bold,
                color = if (s.myTurn && !over) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.onSurface,
            )
            Text(
                "💣 ${s.minesLeft} bombs left · ${s.safeLeft} safe squares left",
                style = MaterialTheme.typography.bodySmall,
            )
            s.notes.forEach { n ->
                Text(noteText(n, s.theirName), style = MaterialTheme.typography.bodyMedium)
            }
        },
        board = {
            SquareGrid(s.size.w, s.size.h, Modifier.fillMaxSize()) { x, y, cellDp ->
                val i = y * s.size.w + x
                MineCell(
                    ch = s.view.getOrElse(i) { '.' },
                    item = s.items.getOrElse(i) { '.' },
                    canTap = s.myTurn && !over,
                    sp = cellDp * 0.5f,
                    onTap = { act { pick(x, y) } },
                )
            }
        },
        bottom = {
            if (over) {
                MinesOverCard(s, onPlayAgain, onBackToGames, onLeave)
            } else {
                TextButton(onClick = { help = true }) { Text("❓ How to play & power-ups") }
            }
        },
    )
    if (help) {
        AlertDialog(
            onDismissRequest = { help = false },
            confirmButton = { TextButton(onClick = { help = false }) { Text("Got it") } },
            title = { Text("💣 Bomb Find") },
            text = { Column(Modifier.verticalScroll(rememberScrollState())) { MinesHelp() } },
        )
    }
}

@Composable
private fun MineScoreChip(name: String, score: Int, shield: Boolean, dbl: Boolean, active: Boolean, modifier: Modifier) {
    Card(
        modifier,
        colors = CardDefaults.cardColors(
            containerColor = if (active) MaterialTheme.colorScheme.primary.copy(alpha = 0.18f)
            else MaterialTheme.colorScheme.surfaceVariant,
        ),
    ) {
        Column(Modifier.padding(horizontal = 12.dp, vertical = 6.dp)) {
            Text(name, style = MaterialTheme.typography.labelMedium, maxLines = 1)
            Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                Text("$score", style = MaterialTheme.typography.titleLarge, fontWeight = FontWeight.Bold)
                if (shield) Text("🛡️")
                if (dbl) Text("✨")
            }
        }
    }
}

private fun noteText(n: MineNote, theirName: String): String {
    val who = if (n.byMe) "You" else theirName
    val squares = if (n.count == 1) "1 square" else "${n.count} squares"
    return when {
        n.kind == "safe" -> "$who opened $squares (+${n.value})"
        n.kind == "safe2" -> "✨ $who opened $squares, doubled! (+${n.value})"
        n.kind == "boom" -> if (n.value > 0) "💥 $who hit a bomb! −${n.value}" else "💥 $who hit a bomb!"
        n.kind == "block" -> "🛡️ $who's shield blocked a bomb! +${n.value}"
        n.kind == "item_B" -> "🎁 $who found a bonus! +${n.value}"
        n.kind == "item_S" -> "🛡️ $who found a shield"
        n.kind == "item_D" -> "✨ $who found double points for the next reveal"
        n.kind == "item_E" -> "⏩ $who gets an extra turn"
        n.kind == "item_T" -> "🧲 $who stole ${n.value} points"
        n.kind == "item_R" -> "📡 Radar flagged a hidden bomb 🚩"
        else -> ""
    }
}

@Composable
private fun MineCell(ch: Char, item: Char, canTap: Boolean, sp: Float, onTap: () -> Unit) {
    val hidden = ch == '.'
    val bg = when (ch) {
        '.' -> Hidden
        'X' -> Color(0xFFEF5350)
        'x' -> Color(0xFFA5D6A7)
        'M' -> Color(0xFFFFCDD2)
        'F' -> Color(0xFFFFE082)
        else -> Open
    }
    Box(
        Modifier
            .fillMaxSize()
            .padding(1.dp)
            .clip(RoundedCornerShape(3.dp))
            .background(bg)
            .clickable(enabled = hidden && canTap, onClick = onTap),
        contentAlignment = Alignment.Center,
    ) {
        val itemEmoji = MineItem.of(item)?.emoji
        when (ch) {
            'X' -> Text("💥", fontSize = sp.sp)
            'x' -> Text("🛡️", fontSize = sp.sp)
            'F' -> Text("🚩", fontSize = sp.sp)
            'M' -> Text("💣", fontSize = sp.sp)
            in '1'..'8' -> {
                Text("$ch", fontSize = sp.sp, fontWeight = FontWeight.ExtraBold, color = NumberColours[ch - '0'])
                if (itemEmoji != null) Text(itemEmoji, fontSize = (sp * 0.6f).sp, modifier = Modifier.align(Alignment.TopEnd))
            }
            '0' -> if (itemEmoji != null) Text(itemEmoji, fontSize = sp.sp)
            else -> Unit
        }
    }
}

@Composable
private fun MinesOverCard(s: MinesState, onPlayAgain: () -> Unit, onBackToGames: () -> Unit, onLeave: () -> Unit) {
    val title = when {
        s.myScore > s.theirScore -> "🏆 You win!"
        s.myScore < s.theirScore -> "🏆 ${s.theirName} wins!"
        else -> "🤝 It's a draw!"
    }
    Card(Modifier.fillMaxWidth()) {
        Column(
            Modifier.padding(14.dp).fillMaxWidth(),
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.spacedBy(8.dp),
        ) {
            Text(title, style = MaterialTheme.typography.headlineSmall, fontWeight = FontWeight.Bold, textAlign = TextAlign.Center)
            Text("You ${s.myScore} · ${s.theirName} ${s.theirScore}")
            if (s.isHost) {
                Button(onClick = onPlayAgain, Modifier.fillMaxWidth().height(48.dp)) { Text("Play again (${s.size.label})") }
            } else {
                Text("${s.theirName} can start a rematch…", color = MaterialTheme.colorScheme.onSurfaceVariant)
            }
            TextButton(onClick = onBackToGames) { Text("Choose another game") }
            TextButton(onClick = onLeave) { Text("Leave") }
            Spacer(Modifier.height(2.dp))
        }
    }
}
