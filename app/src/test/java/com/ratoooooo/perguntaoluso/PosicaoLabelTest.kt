package com.ratoooooo.perguntaoluso

import com.ratoooooo.perguntaoluso.game.multi.posicaoLabel
import org.junit.Assert.assertEquals
import org.junit.Test

/**
 * Prende `posicaoLabel` contra o defeito real: um `when` fixo em quatro ramos ficava preso em
 * "4.º lugar" para toda a gente do 4.º lugar para baixo, num Grupo que já vai até 10 (ver
 * decisoes/grupo-4-a-10.md). Os índices 4 e 9 são exactamente os dois casos que essa lambda
 * antiga não distinguia.
 */
class PosicaoLabelTest {

    @Test
    fun `1o lugar tem exclamacao, os restantes nao`() {
        assertEquals("1.º lugar!", posicaoLabel(0))
        assertEquals("2.º lugar", posicaoLabel(1))
        assertEquals("3.º lugar", posicaoLabel(2))
        assertEquals("4.º lugar", posicaoLabel(3))
    }

    @Test
    fun `5o lugar nao fica preso no 4o`() {
        assertEquals("5.º lugar", posicaoLabel(4))
    }

    @Test
    fun `10o lugar - o topo do Grupo`() {
        assertEquals("10.º lugar", posicaoLabel(9))
    }
}
