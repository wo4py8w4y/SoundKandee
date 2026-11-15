package com.example

import android.content.Context
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.junit4.createAndroidComposeRule
import androidx.compose.ui.test.onAllNodesWithText
import androidx.compose.ui.test.onFirst
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performScrollToIndex
import androidx.compose.ui.test.performTextReplacement
import androidx.test.core.app.ApplicationProvider
import com.example.audio.DeviceTapAction
import com.example.audio.SynthMusicStyle
import com.example.data.PreferencesRepository
import com.example.model.AudioFilterScope
import com.example.model.BeatDivision
import com.example.model.FftWindowType
import com.example.model.LayerBlendMode
import com.example.model.LfoWaveform
import com.example.model.ModulationDriver
import com.example.model.PresetTransitionConfig
import com.example.model.ResponseCurve
import com.example.model.TempoSyncMode
import com.example.model.TransformerType
import com.example.model.TransitionCurve
import com.example.model.VisualStyleType
import com.example.ui.ChromaWaveViewModel
import com.example.ui.QuickLfoTarget
import com.example.ui.StudioTab
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [36])
class ExampleRobolectricTest {

    @get:Rule
    val composeRule = createAndroidComposeRule<MainActivity>()

    @Test
    fun read_app_name_matches_SoundKandee() {
        val context = ApplicationProvider.getApplicationContext<Context>()
        assertEquals("SoundKandee", context.getString(R.string.app_name))
    }

    @Test
    fun studio_tabs_and_zen_mode_work_cleanly() {
        // VisualizerCanvas runs a continuous 60fps withFrameNanos loop, so advance clock manually.
        composeRule.mainClock.autoAdvance = false
        composeRule.mainClock.advanceTimeBy(100)

        composeRule.onNodeWithTag("visualizer_canvas").assertIsDisplayed()
        composeRule.onNodeWithTag("soundkandee_logo").assertIsDisplayed()
        composeRule.onNodeWithTag("tab_TRANSFORMERS").performClick()
        composeRule.mainClock.advanceTimeBy(100)

        composeRule.onNodeWithTag("tab_LFOS").performClick()
        composeRule.mainClock.advanceTimeBy(100)

        composeRule.onNodeWithTag("tab_PALETTES").performClick()
        composeRule.mainClock.advanceTimeBy(100)

        composeRule.onNodeWithTag("tab_PRESETS").performClick()
        composeRule.mainClock.advanceTimeBy(100)

        composeRule.onNodeWithTag("tab_AUDIO_DSP").performClick()
        composeRule.mainClock.advanceTimeBy(100)

        composeRule.onNodeWithTag("mutate_button").performClick()
        composeRule.mainClock.advanceTimeBy(100)

        composeRule.onNodeWithTag("zen_mode_button").performClick()
        composeRule.mainClock.advanceTimeBy(100)
    }

