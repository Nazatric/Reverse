package com.thegadget.app.core

import java.text.Normalizer

/**
 * Library model — `utils/musicLibrary.ts`, `utils/albums.ts`, `utils/stats.ts`.
 *
 * Sorting in the web app uses `localeCompare(..., { numeric: true })`, i.e. ICU collation with
 * numeric ordering ("Track 2" before "Track 10"). Android exposes the very same ICU through
 * `android.icu.text.Collator`, so the native build installs an ICU-backed [TextCollator] at
 * startup; this file also ships a deterministic fallback so the model is unit-testable on a plain
 * JVM and never changes behaviour when the locale has no ICU data.
 */
interface TextCollator {
    fun compare(a: String, b: String): Int
}

/**
 * Deterministic, locale-independent, numeric-aware comparison with the same *observable* behaviour
 * as `localeCompare(undefined, {numeric:true})` for the ASCII titles and folder names a music
 * library contains: case-insensitive at primary strength, digits compared as numbers, then a
 * case-sensitive tiebreak so results are stable.
 */
object SimpleNumericCollator : TextCollator {
    /**
     * Four passes, in the order ICU compares them:
     *   1. primary   — letters (accents folded) and whole numbers, punctuation ignored
     *   2. secondary — accents (plain before accented)
     *   3. tertiary  — case (upper-case first, as ICU's en-US root does)
     *   4. quaternary— the ignored punctuation itself, so "apple" sorts before "?pple"
     * This reproduces every ordering the reference vectors contain; the Android layer swaps in
     * `android.icu.text.Collator` with numeric collation for the general case.
     */
    override fun compare(a: String, b: String): Int {
        val primary = comparePrimary(a, b)
        if (primary != 0) return primary
        val secondary = compareSecondary(a, b)
        if (secondary != 0) return secondary
        val tertiary = compareTertiary(a, b)
        if (tertiary != 0) return tertiary
        return ignorable(a) - ignorable(b)
    }

    private fun isDigit(c: Char) = c in '0'..'9'
    private fun isIgnorable(c: Char) = !c.isLetterOrDigit()

    private fun comparePrimary(a: String, b: String): Int {
        var i = 0
        var j = 0
        while (true) {
            while (i < a.length && isIgnorable(a[i])) i++
            while (j < b.length && isIgnorable(b[j])) j++
            if (i >= a.length || j >= b.length) break
            val ca = a[i]
            val cb = b[j]
            if (isDigit(ca) && isDigit(cb)) {
                var ni = i
                while (ni < a.length && isDigit(a[ni])) ni++
                var nj = j
                while (nj < b.length && isDigit(b[nj])) nj++
                val da = a.substring(i, ni).trimStart('0').ifEmpty { "0" }
                val db = b.substring(j, nj).trimStart('0').ifEmpty { "0" }
                if (da.length != db.length) return da.length - db.length
                val digits = da.compareTo(db)
                if (digits != 0) return digits
                if (ni - i != nj - j) return (ni - i) - (nj - j)
                i = ni
                j = nj
                continue
            }
            val fa = fold(ca)
            val fb = fold(cb)
            if (fa != fb) return fa.compareTo(fb)
            if (isDigit(ca) != isDigit(cb)) return if (isDigit(ca)) -1 else 1
            i++
            j++
        }
        val ra = a.substring(i).count { !isIgnorable(it) }
        val rb = b.substring(j).count { !isIgnorable(it) }
        return ra - rb
    }

    private fun compareSecondary(a: String, b: String): Int {
        val wa = a.filter { it.isLetterOrDigit() }
        val wb = b.filter { it.isLetterOrDigit() }
        for (k in 0 until minOf(wa.length, wb.length)) {
            val d = accent(wa[k]).compareTo(accent(wb[k]))
            if (d != 0) return d
        }
        return wa.length - wb.length
    }

    private fun compareTertiary(a: String, b: String): Int {
        val wa = a.filter { it.isLetterOrDigit() }
        val wb = b.filter { it.isLetterOrDigit() }
        for (k in 0 until minOf(wa.length, wb.length)) {
            val ca = wa[k]
            val cb = wb[k]
            val va = if (ca.isUpperCase()) 0 else 1
            val vb = if (cb.isUpperCase()) 0 else 1
            if (va != vb) return va - vb
        }
        return 0
    }

    private fun ignorable(s: String) = s.count { isIgnorable(it) }

    /** 0 = plain letter, 1 = accented. */
    private fun accent(c: Char): Int {
        val lower = c.lowercaseChar()
        return if (lower in ACCENTED) 1 else 0
    }

    private val ACCENTED = "áàâäãåéèêëíìîïóòôöõúùûüñçýÿ".toSet()

