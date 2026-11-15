package com.example.render

import android.os.Build
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.BlendMode
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.StrokeJoin
import androidx.compose.ui.graphics.drawscope.DrawScope
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.drawscope.rotate
import androidx.compose.ui.graphics.drawscope.scale
import androidx.compose.ui.graphics.drawscope.translate
import androidx.compose.ui.graphics.nativeCanvas
import androidx.compose.ui.unit.dp
import com.example.audio.AudioFrame
import com.example.model.VisualStyleType
import kotlin.math.PI
import kotlin.math.cos
import kotlin.math.max
import kotlin.math.min
import kotlin.math.roundToInt
import kotlin.math.sin

data class EvaluatedLayerParams(
    val opacity: Float,
    val scale: Float,
    val rotationDeg: Float,
    val offsetXNorm: Float,
    val offsetYNorm: Float,
    val primary: Float,
    val secondary: Float,
    val tertiary: Float,
    val sliceEnergy: Float,
)

class ParticlePool(capacity: Int = 240) {
    val angles = FloatArray(capacity) { (it * 2.39996f) % (2f * PI.toFloat()) }
    val radii = FloatArray(capacity) { (it % 100) / 100f }
    val speeds = FloatArray(capacity) { 0.3f + ((it * 17) % 100) / 100f }
    val colorMix = FloatArray(capacity) { ((it * 31) % 100) / 100f }
    val sizes = FloatArray(capacity) { 2f + ((it * 13) % 7) }

    fun step(dtSec: Float, burstVel: Float, orbitalPull: Float, beatPulse: Float, sliceEnergy: Float) {
        val boost = 1f + beatPulse * 2.8f + sliceEnergy * 1.4f
        for (i in angles.indices) {
            radii[i] += speeds[i] * burstVel * boost * dtSec * 0.35f
            angles[i] += orbitalPull * dtSec * (1.2f - radii[i] * 0.5f)
            if (radii[i] > 1.2f) {
                radii[i] = 0.02f + (i % 19) * 0.005f
            }
        }
    }
}

object LayerRenderers {

    fun DrawScope.renderLayer(
        styleType: VisualStyleType,
        blendMode: BlendMode,
        params: EvaluatedLayerParams,
        frame: AudioFrame,
        palette: List<Color>,
        timeSec: Float,
        particlePool: ParticlePool,
        agslHolder: Any?,
    ) {
        if (params.opacity <= 0.01f) return
        val cx = size.width * (0.5f + params.offsetXNorm)
        val cy = size.height * (0.5f + params.offsetYNorm)
        val baseRadius = size.minDimension * 0.44f

        translate(left = cx, top = cy) {
            rotate(degrees = params.rotationDeg, pivot = Offset.Zero) {
                scale(scale = params.scale, pivot = Offset.Zero) {
                    when (styleType) {
                        VisualStyleType.OSCILLOSCOPE_RIBBON ->
                            drawOscilloscopeRibbon(params, frame, palette, timeSec, blendMode)
                        VisualStyleType.RADIAL_MANDALA ->
                            drawRadialMandala(params, frame, palette, baseRadius, timeSec, blendMode)
                        VisualStyleType.NEBULA_FLUID ->
                            drawNebulaFluid(params, frame, palette, timeSec, blendMode, agslHolder)
                        VisualStyleType.TUNNEL_WARP_GRID ->
                            drawTunnelWarpGrid(params, frame, palette, baseRadius, timeSec, blendMode)
                        VisualStyleType.PARTICLE_STARBURST ->
                            drawParticleStarburst(params, palette, baseRadius, blendMode, particlePool)
                        VisualStyleType.TERRAIN_HORIZON ->
                            drawTerrainHorizon(params, frame, palette, timeSec, blendMode)
                        VisualStyleType.HARMONIC_SPIROGRAPH ->
                            drawHarmonicSpirograph(params, frame, palette, baseRadius, timeSec, blendMode)
                        VisualStyleType.LIQUID_BLOBS ->
                            drawLiquidBlobs(params, frame, palette, baseRadius, timeSec, blendMode)
                        VisualStyleType.MILK_WAVE_MATRIX ->
                            drawMilkWaveMatrix(params, frame, palette, baseRadius, timeSec, blendMode)
                        VisualStyleType.MILK_VECTOR_FIELD ->
                            drawMilkVectorField(params, frame, palette, baseRadius, timeSec, blendMode)
                        VisualStyleType.MILK_MOTION_SHAPES ->
                            drawMilkMotionShapes(params, frame, palette, baseRadius, timeSec, blendMode)
                        VisualStyleType.MILK_SOLAR_CORONA ->
                            drawMilkSolarCorona(params, frame, palette, baseRadius, timeSec, blendMode)
                    }
                }
            }
        }
    }

