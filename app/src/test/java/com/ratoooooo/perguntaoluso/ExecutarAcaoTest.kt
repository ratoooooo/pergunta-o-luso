package com.ratoooooo.perguntaoluso

import com.google.firebase.database.DatabaseException
import com.ratoooooo.perguntaoluso.game.multi.MultiPhase
import com.ratoooooo.perguntaoluso.game.multi.MultiUiState
import com.ratoooooo.perguntaoluso.game.multi.estadoAposFalhaAoTrocarDeSala
import com.ratoooooo.perguntaoluso.game.multi.executarAcao
import kotlinx.coroutines.CoroutineExceptionHandler
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancelAndJoin
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import kotlinx.coroutines.runBlocking
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * Prende o defeito B3: `switchLobby` corria `joinLobbyById` e `findOrCreateLobby` a descoberto
 * dentro de `viewModelScope.launch`. É uma acção do jogador e não um listener, mas a excepção
 * saía pela mesma porta do B/B2 — o scope — e dava `FATAL EXCEPTION: main`.
 *
 * Como no `ColetarListenerTest`, o scope é montado à imagem do `viewModelScope`
 * (`SupervisorJob` + [CoroutineExceptionHandler]): o handler é o substituto do sítio onde a app
 * morria, e qualquer excepção que lá aterre é o crash reportado.
 */
class ExecutarAcaoTest {

    private val MENSAGEM_RTDB = "This client does not have permission to perform this operation"

    private fun scopeComoViewModelScope(registo: MutableList<Throwable>): CoroutineScope {
        val handler = CoroutineExceptionHandler { _, e -> registo += e }
        return CoroutineScope(SupervisorJob() + Dispatchers.Unconfined + handler)
    }

    /**
     * A forma do corpo do `switchLobby`: entra na sala escolhida e, se ela já não aceitar,
     * arranja outra. São estes os **dois pontos** que corriam sem protecção.
     */
    private suspend fun corpoDoSwitch(
        joinLobbyById: suspend () -> Boolean,
        findOrCreateLobby: suspend () -> String,
        aoEntrar: (String) -> Unit
    ) {
        val entrou = joinLobbyById()
        if (entrou) aoEntrar("sala-escolhida") else aoEntrar(findOrCreateLobby())
    }

    private fun falha(): Nothing = throw DatabaseException(MENSAGEM_RTDB)

    // --- controlo: os dois pontos matavam mesmo a app ---

    @Test
    fun `ponto 1 - joinLobbyById a estoirar chega ao handler do scope`() = runBlocking {
        val naoApanhadas = mutableListOf<Throwable>()
        val scope = scopeComoViewModelScope(naoApanhadas)

        val job = scope.launch {
            corpoDoSwitch(joinLobbyById = { falha() }, findOrCreateLobby = { "x" }, aoEntrar = {})
        }
        job.join()

        assertEquals("o cenário tem mesmo de falhar", 1, naoApanhadas.size)
        assertTrue(naoApanhadas.first() is DatabaseException)
    }

    @Test
    fun `ponto 2 - findOrCreateLobby a estoirar chega ao handler do scope`() = runBlocking {
        val naoApanhadas = mutableListOf<Throwable>()
        val scope = scopeComoViewModelScope(naoApanhadas)

        val job = scope.launch {
            // `false` = a sala escolhida já não aceita, cai no findOrCreateLobby
            corpoDoSwitch(joinLobbyById = { false }, findOrCreateLobby = { falha() }, aoEntrar = {})
        }
        job.join()

        assertEquals("o cenário tem mesmo de falhar", 1, naoApanhadas.size)
        assertTrue(naoApanhadas.first() is DatabaseException)
    }

    // --- com a correcção, nenhum dos dois sobe ---

    @Test
    fun `ponto 1 - executarAcao encaminha a falha em vez de a deixar subir`() = runBlocking {
        val naoApanhadas = mutableListOf<Throwable>()
        val falhas = mutableListOf<Throwable>()
        val scope = scopeComoViewModelScope(naoApanhadas)

        val job = scope.launch {
            executarAcao(onFalha = { falhas += it }) {
                corpoDoSwitch(joinLobbyById = { falha() }, findOrCreateLobby = { "x" }, aoEntrar = {})
            }
        }
        job.join()

        assertTrue("nada pode chegar ao handler do scope: $naoApanhadas", naoApanhadas.isEmpty())
        assertEquals(1, falhas.size)
        assertEquals(MENSAGEM_RTDB, falhas.first().message)
    }

