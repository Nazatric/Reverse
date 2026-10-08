package com.thegadget.app.ui

// GENERATED FILE — do not edit by hand.
// Produced by verification/extract-svg.mjs from the web sources, so every curve the native app
// draws is byte-identical to the SVG the browser renders. Re-generate with:
//     node verification/extract-svg.mjs
// Freshness is enforced by `node verification/extract-svg.mjs --check`; geometry is checked
// against the browser by the `paths` block of verification/RunVectors.kt.
//
// sources: src/components/{ui/Icons.tsx, hub/Glyphs.tsx, hub/Wireframe.tsx, hub/TopBar.tsx,
//          hub/Chains.tsx, ui/Art.tsx, MascotFace.tsx}
// digest: 9693031e14e4ab3f
// 47 groups, 132 elements, 9 gradients, 2 clip paths

/** One gradient stop. `offset` is 0..1 along the gradient vector/radius. */
data class SvgStop(val offset: Float, val color: String, val opacity: Float = 1f)

/**
 * A gradient from a <defs> block. Coordinates are objectBoundingBox fractions (SVG's default
 * gradientUnits), i.e. `cx = 0.44f` means 44% across the painted shape's bounds — which is what
 * the browser resolves `cx="44%"` to. [variant] is non-null only for the mascot face, whose
 * paints depend on its yellow/mono variant.
 */
data class SvgGradient(
    val id: String,
    val kind: String,
    val x1: Float? = null,
    val y1: Float? = null,
    val x2: Float? = null,
    val y2: Float? = null,
    val cx: Float? = null,
    val cy: Float? = null,
    val r: Float? = null,
    val variant: String? = null,
    val stops: List<SvgStop> = emptyList(),
)

/** A <clipPath>: the shapes whose union clips the elements referencing it by id. */
data class SvgClip(val id: String, val elements: List<SvgElement>)

/**
 * One SVG primitive with every attribute the browser would have resolved for it: ancestor paint
 * inherited, transforms composed, the nearest clip remembered, class names accumulated.
 *
 * Paint values are SVG paint strings the renderer resolves against the gadget palette:
 * `currentColor`, `#050505`, `var(--glyph-hole)`, `url(#mascot.f)` (a gradient in the same
 * group), `token:ink` (a variant-dependent colour from [SvgGroup.tokens]) or `none`.
 */
data class SvgElement(
    val kind: String,
    val d: String? = null,
    val cx: Float? = null,
    val cy: Float? = null,
    val r: Float? = null,
    val rx: Float? = null,
    val ry: Float? = null,
    val x: Float? = null,
    val y: Float? = null,
    val x1: Float? = null,
    val y1: Float? = null,
    val x2: Float? = null,
    val y2: Float? = null,
    val width: Float? = null,
    val height: Float? = null,
    val points: String? = null,
    val fill: String? = null,
    val fillOpacity: String? = null,
    val fillRule: String? = null,
    val stroke: String? = null,
    val strokeWidth: String? = null,
    val strokeOpacity: String? = null,
    val strokeLinecap: String? = null,
    val strokeLinejoin: String? = null,
    val opacity: String? = null,
    /** Composed ancestor+self transform list, in SVG application order. */
    val transform: String? = null,
    /** Id of the [SvgClip] in the same group that clips this element, if any. */
    val clipPath: String? = null,
    /** Accumulated class names — the CSS-animated layers (`mascot-eyes`, `wire-ring`) key off these. */
    val className: String? = null,
    /** For kind = "text": the literal, or `{name}` when the web app interpolates a value. */
    val text: String? = null,
    val textAnchor: String? = null,
    val fontSize: String? = null,
    val fontFamily: String? = null,
)

/** A whole <svg>: primitives, definitions and the presentation defaults from the root element. */
data class SvgGroup(
    val name: String,
    val viewBox: String,
    val elements: List<SvgElement>,
    /** `none` for the wireframe (stretches to its box); SVG default otherwise. */
    val preserveAspectRatio: String? = null,
    val rootClassName: String? = null,
    val width: Float? = null,
    val height: Float? = null,
    val strokeWidth: Float? = null,
    val strokeLinecap: String? = null,
    val strokeLinejoin: String? = null,
    val fill: String? = null,
    val stroke: String? = null,
    val gradients: List<SvgGradient> = emptyList(),
    val clips: List<SvgClip> = emptyList(),
    /** Variant-dependent paints, keyed `<token>.<variant>` — referenced as `token:<token>`. */
    val tokens: Map<String, String> = emptyMap(),
) {
    /** The gradient an element's `url(#id)` refers to, for the given mascot variant. */
    fun gradient(id: String, variant: String? = null): SvgGradient? =
        gradients.firstOrNull { it.id == id && (it.variant == null || it.variant == variant) }

    fun clip(id: String): SvgClip? = clips.firstOrNull { it.id == id }

    /** A variant-dependent colour: `resolveToken("ink", "mono")`. */
    fun resolveToken(token: String, variant: String): String? = tokens["$token.$variant"]
}

object SvgPaths {
    const val DIGEST = "9693031e14e4ab3f"

