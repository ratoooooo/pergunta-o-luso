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
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.ratoooooo.perguntaoluso.data.ModeStats
import com.ratoooooo.perguntaoluso.data.Profile
import com.ratoooooo.perguntaoluso.ui.theme.Cream
import com.ratoooooo.perguntaoluso.ui.theme.Gold
import com.ratoooooo.perguntaoluso.ui.theme.Ink
import com.ratoooooo.perguntaoluso.ui.theme.Lavender
import com.ratoooooo.perguntaoluso.ui.theme.LevelPill
import com.ratoooooo.perguntaoluso.ui.theme.Purple
import com.ratoooooo.perguntaoluso.ui.theme.stickerBlock
import com.ratoooooo.perguntaoluso.ui.theme.textColorFor

private data class RankingList(
    val title: String,
    val value: (ModeStats) -> Int,
    val suffix: String
)

private val RANKING_LISTS = listOf(
    RankingList("Mais vitórias", { it.vitorias }, "vit"),
    RankingList("Mais pontos", { it.pontos }, "pts"),
    RankingList("Melhor recorde", { it.recorde }, "pts")
)

@Composable
fun RankingScreen(
    profiles: List<Profile>,
    isLoading: Boolean,
    onBack: () -> Unit,
    onHome: () -> Unit,
    onFriends: () -> Unit,
    onProfile: () -> Unit
) {
    var selectedMode by remember { mutableStateOf(GameMode.CLASSICO) }

    com.ratoooooo.perguntaoluso.game.MainScaffold(
        active = com.ratoooooo.perguntaoluso.ui.theme.NavTab.RANKING,
        onHome = onHome,
        onRanking = {},
        onFriends = onFriends,
        onProfile = onProfile
    ) {
        ScreenHeader(title = "Ranking", subtitle = "Quem manda em cada modo", onBack = onBack)

        Spacer(Modifier.size(18.dp))

        Row(horizontalArrangement = Arrangement.spacedBy(10.dp), modifier = Modifier.fillMaxWidth()) {
            GameMode.entries.forEach { mode ->
                val selected = mode == selectedMode
                Column(
                    modifier = Modifier
                        .weight(1f)
                        .stickerBlock(
                            fillColor = if (selected) Purple else Lavender,
                            cornerRadius = 16.dp,
                            shadowOffset = 4.dp,
                            borderWidth = 2.dp
                        )
                        .clickable { selectedMode = mode }
                        .padding(vertical = 12.dp),
                    horizontalAlignment = Alignment.CenterHorizontally
                ) {
                    Text(
                        text = mode.displayName,
                        style = MaterialTheme.typography.labelLarge,
                        color = if (selected) Cream else Ink
                    )
                }
            }
        }

        Spacer(Modifier.size(18.dp))

        if (isLoading) {
            Text("A carregar...", style = MaterialTheme.typography.bodyLarge, color = Ink)
        } else {
            val played = profiles.filter { (it.modos[selectedMode.id]?.jogos ?: 0) > 0 }
            LazyColumn(verticalArrangement = Arrangement.spacedBy(20.dp)) {
                RANKING_LISTS.forEach { list ->
                    item(key = "${selectedMode.id}-${list.title}") {
                        RankingSection(list = list, mode = selectedMode, profiles = played)
                    }
                }
            }
        }
    }
}

@Composable
private fun RankingSection(list: RankingList, mode: GameMode, profiles: List<Profile>) {
    val ranked = profiles
        .sortedByDescending { list.value(it.modos.getValue(mode.id)) }
        .take(5)
        .filter { list.value(it.modos.getValue(mode.id)) > 0 }

    Column(modifier = Modifier.fillMaxWidth()) {
        Text(text = list.title, style = MaterialTheme.typography.titleLarge, color = Ink)
        Spacer(Modifier.size(10.dp))
        if (ranked.isEmpty()) {
            Text("Ainda sem dados.", style = MaterialTheme.typography.bodyLarge, color = Ink)
            return
        }
        ranked.forEachIndexed { index, profile ->
            val rank = index + 1
            val rowColor = if (rank == 1) Gold else Lavender
            val value = list.value(profile.modos.getValue(mode.id))
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(bottom = 10.dp)
                    .stickerBlock(fillColor = rowColor, cornerRadius = 16.dp, shadowOffset = 4.dp)
                    .padding(horizontal = 16.dp, vertical = 14.dp),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Row(Modifier.weight(1f), verticalAlignment = Alignment.CenterVertically) {
                    Text(
                        text = "#$rank  ${profile.nomeVisivel}",
                        style = MaterialTheme.typography.bodyLarge.copy(fontWeight = FontWeight.Bold),
                        color = textColorFor(rowColor)
                    )
                    Spacer(Modifier.size(8.dp))
                    LevelPill(nivel = profile.nivel)
                }
                Spacer(Modifier.size(8.dp))
                Text(
                    text = "$value ${list.suffix}",
                    style = MaterialTheme.typography.labelLarge,
                    color = textColorFor(rowColor)
                )
            }
        }
    }
}
