package com.ratoooooo.perguntaoluso.game.multi

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.ratoooooo.perguntaoluso.data.AuthRepository
import com.ratoooooo.perguntaoluso.data.FormResult
import com.ratoooooo.perguntaoluso.data.GameResult
import com.ratoooooo.perguntaoluso.data.MultiMatchRepository
import com.ratoooooo.perguntaoluso.data.MultiRoom
import com.ratoooooo.perguntaoluso.data.ProfileRepository
import com.ratoooooo.perguntaoluso.data.Question
import com.ratoooooo.perguntaoluso.data.QuestionRepository
import com.ratoooooo.perguntaoluso.game.ChaoticEvent
import com.ratoooooo.perguntaoluso.game.Difficulty
import com.ratoooooo.perguntaoluso.game.Scoring
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import kotlin.math.ceil
import kotlin.math.max

import com.ratoooooo.perguntaoluso.data.ScoreRepository
import com.ratoooooo.perguntaoluso.data.LobbyData

enum class MultiPhase { SEARCHING, MATCHED, IN_GAME, PODIUM, ERROR }

private const val QUESTION_COUNT = 10

/** Carência de toques ao abrir cada pergunta — ver INPUT_GRACE_MS no GameViewModel (Fase 28). */
private const val INPUT_GRACE_MS = 350L
private const val BASE_QUESTION_MILLIS = 15_000L
private const val TICK_MS = 100L
private const val MATCHED_REVEAL_MS = 2_500L

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
    val error: String? = null
) {
    val currentQuestion: Question? get() = perguntas.getOrNull(currentIndex)
}