    @Test
    fun viewmodel_saves_loads_switches_and_manages_complete_presets() {
        val app = ApplicationProvider.getApplicationContext<android.app.Application>()
        val vm = ChromaWaveViewModel(app)

        // Wait briefly for initial DataStore emission
        val initialPresetId = vm.uiState.value.activePresetId
        assertNotNull(initialPresetId)
        assertFalse(vm.uiState.value.hasUnsavedChanges)

        // Customize layer stack, parameter scopes, transformer, color & DSP
        vm.addLayer(VisualStyleType.HARMONIC_SPIROGRAPH)
        val addedLayer = vm.uiState.value.layers.last()
        vm.updateLayer(addedLayer.id) { layer ->
            layer.copy(
                blendMode = LayerBlendMode.DIFFERENCE,
                audioFilter = AudioFilterScope(lowCutHz = 95f, highCutHz = 4200f, gain = 1.6f),
                primaryParam = layer.primaryParam.copy(
                    scopeMin = 4f,
                    scopeMax = 11f,
                    driver = ModulationDriver.TREBLE,
                    curve = ResponseCurve.EXPONENTIAL,
                ),
            )
        }
        vm.addOrEnableTransformer(TransformerType.MILK_PER_PIXEL_WARP)
        val warpNode = vm.uiState.value.transformers.first { it.type == TransformerType.MILK_PER_PIXEL_WARP }
        vm.updateTransformer(warpNode.id) { node ->
            node.copy(amount = node.amount.copy(scopeMin = 0.4f, scopeMax = 1.3f, inverted = true))
        }
        vm.selectPalette("cyber_noir")
        vm.updateDspSettings { it.copy(fftWindow = FftWindowType.BLACKMAN, inputGainDb = 11f) }

        assertTrue("Expected dirty state after editing layer scopes & DSP", vm.uiState.value.hasUnsavedChanges)

        // Save complete configuration as a named user preset
        val saved = vm.saveCurrentAsPreset("Neon Cathedral", "Tuned spiro + MilkDrop warp")
        assertEquals("Neon Cathedral", saved.name)
        assertEquals(saved.id, vm.uiState.value.activePresetId)
        assertFalse(vm.uiState.value.hasUnsavedChanges)
        assertEquals(1, vm.uiState.value.userPresets.size)

        // Verify JSON serialization roundtrip preserves all layer & transformer parameter scopes
        val repo = PreferencesRepository(app)
        val encoded = repo.encodePresetList(vm.uiState.value.userPresets)
        val decoded = repo.decodePresetList(encoded)
        assertEquals(1, decoded.size)
        val roundTripped = decoded.first()
        assertEquals("Neon Cathedral", roundTripped.name)
        assertEquals("cyber_noir", roundTripped.colorEngine.paletteId)
        assertEquals(FftWindowType.BLACKMAN, roundTripped.dspSettings.fftWindow)
        assertEquals(11f, roundTripped.dspSettings.inputGainDb)
        val decodedAddedLayer = roundTripped.layers.last()
        assertEquals(LayerBlendMode.DIFFERENCE, decodedAddedLayer.blendMode)
        assertEquals(4f, decodedAddedLayer.primaryParam.scopeMin)
        assertEquals(11f, decodedAddedLayer.primaryParam.scopeMax)
        assertEquals(ModulationDriver.TREBLE, decodedAddedLayer.primaryParam.driver)
        assertEquals(ResponseCurve.EXPONENTIAL, decodedAddedLayer.primaryParam.curve)

        // Switch to next built-in preset
        val next = vm.switchToNextPreset()
        assertNotNull(next)
        assertTrue(next!!.id != saved.id)
        assertEquals(next.id, vm.uiState.value.activePresetId)
        assertFalse(vm.uiState.value.hasUnsavedChanges)

        // Switch back to saved preset and verify full restoration
        assertTrue(vm.switchPreset(saved.id))
        assertEquals(saved.id, vm.uiState.value.activePresetId)
        assertEquals("cyber_noir", vm.uiState.value.colorEngine.paletteId)
        assertEquals(FftWindowType.BLACKMAN, vm.uiState.value.dspSettings.fftWindow)
        assertEquals(LayerBlendMode.DIFFERENCE, vm.uiState.value.layers.last().blendMode)
        assertEquals(4f, vm.uiState.value.layers.last().primaryParam.scopeMin)

        // Modify, overwrite, duplicate, rename, delete
        vm.updateDspSettings { it.copy(bassTiltDb = 7.5f) }
        assertTrue(vm.uiState.value.hasUnsavedChanges)
        assertTrue(vm.overwritePreset(saved.id))
        assertFalse(vm.uiState.value.hasUnsavedChanges)
        assertEquals(7.5f, vm.uiState.value.activePreset?.dspSettings?.bassTiltDb)

        val duplicated = vm.duplicatePreset(saved.id, "Neon Cathedral Remix")
        assertNotNull(duplicated)
        assertEquals(2, vm.uiState.value.userPresets.size)
        assertEquals(duplicated!!.id, vm.uiState.value.activePresetId)

        assertTrue(vm.renamePreset(duplicated.id, "Neon Cathedral V2"))
        assertEquals("Neon Cathedral V2", vm.uiState.value.userPresets.first { it.id == duplicated.id }.name)

        assertTrue(vm.deletePreset(duplicated.id))
        assertEquals(1, vm.uiState.value.userPresets.size)
        assertEquals(null, vm.uiState.value.activePresetId)
    }

