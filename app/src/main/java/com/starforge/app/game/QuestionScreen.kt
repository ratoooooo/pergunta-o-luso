package com.starforge.app.game

import androidx.compose.foundation.background
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
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.starforge.app.data.Question
import com.starforge.app.ui.theme.AnswerPalette
import com.starforge.app.ui.theme.Coral
import com.starforge.app.ui.theme.Cream
import com.starforge.app.ui.theme.Gold
import com.starforge.app.ui.theme.Ink
import com.starforge.app.ui.theme.Lavender
import com.starforge.app.ui.theme.Neutral
import com.starforge.app.ui.theme.Teal
import com.starforge.app.ui.theme.stickerBlock
import com.starforge.app.ui.theme.textColorFor

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
    lastDelta: Int,
    currentEvent: ChaoticEvent?,
    remainingMillis: Long,
    durationMillis: Long,
    onOptionSelected: (String) -> Unit
) {
    Column(
        modifier = Modifier
            .fillMaxSize()
            .background(Cream)
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
                    imageVector = com.starforge.app.ui.theme.iconForCategory(categoria),
                    contentDescription = null,
                    tint = com.starforge.app.ui.theme.colorForCategory(categoria),
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
            }
        }

        Spacer(Modifier.size(14.dp))

        TimerBar(remainingMillis = remainingMillis, durationMillis = durationMillis)

        if (currentEvent != null) {
            Spacer(Modifier.size(12.dp))
            EventBanner(currentEvent)
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

        // Uniform, moderately sized option cards with centred text. True/False gets two taller,
        // icon-labelled cards so the two-option layout reads as deliberate instead of a
        // four-option list missing half its items.
        Column(
            modifier = Modifier.fillMaxWidth(),
            verticalArrangement = Arrangement.spacedBy(if (vf) 18.dp else 14.dp)
        ) {
            question.opcoes.forEachIndexed { index, opcao ->
                val baseColor = if (vf) (if (index == 0) Teal else Coral) else AnswerPalette[index % AnswerPalette.size]
                val isCorrectOption = opcao == question.respostaCorreta
                val isSelected = opcao == selectedOption
                val color = when {
                    !isAnswered -> baseColor
                    isCorrectOption && isSelected -> Teal
                    isCorrectOption && userWasWrong -> Gold
                    isSelected -> Coral
                    else -> Neutral
                }
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(if (vf) VF_CARD_HEIGHT else OPTION_CARD_HEIGHT)
                        .stickerBlock(fillColor = color, cornerRadius = 22.dp, shadowOffset = 5.dp)
                        .clickable(enabled = !isAnswered) { onOptionSelected(opcao) }
                        .padding(horizontal = 18.dp),
                    horizontalArrangement = Arrangement.Center,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    if (vf) {
                        androidx.compose.material3.Icon(
                            imageVector = if (index == 0) Icons.Rounded.Check else Icons.Rounded.Close,
                            contentDescription = null,
                            tint = textColorFor(color),
                            modifier = Modifier.size(30.dp)
                        )
                        Spacer(Modifier.size(12.dp))
                    }
                    Text(
                        text = opcao,
                        style = if (vf) MaterialTheme.typography.titleLarge
                        else MaterialTheme.typography.bodyLarge.copy(fontWeight = FontWeight.Bold),
                        color = textColorFor(color),
                        textAlign = androidx.compose.ui.text.style.TextAlign.Center
                    )
                }
            }
        }

        Spacer(Modifier.weight(1f))

        if (isAnswered && lastDelta != 0) {
            Spacer(Modifier.size(12.dp))
            val deltaText = if (lastDelta > 0) "+$lastDelta pontos" else "$lastDelta pontos"
            Text(
                text = deltaText,
                style = MaterialTheme.typography.titleLarge,
                color = if (lastDelta > 0) Teal else Coral,
                modifier = Modifier.fillMaxWidth(),
                textAlign = androidx.compose.ui.text.style.TextAlign.Center
            )
        }
    }
}

@Composable
private fun TimerBar(remainingMillis: Long, durationMillis: Long) {
    val fraction = if (durationMillis > 0) (remainingMillis.toFloat() / durationMillis.toFloat()).coerceIn(0f, 1f) else 0f
    val fillColor: Color = when {
        fraction > 0.5f -> Teal
        fraction > 0.25f -> Gold
        else -> Coral
    }
    Box(
        modifier = Modifier
            .fillMaxWidth()
            .height(20.dp)
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

@Composable
private fun EventBanner(event: ChaoticEvent) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .stickerBlock(fillColor = Gold, cornerRadius = 18.dp, shadowOffset = 4.dp)
            .padding(horizontal = 18.dp, vertical = 12.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        androidx.compose.material3.Icon(
            imageVector = Icons.Rounded.Bolt,
            contentDescription = null,
            tint = Ink,
            modifier = Modifier.size(28.dp)
        )
        Spacer(Modifier.size(10.dp))
        Column {
            Text(
                text = event.displayName,
                style = MaterialTheme.typography.labelLarge,
                color = Ink
            )
            Text(
                text = event.description,
                style = MaterialTheme.typography.bodyLarge,
                color = Ink
            )
        }
    }
}
