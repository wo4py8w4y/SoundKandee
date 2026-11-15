package com.example.ui.components

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.defaultMinSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.AutoFixHigh
import androidx.compose.material.icons.filled.ExpandLess
import androidx.compose.material.icons.filled.ExpandMore
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Slider
import androidx.compose.material3.SliderDefaults
import androidx.compose.material3.Switch
import androidx.compose.material3.SwitchDefaults
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.example.model.PresetTransitionConfig
import com.example.model.TransitionCurve
import com.example.ui.theme.AcidMagenta
import com.example.ui.theme.ElectricCyan
import com.example.ui.theme.ElevatedSurface
import com.example.ui.theme.HighestSurface
import com.example.ui.theme.LuminousBorder
import com.example.ui.theme.MistSecondary
import com.example.ui.theme.MonoTelemetryStyle
import com.example.ui.theme.SolarAmber

@Composable
fun PresetTransitionSettingsCard(
    config: PresetTransitionConfig,
    onUpdateConfig: ((PresetTransitionConfig) -> PresetTransitionConfig) -> Unit,
    modifier: Modifier = Modifier,
) {
    Column(
        modifier = modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(12.dp))
            .background(ElevatedSurface.copy(alpha = 0.8f))
            .border(
                1.dp,
                if (config.enabled) ElectricCyan.copy(alpha = 0.6f) else LuminousBorder,
                RoundedCornerShape(12.dp),
            )
            .padding(10.dp),
        verticalArrangement = Arrangement.spacedBy(8.dp),
    ) {
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Row(
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(6.dp),
                modifier = Modifier.weight(1f),
            ) {
                Icon(
                    imageVector = Icons.Filled.AutoFixHigh,
                    contentDescription = null,
                    tint = ElectricCyan,
                    modifier = Modifier.size(18.dp),
                )
                Column {
                    Text(
                        text = "Preset Crossfade Transitions",
                        style = MaterialTheme.typography.titleSmall,
                        color = Color.White,
                    )
                    Text(
                        text = if (!config.enabled || config.durationMs <= 0) {
                            "Instant switch (crossfade disabled)"
                        } else {
                            "${config.curve.label} • ${config.durationMs}ms"
                        },
                        style = MonoTelemetryStyle,
                        color = if (config.enabled && config.durationMs > 0) ElectricCyan else MistSecondary,
                    )
                }
            }
            Switch(
                checked = config.enabled,
                onCheckedChange = { on -> onUpdateConfig { it.copy(enabled = on) } },
                colors = SwitchDefaults.colors(checkedThumbColor = ElectricCyan),
                modifier = Modifier.testTag("preset_transition_switch"),
            )
        }

        if (config.enabled) {
            PresetTransitionVariationsBody(
                config = config,
                onUpdateConfig = onUpdateConfig,
                tagPrefix = "settings",
            )
        }
    }
}

@Composable
fun PresetsTabCrossfadeBar(
    config: PresetTransitionConfig,
    onUpdateConfig: ((PresetTransitionConfig) -> PresetTransitionConfig) -> Unit,
    modifier: Modifier = Modifier,
) {
    var expanded by remember { mutableStateOf(false) }

    Column(
        modifier = modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(12.dp))
            .background(ElevatedSurface.copy(alpha = 0.78f))
            .border(1.dp, LuminousBorder, RoundedCornerShape(12.dp))
            .padding(horizontal = 10.dp, vertical = 8.dp),
        verticalArrangement = Arrangement.spacedBy(6.dp),
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .defaultMinSize(minHeight = 48.dp)
                .clickable { expanded = !expanded }
                .testTag("presets_crossfade_header"),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Column(modifier = Modifier.weight(1f)) {
                Text(
                    text = "CROSSFADE TRANSITION",
                    style = MaterialTheme.typography.labelSmall,
                    color = MistSecondary,
                )
                Text(
                    text = if (!config.enabled || config.durationMs <= 0) {
                        "Instant • 0ms"
                    } else {
                        "${config.curve.label} • ${config.durationMs}ms • ${if (config.morphShadersAndPalettes) "FX Morph" else "Optical"}"
                    },
                    style = MonoTelemetryStyle.copy(fontWeight = FontWeight.Bold),
                    color = if (config.enabled && config.durationMs > 0) ElectricCyan else MistSecondary,
                )
            }
            Switch(
                checked = config.enabled,
                onCheckedChange = { on -> onUpdateConfig { it.copy(enabled = on) } },
                colors = SwitchDefaults.colors(checkedThumbColor = ElectricCyan),
                modifier = Modifier.testTag("presets_crossfade_switch"),
            )
            Icon(
                imageVector = if (expanded) Icons.Filled.ExpandLess else Icons.Filled.ExpandMore,
                contentDescription = if (expanded) "Hide transition options" else "Show transition options",
                tint = MistSecondary,
                modifier = Modifier.padding(start = 4.dp),
            )
        }

        AnimatedVisibility(visible = expanded && config.enabled) {
            PresetTransitionVariationsBody(
                config = config,
                onUpdateConfig = onUpdateConfig,
                tagPrefix = "presets",
            )
        }
    }
}

