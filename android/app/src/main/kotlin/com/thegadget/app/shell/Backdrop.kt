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
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.drawWithCache
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Rect
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.unit.dp
import com.thegadget.app.core.Clock
import com.thegadget.app.core.SVG_PALETTES
import com.thegadget.app.core.SVG_SOURCES
import com.thegadget.app.core.SvgPath
import com.thegadget.app.core.SvgTransform
import com.thegadget.app.core.SvgFit
import com.thegadget.app.ui.renderSvgGroup
import com.thegadget.app.ui.theme.CssWire
import com.thegadget.app.ui.theme.GadgetTokens
import com.thegadget.app.state.AppState
import kotlin.math.abs
import kotlin.math.sin

/**
 * The backdrop: `body::before` radial wash, `body::after` vignette, the drifting wireframe SVG, and
 * the `Particles` canvas. The wireframe uses the exact extracted path geometry, tinted and
 * alpha-blended exactly as `hub.css` `.wire` does.
 */
@Composable
fun Backdrop(tokens: GadgetTokens, modifier: Modifier = Modifier) {
    val group = SVG_SOURCES.require("wire.Wireframe")
    val drift = rememberInfiniteTransition(label = "wire")
    val dx by drift.animateFloat(
        initialValue = -6f, targetValue = 8f, label = "drift",
        animationSpec = infiniteRepeatable(tween(30_000, easing = LinearEasing), RepeatMode.Reverse),
    )
    Box(modifier.fillMaxSize()) {
        Canvas(Modifier.fillMaxSize()) {
            drawRect(Brush.radialGradient(listOf(Color(0xFF1B1D1B), Color(0xFF06070A)), radius = size.maxDimension))
            val fit = SvgFit.fit(group.viewBox, 0f, 0f, size.width, size.height)
            renderSvgGroup(group, CssWire.palette, "stroke", fit.dst, CssWire.css + mapOf("opacity" to 0.75f))
            // vignette
            drawRect(Brush.radialGradient(listOf(Color.Transparent, Color(0x66000000)), radius = size.maxDimension * 0.75f))
        }
        Particles(tokens)
    }
}

/** `Particles` — 14–34 dots, density-scaled, drifting and twinkling. Frozen under the parity clock. */
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
