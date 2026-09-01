package com.ratoooooo.perguntaoluso.game

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.Login
import androidx.compose.material.icons.rounded.Lock
import androidx.compose.material.icons.rounded.Mail
import androidx.compose.material.icons.rounded.PersonAdd
import androidx.compose.material.icons.rounded.PlayArrow
import androidx.compose.material.icons.rounded.Psychology
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
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import com.ratoooooo.perguntaoluso.ui.theme.Coral
import com.ratoooooo.perguntaoluso.ui.theme.Cream
import com.ratoooooo.perguntaoluso.ui.theme.Ink
import com.ratoooooo.perguntaoluso.ui.theme.Lavender
import com.ratoooooo.perguntaoluso.ui.theme.Purple
import com.ratoooooo.perguntaoluso.ui.theme.StickerButton
import com.ratoooooo.perguntaoluso.ui.theme.StickerTextField
import com.ratoooooo.perguntaoluso.ui.theme.Teal
import com.ratoooooo.perguntaoluso.ui.theme.stickerBlock

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
        // Marca no topo (mockup, ecrã 16). É por aqui que muita gente vê a app pela primeira
        // vez e o ecrã não dizia em lado nenhum como se chama o jogo.
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.Center,
            verticalAlignment = Alignment.CenterVertically
        ) {
            Box(
                Modifier.size(46.dp).stickerBlock(fillColor = Purple, cornerRadius = 14.dp, shadowOffset = 4.dp),
                contentAlignment = Alignment.Center
            ) {
                Icon(Icons.Rounded.Psychology, contentDescription = null, tint = Cream, modifier = Modifier.size(28.dp))
            }
            Spacer(Modifier.size(12.dp))
            Text("Pergunta ó ", style = MaterialTheme.typography.titleLarge, color = Ink)
            Text("Luso", style = MaterialTheme.typography.titleLarge, color = Purple)
        }

        Spacer(Modifier.size(22.dp))

        ScreenHeader(title = "Entrar", onBack = onBack)

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

        Spacer(Modifier.size(12.dp))

        // Ícone diferente do ENTRAR: os dois botões tinham o mesmo, e ler dois botões
        // seguidos com a mesma seta obrigava a decifrar só pelo texto.
        StickerButton(
            text = "ENTRAR SEM CONTA",
            icon = Icons.Rounded.PlayArrow,
            onClick = { if (!authLoading) onContinueAnon() },
            fillColor = Lavender,
            modifier = Modifier.fillMaxWidth()
        )

        Spacer(Modifier.size(16.dp))

        Text(
            text = "OU",
            style = MaterialTheme.typography.labelLarge,
            color = Ink,
            textAlign = TextAlign.Center,
            modifier = Modifier.fillMaxWidth()
        )

        Spacer(Modifier.size(16.dp))

        StickerButton(
            text = "CRIAR CONTA",
            icon = Icons.Rounded.PersonAdd,
            onClick = { if (!authLoading) onGoToRegister() },
            fillColor = Teal,
            modifier = Modifier.fillMaxWidth()
        )
    }
}
