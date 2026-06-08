package com.example.FFTT04M

import android.content.Intent
import android.graphics.BitmapFactory
import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.Button
import android.widget.ImageButton
import android.widget.ImageView
import android.widget.LinearLayout
import android.widget.TextView
import android.widget.Toast
import androidx.appcompat.app.AlertDialog
import androidx.appcompat.app.AppCompatActivity
import com.journeyapps.barcodescanner.ScanContract
import com.journeyapps.barcodescanner.ScanOptions
import java.net.Socket
import kotlin.concurrent.thread
import androidx.recyclerview.widget.GridLayoutManager
import androidx.recyclerview.widget.LinearLayoutManager
import androidx.recyclerview.widget.RecyclerView
import java.io.File

class GalleryActivity : AppCompatActivity() {

    private lateinit var recyclerView: RecyclerView
    private lateinit var btnViewToggle: ImageButton
    private var isGridView = false
    private var files = mutableListOf<File>()

    // Scanner result (receiver side): scanned the donor's QR -> connect and pull the bundle.
    private val scanLauncher = registerForActivityResult(ScanContract()) { result ->
        result.contents?.let { receiveBundleFrom(it) }
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_gallery)

        val prefs = getSharedPreferences("app_settings", MODE_PRIVATE)
        isGridView = prefs.getBoolean("gallery_is_grid", false)

        recyclerView = findViewById(R.id.recyclerView)
        btnViewToggle = findViewById(R.id.btnViewToggle)

        updateLayoutManager()

        loadFiles()

        btnViewToggle.setOnClickListener {
            isGridView = !isGridView
            prefs.edit().putBoolean("gallery_is_grid", isGridView).apply()
            updateLayoutManager()
            recyclerView.adapter?.notifyDataSetChanged()
        }

