package com.thegadget.app.data

import android.content.Context
import androidx.datastore.preferences.core.booleanPreferencesKey
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.floatPreferencesKey
import androidx.datastore.preferences.core.intPreferencesKey
import androidx.datastore.preferences.core.longPreferencesKey
import androidx.datastore.preferences.core.stringPreferencesKey
import androidx.datastore.preferences.preferencesDataStore
import com.thegadget.app.core.GadgetSettings
import com.thegadget.app.core.StorageKeys
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map
import kotlinx.serialization.builtins.MapSerializer
import kotlinx.serialization.builtins.serializer
import kotlinx.serialization.json.Json as KJson

private val Context.dataStore by preferencesDataStore(name = "gadget")

/**
 * The native mirror of the web `localStorage` map (`gadget:*` keys). Every value is stored under
 * the exact same logical key so the two implementations agree on what "persisted" means, and the
 * defaults match `DEFAULT_*` in `src/state/app.tsx`.
 */
class Stores(private val context: Context) {

    private val kjson = KJson { ignoreUnknownKeys = true; encodeDefaults = true }

    /* ---------------------------------------------------------- profile / settings */

    val profile: Flow<Profile> = context.dataStore.data.map { it[stringPreferencesKey(StorageKeys.PROFILE)]?.let { s -> runCatching { kjson.decodeFromString<Profile>(s) }.getOrDefault(Profile()) } ?: Profile() }

    suspend fun setProfile(p: Profile) {
        context.dataStore.edit { it[stringPreferencesKey(StorageKeys.PROFILE)] = kjson.encodeToString(Profile.serializer(), p) }
    }

    val settings: Flow<GadgetSettings> = context.dataStore.data.map { raw ->
        raw[stringPreferencesKey(StorageKeys.SETTINGS)]?.let { s ->
            // Partial documents decode field-by-field; anything absent keeps its web default.
            runCatching { kjson.decodeFromString<GadgetSettingsDto>(s).toModel() }.getOrDefault(GadgetSettings())
        } ?: GadgetSettings()
    }

    suspend fun setSettings(s: GadgetSettings) {
        context.dataStore.edit { it[stringPreferencesKey(StorageKeys.SETTINGS)] = kjson.encodeToString(GadgetSettingsDto.serializer(), GadgetSettingsDto(s)) }
    }

    /* ---------------------------------------------------------- lists */

    val playlists: Flow<List<Playlist>> = list(StorageKeys.PLAYLISTS, kotlinx.serialization.builtins.ListSerializer(Playlist.serializer()))
    val games: Flow<List<Game>> = list(StorageKeys.GAMES, kotlinx.serialization.builtins.ListSerializer(Game.serializer()))
    val homies: Flow<List<Homie>> = list(StorageKeys.HOMIES, kotlinx.serialization.builtins.ListSerializer(Homie.serializer()))

    suspend fun setPlaylists(v: List<Playlist>) = putList(StorageKeys.PLAYLISTS, kotlinx.serialization.builtins.ListSerializer(Playlist.serializer()), v)
    suspend fun setGames(v: List<Game>) = putList(StorageKeys.GAMES, kotlinx.serialization.builtins.ListSerializer(Game.serializer()), v)
    suspend fun setHomies(v: List<Homie>) = putList(StorageKeys.HOMIES, kotlinx.serialization.builtins.ListSerializer(Homie.serializer()), v)

    /* ---------------------------------------------------------- scalars */

    val volume: Flow<Float> = context.dataStore.data.map { it[floatPreferencesKey(StorageKeys.VOLUME)] ?: 0.8f }
    suspend fun setVolume(v: Float) = context.dataStore.edit { it[floatPreferencesKey(StorageKeys.VOLUME)] = v.coerceIn(0f, 1f) }

    val listenSec: Flow<Long> = context.dataStore.data.map { it[longPreferencesKey(StorageKeys.LISTEN_SEC)] ?: 0L }
    suspend fun setListenSec(v: Long) = context.dataStore.edit { it[longPreferencesKey(StorageKeys.LISTEN_SEC)] = v }

    val trackStats: Flow<TrackStats> = context.dataStore.data.map {
        it[stringPreferencesKey(StorageKeys.TRACK_STATS)]?.let { s -> runCatching { kjson.decodeFromString<TrackStats>(s) }.getOrDefault(emptyMap()) } ?: emptyMap()
    }
    suspend fun setTrackStats(v: TrackStats) = context.dataStore.edit { it[stringPreferencesKey(StorageKeys.TRACK_STATS)] = kjson.encodeToString(MapSerializer(String.serializer(), TrackStat.serializer()), v) }

