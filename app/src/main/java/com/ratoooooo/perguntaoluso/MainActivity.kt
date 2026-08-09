package com.ratoooooo.perguntaoluso

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
import com.ratoooooo.perguntaoluso.game.GameApp
import com.ratoooooo.perguntaoluso.ui.theme.Cream
import com.ratoooooo.perguntaoluso.ui.theme.PerguntaOLusoTheme

class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        requestedOrientation = android.content.pm.ActivityInfo.SCREEN_ORIENTATION_PORTRAIT
        com.ratoooooo.perguntaoluso.audio.SoundEffects.init(this)
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

    /**
     * O `SoundPool` guarda o PCM descodificado em memória; sem libertar, uma rotação ou um
     * recomeço da Activity deixava-o pendurado. `isFinishing` distingue o fim real da Activity
     * de uma recriação de configuração, em que vale a pena manter as amostras carregadas.
     */
    override fun onDestroy() {
        super.onDestroy()
        if (isFinishing) com.ratoooooo.perguntaoluso.audio.SoundEffects.libertar()
    }
}
