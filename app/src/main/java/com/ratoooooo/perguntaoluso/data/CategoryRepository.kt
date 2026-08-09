package com.ratoooooo.perguntaoluso.data

import com.google.firebase.auth.FirebaseAuth
import com.google.firebase.database.FirebaseDatabase
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.async
import kotlinx.coroutines.awaitAll
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.tasks.await
import kotlinx.coroutines.withContext
import org.json.JSONArray
import org.json.JSONObject
import java.net.HttpURLConnection
import java.net.URL
import java.net.URLEncoder

class CategoryRepository {

    /** Categories present in RTDB but hidden from the player's picker (data stays intact). */
    private val hiddenCategories = setOf("Património Português", "Gastronomia Portuguesa")

    /**
     * Shallow fetch: only category names, not the 1090 nested questions.
     *
     * `/categorias` is no longer world-readable (Fase 22), so the REST call has to carry the
     * caller's Firebase ID token in `auth=`. Every player reaches this screen through
     * [AuthGate], so there is always a uid — anonymous counts.
     */
    suspend fun loadCategories(): List<String> = withContext(Dispatchers.IO) {
        val body = getJson("categorias.json?shallow=true&auth=${authParam()}")
        JSONObject(body).keys().asSequence()
            .filterNot { it in hiddenCategories }
            .toList()
            .sorted()
    }

    /**
     * Nº de perguntas por categoria, para o cartão do picker (mockup, ecrã 4: "312 perguntas").
     *
     * **Porquê `shallow=true` e não uma leitura normal:** `/categorias/{cat}/perguntas` são as
     * perguntas todas, com enunciado, quatro opções e resposta. Ler o nó para chamar `size` traria
     * o jogo inteiro (~1 MB) só para escrever um número no ecrã — e o `QuestionRepository` já volta
     * a descarregar a categoria escolhida a seguir. Com `shallow=true` o RTDB devolve só as chaves
     * (`{"0":true,"1":true,…}`), uns poucos KB para as cinco categorias juntas. É o mesmo recurso
     * que [loadCategories] já usava para não puxar as perguntas ao listar os nomes.
     *
     * Uma chamada por categoria, em paralelo (o `shallow` não sabe contar netos numa só ida). Uma
     * categoria que falhe fica **de fora do mapa** em vez de aparecer com zero: o ecrã trata a
     * ausência como "ainda não sei" e não escreve nada, enquanto "0 perguntas" seria uma mentira
     * plausível sobre uma categoria que está lá.
     */
    suspend fun loadQuestionCounts(categories: List<String>): Map<String, Int> =
        withContext(Dispatchers.IO) {
            if (categories.isEmpty()) return@withContext emptyMap()
            val auth = authParam()
            coroutineScope {
                categories.map { categoria ->
                    async {
                        val count = runCatching { countQuestions(categoria, auth) }.getOrNull()
                        categoria to count
                    }
                }.awaitAll()
            }.mapNotNull { (categoria, count) ->
                count?.let { categoria to it }
            }.toMap()
        }

    private fun countQuestions(categoria: String, auth: String): Int {
        val path = "categorias/${encodeSegment(categoria)}/perguntas"
        val body = getJson("$path.json?shallow=true&auth=$auth")
        return countKeys(body)
    }

    /**
     * O RTDB devolve chaves numéricas sequenciais como **array** (`[true,true,…]`) e tudo o resto
     * como objecto (`{"-Nxy":true,…}`). As perguntas estão guardadas com índices 0..n, por isso na
     * prática vem array — mas aceitar as duas formas evita que a contagem morra se alguma categoria
     * vier a ser semeada com push ids.
     */
    private fun countKeys(body: String): Int {
        val trimmed = body.trim()
        return when {
            trimmed.startsWith("[") -> {
                val array = JSONArray(trimmed)
                (0 until array.length()).count { !array.isNull(it) }
            }
            trimmed.startsWith("{") -> JSONObject(trimmed).length()
            else -> 0 // "null" — categoria sem perguntas
        }
    }

    private suspend fun authParam(): String {
        val token = FirebaseAuth.getInstance().currentUser?.getIdToken(false)?.await()?.token
            ?: error("CategoryRepository called without a signed-in user")
        return URLEncoder.encode(token, "UTF-8")
    }

    /** `URLEncoder` é para query strings e escreve espaços como `+`; num caminho tem de ser `%20`. */
    private fun encodeSegment(value: String): String =
        URLEncoder.encode(value, "UTF-8").replace("+", "%20")

    private fun getJson(pathAndQuery: String): String {
        val rootUrl = FirebaseDatabase.getInstance().reference.toString().trimEnd('/')
        val connection = URL("$rootUrl/$pathAndQuery").openConnection() as HttpURLConnection
        return try {
            connection.inputStream.bufferedReader().use { it.readText() }
        } finally {
            connection.disconnect()
        }
    }
}
