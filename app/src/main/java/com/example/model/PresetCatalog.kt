package com.example.model

import java.util.UUID
import kotlin.math.max
import kotlin.math.min
import kotlin.random.Random

object PresetCatalog {

    val palettes: List<ColorPalette> = listOf(
        ColorPalette("acid_sunset", "Acid Sunset", listOf(0xFFFF2A85, 0xFFFF7B00, 0xFFFFD166, 0xFF7209B7)),
        ColorPalette("bio_abyss", "Bioluminescent Abyss", listOf(0xFF00F5D4, 0xFF00BBF9, 0xFF3A0CA3, 0xFF72EFDD)),
        ColorPalette("uv_prism", "Ultraviolet Prism", listOf(0xFF9B5DE5, 0xFFF15BB5, 0xFF00F5D4, 0xFFFEE440)),
        ColorPalette("infrared", "Infrared Thermal", listOf(0xFFFF0A54, 0xFFFF758F, 0xFFFFB703, 0xFFFFF3B0)),
        ColorPalette("liquid_mercury", "Liquid Mercury", listOf(0xFFE0FBFC, 0xFF98C1D9, 0xFF5C7AEA, 0xFFEE6C4D)),
        ColorPalette("forest_psy", "Forest Psychedelia", listOf(0xFF06D6A0, 0xFF80ED99, 0xFFD81159, 0xFF5E60CE)),
        ColorPalette("cyber_noir", "Cyber Noir", listOf(0xFF00F0FF, 0xFFFF0055, 0xFF7B2CBF, 0xFF0B132B)),
        ColorPalette("solar_flare", "Solar Flare", listOf(0xFFFF4800, 0xFFFF9E00, 0xFFFFD000, 0xFF8F00FF)),
        ColorPalette("pastel_dream", "Pastel Dream", listOf(0xFFFFAFCC, 0xFFBDE0FE, 0xFFCDB4DB, 0xFFA2D2FF)),
        ColorPalette("crt_phosphor", "CRT Phosphor", listOf(0xFF38FF62, 0xFF00F5D4, 0xFF0096C7, 0xFFB5FF8A)),
        ColorPalette("aurora", "Aurora Borealis", listOf(0xFF2EC4B6, 0xFF80FFDB, 0xFF5390D9, 0xFFC77DFF)),
        ColorPalette("vaporwave", "Vaporwave Neon", listOf(0xFFFF71CE, 0xFF01CDFE, 0xFF05FFA1, 0xFFB967FF)),
    )

    fun paletteById(id: String): ColorPalette =
        palettes.firstOrNull { it.id == id } ?: palettes.first()

    fun defaultColorEngine(paletteId: String = "acid_sunset"): ColorEngineConfig = ColorEngineConfig(
        paletteId = paletteId,
        useCustomPalette = false,
        hueShift = ScopedParam(
            id = "color_hue_shift",
            label = "Hue Shift",
            unit = "°",
            hardMin = 0f,
            hardMax = 360f,
            scopeMin = 0f,
            scopeMax = 90f,
            driver = ModulationDriver.SPECTRAL_CENTROID,
            curve = ResponseCurve.LINEAR,
        ),
        hueDriftSpeed = ScopedParam(
            id = "color_hue_drift",
            label = "Auto Hue Drift",
            unit = "°/s",
            hardMin = 0f,
            hardMax = 120f,
            scopeMin = 8f,
            scopeMax = 38f,
            driver = ModulationDriver.OVERALL_RMS,
            curve = ResponseCurve.LINEAR,
        ),
        saturation = ScopedParam(
            id = "color_sat",
            label = "Saturation",
            unit = "",
            hardMin = 0.2f,
            hardMax = 1.5f,
            scopeMin = 0.9f,
            scopeMax = 1.2f,
            driver = ModulationDriver.BASS,
        ),
        contrast = ScopedParam(
            id = "color_contrast",
            label = "Luma Boost",
            unit = "x",
            hardMin = 0.6f,
            hardMax = 1.8f,
            scopeMin = 0.95f,
            scopeMax = 1.35f,
            driver = ModulationDriver.BEAT_PULSE,
        ),
        beatFlash = ScopedParam(
            id = "color_beat_flash",
            label = "Beat Chroma Flash",
            unit = "",
            hardMin = 0f,
            hardMax = 1.0f,
            scopeMin = 0f,
            scopeMax = 0.45f,
            driver = ModulationDriver.BEAT_PULSE,
            curve = ResponseCurve.EXPONENTIAL,
        ),
    )

