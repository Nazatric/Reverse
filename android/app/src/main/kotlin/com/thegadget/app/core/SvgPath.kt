package com.thegadget.app.core

import kotlin.math.abs
import kotlin.math.acos
import kotlin.math.ceil
import kotlin.math.cos
import kotlin.math.hypot
import kotlin.math.max
import kotlin.math.min
import kotlin.math.sin
import kotlin.math.sqrt
import kotlin.math.tan

/**
 * SVG path parsing — port of the subset of the SVG 1.1 path grammar the app's artwork uses
 * (M/L/H/V/C/S/Q/T/A/Z, absolute and relative).
 *
 * This exists because the native graphics must draw *the same curves* the browser draws. The
 * generated `SvgPaths.kt` carries the raw `d` strings from the web sources; this parser turns them
 * into segments, and [PathData.sampleAtFraction] reproduces `SVGGeometryElement.getPointAtLength`
 * so the two can be compared numerically in the parity tests.
 *
 * Verification: `verification/gen-path-vectors.mjs` samples all 70 of the app's paths in Chromium
 * (`getTotalLength` / `getPointAtLength`) and the `paths` block of `verification/RunVectors.kt`
 * compares this parser against them — worst observed deviation 0.0093 user units.
 */
sealed interface PathSegment {
    data class Move(val x: Float, val y: Float) : PathSegment
    data class Line(val x: Float, val y: Float) : PathSegment
    data class Cubic(
        val x1: Float, val y1: Float,
        val x2: Float, val y2: Float,
        val x: Float, val y: Float,
    ) : PathSegment
    data object Close : PathSegment
}

/** One subpath: an optional move plus the segments that follow it. */
data class SubPath(val segments: List<PathSegment>)

class SvgPathException(message: String) : Exception(message)

/**
 * A parsed path with arc-length helpers. Construction never fails on the app's real data; a
 * malformed string raises [SvgPathException] so a bad asset surfaces loudly in tests instead of
 * drawing nothing.
 */
class PathData(val subPaths: List<SubPath>) {

    /** Dense polyline approximation used for measuring and sampling. */
    private val flat: List<FloatArray> by lazy { subPaths.map { flattenSubPath(it, 0.25f) } }

    val totalLength: Float by lazy {
        flat.sumOf { poly -> polyLength(poly).toDouble() }.toFloat()
    }

    /** Same contract as `path.getPointAtLength(len * fraction)`: even *arc length* sampling. */
    fun sampleAtFraction(fraction: Float): Pair<Float, Float> = sampleAtLength(totalLength * fraction.coerceIn(0f, 1f))

    fun sampleAtLength(distance: Float): Pair<Float, Float> {
        var remaining = distance
        for (poly in flat) {
            val len = polyLength(poly)
            if (remaining <= len) {
                var d = remaining
                var i = 0
                while (i + 3 < poly.size) {
                    val segLen = hypot(poly[i + 2] - poly[i], poly[i + 3] - poly[i + 1])
                    if (d <= segLen || i + 5 >= poly.size) {
                        val t = if (segLen <= 0f) 0f else d / segLen
                        return (poly[i] + (poly[i + 2] - poly[i]) * t) to (poly[i + 1] + (poly[i + 3] - poly[i + 1]) * t)
                    }
                    d -= segLen
                    i += 2
                }
                return poly[0] to poly[1]
            }
            remaining -= len
        }
        return flat.lastOrNull()?.let { it[it.size - 2] to it[it.size - 1] } ?: (0f to 0f)
    }

    companion object {
        fun parse(d: String): PathData = Builder().apply { parseInto(d) }.build()
    }

    private class Builder {
        private val subPaths = mutableListOf<SubPath>()
        private val current = mutableListOf<PathSegment>()
        private var startX = 0f
        private var startY = 0f
        private var x = 0f
        private var y = 0f
        private var lastCubic: Pair<Float, Float>? = null
        private var lastQuad: Pair<Float, Float>? = null

        fun build(): PathData {
            flush()
            return PathData(subPaths.toList())
        }

        private fun flush() {
            if (current.isNotEmpty()) {
                subPaths += SubPath(current.toList())
                current.clear()
            }
        }

