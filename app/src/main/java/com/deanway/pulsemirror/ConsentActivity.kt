package com.deanway.pulsemirror

import android.content.Intent
import android.graphics.Color
import android.graphics.drawable.GradientDrawable
import android.os.Bundle
import android.view.Gravity
import android.view.ViewGroup
import android.widget.Button
import android.widget.LinearLayout
import android.widget.ScrollView
import android.widget.TextView
import androidx.activity.ComponentActivity
import kotlin.math.roundToInt

/**
 * Consent gate. The camera never starts until the user taps "I agree".
 * This screen is also the Consent & Data Handling disclosure required by the challenge.
 */
class ConsentActivity : ComponentActivity() {

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        val bg = GradientDrawable(
            GradientDrawable.Orientation.TOP_BOTTOM,
            intArrayOf(0xFF0B1020.toInt(), 0xFF05070C.toInt()),
        )

        val scroll = ScrollView(this).apply { background = bg }
        val col = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            setPadding(dp(24), dp(48), dp(24), dp(32))
        }
        scroll.addView(col)

        col.addView(title("Pulse Mirror"))
        col.addView(body(
            "Hold still and look at the front camera. Pulse Mirror reads your pulse, " +
            "breathing, and heart-rate variability from tiny changes in your face, and " +
            "reflects your calm/stress balance back to you in real time.",
            top = dp(12),
        ))

        col.addView(heading("Camera consent"))
        col.addView(body(
            "The camera turns on only after you tap \"I agree\" below and grant the Android " +
            "camera permission. Nothing is measured before that.",
        ))

        col.addView(heading("What this app captures"))
        col.addView(body(
            "\u2022 Live front-camera frames, used on-device to compute pulse, breathing rate, " +
            "HRV, and facial expression.\n" +
            "\u2022 It does not record, save, or upload your video. Image output is turned off.",
        ))

        col.addView(heading("Where data goes"))
        col.addView(body(
            "Vitals are computed on your phone by the Presage SmartSpectra SDK. The app uses the " +
            "internet only so the SDK can check its API key with Presage over an encrypted " +
            "connection. The SDK can also send diagnostic telemetry (SDK version, device model, " +
            "performance and quality counts, never your video or vital values). Pulse Mirror " +
            "turns that telemetry OFF.",
        ))

        col.addView(heading("Storage, retention, deletion"))
        col.addView(body(
            "Pulse Mirror stores nothing on your phone and keeps no account. Your readings live " +
            "only on screen and are gone when you close the app. Presage's handling of anything " +
            "its service receives is governed by its own privacy policy.",
        ))

        col.addView(heading("AI services"))
        col.addView(body(
            "Pulse Mirror does not use the SDK's optional LLM insights, so no data is sent to any " +
            "AI service.",
        ))

        col.addView(heading("Not a medical device"))
        col.addView(body(
            "Pulse Mirror is for curiosity and relaxation only. It does not diagnose, treat, or " +
            "monitor any medical condition. Do not use it for medical decisions.",
        ))

        val agree = Button(this).apply {
            text = "I agree \u2014 start the mirror"
            isAllCaps = false
            textSize = 16f
            setTextColor(Color.BLACK)
            background = GradientDrawable().apply {
                setColor(0xFF4FCCC4.toInt())
                cornerRadius = dp(16).toFloat()
            }
            setPadding(dp(16), dp(16), dp(16), dp(16))
            setOnClickListener {
                startActivity(Intent(this@ConsentActivity, MirrorActivity::class.java))
            }
        }
        col.addView(agree, LinearLayout.LayoutParams(
            ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT,
        ).apply { topMargin = dp(28) })

        setContentView(scroll)
    }

    private fun title(text: String) = TextView(this).apply {
        this.text = text
        textSize = 30f
        setTextColor(Color.WHITE)
        typeface = android.graphics.Typeface.DEFAULT_BOLD
    }

    private fun heading(text: String) = TextView(this).apply {
        this.text = text
        textSize = 17f
        setTextColor(0xFF4FCCC4.toInt())
        typeface = android.graphics.Typeface.DEFAULT_BOLD
        setPadding(0, dp(20), 0, dp(6))
    }

    private fun body(text: String, top: Int = 0) = TextView(this).apply {
        this.text = text
        textSize = 15f
        setTextColor(0xCCFFFFFF.toInt())
        setLineSpacing(dp(4).toFloat(), 1f)
        setPadding(0, top, 0, 0)
    }

    private fun dp(value: Int): Int = (value * resources.displayMetrics.density).roundToInt()
}