    fun createLayer(
        styleType: VisualStyleType,
        blendMode: LayerBlendMode = LayerBlendMode.SCREEN,
        audioFilter: AudioFilterScope = AudioFilterScope(),
        driverHint: ModulationDriver = ModulationDriver.BASS,
        id: String = UUID.randomUUID().toString().take(8),
    ): VisualLayer {
        val (pMin, pMax, sMin, sMax, tMin, tMax) = when (styleType) {
            VisualStyleType.OSCILLOSCOPE_RIBBON -> FloatArray6(2f, 5f, 0.45f, 1.35f, 3f, 9f)
            VisualStyleType.RADIAL_MANDALA -> FloatArray6(6f, 12f, 0.12f, 0.28f, 0.5f, 1.45f)
            VisualStyleType.NEBULA_FLUID -> FloatArray6(1.5f, 3.8f, 0.6f, 1.8f, 0.6f, 1.5f)
            VisualStyleType.TUNNEL_WARP_GRID -> FloatArray6(10f, 20f, 0.7f, 2.4f, -45f, 45f)
            VisualStyleType.PARTICLE_STARBURST -> FloatArray6(60f, 140f, 0.6f, 2.1f, -0.5f, 0.8f)
            VisualStyleType.TERRAIN_HORIZON -> FloatArray6(10f, 18f, 0.5f, 1.6f, 0.35f, 0.75f)
            VisualStyleType.HARMONIC_SPIROGRAPH -> FloatArray6(3f, 9f, 0.35f, 1.05f, 140f, 260f)
            VisualStyleType.LIQUID_BLOBS -> FloatArray6(5f, 10f, 0.4f, 1.4f, 0.45f, 1.2f)
            VisualStyleType.MILK_WAVE_MATRIX -> FloatArray6(0f, 3f, 0.6f, 2.2f, 2.5f, 8.5f)
            VisualStyleType.MILK_VECTOR_FIELD -> FloatArray6(48f, 96f, 0.8f, 2.4f, 0.5f, 1.4f)
            VisualStyleType.MILK_MOTION_SHAPES -> FloatArray6(4f, 9f, 2f, 5f, -45f, 75f)
            VisualStyleType.MILK_SOLAR_CORONA -> FloatArray6(16f, 36f, 0.5f, 1.6f, -1.2f, 1.2f)
        }
        return VisualLayer(
            id = id,
            styleType = styleType,
            enabled = true,
            blendMode = blendMode,
            audioFilter = audioFilter,
            opacity = ScopedParam(
                id = "${id}_opacity",
                label = "Opacity",
                hardMin = 0f, hardMax = 1f,
                scopeMin = 0.55f, scopeMax = 0.95f,
                driver = ModulationDriver.FILTERED_BAND,
            ),
            scale = ScopedParam(
                id = "${id}_scale",
                label = "Scale",
                unit = "x",
                hardMin = 0.3f, hardMax = 2.4f,
                scopeMin = 0.88f, scopeMax = 1.22f,
                driver = driverHint,
            ),
            rotationSpeed = ScopedParam(
                id = "${id}_rot",
                label = "Spin Speed",
                unit = "°/s",
                hardMin = -120f, hardMax = 120f,
                scopeMin = -18f, scopeMax = 28f,
                driver = ModulationDriver.LFO_SINE,
                lfoHz = 0.15f,
            ),
            offsetX = ScopedParam(
                id = "${id}_ox",
                label = "Center X",
                hardMin = -0.5f, hardMax = 0.5f,
                scopeMin = 0f, scopeMax = 0f,
                driver = ModulationDriver.FIXED,
            ),
            offsetY = ScopedParam(
                id = "${id}_oy",
                label = "Center Y",
                hardMin = -0.5f, hardMax = 0.5f,
                scopeMin = 0f, scopeMax = 0f,
                driver = ModulationDriver.FIXED,
            ),
            primaryParam = ScopedParam(
                id = "${id}_p1",
                label = styleType.primaryLabel,
                unit = styleType.primaryUnit,
                hardMin = styleType.primaryMin, hardMax = styleType.primaryMax,
                scopeMin = pMin, scopeMax = pMax,
                driver = ModulationDriver.SPECTRAL_CENTROID,
            ),
            secondaryParam = ScopedParam(
                id = "${id}_p2",
                label = styleType.secondaryLabel,
                unit = styleType.secondaryUnit,
                hardMin = styleType.secondaryMin, hardMax = styleType.secondaryMax,
                scopeMin = sMin, scopeMax = sMax,
                driver = driverHint,
            ),
            tertiaryParam = ScopedParam(
                id = "${id}_p3",
                label = styleType.tertiaryLabel,
                unit = styleType.tertiaryUnit,
                hardMin = styleType.tertiaryMin, hardMax = styleType.tertiaryMax,
                scopeMin = tMin, scopeMax = tMax,
                driver = ModulationDriver.TREBLE,
            ),
        )
    }

