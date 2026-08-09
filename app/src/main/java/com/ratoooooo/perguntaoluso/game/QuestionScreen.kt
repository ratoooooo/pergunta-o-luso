package com.ratoooooo.perguntaoluso.game

import androidx.compose.animation.animateColorAsState
import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.FastOutSlowInEasing
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.tween
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.draw.scale
import androidx.compose.ui.graphics.graphicsLayer
import com.ratoooooo.perguntaoluso.ui.theme.Motion
import com.ratoooooo.perguntaoluso.ui.theme.cascadeIn
import com.ratoooooo.perguntaoluso.ui.theme.pressScale
import com.ratoooooo.perguntaoluso.ui.theme.rememberPressScale
import com.ratoooooo.perguntaoluso.ui.theme.rememberPulse
import com.ratoooooo.perguntaoluso.ui.theme.stickerSpring
import androidx.compose.foundation.background
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.Bolt
import androidx.compose.material.icons.rounded.Check
import androidx.compose.material.icons.rounded.Close
import androidx.compose.material.icons.rounded.Whatshot
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.ratoooooo.perguntaoluso.data.Question
import com.ratoooooo.perguntaoluso.ui.theme.AnswerPalette
import com.ratoooooo.perguntaoluso.ui.theme.Coral
import com.ratoooooo.perguntaoluso.ui.theme.Cream
import com.ratoooooo.perguntaoluso.ui.theme.Gold
import com.ratoooooo.perguntaoluso.ui.theme.Ink
import com.ratoooooo.perguntaoluso.ui.theme.Lavender
import com.ratoooooo.perguntaoluso.ui.theme.Neutral
import com.ratoooooo.perguntaoluso.ui.theme.Teal
import com.ratoooooo.perguntaoluso.ui.theme.stickerBlock
import com.ratoooooo.perguntaoluso.ui.theme.textColorFor

private val OPTION_CARD_HEIGHT = 68.dp
private val VF_CARD_HEIGHT = 92.dp

