package me.chetan.indoornavigation.data

enum class RouteStepType {
    WALK,
    TURN_LEFT,
    TURN_RIGHT,
    SLIGHT_LEFT,
    SLIGHT_RIGHT,
    STAIRS_UP,
    STAIRS_DOWN,
    ARRIVE
}

data class RouteStep(
    val stepIndex: Int,
    val type: RouteStepType,
    val instructionText: String,
    val distanceMeters: Double,
    val startPoint: GeoLocation,
    val endPoint: GeoLocation,
    val waypointName: String? = null
)
