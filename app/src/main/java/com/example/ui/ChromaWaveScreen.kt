package com.example.ui

import android.Manifest
import android.content.Context
import android.content.pm.PackageManager
import android.media.projection.MediaProjectionManager
import android.os.Build
import androidx.activity.compose.BackHandler
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.slideInVertically
import androidx.compose.animation.slideOutVertically
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.defaultMinSize
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.KeyboardArrowLeft
import androidx.compose.material.icons.automirrored.filled.KeyboardArrowRight
import androidx.compose.material.icons.filled.AutoAwesome
import androidx.compose.material.icons.filled.BookmarkAdd
import androidx.compose.material.icons.filled.Casino
import androidx.compose.material.icons.filled.Fullscreen
import androidx.compose.material.icons.filled.GraphicEq
import androidx.compose.material.icons.filled.Mic
import androidx.compose.material.icons.filled.MusicNote
import androidx.compose.material.icons.filled.Tune
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.hapticfeedback.HapticFeedbackType
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalHapticFeedback
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.core.content.ContextCompat
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleEventObserver
import androidx.lifecycle.compose.LocalLifecycleOwner
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.example.R
import com.example.model.AudioSourceType
import com.example.model.CaptureStatus
import com.example.model.ModulationEvaluator
import com.example.model.PresetCatalog
import com.example.render.VisualizerCanvas
import com.example.ui.components.SavePresetDialog
import com.example.ui.components.StudioDock
import com.example.ui.theme.AcidMagenta
import com.example.ui.theme.ElectricCyan
import com.example.ui.theme.ElevatedSurface
import com.example.ui.theme.ErrorCoral
import com.example.ui.theme.GlassSurface
import com.example.ui.theme.LuminousBorder
import com.example.ui.theme.MistSecondary
import com.example.ui.theme.MonoTelemetryStyle
import com.example.ui.theme.SolarAmber
import com.example.ui.theme.VoidObsidian

