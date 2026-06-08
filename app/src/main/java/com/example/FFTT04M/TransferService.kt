package com.example.FFTT04M

import android.app.Notification
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.Service
import android.bluetooth.BluetoothManager
import android.content.Context
import android.content.Intent
import android.content.pm.ServiceInfo
import android.os.Build
import android.os.IBinder
import androidx.core.app.NotificationCompat
import java.net.InetSocketAddress
import java.net.Socket
import kotlin.concurrent.thread

/**
 * Receives a gallery transfer in the background (foreground service) so the user can leave the
 * Gallery while it runs. Posts an ongoing progress notification plus a heads-up notification per
 * imported recording. Handles both transports:
 *   transport=bt   → connect to a bonded device by MAC, RFCOMM
 *   transport=wifi → connect to ip:port (TCP)
 */
class TransferService : Service() {

    companion object {
        const val EX_TRANSPORT = "transport"   // "bt" | "wifi"
        const val EX_TOKEN = "token"
        const val EX_BT_ADDR = "btAddr"
        const val EX_IP = "ip"
        const val EX_PORT = "port"
        private const val CH_PROGRESS = "fftt_transfer_progress"
        private const val CH_FILES = "fftt_transfer_files"
        private const val NID_PROGRESS = 4100
        private const val NID_FILE_BASE = 4200
        private const val TAG = "FFTTxfer"
    }

    override fun onBind(intent: Intent?): IBinder? = null

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        createChannels()
        val transport = intent?.getStringExtra(EX_TRANSPORT) ?: run { stopSelf(); return START_NOT_STICKY }
        android.util.Log.i(TAG, "onStartCommand transport=$transport")
        // Try to run in the foreground; if the OS rejects the FGS (type/permission rules on newer
        // Android), don't die — log it and still run the transfer on a plain thread.
        try {
            startForegroundCompat(transport, "Receiving gallery…")
        } catch (e: Throwable) {
            android.util.Log.e(TAG, "startForeground failed; running without FGS", e)
        }
        thread {
            try {
                runReceive(intent, transport)
            } catch (e: Throwable) {
                android.util.Log.e(TAG, "receive failed", e)
                notify(NID_PROGRESS, progress("Transfer failed", e.message ?: e.javaClass.simpleName, ongoing = false))
            } finally {
                stopForegroundCompat()
                stopSelf()
            }
        }
        return START_NOT_STICKY
    }

    private fun runReceive(intent: Intent, transport: String) {
        val token = intent.getStringExtra(EX_TOKEN) ?: return
        var fileCount = 0
        val onImported: (Int, String) -> Unit = { n, name ->
            fileCount = n
            notify(NID_FILE_BASE + (n % 50), file("Imported recording", prettyName(name)))
            notify(NID_PROGRESS, progress("Receiving gallery…", "$n imported so far", ongoing = true))
        }

        val res: GalleryTransfer.ImportResult = when (transport) {
            "bt" -> {
                val addr = intent.getStringExtra(EX_BT_ADDR) ?: return
                val adapter = (getSystemService(BluetoothManager::class.java))?.adapter ?: return
                // Best-effort only; needs BLUETOOTH_SCAN on API 31+ which we don't hold (we use
                // bonded devices, never discovery). Swallow so it can't kill the transfer.
                runCatching { adapter.cancelDiscovery() }
                android.util.Log.i(TAG, "bt connecting to $addr")
                adapter.getRemoteDevice(addr).createRfcommSocketToServiceRecord(GalleryTransfer.BT_UUID).use { sock ->
                    sock.connect()
                    android.util.Log.i(TAG, "bt connected")
                    GalleryTransfer.receiveNegotiated(this, sock.inputStream, sock.outputStream, token, onImported)
                }
            }
            else -> {
                val ip = intent.getStringExtra(EX_IP) ?: return
                val port = intent.getIntExtra(EX_PORT, -1)
                android.util.Log.i(TAG, "wifi connecting to $ip:$port")
                Socket().use { sock ->
                    sock.connect(InetSocketAddress(ip, port), 8000)
                    android.util.Log.i(TAG, "wifi connected")
                    GalleryTransfer.receiveNegotiated(this, sock.inputStream, sock.outputStream, token, onImported)
                }
            }
        }
        android.util.Log.i(TAG, "done imported=${res.imported} skipped=${res.skipped}")

        val summary = buildString {
            append("Imported ${res.imported}")
            if (res.skipped > 0) append(", skipped ${res.skipped} already present")
        }
        notify(NID_PROGRESS, progress("Gallery received", summary, ongoing = false))
    }

    // ---- notifications --------------------------------------------------------------------------

    private fun createChannels() {
        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.O) return
        val mgr = getSystemService(NotificationManager::class.java)
        mgr.createNotificationChannel(NotificationChannel(CH_PROGRESS, "Transfer progress", NotificationManager.IMPORTANCE_LOW))
        mgr.createNotificationChannel(NotificationChannel(CH_FILES, "Imported recordings", NotificationManager.IMPORTANCE_HIGH).apply {
            description = "Pops up as each recording arrives"
        })
    }

    private fun openGalleryIntent() = android.app.PendingIntent.getActivity(
        this, 0, Intent(this, GalleryActivity::class.java).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TOP),
        android.app.PendingIntent.FLAG_UPDATE_CURRENT or android.app.PendingIntent.FLAG_IMMUTABLE
    )

    private fun progress(title: String, text: String, ongoing: Boolean): Notification =
        NotificationCompat.Builder(this, CH_PROGRESS)
            .setSmallIcon(android.R.drawable.stat_sys_download)
            .setContentTitle(title).setContentText(text)
            .setOngoing(ongoing).setOnlyAlertOnce(true)
            .setContentIntent(openGalleryIntent())
            .build()

    private fun file(title: String, text: String): Notification =
        NotificationCompat.Builder(this, CH_FILES)
            .setSmallIcon(android.R.drawable.stat_sys_download_done)
            .setContentTitle(title).setContentText(text)
            .setPriority(NotificationCompat.PRIORITY_HIGH)
            .setAutoCancel(true)
            .setContentIntent(openGalleryIntent())
            .build()

    private fun notify(id: Int, n: Notification) {
        try { androidx.core.app.NotificationManagerCompat.from(this).notify(id, n) } catch (_: SecurityException) {}
    }

    private fun startForegroundCompat(transport: String, text: String) {
        val n = progress("Receiving gallery…", text, ongoing = true)
        when {
            // CONNECTED_DEVICE constant exists from API 30; DATA_SYNC from API 29.
            Build.VERSION.SDK_INT >= Build.VERSION_CODES.R -> startForeground(
                NID_PROGRESS, n,
                if (transport == "bt") ServiceInfo.FOREGROUND_SERVICE_TYPE_CONNECTED_DEVICE
                else ServiceInfo.FOREGROUND_SERVICE_TYPE_DATA_SYNC
            )
            Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q ->
                startForeground(NID_PROGRESS, n, ServiceInfo.FOREGROUND_SERVICE_TYPE_DATA_SYNC)
            else -> startForeground(NID_PROGRESS, n)
        }
    }

    @Suppress("DEPRECATION")
    private fun stopForegroundCompat() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.N) stopForeground(STOP_FOREGROUND_DETACH)
        else stopForeground(false)
    }

    private fun prettyName(base: String): String = base   // timestamp filename; shown as-is for now
}
