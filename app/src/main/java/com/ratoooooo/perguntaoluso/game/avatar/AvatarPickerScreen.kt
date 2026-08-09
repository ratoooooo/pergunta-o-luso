package com.ratoooooo.perguntaoluso.game.avatar

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.lazy.grid.items
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import com.ratoooooo.perguntaoluso.game.ScreenHeader
import com.ratoooooo.perguntaoluso.ui.theme.Cream
import com.ratoooooo.perguntaoluso.ui.theme.Gold
import com.ratoooooo.perguntaoluso.ui.theme.Ink
import com.ratoooooo.perguntaoluso.ui.theme.stickerCircle
import com.ratoooooo.perguntaoluso.ui.theme.textColorFor

@Composable
fun AvatarPickerScreen(
    current: String?,
    onSelect: (String) -> Unit,
    onBack: () -> Unit
) {
    Column(Modifier.fillMaxSize().background(Cream).padding(horizontal = 24.dp).padding(top = 24.dp)) {
        ScreenHeader(title = "Escolhe o teu avatar", subtitle = "Símbolos de Portugal", onBack = onBack)
        Spacer(Modifier.size(18.dp))
        LazyVerticalGrid(
            columns = GridCells.Fixed(3),
            horizontalArrangement = Arrangement.spacedBy(14.dp),
            verticalArrangement = Arrangement.spacedBy(18.dp)
        ) {
            items(PortugueseSymbol.entries.toList()) { sym ->
                val selected = sym.id == current
                Column(horizontalAlignment = Alignment.CenterHorizontally, modifier = Modifier.clickable { onSelect(sym.id) }) {
                    Box(
                        Modifier.fillMaxWidth().aspectRatio(1f)
                            .stickerCircle(fillColor = sym.bg, shadowOffset = 5.dp, borderWidth = if (selected) 4.dp else 3.dp, borderColor = if (selected) Gold else Ink),
                        contentAlignment = Alignment.Center
                    ) {
                        SymbolIcon(sym, Modifier.fillMaxSize(0.6f), tint = textColorFor(sym.bg))
                    }
                    Spacer(Modifier.size(6.dp))
                    Text(sym.displayName, style = MaterialTheme.typography.labelLarge, color = Ink, textAlign = TextAlign.Center, maxLines = 2)
                }
            }
        }
    }
}
