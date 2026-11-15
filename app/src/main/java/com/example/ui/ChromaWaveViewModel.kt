package com.example.ui

import android.app.Application
import android.content.Intent
import androidx.compose.runtime.mutableStateMapOf
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import com.example.audio.AudioCaptureCoordinator
import com.example.audio.AudioFrame
import com.example.audio.DeviceTapAction
import com.example.audio.DeviceTapConfig
import com.example.audio.DeviceTapDetector
import com.example.audio.SynthFallbackConfig
import com.example.audio.TempoSyncEngine
import com.example.data.PersistedWorkspace
import com.example.data.PreferencesRepository
import com.example.model.AudioSourceType
import com.example.model.CaptureStatus
import com.example.model.ColorEngineConfig
import com.example.model.DspSettings
import com.example.model.LayerBlendMode
import com.example.model.LfoChannelConfig
import com.example.model.ModulationDriver
import com.example.model.PresetCatalog
import com.example.model.PresetTransitionConfig
import com.example.model.TempoSyncConfig
import com.example.model.TempoSyncMode
import com.example.model.TempoSyncSnapshot
import com.example.model.TransformerNode
import com.example.model.TransformerType
import com.example.model.VisualLayer
import com.example.model.VisualizerPreset
import com.example.model.VisualStyleType
import java.util.UUID
import kotlin.random.Random
import kotlinx.coroutines.FlowPreview
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.debounce
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch

enum class StudioTab(val label: String) {
    LAYERS("Layers"),
    TRANSFORMERS("FX"),
    LFOS("LFO"),
    PALETTES("Color"),
    PRESETS("Presets"),
    AUDIO_DSP("Audio"),
}

enum class QuickLfoTarget(val label: String) {
    FIRST_LAYER_SPIN("Layer Spin"),
    FIRST_LAYER_SCALE("Layer Scale"),
    FIRST_TX_AMOUNT("FX Amt"),
    FIRST_TX_FREQ("FX Freq"),
    HUE_SHIFT("Hue Shift"),
}

data class VisualizerUiState(
    val audioSource: AudioSourceType = AudioSourceType.OUTPUT_MIX,
    val captureStatus: CaptureStatus = CaptureStatus.Idle,
    val dspSettings: DspSettings = DspSettings(),
    val lfoBank: List<LfoChannelConfig> = PresetCatalog.presets.first().lfoBank,
    val deviceTapConfig: DeviceTapConfig = DeviceTapConfig(),
    val synthFallbackConfig: SynthFallbackConfig = SynthFallbackConfig(),
    val presetTransitionConfig: PresetTransitionConfig = PresetTransitionConfig(),
    val visualTransitionToken: Long = 0L,
    val lastDeviceTapEventMs: Long = 0L,
    val layers: List<VisualLayer> = PresetCatalog.presets.first().layers,
    val transformers: List<TransformerNode> = PresetCatalog.presets.first().transformers,
    val colorEngine: ColorEngineConfig = PresetCatalog.defaultColorEngine(PresetCatalog.presets.first().paletteId),
    val activeTab: StudioTab = StudioTab.LAYERS,
    val dockExpanded: Boolean = true,
    val zenMode: Boolean = false,
    val expandedLayerId: String? = PresetCatalog.presets.first().layers.firstOrNull()?.id,
    val expandedTransformerId: String? = PresetCatalog.presets.first().transformers.firstOrNull()?.id,
    val userPresets: List<VisualizerPreset> = emptyList(),
    val builtInPresets: List<VisualizerPreset> = PresetCatalog.presets,
    val activePresetId: String? = PresetCatalog.presets.first().id,
    val hasUnsavedChanges: Boolean = false,
) {
    val allPresets: List<VisualizerPreset>
        get() = userPresets + builtInPresets

    val activePreset: VisualizerPreset?
        get() = allPresets.firstOrNull { it.id == activePresetId }
}

@OptIn(FlowPreview::class)
class ChromaWaveViewModel(application: Application) : AndroidViewModel(application) {

    private val prefsRepo = PreferencesRepository(application)
    val audioCoordinator = AudioCaptureCoordinator(application)
    val tempoSyncEngine = TempoSyncEngine()
    val deviceTapDetector = DeviceTapDetector(application) {
        onPhysicalDeviceTapTriggered()
    }

