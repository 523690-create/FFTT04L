# FFTT04M — UI Change List

Tracking the UI review requests from 2026-06-04. Status legend: ✅ = done & committed (green) · ⬜ = pending · 🟨 = in progress.

> **NOTE (2026-06-04):** The tabbed EQ / FILTER / DISPLAY UI shown in the review screenshots exists in the **local `H:\FFTT04M`** working copy but is **not yet pushed to GitHub** — `main` still has the older single-row `activity_viewer.xml`. Code edits below must be applied against the local tabbed layout, not the stale GitHub version. Resolve by pushing local first, or by editing the local files directly.

---

## 1. Top control bar (FFT analysis mode)
- ⬜ 1.1 Move the COLOR spinner out of the top bar (it moves into the Display tab stack — see 2.3)
- ⬜ 1.2 With COLOR removed, re-review remaining top-bar control sizes and label fonts for best fit

## 2. Display tab stack
- ⬜ 2.1 Remove the superfluous orange Enhance spinner
- ⬜ 2.2 Move the Blur spinner up into the freed position (it currently sits too far down)
- ⬜ 2.3 Add the COLOR spinner below Blur
- ⬜ 2.4 Leave ample empty space below the stack to clear any system control bars

## 3. FFT display corner labels (Sz / St)
- ⬜ 3.1 Synchronise the `Sz` / `St` labels (top-right of the spectrogram) with the **live** Size and Step spinner values

## 4. Enhance control bug
- ⬜ 4.1 Fix: on first engagement the dialog fails to capture the selected checkboxes (works only on a second attempt)

## 5. EQ slider band labels & sizing
- ⬜ 5.1 Fix band-label clipping — caused by labels living in the wrong parent
- ⬜ 5.2 Each EQ slider width = 0.15 × screen width; gutter = 0.05 × screen width
- ⬜ 5.3 dB value labels adjusted to fit within the green slider bar
- ⬜ 5.4 Frequency labels placed directly above the slider bar (not above the gutter); font optimised for the space

## 6. Filter slider band labels & sizing
- ⬜ 6.1 Same label-parent / clipping fix as EQ
- ⬜ 6.2 Each Filter slider (bar) width = 0.25 × screen width; gutter = 0.08 × screen width
- ⬜ 6.3 Value labels fit within bar; frequency/name labels directly above bar; font optimised

---

## Commit log (most recent first)
_(entries added as each increment lands)_
