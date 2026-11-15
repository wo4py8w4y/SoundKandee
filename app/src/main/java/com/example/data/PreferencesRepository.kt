package com.example.data

import android.content.Context
import androidx.datastore.core.DataStore
import androidx.datastore.preferences.core.Preferences
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.stringPreferencesKey
import androidx.datastore.preferences.preferencesDataStore
import com.example.audio.DeviceTapAction
import com.example.audio.DeviceTapConfig
import com.example.audio.SynthFallbackConfig
import com.example.audio.SynthMusicStyle
import com.example.model.AudioFilterScope
import com.example.model.AudioSourceType
import com.example.model.BeatDivision
import com.example.model.ColorEngineConfig
import com.example.model.DspSettings
import com.example.model.FftWindowType
import com.example.model.LayerBlendMode
import com.example.model.LfoChannelConfig
import com.example.model.LfoWaveform
import com.example.model.ModulationDriver
import com.example.model.PresetCatalog
import com.example.model.PresetTransitionConfig
import com.example.model.ResponseCurve
import com.example.model.ScopedParam
import com.example.model.TempoSyncConfig
import com.example.model.TempoSyncMode
import com.example.model.TransformerNode
import com.example.model.TransformerType
import com.example.model.TransitionCurve
import com.example.model.VisualLayer
import com.example.model.VisualStyleType
import com.example.model.VisualizerPreset
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map
import org.json.JSONArray
import org.json.JSONObject

private val Context.chromaDataStore: DataStore<Preferences> by preferencesDataStore(name = "chromawave_prefs")

data class PersistedWorkspace(
    val audioSource: AudioSourceType = AudioSourceType.OUTPUT_MIX,
    val dspSettings: DspSettings = DspSettings(),
    val lfoBank: List<LfoChannelConfig> = LfoChannelConfig.defaults(),
    val deviceTapConfig: DeviceTapConfig = DeviceTapConfig(),
    val synthFallbackConfig: SynthFallbackConfig = SynthFallbackConfig(),
    val presetTransitionConfig: PresetTransitionConfig = PresetTransitionConfig(),
    val layers: List<VisualLayer> = PresetCatalog.presets.first().layers,
    val transformers: List<TransformerNode> = PresetCatalog.presets.first().transformers,
    val colorEngine: ColorEngineConfig = PresetCatalog.defaultColorEngine(PresetCatalog.presets.first().paletteId),
    val activePresetId: String? = PresetCatalog.presets.first().id,
)

class PreferencesRepository(private val context: Context) {

    private val workspaceKey = stringPreferencesKey("chromawave_workspace_v1")
    private val userPresetsKey = stringPreferencesKey("chromawave_user_presets_v1")

    val workspaceFlow: Flow<PersistedWorkspace> = context.chromaDataStore.data.map { prefs ->
        val raw = prefs[workspaceKey] ?: return@map PersistedWorkspace()
        runCatching { decodeWorkspace(raw) }.getOrElse { PersistedWorkspace() }
    }

    val userPresetsFlow: Flow<List<VisualizerPreset>> = context.chromaDataStore.data.map { prefs ->
        val raw = prefs[userPresetsKey] ?: return@map emptyList()
        runCatching { decodePresetList(raw) }.getOrElse { emptyList() }
    }

    suspend fun saveWorkspace(workspace: PersistedWorkspace) {
        val json = encodeWorkspace(workspace)
        context.chromaDataStore.edit { prefs ->
            prefs[workspaceKey] = json
        }
    }

    suspend fun saveUserPresets(presets: List<VisualizerPreset>) {
        val json = encodePresetList(presets)
        context.chromaDataStore.edit { prefs ->
            prefs[userPresetsKey] = json
        }
    }

    fun encodePresetList(presets: List<VisualizerPreset>): String {
        val arr = JSONArray()
        presets.forEach { arr.put(encodePreset(it)) }
        return arr.toString()
    }

