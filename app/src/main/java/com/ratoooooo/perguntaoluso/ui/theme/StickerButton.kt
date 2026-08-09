package com.ratoooooo.perguntaoluso.ui.theme

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.unit.dp

@Composable
fun StickerButton(
    text: String,
    icon: ImageVector,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    fillColor: androidx.compose.ui.graphics.Color = Gold
) {
    Row(
        modifier = modifier
            .stickerBlock(fillColor = fillColor, cornerRadius = 28.dp, shadowOffset = 6.dp)
            .clickable(onClick = onClick)
            .padding(horizontal = 32.dp, vertical = 18.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = androidx.compose.foundation.layout.Arrangement.Center
    ) {
        Icon(
            imageVector = icon,
            contentDescription = null,
            tint = Ink,
            modifier = Modifier.size(24.dp)
        )
        androidx.compose.foundation.layout.Spacer(Modifier.size(10.dp))
        Text(
            text = text,
            style = MaterialTheme.typography.titleLarge,
            color = Ink
        )
    }
}
