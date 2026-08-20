package com.ratoooooo.perguntaoluso.data

import java.time.LocalDate
import java.time.ZoneId

/**
 * Sequência de **dias** seguidos a jogar. Não confundir com a sequência de respostas certas
 * dentro de uma partida (`maxStreak`), que é outra coisa e vive noutro campo.
 *
 * ### O fuso horário é fixo em Europe/Lisbon, de propósito
 *
 * O "dia" desta sequência é o dia civil de Lisboa, guardado como `"AAAA-MM-DD"` e não como
 * timestamp. Razões, por ordem de peso:
 *
 * 1. **Um jogo português.** O conteúdo, a língua e o público são de Portugal; o dia que o jogador
 *    tem em mente é o de Lisboa.
 * 2. **A fronteira do dia é a mesma para toda a gente.** Com fuso do dispositivo, dois jogadores
 *    a jogar ao mesmo instante podiam estar em dias diferentes, e o mesmo jogador podia ver a
 *    sequência mudar por ter atravessado um fuso.
 * 3. **O relógio do dispositivo pode andar para trás** (viajar para oeste, ou mexer nas
 *    definições). Guardar data e não instante torna a comparação trivial, e uma data no futuro
 *    é tratada como "hoje" em vez de partir a conta — ver [avaliar].
 *
 * Custo assumido: um jogador nos Açores ou no Brasil vê o dia virar à meia-noite de Lisboa e não
 * à dele. É consciente e está documentado; se um dia houver público fora de Portugal, isto é o
 * que se muda.
 *
 * ### Isto é validado no cliente
 *
 * Como tudo o resto neste projeto (não há servidor), quem quiser bater a sequência muda a data do
 * telemóvel. As rules só impõem tectos de sanidade. Ver `seguranca/limitacoes-conhecidas.md`.
 */
object StreakDiario {

    val ZONA: ZoneId = ZoneId.of("Europe/Lisbon")

    /** Máximo de protecções que se podem ter guardadas ao mesmo tempo. */
    const val MAX_PROTECOES = 1

    /** De quantos em quantos dias de sequência se repõe uma protecção gasta. */
    const val DIAS_PARA_REPOR_PROTECAO = 7

    /** XP fixo por cada dia novo de sequência. Ver [Progressao] para o porquê de ser fixo. */
    const val XP_POR_DIA = 5

    fun hoje(zona: ZoneId = ZONA): String = LocalDate.now(zona).toString()

    /** Estado guardado, tal como está em `/jogadores/{uid}`. */
    data class Estado(
        val diasSeguidos: Int = 0,
        val ultimoDiaJogado: String = "",
        val maiorSequenciaDias: Int = 0,
        val protecoes: Int = MAX_PROTECOES,
        val protecaoUsadaEm: String = ""
    )

    /** O que muda por causa de uma partida terminada em [hoje]. */
    data class Resultado(
        val estado: Estado,
        /** `true` se hoje foi o primeiro jogo do dia e a sequência avançou. */
        val avancou: Boolean,
        /** `true` se uma protecção foi gasta agora para tapar um dia em falta. */
        val protegeu: Boolean,
        /** XP a somar por causa da sequência. `0` quando o dia já tinha contado. */
        val xp: Int
    )

    /**
     * Decide o novo estado da sequência.
     *
     * Casos, por ordem:
     * - **já jogou hoje** → nada muda, nem XP (senão pagava-se a sequência por partida, não por dia)
     * - **jogou ontem** → sequência +1
     * - **falhou um único dia e tem protecção** → gasta-a, sequência +1, e regista o dia tapado
     * - **tudo o resto** (falhou mais do que um dia, ou falhou um sem protecção, ou é a primeira
     *   vez) → sequência a 1
     *
     * Uma data guardada **no futuro** em relação a [hoje] trata-se como "já jogou hoje": o relógio
     * do dispositivo andou para trás e não há aqui nada de bom a fazer senão não estragar nada.
     */
    fun avaliar(anterior: Estado, hoje: String): Resultado {
        val d = runCatching { LocalDate.parse(hoje) }.getOrNull()
            ?: return Resultado(anterior, avancou = false, protegeu = false, xp = 0)
        val ultimo = anterior.ultimoDiaJogado.takeIf { it.isNotBlank() }
            ?.let { runCatching { LocalDate.parse(it) }.getOrNull() }

        // Já contou hoje (ou o relógio recuou) — não mexer.
        if (ultimo != null && !ultimo.isBefore(d)) {
            return Resultado(anterior, avancou = false, protegeu = false, xp = 0)
        }

        val diasDeIntervalo = ultimo?.let { java.time.temporal.ChronoUnit.DAYS.between(it, d) }

        var protecoes = anterior.protecoes
        var protegeu = false
        val novosDias = when {
            diasDeIntervalo == 1L -> anterior.diasSeguidos + 1
            diasDeIntervalo == 2L && protecoes >= 1 -> {
                protecoes -= 1
                protegeu = true
                anterior.diasSeguidos + 1
            }
            else -> 1
        }

        // Repor a protecção a cada N dias de sequência, para o escudo não ser de uso único —
        // sem isto, quem o gastasse ficava sem rede para sempre e a mecânica morria à primeira.
        if (novosDias % DIAS_PARA_REPOR_PROTECAO == 0 && protecoes < MAX_PROTECOES) {
            protecoes = MAX_PROTECOES
        }

        val diaTapado = if (protegeu) d.minusDays(1).toString() else anterior.protecaoUsadaEm

        return Resultado(
            estado = Estado(
                diasSeguidos = novosDias,
                ultimoDiaJogado = hoje,
                maiorSequenciaDias = maxOf(anterior.maiorSequenciaDias, novosDias),
                protecoes = protecoes,
                protecaoUsadaEm = diaTapado
            ),
            avancou = true,
            protegeu = protegeu,
            xp = XP_POR_DIA
        )
    }

    /**
     * A nota "a tua sequência foi protegida" só faz sentido enquanto for recente. Mostra-se no
     * próprio dia em que a protecção foi gasta e no seguinte; depois disso é ruído.
     */
    fun protecaoRecente(estado: Estado, hoje: String = hoje()): Boolean {
        val usada = estado.protecaoUsadaEm.takeIf { it.isNotBlank() }
            ?.let { runCatching { LocalDate.parse(it) }.getOrNull() } ?: return false
        val d = runCatching { LocalDate.parse(hoje) }.getOrNull() ?: return false
        val dias = java.time.temporal.ChronoUnit.DAYS.between(usada, d)
        return dias in 0..1
    }
}
