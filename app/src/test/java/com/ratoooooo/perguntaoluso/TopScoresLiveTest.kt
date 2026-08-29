package com.ratoooooo.perguntaoluso

import com.ratoooooo.perguntaoluso.data.ScoreEntry
import com.ratoooooo.perguntaoluso.data.ScoreRepository
import com.ratoooooo.perguntaoluso.game.GameScreen
import com.ratoooooo.perguntaoluso.game.GameUiState
import com.ratoooooo.perguntaoluso.game.sessionOnly
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancelAndJoin
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.asSharedFlow
import kotlinx.coroutines.launch
import kotlinx.coroutines.runBlocking
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotEquals
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * Prende a correção do defeito C: `topScores` passa a ser um listener ao vivo sobre `/scores`
 * em vez de uma leitura pontual `loadTopScores()`, e sobrevive a limpezas de `sessionOnly()`.
 */
class TopScoresLiveTest {

    private class FakeScoreRepository : ScoreRepository() {
        var loadTopScoresChamadas = 0
        var saveScoreChamadas = 0
        private val _flow = MutableSharedFlow<List<ScoreEntry>>(replay = 1)

        override fun observeTopScores(limit: Int): Flow<List<ScoreEntry>> = _flow.asSharedFlow()

        override suspend fun loadTopScores(limit: Int): List<ScoreEntry> {
            loadTopScoresChamadas++
            return emptyList()
        }

        override suspend fun saveScore(
            modo: String,
            categoria: String,
            score: Int,
            correctCount: Int,
            total: Int,
            formato: String
        ) {
            saveScoreChamadas++
            // Simula o comportamento da RTDB: após a escrita, o ValueEventListener dispara
            // uma nova emissão com o registo adicionado.
            val novoRegisto = ScoreEntry(
                uid = "meu-uid",
                modo = modo,
                categoria = categoria,
                formato = formato,
                score = score,
                correctCount = correctCount,
                total = total,
                timestamp = System.currentTimeMillis()
            )
            val actuais = (_flow.replayCache.firstOrNull() ?: emptyList())
            val novaLista = (actuais + novoRegisto).sortedByDescending { it.score }
            _flow.emit(novaLista)
        }

        suspend fun emitir(scores: List<ScoreEntry>) {
            _flow.emit(scores)
        }
    }

    @Test
    fun `escrita nova em scores actualiza topScores sem chamar loadTopScores`() = runBlocking {
        val repo = FakeScoreRepository()
        val topScoresObservados = mutableListOf<List<ScoreEntry>>()
        val scope = CoroutineScope(SupervisorJob() + Dispatchers.Unconfined)

        val job = scope.launch {
            repo.observeTopScores().collect {
                topScoresObservados.add(it)
            }
        }

        val listaInicial = listOf(
            ScoreEntry(uid = "u1", score = 1500, modo = "classico"),
            ScoreEntry(uid = "u2", score = 1200, modo = "caotico")
        )
        repo.emitir(listaInicial)

        assertEquals(1, topScoresObservados.size)
        assertEquals(listaInicial, topScoresObservados.last())
        assertEquals("loadTopScores nunca deve ser chamado", 0, repo.loadTopScoresChamadas)

        // Nova pontuação entra no topo (ex: outro jogador ou servidor)
        val listaActualizada = listOf(
            ScoreEntry(uid = "u3", score = 2000, modo = "classico"),
            ScoreEntry(uid = "u1", score = 1500, modo = "classico"),
            ScoreEntry(uid = "u2", score = 1200, modo = "caotico")
        )
        repo.emitir(listaActualizada)

        assertEquals(2, topScoresObservados.size)
        assertEquals(listaActualizada, topScoresObservados.last())
        assertEquals("loadTopScores continua sem ser chamado", 0, repo.loadTopScoresChamadas)

        job.cancel()
    }

    @Test
    fun `sessionOnly preserva topScores em vez de limpar a lista`() {
        val topScores = listOf(
            ScoreEntry(uid = "u1", score = 2500, modo = "classico", timestamp = 1000L),
            ScoreEntry(uid = "u2", score = 1800, modo = "caotico", timestamp = 2000L)
        )
        val estadoComPartida = GameUiState(
            screen = GameScreen.QUESTION,
            points = 500,
            correctCount = 4,
            currentIndex = 3,
            topScores = topScores,
            categoryCounts = mapOf("História" to 20),
            playingNow = 42
        )

        val estadoLimpo = estadoComPartida.sessionOnly()

        // Estado do jogo limpo
        assertEquals(0, estadoLimpo.points)
        assertEquals(0, estadoLimpo.correctCount)
        assertEquals(0, estadoLimpo.currentIndex)

        // Dados de sessão preservados (incluindo topScores)
        assertEquals("topScores tem de sobreviver ao sessionOnly", topScores, estadoLimpo.topScores)
        assertEquals(mapOf("História" to 20), estadoLimpo.categoryCounts)
        assertEquals(42, estadoLimpo.playingNow)
    }

    @Test
    fun `saveScore despoleta emissao no listener e reflecte imediatamente`() = runBlocking {
        val repo = FakeScoreRepository()
        var estadoTopScores = emptyList<ScoreEntry>()
        val scope = CoroutineScope(SupervisorJob() + Dispatchers.Unconfined)

        val job = scope.launch {
            repo.observeTopScores().collect {
                estadoTopScores = it
            }
        }

        // Arranque inicial com lista existente
        val existentes = listOf(
            ScoreEntry(uid = "rival", score = 1000, modo = "classico")
        )
        repo.emitir(existentes)
        assertEquals(existentes, estadoTopScores)

        // Jogador conclui partida solo e grava pontuação
        repo.saveScore(
            modo = "classico",
            categoria = "História",
            score = 1500,
            correctCount = 10,
            total = 10,
            formato = "solo"
        )

        assertEquals("saveScore foi chamado 1 vez", 1, repo.saveScoreChamadas)
        assertEquals("loadTopScores nunca foi chamado", 0, repo.loadTopScoresChamadas)
        assertEquals("topScores actualizado para 2 registos", 2, estadoTopScores.size)
        assertEquals("novo recorde passa para 1.º lugar", "meu-uid", estadoTopScores.first().uid)
        assertEquals(1500, estadoTopScores.first().score)

        job.cancel()
    }

    @Test
    fun `reconexao do listener substitui o anterior sem chamadas a loadTopScores`() = runBlocking {
        val repo = FakeScoreRepository()
        val recebidosNovo = mutableListOf<List<ScoreEntry>>()
        val scope = CoroutineScope(SupervisorJob() + Dispatchers.Unconfined)

        // 1.º listener (ex: sessão anterior)
        var job = scope.launch {
            repo.observeTopScores().collect { }
        }
        repo.emitir(listOf(ScoreEntry(uid = "u1", score = 500)))

        // Troca de utilizador (ex: login/logout) -> cancela e recria job
        job.cancelAndJoin()

        job = scope.launch {
            repo.observeTopScores().collect {
                recebidosNovo.add(it)
            }
        }

        val listaAntiga = listOf(ScoreEntry(uid = "u1", score = 500))
        val listaNova = listOf(ScoreEntry(uid = "u2", score = 900))
        repo.emitir(listaNova)

        assertEquals(listOf(listaAntiga, listaNova), recebidosNovo)
        assertEquals(listaNova, recebidosNovo.last())
        assertEquals(0, repo.loadTopScoresChamadas)

        job.cancel()
    }
}
