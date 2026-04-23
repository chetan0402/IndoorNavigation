package me.chetan.indoornavigation

import kotlin.math.pow
import kotlin.math.sqrt

fun distance_from_line(point: GeoLocation, lineStart: GeoLocation?, lineEnd: GeoLocation?): Double {
    if (lineStart == null || lineEnd == null) return Double.MAX_VALUE
    val nearest = interpolate_onto_line(point, lineStart, lineEnd)
    return distance_between_point(point, nearest)
}

fun interpolate_onto_line(point: GeoLocation, lineStart: GeoLocation, lineEnd: GeoLocation): GeoLocation{

    val dx = lineEnd.long - lineStart.long
    val dy = lineEnd.lat - lineStart.lat
    val dz = lineEnd.alt - lineStart.alt

    // Squared magnitude of the line segment
    val magSq = dx * dx + dy * dy + dz * dz

    // Handle case where lineStart and lineEnd are the same point
    if (magSq == 0.0) return lineStart.copy()

    // Vector w = point - lineStart
    val wx = point.long - lineStart.long
    val wy = point.lat - lineStart.lat
    val wz = point.alt - lineStart.alt

    // Dot product (w · v) / |v|^2 to find the projection ratio t
    var t = (wx * dx + wy * dy + wz * dz) / magSq

    // Clamp t to [0, 1] to stay within the segment bounds
    t = t.coerceIn(0.0, 1.0)

    // Interpolate to find the nearest point
    return GeoLocation(
        long = lineStart.long + t * dx,
        lat = lineStart.lat + t * dy,
        alt = lineStart.alt + t * dz
    )
}

fun distance_between_point(point1: GeoLocation, point2: GeoLocation): Double{
    val dLong = point2.long - point1.long
    val dLat = point2.lat - point1.lat
    val dAlt = point2.alt - point1.alt
    return sqrt(dLong.pow(2.0) + dLat.pow(2.0) + dAlt.pow(2.0))
}