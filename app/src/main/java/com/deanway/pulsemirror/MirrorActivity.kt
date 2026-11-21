package com.deanway.pulsemirror

import android.Manifest
import android.content.pm.PackageManager
import android.content.res.ColorStateList
import android.graphics.Color
import android.graphics.drawable.GradientDrawable
import android.os.Bundle
import android.view.Gravity
import android.view.ViewGroup
import android.view.WindowManager
import android.widget.Button
import android.widget.FrameLayout
import android.widget.LinearLayout
import android.widget.TextView
import androidx.activity.ComponentActivity
import androidx.activity.result.contract.ActivityResultContracts
import androidx.camera.view.PreviewView
import androidx.core.content.ContextCompat
import androidx.core.view.ViewCompat
import androidx.core.view.WindowCompat
import androidx.core.view.WindowInsetsCompat
import androidx.lifecycle.lifecycleScope
import com.presagetech.smartspectra.CameraSelection
import com.presagetech.smartspectra.ProcessingStatus
import com.presagetech.smartspectra.SmartSpectraConfig
import com.presagetech.smartspectra.SmartSpectraSdk
import com.presagetech.smartspectra.proto.MetricTypesProto.MetricType
import kotlin.math.roundToInt
import kotlinx.coroutines.launch

/**
 * The mirror: full-screen front camera with a ring that beats at your live pulse,
 * a halo that swells with your breathing, and a calm meter driven by HRV.
 * Based on the SmartSpectra 3.5 Android quickstart (Option 1: API key).
 */
class MirrorActivity : ComponentActivity() {

    private val sdk by lazy { SmartSpectraSdk.shared }

    private lateinit var ring: PulseRingView
    private lateinit var pulseLabel: TextView
    private lateinit var breathLabel: TextView
    private lateinit var calmLabel: TextView
    private lateinit var moodLabel: TextView
    private lateinit var statusLabel: TextView
    private lateinit var hintLabel: TextView
    private lateinit var toggleButton: Button

    private var latestChestTimestamp: Long = Long.MIN_VALUE
    private val session = SessionStats()
    private val moodGate = MoodGate()
    private val hintGate = HintGate { code, hint -> friendlyHint(code, hint) }

    private val cameraPermissionLauncher = registerForActivityResult(
        ActivityResultContracts.RequestPermission(),
    ) { granted ->
        if (granted) startProcessing() else statusLabel.text = "Camera permission is needed"
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        WindowCompat.setDecorFitsSystemWindows(window, false)
        window.addFlags(WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON)

        sdk.config.apiKey = BuildConfig.PRESAGE_API_KEY
        sdk.config.imageOutputEnabled = false   // never hand frames back to the app
        sdk.config.enableTelemetry = false      // see Consent & Data Handling
        sdk.useCamera(CameraSelection.Front)
        sdk.config.requestedMetrics =
            SmartSpectraConfig.breathingMetrics +
                SmartSpectraConfig.cardioMetrics +
                listOf(MetricType.EXPRESSIONS)

        buildUi()
        bindSdk()
        resetUi()
    }

    override fun onPause() {
        super.onPause()
        lifecycleScope.launch {
            when (sdk.processingStatus.value) {
                ProcessingStatus.RUNNING, ProcessingStatus.STARTING, ProcessingStatus.STOPPING ->
                    runCatching { sdk.stop() }
                else -> Unit
            }
        }
    }

    private fun bindSdk() {
        sdk.processingStatus.observe(this) { updateStatus(it) }
        sdk.validationStatus.observe(this) { status ->
            val st = status ?: return@observe
            hintGate.update(st.code.name, st.hint)?.let { hintLabel.text = it }
        }
        sdk.error.observe(this) { error ->
            if (error != null) statusLabel.text = "Error: ${error.message ?: "Unknown"}"
        }
        sdk.metrics.observe(this) { metrics ->
            if (metrics == null) return@observe

            if (metrics.hasCardio()) {
                metrics.cardio.pulseRateList.lastOrNull { it.timestamp > 0 }?.value?.let { bpm ->
                    ring.pulseBpm = bpm
                    pulseLabel.text = "${bpm.roundToInt()}"
                    session.addPulse(bpm)
                }
                metrics.cardio.hrvList.lastOrNull()?.rmssd?.let { rmssd ->
                    if (rmssd > 0) {
                        val calm = CalmScore.fromRmssd(rmssd.toFloat())
                        ring.calm = calm
                        calmLabel.text = CalmScore.label(calm)
                        session.addCalm(calm)
                    }
                }
            }

            if (metrics.hasBreathing()) {
                if (metrics.breathing.rateCount > 0) {
                    breathLabel.text = "${metrics.breathing.rateList.last().value.roundToInt()}"
                }
                metrics.breathing.upperTraceList.forEach { s ->
                    if (s.timestamp > latestChestTimestamp) {
                        latestChestTimestamp = s.timestamp
                        ring.pushBreath(s.value)
                    }
                }
            }

            if (metrics.hasFace()) {
                val top = metrics.face.expressionList.lastOrNull()?.scoresList
                    ?.filter { it.confidence > 0f }?.maxByOrNull { it.confidence }
                moodLabel.text = moodGate.update(top?.type, top?.confidence ?: 0f)
            }
        }
    }

