package com.thegadget.app.shell

import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Rect
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.StrokeJoin
import androidx.compose.ui.graphics.drawscope.DrawScope
import androidx.compose.ui.graphics.drawscope.clipPath
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.drawscope.rotate
import androidx.compose.ui.graphics.drawscope.scale
import androidx.compose.ui.graphics.drawscope.translate
import kotlin.math.PI
import kotlin.math.cos
import kotlin.math.min
import kotlin.math.sin

/**
 * The hub glyphs, ported point-for-point from `src/components/hub/Glyphs.tsx` (140×140 viewBox).
 * Each is expressed in that coordinate space and scaled to the target box, so the shapes match the
 * reference instead of being approximated.
 */
private const val VIEW = 140f

fun DrawScope.drawGlyph(
    id: String,
    box: Rect,
    ink: Color = Color(0xFF383937),
    hole: Color = Color(0xFF9A9B98),
    dot: Color = Color(0xFFD2D3CF),
    avatar: androidx.compose.ui.graphics.ImageBitmap? = null,
) {
    val s = box.width / VIEW
    translate(left = box.left, top = box.top) {
        scale(s, s, pivot = Offset.Zero) {
            when (id) {
                "music" -> musicGlyph(ink)
                "games" -> gamesGlyph(ink, hole)
                "homies" -> homiesGlyph(ink)
                "config" -> configGlyph(ink, dot)
                "play" -> playGlyph(ink)
                "note" -> noteGlyph(ink)
                "star" -> starGlyph(ink)
                "account" -> {
                    if (avatar != null) {
                        clipCircle(Offset(70f, 70f), 62f) {
                            drawImage(
                                image = avatar,
                                srcOffset = androidx.compose.ui.unit.IntOffset.Zero,
                                srcSize = androidx.compose.ui.unit.IntSize(avatar.width, avatar.height),
                                dstOffset = androidx.compose.ui.unit.IntOffset(8, 8),
                                dstSize = androidx.compose.ui.unit.IntSize(124, 124),
                            )
                        }
                    } else {
                        mascotFace(Offset(70f, 70f), 62f, yellow = false)
                    }
                }
                else -> drawCircle(ink, radius = 34f, center = Offset(70f, 70f), style = Stroke(9f))
            }
        }
    }
}

private val musicPath = listOf(
    listOf(20f to 42f, 50f to 28f, 86f to 28f, 117f to 38f),
    listOf(18f to 54f, 50f to 40f, 87f to 40f, 119f to 50f),
    listOf(18f to 66f, 48f to 53f, 84f to 53f, 113f to 62f),
    listOf(19f to 78f, 45f to 66f, 76f to 66f, 99f to 71f),
)

private fun DrawScope.musicGlyph(ink: Color) {
    musicPath.forEach { pts ->
        val p = Path().apply {
            moveTo(pts[0].first, pts[0].second)
            cubicTo(
                pts[1].first, pts[1].second - 6f, pts[2].first, pts[2].second - 6f, pts[3].first, pts[3].second,
            )
        }
        drawPath(p, ink, style = Stroke(6.5f, cap = StrokeCap.Round))
    }
    drawLine(ink, Offset(108f, 18f), Offset(100f, 88f), strokeWidth = 7.5f, cap = StrokeCap.Round)
    rotate(-12f, Offset(94f, 97f)) {
        drawOval(
            ink,
            topLeft = Offset(79f, 84.5f),
            size = Size(30f, 25f),
            style = Stroke(7f, cap = StrokeCap.Round),
        )
    }
    // the paired notes
    drawLine(ink, Offset(40f, 86f), Offset(38f, 113f), strokeWidth = 5.5f, cap = StrokeCap.Round)
    noteHead(ink, 32.5f, 111f)
    drawLine(ink, Offset(64f, 86f), Offset(62f, 113f), strokeWidth = 5.5f, cap = StrokeCap.Round)
    noteHead(ink, 56.5f, 111f)
}

private fun DrawScope.noteHead(ink: Color, cx: Float, cy: Float) {
    val p = Path().apply {
        moveTo(cx, cy - 11f)
        cubicTo(cx + 7f, cy - 4f, cx + 8f, cy - 1f, cx + 8f, cy + 1f)
        cubicTo(cx + 8f, cy + 5f, cx + 2f, cy + 7f, cx - 2f, cy + 3f)
        cubicTo(cx - 6f, cy - 1f, cx - 6f, cy - 6f, cx, cy - 11f)
        close()
    }
    drawPath(p, ink, style = Stroke(5.5f, cap = StrokeCap.Round, join = StrokeJoin.Round))
}

