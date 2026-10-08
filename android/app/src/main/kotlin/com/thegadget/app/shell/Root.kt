package com.thegadget.app.shell

import androidx.activity.compose.BackHandler
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import com.thegadget.app.AppStateHolder
import com.thegadget.app.core.GadgetMetrics
import com.thegadget.app.core.Route
import com.thegadget.app.screens.AccountScreen
import com.thegadget.app.screens.AlbumScreen
import com.thegadget.app.screens.AlbumsScreen
import com.thegadget.app.screens.ChatScreen
import com.thegadget.app.screens.ConfigScreen
import com.thegadget.app.screens.Game2048Screen
import com.thegadget.app.screens.GameBrowserScreen
import com.thegadget.app.screens.GameEditScreen
import com.thegadget.app.screens.GameDetailScreen
import com.thegadget.app.screens.GamesScreen
import com.thegadget.app.screens.HomieEditScreen
import com.thegadget.app.screens.HomieDetailScreen
import com.thegadget.app.screens.HomiesScreen
import com.thegadget.app.screens.MusicHomeScreen
import com.thegadget.app.screens.NowPlayingScreen
import com.thegadget.app.screens.OnboardingScreen
import com.thegadget.app.screens.PlaylistScreen
import com.thegadget.app.screens.PlaylistsScreen
import com.thegadget.app.screens.PluginPageScreen
import com.thegadget.app.screens.PluginsScreen
import com.thegadget.app.screens.SearchScreen
import com.thegadget.app.screens.SongsScreen
import com.thegadget.app.state.AppState
import com.thegadget.app.ui.theme.GadgetTokens
import com.thegadget.app.ui.theme.LocalTokens

/**
 * The activity content: tokens in, chrome + page host out. Back steps the nav stack exactly one
 * level (like the web History mirror); at the hub it exits.
 */
@Composable
fun Root(app: AppState) {
    AppStateHolder.current = app
    val settings by app.settings.collectAsState()
    val onboarded by app.onboarded.collectAsState()

    val tokens = GadgetTokens(
        orbScale = settings.orbScale,
        hubScale = settings.hubScale,
        chainScale = settings.chainScale,
        labelScale = settings.labelScale,
        glow = settings.glow,
    )

    CompositionLocalProvider(LocalTokens provides tokens) {
        Box(Modifier.fillMaxSize().background(Color.Black)) {
            if (!onboarded) {
                OnboardingScreen(app)
            } else {
                Chrome(app, tokens)
            }
        }
    }
}

@Composable
private fun Chrome(app: AppState, tokens: GadgetTokens) {
    val nav by app.nav.collectAsState()
    val activity = LocalContext.current as? android.app.Activity
    BackHandler {
        if (!nav.isEmpty) app.back() else activity?.finish()
    }
    BoxWithConstraints(Modifier.fillMaxSize().background(Color.Black)) {
        val density = LocalDensity.current
        val wPx = with(density) { maxWidth.toPx() }
        val hPx = with(density) { maxHeight.toPx() }
        LaunchedEffect(wPx, hPx) { app.setViewport(wPx, hPx) }
        Backdrop(tokens)
        if (nav.isEmpty) {
            HubScreen(app, tokens)
        } else {
            PageHost(app, nav.route!!, tokens)
        }
        ChromeBar(app, tokens)
    }
}

@Composable
fun PageHost(app: AppState, route: Route, tokens: GadgetTokens) {
    when (route) {
        is Route.Music -> MusicHomeScreen(app, tokens)
        is Route.Albums -> AlbumsScreen(app, tokens)
        is Route.Album -> AlbumScreen(app, route.id, tokens)
        is Route.Songs -> SongsScreen(app, tokens)
        is Route.Playlists -> PlaylistsScreen(app, tokens)
        is Route.Playlist -> PlaylistScreen(app, route.id, tokens)
        is Route.Search -> SearchScreen(app, tokens)
        is Route.Now -> NowPlayingScreen(app, tokens)
        is Route.Games -> GamesScreen(app, tokens)
        is Route.Game -> GameDetailScreen(app, route.id, tokens)
        is Route.GameEdit -> GameEditScreen(app, route.id, tokens)
        is Route.Game2048 -> Game2048Screen(app, tokens)
        is Route.Homies -> HomiesScreen(app, tokens)
        is Route.Homie -> HomieDetailScreen(app, route.id, tokens)
        is Route.HomieEdit -> HomieEditScreen(app, route.id, tokens)
        is Route.Chat -> ChatScreen(app, route.code, tokens)
        is Route.Account -> AccountScreen(app, tokens)
        is Route.Config -> ConfigScreen(app, tokens)
        is Route.Plugins -> PluginsScreen(app, tokens)
        is Route.PluginPage -> PluginPageScreen(app, route.id, tokens)
    }
}