    private fun DrawScope.drawOscilloscopeRibbon(
        params: EvaluatedLayerParams,
        frame: AudioFrame,
        palette: List<Color>,
        timeSec: Float,
        blendMode: BlendMode,
    ) {
        val harmonics = params.primary.roundToInt().coerceIn(1, 8)
        val ampScale = params.secondary * (0.5f + params.sliceEnergy * 1.4f)
        val strokePx = params.tertiary.dp.toPx()
        val halfW = size.width * 0.48f
        val halfH = size.height * 0.28f
        val wave = frame.waveform
        val count = wave.size.coerceAtLeast(32)

        for (h in 0 until harmonics) {
            val hRatio = h / max(1, harmonics - 1).toFloat()
            val col = palette.sample(hRatio)
            val path = Path()
            val phaseShift = h * 0.55f + timeSec * (0.8f + h * 0.25f)
            val ampMul = (1f - hRatio * 0.5f) * ampScale

            for (i in 0 until count) {
                val t = i / (count - 1f)
                val x = -halfW + t * (halfW * 2f)
                val sample = wave[i]
                val harmonicRipple = sin(t * PI.toFloat() * (2f + h * 2f) + phaseShift) * (0.08f + params.sliceEnergy * 0.25f)
                val y = (sample * ampMul + harmonicRipple) * halfH + (h - harmonics * 0.5f) * (8f + params.sliceEnergy * 24f)
                if (i == 0) path.moveTo(x, y) else path.lineTo(x, y)
            }

            // Outer glow pass
            drawPath(
                path = path,
                color = col.copy(alpha = (params.opacity * 0.28f).coerceIn(0f, 1f)),
                style = Stroke(width = strokePx * 2.6f, cap = StrokeCap.Round, join = StrokeJoin.Round),
                blendMode = blendMode,
            )
            // Crisp core pass
            drawPath(
                path = path,
                color = col.copy(alpha = (params.opacity * 0.92f).coerceIn(0f, 1f)),
                style = Stroke(width = strokePx * 0.7f, cap = StrokeCap.Round, join = StrokeJoin.Round),
                blendMode = blendMode,
            )
        }
    }

