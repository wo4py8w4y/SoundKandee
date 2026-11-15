package com.example.ui.components

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.defaultMinSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.TouchApp
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Switch
import androidx.compose.material3.SwitchDefaults
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.example.audio.TempoSyncEngine
import com.example.model.BeatDivision
import com.example.model.ColorEngineConfig
import com.example.model.LfoChannelConfig
import com.example.model.LfoMath
import com.example.model.LfoWaveform
import com.example.model.ModulationDriver
import com.example.model.TempoSyncConfig
import com.example.model.TempoSyncMode
import com.example.model.TempoSyncSnapshot
import com.example.model.TransformerNode
import com.example.model.VisualLayer
import com.example.ui.QuickLfoTarget
import com.example.ui.theme.AcidMagenta
import com.example.ui.theme.DimMuted
import com.example.ui.theme.ElectricCyan
import com.example.ui.theme.ElevatedSurface
import com.example.ui.theme.HighestSurface
import com.example.ui.theme.LuminousBorder
import com.example.ui.theme.MistSecondary
import com.example.ui.theme.MonoTelemetryStyle
import com.example.ui.theme.SolarAmber
import com.example.ui.theme.VoidObsidian

@Composable
fun LfoAndTempoTabContent(
    lfoBank: List<LfoChannelConfig>,
    tempoSyncConfig: TempoSyncConfig,
    tempoSnapshot: TempoSyncSnapshot,
    layers: List<VisualLayer>,
    transformers: List<TransformerNode>,
    colorEngine: ColorEngineConfig,
    onTapTempo: () -> Unit,
    onSetTempoMode: (TempoSyncMode) -> Unit,
    onSetManualBpm: (Float) -> Unit,
    onHalveBpm: () -> Unit,
    onDoubleBpm: () -> Unit,
    onResetBpmMultiplier: () -> Unit,
    onSyncDownbeatPhase: () -> Unit,
    onUpdateTempoSyncConfig: ((TempoSyncConfig) -> TempoSyncConfig) -> Unit,
    onUpdateLfoChannel: (Int, (LfoChannelConfig) -> LfoChannelConfig) -> Unit,
    onAssignQuickTarget: (QuickLfoTarget, Int) -> Unit,
) {
    LazyColumn(
        verticalArrangement = Arrangement.spacedBy(10.dp),
        modifier = Modifier
            .fillMaxWidth()
            .testTag("lfo_tempo_list"),
    ) {
        item(key = "tempo_sync_card") {
            TempoSyncCard(
                config = tempoSyncConfig,
                snapshot = tempoSnapshot,
                onTapTempo = onTapTempo,
                onSetTempoMode = onSetTempoMode,
                onSetManualBpm = onSetManualBpm,
                onHalveBpm = onHalveBpm,
                onDoubleBpm = onDoubleBpm,
                onResetBpmMultiplier = onResetBpmMultiplier,
                onSyncDownbeatPhase = onSyncDownbeatPhase,
                onUpdateConfig = onUpdateTempoSyncConfig,
            )
        }

        itemsIndexed(lfoBank, key = { _, ch -> ch.id }) { idx, channel ->
            val accent = when (channel.id) {
                1 -> ElectricCyan
                2 -> AcidMagenta
                else -> SolarAmber
            }
            val busVal = tempoSnapshot.lfoBusValues.getOrElse(idx) { 0.5f }
            val busPhase = tempoSnapshot.lfoBusPhases.getOrElse(idx) { 0f }
            val busDriver = when (channel.id) {
                1 -> ModulationDriver.LFO_BUS_1
                2 -> ModulationDriver.LFO_BUS_2
                else -> ModulationDriver.LFO_BUS_3
            }
            val routedCount = countParamsUsingDriver(layers, transformers, colorEngine, busDriver)

            LfoChannelCard(
                channel = channel,
                accentColor = accent,
                liveValue = busVal,
                livePhase = busPhase,
                effectiveBpm = tempoSnapshot.effectiveBpm,
                tempoEnabled = tempoSyncConfig.enabled,
                routedCount = routedCount,
                layers = layers,
                transformers = transformers,
                colorEngine = colorEngine,
                onUpdate = { transform -> onUpdateLfoChannel(channel.id, transform) },
                onAssignQuickTarget = { target -> onAssignQuickTarget(target, channel.id) },
            )
        }
    }
}