    @Test
    fun `ponto 2 - executarAcao encaminha a falha em vez de a deixar subir`() = runBlocking {
        val naoApanhadas = mutableListOf<Throwable>()
        val falhas = mutableListOf<Throwable>()
        val scope = scopeComoViewModelScope(naoApanhadas)

        val job = scope.launch {
            executarAcao(onFalha = { falhas += it }) {
                corpoDoSwitch(joinLobbyById = { false }, findOrCreateLobby = { falha() }, aoEntrar = {})
            }
        }
        job.join()

        assertTrue("nada pode chegar ao handler do scope: $naoApanhadas", naoApanhadas.isEmpty())
        assertEquals(1, falhas.size)
        assertTrue(falhas.first() is DatabaseException)
    }

    /** O caminho feliz não pode passar a assinalar falhas nem a perder a entrada na sala. */
    @Test
    fun `troca bem sucedida nao chama onFalha`() = runBlocking {
        val naoApanhadas = mutableListOf<Throwable>()
        val falhas = mutableListOf<Throwable>()
        val entradas = mutableListOf<String>()
        val scope = scopeComoViewModelScope(naoApanhadas)

        val job = scope.launch {
            executarAcao(onFalha = { falhas += it }) {
                corpoDoSwitch(
                    joinLobbyById = { false },
                    findOrCreateLobby = { "sala-nova" },
                    aoEntrar = { entradas += it }
                )
            }
        }
        job.join()

        assertEquals(listOf("sala-nova"), entradas)
        assertTrue(falhas.isEmpty())
        assertTrue(naoApanhadas.isEmpty())
    }

    /**
     * Sair do ecrã cancela o scope. Se o `catch` engolisse a
     * [kotlinx.coroutines.CancellationException], o job ficava "vivo" para o pai e uma saída
     * normal aparecia ao jogador como erro.
     */
    @Test
    fun `cancelar o job nao conta como falha`() = runBlocking {
        val naoApanhadas = mutableListOf<Throwable>()
        val falhas = mutableListOf<Throwable>()
        val scope = scopeComoViewModelScope(naoApanhadas)

        val job = scope.launch {
            executarAcao(onFalha = { falhas += it }) {
                corpoDoSwitch(
                    joinLobbyById = { delay(60_000); true },
                    findOrCreateLobby = { "x" },
                    aoEntrar = {}
                )
            }
        }
        job.cancelAndJoin()

        assertTrue("cancelar não é falhar: $falhas", falhas.isEmpty())
        assertTrue(naoApanhadas.isEmpty())
        assertTrue(job.isCancelled)
    }

    // --- o estado a meio da troca ---

    /**
     * Falhar a troca deixa o jogador fora de qualquer lobby: já saiu do antigo e não entrou no
     * novo. O estado tem de dizer isso, senão o VOLTAR do ecrã de erro tenta sair de um lobby
     * onde nunca entrou.
     */
    @Test
    fun `falha a trocar deixa o jogador sem lobby`() {
        val antes = MultiUiState(
            phase = MultiPhase.SEARCHING,
            categoria = "Geografia",
            modo = "caotico",
            currentLobbyId = "lobby-de-destino",
            isHost = true
        )

        val depois = estadoAposFalhaAoTrocarDeSala(antes)

        assertEquals(MultiPhase.ERROR, depois.phase)
        assertNotNull("o ecrã de erro precisa de texto", depois.error)
        assertNull("não pertence a lobby nenhum", depois.currentLobbyId)
        assertFalse("não é anfitrião de coisa nenhuma", depois.isHost)
    }

    /** A escolha do jogador sobrevive à falha — é dele, não da sala que falhou. */
    @Test
    fun `falha a trocar preserva categoria e modo`() {
        val antes = MultiUiState(categoria = "Desporto", modo = "classico", currentLobbyId = "x")

        val depois = estadoAposFalhaAoTrocarDeSala(antes)

        assertEquals("Desporto", depois.categoria)
        assertEquals("classico", depois.modo)
    }
}
