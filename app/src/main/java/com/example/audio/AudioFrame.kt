package com.example.audio

/**
 * Snapshot of live analyzed audio delivered at ~60Hz to the modulation matrix and renderers.
 */
data class AudioFrame(
    val waveform: FloatArray = FloatArray(256),
    val bands64: FloatArray = FloatArray(64),
    val linearSpectrum: FloatArray = FloatArray(512),
    val sampleRateHz: Int = 44100,
    val subBass: Float = 0f,
    val bass: Float = 0f,
    val lowMid: Float = 0f,
    val highMid: Float = 0f,
    val treble: Float = 0f,
    val overallRms: Float = 0f,
    val rmsDb: Float = -60f,
    val spectralCentroid: Float = 0f,
    val dominantHz: Float = 0f,
    val beatPulse: Float = 0f,
    val isSignalActive: Boolean = false,
    val timestampNs: Long = 0L,
) {
    override fun equals(other: Any?): Boolean {
        if (this === other) return true
        if (other !is AudioFrame) return false
        return timestampNs == other.timestampNs && overallRms == other.overallRms && beatPulse == other.beatPulse
    }

    override fun hashCode(): Int {
        var result = timestampNs.hashCode()
        result = 31 * result + overallRms.hashCode()
        result = 31 * result + beatPulse.hashCode()
        return result
    }
}
