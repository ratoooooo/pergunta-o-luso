package com.ratoooooo.perguntaoluso.game

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.size
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.Info
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import com.ratoooooo.perguntaoluso.ui.theme.BackButton
import com.ratoooooo.perguntaoluso.ui.theme.Cream
import com.ratoooooo.perguntaoluso.ui.theme.Ink
import com.ratoooooo.perguntaoluso.ui.theme.rememberPressScale
import com.ratoooooo.perguntaoluso.ui.theme.pressScale
import com.ratoooooo.perguntaoluso.ui.theme.stickerCircle

/**
 * Consistent screen header: optional back button, a Fredoka title, and an
 * optional Manrope support line — one shared typographic hierarchy across screens.
 *
 * [onInfo] acrescenta um ⓘ encostado à direita do título. Vive aqui, e não em cada ecrã, para
 * os três ecrãs de escolha (formato, categoria, modo) o porem exactamente no mesmo sítio.
 */
@Composable
fun ScreenHeader(
    title: String,
    modifier: Modifier = Modifier,
    subtitle: String? = null,
    onBack: (() -> Unit)? = null,
    onInfo: (() -> Unit)? = null
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
                color = Ink,
                modifier = Modifier.weight(1f, fill = false)
            )
            if (onInfo != null) {
                Spacer(Modifier.weight(1f))
                InfoButton(onClick = onInfo)
            }
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

/**
 * ⓘ em disco Cream. Neutro de propósito: Dourado é a ação primária do ecrã e Teal/Coral são
 * cores de estado — "explica-me isto" não é nenhuma das três, e não pode competir com o cartão
 * que o jogador veio aqui tocar.
 */
@Composable
private fun InfoButton(onClick: () -> Unit) {
    val (interacao, escala) = rememberPressScale()
    Box(
        modifier = Modifier
            .size(38.dp)
            .pressScale(escala)
            .stickerCircle(fillColor = Cream, shadowOffset = 3.dp, borderWidth = 2.dp)
            .clickable(interactionSource = interacao, indication = null, onClick = onClick),
        contentAlignment = Alignment.Center
    ) {
        Icon(
            Icons.Rounded.Info,
            contentDescription = "Como funciona este ecrã",
            tint = Ink,
            modifier = Modifier.size(22.dp)
        )
    }
}
