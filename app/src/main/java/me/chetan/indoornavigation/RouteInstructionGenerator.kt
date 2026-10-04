package me.chetan.indoornavigation

import me.chetan.indoornavigation.data.GeoLocation
import me.chetan.indoornavigation.data.RouteStep
import me.chetan.indoornavigation.data.RouteStepType
import java.util.Locale
import kotlin.math.atan2
import kotlin.math.roundToInt

object RouteInstructionGenerator {

    /**
     * Converts a raw list of GeoLocations into descriptive, step-by-step navigation instructions.
     * Combines continuous walk straight steps into single instructions.
     */
    fun generateSteps(route: List<GeoLocation>): List<RouteStep> {
        if (route.isEmpty()) return emptyList()

        if (route.size == 1) {
            val destName = route.first().name.ifEmpty { "your destination" }
            return listOf(
                RouteStep(
                    stepIndex = 0,
                    type = RouteStepType.ARRIVE,
                    instructionText = "You have arrived at $destName",
                    distanceMeters = 0.0,
                    startPoint = route.first(),
                    endPoint = route.first(),
                    waypointName = destName
                )
            )
        }

        val rawSteps = mutableListOf<RouteStep>()

        for (i in 0 until route.size - 1) {
            val start = route[i]
            val end = route[i + 1]
            val dist = distance_between_point(start, end)
            val estSteps = (dist / 0.7).roundToInt().coerceAtLeast(1)

            val altDiff = end.alt - start.alt
            val targetName = end.name.ifEmpty {
                if (i + 1 == route.lastIndex) "your destination" else "next waypoint"
            }

            // Determine turn direction or vertical movement based on changes from previous segment
            val stepType: RouteStepType
            if (altDiff > 0.5) {
                stepType = RouteStepType.STAIRS_UP
            } else if (altDiff < -0.5) {
                stepType = RouteStepType.STAIRS_DOWN
            } else if (i > 0) {
                val prev = route[i - 1]
                val turnAngle = calculateTurnAngle(prev, start, end)
                stepType = when {
                    turnAngle in 25.0..110.0 -> RouteStepType.TURN_RIGHT
                    turnAngle in -110.0..-25.0 -> RouteStepType.TURN_LEFT
                    turnAngle in 10.0..25.0 -> RouteStepType.SLIGHT_RIGHT
                    turnAngle in -25.0..-10.0 -> RouteStepType.SLIGHT_LEFT
                    turnAngle > 110.0 -> RouteStepType.TURN_RIGHT
                    turnAngle < -110.0 -> RouteStepType.TURN_LEFT
                    else -> RouteStepType.WALK
                }
            } else {
                stepType = RouteStepType.WALK
            }

            val instructionText = buildInstructionText(stepType, dist, estSteps, targetName, i + 1 == route.lastIndex)

            rawSteps.add(
                RouteStep(
                    stepIndex = rawSteps.size,
                    type = stepType,
                    instructionText = instructionText,
                    distanceMeters = dist,
                    startPoint = start,
                    endPoint = end,
                    waypointName = targetName
                )
            )
        }

        val mergedSteps = mergeContinuousWalkSteps(rawSteps, route.last())

        // Add final Arrival Step
        val lastPoint = route.last()
        val finalDestName = lastPoint.name.ifEmpty { "your destination" }
        val finalSteps = mergedSteps.toMutableList()
        finalSteps.add(
            RouteStep(
                stepIndex = finalSteps.size,
                type = RouteStepType.ARRIVE,
                instructionText = "Arrive at $finalDestName",
                distanceMeters = 0.0,
                startPoint = lastPoint,
                endPoint = lastPoint,
                waypointName = finalDestName
            )
        )

        return finalSteps
    }

