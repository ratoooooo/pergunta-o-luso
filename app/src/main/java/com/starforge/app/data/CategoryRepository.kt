package com.starforge.app.data

import com.google.firebase.database.FirebaseDatabase
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import org.json.JSONObject
import java.net.HttpURLConnection
import java.net.URL

class CategoryRepository {

    /** Categories present in RTDB but hidden from the player's picker (data stays intact). */
    private val hiddenCategories = setOf("Património Português", "Gastronomia Portuguesa")

    /** Shallow fetch: only category names, not the 964 nested questions. */
    suspend fun loadCategories(): List<String> = withContext(Dispatchers.IO) {
        val rootUrl = FirebaseDatabase.getInstance().reference.toString().trimEnd('/')
        val connection = URL("$rootUrl/categorias.json?shallow=true").openConnection() as HttpURLConnection
        try {
            val body = connection.inputStream.bufferedReader().use { it.readText() }
            val json = JSONObject(body)
            json.keys().asSequence()
                .filterNot { it in hiddenCategories }
                .toList()
                .sorted()
        } finally {
            connection.disconnect()
        }
    }
}
