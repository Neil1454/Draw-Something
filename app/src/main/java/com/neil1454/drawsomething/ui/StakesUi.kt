package com.neil1454.drawsomething.ui

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Card
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.compositionLocalOf
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateListOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.neil1454.drawsomething.core.Stake
import com.neil1454.drawsomething.core.StakeWire
import com.neil1454.drawsomething.core.TheirStake
import com.neil1454.drawsomething.sound.Sfx
import com.neil1454.drawsomething.core.Sound
import org.json.JSONArray
import org.json.JSONObject

/** Inside a Stakes Showdown contest this is how you get back to the stakes; null everywhere else. */
val LocalShowdownContinue = compositionLocalOf<(() -> Unit)?> { null }

private fun theirText(t: TheirStake) = if (t.known) "• ${t.text}" else "🎁 A mystery stake (revealed when the game ends)"

/** The stakes for a new Stakes Showdown, in the shape the engine wants. */
fun showdownOptions(ui: UiState): JSONObject {
    val mine = JSONArray()
    ui.myStakes.forEach { mine.put(JSONArray().put(it.text).put(if (it.shown) 1 else 0)) }
    val theirs = JSONArray()
    ui.theirStakes.forEach { theirs.put(JSONArray().put(if (it.shown) it.text else "").put(if (it.shown) 1 else 0)) }
    return JSONObject().put("mine", mine).put("theirs", theirs)
}

/** Shown in the lobby for every game: what each of you is putting up, and a button to change yours. */
@Composable
fun StakesCard(ui: UiState, onEdit: () -> Unit) {
    Card(Modifier.fillMaxWidth()) {
        Column(Modifier.padding(14.dp), verticalArrangement = Arrangement.spacedBy(6.dp)) {
            Text("🏆 Play for stakes (optional)", style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.Bold)
            Text(
                "Put something up for the winner: money, a treat, a chore, anything you type. You can add several, and choose whether the other player sees each one now or keeps it a mystery until the game ends.",
                style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
            Text("You're putting up:", fontWeight = FontWeight.SemiBold, fontSize = 13.sp)
            if (ui.myStakes.isEmpty()) Text("Nothing yet", fontSize = 13.sp, color = MaterialTheme.colorScheme.onSurfaceVariant)
            ui.myStakes.forEach { Text((if (it.shown) "• " else "🎁 ") + it.text + (if (it.shown) "" else "  (mystery to ${ui.partnerName})"), fontSize = 13.sp) }
            Text("${ui.partnerName} is putting up:", fontWeight = FontWeight.SemiBold, fontSize = 13.sp)
            if (ui.theirStakes.isEmpty()) Text("Nothing yet", fontSize = 13.sp, color = MaterialTheme.colorScheme.onSurfaceVariant)
            ui.theirStakes.forEach { Text(theirText(it), fontSize = 13.sp) }
            FunkyButton(if (ui.myStakes.isEmpty()) "➕  Set my stakes" else "✏️  Change my stakes", onEdit, Modifier.fillMaxWidth(), listOf(Funk.Violet, Funk.Orange), fontSize = 16.sp)
            Text(
                "The winner of each game claims the loser's stakes. The app never handles money: a stake is just a promise between the two of you.",
                fontSize = 11.sp, color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }
    }
}

@Composable
fun StakesEditor(initial: List<Stake>, onSave: (List<Stake>) -> Unit, onClose: () -> Unit) {
    val rows = remember { mutableStateListOf<Stake>().also { it.addAll(initial.ifEmpty { listOf(Stake("", true)) }) } }
    AlertDialog(
        onDismissRequest = onClose,
        title = { Text("🏆 Your stakes") },
        text = {
            Column(Modifier.verticalScroll(rememberScrollState()), verticalArrangement = Arrangement.spacedBy(10.dp)) {
                Text("What are you putting up if you lose? Type money (£5) or anything else. Up to ${StakeWire.MAX}.", fontSize = 13.sp)
                rows.forEachIndexed { i, st ->
                    Column(verticalArrangement = Arrangement.spacedBy(2.dp)) {
                        Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                            OutlinedTextField(
                                value = st.text, onValueChange = { rows[i] = st.copy(text = it.take(StakeWire.MAX_LEN)) },
                                singleLine = true, label = { Text("Stake ${i + 1}") }, modifier = Modifier.weight(1f),
                            )
                            Text("✕", fontSize = 20.sp, modifier = Modifier.clickable { rows.removeAt(i) }.padding(8.dp))
                        }
                        Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                            Switch(checked = st.shown, onCheckedChange = { rows[i] = st.copy(shown = it) })
                            Text(if (st.shown) "Shown to your partner now" else "Mystery: revealed when the game ends", fontSize = 12.sp)
                        }
                    }
                }
                if (rows.size < StakeWire.MAX) TextButton(onClick = { rows.add(Stake("", true)) }) { Text("➕ Add another stake") }
                Text("The app never handles money. Stakes are a friendly promise between you two.", fontSize = 11.sp, color = MaterialTheme.colorScheme.onSurfaceVariant)
            }
        },
        confirmButton = { TextButton(onClick = { Sfx.play(Sound.COIN); onSave(rows.toList()) }) { Text("Save") } },
        dismissButton = { TextButton(onClick = onClose) { Text("Cancel") } },
    )
}