@Composable
fun ChromaWaveScreen(
    viewModel: ChromaWaveViewModel,
    modifier: Modifier = Modifier,
) {
    val context = LocalContext.current
    val lifecycleOwner = LocalLifecycleOwner.current
    val haptics = LocalHapticFeedback.current
    val uiState by viewModel.uiState.collectAsStateWithLifecycle()
    val audioFrame by viewModel.audioFrame.collectAsStateWithLifecycle()
    val tempoSnapshot by viewModel.tempoSnapshot.collectAsStateWithLifecycle()

    var pendingSourceAfterPermission by remember { mutableStateOf<AudioSourceType?>(null) }
    var showSavePresetDialog by remember { mutableStateOf(false) }

    DisposableEffect(lifecycleOwner, viewModel.deviceTapDetector) {
        val observer = LifecycleEventObserver { _, event ->
            when (event) {
                Lifecycle.Event.ON_RESUME -> viewModel.deviceTapDetector.syncListeningState()
                Lifecycle.Event.ON_PAUSE -> viewModel.deviceTapDetector.stop()
                else -> Unit
            }
        }
        lifecycleOwner.lifecycle.addObserver(observer)
        if (lifecycleOwner.lifecycle.currentState.isAtLeast(Lifecycle.State.STARTED)) {
            viewModel.deviceTapDetector.syncListeningState()
        }
        onDispose {
            lifecycleOwner.lifecycle.removeObserver(observer)
            viewModel.deviceTapDetector.stop()
        }
    }

    LaunchedEffect(uiState.lastDeviceTapEventMs) {
        if (uiState.lastDeviceTapEventMs > 0L) {
            haptics.performHapticFeedback(HapticFeedbackType.LongPress)
        }
    }

    val mediaProjectionLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.StartActivityForResult(),
    ) { result ->
        viewModel.onMediaProjectionResult(result.resultCode, result.data)
    }

    val notificationPermissionLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.RequestPermission(),
    ) { _ ->
        val mgr = context.getSystemService(Context.MEDIA_PROJECTION_SERVICE) as MediaProjectionManager
        mediaProjectionLauncher.launch(mgr.createScreenCaptureIntent())
    }

    val launchAppPlaybackFlow: () -> Unit = {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU &&
            ContextCompat.checkSelfPermission(context, Manifest.permission.POST_NOTIFICATIONS) != PackageManager.PERMISSION_GRANTED
        ) {
            notificationPermissionLauncher.launch(Manifest.permission.POST_NOTIFICATIONS)
        } else {
            val mgr = context.getSystemService(Context.MEDIA_PROJECTION_SERVICE) as MediaProjectionManager
            mediaProjectionLauncher.launch(mgr.createScreenCaptureIntent())
        }
    }

    val recordAudioLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.RequestPermission(),
    ) { granted ->
        viewModel.onRecordPermissionResult(granted)
        val pending = pendingSourceAfterPermission
        pendingSourceAfterPermission = null
        if (granted && pending != null) {
            if (pending == AudioSourceType.APP_PLAYBACK) {
                launchAppPlaybackFlow()
            } else {
                viewModel.selectAudioSource(pending)
            }
        }
    }

    val requestAudioSource: (AudioSourceType) -> Unit = { source ->
        val hasMic = ContextCompat.checkSelfPermission(
            context,
            Manifest.permission.RECORD_AUDIO,
        ) == PackageManager.PERMISSION_GRANTED

        if (!hasMic) {
            pendingSourceAfterPermission = source
            viewModel.selectAudioSource(source)
            recordAudioLauncher.launch(Manifest.permission.RECORD_AUDIO)
        } else if (source == AudioSourceType.APP_PLAYBACK) {
            launchAppPlaybackFlow()
        } else {
            viewModel.selectAudioSource(source)
        }
    }

    BackHandler(enabled = uiState.zenMode) {
        viewModel.toggleZenMode()
    }

    BoxWithConstraints(
        modifier = modifier
            .fillMaxSize()
            .background(VoidObsidian),
    ) {
        val isWideScreen = maxWidth >= 720.dp

        Row(modifier = Modifier.fillMaxSize()) {
            Box(
                modifier = Modifier
                    .weight(1f)
                    .fillMaxHeight(),
            ) {
                VisualizerCanvas(
                    layers = uiState.layers,
                    transformers = uiState.transformers,
                    colorEngine = uiState.colorEngine,
                    tempoSyncConfig = uiState.dspSettings.tempoSync,
                    lfoBank = uiState.lfoBank,
                    tempoSyncEngine = viewModel.tempoSyncEngine,
                    audioFrameProvider = { viewModel.audioFrame.value },
                    onLiveEvaluatedValues = { map -> viewModel.updateLiveEvaluatedValues(map) },
                    onTempoSnapshot = viewModel::updateTempoSnapshot,
                    onTapCanvas = { viewModel.toggleZenMode() },
                    modifier = Modifier
                        .fillMaxSize()
                        .testTag("visualizer_canvas"),
                )

                // Top Glassmorphic HUD Bar
                androidx.compose.animation.AnimatedVisibility(
                    visible = !uiState.zenMode,
                    enter = fadeIn() + slideInVertically { -it },
                    exit = fadeOut() + slideOutVertically { -it },
                    modifier = Modifier.align(Alignment.TopCenter),
                ) {
                    Column(
                        modifier = Modifier
                            .fillMaxWidth()
                            .statusBarsPadding()
                            .padding(horizontal = 12.dp, vertical = 10.dp),
                        verticalArrangement = Arrangement.spacedBy(8.dp),
                    ) {
                        Row(
                            modifier = Modifier
                                .fillMaxWidth()
                                .clip(RoundedCornerShape(16.dp))
                                .background(GlassSurface)
                                .border(1.dp, LuminousBorder, RoundedCornerShape(16.dp))
                                .padding(horizontal = 12.dp, vertical = 8.dp),
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.SpaceBetween,
                        ) {
                            Row(
                                verticalAlignment = Alignment.CenterVertically,
                                horizontalArrangement = Arrangement.spacedBy(8.dp),
                                modifier = Modifier.weight(1f),
                            ) {
                                val statusRingColor = when (uiState.captureStatus) {
                                    is CaptureStatus.Active ->
                                        if (audioFrame.beatPulse > 0.35f) AcidMagenta else ElectricCyan
                                    is CaptureStatus.SyntheticFallback -> SolarAmber
                                    is CaptureStatus.SilentSignal -> SolarAmber
                                    is CaptureStatus.Error -> ErrorCoral
                                    else -> LuminousBorder
                                }
                                Box(
                                    modifier = Modifier
                                        .size(34.dp)
                                        .clip(CircleShape)
                                        .border(1.5.dp, statusRingColor, CircleShape)
                                        .padding(1.5.dp),
                                ) {
                                    Image(
                                        painter = painterResource(id = R.drawable.soundkandee_icon_1794726411343),
                                        contentDescription = "SoundKandee candy K-ee emblem",
                                        contentScale = ContentScale.Crop,
                                        modifier = Modifier
                                            .fillMaxSize()
                                            .clip(CircleShape),
                                    )
                                }
                                Column(verticalArrangement = Arrangement.spacedBy(2.dp)) {
                                    Image(
                                        painter = painterResource(id = R.drawable.img_soundkandee_wordmark),
                                        contentDescription = "SoundKandee",
                                        contentScale = ContentScale.Fit,
                                        modifier = Modifier
                                            .height(26.dp)
                                            .clip(RoundedCornerShape(6.dp))
                                            .testTag("soundkandee_logo"),
                                    )
                                    val statusText = when (val st = uiState.captureStatus) {
                                        is CaptureStatus.Active -> "${st.sourceLabel} • ${audioFrame.rmsDb.toInt()}dB • ${ModulationEvaluator.formatHz(audioFrame.dominantHz)}"
                                        is CaptureStatus.SyntheticFallback -> "Synth Idle • ${st.styleLabel} • ${audioFrame.rmsDb.toInt()}dB"
                                        is CaptureStatus.SilentSignal -> "${st.sourceLabel} • Listening (quiet)"
                                        CaptureStatus.RequestingPermission -> "Permission needed"
                                        is CaptureStatus.Error -> "Audio issue"
                                        CaptureStatus.Idle -> "Tap source to connect audio"
                                    }
                                    Text(
                                        text = statusText,
                                        style = MonoTelemetryStyle,
                                        color = if (uiState.captureStatus is CaptureStatus.SyntheticFallback) SolarAmber else MistSecondary,
                                        maxLines = 1,
                                    )
                                }
                            }

                            Row(
                                verticalAlignment = Alignment.CenterVertically,
                                horizontalArrangement = Arrangement.spacedBy(2.dp),
                            ) {
                                Box(
                                    contentAlignment = Alignment.Center,
                                    modifier = Modifier
                                        .defaultMinSize(minWidth = 48.dp, minHeight = 48.dp)
                                        .clip(RoundedCornerShape(10.dp))
                                        .background(ElevatedSurface.copy(alpha = 0.85f))
                                        .border(
                                            1.dp,
                                            if (tempoSnapshot.beatInBar == 0 && tempoSnapshot.beatPhase < 0.25f) AcidMagenta else ElectricCyan.copy(alpha = 0.55f),
                                            RoundedCornerShape(10.dp),
                                        )
                                        .clickable { viewModel.tapTempo() }
                                        .padding(horizontal = 8.dp, vertical = 6.dp)
                                        .testTag("hud_tap_tempo_btn"),
                                ) {
                                    Text(
                                        text = "${tempoSnapshot.effectiveBpm.toInt()} BPM",
                                        style = MonoTelemetryStyle.copy(fontWeight = FontWeight.Bold),
                                        color = ElectricCyan,
                                        maxLines = 1,
                                        softWrap = false,
                                    )
                                }
                                IconButton(
                                    onClick = { viewModel.mutate() },
                                    modifier = Modifier
                                        .size(48.dp)
                                        .testTag("mutate_button"),
                                ) {
                                    Icon(
                                        imageVector = Icons.Filled.AutoAwesome,
                                        contentDescription = "Mutate within scopes",
                                        tint = ElectricCyan,
                                    )
                                }
                                IconButton(
                                    onClick = { viewModel.randomize() },
                                    modifier = Modifier
                                        .size(48.dp)
                                        .testTag("randomize_button"),
                                ) {
                                    Icon(
                                        imageVector = Icons.Filled.Casino,
                                        contentDescription = "Randomize overlapping layers",
                                        tint = AcidMagenta,
                                    )
                                }
                                IconButton(
                                    onClick = { viewModel.toggleZenMode() },
                                    modifier = Modifier
                                        .size(48.dp)
                                        .testTag("zen_mode_button"),
                                ) {
                                    Icon(
                                        imageVector = Icons.Filled.Fullscreen,
                                        contentDescription = "Enter Zen mode",
                                        tint = Color.White,
                                    )
                                }
                            }
                        }

                        // Quick Preset Switcher Strip
                        QuickPresetSwitcherBar(
                            presetName = uiState.activePreset?.name ?: "Custom Mix",
                            isModified = uiState.hasUnsavedChanges,
                            layerCount = uiState.layers.count { it.enabled },
                            txCount = uiState.transformers.count { it.enabled },
                            onPrevPreset = viewModel::switchToPreviousPreset,
                            onNextPreset = viewModel::switchToNextPreset,
                            onOpenPresets = { viewModel.setActiveTab(StudioTab.PRESETS) },
                            onQuickSave = { showSavePresetDialog = true },
                        )

                        // Actionable Audio Connect / Error Card when audio is not actively connected
                        if (uiState.captureStatus is CaptureStatus.Idle ||
                            uiState.captureStatus is CaptureStatus.RequestingPermission ||
                            uiState.captureStatus is CaptureStatus.Error
                        ) {
                            AudioConnectBanner(
                                status = uiState.captureStatus,
                                onChooseSource = requestAudioSource,
                            )
                        }
                    }
                }

                // Bottom Studio Dock on compact/phone screens
                if (!isWideScreen) {
                    androidx.compose.animation.AnimatedVisibility(
                        visible = !uiState.zenMode,
                        enter = fadeIn() + slideInVertically { it },
                        exit = fadeOut() + slideOutVertically { it },
                        modifier = Modifier.align(Alignment.BottomCenter),
                    ) {
                        StudioDock(
                            uiState = uiState,
                            audioFrame = audioFrame,
                            tempoSnapshot = tempoSnapshot,
                            liveValues = viewModel.liveEvaluatedValues,
                            hasTapHardwareSensor = viewModel.deviceTapDetector.hasHardwareSensor,
                            isWideScreen = false,
                            onSelectTab = viewModel::setActiveTab,
                            onToggleExpanded = viewModel::toggleDockExpanded,
                            onSelectAudioSource = requestAudioSource,
                            onUpdateDsp = viewModel::updateDspSettings,
                            onTapTempo = { viewModel.tapTempo() },
                            onSetTempoMode = viewModel::setTempoMode,
                            onSetManualBpm = viewModel::setManualBpm,
                            onHalveBpm = viewModel::halveBpm,
                            onDoubleBpm = viewModel::doubleBpm,
                            onResetBpmMultiplier = viewModel::resetBpmMultiplier,
                            onSyncDownbeatPhase = viewModel::syncDownbeatPhase,
                            onUpdateTempoSyncConfig = viewModel::updateTempoSyncConfig,
                            onUpdateLfoChannel = viewModel::updateLfoChannel,
                            onAssignQuickLfoTarget = viewModel::assignQuickTargetToLfoBus,
                            onUpdateSynthFallback = viewModel::updateSynthFallbackConfig,
                            onUpdateDeviceTap = viewModel::updateDeviceTapConfig,
                            onSimulateDeviceTapNow = { viewModel.onPhysicalDeviceTapTriggered() },
                            onLoadPreset = viewModel::loadPreset,
                            onPrevPreset = viewModel::switchToPreviousPreset,
                            onNextPreset = viewModel::switchToNextPreset,
                            onRequestSaveNewPreset = { showSavePresetDialog = true },
                            onOverwritePreset = { id -> viewModel.overwritePreset(id) },
                            onDuplicatePreset = { id -> viewModel.duplicatePreset(id) },
                            onRenamePreset = { id, name, sub -> viewModel.renamePreset(id, name, sub) },
                            onDeletePreset = { id -> viewModel.deletePreset(id) },
                            onRevertPreset = { viewModel.revertToActivePreset() },
                            onToggleLayerExpanded = viewModel::toggleLayerExpanded,
                            onAddLayer = viewModel::addLayer,
                            onDuplicateLayer = viewModel::duplicateLayer,
                            onRemoveLayer = viewModel::removeLayer,
                            onMoveLayer = viewModel::moveLayer,
                            onUpdateLayer = viewModel::updateLayer,
                            onToggleTransformerExpanded = viewModel::toggleTransformerExpanded,
                            onAddOrEnableTransformer = viewModel::addOrEnableTransformer,
                            onRemoveTransformer = viewModel::removeTransformer,
                            onUpdateTransformer = viewModel::updateTransformer,
                            onSelectPalette = viewModel::selectPalette,
                            onUpdateColorEngine = viewModel::updateColorEngine,
                            onUpdateCustomColor = viewModel::updateCustomColor,
                            onAddCustomStop = viewModel::addCustomColorStop,
                            onRemoveCustomStop = viewModel::removeCustomColorStop,
                        )
                    }
                }
            }

            // Side Inspector Rail on wide/tablet/foldable screens
            if (isWideScreen && !uiState.zenMode) {
                StudioDock(
                    uiState = uiState,
                    audioFrame = audioFrame,
                    tempoSnapshot = tempoSnapshot,
                    liveValues = viewModel.liveEvaluatedValues,
                    hasTapHardwareSensor = viewModel.deviceTapDetector.hasHardwareSensor,
                    isWideScreen = true,
                    onSelectTab = viewModel::setActiveTab,
                    onToggleExpanded = viewModel::toggleDockExpanded,
                    onSelectAudioSource = requestAudioSource,
                    onUpdateDsp = viewModel::updateDspSettings,
                    onTapTempo = { viewModel.tapTempo() },
                    onSetTempoMode = viewModel::setTempoMode,
                    onSetManualBpm = viewModel::setManualBpm,
                    onHalveBpm = viewModel::halveBpm,
                    onDoubleBpm = viewModel::doubleBpm,
                    onResetBpmMultiplier = viewModel::resetBpmMultiplier,
                    onSyncDownbeatPhase = viewModel::syncDownbeatPhase,
                    onUpdateTempoSyncConfig = viewModel::updateTempoSyncConfig,
                    onUpdateLfoChannel = viewModel::updateLfoChannel,
                    onAssignQuickLfoTarget = viewModel::assignQuickTargetToLfoBus,
                    onUpdateSynthFallback = viewModel::updateSynthFallbackConfig,
                    onUpdateDeviceTap = viewModel::updateDeviceTapConfig,
                    onSimulateDeviceTapNow = { viewModel.onPhysicalDeviceTapTriggered() },
                    onLoadPreset = viewModel::loadPreset,
                    onPrevPreset = viewModel::switchToPreviousPreset,
                    onNextPreset = viewModel::switchToNextPreset,
                    onRequestSaveNewPreset = { showSavePresetDialog = true },
                    onOverwritePreset = { id -> viewModel.overwritePreset(id) },
                    onDuplicatePreset = { id -> viewModel.duplicatePreset(id) },
                    onRenamePreset = { id, name, sub -> viewModel.renamePreset(id, name, sub) },
                    onDeletePreset = { id -> viewModel.deletePreset(id) },
                    onRevertPreset = { viewModel.revertToActivePreset() },
                    onToggleLayerExpanded = viewModel::toggleLayerExpanded,
                    onAddLayer = viewModel::addLayer,
                    onDuplicateLayer = viewModel::duplicateLayer,
                    onRemoveLayer = viewModel::removeLayer,
                    onMoveLayer = viewModel::moveLayer,
                    onUpdateLayer = viewModel::updateLayer,
                    onToggleTransformerExpanded = viewModel::toggleTransformerExpanded,
                    onAddOrEnableTransformer = viewModel::addOrEnableTransformer,
                    onRemoveTransformer = viewModel::removeTransformer,
                    onUpdateTransformer = viewModel::updateTransformer,
                    onSelectPalette = viewModel::selectPalette,
                    onUpdateColorEngine = viewModel::updateColorEngine,
                    onUpdateCustomColor = viewModel::updateCustomColor,
                    onAddCustomStop = viewModel::addCustomColorStop,
                    onRemoveCustomStop = viewModel::removeCustomColorStop,
                )
            }
        }

        if (showSavePresetDialog) {
            val baseName = uiState.activePreset?.name?.let { "$it Mix" }
                ?: "Custom Mix ${uiState.userPresets.size + 1}"
            val summary = PresetCatalog.summarizeConfiguration(
                layers = uiState.layers,
                transformers = uiState.transformers,
                colorEngine = uiState.colorEngine,
            )
            SavePresetDialog(
                suggestedName = baseName,
                configurationSummary = summary,
                onDismiss = { showSavePresetDialog = false },
                onConfirmSave = { name, subtitle ->
                    viewModel.saveCurrentAsPreset(name, subtitle)
                    showSavePresetDialog = false
                },
            )
        }
    }
}

