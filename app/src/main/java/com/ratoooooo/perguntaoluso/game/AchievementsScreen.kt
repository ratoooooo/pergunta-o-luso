package com.ratoooooo.perguntaoluso.game

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.animation.core.Animatable
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.animation.core.FastOutSlowInEasing
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.tween
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.ui.unit.sp
import com.ratoooooo.perguntaoluso.ui.theme.Purple
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.lazy.grid.itemsIndexed
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.Lock
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.scale
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.ratoooooo.perguntaoluso.data.Profile
import com.ratoooooo.perguntaoluso.game.avatar.SymbolIcon
import com.ratoooooo.perguntaoluso.ui.theme.Cream
import com.ratoooooo.perguntaoluso.ui.theme.Gold
import com.ratoooooo.perguntaoluso.ui.theme.Ink
import com.ratoooooo.perguntaoluso.ui.theme.Lavender
import com.ratoooooo.perguntaoluso.ui.theme.NavTab
import com.ratoooooo.perguntaoluso.ui.theme.Neutral
import com.ratoooooo.perguntaoluso.ui.theme.Motion
import com.ratoooooo.perguntaoluso.ui.theme.SegmentedTabs
import com.ratoooooo.perguntaoluso.ui.theme.cascadeIn
import com.ratoooooo.perguntaoluso.ui.theme.rememberGlow
import com.ratoooooo.perguntaoluso.ui.theme.stickerBlock
import com.ratoooooo.perguntaoluso.ui.theme.stickerCircle
import com.ratoooooo.perguntaoluso.ui.theme.stickerSpring
import com.ratoooooo.perguntaoluso.ui.theme.textColorFor
import kotlinx.coroutines.delay

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
    var filter by rememberSaveable { mutableIntStateOf(0) }
    val visible = when (filter) {
        1 -> ACHIEVEMENTS.filter { it.unlocked(p) }
        2 -> ACHIEVEMENTS.filterNot { it.unlocked(p) }
        else -> ACHIEVEMENTS
    }
    MainScaffold(active = NavTab.NONE, onHome = onHome, onRanking = onRanking, onFriends = onFriends, onProfile = onProfile) {
        ScreenHeader(title = "Conquistas", subtitle = "$unlocked / ${ACHIEVEMENTS.size} desbloqueadas", onBack = onBack)
        Spacer(Modifier.size(16.dp))
        // Filtro em roxo: neste ecrã o dourado é o anel das conquistas já desbloqueadas —
        // um separador dourado dizia "conquista" onde só havia um filtro.
        SegmentedTabs(
            labels = listOf("Todas", "Feitas", "Por fazer"),
            selectedIndex = filter,
            onSelect = { filter = it }
        )
        Spacer(Modifier.size(14.dp))
        LazyVerticalGrid(
            columns = GridCells.Fixed(2),
            modifier = Modifier.weight(1f).fillMaxWidth(),
            horizontalArrangement = Arrangement.spacedBy(12.dp),
            verticalArrangement = Arrangement.spacedBy(14.dp)
        ) {
            itemsIndexed(visible, key = { _, a -> a.title }) { index, a -> AchievementCard(a, p, index) }
        }
    }
}

@Composable
private fun AchievementCard(a: Achievement, p: Profile, index: Int) {
    val done = a.unlocked(p)
    Column(
        Modifier.fillMaxWidth()
            .height(190.dp)
            .cascadeIn(index, key = done)
            .stickerBlock(fillColor = if (done) Lavender else Cream, cornerRadius = 20.dp, shadowOffset = if (done) 5.dp else 3.dp)
            .padding(vertical = 14.dp, horizontal = 10.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.SpaceBetween
    ) {
        // Celebração de desbloqueio: o círculo salta ao aparecer (mola com ultrapassagem,
        // não um tween linear) e as conquistas já feitas ganham um halo dourado a pulsar
        // por trás — o mesmo truque da barra de XP quase cheia, aqui a assinalar "mérito
        // alcançado" em vez de "quase lá".
        val escalaPop = remember(a.title) { Animatable(0.6f) }
        LaunchedEffect(a.title) {
            delay((index.coerceAtMost(Motion.MAX_STAGGER_STEPS) * Motion.STAGGER_MS).toLong())
            escalaPop.animateTo(1f, stickerSpring())
        }
        val brilho = rememberGlow(ativo = done, min = 0.15f, max = 0.5f, periodoMs = 1600)
        Box(contentAlignment = Alignment.Center) {
            if (done) {
                Box(
                    Modifier.size(76.dp)
                        .clip(androidx.compose.foundation.shape.CircleShape)
                        .background(Gold.copy(alpha = brilho))
                )
            }
            Box(
                Modifier.size(64.dp)
                    .scale(escalaPop.value)
                    .stickerCircle(
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
        Spacer(Modifier.size(6.dp))
        Box(Modifier.height(40.dp), contentAlignment = Alignment.Center) {
            Text(a.title, style = MaterialTheme.typography.labelLarge, color = Ink, textAlign = TextAlign.Center, maxLines = 2)
        }
        if (done) {
            Text(
                "Desbloqueada",
                style = MaterialTheme.typography.bodyLarge, color = Ink, textAlign = TextAlign.Center
            )
        } else {
            // Barra por baixo do "x de y". O texto sozinho obriga a fazer contas para saber se
            // se está perto; a barra responde a isso de relance, que é o ponto de mostrar
            // progresso em vez de um cadeado. Só nas bloqueadas — numa feita seria sempre 100 %
            // e o cartão já diz "Desbloqueada".
            Column(
                Modifier.fillMaxWidth().padding(horizontal = 6.dp),
                horizontalAlignment = Alignment.CenterHorizontally
            ) {
                Text(
                    a.progressText(p),
                    style = MaterialTheme.typography.bodyLarge.copy(fontSize = 13.sp, lineHeight = 16.sp),
                    color = LockedTint,
                    textAlign = TextAlign.Center,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis
                )
                Spacer(Modifier.size(5.dp))
                val fracao by animateFloatAsState(
                    targetValue = a.fracao(p),
                    animationSpec = tween(600, easing = FastOutSlowInEasing),
                    label = "progressoConquista"
                )
                Box(
                    Modifier.fillMaxWidth().height(8.dp)
                        .clip(RoundedCornerShape(4.dp))
                        .background(Neutral)
                        .border(2.dp, Ink, RoundedCornerShape(4.dp))
                ) {
                    Box(
                        Modifier.fillMaxHeight().fillMaxWidth(fracao)
                            .clip(RoundedCornerShape(4.dp))
                            .background(Purple)
                    )
                }
            }
        }
    }
}
