package com.ratoooooo.perguntaoluso.game.multi

import androidx.compose.animation.AnimatedContent
import androidx.compose.animation.ContentTransform
import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.scaleIn
import androidx.compose.animation.togetherWith
import androidx.compose.foundation.background
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.Bolt
import androidx.compose.material.icons.rounded.Check
import androidx.compose.material.icons.rounded.CheckCircle
import androidx.compose.material.icons.rounded.Close
import androidx.compose.material.icons.rounded.EmojiEvents
import androidx.compose.material.icons.rounded.Groups
import androidx.compose.material.icons.rounded.Home
import androidx.compose.material.icons.rounded.MoreHoriz
import androidx.compose.material.icons.rounded.Refresh
import androidx.compose.material.icons.rounded.Schedule
import androidx.compose.material.icons.rounded.SportsKabaddi
import androidx.compose.material.icons.rounded.SportsMma
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.scale
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import com.ratoooooo.perguntaoluso.game.ScreenHeader
import com.ratoooooo.perguntaoluso.ui.theme.AnswerPalette
import com.ratoooooo.perguntaoluso.ui.theme.Coral
import com.ratoooooo.perguntaoluso.ui.theme.Cream
import com.ratoooooo.perguntaoluso.ui.theme.Gold
import com.ratoooooo.perguntaoluso.ui.theme.Ink
import com.ratoooooo.perguntaoluso.ui.theme.Lavender
import com.ratoooooo.perguntaoluso.ui.theme.Neutral
import com.ratoooooo.perguntaoluso.ui.theme.Purple
import com.ratoooooo.perguntaoluso.ui.theme.StickerButton
import com.ratoooooo.perguntaoluso.ui.theme.Teal
import com.ratoooooo.perguntaoluso.ui.theme.Motion
import com.ratoooooo.perguntaoluso.ui.theme.bounceIn
import com.ratoooooo.perguntaoluso.ui.theme.cascadeIn
import com.ratoooooo.perguntaoluso.ui.theme.rememberPulse
import com.ratoooooo.perguntaoluso.ui.theme.stickerBlock
import com.ratoooooo.perguntaoluso.ui.theme.stickerCircle
import com.ratoooooo.perguntaoluso.ui.theme.stickerDashed
import com.ratoooooo.perguntaoluso.ui.theme.textColorFor
import com.ratoooooo.perguntaoluso.data.multi.LobbyData
import kotlinx.coroutines.delay

@Composable
fun MultiMatchScreen(
    state: MultiUiState,
    onSelectAnswer: (String) -> Unit,
    onLeave: () -> Unit,
    onPlayAgain: () -> Unit,
    onHome: () -> Unit,
    onForceStart: () -> Unit = {},
    onSwitchLobby: (LobbyData) -> Unit = {}
) {
    // Os dois estados que só o servidor da partida introduz. A manutenção substitui o ecrã — não
    // há partida nenhuma para mostrar por baixo. A reconexão é uma faixa POR CIMA do jogo, de
    // propósito: o lugar ainda é nosso durante a carência, e trocar a partida por um ecrã de
    // espera seria deitar fora o que ainda dá para recuperar.
    if (state.emManutencao) {
        ManutencaoView(onHome)
        return
    }

    Box(Modifier.fillMaxSize()) {
        when (state.phase) {
            MultiPhase.SEARCHING -> WaitingRoom(
                state = state,
                onCancel = onLeave,
                onForceStart = onForceStart,
                onSwitchLobby = onSwitchLobby
            )
            MultiPhase.MATCHED -> Matched(state)
            MultiPhase.ERROR -> ErrorView(state.error, onHome)
            MultiPhase.PODIUM -> Podium(state, onPlayAgain, onHome)
            MultiPhase.IN_GAME -> QuestionView(state, onSelectAnswer)
        }
        if (state.aReconectar) FaixaDeReconexao(Modifier.align(Alignment.BottomCenter))
    }
}

/**
 * O servidor está a drenar para actualizar: recusa partidas novas e deixa acabar as que estão a
 * correr. Não é erro — é uma janela de menos de um minuto —, por isso não usa o vermelho do
 * [ErrorView].
 */
@Composable
private fun ManutencaoView(onHome: () -> Unit) {
    Box(Modifier.fillMaxSize().background(Cream), contentAlignment = Alignment.Center) {
        Column(horizontalAlignment = Alignment.CenterHorizontally, modifier = Modifier.padding(32.dp)) {
            Text(
                "O servidor está a actualizar.",
                style = MaterialTheme.typography.headlineSmall, color = Ink, textAlign = TextAlign.Center
            )
            Spacer(Modifier.size(8.dp))
            Text(
                "As partidas a decorrer acabam primeiro. Tenta daqui a um minuto.",
                style = MaterialTheme.typography.bodyLarge, color = Ink, textAlign = TextAlign.Center
            )
            Spacer(Modifier.size(24.dp))
            StickerButton("VOLTAR", Icons.Rounded.Home, onHome)
        }
    }
}