    fun decodePresetList(raw: String): List<VisualizerPreset> {
        val arr = JSONArray(raw)
        return (0 until arr.length()).mapNotNull { idx ->
            decodePreset(arr.optJSONObject(idx))
        }
    }

    private fun encodePreset(p: VisualizerPreset): JSONObject = JSONObject().apply {
        put("id", p.id)
        put("name", p.name)
        put("subtitle", p.subtitle)
        put("paletteId", p.paletteId)
        put("isBuiltIn", false)
        put("createdAtEpochMs", p.createdAtEpochMs)
        put("updatedAtEpochMs", p.updatedAtEpochMs)
        put("dsp", encodeDsp(p.dspSettings))
        put("lfoBank", encodeLfoBank(p.lfoBank))
        put("layers", JSONArray().apply { p.layers.forEach { put(encodeLayer(it)) } })
        put("transformers", JSONArray().apply { p.transformers.forEach { put(encodeTransformer(it)) } })
        put("color", encodeColor(p.colorEngine))
    }

    private fun decodePreset(obj: JSONObject?): VisualizerPreset? {
        if (obj == null) return null
        val id = obj.optString("id").ifBlank { return null }
        val name = obj.optString("name").ifBlank { "Custom Preset" }
        val paletteId = obj.optString("paletteId", "acid_sunset")
        val dsp = decodeDsp(obj.optJSONObject("dsp"))
        val lfoBank = decodeLfoBank(obj.optJSONArray("lfoBank"))
        val layersArr = obj.optJSONArray("layers")
        val layers = if (layersArr != null && layersArr.length() > 0) {
            (0 until layersArr.length()).mapNotNull { decodeLayer(layersArr.optJSONObject(it)) }
        } else {
            PresetCatalog.presets.first().layers
        }
        val transArr = obj.optJSONArray("transformers")
        val transformers = if (transArr != null) {
            (0 until transArr.length()).mapNotNull { decodeTransformer(transArr.optJSONObject(it)) }
        } else {
            emptyList()
        }
        val colorObj = obj.optJSONObject("color")
        val color = if (colorObj != null) decodeColor(colorObj) else PresetCatalog.defaultColorEngine(paletteId)
        return VisualizerPreset(
            id = id,
            name = name,
            subtitle = obj.optString("subtitle", ""),
            layers = layers,
            transformers = transformers,
            paletteId = color.paletteId,
            colorEngine = color,
            dspSettings = dsp,
            lfoBank = lfoBank,
            isBuiltIn = false,
            createdAtEpochMs = obj.optLong("createdAtEpochMs", 0L),
            updatedAtEpochMs = obj.optLong("updatedAtEpochMs", 0L),
        )
    }

    private fun encodeDsp(d: DspSettings): JSONObject = JSONObject().apply {
        put("inputGainDb", d.inputGainDb.toDouble())
        put("noiseFloor", d.noiseFloor.toDouble())
        put("fftWindow", d.fftWindow.name)
        put("beatThreshold", d.beatThreshold.toDouble())
        put("beatCooldownMs", d.beatCooldownMs)
        put("bassTiltDb", d.bassTiltDb.toDouble())
        put("globalSmoothing", d.globalSmoothing.toDouble())
        put("tempoSync", encodeTempoSync(d.tempoSync))
    }

    private fun decodeDsp(dspObj: JSONObject?): DspSettings {
        if (dspObj == null) return DspSettings()
        return DspSettings(
            inputGainDb = dspObj.optDouble("inputGainDb", 6.0).toFloat(),
            noiseFloor = dspObj.optDouble("noiseFloor", 0.02).toFloat(),
            fftWindow = enumOrDefault(dspObj.optString("fftWindow"), FftWindowType.HANN),
            beatThreshold = dspObj.optDouble("beatThreshold", 1.35).toFloat(),
            beatCooldownMs = dspObj.optInt("beatCooldownMs", 140),
            bassTiltDb = dspObj.optDouble("bassTiltDb", 3.0).toFloat(),
            globalSmoothing = dspObj.optDouble("globalSmoothing", 0.55).toFloat(),
            tempoSync = decodeTempoSync(dspObj.optJSONObject("tempoSync")),
        )
    }

