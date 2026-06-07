# FFTT04M — User Manual

A real-time audio spectrogram and wavelet analysis tool. Listen live, freeze and crop
interesting moments, save them, and analyse them with FFT or wavelet transforms.

*This manual is bundled with the app and opened from the **HELP** button in the Gallery.*

---

## Quick start

1. On the **Listen** screen, grant microphone access when prompted.
2. Watch the live spectrogram scroll. Tap it once to **freeze**; tap again to resume.
3. While frozen, drag a box to **crop** a region, then **Save** it.
4. Open the **Gallery** to see saved recordings.
5. Tap a recording to open the **FFT analysis** viewer; from there you can switch to
   **Wavelet** analysis or play the audio.

---

## Screens

### Listen (live)
The home screen shows the live microphone spectrogram (frequency vs. time, colour = intensity).

- **Tap** the spectrogram to freeze / unfreeze.
- **Freeze, then drag** a rectangle to select a time/frequency region.
- **Save** writes the crop as a `.wav` recording (16-bit PCM) plus a thumbnail.
- **COLOR** opens the colour-scheme picker (see *Colour schemes*). In Listen mode your
  choice is global and remembered across sessions.
- **GALLERY** opens saved recordings. **LATENCY** helps measure audio round-trip delay.
- The EQ sliders (100 Hz … 8 kHz) shape what you hear during live monitoring.

### Gallery
A grid/list of saved recordings, each with a thumbnail and filename.

- **Tap** a recording to open it in the **FFT analysis** viewer.
- The grid/list toggle (top-left) switches layout.
- **SHARE** sends/receives recordings between devices (see *Sharing*).
- **HELP** opens this manual. **LISTEN** returns to the live screen.

### FFT analysis (Viewer)
Detailed FFT spectrogram of a saved recording, with three tabs:

- **EQ** — per-band gain sliders.
- **FILTER** — noise filter %, plus attack (Rise) and release (Fall) times.
- **DISPLAY** — FFT **Size** and **Step** (overlap), **ENHANCE**, and **COLOR**.

Top bar: **GALLERY**, **LISTEN**, **WAVELET** (analysis of the same file), **NOTE**
(add a comment / refresh the thumbnail), **PLAY** (audio playback).

In analysis screens the colour choice is **tied to the recording**, so each recording
remembers its own scheme. The first time, it inherits your last-used (global) scheme.

### Wavelet analysis
A continuous/discrete wavelet view of the recording, with two tabs:

- **SETUP** — choose the **MODE**, then the **FAMILY** (its choices change with the mode),
  the boundary handling (**BND**), soft/hard **Threshold**, and **View** options (LOG,
  L-NORM). A safety note warns when the sampling rate is above the safe limit for the mode.
- **SLIDERS** — **LEVEL** (decomposition depth), **ORDER**, **SAMPLE** (rate), **THRESH**.

New recordings default to **CWT, max level/order, zero threshold, max safe sample rate**.
All settings persist per recording.

---

## Analysis modes (Wavelet)

| Mode | What it does | FAMILY choices |
|------|--------------|----------------|
| **DWT** | Discrete wavelet transform | Daubechies / Symlet / Coiflet |
| **WPT** | Wavelet packet transform | Daubechies / Symlet / Coiflet |
| **CWT** | Continuous wavelet transform (best frequency detail) | Morlet / Mexican Hat |
| **Reconstruct** | Inverse transform (denoise preview) | Daubechies / Symlet / Coiflet |

- **Morlet** — the default CWT wavelet; excellent for tonal, "squiggly" pitch tracks.
- **Mexican Hat (Ricker)** — a second-derivative shape that isolates ridge peaks and
  transients.

---

## Colour schemes

Eight perceptually-designed colour maps (256-level gradients):

**Turbo** (default), **Viridis**, **Magma**, **Inferno**, **Plasma**, **Cividis**
(colour-vision-deficiency friendly), and **Gray**.

Open the picker from the **COLOR** button on any screen. Tap a swatch to apply it
immediately. Listen remembers your choice globally; analysis screens remember it per
recording.

---

## Enhancements (FFT analysis → DISPLAY → ENHANCE)

Pick one **engine** (or none) and stack any number of **post-processors**:

- **Gaussian / Bilateral / TV Denoise / Butterworth** — general smoothing/denoise.
- **Anisotropic** — edge-preserving diffusion; smooths along ridges, keeps edges sharp.
- **Gabor ridges** — boosts oriented "squiggly" spectral lines of any slope.
- **Frangi ridges** — multi-scale ridge (vesselness) detector for continuous lines.

Heavier filters (Gabor, Frangi) are disabled on older/low-memory devices and labelled
"(needs newer device)".

---

## Sharing recordings between devices

Two devices running this app can transfer recordings — **with all their analysis settings,
comments, and thumbnails** — directly over Wi-Fi, no internet or account needed.

1. Put **both devices on the same Wi-Fi network**.
2. On the **receiving** device: **Gallery → SHARE → Receive onto this device**. A QR code appears.
3. On the **sending** device: **Gallery → SHARE → Send to another device**, then point the
   camera at the other device's QR code.
4. The whole gallery transfers; imported recordings keep their own colour/analysis settings.
   Name clashes are auto-renamed (e.g. `…_imp`), so nothing is overwritten.

The QR code only carries the connection handshake; the audio itself streams over Wi-Fi.

## Tips

- If a recording looks empty, check the COLOR scheme and the LOG/L-NORM view toggles.
- On older devices, wavelet analysis automatically eases its settings to avoid running
  out of memory; a brief message appears when it does.
- Playback uses the raw PCM audio of the recording.

---

*Draft manual — updated as features change.*
