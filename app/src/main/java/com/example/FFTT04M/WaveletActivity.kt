package com.example.FFTT04M

import android.content.Context
import android.content.Intent
import android.content.SharedPreferences
import android.graphics.Color
import android.media.MediaCodec
import android.media.MediaExtractor
import android.media.MediaFormat
import androidx.core.content.edit
import androidx.core.view.isGone
import android.os.Bundle
import android.view.View
import android.view.ViewTreeObserver
import android.widget.*
import androidx.appcompat.app.AppCompatActivity
import com.google.android.material.slider.Slider
import java.io.File
import java.util.*
import kotlin.math.*

class WaveletActivity : AppCompatActivity() {

    private lateinit var waveletView: WaveletView
    private lateinit var familySpinner: Spinner
    private lateinit var boundarySpinner: Spinner
    private lateinit var checkSoft: CheckBox
    private lateinit var checkLog: CheckBox
    private lateinit var checkLocalNorm: CheckBox
    private lateinit var modeSpinner: Spinner
    private lateinit var sliderOrder: Slider
    private lateinit var sliderLevel: Slider
    private lateinit var sliderSampling: Slider
    private lateinit var sliderThreshold: Slider
    private lateinit var btnColor: Button

    private lateinit var txtLevelValue: TextView
    private lateinit var txtOrderValue: TextView
    private lateinit var txtSamplingValue: TextView
    private lateinit var txtThresholdValue: TextView
    private lateinit var txtSafetyStatus: TextView

    private var filePath: String? = null
    private var pcmData: FloatArray? = null

    private var decompositionLevel = 8           // Max level (1-8) for finest detail
    private var targetFreq = 44100f              // Max safe frequency (44.1 kHz)
    private var originalSampleRate = 44100f
    private var threshold = 0f                   // Zero threshold = off
    private var selectedFamilyIdx = 0
    private var waveletOrder = 10                // Max order (2-10) for finest detail
    private var isSoftThreshold = true
    private var isLogScale = false
    private var isLocalNorm = false
    private var selectedBoundaryIdx = 0
    private var colorSchemeIdx = 0
    private var cwtWaveletIdx = 0                 // 0 = Morlet (default), 1 = Mexican Hat (Ricker)

    // Analysis mode is a single exclusive choice (was three independent checkboxes). The legacy
    // boolean flags below are derived from it via syncModeFlags(), so the downstream engine
    // dispatch (which still reads isCWT/isReconstruct/isWPT) is unchanged.
    private val modeNames = arrayOf("DWT", "WPT", "CWT", "RECON")
    private var analysisMode = 2                 // Default to CWT (0=DWT, 1=WPT, 2=CWT, 3=RECON)
    private var isWPT = false
    private var isReconstruct = false
    private var isCWT = false

    // Threshold is a 10-position log slider: index 0 = OFF, then log-spaced magnitudes.
    private val thresholdSteps = floatArrayOf(0f, 0.001f, 0.002f, 0.005f, 0.01f, 0.02f, 0.05f, 0.1f, 0.2f, 0.5f)

    @Volatile
    private var isStopRequested = false
    private var currentRequestId = 0

    private lateinit var prefs: SharedPreferences

    companion object {
        private const val MAX_CWT_SAMPLES = 60000L            // CWT buffer-size ceiling
        private const val MAX_DWT_SAMPLES = 300000L           // DWT buffer-size ceiling
        private const val MEM_BUDGET_BYTES = 40L * 1024 * 1024 // heap budget for coefficient buffers
        private const val MIN_SAMPLING_FREQ = 1000f           // matches sliderSampling valueFrom
        private const val MIN_LEVEL = 1                       // matches sliderLevel valueFrom
    }

