package com.ratoooooo.perguntaoluso

import com.ratoooooo.perguntaoluso.data.multi.EquipaNoPodio
import com.ratoooooo.perguntaoluso.data.multi.EstadoDePresenca
import com.ratoooooo.perguntaoluso.data.multi.EventoServidor
import com.ratoooooo.perguntaoluso.data.multi.LugarNoPodio
import com.ratoooooo.perguntaoluso.data.multi.MembroDaSala
import com.ratoooooo.perguntaoluso.game.multi.MatchFormat
import com.ratoooooo.perguntaoluso.game.multi.MultiPhase
import com.ratoooooo.perguntaoluso.game.multi.MultiUiState
import com.ratoooooo.perguntaoluso.game.multi.PlayerLive
import com.ratoooooo.perguntaoluso.game.multi.agregacaoDoServidor
import com.ratoooooo.perguntaoluso.game.multi.PedidoDeEntrada
import com.ratoooooo.perguntaoluso.game.multi.deveEnviarConvite
import com.ratoooooo.perguntaoluso.game.multi.erroEhFatal
import com.ratoooooo.perguntaoluso.game.multi.aplicarEvento
import com.ratoooooo.perguntaoluso.game.multi.tituloDoPodio
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * O redutor que traduz o servidor para o estado que o `MultiMatchScreen` já sabe desenhar.
 *
 * Corre sem socket, sem Firebase e sem ViewModel — é uma função pura, como o
 * `estadoAposFalhaAoTrocarDeSala` e o `posicaoLabel`. O que aqui se prende é sobretudo o que a
 * leitura do ecrã obrigou: o total de perguntas e o momento da revelação.
 */
class EstadoDoServidorTest {

    private val eu = "uid-eu"
    private val base = MultiUiState(format = MatchFormat.ONE_V_ONE, myUid = eu)

    private fun membro(uid: String, equipa: String? = null) = MembroDaSala(uid, "Nome $uid", equipa)

    // --- sala de espera ---

    @Test
    fun `sessao guarda quem eu sou`() {
        val depois = aplicarEvento(MultiUiState(), EventoServidor.Sessao(eu, "Rato", "0.1.0"))
        assertEquals(eu, depois.myUid)
        assertEquals("Rato", depois.myName)
    }

    @Test
    fun `sala marca-me a mim entre os membros`() {
        val depois = aplicarEvento(
            base,
            EventoServidor.Sala("L1", MatchFormat.ONE_V_ONE, "História", "classico", null,
                listOf(membro("outro"), membro(eu)), souAnfitriao = true)
        )
        assertEquals(MultiPhase.SEARCHING, depois.phase)
        assertEquals("L1", depois.currentLobbyId)
        assertEquals(2, depois.joinedCount)
        assertTrue(depois.isHost)
        assertEquals(listOf(false, true), depois.players.map { it.isMe })
    }

    // --- o total de perguntas, que o ecrã lê de perguntas.size ---

    @Test
    fun `partida abre a lista com o tamanho final, nao a crescer`() {
        // MultiMatchScreen mostra "Pergunta X de ${perguntas.size}" e o pódio conta pelo mesmo
        // size. Uma lista a crescer daria "1 de 1", "2 de 2", e um total errado no fim.
        val depois = aplicarEvento(base, EventoServidor.Partida("S1", MatchFormat.ONE_V_ONE, listOf(membro(eu), membro("outro")), 10))
        assertEquals(MultiPhase.MATCHED, depois.phase)
        assertEquals(10, depois.perguntas.size)
    }

    @Test
    fun `pergunta so preenche a sua posicao e o total nao muda`() {
        val comPartida = aplicarEvento(base, EventoServidor.Partida("S1", MatchFormat.ONE_V_ONE, listOf(membro(eu)), 10))
        val depois = aplicarEvento(comPartida, pergunta(indice = 3))
        assertEquals(10, depois.perguntas.size)
        assertEquals(3, depois.currentIndex)
        assertEquals("Capital?", depois.perguntas[3].pergunta)
        assertEquals("", depois.perguntas[4].pergunta)
    }