    private fun DrawScope.drawRadialMandala(
        params: EvaluatedLayerParams,
        frame: AudioFrame,
        palette: List<Color>,
        baseRadius: Float,
        timeSec: Float,
        blendMode: BlendMode,
    ) {
        val petals = params.primary.roundToInt().coerceIn(3, 24)
        val innerR = baseRadius * params.secondary.coerceIn(0.05f, 0.75f)
        val flare = params.tertiary
        val bands = frame.bands64
        val stepRad = (2.0 * PI / petals).toFloat()

        // Outer + inner petal rings
        for (ring in 0..1) {
            val path = Path()
            val samplesPerPetal = 10
            val totalSteps = petals * samplesPerPetal
            val ringRadius = innerR * (1f + ring * 0.65f)

            for (i in 0..totalSteps) {
                val idx = i % totalSteps
                val bandIdx = ((idx % (samplesPerPetal * 2)) * bands.size) / (samplesPerPetal * 2)
                val mag = bands[bandIdx.coerceIn(0, bands.lastIndex)]
                val angle = idx * (2f * PI.toFloat() / totalSteps) + timeSec * (0.35f - ring * 0.6f)
                val petalShape = kotlin.math.abs(sin((idx % samplesPerPetal) / samplesPerPetal.toFloat() * PI.toFloat()))
                val r = ringRadius + (mag * 0.75f + params.sliceEnergy * 0.45f) * baseRadius * flare * (0.4f + 0.6f * petalShape)
                val px = cos(angle) * r
                val py = sin(angle) * r
                if (i == 0) path.moveTo(px, py) else path.lineTo(px, py)
            }
            path.close()

            val brush = Brush.sweepGradient(
                colors = palette.map { it.copy(alpha = (params.opacity * (0.85f - ring * 0.25f)).coerceIn(0f, 1f)) } +
                    palette.first().copy(alpha = (params.opacity * (0.85f - ring * 0.25f)).coerceIn(0f, 1f)),
                center = Offset.Zero,
            )
            drawPath(
                path = path,
                brush = brush,
                style = Stroke(width = (3.5f + frame.beatPulse * 4.5f).dp.toPx(), join = StrokeJoin.Round),
                blendMode = blendMode,
            )
            drawPath(
                path = path,
                brush = Brush.radialGradient(
                    colors = listOf(
                        palette.sample(0.2f).copy(alpha = params.opacity * 0.18f),
                        palette.sample(0.8f).copy(alpha = params.opacity * 0.05f),
                        Color.Transparent,
                    ),
                    center = Offset.Zero,
                    radius = baseRadius * 1.3f,
                ),
                blendMode = blendMode,
            )
        }

        // Radial spokes
        for (p in 0 until petals) {
            val ang = p * stepRad + timeSec * 0.25f
            val mag = bands[(p * 3) % bands.size]
            val rEnd = innerR + baseRadius * (0.35f + mag * flare * 0.9f)
            val col = palette.sample(p / petals.toFloat())
            drawLine(
                color = col.copy(alpha = (params.opacity * 0.75f).coerceIn(0f, 1f)),
                start = Offset(cos(ang) * innerR * 0.5f, sin(ang) * innerR * 0.5f),
                end = Offset(cos(ang) * rEnd, sin(ang) * rEnd),
                strokeWidth = (2.0f + mag * 3.5f).dp.toPx(),
                cap = StrokeCap.Round,
                blendMode = blendMode,
            )
        }
    }

    private fun DrawScope.drawNebulaFluid(
        params: EvaluatedLayerParams,
        frame: AudioFrame,
        palette: List<Color>,
        timeSec: Float,
        blendMode: BlendMode,
        agslHolder: Any?,
    ) {
        val canUseRuntimeShader = Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU &&
            agslHolder is AgslShaders.Api33ShaderHolder &&
            drawContext.canvas.nativeCanvas.isHardwareAccelerated
        if (canUseRuntimeShader) {
            val holder = agslHolder as AgslShaders.Api33ShaderHolder
            holder.updateNebulaUniforms(
                width = size.width,
                height = size.height,
                timeSec = timeSec,
                turbulence = params.primary,
                warpFlow = params.secondary,
                glowDensity = params.tertiary,
                bass = max(frame.subBass, params.sliceEnergy),
                treble = frame.treble,
                colorA = palette.sample(0f),
                colorB = palette.sample(0.45f),
                colorC = palette.sample(0.85f),
                opacity = params.opacity,
            )
            // Draw centered rect covering viewport even under scale/rotation
            val span = size.maxDimension * 1.4f
            drawRect(
                brush = holder.nebulaBrush,
                topLeft = Offset(-span * 0.5f, -span * 0.5f),
                size = Size(span, span),
                blendMode = blendMode,
            )
        } else {
            // Smooth multi-lobe gradient plasma fallback for API 24-32
            val clouds = 5
            val maxR = size.maxDimension * 0.65f
            for (i in 0 until clouds) {
                val ang = timeSec * params.secondary * 0.5f + i * (2f * PI.toFloat() / clouds)
                val dist = maxR * 0.22f * (0.5f + params.sliceEnergy)
                val cx = cos(ang) * dist
                val cy = sin(ang * 1.3f) * dist
                val r = maxR * (0.45f + 0.2f * sin(timeSec + i)) * params.tertiary.coerceIn(0.4f, 1.5f)
                val col = palette.sample(i / clouds.toFloat())
                drawCircle(
                    brush = Brush.radialGradient(
                        colors = listOf(
                            col.copy(alpha = (params.opacity * 0.45f).coerceIn(0f, 1f)),
                            Color.Transparent,
                        ),
                        center = Offset(cx, cy),
                        radius = r,
                    ),
                    radius = r,
                    center = Offset(cx, cy),
                    blendMode = blendMode,
                )
            }
        }
    }

