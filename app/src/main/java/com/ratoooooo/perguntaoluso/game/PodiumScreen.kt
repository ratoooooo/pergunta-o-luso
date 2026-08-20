package com.ratoooooo.perguntaoluso.game

import androidx.compose.foundation.background
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
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
import androidx.compose.runtime.key
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.scale
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
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

/** Folga entre linhas do top. */
private val LINHA_TOP_ESPACO = 10.dp

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
    /** Perfil **já agregado** desta partida, para o "quase lá". Null enquanto não chega. */
    profile: com.ratoooooo.perguntaoluso.data.Profile? = null,
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
    // Cada som tem a **sua** trava. Antes só o do resultado a tinha, e as outras duas contavam
    // com o efeito correr uma vez — mas as chaves são precisamente os dados que chegam tarde
    // (nível e conquistas vêm da agregação, depois do ecrã já estar montado). Cada chegada
    // cancelava e reiniciava o bloco, e o "subiu de nível" voltava ao princípio: tocava outra
    // vez, por cima de si próprio.
    val resultadoTocado = remember { mutableStateOf(false) }
    val nivelTocado = remember { mutableStateOf(false) }
    val conquistaTocada = remember { mutableStateOf(false) }
    LaunchedEffect(subiuDeNivel, novasConquistas) {
        if (!resultadoTocado.value) {
            resultadoTocado.value = true
            com.ratoooooo.perguntaoluso.audio.SoundEffects.tocar(
                context,
                if (won) com.ratoooooo.perguntaoluso.audio.SoundEffects.Efeito.VITORIA
                else com.ratoooooo.perguntaoluso.audio.SoundEffects.Efeito.DERROTA
            )
        }
        if (subiuDeNivel && !nivelTocado.value) {
            nivelTocado.value = true
            kotlinx.coroutines.delay(950)
            com.ratoooooo.perguntaoluso.audio.SoundEffects.tocar(
                context, com.ratoooooo.perguntaoluso.audio.SoundEffects.Efeito.SUBIU_NIVEL
            )
        }
        if (novasConquistas.isNotEmpty() && !conquistaTocada.value) {
            conquistaTocada.value = true
            kotlinx.coroutines.delay(if (subiuDeNivel) 1_150 else 950)
            com.ratoooooo.perguntaoluso.audio.SoundEffects.tocar(
                context, com.ratoooooo.perguntaoluso.audio.SoundEffects.Efeito.CONQUISTA
            )
        }
    }

    // O ecrã desliza. Era uma coluna de altura fixa em que a lista tomava o espaço que sobrava
    // (`weight(1f)`); ao acrescentar o cartão "A seguir", o que sobrava dava para **uma** linha do
    // top e o resto ficava cortado. Deslizar é o que os ecrãs de Formato e de Modo já fazem pela
    // mesma razão: em ecrãs mais baixos o conteúdo não desaparece, chega-se lá.
    Column(
        modifier = Modifier
            .fillMaxSize()
            .background(Cream)
            .verticalScroll(rememberScrollState())
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

        // "Quase lá" — o gancho de fim de sessão.
        //
        // Era Cream sobre fundo Cream com contorno fino, ou seja: praticamente sem superfície.
        // Ficava menos destacado do que as linhas do ranking logo a seguir (essas em Lavanda) e
        // escorria visualmente para dentro do título "Melhores pontuações". Passa a usar o padrão
        // que os cartões de Formato e de Modo já usam — **superfície neutra + emblema colorido** —
        // que é o que nesta app diz "isto é um bloco com identidade própria".
        //
        // O emblema é Roxo porque é a cor da progressão (é a da barra de XP e a do `LevelPill`), e
        // porque as outras estão tomadas: Dourado é o mérito no cartão de resultado, Coral é a
        // derrota mesmo por cima, Teal é "resposta certa".
        val objetivo = remember(profile, subiuDeNivel) {
            profile?.let { proximoObjetivo(it, subiuDeNivel) }
        }
        if (objetivo != null && !objetivo.vazio) {
            Spacer(Modifier.size(18.dp))
            Row(
                Modifier.fillMaxWidth()
                    .cascadeIn(3)
                    .stickerBlock(fillColor = Lavender, cornerRadius = 18.dp, shadowOffset = 5.dp, borderWidth = 3.dp)
                    .padding(horizontal = 14.dp, vertical = 10.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                Box(
                    Modifier.size(38.dp).stickerCircle(fillColor = Purple, shadowOffset = 2.dp, borderWidth = 2.dp),
                    contentAlignment = Alignment.Center
                ) {
                    Icon(
                        Icons.Rounded.Flag, contentDescription = null,
                        tint = textColorFor(Purple), modifier = Modifier.size(20.dp)
                    )
                }
                Spacer(Modifier.size(12.dp))
                Column(Modifier.weight(1f)) {
                    Text(
                        "A SEGUIR",
                        style = MaterialTheme.typography.labelLarge.copy(fontSize = 12.sp, lineHeight = 15.sp),
                        color = Purple
                    )
                    objetivo.conquista?.let {
                        Spacer(Modifier.size(3.dp))
                        Text(it, style = MaterialTheme.typography.bodyLarge, color = Ink)
                    }
                    objetivo.nivel?.let {
                        Spacer(Modifier.size(2.dp))
                        Text(it, style = MaterialTheme.typography.bodyLarge, color = Ink)
                    }
                }
            }
        }

        // Folga maior do que a que separa os blocos anteriores: aqui muda-se de secção
        // ("o que te falta" → "o melhor de sempre"), não só de cartão. Não passa disto porque
        // o que sobra é a altura da lista — ver o comentário do `take` mais abaixo.
        Spacer(Modifier.size(24.dp))

        // `fillMaxWidth` para o título **encostar à esquerda**.
        //
        // A coluna do pódio é `CenterHorizontally` e todos os outros blocos ocupam a largura
        // toda, por isso alinham-se sozinhos à esquerda; este título era o único elemento do
        // ecrã sem largura própria — e portanto o único centrado. Resultado: ficava a pairar no
        // vão entre o cartão "A seguir" e as linhas, sem se colar a nenhum dos dois, em vez de
        // se ler como o cabeçalho da lista que vem a seguir. É também o alinhamento que
        // "Estatísticas globais" e "Por modo" já usam no Perfil.
        Text(
            text = "Melhores pontuações",
            style = MaterialTheme.typography.titleLarge,
            color = Ink,
            modifier = Modifier.fillMaxWidth().cascadeIn(3)
        )

        Spacer(Modifier.size(12.dp))

        // Quantas linhas cabem, em vez de um número fixo.
        //
        // Eram três fixas, escolhidas quando o espaço dava para três: **uma linha cortada a meio
        // por cima dos botões parece um erro de desenho**, e era isso que acontecia agora que o
        // cartão "A seguir" ficou por cima e mais alto. Medir o espaço resolve isto sem depender
        // da altura do ecrã nem de eu voltar a acertar um número à mão de cada vez que algo
        // acima cresce. O quadro completo vive no Ranking; aqui basta o pódio.
        //
        // As linhas ficam todas neutras e o 1.º lugar marca-se com um emblema dourado do
        // lado esquerdo. Uma linha inteira dourada competia com o cartão de resultado.
        // Coluna simples, **não** uma `LazyColumn`.
        //
        // São três linhas: não há nada a "reciclar" e a laziness não paga nada. Pagava, isso sim,
        // um problema: dentro de uma coluna que desliza, a `LazyColumn` precisa de altura
        // definida, e a altura tinha de ser calculada à mão. As sombras do sticker são desenhadas
        // **fora** dos limites do bloco, por isso a conta ficava ~1 dp curta, a lista passava a
        // ter scroll próprio, e arrastar o dedo por cima dela deslocava-a dentro da sua janela —
        // via-se o cartão do 1.º lugar cortado. Scroll dentro de scroll, pelo qual eu era o
        // culpado.
        //
        // Uma `Column` mede-se pelo conteúdo: não tem janela, não corta nada, e o deslize do ecrã
        // é o único que existe. Três linhas é o que basta aqui; o quadro completo vive no Ranking.
        //
        // Custo assumido: perde-se o `animateItemPlacement`, que só existe em listas lazy. Não faz
        // falta — a lista **nunca reordena com o ecrã à frente** (`sessionOnly()` limpa
        // `topScores` e `loadTopScores()` corre uma vez por pódio), por isso o que ele protegia
        // não é alcançável. A identidade estável fica no `key(...)`, que é o que a cascata precisa.
        val linhas = topScores.take(3)
        Column(Modifier.fillMaxWidth()) {
            linhas.forEachIndexed { index, entry ->
                val rank = index + 1
                // A cascata é uma animação de **entrada**, e `cascadeIn` volta a corrê-la
                // sempre que o `index` muda. Fixado à identidade da linha, uma pontuação que
                // apenas desce de lugar desliza (ver `animateItemPlacement`) em vez de
                // desaparecer e voltar a aparecer.
                val chave = "${entry.uid}:${entry.timestamp}"
                val escalao = remember(chave) { index + 4 }
                key(chave) {
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .cascadeIn(escalao, key = chave)
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
                Spacer(Modifier.size(LINHA_TOP_ESPACO))
                }
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