private fun DrawScope.gamesGlyph(ink: Color, hole: Color) {
    val body = Path().apply {
        moveTo(38f, 44f)
        cubicTo(24f, 44f, 16f, 54f, 13f, 68f)
        lineTo(6f, 101f)
        cubicTo(3f, 116f, 15f, 124f, 26f, 115f)
        lineTo(51f, 95f)
        cubicTo(57f, 90f, 63f, 88f, 70f, 88f)
        cubicTo(77f, 88f, 83f, 90f, 89f, 95f)
        lineTo(114f, 115f)
        cubicTo(125f, 124f, 137f, 116f, 134f, 101f)
        lineTo(127f, 68f)
        cubicTo(124f, 54f, 116f, 44f, 102f, 44f)
        cubicTo(88f, 44f, 82f, 52f, 70f, 52f)
        cubicTo(58f, 52f, 52f, 44f, 38f, 44f)
        close()
    }
    drawPath(body, ink)
    drawLine(hole, Offset(38f, 58f), Offset(38f, 84f), strokeWidth = 7.5f, cap = StrokeCap.Round)
    drawLine(hole, Offset(25f, 71f), Offset(51f, 71f), strokeWidth = 7.5f, cap = StrokeCap.Round)
    drawCircle(hole, 5.5f, Offset(98f, 66f))
    drawCircle(hole, 5.5f, Offset(112f, 79f))
}

private fun DrawScope.homiesGlyph(ink: Color) {
    val faded = ink.copy(alpha = 0.85f)
    drawCircle(ink, 17f, Offset(70f, 42f))
    drawPath(
        Path().apply {
            moveTo(36f, 100f)
            cubicTo(36f, 82f, 50f, 69f, 70f, 69f)
            cubicTo(90f, 69f, 104f, 82f, 104f, 100f)
            lineTo(104f, 106f); lineTo(36f, 106f); close()
        },
        ink,
    )
    drawCircle(faded, 13.5f, Offset(34f, 54f))
    drawPath(
        Path().apply {
            moveTo(6f, 103f)
            cubicTo(6f, 88f, 16f, 77f, 32f, 77f)
            cubicTo(37f, 77f, 43f, 80f, 48f, 85f)
            cubicTo(42f, 92f, 38f, 100f, 38f, 109f)
            lineTo(7f, 109f); close()
        },
        faded,
    )
    drawCircle(faded, 13.5f, Offset(106f, 54f))
    drawPath(
        Path().apply {
            moveTo(134f, 103f)
            cubicTo(134f, 88f, 124f, 77f, 108f, 77f)
            cubicTo(103f, 77f, 97f, 80f, 92f, 85f)
            cubicTo(98f, 92f, 102f, 100f, 102f, 109f)
            lineTo(133f, 109f); close()
        },
        faded,
    )
    drawPath(
        Path().apply {
            moveTo(14f, 114f)
            cubicTo(19f, 104f, 30f, 99f, 46f, 99f)
            lineTo(94f, 99f)
            cubicTo(110f, 99f, 121f, 104f, 126f, 114f)
            lineTo(126f, 118f); lineTo(14f, 118f); close()
        },
        ink,
    )
}

/** The gear teeth are generated with the same trigonometry as the SVG path builder. */
private fun DrawScope.configGlyph(ink: Color, dot: Color) {
    val big = gearPath(cx = 56f, cy = 82f, ro = 40f, ri = 32f, teeth = 9, hole = 15f)
    val small = gearPath(cx = 106f, cy = 38f, ro = 24f, ri = 19f, teeth = 8, hole = 8f)
    drawPath(big, ink)
    drawPath(small, ink)
    drawCircle(dot, 8f, Offset(56f, 82f))
}

private fun gearPath(cx: Float, cy: Float, ro: Float, ri: Float, teeth: Int, hole: Float): Path {
    val step = (PI * 2) / teeth
    val path = Path()
    for (i in 0 until teeth) {
        val a = i * step
        val pts = listOf(
            ri to (a - 0.31 * step),
            ro to (a - 0.17 * step),
            ro to (a + 0.17 * step),
            ri to (a + 0.31 * step),
        )
        pts.forEachIndexed { k, (r, t) ->
            val x = cx + cos(t).toFloat() * r
            val y = cy + sin(t).toFloat() * r
            if (i == 0 && k == 0) path.moveTo(x, y) else path.lineTo(x, y)
        }
    }
    path.close()
    path.addOval(Rect(cx - hole, cy - hole, cx + hole, cy + hole))
    path.fillType = androidx.compose.ui.graphics.PathFillType.EvenOdd
    return path
}