    private fun DrawScope.drawTunnelWarpGrid(
        params: EvaluatedLayerParams,
        frame: AudioFrame,
        palette: List<Color>,
        baseRadius: Float,
        timeSec: Float,
        blendMode: BlendMode,
    ) {
        val rings = params.primary.roundToInt().coerceIn(6, 32)
        val warpSpeed = params.secondary
        val twistDeg = params.tertiary
        val sides = 8
        val maxR = size.maxDimension * 0.78f
        val travel = (timeSec * warpSpeed * 0.65f) % 1f

        val spokesPrev = Array(sides) { Offset.Zero }
        var hasPrev = false

        for (rIdx in 0 until rings) {
            val depth = ((rIdx / rings.toFloat()) + travel) % 1f
            val perspective = depth * depth
            val band = frame.bands64[(rIdx * 2) % frame.bands64.size]
            val radius = (0.04f + perspective * 0.96f) * maxR * (1f + band * 0.16f)
            val rotRad = Math.toRadians((twistDeg * (1f - depth)).toDouble()).toFloat()
            val col = palette.sample(depth)
            val alpha = (params.opacity * (0.15f + depth * 0.85f)).coerceIn(0f, 1f)

            val path = Path()
            val currentVerts = Array(sides) { Offset.Zero }
            for (s in 0 until sides) {
                val a = rotRad + s * (2f * PI.toFloat() / sides)
                val wobble = 1f + frame.waveform[(s * 24) % frame.waveform.size] * 0.14f * params.sliceEnergy
                val vx = cos(a) * radius * wobble
                val vy = sin(a) * radius * wobble
                currentVerts[s] = Offset(vx, vy)
                if (s == 0) path.moveTo(vx, vy) else path.lineTo(vx, vy)
            }
            path.close()

            drawPath(
                path = path,
                color = col.copy(alpha = alpha),
                style = Stroke(width = (1.2f + perspective * 3.5f).dp.toPx()),
                blendMode = blendMode,
            )

            if (hasPrev && rIdx > 0) {
                // Radial tunnel struts
                for (s in 0 until sides) {
                    if (currentVerts[s].getDistance() > spokesPrev[s].getDistance()) {
                        drawLine(
                            color = col.copy(alpha = alpha * 0.4f),
                            start = spokesPrev[s],
                            end = currentVerts[s],
                            strokeWidth = 1.2.dp.toPx(),
                            blendMode = blendMode,
                        )
                    }
                }
            }
            for (s in 0 until sides) spokesPrev[s] = currentVerts[s]
            hasPrev = true
        }
    }

