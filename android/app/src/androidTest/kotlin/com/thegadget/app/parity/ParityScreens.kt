package com.thegadget.app.parity

import android.app.Activity
import com.thegadget.app.MainActivity

/**
 * The screen list and the state each one needs — the exact mirror of `SCREENS` and `SEED_BASE` in
 * `parity/web/capture.mjs`. Keeping the names identical is what lets `compare.mjs` line up
 * `parity/out/web/<viewport>/hub.png` with `parity/out/native/<viewport>/hub.png`.
 */
object ParityScreens {
    const val FROZEN_TIME_MS = 1_790_000_400_000L // 2026-10-07T16:20:00Z, same instant as the web run

    /**
     * Every screen the web harness knows about. The route string is the app's own route key
     * (`Route.key()`), so a screen is reproducible from the same stack the web harness pushes.
     */
    val ALL = listOf(
        "onboarding",
        "hub",
        "hub-playing",
        "playlist",
        "hub-theme",
        "music-empty",
        "music",
        "albums",
        "album",
        "songs",
        "playlists",
        "search",
        "now-playing",
        "games",
        "game-detail",
        "game-edit",
        "game-browser",
        "g2048",
        "homies",
        "homie-detail",
        "homie-edit",
        "chat",
        "account",
        "config",
        "plugins",
        "plugin-page",
    )

    /**
     * Seeds the app for one screen. The seed data is deliberately the same fixture set as the web
     * harness: 6 songs across 2 folders (Chrome Dreams / Late Night), 3 games, 4 homies (one
     * linked with code `hpabc234`), an installed plugin record and host code `hpk7m2x9`.
     */
    fun apply(activity: Activity, screen: String) {
        val main = activity as MainActivity
        main.parityApply(screen)
    }
}
