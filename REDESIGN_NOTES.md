# FFTT04M — Redesign & Feature Notes

Running log of the redesign + new-feature work. All changes land on `main`, **one
commit per change**, so non-optimal changes can be reverted selectively with
`git revert <hash>` (find the hash via `git log --oneline`).

## Conventions
- Each new visualization option is an **isolated Enhance mode** wrapped in try/catch —
  if it fails it's just an inert menu entry and the rest of the app is unaffected.
- Engine modes (build the base spectrogram): Sweep+, Constant-Q/Morlet, Reassignment, Synchrosqueeze.
  If two engines are selected, the highest-priority one wins (priority: Synchrosqueeze > Reassignment
  > Constant-Q > Sweep+). Post-processors (Gaussian, Bilateral, TV Denoise, Butterworth, Multitaper)
  stack on top. The original additive "Sweep" engine has been removed (see changelog).
- **Build Convention**: APKs are generated with a timestamp in the project root: `FFTT04M-yyyyMMdd-HHmmss.apk`.
  Use the PowerShell command documented in `agents.md` to build and rename.

## Changelog (newest first)

### UI Perfection & Final Multi-Device Stability
- What: Enabled full 360-degree rotation (`fullSensor`). Added a **CLEAR** button to the Enhance dialog to reset all settings. Reversed colors for all **COLOR** controls (accent bg, base text). Fixed vertical alignment and text wrapping for landscape buttons. Standardized all spinner heights to 44dp. Hardened Nexus 7 recording with a **16kHz** fallback. Optimized text auto-sizing with caching to resolve tablet UI freezes. Corrected Sz/St label logic to keep the larger, clearer version.
- Files: `AndroidManifest.xml`, `ViewerActivity.kt`, `MainActivity.kt`, `WaveletActivity.kt`, `ViewUtils.kt`, `FFTHeatMapView.kt`, `activity_viewer.xml`, `activity_wavelet.xml`, `layout-land/activity_viewer.xml`
- Revert: `git revert` this series of commits.

### Dynamic Height Allocation in ViewerActivity (Portrait)
- What: Restructured `activity_viewer.xml` with a vertical `LinearLayout` root to support dynamic
  proportional heights on all screen sizes. The FFT display is pinned as a square (1:1), and the
  remaining vertical "allowance" is divided into units of 0.11 for each: Top Bar, Tab Bar, and the
  5 Display tab controls (Size, Step, Enhance, Blur, Color). A 0.23 spacer sits at the bottom.
  Top bar buttons correctly redistributed to 20% width each.
- Files: `activity_viewer.xml`
- Revert: `git revert` this commit.

### ViewerActivity UI: Top bar redistribution and Display tab refinement
- What: Redistributed top control bar buttons to 0.2 width each (layout_weight="20") with optimized
  8sp bold fonts. In the Display tab, moved the Color spinner immediately below Blur, made all
  control heights uniform (44dp), and synchronized Color/Blur font sizes (10sp bold) with the
  Enhance control. Cleaned up redundant color spinner from the top bar. Addresses UI review items 1.1, 1.2, 2.3, 2.4.
- Files: `activity_viewer.xml`, `spinner_item_gold.xml`, `spinner_item_blur_cyan.xml`
- Revert: `git revert` this commit.

### Hide Enhance spinner and relocate Blur spinner (UI refinement)
- What: To reduce clutter in the DISPLAY tab, the orange `vEnhanceSpinner` is now hidden and disabled 
  (its logic remains for future use/reversion). The `vBlurSpinner` moved up into the freed position 
  at the top of the lower control group, and the redundant `vColorSpinner` entry was cleaned up so 
  it now sits directly below Blur. Addresses UI review items 2.1, 2.2, and 2.3.
- Files: `ViewerActivity.kt`, `activity_viewer.xml`
- Revert: `git revert` this commit; restore visibility in `ViewerActivity.kt`.