    private fun encodeTempoSync(t: TempoSyncConfig): JSONObject = JSONObject().apply {
        put("enabled", t.enabled)
        put("mode", t.mode.name)
        put("manualBpm", t.manualBpm.toDouble())
        put("tapBpm", t.tapBpm.toDouble())
        put("bpmMultiplier", t.bpmMultiplier.toDouble())
        put("lockVisualOscillators", t.lockVisualOscillators)
        put("quantizeTransformerChanges", t.quantizeTransformerChanges)
        put("transformerQuantizeDivision", t.transformerQuantizeDivision.name)
    }

    private fun decodeTempoSync(obj: JSONObject?): TempoSyncConfig {
        if (obj == null) return TempoSyncConfig()
        return TempoSyncConfig(
            enabled = obj.optBoolean("enabled", true),
            mode = enumOrDefault(obj.optString("mode"), TempoSyncMode.AUTO_BEAT),
            manualBpm = obj.optDouble("manualBpm", 120.0).toFloat().coerceIn(40f, 220f),
            tapBpm = obj.optDouble("tapBpm", 120.0).toFloat().coerceIn(40f, 220f),
            bpmMultiplier = obj.optDouble("bpmMultiplier", 1.0).toFloat().coerceIn(0.25f, 4f),
            lockVisualOscillators = obj.optBoolean("lockVisualOscillators", true),
            quantizeTransformerChanges = obj.optBoolean("quantizeTransformerChanges", false),
            transformerQuantizeDivision = enumOrDefault(obj.optString("transformerQuantizeDivision"), BeatDivision.DIV_QUARTER),
        )
    }

    private fun encodeLfoBank(bank: List<LfoChannelConfig>): JSONArray = JSONArray().apply {
        bank.forEach { ch ->
            put(JSONObject().apply {
                put("id", ch.id)
                put("name", ch.name)
                put("enabled", ch.enabled)
                put("waveform", ch.waveform.name)
                put("tempoSynced", ch.tempoSynced)
                put("beatDivision", ch.beatDivision.name)
                put("rateHz", ch.rateHz.toDouble())
                put("phaseDeg", ch.phaseDeg.toDouble())
                put("depth", ch.depth.toDouble())
                put("dutyCycle", ch.dutyCycle.toDouble())
                put("retriggerOnBeat", ch.retriggerOnBeat)
            })
        }
    }

    private fun decodeLfoBank(arr: JSONArray?): List<LfoChannelConfig> {
        val defaults = LfoChannelConfig.defaults()
        if (arr == null || arr.length() == 0) return defaults
        return defaults.mapIndexed { idx, fallback ->
            val obj = arr.optJSONObject(idx) ?: return@mapIndexed fallback
            fallback.copy(
                name = obj.optString("name", fallback.name).ifBlank { fallback.name },
                enabled = obj.optBoolean("enabled", fallback.enabled),
                waveform = enumOrDefault(obj.optString("waveform"), fallback.waveform),
                tempoSynced = obj.optBoolean("tempoSynced", fallback.tempoSynced),
                beatDivision = enumOrDefault(obj.optString("beatDivision"), fallback.beatDivision),
                rateHz = obj.optDouble("rateHz", fallback.rateHz.toDouble()).toFloat().coerceIn(0.05f, 12f),
                phaseDeg = obj.optDouble("phaseDeg", fallback.phaseDeg.toDouble()).toFloat().coerceIn(0f, 360f),
                depth = obj.optDouble("depth", fallback.depth.toDouble()).toFloat().coerceIn(0f, 1f),
                dutyCycle = obj.optDouble("dutyCycle", fallback.dutyCycle.toDouble()).toFloat().coerceIn(0.1f, 0.9f),
                retriggerOnBeat = obj.optBoolean("retriggerOnBeat", fallback.retriggerOnBeat),
            )
        }
    }

