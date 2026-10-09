package me.chetan.indoornavigation

import me.chetan.indoornavigation.data.FilterEstimate
import me.chetan.indoornavigation.data.Measurement
import org.json.JSONObject
import org.junit.Test
import java.io.File
import kotlin.math.absoluteValue
import kotlin.math.pow
import kotlin.math.sqrt

class ParticleFilterMetric {
    @Test
    fun metric20261005_094042(){
        val particleFilter = ParticleFilter()

        val distancePredictors = mutableMapOf<String, RSSIDistancePredictor>()

        val anchors = mapOf(
            "2D:7E:1A:02:3D:21" to Triple(0.0, 0.0, 0.0),
            "55:6D:EA:22:2C:71" to Triple(-15.39,0.0,0.0),
            "A8:A4:B8:87:69:A0" to Triple(-15.39,-13.8,0.0),
            "68:1A:9E:8C:E2:CB" to Triple(-15.39,-27.6,0.0)
        )

        val actualPosition: (Long) -> Triple<Double, Double, Double> = { time ->
            val testAbsStart = 1791173442630L
            val testAbsEnd = 1791173474566L
            val actualStart = testAbsStart + 2000
            val testDuration = testAbsEnd - actualStart
            val distanceInSegment1 = 15.39
            val distanceInSegment2 = 27.6
            val timeForSegment1 = (distanceInSegment1) * testDuration / (distanceInSegment1 + distanceInSegment2)
            val timeForSegment2 = testDuration - timeForSegment1
            val segment1End = actualStart + timeForSegment1
            val segment2End = segment1End + timeForSegment2
            when {
                time < actualStart -> Triple(0.0, 0.0, 0.0)
                time < segment1End -> {
                    val progress = (time - actualStart) / timeForSegment1
                    Triple(progress * -distanceInSegment1, 0.0, 0.0)
                }
                time < segment2End -> {
                    val progress = (time - segment1End) / timeForSegment2
                    Triple(-15.39, progress * -distanceInSegment2, 0.0)
                }
                else -> Triple(-15.39, -27.6, 0.0)
            }
        }

        var meanError = 0.0
        var meanErrorInterpolated=0.0
        var cnt=0

        val currentWorkingDirectory = System.getProperty("user.dir")
        println("Current Working Directory: $currentWorkingDirectory")

        File("BLE_Telemetry_20261005_094042.json").useLines { lines ->
            lines.forEach { line ->
                val json = JSONObject(line)
                val stepTriggered = json.getBoolean("step_triggered")
                if (stepTriggered) {
                    particleFilter.predict(1.0, 0.1)
                }else{
                    val bleRssis = json.getJSONObject("ble_rssis")
                    val measurements = mutableListOf<Measurement>()
                    for (ble in bleRssis.keys()) {
                        val predictor = distancePredictors.getOrPut(
                            ble
                        ) { RSSIDistancePredictor() }
                        val distance=predictor.predict(bleRssis.getDouble(ble))
                        measurements.add(Measurement(anchors[ble]!!.first,anchors[ble]!!.second,anchors[ble]!!.third,distance,5.0))
                    }
                    particleFilter.update(measurements)
                }
                val estimate=particleFilter.estimate()
                val estimateInterpolated=interpolate(estimate,anchors)
                val time=json.getLong("timestamp")
                val actual=actualPosition(time)
                val error=sqrt(
                    (estimate.x - actual.first).pow(2.0)+
                            (estimate.y - actual.second).pow(2.0)+
                            (estimate.z - actual.third).pow(2.0)
                )
                val errorInterpolated=sqrt(
                    (estimateInterpolated.first - actual.first).pow(2.0)+
                            (estimateInterpolated.second - actual.second).pow(2.0)+
                            (estimateInterpolated.third - actual.third).pow(2.0)
                )
                meanError+=error.absoluteValue
                meanErrorInterpolated+=errorInterpolated.absoluteValue
                cnt++
            }
        }
        meanError/=cnt
        meanErrorInterpolated/=cnt
        println("Mean Error: $meanError")
        println("Mean Error Interpolated: $meanErrorInterpolated")
    }

    fun interpolate(pos: FilterEstimate, anchors: Map<String, Triple<Double, Double, Double>>): Triple<Double, Double, Double> {
        val x = pos.x
        val y = pos.y
        val z = pos.z

        val distances = mutableListOf<Pair<Double, Triple<Double, Double, Double>>>()

        for (anchor in anchors) {
            val distance = sqrt(
                (anchor.value.first - x).pow(2) +
                        (anchor.value.second - y).pow(2) +
                        (anchor.value.third - z).pow(2)
            )
            distances.add(Pair(distance, anchor.value))
        }

        distances.sortBy { it.first }

        val anchor1 = distances[0].second
        val anchor2 = distances[1].second
        val distance1 = distances[0].first
        val distance2 = distances[1].first

        val ratio = distance1 / (distance1 + distance2)

        val interpolatedX = anchor1.first * (1.0 - ratio) + anchor2.first * ratio
        val interpolatedY = anchor1.second * (1.0 - ratio) + anchor2.second * ratio
        val interpolatedZ = anchor1.third * (1.0 - ratio) + anchor2.third * ratio

        return Triple(interpolatedX, interpolatedY, interpolatedZ)
    }
}