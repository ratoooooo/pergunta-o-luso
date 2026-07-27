package com.starforge.app

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.Scaffold
import androidx.compose.ui.Modifier
import com.starforge.app.game.GameApp
import com.starforge.app.ui.theme.Cream
import com.starforge.app.ui.theme.PerguntaOLusoTheme

class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        setContent {
            PerguntaOLusoTheme {
                Scaffold(
                    modifier = Modifier.fillMaxSize().background(Cream)
                ) { innerPadding ->
                    Box(modifier = Modifier.fillMaxSize().padding(innerPadding)) {
                        AuthGate {
                            GameApp()
                        }
                    }
                }
            }
        }
    }
}
