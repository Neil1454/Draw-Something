package com.neil1454.drawsomething.core

import kotlin.math.PI
import kotlin.math.exp
import kotlin.math.max
import kotlin.math.min
import kotlin.math.sin
import kotlin.random.Random

/** Every sound effect in the app. They are generated in code, so there are no audio files to ship. */
enum class Sound {
    TAP, POP, GOOD, BAD, WIN, LOSE, EXPLODE, SPLASH, FLIP, TICK, CHIME, CARD, COIN, WHOOSH, DROP, ROLL, GO, BUZZ,
}

/**
 * A tiny synthesiser: each sound is a few tones/noise bursts mixed into 16-bit mono PCM.
 * Pure Kotlin so it can be tested; the Android side just plays the samples.
 */
object Synth {
    const val RATE = 22_050

    private enum class Wave { SINE, SQUARE, SAW, NOISE }

    /** A tone that glides from [f0] to [f1] Hz over [ms], starting [at] ms in. */
    private class Seg(val f0: Double, val f1: Double, val ms: Int, val vol: Double, val wave: Wave = Wave.SINE, val at: Int = 0, val decay: Double = 3.0)

    private fun seg(f: Int, ms: Int, vol: Double = 0.45, wave: Wave = Wave.SINE, at: Int = 0, to: Int = f, decay: Double = 3.0) =
        Seg(f.toDouble(), to.toDouble(), ms, vol, wave, at, decay)

    private fun spec(s: Sound): List<Seg> = when (s) {
        Sound.TAP -> listOf(seg(880, 55, 0.3, to = 640))
        Sound.POP -> listOf(seg(480, 90, 0.4, to = 900))
        Sound.GOOD -> listOf(seg(523, 90), seg(659, 90, at = 80), seg(784, 200, at = 160))
        Sound.BAD -> listOf(seg(240, 260, 0.4, Wave.SAW, to = 110))
        Sound.WIN -> listOf(
            seg(523, 120), seg(659, 120, at = 110), seg(784, 120, at = 220), seg(1047, 520, at = 330, decay = 2.0),
            seg(784, 520, 0.2, at = 330, decay = 2.0),
        )
        Sound.LOSE -> listOf(seg(392, 190, 0.4, Wave.SAW), seg(330, 190, 0.4, Wave.SAW, at = 170), seg(262, 420, 0.4, Wave.SAW, at = 340))
        Sound.EXPLODE -> listOf(Seg(0.0, 0.0, 420, 0.5, Wave.NOISE, 0, 5.0), seg(130, 380, 0.5, to = 38, decay = 4.0))
        Sound.SPLASH -> listOf(Seg(0.0, 0.0, 240, 0.25, Wave.NOISE, 0, 6.0), seg(420, 200, 0.25, to = 170))
        Sound.FLIP -> listOf(seg(600, 45, 0.3, Wave.SQUARE, to = 1000), seg(1000, 45, 0.25, Wave.SQUARE, at = 45, to = 700))
        Sound.TICK -> listOf(seg(1300, 28, 0.25))
        Sound.CHIME -> listOf(seg(1047, 300, 0.4, decay = 4.0), seg(1568, 300, 0.25, at = 90, decay = 4.0))
        Sound.CARD -> listOf(Seg(0.0, 0.0, 70, 0.22, Wave.NOISE, 0, 4.0), seg(210, 75, 0.35, to = 120))
        Sound.COIN -> listOf(seg(988, 70, 0.3, Wave.SQUARE), seg(1319, 280, 0.3, Wave.SQUARE, at = 70, decay = 4.0))
        Sound.WHOOSH -> listOf(Seg(0.0, 0.0, 280, 0.22, Wave.NOISE, 0, 2.0), seg(200, 260, 0.15, to = 700, decay = 2.0))
        Sound.DROP -> listOf(seg(520, 70, 0.3, to = 300), seg(180, 110, 0.5, to = 90, at = 140, decay = 5.0))
        Sound.ROLL -> listOf(
            Seg(0.0, 0.0, 60, 0.2, Wave.NOISE, 0, 3.0), Seg(0.0, 0.0, 60, 0.2, Wave.NOISE, 90, 3.0),
            Seg(0.0, 0.0, 60, 0.2, Wave.NOISE, 180, 3.0), seg(300, 70, 0.35, at = 270, to = 200),
        )
        Sound.GO -> listOf(seg(1200, 220, 0.5, Wave.SQUARE, decay = 1.5))
        Sound.BUZZ -> listOf(seg(150, 300, 0.4, Wave.SQUARE, decay = 1.5))
    }

    /** The samples for [sound]: 16-bit, mono, [RATE] Hz. */
    fun render(sound: Sound): ShortArray {
        val segs = spec(sound)
        val totalMs = segs.maxOf { it.at + it.ms } + 20
        val mix = DoubleArray(totalMs * RATE / 1000)
        val rnd = Random(sound.ordinal + 1)
        for (s in segs) {
            val start = s.at * RATE / 1000
            val n = s.ms * RATE / 1000
            var phase = 0.0
            for (i in 0 until n) {
                val idx = start + i
                if (idx >= mix.size) break
                val t = i.toDouble() / n
                val freq = s.f0 + (s.f1 - s.f0) * t
                phase += 2 * PI * freq / RATE
                val raw = when (s.wave) {
                    Wave.SINE -> sin(phase)
                    Wave.SQUARE -> if (sin(phase) >= 0) 0.6 else -0.6
                    Wave.SAW -> 2.0 * ((phase / (2 * PI)) % 1.0) - 1.0
                    Wave.NOISE -> rnd.nextDouble() * 2 - 1
                }
                val attack = min(1.0, i / (RATE * 0.004))
                val env = attack * exp(-s.decay * t) * (if (t > 0.9) (1.0 - t) / 0.1 else 1.0)
                mix[idx] += raw * s.vol * env
            }
        }
        return ShortArray(mix.size) { (max(-1.0, min(1.0, mix[it])) * 30_000).toInt().toShort() }
    }

    fun durationMs(sound: Sound): Int = render(sound).size * 1000 / RATE
}
