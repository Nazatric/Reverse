package com.thegadget.app.core

import kotlin.math.hypot
import kotlin.math.max

/**
 * Port of `src/state/nav.tsx` — the navigation stack and the radial page transition.
 *
 * The web app keeps a real stack and mirrors it into the History API so that the device back
 * gesture always steps exactly one level up (hub → music → album → now playing) and Escape /
 * Back behaves like `history.back()`. Android gets the same behaviour from [NavStack] driven by
 * `BackHandler` + `OnBackInvokedCallback`, so the semantics are identical rather than "close the
 * activity".
 */
sealed interface Route {
    val name: String

    data object Music : Route { override val name = "music" }
    data object Albums : Route { override val name = "albums" }
    data class Album(val id: String) : Route { override val name = "album" }
    data object Songs : Route { override val name = "songs" }
    data object Playlists : Route { override val name = "playlists" }
    data class Playlist(val id: String) : Route { override val name = "playlist" }
    data object Search : Route { override val name = "search" }
    data object Now : Route { override val name = "now" }
    data object Games : Route { override val name = "games" }
    data class Game(val id: String) : Route { override val name = "game" }
    data class GameEdit(val id: String? = null) : Route { override val name = "game-edit" }
    data object Game2048 : Route { override val name = "g2048" }
    data object Homies : Route { override val name = "homies" }
    data class Homie(val id: String) : Route { override val name = "homie" }
    data class HomieEdit(val id: String? = null) : Route { override val name = "homie-edit" }
    data class Chat(val code: String) : Route { override val name = "chat" }
    data object Account : Route { override val name = "account" }
    data object Config : Route { override val name = "config" }
    data object Plugins : Route { override val name = "plugins" }
    data class PluginPage(val id: String) : Route { override val name = "plugin-page" }
    /** An external, user-added web game, shown in an isolated WebView (the one sanctioned WebView use). */
    data class GameBrowser(val url: String, val title: String) : Route { override val name = "game-browser" }
}

/** `routeKey()` — used as the Compose key so a route change animates a fresh view. */
fun Route.key(): String = when (this) {
    is Route.Album -> "album:$id"
    is Route.Playlist -> "playlist:$id"
    is Route.Game -> "game:$id"
    is Route.GameEdit -> "game-edit:${id ?: ""}"
    is Route.Homie -> "homie:$id"
    is Route.HomieEdit -> "homie-edit:${id ?: ""}"
    is Route.Chat -> "chat:$code"
    is Route.PluginPage -> "plugin-page:$id"
    is Route.GameBrowser -> "game-browser:$url"
    else -> name
}

/** Where the iris transition opens from: the hub node the user tapped. */
data class Origin(val x: Float, val y: Float, val r: Float) {
    companion object {
        fun center(width: Float, height: Float) = Origin(width / 2f, height / 2f, 40f)
    }
}

/** The radial reveal, in the reference stage's units (`PageHost` in the web app). */
object Iris {
    const val BASE = 600f

    fun start(originR: Float): Float = max(0.06f, originR / (BASE / 2f))
    fun end(origin: Origin, viewportW: Float, viewportH: Float): Float {
        val far = hypot(
            max(origin.x, viewportW - origin.x).toDouble(),
            max(origin.y, viewportH - origin.y).toDouble(),
        )
        return (far / (BASE / 2.0)).toFloat() * 1.06f
    }
}

/**
 * Immutable snapshot of the navigation state — the same three fields the React context exposes,
 * so the Compose layer can render from one value.
 */
data class NavState(
    val stack: List<Route> = emptyList(),
    val dir: NavDirection = NavDirection.FORWARD,
    val origin: Origin = Origin(0f, 0f, 40f),
) {
    val route: Route? get() = stack.lastOrNull()
    val isEmpty: Boolean get() = stack.isEmpty()
}

enum class NavDirection { FORWARD, BACK }

/**
 * Pure stack transitions. Kept out of the UI so the *behaviour* can be unit tested: pushing from
 * the hub captures the origin, back pops exactly one level, home clears the stack.
 */
object NavStack {
    fun push(state: NavState, route: Route, origin: Origin, fromHub: Boolean): NavState =
        state.copy(
            stack = state.stack + route,
            dir = NavDirection.FORWARD,
            origin = if (fromHub) origin else state.origin,
        )

    fun back(state: NavState): NavState =
        if (state.stack.isEmpty()) state
        else state.copy(stack = state.stack.dropLast(1), dir = NavDirection.BACK)

    fun home(state: NavState): NavState = state.copy(stack = emptyList(), dir = NavDirection.BACK)

    /** The web app resolves any plugin page id as `plugin:<pid>:<page>` or the bare page id. */
    fun resolvePluginPage(id: String, pages: List<Pair<String, String>>): Pair<String, String>? =
        pages.firstOrNull { (pluginId, pageId) -> "plugin:$pluginId:$pageId" == id || pageId == id }
}
