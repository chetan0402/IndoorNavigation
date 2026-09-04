package me.chetan.indoornavigation

import me.chetan.indoornavigation.data.FilterEstimate
import me.chetan.indoornavigation.data.Measurement
import me.chetan.indoornavigation.data.Particle
import java.util.Random
import kotlin.math.PI
import kotlin.math.atan2
import kotlin.math.cos
import kotlin.math.exp
import kotlin.math.ln
import kotlin.math.pow
import kotlin.math.sin
import kotlin.math.sqrt

class ParticleFilter(private val numParticles: Int = 1000) {
    private var particles = mutableListOf<Particle>()
    private val random = Random()

    private var xMin = -100.0
    private var xMax = 100.0
    private var yMin = -100.0
    private var yMax = 100.0
    private var zMin = -10.0
    private var zMax = 10.0

    init {
        initializeParticles()
    }

    private fun initializeParticles() {
        particles.clear()
        for (i in 0 until numParticles) {
            particles.add(
                Particle(
                    x = random.nextDouble() * (xMax - xMin) + xMin,
                    y = random.nextDouble() * (yMax - yMin) + yMin,
                    z = random.nextDouble() * (zMax - zMin) + zMin,
                    headingOffset = random.nextDouble() * 2.0 * PI,
                    weight = 1.0 / numParticles
                )
            )
        }
    }

    /**
     * Updates boundaries. Soft boundary handling: particles are not immediately moved,
     * but will be penalized in the update step.
     */
    fun setBounds(xMin: Double, yMin: Double, zMin: Double, xMax: Double, yMax: Double, zMax: Double) {
        this.xMin = xMin
        this.yMin = yMin
        this.zMin = zMin
        this.xMax = xMax
        this.yMax = yMax
        this.zMax = zMax
    }

    /**
     * Prediction step: Moves particles based on step length and direction with noise.
     * Incorporates the particle's internal heading offset.
     */
    fun predict(step: Double, phoneAzimuth: Double, variance: Double) {
        val std = sqrt(variance)
        for (particle in particles) {
            val noisyStep = step + random.nextGaussian() * std
            val walkingDirection = phoneAzimuth + particle.headingOffset + random.nextGaussian() * 0.1

            particle.x += noisyStep * cos(walkingDirection)
            particle.y += noisyStep * sin(walkingDirection)
            particle.z += random.nextGaussian() * 0.05
            
            // Allow heading offset to drift slightly to adapt to changes
            particle.headingOffset += random.nextGaussian() * 0.02
        }
    }

    /**
     * Update step: Supports multiple measurements. Particles out of bounds get zero weight.
     */
    fun update(measurements: List<Measurement>) {
        if (measurements.isEmpty()) return

        var sumWeights = 0.0

        for (particle in particles) {
            // Soft boundary check
            if (particle.x !in xMin..xMax || particle.y !in yMin..yMax || particle.z !in zMin..zMax) {
                particle.weight = 0.0
                continue
            }

            var totalLogLikelihood = 0.0
            for (m in measurements) {
                val dist = sqrt(
                    (particle.x - m.x).pow(2) +
                            (particle.y - m.y).pow(2) +
                            (particle.z - m.z).pow(2)
                )

                val std = sqrt(m.variance)
                // Use likelihood for weight update, handling multiple measurements via log-sum for stability
                val likelihood = exp(-0.5 * ((dist - m.radius) / std).pow(2)) / (std * sqrt(2.0 * PI))
                totalLogLikelihood += ln(likelihood.coerceAtLeast(1e-10))
            }

            particle.weight *= exp(totalLogLikelihood)
            sumWeights += particle.weight
        }

        // Normalize weights
        if (sumWeights > 0) {
            for (particle in particles) {
                particle.weight /= sumWeights
            }
            resample()
        } else {
            // If all particles died (out of bounds or weight collapse), re-initialize in new bounds
            initializeParticles()
        }
    }

    /**
     * Original single update preserved for convenience, but calls batch update internally.
     */
    fun update(anchorX: Double, anchorY: Double, anchorZ: Double, measuredRadius: Double, variance: Double) {
        update(listOf(Measurement(anchorX, anchorY, anchorZ, measuredRadius, variance)))
    }

    /**
     * Systematic Resampling
     */
    private fun resample() {
        val newParticles = mutableListOf<Particle>()
        val weights = particles.map { it.weight }
        val cumulativeWeights = DoubleArray(numParticles)
        cumulativeWeights[0] = weights[0]
        for (i in 1 until numParticles) {
            cumulativeWeights[i] = cumulativeWeights[i - 1] + weights[i]
        }

        val step = 1.0 / numParticles
        var u = random.nextDouble() * step
        var i = 0
        for (j in 0 until numParticles) {
            while (u > cumulativeWeights[i] && i < numParticles - 1) {
                i++
            }
            val p = particles[i]
            // Add jitter to resampled particles to maintain diversity, including heading offset
            newParticles.add(
                Particle(
                    x = (p.x + random.nextGaussian() * 0.2).coerceIn(xMin, xMax),
                    y = (p.y + random.nextGaussian() * 0.2).coerceIn(yMin, yMax),
                    z = (p.z + random.nextGaussian() * 0.05).coerceIn(zMin, zMax),
                    headingOffset = p.headingOffset + random.nextGaussian() * 0.05,
                    weight = 1.0 / numParticles
                )
            )
            u += step
        }
        particles = newParticles
    }

    /**
     * Returns the estimated position and heading offset as the weighted mean.
     */
    fun estimate(): FilterEstimate {
        var x = 0.0
        var y = 0.0
        var z = 0.0
        var sinHeading = 0.0
        var cosHeading = 0.0
        var totalWeight = 0.0
        
        for (p in particles) {
            x += p.x * p.weight
            y += p.y * p.weight
            z += p.z * p.weight
            
            // Average angles by averaging their unit vectors
            sinHeading += sin(p.headingOffset) * p.weight
            cosHeading += cos(p.headingOffset) * p.weight
            
            totalWeight += p.weight
        }
        
        return if (totalWeight > 0) {
            FilterEstimate(
                x = x / totalWeight,
                y = y / totalWeight,
                z = z / totalWeight,
                headingOffset = atan2(sinHeading, cosHeading)
            )
        } else {
            FilterEstimate(0.0, 0.0, 0.0, 0.0)
        }
    }
}
