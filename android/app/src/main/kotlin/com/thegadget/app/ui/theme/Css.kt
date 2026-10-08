package com.thegadget.app.ui.theme

import androidx.compose.ui.graphics.Color
import com.thegadget.app.ui.SvgCss

/**
 * The class-driven paint the SVG renderer cannot know — copied verbatim from `src/styles`.
 * `base.css` lines 33-35 carry the glyph palette; `hub.css` lines 28-30 carry the wireframe
 * strokes, including `vector-effect: non-scaling-stroke` (the 0.8px stays 0.8px on screen no
 * matter how the 800×800 viewBox is stretched, hence [SvgCss.nonScalingStroke]).
 */
object Css {

    /* base.css */
    val GLYPH = Color(0xFF383937)
    val GLYPH_HOLE = Color(0xFF9A9B98)
    val GLYPH_DOT = Color(0xFFD2D3CF)

    fun cssVar(name: String): Color? = when (name) {
        "--glyph" -> GLYPH
        "--glyph-hole" -> GLYPH_HOLE
        "--glyph-dot" -> GLYPH_DOT
        else -> null
    }

    /** hub.css `.wire path` / `.wire-ring` / `.wire-ring--2`. */
    private val WIRE_PATH = SvgCss(
        fill = "none",
        stroke = "#dee2da",
        strokeWidth = 0.8f,
        opacity = 0.3f * 0.75f, // stroke rgba(…,0.3) under .wire { opacity: .75 }
        nonScalingStroke = true,
    )
    private val WIRE_RING = SvgCss(
        fill = "none",
        stroke = "#e8eee4",
        strokeWidth = 0.8f,
        opacity = 0.16f * 0.75f,
        nonScalingStroke = true,
    )
    private val WIRE_RING_2 = SvgCss(
        fill = "none",
        stroke = "#d0d9cd",
        strokeWidth = 0.8f,
        opacity = 0.1f * 0.75f,
        nonScalingStroke = true,
    )

    /** hub.css `.glyph` + per-node overrides (171-181). */
    private fun glyph(color: String, opacity: Float) = SvgCss(fill = color, opacity = opacity)

    fun forClass(className: String?): SvgCss? {
        if (className == null) return null
        val classes = className.split(' ')
        return when {
            "wire-ring--2" in classes -> WIRE_RING_2
            "wire-ring" in classes -> WIRE_RING
            className.contains("wire") && className.contains("path") -> WIRE_PATH
            else -> null
        }
    }

    /** Class styles for the wireframe group's elements (the group's paths carry no paint attrs). */
    fun wireElement(className: String?): SvgCss =
        if (className?.contains("wire-ring") == true) {
            if (className.contains("wire-ring--2")) WIRE_RING_2 else WIRE_RING
        } else {
            WIRE_PATH
        }
}
