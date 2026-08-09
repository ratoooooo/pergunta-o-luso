package com.ratoooooo.perguntaoluso.ui.theme

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.EmojiEvents
import androidx.compose.material.icons.rounded.Group
import androidx.compose.material.icons.rounded.Home
import androidx.compose.material.icons.rounded.Person
import androidx.compose.material3.Icon
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.unit.dp

enum class NavTab { HOME, RANKING, FRIENDS, PROFILE, NONE }

/**
 * Fixed bottom navigation bar (mockup screens' footer): white pill, 3dp ink border,
 * four equal sections. The active tab is purple with a small rounded underline;
 * inactive tabs are ink with no underline.
 */
@Composable
fun BottomNav(
    active: NavTab,
    onHome: () -> Unit,
    onRanking: () -> Unit,
    onFriends: () -> Unit,
    onProfile: () -> Unit,
    modifier: Modifier = Modifier
) {
    Row(
        modifier = modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(24.dp))
            .background(Color.White)
            .border(BorderStroke(3.dp, Ink), RoundedCornerShape(24.dp))
            .padding(vertical = 12.dp),
        horizontalArrangement = Arrangement.SpaceEvenly,
        verticalAlignment = Alignment.CenterVertically
    ) {
        NavItem(Icons.Rounded.Home, active == NavTab.HOME, onHome)
        NavItem(Icons.Rounded.EmojiEvents, active == NavTab.RANKING, onRanking)
        NavItem(Icons.Rounded.Group, active == NavTab.FRIENDS, onFriends)
        NavItem(Icons.Rounded.Person, active == NavTab.PROFILE, onProfile)
    }
}

@Composable
private fun NavItem(icon: ImageVector, active: Boolean, onClick: () -> Unit) {
    Column(
        horizontalAlignment = Alignment.CenterHorizontally,
        modifier = Modifier.clickable(onClick = onClick).padding(horizontal = 18.dp, vertical = 4.dp)
    ) {
        Icon(
            imageVector = icon,
            contentDescription = null,
            tint = if (active) Purple else Ink,
            modifier = Modifier.size(28.dp)
        )
        Spacer(Modifier.size(5.dp))
        Box(
            modifier = Modifier
                .width(20.dp)
                .height(3.dp)
                .clip(RoundedCornerShape(2.dp))
                .background(if (active) Purple else Color.Transparent)
        )
    }
}