/** Caiu a ligação com a partida a decorrer. O lugar fica guardado enquanto a carência durar. */
@Composable
private fun FaixaDeReconexao(modifier: Modifier = Modifier) {
    Box(
        modifier
            .fillMaxWidth()
            .padding(16.dp)
            .stickerBlock(fillColor = Gold, cornerRadius = 20.dp)
            .padding(vertical = 14.dp),
        contentAlignment = Alignment.Center
    ) {
        Text(
            "A reconectar…  o teu lugar está guardado",
            style = MaterialTheme.typography.labelLarge, color = Ink, textAlign = TextAlign.Center
        )
    }
}

@Composable
private fun Matched(state: MultiUiState) {
    Column(
        Modifier.fillMaxSize().background(Cream).padding(24.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.Center
    ) {
        Text("Encontrado!", style = MaterialTheme.typography.headlineLarge, color = Ink)
        Spacer(Modifier.size(24.dp))
        if (state.format.teamBased) {
            val a = state.players.filter { it.team == "A" }
            val b = state.players.filter { it.team == "B" }
            Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(14.dp)) {
                MatchedTeam("Equipa A", a, Teal, Modifier.weight(1f).bounceIn(0))
                MatchedTeam("Equipa B", b, Purple, Modifier.weight(1f).bounceIn(1))
            }
        } else {
            Column(Modifier.fillMaxWidth(), verticalArrangement = Arrangement.spacedBy(12.dp)) {
                state.players.forEachIndexed { index, p ->
                    Box(
                        Modifier.fillMaxWidth()
                            .bounceIn(index)
                            .stickerBlock(
                                fillColor = Lavender, cornerRadius = 18.dp, shadowOffset = 4.dp,
                                borderColor = if (p.isMe) Purple else Ink
                            )
                            .padding(vertical = 16.dp),
                        contentAlignment = Alignment.Center
                    ) {
                        Text(if (p.isMe) "${p.nome} (tu)" else p.nome, style = MaterialTheme.typography.titleLarge, color = Ink)
                    }
                }
            }
        }
        Spacer(Modifier.size(24.dp))
        Text("A começar...", style = MaterialTheme.typography.bodyLarge, color = Ink)
    }
}

@Composable
private fun MatchedTeam(name: String, players: List<PlayerLive>, color: Color, modifier: Modifier = Modifier) {
    Column(
        modifier.stickerBlock(fillColor = color, cornerRadius = 22.dp, shadowOffset = 6.dp).padding(vertical = 18.dp, horizontal = 12.dp),
        horizontalAlignment = Alignment.CenterHorizontally
    ) {
        Text(name, style = MaterialTheme.typography.titleLarge, color = textColorFor(color))
        Spacer(Modifier.size(8.dp))
        players.forEach { p ->
            Text(if (p.isMe) "${p.nome} (tu)" else p.nome, style = MaterialTheme.typography.bodyLarge, color = textColorFor(color), maxLines = 1)
        }
    }
}

/**
 * Waiting/matchmaking room.
 */