    private fun buildUi() {
        val root = FrameLayout(this).apply { setBackgroundColor(Color.BLACK) }

        val preview = PreviewView(this).apply {
            contentDescription = "Your mirror"
            scaleType = PreviewView.ScaleType.FILL_CENTER
            implementationMode = PreviewView.ImplementationMode.PERFORMANCE
        }
        sdk.config.previewSurfaceProvider = preview.surfaceProvider
        root.addView(preview, FrameLayout.LayoutParams(MATCH, MATCH))

        ring = PulseRingView(this)
        root.addView(ring, FrameLayout.LayoutParams(MATCH, MATCH))

        // Top: status + start/stop
        val top = row().apply { setPadding(dp(16), dp(12), dp(16), 0) }
        statusLabel = pill("Idle")
        toggleButton = Button(this).apply {
            text = "Start"
            isAllCaps = false
            setTextColor(Color.BLACK)
            backgroundTintList = ColorStateList.valueOf(Color.WHITE)
            setOnClickListener { toggle() }
        }
        top.addView(statusLabel, LinearLayout.LayoutParams(0, dp(40), 1f).apply { marginEnd = dp(8) })
        top.addView(toggleButton, LinearLayout.LayoutParams(dp(96), dp(44)))
        root.addView(top, FrameLayout.LayoutParams(MATCH, WRAP, Gravity.TOP))

        // Bottom: hint + three readouts
        val bottom = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            setPadding(dp(16), 0, dp(16), dp(16))
        }
        hintLabel = text("", 14f, 0xCCFFFFFF.toInt()).apply { gravity = Gravity.CENTER }
        bottom.addView(hintLabel, LinearLayout.LayoutParams(MATCH, WRAP).apply { bottomMargin = dp(10) })

        pulseLabel = big(CORAL); breathLabel = big(TEAL); calmLabel = big(MINT, 20f)
        val r1 = row()
        r1.addView(card("Pulse  bpm", pulseLabel, CORAL), weight(dp(8)))
        r1.addView(card("Breaths / min", breathLabel, TEAL), weight(dp(8)))
        r1.addView(card("Calm", calmLabel, MINT), weight())
        bottom.addView(r1)
        moodLabel = text("--", 14f, 0xB3FFFFFF.toInt()).apply { gravity = Gravity.CENTER }
        bottom.addView(moodLabel, LinearLayout.LayoutParams(MATCH, WRAP).apply { topMargin = dp(8) })
        root.addView(bottom, FrameLayout.LayoutParams(MATCH, WRAP, Gravity.BOTTOM))

