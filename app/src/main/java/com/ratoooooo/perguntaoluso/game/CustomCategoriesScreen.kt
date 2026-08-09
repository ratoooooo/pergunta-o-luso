package com.ratoooooo.perguntaoluso.game

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.Add
import androidx.compose.material.icons.rounded.Check
import androidx.compose.material.icons.rounded.Close
import androidx.compose.material.icons.rounded.Delete
import androidx.compose.material.icons.rounded.Description
import androidx.compose.material.icons.rounded.Edit
import androidx.compose.material.icons.rounded.Flag
import androidx.compose.material.icons.rounded.Groups
import androidx.compose.material.icons.rounded.Key
import androidx.compose.material.icons.rounded.Person
import androidx.compose.material.icons.rounded.PlayArrow
import androidx.compose.material.icons.rounded.Star
import androidx.compose.material.icons.rounded.StarBorder
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Switch
import androidx.compose.material3.SwitchDefaults
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateListOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.ratoooooo.perguntaoluso.data.AuthRepository
import com.ratoooooo.perguntaoluso.data.CustomCategory
import com.ratoooooo.perguntaoluso.data.ConteudoImproprioException
import com.ratoooooo.perguntaoluso.data.CustomCategoryRepository
import com.ratoooooo.perguntaoluso.data.ProfileRepository
import com.ratoooooo.perguntaoluso.data.Question
import com.ratoooooo.perguntaoluso.game.multi.MatchFormat
import com.ratoooooo.perguntaoluso.ui.theme.Coral
import com.ratoooooo.perguntaoluso.ui.theme.Cream
import com.ratoooooo.perguntaoluso.ui.theme.Gold
import com.ratoooooo.perguntaoluso.ui.theme.Ink
import com.ratoooooo.perguntaoluso.ui.theme.Lavender
import com.ratoooooo.perguntaoluso.ui.theme.NavTab
import com.ratoooooo.perguntaoluso.ui.theme.Neutral
import com.ratoooooo.perguntaoluso.ui.theme.Purple
import com.ratoooooo.perguntaoluso.ui.theme.SegmentedTabs
import com.ratoooooo.perguntaoluso.ui.theme.StickerButton
import com.ratoooooo.perguntaoluso.ui.theme.StickerDialog
import com.ratoooooo.perguntaoluso.ui.theme.StickerTextField
import com.ratoooooo.perguntaoluso.ui.theme.Teal
import com.ratoooooo.perguntaoluso.ui.theme.cascadeIn
import com.ratoooooo.perguntaoluso.ui.theme.pressScale
import com.ratoooooo.perguntaoluso.ui.theme.rememberPressScale
import com.ratoooooo.perguntaoluso.ui.theme.stickerBlock
import com.ratoooooo.perguntaoluso.ui.theme.stickerCircle
import com.ratoooooo.perguntaoluso.ui.theme.textColorFor
import kotlinx.coroutines.launch

