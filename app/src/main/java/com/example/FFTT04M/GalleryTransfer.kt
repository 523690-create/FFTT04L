package com.example.FFTT04M

import android.content.Context
import org.json.JSONObject
import java.io.BufferedInputStream
import java.io.BufferedOutputStream
import java.io.File
import java.io.InputStream
import java.io.OutputStream
import java.net.Inet4Address
import java.net.NetworkInterface
import java.util.zip.ZipEntry
import java.util.zip.ZipInputStream
import java.util.zip.ZipOutputStream

/**
 * Device-to-device gallery transfer. A "bundle" is a ZIP containing, per recording, the
 * `.wav` (+ optional `.png` thumbnail, `.txt` comment) and a `<base>.json` of its per-recording
 * SharedPreferences (all analysis/colour state). Plus a `manifest.json`.
 *
 * Transport is a plain TCP socket on the local Wi-Fi; a QR code carries only the handshake
 * (receiver IP:port:token). The bundle streams over the socket — nothing is buffered whole in
 * memory, so it is safe for large galleries on low-RAM devices.
 */
object GalleryTransfer {

    const val HANDSHAKE_PREFIX = "FFTT1"   // QR payload: FFTT1:<ip>:<port>:<token>

    // Per-recording pref keys are mostly Int; these are the exceptions. Used on import so JSON
    // number ambiguity (44100.0 -> "44100") can't write a Float key as an Int (which would later
    // throw ClassCastException on getFloat). New Float/Bool/String keys must be added here.
    private val FLOAT_KEYS = setOf(
        "freq", "threshold", "view_off_x", "view_off_y", "view_zoom_x", "view_zoom_y",
        "noise_filter_strength", "noise_filter_rise_ms", "noise_filter_fall_ms"
    )
    private val BOOL_KEYS = setOf("soft_thresh", "log_scale", "local_norm", "gallery_is_grid")
    private val STRING_KEYS = setOf("mic_device")

    /** Mirrors ViewerActivity/WaveletActivity: per-recording prefs namespace from the file name. */
    fun prefsNameForFile(fileName: String): String {
        val base = fileName.substringBeforeLast('.')
        return "rec_" + buildString { for (c in base) append(if (c.isLetterOrDigit()) c else '_') }
    }

    /** Recordings live in the EXTERNAL files dir (same as GalleryActivity/MainActivity) — NOT the
     *  internal filesDir. Using the wrong one made Send see "no recordings" and Import write to a
     *  place the Gallery doesn't read. */
    fun recordingsDir(ctx: Context): File? = ctx.getExternalFilesDir(null)

    fun recordingWavs(ctx: Context): List<File> =
        recordingsDir(ctx)?.listFiles { f -> f.extension == "wav" || f.extension == "flac" }
            ?.sortedBy { it.name } ?: emptyList()

    // ---- Export ---------------------------------------------------------------------------------

    fun buildBundle(ctx: Context, wavs: List<File>, out: OutputStream) {
        ZipOutputStream(BufferedOutputStream(out)).use { zip ->
            val manifest = JSONObject()
                .put("app", "FFTT04M").put("schema", 1).put("count", wavs.size)
            writeEntry(zip, "manifest.json", manifest.toString().toByteArray())

            for (wav in wavs) {
                val base = wav.nameWithoutExtension
                addFile(zip, wav)
                File(wav.parentFile, "$base.png").takeIf { it.exists() }?.let { addFile(zip, it) }
                File(wav.parentFile, "$base.txt").takeIf { it.exists() }?.let { addFile(zip, it) }

                val prefs = ctx.getSharedPreferences(prefsNameForFile(wav.name), Context.MODE_PRIVATE)
                val meta = JSONObject()
                for ((k, v) in prefs.all) if (v != null) meta.put(k, v)
                writeEntry(zip, "$base.json", meta.toString().toByteArray())
            }
        }
        out.flush()
    }

    private fun addFile(zip: ZipOutputStream, f: File) {
        zip.putNextEntry(ZipEntry(f.name))
        f.inputStream().use { it.copyTo(zip) }
        zip.closeEntry()
    }

