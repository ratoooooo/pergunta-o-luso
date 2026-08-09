package com.ratoooooo.perguntaoluso.game

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.lifecycle.viewmodel.compose.viewModel
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import com.ratoooooo.perguntaoluso.ui.theme.Cream
import com.ratoooooo.perguntaoluso.ui.theme.Ink

@Composable
fun GameApp(viewModel: GameViewModel = viewModel()) {
    val state by viewModel.uiState.collectAsState()

    Box(Modifier.fillMaxSize()) {
    when {
        state.error != null -> CenteredMessage(state.error ?: "")

        state.isLoading && state.screen != GameScreen.PODIUM && state.screen != GameScreen.RANKING &&
            state.screen != GameScreen.HISTORY ->
            CenteredMessage("A carregar...")

        state.screen == GameScreen.START -> StartScreen(
            userInfo = state.userInfo,
            profile = state.profile,
            playingNow = state.playingNow,
            onPlayClick = viewModel::goToFormatSelect,
            onCommunityClick = viewModel::goToCustomCategories,
            onRankingClick = viewModel::goToRanking,
            onHistoryClick = viewModel::goToHistory,
            onProfileClick = viewModel::goToProfile,
            onFriendsClick = viewModel::goToFriends,
            onLoginClick = viewModel::goToLogin
        )

        state.screen == GameScreen.HISTORY -> HistoryScreen(
            scores = state.myScores,
            isLoading = state.isLoading,
            onBack = viewModel::backToStart,
            onHome = viewModel::backToStart,
            onRanking = viewModel::goToRanking,
            onFriends = viewModel::goToFriends,
            onProfile = viewModel::goToProfile
        )

        state.screen == GameScreen.PROFILE -> ProfileScreen(
            profile = state.profile,
            onSaveNome = viewModel::updateNome,
            onBack = viewModel::backToStart,
            onHome = viewModel::backToStart,
            onRanking = viewModel::goToRanking,
            onFriends = viewModel::goToFriends,
            onAvatarClick = viewModel::goToAvatar,
            onAchievementsClick = viewModel::goToAchievements,
            onSignOut = viewModel::signOut,
            isRegistered = state.userInfo?.isAnonymous == false,
            delete = state.delete,
            onOpenDelete = viewModel::openDeleteAccount,
            onDismissDelete = viewModel::dismissDeleteAccount,
            onConfirmDelete = viewModel::confirmDeleteAccount
        )

        state.screen == GameScreen.AVATAR -> com.ratoooooo.perguntaoluso.game.avatar.AvatarPickerScreen(
            current = state.profile?.avatar,
            onSelect = viewModel::updateAvatar,
            onBack = viewModel::goToProfile
        )

        state.screen == GameScreen.ACHIEVEMENTS -> com.ratoooooo.perguntaoluso.game.AchievementsScreen(
            profile = state.profile,
            onBack = viewModel::goToProfile,
            onHome = viewModel::backToStart,
            onRanking = viewModel::goToRanking,
            onFriends = viewModel::goToFriends,
            onProfile = viewModel::goToProfile
        )

        state.screen == GameScreen.FRIENDS -> FriendsScreen(
            friends = state.friends,
            profiles = state.friendProfiles,
            onlineUids = state.onlineUids,
            desafioPara = state.desafioPara,
            desafioSegundos = state.desafioSegundos,
            desafioAviso = state.desafioAviso,
            onSearch = viewModel::goToFriendSearch,
            onChallenge = viewModel::startChallenge,
            onCancelChallenge = viewModel::cancelChallenge,
            onDismissAviso = viewModel::dismissChallengeAviso,
            onAccept = { viewModel.acceptFriend(it.uid, it.nome) },
            onDecline = { viewModel.declineFriend(it.uid) },
            onCancel = { viewModel.cancelFriendRequest(it.uid) },
            onHome = viewModel::backToStart,
            onRanking = viewModel::goToRanking,
            onProfile = viewModel::goToProfile
        )

        state.screen == GameScreen.FRIEND_SEARCH -> FriendSearchScreen(
            query = state.friendQuery,
            results = state.friendResults,
            searching = state.friendSearching,
            searchDone = state.friendSearchDone,
            friends = state.friends,
            onQueryChange = viewModel::onFriendQueryChange,
            onSearch = viewModel::searchPlayers,
            onAdd = viewModel::sendFriendRequest,
            onBack = viewModel::goToFriends
        )

        state.screen == GameScreen.FORMAT_SELECT -> FormatScreen(
            onSolo = { viewModel.chooseFormat(null) },
            onMulti = { viewModel.chooseFormat(it) },
            onBack = viewModel::backToStart
        )

        state.screen == GameScreen.MULTI_MATCH ->
            com.ratoooooo.perguntaoluso.game.multi.MultiMatchHost(
                format = state.multiFormat,
                categoria = state.multiCategory,
                modo = state.multiMode,
                salaId = state.multiSalaId,
                onExit = viewModel::backToStart
            )

        state.screen == GameScreen.LOGIN -> LoginScreen(
            authLoading = state.authLoading,
            authError = state.authError,
            onLogin = viewModel::login,
            onGoToRegister = viewModel::goToRegister,
            onContinueAnon = viewModel::backToStart,
            onBack = viewModel::backToStart
        )

        state.screen == GameScreen.REGISTER -> RegisterScreen(
            isAnonymous = state.userInfo?.isAnonymous ?: true,
            authLoading = state.authLoading,
            authError = state.authError,
            onRegister = viewModel::register,
            onBack = viewModel::goToLogin
        )

        state.screen == GameScreen.RANKING -> RankingScreen(
            profiles = state.allProfiles,
            isLoading = state.isLoading,
            meuUid = state.userInfo?.uid,
            onBack = viewModel::backToStart,
            onHome = viewModel::backToStart,
            onFriends = viewModel::goToFriends,
            onProfile = viewModel::goToProfile
        )

        state.screen == GameScreen.CATEGORY_SELECT -> CategoryScreen(
            categories = state.categories,
            questionCounts = state.categoryCounts,
            formato = state.pendingMultiFormat,
            onCategorySelected = viewModel::selectCategory,
            onBack = viewModel::backToStart
        )

        state.screen == GameScreen.CUSTOM_CATEGORIES -> CustomCategoriesScreen(
            onPlayCustomCategorySolo = viewModel::playCustomCategory,
            onCreatePrivateRoom = viewModel::createPrivateRoomForCustomCategory,
            onJoinPrivateRoomByCode = viewModel::joinPrivateRoomByCode,
            onBack = viewModel::backToStart,
            onHome = viewModel::backToStart,
            onRanking = viewModel::goToRanking,
            onFriends = viewModel::goToFriends,
            onProfile = viewModel::goToProfile
        )

        state.screen == GameScreen.MODE_SELECT -> ModeScreen(
            categoria = state.selectedCategory,
            // Multiplayer offers Clássico + Caótico only (Eliminatórias is a solo survival mode).
            modes = if (state.pendingMultiFormat != null)
                listOf(GameMode.CLASSICO, GameMode.CAOTICO) else GameMode.entries,
            onModeSelected = viewModel::onModeChosen,
            onBack = viewModel::backToCategoryFromMode
        )

        state.screen == GameScreen.QUESTION && state.currentQuestion != null && state.mode != null ->
            QuestionScreen(
                question = state.currentQuestion!!,
                categoria = state.selectedCategory,
                mode = state.mode!!,
                questionNumber = state.currentIndex + 1,
                totalQuestions = state.questions.size,
                points = state.points,
                selectedOption = state.selectedOption,
                isAnswered = state.isAnswered,
                aceitaToques = state.aceitaToques,
                lastDelta = state.lastDelta,
                streak = state.streak,
                currentEvent = state.currentEvent,
                remainingMillis = state.remainingMillis,
                durationMillis = state.questionDurationMillis,
                onOptionSelected = viewModel::selectAnswer
            )

        state.screen == GameScreen.PODIUM && state.mode != null -> PodiumScreen(
            categoria = state.selectedCategory,
            mode = state.mode!!,
            points = state.points,
            correctCount = state.correctCount,
            total = state.questions.size.let { if (state.eliminated) state.currentIndex + 1 else it },
            eliminated = state.eliminated,
            won = state.wonLastGame,
            subiuDeNivel = state.subiuDeNivel,
            novasConquistas = state.novasConquistas,
            topScores = state.topScores,
            onPlayAgain = viewModel::playAgain,
            onHome = viewModel::backToStart
        )
    }

        // Incoming direct challenge — surfaced over whatever screen the player is on, except
        // during an active match (which already owns the screen).
        val convite = state.convites.recebidos.firstOrNull()
        if (convite != null && state.screen != GameScreen.MULTI_MATCH && state.screen != GameScreen.QUESTION) {
            ChallengeOverlay(
                convite = convite,
                onAccept = { viewModel.acceptConvite(convite) },
                onDecline = { viewModel.declineConvite(convite) }
            )
        }
    }
}

@Composable
private fun CenteredMessage(message: String) {
    Box(
        modifier = Modifier.fillMaxSize().background(Cream),
        contentAlignment = Alignment.Center
    ) {
        Text(text = message, color = Ink, style = MaterialTheme.typography.bodyLarge)
    }
}