    private fun DrawScope.drawParticleStarburst(
        params: EvaluatedLayerParams,
        palette: List<Color>,
        baseRadius: Float,
        blendMode: BlendMode,
        pool: ParticlePool,
    ) {
        val count = params.primary.roundToInt().coerceIn(24, pool.angles.size)
        val maxR = size.maxDimension * 0.6f
        for (i in 0 until count) {
            val r = pool.radii[i] * maxR
            val ang = pool.angles[i]
            val px = cos(ang) * r
            val py = sin(ang) * r
            val tailR = (r - 12f - params.secondary * 18f).coerceAtLeast(0f)
            val tx = cos(ang) * tailR
            val ty = sin(ang) * tailR
            val col = palette.sample(pool.colorMix[i])
            val fade = (1f - (pool.radii[i] / 1.2f)).coerceIn(0f, 1f)
            val alpha = (params.opacity * fade).coerceIn(0f, 1f)

            drawLine(
                color = col.copy(alpha = alpha * 0.65f),
                start = Offset(tx, ty),
                end = Offset(px, py),
                strokeWidth = (pool.sizes[i] * 0.55f).dp.toPx(),
                cap = StrokeCap.Round,
                blendMode = blendMode,
            )
            drawCircle(
                color = col.copy(alpha = alpha),
                radius = (pool.sizes[i] * (0.6f + params.sliceEnergy * 0.9f)).dp.toPx(),
                center = Offset(px, py),
                blendMode = blendMode,
            )
        }
    }

    private fun DrawScope.drawTerrainHorizon(
        params: EvaluatedLayerParams,
        frame: AudioFrame,
        palette: List<Color>,
        timeSec: Float,
        blendMode: BlendMode,
    ) {
        val rows = params.primary.roundToInt().coerceIn(6, 28)
        val peakHeight = params.secondary * size.height * 0.14f
        val tilt = params.tertiary
        val halfW = size.width * 0.52f
        val topY = -size.height * 0.25f * tilt
        val bottomY = size.height * 0.45f

        for (row in 0 until rows) {
            val depth = row / (rows - 1f).coerceAtLeast(1f)
            val scrollOffset = ((timeSec * 0.6f + depth) % 1f)
            val yBase = topY + (bottomY - topY) * (scrollOffset * scrollOffset)
            val rowWidth = halfW * (0.35f + scrollOffset * 0.85f)
            val col = palette.sample(scrollOffset)
            val alpha = (params.opacity * (0.25f + scrollOffset * 0.75f)).coerceIn(0f, 1f)

            val path = Path()
            val cols = 32
            for (c in 0..cols) {
                val u = c / cols.toFloat()
                val x = -rowWidth + u * rowWidth * 2f
                val bandIdx = ((1f - kotlin.math.abs(u - 0.5f) * 2f) * 48f).toInt().coerceIn(0, frame.bands64.lastIndex)
                val ridge = frame.bands64[bandIdx] * peakHeight * (0.4f + scrollOffset * 0.8f)
                val y = yBase - ridge
                if (c == 0) path.moveTo(x, y) else path.lineTo(x, y)
            }
            drawPath(
                path = path,
                color = col.copy(alpha = alpha),
                style = Stroke(width = (1.5f + scrollOffset * 2.2f).dp.toPx(), join = StrokeJoin.Round),
                blendMode = blendMode,
            )
        }
    }

    private fun DrawScope.drawHarmonicSpirograph(
        params: EvaluatedLayerParams,
        frame: AudioFrame,
        palette: List<Color>,
        baseRadius: Float,
        timeSec: Float,
        blendMode: BlendMode,
    ) {
        val ratio = params.primary.coerceIn(2f, 14f)
        val lobeDepth = params.secondary
        val steps = params.tertiary.roundToInt().coerceIn(80, 360)
        val R = baseRadius * 0.62f
        val r = R / ratio
        val d = r * (0.8f + lobeDepth * (0.8f + params.sliceEnergy * 1.2f))

        val path = Path()
        val phase = timeSec * 0.8f
        for (i in 0..steps) {
            val theta = (i / steps.toFloat()) * 4f * PI.toFloat() + phase
            val waveMod = 1f + frame.waveform[i % frame.waveform.size] * 0.18f * params.sliceEnergy
            val x = ((R - r) * cos(theta) + d * cos(((R - r) / r) * theta)) * waveMod
            val y = ((R - r) * sin(theta) - d * sin(((R - r) / r) * theta)) * waveMod
            if (i == 0) path.moveTo(x, y) else path.lineTo(x, y)
        }

        val brush = Brush.sweepGradient(
            colors = palette.map { it.copy(alpha = params.opacity) } + palette.first().copy(alpha = params.opacity),
            center = Offset.Zero,
        )
        drawPath(
            path = path,
            brush = brush,
            style = Stroke(width = (2.5f + frame.beatPulse * 3.5f).dp.toPx(), join = StrokeJoin.Round),
            blendMode = blendMode,
        )
    }

