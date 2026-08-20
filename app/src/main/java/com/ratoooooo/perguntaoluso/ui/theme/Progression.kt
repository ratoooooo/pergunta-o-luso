package com.ratoooooo.perguntaoluso.ui.theme

import androidx.compose.animation.core.FastOutSlowInEasing
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.tween
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.ratoooooo.perguntaoluso.data.Progressao

/** Gold circle showing the player's level number (mockup "Nv N" badge). */
@Composable
fun LevelBadge(nivel: Int, modifier: Modifier = Modifier, size: Dp = 44.dp, fillColor: Color = Gold) {
    Box(
        modifier.size(size).stickerCircle(fillColor = fillColor, shadowOffset = 3.dp, borderWidth = 2.dp),
        contentAlignment = Alignment.Center
    ) {
        Text("$nivel", style = MaterialTheme.typography.titleLarge, color = textColorFor(fillColor))
    }
}

/**
 * Cor da barra de XP: **Roxo liso, em toda a app**.
 *
 * Já foi um gradiente Teal → Azul → Roxo. O gradiente resolvia um problema que afinal não
 * existe — distinguir a barra de XP da barra do tempo — porque as duas nunca partilham ecrã:
 * a do tempo só vive na pergunta, a de XP só no Início, no Perfil e no fim de jogo. O que o
 * gradiente trazia a sério era três cores a mudar de sítio conforme a largura da barra, e ler-se
 * diferente em cada ecrã.
 *
 * Roxo e não outra: Dourado é a ação primária no Início, mesmo por cima desta barra; Coral é
 * destrutivo; Teal é "resposta certa". Sobra o Roxo, que já é a cor da progressão noutro sítio
 * (`LevelPill`) e não é cor de estado nenhum.
 */
val XpFill = Purple

/**
 * XP progress bar for the current level. When [showLabel] is set it prints
 * `NÍVEL n` on the left and `xpNoNivelAtual / xpNecessario XP` on the right above the bar.
 */
@Composable
fun XpBar(
    estado: Progressao.Estado,
    modifier: Modifier = Modifier,
    showLabel: Boolean = true,
    levelLabel: Boolean = true,
    patente: String? = null,
    trackColor: Color = Lavender,
    fillColor: Color = XpFill
) {
    androidx.compose.foundation.layout.Column(modifier) {
        if (showLabel) {
            // A patente ocupa o canto esquerdo desta linha. Com `levelLabel = false` — que é o
            // caso no Início e no Perfil, onde o número já está no emblema mesmo ao lado — a
            // linha só tinha o "x / y XP" encostado à direita e o resto era espaço morto. Pôr
            // aqui o nome mantém-no colado ao bloco de progressão sem disputar largura com o
            // nome do jogador, que na Fase 29 já se mostrou apertado em ecrãs estreitos.
            val nomePatente = patente?.uppercase()?.takeIf { it.isNotBlank() }
            val leading = when {
                levelLabel && nomePatente != null -> "NÍVEL ${estado.nivel} · $nomePatente"
                nomePatente != null -> nomePatente
                levelLabel -> "NÍVEL ${estado.nivel}"
                else -> null
            }
            // 13 sp quando há patente. A 16 sp, "GRUMETE" + "510 / 750 XP" não cabem lado a lado
            // num ecrã de 720 px com `font_scale` a 1.3 e o nome saía cortado ("GRUM…"). É o
            // mesmo recurso do `SegmentedTabs` com quatro separadores e da pastilha do Ranking.
            val labelStyle = MaterialTheme.typography.labelLarge.let {
                if (nomePatente != null) it.copy(fontSize = 13.sp, lineHeight = 17.sp) else it
            }
            Row(
                Modifier.fillMaxWidth(),
                horizontalArrangement = if (leading != null) Arrangement.SpaceBetween else Arrangement.End
            ) {
                if (leading != null) {
                    Text(
                        leading,
                        style = labelStyle,
                        color = Ink,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis,
                        modifier = Modifier.weight(1f, fill = false)
                    )
                }
                Text(
                    "${estado.xpNoNivelAtual} / ${estado.xpNecessarioProximoNivel} XP",
                    style = labelStyle, color = Ink, maxLines = 1
                )
            }
            Spacer(Modifier.size(6.dp))
        }
        // A barra enche com animação em vez de aparecer já cheia; perto do próximo nível
        // (>= 85%) ganha um brilho pulsante, para o salto de nível se antecipar. O brilho é
        // Cream e não Dourado: em Dourado a barra passava a ler-se como duas cores mesmo depois
        // de o gradiente ter saído, e no Início competia com o botão JOGAR, que é a ação
        // primária. Um clarão da mesma barra continua a chamar a atenção sem inventar cor.
        val fracaoAnimada by animateFloatAsState(
            targetValue = estado.fracao,
            animationSpec = tween(700, easing = FastOutSlowInEasing),
            label = "xpFill"
        )
        val quaseNivel = estado.fracao >= 0.85f
        val brilho = rememberGlow(quaseNivel, min = 0.2f, max = 0.6f)
        Box(
            Modifier.fillMaxWidth().height(16.dp).clip(RoundedCornerShape(8.dp))
                .background(trackColor).border(3.dp, Ink, RoundedCornerShape(8.dp))
        ) {
            Box(
                Modifier.fillMaxHeight().fillMaxWidth(fracaoAnimada)
                    .clip(RoundedCornerShape(8.dp)).background(fillColor)
            )
            if (quaseNivel) {
                Box(
                    Modifier.fillMaxHeight().fillMaxWidth(fracaoAnimada)
                        .clip(RoundedCornerShape(8.dp))
                        .background(Cream.copy(alpha = brilho * 0.5f))
                )
            }
        }
    }
}

/**
 * Compact "Nv N" pill for tight rows (ranking list), optionally carrying the rank name
 * ("Nv 7 · Marinheiro").
 *
 * O corpo desce para 13 sp quando leva patente — é o mesmo recurso que o `SegmentedTabs` já
 * usa com quatro separadores. A 16 sp, "Nv 12 · Descobridor" não cabe na coluna que sobra
 * numa linha do ranking (nome + avatar + pontuação já lá estão) e a palavra partia-se.
 */
@Composable
fun LevelPill(
    nivel: Int,
    modifier: Modifier = Modifier,
    patente: String? = null,
    fillColor: Color = Purple
) {
    val nome = patente?.takeIf { it.isNotBlank() }
    val texto = if (nome != null) "Nv $nivel · $nome" else "Nv $nivel"
    val style = MaterialTheme.typography.labelLarge.let {
        if (nome != null) it.copy(fontSize = 13.sp, lineHeight = 17.sp) else it
    }
    Box(
        modifier
            .clip(RoundedCornerShape(10.dp))
            .background(fillColor)
            .border(2.dp, Ink, RoundedCornerShape(10.dp))
            .padding(horizontal = 8.dp, vertical = 3.dp),
        contentAlignment = Alignment.Center
    ) {
        Text(
            texto,
            style = style,
            color = textColorFor(fillColor),
            textAlign = TextAlign.Center,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis
        )
    }
}
