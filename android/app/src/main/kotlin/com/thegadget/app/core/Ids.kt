package com.thegadget.app.core

import kotlin.random.Random

/** The inline id generators the web app uses (`Playlists.tsx`, `Homies.tsx`, `utils/social.ts`). */
object Ids {
    /** `ALPHA` in `utils/social.ts` — no i/l/o/0/1 to keep codes unambiguous. */
    private const val ALPHA = "abcdefghjkmnpqrstuvwxyz23456789"

    /** Playlists and local homies both use `String(Date.now())`. */
    fun timestampId(): String = Clock.now().toString()

    /** `getMyCode()`: `hp` + 6 chars from ALPHA. */
    fun hostCode(): String {
        val s = StringBuilder("hp")
        repeat(6) { s.append(ALPHA[Random.nextInt(ALPHA.length)]) }
        return s.toString()
    }

    /** A linked homie's id is `lnk-${code}`. */
    fun linkedHomieId(code: String): String = "lnk-$code"

    fun normalizeCode(raw: String): String = raw.trim().lowercase().replace(Regex("\\s+"), "")
    fun isValidCode(c: String): Boolean = Regex("^[a-z0-9]{3,16}$").matches(c)
}
