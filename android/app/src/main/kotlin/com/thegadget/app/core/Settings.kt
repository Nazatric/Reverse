package com.thegadget.app.core

/**
 * Port of the settings model in `src/state/app.tsx` + `src/pages/Config.tsx`.
 *
 * The web app stores every preference as JSON in `localStorage` under `gadget:settings`. Android
 * keeps the same keys and the same defaults in DataStore, so a settings screen wired to this model
 * behaves identically — including the fact that turning motion off makes every transition instant
 * (0.001ms) rather than merely faster.
 */
data class GadgetSettings(
    val sounds: Boolean = true,
    val haptics: Boolean = true,
    val hour24: Boolean = false,
    val ambient: Boolean = true,
    val parallax: Boolean = true,
    val chainSway: Boolean = true,
    val reduceMotion: Boolean = false,
    val y2k: Boolean = false,
    val autoImmersive: Boolean = true,
    val notify: Boolean = false,
    val keepAwake: Boolean = true,
    val artwork: Boolean = true,
    val orbScale: Float = 1f,
    val hubScale: Float = 1f,
    val chainScale: Float = 1f,
    val labelScale: Float = 1f,
    val glow: Float = 1f,
) {
    /** `data-motion`, `data-ambient`, `data-sway`, `data-y2k`, `data-art` on <html>. */
    val motionOn: Boolean get() = !reduceMotion
    val ambientOn: Boolean get() = ambient && !reduceMotion
    val swayOn: Boolean get() = chainSway && !reduceMotion
    val artOn: Boolean get() = artwork

    fun with(key: String, value: Float): GadgetSettings = when (key) {
        "orbScale" -> copy(orbScale = value.coerceIn(SCALE_MIN, SCALE_MAX))
        "hubScale" -> copy(hubScale = value.coerceIn(SCALE_MIN, SCALE_MAX))
        "chainScale" -> copy(chainScale = value.coerceIn(SCALE_MIN, SCALE_MAX))
        "labelScale" -> copy(labelScale = value.coerceIn(SCALE_MIN, SCALE_MAX))
        "glow" -> copy(glow = value.coerceIn(GLOW_MIN, GLOW_MAX))
        else -> this
    }

    companion object {
        const val SCALE_MIN = 0.7f
        const val SCALE_MAX = 1.5f
        const val GLOW_MIN = 0.3f
        const val GLOW_MAX = 1.8f

        /** The five appearance sliders, in the order Config lists them. */
        val SCALE_KEYS = listOf("orbScale", "hubScale", "chainScale", "labelScale", "glow")

        /** `reset` in Config puts every slider back to 1. */
        fun reset(): GadgetSettings = GadgetSettings()
    }
}

/**
 * The `gadget:*` storage map. IndexedDB (`gadget-handles/handles`) becomes Room on Android; the
 * rest are DataStore keys. Keeping the mapping in one place documents what a data migration has to
 * move and lets the tests assert the key strings never drift from the web app's.
 */
object StorageKeys {
    const val PREFIX = "gadget:"
    const val PROFILE = "gadget:profile"
    const val SETTINGS = "gadget:settings"
    const val GAMES = "gadget:games"
    const val HOMIES = "gadget:homies"
    const val PLAYLISTS = "gadget:playlists"
    const val VOLUME = "gadget:volume"
    const val LISTEN_SEC = "gadget:listenSec"
    const val TRACK_STATS = "gadget:trackStats"
    const val LAST_TRACK = "gadget:last"
    const val BEST_2048 = "gadget:best2048"
    const val HOST_CODE = "gadget:hostcode"
    const val ONBOARDED = "gadget:onboarded"
    const val NOTIFY_ASKED = "gadget:notify-asked"
    const val CRASH = "gadget:crash"

    /** IndexedDB database/store/record in the web app → a Room table on Android. */
    const val HANDLE_DB = "gadget-handles"
    const val HANDLE_STORE = "handles"
    const val HANDLE_KEY = "music-folder"
}
