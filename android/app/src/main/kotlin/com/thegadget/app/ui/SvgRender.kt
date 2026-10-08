package com.thegadget.app.ui

import android.graphics.Typeface
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Rect
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.LinearGradientShader
import androidx.compose.ui.graphics.Matrix
import androidx.compose.ui.graphics.Paint
import androidx.compose.ui.graphics.PaintingStyle
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.PathFillType
import androidx.compose.ui.graphics.RadialGradientShader
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.StrokeJoin
import androidx.compose.ui.graphics.TileMode
import androidx.compose.ui.graphics.drawscope.DrawScope
import androidx.compose.ui.graphics.nativeCanvas
import com.thegadget.app.core.PathData
import com.thegadget.app.core.PathSegment
import com.thegadget.app.core.SvgFit
import com.thegadget.app.core.SvgTransform

/**
 * Draws the groups extracted by `verification/extract-svg.mjs` with the native Canvas — the same
 * geometry the browser renders, no hand-redrawn approximations.
 *
 * The catalogue already carries everything the browser would have *resolved* (inherited paint,
 * composed transforms, the nearest clip). What is left is SVG paint semantics, gradients, clips
 * and the viewBox fit, all of which are checked numerically in `verification/RunVectors.kt`
 * (blocks `paths`, `transforms`, `fit`, `svg catalogue`).
 *
 * Paints arrive as SVG paint strings and resolve like this:
 *   `#050505`          literal
 *   `none`             no paint
 *   `currentColor`     [SvgPalette.currentColor] — the glyph tint / icon colour of the context
 *   `var(--glyph-hole)`[SvgPalette.cssVar]
 *   `token:ink`        [SvgGroup.resolveToken] with the active [variant] (mascot yellow/mono)
 *   `url(#mascot.f)`   a [SvgGradient] from the same group, mapped onto the element's bounds
 *
 * Class-driven paint (`.wire path`, `.wire-ring`, `.const-lines` …) lives in CSS, not in the SVG;
 * the caller supplies it through [css] so the values stay in one place (`ui/theme/Css.kt`).
 */
class SvgPalette(
    val currentColor: Color,
    val cssVar: (String) -> Color? = { null },
    val typeface: (family: String, weight: Int) -> Typeface = { _, _ -> Typeface.DEFAULT },
)

/** CSS-side overrides for a class list; null fields fall through to the element's own paint. */
data class SvgCss(
    val fill: String? = null,
    val stroke: String? = null,
    val strokeWidth: Float? = null,
    val opacity: Float? = null,
)

/** The SVG 1.1 default: fill is black unless a class or the element says otherwise. */
private val DEFAULT_FILL = Color(0xFF000000)

fun DrawScope.renderSvgGroup(
    group: SvgGroup,
    palette: SvgPalette,
    variant: String = "yellow",
    dst: Rect = Rect(Offset.Zero, size),
    css: (String?) -> SvgCss? = { null },
) {
    val vb = group.viewBox.split(" ").mapNotNull { it.toFloatOrNull() }
    require(vb.size == 4) { "group ${group.name} has no usable viewBox" }
    val fit = SvgFit.fit(vb[2], vb[3], dst.width, dst.height, group.preserveAspectRatio)

    drawIntoCanvas { canvas ->
        canvas.save()
        val m = Matrix()
        m.translate(dst.left, dst.top)
        m.scale(fit.scaleX, fit.scaleY)
        m.translate(fit.tx / fit.scaleX, fit.ty / fit.scaleY)
        canvas.concat(m)
        for (el in group.elements) drawElement(group, el, palette, variant, fit.strokeScale, css, this)
        canvas.restore()
    }
}

private fun DrawScope.drawElement(
    group: SvgGroup,
    el: SvgElement,
    palette: SvgPalette,
    variant: String,
    strokeScale: Float,
    css: (String?) -> SvgCss?,
    scope: DrawScope,
) {
    val style = css(el.className)
    val path = el.toPath() ?: return
    val transform = el.transform?.let { SvgTransform.parse(it) }

    scope.drawIntoCanvas { canvas ->
        canvas.save()
        if (transform != null) {
            val m = Matrix()
            m.setValues(
                floatArrayOf(
                    transform.a, transform.c, transform.e,
                    transform.b, transform.d, transform.f,
                    0f, 0f, 1f,
                ),
            )
            canvas.concat(m)
        }

        val clipped = el.clipPath?.let { ref ->
            val id = ref.removePrefix("url(#").removeSuffix(")")
            group.clip(id)
        }
        val applyClip = clipped != null
        val clipBlock: () -> Unit = clip@{
            drawPainted(group, el, path, palette, variant, strokeScale, style, scope)
        }
        if (applyClip && clipped != null) {
            val clipPath = Path()
            for (c in clipped.elements) c.toPath()?.let { clipPath.addPath(it) }
            scope.clipPath(clipPath) { clipBlock() }
        } else {
            clipBlock()
        }
        canvas.restore()
    }
}

