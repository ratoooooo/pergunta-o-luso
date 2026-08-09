package com.ratoooooo.perguntaoluso.ui.theme

import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.dp
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.window.DialogProperties

/**
 * Diálogo no sistema sticker (contorno de tinta, sombra dura), para substituir o
 * `AlertDialog` genérico do Material3 nos poucos sítios que ainda o usavam — o resto da
 * app (`ChallengeOverlay`, por exemplo) já resolve pop-ups assim, nunca com o diálogo
 * Material por defeito.
 *
 * `usePlatformDefaultWidth = false` porque o Dialog do Android limita a largura por
 * omissão; aqui quem decide a largura é o próprio `stickerBlock`, não a plataforma.
 */
@Composable
fun StickerDialog(
    onDismissRequest: () -> Unit,
    modifier: Modifier = Modifier,
    fillColor: Color = Lavender,
    content: @Composable ColumnScope.() -> Unit
) {
    Dialog(
        onDismissRequest = onDismissRequest,
        properties = DialogProperties(usePlatformDefaultWidth = false)
    ) {
        Box(
            modifier = Modifier.fillMaxSize().padding(horizontal = 22.dp, vertical = 40.dp),
            contentAlignment = Alignment.Center
        ) {
            Column(
                modifier = modifier
                    .fillMaxWidth()
                    .stickerBlock(fillColor = fillColor, cornerRadius = 28.dp, shadowOffset = 7.dp)
                    .padding(22.dp),
                content = content
            )
        }
    }
}