    // --- a revelação, que o ecrã faz por respostaCorreta ---

    @Test
    fun `uma pergunta sem partida antes ainda assim joga-se`() {
        // Voltar a uma partida a decorrer: o servidor manda `reentraste` e a pergunta seguinte,
        // mas não repete a `partida`. Descartar a pergunta deixava o ecrã vazio até ao pódio.
        val depois = aplicarEvento(base, pergunta(indice = 2))
        assertEquals("Capital?", depois.perguntas[2].pergunta)
        assertEquals(2, depois.currentIndex)
    }

    @Test
    fun `a pergunta chega SEM a resposta certa`() {
        val comPartida = aplicarEvento(base, EventoServidor.Partida("S1", MatchFormat.ONE_V_ONE, listOf(membro(eu)), 10))
        val depois = aplicarEvento(comPartida, pergunta(indice = 0))
        assertEquals("", depois.perguntas[0].respostaCorreta)
        assertFalse(depois.isAnswered)
        assertFalse("a carência de toques tem de recomeçar", depois.aceitaToques)
    }

    @Test
    fun `so o veredicto revela a resposta e marca respondido`() {
        // Marcar `isAnswered` ao toque fazia o ecrã tocar o som de errado e pintar a opção certa
        // de vermelho durante o tempo de ida e volta, porque `respostaCorreta` ainda era "".
        val comPartida = aplicarEvento(base, EventoServidor.Partida("S1", MatchFormat.ONE_V_ONE, listOf(membro(eu)), 10))
        val comPergunta = aplicarEvento(comPartida, pergunta(indice = 0))
        val depois = aplicarEvento(
            comPergunta,
            EventoServidor.Resposta(indice = 0, certa = true, respostaCorreta = "Lisboa", total = 340, certas = 1)
        )
        assertTrue(depois.isAnswered)
        assertEquals("Lisboa", depois.perguntas[0].respostaCorreta)
        assertEquals("as opções não podem ser perdidas na substituição", 2, depois.perguntas[0].opcoes.size)
    }

    @Test
    fun `a pontuacao vem do servidor, nao e calculada aqui`() {
        val depois = aplicarEvento(
            base.copy(myScore = 999, myCorrect = 9),
            EventoServidor.Resposta(0, certa = false, respostaCorreta = "Lisboa", total = 120, certas = 4)
        )
        assertEquals(120, depois.myScore)
        assertEquals(4, depois.myCorrect)
    }

    // --- placar e presenças ---

    @Test
    fun `placar actualiza so quem vem na mensagem`() {
        val comJogadores = base.copy(
            players = listOf(
                PlayerLive(eu, "Eu", 10, null, isMe = true, left = false),
                PlayerLive("outro", "Outro", 20, null, isMe = false, left = false)
            )
        )
        val depois = aplicarEvento(comJogadores, EventoServidor.Placar(mapOf(eu to 300)))
        assertEquals(300, depois.players.first { it.isMe }.score)
        assertEquals("quem não vem na mensagem mantém o que tinha", 20, depois.players.last().score)
    }

    @Test
    fun `perder a ligacao ainda nao e sair`() {
        val comJogadores = base.copy(players = listOf(PlayerLive("outro", "Outro", 0, null, false, false)))
        val ausente = aplicarEvento(comJogadores, EventoServidor.Presenca("outro", EstadoDePresenca.AUSENTE))
        assertFalse("há carência para voltar antes de contar como desistência", ausente.players[0].left)

        val saiu = aplicarEvento(ausente, EventoServidor.Presenca("outro", EstadoDePresenca.SAIU))
        assertTrue(saiu.players[0].left)
    }

    // --- pódio ---

    @Test
    fun `podio marca o meu lugar e traz os numeros do servidor`() {
        val depois = aplicarEvento(
            base.copy(format = MatchFormat.GRUPO),
            podio(
                ganhei = false,
                ranking = listOf(
                    LugarNoPodio("outro", "Outro", 400, false),
                    LugarNoPodio(eu, "Eu", 300, false)
                )
            )
        )
        assertEquals(MultiPhase.PODIUM, depois.phase)
        assertEquals(listOf(false, true), depois.ranking.map { it.isMe })
        assertEquals(275, depois.myScore)
        assertEquals("2.º lugar", depois.resultTitle)
    }