private fun DrawScope.drawPainted(
    group: SvgGroup,
    el: SvgElement,
    path: Path,
    palette: SvgPalette,
    variant: String,
    strokeScale: Float,
    style: SvgCss?,
    scope: DrawScope,
) {
    val fillSpec = style?.fill ?: el.fill
    val strokeSpec = style?.stroke ?: el.stroke
    val opacity = (style?.opacity ?: el.opacity?.toFloatOrNull() ?: 1f)

    if (el.kind == "text") {
        drawText(el, path, fillSpec, group, palette, variant, opacity, scope)
        return
    }

    if (fillSpec != "none") {
        val paint = Paint()
        paint.style = PaintingStyle.Fill
        paint.alpha = opacity * (el.fillOpacity?.toFloatOrNull() ?: 1f)
        applyFill(paint, fillSpec, group, palette, variant, path.getBounds())
        if (el.fillRule == "evenodd") path.fillType = PathFillType.EvenOdd
        // drawContext (not drawPath) so gradient shaders survive
        scope.drawContext.canvas.drawPath(path, paint)
    }

    val strokeW = (style?.strokeWidth?.toString() ?: el.strokeWidth ?: group.strokeWidth?.toString())?.toFloatOrNull()
    if (strokeSpec != null && strokeSpec != "none" && strokeW != null) {
        val paint = Paint()
        paint.style = PaintingStyle.Stroke
        paint.strokeWidth = strokeW * strokeScale
        paint.alpha = opacity * (el.strokeOpacity?.toFloatOrNull() ?: 1f)
        paint.strokeCap = cap(el.strokeLinecap ?: group.strokeLinecap)
        paint.strokeJoin = join(el.strokeLinejoin ?: group.strokeLinejoin)
        applyFill(paint, strokeSpec, group, palette, variant, path.getBounds())
        scope.drawContext.canvas.drawPath(path, paint)
    }
}

/* ------------------------------------------------------------------- paints */

private fun applyFill(paint: Paint, spec: String?, group: SvgGroup, palette: SvgPalette, variant: String, bounds: Rect) {
    when {
        spec == null || spec == "none" -> paint.color = if (paint.style == PaintingStyle.Fill) DEFAULT_FILL else Color.Transparent
        spec == "currentColor" -> paint.color = palette.currentColor
        spec.startsWith("#") -> paint.color = hex(spec)
        spec.startsWith("token:") -> {
            val t = spec.removePrefix("token:")
            paint.color = group.resolveToken(t, variant)?.let(::hex) ?: palette.currentColor
        }
        spec.startsWith("var(") -> {
            val name = spec.removePrefix("var(").removeSuffix(")").trim()
            paint.color = palette.cssVar(name) ?: palette.currentColor
        }
        spec.startsWith("url(#") -> {
            val id = spec.removePrefix("url(#").removeSuffix(")")
            paint.shader = group.gradient(id, variant)?.toShader(bounds)
            if (paint.shader == null) paint.color = palette.currentColor
        }
        else -> paint.color = palette.currentColor
    }
}

private fun hex(s: String): Color {
    val v = s.removePrefix("#")
    return when (v.length) {
        6 -> Color(0xFF000000L or v.toLong(16))
        8 -> Color(v.toLong(16))
        3 -> {
            val full = StringBuilder()
            for (ch in v) { full.append(ch); full.append(ch) }
            hex("#$full")
        }
        else -> Color.Black
    }
}

private fun SvgGradient.toShader(bounds: Rect): androidx.compose.ui.graphics.Shader? {
    val colors = stops.map { hex(it.color) }
    val stopsArr = stops.map { it.offset }.toFloatArray()
    if (colors.size < 2) return null
    val w = bounds.width
    val h = bounds.height
    return if (kind == "linear") {
        LinearGradientShader(
            colors = colors,
            colorStops = stopsArr,
            start = Offset(bounds.left + (x1 ?: 0f) * w, bounds.top + (y1 ?: 0f) * h),
            end = Offset(bounds.left + (x2 ?: 1f) * w, bounds.top + (y2 ?: 0f) * h),
            tileMode = TileMode.Clamp,
        )
    } else {
        // objectBoundingBox radial radius: the spec's normalised diagonal
        val radius = (r ?: 0.5f) * kotlin.math.sqrt((w * w + h * h) / 2f)
        RadialGradientShader(
            colors = colors,
            colorStops = stopsArr,
            center = Offset(bounds.left + (cx ?: 0.5f) * w, bounds.top + (cy ?: 0.5f) * h),
            radius = radius,
            tileMode = TileMode.Clamp,
        )
    }
}