    private fun encodeWorkspace(w: PersistedWorkspace): String {
        val root = JSONObject()
        root.put("audioSource", w.audioSource.name)
        if (w.activePresetId != null) {
            root.put("activePresetId", w.activePresetId)
        } else {
            root.put("activePresetId", JSONObject.NULL)
        }
        root.put("dsp", encodeDsp(w.dspSettings))
        root.put("lfoBank", encodeLfoBank(w.lfoBank))
        root.put("deviceTap", JSONObject().apply {
            put("enabled", w.deviceTapConfig.enabled)
            put("action", w.deviceTapConfig.action.name)
            put("sensitivityThreshold", w.deviceTapConfig.sensitivityThreshold.toDouble())
            put("cooldownMs", w.deviceTapConfig.cooldownMs)
        })
        root.put("synthFallback", JSONObject().apply {
            put("enabled", w.synthFallbackConfig.enabled)
            put("style", w.synthFallbackConfig.style.name)
            put("intensity", w.synthFallbackConfig.intensity.toDouble())
            put("silenceDelaySec", w.synthFallbackConfig.silenceDelaySec.toDouble())
            put("tempoBpm", w.synthFallbackConfig.tempoBpm.toDouble())
        })
        root.put("presetTransition", JSONObject().apply {
            put("enabled", w.presetTransitionConfig.enabled)
            put("durationMs", w.presetTransitionConfig.durationMs)
            put("curve", w.presetTransitionConfig.curve.name)
            put("morphShadersAndPalettes", w.presetTransitionConfig.morphShadersAndPalettes)
            put("crossfadeOnRandomize", w.presetTransitionConfig.crossfadeOnRandomize)
        })
        root.put("layers", JSONArray().apply {
            w.layers.forEach { put(encodeLayer(it)) }
        })
        root.put("transformers", JSONArray().apply {
            w.transformers.forEach { put(encodeTransformer(it)) }
        })
        root.put("color", encodeColor(w.colorEngine))
        return root.toString()
    }

