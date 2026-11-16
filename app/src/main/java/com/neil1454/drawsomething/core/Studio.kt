package com.neil1454.drawsomething.core

import kotlin.math.PI
import kotlin.math.abs
import kotlin.math.cos
import kotlin.math.exp
import kotlin.math.max
import kotlin.math.min
import kotlin.math.pow
import kotlin.math.sin
import kotlin.math.tanh
import kotlin.random.Random

// ====================================================================================
// A small recording studio, in code. Rather than the beeps and square waves of an old games console, the sounds are built the way real ones are:
// bells and coins from the inharmonic overtones of struck metal, plucked strings (Karplus-Strong), brass with an opening filter, drums from a falling
// pitch and a burst of noise, crowds from hundreds of random claps, and voices from a buzzing "throat" run through the resonances of a mouth (formants).
// Everything is plain arithmetic on arrays, so it is repeatable and testable, and it needs no sound files.
// ====================================================================================

internal object Studio {
    private const val R = Synth.RATE

    private fun samples(ms: Int) = (ms * R / 1000).coerceAtLeast(1)

    // ------------------------------------------------------------------ filters and effects

    fun noise(rnd: Random, n: Int) = DoubleArray(n) { rnd.nextDouble() * 2.0 - 1.0 }

    fun lowpass(x: DoubleArray, fc: Double): DoubleArray {
        val a = 1.0 - exp(-2.0 * PI * fc / R)
        val y = DoubleArray(x.size)
        var s = 0.0
        for (i in x.indices) { s += a * (x[i] - s); y[i] = s }
        return y
    }

    fun highpass(x: DoubleArray, fc: Double): DoubleArray {
        val l = lowpass(x, fc)
        return DoubleArray(x.size) { x[it] - l[it] }
    }

    /** A resonant band-pass filter (a biquad). */
    fun bandpass(x: DoubleArray, f: Double, q: Double): DoubleArray {
        val w0 = 2.0 * PI * f / R
        val alpha = sin(w0) / (2.0 * q)
        val a0 = 1.0 + alpha
        val b0 = alpha / a0; val b2 = -alpha / a0
        val a1 = -2.0 * cos(w0) / a0; val a2 = (1.0 - alpha) / a0
        val y = DoubleArray(x.size)
        var x1 = 0.0; var x2 = 0.0; var y1 = 0.0; var y2 = 0.0
        for (i in x.indices) {
            val v = b0 * x[i] + b2 * x2 - a1 * y1 - a2 * y2
            x2 = x1; x1 = x[i]; y2 = y1; y1 = v; y[i] = v
        }
        return y
    }

    /** A Schroeder reverb: four echoing combs and two smearing all-pass filters give the sound a room to live in. */
    fun reverb(x: DoubleArray, mix: Double, tailMs: Int = 450, room: Double = 1.0): DoubleArray {
        val n = x.size + samples(tailMs)
        val input = x.copyOf(n)
        val delays = intArrayOf(29, 37, 41, 44).map { (it * room * R / 1000.0).toInt().coerceAtLeast(8) }
        val wet = DoubleArray(n)
        for (d in delays) {
            val buf = DoubleArray(d)
            var idx = 0
            val g = 0.74
            for (i in 0 until n) {
                val out = buf[idx]
                buf[idx] = input[i] + out * g
                idx = (idx + 1) % d
                wet[i] += out * 0.25
            }
        }
        // two all-pass stages smear the echoes into a wash
        var cur = wet
        for (ms in doubleArrayOf(5.0, 1.7)) {
            val d = (ms * R / 1000.0).toInt().coerceAtLeast(2)
            val buf = DoubleArray(d)
            var idx = 0
            val g = 0.68
            val o = DoubleArray(n)
            for (i in 0 until n) {
                val y = -g * cur[i] + buf[idx]
                buf[idx] = cur[i] + g * y
                o[i] = y
                idx = (idx + 1) % d
            }
            cur = o
        }
        return DoubleArray(n) { i -> (if (i < x.size) x[i] * (1.0 - mix) else 0.0) + cur[i] * mix * 1.6 }
    }

    fun gain(x: DoubleArray, g: Double): DoubleArray = DoubleArray(x.size) { x[it] * g }

    /** Adds [src] into [dst] starting [startMs] in. */
    fun mix(dst: DoubleArray, src: DoubleArray, startMs: Int, g: Double = 1.0) {
        val s = (startMs * R / 1000)
        for (i in src.indices) { val j = s + i; if (j >= dst.size) break; if (j >= 0) dst[j] += src[i] * g }
    }

    private fun exp(t: Double, tau: Double) = exp(-t / tau)

    // ------------------------------------------------------------------ instruments