@Composable
private fun WaitingRoom(
    state: MultiUiState,
    onCancel: () -> Unit,
    onForceStart: () -> Unit = {},
    onSwitchLobby: (LobbyData) -> Unit = {}
) {
    val format = state.format
    val title = when (format) {
        MatchFormat.ONE_V_ONE -> "À Procura de Adversário"
        MatchFormat.TWO_V_TWO -> "À Procura de Equipa"
        MatchFormat.GRUPO -> "À Procura de Jogadores"
    }
    val icon = when (format) {
        MatchFormat.ONE_V_ONE -> Icons.Rounded.SportsKabaddi
        MatchFormat.TWO_V_TWO -> Icons.Rounded.SportsMma
        MatchFormat.GRUPO -> Icons.Rounded.Groups
    }
    var elapsedMs by remember { mutableStateOf(0L) }
    var showOtherLobbies by remember { mutableStateOf(false) }

    LaunchedEffect(Unit) {
        val start = System.currentTimeMillis()
        while (true) { elapsedMs = System.currentTimeMillis() - start; delay(500) }
    }

    val occupants: List<PlayerLive> = if (state.players.isNotEmpty()) state.players
        else listOf(PlayerLive("me", state.myName.ifBlank { "Tu" }, 0, null, isMe = true, left = false))

    val modoLabel = if (state.modo == "caotico") "Caótico" else "Clássico"
    val categoryLabel = state.categoria.ifBlank { "Geral" }
    val otherLobbies = state.openLobbies.filter { lobby ->
        lobby.lobbyId != state.currentLobbyId && lobby.membros.none { m -> m.first == state.myUid }
    }

    Column(Modifier.fillMaxSize().background(Cream).padding(horizontal = 24.dp).padding(top = 24.dp, bottom = 20.dp)) {
        // Fase 30: tudo acima dos botões passa a deslizar, e os botões ficam fixos em baixo.
        //
        // O Grupo desenha `format.players` lugares — são **10**. A lista enchia o ecrã, o
        // `Spacer(weight(1f))` colapsava a zero, e "INICIAR JOGO" e "CANCELAR PROCURA" ficavam
        // desenhados abaixo da área visível, sem scroll para lá chegar: não dava para começar a
        // partida NEM para sair da sala. O formato ficava injogável em qualquer telemóvel cujo
        // ecrã não coubesse os 10 lugares.
        Column(
            Modifier
                .weight(1f)
                .fillMaxWidth()
                .verticalScroll(rememberScrollState())
        ) {
        ScreenHeader(title = title)
        Spacer(Modifier.size(16.dp))

        // Search status card
        Column(
            Modifier.fillMaxWidth().stickerBlock(fillColor = Purple, cornerRadius = 26.dp, shadowOffset = 6.dp).padding(vertical = 18.dp),
            horizontalAlignment = Alignment.CenterHorizontally
        ) {
            Box(Modifier.size(52.dp).stickerCircle(fillColor = Cream, shadowOffset = 3.dp, borderWidth = 2.dp), contentAlignment = Alignment.Center) {
                Icon(icon, contentDescription = null, tint = Ink, modifier = Modifier.size(28.dp))
            }
            Spacer(Modifier.size(10.dp))
            Text("Sala de Espera", style = MaterialTheme.typography.titleLarge, color = Cream, textAlign = TextAlign.Center)
            Spacer(Modifier.size(4.dp))
            Row(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                Text(
                    "[$categoryLabel · $modoLabel]",
                    style = MaterialTheme.typography.labelLarge.copy(fontWeight = FontWeight.Bold),
                    color = Gold
                )
            }
            Spacer(Modifier.size(4.dp))
            Text("${state.joinedCount} / ${format.players} inscritos", style = MaterialTheme.typography.headlineLarge, color = Cream)
            Spacer(Modifier.size(10.dp))
            Row(verticalAlignment = Alignment.CenterVertically) {
                Icon(Icons.Rounded.Schedule, contentDescription = null, tint = Cream, modifier = Modifier.size(18.dp))
                Spacer(Modifier.size(6.dp))
                Text("Tempo de espera: ${formatWait(elapsedMs)}", style = MaterialTheme.typography.bodyLarge, color = Cream)
            }
        }

        Spacer(Modifier.size(16.dp))

        if (otherLobbies.isNotEmpty()) {
            Box(
                Modifier.fillMaxWidth()
                    .stickerBlock(fillColor = Teal, cornerRadius = 16.dp, shadowOffset = 3.dp)
                    .clickable { showOtherLobbies = !showOtherLobbies }
                    .padding(vertical = 10.dp, horizontal = 14.dp),
                contentAlignment = Alignment.Center
            ) {
                Text(
                    if (showOtherLobbies) "ESCONDER OUTRAS SALAS ABERTAS" else "VER OUTRAS SALAS ABERTAS (${otherLobbies.size})",
                    style = MaterialTheme.typography.labelLarge,
                    color = textColorFor(Teal)
                )
            }
            Spacer(Modifier.size(12.dp))
        }

        if (showOtherLobbies && otherLobbies.isNotEmpty()) {
            Column(Modifier.fillMaxWidth(), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                otherLobbies.forEach { lobby ->
                    val catText = lobby.categoria.ifBlank { "Geral" }
                    val modeText = if (lobby.modo == "caotico") "Caótico" else "Clássico"
                    Row(
                        Modifier.fillMaxWidth()
                            .stickerBlock(fillColor = Lavender, cornerRadius = 14.dp, shadowOffset = 3.dp)
                            .padding(12.dp),
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.SpaceBetween
                    ) {
                        Column(Modifier.weight(1f)) {
                            Text("Sala de ${lobby.hostNome}", style = MaterialTheme.typography.bodyLarge.copy(fontWeight = FontWeight.Bold), color = Ink)
                            Text("$catText · $modeText (${lobby.membros.size}/${format.players})", style = MaterialTheme.typography.labelLarge, color = Ink)
                        }
                        Box(
                            Modifier.stickerBlock(fillColor = Purple, cornerRadius = 12.dp, shadowOffset = 2.dp)
                                .clickable { onSwitchLobby(lobby); showOtherLobbies = false }
                                .padding(horizontal = 12.dp, vertical = 6.dp)
                        ) {
                            Text("MUDAR", style = MaterialTheme.typography.labelLarge, color = Cream)
                        }
                    }
                }
            }
            Spacer(Modifier.size(12.dp))
        }

        // Per-format seats
        if (format == MatchFormat.TWO_V_TWO) TeamSeats(occupants) else FlatSeats(format, occupants)
        }

        Spacer(Modifier.size(12.dp))

        // O arranque manual só existe a partir do mínimo do formato. Era `>= 2`, o que além de
        // deixar um Grupo começar a dois **deixava um 2x2 começar a três** — e aí o anfitrião
        // divide os membros em duas equipas, uma das quais ficava com um jogador só. Com
        // `minPlayers` (4 no 2x2) essa janela fecha-se.
        val podeArrancar = state.joinedCount >= format.minPlayers
        if (state.isHost && podeArrancar && state.joinedCount < format.players) {
            // O relógio reinicia a cada entrada — e é essa reposição que **é** a janela de
            // graça: cada jogador novo compra mais 60 s à sala, por isso uma sala a encher
            // continua a esperar e só uma sala parada é que fecha sozinha. Sem arranque
            // automático, um Grupo com 4 dependia de o anfitrião estar atento; se ele se
            // distraísse, ninguém jogava — que é exactamente o beco da Fase 30.
            var autoStartSeconds by remember { mutableIntStateOf(60) }
            LaunchedEffect(state.joinedCount) {
                autoStartSeconds = 60
                while (autoStartSeconds > 0) {
                    delay(1000)
                    autoStartSeconds--
                }
                onForceStart()
            }
            StickerButton(
                "INICIAR JOGO (${state.joinedCount} JOGADORES) · Auto: ${autoStartSeconds}s",
                Icons.Rounded.CheckCircle,
                onForceStart,
                fillColor = Gold,
                modifier = Modifier.fillMaxWidth()
            )
            Spacer(Modifier.size(12.dp))
        }

        StickerButton("CANCELAR PROCURA", Icons.Rounded.Close, onCancel, fillColor = Coral, modifier = Modifier.fillMaxWidth())
        Spacer(Modifier.size(10.dp))
        Text(
            when {
                // Ainda abaixo do mínimo: dizer quantos faltam é mais útil do que prometer um
                // arranque automático que, neste estado, não vai acontecer.
                format.hasFlexibleSize && !podeArrancar ->
                    "Faltam ${format.minPlayers - state.joinedCount} para poder começar (mínimo ${format.minPlayers}, máximo ${format.players})."
                format.hasFlexibleSize && !state.isHost ->
                    "A partida começa quando o anfitrião iniciar ou a sala encher."
                format.hasFlexibleSize ->
                    "Já podes começar. A sala continua a aceitar jogadores até ${format.players}."
                else -> "A partida começa automaticamente quando a sala encher."
            },
            style = MaterialTheme.typography.labelLarge, color = Ink,
            modifier = Modifier.fillMaxWidth(), textAlign = TextAlign.Center
        )
    }
}

