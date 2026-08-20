package com.ratoooooo.perguntaoluso

import com.ratoooooo.perguntaoluso.game.GameMode
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * Prende as invariantes dos modos.
 *
 * O `tagline` já se desencontrou do código antes — "Quatro jogadores" ficou no ecrã de formato
 * durante fases depois de o Grupo passar a 10, e "Erras uma e acabou" sobreviveu à mudança para
 * três vidas. Estes testes fazem barulho quando a regra muda e o texto não.
 */
class GameModeTest {

    @Test
    fun `so as Eliminatorias eliminam por vidas`() {
        val comVidas = GameMode.entries.filter { it.vidas > 0 }
        assertEquals(listOf(GameMode.ELIMINATORIAS), comVidas)
    }

    @Test
    fun `Eliminatorias tem tres vidas`() {
        assertEquals(3, GameMode.ELIMINATORIAS.vidas)
    }

    @Test
    fun `modo que elimina por vidas nao tem limite de perguntas`() {
        GameMode.entries.forEach { modo ->
            assertEquals(
                "semLimiteDePerguntas tem de acompanhar vidas em ${modo.id}",
                modo.vidas > 0,
                modo.semLimiteDePerguntas
            )
        }
    }

    @Test
    fun `o texto das Eliminatorias nao promete morte a primeira`() {
        val tagline = GameMode.ELIMINATORIAS.tagline.lowercase()
        assertFalse(
            "tagline ainda descreve a regra antiga: $tagline",
            "erras uma e acabou" in tagline
        )
        assertTrue("tagline devia falar das vidas: $tagline", "vidas" in tagline)
    }

    @Test
    fun `modos sem vidas correm um numero fixo de perguntas`() {
        GameMode.entries.filter { it.vidas == 0 }.forEach {
            assertTrue("${it.id} devia ter perguntas", it.questionCount > 0)
            assertFalse(it.semLimiteDePerguntas)
        }
    }

    @Test
    fun `o lote inicial das Eliminatorias cobre o marco de vitoria com folga`() {
        // Se o lote fosse menor do que o marco, ninguém ganhava sem depender da recarga.
        assertTrue(GameMode.ELIMINATORIAS.questionCount >= 20)
    }
}
