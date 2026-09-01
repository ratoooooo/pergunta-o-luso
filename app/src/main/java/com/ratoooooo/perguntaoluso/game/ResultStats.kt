package com.ratoooooo.perguntaoluso.game

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.ratoooooo.perguntaoluso.data.Progressao
import com.ratoooooo.perguntaoluso.ui.theme.Cream
import com.ratoooooo.perguntaoluso.ui.theme.Ink
import com.ratoooooo.perguntaoluso.ui.theme.Purple
import com.ratoooooo.perguntaoluso.ui.theme.Teal
import com.ratoooooo.perguntaoluso.ui.theme.cascadeIn
import com.ratoooooo.perguntaoluso.ui.theme.stickerBlock

/**
 * Três números do fim de partida — perguntas, precisão e XP ganho — como no ecrã 10 do
 * mockup. São a resposta às perguntas que o jogador faz a seguir a jogar ("acertei
 * quantas?", "ganhei quanto?") e antes não estavam em lado nenhum: o XP só se via depois,
 * no Início, já somado ao total.
 *
 * O XP vem da mesma [Progressao.xpGanho] que o perfil usa para acumular, por isso o número
 * mostrado é literalmente o que foi escrito na base de dados — não uma estimativa própria
 * do ecrã.
 *
 * Os cartões são de contorno sobre creme, não blocos cheios de cor: são leitura, não ação,
 * e nesta altura o dourado do resultado já é a única mancha forte do ecrã.
 */
@Composable
fun ResultStats(
    modoId: String,
    perguntas: Int,
    respostasCertas: Int,
    venceu: Boolean,
    modifier: Modifier = Modifier
) {
    val precisao = if (perguntas > 0) (respostasCertas * 100) / perguntas else 0
    val xp = Progressao.xpGanho(modoId, respostasCertas, venceu)
    Row(modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(10.dp)) {
        StatTile("$perguntas", "Perguntas", Purple, 0, Modifier.weight(1f))
        StatTile("$precisao%", "Precisão", Teal, 1, Modifier.weight(1f))
        StatTile("+$xp", "XP ganho", Ink, 2, Modifier.weight(1f))
    }
}

@Composable
private fun StatTile(value: String, label: String, valueColor: Color, index: Int, modifier: Modifier = Modifier) {
    Column(
        modifier
            .cascadeIn(index, key = value + label)
            .stickerBlock(fillColor = Cream, cornerRadius = 18.dp, shadowOffset = 4.dp, borderWidth = 2.dp)
            .padding(vertical = 14.dp, horizontal = 6.dp),
        horizontalAlignment = Alignment.CenterHorizontally
    ) {
        Text(value, style = MaterialTheme.typography.titleLarge, color = valueColor, maxLines = 1, overflow = TextOverflow.Ellipsis)
        Spacer(Modifier.size(2.dp))
        Text(label, style = MaterialTheme.typography.bodyLarge, color = Ink, maxLines = 1, overflow = TextOverflow.Ellipsis, textAlign = TextAlign.Center)
    }
}