/** A small 🏆 button in every game that shows what is at stake. */
@Composable
fun StakesCorner(ui: UiState, modifier: Modifier) {
    if (ui.solo || (ui.myStakes.isEmpty() && ui.theirStakes.isEmpty())) return
    var open by remember { mutableStateOf(false) }
    Box(
        modifier.padding(top = 6.dp, start = 6.dp).size(34.dp).clip(CircleShape).background(Color(0x99000000))
            .border(1.5.dp, Funk.Sun.copy(alpha = 0.8f), CircleShape).clickable { Sfx.play(Sound.TAP); open = true },
        contentAlignment = Alignment.Center,
    ) { Text("🏆", fontSize = 15.sp) }
    if (open) {
        AlertDialog(
            onDismissRequest = { open = false },
            title = { Text("🏆 What's at stake") },
            text = {
                Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
                    Text("You're putting up:", fontWeight = FontWeight.SemiBold)
                    if (ui.myStakes.isEmpty()) Text("Nothing")
                    ui.myStakes.forEach { Text((if (it.shown) "• " else "🎁 ") + it.text) }
                    Text("${ui.partnerName} is putting up:", fontWeight = FontWeight.SemiBold, modifier = Modifier.padding(top = 6.dp))
                    if (ui.theirStakes.isEmpty()) Text("Nothing")
                    ui.theirStakes.forEach { Text(theirText(it)) }
                    Text("The winner claims the loser's stakes.", fontSize = 12.sp, modifier = Modifier.padding(top = 6.dp))
                }
            },
            confirmButton = { TextButton(onClick = { open = false }) { Text("Close") } },
        )
    }
}

/** When a game ends: who claims what. [result]: 1 I won, 2 they won, 3 a draw. */
@Composable
fun StakeResultBanner(ui: UiState, result: Int, modifier: Modifier) {
    if (ui.solo || (ui.myStakes.isEmpty() && ui.theirStakes.isEmpty())) return
    var hidden by remember(result) { mutableStateOf(false) }
    if (hidden) return
    val shape = RoundedCornerShape(16.dp)
    val lines = when (result) {
        1 -> "🏆 You win! You claim ${ui.partnerName}'s stakes:" to ui.theirStakes.map { if (it.known) it.text else "🎁 mystery (revealing…)" }
        2 -> "🏆 ${ui.partnerName} wins and claims your stakes:" to ui.myStakes.map { it.text }
        else -> "🤝 A draw: nobody claims anything." to emptyList()
    }
    Column(
        modifier.padding(top = 46.dp, start = 12.dp, end = 12.dp).clip(shape).background(Color(0xEE0B0624)).border(2.dp, Funk.Sun, shape)
            .clickable { hidden = true }.padding(horizontal = 14.dp, vertical = 8.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
    ) {
        Text(lines.first, color = Funk.Sun, fontWeight = FontWeight.Black, fontSize = 15.sp, textAlign = TextAlign.Center)
        lines.second.forEach { Text("• $it", color = Color.White, fontSize = 14.sp, textAlign = TextAlign.Center) }
        if (lines.second.isNotEmpty()) Text("Settle up outside the app. Tap to hide.", color = Color.White.copy(alpha = 0.6f), fontSize = 10.sp)
    }
}
