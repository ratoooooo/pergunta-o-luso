package com.ratoooooo.perguntaoluso.game

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
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
import androidx.compose.material.icons.rounded.Check
import androidx.compose.material.icons.rounded.Close
import androidx.compose.material.icons.rounded.DeleteForever
import androidx.compose.material.icons.rounded.Edit
import androidx.compose.material.icons.rounded.EmojiEvents
import androidx.compose.material.icons.rounded.Lock
import androidx.compose.material.icons.rounded.Logout
import androidx.compose.material3.HorizontalDivider
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
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.dp
import com.ratoooooo.perguntaoluso.data.ModeStats
import com.ratoooooo.perguntaoluso.data.Profile
import com.ratoooooo.perguntaoluso.game.avatar.AvatarView
import com.ratoooooo.perguntaoluso.ui.theme.StickerButton
import com.ratoooooo.perguntaoluso.ui.theme.Coral
import com.ratoooooo.perguntaoluso.ui.theme.Cream
import com.ratoooooo.perguntaoluso.ui.theme.Gold
import com.ratoooooo.perguntaoluso.ui.theme.Ink
import com.ratoooooo.perguntaoluso.ui.theme.Lavender
import com.ratoooooo.perguntaoluso.ui.theme.LevelBadge
import com.ratoooooo.perguntaoluso.ui.theme.Neutral
import com.ratoooooo.perguntaoluso.ui.theme.SegmentedTabs
import com.ratoooooo.perguntaoluso.ui.theme.StickerDialog
import com.ratoooooo.perguntaoluso.ui.theme.XpBar
import com.ratoooooo.perguntaoluso.ui.theme.StickerTextField
import com.ratoooooo.perguntaoluso.ui.theme.Teal
import com.ratoooooo.perguntaoluso.ui.theme.stickerBlock
import com.ratoooooo.perguntaoluso.ui.theme.stickerCircle
import com.ratoooooo.perguntaoluso.ui.theme.textColorFor

