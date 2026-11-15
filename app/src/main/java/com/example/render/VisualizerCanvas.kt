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
import androidx.compose.runtime.rememberUpdatedState
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
import com.example.audio.TempoSyncEngine
import com.example.model.ColorEngineConfig
import com.example.model.LfoChannelConfig
import com.example.model.ModulationEvaluator
import com.example.model.PresetTransitionBlender
import com.example.model.PresetTransitionConfig
import com.example.model.TempoSyncConfig
import com.example.model.TempoSyncSnapshot
import com.example.model.TransformerNode
import com.example.model.TransformerType
import com.example.model.TransitionCurve
import com.example.model.VisualLayer
import com.example.model.VisualStyleType
import com.example.ui.theme.VoidObsidian
import kotlin.math.cos
import kotlin.math.roundToInt
import kotlin.math.sin
import kotlin.random.Random

private data class EvaluatedRenderLayer(
    val layer: VisualLayer,
    val params: EvaluatedLayerParams,
    val palette: List<Color>,
)

private data class OutgoingLayerEntry(
    val layer: VisualLayer,
    val colorEngine: ColorEngineConfig,
    val baseWeight: Float,
)

/**
 * Live 60fps composited visualizer canvas supporting:
 * - Multi-layer overlapping visual generators with BlendModes & per-layer Hz band filters
 * - Offscreen ping-pong echo buffer for Feedback Echo Trails
 * - Overlapping spatial & wave transformers (Kaleidoscope, Tile Mosaic, Vortex, WaveFold, Chromatic Split, Ripples, Bloom, CRT)
 * - Tempo-synced visual oscillator clock & 3 Master LFO buses
 * - Smooth cross-fading preset transitions across layers, palettes, spatial mirrors & shader uniforms
 * - Tactile pinch/pan focal point steering & tap-to-Zen toggle
 */
