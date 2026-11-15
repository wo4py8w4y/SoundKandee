package com.example.ui.components

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
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
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.KeyboardArrowLeft
import androidx.compose.material.icons.automirrored.filled.KeyboardArrowRight
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.BookmarkAdded
import androidx.compose.material.icons.filled.ContentCopy
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.Edit
import androidx.compose.material.icons.filled.Restore
import androidx.compose.material.icons.filled.Save
import androidx.compose.material.icons.filled.Tune
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.OutlinedTextFieldDefaults
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.example.model.ColorEngineConfig
import com.example.model.PresetCatalog
import com.example.model.TransformerNode
import com.example.model.VisualLayer
import com.example.model.VisualizerPreset
import com.example.ui.theme.AcidMagenta
import com.example.ui.theme.ElectricCyan
import com.example.ui.theme.ElevatedSurface
import com.example.ui.theme.HighestSurface
import com.example.ui.theme.LuminousBorder
import com.example.ui.theme.MistSecondary
import com.example.ui.theme.MonoTelemetryStyle
import com.example.ui.theme.SolarAmber
import com.example.ui.theme.VoidObsidian

@Composable
fun PresetsTabContent(
    userPresets: List<VisualizerPreset>,
    builtInPresets: List<VisualizerPreset>,
    activePresetId: String?,
    hasUnsavedChanges: Boolean,
    currentLayers: List<VisualLayer>,
    currentTransformers: List<TransformerNode>,
    currentColorEngine: ColorEngineConfig,
    onLoadPreset: (VisualizerPreset) -> Unit,
    onPrevPreset: () -> Unit,
    onNextPreset: () -> Unit,
    onRequestSaveNew: () -> Unit,
    onOverwritePreset: (String) -> Unit,
    onDuplicatePreset: (String) -> Unit,
    onRenamePreset: (String, String, String?) -> Unit,
    onDeletePreset: (String) -> Unit,
    onRevertPreset: () -> Unit,
) {
    var renamingPreset by remember { mutableStateOf<VisualizerPreset?>(null) }
    val activePreset = (userPresets + builtInPresets).firstOrNull { it.id == activePresetId }
    val liveSummary = remember(currentLayers, currentTransformers, currentColorEngine) {
        PresetCatalog.summarizeConfiguration(currentLayers, currentTransformers, currentColorEngine)
    }

    LazyColumn(
        verticalArrangement = Arrangement.spacedBy(10.dp),
        modifier = Modifier.fillMaxWidth(),
    ) {
        item(key = "active_snapshot_card") {
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .clip(RoundedCornerShape(14.dp))
                    .background(ElevatedSurface.copy(alpha = 0.9f))
                    .border(1.dp, ElectricCyan.copy(alpha = 0.55f), RoundedCornerShape(14.dp))
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
                            text = "ACTIVE CONFIGURATION",
                            style = MaterialTheme.typography.labelSmall,
                            color = MistSecondary,
                        )
                        Row(
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.spacedBy(6.dp),
                        ) {
                            Text(
                                text = activePreset?.name ?: "Custom Mix",
                                style = MaterialTheme.typography.titleSmall,
                                color = Color.White,
                                maxLines = 1,
                                overflow = TextOverflow.Ellipsis,
                            )
                            if (hasUnsavedChanges) {
                                Box(
                                    modifier = Modifier
                                        .clip(RoundedCornerShape(6.dp))
                                        .background(SolarAmber.copy(alpha = 0.22f))
                                        .border(1.dp, SolarAmber, RoundedCornerShape(6.dp))
                                        .padding(horizontal = 6.dp, vertical = 2.dp)
                                        .testTag("preset_modified_badge"),
                                ) {
                                    Text(
                                        text = "MODIFIED",
                                        style = MonoTelemetryStyle.copy(fontWeight = FontWeight.Bold),
                                        color = SolarAmber,
                                    )
                                }
                            }
                        }
                        Text(
                            text = liveSummary,
                            style = MonoTelemetryStyle,
                            color = ElectricCyan,
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis,
                        )
                    }

                    Row(verticalAlignment = Alignment.CenterVertically) {
                        IconButton(
                            onClick = onPrevPreset,
                            modifier = Modifier
                                .size(48.dp)
                                .testTag("presets_tab_prev_btn"),
                        ) {
                            Icon(
                                imageVector = Icons.AutoMirrored.Filled.KeyboardArrowLeft,
                                contentDescription = "Previous preset",
                                tint = Color.White,
                            )
                        }
                        IconButton(
                            onClick = onNextPreset,
                            modifier = Modifier
                                .size(48.dp)
                                .testTag("presets_tab_next_btn"),
                        ) {
                            Icon(
                                imageVector = Icons.AutoMirrored.Filled.KeyboardArrowRight,
                                contentDescription = "Next preset",
                                tint = Color.White,
                            )
                        }
                    }
                }

                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(8.dp),
                ) {
                    Button(
                        onClick = onRequestSaveNew,
                        colors = ButtonDefaults.buttonColors(
                            containerColor = ElectricCyan,
                            contentColor = VoidObsidian,
                        ),
                        modifier = Modifier
                            .weight(1f)
                            .testTag("save_new_preset_button"),
                    ) {
                        Icon(Icons.Filled.Add, contentDescription = null, modifier = Modifier.size(16.dp))
                        Spacer(Modifier.width(4.dp))
                        Text("Save New", maxLines = 1, style = MaterialTheme.typography.labelMedium)
                    }

                    if (activePreset != null && !activePreset.isBuiltIn && hasUnsavedChanges) {
                        OutlinedButton(
                            onClick = { onOverwritePreset(activePreset.id) },
                            modifier = Modifier
                                .weight(1f)
                                .testTag("update_preset_button"),
                        ) {
                            Icon(Icons.Filled.Save, contentDescription = null, modifier = Modifier.size(16.dp), tint = AcidMagenta)
                            Spacer(Modifier.width(4.dp))
                            Text("Update", maxLines = 1, style = MaterialTheme.typography.labelMedium, color = Color.White)
                        }
                    }

                    if (activePreset != null && hasUnsavedChanges) {
                        OutlinedButton(
                            onClick = onRevertPreset,
                            modifier = Modifier
                                .weight(1f)
                                .testTag("revert_preset_button"),
                        ) {
                            Icon(Icons.Filled.Restore, contentDescription = null, modifier = Modifier.size(16.dp), tint = SolarAmber)
                            Spacer(Modifier.width(4.dp))
                            Text("Revert", maxLines = 1, style = MaterialTheme.typography.labelMedium, color = Color.White)
                        }
                    }
                }
            }
        }

        item(key = "user_presets_header") {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Text(
                    text = "MY SAVED PRESETS (${userPresets.size})",
                    style = MaterialTheme.typography.labelSmall,
                    color = MistSecondary,
                )
            }
        }

        if (userPresets.isEmpty()) {
            item(key = "user_presets_empty") {
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .clip(RoundedCornerShape(12.dp))
                        .background(ElevatedSurface.copy(alpha = 0.55f))
                        .border(1.dp, LuminousBorder, RoundedCornerShape(12.dp))
                        .padding(14.dp),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(10.dp),
                ) {
                    Icon(
                        imageVector = Icons.Filled.Tune,
                        contentDescription = null,
                        tint = ElectricCyan,
                        modifier = Modifier.size(22.dp),
                    )
                    Text(
                        text = "Tap Save New above to store your current layer stack, audio filters, color stops & parameter scopes.",
                        style = MaterialTheme.typography.bodyMedium,
                        color = MistSecondary,
                    )
                }
            }
        } else {
            items(userPresets, key = { it.id }) { preset ->
                val isSelected = preset.id == activePresetId
                UserPresetRowCard(
                    preset = preset,
                    isSelected = isSelected,
                    hasUnsavedChanges = isSelected && hasUnsavedChanges,
                    onLoad = { onLoadPreset(preset) },
                    onOverwrite = { onOverwritePreset(preset.id) },
                    onRename = { renamingPreset = preset },
                    onDuplicate = { onDuplicatePreset(preset.id) },
                    onDelete = { onDeletePreset(preset.id) },
                )
            }
        }

        item(key = "builtin_presets_header") {
            Text(
                text = "CURATED STARTER RECIPES",
                style = MaterialTheme.typography.labelSmall,
                color = MistSecondary,
                modifier = Modifier.padding(top = 4.dp),
            )
        }

        items(builtInPresets, key = { it.id }) { preset ->
            val isSelected = preset.id == activePresetId
            val palette = remember(preset.paletteId, preset.colorEngine) {
                if (preset.colorEngine.useCustomPalette) {
                    preset.colorEngine.customColors
                } else {
                    PresetCatalog.paletteById(preset.paletteId).colors
                }
            }
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .clip(RoundedCornerShape(12.dp))
                    .background(if (isSelected) AcidMagenta.copy(alpha = 0.16f) else ElevatedSurface)
                    .border(1.dp, if (isSelected) AcidMagenta else LuminousBorder, RoundedCornerShape(12.dp))
                    .clickable { onLoadPreset(preset) }
                    .padding(horizontal = 10.dp, vertical = 8.dp)
                    .testTag("preset_${preset.id}"),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(8.dp),
            ) {
                Box(
                    modifier = Modifier
                        .size(width = 8.dp, height = 38.dp)
                        .clip(RoundedCornerShape(4.dp))
                        .background(Brush.verticalGradient(palette.map { Color(it.toInt()) })),
                )
                Column(modifier = Modifier.weight(1f)) {
                    Text(
                        text = preset.name,
                        style = MaterialTheme.typography.titleSmall,
                        color = Color.White,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis,
                    )
                    Text(
                        text = preset.subtitle,
                        style = MaterialTheme.typography.bodyMedium,
                        color = MistSecondary,
                        maxLines = 2,
                        overflow = TextOverflow.Ellipsis,
                    )
                }
                IconButton(
                    onClick = { onDuplicatePreset(preset.id) },
                    modifier = Modifier
                        .size(48.dp)
                        .testTag("duplicate_preset_${preset.id}"),
                ) {
                    Icon(
                        imageVector = Icons.Filled.ContentCopy,
                        contentDescription = "Duplicate ${preset.name} to my presets",
                        tint = ElectricCyan,
                        modifier = Modifier.size(18.dp),
                    )
                }
            }
        }
    }

    val toRename = renamingPreset
    if (toRename != null) {
        RenamePresetDialog(
            initialName = toRename.name,
            initialSubtitle = toRename.subtitle,
            onDismiss = { renamingPreset = null },
            onConfirm = { newName, newSub ->
                onRenamePreset(toRename.id, newName, newSub)
                renamingPreset = null
            },
        )
    }
}

