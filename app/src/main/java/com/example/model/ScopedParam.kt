package com.example.model

import com.example.audio.AudioFrame
import kotlin.math.PI
import kotlin.math.abs
import kotlin.math.exp
import kotlin.math.ln
import kotlin.math.max
import kotlin.math.min
import kotlin.math.pow
import kotlin.math.roundToInt
import kotlin.math.sin

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
    CHAOS_DRIFT("Chaos Drift", "CHAO"),
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
 * Stateful modulation matrix evaluating ScopedParams smoothly at 60fps.
 */
class ModulationEvaluator {
    private val smoothedNorms = HashMap<String, Float>(64)

    fun reset() {
        smoothedNorms.clear()
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
    ): Float {
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
            ModulationDriver.LFO_SINE -> {
                (0.5f + 0.5f * sin(2.0 * PI * param.lfoHz * timeSec)).toFloat()
            }
            ModulationDriver.LFO_TRIANGLE -> {
                val phase = (param.lfoHz * timeSec) % 1f
                val p = if (phase < 0f) phase + 1f else phase
                if (p < 0.5f) p * 2f else (1f - p) * 2f
            }
            ModulationDriver.CHAOS_DRIFT -> {
                val t = timeSec * param.lfoHz
                val n = sin(t * 1.73f) * 0.5f + sin(t * 3.17f + 1.3f) * 0.3f + sin(t * 5.91f + 2.7f) * 0.2f
                (n * 0.5f + 0.5f).coerceIn(0f, 1f)
            }
        }.coerceIn(0f, 1f)

        val curved = when (param.curve) {
            ResponseCurve.LINEAR -> rawNorm
            ResponseCurve.EXPONENTIAL -> rawNorm * rawNorm
            ResponseCurve.LOGARITHMIC -> rawNorm.pow(0.55f)
            ResponseCurve.STEP_4 -> (rawNorm * 4f).roundToInt() / 4f
            ResponseCurve.GATE -> if (rawNorm > 0.35f) 1f else 0f
        }

        val target = if (param.inverted) 1f - curved else curved
        val prev = smoothedNorms[stateKey] ?: target

        val smoothed = if (param.driver == ModulationDriver.FIXED) {
            0.5f
        } else {
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