    private val filterMap = mapOf(
        0 to mapOf( // Daubechies
            2 to floatArrayOf(0.4829629f, 0.8365163f, 0.22414387f, -0.12940952f),
            4 to floatArrayOf(0.23037782f, 0.71484655f, 0.6308807f, -0.02798377f, -0.18703482f, 0.03084138f, 0.03288301f, -0.010597401f),
            6 to floatArrayOf(0.11154074f, 0.4946239f, 0.7511339f, 0.31525035f, -0.2262647f, -0.12976687f, 0.097501605f, 0.027522866f, -0.03158204f, 0.0005538422f, 0.0047772575f, -0.10773011f)
        ),
        1 to mapOf( // Symlets
            2 to floatArrayOf(0.4829629131445341f, 0.8365163037378077f, 0.2241438680420134f, -0.1294095225512603f),
            4 to floatArrayOf(0.0322231006040713f, -0.0126039672622612f, -0.0992195435769354f, 0.2978577956055422f, 0.8037387518052163f, 0.4976186676324459f, -0.0296355276459541f, -0.0757657147893407f),
            6 to floatArrayOf(0.0154041093270273f, 0.0034907120842174f, -0.0679442279548073f, -0.001546137461876f, 0.2239077890625405f, 0.7661305174151252f, 0.5661984509385461f, -0.0456156055694133f, -0.1656939890451729f, 0.0381335880155719f, 0.022552550234519f, -0.0084333777038722f)
        ),
        2 to mapOf( // Coiflets
            1 to floatArrayOf(-0.0156557281354445f, -0.0727326195128538f, 0.3848648468642029f, 0.8525720202122554f, 0.3378976624578092f, -0.0713941471666038f),
            2 to floatArrayOf(0.0007205494453681f, 0.0098188151214044f, -0.0145672283288424f, -0.1459110111442116f, 0.446100069123405f, 0.862633294433604f, 0.2026431902046894f, -0.0783824357753173f, 0.0355152331018546f, 0.0117451274026362f, -0.0049137179673602f, -0.0029685472443134f)
        )
    )

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_wavelet)

        waveletView = findViewById(R.id.waveletView)
        familySpinner = findViewById(R.id.waveletFamilySpinner)
        boundarySpinner = findViewById(R.id.waveletBoundarySpinner)
        checkSoft = findViewById(R.id.checkSoftThresh)
        checkLog = findViewById(R.id.checkLogScale)
        checkLocalNorm = findViewById(R.id.checkLocalNorm)
        modeSpinner = findViewById(R.id.waveletModeSpinner)
        sliderOrder = findViewById(R.id.sliderOrder)
        sliderLevel = findViewById(R.id.sliderLevel)
        sliderSampling = findViewById(R.id.sliderSampling)
        sliderThreshold = findViewById(R.id.sliderThreshold)
        btnColor = findViewById(R.id.btnWaveletColor)

        txtLevelValue = findViewById(R.id.txtLevelValue)
        txtOrderValue = findViewById(R.id.txtOrderValue)
        txtSamplingValue = findViewById(R.id.txtSamplingValue)
        txtThresholdValue = findViewById(R.id.txtThresholdValue)
        txtSafetyStatus = findViewById(R.id.txtSafetyStatus)

        filePath = intent.getStringExtra("FILE_PATH")
        // Persist analysis/display settings per recording (shares the rec_<name> namespace with the
        // FFT Viewer, so e.g. the colour scheme stays consistent for a given recording).
        prefs = getSharedPreferences(prefsNameForFile(filePath), MODE_PRIVATE)
        loadPrefs()
        setupControls()
        setupWaveletTabs()
        applyAutoSizeText(findViewById(android.R.id.content))

        waveletView.post {
            updateAllLabelPositions()
            filePath?.let { loadAndDecode(File(it)) }
        }
    }

    override fun onResume() {
        super.onResume()
        findViewById<View>(android.R.id.content).post {
            updateAllLabelPositions()
        }
    }

    /**
     * Tabbed control panel: a TabLayout switches between the SETUP page (families/mode + toggles)
     * and the SLIDERS page. Null-guarded for the stub-only landscape layout. Slider value labels are
     * repositioned when the sliders page becomes visible; the selected tab is persisted per recording.
     */
    private fun setupWaveletTabs() {
        val tabs = findViewById<com.google.android.material.tabs.TabLayout?>(R.id.waveletTabs) ?: return
        val pageSetup = findViewById<View?>(R.id.pageWaveletSetup)
        val pageSliders = findViewById<View?>(R.id.pageWaveletSliders)
        fun show(index: Int) {
            pageSetup?.visibility = if (index == 0) View.VISIBLE else View.GONE
            pageSliders?.visibility = if (index == 1) View.VISIBLE else View.GONE
            if (index == 1) findViewById<View>(android.R.id.content).post { updateAllLabelPositions() }
        }
        tabs.addOnTabSelectedListener(object : com.google.android.material.tabs.TabLayout.OnTabSelectedListener {
            override fun onTabSelected(tab: com.google.android.material.tabs.TabLayout.Tab) {
                show(tab.position)
                prefs.edit { putInt("wavelet_tab", tab.position) }
            }
            override fun onTabUnselected(tab: com.google.android.material.tabs.TabLayout.Tab) {}
            override fun onTabReselected(tab: com.google.android.material.tabs.TabLayout.Tab) {}
        })
        val initial = prefs.getInt("wavelet_tab", 0).coerceIn(0, tabs.tabCount - 1)
        tabs.getTabAt(initial)?.select()
        show(initial)
    }

    /** Per-recording settings namespace; mirrors ViewerActivity.prefsNameForFile. */
    private fun prefsNameForFile(path: String?): String {
        if (path.isNullOrEmpty()) return "app_settings"
        val base = File(path).nameWithoutExtension
        val safe = buildString { for (c in base) append(if (c.isLetterOrDigit()) c else '_') }
        return "rec_$safe"
    }

    private fun fitSpinner(s: Spinner?) {
        s?.post { (s.selectedView as? TextView)?.let { it.setMaxTextSizeToFit(it.text.toString()) } }
    }

    private fun updateAllLabelPositions() {
        if (findViewById<View>(android.R.id.content).width <= 0) return

        adjustSliderThickness(sliderLevel, txtLevelValue)
        adjustSliderThickness(sliderOrder, txtOrderValue)
        adjustSliderThickness(sliderSampling, txtSamplingValue)
        adjustSliderThickness(sliderThreshold, txtThresholdValue)
        
        // Apply robust auto-sizing to ALL relevant labels to fix "printed too small" issues.
        val labelsToFit = intArrayOf(
            R.id.txtLevelValue, R.id.txtOrderValue, R.id.txtSamplingValue, R.id.txtThresholdValue,
            R.id.lblWavLevel, R.id.lblWavOrder, R.id.lblWavSampling, R.id.lblWavThreshold
        )
        for (id in labelsToFit) {
            val tv = findViewById<TextView>(id) ?: continue
            if ((tv.visibility == View.VISIBLE) && ((tv.parent as? View)?.width ?: 0 > 0)) {
                tv.setMaxTextSizeToFit(tv.text.toString())
            }
        }
        updateSafetyStatus()
    }

    private fun barColorForSlider(slider: Slider): Int = when (slider.id) {
        R.id.sliderLevel, R.id.sliderOrder -> Color.GREEN
        R.id.sliderThreshold -> Color.CYAN
        else -> Color.LTGRAY // FS / sampling
    }

    // Slider rendering is unified in ViewUtils (styleValueBarSlider / updateValueBarLabel) so the
    // Wavelet sliders look identical to the Listen/FFT EQ & Filter sliders.
    private fun adjustSliderThickness(slider: Slider, label: TextView?) =
        styleValueBarSlider(slider, label, barColorForSlider(slider))

    private fun updateLabelPosition(slider: Slider, label: TextView?) =
        updateValueBarLabel(slider, label, barColorForSlider(slider))

    private fun Slider.setSafeValue(v: Float) {
        val step = this.stepSize
        if (step > 0f) {
            val numSteps = round((v - valueFrom) / step)
            this.value = (valueFrom + numSteps * step).coerceIn(valueFrom, valueTo)
        } else {
            this.value = v.coerceIn(valueFrom, valueTo)
        }
    }

    /** Keep the legacy engine flags in lockstep with the single analysisMode selection. */
    private fun syncModeFlags() {
        isWPT = analysisMode == 1
        isCWT = analysisMode == 2
        isReconstruct = analysisMode == 3
    }

    private fun loadPrefs() {
        selectedFamilyIdx = prefs.getInt("family", 0)
        selectedBoundaryIdx = prefs.getInt("boundary", 0)
        isSoftThreshold = prefs.getBoolean("soft_thresh", true)
        isLogScale = prefs.getBoolean("log_scale", false)
        isLocalNorm = prefs.getBoolean("local_norm", false)

        // Single mode selection; migrate from the old independent cwt/wpt/reconstruct booleans.
        // Default to CWT (mode 2) for best frequency resolution.
        analysisMode = if (prefs.contains("analysis_mode")) {
            prefs.getInt("analysis_mode", 2)
        } else when {
            prefs.getBoolean("cwt", false) -> 2
            prefs.getBoolean("reconstruct", false) -> 3
            prefs.getBoolean("wpt", false) -> 1
            else -> 2  // Default to CWT instead of DWT
        }
        syncModeFlags()

        decompositionLevel = prefs.getInt("level", 8)           // Default to max level (8)
        waveletOrder = prefs.getInt("order", 10)                // Default to max order (10)
        targetFreq = prefs.getFloat("freq", 44100f)

        // Snap the saved threshold onto one of the log slider positions.
        // Default to 0f (off) for clean visualization.
        threshold = thresholdSteps[thresholdToIndex(prefs.getFloat("threshold", 0f))]

        colorSchemeIdx = ColorMaps.loadForRecording(this, prefs)
        cwtWaveletIdx = prefs.getInt("cwt_wavelet", 0)

        updateUIFromSettings()
    }

    /** Nearest log-slider position for a threshold magnitude. */
    private fun thresholdToIndex(t: Float): Int {
        var best = 0
        var bestD = Float.MAX_VALUE
        for (i in thresholdSteps.indices) {
            val d = abs(thresholdSteps[i] - t)
            if (d < bestD) { bestD = d; best = i }
        }
        return best
    }

    private fun thresholdLabel(t: Float): String =
        if (t <= 0f) "OFF" else String.format(java.util.Locale.US, "%.3f", t)

    /** Refresh the safety status line: current mode + the largest sampling freq that fits.
     *  Portrait shows this compactly inline next to the SOFT checkbox (txtModeSafeIndicator);
     *  the legacy full-width txtSafetyStatus is kept for layouts that still declare it. */
    private fun updateSafetyStatus() {
        val inline = findViewById<TextView?>(R.id.txtModeSafeIndicator)
        val pcm = pcmData
        if (pcm == null || pcm.isEmpty()) {
            txtSafetyStatus.text = ""
            inline?.text = ""
            return
        }
        val ceilingKhz = safeFreqCeiling() / 1000f
        val over = targetFreq > safeFreqCeiling()
        val statusText = if (over) {
            "⚠ ${modeNames[analysisMode]}: FS above safe ${String.format(java.util.Locale.US, "%.1f", ceilingKhz)} kHz — will auto-ease"
        } else {
            "${modeNames[analysisMode]} · safe FS ≤ ${String.format(java.util.Locale.US, "%.1f", ceilingKhz)} kHz"
        }
        txtSafetyStatus.setTextColor(if (over) Color.YELLOW else Color.LTGRAY)
        txtSafetyStatus.setMaxTextSizeToFit(statusText, maxSizeSp = 12f)

        // Compact inline indicator, e.g. "CWT safe <60kHz" (or "⚠ CWT >60kHz" when over budget).
        inline?.apply {
            text = if (over) "⚠ ${modeNames[analysisMode]} >${String.format(java.util.Locale.US, "%.0f", ceilingKhz)}kHz"
                    else "${modeNames[analysisMode]} safe <${String.format(java.util.Locale.US, "%.0f", ceilingKhz)}kHz"
            setTextColor(if (over) Color.YELLOW else Color.parseColor("#FFCC99"))
        }
    }

    /**
     * Largest sampling frequency whose resampled buffer still fits the size + memory budgets for the
     * currently-selected mode/level. Used to *trim the Sampling slider* rather than disable features.
     */
    private fun safeFreqCeiling(): Float {
        val pcm = pcmData ?: return targetFreq
        if (pcm.isEmpty()) return MIN_SAMPLING_FREQ
        val maxBySize = if (isCWT) MAX_CWT_SAMPLES else MAX_DWT_SAMPLES
        val perSampleBytes = if (isCWT) 100L * 4 else (decompositionLevel + 1).toLong() * 4
        val maxByMem = (MEM_BUDGET_BYTES * 9 / 10) / perSampleBytes   // 90% of budget for headroom
        val maxResampled = min(maxBySize, maxByMem)
        return (maxResampled.toFloat() * originalSampleRate) / pcm.size
    }

    /**
     * One escalation step of the graduated failsafe. Gentlest first:
     *   1. trim the Sampling-frequency slider toward a value that fits (jump to the safe ceiling on
     *      a predicted over-budget, or step down 25% on an unpredicted runtime crash);
     *   2. then lower the Decomposition-level slider (the dominant cost for WPT's 2^level fan-out);
     *   3. and only as a *last resort* uncheck the most expensive engine (CWT > WPT > Reconstruct).
     * Returns true if a setting was changed (caller should re-run), false if nothing is left to trim.
     */
    private fun degradeForSafety(): Boolean {
        val pcm = pcmData ?: return false
        if (pcm.isEmpty()) return false

        // 1) Sampling frequency.
        if (targetFreq > MIN_SAMPLING_FREQ) {
            val ceiling = safeFreqCeiling()
            val newFreq = max(MIN_SAMPLING_FREQ, min(ceiling, targetFreq * 0.75f))
            if (newFreq < targetFreq - 0.5f) {
                targetFreq = floor(newFreq)
                prefs.edit { putFloat("freq", targetFreq) }
                return true
            }
        }

        // 2) Decomposition level.
        if (decompositionLevel > MIN_LEVEL) {
            decompositionLevel -= 1
            prefs.edit { putInt("level", decompositionLevel) }
            return true
        }

        // 3) Last resort: drop the (single) active engine back to plain DWT, the cheapest path.
        if (analysisMode != 0) {
            analysisMode = 0
            syncModeFlags()
            prefs.edit { putInt("analysis_mode", 0) }
            return true
        }
        return false
    }

    /**
     * Entry point for every failsafe trigger (predicted over-budget or an actual runtime crash).
     * Backs off one graduated step and retries; if nothing can be backed off further, stops cleanly.
     */
    private fun applyFailsafe(reason: String) {
        runOnUiThread {
            isStopRequested = true
            if (degradeForSafety()) {
                updateUIFromSettings()
                Toast.makeText(this, "Eased settings to avoid crash: $reason", Toast.LENGTH_SHORT).show()
                runDwt()
            } else {
                waveletView.setCalculating(false)
                Toast.makeText(this, "Cannot run safely even at minimum settings: $reason", Toast.LENGTH_LONG).show()
            }
        }
    }

    private fun updateUIFromSettings() {
        if (familySpinner.adapter != null) refreshFamilySpinner()  // mode-aware; skip pre-setup
        boundarySpinner.setSelection(selectedBoundaryIdx)
        modeSpinner.setSelection(analysisMode)
        checkSoft.isChecked = isSoftThreshold
        checkLog.isChecked = isLogScale
        checkLocalNorm.isChecked = isLocalNorm

        // Use safe setters to prevent IllegalStateException
        sliderLevel.setSafeValue(decompositionLevel.toFloat())
        sliderOrder.setSafeValue(waveletOrder.toFloat())
        sliderSampling.setSafeValue(targetFreq)
        sliderThreshold.setSafeValue(thresholdToIndex(threshold).toFloat())

        txtLevelValue.text = decompositionLevel.toString()
        txtOrderValue.text = waveletOrder.toString()
        txtSamplingValue.text = if (targetFreq >= 1000) "${(targetFreq/1000).toInt()}\nkHz" else "${targetFreq.toInt()}\nHz"
        txtThresholdValue.text = thresholdLabel(threshold)

        updateOrderSliderRange()

        // Sync WaveletView state
        waveletView.setWPT(isWPT)
        waveletView.setLogScale(isLogScale)
        waveletView.setLocalNorm(isLocalNorm)
        waveletView.setCwtMode(isCWT)
        waveletView.setColorScheme(colorSchemeIdx)
        waveletView.setSamplingFreq(targetFreq)

        updateSafetyStatus()
    }

    private fun validateConstraints(): Boolean {
        val pcm = pcmData ?: return false

        val resampledSize = (pcm.size * (targetFreq / originalSampleRate)).toInt()
        val maxSamples = if (isCWT) MAX_CWT_SAMPLES else MAX_DWT_SAMPLES
        if (resampledSize > maxSamples) {
            applyFailsafe("buffer ${resampledSize} samples over the ${maxSamples} ${if (isCWT) "CWT" else "DWT"} limit")
            return false
        }

        val estMemory = if (isCWT) resampledSize.toLong() * 100 * 4
                        else resampledSize.toLong() * (decompositionLevel + 1) * 4
        if (estMemory > MEM_BUDGET_BYTES) {
            applyFailsafe("estimated ${estMemory / 1024 / 1024} MB over the ${MEM_BUDGET_BYTES / 1024 / 1024} MB budget")
            return false
        }

        return true
    }

    private fun setupControls() {
        // FAMILY is mode-aware: in CWT it lists mother wavelets (Morlet / Mexican Hat); otherwise
        // the DWT filter families. The listener writes whichever selection the current mode owns.
        familySpinner.onItemSelectedListener = object : AdapterView.OnItemSelectedListener {
            override fun onItemSelected(p0: AdapterView<*>?, p1: View?, pos: Int, p3: Long) {
                if (isCWT) {
                    if (cwtWaveletIdx != pos) {
                        cwtWaveletIdx = pos
                        prefs.edit { putInt("cwt_wavelet", pos) }
                        runDwt()
                    }
                } else {
                    if (selectedFamilyIdx != pos) {
                        selectedFamilyIdx = pos
                        prefs.edit().putInt("family", pos).apply()
                        updateOrderSliderRange()
                        runDwt()
                    }
                }
            }
            override fun onNothingSelected(p0: AdapterView<*>?) {}
        }

        val boundaries = arrayOf("Zero-padding", "Periodic", "Symmetric")
        val boundaryAdapter = ArrayAdapter(this, R.layout.spinner_item, boundaries)
        boundaryAdapter.setDropDownViewResource(android.R.layout.simple_spinner_dropdown_item)
        boundarySpinner.adapter = boundaryAdapter
        boundarySpinner.onItemSelectedListener = object : AdapterView.OnItemSelectedListener {
            override fun onItemSelected(p0: AdapterView<*>?, p1: View?, pos: Int, p3: Long) {
                if (selectedBoundaryIdx != pos) {
                    selectedBoundaryIdx = pos
                    prefs.edit().putInt("boundary", pos).apply()
                    runDwt()
                }
            }
            override fun onNothingSelected(p0: AdapterView<*>?) {}
        }

        // Analysis mode: one exclusive choice (DWT / WPT / CWT / RECON) replacing the old trio of
        // independent checkboxes, which let users tick combinations the dispatch silently ignored.
        // Descriptive labels for the spinner; modeNames stays short for the safety status line.
        val modeDescriptions = arrayOf("DWT (Discrete)", "WPT (Packet)", "CWT (Continuous)", "Reconstruct")
        val modeAdapter = ArrayAdapter(this, R.layout.spinner_item, modeDescriptions)
        modeAdapter.setDropDownViewResource(android.R.layout.simple_spinner_dropdown_item)
        modeSpinner.adapter = modeAdapter
        modeSpinner.setSelection(analysisMode)
        modeSpinner.onItemSelectedListener = object : AdapterView.OnItemSelectedListener {
            override fun onItemSelected(p0: AdapterView<*>?, p1: View?, pos: Int, p3: Long) {
                if (analysisMode != pos) {
                    analysisMode = pos
                    syncModeFlags()
                    waveletView.setWPT(isWPT)
                    waveletView.setCwtMode(isCWT)
                    prefs.edit { putInt("analysis_mode", pos) }
                    refreshFamilySpinner()   // swap FAMILY choices to match the new mode
                    updateSafetyStatus()
                    runDwt()
                }
            }
            override fun onNothingSelected(p0: AdapterView<*>?) {}
        }

        // Populate FAMILY for the initial mode (after the mode spinner is wired).
        refreshFamilySpinner()

        checkSoft.setOnCheckedChangeListener { _, isChecked ->
            isSoftThreshold = isChecked
            prefs.edit().putBoolean("soft_thresh", isChecked).apply()
            runDwt()
        }
        checkLog.setOnCheckedChangeListener { _, isChecked ->
            isLogScale = isChecked
            waveletView.setLogScale(isLogScale)
            prefs.edit().putBoolean("log_scale", isChecked).apply()
        }
        checkLocalNorm.setOnCheckedChangeListener { _, isChecked ->
            isLocalNorm = isChecked
            waveletView.setLocalNorm(isLocalNorm)
            prefs.edit().putBoolean("local_norm", isChecked).apply()
        }

        findViewById<Button>(R.id.btnWaveletGalleryTop).setOnClickListener {
            val intent = Intent(this, GalleryActivity::class.java)
            intent.flags = Intent.FLAG_ACTIVITY_CLEAR_TOP
            startActivity(intent)
            finish()
        }
        findViewById<Button>(R.id.btnWaveletListenTop).setOnClickListener {
            val intent = Intent(this, MainActivity::class.java)
            intent.flags = Intent.FLAG_ACTIVITY_CLEAR_TOP or Intent.FLAG_ACTIVITY_SINGLE_TOP
            startActivity(intent)
            finish()
        }

        // STOP button is optional (portrait layouts removed it; stub exists in landscape)
        findViewById<Button?>(R.id.btnWaveletStop)?.setOnClickListener {
            isStopRequested = true
        }

        sliderLevel.addOnChangeListener { s, value, fromUser ->
            if (fromUser) {
                decompositionLevel = value.toInt()
                txtLevelValue.text = decompositionLevel.toString()
                prefs.edit().putInt("level", decompositionLevel).apply()
                updateLabelPosition(s, txtLevelValue)
                runDwt()
            }
        }
        sliderOrder.addOnChangeListener { s, value, fromUser ->
            if (fromUser) {
                waveletOrder = value.toInt()
                txtOrderValue.text = waveletOrder.toString()
                prefs.edit().putInt("order", waveletOrder).apply()
                updateLabelPosition(s, txtOrderValue)
                runDwt()
            }
        }
        sliderSampling.addOnChangeListener { s, value, fromUser ->
            if (fromUser) {
                targetFreq = value
                txtSamplingValue.text = if (targetFreq >= 1000) "${(targetFreq/1000).toInt()}\nkHz" else "${targetFreq.toInt()}\nHz"
                prefs.edit().putFloat("freq", targetFreq).apply()
                updateLabelPosition(s, txtSamplingValue)
                updateSafetyStatus()
                runDwt()
            }
        }
        sliderThreshold.addOnChangeListener { s, value, fromUser ->
            if (fromUser) {
                // Slider holds a log-step index (0..9); map it to the actual threshold magnitude.
                val idx = value.roundToInt().coerceIn(0, thresholdSteps.size - 1)
                threshold = thresholdSteps[idx]
                txtThresholdValue.text = thresholdLabel(threshold)
                prefs.edit().putFloat("threshold", threshold).apply()
                updateLabelPosition(s, txtThresholdValue)
                runDwt()
            }
        }
        setupColorSpinner()
    }

    private fun setupColorSpinner() {
        colorSchemeIdx = colorSchemeIdx.coerceIn(0, ColorMaps.count - 1)
        waveletView.setColorScheme(colorSchemeIdx)
        styleColorButton(colorSchemeIdx)
        btnColor.setOnClickListener { showColorDialog() }
    }

    /** Style the COLOR button to reflect the active scheme: background = scheme's high colour,
     *  text = scheme's low colour (a tiny live preview of the gradient endpoints). */
    private fun styleColorButton(schemeIdx: Int) {
        val bg = ColorMaps.highColorFor(schemeIdx)
        val fg = ColorMaps.lowColorFor(schemeIdx)
        btnColor.backgroundTintList = android.content.res.ColorStateList.valueOf(bg)
        btnColor.setTextColor(fg)
        btnColor.text = "COLOR"
    }

    /** COLOR picker: the shared gradient-swatch radio dialog (same on Listen, FFT, Wavelet). */
    private fun showColorDialog() {
        showColorSchemeDialog(colorSchemeIdx) { sel ->
            colorSchemeIdx = sel
            waveletView.setColorScheme(sel)
            ColorMaps.saveForRecording(this, prefs, sel)
            styleColorButton(sel)
        }
    }

    /** Populate the mode-aware FAMILY spinner: CWT mother wavelets in CWT mode, DWT filter families
     *  otherwise. Selecting the current value is guarded in the listener, so no spurious re-run. */
    private fun refreshFamilySpinner() {
        val items = if (isCWT) arrayOf("Morlet", "Mexican Hat")
                    else arrayOf("Daubechies", "Symlet", "Coiflet")
        val adapter = ArrayAdapter(this, R.layout.spinner_item, items)
        adapter.setDropDownViewResource(android.R.layout.simple_spinner_dropdown_item)
        familySpinner.adapter = adapter
        val sel = (if (isCWT) cwtWaveletIdx else selectedFamilyIdx).coerceIn(0, items.size - 1)
        familySpinner.setSelection(sel)
    }

    private fun updateOrderSliderRange() {
        val availableOrders = filterMap[selectedFamilyIdx]?.keys?.sorted() ?: listOf(2)
        val min = availableOrders.first().toFloat()
        val max = availableOrders.last().toFloat()
        
        // Temporarily broaden bounds to avoid value-out-of-bounds crashes during update
        sliderOrder.valueFrom = minOf(sliderOrder.valueFrom, min, sliderOrder.value)
        sliderOrder.valueTo = maxOf(sliderOrder.valueTo, max, sliderOrder.value)
        
        if (waveletOrder !in availableOrders) {
            waveletOrder = availableOrders.first()
        }
        
        sliderOrder.setSafeValue(waveletOrder.toFloat())
        
        if (min == max) {
            sliderOrder.valueFrom = min - 1
            sliderOrder.valueTo = max
            sliderOrder.isEnabled = false
        } else {
            sliderOrder.valueFrom = min
            sliderOrder.valueTo = max
            sliderOrder.isEnabled = true
        }
    }

    private fun loadAndDecode(file: File) {
        val requestId = ++currentRequestId
        isStopRequested = false
        Thread {
            var codec: MediaCodec? = null
            var extractor: MediaExtractor? = null
            val maxSamples = 200000
            try {
                // WAV is raw PCM — no MediaCodec decoder for audio/raw, so parse directly.
                if (file.extension.equals("wav", ignoreCase = true)) {
                    val wav = WavReader.read(file, maxSamples)
                    originalSampleRate = wav.sampleRate.toFloat()
                    if (requestId == currentRequestId && !isStopRequested) {
                        pcmData = wav.samples
                        runOnUiThread {
                            if (validateConstraints()) {
                                runDwt()
                            }
                        }
                    }
                    return@Thread
                }
                extractor = MediaExtractor()
                extractor.setDataSource(file.absolutePath)
                if (extractor.trackCount == 0) {
                    extractor.release()
                    runOnUiThread { Toast.makeText(this, "No audio track found", Toast.LENGTH_LONG).show() }
                    return@Thread
                }
                extractor.selectTrack(0)
                val format = extractor.getTrackFormat(0)
                val channels = if (format.containsKey(MediaFormat.KEY_CHANNEL_COUNT)) format.getInteger(MediaFormat.KEY_CHANNEL_COUNT) else 1
                originalSampleRate = format.getInteger(MediaFormat.KEY_SAMPLE_RATE).toFloat()
                val mime = format.getString(MediaFormat.KEY_MIME)
                if (mime == null) {
                    runOnUiThread { Toast.makeText(this, "Unknown audio format", Toast.LENGTH_LONG).show() }
                    return@Thread
                }
                codec = MediaCodec.createDecoderByType(mime)
                codec.configure(format, null, null, 0)
                codec.start()

                var pcmDataArr = FloatArray(256 * 1024)
                var pcmCount = 0
                val info = MediaCodec.BufferInfo()
                var isEOS = false

                while (!isEOS && pcmCount < maxSamples && !isStopRequested && requestId == currentRequestId) {
                    val inIdx = codec.dequeueInputBuffer(10000)
                    if (inIdx >= 0) {
                        val buffer = codec.getInputBuffer(inIdx)!!
                        val size = extractor.readSampleData(buffer, 0)
                        if (size < 0) {
                            codec.queueInputBuffer(inIdx, 0, 0, 0, MediaCodec.BUFFER_FLAG_END_OF_STREAM)
                            isEOS = true
                        } else {
                            codec.queueInputBuffer(inIdx, 0, size, extractor.sampleTime, 0)
                            extractor.advance()
                        }
                    }
                    val outIdx = codec.dequeueOutputBuffer(info, 10000)
                    if (outIdx >= 0) {
                        val outBuffer = codec.getOutputBuffer(outIdx)!!
                        val samplesAvailable = outBuffer.remaining() / 2
                        val framesAvailable = samplesAvailable / channels
                        val framesToRead = min(framesAvailable, maxSamples - pcmCount)
                        
                        if (pcmCount + framesToRead > pcmDataArr.size) {
                            pcmDataArr = pcmDataArr.copyOf(max(pcmDataArr.size * 2, pcmCount + framesToRead))
                        }
                        
                        for (unused1 in 0 until framesToRead) {
                            var sum = 0f
                            for (unused2 in 0 until channels) {
                                if (outBuffer.remaining() >= 2) {
                                    sum += outBuffer.short / 32768f
                                }
                            }
                            pcmDataArr[pcmCount++] = sum / channels
                        }
                        codec.releaseOutputBuffer(outIdx, false)
                    }
                }
                
                if (requestId == currentRequestId && !isStopRequested) {
                    pcmData = pcmDataArr.copyOf(pcmCount)
                    runOnUiThread { 
                        if (validateConstraints()) {
                            runDwt()
                        }
                    }
                }
            } catch (e: Exception) {
                e.printStackTrace()
                // A decode failure can't be fixed by easing analysis settings (there's no PCM to
                // retry), so just report it rather than running the back-off.
                runOnUiThread {
                    waveletView.setCalculating(false)
                    Toast.makeText(this, "Decode error: ${e.message}", Toast.LENGTH_LONG).show()
                }
            } finally {
                try {
                    codec?.stop()
                    codec?.release()
                } catch (unused: Exception) {}
                try {
                    extractor?.release()
                } catch (unused: Exception) {}
            }
        }.start()
    }

    private fun runDwt() {
        val originalData = pcmData ?: return
        updateSafetyStatus()
        if (!validateConstraints()) return

        val requestId = ++currentRequestId
        isStopRequested = true

        if (isCWT) {
            Thread { 
                try {
                    if (requestId == currentRequestId) {
                        isStopRequested = false
                        runCwt(originalData, requestId)
                    }
                } catch (e: Throwable) {
                    if (requestId == currentRequestId) {
                        applyFailsafe("CWT failure: ${e.message}")
                    }
                }
            }.start()
            return
        }

        if (isReconstruct) {
            runReconstruct()
            return
        }

        Thread {
            try {
                if (requestId != currentRequestId) return@Thread
                isStopRequested = false
                
                val data = resample(originalData, originalSampleRate, targetFreq)
                val h = filterMap[selectedFamilyIdx]?.get(waveletOrder) ?: return@Thread
                val g = getDetailFilter(h)

                val results = mutableListOf<FloatArray>()
                var currentSignal = data
                
                runOnUiThread { waveletView.setCalculating(calculating = true) }

                if (isWPT) {
                    var currentLevelNodes = mutableListOf(data)
                    for (level in 0 until decompositionLevel) {
                        if (isStopRequested || requestId != currentRequestId) break
                        val nextLevelNodes = mutableListOf<FloatArray>()
                        for (node in currentLevelNodes) {
                            val (a, d) = decompose(node, h, g)
                            nextLevelNodes.add(a.map { applyThreshold(it) }.toFloatArray())
                            nextLevelNodes.add(d.map { applyThreshold(it) }.toFloatArray())
                        }
                        currentLevelNodes = nextLevelNodes
                        val progressVal = (level + 1).toFloat() / decompositionLevel
                        val interim = currentLevelNodes.toList()
                        runOnUiThread {
                            waveletView.setProgress(progressVal)
                            waveletView.updateData(interim, isInterim = true)
                        }
                    }
                    results.addAll(currentLevelNodes)
                } else {
                    for (level in 0 until decompositionLevel) {
                        if (isStopRequested || requestId != currentRequestId) break
                        val (a, d) = decompose(currentSignal, h, g)
                        results.add(d.map { applyThreshold(it) }.toFloatArray())
                        currentSignal = a
                        
                        val progressVal = (level + 1).toFloat() / decompositionLevel
                        val interim = results.toList() + listOf(currentSignal)
                        runOnUiThread {
                            waveletView.setProgress(progressVal)
                            waveletView.updateData(interim, isInterim = true)
                        }
                    }
                    results.add(currentSignal)
                }

                if (requestId == currentRequestId) {
                    if (isStopRequested) {
                        runOnUiThread { 
                            waveletView.setCalculating(false)
                            Toast.makeText(this@WaveletActivity, "Calculation Stopped", Toast.LENGTH_SHORT).show()
                        }
                    } else {
                        val finalResult = if (isWPT) results else results.toList() + listOf(currentSignal)
                        runOnUiThread {
                            waveletView.setSamplingFreq(targetFreq)
                            waveletView.updateData(finalResult, isInterim = false)
                        }
                    }
                }
            } catch (e: Exception) {
                applyFailsafe("DWT error: ${e.message}")
            }
        }.start()
    }

    private fun runCwt(originalData: FloatArray, requestId: Int) {
        if (isStopRequested || requestId != currentRequestId) return
        val data = resample(originalData, originalSampleRate, targetFreq)
        val n = data.size
        
        // Final safety check for CWT memory usage
        if (n > 60000) {
            applyFailsafe("CWT buffer $n samples over the 60k limit")
            return
        }

        val paddedSize = try {
            FFTUtils.nextPowerOfTwo(n)
        } catch (e: Exception) {
            applyFailsafe("buffer error: ${e.message}")
            return
        }
        
        val sigRe = try { FloatArray(paddedSize) } catch (e: OutOfMemoryError) { null }
        val sigIm = try { FloatArray(paddedSize) } catch (e: OutOfMemoryError) { null }
        
        if (sigRe == null || sigIm == null) {
            applyFailsafe("out of memory allocating CWT signal buffers")
            return
        }

        for (i in 0 until n) sigRe[i] = data[i]
        
        FFTUtils.compute(sigRe, sigIm)
        
        val numScales = 100
        val minScale = 1f
        val maxScale = 2f.pow(decompositionLevel.toFloat() + 3)
        
        val coefficients = Array(numScales) { FloatArray(n) }
        
        val wavRe = try { FloatArray(paddedSize) } catch (e: OutOfMemoryError) { null }
        val wavIm = try { FloatArray(paddedSize) } catch (e: OutOfMemoryError) { null }
        
        if (wavRe == null || wavIm == null) {
            applyFailsafe("out of memory allocating CWT scaling buffers")
            return
        }

        try {
            for (s in 0 until numScales) {
                if (isStopRequested || requestId != currentRequestId) break
                val scale = minScale * (maxScale / minScale).pow(s.toFloat() / (numScales - 1))
                
                val w0 = 6.0f
                val sqrtScale = sqrt(scale)
                val isMexican = cwtWaveletIdx == 1
                for (i in 0 until paddedSize) {
                    val omega = if (i <= paddedSize / 2) {
                        (2f * PI.toFloat() * i) / paddedSize
                    } else {
                        (2f * PI.toFloat() * (i - paddedSize)) / paddedSize
                    }

                    if (isMexican) {
                        // Mexican Hat (Ricker): frequency response ∝ (sω)²·exp(−(sω)²/2).
                        // Real & symmetric, so the imaginary part is zero (like the Morlet branch).
                        val sw = scale * omega
                        val sw2 = sw * sw
                        val valExp = -0.5f * sw2
                        wavRe[i] = if (valExp > -20f) sw2 * exp(valExp) * sqrtScale else 0f
                    } else {
                        // Morlet (default).
                        val valExp = -0.5f * (scale * omega - w0).pow(2)
                        wavRe[i] = if (valExp > -20f) exp(valExp) * sqrtScale else 0f
                    }
                    wavIm[i] = 0f
                }
                
                for (i in 0 until paddedSize) {
                    val r = sigRe[i] * wavRe[i] - sigIm[i] * wavIm[i]
                    val im = sigRe[i] * wavIm[i] + sigIm[i] * wavRe[i]
                    wavRe[i] = r
                    wavIm[i] = im
                }
                
                FFTUtils.inverse(wavRe, wavIm)
                for (i in 0 until n) {
                    coefficients[s][i] = applyThreshold(sqrt(wavRe[i] * wavRe[i] + wavIm[i] * wavIm[i]))
                }

                if (s % 10 == 0 || s == numScales - 1) {
                    val progressVal = (s + 1).toFloat() / numScales
                    val interim = coefficients.slice(0..s).toList()
                    runOnUiThread {
                        waveletView.setProgress(progressVal)
                        waveletView.updateData(interim, isInterim = true)
                    }
                }
            }
        } catch (e: Exception) {
            applyFailsafe("CWT error: ${e.message}")
            return
        }
        
        if (requestId == currentRequestId) {
            if (isStopRequested) {
                runOnUiThread { 
                    waveletView.setCalculating(false) 
                    Toast.makeText(this@WaveletActivity, "Calculation Stopped", Toast.LENGTH_SHORT).show()
                }
            } else {
                runOnUiThread {
                    waveletView.setSamplingFreq(targetFreq)
                    waveletView.updateData(coefficients.toList(), isInterim = false)
                }
            }
        }
    }

    private fun resample(input: FloatArray, from: Float, to: Float): FloatArray {
        if (abs(from - to) < 1f) return input
        val ratio = to / from
        val newSize = (input.size * ratio).toInt()
        val output = FloatArray(newSize)
        for (i in 0 until newSize) {
            val srcIdx = i / ratio
            val i0 = floor(srcIdx).toInt().coerceIn(0, input.size - 1)
            val i1 = (i0 + 1).coerceIn(0, input.size - 1)
            val frac = srcIdx - i0
            output[i] = input[i0] * (1 - frac) + input[i1] * frac
        }
        return output
    }

    private fun decompose(signal: FloatArray, h: FloatArray, g: FloatArray): Pair<FloatArray, FloatArray> {
        val n = signal.size
        val m = h.size
        val outSize = (n + m - 1) / 2
        val approx = FloatArray(outSize)
        val detail = FloatArray(outSize)

        for (i in 0 until outSize) {
            var sumA = 0f
            var sumD = 0f
            for (j in 0 until m) {
                val idx = getIdx(2 * i + j, n)
                sumA += signal[idx] * h[j]
                sumD += signal[idx] * g[j]
            }
            approx[i] = sumA
            detail[i] = sumD
        }
        return Pair(approx, detail)
    }

    private fun getDetailFilter(h: FloatArray): FloatArray {
        val g = FloatArray(h.size)
        for (i in h.indices) {
            g[i] = (if (i % 2 == 0) 1f else -1f) * h[h.size - 1 - i]
        }
        return g
    }

    private fun runReconstruct() {
        val originalData = pcmData ?: return
        val requestId = ++currentRequestId
        isStopRequested = false
        Thread {
            try {
                val data = resample(originalData, originalSampleRate, targetFreq)
                val h = filterMap[selectedFamilyIdx]?.get(waveletOrder) ?: return@Thread
                val g = getDetailFilter(h)
                
                runOnUiThread { waveletView.setCalculating(calculating = true) }
                runReconstructInternal(data, h, g, requestId)
            } catch (e: Exception) {
                applyFailsafe("reconstruct error: ${e.message}")
            }
        }.start()
    }

    private fun runReconstructInternal(data: FloatArray, h: FloatArray, g: FloatArray, requestId: Int) {
        val details = mutableListOf<FloatArray>()
        var currentSignal = data
        for (level in 0 until decompositionLevel) {
            if (isStopRequested || requestId != currentRequestId) break
            val (a, d) = decompose(currentSignal, h, g)
            details.add(d.map { applyThreshold(it) }.toFloatArray())
            currentSignal = a
            runOnUiThread {
                waveletView.setProgress((level + 1).toFloat() / (decompositionLevel * 2))
                waveletView.updateData(listOf(currentSignal), isInterim = true)
            }
        }

        if (!isStopRequested && requestId == currentRequestId) {
            var reconstructed = currentSignal
            for (i in details.size - 1 downTo 0) {
                if (isStopRequested || requestId != currentRequestId) break
                reconstructed = reconstruct(reconstructed, details[i], h, g)
                val p = (decompositionLevel + (details.size - i)).toFloat() / (decompositionLevel * 2)
                runOnUiThread {
                    waveletView.setProgress(p)
                    waveletView.updateData(listOf(reconstructed), isInterim = true)
                }
            }
            if (requestId == currentRequestId && !isStopRequested) {
                runOnUiThread {
                    waveletView.setSamplingFreq(targetFreq)
                    waveletView.updateData(listOf(reconstructed), isInterim = false)
                }
            }
        }
    }

    private fun reconstruct(approx: FloatArray, detail: FloatArray, h: FloatArray, g: FloatArray): FloatArray {
        val n = approx.size * 2
        val m = h.size
        val signal = FloatArray(n)
        for (i in 0 until n) {
            var sum = 0f
            for (j in 0 until m) {
                val idx = i - j
                if (idx >= 0 && idx % 2 == 0) {
                    val p = idx / 2
                    if (p < approx.size) {
                        sum += approx[p] * h[j] + detail[p] * g[j]
                    }
                }
            }
            signal[i] = sum
        }
        return signal
    }

    private fun getIdx(i: Int, n: Int): Int {
        return when (selectedBoundaryIdx) {
            0 -> if (i < n) i else 0 
            1 -> i % n 
            2 -> {
                if (i < n) i else (2 * n - 1 - i).coerceIn(0, n - 1)
            }
            else -> i.coerceIn(0, n - 1)
        }
    }

    private fun applyThreshold(d: Float): Float {
        // Floating point precision fix for Material Slider validation
        val roundedThreshold = (threshold * 1000f).roundToInt() / 1000f
        val mag = abs(d)
        return if (isSoftThreshold) {
            if (mag > roundedThreshold) sign(d) * (mag - roundedThreshold) else 0f
        } else {
            if (mag > roundedThreshold) d else 0f
        }
    }
}
