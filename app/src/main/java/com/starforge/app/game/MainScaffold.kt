package com.starforge.app.game

import androidx.compose.foundation.background
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
import com.starforge.app.ui.theme.BottomNav
import com.starforge.app.ui.theme.Cream
import com.starforge.app.ui.theme.NavTab

/** Main screens: cream page, content on top, the fixed bottom nav bar pinned below. */
@Composable
fun MainScaffold(
    active: NavTab,
    onHome: () -> Unit,
    onRanking: () -> Unit,
    onFriends: () -> Unit,
    onProfile: () -> Unit,
    content: @Composable ColumnScope.() -> Unit
) {
    Column(
        modifier = Modifier.fillMaxSize().background(Cream).padding(horizontal = 24.dp).padding(top = 24.dp)
    ) {
        Column(modifier = Modifier.weight(1f).fillMaxWidth(), content = content)
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
