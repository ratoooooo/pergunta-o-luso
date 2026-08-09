package com.ratoooooo.perguntaoluso.game

import androidx.compose.animation.animateColorAsState
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.tween
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.Bolt
import androidx.compose.material.icons.rounded.Check
import androidx.compose.material.icons.rounded.Close
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import com.ratoooooo.perguntaoluso.ui.theme.Coral
import com.ratoooooo.perguntaoluso.ui.theme.Cream
import com.ratoooooo.perguntaoluso.ui.theme.Gold
import com.ratoooooo.perguntaoluso.ui.theme.Lavender
import com.ratoooooo.perguntaoluso.ui.theme.Motion
import com.ratoooooo.perguntaoluso.ui.theme.Neutral
import com.ratoooooo.perguntaoluso.ui.theme.Purple
import com.ratoooooo.perguntaoluso.ui.theme.Teal
import com.ratoooooo.perguntaoluso.ui.theme.cascadeIn
import com.ratoooooo.perguntaoluso.ui.theme.pressScale
import com.ratoooooo.perguntaoluso.ui.theme.rememberPressScale
import com.ratoooooo.perguntaoluso.ui.theme.stickerBlock
import com.ratoooooo.perguntaoluso.ui.theme.stickerCircle
import com.ratoooooo.perguntaoluso.ui.theme.textColorFor
import com.ratoooooo.perguntaoluso.ui.theme.stickerSpring

private val LETRAS = listOf("A", "B", "C", "D", "E", "F")

/**
 * Opção de resposta, partilhada pelo jogo solo e pelo multijogador.
 *
 * **Em repouso o cartão é neutro (lavanda) com um emblema roxo**; só depois de responder é
 * que ganha cor. Antes, as quatro opções nasciam pintadas de Roxo/Coral/Teal/Dourado — as
 * mesmas cores que a revelação usa para dizer "certa" (Teal), "era esta" (Dourado) e
 * "erraste" (Coral). Uma opção ainda por responder aparecia verde ou vermelha e parecia já
 * estar corrigida. Com o cartão neutro, qualquer cor no ecrã significa exactamente uma
 * coisa: resultado.
 *
 * A letra A/B/C/D substitui a cor como forma de distinguir as opções (é o que o mockup faz,
 * no ecrã 9); em Verdadeiro/Falso o emblema mostra ✓/✗, que identifica a afirmação sem
 * insinuar qual está certa.
 */
@Composable
fun AnswerOption(
    text: String,
    index: Int,
    isVerdadeiroFalso: Boolean,
    isAnswered: Boolean,
    isCorrectOption: Boolean,
    isSelected: Boolean,
    userWasWrong: Boolean,
    height: Dp,
    animationKey: Any,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    /**
     * Janela de carência logo depois de a pergunta abrir. Bloqueia o toque **sem** mudar as
     * cores — ao contrário de `isAnswered`, que também pinta a revelação.
     *
     * Existe porque, ao responder e voltar a tocar no mesmo sítio, o segundo toque caía já
     * depois de a pergunta seguinte ter carregado e respondia-a de imediato: o jogador via
     * uma pergunta ser "saltada" sem ter escolhido nada.
     */
    aceitaToques: Boolean = true
) {
    val alvo = when {
        !isAnswered -> Lavender
        isCorrectOption -> Teal
        isSelected -> Coral
        else -> Neutral
    }
    // A revelação transita em ~280 ms em vez de cortar a seco.
    val cor by animateColorAsState(alvo, tween(Motion.FEEDBACK_MS), label = "optionColor")
    // A opção certa dá um pequeno salto ao ser revelada.
    val destaque by animateFloatAsState(
        targetValue = if (isAnswered && isCorrectOption) 1.03f else 1f,
        animationSpec = stickerSpring(),
        label = "optionPop"
    )
    val (interacao, escalaToque) = rememberPressScale()
    val corTexto = textColorFor(cor)
    // Emblema: roxo enquanto a opção é só uma escolha; creme depois de responder, para se
    // ler bem por cima de qualquer uma das cores de resultado.
    val corEmblema = if (isAnswered) Cream else Purple

    Row(
        modifier = modifier
            .fillMaxWidth()
            .height(height)
            .cascadeIn(index, key = animationKey)
            .pressScale(escalaToque * destaque)
            .stickerBlock(fillColor = cor, cornerRadius = 22.dp, shadowOffset = 5.dp)
            .clickable(
                interactionSource = interacao,
                indication = null,
                enabled = !isAnswered && aceitaToques,
                onClick = onClick
            )
            .padding(horizontal = 14.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.Start
    ) {
        Box(
            Modifier.size(if (isVerdadeiroFalso) 40.dp else 34.dp)
                .stickerCircle(fillColor = corEmblema, shadowOffset = 2.dp, borderWidth = 2.dp),
            contentAlignment = Alignment.Center
        ) {
            if (isVerdadeiroFalso) {
                Icon(
                    imageVector = if (index == 0) Icons.Rounded.Check else Icons.Rounded.Close,
                    contentDescription = null,
                    tint = textColorFor(corEmblema),
                    modifier = Modifier.size(24.dp)
                )
            } else {
                Text(
                    text = LETRAS.getOrElse(index) { "?" },
                    style = MaterialTheme.typography.labelLarge,
                    color = textColorFor(corEmblema)
                )
            }
        }
        Spacer(Modifier.size(12.dp))
        Text(
            text = text,
            style = if (isVerdadeiroFalso) MaterialTheme.typography.titleLarge
            else MaterialTheme.typography.bodyLarge.copy(fontWeight = FontWeight.Bold),
            color = corTexto,
            textAlign = TextAlign.Start,
            modifier = Modifier.weight(1f)
        )
    }
}

/**
 * Faixa do evento Caótico. Roxo (informação), não dourado: o dourado no ecrã da pergunta
 * passou a querer dizer só uma coisa — "era esta a resposta certa".
 */
@Composable
fun EventBannerRow(
    titulo: String,
    descricao: String,
    modifier: Modifier = Modifier
) {
    Row(
        modifier = modifier
            .fillMaxWidth()
            .stickerBlock(fillColor = Purple, cornerRadius = 18.dp, shadowOffset = 4.dp)
            .padding(horizontal = 16.dp, vertical = 11.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Icon(
            imageVector = Icons.Rounded.Bolt,
            contentDescription = null,
            tint = Gold,
            modifier = Modifier.size(26.dp)
        )
        Spacer(Modifier.size(10.dp))
        androidx.compose.foundation.layout.Column {
            Text(titulo, style = MaterialTheme.typography.labelLarge, color = Cream)
            Text(descricao, style = MaterialTheme.typography.bodyLarge, color = Cream)
        }
    }
}