        fun parseInto(d: String) {
            val tokens = tokenize(d)
            var i = 0
            var command = ' '
            while (i < tokens.size) {
                val t = tokens[i]
                if (t.isCommand) {
                    command = t.c
                    i++
                    if (command == 'Z' || command == 'z') {
                        current += PathSegment.Close
                        x = startX; y = startY
                        lastCubic = null; lastQuad = null
                        continue
                    }
                } else if (command == ' ') {
                    throw SvgPathException("path data starts with a number: $d")
                }
                val rel = command.isLowerCase()
                val dx = if (rel) x else 0f
                val dy = if (rel) y else 0f
                fun n(k: Int): Float {
                    if (i + k >= tokens.size) {
                        throw SvgPathException("'$command' ran out of parameters in path: $d")
                    }
                    return tokens[i + k].value
                }
                // A numeric token keeps the previous command, so repeated parameter sets
                // (`L1 2 3 4`, `c… …`) are consumed by simply looping with `command` unchanged.
                when (command.uppercaseChar()) {
                    'M' -> {
                        val nx = n(0) + dx
                        val ny = n(1) + dy
                        flush()
                        x = nx; y = ny; startX = nx; startY = ny
                        current += PathSegment.Move(nx, ny)
                        i += 2
                        // Any further pairs after a moveto are implicit linetos.
                        command = if (rel) 'l' else 'L'
                        lastCubic = null; lastQuad = null
                    }
                    'L' -> { val px = n(0) + dx; val py = n(1) + dy; x = px; y = py; current += PathSegment.Line(px, py); i += 2; lastCubic = null; lastQuad = null }
                    'H' -> { val px = n(0) + dx; x = px; current += PathSegment.Line(px, y); i += 1; lastCubic = null; lastQuad = null }
                    'V' -> { val py = n(0) + dy; y = py; current += PathSegment.Line(x, py); i += 1; lastCubic = null; lastQuad = null }
                    'C' -> {
                        val c1x = n(0) + dx; val c1y = n(1) + dy
                        val c2x = n(2) + dx; val c2y = n(3) + dy
                        val px = n(4) + dx; val py = n(5) + dy
                        current += PathSegment.Cubic(c1x, c1y, c2x, c2y, px, py)
                        lastCubic = c2x to c2y; lastQuad = null
                        x = px; y = py; i += 6
                    }
                    'S' -> {
                        val rx = lastCubic?.let { 2 * x - it.first } ?: x
                        val ry = lastCubic?.let { 2 * y - it.second } ?: y
                        val c2x = n(0) + dx; val c2y = n(1) + dy
                        val px = n(2) + dx; val py = n(3) + dy
                        current += PathSegment.Cubic(rx, ry, c2x, c2y, px, py)
                        lastCubic = c2x to c2y; lastQuad = null
                        x = px; y = py; i += 4
                    }
                    'Q' -> {
                        val qx = n(0) + dx; val qy = n(1) + dy
                        val px = n(2) + dx; val py = n(3) + dy
                        current += quadToCubic(x, y, qx, qy, px, py)
                        lastQuad = qx to qy; lastCubic = null
                        x = px; y = py; i += 4
                    }
                    'T' -> {
                        val qx = lastQuad?.let { 2 * x - it.first } ?: x
                        val qy = lastQuad?.let { 2 * y - it.second } ?: y
                        val px = n(0) + dx; val py = n(1) + dy
                        current += quadToCubic(x, y, qx, qy, px, py)
                        lastQuad = qx to qy; lastCubic = null
                        x = px; y = py; i += 2
                    }
                    'A' -> {
                        val rx = abs(n(0)); val ry = abs(n(1))
                        val rot = n(2); val largeArc = n(3) != 0f; val sweep = n(4) != 0f
                        val px = n(5) + dx; val py = n(6) + dy
                        current += arcToCubics(x, y, rx, ry, rot, largeArc, sweep, px, py)
                        x = px; y = py; i += 7
                        lastCubic = null; lastQuad = null
                    }
                    else -> throw SvgPathException("unsupported command '$command' in path: $d")
                }
            }
        }
    }
}

private class Token(val isCommand: Boolean, val c: Char = ' ', val value: Float = 0f)

/**
 * Number/command tokenizer.
 *
 * Two details make this more than `split(" ")`, and both show up in the app's own artwork:
 *  - numbers may run together: `M-3.5.25` is `M`, `-3.5`, `.25`, and a second `.` starts a new
 *    number rather than being swallowed;
 *  - arc flags are single digits, so `a2 2 0 012-2` is rx=2 ry=2 rot=0 large=0 sweep=1 x=2 y=-2.
 *    Reading that as `012`, `-2` silently drops a corner from every rounded icon.
 */
