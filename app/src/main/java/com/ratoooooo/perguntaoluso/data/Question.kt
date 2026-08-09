package com.ratoooooo.perguntaoluso.data

const val VERDADEIRO = "Verdadeiro"
const val FALSO = "Falso"

data class Question(
    val pergunta: String = "",
    val opcoes: List<String> = emptyList(),
    val respostaCorreta: String = "",
    val dificuldade: String = ""
) {
    val isVerdadeiroFalso: Boolean
        get() = opcoes.size == 2 &&
            opcoes.map { it.trim().lowercase() }.toSet() == setOf(VERDADEIRO.lowercase(), FALSO.lowercase())

    fun toMap(): Map<String, Any> = mapOf(
        "pergunta" to pergunta,
        "opcoes" to opcoes,
        "respostaCorreta" to respostaCorreta,
        "dificuldade" to dificuldade
    )
}
