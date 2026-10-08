package com.thegadget.app.core

import kotlin.math.abs
import kotlin.math.exp
import kotlin.math.floor
import kotlin.math.pow
import kotlin.math.sin
import kotlin.random.Random

/**
 * The SFXR synthesiser the web app uses for its UI sounds (`src/utils/audio.ts` generates its six
 * effects with jsfxr). This is a port of that generator — same parameter defaults, same presets,
 * same 8× oversampled synthesis chain, same per-sound volumes.
 *
 * One deliberate difference: the web draws its randomness from `Math.random()`, so it renders a
 * *different* variant of each preset on every page load. The native build passes a fixed seed, so
 * the same six sounds come out identically on every launch. Reproducing the web's per-load
 * variation is impossible by construction; making the native build stable is the useful behaviour.
 */
object Sfxr {
    const val SQUARE = 0
    const val SAWTOOTH = 1
    const val SINE = 2
    const val NOISE = 3

    private const val OVERSAMPLING = 8
    private const val MASTER_VOLUME = 1.0
    private const val MAX_SAMPLES = 44100 * 4

    /** `function Params()` in sfxr.js — the defaults every preset starts from. */
    class Params {
        var waveType = SQUARE
        var envAttack = 0.0
        var envSustain = 0.3
        var envPunch = 0.0
        var envDecay = 0.4
        var baseFreq = 0.3
        var freqLimit = 0.0
        var freqRamp = 0.0
        var freqDramp = 0.0
        var vibStrength = 0.0
        var vibSpeed = 0.0
        var arpMod = 0.0
        var arpSpeed = 0.0
        var duty = 0.0
        var dutyRamp = 0.0
        var repeatSpeed = 0.0
        var phaOffset = 0.0
        var phaRamp = 0.0
        var lpfFreq = 1.0
        var lpfRamp = 0.0
        var lpfResonance = 0.0
        var hpfFreq = 0.0
        var hpfRamp = 0.0
        var soundVol = 0.5
        var sampleRate = 44100
    }

    private fun frnd(r: Random, range: Double) = r.nextDouble() * range
    private fun rnd(r: Random, max: Int) = floor(r.nextDouble() * (max + 1)).toInt()
    private fun sqr(x: Double) = x * x

    // ---- presets --------------------------------------------------------

    fun pickupCoin(r: Random): Params = Params().apply {
        waveType = SAWTOOTH
        baseFreq = 0.4 + frnd(r, 0.5)
        envAttack = 0.0
        envSustain = frnd(r, 0.1)
        envDecay = 0.1 + frnd(r, 0.4)
        envPunch = 0.3 + frnd(r, 0.3)
        if (rnd(r, 1) == 1) {
            arpSpeed = 0.5 + frnd(r, 0.2)
            arpMod = 0.2 + frnd(r, 0.4)
        }
    }

    fun powerUp(r: Random): Params = Params().apply {
        if (rnd(r, 1) == 1) {
            waveType = SAWTOOTH
            duty = 1.0
        } else {
            duty = frnd(r, 0.6)
        }
        baseFreq = 0.2 + frnd(r, 0.3)
        if (rnd(r, 1) == 1) {
            freqRamp = 0.1 + frnd(r, 0.4)
            repeatSpeed = 0.4 + frnd(r, 0.4)
        } else {
            freqRamp = 0.05 + frnd(r, 0.2)
            if (rnd(r, 1) == 1) {
                vibStrength = frnd(r, 0.7)
                vibSpeed = frnd(r, 0.6)
            }
        }
        envAttack = 0.0
        envSustain = frnd(r, 0.4)
        envDecay = 0.1 + frnd(r, 0.4)
    }

