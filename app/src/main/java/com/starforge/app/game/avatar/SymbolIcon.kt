package com.starforge.app.game.avatar

import androidx.compose.foundation.Canvas
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.StrokeJoin
import androidx.compose.ui.graphics.drawscope.DrawScope
import androidx.compose.ui.graphics.drawscope.Stroke
import com.starforge.app.ui.theme.Ink

/**
 * Draws a [PortugueseSymbol] as sticker-style line art in a single [tint]: one shared stroke
 * width across every icon (so contour thickness is identical), rounded caps/joins. Works on a
 * coloured avatar circle (tint = cream), locked (tint = grey), or unlocked (tint = ink/gold).
 */
@Composable
fun SymbolIcon(symbol: PortugueseSymbol, modifier: Modifier = Modifier, tint: Color = Ink) {
    Canvas(modifier) {
        val sw = size.minDimension * 0.072f
        val st = Stroke(width = sw, cap = StrokeCap.Round, join = StrokeJoin.Round)
        when (symbol) {
            PortugueseSymbol.AZULEJO -> drawAzulejo(tint, st)
            PortugueseSymbol.NATA -> drawNata(tint, st)
            PortugueseSymbol.CARAVELA -> drawCaravela(tint, st)
            PortugueseSymbol.FAROL -> drawFarol(tint, st)
            PortugueseSymbol.SARDINHA -> drawSardinha(tint, st)
            PortugueseSymbol.GALO -> drawGalo(tint, st)
            PortugueseSymbol.LUSIADAS -> drawLusiadas(tint, st)
            PortugueseSymbol.GUITARRA -> drawGuitarra(tint, st)
            PortugueseSymbol.CALCADA -> drawCalcada(tint, st)
            PortugueseSymbol.CORACAO -> drawCoracao(tint, st)
        }
    }
}

// ---- fractional-coordinate helpers (0..1 of the canvas) ----
private fun DrawScope.X(f: Float) = f * size.width
private fun DrawScope.Y(f: Float) = f * size.height
private fun DrawScope.o(fx: Float, fy: Float) = Offset(fx * size.width, fy * size.height)

private fun DrawScope.line(x1: Float, y1: Float, x2: Float, y2: Float, c: Color, st: Stroke) =
    drawLine(c, o(x1, y1), o(x2, y2), strokeWidth = st.width, cap = st.cap)

private fun DrawScope.dot(fx: Float, fy: Float, r: Float, c: Color) =
    drawCircle(c, r * size.minDimension, o(fx, fy))

private fun DrawScope.path(c: Color, st: Stroke, block: Path.() -> Unit) {
    val p = Path().apply {
        // Path uses absolute px; wrap moveTo/lineTo via extension below
        block()
    }
    drawPath(p, c, style = st)
}

private fun Path.m(d: DrawScope, fx: Float, fy: Float) = moveTo(fx * d.size.width, fy * d.size.height)
private fun Path.l(d: DrawScope, fx: Float, fy: Float) = lineTo(fx * d.size.width, fy * d.size.height)
private fun Path.q(d: DrawScope, cx: Float, cy: Float, x: Float, y: Float) =
    quadraticBezierTo(cx * d.size.width, cy * d.size.height, x * d.size.width, y * d.size.height)

// ---- symbols ----

private fun DrawScope.drawAzulejo(c: Color, st: Stroke) {
    val d = this
    // outer tile frame
    path(c, st) { m(d, 0.18f, 0.18f); l(d, 0.82f, 0.18f); l(d, 0.82f, 0.82f); l(d, 0.18f, 0.82f); close() }
    // inner diamond motif
    path(c, st) { m(d, 0.5f, 0.26f); l(d, 0.74f, 0.5f); l(d, 0.5f, 0.74f); l(d, 0.26f, 0.5f); close() }
    dot(0.5f, 0.5f, 0.045f, c)
    listOf(0.30f to 0.30f, 0.70f to 0.30f, 0.30f to 0.70f, 0.70f to 0.70f).forEach { (x, y) -> dot(x, y, 0.03f, c) }
}

private fun DrawScope.drawNata(c: Color, st: Stroke) {
    val d = this
    // custard top (oval rim)
    drawOval(c, topLeft = o(0.26f, 0.4f), size = Size(X(0.48f), Y(0.18f)), style = st)
    // pastry cup
    path(c, st) { m(d, 0.3f, 0.49f); l(d, 0.37f, 0.8f); l(d, 0.63f, 0.8f); l(d, 0.7f, 0.49f) }
    // fluted ridges
    line(0.42f, 0.56f, 0.4f, 0.78f, c, st)
    line(0.5f, 0.57f, 0.5f, 0.79f, c, st)
    line(0.58f, 0.56f, 0.6f, 0.78f, c, st)
    // scorch mark
    dot(0.5f, 0.47f, 0.035f, c)
}

