# FFTT04M — Redesign & Feature Notes

Running log of the redesign + new-feature work. All changes land on `main`, **one
commit per change**, so non-optimal changes can be reverted selectively with
`git revert <hash>` (find the hash via `git log --oneline`).

## Conventions
- Each new visualization option is an **isolated Enhance mode** wrapped in try/catch —
  if it fails it's just an inert menu entry and the rest of the app is unaffected.
- Engine modes (build the base spectrogram): Sweep, Sweep+, Constant-Q/Morlet, Reassignment, Synchrosqueeze.
  If two engines are selected, the highest-priority one wins. Post-processors (denoise, Multitaper) stack on top.

## Changelog (newest first)

### Play cursor (playhead synced to audio)
- What: `FFTHeatMapView` draws a vertical playhead at the current playback position
  (transformed by pan/zoom like the time-grid). `ViewerActivity.playAudio` polls
  `AudioTrack.playbackHeadPosition` (~30 ms) and updates the cursor; clears it when
  playback ends or is stopped.
- Files: `FFTHeatMapView.kt`, `ViewerActivity.kt`
- Revert: `git revert` the commit titled "Add Play cursor (playhead synced to audio)".