private fun DrawScope.playGlyph(ink: Color) {
    drawPath(
        Path().apply {
            moveTo(48f, 32f); lineTo(112f, 70f); lineTo(48f, 108f); close()
        },
        ink,
    )
}

private fun DrawScope.noteGlyph(ink: Color) {
    drawPath(
        Path().apply {
            moveTo(54f, 106f); lineTo(54f, 30f); lineTo(104f, 20f); lineTo(104f, 94f)
        },
        ink,
        style = Stroke(7f, cap = StrokeCap.Round, join = StrokeJoin.Round),
    )
    rotate(-16f, Offset(44f, 107f)) { drawOval(ink, topLeft = Offset(31f, 97f), size = Size(26f, 20f)) }
    rotate(-16f, Offset(94f, 95f)) { drawOval(ink, topLeft = Offset(81f, 85f), size = Size(26f, 20f)) }
}

private fun DrawScope.starGlyph(ink: Color) {
    drawPath(
        Path().apply {
            moveTo(70f, 20f); lineTo(87f, 54f); lineTo(125f, 60f); lineTo(98f, 87f)
            lineTo(104f, 125f); lineTo(70f, 107f); lineTo(36f, 125f); lineTo(42f, 87f)
            lineTo(15f, 60f); lineTo(53f, 54f); close()
        },
        ink,
    )
}

/* ------------------------------------------------------------------ mascot face */

/**
 * `MascotFace.tsx` — glass bezel, domed face with rim shading, wedge brows, the lens flare and
 * the chrome tooth grille, all in a 200×200 viewBox.
 */
