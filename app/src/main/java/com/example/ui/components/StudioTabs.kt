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
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.ArrowDownward
import androidx.compose.material.icons.filled.ArrowUpward
import androidx.compose.material.icons.filled.ContentCopy
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.ExpandLess
import androidx.compose.material.icons.filled.ExpandMore
import androidx.compose.material.icons.filled.Lock
import androidx.compose.material.icons.filled.LockOpen
import androidx.compose.material.icons.filled.Visibility
import androidx.compose.material.icons.filled.VisibilityOff
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Switch
import androidx.compose.material3.SwitchDefaults
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.toArgb
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.foundation.layout.defaultMinSize
import androidx.compose.material.icons.filled.Sensors
import androidx.compose.material.icons.filled.Waves
import androidx.compose.material3.OutlinedButton
import com.example.audio.AudioFrame
import com.example.audio.DeviceTapAction
import com.example.audio.DeviceTapConfig
import com.example.audio.SynthFallbackConfig
import com.example.audio.SynthMusicStyle
import com.example.model.AudioSourceType
import com.example.model.ColorEngineConfig
import com.example.model.DspSettings
import com.example.model.FftWindowType
import com.example.model.LayerBlendMode
import com.example.model.ModulationEvaluator
import com.example.model.PresetCatalog
import com.example.model.TransformerNode
import com.example.model.TransformerType
import com.example.model.VisualLayer
import com.example.model.VisualStyleType
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
fun LayersTabContent(
    layers: List<VisualLayer>,
    expandedLayerId: String?,
    liveValues: Map<String, Float>,
    onToggleExpanded: (String) -> Unit,
    onAddLayer: (VisualStyleType) -> Unit,
    onDuplicateLayer: (String) -> Unit,
    onRemoveLayer: (String) -> Unit,
    onMoveLayer: (String, Int) -> Unit,
    onUpdateLayer: (String, (VisualLayer) -> VisualLayer) -> Unit,
) {
    LazyColumn(
        verticalArrangement = Arrangement.spacedBy(10.dp),
        modifier = Modifier.fillMaxWidth(),
    ) {
        item(key = "add_layer_strip") {
            Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
                Text(
                    text = "ADD OVERLAPPING VISUAL STYLE",
                    style = MaterialTheme.typography.labelSmall,
                    color = MistSecondary,
                )
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .horizontalScroll(rememberScrollState()),
                    horizontalArrangement = Arrangement.spacedBy(8.dp),
                ) {
                    VisualStyleType.entries.forEach { style ->
                        Row(
                            verticalAlignment = Alignment.CenterVertically,
                            modifier = Modifier
                                .clip(RoundedCornerShape(10.dp))
                                .background(ElevatedSurface)
                                .border(1.dp, ElectricCyan.copy(alpha = 0.45f), RoundedCornerShape(10.dp))
                                .clickable { onAddLayer(style) }
                                .padding(horizontal = 10.dp, vertical = 8.dp)
                                .testTag("add_layer_${style.name}"),
                        ) {
                            Icon(
                                imageVector = Icons.Filled.Add,
                                contentDescription = "Add ${style.displayName}",
                                tint = ElectricCyan,
                                modifier = Modifier.size(15.dp),
                            )
                            Spacer(Modifier.width(4.dp))
                            Text(
                                text = style.displayName,
                                style = MaterialTheme.typography.labelMedium,
                                color = Color.White,
                            )
                        }
                    }
                }
            }
        }

        items(layers, key = { it.id }) { layer ->
            val isExpanded = layer.id == expandedLayerId
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .clip(RoundedCornerShape(14.dp))
                    .background(ElevatedSurface.copy(alpha = if (layer.enabled) 0.85f else 0.45f))
                    .border(
                        1.dp,
                        if (isExpanded) ElectricCyan.copy(alpha = 0.65f) else LuminousBorder,
                        RoundedCornerShape(14.dp),
                    )
                    .padding(10.dp),
            ) {
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .clickable { onToggleExpanded(layer.id) },
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    IconButton(
                        onClick = { onUpdateLayer(layer.id) { it.copy(enabled = !it.enabled) } },
                        modifier = Modifier.size(36.dp),
                    ) {
                        Icon(
                            imageVector = if (layer.enabled) Icons.Filled.Visibility else Icons.Filled.VisibilityOff,
                            contentDescription = "Toggle layer visibility",
                            tint = if (layer.enabled) ElectricCyan else DimMuted,
                            modifier = Modifier.size(18.dp),
                        )
                    }

                    Column(modifier = Modifier.weight(1f)) {
                        Text(
                            text = layer.styleType.displayName,
                            style = MaterialTheme.typography.titleSmall,
                            color = if (layer.enabled) Color.White else MistSecondary,
                        )
                        Text(
                            text = "${layer.blendMode.label} • ${ModulationEvaluator.formatHz(layer.audioFilter.safeLowHz)}–${ModulationEvaluator.formatHz(layer.audioFilter.safeHighHz)}",
                            style = MonoTelemetryStyle,
                            color = MistSecondary,
                        )
                    }

                    // Solo button
                    Box(
                        modifier = Modifier
                            .clip(RoundedCornerShape(6.dp))
                            .background(if (layer.solo) SolarAmber else HighestSurface)
                            .clickable { onUpdateLayer(layer.id) { it.copy(solo = !it.solo) } }
                            .padding(horizontal = 7.dp, vertical = 4.dp),
                    ) {
                        Text(
                            text = "S",
                            style = MonoTelemetryStyle.copy(fontWeight = FontWeight.Bold),
                            color = if (layer.solo) VoidObsidian else MistSecondary,
                        )
                    }

                    IconButton(
                        onClick = { onUpdateLayer(layer.id) { it.copy(locked = !it.locked) } },
                        modifier = Modifier.size(34.dp),
                    ) {
                        Icon(
                            imageVector = if (layer.locked) Icons.Filled.Lock else Icons.Filled.LockOpen,
                            contentDescription = "Lock layer",
                            tint = if (layer.locked) SolarAmber else DimMuted,
                            modifier = Modifier.size(16.dp),
                        )
                    }

                    Icon(
                        imageVector = if (isExpanded) Icons.Filled.ExpandLess else Icons.Filled.ExpandMore,
                        contentDescription = "Expand layer controls",
                        tint = MistSecondary,
                    )
                }

                AnimatedVisibility(visible = isExpanded) {
                    Column(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(top = 8.dp),
                        verticalArrangement = Arrangement.spacedBy(8.dp),
                    ) {
                        // Layer stack management actions
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.spacedBy(6.dp),
                            verticalAlignment = Alignment.CenterVertically,
                        ) {
                            IconButton(
                                onClick = { onMoveLayer(layer.id, -1) },
                                modifier = Modifier
                                    .size(36.dp)
                                    .clip(RoundedCornerShape(8.dp))
                                    .background(HighestSurface),
                            ) {
                                Icon(Icons.Filled.ArrowUpward, contentDescription = "Move layer up", tint = Color.White, modifier = Modifier.size(16.dp))
                            }
                            IconButton(
                                onClick = { onMoveLayer(layer.id, 1) },
                                modifier = Modifier
                                    .size(36.dp)
                                    .clip(RoundedCornerShape(8.dp))
                                    .background(HighestSurface),
                            ) {
                                Icon(Icons.Filled.ArrowDownward, contentDescription = "Move layer down", tint = Color.White, modifier = Modifier.size(16.dp))
                            }
                            IconButton(
                                onClick = { onDuplicateLayer(layer.id) },
                                modifier = Modifier
                                    .size(36.dp)
                                    .clip(RoundedCornerShape(8.dp))
                                    .background(HighestSurface),
                            ) {
                                Icon(Icons.Filled.ContentCopy, contentDescription = "Duplicate layer", tint = ElectricCyan, modifier = Modifier.size(16.dp))
                            }
                            if (layers.size > 1) {
                                IconButton(
                                    onClick = { onRemoveLayer(layer.id) },
                                    modifier = Modifier
                                        .size(36.dp)
                                        .clip(RoundedCornerShape(8.dp))
                                        .background(HighestSurface),
                                ) {
                                    Icon(Icons.Filled.Delete, contentDescription = "Remove layer", tint = AcidMagenta, modifier = Modifier.size(16.dp))
                                }
                            }
                        }

                        // Blend Mode Selector
                        Text("BLEND MODE", style = MaterialTheme.typography.labelSmall, color = MistSecondary)
                        Row(
                            modifier = Modifier
                                .fillMaxWidth()
                                .horizontalScroll(rememberScrollState()),
                            horizontalArrangement = Arrangement.spacedBy(6.dp),
                        ) {
                            LayerBlendMode.entries.forEach { mode ->
                                val selected = layer.blendMode == mode
                                Box(
                                    modifier = Modifier
                                        .clip(RoundedCornerShape(8.dp))
                                        .background(if (selected) AcidMagenta.copy(alpha = 0.25f) else HighestSurface)
                                        .border(1.dp, if (selected) AcidMagenta else LuminousBorder, RoundedCornerShape(8.dp))
                                        .clickable { onUpdateLayer(layer.id) { it.copy(blendMode = mode) } }
                                        .padding(horizontal = 10.dp, vertical = 6.dp),
                                ) {
                                    Text(
                                        text = mode.label,
                                        style = MonoTelemetryStyle,
                                        color = if (selected) Color.White else MistSecondary,
                                    )
                                }
                            }
                        }

                        AudioFilterScopeControl(
                            filter = layer.audioFilter,
                            onFilterChange = { f -> onUpdateLayer(layer.id) { it.copy(audioFilter = f) } },
                        )

                        ScopedParamControl(
                            param = layer.opacity,
                            liveValueProvider = { liveValues[layer.opacity.id] },
                            onParamChange = { p -> onUpdateLayer(layer.id) { it.copy(opacity = p) } },
                        )
                        ScopedParamControl(
                            param = layer.primaryParam,
                            liveValueProvider = { liveValues[layer.primaryParam.id] },
                            onParamChange = { p -> onUpdateLayer(layer.id) { it.copy(primaryParam = p) } },
                        )
                        ScopedParamControl(
                            param = layer.secondaryParam,
                            liveValueProvider = { liveValues[layer.secondaryParam.id] },
                            onParamChange = { p -> onUpdateLayer(layer.id) { it.copy(secondaryParam = p) } },
                        )
                        ScopedParamControl(
                            param = layer.tertiaryParam,
                            liveValueProvider = { liveValues[layer.tertiaryParam.id] },
                            onParamChange = { p -> onUpdateLayer(layer.id) { it.copy(tertiaryParam = p) } },
                        )
                        ScopedParamControl(
                            param = layer.scale,
                            liveValueProvider = { liveValues[layer.scale.id] },
                            onParamChange = { p -> onUpdateLayer(layer.id) { it.copy(scale = p) } },
                        )
                        ScopedParamControl(
                            param = layer.rotationSpeed,
                            liveValueProvider = { liveValues[layer.rotationSpeed.id] },
                            onParamChange = { p -> onUpdateLayer(layer.id) { it.copy(rotationSpeed = p) } },
                        )
                        ScopedParamControl(
                            param = layer.offsetX,
                            liveValueProvider = { liveValues[layer.offsetX.id] },
                            onParamChange = { p -> onUpdateLayer(layer.id) { it.copy(offsetX = p) } },
                        )
                        ScopedParamControl(
                            param = layer.offsetY,
                            liveValueProvider = { liveValues[layer.offsetY.id] },
                            onParamChange = { p -> onUpdateLayer(layer.id) { it.copy(offsetY = p) } },
                        )
                    }
                }
            }
        }
    }
}

