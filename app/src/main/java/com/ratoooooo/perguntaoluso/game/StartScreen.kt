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
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.Casino
import androidx.compose.material.icons.rounded.Groups
import androidx.compose.material.icons.rounded.History
import androidx.compose.material.icons.rounded.Login
import androidx.compose.material.icons.rounded.PlayArrow
import androidx.compose.material.icons.rounded.Whatshot
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.sp
import androidx.compose.ui.draw.clip
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.draw.scale
import com.ratoooooo.perguntaoluso.data.Profile
import com.ratoooooo.perguntaoluso.data.StreakDiario
import com.ratoooooo.perguntaoluso.data.UserInfo
import com.ratoooooo.perguntaoluso.game.avatar.AvatarView
import com.ratoooooo.perguntaoluso.ui.FeatureFlags
import com.ratoooooo.perguntaoluso.ui.theme.Coral
import com.ratoooooo.perguntaoluso.ui.theme.Cream
import com.ratoooooo.perguntaoluso.ui.theme.Gold
import com.ratoooooo.perguntaoluso.ui.theme.Ink
import com.ratoooooo.perguntaoluso.ui.theme.Lavender
import com.ratoooooo.perguntaoluso.ui.theme.LevelBadge
import com.ratoooooo.perguntaoluso.ui.theme.NavTab
import com.ratoooooo.perguntaoluso.ui.theme.Purple
import com.ratoooooo.perguntaoluso.ui.theme.StickerButton
import com.ratoooooo.perguntaoluso.ui.theme.Teal
import com.ratoooooo.perguntaoluso.ui.theme.rememberPulse
import com.ratoooooo.perguntaoluso.ui.theme.XpBar
import com.ratoooooo.perguntaoluso.ui.theme.stickerBlock
import com.ratoooooo.perguntaoluso.ui.theme.textColorFor

@Composable
fun StartScreen(
    userInfo: UserInfo?,
    profile: Profile?,
    playingNow: Int,
    onPlayClick: () -> Unit,
    onCommunityClick: () -> Unit,
    onRankingClick: () -> Unit,
    onHistoryClick: () -> Unit,
    onProfileClick: () -> Unit,
    onFriendsClick: () -> Unit,
    onLoginClick: () -> Unit
) {
    val isRegistered = userInfo != null && !userInfo.isAnonymous

    MainScaffold(
        active = NavTab.HOME,
        onHome = {},
        onRanking = onRankingClick,
        onFriends = onFriendsClick,
        onProfile = onProfileClick,
        scrollable = true
    ) {
        Column(
            modifier = Modifier.fillMaxSize(),
            horizontalAlignment = Alignment.CenterHorizontally
        ) {
            Spacer(Modifier.size(8.dp))
            ProfileCard(profile = profile, onClick = onProfileClick)

            Spacer(Modifier.size(12.dp))
            StreakRow(profile = profile)
            PlayingNowChip(count = playingNow)

            Spacer(Modifier.size(14.dp))

            Text("Pergunta ó Luso", style = MaterialTheme.typography.headlineLarge, color = Ink, textAlign = TextAlign.Center)
            Spacer(Modifier.size(2.dp))
            Text("Quanto sabes sobre Portugal e o mundo?", style = MaterialTheme.typography.bodyLarge, color = Ink, textAlign = TextAlign.Center)

            Spacer(Modifier.size(16.dp))

            StickerButton("JOGAR", Icons.Rounded.PlayArrow, onPlayClick, fillColor = Gold, modifier = Modifier.fillMaxWidth())
            Spacer(Modifier.size(10.dp))
            // Escondido por `FeatureFlags.QUIZZES_COMUNIDADE_VISIVEIS` — este é o único ponto de
            // entrada na navegação normal, por isso apagá-lo torna todo o ramo inalcançável sem
            // deixar botão sem destino em lado nenhum. Nada foi removido: pôr a flag a `true`
            // devolve tudo.
            if (FeatureFlags.QUIZZES_COMUNIDADE_VISIVEIS) {
                StickerButton("QUIZZES DA COMUNIDADE", Icons.Rounded.Groups, onCommunityClick, fillColor = Teal, modifier = Modifier.fillMaxWidth())
                Spacer(Modifier.size(10.dp))
            }
            StickerButton("HISTÓRICO", Icons.Rounded.History, onHistoryClick, fillColor = Purple, modifier = Modifier.fillMaxWidth())
            if (!isRegistered) {
                Spacer(Modifier.size(14.dp))
                StickerButton("LOGIN / CRIAR CONTA", Icons.Rounded.Login, onLoginClick, fillColor = Teal, modifier = Modifier.fillMaxWidth())
            }
        }
    }
}