        ViewCompat.setOnApplyWindowInsetsListener(root) { _, insets ->
            val bars = insets.getInsets(WindowInsetsCompat.Type.systemBars())
            top.setPadding(dp(16) + bars.left, dp(12) + bars.top, dp(16) + bars.right, 0)
            bottom.setPadding(dp(16) + bars.left, 0, dp(16) + bars.right, dp(16) + bars.bottom)
            insets
        }
        setContentView(root)
        ViewCompat.requestApplyInsets(root)
    }

    private fun toggle() {
        when (sdk.processingStatus.value) {
            ProcessingStatus.RUNNING -> lifecycleScope.launch { runCatching { sdk.stop() } }
            ProcessingStatus.STARTING, ProcessingStatus.STOPPING -> Unit
            else -> startProcessing()
        }
    }

    private fun startProcessing() {
        if (ContextCompat.checkSelfPermission(this, Manifest.permission.CAMERA)
            != PackageManager.PERMISSION_GRANTED
        ) {
            cameraPermissionLauncher.launch(Manifest.permission.CAMERA)
            return
        }
        lifecycleScope.launch {
            resetUi()
            runCatching { sdk.start() }
                .onFailure { statusLabel.text = "Error: ${it.message ?: "Unknown"}" }
        }
    }

    private fun updateStatus(status: ProcessingStatus?) {
        when (status) {
            ProcessingStatus.IDLE -> {
                statusLabel.text = if (session.hasData()) session.summary() else "Idle"
                toggleButton.text = "Start"; toggleButton.isEnabled = true
                ring.running = false
            }
            ProcessingStatus.STARTING -> {
                statusLabel.text = "Tuning camera\u2026"
                toggleButton.text = "\u2026"; toggleButton.isEnabled = false
            }
            ProcessingStatus.RUNNING -> {
                statusLabel.text = "Mirror on"
                toggleButton.text = "Stop"; toggleButton.isEnabled = true
                ring.running = true
            }
            ProcessingStatus.STOPPING -> {
                statusLabel.text = "Stopping\u2026"
                toggleButton.isEnabled = false
            }
            ProcessingStatus.ERROR -> {
                toggleButton.text = "Start"; toggleButton.isEnabled = true
                ring.running = false
            }
            null -> Unit
        }
    }

    private fun resetUi() {
        latestChestTimestamp = Long.MIN_VALUE
        session.reset()
        moodGate.reset()
        hintGate.reset()
        ring.reset()
        pulseLabel.text = "--"; breathLabel.text = "--"; calmLabel.text = "--"
        moodLabel.text = "Pulse reads in ~12 s, breathing ~30 s, calm ~60 s"
        hintLabel.text = "Face the camera in even light"
    }

    private fun friendlyHint(code: String, sdkHint: String): String = when (code) {
        "OK" -> "Hold still \u2014 reading you"
        "NO_FACE_FOUND" -> "Look into the mirror"
        "MULTIPLE_FACES_FOUND" -> "One face at a time, please"
        "FACE_NOT_CENTERED", "FACE_TOO_HIGH", "FACE_TOO_LOW" -> "Center your face in the ring"
        "FACE_TOO_CLOSE" -> "Lean back a little"
        "FACE_TOO_FAR" -> "Come a little closer"
        "TOO_DARK" -> "Find a bit more light"
        "TOO_BRIGHT" -> "Too bright \u2014 try softer light"
        "EXCESSIVE_MOTION" -> "Hold still for a moment"
        "CAMERA_TUNING" -> "Tuning the camera\u2026"
        "FRAME_RATE_TOO_LOW" -> "Phone is busy \u2014 close other apps for a smoother read"
        else -> sdkHint.ifBlank { code.replace('_', ' ').lowercase() }
    }

    // ---- tiny view helpers ----
    private fun row() = LinearLayout(this).apply {
        orientation = LinearLayout.HORIZONTAL; gravity = Gravity.CENTER_VERTICAL
    }
    private fun text(t: String, sp: Float, color: Int) = TextView(this).apply {
        text = t; textSize = sp; setTextColor(color)
    }
    private fun big(color: Int, sp: Float = 28f) = text("--", sp, color).apply {
        typeface = android.graphics.Typeface.DEFAULT_BOLD
    }
    private fun pill(t: String) = text(t, 13f, Color.WHITE).apply {
        gravity = Gravity.CENTER_VERTICAL
        setPadding(dp(12), 0, dp(12), 0)
        background = GradientDrawable().apply { setColor(0x55000000); cornerRadius = dp(20).toFloat() }
    }
    private fun card(title: String, value: TextView, accent: Int) = LinearLayout(this).apply {
        orientation = LinearLayout.VERTICAL
        setPadding(dp(12), dp(8), dp(12), dp(10))
        background = GradientDrawable().apply {
            setColor(0xCC10141F.toInt()); cornerRadius = dp(18).toFloat()
            setStroke(dp(1), Color.argb(90, Color.red(accent), Color.green(accent), Color.blue(accent)))
        }
        addView(text(title, 11f, 0x99FFFFFF.toInt()))
        addView(value)
    }
    private fun weight(end: Int = 0) = LinearLayout.LayoutParams(0, WRAP, 1f).apply { marginEnd = end }
    private fun dp(v: Int) = (v * resources.displayMetrics.density).roundToInt()

    private companion object {
        const val MATCH = ViewGroup.LayoutParams.MATCH_PARENT
        const val WRAP = ViewGroup.LayoutParams.WRAP_CONTENT
        const val CORAL = 0xFFFF6B6B.toInt()
        const val TEAL = 0xFF4FCCC4.toInt()
        const val MINT = 0xFF6EE7B7.toInt()
    }
}