class MultiMatchViewModel(
    private val authRepository: AuthRepository = AuthRepository(),
    private val profileRepository: ProfileRepository = ProfileRepository(),
    private val questionRepository: QuestionRepository = QuestionRepository(),
    private val matchRepository: MultiMatchRepository = MultiMatchRepository(),
    private val scoreRepository: ScoreRepository = ScoreRepository()
) : ViewModel() {

    private val _uiState = MutableStateFlow(MultiUiState())
    val uiState: StateFlow<MultiUiState> = _uiState.asStateFlow()

    private lateinit var format: MatchFormat
    private var categoria: String = ""
    private var modo: String = "classico"
    private var myUid: String = ""
    private var salaId: String? = null
    private var currentLobbyId: String? = null
    private var serverOffset: Long = 0L
    private var streak: Int = 0
    private var maxStreak: Int = 0
    private var gameStarted = false
    private var finished = false
    private var aggregated = false

    private var observeJob: Job? = null
    private var timerJob: Job? = null
    private var lobbyJob: Job? = null
    private var openLobbiesJob: Job? = null

    private val isCaotico get() = modo == "caotico"

    /**
     * Quantas perguntas esta partida tem mesmo. No matchmaking aleatório são sempre
     * [QUESTION_COUNT]; numa sala privada são as do quiz da comunidade escolhido. Usar o valor
     * real evita terminar cedo, indexar fora dos limites, e gravar em `/scores` um `total` que
     * não corresponde ao que foi jogado.
     */
    private val totalPerguntas: Int
        get() = _uiState.value.perguntas.size.takeIf { it > 0 } ?: QUESTION_COUNT

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
    private fun resetMatchState() {
        lobbyJob?.cancel(); lobbyJob = null
        observeJob?.cancel(); observeJob = null
        timerJob?.cancel(); timerJob = null
        openLobbiesJob?.cancel(); openLobbiesJob = null
        salaId = null
        currentLobbyId = null
        gameStarted = false
        finished = false
        aggregated = false
        streak = 0
        maxStreak = 0
    }

    fun start(format: MatchFormat, categoria: String, modo: String) {
        resetMatchState()
        this.format = format
        this.categoria = categoria
        this.modo = modo
        _uiState.value = MultiUiState(format = format, categoria = categoria, modo = modo)
        viewModelScope.launch {
            try {
                val user = authRepository.ensureSignedIn()
                myUid = user.uid
                val nome = runCatching { profileRepository.loadProfile(myUid).nomeVisivel }.getOrDefault("Convidado")
                _uiState.value = _uiState.value.copy(myUid = myUid, myName = nome)
                serverOffset = runCatching { matchRepository.serverTimeOffset() }.getOrDefault(0L)

                openLobbiesJob = viewModelScope.launch {
                    matchRepository.observeOpenLobbies(format.id).collect { lobbies ->
                        _uiState.value = _uiState.value.copy(openLobbies = lobbies)
                    }
                }

                val (lobbyId, isHost) = matchRepository.findOrCreateLobby(format, categoria, modo, myUid, nome)
                currentLobbyId = lobbyId
                _uiState.value = _uiState.value.copy(currentLobbyId = lobbyId, isHost = isHost)
                listenToLobby(lobbyId)
            } catch (e: Exception) {
                _uiState.value = _uiState.value.copy(phase = MultiPhase.ERROR, error = e.message ?: "Erro no matchmaking")
            }
        }
    }

    private fun listenToLobby(lobbyId: String) {
        lobbyJob?.cancel()
        lobbyJob = viewModelScope.launch {
            matchRepository.observeLobby(format.id, lobbyId).collect { lobby ->
                if (lobby == null || lobby.estado == "cancelled") {
                    if (salaId == null) {
                        _uiState.value = _uiState.value.copy(phase = MultiPhase.ERROR, error = "A sala de espera foi cancelada")
                    }
                    return@collect
                }

                val count = lobby.membros.size
                val playerLives = lobby.membros.map { (uid, name) ->
                    PlayerLive(uid = uid, nome = name, score = 0, team = null, isMe = uid == myUid, left = false)
                }
                _uiState.value = _uiState.value.copy(
                    categoria = lobby.categoria,
                    modo = lobby.modo,
                    currentLobbyId = lobbyId,
                    joinedCount = count,
                    players = playerLives,
                    isHost = (lobby.hostUid == myUid)
                )

                if (lobby.estado == "started" && lobby.salaId != null && salaId == null) {
                    salaId = lobby.salaId
                    matchRepository.joinRoom(lobby.salaId, myUid, _uiState.value.myName)
                    matchRepository.setupDisconnect(lobby.salaId, myUid)
                    observeRoom(lobby.salaId)
                } else if (lobby.estado == "waiting" && lobby.hostUid == myUid && count >= format.players && salaId == null) {
                    val questions = questionRepository.loadGameQuestions(lobby.categoria, QUESTION_COUNT)
                    val id = matchRepository.startLobbyRoom(format.id, lobbyId, myUid, lobby.membros, questions, lobby.categoria, lobby.modo)
                    salaId = id
                    matchRepository.setupDisconnect(id, myUid)
                    observeRoom(id)
                }
            }
        }
    }

    fun switchLobby(targetLobby: LobbyData) {
        if (targetLobby.lobbyId == currentLobbyId || targetLobby.membros.any { it.first == myUid }) return
        val oldLobbyId = currentLobbyId
        viewModelScope.launch {
            lobbyJob?.cancel()
            if (oldLobbyId != null) {
                runCatching { matchRepository.leaveLobby(format.id, oldLobbyId, myUid) }
            }
            this@MultiMatchViewModel.categoria = targetLobby.categoria
            this@MultiMatchViewModel.modo = targetLobby.modo
            this@MultiMatchViewModel.currentLobbyId = targetLobby.lobbyId
            _uiState.value = _uiState.value.copy(
                categoria = targetLobby.categoria,
                modo = targetLobby.modo,
                currentLobbyId = targetLobby.lobbyId,
                isHost = (targetLobby.hostUid == myUid)
            )
            val joined = matchRepository.joinLobbyById(format.id, targetLobby.lobbyId, myUid, _uiState.value.myName)
            if (joined) {
                listenToLobby(targetLobby.lobbyId)
            } else {
                val (newId, isHost) = matchRepository.findOrCreateLobby(format, categoria, modo, myUid, _uiState.value.myName)
                this@MultiMatchViewModel.currentLobbyId = newId
                _uiState.value = _uiState.value.copy(currentLobbyId = newId, isHost = isHost)
                listenToLobby(newId)
            }
        }
    }

    fun forceStartGame() {
        val lobbyId = currentLobbyId ?: return
        if (salaId != null || !_uiState.value.isHost) return
        viewModelScope.launch {
            try {
                val questions = questionRepository.loadGameQuestions(categoria, QUESTION_COUNT)
                val currentPlayers = _uiState.value.players.map { it.uid to it.nome }
                val id = matchRepository.startLobbyRoom(format.id, lobbyId, myUid, currentPlayers, questions, categoria, modo)
                salaId = id
                matchRepository.setupDisconnect(id, myUid)
                observeRoom(id)
            } catch (e: Exception) {
                _uiState.value = _uiState.value.copy(phase = MultiPhase.ERROR, error = e.message ?: "Erro ao iniciar sala")
            }
        }
    }

    /**
     * Enters a room that already exists — used by **direct friend challenges**, where the room was
     * created by the challenger and its id travelled inside the invite. No queue involved;
     * everything after this point (lockstep timing, podium, profile aggregation) is identical to
     * random matchmaking.
     */
    fun startExisting(format: MatchFormat, categoria: String, modo: String, salaId: String) {
        resetMatchState()
        this.format = format
        this.categoria = categoria
        this.modo = modo
        _uiState.value = MultiUiState(format = format, categoria = categoria, modo = modo)
        viewModelScope.launch {
            try {
                val user = authRepository.ensureSignedIn()
                myUid = user.uid
                val nome = runCatching { profileRepository.loadProfile(myUid).nomeVisivel }.getOrDefault("Convidado")
                _uiState.value = _uiState.value.copy(myName = nome)
                serverOffset = runCatching { matchRepository.serverTimeOffset() }.getOrDefault(0L)
                this@MultiMatchViewModel.salaId = salaId
                matchRepository.joinRoom(salaId, myUid, nome)
                matchRepository.setupDisconnect(salaId, myUid)
                observeRoom(salaId)
            } catch (e: Exception) {
                _uiState.value = _uiState.value.copy(phase = MultiPhase.ERROR, error = e.message ?: "Erro ao entrar na sala")
            }
        }
    }

    private fun observeRoom(id: String) {
        observeJob?.cancel()
        observeJob = viewModelScope.launch {
            matchRepository.observeRoom(id).collect { room -> onRoom(room) }
        }
    }

    private fun teamOf(room: MultiRoom, uid: String): String? =
        room.equipas.entries.firstOrNull { it.value.contains(uid) }?.key

    private fun onRoom(room: MultiRoom) {
        val live = room.membros.map { uid ->
            val p = room.jogadores[uid]
            PlayerLive(
                uid = uid,
                nome = p?.nome ?: room.membrosNomes[uid] ?: "Jogador",
                score = p?.pontuacao ?: 0,
                team = teamOf(room, uid),
                isMe = uid == myUid,
                left = p?.estado == "off" || p?.desistiu == true
            )
        }
        _uiState.value = _uiState.value.copy(perguntas = room.perguntas, players = live, joinedCount = room.jogadores.size)

        // All present + questions loaded → reveal the match, then start.
        // Fase 25: era `room.perguntas.size == QUESTION_COUNT`. O matchmaking aleatório escreve
        // sempre 10 perguntas, mas uma sala privada usa o quiz da comunidade escolhido, que pode
        // ter qualquer número — com 1 ou 7 perguntas a condição nunca era verdadeira e a sala
        // ficava eternamente em "À procura de adversário". `meta` é escrita de uma só vez
        // (create-once), por isso `isNotEmpty()` já garante que as perguntas chegaram inteiras.
        // Fase 30: era `>= format.players`, que no Grupo é 10. O arranque manual ("INICIAR
        // JOGO", e o auto-arranque aos 60 s) cria a sala com os jogadores que lá estão — quatro,
        // por exemplo — e depois este portão exigia dez e nunca deixava entrar: a sala existia,
        // o lobby ficava `started`, e toda a gente continuava presa em "À procura de jogadores".
        // O número certo é quantos membros a sala tem MESMO, que o anfitrião fixou ao criá-la.
        val esperados = room.membros.size.takeIf { it > 0 } ?: format.players
        if (!gameStarted && room.perguntas.isNotEmpty() && room.jogadores.size >= esperados) {
            gameStarted = true
            _uiState.value = _uiState.value.copy(phase = MultiPhase.MATCHED)
            viewModelScope.launch {
                delay(MATCHED_REVEAL_MS)
                beginQuestion(0)
            }
            return
        }

        if (gameStarted && !finished) {
            if (format.teamBased) {
                val leaver = room.membros.firstOrNull { uid ->
                    val p = room.jogadores[uid]
                    (p?.estado == "off" || p?.desistiu == true) && room.pontuacoes[uid] == null
                }
                if (leaver != null) { finishTeamWalkover(room, leaver); return }
            }
            val active = room.membros.filter { room.jogadores[it]?.estado != "off" && room.jogadores[it]?.desistiu != true }

            // Fase 28: o walkover só existia no ramo `teamBased`, ou seja, apenas em 2x2. Num 1x1
            // o adversário podia sair e o jogador que ficava continuava a responder sozinho até à
            // décima pergunta, com o outro ainda no marcador — era o comportamento reportado.
            // O 1x1 autónomo tinha esta deteção; perdeu-se ao ser dobrado no MultiMatch.
            //
            // A condição é genérica em vez de específica do 1x1: se sobrar UM só jogador activo
            // numa sala que tinha mais do que um, a partida não tem como continuar. No Grupo, sair
            // um de quatro deixa três activos e o jogo segue — que é o comportamento documentado.
            if (!format.teamBased && room.membros.size > 1 && active.size == 1 &&
                active.first() == myUid && !room.pontuacoes.containsKey(myUid)
            ) {
                finishSoloWalkover(); return
            }

            if (active.isNotEmpty() && active.all { room.pontuacoes.containsKey(it) }) { showPodium(room); return }
            maybeAdvance(room)
        }
    }

    private fun beginQuestion(index: Int) {
        val id = salaId ?: return
        val event = if (isCaotico) ChaoticEvent.forIndex(index) else null
        val duration = if (event == ChaoticEvent.VELOCIDADE_MAXIMA) BASE_QUESTION_MILLIS / 2 else BASE_QUESTION_MILLIS
        _uiState.value = _uiState.value.copy(
            phase = MultiPhase.IN_GAME,
            currentIndex = index,
            currentEvent = event,
            selectedOption = null,
            isAnswered = false,
            remainingMillis = duration,
            durationMillis = duration,
            aceitaToques = false
        )
        viewModelScope.launch {
            delay(INPUT_GRACE_MS)
            val s = _uiState.value
            if (s.currentIndex == index && !s.isAnswered) {
                _uiState.value = s.copy(aceitaToques = true)
            }
        }
        viewModelScope.launch {
            // Refresh the server-clock offset each question so a stale one-time value can't drift.
            serverOffset = runCatching { matchRepository.serverTimeOffset() }.getOrDefault(serverOffset)
            val startMs = runCatching { matchRepository.syncQuestionStart(id, index) }.getOrDefault(serverNow())
            startTimer(startMs, duration)
        }
    }

    private fun serverNow(): Long = System.currentTimeMillis() + serverOffset

    private fun startTimer(startMs: Long, duration: Long) {
        timerJob?.cancel()
        timerJob = viewModelScope.launch {
            while (true) {
                val remaining = max(0L, duration - (serverNow() - startMs))
                _uiState.value = _uiState.value.copy(remainingMillis = remaining)
                if (remaining <= 0L) {
                    if (!_uiState.value.isAnswered) registerTimeout()
                    maybeAdvance(null)
                    break
                }
                delay(TICK_MS)
            }
        }
    }

    fun selectAnswer(option: String) {
        val state = _uiState.value
        val question = state.currentQuestion ?: return
        val id = salaId ?: return
        if (state.isAnswered || !state.aceitaToques) return

        val isCorrect = option == question.respostaCorreta
        val remainingSeconds = ceil(state.remainingMillis / 1000.0).toInt()
        val newStreak = if (isCorrect) streak + 1 else 0
        streak = newStreak
        if (newStreak > maxStreak) maxStreak = newStreak
        val delta = Scoring.pointsForAnswer(isCorrect, remainingSeconds, Difficulty.fromId(question.dificuldade), state.currentEvent, newStreak)
        val newScore = Scoring.clampTotal(state.myScore + delta)
        val newCorrect = state.myCorrect + if (isCorrect) 1 else 0

        _uiState.value = state.copy(selectedOption = option, isAnswered = true, myScore = newScore, myCorrect = newCorrect)
        viewModelScope.launch {
            runCatching { matchRepository.writeAnswer(id, myUid, state.currentIndex, isCorrect, newScore, newCorrect) }
            maybeAdvance(null)
        }
    }

    private fun registerTimeout() {
        val state = _uiState.value
        val id = salaId ?: return
        streak = 0
        // Caótico tudo_ou_nada penalises a wrong/timeout answer.
        val delta = Scoring.pointsForAnswer(false, 0, Difficulty.FACIL, state.currentEvent, 0)
        val newScore = Scoring.clampTotal(state.myScore + delta)
        _uiState.value = state.copy(isAnswered = true, selectedOption = null, myScore = newScore)
        viewModelScope.launch {
            runCatching { matchRepository.writeAnswer(id, myUid, state.currentIndex, false, newScore, state.myCorrect) }
        }
    }

    private fun maybeAdvance(room: MultiRoom?) {
        val state = _uiState.value
        if (!gameStarted || finished || state.phase != MultiPhase.IN_GAME) return
        val index = state.currentIndex
        val timerExpired = state.remainingMillis <= 0L
        val allAnswered = if (room != null) {
            val active = room.membros.filter { room.jogadores[it]?.estado != "off" && room.jogadores[it]?.desistiu != true }
            active.isNotEmpty() && active.all { uid ->
                if (uid == myUid) state.isAnswered else room.jogadores[uid]?.answered?.contains(index) == true
            }
        } else false
        if (!allAnswered && !timerExpired) return
        timerJob?.cancel()
        val next = index + 1
        if (next < totalPerguntas) beginQuestion(next) else finishGame()
    }

    private fun finishGame() {
        if (finished) return
        val id = salaId ?: return
        viewModelScope.launch {
            runCatching { matchRepository.writeFinal(id, myUid, _uiState.value.myScore) }
        }
    }

    /**
     * Folds this match into the local player's aggregated profile — exactly once per game,
     * on the same device that played it (RTDB rules only allow writing your own uid). Uses the
     * same [GameResult]/[ProfileRepository] path and XP formula as Solo. [won] follows each
     * format's podium criterion: team-level for 2x2, strictly-top individual for 1x1/Grupo.
     */
    private fun aggregateProfile(won: Boolean) {
        if (aggregated || myUid.isEmpty()) return
        aggregated = true
        val st = _uiState.value
        viewModelScope.launch {
            runCatching {
                profileRepository.updateAfterGame(
                    myUid,
                    GameResult(
                        modo = modo,
                        score = st.myScore,
                        correctCount = st.myCorrect,
                        total = totalPerguntas,
                        won = won,
                        maxStreak = maxStreak,
                        formato = format.id,
                        categoria = categoria
                    )
                )
            }
            runCatching {
                scoreRepository.saveScore(
                    modo = modo,
                    categoria = categoria,
                    score = st.myScore,
                    correctCount = st.myCorrect,
                    total = totalPerguntas,
                    formato = format.id
                )
            }
        }
    }

    private fun showPodium(room: MultiRoom) {
        if (finished) return
        finished = true
        timerJob?.cancel()
        salaId?.let { matchRepository.cancelDisconnect(it, myUid) }

        fun score(uid: String) = room.pontuacoes[uid] ?: room.jogadores[uid]?.pontuacao ?: 0
        fun nome(uid: String) = room.jogadores[uid]?.nome ?: room.membrosNomes[uid] ?: "Jogador"

        if (format.teamBased) {
            val a = room.equipas["A"].orEmpty(); val b = room.equipas["B"].orEmpty()
            val totalA = a.sumOf { score(it) }; val totalB = b.sumOf { score(it) }
            val myTeam = teamOf(room, myUid)
            val aWins = totalA > totalB; val bWins = totalB > totalA
            val teams = listOf(
                TeamResult("Equipa A", a.map { nome(it) to score(it) }, totalA, myTeam == "A", aWins),
                TeamResult("Equipa B", b.map { nome(it) to score(it) }, totalB, myTeam == "B", bWins)
            )
            val iWon = (myTeam == "A" && aWins) || (myTeam == "B" && bWins)
            val title = when {
                totalA == totalB -> "Empate!"
                iWon -> "A tua equipa ganhou!"
                else -> "A tua equipa perdeu"
            }
            _uiState.value = _uiState.value.copy(phase = MultiPhase.PODIUM, teams = teams, iWon = iWon, resultTitle = title)
            aggregateProfile(iWon)   // 2x2: team-level win (strict; a tie is not a win)
        } else {
            val ranked = room.membros
                .map { uid ->
                    val left = room.jogadores[uid]?.estado == "off" || room.jogadores[uid]?.desistiu == true
                    RankResult(nome(uid), score(uid), uid == myUid, left)
                }
                .sortedWith(compareByDescending<RankResult> { !it.left }.thenByDescending { it.score })
            val myRank = ranked.indexOfFirst { it.isMe }
            val iWon = myRank == 0
            // 1x1 is head-to-head → Vitória/Derrota wording; larger groups → placement.
            val title = if (format == MatchFormat.ONE_V_ONE) {
                val mine = ranked.getOrNull(myRank)?.score ?: 0
                val other = ranked.firstOrNull { !it.isMe }?.score ?: 0
                when { mine == other -> "Empate!"; iWon -> "Vitória!"; else -> "Derrota" }
            } else posicaoLabel(myRank)
            _uiState.value = _uiState.value.copy(phase = MultiPhase.PODIUM, ranking = ranked, iWon = iWon, resultTitle = title)
            // 1x1/Grupo: win = strictly top score (a tie for 1st is not a win).
            val wonStrict = ranked.firstOrNull()?.isMe == true && (ranked.size < 2 || ranked[0].score > ranked[1].score)
            aggregateProfile(wonStrict)
        }
    }

    /**
     * Sobrou só este jogador numa sala sem equipas (1x1, ou Grupo esvaziado). Fecha a partida
     * já, em vez de o deixar a responder sozinho contra ninguém.
     */
    private fun finishSoloWalkover() {
        if (finished) return
        finished = true
        timerJob?.cancel()
        val id = salaId
        viewModelScope.launch {
            if (id != null) {
                runCatching { matchRepository.writeFinal(id, myUid, _uiState.value.myScore) }
                runCatching { matchRepository.cancelDisconnect(id, myUid) }
            }
        }
        _uiState.value = _uiState.value.copy(
            phase = MultiPhase.PODIUM, walkover = true, iWon = true,
            resultTitle = "Adversário desistiu!"
        )
        aggregateProfile(true)
    }

    private fun finishTeamWalkover(room: MultiRoom, leaverUid: String) {
        if (finished) return
        finished = true
        timerJob?.cancel()
        salaId?.let { matchRepository.cancelDisconnect(it, myUid) }
        val leaverTeam = teamOf(room, leaverUid)
        val myTeam = teamOf(room, myUid)
        val iWon = myTeam != null && myTeam != leaverTeam
        _uiState.value = _uiState.value.copy(
            phase = MultiPhase.PODIUM, walkover = true, iWon = iWon,
            resultTitle = if (iWon) "A tua equipa ganhou!" else "A tua equipa perdeu"
        )
        aggregateProfile(iWon)   // 2x2 walkover: present team wins by desistência
    }

    fun leave() {
        // Lidos e limpos de forma SÍNCRONA: o `MultiMatchHost` faz `vm.leave(); restart++`, e o
        // `start()` que se segue chamaria `resetMatchState()` antes de esta corrotina correr —
        // a saída acabava por ser escrita com `salaId` já a null, ou seja, nunca era publicada.
        val id = salaId
        val lobbyId = currentLobbyId
        val jaTerminou = finished
        val fmt = format.id
        val uid = myUid
        salaId = null
        currentLobbyId = null
        lobbyJob?.cancel(); lobbyJob = null
        observeJob?.cancel(); observeJob = null
        timerJob?.cancel(); timerJob = null
        openLobbiesJob?.cancel(); openLobbiesJob = null

        viewModelScope.launch {
            if (id != null && !jaTerminou) {
                runCatching { matchRepository.leaveRoom(id, uid) }
                runCatching { matchRepository.cancelDisconnect(id, uid) }
            } else if (lobbyId != null && !jaTerminou) {
                runCatching { matchRepository.leaveLobby(fmt, lobbyId, uid) }
            }
        }
    }

    override fun onCleared() {
        lobbyJob?.cancel()
        observeJob?.cancel()
        timerJob?.cancel()
        openLobbiesJob?.cancel()
        super.onCleared()
    }
}
