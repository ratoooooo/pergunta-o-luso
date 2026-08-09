package com.ratoooooo.perguntaoluso.game.avatar

import com.ratoooooo.perguntaoluso.ui.theme.stickerSpring
import com.ratoooooo.perguntaoluso.ui.theme.rememberPressScale
import com.ratoooooo.perguntaoluso.ui.theme.pressScale
import com.ratoooooo.perguntaoluso.ui.theme.cascadeIn
import androidx.compose.runtime.getValue
import androidx.compose.foundation.lazy.grid.itemsIndexed
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.lazy.grid.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.Check
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import com.ratoooooo.perguntaoluso.game.ScreenHeader
import com.ratoooooo.perguntaoluso.ui.theme.Cream
import com.ratoooooo.perguntaoluso.ui.theme.Gold
import com.ratoooooo.perguntaoluso.ui.theme.Ink
import com.ratoooooo.perguntaoluso.ui.theme.stickerCircle
import com.ratoooooo.perguntaoluso.ui.theme.textColorFor

@Composable
fun AvatarPickerScreen(
    current: String?,
    onSelect: (String) -> Unit,
    onBack: () -> Unit
) {
    Column(Modifier.fillMaxSize().background(Cream).padding(horizontal = 24.dp).padding(top = 24.dp)) {
        ScreenHeader(title = "Escolhe o teu avatar", subtitle = "Símbolos de Portugal", onBack = onBack)
        Spacer(Modifier.size(18.dp))
        LazyVerticalGrid(
            columns = GridCells.Fixed(3),
            horizontalArrangement = Arrangement.spacedBy(14.dp),
            verticalArrangement = Arrangement.spacedBy(18.dp)
        ) {
            itemsIndexed(PortugueseSymbol.entries.toList()) { index, sym ->
                val selected = sym.id == current
                val (interacao, escalaToque) = rememberPressScale()
                // "Pop" ao selecionar: o símbolo escolhido salta ligeiramente acima do
                // tamanho normal e assenta com mola, para a escolha se sentir confirmada.
                val escalaSel by animateFloatAsState(
                    targetValue = if (selected) 1.08f else 1f,
                    animationSpec = stickerSpring(),
                    label = "avatarPop"
                )
                Column(
                    horizontalAlignment = Alignment.CenterHorizontally,
                    modifier = Modifier
                        .cascadeIn(index)
                        .pressScale(escalaToque * escalaSel)
                        .clickable(interactionSource = interacao, indication = null) { onSelect(sym.id) }
                ) {
                    // O escolhido marca-se com um emblema de visto, não só com um anel dourado:
                    // o anel dourado desaparecia por cima dos símbolos que já são dourados
                    // (Pastel de Nata, Galo de Barcelos) e não se via qual estava escolhido.
                    Box(contentAlignment = Alignment.Center) {
                        Box(
                            Modifier.fillMaxWidth().aspectRatio(1f)
                                .stickerCircle(fillColor = sym.bg, shadowOffset = 5.dp, borderWidth = if (selected) 4.dp else 3.dp),
                            contentAlignment = Alignment.Center
                        ) {
                            SymbolIcon(sym, Modifier.fillMaxSize(0.6f), tint = textColorFor(sym.bg))
                        }
                        if (selected) {
                            Box(
                                Modifier.size(30.dp).align(Alignment.BottomEnd)
                                    .stickerCircle(fillColor = Gold, shadowOffset = 2.dp, borderWidth = 2.dp),
                                contentAlignment = Alignment.Center
                            ) {
                                Icon(Icons.Rounded.Check, contentDescription = "Escolhido", tint = Ink, modifier = Modifier.size(18.dp))
                            }
                        }
                    }
                    Spacer(Modifier.size(6.dp))
                    Text(sym.displayName, style = MaterialTheme.typography.labelLarge, color = Ink, textAlign = TextAlign.Center, maxLines = 2)
                }
            }
        }
    }
}
