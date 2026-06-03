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
- add a Comment button in analysis screens: the comment will appear immediately beneath the filename in the Gallery view. Any time Comment is changed and saved, replace the recording icon with the current 
- Additionally, add progress indicator for all procedures taking more than 100 msec, and persist all analysis and display settings per recording.
- make mutually exclusive enhance settings into radio buttons ratherthan checkboxes
- 