    private fun decodeWorkspace(raw: String): PersistedWorkspace {
        val root = JSONObject(raw)
        val source = enumOrDefault(root.optString("audioSource"), AudioSourceType.OUTPUT_MIX)
        val activePresetId = if (root.has("activePresetId") && !root.isNull("activePresetId")) {
            root.optString("activePresetId").takeIf { it.isNotBlank() }
        } else if (!root.has("activePresetId")) {
            PresetCatalog.presets.first().id
        } else {
            null
        }
        val dsp = decodeDsp(root.optJSONObject("dsp"))
        val lfoBank = decodeLfoBank(root.optJSONArray("lfoBank"))
        val tapObj = root.optJSONObject("deviceTap")
        val deviceTap = if (tapObj != null) {
            DeviceTapConfig(
                enabled = tapObj.optBoolean("enabled", true),
                action = enumOrDefault(tapObj.optString("action"), DeviceTapAction.RANDOMIZE_OPTIONS),
                sensitivityThreshold = tapObj.optDouble("sensitivityThreshold", 5.5).toFloat().coerceIn(2.5f, 14f),
                cooldownMs = tapObj.optLong("cooldownMs", 650L).coerceIn(250L, 2000L),
            )
        } else DeviceTapConfig()
        val synthObj = root.optJSONObject("synthFallback")
        val synthFallback = if (synthObj != null) {
            SynthFallbackConfig(
                enabled = synthObj.optBoolean("enabled", true),
                style = enumOrDefault(synthObj.optString("style"), SynthMusicStyle.TECHNO_PULSE),
                intensity = synthObj.optDouble("intensity", 0.85).toFloat().coerceIn(0.2f, 1.2f),
                silenceDelaySec = synthObj.optDouble("silenceDelaySec", 1.5).toFloat().coerceIn(0.2f, 8.0f),
                tempoBpm = synthObj.optDouble("tempoBpm", 126.0).toFloat().coerceIn(60f, 180f),
            )
        } else SynthFallbackConfig()

        val layersArr = root.optJSONArray("layers")
        val layers = if (layersArr != null && layersArr.length() > 0) {
            (0 until layersArr.length()).mapNotNull { decodeLayer(layersArr.optJSONObject(it)) }
        } else PresetCatalog.presets.first().layers

        val transArr = root.optJSONArray("transformers")
        val transformers = if (transArr != null) {
            (0 until transArr.length()).mapNotNull { decodeTransformer(transArr.optJSONObject(it)) }
        } else PresetCatalog.presets.first().transformers

        val colorObj = root.optJSONObject("color")
        val color = if (colorObj != null) decodeColor(colorObj) else PresetCatalog.defaultColorEngine()

        val transCfgObj = root.optJSONObject("presetTransition")
        val presetTransition = if (transCfgObj != null) {
            PresetTransitionConfig(
                enabled = transCfgObj.optBoolean("enabled", true),
                durationMs = transCfgObj.optInt("durationMs", 750)
                    .coerceIn(PresetTransitionConfig.MIN_DURATION_MS, PresetTransitionConfig.MAX_DURATION_MS),
                curve = enumOrDefault(transCfgObj.optString("curve"), TransitionCurve.SMOOTHSTEP),
                morphShadersAndPalettes = transCfgObj.optBoolean("morphShadersAndPalettes", true),
                crossfadeOnRandomize = transCfgObj.optBoolean("crossfadeOnRandomize", true),
            )
        } else PresetTransitionConfig()

        return PersistedWorkspace(
            audioSource = source,
            dspSettings = dsp,
            lfoBank = lfoBank,
            deviceTapConfig = deviceTap,
            synthFallbackConfig = synthFallback,
            presetTransitionConfig = presetTransition,
            layers = layers,
            transformers = transformers,
            colorEngine = color,
            activePresetId = activePresetId,
        )
    }

    private fun encodeScopedParam(p: ScopedParam): JSONObject = JSONObject().apply {
        put("id", p.id)
        put("label", p.label)
        put("unit", p.unit)
        put("hardMin", p.hardMin.toDouble())
        put("hardMax", p.hardMax.toDouble())
        put("scopeMin", p.scopeMin.toDouble())
        put("scopeMax", p.scopeMax.toDouble())
        put("driver", p.driver.name)
        put("curve", p.curve.name)
        put("attackMs", p.attackMs.toDouble())
        put("releaseMs", p.releaseMs.toDouble())
        put("lfoHz", p.lfoHz.toDouble())
        put("lfoPhaseDeg", p.lfoPhaseDeg.toDouble())
        put("lfoTempoSynced", p.lfoTempoSynced)
        put("beatDivision", p.beatDivision.name)
        put("tempoQuantizeHold", p.tempoQuantizeHold)
        put("inverted", p.inverted)
        put("locked", p.locked)
    }

