# **AGENTS.md**

## **Agent Behavior Rules**

### **General Conduct**
- Do not apologize under any circumstances.
- Maintain a direct, technical, execution‑focused communication style.
- Do not introduce changes that were not explicitly requested.
- When prompted to modify the project, apply **only** the most recently requested changes.

### **Build & Sync Discipline**
- After every iteration, perform a full **Gradle sync** and **project build**.
- Automatically correct any errors encountered during sync or build.
- Do not consider a task complete, and do not present the project as ready for user review, if either sync or build fails.
- Continue refining until both sync and build succeed without warnings or errors.

### **Project Structure & Debuggability**
- Break down each feature or workflow into **multiple small, focused Activities** (or Fragments when appropriate) to simplify debugging and isolate failures.
- Prefer modular, testable components over large monolithic structures.
- When adding new functionality, create a dedicated Activity unless explicitly instructed otherwise.

### **Change Management**
- When the user issues a new instruction, treat it as the sole source of truth.
- Do not re‑interpret or re‑apply older instructions unless the user restates them.
- Avoid refactoring, optimizing, or restructuring unrelated parts of the project unless explicitly asked.

### **Execution Expectations**
- Provide clear, minimal diffs or file‑level changes.
- Do not modify files unrelated to the requested change.
- Ensure all generated code adheres to Android Studio’s current best practices and compiles cleanly.

---

## **Addendum**
- Use a red‑blue roundel as the project icon.
- For each build, name the APK file `FFTT04M-[date and time stamp].apk` and place it in the project root.
- To generate a timestamped APK, run:
  `$timestamp = Get-Date -Format "yyyyMMdd-HHmmss"; ./gradlew :app:assembleDebug; if ($?) { cp app/build/outputs/apk/debug/app-debug.apk "FFTT04M-$timestamp.apk" }`

---

## **Project Conventions (inline code documentation)**

These conventions apply when writing or reviewing source code. They are documented here so
agents do not need to re‑infer them from the codebase on each session.

### Layout & strings
- `%%` in `strings.xml` values is the Android XML escape for a literal `%` character.
  It renders correctly as `%` on device. Do **not** treat it as a format bug.
- EQ band frequency labels (`label_100hz` through `label_8khz`) use a two‑line format
  (`100\nHz`). At narrow screen widths (~360 dp) the `wrap_content` label clips because
  each band column is only ~40 dp wide (`layout_weight="10"` in a `weightSum="54"` parent).
  **Known issue — TODO:** shorten labels to the numeric part only (e.g. `100`, `300`, `1k`).

### Audio playback
- Playback always uses **raw decoded PCM**. EQ biquad filters and noise‑filter spectral
  subtraction are applied to the *visualisation* pipeline only. Do **not** apply them to
  audio output: EQ boosts clip past full‑scale, and spectral‑subtraction block
  reconstruction has no COLA normalisation, producing discontinuities.

### Enhance / refresh dispatch
- `triggerRefresh()` is the single entry point for all spectrogram recomputation.
  It increments `refreshCount` (under `refreshLock`) and cancels the previous thread
  via `InterruptedException`. Always call `triggerRefresh()` rather than touching
  `FFTHeatMapView` directly from UI callbacks.
- Enhance mode priority (highest wins when multiple are selected):
  index 5 – Sweep+ → index 0 – Sweep → standard FFT.
  Post‑processors (indices 1–4: Gaussian, Bilateral, TV Denoise, Butterworth) run after
  whichever engine was selected, via `applyEnhancements()`.

### SharedPreferences keys (ViewerActivity)
| Key | Type | Meaning |
|-----|------|---------|
| `fft_size_idx` | Int | Index into `fftValues` array (256/512/1024/2048/4096) |
| `fft_step_idx` | Int | Index into `fftValues` for step size |
| `color_scheme` | Int | 0=Default, 1=Viridis, 2=Magma, 3=Gray |
| `blur_radius` | Int | 0–10 |
| `enhance_mask` | Int | Bitmask over `enhanceModeNames` array |
| `noise_filter_strength` | Float | 0.0–1.0 |
| `noise_filter_rise_ms` | Float | Rise time in ms (stored linear; slider is log₂) |
| `noise_filter_fall_ms` | Float | Fall time in ms (stored linear; slider is log₂) |
| `eq_gain_0`…`eq_gain_4` | Float | Per‑band EQ gain in dB (−40…+40) |

### Display tab — live preview behaviour
- Size and Step spinners call `triggerRefresh()` immediately on selection.
  The spectrogram updates live as the user moves the spinner — this is intentional,
  not a bug. Do not add a confirmation step unless explicitly requested.