    @Test
    fun `titulo do 1x1 distingue vitoria, derrota e empate`() {
        fun titulo(meus: Int, outros: Int, ganhei: Boolean) = tituloDoPodio(
            MatchFormat.ONE_V_ONE,
            podio(ganhei = ganhei, ranking = emptyList()),
            listOf(RankDeTeste(meus, true), RankDeTeste(outros, false)).map { it.paraRankResult() },
            emptyList()
        )
        assertEquals("Vitória!", titulo(400, 300, ganhei = true))
        assertEquals("Derrota", titulo(300, 400, ganhei = false))
        assertEquals("Empate!", titulo(300, 300, ganhei = false))
    }

    @Test
    fun `walkover sem equipas diz que o adversario desistiu`() {
        val depois = aplicarEvento(
            base,
            podio(ganhei = true, ranking = listOf(LugarNoPodio(eu, "Eu", 0, false)), walkover = true)
        )
        assertEquals("Adversário desistiu!", depois.resultTitle)
        assertTrue(depois.walkover)
        assertTrue(depois.iWon)
    }

    @Test
    fun `2x2 usa o total de equipa e sabe qual e a minha`() {
        val comEquipas = base.copy(
            format = MatchFormat.TWO_V_TWO,
            players = listOf(PlayerLive(eu, "Eu", 0, "B", isMe = true, left = false))
        )
        val depois = aplicarEvento(
            comEquipas,
            podio(
                ganhei = true,
                ranking = emptyList(),
                equipas = listOf(
                    EquipaNoPodio("A", 200, venceu = false, jogadores = emptyList()),
                    EquipaNoPodio("B", 500, venceu = true, jogadores = emptyList())
                )
            )
        )
        assertEquals(listOf("Equipa A", "Equipa B"), depois.teams.map { it.name })
        assertEquals(listOf(false, true), depois.teams.map { it.isMine })
        assertEquals("A tua equipa ganhou!", depois.resultTitle)
    }

    // --- robustez ---

    @Test
    fun `erro de entrada manda para o ecra de erro com texto`() {
        val depois = aplicarEvento(base, EventoServidor.Erro("codigo_invalido", null))
        assertEquals(MultiPhase.ERROR, depois.phase)
        assertTrue(depois.error!!.isNotBlank())
    }

    @Test
    fun `mensagem desconhecida nao mexe em nada`() {
        // Um servidor mais recente do que a app é normal; rebentar por causa disso não é.
        val antes = base.copy(phase = MultiPhase.IN_GAME, myScore = 120)
        assertEquals(antes, aplicarEvento(antes, EventoServidor.Desconhecido("mensagem_do_futuro")))
    }

    // --- ajudas ---

    private fun pergunta(indice: Int) = EventoServidor.Pergunta(
        indice = indice,
        pergunta = "Capital?",
        opcoes = listOf("Lisboa", "Porto"),
        dificuldade = "facil",
        evento = null,
        duracao = 15_000,
        fimEm = 1_000_000
    )

    private fun podio(
        ganhei: Boolean,
        ranking: List<LugarNoPodio>,
        equipas: List<EquipaNoPodio> = emptyList(),
        walkover: Boolean = false
    ) = EventoServidor.Podio(
        walkover = walkover,
        ganhei = ganhei,
        meuScore = 275,
        minhasCertas = 6,
        maxSequencia = 3,
        totalPerguntas = 10,
        ranking = ranking,
        equipas = equipas
    )

    private data class RankDeTeste(val score: Int, val isMe: Boolean) {
        fun paraRankResult() =
            com.ratoooooo.perguntaoluso.game.multi.RankResult("n", score, isMe, false)
    }
}