    private fun decodeScopedParam(obj: JSONObject?, fallback: ScopedParam): ScopedParam {
        if (obj == null) return fallback
        return fallback.copy(
            scopeMin = obj.optDouble("scopeMin", fallback.scopeMin.toDouble()).toFloat().coerceIn(fallback.hardMin, fallback.hardMax),
            scopeMax = obj.optDouble("scopeMax", fallback.scopeMax.toDouble()).toFloat().coerceIn(fallback.hardMin, fallback.hardMax),
            driver = enumOrDefault(obj.optString("driver"), fallback.driver),
            curve = enumOrDefault(obj.optString("curve"), fallback.curve),
            attackMs = obj.optDouble("attackMs", fallback.attackMs.toDouble()).toFloat().coerceIn(5f, 500f),
            releaseMs = obj.optDouble("releaseMs", fallback.releaseMs.toDouble()).toFloat().coerceIn(15f, 1500f),
            lfoHz = obj.optDouble("lfoHz", fallback.lfoHz.toDouble()).toFloat().coerceIn(0.05f, 12f),
            lfoPhaseDeg = obj.optDouble("lfoPhaseDeg", fallback.lfoPhaseDeg.toDouble()).toFloat().coerceIn(0f, 360f),
            lfoTempoSynced = obj.optBoolean("lfoTempoSynced", fallback.lfoTempoSynced),
            beatDivision = enumOrDefault(obj.optString("beatDivision"), fallback.beatDivision),
            tempoQuantizeHold = obj.optBoolean("tempoQuantizeHold", fallback.tempoQuantizeHold),
            inverted = obj.optBoolean("inverted", fallback.inverted),
            locked = obj.optBoolean("locked", fallback.locked),
        )
    }

    private fun encodeAudioFilter(f: AudioFilterScope): JSONObject = JSONObject().apply {
        put("lowCutHz", f.lowCutHz.toDouble())
        put("highCutHz", f.highCutHz.toDouble())
        put("thresholdGate", f.thresholdGate.toDouble())
        put("ceilingClamp", f.ceilingClamp.toDouble())
        put("gain", f.gain.toDouble())
    }

    private fun decodeAudioFilter(obj: JSONObject?): AudioFilterScope {
        if (obj == null) return AudioFilterScope()
        return AudioFilterScope(
            lowCutHz = obj.optDouble("lowCutHz", 20.0).toFloat(),
            highCutHz = obj.optDouble("highCutHz", 20000.0).toFloat(),
            thresholdGate = obj.optDouble("thresholdGate", 0.0).toFloat(),
            ceilingClamp = obj.optDouble("ceilingClamp", 1.0).toFloat(),
            gain = obj.optDouble("gain", 1.0).toFloat(),
        )
    }

    private fun encodeLayer(l: VisualLayer): JSONObject = JSONObject().apply {
        put("id", l.id)
        put("styleType", l.styleType.name)
        put("enabled", l.enabled)
        put("solo", l.solo)
        put("locked", l.locked)
        put("blendMode", l.blendMode.name)
        put("audioFilter", encodeAudioFilter(l.audioFilter))
        put("opacity", encodeScopedParam(l.opacity))
        put("scale", encodeScopedParam(l.scale))
        put("rotationSpeed", encodeScopedParam(l.rotationSpeed))
        put("offsetX", encodeScopedParam(l.offsetX))
        put("offsetY", encodeScopedParam(l.offsetY))
        put("primaryParam", encodeScopedParam(l.primaryParam))
        put("secondaryParam", encodeScopedParam(l.secondaryParam))
        put("tertiaryParam", encodeScopedParam(l.tertiaryParam))
    }

    private fun decodeLayer(obj: JSONObject?): VisualLayer? {
        if (obj == null) return null
        val style = enumOrDefault(obj.optString("styleType"), VisualStyleType.RADIAL_MANDALA)
        val id = obj.optString("id").ifBlank { "l_${System.nanoTime()}" }
        val template = PresetCatalog.createLayer(style, id = id)
        return template.copy(
            enabled = obj.optBoolean("enabled", true),
            solo = obj.optBoolean("solo", false),
            locked = obj.optBoolean("locked", false),
            blendMode = enumOrDefault(obj.optString("blendMode"), LayerBlendMode.SCREEN),
            audioFilter = decodeAudioFilter(obj.optJSONObject("audioFilter")),
            opacity = decodeScopedParam(obj.optJSONObject("opacity"), template.opacity),
            scale = decodeScopedParam(obj.optJSONObject("scale"), template.scale),
            rotationSpeed = decodeScopedParam(obj.optJSONObject("rotationSpeed"), template.rotationSpeed),
            offsetX = decodeScopedParam(obj.optJSONObject("offsetX"), template.offsetX),
            offsetY = decodeScopedParam(obj.optJSONObject("offsetY"), template.offsetY),
            primaryParam = decodeScopedParam(obj.optJSONObject("primaryParam"), template.primaryParam),
            secondaryParam = decodeScopedParam(obj.optJSONObject("secondaryParam"), template.secondaryParam),
            tertiaryParam = decodeScopedParam(obj.optJSONObject("tertiaryParam"), template.tertiaryParam),
        )
    }