private fun DrawScope.drawCaravela(c: Color, st: Stroke) {
    val d = this
    // hull (boat bowl)
    path(c, st) { m(d, 0.12f, 0.62f); l(d, 0.22f, 0.78f); l(d, 0.78f, 0.78f); l(d, 0.88f, 0.62f); close() }
    // deck line
    line(0.12f, 0.62f, 0.88f, 0.62f, c, st)
    // mast
    line(0.5f, 0.62f, 0.5f, 0.12f, c, st)
    // lower square sail + cross of the Order of Christ
    path(c, st) { m(d, 0.32f, 0.4f); l(d, 0.68f, 0.4f); l(d, 0.68f, 0.56f); l(d, 0.32f, 0.56f); close() }
    line(0.5f, 0.4f, 0.5f, 0.56f, c, st)
    line(0.4f, 0.48f, 0.6f, 0.48f, c, st)
    // upper square sail
    path(c, st) { m(d, 0.37f, 0.16f); l(d, 0.63f, 0.16f); l(d, 0.63f, 0.32f); l(d, 0.37f, 0.32f); close() }
}

private fun DrawScope.drawFarol(c: Color, st: Stroke) {
    val d = this
    // tower (wider at base)
    path(c, st) { m(d, 0.4f, 0.82f); l(d, 0.44f, 0.42f); l(d, 0.56f, 0.42f); l(d, 0.6f, 0.82f) }
    line(0.34f, 0.82f, 0.66f, 0.82f, c, st)
    // band
    line(0.435f, 0.6f, 0.565f, 0.6f, c, st)
    // lantern room
    path(c, st) { m(d, 0.43f, 0.42f); l(d, 0.43f, 0.3f); l(d, 0.57f, 0.3f); l(d, 0.57f, 0.42f) }
    // roof
    path(c, st) { m(d, 0.4f, 0.3f); l(d, 0.5f, 0.19f); l(d, 0.6f, 0.3f) }
    // light beams
    line(0.6f, 0.35f, 0.72f, 0.31f, c, st)
    line(0.4f, 0.35f, 0.28f, 0.31f, c, st)
}

private fun DrawScope.drawSardinha(c: Color, st: Stroke) {
    val d = this
    // body
    path(c, st) {
        m(d, 0.16f, 0.5f); q(d, 0.42f, 0.28f, 0.66f, 0.44f); q(d, 0.72f, 0.5f, 0.66f, 0.56f)
        q(d, 0.42f, 0.72f, 0.16f, 0.5f); close()
    }
    // tail
    path(c, st) { m(d, 0.66f, 0.5f); l(d, 0.86f, 0.37f); l(d, 0.82f, 0.5f); l(d, 0.86f, 0.63f); close() }
    // eye
    dot(0.27f, 0.46f, 0.028f, c)
    // gill
    path(c, st) { m(d, 0.37f, 0.38f); q(d, 0.34f, 0.5f, 0.37f, 0.62f) }
}

private fun DrawScope.drawGalo(c: Color, st: Stroke) {
    val d = this
    // plump body (leaning right)
    path(c, st) {
        m(d, 0.36f, 0.72f)
        q(d, 0.28f, 0.5f, 0.5f, 0.46f)
        q(d, 0.64f, 0.44f, 0.62f, 0.64f)
        q(d, 0.58f, 0.76f, 0.36f, 0.72f)
        close()
    }
    // big fanned tail (three feathers up-left)
    path(c, st) { m(d, 0.38f, 0.66f); q(d, 0.14f, 0.58f, 0.2f, 0.3f) }
    path(c, st) { m(d, 0.34f, 0.6f); q(d, 0.2f, 0.44f, 0.32f, 0.24f) }
    path(c, st) { m(d, 0.44f, 0.52f); q(d, 0.34f, 0.36f, 0.46f, 0.22f) }
    // neck to head (right)
    path(c, st) { m(d, 0.58f, 0.5f); q(d, 0.72f, 0.44f, 0.72f, 0.32f) }
    drawCircle(c, 0.08f * size.minDimension, o(0.72f, 0.28f), style = st)
    // comb (bumps on top of head)
    path(c, st) { m(d, 0.66f, 0.22f); q(d, 0.69f, 0.14f, 0.72f, 0.2f); q(d, 0.75f, 0.13f, 0.78f, 0.2f) }
    // beak
    path(c, st) { m(d, 0.8f, 0.28f); l(d, 0.88f, 0.3f); l(d, 0.8f, 0.33f) }
    // wattle
    line(0.74f, 0.35f, 0.74f, 0.42f, c, st)
    // legs + feet
    line(0.46f, 0.74f, 0.46f, 0.86f, c, st)
    line(0.43f, 0.86f, 0.5f, 0.86f, c, st)
    line(0.54f, 0.74f, 0.54f, 0.86f, c, st)
    line(0.51f, 0.86f, 0.58f, 0.86f, c, st)
}

