package com.ratoooooo.perguntaoluso.game.multi

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.ratoooooo.perguntaoluso.data.AuthRepository
import com.ratoooooo.perguntaoluso.data.ChallengeRepository
import com.ratoooooo.perguntaoluso.data.GameResult
import com.ratoooooo.perguntaoluso.data.ProfileRepository
import com.ratoooooo.perguntaoluso.data.Question
import com.ratoooooo.perguntaoluso.game.ChaoticEvent
import com.ratoooooo.perguntaoluso.game.Difficulty
import com.ratoooooo.perguntaoluso.game.Scoring
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import kotlin.math.ceil
import kotlin.math.max

import com.ratoooooo.perguntaoluso.data.multi.LobbyData
import com.ratoooooo.perguntaoluso.data.multi.EventoServidor
import com.ratoooooo.perguntaoluso.data.multi.MultiSocketClient

enum class MultiPhase { SEARCHING, MATCHED, IN_GAME, PODIUM, ERROR }

private const val QUESTION_COUNT = 10

/** Carência de toques ao abrir cada pergunta — ver INPUT_GRACE_MS no GameViewModel (Fase 28). */
private const val INPUT_GRACE_MS = 350L
private const val BASE_QUESTION_MILLIS = 15_000L
private const val TICK_MS = 100L

/**
 * Reconexão: 4 tentativas de 2 em 2 segundos = 8 s, dentro da carência de 10 s que o servidor dá
 * antes de contar a queda como desistência. Passar disso seria ficar a tentar entrar numa partida
 * que já foi decidida por walkover do outro lado.
 */
private const val RECONEXAO_TENTATIVAS = 4
private const val RECONEXAO_ESPERA_MS = 2_000L

/**
 * Título do pódio para um lugar em Grupo (1x1 tem a sua própria formulação de Vitória/Derrota).
 *
 * Era um `when (myRank) { 0 -> ...; 1 -> ...; 2 -> ...; else -> "4.º lugar" }` que prendia toda
 * a gente do 4.º lugar para baixo no mesmo texto — inofensivo enquanto o Grupo era 4 fixos, mas
 * o Grupo passou a ir até 10 (ver decisoes/grupo-4-a-10.md) e ninguém actualizou este `when`.
 */
internal fun posicaoLabel(rank: Int): String {
    val posicao = rank + 1
    return if (rank == 0) "$posicao.º lugar!" else "$posicao.º lugar"
}

/**
 * Recolhe um fluxo de listener da RTDB **sem deixar a falha matar a app**.
 *
 * Os `callbackFlow` do [com.ratoooooo.perguntaoluso.data.multi.MultiSocketClient] fecham-se com a
 * excepção quando o listener é cancelado (`onCancelled` → `close(error.toException())`). Um
 * `collect` cru dentro de `viewModelScope.launch` deixa essa excepção sair pelo scope e chegar
 * ao handler por omissão: `FATAL EXCEPTION: main`, com
 * `DatabaseException: This client does not have permission to perform this operation`. Foi
 * provocado a apagar `/multisalas` com clientes lá dentro, mas qualquer negação de leitura na
 * sala (sair de `meta.membrosNomes`, limpeza de estado) segue o mesmo caminho.
 *
 * [CancellationException] é **re-lançada de propósito**: `leave()` e `onCleared()` cancelam estes
 * jobs, e engolir o cancelamento partia a concorrência estruturada — o job ficaria "vivo" para o
 * pai e o listener nunca seria removido.
 */
internal suspend fun <T> coletarListener(
    fluxo: Flow<T>,
    onFalha: (Throwable) -> Unit,
    // `suspend` porque o corpo do `listenToLobby` chama a RTDB de dentro do `collect`
    // (joinRoom/startLobbyRoom). Alargar aqui evita um segundo helper só para esse caso; quem
    // passa uma lambda normal, como o `observeRoom`, não nota diferença.
    onValor: suspend (T) -> Unit
) {
    try {
        fluxo.collect { onValor(it) }
    } catch (e: CancellationException) {
        throw e
    } catch (e: Exception) {
        onFalha(e)
    }
}

