package com.example

import android.content.Context
import androidx.test.core.app.ApplicationProvider
import com.example.audio.ConfigurableDspProcessor
import com.example.audio.DeviceTapConfig
import com.example.audio.DeviceTapDetector
import com.example.audio.SynthFallbackConfig
import com.example.audio.SynthMusicStyle
import com.example.audio.SyntheticMusicGenerator
import com.example.audio.TempoSyncEngine
import com.example.model.AudioFilterScope
import com.example.model.BeatDivision
import com.example.model.DspSettings
import com.example.model.LfoChannelConfig
import com.example.model.LfoWaveform
import com.example.model.ModulationDriver
import com.example.model.ModulationEvaluator
import androidx.compose.ui.graphics.Color
import com.example.model.PresetCatalog
import com.example.model.PresetTransitionBlender
import com.example.model.ScopedParam
import com.example.model.TempoSyncConfig
import com.example.model.TempoSyncMode
import com.example.model.TempoSyncSnapshot
import com.example.model.TransformerType
import com.example.model.TransitionCurve
import kotlin.math.PI
import kotlin.math.pow
import kotlin.math.sin
import kotlin.random.Random
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [36])
class ExampleUnitTest {

    @Test
    fun dsp_detects_440Hz_sine_peak_and_activity() {
        val sampleRate = 44100
        val dsp = ConfigurableDspProcessor(fftSize = 1024)
        val pcm = ShortArray(1024) { i ->
            val v = sin(2.0 * PI * 440.0 * i / sampleRate) * 20000.0
            v.toInt().toShort()
        }
        val frame = dsp.processPcm16(pcm, pcm.size, sampleRate, DspSettings(inputGainDb = 0f, noiseFloor = 0.01f))
        assertTrue("Expected active audio signal", frame.isSignalActive)
        assertTrue("Expected dominant frequency near 440Hz, got ${frame.dominantHz}", frame.dominantHz in 340f..540f)
    }

    @Test
    fun scoped_param_strictly_stays_within_user_scope() {
        val sampleRate = 44100
        val dsp = ConfigurableDspProcessor(fftSize = 1024)
        val pcm = ShortArray(1024) { i ->
            (sin(2.0 * PI * 60.0 * i / sampleRate) * 28000.0).toInt().toShort()
        }
        val frame = dsp.processPcm16(pcm, pcm.size, sampleRate, DspSettings())
        val evaluator = ModulationEvaluator()
        val param = ScopedParam(
            id = "test_folds",
            label = "Folds",
            hardMin = 2f,
            hardMax = 24f,
            scopeMin = 6f,
            scopeMax = 12f,
            driver = ModulationDriver.SUB_BASS,
        )
        repeat(20) { step ->
            val evaluated = evaluator.evaluate("k", param, frame, 0.8f, step * 0.016f, 0.016f)
            assertTrue("Evaluated value $evaluated out of [6, 12]", evaluated in 6f..12f)
        }
    }

    @Test
    fun audio_filter_slice_isolates_frequency_window() {
        val sampleRate = 44100
        val dsp = ConfigurableDspProcessor(fftSize = 1024)
        val pcm = ShortArray(1024) { i ->
            (sin(2.0 * PI * 80.0 * i / sampleRate) * 26000.0).toInt().toShort()
        }
        val frame = dsp.processPcm16(pcm, pcm.size, sampleRate, DspSettings(globalSmoothing = 0f))
        val evaluator = ModulationEvaluator()
        val lowEnergy = evaluator.evaluateAudioSlice(frame, AudioFilterScope(lowCutHz = 40f, highCutHz = 160f))
        val highEnergy = evaluator.evaluateAudioSlice(frame, AudioFilterScope(lowCutHz = 6000f, highCutHz = 12000f))
        assertTrue("Low window ($lowEnergy) should exceed high window ($highEnergy) for 80Hz tone", lowEnergy > highEnergy)
    }

