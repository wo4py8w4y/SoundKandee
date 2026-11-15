package com.example.audio

import kotlin.math.PI
import kotlin.math.exp
import kotlin.math.pow
import kotlin.math.sin
import kotlin.random.Random

enum class SynthMusicStyle(val label: String, val defaultBpm: Float) {
    TECHNO_PULSE("Techno Pulse", 128f),
    PSYCHEDELIC_ARP("Psychedelic Arp", 138f),
    DUB_BASS("Dub Sub-Bass", 112f),
    AMBIENT_DRIFT("Ambient Drift", 92f),
}

data class SynthFallbackConfig(
    val enabled: Boolean = true,
    val style: SynthMusicStyle = SynthMusicStyle.TECHNO_PULSE,
    val intensity: Float = 0.85f,
    val silenceDelaySec: Float = 1.5f,
    val tempoBpm: Float = 126f,
)

/**
 * Generates realistic musical PCM-16 audio buffers (kick drum transients, basslines,
 * pentatonic synth arpeggios, and crisp hi-hat percussion) when no external music is playing.
 *
 * Output buffers feed directly into [ConfigurableDspProcessor.processPcm16] so FFT,
 * spectral centroid, frequency bands, and beat detection respond organically.
 */
class SyntheticMusicGenerator(private val sampleRateHz: Int = 44100) {

    private var totalSamples = 0L
    private var bassPhase = 0.0
    private var arpPhase = 0.0
    private var padPhaseA = 0.0
    private var padPhaseB = 0.0
    private var rng = Random(7719)

    private val scaleHz = floatArrayOf(
        110.00f, // A2
        130.81f, // C3
        146.83f, // D3
        164.81f, // E3
        196.00f, // G3
        220.00f, // A3
        261.63f, // C4
        293.66f, // D4
        329.63f, // E4
        392.00f, // G4
        440.00f, // A4
        523.25f, // C5
    )

    fun reset() {
        totalSamples = 0L
        bassPhase = 0.0
        arpPhase = 0.0
        padPhaseA = 0.0
        padPhaseB = 0.0
    }

