# HANDOFF — FFTT04M (for Claude Code)

Read this file first, then continue the work. This captures context that is NOT in the
codebase or the other .md files — it lives only in the chat session that produced it.
Date: 2026-06-05.

## COMPLETED IN THIS SESSION (2026-06-05) — verified on Nexus 7 (API 23) landscape
- ✅ **FLAC removed, WAV-only** (`a31505e`): 16-bit PCM WAV everywhere; dropped the invalid FLAC container.
- ✅ **REAL root cause of "empty Gallery" found & fixed** (`4fab9d7`): GalleryActivity only
  listed `.flac`, so WAV recordings never showed. The "save regression" was a red herring —
  saves were succeeding, just invisible. Fixes:
  - GalleryActivity now lists `.wav` (+ `.flac` legacy).
  - New `WavReader.kt` parses raw PCM WAV (no MediaCodec decoder exists for audio/raw).
  - ViewerActivity.loadAndDecode + WaveletActivity.loadAndDecode branch on extension:
    WAV → WavReader, FLAC → MediaExtractor/MediaCodec. PLAY uses decoded PCM, unchanged.
- ✅ **Band-label corruption ACTUALLY fixed** (`4fab9d7`): real culprit was
  `updateAllLabelPositions()` calling `setMaxTextSizeToFit()` on the header labels (blew them
  up from stale parent dims → clipped to one glyph). Removed header labels from that fit list;
  they keep fixed XML textSize. (Earlier `301082a` autosize-skip alone was NOT sufficient.)
- ✅ **UI_CHANGELIST.md**: already fully reconciled.

### Verified end-to-end on Nexus 7
Gallery lists the WAV → tap opens Viewer → spectrogram renders → EQ ("100/300/1k/3k/8k Hz"),
FILTER ("Filter %/Rise Time/Fall Time"), and DISPLAY labels all render correctly.

### Superseded notes (kept for history)
- `28dd073` (WAV fallback in legacy save path) and `301082a` (autosize skip) were partial; the
  `a31505e` + `4fab9d7` changes are the complete fix. The legacy save fallback in `28dd073` was
  later replaced by WAV-only in `a31505e`.

Next (optional polish): push a matching `.png` so gallery thumbnails aren't placeholders for
adb-pushed test files (real recordings already write the PNG); minor top-bar truncation tuning.

## How to work on this project (conventions)
See `agents.md` for full rules. Key points: don't apologize; terse technical tone; apply
only the most-recently-requested change; after each change do a Gradle sync + build and do
NOT present work as done if the build fails; keep Activities small/modular; name each built
APK `FFTT04M-[date-time].apk` in the project root. In `strings.xml`, `%%` is a literal
`%` escape (not a bug). Playback uses raw PCM only.

## Build / install environment
- Local repo: H:\FFTT04M (Windows, PowerShell). User runs git, gradlew, adb here.
- Android Studio was updated to "Quail 1"; targetSdk 36; package com.example.FFTT04M.
- Test devices: Pixel 10 (modern, API 36) and Nexus 7 (API 23, Android 6).

### CRITICAL install gotcha
The Quail Studio update changed the signing key. Installing a new APK over the old one
fails with: `INSTALL_FAILED_UPDATE_INCOMPATIBLE: signatures do not match`. The install is
silently rejected and the device keeps running OLD code. ALWAYS uninstall first:
`adb uninstall com.example.FFTT04M` on BOTH devices before installing a fresh build.
Many "bugs" this session were actually stale APKs from this issue.

## Commits made via GitHub web editor this session (already on origin/main)
- `a9e39dc` Wavelet landscape crash fix — in layout-land/activity_wavelet.xml the
  `btnWaveletStop` stub was `<View>`; code does findViewById with an inferred Button cast
  that runs before the `?.` null-check, so landscape threw
  `ClassCastException: View cannot be cast to Button` at WaveletActivity.kt:586 (from
  onCreate:129). Fixed by changing the stub to a zero-size `<Button ... visibility="gone">`.
  NOTE: the landscape wavelet layout actually HAS all 23 control IDs (an earlier
  "missing controls" diagnosis was WRONG). Only the stub type was the defect.
- `a3429af` Legacy audio path (API < 26) — in MainActivity.kt `saveFragmentAudio()` added a
  guard at the top: if SDK_INT < O, call encodeWav() directly and return the .wav (return
  null on exception), skipping the FLAC attempt. >>> SEE REGRESSION BELOW — THIS IS SUSPECT. <<<
- `2291122` Hide Wavelet below API 26 — in ViewerActivity.kt the WAVELET button
  (R.id.btnViewerWavelet) is set to View.GONE when SDK_INT < O, else wired to launch
  WaveletActivity. Rationale: old devices can't run wavelet analysis fast enough.

API cutoff chosen for legacy behavior = 26 (Android 8.0 / VERSION_CODES.O). Nexus 7 (API 23)
is below the cutoff.

## OPEN REGRESSION — fix this first
After `a3429af`, the Nexus 7 now saves NOTHING — the Gallery is empty after a Save (it used
to save a file, just silent/garbled). Latest Nexus 7 logcat shows:
  `Started recording: rate=44100, enc=4, src=1`  (repeated, no crash, no save log line)
Interpretation:
- enc=4 = ENCODING_PCM_FLOAT, rate=44100. So the Nexus 7 does NOT fall back to 16kHz/16-bit
  at all — it opens a 44.1k FLOAT stream fine. The earlier "needs 16kHz fallback" premise was
  WRONG. Capture works.
