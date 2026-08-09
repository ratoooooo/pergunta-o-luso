package com.ratoooooo.perguntaoluso.data

import java.text.Normalizer

/**
 * Filtro estático de linguagem imprópria, aplicado antes de gravar um quiz da comunidade.
 *
 * **Isto não é moderação a sério** e não pretende ser: é uma barreira contra spam e insultos
 * óbvios, do tipo que qualquer lista de palavras apanha. Quem quiser contornar contorna, e
 * ofensas escritas sem palavrões passam intactas. O que trava conteúdo problemático a sério é
 * o sistema de denúncias e a revisão manual — ver a Fase 25 no GAME_DESIGN.md.
 *
 * ### Porquê fronteiras de palavra
 *
 * A armadilha clássica destes filtros é a correspondência por substring: `"puta"` está dentro de
 * *disputa* e *reputação*, `"cu"` dentro de *cuidado* e *curso*, `"merda"` dentro de nada útil
 * mas `"caralho"` perto de nomes de lugares. Bloquear palavras legítimas é pior do que deixar
 * passar um palavrão, porque o autor não percebe o que fez de errado. Por isso a comparação é
 * feita com `\b` sobre o texto normalizado (minúsculas, sem acentos), nunca por `contains`.
 */
object ProfanityFilter {

    /**
     * Lista deliberadamente curta: palavrões e insultos inequívocos em português. Termos
     * ambíguos ou que dependem do contexto ficam de fora de propósito — para esses existe a
     * denúncia. Sem variantes com números/símbolos: perseguir *l33tspeak* numa lista estática
     * gera mais falsos positivos do que apanha.
     */
    private val PALAVRAS = listOf(
        "caralho", "caralhos", "foda", "fodas", "fode", "foder", "fodido", "fodida",
        "merda", "merdas", "puta", "putas", "cabrao", "cabroes",
        "piroca", "pila", "cona", "colhoes",
        "filho da puta", "filhos da puta", "vai te foder", "vai levar no cu",
        "otario", "otaria", "imbecil", "estupido", "estupida",
        "paneleiro", "paneleiros", "maricas",
        "preto de merda", "macaco de merda", "cigano de merda",
        "retardado", "retardada", "mongoloide", "atrasado mental"
    )
        // Frases antes de palavras soltas: assim "vai te foder" é reportado por inteiro em vez
        // de sair só "foder", e o autor percebe melhor o que tem de corrigir.
        .sortedByDescending { it.length }

    /*
     * Termos deliberadamente FORA da lista, depois de os testes unitários apanharem falsos
     * positivos reais numa app de cultura geral portuguesa:
     *
     *   cabra   — "cabra-cega" (o jogo) e o animal; só é insulto em contexto
     *   burro   — o animal, óbvio numa app de perguntas
     *   puto    — em Portugal significa miúdo, e não é ofensivo
     *   broche  — a peça de joalharia é o sentido corrente
     *   corno   — o do animal, e o corne inglês
     *   idiota  — "O Idiota", de Dostoiévski
     *   bicha   — em Portugal também é fila
     *
     * Bloquear qualquer um destes recusaria perguntas legítimas sem o autor perceber porquê —
     * pior do que deixar passar o palavrão, que a denúncia ainda apanha.
     */

    private val REGEX = PALAVRAS
        .map { Regex("\\b" + Regex.escape(normalizar(it)) + "\\b") }

    /** Minúsculas + sem acentos, para `cabrão`/`CABRAO`/`cabrao` colidirem todos. */
    private fun normalizar(texto: String): String =
        Normalizer.normalize(texto.lowercase(), Normalizer.Form.NFD)
            .replace(Regex("\\p{Mn}+"), "")

    /**
     * Devolve a primeira palavra bloqueada encontrada em [textos], ou `null` se estiver tudo
     * limpo. Devolver a palavra (e não só um booleano) permite dizer ao autor o que corrigir.
     */
    fun primeiraPalavraBloqueada(vararg textos: String?): String? {
        for (texto in textos) {
            if (texto.isNullOrBlank()) continue
            val limpo = normalizar(texto)
            for ((i, r) in REGEX.withIndex()) {
                if (r.containsMatchIn(limpo)) return PALAVRAS[i]
            }
        }
        return null
    }

    /** Igual, mas sobre um quiz inteiro: título, descrição, perguntas e opções. */
    fun primeiraPalavraBloqueadaNoQuiz(
        titulo: String,
        descricao: String,
        perguntas: List<Question>
    ): String? {
        primeiraPalavraBloqueada(titulo, descricao)?.let { return it }
        for (p in perguntas) {
            primeiraPalavraBloqueada(p.pergunta, p.respostaCorreta)?.let { return it }
            primeiraPalavraBloqueada(*p.opcoes.toTypedArray())?.let { return it }
        }
        return null
    }
}

/** Lançada quando um quiz é recusado pelo [ProfanityFilter]. */
class ConteudoImproprioException(val palavra: String) :
    IllegalArgumentException("Conteúdo impróprio: \"$palavra\"")
