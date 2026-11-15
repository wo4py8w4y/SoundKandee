# SoundKandee — Blow-by-Blow Architecture & Unusual Code Paths

This document provides a complete module-by-module walkthrough of **SoundKandee** alongside transparent engineering reasoning for every non-obvious design choice, workaround, and architectural quirk in the codebase.

---

## 1. End-to-End Signal & Render Flow

```text
┌───────────────────────────┐   ┌────────────────────────────────┐
│   Live Audio Ingestion    │   │     Idle Synthetic Fallback    │
│ • Output Mix (Visualizer) │   │ SyntheticMusicGenerator (44.1k)│
│ • App Audio (MediaProj.)  │   │ Techno / Arp / Dub / Ambient   │
│ • Mic (AudioRecord)       │   └───────────────┬────────────────┘
└─────────────┬─────────────┘                   │ yields instantly on real signal
              └───────────────┬─────────────────┘
                              ▼
             ┌─────────────────────────────────┐
             │    ConfigurableDspProcessor     │
             │ • Hann / Hamming / Blackman / □ │
             │ • Radix-2 Cooley-Tukey 1024-FFT │
             │ • 64 Log Bands + Spectral Flux  │
             └────────┬───────────────┬────────┘
                      │ Beat Onsets   │ AudioFrame (~60Hz)
                      ▼               ▼
       ┌──────────────────┐   ┌──────────────────────────────────┐
       │  TempoSyncEngine │   │      ModulationEvaluator         │
       │ • Auto-Beat PLL  │   │ • 22 Drivers -> [scopeMin..Max]  │
       │ • Tap-Tempo Avg  │──▶│ • 5 Curves + Attack/Release AR   │
       │ • 3 Master LFOs  │   │ • Beat-Grid Quantize Hold        │
       └──────────────────┘   └───────────────┬──────────────────┘
                                              ▼
                       ┌────────────────────────────────────────┐
                       │      Split CPU / GPU VisualizerCanvas  │
                       │ • Offscreen Ping-Pong ImageBitmap Echo │
                       │ • 12 Vector/Canvas Style Renderers     │
                       │ • Fused AGSL Post-FX Layer (API 33+)   │
                       └────────────────────────────────────────┘
```

---

## 2. Ten Oddities & Unusual Engineering Paths (And Why They Exist)

### Oddity 1 — Split-Pipeline Rendering Guard Against Skia's `RuntimeShader` Software Crash
- **Where**: `VisualizerCanvas.kt` (offscreen `echoFront`/`echoBack` buffers vs. `Modifier.graphicsLayer { renderEffect }`) and `LayerRenderers.kt` (`drawNebulaFluid` checking `drawContext.canvas.nativeCanvas.isHardwareAccelerated`).
- **What Happened / The Quirk**: Jetpack Compose's offscreen `Canvas(ImageBitmap(w, h))` is **always software-backed** on Android (`BaseCanvas`). Drawing an AGSL `RuntimeShader` brush (`Nebula Plasma`) or attaching a hardware `RuntimeShader` into that offscreen buffer crashes with:
  `java.lang.IllegalArgumentException: Software rendering doesn't support RuntimeShader`.
- **Reasoning & Solution**: Instead of disabling `FEEDBACK_ECHO` trails when `NEBULA_FLUID` or MilkDrop shaders are active:
  1. `LayerRenderers.drawNebulaFluid` inspects `drawContext.canvas.nativeCanvas.isHardwareAccelerated` and seamlessly falls back to multi-lobe radial gradient plasma clouds whenever drawing into a software offscreen bitmap or on API 24–32 devices.
  2. All 13+ spatial/pixel AGSL MilkDrop shaders run **outside** the offscreen feedback pass on the hardware-accelerated outer `Canvas` via `Modifier.graphicsLayer { renderEffect = agslHolder.buildCompositeRenderEffect(...)?.asComposeRenderEffect() }`.
  3. On API 24–32, geometric Compose overlays in `VisualizerCanvas` render crisp fallbacks for *Ripple Shockwaves*, *CRT Scanlines*, *Beat Bloom*, *Kaleidoscope*, and *Tile Mosaic*.

---