    private val _uiState = MutableStateFlow(VisualizerUiState())
    val uiState: StateFlow<VisualizerUiState> = _uiState.asStateFlow()

    val audioFrame: StateFlow<AudioFrame> = audioCoordinator.audioFrame

    private val _tempoSnapshot = MutableStateFlow(TempoSyncSnapshot())
    val tempoSnapshot: StateFlow<TempoSyncSnapshot> = _tempoSnapshot.asStateFlow()

    // Live per-frame evaluated values keyed by ScopedParam.id (read inside HUD dots without full-tree recomposition)
    val liveEvaluatedValues = mutableStateMapOf<String, Float>()

    init {
        audioCoordinator.onBeatOnset = { timestampMs ->
            tempoSyncEngine.onAutoBeatDetected(timestampMs)
        }

        viewModelScope.launch {
            val saved = prefsRepo.workspaceFlow.first()
            val savedPresets = prefsRepo.userPresetsFlow.first()
            audioCoordinator.dspSettings = saved.dspSettings
            audioCoordinator.synthFallbackConfig = saved.synthFallbackConfig
            tempoSyncEngine.setTapBpm(saved.dspSettings.tempoSync.tapBpm)
            deviceTapDetector.config = saved.deviceTapConfig
            deviceTapDetector.syncListeningState()
            _uiState.update { current ->
                val all = savedPresets + current.builtInPresets
                val resolvedPresetId = saved.activePresetId?.takeIf { id -> all.any { it.id == id } }
                val basePreset = all.firstOrNull { it.id == resolvedPresetId }
                val dirty = computeDirtyFlag(
                    layers = saved.layers,
                    transformers = saved.transformers,
                    colorEngine = saved.colorEngine,
                    dspSettings = saved.dspSettings,
                    lfoBank = saved.lfoBank,
                    basePreset = basePreset,
                )
                current.copy(
                    audioSource = saved.audioSource,
                    dspSettings = saved.dspSettings,
                    lfoBank = saved.lfoBank,
                    deviceTapConfig = saved.deviceTapConfig,
                    synthFallbackConfig = saved.synthFallbackConfig,
                    presetTransitionConfig = saved.presetTransitionConfig,
                    layers = saved.layers,
                    transformers = saved.transformers,
                    colorEngine = saved.colorEngine,
                    expandedLayerId = saved.layers.firstOrNull()?.id,
                    expandedTransformerId = saved.transformers.firstOrNull()?.id,
                    userPresets = savedPresets,
                    activePresetId = resolvedPresetId,
                    hasUnsavedChanges = dirty,
                    visualTransitionToken = current.visualTransitionToken + 1L,
                )
            }
            _tempoSnapshot.value = tempoSyncEngine.currentSnapshot(_uiState.value.dspSettings.tempoSync)
            if (audioCoordinator.hasRecordAudioPermission() && saved.audioSource != AudioSourceType.APP_PLAYBACK) {
                audioCoordinator.startSource(saved.audioSource)
            }
        }

        viewModelScope.launch {
            audioCoordinator.captureStatus.collect { status ->
                _uiState.update { it.copy(captureStatus = status) }
            }
        }

        viewModelScope.launch {
            _uiState
                .debounce(450L)
                .collect { state ->
                    prefsRepo.saveWorkspace(
                        PersistedWorkspace(
                            audioSource = state.audioSource,
                            dspSettings = state.dspSettings,
                            lfoBank = state.lfoBank,
                            deviceTapConfig = state.deviceTapConfig,
                            synthFallbackConfig = state.synthFallbackConfig,
                            presetTransitionConfig = state.presetTransitionConfig,
                            layers = state.layers,
                            transformers = state.transformers,
                            colorEngine = state.colorEngine,
                            activePresetId = state.activePresetId,
                        )
                    )
                }
        }
    }

