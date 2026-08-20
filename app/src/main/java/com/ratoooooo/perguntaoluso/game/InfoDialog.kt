package com.ratoooooo.perguntaoluso.game

import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.size
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.Check
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.ratoooooo.perguntaoluso.ui.theme.Ink
import com.ratoooooo.perguntaoluso.ui.theme.Purple
import com.ratoooooo.perguntaoluso.ui.theme.StickerButton
import com.ratoooooo.perguntaoluso.ui.theme.StickerDialog

/**
 * Diálogo de ajuda dos ecrãs de escolha.
 *
 * Regra do texto: **não repetir o que já está no cartão**. Se o cartão já diz "Duelo a dois — só
 * um vence", o diálogo não volta a dizê-lo; diz o que o cartão não cabe a dizer — como é que se
 * arranja adversário, o que acontece se alguém sair, quantos pontos vale o quê.
 */
@Composable
fun InfoDialog(
    titulo: String,
    linhas: List<Pair<String, String>>,
    rodape: String? = null,
    onDismiss: () -> Unit
) {
    StickerDialog(onDismissRequest = onDismiss) {
        Text(titulo, style = MaterialTheme.typography.titleLarge, color = Ink)
        Spacer(Modifier.size(14.dp))
        linhas.forEachIndexed { i, (cabeca, corpo) ->
            if (i > 0) Spacer(Modifier.size(12.dp))
            Row(Modifier.fillMaxWidth()) {
                Column {
                    Text(cabeca, style = MaterialTheme.typography.labelLarge, color = Ink)
                    Spacer(Modifier.size(2.dp))
                    Text(corpo, style = MaterialTheme.typography.bodyLarge, color = Ink)
                }
            }
        }
        if (rodape != null) {
            Spacer(Modifier.size(14.dp))
            Text(
                rodape,
                style = MaterialTheme.typography.bodyLarge.copy(fontSize = 14.sp),
                color = Ink
            )
        }
        Spacer(Modifier.size(20.dp))
        // Roxo e não Dourado: o Dourado deste ecrã pertence aos cartões de escolha, que são a
        // ação real. Fechar a ajuda é secundário.
        StickerButton(
            "PERCEBI",
            Icons.Rounded.Check,
            onDismiss,
            fillColor = Purple,
            modifier = Modifier.fillMaxWidth()
        )
    }
}
