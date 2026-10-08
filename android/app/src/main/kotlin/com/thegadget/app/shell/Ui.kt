package com.thegadget.app.shell

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.RowScope
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.thegadget.app.ui.theme.GadgetFonts
import com.thegadget.app.ui.theme.GadgetTokens
import com.thegadget.app.ui.theme.orbBrush

/**
 * The shared chrome pieces — `Orb`, `GlassButton`, `Field`, `Empty`, page scaffolding — ported
 * from `src/components/ui`. They compose from the token system so the settings sliders (orb /
 * glow scale) ripple through exactly as they do on the web.
 */

@Composable
fun Orb(
    label: String? = null,
    size: Float,
    tokens: GadgetTokens,
    modifier: Modifier = Modifier,
    on: Boolean = false,
    hot: Boolean = false,
    enabled: Boolean = true,
    onClick: () -> Unit = {},
    content: @Composable (RowScope.() -> Unit)? = null,
) {
    val colors = tokens.colors
    val glow = tokens.glow
    Box(
        modifier = modifier
            .size(size.dp)
            .then(
                if (hot) Modifier.shadow(12.dp * glow, CircleShape, ambientColor = colors.accent, spotColor = colors.accent)
                else Modifier.shadow(6.dp, CircleShape),
            )
            .clip(CircleShape)
            .background(orbBrush(colors.orb, size))
            .border(1.5.dp, if (on) colors.accent else colors.glassEdge, CircleShape)
            .clickable(
                interactionSource = remember { MutableInteractionSource() },
                indication = null,
                enabled = enabled,
                onClick = onClick,
            ),
        contentAlignment = Alignment.Center,
    ) {
        if (content != null) Row(horizontalArrangement = Arrangement.Center, content = content)
    }
}

@Composable
fun GlassButton(
    text: String,
    tokens: GadgetTokens,
    modifier: Modifier = Modifier,
    variant: String = "primary",
    onClick: () -> Unit = {},
) {
    val colors = tokens.colors
    val shape = RoundedCornerShape(999.dp)
    val press: () -> Unit = {
        com.thegadget.app.ui.Fx.tap()
        onClick()
    }
    val bg = when (variant) {
        "primary" -> Brush.verticalGradient(listOf(Color(0xFFF4F4F1), Color(0xFFC9CAC6)))
        else -> Brush.verticalGradient(listOf(colors.glassTop, colors.glassBottom))
    }
    val fg = if (variant == "primary") Color(0xFF141414) else colors.text
    Box(
        modifier = modifier
            .clip(shape)
            .background(bg)
            .border(1.dp, colors.glassEdge, shape)
            .clickable(onClick = press)
            .padding(horizontal = 18.dp, vertical = 10.dp),
        contentAlignment = Alignment.Center,
    ) {
        androidx.compose.material3.Text(
            text,
            color = fg,
            style = TextStyle(fontSize = 13.sp, fontWeight = FontWeight.W700, fontFamily = GadgetFonts.display),
        )
    }
}

@Composable
fun Field(
    value: String,
    onValueChange: (String) -> Unit,
    tokens: GadgetTokens,
    modifier: Modifier = Modifier,
    label: String? = null,
    maxLength: Int = 80,
) {
    val colors = tokens.colors
    Column(modifier = modifier) {
        if (label != null) {
            androidx.compose.material3.Text(
                label,
                color = colors.dim,
                style = TextStyle(fontSize = 10.sp, letterSpacing = 2.sp, fontFamily = GadgetFonts.display, fontWeight = FontWeight.W600),
            )
        }
        BasicTextField(
            value = value,
            onValueChange = { if (it.length <= maxLength) onValueChange(it) },
            singleLine = true,
            textStyle = TextStyle(color = colors.text, fontSize = 14.sp, fontFamily = GadgetFonts.body),
            modifier = Modifier
                .fillMaxWidth()
                .clip(RoundedCornerShape(12.dp))
                .background(Color(0x14FFFFFF))
                .border(1.dp, colors.glassEdge, RoundedCornerShape(12.dp))
                .padding(horizontal = 12.dp, vertical = 10.dp),
        )
    }
}

@Composable
fun Empty(
    icon: String,
    title: String,
    text: String,
    tokens: GadgetTokens,
    modifier: Modifier = Modifier,
) {
    val colors = tokens.colors
    Column(modifier = modifier.padding(24.dp), horizontalAlignment = Alignment.CenterHorizontally, verticalArrangement = Arrangement.spacedBy(10.dp)) {
        androidx.compose.material3.Text(title, color = colors.text, style = TextStyle(fontSize = 20.sp, fontWeight = FontWeight.W800, fontFamily = GadgetFonts.display))
        androidx.compose.material3.Text(text, color = colors.dim, style = TextStyle(fontSize = 13.sp, fontFamily = GadgetFonts.body))
    }
}
