package com.example.model

import androidx.compose.ui.graphics.Color
import kotlin.math.PI
import kotlin.math.cos
import kotlin.math.max
import kotlin.math.pow
import kotlin.math.roundToInt
import kotlin.math.sin

enum class TransitionCurve(val label: String, val description: String) {
    SMOOTHSTEP("Smooth", "Silky ease-in-out optical & shader dissolve"),
    EQUAL_POWER("Eq Power", "Constant-luminance trigonometric cross-fade"),
    LINEAR("Linear", "Uniform straight-line parameter fade"),
    BEAT_PUNCH("Beat Punch", "Fast energetic snap-in with smooth outgoing tail"),
    COSINE_DIP("Dip Fade", "Cinematic obsidian dip at midpoint before blooming"),
}

data class PresetTransitionConfig(
    val enabled: Boolean = true,
    val durationMs: Int = 750,
    val curve: TransitionCurve = TransitionCurve.SMOOTHSTEP,
    val morphShadersAndPalettes: Boolean = true,
    val crossfadeOnRandomize: Boolean = true,
) {
    companion object {
        const val MIN_DURATION_MS = 0
        const val MAX_DURATION_MS = 2500
        val QUICK_DURATIONS_MS = listOf(250, 500, 750, 1200, 2000)
    }
}

data class TransitionGains(
    val outgoingGain: Float,
    val incomingGain: Float,
    val morphT: Float,
)

object PresetTransitionBlender {

    fun evaluateGains(rawProgress: Float, curve: TransitionCurve): TransitionGains {
        val t = rawProgress.coerceIn(0f, 1f)
        return when (curve) {
            TransitionCurve.LINEAR -> TransitionGains(
                outgoingGain = 1f - t,
                incomingGain = t,
                morphT = t,
            )
            TransitionCurve.SMOOTHSTEP -> {
                val s = t * t * (3f - 2f * t)
                TransitionGains(
                    outgoingGain = 1f - s,
                    incomingGain = s,
                    morphT = s,
                )
            }
            TransitionCurve.EQUAL_POWER -> {
                val theta = t * (PI * 0.5).toFloat()
                val s = t * t * (3f - 2f * t)
                TransitionGains(
                    outgoingGain = cos(theta).coerceIn(0f, 1f),
                    incomingGain = sin(theta).coerceIn(0f, 1f),
                    morphT = s,
                )
            }
            TransitionCurve.BEAT_PUNCH -> {
                val outG = (1f - t).pow(2.2f).coerceIn(0f, 1f)
                val inG = (1f - (1f - t).pow(3f)).coerceIn(0f, 1f)
                TransitionGains(
                    outgoingGain = outG,
                    incomingGain = inG,
                    morphT = inG,
                )
            }
            TransitionCurve.COSINE_DIP -> {
                if (t < 0.5f) {
                    val u = (t * 2f).coerceIn(0f, 1f)
                    val s = u * u * (3f - 2f * u)
                    TransitionGains(
                        outgoingGain = 1f - s,
                        incomingGain = 0f,
                        morphT = 0f,
                    )
                } else {
                    val u = ((t - 0.5f) * 2f).coerceIn(0f, 1f)
                    val s = u * u * (3f - 2f * u)
                    TransitionGains(
                        outgoingGain = 0f,
                        incomingGain = s,
                        morphT = 1f,
                    )
                }
            }
        }
    }

    fun neutralValuesFor(
        type: TransformerType,
        reference: Triple<Float, Float, Float>,
    ): Triple<Float, Float, Float> = when (type) {
        TransformerType.MILK_RADIAL_TUNNEL_PULL -> Triple(1f, reference.second, 0f)
        TransformerType.MILK_ANAMORPHIC_STRETCH -> Triple(1f, reference.second, 0f)
        TransformerType.FEEDBACK_ECHO -> Triple(0f, 1f, 0f)
        TransformerType.CRT_SCANLINE_WARP -> Triple(0f, reference.second, 0f)
        TransformerType.BEAT_BLOOM_STROBE -> Triple(0f, 0f, 0f)
        TransformerType.KALEIDOSCOPE_MIRROR -> Triple(0f, reference.second, 1f)
        TransformerType.KALEIDO_TILE_MOSAIC -> Triple(1f, 0f, reference.third)
        TransformerType.WAVE_FOLD_WARP,
        TransformerType.CHROMATIC_SPLIT,
        TransformerType.VORTEX_SWIRL,
        TransformerType.RIPPLE_INTERFERENCE,
        TransformerType.MILK_PER_PIXEL_WARP,
        TransformerType.MILK_ANGULAR_PINWHEEL,
        TransformerType.MILK_EDGE_SOLARIZE,
        TransformerType.MILK_POSTERIZE_QUANTIZE,
        TransformerType.MILK_MIRROR_QUAD,
        TransformerType.MILK_POLAR_UNWRAP -> Triple(0f, reference.second, reference.third)
    }

