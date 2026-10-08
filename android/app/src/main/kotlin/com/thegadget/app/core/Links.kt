package com.thegadget.app.core

/** Port of `src/utils/link.ts` — the parts that do not need a browser. */
object Links {
    private val SCHEME = Regex("^[a-z][a-z0-9+.-]*:", RegexOption.IGNORE_CASE)

    /** Add https:// to bare domains; leave real schemes (tel:, steam://, intent://) alone. */
    fun normalise(raw: String): String {
        val v = raw.trim()
        if (v.isEmpty()) return ""
        return if (SCHEME.containsMatchIn(v)) v else "https://$v"
    }

    fun isHttp(url: String): Boolean = Regex("^https?:", RegexOption.IGNORE_CASE).containsMatchIn(url)
}
