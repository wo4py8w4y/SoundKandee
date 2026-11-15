package com.example.ui.theme

import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.darkColorScheme
import androidx.compose.runtime.Composable

private val ChromaWaveDarkColorScheme = darkColorScheme(
    primary = ElectricCyan,
    onPrimary = VoidObsidian,
    primaryContainer = ElevatedSurface,
    onPrimaryContainer = ElectricCyan,
    secondary = AcidMagenta,
    onSecondary = CrispWhite,
    secondaryContainer = HighestSurface,
    onSecondaryContainer = AcidMagenta,
    tertiary = SolarAmber,
    onTertiary = VoidObsidian,
    tertiaryContainer = ElevatedSurface,
    onTertiaryContainer = SolarAmber,
    background = VoidObsidian,
    onBackground = CrispWhite,
    surface = SolidSurface,
    onSurface = CrispWhite,
    surfaceVariant = ElevatedSurface,
    onSurfaceVariant = MistSecondary,
    surfaceContainer = GlassSurface,
    surfaceContainerHigh = ElevatedSurface,
    surfaceContainerHighest = HighestSurface,
    outline = LuminousBorder,
    outlineVariant = DimMuted,
    error = ErrorCoral,
    onError = VoidObsidian,
)

@Composable
fun ChromaWaveTheme(
    content: @Composable () -> Unit,
) {
    MaterialTheme(
        colorScheme = ChromaWaveDarkColorScheme,
        typography = AppTypography,
        content = content,
    )
}