    @Test
    fun viewmodel_tempo_lfo_synth_and_device_tap_work_end_to_end() {
        val app = ApplicationProvider.getApplicationContext<android.app.Application>()
        val vm = ChromaWaveViewModel(app)

        // Tap tempo switches to TAP_TEMPO mode and computes BPM
        vm.tapTempo(1_000L)
        vm.tapTempo(1_500L)
        vm.tapTempo(2_000L)
        val tempoCfg = vm.uiState.value.dspSettings.tempoSync
        assertEquals(TempoSyncMode.TAP_TEMPO, tempoCfg.mode)
        assertTrue("Expected tapBpm ~120, got ${tempoCfg.tapBpm}", tempoCfg.tapBpm in 116f..124f)

        // LFO Bank edits + Quick Patch routing
        vm.updateLfoChannel(1) { ch ->
            ch.copy(waveform = LfoWaveform.SAW_UP, beatDivision = BeatDivision.DIV_EIGHTH, depth = 0.9f)
        }
        vm.assignQuickTargetToLfoBus(QuickLfoTarget.FIRST_LAYER_SPIN, 1)
        assertEquals(LfoWaveform.SAW_UP, vm.uiState.value.lfoBank.first().waveform)
        assertEquals(BeatDivision.DIV_EIGHTH, vm.uiState.value.lfoBank.first().beatDivision)
        assertEquals(ModulationDriver.LFO_BUS_1, vm.uiState.value.layers.first().rotationSpeed.driver)

        // Synth Idle fallback config
        vm.updateSynthFallbackConfig {
            it.copy(enabled = true, style = SynthMusicStyle.PSYCHEDELIC_ARP, tempoBpm = 136f)
        }
        assertEquals(SynthMusicStyle.PSYCHEDELIC_ARP, vm.uiState.value.synthFallbackConfig.style)
        assertEquals(136f, vm.uiState.value.synthFallbackConfig.tempoBpm)

        // Device tap randomize trigger
        vm.updateDeviceTapConfig { it.copy(enabled = true, action = DeviceTapAction.RANDOMIZE_OPTIONS) }
        val beforeLayers = vm.uiState.value.layers
        vm.onPhysicalDeviceTapTriggered(nowMs = 5_000L)
        assertEquals(5_000L, vm.uiState.value.lastDeviceTapEventMs)
        assertTrue(vm.uiState.value.hasUnsavedChanges)
        assertTrue(vm.uiState.value.layers.isNotEmpty() && vm.uiState.value.layers != beforeLayers)

        // Verify tempoSync and lfoBank survive preset save + JSON roundtrip
        val saved = vm.saveCurrentAsPreset("LFO Tempo Kit")
        val repo = PreferencesRepository(app)
        val decoded = repo.decodePresetList(repo.encodePresetList(listOf(saved))).first()
        assertEquals(TempoSyncMode.TAP_TEMPO, decoded.dspSettings.tempoSync.mode)
        assertEquals(LfoWaveform.SAW_UP, decoded.lfoBank.first().waveform)
        assertEquals(BeatDivision.DIV_EIGHTH, decoded.lfoBank.first().beatDivision)
    }