    fun createTransformer(
        type: TransformerType,
        enabled: Boolean = true,
        id: String = UUID.randomUUID().toString().take(8),
    ): TransformerNode {
        val (aMin, aMax, fMin, fMax, dMin, dMax) = when (type) {
            TransformerType.KALEIDOSCOPE_MIRROR -> FloatArray6(4f, 10f, -30f, 30f, 0.9f, 1.35f)
            TransformerType.FEEDBACK_ECHO -> FloatArray6(0.55f, 0.85f, 0.97f, 1.04f, -2.5f, 3.0f)
            TransformerType.WAVE_FOLD_WARP -> FloatArray6(0.15f, 0.75f, 3f, 10f, 0.5f, 2.0f)
            TransformerType.CHROMATIC_SPLIT -> FloatArray6(3f, 22f, 0f, 180f, 0.7f, 1.5f)
            TransformerType.VORTEX_SWIRL -> FloatArray6(-1.1f, 1.4f, 0.45f, 1.0f, 0.0f, 0.15f)
            TransformerType.RIPPLE_INTERFERENCE -> FloatArray6(0.2f, 0.95f, 6f, 16f, 0.8f, 2.6f)
            TransformerType.BEAT_BLOOM_STROBE -> FloatArray6(0.2f, 0.85f, 0f, 10f, 0f, 0.35f)
            TransformerType.KALEIDO_TILE_MOSAIC -> FloatArray6(2f, 3f, 0.01f, 0.06f, 0.5f, 1f)
            TransformerType.CRT_SCANLINE_WARP -> FloatArray6(0.2f, 0.5f, 4f, 8f, 0.15f, 0.55f)
            TransformerType.MILK_PER_PIXEL_WARP -> FloatArray6(0.25f, 1.05f, 2.5f, 8.0f, 0.4f, 2.0f)
            TransformerType.MILK_RADIAL_TUNNEL_PULL -> FloatArray6(0.96f, 1.12f, 0.8f, 2.4f, -0.08f, 0.14f)
            TransformerType.MILK_ANAMORPHIC_STRETCH -> FloatArray6(0.88f, 1.16f, 0f, 180f, 0.005f, 0.035f)
            TransformerType.MILK_ANGULAR_PINWHEEL -> FloatArray6(-1.2f, 1.5f, 2f, 8f, 2f, 6f)
            TransformerType.MILK_EDGE_SOLARIZE -> FloatArray6(0.2f, 0.85f, 1.5f, 5.0f, 0f, 3.14f)
            TransformerType.MILK_POSTERIZE_QUANTIZE -> FloatArray6(0.25f, 0.85f, 4f, 10f, 0.2f, 0.8f)
            TransformerType.MILK_MIRROR_QUAD -> FloatArray6(0.35f, 1.0f, -0.12f, 0.12f, 0f, 0.4f)
            TransformerType.MILK_POLAR_UNWRAP -> FloatArray6(0.2f, 0.9f, 2f, 6f, -1.5f, 1.5f)
        }
        val primaryDriver = when (type) {
            TransformerType.CHROMATIC_SPLIT, TransformerType.BEAT_BLOOM_STROBE,
            TransformerType.RIPPLE_INTERFERENCE, TransformerType.MILK_EDGE_SOLARIZE ->
                ModulationDriver.BEAT_PULSE
            TransformerType.FEEDBACK_ECHO, TransformerType.VORTEX_SWIRL,
            TransformerType.MILK_RADIAL_TUNNEL_PULL, TransformerType.MILK_PER_PIXEL_WARP ->
                ModulationDriver.SUB_BASS
            TransformerType.KALEIDOSCOPE_MIRROR, TransformerType.MILK_ANGULAR_PINWHEEL ->
                ModulationDriver.LFO_TRIANGLE
            else -> ModulationDriver.BASS
        }
        return TransformerNode(
            id = id,
            type = type,
            enabled = enabled,
            audioFilter = AudioFilterScope(),
            amount = ScopedParam(
                id = "${id}_amt",
                label = type.amountLabel,
                unit = type.amountUnit,
                hardMin = type.amountMin, hardMax = type.amountMax,
                scopeMin = aMin, scopeMax = aMax,
                driver = primaryDriver,
            ),
            frequency = ScopedParam(
                id = "${id}_freq",
                label = type.freqLabel,
                unit = type.freqUnit,
                hardMin = type.freqMin, hardMax = type.freqMax,
                scopeMin = fMin, scopeMax = fMax,
                driver = ModulationDriver.LFO_SINE,
                lfoHz = 0.22f,
            ),
            phaseOrDrift = ScopedParam(
                id = "${id}_drift",
                label = type.driftLabel,
                unit = type.driftUnit,
                hardMin = type.driftMin, hardMax = type.driftMax,
                scopeMin = dMin, scopeMax = dMax,
                driver = ModulationDriver.LOW_MID,
            ),
        )
    }