@Composable
private fun QuickPresetSwitcherBar(
    presetName: String,
    isModified: Boolean,
    layerCount: Int,
    txCount: Int,
    onPrevPreset: () -> Unit,
    onNextPreset: () -> Unit,
    onOpenPresets: () -> Unit,
    onQuickSave: () -> Unit,
) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(14.dp))
            .background(GlassSurface)
            .border(1.dp, LuminousBorder, RoundedCornerShape(14.dp))
            .padding(horizontal = 4.dp, vertical = 2.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        IconButton(
            onClick = onPrevPreset,
            modifier = Modifier
                .size(48.dp)
                .testTag("prev_preset_btn"),
        ) {
            Icon(
                imageVector = Icons.AutoMirrored.Filled.KeyboardArrowLeft,
                contentDescription = "Previous preset",
                tint = Color.White,
            )
        }

        Row(
            modifier = Modifier
                .weight(1f)
                .clip(RoundedCornerShape(10.dp))
                .clickable { onOpenPresets() }
                .padding(horizontal = 6.dp, vertical = 6.dp)
                .testTag("active_preset_pill"),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(6.dp),
        ) {
            Icon(
                imageVector = Icons.Filled.Tune,
                contentDescription = null,
                tint = ElectricCyan,
                modifier = Modifier.size(15.dp),
            )
            Text(
                text = presetName,
                style = MaterialTheme.typography.labelLarge,
                color = Color.White,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
                modifier = Modifier.weight(1f, fill = false),
            )
            if (isModified) {
                Box(
                    modifier = Modifier
                        .clip(RoundedCornerShape(4.dp))
                        .background(SolarAmber.copy(alpha = 0.22f))
                        .border(1.dp, SolarAmber, RoundedCornerShape(4.dp))
                        .padding(horizontal = 4.dp, vertical = 1.dp),
                ) {
                    Text(
                        text = "MOD",
                        style = MonoTelemetryStyle.copy(fontWeight = FontWeight.Bold),
                        color = SolarAmber,
                    )
                }
            } else {
                Text(
                    text = "${layerCount}L·${txCount}FX",
                    style = MonoTelemetryStyle,
                    color = MistSecondary,
                )
            }
        }

        IconButton(
            onClick = onNextPreset,
            modifier = Modifier
                .size(48.dp)
                .testTag("next_preset_btn"),
        ) {
            Icon(
                imageVector = Icons.AutoMirrored.Filled.KeyboardArrowRight,
                contentDescription = "Next preset",
                tint = Color.White,
            )
        }

        IconButton(
            onClick = onQuickSave,
            modifier = Modifier
                .size(48.dp)
                .testTag("quick_save_preset_btn"),
        ) {
            Icon(
                imageVector = Icons.Filled.BookmarkAdd,
                contentDescription = "Save current configuration as preset",
                tint = ElectricCyan,
            )
        }
    }
}

