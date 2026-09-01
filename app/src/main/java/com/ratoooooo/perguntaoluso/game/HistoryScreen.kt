package com.ratoooooo.perguntaoluso.game

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
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
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.ratoooooo.perguntaoluso.data.ScoreEntry
import com.ratoooooo.perguntaoluso.ui.theme.Ink
import com.ratoooooo.perguntaoluso.ui.theme.SegmentedTabs
import com.ratoooooo.perguntaoluso.ui.theme.colorForCategory
import com.ratoooooo.perguntaoluso.ui.theme.stickerBlock
import com.ratoooooo.perguntaoluso.ui.theme.textColorFor
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
        active = com.ratoooooo.perguntaoluso.ui.theme.NavTab.NONE,
        onHome = onHome, onRanking = onRanking, onFriends = onFriends, onProfile = onProfile
    ) {
        ScreenHeader(title = "Histórico", onBack = onBack)
        Spacer(Modifier.size(18.dp))

        var selectedFilter by rememberSaveable { mutableIntStateOf(0) }
        val filters = listOf(
            "Todos" to null,
            "Solo" to "solo",
            "1x1" to "1x1",
            "2x2" to "2x2",
            "Grupo" to "grupo"
        )
        SegmentedTabs(
            labels = filters.map { it.first },
            selectedIndex = selectedFilter,
            onSelect = { selectedFilter = it }
        )
        Spacer(Modifier.size(16.dp))

        val activeFormat = filters[selectedFilter.coerceIn(filters.indices)].second
        val visibleScores = if (activeFormat == null) scores else scores.filter { (it.formato.ifBlank { "solo" }) == activeFormat }
        when {
            isLoading -> Text("A carregar...", style = MaterialTheme.typography.bodyLarge, color = Ink)
            visibleScores.isEmpty() -> Text("Ainda não há partidas neste filtro.", style = MaterialTheme.typography.bodyLarge, color = Ink)
            else -> LazyColumn(verticalArrangement = Arrangement.spacedBy(12.dp)) {
                items(visibleScores) { HistoryRow(it) }
            }
        }
    }
}

private val dateFmt = SimpleDateFormat("d MMM, HH:mm", Locale("pt", "PT"))

@Composable
private fun HistoryRow(e: ScoreEntry) {
    val color = colorForCategory(e.categoria)
    val fmtLabel = when (e.formato.lowercase()) {
        "1x1" -> "1x1"
        "2x2" -> "2x2"
        "grupo" -> "GRUPO"
        else -> "SOLO"
    }
    Row(
        Modifier.fillMaxWidth().stickerBlock(fillColor = color, cornerRadius = 18.dp, shadowOffset = 4.dp)
            .padding(horizontal = 18.dp, vertical = 14.dp),
        horizontalArrangement = Arrangement.SpaceBetween,
        verticalAlignment = Alignment.CenterVertically
    ) {
        Column(Modifier.weight(1f)) {
            Text(
                "[$fmtLabel] ${e.categoria.ifBlank { "—" }} · ${GameMode.displayNameForId(e.modo)}",
                style = MaterialTheme.typography.bodyLarge.copy(fontWeight = FontWeight.Bold),
                color = textColorFor(color), maxLines = 1, overflow = TextOverflow.Ellipsis
            )
            Text(
                "${e.correctCount}/${e.total} certas · ${if (e.timestamp > 0) dateFmt.format(Date(e.timestamp)) else ""}",
                style = MaterialTheme.typography.labelLarge, color = textColorFor(color), maxLines = 1, overflow = TextOverflow.Ellipsis
            )
        }
        Spacer(Modifier.size(10.dp))
        Text("${e.score} pts", style = MaterialTheme.typography.titleLarge, color = textColorFor(color))
    }
}
