package me.chetan.indoornavigation

import java.util.PriorityQueue
import kotlin.math.abs
import kotlin.math.round
import kotlin.math.sqrt

data class GeoLocation(val long: Double, val lat: Double, val alt: Double, val name: String = "") {
    override fun equals(other: Any?): Boolean {
        if (this === other) return true
        if (other !is GeoLocation) return false

        val precision = 10000.0
        return round(long * precision) == round(other.long * precision) &&
               round(lat * precision) == round(other.lat * precision) &&
               round(alt * precision) == round(other.alt * precision)
    }

    override fun hashCode(): Int {
        val precision = 10000.0
        var result = round(long * precision).hashCode()
        result = 31 * result + round(lat * precision).hashCode()
        result = 31 * result + round(alt * precision).hashCode()
        return result
    }
}

class PathFind(val graph: Map<GeoLocation, MutableList<GeoLocation>>) {
    fun insertPointInGraph(mutGraph: MutableMap<GeoLocation, MutableList<GeoLocation>>, point: GeoLocation): GeoLocation{
        var minDis= Double.MAX_VALUE
        var edgeStart: GeoLocation? = null
        var edgeEnd: GeoLocation? = null
        mutGraph.forEach { (u, adj) ->
            adj.forEach { v ->
                val dis=distance_from_line(point,u,v)
                if(dis<minDis){
                    minDis=dis
                    edgeStart=u
                    edgeEnd=v
                }
            }
        }

        if(edgeStart==null || edgeEnd==null){
            return point
        }

        val pointToInsert = interpolate_onto_line(point, edgeStart, edgeEnd)

        if(pointToInsert==edgeStart || pointToInsert==edgeEnd){
            return pointToInsert
        }

        mutGraph[pointToInsert]= mutableListOf(edgeStart,edgeEnd)

        mutGraph[edgeStart]?.remove(edgeEnd)
        mutGraph[edgeEnd]?.remove(edgeStart)

        mutGraph[edgeStart]?.add(pointToInsert)
        mutGraph[edgeEnd]?.add(pointToInsert)

        return pointToInsert
    }

    fun route(start: GeoLocation, goal: GeoLocation): List<GeoLocation> {
        val mutGraph = graph.mapValues { it.value.toMutableList() }.toMutableMap()
        val startPoint = insertPointInGraph(mutGraph, start)

        val minQ = PriorityQueue<Pair<Double, List<GeoLocation>>>(
            compareBy { it.first }
        )
        minQ.add(0.0 to listOf(startPoint))
        while(minQ.isNotEmpty()){
            val (dis, curRoute)=minQ.poll()!!
            if(curRoute.last()==goal) {
                return curRoute
            }

            mutGraph[curRoute.last()]?.forEach {
                val mutList=curRoute.toMutableList()
                mutList.add(it)
                minQ.add((dis+distance_between_point(curRoute.last(),it)) to mutList.toList())
            }
        }

        return listOf()
    }

    fun trilateratePlanar(
        anchors: List<GeoLocation>,
        distances: List<Double>
    ): GeoLocation {
        val p1 = anchors[0]
        val p2 = anchors[1]
        val p3 = anchors[2]

        // 1. Define Local Coordinate System (Basis Vectors)
        // Vector eX is the direction from p1 to p2
        val ex = doubleArrayOf(p2.long - p1.long, p2.lat - p1.lat, p2.alt - p1.alt)
        val d = sqrt(ex[0]*ex[0] + ex[1]*ex[1] + ex[2]*ex[2])
        for (i in 0..2) ex[i] /= d

        // Vector p31 is vector from p1 to p3
        val p31 = doubleArrayOf(p3.long - p1.long, p3.lat - p1.lat, p3.alt - p1.alt)

        // i is the signed magnitude of p31 projected onto ex
        val i = ex[0]*p31[0] + ex[1]*p31[1] + ex[2]*p31[2]

        // Vector ey is the direction perpendicular to ex in the plane of the 3 points
        val ey = doubleArrayOf(
            p31[0] - i * ex[0],
            p31[1] - i * ex[1],
            p31[2] - i * ex[2]
        )
        val j = sqrt(ey[0]*ey[0] + ey[1]*ey[1] + ey[2]*ey[2])
        for (k in 0..2) ey[k] /= j

        // 2. Solve for (x, y) in the local 2D plane
        // Equations:
        // x^2 + y^2 = d1^2
        // (x-d)^2 + y^2 = d2^2
        // (x-i)^2 + (y-j)^2 = d3^2

        val r1 = distances[0]
        val r2 = distances[1]
        val r3 = distances[2]

        val x = (r1 * r1 - r2 * r2 + d * d) / (2 * d)
        val y = (r1 * r1 - r3 * r3 + i * i + j * j) / (2 * j) - (i / j) * x

        // 3. Project back to Global 3D Coordinates
        return GeoLocation(
            long = p1.long + x * ex[0] + y * ey[0],
            lat = p1.lat + x * ex[1] + y * ey[1],
            alt = p1.alt + x * ex[2] + y * ey[2]
        )
    }

    fun trilaterate(
        anchors: List<GeoLocation>,
        distances: List<Double>,
        iterations: Int = 100
    ): GeoLocation {

        require(anchors.size == distances.size)

        if (anchors.size == 2) {
            val a1 = anchors[0]
            val a2 = anchors[1]
            val d1 = distances[0]
            val d2 = distances[1]

            val d = distance_between_point(a1, a2)
            if (d < 1e-9) return a1

            // 2D-based least-error position on the line connecting the two anchors.
            // This calculates the intersection of the radical axis with the line.
            val x = (d1 * d1 - d2 * d2 + d * d) / (2 * d)
            val ratio = x / d

            return GeoLocation(
                long = a1.long + ratio * (a2.long - a1.long),
                lat = a1.lat + ratio * (a2.lat - a1.lat),
                alt = a1.alt + ratio * (a2.alt - a1.alt)
            )
        }
        if(anchors.size == 3) return trilateratePlanar(anchors,distances)

        require(anchors.size >= 3)

        // Initial guess: centroid of anchors
        var x = anchors.map { it.long }.average()
        var y = anchors.map { it.lat }.average()
        var z = anchors.map { it.alt }.average()

        val lambda = 1e-3

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