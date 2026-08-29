package com.ratoooooo.perguntaoluso.ui

/**
 * Interruptores de funcionalidade que se ligam e desligam **sem apagar código**.
 *
 * Nada aqui deve esconder código morto: cada flag protege uma funcionalidade inteira e a
 * funcionar, que se quer fora do caminho do jogador por agora.
 */
object FeatureFlags {

    /**
     * Quizzes da Comunidade escondidos da navegação normal (Fase 30).
     *
     * A funcionalidade continua inteira — `CustomCategoriesScreen`, `CustomCategoryRepository`,
     * `GameScreen.CUSTOM_CATEGORIES`, as salas privadas por código e as rules de
     * `/categorias_comunitarias` e `/denuncias` não foram tocadas. Só deixa de haver botão.
     *
     * Pôr a `true` devolve o botão ao Início e volta a tornar o ecrã alcançável; não é preciso
     * mexer em mais nada.
     */
    const val QUIZZES_COMUNIDADE_VISIVEIS = false
}
