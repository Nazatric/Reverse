package com.thegadget.app.core

import java.nio.charset.Charset

/**
 * Port of `src/utils/tags.ts` — the dependency-free metadata reader for the formats phones hold:
 * MP3 (ID3v2.3/2.4 + ID3v1.1 fallback), M4A/MP4 (`ilst`/`covr`) and FLAC (Vorbis comment +
 * PICTURE).
 *
 * Differences from the web version, all deliberate:
 *   - Instead of a `blob:` URL, embedded art is returned as bytes + MIME so the caller can decode
 *     it (BitmapFactory/Coil). Cover *selection* rules are unchanged.
 *   - The reader is synchronous over a [ByteSource]: browsers read slices of a `File`, Android
 *     reads ranges of a `content://` document. Callers hop to Dispatchers.IO.
 *   - `iso-8859-1` is decoded as windows-1252, which is what the browser's TextDecoder label
 *     actually means, so accents in old ID3v1 tags match the web app byte for byte.
 */
data class CoverArt(val bytes: ByteArray, val mime: String) {
    override fun equals(other: Any?) = other is CoverArt && mime == other.mime && bytes.contentEquals(other.bytes)
    override fun hashCode() = 31 * bytes.contentHashCode() + mime.hashCode()
}

data class Meta(
    val title: String? = null,
    val artist: String? = null,
    val album: String? = null,
    val cover: CoverArt? = null,
)

/** Random-access byte view of a file. `read` returns at most `length` bytes, fewer at EOF. */
interface ByteSource {
    val size: Long
    fun read(offset: Long, length: Int): ByteArray
}

class ByteArraySource(private val data: ByteArray) : ByteSource {
    override val size: Long get() = data.size.toLong()
    override fun read(offset: Long, length: Int): ByteArray {
        if (offset >= data.size) return ByteArray(0)
        val end = minOf(data.size.toLong(), offset + length).toInt()
        return data.copyOfRange(offset.toInt(), end)
    }
}

object TagReader {
    private const val ID3_MAX = 8 * 1024 * 1024
    private const val FLAC_MAX = 10 * 1024 * 1024
    private const val MOOV_MAX = 16 * 1024 * 1024

    fun readTags(source: ByteSource): Meta = try {
        val head = source.read(0, 12)
        when {
            ascii(head, 0, 3) == "ID3" -> {
                val v2 = readId3(source)
                if (v2.title != null || v2.artist != null || v2.album != null || v2.cover != null) v2
                else {
                    // some files carry an ID3v2 header but no frames — fall through to v1
                    val v1 = readId3v1(source)
                    if (v1.title != null || v1.artist != null) v1 else v2
                }
            }
            ascii(head, 0, 4) == "fLaC" -> readFlac(source)
            ascii(head, 4, 4) == "ftyp" -> readMp4(source)
            else -> {
                val v1 = readId3v1(source)
                if (v1.title != null || v1.artist != null) v1 else Meta()
            }
        }
    } catch (_: Exception) {
        Meta() // unreadable tags are fine — the filename is the fallback
    }

    /* ---------------------------------------------------------------- helpers */

    private fun ascii(b: ByteArray, s: Int, n: Int): String {
        val sb = StringBuilder()
        var i = s
        while (i < s + n && i < b.size) {
            sb.append((b[i].toInt() and 0xff).toChar())
            i++
        }
        return sb.toString()
    }

    private fun u32(b: ByteArray, p: Int): Long =
        (((b[p].toLong() and 0xff) shl 24) or ((b[p + 1].toLong() and 0xff) shl 16) or
            ((b[p + 2].toLong() and 0xff) shl 8) or (b[p + 3].toLong() and 0xff))

    private fun u32le(b: ByteArray, p: Int): Long =
        (((b[p + 3].toLong() and 0xff) shl 24) or ((b[p + 2].toLong() and 0xff) shl 16) or
            ((b[p + 1].toLong() and 0xff) shl 8) or (b[p].toLong() and 0xff))

    private fun syncsafe(b: ByteArray, p: Int): Int =
        ((b[p].toInt() and 127) shl 21) or ((b[p + 1].toInt() and 127) shl 14) or
            ((b[p + 2].toInt() and 127) shl 7) or (b[p + 3].toInt() and 127)

    /** JS `str.replace(/\0+$/g, "").trim()`. */
    private fun clean(s: String): String {
        var end = s.length
        while (end > 0 && s[end - 1] == '\u0000') end--
        return s.substring(0, end).trim { it.isWhitespace() || it == '\uFEFF' || it == '\u00A0' }
    }

    private fun decode(bytes: ByteArray, charset: Charset): String =
        try {
            clean(String(bytes, charset))
        } catch (_: Exception) {
            ""
        }

    private fun decodeUtf8(bytes: ByteArray) = decode(bytes, Charsets.UTF_8)

