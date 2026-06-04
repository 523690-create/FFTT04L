# FFTT04M — Redesign & Feature Notes

Running log of the redesign + new-feature work. All changes land on `main`, **one commit per change**, so non-optimal changes can be reverted selectively with `git revert <hash>` (find the hash via `git log --oneline`).

---

## Conventions
- Each new visualization option is an **isolated Enhance mode** wrapped in try/catch — if it fails it is just an inert menu entry and the rest of the app is unaffected.
- Engine modes (build the base spectrogram): Sweep, Sweep+, Constant-Q/Morlet, Reassignment, Synchrosqueeze. If two engines are selected, the highest-priority one wins. Post-processors (denoise, Multitaper) stack on top.
- Playback always uses raw PCM. EQ biquads and noise-filter spectral subtraction are **visualization aids only**: applying them to audio output distorts the signal (EQ boosts clip past full-scale; spectral-subtraction reconstruction overwrites overlapping windowed blocks with no COLA normalization).
- APK naming convention: `FFTT04M-[date-time].apk` per build.
- strings.xml: `%%` is the XML escape for a literal `%` character — it renders as `%` on device, not as `%%`.

---

## Known UI Issues (screenshots 2026-06-04)

| # | Screen | Observed | Root cause | Status |
|---|--------|----------|------------|--------|
| 1 | EQ tab | Band freq labels clip to `10H` / `30H` | Each band column is `layout_weight="10"` in a parent with `weightSum="54"`; at ~360 dp screen width the column is ~40 dp, too narrow for `wrap_content` label `"100\nHz"` at 7 sp | TODO: drop the `\nHz` suffix so labels read `100`, `300`, `1k`, `3k`, `8k` |
| 2 | Filter tab | Label reads `Filter\n%%` in XML | `%%` is correct XML escape for `%`; renders as `Filter %` on device — not a bug | N/A |
| 3 | Display tab | Sz/St changes live while spinner moves (2048/1024 → 512/256) | `sizeSpinner.onItemSelectedListener` calls `triggerRefresh()` immediately — intentional live preview | By design |

---

## Changelog (newest first)

### Sweep+ (de-striped multi-resolution) — Enhance mode
- **What:** New Enhance engine “Sweep+” (mode index 5). Computes each window size (512/1024/2048/4096) on the common base time grid, normalizes each to its peak, and merges across sizes by per-pixel **max** — eliminating the additive banding/stripes of the original Sweep. Selected via the Enhance dialog; isolated in the refresh dispatch.
- **Files:** `ViewerActivity.kt`
- **Revert:** `git revert 3a2d56d` (Add Sweep+ de-striped multi-resolution Enhance mode)

### Play cursor (playhead synced to audio)
- **What:** `FFTHeatMapView` draws a vertical playhead at the current playback position (transformed by pan/zoom like the time-grid). `ViewerActivity.playAudio` polls `AudioTrack.playbackHeadPosition` (~30 ms) and updates the cursor; clears it when playback ends or is stopped.
- **Files:** `FFTHeatMapView.kt`, `ViewerActivity.kt`
- **Revert:** `git revert 2de0bb9` (Add Play cursor)

### Fix distorted playback — play raw PCM, ignore Filter settings
- **What:** Playback was applying EQ biquad and spectral-subtraction transforms to audio output, causing clipping and discontinuities. Playback now uses raw decoded PCM only. See Conventions above for rationale.
- **Files:** `ViewerActivity.kt`
- **Revert:** `git revert bd65e7f` (Fix distorted playback)

### Replace Wavelet COLOR slider with shared color spinner
- **What:** WaveletActivity COLOR control replaced with the shared spinner (Default / Viridis / Magma / Gray) used by ViewerActivity, so color scheme is consistent and persisted via SharedPreferences.
- **Files:** `WaveletActivity.kt`, `WaveletView.kt`, `activity_wavelet.xml`
- **Revert:** `git revert bcaec2a`

### Fix blur 250 ms vertical lines to track pan/zoom
- **What:** The 250 ms time-grid lines were drawn in raw pixel space and did not move when the user panned or zoomed. Fixed to transform with the same pan/zoom matrix as the spectrogram.
- **Files:** `FFTHeatMapView.kt`
- **Revert:** `git revert 71c5760`

### Borrow FFTT02M blur time-grid — vertical lines every 250 ms
- **What:** `FFTHeatMapView` draws faint vertical grid lines at every 250 ms boundary, matching the FFTT02M visual style.
- **Files:** `FFTHeatMapView.kt`
- **Revert:** `git revert 7761fa7`

### Mixed-mode Enhance control
- **What:** ENHANCE button opens a multi-choice dialog (checkboxes) listing all engines and post-processors: Sweep, Gaussian, Bilateral, TV Denoise, Butterworth, Sweep+. Any combination can be active simultaneously; the button shows the active count as `ENH:N` or `ENHANCE` when none selected. Persisted via `enhance_mask` bitmask in SharedPreferences.
- **Files:** `ViewerActivity.kt`
- **Revert:** `git revert c3a8f3e`

### Borrow FFTT02M COLOR-control appearance across screens
- **What:** COLOR spinner uses the selected scheme’s own colors for its background and text — caption replaced with “COLOR”, drawn in the scheme’s high-value color over its low-value background color. Applied to ViewerActivity and WaveletActivity.
- **Files:** `MainActivity.kt`, `ViewerActivity.kt`
- **Revert:** `git revert 67f4942`

### WAV fallback when FLAC encoding is unavailable
- **What:** Recording now falls back to WAV (PCM 16-bit) if the device’s MediaCodec does not expose a FLAC encoder.
- **Files:** `MainActivity.kt`
- **Revert:** `git revert fdf7e97`
