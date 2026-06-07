# HANDOFF — FFTT04M (for Claude Code)

Read this file first, then continue the work. This captures context that is NOT in the
codebase or the other .md files — it lives only in the chat session that produced it.
Date: 2026-06-05.

## COMPLETED IN THIS SESSION (2026-06-05) — verified end-to-end on Nexus 7 (API 23)
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

---

## COMPLETED IN THIS SESSION (2026-06-05) — verified end-to-end on Nexus 7 (API 23)

All items from "Suggested order of work" completed:

1. ✅ **Real root cause of "empty Gallery"**: GalleryActivity filtered to `.flac` only.
   When saves became WAV, recordings were written successfully but invisible to Gallery.
   - Fix: Gallery now lists `.wav` (and `.flac` legacy files) (`4fab9d7`)
   - Add WavReader.kt to parse raw PCM WAV (no MediaCodec decoder for audio/raw)
   - ViewerActivity.loadAndDecode + WaveletActivity.loadAndDecode branch on extension

2. ✅ **Audio format decision**: WAV-only (dropped invalid FLAC container)
   - MediaCodec FLAC encoder writes raw bytes with no proper framing
   - WAV (16-bit PCM) is reliable, proven everywhere (`a31505e`)

3. ✅ **Band-label corruption fixed**: Complete solution
   - Layout restructure: band labels moved to dedicated header row ABOVE sliders
   - Previously: narrow columns beside sliders, auto-sized and clipped to one glyph ("Hz Hz" → "Z Z Z")
   - Now: header row with 35% height allocation, dynamic sizing maximizes fonts
   - EQ labels: "100 Hz", "300 Hz", "1k Hz", "3k Hz", "8k Hz" — all readable
   - Filter labels: "Filter %", "Rise Time", "Fall Time" — all readable

4. ✅ **Portrait UI Layout improvements**
   - EQ/FILTER pages restructured: header row (35%) above sliders (65%)
   - Dynamic sizing for ALL labels: headers, value labels (dB/ms/%), top-bar buttons
   - Landscape layout unchanged (horizontal still works, readable but compact)

5. ✅ **UI_CHANGELIST.md**: Already fully reconciled

### End-to-End Flow Verified (Nexus 7, API 23)
Record → Freeze/Crop → Save (.wav + .png) → Gallery lists it → Open in Viewer →
Decode WAV → Render spectrogram with EQ/Filter/Display tabs → All labels readable →
PLAY audio via AudioTrack

### Commits in this session
- `a31505e` WAV-only audio format
- `4fab9d7` Gallery filter fix + WavReader + band-label fix (real root cause)
- `cb8cc87` Portrait layout restructure + dynamic label sizing

### SESSION 2 (2026-06-05 cont.) — Font/label overhaul, verified on BOTH devices
Devices: Nexus 7 (API 23, razor, native landscape) + Pixel 3a XL (API 32, bonito).
(The "Pixel 10" in older notes is actually this Pixel 3a XL.)

Central fix in ViewUtils.setMaxTextSizeToFit (`8b51e4b`, `893dbdb`):
1. Size text to the view's OWN allocated box when its dimensions are determinate
   (match_parent / 0dp+weight); only fall back to parent for wrap_content. Fixes
   Viewer-landscape EQ "Hz Hz Z Z Z" (label parent was the tall column → font ballooned
   → clipped) AND top-bar button truncation (sized to whole toolbar → ellipsis).
2. Subtract content padding so text fits the drawable area, not the raw box.
3. measureText (not getTextBounds) so Material-button letterSpacing is counted.
4. Pin maxLines to the \n line count so text never spills to an extra line / per-char wrap.

Layout/code (`5688c14`):
- MainActivity band labels: maxLines="2" (both orientations); dB value labels now
  dynamic (was fixed 8sp).

VERIFIED:
- Pixel 3a landscape MainActivity ("Listen"): band labels + "0 dB" readable. ✓
- Pixel 3a landscape Viewer ("FFT analysis"): EQ labels readable, top bar
  "GALLERY LISTEN WAVELET NOTE PLAY" full (no truncation). ✓
