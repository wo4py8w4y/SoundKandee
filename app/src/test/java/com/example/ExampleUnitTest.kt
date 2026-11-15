package com.example

import com.example.audio.ConfigurableDspProcessor
import com.example.model.AudioFilterScope
import com.example.model.DspSettings
import com.example.model.ModulationDriver
import com.example.model.ModulationEvaluator
import com.example.model.PresetCatalog
import com.example.model.ScopedParam
import kotlin.math.PI
import kotlin.math.sin
import kotlin.random.Random
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

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
}