    private fun DrawScope.drawLiquidBlobs(
        params: EvaluatedLayerParams,
        frame: AudioFrame,
        palette: List<Color>,
        baseRadius: Float,
        timeSec: Float,
        blendMode: BlendMode,
    ) {
        val count = params.primary.roundToInt().coerceIn(3, 16)
        val wobble = params.secondary
        val radiusMul = params.tertiary
        for (i in 0 until count) {
            val t = i / count.toFloat()
            val band = frame.bands64[(i * 4) % frame.bands64.size]
            val orbitAng = timeSec * (0.4f + t * 0.5f) + i * (2f * PI.toFloat() / count)
            val orbitDist = baseRadius * (0.28f + 0.35f * sin(timeSec * 0.7f + i)) * wobble
            val cx = cos(orbitAng) * orbitDist
            val cy = sin(orbitAng * 1.15f) * orbitDist
            val r = baseRadius * (0.22f + band * 0.45f) * radiusMul
            val col = palette.sample(t)

            drawCircle(
                brush = Brush.radialGradient(
                    colors = listOf(
                        col.copy(alpha = (params.opacity * 0.78f).coerceIn(0f, 1f)),
                        col.copy(alpha = (params.opacity * 0.25f).coerceIn(0f, 1f)),
                        Color.Transparent,
                    ),
                    center = Offset(cx, cy),
                    radius = r,
                ),
                radius = r,
                center = Offset(cx, cy),
                blendMode = blendMode,
            )
        }
    }