private fun formatWait(ms: Long): String {
    val total = ms / 1000
    return "%02d:%02d".format(total / 60, total % 60)
}

private fun initials(name: String): String =
    name.trim().split(Regex("\\s+")).filter { it.isNotEmpty() }.take(2)
        .joinToString("") { it.first().uppercase() }.ifBlank { "?" }

/** 1x1 and Grupo: a flat vertical list of seats (filled first, then empty placeholders). */
@Composable
private fun FlatSeats(format: MatchFormat, occupants: List<PlayerLive>) {
    if (format == MatchFormat.GRUPO) {
        Text(
            "JOGADORES (${occupants.size.coerceAtMost(format.players)}/${format.players}) · MÍNIMO ${format.minPlayers}",
            style = MaterialTheme.typography.labelLarge, color = Ink
        )
        Spacer(Modifier.size(10.dp))
    }
    Column(Modifier.fillMaxWidth(), verticalArrangement = Arrangement.spacedBy(12.dp)) {
        for (i in 0 until format.players) {
            val p = occupants.getOrNull(i)
            // O lugar entra em cascata na primeira composição (i escalonado) e, quando o
            // "À procura..." dá lugar a um nome real, o `AnimatedContent` faz o lugar
            // desvanecer/crescer para o novo conteúdo em vez de trocar a seco.
            Box(Modifier.cascadeIn(i)) {
                AnimatedContent(
                    targetState = p?.uid,
                    transitionSpec = { seatFillTransition() },
                    label = "seat$i"
                ) {
                    SeatRow(occupants.getOrNull(i), emptyLabel = if (format == MatchFormat.GRUPO) "Vaga livre" else "À procura...")
                }
            }
        }
    }
}

