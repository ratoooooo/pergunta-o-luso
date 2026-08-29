package com.ratoooooo.perguntaoluso.game

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.google.firebase.auth.FirebaseAuthRecentLoginRequiredException
import com.ratoooooo.perguntaoluso.data.AccountDeletionRepository
import com.ratoooooo.perguntaoluso.data.AuthRepository
import com.ratoooooo.perguntaoluso.ui.FeatureFlags
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

enum class GameScreen { START, LOGIN, REGISTER, RANKING, HISTORY, PROFILE, AVATAR, ACHIEVEMENTS, FRIENDS, FRIEND_SEARCH, FORMAT_SELECT, MULTI_MATCH, CATEGORY_SELECT, CUSTOM_CATEGORIES, MODE_SELECT, QUESTION, PODIUM }

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
    /** categoria -> nº de perguntas, para o cartão do picker. Ausente = ainda não contada. */
    val categoryCounts: Map<String, Int> = emptyMap(),
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
    /** Vidas que restam nas Eliminatórias. `0` nos modos que não eliminam. */
    val vidasRestantes: Int = 0,
    val wonLastGame: Boolean = false,
    /**
     * Subiu de nível nesta partida — comparado entre o perfil antes e depois da agregação.
     * Só serve para celebrar no pódio (som + háptico); nada disto é persistido.
     */
    val subiuDeNivel: Boolean = false,
    /** Conquistas que passaram de bloqueadas a desbloqueadas nesta partida. */
    val novasConquistas: List<String> = emptyList(),
    val multiFormat: com.ratoooooo.perguntaoluso.game.multi.MatchFormat = com.ratoooooo.perguntaoluso.game.multi.MatchFormat.GRUPO,
    val pendingMultiFormat: com.ratoooooo.perguntaoluso.game.multi.MatchFormat? = null,
    val customCategoryQuestions: List<Question>? = null,
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
    /**
     * O que pedir ao servidor da partida assim que o socket abrir. Só é preenchido quando o
     * multijogador corre pelo servidor: aí a sala **não existe** antes de se navegar — nasce da
     * ligação —, ao contrário da RTDB, onde era criada primeiro e o id viajava até aqui.
     */
    val multiPedido: com.ratoooooo.perguntaoluso.game.multi.PedidoDeEntrada? = null,
    /** `false` durante a carência inicial da pergunta — ver [INPUT_GRACE_MS]. */
    val aceitaToques: Boolean = true,
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

/**
 * Carência a seguir a cada pergunta abrir, durante a qual toques em respostas são ignorados.
 *
 * Sem isto, tocar duas vezes no mesmo sítio fazia o segundo toque cair já depois de a pergunta
 * seguinte ter carregado e respondê-la de imediato — o jogador via uma pergunta ser saltada sem
 * ter escolhido nada. 350 ms é maior do que um duplo-toque acidental (~150-250 ms) e curto o
 * suficiente para não estorvar quem responde depressa de propósito.
 */
private const val INPUT_GRACE_MS = 350L
private const val TICK_MS = 100L

/**
 * Perguntas respondidas que dão "vitória" nas Eliminatórias — o mesmo número que antes era
 * preciso sobreviver, agora que a corrida não tem fim. Ver `didWin`.
 */
const val ELIMINATORIAS_MARCO_VITORIA = 20

/**
 * A quantas perguntas do fim do lote se começa a carregar o lote seguinte.
 *
 * Cinco e não uma: carregar `/categorias/{cat}` inteiro leva bem mais do que os 15 s de uma
 * pergunta num emulador lento, e o jogador não pode chegar ao fim do lote com o pedido ainda
 * a voar.
 */
private const val PREFETCH_MARGEM = 5

