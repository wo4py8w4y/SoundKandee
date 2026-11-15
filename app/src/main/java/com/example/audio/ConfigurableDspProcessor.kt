package com.example.audio

import com.example.model.DspSettings
import com.example.model.FftWindowType
import kotlin.math.PI
import kotlin.math.cos
import kotlin.math.hypot
import kotlin.math.ln
import kotlin.math.log10
import kotlin.math.max
import kotlin.math.min
import kotlin.math.pow
import kotlin.math.sin
import kotlin.math.sqrt

/**
 * Pure Kotlin real-time DSP engine supporting selectable FFT windowing,
 * Radix-2 Cooley-Tukey FFT, logarithmic 64-band spectral compression,
 * bass-tilt EQ, noise gating, and spectral-flux beat onset detection.
 */
class ConfigurableDspProcessor(private val fftSize: Int = 1024) {
    private val halfFft = fftSize / 2
    private val real = FloatArray(fftSize)
    private val imag = FloatArray(fftSize)
    private val prevSpectrum = FloatArray(halfFft)
    private val smoothedBands = FloatArray(64)
    private val smoothedLinear = FloatArray(halfFft)
    private val smoothedWave = FloatArray(256)

    private var fluxAverage = 0.05f
    private var lastBeatMs = 0L
    private var beatPulse = 0f

    private var subSmooth = 0f
    private var bassSmooth = 0f
    private var lowMidSmooth = 0f
    private var highMidSmooth = 0f
    private var trebleSmooth = 0f
    private var rmsSmooth = 0f

    private var cachedWindowType: FftWindowType? = null
    private var windowWeights = FloatArray(fftSize)

    private fun ensureWindow(type: FftWindowType) {
        if (cachedWindowType == type) return
        cachedWindowType = type
        val nMinus1 = (fftSize - 1).toFloat()
        for (i in 0 until fftSize) {
            val r = i / nMinus1
            windowWeights[i] = when (type) {
                FftWindowType.HANN -> (0.5f * (1f - cos(2.0 * PI * r))).toFloat()
                FftWindowType.HAMMING -> (0.54f - 0.46f * cos(2.0 * PI * r)).toFloat()
                FftWindowType.BLACKMAN -> (0.42f - 0.5f * cos(2.0 * PI * r) + 0.08f * cos(4.0 * PI * r)).toFloat()
                FftWindowType.RECTANGULAR -> 1f
            }
        }
    }

    fun processPcm16(
        pcm: ShortArray,
        sampleCount: Int,
        sampleRateHz: Int,
        settings: DspSettings,
        nowMs: Long = System.currentTimeMillis(),
    ): AudioFrame {
        if (sampleCount <= 0) return AudioFrame(sampleRateHz = sampleRateHz)
        ensureWindow(settings.fftWindow)

        val gainLin = 10f.pow(settings.inputGainDb / 20f)
        var sumSq = 0.0
        val usable = min(sampleCount, fftSize)

        for (i in 0 until fftSize) {
            val sample = if (i < usable) (pcm[i] / 32768f) * gainLin else 0f
            val clamped = sample.coerceIn(-2f, 2f)
            sumSq += (clamped * clamped)
            real[i] = clamped * windowWeights[i]
            imag[i] = 0f
        }

        val rawRms = sqrt((sumSq / usable).toFloat()).coerceIn(0f, 1.5f)
        val waveOut = FloatArray(256)
        val stride = max(1, usable / 256)
        for (i in 0 until 256) {
            val idx = min(usable - 1, i * stride)
            val s = ((pcm[idx] / 32768f) * gainLin).coerceIn(-1f, 1f)
            val smoothFactor = 0.45f * (1f - settings.globalSmoothing * 0.5f)
            smoothedWave[i] = smoothedWave[i] + (s - smoothedWave[i]) * smoothFactor
            waveOut[i] = smoothedWave[i]
        }

        fftInPlace(real, imag)

        val magnitudes = FloatArray(halfFft)
        val scale = 2.0f / fftSize.toFloat()
        for (i in 0 until halfFft) {
            val mag = hypot(real[i], imag[i]) * scale
            magnitudes[i] = (mag * 3.8f).coerceIn(0f, 2f)
        }

        return buildAudioFrame(
            rawWaveform = waveOut,
            rawSpectrum = magnitudes,
            rawRms = rawRms,
            sampleRateHz = sampleRateHz,
            settings = settings,
            nowMs = nowMs,
        )
    }

