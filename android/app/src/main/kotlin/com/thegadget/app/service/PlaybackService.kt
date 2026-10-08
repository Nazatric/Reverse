package com.thegadget.app.service

import android.app.PendingIntent
import android.content.Intent
import android.graphics.BitmapFactory
import android.os.Bundle
import androidx.media3.common.C
import androidx.media3.common.ForwardingPlayer
import androidx.media3.common.MediaItem
import androidx.media3.common.MediaMetadata
import androidx.media3.common.Player
import androidx.media3.exoplayer.ExoPlayer
import androidx.media3.session.MediaSession
import androidx.media3.session.MediaSessionService
import androidx.media3.session.SessionCommand
import androidx.media3.session.SessionResult
import com.google.common.util.concurrent.ListenableFuture
import kotlinx.coroutines.flow.first
import com.thegadget.app.MainActivity
import com.thegadget.app.data.Stores
import java.io.File
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.launch
import kotlinx.serialization.Serializable
import kotlinx.serialization.builtins.ListSerializer
import kotlinx.serialization.json.Json

@Serializable
data class QueueItem(
    val id: String,
    val title: String,
    val folder: String,
    val uri: String,
    val cover: String? = null,
)

@Serializable
data class PlaybackSnapshot(
    val current: QueueItem? = null,
    val playing: Boolean = false,
    val shuffle: Boolean = false,
    val repeat: String = "off",
    val positionMs: Long = 0,
    val durationMs: Long = 0,
    val volume: Float = 0.8f,
    val queueSize: Int = 0,
    val index: Int = -1,
)

/**
 * The single authoritative playback engine — the native stand-in for the web app's one
 * `<audio>` element. Queue stepping, shuffle, repeat and the `prev` restarts-after-3s rule are
 * ported from `src/hooks/useMusicPlayer.ts` verbatim in behaviour.
 */
