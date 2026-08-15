package com.example.smartlandmarks.utils

import androidx.annotation.ColorInt

/**
 * Maps a landmark score onto a red -> amber -> green ramp.
 *
 * Scores are computed server-side and the range differs per student key, so a fixed
 * scale would collapse every marker onto one colour for some keys. Instead the ramp is
 * stretched across whatever range is actually present in the loaded data.
 */
object ScoreColor {

    @ColorInt private const val LOW = 0xFFD32F2F.toInt()     // red
    @ColorInt private const val MID = 0xFFF9A825.toInt()     // amber
    @ColorInt private const val HIGH = 0xFF2E7D32.toInt()    // green

    @ColorInt
    fun forScore(score: Double, minScore: Double, maxScore: Double): Int {
        val fraction = normalise(score, minScore, maxScore)
        return if (fraction <= 0.5f) {
            blend(LOW, MID, fraction / 0.5f)
        } else {
            blend(MID, HIGH, (fraction - 0.5f) / 0.5f)
        }
    }

    /**
     * Blends two ARGB colours by a ratio. A ratio of 0.0 returns color1, 1.0 returns
     * color2. Manual bitwise implementation is used rather than ColorUtils so the
     * logic can be unit-tested without mocking the Android SDK.
     */
    private fun blend(color1: Int, color2: Int, ratio: Float): Int {
        val inverseRatio = 1f - ratio
        val a = (color1 shr 24 and 0xFF) * inverseRatio + (color2 shr 24 and 0xFF) * ratio
        val r = (color1 shr 16 and 0xFF) * inverseRatio + (color2 shr 16 and 0xFF) * ratio
        val g = (color1 shr 8 and 0xFF) * inverseRatio + (color2 shr 8 and 0xFF) * ratio
        val b = (color1 and 0xFF) * inverseRatio + (color2 and 0xFF) * ratio
        return (a.toInt() shl 24) or (r.toInt() shl 16) or (g.toInt() shl 8) or b.toInt()
    }

    /**
     * Returns 0f..1f. When every landmark shares one score the range is degenerate,
     * so everything sits at the middle of the ramp rather than dividing by zero.
     */
    fun normalise(score: Double, minScore: Double, maxScore: Double): Float {
        val span = maxScore - minScore
        if (span <= 0.0) return 0.5f
        return ((score - minScore) / span).coerceIn(0.0, 1.0).toFloat()
    }
}
