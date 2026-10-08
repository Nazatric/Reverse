package com.thegadget.app.data

import kotlinx.serialization.Serializable

/**
 * The `localStorage` document shapes, byte for byte. Keeping the JSON schema identical is what
 * makes the native app a drop-in for the web app's persisted state model — same keys, same
 * defaults, same optional fields.
 */

@Serializable
data class Profile(
    val name: String = "",
    val tagline: String = "",
    val avatar: String? = null,
    val status: String = "online",
    val mascot: String = "grin",
)

@Serializable
data class Playlist(
    val id: String,
    val name: String,
    val ids: List<String> = emptyList(),
)

@Serializable
data class Game(
    val id: String,
    val name: String,
    val category: String = "",
    val url: String,
    val cover: String? = null,
    val favorite: Boolean = false,
    val launches: Int = 0,
    val lastPlayed: Long? = null,
    val added: Long = 0,
)

@Serializable
data class Homie(
    val id: String,
    val name: String,
    val note: String = "",
    val link: String = "",
    val phone: String = "",
    val status: String = "online",
    val avatar: String? = null,
    val added: Long = 0,
    val kind: String = "local",
    val code: String? = null,
)

@Serializable
data class TrackStat(
    val plays: Int = 0,
    val sec: Long = 0,
    val last: Long = 0,
)

/** Per-track persisted stats map, same as the web's `gadget:trackStats`. */
typealias TrackStats = Map<String, TrackStat>
