package com.ratoooooo.perguntaoluso.data

/**
 * Quantas denúncias distintas ocultam um quiz automaticamente.
 *
 * Três é um compromisso: um número mais baixo deixa uma única pessoa (ou duas) silenciar
 * conteúdo legítimo; mais alto deixa spam evidente à vista tempo de mais, num jogo que não tem
 * moderadores a olhar. O quiz não é apagado — só deixa de ser público — e o autor continua a
 * vê-lo e pode voltar a publicá-lo.
 */
const val DENUNCIAS_PARA_OCULTAR = 3

data class CustomCategory(
    val id: String = "",
    val titulo: String = "",
    val descricao: String = "",
    val criadorUid: String = "",
    val criadorNome: String = "",
    val publica: Boolean = true,
    val criadoEm: Long = 0L,
    val mediaClassificacao: Double = 0.0,
    val totalVotos: Int = 0,
    /** Denúncias acumuladas. Aos [DENUNCIAS_PARA_OCULTAR] o quiz é despublicado automaticamente. */
    val totalDenuncias: Int = 0,
    val perguntas: List<Question> = emptyList()
)
