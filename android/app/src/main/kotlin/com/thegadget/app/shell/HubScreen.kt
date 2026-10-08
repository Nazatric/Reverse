package com.thegadget.app.shell

import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.unit.dp
import com.thegadget.app.core.GadgetMetrics
import com.thegadget.app.core.HubNodeSpec
import com.thegadget.app.core.Route
import com.thegadget.app.ui.theme.GadgetTokens
import com.thegadget.app.state.AppState

/**
 * The hub page. It measures the viewport, builds the stage metrics the web app would compute for
 * this size, then draws the orb/chain cluster. Tapping a node pushes its route with the node's
 * centre as the iris origin — exactly what `app.tsx` `openRoute` records.
 */
@Composable
fun HubScreen(app: AppState, tokens: GadgetTokens, modifier: Modifier = Modifier) {
    val settings by app.settings.collectAsState()
    BoxWithConstraints(modifier.fillMaxSize()) {
        val density = LocalDensity.current
        val wPx = with(density) { maxWidth.toPx() }
        val hPx = with(density) { maxHeight.toPx() }
        val metrics = remember(wPx, hPx) { GadgetMetrics.compute(wPx, hPx) }
        val geometry = remember(metrics, tokens) { hubGeometry(metrics, tokens) }
        val pluginNodes = remember(settings.plugins) {
            settings.plugins.filter { it.kind == "hub" }.mapIndexed { i, p ->
                HubNodeSpec(
                    id = com.thegadget.app.core.HubNodeId.MUSIC,
                    label = p.title,
                    x = 30f + i * 10f,
                    y = 50f + i * 6f,
                    d = 90f,
                    ly = 16f,
                    icon = p.icon.ifEmpty { "puzzle" },
                    page = p.id,
                    pluginId = p.id,
                )
            }
        }
        Hub(
            tokens = tokens,
            geometry = geometry,
            pluginNodes = pluginNodes,
            onNodeTap = { spec, center, size ->
                val route = if (spec.pluginId != null) Route.PluginPage(spec.pluginId!!) else when (spec.id) {
                    com.thegadget.app.core.HubNodeId.MUSIC -> Route.Music
                    com.thegadget.app.core.HubNodeId.GAMES -> Route.Games
                    com.thegadget.app.core.HubNodeId.CONFIG -> Route.Config
                    com.thegadget.app.core.HubNodeId.HOMIES -> Route.Homies
                    com.thegadget.app.core.HubNodeId.ACCOUNT -> Route.Account
                }
                app.push(route, origin = com.thegadget.app.core.Origin(center.x, center.y), fromHub = true)
            },
        )
    }
}
