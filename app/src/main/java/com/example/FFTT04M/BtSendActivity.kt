package com.example.FFTT04M

import android.Manifest
import android.bluetooth.BluetoothManager
import android.bluetooth.BluetoothServerSocket
import android.content.pm.PackageManager
import android.graphics.Color
import android.os.Build
import android.os.Bundle
import android.view.Gravity
import android.widget.ImageView
import android.widget.LinearLayout
import android.widget.TextView
import android.widget.Toast
import androidx.activity.result.contract.ActivityResultContracts
import androidx.appcompat.app.AppCompatActivity
import androidx.core.content.ContextCompat
import com.google.zxing.BarcodeFormat
import com.journeyapps.barcodescanner.BarcodeEncoder
import java.util.UUID
import kotlin.concurrent.thread

/**
 * Bluetooth donor: opens an RFCOMM server and shows a QR (FFTTBT:token:btName). The receiver scans
 * it, connects to this (already-paired) device by name, negotiates which recordings it's missing,
 * and we stream only those. No Wi-Fi / network needed — sidesteps AP isolation.
 */
class BtSendActivity : AppCompatActivity() {

    private var server: BluetoothServerSocket? = null
    @Volatile private var done = false
    private lateinit var status: TextView
    private lateinit var qr: ImageView

    private val permLauncher = registerForActivityResult(ActivityResultContracts.RequestPermission()) { granted ->
        if (granted) startServer() else { toast("Bluetooth permission is needed to send"); finish() }
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        title = "Send via Bluetooth"
        val d = resources.displayMetrics.density
        val pad = (24 * d).toInt()
        status = TextView(this).apply {
            setTextColor(Color.WHITE); textSize = 16f; gravity = Gravity.CENTER; setPadding(0, 0, 0, pad)
        }
        qr = ImageView(this)
        setContentView(LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL; gravity = Gravity.CENTER
            setBackgroundColor(Color.BLACK); setPadding(pad, pad, pad, pad)
            addView(status)
            addView(qr, LinearLayout.LayoutParams((260 * d).toInt(), (260 * d).toInt()))
        })

        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S &&
            ContextCompat.checkSelfPermission(this, Manifest.permission.BLUETOOTH_CONNECT) != PackageManager.PERMISSION_GRANTED) {
            permLauncher.launch(Manifest.permission.BLUETOOTH_CONNECT)
        } else {
            startServer()
        }
    }

    private fun startServer() {
        val adapter = (getSystemService(BluetoothManager::class.java))?.adapter
        if (adapter == null) { toast("No Bluetooth on this device"); finish(); return }
        if (!adapter.isEnabled) { toast("Turn Bluetooth on, then try again"); finish(); return }
        val wavs = GalleryTransfer.recordingWavs(this)
        if (wavs.isEmpty()) { toast("No recordings to send"); finish(); return }

        val token = UUID.randomUUID().toString().take(8)
        val btName = try { adapter.name ?: "this device" } catch (e: SecurityException) { "this device" }

        val srv = try {
            adapter.listenUsingRfcommWithServiceRecord("FFTTShare", GalleryTransfer.BT_UUID)
        } catch (e: Throwable) {
            toast("Bluetooth error: ${e.message}"); finish(); return
        }
        server = srv

        val payload = "${GalleryTransfer.BT_PREFIX}:$token:$btName"
        try {
            val sz = (260 * resources.displayMetrics.density).toInt().coerceAtLeast(256)
            qr.setImageBitmap(BarcodeEncoder().encodeBitmap(payload, BarcodeFormat.QR_CODE, sz, sz))
        } catch (_: Throwable) {}
        status.text = "On the receiving device:\nGallery → SHARE → Receive (scan QR), then scan this code.\n\n" +
            "Bluetooth name: $btName\n(The two devices must already be paired in Bluetooth settings.)"

        android.util.Log.i("FFTTxfer", "BT serving as '$btName' token=$token, waiting for connection")
        thread {
            try {
                srv.accept().use { sock ->
                    android.util.Log.i("FFTTxfer", "BT accepted connection")
                    val sent = GalleryTransfer.sendNegotiated(this, sock.inputStream, sock.outputStream, token)
                    android.util.Log.i("FFTTxfer", "BT sendNegotiated returned $sent")
                    done = true
                    runOnUiThread {
                        toast(if (sent < 0) "Handshake mismatch" else "Sent $sent recording(s)")
                        finish()
                    }
                }
            } catch (e: Throwable) {
                if (!done && !isFinishing) runOnUiThread { toast("Send cancelled/failed: ${e.message}") }
            }
        }
    }

    private fun toast(m: String) = Toast.makeText(this, m, Toast.LENGTH_LONG).show()

    override fun onDestroy() {
        super.onDestroy()
        try { server?.close() } catch (_: Throwable) {}
    }
}
