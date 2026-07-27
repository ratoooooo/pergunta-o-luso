package com.starforge.app.game

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.Check
import androidx.compose.material.icons.rounded.Close
import androidx.compose.material.icons.rounded.PersonSearch
import androidx.compose.material.icons.rounded.SportsKabaddi
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.starforge.app.data.FriendRef
import com.starforge.app.data.FriendsState
import com.starforge.app.data.Profile
import com.starforge.app.game.avatar.AvatarView
import com.starforge.app.ui.theme.Coral
import com.starforge.app.ui.theme.Cream
import com.starforge.app.ui.theme.Gold
import com.starforge.app.ui.theme.Ink
import com.starforge.app.ui.theme.Lavender
import com.starforge.app.ui.theme.NavTab
import com.starforge.app.ui.theme.Purple
import com.starforge.app.ui.theme.StickerButton
import com.starforge.app.ui.theme.Teal
import com.starforge.app.ui.theme.stickerBlock
import com.starforge.app.ui.theme.stickerCircle

@Composable
fun FriendsScreen(
    friends: FriendsState,
    profiles: Map<String, Profile>,
    onlineUids: Set<String>,
    desafioPara: FriendRef?,
    desafioSegundos: Int,
    desafioAviso: String?,
    onSearch: () -> Unit,
    onChallenge: (FriendRef) -> Unit,
    onCancelChallenge: () -> Unit,
    onDismissAviso: () -> Unit,
    onAccept: (FriendRef) -> Unit,
    onDecline: (FriendRef) -> Unit,
    onCancel: (FriendRef) -> Unit,
    onHome: () -> Unit,
    onRanking: () -> Unit,
    onProfile: () -> Unit
) {
    MainScaffold(active = NavTab.FRIENDS, onHome = onHome, onRanking = onRanking, onFriends = {}, onProfile = onProfile) {
        ScreenHeader(title = "Amigos")
        Spacer(Modifier.size(14.dp))
        StickerButton("PROCURAR JOGADORES", Icons.Rounded.PersonSearch, onSearch, fillColor = Gold, modifier = Modifier.fillMaxWidth())
        Spacer(Modifier.size(14.dp))

        if (desafioPara != null) {
            Row(
                Modifier.fillMaxWidth().stickerBlock(fillColor = Purple, cornerRadius = 18.dp, shadowOffset = 4.dp)
                    .padding(horizontal = 14.dp, vertical = 12.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                Column(Modifier.weight(1f)) {
                    Text("À espera de ${desafioPara.nome}...", style = MaterialTheme.typography.bodyLarge.copy(fontWeight = FontWeight.Bold), color = Cream, maxLines = 1)
                    Text("O desafio expira em ${desafioSegundos}s", style = MaterialTheme.typography.labelLarge, color = Cream)
                }
                Spacer(Modifier.size(8.dp))
                ActionCircle(Icons.Rounded.Close, "Cancelar desafio", Coral, onCancelChallenge)
            }
            Spacer(Modifier.size(14.dp))
        }

        if (desafioAviso != null) {
            Row(
                Modifier.fillMaxWidth().stickerBlock(fillColor = Gold, cornerRadius = 18.dp, shadowOffset = 4.dp)
                    .clickable(onClick = onDismissAviso).padding(horizontal = 14.dp, vertical = 12.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                Text(desafioAviso, style = MaterialTheme.typography.bodyLarge, color = Ink, modifier = Modifier.weight(1f))
                Icon(Icons.Rounded.Close, contentDescription = "Fechar", tint = Ink, modifier = Modifier.size(20.dp))
            }
            Spacer(Modifier.size(14.dp))
        }

        // As três zonas passaram a separadores: só uma lista visível de cada vez. Empilhá-las
        // enchia o ecrã de títulos e de "vazios" mesmo quando não havia nada pendente.
        var aba by rememberSaveable { mutableIntStateOf(0) }
        Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            FriendTab("Amigos", friends.lista.size, aba == 0, Modifier.weight(1f)) { aba = 0 }
            FriendTab("Recebidos", friends.recebidos.size, aba == 1, Modifier.weight(1f), destaque = friends.recebidos.isNotEmpty()) { aba = 1 }
            FriendTab("Enviados", friends.enviados.size, aba == 2, Modifier.weight(1f)) { aba = 2 }
        }
        Spacer(Modifier.size(16.dp))

        LazyColumn(verticalArrangement = Arrangement.spacedBy(10.dp)) {
            when (aba) {
                0 -> if (friends.lista.isEmpty()) {
                    item { EmptyNote("Ainda não tens amigos. Procura jogadores pelo nome.") }
                } else items(friends.lista, key = { "f-${it.uid}" }) { f ->
                    val online = f.uid in onlineUids
                    FriendRow(
                        f, profiles[f.uid], fill = Lavender,
                        subtitleOverride = if (online) null else "Offline"
                    ) {
                        // Desafiar exige o amigo com a app aberta — o convite só é entregue
                        // enquanto ele está à escuta.
                        if (online && desafioPara == null) {
                            ActionCircle(Icons.Rounded.SportsKabaddi, "Desafiar", Gold) { onChallenge(f) }
                        }
                    }
                }

                1 -> if (friends.recebidos.isEmpty()) {
                    item { EmptyNote("Sem pedidos pendentes.") }
                } else items(friends.recebidos, key = { "r-${it.uid}" }) { f ->
                    FriendRow(f, profiles[f.uid], fill = Lavender) {
                        ActionCircle(Icons.Rounded.Check, "Aceitar", Teal) { onAccept(f) }
                        Spacer(Modifier.size(8.dp))
                        ActionCircle(Icons.Rounded.Close, "Recusar", Coral) { onDecline(f) }
                    }
                }

                else -> if (friends.enviados.isEmpty()) {
                    item { EmptyNote("Não enviaste pedidos.") }
                } else items(friends.enviados, key = { "s-${it.uid}" }) { f ->
                    FriendRow(f, profiles[f.uid], fill = Cream, subtitleOverride = "À espera de resposta") {
                        ActionCircle(Icons.Rounded.Close, "Cancelar", Coral) { onCancel(f) }
                    }
                }
            }
            item { Spacer(Modifier.size(12.dp)) }
        }
    }
}

/** Separador das três zonas de Amigos, com contador e ponto de aviso quando há pendentes. */
@Composable
private fun FriendTab(
    label: String,
    count: Int,
    selected: Boolean,
    modifier: Modifier = Modifier,
    destaque: Boolean = false,
    onClick: () -> Unit
) {
    // Rótulo em cima, contador por baixo: com os três nomes numa só linha o contador
    // era cortado nos separadores mais estreitos.
    Column(
        modifier
            .stickerBlock(
                fillColor = if (selected) Purple else Lavender,
                cornerRadius = 16.dp, shadowOffset = 4.dp, borderWidth = 2.dp
            )
            .clickable(onClick = onClick)
            .padding(vertical = 8.dp, horizontal = 4.dp),
        horizontalAlignment = Alignment.CenterHorizontally
    ) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            if (destaque && !selected) {
                Box(Modifier.size(8.dp).clip(CircleShape).background(Coral))
                Spacer(Modifier.size(5.dp))
            }
            Text(
                label,
                style = MaterialTheme.typography.bodyLarge,
                color = if (selected) Cream else Ink,
                maxLines = 1
            )
        }
        Text(
            "$count",
            style = MaterialTheme.typography.labelLarge,
            color = if (selected) Cream else Ink
        )
    }
}

