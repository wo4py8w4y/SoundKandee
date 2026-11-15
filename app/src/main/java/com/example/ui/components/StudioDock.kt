package com.example.ui.components

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.defaultMinSize
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.KeyboardArrowDown
import androidx.compose.material.icons.filled.KeyboardArrowUp
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.unit.dp
import com.example.audio.AudioFrame
import com.example.audio.DeviceTapConfig
import com.example.audio.SynthFallbackConfig
import com.example.model.AudioSourceType
import com.example.model.ColorEngineConfig
import com.example.model.DspSettings
import com.example.model.LfoChannelConfig
import com.example.model.PresetTransitionConfig
import com.example.model.TempoSyncConfig
import com.example.model.TempoSyncMode
import com.example.model.TempoSyncSnapshot
import com.example.model.TransformerNode
import com.example.model.TransformerType
import com.example.model.VisualLayer
import com.example.model.VisualizerPreset
import com.example.model.VisualStyleType
import com.example.ui.QuickLfoTarget
import com.example.ui.StudioTab
import com.example.ui.VisualizerUiState
import com.example.ui.theme.ElectricCyan
import com.example.ui.theme.GlassSurface
import com.example.ui.theme.LuminousBorder
import com.example.ui.theme.MistSecondary

@Composable
fun StudioDock(
    uiState: VisualizerUiState,
    audioFrame: AudioFrame,
    tempoSnapshot: TempoSyncSnapshot,
    liveValues: Map<String, Float>,
    hasTapHardwareSensor: Boolean,
    isWideScreen: Boolean,
    onSelectTab: (StudioTab) -> Unit,
    onToggleExpanded: () -> Unit,
    onSelectAudioSource: (AudioSourceType) -> Unit,
    onUpdateDsp: ((DspSettings) -> DspSettings) -> Unit,
    onTapTempo: () -> Unit,
    onSetTempoMode: (TempoSyncMode) -> Unit,
    onSetManualBpm: (Float) -> Unit,
    onHalveBpm: () -> Unit,
    onDoubleBpm: () -> Unit,
    onResetBpmMultiplier: () -> Unit,
    onSyncDownbeatPhase: () -> Unit,
    onUpdateTempoSyncConfig: ((TempoSyncConfig) -> TempoSyncConfig) -> Unit,
    onUpdateLfoChannel: (Int, (LfoChannelConfig) -> LfoChannelConfig) -> Unit,
    onAssignQuickLfoTarget: (QuickLfoTarget, Int) -> Unit,
    onUpdateSynthFallback: ((SynthFallbackConfig) -> SynthFallbackConfig) -> Unit,
    onUpdateDeviceTap: ((DeviceTapConfig) -> DeviceTapConfig) -> Unit,
    onSimulateDeviceTapNow: () -> Unit,
    onLoadPreset: (VisualizerPreset) -> Unit,
    onPrevPreset: () -> Unit,
    onNextPreset: () -> Unit,
    onRequestSaveNewPreset: () -> Unit,
    onOverwritePreset: (String) -> Unit,
    onDuplicatePreset: (String) -> Unit,
    onRenamePreset: (String, String, String?) -> Unit,
    onDeletePreset: (String) -> Unit,
    onRevertPreset: () -> Unit,
    onUpdatePresetTransition: ((PresetTransitionConfig) -> PresetTransitionConfig) -> Unit,
    onToggleLayerExpanded: (String) -> Unit,
    onAddLayer: (VisualStyleType) -> Unit,
    onDuplicateLayer: (String) -> Unit,
    onRemoveLayer: (String) -> Unit,
    onMoveLayer: (String, Int) -> Unit,
    onUpdateLayer: (String, (VisualLayer) -> VisualLayer) -> Unit,
    onToggleTransformerExpanded: (String) -> Unit,
    onAddOrEnableTransformer: (TransformerType) -> Unit,
    onRemoveTransformer: (String) -> Unit,
    onUpdateTransformer: (String, (TransformerNode) -> TransformerNode) -> Unit,
    onSelectPalette: (String) -> Unit,
    onUpdateColorEngine: ((ColorEngineConfig) -> ColorEngineConfig) -> Unit,
    onUpdateCustomColor: (Int, Long) -> Unit,
    onAddCustomStop: (Long) -> Unit,
    onRemoveCustomStop: (Int) -> Unit,
    modifier: Modifier = Modifier,
) {
    val containerShape = if (isWideScreen) {
        RoundedCornerShape(topStart = 20.dp, bottomStart = 20.dp)
    } else {
        RoundedCornerShape(topStart = 22.dp, topEnd = 22.dp)
    }

    val dockModifier = if (isWideScreen) {
        modifier
            .width(380.dp)
            .fillMaxHeight()
            .navigationBarsPadding()
            .clip(containerShape)
            .background(GlassSurface)
            .border(1.dp, LuminousBorder, containerShape)
            .padding(horizontal = 14.dp, vertical = 12.dp)
    } else {
        modifier
            .fillMaxWidth()
            .clip(containerShape)
            .background(GlassSurface)
            .border(1.dp, LuminousBorder, containerShape)
            .navigationBarsPadding()
            .padding(horizontal = 14.dp, vertical = 10.dp)
    }

    Column(
        modifier = dockModifier,
        verticalArrangement = Arrangement.spacedBy(10.dp),
    ) {
        Row(
            modifier = Modifier.fillMaxWidth(),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(4.dp),
        ) {
            Row(
                modifier = Modifier.weight(1f),
                horizontalArrangement = Arrangement.spacedBy(3.dp),
            ) {
                StudioTab.entries.forEach { tab ->
                    val selected = uiState.activeTab == tab && uiState.dockExpanded
                    Box(
                        contentAlignment = Alignment.Center,
                        modifier = Modifier
                            .weight(1f)
                            .defaultMinSize(minHeight = 44.dp)
                            .clip(RoundedCornerShape(10.dp))
                            .background(if (selected) ElectricCyan.copy(alpha = 0.2f) else Color.Transparent)
                            .border(
                                1.dp,
                                if (selected) ElectricCyan else LuminousBorder.copy(alpha = 0.6f),
                                RoundedCornerShape(10.dp),
                            )
                            .clickable { onSelectTab(tab) }
                            .padding(vertical = 8.dp, horizontal = 2.dp)
                            .testTag("tab_${tab.name}"),
                    ) {
                        Text(
                            text = tab.label,
                            style = MaterialTheme.typography.labelSmall,
                            color = if (selected) ElectricCyan else MistSecondary,
                            maxLines = 1,
                            softWrap = false,
                        )
                    }
                }
            }

            if (!isWideScreen) {
                IconButton(
                    onClick = onToggleExpanded,
                    modifier = Modifier
                        .size(48.dp)
                        .testTag("toggle_dock_button"),
                ) {
                    Icon(
                        imageVector = if (uiState.dockExpanded) Icons.Filled.KeyboardArrowDown else Icons.Filled.KeyboardArrowUp,
                        contentDescription = if (uiState.dockExpanded) "Collapse studio dock" else "Expand studio dock",
                        tint = Color.White,
                    )
                }
            }
        }

        AnimatedVisibility(visible = uiState.dockExpanded || isWideScreen) {
            Box(
                modifier = if (isWideScreen) {
                    Modifier.fillMaxWidth().weight(1f)
                } else {
                    Modifier.fillMaxWidth().heightIn(min = 180.dp, max = 330.dp)
                },
            ) {
                when (uiState.activeTab) {
                    StudioTab.LAYERS -> LayersTabContent(
                        layers = uiState.layers,
                        expandedLayerId = uiState.expandedLayerId,
                        liveValues = liveValues,
                        onToggleExpanded = onToggleLayerExpanded,
                        onAddLayer = onAddLayer,
                        onDuplicateLayer = onDuplicateLayer,
                        onRemoveLayer = onRemoveLayer,
                        onMoveLayer = onMoveLayer,
                        onUpdateLayer = onUpdateLayer,
                    )
                    StudioTab.TRANSFORMERS -> TransformersTabContent(
                        transformers = uiState.transformers,
                        expandedTransformerId = uiState.expandedTransformerId,
                        liveValues = liveValues,
                        onToggleExpanded = onToggleTransformerExpanded,
                        onAddOrEnable = onAddOrEnableTransformer,
                        onRemove = onRemoveTransformer,
                        onUpdateTransformer = onUpdateTransformer,
                    )
                    StudioTab.LFOS -> LfoAndTempoTabContent(
                        lfoBank = uiState.lfoBank,
                        tempoSyncConfig = uiState.dspSettings.tempoSync,
                        tempoSnapshot = tempoSnapshot,
                        layers = uiState.layers,
                        transformers = uiState.transformers,
                        colorEngine = uiState.colorEngine,
                        onTapTempo = onTapTempo,
                        onSetTempoMode = onSetTempoMode,
                        onSetManualBpm = onSetManualBpm,
                        onHalveBpm = onHalveBpm,
                        onDoubleBpm = onDoubleBpm,
                        onResetBpmMultiplier = onResetBpmMultiplier,
                        onSyncDownbeatPhase = onSyncDownbeatPhase,
                        onUpdateTempoSyncConfig = onUpdateTempoSyncConfig,
                        onUpdateLfoChannel = onUpdateLfoChannel,
                        onAssignQuickTarget = onAssignQuickLfoTarget,
                    )
                    StudioTab.PALETTES -> PalettesTabContent(
                        colorEngine = uiState.colorEngine,
                        liveValues = liveValues,
                        onSelectPalette = onSelectPalette,
                        onUpdateColorEngine = onUpdateColorEngine,
                        onUpdateCustomColor = onUpdateCustomColor,
                        onAddCustomStop = onAddCustomStop,
                        onRemoveCustomStop = onRemoveCustomStop,
                    )
                    StudioTab.PRESETS -> PresetsTabContent(
                        userPresets = uiState.userPresets,
                        builtInPresets = uiState.builtInPresets,
                        activePresetId = uiState.activePresetId,
                        hasUnsavedChanges = uiState.hasUnsavedChanges,
                        currentLayers = uiState.layers,
                        currentTransformers = uiState.transformers,
                        currentColorEngine = uiState.colorEngine,
                        presetTransitionConfig = uiState.presetTransitionConfig,
                        onLoadPreset = onLoadPreset,
                        onPrevPreset = onPrevPreset,
                        onNextPreset = onNextPreset,
                        onRequestSaveNew = onRequestSaveNewPreset,
                        onOverwritePreset = onOverwritePreset,
                        onDuplicatePreset = onDuplicatePreset,
                        onRenamePreset = onRenamePreset,
                        onDeletePreset = onDeletePreset,
                        onRevertPreset = onRevertPreset,
                        onUpdatePresetTransition = onUpdatePresetTransition,
                    )
                    StudioTab.AUDIO_DSP -> AudioDspTabContent(
                        audioSource = uiState.audioSource,
                        audioFrame = audioFrame,
                        dspSettings = uiState.dspSettings,
                        synthFallbackConfig = uiState.synthFallbackConfig,
                        deviceTapConfig = uiState.deviceTapConfig,
                        presetTransitionConfig = uiState.presetTransitionConfig,
                        hasTapHardwareSensor = hasTapHardwareSensor,
                        onSelectAudioSource = onSelectAudioSource,
                        onUpdateDsp = onUpdateDsp,
                        onUpdateSynthFallback = onUpdateSynthFallback,
                        onUpdateDeviceTap = onUpdateDeviceTap,
                        onSimulateDeviceTapNow = onSimulateDeviceTapNow,
                        onUpdatePresetTransition = onUpdatePresetTransition,
                    )
                }
            }
        }
    }
}

