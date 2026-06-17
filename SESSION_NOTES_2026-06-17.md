# Session notes — 2026-06-17 (FFTT04L, branch `main`, minSdk 23)

L received the **same** changes as M this session (the two apps are kept in lockstep). For the full
write-up — equalizer AUTO/MANUAL + band-AGC de-tilt, Bluetooth removal, USB transfer reliability +
arm-and-defer delete, the raw-recording invariant, and all the `AGC_*` tunables — see the companion
doc in the M repo: **`FFTT04M/SESSION_NOTES_2026-06-17.md`**.

End-of-session debug build letter: **L = `M`**.

Files touched (identical to M): `MainActivity.kt`, `GalleryActivity.kt`,
`res/layout/activity_main.xml`, and new `AgcBarsOverlay.kt`.

L-specific note: the cough auto-capture path is legacy-safe (disables itself on overload rather than
crashing); otherwise behavior matches M.
