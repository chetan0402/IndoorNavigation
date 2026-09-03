package me.chetan.indoornavigation.data

import me.chetan.indoornavigation.RSSIDistancePredictor

data class DeviceScanInfo(
    val distance: Double,
    val predictor: RSSIDistancePredictor,
    val lastSeen: Long
)
