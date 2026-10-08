package com.thegadget.app

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.foundation.gestures.awaitFirstDown
import androidx.activity.enableEdgeToEdge
import androidx.core.view.WindowCompat
import androidx.lifecycle.lifecycleScope
import androidx.lifecycle.viewmodel.compose.viewModel
import com.thegadget.app.core.Clock
import com.thegadget.app.core.Route
import com.thegadget.app.data.Game
import com.thegadget.app.data.Homie
import com.thegadget.app.data.Playlist
import com.thegadget.app.data.Profile
import com.thegadget.app.shell.Root
import com.thegadget.app.state.AppState
import kotlinx.coroutines.launch
import kotlinx.serialization.builtins.ListSerializer
import kotlinx.serialization.json.Json

class MainActivity : ComponentActivity() {

    private val json = Json { ignoreUnknownKeys = true }

    override fun onCreate(savedInstanceState: Bundle?) {
        enableEdgeToEdge()
        super.onCreate(savedInstanceState)
        setContent {
            val app = viewModel<AppState>()
            AppStateHolder.current = app
            val settings by app.settings.collectAsState()

            // `navigator.wakeLock.request('screen')` — the screen stays on while that is enabled.
            LaunchedEffect(settings.keepAwake) {
                if (settings.keepAwake) window.addFlags(android.view.WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON)
                else window.clearFlags(android.view.WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON)
            }
            // `requestFullscreen({navigationUI:'hide'})` on the first tap, when auto-immersive is on.
            var immersive by remember { mutableStateOf(false) }
            val controller = WindowCompat.getInsetsController(window, window.decorView)
            LaunchedEffect(immersive, settings.autoImmersive) {
                if (immersive && settings.autoImmersive) {
                    controller.systemBarsBehavior =
                        androidx.core.view.WindowInsetsControllerCompat.BEHAVIOR_SHOW_TRANSIENT_BARS_BY_SWIPE
                    controller.hide(androidx.core.view.WindowInsetsCompat.Type.systemBars())
                } else {
                    controller.show(androidx.core.view.WindowInsetsCompat.Type.systemBars())
                }
            }
            Box(
                Modifier.fillMaxSize().pointerInput(Unit) {
                    awaitFirstDown(requireUnconsumed = false)
                    if (!immersive) immersive = true
                },
            ) { Root(app) }
        }
        maybeAskForNotifications()
    }

    /** The web asks once, after the first tap; the flag lives in `gadget:notify-asked`. */
    private fun maybeAskForNotifications() {
        if (android.os.Build.VERSION.SDK_INT < 33) return
        if (checkSelfPermission(android.Manifest.permission.POST_NOTIFICATIONS) ==
            android.content.pm.PackageManager.PERMISSION_GRANTED
        ) return
        notifyLauncher.launch(android.Manifest.permission.POST_NOTIFICATIONS)
    }

    private val notifyLauncher =
        registerForActivityResult(androidx.activity.result.contract.ActivityResultContracts.RequestPermission()) { }

    /* ------------------------------------------------------------ parity harness */

    /**
     * Invoked by `ParityScreens.apply` to reproduce, screen for screen, the state the web harness
     * seeds into localStorage plus the frozen clock. It writes the same JSON and then drives the
     * nav stack to the route the web capture used.
     */
    fun parityApply(screen: String) {
        Clock.now = { com.thegadget.app.paritySeedFrozenTime() }
        val app = (application as? GadgetApp)?.let { null } // state lives in the ViewModel
        lifecycleScope.launch {
            seed(app = null)
            navigate(screen)
        }
    }