### Oddity 2 — Codename Archeology: `ChromaWave` Identifiers Inside `SoundKandee`
- **Where**: `app/build.gradle.kts` (`applicationId = "com.aistudio.chromawave.vzxqkm"`), `namespace = "com.example"`, `PreferencesRepository.kt` (`chromawave_prefs`), `ChromaWaveViewModel`, `ChromaWaveScreen`, `ChromaWaveTheme`.
- **The Quirk**: All external branding, `strings.xml`, `settings.gradle.kts`, `metadata.json`, and launcher assets read **SoundKandee**, while internal Kotlin symbols and package IDs still say `ChromaWave` or `com.example`.
- **Reasoning**:
  - Changing `applicationId` after initial project creation breaks existing device installs, keystores, and upgrade continuity.
  - Renaming `chromawave_prefs` or `chromawave_workspace_v1` in Jetpack DataStore would silently orphan every user's saved presets and workspace settings.
  - Keeping internal Kotlin class names untouched avoided risky cross-file churn while keeping external user identity 100% consistent as **SoundKandee**.

---

### Oddity 3 — Two Completely Separate Front-End Ingestion Math Paths in One DSP Class
- **Where**: `ConfigurableDspProcessor.kt` (`processPcm16` vs. `processVisualizerBytes`).
- **The Quirk**: One audio analyzer class exposes two separate entry points with asymmetrical preprocessing before converging on `buildAudioFrame(...)`.
- **Reasoning**:
  - `AudioRecord` (Mic) and `AudioPlaybackCaptureService` (App Audio `MediaProjection`) provide true **signed 16-bit PCM (`ShortArray`)**, which needs windowing (*Hann / Hamming / Blackman / Rect*) and a custom Radix-2 Cooley-Tukey `fftInPlace` transform (`fftSize = 1024`).
  - Android's `android.media.audiofx.Visualizer` (Session `0` Output Mix) cannot yield clean 16-bit PCM; it yields **unsigned 8-bit bytes (`0..255` biased at `128`)** for waveforms and pre-baked interleaved `re/im` signed FFT bytes.
  - Normalizing both front-ends into unified linear magnitude + 256-sample float waveforms lets every downstream preset, LFO, beat detector, and bandpass filter behave identically regardless of input source.

---

### Oddity 4 — Global Singleton `PlaybackCaptureBridge` Instead of Bound Service AIDL or `StateFlow` Beats
- **Where**: `PlaybackCaptureBridge.kt`, `AppPlaybackCaptureService.kt`, `AudioCaptureCoordinator.kt`.
- **The Quirk**: `AppPlaybackCaptureService` pushes frames through mutable function references on an `object` singleton (`PlaybackCaptureBridge.onAudioFrame`), and `TempoSyncEngine.onAutoBeatDetected` is invoked synchronously inside `handleRealAudioFrame(...)` rather than collecting `audioFrame: StateFlow<AudioFrame>`.
- **Reasoning**:
  - `MediaProjection` playback capture must run in a foreground service on Android 10+. Since both the service and UI run in the same process, a lightweight callback bridge avoids AIDL/Binder marshalling latency for 60Hz `FloatArray(512)` buffers.
  - **`StateFlow` conflates rapid emissions**: if the UI thread pauses for 20ms, an intermediate frame with `beatOnset == true` would be silently overwritten before the collector reads it, starving the BPM tracker. Forwarding `frame.beatTimestampMs` synchronously on the audio callback thread preserves microsecond beat timing fidelity.

---

### Oddity 5 — Zero-Recomposition 60fps Canvas + Throttled 20Hz HUD Snapshot + `autoAdvance = false` Tests
- **Where**: `VisualizerCanvas.kt` (`frameTick`, `uiTempoThrottle`), `ChromaWaveViewModel.kt` (`liveEvaluatedValues = mutableStateMapOf()`), and `ExampleRobolectricTest.kt`.
- **The Quirk**: The visualizer evaluates dozens of `ScopedParam`s inside `LaunchedEffect { withFrameNanos { ... } }` instead of emitting 60fps `StateFlow` updates from the `ViewModel`.
- **Reasoning**:
  - Triggering full Compose UI tree recomposition across nested sliders, dock tabs, and HUD rows at 60Hz wastes CPU/battery and drops shader frames. Keeping `evaluatedLayerList` and `activeTxValues` strictly read inside the `Canvas { val tick = frameTick ... }` draw phase limits invalidation to the GPU canvas alone.
  - Live telemetry dots read per-key entries from `liveEvaluatedValues` (`mutableStateMapOf`), and `onTempoSnapshot` is throttled every 3rd frame (~20Hz) so BPM/beat LEDs stay responsive without spamming state updates.
  - **Testing Side Effect**: Because `withFrameNanos` never becomes "idle", Robolectric Compose UI tests explicitly set `composeRule.mainClock.autoAdvance = false` and step time deterministically (`advanceTimeBy(...)`).