@Composable
private fun ProfileCard(profile: Profile?, onClick: () -> Unit) {
    val nome = profile?.nomeVisivel ?: "Convidado"
    val iniciais = profile?.iniciais ?: "?"
    val pontos = profile?.pontos ?: 0
    val taxa = ((profile?.taxaAcertos ?: 0.0) * 100).toInt()
    val jogos = profile?.jogos ?: 0

    Row(
        modifier = Modifier
            .fillMaxWidth()
            .stickerBlock(fillColor = Lavender, cornerRadius = 28.dp, shadowOffset = 6.dp)
            .clickable(onClick = onClick)
            .padding(18.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        AvatarView(avatarId = profile?.avatar, iniciais = iniciais, modifier = Modifier.size(64.dp))
        Spacer(Modifier.size(16.dp))
        Column(modifier = Modifier.fillMaxWidth()) {
            Row(verticalAlignment = Alignment.CenterVertically, modifier = Modifier.fillMaxWidth()) {
                Column(Modifier.weight(1f)) {
                    Text(text = "Olá,", style = MaterialTheme.typography.bodyLarge, color = Ink)
                    Text(text = nome, style = MaterialTheme.typography.titleLarge, color = Ink)
                }
                LevelBadge(nivel = profile?.nivel ?: 1, size = 40.dp)
            }
            Spacer(Modifier.size(10.dp))
            // Fase 29: os três chips repartem a largura disponível em vez de a exigirem. Sem os
            // `weight`, num ecrã estreito (ou com letra grande do sistema) o terceiro era
            // empurrado para fora e simplesmente desaparecia — o jogador deixava de ver "jogos".
            Row(
                horizontalArrangement = Arrangement.spacedBy(10.dp),
                modifier = Modifier.fillMaxWidth()
            ) {
                StatChip(label = "pontos", value = "$pontos", color = Gold, modifier = Modifier.weight(1f))
                StatChip(label = "acertos", value = "$taxa%", color = Teal, modifier = Modifier.weight(1f))
                StatChip(label = "jogos", value = "$jogos", color = Coral, modifier = Modifier.weight(1f))
            }
            Spacer(Modifier.size(12.dp))
            XpBar(
                estado = (profile ?: Profile()).progressao,
                levelLabel = false,
                patente = (profile ?: Profile()).patente.titulo
            )
        }
    }
}

@Composable
private fun PlayingNowChip(count: Int) {
    Row(
        modifier = Modifier
            .stickerBlock(fillColor = Teal, cornerRadius = 16.dp, shadowOffset = 4.dp, borderWidth = 2.dp)
            .padding(horizontal = 16.dp, vertical = 8.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Box(Modifier.size(9.dp).clip(CircleShape).background(Cream))
        Spacer(Modifier.size(8.dp))
        Text(
            text = "${count.coerceAtLeast(0)} A JOGAR AGORA",
            style = MaterialTheme.typography.labelLarge,
            color = textColorFor(Teal)
        )
    }
}

@Composable
private fun StatChip(
    label: String,
    value: String,
    color: androidx.compose.ui.graphics.Color,
    modifier: Modifier = Modifier
) {
    Column(
        modifier = modifier
            .stickerBlock(fillColor = color, cornerRadius = 14.dp, shadowOffset = 3.dp, borderWidth = 2.dp)
            .padding(horizontal = 8.dp, vertical = 8.dp),
        horizontalAlignment = Alignment.CenterHorizontally
    ) {
        Text(
            text = value,
            style = MaterialTheme.typography.labelLarge,
            color = textColorFor(color),
            maxLines = 1,
            overflow = TextOverflow.Ellipsis
        )
        // A legenda é rótulo de chip, não texto corrido: a bodyLarge (16 sp) "pontos" e
        // "acertos" não cabiam na largura de um terço do cartão e partiam em duas linhas,
        // desalinhando o chip do meio em relação a "jogos". 13 sp cabe com folga e mantém a
        // hierarquia — o número continua a ser o que salta à vista.
        Text(
            text = label,
            style = MaterialTheme.typography.bodyLarge.copy(fontSize = 13.sp, lineHeight = 16.sp),
            color = textColorFor(color),
            maxLines = 1,
            overflow = TextOverflow.Ellipsis
        )
    }
}

/**
 * Sequência de **dias** seguidos, com a nota de protecção por baixo quando houve uma gasta.
 *
 * Vocabulário deliberadamente **igual** ao da sequência dentro da partida — chama a partir de 2,
 * Coral a partir de 5 — porque é a mesma ideia ("não quebres isto") e o jogador não tem de
 * aprender dois sinais. O que os separa é o rótulo: aqui diz sempre "dias seguidos", e a
 * sequência de respostas só aparece durante uma pergunta, com um número solto ao lado dos pontos.
 * Nunca partilham ecrã, por isso não há como confundir qual é qual.
 *
 * Abaixo de 2 dias não se mostra nada: um "1 dia seguido" não é uma sequência, é ter jogado hoje,
 * e ocuparia espaço no ecrã mais cheio da app a dizer nada.
 */
@Composable
private fun StreakRow(profile: Profile?) {
    val estado = (profile ?: return).streak
    val dias = estado.diasSeguidos
    val protegido = StreakDiario.protecaoRecente(estado)
    if (dias < 2 && !protegido) return

    Column(horizontalAlignment = Alignment.CenterHorizontally) {
        if (dias >= 2) {
            val forte = dias >= 5
            val cor = if (forte) Coral else Gold
            val pulso = rememberPulse(ativo = forte, min = 1f, max = 1.06f, periodoMs = 900)
            Row(
                modifier = Modifier
                    .scale(pulso)
                    .stickerBlock(fillColor = cor, cornerRadius = 16.dp, shadowOffset = 4.dp, borderWidth = 2.dp)
                    .padding(horizontal = 14.dp, vertical = 8.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                Icon(
                    Icons.Rounded.Whatshot, contentDescription = null,
                    tint = textColorFor(cor), modifier = Modifier.size(18.dp)
                )
                Spacer(Modifier.size(6.dp))
                Text(
                    "$dias DIAS SEGUIDOS",
                    style = MaterialTheme.typography.labelLarge,
                    color = textColorFor(cor)
                )
            }
        }
        if (protegido) {
            // Sem isto o jogador falta um dia, vê a sequência intacta e não percebe porquê — e
            // uma rede de segurança que ninguém vê não tranquiliza ninguém.
            Spacer(Modifier.size(6.dp))
            Text(
                "A tua sequência foi protegida pelo dia que falhaste.",
                style = MaterialTheme.typography.bodyLarge.copy(fontSize = 13.sp, lineHeight = 16.sp),
                color = Ink,
                textAlign = TextAlign.Center
            )
        }
        Spacer(Modifier.size(12.dp))
    }
}
