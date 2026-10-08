package com.thegadget.app.shell

import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.CubicBezierEasing
import androidx.compose.animation.core.LinearEasing
import androidx.compose.animation.core.tween
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.thegadget.app.state.AppState
import com.thegadget.app.ui.theme.GadgetFonts
import com.thegadget.app.ui.theme.GadgetTokens
import kotlin.math.min

/** `--ease: cubic-bezier(0.16, 0.84, 0.24, 1)` */
val EaseCurve = CubicBezierEasing(0.16f, 0.84f, 0.24f, 1f)

/**
 * The boot overlay (`hub.css` `.boot`): two 40vmin rings at 1.5 px expanding 0.05→6.5 over 1.4 s on
 * `--ease` (the second delayed 0.14 s, at 55 % white) plus a 26vmin radial flash over 0.9 s. It is
 * pointer-transparent, exactly like `pointer-events: none`.
 */
@Composable
fun BootOverlay(modifier: Modifier = Modifier) {
    val p = remember { Animatable(0f) }
    val f = remember { Animatable(0f) }
    LaunchedEffect(Unit) {
        p.animateTo(1f, tween(1400, easing = EaseCurve))
    }
    LaunchedEffect(Unit) {
        kotlinx.coroutines.delay(140)
        f.animateTo(1f, tween(900, easing = EaseCurve))
    }
    Box(modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
        Canvas(Modifier.fillMaxSize()) {
            val vmin = min(size.width, size.height) / 100f
            // flash: 26vmin, radial white 0.5 -> transparent at 68%, scale 0.4 -> 2.6
            val fs = 26f * vmin * (0.4f + (2.6f - 0.4f) * f.value)
            val fa = when {
                f.value <= 0.22f -> f.value / 0.22f * 0.9f
                else -> 0.9f * (1f - (f.value - 0.22f) / 0.78f)
            }
            drawCircle(
                brush = Brush.radialGradient(
                    listOf(Color(0x80FFFFFF).copy(alpha = 0.5f * fa), Color.Transparent),
                    radius = fs / 2f,
                ),
                radius = fs / 2f,
                center = center,
            )
            // two rings
            for ((i, alpha) in listOf(0.85f, 0.55f).withIndex()) {
                val delay = if (i == 1) 0.1f else 0f // 0.14s of 1.4s
                val t = ((p.value - delay) / (1f - delay)).coerceIn(0f, 1f)
                if (t <= 0f) continue
                val scale = 0.05f + (6.5f - 0.05f) * t
                val op = if (t < 0.18f) t / 0.18f else 1f - (t - 0.18f) / 0.82f
                val d = 40f * vmin * scale
                drawCircle(
                    color = Color.White.copy(alpha = alpha * op.coerceIn(0f, 1f)),
                    radius = d / 2f,
                    center = center,
                    style = Stroke(width = 1.5f),
                )
            }
        }
    }
}

/**
 * `html[data-y2k="on"] .y2k-fx`: a 1 px-in-3 px black scanline raster crossed with a 2 px magenta /
 * 2 px cyan column raster, composited in overlay blend mode.
 */
@Composable
fun Y2kOverlay(modifier: Modifier = Modifier) {
    Canvas(modifier.fillMaxSize()) {
        // horizontal scanlines: rgba(0,0,0,0.2) 0-1px, transparent 1-3px
        var y = 0f
        while (y < size.height) {
            drawRect(color = Color(0x33000000), topLeft = Offset(0f, y), size = Size(size.width, 1f))
            y += 3f
        }
        // vertical columns: magenta 0-2px, cyan 2-4px
        var x = 0f
        while (x < size.width) {
            drawRect(color = Color(0x04FF005A), topLeft = Offset(x, 0f), size = Size(2f, size.height))
            drawRect(color = Color(0x0400C8FF), topLeft = Offset(x + 2f, 0f), size = Size(2f, size.height))
            x += 4f
        }
    }
}

/** The `ErrorBoundary` card — same kicker, title and two actions as the web. */
@Composable
fun CrashCard(message: String, tokens: GadgetTokens, onReload: () -> Unit, onReset: () -> Unit) {
    val card = RoundedCornerShape(20.dp)
    Box(Modifier.fillMaxSize().background(Color(0xCC000000)), contentAlignment = Alignment.Center) {
        Column(
            Modifier.padding(28.dp).fillMaxWidth().clip(card)
                .background(Brush.verticalGradient(listOf(Color(0xFF2B2B2A), Color(0xFF121211))))
                .border(1.dp, Color(0x33FFFFFF), card)
                .padding(22.dp),
            horizontalAlignment = Alignment.Start,
        ) {
            Text("the gadget", color = tokens.colors.dim, style = TextStyle(fontFamily = GadgetFonts.display, fontSize = 11.sp, letterSpacing = 3.sp))
            Spacer(Modifier.height(8.dp))
            Text("something broke", color = tokens.colors.text, style = TextStyle(fontFamily = GadgetFonts.display, fontWeight = FontWeight.Black, fontSize = 26.sp))
            Spacer(Modifier.height(10.dp))
            Text(message, color = tokens.colors.dim, style = TextStyle(fontFamily = GadgetFonts.body, fontSize = 13.sp))
            Spacer(Modifier.height(18.dp))
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                GlassButton("reload", tokens) { onReload() }
                GlassButton("reset data", tokens, variant = "ghost") { onReset() }
            }
        }
    }
}

/** The `.toast` capsule (`.toast--mini` variant used for confirmations). */
@Composable
fun ToastHost(app: AppState, tokens: GadgetTokens, modifier: Modifier = Modifier) {
    val toasts by app.toasts.collectAsState()
    if (toasts.isEmpty()) return
    Column(
        modifier.fillMaxWidth().padding(horizontal = 14.dp).padding(bottom = 120.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.spacedBy(10.dp),
    ) {
        toasts.forEach { msg ->
            val pill = RoundedCornerShape(999.dp)
            Box(
                Modifier.clip(pill)
                    .background(Brush.verticalGradient(listOf(Color(0xFF2B2B2A), Color(0xFF121211))))
                    .border(1.dp, Color(0x33FFFFFF), pill)
                    .padding(horizontal = 20.dp, vertical = 12.dp),
            ) {
                Text(msg, color = Color(0xFFE8E9E5), style = TextStyle(fontFamily = GadgetFonts.body, fontSize = 12.5.sp, lineHeight = 18.sp))
            }
        }
    }
}
