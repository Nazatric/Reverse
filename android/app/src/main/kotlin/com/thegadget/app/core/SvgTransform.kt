package com.thegadget.app.core

import kotlin.math.cos
import kotlin.math.sin
import kotlin.math.tan

/**
 * SVG transform lists (`transform="rotate(-18 68 34) translate(100 90)"`) as 2-D affine matrices.
 *
 * The generated catalogue composes ancestor + own transforms into one string per element; the
 * renderer concats that matrix onto the canvas. The numbers must match the browser's own
 * `transform.baseVal.consolidate()`, and `verification/RunVectors.kt` (block `transforms`) checks
 * exactly that against matrices sampled in Chromium by `verification/gen-path-vectors.mjs`.
 *
 * Convention mirrors SVG's `matrix(a b c d e f)`:
 *   x' = a·x + c·y + e
 *   y' = b·x + d·y + f
 */
data class Matrix2(
    val a: Float,
    val b: Float,
    val c: Float,
    val d: Float,
    val e: Float,
    val f: Float,
) {
    /**
     * Matrix product `this · o` — the transform that applies [o] first, then `this`.
     * SVG lists compose left-to-right with this product: \`"A B"\` maps p to A·(B·p).
     */
    fun times(o: Matrix2): Matrix2 = Matrix2(
        a = a * o.a + c * o.b,
        b = b * o.a + d * o.b,
        c = a * o.c + c * o.d,
        d = b * o.c + d * o.d,
        e = a * o.e + c * o.f + e,
        f = b * o.e + d * o.f + f,
    )

    fun map(x: Float, y: Float): Pair<Float, Float> = (a * x + c * y + e) to (b * x + d * y + f)

    companion object {
        val IDENTITY = Matrix2(1f, 0f, 0f, 1f, 0f, 0f)
        fun translate(tx: Float, ty: Float) = Matrix2(1f, 0f, 0f, 1f, tx, ty)
        fun scale(sx: Float, sy: Float) = Matrix2(sx, 0f, 0f, sy, 0f, 0f)
        fun rotate(deg: Float): Matrix2 {
            val t = Math.toRadians(deg.toDouble())
            return Matrix2(cos(t).toFloat(), sin(t).toFloat(), -sin(t).toFloat(), cos(t).toFloat(), 0f, 0f)
        }

        /** rotate(a cx cy) = translate(cx cy) · rotate(a) · translate(−cx −cy). */
        fun rotate(deg: Float, cx: Float, cy: Float): Matrix2 =
            translate(cx, cy).times(rotate(deg)).times(translate(-cx, -cy))

        fun skewX(deg: Float): Matrix2 {
            val t = tan(Math.toRadians(deg.toDouble())).toFloat()
            return Matrix2(1f, 0f, t, 1f, 0f, 0f)
        }

        fun skewY(deg: Float): Matrix2 {
            val t = tan(Math.toRadians(deg.toDouble())).toFloat()
            return Matrix2(1f, t, 0f, 1f, 0f, 0f)
        }
    }
}

class SvgTransformException(message: String) : Exception(message)

object SvgTransform {

    /** Compose a full transform list the way SVG does: leftmost function applied outermost. */
    fun parse(list: String): Matrix2 = parts(list).fold(Matrix2.IDENTITY) { acc, m -> acc.times(m) }

    fun parts(list: String): List<Matrix2> {
        val out = mutableListOf<Matrix2>()
        var i = 0
        while (i < list.length) {
            while (i < list.length && (list[i].isWhitespace() || list[i] == ',')) i++
            if (i >= list.length) break
            val nameStart = i
            while (i < list.length && list[i].isLetter()) i++
            val name = list.substring(nameStart, i)
            while (i < list.length && (list[i].isWhitespace() || list[i] == ',')) i++
            if (i >= list.length || list[i] != '(') throw SvgTransformException("expected '(' after '$name' in: $list")
            i++
            val args = mutableListOf<Float>()
            while (true) {
                while (i < list.length && (list[i].isWhitespace() || list[i] == ',')) i++
                if (i < list.length && list[i] == ')') { i++; break }
                val s = i
                if (i < list.length && (list[i] == '+' || list[i] == '-')) i++
                while (i < list.length && (list[i].isDigit() || list[i] == '.')) i++
                if (i < list.length && (list[i] == 'e' || list[i] == 'E')) {
                    i++
                    if (i < list.length && (list[i] == '+' || list[i] == '-')) i++
                    while (i < list.length && list[i].isDigit()) i++
                }
                val raw = list.substring(s, i)
                args += raw.toFloatOrNull() ?: throw SvgTransformException("bad number '$raw' in: $list")
                if (s == i) throw SvgTransformException("unexpected '${list[i]}' in: $list")
            }
            out += when (name) {
                "matrix" -> {
                    require(args, 6, name, list)
                    Matrix2(args[0], args[1], args[2], args[3], args[4], args[5])
                }
                "translate" -> when (args.size) {
                    1 -> Matrix2.translate(args[0], 0f)
                    2 -> Matrix2.translate(args[0], args[1])
                    else -> throw SvgTransformException("translate takes 1-2 args in: $list")
                }
                "scale" -> when (args.size) {
                    1 -> Matrix2.scale(args[0], args[0])
                    2 -> Matrix2.scale(args[0], args[1])
                    else -> throw SvgTransformException("scale takes 1-2 args in: $list")
                }
                "rotate" -> when (args.size) {
                    1 -> Matrix2.rotate(args[0])
                    3 -> Matrix2.rotate(args[0], args[1], args[2])
                    else -> throw SvgTransformException("rotate takes 1 or 3 args in: $list")
                }
                "skewX" -> { require(args, 1, name, list); Matrix2.skewX(args[0]) }
                "skewY" -> { require(args, 1, name, list); Matrix2.skewY(args[0]) }
                else -> throw SvgTransformException("unsupported transform '$name' in: $list")
            }
        }
        return out
    }

    private fun require(args: List<Float>, n: Int, name: String, list: String) {
        if (args.size != n) throw SvgTransformException("$name takes $n args in: $list")
    }
}
