package com.ratoooooo.perguntaoluso.data

/**
 * Nome de patente associado a uma faixa de níveis.
 *
 * O nível numérico já existia ([Progressao]) mas não dizia nada — "Nv 7" não é um estatuto,
 * é um contador. A patente dá-lhe um nome sem introduzir dados novos: é **derivada** do
 * nível, exactamente como o nível é derivado do `xpTotal`, por isso nada é persistido e
 * nada pode ficar dessincronizado.
 *
 * **Tema: a hierarquia de bordo de uma nau portuguesa, seguida da progressão para o mar
 * aberto.** As quatro primeiras são postos reais e pela ordem certa (grumete → marinheiro →
 * piloto → capitão); as duas últimas saem dos Descobrimentos. Encaixa no resto do jogo — o
 * ícone da app é uma caravela, e Caravela e Farol já são dois dos dez símbolos de avatar.
 * Seis patentes é o suficiente para haver degraus visíveis sem virar tabela de patentes
 * militares.
 *
 * As fronteiras foram escolhidas contra a curva de XP real (`75*(n-1)*(n+2)` de XP acumulado
 * para chegar ao nível n) e contra os ~50–150 XP de uma partida:
 *
 * | Patente | Níveis | XP acumulado | ≈ partidas (a 120 XP) |
 * |---|---|---|---|
 * | Grumete     | 1–4   | 0      | 0   |
 * | Marinheiro  | 5–9   | 2 100  | 18  |
 * | Piloto      | 10–14 | 8 100  | 68  |
 * | Capitão     | 15–19 | 17 850 | 149 |
 * | Navegador   | 20–24 | 31 350 | 261 |
 * | Descobridor | 25+   | 48 600 | 405 |
 *
 * A primeira subida (nível 5) chega em menos de vinte partidas, para a mecânica se dar a
 * conhecer cedo, e coincide de propósito com a conquista "Nível 5" que já existia — o mesmo
 * momento passa a valer duas coisas. Descobridor fica deliberadamente longe: é a patente que
 * quase ninguém tem.
 */
enum class Patente(val titulo: String, val nivelMinimo: Int) {
    GRUMETE("Grumete", 1),
    MARINHEIRO("Marinheiro", 5),
    PILOTO("Piloto", 10),
    CAPITAO("Capitão", 15),
    NAVEGADOR("Navegador", 20),
    DESCOBRIDOR("Descobridor", 25);

    companion object {
        /**
         * A patente de um nível — a última cujo [nivelMinimo] o nível já alcançou.
         *
         * `Progressao.estado` nunca devolve nível abaixo de 1, mas um nível inválido cai em
         * GRUMETE em vez de rebentar: um rótulo cosmético não deve poder deitar abaixo um ecrã.
         */
        fun paraNivel(nivel: Int): Patente =
            entries.lastOrNull { nivel >= it.nivelMinimo } ?: GRUMETE

        /** Título directo, para quem só quer o texto. */
        fun tituloParaNivel(nivel: Int): String = paraNivel(nivel).titulo

        /**
         * Nível a que se ganha a próxima patente, ou `null` se já está na última.
         * Fica disponível para um futuro aviso de "faltam N níveis para Piloto".
         */
        fun proximaApos(nivel: Int): Patente? =
            entries.firstOrNull { nivel < it.nivelMinimo }
    }
}