---

### Oddity 6 — Two-Stage Fused AGSL Shader vs. Chained `RenderEffect`s
- **Where**: `AgslShaders.kt` (`FEEDBACK_WARP_SRC` + `COMPOSITE_FX_SRC` + `Api33ShaderHolder`).
- **The Quirk**: Rather than building modular `RenderEffect` objects per active transformer and chaining them with `RenderEffect.createChainEffect`, all MilkDrop UV distortions (*Warp*, *Tunnel ZoomExp*, *Stretch*, *Pinwheel*, *4-Way Mirror*, *Polar Unwrap*), *WaveFold*, *Vortex*, *Ripple*, *Chromatic Split*, *CRT Scanlines*, *Solarize*, and *Posterize* live inside one composite AGSL uber-shader.
- **Reasoning**: Chaining 8–12 offscreen render-target blits per frame saturates mobile GPU memory bandwidth on mid-range SoCs. Fusing UV warping into a single coordinate-remap pass and RGB post-FX into minimal texture taps (`background.eval(...)`) runs multiple stacked MilkDrop transforms simultaneously at locked 60fps on API 33+.

---

### Oddity 7 — Hand-Rolled `org.json` Persistence with Commented-Out Room/Moshi/Firebase Dependencies
- **Where**: `PreferencesRepository.kt` and `app/build.gradle.kts`.
- **The Quirk**: `app/build.gradle.kts` retains commented-out references to Room, Moshi, and Firebase while persistence uses `org.json.JSONObject` stored in Jetpack DataStore (`Preferences`).
- **Reasoning**:
  - Cloud sync/Firebase was explicitly declined, keeping user presets 100% local and offline.
  - Avoiding `kotlinx-serialization` compiler plugins, Moshi codegen, or Room `ksp` keeps APK size small and build times fast, while every `decode*` routine uses `optDouble` / `optString` / `optBoolean` defaults so presets created before LFO/Tempo/MilkDrop updates load cleanly with zero schema migration failures.

---

### Oddity 8 — Synthetic Idle Music Feeding Real DSP Rather Than Fake UI Curves
- **Where**: `SyntheticMusicGenerator.kt` and `AudioCaptureCoordinator.kt` (`ensureIdleSynthLoopRunning`).
- **The Quirk**: When idle or silent, SoundKandee synthesizes actual 44.1 kHz 16-bit PCM buffers (pitch-swept kicks, rolling sub-bass, pentatonic arpeggios, noise-burst hi-hats) and runs them through a dedicated `ConfigurableDspProcessor` instance.
- **Reasoning**: Injecting fake `bands64` arrays directly into the UI would leave FFT windowing, noise-floor gating, spectral centroid tracking, bandpass layer filters, and the auto-beat PLL untested during idle playback. Running synthesized PCM-16 through the real DSP ensures every modulation slider and BPM confidence meter behaves authentically, while `lastRealSignalMs` guarantees instant handoff (`isSynthEmitting = false`) on the first real audio frame.

---

### Oddity 9 — Permissionless Hardware Tap Gesture with Synthetic IIR High-Pass Fallback
- **Where**: `DeviceTapDetector.kt`.
- **The Quirk**: Physical chassis-tap detection works without any Android runtime permissions and checks `TYPE_LINEAR_ACCELERATION` first, falling back to `TYPE_ACCELEROMETER` with a manual `alpha = 0.8f` gravity subtraction loop and a 4-sample warmup gate.
- **Reasoning**: Budget/headless/emulator hardware often lacks fused `TYPE_LINEAR_ACCELERATION` virtual sensors. Ignoring the first 4 raw accelerometer samples while initializing the low-pass gravity estimate prevents the initial `~9.81 m/s²` static gravity vector from false-firing randomization when the listener attaches.

---

