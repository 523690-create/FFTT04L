# FFTT04M Three-Project Split - Complete Setup

## ✅ Completed

### Projects Created
1. **FFTT04M** (H:\FFTT04M) - High-API cough analysis
   - Branch: blue_sky
   - minSdk: 32
   - Features: Full Tier-1/2/3 DSP, cough detection, MFCC, phases
   - Icon: Original cyan/magenta roundel

2. **FFTT04L** (H:\FFTT04L) - Legacy baseline
   - Branch: main
   - minSdk: 23
   - Features: Core FFT, RMS/Peak, Bluetooth, gallery
   - Icon: Square-in-square (to be updated)

3. **FFTT04D** (H:\FFTT04D) - Desktop analyzer
   - Branch: port_windows
   - Platform: Windows Java 8+
   - Launcher: CoughAnalyzer.bat
   - Desktop Shortcut: Created (FFTT04D - Cough Analyzer.lnk)
   - Features: Batch analysis, Coswara/ESC-50 loading, DSP foundation

### Infrastructure
- ✅ All 3 projects cloned and branch-switched
- ✅ ARCHITECTURE.md created documenting algorithm homology
- ✅ Desktop launcher script (CoughAnalyzer.bat) created
- ✅ Desktop shortcut created on user desktop
- ✅ Coswara dataset loading with tar.gz extraction (port_windows)
- ✅ Tier-1 DSP code replicated in desktop module

### Commits
- FFTT04L: Documented architecture (main branch)
- FFTT04D: Documented architecture (port_windows branch)
- port_windows: Coswara tar extraction + DSP library foundation

## ⏳ Remaining Tasks

### Icons (Design)
- [ ] FFTT04L: Update icon to square-in-square design
  - Outer: Magenta (#FF00FF)
  - Inner: Cyan (#00FFFF)
  - This is opposite of current (cyan outer, magenta inner)
  - Update in: app/build.gradle.kts icon generation

### Desktop Launcher
- [ ] Pin CoughAnalyzer.lnk to Windows taskbar
  - Currently: Desktop shortcut only
  - Goal: Taskbar pin for quick access
  - Alternative: Create VBS wrapper if .lnk pinning fails

### Feature Integration
- [ ] Desktop DSP: Integrate full Tier-1 DSP into Main.kt
  - CoughAnalyzer class is available in desktop/cough package
  - Need to call analyzer.analyze(pcm, 44100) in analyzeAll()
  - Display per-event features: FFT Q-ratio, ridge f0, phases, MFCC

### Testing
- [ ] FFTT04M: Build and test on modern devices
- [ ] FFTT04L: Build and test on legacy devices (API 23)
- [ ] FFTT04D: Run desktop analyzer with real datasets

## File Structure

```
H:\
├── FFTT04M/          # High-API (blue_sky)
│   ├── app/          # Android app (API 32+)
│   ├── shared/       # Shared code
│   └── ARCHITECTURE.md
│
├── FFTT04L/          # Legacy (main)
│   ├── app/          # Android app (API 23+)
│   ├── shared/       # Shared code
│   ├── ARCHITECTURE.md
│   └── THREE_PROJECT_SETUP.md
│
└── FFTT04D/          # Desktop (port_windows)
    ├── desktop/      # Java desktop app
    ├── app/          # Android app (for reference)
    ├── ARCHITECTURE.md
    ├── CoughAnalyzer.bat
    └── desktop\build\libs\CoughAnalyzer.jar
```

## Build & Deploy Commands

### FFTT04M (High-API Android)
```bash
cd H:\FFTT04M
./gradlew app:assembleDebug
adb install -r app/build/outputs/apk/debug/*.apk
```

### FFTT04L (Legacy Android)
```bash
cd H:\FFTT04L
./gradlew app:assembleDebug
adb install -r app/build/outputs/apk/debug/*.apk
```

### FFTT04D (Desktop)
```bash
cd H:\FFTT04D
./gradlew desktop:build
java -jar desktop\build\libs\CoughAnalyzer.jar
# Or use: H:\FFTT04D\CoughAnalyzer.bat
```

## Consistency Rules Going Forward

1. **Bug Fixes**: main (FFTT04L) → port to blue_sky (FFTT04M)
2. **New Features**: Stay in blue_sky, don't backport to main
3. **Algorithm Changes**: Apply to all 3 projects simultaneously
4. **Sample Rate**: Always 44.1 kHz (resample at I/O boundaries)
5. **Feature Normalization**: Z-score standardization mandatory
6. **DSP Code**: Keep identical across all platforms

## Next Phase

Once complete:
1. Full Tier-1 DSP integration in FFTT04D
2. Tier-2 TDNN inference for desktop
3. Model training pipeline setup
4. Cloud dataset consolidation