@Composable
fun CustomCategoriesScreen(
    onPlayCustomCategorySolo: (CustomCategory) -> Unit,
    onCreatePrivateRoom: (CustomCategory, MatchFormat) -> Unit,
    onJoinPrivateRoomByCode: (String) -> Unit,
    onBack: () -> Unit,
    onHome: () -> Unit,
    onRanking: () -> Unit,
    onFriends: () -> Unit,
    onProfile: () -> Unit
) {
    val repo = remember { CustomCategoryRepository() }
    val authRepo = remember { AuthRepository() }
    val profileRepo = remember { ProfileRepository() }
    val scope = rememberCoroutineScope()

    var myUid by remember { mutableStateOf("") }
    var myName by remember { mutableStateOf("Jogador") }
    var tabIndex by rememberSaveable { mutableIntStateOf(0) }

    var publicCategories by remember { mutableStateOf<List<CustomCategory>>(emptyList()) }
    var myCategories by remember { mutableStateOf<List<CustomCategory>>(emptyList()) }

    var showCreateDialog by remember { mutableStateOf(false) }
    var categoryToEdit by remember { mutableStateOf<CustomCategory?>(null) }
    var categoryToPlay by remember { mutableStateOf<CustomCategory?>(null) }
    var categoryToReport by remember { mutableStateOf<CustomCategory?>(null) }
    var erroCriacao by remember { mutableStateOf<String?>(null) }
    var showJoinCodeDialog by remember { mutableStateOf(false) }

    LaunchedEffect(Unit) {
        val u = authRepo.ensureSignedIn()
        myUid = u.uid
        myName = runCatching { profileRepo.loadProfile(myUid).nomeVisivel }.getOrDefault("Convidado")

        launch {
            repo.observePublicCategories().collect { publicCategories = it }
        }
        launch {
            repo.observeMyCategories(myUid).collect { myCategories = it }
        }
    }

    MainScaffold(
        active = NavTab.NONE,
        onHome = onHome,
        onRanking = onRanking,
        onFriends = onFriends,
        onProfile = onProfile
    ) {
        ScreenHeader(
            title = "Quizzes da Comunidade",
            subtitle = "Cria, joga & descobre quizzes personalizados",
            onBack = onBack
        )
        Spacer(Modifier.size(12.dp))

        // Clean compact action bar (No wrapped text bugs!)
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.spacedBy(10.dp)
        ) {
            CompactActionButton(
                label = "ENTRAR COM CÓDIGO",
                icon = Icons.Rounded.Key,
                color = Gold,
                modifier = Modifier.weight(1f),
                onClick = { showJoinCodeDialog = true }
            )
            CompactActionButton(
                label = "+ CRIAR CATEGORIA",
                icon = Icons.Rounded.Add,
                color = Teal,
                modifier = Modifier.weight(1f),
                onClick = { categoryToEdit = null; showCreateDialog = true }
            )
        }

        Spacer(Modifier.size(14.dp))

        SegmentedTabs(
            labels = listOf("Explorar Públicas (${publicCategories.size})", "As Minhas (${myCategories.size})"),
            selectedIndex = tabIndex,
            onSelect = { tabIndex = it }
        )
        Spacer(Modifier.size(14.dp))

        LazyColumn(
            modifier = Modifier.weight(1f).fillMaxWidth(),
            verticalArrangement = Arrangement.spacedBy(12.dp)
        ) {
            val list = if (tabIndex == 0) publicCategories else myCategories
            if (list.isEmpty()) {
                item {
                    Text(
                        text = if (tabIndex == 0) "Ainda não existem categorias públicas criadas."
                        else "Ainda não criaste nenhuma categoria. Clica em '+ CRIAR CATEGORIA'!",
                        style = MaterialTheme.typography.bodyLarge,
                        color = Ink.copy(alpha = 0.6f),
                        modifier = Modifier.padding(16.dp),
                        textAlign = TextAlign.Center
                    )
                }
            } else {
                itemsIndexed(list, key = { _, cat -> cat.id }) { index, cat ->
                    CleanCategoryCard(
                        cat = cat,
                        isMine = cat.criadorUid == myUid,
                        index = index,
                        onPlay = { categoryToPlay = cat },
                        onEdit = { categoryToEdit = cat; showCreateDialog = true },
                        onRate = { rating ->
                            scope.launch { repo.rateCategory(cat.id, myUid, rating) }
                        },
                        onTogglePublic = {
                            scope.launch { repo.togglePublicStatus(cat.id, cat.publica) }
                        },
                        onReport = { categoryToReport = cat }
                    )
                }
            }
        }
    }

    if (showCreateDialog) {
        CreateCategoryDialog(
            existing = categoryToEdit,
            onDismiss = { showCreateDialog = false; categoryToEdit = null },
            erro = erroCriacao,
            onSave = { titulo, desc, publica, perguntas ->
                scope.launch {
                    erroCriacao = null
                    try {
                        repo.saveCategory(categoryToEdit?.id, titulo, desc, myUid, myName, publica, perguntas)
                        showCreateDialog = false
                        categoryToEdit = null
                    } catch (e: ConteudoImproprioException) {
                        erroCriacao = "Não podes usar a palavra \"${e.palavra}\". Corrige o texto e tenta de novo."
                    } catch (e: Exception) {
                        erroCriacao = "Não foi possível guardar o quiz."
                    }
                }
            }
        )
    }

    categoryToReport?.let { cat ->
        ReportDialog(
            cat = cat,
            repo = repo,
            myUid = myUid,
            onDismiss = { categoryToReport = null }
        )
    }

    categoryToPlay?.let { cat ->
        PlayCategoryOptionsDialog(
            cat = cat,
            onDismiss = { categoryToPlay = null },
            onPlaySolo = {
                categoryToPlay = null
                onPlayCustomCategorySolo(cat)
            },
            onCreatePrivateRoom = { format ->
                categoryToPlay = null
                onCreatePrivateRoom(cat, format)
            }
        )
    }

    if (showJoinCodeDialog) {
        JoinCodeDialog(
            onDismiss = { showJoinCodeDialog = false },
            onJoin = { code ->
                showJoinCodeDialog = false
                onJoinPrivateRoomByCode(code)
            }
        )
    }
}