    private fun fold(c: Char): Char {
        val lower = c.lowercaseChar()
        return when (lower) {
            in ACCENTED -> when (lower) {
                'á', 'à', 'â', 'ä', 'ã', 'å' -> 'a'
                'é', 'è', 'ê', 'ë' -> 'e'
                'í', 'ì', 'î', 'ï' -> 'i'
                'ó', 'ò', 'ô', 'ö', 'õ' -> 'o'
                'ú', 'ù', 'û', 'ü' -> 'u'
                'ñ' -> 'n'
                'ç' -> 'c'
                'ý', 'ÿ' -> 'y'
                else -> lower
            }
            else -> lower
        }
    }
}

/** Installed by the Android layer (ICU). */
object Collators {
    var text: TextCollator = SimpleNumericCollator
    var numeric: TextCollator = SimpleNumericCollator
}

data class Track(
    val id: String,
    val title: String,
    /** parent folder path — doubles as the album when a file has no album tag */
    val folder: String,
    /** folder artwork (cover.jpg / front.png …) used when the file itself has no embedded art */
    var cover: String? = null,
    /** content:// URI of the audio file */
    val uri: String,
)

data class TrackMeta(
    val title: String? = null,
    val artist: String? = null,
    val album: String? = null,
    val coverUri: String? = null,
)

data class Album(
    val id: String,
    val name: String,
    /** Mutable: like `groupAlbums` in the web app, the first non-empty artist/cover wins as
     *  tracks are folded in. */
    var artist: String,
    var cover: String?,
    val tracks: MutableList<Track>,
)

object Library {

    val AUDIO_EXT = Regex("\\.(mp3|m4a|aac|flac|wav|ogg|oga|opus|weba|webm|aif|aiff)$", RegexOption.IGNORE_CASE)
    val IMAGE_EXT = Regex("\\.(jpe?g|png|webp|gif)$", RegexOption.IGNORE_CASE)
    /** recognised by every media player as album artwork */
    val ART_NAME = Regex(
        "^(cover|front|folder|album|art|artwork|thumbnail|albumart)[^/]*\\.(jpe?g|png|webp)$",
        RegexOption.IGNORE_CASE,
    )
    const val MAX_DEPTH = 6

    fun artRank(name: String): Int {
        val n = name.lowercase()
        if (n.startsWith("cover") || n.startsWith("front")) return 0
        if (n.startsWith("folder") || n.startsWith("album")) return 1
        return 2
    }

    /** `sortTracks` — folder first, then numeric title. */
    fun sortTracks(tracks: MutableList<Track>) {
        tracks.sortWith { a, b ->
            val byFolder = Collators.text.compare(a.folder, b.folder)
            if (byFolder != 0) byFolder else Collators.numeric.compare(a.title, b.title)
        }
    }

    val byTitle: Comparator<Track> = Comparator { a, b -> Collators.numeric.compare(a.title, b.title) }

    /** `letterOf` — NFD, strip combining marks, uppercase, `[A-Z]` or `#`. */
    fun letterOf(title: String): String {
        val normalized = Normalizer.normalize(title.trim(), Normalizer.Form.NFD)
            .replace(Regex("\\p{Mn}+"), "")
        val c = normalized.firstOrNull()?.uppercaseChar() ?: return "#"
        return if (c in 'A'..'Z') c.toString() else "#"
    }

    val LETTERS: List<String> = listOf("#") + ('A'..'Z').map { it.toString() }

    /** `groupAlbums` — album tag (trimmed) or the folder; sorted numerically by name. */
    fun groupAlbums(tracks: List<Track>, meta: (String) -> TrackMeta?): List<Album> {
        val map = LinkedHashMap<String, Album>()
        for (t in tracks) {
            val m = meta(t.id)
            val albumTag = m?.album?.trim().orEmpty()
            val key = albumTag.ifEmpty { t.folder }
            val album = map.getOrPut(key) {
                Album(
                    id = key,
                    name = key.split("/").lastOrNull() ?: key,
                    artist = m?.artist ?: "",
                    cover = null,
                    tracks = mutableListOf(),
                )
            }
            if (album.artist.isEmpty() && !m?.artist.isNullOrEmpty()) album.artist = m!!.artist!!
            if (album.cover == null) album.cover = m?.coverUri ?: t.cover
            album.tracks.add(t)
        }
        return map.values.sortedWith { a, b -> Collators.numeric.compare(a.name, b.name) }
    }

    /** `topTracks` — entries ≥ 30 s, top [limit]. */
    fun topTracks(library: List<Track>, stats: Map<String, Double>, limit: Int = 3): List<Pair<String, Double>> =
        stats.entries
            .filter { it.value >= 30 }
            .sortedByDescending { it.value }
            .take(limit)
            .map { (id, sec) -> (library.firstOrNull { it.id == id }?.title ?: "song") to sec }