    /** Struck metal: a bell, a coin, a glass. The overtones are not harmonic, which is what makes them ring like metal. */
    fun bell(freq: Double, ms: Int, decaySec: Double, bright: Double = 1.0, rnd: Random? = null): DoubleArray {
        val n = samples(ms)
        val ratios = doubleArrayOf(1.0, 2.0, 2.76, 5.4, 8.93)
        val amps = doubleArrayOf(1.0, 0.55, 0.45, 0.22 * bright, 0.10 * bright)
        val phase = DoubleArray(5) { rnd?.nextDouble()?.times(2 * PI) ?: 0.0 }
        val out = DoubleArray(n)
        for (k in ratios.indices) {
            val f = freq * ratios[k]
            if (f > R / 2.2) continue
            val tau = decaySec / (1.0 + k * 0.9)
            for (i in 0 until n) {
                val t = i.toDouble() / R
                out[i] += sin(2 * PI * f * t + phase[k]) * amps[k] * exp(t, tau)
            }
        }
        val a = (R * 0.0015).toInt()
        for (i in 0 until min(a, n)) out[i] *= i.toDouble() / a
        return out
    }

    /** A plucked string (Karplus-Strong): a burst of noise circulating in a delay line that loses its brightness as it rings. */
    fun pluck(freq: Double, ms: Int, rnd: Random, damping: Double = 0.996): DoubleArray {
        val n = samples(ms)
        val len = max(2, (R / freq).toInt())
        val buf = lowpass(noise(rnd, len), 5200.0).let { b -> DoubleArray(len) { b[it] } }
        val out = DoubleArray(n)
        var idx = 0
        for (i in 0 until n) {
            val cur = buf[idx]
            out[i] = cur
            buf[idx] = 0.5 * (cur + buf[(idx + 1) % len]) * damping
            idx = (idx + 1) % len
        }
        return out
    }

    /** Brass: a bright sawtooth whose filter opens as the note speaks, with a touch of vibrato. */
    fun brass(freq: Double, ms: Int, attackMs: Int = 50, vibrato: Double = 0.004): DoubleArray {
        val n = samples(ms)
        val out = DoubleArray(n)
        var phase = 0.0
        var lp = 0.0
        val atk = samples(attackMs)
        val rel = samples(min(120, ms / 3))
        for (i in 0 until n) {
            val t = i.toDouble() / R
            val f = freq * (1.0 + vibrato * sin(2 * PI * 5.2 * t) * min(1.0, t * 3))
            phase += f / R
            val saw = 2.0 * (phase - kotlin.math.floor(phase)) - 1.0
            val open = min(1.0, i.toDouble() / (atk * 1.6))
            val cut = 500.0 + 3200.0 * open
            val a = 1.0 - exp(-2.0 * PI * cut / R)
            lp += a * (saw - lp)
            val env = min(1.0, i.toDouble() / atk) * (if (i > n - rel) (n - i).toDouble() / rel else 1.0)
            out[i] = lp * env
        }
        return out
    }

    /** Strings or a choir pad: three slightly detuned sawtooths, softened, with a slow swell. */
    fun pad(freq: Double, ms: Int, attackMs: Int = 400): DoubleArray {
        val n = samples(ms)
        val raw = DoubleArray(n)
        for (cents in doubleArrayOf(-8.0, 0.0, 8.0)) {
            val f = freq * 2.0.pow(cents / 1200.0)
            var phase = 0.0
            for (i in 0 until n) { phase += f / R; raw[i] += (2.0 * (phase - kotlin.math.floor(phase)) - 1.0) / 3.0 }
        }
        val soft = lowpass(raw, 1300.0)
        val atk = samples(attackMs); val rel = samples(min(600, ms / 2))
        for (i in 0 until n) soft[i] *= min(1.0, i.toDouble() / atk) * (if (i > n - rel) (n - i).toDouble() / rel else 1.0)
        return soft
    }

    /** A kettle drum: a thump whose pitch falls as it speaks, with the slap of the stick. */
    fun timpani(freq: Double, ms: Int, rnd: Random): DoubleArray {
        val n = samples(ms)
        val out = DoubleArray(n)
        var phase = 0.0
        for (i in 0 until n) {
            val t = i.toDouble() / R
            val f = freq * (1.0 + 0.6 * exp(t, 0.05))
            phase += f / R
            out[i] = sin(2 * PI * phase) * exp(t, 0.30) + 0.45 * sin(2 * PI * phase * 1.5) * exp(t, 0.12)
        }
        val slap = lowpass(noise(rnd, samples(30)), 900.0)
        for (i in slap.indices) if (i < n) out[i] += slap[i] * 0.6 * (1.0 - i.toDouble() / slap.size)
        return out
    }

    fun kick(ms: Int = 220): DoubleArray {
        val n = samples(ms)
        val out = DoubleArray(n)
        var phase = 0.0
        for (i in 0 until n) { val t = i.toDouble() / R; phase += (46.0 + 110.0 * exp(t, 0.035)) / R; out[i] = sin(2 * PI * phase) * exp(t, 0.12) }
        return out
    }

