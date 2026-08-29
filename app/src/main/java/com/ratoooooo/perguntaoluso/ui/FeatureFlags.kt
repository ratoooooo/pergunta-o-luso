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

    /**
     * A partida ao vivo corre no servidor próprio em vez da RTDB (fase 3 do servidor da partida).
     *
     * A `false`, **nada muda**: o multijogador continua inteiro em `/lobbies` + `/multisalas`,
     * pelo `MultiMatchRepository`, exactamente como sempre correu. O caminho novo existe todo —
     * `MultiSocketClient`, o redutor de estado, e o segundo ramo de cada acção do
     * `MultiMatchViewModel` — mas nenhuma dessas linhas é alcançada.
     *
     * A `true`, o `MultiMatchViewModel` fala por WebSocket com o servidor, que passa a decidir
     * certo/errado, a pontuação e o vencedor. É a fase 4 que a liga, num build de debug e com
     * dispositivos a sério: nada disto foi exercido contra o servidor real ainda.
     *
     * Os dois caminhos não se misturam — a flag é lida no topo de cada entrada pública e o ramo
     * escolhido devolve logo. Quando a fase 6 apagar o caminho da RTDB, esta flag vai com ele.
     */
    const val MULTIJOGADOR_SERVIDOR = false
}