/** Transição partilhada por todos os lugares: entra com um pequeno "pop", sai a desvanecer. */
private fun seatFillTransition(): ContentTransform {
    val entrar = fadeIn(tween(Motion.ENTRY_MS)) +
        scaleIn(initialScale = 0.85f, animationSpec = tween(Motion.ENTRY_MS))
    val sair = fadeOut(tween(Motion.FEEDBACK_MS))
    return entrar.togetherWith(sair)
}

@Composable
private fun SeatRow(p: PlayerLive?, emptyLabel: String) {
    if (p == null) {
        Row(
            Modifier.fillMaxWidth().stickerDashed(cornerRadius = 18.dp).padding(horizontal = 16.dp, vertical = 15.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Box(Modifier.size(40.dp).clip(CircleShape).background(Cream), contentAlignment = Alignment.Center) {
                Icon(Icons.Rounded.MoreHoriz, contentDescription = null, tint = Ink, modifier = Modifier.size(22.dp))
            }
            Spacer(Modifier.size(12.dp))
            Text(emptyLabel, style = MaterialTheme.typography.bodyLarge, color = Ink)
        }
    } else {
        // "Tu" marca-se com contorno roxo sobre lavanda, o mesmo sinal usado no pódio e no
        // ranking. Antes o próprio jogador vinha em Teal, que na app significa "confirmar".
        Row(
            Modifier.fillMaxWidth()
                .stickerBlock(
                    fillColor = Lavender, cornerRadius = 18.dp, shadowOffset = 4.dp,
                    borderColor = if (p.isMe) Purple else Ink
                )
                .padding(horizontal = 16.dp, vertical = 12.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            val avatar = if (p.isMe) Purple else Teal
            Box(Modifier.size(40.dp).stickerCircle(fillColor = avatar, shadowOffset = 3.dp, borderWidth = 2.dp), contentAlignment = Alignment.Center) {
                Text(initials(p.nome), style = MaterialTheme.typography.labelLarge, color = textColorFor(avatar))
            }
            Spacer(Modifier.size(12.dp))
            Column(Modifier.weight(1f)) {
                Text(if (p.isMe) "${p.nome} (tu)" else p.nome, style = MaterialTheme.typography.bodyLarge.copy(fontWeight = FontWeight.Bold), color = Ink, maxLines = 1)
                // "Na sala", não "Pronto": não há ready-up nenhum nesta app — a partida
                // arranca sozinha quando a sala enche. Dizer "Pronto" sugeria um passo
                // que o jogador teria de dar e que não existe.
                Text("Na sala", style = MaterialTheme.typography.labelLarge, color = Ink)
            }
            Icon(Icons.Rounded.CheckCircle, contentDescription = null, tint = Teal, modifier = Modifier.size(20.dp))
        }
    }
}

/** 2x2: two team columns of two seats each. */
@Composable
private fun TeamSeats(occupants: List<PlayerLive>) {
    val a = ArrayList<PlayerLive>(); val b = ArrayList<PlayerLive>()
    occupants.forEach { p ->
        when (p.team) {
            "A" -> if (a.size < 2) a.add(p)
            "B" -> if (b.size < 2) b.add(p)
            else -> if (a.size <= b.size && a.size < 2) a.add(p) else if (b.size < 2) b.add(p)
        }
    }
    Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(12.dp)) {
        TeamColumn("EQUIPA A", a, Modifier.weight(1f))
        TeamColumn("EQUIPA B", b, Modifier.weight(1f))
    }
}

@Composable
private fun TeamColumn(label: String, players: List<PlayerLive>, modifier: Modifier = Modifier) {
    Column(
        modifier.stickerBlock(fillColor = Lavender, cornerRadius = 20.dp, shadowOffset = 5.dp).padding(14.dp),
        horizontalAlignment = Alignment.CenterHorizontally
    ) {
        Text(label, style = MaterialTheme.typography.labelLarge, color = Ink)
        Spacer(Modifier.size(10.dp))
        for (i in 0 until 2) {
            val p = players.getOrNull(i)
            AnimatedContent(
                targetState = p?.uid,
                transitionSpec = { seatFillTransition() },
                label = "teamSeat$i"
            ) {
                CompactSeat(players.getOrNull(i))
            }
            if (i == 0) Spacer(Modifier.size(8.dp))
        }
    }
}

@Composable
private fun CompactSeat(p: PlayerLive?) {
    if (p == null) {
        Row(
            Modifier.fillMaxWidth().stickerDashed(cornerRadius = 14.dp).padding(vertical = 12.dp, horizontal = 10.dp),
            verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.Center
        ) {
            Text("Vazio", style = MaterialTheme.typography.bodyLarge, color = Ink)
        }
    } else {
        Row(
            Modifier.fillMaxWidth()
                .stickerBlock(
                    fillColor = Cream, cornerRadius = 14.dp, shadowOffset = 3.dp, borderWidth = 2.dp,
                    borderColor = if (p.isMe) Purple else Ink
                )
                .padding(vertical = 10.dp, horizontal = 10.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Text(if (p.isMe) "${p.nome} (tu)" else p.nome, style = MaterialTheme.typography.bodyLarge, color = Ink, maxLines = 1, modifier = Modifier.weight(1f))
            Spacer(Modifier.size(6.dp))
            // Um único significado: verde = este lugar está ocupado.
            Icon(Icons.Rounded.CheckCircle, contentDescription = null, tint = Teal, modifier = Modifier.size(18.dp))
        }
    }
}

@Composable
private fun ErrorView(error: String?, onHome: () -> Unit) {
    Box(Modifier.fillMaxSize().background(Cream), contentAlignment = Alignment.Center) {
        Column(horizontalAlignment = Alignment.CenterHorizontally, modifier = Modifier.padding(32.dp)) {
            Text(error ?: "Erro", style = MaterialTheme.typography.bodyLarge, color = Coral, textAlign = TextAlign.Center)
            Spacer(Modifier.size(24.dp))
            StickerButton("VOLTAR", Icons.Rounded.Home, onHome)
        }
    }
}

@Composable
private fun QuestionView(state: MultiUiState, onSelect: (String) -> Unit) {
    val question = state.currentQuestion ?: return
    Column(Modifier.fillMaxSize().background(Cream).padding(20.dp)) {
        Scoreboard(state)
        Spacer(Modifier.size(12.dp))
        Text("Pergunta ${state.currentIndex + 1} de ${state.perguntas.size}", style = MaterialTheme.typography.labelLarge, color = Ink)
        Spacer(Modifier.size(10.dp))
        TimerBar(state.remainingMillis, state.durationMillis)
        state.currentEvent?.let { ev ->
            Spacer(Modifier.size(12.dp))
            com.ratoooooo.perguntaoluso.game.EventBannerRow(ev.displayName, ev.description)
        }
        Spacer(Modifier.size(16.dp))
        Box(Modifier.fillMaxWidth().stickerBlock(fillColor = Lavender, cornerRadius = 28.dp).padding(22.dp)) {
            Text(question.pergunta, style = MaterialTheme.typography.titleLarge, color = Ink)
        }
        Spacer(Modifier.size(16.dp))
        val userWasWrong = state.isAnswered && state.selectedOption != null && state.selectedOption != question.respostaCorreta
        val vf = question.isVerdadeiroFalso
        val context = androidx.compose.ui.platform.LocalContext.current

        // Mesma regra do solo: o som segue o estado, para o tempo esgotado também soar.
        // No multijogador o timeout é ainda mais comum — o relógio é partilhado e acaba
        // à mesma para quem não respondeu.
        androidx.compose.runtime.LaunchedEffect(state.isAnswered, state.currentIndex) {
            if (!state.isAnswered) return@LaunchedEffect
            val acertou = state.selectedOption == question.respostaCorreta
            com.ratoooooo.perguntaoluso.audio.SoundEffects.tocar(
                context,
                if (acertou) com.ratoooooo.perguntaoluso.audio.SoundEffects.Efeito.CERTO
                else com.ratoooooo.perguntaoluso.audio.SoundEffects.Efeito.ERRADO
            )
        }

        // Mesmo componente do solo: cartão neutro + emblema A/B/C/D, cor só na revelação.
        Column(Modifier.fillMaxWidth(), verticalArrangement = Arrangement.spacedBy(if (vf) 16.dp else 12.dp)) {
            question.opcoes.forEachIndexed { index, opcao ->
                com.ratoooooo.perguntaoluso.game.AnswerOption(
                    text = opcao,
                    index = index,
                    isVerdadeiroFalso = vf,
                    isAnswered = state.isAnswered,
                    aceitaToques = state.aceitaToques,
                    isCorrectOption = opcao == question.respostaCorreta,
                    isSelected = opcao == state.selectedOption,
                    userWasWrong = userWasWrong,
                    height = if (vf) 88.dp else 66.dp,
                    animationKey = question.pergunta,
                    onClick = { onSelect(opcao) }
                )
            }
        }
        Spacer(Modifier.weight(1f))
        if (state.isAnswered) {
            Spacer(Modifier.size(8.dp))
            Text("À espera dos outros jogadores...", style = MaterialTheme.typography.labelLarge, color = Ink,
                modifier = Modifier.fillMaxWidth(), textAlign = TextAlign.Center)
        }
    }
}

@Composable
private fun Scoreboard(state: MultiUiState) {
    if (state.format.teamBased) {
        val totalA = state.players.filter { it.team == "A" }.sumOf { it.score }
        val totalB = state.players.filter { it.team == "B" }.sumOf { it.score }
        val myTeam = state.players.firstOrNull { it.isMe }?.team
        Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(12.dp)) {
            TeamPill("Equipa A", totalA, myTeam == "A", Teal, Modifier.weight(1f))
            TeamPill("Equipa B", totalB, myTeam == "B", Purple, Modifier.weight(1f))
        }
    } else {
        Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            state.players.forEach { p ->
                // "Tu" com contorno roxo, o mesmo sinal do pódio, da sala e do ranking.
                // Em Teal ficava verde ao lado de opções que também usam verde para "certo".
                Column(
                    Modifier.weight(1f)
                        .stickerBlock(
                            fillColor = Lavender, cornerRadius = 14.dp, shadowOffset = 3.dp,
                            borderWidth = 2.dp, borderColor = if (p.isMe) Purple else Ink
                        )
                        .padding(vertical = 8.dp, horizontal = 4.dp),
                    horizontalAlignment = Alignment.CenterHorizontally
                ) {
                    Text(if (p.isMe) "Tu" else p.nome.take(6), style = MaterialTheme.typography.bodyLarge, color = Ink, maxLines = 1)
                    Text("${p.score}", style = MaterialTheme.typography.labelLarge, color = Ink)
                }
            }
        }
    }
}

