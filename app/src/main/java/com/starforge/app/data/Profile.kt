package com.starforge.app.data

/** Per-mode aggregated stats. */
data class ModeStats(
    val jogos: Int = 0,
    val pontos: Int = 0,
    val respostasCertas: Int = 0,
    val respostasTotais: Int = 0,
    val vitorias: Int = 0,
    val recorde: Int = 0
) {
    val taxaAcertos: Double
        get() = if (respostasTotais > 0) respostasCertas.toDouble() / respostasTotais else 0.0
}

/** Aggregated player profile stored at /jogadores/{uid}. */
data class Profile(
    val uid: String = "",
    val nome: String = "",
    val jogos: Int = 0,
    val pontos: Int = 0,
    val respostasCertas: Int = 0,
    val respostasTotais: Int = 0,
    val vitorias: Int = 0,
    val recorde: Int = 0,
    val maxStreak: Int = 0,
    val xpTotal: Int = 0,
    val avatar: String = "",
    val partidasPerfeitas: Int = 0,
    val modos: Map<String, ModeStats> = emptyMap(),
    val multiVitorias: Map<String, Int> = emptyMap(),
    val multiJogos: Map<String, Int> = emptyMap(),
    /** category slug -> vitórias in that category. */
    val categoriaVitorias: Map<String, Int> = emptyMap(),
    val categoriaJogos: Map<String, Int> = emptyMap()
) {
    val taxaAcertos: Double
        get() = if (respostasTotais > 0) respostasCertas.toDouble() / respostasTotais else 0.0

    /** Level + within-level XP split, derived from [xpTotal] (never stored). */
    val progressao: Progressao.Estado
        get() = Progressao.estado(xpTotal)

    val nivel: Int
        get() = progressao.nivel

    val temNome: Boolean
        get() = nome.isNotBlank()

    val nomeVisivel: String
        get() = if (temNome) nome else "Convidado"

    /** Up to two uppercase initials for the avatar. */
    val iniciais: String
        get() {
            if (!temNome) return "?"
            val parts = nome.trim().split(Regex("\\s+")).filter { it.isNotBlank() }
            return when {
                parts.isEmpty() -> "?"
                parts.size == 1 -> parts[0].take(2).uppercase()
                else -> (parts[0].take(1) + parts[1].take(1)).uppercase()
            }
        }
}
