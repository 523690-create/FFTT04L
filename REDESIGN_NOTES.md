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

## Changelog (newest first)

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