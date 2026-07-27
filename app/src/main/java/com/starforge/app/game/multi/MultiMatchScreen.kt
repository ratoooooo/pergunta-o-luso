package com.starforge.app.game.multi

import androidx.compose.foundation.background
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
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import com.starforge.app.game.ScreenHeader
import com.starforge.app.ui.theme.AnswerPalette
import com.starforge.app.ui.theme.Coral
import com.starforge.app.ui.theme.Cream
import com.starforge.app.ui.theme.Gold
import com.starforge.app.ui.theme.Ink
import com.starforge.app.ui.theme.Lavender
import com.starforge.app.ui.theme.Neutral
import com.starforge.app.ui.theme.Purple
import com.starforge.app.ui.theme.StickerButton
import com.starforge.app.ui.theme.Teal
import com.starforge.app.ui.theme.stickerBlock
import com.starforge.app.ui.theme.stickerCircle
import com.starforge.app.ui.theme.stickerDashed
import com.starforge.app.ui.theme.textColorFor
import kotlinx.coroutines.delay

@Composable
fun MultiMatchScreen(
    state: MultiUiState,
    onSelectAnswer: (String) -> Unit,
    onLeave: () -> Unit,
    onPlayAgain: () -> Unit,
    onHome: () -> Unit
) {
    when (state.phase) {
        MultiPhase.SEARCHING -> WaitingRoom(state, onLeave)
        MultiPhase.MATCHED -> Matched(state)
        MultiPhase.ERROR -> ErrorView(state.error, onHome)
        MultiPhase.PODIUM -> Podium(state, onPlayAgain, onHome)
        MultiPhase.IN_GAME -> QuestionView(state, onSelectAnswer)
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
                MatchedTeam("Equipa A", a, Teal, Modifier.weight(1f))
                MatchedTeam("Equipa B", b, Purple, Modifier.weight(1f))
            }
        } else {
            Column(Modifier.fillMaxWidth(), verticalArrangement = Arrangement.spacedBy(12.dp)) {
                state.players.forEach { p ->
                    Box(
                        Modifier.fillMaxWidth().stickerBlock(fillColor = if (p.isMe) Teal else Lavender, cornerRadius = 18.dp, shadowOffset = 4.dp)
                            .padding(vertical = 16.dp),
                        contentAlignment = Alignment.Center
                    ) {
                        Text(if (p.isMe) "${p.nome} (tu)" else p.nome, style = MaterialTheme.typography.titleLarge, color = textColorFor(if (p.isMe) Teal else Lavender))
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
 * Waiting/matchmaking room (mockup screen 5). Random matchmaking, so — unlike the mockup's
 * invite-based "Sala de Espera" screens 6/7/8 — there is no room code, invite or manual ready-up:
 * the match auto-starts (→ MATCHED) once the room fills. Seats fill with real names as players
 * join the room; before a room exists only "Tu" is known.
 */
@Composable
private fun WaitingRoom(state: MultiUiState, onCancel: () -> Unit) {
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
    // Elapsed wait clock, client-side — there is no server-tracked queue time to read.
    var elapsedMs by remember { mutableStateOf(0L) }
    LaunchedEffect(Unit) {
        val start = System.currentTimeMillis()
        while (true) { elapsedMs = System.currentTimeMillis() - start; delay(500) }
    }

    val occupants: List<PlayerLive> = if (state.players.isNotEmpty()) state.players
        else listOf(PlayerLive("me", state.myName.ifBlank { "Tu" }, 0, null, isMe = true, left = false))

    Column(Modifier.fillMaxSize().background(Cream).padding(horizontal = 24.dp).padding(top = 24.dp, bottom = 20.dp)) {
        ScreenHeader(title = title)
        Spacer(Modifier.size(20.dp))

        // Search status card
        Column(
            Modifier.fillMaxWidth().stickerBlock(fillColor = Purple, cornerRadius = 26.dp, shadowOffset = 6.dp).padding(vertical = 22.dp),
            horizontalAlignment = Alignment.CenterHorizontally
        ) {
            Box(Modifier.size(56.dp).stickerCircle(fillColor = Cream, shadowOffset = 3.dp, borderWidth = 2.dp), contentAlignment = Alignment.Center) {
                Icon(icon, contentDescription = null, tint = Ink, modifier = Modifier.size(30.dp))
            }
            Spacer(Modifier.size(12.dp))
            Text("À procura de jogadores...", style = MaterialTheme.typography.titleLarge, color = Cream, textAlign = TextAlign.Center)
            Spacer(Modifier.size(4.dp))
            Text("${state.joinedCount} / ${format.players} encontrados", style = MaterialTheme.typography.headlineLarge, color = Cream)
            Spacer(Modifier.size(12.dp))
            Row(verticalAlignment = Alignment.CenterVertically) {
                Icon(Icons.Rounded.Schedule, contentDescription = null, tint = Cream, modifier = Modifier.size(18.dp))
                Spacer(Modifier.size(6.dp))
                Text("Tempo de espera: ${formatWait(elapsedMs)}", style = MaterialTheme.typography.bodyLarge, color = Cream)
            }
        }

        Spacer(Modifier.size(20.dp))

        // Per-format seats
        if (format == MatchFormat.TWO_V_TWO) TeamSeats(occupants) else FlatSeats(format, occupants)

        Spacer(Modifier.weight(1f))
        StickerButton("CANCELAR", Icons.Rounded.Close, onCancel, fillColor = Coral, modifier = Modifier.fillMaxWidth())
        Spacer(Modifier.size(10.dp))
        Text(
            "Navegação bloqueada durante a procura.",
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
            "JOGADORES (${occupants.size.coerceAtMost(format.players)}/${format.players})",
            style = MaterialTheme.typography.labelLarge, color = Ink
        )
        Spacer(Modifier.size(10.dp))
    }
    Column(Modifier.fillMaxWidth(), verticalArrangement = Arrangement.spacedBy(12.dp)) {
        for (i in 0 until format.players) {
            SeatRow(occupants.getOrNull(i), emptyLabel = if (format == MatchFormat.GRUPO) "Vaga livre" else "À procura...")
        }
    }
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
        val color = if (p.isMe) Teal else Lavender
        val avatar = if (p.isMe) Purple else Gold
        Row(
            Modifier.fillMaxWidth().stickerBlock(fillColor = color, cornerRadius = 18.dp, shadowOffset = 4.dp).padding(horizontal = 16.dp, vertical = 12.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Box(Modifier.size(40.dp).stickerCircle(fillColor = avatar, shadowOffset = 3.dp, borderWidth = 2.dp), contentAlignment = Alignment.Center) {
                Text(initials(p.nome), style = MaterialTheme.typography.labelLarge, color = textColorFor(avatar))
            }
            Spacer(Modifier.size(12.dp))
            Column(Modifier.weight(1f)) {
                Text(if (p.isMe) "${p.nome} (tu)" else p.nome, style = MaterialTheme.typography.bodyLarge.copy(fontWeight = FontWeight.Bold), color = textColorFor(color), maxLines = 1)
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Box(Modifier.size(9.dp).clip(CircleShape).background(if (color == Teal) Gold else Teal))
                    Spacer(Modifier.size(6.dp))
                    Text("Pronto", style = MaterialTheme.typography.labelLarge, color = textColorFor(color))
                }
            }
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
            CompactSeat(players.getOrNull(i))
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
        val color = if (p.isMe) Teal else Cream
        Row(
            Modifier.fillMaxWidth().stickerBlock(fillColor = color, cornerRadius = 14.dp, shadowOffset = 3.dp, borderWidth = 2.dp).padding(vertical = 10.dp, horizontal = 10.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Text(if (p.isMe) "${p.nome} (tu)" else p.nome, style = MaterialTheme.typography.bodyLarge, color = textColorFor(color), maxLines = 1, modifier = Modifier.weight(1f))
            Spacer(Modifier.size(6.dp))
            Icon(Icons.Rounded.CheckCircle, contentDescription = null, tint = if (color == Teal) Gold else Teal, modifier = Modifier.size(18.dp))
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
            Row(
                Modifier.fillMaxWidth().stickerBlock(fillColor = Gold, cornerRadius = 16.dp, shadowOffset = 4.dp).padding(horizontal = 16.dp, vertical = 10.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                Icon(Icons.Rounded.Bolt, contentDescription = null, tint = Ink, modifier = Modifier.size(24.dp))
                Spacer(Modifier.size(10.dp))
                Column {
                    Text(ev.displayName, style = MaterialTheme.typography.labelLarge, color = Ink)
                    Text(ev.description, style = MaterialTheme.typography.bodyLarge, color = Ink)
                }
            }
        }
        Spacer(Modifier.size(16.dp))
        Box(Modifier.fillMaxWidth().stickerBlock(fillColor = Lavender, cornerRadius = 28.dp).padding(22.dp)) {
            Text(question.pergunta, style = MaterialTheme.typography.titleLarge, color = Ink)
        }
        Spacer(Modifier.size(16.dp))
        val userWasWrong = state.isAnswered && state.selectedOption != null && state.selectedOption != question.respostaCorreta
        val vf = question.isVerdadeiroFalso
        Column(Modifier.fillMaxWidth(), verticalArrangement = Arrangement.spacedBy(if (vf) 16.dp else 12.dp)) {
            question.opcoes.forEachIndexed { index, opcao ->
                val base = if (vf) (if (index == 0) Teal else Coral) else AnswerPalette[index % AnswerPalette.size]
                val isCorrect = opcao == question.respostaCorreta
                val isSelected = opcao == state.selectedOption
                val color = when {
                    !state.isAnswered -> base
                    isCorrect && isSelected -> Teal
                    isCorrect && userWasWrong -> Gold
                    isSelected -> Coral
                    else -> Neutral
                }
                Row(
                    Modifier.fillMaxWidth().height(if (vf) 88.dp else 66.dp)
                        .stickerBlock(fillColor = color, cornerRadius = 20.dp, shadowOffset = 5.dp)
                        .clickable(enabled = !state.isAnswered) { onSelect(opcao) }
                        .padding(horizontal = 18.dp),
                    horizontalArrangement = Arrangement.Center,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    if (vf) {
                        Icon(
                            if (index == 0) Icons.Rounded.Check else Icons.Rounded.Close,
                            contentDescription = null, tint = textColorFor(color), modifier = Modifier.size(28.dp)
                        )
                        Spacer(Modifier.size(12.dp))
                    }
                    Text(
                        opcao,
                        style = if (vf) MaterialTheme.typography.titleLarge
                        else MaterialTheme.typography.bodyLarge.copy(fontWeight = FontWeight.Bold),
                        color = textColorFor(color), textAlign = TextAlign.Center
                    )
                }
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
                Column(
                    Modifier.weight(1f).stickerBlock(fillColor = if (p.isMe) Teal else Lavender, cornerRadius = 14.dp, shadowOffset = 3.dp, borderWidth = 2.dp)
                        .padding(vertical = 8.dp, horizontal = 4.dp),
                    horizontalAlignment = Alignment.CenterHorizontally
                ) {
                    Text(if (p.isMe) "Tu" else p.nome.take(6), style = MaterialTheme.typography.bodyLarge, color = textColorFor(if (p.isMe) Teal else Lavender), maxLines = 1)
                    Text("${p.score}", style = MaterialTheme.typography.labelLarge, color = textColorFor(if (p.isMe) Teal else Lavender))
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

@Composable
private fun TimerBar(remainingMillis: Long, durationMillis: Long) {
    val fraction = if (durationMillis > 0) (remainingMillis.toFloat() / durationMillis).coerceIn(0f, 1f) else 0f
    val fill = when {
        fraction > 0.5f -> Teal
        fraction > 0.25f -> Gold
        else -> Coral
    }
    Box(Modifier.fillMaxWidth().height(18.dp).clip(RoundedCornerShape(9.dp)).background(Lavender).border(3.dp, Ink, RoundedCornerShape(9.dp))) {
        Box(Modifier.fillMaxHeight().fillMaxWidth(fraction).clip(RoundedCornerShape(9.dp)).background(fill))
    }
}

@Composable
private fun Podium(state: MultiUiState, onPlayAgain: () -> Unit, onHome: () -> Unit) {
    Column(Modifier.fillMaxSize().background(Cream).padding(24.dp), horizontalAlignment = Alignment.CenterHorizontally) {
        ScreenHeader(title = state.resultTitle, onBack = onHome)
        Spacer(Modifier.size(20.dp))

        if (state.format.teamBased) {
            Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(14.dp)) {
                state.teams.forEach { t -> TeamCard(t, Modifier.weight(1f)) }
            }
            if (state.walkover) {
                Spacer(Modifier.size(16.dp))
                Text("Uma equipa ficou incompleta porque um jogador saiu.", style = MaterialTheme.typography.bodyLarge, color = Ink, textAlign = TextAlign.Center)
            }
        } else {
            Column(Modifier.fillMaxWidth().weight(1f), verticalArrangement = Arrangement.spacedBy(12.dp)) {
                state.ranking.forEachIndexed { i, r -> RankRow(i + 1, r) }
            }
        }

        Spacer(Modifier.size(24.dp))
        StickerButton("NOVO JOGO", Icons.Rounded.Refresh, onPlayAgain, modifier = Modifier.fillMaxWidth())
        Spacer(Modifier.size(14.dp))
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
            Icon(Icons.Rounded.EmojiEvents, contentDescription = null, tint = Ink, modifier = Modifier.size(28.dp))
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
private fun RankRow(rank: Int, r: RankResult) {
    val color = when {
        r.left -> Neutral
        rank == 1 -> Gold
        r.isMe -> Teal
        else -> Lavender
    }
    Row(
        Modifier.fillMaxWidth().stickerBlock(fillColor = color, cornerRadius = 18.dp, shadowOffset = 4.dp).padding(horizontal = 18.dp, vertical = 14.dp),
        horizontalArrangement = Arrangement.SpaceBetween, verticalAlignment = Alignment.CenterVertically
    ) {
        Text(
            "#$rank  ${if (r.isMe) "${r.nome} (tu)" else r.nome}${if (r.left) " — saiu" else ""}",
            style = MaterialTheme.typography.bodyLarge.copy(fontWeight = FontWeight.Bold), color = textColorFor(color), maxLines = 1
        )
        Text("${r.score}", style = MaterialTheme.typography.labelLarge, color = textColorFor(color))
    }
}
