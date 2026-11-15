package com.example.ui.components

import androidx.compose.animation.AnimatedVisibility
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
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Lock
import androidx.compose.material.icons.filled.LockOpen
import androidx.compose.material.icons.filled.Tune
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.RangeSlider
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
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.example.model.AudioFilterScope
import com.example.model.ModulationDriver
import com.example.model.ModulationEvaluator
import com.example.model.ResponseCurve
import com.example.model.ScopedParam
import com.example.ui.theme.AcidMagenta
import com.example.ui.theme.DimMuted
import com.example.ui.theme.ElectricCyan
import com.example.ui.theme.ElevatedSurface
import com.example.ui.theme.HighestSurface
import com.example.ui.theme.LuminousBorder
import com.example.ui.theme.MistSecondary
import com.example.ui.theme.MonoTelemetryStyle
import com.example.ui.theme.SolarAmber
import kotlin.math.abs

/**
 * Dual-thumb Scoped Parameter control letting users specify the exact [scopeMin..scopeMax]
 * operating range, view the live modulated value inside that window, and patch its
 * modulation driver, curve, invert, lock, attack/release smoothing, and LFO rate.
 */
@Composable
fun ScopedParamControl(
    param: ScopedParam,
    liveValueProvider: () -> Float?,
    onParamChange: (ScopedParam) -> Unit,
    modifier: Modifier = Modifier,
) {
    var patchExpanded by remember { mutableStateOf(false) }
    val liveVal = liveValueProvider() ?: param.midpoint

    Column(
        modifier = modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(12.dp))
            .background(ElevatedSurface.copy(alpha = 0.72f))
            .border(1.dp, LuminousBorder.copy(alpha = 0.7f), RoundedCornerShape(12.dp))
            .padding(horizontal = 12.dp, vertical = 8.dp),
    ) {
        Row(
            modifier = Modifier.fillMaxWidth(),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.SpaceBetween,
        ) {
            Column(modifier = Modifier.weight(1f)) {
                Text(
                    text = param.label,
                    style = MaterialTheme.typography.titleSmall,
                    color = MaterialTheme.colorScheme.onSurface,
                )
                Text(
                    text = "Scope ${formatVal(param.orderedMin, param.unit)} .. ${formatVal(param.orderedMax, param.unit)}  •  Now ${formatVal(liveVal, param.unit)}",
                    style = MonoTelemetryStyle,
                    color = ElectricCyan,
                )
            }

            Row(
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(4.dp),
            ) {
                Box(
                    modifier = Modifier
                        .clip(RoundedCornerShape(8.dp))
                        .background(if (param.driver == ModulationDriver.FIXED) HighestSurface else AcidMagenta.copy(alpha = 0.22f))
                        .border(
                            1.dp,
                            if (param.driver == ModulationDriver.FIXED) LuminousBorder else AcidMagenta.copy(alpha = 0.7f),
                            RoundedCornerShape(8.dp),
                        )
                        .clickable { patchExpanded = !patchExpanded }
                        .padding(horizontal = 8.dp, vertical = 5.dp)
                        .testTag("param_driver_${param.id}"),
                ) {
                    Text(
                        text = param.driver.shortLabel,
                        style = MonoTelemetryStyle.copy(fontWeight = FontWeight.Bold),
                        color = if (param.driver == ModulationDriver.FIXED) MistSecondary else AcidMagenta,
                    )
                }

                IconButton(
                    onClick = { onParamChange(param.copy(locked = !param.locked)) },
                    modifier = Modifier
                        .size(36.dp)
                        .testTag("param_lock_${param.id}"),
                ) {
                    Icon(
                        imageVector = if (param.locked) Icons.Filled.Lock else Icons.Filled.LockOpen,
                        contentDescription = if (param.locked) "Unlock parameter" else "Lock parameter",
                        tint = if (param.locked) SolarAmber else DimMuted,
                        modifier = Modifier.size(16.dp),
                    )
                }

                IconButton(
                    onClick = { patchExpanded = !patchExpanded },
                    modifier = Modifier
                        .size(36.dp)
                        .testTag("param_patch_${param.id}"),
                ) {
                    Icon(
                        imageVector = Icons.Filled.Tune,
                        contentDescription = "Configure modulation routing",
                        tint = if (patchExpanded) ElectricCyan else MistSecondary,
                        modifier = Modifier.size(16.dp),
                    )
                }
            }
        }

        // Live modulation visualizer bar inside the full hardMin..hardMax track
        Canvas(
            modifier = Modifier
                .fillMaxWidth()
                .height(8.dp)
                .padding(top = 4.dp),
        ) {
            val span = (param.hardMax - param.hardMin).coerceAtLeast(1e-5f)
            val normMin = ((param.orderedMin - param.hardMin) / span).coerceIn(0f, 1f)
            val normMax = ((param.orderedMax - param.hardMin) / span).coerceIn(0f, 1f)
            val normLive = ((liveVal - param.hardMin) / span).coerceIn(0f, 1f)

            val y = size.height * 0.5f
            drawLine(
                color = LuminousBorder,
                start = Offset(0f, y),
                end = Offset(size.width, y),
                strokeWidth = 3.dp.toPx(),
            )
            drawLine(
                color = ElectricCyan.copy(alpha = 0.5f),
                start = Offset(normMin * size.width, y),
                end = Offset(normMax * size.width, y),
                strokeWidth = 4.dp.toPx(),
            )
            drawCircle(
                color = AcidMagenta,
                radius = 4.dp.toPx(),
                center = Offset(normLive * size.width, y),
            )
        }

        RangeSlider(
            value = param.orderedMin..param.orderedMax,
            onValueChange = { range ->
                onParamChange(
                    param.copy(
                        scopeMin = range.start.coerceIn(param.hardMin, param.hardMax),
                        scopeMax = range.endInclusive.coerceIn(param.hardMin, param.hardMax),
                    )
                )
            },
            valueRange = param.hardMin..param.hardMax,
            colors = SliderDefaults.colors(
                thumbColor = ElectricCyan,
                activeTrackColor = ElectricCyan,
                inactiveTrackColor = HighestSurface,
            ),
            modifier = Modifier
                .fillMaxWidth()
                .testTag("param_range_${param.id}"),
        )

        AnimatedVisibility(visible = patchExpanded) {
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(top = 4.dp),
                verticalArrangement = Arrangement.spacedBy(8.dp),
            ) {
                Text(
                    text = "MODULATION DRIVER",
                    style = MaterialTheme.typography.labelSmall,
                    color = MistSecondary,
                )
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .horizontalScroll(rememberScrollState()),
                    horizontalArrangement = Arrangement.spacedBy(6.dp),
                ) {
                    ModulationDriver.entries.forEach { drv ->
                        val selected = param.driver == drv
                        Box(
                            modifier = Modifier
                                .clip(RoundedCornerShape(8.dp))
                                .background(if (selected) ElectricCyan.copy(alpha = 0.2f) else HighestSurface)
                                .border(
                                    1.dp,
                                    if (selected) ElectricCyan else LuminousBorder,
                                    RoundedCornerShape(8.dp),
                                )
                                .clickable { onParamChange(param.copy(driver = drv)) }
                                .padding(horizontal = 10.dp, vertical = 6.dp),
                        ) {
                            Text(
                                text = drv.shortLabel,
                                style = MonoTelemetryStyle,
                                color = if (selected) ElectricCyan else MistSecondary,
                            )
                        }
                    }
                }

                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    Row(
                        modifier = Modifier
                            .weight(1f)
                            .horizontalScroll(rememberScrollState()),
                        horizontalArrangement = Arrangement.spacedBy(6.dp),
                    ) {
                        ResponseCurve.entries.forEach { cv ->
                            val selected = param.curve == cv
                            Box(
                                modifier = Modifier
                                    .clip(RoundedCornerShape(8.dp))
                                    .background(if (selected) SolarAmber.copy(alpha = 0.2f) else HighestSurface)
                                    .border(
                                        1.dp,
                                        if (selected) SolarAmber else LuminousBorder,
                                        RoundedCornerShape(8.dp),
                                    )
                                    .clickable { onParamChange(param.copy(curve = cv)) }
                                    .padding(horizontal = 8.dp, vertical = 5.dp),
                            ) {
                                Text(
                                    text = cv.label,
                                    style = MonoTelemetryStyle,
                                    color = if (selected) SolarAmber else MistSecondary,
                                )
                            }
                        }
                    }

                    Spacer(Modifier.width(8.dp))
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Text("Inv", style = MonoTelemetryStyle, color = MistSecondary)
                        Spacer(Modifier.width(4.dp))
                        Switch(
                            checked = param.inverted,
                            onCheckedChange = { onParamChange(param.copy(inverted = it)) },
                            colors = SwitchDefaults.colors(checkedThumbColor = AcidMagenta),
                        )
                    }
                }

                if (param.driver == ModulationDriver.LFO_SINE ||
                    param.driver == ModulationDriver.LFO_TRIANGLE ||
                    param.driver == ModulationDriver.CHAOS_DRIFT
                ) {
                    CompactLabeledSlider(
                        label = "LFO Rate",
                        valueText = "${String.format("%.2f", param.lfoHz)} Hz",
                        value = param.lfoHz,
                        range = 0.05f..6.0f,
                        onValueChange = { onParamChange(param.copy(lfoHz = it)) },
                    )
                } else if (param.driver != ModulationDriver.FIXED) {
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.spacedBy(8.dp),
                    ) {
                        CompactLabeledSlider(
                            label = "Attack",
                            valueText = "${param.attackMs.toInt()}ms",
                            value = param.attackMs,
                            range = 5f..400f,
                            onValueChange = { onParamChange(param.copy(attackMs = it)) },
                            modifier = Modifier.weight(1f),
                        )
                        CompactLabeledSlider(
                            label = "Release",
                            valueText = "${param.releaseMs.toInt()}ms",
                            value = param.releaseMs,
                            range = 15f..1200f,
                            onValueChange = { onParamChange(param.copy(releaseMs = it)) },
                            modifier = Modifier.weight(1f),
                        )
                    }
                }
            }
        }
    }
}

