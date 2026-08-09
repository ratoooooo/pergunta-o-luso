package com.ratoooooo.perguntaoluso.game

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.EmojiEvents
import androidx.compose.material.icons.rounded.Flag
import androidx.compose.material.icons.rounded.Home
import androidx.compose.material.icons.rounded.Refresh
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.scale
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import com.ratoooooo.perguntaoluso.data.ScoreEntry
import com.ratoooooo.perguntaoluso.ui.theme.Coral
import com.ratoooooo.perguntaoluso.ui.theme.Cream
import com.ratoooooo.perguntaoluso.ui.theme.Gold
import com.ratoooooo.perguntaoluso.ui.theme.Ink
import com.ratoooooo.perguntaoluso.ui.theme.Lavender
import com.ratoooooo.perguntaoluso.ui.theme.Purple
import com.ratoooooo.perguntaoluso.ui.theme.StickerButton
import com.ratoooooo.perguntaoluso.ui.theme.cascadeIn
import com.ratoooooo.perguntaoluso.ui.theme.rememberPulse
import com.ratoooooo.perguntaoluso.ui.theme.stickerBlock
import com.ratoooooo.perguntaoluso.ui.theme.stickerCircle
import com.ratoooooo.perguntaoluso.ui.theme.textColorFor

@Composable
fun PodiumScreen(
    categoria: String,
    mode: GameMode,
    points: Int,
    correctCount: Int,
    total: Int,
    eliminated: Boolean,
    won: Boolean,
    subiuDeNivel: Boolean = false,
    novasConquistas: List<String> = emptyList(),
    topScores: List<ScoreEntry>,
    onPlayAgain: () -> Unit,
    onHome: () -> Unit
) {
    val context = androidx.compose.ui.platform.LocalContext.current

    // Os três sons do fim de partida em sequência, nunca em cima uns dos outros: resultado,
    // depois subida de nível, depois conquista. Tocados ao mesmo tempo davam um amontoado
    // sem se perceber que houve três coisas a acontecer.
    //
    // `subiuDeNivel` e `novasConquistas` só chegam depois de a agregação escrever e o perfil
    // voltar a ser lido, por isso são chaves do efeito — quando chegarem, o bloco volta a
    // correr. O som do resultado é reproduzido uma só vez (guardado em `resultadoTocado`),
    // para não repetir quando as conquistas aterram.
    val resultadoTocado = remember { mutableStateOf(false) }
    LaunchedEffect(subiuDeNivel, novasConquistas) {
        if (!resultadoTocado.value) {
            resultadoTocado.value = true
            com.ratoooooo.perguntaoluso.audio.SoundEffects.tocar(
                context,
                if (won) com.ratoooooo.perguntaoluso.audio.SoundEffects.Efeito.VITORIA
                else com.ratoooooo.perguntaoluso.audio.SoundEffects.Efeito.DERROTA
            )
        }
        if (subiuDeNivel) {
            kotlinx.coroutines.delay(950)
            com.ratoooooo.perguntaoluso.audio.SoundEffects.tocar(
                context, com.ratoooooo.perguntaoluso.audio.SoundEffects.Efeito.SUBIU_NIVEL
            )
        }
        if (novasConquistas.isNotEmpty()) {
            kotlinx.coroutines.delay(if (subiuDeNivel) 1_150 else 950)
            com.ratoooooo.perguntaoluso.audio.SoundEffects.tocar(
                context, com.ratoooooo.perguntaoluso.audio.SoundEffects.Efeito.CONQUISTA
            )
        }
    }

    Column(
        modifier = Modifier
            .fillMaxSize()
            .background(Cream)
            .padding(24.dp),
        horizontalAlignment = Alignment.CenterHorizontally
    ) {
        val title = when {
            eliminated -> "Eliminado!"
            won -> "Vitória!"
            else -> "Fim de jogo!"
        }
        ScreenHeader(title = title, onBack = onHome)

        Spacer(Modifier.size(16.dp))

        // O cartão de resultado toma a cor do desfecho em vez de ser sempre dourado: um
        // troféu dourado por cima de "Eliminado!" celebrava uma derrota. Dourado = vitória,
        // Coral = eliminado, Lavanda = fim de jogo sem vitória.
        val resultColor = when {
            eliminated -> Coral
            won -> Gold
            else -> Lavender
        }
        // Revelação em cascata: resultado → estatísticas → ranking, cada bloco a seguir ao
        // anterior (ver `cascadeIn`), em vez de tudo aparecer já composto de uma vez.
        Box(
            modifier = Modifier
                .fillMaxWidth()
                .cascadeIn(0)
                .stickerBlock(fillColor = resultColor, cornerRadius = 28.dp, shadowOffset = 7.dp)
                .padding(vertical = 20.dp),
            contentAlignment = Alignment.Center
        ) {
            Column(horizontalAlignment = Alignment.CenterHorizontally) {
                // Vencedor: o troféu pulsa (destaque mais forte que uma simples vitória sem
                // classificação alta) — a única animação contínua deste cartão.
                val pulso = rememberPulse(ativo = won, min = 1f, max = 1.14f, periodoMs = 900)
                Icon(
                    imageVector = if (won) Icons.Rounded.EmojiEvents else Icons.Rounded.Flag,
                    contentDescription = null,
                    tint = textColorFor(resultColor),
                    modifier = Modifier.size(40.dp).scale(pulso)
                )
                Text(
                    text = "$categoria · ${mode.displayName}",
                    style = MaterialTheme.typography.labelLarge,
                    color = textColorFor(resultColor)
                )
                Text(
                    text = "$points",
                    style = MaterialTheme.typography.headlineLarge,
                    color = textColorFor(resultColor)
                )
                Text(
                    text = "pontos · $correctCount de $total certas",
                    style = MaterialTheme.typography.bodyLarge,
                    color = textColorFor(resultColor)
                )
            }
        }

        Spacer(Modifier.size(16.dp))

        ResultStats(
            modoId = mode.id,
            perguntas = total,
            respostasCertas = correctCount,
            venceu = won,
            modifier = Modifier.cascadeIn(2)
        )

        Spacer(Modifier.size(20.dp))

        Text(
            text = "Melhores pontuações",
            style = MaterialTheme.typography.titleLarge,
            color = Ink,
            modifier = Modifier.cascadeIn(3)
        )

        Spacer(Modifier.size(12.dp))

        // Top 3, não 5: com cinco linhas a lista era cortada a meio da quarta, e uma linha
        // meio visível por cima dos botões parece um erro de desenho. O quadro completo
        // vive no Ranking; aqui basta o pódio.
        // As linhas ficam todas neutras e o 1.º lugar marca-se com um emblema dourado do
        // lado esquerdo. Uma linha inteira dourada competia com o cartão de resultado.
        LazyColumn(modifier = Modifier.fillMaxWidth().weight(1f)) {
            itemsIndexed(topScores.take(3)) { index, entry ->
                val rank = index + 1
                // Continua a cascata do bloco anterior (índices 4+, depois do resultado e
                // das estatísticas) — a lista não aparece toda de repente por cima delas.
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .cascadeIn(index + 4, key = "podiumTop")
                        .stickerBlock(fillColor = Lavender, cornerRadius = 18.dp, shadowOffset = 4.dp)
                        .padding(horizontal = 14.dp, vertical = 12.dp),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    // #1 pulsa também — o mesmo "mais forte" do troféu, aqui a marcar o
                    // melhor resultado histórico, não o desta partida.
                    val pulsoRank = rememberPulse(ativo = rank == 1, min = 1f, max = 1.1f, periodoMs = 1000)
                    Box(
                        Modifier.size(32.dp).scale(pulsoRank).stickerCircle(
                            fillColor = if (rank == 1) Gold else Lavender,
                            shadowOffset = 2.dp, borderWidth = 2.dp
                        ),
                        contentAlignment = Alignment.Center
                    ) {
                        Text("$rank", style = MaterialTheme.typography.labelLarge, color = Ink)
                    }
                    Spacer(Modifier.size(12.dp))
                    Column(modifier = Modifier.weight(1f)) {
                        Text(
                            text = entry.categoria.ifBlank { "—" },
                            style = MaterialTheme.typography.bodyLarge,
                            color = Ink
                        )
                        Text(
                            text = modeLabel(entry.modo),
                            style = MaterialTheme.typography.labelLarge,
                            color = Ink
                        )
                    }
                    Text(
                        text = "${entry.score} pts",
                        style = MaterialTheme.typography.titleLarge,
                        color = Ink
                    )
                }
                Spacer(Modifier.size(10.dp))
            }
        }

        Spacer(Modifier.size(12.dp))

        StickerButton(
            text = "JOGAR NOVAMENTE",
            icon = Icons.Rounded.Refresh,
            onClick = onPlayAgain,
            fillColor = Purple,
            modifier = Modifier.fillMaxWidth()
        )
        Spacer(Modifier.size(12.dp))
        StickerButton(
            text = "VOLTAR AO INÍCIO",
            icon = Icons.Rounded.Home,
            onClick = onHome,
            fillColor = Lavender,
            modifier = Modifier.fillMaxWidth()
        )
    }
}

private fun modeLabel(modeId: String): String = when (modeId) {
    GameMode.CLASSICO.id -> GameMode.CLASSICO.displayName
    GameMode.CAOTICO.id -> GameMode.CAOTICO.displayName
    GameMode.ELIMINATORIAS.id -> GameMode.ELIMINATORIAS.displayName
    else -> "—"
}
