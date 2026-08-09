package com.ratoooooo.perguntaoluso

import com.ratoooooo.perguntaoluso.data.ProfanityFilter
import com.ratoooooo.perguntaoluso.data.Question
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

class ProfanityFilterTest {

    @Test
    fun `apanha palavrao isolado`() {
        assertEquals("merda", ProfanityFilter.primeiraPalavraBloqueada("isto é uma merda"))
    }

    @Test
    fun `apanha independentemente de acentos e maiusculas`() {
        assertEquals("cabrao", ProfanityFilter.primeiraPalavraBloqueada("És um CABRÃO"))
    }

    /**
     * O erro clássico destes filtros: `puta` está dentro de *disputa* e *reputação*, `cu` dentro
     * de *cuidado*. Bloquear palavras legítimas é pior do que deixar passar um palavrão, porque
     * o autor não percebe o que fez de errado.
     */
    @Test
    fun `nao apanha palavras legitimas que contem as bloqueadas como substring`() {
        assertNull(
            ProfanityFilter.primeiraPalavraBloqueada(
                "A disputa pela reputação exigiu cuidado no curso da conferência",
                "Focinho, cabra-cega e assentada",
                "O Sporting empatou com o Estoril"
            )
        )
    }

    @Test
    fun `texto limpo passa`() {
        assertNull(ProfanityFilter.primeiraPalavraBloqueada("Qual é a capital de Portugal?"))
    }

    @Test
    fun `nulos e vazios nao rebentam`() {
        assertNull(ProfanityFilter.primeiraPalavraBloqueada(null, "", "   "))
    }

    @Test
    fun `varre o quiz inteiro incluindo opcoes de resposta`() {
        val q = Question(
            pergunta = "Qual destas é a certa?",
            opcoes = listOf("Lisboa", "vai te foder", "Porto", "Braga"),
            respostaCorreta = "Lisboa",
            dificuldade = "facil"
        )
        assertEquals(
            "vai te foder",
            ProfanityFilter.primeiraPalavraBloqueadaNoQuiz("Geografia", "Quiz simpático", listOf(q))
        )
    }

    @Test
    fun `quiz limpo passa inteiro`() {
        val q = Question(
            pergunta = "Em que ano foi a revolução dos cravos?",
            opcoes = listOf("1974", "1975", "1926", "1910"),
            respostaCorreta = "1974",
            dificuldade = "facil"
        )
        assertNull(ProfanityFilter.primeiraPalavraBloqueadaNoQuiz("História", "Sobre Portugal", listOf(q)))
    }
}