class PlaybackService : MediaSessionService() {

    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.Main)
    private var player: ExoPlayer? = null
    private var session: MediaSession? = null
    private lateinit var stores: Stores
    private lateinit var metaDb: com.thegadget.app.data.MetaDb
    private val json = Json { ignoreUnknownKeys = true }

    private val queue = mutableListOf<QueueItem>()
    private var qIdx = -1
    private var shuffle = false
    private var repeat: String = "off" // off | all | one

    companion object {
        const val CMD_PLAY_QUEUE = "gadget.playQueue"
        const val CMD_PREV = "gadget.prev"
        const val CMD_SHUFFLE = "gadget.shuffle"
        const val CMD_REPEAT = "gadget.repeat"
        const val CMD_VOLUME = "gadget.volume"
        const val CMD_WAKE = "gadget.wake"
        const val EXTRA_STATE = "gadget.state"
    }

    override fun onCreate() {
        super.onCreate()
        stores = Stores(applicationContext)
        metaDb = com.thegadget.app.data.MetaDb.create(applicationContext)
        val exo = ExoPlayer.Builder(this).build().apply {
            setAudioAttributes(
                androidx.media3.common.AudioAttributes.Builder()
                    .setUsage(C.USAGE_MEDIA)
                    .setContentType(C.AUDIO_CONTENT_TYPE_MUSIC)
                    .build(),
                true,
            )
            setWakeMode(C.WAKE_MODE_LOCAL)
            setHandleAudioBecomingNoisy(true)
        }
        player = exo
        scope.launch { exo.volume = stores.volume.first() }

        val wrapped = object : ForwardingPlayer(exo) {
            override fun seekToNext() = next(fromEnded = false)
            override fun seekToNextMediaItem() = next(fromEnded = false)
            override fun seekToPrevious() = prev()
            override fun seekToPreviousMediaItem() = prev()
            override fun getAvailableCommands(): androidx.media3.common.Player.Commands =
                super.getAvailableCommands().buildUpon()
                    .add(Player.COMMAND_SEEK_TO_NEXT)
                    .add(Player.COMMAND_SEEK_TO_PREVIOUS)
                    .build()
        }

        val activityIntent = PendingIntent.getActivity(
            this, 0, Intent(this, MainActivity::class.java),
            PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_UPDATE_CURRENT,
        )
        session = MediaSession.Builder(this, wrapped)
            .setSessionActivity(activityIntent)
            .setCallback(SessionCallback())
            .build()

        exo.addListener(object : Player.Listener {
            override fun onIsPlayingChanged(isPlaying: Boolean) = publish()
            override fun onMediaItemTransition(mediaItem: MediaItem?, reason: Int) {
                onTrackChanged()
                publish()
            }
            override fun onPlaybackStateChanged(state: Int) {
                if (state == Player.STATE_ENDED) ended()
                publish()
            }
            override fun onPositionDiscontinuity(
                oldPosition: Player.PositionInfo,
                newPosition: Player.PositionInfo,
                reason: Int,
            ) = publish()
        })
    }

    override fun onGetSession(controllerInfo: MediaSession.ControllerInfo): MediaSession? = session

    override fun onTaskRemoved(rootIntent: Intent?) {
        // A browser tab keeps playing with the window closed; the service does the same, but
        // stops cleanly when paused so we don't linger silently.
        if (player?.isPlaying != true) stopSelf()
        super.onTaskRemoved(rootIntent)
    }

    override fun onDestroy() {
        scope.cancel()
        session?.release()
        player?.release()
        super.onDestroy()
    }

    /* ------------------------------------------------------------ queue semantics */

    private fun load(item: QueueItem, autoplay: Boolean) {
        val p = player ?: return
        qIdx = queue.indexOfFirst { it.id == item.id }.coerceAtLeast(0)
        p.setMediaItem(MediaItem.Builder().setUri(item.uri).setMediaId(item.id).build())
        p.prepare()
        if (autoplay) p.play() else {
            p.playWhenReady = false
        }
        scope.launch { stores.setLastTrack(item.id) }
        attachMetadata(item)
        publish()
    }

    private fun step(dir: Int, fromEnded: Boolean) {
        if (queue.isEmpty()) return
        var i = qIdx
        if (shuffle && queue.size > 1) {
            var n = i
            while (n == i) n = kotlin.random.Random.nextInt(queue.size)
            i = n
        } else {
            i += dir
            if (i >= queue.size) {
                if (fromEnded && repeat != "all") {
                    player?.pause()
                    return
                }
                i = 0
            }
            if (i < 0) i = queue.size - 1
        }
        load(queue[i], true)
    }

    private fun next(fromEnded: Boolean) = step(1, fromEnded)

    private fun prev() {
        val p = player ?: return
        if (p.currentPosition > 3000) {
            p.seekTo(0)
            return
        }
        step(-1, false)
    }

    private fun ended() {
        if (repeat == "one") {
            player?.seekTo(0)
            player?.play()
        } else {
            step(1, true)
        }
    }

    private fun onTrackChanged() { attachMetadata(queue.getOrNull(qIdx) ?: return) }

    /** MediaSession metadata with real cover art — the lock-screen card. */
    private fun attachMetadata(item: QueueItem) {
        scope.launch(Dispatchers.IO) {
            val meta = runCatching { metaDb.meta().get(item.id) }.getOrNull()
            val art: android.graphics.Bitmap? = (meta?.coverPath ?: item.cover)?.let { path ->
                runCatching { BitmapFactory.decodeFile(path) }.getOrNull()
                    ?: runCatching {
                        contentResolver.openInputStream(android.net.Uri.parse(path))?.use {
                            BitmapFactory.decodeStream(it)
                        }
                    }.getOrNull()
            }
            val folderLeaf = item.folder.substringAfterLast('/').ifEmpty { item.folder }
            withContextMain {
                val p = player ?: return@withContextMain
                val current = p.currentMediaItem?.takeIf { it.mediaId == item.id } ?: return@withContextMain
                p.replaceMediaItem(
                    0,
                    MediaItem.Builder()
                        .setUri(item.uri)
                        .setMediaId(item.id)
                        .setMediaMetadata(
                            MediaMetadata.Builder()
                                .setTitle(meta?.title ?: item.title)
                                .setArtist(meta?.artist ?: folderLeaf)
                                .setAlbumTitle(meta?.album ?: folderLeaf)
                                .setArtworkData(
                                    art?.let { b ->
                                        java.io.ByteArrayOutputStream().use { o ->
                                            b.compress(android.graphics.Bitmap.CompressFormat.JPEG, 86, o)
                                            o.toByteArray()
                                        }
                                    },
                                    MediaMetadata.PICTURE_TYPE_FRONT_COVER,
                                )
                                .build(),
                        )
                        .build(),
                )
            }
        }
    }

    private inline fun withContextMain(crossinline block: () -> Unit) {
        if (android.os.Looper.myLooper() == android.os.Looper.getMainLooper()) block()
        else android.os.Handler(android.os.Looper.getMainLooper()).post { block() }
    }

    /* ------------------------------------------------------------ state broadcast */

    private fun publish() {
        val p = player ?: return
        val snap = PlaybackSnapshot(
            current = queue.getOrNull(qIdx),
            playing = p.isPlaying,
            shuffle = shuffle,
            repeat = repeat,
            positionMs = p.currentPosition.coerceAtLeast(0),
            durationMs = p.duration.coerceAtLeast(0),
            volume = p.volume,
            queueSize = queue.size,
            index = qIdx,
        )
        val extras = Bundle().apply { putString(EXTRA_STATE, json.encodeToString(PlaybackSnapshot.serializer(), snap)) }
        session?.setSessionExtras(extras)
    }

    private inner class SessionCallback : MediaSession.Callback {
        override fun onConnect(
            session: MediaSession,
            controller: MediaSession.ControllerInfo,
        ): MediaSession.ConnectionResult {
            val available = MediaSession.ConnectionResult.DEFAULT_SESSION_AND_LIBRARY_COMMANDS.buildUpon()
                .add(SessionCommand(CMD_PLAY_QUEUE, Bundle.EMPTY))
                .add(SessionCommand(CMD_PREV, Bundle.EMPTY))
                .add(SessionCommand(CMD_SHUFFLE, Bundle.EMPTY))
                .add(SessionCommand(CMD_REPEAT, Bundle.EMPTY))
                .add(SessionCommand(CMD_VOLUME, Bundle.EMPTY))
                .add(SessionCommand(CMD_WAKE, Bundle.EMPTY))
                .build()
            return MediaSession.ConnectionResult.AcceptedResultBuilder(session)
                .setAvailableSessionCommands(available)
                .build()
        }

        override fun onCustomCommand(
            session: MediaSession,
            controller: MediaSession.ControllerInfo,
            customCommand: SessionCommand,
            args: Bundle,
        ): ListenableFuture<SessionResult> {
            when (customCommand.customAction) {
                CMD_PLAY_QUEUE -> {
                    val items = json.decodeFromString(ListSerializer(QueueItem.serializer()), args.getString("queue", "[]"))
                    val start = args.getInt("start", 0)
                    val autoplay = args.getBoolean("autoplay", true)
                    queue.clear()
                    queue.addAll(items)
                    if (queue.size > start) load(queue[start], autoplay)
                    else { player?.stop(); qIdx = -1; publish() }
                }
                CMD_PREV -> prev()
                CMD_SHUFFLE -> { shuffle = args.getBoolean("value", false); publish() }
                CMD_REPEAT -> {
                    repeat = when (repeat) { "off" -> "all"; "all" -> "one"; else -> "off" }
                    publish()
                }
                CMD_VOLUME -> player?.volume = args.getFloat("value", 0.8f).coerceIn(0f, 1f).also { scope.launch { stores.setVolume(it) } }
                CMD_WAKE -> player?.setWakeMode(if (args.getBoolean("value", true)) C.WAKE_MODE_LOCAL else C.WAKE_MODE_NONE)
            }
            return com.google.common.util.concurrent.Futures.immediateFuture(SessionResult(SessionResult.RESULT_SUCCESS))
        }
    }
}

