package com.thegadget.app.shell

import androidx.compose.animation.core.LinearEasing
import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.tween
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Rect
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.BlendMode
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.ColorFilter
import androidx.compose.ui.graphics.ColorMatrix
import androidx.compose.ui.graphics.drawscope.withTransform
import androidx.compose.runtime.remember
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.IntOffset
import androidx.compose.ui.unit.IntSize
import com.thegadget.app.R
import com.thegadget.app.core.Clock
import com.thegadget.app.ui.SvgPalette
import com.thegadget.app.ui.SvgPaths
import com.thegadget.app.ui.renderSvgGroup
import com.thegadget.app.ui.theme.Css
import com.thegadget.app.ui.theme.GadgetTokens
import kotlin.math.abs
import kotlin.math.sin

/**
 * The backdrop: `body::before` radial wash, the drifting wireframe SVG (exact extracted geometry,
 * tinted and alpha-blended as `hub.css` `.wire` does), a vignette, and the `Particles` canvas.
 */
@Composable
fun Backdrop(tokens: GadgetTokens, modifier: Modifier = Modifier) {
    val group = SvgPaths.require("wire.Wireframe")
    val palette = SvgPalette(currentColor = Css.GLYPH, cssVar = Css::cssVar)
    val context = LocalContext.current
    val atmosphere = remember {
        android.graphics.BitmapFactory.decodeResource(context.resources, R.drawable.atmosphere).asImageBitmap()
    }
    // `.bg-photo`: grayscale, contrast 1.14, brightness 0.68, opacity .5, screen-blended.
    val photoFilter = ColorFilter.colorMatrix(
        ColorMatrix(
            floatArrayOf(
                0.1648f, 0.5544f, 0.0560f, 0f, -12.14f,
                0.1648f, 0.5544f, 0.0560f, 0f, -12.14f,
                0.1648f, 0.5544f, 0.0560f, 0f, -12.14f,
                0f, 0f, 0f, 1f, 0f,
            ),
        ),
    )
    Box(modifier.fillMaxSize()) {
        Canvas(Modifier.fillMaxSize()) {
            // --ink base
            drawRect(Color(0xFF050505))
            // .bg-photo: `background: center / cover` at inset -5%. Cover-crop (NOT stretch) so the
            // same region of atmosphere.jpg lands at each screen point as in the browser.
            val iw = atmosphere.width.toFloat()
            val ih = atmosphere.height.toFloat()
            val targetW = size.width * 1.1f
            val targetH = size.height * 1.1f
            val cover = maxOf(targetW / iw, targetH / ih)
            val drawW = iw * cover
            val drawH = ih * cover
            drawImage(
                image = atmosphere,
                dstOffset = IntOffset(((size.width - drawW) / 2f).toInt(), ((size.height - drawH) / 2f).toInt()),
                dstSize = IntSize(drawW.toInt(), drawH.toInt()),
                // CSS is `opacity:.5; mix-blend-mode:screen`, but over the near-black --ink base the
                // browser's screen composite yields ~full photo luminance (REFPIX measured lum 151 at
                // a bright spot — impossible under a literal 0.5 alpha). Calibrated to that render.
                alpha = 1f,
                colorFilter = photoFilter,
                blendMode = BlendMode.Screen,
            )
            // .bg-glow: four broad radial light pools (exact CSS colours/stops).
            bgPool(0.38f, 0.31f, 0.62f, 0.46f, Color(0x33C4C6C1), 0.70f)
            bgPool(0.75f, 0.30f, 0.44f, 0.34f, Color(0x24A5A8A2), 0.70f)
            bgPool(0.50f, 0.68f, 0.72f, 0.40f, Color(0x1A7A7C78), 0.72f)
            bgPool(0.08f, 0.54f, 0.30f, 0.22f, Color(0x21BABCB7), 0.70f)
            // `.wire` fills the centered `.frame` (fx, fy, sw, sh) — not the whole screen.
            val m = com.thegadget.app.core.GadgetMetrics.compute(size.width, size.height)
            renderSvgGroup(
                group = group,
                palette = palette,
                variant = "stroke",
                dst = Rect(m.fx, m.fy, m.fx + m.sw, m.fy + m.sh),
                css = Css::wireElement,
            )
            // .bg-vignette
            drawRect(
                Brush.radialGradient(
                    colorStops = arrayOf(
                        0.38f to Color.Transparent,
                        0.76f to Color.Black.copy(alpha = 0.55f),
                        1f to Color.Black.copy(alpha = 0.92f),
                    ),
                    radius = size.maxDimension * 0.72f,
                ),
            )
        }
        Particles(tokens)
    }
}

/** One `.bg-glow` radial pool: an ellipse (rx% x ry%) centred at (cx%, cy%), colour -> transparent. */
private fun androidx.compose.ui.graphics.drawscope.DrawScope.bgPool(
    cx: Float, cy: Float, rxPct: Float, ryPct: Float, color: Color, stop: Float,
) {
    val rx = size.width * rxPct
    val ry = size.height * ryPct
    withTransform({
        translate(size.width * cx, size.height * cy)
        scale(1f, ry / rx, pivot = Offset.Zero)
    }) {
        drawCircle(
            brush = Brush.radialGradient(
                colorStops = arrayOf(0f to color, stop to Color.Transparent),
                radius = rx,
            ),
            radius = rx,
            center = Offset.Zero,
        )
    }
}

/** `Particles` — density-scaled drifting dots that twinkle. Frozen under the parity clock. */
@Composable
fun Particles(tokens: GadgetTokens, modifier: Modifier = Modifier) {
    val density = tokens.glow
    Canvas(modifier.fillMaxSize()) {
        val n = (22 * density).toInt().coerceIn(6, 40)
        for (i in 0 until n) {
            val seed = i * 137
            val x = (i * 61f % size.width)
            val twinkle = 0.35f + 0.35f * abs(sin(Clock.now() / 1400.0 + seed)).toFloat()
            drawCircle(
                color = Color.White.copy(alpha = twinkle * 0.5f),
                radius = 1.1f + (i % 3) * 0.4f,
                center = Offset(x, (i * 97f % size.height)),
            )
        }
    }
}