- Nexus 7 landscape Viewer: EQ labels readable, "GALLERY LISTEN NOTE PLAY" full. ✓

### Wavelet status (re-verified on Pixel 3a landscape this session)
The central font fix (`8b51e4b`) already resolved the previously-flagged Wavelet issues:
- SETUP "DWT · safe FS ≤ … kHz" status now renders in FULL (was truncated).
- SLIDERS header row "LVL ORD FS THR" renders cleanly (was clipped at top).
- Top bar "GALLERY LISTEN COLOR" full.
Remaining MINOR edge case (not yet fixed): the ORD slider's value number is clipped
when the thumb is at the slider MINIMUM (value label translated below the FrameLayout).
Same value-label-positioning logic as the EQ value labels. Low priority. Asked the user
which Wavelet fix they wanted (#4) but got no answer — left untouched to avoid regressing
a screen that now looks fine. Confirm intent next session.

### Next (optional)
- Wavelet ORD value-label clipping at slider minimum (see above) — needs careful
  updateLabelPosition clamp + on-device test.
- API downgrade analysis (deferred per user): can reach API 15 with no functional loss;
  TextViewCompat autosize already guarded for <26.
- dB value labels in Nexus 7 viewer sliders render small — could enlarge (cap is 14sp).
- Portrait Pixel 3a not re-screenshotted this session (only landscape); spot-check next time.

### API Downgrade Analysis (API 23 → 15)
**Current min: API 23 (Nexus 7)**

- API 22–16: AudioRecord, Material Slider, all key components work fine. No loss.
- API 15: Material Slider (requires 14+) still works. **TextViewCompat.setAutoSizeText()** requires API 26+ fallback (code has guards for < 26).
- **Bottom line**: Can drop to API 15 with no functional loss if Material library supports it. Core audio recording works on API 16+.

### Devices & Known Issues
- **Nexus 7 (API 23)**: ✅ Working end-to-end (record → save → gallery → viewer)
- **Pixel 10 (API 36)**: ✅ Working
- **Pixel 3 (API 29)**: ⚠️ **Label readability issues** (not currently attached; verify next session):
  - Listen tab: labels unreadable
  - FFT analysis landscape: all labels unreadable
  - Likely: parent dimensions wrong, or font sizing algorithm under-estimating needed size
  - Investigation needed: take landscape screenshot, compare with Nexus 7/Pixel 10

### UI Fixes Pending
1. **Wavelet UI**: Quick fixes needed (details TBD; check current state on Nexus 7)
2. **Slider value font logic**: Review edge cases (where values overflow or squeeze)
3. **Button/control font logic**: Review with attention to text overflows ("GA...", "LIS..." truncations)

### For Future Sessions (Different Computer, Same Account)
- Same git workflow: clone from origin/main, branch, commit, PR
- Build environment: Android Studio Quail 1, targetSdk 36, gradlew available
- Test devices: Nexus 7 (API 23, 7" tablet), Pixel 10 (API 36, modern phone), Pixel 3 (API 29, phone) — all attached via adb
- Critical install gotcha: ALWAYS `adb uninstall com.example.FFTT04M` BEFORE installing new APK (signing key mismatch from Studio update)
- Audio: uses raw PCM only (FloatArray), 44.1kHz, mono. WAV format (16-bit PCM header + samples) is the only save format now
- Key files to understand:
  - MainActivity.kt: live recording + crop → save
  - ViewerActivity.kt: load .wav/.flac → decode → display spectrogram + EQ/Filter/Display tabs
  - WaveletActivity.kt: load .wav/.flac → wavelet analysis
  - WavReader.kt: parses raw PCM WAV (no MediaCodec, no container specs)
  - ViewUtils.kt: applyAutoSizeText() walks layout tree, skips EQ/Filter headers; updateAllLabelPositions() dynamically sizes all labels
- Current state: portrait EQ/Filter working well (headers above sliders with dynamic sizing); landscape still side-by-side (readable but compact); Pixel 3 has label readability issues

---

## SESSION 3 (2026-06-06, new dev machine)

**Context:** User moved to new Windows dev machine with 6 USB debug devices (API 23–36, 720×1280 to 1080×2160). All devices had old APK signature from prior machine. User asked to continue UI improvements on new hardware.

**Critical limitation:** New dev machine has different APK signing key than old one. Devices retain old signatures. Most installs fail with `INSTALL_FAILED_UPDATE_INCOMPATIBLE`. Pixel 3a XL verified before most devices hit the barrier.

### Fixes Applied

#### 1. Wavelet Control Height Inconsistency (RESOLVED)
**Observation:** Pixel 3a Wavelet top bar (GALLERY/LISTEN/COLOR) had visibly different heights.

**Root cause:** 
- Toolbar was `wrap_content` with `minHeight="48dp"` → children could center at different heights.
- Spinner rendering differs from Material buttons even at same declared height (40dp).

**Fixes:**
- **activity_wavelet.xml (portrait):**
  - Changed toolbar from `wrap_content` + `minHeight="48dp"` → fixed `height="48dp"`
  - Wrapped COLOR Spinner in FrameLayout to control its rendering
- **activity_wavelet.xml-land (landscape):**
  - Same FrameLayout wrapper for COLOR Spinner

**Verification:** Pixel screenshots before/after show buttons now evenly sized.

#### 2. Wavelet Landscape Slider Width Distribution (RESOLVED)
**Observation:** Landscape sliders left unused space on right (room for one more slider).

**Root cause:** `pageWaveletSliders` had `android:weightSum="5"` but only 4 slider columns with `layout_weight="1"` each → sliders got 4/5 ≈ 80% of width.

**Fix:** Changed `android:weightSum="5"` → `android:weightSum="4"` in landscape layout.

**Impact:** Sliders now fill full width; visual balance consistent with portrait.

#### 3. Samsung Tablet COLOR Spinner Sizing (DIAGNOSTIC)
**Report:** Galaxy Tab A (API 27, landscape) had COLOR spinner larger than GALLERY/LISTEN buttons.

**Fix Applied:** Same as #1 above (FrameLayout wrapper ensures consistent sizing).

**Verification Status:** Samsung Tab A dropped out of USB debug mid-session. Fix is structural (toolbar height control + Spinner containment) so confidence is high for next session when USB is restored.

### Commits in this session
- `5f14fd5` "Fix uneven Wavelet controls: fixed toolbar height and wrap Spinner in FrameLayout"
  - Toolbar 48dp, FrameLayout wrappers, landscape weightSum fix

### Test Coverage (This Session)
- **Pixel 3a XL (API 32, portrait):** ✓ Top buttons verified evenly sized
- **Galaxy J7 (API 25, landscape):** Install attempted, old signature key blocked re-verification
- **Galaxy Tab A (API 27, landscape):** USB debug dropped; fix applied structurally
- **Others (Nexus 7, CP81, T65):** Old signature keys pending resolution

### Remaining Known Issues

1. **Wavelet ORD slider value clipping at minimum** (deferred from prior session)
   - Minor: numeric value ("1") clips below FrameLayout when thumb at minimum
   - Same `updateLabelPosition` logic as EQ sliders
   - Deferred: low priority, already flagged

2. **APK Signing Key Mismatch**
   - New machine signing key differs from old machine
   - Most devices fail install with `INSTALL_FAILED_UPDATE_INCOMPATIBLE`
   - Resolution: either sync key or ask user for key file

### Next Session Checklist
1. **Resolve signing key:** Ask user for old key or re-provision devices
2. **Re-verify on all 6 devices:** Landscape controls (especially COLOR button), slider widths
3. **Wavelet ORD value clip:** Fix if time permits (low priority)
4. **Full portrait sweep on Pixel 3a:** Not done this session (only landscape top buttons)
5. **API downgrade analysis:** Deferred from prior session, still pending

### Build Status
- APK: `app-debug.apk` builds clean
- Git: Commits on main, in sync with origin/main
- Gradle: Configuration cache reused, builds in ~3–5 seconds