@Composable
private fun UserPresetRowCard(
    preset: VisualizerPreset,
    isSelected: Boolean,
    hasUnsavedChanges: Boolean,
    onLoad: () -> Unit,
    onOverwrite: () -> Unit,
    onRename: () -> Unit,
    onDuplicate: () -> Unit,
    onDelete: () -> Unit,
) {
    val paletteColors = remember(preset.colorEngine, preset.paletteId) {
        if (preset.colorEngine.useCustomPalette) {
            preset.colorEngine.customColors
        } else {
            PresetCatalog.paletteById(preset.paletteId).colors
        }
    }
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(12.dp))
            .background(if (isSelected) ElectricCyan.copy(alpha = 0.14f) else ElevatedSurface)
            .border(1.dp, if (isSelected) ElectricCyan else LuminousBorder, RoundedCornerShape(12.dp))
            .clickable { onLoad() }
            .padding(horizontal = 10.dp, vertical = 8.dp)
            .testTag("preset_${preset.id}"),
        verticalArrangement = Arrangement.spacedBy(4.dp),
    ) {
        Row(
            modifier = Modifier.fillMaxWidth(),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(8.dp),
        ) {
            Box(
                modifier = Modifier
                    .size(width = 8.dp, height = 36.dp)
                    .clip(RoundedCornerShape(4.dp))
                    .background(Brush.verticalGradient(paletteColors.map { Color(it.toInt()) })),
            )
            Column(modifier = Modifier.weight(1f)) {
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(6.dp),
                ) {
                    if (isSelected) {
                        Icon(
                            imageVector = Icons.Filled.BookmarkAdded,
                            contentDescription = "Active preset",
                            tint = ElectricCyan,
                            modifier = Modifier.size(15.dp),
                        )
                    }
                    Text(
                        text = preset.name,
                        style = MaterialTheme.typography.titleSmall,
                        color = Color.White,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis,
                    )
                }
                Text(
                    text = preset.subtitle.ifBlank {
                        PresetCatalog.summarizeConfiguration(preset.layers, preset.transformers, preset.colorEngine)
                    },
                    style = MonoTelemetryStyle,
                    color = MistSecondary,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                )
            }
        }

        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.End,
            verticalAlignment = Alignment.CenterVertically,
        ) {
            if (isSelected && hasUnsavedChanges) {
                IconButton(
                    onClick = onOverwrite,
                    modifier = Modifier
                        .size(48.dp)
                        .testTag("overwrite_preset_${preset.id}"),
                ) {
                    Icon(
                        imageVector = Icons.Filled.Save,
                        contentDescription = "Overwrite ${preset.name} with current settings",
                        tint = SolarAmber,
                        modifier = Modifier.size(18.dp),
                    )
                }
            }
            IconButton(
                onClick = onRename,
                modifier = Modifier
                    .size(48.dp)
                    .testTag("rename_preset_${preset.id}"),
            ) {
                Icon(
                    imageVector = Icons.Filled.Edit,
                    contentDescription = "Rename ${preset.name}",
                    tint = MistSecondary,
                    modifier = Modifier.size(18.dp),
                )
            }
            IconButton(
                onClick = onDuplicate,
                modifier = Modifier
                    .size(48.dp)
                    .testTag("duplicate_preset_${preset.id}"),
            ) {
                Icon(
                    imageVector = Icons.Filled.ContentCopy,
                    contentDescription = "Duplicate ${preset.name}",
                    tint = ElectricCyan,
                    modifier = Modifier.size(18.dp),
                )
            }
            IconButton(
                onClick = onDelete,
                modifier = Modifier
                    .size(48.dp)
                    .testTag("delete_preset_${preset.id}"),
            ) {
                Icon(
                    imageVector = Icons.Filled.Delete,
                    contentDescription = "Delete ${preset.name}",
                    tint = AcidMagenta,
                    modifier = Modifier.size(18.dp),
                )
            }
        }
    }
}