    private fun computeDirtyFlag(
        layers: List<VisualLayer>,
        transformers: List<TransformerNode>,
        colorEngine: ColorEngineConfig,
        dspSettings: DspSettings,
        lfoBank: List<LfoChannelConfig>,
        basePreset: VisualizerPreset?,
    ): Boolean {
        if (basePreset == null) return false
        return layers != basePreset.layers ||
            transformers != basePreset.transformers ||
            colorEngine != basePreset.colorEngine ||
            dspSettings != basePreset.dspSettings ||
            lfoBank != basePreset.lfoBank
    }

    private fun updateConfigState(transform: (VisualizerUiState) -> VisualizerUiState) {
        _uiState.update { current ->
            val next = transform(current)
            val base = next.activePreset
            next.copy(
                hasUnsavedChanges = computeDirtyFlag(
                    layers = next.layers,
                    transformers = next.transformers,
                    colorEngine = next.colorEngine,
                    dspSettings = next.dspSettings,
                    lfoBank = next.lfoBank,
                    basePreset = base,
                )
            )
        }
    }

    fun updateTempoSnapshot(snapshot: TempoSyncSnapshot) {
        _tempoSnapshot.value = snapshot
    }

    fun updateLiveEvaluatedValues(map: Map<String, Float>) {
        map.forEach { (k, v) -> liveEvaluatedValues[k] = v }
    }

    fun setActiveTab(tab: StudioTab) {
        _uiState.update { it.copy(activeTab = tab, dockExpanded = true) }
    }

    fun toggleDockExpanded() {
        _uiState.update { it.copy(dockExpanded = !it.dockExpanded) }
    }

    fun toggleZenMode() {
        _uiState.update { it.copy(zenMode = !it.zenMode) }
    }

    fun selectAudioSource(source: AudioSourceType) {
        _uiState.update { it.copy(audioSource = source) }
        if (source != AudioSourceType.APP_PLAYBACK) {
            audioCoordinator.startSource(source)
        }
    }

    fun onRecordPermissionResult(granted: Boolean) {
        if (granted) {
            val src = _uiState.value.audioSource
            if (src != AudioSourceType.APP_PLAYBACK) {
                audioCoordinator.startSource(src)
            }
        } else {
            _uiState.update {
                it.copy(
                    captureStatus = CaptureStatus.Error(
                        "Audio permission denied. Grant Microphone permission in Settings to visualize live audio."
                    )
                )
            }
        }
    }

    fun onMediaProjectionResult(resultCode: Int, data: Intent?) {
        if (resultCode != 0 && data != null) {
            _uiState.update { it.copy(audioSource = AudioSourceType.APP_PLAYBACK) }
            audioCoordinator.startAppPlaybackCapture(resultCode, data)
        } else {
            _uiState.update {
                it.copy(
                    captureStatus = CaptureStatus.Error(
                        "App Audio capture cancelled. Tap Output Mix or Microphone, or try App Audio again."
                    )
                )
            }
        }
    }

    fun stopCapture() {
        audioCoordinator.stopAll()
    }

    fun updateDspSettings(transform: (DspSettings) -> DspSettings) {
        updateConfigState { state ->
            val next = transform(state.dspSettings)
            audioCoordinator.dspSettings = next
            _tempoSnapshot.value = tempoSyncEngine.currentSnapshot(next.tempoSync)
            state.copy(dspSettings = next)
        }
    }

    // --- Tempo Sync & LFO Bank Actions ---

    fun updateTempoSyncConfig(transform: (TempoSyncConfig) -> TempoSyncConfig) {
        updateDspSettings { dsp ->
            dsp.copy(tempoSync = transform(dsp.tempoSync))
        }
    }

    fun tapTempo(nowMs: Long = System.currentTimeMillis()) {
        val bpm = tempoSyncEngine.recordTap(nowMs)
        updateTempoSyncConfig { cfg ->
            cfg.copy(
                enabled = true,
                mode = TempoSyncMode.TAP_TEMPO,
                tapBpm = bpm,
            )
        }
    }

    fun setTempoMode(mode: TempoSyncMode) {
        updateTempoSyncConfig { cfg ->
            cfg.copy(enabled = true, mode = mode)
        }
    }

    fun setManualBpm(bpm: Float) {
        val clamped = bpm.coerceIn(TempoSyncEngine.MIN_BPM, TempoSyncEngine.MAX_BPM)
        updateTempoSyncConfig { cfg ->
            cfg.copy(enabled = true, mode = TempoSyncMode.MANUAL_BPM, manualBpm = clamped)
        }
    }

