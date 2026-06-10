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
                BluetoothAdapter.ACTION_DISCOVERY_FINISHED -> {
                    isDiscovering = false
                    progressBar.visibility = View.GONE
                    statusText.text = "Discovery complete. Found ${discoveredDevices.size} device(s)."
                }
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
        statusText.text = if (adapter == null) "No Bluetooth on this device" else "Ready to scan"

        registerReceiver(discoveryReceiver, IntentFilter().apply {
            addAction(BluetoothDevice.ACTION_FOUND)
            addAction(BluetoothAdapter.ACTION_DISCOVERY_FINISHED)
            addAction(BluetoothDevice.ACTION_BOND_STATE_CHANGED)
        }, if (Build.VERSION.SDK_INT >= 34) Context.RECEIVER_EXPORTED else 0)
    }

    private fun requestScanPermissionAndStart() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S &&
            ContextCompat.checkSelfPermission(this, Manifest.permission.BLUETOOTH_SCAN) != PackageManager.PERMISSION_GRANTED) {
            permLauncher.launch(Manifest.permission.BLUETOOTH_SCAN)
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
        isDiscovering = true
        progressBar.visibility = View.VISIBLE
        statusText.text = "Scanning for Bluetooth devices…"
        try {
            a.startDiscovery()
        } catch (e: SecurityException) {
            Toast.makeText(this, "Bluetooth error: ${e.message}", Toast.LENGTH_LONG).show()
        }
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
        try { unregisterReceiver(discoveryReceiver) } catch (_: Throwable) {}
        val a = adapter ?: return
        try { if (isDiscovering) a.cancelDiscovery() } catch (_: SecurityException) {}
    }
}
