package com.starforge.app.ui.theme

import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.Category
import androidx.compose.material.icons.rounded.Groups
import androidx.compose.material.icons.rounded.HistoryEdu
import androidx.compose.material.icons.rounded.Lightbulb
import androidx.compose.material.icons.rounded.Public
import androidx.compose.material.icons.rounded.SportsSoccer
import androidx.compose.ui.graphics.vector.ImageVector

/** One icon per visible category, reused on the category picker and in-game header. */
fun iconForCategory(name: String): ImageVector = when (name) {
    "Cultura Geral" -> Icons.Rounded.Lightbulb
    "Desporto" -> Icons.Rounded.SportsSoccer
    "Gentílicos" -> Icons.Rounded.Groups
    "Geografia" -> Icons.Rounded.Public
    "História" -> Icons.Rounded.HistoryEdu
    else -> Icons.Rounded.Category
}