    val presets: List<VisualizerPreset> by lazy {
        listOf(
            VisualizerPreset(
                id = "hyperspace_mandala",
                name = "Hyperspace Mandala",
                subtitle = "Nebula plasma + radial petals + kaleidoscope & feedback echo",
                paletteId = "acid_sunset",
                layers = listOf(
                    createLayer(VisualStyleType.NEBULA_FLUID, LayerBlendMode.NORMAL, AudioFilterScope(20f, 220f), ModulationDriver.SUB_BASS, "l_neb"),
                    createLayer(VisualStyleType.RADIAL_MANDALA, LayerBlendMode.SCREEN, AudioFilterScope(60f, 3500f), ModulationDriver.BASS, "l_man"),
                    createLayer(VisualStyleType.OSCILLOSCOPE_RIBBON, LayerBlendMode.ADD, AudioFilterScope(400f, 14000f), ModulationDriver.HIGH_MID, "l_osc"),
                ),
                transformers = listOf(
                    createTransformer(TransformerType.KALEIDOSCOPE_MIRROR, enabled = true, id = "t_kal"),
                    createTransformer(TransformerType.FEEDBACK_ECHO, enabled = true, id = "t_echo"),
                    createTransformer(TransformerType.CHROMATIC_SPLIT, enabled = true, id = "t_rgb"),
                    createTransformer(TransformerType.BEAT_BLOOM_STROBE, enabled = true, id = "t_bloom"),
                ),
            ),
            VisualizerPreset(
                id = "acid_oscilloscope",
                name = "Acid Oscilloscope",
                subtitle = "Harmonic ribbons + spirograph + wave fold & vortex swirl",
                paletteId = "uv_prism",
                layers = listOf(
                    createLayer(VisualStyleType.HARMONIC_SPIROGRAPH, LayerBlendMode.SCREEN, AudioFilterScope(40f, 900f), ModulationDriver.BASS, "l_spi"),
                    createLayer(VisualStyleType.OSCILLOSCOPE_RIBBON, LayerBlendMode.ADD, AudioFilterScope(120f, 12000f), ModulationDriver.OVERALL_RMS, "l_osc2"),
                    createLayer(VisualStyleType.PARTICLE_STARBURST, LayerBlendMode.SCREEN, AudioFilterScope(2500f, 18000f), ModulationDriver.TREBLE, "l_part"),
                ),
                transformers = listOf(
                    createTransformer(TransformerType.WAVE_FOLD_WARP, enabled = true, id = "t_wf"),
                    createTransformer(TransformerType.VORTEX_SWIRL, enabled = true, id = "t_vor"),
                    createTransformer(TransformerType.FEEDBACK_ECHO, enabled = true, id = "t_echo2"),
                ),
            ),
            VisualizerPreset(
                id = "cyber_tunnel",
                name = "Cyber Tunnel Overdrive",
                subtitle = "3D warp grid + starburst + chromatic split & CRT scanlines",
                paletteId = "cyber_noir",
                layers = listOf(
                    createLayer(VisualStyleType.TUNNEL_WARP_GRID, LayerBlendMode.SCREEN, AudioFilterScope(20f, 300f), ModulationDriver.BEAT_PULSE, "l_tun"),
                    createLayer(VisualStyleType.PARTICLE_STARBURST, LayerBlendMode.ADD, AudioFilterScope(1000f, 16000f), ModulationDriver.TREBLE, "l_star"),
                    createLayer(VisualStyleType.RADIAL_MANDALA, LayerBlendMode.DIFFERENCE, AudioFilterScope(120f, 2200f), ModulationDriver.LOW_MID, "l_man2"),
                ),
                transformers = listOf(
                    createTransformer(TransformerType.CHROMATIC_SPLIT, enabled = true, id = "t_rgb2"),
                    createTransformer(TransformerType.CRT_SCANLINE_WARP, enabled = true, id = "t_crt"),
                    createTransformer(TransformerType.BEAT_BLOOM_STROBE, enabled = true, id = "t_bloom2"),
                ),
            ),
            VisualizerPreset(
                id = "liquid_prism",
                name = "Liquid Prism Echo",
                subtitle = "Metaballs + fluid aurora + ripple shockwaves & tile mosaic",
                paletteId = "bio_abyss",
                layers = listOf(
                    createLayer(VisualStyleType.LIQUID_BLOBS, LayerBlendMode.SCREEN, AudioFilterScope(20f, 400f), ModulationDriver.SUB_BASS, "l_blob"),
                    createLayer(VisualStyleType.NEBULA_FLUID, LayerBlendMode.OVERLAY, AudioFilterScope(200f, 4000f), ModulationDriver.LOW_MID, "l_neb2"),
                    createLayer(VisualStyleType.HARMONIC_SPIROGRAPH, LayerBlendMode.ADD, AudioFilterScope(1200f, 16000f), ModulationDriver.TREBLE, "l_spi2"),
                ),
                transformers = listOf(
                    createTransformer(TransformerType.RIPPLE_INTERFERENCE, enabled = true, id = "t_rip"),
                    createTransformer(TransformerType.KALEIDO_TILE_MOSAIC, enabled = true, id = "t_tile"),
                    createTransformer(TransformerType.FEEDBACK_ECHO, enabled = true, id = "t_echo3"),
                ),
            ),
            VisualizerPreset(
                id = "horizon_fever",
                name = "Synthwave Horizon",
                subtitle = "Scrolling terrain ridges + sun mandala + wave fold & CRT glow",
                paletteId = "vaporwave",
                layers = listOf(
                    createLayer(VisualStyleType.RADIAL_MANDALA, LayerBlendMode.SCREEN, AudioFilterScope(20f, 250f), ModulationDriver.BASS, "l_sun"),
                    createLayer(VisualStyleType.TERRAIN_HORIZON, LayerBlendMode.ADD, AudioFilterScope(80f, 10000f), ModulationDriver.OVERALL_RMS, "l_ter"),
                ),
                transformers = listOf(
                    createTransformer(TransformerType.CRT_SCANLINE_WARP, enabled = true, id = "t_crt2"),
                    createTransformer(TransformerType.WAVE_FOLD_WARP, enabled = true, id = "t_wf2"),
                    createTransformer(TransformerType.CHROMATIC_SPLIT, enabled = true, id = "t_rgb3"),
                ),
            ),
            VisualizerPreset(
                id = "geiss_warp_reactor",
                name = "Geiss Warp Reactor",
                subtitle = "MilkDrop wave matrix + custom shapes + per-pixel liquid warp & zoomexp",
                paletteId = "uv_prism",
                layers = listOf(
                    createLayer(VisualStyleType.MILK_MOTION_SHAPES, LayerBlendMode.SCREEN, AudioFilterScope(20f, 240f), ModulationDriver.SUB_BASS, "l_mshape"),
                    createLayer(VisualStyleType.MILK_WAVE_MATRIX, LayerBlendMode.ADD, AudioFilterScope(120f, 12000f), ModulationDriver.OVERALL_RMS, "l_mwave"),
                ),
                transformers = listOf(
                    createTransformer(TransformerType.FEEDBACK_ECHO, enabled = true, id = "t_mecho"),
                    createTransformer(TransformerType.MILK_RADIAL_TUNNEL_PULL, enabled = true, id = "t_mzoom"),
                    createTransformer(TransformerType.MILK_PER_PIXEL_WARP, enabled = true, id = "t_mwarp"),
                    createTransformer(TransformerType.MILK_EDGE_SOLARIZE, enabled = true, id = "t_msol"),
                ),
            ),
            VisualizerPreset(
                id = "milkdrop_pinwheel",
                name = "MilkDrop Acid Pinwheel",
                subtitle = "Vector flow + solar corona + pinwheel twist + 4-way mirror + echo",
                paletteId = "aurora",
                layers = listOf(
                    createLayer(VisualStyleType.MILK_SOLAR_CORONA, LayerBlendMode.SCREEN, AudioFilterScope(30f, 600f), ModulationDriver.BASS, "l_corona"),
                    createLayer(VisualStyleType.MILK_VECTOR_FIELD, LayerBlendMode.ADD, AudioFilterScope(400f, 16000f), ModulationDriver.TREBLE, "l_vec"),
                ),
                transformers = listOf(
                    createTransformer(TransformerType.FEEDBACK_ECHO, enabled = true, id = "t_pecho"),
                    createTransformer(TransformerType.MILK_ANGULAR_PINWHEEL, enabled = true, id = "t_pin"),
                    createTransformer(TransformerType.MILK_MIRROR_QUAD, enabled = true, id = "t_quad"),
                    createTransformer(TransformerType.CHROMATIC_SPLIT, enabled = true, id = "t_prgb"),
                ),
            ),
            VisualizerPreset(
                id = "solaris_polar_matrix",
                name = "Solaris Polar Matrix",
                subtitle = "MilkDrop wave matrix + polar unwrap + contour bands + anamorphic stretch",
                paletteId = "infrared",
                layers = listOf(
                    createLayer(VisualStyleType.MILK_WAVE_MATRIX, LayerBlendMode.SCREEN, AudioFilterScope(40f, 4000f), ModulationDriver.BASS, "l_swave"),
                    createLayer(VisualStyleType.MILK_MOTION_SHAPES, LayerBlendMode.DIFFERENCE, AudioFilterScope(200f, 14000f), ModulationDriver.HIGH_MID, "l_sshape"),
                ),
                transformers = listOf(
                    createTransformer(TransformerType.FEEDBACK_ECHO, enabled = true, id = "t_secho"),
                    createTransformer(TransformerType.MILK_POLAR_UNWRAP, enabled = true, id = "t_spol"),
                    createTransformer(TransformerType.MILK_ANAMORPHIC_STRETCH, enabled = true, id = "t_sstr"),
                    createTransformer(TransformerType.MILK_POSTERIZE_QUANTIZE, enabled = true, id = "t_spost"),
                ),
            ),
        )
    }