@Composable
private fun CompactActionButton(
    label: String,
    icon: androidx.compose.ui.graphics.vector.ImageVector,
    color: Color,
    modifier: Modifier = Modifier,
    onClick: () -> Unit
) {
    // Encolhe e volta com mola ao toque — o mesmo feedback que todos os cartões e botões
    // sticker já têm; estas duas pastilhas eram das últimas ações clicáveis da app sem ele.
    val (interaction, scale) = rememberPressScale()
    Row(
        modifier = modifier
            .pressScale(scale)
            .stickerBlock(fillColor = color, cornerRadius = 16.dp, shadowOffset = 3.dp, borderWidth = 2.dp)
            .clickable(interactionSource = interaction, indication = null, onClick = onClick)
            .padding(horizontal = 10.dp, vertical = 12.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.Center
    ) {
        Icon(icon, contentDescription = label, tint = textColorFor(color), modifier = Modifier.size(20.dp))
        Spacer(Modifier.size(6.dp))
        Text(
            text = label,
            style = MaterialTheme.typography.labelLarge.copy(fontSize = 12.sp, fontWeight = FontWeight.Bold),
            color = textColorFor(color),
            maxLines = 1
        )
    }
}

@Composable
private fun CleanCategoryCard(
    cat: CustomCategory,
    isMine: Boolean,
    onReport: () -> Unit,
    index: Int,
    onPlay: () -> Unit,
    onEdit: () -> Unit,
    onRate: (Int) -> Unit,
    onTogglePublic: () -> Unit
) {
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .cascadeIn(index, key = "customCategorias")
            .stickerBlock(fillColor = if (isMine) Lavender else Cream, cornerRadius = 22.dp, shadowOffset = 4.dp)
            .padding(16.dp)
    ) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Column(modifier = Modifier.weight(1f)) {
                Text(
                    text = cat.titulo,
                    style = MaterialTheme.typography.titleLarge.copy(fontWeight = FontWeight.Bold),
                    color = Ink
                )
                Text(
                    text = "Por ${cat.criadorNome} · ${cat.perguntas.size} perguntas",
                    style = MaterialTheme.typography.labelLarge,
                    color = Ink.copy(alpha = 0.75f)
                )
            }
            if (isMine) {
                Box(
                    modifier = Modifier
                        .stickerCircle(fillColor = if (cat.publica) Teal else Coral, shadowOffset = 2.dp)
                        .clickable { onTogglePublic() }
                        .padding(horizontal = 10.dp, vertical = 5.dp),
                    contentAlignment = Alignment.Center
                ) {
                    Text(
                        text = if (cat.publica) "PÚBLICA" else "PRIVADA",
                        style = MaterialTheme.typography.labelLarge.copy(fontSize = 11.sp, fontWeight = FontWeight.Bold),
                        color = Cream
                    )
                }
            }
        }

        if (cat.descricao.isNotBlank()) {
            Spacer(Modifier.size(6.dp))
            Text(
                text = cat.descricao,
                style = MaterialTheme.typography.bodyLarge,
                color = Ink
            )
        }

        Spacer(Modifier.size(14.dp))

        // Clean Bottom Action Bar (Stars on left, EDITAR and JOGAR side-by-side on right)
        Row(
            modifier = Modifier.fillMaxWidth(),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.SpaceBetween
        ) {
            // Interactive 5-Star Rating
            Row(verticalAlignment = Alignment.CenterVertically) {
                val ratingInt = cat.mediaClassificacao.toInt()
                (1..5).forEach { starIndex ->
                    Icon(
                        imageVector = if (starIndex <= ratingInt) Icons.Rounded.Star else Icons.Rounded.StarBorder,
                        contentDescription = "Star $starIndex",
                        tint = Gold,
                        modifier = Modifier
                            .size(20.dp)
                            .clickable { onRate(starIndex) }
                    )
                }
                Spacer(Modifier.size(4.dp))
                Text(
                    text = "(${cat.totalVotos})",
                    style = MaterialTheme.typography.labelLarge,
                    color = Ink.copy(alpha = 0.7f)
                )
            }

            // Buttons (DENUNCIAR + EDITAR + JOGAR)
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                // Denunciar só aparece em quizzes de outra pessoa — não faz sentido denunciar o
                // próprio, e é a única forma de moderação que existe do lado do jogador.
                if (!isMine) {
                    Box(
                        modifier = Modifier
                            .stickerBlock(fillColor = Cream, cornerRadius = 14.dp, shadowOffset = 3.dp, borderWidth = 2.dp)
                            .clickable(onClick = onReport)
                            .padding(horizontal = 12.dp, vertical = 8.dp),
                        contentAlignment = Alignment.Center
                    ) {
                        Icon(Icons.Rounded.Flag, contentDescription = "Denunciar", tint = Coral, modifier = Modifier.size(18.dp))
                    }
                }
                if (isMine) {
                    Box(
                        modifier = Modifier
                            .stickerBlock(fillColor = Lavender, cornerRadius = 14.dp, shadowOffset = 3.dp, borderWidth = 2.dp)
                            .clickable(onClick = onEdit)
                            .padding(horizontal = 14.dp, vertical = 8.dp),
                        contentAlignment = Alignment.Center
                    ) {
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            Icon(Icons.Rounded.Edit, contentDescription = "Editar", tint = Ink, modifier = Modifier.size(16.dp))
                            Spacer(Modifier.size(4.dp))
                            Text("EDITAR", style = MaterialTheme.typography.labelLarge.copy(fontWeight = FontWeight.Bold), color = Ink)
                        }
                    }
                }
                Box(
                    modifier = Modifier
                        .stickerBlock(fillColor = Gold, cornerRadius = 14.dp, shadowOffset = 3.dp, borderWidth = 2.dp)
                        .clickable(onClick = onPlay)
                        .padding(horizontal = 16.dp, vertical = 8.dp),
                    contentAlignment = Alignment.Center
                ) {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Icon(Icons.Rounded.PlayArrow, contentDescription = "Jogar", tint = Ink, modifier = Modifier.size(18.dp))
                        Spacer(Modifier.size(4.dp))
                        Text("JOGAR", style = MaterialTheme.typography.labelLarge.copy(fontWeight = FontWeight.Bold), color = Ink)
                    }
                }
            }
        }
    }
}