/**
 * O que se pede ao servidor **assim que o socket abre**.
 *
 * Existe porque a ligação e o pedido são coisas separadas: numa reconexão o socket reabre e não
 * se pede nada — o servidor devolve-nos sozinho à partida que ficou a decorrer. Sem esta
 * distinção, voltar de uma queda punha o jogador em matchmaking novo em vez de o pôr de volta no
 * duelo que estava a meio.
 */
sealed interface PedidoDeEntrada {
    /** Matchmaking normal, por categoria e modo. */
    data object Aleatoria : PedidoDeEntrada
    /** Sala privada com um quiz da comunidade; o servidor devolve o código. */
    data class PrivadaCriar(val quizId: String) : PedidoDeEntrada
    /** Entrar numa sala privada com os 4 dígitos. O formato só se sabe quando o `sala` chegar. */
    data class PrivadaEntrar(val codigo: String) : PedidoDeEntrada
    /**
     * Desafio direto: cria a sala para o id poder viajar dentro do convite. O nome vem junto
     * porque é o convite que o mostra ao amigo, e quem o envia é agora o `MultiMatchViewModel`.
     */
    data class DesafioCriar(val paraUid: String, val paraNome: String) : PedidoDeEntrada
    /** Aceitar um desafio. Só o convidado entra — o servidor guarda a lista de permitidos. */
    data class DesafioEntrar(val salaId: String) : PedidoDeEntrada
}

data class PlayerLive(
    val uid: String,
    val nome: String,
    val score: Int,
    val team: String?,   // "A"/"B" for 2x2, null otherwise
    val isMe: Boolean,
    val left: Boolean
)

data class TeamResult(val name: String, val players: List<Pair<String, Int>>, val total: Int, val isMine: Boolean, val isWinner: Boolean)
data class RankResult(val nome: String, val score: Int, val isMe: Boolean, val left: Boolean)

data class MultiUiState(
    val format: MatchFormat = MatchFormat.GRUPO,
    val categoria: String = "",
    val modo: String = "classico",
    val phase: MultiPhase = MultiPhase.SEARCHING,
    val openLobbies: List<LobbyData> = emptyList(),
    val currentLobbyId: String? = null,
    val joinedCount: Int = 1,
    val isHost: Boolean = false,
    val myUid: String = "",
    val myName: String = "Tu",
    val perguntas: List<Question> = emptyList(),
    val currentIndex: Int = 0,
    val myScore: Int = 0,
    val myCorrect: Int = 0,
    val players: List<PlayerLive> = emptyList(),
    val currentEvent: ChaoticEvent? = null,
    val selectedOption: String? = null,
    val isAnswered: Boolean = false,
    /** `false` na carência inicial da pergunta — bloqueia toques herdados da anterior. */
    val aceitaToques: Boolean = true,
    val remainingMillis: Long = BASE_QUESTION_MILLIS,
    val durationMillis: Long = BASE_QUESTION_MILLIS,
    // podium
    val resultTitle: String = "",
    val iWon: Boolean = false,
    val walkover: Boolean = false,
    val teams: List<TeamResult> = emptyList(),
    val ranking: List<RankResult> = emptyList(),
    /**
     * Caiu a ligação a meio da partida e estamos dentro da carência do servidor. O lugar ainda é
     * nosso; só deixa de ser se a carência esgotar. Só o caminho do servidor põe isto a `true`.
     */
    val aReconectar: Boolean = false,
    /** O servidor está a drenar para actualizar e recusa partidas novas. Caminho do servidor. */
    val emManutencao: Boolean = false,
    val error: String? = null
) {
    val currentQuestion: Question? get() = perguntas.getOrNull(currentIndex)
}

