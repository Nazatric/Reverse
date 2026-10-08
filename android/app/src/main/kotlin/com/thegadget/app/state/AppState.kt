package com.thegadget.app.state

import android.app.Application
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import com.thegadget.app.core.NavStack
import com.thegadget.app.core.GadgetMetrics
import com.thegadget.app.core.NavState
import com.thegadget.app.core.Origin
import com.thegadget.app.core.Route
import com.thegadget.app.data.LibraryRepository
import com.thegadget.app.data.MetaDb
import com.thegadget.app.data.Stores
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch

/**
 * One observable app model — the React context (`AppProvider`) as Kotlin. Every screen renders
 * from these flows; nothing keeps a private copy of settings/profile/library state.
 */
class AppState(app: Application) : AndroidViewModel(app) {

    val stores = Stores(app)
    val metaDb = MetaDb.create(app)
    val library = LibraryRepository(app, metaDb)
    val player = PlayerFacade(app)

    val settings = stores.settings.stateIn(viewModelScope, SharingStarted.Eagerly, com.thegadget.app.core.GadgetSettings())
    val profile = stores.profile.stateIn(viewModelScope, SharingStarted.Eagerly, com.thegadget.app.data.Profile())
    val playlists = stores.playlists.stateIn(viewModelScope, SharingStarted.Eagerly, emptyList())
    val games = stores.games.stateIn(viewModelScope, SharingStarted.Eagerly, emptyList())
    val homies = stores.homies.stateIn(viewModelScope, SharingStarted.Eagerly, emptyList())
    val onboarded = stores.onboarded.stateIn(viewModelScope, SharingStarted.Eagerly, false)
    val volume = stores.volume.stateIn(viewModelScope, SharingStarted.Eagerly, 0.8f)
    val best2048 = stores.best2048.stateIn(viewModelScope, SharingStarted.Eagerly, 0)

    private val _nav = kotlinx.coroutines.flow.MutableStateFlow(NavState())
    val nav: StateFlow<NavState> get() = _nav

    init {
        player.connect()
        viewModelScope.launch { library.restore() }
    }

    /* ------------------------------------------------------------ navigation */

    /** Push a route; [origin] is the tapped hub node so the iris opens from it. */
    /** `createPlaylist` — the same random id shape as `utils/id.playlist()`. */
    fun createPlaylist(): String {
        val id = com.thegadget.app.core.Ids.timestampId()
        viewModelScope.launch { stores.addPlaylist(com.thegadget.app.data.Playlist(id, "new playlist")) }
        return id
    }

    fun deletePlaylist(id: String) = viewModelScope.launch { stores.removePlaylist(id) }.let { }

    fun renamePlaylist(id: String, name: String) = viewModelScope.launch {
        stores.setPlaylists(playlists.value.map { if (it.id == id) it.copy(name = name) else it })
    }.let { }

    /* --------------------------------------------------------------- game edits */
    fun markGamePlayed(id: String) = viewModelScope.launch {
        val now = com.thegadget.app.core.Clock.now()
        stores.setGames(games.value.map { if (it.id == id) it.copy(launches = it.launches + 1, lastPlayed = now) else it })
    }.let { }

    fun saveGame(id: String?, name: String, url: String, category: String) = viewModelScope.launch {
        val list = games.value.toMutableList()
        val game = com.thegadget.app.data.Game(
            id = id ?: com.thegadget.app.core.Ids.timestampId(),
            name = name.trim(),
            category = category.trim(),
            url = url.trim(),
            cover = list.firstOrNull { it.id == id }?.cover,
            favorite = list.firstOrNull { it.id == id }?.favorite ?: false,
            launches = list.firstOrNull { it.id == id }?.launches ?: 0,
            lastPlayed = list.firstOrNull { it.id == id }?.lastPlayed,
            added = list.firstOrNull { it.id == id }?.added ?: com.thegadget.app.core.Clock.now(),
        )
        val idx = list.indexOfFirst { it.id == game.id }
        if (idx >= 0) list[idx] = game else list.add(0, game)
        stores.setGames(list)
    }.let { }

    fun deleteGame(id: String) = viewModelScope.launch { stores.setGames(games.value.filter { it.id != id }) }.let { }

    fun setBest2048(v: Int) = viewModelScope.launch { stores.setBest2048(v) }.let { }