@Composable
private fun PlayCategoryOptionsDialog(
    cat: CustomCategory,
    onDismiss: () -> Unit,
    onPlaySolo: () -> Unit,
    onCreatePrivateRoom: (MatchFormat) -> Unit
) {
    StickerDialog(onDismissRequest = onDismiss) {
        Text(cat.titulo, style = MaterialTheme.typography.titleLarge, color = Ink)
        Spacer(Modifier.size(6.dp))
        Text("Escolhe o modo para jogar esta categoria:", style = MaterialTheme.typography.bodyLarge, color = Ink)
        Spacer(Modifier.size(16.dp))

        StickerButton(
            text = "JOGAR SOLO",
            icon = Icons.Rounded.Person,
            onClick = onPlaySolo,
            fillColor = Gold,
            modifier = Modifier.fillMaxWidth()
        )
        Spacer(Modifier.size(10.dp))
        StickerButton(
            text = "CRIAR SALA POR CÓDIGO (1X1)",
            icon = Icons.Rounded.Groups,
            onClick = { onCreatePrivateRoom(MatchFormat.ONE_V_ONE) },
            fillColor = Purple,
            modifier = Modifier.fillMaxWidth()
        )
        Spacer(Modifier.size(10.dp))
        StickerButton(
            text = "CRIAR SALA POR CÓDIGO (GRUPO)",
            icon = Icons.Rounded.Groups,
            onClick = { onCreatePrivateRoom(MatchFormat.GRUPO) },
            fillColor = Teal,
            modifier = Modifier.fillMaxWidth()
        )
        Spacer(Modifier.size(10.dp))
        StickerButton(
            text = "CANCELAR",
            icon = Icons.Rounded.Close,
            onClick = onDismiss,
            fillColor = Cream,
            modifier = Modifier.fillMaxWidth()
        )
    }
}

