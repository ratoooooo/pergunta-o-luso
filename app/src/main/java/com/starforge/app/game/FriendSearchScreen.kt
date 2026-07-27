package com.starforge.app.game

import androidx.compose.foundation.background
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
import com.starforge.app.data.FriendsState
import com.starforge.app.data.Profile
import com.starforge.app.ui.theme.Cream
import com.starforge.app.ui.theme.Gold
import com.starforge.app.ui.theme.Ink
import com.starforge.app.ui.theme.Lavender
import com.starforge.app.ui.theme.Neutral
import com.starforge.app.ui.theme.StickerButton
import com.starforge.app.ui.theme.StickerTextField
import com.starforge.app.ui.theme.Teal

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
        ScreenHeader(title = "Procurar jogadores", subtitle = "Pesquisa pelo nome", onBack = onBack)
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
        when {
            isFriend -> ActionCircle(Icons.Rounded.Check, "Já é amigo", Teal) { }
            sent -> ActionCircle(Icons.Rounded.Schedule, "Pedido pendente", Neutral) { }
            received -> Text("Vê os pedidos", style = MaterialTheme.typography.labelLarge, color = Ink)
            else -> ActionCircle(Icons.Rounded.PersonAdd, "Adicionar", Gold) { onAdd(p) }
        }
    }
}