private fun DrawScope.drawLusiadas(c: Color, st: Stroke) {
    val d = this
    // spine
    line(0.5f, 0.28f, 0.5f, 0.78f, c, st)
    // left page
    path(c, st) { m(d, 0.5f, 0.3f); q(d, 0.3f, 0.22f, 0.16f, 0.32f); l(d, 0.16f, 0.72f); q(d, 0.3f, 0.64f, 0.5f, 0.74f) }
    // right page
    path(c, st) { m(d, 0.5f, 0.3f); q(d, 0.7f, 0.22f, 0.84f, 0.32f); l(d, 0.84f, 0.72f); q(d, 0.7f, 0.64f, 0.5f, 0.74f) }
    // text hints
    line(0.24f, 0.44f, 0.42f, 0.42f, c, st)
    line(0.24f, 0.54f, 0.42f, 0.52f, c, st)
    line(0.58f, 0.42f, 0.76f, 0.44f, c, st)
    line(0.58f, 0.52f, 0.76f, 0.54f, c, st)
}

private fun DrawScope.drawGuitarra(c: Color, st: Stroke) {
    val d = this
    // waisted (figure-8) guitar body
    path(c, st) {
        m(d, 0.34f, 0.4f)
        q(d, 0.2f, 0.44f, 0.24f, 0.58f)   // upper bout (left)
        q(d, 0.27f, 0.64f, 0.33f, 0.64f)  // waist in
        q(d, 0.22f, 0.68f, 0.24f, 0.82f)  // lower bout (left)
        q(d, 0.32f, 0.92f, 0.44f, 0.9f)   // bottom
        q(d, 0.56f, 0.92f, 0.6f, 0.82f)   // lower bout (right)
        q(d, 0.62f, 0.68f, 0.51f, 0.64f)  // waist in (right)
        q(d, 0.57f, 0.64f, 0.6f, 0.58f)   // waist out
        q(d, 0.64f, 0.44f, 0.5f, 0.4f)    // upper bout (right)
        close()
    }
    // soundhole
    drawCircle(c, 0.055f * size.minDimension, o(0.42f, 0.7f), style = st)
    // bridge
    line(0.34f, 0.8f, 0.5f, 0.8f, c, st)
    // neck
    line(0.4f, 0.4f, 0.62f, 0.14f, c, st)
    line(0.47f, 0.42f, 0.69f, 0.16f, c, st)
    // strings along the neck
    line(0.43f, 0.41f, 0.655f, 0.15f, c, st)
    // headstock + tuning pegs
    path(c, st) { m(d, 0.62f, 0.14f); l(d, 0.66f, 0.08f); l(d, 0.76f, 0.14f); l(d, 0.69f, 0.2f); close() }
    dot(0.66f, 0.12f, 0.018f, c)
    dot(0.72f, 0.14f, 0.018f, c)
}

private fun DrawScope.drawCalcada(c: Color, st: Stroke) {
    val d = this
    // three wave rows (Rossio-style calçada)
    listOf(0.34f, 0.5f, 0.66f).forEach { y ->
        path(c, st) {
            m(d, 0.14f, y)
            q(d, 0.26f, y - 0.1f, 0.38f, y)
            q(d, 0.5f, y + 0.1f, 0.62f, y)
            q(d, 0.74f, y - 0.1f, 0.86f, y)
        }
    }
}

private fun DrawScope.drawCoracao(c: Color, st: Stroke) {
    val d = this
    // Coração de Viana: heart + top loop/flame
    path(c, st) {
        m(d, 0.5f, 0.8f)
        q(d, 0.16f, 0.56f, 0.24f, 0.36f)
        q(d, 0.32f, 0.22f, 0.5f, 0.36f)
        q(d, 0.68f, 0.22f, 0.76f, 0.36f)
        q(d, 0.84f, 0.56f, 0.5f, 0.8f)
        close()
    }
    // crown loop at top
    drawCircle(c, 0.06f * size.minDimension, o(0.5f, 0.24f), style = st)
    // inner filigree hint
    dot(0.5f, 0.52f, 0.03f, c)
}
