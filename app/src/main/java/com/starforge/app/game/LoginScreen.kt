package com.starforge.app.game

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.Login
import androidx.compose.material.icons.rounded.Lock
import androidx.compose.material.icons.rounded.Mail
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
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import com.starforge.app.ui.theme.Coral
import com.starforge.app.ui.theme.Cream
import com.starforge.app.ui.theme.Ink
import com.starforge.app.ui.theme.StickerButton
import com.starforge.app.ui.theme.StickerTextField

@Composable
fun LoginScreen(
    authLoading: Boolean,
    authError: String?,
    onLogin: (String, String) -> Unit,
    onGoToRegister: () -> Unit,
    onContinueAnon: () -> Unit,
    onBack: () -> Unit
) {
    var email by remember { mutableStateOf("") }
    var password by remember { mutableStateOf("") }

    Column(
        modifier = Modifier
            .fillMaxSize()
            .background(Cream)
            .padding(24.dp)
    ) {
        ScreenHeader(title = "Entrar", subtitle = "Continua a tua batalha de conhecimento.", onBack = onBack)

        Spacer(Modifier.size(28.dp))

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

        if (authError != null) {
            Spacer(Modifier.size(14.dp))
            Text(text = authError, style = MaterialTheme.typography.bodyLarge, color = Coral)
        }

        Spacer(Modifier.size(28.dp))

        StickerButton(
            text = if (authLoading) "A ENTRAR..." else "ENTRAR",
            icon = Icons.Rounded.Login,
            onClick = { if (!authLoading) onLogin(email, password) },
            modifier = Modifier.fillMaxWidth()
        )

        Spacer(Modifier.size(20.dp))

        Text(
            text = "Ainda não tens conta? Criar conta",
            style = MaterialTheme.typography.labelLarge,
            color = Ink,
            textAlign = TextAlign.Center,
            modifier = Modifier.fillMaxWidth().clickable(enabled = !authLoading) { onGoToRegister() }
        )
        Spacer(Modifier.size(16.dp))
        Text(
            text = "Entrar sem conta",
            style = MaterialTheme.typography.bodyLarge,
            color = Ink,
            textAlign = TextAlign.Center,
            modifier = Modifier.fillMaxWidth().clickable(enabled = !authLoading) { onContinueAnon() }
        )
    }
}
