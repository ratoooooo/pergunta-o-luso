package com.ratoooooo.perguntaoluso

import com.ratoooooo.perguntaoluso.data.Patente
import com.ratoooooo.perguntaoluso.data.Progressao
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

/**
 * As patentes são puramente derivadas do nível, que por sua vez é derivado do `xpTotal`.
 * Estes testes prendem as fronteiras: são a parte que se pode enganar sem dar erro nenhum
 * em runtime (um jogador ficaria só com o nome errado ao lado do número).
 */
class PatenteTest {

    @Test
    fun `cada fronteira cai na patente certa`() {
        val esperado = mapOf(
            1 to Patente.GRUMETE, 4 to Patente.GRUMETE,
            5 to Patente.MARINHEIRO, 9 to Patente.MARINHEIRO,
            10 to Patente.PILOTO, 14 to Patente.PILOTO,
            15 to Patente.CAPITAO, 19 to Patente.CAPITAO,
            20 to Patente.NAVEGADOR, 24 to Patente.NAVEGADOR,
            25 to Patente.DESCOBRIDOR, 99 to Patente.DESCOBRIDOR
        )
        esperado.forEach { (nivel, patente) ->
            assertEquals("nível $nivel", patente, Patente.paraNivel(nivel))
        }
    }

    @Test
    fun `nivel invalido nao rebenta e cai em grumete`() {
        assertEquals(Patente.GRUMETE, Patente.paraNivel(0))
        assertEquals(Patente.GRUMETE, Patente.paraNivel(-5))
    }

    @Test
    fun `proxima patente aponta para a seguinte e termina em null`() {
        assertEquals(Patente.MARINHEIRO, Patente.proximaApos(1))
        assertEquals(Patente.MARINHEIRO, Patente.proximaApos(4))
        assertEquals(Patente.PILOTO, Patente.proximaApos(5))
        assertEquals(Patente.DESCOBRIDOR, Patente.proximaApos(24))
        assertNull(Patente.proximaApos(25))
        assertNull(Patente.proximaApos(80))
    }

    @Test
    fun `as fronteiras estao ordenadas e comecam no nivel 1`() {
        val minimos = Patente.entries.map { it.nivelMinimo }
        assertEquals(1, minimos.first())
        assertEquals(minimos.sorted(), minimos)
        assertEquals(minimos.distinct().size, minimos.size)
    }

    /**
     * O XP acumulado para chegar ao nível n é `75*(n-1)*(n+2)` — a soma dos custos
     * `300 + (k-1)*150`. Os números da tabela documentada em [Patente] saem daqui, por isso
     * este teste é o que impede a documentação de mentir se a curva mudar.
     */
    @Test
    fun `xp acumulado das fronteiras bate certo com a curva`() {
        fun xpAcumuladoParaNivel(n: Int) = 75 * (n - 1) * (n + 2)

        val esperado = mapOf(
            Patente.GRUMETE to 0,
            Patente.MARINHEIRO to 2_100,
            Patente.PILOTO to 8_100,
            Patente.CAPITAO to 17_850,
            Patente.NAVEGADOR to 31_350,
            Patente.DESCOBRIDOR to 48_600
        )
        esperado.forEach { (patente, xp) ->
            assertEquals(
                "XP acumulado de ${patente.titulo}",
                xp,
                xpAcumuladoParaNivel(patente.nivelMinimo)
            )
            // e o mesmo XP, passado pela Progressao real, tem de dar o nível da fronteira
            assertEquals(
                "nível a ${xp} XP",
                patente.nivelMinimo,
                Progressao.estado(xp).nivel
            )
        }
    }
}
