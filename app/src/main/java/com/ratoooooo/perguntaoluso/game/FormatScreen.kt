package com.ratoooooo.perguntaoluso.game

import com.ratoooooo.perguntaoluso.ui.theme.rememberPressScale
import com.ratoooooo.perguntaoluso.ui.theme.pressScale
import com.ratoooooo.perguntaoluso.ui.theme.cascadeIn
import androidx.compose.foundation.background
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.ChevronRight
import androidx.compose.material.icons.rounded.Groups
import androidx.compose.material.icons.rounded.Person
import androidx.compose.material.icons.rounded.SportsKabaddi
import androidx.compose.material.icons.rounded.SportsMma
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.unit.dp
import com.ratoooooo.perguntaoluso.game.multi.MatchFormat
import com.ratoooooo.perguntaoluso.ui.theme.Coral
import com.ratoooooo.perguntaoluso.ui.theme.Cream
import com.ratoooooo.perguntaoluso.ui.theme.Gold
import com.ratoooooo.perguntaoluso.ui.theme.Ink
import com.ratoooooo.perguntaoluso.ui.theme.stickerCircle
import com.ratoooooo.perguntaoluso.ui.theme.Lavender
import com.ratoooooo.perguntaoluso.ui.theme.Purple
import com.ratoooooo.perguntaoluso.ui.theme.Teal
import com.ratoooooo.perguntaoluso.ui.theme.stickerBlock
import com.ratoooooo.perguntaoluso.ui.theme.textColorFor

@Composable
fun FormatScreen(
    onSolo: () -> Unit,
    onMulti: (MatchFormat) -> Unit,
    onBack: () -> Unit
) {
    Column(
        modifier = Modifier.fillMaxSize().background(Cream)
            .verticalScroll(rememberScrollState()).padding(24.dp)
    ) {
        ScreenHeader(title = "Sozinho ou à batalha?", subtitle = "Escolhe o formato de jogo", onBack = onBack)
        Spacer(Modifier.size(24.dp))

        FormatOption("Solo", "Tu contra as perguntas", Icons.Rounded.Person, Purple, 0, onSolo)
        Spacer(Modifier.size(16.dp))
        FormatOption("1x1", "Duelo a dois — só um vence", Icons.Rounded.SportsKabaddi, Gold, 1) { onMulti(MatchFormat.ONE_V_ONE) }
        Spacer(Modifier.size(16.dp))
        FormatOption("2x2", "Duas equipas, dois contra dois", Icons.Rounded.SportsMma, Coral, 2) { onMulti(MatchFormat.TWO_V_TWO) }
        Spacer(Modifier.size(16.dp))
        // "Quatro jogadores" estava errado desde que o Grupo passou a 10 lugares (ver Fase 30);
        // o texto vem agora do próprio formato, para não voltar a divergir do código.
        FormatOption("Grupo", "${MatchFormat.GRUPO.sizeLabel}, todos contra todos", Icons.Rounded.Groups, Teal, 3) { onMulti(MatchFormat.GRUPO) }
    }
}

/**
 * Cartão neutro com emblema colorido, como no ecrã de Modo. Antes o cartão inteiro era
 * pintado com a cor do formato, o que punha Coral (que noutros ecrãs significa "cancelar/
 * destrutivo") e Teal ("confirmar") a identificar formatos de jogo. A cor identificadora
 * passou para o emblema; a superfície fica neutra.
 */
@Composable
private fun FormatOption(title: String, subtitle: String, icon: ImageVector, color: Color, index: Int, onClick: () -> Unit) {
    val (interacao, escala) = rememberPressScale()
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .cascadeIn(index)
            .pressScale(escala)
            .stickerBlock(fillColor = Lavender, cornerRadius = 26.dp, shadowOffset = 6.dp)
            .clickable(interactionSource = interacao, indication = null, onClick = onClick)
            .padding(horizontal = 18.dp, vertical = 18.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Box(
            Modifier.size(52.dp).stickerCircle(fillColor = color, shadowOffset = 3.dp, borderWidth = 2.dp),
            contentAlignment = Alignment.Center
        ) {
            Icon(icon, contentDescription = null, tint = textColorFor(color), modifier = Modifier.size(28.dp))
        }
        Spacer(Modifier.size(16.dp))
        Column(Modifier.weight(1f)) {
            Text(title, style = MaterialTheme.typography.titleLarge, color = Ink)
            Text(subtitle, style = MaterialTheme.typography.bodyLarge, color = Ink)
        }
        Spacer(Modifier.size(10.dp))
        Icon(Icons.Rounded.ChevronRight, contentDescription = null, tint = Ink, modifier = Modifier.size(28.dp))
    }
}
