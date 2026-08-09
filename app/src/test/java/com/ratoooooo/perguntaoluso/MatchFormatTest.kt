package com.ratoooooo.perguntaoluso

import com.ratoooooo.perguntaoluso.game.multi.MatchFormat
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * O tamanho dos formatos já se desencontrou do documento e da interface uma vez (o Grupo passou
 * a 10 lugares e tanto o `GAME_DESIGN.md` como o cartão do `FormatScreen` continuaram a dizer 4
 * — ver Fases 30 e 31). Estas invariantes são baratas de prender e caras de descobrir por
 * acidente: um mínimo maior que a capacidade tornaria um formato impossível de arrancar, e é o
 * género de erro que só aparece com gente real numa sala.
 */
class MatchFormatTest {

    @Test
    fun `minimo nunca excede a capacidade`() {
        MatchFormat.entries.forEach { f ->
            assertTrue(
                "${f.id}: minPlayers ${f.minPlayers} > players ${f.players}",
                f.minPlayers <= f.players
            )
        }
    }

    @Test
    fun `minimo e capacidade sao sempre pelo menos dois`() {
        MatchFormat.entries.forEach { f ->
            assertTrue("${f.id}: precisa de pelo menos 2 jogadores", f.minPlayers >= 2)
        }
    }

    /** Com equipas, os lugares têm de dividir em dois — senão uma equipa fica a menos. */
    @Test
    fun `formatos por equipas tem tamanho fixo e par`() {
        MatchFormat.entries.filter { it.teamBased }.forEach { f ->
            assertEquals("${f.id}: equipas exigem tamanho fixo", f.players, f.minPlayers)
            assertEquals("${f.id}: lugares têm de ser pares", 0, f.players % 2)
        }
    }

    @Test
    fun `so o Grupo tem tamanho flexivel`() {
        assertTrue(MatchFormat.GRUPO.hasFlexibleSize)
        assertFalse(MatchFormat.ONE_V_ONE.hasFlexibleSize)
        assertFalse(MatchFormat.TWO_V_TWO.hasFlexibleSize)

        assertEquals(4, MatchFormat.GRUPO.minPlayers)
        assertEquals(10, MatchFormat.GRUPO.players)
    }

    /** O texto do FormatScreen sai daqui, para não voltar a divergir do código. */
    @Test
    fun `rotulo de tamanho descreve o formato`() {
        assertEquals("4 a 10 jogadores", MatchFormat.GRUPO.sizeLabel)
        assertEquals("2 jogadores", MatchFormat.ONE_V_ONE.sizeLabel)
        assertEquals("4 jogadores", MatchFormat.TWO_V_TWO.sizeLabel)
    }

    @Test
    fun `queueKey separa formato categoria e modo e limpa caracteres proibidos`() {
        val k = MatchFormat.queueKey(MatchFormat.GRUPO, "Cultura Geral", "classico")
        assertEquals("grupo__Cultura_Geral__classico", k)
        // RTDB proíbe . $ # [ ] / numa chave
        val sujo = MatchFormat.queueKey(MatchFormat.ONE_V_ONE, "A.B\$C#D[E]F/G", "caotico")
        assertFalse(sujo.any { it in ".\$#[]/" })
    }
}
