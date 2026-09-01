package com.ratoooooo.perguntaoluso

import com.ratoooooo.perguntaoluso.game.GameScreen
import com.ratoooooo.perguntaoluso.game.destinoAoVoltarDoPicker
import org.junit.Assert.assertEquals
import org.junit.Test

/**
 * O picker de categoria tem **duas** entradas, e por isso não pode ter um "voltar" fixo.
 *
 * Do Início escolhe-se o formato primeiro, e voltar é ir ao FormatScreen. Mas `startChallenge`
 * entra no picker a partir de **Amigos**, com o formato já fixado em 1x1 — aí o FormatScreen não
 * é de onde se veio, e sair sem limpar deixa `desafioPara` armado (sobrevive ao `sessionOnly()`).
 * O primeiro ramo do `onModeChosen` é `desafio != null`, portanto um jogo **Solo** começado a
 * seguir enviava um convite de 1x1 em vez de jogar sozinho.
 */
class VoltarDoPickerTest {

    @Test
    fun `sem desafio pendente volta ao formato`() {
        assertEquals(GameScreen.FORMAT_SELECT, destinoAoVoltarDoPicker(temDesafioPendente = false))
    }

    @Test
    fun `com desafio pendente volta a amigos, que e de onde se veio`() {
        assertEquals(GameScreen.FRIENDS, destinoAoVoltarDoPicker(temDesafioPendente = true))
    }
}
