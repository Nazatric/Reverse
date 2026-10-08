package com.thegadget.app.state

import android.content.ComponentName
import android.content.Context
import android.os.Bundle
import androidx.media3.session.MediaController
import androidx.media3.session.SessionCommand
import androidx.media3.session.SessionToken
import com.google.common.util.concurrent.MoreExecutors
import com.thegadget.app.service.PlaybackService
import com.thegadget.app.service.PlaybackSnapshot
import com.thegadget.app.service.QueueItem
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
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

    private val _snapshot = MutableStateFlow(PlaybackSnapshot())
    val snapshot: StateFlow<PlaybackSnapshot> get() = _snapshot

    fun connect() {
        if (controller != null) return
        val token = SessionToken(appContext, ComponentName(appContext, PlaybackService::class.java))
        MediaController.Builder(appContext, token).buildAsync().addListener(
            {
                controller = it as MediaController
                (it as MediaController).addListener(object : MediaController.Listener {
                    override fun onExtrasChanged(c: MediaController, extras: Bundle) {
                        extras.getString(PlaybackService.EXTRA_STATE)?.let { s ->
                            runCatching { json.decodeFromString(PlaybackSnapshot.serializer(), s) }
                                .getOrNull()?.let { snap -> _snapshot.value = snap }
                        }
                    }
                })
                _snapshot.value = readExtras(it as MediaController)
            },
            MoreExecutors.directExecutor(),
        )
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

    fun playQueue(items: List<QueueItem>, start: Int, autoplay: Boolean = true) =
        cmd(PlaybackService.CMD_PLAY_QUEUE) {
            putString("queue", json.encodeToString(ListSerializer(QueueItem.serializer()), items))
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

    fun setShuffle(v: Boolean) = cmd(PlaybackService.CMD_SHUFFLE) { putBoolean("value", v) }

    fun cycleRepeat() = cmd(PlaybackService.CMD_REPEAT)

    fun setVolume(v: Float) = cmd(PlaybackService.CMD_VOLUME) { putFloat("value", v) }

    fun setKeepAwake(v: Boolean) = cmd(PlaybackService.CMD_WAKE) { putBoolean("value", v) }
}