    private fun encodeTransformer(t: TransformerNode): JSONObject = JSONObject().apply {
        put("id", t.id)
        put("type", t.type.name)
        put("enabled", t.enabled)
        put("locked", t.locked)
        put("audioFilter", encodeAudioFilter(t.audioFilter))
        put("amount", encodeScopedParam(t.amount))
        put("frequency", encodeScopedParam(t.frequency))
        put("phaseOrDrift", encodeScopedParam(t.phaseOrDrift))
    }

    private fun decodeTransformer(obj: JSONObject?): TransformerNode? {
        if (obj == null) return null
        val type = enumOrDefault(obj.optString("type"), TransformerType.KALEIDOSCOPE_MIRROR)
        val id = obj.optString("id").ifBlank { "t_${System.nanoTime()}" }
        val template = PresetCatalog.createTransformer(type, id = id)
        return template.copy(
            enabled = obj.optBoolean("enabled", true),
            locked = obj.optBoolean("locked", false),
            audioFilter = decodeAudioFilter(obj.optJSONObject("audioFilter")),
            amount = decodeScopedParam(obj.optJSONObject("amount"), template.amount),
            frequency = decodeScopedParam(obj.optJSONObject("frequency"), template.frequency),
            phaseOrDrift = decodeScopedParam(obj.optJSONObject("phaseOrDrift"), template.phaseOrDrift),
        )
    }

    private fun encodeColor(c: ColorEngineConfig): JSONObject = JSONObject().apply {
        put("paletteId", c.paletteId)
        put("useCustomPalette", c.useCustomPalette)
        put("customColors", JSONArray().apply { c.customColors.forEach { put(it) } })
        put("hueShift", encodeScopedParam(c.hueShift))
        put("hueDriftSpeed", encodeScopedParam(c.hueDriftSpeed))
        put("saturation", encodeScopedParam(c.saturation))
        put("contrast", encodeScopedParam(c.contrast))
        put("beatFlash", encodeScopedParam(c.beatFlash))
        put("locked", c.locked)
    }

    private fun decodeColor(obj: JSONObject): ColorEngineConfig {
        val base = PresetCatalog.defaultColorEngine(obj.optString("paletteId", "acid_sunset"))
        val colorsArr = obj.optJSONArray("customColors")
        val custom = if (colorsArr != null && colorsArr.length() >= 2) {
            (0 until colorsArr.length()).map { colorsArr.optLong(it, 0xFF00F5D4) }
        } else base.customColors
        return base.copy(
            useCustomPalette = obj.optBoolean("useCustomPalette", false),
            customColors = custom,
            hueShift = decodeScopedParam(obj.optJSONObject("hueShift"), base.hueShift),
            hueDriftSpeed = decodeScopedParam(obj.optJSONObject("hueDriftSpeed"), base.hueDriftSpeed),
            saturation = decodeScopedParam(obj.optJSONObject("saturation"), base.saturation),
            contrast = decodeScopedParam(obj.optJSONObject("contrast"), base.contrast),
            beatFlash = decodeScopedParam(obj.optJSONObject("beatFlash"), base.beatFlash),
            locked = obj.optBoolean("locked", false),
        )
    }

    private inline fun <reified T : Enum<T>> enumOrDefault(name: String, default: T): T {
        return enumValues<T>().firstOrNull { it.name == name } ?: default
    }
}
