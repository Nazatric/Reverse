package com.thegadget.app.core

/**
 * Where a group's viewBox lands inside a destination rect — the `preserveAspectRatio` rule.
 *
 * The web app stretches the wireframe with `preserveAspectRatio="none"` (its 800×800 mesh fills the
 * whole hub stage) but meets every other group (`xMidYMid meet`, the SVG default). The renderer
 * builds its canvas transform from this; the numbers are checked by the `fit` block of
 * `verification/RunVectors.kt`.
 */
data class SvgFit(val scaleX: Float, val scaleY: Float, val tx: Float, val ty: Float) {
    fun x(vbX: Float): Float = tx + vbX * scaleX
    fun y(vbY: Float): Float = ty + vbY * scaleY

    /** Uniform scale for stroke widths and font sizes (meet: the shared scale; none: the mean). */
    val strokeScale: Float get() = if (scaleX == scaleY) scaleX else (scaleX + scaleY) / 2f

    fun asMatrix(): Matrix2 = Matrix2(scaleX, 0f, 0f, scaleY, tx, ty)

    companion object {

        /**
         * @param vbWidth / [vbHeight] the group's viewBox size
         * @param dstWidth / [dstHeight] the destination box in pixels
         * @param preserveAspectRatio `"none"` stretches; anything else (including null) is xMidYMid meet
         */
        fun fit(
            vbWidth: Float,
            vbHeight: Float,
            dstWidth: Float,
            dstHeight: Float,
            preserveAspectRatio: String? = null,
        ): SvgFit {
            if (vbWidth <= 0f || vbHeight <= 0f || dstWidth <= 0f || dstHeight <= 0f) {
                return SvgFit(1f, 1f, 0f, 0f)
            }
            if (preserveAspectRatio == "none") {
                return SvgFit(dstWidth / vbWidth, dstHeight / vbHeight, 0f, 0f)
            }
            val s = minOf(dstWidth / vbWidth, dstHeight / vbHeight)
            return SvgFit(s, s, (dstWidth - vbWidth * s) / 2f, (dstHeight - vbHeight * s) / 2f)
        }
    }
}