    fun snare(rnd: Random, ms: Int = 170, vel: Double = 1.0): DoubleArray {
        val n = samples(ms)
        val body = bandpass(noise(rnd, n), 1900.0, 0.7)
        val out = DoubleArray(n)
        for (i in 0 until n) {
            val t = i.toDouble() / R
            out[i] = (body[i] * exp(t, 0.06) * 1.6 + sin(2 * PI * 185.0 * t) * exp(t, 0.05) * 0.5) * vel
        }
        return out
    }

    fun hat(rnd: Random, ms: Int = 70): DoubleArray {
        val n = samples(ms)
        val h = highpass(noise(rnd, n), 6500.0)
        return DoubleArray(n) { h[it] * exp(it.toDouble() / R, 0.018) * 0.7 }
    }

    /** Something knocking on a wooden box. */
    fun knock(rnd: Random, freq: Double = 190.0, ms: Int = 150): DoubleArray {
        val n = samples(ms)
        val body = bandpass(noise(rnd, n), 650.0, 3.0)
        return DoubleArray(n) { i -> val t = i.toDouble() / R; (body[i] * exp(t, 0.025) * 2.0 + sin(2 * PI * freq * t) * exp(t, 0.045)) * min(1.0, i.toDouble() / 18) }
    }

    // ------------------------------------------------------------------ voices

    private class Vowel(val f1: Double, val f2: Double, val f3: Double)
    private val VOWELS = listOf(Vowel(300.0, 2300.0, 3000.0), Vowel(500.0, 1900.0, 2500.0), Vowel(750.0, 1200.0, 2500.0), Vowel(500.0, 900.0, 2400.0), Vowel(330.0, 800.0, 2300.0))

    /** A buzzing glottal source: a sawtooth with a little breath in it, at a pitch given by [f0] for each sample. */
    private fun source(n: Int, f0: (Int) -> Double, rnd: Random, breath: Double): DoubleArray {
        val out = DoubleArray(n)
        var phase = 0.0
        for (i in 0 until n) {
            phase += f0(i) / R
            val saw = 2.0 * (phase - kotlin.math.floor(phase)) - 1.0
            // a pulse-like source: emphasise the start of each cycle
            val frac = phase - kotlin.math.floor(phase)
            out[i] = (saw * 0.6 + (if (frac < 0.08) 0.6 else 0.0)) + (rnd.nextDouble() * 2.0 - 1.0) * breath
        }
        return out
    }

    /** Runs a source through three resonances (the formants of a vowel), which may glide from one vowel to another. */
    private fun mouth(src: DoubleArray, formants: (Int) -> Triple<Double, Double, Double>): DoubleArray {
        val n = src.size
        val out = DoubleArray(n)
        val y1 = DoubleArray(3); val y2 = DoubleArray(3)
        val bws = doubleArrayOf(80.0, 110.0, 160.0)
        val gains = doubleArrayOf(1.0, 0.7, 0.4)
        for (i in 0 until n) {
            val (a, b, c) = formants(i)
            val fs = doubleArrayOf(a, b, c)
            var sum = 0.0
            for (k in 0 until 3) {
                val r = exp(-PI * bws[k] / R)
                val c1 = 2.0 * r * cos(2.0 * PI * fs[k] / R)
                val c2 = -r * r
                val v = (1.0 - r) * src[i] + c1 * y1[k] + c2 * y2[k]
                y2[k] = y1[k]; y1[k] = v
                sum += v * gains[k]
            }
            out[i] = sum
        }
        return out
    }

