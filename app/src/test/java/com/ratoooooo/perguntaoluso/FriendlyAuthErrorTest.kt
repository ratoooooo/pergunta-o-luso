package com.ratoooooo.perguntaoluso

import com.ratoooooo.perguntaoluso.game.friendlyAuthError
import org.junit.Assert.assertEquals
import org.junit.Test

/**
 * Prende a resolução do Defeito D: mensagens de erro de rede e transporte (como
 * "unexpected end of stream on com.android.okhttp.Address@...") são traduzidas para
 * português europeu amigável em vez de escaparem para o utilizador como texto técnico cru.
 */
class FriendlyAuthErrorTest {

    @Test
    fun `mensagem exacta do defeito D nao cai no else cru e e traduzida`() {
        val ex = Exception("An internal error has occurred. [ unexpected end of stream on com.android.okhttp.Address@1234abcd ]")
        val resultado = friendlyAuthError(ex)
        assertEquals("Sem ligação à internet — tenta outra vez", resultado)
    }

    @Test
    fun `erros de ligacao e timeout sao mapeados para sem ligacao`() {
        assertEquals(
            "Sem ligação à internet — tenta outra vez",
            friendlyAuthError(Exception("failed to connect to /192.168.1.1:443"))
        )
        assertEquals(
            "Sem ligação à internet — tenta outra vez",
            friendlyAuthError(Exception("connect timeout"))
        )
        assertEquals(
            "Sem ligação à internet — tenta outra vez",
            friendlyAuthError(Exception("Socket timed out"))
        )
        assertEquals(
            "Sem ligação à internet — tenta outra vez",
            friendlyAuthError(Exception("A network error (such as timeout, interrupted connection) has occurred"))
        )
        assertEquals(
            "Sem ligação à internet — tenta outra vez",
            friendlyAuthError(Exception("Unable to resolve host \"firestore.googleapis.com\": No address associated with hostname"))
        )
    }

    @Test
    fun `os 4 ramos existentes continuam a funcionar sem alteracoes`() {
        // 1. Email já registado
        assertEquals(
            "Este e-mail já está registado",
            friendlyAuthError(Exception("The email address is already in use by another account."))
        )
        assertEquals(
            "Este e-mail já está registado",
            friendlyAuthError(Exception("The email address is already registered"))
        )

        // 2. Email inválido / mal formatado
        assertEquals(
            "E-mail inválido",
            friendlyAuthError(Exception("The email address is badly formatted."))
        )

        // 3. Palavra-passe errada ou credenciais inválidas
        assertEquals(
            "E-mail ou palavra-passe incorretos",
            friendlyAuthError(Exception("The password is invalid or the user does not have a password."))
        )
        assertEquals(
            "E-mail ou palavra-passe incorretos",
            friendlyAuthError(Exception("INVALID_LOGIN_CREDENTIALS"))
        )
        assertEquals(
            "E-mail ou palavra-passe incorretos",
            friendlyAuthError(Exception("The credential is incorrect"))
        )

        // 4. Conta não encontrada
        assertEquals(
            "Conta não encontrada",
            friendlyAuthError(Exception("There is no user record corresponding to this identifier."))
        )
    }

    @Test
    fun `excepcao com mensagem nula devolve erro generico`() {
        assertEquals("Erro de autenticação", friendlyAuthError(Exception()))
    }

    @Test
    fun `erro desconhecido nao mapeado continua a cair no else`() {
        val msgInesperada = "Operação bloqueada temporariamente pela política de segurança"
        assertEquals(msgInesperada, friendlyAuthError(Exception(msgInesperada)))
    }
}
