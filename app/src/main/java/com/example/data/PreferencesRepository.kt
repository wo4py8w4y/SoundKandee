package com.example.data

import android.content.Context
import androidx.datastore.core.DataStore
import androidx.datastore.preferences.core.Preferences
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.stringPreferencesKey
import androidx.datastore.preferences.preferencesDataStore
import com.example.model.AudioFilterScope
import com.example.model.AudioSourceType
import com.example.model.ColorEngineConfig
import com.example.model.DspSettings
import com.example.model.FftWindowType
import com.example.model.LayerBlendMode
import com.example.model.ModulationDriver
import com.example.model.PresetCatalog
import com.example.model.ResponseCurve
import com.example.model.ScopedParam
import com.example.model.TransformerNode
import com.example.model.TransformerType
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
        )
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

        return PersistedWorkspace(
            audioSource = source,
            dspSettings = dsp,
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
            lfoHz = obj.optDouble("lfoHz", fallback.lfoHz.toDouble()).toFloat().coerceIn(0.05f, 8f),
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