@Composable
private fun TeamPill(name: String, total: Int, mine: Boolean, color: Color, modifier: Modifier = Modifier) {
    Column(
        modifier.stickerBlock(fillColor = color, cornerRadius = 18.dp, shadowOffset = 4.dp).padding(vertical = 10.dp),
        horizontalAlignment = Alignment.CenterHorizontally
    ) {
        Text(if (mine) "$name (tu)" else name, style = MaterialTheme.typography.labelLarge, color = textColorFor(color), maxLines = 1)
        Text("$total", style = MaterialTheme.typography.titleLarge, color = textColorFor(color))
    }
}

/**
 * Mesma urgência do temporizador solo (`QuestionScreen.TimerBar`): abaixo de 25% a barra
 * pulsa, cada vez mais depressa perto do fim. Faltava aqui — o multijogador tinha a versão
 * lisa de antes da Fase 18.
 */
@Composable
private fun TimerBar(remainingMillis: Long, durationMillis: Long) {
    val fraction = if (durationMillis > 0) (remainingMillis.toFloat() / durationMillis).coerceIn(0f, 1f) else 0f
    val fill = when {
        fraction > 0.5f -> Teal
        fraction > 0.25f -> Gold
        else -> Coral
    }
    val urgente = fraction <= 0.25f && fraction > 0f
    val periodo = (260 + (fraction / 0.25f) * 740).toInt().coerceAtLeast(220)
    val pulso = rememberPulse(ativo = urgente, min = 1f, max = 1.22f, periodoMs = periodo)
    Box(
        Modifier.fillMaxWidth().height(18.dp)
            .scale(scaleX = 1f, scaleY = if (urgente) pulso else 1f)
            .clip(RoundedCornerShape(9.dp)).background(Lavender).border(3.dp, Ink, RoundedCornerShape(9.dp))
    ) {
        Box(Modifier.fillMaxHeight().fillMaxWidth(fraction).clip(RoundedCornerShape(9.dp)).background(fill))
    }
}