    fun hitHurt(r: Random): Params = Params().apply {
        waveType = rnd(r, 2)
        if (waveType == SINE) waveType = NOISE
        if (waveType == SQUARE) duty = frnd(r, 0.6)
        if (waveType == SAWTOOTH) duty = 1.0
        baseFreq = 0.2 + frnd(r, 0.6)
        freqRamp = -0.3 - frnd(r, 0.4)
        envAttack = 0.0
        envSustain = frnd(r, 0.1)
        envDecay = 0.1 + frnd(r, 0.2)
        if (rnd(r, 1) == 1) hpfFreq = frnd(r, 0.3)
    }

    fun blipSelect(r: Random): Params = Params().apply {
        waveType = rnd(r, 1)
        if (waveType == SQUARE) duty = frnd(r, 0.6) else duty = 1.0
        baseFreq = 0.2 + frnd(r, 0.4)
        envAttack = 0.0
        envSustain = 0.1 + frnd(r, 0.1)
        envDecay = frnd(r, 0.2)
        hpfFreq = 0.1
    }

    fun jump(r: Random): Params = Params().apply {
        waveType = SQUARE
        duty = frnd(r, 0.6)
        baseFreq = 0.3 + frnd(r, 0.3)
        freqRamp = 0.1 + frnd(r, 0.2)
        envAttack = 0.0
        envSustain = 0.1 + frnd(r, 0.3)
        envDecay = 0.1 + frnd(r, 0.2)
        if (rnd(r, 1) == 1) hpfFreq = frnd(r, 0.3)
        if (rnd(r, 1) == 1) lpfFreq = 1 - frnd(r, 0.6)
    }

    fun explosion(r: Random): Params = Params().apply {
        waveType = NOISE
        if (rnd(r, 1) == 1) {
            baseFreq = sqr(0.1 + frnd(r, 0.4))
            freqRamp = -0.1 + frnd(r, 0.4)
        } else {
            baseFreq = sqr(0.2 + frnd(r, 0.7))
            freqRamp = -0.2 - frnd(r, 0.2)
        }
        if (rnd(r, 4) == 0) freqRamp = 0.0
        if (rnd(r, 2) == 0) repeatSpeed = 0.3 + frnd(r, 0.5)
        envAttack = 0.0
        envSustain = 0.1 + frnd(r, 0.3)
        envDecay = frnd(r, 0.5)
        if (rnd(r, 1) == 1) {
            phaOffset = -0.3 + frnd(r, 0.9)
            phaRamp = -frnd(r, 0.3)
        }
        envPunch = 0.2 + frnd(r, 0.6)
        if (rnd(r, 1) == 1) {
            vibStrength = frnd(r, 0.7)
            vibSpeed = frnd(r, 0.6)
        }
        if (rnd(r, 2) == 0) {
            arpSpeed = 0.6 + frnd(r, 0.3)
            arpMod = 0.8 - frnd(r, 1.6)
        }
    }

    /** `Params.prototype.click` — explosion *or* hitHurt, then perturbed. */
    fun click(r: Random): Params = (if (rnd(r, 1) == 1) explosion(r) else hitHurt(r)).apply {
        if (rnd(r, 1) == 1) freqRamp = -0.5 + frnd(r, 1.0)
        if (rnd(r, 1) == 1) {
            envSustain = (frnd(r, 0.4) + 0.2) * envSustain
            envDecay = (frnd(r, 0.4) + 0.2) * envDecay
        }
        if (rnd(r, 3) == 0) envAttack = frnd(r, 0.3)
    }

    /** The `MAP` in `utils/audio.ts`: which preset each UI sound uses, and at what volume. */
    val MAP: Map<String, Pair<String, Double>> = linkedMapOf(
        "hover" to ("blipSelect" to 0.10),
        "tap" to ("click" to 0.40),
        "open" to ("powerUp" to 0.20),
        "close" to ("jump" to 0.18),
        "confirm" to ("pickupCoin" to 0.30),
        "shake" to ("hitHurt" to 0.22),
    )

    /** The haptics the web pairs with each sound (`navigator.vibrate`). */
    val HAPTICS: Map<String, LongArray> = mapOf(
        "tap" to longArrayOf(8),
        "open" to longArrayOf(10),
        "confirm" to longArrayOf(14),
        "shake" to longArrayOf(20, 40, 20),
    )