    /** Somebody talking on a bad telephone line: mumbled syllables with a falling sentence tune, squeezed into the narrow band a phone passes. */
    fun phoneVoice(ms: Int, seed: Int): DoubleArray {
        val rnd = Random(seed)
        val n = samples(ms)
        // plan the syllables
        class Syl(val start: Int, val len: Int, val v: Vowel, val fric: Boolean)
        val sylls = ArrayList<Syl>()
        var t = (R * 0.05).toInt()
        var wordLeft = 2 + rnd.nextInt(3)
        while (t < n - R / 10) {
            val len = (R * (0.10 + rnd.nextDouble() * 0.11)).toInt()
            sylls.add(Syl(t, len, VOWELS[rnd.nextInt(VOWELS.size)], rnd.nextInt(100) < 40))
            t += len
            wordLeft--
            if (wordLeft <= 0) { t += (R * (0.07 + rnd.nextDouble() * 0.12)).toInt(); wordLeft = 2 + rnd.nextInt(3) }
        }
        val amp = DoubleArray(n)
        for (s in sylls) for (i in 0 until s.len) { val k = s.start + i; if (k < n) amp[k] = sin(PI * i / s.len).pow(0.6) }
        val vowelAt = Array(n) { VOWELS[2] }
        var sy = 0
        for (i in 0 until n) { while (sy < sylls.size - 1 && i >= sylls[sy + 1].start) sy++; vowelAt[i] = sylls.getOrNull(sy)?.v ?: VOWELS[2] }
        val src = source(n, { i ->
            val tt = i.toDouble() / R
            val decline = 1.0 - 0.10 * (i.toDouble() / n)
            (112.0 * decline + 13.0 * sin(2 * PI * 0.55 * tt) + 5.0 * sin(2 * PI * 3.1 * tt)) * (1.0 + 0.006 * (rnd.nextDouble() - 0.5))
        }, rnd, 0.07)
        val glide = 0.0035
        var f1 = 500.0; var f2 = 1500.0; var f3 = 2500.0
        val voiced = mouth(src) { i ->
            val v = vowelAt[i]
            f1 += (v.f1 - f1) * glide * 4; f2 += (v.f2 - f2) * glide * 4; f3 += (v.f3 - f3) * glide * 4
            Triple(f1, f2, f3)
        }
        // the hissy consonants: a burst of high noise at the front of some syllables
        val hiss = highpass(noise(rnd, n), 3800.0)
        val out = DoubleArray(n)
        for (s in sylls) if (s.fric) for (i in 0 until (R * 0.045).toInt()) { val k = s.start + i; if (k < n) out[k] += hiss[k] * 0.55 * (1.0 - i / (R * 0.045)) }
        for (i in 0 until n) out[i] += voiced[i] * amp[i]
        // the telephone: only a narrow band gets through, it distorts, and the line hisses and crackles
        var band = lowpass(lowpass(highpass(highpass(highpass(out, 380.0), 380.0), 380.0), 3200.0), 3200.0)
        val peak = band.maxOf { abs(it) }.coerceAtLeast(1e-9)
        band = highpass(highpass(DoubleArray(n) { tanh(band[it] / peak * 1.6) }, 330.0), 330.0)
        val line = lowpass(noise(rnd, n), 2600.0)
        for (i in 0 until n) {
            band[i] = band[i] * 0.8 + line[i] * 0.045
            if (rnd.nextInt(2400) == 0) band[i] += (rnd.nextDouble() - 0.5) * 0.7 // a crackle
        }
        return band
    }

    /** A crowd going "ooooh": several voices on a low vowel, drifting down together, with the room around them. */
    fun crowdOoh(ms: Int, seed: Int): DoubleArray {
        val rnd = Random(seed)
        val n = samples(ms)
        val mixed = DoubleArray(n)
        for (v in 0 until 7) {
            val base = 100.0 + rnd.nextDouble() * 120.0
            val drift = 0.10 + rnd.nextDouble() * 0.06
            val delay = rnd.nextInt(90)
            val ph = rnd.nextDouble() * 6.0
            val src = source(n, { i -> val tt = i.toDouble() / R; base * (1.0 - drift * (i.toDouble() / n)) * (1.0 + 0.012 * sin(2 * PI * 5.5 * tt + ph)) }, rnd, 0.10)
            val vow = Vowel(340.0 + rnd.nextDouble() * 40, 800.0 + rnd.nextDouble() * 80, 2400.0)
            val voiced = mouth(src) { Triple(vow.f1, vow.f2, vow.f3) }
            for (i in 0 until n) {
                val tt = i.toDouble() / R
                val env = min(1.0, tt / 0.30) * (if (tt > ms / 1000.0 - 0.5) max(0.0, (ms / 1000.0 - tt) / 0.5) else 1.0)
                val k = i + delay * R / 1000
                if (k < n) mixed[k] += voiced[i] * env / 7.0
            }
        }
        return reverb(highpass(mixed, 170.0), 0.30, 400)
    }

    /** A crowd clapping: hundreds of separate little claps, which swell and fade. */
    fun applause(ms: Int, seed: Int, density: Double = 1.0): DoubleArray {
        val rnd = Random(seed)
        val n = samples(ms)
        val out = DoubleArray(n)
        val body = lowpass(noise(rnd, n), 2200.0)
        val clapLen = samples(14)
        val shape = DoubleArray(clapLen) { exp(it.toDouble() / R, 0.004) }
        var i = 0
        while (i < n) {
            val tt = i.toDouble() / R
            val total = ms / 1000.0
            val swell = min(1.0, tt / 0.25) * (if (tt > total - 0.55) max(0.0, (total - tt) / 0.55) else 1.0)
            if (rnd.nextDouble() < swell * 0.012 * density) {
                val g = 0.25 + rnd.nextDouble() * 0.75
                val tone = 1400.0 + rnd.nextDouble() * 1800.0
                val burst = bandpass(noise(rnd, clapLen), tone, 0.8)
                for (k in 0 until clapLen) if (i + k < n) out[i + k] += burst[k] * shape[k] * g * 3.5
            }
            out[i] += body[i] * swell * 0.09 * density
            i++
        }
        return reverb(out, 0.22, 350)
    }

    // ------------------------------------------------------------------ the sounds themselves

    private fun note(semitonesFromA4: Int) = 440.0 * 2.0.pow(semitonesFromA4 / 12.0)

