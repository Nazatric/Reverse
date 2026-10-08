package com.thegadget.app.core

import kotlin.math.min

/**
 * Stage metrics — a 1:1 port of `src/utils/metrics.ts`.
 *
 * The web app authors the whole hub in a 736x736 reference composition and multiplies by `--u`
 * (one reference pixel in real pixels), so proportions stay exact at any size. On tall screens the
 * stage grows vertically instead of shrinking, and orbs get a size boost so the centre logo stays
 * dominant. Every value here is produced by the same arithmetic as the TypeScript source.
 */
data class GadgetMetrics(
    val w: Float,
    val h: Float,
    /** stage width / height in px */
    val sw: Float,
    val sh: Float,
    /** stage offset in px */
    val fx: Float,
    val fy: Float,
    /** one reference pixel, in px */
    val u: Float,
    val portrait: Boolean,
) {
    /** The web app treats the stage as a 736-unit canvas. */
    fun stageX(percent: Float): Float = percent / 100f * sw
    fun stageY(percent: Float): Float = percent / 100f * sh

    companion object {
        const val REFERENCE = 736f
        const val MAX_STAGE = 1000f

        fun compute(w: Float, h: Float): GadgetMetrics {
            val portrait = h > w * 1.02f
            var sw: Float
            var sh: Float
            if (portrait) {
                sw = min(w, MAX_STAGE)
                val maxRatio = if (w >= 700f) 1.3f else 2.05f
                sh = min(h, sw * maxRatio)
            } else {
                sh = min(min(h, w), MAX_STAGE)
                sw = sh
            }
            val minDim = min(w, h)
            val boost = if (minDim < 520f) 1.2f else if (minDim < 800f) 1.08f else 1f
            return GadgetMetrics(
                w = w,
                h = h,
                sw = sw,
                sh = sh,
                fx = (w - sw) / 2f,
                fy = (h - sh) / 2f,
                u = (sw / REFERENCE) * boost,
                portrait = portrait,
            )
        }
    }
}

/** Orb diameter on screen: `max(d * u * scale, floor)` — matches `.node`/`.hub-mascot` CSS. */
fun orbSize(d: Float, u: Float, scale: Float, floor: Float): Float =
    maxOf(d * u * scale, floor)