### Oddity 10 — Non-Linear Beat-Grid Quantization & Inverted Min/Max Guard in `ScopedParam`
- **Where**: `ScopedParam.kt` (`orderedMin`/`orderedMax`, `quantizedHoldMap`) and `TempoSyncEngine.kt` (`recordTap` phase snap, `step` monotonic clock).
- **The Quirk**:
  1. `ScopedParam` stores `scopeMin` and `scopeMax` freely, computing `orderedMin = min(scopeMin, scopeMax)` and `orderedMax = max(scopeMin, scopeMax)` dynamically.
  2. `TempoSyncEngine.recordTap()` snaps `totalBeats` *forward* to `floor(totalBeats) + 1.0`, never backward; `step()` only bleeds auto-beat PLL error forward (`if (applied > 0) totalBeats += applied`).
- **Reasoning**:
  - Allowing dual-thumb range sliders to cross gracefully without crashing prevents touch-drag edge glitches.
  - Monotonic forward phase snapping prevents visual oscillators (`Oscilloscope`, `Nebula`, `Pinwheel`) from suddenly spinning backward or stuttering on a downbeat correction.
  - `quantizedHoldMap` caches the sampled normalized value keyed by `(paramKey -> tickIndex)` so enabled transformer parameters lock crisply to musical bars/quarter/eighth/sixteenth subdivisions.

---

## 3. Complete File Inventory

| File | Responsibility |
| :--- | :--- |
| `MainActivity.kt` | Edge-to-edge system bar styling & Compose host |
| `audio/AudioFrame.kt` | Per-frame waveform, 64-band FFT, 512-bin spectrum, beat onset & synthetic flag |
| `audio/ConfigurableDspProcessor.kt` | Dual PCM-16 / Visualizer byte ingestion, FFT windowing, flux beat detector |
| `audio/AudioCaptureCoordinator.kt` | Multi-source capture router, mic/output loops, silence timeout & synth handoff |
| `audio/AppPlaybackCaptureService.kt` | Android 10+ `MediaProjection` foreground audio capture service |
| `audio/PlaybackCaptureBridge.kt` | Low-overhead in-process bridge between capture service & coordinator |
| `audio/TempoSyncEngine.kt` | Thread-safe auto-beat BPM tracker, octave folding, tap-tempo & 3 LFO clocks |
| `audio/DeviceTapDetector.kt` | `SensorEventListener` impulse detector + unit-testable `processSample()` |
| `audio/SyntheticMusicGenerator.kt` | 4-style musical PCM-16 synthesizer for idle/silence operation |
| `model/ScopedParam.kt` | `ScopedParam`, `AudioFilterScope`, 22 drivers, 5 curves & `ModulationEvaluator` |
| `model/TempoAndLfoModels.kt` | `TempoSyncConfig`, `BeatDivision`, `LfoWaveform`, `LfoChannelConfig`, `LfoMath` |
| `model/VisualizerModels.kt` | 12 styles, 17 transformers, `ColorEngineConfig`, `DspSettings`, `VisualizerPreset` |
| `model/PresetCatalog.kt` | 12 palettes, 8 built-in presets, bounded `mutate()` & `randomize()`, ID cloning |
| `render/VisualizerCanvas.kt` | 60fps `withFrameNanos` orchestrator, ping-pong echo buffer, gestures & API fallbacks |
| `render/LayerRenderers.kt` | 12 generative drawing engines + particle pool + HW acceleration guard |
| `render/AgslShaders.kt` | API 33+ AGSL shaders (`Nebula`, `Feedback Warp`, `Composite Post-FX`) |
| `render/ColorEngine.kt` | HSL hue rotation, saturation, luma boost & beat chroma flash |
| `data/PreferencesRepository.kt` | Jetpack DataStore + `org.json` serialization for workspace & user presets |
| `ui/ChromaWaveViewModel.kt` | Central `StateFlow` state holder, preset CRUD, tempo/LFO/tap/synth wiring |
| `ui/ChromaWaveScreen.kt` | Responsive portrait/landscape HUD, SoundKandee candy branding, dock/rail host |
| `ui/components/*` | `StudioDock`, `Layers`/`FX`/`Color`/`Audio` tabs, `LfoAndTempoTab`, `PresetsTab`, `ScopedParamControls` |
| `ui/theme/*` | Bundled OFL fonts (`Syne`, `Plus Jakarta Sans`, `JetBrains Mono`) & dark neon scheme |
