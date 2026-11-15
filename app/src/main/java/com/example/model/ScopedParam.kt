package com.example.model

import com.example.audio.AudioFrame
import kotlin.math.exp
import kotlin.math.floor
import kotlin.math.ln
import kotlin.math.max
import kotlin.math.min
import kotlin.math.pow
import kotlin.math.roundToInt

enum class ModulationDriver(val label: String, val shortLabel: String) {
    FIXED("Fixed", "FIX"),
    SUB_BASS("Sub-Bass (20-80Hz)", "SUB"),
    BASS("Bass (80-250Hz)", "BAS"),
    LOW_MID("Low-Mid (250-800Hz)", "LMD"),
    HIGH_MID("High-Mid (800-3kHz)", "HMD"),
    TREBLE("Treble (3-16kHz)", "TRB"),
    OVERALL_RMS("Overall RMS", "RMS"),
    BEAT_PULSE("Beat Transient", "BEAT"),
    SPECTRAL_CENTROID("Spectral Pitch", "FREQ"),
    FILTERED_BAND("Layer Hz Filter", "FILT"),
    LFO_SINE("Sine LFO", "SIN"),
    LFO_TRIANGLE("Triangle LFO", "TRI"),
    LFO_SAW_UP("Sawtooth Up LFO", "SAW+"),
    LFO_SAW_DOWN("Sawtooth Down LFO", "SAW-"),
    LFO_SQUARE("Square Gate LFO", "SQR"),
    LFO_SAMPLE_HOLD("S&H Random LFO", "S&H"),
    CHAOS_DRIFT("Chaos Drift", "CHAO"),
    TEMPO_PULSE("BPM Beat Pulse", "BPM"),
    TEMPO_SAW("BPM Beat Ramp", "RAMP"),
    LFO_BUS_1("Master LFO 1", "LFO1"),
    LFO_BUS_2("Master LFO 2", "LFO2"),
    LFO_BUS_3("Master LFO 3", "LFO3");

    val isLocalLfo: Boolean
        get() = this == LFO_SINE ||
            this == LFO_TRIANGLE ||
            this == LFO_SAW_UP ||
            this == LFO_SAW_DOWN ||
            this == LFO_SQUARE ||
            this == LFO_SAMPLE_HOLD ||
            this == CHAOS_DRIFT

    val isGlobalLfoBus: Boolean
        get() = this == LFO_BUS_1 || this == LFO_BUS_2 || this == LFO_BUS_3

    val isTempoDriver: Boolean
        get() = this == TEMPO_PULSE || this == TEMPO_SAW

    val isOscillator: Boolean
        get() = isLocalLfo || isGlobalLfoBus || isTempoDriver

    fun toLfoWaveformOrNull(): LfoWaveform? = when (this) {
        LFO_SINE -> LfoWaveform.SINE
        LFO_TRIANGLE -> LfoWaveform.TRIANGLE
        LFO_SAW_UP -> LfoWaveform.SAW_UP
        LFO_SAW_DOWN -> LfoWaveform.SAW_DOWN
        LFO_SQUARE -> LfoWaveform.SQUARE
        LFO_SAMPLE_HOLD -> LfoWaveform.SAMPLE_HOLD
        CHAOS_DRIFT -> LfoWaveform.CHAOS
        else -> null
    }
}

enum class ResponseCurve(val label: String) {
    LINEAR("Linear"),
    EXPONENTIAL("Exp"),
    LOGARITHMIC("Log"),
    STEP_4("Quantize"),
    GATE("Gate"),
}

/**
 * Defines a bounded operating scope [scopeMin..scopeMax] and modulation routing
 * so every visual style parameter, transformer, and filter operates strictly within
 * user-defined limits.
 */
data class ScopedParam(
    val id: String,
    val label: String,
    val unit: String = "",
    val hardMin: Float,
    val hardMax: Float,
    val scopeMin: Float,
    val scopeMax: Float,
    val driver: ModulationDriver = ModulationDriver.FIXED,
    val curve: ResponseCurve = ResponseCurve.LINEAR,
    val attackMs: Float = 35f,
    val releaseMs: Float = 180f,
    val lfoHz: Float = 0.35f,
    val lfoPhaseDeg: Float = 0f,
    val lfoTempoSynced: Boolean = true,
    val beatDivision: BeatDivision = BeatDivision.DIV_QUARTER,
    val tempoQuantizeHold: Boolean = false,
    val inverted: Boolean = false,
    val locked: Boolean = false,
) {
    val orderedMin: Float get() = min(scopeMin, scopeMax).coerceIn(hardMin, hardMax)
    val orderedMax: Float get() = max(scopeMin, scopeMax).coerceIn(hardMin, hardMax)
    val midpoint: Float get() = (orderedMin + orderedMax) * 0.5f

    fun normalizedPosition(evaluatedValue: Float): Float {
        val span = orderedMax - orderedMin
        return if (span <= 1e-5f) 0.5f else ((evaluatedValue - orderedMin) / span).coerceIn(0f, 1f)
    }
}

