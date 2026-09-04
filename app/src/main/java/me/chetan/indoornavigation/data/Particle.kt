package me.chetan.indoornavigation.data

data class Particle(
    var x: Double,
    var y: Double,
    var z: Double,
    var headingOffset: Double = 0.0,
    var weight: Double = 1.0
)