    /**
     * Merges consecutive WALK steps into a single WALK step with combined distance and target name.
     */
    private fun mergeContinuousWalkSteps(rawSteps: List<RouteStep>, finalDestination: GeoLocation): List<RouteStep> {
        if (rawSteps.isEmpty()) return emptyList()

        val merged = mutableListOf<RouteStep>()
        var current: RouteStep? = null

        for (step in rawSteps) {
            if (current == null) {
                current = step
                continue
            }

            if (current.type == RouteStepType.WALK && step.type == RouteStepType.WALK) {
                val combinedDist = current.distanceMeters + step.distanceMeters
                val combinedEstSteps = (combinedDist / 0.7).roundToInt().coerceAtLeast(1)
                val isLastSegment = (step.endPoint == finalDestination)
                val targetName = step.waypointName ?: current.waypointName ?: "next waypoint"
                val text = buildInstructionText(RouteStepType.WALK, combinedDist, combinedEstSteps, targetName, isLastSegment)

                current = current.copy(
                    distanceMeters = combinedDist,
                    endPoint = step.endPoint,
                    instructionText = text,
                    waypointName = targetName
                )
            } else {
                merged.add(current)
                current = step
            }
        }

        if (current != null) {
            merged.add(current)
        }

        return merged.mapIndexed { idx, s -> s.copy(stepIndex = idx) }
    }

    /**
     * Calculates the turn angle in degrees between segment (p1->p2) and segment (p2->p3).
     * Positive value represents a clockwise (right) turn.
     */
    private fun calculateTurnAngle(p1: GeoLocation, p2: GeoLocation, p3: GeoLocation): Double {
        val dx1 = p2.long - p1.long
        val dy1 = p2.lat - p1.lat
        val dx2 = p3.long - p2.long
        val dy2 = p3.lat - p2.lat

        val crossProduct = dx1 * dy2 - dy1 * dx2
        val dotProduct = dx1 * dx2 + dy1 * dy2

        val radians = atan2(crossProduct, dotProduct)
        return Math.toDegrees(radians)
    }

    private fun buildInstructionText(
        type: RouteStepType,
        distanceMeters: Double,
        estSteps: Int,
        targetName: String,
        isLastSegment: Boolean
    ): String {
        val distFormatted = String.format(Locale.US, "%.1f", distanceMeters)
        val distText = "$distFormatted m (~$estSteps steps)"

        return when (type) {
            RouteStepType.WALK -> {
                if (isLastSegment) "Walk $distText straight to $targetName"
                else "Walk $distText straight towards $targetName"
            }
            RouteStepType.TURN_LEFT -> "Turn left and walk $distText towards $targetName"
            RouteStepType.TURN_RIGHT -> "Turn right and walk $distText towards $targetName"
            RouteStepType.SLIGHT_LEFT -> "Bear left and walk $distText towards $targetName"
            RouteStepType.SLIGHT_RIGHT -> "Bear right and walk $distText towards $targetName"
            RouteStepType.STAIRS_UP -> "Take stairs up $distText towards $targetName"
            RouteStepType.STAIRS_DOWN -> "Take stairs down $distText towards $targetName"
            RouteStepType.ARRIVE -> "Arrive at $targetName"
        }
    }

    /**
     * Determines which step in the route the user is currently at based on user's current location.
     */
    fun calculateActiveStepIndex(routeSteps: List<RouteStep>, currentLocation: GeoLocation): Int {
        if (routeSteps.isEmpty()) return 0
        if (routeSteps.size == 1) return 0

        var minDistance = Double.MAX_VALUE
        var bestIndex = 0

        for (i in routeSteps.indices) {
            val step = routeSteps[i]
            if (step.type == RouteStepType.ARRIVE) {
                val distToFinal = distance_between_point(currentLocation, step.endPoint)
                if (distToFinal < 1.0 && minDistance > distToFinal) {
                    minDistance = distToFinal
                    bestIndex = i
                }
                continue
            }

            val segDistance = distance_from_line(currentLocation, step.startPoint, step.endPoint)
            if (segDistance < minDistance) {
                minDistance = segDistance
                bestIndex = i
            }
        }

        // If user is very close to the end point of the active step, move to next step if available
        if (bestIndex < routeSteps.lastIndex) {
            val currentStep = routeSteps[bestIndex]
            val distToEndPoint = distance_between_point(currentLocation, currentStep.endPoint)
            if (distToEndPoint < 1.0) {
                bestIndex = (bestIndex + 1).coerceAtMost(routeSteps.lastIndex)
            }
        }

        return bestIndex
    }
}