    /**
     * Mutates unlocked parameters strictly within their user-configured [scopeMin..scopeMax]
     * or gently shifts unlocked scopes and blend modes while preserving locked items.
     */
    fun mutate(
        layers: List<VisualLayer>,
        transformers: List<TransformerNode>,
        colorEngine: ColorEngineConfig,
        rng: Random = Random.Default,
    ): Triple<List<VisualLayer>, List<TransformerNode>, ColorEngineConfig> {
        val drivers = ModulationDriver.entries
        val curves = ResponseCurve.entries
        val blends = LayerBlendMode.entries

        fun mutateParam(p: ScopedParam): ScopedParam {
            if (p.locked) return p
            val span = p.hardMax - p.hardMin
            val jitter = span * 0.14f
            val newMin = (p.scopeMin + (rng.nextFloat() * 2f - 1f) * jitter).coerceIn(p.hardMin, p.hardMax)
            val newMax = (p.scopeMax + (rng.nextFloat() * 2f - 1f) * jitter).coerceIn(p.hardMin, p.hardMax)
            return p.copy(
                scopeMin = min(newMin, newMax),
                scopeMax = max(newMin, newMax),
                driver = if (rng.nextFloat() < 0.25f) drivers[rng.nextInt(drivers.size)] else p.driver,
                curve = if (rng.nextFloat() < 0.2f) curves[rng.nextInt(curves.size)] else p.curve,
            )
        }

        val mutatedLayers = layers.map { layer ->
            if (layer.locked) layer else layer.copy(
                blendMode = if (rng.nextFloat() < 0.35f) blends[rng.nextInt(blends.size)] else layer.blendMode,
                opacity = mutateParam(layer.opacity),
                scale = mutateParam(layer.scale),
                rotationSpeed = mutateParam(layer.rotationSpeed),
                primaryParam = mutateParam(layer.primaryParam),
                secondaryParam = mutateParam(layer.secondaryParam),
                tertiaryParam = mutateParam(layer.tertiaryParam),
            )
        }

        val mutatedTransformers = transformers.map { node ->
            if (node.locked) node else node.copy(
                enabled = if (rng.nextFloat() < 0.25f) !node.enabled else node.enabled,
                amount = mutateParam(node.amount),
                frequency = mutateParam(node.frequency),
                phaseOrDrift = mutateParam(node.phaseOrDrift),
            )
        }

        val mutatedColor = if (colorEngine.locked) colorEngine else colorEngine.copy(
            paletteId = if (rng.nextFloat() < 0.45f) palettes[rng.nextInt(palettes.size)].id else colorEngine.paletteId,
            hueShift = mutateParam(colorEngine.hueShift),
            hueDriftSpeed = mutateParam(colorEngine.hueDriftSpeed),
            beatFlash = mutateParam(colorEngine.beatFlash),
        )

        return Triple(mutatedLayers, mutatedTransformers, mutatedColor)
    }

