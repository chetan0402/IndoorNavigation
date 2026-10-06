package me.chetan.indoornavigation

import kotlin.math.pow

class RSSIDistancePredictor(
    private val alpha: Double = 0.2,
    private val txPower: Double = -72.0,
    private val pathLossExponent: Double = 2.4
) {
    private var smoothedRssi: Double? = null

    constructor(windowSize: Int, txPower: Double = -72.0, pathLossExponent: Double = 2.4) : this(
        alpha = 2.0 / (windowSize + 1.0),
        txPower = txPower,
        pathLossExponent = pathLossExponent
    )

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
