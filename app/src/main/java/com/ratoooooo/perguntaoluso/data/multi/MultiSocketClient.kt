package com.ratoooooo.perguntaoluso.data.multi

import com.google.firebase.auth.FirebaseAuth
import com.ratoooooo.perguntaoluso.data.LobbyData
import com.ratoooooo.perguntaoluso.game.multi.MatchFormat
import kotlinx.coroutines.channels.awaitClose
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.callbackFlow
import kotlinx.coroutines.tasks.await
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.Response
import okhttp3.WebSocket
import okhttp3.WebSocketListener
import org.json.JSONArray
import org.json.JSONObject
import java.util.concurrent.TimeUnit

/**
 * **O único sítio onde o servidor da partida é nomeado.**
 *
 * Vai embutido no APK, por isso trocar de domínio custa uma versão nova na Play Store. Está aqui
 * sozinho para essa troca ser uma linha e não uma caça. `wss://` e não `ws://`: o ID token do
 * Firebase viaja no handshake, e o Android 9+ bloqueia texto em claro de qualquer maneira.
 */
const val SERVIDOR_PARTIDA_WSS = "wss://perguntaoluso.duckdns.org"

/**
 * O que o servidor manda. Espelha a tabela "Servidor → cliente" de `servidor/PROTOCOLO.md`, que é
 * a fonte única — se os dois discordarem, é este ficheiro que está errado.
 *
 * É uma hierarquia selada e não `JSONObject` cru de propósito: isto é uma fronteira de confiança,
 * e o `when` exaustivo do redutor passa a acusar em compilação qualquer mensagem nova do
 * protocolo que ninguém tenha tratado.
 */
sealed interface EventoServidor {

    /** Socket aberto e autenticado. Só a partir daqui é que faz sentido enviar seja o que for. */
    data object Ligado : EventoServidor

    data class Sessao(val uid: String, val nome: String, val versao: String) : EventoServidor

    data class Salas(val salas: List<LobbyData>) : EventoServidor

    data class Sala(
        val lobbyId: String,
        /**
         * Quem manda no formato é o servidor. Numa sala por código o cliente não o pode saber
         * antes de entrar — só conhece os 4 dígitos.
         */
        val formato: MatchFormat,
        val categoria: String,
        val modo: String,
        val codigo: String?,
        val membros: List<MembroDaSala>,
        val souAnfitriao: Boolean
    ) : EventoServidor

    data class Partida(
        val salaId: String,
        val formato: MatchFormat,
        val membros: List<MembroDaSala>,
        val totalPerguntas: Int
    ) : EventoServidor

    data class Pergunta(
        val indice: Int,
        val pergunta: String,
        val opcoes: List<String>,
        val dificuldade: String,
        val evento: String?,
        val duracao: Long,
        val fimEm: Long
    ) : EventoServidor

    /**
     * O veredicto. **Só chega a quem respondeu**, e traz a `respostaCorreta` — que é a primeira
     * vez que ela sai do servidor. Era exactamente isto que a arquitectura só-RTDB não conseguia
     * fazer: lá, o cliente tinha de a receber com a pergunta para poder corrigir.
     */
    data class Resposta(
        val indice: Int,
        val certa: Boolean,
        val respostaCorreta: String,
        val total: Int,
        val certas: Int
    ) : EventoServidor

    data class Placar(val pontos: Map<String, Int>) : EventoServidor

    /** `ausente` (ligação perdida), `voltou`, ou `saiu` (desistência confirmada). */
    data class Presenca(val uid: String, val estado: EstadoDePresenca) : EventoServidor

    data class Podio(
        val walkover: Boolean,
        val ganhei: Boolean,
        val meuScore: Int,
        val minhasCertas: Int,
        val maxSequencia: Int,
        val totalPerguntas: Int,
        val ranking: List<LugarNoPodio>,
        val equipas: List<EquipaNoPodio>
    ) : EventoServidor

    /** Medição de rtt do lado do SERVIDOR. Responder já, sem passar pelo estado. */
    data class Sonda(val s: String) : EventoServidor

    /** Resposta ao nosso `ping`: dá o desvio do relógio deste dispositivo. */
    data class Pong(val t0: Long, val tS: Long) : EventoServidor

