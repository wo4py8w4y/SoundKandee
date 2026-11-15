<div align="center">
  <img src="docs/assets/soundkandee-logo.jpg" alt="SoundKandee candy-lettered K-e-e logo" width="680" />
  <br />
  <br />
  <img src="docs/assets/soundkandee-icon.jpg" alt="SoundKandee candy Kee monogram icon" width="92" />

  <h1>SoundKandee</h1>
  <p>
    <strong>Modular, GPU-Accelerated Android Audio Visualizer with Bounded Scoped Modulation, MilkDrop Shaders, BPM Tempo-Sync, LFO Buses, Hardware Tap Randomization &amp; Synthetic Idle Music.</strong>
  </p>

  <p>
    <a href="LICENSE"><img src="https://img.shields.io/badge/License-Apache_2.0-00F5D4?style=for-the-badge" alt="License: Apache 2.0" /></a>
    <img src="https://img.shields.io/badge/Platform-Android_7.0%2B_(API_24--36)-FF2A85?style=for-the-badge" alt="Android API 24-36" />
    <img src="https://img.shields.io/badge/Network_Permission-None_(100%25_Offline)-FFB703?style=for-the-badge" alt="100% Offline FOSS" />
    <a href="docs/index.html"><img src="https://img.shields.io/badge/Website-Product_Microsite-7B2CBF?style=for-the-badge" alt="Product Microsite" /></a>
    <a href="docs/architecture.md"><img src="https://img.shields.io/badge/Engineering-Architecture_%26_Oddities-00BBF9?style=for-the-badge" alt="Architecture Deep Dive" /></a>
  </p>
</div>

---

## Live GitHub Webpages & Microsite

Once GitHub Pages is enabled (either via **Settings → Pages → Deploy from a branch (`main` / `/docs`)** or via the included [GitHub Pages workflow](.github/workflows/pages.yml)):

- **Product Microsite & Interactive Web Visualizer**: [`docs/index.html`](docs/index.html)
- **Complete Architecture & Unusual Code Paths Webpage**: [`docs/architecture.html`](docs/architecture.html)
- **Markdown Blow-by-Blow Engineering Log**: [`docs/architecture.md`](docs/architecture.md)

---

## Feature Set

### 1. Triple-Source Real-Time Audio Capture
- **Output Mix (`Visualizer` Session `0`)**: Visualizes global device audio output.
- **App Playback (`MediaProjection` + Foreground Service)**: Captures direct 44.1 kHz 16-bit PCM playback from other apps on Android 10+.
- **Live Microphone (`AudioRecord`)**: Captures live room/instrument acoustics.
- **Synthetic Idle Music Fallback (`SyntheticMusicGenerator`)**: When no live signal is active or input stays silent past a configurable delay, synthesizes multi-voice musical PCM-16 waveforms (*Techno Pulse*, *Psychedelic Arp*, *Dub Sub-Bass*, *Ambient Drift*) fed directly into the real FFT/beat DSP pipeline—yielding instantly the moment live music resumes.

### 2. Configurable DSP & Bounded Parameter Scopes (`ScopedParam`)
- Selectable FFT windows (**Hann**, **Hamming**, **Blackman**, **Rectangular**), Radix-2 Cooley-Tukey FFT, 64 logarithmic spectral bands, bass-tilt EQ, noise-floor gating, and spectral-flux beat onset detection.
- Every layer property, color parameter, and post-FX parameter runs inside user-defined **`[scopeMin .. scopeMax]`** bounds inside `[hardMin .. hardMax]`, driven by **22 Modulation Drivers** and **5 Response Curves** (*Linear*, *Exp*, *Log*, *Quantize*, *Gate*) with independent attack/release envelopes, inversion, locking, and per-layer **20 Hz–20 kHz bandpass filter windows**.

### 3. 12 Overlapping Visual Generators + 17 Post-FX & MilkDrop Transformers
- **8 Core Generators**: *Oscilloscope Ribbon*, *Radial Mandala*, *Nebula Plasma* (AGSL API 33+ with vector fallback), *Tunnel Warp Grid*, *Particle Starburst*, *Terrain Horizon*, *Harmonic Spirograph*, *Liquid Metaballs*.
- **4 MilkDrop-Inspired Generators**: *MilkDrop Wave Matrix*, *MilkDrop Vector Flow*, *MilkDrop Custom Shapes*, *MilkDrop Solar Corona*.
- **17 Stackable Transformers**: *Kaleidoscope Mirror*, *Feedback Echo Trails* (ping-pong offscreen buffer), *Wave Fold & Phase Warp*, *Chromatic RGB Split*, *Vortex Swirl*, *Ripple Shockwaves*, *Beat Bloom & Strobe*, *Tile Mosaic Matrix*, *CRT Scanline & Glow*, plus **8 MilkDrop Warp & Color Shaders** (*Liquid Warp*, *Zoom & ZoomExp*, *Anamorphic Stretch & Pan*, *Pinwheel Twist*, *Solarize Fold*, *Contour Bands*, *4-Way Mirror*, *Polar Unwrap*).