@Composable
private fun Podium(state: MultiUiState, onPlayAgain: () -> Unit, onHome: () -> Unit) {
    val context = androidx.compose.ui.platform.LocalContext.current
    // Perder também merece som: antes só a vitória tinha, e um pódio de derrota ficava mudo,
    // como se a partida não tivesse acabado. O de derrota é mais grave e mais fraco (ver
    // SoundEffects), assinala sem repreender.
    LaunchedEffect(Unit) {
        com.ratoooooo.perguntaoluso.audio.SoundEffects.tocar(
            context,
            if (state.iWon) com.ratoooooo.perguntaoluso.audio.SoundEffects.Efeito.VITORIA
            else com.ratoooooo.perguntaoluso.audio.SoundEffects.Efeito.DERROTA
        )
    }
    Column(Modifier.fillMaxSize().background(Cream).padding(24.dp), horizontalAlignment = Alignment.CenterHorizontally) {
        ScreenHeader(title = state.resultTitle, onBack = onHome)
        Spacer(Modifier.size(20.dp))

        // Revelação em cascata: equipas/classificação primeiro, estatísticas a seguir —
        // mesma ideia do pódio solo.
        if (state.format.teamBased) {
            Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(14.dp)) {
                state.teams.forEachIndexed { i, t -> TeamCard(t, Modifier.weight(1f).cascadeIn(i)) }
            }
            if (state.walkover) {
                Spacer(Modifier.size(16.dp))
                Text("Uma equipa ficou incompleta porque um jogador saiu.", style = MaterialTheme.typography.bodyLarge, color = Ink, textAlign = TextAlign.Center)
            }
        } else {
            // Sem weight: com dois jogadores a lista esticava-se e abria um buraco entre a
            // classificação e o resto do ecrã.
            Column(Modifier.fillMaxWidth(), verticalArrangement = Arrangement.spacedBy(12.dp)) {
                state.ranking.forEachIndexed { i, r -> RankRow(i + 1, r, index = i) }
            }
        }

        Spacer(Modifier.size(18.dp))
        com.ratoooooo.perguntaoluso.game.ResultStats(
            modoId = state.modo,
            perguntas = state.perguntas.size,
            respostasCertas = state.myCorrect,
            venceu = state.iWon,
            modifier = Modifier.cascadeIn(state.teams.size.coerceAtLeast(state.ranking.size) + 2)
        )

        Spacer(Modifier.weight(1f))
        Spacer(Modifier.size(20.dp))
        // Neste ecrã o dourado já está tomado pelo vencedor, por isso a ação primária é roxa
        // (como no ecrã 10 do mockup). Ver a regra "dourado por ecrã" em GAME_DESIGN.md.
        StickerButton("NOVO JOGO", Icons.Rounded.Refresh, onPlayAgain, fillColor = Purple, modifier = Modifier.fillMaxWidth())
        Spacer(Modifier.size(12.dp))
        StickerButton("VOLTAR AO INÍCIO", Icons.Rounded.Home, onHome, fillColor = Lavender, modifier = Modifier.fillMaxWidth())
    }
}

