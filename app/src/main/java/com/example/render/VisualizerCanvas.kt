package com.example.render

import android.os.Build
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.gestures.detectTransformGestures
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableLongStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.runtime.withFrameNanos
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.BlendMode
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.ImageBitmap
import androidx.compose.ui.graphics.ImageBitmapConfig
import androidx.compose.ui.graphics.asComposeRenderEffect
import androidx.compose.ui.graphics.drawscope.CanvasDrawScope
import androidx.compose.ui.graphics.drawscope.DrawScope
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.drawscope.clipRect
import androidx.compose.ui.graphics.drawscope.rotate
import androidx.compose.ui.graphics.drawscope.scale
import androidx.compose.ui.graphics.drawscope.translate
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.unit.IntSize
import androidx.compose.ui.unit.LayoutDirection
import androidx.compose.ui.unit.dp
import com.example.audio.AudioFrame
import com.example.model.ColorEngineConfig
import com.example.model.ModulationEvaluator
import com.example.model.TransformerNode
import com.example.model.TransformerType
import com.example.model.VisualLayer
import com.example.ui.theme.VoidObsidian
import kotlin.math.PI
import kotlin.math.cos
import kotlin.math.roundToInt
import kotlin.math.sin
import kotlin.random.Random

/**
 * Live 60fps composited visualizer canvas supporting:
 * - Multi-layer overlapping visual generators with BlendModes & per-layer Hz band filters
 * - Offscreen ping-pong echo buffer for Feedback Echo Trails
 * - Overlapping spatial & wave transformers (Kaleidoscope, Tile Mosaic, Vortex, WaveFold, Chromatic Split, Ripples, Bloom, CRT)
 * - Tactile pinch/pan focal point steering & tap-to-Zen toggle
 */
