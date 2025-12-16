import java.math.BigDecimal
import kotlin.math.hypot
import kotlin.math.max
import kotlin.math.min

data class Vec2(val x: Double, val y: Double)

object LineConstrainedTrilateration {
    fun estimate(A: Vec2, B: Vec2, d1: BigDecimal, d2: BigDecimal): Vec2 {
        val dx = B.x - A.x
        val dy = B.y - A.y
        val D = hypot(dx, dy)

        require(D > 1e-9) { "Anchors must not be coincident" }

        // Closed-form least-squares solution
        val tStar = (d1 - d2 + D) / (2.0 * D)

        // Clamp to line segment
        val t = min(1.0, max(0.0, tStar))

        return Vec2(
            x = A.x + t * dx,
            y = A.y + t * dy
        )
    }
}