@Composable
fun SavePresetDialog(
    suggestedName: String,
    configurationSummary: String,
    onDismiss: () -> Unit,
    onConfirmSave: (name: String, subtitle: String) -> Unit,
) {
    var name by remember(suggestedName) { mutableStateOf(suggestedName) }
    var subtitle by remember { mutableStateOf("") }

    AlertDialog(
        onDismissRequest = onDismiss,
        containerColor = HighestSurface,
        titleContentColor = Color.White,
        textContentColor = MistSecondary,
        title = {
            Text("Save Visualizer Preset", style = MaterialTheme.typography.titleMedium)
        },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
                Text(
                    text = "Captures: $configurationSummary (including all ScopedParam bounds, drivers, filters, color stops & DSP).",
                    style = MaterialTheme.typography.bodyMedium,
                    color = MistSecondary,
                )
                OutlinedTextField(
                    value = name,
                    onValueChange = { name = it },
                    label = { Text("Preset Name") },
                    singleLine = true,
                    colors = OutlinedTextFieldDefaults.colors(
                        focusedBorderColor = ElectricCyan,
                        unfocusedBorderColor = LuminousBorder,
                        focusedTextColor = Color.White,
                        unfocusedTextColor = Color.White,
                    ),
                    modifier = Modifier
                        .fillMaxWidth()
                        .testTag("preset_name_input"),
                )
                OutlinedTextField(
                    value = subtitle,
                    onValueChange = { subtitle = it },
                    label = { Text("Notes (optional)") },
                    singleLine = true,
                    colors = OutlinedTextFieldDefaults.colors(
                        focusedBorderColor = ElectricCyan,
                        unfocusedBorderColor = LuminousBorder,
                        focusedTextColor = Color.White,
                        unfocusedTextColor = Color.White,
                    ),
                    modifier = Modifier
                        .fillMaxWidth()
                        .testTag("preset_subtitle_input"),
                )
            }
        },
        confirmButton = {
            Button(
                onClick = { onConfirmSave(name, subtitle) },
                colors = ButtonDefaults.buttonColors(containerColor = ElectricCyan, contentColor = VoidObsidian),
                modifier = Modifier.testTag("confirm_save_preset_button"),
            ) {
                Text("Save Preset")
            }
        },
        dismissButton = {
            TextButton(
                onClick = onDismiss,
                modifier = Modifier.testTag("cancel_preset_dialog_button"),
            ) {
                Text("Cancel", color = MistSecondary)
            }
        },
    )
}