@Composable
fun TempoSyncCard(
    config: TempoSyncConfig,
    snapshot: TempoSyncSnapshot,
    onTapTempo: () -> Unit,
    onSetTempoMode: (TempoSyncMode) -> Unit,
    onSetManualBpm: (Float) -> Unit,
    onHalveBpm: () -> Unit,
    onDoubleBpm: () -> Unit,
    onResetBpmMultiplier: () -> Unit,
    onSyncDownbeatPhase: () -> Unit,
    onUpdateConfig: ((TempoSyncConfig) -> TempoSyncConfig) -> Unit,
    modifier: Modifier = Modifier,
) {
    Column(
        modifier = modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(14.dp))
            .background(ElevatedSurface.copy(alpha = 0.85f))
            .border(1.dp, ElectricCyan.copy(alpha = 0.5f), RoundedCornerShape(14.dp))
            .padding(12.dp),
        verticalArrangement = Arrangement.spacedBy(8.dp),
    ) {
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Column(modifier = Modifier.weight(1f)) {
                Text(
                    text = "TEMPO-SYNC & BEAT CLOCK",
                    style = MaterialTheme.typography.labelSmall,
                    color = MistSecondary,
                )
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(8.dp),
                ) {
                    Text(
                        text = "${String.format("%.1f", snapshot.effectiveBpm)} BPM",
                        style = MaterialTheme.typography.titleMedium.copy(fontWeight = FontWeight.Bold),
                        color = if (config.enabled) ElectricCyan else MistSecondary,
                        modifier = Modifier.testTag("tempo_bpm_readout"),
                    )
                    if (config.bpmMultiplier != 1.0f) {
                        Text(
                            text = "${config.bpmMultiplier}x",
                            style = MonoTelemetryStyle,
                            color = SolarAmber,
                        )
                    }
                }
            }

            // 4-Beat Bar Phase LEDs
            Row(
                horizontalArrangement = Arrangement.spacedBy(5.dp),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                for (beat in 0 until 4) {
                    val active = config.enabled && snapshot.beatInBar == beat
                    val pulseGlow = if (active) (1f - snapshot.beatPhase).coerceIn(0.35f, 1f) else 0.2f
                    val dotColor = if (beat == 0) AcidMagenta else ElectricCyan
                    Box(
                        modifier = Modifier
                            .size(if (active) 12.dp else 9.dp)
                            .clip(CircleShape)
                            .background(dotColor.copy(alpha = pulseGlow))
                            .border(1.dp, dotColor.copy(alpha = 0.6f), CircleShape),
                    )
                }
                Spacer(Modifier.width(4.dp))
                Switch(
                    checked = config.enabled,
                    onCheckedChange = { on -> onUpdateConfig { it.copy(enabled = on) } },
                    colors = SwitchDefaults.colors(checkedThumbColor = ElectricCyan),
                    modifier = Modifier.testTag("tempo_sync_enabled_switch"),
                )
            }
        }

        // Mode selector: Auto Beat | Tap Tempo | Manual
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.spacedBy(6.dp),
        ) {
            TempoSyncMode.entries.forEach { mode ->
                val selected = config.mode == mode
                Box(
                    contentAlignment = Alignment.Center,
                    modifier = Modifier
                        .weight(1f)
                        .defaultMinSize(minHeight = 40.dp)
                        .clip(RoundedCornerShape(10.dp))
                        .background(if (selected) ElectricCyan.copy(alpha = 0.2f) else HighestSurface)
                        .border(1.dp, if (selected) ElectricCyan else LuminousBorder, RoundedCornerShape(10.dp))
                        .clickable { onSetTempoMode(mode) }
                        .padding(horizontal = 6.dp, vertical = 6.dp)
                        .testTag("tempo_mode_${mode.name}"),
                ) {
                    Text(
                        text = mode.label,
                        style = MonoTelemetryStyle.copy(fontWeight = if (selected) FontWeight.Bold else FontWeight.Normal),
                        color = if (selected) ElectricCyan else MistSecondary,
                        maxLines = 1,
                    )
                }
            }
        }

        // Tap Tempo & Multiplier Row
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.spacedBy(6.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Button(
                onClick = onTapTempo,
                colors = ButtonDefaults.buttonColors(
                    containerColor = AcidMagenta,
                    contentColor = Color.White,
                ),
                modifier = Modifier
                    .weight(1.4f)
                    .defaultMinSize(minHeight = 48.dp)
                    .testTag("tap_tempo_button"),
            ) {
                Icon(Icons.Filled.TouchApp, contentDescription = "Tap beat tempo", modifier = Modifier.size(16.dp))
                Spacer(Modifier.width(6.dp))
                Text("TAP BPM", style = MaterialTheme.typography.labelLarge)
            }

            TempoUtilityPill(label = "÷2", onClick = onHalveBpm, modifier = Modifier.testTag("bpm_halve_btn"))
            TempoUtilityPill(label = "1x", onClick = onResetBpmMultiplier, active = config.bpmMultiplier == 1.0f)
            TempoUtilityPill(label = "×2", onClick = onDoubleBpm, modifier = Modifier.testTag("bpm_double_btn"))
            TempoUtilityPill(label = "Sync 1", onClick = onSyncDownbeatPhase, accent = SolarAmber)
        }

        if (config.mode == TempoSyncMode.AUTO_BEAT) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Text(
                    text = "Auto Beat Confidence",
                    style = MaterialTheme.typography.labelSmall,
                    color = MistSecondary,
                )
                Text(
                    text = "${(snapshot.confidence * 100).toInt()}% • ${String.format("%.1f", snapshot.autoDetectedBpm)} BPM",
                    style = MonoTelemetryStyle,
                    color = if (snapshot.confidence > 0.4f) ElectricCyan else SolarAmber,
                )
            }
        }

        CompactLabeledSlider(
            label = if (config.mode == TempoSyncMode.MANUAL_BPM) "Manual Tempo BPM" else "Base / Fallback BPM",
            valueText = "${config.manualBpm.toInt()} BPM",
            value = config.manualBpm,
            range = TempoSyncEngine.MIN_BPM..TempoSyncEngine.MAX_BPM,
            onValueChange = onSetManualBpm,
        )

        // Locks: Visual Oscillators & Quantize Transformer Parameters
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Column(modifier = Modifier.weight(1f)) {
                Text("Lock Visual Oscillators to BPM", style = MaterialTheme.typography.labelMedium, color = Color.White)
                Text("LFOs, ripples & shader motion follow BPM", style = MonoTelemetryStyle, color = MistSecondary)
            }
            Switch(
                checked = config.lockVisualOscillators,
                onCheckedChange = { locked -> onUpdateConfig { it.copy(lockVisualOscillators = locked) } },
                colors = SwitchDefaults.colors(checkedThumbColor = ElectricCyan),
                modifier = Modifier.testTag("lock_oscillators_switch"),
            )
        }

        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Column(modifier = Modifier.weight(1f)) {
                Text("Quantize Transformer Changes", style = MaterialTheme.typography.labelMedium, color = Color.White)
                Text("Step-lock FX changes to beat grid", style = MonoTelemetryStyle, color = MistSecondary)
            }
            Switch(
                checked = config.quantizeTransformerChanges,
                onCheckedChange = { q -> onUpdateConfig { it.copy(quantizeTransformerChanges = q) } },
                colors = SwitchDefaults.colors(checkedThumbColor = AcidMagenta),
                modifier = Modifier.testTag("quantize_transformers_switch"),
            )
        }

        if (config.quantizeTransformerChanges) {
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .horizontalScroll(rememberScrollState()),
                horizontalArrangement = Arrangement.spacedBy(6.dp),
            ) {
                BeatDivision.entries.forEach { div ->
                    val selected = config.transformerQuantizeDivision == div
                    Box(
                        modifier = Modifier
                            .clip(RoundedCornerShape(8.dp))
                            .background(if (selected) AcidMagenta.copy(alpha = 0.24f) else HighestSurface)
                            .border(1.dp, if (selected) AcidMagenta else LuminousBorder, RoundedCornerShape(8.dp))
                            .clickable { onUpdateConfig { it.copy(transformerQuantizeDivision = div) } }
                            .padding(horizontal = 9.dp, vertical = 5.dp),
                    ) {
                        Text(div.label, style = MonoTelemetryStyle, color = if (selected) Color.White else MistSecondary)
                    }
                }
            }
        }
    }
}