### 4. Tempo-Sync Engine, Master LFO Bank & Hardware Tap Randomizer
- **Tempo-Sync Engine**: Auto-Beat BPM tracking (65–180 BPM octave folding, median outlier filtering, PLL phase alignment), Tap-Tempo BPM averaging with downbeat snap (`÷2`, `1x`, `×2`, `Sync 1`), visual oscillator clock locking, and beat-grid FX step quantization (`4 Bars` to `1/16`).
- **3 Global Master LFO Buses (`LFO1`–`LFO3`)**: Real-time oscilloscope view, 7 waveforms (*Sine*, *Triangle*, *Saw +*, *Saw -*, *Square*, *S&H*, *Chaos*), free Hz (`0.05–12 Hz`) or BPM beat divisions, phase (`0°–360°`), pulse-width/skew, beat retrigger, and one-tap quick-patch routing.
- **Physical Device Tap-to-Randomize**: Permissionless accelerometer impulse detector (`TYPE_LINEAR_ACCELERATION` with gravity-filtered `TYPE_ACCELEROMETER` fallback) triggering *Randomize Next Options*, *Mutate Within Scopes*, or *Switch Random Preset* with haptic feedback.
- **Preset Manager & Edge-to-Edge Studio UI**: 8 built-in presets, 12 curated palettes + 2–5 stop custom editor, complete save/overwrite/duplicate/rename/delete/revert preset management backed locally by Jetpack DataStore, Zen Mode, and adaptive Compact Bottom Dock / Wide Side Rail layouts.

---

## FOSS Verification & Compliance Audit

SoundKandee is verified to be **100% Free and Open Source Software (FOSS)**, free of cost, free of ads/tracking, and builds with **zero proprietary runtime dependencies**:

| Audit Dimension | Verified Status | Evidence |
| :--- | :--- | :--- |
| **Source License** | **Apache License 2.0** (OSI-Approved) | [`LICENSE`](LICENSE) & [`NOTICE`](NOTICE) |
| **Release Runtime Dependencies** | **100% Apache-2.0** (`kotlin-stdlib`, `androidx.*`, `kotlinx-coroutines`) | Verified via `gradle :app:dependencies --configuration releaseRuntimeClasspath` |
| **Google Play Services / Firebase / Ads** | **0 runtime dependencies** | All Firebase/Room/Moshi entries remain commented out in `app/build.gradle.kts` |
| **Network Access / Telemetry** | **100% Offline** | `INTERNET` permission is **not declared** in `AndroidManifest.xml` |
| **Bundled Fonts** | **SIL OFL 1.1** | `syne.ttf`, `plus_jakarta_sans.ttf`, `jetbrains_mono.ttf` |
| **APK Dependency Metadata Blob** | **Disabled** | `dependenciesInfo { includeInApk = false }` in `app/build.gradle.kts` |
| **F-Droid Recipe Note** | **Minor build-plugin passthrough** | `com.google.gms.google-services` plugin (`Apache-2.0`) is applied in `app/build.gradle.kts` with `missingGoogleServicesStrategy = WARN` and `googleServices.missing.passthrough=true` (`gradle.properties`), injecting zero runtime code; strict automated F-Droid metadata scanners can strip that unused plugin line during build recipe preparation. |

---

## Building & Running Tests

```bash
# Build Debug APK
gradle assembleDebug

# Run all JVM Unit & Robolectric Compose UI Tests (no emulator needed)
gradle :app:testDebugUnitTest
```

> **Note:** All environment secrets remain unconfigured/commented out in `.env.example`; SoundKandee operates completely locally with no API keys required.

---

## Repository Structure

```text
.
├── app/src/main/java/com/example/
│   ├── audio/          # Dual DSP processor, capture coordinator, tempo PLL, LFO clock, tap sensor, synth music
│   ├── data/           # Jetpack DataStore + forward-compatible org.json workspace & preset persistence
│   ├── model/          # ScopedParam modulation matrix, 12 styles, 17 transformers, LFO/Tempo models, 8 presets
│   ├── render/         # Split CPU/GPU Canvas, ping-pong echo buffer, 12 draw engines, AGSL shaders (API 33+)
│   └── ui/             # ViewModel, adaptive HUD + Studio Dock, LFO/Tempo scopes, ScopedParam & Preset controls
├── app/src/test/       # DSP, TempoSync, LFO quantization, TapDetector, Synth & Robolectric Compose UI tests
├── docs/               # GitHub Pages product microsite + architecture webpage & assets
└── .github/workflows/  # Android CI test runner + GitHub Pages deployment workflows
```

Read the complete file-by-file breakdown and unusual engineering decisions in **[`docs/architecture.md`](docs/architecture.md)** or on the **[Architecture Webpage](docs/architecture.html)**.
