package me.chetan.indoornavigation.data

val BLE1 = "2D:7E:1A:02:3D:21"
val BLE2 = "55:6D:EA:22:2C:71"
val BLE3 = "68:1A:9E:8C:E2:CB"
val BLE4 = "25:B6:BE:A7:E6:47"

val ANCHORS = mapOf(
    BLE1 to GeoLocation(0.0, 0.0, 0.0, "BLE1"),
    BLE2 to GeoLocation(15.39, 0.0, 0.0, "BLE2"),
    BLE3 to GeoLocation(15.39, 13.8, 0.0, "BLE3"),
    BLE4 to GeoLocation(long = 15.39, 27.6, 0.0, "BLE4")
)

val POINT_A = GeoLocation(0.0, 0.0, 0.0, "Prof. Manish Panday")
val POINT_B = GeoLocation(8.86, 0.0, 0.0, "Prof. Bholanath Roy")

val TURN = GeoLocation(15.39, 0.0, 0.0, "Turn")
val POINT_C = GeoLocation(15.39, 7.8, 0.0, "Room 202")
val POINT_D = GeoLocation(15.39, 24.0, 0.0, "Programming lab 2")

val NAV_GRAPH = mapOf(
    POINT_A to mutableListOf(POINT_B),
    POINT_B to mutableListOf(POINT_A, TURN),
    TURN to mutableListOf(POINT_B, POINT_C),
    POINT_C to mutableListOf(TURN, POINT_D),
    POINT_D to mutableListOf(POINT_C)
)

const val SCAN_TIMEOUT_MS = 10_000L