private fun cap(s: String?) = when (s) {
    "round" -> StrokeCap.Round
    "square" -> StrokeCap.Square
    else -> StrokeCap.Butt
}

private fun join(s: String?) = when (s) {
    "round" -> StrokeJoin.Round
    "bevel" -> StrokeJoin.Bevel
    else -> StrokeJoin.Miter
}

/* ------------------------------------------------------------------- text */

private fun DrawScope.drawText(
    el: SvgElement,
    path: Path,
    fillSpec: String?,
    group: SvgGroup,
    palette: SvgPalette,
    variant: String,
    opacity: Float,
    scope: DrawScope,
) {
    val text = el.text ?: return
    scope.drawIntoCanvas { canvas ->
        val paint = android.graphics.Paint(android.graphics.Paint.ANTI_ALIAS_FLAG)
        val size = el.fontSize?.toFloatOrNull() ?: 12f
        paint.textSize = size
        paint.color = run {
            val p = Paint()
            applyFill(p, fillSpec, group, palette, variant, path.getBounds())
            val c = p.color
            android.graphics.Color.argb((opacity * 255).toInt(), (c.red * 255).toInt(), (c.green * 255).toInt(), (c.blue * 255).toInt())
        }
        paint.typeface = palette.typeface(el.fontFamily?.substringBefore(",")?.trim() ?: "Orbitron", 500)
        val x = el.x ?: 0f
        val y = el.y ?: 0f
        val adjusted = when (el.textAnchor) {
            "middle" -> x - paint.measureText(text) / 2f
            "end" -> x - paint.measureText(text)
            else -> x
        }
        canvas.nativeCanvas.drawText(text, adjusted, y, paint)
    }
}

/* ------------------------------------------------------------------- geometry */

/** Build the Compose [Path] for an element; null for kinds with no drawable geometry. */
fun SvgElement.toPath(): Path? {
    val p = Path()
    when (kind) {
        "path" -> {
            val d = d ?: return null
            val data = PathData.parse(d)
            for (sp in data.subPaths) {
                var started = false
                for (seg in sp.segments) when (seg) {
                    is PathSegment.Move -> { p.moveTo(seg.x, seg.y); started = true }
                    is PathSegment.Line -> { if (!started) { p.moveTo(0f, 0f); started = true }; p.lineTo(seg.x, seg.y) }
                    is PathSegment.Cubic -> { if (!started) { p.moveTo(0f, 0f); started = true }; p.cubicTo(seg.x1, seg.y1, seg.x2, seg.y2, seg.x, seg.y) }
                    is PathSegment.Close -> p.close()
                }
            }
        }
        "circle" -> p.addOval(Rect((cx ?: 0f) - (r ?: 0f), (cy ?: 0f) - (r ?: 0f), (cx ?: 0f) + (r ?: 0f), (cy ?: 0f) + (r ?: 0f)))
        "ellipse" -> p.addOval(Rect((cx ?: 0f) - (rx ?: 0f), (cy ?: 0f) - (ry ?: 0f), (cx ?: 0f) + (rx ?: 0f), (cy ?: 0f) + (ry ?: 0f)))
        "rect" -> {
            val x = x ?: 0f; val y = y ?: 0f; val w = width ?: 0f; val h = height ?: 0f
            val r = rx ?: ry ?: 0f
            if (r > 0f) p.addRoundRect(androidx.compose.ui.geometry.RoundRect(Rect(x, y, x + w, y + h), r, (ry ?: r)))
            else p.addRect(Rect(x, y, x + w, y + h))
        }
        "text" -> {
            // text is drawn via the native canvas; return a zero-size path carrying the origin
            p.moveTo(x ?: 0f, y ?: 0f)
        }
        else -> return null
    }
    return p
}

/** Convenience for the common full-scope case. */
fun DrawScope.renderIcon(name: String, palette: SvgPalette, css: (String?) -> SvgCss? = { null }) {
    renderSvgGroup(SvgPaths.require("icon.$name"), palette, css = css)
}