fun DrawScope.mascotFace(center: Offset, radius: Float, yellow: Boolean, bare: Boolean = true, flare: Float = 1f) {
    val s = radius / 100f
    translate(left = center.x, top = center.y) {
        scale(s, s, pivot = Offset.Zero) {
            val stops = if (yellow) {
                arrayOf(0f to Color(0xFFFFFDB4), 0.24f to Color(0xFFEEF43D), 0.58f to Color(0xFFC2CF29), 0.86f to Color(0xFF7F8C16), 1f to Color(0xFF434D0B))
            } else {
                arrayOf(0f to Color(0xFFFCFCFB), 0.24f to Color(0xFFDCDDDA), 0.58f to Color(0xFFA1A29F), 0.86f to Color(0xFF5F605E), 1f to Color(0xFF2A2B2A))
            }
            val ink = if (yellow) Color(0xFF14150E) else Color(0xFF161716)

            val faceClip = Path().apply { addOval(Rect(-85f, -85f, 85f, 85f)) }
            clipPath(faceClip) {
                drawCircle(
                    brush = Brush.radialGradient(colorStops = stops, center = Offset(-12f, -48f), radius = 168f),
                    radius = 85f,
                )
                drawOval(Color.White.copy(alpha = 0.10f), topLeft = Offset(-74f, 62f), size = Size(148f, 56f))
                drawOval(Color.White.copy(alpha = 0.26f), topLeft = Offset(-54f, -12f), size = Size(124f, 68f))
                rotate(-18f, Offset(-32f, -66f)) {
                    drawOval(Color.White.copy(alpha = 0.42f), topLeft = Offset(-59f, -75f), size = Size(54f, 18f))
                }
                // brows
                drawPath(
                    Path().apply {
                        moveTo(-66f, -18f)
                        cubicTo(-54f, -44f, -24f, -44f, 0f, -6f)
                        lineTo(-4f, 0f)
                        cubicTo(-24f, -26f, -46f, -26f, -60f, -10f)
                        close()
                    },
                    ink,
                )
                drawPath(
                    Path().apply {
                        moveTo(66f, -18f)
                        cubicTo(54f, -44f, 24f, -44f, 0f, -6f)
                        lineTo(4f, 0f)
                        cubicTo(24f, -26f, 46f, -26f, 60f, -10f)
                        close()
                    },
                    ink,
                )
                // eyes
                rotate(12f, Offset(-34f, 2f)) {
                    drawOval(ink, topLeft = Offset(-39.5f, -8f), size = Size(11f, 20f))
                }
                rotate(-12f, Offset(34f, 2f)) {
                    drawOval(ink, topLeft = Offset(28.5f, -8f), size = Size(11f, 20f))
                }
                // lens flare (fades out while blinking)
                translate(left = 0f, top = -10f) {
                    drawCircle(
                        brush = Brush.radialGradient(
                            colorStops = arrayOf(0f to Color.White, 0.4f to Color.White.copy(alpha = 0.4f), 1f to Color.Transparent),
                            center = Offset.Zero,
                            radius = 24f,
                        ),
                        radius = 24f,
                        alpha = flare,
                    )
                    drawPath(
                        Path().apply {
                            moveTo(-30f, 0f); quadraticBezierTo(0f, 0f, 0f, -30f); quadraticBezierTo(0f, 0f, 30f, 0f)
                            quadraticBezierTo(0f, 0f, 0f, 30f); quadraticBezierTo(0f, 0f, -30f, 0f); close()
                        },
                        Color.White.copy(alpha = 0.95f * flare),
                    )
                    drawCircle(Color.White, 4.2f, alpha = flare)
                }
                // mouth: dark cavity, chrome grille, teeth bars clipped to the lip curve
                drawPath(
                    Path().apply {
                        moveTo(-72f, 16f); quadraticBezierTo(0f, 40f, 72f, 16f)
                        lineTo(68f, 36f)
                        cubicTo(58f, 76f, -58f, 76f, -68f, 36f)
                        close()
                    },
                    Color(0xFF0A0A07),
                )
                val lip = Path().apply {
                    moveTo(-62f, 24f); quadraticBezierTo(0f, 46f, 62f, 24f)
                    lineTo(59f, 38f)
                    cubicTo(50f, 72f, -50f, 72f, -41f, 38f)
                    close()
                }
                clipPath(lip) {
                    drawPath(
                        lip,
                        Brush.verticalGradient(
                            listOf(Color(0xFFFFFFFF), Color(0xFFE7E8E5), Color(0xFFA3A4A0), Color(0xFF5D5E5A)),
                            startY = 24f,
                            endY = 76f,
                        ),
                    )
                    val teeth = Path().apply { addRect(Rect(-62f, 12f, 62f, 82f)) }
                    clipPath(teeth) {
                        for (x in listOf(-47f, -31f, -15f, 0f, 15f, 31f, 47f)) {
                            drawLine(Color(0xFF15160F), Offset(x, 12f), Offset(x, 82f), strokeWidth = 3.2f)
                        }
                    }
                    drawOval(Color.Black.copy(alpha = 0.28f), topLeft = Offset(-64f, 64f), size = Size(128f, 24f))
                }
                drawPath(
                    Path().apply { moveTo(-60f, 26f); quadraticBezierTo(0f, 48f, 60f, 26f) },
                    Color.White.copy(alpha = 0.6f),
                    style = Stroke(1.5f),
                )
                drawPath(
                    Path().apply { moveTo(-72f, 16f); quadraticBezierTo(0f, 40f, 72f, 16f) },
                    Color(0xFF0A0A07),
                    style = Stroke(5f),
                )
                drawCircle(Color.Black.copy(alpha = 0.5f), 84f, style = Stroke(3.5f))
                drawCircle(Color.White.copy(alpha = 0.14f), 80f, style = Stroke(1.2f))
            }
            if (!bare) {
                drawCircle(Color(0xFFECEDE9), 99f)
                drawCircle(Color(0xFF0B0B0B), 96f)
                drawCircle(
                    Brush.verticalGradient(
                        listOf(Color(0xFF9A9B98), Color(0xFF2B2C2B), Color(0xFF575856)),
                        startY = -96f,
                        endY = 96f,
                    ),
                    93f,
                )
                drawCircle(Color(0xFF050505), 88.5f)
            }
        }
    }
}

/** Blink: eyes squash to a line for ~120ms on a slow irregular cycle (mascot.css). */
fun blinkAmount(tMs: Long): Float {
    val period = 4200L
    val phase = tMs % period
    return if (phase < 120) 1f - (phase / 120f) * 2f else if (phase < 240) (phase - 120f) / 120f * 2f - 1f else 1f
}

internal fun DrawScope.clipCircle(center: Offset, radius: Float, block: DrawScope.() -> Unit) {
    clipPath(Path().apply { addOval(Rect(center.x - radius, center.y - radius, center.x + radius, center.y + radius)) }) {
        block()
    }
}

internal fun DrawScope.drawRoundRectShim() {
    // (placeholder removed — kept for source compatibility of older call sites)
    drawRoundRect(Color.Transparent, size = Size(0f, 0f), cornerRadius = CornerRadius.Zero)
}

internal fun min2(a: Float, b: Float) = min(a, b)