    fun halveBpm() {
        updateTempoSyncConfig { cfg ->
            cfg.copy(bpmMultiplier = (cfg.bpmMultiplier * 0.5f).coerceIn(0.25f, 4f))
        }
    }

    fun doubleBpm() {
        updateTempoSyncConfig { cfg ->
            cfg.copy(bpmMultiplier = (cfg.bpmMultiplier * 2f).coerceIn(0.25f, 4f))
        }
    }

    fun resetBpmMultiplier() {
        updateTempoSyncConfig { cfg ->
            cfg.copy(bpmMultiplier = 1.0f)
        }
    }

    fun syncDownbeatPhase() {
        tempoSyncEngine.syncDownbeatPhase()
        _tempoSnapshot.value = tempoSyncEngine.currentSnapshot(_uiState.value.dspSettings.tempoSync)
    }

    fun resetAutoTempo() {
        tempoSyncEngine.resetAutoTracker()
        _tempoSnapshot.value = tempoSyncEngine.currentSnapshot(_uiState.value.dspSettings.tempoSync)
    }

    fun updateLfoChannel(channelId: Int, transform: (LfoChannelConfig) -> LfoChannelConfig) {
        updateConfigState { state ->
            state.copy(
                lfoBank = state.lfoBank.map { ch ->
                    if (ch.id == channelId) transform(ch) else ch
                }
            )
        }
    }

    fun assignQuickTargetToLfoBus(target: QuickLfoTarget, busId: Int) {
        val busDriver = when (busId) {
            1 -> ModulationDriver.LFO_BUS_1
            2 -> ModulationDriver.LFO_BUS_2
            else -> ModulationDriver.LFO_BUS_3
        }
        updateConfigState { state ->
            when (target) {
                QuickLfoTarget.FIRST_LAYER_SPIN -> {
                    val first = state.layers.firstOrNull() ?: return@updateConfigState state
                    val nextDriver = if (first.rotationSpeed.driver == busDriver) ModulationDriver.LFO_SINE else busDriver
                    state.copy(
                        layers = state.layers.mapIndexed { i, l ->
                            if (i == 0) l.copy(rotationSpeed = l.rotationSpeed.copy(driver = nextDriver)) else l
                        }
                    )
                }
                QuickLfoTarget.FIRST_LAYER_SCALE -> {
                    val first = state.layers.firstOrNull() ?: return@updateConfigState state
                    val nextDriver = if (first.scale.driver == busDriver) ModulationDriver.BASS else busDriver
                    state.copy(
                        layers = state.layers.mapIndexed { i, l ->
                            if (i == 0) l.copy(scale = l.scale.copy(driver = nextDriver)) else l
                        }
                    )
                }
                QuickLfoTarget.FIRST_TX_AMOUNT -> {
                    val first = state.transformers.firstOrNull() ?: return@updateConfigState state
                    val nextDriver = if (first.amount.driver == busDriver) ModulationDriver.BASS else busDriver
                    state.copy(
                        transformers = state.transformers.mapIndexed { i, t ->
                            if (i == 0) t.copy(amount = t.amount.copy(driver = nextDriver)) else t
                        }
                    )
                }
                QuickLfoTarget.FIRST_TX_FREQ -> {
                    val first = state.transformers.firstOrNull() ?: return@updateConfigState state
                    val nextDriver = if (first.frequency.driver == busDriver) ModulationDriver.LFO_SINE else busDriver
                    state.copy(
                        transformers = state.transformers.mapIndexed { i, t ->
                            if (i == 0) t.copy(frequency = t.frequency.copy(driver = nextDriver)) else t
                        }
                    )
                }
                QuickLfoTarget.HUE_SHIFT -> {
                    val nextDriver = if (state.colorEngine.hueShift.driver == busDriver) {
                        ModulationDriver.SPECTRAL_CENTROID
                    } else busDriver
                    state.copy(
                        colorEngine = state.colorEngine.copy(
                            hueShift = state.colorEngine.hueShift.copy(driver = nextDriver)
                        )
                    )
                }
            }
        }
    }