        findViewById<Button>(R.id.btnBack).setOnClickListener {
            finish()
        }
        findViewById<Button>(R.id.btnManual).setOnClickListener {
            startActivity(android.content.Intent(this, ManualActivity::class.java))
        }
        findViewById<Button>(R.id.btnShare).setOnClickListener { showShareDialog() }
    }

    /** SHARE → Send (show this device's QR; it streams its gallery) or Receive (scan the donor's QR). */
    private fun showShareDialog() {
        AlertDialog.Builder(this)
            .setTitle("Share gallery")
            .setItems(arrayOf("Send to another device", "Receive onto this device")) { _, which ->
                if (which == 0) {
                    if (GalleryTransfer.recordingWavs(this).isEmpty()) {
                        Toast.makeText(this, "No recordings to send", Toast.LENGTH_SHORT).show()
                        return@setItems
                    }
                    startActivity(Intent(this, ExportActivity::class.java))   // shows QR, streams on connect
                } else {
                    scanLauncher.launch(ScanOptions().apply {
                        setDesiredBarcodeFormats(ScanOptions.QR_CODE)
                        setPrompt("Scan the QR shown on the SENDING device")
                        setBeepEnabled(false)
                        setOrientationLocked(false)
                    })
                }
            }
            .setNegativeButton("Cancel", null)
            .show()
    }

    /** Receiver: parse the donor's handshake (FFTT1:ip:port:token), connect, send the token, and
     *  pull the gallery bundle. */
    private fun receiveBundleFrom(handshake: String) {
        val parts = handshake.split(":")
        if (parts.size != 4 || parts[0] != GalleryTransfer.HANDSHAKE_PREFIX) {
            Toast.makeText(this, "Not an FFTT transfer code", Toast.LENGTH_SHORT).show()
            return
        }
        val ip = parts[1]
        val port = parts[2].toIntOrNull() ?: return
        val token = parts[3]
        Toast.makeText(this, "Receiving…", Toast.LENGTH_SHORT).show()
        thread {
            try {
                Socket(ip, port).use { sock ->
                    sock.getOutputStream().apply { write("$token\n".toByteArray()); flush() }
                    val count = GalleryTransfer.importBundle(this, sock.getInputStream())
                    runOnUiThread {
                        Toast.makeText(this, "Imported $count recording(s)", Toast.LENGTH_LONG).show()
                        loadFiles()
                    }
                }
            } catch (e: Throwable) {
                runOnUiThread { Toast.makeText(this, "Receive failed: ${e.message}", Toast.LENGTH_LONG).show() }
            }
        }
    }

    override fun onResume() {
        super.onResume()
        // A comment edit in the Viewer can change the sidecar .txt and the .png thumbnail; reload so
        // those changes (and any newly recorded files) show without leaving the gallery.
        loadFiles()
    }

    private fun loadFiles() {
        val filesDir = getExternalFilesDir(null)
        // Match audio recordings: WAV is the current format; FLAC is kept for backward compat
        // with files saved by older builds.
        files = filesDir?.listFiles { file -> file.extension == "wav" || file.extension == "flac" }
            ?.sortedByDescending { it.lastModified() }
            ?.toMutableList() ?: mutableListOf()
        recyclerView.adapter = GalleryAdapter(files)
    }

    private fun updateLayoutManager() {
        val isLandscape = resources.configuration.orientation == android.content.res.Configuration.ORIENTATION_LANDSCAPE
        if (isGridView || isLandscape) {
            recyclerView.layoutManager = GridLayoutManager(this, 3)
            btnViewToggle.setImageResource(R.drawable.ic_view_list)
        } else {
            recyclerView.layoutManager = LinearLayoutManager(this)
            btnViewToggle.setImageResource(R.drawable.ic_view_module)
        }
    }

    private fun showDeleteDialog(file: File, position: Int) {
        AlertDialog.Builder(this)
            .setTitle("DELETE?")
            .setMessage("Remove ${file.name}?")
            .setPositiveButton("DELETE") { _, _ ->
                val iconFile = File(file.parent, file.nameWithoutExtension + ".png")
                val commentFile = File(file.parent, file.nameWithoutExtension + ".txt")
                if (file.exists()) file.delete()
                if (iconFile.exists()) iconFile.delete()
                if (commentFile.exists()) commentFile.delete()
                files.removeAt(position)
                recyclerView.adapter?.notifyItemRemoved(position)
            }
            .setNegativeButton("CANCEL", null)
            .show()
    }

    inner class GalleryAdapter(private val files: List<File>) : RecyclerView.Adapter<GalleryAdapter.ViewHolder>() {

        override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): ViewHolder {
            val view = LayoutInflater.from(parent.context).inflate(R.layout.gallery_item, parent, false)
            applyAutoSizeText(view)
            return ViewHolder(view)
        }

        override fun onBindViewHolder(holder: ViewHolder, position: Int) {
            val file = files[position]
            val isLandscape = holder.itemView.resources.configuration.orientation == android.content.res.Configuration.ORIENTATION_LANDSCAPE
            
            val boxParams = holder.textBox.layoutParams as LinearLayout.LayoutParams
            if (isGridView || isLandscape) {
                holder.root.orientation = LinearLayout.VERTICAL
                boxParams.width = ViewGroup.LayoutParams.MATCH_PARENT
                boxParams.weight = 0f
                boxParams.marginStart = 0
                holder.textBox.setPadding(0, 4, 0, 0)
                holder.textView.gravity = android.view.Gravity.CENTER
                holder.textView.maxLines = 1
                holder.textView.ellipsize = android.text.TextUtils.TruncateAt.END
                holder.commentView.gravity = android.view.Gravity.CENTER
                holder.commentView.maxLines = 1
                holder.commentView.ellipsize = android.text.TextUtils.TruncateAt.END
            } else {
                holder.root.orientation = LinearLayout.HORIZONTAL
                boxParams.width = 0
                boxParams.weight = 1f
                boxParams.marginStart = 8
                holder.textBox.setPadding(0, 0, 0, 0)
                holder.textView.gravity = android.view.Gravity.START
                holder.textView.maxLines = Int.MAX_VALUE
                holder.commentView.gravity = android.view.Gravity.START
                holder.commentView.maxLines = 3
                holder.commentView.ellipsize = android.text.TextUtils.TruncateAt.END
            }

            holder.textView.text = file.name

            val commentFile = File(file.parent, file.nameWithoutExtension + ".txt")
            val comment = if (commentFile.exists()) commentFile.readText().trim() else ""
            if (comment.isEmpty()) {
                holder.commentView.visibility = View.GONE
            } else {
                holder.commentView.visibility = View.VISIBLE
                holder.commentView.text = comment
            }

            val iconFile = File(file.parent, file.nameWithoutExtension + ".png")
            if (iconFile.exists()) {
                val bitmap = BitmapFactory.decodeFile(iconFile.absolutePath)
                holder.imageView.setImageBitmap(bitmap)
            } else {
                holder.imageView.setImageResource(android.R.drawable.ic_menu_report_image)
            }

            holder.itemView.setOnClickListener {
                val intent = Intent(this@GalleryActivity, ViewerActivity::class.java).apply {
                    putExtra("FILE_PATH", file.absolutePath)
                }
                startActivity(intent)
            }

            holder.itemView.setOnLongClickListener {
                showDeleteDialog(file, position)
                true
            }
        }

        override fun getItemCount(): Int = files.size

        inner class ViewHolder(view: View) : RecyclerView.ViewHolder(view) {
            val root: LinearLayout = view.findViewById(R.id.galleryItemRoot)
            val imageView: ImageView = view.findViewById(R.id.itemIcon)
            val textBox: LinearLayout = view.findViewById(R.id.itemTextBox)
            val textView: TextView = view.findViewById(R.id.itemText)
            val commentView: TextView = view.findViewById(R.id.itemComment)
        }
    }
}
