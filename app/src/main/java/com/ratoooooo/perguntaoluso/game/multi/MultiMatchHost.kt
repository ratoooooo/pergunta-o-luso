package com.ratoooooo.perguntaoluso.game.multi

import androidx.activity.compose.BackHandler
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.key
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.lifecycle.viewmodel.compose.viewModel

/**
 * [salaId] non-null means this match came from a **direct friend challenge**: the room already
 * exists, so we join it instead of queueing. "Novo jogo" then falls back to random matchmaking,
 * since the one-off challenge room cannot be replayed.
 */
@Composable
fun MultiMatchHost(
    format: MatchFormat,
    categoria: String,
    modo: String,
    salaId: String? = null,
    /**
     * Só o caminho do servidor: diz o que pedir assim que o socket abrir (criar um desafio, criar
     * uma sala privada, entrar por código). Na RTDB a sala já existia antes de se chegar aqui e o
     * [salaId] bastava.
     */
    pedido: PedidoDeEntrada? = null,
    onExit: () -> Unit
) {
    var restart by remember { mutableIntStateOf(0) }
    key(restart) {
        val vm: MultiMatchViewModel = viewModel()
        val state by vm.uiState.collectAsState()

        LaunchedEffect(Unit) {
            when {
                restart > 0 -> vm.start(format, categoria, modo)
                pedido != null -> vm.iniciarComPedido(format, categoria, modo, pedido)
                salaId != null -> vm.startExisting(format, categoria, modo, salaId)
                else -> vm.start(format, categoria, modo)
            }
        }
        BackHandler { vm.leave(); onExit() }

        MultiMatchScreen(
            state = state,
            onSelectAnswer = vm::selectAnswer,
            onLeave = { vm.leave(); onExit() },
            onPlayAgain = { vm.leave(); restart++ },
            onHome = { vm.leave(); onExit() },
            onForceStart = vm::forceStartGame,
            onSwitchLobby = vm::switchLobby
        )
    }
}