/**
 * Prende o defeito encontrado na fase 4, com dois emuladores contra o servidor real: a partida
 * acabou, `/scores` recebeu os dois registos, e `/jogadores` não mexeu **um único número** —
 * nem pontos, nem jogos, nem XP —, apesar de o pódio anunciar "+50 XP ganho".
 *
 * A causa era o caminho do servidor pedir a agregação com o `myUid` privado do ViewModel, que só
 * o arranque da RTDB preenche. Vazio, o `aggregateProfile` devolvia logo e não escrevia nada.
 */
class AgregacaoDoServidorTest {

    private val podio = EventoServidor.Podio(
        walkover = false, ganhei = true, meuScore = 340, minhasCertas = 7,
        maxSequencia = 4, totalPerguntas = 10, ranking = emptyList(), equipas = emptyList()
    )

    @org.junit.Test
    fun `sem uid nao ha agregacao — era isto que falhava em silencio`() {
        org.junit.Assert.assertNull(
            agregacaoDoServidor("", MatchFormat.ONE_V_ONE, "classico", "Cultura Geral", podio)
        )
    }

    @org.junit.Test
    fun `o uid vem da sessao do servidor, e os numeros do podio`() {
        // O uid tem de ser o que o servidor mandou — o mesmo com que ele gravou o /scores.
        val estado = aplicarEvento(MultiUiState(), EventoServidor.Sessao("uid-do-servidor", "Rato", "0.1.0"))
        val r = agregacaoDoServidor(estado.myUid, MatchFormat.ONE_V_ONE, "classico", "Cultura Geral", podio)!!

        org.junit.Assert.assertEquals(340, r.score)
        org.junit.Assert.assertEquals(7, r.correctCount)
        org.junit.Assert.assertEquals(10, r.total)
        org.junit.Assert.assertEquals(4, r.maxStreak)
        org.junit.Assert.assertTrue(r.won)
        org.junit.Assert.assertEquals("1x1", r.formato)
        org.junit.Assert.assertEquals("Cultura Geral", r.categoria)
    }
}

/**
 * O que a fase 5 acrescenta: os formatos que faltavam, as entradas por código e por desafio, e os
 * dois estados que só o servidor tem (reconexão e manutenção).
 */
class FormatosEEstadosDoServidorTest {

    private val eu = "uid-eu"
    private val base = MultiUiState(myUid = eu)
    private fun membro(uid: String, equipa: String? = null) = MembroDaSala(uid, "Nome $uid", equipa)

    // --- 2x2 ---

    @Test
    fun `2x2 recebe as equipas do servidor, nao as inventa`() {
        val depois = aplicarEvento(
            base,
            EventoServidor.Partida(
                "S1", MatchFormat.TWO_V_TWO,
                listOf(membro("a1", "A"), membro(eu, "A"), membro("b1", "B"), membro("b2", "B")), 10
            )
        )
        assertEquals(MatchFormat.TWO_V_TWO, depois.format)
        assertEquals(listOf("A", "A", "B", "B"), depois.players.map { it.team })
        assertEquals("A", depois.players.first { it.isMe }.team)
    }

    @Test
    fun `2x2 walkover - quem sai leva a equipa dele abaixo, mesmo com mais pontos`() {
        // O critério de docs/vault/decisoes/criterio-vitoria.md: num walkover ganha a equipa que
        // ficou, e não a que tem mais pontos. Quem decide é o servidor; aqui prende-se que o
        // cliente mostra o que ele decidiu em vez de recalcular pelo total.
        val comEquipas = aplicarEvento(
            base,
            EventoServidor.Partida("S1", MatchFormat.TWO_V_TWO,
                listOf(membro("a1", "A"), membro("a2", "A"), membro(eu, "B"), membro("b2", "B")), 10)
        )
        val depois = aplicarEvento(
            comEquipas,
            EventoServidor.Podio(
                walkover = true, ganhei = true, meuScore = 40, minhasCertas = 1, maxSequencia = 1,
                totalPerguntas = 10, ranking = emptyList(),
                equipas = listOf(
                    EquipaNoPodio("A", 900, venceu = false, jogadores = emptyList()),
                    EquipaNoPodio("B", 40, venceu = true, jogadores = emptyList())
                )
            )
        )
        val a = depois.teams.first { it.name == "Equipa A" }
        val b = depois.teams.first { it.name == "Equipa B" }
        assertTrue("a equipa A tinha muito mais pontos", a.total > b.total)
        assertTrue("e mesmo assim perde — saiu um dos seus", b.isWinner)
        assertFalse(a.isWinner)
        assertTrue(b.isMine)
        assertEquals("A tua equipa ganhou!", depois.resultTitle)
    }

