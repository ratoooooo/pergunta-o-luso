package com.ratoooooo.perguntaoluso.ui.theme

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.Visibility
import androidx.compose.material.icons.rounded.VisibilityOff
import androidx.compose.material3.Icon
import androidx.compose.material3.LocalTextStyle
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.text.input.VisualTransformation
import androidx.compose.ui.unit.dp

@Composable
fun StickerTextField(
    value: String,
    onValueChange: (String) -> Unit,
    placeholder: String,
    /** Ícone à esquerda. `null` para campos onde já existe outro identificador ao lado
     *  (ex.: as opções de resposta, marcadas por um emblema A/B/C/D). */
    icon: ImageVector? = null,
    modifier: Modifier = Modifier,
    isPassword: Boolean = false,
    keyboardType: KeyboardType = KeyboardType.Text
) {
    // Olho para revelar a palavra-passe (mockup, ecrãs 16/17). Além de ser o esperado num
    // campo destes, resolve um problema real já registado no GAME_DESIGN.md: o teclado do
    // emulador engoliu um caractere e o registo falhou com "as palavras-passe não coincidem"
    // sem que se conseguisse ver porquê.
    var revelada by remember { mutableStateOf(false) }
    Row(
        modifier = modifier
            .stickerBlock(fillColor = Lavender, cornerRadius = 20.dp, shadowOffset = 4.dp)
            .padding(horizontal = 18.dp, vertical = 16.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        if (icon != null) {
            Icon(imageVector = icon, contentDescription = null, tint = Purple, modifier = Modifier.size(22.dp))
            Spacer(Modifier.size(12.dp))
        }
        BasicTextField(
            value = value,
            onValueChange = onValueChange,
            singleLine = true,
            textStyle = LocalTextStyle.current.merge(MaterialTheme.typography.bodyLarge).merge(
                androidx.compose.ui.text.TextStyle(color = Ink)
            ),
            cursorBrush = SolidColor(Ink),
            visualTransformation = if (isPassword && !revelada) PasswordVisualTransformation() else VisualTransformation.None,
            keyboardOptions = KeyboardOptions(keyboardType = keyboardType),
            modifier = Modifier.weight(1f),
            decorationBox = { inner ->
                if (value.isEmpty()) {
                    Text(
                        text = placeholder,
                        style = MaterialTheme.typography.bodyLarge,
                        color = Ink.copy(alpha = 0.45f)
                    )
                }
                inner()
            }
        )
        if (isPassword) {
            Spacer(Modifier.size(10.dp))
            Icon(
                imageVector = if (revelada) Icons.Rounded.VisibilityOff else Icons.Rounded.Visibility,
                contentDescription = if (revelada) "Esconder palavra-passe" else "Mostrar palavra-passe",
                tint = Ink,
                modifier = Modifier.size(22.dp).clickable { revelada = !revelada }
            )
        }
    }
}
