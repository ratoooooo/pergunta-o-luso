package com.ratoooooo.perguntaoluso.game

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.EmojiEvents
import androidx.compose.material.icons.rounded.Refresh
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import com.ratoooooo.perguntaoluso.data.ScoreEntry
import com.ratoooooo.perguntaoluso.ui.theme.Cream
import com.ratoooooo.perguntaoluso.ui.theme.Gold
import com.ratoooooo.perguntaoluso.ui.theme.Ink
import com.ratoooooo.perguntaoluso.ui.theme.Lavender
import com.ratoooooo.perguntaoluso.ui.theme.StickerButton
import com.ratoooooo.perguntaoluso.ui.theme.stickerBlock
import com.ratoooooo.perguntaoluso.ui.theme.textColorFor

@Composable
fun PodiumScreen(
    categoria: String,
    mode: GameMode,
    points: Int,
    correctCount: Int,
    total: Int,
    eliminated: Boolean,
    won: Boolean,
    topScores: List<ScoreEntry>,
    onPlayAgain: () -> Unit,
    onHome: () -> Unit
) {
    Column(
        modifier = Modifier
            .fillMaxSize()
            .background(Cream)
            .padding(24.dp),
        horizontalAlignment = Alignment.CenterHorizontally
    ) {
        val title = when {
            eliminated -> "Eliminado!"
            won -> "Vitória!"
            else -> "Fim de jogo!"
        }
        ScreenHeader(title = title, onBack = onHome)

        Spacer(Modifier.size(16.dp))

        // Current-game medal.
        Box(
            modifier = Modifier
                .fillMaxWidth()
                .stickerBlock(fillColor = Gold, cornerRadius = 28.dp, shadowOffset = 7.dp)
                .padding(vertical = 20.dp),
            contentAlignment = Alignment.Center
        ) {
            Column(horizontalAlignment = Alignment.CenterHorizontally) {
                Icon(
                    imageVector = Icons.Rounded.EmojiEvents,
                    contentDescription = null,
                    tint = Ink,
                    modifier = Modifier.size(40.dp)
                )
                Text(
                    text = "$categoria · ${mode.displayName}",
                    style = MaterialTheme.typography.labelLarge,
                    color = Ink
                )
                Text(
                    text = "$points",
                    style = MaterialTheme.typography.headlineLarge,
                    color = Ink
                )
                Text(
                    text = "pontos · $correctCount de $total certas",
                    style = MaterialTheme.typography.bodyLarge,
                    color = Ink
                )
            }
        }

        Spacer(Modifier.size(20.dp))

        Text(
            text = "Melhores pontuações",
            style = MaterialTheme.typography.titleLarge,
            color = Ink
        )

        Spacer(Modifier.size(12.dp))

        LazyColumn(modifier = Modifier.fillMaxWidth().weight(1f)) {
            itemsIndexed(topScores) { index, entry ->
                val rank = index + 1
                val rowColor = if (rank == 1) Gold else Lavender
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .stickerBlock(fillColor = rowColor, cornerRadius = 18.dp, shadowOffset = 4.dp)
                        .padding(horizontal = 18.dp, vertical = 14.dp),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Column(modifier = Modifier.weight(1f)) {
                        Text(
                            text = "#$rank  ${entry.categoria.ifBlank { "—" }}",
                            style = MaterialTheme.typography.bodyLarge,
                            color = textColorFor(rowColor)
                        )
                        Text(
                            text = modeLabel(entry.modo),
                            style = MaterialTheme.typography.labelLarge,
                            color = textColorFor(rowColor)
                        )
                    }
                    Text(
                        text = "${entry.score} pts",
                        style = MaterialTheme.typography.titleLarge,
                        color = textColorFor(rowColor)
                    )
                }
                Spacer(Modifier.size(10.dp))
            }
        }

        Spacer(Modifier.size(12.dp))

        StickerButton(
            text = "JOGAR NOVAMENTE",
            icon = Icons.Rounded.Refresh,
            onClick = onPlayAgain
        )
    }
}

private fun modeLabel(modeId: String): String = when (modeId) {
    GameMode.CLASSICO.id -> GameMode.CLASSICO.displayName
    GameMode.CAOTICO.id -> GameMode.CAOTICO.displayName
    GameMode.ELIMINATORIAS.id -> GameMode.ELIMINATORIAS.displayName
    else -> "—"
}
