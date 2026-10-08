package com.thegadget.app.shell

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Rect
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Matrix
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.PathEffect
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.StrokeJoin
import androidx.compose.ui.graphics.drawscope.DrawScope
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.drawscope.clipPath
import androidx.compose.ui.graphics.drawscope.rotate
import androidx.compose.ui.graphics.drawscope.scale
import androidx.compose.ui.graphics.drawscope.translate
import androidx.compose.ui.graphics.drawscope.withTransform
import androidx.compose.ui.text.TextMeasurer
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.drawText
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.rememberTextMeasurer
import androidx.compose.ui.unit.sp
import com.thegadget.app.core.GadgetMetrics
import com.thegadget.app.core.HubNodeSpec
import com.thegadget.app.core.HubNodes
import com.thegadget.app.ui.theme.Exo2
import com.thegadget.app.ui.theme.GadgetType
import com.thegadget.app.ui.theme.GadgetTokens
import com.thegadget.app.ui.theme.Orbitron
import com.thegadget.app.ui.theme.orbSize
import kotlin.math.PI
import kotlin.math.atan2
import kotlin.math.cos
import kotlin.math.hypot
import kotlin.math.min
import kotlin.math.sin
import kotlin.math.tan

/**
 * The hub: five orbs on chains, drawn entirely on Canvas from the values measured off the running
 * web app (see parity/out/web/<viewport>/geometry.json).
 *
 *  - orb box:      `max(d * u * orbScale, 54)` px, the hub floor at 96 — core/HubLayout.kt
 *  - orb paint:    `radial-gradient(70% 38% at 50% 10%, …)` highlight over
 *                  `radial-gradient(circle at 50% 56%, #bdbdba 0%, … #0d0d0d 100%)`,
 *                  plus five inset rings measured from the computed box-shadow
 *  - chains:       a 64×40 chain-link tile repeated along each branch, rotated by the branch
 *                  angle, swaying ±0.7–0.9° over 9s with a −1.9s stagger per branch
 *  - labels:       Orbitron 900, `max(12px, 18u * labelScale)`, skewX(−12°) + scaleX(1.1)
 */

/** Everything the hub needs that the layout maths already computed. */
data class HubGeometry(
    val metrics: GadgetMetrics,
    val hubSize: Float,
    val hubCenter: Offset,
    val nodes: List<Pair<HubNodeSpec, Rect>>,
)

fun hubGeometry(m: GadgetMetrics, tokens: GadgetTokens): HubGeometry {
    val hubSize = orbSize(HubNodes.HUB.d, m.u, tokens.orbScale * tokens.hubScale, floor = 96f)
    return HubGeometry(
        metrics = m,
        hubSize = hubSize,
        hubCenter = Offset(m.stageX(HubNodes.HUB.x), m.stageY(HubNodes.HUB.y)),
        nodes = HubNodes.NODES.map { n ->
            val size = orbSize(n.d, m.u, tokens.orbScale, floor = 54f)
            n to Rect(
                left = m.stageX(n.x) - size / 2f,
                top = m.stageY(n.y) - size / 2f,
                right = m.stageX(n.x) + size / 2f,
                bottom = m.stageY(n.y) + size / 2f,
            )
        },
    )
}