@Composable
private fun TeamCard(t: TeamResult, modifier: Modifier = Modifier) {
    val color = if (t.isWinner) Gold else Lavender
    Column(
        modifier.stickerBlock(fillColor = color, cornerRadius = 24.dp, shadowOffset = 6.dp).padding(vertical = 18.dp, horizontal = 12.dp),
        horizontalAlignment = Alignment.CenterHorizontally
    ) {
        if (t.isWinner) {
            // Destaque mais forte para a equipa vencedora: o troféu pulsa.
            val pulso = rememberPulse(ativo = true, min = 1f, max = 1.14f, periodoMs = 900)
            Icon(Icons.Rounded.EmojiEvents, contentDescription = null, tint = Ink, modifier = Modifier.size(28.dp).scale(pulso))
            Spacer(Modifier.size(4.dp))
        }
        Text(if (t.isMine) "${t.name} (tu)" else t.name, style = MaterialTheme.typography.labelLarge, color = textColorFor(color), maxLines = 1)
        Text("${t.total}", style = MaterialTheme.typography.headlineLarge, color = textColorFor(color))
        Spacer(Modifier.size(6.dp))
        t.players.forEach { (nome, sc) ->
            Text("$nome · $sc", style = MaterialTheme.typography.bodyLarge, color = textColorFor(color), maxLines = 1)
        }
    }
}

@Composable
private fun RankRow(rank: Int, r: RankResult, index: Int) {
    // Dourado = 1.º lugar (mérito). O próprio jogador distingue-se pelo contorno roxo, não
    // por uma cor de fundo — assim "sou eu" e "ganhei" deixam de ser o mesmo tipo de sinal.
    val color = when {
        r.left -> Neutral
        rank == 1 -> Gold
        else -> Lavender
    }
    // #1 pulsa — o mesmo destaque usado no troféu das equipas e no pódio solo.
    val pulso = rememberPulse(ativo = rank == 1 && !r.left, min = 1f, max = 1.03f, periodoMs = 1000)
    Row(
        Modifier.fillMaxWidth()
            .cascadeIn(index, key = "multiPodiumRank")
            .scale(pulso)
            .stickerBlock(
                fillColor = color, cornerRadius = 18.dp, shadowOffset = 4.dp,
                borderColor = if (r.isMe) Purple else Ink
            )
            .padding(horizontal = 18.dp, vertical = 14.dp),
        horizontalArrangement = Arrangement.SpaceBetween, verticalAlignment = Alignment.CenterVertically
    ) {
        Text(
            "#$rank  ${if (r.isMe) "${r.nome} (tu)" else r.nome}${if (r.left) " — saiu" else ""}",
            style = MaterialTheme.typography.bodyLarge.copy(fontWeight = FontWeight.Bold), color = textColorFor(color), maxLines = 1
        )
        Text("${r.score}", style = MaterialTheme.typography.labelLarge, color = textColorFor(color))
    }
}