    private fun DrawScope.drawMilkWaveMatrix(
        params: EvaluatedLayerParams,
        frame: AudioFrame,
        palette: List<Color>,
        baseRadius: Float,
        timeSec: Float,
        blendMode: BlendMode,
    ) {
        val mode = params.primary.roundToInt().coerceIn(0, 4)
        val mystery = params.secondary
        val glowPx = params.tertiary.dp.toPx()
        val wave = frame.waveform
        val count = wave.size.coerceAtLeast(64)
        val path = Path()

        for (i in 0 until count) {
            val t = i / (count - 1f)
            val s = wave[i]
            val sLag = wave[(i + 32) % count]
            val col = palette.sample(t)
            val alpha = (params.opacity * 0.9f).coerceIn(0f, 1f)

            val pt = when (mode) {
                0 -> {
                    // Classic MilkDrop radial oscilloscope circle (wave_mode=0)
                    val ang = t * 2f * PI.toFloat() + timeSec * 0.4f
                    val r = baseRadius * (0.45f + s * 0.45f * mystery + params.sliceEnergy * 0.25f)
                    Offset(cos(ang) * r, sin(ang) * r)
                }
                1 -> {
                    // Dual Lissajous X-Y phase ribbon (wave_mode=1)
                    val ang = t * 2f * PI.toFloat() * mystery + timeSec * 0.5f
                    val x = (s * 0.65f + cos(ang) * 0.35f) * baseRadius * 1.15f
                    val y = (sLag * 0.65f + sin(ang * 1.5f) * 0.35f) * baseRadius * 1.15f
                    Offset(x, y)
                }
                2 -> {
                    // Symmetric butterfly double-mirror wave (wave_mode=2/4)
                    val x = (t - 0.5f) * size.width * 0.85f
                    val y = s * baseRadius * 0.85f * mystery * sin(t * PI.toFloat())
                    Offset(x, y)
                }
                3 -> {
                    // Dotted starburst spiral wave (wave_mode=7)
                    val ang = t * 4f * PI.toFloat() * mystery + timeSec * 0.6f
                    val r = baseRadius * (0.12f + t * 0.95f) * (1f + s * 0.4f)
                    Offset(cos(ang) * r, sin(ang) * r)
                }
                else -> {
                    // Polar flower burst
                    val ang = t * 2f * PI.toFloat() + timeSec * 0.3f
                    val r = baseRadius * (0.35f + 0.55f * kotlin.math.abs(cos(ang * (2f + mystery))) + s * 0.35f)
                    Offset(cos(ang) * r, sin(ang) * r)
                }
            }

            if (mode == 3) {
                drawCircle(
                    color = col.copy(alpha = alpha),
                    radius = (glowPx * (0.35f + kotlin.math.abs(s))).coerceAtLeast(1.5f),
                    center = pt,
                    blendMode = blendMode,
                )
            } else {
                if (i == 0) path.moveTo(pt.x, pt.y) else path.lineTo(pt.x, pt.y)
            }
        }

        if (mode != 3) {
            if (mode == 0 || mode == 4) path.close()
            val brush = Brush.sweepGradient(
                colors = palette.map { it.copy(alpha = params.opacity) } + palette.first().copy(alpha = params.opacity),
                center = Offset.Zero,
            )
            drawPath(
                path = path,
                brush = brush,
                style = Stroke(width = glowPx * 1.4f, cap = StrokeCap.Round, join = StrokeJoin.Round),
                blendMode = blendMode,
            )
            drawPath(
                path = path,
                color = Color.White.copy(alpha = (params.opacity * 0.65f).coerceIn(0f, 1f)),
                style = Stroke(width = (glowPx * 0.35f).coerceAtLeast(1.2f), cap = StrokeCap.Round, join = StrokeJoin.Round),
                blendMode = blendMode,
            )
        }
    }

    private fun DrawScope.drawMilkVectorField(
        params: EvaluatedLayerParams,
        frame: AudioFrame,
        palette: List<Color>,
        baseRadius: Float,
        timeSec: Float,
        blendMode: BlendMode,
    ) {
        val count = params.primary.roundToInt().coerceIn(18, 120)
        val curlForce = params.secondary
        val trailLen = params.tertiary * baseRadius * 0.24f
        val cols = 10
        val rows = (count / cols).coerceAtLeast(3)

        for (r in 0 until rows) {
            for (c in 0 until cols) {
                val u = (c + 0.5f) / cols - 0.5f
                val v = (r + 0.5f) / rows - 0.5f
                val sx = u * size.width * 0.88f
                val sy = v * size.height * 0.78f
                val band = frame.bands64[(r * cols + c) % frame.bands64.size]
                val ang = sin(u * 4.5f * curlForce + timeSec * 1.1f) * PI.toFloat() +
                    cos(v * 4.5f * curlForce - timeSec * 0.9f) * PI.toFloat() * 0.6f +
                    band * 2.5f
                val len = trailLen * (0.4f + band * 1.4f + params.sliceEnergy * 0.6f)
                val ex = sx + cos(ang) * len
                val ey = sy + sin(ang) * len
                val mx = (sx + ex) * 0.5f + cos(ang + 1.1f) * len * 0.28f
                val my = (sy + ey) * 0.5f + sin(ang + 1.1f) * len * 0.28f

                val path = Path().apply {
                    moveTo(sx, sy)
                    quadraticTo(mx, my, ex, ey)
                }
                val col = palette.sample((r + c) / (rows + cols).toFloat())
                drawPath(
                    path = path,
                    color = col.copy(alpha = (params.opacity * (0.35f + band * 0.65f)).coerceIn(0f, 1f)),
                    style = Stroke(width = (1.6f + band * 3.5f).dp.toPx(), cap = StrokeCap.Round),
                    blendMode = blendMode,
                )
            }
        }
    }

