package me.chetan.indoornavigation.ui.components

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.dp
import me.chetan.indoornavigation.data.GeoLocation

@Composable
fun RouteGraph(
    graph: Map<GeoLocation, List<GeoLocation>>,
    route: List<GeoLocation>,
    currentLocation: GeoLocation? = null
) {
    val allPoints = graph.keys + graph.values.flatten() + route + listOfNotNull(currentLocation)
    val minLong = allPoints.minOfOrNull { it.long } ?: 0.0
    val maxLong = allPoints.maxOfOrNull { it.long } ?: 1.0
    val minLat = allPoints.minOfOrNull { it.lat } ?: 0.0
    val maxLat = allPoints.maxOfOrNull { it.lat } ?: 1.0

    val padding = 50f
    val primaryColor = MaterialTheme.colorScheme.primary
    val secondaryColor = MaterialTheme.colorScheme.secondary

    Canvas(
        modifier = Modifier
            .fillMaxWidth()
            .height(300.dp)
            .background(MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.3f), RoundedCornerShape(16.dp))
            .padding(16.dp)
    ) {
        val width = size.width - 2 * padding
        val height = size.height - 2 * padding

        fun GeoLocation.toOffset(): Offset {
            val x = if (maxLong > minLong) {
                padding + ((long - minLong) / (maxLong - minLong) * width).toFloat()
            } else padding
            val y = if (maxLat > minLat) {
                padding + ((lat - minLat) / (maxLat - minLat) * height).toFloat()
            } else padding
            return Offset(x, y)
        }

        // Draw all edges
        graph.forEach { (start, neighbors) ->
            val startOffset = start.toOffset()
            neighbors.forEach { end ->
                drawLine(
                    color = Color.LightGray,
                    start = startOffset,
                    end = end.toOffset(),
                    strokeWidth = 2f
                )
            }
        }

        // Draw route
        if (route.size >= 2) {
            for (i in 0 until route.size - 1) {
                drawLine(
                    color = primaryColor,
                    start = route[i].toOffset(),
                    end = route[i + 1].toOffset(),
                    strokeWidth = 10f
                )
            }
        }

        // Draw points
        allPoints.distinct().forEach { point ->
            drawCircle(
                color = if (point in route) primaryColor else Color.Gray,
                radius = if (point in route) 10f else 6f,
                center = point.toOffset()
            )
        }

        if (currentLocation != null) {
            drawCircle(
                color = Color(0xFF4CAF50), // Green
                radius = 16f,
                center = currentLocation.toOffset()
            )
        }

        drawCircle(
            color = secondaryColor,
            radius = 14f,
            center = route.last().toOffset()
        )

        drawCircle(
            color = Color(0xFFF44336), // Red
            radius = 14f,
            center = route.first().toOffset()
        )
    }
}