@Composable
fun VisualizerCanvas(
    layers: List<VisualLayer>,
    transformers: List<TransformerNode>,
    colorEngine: ColorEngineConfig,
    tempoSyncConfig: TempoSyncConfig,
    lfoBank: List<LfoChannelConfig>,
    tempoSyncEngine: TempoSyncEngine,
    presetTransitionConfig: PresetTransitionConfig,
    visualTransitionToken: Long,
    audioFrameProvider: () -> AudioFrame,
    onLiveEvaluatedValues: (Map<String, Float>) -> Unit,
    onTempoSnapshot: (TempoSyncSnapshot) -> Unit,
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

    val currentLayers by rememberUpdatedState(layers)
    val currentTransformers by rememberUpdatedState(transformers)
    val currentColorEngine by rememberUpdatedState(colorEngine)
    val currentTempoSync by rememberUpdatedState(tempoSyncConfig)
    val currentLfoBank by rememberUpdatedState(lfoBank)
    val currentTransitionConfig by rememberUpdatedState(presetTransitionConfig)
    val currentTransitionToken by rememberUpdatedState(visualTransitionToken)

    var frameTick by remember { mutableLongStateOf(0L) }
    var elapsedSec by remember { mutableFloatStateOf(0f) }
    var oscTimeSec by remember { mutableFloatStateOf(0f) }
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
    var outgoingRenderLayers by remember { mutableStateOf<List<EvaluatedRenderLayer>>(emptyList()) }
    var incomingRenderLayers by remember { mutableStateOf<List<EvaluatedRenderLayer>>(emptyList()) }
    var evaluatedPalette by remember { mutableStateOf<List<Color>>(emptyList()) }
    var activeTxValues by remember { mutableStateOf<Map<TransformerType, Triple<Float, Float, Float>>>(emptyMap()) }
    var outgoingSpatialTx by remember { mutableStateOf<Map<TransformerType, Triple<Float, Float, Float>>>(emptyMap()) }
    var incomingSpatialTx by remember { mutableStateOf<Map<TransformerType, Triple<Float, Float, Float>>>(emptyMap()) }
    var splitSpatialPass by remember { mutableStateOf(false) }

    LaunchedEffect(Unit) {
        var prevNanos = 0L
        var uiTempoThrottle = 0

        // Cross-fade transition state maintained across live updates without restarting frame clock
        var isTransitioning = false
        var transitionElapsedSec = 0f
        var transitionDurationSec = 0.75f
        var activeTransitionCurve = TransitionCurve.SMOOTHSTEP
        var activeMorphShadersAndPalettes = true
        var outgoingEntries: List<OutgoingLayerEntry> = emptyList()
        var outgoingPaletteSnapshot: List<Color> = emptyList()
        var outgoingTxSnapshot: Map<TransformerType, Triple<Float, Float, Float>> = emptyMap()
        var lastCommittedLayers: List<VisualLayer> = emptyList()
        var lastCommittedColorEngine: ColorEngineConfig? = null
        var lastCommittedToken = currentTransitionToken
        var lastOutgoingGain = 0f
        var lastIncomingGain = 1f

        fun evaluatePalette(
            ce: ColorEngineConfig,
            audio: AudioFrame,
            dt: Float,
            tempoSnap: TempoSyncSnapshot,
            driftRate: Float,
            advanceHue: Boolean,
            telemetryTarget: MutableMap<String, Float>?,
        ): List<Color> {
            val hueBase = evaluator.evaluate("c_hue", ce.hueShift, audio, audio.overallRms, elapsedSec, dt, tempoSnap)
            val hueDrift = evaluator.evaluate("c_drift", ce.hueDriftSpeed, audio, audio.overallRms, elapsedSec, dt, tempoSnap)
            val sat = evaluator.evaluate("c_sat", ce.saturation, audio, audio.overallRms, elapsedSec, dt, tempoSnap)
            val luma = evaluator.evaluate("c_luma", ce.contrast, audio, audio.overallRms, elapsedSec, dt, tempoSnap)
            val flash = evaluator.evaluate("c_flash", ce.beatFlash, audio, audio.beatPulse, elapsedSec, dt, tempoSnap)
            if (advanceHue) {
                accumulatedHueDeg = (accumulatedHueDeg + hueDrift * dt * driftRate) % 360f
            }
            if (telemetryTarget != null) {
                telemetryTarget[ce.hueShift.id] = hueBase
                telemetryTarget[ce.hueDriftSpeed.id] = hueDrift
                telemetryTarget[ce.saturation.id] = sat
                telemetryTarget[ce.contrast.id] = luma
                telemetryTarget[ce.beatFlash.id] = flash
            }
            val basePalette = ColorEngine.activeBaseColors(ce)
            return ColorEngine.transformPalette(
                colors = basePalette,
                hueShiftDeg = hueBase + accumulatedHueDeg,
                saturationMul = sat,
                lumaBoost = luma,
                beatFlash = flash,
            )
        }

        fun evaluateSingleLayer(
            keyPrefix: String,
            layer: VisualLayer,
            palette: List<Color>,
            opacityGain: Float,
            audio: AudioFrame,
            dt: Float,
            tempoSnap: TempoSyncSnapshot,
            driftRate: Float,
            telemetryTarget: MutableMap<String, Float>?,
        ): EvaluatedRenderLayer {
            val slice = evaluator.evaluateAudioSlice(audio, layer.audioFilter)
            val op = evaluator.evaluate("${keyPrefix}_op", layer.opacity, audio, slice, elapsedSec, dt, tempoSnap)
            val sc = evaluator.evaluate("${keyPrefix}_sc", layer.scale, audio, slice, elapsedSec, dt, tempoSnap)
            val rotSpeed = evaluator.evaluate("${keyPrefix}_rot", layer.rotationSpeed, audio, slice, elapsedSec, dt, tempoSnap)
            val ox = evaluator.evaluate("${keyPrefix}_ox", layer.offsetX, audio, slice, elapsedSec, dt, tempoSnap)
            val oy = evaluator.evaluate("${keyPrefix}_oy", layer.offsetY, audio, slice, elapsedSec, dt, tempoSnap)
            val p1 = evaluator.evaluate("${keyPrefix}_p1", layer.primaryParam, audio, slice, elapsedSec, dt, tempoSnap)
            val p2 = evaluator.evaluate("${keyPrefix}_p2", layer.secondaryParam, audio, slice, elapsedSec, dt, tempoSnap)
            val p3 = evaluator.evaluate("${keyPrefix}_p3", layer.tertiaryParam, audio, slice, elapsedSec, dt, tempoSnap)

            val prevAngle = layerRotations[layer.id] ?: 0f
            val nextAngle = (prevAngle + rotSpeed * dt * driftRate) % 360f
            layerRotations[layer.id] = nextAngle

            if (telemetryTarget != null) {
                telemetryTarget[layer.opacity.id] = op
                telemetryTarget[layer.scale.id] = sc
                telemetryTarget[layer.rotationSpeed.id] = rotSpeed
                telemetryTarget[layer.offsetX.id] = ox
                telemetryTarget[layer.offsetY.id] = oy
                telemetryTarget[layer.primaryParam.id] = p1
                telemetryTarget[layer.secondaryParam.id] = p2
                telemetryTarget[layer.tertiaryParam.id] = p3
            }

            if (layer.styleType == VisualStyleType.PARTICLE_STARBURST && opacityGain > 0.05f) {
                particlePool.step(dt * driftRate, p2, p3, audio.beatPulse, slice)
            }

            return EvaluatedRenderLayer(
                layer = layer,
                params = EvaluatedLayerParams(
                    opacity = (op * opacityGain).coerceIn(0f, 1f),
                    scale = sc,
                    rotationDeg = nextAngle,
                    offsetXNorm = ox,
                    offsetYNorm = oy,
                    primary = p1,
                    secondary = p2,
                    tertiary = p3,
                    sliceEnergy = slice,
                ),
                palette = palette,
            )
        }

        while (true) {
            withFrameNanos { nanos ->
                val dt = if (prevNanos == 0L) 0.016f else ((nanos - prevNanos) / 1_000_000_000f).coerceIn(0.001f, 0.05f)
                prevNanos = nanos
                elapsedSec += dt

                val latestLayers = currentLayers
                val latestTransformers = currentTransformers
                val latestColorEngine = currentColorEngine
                val latestTempoSync = currentTempoSync
                val latestLfoBank = currentLfoBank
                val latestTransitionConfig = currentTransitionConfig
                val latestToken = currentTransitionToken

                // Detect a preset/configuration switch trigger without touching live slider edits
                if (latestToken != lastCommittedToken) {
                    lastCommittedToken = latestToken
                    val prevCe = lastCommittedColorEngine
                    if (latestTransitionConfig.enabled &&
                        latestTransitionConfig.durationMs > 0 &&
                        prevCe != null &&
                        lastCommittedLayers.isNotEmpty()
                    ) {
                        outgoingEntries = if (isTransitioning) {
                            val carriedOld = outgoingEntries.map {
                                it.copy(baseWeight = it.baseWeight * lastOutgoingGain)
                            }
                            val anyPrevSolo = lastCommittedLayers.any { it.solo && it.enabled }
                            val carriedIncoming = lastCommittedLayers
                                .filter { it.enabled && (!anyPrevSolo || it.solo) }
                                .map { OutgoingLayerEntry(it, prevCe, lastIncomingGain) }
                            (carriedOld + carriedIncoming)
                                .filter { it.baseWeight >= 0.02f }
                                .takeLast(10)
                        } else {
                            val anyPrevSolo = lastCommittedLayers.any { it.solo && it.enabled }
                            lastCommittedLayers
                                .filter { it.enabled && (!anyPrevSolo || it.solo) }
                                .map { OutgoingLayerEntry(it, prevCe, 1f) }
                        }
                        outgoingPaletteSnapshot = evaluatedPalette
                        outgoingTxSnapshot = activeTxValues
                        transitionElapsedSec = 0f
                        transitionDurationSec = latestTransitionConfig.durationMs / 1000f
                        activeTransitionCurve = latestTransitionConfig.curve
                        activeMorphShadersAndPalettes = latestTransitionConfig.morphShadersAndPalettes
                        isTransitioning = outgoingEntries.isNotEmpty() || outgoingTxSnapshot.isNotEmpty()
                        lastOutgoingGain = 1f
                        lastIncomingGain = 0f
                    } else {
                        isTransitioning = false
                        outgoingEntries = emptyList()
                        lastOutgoingGain = 0f
                        lastIncomingGain = 1f
                    }
                }
                lastCommittedLayers = latestLayers
                lastCommittedColorEngine = latestColorEngine

                val tempoSnap = tempoSyncEngine.step(dt, latestTempoSync, latestLfoBank)
                val visualClock = if (tempoSnap.lockVisualOscillators) tempoSnap.oscillatorTimeSec else elapsedSec
                oscTimeSec = visualClock
                uiTempoThrottle++
                if (uiTempoThrottle >= 3) {
                    uiTempoThrottle = 0
                    onTempoSnapshot(tempoSnap)
                }

                val audio = audioFrameProvider()
                currentFrame = audio

                val liveTelemetryMap = HashMap<String, Float>(48)
                val driftRate = if (tempoSnap.lockVisualOscillators) (tempoSnap.effectiveBpm / 120f) else 1f

                // Advance cross-fade envelope
                var outGain = 0f
                var inGain = 1f
                var morphT = 1f
                if (isTransitioning) {
                    transitionElapsedSec += dt
                    val rawProgress = (transitionElapsedSec / transitionDurationSec.coerceAtLeast(0.001f)).coerceIn(0f, 1f)
                    if (rawProgress >= 1f) {
                        isTransitioning = false
                        outgoingEntries = emptyList()
                    } else {
                        val gains = PresetTransitionBlender.evaluateGains(rawProgress, activeTransitionCurve)
                        outGain = gains.outgoingGain
                        inGain = gains.incomingGain
                        morphT = gains.morphT
                    }
                    lastOutgoingGain = outGain
                    lastIncomingGain = inGain
                }

                // Evaluate incoming color palette
                val targetPalette = evaluatePalette(
                    ce = latestColorEngine,
                    audio = audio,
                    dt = dt,
                    tempoSnap = tempoSnap,
                    driftRate = driftRate,
                    advanceHue = true,
                    telemetryTarget = liveTelemetryMap,
                )

                val resolvedGlobalPalette = if (isTransitioning) {
                    if (activeMorphShadersAndPalettes) {
                        PresetTransitionBlender.lerpPalettes(outgoingPaletteSnapshot, targetPalette, morphT)
                    } else {
                        if (morphT < 0.5f) outgoingPaletteSnapshot.ifEmpty { targetPalette } else targetPalette
                    }
                } else {
                    targetPalette
                }
                evaluatedPalette = resolvedGlobalPalette

                // Evaluate outgoing fading layers (keeping each outgoing layer alive with audio + its own palette)
                val evaluatedOutgoing = if (isTransitioning && outGain > 0.005f && outgoingEntries.isNotEmpty()) {
                    val outgoingPalCache = HashMap<ColorEngineConfig, List<Color>>()
                    outgoingEntries.mapNotNull { entry ->
                        val weight = outGain * entry.baseWeight
                        if (weight <= 0.005f) return@mapNotNull null
                        val entryPalette = outgoingPalCache.getOrPut(entry.colorEngine) {
                            if (entry.colorEngine == latestColorEngine) {
                                targetPalette
                            } else {
                                evaluatePalette(
                                    ce = entry.colorEngine,
                                    audio = audio,
                                    dt = dt,
                                    tempoSnap = tempoSnap,
                                    driftRate = driftRate,
                                    advanceHue = false,
                                    telemetryTarget = null,
                                )
                            }
                        }
                        evaluateSingleLayer(
                            keyPrefix = "out_${entry.layer.id}",
                            layer = entry.layer,
                            palette = entryPalette,
                            opacityGain = weight,
                            audio = audio,
                            dt = dt,
                            tempoSnap = tempoSnap,
                            driftRate = driftRate,
                            telemetryTarget = null,
                        )
                    }
                } else {
                    emptyList()
                }

                // Evaluate incoming active layers (always populate telemetry so HUD reflects target scopes)
                val anySolo = latestLayers.any { it.solo && it.enabled }
                val activeLayers = latestLayers.filter { it.enabled && (!anySolo || it.solo) }
                val evaluatedIncoming = activeLayers.map { layer ->
                    evaluateSingleLayer(
                        keyPrefix = layer.id,
                        layer = layer,
                        palette = targetPalette,
                        opacityGain = inGain,
                        audio = audio,
                        dt = dt,
                        tempoSnap = tempoSnap,
                        driftRate = driftRate,
                        telemetryTarget = liveTelemetryMap,
                    )
                }

                // Evaluate incoming transformers (respecting beat quantization if enabled)
                val quantizeTx = tempoSnap.quantizeTransformerChanges
                val targetTxMap = HashMap<TransformerType, Triple<Float, Float, Float>>()
                latestTransformers.filter { it.enabled }.forEach { node ->
                    val slice = evaluator.evaluateAudioSlice(audio, node.audioFilter)
                    val amt = evaluator.evaluate("${node.id}_amt", node.amount, audio, slice, elapsedSec, dt, tempoSnap, quantizeTx)
                    val freq = evaluator.evaluate("${node.id}_freq", node.frequency, audio, slice, elapsedSec, dt, tempoSnap, quantizeTx)
                    val drift = evaluator.evaluate("${node.id}_drift", node.phaseOrDrift, audio, slice, elapsedSec, dt, tempoSnap, quantizeTx)

                    liveTelemetryMap[node.amount.id] = amt
                    liveTelemetryMap[node.frequency.id] = freq
                    liveTelemetryMap[node.phaseOrDrift.id] = drift

                    targetTxMap[node.type] = Triple(amt, freq, drift)
                }

                val blendedTxMap = if (isTransitioning) {
                    if (activeMorphShadersAndPalettes) {
                        PresetTransitionBlender.interpolateTxMaps(outgoingTxSnapshot, targetTxMap, morphT)
                    } else {
                        if (morphT < 0.5f) outgoingTxSnapshot else targetTxMap
                    }
                } else {
                    targetTxMap
                }

                val needsSplitSpatial = isTransitioning && (
                    !activeMorphShadersAndPalettes ||
                        PresetTransitionBlender.hasDifferentSpatialSymmetry(outgoingTxSnapshot, targetTxMap)
                    )

                outgoingRenderLayers = evaluatedOutgoing
                incomingRenderLayers = evaluatedIncoming
                outgoingSpatialTx = outgoingTxSnapshot
                incomingSpatialTx = targetTxMap
                splitSpatialPass = needsSplitSpatial
                activeTxValues = blendedTxMap

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
                            timeSec = oscTimeSec,
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

            fun DrawScope.renderLayerList(
                items: List<EvaluatedRenderLayer>,
                targetSize: Size,
            ) {
                if (items.isEmpty()) return
                translate(left = userPanOffset.x, top = userPanOffset.y) {
                    scale(scale = userZoom, pivot = Offset(targetSize.width * 0.5f, targetSize.height * 0.5f)) {
                        items.forEach { item ->
                            with(LayerRenderers) {
                                renderLayer(
                                    styleType = item.layer.styleType,
                                    blendMode = item.layer.blendMode.composeBlendMode,
                                    params = item.params,
                                    frame = currentFrame,
                                    palette = item.palette,
                                    timeSec = oscTimeSec,
                                    particlePool = particlePool,
                                    agslHolder = agslHolder,
                                )
                            }
                        }
                    }
                }
            }

            fun DrawScope.drawSpatialLayers(
                items: List<EvaluatedRenderLayer>,
                spatialTx: Map<TransformerType, Triple<Float, Float, Float>>,
                targetSize: Size,
            ) {
                if (items.isEmpty()) return
                val kaleido = spatialTx[TransformerType.KALEIDOSCOPE_MIRROR]
                val tile = spatialTx[TransformerType.KALEIDO_TILE_MOSAIC]

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
                                    renderLayerList(items, targetSize)
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
                                        renderLayerList(items, targetSize)
                                    }
                                }
                            }
                        }
                    }
                } else {
                    renderLayerList(items, targetSize)
                }
            }

            fun DrawScope.drawSceneContent(targetSize: Size) {
                if (splitSpatialPass) {
                    drawSpatialLayers(outgoingRenderLayers, outgoingSpatialTx, targetSize)
                    drawSpatialLayers(incomingRenderLayers, incomingSpatialTx, targetSize)
                } else {
                    val combined = if (outgoingRenderLayers.isEmpty()) {
                        incomingRenderLayers
                    } else {
                        outgoingRenderLayers + incomingRenderLayers
                    }
                    drawSpatialLayers(combined, activeTxValues, targetSize)
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
                if (echoFront != null) {
                    echoFront = null
                    echoBack = null
                }
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
                    val phase = ((oscTimeSec * ripple.third * 0.45f) + r / ringCount.toFloat()) % 1f
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