@Composable
fun Hub(
    tokens: GadgetTokens,
    geometry: HubGeometry,
    pluginNodes: List<HubNodeSpec>,
    onNodeTap: (HubNodeSpec, Offset, Float) -> Unit,
    modifier: Modifier = Modifier,
    swayPhase: Float = 0f,
) {
    val measurer = rememberTextMeasurer()
    val labelStyle = remember(tokens.labelScale) {
        TextStyle(
            fontFamily = Orbitron,
            fontWeight = FontWeight.Black,
            letterSpacing = 0.5.sp,
        )
    }

    BoxWithConstraints(modifier.fillMaxSize()) {
        Canvas(Modifier.fillMaxSize()) {
            drawChains(geometry, tokens, swayPhase)
            mascotFace(geometry.hubCenter, geometry.hubSize / 2f, yellow = true)
            for ((spec, box) in geometry.nodes) drawNode(spec, box, tokens, measurer, labelStyle)
            for ((i, spec) in pluginNodes.withIndex()) {
                val size = orbSize(spec.d, geometry.metrics.u, tokens.orbScale, floor = 54f)
                val box = Rect(
                    left = geometry.metrics.stageX(spec.x) - size / 2f,
                    top = geometry.metrics.stageY(spec.y) - size / 2f,
                    right = geometry.metrics.stageX(spec.x) + size / 2f,
                    bottom = geometry.metrics.stageY(spec.y) + size / 2f,
                )
                drawNode(spec, box, tokens, measurer, labelStyle, pluginIndex = i)
            }
        }
    }
}

/* ------------------------------------------------------------------------ orbs */

fun DrawScope.drawOrb(center: Offset, size: Float) {
    val radius = size / 2f
    // Base chrome: radial-gradient(circle at 50% 56%, #bdbdba, #a3a4a1 20%, #7a7b78 42%,
    // #4a4b49 62%, #232423 80%, #0d0d0d 100%)
    drawCircle(
        brush = Brush.radialGradient(
            colorStops = arrayOf(
                0f to Color(0xFFBDBDBA),
                0.20f to Color(0xFFA3A4A1),
                0.42f to Color(0xFF7A7B78),
                0.62f to Color(0xFF4A4B49),
                0.80f to Color(0xFF232423),
                1f to Color(0xFF0D0D0D),
            ),
            center = Offset(center.x, center.y + size * 0.06f),
            radius = radius,
        ),
        radius = radius,
        center = center,
    )
    // Top highlight: radial-gradient(70% 38% at 50% 10%, rgba(255,255,255,.64), .14 55%, transparent)
    clipPath(Path().apply { addOval(Rect(center, radius)) }) {
        withTransform({
            scale(scaleX = 1f, scaleY = 0.38f, pivot = Offset(center.x, center.y - size * 0.40f))
        }) {
            drawCircle(
                brush = Brush.radialGradient(
                    colorStops = arrayOf(
                        0f to Color(0xFFFFF_FFF).copy(alpha = 0.64f),
                        0.55f to Color.White.copy(alpha = 0.14f),
                        1f to Color.Transparent,
                    ),
                    center = Offset(center.x, center.y - size * 0.40f),
                    radius = size * 0.70f,
                ),
                radius = size * 0.70f,
                center = Offset(center.x, center.y - size * 0.40f),
            )
        }
    }
    // Inset rings, in the order the computed box-shadow lists them.
    drawCircle(Color(0xF5F0F1ED), radius = radius - 0.75f, center = center, style = Stroke(1.5f))
    drawCircle(Color(0x9E080808), radius = radius - 2.5f, center = center, style = Stroke(5f))
    drawCircle(Color(0x33FFFFFF), radius = radius - 3f, center = center, style = Stroke(6f))
    // rgba(255,255,255,.4) 0 3px 12px 2px inset — a soft inner light from the top
    drawCircle(
        brush = Brush.verticalGradient(
            colors = listOf(Color.White.copy(alpha = 0.18f), Color.Transparent),
            startY = center.y - radius,
            endY = center.y + radius * 0.4f,
        ),
        radius = radius - 1f,
        center = center,
        style = Stroke(size * 0.06f),
    )
    // rgba(0,0,0,.65) 0 -14px 22px inset — the underside shadow
    drawCircle(
        brush = Brush.verticalGradient(
            colors = listOf(Color.Transparent, Color.Black.copy(alpha = 0.45f)),
            startY = center.y,
            endY = center.y + radius,
        ),
        radius = radius - 1f,
        center = center,
        style = Stroke(size * 0.16f),
    )
}

