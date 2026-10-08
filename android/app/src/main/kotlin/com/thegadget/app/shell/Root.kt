package com.thegadget.app.shell

import androidx.activity.compose.BackHandler
import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.tween
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Rect
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Outline
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.Shape
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.unit.Density
import androidx.compose.ui.unit.LayoutDirection
import androidx.compose.ui.util.lerp
import com.thegadget.app.AppStateHolder
import com.thegadget.app.core.GadgetSettings
import com.thegadget.app.core.Iris
import com.thegadget.app.core.NavDirection
import com.thegadget.app.core.Route
import com.thegadget.app.screens.AccountScreen
import com.thegadget.app.screens.AlbumScreen
import com.thegadget.app.screens.AlbumsScreen
import com.thegadget.app.screens.ChatScreen
import com.thegadget.app.screens.ConfigScreen
import com.thegadget.app.screens.Game2048Screen
import com.thegadget.app.screens.GameBrowserScreen
import com.thegadget.app.screens.GameDetailScreen
import com.thegadget.app.screens.GameEditScreen
import com.thegadget.app.screens.GamesScreen
import com.thegadget.app.screens.HomieDetailScreen
import com.thegadget.app.screens.HomieEditScreen
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
import com.thegadget.app.ui.Fx
import com.thegadget.app.ui.theme.GadgetTokens
import com.thegadget.app.ui.theme.LocalTokens

/** `clip-path: circle(R at X Y)`, with R animated by the caller. */
private fun circleAt(cx: Float, cy: Float, r: Float): Shape = object : Shape {
    override fun createOutline(size: Size, layoutDirection: LayoutDirection, density: Density): Outline =
        Outline.Generic(Path().apply { addOval(Rect(Offset(cx, cy), r)) })
}

/**
 * The activity content: tokens in, chrome + page host out. Back steps the nav stack exactly one
 * level (like the web History mirror); at the hub it exits.
 *
 * Outermost is the ErrorBoundary equivalent — a crash renders the gadget card instead of a blank
 * screen, and "reset data" wipes the persisted state before reloading.
 */
@Composable
fun Root(app: AppState) {
    AppStateHolder.current = app
    val settings by app.settings.collectAsState()
    val onboarded by app.onboarded.collectAsState()
    val crash by app.crash.collectAsState()
    val liveCrash = remember { mutableStateOf<String?>(null) }

    val tokens = GadgetTokens(
        orbScale = settings.orbScale,
        hubScale = settings.hubScale,
        chainScale = settings.chainScale,
        labelScale = settings.labelScale,
        glow = settings.glow,
    )

    if (crash != null || liveCrash.value != null) {
        CrashCard(
            message = liveCrash.value ?: crash ?: "something broke",
            tokens = tokens,
            onReload = {
                liveCrash.value = null
                app.clearCrash()
            },
            onReset = { app.resetAllData { liveCrash.value = null } },
        )
        return
    }

    CompositionLocalProvider(LocalTokens provides tokens) {
        Box(Modifier.fillMaxSize().background(Color.Black)) {
            if (!onboarded) {
                OnboardingScreen(app)
            } else {
                Chrome(app, tokens, settings)
            }
        }
    }
}

@Composable
private fun Chrome(app: AppState, tokens: GadgetTokens, settings: GadgetSettings) {
    val nav by app.nav.collectAsState()
    val activity = LocalContext.current as? android.app.Activity
    BackHandler {
        if (!nav.isEmpty) {
            Fx.close()
            app.back()
        } else {
            activity?.finish()
        }
    }
    BoxWithConstraints(Modifier.fillMaxSize().background(Color.Black)) {
        val density = LocalDensity.current
        val wPx = with(density) { maxWidth.toPx() }
        val hPx = with(density) { maxHeight.toPx() }
        LaunchedEffect(wPx, hPx) { app.setViewport(wPx, hPx) }
        val ctx = LocalContext.current
        LaunchedEffect(settings.sounds, settings.haptics) {
            Fx.init(ctx)
            Fx.configure(settings.sounds, settings.haptics)
        }
        Backdrop(tokens)

        val motion = settings.motionOn
        val o = nav.origin
        // a page entered straight off the hub reveals with the iris; deeper pages surface-in
        val irisOpen = nav.dir == NavDirection.FORWARD && nav.stack.size == 1
        val startPx = Iris.start(o.r) * (Iris.BASE / 2f)
        val endPx = Iris.end(o, wPx, hPx) * (Iris.BASE / 2f)

        // --- enter: iris-open 660ms / surface-in 560ms / view-back 420ms ---
        val enter = remember { Animatable(1f) }
        LaunchedEffect(nav.route, motion) {
            if (!motion || nav.isEmpty) {
                enter.snapTo(1f)
            } else {
                if (nav.dir == NavDirection.FORWARD) Fx.open()
                enter.snapTo(0f)
                val dur = if (irisOpen) 660 else if (nav.dir == NavDirection.BACK) 420 else 560
                enter.animateTo(1f, tween(dur, easing = EaseCurve))
            }
        }

        // --- exit: closing back into the hub keeps the old page mounted for 500ms ---
        val exit = remember { Animatable(1f) }
        val prevRoute = remember { mutableStateOf<Route?>(nav.route) }
        val closing = remember { mutableStateOf<Route?>(null) }
        LaunchedEffect(nav.route, motion) {
            val prev = prevRoute.value
            prevRoute.value = nav.route
            if (motion && prev != null && nav.isEmpty && nav.dir == NavDirection.BACK) {
                closing.value = prev
                exit.snapTo(0f)
                exit.animateTo(1f, tween(500, easing = EaseCurve)) // iris-close
                closing.value = null
            }
        }

        if (nav.isEmpty) {
            HubScreen(app, tokens)
        } else {
            val t = enter.value
            Box(
                Modifier
                    .fillMaxSize()
                    .then(if (irisOpen) Modifier.clip(circleAt(o.x, o.y, lerp(startPx, endPx, t))) else Modifier)
                    .graphicsLayer {
                        when {
                            irisOpen -> alpha = lerp(0.55f, 1f, t)
                            nav.dir == NavDirection.BACK -> {
                                val s = lerp(1.04f, 1f, t)
                                scaleX = s
                                scaleY = s
                                alpha = t
                            }
                            else -> {
                                val s = lerp(0.92f, 1f, t)
                                scaleX = s
                                scaleY = s
                                alpha = t
                            }
                        }
                    }
                    .background(Color.Black.copy(alpha = 0.52f * if (irisOpen) t else 1f)),
            ) { PageHost(app, nav.route!!, tokens) }
        }

        // the page collapsing back into the node it came from
        closing.value?.let { cr ->
            Box(
                Modifier
                    .fillMaxSize()
                    .clip(circleAt(o.x, o.y, lerp(endPx, startPx, exit.value)))
                    .background(Color.Black.copy(alpha = 0.52f)),
            ) { PageHost(app, cr, tokens) }
        }

        MiniPlayer(app, tokens)
        ChromeBar(app, tokens)
        if (settings.y2k) Y2kOverlay()
        ToastHost(app, tokens)
        if (motion && nav.isEmpty) BootOverlay()
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
        is Route.GameBrowser -> GameBrowserScreen(route.url, route.title, tokens)
    }
}
