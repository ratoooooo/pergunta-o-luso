package com.ratoooooo.perguntaoluso.game

import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.size
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import com.ratoooooo.perguntaoluso.ui.theme.BackButton
import com.ratoooooo.perguntaoluso.ui.theme.Ink

/**
 * Consistent screen header: optional back button, a Fredoka title, and an
 * optional Manrope support line — one shared typographic hierarchy across screens.
 */
@Composable
fun ScreenHeader(
    title: String,
    modifier: Modifier = Modifier,
    subtitle: String? = null,
    onBack: (() -> Unit)? = null
) {
    Column(modifier = modifier.fillMaxWidth()) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            if (onBack != null) {
                BackButton(onClick = onBack)
                Spacer(Modifier.size(16.dp))
            }
            Text(
                text = title,
                style = MaterialTheme.typography.titleLarge,
                color = Ink
            )
        }
        if (subtitle != null) {
            Spacer(Modifier.size(6.dp))
            Text(
                text = subtitle,
                style = MaterialTheme.typography.bodyLarge,
                color = Ink
            )
        }
    }
}