    /** `utils/link.normaliseLink` — add https:// to bare domains, keep real schemes. */
    fun normaliseLink(raw: String): String {
        val v = raw.trim()
        if (v.isEmpty()) return ""
        return if (Regex("^[a-z][a-z0-9+.-]*:", RegexOption.IGNORE_CASE).containsMatchIn(v)) v else "https://$v"
    }

    fun isWebUrl(url: String): Boolean = Regex("^https?:", RegexOption.IGNORE_CASE).containsMatchIn(url)

    fun isHttpUrl(url: String): Boolean = Regex("^https?://", RegexOption.IGNORE_CASE).containsMatchIn(url)

    fun hostOf(url: String): String = runCatching { java.net.URI(url).host }.getOrNull()?.takeIf { it.isNotEmpty() } ?: url
}

/** Crop-to-fill maths used by `imageToDataUrl` (cover art 360×450, avatars 240×240). */
object ImageMath {
    data class Placement(val left: Float, val top: Float, val width: Float, val height: Float)

    fun cropToFill(srcW: Float, srcH: Float, dstW: Float, dstH: Float): Placement {
        if (srcW <= 0f || srcH <= 0f) return Placement(0f, 0f, dstW, dstH)
        val scale = maxOf(dstW / srcW, dstH / srcH)
        val dw = srcW * scale
        val dh = srcH * scale
        return Placement((dstW - dw) / 2f, (dstH - dh) / 2f, dw, dh)
    }
}

/** Host codes, topic names and chat ids — `utils/social.ts`. */
object SocialCode {
    const val ROOT = "gadget/v1"
    val BROKERS = listOf("wss://broker.hivemq.com:8884/mqtt", "wss://broker.emqx.io:8084/mqtt")
    const val ALPHA = "abcdefghjkmnpqrstuvwxyz23456789"
    val CODE_RE = Regex("^[a-z0-9]{3,16}$")

    fun generate(random: RandomSource): String {
        val sb = StringBuilder("hp")
        repeat(6) { sb.append(ALPHA[(random.nextDouble() * ALPHA.length).toInt().coerceIn(0, ALPHA.length - 1)]) }
        return sb.toString()
    }

    fun normalize(raw: String): String = raw.trim().lowercase().replace(Regex("\\s+"), "")

    fun isValid(code: String): Boolean = CODE_RE.matches(code)

    fun presenceTopic(code: String): String = "$ROOT/p/$code"

    /** Sorted pair so both peers subscribe to the same topic. */
    fun chatTopic(a: String, b: String): String = if (a < b) "$ROOT/c/$a/$b" else "$ROOT/c/$b/$a"

    fun chatId(now: Long, random: RandomSource): String {
        val suffix = (0 until 5).map { (random.nextDouble() * 36).toInt().coerceIn(0, 35).toString(36) }.joinToString("")
        return "$now-$suffix"
    }

    fun clientId(random: RandomSource): String {
        val suffix = (0 until 10).map { (random.nextDouble() * 36).toInt().coerceIn(0, 35).toString(36) }.joinToString("")
        return "hpw$suffix"
    }

    const val KEEPALIVE_SEC = 30
    const val RECONNECT_PERIOD_MS = 4_000L
    const val CONNECT_TIMEOUT_MS = 9_000L
    const val PUBLISH_INTERVAL_MS = 25_000L
    const val PUBLISH_DEBOUNCE_MS = 350L
    const val ACTIVE_GAME_DEBOUNCE_MS = 200L
    const val BROKER_SWITCH_AFTER_ERRORS = 4
    const val BROKER_RETRY_MS = 6_000L
    const val CHAT_BUFFER_LIMIT = 200
    const val CHAT_TEXT_LIMIT = 500

    /** The tombstone a client leaves as its MQTT last will. */
    fun tombstone(now: Long): String = "{\"v\":1,\"t\":$now,\"up\":false}"

    /** Ports used by the native client. Same brokers, same protocol — TCP/TLS instead of
     *  WebSocket, which is a transport detail of the browser. */
    val BROKER_ENDPOINTS = listOf(
        Broker("broker.hivemq.com", wssPort = 8884, tlsPort = 8883, tcpPort = 1883),
        Broker("broker.emqx.io", wssPort = 8084, tlsPort = 8883, tcpPort = 1883),
    )

    /** Strings the Homies header shows for each relay state. */
    fun relayLabel(state: RelayState): String = when (state) {
        RelayState.IDLE -> "standby"
        RelayState.CONNECTING -> "connecting…"
        RelayState.ONLINE -> "relay connected"
        RelayState.OFFLINE -> "relay offline"
    }
}

enum class RelayState { IDLE, CONNECTING, ONLINE, OFFLINE }

data class Broker(val host: String, val wssPort: Int, val tlsPort: Int, val tcpPort: Int)