### Tabbed control panels (Viewer + Wavelet, portrait + landscape)
- What: All controls are reachable via a Material `TabLayout` that shows one control group at a time
  (lowest-risk approach: reuse the existing control views in place, toggle page visibility — no
  ViewPager/fragments). Viewer tabs: **EQ / FILTER / DISPLAY**. Wavelet tabs: **SETUP / SLIDERS**.
  Selected tab is persisted per recording; slider value labels are repositioned when their page is
  shown. Landscape was rebuilt from the old stripped-down stubs into a tabbed left sidebar beside the
  square map, so landscape now exposes the full control set too; ViewerActivity wires colour/filter/
  blur/enhance in both orientations (the `!isLandscape` gate is gone). `R.id.sliderColor` declared in
  `ids.xml` to survive removal of the old landscape stub.
- Files: `ViewerActivity.kt`, `WaveletActivity.kt`, all four `activity_viewer/activity_wavelet`
  layouts (portrait + `layout-land`), `res/values/ids.xml`
- Revert: `git revert` the four "tabbed" commits (portrait viewer, portrait wavelet, landscape).

### Responsive fonts (uniform text autosizing)
- What: `applyAutoSizeText` (ViewUtils.kt) walks each screen's view tree and enables uniform text
  autosizing on every TextView/Button so captions scale to fit across device sizes/orientations. The
  hand-positioned slider value labels (inside slider FrameLayouts, sized at runtime) and TabLayout
  subtrees are skipped. Wired into Main/Viewer/Wavelet onCreate and the Gallery item holder.
- Files: `ViewUtils.kt`, `ViewerActivity.kt`, `WaveletActivity.kt`, `MainActivity.kt`, `GalleryActivity.kt`
- Revert: `git revert` the commit "Responsive fonts: uniform text autosizing across all screens".

### Listen screen: persist mic device globally
- What: The Listen screen already persisted colour/EQ/noise globally; the selected microphone now
  persists too (saved by display name, restored before the spinner listener attaches to avoid a
  spurious recording restart).
- Files: `MainActivity.kt`
- Revert: `git revert` the commit "Listen screen: persist mic device selection globally".

### Persist FFT pan/zoom per recording; WAVE -> WAVELET caption
- What: `FFTHeatMapView` exposes get/setViewState + an onViewStateChanged callback (fires when a
  pan/zoom gesture ends). ViewerActivity restores the saved pan/zoom from the per-recording prefs on
  load and writes it back on change. Also renamed the `btn_wave` caption WAVE -> WAVELET.
- Files: `ViewerActivity.kt`, `FFTHeatMapView.kt`, `res/values/strings.xml`
- Revert: `git revert` the commit "Persist FFT pan/zoom per recording; rename WAVE button to WAVELET".

### True Multitaper engine (replaces the post-processor approximation)
- What: The old Multitaper post-processor could only smooth a finished map; replaced with a real
  PCM engine (`runMultitaperInternal`) that averages K=5 orthogonal sine-tapered (Riedel-Sidorenko)
  periodograms per column, cutting spectral-estimate variance at the window's true resolution.
  Multitaper is now the 5th engine (radio, index 4); post-processors shift to 5..8; `engineCount=5`;
  enhance pref key bumped to `enhance_mask_v3`.
- Files: `ViewerActivity.kt`
- Revert: `git revert` the commit "Replace Multitaper post-processor with a true PCM multitaper engine".

### FFT Enhance dialog: engines as radio buttons, post-processors as checkboxes
- (see commit) Custom dialog with a RadioGroup for the mutually-exclusive engines (+ "None") and
  checkboxes for the stacking post-processors. Files: `ViewerActivity.kt`.

### Persist analysis & display settings per recording
- What: ViewerActivity and WaveletActivity now key their `SharedPreferences` to the recording
  filename (`rec_<sanitised-name>`) instead of the shared `app_settings`, so each recording
  remembers its own FFT size/step, colour, blur, enhance selection, EQ, noise filter, and wavelet
  family/level/mode/threshold. Both screens share one namespace per recording, so overlapping keys
  like `color_scheme` stay consistent between the FFT and Wavelet views. `filePath` is now read
  before prefs init in both activities; global UI prefs (gallery grid mode) remain in `app_settings`.
  New recordings open at code defaults (true per-recording isolation rather than inheriting last-used).
