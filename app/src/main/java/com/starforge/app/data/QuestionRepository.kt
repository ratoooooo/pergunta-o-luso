package com.starforge.app.data

import com.google.firebase.database.DataSnapshot
import com.google.firebase.database.FirebaseDatabase
import com.starforge.app.game.Difficulty
import kotlinx.coroutines.suspendCancellableCoroutine
import kotlin.coroutines.resume
import kotlin.coroutines.resumeWithException

class QuestionRepository {

    /**
     * Loads [count] questions for a category as a difficulty progression ramp:
     * questions are shuffled within each difficulty tier, then concatenated
     * facil -> medio -> dificil, so order still varies each game but ramps up.
     */
    suspend fun loadGameQuestions(categoria: String, count: Int): List<Question> {
        val snapshot = getSnapshot("categorias/$categoria/perguntas")
        val all = snapshot.children.mapNotNull { child -> child.toQuestion() }
        return buildProgression(all, count)
    }

    private fun buildProgression(all: List<Question>, count: Int): List<Question> {
        val byTier = Difficulty.entries.associateWith { tier ->
            all.filter { Difficulty.fromId(it.dificuldade) == tier }.shuffled().toMutableList()
        }

        // Target split across the three tiers, biased so easy comes first, hard last.
        val easyTarget = Math.ceil(count / 3.0).toInt()
        val hardTarget = count / 3
        val mediumTarget = count - easyTarget - hardTarget

        val ramp = mutableListOf<Question>()
        ramp += takeUpTo(byTier.getValue(Difficulty.FACIL), easyTarget)
        ramp += takeUpTo(byTier.getValue(Difficulty.MEDIO), mediumTarget)
        ramp += takeUpTo(byTier.getValue(Difficulty.DIFICIL), hardTarget)

        // If some tier was short, backfill from whatever remains, preserving ramp order.
        if (ramp.size < count) {
            val leftovers = byTier.values.flatten()
            ramp += leftovers.take(count - ramp.size)
        }
        return ramp.take(count)
    }

    private fun takeUpTo(pool: MutableList<Question>, n: Int): List<Question> {
        val taken = pool.take(n)
        repeat(taken.size) { if (pool.isNotEmpty()) pool.removeAt(0) }
        return taken
    }

    private suspend fun getSnapshot(path: String): DataSnapshot =
        suspendCancellableCoroutine { continuation ->
            FirebaseDatabase.getInstance().getReference(path).get()
                .addOnSuccessListener { snapshot -> continuation.resume(snapshot) }
                .addOnFailureListener { error -> continuation.resumeWithException(error) }
        }

    private fun DataSnapshot.toQuestion(): Question? {
        val pergunta = child("pergunta").getValue(String::class.java) ?: return null
        val resposta = child("respostaCorreta").getValue(String::class.java) ?: return null
        val dificuldade = child("dificuldade").getValue(String::class.java) ?: ""
        val opcoes = child("opcoes").children.mapNotNull { it.getValue(String::class.java) }
        if (opcoes.size < 2) return null
        val vf = opcoes.size == 2 &&
            opcoes.map { it.trim().lowercase() }.toSet() == setOf(VERDADEIRO.lowercase(), FALSO.lowercase())
        return Question(
            pergunta = pergunta,
            // Multiple choice is shuffled so the answer never sits in a fixed slot; True/False
            // keeps its canonical order instead (shuffling "Verdadeiro"/"Falso" reads as a bug).
            opcoes = if (vf) listOf(VERDADEIRO, FALSO) else opcoes.shuffled(),
            respostaCorreta = resposta,
            dificuldade = dificuldade
        )
    }
}
