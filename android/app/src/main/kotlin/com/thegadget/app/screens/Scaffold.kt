package com.thegadget.app.screens

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.thegadget.app.core.GadgetType
import com.thegadget.app.state.AppState
import com.thegadget.app.ui.theme.GadgetFonts
import com.thegadget.app.ui.theme.GadgetTokens

/**
 * A page surface: the shared status pill, a large Orbitron title, an optional subtitle, and
 * scrolling body — the layout every `pages/*` uses. Title/subtitle sizes come from the metric
 * system (`page-title`, `page-sub`), not hardcoded, so they track the viewport like the CSS does.
 */
@Composable
fun Page(
    app: AppState,
    tokens: GadgetTokens,
    title: String,
    modifier: Modifier = Modifier,
    subtitle: String? = null,
    actions: @Composable RowScopeActions.() -> Unit = {},
    content: @Composable ColumnScope.() -> Unit,
) {
    val u = app.metrics().u
    Box(Modifier.fillMaxSize()) {
        Column(Modifier.fillMaxSize().verticalScroll(rememberScrollState())) {
            Spacer(Modifier.height(132.dp))
            Column(Modifier.fillMaxWidth().padding(horizontal = 20.dp)) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Text(
                        title,
                        color = tokens.colors.text,
                        style = TextStyle(
                            fontFamily = GadgetFonts.display,
                            fontWeight = FontWeight.Black,
                            fontSize = GadgetType.pageTitleSize(u).sp,
                        ),
                    )
                    Spacer(Modifier.weight(1f))
                    RowScopeActions.actions()
                }
                if (subtitle != null) {
                    Spacer(Modifier.height(6.dp))
                    Text(
                        subtitle,
                        color = tokens.colors.dim,
                        style = TextStyle(fontFamily = GadgetFonts.body, fontSize = GadgetType.pageSubSize(u).sp),
                    )
                }
                Spacer(Modifier.height(20.dp))
                content()
            }
            Spacer(Modifier.height(160.dp))
        }
    }
}

/** Namespace for the page action slot (a `Row` of buttons, right-aligned). */
object RowScopeActions {
    @Composable
    fun Row(
        modifier: Modifier = Modifier,
        horizontalArrangement: Arrangement.Horizontal = Arrangement.End,
        verticalAlignment: Alignment.Vertical = Alignment.CenterVertically,
        content: @Composable androidx.compose.foundation.layout.RowScope.() -> Unit,
    ) = androidx.compose.foundation.layout.Row(modifier, horizontalArrangement, verticalAlignment, content)
}