    // --- Grupo ---

    @Test
    fun `Grupo aceita de 4 a 10 e conta os que la estao`() {
        val membros = (1..7).map { membro("j$it") } + membro(eu)
        val depois = aplicarEvento(
            base,
            EventoServidor.Sala("L1", MatchFormat.GRUPO, "História", "classico", null, membros, souAnfitriao = false)
        )
        assertEquals(MatchFormat.GRUPO, depois.format)
        assertEquals(8, depois.joinedCount)
        assertEquals(4, depois.format.minPlayers)
        assertEquals(10, depois.format.players)
    }

    @Test
    fun `Grupo com 10 no podio mostra os 10 pela ordem do servidor`() {
        val ranking = (1..10).map { LugarNoPodio("j$it", "Jogador $it", 1000 - it * 10, false) }
        val depois = aplicarEvento(
            base.copy(format = MatchFormat.GRUPO, myUid = "j7"),
            EventoServidor.Podio(false, false, 930, 5, 2, 10, ranking, emptyList())
        )
        assertEquals(10, depois.ranking.size)
        assertEquals("a ordem é a do servidor", ranking.map { it.nome }, depois.ranking.map { it.nome })
        assertEquals("7.º lugar", depois.resultTitle)
    }

    // --- sala privada por código ---

    @Test
    fun `entrar por codigo aprende o formato com o servidor`() {
        // Do código de 4 dígitos não se deduz o formato. O cliente arranca em Grupo por omissão e
        // é o `sala` que corrige — sem isto, um 1x1 por código era pontuado como Grupo no pódio.
        val depois = aplicarEvento(
            base.copy(format = MatchFormat.GRUPO),
            EventoServidor.Sala("L9", MatchFormat.ONE_V_ONE, "Quiz do Zé", "classico", "4242",
                listOf(membro(eu)), souAnfitriao = true)
        )
        assertEquals(MatchFormat.ONE_V_ONE, depois.format)
        assertEquals("Quiz do Zé", depois.categoria)
    }

    // --- triagem de erros ---

    @Test
    fun `nao_pode_comecar NAO derruba a partida`() {
        // O temporizador de auto-arranque vive na composição e dispara `iniciar` aos 60 s mesmo
        // quando o servidor já arrancou sozinho. Com o erro a ser fatal, uma sala de Grupo que
        // enchesse atirava toda a gente para o ecrã de erro logo a seguir a começar.
        assertFalse(erroEhFatal("nao_pode_comecar", MultiPhase.SEARCHING))
        val depois = aplicarEvento(base, EventoServidor.Erro("nao_pode_comecar", null))
        assertEquals(MultiPhase.SEARCHING, depois.phase)
    }

    @Test
    fun `recusas normais a meio do jogo nao trocam a partida por um erro`() {
        val aJogar = base.copy(phase = MultiPhase.IN_GAME, myScore = 120)
        for (codigo in listOf("tarde_demais", "ja_respondeu", "pergunta_errada", "opcao_invalida")) {
            val depois = aplicarEvento(aJogar, EventoServidor.Erro(codigo, null))
            assertEquals("$codigo derrubou a partida", MultiPhase.IN_GAME, depois.phase)
            assertEquals(120, depois.myScore)
        }
    }

    @Test
    fun `erros de entrada continuam a ser fatais, mas so a procurar sala`() {
        for (codigo in listOf("codigo_invalido", "sala_cheia", "quiz_invalido", "desafio_expirado")) {
            assertTrue(codigo, erroEhFatal(codigo, MultiPhase.SEARCHING))
            assertFalse("$codigo a meio do jogo", erroEhFatal(codigo, MultiPhase.IN_GAME))
        }
    }