    data class Aviso(val codigo: String) : EventoServidor

    data class Erro(val codigo: String, val msg: String?) : EventoServidor

    /** Socket fechado ou falhado. Um handshake recusado (401) chega por aqui. */
    data class Desligado(val motivo: String) : EventoServidor

    /** Mensagem que este cliente não conhece — servidor mais recente do que a app. Ignorada. */
    data class Desconhecido(val tipo: String) : EventoServidor
}

enum class EstadoDePresenca { AUSENTE, VOLTOU, SAIU }

data class MembroDaSala(val uid: String, val nome: String, val equipa: String?)

data class LugarNoPodio(val uid: String, val nome: String, val pontos: Int, val saiu: Boolean)

data class EquipaNoPodio(
    val nome: String,
    val total: Int,
    val venceu: Boolean,
    val jogadores: List<LugarNoPodio>
)

/**
 * Ligação ao servidor da partida ao vivo.
 *
 * Mesmo formato dos repositórios que já existem (`ProfileRepository.observe`,
 * `PresenceRepository.observeCount`): um `callbackFlow` que abre o recurso, empurra o que chega, e
 * fecha-o no `awaitClose`. Quem recolhe é o `MultiMatchViewModel`, pelo mesmo `coletarListener`
 * que protege os listeners da RTDB — uma falha aqui não pode matar a app.
 */
class MultiSocketClient(
    private val url: String = SERVIDOR_PARTIDA_WSS,
    private val cliente: OkHttpClient = clientePadrao()
) {

    @Volatile
    private var socket: WebSocket? = null

    /**
     * Abre o socket e emite o que o servidor manda.
     *
     * O ID token vai no cabeçalho `Authorization` do **handshake HTTP**, nunca em query string: o
     * URL fica em registos de proxy e em histórico, e este token dá acesso à conta. Sem token
     * válido o servidor responde 401 e a ligação nunca chega a ser um WebSocket.
     */
    fun ligar(): Flow<EventoServidor> = callbackFlow {
        val pedido = Request.Builder()
            .url(url)
            .header("Authorization", "Bearer ${idTokenDoFirebase()}")
            .build()

        val ouvinte = object : WebSocketListener() {
            override fun onOpen(webSocket: WebSocket, response: Response) {
                trySend(EventoServidor.Ligado)
            }

            override fun onMessage(webSocket: WebSocket, text: String) {
                trySend(interpretar(text))
            }

            override fun onFailure(webSocket: WebSocket, t: Throwable, response: Response?) {
                // Uma falha de rede ou um 401 são estados previstos, não excepções a propagar:
                // o `MultiMatchViewModel` decide o que mostrar. Fechar o fluxo com a excepção
                // obrigava quem recolhe a distinguir "caiu a rede" de "o código tem um defeito".
                trySend(EventoServidor.Desligado(response?.code?.toString() ?: t.message.orEmpty()))
                close()
            }

            override fun onClosed(webSocket: WebSocket, code: Int, reason: String) {
                trySend(EventoServidor.Desligado(reason.ifBlank { code.toString() }))
                close()
            }
        }

        socket = cliente.newWebSocket(pedido, ouvinte)
        awaitClose {
            socket?.close(FECHO_NORMAL, null)
            socket = null
        }
    }

    // ---- envio ----------------------------------------------------------------
    //
    // Um método por mensagem, em vez de um `enviar(JSONObject)` genérico: os nomes do protocolo
    // ficam todos neste ficheiro, e um erro de escrita passa a ser um erro de compilação.
    // Cobrem a tabela "Cliente → servidor" do PROTOCOLO.md por inteiro.

    fun procurar(formato: String, categoria: String, modo: String) = enviar("procurar") {
        put("formato", formato); put("categoria", categoria); put("modo", modo)
    }

    fun trocarSala(lobbyId: String) = enviar("trocar_sala") { put("lobbyId", lobbyId) }

    fun iniciar() = enviar("iniciar") {}

    fun sair() = enviar("sair") {}

    /**
     * A opção que o jogador tocou — e mais nada. Sem pontos: quem pontua é o servidor.
     *
     * [tCliente] é o instante da resposta em tempo de SERVIDOR (relógio local + desvio medido
     * pelo ping). Sem ele, carimbar à chegada faria a latência custar pontos; o servidor limita
     * o crédito ao rtt que ele próprio mediu, por isso exagerar aqui não rende nada.
     */
    fun responder(indice: Int, opcao: String?, tCliente: Long) = enviar("responder") {
        put("indice", indice); put("opcao", opcao ?: JSONObject.NULL); put("tCliente", tCliente)
    }

    /** Sala privada com um quiz da comunidade. O servidor devolve `sala` já com o código. */
    fun privadaCriar(formato: String, quizId: String) = enviar("privada_criar") {
        put("formato", formato); put("quizId", quizId)
    }

    fun privadaEntrar(codigo: String) = enviar("privada_entrar") { put("codigo", codigo) }

    /**
     * Cria a sala de um desafio direto. Só o convidado lá entra — o servidor guarda a lista de
     * permitidos, por isso conhecer o id da sala não chega para se meter no duelo de outra pessoa.
     */
    fun desafioCriar(formato: String, categoria: String, modo: String, paraUid: String) =
        enviar("desafio_criar") {
            put("formato", formato); put("categoria", categoria)
            put("modo", modo); put("paraUid", paraUid)
        }

    fun desafioEntrar(salaId: String) = enviar("desafio_entrar") { put("salaId", salaId) }

    fun ping(t0: Long) = enviar("ping") { put("t0", t0) }

    fun sondaOk(s: String) = enviar("sonda_ok") { put("s", s) }

    /** Fecha à mão, fora do ciclo de vida do fluxo (usado pelo `leave()`). */
    fun fechar() {
        socket?.close(FECHO_NORMAL, null)
        socket = null
    }

    private inline fun enviar(tipo: String, corpo: JSONObject.() -> Unit) {
        val ws = socket ?: return
        ws.send(JSONObject().put("t", tipo).apply(corpo).toString())
    }

    private suspend fun idTokenDoFirebase(): String {
        val utilizador = FirebaseAuth.getInstance().currentUser
            ?: error("sem sessão do Firebase — o socket não pode ser aberto")
        return utilizador.getIdToken(false).await().token
            ?: error("o Firebase devolveu um ID token vazio")
    }

    companion object {
        private const val FECHO_NORMAL = 1000

        private fun clientePadrao(): OkHttpClient = OkHttpClient.Builder()
            // Ping do próprio WebSocket (não o `ping` do protocolo): mantém a ligação viva
            // através do Caddy e de NAT de rede móvel, que fecham ligações ociosas sem aviso.
            .pingInterval(20, TimeUnit.SECONDS)
            .build()
    }
}