- Files: `ViewerActivity.kt`, `WaveletActivity.kt`
- Revert: `git revert` the commit "Persist analysis & display settings per recording".

### Per-recording comments with thumbnail refresh on save
- What: A **NOTE** button on the FFT analysis screen (replaces the decorative title in portrait,
  added to the sidebar in landscape) opens a comment editor backed by a `<name>.txt` sidecar next to
  the recording. Saving writes the comment and refreshes the gallery thumbnail via the new
  `FFTHeatMapView.saveSnapshot()`, capturing the heat-map exactly as displayed. The Gallery shows the
  comment in italic beneath the filename (grid + list), reloads on resume so edits appear, and the
  delete flow also removes the `.txt` sidecar.
- Files: `ViewerActivity.kt`, `GalleryActivity.kt`, `FFTHeatMapView.kt`, `gallery_item.xml`,
  `res/layout/activity_viewer.xml`, `res/layout-land/activity_viewer.xml`
- Revert: `git revert` the commit "Add per-recording comments with thumbnail refresh on save".

### FFT Viewer: busy spinner for procedures over 100 ms
- What: Indeterminate `ProgressBar` overlaid on the heat-map (both orientations), shown only when a
  decode or recompute runs longer than 100 ms so quick refreshes don't flash it. Reference-counted
  `beginBusy()`/`endBusy()` wrap the decode and refresh worker threads (paired in finally blocks);
  the 100 ms delayed show is cancelled if the work finishes first.
- Files: `ViewerActivity.kt`, `res/layout/activity_viewer.xml`, `res/layout-land/activity_viewer.xml`
- Revert: `git revert` the commit "FFT Viewer: busy spinner for procedures over 100 ms".

### FFT Enhance dialog: engines as radio buttons, post-processors as checkboxes
- What: The four engines (Sweep+/Constant-Q/Reassignment/Synchrosqueeze) are mutually exclusive but
  were multi-select checkboxes, letting users tick combinations the dispatch silently resolved by
  priority. Replaced `setMultiChoiceItems` with a custom view: a `RadioGroup` for the engines (plus
  an explicit "None = plain STFT") and checkboxes for the stacking post-processors. A stale mask with
  multiple engine bits is sanitised on load.
- Files: `ViewerActivity.kt`
- Revert: `git revert` the commit "FFT Enhance dialog: engines as radio buttons, post-processors as checkboxes".

### Wavelet UI redesign: exclusive MODE selector, safety status line, log threshold
- What: MODE spinner (DWT/WPT/CWT/RECON) replaces the three independent WPT/CWT/RECON checkboxes;
  `isWPT`/`isCWT`/`isReconstruct` derived from `analysisMode` via `syncModeFlags()`; pref migrated
  to `analysis_mode`; failsafe last-resort step is now just "drop to DWT". Added a safety status line
  (`txtSafetyStatus`) showing mode + the safe-FS ceiling. Threshold is a 10-position log slider
  (0 = OFF). STOP button relabelled; SOFT/LOG/L-NORM grouped under THRESH:/VIEW: captions. Portrait
  and `layout-land` stubs kept in sync.
- Files: `WaveletActivity.kt`, `res/layout/activity_wavelet.xml`, `res/layout-land/activity_wavelet.xml`
- Revert: `git revert` the commit "Wavelet UI redesign: exclusive mode selector + safety status + log threshold".

