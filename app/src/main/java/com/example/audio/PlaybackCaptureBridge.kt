package com.example.audio

import com.example.model.CaptureStatus
import com.example.model.DspSettings
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow

/**
 * In-process bridge linking AppPlaybackCaptureService to AudioCaptureCoordinator.
 */
object PlaybackCaptureBridge {
    private val _isRunning = MutableStateFlow(false)
    val isRunning: StateFlow<Boolean> = _isRunning.asStateFlow()

    var onAudioFrame: ((AudioFrame) -> Unit)? = null
    var onStatusChanged: ((CaptureStatus) -> Unit)? = null
    var dspSettingsProvider: () -> DspSettings = { DspSettings() }

    fun markRunning(running: Boolean) {
        _isRunning.value = running
    }
}
