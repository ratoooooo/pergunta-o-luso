package com.ratoooooo.perguntaoluso.game

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.CheckCircle
import androidx.compose.material.icons.rounded.Lock
import androidx.compose.material.icons.rounded.Mail
import androidx.compose.material.icons.rounded.Person
import androidx.compose.material.icons.rounded.PersonAdd
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.dp
import com.ratoooooo.perguntaoluso.ui.theme.Coral
import com.ratoooooo.perguntaoluso.ui.theme.Cream
import com.ratoooooo.perguntaoluso.ui.theme.Ink
import com.ratoooooo.perguntaoluso.ui.theme.StickerButton
import com.ratoooooo.perguntaoluso.ui.theme.StickerTextField
import com.ratoooooo.perguntaoluso.ui.theme.Teal

@Composable
fun RegisterScreen(
    isAnonymous: Boolean,
    authLoading: Boolean,
    authError: String?,
    onRegister: (nome: String, email: String, password: String, confirm: String) -> Unit,
    onBack: () -> Unit
) {
    var nome by remember { mutableStateOf("") }
    var email by remember { mutableStateOf("") }
    var password by remember { mutableStateOf("") }
    var confirm by remember { mutableStateOf("") }

    Column(
        modifier = Modifier
            .fillMaxSize()
            .background(Cream)
            .verticalScroll(rememberScrollState())
            .padding(24.dp)
    ) {
        ScreenHeader(
            title = "Cria a tua conta",
            subtitle = if (isAnonymous) "Guarda o progresso que já fizeste!" else "Junta-te à batalha do conhecimento!",
            onBack = onBack
        )

        Spacer(Modifier.size(24.dp))

        StickerTextField(
            value = nome,
            onValueChange = { nome = it },
            placeholder = "Nome de utilizador",
            icon = Icons.Rounded.Person,
            modifier = Modifier.fillMaxWidth()
        )
        Spacer(Modifier.size(14.dp))
        StickerTextField(
            value = email,
            onValueChange = { email = it },
            placeholder = "E-mail",
            icon = Icons.Rounded.Mail,
            keyboardType = KeyboardType.Email,
            modifier = Modifier.fillMaxWidth()
        )
        Spacer(Modifier.size(14.dp))
        StickerTextField(
            value = password,
            onValueChange = { password = it },
            placeholder = "Palavra-passe",
            icon = Icons.Rounded.Lock,
            isPassword = true,
            keyboardType = KeyboardType.Password,
            modifier = Modifier.fillMaxWidth()
        )
        Spacer(Modifier.size(14.dp))
        StickerTextField(
            value = confirm,
            onValueChange = { confirm = it },
            placeholder = "Confirmar palavra-passe",
            icon = Icons.Rounded.Lock,
            isPassword = true,
            keyboardType = KeyboardType.Password,
            modifier = Modifier.fillMaxWidth()
        )

        Spacer(Modifier.size(14.dp))
        val ok = password.length >= 8
        Row(verticalAlignment = Alignment.CenterVertically) {
            Icon(
                imageVector = Icons.Rounded.CheckCircle,
                contentDescription = null,
                tint = if (ok) Teal else Ink.copy(alpha = 0.35f),
                modifier = Modifier.size(20.dp)
            )
            Spacer(Modifier.size(8.dp))
            Text(
                text = "Pelo menos 8 caracteres",
                style = MaterialTheme.typography.bodyLarge,
                color = Ink
            )
        }

        if (authError != null) {
            Spacer(Modifier.size(14.dp))
            Text(text = authError, style = MaterialTheme.typography.bodyLarge, color = Coral)
        }

        Spacer(Modifier.size(28.dp))

        StickerButton(
            text = if (authLoading) "A REGISTAR..." else "REGISTAR",
            icon = Icons.Rounded.PersonAdd,
            onClick = { if (!authLoading) onRegister(nome, email, password, confirm) },
            modifier = Modifier.fillMaxWidth()
        )
        Spacer(Modifier.size(24.dp))
    }
}