@Composable
private fun TempoUtilityPill(
    label: String,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    active: Boolean = false,
    accent: Color = ElectricCyan,
) {
    Box(
        contentAlignment = Alignment.Center,
        modifier = modifier
            .defaultMinSize(minWidth = 42.dp, minHeight = 48.dp)
            .clip(RoundedCornerShape(10.dp))
            .background(if (active) accent.copy(alpha = 0.2f) else HighestSurface)
            .border(1.dp, if (active) accent else LuminousBorder, RoundedCornerShape(10.dp))
            .clickable { onClick() }
            .padding(horizontal = 8.dp),
    ) {
        Text(
            text = label,
            style = MonoTelemetryStyle.copy(fontWeight = FontWeight.Bold),
            color = if (active) accent else Color.White,
        )
    }
}

@Composable
private fun LfoChannelCard(
    channel: LfoChannelConfig,
    accentColor: Color,
    liveValue: Float,
    livePhase: Float,
    effectiveBpm: Float,
    tempoEnabled: Boolean,
    routedCount: Int,
    layers: List<VisualLayer>,
    transformers: List<TransformerNode>,
    colorEngine: ColorEngineConfig,
    onUpdate: ((LfoChannelConfig) -> LfoChannelConfig) -> Unit,
    onAssignQuickTarget: (QuickLfoTarget) -> Unit,
) {
    val effectiveHz = if (channel.tempoSynced && tempoEnabled) {
        (effectiveBpm / 60f) * channel.beatDivision.cyclesPerBeat
    } else {
        channel.rateHz
    }

    Column(
        modifier = Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(14.dp))
            .background(ElevatedSurface.copy(alpha = if (channel.enabled) 0.85f else 0.45f))
            .border(
                1.dp,
                if (channel.enabled) accentColor.copy(alpha = 0.55f) else LuminousBorder,
                RoundedCornerShape(14.dp),
            )
            .padding(10.dp),
        verticalArrangement = Arrangement.spacedBy(8.dp),
    ) {
        Row(
            modifier = Modifier.fillMaxWidth(),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.SpaceBetween,
        ) {
            Column(modifier = Modifier.weight(1f)) {
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(6.dp),
                ) {
                    Text(
                        text = channel.name,
                        style = MaterialTheme.typography.titleSmall,
                        color = if (channel.enabled) accentColor else MistSecondary,
                    )
                    Text(
                        text = "${channel.waveform.shortLabel} • ${
                            if (channel.tempoSynced && tempoEnabled) channel.beatDivision.label else "${String.format("%.2f", channel.rateHz)}Hz"
                        } (${String.format("%.2f", effectiveHz)}Hz)",
                        style = MonoTelemetryStyle,
                        color = MistSecondary,
                    )
                }
                Text(
                    text = if (routedCount > 0) "$routedCount param${if (routedCount == 1) "" else "s"} patched  •  Out ${(liveValue * 100).toInt()}%" else "Ready to patch (choose LFO${channel.id} on any parameter)",
                    style = MonoTelemetryStyle,
                    color = if (routedCount > 0) accentColor else DimMuted,
                )
            }

            Switch(
                checked = channel.enabled,
                onCheckedChange = { on -> onUpdate { it.copy(enabled = on) } },
                colors = SwitchDefaults.colors(checkedThumbColor = accentColor),
                modifier = Modifier.testTag("lfo_enable_${channel.id}"),
            )
        }

        // Live animated LFO Waveform Scope
        Canvas(
            modifier = Modifier
                .fillMaxWidth()
                .height(42.dp)
                .clip(RoundedCornerShape(8.dp))
                .background(VoidObsidian.copy(alpha = 0.65f))
                .border(1.dp, LuminousBorder.copy(alpha = 0.5f), RoundedCornerShape(8.dp)),
        ) {
            val midY = size.height * 0.5f
            drawLine(
                color = LuminousBorder.copy(alpha = 0.45f),
                start = Offset(0f, midY),
                end = Offset(size.width, midY),
                strokeWidth = 1.dp.toPx(),
            )
            val path = Path()
            val samples = 72
            for (i in 0..samples) {
                val t = i / samples.toFloat()
                val raw = LfoMath.evaluateNormalized(
                    waveform = channel.waveform,
                    totalCycles = t.toDouble() + (channel.phaseDeg / 360.0),
                    dutyCycle = channel.dutyCycle,
                    seedOffset = channel.id * 97,
                )
                val shaped = LfoMath.applyDepth(raw, channel.depth)
                val x = t * size.width
                val y = size.height * (0.88f - shaped * 0.76f)
                if (i == 0) path.moveTo(x, y) else path.lineTo(x, y)
            }
            drawPath(
                path = path,
                color = if (channel.enabled) accentColor else DimMuted,
                style = Stroke(width = 2.dp.toPx(), cap = StrokeCap.Round),
            )
            if (channel.enabled) {
                val dotX = (livePhase.coerceIn(0f, 1f)) * size.width
                val dotY = size.height * (0.88f - liveValue.coerceIn(0f, 1f) * 0.76f)
                drawCircle(
                    color = Color.White,
                    radius = 4.dp.toPx(),
                    center = Offset(dotX, dotY),
                )
            }
        }

        // Waveform Selector Row
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .horizontalScroll(rememberScrollState()),
            horizontalArrangement = Arrangement.spacedBy(6.dp),
        ) {
            LfoWaveform.entries.forEach { wf ->
                val selected = channel.waveform == wf
                Box(
                    modifier = Modifier
                        .clip(RoundedCornerShape(8.dp))
                        .background(if (selected) accentColor.copy(alpha = 0.22f) else HighestSurface)
                        .border(1.dp, if (selected) accentColor else LuminousBorder, RoundedCornerShape(8.dp))
                        .clickable { onUpdate { it.copy(waveform = wf) } }
                        .padding(horizontal = 9.dp, vertical = 5.dp)
                        .testTag("lfo_wave_${channel.id}_${wf.name}"),
                ) {
                    Text(
                        text = wf.shortLabel,
                        style = MonoTelemetryStyle,
                        color = if (selected) accentColor else MistSecondary,
                    )
                }
            }
        }

        // BPM Sync vs Free Hz + Retrigger toggles
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Row(
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(6.dp),
            ) {
                Box(
                    modifier = Modifier
                        .clip(RoundedCornerShape(8.dp))
                        .background(if (channel.tempoSynced) accentColor.copy(alpha = 0.2f) else HighestSurface)
                        .border(1.dp, if (channel.tempoSynced) accentColor else LuminousBorder, RoundedCornerShape(8.dp))
                        .clickable { onUpdate { it.copy(tempoSynced = !it.tempoSynced) } }
                        .padding(horizontal = 10.dp, vertical = 6.dp)
                        .testTag("lfo_sync_toggle_${channel.id}"),
                ) {
                    Text(
                        text = if (channel.tempoSynced) "BPM Sync" else "Free Hz",
                        style = MonoTelemetryStyle,
                        color = if (channel.tempoSynced) accentColor else MistSecondary,
                    )
                }

                Box(
                    modifier = Modifier
                        .clip(RoundedCornerShape(8.dp))
                        .background(if (channel.retriggerOnBeat) SolarAmber.copy(alpha = 0.2f) else HighestSurface)
                        .border(1.dp, if (channel.retriggerOnBeat) SolarAmber else LuminousBorder, RoundedCornerShape(8.dp))
                        .clickable { onUpdate { it.copy(retriggerOnBeat = !it.retriggerOnBeat) } }
                        .padding(horizontal = 10.dp, vertical = 6.dp),
                ) {
                    Text(
                        text = "Beat Retrig",
                        style = MonoTelemetryStyle,
                        color = if (channel.retriggerOnBeat) SolarAmber else MistSecondary,
                    )
                }
            }
        }

        if (channel.tempoSynced) {
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .horizontalScroll(rememberScrollState()),
                horizontalArrangement = Arrangement.spacedBy(6.dp),
            ) {
                BeatDivision.entries.forEach { div ->
                    val selected = channel.beatDivision == div
                    Box(
                        modifier = Modifier
                            .clip(RoundedCornerShape(8.dp))
                            .background(if (selected) accentColor.copy(alpha = 0.22f) else HighestSurface)
                            .border(1.dp, if (selected) accentColor else LuminousBorder, RoundedCornerShape(8.dp))
                            .clickable { onUpdate { it.copy(beatDivision = div) } }
                            .padding(horizontal = 8.dp, vertical = 5.dp)
                            .testTag("lfo_div_${channel.id}_${div.name}"),
                    ) {
                        Text(div.label, style = MonoTelemetryStyle, color = if (selected) accentColor else MistSecondary)
                    }
                }
            }
        } else {
            CompactLabeledSlider(
                label = "Free LFO Rate",
                valueText = "${String.format("%.2f", channel.rateHz)} Hz",
                value = channel.rateHz,
                range = 0.05f..12.0f,
                onValueChange = { hz -> onUpdate { it.copy(rateHz = hz) } },
            )
        }

        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.spacedBy(8.dp),
        ) {
            CompactLabeledSlider(
                label = "Depth",
                valueText = "${(channel.depth * 100).toInt()}%",
                value = channel.depth,
                range = 0f..1f,
                onValueChange = { d -> onUpdate { it.copy(depth = d) } },
                modifier = Modifier.weight(1f),
            )
            CompactLabeledSlider(
                label = "Phase",
                valueText = "${channel.phaseDeg.toInt()}°",
                value = channel.phaseDeg,
                range = 0f..360f,
                onValueChange = { p -> onUpdate { it.copy(phaseDeg = p) } },
                modifier = Modifier.weight(1f),
            )
        }

        if (channel.waveform == LfoWaveform.SQUARE || channel.waveform == LfoWaveform.TRIANGLE) {
            CompactLabeledSlider(
                label = if (channel.waveform == LfoWaveform.SQUARE) "Pulse Width" else "Triangle Skew",
                valueText = "${(channel.dutyCycle * 100).toInt()}%",
                value = channel.dutyCycle,
                range = 0.1f..0.9f,
                onValueChange = { duty -> onUpdate { it.copy(dutyCycle = duty) } },
            )
        }

        // Quick-Patch Targets
        val busDriver = when (channel.id) {
            1 -> ModulationDriver.LFO_BUS_1
            2 -> ModulationDriver.LFO_BUS_2
            else -> ModulationDriver.LFO_BUS_3
        }
        Text("QUICK PATCH TO LFO ${channel.id}", style = MaterialTheme.typography.labelSmall, color = MistSecondary)
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .horizontalScroll(rememberScrollState()),
            horizontalArrangement = Arrangement.spacedBy(6.dp),
        ) {
            QuickLfoTarget.entries.forEach { target ->
                val patched = isQuickTargetPatched(layers, transformers, colorEngine, target, busDriver)
                Box(
                    modifier = Modifier
                        .clip(RoundedCornerShape(8.dp))
                        .background(if (patched) accentColor.copy(alpha = 0.22f) else HighestSurface)
                        .border(1.dp, if (patched) accentColor else LuminousBorder, RoundedCornerShape(8.dp))
                        .clickable { onAssignQuickTarget(target) }
                        .padding(horizontal = 8.dp, vertical = 5.dp)
                        .testTag("lfo_${channel.id}_patch_${target.name}"),
                ) {
                    Text(
                        text = if (patched) "✓ ${target.label}" else target.label,
                        style = MonoTelemetryStyle,
                        color = if (patched) accentColor else MistSecondary,
                    )
                }
            }
        }
    }
}

