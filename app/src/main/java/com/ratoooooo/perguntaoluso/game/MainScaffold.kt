package com.ratoooooo.perguntaoluso.game

import androidx.compose.foundation.background
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import com.ratoooooo.perguntaoluso.ui.theme.BottomNav
import com.ratoooooo.perguntaoluso.ui.theme.Cream
import com.ratoooooo.perguntaoluso.ui.theme.NavTab

/** Main screens: cream page, content on top, the fixed bottom nav bar pinned below. */
@Composable
fun MainScaffold(
    active: NavTab,
    onHome: () -> Unit,
    onRanking: () -> Unit,
    onFriends: () -> Unit,
    onProfile: () -> Unit,
    /**
     * Fase 29: torna a área de conteúdo deslizável. **Opt-in**, e não o comportamento por
     * omissão, porque os ecrãs que já usam `LazyColumn` (Ranking, Histórico, Amigos,
     * Conquistas, Quizzes) rebentam se forem medidos dentro de um scroll — altura máxima
     * infinita. Só os ecrãs de conteúdo fixo é que devem ligar isto.
     */
    scrollable: Boolean = false,
    content: @Composable ColumnScope.() -> Unit
) {
    Column(
        modifier = Modifier.fillMaxSize().background(Cream).padding(horizontal = 24.dp).padding(top = 24.dp)
    ) {
        Column(
            modifier = Modifier
                .weight(1f)
                .fillMaxWidth()
                .then(if (scrollable) Modifier.verticalScroll(rememberScrollState()) else Modifier),
            content = content
        )
        Spacer(Modifier.size(8.dp))
        BottomNav(
            active = active,
            onHome = onHome,
            onRanking = onRanking,
            onFriends = onFriends,
            onProfile = onProfile,
            modifier = Modifier.padding(bottom = 12.dp)
        )
    }
}