    val lastTrack: Flow<String?> = context.dataStore.data.map { it[stringPreferencesKey(StorageKeys.LAST_TRACK)] }
    suspend fun setLastTrack(id: String?) = context.dataStore.edit { if (id == null) it.remove(stringPreferencesKey(StorageKeys.LAST_TRACK)) else it[stringPreferencesKey(StorageKeys.LAST_TRACK)] = id }

    val best2048: Flow<Int> = context.dataStore.data.map { it[intPreferencesKey(StorageKeys.BEST_2048)] ?: 0 }
    suspend fun setBest2048(v: Int) = context.dataStore.edit { it[intPreferencesKey(StorageKeys.BEST_2048)] = v }

    val hostCode: Flow<String?> = context.dataStore.data.map { it[stringPreferencesKey(StorageKeys.HOST_CODE)] }
    suspend fun setHostCode(v: String?) = context.dataStore.edit { if (v == null) it.remove(stringPreferencesKey(StorageKeys.HOST_CODE)) else it[stringPreferencesKey(StorageKeys.HOST_CODE)] = v }

    val onboarded: Flow<Boolean> = context.dataStore.data.map { it[booleanPreferencesKey(StorageKeys.ONBOARDED)] ?: false }
    suspend fun setOnboarded(v: Boolean) = context.dataStore.edit { it[booleanPreferencesKey(StorageKeys.ONBOARDED)] = v }

    val notifyAsked: Flow<Boolean> = context.dataStore.data.map { it[booleanPreferencesKey(StorageKeys.NOTIFY_ASKED)] ?: false }
    suspend fun setNotifyAsked(v: Boolean) = context.dataStore.edit { it[booleanPreferencesKey(StorageKeys.NOTIFY_ASKED)] = v }

    val crash: Flow<String?> = context.dataStore.data.map { it[stringPreferencesKey(StorageKeys.CRASH)] }
    suspend fun setCrash(v: String?) = context.dataStore.edit { if (v == null) it.remove(stringPreferencesKey(StorageKeys.CRASH)) else it[stringPreferencesKey(StorageKeys.CRASH)] = v }

    /* ---------------------------------------------------------- helpers */

    private fun <T> list(key: String, ser: kotlinx.serialization.KSerializer<List<T>>): Flow<List<T>> =
        context.dataStore.data.map { it[stringPreferencesKey(key)]?.let { s -> runCatching { kjson.decodeFromString(ser, s) }.getOrDefault(emptyList()) } ?: emptyList() }

    private suspend fun <T> putList(key: String, ser: kotlinx.serialization.KSerializer<List<T>>, v: List<T>) =
        context.dataStore.edit { it[stringPreferencesKey(key)] = kjson.encodeToString(ser, v) }

}

@kotlinx.serialization.Serializable
private data class GadgetSettingsDto(
    val sounds: Boolean = true, val haptics: Boolean = true, val hour24: Boolean = false,
    val ambient: Boolean = true, val parallax: Boolean = true, val chainSway: Boolean = true,
    val reduceMotion: Boolean = false, val y2k: Boolean = false, val autoImmersive: Boolean = true,
    val notify: Boolean = false, val keepAwake: Boolean = true, val artwork: Boolean = true,
    val orbScale: Float = 1f, val hubScale: Float = 1f, val chainScale: Float = 1f,
    val labelScale: Float = 1f, val glow: Float = 1f,
) {
    constructor(s: GadgetSettings) : this(
        s.sounds, s.haptics, s.hour24, s.ambient, s.parallax, s.chainSway, s.reduceMotion, s.y2k,
        s.autoImmersive, s.notify, s.keepAwake, s.artwork, s.orbScale, s.hubScale, s.chainScale,
        s.labelScale, s.glow,
    )

    fun toModel(): GadgetSettings = GadgetSettings(
        sounds = sounds, haptics = haptics, hour24 = hour24, ambient = ambient, parallax = parallax,
        chainSway = chainSway, reduceMotion = reduceMotion, y2k = y2k, autoImmersive = autoImmersive,
        notify = notify, keepAwake = keepAwake, artwork = artwork, orbScale = orbScale,
        hubScale = hubScale, chainScale = chainScale, labelScale = labelScale, glow = glow,
    )
}