    // --- Layer stack actions ---
    fun toggleLayerExpanded(layerId: String) {
        _uiState.update {
            it.copy(expandedLayerId = if (it.expandedLayerId == layerId) null else layerId)
        }
    }

    fun addLayer(styleType: VisualStyleType) {
        val newLayer = PresetCatalog.createLayer(
            styleType = styleType,
            blendMode = if (_uiState.value.layers.isEmpty()) LayerBlendMode.NORMAL else LayerBlendMode.SCREEN,
        )
        updateConfigState {
            it.copy(
                layers = it.layers + newLayer,
                expandedLayerId = newLayer.id,
            )
        }
    }

    fun duplicateLayer(layerId: String) {
        updateConfigState { state ->
            val source = state.layers.firstOrNull { it.id == layerId } ?: return@updateConfigState state
            val newId = "l_${UUID.randomUUID().toString().take(8)}"
            val copy = source.copy(
                id = newId,
                locked = false,
                opacity = source.opacity.copy(id = "${newId}_opacity"),
                scale = source.scale.copy(id = "${newId}_scale"),
                rotationSpeed = source.rotationSpeed.copy(id = "${newId}_rot"),
                offsetX = source.offsetX.copy(id = "${newId}_ox"),
                offsetY = source.offsetY.copy(id = "${newId}_oy"),
                primaryParam = source.primaryParam.copy(id = "${newId}_p1"),
                secondaryParam = source.secondaryParam.copy(id = "${newId}_p2"),
                tertiaryParam = source.tertiaryParam.copy(id = "${newId}_p3"),
            )
            state.copy(layers = state.layers + copy, expandedLayerId = newId)
        }
    }

    fun removeLayer(layerId: String) {
        updateConfigState { state ->
            if (state.layers.size <= 1) return@updateConfigState state
            val next = state.layers.filterNot { it.id == layerId }
            state.copy(
                layers = next,
                expandedLayerId = if (state.expandedLayerId == layerId) next.firstOrNull()?.id else state.expandedLayerId,
            )
        }
    }

    fun moveLayer(layerId: String, delta: Int) {
        updateConfigState { state ->
            val idx = state.layers.indexOfFirst { it.id == layerId }
            if (idx < 0) return@updateConfigState state
            val target = (idx + delta).coerceIn(0, state.layers.lastIndex)
            if (target == idx) return@updateConfigState state
            val mutable = state.layers.toMutableList()
            val item = mutable.removeAt(idx)
            mutable.add(target, item)
            state.copy(layers = mutable)
        }
    }

    fun updateLayer(layerId: String, transform: (VisualLayer) -> VisualLayer) {
        updateConfigState { state ->
            state.copy(
                layers = state.layers.map { if (it.id == layerId) transform(it) else it },
            )
        }
    }

    // --- Transformer stack actions ---
    fun toggleTransformerExpanded(id: String) {
        _uiState.update {
            it.copy(expandedTransformerId = if (it.expandedTransformerId == id) null else id)
        }
    }

    fun addOrEnableTransformer(type: TransformerType) {
        updateConfigState { state ->
            val existing = state.transformers.firstOrNull { it.type == type }
            if (existing != null) {
                state.copy(
                    transformers = state.transformers.map {
                        if (it.id == existing.id) it.copy(enabled = true) else it
                    },
                    expandedTransformerId = existing.id,
                )
            } else {
                val created = PresetCatalog.createTransformer(type, enabled = true)
                state.copy(
                    transformers = state.transformers + created,
                    expandedTransformerId = created.id,
                )
            }
        }
    }

    fun removeTransformer(id: String) {
        updateConfigState { state ->
            val next = state.transformers.filterNot { it.id == id }
            state.copy(
                transformers = next,
                expandedTransformerId = if (state.expandedTransformerId == id) next.firstOrNull()?.id else state.expandedTransformerId,
            )
        }
    }

    fun updateTransformer(id: String, transform: (TransformerNode) -> TransformerNode) {
        updateConfigState { state ->
            state.copy(
                transformers = state.transformers.map { if (it.id == id) transform(it) else it },
            )
        }
    }