    fun generateNextBuffer(
        sampleCount: Int = 1024,
        config: SynthFallbackConfig,
    ): ShortArray {
        val out = ShortArray(sampleCount)
        val bpm = config.tempoBpm.coerceIn(60f, 180f)
        val beatsPerSec = bpm / 60.0
        val samplesPerBeat = sampleRateHz / beatsPerSec
        val intensity = config.intensity.coerceIn(0.2f, 1.2f)
        val dt = 1.0 / sampleRateHz

        for (i in 0 until sampleCount) {
            val beatPos = totalSamples / samplesPerBeat
            val beatInBar = (beatPos.toInt() % 4)
            val beatFrac = (beatPos - kotlin.math.floor(beatPos)).toFloat()
            val eighthPos = beatPos * 2.0
            val eighthFrac = (eighthPos - kotlin.math.floor(eighthPos)).toFloat()
            val sixteenthPos = beatPos * 4.0
            val sixteenthIndex = sixteenthPos.toInt()
            val sixteenthFrac = (sixteenthPos - kotlin.math.floor(sixteenthPos)).toFloat()

            var sample = 0.0

            when (config.style) {
                SynthMusicStyle.TECHNO_PULSE -> {
                    // Punchy 4-on-the-floor kick with pitch drop
                    val kickEnv = exp((-beatFrac * 11f).toDouble())
                    val kickHz = 46.0 + 135.0 * exp((-beatFrac * 26f).toDouble())
                    val kick = sin(2.0 * PI * kickHz * (beatFrac / beatsPerSec)) * kickEnv * 0.62

                    // Offbeat rolling sub-bass
                    val bassIdx = if (beatInBar == 3) 2 else 0
                    val bassHz = scaleHz[bassIdx] * 0.5
                    bassPhase = (bassPhase + bassHz * dt) % 1.0
                    val bassEnv = if (beatFrac > 0.35f) exp(-((beatFrac - 0.35f) * 6.0)) else 0.15
                    val bass = sin(2.0 * PI * bassPhase) * bassEnv * 0.38

                    // Crisp 16th hi-hats
                    val hatTrig = if (sixteenthIndex % 2 == 1) 1.0 else 0.35
                    val hatEnv = exp((-sixteenthFrac * 22f).toDouble()) * hatTrig
                    val hat = (rng.nextFloat() * 2f - 1f) * hatEnv * 0.22

                    // Resonant synth stab every 2 beats
                    val arpNoteIdx = (sixteenthIndex * 3) % 8 + 4
                    arpPhase = (arpPhase + scaleHz[arpNoteIdx] * dt) % 1.0
                    val arpEnv = exp((-eighthFrac * 7.5).toDouble())
                    val arp = (sin(2.0 * PI * arpPhase) + 0.45 * sin(4.0 * PI * arpPhase)) * arpEnv * 0.22

                    sample = kick + bass + hat + arp
                }

                SynthMusicStyle.PSYCHEDELIC_ARP -> {
                    // Fast swirling 16th arpeggio + tight kick
                    val kickActive = beatInBar == 0 || beatInBar == 2 || (beatInBar == 3 && beatFrac > 0.5f)
                    val kickEnv = if (kickActive) exp((-beatFrac * 13f).toDouble()) else 0.0
                    val kickHz = 50.0 + 140.0 * exp((-beatFrac * 28f).toDouble())
                    val kick = sin(2.0 * PI * kickHz * (beatFrac / beatsPerSec)) * kickEnv * 0.52

                    val noteIdx = ((sixteenthIndex * 5 + beatInBar) % scaleHz.size)
                    val noteHz = scaleHz[noteIdx] * (if (sixteenthIndex % 4 == 0) 2.0 else 1.0)
                    arpPhase = (arpPhase + noteHz * dt) % 1.0
                    val arpEnv = exp((-sixteenthFrac * 6.5).toDouble())
                    val fmMod = sin(2.0 * PI * arpPhase * 2.0) * 0.8
                    val arp = sin(2.0 * PI * arpPhase + fmMod) * arpEnv * 0.35

                    bassPhase = (bassPhase + (scaleHz[0] * 0.5) * dt) % 1.0
                    val bass = sin(2.0 * PI * bassPhase) * 0.26

                    val hatEnv = exp((-sixteenthFrac * 24f).toDouble()) * if (sixteenthIndex % 2 == 1) 0.8 else 0.2
                    val hat = (rng.nextFloat() * 2f - 1f) * hatEnv * 0.16

                    sample = kick + arp + bass + hat
                }

                SynthMusicStyle.DUB_BASS -> {
                    // Heavy sub-bass wobble + half-time snare/kick
                    val kickEnv = if (beatInBar == 0 || (beatInBar == 2 && beatFrac > 0.5f)) exp((-beatFrac * 9f).toDouble()) else 0.0
                    val kick = sin(2.0 * PI * (44.0 + 95.0 * exp((-beatFrac * 20f).toDouble())) * (beatFrac / beatsPerSec)) * kickEnv * 0.58

                    val subNote = scaleHz[(beatPos.toInt() / 2) % 4] * 0.5
                    bassPhase = (bassPhase + subNote * dt) % 1.0
                    val wobble = 0.5 + 0.5 * sin(2.0 * PI * eighthFrac * 2.0)
                    val sub = (sin(2.0 * PI * bassPhase) + 0.35 * sin(4.0 * PI * bassPhase) * wobble) * 0.45

                    val snareActive = beatInBar == 1 || beatInBar == 3
                    val snareEnv = if (snareActive) exp((-beatFrac * 14f).toDouble()) else 0.0
                    val snare = (rng.nextFloat() * 2f - 1f) * snareEnv * 0.28

                    sample = kick + sub + snare
                }

                SynthMusicStyle.AMBIENT_DRIFT -> {
                    // Lush evolving harmonic shimmer chords + gentle pulse
                    val barIdx = (beatPos.toInt() / 4) % 4
                    val rootHz = scaleHz[barIdx * 2].toDouble()
                    padPhaseA = (padPhaseA + rootHz * dt) % 1.0
                    padPhaseB = (padPhaseB + (rootHz * 1.498) * dt) % 1.0
                    arpPhase = (arpPhase + (rootHz * 2.0) * dt) % 1.0

                    val swell = 0.45 + 0.55 * sin(PI * (beatPos % 4.0) / 4.0)
                    val pulse = exp((-beatFrac * 4.5).toDouble()) * 0.25 + 0.18
                    val pad = (sin(2.0 * PI * padPhaseA) * 0.35 +
                        sin(2.0 * PI * padPhaseB) * 0.28 +
                        sin(2.0 * PI * arpPhase) * 0.18 * swell) * (0.65 + pulse)

                    sample = pad
                }
            }

            val pcm = (sample * intensity * 26000.0).toInt().coerceIn(-32767, 32767)
            out[i] = pcm.toShort()
            totalSamples++
        }
        return out
    }
}
