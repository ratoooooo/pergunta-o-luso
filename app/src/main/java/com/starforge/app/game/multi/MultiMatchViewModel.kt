package com.starforge.app.game.multi

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.starforge.app.data.AuthRepository
import com.starforge.app.data.FormResult
import com.starforge.app.data.GameResult
import com.starforge.app.data.MultiMatchRepository
import com.starforge.app.data.MultiRoom
import com.starforge.app.data.ProfileRepository
import com.starforge.app.data.Question
import com.starforge.app.data.QuestionRepository
import com.starforge.app.game.ChaoticEvent
import com.starforge.app.game.Difficulty
import com.starforge.app.game.Scoring
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import kotlin.math.ceil
import kotlin.math.max

enum class MultiPhase { SEARCHING, MATCHED, IN_GAME, PODIUM, ERROR }

private const val QUESTION_COUNT = 10
private const val BASE_QUESTION_MILLIS = 15_000L
private const val TICK_MS = 100L
private const val MATCHED_REVEAL_MS = 2_500L

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
    val joinedCount: Int = 1,
    val myName: String = "Tu",
    val perguntas: List<Question> = emptyList(),
    val currentIndex: Int = 0,
    val myScore: Int = 0,
    val myCorrect: Int = 0,
    val players: List<PlayerLive> = emptyList(),
    val currentEvent: ChaoticEvent? = null,
    val selectedOption: String? = null,
    val isAnswered: Boolean = false,
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
    private val matchRepository: MultiMatchRepository = MultiMatchRepository()
) : ViewModel() {

    private val _uiState = MutableStateFlow(MultiUiState())
    val uiState: StateFlow<MultiUiState> = _uiState.asStateFlow()

    private lateinit var format: MatchFormat
    private var categoria: String = ""
    private var modo: String = "classico"
    private var queueKey: String = ""
    private var myUid: String = ""
    private var salaId: String? = null
    private var serverOffset: Long = 0L
    private var streak: Int = 0
    private var maxStreak: Int = 0
    private var gameStarted = false
    private var finished = false
    private var aggregated = false

    private var observeJob: Job? = null
    private var timerJob: Job? = null

    private val isCaotico get() = modo == "caotico"

    fun start(format: MatchFormat, categoria: String, modo: String) {
        this.format = format
        this.categoria = categoria
        this.modo = modo
        this.queueKey = MatchFormat.queueKey(format, categoria, modo)
        _uiState.value = MultiUiState(format = format, categoria = categoria, modo = modo)
        viewModelScope.launch {
            try {
                val user = authRepository.ensureSignedIn()
                myUid = user.uid
                val nome = runCatching { profileRepository.loadProfile(myUid).nomeVisivel }.getOrDefault("Convidado")
                _uiState.value = _uiState.value.copy(myName = nome)
                val questions = questionRepository.loadGameQuestions(categoria, QUESTION_COUNT)
                serverOffset = runCatching { matchRepository.serverTimeOffset() }.getOrDefault(0L)

                when (val res = matchRepository.joinQueue(format, queueKey, myUid, nome)) {
                    is FormResult.Host -> {
                        val id = matchRepository.createRoom(format, queueKey, myUid, res.membros, questions, categoria, modo)
                        salaId = id
                        matchRepository.setupDisconnect(id, myUid)
                        observeRoom(id)
                    }
                    FormResult.Waiting -> {
                        viewModelScope.launch {
                            matchRepository.listenForMatch(queueKey, myUid).collect { id ->
                                if (salaId == null) {
                                    salaId = id
                                    matchRepository.joinRoom(id, myUid, nome)
                                    matchRepository.setupDisconnect(id, myUid)
                                    matchRepository.clearNotify(queueKey, myUid)
                                    observeRoom(id)
                                }
                            }
                        }
                    }
                }
            } catch (e: Exception) {
                _uiState.value = _uiState.value.copy(phase = MultiPhase.ERROR, error = e.message ?: "Erro no matchmaking")
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
        this.format = format
        this.categoria = categoria
        this.modo = modo
        this.queueKey = ""
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
        if (!gameStarted && room.perguntas.size == QUESTION_COUNT && room.jogadores.size >= format.players) {
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
            durationMillis = duration
        )
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
        if (state.isAnswered) return

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
        if (next < QUESTION_COUNT) beginQuestion(next) else finishGame()
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
                        total = QUESTION_COUNT,
                        won = won,
                        maxStreak = maxStreak,
                        formato = format.id,
                        categoria = categoria
                    )
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
            } else when (myRank) {
                0 -> "1.º lugar!"; 1 -> "2.º lugar"; 2 -> "3.º lugar"; else -> "4.º lugar"
            }
            _uiState.value = _uiState.value.copy(phase = MultiPhase.PODIUM, ranking = ranked, iWon = iWon, resultTitle = title)
            // 1x1/Grupo: win = strictly top score (a tie for 1st is not a win).
            val wonStrict = ranked.firstOrNull()?.isMe == true && (ranked.size < 2 || ranked[0].score > ranked[1].score)
            aggregateProfile(wonStrict)
        }
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
        viewModelScope.launch {
            val id = salaId
            if (id != null && !finished) {
                runCatching { matchRepository.leaveRoom(id, myUid) }
                matchRepository.cancelDisconnect(id, myUid)
            } else if (id == null && myUid.isNotEmpty() && queueKey.isNotEmpty()) {
                runCatching { matchRepository.cancelQueue(queueKey, myUid) }
            }
            observeJob?.cancel()
            timerJob?.cancel()
        }
    }

    override fun onCleared() {
        observeJob?.cancel()
        timerJob?.cancel()
        super.onCleared()
    }
}
