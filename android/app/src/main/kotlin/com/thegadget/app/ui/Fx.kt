package com.thegadget.app.ui

import android.content.Context
import android.media.AudioAttributes
import android.media.AudioFormat
import android.media.AudioTrack
import android.os.Build
import android.os.VibrationEffect
import android.os.Vibrator
import android.os.VibratorManager
import com.thegadget.app.core.Sfxr
import java.util.concurrent.ConcurrentHashMap
import java.util.concurrent.Executors
import kotlin.random.Random

/**
 * The native counterpart of `src/utils/audio.ts`: the same six UI sounds, synthesised from the same
 * SFXR presets at the same volumes, with the same haptic patterns. Effects are generated once and
 * replayed from cache, just as the web warms its jsfxr cache after the first gesture.
 *
 * Both channels are gated by the persisted settings, so `settings.sounds` / `settings.haptics`
 * behave as they do on the web.
 */
object Fx {
    /** Fixed so the six sounds are identical on every launch (the web re-randomises each load). */
    private const val SEED = 0x6AD6E7L
    private const val RATE = 44100

    private val cache = ConcurrentHashMap<String, ByteArray>()
    private val exec = Executors.newSingleThreadExecutor { r ->
        Thread(r, "gadget-fx").apply { isDaemon = true }
    }

    @Volatile
    private var soundsOn = true

    @Volatile
    private var hapticsOn = true

    @Volatile
    private var vibrator: Vibrator? = null

    fun init(context: Context) {
        if (vibrator != null) return
        vibrator = if (Build.VERSION.SDK_INT >= 31) {
            (context.getSystemService(Context.VIBRATOR_MANAGER_SERVICE) as? VibratorManager)
                ?.defaultVibrator
        } else {
            @Suppress("DEPRECATION")
            context.getSystemService(Context.VIBRATOR_SERVICE) as? Vibrator
        }
    }

    fun configure(sounds: Boolean, haptics: Boolean) {
        soundsOn = sounds
        hapticsOn = haptics
    }

    private fun pcm(key: String): ByteArray? {
        cache[key]?.let { return it }
        val spec = Sfxr.MAP[key] ?: return null
        val samples = Sfxr.render(Sfxr.preset(spec.first, Random(SEED)), Random(SEED))
        val vol = spec.second.toFloat()
        val bytes = ByteArray(samples.size * 2)
        for (i in samples.indices) {
            val s = (samples[i] * vol * Short.MAX_VALUE).toInt().coerceIn(-32768, 32767)
            bytes[i * 2] = (s and 0xFF).toByte()
            bytes[i * 2 + 1] = ((s shr 8) and 0xFF).toByte()
        }
        cache[key] = bytes
        return bytes
    }

    private fun buzz(key: String) {
        if (!hapticsOn) return
        val pattern = Sfxr.HAPTICS[key] ?: return
        val v = vibrator ?: return
        try {
            if (Build.VERSION.SDK_INT >= 26) {
                if (pattern.size == 1) {
                    v.vibrate(VibrationEffect.createOneShot(pattern[0], VibrationEffect.DEFAULT_AMPLITUDE))
                } else {
                    v.vibrate(VibrationEffect.createWaveform(longArrayOf(0L) + pattern, -1))
                }
            } else {
                @Suppress("DEPRECATION")
                if (pattern.size == 1) v.vibrate(pattern[0])
                else @Suppress("DEPRECATION") v.vibrate(longArrayOf(0L) + pattern, -1)
            }
        } catch (_: Throwable) {
            /* no vibrator */
        }
    }

    fun play(key: String) {
        buzz(key)
        if (!soundsOn) return
        val bytes = pcm(key) ?: return
        exec.execute {
            var track: AudioTrack? = null
            try {
                val minBuf = AudioTrack.getMinBufferSize(
                    RATE,
                    AudioFormat.CHANNEL_OUT_MONO,
                    AudioFormat.ENCODING_PCM_16BIT,
                ).coerceAtLeast(bytes.size)
                track = AudioTrack.Builder()
                    .setAudioAttributes(
                        AudioAttributes.Builder()
                            .setUsage(AudioAttributes.USAGE_ASSISTANCE_SONIFICATION)
                            .setContentType(AudioAttributes.CONTENT_TYPE_SONIFICATION)
                            .build(),
                    )
                    .setAudioFormat(
                        AudioFormat.Builder()
                            .setEncoding(AudioFormat.ENCODING_PCM_16BIT)
                            .setSampleRate(RATE)
                            .setChannelMask(AudioFormat.CHANNEL_OUT_MONO)
                            .build(),
                    )
                    .setBufferSizeInBytes(minBuf)
                    .setTransferMode(AudioTrack.MODE_STREAM)
                    .build()
                track.play()
                var off = 0
                while (off < bytes.size) {
                    val w = track.write(bytes, off, bytes.size - off)
                    if (w <= 0) break
                    off += w
                }
                // let the tail drain before releasing
                Thread.sleep((bytes.size / 2.0 / RATE * 1000).toLong() + 40)
            } catch (_: Throwable) {
                /* audio unavailable */
            } finally {
                try {
                    track?.stop()
                } catch (_: Throwable) {
                }
                track?.release()
            }
        }
    }

    /** The six named cues from `utils/audio.ts`. */
    fun hover() = play("hover")
    fun tap() = play("tap")
    fun open() = play("open")
    fun close() = play("close")
    fun confirm() = play("confirm")
    fun shake() = play("shake")
}
