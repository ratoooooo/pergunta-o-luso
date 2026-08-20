package com.ratoooooo.perguntaoluso

import com.ratoooooo.perguntaoluso.data.StreakDiario
import com.ratoooooo.perguntaoluso.data.StreakDiario.Estado
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class StreakDiarioTest {

    private fun estado(
        dias: Int = 0, ultimo: String = "", maior: Int = 0,
        protecoes: Int = 1, usada: String = ""
    ) = Estado(dias, ultimo, maior, protecoes, usada)

    @Test
    fun `primeira partida de sempre arranca a sequencia em 1`() {
        val r = StreakDiario.avaliar(estado(), "2026-08-10")
        assertEquals(1, r.estado.diasSeguidos)
        assertEquals("2026-08-10", r.estado.ultimoDiaJogado)
        assertEquals(1, r.estado.maiorSequenciaDias)
        assertTrue(r.avancou)
        assertFalse(r.protegeu)
    }

    @Test
    fun `jogar ontem e hoje incrementa`() {
        val r = StreakDiario.avaliar(estado(dias = 3, ultimo = "2026-08-09", maior = 3), "2026-08-10")
        assertEquals(4, r.estado.diasSeguidos)
        assertEquals(4, r.estado.maiorSequenciaDias)
        assertEquals(StreakDiario.XP_POR_DIA, r.xp)
    }

    @Test
    fun `segunda partida do mesmo dia nao conta outra vez`() {
        val r = StreakDiario.avaliar(estado(dias = 4, ultimo = "2026-08-10", maior = 4), "2026-08-10")
        assertEquals(4, r.estado.diasSeguidos)
        assertFalse(r.avancou)
        assertEquals("XP de sequência é por dia, não por partida", 0, r.xp)
    }

    @Test
    fun `falhar um dia com proteccao mantem a sequencia e gasta o escudo`() {
        val r = StreakDiario.avaliar(
            estado(dias = 5, ultimo = "2026-08-08", maior = 5, protecoes = 1), "2026-08-10"
        )
        assertTrue(r.protegeu)
        assertEquals(6, r.estado.diasSeguidos)
        assertEquals(0, r.estado.protecoes)
        assertEquals("regista o dia que ficou tapado", "2026-08-09", r.estado.protecaoUsadaEm)
    }

    @Test
    fun `falhar um dia sem proteccao reinicia`() {
        val r = StreakDiario.avaliar(
            estado(dias = 5, ultimo = "2026-08-08", maior = 5, protecoes = 0), "2026-08-10"
        )
        assertFalse(r.protegeu)
        assertEquals(1, r.estado.diasSeguidos)
        assertEquals("o recorde não se perde", 5, r.estado.maiorSequenciaDias)
    }

    @Test
    fun `falhar dois dias reinicia mesmo com proteccao`() {
        val r = StreakDiario.avaliar(
            estado(dias = 9, ultimo = "2026-08-07", maior = 9, protecoes = 1), "2026-08-10"
        )
        assertEquals(1, r.estado.diasSeguidos)
        assertEquals("o escudo só tapa um dia, não dois", 1, r.estado.protecoes)
        assertFalse(r.protegeu)
    }

    @Test
    fun `proteccao repoe-se ao chegar a sete dias`() {
        val r = StreakDiario.avaliar(
            estado(dias = 6, ultimo = "2026-08-09", maior = 6, protecoes = 0), "2026-08-10"
        )
        assertEquals(7, r.estado.diasSeguidos)
        assertEquals(StreakDiario.MAX_PROTECOES, r.estado.protecoes)
    }

    @Test
    fun `proteccoes nunca passam do maximo`() {
        val r = StreakDiario.avaliar(
            estado(dias = 13, ultimo = "2026-08-09", maior = 13, protecoes = 1), "2026-08-10"
        )
        assertEquals(14, r.estado.diasSeguidos)
        assertEquals(StreakDiario.MAX_PROTECOES, r.estado.protecoes)
    }

    @Test
    fun `relogio do dispositivo para tras nao estraga a sequencia`() {
        val anterior = estado(dias = 8, ultimo = "2026-08-10", maior = 8)
        val r = StreakDiario.avaliar(anterior, "2026-08-08")
        assertEquals(8, r.estado.diasSeguidos)
        assertFalse(r.avancou)
        assertEquals(anterior.ultimoDiaJogado, r.estado.ultimoDiaJogado)
    }

    @Test
    fun `data guardada invalida trata-se como primeira vez`() {
        val r = StreakDiario.avaliar(estado(dias = 4, ultimo = "lixo"), "2026-08-10")
        assertEquals(1, r.estado.diasSeguidos)
    }

    @Test
    fun `o recorde historico nunca desce`() {
        var e = estado(dias = 20, ultimo = "2026-08-01", maior = 20, protecoes = 0)
        e = StreakDiario.avaliar(e, "2026-08-10").estado   // falhou muito, reinicia
        assertEquals(1, e.diasSeguidos)
        assertEquals(20, e.maiorSequenciaDias)
    }

    @Test
    fun `a nota de proteccao so aparece no dia e no seguinte`() {
        val e = estado(usada = "2026-08-09")
        assertTrue(StreakDiario.protecaoRecente(e, "2026-08-09"))
        assertTrue(StreakDiario.protecaoRecente(e, "2026-08-10"))
        assertFalse(StreakDiario.protecaoRecente(e, "2026-08-11"))
        assertFalse(StreakDiario.protecaoRecente(estado(), "2026-08-10"))
    }

    @Test
    fun `o XP da sequencia e fixo e nao escala com o tamanho`() {
        val curto = StreakDiario.avaliar(estado(dias = 1, ultimo = "2026-08-09"), "2026-08-10")
        val longo = StreakDiario.avaliar(estado(dias = 300, ultimo = "2026-08-09"), "2026-08-10")
        assertEquals(curto.xp, longo.xp)
        assertEquals(StreakDiario.XP_POR_DIA, longo.xp)
    }
}