    // --- Color Engine actions ---
    fun selectPalette(paletteId: String) {
        updateConfigState {
            it.copy(
                colorEngine = it.colorEngine.copy(paletteId = paletteId, useCustomPalette = false),
            )
        }
    }

    fun updateColorEngine(transform: (ColorEngineConfig) -> ColorEngineConfig) {
        updateConfigState { state ->
            state.copy(colorEngine = transform(state.colorEngine))
        }
    }

    fun updateCustomColor(index: Int, argb: Long) {
        updateConfigState { state ->
            val list = state.colorEngine.customColors.toMutableList()
            if (index in list.indices) {
                list[index] = argb
            }
            state.copy(
                colorEngine = state.colorEngine.copy(useCustomPalette = true, customColors = list),
            )
        }
    }

    fun addCustomColorStop(argb: Long) {
        updateConfigState { state ->
            val list = state.colorEngine.customColors
            if (list.size >= 5) return@updateConfigState state
            state.copy(
                colorEngine = state.colorEngine.copy(useCustomPalette = true, customColors = list + argb),
            )
        }
    }

    fun removeCustomColorStop(index: Int) {
        updateConfigState { state ->
            val list = state.colorEngine.customColors
            if (list.size <= 2 || index !in list.indices) return@updateConfigState state
            val next = list.toMutableList().apply { removeAt(index) }
            state.copy(
                colorEngine = state.colorEngine.copy(useCustomPalette = true, customColors = next),
            )
        }
    }

    // --- Preset Management System ---

    fun saveCurrentAsPreset(name: String, subtitle: String = ""): VisualizerPreset {
        val state = _uiState.value
        val now = System.currentTimeMillis()
        val resolvedName = name.trim().ifBlank { "Custom Preset ${state.userPresets.size + 1}" }
        val resolvedSubtitle = subtitle.trim().ifBlank {
            PresetCatalog.summarizeConfiguration(state.layers, state.transformers, state.colorEngine)
        }
        val snapshotLayers = PresetCatalog.cloneLayersWithFreshIds(state.layers)
        val snapshotTransformers = PresetCatalog.cloneTransformersWithFreshIds(state.transformers)
        val preset = VisualizerPreset(
            id = "usr_${UUID.randomUUID().toString().take(10)}",
            name = resolvedName,
            subtitle = resolvedSubtitle,
            layers = snapshotLayers,
            transformers = snapshotTransformers,
            paletteId = state.colorEngine.paletteId,
            colorEngine = state.colorEngine,
            dspSettings = state.dspSettings,
            lfoBank = state.lfoBank,
            isBuiltIn = false,
            createdAtEpochMs = now,
            updatedAtEpochMs = now,
        )
        val updatedList = listOf(preset) + state.userPresets
        _uiState.update { current ->
            current.copy(
                layers = preset.layers,
                transformers = preset.transformers,
                expandedLayerId = preset.layers.firstOrNull()?.id,
                expandedTransformerId = preset.transformers.firstOrNull()?.id,
                userPresets = updatedList,
                activePresetId = preset.id,
                hasUnsavedChanges = false,
            )
        }
        viewModelScope.launch {
            prefsRepo.saveUserPresets(updatedList)
        }
        return preset
    }

    fun overwritePreset(presetId: String): Boolean {
        val state = _uiState.value
        val existing = state.userPresets.firstOrNull { it.id == presetId } ?: return false
        val now = System.currentTimeMillis()
        val updatedPreset = existing.copy(
            subtitle = PresetCatalog.summarizeConfiguration(state.layers, state.transformers, state.colorEngine),
            layers = state.layers,
            transformers = state.transformers,
            paletteId = state.colorEngine.paletteId,
            colorEngine = state.colorEngine,
            dspSettings = state.dspSettings,
            lfoBank = state.lfoBank,
            updatedAtEpochMs = now,
        )
        val updatedList = state.userPresets.map { if (it.id == presetId) updatedPreset else it }
        _uiState.update { current ->
            current.copy(
                userPresets = updatedList,
                activePresetId = presetId,
                hasUnsavedChanges = false,
            )
        }
        viewModelScope.launch {
            prefsRepo.saveUserPresets(updatedList)
        }
        return true
    }

