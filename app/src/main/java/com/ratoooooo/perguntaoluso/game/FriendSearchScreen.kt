package com.ratoooooo.perguntaoluso.game

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material3.Icon
import androidx.compose.ui.Alignment
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.Check
import androidx.compose.material.icons.rounded.PersonAdd
import androidx.compose.material.icons.rounded.Schedule
import androidx.compose.material.icons.rounded.Search
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import com.ratoooooo.perguntaoluso.data.FriendsState
import com.ratoooooo.perguntaoluso.data.Profile
import com.ratoooooo.perguntaoluso.ui.theme.Cream
import com.ratoooooo.perguntaoluso.ui.theme.Gold
import com.ratoooooo.perguntaoluso.ui.theme.Ink
import com.ratoooooo.perguntaoluso.ui.theme.Lavender
import com.ratoooooo.perguntaoluso.ui.theme.Neutral
import com.ratoooooo.perguntaoluso.ui.theme.StickerButton
import com.ratoooooo.perguntaoluso.ui.theme.StickerTextField
import com.ratoooooo.perguntaoluso.ui.theme.Teal

@Composable
fun FriendSearchScreen(
    query: String,
    results: List<Profile>,
    searching: Boolean,
    searchDone: Boolean,
    friends: FriendsState,
    onQueryChange: (String) -> Unit,
    onSearch: () -> Unit,
    onAdd: (Profile) -> Unit,
    onBack: () -> Unit
) {
    Column(Modifier.fillMaxSize().background(Cream).padding(horizontal = 24.dp).padding(top = 24.dp, bottom = 20.dp)) {
        ScreenHeader(title = "Procurar jogadores", onBack = onBack)
        Spacer(Modifier.size(18.dp))

        StickerTextField(
            value = query, onValueChange = onQueryChange,
            placeholder = "Nome do jogador", icon = Icons.Rounded.Search,
            modifier = Modifier.fillMaxWidth()
        )
        Spacer(Modifier.size(12.dp))
        StickerButton("PESQUISAR", Icons.Rounded.Search, onSearch, fillColor = Gold, modifier = Modifier.fillMaxWidth())
        Spacer(Modifier.size(20.dp))

        when {
            searching -> Text("A procurar...", style = MaterialTheme.typography.bodyLarge, color = Ink)
            searchDone && results.isEmpty() ->
                Text("Nenhum jogador encontrado com esse nome.", style = MaterialTheme.typography.bodyLarge, color = Ink)
            else -> LazyColumn(verticalArrangement = Arrangement.spacedBy(10.dp)) {
                items(results, key = { it.uid }) { p -> ResultRow(p, friends, onAdd) }
            }
        }
    }
}

/** Indicador de estado: sem sombra nem contorno, para não parecer um botão. */
@Composable
private fun StatusIcon(icon: ImageVector, desc: String, color: Color) {
    Box(
        Modifier.size(36.dp).clip(CircleShape).background(color),
        contentAlignment = Alignment.Center
    ) {
        Icon(icon, contentDescription = desc, tint = Ink, modifier = Modifier.size(18.dp))
    }
}

@Composable
private fun ResultRow(p: Profile, friends: FriendsState, onAdd: (Profile) -> Unit) {
    val isFriend = friends.lista.any { it.uid == p.uid }
    val sent = friends.enviados.any { it.uid == p.uid }
    val received = friends.recebidos.any { it.uid == p.uid }

    PlayerRow(
        nome = p.nomeVisivel,
        profile = p,
        fill = if (isFriend) Lavender else Cream,
        subtitleOverride = when {
            isFriend -> "Já são amigos"
            sent -> "Pedido enviado"
            received -> "Enviou-te um pedido"
            else -> null
        }
    ) {
        // Só a linha acionável tem botão. Os outros estados são etiquetas — antes eram
        // círculos com onClick vazio, que pareciam botões e não faziam nada.
        when {
            isFriend -> StatusIcon(Icons.Rounded.Check, "Já é amigo", Teal)
            sent -> StatusIcon(Icons.Rounded.Schedule, "Pedido pendente", Neutral)
            received -> Text("Vê os pedidos", style = MaterialTheme.typography.labelLarge, color = Ink)
            else -> ActionCircle(Icons.Rounded.PersonAdd, "Adicionar", Gold) { onAdd(p) }
        }
    }
}