    fun processVisualizerBytes(
        waveformBytes: ByteArray?,
        fftBytes: ByteArray?,
        sampleRateHz: Int,
        settings: DspSettings,
        nowMs: Long = System.currentTimeMillis(),
    ): AudioFrame {
        val gainLin = 10f.pow(settings.inputGainDb / 20f)
        val waveOut = FloatArray(256)
        var sumSq = 0.0

        if (waveformBytes != null && waveformBytes.isNotEmpty()) {
            val len = waveformBytes.size
            for (i in 0 until 256) {
                val idx = (i * len) / 256
                val unsigned = (waveformBytes[idx].toInt() and 0xFF) - 128
                val s = ((unsigned / 128f) * gainLin).coerceIn(-1f, 1f)
                sumSq += s * s
                smoothedWave[i] = smoothedWave[i] + (s - smoothedWave[i]) * 0.55f
                waveOut[i] = smoothedWave[i]
            }
        }
        val rawRms = sqrt((sumSq / 256.0).toFloat()).coerceIn(0f, 1.5f)

        val magnitudes = FloatArray(halfFft)
        if (fftBytes != null && fftBytes.size >= 4) {
            val binsAvailable = fftBytes.size / 2
            for (i in 0 until halfFft) {
                val srcBin = ((i.toFloat() / halfFft) * binsAvailable).toInt().coerceIn(0, binsAvailable - 1)
                val re = fftBytes[srcBin * 2].toFloat()
                val im = fftBytes[srcBin * 2 + 1].toFloat()
                val mag = (hypot(re, im) / 128f) * gainLin * 1.4f
                magnitudes[i] = mag.coerceIn(0f, 2f)
            }
        }

        return buildAudioFrame(
            rawWaveform = waveOut,
            rawSpectrum = magnitudes,
            rawRms = rawRms,
            sampleRateHz = sampleRateHz,
            settings = settings,
            nowMs = nowMs,
        )
    }