    /** [Studio.sound] result: samples at a level of about ±1, to be scaled by the caller. */
    fun sound(s: Sound): DoubleArray {
        val rnd = Random(s.ordinal + 101)
        return when (s) {
            Sound.TAP -> knock(rnd, 520.0, 60).let { gain(it, 0.8) }
            Sound.POP -> { val o = DoubleArray(samples(120)); var ph = 0.0; for (i in o.indices) { val t = i.toDouble() / R; ph += (380.0 + 700.0 * min(1.0, t / 0.05)) / R; o[i] = sin(2 * PI * ph) * exp(t, 0.03) }; o }
            Sound.GOOD -> DoubleArray(samples(520)).also { mix(it, bell(784.0, 360, 0.25), 0); mix(it, bell(988.0, 360, 0.25), 90); mix(it, bell(1175.0, 480, 0.3), 190) }
            Sound.BAD -> DoubleArray(samples(520)).also { mix(it, lowpass(brass(147.0, 450, 30, 0.01), 900.0), 0); mix(it, lowpass(brass(139.0, 450, 30, 0.01), 900.0), 160, 0.8) }
            Sound.WIN -> DoubleArray(samples(1000)).also { mix(it, pluck(523.0, 300, rnd), 0); mix(it, pluck(659.0, 300, rnd), 110); mix(it, pluck(784.0, 300, rnd), 220); mix(it, brass(1047.0 / 2, 650, 40), 330); mix(it, bell(1047.0, 650, 0.5), 330, 0.7) }.let { reverb(it, 0.18, 250) }
            Sound.LOSE -> DoubleArray(samples(900)).also { mix(it, brass(196.0, 300, 30, 0.008), 0); mix(it, brass(175.0, 300, 30, 0.008), 260, 0.9); mix(it, brass(147.0, 520, 30, 0.01), 520, 0.9) }.let { lowpass(it, 1100.0) }
            Sound.EXPLODE -> DoubleArray(samples(700)).also { val nz = lowpass(noise(rnd, it.size), 700.0); for (i in it.indices) { val t = i.toDouble() / R; it[i] = nz[i] * exp(t, 0.18) * 1.6 + sin(2 * PI * (60.0 - 25.0 * min(1.0, t / 0.4)) * t) * exp(t, 0.22) } }
            Sound.SPLASH -> DoubleArray(samples(400)).also { val nz = bandpass(noise(rnd, it.size), 2500.0, 0.5); for (i in it.indices) it[i] = nz[i] * exp(i.toDouble() / R, 0.12) * 1.5 }
            Sound.FLIP -> DoubleArray(samples(140)).also { val a = bandpass(noise(rnd, it.size), 2400.0, 1.2); for (i in it.indices) { val t = i.toDouble() / R; it[i] = a[i] * (exp(t, 0.015) + 0.7 * exp(max(0.0, t - 0.05), 0.02) * (if (t > 0.05) 1.0 else 0.0)) } }
            Sound.TICK -> knock(rnd, 900.0, 40).let { gain(it, 0.7) }
            Sound.CHIME -> DoubleArray(samples(900)).also { mix(it, bell(1047.0, 800, 0.6), 0); mix(it, bell(1568.0, 700, 0.5), 90, 0.6) }.let { reverb(it, 0.15, 200) }
            Sound.CARD -> DoubleArray(samples(110)).also { val a = bandpass(noise(rnd, it.size), 3200.0, 0.6); for (i in it.indices) it[i] = a[i] * exp(i.toDouble() / R, 0.022) * 1.4 + 0.2 * sin(2 * PI * 150.0 * i / R) * exp(i.toDouble() / R, 0.03) }
            Sound.COIN -> DoubleArray(samples(420)).also { mix(it, bell(2637.0, 380, 0.18, 1.3, rnd), 0); mix(it, bell(3520.0, 300, 0.14, 1.3, rnd), 55, 0.8) }
            Sound.WHOOSH -> DoubleArray(samples(380)).also { var lp = 0.0; for (i in it.indices) { val t = i.toDouble() / R; val cut = 300.0 + 3500.0 * sin(PI * min(1.0, t / 0.36)); val a = 1.0 - exp(-2.0 * PI * cut / R); lp += a * ((rnd.nextDouble() * 2 - 1) - lp); it[i] = lp * sin(PI * min(1.0, t / 0.36)) * 2.0 } }
            Sound.DROP -> DoubleArray(samples(300)).also { mix(it, knock(rnd, 160.0, 120), 0); mix(it, knock(rnd, 120.0, 120), 150, 1.1) }
            Sound.ROLL -> DoubleArray(samples(420)).also { var t = 0; while (t < 340) { mix(it, knock(rnd, 600.0 + rnd.nextInt(400), 40), t, 0.6 + rnd.nextDouble() * 0.5); t += 25 + rnd.nextInt(45) } }
            Sound.GO -> DoubleArray(samples(380)).also { mix(it, brass(392.0, 330, 25), 0); mix(it, brass(523.0, 330, 25), 0, 0.7) }
            Sound.BUZZ -> DoubleArray(samples(330)).also { val b = lowpass(brass(110.0, 300, 15, 0.0), 700.0); for (i in b.indices) it[i] = b[i] * 1.2 }
            Sound.ZAP -> DoubleArray(samples(160)).also { var ph = 0.0; for (i in it.indices) { val t = i.toDouble() / R; ph += (1700.0 - 1200.0 * min(1.0, t / 0.12)) / R; it[i] = sin(2 * PI * ph) * exp(t, 0.05) * 0.8 } }
            Sound.THUD -> DoubleArray(samples(260)).also { mix(it, knock(rnd, 90.0, 220), 0, 1.4) }
            Sound.RING -> {
                // an old telephone bell: two gongs struck in turn, very fast, in two bursts
                val o = DoubleArray(samples(1100))
                for (burst in 0..1) {
                    val start = burst * 560
                    var t = 0
                    var which = 0
                    while (t < 400) { mix(o, bell(if (which == 0) 1330.0 else 1520.0, 90, 0.05, 1.4, rnd), start + t, 0.7); which = 1 - which; t += 22 }
                }
                reverb(highpass(o, 400.0), 0.12, 120)
            }
            Sound.PICKUP -> DoubleArray(samples(400)).also { mix(it, knock(rnd, 260.0, 120), 0, 1.3); mix(it, bandpass(noise(rnd, samples(40)), 2500.0, 1.0), 140, 0.5); mix(it, knock(rnd, 300.0, 80), 230, 0.6) }
            Sound.BOX_CREAK -> DoubleArray(samples(520)).also {
                var ph = 0.0
                val squeak = DoubleArray(it.size)
                for (i in squeak.indices) { val t = i.toDouble() / R; val f = 480.0 + 330.0 * min(1.0, t / 0.45) + 40.0 * sin(2 * PI * 8.0 * t) + 25.0 * (rnd.nextDouble() - 0.5); ph += f / R; squeak[i] = sin(2 * PI * ph) * sin(PI * min(1.0, t / 0.5)) * 0.35 }
                val rumble = lowpass(noise(rnd, it.size), 220.0)
                for (i in it.indices) it[i] = squeak[i] + rumble[i] * 0.6 * sin(PI * i / it.size)
            }
            Sound.BOX_OPEN -> DoubleArray(samples(1000)).also {
                val nz = noise(rnd, it.size)
                var lp = 0.0
                for (i in 0 until samples(320)) { val t = i.toDouble() / R; val cut = 200.0 + 4200.0 * min(1.0, t / 0.22); val a = 1.0 - exp(-2.0 * PI * cut / R); lp += a * (nz[i] - lp); it[i] += lp * sin(PI * min(1.0, i.toDouble() / samples(320))) * 1.4 }
                mix(it, timpani(70.0, 420, rnd), 40, 0.8)
                mix(it, bell(1760.0, 760, 0.5, 1.2), 140, 0.35); mix(it, bell(2349.0, 700, 0.45, 1.2), 210, 0.28); mix(it, bell(2960.0, 600, 0.4, 1.2), 290, 0.22)
            }.let { reverb(it, 0.2, 200) }
            Sound.DRUMROLL -> {
                val o = DoubleArray(samples(1150))
                var t = 0.0
                var k = 0
                while (t < 980) { val vel = 0.25 + 0.75 * (t / 980.0); mix(o, snare(rnd, 110, vel), t.toInt(), 0.75); t += 62.0 - 26.0 * (t / 980.0); k++ }
                mix(o, highpass(noise(rnd, samples(500)), 5200.0).let { c -> DoubleArray(c.size) { c[it] * exp(it.toDouble() / R, 0.2) * 0.5 } }, 1000, 0.7)
                mix(o, timpani(98.0, 300, rnd), 990, 0.9)
                o
            }
            Sound.BANKER -> DoubleArray(samples(1150)).also {
                mix(it, brass(note(-19), 760, 40), 0, 0.8); mix(it, brass(note(-16), 760, 40), 0, 0.6); mix(it, brass(note(-12), 760, 40), 0, 0.55); mix(it, brass(note(-7), 760, 40), 0, 0.45)
                mix(it, timpani(110.0, 800, rnd), 0, 0.6); mix(it, pad(note(-24), 1100, 80), 0, 0.5)
            }.let { reverb(it, 0.25, 300) }
            Sound.SUSPENSE -> DoubleArray(samples(1150)).also {
                val nz = noise(rnd, it.size)
                var lp = 0.0
                for (i in it.indices) { val t = i.toDouble() / R; val cut = 250.0 + 2800.0 * (t / 1.1); val a = 1.0 - exp(-2.0 * PI * cut / R); lp += a * (nz[i] - lp); it[i] = lp * (0.2 + 0.8 * (t / 1.1)) * 0.9 }
                mix(it, timpani(65.0, 400, rnd), 0, 0.8); mix(it, timpani(65.0, 400, rnd), 480, 0.9); mix(it, timpani(65.0, 400, rnd), 900, 1.0)
            }
            Sound.CASH -> {
                // cha-ching: the catch of the drawer, then the bell rung twice, then coins settling
                val o = DoubleArray(samples(1100))
                mix(o, knock(rnd, 340.0, 90), 0, 0.9); mix(o, bandpass(noise(rnd, samples(50)), 3400.0, 1.5), 20, 0.5)
                mix(o, bell(2093.0, 900, 0.55, 1.2), 110, 0.8); mix(o, bell(2093.0, 850, 0.55, 1.2), 235, 0.8); mix(o, bell(2637.0, 700, 0.4, 1.2), 235, 0.4)
                for (c in 0 until 7) mix(o, bell(2400.0 + rnd.nextInt(1800), 160, 0.07, 1.4, rnd), 480 + c * 55 + rnd.nextInt(30), 0.35)
                reverb(o, 0.12, 150)
            }
            Sound.JACKPOT -> DoubleArray(samples(1150)).also {
                val fan = intArrayOf(3, 7, 10, 15, 19)   // C5 E5 G5 C6 E6 (semitones from A4)
                for ((k, st) in fan.withIndex()) { mix(it, bell(note(st), 700, 0.5, 1.2), k * 110, 0.7); mix(it, pluck(note(st - 12), 400, rnd), k * 110, 0.8) }
                mix(it, brass(note(-9), 640, 40), 520, 0.6); mix(it, brass(note(-5), 640, 40), 520, 0.45)
                for (c in 0 until 16) mix(it, bell(2300.0 + rnd.nextInt(2200), 160, 0.07, 1.4, rnd), 560 + c * 32 + rnd.nextInt(20), 0.25)
            }.let { reverb(it, 0.2, 200) }
            Sound.REEL -> knock(rnd, 1100.0, 30).let { gain(it, 0.5) }
            Sound.STOP -> DoubleArray(samples(240)).also { mix(it, knock(rnd, 110.0, 160), 0, 1.4); mix(it, bandpass(noise(rnd, samples(25)), 2000.0, 1.0), 0, 0.5) }
            Sound.LEVER -> DoubleArray(samples(520)).also { for (k in 0 until 7) mix(it, bandpass(noise(rnd, samples(26)), 2600.0 + k * 140.0, 3.0).let { c -> DoubleArray(c.size) { c[it] * exp(it.toDouble() / R, 0.007) * 3.0 } }, k * (40 - k * 2), 0.7); mix(it, knock(rnd, 80.0, 220), 300, 1.5) }
            Sound.SPARKLE -> DoubleArray(samples(700)).also { for (k in 0 until 6) mix(it, bell(1760.0 * 2.0.pow(k * 0.17), 420, 0.2, 1.3), k * 65, 0.55) }.let { reverb(it, 0.25, 250) }
            Sound.GASP -> DoubleArray(samples(500)).also {
                val br = noise(rnd, it.size)
                val v = mouth(br) { i -> val t = i.toDouble() / R; Triple(500.0 + 250.0 * min(1.0, t / 0.3), 1300.0, 2500.0) }
                for (i in it.indices) { val t = i.toDouble() / R; it[i] = v[i] * sin(PI * min(1.0, t / 0.45)).pow(1.5) * 0.9 }
            }
            Sound.VOICE -> phoneVoice(2400, 7)
            Sound.APPLAUSE -> applause(2400, 3)
            Sound.CHEER -> DoubleArray(samples(1900)).also {
                mix(it, applause(1900, 5, 1.3), 0, 1.0)
                for (v in 0 until 4) {
                    val src = source(samples(520), { i -> val t = i.toDouble() / R; (180.0 + v * 34.0) * (0.9 + 0.35 * min(1.0, t / 0.2)) }, rnd, 0.06)
                    val vow = mouth(src) { Triple(700.0, 1100.0, 2500.0) }
                    for (i in vow.indices) vow[i] *= sin(PI * i / vow.size).pow(0.7) * 0.35
                    mix(it, vow, v * 40, 0.5)
                }
            }.let { reverb(it, 0.15, 250) }
            Sound.GROAN -> crowdOoh(1500, 11)
            Sound.KNOCK -> DoubleArray(samples(380)).also { mix(it, knock(rnd, 200.0, 140), 0, 1.2); mix(it, knock(rnd, 190.0, 140), 170, 1.0) }
            Sound.JINGLE_A -> jingle(rnd, listOf(0, 4, 7, 12), listOf(0, 110, 220, 330), pluckStyle = true)
            Sound.JINGLE_B -> jingle(rnd, listOf(-3, 0, 2, 4, 7), listOf(0, 100, 200, 300, 400), pluckStyle = false)
            Sound.JINGLE_C -> DoubleArray(samples(900)).also { for ((k, st) in listOf(-24, -21, -17, -12).withIndex()) mix(it, brass(note(st), 400, 40), k * 150, 0.8) }.let { reverb(it, 0.2, 250) }
            Sound.JINGLE_D -> jingle(rnd, listOf(-2, -5, -2, 3), listOf(0, 100, 200, 300), pluckStyle = true)
            Sound.JINGLE_E -> DoubleArray(samples(1000)).also { mix(it, bell(note(-8), 800, 0.7), 0, 0.8); mix(it, bell(note(-13), 800, 0.7), 220, 0.8); mix(it, bell(note(-3), 800, 0.7), 440, 0.8); mix(it, pad(note(-20), 900, 120), 0, 0.5) }.let { reverb(it, 0.3, 350) }
            Sound.JINGLE_F -> DoubleArray(samples(1000)).also { for ((k, st) in listOf(-2, -2, 3, 7).withIndex()) mix(it, brass(note(st - 12), if (k == 3) 560 else 190, 30), k * 180, 0.8) }.let { reverb(it, 0.2, 250) }
            Sound.JINGLE_G -> jingle(rnd, listOf(-17, -10, -9, -17), listOf(0, 150, 300, 480), pluckStyle = true)
            Sound.JINGLE_H -> jingle(rnd, listOf(3, 10, 3, 7, 10), listOf(0, 90, 180, 270, 360), pluckStyle = true)
        }
    }

