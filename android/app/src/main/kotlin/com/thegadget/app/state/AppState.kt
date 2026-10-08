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

    /** Installed plugins, parsed from the stored source with the enabled override applied. */
    val plugins: StateFlow<List<com.thegadget.app.core.PluginRecord>> =
        kotlinx.coroutines.flow.combine(stores.pluginsRaw, stores.pluginEnabled) { raw, enabled ->
            raw.mapNotNull { src -> runCatching { com.thegadget.app.core.PluginSchema.parsePlugin(src) }.getOrNull() }
                .map { it.copy(enabled = enabled[it.id] ?: it.enabled) }
        }.stateIn(viewModelScope, SharingStarted.Eagerly, emptyList())

    /** The viewport in px, measured by the shell; drives the metric system. */
    private val _size = kotlinx.coroutines.flow.MutableStateFlow(390f to 844f)
    fun setViewport(w: Float, h: Float) { _size.value = w to h }
    fun metrics(): GadgetMetrics = GadgetMetrics.compute(_size.value.first, _size.value.second)

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
        val current = plugins.value.firstOrNull { it.id == id }?.enabled ?: true
        stores.setPluginEnabled(id, !current)
    }.let { }

    /** `installPlugin` — validate through [PluginSchema.parsePlugin]; a [PluginError] surfaces its message. */
    fun importPlugin(json: String): String? {
        val record = try {
            com.thegadget.app.core.PluginSchema.parsePlugin(json)
        } catch (e: com.thegadget.app.core.PluginError) {
            return e.message
        }
        viewModelScope.launch {
            val others = stores.pluginsRawOnce().filterNot { src ->
                runCatching { com.thegadget.app.core.PluginSchema.parsePlugin(src).id }.getOrNull() == record.id
            }
            stores.setPluginsRaw(others + json)
        }
        return null
    }

    fun deletePlugin(id: String) = viewModelScope.launch {
        val kept = stores.pluginsRawOnce().filterNot { src ->
            runCatching { com.thegadget.app.core.PluginSchema.parsePlugin(src).id }.getOrNull() == id
        }
        stores.setPluginsRaw(kept)
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