@Composable
private fun JoinCodeDialog(
    onDismiss: () -> Unit,
    onJoin: (String) -> Unit
) {
    var code by remember { mutableStateOf("") }
    StickerDialog(onDismissRequest = onDismiss) {
        Text("Entrar numa Sala por Código", style = MaterialTheme.typography.titleLarge, color = Ink)
        Spacer(Modifier.size(6.dp))
        Text(
            "Introduz o código de 4 dígitos gerado pelo teu amigo:",
            style = MaterialTheme.typography.bodyLarge,
            color = Ink
        )
        Spacer(Modifier.size(14.dp))
        StickerTextField(
            value = code,
            onValueChange = { if (it.length <= 6) code = it },
            placeholder = "Código da sala (ex: 4829)",
            icon = Icons.Rounded.Key,
            keyboardType = androidx.compose.ui.text.input.KeyboardType.Number,
            modifier = Modifier.fillMaxWidth()
        )
        Spacer(Modifier.size(18.dp))
        StickerButton(
            text = "ENTRAR",
            icon = Icons.Rounded.Key,
            onClick = { if (code.isNotBlank()) onJoin(code) },
            fillColor = Gold,
            modifier = Modifier.fillMaxWidth()
        )
        Spacer(Modifier.size(10.dp))
        StickerButton(
            text = "CANCELAR",
            icon = Icons.Rounded.Close,
            onClick = onDismiss,
            fillColor = Cream,
            modifier = Modifier.fillMaxWidth()
        )
    }
}