@Composable
fun ProfileScreen(
    profile: Profile?,
    onSaveNome: (String) -> Unit,
    onBack: () -> Unit,
    onHome: () -> Unit,
    onRanking: () -> Unit,
    onFriends: () -> Unit,
    onAvatarClick: () -> Unit,
    onAchievementsClick: () -> Unit,
    onSignOut: () -> Unit,
    isRegistered: Boolean,
    delete: DeleteAccountUi = DeleteAccountUi(),
    onOpenDelete: () -> Unit = {},
    onDismissDelete: () -> Unit = {},
    onConfirmDelete: (String?) -> Unit = {}
) {
    val p = profile ?: Profile()
    var editing by remember { mutableStateOf(false) }
    var nome by remember(p.nome) { mutableStateOf(p.nome) }
    var selectedMode by remember { mutableStateOf("classico") }

    MainScaffold(
        active = com.ratoooooo.perguntaoluso.ui.theme.NavTab.PROFILE,
        onHome = onHome, onRanking = onRanking, onFriends = onFriends, onProfile = {}
    ) {
      Column(Modifier.fillMaxSize().verticalScroll(rememberScrollState())) {
        ScreenHeader(title = "Perfil", onBack = onBack)
        Spacer(Modifier.size(18.dp))

        // Identity
        Row(verticalAlignment = Alignment.CenterVertically, modifier = Modifier.fillMaxWidth()) {
            AvatarView(
                avatarId = p.avatar, iniciais = p.iniciais,
                modifier = Modifier.size(64.dp).clickable { onAvatarClick() }
            )
            Spacer(Modifier.size(16.dp))
            Text(p.nomeVisivel, style = MaterialTheme.typography.headlineLarge, color = Ink, modifier = Modifier.weight(1f))
            Spacer(Modifier.size(12.dp))
            // Editar nome é uma ação rara e passa a botão de contorno (como no mockup,
            // ecrã 14). Era dourado, exactamente igual a CONQUISTAS logo abaixo — dois
            // "primários" no mesmo ecrã, e o mais raro dos dois era o mais visível.
            Box(
                Modifier.size(44.dp).stickerCircle(fillColor = Lavender, shadowOffset = 3.dp, borderWidth = 2.dp).clickable { editing = !editing },
                contentAlignment = Alignment.Center
            ) {
                Icon(if (editing) Icons.Rounded.Check else Icons.Rounded.Edit, contentDescription = "Editar nome", tint = Ink, modifier = Modifier.size(22.dp))
            }
        }

        if (editing) {
            Spacer(Modifier.size(12.dp))
            StickerTextField(
                value = nome, onValueChange = { nome = it },
                placeholder = "Nome de utilizador", icon = Icons.Rounded.Edit,
                modifier = Modifier.fillMaxWidth()
            )
            Spacer(Modifier.size(10.dp))
            Box(
                Modifier.fillMaxWidth().stickerBlock(fillColor = Teal, cornerRadius = 18.dp, shadowOffset = 4.dp)
                    .clickable { onSaveNome(nome); editing = false }.padding(vertical = 14.dp),
                contentAlignment = Alignment.Center
            ) {
                Text("GUARDAR NOME", style = MaterialTheme.typography.labelLarge, color = Cream)
            }
        }

        Spacer(Modifier.size(20.dp))
        Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
            LevelBadge(nivel = p.nivel, size = 48.dp)
            Spacer(Modifier.size(14.dp))
            XpBar(
                estado = p.progressao,
                modifier = Modifier.weight(1f),
                levelLabel = false,
                patente = p.patente.titulo
            )
        }

        Spacer(Modifier.size(16.dp))
        StickerButton("CONQUISTAS", Icons.Rounded.EmojiEvents, onAchievementsClick, fillColor = Gold, modifier = Modifier.fillMaxWidth())

        Spacer(Modifier.size(24.dp))
        Text("Estatísticas globais", style = MaterialTheme.typography.titleLarge, color = Ink)
        Spacer(Modifier.size(12.dp))
        // As três métricas com cor são as mesmas do cartão do Início (pontos/acertos/jogos),
        // para a cor significar sempre o mesmo. As restantes ficam neutras — são detalhe,
        // não devem gritar tanto como as principais.
        Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(10.dp)) {
            StatCell("Pontos", "${p.pontos}", Gold, Modifier.weight(1f))
            StatCell("Acertos", "${(p.taxaAcertos * 100).toInt()}%", Teal, Modifier.weight(1f))
            StatCell("Jogos", "${p.jogos}", Coral, Modifier.weight(1f))
        }
        Spacer(Modifier.size(10.dp))
        Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(10.dp)) {
            StatCell("Vitórias", "${p.vitorias}", Lavender, Modifier.weight(1f))
            StatCell("Recorde", "${p.recorde}", Lavender, Modifier.weight(1f))
            StatCell("Streak", "${p.maxStreak}", Lavender, Modifier.weight(1f))
        }

        Spacer(Modifier.size(24.dp))
        Text("Por modo", style = MaterialTheme.typography.titleLarge, color = Ink)
        Spacer(Modifier.size(12.dp))
        val modeTabs = listOf("classico" to "Clássico", "caotico" to "Caótico", "eliminatorias" to "Eliminatórias")
        val modeIndex = modeTabs.indexOfFirst { it.first == selectedMode }.coerceAtLeast(0)
        SegmentedTabs(
            labels = modeTabs.map { it.second },
            selectedIndex = modeIndex,
            onSelect = { selectedMode = modeTabs[it].first }
        )
        Spacer(Modifier.size(12.dp))
        ModeStatsCard(modeTabs[modeIndex].second, p.modos[selectedMode] ?: ModeStats())

        // Terminar sessão vive só aqui, no fim do Perfil: é raro e destrutivo, por isso
        // aparece pequeno, alinhado à direita e discreto — não compete com nenhuma ação.
        if (isRegistered) {
            Spacer(Modifier.size(20.dp))
            Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.End) {
                Row(
                    Modifier
                        .stickerBlock(fillColor = Cream, cornerRadius = 16.dp, shadowOffset = 3.dp, borderWidth = 2.dp)
                        .clickable(onClick = onSignOut)
                        .padding(horizontal = 16.dp, vertical = 10.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Icon(Icons.Rounded.Logout, contentDescription = null, tint = Coral, modifier = Modifier.size(18.dp))
                    Spacer(Modifier.size(8.dp))
                    Text("Terminar sessão", style = MaterialTheme.typography.labelLarge, color = Coral)
                }
            }
        }

        // Eliminar conta: exigido pela Play Store para qualquer app que permita criar conta.
        // Fica no fim, separado de "Terminar sessão" por uma linha e um espaço maior, e é o
        // único elemento do ecrã pintado de Coral cheio — terminar sessão é recuperável, isto
        // não é, e as duas não podem parecer a mesma classe de ação.
        Spacer(Modifier.size(28.dp))
        HorizontalDivider(color = Ink.copy(alpha = 0.12f), thickness = 2.dp)
        Spacer(Modifier.size(20.dp))
        Row(
            Modifier
                .fillMaxWidth()
                .stickerBlock(fillColor = Coral, cornerRadius = 18.dp, shadowOffset = 4.dp)
                .clickable(onClick = onOpenDelete)
                .padding(vertical = 14.dp),
            horizontalArrangement = Arrangement.Center,
            verticalAlignment = Alignment.CenterVertically
        ) {
            Icon(
                Icons.Rounded.DeleteForever,
                contentDescription = null,
                tint = textColorFor(Coral),
                modifier = Modifier.size(20.dp)
            )
            Spacer(Modifier.size(10.dp))
            Text(
                "Eliminar conta",
                style = MaterialTheme.typography.labelLarge,
                color = textColorFor(Coral)
            )
        }
        Spacer(Modifier.size(8.dp))
        Text(
            "Apaga permanentemente o teu perfil, histórico, amigos e quizzes.",
            style = MaterialTheme.typography.bodyMedium,
            color = Ink.copy(alpha = 0.6f)
        )
        Spacer(Modifier.size(16.dp))
      }
    }

    if (delete.open) {
        DeleteAccountDialog(
            state = delete,
            onDismiss = onDismissDelete,
            onConfirm = onConfirmDelete
        )
    }
}

