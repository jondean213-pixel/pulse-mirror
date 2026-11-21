package com.deanway.pulsemirror

import android.os.SystemClock
import com.presagetech.smartspectra.proto.MetricsProto.ExpressionType

/**
 * Only shows an expression nudge when the same expression has held, with real confidence,
 * for a while. A resting face often reads as "angry" or "contempt" for a frame or two;
 * that should never become a permanent "Unclench your jaw".
 */
class MoodGate(
    private val holdMs: Long = 8_000,
    private val minConfidence: Float = 0.6f,
    private val showMs: Long = 6_000,
) {
    private var candidate: ExpressionType? = null
    private var since = 0L
    private var shownAt = 0L
    private var shown: String = ""

    fun reset() { candidate = null; since = 0L; shownAt = 0L; shown = "" }

    fun update(type: ExpressionType?, confidence: Float): String {
        val now = SystemClock.uptimeMillis()
        val t = if (confidence >= minConfidence) type else null
        if (t != candidate) { candidate = t; since = now }

        if (shown.isNotEmpty() && now - shownAt > showMs) shown = ""

        val held = now - since
        val needed = if (t == ExpressionType.HAPPY) 2_000L else holdMs
        if (t != null && held >= needed && shown.isEmpty()) {
            nudge(t)?.let { shown = it; shownAt = now; since = now }
        }
        return shown
    }

    private fun nudge(t: ExpressionType): String? = when (t) {
        ExpressionType.HAPPY -> "Nice smile"
        // A resting face often reads as angry/contempt/sad, so the app never
        // comments on those. It only celebrates a real smile.
        else -> null
    }
}

/**
 * Keeps coaching hints from flickering: a problem hint only appears after it persists
 * for a couple of seconds, while "OK" clears it right away.
 */
class HintGate(
    private val persistMs: Long = 2_500,
    private val format: (String, String) -> String,
) {
    private var code: String? = null
    private var since = 0L
    private var current: String? = null

    fun reset() { code = null; since = 0L; current = null }

    fun update(newCode: String, sdkHint: String): String? {
        val now = SystemClock.uptimeMillis()
        if (newCode != code) { code = newCode; since = now }
        val ready = newCode == "OK" || newCode == "CAMERA_TUNING" || now - since >= persistMs
        if (!ready) return null
        val text = format(newCode, sdkHint)
        if (text == current) return null
        current = text
        return text
    }
}
