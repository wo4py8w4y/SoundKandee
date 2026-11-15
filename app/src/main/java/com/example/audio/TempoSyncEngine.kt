package com.example.audio

import com.example.model.LfoChannelConfig
import com.example.model.LfoMath
import com.example.model.TempoSyncConfig
import com.example.model.TempoSyncMode
import com.example.model.TempoSyncSnapshot
import kotlin.math.abs
import kotlin.math.floor

/**
 * Real-time tempo synchronization and master LFO clock engine.
 *
 * Combines:
 * - Automatic BPM estimation from DSP spectral-flux beat onsets (with octave folding & PLL phase nudge)
 * - Manual Tap-Tempo interval averaging with instant downbeat phase lock
 * - Continuous, click-free monotonic beat clock driving BPM-locked LFO buses and visual oscillators
 */
class TempoSyncEngine {

    private val lock = Any()

    // Tap tempo state
    private val tapTimestamps = ArrayDeque<Long>(8)
    private var tapBpm: Float = 120f

    // Auto beat detection state
    private var lastAutoOnsetMs: Long = 0L
    private val autoIntervalsMs = ArrayDeque<Long>(16)
    private var autoDetectedBpm: Float = 120f
    private var autoConfidence: Float = 0f
    private var pendingAutoPhaseNudge: Double = 0.0

    // Continuous monotonic clock state
    private var totalBeats: Double = 0.0
    private var lastBeatIndex: Long = 0L
    private val lfoBusCycles = DoubleArray(3)
    private var lastLfoValues = floatArrayOf(0.5f, 0.5f, 0.5f)
    private var lastLfoPhases = floatArrayOf(0f, 0f, 0f)

    /**
     * Records a beat onset detected by the real-time DSP analyzer.
     */
    fun onAutoBeatDetected(timestampMs: Long) {
        synchronized(lock) {
            if (lastAutoOnsetMs == 0L || (timestampMs - lastAutoOnsetMs) > MAX_AUTO_GAP_MS) {
                autoIntervalsMs.clear()
                lastAutoOnsetMs = timestampMs
                autoConfidence = 0f
                return
            }

            val interval = timestampMs - lastAutoOnsetMs
            if (interval < MIN_BEAT_INTERVAL_MS) {
                // Ignore double-trigger inside refractory window
                return
            }
            lastAutoOnsetMs = timestampMs

            // Fold interval into musical octave [65..180] BPM
            var rawBpm = 60_000.0 / interval.toDouble()
            while (rawBpm < 65.0) rawBpm *= 2.0
            while (rawBpm > 180.0) rawBpm *= 0.5
            val normalizedInterval = (60_000.0 / rawBpm).toLong()

            if (autoIntervalsMs.size >= 12) {
                autoIntervalsMs.removeFirst()
            }
            autoIntervalsMs.addLast(normalizedInterval)

            if (autoIntervalsMs.size >= 2) {
                val sorted = autoIntervalsMs.sorted()
                val median = sorted[sorted.size / 2].toDouble()
                val inliers = sorted.filter { abs(it - median) <= median * 0.16 }
                val avgInterval = if (inliers.isNotEmpty()) inliers.average() else median
                val targetBpm = (60_000.0 / avgInterval).toFloat().coerceIn(MIN_BPM, MAX_BPM)

                val stability = (inliers.size.toFloat() / sorted.size.toFloat()).coerceIn(0f, 1f)
                val sampleWeight = (autoIntervalsMs.size / 6f).coerceIn(0f, 1f)
                autoConfidence = (stability * sampleWeight).coerceIn(0f, 1f)

                val slewAlpha = if (autoConfidence > 0.5f) 0.42f else 0.25f
                autoDetectedBpm = (autoDetectedBpm + (targetBpm - autoDetectedBpm) * slewAlpha).coerceIn(MIN_BPM, MAX_BPM)

                // Queue gentle PLL phase nudge toward downbeat
                val beatFrac = ((totalBeats % 1.0) + 1.0) % 1.0
                val phaseError = if (beatFrac > 0.5) 1.0 - beatFrac else -beatFrac
                if (abs(phaseError) > 0.015 && autoConfidence >= 0.25f) {
                    pendingAutoPhaseNudge = (pendingAutoPhaseNudge + phaseError * 0.28).coerceIn(-0.22, 0.22)
                }
            } else {
                autoConfidence = 0.15f
            }
        }
    }