    @Test
    fun randomize_preserves_locked_layers_and_palette() {
        val preset = PresetCatalog.presets.first()
        val lockedLayer = preset.layers.first().copy(locked = true)
        val layers = listOf(lockedLayer) + preset.layers.drop(1)
        val lockedColor = PresetCatalog.defaultColorEngine("bio_abyss").copy(locked = true)

        val (nextLayers, _, nextColor) = PresetCatalog.randomize(
            currentLayers = layers,
            currentTransformers = preset.transformers,
            colorEngine = lockedColor,
            rng = Random(42),
        )
        assertTrue("Locked layer must be preserved", nextLayers.any { it.id == lockedLayer.id && it.locked })
        assertEquals("bio_abyss", nextColor.paletteId)
    }

    @Test
    fun catalog_all_styles_and_transformers_have_valid_scopes() {
        com.example.model.VisualStyleType.entries.forEach { style ->
            val layer = PresetCatalog.createLayer(style)
            listOf(layer.opacity, layer.scale, layer.rotationSpeed, layer.primaryParam, layer.secondaryParam, layer.tertiaryParam)
                .forEach { p ->
                    assertTrue("${style.name}:${p.id} invalid bounds", p.hardMin <= p.orderedMin && p.orderedMin <= p.orderedMax && p.orderedMax <= p.hardMax)
                }
        }
        com.example.model.TransformerType.entries.forEach { type ->
            val node = PresetCatalog.createTransformer(type)
            listOf(node.amount, node.frequency, node.phaseOrDrift).forEach { p ->
                assertTrue("${type.name}:${p.id} invalid bounds", p.hardMin <= p.orderedMin && p.orderedMin <= p.orderedMax && p.orderedMax <= p.hardMax)
            }
        }
        assertTrue(PresetCatalog.presets.size >= 8)
    }

    @Test
    fun preset_cloning_preserves_parameter_scopes_with_fresh_ids() {
        val original = PresetCatalog.presets.first()
        val tunedLayer = original.layers.first().copy(
            opacity = original.layers.first().opacity.copy(
                scopeMin = 0.25f,
                scopeMax = 0.78f,
                driver = ModulationDriver.TREBLE,
                inverted = true,
            ),
            audioFilter = AudioFilterScope(lowCutHz = 110f, highCutHz = 2400f, gain = 1.45f),
        )
        val clonedLayers = PresetCatalog.cloneLayersWithFreshIds(listOf(tunedLayer))
        val clonedTx = PresetCatalog.cloneTransformersWithFreshIds(original.transformers)

        assertEquals(1, clonedLayers.size)
        val cLayer = clonedLayers.first()
        assertTrue(cLayer.id != tunedLayer.id)
        assertTrue(cLayer.opacity.id != tunedLayer.opacity.id)
        assertEquals(0.25f, cLayer.opacity.scopeMin)
        assertEquals(0.78f, cLayer.opacity.scopeMax)
        assertEquals(ModulationDriver.TREBLE, cLayer.opacity.driver)
        assertTrue(cLayer.opacity.inverted)
        assertEquals(110f, cLayer.audioFilter.lowCutHz)
        assertEquals(2400f, cLayer.audioFilter.highCutHz)
        assertEquals(original.transformers.size, clonedTx.size)
        assertTrue(clonedTx.first().id != original.transformers.first().id)
    }

