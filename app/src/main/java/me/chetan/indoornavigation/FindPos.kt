package me.chetan.indoornavigation

import java.math.BigDecimal
import kotlin.math.hypot
import kotlin.math.max
import kotlin.math.min

data class Vec2(val x: Double, val y: Double)

object LineConstrainedTrilateration {
    fun estimate(vecA: Vec2, vecB: Vec2, d1: BigDecimal, d2: BigDecimal): Vec2 {
        val dx = vecB.x - vecA.x
        val dy = vecB.y - vecA.y
        val d = hypot(dx, dy)

        require(d > 1e-9) { "Anchors must not be coincident" }

        // Closed-form least-squares solution
        val tStar = (d1.toDouble() - d2.toDouble() + d) / (2.0 * d)

        // Clamp to line segment
        val t = min(1.0, max(0.0, tStar))

        return Vec2(
            x = vecA.x + t * dx,
            y = vecA.y + t * dy
        )
    }
}