private fun tokenize(d: String): List<Token> {
    val out = mutableListOf<Token>()
    var i = 0
    var command = ' '
    var arg = 0
    while (i < d.length) {
        val ch = d[i]
        when {
            ch.isWhitespace() || ch == ',' -> i++
            ch.isLetter() -> {
                command = ch
                arg = 0
                out += Token(true, c = ch)
                i++
            }
            else -> {
                val start = i
                if (command.uppercaseChar() == 'A' && (arg % 7 == 3 || arg % 7 == 4) && (ch == '0' || ch == '1')) {
                    out += Token(false, value = (ch - '0').toFloat())
                    i++
                } else {
                    if (ch == '+' || ch == '-') i++
                    var seenDot = false
                    while (i < d.length) {
                        val c = d[i]
                        if (c.isDigit()) { i++; continue }
                        if (c == '.' && !seenDot) { seenDot = true; i++; continue }
                        break
                    }
                    if (i < d.length && (d[i] == 'e' || d[i] == 'E')) {
                        val before = i
                        i++
                        if (i < d.length && (d[i] == '+' || d[i] == '-')) i++
                        val digits = i
                        while (i < d.length && d[i].isDigit()) i++
                        if (i == digits) i = before // a trailing 'e' with no exponent is not one
                    }
                    val raw = d.substring(start, i)
                    val v = raw.toFloatOrNull() ?: throw SvgPathException("bad number '$raw' in path: $d")
                    out += Token(false, value = v)
                }
                if (start == i) throw SvgPathException("unexpected '$ch' in path: $d")
                arg++
            }
        }
    }
    return out
}

private fun quadToCubic(x0: Float, y0: Float, qx: Float, qy: Float, x1: Float, y1: Float): PathSegment.Cubic {
    val c1x = x0 + (2f / 3f) * (qx - x0)
    val c1y = y0 + (2f / 3f) * (qy - y0)
    val c2x = x1 + (2f / 3f) * (qx - x1)
    val c2y = y1 + (2f / 3f) * (qy - y1)
    return PathSegment.Cubic(c1x, c1y, c2x, c2y, x1, y1)
}

/**
 * Endpoint-parameterised elliptical arc → cubic Béziers (SVG 1.1 §F.6.5). This is the code that
 * makes the icons' rounded corners (`a2 2 0 012-2`) land in the same place the browser puts them.
 */
private fun arcToCubics(
    x0: Float, y0: Float,
    rxIn: Float, ryIn: Float,
    rotationDeg: Float,
    largeArc: Boolean, sweep: Boolean,
    x1: Float, y1: Float,
): List<PathSegment> {
    if (rxIn == 0f || ryIn == 0f) return listOf(PathSegment.Line(x1, y1))
    var rx = rxIn
    var ry = ryIn
    val phi = rotationDeg * (Math.PI.toFloat() / 180f)
    val cosPhi = cos(phi)
    val sinPhi = sin(phi)
    // Step 1: compute (x1', y1')
    val dx2 = (x0 - x1) / 2f
    val dy2 = (y0 - y1) / 2f
    val x1p = cosPhi * dx2 + sinPhi * dy2
    val y1p = -sinPhi * dx2 + cosPhi * dy2
    // Correct out-of-range radii
    val lambda = (x1p * x1p) / (rx * rx) + (y1p * y1p) / (ry * ry)
    if (lambda > 1f) {
        val s = sqrt(lambda)
        rx *= s
        ry *= s
    }
    val rx2 = rx * rx
    val ry2 = ry * ry
    val x1p2 = x1p * x1p
    val y1p2 = y1p * y1p
    // Step 2: compute (cx', cy')
    var num = rx2 * ry2 - rx2 * y1p2 - ry2 * x1p2
    val den = rx2 * y1p2 + ry2 * x1p2
    if (num < 0f) num = 0f
    val coef = if (den == 0f) 0f else sqrt(num / den) * (if (largeArc == sweep) -1f else 1f)
    val cxp = coef * (rx * y1p / ry)
    val cyp = coef * -(ry * x1p / rx)
    // Step 3: centre point
    val cx = cosPhi * cxp - sinPhi * cyp + (x0 + x1) / 2f
    val cy = sinPhi * cxp + cosPhi * cyp + (y0 + y1) / 2f
    // Step 4: angles
    val theta1 = angle(1f, 0f, (x1p - cxp) / rx, (y1p - cyp) / ry)
    var delta = angle((x1p - cxp) / rx, (y1p - cyp) / ry, (-x1p - cxp) / rx, (-y1p - cyp) / ry)
    if (!sweep && delta > 0f) delta -= 2f * Math.PI.toFloat()
    if (sweep && delta < 0f) delta += 2f * Math.PI.toFloat()
    // Step 5: split into ≤90° cubic segments
    val count = max(1, ceil(abs(delta) / (Math.PI.toFloat() / 2f)).toInt())
    val step = delta / count
    val out = mutableListOf<PathSegment>()
    var t = theta1
    var px = x0
    var py = y0
    repeat(count) {
        val next = t + step
        val alpha = (4f / 3f) * tan(step / 4f)
        val cosT = cos(t); val sinT = sin(t)
        val cosN = cos(next); val sinN = sin(next)
        val p1 = ellipsePoint(cx, cy, rx, ry, cosPhi, sinPhi, cosT, sinT)
        val p2 = ellipsePoint(cx, cy, rx, ry, cosPhi, sinPhi, cosN, sinN)
        val d1 = ellipseDerivative(rx, ry, cosPhi, sinPhi, cosT, sinT)
        val d2 = ellipseDerivative(rx, ry, cosPhi, sinPhi, cosN, sinN)
        val c1x = p1.first + alpha * d1.first
        val c1y = p1.second + alpha * d1.second
        val c2x = p2.first - alpha * d2.first
        val c2y = p2.second - alpha * d2.second
        out += PathSegment.Cubic(c1x, c1y, c2x, c2y, p2.first, p2.second)
        px = p2.first; py = p2.second
        t = next
    }
    // Snap the final point to the requested endpoint, exactly like a browser does.
    if (out.isNotEmpty()) {
        val last = out.removeAt(out.size - 1) as PathSegment.Cubic
        out += last.copy(x = x1, y = y1)
    }
    return out
}