    @Test
    fun tempo_sync_auto_beat_tracks_steady_bpm_and_octave_folds() {
        val engine = TempoSyncEngine()
        val cfg = TempoSyncConfig(enabled = true, mode = TempoSyncMode.AUTO_BEAT)
        // Feed steady 140 BPM onsets (~429ms)
        var t = 1_000L
        repeat(8) {
            engine.onAutoBeatDetected(t)
            t += 429L
        }
        val snap140 = engine.step(0.016f, cfg)
        assertTrue("Expected auto BPM near 140, got ${snap140.effectiveBpm}", snap140.effectiveBpm in 134f..146f)
        assertTrue("Expected confidence > 0.4, got ${snap140.confidence}", snap140.confidence > 0.4f)

        // Reset and feed double-time 280 BPM (214ms) -> should octave-fold into 140 BPM
        engine.resetAutoTracker()
        t = 10_000L
        repeat(8) {
            engine.onAutoBeatDetected(t)
            t += 214L
        }
        val snapFolded = engine.step(0.016f, cfg)
        assertTrue("Expected octave-folded BPM near 140, got ${snapFolded.effectiveBpm}", snapFolded.effectiveBpm in 132f..148f)
    }

    @Test
    fun tap_tempo_computes_bpm_and_locks_phase_to_downbeat() {
        val engine = TempoSyncEngine()
        val cfg = TempoSyncConfig(enabled = true, mode = TempoSyncMode.TAP_TEMPO)
        engine.step(0.123f, cfg) // drift away from zero first
        engine.recordTap(1_000L)
        engine.recordTap(1_500L)
        engine.recordTap(2_000L)
        val bpm = engine.recordTap(2_500L)
        assertTrue("Expected tap BPM near 120, got $bpm", bpm in 118f..122f)

        val snap = engine.step(0.001f, cfg)
        assertTrue("Expected beat phase locked near 0.0 right after tap, got ${snap.beatPhase}", snap.beatPhase < 0.05f)
        assertTrue("Expected effective BPM near 120, got ${snap.effectiveBpm}", snap.effectiveBpm in 118f..122f)
    }

    @Test
    fun modulation_evaluator_locks_lfo_to_bpm_and_quantizes_transformer_changes() {
        val evaluator = ModulationEvaluator()
        val engine = TempoSyncEngine()
        val cfg = TempoSyncConfig(enabled = true, mode = TempoSyncMode.MANUAL_BPM, manualBpm = 120f, lockVisualOscillators = true)
        val lfoBank = listOf(
            LfoChannelConfig(id = 1, name = "LFO 1", enabled = true, waveform = LfoWaveform.SAW_UP, tempoSynced = true, beatDivision = BeatDivision.DIV_QUARTER, depth = 1f),
            LfoChannelConfig(id = 2, name = "LFO 2", enabled = true),
            LfoChannelConfig(id = 3, name = "LFO 3", enabled = true),
        )
        val busParam = ScopedParam(
            id = "tx_amt",
            label = "Amount",
            hardMin = 0f,
            hardMax = 2f,
            scopeMin = 0.2f,
            scopeMax = 1.4f,
            driver = ModulationDriver.LFO_BUS_1,
            attackMs = 0f,
            releaseMs = 0f,
        )
        val frame = com.example.audio.AudioFrame(isSignalActive = true, bass = 0.5f)
        val snap1 = engine.step(0.05f, cfg, lfoBank)
        val v1 = evaluator.evaluate("k_bus", busParam, frame, 0.5f, 0.05f, 0.016f, snap1, forceTempoQuantize = false)
        assertTrue("LFO bus output $v1 out of [0.2, 1.4]", v1 in 0.2f..1.4f)

        // Now test beat-hold quantization for transformer parameter changes
        val qParam = ScopedParam(
            id = "tx_quant",
            label = "Warp",
            hardMin = 0f,
            hardMax = 1f,
            scopeMin = 0f,
            scopeMax = 1f,
            driver = ModulationDriver.LFO_SAW_UP,
            lfoTempoSynced = true,
            beatDivision = BeatDivision.DIV_QUARTER,
            attackMs = 0f,
            releaseMs = 0f,
        )
        // With DIV_QUARTER (1 cycle/beat), lock tick is floor(totalBeats * 1)
        val holdSnapA = TempoSyncSnapshot(
            enabled = true,
            effectiveBpm = 120f,
            totalBeats = 1.15,
            beatPhase = 0.15f,
            lockVisualOscillators = true,
            quantizeTransformerChanges = true,
            transformerQuantizeDivision = BeatDivision.DIV_QUARTER,
        )
        val holdSnapB = holdSnapA.copy(totalBeats = 1.75, beatPhase = 0.75f)
        val holdSnapNextStep = holdSnapA.copy(totalBeats = 2.25, beatPhase = 0.25f)

        val q1 = evaluator.evaluate("q_tx", qParam, frame, 0.2f, 1.0f, 0.016f, holdSnapA, forceTempoQuantize = true)
        val q2 = evaluator.evaluate("q_tx", qParam, frame, 0.9f, 1.1f, 0.016f, holdSnapB, forceTempoQuantize = true)
        val q3 = evaluator.evaluate("q_tx", qParam, frame, 0.9f, 1.2f, 0.016f, holdSnapNextStep, forceTempoQuantize = true)

        assertEquals("Quantized value must remain locked within the same 1/4 beat step", q1, q2, 1e-4f)
        assertTrue("Quantized value must step on next beat boundary (q2=$q2, q3=$q3)", q3 != q2)
    }