@Composable
fun QuestionScreen(
    question: Question,
    categoria: String,
    mode: GameMode,
    questionNumber: Int,
    totalQuestions: Int,
    points: Int,
    selectedOption: String?,
    isAnswered: Boolean,
    aceitaToques: Boolean,
    lastDelta: Int,
    streak: Int,
    currentEvent: ChaoticEvent?,
    remainingMillis: Long,
    durationMillis: Long,
    onOptionSelected: (String) -> Unit
) {
    Column(
        modifier = Modifier
            .fillMaxSize()
            .background(Cream)
            // Fase 29: sem scroll, ecrãs mais baixos (ou letra grande do sistema) cortavam o
            // conteúdo sem forma de lá chegar. `fillMaxSize` garante que continua centrado
            // quando sobra espaço; o scroll só entra em ação quando falta.
            .verticalScroll(rememberScrollState())
            .padding(20.dp)
    ) {
        // Header: step indicator (Manrope) on the left, points (Fredoka) on the right.
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically
        ) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                androidx.compose.material3.Icon(
                    imageVector = com.ratoooooo.perguntaoluso.ui.theme.iconForCategory(categoria),
                    contentDescription = null,
                    tint = com.ratoooooo.perguntaoluso.ui.theme.colorForCategory(categoria),
                    modifier = Modifier.size(30.dp)
                )
                Spacer(Modifier.size(10.dp))
                Column {
                    Text(
                        text = "Pergunta $questionNumber de $totalQuestions",
                        style = MaterialTheme.typography.labelLarge,
                        color = Ink
                    )
                    Text(
                        text = "$categoria · ${mode.displayName}",
                        style = MaterialTheme.typography.bodyLarge,
                        color = Ink
                    )
                }
            }
            Column(horizontalAlignment = Alignment.End) {
                Text(
                    text = "$points",
                    style = MaterialTheme.typography.titleLarge,
                    color = Ink
                )
                Text(
                    text = "pontos",
                    style = MaterialTheme.typography.labelLarge,
                    color = Ink
                )
                // Sequência: só aparece a partir de 2 acertos seguidos e vai ganhando
                // presença — 2 é discreto (dourado), 5+ é coral e pulsa. Cresce com o
                // mérito em vez de estar sempre visível a ocupar espaço.
                if (streak >= 2) {
                    val forte = streak >= 5
                    val pulso = rememberPulse(ativo = forte, min = 1f, max = 1.10f, periodoMs = 800)
                    val corStreak = if (forte) Coral else Gold
                    Spacer(Modifier.size(6.dp))
                    Row(
                        modifier = Modifier
                            .scale(pulso)
                            .stickerBlock(fillColor = corStreak, cornerRadius = 12.dp, shadowOffset = 3.dp, borderWidth = 2.dp)
                            .padding(horizontal = 10.dp, vertical = 4.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        androidx.compose.material3.Icon(
                            Icons.Rounded.Whatshot, contentDescription = null,
                            tint = textColorFor(corStreak), modifier = Modifier.size(16.dp)
                        )
                        Spacer(Modifier.size(4.dp))
                        Text("$streak", style = MaterialTheme.typography.labelLarge, color = textColorFor(corStreak))
                    }
                }
            }
        }

        Spacer(Modifier.size(14.dp))

        TimerBar(remainingMillis = remainingMillis, durationMillis = durationMillis)

        if (currentEvent != null) {
            Spacer(Modifier.size(12.dp))
            EventBannerRow(currentEvent.displayName, currentEvent.description)
        }

        Spacer(Modifier.size(16.dp))

        Box(
            modifier = Modifier
                .fillMaxWidth()
                .stickerBlock(fillColor = Lavender, cornerRadius = 28.dp)
                .padding(24.dp)
        ) {
            Text(
                text = question.pergunta,
                style = MaterialTheme.typography.titleLarge,
                color = Ink
            )
        }

        Spacer(Modifier.size(20.dp))

        val userWasWrong = isAnswered && selectedOption != question.respostaCorreta
        val vf = question.isVerdadeiroFalso

        // Cartões neutros com emblema A/B/C/D — a cor só entra na revelação (ver AnswerOption).
        // Verdadeiro/Falso mantém dois cartões mais altos, com ✓/✗ no emblema, para o layout
        // de duas opções se ler como deliberado e não como uma lista a que faltam itens.
        val context = androidx.compose.ui.platform.LocalContext.current

        // O som segue o **estado**, não o toque. Antes era disparado dentro do `onClick` de cada
        // opção, o que deixava o **tempo esgotado** sem retorno nenhum — precisamente o momento
        // em que o jogador não olhou para o ecrã e mais precisa de o ouvir. Aqui apanha os dois
        // caminhos, porque `isAnswered` também fica `true` no timeout.
        androidx.compose.runtime.LaunchedEffect(isAnswered, questionNumber) {
            if (!isAnswered) return@LaunchedEffect
            com.ratoooooo.perguntaoluso.audio.SoundEffects.tocar(
                context,
                if (userWasWrong) com.ratoooooo.perguntaoluso.audio.SoundEffects.Efeito.ERRADO
                else com.ratoooooo.perguntaoluso.audio.SoundEffects.Efeito.CERTO
            )
        }

        Column(
            modifier = Modifier.fillMaxWidth(),
            verticalArrangement = Arrangement.spacedBy(if (vf) 18.dp else 14.dp)
        ) {
            question.opcoes.forEachIndexed { index, opcao ->
                AnswerOption(
                    text = opcao,
                    index = index,
                    isVerdadeiroFalso = vf,
                    isAnswered = isAnswered,
                    aceitaToques = aceitaToques,
                    isCorrectOption = opcao == question.respostaCorreta,
                    isSelected = opcao == selectedOption,
                    userWasWrong = userWasWrong,
                    height = if (vf) VF_CARD_HEIGHT else OPTION_CARD_HEIGHT,
                    animationKey = question.pergunta,
                    onClick = { onOptionSelected(opcao) }
                )
            }
        }

        Spacer(Modifier.weight(1f))

        // Os pontos ganhos "sobem": entram de baixo, sobem ~26 dp e desvanecem enquanto o
        // total no cabeçalho já mostra o valor novo.
        if (isAnswered && lastDelta != 0) {
            Spacer(Modifier.size(12.dp))
            val subida = remember(lastDelta, questionNumber) { Animatable(0f) }
            LaunchedEffect(lastDelta, questionNumber) {
                subida.snapTo(0f)
                subida.animateTo(1f, tween(900, easing = FastOutSlowInEasing))
            }
            val p = subida.value
            val deltaText = if (lastDelta > 0) "+$lastDelta pontos" else "$lastDelta pontos"
            Text(
                text = deltaText,
                style = MaterialTheme.typography.titleLarge,
                color = if (lastDelta > 0) Teal else Coral,
                modifier = Modifier
                    .fillMaxWidth()
                    .graphicsLayer {
                        translationY = (1f - p) * 20.dp.toPx() - p * 26.dp.toPx()
                        alpha = if (p < 0.25f) p / 0.25f else (1f - (p - 0.25f) / 0.75f).coerceAtLeast(0.25f)
                    },
                textAlign = androidx.compose.ui.text.style.TextAlign.Center
            )
        }
    }
}

/**
 * Barra de tempo com urgência crescente: a cor já mudava por patamares, agora abaixo de 25%
 * a barra inteira pulsa — quanto menos tempo resta, mais rápido o pulso (1000 ms → 260 ms).
 * O pulso é só escala vertical, não mexe no resto do ecrã nem atrasa a resposta.
 */
@Composable
private fun TimerBar(remainingMillis: Long, durationMillis: Long) {
    val fraction = if (durationMillis > 0) (remainingMillis.toFloat() / durationMillis.toFloat()).coerceIn(0f, 1f) else 0f
    val fillColor: Color = when {
        fraction > 0.5f -> Teal
        fraction > 0.25f -> Gold
        else -> Coral
    }
    val urgente = fraction <= 0.25f && fraction > 0f
    val periodo = (260 + (fraction / 0.25f) * 740).toInt().coerceAtLeast(220)
    val pulso = rememberPulse(ativo = urgente, min = 1f, max = 1.22f, periodoMs = periodo)
    Box(
        modifier = Modifier
            .fillMaxWidth()
            .height(20.dp)
            .scale(scaleX = 1f, scaleY = if (urgente) pulso else 1f)
            .clip(RoundedCornerShape(10.dp))
            .background(Lavender)
            .border(3.dp, Ink, RoundedCornerShape(10.dp))
    ) {
        Box(
            modifier = Modifier
                .fillMaxHeight()
                .fillMaxWidth(fraction)
                .clip(RoundedCornerShape(10.dp))
                .background(fillColor)
        )
    }
}