@Composable
private fun PresetTransitionVariationsBody(
    config: PresetTransitionConfig,
    onUpdateConfig: ((PresetTransitionConfig) -> PresetTransitionConfig) -> Unit,
    tagPrefix: String,
) {
    Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
        // Quick duration pills + fine slider
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .horizontalScroll(rememberScrollState()),
            horizontalArrangement = Arrangement.spacedBy(6.dp),
        ) {
            PresetTransitionConfig.QUICK_DURATIONS_MS.forEach { ms ->
                val selected = config.durationMs == ms
                val label = if (ms >= 1000) String.format("%.1fs", ms / 1000f) else "${ms}ms"
                Box(
                    contentAlignment = Alignment.Center,
                    modifier = Modifier
                        .defaultMinSize(minHeight = 36.dp)
                        .clip(RoundedCornerShape(8.dp))
                        .background(if (selected) ElectricCyan.copy(alpha = 0.22f) else HighestSurface)
                        .border(1.dp, if (selected) ElectricCyan else LuminousBorder, RoundedCornerShape(8.dp))
                        .clickable { onUpdateConfig { it.copy(durationMs = ms) } }
                        .padding(horizontal = 10.dp, vertical = 6.dp)
                        .testTag("${tagPrefix}_duration_$ms"),
                ) {
                    Text(
                        text = label,
                        style = MonoTelemetryStyle,
                        color = if (selected) ElectricCyan else MistSecondary,
                    )
                }
            }
        }

        Column {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
            ) {
                Text(
                    text = "Crossfade Duration",
                    style = MaterialTheme.typography.labelSmall,
                    color = MistSecondary,
                )
                Text(
                    text = if (config.durationMs <= 0) "0 ms (Instant)" else "${config.durationMs} ms",
                    style = MonoTelemetryStyle,
                    color = Color.White,
                )
            }
            Slider(
                value = config.durationMs.toFloat(),
                onValueChange = { v -> onUpdateConfig { it.copy(durationMs = v.toInt()) } },
                valueRange = PresetTransitionConfig.MIN_DURATION_MS.toFloat()..PresetTransitionConfig.MAX_DURATION_MS.toFloat(),
                colors = SliderDefaults.colors(
                    thumbColor = AcidMagenta,
                    activeTrackColor = AcidMagenta,
                    inactiveTrackColor = HighestSurface,
                ),
                modifier = Modifier.testTag("${tagPrefix}_preset_crossfade_slider"),
            )
        }

        // Crossfade curve variations
        Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
            Text(
                text = "BLEND CURVE",
                style = MaterialTheme.typography.labelSmall,
                color = MistSecondary,
            )
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .horizontalScroll(rememberScrollState()),
                horizontalArrangement = Arrangement.spacedBy(6.dp),
            ) {
                TransitionCurve.entries.forEach { curve ->
                    val selected = config.curve == curve
                    Box(
                        contentAlignment = Alignment.Center,
                        modifier = Modifier
                            .defaultMinSize(minHeight = 36.dp)
                            .clip(RoundedCornerShape(8.dp))
                            .background(if (selected) AcidMagenta.copy(alpha = 0.22f) else HighestSurface)
                            .border(1.dp, if (selected) AcidMagenta else LuminousBorder, RoundedCornerShape(8.dp))
                            .clickable { onUpdateConfig { it.copy(curve = curve) } }
                            .padding(horizontal = 10.dp, vertical = 6.dp)
                            .testTag("${tagPrefix}_curve_${curve.name}"),
                    ) {
                        Text(
                            text = curve.label,
                            style = MonoTelemetryStyle,
                            color = if (selected) Color.White else MistSecondary,
                        )
                    }
                }
            }
            Text(
                text = config.curve.description,
                style = MonoTelemetryStyle,
                color = SolarAmber,
            )
        }

        // Morph shaders/palettes + randomize crossfade variations
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Column(modifier = Modifier.weight(1f)) {
                Text(
                    text = "Morph Shaders & Palettes",
                    style = MaterialTheme.typography.labelMedium,
                    color = Color.White,
                )
                Text(
                    text = "Interpolate warps, trails & color stops continuously",
                    style = MonoTelemetryStyle,
                    color = MistSecondary,
                )
            }
            Switch(
                checked = config.morphShadersAndPalettes,
                onCheckedChange = { on -> onUpdateConfig { it.copy(morphShadersAndPalettes = on) } },
                colors = SwitchDefaults.colors(checkedThumbColor = ElectricCyan),
                modifier = Modifier.testTag("${tagPrefix}_morph_switch"),
            )
        }

        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Column(modifier = Modifier.weight(1f)) {
                Text(
                    text = "Crossfade Randomize & Tap",
                    style = MaterialTheme.typography.labelMedium,
                    color = Color.White,
                )
                Text(
                    text = "Smoothly blend Mutate, Randomize & Device Tap",
                    style = MonoTelemetryStyle,
                    color = MistSecondary,
                )
            }
            Switch(
                checked = config.crossfadeOnRandomize,
                onCheckedChange = { on -> onUpdateConfig { it.copy(crossfadeOnRandomize = on) } },
                colors = SwitchDefaults.colors(checkedThumbColor = AcidMagenta),
                modifier = Modifier.testTag("${tagPrefix}_randomize_fade_switch"),
            )
        }
    }
}
