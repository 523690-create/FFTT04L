package com.example.FFTT04M

import android.graphics.Color
import android.os.Bundle
import android.view.Gravity
import android.widget.ImageView
import android.widget.LinearLayout
import android.widget.TextView
import android.widget.Toast
import androidx.appcompat.app.AppCompatActivity
import com.google.zxing.BarcodeFormat
import com.journeyapps.barcodescanner.BarcodeEncoder
import java.net.ServerSocket
import java.util.UUID
import kotlin.concurrent.thread

/**
 * Donor side of device-to-device transfer. Shows a QR (FFTT1:ip:port:token) and runs a TCP server
 * on the Wi-Fi LAN; the receiving device scans the QR, connects, sends the token, and we stream the
 * gallery bundle to it. (QR on the device that HAS the data; camera on the empty one.)
 */
class ExportActivity : AppCompatActivity() {

    private var server: ServerSocket? = null
    @Volatile private var done = false

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        title = "Send gallery"

        val density = resources.displayMetrics.density
        val pad = (24 * density).toInt()
        val status = TextView(this).apply {
            setTextColor(Color.WHITE); textSize = 16f; gravity = Gravity.CENTER
            setPadding(0, 0, 0, pad)
        }
        val qr = ImageView(this)
        setContentView(LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            gravity = Gravity.CENTER
            setBackgroundColor(Color.BLACK)
            setPadding(pad, pad, pad, pad)
            addView(status)
            addView(qr, LinearLayout.LayoutParams((260 * density).toInt(), (260 * density).toInt()))
        })

        val wavs = GalleryTransfer.recordingWavs(this)
        if (wavs.isEmpty()) {
            Toast.makeText(this, "No recordings to send", Toast.LENGTH_LONG).show(); finish(); return
        }
        val ip = GalleryTransfer.localIpv4()
        if (ip == null) {
            Toast.makeText(this, "Connect this device to Wi-Fi first", Toast.LENGTH_LONG).show(); finish(); return
        }

        val token = UUID.randomUUID().toString().take(8)
        val srv = try { ServerSocket(0) } catch (e: Throwable) {
            Toast.makeText(this, "Couldn't open a port: ${e.message}", Toast.LENGTH_LONG).show(); finish(); return
        }
        server = srv
        val payload = "${GalleryTransfer.HANDSHAKE_PREFIX}:$ip:${srv.localPort}:$token"
        try {
            val sz = (260 * density).toInt().coerceAtLeast(256)
            qr.setImageBitmap(BarcodeEncoder().encodeBitmap(payload, BarcodeFormat.QR_CODE, sz, sz))
        } catch (_: Throwable) {}
        status.text = "On the receiving device:\nGallery → SHARE → Receive, then scan this code.\n\nSending ${wavs.size} recording(s) · $ip"

        thread {
            try {
                srv.use { s ->
                    s.accept().use { sock ->
                        val line = GalleryTransfer.readLine(sock.getInputStream())
                        if (line != token) {
                            runOnUiThread { Toast.makeText(this, "Handshake mismatch", Toast.LENGTH_LONG).show() }
                            return@use
                        }
                        GalleryTransfer.buildBundle(this, wavs, sock.getOutputStream())
                        done = true
                        runOnUiThread {
                            Toast.makeText(this, "Sent ${wavs.size} recording(s)", Toast.LENGTH_LONG).show()
                            finish()
                        }
                    }
                }
            } catch (e: Throwable) {
                if (!done && !isFinishing) runOnUiThread {
                    Toast.makeText(this, "Send cancelled/failed: ${e.message}", Toast.LENGTH_SHORT).show()
                }
            }
        }
    }

    override fun onDestroy() {
        super.onDestroy()
        try { server?.close() } catch (_: Throwable) {}   // unblocks accept()
    }
}
