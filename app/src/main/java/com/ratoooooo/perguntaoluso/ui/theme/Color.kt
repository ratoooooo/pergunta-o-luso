package com.ratoooooo.perguntaoluso.ui.theme

import androidx.compose.ui.graphics.Color

val Cream = Color(0xFFEAE6DD)
val Lavender = Color(0xFFF6F1FB)
val Ink = Color(0xFF1A1523)
val Purple = Color(0xFF6C3CE0)
val Gold = Color(0xFFFFC93C)
val Coral = Color(0xFFFF6B5B)
val Teal = Color(0xFF2FBF9F)
/** Muted tone for answered options that are neither the correct nor the picked one. */
val Neutral = Color(0xFFC9BEDD)
/** História category colour — a true royal blue, kept clearly distinct from Teal (Desporto). */
val Azul = Color(0xFF3D6EE8)
val AnswerPalette = listOf(Purple, Coral, Teal, Gold)

/** Fixed, distinct colour per visible category — no cycling. */
private val CategoryColors: Map<String, Color> = mapOf(
    "Cultura Geral" to Purple,
    "Desporto" to Teal,
    "Gentílicos" to Coral,
    "Geografia" to Gold,
    "História" to Azul
)

private val CategoryFallback = listOf(Purple, Teal, Coral, Gold, Ink)

fun colorForCategory(name: String): Color =
    CategoryColors[name] ?: CategoryFallback[(name.hashCode() and 0x7fffffff) % CategoryFallback.size]

private val LightBackgrounds = setOf(Gold, Lavender, Cream, Neutral)

fun textColorFor(background: Color): Color = if (background in LightBackgrounds) Ink else Cream