@Composable
internal fun SectionTitle(text: String) {
    Text(text, style = MaterialTheme.typography.titleLarge, color = Ink, modifier = Modifier.padding(bottom = 4.dp))
}

@Composable
private fun EmptyNote(text: String) {
    Text(text, style = MaterialTheme.typography.bodyLarge, color = Ink.copy(alpha = 0.6f), modifier = Modifier.padding(bottom = 6.dp))
}

/** Avatar + name + level row, with optional trailing actions. */
@Composable
internal fun FriendRow(
    f: FriendRef,
    profile: Profile?,
    fill: Color,
    subtitleOverride: String? = null,
    actions: @Composable () -> Unit = {}
) {
    val nome = profile?.nomeVisivel?.takeIf { profile.temNome } ?: f.nome
    PlayerRow(nome = nome, profile = profile, fill = fill, subtitleOverride = subtitleOverride, actions = actions)
}

@Composable
internal fun PlayerRow(
    nome: String,
    profile: Profile?,
    fill: Color,
    subtitleOverride: String? = null,
    actions: @Composable () -> Unit = {}
) {
    Row(
        Modifier.fillMaxWidth()
            .stickerBlock(fillColor = fill, cornerRadius = 18.dp, shadowOffset = 4.dp)
            .padding(horizontal = 14.dp, vertical = 12.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        AvatarView(
            avatarId = profile?.avatar,
            iniciais = profile?.iniciais ?: nome.take(1).uppercase(),
            modifier = Modifier.size(46.dp),
            shadowOffset = 3.dp
        )
        Spacer(Modifier.size(12.dp))
        Column(Modifier.weight(1f)) {
            Text(nome, style = MaterialTheme.typography.bodyLarge.copy(fontWeight = FontWeight.Bold), color = Ink, maxLines = 1)
            Text(
                subtitleOverride ?: "Nível ${profile?.nivel ?: 1}",
                style = MaterialTheme.typography.labelLarge, color = Ink.copy(alpha = 0.7f), maxLines = 1
            )
        }
        Spacer(Modifier.size(8.dp))
        actions()
    }
}

@Composable
internal fun ActionCircle(icon: ImageVector, desc: String, color: Color, onClick: () -> Unit) {
    Box(
        Modifier.size(40.dp).stickerCircle(fillColor = color, shadowOffset = 3.dp, borderWidth = 2.dp).clickable(onClick = onClick),
        contentAlignment = Alignment.Center
    ) {
        Icon(icon, contentDescription = desc, tint = Ink, modifier = Modifier.size(20.dp))
    }
}
