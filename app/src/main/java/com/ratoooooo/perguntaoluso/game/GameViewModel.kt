package com.ratoooooo.perguntaoluso.game

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.google.firebase.auth.FirebaseAuthRecentLoginRequiredException
import com.ratoooooo.perguntaoluso.data.AccountDeletionRepository
import com.ratoooooo.perguntaoluso.data.AuthRepository
import com.ratoooooo.perguntaoluso.data.CategoryRepository
import com.ratoooooo.perguntaoluso.data.CONVITE_ACEITE
import com.ratoooooo.perguntaoluso.data.CONVITE_RECUSADO
import com.ratoooooo.perguntaoluso.data.CONVITE_TTL_MS
import com.ratoooooo.perguntaoluso.data.ChallengeRepository
import com.ratoooooo.perguntaoluso.data.Convite
import com.ratoooooo.perguntaoluso.data.ConvitesState
import com.ratoooooo.perguntaoluso.data.FriendRef
import com.ratoooooo.perguntaoluso.data.FriendsRepository
import com.ratoooooo.perguntaoluso.data.FriendsState
import com.ratoooooo.perguntaoluso.data.GameResult
import com.ratoooooo.perguntaoluso.data.MultiMatchRepository
import com.ratoooooo.perguntaoluso.game.multi.MatchFormat
import com.ratoooooo.perguntaoluso.data.PresenceRepository
import com.ratoooooo.perguntaoluso.data.Profile
import com.ratoooooo.perguntaoluso.data.ProfileRepository
import com.ratoooooo.perguntaoluso.data.Question
import com.ratoooooo.perguntaoluso.data.QuestionRepository
import com.ratoooooo.perguntaoluso.data.ScoreEntry
import com.ratoooooo.perguntaoluso.data.ScoreRepository
import com.ratoooooo.perguntaoluso.data.UserInfo
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import kotlin.math.ceil
import kotlin.math.max

enum class GameScreen { START, LOGIN, REGISTER, RANKING, HISTORY, PROFILE, AVATAR, ACHIEVEMENTS, FRIENDS, FRIEND_SEARCH, FORMAT_SELECT, MULTI_MATCH, CATEGORY_SELECT, MODE_SELECT, QUESTION, PODIUM }

/** Correct answers needed (out of total) for a Clássico/Caótico "win". */
private const val WIN_ACCURACY = 0.7

/**
 * Estado do fluxo de eliminação de conta (Perfil). [needsPassword] só fica a `true` quando o
 * Firebase recusa o `delete()` por a sessão ser antiga — aí o diálogo pede a palavra-passe.
 */
data class DeleteAccountUi(
    val open: Boolean = false,
    val working: Boolean = false,
    val needsPassword: Boolean = false,
    val error: String? = null
)

data class GameUiState(
    val screen: GameScreen = GameScreen.START,
    // auth / profile
    val userInfo: UserInfo? = null,
    val profile: Profile? = null,
    val allProfiles: List<Profile> = emptyList(),
    val myScores: List<ScoreEntry> = emptyList(),
    val authLoading: Boolean = false,
    val authError: String? = null,
    // game
    val categories: List<String> = emptyList(),
    val selectedCategory: String = "",
    val mode: GameMode? = null,
    val questions: List<Question> = emptyList(),
    val currentIndex: Int = 0,
    val points: Int = 0,
    val correctCount: Int = 0,
    val streak: Int = 0,
    val maxStreakThisGame: Int = 0,
    val selectedOption: String? = null,
    val isAnswered: Boolean = false,
    val wasTimeout: Boolean = false,
    val lastDelta: Int = 0,
    val currentEvent: ChaoticEvent? = null,
    val remainingMillis: Long = 0L,
    val questionDurationMillis: Long = 1L,
    val eliminated: Boolean = false,
    val wonLastGame: Boolean = false,
    val multiFormat: com.ratoooooo.perguntaoluso.game.multi.MatchFormat = com.ratoooooo.perguntaoluso.game.multi.MatchFormat.GRUPO,
    val pendingMultiFormat: com.ratoooooo.perguntaoluso.game.multi.MatchFormat? = null,
    val multiCategory: String = "",
    val multiMode: String = "classico",
    val topScores: List<ScoreEntry> = emptyList(),
    val playingNow: Int = 0,
    // friends
    val friends: FriendsState = FriendsState(),
    /** uid -> profile, to show avatar + level next to friends/requests. */
    val friendProfiles: Map<String, Profile> = emptyMap(),
    val friendQuery: String = "",
    val friendResults: List<Profile> = emptyList(),
    val friendSearching: Boolean = false,
    val friendSearchDone: Boolean = false,
    /** uids currently online (`/presenca`) — gates the "Desafiar" button. */
    val onlineUids: Set<String> = emptySet(),
    // direct challenges
    val convites: ConvitesState = ConvitesState(),
    /** Friend being challenged while picking categoria/modo, and afterwards while waiting. */
    val desafioPara: FriendRef? = null,
    /** Seconds left on the challenge I sent; 0 = none pending. */
    val desafioSegundos: Int = 0,
    val desafioAviso: String? = null,
    /** Room id when this match came from a direct challenge (null = random matchmaking). */
    val multiSalaId: String? = null,
    val delete: DeleteAccountUi = DeleteAccountUi(),
    val isLoading: Boolean = false,
    val error: String? = null
) {
    val currentQuestion: Question?
        get() = questions.getOrNull(currentIndex)

    val currentDifficulty: Difficulty
        get() = Difficulty.fromId(currentQuestion?.dificuldade)
}

