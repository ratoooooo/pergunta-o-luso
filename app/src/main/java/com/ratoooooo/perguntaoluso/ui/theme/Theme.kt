package com.ratoooooo.perguntaoluso.ui.theme

import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.runtime.Composable

private val StickerColorScheme = lightColorScheme(
    primary = Purple,
    secondary = Gold,
    tertiary = Teal,
    background = Cream,
    surface = Lavender,
    onBackground = Ink,
    onSurface = Ink,
    onPrimary = Cream,
    onSecondary = Ink
)

@Composable
fun PerguntaOLusoTheme(content: @Composable () -> Unit) {
    MaterialTheme(
        colorScheme = StickerColorScheme,
        typography = Typography,
        content = content
    )
}