    fun interpolateTxMaps(
        from: Map<TransformerType, Triple<Float, Float, Float>>,
        to: Map<TransformerType, Triple<Float, Float, Float>>,
        t: Float,
    ): Map<TransformerType, Triple<Float, Float, Float>> {
        val f = t.coerceIn(0f, 1f)
        if (f <= 0f) return from
        if (f >= 1f) return to

        val result = HashMap<TransformerType, Triple<Float, Float, Float>>(from.size + to.size)
        val keys = HashSet<TransformerType>(from.size + to.size).apply {
            addAll(from.keys)
            addAll(to.keys)
        }
        for (type in keys) {
            val a = from[type]
            val b = to[type]
            when {
                a != null && b != null -> {
                    result[type] = Triple(
                        lerpFloat(a.first, b.first, f),
                        lerpFloat(a.second, b.second, f),
                        lerpFloat(a.third, b.third, f),
                    )
                }
                a != null -> {
                    val neutral = neutralValuesFor(type, a)
                    result[type] = Triple(
                        lerpFloat(a.first, neutral.first, f),
                        lerpFloat(a.second, neutral.second, f),
                        lerpFloat(a.third, neutral.third, f),
                    )
                }
                b != null -> {
                    val neutral = neutralValuesFor(type, b)
                    result[type] = Triple(
                        lerpFloat(neutral.first, b.first, f),
                        lerpFloat(neutral.second, b.second, f),
                        lerpFloat(neutral.third, b.third, f),
                    )
                }
            }
        }
        return result
    }

    fun lerpPalettes(
        from: List<Color>,
        to: List<Color>,
        t: Float,
    ): List<Color> {
        val f = t.coerceIn(0f, 1f)
        if (from.isEmpty()) return to
        if (to.isEmpty()) return from
        if (f <= 0f) return from
        if (f >= 1f) return to

        val count = max(from.size, to.size).coerceAtLeast(2)
        return List(count) { idx ->
            val u = idx / (count - 1).toFloat()
            val cA = sampleNormalized(from, u)
            val cB = sampleNormalized(to, u)
            Color(
                red = lerpFloat(cA.red, cB.red, f).coerceIn(0f, 1f),
                green = lerpFloat(cA.green, cB.green, f).coerceIn(0f, 1f),
                blue = lerpFloat(cA.blue, cB.blue, f).coerceIn(0f, 1f),
                alpha = lerpFloat(cA.alpha, cB.alpha, f).coerceIn(0f, 1f),
            )
        }
    }

    fun hasDifferentSpatialSymmetry(
        from: Map<TransformerType, Triple<Float, Float, Float>>,
        to: Map<TransformerType, Triple<Float, Float, Float>>,
    ): Boolean {
        val fromKaleido = from[TransformerType.KALEIDOSCOPE_MIRROR]?.takeIf { it.first >= 2f }
        val toKaleido = to[TransformerType.KALEIDOSCOPE_MIRROR]?.takeIf { it.first >= 2f }
        val fromFolds = fromKaleido?.first?.roundToInt()?.coerceIn(2, 24)
        val toFolds = toKaleido?.first?.roundToInt()?.coerceIn(2, 24)
        if (fromFolds != toFolds) return true

        val fromTile = from[TransformerType.KALEIDO_TILE_MOSAIC]?.takeIf { it.first >= 1.5f }
        val toTile = to[TransformerType.KALEIDO_TILE_MOSAIC]?.takeIf { it.first >= 1.5f }
        val fromGrid = fromTile?.first?.roundToInt()?.coerceIn(2, 6)
        val toGrid = toTile?.first?.roundToInt()?.coerceIn(2, 6)
        return fromGrid != toGrid
    }

    private fun sampleNormalized(colors: List<Color>, t: Float): Color {
        if (colors.size == 1) return colors[0]
        val scaled = t.coerceIn(0f, 1f) * (colors.size - 1)
        val idx = scaled.toInt().coerceIn(0, colors.lastIndex - 1)
        val frac = scaled - idx
        val a = colors[idx]
        val b = colors[idx + 1]
        return Color(
            red = a.red + (b.red - a.red) * frac,
            green = a.green + (b.green - a.green) * frac,
            blue = a.blue + (b.blue - a.blue) * frac,
            alpha = a.alpha + (b.alpha - a.alpha) * frac,
        )
    }

    private fun lerpFloat(a: Float, b: Float, t: Float): Float = a + (b - a) * t
}
