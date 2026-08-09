package com.ratoooooo.perguntaoluso.game

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.Check
import androidx.compose.material.icons.rounded.Close
import androidx.compose.material.icons.rounded.SportsKabaddi
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import com.ratoooooo.perguntaoluso.data.Convite
import com.ratoooooo.perguntaoluso.ui.theme.Coral
import com.ratoooooo.perguntaoluso.ui.theme.Cream
import com.ratoooooo.perguntaoluso.ui.theme.Gold
import com.ratoooooo.perguntaoluso.ui.theme.Ink
import com.ratoooooo.perguntaoluso.ui.theme.Purple
import com.ratoooooo.perguntaoluso.ui.theme.StickerButton
import com.ratoooooo.perguntaoluso.ui.theme.Teal
import com.ratoooooo.perguntaoluso.ui.theme.stickerBlock
import com.ratoooooo.perguntaoluso.ui.theme.stickerCircle
import com.ratoooooo.perguntaoluso.ui.theme.textColorFor

/**
 * Real-time challenge notification. Rendered on top of whatever screen the player is on (except
 * an active match), so an invite never goes unnoticed while the app is open.
 */
@Composable
fun ChallengeOverlay(convite: Convite, onAccept: () -> Unit, onDecline: () -> Unit) {
    Box(
        Modifier.fillMaxSize().background(Ink.copy(alpha = 0.55f)).padding(28.dp),
        contentAlignment = Alignment.Center
    ) {
        Column(
            Modifier.fillMaxWidth().stickerBlock(fillColor = Purple, cornerRadius = 30.dp, shadowOffset = 7.dp).padding(24.dp),
            horizontalAlignment = Alignment.CenterHorizontally
        ) {
            Box(Modifier.size(64.dp).stickerCircle(fillColor = Cream, shadowOffset = 4.dp), contentAlignment = Alignment.Center) {
                Icon(Icons.Rounded.SportsKabaddi, contentDescription = null, tint = Ink, modifier = Modifier.size(34.dp))
            }
            Spacer(Modifier.size(14.dp))
            Text("Desafio!", style = MaterialTheme.typography.headlineLarge, color = textColorFor(Purple))
            Spacer(Modifier.size(6.dp))
            Text(
                "${convite.nome} desafiou-te para um ${convite.formato}",
                style = MaterialTheme.typography.bodyLarge, color = textColorFor(Purple), textAlign = TextAlign.Center
            )
            Spacer(Modifier.size(14.dp))
            Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                Pill(convite.categoria, Gold)
                Pill(GameMode.displayNameForId(convite.modo), Coral)
            }
            Spacer(Modifier.size(22.dp))
            StickerButton("ACEITAR", Icons.Rounded.Check, onAccept, fillColor = Teal, modifier = Modifier.fillMaxWidth())
            Spacer(Modifier.size(12.dp))
            StickerButton("RECUSAR", Icons.Rounded.Close, onDecline, fillColor = Coral, modifier = Modifier.fillMaxWidth())
        }
    }
}

@Composable
private fun Pill(text: String, color: Color) {
    Box(
        Modifier.stickerBlock(fillColor = color, cornerRadius = 14.dp, shadowOffset = 3.dp, borderWidth = 2.dp)
            .padding(horizontal = 14.dp, vertical = 7.dp)
    ) {
        Text(text, style = MaterialTheme.typography.labelLarge, color = textColorFor(color), maxLines = 1)
    }
}