    private fun DrawScope.drawMilkMotionShapes(
        params: EvaluatedLayerParams,
        frame: AudioFrame,
        palette: List<Color>,
        baseRadius: Float,
        timeSec: Float,
        blendMode: BlendMode,
    ) {
        val sides = params.primary.roundToInt().coerceIn(3, 12)
        val nested = params.secondary.roundToInt().coerceIn(1, 7)
        val twistDeg = params.tertiary

        for (n in 0 until nested) {
            val ratio = (n + 1) / nested.toFloat()
            val band = frame.bands64[(n * 8) % frame.bands64.size]
            val r = baseRadius * (0.2f + ratio * 0.72f) * (1f + band * 0.28f + frame.beatPulse * 0.16f)
            val rotRad = Math.toRadians((twistDeg * ratio + timeSec * (18f - n * 6f)).toDouble()).toFloat()
            val col = palette.sample(ratio)

            val path = Path()
            for (s in 0 until sides) {
                val a = rotRad + s * (2f * PI.toFloat() / sides)
                val px = cos(a) * r
                val py = sin(a) * r
                if (s == 0) path.moveTo(px, py) else path.lineTo(px, py)
            }
            path.close()

            drawPath(
                path = path,
                brush = Brush.radialGradient(
                    colors = listOf(
                        col.copy(alpha = (params.opacity * 0.24f).coerceIn(0f, 1f)),
                        Color.Transparent,
                    ),
                    center = Offset.Zero,
                    radius = r,
                ),
                blendMode = blendMode,
            )
            drawPath(
                path = path,
                color = col.copy(alpha = (params.opacity * 0.9f).coerceIn(0f, 1f)),
                style = Stroke(width = (2.2f + band * 4.5f).dp.toPx(), join = StrokeJoin.Round),
                blendMode = blendMode,
            )
        }
    }

    private fun DrawScope.drawMilkSolarCorona(
        params: EvaluatedLayerParams,
        frame: AudioFrame,
        palette: List<Color>,
        baseRadius: Float,
        timeSec: Float,
        blendMode: BlendMode,
    ) {
        val arcs = params.primary.roundToInt().coerceIn(8, 48)
        val reach = params.secondary
        val curvature = params.tertiary
        val innerR = baseRadius * 0.24f

        for (i in 0 until arcs) {
            val t = i / arcs.toFloat()
            val band = frame.bands64[(i * 64 / arcs) % frame.bands64.size]
            val a0 = t * 2f * PI.toFloat() + timeSec * 0.35f
            val a1 = a0 + curvature * 0.65f + (band - 0.3f) * 0.4f
            val outerR = innerR + baseRadius * (0.28f + band * 0.95f * reach + params.sliceEnergy * 0.35f)
            val sx = cos(a0) * innerR
            val sy = sin(a0) * innerR
            val ex = cos(a1) * outerR
            val ey = sin(a1) * outerR
            val cx = cos((a0 + a1) * 0.5f + curvature * 0.5f) * (innerR + outerR) * 0.55f
            val cy = sin((a0 + a1) * 0.5f + curvature * 0.5f) * (innerR + outerR) * 0.55f

            val path = Path().apply {
                moveTo(sx, sy)
                quadraticTo(cx, cy, ex, ey)
            }
            val col = palette.sample(t)
            drawPath(
                path = path,
                color = col.copy(alpha = (params.opacity * (0.4f + band * 0.6f)).coerceIn(0f, 1f)),
                style = Stroke(width = (1.8f + band * 4.0f).dp.toPx(), cap = StrokeCap.Round),
                blendMode = blendMode,
            )
        }
    }

    private fun List<Color>.sample(t: Float): Color {
        if (isEmpty()) return Color.Cyan
        val w = ((t % 1f) + 1f) % 1f
        val idx = (w * (size - 1)).toInt().coerceIn(0, lastIndex)
        return this[idx]
    }
}
