package com.thegadget.app.shell

import androidx.compose.animation.core.LinearEasing
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.tween
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.rotate
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import coil3.compose.AsyncImage
import com.thegadget.app.core.Route
import com.thegadget.app.state.AppState
import com.thegadget.app.ui.theme.GadgetFonts
import com.thegadget.app.ui.theme.GadgetTokens
import java.io.File

/**
 * The persistent glass capsule (`src/components/ui/MiniPlayer.tsx` + `pages.css` `.mini`):
 * cover art, title/subtitle, transport orb and a hairline progress bar. Every number below is the
 * CSS value — 64 px tall, `min(100vw - 28px, 480px)` wide, 46 px art disc that spins at 22 s while
 * playing, and a 3 px bar inset 18 px / 60 px with the `#d6e537 → --accent` fill.
 */
@Composable
fun MiniPlayer(app: AppState, tokens: GadgetTokens, modifier: Modifier = Modifier) {
    val nav by app.nav.collectAsState()
    val snap by app.player.state.collectAsState()
    val t = snap.current ?: return
    if (nav.route is Route.Now) return

    val pill = RoundedCornerShape(999.dp)
    val spin = rememberInfiniteTransition(label = "miniart")
    val angle by spin.animateFloat(
        initialValue = 0f,
        targetValue = 360f,
        label = "spin",
        animationSpec = infiniteRepeatable(tween(22_000, easing = LinearEasing)),
    )

    Box(modifier.fillMaxWidth().padding(horizontal = 14.dp), contentAlignment = Alignment.Center) {
        Box(
            Modifier
                .width(480.dp)
                .fillMaxWidth()
                .height(64.dp)
                .clip(pill)
                .background(
                    Brush.verticalGradient(
                        0f to Color(0xFF747472),
                        0.32f to Color(0xFF444443),
                        0.64f to Color(0xFF272726),
                        1f to Color(0xFF181817),
                    ),
                )
                .background(
                    Brush.verticalGradient(
                        0f to Color(0x4DFFFFFF),
                        0.45f to Color(0x0DFFFFFF),
                        0.5f to Color.Transparent,
                    ),
                )
                .border(1.5.dp, Color(0xD9F2F3EF), pill)
                .padding(start = 9.dp, end = 10.dp),
        ) {
            Row(
                Modifier.fillMaxWidth().height(64.dp).clickable { app.push(Route.Now) },
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Box(
                    Modifier
                        .size(46.dp)
                        .clip(CircleShape)
                        .background(Brush.radialGradient(listOf(Color(0xFF3A3B3D), Color(0xFF0E0F0E))))
                        .border(1.5.dp, Color(0xEBF0F1ED), CircleShape)
                        .rotate(if (snap.playing) angle else 0f),
                    contentAlignment = Alignment.Center,
                ) {
                    val cover = t.cover
                    if (cover != null && File(cover).exists()) {
                        AsyncImage(
                            model = File(cover),
                            contentDescription = null,
                            contentScale = ContentScale.Crop,
                            modifier = Modifier.size(46.dp).clip(CircleShape),
                        )
                    } else {
                        Text("♪", color = Color(0xFF70716D), style = TextStyle(fontSize = 16.sp))
                    }
                }
                Spacer(Modifier.width(12.dp))
                androidx.compose.foundation.layout.Column(Modifier.weight(1f)) {
                    Text(
                        t.title,
                        color = Color.White,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis,
                        style = TextStyle(fontFamily = GadgetFonts.body, fontWeight = FontWeight.W700, fontSize = 14.sp, lineHeight = 16.sp),
                    )
                    Spacer(Modifier.height(3.dp))
                    Text(
                        t.folder.substringAfterLast('/'),
                        color = Color(0xFFCFD0CC),
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis,
                        style = TextStyle(fontFamily = GadgetFonts.body, fontWeight = FontWeight.W400, fontSize = 12.sp, lineHeight = 14.sp),
                    )
                }
                Spacer(Modifier.width(10.dp))
                Orb(
                    size = 44f,
                    tokens = tokens,
                    on = snap.playing,
                    hot = true,
                    onClick = { app.player.toggle() },
                ) {
                    Text(if (snap.playing) "❚❚" else "▶", color = Color(0xFF141414), style = TextStyle(fontSize = 13.sp))
                }
            }

            // `.mini-progress`: 3 px tall, inset 18 px left / 60 px right, 5 px from the bottom.
            val dur = snap.durationMs.coerceAtLeast(1L)
            val ratio = (snap.positionMs.toFloat() / dur).coerceIn(0f, 1f)
            Canvas(
                Modifier.align(Alignment.BottomStart).padding(start = 18.dp, end = 60.dp, bottom = 5.dp)
                    .fillMaxWidth().height(3.dp),
            ) {
                drawRoundRect(color = Color(0x80000000), cornerRadius = androidx.compose.ui.geometry.CornerRadius(size.height / 2, size.height / 2))
                drawRoundRect(
                    brush = Brush.horizontalGradient(listOf(Color(0xFFD6E537), tokens.colors.accent)),
                    cornerRadius = androidx.compose.ui.geometry.CornerRadius(size.height / 2, size.height / 2),
                    size = size.copy(width = size.width * ratio),
                )
            }
        }
    }
}
