package com.ratoooooo.perguntaoluso.game

import com.ratoooooo.perguntaoluso.data.Profile

/**
 * O "quase lá" do pódio: o que está mais perto de acontecer a seguir.
 *
 * Calculado sobre o perfil **já agregado** — o mesmo instante em que se sabe se houve subida de
 * nível e conquistas novas. Isso resolve sozinho metade da regra de não repetir: uma conquista
 * desbloqueada nesta partida já não está bloqueada no perfil novo, por isso nunca pode aparecer
 * aqui como objectivo. Para o nível é preciso dizê-lo à mão, daí [subiuDeNivel].
 */
data class ProximoObjetivo(
    /** Ex.: "Só mais 1 vitória para Duelista". */
    val conquista: String?,
    /** Ex.: "250 XP para o Nível 8". */
    val nivel: String?
) {
    val vazio: Boolean get() = conquista == null && nivel == null
}

/**
 * @param p perfil depois da agregação desta partida
 * @param subiuDeNivel se subiu de nível **nesta** partida — nesse caso não se mostra a distância
 *   ao nível seguinte, que acabou de ser celebrada e leria como se não tivesse valido de nada
 */
fun proximoObjetivo(p: Profile, subiuDeNivel: Boolean): ProximoObjetivo {
    // Conquista bloqueada mais perto do limiar. Empate desfaz-se pela ordem de ACHIEVEMENTS,
    // que é estável, para o pódio não trocar de objectivo entre partidas sem nada ter mudado.
    val maisPerto = ACHIEVEMENTS
        .filterNot { it.unlocked(p) }
        .minByOrNull { it.falta(p) }

    val textoConquista = maisPerto?.let {
        val falta = it.falta(p)
        val unidade = if (falta == 1) singular(it.unidade) else it.unidade
        "Só mais $falta $unidade para ${it.title}"
    }

    val estado = p.progressao
    val faltaXp = (estado.xpNecessarioProximoNivel - estado.xpNoNivelAtual).coerceAtLeast(0)
    val textoNivel = if (subiuDeNivel || faltaXp <= 0) null
    else "$faltaXp XP para o Nível ${estado.nivel + 1}"

    return ProximoObjetivo(conquista = textoConquista, nivel = textoNivel)
}

/**
 * "vitórias" → "vitória". Só precisa de dar conta dos plurais que existem em [ACHIEVEMENTS];
 * não é um pluralizador geral e não vale a pena que seja.
 */
private fun singular(unidade: String): String = when (unidade) {
    "vitórias" -> "vitória"
    "jogos" -> "jogo"
    "níveis" -> "nível"
    "respostas seguidas" -> "resposta seguida"
    else -> unidade
}
