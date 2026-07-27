package com.starforge.app.game

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.lazy.grid.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.Lock
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import com.starforge.app.data.Profile
import com.starforge.app.game.avatar.SymbolIcon
import com.starforge.app.ui.theme.Cream
import com.starforge.app.ui.theme.Gold
import com.starforge.app.ui.theme.Ink
import com.starforge.app.ui.theme.Lavender
import com.starforge.app.ui.theme.NavTab
import com.starforge.app.ui.theme.Neutral
import com.starforge.app.ui.theme.stickerBlock
import com.starforge.app.ui.theme.stickerCircle
import com.starforge.app.ui.theme.textColorFor

private val LockedTint = Color(0xFF6E6780)

@Composable
fun AchievementsScreen(
    profile: Profile?,
    onBack: () -> Unit,
    onHome: () -> Unit,
    onRanking: () -> Unit,
    onFriends: () -> Unit,
    onProfile: () -> Unit
) {
    val p = profile ?: Profile()
    val unlocked = ACHIEVEMENTS.count { it.unlocked(p) }
    MainScaffold(active = NavTab.NONE, onHome = onHome, onRanking = onRanking, onFriends = onFriends, onProfile = onProfile) {
        ScreenHeader(title = "Conquistas", subtitle = "$unlocked / ${ACHIEVEMENTS.size} desbloqueadas", onBack = onBack)
        Spacer(Modifier.size(16.dp))
        LazyVerticalGrid(
            columns = GridCells.Fixed(2),
            modifier = Modifier.weight(1f).fillMaxWidth(),
            horizontalArrangement = Arrangement.spacedBy(12.dp),
            verticalArrangement = Arrangement.spacedBy(14.dp)
        ) {
            items(ACHIEVEMENTS) { a -> AchievementCard(a, p) }
        }
    }
}

@Composable
private fun AchievementCard(a: Achievement, p: Profile) {
    val done = a.unlocked(p)
    Column(
        Modifier.fillMaxWidth()
            .stickerBlock(fillColor = if (done) Lavender else Cream, cornerRadius = 20.dp, shadowOffset = if (done) 5.dp else 3.dp)
            .padding(vertical = 14.dp, horizontal = 10.dp),
        horizontalAlignment = Alignment.CenterHorizontally
    ) {
        Box(contentAlignment = Alignment.Center) {
            Box(
                Modifier.size(64.dp).stickerCircle(
                    fillColor = if (done) a.symbol.bg else Neutral,
                    shadowOffset = 3.dp,
                    borderWidth = if (done) 4.dp else 3.dp,
                    borderColor = if (done) Gold else Ink
                ),
                contentAlignment = Alignment.Center
            ) {
                SymbolIcon(a.symbol, Modifier.fillMaxSize(0.56f), tint = if (done) textColorFor(a.symbol.bg) else LockedTint)
            }
            if (!done) {
                Box(
                    Modifier.size(26.dp).align(Alignment.BottomEnd).stickerCircle(fillColor = Ink, shadowOffset = 2.dp, borderWidth = 2.dp),
                    contentAlignment = Alignment.Center
                ) {
                    Icon(Icons.Rounded.Lock, contentDescription = "Bloqueado", tint = Cream, modifier = Modifier.size(14.dp))
                }
            }
        }
        Spacer(Modifier.size(10.dp))
        Text(a.title, style = MaterialTheme.typography.labelLarge, color = Ink, textAlign = TextAlign.Center, maxLines = 2)
        Spacer(Modifier.size(3.dp))
        Text(
            if (done) "Desbloqueada" else a.progressText(p),
            style = MaterialTheme.typography.bodyLarge, color = if (done) Ink else LockedTint, textAlign = TextAlign.Center
        )
    }
}
