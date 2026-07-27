package com.starforge.app.game

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.starforge.app.data.ScoreEntry
import com.starforge.app.ui.theme.Cream
import com.starforge.app.ui.theme.Ink
import com.starforge.app.ui.theme.colorForCategory
import com.starforge.app.ui.theme.stickerBlock
import com.starforge.app.ui.theme.textColorFor
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

@Composable
fun HistoryScreen(
    scores: List<ScoreEntry>,
    isLoading: Boolean,
    onBack: () -> Unit,
    onHome: () -> Unit,
    onRanking: () -> Unit,
    onFriends: () -> Unit,
    onProfile: () -> Unit
) {
    MainScaffold(
        active = com.starforge.app.ui.theme.NavTab.NONE,
        onHome = onHome, onRanking = onRanking, onFriends = onFriends, onProfile = onProfile
    ) {
        ScreenHeader(title = "Histórico", subtitle = "As tuas últimas partidas", onBack = onBack)
        Spacer(Modifier.size(18.dp))

        when {
            isLoading -> Text("A carregar...", style = MaterialTheme.typography.bodyLarge, color = Ink)
            scores.isEmpty() -> Text("Ainda não jogaste nenhuma partida.", style = MaterialTheme.typography.bodyLarge, color = Ink)
            else -> LazyColumn(verticalArrangement = Arrangement.spacedBy(12.dp)) {
                items(scores) { HistoryRow(it) }
            }
        }
    }
}

private val dateFmt = SimpleDateFormat("d MMM, HH:mm", Locale("pt", "PT"))

@Composable
private fun HistoryRow(e: ScoreEntry) {
    val color = colorForCategory(e.categoria)
    Row(
        Modifier.fillMaxWidth().stickerBlock(fillColor = color, cornerRadius = 18.dp, shadowOffset = 4.dp)
            .padding(horizontal = 18.dp, vertical = 14.dp),
        horizontalArrangement = Arrangement.SpaceBetween,
        verticalAlignment = Alignment.CenterVertically
    ) {
        Column(Modifier.weight(1f)) {
            Text(
                "${e.categoria.ifBlank { "—" }} · ${GameMode.displayNameForId(e.modo)}",
                style = MaterialTheme.typography.bodyLarge.copy(fontWeight = FontWeight.Bold),
                color = textColorFor(color), maxLines = 1
            )
            Text(
                "${e.correctCount}/${e.total} certas · ${if (e.timestamp > 0) dateFmt.format(Date(e.timestamp)) else ""}",
                style = MaterialTheme.typography.labelLarge, color = textColorFor(color), maxLines = 1
            )
        }
        Spacer(Modifier.size(10.dp))
        Text("${e.score} pts", style = MaterialTheme.typography.titleLarge, color = textColorFor(color))
    }
}
