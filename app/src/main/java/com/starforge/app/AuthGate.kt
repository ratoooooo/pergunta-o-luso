package com.starforge.app

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import com.starforge.app.data.AuthRepository
import com.starforge.app.ui.theme.Cream

/**
 * Blocks [content] until anonymous sign-in resolves, so no screen (and no RTDB
 * write) ever happens without a signed-in uid. Renders the plain app background
 * while waiting — sign-in is fast enough that the player sees nothing unusual.
 */
@Composable
fun AuthGate(content: @Composable () -> Unit) {
    var signedIn by remember { mutableStateOf(false) }

    LaunchedEffect(Unit) {
        AuthRepository().ensureSignedIn()
        signedIn = true
    }

    if (signedIn) {
        content()
    } else {
        Box(modifier = Modifier.fillMaxSize().background(Cream))
    }
}