    private fun buildAudioFrame(
        rawWaveform: FloatArray,
        rawSpectrum: FloatArray,
        rawRms: Float,
        sampleRateHz: Int,
        settings: DspSettings,
        nowMs: Long,
    ): AudioFrame {
        val nyquist = sampleRateHz * 0.5f
        val binHz = nyquist / halfFft.toFloat()
        val smoothAlpha = (1f - settings.globalSmoothing * 0.78f).coerceIn(0.12f, 0.95f)
        val bassTiltLinear = 10f.pow(settings.bassTiltDb / 20f)

        var weightedFreqSum = 0f
        var totalMagSum = 0f
        var maxMag = 0f
        var maxBin = 1
        var flux = 0f

        for (i in 0 until halfFft) {
            val freq = i * binHz
            val tilt = if (freq < 250f) {
                1f + (bassTiltLinear - 1f) * (1f - freq / 250f)
            } else {
                1f
            }
            // Slight perceptual boost for higher frequencies so treble remains vivid
            val presenceBoost = 1f + (i.toFloat() / halfFft) * 1.35f
            val gated = max(0f, rawSpectrum[i] * tilt * presenceBoost - settings.noiseFloor)
            val compressed = ln(1f + gated * 6.5f) / ln(7.5f)
            val clamped = compressed.coerceIn(0f, 1f)

            val prev = prevSpectrum[i]
            if (clamped > prev && i in 1..120) {
                flux += (clamped - prev)
            }
            prevSpectrum[i] = clamped
            smoothedLinear[i] = smoothedLinear[i] + (clamped - smoothedLinear[i]) * smoothAlpha

            if (i >= 1) {
                weightedFreqSum += freq * clamped
                totalMagSum += clamped
                if (clamped > maxMag) {
                    maxMag = clamped
                    maxBin = i
                }
            }
        }

        // Compute 64 logarithmic frequency bands from 25 Hz to nyquist
        val bands64 = FloatArray(64)
        val minLog = ln(25f)
        val maxLog = ln(max(200f, nyquist * 0.88f))
        for (b in 0 until 64) {
            val t0 = b / 64f
            val t1 = (b + 1) / 64f
            val f0 = kotlin.math.exp(minLog + (maxLog - minLog) * t0)
            val f1 = kotlin.math.exp(minLog + (maxLog - minLog) * t1)
            val bin0 = (f0 / binHz).toInt().coerceIn(1, halfFft - 1)
            val bin1 = (f1 / binHz).toInt().coerceIn(bin0, halfFft - 1)
            var sum = 0f
            var peak = 0f
            var cnt = 0
            for (k in bin0..bin1) {
                val v = smoothedLinear[k]
                sum += v
                if (v > peak) peak = v
                cnt++
            }
            val bandVal = if (cnt > 0) (sum / cnt) * 0.55f + peak * 0.45f else 0f
            smoothedBands[b] = smoothedBands[b] + (bandVal - smoothedBands[b]) * smoothAlpha
            bands64[b] = smoothedBands[b].coerceIn(0f, 1f)
        }

        fun bandAvg(lowHz: Float, highHz: Float): Float {
            val b0 = (lowHz / binHz).toInt().coerceIn(1, halfFft - 1)
            val b1 = (highHz / binHz).toInt().coerceIn(b0, halfFft - 1)
            var sum = 0f
            var peak = 0f
            var count = 0
            for (k in b0..b1) {
                val v = smoothedLinear[k]
                sum += v
                if (v > peak) peak = v
                count++
            }
            return if (count > 0) ((sum / count) * 0.6f + peak * 0.4f).coerceIn(0f, 1f) else 0f
        }

        subSmooth = subSmooth + (bandAvg(20f, 80f) - subSmooth) * smoothAlpha
        bassSmooth = bassSmooth + (bandAvg(80f, 250f) - bassSmooth) * smoothAlpha
        lowMidSmooth = lowMidSmooth + (bandAvg(250f, 800f) - lowMidSmooth) * smoothAlpha
        highMidSmooth = highMidSmooth + (bandAvg(800f, 3000f) - highMidSmooth) * smoothAlpha
        trebleSmooth = trebleSmooth + (bandAvg(3000f, 16000f) - trebleSmooth) * smoothAlpha

        val gatedRms = max(0f, rawRms - settings.noiseFloor).coerceIn(0f, 1f)
        rmsSmooth = rmsSmooth + (gatedRms - rmsSmooth) * smoothAlpha
        val rmsDb = (20f * log10(max(1e-4f, rawRms))).coerceIn(-60f, 0f)

        // Spectral-flux beat detection
        fluxAverage = fluxAverage * 0.92f + flux * 0.08f
        val dynamicThresh = max(0.28f, fluxAverage * settings.beatThreshold)
        if (flux > dynamicThresh && (nowMs - lastBeatMs) >= settings.beatCooldownMs && gatedRms > 0.02f) {
            beatPulse = 1.0f
            lastBeatMs = nowMs
        } else {
            beatPulse *= 0.86f
            if (beatPulse < 0.01f) beatPulse = 0f
        }

        val centroidHz = if (totalMagSum > 1e-4f) weightedFreqSum / totalMagSum else 0f
        val centroidNorm = (ln(max(40f, centroidHz) / 40f) / ln(12000f / 40f)).coerceIn(0f, 1f)
        val dominantHz = maxBin * binHz
        val active = rmsSmooth > 0.008f || maxMag > 0.03f

        return AudioFrame(
            waveform = rawWaveform,
            bands64 = bands64,
            linearSpectrum = smoothedLinear.copyOf(),
            sampleRateHz = sampleRateHz,
            subBass = subSmooth,
            bass = bassSmooth,
            lowMid = lowMidSmooth,
            highMid = highMidSmooth,
            treble = trebleSmooth,
            overallRms = rmsSmooth,
            rmsDb = rmsDb,
            spectralCentroid = centroidNorm,
            dominantHz = dominantHz,
            beatPulse = beatPulse,
            isSignalActive = active,
            timestampNs = System.nanoTime(),
        )
    }

    private fun fftInPlace(re: FloatArray, im: FloatArray) {
        val n = re.size
        var j = 0
        for (i in 1 until n) {
            var bit = n shr 1
            while ((j and bit) != 0) {
                j = j xor bit
                bit = bit shr 1
            }
            j = j xor bit
            if (i < j) {
                val tr = re[i]
                re[i] = re[j]
                re[j] = tr
                val ti = im[i]
                im[i] = im[j]
                im[j] = ti
            }
        }

        var len = 2
        while (len <= n) {
            val ang = -2.0 * PI / len
            val wLenRe = cos(ang).toFloat()
            val wLenIm = sin(ang).toFloat()
            var i = 0
            while (i < n) {
                var wRe = 1f
                var wIm = 0f
                val half = len / 2
                for (k in 0 until half) {
                    val uRe = re[i + k]
                    val uIm = im[i + k]
                    val vRe = re[i + k + half] * wRe - im[i + k + half] * wIm
                    val vIm = re[i + k + half] * wIm + im[i + k + half] * wRe
                    re[i + k] = uRe + vRe
                    im[i + k] = uIm + vIm
                    re[i + k + half] = uRe - vRe
                    im[i + k + half] = uIm - vIm
                    val nextWRe = wRe * wLenRe - wIm * wLenIm
                    wIm = wRe * wLenIm + wIm * wLenRe
                    wRe = nextWRe
                }
                i += len
            }
            len = len shl 1
        }
    }
}
