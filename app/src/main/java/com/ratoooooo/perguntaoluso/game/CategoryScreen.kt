package com.ratoooooo.perguntaoluso.game

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
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import com.ratoooooo.perguntaoluso.ui.theme.Cream
import com.ratoooooo.perguntaoluso.ui.theme.colorForCategory
import com.ratoooooo.perguntaoluso.ui.theme.iconForCategory
import com.ratoooooo.perguntaoluso.ui.theme.stickerBlock
import com.ratoooooo.perguntaoluso.ui.theme.textColorFor

private val CATEGORY_CARD_HEIGHT = 84.dp

@Composable
fun CategoryScreen(
    categories: List<String>,
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

        Spacer(Modifier.size(20.dp))

        LazyColumn {
            items(categories) { categoria ->
                val color = colorForCategory(categoria)
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(CATEGORY_CARD_HEIGHT)
                        .stickerBlock(fillColor = color, cornerRadius = 24.dp, shadowOffset = 6.dp)
                        .clickable { onCategorySelected(categoria) }
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
                    Text(
                        text = categoria,
                        style = MaterialTheme.typography.titleLarge,
                        color = textColorFor(color)
                    )
                }
                Spacer(Modifier.size(16.dp))
            }
        }
    }
}
