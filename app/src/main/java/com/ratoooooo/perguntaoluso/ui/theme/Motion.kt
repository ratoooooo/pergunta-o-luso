package com.ratoooooo.perguntaoluso.ui.theme

import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.FastOutSlowInEasing
import androidx.compose.animation.core.LinearEasing
import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.Spring
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.spring
import androidx.compose.animation.core.tween
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.interaction.collectIsPressedAsState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.composed
import androidx.compose.ui.draw.scale
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.unit.dp
import kotlinx.coroutines.launch

/**
 * Movimento partilhado da app.
 *
 * Regra: nada aqui bloqueia o jogador. As entradas são curtas (≤ 320 ms) e puramente
 * visuais — o toque já funciona enquanto a animação corre —, e as animações contínuas
 * (pulsar, brilho) nunca alteram a posição dos elementos, só a escala/opacidade.
 */
object Motion {
    const val ENTRY_MS = 260
    const val STAGGER_MS = 55
    const val MAX_STAGGER_STEPS = 6
    const val FEEDBACK_MS = 280
}

/** Mola curta e sem oscilação exagerada, usada em toques e "pops". */
fun <T> stickerSpring() = spring<T>(
    dampingRatio = Spring.DampingRatioMediumBouncy,
    stiffness = Spring.StiffnessMediumLow
)

/**
 * Entrada em cascata: o item [index] aparece com um pequeno atraso e sobe alguns dp.
 * O atraso satura ao fim de [Motion.MAX_STAGGER_STEPS] itens, para uma lista longa não
 * demorar segundos a compor-se.
 */
@Composable
fun Modifier.cascadeIn(index: Int, key: Any? = Unit): Modifier = composed {
    val progresso = remember(key, index) { Animatable(0f) }
    val density = LocalDensity.current
    LaunchedEffect(key, index) {
        val passos = index.coerceAtMost(Motion.MAX_STAGGER_STEPS)
        progresso.animateTo(
            1f,
            tween(Motion.ENTRY_MS, delayMillis = passos * Motion.STAGGER_MS, easing = FastOutSlowInEasing)
        )
    }
    val p = progresso.value
    val deslocamento = with(density) { (1f - p) * 14.dp.toPx() }
    this.graphicsLayer {
        alpha = p
        translationY = deslocamento
    }
}

/**
 * Entrada de impacto: o item [index] salta de pequeno para o tamanho normal com mola
 * (em vez de só desvanecer/subir como o [cascadeIn]). Usada só no "Encontrado!" — o
 * momento em que os jogadores da partida aparecem pela primeira vez merece mais peso do
 * que uma simples entrada em cascata.
 */
@Composable
fun Modifier.bounceIn(index: Int, key: Any? = Unit): Modifier = composed {
    val escala = remember(key, index) { Animatable(0.5f) }
    val alfa = remember(key, index) { Animatable(0f) }
    LaunchedEffect(key, index) {
        val passos = index.coerceAtMost(Motion.MAX_STAGGER_STEPS)
        kotlinx.coroutines.delay((passos * Motion.STAGGER_MS).toLong())
        launch { alfa.animateTo(1f, tween(Motion.ENTRY_MS)) }
        escala.animateTo(1f, stickerSpring())
    }
    this.graphicsLayer {
        alpha = alfa.value
        scaleX = escala.value
        scaleY = escala.value
    }
}

/**
 * Feedback de toque: encolhe enquanto o dedo está em baixo e volta com mola.
 * Devolve também a [MutableInteractionSource] a ligar ao `clickable`.
 */
@Composable
fun rememberPressScale(): Pair<MutableInteractionSource, Float> {
    val interaction = remember { MutableInteractionSource() }
    val pressed by interaction.collectIsPressedAsState()
    val escala = remember { Animatable(1f) }
    LaunchedEffect(pressed) {
        escala.animateTo(if (pressed) 0.96f else 1f, stickerSpring())
    }
    return interaction to escala.value
}

/** Aplica a escala devolvida por [rememberPressScale]. */
fun Modifier.pressScale(escala: Float): Modifier = this.scale(escala)

/**
 * Pulsar contínuo e discreto (escala), para destacar sem distrair — vencedor do pódio,
 * conquista desbloqueada, barra de XP quase cheia.
 */
@Composable
fun rememberPulse(ativo: Boolean, min: Float = 1f, max: Float = 1.04f, periodoMs: Int = 1100): Float {
    if (!ativo) return 1f
    val transition = rememberInfiniteTransition(label = "pulse")
    val valor by transition.animateFloat(
        initialValue = min,
        targetValue = max,
        animationSpec = infiniteRepeatable(tween(periodoMs, easing = LinearEasing), RepeatMode.Reverse),
        label = "pulseValue"
    )
    return valor
}

/** Brilho contínuo (opacidade), para halos. */
@Composable
fun rememberGlow(ativo: Boolean, min: Float = 0.25f, max: Float = 0.75f, periodoMs: Int = 1300): Float {
    if (!ativo) return 0f
    val transition = rememberInfiniteTransition(label = "glow")
    val valor by transition.animateFloat(
        initialValue = min,
        targetValue = max,
        animationSpec = infiniteRepeatable(tween(periodoMs, easing = LinearEasing), RepeatMode.Reverse),
        label = "glowValue"
    )
    return valor
}