private fun ellipsePoint(cx: Float, cy: Float, rx: Float, ry: Float, cosPhi: Float, sinPhi: Float, cosT: Float, sinT: Float): Pair<Float, Float> {
    val x = rx * cosT
    val y = ry * sinT
    return (cx + cosPhi * x - sinPhi * y) to (cy + sinPhi * x + cosPhi * y)
}

private fun ellipseDerivative(rx: Float, ry: Float, cosPhi: Float, sinPhi: Float, cosT: Float, sinT: Float): Pair<Float, Float> {
    val dx = -rx * sinT
    val dy = ry * cosT
    return (cosPhi * dx - sinPhi * dy) to (sinPhi * dx + cosPhi * dy)
}

private fun angle(ux: Float, uy: Float, vx: Float, vy: Float): Float {
    val dot = ux * vx + uy * vy
    val len = sqrt((ux * ux + uy * uy) * (vx * vx + vy * vy))
    var a = if (len == 0f) 0f else acos((dot / len).coerceIn(-1f, 1f))
    if (ux * vy - uy * vx < 0f) a = -a
    return a
}

/** Sub-polyline for one subpath: `[x, y, x, y, …]`, starting at the move point. */
private fun flattenSubPath(sub: SubPath, tolerance: Float): FloatArray {
    val out = ArrayList<Float>(64)
    var cx = 0f
    var cy = 0f
    var sx = 0f
    var sy = 0f
    for (seg in sub.segments) {
        when (seg) {
            is PathSegment.Move -> {
                if (out.isEmpty()) { out += seg.x; out += seg.y }
                cx = seg.x; cy = seg.y; sx = seg.x; sy = seg.y
            }
            is PathSegment.Line -> {
                out += cx; out += cy; out += seg.x; out += seg.y
                cx = seg.x; cy = seg.y
            }
            is PathSegment.Cubic -> {
                val steps = cubicSteps(cx, cy, seg, tolerance)
                var prevX = cx
                var prevY = cy
                for (i in 1..steps) {
                    val t = i.toFloat() / steps
                    val p = cubicAt(cx, cy, seg, t)
                    out += prevX; out += prevY; out += p.first; out += p.second
                    prevX = p.first; prevY = p.second
                }
                cx = seg.x; cy = seg.y
            }
            PathSegment.Close -> {
                out += cx; out += cy; out += sx; out += sy
                cx = sx; cy = sy
            }
        }
    }
    if (out.isEmpty()) out += 0f else if (out.size == 1) out += 0f
    return out.toFloatArray()
}

private fun cubicSteps(x0: Float, y0: Float, c: PathSegment.Cubic, tolerance: Float): Int {
    // Estimate by the control polygon's length, which upper-bounds the curve.
    val poly = abs(c.x1 - x0) + abs(c.y1 - y0) + abs(c.x2 - c.x1) + abs(c.y2 - c.y1) + abs(c.x - c.x2) + abs(c.y - c.y2)
    return min(96, max(6, (poly / max(tolerance, 0.05f)).toInt()))
}

private fun cubicAt(x0: Float, y0: Float, c: PathSegment.Cubic, t: Float): Pair<Float, Float> {
    val mt = 1f - t
    val a = mt * mt * mt
    val b = 3f * mt * mt * t
    val cc = 3f * mt * t * t
    val d = t * t * t
    return (a * x0 + b * c.x1 + cc * c.x2 + d * c.x) to (a * y0 + b * c.y1 + cc * c.y2 + d * c.y)
}

private fun polyLength(poly: FloatArray): Float {
    var len = 0f
    var i = 0
    while (i + 3 < poly.size) {
        len += hypot(poly[i + 2] - poly[i], poly[i + 3] - poly[i + 1])
        i += 2
    }
    return len
}