    fun preset(name: String, r: Random): Params = when (name) {
        "pickupCoin" -> pickupCoin(r)
        "powerUp" -> powerUp(r)
        "hitHurt" -> hitHurt(r)
        "blipSelect" -> blipSelect(r)
        "jump" -> jump(r)
        "explosion" -> explosion(r)
        "click" -> click(r)
        else -> Params()
    }

    /**
     * `SoundEffect.prototype.getRawBuffer` — returns the normalised float samples in [-1, 1].
     */
    fun render(ps: Params, rnd: Random): FloatArray {
        // --- init (SoundEffect.prototype.init) ---
        val waveShape = ps.waveType
        var fltw = ps.lpfFreq.pow(3) * 0.1
        val enableLowPass = ps.lpfFreq != 1.0
        val fltwD = 1 + ps.lpfRamp * 0.0001
        val rawDmp = 5 / (1 + ps.lpfResonance.pow(2) * 20) * (0.01 + fltw)
        val fltdmp = if (rawDmp > 0.8) 0.8 else rawDmp
        var flthp = ps.hpfFreq.pow(2) * 0.1
        val flthpD = 1 + ps.hpfRamp * 0.0003
        val vibratoSpeed = ps.vibSpeed.pow(2) * 0.01
        val vibratoAmplitude = ps.vibStrength * 0.5
        val envelopeLength = intArrayOf(
            floor(ps.envAttack * ps.envAttack * 100000).toInt(),
            floor(ps.envSustain * ps.envSustain * 100000).toInt(),
            floor(ps.envDecay * ps.envDecay * 100000).toInt(),
        )
        val envelopePunch = ps.envPunch
        var flangerOffset = ps.phaOffset.pow(2) * 1020
        if (ps.phaOffset < 0) flangerOffset = -flangerOffset
        var flangerOffsetSlide = ps.phaRamp.pow(2) * 1
        if (ps.phaRamp < 0) flangerOffsetSlide = -flangerOffsetSlide
        var repeatTime = floor((1 - ps.repeatSpeed).pow(2) * 20000 + 32).toInt()
        if (ps.repeatSpeed == 0.0) repeatTime = 0
        val gain = exp(ps.soundVol) - 1
        val sampleRate = ps.sampleRate

        // --- initForRepeat (the fields a repeat resets) ---
        val periodInit = 100 / (ps.baseFreq * ps.baseFreq + 0.001)
        val periodMax = 100 / (ps.freqLimit * ps.freqLimit + 0.001)
        val enableFreqCutoff = ps.freqLimit > 0
        val periodMultInit = 1 - ps.freqRamp.pow(3) * 0.01
        val periodMultSlide = -ps.freqDramp.pow(3) * 0.000001
        val dutyCycleInit = 0.5 - ps.duty * 0.5
        val dutyCycleSlide = -ps.dutyRamp * 0.00005
        val arpeggioMultiplier =
            if (ps.arpMod >= 0) 1 - ps.arpMod.pow(2) * 0.9 else 1 + ps.arpMod.pow(2) * 10
        val arpeggioTimeInit = floor((1 - ps.arpSpeed).pow(2) * 20000 + 32).toInt()
            .let { if (ps.arpSpeed == 1.0) 0 else it }

        var period = periodInit
        var periodMult = periodMultInit
        var dutyCycle = dutyCycleInit
        var arpeggioTime = arpeggioTimeInit
        var elapsedSinceRepeat = 0

        // --- loop state ---
        var fltp = 0.0
        var fltdp = 0.0
        var fltphp = 0.0
        val noiseBuffer = DoubleArray(32) { rnd.nextDouble() * 2 - 1 }
        var envelopeStage = 0
        var envelopeElapsed = 0
        var vibratoPhase = 0.0
        var phase = 0
        var ipp = 0
        val flangerBuffer = DoubleArray(1024)
        val summands = floor(44100.0 / sampleRate).toInt().coerceAtLeast(1)

        var sampleSum = 0.0
        var numSummed = 0
        val out = ArrayList<Float>(4096)

        var t = 0
        while (t < MAX_SAMPLES) {
            t++
            if (repeatTime != 0 && ++elapsedSinceRepeat >= repeatTime) {
                period = periodInit
                periodMult = periodMultInit
                dutyCycle = dutyCycleInit
                arpeggioTime = arpeggioTimeInit
                elapsedSinceRepeat = 0
            }

            if (arpeggioTime != 0 && t >= arpeggioTime) {
                arpeggioTime = 0
                period *= arpeggioMultiplier
            }

            periodMult += periodMultSlide
            period *= periodMult
            if (period > periodMax) {
                period = periodMax
                if (enableFreqCutoff) break
            }

            var rfperiod = period
            if (vibratoAmplitude > 0) {
                vibratoPhase += vibratoSpeed
                rfperiod = period * (1 + sin(vibratoPhase) * vibratoAmplitude)
            }
            var iperiod = floor(rfperiod).toInt()
            if (iperiod < OVERSAMPLING) iperiod = OVERSAMPLING

            dutyCycle += dutyCycleSlide
            if (dutyCycle < 0) dutyCycle = 0.0
            if (dutyCycle > 0.5) dutyCycle = 0.5

            if (++envelopeElapsed > envelopeLength[envelopeStage]) {
                envelopeElapsed = 0
                if (++envelopeStage > 2) break
            }
            val envf = envelopeElapsed.toDouble() / envelopeLength[envelopeStage]
            val envVol = when (envelopeStage) {
                0 -> envf
                1 -> 1 + (1 - envf) * 2 * envelopePunch
                else -> 1 - envf
            }

            flangerOffset += flangerOffsetSlide
            var iphase = abs(floor(flangerOffset)).toInt()
            if (iphase > 1023) iphase = 1023

            if (flthpD != 0.0) {
                flthp *= flthpD
                if (flthp < 0.00001) flthp = 0.00001
                if (flthp > 0.1) flthp = 0.1
            }

            var sample = 0.0
            for (si in 0 until OVERSAMPLING) {
                var subSample: Double
                phase++
                if (phase >= iperiod) {
                    phase %= iperiod
                    if (waveShape == NOISE) {
                        for (i in 0 until 32) noiseBuffer[i] = rnd.nextDouble() * 2 - 1
                    }
                }

                val fp = phase.toDouble() / iperiod
                subSample = when (waveShape) {
                    SQUARE -> if (fp < dutyCycle) 0.5 else -0.5
                    SAWTOOTH ->
                        if (fp < dutyCycle) -1 + 2 * fp / dutyCycle
                        else 1 - 2 * (fp - dutyCycle) / (1 - dutyCycle)
                    SINE -> sin(fp * 2 * Math.PI)
                    else -> noiseBuffer[floor(phase * 32.0 / iperiod).toInt()]
                }

                val pp = fltp
                fltw *= fltwD
                if (fltw < 0) fltw = 0.0
                if (fltw > 0.1) fltw = 0.1
                if (enableLowPass) {
                    fltdp += (subSample - fltp) * fltw
                    fltdp -= fltdp * fltdmp
                } else {
                    fltp = subSample
                    fltdp = 0.0
                }
                fltp += fltdp

                fltphp += fltp - pp
                fltphp -= fltphp * flthp
                subSample = fltphp

                flangerBuffer[ipp and 1023] = subSample
                subSample += flangerBuffer[(ipp - iphase + 1024) and 1023]
                ipp = (ipp + 1) and 1023

                sample += subSample * envVol
            }

            sampleSum += sample
            if (++numSummed >= summands) {
                numSummed = 0
                sample = sampleSum / summands
                sampleSum = 0.0
            } else {
                continue
            }

            sample = sample / OVERSAMPLING * MASTER_VOLUME
            sample *= gain
            out.add(sample.toFloat())
        }
        return out.toFloatArray()
    }
}
