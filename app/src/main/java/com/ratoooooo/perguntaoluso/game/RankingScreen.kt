package com.ratoooooo.perguntaoluso.game

import com.ratoooooo.perguntaoluso.ui.theme.cascadeIn
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
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
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.ratoooooo.perguntaoluso.data.ModeStats
import com.ratoooooo.perguntaoluso.data.Profile
import com.ratoooooo.perguntaoluso.game.avatar.AvatarView
import com.ratoooooo.perguntaoluso.game.multi.MatchFormat
import com.ratoooooo.perguntaoluso.ui.theme.Cream
import com.ratoooooo.perguntaoluso.ui.theme.Gold
import com.ratoooooo.perguntaoluso.ui.theme.Ink
import com.ratoooooo.perguntaoluso.ui.theme.Lavender
import com.ratoooooo.perguntaoluso.ui.theme.LevelPill
import com.ratoooooo.perguntaoluso.ui.theme.Purple
import com.ratoooooo.perguntaoluso.ui.theme.SegmentedTabs
import com.ratoooooo.perguntaoluso.ui.theme.UnderlineTabs
import com.ratoooooo.perguntaoluso.ui.theme.stickerBlock
import com.ratoooooo.perguntaoluso.ui.theme.stickerCircle
import com.ratoooooo.perguntaoluso.ui.theme.textColorFor

/**
 * O Ranking tem duas famílias de tabelas. A dimensão escolhe qual:
 *
 * - **Por modo** — Clássico / Caótico / Eliminatórias, a partir de `modos/{modo}` no perfil.
 * - **Por formato** — 1x1 / 2x2 / Grupo, a partir de `multiVitorias` / `multiJogos`.
 *
 * Ambas saem de `/jogadores`, nunca de `/scores`: o agregado tem uma linha por jogador, e as
 * pontuações em bruto teriam o mesmo jogador várias vezes na mesma tabela.
 */
private enum class RankDimension(val label: String) {
    MODO("Por modo"),
    FORMATO("Por formato")
}

private data class RankingList(
    val title: String,
    val suffix: String,
    /** Valor desta tabela para um perfil, dada a chave (id do modo ou do formato). */
    val value: (Profile, String) -> Int
)

private fun modeStats(p: Profile, modo: String): ModeStats = p.modos[modo] ?: ModeStats()

private val LISTAS_POR_MODO = listOf(
    RankingList("Mais vitórias", "vit") { p, k -> modeStats(p, k).vitorias },
    RankingList("Mais pontos", "pts") { p, k -> modeStats(p, k).pontos },
    RankingList("Melhor recorde", "pts") { p, k -> modeStats(p, k).recorde }
)

/** Jogos mínimos para entrar na tabela de percentagem — ver [LISTAS_POR_FORMATO]. */
private const val MIN_JOGOS_PARA_PERCENTAGEM = 3

/**
 * **Limitação assumida: por formato só existem duas contagens** — `multiVitorias` e
 * `multiJogos` (Fase 13, escritas para as conquistas de multijogador). Não há pontos nem
 * recorde por formato em `/jogadores`, por isso "Mais pontos" e "Melhor recorde" **não são
 * deriváveis** aqui e não foram inventados campos novos para os fabricar.
 *
 * Em vez disso as três tabelas usam os três eixos que os dois contadores existentes dão de
 * facto — e que são os mesmos três eixos das tabelas por modo: pico, volume e consistência.
 * A percentagem exige [MIN_JOGOS_PARA_PERCENTAGEM] jogos, senão quem ganhou o único jogo que
 * fez aparecia em 1.º com 100 % à frente de quem ganhou 8 em 10.
 */
private val LISTAS_POR_FORMATO = listOf(
    RankingList("Mais vitórias", "vit") { p, k -> p.multiVitorias[k] ?: 0 },
    RankingList("Mais jogos", "jogos") { p, k -> p.multiJogos[k] ?: 0 },
    RankingList("% vitórias", "%") { p, k ->
        val jogos = p.multiJogos[k] ?: 0
        val vitorias = p.multiVitorias[k] ?: 0
        if (jogos >= MIN_JOGOS_PARA_PERCENTAGEM) vitorias * 100 / jogos else 0
    }
)

