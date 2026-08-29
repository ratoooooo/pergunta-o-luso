package com.ratoooooo.perguntaoluso

import com.google.firebase.database.DatabaseException
import com.ratoooooo.perguntaoluso.game.multi.coletarListener
import kotlinx.coroutines.CoroutineExceptionHandler
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancelAndJoin
import kotlinx.coroutines.channels.awaitClose
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.callbackFlow
import kotlinx.coroutines.launch
import kotlinx.coroutines.runBlocking
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * Prende o defeito B: `observeRoom` fazia `collect` cru dentro de `viewModelScope.launch`, e o
 * `callbackFlow` do `MultiMatchRepository` fecha-se **com a excepção** quando a RTDB cancela o
 * listener (`onCancelled` → `close(error.toException())`). A excepção saía pelo scope e chegava
 * ao handler por omissão — `FATAL EXCEPTION: main`.
 *
 * O scope destes testes é montado como o `viewModelScope` real (`SupervisorJob` + um
 * [CoroutineExceptionHandler]): o handler é o substituto do sítio onde a app morria. Se uma
 * excepção lá aterrar, é exactamente o crash reportado.
 */
class ColetarListenerTest {

    /** A mensagem verdadeira da negação de permissão, tal como aparecia no logcat. */
    private val MENSAGEM_RTDB = "This client does not have permission to perform this operation"

    /** Espelha `onCancelled(error) { close(error.toException()) }` do MultiMatchRepository. */
    private fun fluxoQueARtdbCancela(): Flow<String> = callbackFlow {
        close(DatabaseException(MENSAGEM_RTDB))
        awaitClose { }
    }

    private fun fluxoQueEmiteEDepoisFalha(): Flow<String> = callbackFlow {
        trySend("sala-viva")
        close(DatabaseException(MENSAGEM_RTDB))
        awaitClose { }
    }

    private fun fluxoQueNuncaAcaba(): Flow<String> = callbackFlow {
        trySend("sala-viva")
        awaitClose { }
    }

    private fun scopeComoViewModelScope(registo: MutableList<Throwable>): CoroutineScope {
        val handler = CoroutineExceptionHandler { _, e -> registo += e }
        return CoroutineScope(SupervisorJob() + Dispatchers.Unconfined + handler)
    }

    /**
     * Controlo — reproduz o crash **antigo**. Sem isto, os testes a seguir não provariam nada:
     * podiam passar por o cenário nunca chegar a falhar.
     */
    @Test
    fun `o collect cru deixa a excecao chegar ao handler do scope`() = runBlocking {
        val naoApanhadas = mutableListOf<Throwable>()
        val scope = scopeComoViewModelScope(naoApanhadas)

        val job = scope.launch {
            fluxoQueARtdbCancela().collect { }   // o que o observeRoom fazia antes
        }
        job.join()

        assertEquals("o cenário tem mesmo de falhar", 1, naoApanhadas.size)
        assertTrue(naoApanhadas.first() is DatabaseException)
    }

    @Test
    fun `coletarListener encaminha a falha em vez de a deixar subir`() = runBlocking {
        val naoApanhadas = mutableListOf<Throwable>()
        val falhas = mutableListOf<Throwable>()
        val scope = scopeComoViewModelScope(naoApanhadas)

        val job = scope.launch {
            coletarListener(fluxoQueARtdbCancela(), onFalha = { falhas += it }) { }
        }
        job.join()

        assertTrue("nada pode chegar ao handler do scope: $naoApanhadas", naoApanhadas.isEmpty())
        assertEquals(1, falhas.size)
        assertTrue(falhas.first() is DatabaseException)
        assertEquals(MENSAGEM_RTDB, falhas.first().message)
    }

    /** A falha não pode engolir o que a sala já tinha entregue antes de morrer. */
    @Test
    fun `os valores continuam a passar ate ao momento da falha`() = runBlocking {
        val naoApanhadas = mutableListOf<Throwable>()
        val recebidos = mutableListOf<String>()
        val falhas = mutableListOf<Throwable>()
        val scope = scopeComoViewModelScope(naoApanhadas)

        val job = scope.launch {
            coletarListener(fluxoQueEmiteEDepoisFalha(), onFalha = { falhas += it }) { recebidos += it }
        }
        job.join()

        assertEquals(listOf("sala-viva"), recebidos)
        assertEquals(1, falhas.size)
        assertTrue(naoApanhadas.isEmpty())
    }

    // --- Defeito B2: os dois listeners do lobby (openLobbiesJob e lobbyJob) ---
    //
    // O fluxo falhar já está coberto acima — o helper é o mesmo. O que é **novo** nestes dois
    // pontos de chamada é o corpo do `collect`: o `listenToLobby` chama a RTDB lá dentro
    // (joinRoom, loadGameQuestions, startLobbyRoom), e uma dessas chamadas a estoirar saía pelo
    // `collect` exactamente como a falha do listener — o mesmo crash, por outra porta.

    /** Controlo: o corpo a estoirar mata a app tal como o listener a falhar. */
    @Test
    fun `o corpo do collect a estoirar chega ao handler do scope`() = runBlocking {
        val naoApanhadas = mutableListOf<Throwable>()
        val scope = scopeComoViewModelScope(naoApanhadas)

        val job = scope.launch {
            fluxoQueNuncaAcaba().collect { throw DatabaseException(MENSAGEM_RTDB) }
        }
        job.join()

        assertEquals("o cenário tem mesmo de falhar", 1, naoApanhadas.size)
        assertTrue(naoApanhadas.first() is DatabaseException)
    }

    @Test
    fun `coletarListener tambem apanha o corpo do collect a estoirar`() = runBlocking {
        val naoApanhadas = mutableListOf<Throwable>()
        val falhas = mutableListOf<Throwable>()
        val scope = scopeComoViewModelScope(naoApanhadas)

        val job = scope.launch {
            coletarListener(fluxoQueNuncaAcaba(), onFalha = { falhas += it }) {
                throw DatabaseException(MENSAGEM_RTDB)   // o joinRoom/startLobbyRoom a falhar
            }
        }
        job.join()

        assertTrue("nada pode chegar ao handler do scope: $naoApanhadas", naoApanhadas.isEmpty())
        assertEquals(1, falhas.size)
        assertTrue(falhas.first() is DatabaseException)
    }

    /**
     * `leave()` e `onCleared()` cancelam o `observeJob`. Se o `catch` engolisse a
     * [kotlinx.coroutines.CancellationException], o job ficava "vivo" para o pai e a saída
     * normal da sala passava a contar como falha — o ecrã de erro aparecia a quem só carregou
     * em VOLTAR.
     */
    @Test
    fun `cancelar o job nao conta como falha`() = runBlocking {
        val naoApanhadas = mutableListOf<Throwable>()
        val falhas = mutableListOf<Throwable>()
        val scope = scopeComoViewModelScope(naoApanhadas)

        val job = scope.launch {
            coletarListener(fluxoQueNuncaAcaba(), onFalha = { falhas += it }) { }
        }
        job.cancelAndJoin()

        assertTrue("cancelar não é falhar: $falhas", falhas.isEmpty())
        assertTrue(naoApanhadas.isEmpty())
        assertTrue(job.isCancelled)
    }
}
