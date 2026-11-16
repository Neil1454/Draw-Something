package com.neil1454.drawsomething.sound

import android.content.Context
import android.os.Build
import android.os.VibrationEffect
import android.os.Vibrator
import android.os.VibratorManager
import com.neil1454.drawsomething.core.Sound

/**
 * Vibration for every game. Each sound effect has a matching feel (a tick for a tap, a rumble for an explosion, a rising
 * double-buzz for a win), so anything that makes a noise in a game also gives your hand a little feedback.
 * Harmless on phones that can't vibrate, and switched off for good by [enabled] = false (remembered between runs).
 */
object Haptics {
    private const val PREFS = "draw_something"
    private const val KEY = "haptics_on"

    private var vibrator: Vibrator? = null
    private var appContext: Context? = null
    private var lastAt = 0L

    @Volatile
    var enabled: Boolean = true
        private set

    @Suppress("DEPRECATION")
    fun init(context: Context) {
        val app = context.applicationContext
        appContext = app
        vibrator = try {
            if (Build.VERSION.SDK_INT >= 31) (app.getSystemService(Context.VIBRATOR_MANAGER_SERVICE) as? VibratorManager)?.defaultVibrator
            else app.getSystemService(Context.VIBRATOR_SERVICE) as? Vibrator
        } catch (e: Exception) {
            null
        }
        enabled = try {
            app.getSharedPreferences(PREFS, Context.MODE_PRIVATE).getBoolean(KEY, true)
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
        if (on) play(Sound.GOOD)
    }

    /** The feel that goes with a sound effect. */
    fun play(sound: Sound) {
        when (sound) {
            Sound.TAP, Sound.TICK, Sound.FLIP -> pulse(8, 40)
            Sound.POP, Sound.CARD -> pulse(12, 70)
            Sound.GOOD, Sound.COIN, Sound.CHIME -> pulse(22, 110)
            Sound.BAD -> wave(longArrayOf(0, 40, 30, 50), intArrayOf(0, 160, 0, 200))
            Sound.BUZZ -> pulse(70, 200)
            Sound.DROP -> pulse(28, 140)
            Sound.THUD -> pulse(45, 220)
            Sound.ZAP -> pulse(14, 90)
            Sound.SPLASH -> pulse(30, 90)
            Sound.GO -> wave(longArrayOf(0, 30, 40, 60), intArrayOf(0, 120, 0, 220))
            Sound.ROLL -> wave(longArrayOf(0, 15, 25, 15, 25, 15, 25, 25), intArrayOf(0, 90, 0, 120, 0, 150, 0, 200))
            Sound.WHOOSH -> pulse(16, 50)
            Sound.EXPLODE -> boom()
            Sound.WIN -> wave(longArrayOf(0, 40, 50, 40, 50, 120), intArrayOf(0, 120, 0, 180, 0, 255))
            Sound.LOSE -> wave(longArrayOf(0, 120, 80, 200), intArrayOf(0, 200, 0, 120))
            Sound.RING -> wave(longArrayOf(0, 60, 50, 60, 50, 60, 200, 60, 50, 60), intArrayOf(0, 180, 0, 180, 0, 180, 0, 180, 0, 180))
            Sound.PICKUP, Sound.REEL, Sound.SPARKLE -> pulse(10, 60)
            Sound.BOX_CREAK, Sound.GASP, Sound.SUSPENSE -> pulse(30, 90)
            Sound.BOX_OPEN, Sound.STOP, Sound.LEVER -> pulse(40, 200)
            Sound.DRUMROLL, Sound.BANKER -> wave(longArrayOf(0, 25, 20, 25, 20, 25, 20, 25, 20, 80), intArrayOf(0, 70, 0, 90, 0, 110, 0, 130, 0, 200))
            Sound.CASH, Sound.JACKPOT -> wave(longArrayOf(0, 40, 40, 40, 40, 40, 40, 140), intArrayOf(0, 150, 0, 190, 0, 230, 0, 255))
            Sound.JINGLE_A, Sound.JINGLE_B, Sound.JINGLE_C, Sound.JINGLE_D, Sound.JINGLE_E, Sound.JINGLE_F, Sound.JINGLE_G, Sound.JINGLE_H -> pulse(14, 80)
        }
    }

    /** A sharp double-buzz and a rumble: mines, bombs and explosions. */
    fun boom() = wave(longArrayOf(0, 90, 50, 70, 40, 260), intArrayOf(0, 255, 0, 200, 0, 140))

    private fun pulse(ms: Long, amp: Int) {
        val v = ready() ?: return
        try {
            v.vibrate(VibrationEffect.createOneShot(ms, if (v.hasAmplitudeControl()) amp.coerceIn(1, 255) else VibrationEffect.DEFAULT_AMPLITUDE))
        } catch (e: Exception) {
            // a phone that refuses: carry on silently
        }
    }

    private fun wave(timings: LongArray, amps: IntArray) {
        val v = ready() ?: return
        try {
            v.vibrate(
                if (v.hasAmplitudeControl()) VibrationEffect.createWaveform(timings, amps, -1)
                else VibrationEffect.createWaveform(timings, -1),
            )
        } catch (e: Exception) {
            // a phone that refuses: carry on silently
        }
    }

    /** The vibrator, or null if vibration is off or it was used a moment ago (so a burst of sounds is not a constant buzz). */
    @Synchronized
    private fun ready(): Vibrator? {
        if (!enabled) return null
        val v = vibrator ?: return null
        val now = android.os.SystemClock.uptimeMillis()
        if (now - lastAt < 45) return null
        lastAt = now
        return if (v.hasVibrator()) v else null
    }
}