    @Test
    fun device_tap_detector_triggers_on_impulse_and_respects_cooldown() {
        val context = ApplicationProvider.getApplicationContext<Context>()
        val detector = DeviceTapDetector(context) {}
        detector.config = DeviceTapConfig(enabled = true, sensitivityThreshold = 5.0f, cooldownMs = 600L)

        // Gentle rest sample must not trigger
        assertFalse(detector.processSample(0.2f, 0.1f, 0.3f, isLinearAcceleration = true, timestampMs = 1_000L))
        // Sharp physical tap impulse triggers
        assertTrue(detector.processSample(6.8f, 1.2f, 0.5f, isLinearAcceleration = true, timestampMs = 1_100L))
        // Bounce inside cooldown must not re-trigger
        assertFalse(detector.processSample(7.2f, 0.5f, 0.4f, isLinearAcceleration = true, timestampMs = 1_400L))
        // Tap after cooldown triggers again
        assertTrue(detector.processSample(6.1f, 2.0f, 0.2f, isLinearAcceleration = true, timestampMs = 1_800L))
    }

    @Test
    fun synthetic_music_generator_produces_active_dsp_spectrum_and_beats() {
        val generator = SyntheticMusicGenerator(44100)
        val dsp = ConfigurableDspProcessor(fftSize = 1024)
        val synthCfg = SynthFallbackConfig(
            enabled = true,
            style = SynthMusicStyle.TECHNO_PULSE,
            intensity = 0.9f,
            tempoBpm = 128f,
        )
        val settings = DspSettings(noiseFloor = 0.01f, beatThreshold = 1.25f)

        var activeFrames = 0
        var beatOnsets = 0
        var peakHighEnergy = 0f

        repeat(90) { idx ->
            val pcm = generator.generateNextBuffer(1024, synthCfg)
            val frame = dsp.processPcm16(pcm, pcm.size, 44100, settings, nowMs = (idx + 1) * 23L)
            if (frame.isSignalActive) activeFrames++
            if (frame.beatOnset) beatOnsets++
            if (frame.treble > peakHighEnergy) peakHighEnergy = frame.treble
        }

        assertTrue("Expected most synthetic music frames to be active, got $activeFrames/90", activeFrames > 75)
        assertTrue("Expected synthetic techno beats to trigger beatOnset, got $beatOnsets", beatOnsets >= 2)
        assertTrue("Expected synthetic hi-hats/synths to excite treble spectrum bands", peakHighEnergy > 0.02f)
    }