    private suspend fun seed(app: AppState?) {
        // The ViewModel owns the stores; parity seeding goes through the same DataStore so the
        // flows pick the values up. We obtain it via the last-created AppState hook.
        val state = AppStateHolder.current ?: return
        state.stores.setOnboarded(true)
        state.stores.setProfile(json.decodeFromString(Profile.serializer(), SEED_PROFILE))
        state.stores.setPlaylists(json.decodeFromString(ListSerializer(Playlist.serializer()), SEED_PLAYLISTS))
        state.stores.setGames(json.decodeFromString(ListSerializer(Game.serializer()), SEED_GAMES))
        state.stores.setHomies(json.decodeFromString(ListSerializer(Homie.serializer()), SEED_HOMIES))
    }

    private fun navigate(screen: String) {
        val state = AppStateHolder.current ?: return
        // Reset then push the route the web harness used for this screen.
        state.home()
        val r: List<Route> = when (screen) {
            "music", "music-empty" -> listOf(Route.Music)
            "albums" -> listOf(Route.Music, Route.Albums)
            "album" -> listOf(Route.Music, Route.Albums, Route.Album("chrome"))
            "songs" -> listOf(Route.Music, Route.Songs)
            "playlists" -> listOf(Route.Music, Route.Playlists)
            "playlist" -> listOf(Route.Music, Route.Playlists, Route.Playlist("p1"))
            "search" -> listOf(Route.Music, Route.Search)
            "now-playing" -> listOf(Route.Music, Route.Now)
            "games" -> listOf(Route.Games)
            "game-detail" -> listOf(Route.Games, Route.Game("g1"))
            "game-edit" -> listOf(Route.Games, Route.GameEdit("g1"))
            "g2048" -> listOf(Route.Games, Route.Game2048)
            "homies" -> listOf(Route.Homies)
            "homie-detail" -> listOf(Route.Homies, Route.Homie("h4"))
            "homie-edit" -> listOf(Route.Homies, Route.HomieEdit("h1"))
            "chat" -> listOf(Route.Homies, Route.Chat("hpabc234"))
            "account" -> listOf(Route.Account)
            "config" -> listOf(Route.Config)
            "plugins" -> listOf(Route.Config, Route.Plugins)
            "plugin-page" -> listOf(Route.Config, Route.Plugins, Route.PluginPage("plugin:example.arcade:arcade.page"))
            else -> emptyList()
        }
        for (route in r) state.push(route, fromHub = false)
    }
}

/** Bridges the ViewModel into the parity harness without a DI framework. */
object AppStateHolder {
    var current: AppState? = null
}

internal fun paritySeedFrozenTime(): Long = 1_790_000_400_000L

// The same fixtures as parity/web/capture.mjs SEED_BASE.
private val SEED_PROFILE = """{"name":"naz","tagline":"player one","avatar":null,"status":"online","mascot":"grin"}"""
private val SEED_PLAYLISTS = """[{"id":"p1","name":"late night","ids":[]},{"id":"p2","name":"chrome","ids":[]}]"""
private val SEED_GAMES = """[{"id":"g1","name":"2048","category":"puzzle","url":"https://play2048.co/","cover":null,"favorite":true,"launches":12,"lastPlayed":1759000000000,"added":1758000000000},{"id":"g2","name":"Slither","category":"arcade","url":"https://slither.io/","cover":null,"favorite":false,"launches":4,"lastPlayed":null,"added":1758100000000},{"id":"g3","name":"Krunker","category":"shooter","url":"https://krunker.io/","cover":null,"favorite":false,"launches":1,"lastPlayed":null,"added":1758200000000}]"""
private val SEED_HOMIES = """[{"id":"h1","name":"kaya","note":"neighbour","link":"","phone":"","status":"online","avatar":null,"added":1758000000000,"kind":"local"},{"id":"h2","name":"dee","note":"band","link":"","phone":"","status":"away","avatar":null,"added":1758000001000,"kind":"local"},{"id":"h3","name":"rio","note":"","link":"","phone":"","status":"busy","avatar":null,"added":1758000002000,"kind":"local"},{"id":"h4","name":"sable","note":"linked homie","link":"","phone":"","status":"online","avatar":null,"added":1758000003000,"kind":"linked","code":"hpabc234"}]"""