@Composable
private fun AudioConnectBanner(
    status: CaptureStatus,
    onChooseSource: (AudioSourceType) -> Unit,
) {
    val msg = when (status) {
        is CaptureStatus.Error -> status.message
        CaptureStatus.RequestingPermission -> "Grant audio capture permission to visualize live sound."
        else -> "Choose a live audio input to drive the overlapping visual engine:"
    }

    Column(
        modifier = Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(14.dp))
            .background(GlassSurface)
            .border(
                1.dp,
                if (status is CaptureStatus.Error) ErrorCoral.copy(alpha = 0.7f) else ElectricCyan.copy(alpha = 0.55f),
                RoundedCornerShape(14.dp),
            )
            .padding(12.dp),
        verticalArrangement = Arrangement.spacedBy(8.dp),
    ) {
        Text(
            text = msg,
            style = MaterialTheme.typography.bodyMedium,
            color = if (status is CaptureStatus.Error) ErrorCoral else Color.White,
        )
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.spacedBy(8.dp),
        ) {
            Button(
                onClick = { onChooseSource(AudioSourceType.OUTPUT_MIX) },
                colors = ButtonDefaults.buttonColors(containerColor = ElectricCyan, contentColor = VoidObsidian),
                modifier = Modifier
                    .weight(1f)
                    .testTag("connect_output_mix_btn"),
            ) {
                Icon(Icons.Filled.GraphicEq, contentDescription = null, modifier = Modifier.size(16.dp))
                Spacer(Modifier.width(4.dp))
                Text("Output Mix", maxLines = 1, style = MaterialTheme.typography.labelMedium)
            }
            OutlinedButton(
                onClick = { onChooseSource(AudioSourceType.APP_PLAYBACK) },
                modifier = Modifier
                    .weight(1f)
                    .testTag("connect_app_audio_btn"),
            ) {
                Icon(Icons.Filled.MusicNote, contentDescription = null, modifier = Modifier.size(16.dp), tint = AcidMagenta)
                Spacer(Modifier.width(4.dp))
                Text("App Audio", maxLines = 1, style = MaterialTheme.typography.labelMedium, color = Color.White)
            }
            OutlinedButton(
                onClick = { onChooseSource(AudioSourceType.MICROPHONE) },
                modifier = Modifier
                    .weight(1f)
                    .testTag("connect_mic_btn"),
            ) {
                Icon(Icons.Filled.Mic, contentDescription = null, modifier = Modifier.size(16.dp), tint = SolarAmber)
                Spacer(Modifier.width(4.dp))
                Text("Mic", maxLines = 1, style = MaterialTheme.typography.labelMedium, color = Color.White)
            }
        }
    }
}