@Composable
private fun CreateCategoryDialog(
    existing: CustomCategory?,
    erro: String?,
    onDismiss: () -> Unit,
    onSave: (String, String, Boolean, List<Question>) -> Unit
) {
    var titulo by remember { mutableStateOf(existing?.titulo ?: "") }
    var descricao by remember { mutableStateOf(existing?.descricao ?: "") }
    var publica by remember { mutableStateOf(existing?.publica ?: true) }

    val perguntas = remember { mutableStateListOf<Question>().apply { if (existing != null) addAll(existing.perguntas) } }

    // Draft question form state
    var editingQuestionIndex by remember { mutableStateOf<Int?>(null) }
    var pText by remember { mutableStateOf("") }
    var opA by remember { mutableStateOf("") }
    var opB by remember { mutableStateOf("") }
    var opC by remember { mutableStateOf("") }
    var opD by remember { mutableStateOf("") }
    var selectedCorrectIndex by remember { mutableIntStateOf(0) }
    var selectedDifficulty by remember { mutableStateOf("medio") }

    fun loadQuestionForEdit(index: Int) {
        val q = perguntas[index]
        editingQuestionIndex = index
        pText = q.pergunta
        val ops = q.opcoes
        opA = ops.getOrNull(0) ?: ""
        opB = ops.getOrNull(1) ?: ""
        opC = ops.getOrNull(2) ?: ""
        opD = ops.getOrNull(3) ?: ""
        val cIdx = ops.indexOf(q.respostaCorreta)
        selectedCorrectIndex = if (cIdx >= 0) cIdx else 0
        selectedDifficulty = q.dificuldade
    }

    fun clearDraft() {
        editingQuestionIndex = null
        pText = ""; opA = ""; opB = ""; opC = ""; opD = ""
        selectedCorrectIndex = 0
        selectedDifficulty = "medio"
    }

    val difficulties = listOf("facil" to "Fácil", "medio" to "Médio", "dificil" to "Difícil")
    val difficultyIndex = difficulties.indexOfFirst { it.first == selectedDifficulty }.coerceAtLeast(0)

    StickerDialog(onDismissRequest = onDismiss) {
        Text(
            if (existing != null) "Editar Categoria" else "Criar Categoria",
            style = MaterialTheme.typography.titleLarge,
            color = Ink
        )
        Spacer(Modifier.size(14.dp))

        Column(
            modifier = Modifier
                .fillMaxWidth()
                .heightIn(max = 520.dp)
                .verticalScroll(rememberScrollState()),
            verticalArrangement = Arrangement.spacedBy(10.dp)
        ) {
            StickerTextField(
                value = titulo,
                onValueChange = { titulo = it },
                placeholder = "Nome da Categoria",
                icon = Icons.Rounded.Edit,
                modifier = Modifier.fillMaxWidth()
            )
            StickerTextField(
                value = descricao,
                onValueChange = { descricao = it },
                placeholder = "Descrição (opcional)",
                icon = Icons.Rounded.Description,
                modifier = Modifier.fillMaxWidth()
            )

            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .stickerBlock(fillColor = Cream, cornerRadius = 16.dp, shadowOffset = 3.dp, borderWidth = 2.dp)
                    .clickable { publica = !publica }
                    .padding(horizontal = 16.dp, vertical = 12.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                Text("Pública para a Comunidade", style = MaterialTheme.typography.bodyLarge, color = Ink, modifier = Modifier.weight(1f))
                Switch(
                    checked = publica,
                    onCheckedChange = { publica = it },
                    colors = SwitchDefaults.colors(
                        checkedThumbColor = Cream, checkedTrackColor = Teal, checkedBorderColor = Ink,
                        uncheckedThumbColor = Cream, uncheckedTrackColor = Ink.copy(alpha = 0.35f), uncheckedBorderColor = Ink
                    )
                )
            }

            Spacer(Modifier.height(2.dp))

            // Question Builder Box
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .stickerBlock(fillColor = Lavender, cornerRadius = 18.dp, shadowOffset = 3.dp)
                    .padding(14.dp),
                verticalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                Text(
                    text = if (editingQuestionIndex != null) "Editar Pergunta #${editingQuestionIndex!! + 1}" else "Adicionar Nova Pergunta",
                    style = MaterialTheme.typography.titleLarge.copy(fontSize = 16.sp, fontWeight = FontWeight.Bold),
                    color = Ink
                )

                SegmentedTabs(
                    labels = difficulties.map { it.second },
                    selectedIndex = difficultyIndex,
                    onSelect = { selectedDifficulty = difficulties[it].first }
                )

                StickerTextField(
                    value = pText,
                    onValueChange = { pText = it },
                    placeholder = "Texto da Pergunta",
                    icon = Icons.Rounded.Edit,
                    modifier = Modifier.fillMaxWidth()
                )

                Text("Opções de Resposta (marca a correta):", style = MaterialTheme.typography.labelLarge.copy(fontSize = 12.sp), color = Ink)

                val optionsList = listOf(opA, opB, opC, opD)
                val setters = listOf<(String) -> Unit>({ opA = it }, { opB = it }, { opC = it }, { opD = it })
                val letras = listOf("A", "B", "C", "D")

                optionsList.forEachIndexed { index, optVal ->
                    val correta = selectedCorrectIndex == index
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        // Emblema A/B/C/D em vez do RadioButton Material — o mesmo desenho
                        // usado no ecrã da pergunta para marcar "qual das opções".
                        Box(
                            modifier = Modifier
                                .size(34.dp)
                                .stickerCircle(fillColor = if (correta) Teal else Cream, shadowOffset = 2.dp, borderWidth = 2.dp)
                                .clickable { selectedCorrectIndex = index },
                            contentAlignment = Alignment.Center
                        ) {
                            Text(letras[index], style = MaterialTheme.typography.labelLarge, color = if (correta) Cream else Ink)
                        }
                        Spacer(Modifier.size(8.dp))
                        // Sem ícone: o emblema A/B/C/D à esquerda já identifica o campo, e um
                        // ✓ repetido nos quatro dizia "correta" em todas elas.
                        StickerTextField(
                            value = optVal,
                            onValueChange = { setters[index](it) },
                            placeholder = if (correta) "Opção ${index + 1} (correta)" else "Opção ${index + 1}",
                            modifier = Modifier.weight(1f)
                        )
                    }
                }

                Spacer(Modifier.height(2.dp))
                // Rótulos curtos: dentro do diálogo a largura é pouca e
                // "+ INCLUIR PERGUNTA" partia em duas linhas dentro do botão.
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    if (editingQuestionIndex != null) {
                        CompactActionButton(
                            label = "CANCELAR",
                            icon = Icons.Rounded.Close,
                            color = Coral,
                            modifier = Modifier.weight(1f),
                            onClick = { clearDraft() }
                        )
                    }
                    CompactActionButton(
                        label = if (editingQuestionIndex != null) "GUARDAR PERGUNTA" else "+ ADICIONAR PERGUNTA",
                        icon = Icons.Rounded.Add,
                        color = Teal,
                        modifier = Modifier.weight(1.5f),
                        onClick = {
                            val validOps = optionsList.filter { it.isNotBlank() }
                            if (pText.isNotBlank() && validOps.size >= 2) {
                                val correctAns = optionsList[selectedCorrectIndex].ifBlank { validOps.first() }
                                val newQ = Question(
                                    pergunta = pText,
                                    opcoes = validOps,
                                    respostaCorreta = correctAns,
                                    dificuldade = selectedDifficulty
                                )
                                val eIdx = editingQuestionIndex
                                if (eIdx != null && eIdx in perguntas.indices) {
                                    perguntas[eIdx] = newQ
                                } else {
                                    perguntas.add(newQ)
                                }
                                clearDraft()
                            }
                        }
                    )
                }
            }

            if (perguntas.isNotEmpty()) {
                Spacer(Modifier.height(2.dp))
                Text("Perguntas (${perguntas.size}):", style = MaterialTheme.typography.titleLarge, color = Ink)
                perguntas.forEachIndexed { i, q ->
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .cascadeIn(i, key = "createCategoryQuestions")
                            .stickerBlock(fillColor = Cream, cornerRadius = 14.dp, shadowOffset = 2.dp)
                            .padding(10.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Column(modifier = Modifier.weight(1f)) {
                            Text("${i + 1}. ${q.pergunta}", style = MaterialTheme.typography.labelLarge.copy(fontWeight = FontWeight.Bold), color = Ink)
                            Text("Certa: ${q.respostaCorreta} · [${q.dificuldade}]", style = MaterialTheme.typography.bodyLarge, color = Teal)
                        }
                        Row(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                            Icon(
                                Icons.Rounded.Edit,
                                contentDescription = "Editar",
                                tint = Purple,
                                modifier = Modifier
                                    .size(22.dp)
                                    .clickable { loadQuestionForEdit(i) }
                            )
                            Icon(
                                Icons.Rounded.Delete,
                                contentDescription = "Eliminar",
                                tint = Coral,
                                modifier = Modifier
                                    .size(22.dp)
                                    .clickable {
                                        perguntas.removeAt(i)
                                        if (editingQuestionIndex == i) clearDraft()
                                    }
                            )
                        }
                    }
                    Spacer(Modifier.size(8.dp))
                }
            }
        }

        if (erro != null) {
            Spacer(Modifier.size(12.dp))
            Text(erro, style = MaterialTheme.typography.bodyMedium, color = Coral)
        }

        Spacer(Modifier.size(16.dp))

        // Desativado usa Neutral (a cor de "apagado" da app) e não um Ink translúcido, que
        // pintava o botão quase a preto — o elemento mais pesado do diálogo era justamente
        // o que não se podia carregar.
        val podeGuardar = titulo.isNotBlank() && perguntas.isNotEmpty()
        StickerButton(
            text = if (existing != null) "GUARDAR ALTERAÇÕES" else "CRIAR CATEGORIA",
            icon = Icons.Rounded.Check,
            onClick = {
                if (podeGuardar) {
                    onSave(titulo, descricao, publica, perguntas.toList())
                }
            },
            fillColor = if (podeGuardar) Teal else Neutral,
            modifier = Modifier.fillMaxWidth()
        )
        Spacer(Modifier.size(10.dp))
        StickerButton(
            text = "CANCELAR",
            icon = Icons.Rounded.Close,
            onClick = onDismiss,
            fillColor = Cream,
            modifier = Modifier.fillMaxWidth()
        )
    }
}