@Composable
fun VisualizerCanvas(
    layers: List<VisualLayer>,
    transformers: List<TransformerNode>,
    colorEngine: ColorEngineConfig,
    audioFrameProvider: () -> AudioFrame,
    onLiveEvaluatedValues: (Map<String, Float>) -> Unit,
    onTapCanvas: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val evaluator = remember { ModulationEvaluator() }
    val particlePool = remember { ParticlePool(240) }
    val agslHolder = remember {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            AgslShaders.Api33ShaderHolder()
        } else {
            null
        }
    }

    var frameTick by remember { mutableLongStateOf(0L) }
    var elapsedSec by remember { mutableFloatStateOf(0f) }
    var accumulatedHueDeg by remember { mutableFloatStateOf(0f) }
    val layerRotations = remember { HashMap<String, Float>() }

    // Interactive pinch/pan focal steering
    var userZoom by remember { mutableFloatStateOf(1f) }
    var userPanOffset by remember { mutableStateOf(Offset.Zero) }

    // Offscreen ping-pong buffer for feedback echo trails
    var echoFront by remember { mutableStateOf<ImageBitmap?>(null) }
    var echoBack by remember { mutableStateOf<ImageBitmap?>(null) }
    val offscreenDrawScope = remember { CanvasDrawScope() }
    val density = LocalDensity.current

    // Snapshot holders updated every frame for zero-recomposition DrawScope reads
    var currentFrame by remember { mutableStateOf(AudioFrame()) }
    var evaluatedLayerList by remember { mutableStateOf<List<Pair<VisualLayer, EvaluatedLayerParams>>>(emptyList()) }
    var evaluatedPalette by remember { mutableStateOf<List<Color>>(emptyList()) }
    var activeTxValues by remember { mutableStateOf<Map<TransformerType, Triple<Float, Float, Float>>>(emptyMap()) }
    var lastDtSec by remember { mutableFloatStateOf(0.016f) }

    LaunchedEffect(layers, transformers, colorEngine) {
        var prevNanos = 0L
        while (true) {
            withFrameNanos { nanos ->
                val dt = if (prevNanos == 0L) 0.016f else ((nanos - prevNanos) / 1_000_000_000f).coerceIn(0.001f, 0.05f)
                prevNanos = nanos
                lastDtSec = dt
                elapsedSec += dt

                val audio = audioFrameProvider()
                currentFrame = audio

                val liveTelemetryMap = HashMap<String, Float>(48)

                // Evaluate color engine
                val hueBase = evaluator.evaluate("c_hue", colorEngine.hueShift, audio, audio.overallRms, elapsedSec, dt)
                val hueDrift = evaluator.evaluate("c_drift", colorEngine.hueDriftSpeed, audio, audio.overallRms, elapsedSec, dt)
                val sat = evaluator.evaluate("c_sat", colorEngine.saturation, audio, audio.overallRms, elapsedSec, dt)
                val luma = evaluator.evaluate("c_luma", colorEngine.contrast, audio, audio.overallRms, elapsedSec, dt)
                val flash = evaluator.evaluate("c_flash", colorEngine.beatFlash, audio, audio.beatPulse, elapsedSec, dt)
                accumulatedHueDeg = (accumulatedHueDeg + hueDrift * dt) % 360f

                liveTelemetryMap[colorEngine.hueShift.id] = hueBase
                liveTelemetryMap[colorEngine.hueDriftSpeed.id] = hueDrift
                liveTelemetryMap[colorEngine.saturation.id] = sat
                liveTelemetryMap[colorEngine.contrast.id] = luma
                liveTelemetryMap[colorEngine.beatFlash.id] = flash

                val basePalette = ColorEngine.activeBaseColors(colorEngine)
                evaluatedPalette = ColorEngine.transformPalette(
                    colors = basePalette,
                    hueShiftDeg = hueBase + accumulatedHueDeg,
                    saturationMul = sat,
                    lumaBoost = luma,
                    beatFlash = flash,
                )

                // Evaluate layers (respect solo if any layer is soloed)
                val anySolo = layers.any { it.solo && it.enabled }
                val activeLayers = layers.filter { it.enabled && (!anySolo || it.solo) }
                evaluatedLayerList = activeLayers.map { layer ->
                    val slice = evaluator.evaluateAudioSlice(audio, layer.audioFilter)
                    val op = evaluator.evaluate("${layer.id}_op", layer.opacity, audio, slice, elapsedSec, dt)
                    val sc = evaluator.evaluate("${layer.id}_sc", layer.scale, audio, slice, elapsedSec, dt)
                    val rotSpeed = evaluator.evaluate("${layer.id}_rot", layer.rotationSpeed, audio, slice, elapsedSec, dt)
                    val ox = evaluator.evaluate("${layer.id}_ox", layer.offsetX, audio, slice, elapsedSec, dt)
                    val oy = evaluator.evaluate("${layer.id}_oy", layer.offsetY, audio, slice, elapsedSec, dt)
                    val p1 = evaluator.evaluate("${layer.id}_p1", layer.primaryParam, audio, slice, elapsedSec, dt)
                    val p2 = evaluator.evaluate("${layer.id}_p2", layer.secondaryParam, audio, slice, elapsedSec, dt)
                    val p3 = evaluator.evaluate("${layer.id}_p3", layer.tertiaryParam, audio, slice, elapsedSec, dt)

                    val prevAngle = layerRotations[layer.id] ?: 0f
                    val nextAngle = (prevAngle + rotSpeed * dt) % 360f
                    layerRotations[layer.id] = nextAngle

                    liveTelemetryMap[layer.opacity.id] = op
                    liveTelemetryMap[layer.scale.id] = sc
                    liveTelemetryMap[layer.rotationSpeed.id] = rotSpeed
                    liveTelemetryMap[layer.offsetX.id] = ox
                    liveTelemetryMap[layer.offsetY.id] = oy
                    liveTelemetryMap[layer.primaryParam.id] = p1
                    liveTelemetryMap[layer.secondaryParam.id] = p2
                    liveTelemetryMap[layer.tertiaryParam.id] = p3

                    if (layer.styleType == com.example.model.VisualStyleType.PARTICLE_STARBURST) {
                        particlePool.step(dt, p2, p3, audio.beatPulse, slice)
                    }

                    layer to EvaluatedLayerParams(
                        opacity = op,
                        scale = sc,
                        rotationDeg = nextAngle,
                        offsetXNorm = ox,
                        offsetYNorm = oy,
                        primary = p1,
                        secondary = p2,
                        tertiary = p3,
                        sliceEnergy = slice,
                    )
                }

                // Evaluate transformers
                val txMap = HashMap<TransformerType, Triple<Float, Float, Float>>()
                transformers.filter { it.enabled }.forEach { node ->
                    val slice = evaluator.evaluateAudioSlice(audio, node.audioFilter)
                    val amt = evaluator.evaluate("${node.id}_amt", node.amount, audio, slice, elapsedSec, dt)
                    val freq = evaluator.evaluate("${node.id}_freq", node.frequency, audio, slice, elapsedSec, dt)
                    val drift = evaluator.evaluate("${node.id}_drift", node.phaseOrDrift, audio, slice, elapsedSec, dt)

                    liveTelemetryMap[node.amount.id] = amt
                    liveTelemetryMap[node.frequency.id] = freq
                    liveTelemetryMap[node.phaseOrDrift.id] = drift

                    txMap[node.type] = Triple(amt, freq, drift)
                }
                activeTxValues = txMap
                onLiveEvaluatedValues(liveTelemetryMap)
                frameTick = nanos
            }
        }
    }

    Box(
        modifier = modifier
            .fillMaxSize()
            .background(VoidObsidian)
            .pointerInput(Unit) {
                detectTapGestures(
                    onTap = { onTapCanvas() },
                    onDoubleTap = {
                        userZoom = 1f
                        userPanOffset = Offset.Zero
                    },
                )
            }
            .pointerInput(Unit) {
                detectTransformGestures { _, pan, zoom, _ ->
                    userZoom = (userZoom * zoom).coerceIn(0.6f, 2.2f)
                    userPanOffset = Offset(
                        x = (userPanOffset.x + pan.x).coerceIn(-300f, 300f),
                        y = (userPanOffset.y + pan.y).coerceIn(-300f, 300f),
                    )
                }
            },
    ) {
        val bloomTriple = activeTxValues[TransformerType.BEAT_BLOOM_STROBE]
        val shakeMag = bloomTriple?.second ?: 0f
        val shakeX = if (shakeMag > 0.5f) (Random.nextFloat() * 2f - 1f) * shakeMag * currentFrame.beatPulse else 0f
        val shakeY = if (shakeMag > 0.5f) (Random.nextFloat() * 2f - 1f) * shakeMag * currentFrame.beatPulse else 0f

        Canvas(
            modifier = Modifier
                .fillMaxSize()
                .graphicsLayer {
                    if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU && agslHolder != null) {
                        val wf = activeTxValues[TransformerType.WAVE_FOLD_WARP]
                        val vor = activeTxValues[TransformerType.VORTEX_SWIRL]
                        val rip = activeTxValues[TransformerType.RIPPLE_INTERFERENCE]
                        val rgb = activeTxValues[TransformerType.CHROMATIC_SPLIT]
                        val crt = activeTxValues[TransformerType.CRT_SCANLINE_WARP]
                        val milkWarp = activeTxValues[TransformerType.MILK_PER_PIXEL_WARP]
                        val tunnelPull = activeTxValues[TransformerType.MILK_RADIAL_TUNNEL_PULL]
                        val stretch = activeTxValues[TransformerType.MILK_ANAMORPHIC_STRETCH]
                        val pinwheel = activeTxValues[TransformerType.MILK_ANGULAR_PINWHEEL]
                        val quad = activeTxValues[TransformerType.MILK_MIRROR_QUAD]
                        val polar = activeTxValues[TransformerType.MILK_POLAR_UNWRAP]
                        val solarize = activeTxValues[TransformerType.MILK_EDGE_SOLARIZE]
                        val posterize = activeTxValues[TransformerType.MILK_POSTERIZE_QUANTIZE]

                        val stretchAmt = stretch?.first ?: 1f
                        val sx = stretchAmt
                        val sy = if (stretchAmt > 0.01f) 1f / stretchAmt else 1f

                        val effect = agslHolder.buildCompositeRenderEffect(
                            width = size.width,
                            height = size.height,
                            timeSec = elapsedSec,
                            waveFoldAmp = wf?.first ?: 0f,
                            waveFoldFreq = wf?.second ?: 6f,
                            waveFoldSpeed = wf?.third ?: 1f,
                            vortexTwist = vor?.first ?: 0f,
                            vortexRadius = vor?.second ?: 0.8f,
                            vortexOrbit = vor?.third ?: 0f,
                            rippleAmp = rip?.first ?: 0f,
                            rippleFreq = rip?.second ?: 10f,
                            rippleSpeed = rip?.third ?: 1.5f,
                            chromaticPx = rgb?.first ?: 0f,
                            chromaticAngleRad = Math.toRadians((rgb?.second ?: 0f).toDouble()).toFloat(),
                            chromaticSpread = rgb?.third ?: 1f,
                            crtDepth = crt?.first ?: 0f,
                            crtSpacing = crt?.second ?: 6f,
                            crtBarrel = crt?.third ?: 0f,
                            shakeX = shakeX,
                            shakeY = shakeY,
                            milkWarpAmt = milkWarp?.first ?: 0f,
                            milkWarpScale = milkWarp?.second ?: 4f,
                            milkWarpSpeed = milkWarp?.third ?: 1f,
                            tunnelZoom = tunnelPull?.first ?: 1f,
                            tunnelZoomExp = tunnelPull?.second ?: 1f,
                            tunnelPulse = tunnelPull?.third ?: 0f,
                            stretchX = sx,
                            stretchY = sy,
                            pinwheelRot = pinwheel?.first ?: 0f,
                            pinwheelRadFreq = pinwheel?.second ?: 4f,
                            pinwheelPetals = pinwheel?.third ?: 4f,
                            quadMirrorBlend = quad?.first ?: 0f,
                            quadOffset = quad?.second ?: 0f,
                            quadInvert = quad?.third ?: 0f,
                            polarBlend = polar?.first ?: 0f,
                            polarArms = polar?.second ?: 3f,
                            polarSpin = polar?.third ?: 0f,
                            solarizeMix = solarize?.first ?: 0f,
                            solarizeFreq = solarize?.second ?: 2.5f,
                            solarizePhase = solarize?.third ?: 0f,
                            posterizeMix = posterize?.first ?: 0f,
                            posterizeLevels = posterize?.second ?: 6f,
                            posterizeEdge = posterize?.third ?: 0.5f,
                        )
                        renderEffect = effect?.asComposeRenderEffect()
                    }
                },
        ) {
            // Read frameTick so DrawScope invalidates every animation frame
            val tick = frameTick
            if (tick < 0L) return@Canvas

            val wPx = size.width.roundToInt().coerceAtLeast(1)
            val hPx = size.height.roundToInt().coerceAtLeast(1)
            val echoConfig = activeTxValues[TransformerType.FEEDBACK_ECHO]?.takeIf { it.first > 0.02f }

            // Helper lambda to draw all overlapping generator layers with spatial transforms
            fun DrawScope.drawSceneContent(targetSize: Size) {
                val kaleido = activeTxValues[TransformerType.KALEIDOSCOPE_MIRROR]
                val tile = activeTxValues[TransformerType.KALEIDO_TILE_MOSAIC]

                fun DrawScope.renderGenerators() {
                    translate(left = userPanOffset.x, top = userPanOffset.y) {
                        scale(scale = userZoom, pivot = Offset(targetSize.width * 0.5f, targetSize.height * 0.5f)) {
                            evaluatedLayerList.forEach { (layer, params) ->
                                with(LayerRenderers) {
                                    renderLayer(
                                        styleType = layer.styleType,
                                        blendMode = layer.blendMode.composeBlendMode,
                                        params = params,
                                        frame = currentFrame,
                                        palette = evaluatedPalette,
                                        timeSec = elapsedSec,
                                        particlePool = particlePool,
                                        agslHolder = agslHolder,
                                    )
                                }
                            }
                        }
                    }
                }

                if (kaleido != null && kaleido.first >= 2f) {
                    val folds = kaleido.first.roundToInt().coerceIn(2, 24)
                    val mirrorAngle = kaleido.second
                    val wedgeZoom = kaleido.third
                    val stepDeg = 360f / folds
                    val cx = targetSize.width * 0.5f
                    val cy = targetSize.height * 0.5f

                    for (f in 0 until folds) {
                        rotate(degrees = mirrorAngle + f * stepDeg, pivot = Offset(cx, cy)) {
                            scale(
                                scaleX = if (f % 2 == 1) -wedgeZoom else wedgeZoom,
                                scaleY = wedgeZoom,
                                pivot = Offset(cx, cy),
                            ) {
                                clipRect(left = cx, top = 0f, right = targetSize.width, bottom = targetSize.height) {
                                    renderGenerators()
                                }
                            }
                        }
                    }
                } else if (tile != null && tile.first >= 1.5f) {
                    val grid = tile.first.roundToInt().coerceIn(2, 6)
                    val marginFrac = tile.second
                    val flipOdd = tile.third > 0.5f
                    val cellW = targetSize.width / grid
                    val cellH = targetSize.height / grid
                    for (gx in 0 until grid) {
                        for (gy in 0 until grid) {
                            val left = gx * cellW
                            val top = gy * cellH
                            val padX = cellW * marginFrac
                            val padY = cellH * marginFrac
                            clipRect(left + padX, top + padY, left + cellW - padX, top + cellH - padY) {
                                translate(left = left - targetSize.width * 0.5f + cellW * 0.5f, top = top - targetSize.height * 0.5f + cellH * 0.5f) {
                                    val sx = (1f / grid) * if (flipOdd && gx % 2 == 1) -1f else 1f
                                    val sy = (1f / grid) * if (flipOdd && gy % 2 == 1) -1f else 1f
                                    scale(scaleX = sx, scaleY = sy, pivot = Offset(targetSize.width * 0.5f, targetSize.height * 0.5f)) {
                                        renderGenerators()
                                    }
                                }
                            }
                        }
                    }
                } else {
                    renderGenerators()
                }
            }

            if (echoConfig != null) {
                val targetW = (wPx / 2).coerceIn(180, 960)
                val targetH = (hPx / 2).coerceIn(180, 960)
                if (echoFront == null || echoFront?.width != targetW || echoFront?.height != targetH) {
                    echoFront = ImageBitmap(targetW, targetH, ImageBitmapConfig.Argb8888)
                    echoBack = ImageBitmap(targetW, targetH, ImageBitmapConfig.Argb8888)
                }

                val front = echoFront!!
                val back = echoBack!!
                val offCanvas = androidx.compose.ui.graphics.Canvas(back)
                val decay = echoConfig.first.coerceIn(0f, 0.96f)
                val zoomDrift = echoConfig.second.coerceIn(0.9f, 1.1f)
                val spiralDeg = echoConfig.third

                val stretch = activeTxValues[TransformerType.MILK_ANAMORPHIC_STRETCH]
                val stretchAng = Math.toRadians((stretch?.second ?: 0f).toDouble()).toFloat()
                val panSpeed = stretch?.third ?: 0f

                // Offscreen Canvas(ImageBitmap) is always software-backed, so keep feedback drawing purely Canvas/matrix-based.
                // On API 33+, AGSL MilkDrop warps/zoomexp/pinwheel run safely on the GPU via graphicsLayer RenderEffect above.
                offscreenDrawScope.draw(
                    density = density,
                    layoutDirection = LayoutDirection.Ltr,
                    canvas = offCanvas,
                    size = Size(targetW.toFloat(), targetH.toFloat()),
                ) {
                    drawRect(VoidObsidian)
                    translate(
                        left = cos(stretchAng) * panSpeed * targetW * 0.5f,
                        top = sin(stretchAng) * panSpeed * targetH * 0.5f,
                    ) {
                        rotate(degrees = spiralDeg, pivot = Offset(targetW * 0.5f, targetH * 0.5f)) {
                            scale(scaleX = zoomDrift, scaleY = zoomDrift, pivot = Offset(targetW * 0.5f, targetH * 0.5f)) {
                                drawImage(
                                    image = front,
                                    alpha = decay,
                                )
                            }
                        }
                    }
                    drawSceneContent(Size(targetW.toFloat(), targetH.toFloat()))
                }

                // Swap buffers & blit to screen
                echoFront = back
                echoBack = front

                drawImage(
                    image = back,
                    dstSize = IntSize(wPx, hPx),
                )
            } else {
                drawRect(VoidObsidian)
                drawSceneContent(size)
            }

            // Software overlay effects (work identically across all API levels 24..36)
            // 1. Ripple Shockwaves (geometric pulse rings on Canvas in addition to AGSL refraction)
            val ripple = activeTxValues[TransformerType.RIPPLE_INTERFERENCE]
            if (ripple != null && ripple.first > 0.05f) {
                val ringCount = (ripple.second * 0.4f).roundToInt().coerceIn(2, 10)
                val maxR = size.maxDimension * 0.65f
                val cx = size.width * 0.5f
                val cy = size.height * 0.5f
                for (r in 0 until ringCount) {
                    val phase = ((elapsedSec * ripple.third * 0.45f) + r / ringCount.toFloat()) % 1f
                    val col = evaluatedPalette.getOrElse(r % evaluatedPalette.size.coerceAtLeast(1)) { Color.Cyan }
                    drawCircle(
                        color = col.copy(alpha = ((1f - phase) * ripple.first * 0.45f).coerceIn(0f, 0.65f)),
                        radius = phase * maxR,
                        center = Offset(cx, cy),
                        style = Stroke(width = (1.5f + currentFrame.beatPulse * 4f).dp.toPx()),
                        blendMode = BlendMode.Screen,
                    )
                }
            }

            // 2. Beat Bloom & Strobe glow halo + subtle flash
            if (bloomTriple != null && bloomTriple.first > 0.02f) {
                val haloAlpha = (bloomTriple.first * (0.18f + currentFrame.beatPulse * 0.55f)).coerceIn(0f, 0.7f)
                val strobeAlpha = (bloomTriple.third * currentFrame.beatPulse * 0.45f).coerceIn(0f, 0.5f)
                val primaryGlow = evaluatedPalette.firstOrNull() ?: Color.Cyan
                drawRect(
                    brush = Brush.radialGradient(
                        colors = listOf(
                            primaryGlow.copy(alpha = haloAlpha),
                            Color.Transparent,
                        ),
                        center = Offset(size.width * 0.5f, size.height * 0.5f),
                        radius = size.maxDimension * 0.65f,
                    ),
                    blendMode = BlendMode.Plus,
                )
                if (strobeAlpha > 0.02f) {
                    drawRect(
                        color = Color.White.copy(alpha = strobeAlpha),
                        blendMode = BlendMode.Screen,
                    )
                }
            }

            // 3. API 24-32 CRT Scanlines fallback
            if (Build.VERSION.SDK_INT < Build.VERSION_CODES.TIRAMISU) {
                val crt = activeTxValues[TransformerType.CRT_SCANLINE_WARP]
                if (crt != null && crt.first > 0.05f) {
                    val step = (crt.second.dp.toPx()).coerceAtLeast(4f)
                    var y = 0f
                    while (y < size.height) {
                        drawLine(
                            color = Color.Black.copy(alpha = (crt.first * 0.45f).coerceIn(0f, 0.6f)),
                            start = Offset(0f, y),
                            end = Offset(size.width, y),
                            strokeWidth = step * 0.4f,
                        )
                        y += step
                    }
                }
            }
        }
    }
}