private fun DrawScope.drawNode(
    spec: HubNodeSpec,
    box: Rect,
    tokens: GadgetTokens,
    measurer: TextMeasurer,
    labelStyle: TextStyle,
    pluginIndex: Int = -1,
) {
    val size = box.width
    drawOrb(box.center, size)
    // The glyph sits at 46% of the orb, tinted --glyph with the hole colour showing through.
    val glyphSize = size * 0.46f
    drawGlyph(
        spec.iconOrDefault(),
        Rect(
            left = box.center.x - glyphSize / 2f,
            top = box.center.y - glyphSize / 2f,
            right = box.center.x + glyphSize / 2f,
            bottom = box.center.y + glyphSize / 2f,
        ),
        ink = Color(0xFF383937),
        hole = Color(0xFF9A9B98),
        dot = Color(0xFFD2D3CF),
    )
    // Label below the orb: ly is the offset in reference px from the orb bottom.
    if (spec.label.isNotEmpty()) {
        val u = size / (spec.d * tokens.orbScale) // recover u for this node
        val ly = spec.ly * u
        val fontSize = GadgetType.hubLabelSize(u, tokens.labelScale)
        val measured = measurer.measure(spec.label, labelStyle.copy(fontSize = fontSize.sp))
        val cx = box.center.x
        val cy = box.bottom + ly + measured.size.height / 2f
        withTransform({
            // transform: scaleX(1.1) skewX(-12deg)
            val m = Matrix()
            m.values[0] = 1.1f
            m.values[4] = tan(-12.0 * PI / 180.0).toFloat()
            transform(m)
        }) {
            drawText(
                textMeasurer = measurer,
                text = spec.label,
                style = labelStyle.copy(fontSize = fontSize.sp, color = Color(0xFFF6F6F3)),
                topLeft = Offset(cx - measured.size.width / 2f, cy - measured.size.height / 2f),
            )
        }
    }
}

private fun HubNodeSpec.iconOrDefault(): String = when (id.key) {
    "music", "games", "homies", "config", "account" -> id.key
    else -> icon ?: "star"
}

/* ----------------------------------------------------------------------- chains */

private const val TILE_W = 64f
private const val TILE_H = 40f

/**
 * One chain per node: a div of width `hypot(dx, dy)`, height `max(26, 40u)`, rotated to the
 * branch angle, with the chain-link tile repeated along it and a ±0.7–0.9° sway.
 */
private fun DrawScope.drawChains(geometry: HubGeometry, tokens: GadgetTokens, phase: Float) {
    val hub = geometry.hubCenter
    val thickness = maxOf(26f, 40f * geometry.metrics.u * tokens.chainScale)
    val entries = geometry.nodes
    for ((i, entry) in entries.withIndex()) {
        val branchAngle = stageAngle(geometry, entry.first)
        // Sway: the stylesheet animates each branch with a −1.9s stagger over 9s.
        val sway = if (tokens.chainScale == 0f) 0f else {
            val t = ((phase - i * (1900f / 9000f)) % 1f + 1f) % 1f
            val wave = sin(t * 2f * PI.toFloat())
            (-0.7f + wave * 0.8f) * (PI.toFloat() / 180f)
        }
        val length = hypot(
            geometry.metrics.stageX(entry.first.x) - hub.x,
            geometry.metrics.stageY(entry.first.y) - hub.y,
        )
        rotate(degrees = branchAngle * 180f / PI.toFloat() + sway * 180f / PI.toFloat(), pivot = hub) {
            val x0 = hub.x
            val y0 = hub.y - thickness / 2f
            var x = 0f
            while (x < length) {
                val w = min(TILE_W, length - x)
                translate(left = x0 + x, top = y0) {
                    clipRect(0f, 0f, w, thickness) { drawChainTile(thickness) }
                }
                x += TILE_W
            }
        }
    }
}

