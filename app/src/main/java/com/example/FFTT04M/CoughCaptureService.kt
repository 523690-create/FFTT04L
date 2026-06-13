package com.example.FFTT04M

import android.app.Notification
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.app.Service
import android.content.Context
import android.content.Intent
import android.content.pm.ServiceInfo
import android.media.AudioFormat
import android.media.AudioRecord
import android.media.MediaRecorder
import android.os.Build
import android.os.IBinder
import android.os.PowerManager
import androidx.core.app.NotificationCompat
import androidx.core.app.NotificationManagerCompat
import androidx.core.content.ContextCompat
import com.example.FFTT04M.cough.CoughClassifier
import com.example.FFTT04M.cough.CoughDetector
import com.example.FFTT04M.cough.CoughWav
import java.io.File
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale
import kotlin.concurrent.thread

/**
 * Foreground MICROPHONE service: hands-free cough auto-capture that keeps running in the background
 * and behind the lock screen, saving each detected cough as a Gallery WAV. Designed for dedicated /
 * legacy capture devices (e.g. the J7) left listening. A persistent "Listening…" notification with a
 * Stop action is mandatory — Android only allows background mic access from a foreground service.
 *
 * Legacy-safe: any overload in detection stops capture rather than crashing the service. A partial
 * wake lock keeps the CPU alive with the screen off.
 */
class CoughCaptureService : Service() {

    private val sampleRate = 44100
    @Volatile private var capturing = false
    private var captureThread: Thread? = null
    private var wakeLock: PowerManager.WakeLock? = null
    @Volatile private var count = 0

