package com.starforge.app.game

import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.wrapContentWidth
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import com.starforge.app.ui.theme.colorForCategory
import com.starforge.app.ui.theme.iconForCategory
import com.starforge.app.ui.theme.stickerBlock
import com.starforge.app.ui.theme.textColorFor

/** Small pill showing the chosen category with its colour + icon — a proper badge, not a loose label. */
@Composable
fun CategoryChip(categoria: String, modifier: Modifier = Modifier) {
    val color = colorForCategory(categoria)
    Row(
        modifier = modifier
            .wrapContentWidth()
            .stickerBlock(fillColor = color, cornerRadius = 16.dp, shadowOffset = 3.dp, borderWidth = 2.dp)
            .padding(horizontal = 14.dp, vertical = 8.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Icon(
            imageVector = iconForCategory(categoria),
            contentDescription = null,
            tint = textColorFor(color),
            modifier = Modifier.size(18.dp)
        )
        Spacer(Modifier.size(8.dp))
        Text(text = categoria, style = MaterialTheme.typography.labelLarge, color = textColorFor(color))
    }
}