@Composable
private fun RenamePresetDialog(
    initialName: String,
    initialSubtitle: String,
    onDismiss: () -> Unit,
    onConfirm: (name: String, subtitle: String) -> Unit,
) {
    var name by remember(initialName) { mutableStateOf(initialName) }
    var subtitle by remember(initialSubtitle) { mutableStateOf(initialSubtitle) }

    AlertDialog(
        onDismissRequest = onDismiss,
        containerColor = HighestSurface,
        titleContentColor = Color.White,
        textContentColor = MistSecondary,
        title = {
            Text("Rename Preset", style = MaterialTheme.typography.titleMedium)
        },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
                OutlinedTextField(
                    value = name,
                    onValueChange = { name = it },
                    label = { Text("Preset Name") },
                    singleLine = true,
                    colors = OutlinedTextFieldDefaults.colors(
                        focusedBorderColor = ElectricCyan,
                        unfocusedBorderColor = LuminousBorder,
                        focusedTextColor = Color.White,
                        unfocusedTextColor = Color.White,
                    ),
                    modifier = Modifier
                        .fillMaxWidth()
                        .testTag("rename_preset_name_input"),
                )
                Spacer(Modifier.height(2.dp))
                OutlinedTextField(
                    value = subtitle,
                    onValueChange = { subtitle = it },
                    label = { Text("Notes") },
                    singleLine = true,
                    colors = OutlinedTextFieldDefaults.colors(
                        focusedBorderColor = ElectricCyan,
                        unfocusedBorderColor = LuminousBorder,
                        focusedTextColor = Color.White,
                        unfocusedTextColor = Color.White,
                    ),
                    modifier = Modifier.fillMaxWidth(),
                )
            }
        },
        confirmButton = {
            Button(
                onClick = { onConfirm(name, subtitle) },
                enabled = name.trim().isNotEmpty(),
                colors = ButtonDefaults.buttonColors(containerColor = ElectricCyan, contentColor = VoidObsidian),
                modifier = Modifier.testTag("confirm_rename_preset_button"),
            ) {
                Text("Save")
            }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) {
                Text("Cancel", color = MistSecondary)
            }
        },
    )
}