    override fun onBind(intent: Intent?): IBinder? = null

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        if (intent?.action == ACTION_STOP) { stopSelf(); return START_NOT_STICKY }
        if (capturing) return START_STICKY
        createChannel()
        startForegroundCompat("Listening for coughs… (0 saved)")
        acquireWakeLock()
        capturing = true; running = true
        captureThread = thread(name = "cough-capture-svc") { captureLoop() }
        return START_STICKY   // keep listening across transient kills
    }

    private fun captureLoop() {
        val minBuf = AudioRecord.getMinBufferSize(
            sampleRate, AudioFormat.CHANNEL_IN_MONO, AudioFormat.ENCODING_PCM_16BIT
        ).coerceAtLeast(sampleRate / 4)
        val recorder = try {
            AudioRecord(MediaRecorder.AudioSource.MIC, sampleRate,
                AudioFormat.CHANNEL_IN_MONO, AudioFormat.ENCODING_PCM_16BIT, minBuf * 2)
        } catch (t: Throwable) { stopSelf(); return }
        if (recorder.state != AudioRecord.STATE_INITIALIZED) { recorder.release(); stopSelf(); return }

        // Stopgap specificity (same as the Listen screen) until the forest is retrained on movie negatives.
        CoughClassifier.thresholdOverride = 0.65
        val detector = CoughDetector(sampleRate) { onCough(it) }
        val shortBuf = ShortArray(minBuf); val floatBuf = FloatArray(minBuf)
        try {
            recorder.startRecording()
            while (capturing) {
                val n = recorder.read(shortBuf, 0, shortBuf.size)
                if (n <= 0) continue
                for (i in 0 until n) floatBuf[i] = shortBuf[i] / 32768f
                try { detector.process(if (n == floatBuf.size) floatBuf else floatBuf.copyOf(n)) }
                catch (t: Throwable) {                  // overload on a weak device → stop, don't crash
                    updateNotification("Auto-capture stopped (device overloaded) · $count saved"); break
                }
            }
            detector.finish()
        } catch (t: Throwable) {
            // mic error mid-stream — fall through to cleanup
        } finally {
            try { recorder.stop() } catch (_: Throwable) {}
            recorder.release()
        }
    }

    private fun onCough(c: CoughDetector.CapturedCough) {
        val now = Date()
        val ts = SimpleDateFormat("yyyyMMdd_HHmmss_SSS", Locale.US).format(now)
        val dir = GalleryTransfer.recordingsDir(this) ?: filesDir
        runCatching { CoughWav.write(File(dir, "cough_$ts.wav"), c.pcm, sampleRate) }
        // Live-refresh an open Gallery (reuses the import service's broadcast that triggers loadFiles()).
        runCatching { sendBroadcast(Intent(TransferService.ACTION_PROGRESS).setPackage(packageName)) }
        count++
        val seconds = c.pcm.size / sampleRate.toFloat()
        val clock = SimpleDateFormat("HH:mm:ss", Locale.US).format(now)
        pushCaptureAlert(String.format(Locale.US, "FFT %.1f seconds captured at %s", seconds, clock))
        updateNotification("Listening for coughs… ($count saved)")
    }

    override fun onDestroy() {
        capturing = false; running = false
        try { captureThread?.join(800) } catch (_: Throwable) {}
        releaseWakeLock()
        super.onDestroy()
    }

    // ---- foreground notification --------------------------------------------------------------
    private fun createChannel() {
        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.O) return
        val mgr = getSystemService(NotificationManager::class.java)
        mgr.createNotificationChannel(
            NotificationChannel(CH, "Cough auto-capture", NotificationManager.IMPORTANCE_LOW))
        // Separate HIGH channel so each capture pings/peeks (the ongoing "Listening…" channel is silent/LOW).
        mgr.createNotificationChannel(
            NotificationChannel(CH_ALERT, "Cough captured", NotificationManager.IMPORTANCE_HIGH).apply { enableVibration(true) })
    }

    private fun openGallery() = PendingIntent.getActivity(this, 0,
        Intent(this, GalleryActivity::class.java).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TOP),
        PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE)

    /** Heads-up ping on every capture: "FFT 1.4 seconds captured at 14:23:07". */
    private fun pushCaptureAlert(text: String) {
        android.util.Log.i("CoughCapture", "alert: $text")
        val n = NotificationCompat.Builder(this, CH_ALERT)
            .setSmallIcon(android.R.drawable.stat_sys_upload_done)
            .setContentTitle("Cough captured").setContentText(text)
            .setPriority(NotificationCompat.PRIORITY_HIGH)        // peek (heads-up) on pre-O too
            .setVibrate(longArrayOf(0, 60))                       // brief buzz so the ping is noticed
            .setOnlyAlertOnce(false).setAutoCancel(true)
            .setContentIntent(openGallery())
            .build()
        try { NotificationManagerCompat.from(this).notify(CAPTURE_NID, n) } catch (_: SecurityException) {}
    }

    private fun buildNotification(text: String): Notification {
        val stop = PendingIntent.getService(this, 1,
            Intent(this, CoughCaptureService::class.java).setAction(ACTION_STOP),
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE)
        val open = PendingIntent.getActivity(this, 0,
            Intent(this, MainActivity::class.java).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TOP),
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE)
        return NotificationCompat.Builder(this, CH)
            .setSmallIcon(android.R.drawable.ic_btn_speak_now)
            .setContentTitle("FFTT cough capture").setContentText(text)
            .setOngoing(true).setOnlyAlertOnce(true).setContentIntent(open)
            .addAction(android.R.drawable.ic_menu_close_clear_cancel, "Stop", stop)
            .build()
    }

    private fun startForegroundCompat(text: String) {
        val n = buildNotification(text)
        try {
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q)
                startForeground(NID, n, ServiceInfo.FOREGROUND_SERVICE_TYPE_MICROPHONE)
            else startForeground(NID, n)
        } catch (e: Throwable) { try { startForeground(NID, n) } catch (_: Throwable) {} }
    }

    private fun updateNotification(text: String) {
        try { NotificationManagerCompat.from(this).notify(NID, buildNotification(text)) } catch (_: SecurityException) {}
    }

    private fun acquireWakeLock() {
        try {
            val pm = getSystemService(Context.POWER_SERVICE) as PowerManager
            wakeLock = pm.newWakeLock(PowerManager.PARTIAL_WAKE_LOCK, "FFTT:coughCapture")
                .apply { setReferenceCounted(false); acquire() }
        } catch (_: Throwable) {}
    }
    private fun releaseWakeLock() { try { if (wakeLock?.isHeld == true) wakeLock?.release() } catch (_: Throwable) {} }

    companion object {
        @Volatile var running = false; private set
        private const val CH = "cough_capture"
        private const val CH_ALERT = "cough_capture_alert"
        private const val NID = 4201
        private const val CAPTURE_NID = 4202
        const val ACTION_STOP = "com.example.FFTT04M.STOP_COUGH_CAPTURE"
        fun start(ctx: Context) =
            ContextCompat.startForegroundService(ctx, Intent(ctx, CoughCaptureService::class.java))
        fun stop(ctx: Context) =
            ctx.startService(Intent(ctx, CoughCaptureService::class.java).setAction(ACTION_STOP))
    }
}