    /**
     * Records a user tap-tempo press. Updates tapped BPM and snaps beat phase to 0.0.
     *
     * @return Updated raw tap BPM.
     */
    fun recordTap(timestampMs: Long = System.currentTimeMillis()): Float {
        synchronized(lock) {
            val prev = tapTimestamps.lastOrNull()
            if (prev == null || (timestampMs - prev) > MAX_TAP_GAP_MS) {
                tapTimestamps.clear()
                tapTimestamps.addLast(timestampMs)
            } else {
                val dt = timestampMs - prev
                if (dt >= MIN_BEAT_INTERVAL_MS) {
                    if (tapTimestamps.size >= 7) {
                        tapTimestamps.removeFirst()
                    }
                    tapTimestamps.addLast(timestampMs)

                    if (tapTimestamps.size >= 2) {
                        val intervals = ArrayList<Long>(tapTimestamps.size - 1)
                        val list = tapTimestamps.toList()
                        for (i in 1 until list.size) {
                            intervals += (list[i] - list[i - 1]).coerceIn(MIN_BEAT_INTERVAL_MS, MAX_TAP_GAP_MS)
                        }
                        val sorted = intervals.sorted()
                        val median = sorted[sorted.size / 2].toDouble()
                        val inliers = sorted.filter { abs(it - median) <= median * 0.25 }
                        val avgMs = if (inliers.isNotEmpty()) inliers.average() else median
                        tapBpm = (60_000.0 / avgMs).toFloat().coerceIn(MIN_BPM, MAX_BPM)
                    }
                }
            }

            // Snap phase cleanly forward to the next integer beat boundary so visual downbeats align immediately
            val nextIntegerBeat = floor(totalBeats) + 1.0
            totalBeats = nextIntegerBeat
            lastBeatIndex = nextIntegerBeat.toLong()
            pendingAutoPhaseNudge = 0.0
            return tapBpm
        }
    }

    fun syncDownbeatPhase() {
        synchronized(lock) {
            val nextBeat = floor(totalBeats) + 1.0
            totalBeats = nextBeat
            lastBeatIndex = nextBeat.toLong()
            pendingAutoPhaseNudge = 0.0
        }
    }

    fun resetAutoTracker() {
        synchronized(lock) {
            autoIntervalsMs.clear()
            lastAutoOnsetMs = 0L
            autoConfidence = 0f
            pendingAutoPhaseNudge = 0.0
        }
    }

    fun setTapBpm(bpm: Float) {
        synchronized(lock) {
            tapBpm = bpm.coerceIn(MIN_BPM, MAX_BPM)
        }
    }

