package com.neil1454.drawsomething.core

import kotlin.math.PI
import kotlin.math.abs
import kotlin.math.exp
import kotlin.math.max
import kotlin.math.min
import kotlin.math.sin

/** Every sound effect in the app. They are generated in code (see [Studio]), so there are no audio files to ship. */
enum class Sound {
    TAP, POP, GOOD, BAD, WIN, LOSE, EXPLODE, SPLASH, FLIP, TICK, CHIME, CARD, COIN, WHOOSH, DROP, ROLL, GO, BUZZ, ZAP, THUD,
    // game-show and casino sounds
    RING, PICKUP, BOX_CREAK, BOX_OPEN, DRUMROLL, BANKER, SUSPENSE, CASH, JACKPOT, REEL, STOP, LEVER, SPARKLE, GASP,
    // somebody talking on the telephone, a clapping crowd, a cheering crowd, a crowd going "oooh", and a knock on a wooden box
    VOICE, APPLAUSE, CHEER, GROAN, KNOCK,
    // a short signature jingle for each game's title card: every game gets one of these
    JINGLE_A, JINGLE_B, JINGLE_C, JINGLE_D, JINGLE_E, JINGLE_F, JINGLE_G, JINGLE_H,
}

/**
 * Turns the [Studio]'s recipes into 16-bit mono samples. Pure Kotlin so it can be tested; the Android side just plays the samples.
 */
object Synth {
    const val RATE = 22_050

    /** The sounds that are allowed to run longer than a second (speech, crowds). */
    val LONG = setOf(Sound.VOICE, Sound.APPLAUSE, Sound.CHEER, Sound.GROAN)

    /** How loud each sound should be at its peak, as a fraction of full scale (clicks and ticks are kept gentle). */
    private val LEVEL = mapOf(
        Sound.TAP to 0.45, Sound.TICK to 0.40, Sound.REEL to 0.40, Sound.FLIP to 0.55, Sound.CARD to 0.55, Sound.KNOCK to 0.75, Sound.ROLL to 0.6,
        Sound.APPLAUSE to 0.70, Sound.CHEER to 0.75, Sound.GROAN to 0.70, Sound.VOICE to 0.75, Sound.RING to 0.75, Sound.COIN to 0.7,
    )

    /** The samples for [sound]: 16-bit, mono, [RATE] Hz. */
    fun render(sound: Sound): ShortArray {
        // small phone speakers can't make deep notes, so the sub-bass that would only eat the headroom is taken out
        val x = Studio.highpass(Studio.sound(sound), 110.0)
        val peak = x.maxOf { abs(it) }.coerceAtLeast(1e-9)
        val g = (LEVEL[sound] ?: 0.85) * 30_000.0 / peak
        return ShortArray(x.size) { max(-32_767.0, min(32_767.0, x[it] * g)).toInt().toShort() }
    }

    /** The quiet tune for Deal or No Deal (an original tense underscore, about 20 seconds, looping). */
    fun renderDealMusic(): ShortArray {
        val x = Studio.highpass(Studio.highpass(Studio.dealMusic(), 130.0), 130.0)
        val peak = x.maxOf { abs(it) }.coerceAtLeast(1e-9)
        return ShortArray(x.size) { max(-32_767.0, min(32_767.0, x[it] / peak * 17_000.0)).toInt().toShort() }
    }

    /** A calm original loop (8 bars, ~14 s) for long Tetris sessions: soft bass, gentle arpeggio, simple melody. Loops seamlessly. */
    fun renderMusic(): ShortArray {
        val eighthMs = 220
        val n = eighthMs * RATE / 1000
        // semitones from A3 (220 Hz); -99 = rest
        val bass = intArrayOf(-12, -12, -7, -7, -4, -4, -2, -2) // per bar: A F C G, then repeats with variation
        val roots = intArrayOf(0, -4, 3, -2, 0, -4, 3, -2) // chord roots per bar (A, F, C, G)
        val melody = intArrayOf(
            12, -99, 15, -99, 19, -99, 15, 12,  10, -99, 12, -99, 17, -99, 15, -99,
            15, -99, 19, -99, 22, -99, 19, 15,  14, -99, 17, -99, 14, -99, 10, -99,
            12, -99, 15, -99, 19, -99, 24, 22,  21, -99, 17, -99, 12, -99, 17, 21,
            19, -99, 22, -99, 19, -99, 14, -99,  12, -99, -99, -99, 7, -99, -99, -99,
        )
        val total = 64 * n
        val mix = DoubleArray(total)
        fun note(startEighth: Int, eighths: Int, semis: Int, vol: Double, square: Boolean, decay: Double) {
            val f = 220.0 * Math.pow(2.0, semis / 12.0)
            val len = eighths * n
            val st = startEighth * n
            var ph = 0.0
            for (i in 0 until len) {
                val idx = st + i
                if (idx >= total) break
                ph += 2 * PI * f / RATE
                val t = i.toDouble() / len
                val raw = if (square) (if (sin(ph) >= 0) 0.5 else -0.5) + 0.5 * sin(ph) else sin(ph)
                val env = min(1.0, i / (RATE * 0.01)) * exp(-decay * t) * (if (t > 0.85) (1.0 - t) / 0.15 else 1.0)
                mix[idx] += raw * vol * env
            }
        }
        for (bar in 0 until 8) {
            val r = roots[bar]
            note(bar * 8, 4, r - 12, 0.20, false, 1.2)
            note(bar * 8 + 4, 4, r - 12 + 7, 0.16, false, 1.2)
            for (k in 0 until 8) { // soft arpeggio
                val off = intArrayOf(0, 7, 12, 7, 0, 7, 12, 7)[k] + (if (bar % 4 == 1 || bar % 4 == 3) 0 else 3 * 0)
                note(bar * 8 + k, 1, r + off, 0.05, false, 2.5)
            }
        }
        for (i in melody.indices) {
            if (melody[i] > -90) {
                var len = 1
                while (i + len < melody.size && melody[i + len] == -99 && len < 2) len++
                note(i, len, melody[i], 0.13, true, 2.0)
            }
        }
        return ShortArray(total) { (max(-1.0, min(1.0, mix[it] * 1.4)) * 14_000).toInt().toShort() }
    }

    fun durationMs(sound: Sound): Int = render(sound).size * 1000 / RATE
}
