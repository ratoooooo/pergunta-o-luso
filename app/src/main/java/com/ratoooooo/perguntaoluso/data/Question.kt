package com.ratoooooo.perguntaoluso.data

const val VERDADEIRO = "Verdadeiro"
const val FALSO = "Falso"

data class Question(
    val pergunta: String = "",
    val opcoes: List<String> = emptyList(),
    val respostaCorreta: String = "",
    val dificuldade: String = ""
) {
    /**
     * True/False question. No schema change was needed: it is just an `opcoes` array of two
     * ("Verdadeiro"/"Falso") instead of four, so the 964 existing questions keep working
     * untouched. The UI uses this to render two taller, icon-labelled cards.
     */
    val isVerdadeiroFalso: Boolean
        get() = opcoes.size == 2 &&
            opcoes.map { it.trim().lowercase() }.toSet() == setOf(VERDADEIRO.lowercase(), FALSO.lowercase())
}