    /**
     * Generates a fresh overlapping multi-layer + multi-transformer combination,
     * preserving any layers, transformers, or palette locked by the user.
     */
    fun randomize(
        currentLayers: List<VisualLayer>,
        currentTransformers: List<TransformerNode>,
        colorEngine: ColorEngineConfig,
        rng: Random = Random.Default,
    ): Triple<List<VisualLayer>, List<TransformerNode>, ColorEngineConfig> {
        val lockedLayers = currentLayers.filter { it.locked }
        val styles = VisualStyleType.entries.shuffled(rng)
        val blends = listOf(
            LayerBlendMode.SCREEN,
            LayerBlendMode.ADD,
            LayerBlendMode.DIFFERENCE,
            LayerBlendMode.EXCLUSION,
            LayerBlendMode.OVERLAY,
            LayerBlendMode.LIGHTEN,
        )
        val drivers = listOf(
            ModulationDriver.SUB_BASS,
            ModulationDriver.BASS,
            ModulationDriver.LOW_MID,
            ModulationDriver.HIGH_MID,
            ModulationDriver.TREBLE,
            ModulationDriver.BEAT_PULSE,
            ModulationDriver.SPECTRAL_CENTROID,
            ModulationDriver.LFO_SINE,
            ModulationDriver.LFO_TRIANGLE,
            ModulationDriver.LFO_SAW_UP,
            ModulationDriver.LFO_SAMPLE_HOLD,
            ModulationDriver.CHAOS_DRIFT,
            ModulationDriver.LFO_BUS_1,
            ModulationDriver.LFO_BUS_2,
        )

        fun randScope(p: ScopedParam): ScopedParam {
            if (p.locked) return p
            val a = p.hardMin + rng.nextFloat() * (p.hardMax - p.hardMin)
            val b = p.hardMin + rng.nextFloat() * (p.hardMax - p.hardMin)
            return p.copy(
                scopeMin = min(a, b),
                scopeMax = max(a, b),
                driver = drivers[rng.nextInt(drivers.size)],
                inverted = rng.nextFloat() < 0.2f,
            )
        }

        val targetCount = (2 + rng.nextInt(3)).coerceAtLeast(lockedLayers.size)
        val newLayers = lockedLayers.toMutableList()
        var idx = 0
        while (newLayers.size < targetCount && idx < styles.size) {
            val style = styles[idx++]
            val lowHz = listOf(20f, 60f, 180f, 500f, 1500f)[rng.nextInt(5)]
            val highHz = (lowHz * (3f + rng.nextFloat() * 14f)).coerceAtMost(20000f)
            val base = createLayer(
                styleType = style,
                blendMode = if (newLayers.isEmpty()) LayerBlendMode.NORMAL else blends[rng.nextInt(blends.size)],
                audioFilter = AudioFilterScope(lowCutHz = lowHz, highCutHz = highHz, gain = 0.9f + rng.nextFloat() * 0.7f),
                driverHint = drivers[rng.nextInt(drivers.size)],
            )
            newLayers += base.copy(
                opacity = randScope(base.opacity).let { it.copy(scopeMin = it.scopeMin.coerceAtLeast(0.35f)) },
                scale = randScope(base.scale),
                rotationSpeed = randScope(base.rotationSpeed),
                primaryParam = randScope(base.primaryParam),
                secondaryParam = randScope(base.secondaryParam),
                tertiaryParam = randScope(base.tertiaryParam),
            )
        }

        val lockedTransformers = currentTransformers.filter { it.locked }
        val lockedTypes = lockedTransformers.map { it.type }.toSet()
        val chosenTypes = TransformerType.entries.filter { it !in lockedTypes }.shuffled(rng).take(2 + rng.nextInt(3))
        val newTransformers = lockedTransformers + chosenTypes.map { type ->
            val node = createTransformer(type, enabled = true)
            node.copy(
                amount = randScope(node.amount),
                frequency = randScope(node.frequency),
                phaseOrDrift = randScope(node.phaseOrDrift),
            )
        }

        val newColor = if (colorEngine.locked) colorEngine else colorEngine.copy(
            paletteId = palettes[rng.nextInt(palettes.size)].id,
            useCustomPalette = false,
            hueShift = randScope(colorEngine.hueShift),
            hueDriftSpeed = randScope(colorEngine.hueDriftSpeed),
            beatFlash = randScope(colorEngine.beatFlash),
        )

        return Triple(newLayers, newTransformers, newColor)
    }

