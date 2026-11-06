package me.chetan.indoornavigation.ui.components

import androidx.compose.animation.core.FastOutSlowInEasing
import androidx.compose.animation.core.LinearEasing
import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.Spring
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.spring
import androidx.compose.animation.core.tween
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.gestures.detectDragGestures
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.RotateRight
import androidx.compose.material.icons.filled.CenterFocusStrong
import androidx.compose.material.icons.filled.ViewInAr
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.ElevatedCard
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import me.chetan.indoornavigation.data.GeoLocation
import kotlin.math.atan2
import kotlin.math.cos
import kotlin.math.max
import kotlin.math.min
import kotlin.math.sin

/**
 * 3D Projected Spatial Point containing original GeoLocation and transformed 3D canvas coordinates.
 */
private data class ProjectedPoint3D(
    val location: GeoLocation,
    val screenOffset: Offset,
    val depthY: Double,
    val scaleFactor: Float
)

/**
 * Projected 3D Edge between two 3D spatial points.
 */
private data class ProjectedEdge3D(
    val start: ProjectedPoint3D,
    val end: ProjectedPoint3D,
    val isRouteEdge: Boolean,
    val averageDepth: Double
)

@Composable
fun RouteGraph3D(
    graph: Map<GeoLocation, List<GeoLocation>>,
    modifier: Modifier = Modifier,
    route: List<GeoLocation> = emptyList(),
    currentLocation: GeoLocation? = null
) {
    var isAutoAligned by remember { mutableStateOf(true) }
    var isAutoRotating by remember { mutableStateOf(false) }

    var userYaw by remember { mutableFloatStateOf(45f) }
    var userPitch by remember { mutableFloatStateOf(40f) }

    // Calculate optimal auto-alignment angles based on the route
    val autoYaw = remember(route, graph) {
        if (route.size >= 2) {
            val start = route.first()
            val end = route.last()
            val dx = end.long - start.long
            val dy = end.lat - start.lat
            val headingDeg = Math.toDegrees(atan2(dy, dx)).toFloat()
            // Angle to orient path flowing gracefully bottom-to-top on screen
            -headingDeg + 90f
        } else {
            35f
        }
    }

    val autoPitch = 45f

    // Smooth continuous rotation transition when auto-rotate is toggled
    val infiniteTransition = rememberInfiniteTransition(label = "ContinuousRotation")
    val continuousYawOffset by infiniteTransition.animateFloat(
        initialValue = 0f,
        targetValue = 360f,
        animationSpec = infiniteRepeatable(
            animation = tween(durationMillis = 20000, easing = LinearEasing),
            repeatMode = RepeatMode.Restart
        ),
        label = "YawContinuous"
    )

    // Compute target yaw/pitch
    val targetYaw = when {
        isAutoRotating -> (if (isAutoAligned) autoYaw else userYaw) + continuousYawOffset
        isAutoAligned -> autoYaw
        else -> userYaw
    }

    val targetPitch = if (isAutoAligned) autoPitch else userPitch

    // Smooth spring animations for rotation angles
    val animatedYaw by animateFloatAsState(
        targetValue = targetYaw,
        animationSpec = spring(
            stiffness = Spring.StiffnessLow,
            dampingRatio = Spring.DampingRatioNoBouncy
        ),
        label = "SmoothYaw"
    )

    val animatedPitch by animateFloatAsState(
        targetValue = targetPitch,
        animationSpec = spring(
            stiffness = Spring.StiffnessLow,
            dampingRatio = Spring.DampingRatioNoBouncy
        ),
        label = "SmoothPitch"
    )

    // Pulsing animation for current location indicator
    val pulseScale by infiniteTransition.animateFloat(
        initialValue = 0.8f,
        targetValue = 1.4f,
        animationSpec = infiniteRepeatable(
            animation = tween(durationMillis = 1200, easing = FastOutSlowInEasing),
            repeatMode = RepeatMode.Reverse
        ),
        label = "PulseCurrentLocation"
    )

    val primaryColor = MaterialTheme.colorScheme.primary
    val secondaryColor = MaterialTheme.colorScheme.secondary
    val surfaceColor = MaterialTheme.colorScheme.surfaceVariant

    ElevatedCard(
        modifier = modifier.fillMaxWidth(),
        shape = RoundedCornerShape(20.dp),
        colors = CardDefaults.elevatedCardColors(
            containerColor = surfaceColor.copy(alpha = 0.4f)
        )
    ) {
        Column(modifier = Modifier.padding(16.dp)) {
            // Card Header & Controls
            Row(
                modifier = Modifier.fillMaxWidth(),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.SpaceBetween
            ) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Icon(
                        imageVector = Icons.Default.ViewInAr,
                        contentDescription = "3D Spatial Graph",
                        tint = primaryColor,
                        modifier = Modifier.size(24.dp)
                    )
                    Spacer(modifier = Modifier.width(8.dp))
                    Column {
                        Text(
                            text = "3D Route & Spatial View",
                            style = MaterialTheme.typography.titleMedium,
                            fontWeight = FontWeight.Bold
                        )
                        Text(
                            text = if (isAutoAligned) "Auto-aligned to path" else "Interactive 3D Orbit",
                            style = MaterialTheme.typography.labelSmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }
                }

                Row(verticalAlignment = Alignment.CenterVertically) {
                    // Continuous Rotation Toggle
                    IconButton(
                        onClick = { isAutoRotating = !isAutoRotating },
                        modifier = Modifier.size(36.dp)
                    ) {
                        Icon(
                            imageVector = Icons.AutoMirrored.Filled.RotateRight,
                            contentDescription = "Auto Rotate",
                            tint = if (isAutoRotating) primaryColor else MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }

                    // Reset / Auto-Align Button
                    IconButton(
                        onClick = {
                            isAutoAligned = true
                            userYaw = autoYaw
                            userPitch = autoPitch
                        },
                        modifier = Modifier.size(36.dp)
                    ) {
                        Icon(
                            imageVector = Icons.Default.CenterFocusStrong,
                            contentDescription = "Auto Align",
                            tint = if (isAutoAligned) primaryColor else MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }
                }
            }

            Spacer(modifier = Modifier.height(12.dp))

            // 3D Canvas Viewport
            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .height(320.dp)
                    .background(
                        color = MaterialTheme.colorScheme.surface.copy(alpha = 0.8f),
                        shape = RoundedCornerShape(16.dp)
                    )
                    .pointerInput(Unit) {
                        detectDragGestures { change, dragAmount ->
                            change.consume()
                            isAutoAligned = false
                            userYaw = (userYaw + dragAmount.x * 0.4f) % 360f
                            userPitch = (userPitch - dragAmount.y * 0.4f).coerceIn(10f, 85f)
                        }
                    }
            ) {
                Canvas(
                    modifier = Modifier.matchParentSize()
                ) {
                    val width = size.width
                    val height = size.height

                    // Collect all active points
                    val allPoints = (graph.keys + graph.values.flatten() + route + listOfNotNull(currentLocation))
                        .distinct()

                    if (allPoints.isEmpty()) return@Canvas

                    // Active points subset for auto-centering (prefer route points if route is active)
                    val focusPoints = if (route.isNotEmpty()) route else allPoints

                    val minX = focusPoints.minOf { it.long }
                    val maxX = focusPoints.maxOf { it.long }
                    val minY = focusPoints.minOf { it.lat }
                    val maxY = focusPoints.maxOf { it.lat }
                    val minZ = focusPoints.minOf { it.alt }
                    val maxZ = focusPoints.maxOf { it.alt }

                    val centerX = (minX + maxX) / 2.0
                    val centerY = (minY + maxY) / 2.0
                    val centerZ = (minZ + maxZ) / 2.0

                    val spanX = max(maxX - minX, 2.0)
                    val spanY = max(maxY - minY, 2.0)
                    val spanZ = max(maxZ - minZ, 2.0)
                    val maxSpan = max(spanX, max(spanY, spanZ)).toFloat()

                    // Canvas scale & camera projection parameters
                    val baseScale = min(width, height) * 0.32f / maxSpan
                    val yawRad = Math.toRadians(animatedYaw.toDouble())
                    val pitchRad = Math.toRadians(animatedPitch.toDouble())

                    val cosYaw = cos(yawRad)
                    val sinYaw = sin(yawRad)
                    val cosPitch = cos(pitchRad)
                    val sinPitch = sin(pitchRad)

                    // 3D Projection transformation function
                    fun project3D(location: GeoLocation): ProjectedPoint3D {
                        val rx = location.long - centerX
                        val ry = location.lat - centerY
                        val rz = location.alt - centerZ

                        // Yaw rotation around Z
                        val x1 = rx * cosYaw - ry * sinYaw
                        val y1 = rx * sinYaw + ry * cosYaw
                        val z1 = rz

                        // Pitch rotation around X
                        val x2 = x1
                        val y2 = y1 * cosPitch - z1 * sinPitch
                        val z2 = y1 * sinPitch + z1 * cosPitch

                        // Perspective distance simulation
                        val cameraDistance = 60.0
                        val perspective = (cameraDistance / (cameraDistance + y2)).toFloat()

                        val screenX = (width / 2.0 + x2 * baseScale * perspective).toFloat()
                        val screenY = (height / 2.0 - z2 * baseScale * perspective).toFloat()

                        return ProjectedPoint3D(
                            location = location,
                            screenOffset = Offset(screenX, screenY),
                            depthY = y2,
                            scaleFactor = perspective
                        )
                    }

                    // Map all points to 3D projected screen coordinates
                    val projectedMap = allPoints.associateWith { project3D(it) }

                    // Draw subtle 3D ground plane grid at base Z level
                    val gridZ = minZ
                    val gridStep = max(maxSpan / 4f, 2f)
                    val gridColor = Color.Gray.copy(alpha = 0.15f)

                    for (i in -2..2) {
                        val p1 = project3D(GeoLocation(centerX + i * gridStep, centerY - 2 * gridStep, gridZ))
                        val p2 = project3D(GeoLocation(centerX + i * gridStep, centerY + 2 * gridStep, gridZ))
                        val p3 = project3D(GeoLocation(centerX - 2 * gridStep, centerY + i * gridStep, gridZ))
                        val p4 = project3D(GeoLocation(centerX + 2 * gridStep, centerY + i * gridStep, gridZ))

                        drawLine(gridColor, p1.screenOffset, p2.screenOffset, strokeWidth = 1f)
                        drawLine(gridColor, p3.screenOffset, p4.screenOffset, strokeWidth = 1f)
                    }

                    // Build list of all edges in graph
                    val allEdges = mutableListOf<ProjectedEdge3D>()
                    val routeEdgePairs = if (route.size >= 2) {
                        (0 until route.size - 1).map { i -> route[i] to route[i + 1] }.toSet()
                    } else emptySet()

                    val processedPairs = mutableSetOf<Pair<GeoLocation, GeoLocation>>()

                    graph.forEach { (startLoc, neighbors) ->
                        val startProj = projectedMap[startLoc] ?: return@forEach
                        neighbors.forEach { endLoc ->
                            val endProj = projectedMap[endLoc] ?: return@forEach
                            val pairKey = if (startLoc.hashCode() < endLoc.hashCode()) startLoc to endLoc else endLoc to startLoc
                            if (pairKey !in processedPairs) {
                                processedPairs.add(pairKey)
                                val isRoute = (startLoc to endLoc) in routeEdgePairs || (endLoc to startLoc) in routeEdgePairs
                                val avgDepth = (startProj.depthY + endProj.depthY) / 2.0
                                allEdges.add(ProjectedEdge3D(startProj, endProj, isRoute, avgDepth))
                            }
                        }
                    }

                    // Sort edges by depth (back to front rendering)
                    allEdges.sortByDescending { it.averageDepth }

                    // 1. Draw non-route background edges
                    allEdges.filter { !it.isRouteEdge }.forEach { edge ->
                        drawLine(
                            color = Color.LightGray.copy(alpha = 0.45f),
                            start = edge.start.screenOffset,
                            end = edge.end.screenOffset,
                            strokeWidth = 2.5f * edge.start.scaleFactor,
                            cap = StrokeCap.Round
                        )
                    }

                    // 2. Draw highlighted route edges (with outer glow + inner vibrant line)
                    val routeEdges = if (route.size >= 2) {
                        (0 until route.size - 1).mapNotNull { i ->
                            val p1 = projectedMap[route[i]]
                            val p2 = projectedMap[route[i + 1]]
                            if (p1 != null && p2 != null) {
                                val avgDepth = (p1.depthY + p2.depthY) / 2.0
                                ProjectedEdge3D(p1, p2, true, avgDepth)
                            } else null
                        }
                    } else emptyList()

                    routeEdges.sortedByDescending { it.averageDepth }.forEach { edge ->
                        // Outer glow line
                        drawLine(
                            color = primaryColor.copy(alpha = 0.35f),
                            start = edge.start.screenOffset,
                            end = edge.end.screenOffset,
                            strokeWidth = 14f * edge.start.scaleFactor,
                            cap = StrokeCap.Round
                        )
                        // Inner vibrant line
                        drawLine(
                            color = primaryColor,
                            start = edge.start.screenOffset,
                            end = edge.end.screenOffset,
                            strokeWidth = 6.5f * edge.start.scaleFactor,
                            cap = StrokeCap.Round
                        )
                    }

                    // 3. Draw vertices (nodes)
                    projectedMap.values.sortedByDescending { it.depthY }.forEach { point ->
                        val isOnRoute = point.location in route
                        val radius = (if (isOnRoute) 8f else 4.5f) * point.scaleFactor
                        val color = if (isOnRoute) primaryColor else Color.Gray.copy(alpha = 0.7f)

                        drawCircle(
                            color = color,
                            radius = radius,
                            center = point.screenOffset
                        )
                        if (isOnRoute) {
                            drawCircle(
                                color = Color.White,
                                radius = radius * 0.45f,
                                center = point.screenOffset
                            )
                        }
                    }

                    // 4. Draw Start and Destination Pin Markers
                    if (route.isNotEmpty()) {
                        // Start point marker (Red)
                        val startProj = projectedMap[route.first()]
                        if (startProj != null) {
                            drawCircle(
                                color = Color(0xFFE53935),
                                radius = 10f * startProj.scaleFactor,
                                center = startProj.screenOffset
                            )
                            drawCircle(
                                color = Color.White,
                                radius = 4f * startProj.scaleFactor,
                                center = startProj.screenOffset
                            )
                        }

                        // Destination point marker (Green/Accent)
                        val endProj = projectedMap[route.last()]
                        if (endProj != null) {
                            drawCircle(
                                color = secondaryColor,
                                radius = 11f * endProj.scaleFactor,
                                center = endProj.screenOffset
                            )
                            drawCircle(
                                color = Color.White,
                                radius = 4.5f * endProj.scaleFactor,
                                center = endProj.screenOffset
                            )
                        }
                    }

                    // 5. Draw Animated Current Location Marker
                    if (currentLocation != null) {
                        val currentProj = project3D(currentLocation)

                        // Pulsing outer aura ring
                        drawCircle(
                            color = Color(0xFF00E676).copy(alpha = 0.35f),
                            radius = 18f * currentProj.scaleFactor * pulseScale,
                            center = currentProj.screenOffset
                        )

                        // Solid core green location dot
                        drawCircle(
                            color = Color(0xFF00C853),
                            radius = 10f * currentProj.scaleFactor,
                            center = currentProj.screenOffset
                        )
                        drawCircle(
                            color = Color.White,
                            radius = 4f * currentProj.scaleFactor,
                            center = currentProj.screenOffset
                        )
                    }
                }

                // Overlay Guidance Hint
                Surface(
                    modifier = Modifier
                        .align(Alignment.BottomStart)
                        .padding(12.dp),
                    shape = RoundedCornerShape(8.dp),
                    color = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.85f)
                ) {
                    Text(
                        text = "Drag to rotate 3D view",
                        style = MaterialTheme.typography.labelSmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        modifier = Modifier.padding(horizontal = 8.dp, vertical = 4.dp)
                    )
                }
            }
        }
    }
}

@Preview(showBackground = true)
@Composable
private fun RouteGraph3DPreview() {
    val sampleLocations = listOf(
        GeoLocation(0.0, 0.0, 0.0, "Start"),
        GeoLocation(5.0, 2.0, 1.0, "Point A"),
        GeoLocation(10.0, 8.0, 3.0, "Stairs Top"),
        GeoLocation(15.0, 12.0, 3.0, "Destination")
    )

    val sampleGraph = mapOf(
        sampleLocations[0] to listOf(sampleLocations[1]),
        sampleLocations[1] to listOf(sampleLocations[0], sampleLocations[2]),
        sampleLocations[2] to listOf(sampleLocations[1], sampleLocations[3]),
        sampleLocations[3] to listOf(sampleLocations[2])
    )

    MaterialTheme {
        RouteGraph3D(
            graph = sampleGraph,
            route = sampleLocations,
            currentLocation = sampleLocations[1]
        )
    }
}
