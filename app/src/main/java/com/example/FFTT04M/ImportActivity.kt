package com.example.FFTT04M

import android.graphics.Color
import android.os.Bundle
import android.view.Gravity
import android.view.ViewGroup
import android.widget.ImageView
import android.widget.LinearLayout
import android.widget.TextView
import android.widget.Toast
import androidx.appcompat.app.AppCompatActivity
import com.google.zxing.BarcodeFormat
import com.journeyapps.barcodescanner.BarcodeEncoder
import java.net.ServerSocket
import java.util.UUID

/**
 * Receiver side of device-to-device gallery transfer. Opens a TCP server on the Wi-Fi LAN and
 * shows a QR code (FFTT1:ip:port:token). The sender scans it, connects, and streams the bundle.
 */
class ImportActivity : AppCompatActivity() {

    private var server: ServerSocket? = null
    @Volatile private var done = false

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        title = "Receive gallery"

        val density = resources.displayMetrics.density
        val pad = (24 * density).toInt()
        val status = TextView(this).apply {
            setTextColor(Color.WHITE)
            textSize = 16f
            gravity = Gravity.CENTER
            setPadding(0, 0, 0, pad)
        }
        val qr = ImageView(this)
        val root = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            gravity = Gravity.CENTER
            setBackgroundColor(Color.BLACK)
            setPadding(pad, pad, pad, pad)
            addView(status)
            addView(qr, LinearLayout.LayoutParams(
                (260 * density).toInt(), (260 * density).toInt()))
        }
        setContentView(root)

        val ip = GalleryTransfer.localIpv4()
        if (ip == null) {
            Toast.makeText(this, "Connect both devices to the same Wi-Fi first", Toast.LENGTH_LONG).show()
            finish()
            return
        }

        val token = UUID.randomUUID().toString().take(8)
        val srv = ServerSocket(0)
        server = srv
        val payload = "${GalleryTransfer.HANDSHAKE_PREFIX}:$ip:${srv.localPort}:$token"

        val size = (260 * density).toInt().coerceAtLeast(256)
        try {
            qr.setImageBitmap(BarcodeEncoder().encodeBitmap(payload, BarcodeFormat.QR_CODE, size, size))
        } catch (_: Exception) {
        }
        status.text = "On the sending device:\nGallery → SHARE → Send, then scan this code.\n\n$ip"

        Thread {
            try {
                srv.use { s ->
                    val sock = s.accept()
                    sock.use {
                        val ins = it.getInputStream()
                        val line = GalleryTransfer.readLine(ins)
                        if (line != token) {
                            runOnUiThread { Toast.makeText(this, "Handshake mismatch", Toast.LENGTH_LONG).show() }
                            return@use
                        }
                        val count = GalleryTransfer.importBundle(this, ins)
                        done = true
                        runOnUiThread {
                            Toast.makeText(this, "Imported $count recording(s)", Toast.LENGTH_LONG).show()
                            finish()
                        }
                    }
                }
            } catch (e: Exception) {
                if (!done && !isFinishing) runOnUiThread {
                    Toast.makeText(this, "Receive cancelled/failed: ${e.message}", Toast.LENGTH_SHORT).show()
                }
            }
        }.start()
    }

    override fun onDestroy() {
        super.onDestroy()
        try { server?.close() } catch (_: Exception) {}   // unblocks accept()
    }
}
