package com.thegadget.app.shell

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Text
import coil3.compose.AsyncImage
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.Rect
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.sp
import com.thegadget.app.core.Clock
import com.thegadget.app.core.GadgetMetrics
import com.thegadget.app.core.GadgetText
import com.thegadget.app.ui.Fx
import com.thegadget.app.ui.SvgPalette
import com.thegadget.app.ui.SvgPaths
import com.thegadget.app.ui.renderSvgGroup
import com.thegadget.app.ui.theme.Css
import com.thegadget.app.ui.theme.GadgetFonts
import com.thegadget.app.ui.theme.GadgetTokens
import com.thegadget.app.state.AppState
import kotlinx.coroutines.delay
import java.util.Calendar
import kotlin.math.max

/**
 * Persistent chrome, mirroring `TopBar.tsx`: the glowing up-arrow (back one level) top-left and the
 * status pill (avatar, status dot, name, clock) as a right-aligned capsule. All dimensions come
 * from the live stage metrics so proportions match the reference at any size.
 */
@Composable
fun ChromeBar(app: AppState, tokens: GadgetTokens, modifier: Modifier = Modifier) {
    val nav by app.nav.collectAsState()
    val profile by app.profile.collectAsState()
    val settings by app.settings.collectAsState()
    var cal by remember { mutableStateOf(Calendar.getInstance()) }
    LaunchedEffect(Unit) {
        while (true) {
            val now = Clock.now()
            delay(60_000 - now % 60_000)
            cal = Calendar.getInstance().apply { timeInMillis = Clock.now() }
        }
    }
    val palette = SvgPalette(currentColor = Css.GLYPH, cssVar = Css::cssVar)
    val density = LocalDensity.current

    BoxWithConstraints(modifier.fillMaxWidth().statusBarsPadding()) {
        val wPx = with(density) { maxWidth.toPx() }
        val hPx = with(density) { maxHeight.toPx() }
        val m = GadgetMetrics.compute(wPx, hPx)
        val u = m.u
        fun pd(px: Float): Dp = with(density) { px.toDp() }

        Box(Modifier.fillMaxWidth().padding(horizontal = pd(16f * u.coerceAtLeast(1f)), vertical = pd(10f))) {
            // ---- up-arrow, top-left (back one level; bump at the hub) ----
            Box(
                Modifier
                    .align(Alignment.TopStart)
                    .size(pd(max(72f * u, 50f)), pd(max(58f * u, 41f)))
                    .clickable {
                        Fx.close()
                        if (!nav.isEmpty) app.back()
                    },
                contentAlignment = Alignment.Center,
            ) {
                Canvas(Modifier.size(pd(max(72f * u, 50f)), pd(max(58f * u, 41f)))) {
                    val up = SvgPaths.require("top.TopBar")
                    renderSvgGroup(up, palette, "fill", Rect(0f, 0f, size.width, size.height), Css::forClass)
                }
            }

            // ---- status pill, top-right capsule ----
            val pillH = pd(max(54f * u, 44f))
            Row(
                modifier = Modifier
                    .align(Alignment.TopEnd)
                    .clickable {
                        Fx.tap()
                        app.push(com.thegadget.app.core.Route.Account)
                    }
                    .clip(RoundedCornerShape(50))
                    .background(
                        Brush.verticalGradient(listOf(Color(0xE6F4F4F1), Color(0xB8B9BAB6), Color(0x8E303130))),
                    )
                    .border(pd(1.5f), Color(0x66FFFFFF), RoundedCornerShape(50))
                    .padding(start = pd(max(7f * u, 4f)), end = pd(max(20f * u, 14f))),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(pd(max(10f * u, 7f))),
            ) {
                // avatar
                Box(
                    Modifier
                        .size(pillH * 0.76f)
                        .clip(CircleShape)
                        .background(Color(0xFF1B1C15))
                        .border(pd(1.5f), Color(0xF5FAFAF7), CircleShape),
                    contentAlignment = Alignment.Center,
                ) {
                    if (profile.avatar != null) {
                        AsyncImage(
                            model = profile.avatar,
                            contentDescription = null,
                            modifier = Modifier.size(pillH * 0.76f),
                        )
                    } else {
                        Canvas(Modifier.size(pillH * 0.76f)) {
                            val face = SvgPaths.require("mascot.MascotFace")
                            renderSvgGroup(face, palette, "grin", Rect(0f, 0f, size.width, size.height), Css::forClass)
                        }
                    }
                }
                // status dot
                Box(
                    Modifier
                        .size(pd(max(11f * u, 7f)))
                        .clip(CircleShape)
                        .background(
                            Brush.radialGradient(
                                listOf(Color(0xFF94D4F7), Color(0xFF1C78A5), Color(0xFF083049)),
                            ),
                        )
                        .border(pd(1f), Color(0xE6AAE1FF), CircleShape),
                )
                // name
                Text(
                    profile.name.ifBlank { "set your name" },
                    color = Color(0xFFF1F1EE),
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                    style = TextStyle(
                        fontFamily = GadgetFonts.body,
                        fontWeight = FontWeight.W500,
                        fontSize = (max(11f, 12f * u) / density.density).sp,
                    ),
                )
                Spacer(Modifier.size(pd(6f)))
                // clock
                Text(
                    GadgetText.formatClock(cal.get(Calendar.HOUR_OF_DAY), cal.get(Calendar.MINUTE), settings.hour24),
                    color = Color.White,
                    style = TextStyle(
                        fontFamily = GadgetFonts.body,
                        fontWeight = FontWeight.W300,
                        fontSize = (max(13f, 18f * u) / density.density).sp,
                        letterSpacing = 1.sp,
                    ),
                )
            }
        }
    }
}