class MultiMatchViewModel(
    private val authRepository: AuthRepository = AuthRepository(),
    private val profileRepository: ProfileRepository = ProfileRepository(),
    private val challengeRepository: ChallengeRepository = ChallengeRepository()
) : ViewModel() {

    private val _uiState = MutableStateFlow(MultiUiState())
    val uiState: StateFlow<MultiUiState> = _uiState.asStateFlow()

    private lateinit var format: MatchFormat
    private var categoria: String = ""
    private var modo: String = "classico"
    private var aggregated = false


    // ---- caminho do servidor (só vive com FeatureFlags.MULTIJOGADOR_SERVIDOR a true) ----
    private val socket = MultiSocketClient()
    private var socketJob: Job? = null
    private var cronometroServidorJob: Job? = null
    private var toquesJob: Job? = null
    /** Desvio entre o relógio deste dispositivo e o do servidor, relido a cada pergunta. */
    private var desvioDoServidor: Long = 0L
    /** Trava um segundo envio enquanto o veredicto da mesma pergunta não chega. */
    private var respostaEnviada = false
    private var pedido: PedidoDeEntrada = PedidoDeEntrada.Aleatoria
    /** Na reabertura do socket depois de uma queda não se pede entrada nenhuma. */
    private var reconectando = false
    /** O convite de um desafio sai UMA vez, quando a sala existe. Ver [deveEnviarConvite]. */
    private var convitePorEnviar = true

    /**
     * Limpa TODO o estado por-partida antes de começar outra (Fase 28).
     *
     * O `MultiMatchHost` faz `key(restart) { viewModel() }`, mas `viewModel()` resolve pelo
     * `ViewModelStore` da Activity — a `key()` muda a identidade da composição, **não** a do
     * ViewModel. "NOVO JOGO" reutiliza sempre a mesma instância, e estas variáveis privadas
     * sobreviviam de uma partida para a seguinte:
     *
     *  - `gameStarted` ficava `true`, e como o `observeRoom` só arranca com `!gameStarted`,
     *    a segunda partida ficava eternamente em "À procura de adversário";
     *  - `finished` ficava `true`, e como o `leave()` só escreve na RTDB com `!finished`,
     *    a saída deixava de ser publicada — do outro lado o jogador continuava "presente";
     *  - `openLobbiesJob` nunca era cancelado em lado nenhum, acumulando listeners.
     */
    /**
     * Limpa o estado por-partida antes de começar outra.
     *
     * O `MultiMatchHost` faz `key(restart) { viewModel() }`, mas `viewModel()` resolve pelo
     * `ViewModelStore` da Activity — a `key()` muda a identidade da composição, **não** a do
     * ViewModel. "NOVO JOGO" reutiliza sempre a mesma instância, por isso o que sobrevive a uma
     * partida tem de ser limpo à mão (Fase 28). O resto do estado por-partida vive agora no
     * servidor, e o que é local morre com o socket em `resetEstadoDoServidor`.
     */
    private fun resetMatchState() {
        aggregated = false
    }

    /** Matchmaking aleatório: entra na primeira sala compatível, ou cria uma. */
    fun start(format: MatchFormat, categoria: String, modo: String) {
        iniciarNoServidor(format, categoria, modo, PedidoDeEntrada.Aleatoria)
    }

    /** "VER OUTRAS SALAS ABERTAS". Se a sala já não der, o servidor devolve-nos a outra. */
    fun switchLobby(targetLobby: LobbyData) {
        socket.trocarSala(targetLobby.lobbyId)
    }

    /** INICIAR JOGO. O servidor recusa se não formos anfitriões ou se faltar gente. */
    fun forceStartGame() {
        socket.iniciar()
    }

    /**
     * Enters a room that already exists — used by **direct friend challenges**, where the room was
     * created by the challenger and its id travelled inside the invite. No queue involved;
     * everything after this point (lockstep timing, podium, profile aggregation) is identical to
     * random matchmaking.
     */
    /**
     * Aceitar um desafio direto: a sala já existe no servidor e o id veio dentro do convite.
     * Só o convidado lá entra — o servidor guarda a lista de permitidos.
     */
    fun startExisting(format: MatchFormat, categoria: String, modo: String, salaId: String) {
        iniciarNoServidor(format, categoria, modo, PedidoDeEntrada.DesafioEntrar(salaId))
    }

    /** Manda a opção tocada. **Não pontua nada** — quem pontua é o servidor. */
    fun selectAnswer(option: String) {
        responderNoServidor(option)
    }

    /** Sai da sala de espera ou desiste da partida. */
    fun leave() {
        sairDoServidor()
    }

    // -----------------------------------------------------------------------------------------
    // Caminho do servidor da partida (fase 3).
    //
    // Vive inteiro abaixo desta linha e é alcançado só pelos `return` no topo de cada acção
    // pública. Com `FeatureFlags.MULTIJOGADOR_SERVIDOR` a `false` nada aqui corre — o que torna
    // trivial a garantia de que o caminho da RTDB continua a comportar-se exactamente como antes.
    //
    // NUNCA exercido contra o servidor a sério: isso é a fase 4, com dispositivos reais.
    // -----------------------------------------------------------------------------------------

    /** Sala privada com um quiz da comunidade. O código chega no `sala` que o servidor devolve. */
    fun criarSalaPrivada(format: MatchFormat, quizId: String, titulo: String) {
        iniciarNoServidor(format, titulo, "classico", PedidoDeEntrada.PrivadaCriar(quizId))
    }

    /**
     * Entrar numa sala privada com os 4 dígitos.
     *
     * O formato entra como [MatchFormat.GRUPO] só para o estado inicial ter alguma coisa: quem o
     * decide é o servidor, e o `sala` que responde ao código traz o verdadeiro. O cliente não o
     * pode saber de antemão — do código não se deduz se aquilo é um 1x1 ou um Grupo.
     */
    fun entrarPorCodigo(codigo: String) {
        iniciarNoServidor(MatchFormat.GRUPO, "Comunidade", "classico", PedidoDeEntrada.PrivadaEntrar(codigo))
    }

    /**
     * Arranca o caminho do servidor com um pedido explícito. É por aqui que o `GameViewModel`
     * entra para criar um desafio ou uma sala privada — casos em que a sala **só existe depois**
     * de o socket abrir, ao contrário da RTDB, onde era criada antes de se navegar.
     */
    fun iniciarComPedido(format: MatchFormat, categoria: String, modo: String, pedido: PedidoDeEntrada) {
        iniciarNoServidor(format, categoria, modo, pedido)
    }

    private fun iniciarNoServidor(
        format: MatchFormat,
        categoria: String,
        modo: String,
        pedido: PedidoDeEntrada
    ) {
        resetMatchState()
        resetEstadoDoServidor()
        this.format = format
        this.categoria = categoria
        this.modo = modo
        this.pedido = pedido
        _uiState.value = MultiUiState(format = format, categoria = categoria, modo = modo)
        socketJob = viewModelScope.launch { cicloDoSocket() }
    }

    /**
     * Mantém a ligação enquanto houver partida para salvar.
     *
     * O fluxo do socket termina sozinho quando a ligação cai (`Desligado` e fecha). Se nessa
     * altura havia uma partida a decorrer, o redutor põe `aReconectar` e volta-se a ligar — o
     * servidor guardou o lugar durante a carência. À espera de adversário não há nada a
     * recuperar, e sai-se à primeira.
     */
    private suspend fun cicloDoSocket() {
        // O caminho da RTDB começava por `ensureSignedIn()`. O socket precisa de um ID token, e
        // sem sessão o `MultiSocketClient` lança — garantir a sessão aqui mantém o comportamento
        // de sempre em vez de o deixar depender de o AuthGate ter corrido antes.
        runCatching { authRepository.ensureSignedIn() }
        var tentativas = 0
        while (true) {
            // O mesmo `coletarListener` dos listeners da RTDB: um erro no fluxo não pode sair
            // pelo `viewModelScope` e virar `FATAL EXCEPTION: main` (defeitos B/B2/B3).
            coletarListener(
                fluxo = socket.ligar(),
                onFalha = { falhaDoServidor() }
            ) { evento -> aoReceberDoServidor(evento) }

            if (!_uiState.value.aReconectar) return
            if (++tentativas > RECONEXAO_TENTATIVAS) return desistirDaReconexao()
            reconectando = true
            delay(RECONEXAO_ESPERA_MS)
        }
    }

    /**
     * A carência esgotou-se. Do outro lado a partida já foi decidida por walkover, por isso não
     * há para onde voltar — e dizê-lo é melhor do que deixar o ecrã a tentar para sempre.
     */
    private fun desistirDaReconexao() {
        _uiState.value = _uiState.value.copy(
            phase = MultiPhase.ERROR,
            aReconectar = false,
            error = "Perdeste a ligação ao servidor."
        )
    }

    /** O que se pede assim que o socket abre — excepto numa reconexão, em que não se pede nada. */
    private fun pedirEntrada() {
        when (val p = pedido) {
            PedidoDeEntrada.Aleatoria -> socket.procurar(format.id, categoria, modo)
            is PedidoDeEntrada.PrivadaCriar -> socket.privadaCriar(format.id, p.quizId)
            is PedidoDeEntrada.PrivadaEntrar -> socket.privadaEntrar(p.codigo)
            is PedidoDeEntrada.DesafioCriar -> socket.desafioCriar(format.id, categoria, modo, p.paraUid)
            is PedidoDeEntrada.DesafioEntrar -> socket.desafioEntrar(p.salaId)
        }
    }

    private fun resetEstadoDoServidor() {
        socketJob?.cancel(); socketJob = null
        cronometroServidorJob?.cancel(); cronometroServidorJob = null
        toquesJob?.cancel(); toquesJob = null
        socket.fechar()
        desvioDoServidor = 0L
        respostaEnviada = false
        reconectando = false
        convitePorEnviar = true
    }

    /**
     * Os efeitos primeiro, o estado depois. O redutor [aplicarEvento] é puro e não pode enviar
     * mensagens nem arrancar cronómetros; tudo o que é efeito está neste `when`.
     */
    private suspend fun aoReceberDoServidor(evento: EventoServidor) {
        when (evento) {
            EventoServidor.Ligado -> if (reconectando) reconectando = false else pedirEntrada()
            is EventoServidor.Sonda -> socket.sondaOk(evento.s)
            is EventoServidor.Pong -> ajustarRelogio(evento)
            is EventoServidor.Pergunta -> {
                respostaEnviada = false
                // Relê-se o desvio a CADA pergunta, não uma vez por partida: um desvio velho era
                // a principal fonte de dessincronia na versão RTDB, e a lição migra com o resto.
                pedirRelogio()
                iniciarCronometroDoServidor(evento.fimEm)
                libertarToques(evento.indice)
            }
            is EventoServidor.Podio -> cronometroServidorJob?.cancel()
            else -> Unit
        }

        _uiState.value = aplicarEvento(_uiState.value, evento)

        // O convite de um desafio só pode sair depois de a sala existir — ver `deveEnviarConvite`.
        if (evento is EventoServidor.Sala) talvezEnviarConvite()
        // A partida começou: o convite cumpriu o que tinha a fazer e sai de `/convites`.
        if (evento is EventoServidor.Partida) limparConvite()

        // Depois do estado, nunca antes: o uid do jogador vem do `sessao` e vive no `_uiState`.
        if (evento is EventoServidor.Podio) agregarPerfilDoServidor(evento)
    }

    private fun talvezEnviarConvite() {
        val p = pedido
        if (!deveEnviarConvite(p, _uiState.value.currentLobbyId, !convitePorEnviar)) return
        p as PedidoDeEntrada.DesafioCriar
        val eu = _uiState.value.myUid
        val salaId = _uiState.value.currentLobbyId ?: return
        if (eu.isBlank()) return
        convitePorEnviar = false
        viewModelScope.launch {
            runCatching {
                challengeRepository.send(
                    eu, _uiState.value.myName, p.paraUid, p.paraNome,
                    format.id, categoria, modo, salaId
                )
            }
        }
    }

    /**
     * Tira o convite de `/convites` dos dois lados. Corre quando a partida arranca (já não serve)
     * e quando o desafiante sai da sala de espera (equivale a cancelar a procura) — sem isto o
     * convite ficava pendurado a apontar para uma sala que o servidor já largou.
     */
    private fun limparConvite() {
        val p = pedido as? PedidoDeEntrada.DesafioCriar ?: return
        if (convitePorEnviar) return
        convitePorEnviar = true
        val eu = _uiState.value.myUid
        if (eu.isBlank()) return
        viewModelScope.launch { runCatching { challengeRepository.clear(eu, p.paraUid) } }
    }

    /**
     * Agrega o perfil no fim de uma partida do servidor.
     *
     * **Não** passa pelo `aggregateProfile`: aquele lê o `myUid` privado, que só o arranque da
     * RTDB preenche. Foi esse o defeito da fase 4 — o caminho do servidor pedia a agregação com
     * um uid vazio, o `aggregateProfile` devolvia logo, e o perfil não era escrito. Sem erro e
     * sem aviso, com o pódio a anunciar o XP na mesma. Aqui o uid é o que o servidor mandou no
     * `sessao`, que é o mesmo com que ele gravou o `/scores`.
     *
     * `ScoreRepository` não entra: o registo em bruto é gravado pelo servidor, e as rules recusam
     * a um cliente qualquer `formato` que não seja `solo` (fase 2).
     */
    private fun agregarPerfilDoServidor(podio: EventoServidor.Podio) {
        if (aggregated) return
        val uid = _uiState.value.myUid
        val resultado = agregacaoDoServidor(uid, format, modo, categoria, podio) ?: return
        aggregated = true
        viewModelScope.launch {
            runCatching { profileRepository.updateAfterGame(uid, resultado) }
        }
    }

    private fun pedirRelogio() = socket.ping(System.currentTimeMillis())

    /** `desvio = tS - (t0 + t1) / 2` — ver a secção "Relógio" do PROTOCOLO.md. */
    private fun ajustarRelogio(pong: EventoServidor.Pong) {
        val t1 = System.currentTimeMillis()
        desvioDoServidor = pong.tS - (pong.t0 + t1) / 2
    }

    private fun agoraNoServidor(): Long = System.currentTimeMillis() + desvioDoServidor

    /**
     * Conta até ao `fimEm` que o servidor carimbou. **Não avança a pergunta** — quem decide que a
     * pergunta acabou é o servidor, e a seguinte chega por mensagem. Aqui só se desenha o tempo.
     */
    private fun iniciarCronometroDoServidor(fimEm: Long) {
        cronometroServidorJob?.cancel()
        cronometroServidorJob = viewModelScope.launch {
            while (true) {
                val restante = max(0L, fimEm - agoraNoServidor())
                _uiState.value = _uiState.value.copy(remainingMillis = restante)
                if (restante <= 0L) {
                    // Sem resposta até ao fim: marca-se respondido para o ecrã tocar o som de
                    // errado, como o `registerTimeout` faz no caminho da RTDB. A resposta certa
                    // fica por revelar — o servidor só a manda a quem respondeu (ver PROTOCOLO.md).
                    if (!_uiState.value.isAnswered) {
                        _uiState.value = _uiState.value.copy(isAnswered = true)
                    }
                    break
                }
                delay(TICK_MS)
            }
        }
    }

    /** Mesma carência do caminho da RTDB: bloqueia toques herdados da pergunta anterior. */
    private fun libertarToques(indice: Int) {
        toquesJob?.cancel()
        toquesJob = viewModelScope.launch {
            delay(INPUT_GRACE_MS)
            val s = _uiState.value
            if (s.currentIndex == indice && !s.isAnswered) {
                _uiState.value = s.copy(aceitaToques = true)
            }
        }
    }

    /**
     * Envia a opção tocada. **Não pontua nada** — é essa a diferença toda.
     *
     * `isAnswered` fica por marcar de propósito: o ecrã revela as cores e toca o som comparando
     * com `respostaCorreta`, que só chega no veredicto. Marcá-lo já fazia soar "errado" durante
     * o tempo de ida e volta. Quem trava o duplo toque nesse intervalo é [respostaEnviada].
     */
    private fun responderNoServidor(option: String) {
        val s = _uiState.value
        val pergunta = s.currentQuestion ?: return
        if (s.isAnswered || respostaEnviada || !s.aceitaToques) return
        if (option !in pergunta.opcoes) return
        respostaEnviada = true
        _uiState.value = s.copy(selectedOption = option)
        socket.responder(s.currentIndex, option, agoraNoServidor())
    }

    private fun sairDoServidor() {
        if (_uiState.value.phase != MultiPhase.PODIUM) {
            // Sair da sala de espera de um desafio é cancelá-lo: o convite tem de ir com ele.
            if (_uiState.value.phase == MultiPhase.SEARCHING) limparConvite()
            socket.sair()
        }
        resetEstadoDoServidor()
    }

    /**
     * Um listener morto depois do pódio não pode trocar o resultado pelo ecrã de erro — mesma
     * regra do `deveAvisarDeFalhaNoLobby` no caminho da RTDB.
     */
    private fun falhaDoServidor() {
        if (_uiState.value.phase == MultiPhase.PODIUM) return
        cronometroServidorJob?.cancel()
        _uiState.value = _uiState.value.copy(
            phase = MultiPhase.ERROR,
            error = "Perdeste a ligação ao servidor."
        )
    }


    override fun onCleared() {
        socketJob?.cancel()
        cronometroServidorJob?.cancel()
        toquesJob?.cancel()
        socket.fechar()
        super.onCleared()
    }
}
