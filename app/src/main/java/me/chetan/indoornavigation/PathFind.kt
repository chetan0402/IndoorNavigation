package me.chetan.indoornavigation

import kotlin.math.abs
import kotlin.math.sqrt

data class GeoLocation(var long: Double, var lat: Double, var alt: Double, val name: String = "")

class PathFind(val graph: Map<String,List<GeoLocation>>) {
    fun route(start: GeoLocation,goal: GeoLocation): List<GeoLocation> {
        return listOf()
    }

    fun pos(device: String): GeoLocation{
        return GeoLocation(0.0,0.0,0.0) // TODO: get pos of each device based on their device ID
    }

    fun loc(devices: Map<String, Double>): GeoLocation{
        val anchors = mutableListOf<GeoLocation>()
        val distances = mutableListOf<Double>()

        for (device in devices){
            anchors.add(pos(device.key))
            distances.add(device.value)
        }

        return trilaterate(anchors,distances)
    }

    fun trilaterate(
        anchors: List<GeoLocation>,
        distances: List<Double>,
        iterations: Int = 100
    ): GeoLocation {

        require(anchors.size == distances.size)
        require(anchors.size >= 3)

        // Initial guess: centroid of anchors
        var x = anchors.map { it.long }.average()
        var y = anchors.map { it.lat }.average()
        var z = anchors.map { it.alt }.average()

        var lambda = 1e-3

        repeat(iterations) {

            val residuals = DoubleArray(anchors.size)
            val jacobian = Array(anchors.size) { DoubleArray(3) }

            for (i in anchors.indices) {
                val dx = x - anchors[i].long
                val dy = y - anchors[i].lat
                val dz = z - anchors[i].alt

                val r = sqrt(dx*dx + dy*dy + dz*dz).coerceAtLeast(1e-9)

                residuals[i] = r - distances[i]

                jacobian[i][0] = dx / r
                jacobian[i][1] = dy / r
                jacobian[i][2] = dz / r
            }

            // Compute JTJ and JTf
            val jtj = Array(3) { DoubleArray(3) }
            val jtf = DoubleArray(3)

            for (i in anchors.indices) {
                for (j in 0..2) {
                    jtf[j] += jacobian[i][j] * residuals[i]
                    for (k in 0..2) {
                        jtj[j][k] += jacobian[i][j] * jacobian[i][k]
                    }
                }
            }

            // Levenberg–Marquardt damping
            for (i in 0..2) jtj[i][i] += lambda

            val delta = solve3x3(jtj, doubleArrayOf(
                -jtf[0], -jtf[1], -jtf[2]
            ))

            x += delta[0]
            y += delta[1]
            z += delta[2]

            if (norm(delta) < 1e-6) return GeoLocation(x, y, z)
        }

        return GeoLocation(x, y, z)
    }

    private fun norm(v: DoubleArray): Double =
        sqrt(v[0]*v[0] + v[1]*v[1] + v[2]*v[2])

    private fun solve3x3(A: Array<DoubleArray>, b: DoubleArray): DoubleArray {

        val detA =
            A[0][0]*(A[1][1]*A[2][2] - A[1][2]*A[2][1]) -
                    A[0][1]*(A[1][0]*A[2][2] - A[1][2]*A[2][0]) +
                    A[0][2]*(A[1][0]*A[2][1] - A[1][1]*A[2][0])

        if (abs(detA) < 1e-12) return doubleArrayOf(0.0, 0.0, 0.0)

        fun detReplace(col: Int): Double {
            val m = Array(3) { i ->
                DoubleArray(3) { j -> if (j == col) b[i] else A[i][j] }
            }
            return m[0][0]*(m[1][1]*m[2][2] - m[1][2]*m[2][1]) -
                    m[0][1]*(m[1][0]*m[2][2] - m[1][2]*m[2][0]) +
                    m[0][2]*(m[1][0]*m[2][1] - m[1][1]*m[2][0])
        }

        return doubleArrayOf(
            detReplace(0) / detA,
            detReplace(1) / detA,
            detReplace(2) / detA
        )
    }
}