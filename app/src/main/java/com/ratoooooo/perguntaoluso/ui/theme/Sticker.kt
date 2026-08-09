package com.ratoooooo.perguntaoluso.ui.theme

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.drawBehind
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.PathEffect
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp

/**
 * Comic/sticker block: flat fill, thick ink border, hard offset shadow (no blur).
 */
fun Modifier.stickerBlock(
    fillColor: Color,
    cornerRadius: Dp = 24.dp,
    shadowOffset: Dp = 6.dp,
    borderWidth: Dp = 3.dp,
    borderColor: Color = Ink
): Modifier = this
    .drawBehind {
        val offsetPx = shadowOffset.toPx()
        drawRoundRect(
            color = borderColor,
            topLeft = Offset(offsetPx, offsetPx),
            size = size,
            cornerRadius = CornerRadius(cornerRadius.toPx(), cornerRadius.toPx())
        )
    }
    .clip(RoundedCornerShape(cornerRadius))
    .background(fillColor)
    .border(BorderStroke(borderWidth, borderColor), RoundedCornerShape(cornerRadius))

fun Modifier.stickerCircle(
    fillColor: Color,
    shadowOffset: Dp = 6.dp,
    borderWidth: Dp = 3.dp,
    borderColor: Color = Ink
): Modifier = this
    .drawBehind {
        val offsetPx = shadowOffset.toPx()
        drawOval(
            color = borderColor,
            topLeft = Offset(offsetPx, offsetPx),
            size = size
        )
    }
    .clip(CircleShape)
    .background(fillColor)
    .border(BorderStroke(borderWidth, borderColor), CircleShape)

/**
 * Placeholder seat/slot: muted fill, dashed rounded border, no shadow.
 * Used for empty seats in the waiting room ("Vaga livre" / "À procura...").
 */
fun Modifier.stickerDashed(
    fillColor: Color = Cream,
    cornerRadius: Dp = 18.dp,
    borderWidth: Dp = 2.dp,
    borderColor: Color = Neutral
): Modifier = this
    .clip(RoundedCornerShape(cornerRadius))
    .background(fillColor)
    .drawBehind {
        val w = borderWidth.toPx()
        drawRoundRect(
            color = borderColor,
            topLeft = Offset(w / 2, w / 2),
            size = Size(size.width - w, size.height - w),
            cornerRadius = CornerRadius(cornerRadius.toPx(), cornerRadius.toPx()),
            style = Stroke(width = w, pathEffect = PathEffect.dashPathEffect(floatArrayOf(16f, 12f), 0f))
        )
    }
