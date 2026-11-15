(function () {
  const stylesCatalog = [
    { name: "Oscilloscope Ribbon", tag: "Core", desc: "Multi-harmonic glowing wave contours & Lissajous trails driven by custom bandpass slices." },
    { name: "Radial Mandala", tag: "Core", desc: "N-fold symmetric polar FFT petals & sacred geometry responding to transient envelopes." },
    { name: "Nebula Plasma", tag: "AGSL GPU", desc: "Audio-warped AGSL shader plasma & luminous aurora clouds (with vector fallback on API 24–32)." },
    { name: "Tunnel Warp Grid", tag: "Core", desc: "3D hyperspace wireframe rings rushing with beat velocity & twist angle scopes." },
    { name: "Particle Starburst", tag: "Core", desc: "Beat-reactive 240-particle orbital constellation & velocity pool." },
    { name: "Terrain Horizon", tag: "Core", desc: "Retro-futuristic scrolling FFT mountain ridges with perspective tilt." },
    { name: "Harmonic Spirograph", tag: "Core", desc: "Hypotrochoid trace curves sculpted by dominant frequency ratios." },
    { name: "Liquid Metaballs", tag: "Core", desc: "Organic interference blobs morphing smoothly with sub-bass pressure." },
    { name: "MilkDrop Wave Matrix", tag: "MilkDrop", desc: "Classic MilkDrop wave_mode plotter with circular, Lissajous & dot-burst geometry." },
    { name: "MilkDrop Vector Flow", tag: "MilkDrop", desc: "Curl-noise streamline motion trails flowing along bass & treble currents." },
    { name: "MilkDrop Custom Shapes", tag: "MilkDrop", desc: "Nested beat-pulsing geometric polygons with glowing rotational morphs." },
    { name: "MilkDrop Solar Corona", tag: "MilkDrop", desc: "Radiant frequency flares & plasma arcs erupting from the spectral core." }
  ];

  const transformersCatalog = [
    { name: "Kaleidoscope Mirror", tag: "Spatial", desc: "2x–24x rotational wedge mirroring across the active layer composite." },
    { name: "Feedback Echo Trails", tag: "Echo", desc: "Offscreen ping-pong persistence buffer with zoom drift & spiral rotation." },
    { name: "Wave Fold & Phase Warp", tag: "AGSL", desc: "Trigonometric wavefolding & sinusoidal spatial distortion." },
    { name: "Chromatic RGB Split", tag: "AGSL", desc: "Spectral prism aberration separating red, green & blue channels." },
    { name: "Vortex Swirl", tag: "AGSL", desc: "Polar angular twist orbiting the interactive focal point." },
    { name: "Ripple Shockwaves", tag: "Hybrid", desc: "Concentric beat-triggered refractive interference rings & vector pulses." },
    { name: "Beat Bloom & Strobe", tag: "Hybrid", desc: "Transient luminance halo, radial pulse & micro-shake." },
    { name: "Tile Mosaic Matrix", tag: "Spatial", desc: "Multi-cell grid replication with alternate quadrant mirroring." },
    { name: "CRT Scanline & Glow", tag: "AGSL", desc: "Analog phosphor scanlines & barrel curvature vignette (with Canvas fallback)." },
    { name: "MilkDrop Liquid Warp", tag: "MilkDrop", desc: "Per-pixel sinusoidal warp_scale feedback melting pixels organically." },
    { name: "MilkDrop Zoom & ZoomExp", tag: "MilkDrop", desc: "Non-linear radial zoom exponent driving hyperspace tunnel pull." },
    { name: "MilkDrop Stretch & Pan", tag: "MilkDrop", desc: "Independent sx/sy anamorphic stretch & dx/dy directional drift." },
    { name: "MilkDrop Pinwheel Twist", tag: "MilkDrop", desc: "Classic rot + sin(rad) + cos(ang) pinwheel vortex equations." },
    { name: "MilkDrop Solarize Fold", tag: "MilkDrop", desc: "Trigonometric RGB solarization & electric edge fold." },
    { name: "MilkDrop Contour Bands", tag: "MilkDrop", desc: "Quantizes luminance into glowing topographic contour bands." },
    { name: "MilkDrop 4-Way Mirror", tag: "MilkDrop", desc: "4-quadrant kaleidoscope mirror with optional RGB inversion mix." },
    { name: "MilkDrop Polar Unwrap", tag: "MilkDrop", desc: "Projects Cartesian coordinates into polar spiral tunnel space." }
  ];

  const palettesCatalog = [
    { name: "Acid Sunset", colors: ["#FF2A85", "#FF7B00", "#FFD166", "#7209B7"] },
    { name: "Bioluminescent Abyss", colors: ["#00F5D4", "#00BBF9", "#3A0CA3", "#72EFDD"] },
    { name: "Ultraviolet Prism", colors: ["#9B5DE5", "#F15BB5", "#00F5D4", "#FEE440"] },
    { name: "Infrared Thermal", colors: ["#FF0A54", "#FF758F", "#FFB703", "#FFF3B0"] },
    { name: "Liquid Mercury", colors: ["#E0FBFC", "#98C1D9", "#5C7AEA", "#EE6C4D"] },
    { name: "Forest Psychedelia", colors: ["#06D6A0", "#80ED99", "#D81159", "#5E60CE"] },
    { name: "Cyber Noir", colors: ["#00F0FF", "#FF0055", "#7B2CBF", "#0B132B"] },
    { name: "Solar Flare", colors: ["#FF4800", "#FF9E00", "#FFD000", "#8F00FF"] },
    { name: "Pastel Dream", colors: ["#FFAFCC", "#BDE0FE", "#CDB4DB", "#A2D2FF"] },
    { name: "CRT Phosphor", colors: ["#38FF62", "#00F5D4", "#0096C7", "#B5FF8A"] },
    { name: "Aurora Borealis", colors: ["#2EC4B6", "#80FFDB", "#5390D9", "#C77DFF"] },
    { name: "Vaporwave Neon", colors: ["#FF71CE", "#01CDFE", "#05FFA1", "#B967FF"] }
  ];

  function populateCatalog(filter) {
    const grid = document.getElementById("catalogGrid");
    if (!grid) return;

    let items = [];
    if (filter === "styles") items = stylesCatalog;
    else if (filter === "milkdrop") {
      items = stylesCatalog.concat(transformersCatalog).filter(function (i) { return i.tag === "MilkDrop"; });
    } else if (filter === "transformers") items = transformersCatalog;
    else items = stylesCatalog.concat(transformersCatalog);

    grid.innerHTML = items.map(function (item) {
      const badgeClass = item.tag === "MilkDrop" ? 'class="milk-pill"' : 'class="card-tag"';
      return '<div class="catalog-item">' +
        '<h4><span>' + item.name + '</span><span ' + badgeClass + '>' + item.tag + '</span></h4>' +
        '<p>' + item.desc + '</p>' +
      '</div>';
    }).join("");
  }

  function populatePalettes() {
    const pGrid = document.getElementById("paletteGrid");
    if (!pGrid) return;
    pGrid.innerHTML = palettesCatalog.map(function (p) {
      const spans = p.colors.map(function (c) {
        return '<span style="background:' + c + '"></span>';
      }).join("");
      return '<div class="palette-card">' +
        '<div class="swatches">' + spans + '</div>' +
        '<div style="font-weight:700;font-size:0.88rem">' + p.name + '</div>' +
      '</div>';
    }).join("");
  }

  // Interactive HTML5 Canvas Web Simulator mirroring SoundKandee visuals
  function initSimulator() {
    const canvas = document.getElementById("simCanvas");
    if (!canvas) return;
    const ctx = canvas.getContext("2d");

    const presets = {
      mandala: { label: "Hyperspace Mandala • 8x Kaleidoscope", folds: 8, colors: ["#00F5D4", "#FF2A85", "#FFB703", "#7B2CBF"], warp: 0.45 },
      geiss: { label: "Geiss Warp Reactor • MilkDrop Warp + Solarize", folds: 6, colors: ["#9B5DE5", "#00F5D4", "#F15BB5", "#FEE440"], warp: 1.0 },
      pinwheel: { label: "MilkDrop Acid Pinwheel • 4-Way Mirror", folds: 10, colors: ["#2EC4B6", "#80FFDB", "#FF2A85", "#C77DFF"], warp: 0.85 },
      osc: { label: "Acid Oscilloscope • WaveFold & Chromatic Split", folds: 4, colors: ["#00F0FF", "#FF0055", "#FFB703", "#80ED99"], warp: 0.65 }
    };

    const state = {
      preset: "mandala",
      bpm: 126,
      lfoWave: "SINE",
      synthIdle: true,
      tapTimestamps: [],
      beatAccum: 0,
      lastTime: performance.now(),
      paletteOffset: 0
    };

    const bpmReadout = document.getElementById("simBpmReadout");
    const statusLabel = document.getElementById("simStatusLabel");
    const overlayTag = document.getElementById("simOverlayTag");
    const leds = [
      document.getElementById("led0"),
      document.getElementById("led1"),
      document.getElementById("led2"),
      document.getElementById("led3")
    ];

    function evalLfo(wave, phase) {
      const p = ((phase % 1) + 1) % 1;
      switch (wave) {
        case "TRI": return p < 0.5 ? p * 2 : 2 - p * 2;
        case "SAW": return p;
        case "SQR": return p < 0.5 ? 1 : 0;
        case "SH": return ((Math.sin(Math.floor(phase * 4) * 12.9898) * 43758.5453) % 1 + 1) % 1;
        default: return 0.5 + 0.5 * Math.sin(p * Math.PI * 2);
      }
    }

    function updateUi() {
      if (bpmReadout) bpmReadout.textContent = Math.round(state.bpm) + " BPM";
      if (statusLabel) {
        statusLabel.textContent = state.synthIdle
          ? "Synth Idle • Techno Pulse • " + state.lfoWave + " LFO"
          : "Live Audio Reactive Sim • " + state.lfoWave + " LFO";
      }
      if (overlayTag) {
        overlayTag.textContent = presets[state.preset].label + " • " + Math.round(state.bpm) + " BPM";
      }
    }

    document.querySelectorAll("[data-preset]").forEach(function (btn) {
      btn.addEventListener("click", function () {
        document.querySelectorAll("[data-preset]").forEach(function (b) { b.classList.remove("active"); });
        btn.classList.add("active");
        state.preset = btn.getAttribute("data-preset");
        updateUi();
      });
    });

    document.querySelectorAll("[data-lfo]").forEach(function (btn) {
      btn.addEventListener("click", function () {
        document.querySelectorAll("[data-lfo]").forEach(function (b) { b.classList.remove("active"); });
        btn.classList.add("active");
        state.lfoWave = btn.getAttribute("data-lfo");
        updateUi();
      });
    });

    const tapBtn = document.getElementById("simTapTempoBtn");
    if (tapBtn) {
      tapBtn.addEventListener("click", function () {
        const now = performance.now();
        const arr = state.tapTimestamps;
        if (arr.length && now - arr[arr.length - 1] > 2000) arr.length = 0;
        arr.push(now);
        if (arr.length > 6) arr.shift();
        if (arr.length >= 2) {
          let total = 0;
          for (let i = 1; i < arr.length; i++) total += arr[i] - arr[i - 1];
          const avg = total / (arr.length - 1);
          state.bpm = Math.max(60, Math.min(180, 60000 / avg));
        }
        state.beatAccum = Math.floor(state.beatAccum) + 1;
        updateUi();
      });
    }

    const synthBtn = document.getElementById("simSynthToggle");
    if (synthBtn) {
      synthBtn.addEventListener("click", function () {
        state.synthIdle = !state.synthIdle;
        synthBtn.classList.toggle("active", state.synthIdle);
        synthBtn.textContent = state.synthIdle ? "Synth Idle: ON" : "Synth Idle: OFF";
        updateUi();
      });
    }

    const shakeBtn = document.getElementById("simDeviceTapBtn");
    if (shakeBtn) {
      shakeBtn.addEventListener("click", function () {
        const keys = Object.keys(presets);
        state.preset = keys[Math.floor(Math.random() * keys.length)];
        const waves = ["SINE", "TRI", "SAW", "SQR", "SH"];
        state.lfoWave = waves[Math.floor(Math.random() * waves.length)];
        state.paletteOffset += 1;
        document.querySelectorAll("[data-preset]").forEach(function (b) {
          b.classList.toggle("active", b.getAttribute("data-preset") === state.preset);
        });
        document.querySelectorAll("[data-lfo]").forEach(function (b) {
          b.classList.toggle("active", b.getAttribute("data-lfo") === state.lfoWave);
        });
        updateUi();
      });
    }

    function resize() {
      const rect = canvas.getBoundingClientRect();
      const dpr = Math.min(window.devicePixelRatio || 1, 2);
      canvas.width = Math.max(300, Math.floor(rect.width * dpr));
      canvas.height = Math.max(180, Math.floor(rect.height * dpr));
    }

    window.addEventListener("resize", resize);
    resize();
    updateUi();

    function frame(now) {
      const dt = Math.min(0.05, (now - state.lastTime) / 1000);
      state.lastTime = now;
      state.beatAccum += (state.bpm / 60) * dt;

      const beatFrac = state.beatAccum % 1;
      const beatInBar = Math.floor(state.beatAccum) % 4;
      const beatPulse = Math.exp(-beatFrac * 5.2) * (state.synthIdle ? 1.0 : 0.35);
      const lfoVal = evalLfo(state.lfoWave, state.beatAccum * 0.5);

      leds.forEach(function (el, idx) {
        if (!el) return;
        el.classList.toggle("active", idx === beatInBar);
      });

      const w = canvas.width;
      const h = canvas.height;
      const cx = w * 0.5;
      const cy = h * 0.5;
      const radius = Math.min(w, h) * 0.36;
      const cfg = presets[state.preset];

      // Feedback persistence veil
      ctx.fillStyle = "rgba(5, 4, 13, 0.22)";
      ctx.fillRect(0, 0, w, h);

      ctx.save();
      ctx.translate(cx, cy);
      ctx.globalCompositeOperation = "lighter";

      const folds = cfg.folds;
      const spin = state.beatAccum * 0.45 + lfoVal * 1.4;

      for (let f = 0; f < folds; f++) {
        ctx.save();
        ctx.rotate((f / folds) * Math.PI * 2 + spin * (f % 2 === 0 ? 1 : -0.35));

        const col = cfg.colors[(f + state.paletteOffset) % cfg.colors.length];
        ctx.strokeStyle = col;
        ctx.lineWidth = 1.8 + beatPulse * 2.4;
        ctx.beginPath();

        const points = 42;
        for (let i = 0; i <= points; i++) {
          const u = i / points;
          const ang = u * Math.PI * 1.65 + lfoVal * 2.1;
          const mod = Math.sin(u * 10 + state.beatAccum * 3.1) * cfg.warp * (0.35 + beatPulse);
          const r = radius * (0.18 + u * (0.72 + beatPulse * 0.28 + mod * 0.22));
          const x = Math.cos(ang + mod * 0.5) * r;
          const y = Math.sin(ang * (state.preset === "osc" ? 2.0 : 1.0) - mod * 0.4) * r * (state.preset === "pinwheel" ? 0.85 : 1.0);
          if (i === 0) ctx.moveTo(x, y);
          else ctx.lineTo(x, y);
        }
        ctx.stroke();

        // Harmonic node dot
        const tipR = radius * (0.55 + beatPulse * 0.32 + lfoVal * 0.18);
        ctx.fillStyle = col;
        ctx.beginPath();
        ctx.arc(Math.cos(spin) * tipR, Math.sin(spin) * tipR, 2.5 + beatPulse * 3, 0, Math.PI * 2);
        ctx.fill();

        ctx.restore();
      }

      // Beat shockwave ring
      ctx.strokeStyle = cfg.colors[0];
      ctx.globalAlpha = Math.max(0.08, (1 - beatFrac) * (state.synthIdle ? 0.55 : 0.2));
      ctx.lineWidth = 2 + beatPulse * 3;
      ctx.beginPath();
      ctx.arc(0, 0, radius * (0.25 + beatFrac * 0.95), 0, Math.PI * 2);
      ctx.stroke();

      ctx.restore();
      ctx.globalAlpha = 1;

      requestAnimationFrame(frame);
    }

    requestAnimationFrame(frame);
  }

  document.addEventListener("DOMContentLoaded", function () {
    populateCatalog("all");
    populatePalettes();
    initSimulator();

    document.querySelectorAll("[data-filter]").forEach(function (btn) {
      btn.addEventListener("click", function () {
        document.querySelectorAll("[data-filter]").forEach(function (b) { b.classList.remove("active"); });
        btn.classList.add("active");
        populateCatalog(btn.getAttribute("data-filter"));
      });
    });
  });
})();
