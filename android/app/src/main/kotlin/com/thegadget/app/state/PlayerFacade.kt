package com.thegadget.app.state

import android.content.ComponentName
import android.content.Context
import android.os.Bundle
import androidx.media3.common.MediaItem
import androidx.media3.common.Player
import androidx.media3.session.MediaController
import androidx.media3.session.SessionCommand
import androidx.media3.session.SessionToken
import com.google.common.util.concurrent.FutureCallback
import com.google.common.util.concurrent.Futures
import com.google.common.util.concurrent.MoreExecutors
import com.thegadget.app.core.Track
import com.thegadget.app.service.PlaybackService
import com.thegadget.app.service.PlaybackSnapshot
import com.thegadget.app.service.QueueItem
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.launch
import kotlinx.serialization.builtins.ListSerializer
import kotlinx.serialization.json.Json

/**
 * The app-side handle on [PlaybackService] — the equivalent of the web `useMusicPlayer` object.
 * Queue/transport semantics live in the service; this facade mirrors its broadcast snapshot and
 * forwards the custom commands, so the Compose layer talks to one authoritative player.
 */
class PlayerFacade(context: Context) {

    private val appContext = context.applicationContext
    private val json = Json { ignoreUnknownKeys = true }
    private var controller: MediaController? = null
    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.Main)

    private val _snapshot = MutableStateFlow(PlaybackSnapshot())
    val snapshot: StateFlow<PlaybackSnapshot> get() = _snapshot
    val state: StateFlow<PlaybackSnapshot> get() = _snapshot

    fun connect() {
        if (controller != null) return
        val token = SessionToken(appContext, ComponentName(appContext, PlaybackService::class.java))
        val future = MediaController.Builder(appContext, token).buildAsync()
        Futures.addCallback(
            future,
            object : FutureCallback<MediaController> {
                override fun onSuccess(c: MediaController) {
                    controller = c
                    c.addListener(object : Player.Listener {
                        override fun onIsPlayingChanged(isPlaying: Boolean) = refresh()
                        override fun onMediaItemTransition(item: MediaItem?, reason: Int) = refresh()
                        override fun onPlaybackStateChanged(playbackState: Int) = refresh()
                        override fun onPositionDiscontinuity(
                            oldPosition: Player.PositionInfo,
                            newPosition: Player.PositionInfo,
                            reason: Int,
                        ) = refresh()
                    })
                    refresh()
                    // The service broadcasts on discrete changes; tick the seek bar while playing.
                    scope.launch {
                        while (true) {
                            delay(500)
                            if (_snapshot.value.playing) refresh()
                        }
                    }
                }

                override fun onFailure(t: Throwable) {}
            },
            MoreExecutors.directExecutor(),
        )
    }

    private fun refresh() {
        val c = controller ?: return
        _snapshot.value = readExtras(c)
    }

    private fun readExtras(c: MediaController): PlaybackSnapshot =
        c.sessionExtras.getString(PlaybackService.EXTRA_STATE)?.let { s ->
            runCatching { json.decodeFromString(PlaybackSnapshot.serializer(), s) }.getOrNull()
        } ?: PlaybackSnapshot()

    fun disconnect() {
        controller?.release()
        controller = null
    }

    /* ------------------------------------------------------------ commands */

    private fun cmd(action: String, build: Bundle.() -> Unit = {}) {
        controller?.sendCustomCommand(SessionCommand(action, Bundle.EMPTY), Bundle().apply(build))
    }

    fun playQueue(items: List<Track>, start: Int, autoplay: Boolean = true) =
        cmd(PlaybackService.CMD_PLAY_QUEUE) {
            val queue = items.map { QueueItem(id = it.id, title = it.title, folder = it.folder, uri = it.uri, cover = it.cover) }
            putString("queue", json.encodeToString(ListSerializer(QueueItem.serializer()), queue))
            putInt("start", start)
            putBoolean("autoplay", autoplay)
        }

    fun toggle() {
        val p = controller ?: return
        if (p.isPlaying) p.pause() else p.play()
    }

    fun next() = controller?.seekToNext()

    fun prev() = cmd(PlaybackService.CMD_PREV)

    fun seekTo(ms: Long) = controller?.seekTo(ms)

    fun seek(ms: Long) = seekTo(ms)

    fun setShuffle(v: Boolean) = cmd(PlaybackService.CMD_SHUFFLE) { putBoolean("value", v) }

    fun cycleShuffle() = setShuffle(!snapshot.value.shuffle)

    fun cycleRepeat() = cmd(PlaybackService.CMD_REPEAT)

    fun setVolume(v: Float) = cmd(PlaybackService.CMD_VOLUME) { putFloat("value", v) }

    fun setKeepAwake(v: Boolean) = cmd(PlaybackService.CMD_WAKE) { putBoolean("value", v) }
}
