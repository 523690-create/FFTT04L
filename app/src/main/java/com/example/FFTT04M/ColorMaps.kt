package com.example.FFTT04M

import android.content.Context
import android.content.SharedPreferences
import android.graphics.Color

/**
 * Single source of truth for spectrogram colour schemes, shared by FFTHeatMapView and WaveletView.
 *
 * Each scheme is defined by a handful of anchor stops and expanded once (at class load) into a
 * 256-entry LUT. Renderers can index the LUT directly — lut[(value * 255).toInt()].
 *
 * Scheme order (Turbo is the default; the old crude 7-colour "Default/Heat" map was removed in
 * favour of true Turbo):
 *   0 Turbo  1 Viridis  2 Magma  3 Inferno  4 Plasma  5 Cividis  6 Gray
 *
 * Persistence model (see the load/save helpers):
 *   - Listen (live) → GLOBAL scheme in "app_settings", persists across sessions.
 *   - Analysis (Viewer/Wavelet) → tied to the recording's prefs, falling back to global the first
 *     time; saving also updates global so the choice carries across screens.
 */
object ColorMaps {

    val names = arrayOf(
        "Turbo", "Viridis", "Magma", "Inferno", "Plasma", "Cividis", "Gray"
    )

    private val anchors: Array<IntArray> = arrayOf(
        // 0 Turbo (Google) — now the default
        hexes("#30123b", "#4145ab", "#4675ed", "#39a2fc", "#1bcfd4", "#24eca6", "#61fc6c", "#a4fc3b", "#d1e834", "#f3c63a", "#fe9b2d", "#f36315", "#d93806", "#b11901", "#7a0402"),
        // 1 Viridis
        hexes("#440154", "#482878", "#3e4a89", "#31688e", "#26828e", "#1f9e89", "#35b779", "#6ece58", "#b5de2b", "#fde725"),
        // 2 Magma
        hexes("#000004", "#140e36", "#3b0f70", "#641a80", "#8c2981", "#b73779", "#de4968", "#f7705c", "#fe9f6d", "#fcfdbf"),
        // 3 Inferno
        hexes("#000004", "#1f0c48", "#550f6d", "#88226a", "#a83655", "#cc4248", "#ec6824", "#fb9b06", "#f7d03c", "#fcffa4"),
        // 4 Plasma
        hexes("#0d0887", "#41049d", "#6a00a8", "#8f0da4", "#b12a90", "#cc4778", "#e16462", "#f2844b", "#fca636", "#fcce25", "#f0f921"),
        // 5 Cividis (CVD-optimised)
        hexes("#00204d", "#00336f", "#39486b", "#575d6d", "#707173", "#8a8779", "#a69d75", "#c4b56c", "#e4cf5b", "#ffea46"),
        // 6 Grayscale
        hexes("#000000", "#FFFFFF")
    )

    /** 256-entry LUTs, one per scheme, built once from the anchors above. */
    val luts: Array<IntArray> = Array(anchors.size) { buildLut(anchors[it]) }

    val count: Int get() = luts.size

    fun lut(index: Int): IntArray = luts[index.coerceIn(0, luts.size - 1)]

    /** Lowest-value (bottom-of-scale) colour — used as the COLOR control background. */
    fun lowColorFor(index: Int): Int = lut(index)[0]

    /** Highest-value (top-of-scale) colour — used as the COLOR control text/accent. */
    fun highColorFor(index: Int): Int = lut(index)[255]

    // ---- Persistence ----------------------------------------------------------------------------

    const val DEFAULT = 0                         // Turbo
    private const val PREF_KEY = "color_scheme"
    private const val PREF_VER = "color_scheme_ver"
    private const val CUR_VER = 2                 // bumped when the index order changed
    private const val GLOBAL = "app_settings"
    // old order (v1): 0 Default 1 Viridis 2 Magma 3 Gray 4 Inferno 5 Plasma 6 Turbo 7 Cividis
    // new order (v2): 0 Turbo 1 Viridis 2 Magma 3 Inferno 4 Plasma 5 Cividis 6 Gray
    private val REMAP_V1_TO_V2 = intArrayOf(0, 1, 2, 6, 3, 4, 0, 5)

    private fun migrate(p: SharedPreferences) {
        if (p.contains(PREF_KEY) && p.getInt(PREF_VER, 1) < CUR_VER) {
            val old = p.getInt(PREF_KEY, DEFAULT)
            p.edit()
                .putInt(PREF_KEY, REMAP_V1_TO_V2.getOrElse(old) { DEFAULT })
                .putInt(PREF_VER, CUR_VER)
                .apply()
        }
    }

    private fun global(ctx: Context) = ctx.getSharedPreferences(GLOBAL, Context.MODE_PRIVATE)

    /** Listen (live): the global scheme. */
    fun loadGlobal(ctx: Context): Int {
        val g = global(ctx); migrate(g)
        return g.getInt(PREF_KEY, DEFAULT).coerceIn(0, count - 1)
    }

    fun saveGlobal(ctx: Context, idx: Int) {
        global(ctx).edit().putInt(PREF_KEY, idx).putInt(PREF_VER, CUR_VER).apply()
    }

    /** Analysis: the recording's own scheme, falling back to the global one when unset. */
    fun loadForRecording(ctx: Context, rec: SharedPreferences): Int {
        migrate(rec)
        val idx = if (rec.contains(PREF_KEY)) rec.getInt(PREF_KEY, DEFAULT) else loadGlobal(ctx)
        return idx.coerceIn(0, count - 1)
    }

    /** Analysis: persist to the recording AND to global, so the choice carries across screens. */
    fun saveForRecording(ctx: Context, rec: SharedPreferences, idx: Int) {
        rec.edit().putInt(PREF_KEY, idx).putInt(PREF_VER, CUR_VER).apply()
        saveGlobal(ctx, idx)
    }

    // ---- LUT construction -----------------------------------------------------------------------

    private fun hexes(vararg s: String): IntArray = IntArray(s.size) { Color.parseColor(s[it]) }

    private fun buildLut(stops: IntArray): IntArray {
        val lut = IntArray(256)
        if (stops.size == 1) {
            for (i in 0..255) lut[i] = stops[0]
            return lut
        }
        val segs = stops.size - 1
        for (i in 0..255) {
            val t = i / 255f * segs
            val seg = t.toInt().coerceAtMost(segs - 1)
            val frac = t - seg
            lut[i] = lerp(stops[seg], stops[seg + 1], frac)
        }
        return lut
    }

    private fun lerp(c1: Int, c2: Int, f: Float): Int {
        val r = (Color.red(c1) + (Color.red(c2) - Color.red(c1)) * f).toInt()
        val g = (Color.green(c1) + (Color.green(c2) - Color.green(c1)) * f).toInt()
        val b = (Color.blue(c1) + (Color.blue(c2) - Color.blue(c1)) * f).toInt()
        return Color.rgb(r.coerceIn(0, 255), g.coerceIn(0, 255), b.coerceIn(0, 255))
    }
}