    private fun writeEntry(zip: ZipOutputStream, name: String, bytes: ByteArray) {
        zip.putNextEntry(ZipEntry(name))
        zip.write(bytes)
        zip.closeEntry()
    }

    // ---- Import ---------------------------------------------------------------------------------

    /** Reads a bundle stream into the recordings dir, restoring metadata. Recordings already present
     *  (same filename) are skipped. Returns counts of imported vs skipped. */
    fun importBundle(ctx: Context, inp: InputStream): ImportResult {
        val dir = recordingsDir(ctx) ?: return ImportResult(0, 0)
        // Keep original filenames. A recording already present (same base name) is skipped wholesale
        // — files and metadata left untouched — so re-sharing the same gallery is idempotent.
        val isDup = HashMap<String, Boolean>()
        fun duplicate(base: String): Boolean = isDup.getOrPut(base) {
            File(dir, "$base.wav").exists() || File(dir, "$base.flac").exists()
        }

        val pendingMeta = ArrayList<Pair<String, String>>()
        var imported = 0
        var skipped = 0

        ZipInputStream(BufferedInputStream(inp)).use { zip ->
            var e: ZipEntry? = zip.nextEntry
            while (e != null) {
                val nm = e.name
                if (nm != "manifest.json" && !e.isDirectory && nm.contains('.')) {
                    val ext = nm.substringAfterLast('.')
                    val base = nm.substringBeforeLast('.')
                    val dup = duplicate(base)
                    when {
                        ext == "wav" || ext == "flac" -> {
                            if (dup) skipped++
                            else { File(dir, "$base.$ext").outputStream().use { zip.copyTo(it) }; imported++ }
                        }
                        !dup && ext == "json" -> pendingMeta.add(base to zip.readBytes().toString(Charsets.UTF_8))
                        !dup -> File(dir, "$base.$ext").outputStream().use { zip.copyTo(it) }
                    }
                }
                zip.closeEntry()
                e = zip.nextEntry
            }
        }
        for ((b, json) in pendingMeta) restoreMeta(ctx, b, json)
        return ImportResult(imported, skipped)
    }

    /** Outcome of [importBundle]: how many recordings were added vs skipped as already-present. */
    data class ImportResult(val imported: Int, val skipped: Int)

    private fun restoreMeta(ctx: Context, base: String, jsonText: String) {
        val o = try { JSONObject(jsonText) } catch (_: Exception) { return }
        val ed = ctx.getSharedPreferences(prefsNameForFile("$base.wav"), Context.MODE_PRIVATE).edit()
        for (k in o.keys()) {
            when {
                k in BOOL_KEYS -> ed.putBoolean(k, o.optBoolean(k))
                k in STRING_KEYS -> ed.putString(k, o.optString(k))
                k in FLOAT_KEYS -> ed.putFloat(k, o.optDouble(k).toFloat())
                else -> ed.putInt(k, o.optInt(k))
            }
        }
        ed.apply()
    }

    // ---- Network helpers ------------------------------------------------------------------------

    /** First site-local IPv4 address (the Wi-Fi LAN address), or null if not on a network. */
    fun localIpv4(): String? {
        try {
            for (intf in NetworkInterface.getNetworkInterfaces()) {
                if (!intf.isUp || intf.isLoopback) continue
                for (addr in intf.inetAddresses) {
                    if (!addr.isLoopbackAddress && addr is Inet4Address && addr.isSiteLocalAddress) {
                        return addr.hostAddress
                    }
                }
            }
        } catch (_: Exception) {
        }
        return null
    }

    /** Read a single '\n'-terminated line directly off the raw stream (so the rest stays intact
     *  for the ZIP reader — a BufferedReader would swallow bytes). */
    fun readLine(ins: InputStream): String {
        val sb = StringBuilder()
        while (true) {
            val b = ins.read()
            if (b < 0 || b == '\n'.code) break
            sb.append(b.toChar())
        }
        return sb.toString()
    }
}