// ---- interpretação ------------------------------------------------------------

/**
 * Traduz uma trama do servidor. Nunca lança: uma mensagem malformada vira [EventoServidor.Erro] e
 * uma desconhecida vira [EventoServidor.Desconhecido], porque um servidor mais recente do que a
 * app é um cenário normal — não um motivo para deitar a partida abaixo.
 */
internal fun interpretar(texto: String): EventoServidor = runCatching {
    val j = JSONObject(texto)
    when (val tipo = j.optString("t")) {
        "sessao" -> EventoServidor.Sessao(
            uid = j.optString("uid"),
            nome = j.optString("nome").ifBlank { "Jogador" },
            versao = j.optString("versao")
        )

        "salas" -> EventoServidor.Salas(
            salas = j.optJSONArray("salas").objectos().map { s ->
                LobbyData(
                    lobbyId = s.optString("lobbyId"),
                    hostNome = s.optString("anfitriao").ifBlank { "Jogador" },
                    format = MatchFormat.fromId(j.optString("formato")),
                    categoria = s.optString("categoria"),
                    modo = s.optString("modo"),
                    estado = "waiting",
                    // O servidor manda a CONTAGEM de jogadores, não a lista de uids — quem está
                    // na sala dos outros não é assunto de quem está a escolher. O ecrã só usa
                    // `membros.size`, por isso enche-se com lugares anónimos até ao número certo.
                    membros = List(s.optInt("jogadores")) { "" to "" }
                )
            }
        )

        "sala" -> EventoServidor.Sala(
            lobbyId = j.optString("lobbyId"),
            formato = MatchFormat.fromId(j.optString("formato")),
            categoria = j.optString("categoria"),
            modo = j.optString("modo"),
            codigo = j.optString("codigo").ifBlank { null },
            membros = j.optJSONArray("membros").membros(),
            souAnfitriao = j.optBoolean("souAnfitriao")
        )

        "partida" -> EventoServidor.Partida(
            salaId = j.optString("salaId"),
            formato = MatchFormat.fromId(j.optString("formato")),
            membros = j.optJSONArray("membros").membros(),
            totalPerguntas = j.optInt("totalPerguntas")
        )

        "pergunta" -> EventoServidor.Pergunta(
            indice = j.optInt("indice"),
            pergunta = j.optString("pergunta"),
            opcoes = j.optJSONArray("opcoes").textos(),
            dificuldade = j.optString("dificuldade"),
            evento = if (j.isNull("evento")) null else j.optString("evento").ifBlank { null },
            duracao = j.optLong("duracao"),
            fimEm = j.optLong("fimEm")
        )

        "resposta" -> EventoServidor.Resposta(
            indice = j.optInt("indice"),
            certa = j.optBoolean("certa"),
            respostaCorreta = j.optString("respostaCorreta"),
            total = j.optInt("total"),
            certas = j.optInt("certas")
        )

        "placar" -> EventoServidor.Placar(
            pontos = j.optJSONObject("pontos").let { p ->
                if (p == null) emptyMap() else p.keys().asSequence().associateWith { p.optInt(it) }
            }
        )

        "ausente" -> EventoServidor.Presenca(j.optString("uid"), EstadoDePresenca.AUSENTE)
        "voltou" -> EventoServidor.Presenca(j.optString("uid"), EstadoDePresenca.VOLTOU)
        "saiu" -> EventoServidor.Presenca(j.optString("uid"), EstadoDePresenca.SAIU)

        "podio" -> EventoServidor.Podio(
            walkover = j.optBoolean("walkover"),
            ganhei = j.optBoolean("ganhei"),
            meuScore = j.optInt("meuScore"),
            minhasCertas = j.optInt("minhasCertas"),
            maxSequencia = j.optInt("maxSequencia"),
            totalPerguntas = j.optInt("totalPerguntas"),
            ranking = j.optJSONArray("ranking").lugares(),
            equipas = j.optJSONArray("equipas").objectos().map { e ->
                EquipaNoPodio(
                    nome = e.optString("nome"),
                    total = e.optInt("total"),
                    venceu = e.optBoolean("venceu"),
                    jogadores = e.optJSONArray("jogadores").lugares()
                )
            }
        )

        "sonda" -> EventoServidor.Sonda(j.optString("s"))
        "pong" -> EventoServidor.Pong(j.optLong("t0"), j.optLong("tS"))
        "aviso" -> EventoServidor.Aviso(j.optString("codigo"))
        "erro" -> EventoServidor.Erro(j.optString("codigo"), j.optString("msg").ifBlank { null })
        else -> EventoServidor.Desconhecido(tipo)
    }
}.getOrElse { EventoServidor.Erro("trama_invalida", it.message) }

private fun JSONArray?.objectos(): List<JSONObject> =
    if (this == null) emptyList() else (0 until length()).mapNotNull { optJSONObject(it) }

private fun JSONArray?.textos(): List<String> =
    if (this == null) emptyList() else (0 until length()).map { optString(it) }

private fun JSONArray?.membros(): List<MembroDaSala> = objectos().map {
    MembroDaSala(
        uid = it.optString("uid"),
        nome = it.optString("nome").ifBlank { "Jogador" },
        equipa = if (it.isNull("equipa")) null else it.optString("equipa").ifBlank { null }
    )
}

private fun JSONArray?.lugares(): List<LugarNoPodio> = objectos().map {
    LugarNoPodio(
        uid = it.optString("uid"),
        nome = it.optString("nome").ifBlank { "Jogador" },
        pontos = it.optInt("pontos"),
        saiu = it.optBoolean("saiu")
    )
}