    @Test
    fun lfo_and_hud_tap_tempo_ui_controls_work() {
        composeRule.mainClock.autoAdvance = false
        composeRule.mainClock.advanceTimeBy(100)

        composeRule.onNodeWithTag("hud_tap_tempo_btn").performClick()
        composeRule.mainClock.advanceTimeBy(80)

        composeRule.onNodeWithTag("tab_${StudioTab.LFOS.name}").performClick()
        composeRule.mainClock.advanceTimeBy(100)

        composeRule.onNodeWithTag("tap_tempo_button").assertIsDisplayed().performClick()
        composeRule.mainClock.advanceTimeBy(80)

        composeRule.onNodeWithTag("lfo_tempo_list").performScrollToIndex(1)
        composeRule.mainClock.advanceTimeBy(80)

        composeRule.onNodeWithTag("lfo_wave_1_${LfoWaveform.SAW_UP.name}").performClick()
        composeRule.mainClock.advanceTimeBy(80)
    }

    @Test
    fun preset_tab_ui_allows_saving_and_switching_presets() {
        composeRule.mainClock.autoAdvance = false
        composeRule.mainClock.advanceTimeBy(100)

        composeRule.onNodeWithTag("tab_${StudioTab.PRESETS.name}").performClick()
        composeRule.mainClock.advanceTimeBy(100)

        composeRule.onNodeWithTag("save_new_preset_button").performClick()
        composeRule.mainClock.advanceTimeBy(100)

        composeRule.onNodeWithTag("preset_name_input").performTextReplacement("Laser Storm")
        composeRule.mainClock.advanceTimeBy(50)
        composeRule.onNodeWithTag("confirm_save_preset_button").performClick()
        composeRule.mainClock.advanceTimeBy(150)

        composeRule.onAllNodesWithText("Laser Storm").onFirst().assertIsDisplayed()

        composeRule.onNodeWithTag("next_preset_btn").performClick()
        composeRule.mainClock.advanceTimeBy(100)
        composeRule.onNodeWithTag("prev_preset_btn").performClick()
        composeRule.mainClock.advanceTimeBy(100)
        composeRule.onAllNodesWithText("Laser Storm").onFirst().assertIsDisplayed()

        // Expand crossfade settings inside Presets tab and select Equal Power curve variation
        composeRule.onNodeWithTag("presets_crossfade_header").performClick()
        composeRule.mainClock.advanceTimeBy(100)
        composeRule.onNodeWithTag("presets_curve_${TransitionCurve.EQUAL_POWER.name}").assertIsDisplayed().performClick()
        composeRule.mainClock.advanceTimeBy(80)
        composeRule.onAllNodesWithText("Eq Power • 750ms • FX Morph").onFirst().assertIsDisplayed()
    }

    @Test
    fun viewmodel_preset_crossfade_tokens_and_settings_variations_work_without_dirtying_preset() {
        val app = ApplicationProvider.getApplicationContext<android.app.Application>()
        val vm = ChromaWaveViewModel(app)

        val tokenBefore = vm.uiState.value.visualTransitionToken
        vm.updatePresetTransitionConfig {
            PresetTransitionConfig(
                enabled = true,
                durationMs = 1200,
                curve = TransitionCurve.BEAT_PUNCH,
                morphShadersAndPalettes = false,
                crossfadeOnRandomize = false,
            )
        }
        assertFalse("Updating global transition settings must not mark preset dirty", vm.uiState.value.hasUnsavedChanges)
        assertEquals(1200, vm.uiState.value.presetTransitionConfig.durationMs)
        assertEquals(TransitionCurve.BEAT_PUNCH, vm.uiState.value.presetTransitionConfig.curve)

        // Randomize respects crossfadeOnRandomize = false (does not trigger visual crossfade)
        vm.randomize()
        assertEquals(tokenBefore, vm.uiState.value.visualTransitionToken)

        // Preset switches always bump visualTransitionToken
        vm.switchToNextPreset()
        assertEquals(tokenBefore + 1L, vm.uiState.value.visualTransitionToken)

        // Turning crossfadeOnRandomize = true triggers smooth blend on randomize
        vm.updatePresetTransitionConfig { it.copy(crossfadeOnRandomize = true) }
        vm.randomize()
        assertEquals(tokenBefore + 2L, vm.uiState.value.visualTransitionToken)
    }
}


