package com.thegadget.app.screens

import android.content.Intent
import android.net.Uri
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextDecoration
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.thegadget.app.core.PluginBlock
import com.thegadget.app.core.PluginPageSpec
import com.thegadget.app.core.Route
import com.thegadget.app.shell.GlassButton
import com.thegadget.app.state.AppState
import com.thegadget.app.ui.theme.GadgetFonts
import com.thegadget.app.ui.theme.GadgetTokens

/**
 * The plugin page renderer. It is driven entirely by the validated manifest ([PluginPageSpec] /
 * [PluginBlock]) — there is no per-plugin code path and no built-in example, exactly as the web
 * app's `PluginPage` walks the same block list.
 */
@Composable
fun ColumnScope.PluginBlocks(app: AppState, tokens: GadgetTokens, blocks: List<PluginBlock>) {
    blocks.forEach { block ->
        when (block) {
            is PluginBlock.Header -> {
                Text(
                    block.value,
                    color = tokens.colors.text,
                    style = TextStyle(
                        fontFamily = GadgetFonts.display, fontWeight = FontWeight.Bold,
                        fontSize = 15.sp, letterSpacing = 1.5.sp,
                    ),
                )
                Spacer(Modifier.height(8.dp))
            }

            is PluginBlock.Text -> {
                Text(block.value, color = tokens.colors.text, style = TextStyle(fontFamily = GadgetFonts.body, fontSize = 14.sp))
                Spacer(Modifier.height(10.dp))
            }

            is PluginBlock.Note -> {
                val shape = RoundedCornerShape(12.dp)
                Text(
                    block.value,
                    color = tokens.colors.dim,
                    style = TextStyle(fontFamily = GadgetFonts.body, fontSize = 13.sp),
                    modifier = Modifier.fillMaxWidth().clip(shape).background(Color(0x14FFFFFF))
                        .border(1.dp, tokens.colors.glassEdge, shape).padding(12.dp),
                )
                Spacer(Modifier.height(10.dp))
            }

            is PluginBlock.Link -> {
                val ctx = LocalContext.current
                Text(
                    block.label,
                    color = tokens.colors.accent,
                    style = TextStyle(fontFamily = GadgetFonts.body, fontSize = 14.sp, textDecoration = TextDecoration.Underline),
                    modifier = Modifier.clickable {
                        runCatching { ctx.startActivity(Intent(Intent.ACTION_VIEW, Uri.parse(block.url))) }
                    },
                )
                Spacer(Modifier.height(10.dp))
            }

            is PluginBlock.Button -> {
                GlassButton(block.label, tokens) {
                    when {
                        block.url != null -> app.push(Route.GameBrowser(block.url, block.label))
                        block.page != null -> app.openNamedPage(block.page)
                    }
                }
                Spacer(Modifier.height(10.dp))
            }

            is PluginBlock.Tiles -> {
                Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    block.items.forEach { item ->
                        val shape = RoundedCornerShape(12.dp)
                        Row(
                            Modifier.fillMaxWidth().clip(shape).background(Color(0x0FFFFFFF))
                                .border(1.dp, tokens.colors.glassEdge, shape)
                                .clickable {
                                    when {
                                        item.url != null -> app.push(Route.GameBrowser(item.url, item.label))
                                        item.page != null -> app.openNamedPage(item.page)
                                    }
                                }
                                .padding(14.dp),
                            verticalAlignment = Alignment.CenterVertically,
                        ) {
                            Text(item.label, color = tokens.colors.text, style = TextStyle(fontFamily = GadgetFonts.body, fontWeight = FontWeight.W600, fontSize = 14.sp))
                            Spacer(Modifier.weight(1f))
                            if (item.icon != null) {
                                Box(Modifier.size(20.dp))
                            }
                        }
                    }
                }
                Spacer(Modifier.height(10.dp))
            }
        }
    }
}

/** Renders one manifest page: its own title/subtitle, then its blocks. */
@Composable
fun PluginPageBody(app: AppState, tokens: GadgetTokens, page: PluginPageSpec) {
    Column(Modifier.fillMaxWidth()) {
        PluginBlocks(app, tokens, page.blocks)
    }
}
