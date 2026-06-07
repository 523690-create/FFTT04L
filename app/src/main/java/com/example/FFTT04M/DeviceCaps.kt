package com.example.FFTT04M

import android.app.ActivityManager
import android.content.Context
import android.os.Build

/**
 * Coarse device-capability tiers, so heavy analysis/enhancement features can be hidden or eased
 * on old / low-RAM hardware (e.g. Nexus 7, Galaxy J7) instead of running unusably slowly.
 *
 *   tier 0 = legacy  (pre-Oreo OR < ~2 GB RAM)
 *   tier 1 = mid     (Oreo .. Android 11)
 *   tier 2 = modern  (Android 12 / API 31+)
 *
 * Gate at three levels (cheapest → most graceful):
 *   - hide the control          (e.g. heavy enhancement checkboxes on tier 0)
 *   - disable + annotate        (visible but "(needs newer device)")
 *   - auto-degrade parameters   (fewer CWT scales / iterations on tier 0)
 * Whenever a feature silently degrades, surface it (Toast / suffix) so cross-device behaviour
 * differences aren't mistaken for bugs.
 */
object DeviceCaps {

    fun tier(ctx: Context): Int {
        val lowRam = try {
            val am = ctx.getSystemService(Context.ACTIVITY_SERVICE) as ActivityManager
            val mi = ActivityManager.MemoryInfo().also { am.getMemoryInfo(it) }
            mi.totalMem in 1..(2L * 1024 * 1024 * 1024)   // < ~2 GB
        } catch (_: Exception) {
            false
        }
        return when {
            Build.VERSION.SDK_INT < Build.VERSION_CODES.O || lowRam -> 0
            Build.VERSION.SDK_INT < Build.VERSION_CODES.S -> 1
            else -> 2
        }
    }

    /** Heavy per-pixel enhancement filters (Gabor bank, Frangi, NLM) need at least mid tier. */
    fun heavyEnhancementsAllowed(ctx: Context): Boolean = tier(ctx) >= 1
}