private fun isQuickTargetPatched(
    layers: List<VisualLayer>,
    transformers: List<TransformerNode>,
    colorEngine: ColorEngineConfig,
    target: QuickLfoTarget,
    driver: ModulationDriver,
): Boolean = when (target) {
    QuickLfoTarget.FIRST_LAYER_SPIN -> layers.firstOrNull()?.rotationSpeed?.driver == driver
    QuickLfoTarget.FIRST_LAYER_SCALE -> layers.firstOrNull()?.scale?.driver == driver
    QuickLfoTarget.FIRST_TX_AMOUNT -> transformers.firstOrNull()?.amount?.driver == driver
    QuickLfoTarget.FIRST_TX_FREQ -> transformers.firstOrNull()?.frequency?.driver == driver
    QuickLfoTarget.HUE_SHIFT -> colorEngine.hueShift.driver == driver
}

private fun countParamsUsingDriver(
    layers: List<VisualLayer>,
    transformers: List<TransformerNode>,
    colorEngine: ColorEngineConfig,
    driver: ModulationDriver,
): Int {
    var count = 0
    layers.forEach { l ->
        listOf(l.opacity, l.scale, l.rotationSpeed, l.offsetX, l.offsetY, l.primaryParam, l.secondaryParam, l.tertiaryParam)
            .forEach { if (it.driver == driver) count++ }
    }
    transformers.forEach { t ->
        listOf(t.amount, t.frequency, t.phaseOrDrift).forEach { if (it.driver == driver) count++ }
    }
    listOf(colorEngine.hueShift, colorEngine.hueDriftSpeed, colorEngine.saturation, colorEngine.contrast, colorEngine.beatFlash)
        .forEach { if (it.driver == driver) count++ }
    return count
}