    /** windows-1252 — what the WHATWG `iso-8859-1` label decodes to. */
    private fun decodeLatin1(bytes: ByteArray): String {
        val sb = StringBuilder(bytes.size)
        for (raw in bytes) {
            val c = raw.toInt() and 0xff
            sb.append(if (c in 0x80..0x9f) WINDOWS_1252[c - 0x80] else c.toChar())
        }
        return clean(sb.toString())
    }

    private fun decodeUtf16(bytes: ByteArray, bigEndian: Boolean): String {
        var b = bytes
        var be = bigEndian
        if (b.size >= 2) {
            if (b[0] == 0xfe.toByte() && b[1] == 0xff.toByte()) {
                b = b.copyOfRange(2, b.size); be = true
            } else if (b[0] == 0xff.toByte() && b[1] == 0xfe.toByte()) {
                b = b.copyOfRange(2, b.size); be = false
            }
        }
        return decode(b, if (be) Charsets.UTF_16BE else Charsets.UTF_16LE)
    }

    private fun guessMime(img: ByteArray, mime: String?): String {
        if (!mime.isNullOrEmpty() && mime.indexOf('/') >= 0 && mime != "-->") return mime
        return if (img.isNotEmpty() && (img[0].toInt() and 0xff) == 0x89) "image/png" else "image/jpeg"
    }

    /* ---------------------------------------------------------------- ID3v2 */

    private fun id3Text(b: ByteArray): String {
        if (b.isEmpty()) return ""
        val enc = b[0].toInt() and 0xff
        val c = b.copyOfRange(1, b.size)
        return when (enc) {
            0 -> decodeLatin1(c)
            1 -> decodeUtf16(c, bigEndian = false)
            2 -> decodeUtf16(c, bigEndian = true)
            else -> decodeUtf8(c)
        }
    }

    private fun id3Picture(b: ByteArray): CoverArt? {
        val enc = b[0].toInt() and 0xff
        var p = 1
        val mimeSb = StringBuilder()
        while (p < b.size && b[p] != 0.toByte()) mimeSb.append((b[p++].toInt() and 0xff).toChar())
        p += 2 // terminator + picture type
        if (enc == 1 || enc == 2) {
            while (p + 1 < b.size && !(b[p] == 0.toByte() && b[p + 1] == 0.toByte())) p += 2
            p += 2
        } else {
            while (p < b.size && b[p] != 0.toByte()) p++
            p++
        }
        if (p >= b.size) return null
        val img = b.copyOfRange(p, b.size)
        if (img.size < 64) return null
        return CoverArt(img, guessMime(img, mimeSb.toString()))
    }

    private fun readId3(source: ByteSource): Meta {
        val head = source.read(0, 10)
        if (head.size < 10 || ascii(head, 0, 3) != "ID3") return Meta()
        val ver = head[3].toInt() and 0xff
        if (ver < 3 || ver > 4) return Meta()
        val size = syncsafe(head, 6)
        val b = source.read(10, minOf(size, ID3_MAX))
        var p = 0
        if ((head[5].toInt() and 0x40) != 0 && b.size >= 4) {
            p = if (ver == 4) syncsafe(b, 0) else u32(b, 0).toInt() + 4
        }
        var title: String? = null
        var artist: String? = null
        var album: String? = null
        var cover: CoverArt? = null
        while (p + 10 <= b.size) {
            val id = ascii(b, p, 4)
            if (!ID_RE.matches(id)) break
            val len = if (ver == 4) syncsafe(b, p + 4) else u32(b, p + 4).toInt()
            val s = p + 10
            val e = s + len
            if (len <= 0 || e > b.size) break
            val f = b.copyOfRange(s, e)
            when (id) {
                "TIT2" -> title = id3Text(f)
                "TPE1" -> artist = id3Text(f)
                "TALB" -> album = id3Text(f)
                "APIC" -> if (cover == null) cover = id3Picture(f)
            }
            p = e
        }
        return Meta(title, artist, album, cover)
    }

    private val ID_RE = Regex("^[A-Z0-9]{4}$")

    /** Legacy ID3v1.1 — the last 128 bytes of a plain MP3. */
    private fun readId3v1(source: ByteSource): Meta {
        if (source.size < 128) return Meta()
        val b = source.read(source.size - 128, 128)
        if (b.size < 128 || ascii(b, 0, 3) != "TAG") return Meta()
        fun field(start: Int, n: Int) = decodeLatin1(b.copyOfRange(start, start + n)).replace("\u0000", "").trim()
        val title = field(3, 30)
        val artist = field(33, 30)
        val album = field(63, 30)
        return Meta(
            title = title.ifEmpty { null },
            artist = artist.ifEmpty { null },
            album = album.ifEmpty { null },
        )
    }

    /* ------------------------------------------------------------- MP4 / M4A */

    private inline fun eachAtom(b: ByteArray, start: Int, end: Int, fn: (type: String, s: Int, e: Int) -> Unit) {
        var p = start
        while (p + 8 <= end) {
            val len = u32(b, p)
            if (len < 8 || p + len > end) break
            fn(ascii(b, p + 4, 4), p + 8, (p + len).toInt())
            p += len.toInt()
        }
    }

