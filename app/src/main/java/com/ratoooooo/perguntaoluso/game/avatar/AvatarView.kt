package com.ratoooooo.perguntaoluso.game.avatar

import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.dp
import com.ratoooooo.perguntaoluso.ui.theme.Cream
import com.ratoooooo.perguntaoluso.ui.theme.Purple
import com.ratoooooo.perguntaoluso.ui.theme.stickerCircle
import com.ratoooooo.perguntaoluso.ui.theme.textColorFor

/**
 * Player avatar: the chosen Portuguese symbol on its coloured sticker circle, or the name
 * initials as fallback when no avatar is set. [modifier] must carry the size.
 */
@Composable
fun AvatarView(
    avatarId: String?,
    iniciais: String,
    modifier: Modifier = Modifier,
    shadowOffset: androidx.compose.ui.unit.Dp = 4.dp
) {
    val symbol = PortugueseSymbol.fromId(avatarId)
    if (symbol != null) {
        Box(
            modifier.stickerCircle(fillColor = symbol.bg, shadowOffset = shadowOffset),
            contentAlignment = Alignment.Center
        ) {
            SymbolIcon(symbol, Modifier.fillMaxSize(0.6f), tint = textColorFor(symbol.bg))
        }
    } else {
        Box(
            modifier.stickerCircle(fillColor = Purple, shadowOffset = shadowOffset),
            contentAlignment = Alignment.Center
        ) {
            Text(iniciais, style = MaterialTheme.typography.titleLarge, color = Cream)
        }
    }
}