private const val BASE_QUESTION_MILLIS = 15_000L
private const val FEEDBACK_DELAY_MS = 1_000L
private const val TICK_MS = 100L

class GameViewModel(
    private val authRepository: AuthRepository = AuthRepository(),
    private val profileRepository: ProfileRepository = ProfileRepository(),
    private val categoryRepository: CategoryRepository = CategoryRepository(),
    private val questionRepository: QuestionRepository = QuestionRepository(),
    private val scoreRepository: ScoreRepository = ScoreRepository(),
    private val presenceRepository: PresenceRepository = PresenceRepository(),
    private val friendsRepository: FriendsRepository = FriendsRepository(),
    private val challengeRepository: ChallengeRepository = ChallengeRepository(),
    private val matchRepository: MultiMatchRepository = MultiMatchRepository(),
    private val deletionRepository: AccountDeletionRepository = AccountDeletionRepository()
) : ViewModel() {

    private val _uiState = MutableStateFlow(GameUiState())
    val uiState: StateFlow<GameUiState> = _uiState.asStateFlow()

    private var timerJob: Job? = null
    private var presenceUid: String? = null

    init {
        refreshProfile()
        startPresence()
    }

    /** Sign in (if needed), announce presence, and stream the live "a jogar agora" count. */
    private fun startPresence() {
        viewModelScope.launch {
            val uid = runCatching { authRepository.ensureSignedIn().uid }.getOrNull()
                ?: authRepository.currentUserInfo()?.uid ?: return@launch
            presenceUid = uid
            presenceRepository.goOnline(uid)
            if (_uiState.value.userInfo == null) refreshProfile()
            observeFriends(uid)
            observeConvites(uid)
        }
        viewModelScope.launch {
            runCatching {
                presenceRepository.observeCount().collect { count ->
                    _uiState.value = _uiState.value.copy(playingNow = count)
                }
            }
        }
        viewModelScope.launch {
            runCatching {
                presenceRepository.observeOnlineUids().collect { uids ->
                    _uiState.value = _uiState.value.copy(onlineUids = uids)
                }
            }
        }
        // An invite whose sender died would otherwise linger until the next RTDB emission.
        viewModelScope.launch {
            while (true) {
                delay(1_000)
                val cur = _uiState.value.convites
                if (cur.recebidos.isEmpty()) continue
                val vivos = cur.recebidos.filter { serverNow() - it.ts < CONVITE_TTL_MS }
                if (vivos.size != cur.recebidos.size) {
                    _uiState.value = _uiState.value.copy(convites = cur.copy(recebidos = vivos))
                }
            }
        }
    }

    // ---- Auth / profile ----

    private fun refreshProfile() {
        val info = authRepository.currentUserInfo() ?: return
        _uiState.value = _uiState.value.copy(userInfo = info)
        viewModelScope.launch {
            val profile = runCatching { profileRepository.loadProfile(info.uid) }.getOrNull()
            _uiState.value = _uiState.value.copy(profile = profile)
        }
    }

    fun goToLogin() {
        _uiState.value = _uiState.value.copy(screen = GameScreen.LOGIN, authError = null)
    }

    fun goToRegister() {
        _uiState.value = _uiState.value.copy(screen = GameScreen.REGISTER, authError = null)
    }

    fun register(nome: String, email: String, password: String, confirm: String) {
        val validation = validateRegister(nome, email, password, confirm)
        if (validation != null) {
            _uiState.value = _uiState.value.copy(authError = validation)
            return
        }
        _uiState.value = _uiState.value.copy(authLoading = true, authError = null)
        viewModelScope.launch {
            try {
                val user = authRepository.registerWithEmail(email.trim(), password)
                profileRepository.setNome(user.uid, nome.trim())
                _uiState.value = _uiState.value.copy(authLoading = false, screen = GameScreen.START)
                refreshProfile()
            } catch (e: Exception) {
                _uiState.value = _uiState.value.copy(authLoading = false, authError = friendlyAuthError(e))
            }
        }
    }

    fun login(email: String, password: String) {
        if (email.isBlank() || password.isBlank()) {
            _uiState.value = _uiState.value.copy(authError = "Preenche e-mail e palavra-passe")
            return
        }
        _uiState.value = _uiState.value.copy(authLoading = true, authError = null)
        viewModelScope.launch {
            try {
                authRepository.loginWithEmail(email.trim(), password)
                _uiState.value = _uiState.value.copy(authLoading = false, screen = GameScreen.START)
                refreshProfile()
            } catch (e: Exception) {
                _uiState.value = _uiState.value.copy(authLoading = false, authError = friendlyAuthError(e))
            }
        }
    }

    fun signOut() {
        viewModelScope.launch {
            runCatching { authRepository.signOutToAnonymous() }
            _uiState.value = _uiState.value.copy(screen = GameScreen.START, profile = null)
            refreshProfile()
        }
    }

    // ---- Eliminação de conta (exigida pela Play Store) ----

    fun openDeleteAccount() {
        _uiState.value = _uiState.value.copy(delete = DeleteAccountUi(open = true))
    }

    fun dismissDeleteAccount() {
        if (_uiState.value.delete.working) return
        _uiState.value = _uiState.value.copy(delete = DeleteAccountUi())
    }

    /**
     * Purga os dados e só então apaga a conta do Auth — por esta ordem, porque depois do
     * `delete()` o uid deixa de poder escrever seja o que for e tudo o que ficasse para trás
     * ficaria inalcançável para sempre.
     *
     * [password] só vem preenchida na segunda tentativa, depois de o Firebase pedir
     * reautenticação. A purga é idempotente, por isso repeti-la nessa retentativa é inofensivo.
     */
    fun confirmDeleteAccount(password: String?) {
        val uid = _uiState.value.userInfo?.uid ?: authRepository.currentUserInfo()?.uid ?: return
        _uiState.value = _uiState.value.copy(
            delete = _uiState.value.delete.copy(working = true, error = null)
        )
        viewModelScope.launch {
            try {
                if (!password.isNullOrBlank()) authRepository.reauthenticateWithPassword(password)
                deletionRepository.purge(uid)
                authRepository.deleteCurrentUser()
            } catch (e: FirebaseAuthRecentLoginRequiredException) {
                // Os dados já saíram; falta só a conta. O diálogo fica aberto a pedir a
                // palavra-passe em vez de fechar e deixar o utilizador sem saber o que aconteceu.
                _uiState.value = _uiState.value.copy(
                    delete = DeleteAccountUi(
                        open = true,
                        needsPassword = true,
                        error = "Por segurança, confirma a palavra-passe para concluir."
                    )
                )
                return@launch
            } catch (e: Exception) {
                _uiState.value = _uiState.value.copy(
                    delete = _uiState.value.delete.copy(
                        working = false,
                        error = friendlyAuthError(e)
                    )
                )
                return@launch
            }

            // Conta apagada. Volta a uma sessão anónima limpa para a app continuar jogável.
            runCatching { authRepository.ensureSignedIn() }
            _uiState.value = GameUiState(
                screen = GameScreen.START,
                userInfo = authRepository.currentUserInfo()
            )
            refreshProfile()
        }
    }

    private fun validateRegister(nome: String, email: String, password: String, confirm: String): String? = when {
        nome.isBlank() -> "Escolhe um nome de utilizador"
        email.isBlank() -> "Preenche o e-mail"
        password.length < 8 -> "A palavra-passe deve ter pelo menos 8 caracteres"
        password != confirm -> "As palavras-passe não coincidem"
        else -> null
    }

    private fun friendlyAuthError(e: Exception): String {
        val msg = e.message ?: return "Erro de autenticação"
        return when {
            msg.contains("email address is already", true) || msg.contains("already in use", true) ->
                "Este e-mail já está registado"
            msg.contains("badly formatted", true) -> "E-mail inválido"
            msg.contains("password is invalid", true) || msg.contains("INVALID_LOGIN", true) ||
                msg.contains("credential is incorrect", true) -> "E-mail ou palavra-passe incorretos"
            msg.contains("no user record", true) -> "Conta não encontrada"
            else -> msg
        }
    }

    fun goToFormatSelect() {
        stopTimer()
        val s = _uiState.value
        _uiState.value = GameUiState(screen = GameScreen.FORMAT_SELECT, userInfo = s.userInfo, profile = s.profile)
    }

    /**
     * Chosen from the Format screen. Solo passes null; any multiplayer format is stored as
     * "pending" and the same Category -> Mode flow then routes to matchmaking instead of a
     * solo game.
     */
    fun chooseFormat(format: com.ratoooooo.perguntaoluso.game.multi.MatchFormat?) {
        stopTimer()
        _uiState.value = _uiState.value.sessionOnly().copy(
            screen = GameScreen.CATEGORY_SELECT,
            pendingMultiFormat = format,
            isLoading = true
        )
        loadCategories()
    }

    private fun goToMultiMatch(
        format: com.ratoooooo.perguntaoluso.game.multi.MatchFormat,
        categoria: String,
        modo: String,
        salaId: String? = null
    ) {
        stopTimer()
        _uiState.value = _uiState.value.sessionOnly().copy(
            screen = GameScreen.MULTI_MATCH,
            multiFormat = format,
            multiCategory = categoria,
            multiMode = modo,
            multiSalaId = salaId
        )
    }

    // ---- History & Profile ----

    fun goToHistory() {
        val uid = _uiState.value.userInfo?.uid ?: authRepository.currentUserInfo()?.uid
        _uiState.value = _uiState.value.copy(screen = GameScreen.HISTORY, isLoading = true, error = null)
        viewModelScope.launch {
            val scores = if (uid != null) runCatching { scoreRepository.loadMyScores(uid) }.getOrDefault(emptyList()) else emptyList()
            _uiState.value = _uiState.value.copy(myScores = scores, isLoading = false)
        }
    }

    fun goToProfile() {
        _uiState.value = _uiState.value.copy(screen = GameScreen.PROFILE)
        refreshProfile()
    }

    // ---- Friends ----

    private var friendsJob: Job? = null

    /** Streams `/amigos/{uid}` and keeps a profile cache for the uids it mentions. */
    private fun observeFriends(uid: String) {
        friendsJob?.cancel()
        friendsJob = viewModelScope.launch {
            runCatching {
                friendsRepository.observe(uid).collect { state ->
                    _uiState.value = _uiState.value.copy(friends = state)
                    loadFriendProfiles(state)
                }
            }
        }
    }

    private suspend fun loadFriendProfiles(state: FriendsState) {
        val uids = (state.lista + state.recebidos + state.enviados).map { it.uid }.distinct()
        val known = _uiState.value.friendProfiles
        val missing = uids.filter { it !in known }
        if (missing.isEmpty()) return
        val loaded = missing.mapNotNull { u -> runCatching { profileRepository.loadProfile(u) }.getOrNull()?.let { u to it } }
        _uiState.value = _uiState.value.copy(friendProfiles = _uiState.value.friendProfiles + loaded)
    }

    fun goToFriends() {
        _uiState.value = _uiState.value.copy(screen = GameScreen.FRIENDS)
        authRepository.currentUserInfo()?.uid?.let { if (friendsJob == null) observeFriends(it) }
    }

    fun goToFriendSearch() {
        _uiState.value = _uiState.value.copy(
            screen = GameScreen.FRIEND_SEARCH, friendQuery = "", friendResults = emptyList(), friendSearchDone = false
        )
    }

    fun onFriendQueryChange(q: String) {
        _uiState.value = _uiState.value.copy(friendQuery = q)
    }

    fun searchPlayers() {
        val uid = authRepository.currentUserInfo()?.uid ?: return
        val q = _uiState.value.friendQuery
        if (q.isBlank()) return
        _uiState.value = _uiState.value.copy(friendSearching = true)
        viewModelScope.launch {
            val results = runCatching { profileRepository.searchByNome(q, uid) }.getOrDefault(emptyList())
            _uiState.value = _uiState.value.copy(friendResults = results, friendSearching = false, friendSearchDone = true)
        }
    }

    fun sendFriendRequest(to: Profile) {
        val uid = authRepository.currentUserInfo()?.uid ?: return
        val myNome = _uiState.value.profile?.nomeVisivel ?: "Jogador"
        viewModelScope.launch {
            runCatching { friendsRepository.sendRequest(uid, myNome, to.uid, to.nomeVisivel) }
        }
    }

    fun acceptFriend(fromUid: String, fromNome: String) {
        val uid = authRepository.currentUserInfo()?.uid ?: return
        val myNome = _uiState.value.profile?.nomeVisivel ?: "Jogador"
        viewModelScope.launch {
            runCatching { friendsRepository.acceptRequest(uid, myNome, fromUid, fromNome) }
        }
    }

    fun declineFriend(fromUid: String) {
        val uid = authRepository.currentUserInfo()?.uid ?: return
        viewModelScope.launch { runCatching { friendsRepository.declineRequest(uid, fromUid) } }
    }

    fun cancelFriendRequest(toUid: String) {
        val uid = authRepository.currentUserInfo()?.uid ?: return
        viewModelScope.launch { runCatching { friendsRepository.cancelRequest(uid, toUid) } }
    }

    // ---- Direct challenges ----

    private var convitesJob: Job? = null
    private var desafioJob: Job? = null
    private var serverOffset: Long = 0L

    private fun serverNow() = System.currentTimeMillis() + serverOffset

    /** Streams my invite inbox/outbox and reacts to the answer on a challenge I sent. */
    private fun observeConvites(uid: String) {
        convitesJob?.cancel()
        convitesJob = viewModelScope.launch {
            serverOffset = runCatching { challengeRepository.serverTimeOffset() }.getOrDefault(0L)
            runCatching {
                challengeRepository.observe(uid).collect { convites ->
                    // Drop anything already past its TTL (the sender also cleans up on timeout).
                    val vivos = ConvitesState(
                        recebidos = convites.recebidos.filter { serverNow() - it.ts < CONVITE_TTL_MS },
                        enviados = convites.enviados
                    )
                    _uiState.value = _uiState.value.copy(convites = vivos)
                    vivos.enviados.firstOrNull()?.let { onChallengeAnswered(uid, it) }
                }
            }
        }
    }

    /** The invited friend answered a challenge I sent. */
    private fun onChallengeAnswered(myUid: String, convite: Convite) {
        when (convite.estado) {
            CONVITE_ACEITE -> {
                desafioJob?.cancel()
                viewModelScope.launch { runCatching { challengeRepository.clear(myUid, convite.outroUid) } }
                _uiState.value = _uiState.value.copy(desafioPara = null, desafioSegundos = 0, desafioAviso = null)
                goToMultiMatch(MatchFormat.fromId(convite.formato), convite.categoria, convite.modo, convite.salaId)
            }
            CONVITE_RECUSADO -> {
                desafioJob?.cancel()
                viewModelScope.launch { runCatching { challengeRepository.clear(myUid, convite.outroUid) } }
                _uiState.value = _uiState.value.copy(
                    desafioPara = null, desafioSegundos = 0,
                    desafioAviso = "${convite.nome} recusou o desafio."
                )
            }
        }
    }

    /** Step 1 of challenging a friend: pick categoria + modo through the existing screens. */
    fun startChallenge(friend: FriendRef) {
        stopTimer()
        _uiState.value = _uiState.value.sessionOnly().copy(
            screen = GameScreen.CATEGORY_SELECT,
            pendingMultiFormat = MatchFormat.ONE_V_ONE,
            desafioPara = friend,
            desafioAviso = null,
            isLoading = true
        )
        loadCategories()
    }

    /**
     * Step 2: create the room up front (so the invite can carry its id) and open the invite.
     * The challenger stays on the Amigos screen until the friend answers.
     */
    private fun sendChallenge(friend: FriendRef, categoria: String, modo: String) {
        val uid = authRepository.currentUserInfo()?.uid ?: return
        val myNome = _uiState.value.profile?.nomeVisivel ?: "Jogador"
        _uiState.value = _uiState.value.sessionOnly().copy(
            screen = GameScreen.FRIENDS,
            desafioPara = friend,
            desafioSegundos = (CONVITE_TTL_MS / 1000).toInt(),
            desafioAviso = null
        )
        viewModelScope.launch {
            try {
                val questions = questionRepository.loadGameQuestions(categoria, 10)
                val salaId = matchRepository.createRoomDirect(
                    format = MatchFormat.ONE_V_ONE,
                    hostUid = uid,
                    membros = listOf(uid to myNome, friend.uid to friend.nome),
                    questions = questions,
                    categoria = categoria,
                    modo = modo
                )
                challengeRepository.send(uid, myNome, friend.uid, friend.nome, MatchFormat.ONE_V_ONE.id, categoria, modo, salaId)
                startChallengeCountdown(uid, friend)
            } catch (e: Exception) {
                _uiState.value = _uiState.value.copy(
                    desafioPara = null, desafioSegundos = 0,
                    desafioAviso = e.message ?: "Não foi possível enviar o desafio."
                )
            }
        }
    }

    /** Ticks the sender's countdown and drops the invite from both sides when it expires. */
    private fun startChallengeCountdown(myUid: String, friend: FriendRef) {
        desafioJob?.cancel()
        desafioJob = viewModelScope.launch {
            val fim = System.currentTimeMillis() + CONVITE_TTL_MS
            while (true) {
                val restante = fim - System.currentTimeMillis()
                if (restante <= 0) break
                _uiState.value = _uiState.value.copy(desafioSegundos = ((restante + 999) / 1000).toInt())
                delay(500)
            }
            runCatching { challengeRepository.clear(myUid, friend.uid) }
            _uiState.value = _uiState.value.copy(
                desafioPara = null, desafioSegundos = 0,
                desafioAviso = "${friend.nome} não respondeu a tempo."
            )
        }
    }

    fun cancelChallenge() {
        val uid = authRepository.currentUserInfo()?.uid ?: return
        val friend = _uiState.value.desafioPara ?: return
        desafioJob?.cancel()
        viewModelScope.launch { runCatching { challengeRepository.clear(uid, friend.uid) } }
        _uiState.value = _uiState.value.copy(desafioPara = null, desafioSegundos = 0, desafioAviso = null)
    }

    fun dismissChallengeAviso() {
        _uiState.value = _uiState.value.copy(desafioAviso = null)
    }

    /** Invited player accepts: clear the invite and join the room the challenger created. */
    fun acceptConvite(convite: Convite) {
        val uid = authRepository.currentUserInfo()?.uid ?: return
        viewModelScope.launch {
            runCatching { challengeRepository.accept(uid, convite.outroUid) }
            goToMultiMatch(MatchFormat.fromId(convite.formato), convite.categoria, convite.modo, convite.salaId)
        }
    }

    fun declineConvite(convite: Convite) {
        val uid = authRepository.currentUserInfo()?.uid ?: return
        viewModelScope.launch { runCatching { challengeRepository.decline(uid, convite.outroUid) } }
    }

    fun updateNome(nome: String) {
        val uid = authRepository.currentUserInfo()?.uid ?: return
        if (nome.isBlank()) return
        viewModelScope.launch {
            runCatching { profileRepository.setNome(uid, nome.trim()) }
            refreshProfile()
        }
    }

    fun goToAvatar() {
        _uiState.value = _uiState.value.copy(screen = GameScreen.AVATAR)
    }

    fun goToAchievements() {
        _uiState.value = _uiState.value.copy(screen = GameScreen.ACHIEVEMENTS)
    }

    fun updateAvatar(avatarId: String) {
        val uid = authRepository.currentUserInfo()?.uid ?: return
        viewModelScope.launch {
            runCatching { profileRepository.setAvatar(uid, avatarId) }
            refreshProfile()
            _uiState.value = _uiState.value.copy(screen = GameScreen.PROFILE)
        }
    }

    // ---- Ranking ----

    fun goToRanking() {
        _uiState.value = _uiState.value.copy(screen = GameScreen.RANKING, isLoading = true, error = null)
        viewModelScope.launch {
            val profiles = runCatching { profileRepository.loadAllProfiles() }.getOrDefault(emptyList())
            _uiState.value = _uiState.value.copy(allProfiles = profiles, isLoading = false)
        }
    }

    // ---- Navigation ----

    /**
     * A fresh state that keeps everything belonging to the *session* rather than to one screen.
     * The friends/invites/presence listeners only re-emit when their data changes, so a plain
     * `GameUiState(...)` reset would silently drop a pending invite until the next RTDB change.
     */
    private fun GameUiState.sessionOnly() = GameUiState(
        userInfo = userInfo,
        profile = profile,
        playingNow = playingNow,
        friends = friends,
        friendProfiles = friendProfiles,
        friendQuery = friendQuery,
        onlineUids = onlineUids,
        convites = convites,
        desafioPara = desafioPara
    )

    /** Returns to Start, clearing game state but keeping auth/profile. */
    fun backToStart() {
        stopTimer()
        _uiState.value = _uiState.value.sessionOnly().copy(screen = GameScreen.START)
    }

    fun goToCategorySelect() {
        stopTimer()
        val s = _uiState.value
        _uiState.value = s.sessionOnly().copy(
            screen = GameScreen.CATEGORY_SELECT,
            pendingMultiFormat = s.pendingMultiFormat,
            isLoading = true
        )
        loadCategories()
    }

    private fun loadCategories() {
        viewModelScope.launch {
            try {
                val categories = categoryRepository.loadCategories()
                _uiState.value = _uiState.value.copy(categories = categories, isLoading = false)
            } catch (e: Exception) {
                _uiState.value = _uiState.value.copy(isLoading = false, error = e.message ?: "Erro a carregar categorias")
            }
        }
    }

    fun selectCategory(categoria: String) {
        stopTimer()
        val s = _uiState.value
        _uiState.value = s.sessionOnly().copy(
            screen = GameScreen.MODE_SELECT,
            pendingMultiFormat = s.pendingMultiFormat,
            selectedCategory = categoria
        )
    }

    /** Mode chosen — routes to a solo game or to multiplayer matchmaking based on pending format. */
    fun onModeChosen(mode: GameMode) {
        val s = _uiState.value
        val desafio = s.desafioPara
        when {
            // Challenging a friend: same Categoria -> Modo screens, but it opens an invite
            // instead of entering the random-matchmaking queue.
            desafio != null -> sendChallenge(desafio, s.selectedCategory, mode.id)
            s.pendingMultiFormat != null -> goToMultiMatch(s.pendingMultiFormat, s.selectedCategory, mode.id)
            else -> selectMode(mode)
        }
    }

    fun backToCategoryFromMode() {
        goToCategorySelect()
    }

    fun selectMode(mode: GameMode) {
        val categoria = _uiState.value.selectedCategory
        _uiState.value = _uiState.value.copy(mode = mode, isLoading = true, error = null)
        viewModelScope.launch {
            try {
                val questions = questionRepository.loadGameQuestions(categoria, mode.questionCount)
                if (questions.isEmpty()) {
                    _uiState.value = _uiState.value.copy(isLoading = false, error = "Sem perguntas nesta categoria")
                    return@launch
                }
                _uiState.value = _uiState.value.copy(
                    screen = GameScreen.QUESTION,
                    mode = mode,
                    questions = questions,
                    currentIndex = 0,
                    points = 0,
                    correctCount = 0,
                    streak = 0,
                    maxStreakThisGame = 0,
                    selectedOption = null,
                    isAnswered = false,
                    wasTimeout = false,
                    lastDelta = 0,
                    eliminated = false,
                    isLoading = false
                )
                beginQuestion(0)
            } catch (e: Exception) {
                _uiState.value = _uiState.value.copy(isLoading = false, error = e.message ?: "Erro a carregar perguntas")
            }
        }
    }

    // ---- Question lifecycle ----

    private fun beginQuestion(index: Int) {
        val mode = _uiState.value.mode ?: return
        val event = if (mode.hasChaoticEvents) ChaoticEvent.forIndex(index) else null
        val duration = if (event == ChaoticEvent.VELOCIDADE_MAXIMA) BASE_QUESTION_MILLIS / 2 else BASE_QUESTION_MILLIS

        _uiState.value = _uiState.value.copy(
            currentIndex = index,
            currentEvent = event,
            selectedOption = null,
            isAnswered = false,
            wasTimeout = false,
            remainingMillis = duration,
            questionDurationMillis = duration
        )
        startTimer(duration)
    }

    private fun startTimer(duration: Long) {
        stopTimer()
        timerJob = viewModelScope.launch {
            var remaining = duration
            while (remaining > 0) {
                delay(TICK_MS)
                remaining = max(0L, remaining - TICK_MS)
                _uiState.value = _uiState.value.copy(remainingMillis = remaining)
            }
            onTimeout()
        }
    }

    private fun stopTimer() {
        timerJob?.cancel()
        timerJob = null
    }

    fun selectAnswer(option: String) {
        val state = _uiState.value
        val question = state.currentQuestion ?: return
        if (state.isAnswered) return
        stopTimer()
        resolveAnswer(isCorrect = option == question.respostaCorreta, selectedOption = option, wasTimeout = false)
    }

    private fun onTimeout() {
        val state = _uiState.value
        if (state.isAnswered || state.currentQuestion == null) return
        resolveAnswer(isCorrect = false, selectedOption = null, wasTimeout = true)
    }

    private fun resolveAnswer(isCorrect: Boolean, selectedOption: String?, wasTimeout: Boolean) {
        val state = _uiState.value
        val remainingSeconds = ceil(state.remainingMillis / 1000.0).toInt()
        val newStreak = if (isCorrect) state.streak + 1 else 0

        val delta = Scoring.pointsForAnswer(
            isCorrect = isCorrect,
            remainingSeconds = remainingSeconds,
            difficulty = state.currentDifficulty,
            event = state.currentEvent,
            streakAfter = newStreak
        )
        val newPoints = Scoring.clampTotal(state.points + delta)

        _uiState.value = state.copy(
            selectedOption = selectedOption,
            isAnswered = true,
            wasTimeout = wasTimeout,
            streak = newStreak,
            maxStreakThisGame = max(state.maxStreakThisGame, newStreak),
            points = newPoints,
            correctCount = state.correctCount + if (isCorrect) 1 else 0,
            lastDelta = delta
        )

        viewModelScope.launch {
            delay(FEEDBACK_DELAY_MS)
            afterFeedback(isCorrect)
        }
    }

    private fun afterFeedback(wasCorrect: Boolean) {
        val state = _uiState.value
        val mode = state.mode ?: return
        val faced = state.currentIndex + 1

        if (mode.endsOnFirstWrong && !wasCorrect) {
            finishGame(faced = faced, eliminated = true)
            return
        }

        val nextIndex = state.currentIndex + 1
        if (nextIndex < state.questions.size) {
            beginQuestion(nextIndex)
        } else {
            finishGame(faced = state.questions.size, eliminated = false)
        }
    }

    private fun didWin(mode: GameMode, correctCount: Int, total: Int, eliminated: Boolean): Boolean = when (mode) {
        GameMode.ELIMINATORIAS -> !eliminated
        else -> total > 0 && correctCount.toDouble() / total >= WIN_ACCURACY
    }

    private fun finishGame(faced: Int, eliminated: Boolean) {
        stopTimer()
        val state = _uiState.value
        val mode = state.mode ?: return
        val won = didWin(mode, state.correctCount, faced, eliminated)
        _uiState.value = state.copy(
            screen = GameScreen.PODIUM,
            eliminated = eliminated,
            wonLastGame = won,
            isLoading = true
        )
        viewModelScope.launch {
            val uid = authRepository.currentUserInfo()?.uid
            if (uid != null) {
                runCatching {
                    scoreRepository.saveScore(
                        modo = mode.id,
                        categoria = state.selectedCategory,
                        score = state.points,
                        correctCount = state.correctCount,
                        total = faced
                    )
                }
                runCatching {
                    profileRepository.updateAfterGame(
                        uid,
                        GameResult(
                            modo = mode.id,
                            score = state.points,
                            correctCount = state.correctCount,
                            total = faced,
                            won = won,
                            maxStreak = state.maxStreakThisGame,
                            categoria = state.selectedCategory
                        )
                    )
                }
            }
            val topScores = runCatching { scoreRepository.loadTopScores() }.getOrDefault(emptyList())
            _uiState.value = _uiState.value.copy(topScores = topScores, isLoading = false)
            refreshProfile()
        }
    }

    fun playAgain() {
        goToCategorySelect()
    }

    override fun onCleared() {
        stopTimer()
        presenceUid?.let { presenceRepository.goOffline(it) }
        super.onCleared()
    }
}
