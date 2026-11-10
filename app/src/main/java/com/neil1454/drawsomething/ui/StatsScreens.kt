package com.neil1454.drawsomething.ui

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.FilterChip
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
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.neil1454.drawsomething.core.BoardRow
import com.neil1454.drawsomething.core.GameKind
import com.neil1454.drawsomething.core.StatsRules
import com.neil1454.drawsomething.core.availableGames
import com.neil1454.drawsomething.stats.StatsStore

private fun medal(rank: Int) = when (rank) { 1 -> "🥇"; 2 -> "🥈"; 3 -> "🥉"; else -> "$rank" }

/** Per-game leaderboards plus the overall "Dominator" table. */
@Composable
fun LeaderboardScreen(stats: StatsStore, myName: String, onBack: () -> Unit) {
    @Suppress("UNUSED_VARIABLE") val v by stats.version.collectAsStateWithLifecycle()
    var tab by rememberSaveable { mutableStateOf("ALL") }
    var confirmClear by rememberSaveable { mutableStateOf(false) }
    val book = stats.book
    val kind = availableGames().firstOrNull { it.name == tab }
    val rows = if (kind == null) book.dominator() else book.board(kind)

    Page {
        Column(Modifier.fillMaxWidth(), horizontalAlignment = Alignment.CenterHorizontally, verticalArrangement = Arrangement.spacedBy(12.dp)) {
            Spacer(Modifier.height(8.dp))
            GradientText("🏆 Leaderboards", fontSize = 32.sp)
            Row(Modifier.fillMaxWidth().horizontalScroll(rememberScrollState()), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                FilterChip(selected = tab == "ALL", onClick = { tab = "ALL" }, label = { Text("👑 Dominator") })
                availableGames().forEach { g ->
                    FilterChip(selected = tab == g.name, onClick = { tab = g.name }, label = { Text("${g.emoji} ${g.label}") })
                }
            }
            if (kind == null) {
                Text(
                    "The ultimate games player. Every game earns points: a win ${StatsRules.WIN}, a draw ${StatsRules.DRAW}, " +
                        "a loss ${StatsRules.LOSS}, plus up to ${StatsRules.POINT_BONUS_CAP} for the points you score. " +
                        "Wins in any game count, so be good at everything!",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    textAlign = TextAlign.Center,
                )
            } else {
                Text(
                    "${kind.emoji} ${kind.label}: ranked by wins, then by Dominator points.",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    textAlign = TextAlign.Center,
                )
            }
            if (rows.isEmpty()) {
                Text(
                    "No games here yet. Play someone, and you'll both appear on the board!",
                    textAlign = TextAlign.Center, modifier = Modifier.padding(vertical = 24.dp),
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            } else {
                rows.forEachIndexed { i, r -> BoardLine(i + 1, r, r.name.equals(myName.trim(), ignoreCase = true), overall = kind == null) }
            }
            if (kind == null && book.recent.isNotEmpty()) {
                Text("Recent games", style = MaterialTheme.typography.titleMedium, modifier = Modifier.padding(top = 8.dp))
                book.recent.take(10).forEach { r ->
                    val line = when (r.result) {
                        1 -> "${r.me} beat ${r.opp}"
                        2 -> "${r.opp} beat ${r.me}"
                        else -> "${r.me} and ${r.opp} drew"
                    }
                    Text("${r.kind.emoji} $line  (${r.myPoints}–${r.theirPoints})", fontSize = 14.sp, color = MaterialTheme.colorScheme.onSurfaceVariant)
                }
            }
            Text(
                "Scores are kept on this phone only. Games against an Easy computer, and Draw Something practice, don't count.",
                style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant, textAlign = TextAlign.Center,
            )
            FunkyButton("Back", onBack, Modifier.fillMaxWidth(), listOf(Funk.Violet, Funk.Blue))
            if (!book.isEmpty()) TextButton(onClick = { confirmClear = true }) { Text("Reset all scores") }
        }
    }
    if (confirmClear) {
        AlertDialog(
            onDismissRequest = { confirmClear = false },
            title = { Text("Reset all scores?") },
            text = { Text("This clears every leaderboard on this phone. It can't be undone.") },
            confirmButton = { TextButton(onClick = { confirmClear = false; stats.clear() }) { Text("Reset") } },
            dismissButton = { TextButton(onClick = { confirmClear = false }) { Text("Keep") } },
        )
    }
}

@Composable
private fun BoardLine(rank: Int, r: BoardRow, me: Boolean, overall: Boolean) {
    val shape = RoundedCornerShape(16.dp)
    val tint = when (rank) { 1 -> Funk.Sun; 2 -> Color(0xFFC9D2E3); 3 -> Funk.Orange; else -> Funk.Violet }
    Row(
        Modifier.fillMaxWidth()
            .clip(shape)
            .background(Brush.horizontalGradient(listOf(tint.copy(alpha = if (me) 0.45f else 0.25f), Color(0x55000000))))
            .border(if (me) 2.dp else 1.dp, tint.copy(alpha = if (me) 1f else 0.5f), shape)
            .padding(horizontal = 12.dp, vertical = 10.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(10.dp),
    ) {
        Text(medal(rank), Modifier.width(34.dp), fontSize = if (rank <= 3) 24.sp else 18.sp, fontWeight = FontWeight.Black, textAlign = TextAlign.Center)
        Column(Modifier.weight(1f)) {
            Text(r.name + if (me) "  (you)" else "", fontWeight = FontWeight.Bold, fontSize = 17.sp, maxLines = 1)
            Text(
                "${r.played} played · ${r.won}W ${r.drawn}D ${r.lost}L · ${r.points} pts",
                fontSize = 12.sp, color = Color.White.copy(alpha = 0.8f),
            )
        }
        Box(contentAlignment = Alignment.CenterEnd) {
            Column(horizontalAlignment = Alignment.End) {
                Text(if (overall) "${r.rating}" else "${r.winPercent}%", fontSize = 22.sp, fontWeight = FontWeight.Black, color = tint)
                Text(if (overall) "Dominator" else "wins", fontSize = 10.sp, color = Color.White.copy(alpha = 0.8f))
            }
        }
    }
}