class GameViewModel(
    private val authRepository: AuthRepository = AuthRepository(),
    private val profileRepository: ProfileRepository = ProfileRepository(),
    private val categoryRepository: CategoryRepository = CategoryRepository(),
    private val questionRepository: QuestionRepository = QuestionRepository(),
    private val scoreRepository: ScoreRepository = ScoreRepository(),
    private val presenceRepository: PresenceRepository = PresenceRepository(),
    private val friendsRepository: FriendsRepository = FriendsRepository(),
    private val challengeRepository: ChallengeRepository = ChallengeRepository(),
    private val deletionRepository: AccountDeletionRepository = AccountDeletionRepository()
) : ViewModel() {

    private val _uiState = MutableStateFlow(GameUiState())
    val uiState: StateFlow<GameUiState> = _uiState.asStateFlow()

    private var timerJob: Job? = null
    private var prefetchJob: Job? = null
    private var presenceUid: String? = null
    private var profileJob: Job? = null

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
            observeOwnProfile(uid)
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

    /**
     * Liga o `/jogadores/{uid}` vivo do jogador. Chamado sempre que o uid muda (arranque,
     * login, registo, sign-out, eliminação de conta) — o mesmo padrão do `friendsJob`, e pela
     * mesma razão: um listener preso ao uid antigo é tão errado como nenhum listener nenhum.
     *
     * A leitura pontual que `finishGame()` faz a seguir a agregar uma partida solo continua a
     * existir — precisa do valor exacto logo após a escrita para comparar antes/depois e
     * detectar subida de nível. Este listener não a substitui; é a rede que apanha **tudo o
     * resto**, sobretudo o multijogador, onde `MultiMatchViewModel` agrega o perfil escrevendo
     * directamente na RTDB sem qualquer aviso a este ViewModel.
     */
    private fun observeOwnProfile(uid: String) {
        profileJob?.cancel()
        profileJob = viewModelScope.launch {
            runCatching {
                profileRepository.observe(uid).collect { profile ->
                    _uiState.value = _uiState.value.copy(profile = profile)
                }
            }
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
                observeOwnProfile(user.uid)
                observeFriends(user.uid)
                observeConvites(user.uid)
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
                val user = authRepository.loginWithEmail(email.trim(), password)
                _uiState.value = _uiState.value.copy(authLoading = false, screen = GameScreen.START)
                refreshProfile()
                observeOwnProfile(user.uid)
                observeFriends(user.uid)
                observeConvites(user.uid)
            } catch (e: Exception) {
                _uiState.value = _uiState.value.copy(authLoading = false, authError = friendlyAuthError(e))
            }
        }
    }

    fun signOut() {
        viewModelScope.launch {
            val novo = runCatching { authRepository.signOutToAnonymous() }.getOrNull()
            _uiState.value = _uiState.value.copy(screen = GameScreen.START, profile = null)
            refreshProfile()
            // Sem isto o listener continuava preso ao uid da conta que acabou de sair — o
            // padrão exacto do friendsJob, aqui aplicado ao perfil.
            novo?.uid?.let { observeOwnProfile(it) }
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
            authRepository.currentUserInfo()?.uid?.let { observeOwnProfile(it) }
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
        salaId: String? = null,
        pedido: com.ratoooooo.perguntaoluso.game.multi.PedidoDeEntrada? = null
    ) {
        stopTimer()
        _uiState.value = _uiState.value.sessionOnly().copy(
            screen = GameScreen.MULTI_MATCH,
            multiFormat = format,
            multiCategory = categoria,
            multiMode = modo,
            multiSalaId = salaId,
            multiPedido = pedido
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
        authRepository.currentUserInfo()?.uid?.let { observeFriends(it) }
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

    private val lastChallengeTimes = mutableMapOf<String, Long>()

    /** Step 1 of challenging a friend: pick categoria + modo through the existing screens. */
    fun startChallenge(friend: FriendRef) {
        val last = lastChallengeTimes[friend.uid] ?: 0L
        val elapsed = System.currentTimeMillis() - last
        if (elapsed < 30_000) {
            val remainSecs = ((30_000 - elapsed) / 1000).toInt()
            _uiState.value = _uiState.value.copy(
                desafioAviso = "Aguarda ${remainSecs}s para enviar outro desafio a este amigo."
            )
            return
        }
        lastChallengeTimes[friend.uid] = System.currentTimeMillis()
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
        // A sala nasce da ligação ao servidor, não antes dela: o desafiante entra já na sala de
        // espera e o convite sai de lá, com o id que o servidor devolveu. Voltar ao ecrã Amigos
        // deixou de ser possível — o servidor larga o lobby quando o socket fecha, por isso quem
        // saísse destruía a sala que acabou de criar e o convite ficava a apontar para o nada.
        goToMultiMatch(
            MatchFormat.ONE_V_ONE, categoria, modo,
            pedido = com.ratoooooo.perguntaoluso.game.multi.PedidoDeEntrada
                .DesafioCriar(friend.uid, friend.nome)
        )
    }

    /** Ticks the sender's countdown and drops the invite from both sides when it expires. */


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
        // As contagens de perguntas sobrevivem à limpeza de estado de propósito: são conteúdo
        // estático (`/categorias` tem `.write: false`) e `loadCategories` corre a cada ida ao
        // picker, por isso deitá-las fora significaria cinco pedidos de rede repetidos por visita.
        categoryCounts = categoryCounts,
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

    fun reloadProfile() {
        val uid = authRepository.currentUserInfo()?.uid ?: return
        viewModelScope.launch {
            val p = profileRepository.loadProfile(uid)
            _uiState.value = _uiState.value.copy(profile = p)
        }
    }

    fun goToCustomCategories() {
        stopTimer()
        val s = _uiState.value
        _uiState.value = s.sessionOnly().copy(
            screen = GameScreen.CUSTOM_CATEGORIES,
            pendingMultiFormat = s.pendingMultiFormat
        )
    }

    fun goToCategorySelect() {
        stopTimer()
        cancelPrefetch()
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
                loadCategoryCounts(categories)
            } catch (e: Exception) {
                _uiState.value = _uiState.value.copy(isLoading = false, error = e.message ?: "Erro a carregar categorias")
            }
        }
    }

    /**
     * Contagens do picker, numa corrotina à parte e **depois** de as categorias já estarem no
     * estado: o ecrã abre com os cartões e o número entra quando chegar. Se ficasse dentro do
     * mesmo `try`, cinco pedidos de rede a mais atrasariam a lista inteira — e uma falha a contar
     * passaria a `error`, deixando o jogador sem categorias por causa de um rótulo.
     *
     * Guardado por categoria já carregada, para uma resposta atrasada não pintar contagens por
     * cima de uma lista entretanto trocada.
     */
    private fun loadCategoryCounts(categories: List<String>) {
        // Já contadas nesta sessão (sobrevivem ao `sessionOnly`) — não vale a pena repetir.
        if (_uiState.value.categoryCounts.keys.containsAll(categories)) return
        viewModelScope.launch {
            val counts = runCatching { categoryRepository.loadQuestionCounts(categories) }
                .getOrDefault(emptyMap())
            if (counts.isEmpty()) return@launch
            if (_uiState.value.categories != categories) return@launch
            _uiState.value = _uiState.value.copy(categoryCounts = counts)
        }
    }

    fun selectCategory(categoria: String) {
        stopTimer()
        val s = _uiState.value
        if (categoria == "COMUNIDADE") {
            _uiState.value = s.sessionOnly().copy(
                screen = GameScreen.CUSTOM_CATEGORIES,
                pendingMultiFormat = s.pendingMultiFormat
            )
            return
        }
        _uiState.value = s.sessionOnly().copy(
            screen = GameScreen.MODE_SELECT,
            pendingMultiFormat = s.pendingMultiFormat,
            selectedCategory = categoria
        )
    }

    fun playCustomCategory(cat: com.ratoooooo.perguntaoluso.data.CustomCategory) {
        val s = _uiState.value
        val questions = cat.perguntas
        if (questions.isEmpty()) return
        _uiState.value = s.sessionOnly().copy(
            screen = GameScreen.MODE_SELECT,
            selectedCategory = cat.titulo,
            customCategoryQuestions = questions,
            pendingMultiFormat = s.pendingMultiFormat
        )
    }

    fun createPrivateRoomForCustomCategory(cat: com.ratoooooo.perguntaoluso.data.CustomCategory, format: com.ratoooooo.perguntaoluso.game.multi.MatchFormat) {
        // É o servidor que lê o quiz de `/categorias_comunitarias` e gera o código — o anfitrião
        // deixou de escolher as perguntas, que era o que lhe permitia inventá-las.
        goToMultiMatch(
            format, cat.titulo, "classico",
            pedido = com.ratoooooo.perguntaoluso.game.multi.PedidoDeEntrada.PrivadaCriar(cat.id)
        )
    }

    fun joinPrivateRoomByCode(code: String) {
        // O formato entra como GRUPO só para o estado inicial ter alguma coisa: de um código de
        // 4 dígitos não se deduz o formato, e é o `sala` do servidor que o corrige.
        goToMultiMatch(
            com.ratoooooo.perguntaoluso.game.multi.MatchFormat.GRUPO, "Comunidade", "classico",
            pedido = com.ratoooooo.perguntaoluso.game.multi.PedidoDeEntrada.PrivadaEntrar(code.trim())
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
        cancelPrefetch()
        val s = _uiState.value
        val categoria = s.selectedCategory
        val customQs = s.customCategoryQuestions
        _uiState.value = s.copy(mode = mode, isLoading = true, error = null)
        viewModelScope.launch {
            try {
                val questions = if (customQs != null && customQs.isNotEmpty()) customQs.take(mode.questionCount) else questionRepository.loadGameQuestions(categoria, mode.questionCount)
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
                    vidasRestantes = mode.vidas,
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
            questionDurationMillis = duration,
            // Fecha a porta a toques herdados da pergunta anterior; reabre em INPUT_GRACE_MS.
            aceitaToques = false
        )
        startTimer(duration)
        prefetchSePerto(index)
        viewModelScope.launch {
            delay(INPUT_GRACE_MS)
            // Só reabre se ainda estamos nesta pergunta e sem resposta dada.
            val s = _uiState.value
            if (s.currentIndex == index && !s.isAnswered) {
                _uiState.value = s.copy(aceitaToques = true)
            }
        }
    }

    /**
     * Carrega mais perguntas em fundo quando o lote das Eliminatórias está a acabar.
     *
     * Só corre num modo sem limite, só uma vez de cada vez ([prefetchJob]) e só quando faltam
     * [PREFETCH_MARGEM] perguntas. As que já saíram nesta corrida são excluídas do lote novo,
     * para não repetir enquanto houver banco por usar.
     */
    private fun prefetchSePerto(index: Int) {
        val state = _uiState.value
        val mode = state.mode ?: return
        if (!mode.semLimiteDePerguntas) return
        if (state.customCategoryQuestions != null) return
        if (index < state.questions.size - PREFETCH_MARGEM) return
        if (prefetchJob?.isActive == true) return

        val categoria = state.selectedCategory
        val perguntasAntes = state.questions.size
        prefetchJob = viewModelScope.launch {
            val jaVistas = _uiState.value.questions.map { it.pergunta }.toSet()
            val novas = runCatching {
                questionRepository.loadGameQuestions(categoria, mode.questionCount)
            }.getOrNull().orEmpty().filter { it.pergunta !in jaVistas }
            if (novas.isEmpty()) return@launch
            // O pedido pode demorar mais do que a partida. Sem esta guarda, um lote a chegar
            // atrasado escrevia `questions` por cima de um ecrã que já não é o do jogo — ou de
            // uma partida nova, noutra categoria.
            val agora = _uiState.value
            if (agora.screen != GameScreen.QUESTION) return@launch
            if (agora.selectedCategory != categoria || agora.mode != mode) return@launch
            if (agora.questions.size < perguntasAntes) return@launch
            _uiState.value = agora.copy(questions = agora.questions + novas)
        }
    }

    /** Corta um lote a caminho. Chamado quando a partida acaba ou o jogador sai do jogo. */
    private fun cancelPrefetch() {
        prefetchJob?.cancel()
        prefetchJob = null
    }

    /**
     * Chegou-se ao fim do lote sem o prefetch ter trazido nada — ou porque falhou, ou porque a
     * categoria já não tem perguntas por usar. Em vez de acabar a corrida de alguém que está a
     * ir bem, **reaproveitam-se as já respondidas, baralhadas de novo**.
     *
     * Repetir uma pergunta é menos mau do que cortar uma sequência boa por falta de banco: é um
     * modo de sobrevivência e a esta altura o jogador já passou de todas pelo menos uma vez.
     */
    private fun reporLoteEContinuar(nextIndex: Int) {
        val state = _uiState.value
        val recicladas = state.questions.shuffled()
        if (recicladas.isEmpty()) {
            finishGame(faced = state.questions.size, eliminated = false)
            return
        }
        _uiState.value = state.copy(questions = state.questions + recicladas)
        beginQuestion(nextIndex)
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
        if (state.isAnswered || !state.aceitaToques) return
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

        if (mode.vidas > 0 && !wasCorrect) {
            val vidas = state.vidasRestantes - 1
            _uiState.value = state.copy(vidasRestantes = vidas)
            if (vidas <= 0) {
                finishGame(faced = faced, eliminated = true)
                return
            }
        }

        val nextIndex = state.currentIndex + 1
        if (nextIndex < _uiState.value.questions.size) {
            beginQuestion(nextIndex)
            return
        }
        // Fim do lote carregado. Num modo sem limite isto não é o fim do jogo: repõe-se o lote e
        // continua-se. Só o Clássico e o Caótico acabam por esgotar as perguntas.
        if (mode.semLimiteDePerguntas) {
            reporLoteEContinuar(nextIndex)
        } else {
            finishGame(faced = _uiState.value.questions.size, eliminated = false)
        }
    }

    /**
     * Vitória nas Eliminatórias.
     *
     * Era "sobreviver às 20". Sem limite de perguntas isso deixou de existir — a corrida acaba
     * **sempre** em eliminação, e o critério antigo tornaria o bónus de XP inalcançável. Passa a
     * ser um marco: chegar às [ELIMINATORIAS_MARCO_VITORIA] perguntas respondidas, o mesmo número
     * que antes era preciso sobreviver.
     */
    private fun didWin(mode: GameMode, correctCount: Int, total: Int, eliminated: Boolean): Boolean = when (mode) {
        GameMode.ELIMINATORIAS -> !eliminated || total >= ELIMINATORIAS_MARCO_VITORIA
        else -> total > 0 && correctCount.toDouble() / total >= WIN_ACCURACY
    }

    private fun finishGame(faced: Int, eliminated: Boolean) {
        stopTimer()
        cancelPrefetch()
        val state = _uiState.value
        val mode = state.mode ?: return
        val won = didWin(mode, state.correctCount, faced, eliminated)
        val isCustom = state.customCategoryQuestions != null
        // Fotografia do perfil ANTES de agregar, para saber o que mudou por causa desta partida.
        val perfilAntes = state.profile
        _uiState.value = state.copy(
            screen = GameScreen.PODIUM,
            eliminated = eliminated,
            wonLastGame = won,
            subiuDeNivel = false,
            novasConquistas = emptyList(),
            isLoading = true
        )
        viewModelScope.launch {
            val uid = authRepository.currentUserInfo()?.uid
            if (uid != null && !isCustom) {
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
            } else if (uid != null && isCustom) {
                runCatching {
                    val reducedXp = state.correctCount * 5
                    profileRepository.addXp(uid, reducedXp)
                }
            }
            val topScores = runCatching { scoreRepository.loadTopScores() }.getOrDefault(emptyList())
            _uiState.value = _uiState.value.copy(topScores = topScores, isLoading = false)

            // O perfil é recarregado aqui (em vez de `refreshProfile()`, que não devolve nada)
            // para se poder comparar com [perfilAntes]. Nível e conquistas são ambos **derivados**
            // dos campos agregados, por isso não há nada de novo a guardar: basta ver o antes e o
            // depois. Um perfil que falhe a carregar deixa tudo a falso — não se inventa festa.
            val perfilDepois = uid?.let { runCatching { profileRepository.loadProfile(it) }.getOrNull() }
            if (perfilDepois != null) {
                _uiState.value = _uiState.value.copy(
                    profile = perfilDepois,
                    subiuDeNivel = perfilAntes != null && perfilDepois.nivel > perfilAntes.nivel,
                    novasConquistas = conquistasNovas(perfilAntes, perfilDepois)
                )
            } else {
                refreshProfile()
            }
        }
    }

    /**
     * Conquistas que estavam bloqueadas em [antes] e passaram a desbloqueadas em [depois].
     *
     * Sem `antes` (primeira partida da sessão, perfil ainda por carregar) devolve lista vazia em
     * vez de tudo o que está desbloqueado — senão um jogador veterano a abrir a app ouviria a
     * fanfarra de todas as conquistas que já tinha há semanas.
     */
    private fun conquistasNovas(antes: Profile?, depois: Profile): List<String> {
        if (antes == null) return emptyList()
        return ACHIEVEMENTS
            .filter { !it.unlocked(antes) && it.unlocked(depois) }
            .map { it.title }
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