@Composable
fun RankingScreen(
    profiles: List<Profile>,
    isLoading: Boolean,
    meuUid: String?,
    onBack: () -> Unit,
    onHome: () -> Unit,
    onFriends: () -> Unit,
    onProfile: () -> Unit
) {
    var selectedDimIndex by rememberSaveable { mutableIntStateOf(0) }
    var selectedModeIndex by rememberSaveable { mutableIntStateOf(0) }
    var selectedListIndex by rememberSaveable { mutableIntStateOf(0) }

    val dimension = RankDimension.entries[selectedDimIndex.coerceIn(RankDimension.entries.indices)]
    val formatos = MatchFormat.entries

    // Chaves e tabelas mudam com a dimensão; o índice do valor é partilhado entre as duas
    // (ambas têm três) e o da lista também, por isso trocar de dimensão mantém a posição.
    val chaves = when (dimension) {
        RankDimension.MODO -> GameMode.entries.map { it.id }
        RankDimension.FORMATO -> formatos.map { it.id }
    }
    val rotulos = when (dimension) {
        RankDimension.MODO -> GameMode.entries.map { it.displayName }
        RankDimension.FORMATO -> formatos.map { it.displayName }
    }
    val listas = when (dimension) {
        RankDimension.MODO -> LISTAS_POR_MODO
        RankDimension.FORMATO -> LISTAS_POR_FORMATO
    }
    val chave = chaves[selectedModeIndex.coerceIn(chaves.indices)]
    val selectedList = listas[selectedListIndex.coerceIn(listas.indices)]

    // "Jogou isto" — quem nunca jogou o modo/formato não entra na tabela, nem sequer com 0.
    val jogou: (Profile) -> Boolean = when (dimension) {
        RankDimension.MODO -> { p -> (p.modos[chave]?.jogos ?: 0) > 0 }
        RankDimension.FORMATO -> { p -> (p.multiJogos[chave] ?: 0) > 0 }
    }

    com.ratoooooo.perguntaoluso.game.MainScaffold(
        active = com.ratoooooo.perguntaoluso.ui.theme.NavTab.RANKING,
        onHome = onHome,
        onRanking = {},
        onFriends = onFriends,
        onProfile = onProfile
    ) {
        ScreenHeader(
            title = "Ranking",
            onBack = onBack
        )

        Spacer(Modifier.size(14.dp))

        // Três níveis, mas só dois pesos visuais — e o pesado fica no meio, onde já estava.
        //
        // A dimensão (Por modo / Por formato) entra como sublinhado por cima: é uma troca rara,
        // de duas opções, e não deve pesar mais do que a escolha que o jogador faz a seguir.
        // A pastilha roxa continua a ser o modo/formato, tal como antes desta fase, e a lista
        // continua em sublinhado por baixo. Assim o ecrã de hoje mantém-se reconhecível e a
        // linha nova não introduz um terceiro estilo de separador.
        UnderlineTabs(
            labels = RankDimension.entries.map { it.label },
            selectedIndex = selectedDimIndex,
            onSelect = { novo ->
                if (novo != selectedDimIndex) {
                    selectedDimIndex = novo
                    // As duas dimensões têm três chaves e três listas, mas não significam o
                    // mesmo: manter "Eliminatórias" seleccionado ao passar para formato daria
                    // "Grupo" sem o jogador ter pedido nada. Recomeça-se na primeira de cada.
                    selectedModeIndex = 0
                    selectedListIndex = 0
                }
            }
        )

        Spacer(Modifier.size(10.dp))

        SegmentedTabs(
            labels = rotulos,
            selectedIndex = selectedModeIndex,
            onSelect = { selectedModeIndex = it }
        )

        Spacer(Modifier.size(10.dp))

        UnderlineTabs(
            labels = listas.map { it.title },
            selectedIndex = selectedListIndex,
            onSelect = { selectedListIndex = it }
        )

        Spacer(Modifier.size(14.dp))

        if (isLoading) {
            Text("A carregar...", style = MaterialTheme.typography.bodyLarge, color = Ink)
        } else {
            val played = profiles.filter(jogou)
            LazyColumn(verticalArrangement = Arrangement.spacedBy(10.dp)) {
                item(key = "$chave-${selectedList.title}") {
                    RankingSection(
                        list = selectedList,
                        chave = chave,
                        profiles = played,
                        meuUid = meuUid,
                        // A tabela de percentagem esconde quem tem poucos jogos; sem explicação
                        // parece que o ranking se esqueceu de alguém.
                        nota = if (dimension == RankDimension.FORMATO && selectedList.suffix == "%")
                            "Só entram jogadores com $MIN_JOGOS_PARA_PERCENTAGEM ou mais jogos neste formato."
                        else null
                    )
                }
            }
        }
    }
}

