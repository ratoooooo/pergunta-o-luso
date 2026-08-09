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
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.Bolt
import androidx.compose.material.icons.rounded.ChevronRight
import androidx.compose.material.icons.rounded.Favorite
import androidx.compose.material.icons.rounded.Whatshot
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.unit.dp
import com.ratoooooo.perguntaoluso.ui.theme.Coral
import com.ratoooooo.perguntaoluso.ui.theme.Cream
import com.ratoooooo.perguntaoluso.ui.theme.Ink
import com.ratoooooo.perguntaoluso.ui.theme.stickerCircle
import com.ratoooooo.perguntaoluso.ui.theme.Lavender
import com.ratoooooo.perguntaoluso.ui.theme.Purple
import com.ratoooooo.perguntaoluso.ui.theme.Teal
import com.ratoooooo.perguntaoluso.ui.theme.stickerBlock
import com.ratoooooo.perguntaoluso.ui.theme.textColorFor

private val ModeColors = mapOf(
    GameMode.CLASSICO to Purple,
    GameMode.CAOTICO to Coral,
    GameMode.ELIMINATORIAS to Teal
)

private val ModeIcons: Map<GameMode, ImageVector> = mapOf(
    GameMode.CLASSICO to Icons.Rounded.Bolt,
    GameMode.CAOTICO to Icons.Rounded.Whatshot,
    GameMode.ELIMINATORIAS to Icons.Rounded.Favorite
)

private val MODE_CARD_HEIGHT = 100.dp

@Composable
fun ModeScreen(
    categoria: String,
    modes: List<GameMode> = GameMode.entries,
    onModeSelected: (GameMode) -> Unit,
    onBack: () -> Unit
) {
    Column(
        modifier = Modifier
            .fillMaxSize()
            .background(Cream)
            // Fase 29: sem scroll, ecrãs mais baixos (ou letra grande do sistema) cortavam o
            // conteúdo sem forma de lá chegar. `fillMaxSize` garante que continua centrado
            // quando sobra espaço; o scroll só entra em ação quando falta.
            .verticalScroll(rememberScrollState())
            .padding(24.dp)
    ) {
        ScreenHeader(title = "Como queres jogar?", onBack = onBack)
        Spacer(Modifier.size(14.dp))
        CategoryChip(categoria)
        Spacer(Modifier.size(22.dp))

        modes.forEachIndexed { index, mode ->
            val color = ModeColors.getValue(mode)
            val (interacao, escala) = rememberPressScale()
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .height(MODE_CARD_HEIGHT)
                    .cascadeIn(index)
                    .pressScale(escala)
                    .stickerBlock(fillColor = Lavender, cornerRadius = 26.dp, shadowOffset = 6.dp)
                    .clickable(interactionSource = interacao, indication = null) { onModeSelected(mode) }
                    .padding(horizontal = 20.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                Box(
                    Modifier.size(52.dp).stickerCircle(fillColor = color, shadowOffset = 3.dp, borderWidth = 2.dp),
                    contentAlignment = Alignment.Center
                ) {
                    Icon(ModeIcons.getValue(mode), contentDescription = null, tint = textColorFor(color), modifier = Modifier.size(28.dp))
                }
                Spacer(Modifier.size(16.dp))
                Column(Modifier.weight(1f)) {
                    Text(text = mode.displayName, style = MaterialTheme.typography.titleLarge, color = Ink)
                    Spacer(Modifier.size(4.dp))
                    Text(text = mode.tagline, style = MaterialTheme.typography.bodyLarge, color = Ink)
                }
                Spacer(Modifier.size(10.dp))
                Icon(Icons.Rounded.ChevronRight, contentDescription = null, tint = Ink, modifier = Modifier.size(28.dp))
            }
            Spacer(Modifier.size(18.dp))
        }
    }
}
