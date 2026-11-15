package com.example.model

import kotlin.math.PI
import kotlin.math.sin

enum class TempoSyncMode(val label: String) {
    AUTO_BEAT("Auto Beat"),
    TAP_TEMPO("Tap Tempo"),
    MANUAL_BPM("Manual"),
}

enum class BeatDivision(val label: String, val cyclesPerBeat: Float) {
    DIV_4_BARS("4 Bars", 0.0625f),
    DIV_2_BARS("2 Bars", 0.125f),
    DIV_1_BAR("1 Bar", 0.25f),
    DIV_HALF("1/2", 0.5f),
    DIV_DOTTED_QUARTER("1/4.", 0.6666667f),
    DIV_QUARTER("1/4", 1.0f),
    DIV_EIGHTH("1/8", 2.0f),
    DIV_TRIPLET("1/8T", 3.0f),
    DIV_SIXTEENTH("1/16", 4.0f),
}

enum class LfoWaveform(val label: String, val shortLabel: String) {
    SINE("Sine", "SIN"),
    TRIANGLE("Triangle", "TRI"),
    SAW_UP("Saw +", "SAW+"),
    SAW_DOWN("Saw -", "SAW-"),
    SQUARE("Square", "SQR"),
    SAMPLE_HOLD("S&H", "S&H"),
    CHAOS("Chaos", "CHAO"),
}

data class LfoChannelConfig(
    val id: Int,
    val name: String = "LFO $id",
    val enabled: Boolean = true,
    val waveform: LfoWaveform = when (id) {
        1 -> LfoWaveform.SINE
        2 -> LfoWaveform.TRIANGLE
        else -> LfoWaveform.SAMPLE_HOLD
    },
    val tempoSynced: Boolean = true,
    val beatDivision: BeatDivision = when (id) {
        1 -> BeatDivision.DIV_QUARTER
        2 -> BeatDivision.DIV_HALF
        else -> BeatDivision.DIV_EIGHTH
    },
    val rateHz: Float = when (id) {
        1 -> 0.5f
        2 -> 0.25f
        else -> 1.25f
    },
    val phaseDeg: Float = 0f,
    val depth: Float = 1.0f,
    val dutyCycle: Float = 0.5f,
    val retriggerOnBeat: Boolean = false,
) {
    companion object {
        fun defaults(): List<LfoChannelConfig> = listOf(
            LfoChannelConfig(
                id = 1,
                name = "LFO 1",
                enabled = true,
                waveform = LfoWaveform.SINE,
                tempoSynced = true,
                beatDivision = BeatDivision.DIV_QUARTER,
                rateHz = 0.5f,
                phaseDeg = 0f,
                depth = 1.0f,
            ),
            LfoChannelConfig(
                id = 2,
                name = "LFO 2",
                enabled = true,
                waveform = LfoWaveform.TRIANGLE,
                tempoSynced = true,
                beatDivision = BeatDivision.DIV_HALF,
                rateHz = 0.25f,
                phaseDeg = 90f,
                depth = 1.0f,
            ),
            LfoChannelConfig(
                id = 3,
                name = "LFO 3",
                enabled = true,
                waveform = LfoWaveform.SAMPLE_HOLD,
                tempoSynced = true,
                beatDivision = BeatDivision.DIV_EIGHTH,
                rateHz = 1.25f,
                phaseDeg = 0f,
                depth = 0.85f,
                retriggerOnBeat = true,
            ),
        )
    }
}

data class TempoSyncConfig(
    val enabled: Boolean = true,
    val mode: TempoSyncMode = TempoSyncMode.AUTO_BEAT,
    val manualBpm: Float = 120f,
    val tapBpm: Float = 120f,
    val bpmMultiplier: Float = 1.0f,
    val lockVisualOscillators: Boolean = true,
    val quantizeTransformerChanges: Boolean = false,
    val transformerQuantizeDivision: BeatDivision = BeatDivision.DIV_QUARTER,
)

