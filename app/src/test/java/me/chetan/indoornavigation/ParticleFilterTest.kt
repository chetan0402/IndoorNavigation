package me.chetan.indoornavigation

import me.chetan.indoornavigation.data.Measurement
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import kotlin.math.PI
import kotlin.math.cos
import kotlin.math.pow
import kotlin.math.sin
import kotlin.math.sqrt

class ParticleFilterTest {

    @Test
    fun testHeadingEstimation() {
        val filter = ParticleFilter(numParticles = 5000)
        filter.setBounds(0.0, 0.0, 0.0, 100.0, 100.0, 2.0)
        
        val trueHeadingOffset = -PI / 2.0
        val phoneAzimuth = PI / 2.0
        var currentX = 10.0
        var currentY = 10.0
        val trueZ = 0.5
        
        val anchors = listOf(
            Triple(0.0, 0.0, 0.0),
            Triple(100.0, 0.0, 0.0),
            Triple(0.0, 100.0, 0.0),
            Triple(100.0, 100.0, 0.0)
        )

        // 1. Initial convergence: many updates at the starting position to pin the location
        for (i in 1..50) {
            val measurements = anchors.map { anchor ->
                val dist = sqrt((currentX - anchor.first).pow(2) + (currentY - anchor.second).pow(2) + (trueZ - anchor.third).pow(2))
                Measurement(anchor.first, anchor.second, anchor.third, dist, 0.01)
            }
            filter.update(measurements)
        }

        // 2. Movement: simulate walking to learn the heading offset
        for (stepNum in 1..50) {
            filter.predict(step = 1.0, phoneAzimuth = phoneAzimuth, variance = 0.01)
            
            val walkingDir = phoneAzimuth + trueHeadingOffset
            currentX += 1.0 * cos(walkingDir)
            currentY += 1.0 * sin(walkingDir)
            
            val measurements = anchors.map { anchor ->
                val dist = sqrt((currentX - anchor.first).pow(2) + (currentY - anchor.second).pow(2) + (trueZ - anchor.third).pow(2))
                Measurement(anchor.first, anchor.second, anchor.third, dist, 0.01)
            }
            filter.update(measurements)
        }
        
        val estimate = filter.estimate()
        
        assertEquals("X should converge", currentX, estimate.x, 5.0)
        assertEquals("Y should converge", currentY, estimate.y, 5.0)
        
        val angleDiff = Math.atan2(sin(estimate.headingOffset - trueHeadingOffset), cos(estimate.headingOffset - trueHeadingOffset))
        assertTrue("Heading offset should converge (true=$trueHeadingOffset, est=${estimate.headingOffset}, diff=$angleDiff)", Math.abs(angleDiff) < 0.5)
    }

    @Test
    fun testSoftBounds() {
        val filter = ParticleFilter(numParticles = 100)
        // Set initial bounds
        filter.setBounds(0.0, 0.0, 0.0, 10.0, 10.0, 1.0)
        
        // Move particles partially out of bounds
        filter.predict(step = 15.0, phoneAzimuth = 0.0, variance = 0.0)
        
        // Update with a measurement. Particles at X=15 are out of bounds [0, 10]
        // Weight sum will be 0, triggering re-initialization within [0, 10]
        filter.update(5.0, 0.0, 0.0, 0.0, 1.0)
        
        val estimate = filter.estimate()
        assertTrue("Particles should be re-initialized within bounds [0, 10], but was ${estimate.x}", estimate.x in 0.0..10.0)
    }

    @Test
    fun testBatchUpdate() {
        val filter = ParticleFilter(numParticles = 1000)
        filter.setBounds(0.0, 0.0, 0.0, 20.0, 20.0, 2.0)

        val targetX = 8.0
        val targetY = 12.0
        val targetZ = 0.5

        val anchors = listOf(
            Triple(0.0, 0.0, 0.0),
            Triple(20.0, 0.0, 0.0),
            Triple(0.0, 20.0, 0.0),
            Triple(20.0, 20.0, 0.0)
        )

        val measurements = anchors.map { anchor ->
            val dist = sqrt(
                (targetX - anchor.first).pow(2.0) +
                        (targetY - anchor.second).pow(2.0) +
                        (targetZ - anchor.third).pow(2.0)
            )
            Measurement(anchor.first, anchor.second, anchor.third, dist, variance = 0.05)
        }

        // Perform batch updates
        for (i in 1..300) {
            // Predict with zero motion but variance to help exploration
            filter.predict(step = 0.0, phoneAzimuth = 0.0, variance = 0.5)
            filter.update(measurements)
        }

        val estimate = filter.estimate()

        assertEquals("X should converge to 8.0", 8.0, estimate.x, 1.0)
        assertEquals("Y should converge to 12.0", 12.0, estimate.y, 1.0)
        assertEquals("Z should converge to 0.5", 0.5, estimate.z, 0.5)
    }

    @Test
    fun testUpdateConverges() {
        val filter = ParticleFilter(numParticles = 1000)
        filter.setBounds(0.0, 0.0, 0.0, 20.0, 20.0, 2.0)
        
        val targetX = 5.0
        val targetY = 5.0
        val targetZ = 0.0
        
        val anchors = listOf(
            Triple(0.0, 0.0, 0.0),
            Triple(10.0, 0.0, 0.0),
            Triple(0.0, 10.0, 0.0),
            Triple(5.0, 5.0, 5.0)
        )
        
        for (iteration in 1..200) {
            // Predict with zero motion but variance to help exploration
            filter.predict(step = 0.0, phoneAzimuth = 0.0, variance = 0.5)
            for (anchor in anchors) {
                val dist = sqrt(
                    (targetX - anchor.first).pow(2.0) +
                            (targetY - anchor.second).pow(2.0) +
                            (targetZ - anchor.third).pow(2.0)
                )
                filter.update(anchor.first, anchor.second, anchor.third, dist, variance = 0.05)
            }
        }
        
        val estimate = filter.estimate()
        
        assertEquals("X should converge to 5.0", 5.0, estimate.x, 1.0)
        assertEquals("Y should converge to 5.0", 5.0, estimate.y, 1.0)
        assertEquals("Z should converge to 0.0", 0.0, estimate.z, 0.5)
    }
}
