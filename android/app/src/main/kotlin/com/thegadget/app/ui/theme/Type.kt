package com.thegadget.app.ui.theme

import androidx.compose.ui.text.font.Font
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import com.thegadget.app.R

/**
 * The two families the web app loads from Google Fonts, bundled as static instances so the app is
 * fully offline and every weight renders identically to the browser:
 *
 *   Orbitron 400/500/600/700/800/900 — the display voice (hub labels, page titles, buttons)
 *   Exo 2   300/400/500/600/700      — body copy, labels, numbers
 *
 * The instances were produced from the same upstream variable fonts Google Fonts serves, so the
 * letterforms match the reference pixel for pixel.
 */
val Orbitron = FontFamily(
    Font(R.font.orbitron_400, FontWeight.Normal),
    Font(R.font.orbitron_500, FontWeight.Medium),
    Font(R.font.orbitron_600, FontWeight.SemiBold),
    Font(R.font.orbitron_700, FontWeight.Bold),
    Font(R.font.orbitron_800, FontWeight.ExtraBold),
    Font(R.font.orbitron_900, FontWeight.Black),
)

val Exo2 = FontFamily(
    Font(R.font.exo2_300, FontWeight.Light),
    Font(R.font.exo2_400, FontWeight.Normal),
    Font(R.font.exo2_500, FontWeight.Medium),
    Font(R.font.exo2_600, FontWeight.SemiBold),
    Font(R.font.exo2_700, FontWeight.Bold),
)

val Display = Orbitron
val Body = Exo2
