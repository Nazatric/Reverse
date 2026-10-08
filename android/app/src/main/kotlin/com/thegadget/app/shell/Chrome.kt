package com.thegadget.app.shell

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material3.Text
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
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.thegadget.app.core.Clock
import com.thegadget.app.core.GadgetText
import com.thegadget.app.ui.SvgPalette
import com.thegadget.app.ui.SvgPaths
import com.thegadget.app.ui.renderSvgGroup
import com.thegadget.app.ui.theme.Css
import com.thegadget.app.ui.theme.GadgetFonts
import com.thegadget.app.ui.theme.GadgetTokens
import com.thegadget.app.state.AppState
import kotlinx.coroutines.delay
import java.util.Calendar

/** The status pill: up-arrow (when in a page), the mascot avatar, the profile name, the clock. */
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
    Row(
        modifier = modifier
            .statusBarsPadding()
            .fillMaxWidth()
            .height(96.dp)
            .padding(horizontal = 16.dp)
            .padding(top = 16.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        if (!nav.isEmpty) {
            val back = SvgPaths.require("top.TopBar")
            Box(
                Modifier.size(44.dp).clip(CircleShape)
                    .background(Color(0x14FFFFFF)).border(1.dp, tokens.colors.glassEdge, CircleShape),
                contentAlignment = Alignment.Center,
            ) {
                Canvas(Modifier.size(24.dp)) {
                    renderSvgGroup(back, palette, "fill", Rect(0f, 0f, size.width, size.height), Css::forClass)
                }
            }
            Spacer(Modifier.size(10.dp))
        }
        val face = SvgPaths.require("mascot.MascotFace")
        Box(
            Modifier.size(44.dp).clip(CircleShape)
                .background(Brush.radialGradient(listOf(Color(0xFFF4F4F1), Color(0xFF3A3B3D))))
                .border(1.5.dp, tokens.colors.glassEdge, CircleShape),
            contentAlignment = Alignment.Center,
        ) {
            Canvas(Modifier.size(30.dp)) {
                renderSvgGroup(face, palette, profile.avatar ?: "grin", Rect(0f, 0f, size.width, size.height), Css::forClass)
            }
        }
        Spacer(Modifier.size(12.dp))
        Text(
            profile.name,
            color = tokens.colors.text,
            style = TextStyle(fontFamily = GadgetFonts.display, fontWeight = FontWeight.W800, fontSize = 16.sp),
        )
        Spacer(Modifier.weight(1f))
        Text(
            GadgetText.formatClock(cal.get(Calendar.HOUR_OF_DAY), cal.get(Calendar.MINUTE), settings.hour24),
            color = tokens.colors.dim,
            style = TextStyle(fontFamily = GadgetFonts.display, fontWeight = FontWeight.W600, fontSize = 13.sp, letterSpacing = 1.sp),
        )
    }
}