private inline fun DrawScope.clipRect(l: Float, t: Float, w: Float, h: Float, block: DrawScope.() -> Unit) {
    clipPath(Path().apply { addRect(Rect(l, t, l + w, t + h)) }) { block() }
}

/** The 64×40 tile from Chains.tsx, drawn with the same geometry and gradients. */
private fun DrawScope.drawChainTile(thickness: Float) {
    val s = thickness / TILE_H // the tile is authored for a 40px-tall chain
    withTransform({ scale(s, s, pivot = Offset.Zero) }) {
        // <ellipse cx=34 cy=26 rx=34 ry=10 fill=url(#s)> — the contact shadow
        drawOval(
            brush = Brush.radialGradient(
                colorStops = arrayOf(0f to Color.Black.copy(alpha = 0.55f), 1f to Color.Transparent),
                center = Offset(34f, 26f),
                radius = 34f,
            ),
            topLeft = Offset(0f, 16f),
            size = Size(68f, 20f),
        )
        // flat link
        drawOval(
            brush = Brush.linearGradient(
                colorStops = arrayOf(
                    0f to Color(0xFFFFFFFF),
                    0.22f to Color(0xFFC7C8C5),
                    0.5f to Color(0xFF3B3C3A),
                    0.76f to Color(0xFF9B9C99),
                    1f to Color(0xFF1C1D1C),
                ),
                start = Offset(-0.5f, 7f),
                end = Offset(42.5f, 33f),
            ),
            topLeft = Offset(-0.5f, 7f),
            size = Size(43f, 26f),
        )
        drawOval(
            color = Color(0xFF0B0B0B),
            topLeft = Offset(-0.5f, 7f),
            size = Size(43f, 26f),
            style = Stroke(2.2f),
        )
        drawOval(color = Color(0xFF060606), topLeft = Offset(8f, 14.6f), size = Size(26f, 10.8f))
        drawOval(color = Color.White.copy(alpha = 0.62f), topLeft = Offset(5f, 11.5f), size = Size(16f, 4f))
        // edge-on link
        drawRoundRect(
            brush = Brush.linearGradient(
                colorStops = arrayOf(
                    0f to Color(0xFFF2F2EF),
                    0.3f to Color(0xFF7C7D7A),
                    0.6f to Color(0xFF171817),
                    1f to Color(0xFF8D8E8B),
                ),
                start = Offset(45.5f, 5f),
                end = Offset(60.5f, 35f),
            ),
            topLeft = Offset(45.5f, 5f),
            size = Size(15f, 30f),
            cornerRadius = androidx.compose.ui.geometry.CornerRadius(7.5f),
        )
        drawRoundRect(
            color = Color(0xFF0B0B0B),
            topLeft = Offset(45.5f, 5f),
            size = Size(15f, 30f),
            cornerRadius = androidx.compose.ui.geometry.CornerRadius(7.5f),
            style = Stroke(2f),
        )
        drawRoundRect(
            color = Color(0xFF050505),
            topLeft = Offset(50.5f, 10f),
            size = Size(5f, 20f),
            cornerRadius = androidx.compose.ui.geometry.CornerRadius(2.5f),
        )
        drawRoundRect(
            color = Color.White.copy(alpha = 0.6f),
            topLeft = Offset(47.6f, 9f),
            size = Size(2.4f, 13f),
            cornerRadius = androidx.compose.ui.geometry.CornerRadius(1.2f),
        )
    }
}

private fun stageAngle(geometry: HubGeometry, n: HubNodeSpec): Float {
    val dx = geometry.metrics.stageX(n.x) - geometry.hubCenter.x
    val dy = geometry.metrics.stageY(n.y) - geometry.hubCenter.y
    return atan2(dy, dx)
}

internal fun cosDeg(deg: Float) = cos(deg * PI.toFloat() / 180f)
internal fun sinDeg(deg: Float) = sin(deg * PI.toFloat() / 180f)
