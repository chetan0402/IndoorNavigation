package me.chetan.indoornavigation

import me.chetan.indoornavigation.data.GeoLocation
import me.chetan.indoornavigation.data.RouteStepType
import org.junit.Assert.assertEquals
import org.junit.Test

class RouteInstructionGeneratorTest {

    @Test
    fun `test right turn instruction consistency`() {
        // Path: (0,0) -> (0,10) -> (10,10) [Walking North, turning East = Turning RIGHT]
        val p1 = GeoLocation(0.0, 0.0, 0.0, "Start")
        val p2 = GeoLocation(0.0, 10.0, 0.0, "Corner")
        val p3 = GeoLocation(10.0, 10.0, 0.0, "Destination")

        val route = listOf(p1, p2, p3)
        val steps = RouteInstructionGenerator.generateSteps(route)

        // Step 0: WALK straight
        // Step 1: TURN_RIGHT
        assertEquals(RouteStepType.TURN_RIGHT, steps[1].type)
    }

    @Test
    fun `test left turn instruction consistency`() {
        // Path: (0,0) -> (0,10) -> (-10,10) [Walking North, turning West = Turning LEFT]
        val p1 = GeoLocation(0.0, 0.0, 0.0, "Start")
        val p2 = GeoLocation(0.0, 10.0, 0.0, "Corner")
        val p3 = GeoLocation(-10.0, 10.0, 0.0, "Destination")

        val route = listOf(p1, p2, p3)
        val steps = RouteInstructionGenerator.generateSteps(route)

        // Step 0: WALK straight
        // Step 1: TURN_LEFT
        assertEquals(RouteStepType.TURN_LEFT, steps[1].type)
    }
}
