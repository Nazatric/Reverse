package com.thegadget.app.data

import android.content.Context
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.net.Uri
import android.provider.DocumentsContract
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.stringPreferencesKey
import androidx.datastore.preferences.preferencesDataStore
import com.thegadget.app.core.GadgetText
import com.thegadget.app.core.Library
import com.thegadget.app.core.TagReader
import com.thegadget.app.core.Track
import java.io.File
import java.io.FileOutputStream
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.sync.Semaphore
import kotlinx.coroutines.sync.withPermit
import kotlinx.coroutines.withContext

/** Same four states the web library cycles through (`LibraryStatus` in useMusicPlayer). */
enum class LibraryStatus { EMPTY, LOADING, NEEDS_PERMISSION, READY }

data class LibraryState(
    val status: LibraryStatus,
    val folderName: String = "",
    val tracks: List<Track> = emptyList(),
    val error: String? = null,
)

private val Context.handleStore by preferencesDataStore(name = "gadget-handles")
private val HANDLE_KEY = stringPreferencesKey("music-folder")

/**
 * The native port of `src/utils/musicLibrary.ts`.
 *
 * The web app keeps a FileSystemDirectoryHandle in IndexedDB; Android keeps a persisted SAF tree
 * Uri in DataStore (same logical key, `music-folder`). Walking, filtering, artwork precedence and
 * the needs-permission restore path all mirror the web implementation.
 */