/**
 * Per-layer / per-transformer Audio Frequency Filter Window (20Hz - 20kHz) + Amplitude Gate.
 */
@Composable
fun AudioFilterScopeControl(
    filter: AudioFilterScope,
    onFilterChange: (AudioFilterScope) -> Unit,
    modifier: Modifier = Modifier,
) {
    val lowNorm = ModulationEvaluator.hzToNorm(filter.safeLowHz)
    val highNorm = ModulationEvaluator.hzToNorm(filter.safeHighHz)

    Column(
        modifier = modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(12.dp))
            .background(ElevatedSurface.copy(alpha = 0.72f))
            .border(1.dp, LuminousBorder.copy(alpha = 0.7f), RoundedCornerShape(12.dp))
            .padding(horizontal = 12.dp, vertical = 8.dp),
    ) {
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Text(
                text = "Audio Frequency Filter Scope",
                style = MaterialTheme.typography.titleSmall,
                color = MaterialTheme.colorScheme.onSurface,
            )
            Text(
                text = "${ModulationEvaluator.formatHz(filter.safeLowHz)} – ${ModulationEvaluator.formatHz(filter.safeHighHz)}",
                style = MonoTelemetryStyle,
                color = SolarAmber,
            )
        }

        RangeSlider(
            value = lowNorm..highNorm,
            onValueChange = { r ->
                onFilterChange(
                    filter.copy(
                        lowCutHz = ModulationEvaluator.normToHz(r.start),
                        highCutHz = ModulationEvaluator.normToHz(r.endInclusive),
                    )
                )
            },
            valueRange = 0f..1f,
            colors = SliderDefaults.colors(
                thumbColor = SolarAmber,
                activeTrackColor = SolarAmber,
                inactiveTrackColor = HighestSurface,
            ),
            modifier = Modifier.fillMaxWidth(),
        )

        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.spacedBy(8.dp),
        ) {
            CompactLabeledSlider(
                label = "Gate",
                valueText = "${(filter.thresholdGate * 100).toInt()}%",
                value = filter.thresholdGate,
                range = 0f..0.7f,
                onValueChange = { onFilterChange(filter.copy(thresholdGate = it)) },
                modifier = Modifier.weight(1f),
            )
            CompactLabeledSlider(
                label = "Ceiling",
                valueText = "${(filter.ceilingClamp * 100).toInt()}%",
                value = filter.ceilingClamp,
                range = 0.2f..1f,
                onValueChange = { onFilterChange(filter.copy(ceilingClamp = it)) },
                modifier = Modifier.weight(1f),
            )
            CompactLabeledSlider(
                label = "Gain",
                valueText = "${String.format("%.1f", filter.gain)}x",
                value = filter.gain,
                range = 0.2f..3.0f,
                onValueChange = { onFilterChange(filter.copy(gain = it)) },
                modifier = Modifier.weight(1f),
            )
        }
    }
}

@Composable
fun CompactLabeledSlider(
    label: String,
    valueText: String,
    value: Float,
    range: ClosedFloatingPointRange<Float>,
    onValueChange: (Float) -> Unit,
    modifier: Modifier = Modifier,
) {
    Column(modifier = modifier) {
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween,
        ) {
            Text(text = label, style = MaterialTheme.typography.labelSmall, color = MistSecondary)
            Text(text = valueText, style = MonoTelemetryStyle, color = Color.White)
        }
        Slider(
            value = value.coerceIn(range.start, range.endInclusive),
            onValueChange = onValueChange,
            valueRange = range,
            colors = SliderDefaults.colors(
                thumbColor = AcidMagenta,
                activeTrackColor = AcidMagenta,
                inactiveTrackColor = HighestSurface,
            ),
        )
    }
}

private fun formatVal(v: Float, unit: String): String {
    val num = if (abs(v) >= 10f) String.format("%.0f", v) else String.format("%.2f", v)
    return if (unit.isEmpty()) num else "$num$unit"
}
