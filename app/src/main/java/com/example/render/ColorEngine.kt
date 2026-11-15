package com.example.render

import androidx.compose.ui.graphics.Color
import com.example.model.ColorEngineConfig
import com.example.model.PresetCatalog
import kotlin.math.abs
import kotlin.math.max
import kotlin.math.min

/**
 * Evaluates multi-stop psychedelic color palettes with continuous hue drift,
 * HSV saturation/contrast scaling, and beat-flash highlights.
 */
object ColorEngine {

    fun activeBaseColors(config: ColorEngineConfig): List<Color> {
        val rawLongs = if (config.useCustomPalette && config.customColors.size >= 2) {
            config.customColors
        } else {
            PresetCatalog.paletteById(config.paletteId).colors
        }
        return rawLongs.map { Color(it.toInt()) }
    }

    fun sampleColor(
        colors: List<Color>,
        t: Float,
        hueShiftDeg: Float,
        saturationMul: Float,
        lumaBoost: Float,
        beatFlash: Float,
        alpha: Float = 1f,
    ): Color {
        if (colors.isEmpty()) return Color.Cyan.copy(alpha = alpha)
        val wrapped = ((t % 1f) + 1f) % 1f
        val base = if (colors.size == 1) {
            colors[0]
        } else {
            val scaled = wrapped * (colors.size - 1)
            val idx = scaled.toInt().coerceIn(0, colors.lastIndex - 1)
            val frac = scaled - idx
            lerpColor(colors[idx], colors[idx + 1], frac)
        }
        return adjustHsv(
            color = base,
            hueShiftDeg = hueShiftDeg,
            saturationMul = saturationMul,
            lumaBoost = lumaBoost,
            beatFlash = beatFlash,
            alpha = alpha,
        )
    }

    fun transformPalette(
        colors: List<Color>,
        hueShiftDeg: Float,
        saturationMul: Float,
        lumaBoost: Float,
        beatFlash: Float,
    ): List<Color> = colors.map { c ->
        adjustHsv(c, hueShiftDeg, saturationMul, lumaBoost, beatFlash, c.alpha)
    }

    private fun lerpColor(a: Color, b: Color, t: Float): Color {
        val f = t.coerceIn(0f, 1f)
        return Color(
            red = a.red + (b.red - a.red) * f,
            green = a.green + (b.green - a.green) * f,
            blue = a.blue + (b.blue - a.blue) * f,
            alpha = a.alpha + (b.alpha - a.alpha) * f,
        )
    }

    fun adjustHsv(
        color: Color,
        hueShiftDeg: Float,
        saturationMul: Float,
        lumaBoost: Float,
        beatFlash: Float,
        alpha: Float,
    ): Color {
        val r = color.red
        val g = color.green
        val b = color.blue
        val maxC = max(r, max(g, b))
        val minC = min(r, min(g, b))
        val delta = maxC - minC

        var hue = when {
            delta < 1e-5f -> 0f
            maxC == r -> 60f * (((g - b) / delta) % 6f)
            maxC == g -> 60f * (((b - r) / delta) + 2f)
            else -> 60f * (((r - g) / delta) + 4f)
        }
        if (hue < 0f) hue += 360f
        val sat = if (maxC < 1e-5f) 0f else delta / maxC
        val value = maxC

        val newHue = ((hue + hueShiftDeg) % 360f + 360f) % 360f
        val newSat = (sat * saturationMul * (1f - beatFlash * 0.35f)).coerceIn(0f, 1f)
        val newVal = (value * lumaBoost + beatFlash * 0.4f).coerceIn(0f, 1f)

        val c = newVal * newSat
        val x = c * (1f - abs((newHue / 60f) % 2f - 1f))
        val m = newVal - c

        val (rp, gp, bp) = when {
            newHue < 60f -> Triple(c, x, 0f)
            newHue < 120f -> Triple(x, c, 0f)
            newHue < 180f -> Triple(0f, c, x)
            newHue < 240f -> Triple(0f, x, c)
            newHue < 300f -> Triple(x, 0f, c)
            else -> Triple(c, 0f, x)
        }
        return Color(
            red = (rp + m).coerceIn(0f, 1f),
            green = (gp + m).coerceIn(0f, 1f),
            blue = (bp + m).coerceIn(0f, 1f),
            alpha = alpha.coerceIn(0f, 1f),
        )
    }
}
