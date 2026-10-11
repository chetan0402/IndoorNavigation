package me.chetan.indoornavigation

import kotlin.math.pow

class RSSIDistancePredictor(
    private val alpha: Double = 0.2,
    private val txPower: Double = -68.0,
    private val pathLossExponent: Double = 2.5
) {
    private var smoothedRssi: Double? = null

    fun predict(newRssi: Double): Double {
        val current = smoothedRssi
        val smoothed = if (current == null) {
            newRssi
        } else {
            alpha * newRssi + (1.0 - alpha) * current
        }
        smoothedRssi = smoothed
        return 10.0.pow((txPower - smoothed) / (10.0 * pathLossExponent))
    }
}