    @Test
    fun `nada derruba o podio ja mostrado`() {
        val podio = base.copy(phase = MultiPhase.PODIUM, resultTitle = "Vitória!")
        assertEquals(podio, aplicarEvento(podio, EventoServidor.Erro("falha_interna", null)))
        assertEquals(podio, aplicarEvento(podio, EventoServidor.Desligado("1006")))
    }

    // --- manutenção ---

    @Test
    fun `manutencao tem estado proprio, nao e o ecra de erro`() {
        val depois = aplicarEvento(base, EventoServidor.Erro("em_manutencao", null))
        assertTrue(depois.emManutencao)
        assertEquals("não é erro — é uma janela de menos de um minuto", MultiPhase.SEARCHING, depois.phase)
    }

    @Test
    fun `entrar numa sala limpa a manutencao`() {
        val emManutencao = base.copy(emManutencao = true)
        val depois = aplicarEvento(
            emManutencao,
            EventoServidor.Sala("L1", MatchFormat.ONE_V_ONE, "H", "classico", null, listOf(membro(eu)), true)
        )
        assertFalse(depois.emManutencao)
    }

    // --- reconexão ---

    @Test
    fun `cair a meio do jogo pede reconexao em vez de deitar a partida fora`() {
        val aJogar = base.copy(phase = MultiPhase.IN_GAME, myScore = 340)
        val depois = aplicarEvento(aJogar, EventoServidor.Desligado("1006"))
        assertTrue(depois.aReconectar)
        assertEquals("a partida continua no ecrã por baixo da faixa", MultiPhase.IN_GAME, depois.phase)
        assertEquals(340, depois.myScore)
    }

    @Test
    fun `cair a procura de adversario nao tem nada a salvar`() {
        val depois = aplicarEvento(base.copy(phase = MultiPhase.SEARCHING), EventoServidor.Desligado("401"))
        assertEquals(MultiPhase.ERROR, depois.phase)
        assertFalse(depois.aReconectar)
    }

    @Test
    fun `voltar limpa a faixa de reconexao`() {
        val caido = base.copy(phase = MultiPhase.IN_GAME, aReconectar = true)
        assertFalse(aplicarEvento(caido, EventoServidor.Ligado).aReconectar)
        assertFalse(aplicarEvento(caido, EventoServidor.Aviso("reentraste")).aReconectar)
    }
}

/**
 * A inversão que o servidor obriga: na RTDB a sala era criada primeiro e o convite levava o id;
 * no servidor o id só nasce quando o socket abre, por isso o convite sai depois — e de dentro da
 * sala, porque quem sair dela larga o lobby e deixa o convite a apontar para o nada.
 */
class ConviteDoDesafioTest {

    private val desafio = PedidoDeEntrada.DesafioCriar("uid-amigo", "Amigo")

    @org.junit.Test
    fun `o convite espera pela sala`() {
        org.junit.Assert.assertFalse("sem sala não há id para pôr no convite",
            deveEnviarConvite(desafio, lobbyId = null, jaEnviado = false))
        org.junit.Assert.assertFalse(deveEnviarConvite(desafio, lobbyId = "", jaEnviado = false))
        org.junit.Assert.assertTrue(deveEnviarConvite(desafio, lobbyId = "L1", jaEnviado = false))
    }

    @org.junit.Test
    fun `o convite sai uma vez so`() {
        // O `sala` chega a cada mudança do lobby. Sem esta guarda, cada entrada e saída de
        // jogador reenviava o convite ao amigo.
        org.junit.Assert.assertFalse(deveEnviarConvite(desafio, lobbyId = "L1", jaEnviado = true))
    }

    @org.junit.Test
    fun `so o desafio envia convite`() {
        for (outro in listOf(
            PedidoDeEntrada.Aleatoria,
            PedidoDeEntrada.PrivadaCriar("quiz1"),
            PedidoDeEntrada.PrivadaEntrar("4242"),
            PedidoDeEntrada.DesafioEntrar("L1")
        )) {
            org.junit.Assert.assertFalse(
                outro.toString(),
                deveEnviarConvite(outro, lobbyId = "L1", jaEnviado = false)
            )
        }
    }
}
