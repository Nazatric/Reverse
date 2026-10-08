package com.thegadget.app.core

/**
 * The app's single time source. The web app freezes `Date` in the parity harness; Android does the
 * same through this object (see `MainActivity.parityApply`). Everything that shows or stores a
 * time — the status-pill clock, `listenSec` accounting, `lastPlayed` stamps — goes through here.
 */
object Clock {
    var now: () -> Long = { System.currentTimeMillis() }

    fun millis(): Long = now()
}
