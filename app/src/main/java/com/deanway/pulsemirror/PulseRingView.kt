package com.deanway.pulsemirror

import android.content.Context
import android.graphics.Canvas
import android.graphics.Color
import android.graphics.Paint
import android.os.SystemClock
import android.view.View
import kotlin.math.exp
import kotlin.math.max
import kotlin.math.min

/**
 * Overlay drawn on the mirror.
 * - Ring: beats once per measured heartbeat (pulseBpm), a quick swell then a slow ease back.
 * - Halo: grows and shrinks with the live chest breathing trace.
 * - Color: shifts from warm coral (low calm) to mint (high calm).
 */
class PulseRingView(context: Context) : View(context) {

    var pulseBpm: Float = 0f
    var calm: Float = 0.5f
    var running: Boolean = false
        set(value) { field = value; invalidate() }

    private val breath = ArrayDeque<Float>()
    private val start = SystemClock.uptimeMillis()

    private val ringPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply { style = Paint.Style.STROKE }
    private val haloPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply { style = Paint.Style.FILL }

    fun pushBreath(v: Float) {
        breath.addLast(v)
        while (breath.size > 300) breath.removeFirst()
    }

    fun reset() {
        breath.clear(); pulseBpm = 0f; calm = 0.5f; invalidate()
    }

    override fun onDraw(canvas: Canvas) {
        super.onDraw(canvas)
        if (!running) return
        val cx = width / 2f
        val cy = height * 0.42f
        val base = min(width, height) * 0.36f
        val color = blend(0xFFFF6B6B.toInt(), 0xFF6EE7B7.toInt(), calm.coerceIn(0f, 1f))

        // Breathing halo, normalized over the recent window
        val b = breathLevel()
        haloPaint.color = Color.argb(40 + (60 * b).toInt(), Color.red(color), Color.green(color), Color.blue(color))
        canvas.drawCircle(cx, cy, base * (1.05f + 0.18f * b), haloPaint)

        // Heartbeat ring
        val beat = beatEnvelope()
        ringPaint.strokeWidth = 6f + 10f * beat
        ringPaint.color = Color.argb(150 + (105 * beat).toInt(), Color.red(color), Color.green(color), Color.blue(color))
        canvas.drawCircle(cx, cy, base * (1f + 0.06f * beat), ringPaint)

        postInvalidateOnAnimation()
    }

    /** 0..1 pulse shape repeating at pulseBpm. */
    private fun beatEnvelope(): Float {
        if (pulseBpm <= 0f) return 0f
        val periodMs = 60_000f / pulseBpm
        val phase = ((SystemClock.uptimeMillis() - start) % periodMs.toLong()) / periodMs
        return exp(-phase * 7f).toFloat()
    }

    private fun breathLevel(): Float {
        if (breath.size < 10) return 0f
        var lo = Float.POSITIVE_INFINITY; var hi = Float.NEGATIVE_INFINITY
        breath.forEach { lo = min(lo, it); hi = max(hi, it) }
        val range = if (hi - lo == 0f) 1f else hi - lo
        return ((breath.last() - lo) / range).coerceIn(0f, 1f)
    }

    private fun blend(a: Int, b: Int, t: Float): Int = Color.rgb(
        (Color.red(a) + (Color.red(b) - Color.red(a)) * t).toInt(),
        (Color.green(a) + (Color.green(b) - Color.green(a)) * t).toInt(),
        (Color.blue(a) + (Color.blue(b) - Color.blue(a)) * t).toInt(),
    )
}