private const val PALAVRA_CONFIRMACAO = "ELIMINAR"

/**
 * Confirmação explícita antes de uma ação irreversível. Escrever a palavra é deliberado: um
 * segundo botão "tem a certeza?" aceita-se por reflexo, escrever `ELIMINAR` obriga a ler.
 */
@Composable
private fun DeleteAccountDialog(
    state: DeleteAccountUi,
    onDismiss: () -> Unit,
    onConfirm: (String?) -> Unit
) {
    var confirmacao by remember { mutableStateOf("") }
    var password by remember { mutableStateOf("") }

    val palavraOk = confirmacao.trim().equals(PALAVRA_CONFIRMACAO, ignoreCase = true)
    val passwordOk = !state.needsPassword || password.isNotBlank()
    val podeEliminar = palavraOk && passwordOk && !state.working

    StickerDialog(onDismissRequest = { if (!state.working) onDismiss() }, fillColor = Cream) {
        Text("Eliminar conta", style = MaterialTheme.typography.titleLarge, color = Coral)
        Spacer(Modifier.size(12.dp))
        Text(
            "Esta ação é permanente e não pode ser anulada. Vamos apagar:",
            style = MaterialTheme.typography.bodyLarge,
            color = Ink
        )
        Spacer(Modifier.size(10.dp))
        listOf(
            "o teu perfil, nível e conquistas",
            "todo o teu histórico de jogos",
            "os teus amigos e convites pendentes",
            "os quizzes da comunidade que criaste"
        ).forEach { linha ->
            Text("•  $linha", style = MaterialTheme.typography.bodyLarge, color = Ink)
        }
        Spacer(Modifier.size(16.dp))
        Text(
            "Escreve $PALAVRA_CONFIRMACAO para confirmar:",
            style = MaterialTheme.typography.bodyLarge,
            color = Ink
        )
        Spacer(Modifier.size(8.dp))
        StickerTextField(
            value = confirmacao,
            onValueChange = { confirmacao = it },
            placeholder = PALAVRA_CONFIRMACAO,
            modifier = Modifier.fillMaxWidth()
        )

        if (state.needsPassword) {
            Spacer(Modifier.size(12.dp))
            StickerTextField(
                value = password,
                onValueChange = { password = it },
                placeholder = "Palavra-passe",
                icon = Icons.Rounded.Lock,
                isPassword = true,
                modifier = Modifier.fillMaxWidth()
            )
        }

        if (state.error != null) {
            Spacer(Modifier.size(10.dp))
            Text(state.error, style = MaterialTheme.typography.bodyMedium, color = Coral)
        }

        Spacer(Modifier.size(18.dp))
        StickerButton(
            text = if (state.working) "A ELIMINAR…" else "ELIMINAR DEFINITIVAMENTE",
            icon = Icons.Rounded.DeleteForever,
            onClick = { if (podeEliminar) onConfirm(password.takeIf { state.needsPassword }) },
            fillColor = if (podeEliminar) Coral else Neutral,
            modifier = Modifier.fillMaxWidth()
        )
        Spacer(Modifier.size(10.dp))
        StickerButton(
            text = "CANCELAR",
            icon = Icons.Rounded.Close,
            onClick = { if (!state.working) onDismiss() },
            fillColor = Lavender,
            modifier = Modifier.fillMaxWidth()
        )
    }
}

@Composable
private fun StatCell(label: String, value: String, color: Color, modifier: Modifier = Modifier) {
    Column(
        modifier.stickerBlock(fillColor = color, cornerRadius = 16.dp, shadowOffset = 4.dp, borderWidth = 2.dp).padding(vertical = 14.dp),
        horizontalAlignment = Alignment.CenterHorizontally
    ) {
        Text(value, style = MaterialTheme.typography.titleLarge, color = textColorFor(color))
        Text(label, style = MaterialTheme.typography.bodyLarge, color = textColorFor(color))
    }
}

@Composable
private fun ModeStatsCard(label: String, s: ModeStats) {
    Column(
        Modifier.fillMaxWidth().stickerBlock(fillColor = Lavender, cornerRadius = 20.dp, shadowOffset = 5.dp).padding(18.dp)
    ) {
        Text(label, style = MaterialTheme.typography.titleLarge, color = Ink)
        Spacer(Modifier.size(8.dp))
        Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
            MiniStat("Jogos", "${s.jogos}")
            MiniStat("Pontos", "${s.pontos}")
            MiniStat("Vitórias", "${s.vitorias}")
            MiniStat("Recorde", "${s.recorde}")
            MiniStat("Acertos", "${(s.taxaAcertos * 100).toInt()}%")
        }
    }
}

@Composable
private fun MiniStat(label: String, value: String) {
    Column(horizontalAlignment = Alignment.CenterHorizontally) {
        Text(value, style = MaterialTheme.typography.labelLarge, color = Ink)
        Text(label, style = MaterialTheme.typography.bodyLarge, color = Ink)
    }
}