### Wavelet: graduated failsafe (back off sliders before unchecking boxes)
- What: Replaced the all-at-once `resetToSafeSettings` with a graduated back-off. On a predicted
  over-budget or a runtime crash, `degradeForSafety` eases one lever per failure: (1) trim the
  Sampling-frequency slider toward the largest value that fits (`safeFreqCeiling`, or −25% on an
  unpredicted crash), (2) then lower the Decomposition-level slider, (3) and only as a last resort
  drop the engine to plain DWT. Each failure re-enters `applyFailsafe`, so it escalates monotonically
  and terminates. Decode errors no longer trigger the back-off (nothing to retry). Budget limits are
  named constants.
- Files: `WaveletActivity.kt`
- Revert: `git revert` the commit "Wavelet: graduated failsafe instead of all-at-once safe reset".

### Constant-Q/Morlet, Reassignment & Synchrosqueeze engines + Multitaper; drop simple Sweep
- What: Completed the planned engine set. The Enhance dialog now exposes four mutually-exclusive
  engines — **Sweep+** (idx 0), **Constant-Q** (idx 1), **Reassignment** (idx 2), **Synchrosqueeze**
  (idx 3) — and five stacking post-processors — Gaussian/Bilateral/TV Denoise/Butterworth (idx 4–7)
  plus the new **Multitaper** (idx 8). The original additive **Sweep** engine (and its
  `runFftSweepInternal`) was deleted; its index-0 menu slot is now Sweep+.
  - Constant-Q (`runConstantQInternal`): per-row complex Morlet wavelet (fixed cycle count → fixed Q),
    convolved on the base time grid; analysis freqs are log-spaced so they land on the view axis directly.
  - Reassignment (`runReassignmentInternal`): Auger-Flandrin time+freq reassignment of a Hann STFT
    using time-ramped and derivative windows; energy splatted onto the base grid.
  - Synchrosqueeze (`runSynchrosqueezeInternal`): frequency-only reassignment (time column preserved)
    via the derivative-window instantaneous-frequency estimate.
  - Multitaper (`enhMultitaper`): variance-reducing binomial average along the frequency axis. Note:
    it's a post-processor on the finished map, so it *emulates* multitaper variance reduction rather
    than re-tapering raw PCM (documented in-code).
  - Dispatch: engine priority Synchrosqueeze > Reassignment > Constant-Q > Sweep+, else plain STFT.
    Each engine is isolated — the dispatch `try/catch` contains any failure. Shared tail factored into
    `renderEngineMap`/`filterPcm`/`freqToRow`. The `enhance_mask` pref key was bumped to
    `enhance_mask_v2` because the mode order changed.
- Files: `ViewerActivity.kt`
- Revert: `git revert` the commit titled "Add Constant-Q/Reassignment/Synchrosqueeze engines + Multitaper; drop Sweep".

### Sweep+ (de-striped multi-resolution) — Enhance mode
- What: New Enhance engine "Sweep+" (mode index 5). Computes each window size
  (512/1024/2048/4096) on the common base time grid, normalizes each to its peak, and
  merges across sizes by per-pixel **max** — eliminating the additive banding/stripes of
  the original Sweep. Selected via the Enhance dialog; isolated in the refresh dispatch.
- Files: `ViewerActivity.kt`
- Revert: `git revert` the commit titled "Add Sweep+ de-striped multi-resolution Enhance mode".

### Play cursor (playhead synced to audio)
- What: `FFTHeatMapView` draws a vertical playhead at the current playback position
  (transformed by pan/zoom like the time-grid). `ViewerActivity.playAudio` polls
  `AudioTrack.playbackHeadPosition` (~30 ms) and updates the cursor; clears it when
  playback ends or is stopped.
- Files: `FFTHeatMapView.kt`, `ViewerActivity.kt`
- Revert: `git revert` the commit titled "Add Play cursor (playhead synced to audio)".

### wish list:
- add a Comment button in FFT analysis screen: the comment will appear immediately beneath the filename in the Gallery view. Any time Comment is changed and saved, replace the recording icon with the current FFT Analysis rendering as displayed.
- Additionally, add progress indicator for all procedures taking more than 100 msec, and persist all analysis and display settings per recording.
- make mutually exclusive enhance settings into radio buttons ratherthan checkboxes
- 