@Composable
fun TransformersTabContent(
    transformers: List<TransformerNode>,
    expandedTransformerId: String?,
    liveValues: Map<String, Float>,
    onToggleExpanded: (String) -> Unit,
    onAddOrEnable: (TransformerType) -> Unit,
    onRemove: (String) -> Unit,
    onUpdateTransformer: (String, (TransformerNode) -> TransformerNode) -> Unit,
) {
    LazyColumn(
        verticalArrangement = Arrangement.spacedBy(10.dp),
        modifier = Modifier.fillMaxWidth(),
    ) {
        item(key = "add_tx_strip") {
            Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
                Text(
                    text = "STACK WAVE & SPATIAL TRANSFORMERS",
                    style = MaterialTheme.typography.labelSmall,
                    color = MistSecondary,
                )
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .horizontalScroll(rememberScrollState()),
                    horizontalArrangement = Arrangement.spacedBy(8.dp),
                ) {
                    TransformerType.entries.forEach { type ->
                        val active = transformers.any { it.type == type && it.enabled }
                        Row(
                            verticalAlignment = Alignment.CenterVertically,
                            modifier = Modifier
                                .clip(RoundedCornerShape(10.dp))
                                .background(if (active) AcidMagenta.copy(alpha = 0.2f) else ElevatedSurface)
                                .border(
                                    1.dp,
                                    if (active) AcidMagenta else LuminousBorder,
                                    RoundedCornerShape(10.dp),
                                )
                                .clickable { onAddOrEnable(type) }
                                .padding(horizontal = 10.dp, vertical = 8.dp)
                                .testTag("add_tx_${type.name}"),
                        ) {
                            Icon(
                                imageVector = Icons.Filled.Add,
                                contentDescription = "Add ${type.displayName}",
                                tint = if (active) AcidMagenta else ElectricCyan,
                                modifier = Modifier.size(15.dp),
                            )
                            Spacer(Modifier.width(4.dp))
                            Text(
                                text = type.displayName,
                                style = MaterialTheme.typography.labelMedium,
                                color = Color.White,
                            )
                        }
                    }
                }
            }
        }

        items(transformers, key = { it.id }) { node ->
            val isExpanded = node.id == expandedTransformerId
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .clip(RoundedCornerShape(14.dp))
                    .background(ElevatedSurface.copy(alpha = if (node.enabled) 0.85f else 0.45f))
                    .border(
                        1.dp,
                        if (isExpanded) AcidMagenta.copy(alpha = 0.7f) else LuminousBorder,
                        RoundedCornerShape(14.dp),
                    )
                    .padding(10.dp),
            ) {
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .clickable { onToggleExpanded(node.id) },
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    Switch(
                        checked = node.enabled,
                        onCheckedChange = { checked -> onUpdateTransformer(node.id) { it.copy(enabled = checked) } },
                        colors = SwitchDefaults.colors(checkedThumbColor = AcidMagenta),
                    )
                    Spacer(Modifier.width(8.dp))
                    Column(modifier = Modifier.weight(1f)) {
                        Text(
                            text = node.type.displayName,
                            style = MaterialTheme.typography.titleSmall,
                            color = if (node.enabled) Color.White else MistSecondary,
                        )
                        Text(
                            text = node.type.description,
                            style = MaterialTheme.typography.bodyMedium,
                            color = MistSecondary,
                        )
                    }
                    IconButton(
                        onClick = { onUpdateTransformer(node.id) { it.copy(locked = !it.locked) } },
                        modifier = Modifier.size(34.dp),
                    ) {
                        Icon(
                            imageVector = if (node.locked) Icons.Filled.Lock else Icons.Filled.LockOpen,
                            contentDescription = "Lock transformer",
                            tint = if (node.locked) SolarAmber else DimMuted,
                            modifier = Modifier.size(16.dp),
                        )
                    }
                    IconButton(
                        onClick = { onRemove(node.id) },
                        modifier = Modifier.size(34.dp),
                    ) {
                        Icon(
                            imageVector = Icons.Filled.Delete,
                            contentDescription = "Remove transformer",
                            tint = DimMuted,
                            modifier = Modifier.size(16.dp),
                        )
                    }
                }

                AnimatedVisibility(visible = isExpanded) {
                    Column(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(top = 8.dp),
                        verticalArrangement = Arrangement.spacedBy(8.dp),
                    ) {
                        AudioFilterScopeControl(
                            filter = node.audioFilter,
                            onFilterChange = { f -> onUpdateTransformer(node.id) { it.copy(audioFilter = f) } },
                        )
                        ScopedParamControl(
                            param = node.amount,
                            liveValueProvider = { liveValues[node.amount.id] },
                            onParamChange = { p -> onUpdateTransformer(node.id) { it.copy(amount = p) } },
                        )
                        ScopedParamControl(
                            param = node.frequency,
                            liveValueProvider = { liveValues[node.frequency.id] },
                            onParamChange = { p -> onUpdateTransformer(node.id) { it.copy(frequency = p) } },
                        )
                        ScopedParamControl(
                            param = node.phaseOrDrift,
                            liveValueProvider = { liveValues[node.phaseOrDrift.id] },
                            onParamChange = { p -> onUpdateTransformer(node.id) { it.copy(phaseOrDrift = p) } },
                        )
                    }
                }
            }
        }
    }
}

