package me.chetan.indoornavigation.data

import kotlin.math.round

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
