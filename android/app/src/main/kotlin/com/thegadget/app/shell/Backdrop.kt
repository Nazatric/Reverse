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
import androidx.compose.ui.graphics.Color
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
    Box(modifier.fillMaxSize()) {
        Canvas(Modifier.fillMaxSize()) {
            drawRect(Brush.radialGradient(listOf(Color(0xFF1B1D1B), Color(0xFF06070A)), radius = size.maxDimension))
            // `.wire` fills the centered `.frame` (fx, fy, sw, sh) — not the whole screen.
            val m = com.thegadget.app.core.GadgetMetrics.compute(size.width, size.height)
            renderSvgGroup(
                group = group,
                palette = palette,
                variant = "stroke",
                dst = Rect(m.fx, m.fy, m.fx + m.sw, m.fy + m.sh),
                css = Css::wireElement,
            )
            drawRect(Brush.radialGradient(listOf(Color.Transparent, Color(0x66000000)), radius = size.maxDimension * 0.75f))
        }
        Particles(tokens)
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
