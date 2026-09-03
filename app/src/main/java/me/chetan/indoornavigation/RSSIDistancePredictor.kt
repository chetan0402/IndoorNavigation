package me.chetan.indoornavigation

import java.util.ArrayDeque
import kotlin.math.max
import kotlin.math.min
import kotlin.math.roundToInt
import kotlin.math.sqrt

class RSSIDistancePredictor(
    private val windowSize: Int = 7
) {
    private val window = ArrayDeque<Double>(windowSize)
    private var smoothedDistance = 15.0
    private val alphaDistance = 0.45
    private val boostThreshold = -62.0

    // Kalman State: [rssi, velocity]
    private var x0 = -80.0
    private var x1 = 0.0

    // Covariance Matrix P
    private var p00 = 100.0; private var p01 = 0.0
    private var p10 = 0.0;   private var p11 = 1.0

    // Constants
    private val dt = 1.0
    private val r = 1.6927
    private val q00 = 0.01
    private val q11 = 0.5

    fun predict(newRssi: Double): Pair<Double, Double> {
        // --- 1. Kalman Predict ---
        // x = F * x
        val xPred0 = x0 + (dt * x1)
        val xPred1 = x1

        // P = F * P * F' + Q
        val pPred00 = p00 + dt * (p10 + p01 + dt * p11) + q00
        val pPred01 = p01 + dt * p11
        val pPred10 = p10 + dt * p11
        val pPred11 = p11 + q11

        // --- 2. Kalman Update ---
        // y = z - H * x
        val y = newRssi - xPred0

        // S = H * P * H' + R
        val s = pPred00 + r

        // K = P * H' * inv(S)
        val k0 = pPred00 / s
        val k1 = pPred10 / s

        // x = x + K * y
        x0 = xPred0 + k0 * y
        x1 = xPred1 + k1 * y

        // P = (I - K * H) * P
        p00 = (1.0 - k0) * pPred00
        p01 = (1.0 - k0) * pPred01
        p10 = -k1 * pPred00 + pPred10
        p11 = -k1 * pPred01 + pPred11

        val smoothedRssi = x0

        // --- 3. Distance Prediction (m2cgen integration) ---
        if (window.size >= windowSize) window.removeFirst()
        window.addLast(newRssi)

        var rawPred = Model.score(doubleArrayOf(smoothedRssi))
        rawPred = max(4.0, min(28.0, rawPred))

        // Applying your logic: Boost for weak signals
        if (smoothedRssi < boostThreshold) {
            val boost = 2.2 + (boostThreshold - smoothedRssi) * 0.18
            rawPred = max(5.0, rawPred - boost)
        }

        smoothedDistance = (alphaDistance * rawPred) + (1.0 - alphaDistance) * smoothedDistance

        // --- 4. Confidence Calculation ---
        val rssiStd = calculateStd(window.toList())
        val kalmanUncert = sqrt(p00)
        val confidence = max(0.15, 1.0 - (rssiStd / 15.0) - (kalmanUncert / 20.0))

        return Pair(
            (smoothedDistance * 100.0).roundToInt() / 100.0,
            (confidence * 100.0).roundToInt() / 100.0
        )
    }

    private fun calculateStd(list: List<Double>): Double {
        if (list.size < 2) return 0.0
        val mean = list.average()
        return sqrt(list.sumOf { (it - mean) * (it - mean) } / list.size)
    }
}