/**
 * Per-layer or per-transformer audio frequency filter window and amplitude gate.
 */
data class AudioFilterScope(
    val lowCutHz: Float = 20f,
    val highCutHz: Float = 20000f,
    val thresholdGate: Float = 0.0f,
    val ceilingClamp: Float = 1.0f,
    val gain: Float = 1.0f,
) {
    val safeLowHz: Float get() = min(lowCutHz, highCutHz).coerceIn(20f, 20000f)
    val safeHighHz: Float get() = max(lowCutHz, highCutHz).coerceIn(20f, 20000f)
}

/**
 * Stateful modulation matrix evaluating ScopedParams smoothly at 60fps,
 * supporting audio bands, LFO waveforms (free or BPM-synced), Master LFO Buses 1..3,
 * and beat-quantized parameter holding.
 */
class ModulationEvaluator {
    private val smoothedNorms = HashMap<String, Float>(64)
    private val quantizedHoldMap = HashMap<String, Pair<Long, Float>>(64)

    fun reset() {
        smoothedNorms.clear()
        quantizedHoldMap.clear()
    }

    fun evaluateAudioSlice(frame: AudioFrame, filter: AudioFilterScope): Float {
        val spectrum = frame.linearSpectrum
        if (spectrum.isEmpty()) return 0f
        val nyquist = frame.sampleRateHz * 0.5f
        val binHz = nyquist / spectrum.size.toFloat()
        val startBin = (filter.safeLowHz / binHz).toInt().coerceIn(0, spectrum.lastIndex)
        val endBin = (filter.safeHighHz / binHz).toInt().coerceIn(startBin, spectrum.lastIndex)

        var sum = 0f
        var peak = 0f
        var count = 0
        for (i in startBin..endBin) {
            val mag = spectrum[i]
            sum += mag
            if (mag > peak) peak = mag
            count++
        }
        val avg = if (count > 0) sum / count else 0f
        val blended = (avg * 0.65f + peak * 0.35f) * filter.gain
        val gated = if (blended < filter.thresholdGate) {
            0f
        } else {
            val denom = (filter.ceilingClamp - filter.thresholdGate).coerceAtLeast(0.05f)
            ((blended - filter.thresholdGate) / denom).coerceIn(0f, 1f)
        }
        return gated
    }

