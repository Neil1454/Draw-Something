package com.neil1454.drawsomething.sound

import android.content.Context
import android.media.AudioAttributes
import android.media.AudioFormat
import android.media.AudioTrack
import com.neil1454.drawsomething.core.Sound
import com.neil1454.drawsomething.core.Synth
import java.util.EnumMap
import java.util.concurrent.Executors
import java.util.concurrent.ScheduledExecutorService
import java.util.concurrent.TimeUnit
import java.util.concurrent.atomic.AtomicInteger

/**
 * Plays the synthesised sound effects. Silent (and harmless) if the device can't make the sound,
 * and switched off for good by [enabled] = false (remembered between runs).
 */
object Sfx {
    private const val PREFS = "draw_something"
    private const val KEY = "sound_on"

    private var appContext: Context? = null
    private val cache = EnumMap<Sound, ShortArray>(Sound::class.java)
    private val worker: ScheduledExecutorService by lazy { Executors.newSingleThreadScheduledExecutor { r -> Thread(r, "sfx").apply { isDaemon = true } } }
    private val active = AtomicInteger(0)

    @Volatile
    var enabled: Boolean = true
        private set

    fun init(context: Context) {
        appContext = context.applicationContext
        enabled = try {
            context.applicationContext.getSharedPreferences(PREFS, Context.MODE_PRIVATE).getBoolean(KEY, true)
        } catch (e: Exception) {
            true
        }
    }

    fun setEnabled(on: Boolean) {
        enabled = on
        try {
            appContext?.getSharedPreferences(PREFS, Context.MODE_PRIVATE)?.edit()?.putBoolean(KEY, on)?.apply()
        } catch (e: Exception) {
            // not remembered; still works for this run
        }
    }

    fun play(sound: Sound) {
        if (!enabled || active.get() >= 8) return
        active.incrementAndGet()
        worker.execute {
            try {
                val data = synchronized(cache) { cache.getOrPut(sound) { Synth.render(sound) } }
                val track = AudioTrack.Builder()
                    .setAudioAttributes(
                        AudioAttributes.Builder()
                            .setUsage(AudioAttributes.USAGE_GAME)
                            .setContentType(AudioAttributes.CONTENT_TYPE_SONIFICATION)
                            .build(),
                    )
                    .setAudioFormat(
                        AudioFormat.Builder()
                            .setEncoding(AudioFormat.ENCODING_PCM_16BIT)
                            .setSampleRate(Synth.RATE)
                            .setChannelMask(AudioFormat.CHANNEL_OUT_MONO)
                            .build(),
                    )
                    .setBufferSizeInBytes(data.size * 2)
                    .setTransferMode(AudioTrack.MODE_STATIC)
                    .build()
                track.write(data, 0, data.size)
                track.play()
                worker.schedule({
                    try {
                        track.release()
                    } catch (e: Exception) {
                        // already gone
                    }
                    active.decrementAndGet()
                }, (data.size * 1000L / Synth.RATE) + 150, TimeUnit.MILLISECONDS)
            } catch (e: Exception) {
                active.decrementAndGet()
            }
        }
    }
}