    @Test
    fun preset_transition_curves_produce_expected_gain_profiles() {
        // Every curve starts cleanly at out=1, in=0 and ends at out=0, in=1
        TransitionCurve.entries.forEach { curve ->
            val start = PresetTransitionBlender.evaluateGains(0f, curve)
            val end = PresetTransitionBlender.evaluateGains(1f, curve)
            assertEquals("$curve start outGain", 1f, start.outgoingGain, 1e-4f)
            assertEquals("$curve start inGain", 0f, start.incomingGain, 1e-4f)
            assertEquals("$curve end outGain", 0f, end.outgoingGain, 1e-4f)
            assertEquals("$curve end inGain", 1f, end.incomingGain, 1e-4f)
        }

        // Equal power preserves energy: out^2 + in^2 == 1 at midpoint
        val eqMid = PresetTransitionBlender.evaluateGains(0.5f, TransitionCurve.EQUAL_POWER)
        val power = eqMid.outgoingGain.pow(2) + eqMid.incomingGain.pow(2)
        assertEquals("Equal power energy sum", 1f, power, 1e-3f)

        // Cosine dip is near dark at exact 0.5 midpoint before blooming incoming preset
        val dipMid = PresetTransitionBlender.evaluateGains(0.5f, TransitionCurve.COSINE_DIP)
        assertEquals(0f, dipMid.outgoingGain, 1e-4f)
        assertEquals(0f, dipMid.incomingGain, 1e-4f)
    }

    @Test
    fun preset_transition_interpolates_transformers_via_neutral_baselines_and_mismatched_palettes() {
        val fromTx = mapOf(
            TransformerType.MILK_PER_PIXEL_WARP to Triple(1.0f, 6f, 2f),
        )
        val toTx = mapOf(
            TransformerType.MILK_RADIAL_TUNNEL_PULL to Triple(1.16f, 2.5f, 0.2f),
            TransformerType.FEEDBACK_ECHO to Triple(0.8f, 1.04f, 4f),
        )

        val mid = PresetTransitionBlender.interpolateTxMaps(fromTx, toTx, 0.5f)
        assertEquals(0.5f, mid[TransformerType.MILK_PER_PIXEL_WARP]!!.first, 1e-4f)
        // Radial tunnel pull neutral zoom is 1.0f (not 0.0f) and neutral pulse is 0.0f
        val midTunnel = mid[TransformerType.MILK_RADIAL_TUNNEL_PULL]!!
        assertEquals(1.08f, midTunnel.first, 1e-4f)
        assertEquals(2.5f, midTunnel.second, 1e-4f)
        assertEquals(0.1f, midTunnel.third, 1e-4f)
        // Feedback echo neutral decay is 0.0f, neutral zoom is 1.0f
        val midEcho = mid[TransformerType.FEEDBACK_ECHO]!!
        assertEquals(0.4f, midEcho.first, 1e-4f)
        assertEquals(1.02f, midEcho.second, 1e-4f)

        // Mismatched palette sizes blend cleanly without out-of-bounds errors
        val palA = listOf(Color.Red, Color.Blue)
        val palB = listOf(Color.Green, Color.Yellow, Color.Cyan, Color.Magenta)
        val palMid = PresetTransitionBlender.lerpPalettes(palA, palB, 0.5f)
        assertEquals(4, palMid.size)
        assertEquals(0.5f, palMid.first().red, 0.01f)
        assertEquals(0.5f, palMid.first().green, 0.01f)

        // Spatial symmetry difference detection
        val kaleido4 = mapOf(TransformerType.KALEIDOSCOPE_MIRROR to Triple(4f, 0f, 1f))
        val kaleido8 = mapOf(TransformerType.KALEIDOSCOPE_MIRROR to Triple(8f, 0f, 1f))
        assertTrue(PresetTransitionBlender.hasDifferentSpatialSymmetry(kaleido4, emptyMap()))
        assertTrue(PresetTransitionBlender.hasDifferentSpatialSymmetry(kaleido4, kaleido8))
        assertFalse(PresetTransitionBlender.hasDifferentSpatialSymmetry(kaleido4, kaleido4))
    }
}


