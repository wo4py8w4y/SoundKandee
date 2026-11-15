package com.example.audio

import android.Manifest
import android.annotation.SuppressLint
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.media.AudioFormat
import android.media.AudioRecord
import android.media.MediaRecorder
import android.media.audiofx.Visualizer
import android.os.Build
import androidx.core.content.ContextCompat
import com.example.model.AudioSourceType
import com.example.model.CaptureStatus
import com.example.model.DspSettings
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch

/**
 * Coordinates real-time audio capture across Output Mix (Visualizer session 0),
 * App Playback Capture (MediaProjection service), and Microphone (AudioRecord).
 */
class AudioCaptureCoordinator(private val context: Context) {

    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.IO)
    private val dsp = ConfigurableDspProcessor()

    private val _audioFrame = MutableStateFlow(AudioFrame())
    val audioFrame: StateFlow<AudioFrame> = _audioFrame.asStateFlow()

    private val _captureStatus = MutableStateFlow<CaptureStatus>(CaptureStatus.Idle)
    val captureStatus: StateFlow<CaptureStatus> = _captureStatus.asStateFlow()

    @Volatile
    var dspSettings: DspSettings = DspSettings()

    private var activeSourceJob: Job? = null
    private var micRecord: AudioRecord? = null
    private var outputVisualizer: Visualizer? = null

    init {
        PlaybackCaptureBridge.dspSettingsProvider = { dspSettings }
        PlaybackCaptureBridge.onAudioFrame = { frame ->
            _audioFrame.value = frame
        }
        PlaybackCaptureBridge.onStatusChanged = { status ->
            _captureStatus.value = status
        }
    }

    fun hasRecordAudioPermission(): Boolean {
        return ContextCompat.checkSelfPermission(
            context,
            Manifest.permission.RECORD_AUDIO,
        ) == PackageManager.PERMISSION_GRANTED
    }

    fun startMicrophone() {
        stopAll()
        if (!hasRecordAudioPermission()) {
            _captureStatus.value = CaptureStatus.RequestingPermission
            return
        }
        startMicLoop()
    }

    fun startOutputMix() {
        stopAll()
        if (!hasRecordAudioPermission()) {
            _captureStatus.value = CaptureStatus.RequestingPermission
            return
        }
        startVisualizerSessionZero()
    }

    fun startAppPlaybackCapture(resultCode: Int, resultData: Intent) {
        stopAll()
        if (!hasRecordAudioPermission()) {
            _captureStatus.value = CaptureStatus.RequestingPermission
            return
        }
        val intent = Intent(context, AppPlaybackCaptureService::class.java).apply {
            action = AppPlaybackCaptureService.ACTION_START
            putExtra(AppPlaybackCaptureService.EXTRA_RESULT_CODE, resultCode)
            putExtra(AppPlaybackCaptureService.EXTRA_RESULT_DATA, resultData)
        }
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            context.startForegroundService(intent)
        } else {
            context.startService(intent)
        }
    }

    fun startSource(source: AudioSourceType) {
        when (source) {
            AudioSourceType.OUTPUT_MIX -> startOutputMix()
            AudioSourceType.MICROPHONE -> startMicrophone()
            AudioSourceType.APP_PLAYBACK -> {
                // Requires MediaProjection consent intent from Activity; UI handles launch.
                if (!PlaybackCaptureBridge.isRunning.value) {
                    _captureStatus.value = CaptureStatus.RequestingPermission
                }
            }
        }
    }

    @SuppressLint("MissingPermission")
    private fun startMicLoop() {
        val sampleRate = 44100
        val minBuf = AudioRecord.getMinBufferSize(
            sampleRate,
            AudioFormat.CHANNEL_IN_MONO,
            AudioFormat.ENCODING_PCM_16BIT,
        ).coerceAtLeast(2048)

        val record = try {
            AudioRecord(
                MediaRecorder.AudioSource.MIC,
                sampleRate,
                AudioFormat.CHANNEL_IN_MONO,
                AudioFormat.ENCODING_PCM_16BIT,
                minBuf * 2,
            )
        } catch (t: Throwable) {
            _captureStatus.value = CaptureStatus.Error("Microphone unavailable: ${t.message ?: "init error"}")
            return
        }

        if (record.state != AudioRecord.STATE_INITIALIZED) {
            record.release()
            _captureStatus.value = CaptureStatus.Error("Microphone failed to initialize.")
            return
        }

        micRecord = record
        record.startRecording()

        activeSourceJob = scope.launch {
            val buffer = ShortArray(1024)
            while (isActive) {
                val read = record.read(buffer, 0, buffer.size)
                if (read > 0) {
                    val frame = dsp.processPcm16(
                        pcm = buffer,
                        sampleCount = read,
                        sampleRateHz = sampleRate,
                        settings = dspSettings,
                    )
                    _audioFrame.value = frame
                    _captureStatus.value = if (frame.isSignalActive) {
                        CaptureStatus.Active("Microphone", "${sampleRate / 1000}kHz Live")
                    } else {
                        CaptureStatus.SilentSignal("Microphone")
                    }
                }
            }
        }
    }

    private fun startVisualizerSessionZero() {
        val vis = try {
            Visualizer(0)
        } catch (t: Throwable) {
            _captureStatus.value = CaptureStatus.Error(
                "Output Mix restricted on this device (${t.message ?: "unsupported"}). Try App Audio or Microphone."
            )
            return
        }

        try {
            val maxRate = Visualizer.getMaxCaptureRate()
            val captureSizeRange = Visualizer.getCaptureSizeRange()
            val captureSize = captureSizeRange.getOrNull(1)?.coerceAtMost(1024) ?: 1024
            vis.captureSize = captureSize

            var latestWave: ByteArray? = null
            var latestFft: ByteArray? = null

            vis.setDataCaptureListener(
                object : Visualizer.OnDataCaptureListener {
                    override fun onWaveFormDataCapture(
                        visualizer: Visualizer?,
                        waveform: ByteArray?,
                        samplingRate: Int,
                    ) {
                        if (waveform == null) return
                        latestWave = waveform.copyOf()
                        val srHz = (samplingRate / 1000).coerceIn(8000, 48000)
                        val frame = dsp.processVisualizerBytes(
                            waveformBytes = latestWave,
                            fftBytes = latestFft,
                            sampleRateHz = srHz,
                            settings = dspSettings,
                        )
                        _audioFrame.value = frame
                        _captureStatus.value = if (frame.isSignalActive) {
                            CaptureStatus.Active("Output Mix", "Session 0")
                        } else {
                            CaptureStatus.SilentSignal("Output Mix")
                        }
                    }

                    override fun onFftDataCapture(
                        visualizer: Visualizer?,
                        fft: ByteArray?,
                        samplingRate: Int,
                    ) {
                        if (fft == null) return
                        latestFft = fft.copyOf()
                    }
                },
                maxRate.coerceAtMost(20000),
                true,
                true,
            )
            vis.enabled = true
            outputVisualizer = vis
            _captureStatus.value = CaptureStatus.SilentSignal("Output Mix")
        } catch (t: Throwable) {
            try {
                vis.release()
            } catch (_: Throwable) {
            }
            _captureStatus.value = CaptureStatus.Error(
                "Output Mix failed (${t.message ?: "error"}). Try App Audio or Microphone."
            )
        }
    }

    fun stopAll() {
        activeSourceJob?.cancel()
        activeSourceJob = null

        try {
            micRecord?.stop()
        } catch (_: Throwable) {
        }
        try {
            micRecord?.release()
        } catch (_: Throwable) {
        }
        micRecord = null

        try {
            outputVisualizer?.enabled = false
            outputVisualizer?.release()
        } catch (_: Throwable) {
        }
        outputVisualizer = null

        if (PlaybackCaptureBridge.isRunning.value) {
            val stopIntent = Intent(context, AppPlaybackCaptureService::class.java).apply {
                action = AppPlaybackCaptureService.ACTION_STOP
            }
            context.startService(stopIntent)
        }
        _captureStatus.value = CaptureStatus.Idle
    }
}