    /** Every extracted group: `icon.play`, `glyph.ConfigGlyph`, `chain.tile`, `mascot.MascotFace`, … */
    val groups: List<SvgGroup> = listOf(
    SvgGroup(
        name = "art.Fallback",
        viewBox = "0 0 100 100",
        rootClassName = "art-fallback",
        elements = listOf(
            SvgElement(kind = "circle", cx = 50f, cy = 50f, r = 49f, fill = "#161616"),
            SvgElement(kind = "circle", cx = 50f, cy = 50f, r = 40f, fill = "none", stroke = "#2c2c2b", strokeWidth = "1.2"),
            SvgElement(kind = "circle", cx = 50f, cy = 50f, r = 31f, fill = "none", stroke = "#2c2c2b", strokeWidth = "1.2"),
            SvgElement(kind = "circle", cx = 50f, cy = 50f, r = 22f, fill = "none", stroke = "#2c2c2b", strokeWidth = "1.2"),
            SvgElement(kind = "circle", cx = 50f, cy = 50f, r = 15f, fill = "#8d8e8b"),
            SvgElement(kind = "circle", cx = 50f, cy = 50f, r = 15f, fill = "none", stroke = "#e4e5e1", strokeWidth = "1.5"),
            SvgElement(kind = "circle", cx = 50f, cy = 50f, r = 3f, fill = "#0b0b0b"),
            SvgElement(kind = "path", d = "M50 50 62 20", stroke = "#fff", strokeWidth = "14", strokeOpacity = "0.07"),
            SvgElement(kind = "text", x = 50f, y = 90f, fill = "#4b4c4a", textAnchor = "middle", fontSize = "9", fontFamily = "Orbitron, sans-serif", text = "{name}"),
        ),
    ),
    SvgGroup(
        name = "chain.tile",
        viewBox = "0 0 64 40",
        width = 64f,
        height = 40f,
        gradients = listOf(
            SvgGradient(id = "a", kind = "linear", x1 = 0f, y1 = 0f, x2 = 1f, y2 = 1f, stops = listOf(
                SvgStop(offset = 0f, color = "#fff", opacity = 1f),
                SvgStop(offset = 0.22f, color = "#c7c8c5", opacity = 1f),
                SvgStop(offset = 0.5f, color = "#3b3c3a", opacity = 1f),
                SvgStop(offset = 0.76f, color = "#9b9c99", opacity = 1f),
                SvgStop(offset = 1f, color = "#1c1d1c", opacity = 1f),
            )),
            SvgGradient(id = "b", kind = "linear", x1 = 0f, y1 = 0f, x2 = 1f, y2 = 0f, stops = listOf(
                SvgStop(offset = 0f, color = "#f2f2ef", opacity = 1f),
                SvgStop(offset = 0.3f, color = "#7c7d7a", opacity = 1f),
                SvgStop(offset = 0.6f, color = "#171817", opacity = 1f),
                SvgStop(offset = 1f, color = "#8d8e8b", opacity = 1f),
            )),
            SvgGradient(id = "s", kind = "radial", stops = listOf(
                SvgStop(offset = 0f, color = "#000", opacity = 0.55f),
                SvgStop(offset = 1f, color = "#000", opacity = 0f),
            )),
        ),
        elements = listOf(
            SvgElement(kind = "ellipse", cx = 34f, cy = 26f, rx = 34f, ry = 10f, fill = "url(#s)"),
            SvgElement(kind = "ellipse", cx = 21f, cy = 20f, rx = 21.5f, ry = 13f, fill = "url(#a)", stroke = "#0b0b0b", strokeWidth = "2.2"),
            SvgElement(kind = "ellipse", cx = 21f, cy = 20f, rx = 13f, ry = 5.4f, fill = "#060606"),
            SvgElement(kind = "ellipse", cx = 13f, cy = 13.5f, rx = 8f, ry = 2f, fill = "#fff", opacity = ".62"),
            SvgElement(kind = "rect", rx = 7.5f, x = 45.5f, y = 5f, width = 15f, height = 30f, fill = "url(#b)", stroke = "#0b0b0b", strokeWidth = "2"),
            SvgElement(kind = "rect", rx = 2.5f, x = 50.5f, y = 10f, width = 5f, height = 20f, fill = "#050505"),
            SvgElement(kind = "rect", rx = 1.2f, x = 47.6f, y = 9f, width = 2.4f, height = 13f, fill = "#fff", opacity = ".6"),
        ),
    ),
    SvgGroup(
        name = "glyph.ConfigGlyph",
        viewBox = "0 0 140 140",
        rootClassName = "glyph glyph--config",
        elements = listOf(
            SvgElement(kind = "path", d = "M87.3 75.1L95.7 77.3L95.7 86.7L87.3 88.9L84.4 96.8L89.5 103.9L83.4 111.2L75.5 107.4L68.2 111.6L67.6 120.3L58.2 121.9L54.7 114.0L46.3 112.5L40.2 118.8L32.0 114.0L34.4 105.6L29.0 99.1L20.3 100.0L17.1 91.1L24.3 86.2L24.3 77.8L17.1 72.9L20.3 64.0L29.0 64.9L34.4 58.4L32.0 50.0L40.2 45.2L46.3 51.5L54.7 50.0L58.2 42.1L67.6 43.7L68.2 52.4L75.5 56.6L83.4 52.8L89.5 60.1L84.4 67.2ZM41 82a15 15 0 1 0 30 0a15 15 0 1 0 -30 0Z", fill = "currentColor", fillRule = "evenodd"),
            SvgElement(kind = "path", d = "M124.4 33.4L129.8 34.8L129.8 41.2L124.4 42.6L122.3 47.8L125.1 52.6L120.6 57.1L115.8 54.3L110.6 56.4L109.2 61.8L102.8 61.8L101.4 56.4L96.2 54.3L91.4 57.1L86.9 52.6L89.7 47.8L87.6 42.6L82.2 41.2L82.2 34.8L87.6 33.4L89.7 28.2L86.9 23.4L91.4 18.9L96.2 21.7L101.4 19.6L102.8 14.2L109.2 14.2L110.6 19.6L115.8 21.7L120.6 18.9L125.1 23.4L122.3 28.2ZM98 38a8 8 0 1 0 16 0a8 8 0 1 0 -16 0Z", fill = "currentColor", fillRule = "evenodd"),
            SvgElement(kind = "circle", cx = 56f, cy = 82f, r = 8f, fill = "var(--glyph-dot)"),
        ),
    ),
    SvgGroup(
        name = "glyph.GamesGlyph",
        viewBox = "0 0 140 140",
        rootClassName = "glyph glyph--games",
        elements = listOf(
            SvgElement(kind = "path", d = "M38 44c-14 0-22 10-25 24l-7 33c-3 15 9 23 20 14l25-20c6-5 12-7 19-7s13 2 19 7l25 20c11 9 23 1 20-14l-7-33c-3-14-11-24-25-24-14 0-20 8-32 8s-18-8-32-8z", fill = "currentColor"),
            SvgElement(kind = "path", d = "M38 58v26M25 71h26", stroke = "var(--glyph-hole)", strokeWidth = "7.5", strokeLinecap = "round"),
            SvgElement(kind = "circle", cx = 98f, cy = 66f, r = 5.5f, fill = "var(--glyph-hole)"),
            SvgElement(kind = "circle", cx = 112f, cy = 79f, r = 5.5f, fill = "var(--glyph-hole)"),
        ),
    ),
    SvgGroup(
        name = "glyph.HomiesGlyph",
        viewBox = "0 0 140 140",
        rootClassName = "glyph glyph--homies",
        elements = listOf(
            SvgElement(kind = "circle", cx = 70f, cy = 42f, r = 17f, fill = "currentColor"),
            SvgElement(kind = "path", d = "M36 100c0-18 14-31 34-31s34 13 34 31v6H36z", fill = "currentColor"),
            SvgElement(kind = "circle", cx = 34f, cy = 54f, r = 13.5f, fill = "currentColor", opacity = "0.85"),
            SvgElement(kind = "path", d = "M6 103c0-15 10-26 26-26 5 0 11 3 16 8-6 7-10 15-10 24H7z", fill = "currentColor", opacity = "0.85"),
            SvgElement(kind = "circle", cx = 106f, cy = 54f, r = 13.5f, fill = "currentColor", opacity = "0.85"),
            SvgElement(kind = "path", d = "M134 103c0-15-10-26-26-26-5 0-11 3-16 8 6 7 10 15 10 24h31z", fill = "currentColor", opacity = "0.85"),
            SvgElement(kind = "path", d = "M14 114c5-10 16-15 32-15h48c16 0 27 5 32 15v4H14z", fill = "currentColor"),
        ),
    ),
    SvgGroup(
        name = "glyph.MusicGlyph",
        viewBox = "0 0 140 140",
        rootClassName = "glyph glyph--music",
        elements = listOf(
            SvgElement(kind = "path", d = "M20 42C50 28 86 28 117 38", fill = "none", stroke = "currentColor", strokeWidth = "6.5", strokeLinecap = "round"),
            SvgElement(kind = "path", d = "M18 54C50 40 87 40 119 50", fill = "none", stroke = "currentColor", strokeWidth = "6.5", strokeLinecap = "round"),
            SvgElement(kind = "path", d = "M18 66C48 53 84 53 113 62", fill = "none", stroke = "currentColor", strokeWidth = "6.5", strokeLinecap = "round"),
            SvgElement(kind = "path", d = "M19 78C45 66 76 66 99 71", fill = "none", stroke = "currentColor", strokeWidth = "6.5", strokeLinecap = "round"),
            SvgElement(kind = "path", d = "M108 18L100 88", fill = "none", stroke = "currentColor", strokeWidth = "7.5", strokeLinecap = "round"),
            SvgElement(kind = "ellipse", cx = 94f, cy = 97f, rx = 15f, ry = 12.5f, fill = "none", stroke = "currentColor", strokeWidth = "7", strokeLinecap = "round", transform = "rotate(-12 94 97)"),
            SvgElement(kind = "path", d = "M40 86L38 113", fill = "none", stroke = "currentColor", strokeWidth = "5.5", strokeLinecap = "round", strokeLinejoin = "round"),
            SvgElement(kind = "path", d = "M38 100C30 98 24 103 25 110C26 116 34 118 40 114", fill = "none", stroke = "currentColor", strokeWidth = "5.5", strokeLinecap = "round", strokeLinejoin = "round"),
            SvgElement(kind = "path", d = "M64 86L62 113", fill = "none", stroke = "currentColor", strokeWidth = "5.5", strokeLinecap = "round", strokeLinejoin = "round"),
            SvgElement(kind = "path", d = "M62 100C54 98 48 103 49 110C50 116 58 118 64 114", fill = "none", stroke = "currentColor", strokeWidth = "5.5", strokeLinecap = "round", strokeLinejoin = "round"),
        ),
    ),
    SvgGroup(
        name = "glyph.NodeGlyph",
        viewBox = "0 0 140 140",
        rootClassName = "glyph",
        elements = listOf(
            SvgElement(kind = "circle", cx = 70f, cy = 70f, r = 34f, fill = "none", stroke = "currentColor", strokeWidth = "9"),
        ),
    ),
    SvgGroup(
        name = "glyph.NoteGlyph",
        viewBox = "0 0 140 140",
        rootClassName = "glyph glyph--note",
        elements = listOf(
            SvgElement(kind = "path", d = "M54 106 V30 L104 20 V94", fill = "none", stroke = "currentColor", strokeWidth = "7", strokeLinecap = "round", strokeLinejoin = "round"),
            SvgElement(kind = "ellipse", cx = 44f, cy = 107f, rx = 13f, ry = 10f, fill = "currentColor", transform = "rotate(-16 44 107)"),
            SvgElement(kind = "ellipse", cx = 94f, cy = 95f, rx = 13f, ry = 10f, fill = "currentColor", transform = "rotate(-16 94 95)"),
        ),
    ),
    SvgGroup(
        name = "glyph.PlayGlyph",
        viewBox = "0 0 140 140",
        rootClassName = "glyph glyph--play",
        elements = listOf(
            SvgElement(kind = "path", d = "M48 32 L112 70 L48 108 Z", fill = "currentColor", strokeLinejoin = "round"),
        ),
    ),
    SvgGroup(
        name = "glyph.StarGlyph",
        viewBox = "0 0 140 140",
        rootClassName = "glyph glyph--star",
        elements = listOf(
            SvgElement(kind = "path", d = "M70 20l17 34 38 6-27 27 6 38-34-18-34 18 6-38-27-27 38-6z", fill = "currentColor", strokeLinejoin = "round"),
        ),
    ),
    SvgGroup(
        name = "icon.chat",
        viewBox = "0 0 24 24",
        rootClassName = "ico",
        strokeWidth = 1.9f,
        strokeLinecap = "round",
        strokeLinejoin = "round",
        elements = listOf(
            SvgElement(kind = "path", d = "M4 6a2 2 0 012-2h12a2 2 0 012 2v8a2 2 0 01-2 2H10l-4 4v-4a2 2 0 01-2-2z", fill = "none", stroke = "currentColor"),
        ),
    ),
    SvgGroup(
        name = "icon.check",
        viewBox = "0 0 24 24",
        rootClassName = "ico",
        strokeWidth = 1.9f,
        strokeLinecap = "round",
        strokeLinejoin = "round",
        elements = listOf(
            SvgElement(kind = "path", d = "M5 12.5l4.5 4.5L19 7.5", fill = "none", stroke = "currentColor"),
        ),
    ),
    SvgGroup(
        name = "icon.chevron",
        viewBox = "0 0 24 24",
        rootClassName = "ico",
        strokeWidth = 1.9f,
        strokeLinecap = "round",
        strokeLinejoin = "round",
        elements = listOf(
            SvgElement(kind = "path", d = "M9 5l7 7-7 7", fill = "none", stroke = "currentColor"),
        ),
    ),
    SvgGroup(
        name = "icon.close",
        viewBox = "0 0 24 24",
        rootClassName = "ico",
        strokeWidth = 1.9f,
        strokeLinecap = "round",
        strokeLinejoin = "round",
        elements = listOf(
            SvgElement(kind = "path", d = "M6 6l12 12M18 6L6 18", fill = "none", stroke = "currentColor"),
        ),
    ),
    SvgGroup(
        name = "icon.disc",
        viewBox = "0 0 24 24",
        rootClassName = "ico",
        strokeWidth = 1.9f,
        strokeLinecap = "round",
        strokeLinejoin = "round",
        elements = listOf(
            SvgElement(kind = "path", d = "M12 20a8 8 0 100-16 8 8 0 000 16zM12 14.5a2.5 2.5 0 100-5 2.5 2.5 0 000 5z", fill = "none", stroke = "currentColor"),
        ),
    ),
    SvgGroup(
        name = "icon.download",
        viewBox = "0 0 24 24",
        rootClassName = "ico",
        strokeWidth = 1.9f,
        strokeLinecap = "round",
        strokeLinejoin = "round",
        elements = listOf(
            SvgElement(kind = "path", d = "M12 4v11M7 11l5 5 5-5M5 20h14", fill = "none", stroke = "currentColor"),
        ),
    ),
    SvgGroup(
        name = "icon.edit",
        viewBox = "0 0 24 24",
        rootClassName = "ico",
        strokeWidth = 1.9f,
        strokeLinecap = "round",
        strokeLinejoin = "round",
        elements = listOf(
            SvgElement(kind = "path", d = "M4 20l1-4L16 5l3 3L8 19zM14 7l3 3", fill = "none", stroke = "currentColor"),
        ),
    ),
    SvgGroup(
        name = "icon.exit-fullscreen",
        viewBox = "0 0 24 24",
        rootClassName = "ico",
        strokeWidth = 1.9f,
        strokeLinecap = "round",
        strokeLinejoin = "round",
        elements = listOf(
            SvgElement(kind = "path", d = "M9 4v5H4M15 4v5h5M9 20v-5H4M15 20v-5h5", fill = "none", stroke = "currentColor"),
        ),
    ),
    SvgGroup(
        name = "icon.external",
        viewBox = "0 0 24 24",
        rootClassName = "ico",
        strokeWidth = 1.9f,
        strokeLinecap = "round",
        strokeLinejoin = "round",
        elements = listOf(
            SvgElement(kind = "path", d = "M14 4h6v6M20 4l-9 9M18 14v5a1 1 0 01-1 1H5a1 1 0 01-1-1V7a1 1 0 011-1h5", fill = "none", stroke = "currentColor"),
        ),
    ),
    SvgGroup(
        name = "icon.folder",
        viewBox = "0 0 24 24",
        rootClassName = "ico",
        strokeWidth = 1.9f,
        strokeLinecap = "round",
        strokeLinejoin = "round",
        elements = listOf(
            SvgElement(kind = "path", d = "M3 7a2 2 0 012-2h4l2 2h8a2 2 0 012 2v8a2 2 0 01-2 2H5a2 2 0 01-2-2z", fill = "none", stroke = "currentColor"),
        ),
    ),
    SvgGroup(
        name = "icon.fullscreen",
        viewBox = "0 0 24 24",
        rootClassName = "ico",
        strokeWidth = 1.9f,
        strokeLinecap = "round",
        strokeLinejoin = "round",
        elements = listOf(
            SvgElement(kind = "path", d = "M4 9V4h5M20 9V4h-5M4 15v5h5M20 15v5h-5", fill = "none", stroke = "currentColor"),
        ),
    ),
    SvgGroup(
        name = "icon.gamepad",
        viewBox = "0 0 24 24",
        rootClassName = "ico",
        strokeWidth = 1.9f,
        strokeLinecap = "round",
        strokeLinejoin = "round",
        elements = listOf(
            SvgElement(kind = "path", d = "M7 8h10a4 4 0 014 4v2.5a2.5 2.5 0 01-4.3 1.7L15 14.5H9l-1.7 1.7A2.5 2.5 0 013 14.5V12a4 4 0 014-4zM8 10.5v3M6.5 12h3M15.5 11.5h.01M17.5 13h.01", fill = "none", stroke = "currentColor"),
        ),
    ),
    SvgGroup(
        name = "icon.image",
        viewBox = "0 0 24 24",
        rootClassName = "ico",
        strokeWidth = 1.9f,
        strokeLinecap = "round",
        strokeLinejoin = "round",
        elements = listOf(
            SvgElement(kind = "path", d = "M6 5h12a2 2 0 012 2v10a2 2 0 01-2 2H6a2 2 0 01-2-2V7a2 2 0 012-2zM9 10.5a1.5 1.5 0 100-3 1.5 1.5 0 000 3zM5 17l5-4 4 3 3-2 3 3", fill = "none", stroke = "currentColor"),
        ),
    ),
    SvgGroup(
        name = "icon.link",
        viewBox = "0 0 24 24",
        rootClassName = "ico",
        strokeWidth = 1.9f,
        strokeLinecap = "round",
        strokeLinejoin = "round",
        elements = listOf(
            SvgElement(kind = "path", d = "M10 14a4 4 0 005.7 0l3-3a4 4 0 00-5.7-5.7l-1 1M14 10a4 4 0 00-5.7 0l-3 3a4 4 0 005.7 5.7l1-1", fill = "none", stroke = "currentColor"),
        ),
    ),
    SvgGroup(
        name = "icon.list",
        viewBox = "0 0 24 24",
        rootClassName = "ico",
        strokeWidth = 1.9f,
        strokeLinecap = "round",
        strokeLinejoin = "round",
        elements = listOf(
            SvgElement(kind = "path", d = "M5 7h14M5 12h14M5 17h9", fill = "none", stroke = "currentColor"),
        ),
    ),
    SvgGroup(
        name = "icon.next",
        viewBox = "0 0 24 24",
        rootClassName = "ico",
        strokeWidth = 1.9f,
        strokeLinecap = "round",
        strokeLinejoin = "round",
        elements = listOf(
            SvgElement(kind = "path", d = "M6 5.5v13l9-6.5zM17 5h2.2v14H17z", fill = "currentColor", stroke = "none"),
        ),
    ),
    SvgGroup(
        name = "icon.note",
        viewBox = "0 0 24 24",
        rootClassName = "ico",
        strokeWidth = 1.9f,
        strokeLinecap = "round",
        strokeLinejoin = "round",
        elements = listOf(
            SvgElement(kind = "path", d = "M9 18V6l10-2v12M9 18a3 3 0 11-6 0 3 3 0 016 0zM19 16a3 3 0 11-6 0 3 3 0 016 0z", fill = "none", stroke = "currentColor"),
        ),
    ),
    SvgGroup(
        name = "icon.pause",
        viewBox = "0 0 24 24",
        rootClassName = "ico",
        strokeWidth = 1.9f,
        strokeLinecap = "round",
        strokeLinejoin = "round",
        elements = listOf(
            SvgElement(kind = "path", d = "M7 5h4v14H7zM13 5h4v14h-4z", fill = "currentColor", stroke = "none"),
        ),
    ),
    SvgGroup(
        name = "icon.people",
        viewBox = "0 0 24 24",
        rootClassName = "ico",
        strokeWidth = 1.9f,
        strokeLinecap = "round",
        strokeLinejoin = "round",
        elements = listOf(
            SvgElement(kind = "path", d = "M9 11a3 3 0 100-6 3 3 0 000 6zM3 19c.6-3 3-4.5 6-4.5s5.4 1.5 6 4.5M16.5 11a2.6 2.6 0 100-5.2M17 14.6c2 .3 3.5 1.7 4 4.4", fill = "none", stroke = "currentColor"),
        ),
    ),
    SvgGroup(
        name = "icon.phone",
        viewBox = "0 0 24 24",
        rootClassName = "ico",
        strokeWidth = 1.9f,
        strokeLinecap = "round",
        strokeLinejoin = "round",
        elements = listOf(
            SvgElement(kind = "path", d = "M6 4h3l1.5 4-2 1.5a11 11 0 006 6l1.5-2 4 1.5v3a2 2 0 01-2 2A15 15 0 014 6a2 2 0 012-2z", fill = "none", stroke = "currentColor"),
        ),
    ),
    SvgGroup(
        name = "icon.play",
        viewBox = "0 0 24 24",
        rootClassName = "ico",
        strokeWidth = 1.9f,
        strokeLinecap = "round",
        strokeLinejoin = "round",
        elements = listOf(
            SvgElement(kind = "path", d = "M8 5.5v13l11-6.5z", fill = "currentColor", stroke = "none"),
        ),
    ),
    SvgGroup(
        name = "icon.playlist-add",
        viewBox = "0 0 24 24",
        rootClassName = "ico",
        strokeWidth = 1.9f,
        strokeLinecap = "round",
        strokeLinejoin = "round",
        elements = listOf(
            SvgElement(kind = "path", d = "M4 7h11M4 12h11M4 17h6M17 14v6M14 17h6", fill = "none", stroke = "currentColor"),
        ),
    ),
    SvgGroup(
        name = "icon.playlists",
        viewBox = "0 0 24 24",
        rootClassName = "ico",
        strokeWidth = 1.9f,
        strokeLinecap = "round",
        strokeLinejoin = "round",
        elements = listOf(
            SvgElement(kind = "path", d = "M4 7h12M4 12h12M4 17h7M18 10v7a2 2 0 11-2-2", fill = "none", stroke = "currentColor"),
        ),
    ),
    SvgGroup(
        name = "icon.plus",
        viewBox = "0 0 24 24",
        rootClassName = "ico",
        strokeWidth = 1.9f,
        strokeLinecap = "round",
        strokeLinejoin = "round",
        elements = listOf(
            SvgElement(kind = "path", d = "M12 5v14M5 12h14", fill = "none", stroke = "currentColor"),
        ),
    ),
    SvgGroup(
        name = "icon.prev",
        viewBox = "0 0 24 24",
        rootClassName = "ico",
        strokeWidth = 1.9f,
        strokeLinecap = "round",
        strokeLinejoin = "round",
        elements = listOf(
            SvgElement(kind = "path", d = "M18 5.5v13l-9-6.5zM5 5h2.2v14H5z", fill = "currentColor", stroke = "none"),
        ),
    ),
    SvgGroup(
        name = "icon.repeat",
        viewBox = "0 0 24 24",
        rootClassName = "ico",
        strokeWidth = 1.9f,
        strokeLinecap = "round",
        strokeLinejoin = "round",
        elements = listOf(
            SvgElement(kind = "path", d = "M4 10V8a3 3 0 013-3h12M20 14v2a3 3 0 01-3 3H5M16 2l3 3-3 3M8 22l-3-3 3-3", fill = "none", stroke = "currentColor"),
        ),
    ),
    SvgGroup(
        name = "icon.repeat-one",
        viewBox = "0 0 24 24",
        rootClassName = "ico",
        strokeWidth = 1.9f,
        strokeLinecap = "round",
        strokeLinejoin = "round",
        elements = listOf(
            SvgElement(kind = "path", d = "M4 10V8a3 3 0 013-3h12M20 14v2a3 3 0 01-3 3H5M16 2l3 3-3 3M8 22l-3-3 3-3M11 10l1.6-1.2V15", fill = "none", stroke = "currentColor"),
        ),
    ),
    SvgGroup(
        name = "icon.search",
        viewBox = "0 0 24 24",
        rootClassName = "ico",
        strokeWidth = 1.9f,
        strokeLinecap = "round",
        strokeLinejoin = "round",
        elements = listOf(
            SvgElement(kind = "path", d = "M10.5 16a5.5 5.5 0 100-11 5.5 5.5 0 000 11zM15 15l5 5", fill = "none", stroke = "currentColor"),
        ),
    ),
    SvgGroup(
        name = "icon.shuffle",
        viewBox = "0 0 24 24",
        rootClassName = "ico",
        strokeWidth = 1.9f,
        strokeLinecap = "round",
        strokeLinejoin = "round",
        elements = listOf(
            SvgElement(kind = "path", d = "M3 7h4l10 10h4M3 17h4l10-10h4M18 4l3 3-3 3M18 14l3 3-3 3", fill = "none", stroke = "currentColor"),
        ),
    ),
    SvgGroup(
        name = "icon.star",
        viewBox = "0 0 24 24",
        rootClassName = "ico",
        strokeWidth = 1.9f,
        strokeLinecap = "round",
        strokeLinejoin = "round",
        elements = listOf(
            SvgElement(kind = "path", d = "M12 4l2.4 5 5.4.7-4 3.8 1 5.4L12 16.3 7.2 18.9l1-5.4-4-3.8 5.4-.7z", fill = "none", stroke = "currentColor"),
        ),
    ),
    SvgGroup(
        name = "icon.star-fill",
        viewBox = "0 0 24 24",
        rootClassName = "ico",
        strokeWidth = 1.9f,
        strokeLinecap = "round",
        strokeLinejoin = "round",
        elements = listOf(
            SvgElement(kind = "path", d = "M12 4l2.4 5 5.4.7-4 3.8 1 5.4L12 16.3 7.2 18.9l1-5.4-4-3.8 5.4-.7z", fill = "currentColor", stroke = "currentColor"),
        ),
    ),
    SvgGroup(
        name = "icon.trash",
        viewBox = "0 0 24 24",
        rootClassName = "ico",
        strokeWidth = 1.9f,
        strokeLinecap = "round",
        strokeLinejoin = "round",
        elements = listOf(
            SvgElement(kind = "path", d = "M5 7h14M10 7V5h4v2M7 7l1 12h8l1-12M10 11v5M14 11v5", fill = "none", stroke = "currentColor"),
        ),
    ),
    SvgGroup(
        name = "icon.user",
        viewBox = "0 0 24 24",
        rootClassName = "ico",
        strokeWidth = 1.9f,
        strokeLinecap = "round",
        strokeLinejoin = "round",
        elements = listOf(
            SvgElement(kind = "path", d = "M12 12a4 4 0 100-8 4 4 0 000 8zM4 20c1-4 4-6 8-6s7 2 8 6", fill = "none", stroke = "currentColor"),
        ),
    ),
    SvgGroup(
        name = "icon.volume",
        viewBox = "0 0 24 24",
        rootClassName = "ico",
        strokeWidth = 1.9f,
        strokeLinecap = "round",
        strokeLinejoin = "round",
        elements = listOf(
            SvgElement(kind = "path", d = "M4 9.5v5h3.5L12 18V6L7.5 9.5zM15.5 9a4 4 0 010 6M18 6.5a8 8 0 010 11", fill = "none", stroke = "currentColor"),
        ),
    ),
    SvgGroup(
        name = "mascot.MascotFace",
        viewBox = "0 0 200 200",
        gradients = listOf(
            SvgGradient(id = "mascot.f", kind = "radial", cx = 0.44f, cy = 0.26f, r = 0.84f, variant = "yellow", stops = listOf(
                SvgStop(offset = 0f, color = "#fffdb4", opacity = 1f),
                SvgStop(offset = 0.24f, color = "#eef43d", opacity = 1f),
                SvgStop(offset = 0.58f, color = "#c2cf29", opacity = 1f),
                SvgStop(offset = 0.86f, color = "#7f8c16", opacity = 1f),
                SvgStop(offset = 1f, color = "#434d0b", opacity = 1f),
            )),
            SvgGradient(id = "mascot.f", kind = "radial", cx = 0.44f, cy = 0.26f, r = 0.84f, variant = "mono", stops = listOf(
                SvgStop(offset = 0f, color = "#fcfcfb", opacity = 1f),
                SvgStop(offset = 0.24f, color = "#dcddda", opacity = 1f),
                SvgStop(offset = 0.58f, color = "#a1a29f", opacity = 1f),
                SvgStop(offset = 0.86f, color = "#5f605e", opacity = 1f),
                SvgStop(offset = 1f, color = "#2a2b2a", opacity = 1f),
            )),
            SvgGradient(id = "mascot.b", kind = "linear", x1 = 0f, y1 = 0f, x2 = 0f, y2 = 1f, stops = listOf(
                SvgStop(offset = 0f, color = "#9a9b98", opacity = 1f),
                SvgStop(offset = 0.4f, color = "#2b2c2b", opacity = 1f),
                SvgStop(offset = 1f, color = "#575856", opacity = 1f),
            )),
            SvgGradient(id = "mascot.t", kind = "linear", x1 = 0f, y1 = 0f, x2 = 0f, y2 = 1f, stops = listOf(
                SvgStop(offset = 0f, color = "#ffffff", opacity = 1f),
                SvgStop(offset = 0.35f, color = "#e7e8e5", opacity = 1f),
                SvgStop(offset = 0.75f, color = "#a3a4a0", opacity = 1f),
                SvgStop(offset = 1f, color = "#5d5e5a", opacity = 1f),
            )),
            SvgGradient(id = "mascot.s", kind = "radial", stops = listOf(
                SvgStop(offset = 0f, color = "#fff", opacity = 1f),
                SvgStop(offset = 0.4f, color = "#fff", opacity = 0.4f),
                SvgStop(offset = 1f, color = "#fff", opacity = 0f),
            )),
        ),
        clips = listOf(
            SvgClip(id = "mascot.c", elements = listOf(
                SvgElement(kind = "circle", cx = 100f, cy = 100f, r = 85f),
            )),
            SvgClip(id = "mascot.m", elements = listOf(
                SvgElement(kind = "path", d = "M38 124Q100 146 162 124L159 138Q150 172 100 176Q50 172 41 138Z"),
            )),
        ),
        tokens = mapOf(
            "ink.yellow" to "#14150e",
            "ink.mono" to "#161716",
        ),
        elements = listOf(
            SvgElement(kind = "circle", cx = 100f, cy = 100f, r = 99f, fill = "#ecede9"),
            SvgElement(kind = "circle", cx = 100f, cy = 100f, r = 96f, fill = "#0b0b0b"),
            SvgElement(kind = "circle", cx = 100f, cy = 100f, r = 93f, fill = "url(#mascot.b)"),
            SvgElement(kind = "circle", cx = 100f, cy = 100f, r = 88.5f, fill = "#050505"),
            SvgElement(kind = "circle", cx = 100f, cy = 100f, r = 85f, fill = "url(#mascot.f)", clipPath = "url(#mascot.c)"),
            SvgElement(kind = "ellipse", cx = 100f, cy = 190f, rx = 74f, ry = 28f, fill = "#fff", clipPath = "url(#mascot.c)", opacity = "0.1"),
            SvgElement(kind = "ellipse", cx = 92f, cy = 46f, rx = 62f, ry = 34f, fill = "#fff", clipPath = "url(#mascot.c)", opacity = "0.26"),
            SvgElement(kind = "ellipse", cx = 68f, cy = 34f, rx = 27f, ry = 9f, fill = "#fff", clipPath = "url(#mascot.c)", opacity = "0.42", transform = "rotate(-18 68 34)"),
            SvgElement(kind = "path", d = "M34 82C46 56 76 56 100 94L96 100C76 74 54 74 40 90Z", fill = "token:ink", clipPath = "url(#mascot.c)"),
            SvgElement(kind = "path", d = "M166 82C154 56 124 56 100 94L104 100C124 74 146 74 160 90Z", fill = "token:ink", clipPath = "url(#mascot.c)"),
            SvgElement(kind = "path", d = "M44 70C58 60 74 64 88 80", fill = "none", stroke = "#fff", strokeWidth = "1.6", strokeOpacity = "0.18", strokeLinecap = "round", clipPath = "url(#mascot.c)"),
            SvgElement(kind = "path", d = "M156 70C142 60 126 64 112 80", fill = "none", stroke = "#fff", strokeWidth = "1.6", strokeOpacity = "0.18", strokeLinecap = "round", clipPath = "url(#mascot.c)"),
            SvgElement(kind = "ellipse", cx = 66f, cy = 102f, rx = 5.5f, ry = 10f, fill = "token:ink", clipPath = "url(#mascot.c)", transform = "rotate(12 66 102)", className = "mascot-eyes"),
            SvgElement(kind = "ellipse", cx = 134f, cy = 102f, rx = 5.5f, ry = 10f, fill = "token:ink", clipPath = "url(#mascot.c)", transform = "rotate(-12 134 102)", className = "mascot-eyes"),
            SvgElement(kind = "circle", r = 24f, fill = "url(#mascot.s)", clipPath = "url(#mascot.c)", transform = "translate(100 90)", className = "mascot-flare"),
            SvgElement(kind = "path", d = "M-30 0Q0 0 0-30Q0 0 30 0Q0 0 0 30Q0 0-30 0Z", fill = "#fff", clipPath = "url(#mascot.c)", opacity = "0.95", transform = "translate(100 90)", className = "mascot-flare"),
            SvgElement(kind = "circle", r = 4.2f, fill = "#fff", clipPath = "url(#mascot.c)", transform = "translate(100 90)", className = "mascot-flare"),
            SvgElement(kind = "path", d = "M28 116Q100 140 172 116L168 136Q158 176 100 181Q42 176 32 136Z", fill = "#0a0a07", clipPath = "url(#mascot.c)"),
            SvgElement(kind = "path", d = "M38 124Q100 146 162 124L159 138Q150 172 100 176Q50 172 41 138Z", fill = "url(#mascot.t)", clipPath = "url(#mascot.c)"),
            SvgElement(kind = "path", d = "M53 112V182M69 112V182M85 112V182M100 112V182M115 112V182M131 112V182M147 112V182", stroke = "#15160f", strokeWidth = "3.2", clipPath = "url(#mascot.m)"),
            SvgElement(kind = "ellipse", cx = 100f, cy = 176f, rx = 64f, ry = 12f, fill = "#000", clipPath = "url(#mascot.m)", opacity = "0.28"),
            SvgElement(kind = "path", d = "M40 126Q100 148 160 126", fill = "none", stroke = "#fff", strokeWidth = "1.5", strokeOpacity = "0.6", clipPath = "url(#mascot.c)"),
            SvgElement(kind = "path", d = "M28 116Q100 140 172 116", fill = "none", stroke = "#0a0a07", strokeWidth = "5", clipPath = "url(#mascot.c)"),
            SvgElement(kind = "circle", cx = 100f, cy = 100f, r = 84f, fill = "none", stroke = "#000", strokeWidth = "3.5", strokeOpacity = "0.5", clipPath = "url(#mascot.c)"),
            SvgElement(kind = "circle", cx = 100f, cy = 100f, r = 80f, fill = "none", stroke = "#fff", strokeWidth = "1.2", strokeOpacity = "0.14", clipPath = "url(#mascot.c)"),
        ),
    ),
    SvgGroup(
        name = "top.TopBar",
        viewBox = "0 0 72 58",
        gradients = listOf(
            SvgGradient(id = "up-fill", kind = "linear", x1 = 0f, y1 = 0f, x2 = 0f, y2 = 1f, stops = listOf(
                SvgStop(offset = 0f, color = "#9a9a98", opacity = 1f),
                SvgStop(offset = 0.45f, color = "#4b4b4a", opacity = 1f),
                SvgStop(offset = 1f, color = "#161616", opacity = 1f),
            )),
        ),
        elements = listOf(
            SvgElement(kind = "path", d = "M36 4 69 54H3Z", fill = "url(#up-fill)", stroke = "#f1f1ee", strokeWidth = "3.4", strokeLinejoin = "round"),
            SvgElement(kind = "path", d = "M36 14 59 49H13Z", fill = "none", stroke = "#fff", strokeWidth = "1.4", strokeOpacity = "0.25", strokeLinejoin = "round"),
            SvgElement(kind = "path", d = "M36 9 22 31", stroke = "#fff", strokeWidth = "2", strokeOpacity = "0.55", strokeLinecap = "round"),
        ),
    ),
    SvgGroup(
        name = "wire.Wireframe",
        viewBox = "0 0 800 800",
        preserveAspectRatio = "none",
        rootClassName = "wire",
        elements = listOf(
            SvgElement(kind = "path", d = "M160 620C242 516 335 390 415 316S618 214 764 208"),
            SvgElement(kind = "path", d = "M165 632C272 536 362 426 447 339S643 230 774 230"),
            SvgElement(kind = "path", d = "M162 605C259 501 366 400 484 315S668 242 779 256"),
            SvgElement(kind = "path", d = "M162 581C275 483 395 381 522 312S688 260 779 279"),
            SvgElement(kind = "path", d = "M163 558C287 462 418 368 555 311S712 278 779 304"),
            SvgElement(kind = "path", d = "M170 538C306 441 442 365 585 322S729 299 777 332"),
            SvgElement(kind = "path", d = "M180 519C321 429 470 366 612 341S743 323 769 358"),
            SvgElement(kind = "path", d = "M198 500C350 417 496 375 637 361S752 354 761 386"),
            SvgElement(kind = "path", d = "M221 483C373 413 524 390 661 386S755 386 750 417"),
            SvgElement(kind = "path", d = "M246 470C408 414 559 409 685 415S754 419 737 448"),
            SvgElement(kind = "path", d = "M272 460C438 420 592 433 705 443S746 453 720 480"),
            SvgElement(kind = "path", d = "M295 452C469 432 617 456 718 474S736 487 707 509"),
            SvgElement(kind = "path", d = "M318 446C498 446 643 484 729 506S725 522 690 541"),
            SvgElement(kind = "path", d = "M341 440C525 464 664 513 735 539S708 558 673 573"),
            SvgElement(kind = "path", d = "M182 617C362 561 552 480 759 214"),
            SvgElement(kind = "path", d = "M207 630C394 570 584 482 763 234"),
            SvgElement(kind = "path", d = "M239 637C429 583 610 495 768 260"),
            SvgElement(kind = "path", d = "M275 639C464 598 637 514 768 291"),
            SvgElement(kind = "path", d = "M315 638C497 611 654 534 765 324"),
            SvgElement(kind = "path", d = "M357 634C529 624 669 560 756 360"),
            SvgElement(kind = "path", d = "M401 627C558 636 681 591 745 399"),
            SvgElement(kind = "path", d = "M442 617C587 646 691 622 730 442"),
            SvgElement(kind = "ellipse", cx = 444f, cy = 441f, rx = 274f, ry = 158f, transform = "rotate(-19 444 441)", className = "wire-ring"),
            SvgElement(kind = "ellipse", cx = 438f, cy = 441f, rx = 312f, ry = 195f, transform = "rotate(-10 438 441)", className = "wire-ring wire-ring--2"),
        ),
    ),
    )

    private val byName: Map<String, SvgGroup> = groups.associateBy { it.name }

    /** Exact lookup — a missing group is a bug in the extractor, never a silent fallback. */
    fun require(name: String): SvgGroup =
        byName[name] ?: error("no SVG group \"$name\" (generated groups: undefined)")

    fun group(name: String): SvgGroup? = byName[name]

    val iconNames: List<String> = groups.map { it.name }.filter { it.startsWith("icon.") }.map { it.removePrefix("icon.") }

    /** The seven hub-node glyphs, by node id. */
    val glyphNames: List<String> = groups.map { it.name }.filter { it.startsWith("glyph.") }.map { it.removePrefix("glyph.") }
}