    private fun parseMoov(b: ByteArray): Meta {
        var meta = Meta()
        eachAtom(b, 0, b.size) { t, s, e ->
            if (t != "udta") return@eachAtom
            eachAtom(b, s, e) { t2, s2, e2 ->
                if (t2 != "meta") return@eachAtom
                eachAtom(b, s2 + 4, e2) { t3, s3, e3 ->
                    if (t3 != "ilst") return@eachAtom
                    eachAtom(b, s3, e3) { key, s4, e4 ->
                        eachAtom(b, s4, e4) { t5, s5, e5 ->
                            if (t5 != "data") return@eachAtom
                            val payload = b.copyOfRange(s5 + 8, e5)
                            when (key) {
                                "covr" -> if (meta.cover == null && payload.size > 64) {
                                    val flag = (u32(b, s5) and 0xffffff).toInt()
                                    meta = meta.copy(cover = CoverArt(payload, if (flag == 14) "image/png" else "image/jpeg"))
                                }
                                "\u00A9nam" -> meta = meta.copy(title = decodeUtf8(payload))
                                "\u00A9ART" -> meta = meta.copy(artist = decodeUtf8(payload))
                                "\u00A9alb" -> meta = meta.copy(album = decodeUtf8(payload))
                            }
                        }
                    }
                }
            }
        }
        return meta
    }

    private fun readMp4(source: ByteSource): Meta {
        var pos = 0L
        while (pos + 8 <= source.size) {
            val h = source.read(pos, 16)
            if (h.size < 8) break
            var len = u32(h, 0)
            val type = ascii(h, 4, 4)
            var hdr = 8
            if (len == 1L && h.size >= 16) {
                len = 0
                for (i in 8 until 16) len = (len shl 8) or (h[i].toLong() and 0xff)
                hdr = 16
            } else if (len == 0L) {
                len = source.size - pos
            }
            if (len < hdr) break
            if (type == "moov") {
                if (len > MOOV_MAX) return Meta()
                return parseMoov(source.read(pos + hdr, len.toInt() - hdr))
            }
            pos += len
        }
        return Meta()
    }

    /* ----------------------------------------------------------------- FLAC */

    private fun readFlac(source: ByteSource): Meta {
        val b = source.read(0, minOf(source.size, FLAC_MAX.toLong()).toInt())
        if (ascii(b, 0, 4) != "fLaC") return Meta()
        var title: String? = null
        var artist: String? = null
        var album: String? = null
        var cover: CoverArt? = null
        var p = 4
        while (p + 4 <= b.size) {
            val last = (b[p].toInt() and 0x80) != 0
            val type = b[p].toInt() and 0x7f
            val len = ((b[p + 1].toInt() and 0xff) shl 16) or ((b[p + 2].toInt() and 0xff) shl 8) or (b[p + 3].toInt() and 0xff)
            val s = p + 4
            val e = s + len
            if (e > b.size) break
            if (type == 4) {
                var q = s + 4 + u32le(b, s).toInt()
                val count = u32le(b, q).toInt()
                q += 4
                var i = 0
                while (i < count && q + 4 <= e) {
                    val l = u32le(b, q).toInt()
                    val line = decodeUtf8(b.copyOfRange(q + 4, minOf(q + 4 + l, b.size)))
                    val eq = line.indexOf('=')
                    if (eq > 0) {
                        val k = line.substring(0, eq).uppercase()
                        val v = line.substring(eq + 1)
                        when (k) {
                            "TITLE" -> title = v
                            "ARTIST" -> artist = v
                            "ALBUM" -> album = v
                        }
                    }
                    q += 4 + l
                    i++
                }
            } else if (type == 6 && cover == null) {
                var q = s + 4
                val ml = u32(b, q).toInt()
                val mime = ascii(b, q + 4, ml)
                q += 4 + ml
                q += 4 + u32(b, q).toInt() // description
                q += 16 // width, height, depth, colours
                val dl = u32(b, q).toInt()
                q += 4
                if (dl > 64 && q + dl <= e && q >= 0) {
                    cover = CoverArt(b.copyOfRange(q, q + dl), guessMime(b.copyOfRange(q, q + 4), mime))
                }
            }
            if (last) break
            p = e
        }
        return Meta(title, artist, album, cover)
    }

    private val WINDOWS_1252 = charArrayOf(
        '\u20AC', '\u0081', '\u201A', '\u0192', '\u201E', '\u2026', '\u2020', '\u2021',
        '\u02C6', '\u2030', '\u0160', '\u2039', '\u0152', '\u008D', '\u017D', '\u008F',
        '\u0090', '\u2018', '\u2019', '\u201C', '\u201D', '\u2022', '\u2013', '\u2014',
        '\u02DC', '\u2122', '\u0161', '\u203A', '\u0153', '\u009D', '\u017E', '\u0178',
    )
}
