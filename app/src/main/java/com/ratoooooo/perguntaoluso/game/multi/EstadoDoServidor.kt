package com.ratoooooo.perguntaoluso.game.multi

import com.ratoooooo.perguntaoluso.data.Question
import com.ratoooooo.perguntaoluso.data.multi.EstadoDePresenca
import com.ratoooooo.perguntaoluso.data.multi.EventoServidor
import com.ratoooooo.perguntaoluso.game.ChaoticEvent

/**
 * Traduz o que o servidor manda para o [MultiUiState] que o ecrã já sabe desenhar.
 *
 * É uma função **pura** — estado dentro, estado fora — pelo mesmo motivo que
 * `estadoAposFalhaAoTrocarDeSala` e `posicaoLabel` são funções de topo neste módulo: dá para as
 * testar sem Firebase, sem socket e sem ViewModel. Os efeitos (cronómetro, som, envio do
 * `sonda_ok`, agregação do perfil) ficam do lado do [MultiMatchViewModel], que é quem os pode ter.
 *
 * O ecrã **não muda**. Isso é um requisito, não um acaso: a fase 3 não pode alterar nada visível,
 * por isso o trabalho está todo em encher os campos que o `MultiMatchScreen` já lê.
 */
internal fun aplicarEvento(estado: MultiUiState, evento: EventoServidor): MultiUiState = when (evento) {

    // Sem estado a mudar: o socket abriu, e é o ViewModel que decide o que pedir a seguir.
    EventoServidor.Ligado -> estado

    is EventoServidor.Sessao -> estado.copy(myUid = evento.uid, myName = evento.nome)

    is EventoServidor.Salas -> estado.copy(openLobbies = evento.salas)

    is EventoServidor.Sala -> estado.copy(
        phase = MultiPhase.SEARCHING,
        currentLobbyId = evento.lobbyId,
        categoria = evento.categoria,
        modo = evento.modo,
        isHost = evento.souAnfitriao,
        joinedCount = evento.membros.size,
        players = evento.membros.map { m ->
            PlayerLive(m.uid, m.nome, score = 0, team = m.equipa, isMe = m.uid == estado.myUid, left = false)
        }
    )

    /**
     * A revelação de "encontrámos adversários", antes da primeira pergunta.
     *
     * `perguntas` nasce aqui com o **tamanho final**, cheia de marcadores de posição, e cada
     * pergunta que chega substitui a sua. Não é adorno: `MultiMatchScreen` mostra
     * "Pergunta X de ${perguntas.size}" e o pódio conta as perguntas pelo mesmo `size`. Uma lista
     * a crescer daria "1 de 1", "2 de 2" — e um total errado no fim.
     */
    is EventoServidor.Partida -> estado.copy(
        phase = MultiPhase.MATCHED,
        joinedCount = evento.membros.size,
        perguntas = List(evento.totalPerguntas) { Question() },
        players = evento.membros.map { m ->
            PlayerLive(m.uid, m.nome, score = 0, team = m.equipa, isMe = m.uid == estado.myUid, left = false)
        }
    )

    /**
     * `respostaCorreta` fica vazia — o servidor só a manda no veredicto, depois de se responder.
     * É a diferença que justifica o servidor todo: na RTDB a resposta certa vinha com a pergunta,
     * ao alcance de quem soubesse ler a base de dados.
     */
    is EventoServidor.Pergunta -> estado.copy(
        phase = MultiPhase.IN_GAME,
        currentIndex = evento.indice,
        perguntas = estado.perguntas.substituir(
            evento.indice,
            Question(
                pergunta = evento.pergunta,
                opcoes = evento.opcoes,
                respostaCorreta = "",
                dificuldade = evento.dificuldade
            )
        ),
        currentEvent = ChaoticEvent.entries.firstOrNull { it.id == evento.evento },
        selectedOption = null,
        isAnswered = false,
        aceitaToques = false,
        durationMillis = evento.duracao,
        remainingMillis = evento.duracao
    )

    /**
     * O veredicto. `isAnswered` passa a `true` **agora** e não quando o jogador tocou: o ecrã
     * revela as cores e toca o som comparando com `respostaCorreta`, que só existe nesta
     * mensagem. Marcá-lo ao toque fazia soar "errado" durante o tempo de ida e volta, e pintava
     * a opção certa de vermelho até a resposta chegar.
     *
     * A pontuação vem toda daqui: o cliente deixou de a calcular.
     */
    is EventoServidor.Resposta -> estado.copy(
        isAnswered = true,
        myScore = evento.total,
        myCorrect = evento.certas,
        perguntas = estado.perguntas.substituir(
            evento.indice,
            estado.perguntas.getOrNull(evento.indice)?.copy(respostaCorreta = evento.respostaCorreta)
                ?: Question(respostaCorreta = evento.respostaCorreta)
        )
    )

    is EventoServidor.Placar -> estado.copy(
        players = estado.players.map { p -> p.copy(score = evento.pontos[p.uid] ?: p.score) }
    )

    // `ausente` ainda não é saída — há carência para voltar. Só `saiu` marca o lugar como vazio,
    // que é o que o ecrã pinta como "— saiu".
    is EventoServidor.Presenca -> estado.copy(
        players = estado.players.map { p ->
            if (p.uid != evento.uid) p else p.copy(left = evento.estado == EstadoDePresenca.SAIU)
        }
    )

    is EventoServidor.Podio -> podio(estado, evento)

    // Efeitos puros do lado do ViewModel: responder à sonda, acertar o relógio.
    is EventoServidor.Sonda, is EventoServidor.Pong -> estado

    is EventoServidor.Aviso -> estado

    is EventoServidor.Erro -> estado.copy(
        phase = MultiPhase.ERROR,
        error = evento.msg ?: mensagemDeErro(evento.codigo)
    )

    is EventoServidor.Desligado -> estado.copy(
        phase = MultiPhase.ERROR,
        error = "Perdeste a ligação ao servidor."
    )

    // Servidor mais recente do que a app. Ignorar é melhor do que rebentar: as mensagens que
    // interessam a esta versão continuam a chegar.
    is EventoServidor.Desconhecido -> estado
}

