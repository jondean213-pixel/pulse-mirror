package com.deanway.pulsemirror

/**
 * A playful, non-medical "calm" score from HRV RMSSD.
 * Higher short-term HRV generally goes with a more relaxed state; we just map it to 0..1
 * for the mirror's color. Not a health metric and not shown as one.
 */
object CalmScore {
    private const val LOW_MS = 20f
    private const val HIGH_MS = 80f

    fun fromRmssd(rmssdMs: Float): Float =
        ((rmssdMs - LOW_MS) / (HIGH_MS - LOW_MS)).coerceIn(0f, 1f)

    fun label(score: Float): String = when {
        score >= 0.66f -> "Calm"
        score >= 0.33f -> "Steady"
        else -> "Wired"
    }
}

/** In-memory only. Cleared on every start; never written to disk. */
class SessionStats {
    private val pulses = mutableListOf<Float>()
    private val calms = mutableListOf<Float>()

    fun addPulse(v: Float) { pulses += v }
    fun addCalm(v: Float) { calms += v }
    fun hasData() = pulses.isNotEmpty()
    fun reset() { pulses.clear(); calms.clear() }

    fun summary(): String {
        val avg = pulses.average().toInt()
        val calmTxt = if (calms.isEmpty()) "" else " \u00b7 ${CalmScore.label(calms.average().toFloat())}"
        return "Last session: $avg bpm$calmTxt"
    }
}
