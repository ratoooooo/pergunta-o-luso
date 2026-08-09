package com.ratoooooo.perguntaoluso.game

import com.ratoooooo.perguntaoluso.ui.theme.rememberPressScale
import com.ratoooooo.perguntaoluso.ui.theme.pressScale
import com.ratoooooo.perguntaoluso.ui.theme.cascadeIn
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.wrapContentWidth
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.Groups
import androidx.compose.material.icons.rounded.Person
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import com.ratoooooo.perguntaoluso.game.multi.MatchFormat
import com.ratoooooo.perguntaoluso.ui.theme.Cream
import com.ratoooooo.perguntaoluso.ui.theme.Purple
import com.ratoooooo.perguntaoluso.ui.theme.colorForCategory
import com.ratoooooo.perguntaoluso.ui.theme.iconForCategory
import com.ratoooooo.perguntaoluso.ui.theme.StickerButton
import com.ratoooooo.perguntaoluso.ui.theme.stickerBlock
import com.ratoooooo.perguntaoluso.ui.theme.textColorFor

private val CATEGORY_CARD_HEIGHT = 84.dp

@Composable
fun CategoryScreen(
    categories: List<String>,
    questionCounts: Map<String, Int>,
    formato: MatchFormat?,
    onCategorySelected: (String) -> Unit,
    onBack: () -> Unit
) {
    Column(
        modifier = Modifier
            .fillMaxSize()
            .background(Cream)
            .padding(24.dp)
    ) {
        ScreenHeader(
            title = "Que tema te apetece?",
            subtitle = "Escolhe uma categoria para jogar",
            onBack = onBack
        )

        // Pastilha de contexto (mockup, ecrã 4): a escolha da categoria vem depois do
        // formato e o ecrã não lembrava para que tipo de partida se estava a escolher.
        Spacer(Modifier.size(14.dp))
        Row(
            modifier = Modifier
                .wrapContentWidth()
                .stickerBlock(fillColor = Purple, cornerRadius = 16.dp, shadowOffset = 3.dp, borderWidth = 2.dp)
                .padding(horizontal = 14.dp, vertical = 8.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Icon(
                imageVector = if (formato == null) Icons.Rounded.Person else Icons.Rounded.Groups,
                contentDescription = null,
                tint = Cream,
                modifier = Modifier.size(18.dp)
            )
            Spacer(Modifier.size(8.dp))
            Text(
                text = formato?.displayName ?: "Solo",
                style = MaterialTheme.typography.labelLarge,
                color = Cream
            )
        }

        Spacer(Modifier.size(20.dp))

        LazyColumn {
            itemsIndexed(categories) { index, categoria ->
                val color = colorForCategory(categoria)
                val (interacao, escala) = rememberPressScale()
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(CATEGORY_CARD_HEIGHT)
                        .cascadeIn(index)
                        .pressScale(escala)
                        .stickerBlock(fillColor = color, cornerRadius = 24.dp, shadowOffset = 6.dp)
                        .clickable(interactionSource = interacao, indication = null) { onCategorySelected(categoria) }
                        .padding(horizontal = 22.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Icon(
                        imageVector = iconForCategory(categoria),
                        contentDescription = null,
                        tint = textColorFor(color),
                        modifier = Modifier.size(30.dp)
                    )
                    Spacer(Modifier.size(16.dp))
                    // Nome e contagem numa coluna (mockup, ecrã 4). A contagem é subordinada:
                    // corpo mais pequeno e a mesma tinta com transparência, para informar sem
                    // disputar leitura com o nome da categoria. Enquanto a contagem não chegar
                    // não se escreve nada — a linha aparece quando houver número, em vez de
                    // piscar um "0 perguntas" que seria falso.
                    Column {
                        Text(
                            text = categoria,
                            style = MaterialTheme.typography.titleLarge,
                            color = textColorFor(color)
                        )
                        questionCounts[categoria]?.let { total ->
                            Text(
                                text = if (total == 1) "1 pergunta" else "$total perguntas",
                                style = MaterialTheme.typography.bodyLarge,
                                color = textColorFor(color).copy(alpha = 0.75f)
                            )
                        }
                    }
                }
                Spacer(Modifier.size(16.dp))
            }
        }
    }
}
