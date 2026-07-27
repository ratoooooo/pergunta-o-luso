package com.starforge.app.game

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
import androidx.compose.material.icons.rounded.History
import androidx.compose.material.icons.rounded.Login
import androidx.compose.material.icons.rounded.Logout
import androidx.compose.material.icons.rounded.PlayArrow
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import com.starforge.app.data.Profile
import com.starforge.app.data.UserInfo
import com.starforge.app.ui.theme.Coral
import com.starforge.app.ui.theme.Cream
import com.starforge.app.ui.theme.Gold
import com.starforge.app.ui.theme.Ink
import com.starforge.app.ui.theme.Lavender
import com.starforge.app.ui.theme.LevelBadge
import com.starforge.app.ui.theme.NavTab
import com.starforge.app.ui.theme.Purple
import com.starforge.app.ui.theme.StickerButton
import com.starforge.app.ui.theme.Teal
import com.starforge.app.ui.theme.XpBar
import com.starforge.app.ui.theme.stickerBlock
import com.starforge.app.ui.theme.stickerCircle
import com.starforge.app.ui.theme.textColorFor

@Composable
fun StartScreen(
    userInfo: UserInfo?,
    profile: Profile?,
    playingNow: Int,
    onPlayClick: () -> Unit,
    onRankingClick: () -> Unit,
    onHistoryClick: () -> Unit,
    onProfileClick: () -> Unit,
    onFriendsClick: () -> Unit,
    onLoginClick: () -> Unit,
    onSignOut: () -> Unit
) {
    val isRegistered = userInfo != null && !userInfo.isAnonymous

    MainScaffold(
        active = NavTab.HOME,
        onHome = {},
        onRanking = onRankingClick,
        onFriends = onFriendsClick,
        onProfile = onProfileClick
    ) {
        Column(
            modifier = Modifier.fillMaxSize(),
            horizontalAlignment = Alignment.CenterHorizontally
        ) {
            Spacer(Modifier.size(8.dp))
            ProfileCard(profile = profile, onClick = onProfileClick)

            Spacer(Modifier.size(12.dp))
            PlayingNowChip(count = playingNow)

            Spacer(Modifier.size(20.dp))

            Text("Pergunta ó Luso", style = MaterialTheme.typography.headlineLarge, color = Ink, textAlign = TextAlign.Center)
            Spacer(Modifier.size(4.dp))
            Text("Quanto sabes sobre Portugal e o mundo?", style = MaterialTheme.typography.bodyLarge, color = Ink, textAlign = TextAlign.Center)

            Spacer(Modifier.size(28.dp))

            StickerButton("JOGAR", Icons.Rounded.PlayArrow, onPlayClick, modifier = Modifier.fillMaxWidth())
            Spacer(Modifier.size(16.dp))
            StickerButton("HISTÓRICO", Icons.Rounded.History, onHistoryClick, fillColor = Gold, modifier = Modifier.fillMaxWidth())
            Spacer(Modifier.size(16.dp))
            if (isRegistered) {
                StickerButton("TERMINAR SESSÃO", Icons.Rounded.Logout, onSignOut, fillColor = Coral, modifier = Modifier.fillMaxWidth())
            } else {
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
        Box(
            modifier = Modifier.size(64.dp).stickerCircle(fillColor = Purple, shadowOffset = 4.dp),
            contentAlignment = Alignment.Center
        ) {
            Text(text = iniciais, style = MaterialTheme.typography.titleLarge, color = Cream)
        }
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
            Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                StatChip(label = "pontos", value = "$pontos", color = Gold)
                StatChip(label = "acertos", value = "$taxa%", color = Teal)
                StatChip(label = "jogos", value = "$jogos", color = Coral)
            }
            Spacer(Modifier.size(12.dp))
            XpBar(estado = (profile ?: Profile()).progressao, levelLabel = false)
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
private fun StatChip(label: String, value: String, color: androidx.compose.ui.graphics.Color) {
    Column(
        modifier = Modifier
            .stickerBlock(fillColor = color, cornerRadius = 14.dp, shadowOffset = 3.dp, borderWidth = 2.dp)
            .padding(horizontal = 12.dp, vertical = 8.dp),
        horizontalAlignment = Alignment.CenterHorizontally
    ) {
        Text(text = value, style = MaterialTheme.typography.labelLarge, color = textColorFor(color))
        Text(text = label, style = MaterialTheme.typography.bodyLarge, color = textColorFor(color))
    }
}