    /* -------------------------------------------------------------- homie edits */
    fun saveHomie(id: String?, name: String, note: String, phone: String, status: String) = viewModelScope.launch {
        val list = homies.value.toMutableList()
        val existing = list.firstOrNull { it.id == id }
        val h = com.thegadget.app.data.Homie(
            id = id ?: com.thegadget.app.core.Ids.timestampId(),
            name = name.trim(),
            note = note.trim(),
            link = existing?.link ?: "",
            phone = phone.trim(),
            status = status,
            avatar = existing?.avatar,
            added = existing?.added ?: com.thegadget.app.core.Clock.now(),
            kind = "local",
            code = null,
        )
        val idx = list.indexOfFirst { it.id == h.id }
        if (idx >= 0) list[idx] = h else list.add(h)
        stores.setHomies(list)
    }.let { }

    fun deleteHomie(id: String) = viewModelScope.launch { stores.setHomies(homies.value.filter { it.id != id }) }.let { }

    /** `add` in `Homies.tsx` — validate the code, then link it. Returns the error, if any. */
    fun addLinkedHomie(raw: String): String? {
        val c = com.thegadget.app.core.Ids.normalizeCode(raw)
        if (c == myCode()) return "That's your own code."
        if (!com.thegadget.app.core.Ids.isValidCode(c)) return "Codes look like hp7k2m9a."
        if (homies.value.any { it.code == c }) return "Already linked."
        viewModelScope.launch {
            stores.setHomies(
                homies.value + com.thegadget.app.data.Homie(
                    id = com.thegadget.app.core.Ids.linkedHomieId(c), name = c, note = "", link = "",
                    phone = "", status = "online", avatar = null,
                    added = com.thegadget.app.core.Clock.now(), kind = "linked", code = c,
                ),
            )
        }
        return null
    }

    /** `getMyCode()` — stable per install, generated once and persisted. */
    fun myCode(): String {
        hostCode.value?.let { return it }
        val c = com.thegadget.app.core.Ids.hostCode()
        viewModelScope.launch { stores.setHostCode(c) }
        return c
    }

    /* ------------------------------------------------------------------ profile */
    fun saveProfile(name: String, tagline: String, status: String, mascot: String) = viewModelScope.launch {
        stores.setProfile(profile.value.copy(name = name, tagline = tagline, status = status, mascot = mascot))
    }.let { }

    /* ----------------------------------------------------------------- settings */
    fun updateSetting(name: String, value: Any?) = viewModelScope.launch {
        stores.updateSettings(name, value)
    }.let { }

    /* ------------------------------------------------------------------ plugins */
    fun togglePlugin(id: String) = viewModelScope.launch {
        val s = settings.value
        stores.setSettings(s.copy(plugins = s.plugins.map { if (it.id == id) it.copy(enabled = !it.enabled) else it }))
    }.let { }

    /** `validatePlugins` accepts only plugins that pass `validatePlugin`; the rest surface an error. */
    fun importPlugin(json: String): String? {
        val result = com.thegadget.app.core.parsePlugin(json)
        if (result.errors.isNotEmpty()) return result.errors.first().message
        val p = result.plugins.firstOrNull() ?: return "No plugin in that file."
        viewModelScope.launch {
            val s = settings.value
            stores.setSettings(s.copy(plugins = (s.plugins.filterNot { it.id == p.id }) + p))
        }
        return null
    }

    fun deletePlugin(id: String) = viewModelScope.launch {
        val s = settings.value
        stores.setSettings(s.copy(plugins = s.plugins.filterNot { it.id == id }))
    }.let { }

    fun finishOnboarding() = viewModelScope.launch { stores.setOnboarded(true) }.let { }

    fun resetSettings() = viewModelScope.launch {
        stores.setSettings(com.thegadget.app.core.GadgetSettings())
    }.let { }

    fun push(route: Route, origin: Origin? = null, fromHub: Boolean = false) {
        _nav.value = NavStack.push(_nav.value, route, origin ?: _nav.value.origin, fromHub)
    }

    fun back() {
        _nav.value = NavStack.back(_nav.value)
    }

    fun home() {
        _nav.value = NavStack.home(_nav.value)
    }

    override fun onCleared() {
        player.disconnect()
        super.onCleared()
    }
}