private fun podio(estado: MultiUiState, evento: EventoServidor.Podio): MultiUiState {
    val meuUid = estado.myUid
    val ranking = evento.ranking.map { RankResult(it.nome, it.pontos, it.uid == meuUid, it.saiu) }
    val minhaEquipa = estado.players.firstOrNull { it.isMe }?.team
    val equipas = evento.equipas.map { e ->
        TeamResult(
            name = "Equipa ${e.nome}",
            players = e.jogadores.map { it.nome to it.pontos },
            total = e.total,
            isMine = e.nome == minhaEquipa,
            isWinner = e.venceu
        )
    }
    return estado.copy(
        phase = MultiPhase.PODIUM,
        ranking = ranking,
        teams = equipas,
        iWon = evento.ganhei,
        walkover = evento.walkover,
        myScore = evento.meuScore,
        myCorrect = evento.minhasCertas,
        resultTitle = tituloDoPodio(estado.format, evento, ranking, equipas)
    )
}

/**
 * O texto grande do pódio, com as mesmas palavras do caminho da RTDB.
 *
 * Está duplicado do `showPodium`, e é de propósito: esta fase não pode tocar uma linha do caminho
 * antigo, e extrair a lógica para as duas partilharem obrigava a mexer nele. A fase 6 apaga o
 * `showPodium` e esta passa a ser a única. Enquanto as duas existirem, mudar o texto obriga a
 * mudar nos dois sítios.
 */
internal fun tituloDoPodio(
    formato: MatchFormat,
    evento: EventoServidor.Podio,
    ranking: List<RankResult>,
    equipas: List<TeamResult>
): String {
    if (evento.walkover && !formato.teamBased) return "Adversário desistiu!"
    if (formato.teamBased) {
        val a = equipas.getOrNull(0)?.total ?: 0
        val b = equipas.getOrNull(1)?.total ?: 0
        return when {
            a == b && !evento.walkover -> "Empate!"
            evento.ganhei -> "A tua equipa ganhou!"
            else -> "A tua equipa perdeu"
        }
    }
    val meuLugar = ranking.indexOfFirst { it.isMe }
    if (formato == MatchFormat.ONE_V_ONE) {
        val meu = ranking.getOrNull(meuLugar)?.score ?: 0
        val outro = ranking.firstOrNull { !it.isMe }?.score ?: 0
        return when {
            meu == outro -> "Empate!"
            evento.ganhei -> "Vitória!"
            else -> "Derrota"
        }
    }
    return posicaoLabel(if (meuLugar >= 0) meuLugar else ranking.size)
}

/** Texto para os códigos que o servidor manda sem mensagem própria. */
private fun mensagemDeErro(codigo: String): String = when (codigo) {
    "em_manutencao" -> "O servidor está a actualizar. Tenta daqui a um minuto."
    "nao_pode_comecar" -> "Ainda não dá para começar."
    "codigo_invalido" -> "Código de sala inválido ou expirado!"
    "sala_cheia" -> "Essa sala já está cheia."
    "desafio_expirado" -> "O desafio já não está disponível."
    else -> "Erro no servidor da partida."
}

/**
 * Troca o elemento [indice], **fazendo a lista crescer** se ela ainda não lá chegar.
 *
 * O crescimento é uma rede de segurança, não o caminho normal: o normal é a `partida` chegar
 * primeiro e abrir a lista com o tamanho final. Mas há um caso em que não chega — voltar a uma
 * partida a decorrer (`aviso: reentraste`), em que o servidor manda `voltou` e a pergunta
 * seguinte, mas não repete a `partida`. Sem isto, a pergunta era descartada e o jogador ficava a
 * olhar para um ecrã vazio até ao pódio. Com isto joga; só o "de quantas" é que fica curto.
 */
private fun List<Question>.substituir(indice: Int, nova: Question): List<Question> {
    if (indice < 0) return this
    val lista = toMutableList()
    while (lista.size <= indice) lista += Question()
    lista[indice] = nova
    return lista
}