class LibraryRepository(
    private val context: Context,
    private val metaDb: MetaDb,
) {
    val state = MutableStateFlow(LibraryState(LibraryStatus.LOADING))

    /** Two concurrent cover decodes, exactly like the web's artwork pipeline. */
    private val coverGate = Semaphore(2)

    suspend fun restore() = withContext(Dispatchers.IO) {
        val uri = context.handleStore.data.map { it[HANDLE_KEY] }.first()
        if (uri == null) {
            state.value = LibraryState(LibraryStatus.EMPTY)
            return@withContext
        }
        if (!hasPermission(Uri.parse(uri))) {
            state.value = LibraryState(LibraryStatus.NEEDS_PERMISSION, folderName = displayName(Uri.parse(uri)) ?: "")
            return@withContext
        }
        scan(Uri.parse(uri))
    }

    suspend fun adopt(uri: Uri) = withContext(Dispatchers.IO) {
        try {
            context.contentResolver.takePersistableUriPermission(
                uri, android.content.Intent.FLAG_GRANT_READ_URI_PERMISSION,
            )
        } catch (_: SecurityException) { /* read-only for this session */ }
        context.handleStore.edit { it[HANDLE_KEY] = uri.toString() }
        scan(uri)
    }

    suspend fun forget() = withContext(Dispatchers.IO) {
        val uri = context.handleStore.data.map { it[HANDLE_KEY] }.first()
        if (uri != null && hasPermission(Uri.parse(uri))) {
            runCatching {
                context.contentResolver.releasePersistableUriPermission(
                    Uri.parse(uri), android.content.Intent.FLAG_GRANT_READ_URI_PERMISSION,
                )
            }
        }
        context.handleStore.edit { it.remove(HANDLE_KEY) }
        metaDb.meta().clear()
        state.value = LibraryState(LibraryStatus.EMPTY)
    }

    private fun hasPermission(uri: Uri): Boolean =
        context.contentResolver.persistedUriPermissions.any { it.uri == uri && it.isReadPermission }

    private suspend fun scan(root: Uri) {
        state.value = LibraryState(LibraryStatus.LOADING)
        val tracks = mutableListOf<Track>()
        val folderArt = mutableMapOf<String, MutableList<Pair<String, String>>>() // folder -> (name, uri)
        val name = displayName(root) ?: "Music"
        walk(root, name, 0, tracks, folderArt)
        Library.sortTracks(tracks)
        // Attach folder artwork, mirroring cover/front > folder/album > other.
        for (t in tracks) {
            t.cover = folderArt[t.folder]?.minByOrNull { Library.artRank(it.first) }?.second
        }
        state.value = LibraryState(
            status = if (tracks.isEmpty()) LibraryStatus.EMPTY else LibraryStatus.READY,
            folderName = name,
            tracks = tracks,
            error = if (tracks.isEmpty()) "No audio files were found in that folder." else null,
        )
    }

    private fun walk(dir: Uri, path: String, depth: Int, tracks: MutableList<Track>, folderArt: MutableMap<String, MutableList<Pair<String, String>>>) {
        if (depth > Library.MAX_DEPTH) return
        val children = childrenOf(dir) ?: return
        for ((child, childName, isDir) in children) {
            if (childName.startsWith(".")) continue
            if (isDir) {
                walk(child, "$path/$childName", depth + 1, tracks, folderArt)
            } else {
                when {
                    Library.AUDIO_EXT.containsMatchIn(childName) -> {
                        val rel = "$path/$childName"
                        tracks += Track(
                            id = rel,
                            title = GadgetText.titleFromFile(childName),
                            folder = path,
                            uri = child.toString(),
                        )
                    }
                    Library.IMAGE_EXT.containsMatchIn(childName) ->
                        folderArt.getOrPut(path) { mutableListOf() }.add(childName to child.toString())
                }
            }
        }
    }

    private fun childrenOf(dir: Uri): List<Triple<Uri, String, Boolean>>? {
        val childrenUri = DocumentsContract.buildChildDocumentsUriUsingTree(dir, DocumentsContract.getDocumentId(dir))
        return runCatching {
            context.contentResolver.query(childrenUri, null, null, null, null)?.use { c ->
                val idIx = c.getColumnIndex(DocumentsContract.Document.COLUMN_DOCUMENT_ID)
                val nameIx = c.getColumnIndex(DocumentsContract.Document.COLUMN_DISPLAY_NAME)
                val mimeIx = c.getColumnIndex(DocumentsContract.Document.COLUMN_MIME_TYPE)
                val out = mutableListOf<Triple<Uri, String, Boolean>>()
                while (c.moveToNext()) {
                    val docId = c.getString(idIx)
                    val childName = c.getString(nameIx) ?: continue
                    val mime = c.getString(mimeIx)
                    val childUri = DocumentsContract.buildDocumentUriUsingTree(dir, docId)
                    out += Triple(childUri, childName, mime == DocumentsContract.Document.MIME_TYPE_DIR)
                }
                out
            }
        }.getOrNull()
    }

    private fun displayName(uri: Uri): String? = runCatching {
        context.contentResolver.query(uri, null, null, null, null)?.use { c ->
            val ix = c.getColumnIndex(DocumentsContract.Document.COLUMN_DISPLAY_NAME)
            if (c.moveToFirst() && ix >= 0) c.getString(ix) else null
        }
    }.getOrNull()

    /* ---------------------------------------------------------- tags + artwork */

    /** Parsed tags, cached in Room like the web's metaStore. */
    suspend fun metaFor(track: Track): com.thegadget.app.core.TrackMeta = withContext(Dispatchers.IO) {
        metaDb.meta().get(track.id)?.let {
            return@withContext com.thegadget.app.core.TrackMeta(it.title, it.artist, it.album, it.coverPath)
        }
        val (meta, coverPath) = readTags(track)
        metaDb.meta().put(
            TrackMetaEntity(
                id = track.id, title = meta.title, artist = meta.artist, album = meta.album,
                coverPath = coverPath, durationMs = 0, updatedAt = System.currentTimeMillis(),
            ),
        )
        com.thegadget.app.core.TrackMeta(meta.title, meta.artist, meta.album, coverPath)
    }

    private suspend fun readTags(track: Track): Pair<com.thegadget.app.core.Meta, String?> {
        val uri = Uri.parse(track.uri)
        val length = context.contentResolver.query(uri, null, null, null, null)?.use { c ->
            val ix = c.getColumnIndex(DocumentsContract.Document.COLUMN_SIZE)
            if (c.moveToFirst() && ix >= 0) c.getLong(ix).toInt() else -1
        } ?: -1
        if (length <= 0) return com.thegadget.app.core.Meta() to null
        val source = object : com.thegadget.app.core.ByteSource {
            override val size: Long get() = length.toLong()
            override fun read(offset: Long, len: Int): ByteArray =
                context.contentResolver.openInputStream(uri)!!.use { s ->
                    s.skip(offset)
                    val buf = ByteArray(len)
                    var read = 0
                    while (read < len) {
                        val n = s.read(buf, read, len - read)
                        if (n < 0) break
                        read += n
                    }
                    buf.copyOf(read)
                }
        }
        val meta = TagReader.readTags(source)
        val cover = meta.cover?.let { coverGate.withPermit { writeArtwork(track.id, it.bytes) } }
        return meta to cover
    }

    /** cropToFill → JPEG .86, matching src/utils/image.ts. */
    private fun writeArtwork(id: String, bytes: ByteArray): String? = runCatching {
        val src = BitmapFactory.decodeByteArray(bytes, 0, bytes.size) ?: return@runCatching null
        val size = 512
        val scale = maxOf(size.toFloat() / src.width, size.toFloat() / src.height)
        val w = (src.width * scale).toInt()
        val h = (src.height * scale).toInt()
        val scaled = Bitmap.createScaledBitmap(src, w, h, true)
        val x = (w - size) / 2
        val y = (h - size) / 2
        val cropped = Bitmap.createBitmap(scaled, x, y, size, size)
        val out = File(File(context.filesDir, "artwork").apply { mkdirs() }, "${sanitize(id)}.jpg")
        FileOutputStream(out).use { cropped.compress(Bitmap.CompressFormat.JPEG, 86, it) }
        out.absolutePath
    }.getOrNull()

    private fun sanitize(id: String) = id.replace(Regex("[^A-Za-z0-9._-]"), "_")
}
