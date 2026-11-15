package com.example.audio

import android.annotation.SuppressLint
import android.app.Notification
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.app.Service
import android.content.Context
import android.content.Intent
import android.content.pm.ServiceInfo
import android.media.AudioAttributes
import android.media.AudioFormat
import android.media.AudioPlaybackCaptureConfiguration
import android.media.AudioRecord
import android.media.projection.MediaProjection
import android.media.projection.MediaProjectionManager
import android.os.Build
import android.os.IBinder
import androidx.core.app.NotificationCompat
import com.example.MainActivity
import com.example.R
import com.example.model.CaptureStatus
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch

/**
 * Foreground service with mediaProjection type capturing internal audio playback from other apps
 * on Android 10+ (API 29+) via AudioPlaybackCaptureConfiguration.
 */
class AppPlaybackCaptureService : Service() {

    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.IO)
    private var captureJob: Job? = null
    private var mediaProjection: MediaProjection? = null
    private var audioRecord: AudioRecord? = null
    private val dsp = ConfigurableDspProcessor()

    private val projectionCallback = object : MediaProjection.Callback() {
        override fun onStop() {
            stopCaptureAndSelf()
        }
    }

    override fun onBind(intent: Intent?): IBinder? = null

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        when (intent?.action) {
            ACTION_STOP -> {
                stopCaptureAndSelf()
                return START_NOT_STICKY
            }
            ACTION_START -> {
                startInForeground()
                if (Build.VERSION.SDK_INT < Build.VERSION_CODES.Q) {
                    PlaybackCaptureBridge.onStatusChanged?.invoke(
                        CaptureStatus.Error("App playback capture requires Android 10+. Switching to Output Mix recommended.")
                    )
                    stopSelf()
                    return START_NOT_STICKY
                }
                val resultCode = intent.getIntExtra(EXTRA_RESULT_CODE, 0)
                @Suppress("DEPRECATION")
                val resultData: Intent? = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
                    intent.getParcelableExtra(EXTRA_RESULT_DATA, Intent::class.java)
                } else {
                    intent.getParcelableExtra(EXTRA_RESULT_DATA)
                }

                if (resultCode == 0 || resultData == null) {
                    PlaybackCaptureBridge.onStatusChanged?.invoke(
                        CaptureStatus.Error("App audio capture permission was not granted.")
                    )
                    stopSelf()
                    return START_NOT_STICKY
                }
                startProjectionCapture(resultCode, resultData)
            }
        }
        return START_NOT_STICKY
    }

    private fun startInForeground() {
        val nm = getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            val channel = NotificationChannel(
                CHANNEL_ID,
                getString(R.string.capture_notification_channel_name),
                NotificationManager.IMPORTANCE_LOW,
            ).apply {
                description = getString(R.string.capture_notification_channel_desc)
            }
            nm.createNotificationChannel(channel)
        }

        val openIntent = PendingIntent.getActivity(
            this,
            0,
            Intent(this, MainActivity::class.java),
            PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_UPDATE_CURRENT,
        )
        val stopIntent = PendingIntent.getService(
            this,
            1,
            Intent(this, AppPlaybackCaptureService::class.java).apply { action = ACTION_STOP },
            PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_UPDATE_CURRENT,
        )

        val notification: Notification = NotificationCompat.Builder(this, CHANNEL_ID)
            .setSmallIcon(R.mipmap.ic_launcher_round)
            .setContentTitle(getString(R.string.capture_notification_title))
            .setContentText(getString(R.string.capture_notification_text))
            .setContentIntent(openIntent)
            .addAction(0, "Stop", stopIntent)
            .setOngoing(true)
            .build()

        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
            startForeground(
                NOTIFICATION_ID,
                notification,
                ServiceInfo.FOREGROUND_SERVICE_TYPE_MEDIA_PROJECTION,
            )
        } else {
            startForeground(NOTIFICATION_ID, notification)
        }
    }

    @SuppressLint("MissingPermission")
    private fun startProjectionCapture(resultCode: Int, resultData: Intent) {
        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.Q) return
        stopAudioResources()

        val mgr = getSystemService(Context.MEDIA_PROJECTION_SERVICE) as MediaProjectionManager
        val projection = try {
            mgr.getMediaProjection(resultCode, resultData)
        } catch (t: Throwable) {
            PlaybackCaptureBridge.onStatusChanged?.invoke(
                CaptureStatus.Error("Unable to start media projection: ${t.message ?: "unknown error"}")
            )
            stopSelf()
            return
        }

        if (projection == null) {
            PlaybackCaptureBridge.onStatusChanged?.invoke(
                CaptureStatus.Error("MediaProjection returned null.")
            )
            stopSelf()
            return
        }

        mediaProjection = projection
        projection.registerCallback(projectionCallback, null)

        val sampleRate = 44100
        val minBuf = AudioRecord.getMinBufferSize(
            sampleRate,
            AudioFormat.CHANNEL_IN_MONO,
            AudioFormat.ENCODING_PCM_16BIT,
        ).coerceAtLeast(2048)

        val captureConfig = AudioPlaybackCaptureConfiguration.Builder(projection)
            .addMatchingUsage(AudioAttributes.USAGE_MEDIA)
            .addMatchingUsage(AudioAttributes.USAGE_GAME)
            .addMatchingUsage(AudioAttributes.USAGE_UNKNOWN)
            .build()

        val record = try {
            AudioRecord.Builder()
                .setAudioFormat(
                    AudioFormat.Builder()
                        .setEncoding(AudioFormat.ENCODING_PCM_16BIT)
                        .setSampleRate(sampleRate)
                        .setChannelMask(AudioFormat.CHANNEL_IN_MONO)
                        .build()
                )
                .setBufferSizeInBytes(minBuf * 2)
                .setAudioPlaybackCaptureConfig(captureConfig)
                .build()
        } catch (t: Throwable) {
            PlaybackCaptureBridge.onStatusChanged?.invoke(
                CaptureStatus.Error("Audio playback capture failed: ${t.message ?: "check permissions"}")
            )
            stopCaptureAndSelf()
            return
        }

        if (record.state != AudioRecord.STATE_INITIALIZED) {
            record.release()
            PlaybackCaptureBridge.onStatusChanged?.invoke(
                CaptureStatus.Error("AudioRecord failed to initialize for App Audio.")
            )
            stopCaptureAndSelf()
            return
        }

        audioRecord = record
        PlaybackCaptureBridge.markRunning(true)
        record.startRecording()

        captureJob = scope.launch {
            val buffer = ShortArray(1024)
            while (isActive) {
                val read = record.read(buffer, 0, buffer.size)
                if (read > 0) {
                    val frame = dsp.processPcm16(
                        pcm = buffer,
                        sampleCount = read,
                        sampleRateHz = sampleRate,
                        settings = PlaybackCaptureBridge.dspSettingsProvider(),
                    )
                    PlaybackCaptureBridge.onAudioFrame?.invoke(frame)
                    PlaybackCaptureBridge.onStatusChanged?.invoke(
                        if (frame.isSignalActive) {
                            CaptureStatus.Active("App Audio", "${sampleRate / 1000}kHz PCM")
                        } else {
                            CaptureStatus.SilentSignal("App Audio")
                        }
                    )
                }
            }
        }
    }

    private fun stopAudioResources() {
        captureJob?.cancel()
        captureJob = null
        try {
            audioRecord?.stop()
        } catch (_: Throwable) {
        }
        try {
            audioRecord?.release()
        } catch (_: Throwable) {
        }
        audioRecord = null
        try {
            mediaProjection?.unregisterCallback(projectionCallback)
            mediaProjection?.stop()
        } catch (_: Throwable) {
        }
        mediaProjection = null
        PlaybackCaptureBridge.markRunning(false)
    }

    private fun stopCaptureAndSelf() {
        stopAudioResources()
        PlaybackCaptureBridge.onStatusChanged?.invoke(CaptureStatus.Idle)
        stopForeground(STOP_FOREGROUND_REMOVE)
        stopSelf()
    }

    override fun onDestroy() {
        stopAudioResources()
        scope.cancel()
        super.onDestroy()
    }

    companion object {
        const val ACTION_START = "com.example.action.START_APP_AUDIO_CAPTURE"
        const val ACTION_STOP = "com.example.action.STOP_APP_AUDIO_CAPTURE"
        const val EXTRA_RESULT_CODE = "extra_result_code"
        const val EXTRA_RESULT_DATA = "extra_result_data"
        private const val CHANNEL_ID = "chromawave_audio_capture"
        private const val NOTIFICATION_ID = 4102
    }
}