    fun cloneLayersWithFreshIds(layers: List<VisualLayer>): List<VisualLayer> {
        return layers.map { layer ->
            val newId = "l_${UUID.randomUUID().toString().take(8)}"
            layer.copy(
                id = newId,
                opacity = layer.opacity.copy(id = "${newId}_opacity"),
                scale = layer.scale.copy(id = "${newId}_scale"),
                rotationSpeed = layer.rotationSpeed.copy(id = "${newId}_rot"),
                offsetX = layer.offsetX.copy(id = "${newId}_ox"),
                offsetY = layer.offsetY.copy(id = "${newId}_oy"),
                primaryParam = layer.primaryParam.copy(id = "${newId}_p1"),
                secondaryParam = layer.secondaryParam.copy(id = "${newId}_p2"),
                tertiaryParam = layer.tertiaryParam.copy(id = "${newId}_p3"),
            )
        }
    }

    fun cloneTransformersWithFreshIds(transformers: List<TransformerNode>): List<TransformerNode> {
        return transformers.map { node ->
            val newId = "t_${UUID.randomUUID().toString().take(8)}"
            node.copy(
                id = newId,
                amount = node.amount.copy(id = "${newId}_amt"),
                frequency = node.frequency.copy(id = "${newId}_freq"),
                phaseOrDrift = node.phaseOrDrift.copy(id = "${newId}_drift"),
            )
        }
    }

    fun summarizeConfiguration(
        layers: List<VisualLayer>,
        transformers: List<TransformerNode>,
        colorEngine: ColorEngineConfig,
    ): String {
        val activeLayers = layers.count { it.enabled }
        val activeTx = transformers.count { it.enabled }
        val palLabel = if (colorEngine.useCustomPalette) {
            "Custom Palette"
        } else {
            paletteById(colorEngine.paletteId).name
        }
        return "$activeLayers/${layers.size} layers • $activeTx FX • $palLabel"
    }

    private data class FloatArray6(
        val a: Float, val b: Float, val c: Float, val d: Float, val e: Float, val f: Float
    )
}