    /**
     * Advances the continuous beat clock and Master LFO buses by [dtSec].
     */
    fun step(
        dtSec: Float,
        config: TempoSyncConfig,
        lfoChannels: List<LfoChannelConfig> = LfoChannelConfig.defaults(),
    ): TempoSyncSnapshot {
        synchronized(lock) {
            val safeDt = dtSec.coerceIn(0f, 0.1f)
            val rawBpm = when (config.mode) {
                TempoSyncMode.AUTO_BEAT -> if (autoConfidence > 0.1f) autoDetectedBpm else config.manualBpm
                TempoSyncMode.TAP_TEMPO -> tapBpm
                TempoSyncMode.MANUAL_BPM -> config.manualBpm
            }.coerceIn(MIN_BPM, MAX_BPM)

            val effectiveBpm = (rawBpm * config.bpmMultiplier.coerceIn(0.25f, 4f)).coerceIn(30f, 300f)
            val beatsPerSec = effectiveBpm / 60.0
            totalBeats += beatsPerSec * safeDt.toDouble()

            if (config.enabled && config.mode == TempoSyncMode.AUTO_BEAT && abs(pendingAutoPhaseNudge) > 1e-4) {
                // Only advance forward or gently bleed backward slightly so clock remains practically monotonic
                val applied = pendingAutoPhaseNudge * 0.35
                if (applied > 0) {
                    totalBeats += applied
                }
                pendingAutoPhaseNudge -= applied
            }

            val currentBeatIndex = floor(totalBeats).toLong()
            val crossedBeatBoundary = currentBeatIndex > lastBeatIndex
            lastBeatIndex = currentBeatIndex

            val beatPhase = (((totalBeats % 1.0) + 1.0) % 1.0).toFloat()
            val barPhase = (((totalBeats / 4.0) % 1.0 + 1.0) % 1.0).toFloat()
            val beatInBar = ((currentBeatIndex % 4L) + 4L).toInt() % 4

            // Advance Master LFO buses (LFO 1..3) smoothly
            val nextValues = FloatArray(3) { 0.5f }
            val nextPhases = FloatArray(3) { 0f }
            for (busIdx in 0 until 3) {
                val cfg = lfoChannels.firstOrNull { it.id == busIdx + 1 }
                    ?: lfoChannels.getOrNull(busIdx)
                if (cfg == null || !cfg.enabled) {
                    nextValues[busIdx] = 0.5f
                    nextPhases[busIdx] = 0f
                    continue
                }

                if (cfg.retriggerOnBeat && crossedBeatBoundary) {
                    lfoBusCycles[busIdx] = floor(lfoBusCycles[busIdx]) + 1.0
                }

                val freqHz = if (cfg.tempoSynced && config.enabled) {
                    (effectiveBpm / 60.0) * cfg.beatDivision.cyclesPerBeat.toDouble()
                } else {
                    cfg.rateHz.coerceIn(0.02f, 20f).toDouble()
                }
                lfoBusCycles[busIdx] += freqHz * safeDt.toDouble()

                val totalCyclesWithPhase = lfoBusCycles[busIdx] + (cfg.phaseDeg / 360.0)
                val normPhase = (((totalCyclesWithPhase % 1.0) + 1.0) % 1.0).toFloat()
                val rawWave = LfoMath.evaluateNormalized(
                    waveform = cfg.waveform,
                    totalCycles = totalCyclesWithPhase,
                    dutyCycle = cfg.dutyCycle,
                    seedOffset = (busIdx + 1) * 97,
                )
                nextValues[busIdx] = LfoMath.applyDepth(rawWave, cfg.depth)
                nextPhases[busIdx] = normPhase
            }
            lastLfoValues = nextValues
            lastLfoPhases = nextPhases

            return TempoSyncSnapshot(
                enabled = config.enabled,
                mode = config.mode,
                effectiveBpm = effectiveBpm,
                rawBpm = rawBpm,
                autoDetectedBpm = autoDetectedBpm,
                tapBpm = tapBpm,
                confidence = autoConfidence,
                totalBeats = totalBeats,
                beatPhase = beatPhase,
                barPhase = barPhase,
                beatInBar = beatInBar,
                beatIndex = currentBeatIndex,
                // At 120 BPM (2 beats/sec), oscillatorTimeSec advances at 1.0x real-time seconds
                oscillatorTimeSec = (totalBeats * 0.5).toFloat(),
                lockVisualOscillators = config.enabled && config.lockVisualOscillators,
                quantizeTransformerChanges = config.enabled && config.quantizeTransformerChanges,
                transformerQuantizeDivision = config.transformerQuantizeDivision,
                lfoBusValues = nextValues,
                lfoBusPhases = nextPhases,
            )
        }
    }

    fun currentSnapshot(config: TempoSyncConfig): TempoSyncSnapshot {
        synchronized(lock) {
            val rawBpm = when (config.mode) {
                TempoSyncMode.AUTO_BEAT -> if (autoConfidence > 0.1f) autoDetectedBpm else config.manualBpm
                TempoSyncMode.TAP_TEMPO -> tapBpm
                TempoSyncMode.MANUAL_BPM -> config.manualBpm
            }.coerceIn(MIN_BPM, MAX_BPM)
            val effectiveBpm = (rawBpm * config.bpmMultiplier.coerceIn(0.25f, 4f)).coerceIn(30f, 300f)
            val beatPhase = (((totalBeats % 1.0) + 1.0) % 1.0).toFloat()
            val barPhase = (((totalBeats / 4.0) % 1.0 + 1.0) % 1.0).toFloat()
            val beatInBar = ((lastBeatIndex % 4L) + 4L).toInt() % 4
            return TempoSyncSnapshot(
                enabled = config.enabled,
                mode = config.mode,
                effectiveBpm = effectiveBpm,
                rawBpm = rawBpm,
                autoDetectedBpm = autoDetectedBpm,
                tapBpm = tapBpm,
                confidence = autoConfidence,
                totalBeats = totalBeats,
                beatPhase = beatPhase,
                barPhase = barPhase,
                beatInBar = beatInBar,
                beatIndex = lastBeatIndex,
                oscillatorTimeSec = (totalBeats * 0.5).toFloat(),
                lockVisualOscillators = config.enabled && config.lockVisualOscillators,
                quantizeTransformerChanges = config.enabled && config.quantizeTransformerChanges,
                transformerQuantizeDivision = config.transformerQuantizeDivision,
                lfoBusValues = lastLfoValues.copyOf(),
                lfoBusPhases = lastLfoPhases.copyOf(),
            )
        }
    }

    companion object {
        const val MIN_BPM = 40f
        const val MAX_BPM = 220f
        private const val MIN_BEAT_INTERVAL_MS = 220L
        private const val MAX_AUTO_GAP_MS = 2200L
        private const val MAX_TAP_GAP_MS = 2400L
    }
}
