package com.example.FFTT04M

import android.Manifest
import android.bluetooth.BluetoothAdapter
import android.bluetooth.BluetoothDevice
import android.bluetooth.BluetoothManager
import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.content.IntentFilter
import android.content.pm.PackageManager
import android.os.Build
import android.os.Bundle
import android.os.Handler
import android.os.Looper
import android.view.Gravity
import android.view.View
import android.widget.ArrayAdapter
import android.widget.Button
import android.widget.LinearLayout
import android.widget.ListView
import android.widget.ProgressBar
import android.widget.TextView
import android.widget.Toast
import androidx.activity.result.contract.ActivityResultContracts
import androidx.appcompat.app.AlertDialog
import androidx.appcompat.app.AppCompatActivity
import androidx.core.content.ContextCompat

class BluetoothPairingActivity : AppCompatActivity() {

    private var adapter: BluetoothAdapter? = null
    private val discoveredDevices = mutableMapOf<String, BluetoothDevice>()
    private var isDiscovering = false
    private lateinit var listView: ListView
    private lateinit var statusText: TextView
    private lateinit var progressBar: ProgressBar

    private val mainHandler = Handler(Looper.getMainLooper())
    // Safety net: discovery should end in ~12s via ACTION_DISCOVERY_FINISHED, but on some devices that
    // broadcast never arrives — so we stop the spinner ourselves rather than spin forever.
    private val discoveryTimeout = Runnable { finishDiscovery(timedOut = true) }

    private val permLauncher = registerForActivityResult(ActivityResultContracts.RequestPermission()) { granted ->
        if (granted) startDiscovery() else {
            Toast.makeText(this, "Bluetooth permission is needed to discover devices", Toast.LENGTH_LONG).show()
            finish()
        }
    }

    private val discoveryReceiver = object : BroadcastReceiver() {
        override fun onReceive(context: Context, intent: Intent) {
            when (intent.action) {
                BluetoothDevice.ACTION_FOUND -> {
                    val device: BluetoothDevice? = if (Build.VERSION.SDK_INT >= 33)
                        intent.getParcelableExtra(BluetoothDevice.EXTRA_DEVICE, BluetoothDevice::class.java)
                    else @Suppress("DEPRECATION") intent.getParcelableExtra(BluetoothDevice.EXTRA_DEVICE)
                    device?.let {
                        val name = try { it.name ?: "Unknown (${it.address})" } catch (e: SecurityException) { "Unknown (${it.address})" }
                        discoveredDevices[it.address] = it
                        updateDeviceList()
                    }
                }
                BluetoothAdapter.ACTION_DISCOVERY_FINISHED -> finishDiscovery(timedOut = false)
                BluetoothDevice.ACTION_BOND_STATE_CHANGED -> {
                    val device: BluetoothDevice? = if (Build.VERSION.SDK_INT >= 33)
                        intent.getParcelableExtra(BluetoothDevice.EXTRA_DEVICE, BluetoothDevice::class.java)
                    else @Suppress("DEPRECATION") intent.getParcelableExtra(BluetoothDevice.EXTRA_DEVICE)
                    device?.let {
                        val state = intent.getIntExtra(BluetoothDevice.EXTRA_BOND_STATE, BluetoothDevice.BOND_NONE)
                        when (state) {
                            BluetoothDevice.BOND_BONDED -> {
                                val name = try { it.name ?: it.address } catch (_: SecurityException) { it.address }
                                Toast.makeText(this@BluetoothPairingActivity, "Successfully paired with $name", Toast.LENGTH_LONG).show()
                                updateDeviceList()
                            }
                            BluetoothDevice.BOND_BONDING -> {
                                statusText.text = "Pairing in progress…"
                            }
                            BluetoothDevice.BOND_NONE -> {
                                statusText.text = "Pairing cancelled or failed"
                                updateDeviceList()
                            }
                        }
                    }
                }
            }
        }
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        title = "Pair Bluetooth Device"

        val d = resources.displayMetrics.density
        val pad = (16 * d).toInt()

        statusText = TextView(this).apply {
            textSize = 14f
            gravity = Gravity.CENTER
            setPadding(pad, pad, pad, 0)
        }

        progressBar = ProgressBar(this).apply {
            visibility = View.GONE
        }

        listView = ListView(this).apply {
            layoutParams = LinearLayout.LayoutParams(LinearLayout.LayoutParams.MATCH_PARENT, 0, 1f)
        }

        val btnScan = Button(this).apply {
            text = "Scan for Devices"
            layoutParams = LinearLayout.LayoutParams(LinearLayout.LayoutParams.MATCH_PARENT, LinearLayout.LayoutParams.WRAP_CONTENT)
            setOnClickListener { requestScanPermissionAndStart() }
        }

        val btnCancel = Button(this).apply {
            text = "Cancel"
            layoutParams = LinearLayout.LayoutParams(LinearLayout.LayoutParams.MATCH_PARENT, LinearLayout.LayoutParams.WRAP_CONTENT)
            setOnClickListener { finish() }
        }

        val buttonLayout = LinearLayout(this).apply {
            orientation = LinearLayout.HORIZONTAL
            addView(btnScan, LinearLayout.LayoutParams(0, LinearLayout.LayoutParams.WRAP_CONTENT, 1f))
            val spacer = View(this@BluetoothPairingActivity)
            spacer.visibility = View.GONE
            addView(spacer, LinearLayout.LayoutParams((8*d).toInt(), 0))
            addView(btnCancel, LinearLayout.LayoutParams(0, LinearLayout.LayoutParams.WRAP_CONTENT, 1f))
            layoutParams = LinearLayout.LayoutParams(LinearLayout.LayoutParams.MATCH_PARENT, LinearLayout.LayoutParams.WRAP_CONTENT)
            setPadding(pad, pad, pad, pad)
        }

        setContentView(LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            addView(statusText, LinearLayout.LayoutParams(LinearLayout.LayoutParams.MATCH_PARENT, LinearLayout.LayoutParams.WRAP_CONTENT))
            addView(progressBar, LinearLayout.LayoutParams(LinearLayout.LayoutParams.MATCH_PARENT, (40*d).toInt()))
            addView(listView)
            addView(buttonLayout)
        })

