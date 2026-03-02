package me.chetan.indoornavigation

import java.util.PriorityQueue
import kotlin.math.abs
import kotlin.math.sqrt

data class GeoLocation(val long: Double, val lat: Double, val alt: Double, val name: String = "") {
    override fun equals(other: Any?): Boolean {
        if (this === other) return true
        if (javaClass != other?.javaClass) return false

        other as GeoLocation

        if (long != other.long) return false
        if (lat != other.lat) return false
        if (alt != other.alt) return false

        return true
    }

    override fun hashCode(): Int {
        var result = long.hashCode()
        result = 31 * result + lat.hashCode()
        result = 31 * result + alt.hashCode()
        return result
    }
}

class PathFind(val graph: Map<GeoLocation, MutableList<GeoLocation>>) {
    fun insertPointInGraph(mutGraph: MutableMap<GeoLocation, MutableList<GeoLocation>>, point: GeoLocation){
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
            return
        }

        val pointToInsert = interpolate_onto_line(point, edgeStart, edgeEnd) ?: return

        if(pointToInsert==edgeStart || pointToInsert==edgeEnd){
            return
        }

        mutGraph[pointToInsert]= mutableListOf(edgeStart,edgeEnd)

        mutGraph[edgeStart]?.remove(edgeEnd)
        mutGraph[edgeEnd]?.remove(edgeStart)

        mutGraph[edgeStart]?.add(pointToInsert)
        mutGraph[edgeEnd]?.add(pointToInsert)
    }

    fun route(start: GeoLocation,goal: GeoLocation): List<GeoLocation> {
        val mutGraph = graph.toMutableMap()
        insertPointInGraph(mutGraph,start)
        insertPointInGraph(mutGraph,goal)

        val minQ = PriorityQueue<Pair<Double, List<GeoLocation>>>()
        minQ.add(0.0 to listOf(start))
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

    fun pos(device: String): GeoLocation?{
        return mapOf(
            "2D:7E:1A:02:3D:21" to GeoLocation(0.0,0.0,0.0),
            "55:6D:EA:22:2C:71" to GeoLocation(0.0,0.0,0.0)
        )[device] // TODO: get pos of each device based on their device ID
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