data class TempoSyncSnapshot(
    val enabled: Boolean = true,
    val mode: TempoSyncMode = TempoSyncMode.AUTO_BEAT,
    val effectiveBpm: Float = 120f,
    val rawBpm: Float = 120f,
    val autoDetectedBpm: Float = 120f,
    val tapBpm: Float = 120f,
    val confidence: Float = 0f,
    val totalBeats: Double = 0.0,
    val beatPhase: Float = 0f,
    val barPhase: Float = 0f,
    val beatInBar: Int = 0,
    val beatIndex: Long = 0L,
    val oscillatorTimeSec: Float = 0f,
    val lockVisualOscillators: Boolean = true,
    val quantizeTransformerChanges: Boolean = false,
    val transformerQuantizeDivision: BeatDivision = BeatDivision.DIV_QUARTER,
    val lfoBusValues: FloatArray = floatArrayOf(0.5f, 0.5f, 0.5f),
    val lfoBusPhases: FloatArray = floatArrayOf(0f, 0f, 0f),
) {
    override fun equals(other: Any?): Boolean {
        if (this === other) return true
        if (other !is TempoSyncSnapshot) return false
        return enabled == other.enabled &&
            mode == other.mode &&
            effectiveBpm == other.effectiveBpm &&
            totalBeats == other.totalBeats &&
            beatIndex == other.beatIndex &&
            lfoBusValues.contentEquals(other.lfoBusValues)
    }

    override fun hashCode(): Int {
        var res = enabled.hashCode()
        res = 31 * res + mode.hashCode()
        res = 31 * res + effectiveBpm.hashCode()
        res = 31 * res + totalBeats.hashCode()
        res = 31 * res + beatIndex.hashCode()
        res = 31 * res + lfoBusValues.contentHashCode()
        return res
    }
}

object LfoMath {
    /**
     * Evaluates an LFO waveform to a normalized [0f..1f] value.
     *
     * @param waveform Waveform shape
     * @param totalCycles Continuous cycle counter (e.g. phase position = totalCycles + phaseOffset)
     * @param dutyCycle Skew/pulse-width parameter in [0.1f..0.9f]
     * @param seedOffset Deterministic salt for Sample & Hold steps
     */
    fun evaluateNormalized(
        waveform: LfoWaveform,
        totalCycles: Double,
        dutyCycle: Float = 0.5f,
        seedOffset: Int = 0,
    ): Float {
        val frac = ((totalCycles % 1.0) + 1.0) % 1.0
        val p = frac.toFloat()
        val raw = when (waveform) {
            LfoWaveform.SINE -> (0.5f + 0.5f * sin(2.0 * PI * p)).toFloat()
            LfoWaveform.TRIANGLE -> {
                val peak = dutyCycle.coerceIn(0.1f, 0.9f)
                if (p < peak) p / peak else (1f - p) / (1f - peak)
            }
            LfoWaveform.SAW_UP -> p
            LfoWaveform.SAW_DOWN -> 1f - p
            LfoWaveform.SQUARE -> if (p < dutyCycle.coerceIn(0.1f, 0.9f)) 1f else 0f
            LfoWaveform.SAMPLE_HOLD -> {
                val step = kotlin.math.floor(totalCycles).toLong()
                pseudoRandom01(step * 6364136223846793005L + seedOffset * 1442695040888963407L)
            }
            LfoWaveform.CHAOS -> {
                val t = totalCycles * 2.0 * PI
                val n = sin(t * 0.73) * 0.5 + sin(t * 1.37 + 1.3) * 0.3 + sin(t * 2.41 + 2.7) * 0.2
                (n * 0.5 + 0.5).toFloat()
            }
        }
        return raw.coerceIn(0f, 1f)
    }

    fun applyDepth(normalizedWave: Float, depth: Float): Float {
        val d = depth.coerceIn(0f, 1f)
        return (0.5f + (normalizedWave - 0.5f) * d).coerceIn(0f, 1f)
    }

    private fun pseudoRandom01(seed: Long): Float {
        var z = seed + 0x9E3779B97F4A7C15UL.toLong()
        z = (z xor (z ushr 30)) * -0x40A7B892E31B1A47L
        z = (z xor (z ushr 27)) * -0x6B2FB644ECACEE11L
        z = z xor (z ushr 31)
        val positive = (z ushr 40).toInt() and 0xFFFFFF
        return positive / 16777215f
    }
}