/**
 * Denúncia de um quiz. Uma por pessoa por quiz — as rules recusam a segunda, por isso o diálogo
 * verifica primeiro e mostra o estado em vez de deixar carregar num botão que ia falhar.
 */
@Composable
private fun ReportDialog(
    cat: CustomCategory,
    repo: CustomCategoryRepository,
    myUid: String,
    onDismiss: () -> Unit
) {
    val scope = rememberCoroutineScope()
    var motivo by remember { mutableStateOf("") }
    var jaDenunciou by remember { mutableStateOf<Boolean?>(null) }
    var enviando by remember { mutableStateOf(false) }
    var feito by remember { mutableStateOf(false) }
    var erro by remember { mutableStateOf<String?>(null) }

    LaunchedEffect(cat.id) {
        jaDenunciou = runCatching { repo.jaDenunciei(cat.id, myUid) }.getOrDefault(false)
    }

    val motivos = listOf(
        "Conteúdo ofensivo ou insultuoso",
        "Perguntas ou respostas erradas",
        "Spam ou publicidade",
        "Conteúdo sexual ou violento",
        "Outro motivo"
    )

    StickerDialog(onDismissRequest = { if (!enviando) onDismiss() }, fillColor = Cream) {
        Text("Denunciar quiz", style = MaterialTheme.typography.titleLarge, color = Coral)
        Spacer(Modifier.size(6.dp))
        Text(cat.titulo, style = MaterialTheme.typography.bodyLarge, color = Ink)
        Spacer(Modifier.size(14.dp))

        when {
            jaDenunciou == null -> Text("A verificar…", style = MaterialTheme.typography.bodyLarge, color = Ink)

            feito -> {
                Text(
                    "Denúncia registada. Obrigado — vamos rever o quiz.",
                    style = MaterialTheme.typography.bodyLarge, color = Ink
                )
                Spacer(Modifier.size(16.dp))
                StickerButton("FECHAR", Icons.Rounded.Check, onDismiss, Modifier.fillMaxWidth(), Lavender)
            }

            jaDenunciou == true -> {
                Text(
                    "Já denunciaste este quiz. Cada pessoa só pode denunciar uma vez.",
                    style = MaterialTheme.typography.bodyLarge, color = Ink
                )
                Spacer(Modifier.size(16.dp))
                StickerButton("FECHAR", Icons.Rounded.Check, onDismiss, Modifier.fillMaxWidth(), Lavender)
            }

            else -> {
                Text("Qual é o problema?", style = MaterialTheme.typography.bodyLarge, color = Ink)
                Spacer(Modifier.size(10.dp))
                motivos.forEach { m ->
                    val escolhido = motivo == m
                    Row(
                        Modifier
                            .fillMaxWidth()
                            .padding(vertical = 3.dp)
                            .stickerBlock(
                                fillColor = if (escolhido) Coral else Lavender,
                                cornerRadius = 14.dp, shadowOffset = 3.dp, borderWidth = 2.dp
                            )
                            .clickable { motivo = m }
                            .padding(horizontal = 14.dp, vertical = 10.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Text(
                            m,
                            style = MaterialTheme.typography.bodyLarge,
                            color = if (escolhido) textColorFor(Coral) else Ink
                        )
                    }
                }

                if (erro != null) {
                    Spacer(Modifier.size(10.dp))
                    Text(erro!!, style = MaterialTheme.typography.bodyMedium, color = Coral)
                }

                Spacer(Modifier.size(16.dp))
                val pode = motivo.isNotBlank() && !enviando
                StickerButton(
                    text = if (enviando) "A ENVIAR…" else "ENVIAR DENÚNCIA",
                    icon = Icons.Rounded.Flag,
                    onClick = {
                        if (!pode) return@StickerButton
                        enviando = true
                        erro = null
                        scope.launch {
                            try {
                                repo.denunciar(cat.id, myUid, motivo)
                                feito = true
                            } catch (e: Exception) {
                                erro = "Não foi possível enviar a denúncia."
                            } finally {
                                enviando = false
                            }
                        }
                    },
                    modifier = Modifier.fillMaxWidth(),
                    fillColor = if (pode) Coral else Neutral
                )
                Spacer(Modifier.size(8.dp))
                StickerButton("CANCELAR", Icons.Rounded.Close, { if (!enviando) onDismiss() },
                    Modifier.fillMaxWidth(), Lavender)
            }
        }
    }
}