@Composable
fun PalettesTabContent(
    colorEngine: ColorEngineConfig,
    liveValues: Map<String, Float>,
    onSelectPalette: (String) -> Unit,
    onUpdateColorEngine: ((ColorEngineConfig) -> ColorEngineConfig) -> Unit,
    onUpdateCustomColor: (Int, Long) -> Unit,
    onAddCustomStop: (Long) -> Unit,
    onRemoveCustomStop: (Int) -> Unit,
) {
    var editingStopIndex by remember { mutableIntStateOf(0) }
    val safeIndex = editingStopIndex.coerceIn(0, (colorEngine.customColors.size - 1).coerceAtLeast(0))

    LazyColumn(
        verticalArrangement = Arrangement.spacedBy(10.dp),
        modifier = Modifier.fillMaxWidth(),
    ) {
        item(key = "palette_header") {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Text("CURATED & CUSTOM PALETTES", style = MaterialTheme.typography.labelSmall, color = MistSecondary)
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    modifier = Modifier
                        .clip(RoundedCornerShape(8.dp))
                        .background(ElevatedSurface)
                        .clickable { onUpdateColorEngine { it.copy(locked = !it.locked) } }
                        .padding(horizontal = 8.dp, vertical = 4.dp),
                ) {
                    Icon(
                        imageVector = if (colorEngine.locked) Icons.Filled.Lock else Icons.Filled.LockOpen,
                        contentDescription = "Lock palette",
                        tint = if (colorEngine.locked) SolarAmber else DimMuted,
                        modifier = Modifier.size(14.dp),
                    )
                    Spacer(Modifier.width(4.dp))
                    Text(
                        text = if (colorEngine.locked) "Locked" else "Unlocked",
                        style = MonoTelemetryStyle,
                        color = if (colorEngine.locked) SolarAmber else MistSecondary,
                    )
                }
            }
        }

        item(key = "palette_grid") {
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .horizontalScroll(rememberScrollState()),
                horizontalArrangement = Arrangement.spacedBy(8.dp),
            ) {
                PresetCatalog.palettes.forEach { pal ->
                    val selected = !colorEngine.useCustomPalette && colorEngine.paletteId == pal.id
                    Column(
                        modifier = Modifier
                            .width(126.dp)
                            .clip(RoundedCornerShape(12.dp))
                            .background(ElevatedSurface)
                            .border(
                                1.5.dp,
                                if (selected) ElectricCyan else LuminousBorder,
                                RoundedCornerShape(12.dp),
                            )
                            .clickable { onSelectPalette(pal.id) }
                            .padding(8.dp)
                            .testTag("palette_${pal.id}"),
                        verticalArrangement = Arrangement.spacedBy(6.dp),
                    ) {
                        Box(
                            modifier = Modifier
                                .fillMaxWidth()
                                .height(18.dp)
                                .clip(RoundedCornerShape(6.dp))
                                .background(
                                    Brush.horizontalGradient(pal.colors.map { Color(it.toInt()) })
                                ),
                        )
                        Text(
                            text = pal.name,
                            style = MaterialTheme.typography.labelMedium,
                            color = if (selected) ElectricCyan else Color.White,
                            maxLines = 1,
                        )
                    }
                }
            }
        }

        item(key = "custom_gradient_builder") {
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .clip(RoundedCornerShape(12.dp))
                    .background(ElevatedSurface.copy(alpha = 0.72f))
                    .border(
                        1.dp,
                        if (colorEngine.useCustomPalette) AcidMagenta else LuminousBorder,
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
                    Text("Custom Gradient Stops", style = MaterialTheme.typography.titleSmall, color = Color.White)
                    Switch(
                        checked = colorEngine.useCustomPalette,
                        onCheckedChange = { checked -> onUpdateColorEngine { it.copy(useCustomPalette = checked) } },
                        colors = SwitchDefaults.colors(checkedThumbColor = AcidMagenta),
                    )
                }

                Box(
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(16.dp)
                        .clip(RoundedCornerShape(8.dp))
                        .background(Brush.horizontalGradient(colorEngine.customColors.map { Color(it.toInt()) })),
                )

                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(8.dp),
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    colorEngine.customColors.forEachIndexed { idx, argb ->
                        val isStopSelected = idx == safeIndex
                        Box(
                            modifier = Modifier
                                .size(32.dp)
                                .clip(CircleShape)
                                .background(Color(argb.toInt()))
                                .border(
                                    2.dp,
                                    if (isStopSelected) Color.White else LuminousBorder,
                                    CircleShape,
                                )
                                .clickable {
                                    editingStopIndex = idx
                                    onUpdateColorEngine { it.copy(useCustomPalette = true) }
                                },
                        )
                    }
                    if (colorEngine.customColors.size < 5) {
                        IconButton(
                            onClick = { onAddCustomStop(0xFF00F5D4) },
                            modifier = Modifier
                                .size(32.dp)
                                .clip(CircleShape)
                                .background(HighestSurface),
                        ) {
                            Icon(Icons.Filled.Add, contentDescription = "Add color stop", tint = ElectricCyan, modifier = Modifier.size(16.dp))
                        }
                    }
                    if (colorEngine.customColors.size > 2) {
                        IconButton(
                            onClick = { onRemoveCustomStop(safeIndex) },
                            modifier = Modifier
                                .size(32.dp)
                                .clip(CircleShape)
                                .background(HighestSurface),
                        ) {
                            Icon(Icons.Filled.Delete, contentDescription = "Remove selected color stop", tint = AcidMagenta, modifier = Modifier.size(16.dp))
                        }
                    }
                }

                val activeColor = Color(colorEngine.customColors.getOrElse(safeIndex) { 0xFF00F5D4 }.toInt())
                val hsv = FloatArray(3)
                android.graphics.Color.colorToHSV(activeColor.toArgb(), hsv)
                CompactLabeledSlider(
                    label = "Stop ${safeIndex + 1} Hue",
                    valueText = "${hsv[0].toInt()}°",
                    value = hsv[0],
                    range = 0f..360f,
                    onValueChange = { h ->
                        val nextInt = android.graphics.Color.HSVToColor(floatArrayOf(h, hsv[1].coerceAtLeast(0.4f), hsv[2].coerceAtLeast(0.5f)))
                        onUpdateCustomColor(safeIndex, nextInt.toLong() and 0xFFFFFFFFL)
                    },
                )
            }
        }

        item(key = "color_scoped_params") {
            Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                ScopedParamControl(
                    param = colorEngine.hueShift,
                    liveValueProvider = { liveValues[colorEngine.hueShift.id] },
                    onParamChange = { p -> onUpdateColorEngine { it.copy(hueShift = p) } },
                )
                ScopedParamControl(
                    param = colorEngine.hueDriftSpeed,
                    liveValueProvider = { liveValues[colorEngine.hueDriftSpeed.id] },
                    onParamChange = { p -> onUpdateColorEngine { it.copy(hueDriftSpeed = p) } },
                )
                ScopedParamControl(
                    param = colorEngine.saturation,
                    liveValueProvider = { liveValues[colorEngine.saturation.id] },
                    onParamChange = { p -> onUpdateColorEngine { it.copy(saturation = p) } },
                )
                ScopedParamControl(
                    param = colorEngine.contrast,
                    liveValueProvider = { liveValues[colorEngine.contrast.id] },
                    onParamChange = { p -> onUpdateColorEngine { it.copy(contrast = p) } },
                )
                ScopedParamControl(
                    param = colorEngine.beatFlash,
                    liveValueProvider = { liveValues[colorEngine.beatFlash.id] },
                    onParamChange = { p -> onUpdateColorEngine { it.copy(beatFlash = p) } },
                )
            }
        }
    }
}