    fun loadPreset(preset: VisualizerPreset) {
        audioCoordinator.dspSettings = preset.dspSettings
        tempoSyncEngine.setTapBpm(preset.dspSettings.tempoSync.tapBpm)
        _tempoSnapshot.value = tempoSyncEngine.currentSnapshot(preset.dspSettings.tempoSync)
        _uiState.update { state ->
            state.copy(
                layers = preset.layers,
                transformers = preset.transformers,
                colorEngine = preset.colorEngine,
                dspSettings = preset.dspSettings,
                lfoBank = preset.lfoBank,
                expandedLayerId = preset.layers.firstOrNull()?.id,
                expandedTransformerId = preset.transformers.firstOrNull()?.id,
                activePresetId = preset.id,
                hasUnsavedChanges = false,
                visualTransitionToken = state.visualTransitionToken + 1L,
            )
        }
    }

    fun loadPresetById(presetId: String): Boolean {
        val target = _uiState.value.allPresets.firstOrNull { it.id == presetId } ?: return false
        loadPreset(target)
        return true
    }

    fun switchPreset(presetId: String): Boolean = loadPresetById(presetId)

    fun switchToNextPreset(): VisualizerPreset? {
        val state = _uiState.value
        val all = state.allPresets
        if (all.isEmpty()) return null
        val currentIdx = all.indexOfFirst { it.id == state.activePresetId }
        val nextIdx = if (currentIdx < 0) 0 else (currentIdx + 1) % all.size
        val target = all[nextIdx]
        loadPreset(target)
        return target
    }

    fun switchToPreviousPreset(): VisualizerPreset? {
        val state = _uiState.value
        val all = state.allPresets
        if (all.isEmpty()) return null
        val currentIdx = all.indexOfFirst { it.id == state.activePresetId }
        val prevIdx = if (currentIdx <= 0) all.lastIndex else currentIdx - 1
        val target = all[prevIdx]
        loadPreset(target)
        return target
    }

    fun duplicatePreset(presetId: String, newName: String? = null): VisualizerPreset? {
        val state = _uiState.value
        val source = state.allPresets.firstOrNull { it.id == presetId } ?: return null
        val now = System.currentTimeMillis()
        val copyName = newName?.trim()?.takeIf { it.isNotBlank() } ?: "${source.name} (Copy)"
        val copy = source.copy(
            id = "usr_${UUID.randomUUID().toString().take(10)}",
            name = copyName,
            layers = PresetCatalog.cloneLayersWithFreshIds(source.layers),
            transformers = PresetCatalog.cloneTransformersWithFreshIds(source.transformers),
            isBuiltIn = false,
            createdAtEpochMs = now,
            updatedAtEpochMs = now,
        )
        val updatedList = listOf(copy) + state.userPresets
        audioCoordinator.dspSettings = copy.dspSettings
        tempoSyncEngine.setTapBpm(copy.dspSettings.tempoSync.tapBpm)
        _tempoSnapshot.value = tempoSyncEngine.currentSnapshot(copy.dspSettings.tempoSync)
        _uiState.update { current ->
            current.copy(
                layers = copy.layers,
                transformers = copy.transformers,
                colorEngine = copy.colorEngine,
                dspSettings = copy.dspSettings,
                lfoBank = copy.lfoBank,
                expandedLayerId = copy.layers.firstOrNull()?.id,
                expandedTransformerId = copy.transformers.firstOrNull()?.id,
                userPresets = updatedList,
                activePresetId = copy.id,
                hasUnsavedChanges = false,
                visualTransitionToken = current.visualTransitionToken + 1L,
            )
        }
        viewModelScope.launch {
            prefsRepo.saveUserPresets(updatedList)
        }
        return copy
    }

    fun renamePreset(presetId: String, newName: String, newSubtitle: String? = null): Boolean {
        val trimmed = newName.trim()
        if (trimmed.isBlank()) return false
        val state = _uiState.value
        if (state.userPresets.none { it.id == presetId }) return false
        val now = System.currentTimeMillis()
        val updatedList = state.userPresets.map { preset ->
            if (preset.id == presetId) {
                preset.copy(
                    name = trimmed,
                    subtitle = newSubtitle?.trim() ?: preset.subtitle,
                    updatedAtEpochMs = now,
                )
            } else preset
        }
        _uiState.update { it.copy(userPresets = updatedList) }
        viewModelScope.launch {
            prefsRepo.saveUserPresets(updatedList)
        }
        return true
    }

