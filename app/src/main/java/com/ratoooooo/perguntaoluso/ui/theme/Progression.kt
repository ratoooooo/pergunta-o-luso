package com.ratoooooo.perguntaoluso.ui.theme

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import com.ratoooooo.perguntaoluso.data.Progressao

/** Gold circle showing the player's level number (mockup "Nv N" badge). */
@Composable
fun LevelBadge(nivel: Int, modifier: Modifier = Modifier, size: Dp = 44.dp, fillColor: Color = Gold) {
    Box(
        modifier.size(size).stickerCircle(fillColor = fillColor, shadowOffset = 3.dp, borderWidth = 2.dp),
        contentAlignment = Alignment.Center
    ) {
        Text("$nivel", style = MaterialTheme.typography.titleLarge, color = textColorFor(fillColor))
    }
}

/**
 * XP progress bar for the current level. When [showLabel] is set it prints
 * `NÍVEL n` on the left and `xpNoNivelAtual / xpNecessario XP` on the right above the bar.
 */
@Composable
fun XpBar(
    estado: Progressao.Estado,
    modifier: Modifier = Modifier,
    showLabel: Boolean = true,
    levelLabel: Boolean = true,
    trackColor: Color = Lavender,
    fillColor: Color = Purple
) {
    androidx.compose.foundation.layout.Column(modifier) {
        if (showLabel) {
            Row(
                Modifier.fillMaxWidth(),
                horizontalArrangement = if (levelLabel) Arrangement.SpaceBetween else Arrangement.End
            ) {
                if (levelLabel) Text("NÍVEL ${estado.nivel}", style = MaterialTheme.typography.labelLarge, color = Ink)
                Text(
                    "${estado.xpNoNivelAtual} / ${estado.xpNecessarioProximoNivel} XP",
                    style = MaterialTheme.typography.labelLarge, color = Ink
                )
            }
            Spacer(Modifier.size(6.dp))
        }
        Box(
            Modifier.fillMaxWidth().height(16.dp).clip(RoundedCornerShape(8.dp))
                .background(trackColor).border(3.dp, Ink, RoundedCornerShape(8.dp))
        ) {
            Box(
                Modifier.fillMaxHeight().fillMaxWidth(estado.fracao)
                    .clip(RoundedCornerShape(8.dp)).background(fillColor)
            )
        }
    }
}

/** Compact "Nv N" pill for tight rows (ranking list). */
@Composable
fun LevelPill(nivel: Int, modifier: Modifier = Modifier, fillColor: Color = Purple) {
    Box(
        modifier
            .clip(RoundedCornerShape(10.dp))
            .background(fillColor)
            .border(2.dp, Ink, RoundedCornerShape(10.dp))
            .padding(horizontal = 8.dp, vertical = 3.dp),
        contentAlignment = Alignment.Center
    ) {
        Text("Nv $nivel", style = MaterialTheme.typography.labelLarge, color = textColorFor(fillColor), textAlign = TextAlign.Center)
    }
}