@Composable
fun AudioDspTabContent(
    audioSource: AudioSourceType,
    audioFrame: AudioFrame,
    dspSettings: DspSettings,
    synthFallbackConfig: SynthFallbackConfig,
    deviceTapConfig: DeviceTapConfig,
    hasTapHardwareSensor: Boolean,
    onSelectAudioSource: (AudioSourceType) -> Unit,
    onUpdateDsp: ((DspSettings) -> DspSettings) -> Unit,
    onUpdateSynthFallback: ((SynthFallbackConfig) -> SynthFallbackConfig) -> Unit,
    onUpdateDeviceTap: ((DeviceTapConfig) -> DeviceTapConfig) -> Unit,
    onSimulateDeviceTapNow: () -> Unit,
) {
    LazyColumn(
        verticalArrangement = Arrangement.spacedBy(10.dp),
        modifier = Modifier.fillMaxWidth(),
    ) {
        item(key = "audio_sources") {
            Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
                Text("LIVE AUDIO SOURCE", style = MaterialTheme.typography.labelSmall, color = MistSecondary)
                AudioSourceType.entries.forEach { src ->
                    val selected = audioSource == src
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .clip(RoundedCornerShape(12.dp))
                            .background(if (selected) ElectricCyan.copy(alpha = 0.16f) else ElevatedSurface)
                            .border(1.dp, if (selected) ElectricCyan else LuminousBorder, RoundedCornerShape(12.dp))
                            .clickable { onSelectAudioSource(src) }
                            .padding(horizontal = 12.dp, vertical = 10.dp)
                            .testTag("audio_source_${src.name}"),
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.SpaceBetween,
                    ) {
                        Column(modifier = Modifier.weight(1f)) {
                            Text(src.label, style = MaterialTheme.typography.titleSmall, color = Color.White)
                            Text(src.subtitle, style = MaterialTheme.typography.bodyMedium, color = MistSecondary)
                        }
                        if (selected) {
                            Box(
                                modifier = Modifier
                                    .size(10.dp)
                                    .clip(CircleShape)
                                    .background(ElectricCyan),
                            )
                        }
                    }
                }
            }
        }

        item(key = "live_spectrum_meter") {
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .clip(RoundedCornerShape(12.dp))
                    .background(ElevatedSurface)
                    .border(1.dp, LuminousBorder, RoundedCornerShape(12.dp))
                    .padding(10.dp),
                verticalArrangement = Arrangement.spacedBy(6.dp),
            ) {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                ) {
                    Text("LIVE 64-BAND SPECTRUM", style = MaterialTheme.typography.labelSmall, color = MistSecondary)
                    Text(
                        text = "${audioFrame.rmsDb.toInt()} dB  •  Peak ${ModulationEvaluator.formatHz(audioFrame.dominantHz)}",
                        style = MonoTelemetryStyle,
                        color = ElectricCyan,
                    )
                }
                Canvas(
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(42.dp),
                ) {
                    val bands = audioFrame.bands64
                    val barW = size.width / bands.size.coerceAtLeast(1)
                    for (i in bands.indices) {
                        val h = (bands[i] * size.height).coerceIn(2f, size.height)
                        val color = if (i < 12) AcidMagenta else if (i < 36) ElectricCyan else SolarAmber
                        drawLine(
                            color = color,
                            start = Offset(i * barW + barW * 0.5f, size.height),
                            end = Offset(i * barW + barW * 0.5f, size.height - h),
                            strokeWidth = (barW * 0.72f).coerceAtLeast(2f),
                        )
                    }
                }
            }
        }

        item(key = "dsp_controls") {
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .clip(RoundedCornerShape(12.dp))
                    .background(ElevatedSurface.copy(alpha = 0.75f))
                    .border(1.dp, LuminousBorder, RoundedCornerShape(12.dp))
                    .padding(10.dp),
                verticalArrangement = Arrangement.spacedBy(6.dp),
            ) {
                Text("DSP SIGNAL CONDITIONING", style = MaterialTheme.typography.labelSmall, color = MistSecondary)

                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .horizontalScroll(rememberScrollState()),
                    horizontalArrangement = Arrangement.spacedBy(6.dp),
                ) {
                    FftWindowType.entries.forEach { win ->
                        val selected = dspSettings.fftWindow == win
                        Box(
                            modifier = Modifier
                                .clip(RoundedCornerShape(8.dp))
                                .background(if (selected) SolarAmber.copy(alpha = 0.22f) else HighestSurface)
                                .border(1.dp, if (selected) SolarAmber else LuminousBorder, RoundedCornerShape(8.dp))
                                .clickable { onUpdateDsp { it.copy(fftWindow = win) } }
                                .padding(horizontal = 10.dp, vertical = 6.dp),
                        ) {
                            Text("FFT: ${win.label}", style = MonoTelemetryStyle, color = if (selected) SolarAmber else MistSecondary)
                        }
                    }
                }

                CompactLabeledSlider(
                    label = "Input Gain",
                    valueText = "${String.format("%+.1f", dspSettings.inputGainDb)} dB",
                    value = dspSettings.inputGainDb,
                    range = -12f..24f,
                    onValueChange = { g -> onUpdateDsp { it.copy(inputGainDb = g) } },
                )
                CompactLabeledSlider(
                    label = "Bass Tilt EQ",
                    valueText = "${String.format("%+.1f", dspSettings.bassTiltDb)} dB",
                    value = dspSettings.bassTiltDb,
                    range = -6f..12f,
                    onValueChange = { b -> onUpdateDsp { it.copy(bassTiltDb = b) } },
                )
                CompactLabeledSlider(
                    label = "Noise Floor Gate",
                    valueText = "${(dspSettings.noiseFloor * 100).toInt()}%",
                    value = dspSettings.noiseFloor,
                    range = 0f..0.25f,
                    onValueChange = { n -> onUpdateDsp { it.copy(noiseFloor = n) } },
                )
                CompactLabeledSlider(
                    label = "Beat Sensitivity",
                    valueText = "${String.format("%.2f", dspSettings.beatThreshold)}x",
                    value = dspSettings.beatThreshold,
                    range = 1.05f..2.5f,
                    onValueChange = { t -> onUpdateDsp { it.copy(beatThreshold = t) } },
                )
                CompactLabeledSlider(
                    label = "Beat Cooldown",
                    valueText = "${dspSettings.beatCooldownMs} ms",
                    value = dspSettings.beatCooldownMs.toFloat(),
                    range = 60f..400f,
                    onValueChange = { c -> onUpdateDsp { it.copy(beatCooldownMs = c.toInt()) } },
                )
                CompactLabeledSlider(
                    label = "Global Smoothing",
                    valueText = "${(dspSettings.globalSmoothing * 100).toInt()}%",
                    value = dspSettings.globalSmoothing,
                    range = 0f..0.9f,
                    onValueChange = { s -> onUpdateDsp { it.copy(globalSmoothing = s) } },
                )
            }
        }

        item(key = "idle_synth_fallback_card") {
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .clip(RoundedCornerShape(12.dp))
                    .background(ElevatedSurface.copy(alpha = 0.8f))
                    .border(
                        1.dp,
                        if (synthFallbackConfig.enabled) ElectricCyan.copy(alpha = 0.6f) else LuminousBorder,
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
                        Icon(Icons.Filled.Waves, contentDescription = null, tint = ElectricCyan, modifier = Modifier.size(18.dp))
                        Column {
                            Text("Synthetic Music on Silence", style = MaterialTheme.typography.titleSmall, color = Color.White)
                            Text(
                                text = if (audioFrame.isSyntheticFallback) "ACTIVE • Synthesizing ${synthFallbackConfig.style.label}" else "Auto-generates music-like data when idle",
                                style = MonoTelemetryStyle,
                                color = if (audioFrame.isSyntheticFallback) SolarAmber else MistSecondary,
                            )
                        }
                    }
                    Switch(
                        checked = synthFallbackConfig.enabled,
                        onCheckedChange = { on -> onUpdateSynthFallback { it.copy(enabled = on) } },
                        colors = SwitchDefaults.colors(checkedThumbColor = ElectricCyan),
                        modifier = Modifier.testTag("synth_fallback_switch"),
                    )
                }

                if (synthFallbackConfig.enabled) {
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .horizontalScroll(rememberScrollState()),
                        horizontalArrangement = Arrangement.spacedBy(6.dp),
                    ) {
                        SynthMusicStyle.entries.forEach { st ->
                            val selected = synthFallbackConfig.style == st
                            Box(
                                modifier = Modifier
                                    .clip(RoundedCornerShape(8.dp))
                                    .background(if (selected) ElectricCyan.copy(alpha = 0.22f) else HighestSurface)
                                    .border(1.dp, if (selected) ElectricCyan else LuminousBorder, RoundedCornerShape(8.dp))
                                    .clickable {
                                        onUpdateSynthFallback { it.copy(style = st, tempoBpm = st.defaultBpm) }
                                    }
                                    .padding(horizontal = 10.dp, vertical = 6.dp)
                                    .testTag("synth_style_${st.name}"),
                            ) {
                                Text(st.label, style = MonoTelemetryStyle, color = if (selected) ElectricCyan else MistSecondary)
                            }
                        }
                    }

                    CompactLabeledSlider(
                        label = "Synth Tempo",
                        valueText = "${synthFallbackConfig.tempoBpm.toInt()} BPM",
                        value = synthFallbackConfig.tempoBpm,
                        range = 60f..180f,
                        onValueChange = { bpm -> onUpdateSynthFallback { it.copy(tempoBpm = bpm) } },
                    )
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.spacedBy(8.dp),
                    ) {
                        CompactLabeledSlider(
                            label = "Synth Drive",
                            valueText = "${(synthFallbackConfig.intensity * 100).toInt()}%",
                            value = synthFallbackConfig.intensity,
                            range = 0.2f..1.2f,
                            onValueChange = { i -> onUpdateSynthFallback { it.copy(intensity = i) } },
                            modifier = Modifier.weight(1f),
                        )
                        CompactLabeledSlider(
                            label = "Silence Delay",
                            valueText = "${String.format("%.1f", synthFallbackConfig.silenceDelaySec)}s",
                            value = synthFallbackConfig.silenceDelaySec,
                            range = 0.2f..6.0f,
                            onValueChange = { d -> onUpdateSynthFallback { it.copy(silenceDelaySec = d) } },
                            modifier = Modifier.weight(1f),
                        )
                    }
                }
            }
        }

        item(key = "device_tap_sensor_card") {
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .clip(RoundedCornerShape(12.dp))
                    .background(ElevatedSurface.copy(alpha = 0.8f))
                    .border(
                        1.dp,
                        if (deviceTapConfig.enabled) AcidMagenta.copy(alpha = 0.6f) else LuminousBorder,
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
                        Icon(Icons.Filled.Sensors, contentDescription = null, tint = AcidMagenta, modifier = Modifier.size(18.dp))
                        Column {
                            Text("Tap Device for Random Options", style = MaterialTheme.typography.titleSmall, color = Color.White)
                            Text(
                                text = if (!hasTapHardwareSensor) "Accelerometer unavailable on device (use test button)" else "Tap phone chassis to pick next options",
                                style = MonoTelemetryStyle,
                                color = MistSecondary,
                            )
                        }
                    }
                    Switch(
                        checked = deviceTapConfig.enabled,
                        onCheckedChange = { on -> onUpdateDeviceTap { it.copy(enabled = on) } },
                        colors = SwitchDefaults.colors(checkedThumbColor = AcidMagenta),
                        modifier = Modifier.testTag("device_tap_switch"),
                    )
                }

                if (deviceTapConfig.enabled) {
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .horizontalScroll(rememberScrollState()),
                        horizontalArrangement = Arrangement.spacedBy(6.dp),
                    ) {
                        DeviceTapAction.entries.forEach { act ->
                            val selected = deviceTapConfig.action == act
                            Box(
                                modifier = Modifier
                                    .clip(RoundedCornerShape(8.dp))
                                    .background(if (selected) AcidMagenta.copy(alpha = 0.22f) else HighestSurface)
                                    .border(1.dp, if (selected) AcidMagenta else LuminousBorder, RoundedCornerShape(8.dp))
                                    .clickable { onUpdateDeviceTap { it.copy(action = act) } }
                                    .padding(horizontal = 10.dp, vertical = 6.dp),
                            ) {
                                Text(act.label, style = MonoTelemetryStyle, color = if (selected) Color.White else MistSecondary)
                            }
                        }
                    }

                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.spacedBy(8.dp),
                    ) {
                        CompactLabeledSlider(
                            label = "Tap Threshold",
                            valueText = "${String.format("%.1f", deviceTapConfig.sensitivityThreshold)} m/s²",
                            value = deviceTapConfig.sensitivityThreshold,
                            range = 2.5f..14.0f,
                            onValueChange = { th -> onUpdateDeviceTap { it.copy(sensitivityThreshold = th) } },
                            modifier = Modifier.weight(1f),
                        )
                        CompactLabeledSlider(
                            label = "Cooldown",
                            valueText = "${deviceTapConfig.cooldownMs}ms",
                            value = deviceTapConfig.cooldownMs.toFloat(),
                            range = 300f..1500f,
                            onValueChange = { cd -> onUpdateDeviceTap { it.copy(cooldownMs = cd.toLong()) } },
                            modifier = Modifier.weight(1f),
                        )
                    }

                    OutlinedButton(
                        onClick = onSimulateDeviceTapNow,
                        modifier = Modifier
                            .fillMaxWidth()
                            .defaultMinSize(minHeight = 48.dp)
                            .testTag("simulate_device_tap_btn"),
                    ) {
                        Text("Trigger Tap Action Now", color = Color.White)
                    }
                }
            }
        }
    }
}