    fun deletePreset(presetId: String): Boolean {
        val state = _uiState.value
        if (state.userPresets.none { it.id == presetId }) return false
        val updatedList = state.userPresets.filterNot { it.id == presetId }
        _uiState.update { current ->
            val wasActive = current.activePresetId == presetId
            current.copy(
                userPresets = updatedList,
                activePresetId = if (wasActive) null else current.activePresetId,
                hasUnsavedChanges = if (wasActive) false else current.hasUnsavedChanges,
            )
        }
        viewModelScope.launch {
            prefsRepo.saveUserPresets(updatedList)
        }
        return true
    }

    fun revertToActivePreset(): Boolean {
        val preset = _uiState.value.activePreset ?: return false
        loadPreset(preset)
        return true
    }

    fun mutate() {
        updateConfigState { state ->
            val (layers, txs, color) = PresetCatalog.mutate(state.layers, state.transformers, state.colorEngine)
            val bump = if (state.presetTransitionConfig.crossfadeOnRandomize) 1L else 0L
            state.copy(
                layers = layers,
                transformers = txs,
                colorEngine = color,
                visualTransitionToken = state.visualTransitionToken + bump,
            )
        }
    }

    fun randomize() {
        updateConfigState { state ->
            val (layers, txs, color) = PresetCatalog.randomize(state.layers, state.transformers, state.colorEngine)
            val bump = if (state.presetTransitionConfig.crossfadeOnRandomize) 1L else 0L
            state.copy(
                layers = layers,
                transformers = txs,
                colorEngine = color,
                expandedLayerId = layers.firstOrNull()?.id,
                expandedTransformerId = txs.firstOrNull()?.id,
                visualTransitionToken = state.visualTransitionToken + bump,
            )
        }
    }

    fun updatePresetTransitionConfig(transform: (PresetTransitionConfig) -> PresetTransitionConfig) {
        _uiState.update { state ->
            val raw = transform(state.presetTransitionConfig)
            val clamped = raw.copy(
                durationMs = raw.durationMs.coerceIn(
                    PresetTransitionConfig.MIN_DURATION_MS,
                    PresetTransitionConfig.MAX_DURATION_MS,
                )
            )
            state.copy(presetTransitionConfig = clamped)
        }
    }

    fun updateDeviceTapConfig(transform: (DeviceTapConfig) -> DeviceTapConfig) {
        _uiState.update { state ->
            val next = transform(state.deviceTapConfig)
            deviceTapDetector.config = next
            deviceTapDetector.syncListeningState()
            state.copy(deviceTapConfig = next)
        }
    }

    fun onPhysicalDeviceTapTriggered(nowMs: Long = System.currentTimeMillis()) {
        val cfg = _uiState.value.deviceTapConfig
        when (cfg.action) {
            DeviceTapAction.RANDOMIZE_OPTIONS -> randomize()
            DeviceTapAction.MUTATE_SCOPES -> mutate()
            DeviceTapAction.RANDOM_PRESET -> {
                val all = _uiState.value.allPresets
                val activeId = _uiState.value.activePresetId
                val candidates = all.filter { it.id != activeId }.ifEmpty { all }
                if (candidates.isNotEmpty()) {
                    loadPreset(candidates[Random.nextInt(candidates.size)])
                }
            }
        }
        _uiState.update { it.copy(lastDeviceTapEventMs = nowMs) }
    }

    fun updateSynthFallbackConfig(transform: (SynthFallbackConfig) -> SynthFallbackConfig) {
        _uiState.update { state ->
            val next = transform(state.synthFallbackConfig)
            audioCoordinator.synthFallbackConfig = next
            state.copy(synthFallbackConfig = next)
        }
    }

    override fun onCleared() {
        deviceTapDetector.stop()
        audioCoordinator.release()
        super.onCleared()
    }
}