    private fun jingle(rnd: Random, semis: List<Int>, startsMs: List<Int>, pluckStyle: Boolean): DoubleArray {
        val o = DoubleArray(samples(1050))
        for ((k, st) in semis.withIndex()) {
            val f = note(st + 3)
            if (pluckStyle) mix(o, pluck(f, 520, rnd, 0.9965), startsMs[k], 1.0) else mix(o, bell(f, 520, 0.28), startsMs[k], 0.9)
            if (k == semis.lastIndex) mix(o, bell(f * 2, 600, 0.4), startsMs[k], 0.35)
        }
        return reverb(o, 0.22, 300)
    }

    /** The tune that plays quietly under Deal or No Deal: an original, tense, driving game-show underscore in D minor (about 20 seconds, looping). */
    fun dealMusic(): DoubleArray {
        val bpm = 96.0
        val beatMs = 60_000.0 / bpm
        val bars = 8
        val totalMs = (bars * 4 * beatMs).toInt()
        val rnd = Random(77)
        val out = DoubleArray(samples(totalMs))
        // chords, one per two bars: Dm, Bb, Gm, A (as semitones from A4)
        val roots = intArrayOf(-31, -35, -38, -36)
        val chords = arrayOf(intArrayOf(0, 3, 7), intArrayOf(0, 4, 7), intArrayOf(0, 3, 7), intArrayOf(0, 4, 7))
        for (bar in 0 until bars) {
            val ci = bar / 2
            val barStart = (bar * 4 * beatMs).toInt()
            val rootSemi = roots[ci]
            // a pulsing eighth-note bass
            for (e in 0 until 8) {
                val ms = barStart + (e * beatMs / 2).toInt()
                mix(out, lowpass(brass(note(rootSemi + 12), (beatMs / 2 * 0.85).toInt(), 12, 0.0), 900.0), ms, if (e % 2 == 0) 0.40 else 0.28)
            }
            // a kettle drum on one and three, a snare tick on two and four
            mix(out, timpani(note(rootSemi + 12), 500, rnd), barStart, 0.40)
            mix(out, timpani(note(rootSemi + 12), 500, rnd), barStart + (2 * beatMs).toInt(), 0.30)
            for (b in intArrayOf(1, 3)) mix(out, snare(rnd, 140, 0.35), barStart + (b * beatMs).toInt(), 0.45)
            for (e in 0 until 8) mix(out, hat(rnd, 60), barStart + (e * beatMs / 2).toInt(), if (e % 2 == 0) 0.16 else 0.10)
            // strings hold the chord for two bars
            if (bar % 2 == 0) for (st in chords[ci]) mix(out, pad(note(rootSemi + 24 + st), (8 * beatMs).toInt(), 700), barStart, 0.42)
            // a nervous plucked figure, up an octave
            val fig = intArrayOf(0, 7, 12, 7, 15, 12, 7, 3)
            for (e in 0 until 8) if (e % 2 == 0 || bar % 4 == 3) mix(out, pluck(note(rootSemi + 24 + fig[e]), 420, rnd, 0.995), barStart + (e * beatMs / 2).toInt(), 0.75)
        }
        // a rising snare roll into the top of the loop
        val rollStart = totalMs - 1800
        var t = rollStart.toDouble()
        while (t < totalMs - 60) { val vel = 0.1 + 0.55 * ((t - rollStart) / 1800.0); mix(out, snare(rnd, 90, vel), t.toInt(), 0.55); t += 78.0 - 40.0 * ((t - rollStart) / 1800.0) }
        return reverb(out, 0.14, 0).copyOf(out.size)
    }
}