    fun evaluate(
        stateKey: String,
        param: ScopedParam,
        frame: AudioFrame,
        filterEnergy: Float,
        timeSec: Float,
        dtSec: Float,
        tempo: TempoSyncSnapshot? = null,
        forceTempoQuantize: Boolean = false,
    ): Float {
        val phaseOffsetCycles = (param.lfoPhaseDeg / 360.0)
        val useTempoClock = tempo != null && tempo.enabled && tempo.lockVisualOscillators && param.lfoTempoSynced

        fun localLfoCycles(): Double {
            val baseCycles = if (useTempoClock) {
                tempo!!.totalBeats * param.beatDivision.cyclesPerBeat.toDouble()
            } else {
                timeSec.toDouble() * param.lfoHz.toDouble()
            }
            return baseCycles + phaseOffsetCycles
        }

        val rawNorm = when (param.driver) {
            ModulationDriver.FIXED -> 0.5f
            ModulationDriver.SUB_BASS -> frame.subBass
            ModulationDriver.BASS -> frame.bass
            ModulationDriver.LOW_MID -> frame.lowMid
            ModulationDriver.HIGH_MID -> frame.highMid
            ModulationDriver.TREBLE -> frame.treble
            ModulationDriver.OVERALL_RMS -> frame.overallRms
            ModulationDriver.BEAT_PULSE -> frame.beatPulse
            ModulationDriver.SPECTRAL_CENTROID -> frame.spectralCentroid
            ModulationDriver.FILTERED_BAND -> filterEnergy
            ModulationDriver.LFO_SINE,
            ModulationDriver.LFO_TRIANGLE,
            ModulationDriver.LFO_SAW_UP,
            ModulationDriver.LFO_SAW_DOWN,
            ModulationDriver.LFO_SQUARE,
            ModulationDriver.LFO_SAMPLE_HOLD,
            ModulationDriver.CHAOS_DRIFT -> {
                val wf = param.driver.toLfoWaveformOrNull() ?: LfoWaveform.SINE
                LfoMath.evaluateNormalized(
                    waveform = wf,
                    totalCycles = localLfoCycles(),
                    dutyCycle = 0.5f,
                    seedOffset = param.id.hashCode(),
                )
            }
            ModulationDriver.TEMPO_PULSE -> {
                val cycles = if (tempo != null && tempo.enabled) {
                    tempo.totalBeats * param.beatDivision.cyclesPerBeat.toDouble()
                } else {
                    timeSec.toDouble() * 2.0 * param.beatDivision.cyclesPerBeat.toDouble()
                }
                val frac = (((cycles + phaseOffsetCycles) % 1.0) + 1.0) % 1.0
                (1f - frac.toFloat()).pow(2.2f)
            }
            ModulationDriver.TEMPO_SAW -> {
                val cycles = if (tempo != null && tempo.enabled) {
                    tempo.totalBeats * param.beatDivision.cyclesPerBeat.toDouble()
                } else {
                    timeSec.toDouble() * 2.0 * param.beatDivision.cyclesPerBeat.toDouble()
                }
                val frac = (((cycles + phaseOffsetCycles) % 1.0) + 1.0) % 1.0
                frac.toFloat()
            }
            ModulationDriver.LFO_BUS_1 -> tempo?.lfoBusValues?.getOrElse(0) { 0.5f } ?: 0.5f
            ModulationDriver.LFO_BUS_2 -> tempo?.lfoBusValues?.getOrElse(1) { 0.5f } ?: 0.5f
            ModulationDriver.LFO_BUS_3 -> tempo?.lfoBusValues?.getOrElse(2) { 0.5f } ?: 0.5f
        }.coerceIn(0f, 1f)

        val curved = when (param.curve) {
            ResponseCurve.LINEAR -> rawNorm
            ResponseCurve.EXPONENTIAL -> rawNorm * rawNorm
            ResponseCurve.LOGARITHMIC -> rawNorm.pow(0.55f)
            ResponseCurve.STEP_4 -> (rawNorm * 4f).roundToInt() / 4f
            ResponseCurve.GATE -> if (rawNorm > 0.35f) 1f else 0f
        }

        val target = if (param.inverted) 1f - curved else curved

        val shouldQuantize = (param.tempoQuantizeHold || forceTempoQuantize) &&
            tempo != null &&
            tempo.enabled &&
            param.driver != ModulationDriver.FIXED

        val smoothed = if (param.driver == ModulationDriver.FIXED) {
            0.5f
        } else if (shouldQuantize) {
            val quantDiv = if (param.tempoQuantizeHold) param.beatDivision else tempo!!.transformerQuantizeDivision
            val tickIdx = floor(tempo!!.totalBeats * quantDiv.cyclesPerBeat.toDouble()).toLong()
            val held = quantizedHoldMap[stateKey]
            val latchedValue = if (held == null || held.first != tickIdx) {
                quantizedHoldMap[stateKey] = tickIdx to target
                target
            } else {
                held.second
            }
            latchedValue.coerceIn(0f, 1f)
        } else {
            val prev = smoothedNorms[stateKey] ?: target
            val tauMs = if (target >= prev) param.attackMs else param.releaseMs
            val tauSec = (tauMs / 1000f).coerceAtLeast(0.004f)
            val alpha = 1f - exp((-dtSec / tauSec).toDouble()).toFloat()
            (prev + (target - prev) * alpha.coerceIn(0.02f, 1f)).coerceIn(0f, 1f)
        }

        smoothedNorms[stateKey] = smoothed
        val lo = param.orderedMin
        val hi = param.orderedMax
        return lo + (hi - lo) * smoothed
    }

    companion object {
        fun hzToNorm(hz: Float): Float {
            val clamped = hz.coerceIn(20f, 20000f)
            return ((ln(clamped / 20f) / ln(1000f))).coerceIn(0f, 1f)
        }

        fun normToHz(norm: Float): Float {
            val t = norm.coerceIn(0f, 1f)
            return (20f * 1000f.pow(t)).coerceIn(20f, 20000f)
        }

        fun formatHz(hz: Float): String {
            return if (hz >= 1000f) {
                String.format("%.1fk", hz / 1000f)
            } else {
                "${hz.roundToInt()}Hz"
            }
        }
    }
}