        adapter = (getSystemService(BluetoothManager::class.java))?.adapter
        statusText.text = if (adapter == null) "No Bluetooth on this device" else "Requesting permission…"

        val filter = IntentFilter().apply {
            addAction(BluetoothDevice.ACTION_FOUND)
            addAction(BluetoothAdapter.ACTION_DISCOVERY_FINISHED)
            addAction(BluetoothDevice.ACTION_BOND_STATE_CHANGED)
        }
        // The 3-arg registerReceiver(receiver, filter, flags) is API 26+, and the flags arg is only
        // REQUIRED on API 33+. Calling it on API 23 (e.g. the J7) throws NoSuchMethodError and crashes
        // the scan, so fall back to the 2-arg form below 33.
        if (Build.VERSION.SDK_INT >= 33) {
            registerReceiver(discoveryReceiver, filter, Context.RECEIVER_EXPORTED)
        } else {
            @Suppress("UnspecifiedRegisterReceiverFlag")
            registerReceiver(discoveryReceiver, filter)
        }

        requestScanPermissionAndStart()
    }

    private fun requestScanPermissionAndStart() {
        // API 31+ gates discovery on BLUETOOTH_SCAN; below that it needs location to receive
        // ACTION_FOUND results at all (otherwise the scan silently finds 0 devices).
        val needed = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S)
            Manifest.permission.BLUETOOTH_SCAN
        else
            Manifest.permission.ACCESS_FINE_LOCATION
        if (ContextCompat.checkSelfPermission(this, needed) != PackageManager.PERMISSION_GRANTED) {
            permLauncher.launch(needed)
        } else {
            startDiscovery()
        }
    }

    private fun startDiscovery() {
        val a = adapter ?: return
        if (!a.isEnabled) {
            Toast.makeText(this, "Turn Bluetooth on, then try again", Toast.LENGTH_LONG).show()
            return
        }
        discoveredDevices.clear()
        updateDeviceList()
        // A scan already in progress makes startDiscovery() return false; cancel it first.
        try { if (a.isDiscovering) a.cancelDiscovery() } catch (_: SecurityException) {}

        val started = try {
            a.startDiscovery()
        } catch (e: SecurityException) {
            Toast.makeText(this, "Bluetooth error: ${e.message}", Toast.LENGTH_LONG).show()
            false
        }
        if (!started) {
            // No spinner-forever: tell the user why and bail. Pre-31 this is usually Location being off.
            isDiscovering = false
            progressBar.visibility = View.GONE
            statusText.text = if (Build.VERSION.SDK_INT < Build.VERSION_CODES.S)
                "Couldn't start scanning. Turn on Location (required for Bluetooth scan on this Android), then tap Scan."
            else
                "Couldn't start scanning. Make sure Bluetooth is on, then tap Scan."
            return
        }
        isDiscovering = true
        progressBar.visibility = View.VISIBLE
        statusText.text = "Scanning for Bluetooth devices…"
        mainHandler.removeCallbacks(discoveryTimeout)
        mainHandler.postDelayed(discoveryTimeout, 20_000)
    }

    /** End the scan exactly once — whether the system told us it finished or our timeout fired. */
    private fun finishDiscovery(timedOut: Boolean) {
        mainHandler.removeCallbacks(discoveryTimeout)
        if (!isDiscovering && !timedOut) return
        isDiscovering = false
        if (timedOut) try { adapter?.cancelDiscovery() } catch (_: SecurityException) {}
        progressBar.visibility = View.GONE
        statusText.text = if (discoveredDevices.isEmpty())
            "No discoverable devices found.\nOpen the Bluetooth settings screen on the OTHER device so it's discoverable, then tap Scan."
        else
            "Found ${discoveredDevices.size} device(s). Tap one to pair."
    }

    private fun updateDeviceList() {
        val items = discoveredDevices.map { (addr, dev) ->
            val name = try { dev.name ?: "Unknown" } catch (_: SecurityException) { "Unknown" }
            val bonded = try { dev.bondState == BluetoothDevice.BOND_BONDED } catch (_: SecurityException) { false }
            val suffix = if (bonded) " ✓ Paired" else ""
            "$name$suffix\n$addr"
        }
        val adapter = ArrayAdapter(this, android.R.layout.simple_list_item_1, items)
        listView.adapter = adapter
        listView.setOnItemClickListener { _, _, position, _ ->
            val addr = discoveredDevices.keys.toList()[position]
            val device = discoveredDevices[addr] ?: return@setOnItemClickListener
            val name = try { device.name ?: device.address } catch (_: SecurityException) { device.address }
            showPairingDialog(device, name)
        }
    }

    private fun showPairingDialog(device: BluetoothDevice, name: String) {
        val isBonded = try { device.bondState == BluetoothDevice.BOND_BONDED } catch (_: SecurityException) { false }
        val message = if (isBonded) "Already paired with $name. Proceed?" else "Pair with $name?"
        AlertDialog.Builder(this)
            .setTitle("Pair Device")
            .setMessage(message)
            .setPositiveButton("Pair") { _, _ -> pairDevice(device, name) }
            .setNegativeButton("Cancel", null)
            .show()
    }

    private fun pairDevice(device: BluetoothDevice, name: String) {
        try {
            statusText.text = "Pairing with $name…"
            device.createBond()
        } catch (e: SecurityException) {
            Toast.makeText(this, "Pairing error: ${e.message}", Toast.LENGTH_LONG).show()
        }
    }

    override fun onDestroy() {
        super.onDestroy()
        mainHandler.removeCallbacks(discoveryTimeout)
        try { unregisterReceiver(discoveryReceiver) } catch (_: Throwable) {}
        val a = adapter ?: return
        try { if (isDiscovering) a.cancelDiscovery() } catch (_: SecurityException) {}
    }
}
