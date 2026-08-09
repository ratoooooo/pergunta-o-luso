package com.ratoooooo.perguntaoluso.ui.theme

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp

/**
 * Separadores da app, em dois níveis de importância. Ambos usam **Roxo** para o estado
 * activo: o Dourado ficou reservado para a ação primária do ecrã e para o primeiro lugar,
 * por isso um filtro (que não é ação nem mérito) nunca é dourado.
 *
 * - [SegmentedTabs] — nível principal: pastilha roxa cheia dentro de uma calha lavanda.
 * - [UnderlineTabs] — nível secundário: só texto com um sublinhado roxo, o mesmo sinal
 *   de "activo" que a barra de navegação inferior já usa.
 *
 * Ter os dois permite empilhar dois filtros no mesmo ecrã (Ranking: modo + lista) sem que
 * compitam — o primeiro tem massa de cor, o segundo é leve.
 */
@Composable
fun SegmentedTabs(
    labels: List<String>,
    selectedIndex: Int,
    onSelect: (Int) -> Unit,
    modifier: Modifier = Modifier,
    /** Reduz o corpo do texto para caber um 4.º separador sem cortar palavras. */
    compact: Boolean = labels.size >= 4,
    /** Ponto coral antes do rótulo — separador com algo à espera de resposta. */
    alerts: List<Boolean>? = null
) {
    Row(
        modifier = modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(20.dp))
            .background(Lavender)
            .border(BorderStroke(3.dp, Ink), RoundedCornerShape(20.dp))
            .padding(4.dp),
        horizontalArrangement = Arrangement.spacedBy(4.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        labels.forEachIndexed { index, label ->
            val activo = index == selectedIndex
            Box(
                modifier = Modifier
                    .weight(1f)
                    .clip(RoundedCornerShape(16.dp))
                    .background(if (activo) Purple else Lavender)
                    .clickable { onSelect(index) }
                    .padding(vertical = 10.dp, horizontal = if (compact) 2.dp else 4.dp),
                contentAlignment = Alignment.Center
            ) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    if (alerts?.getOrNull(index) == true && !activo) {
                        Box(Modifier.size(8.dp).clip(RoundedCornerShape(4.dp)).background(Coral))
                        Spacer(Modifier.size(5.dp))
                    }
                    Text(
                        text = label,
                        style = MaterialTheme.typography.bodyLarge.copy(
                            fontWeight = if (activo) FontWeight.Bold else FontWeight.Normal,
                            fontSize = if (compact) 13.sp else 16.sp
                        ),
                        color = if (activo) Cream else Ink,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis,
                        textAlign = TextAlign.Center
                    )
                }
            }
        }
    }
}

@Composable
fun UnderlineTabs(
    labels: List<String>,
    selectedIndex: Int,
    onSelect: (Int) -> Unit,
    modifier: Modifier = Modifier
) {
    Row(
        modifier = modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.spacedBy(4.dp),
        verticalAlignment = Alignment.Top
    ) {
        labels.forEachIndexed { index, label ->
            val activo = index == selectedIndex
            Column(
                modifier = Modifier
                    .weight(1f)
                    .clip(RoundedCornerShape(10.dp))
                    .clickable { onSelect(index) }
                    .padding(vertical = 6.dp, horizontal = 2.dp),
                horizontalAlignment = Alignment.CenterHorizontally
            ) {
                Text(
                    text = label,
                    style = MaterialTheme.typography.bodyLarge.copy(
                        fontWeight = if (activo) FontWeight.Bold else FontWeight.Normal
                    ),
                    color = if (activo) Purple else Ink,
                    maxLines = 1,
                    textAlign = TextAlign.Center
                )
                Spacer(Modifier.size(5.dp))
                Box(
                    Modifier
                        .width(22.dp)
                        .height(3.dp)
                        .clip(RoundedCornerShape(2.dp))
                        .background(if (activo) Purple else androidx.compose.ui.graphics.Color.Transparent)
                )
            }
        }
    }
}