- No exception is logged, but no file appears. The new legacy branch returns null silently on
  any encodeWav() failure, so the user gets nothing instead of the previous (present-but-bad)
  FLAC file.
Likely cause: the `a3429af` legacy WAV branch in saveFragmentAudio() either throws inside
encodeWav() on this device/buffer and returns null, or writes a file the Gallery's scan
doesn't pick up. Recommended action: in Claude Code, REVERT or repair `a3429af`:
  - Quick: revert a3429af to restore the FLAC->WAV fallback (at least files reappear).
  - Better: keep WAV-first but (a) don't swallow the failure — log e and still attempt the
    other path; (b) verify the saved file is registered the same way the FLAC path was so it
    shows in the Gallery; (c) confirm encodeWav() works on API 23 with the captured FloatArray.
  Then build, uninstall, install on Nexus 7, Save a clip, confirm it appears in Gallery and is
  audible. Capture logcat (tag FFTT04M) if it still fails.

## Audio architecture (so you don't re-derive it)
- Live mic -> 3-second circular buffer (audioCircularBuffer, FloatArray, size = sampleRate*3),
  filled in the recording thread read loop in MainActivity.kt (~lines 750-780). Both FLOAT and
  16-bit read branches exist and are correct (16-bit path divides by 32768f).
- A "fragment" = the user crops a region of the live spectrogram; saveFragment() (~241) maps
  the crop rect to indices in the circular buffer, copies them into a FloatArray, and calls
  saveFragmentAudio(dir, timestamp, data) (~278).
- saveFragmentAudio: tries encodeFlac() (~298) then falls back to encodeWav() (~357).
  encodeWav() is a correct 44-byte-header 16-bit PCM WAV writer and is reliable everywhere.
- KNOWN DEFECT (not yet fixed): encodeFlac() writes RAW MediaCodec FLAC output bytes straight
  to a .flac file with NO MediaMuxer and NO BUFFER_FLAG_CODEC_CONFIG handling — that is not a
  valid FLAC container. Lenient decoders (Pixel) tolerate it; stricter paths produce silence.
  Proper fix (do in Code where you can build/test): either wrap output in a MediaMuxer, or
  just make WAV the default for ALL devices and drop the broken FLAC writer. Keep encodeFlac in
  history for reference / revert.

## Enhance dialog model (CORRECT — not a bug)
The FFT "Enhance" dialog is intentionally: radio buttons to pick ONE engine
(None / Sweep+ / Constant-Q / Reassignment / Synchrosqueeze / Multitaper) PLUS checkboxes to
stack post-processors (Gaussian / Bilateral / TV Denoise / Butterworth). The old additive
"Sweep" was removed on purpose. Do not "fix" this into a single multi-select.

## OPEN UI ITEMS (from latest landscape + portrait screenshots)
1. BAND-LABEL CORRUPTION (highest-value cosmetic bug, both orientations):
   - EQ tab frequency labels render as "Hz Hz" then "Z Z Z" instead of "100 Hz / 300 Hz /
     1k Hz / 3k Hz / 8k Hz". Filter tab shows "%% e e" instead of "Filter % / Rise Time /
     Fall Time". The labels are being shrunk/wrapped to almost nothing.
   - Prime suspect: `applyAutoSizeText` in ViewUtils.kt over-shrinking these narrow-column
     TextViews until the text wraps to one glyph. REDESIGN_NOTES says slider VALUE labels and
     TabLayout subtrees are already in an auto-size skip list; the EQ/Filter COLUMN HEADER
     labels are apparently NOT skipped. Fix = add those header TextViews (the 100Hz/300Hz/...
     and Filter%/Rise/Fall labels) to the auto-size skip list, OR give them a fixed textSize
     and disable autosize for them. Verify in both portrait and landscape.
2. Top-bar buttons truncate to "GA... LIS... WA... NO... PL..." in landscape (and "GALL/LIST/
   WAV/NOTE/PLAY" in portrait). Acceptable but could be tuned; low priority. Landscape Wavelet
   top bar shows "COL..." truncated COLOR button — minor.
3. Wavelet landscape now WORKS (post a9e39dc): SETUP tab (FAM/BND/MODE spinners, THRESH SOFT,
   VIEW LOG/L-NORM, "safe FS" status) and SLIDERS tab (LVL/ORD/FS/THR) render fine. Confirm on
   device after the rebuild. Portrait Wavelet SLIDERS header labels are partly cut at the top
   (the "LVL ORD FS THR" row overlaps the slider tops slightly) — minor polish.
4. DISPLAY tab order/labels look good (Size / Step / ENHANCE:n / Blur:n / COLOR). No action
   unless user requests.

## UI_CHANGELIST.md is STALE
It still shows items 1.1/1.2/2.1-2.4/3.1 as unchecked (⬜) even though REDESIGN_NOTES confirms
they are done. Reconcile: mark completed items ✅. (Low priority, do when convenient.)

## Suggested order of work in Claude Code
1. Fix the Nexus 7 save regression (revert/repair a3429af). Build, uninstall, install, test.
2. Decide FLAC vs WAV-default properly (MediaMuxer or WAV-for-all) and implement+build+test.
3. Fix band-label corruption (ViewUtils.kt auto-size skip list). Build, screenshot both orientations.
4. Reconcile UI_CHANGELIST.md.
5. Minor landscape/portrait label polish as desired.
ALWAYS: build with gradlew before claiming done; uninstall on devices before installing;
commit + push each green change as its own commit; keep this HANDOFF.md updated at end of session.