@Composable
private fun RankingSection(
    list: RankingList,
    chave: String,
    profiles: List<Profile>,
    meuUid: String?,
    nota: String? = null
) {
    // Filtrar **antes** de cortar aos 5: com o `take` primeiro, um zero no top-5 gastava um
    // lugar e a tabela mostrava menos gente do que tinha para mostrar.
    val ranked = profiles
        .filter { list.value(it, chave) > 0 }
        .sortedByDescending { list.value(it, chave) }
        .take(5)

    Column(modifier = Modifier.fillMaxWidth()) {
        // O título da lista saiu daqui: repetia à letra o separador escolhido logo acima.
        if (ranked.isEmpty()) {
            Text("Ainda sem dados.", style = MaterialTheme.typography.bodyLarge, color = Ink)
            Nota(nota)
            return
        }
        // A nota fica **por cima** da lista e aparece mesmo com a tabela cheia: é aí que faz
        // mais falta. Uma tabela com cinco nomes parece completa, e quem não se encontra nela
        // por ter poucos jogos não teria como saber porquê.
        Nota(nota)
        ranked.forEachIndexed { index, profile ->
            val rank = index + 1
            val rowColor = if (rank == 1) Gold else Lavender
            val sou = meuUid != null && profile.uid == meuUid
            val value = list.value(profile, chave)
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(bottom = 10.dp)
                    // Cascata ao abrir/trocar de modo — a chave inclui o modo para a lista
                    // voltar a compor-se quando o jogador muda de separador.
                    .cascadeIn(index, key = chave + list.title)
                    // "Sou eu" = contorno roxo. Sem isto era preciso ler todos os nomes para
                    // me encontrar na tabela.
                    .stickerBlock(
                        fillColor = rowColor, cornerRadius = 16.dp, shadowOffset = 4.dp,
                        borderColor = if (sou) Purple else Ink
                    )
                    .padding(horizontal = 12.dp, vertical = 10.dp),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Text(
                    text = "$rank",
                    style = MaterialTheme.typography.titleLarge,
                    color = textColorFor(rowColor)
                )
                Spacer(Modifier.size(10.dp))
                // Encaixe creme por trás do avatar: um avatar dourado (pastel de nata, galo)
                // em cima da linha dourada do 1.º lugar desaparecia — ficava só o contorno.
                Box(
                    Modifier.size(46.dp).stickerCircle(fillColor = Cream, shadowOffset = 2.dp, borderWidth = 2.dp),
                    contentAlignment = Alignment.Center
                ) {
                    AvatarView(
                        avatarId = profile.avatar, iniciais = profile.iniciais,
                        modifier = Modifier.size(36.dp), shadowOffset = 0.dp
                    )
                }
                Spacer(Modifier.size(10.dp))
                Column(Modifier.weight(1f)) {
                    Text(
                        text = if (sou) "${profile.nomeVisivel} (tu)" else profile.nomeVisivel,
                        style = MaterialTheme.typography.bodyLarge.copy(fontWeight = FontWeight.Bold),
                        color = textColorFor(rowColor),
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis
                    )
                    Spacer(Modifier.size(3.dp))
                    LevelPill(nivel = profile.nivel, patente = profile.patente.titulo)
                }
                Spacer(Modifier.size(8.dp))
                Text(
                    text = "$value ${list.suffix}",
                    style = MaterialTheme.typography.labelLarge,
                    color = textColorFor(rowColor)
                )
            }
        }
    }
}

/** Explicação de porque é que uma tabela pode esconder jogadores. Nada quando não há o que explicar. */
@Composable
private fun Nota(texto: String?) {
    if (texto == null) return
    Text(
        texto,
        style = MaterialTheme.typography.labelLarge,
        color = Ink.copy(alpha = 0.7f),
        modifier = Modifier.padding(bottom = 10.dp)
    )
}
