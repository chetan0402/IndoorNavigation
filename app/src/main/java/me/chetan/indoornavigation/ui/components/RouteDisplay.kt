package me.chetan.indoornavigation.ui.components

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.IntrinsicSize
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.DirectionsWalk
import androidx.compose.material.icons.filled.CheckCircle
import androidx.compose.material.icons.filled.Flag
import androidx.compose.material.icons.filled.Navigation
import androidx.compose.material.icons.filled.Stairs
import androidx.compose.material.icons.filled.TurnLeft
import androidx.compose.material.icons.filled.TurnRight
import androidx.compose.material.icons.filled.TurnSlightLeft
import androidx.compose.material.icons.filled.TurnSlightRight
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.ElevatedCard
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import me.chetan.indoornavigation.RouteInstructionGenerator
import me.chetan.indoornavigation.data.GeoLocation
import me.chetan.indoornavigation.data.RouteStep
import me.chetan.indoornavigation.data.RouteStepType
import java.util.Locale
import kotlin.math.roundToInt

@Composable
fun RouteDisplay(
    route: List<GeoLocation>,
    modifier: Modifier = Modifier,
    currentLocation: GeoLocation? = null
) {
    if (route.isEmpty()) return

    val routeSteps = remember(route) { RouteInstructionGenerator.generateSteps(route) }
    val activeStepIndex = remember(routeSteps, currentLocation) {
        if (currentLocation != null) {
            RouteInstructionGenerator.calculateActiveStepIndex(routeSteps, currentLocation)
        } else 0
    }

    val activeStep = routeSteps.getOrElse(activeStepIndex) { routeSteps.first() }
    val totalDistance = remember(routeSteps) {
        routeSteps.filter { it.type != RouteStepType.ARRIVE }.sumOf { it.distanceMeters }
    }
    val totalEstSteps = (totalDistance / 0.7).roundToInt()

    ElevatedCard(
        modifier = modifier.fillMaxWidth(),
        shape = RoundedCornerShape(20.dp)
    ) {
        Column(modifier = Modifier.padding(16.dp)) {
            
            // Active Step Hero Banner
            Card(
                colors = CardDefaults.cardColors(
                    containerColor = MaterialTheme.colorScheme.primaryContainer
                ),
                shape = RoundedCornerShape(16.dp),
                modifier = Modifier.fillMaxWidth()
            ) {
                Column(modifier = Modifier.padding(16.dp)) {
                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        modifier = Modifier.fillMaxWidth()
                    ) {
                        Surface(
                            shape = CircleShape,
                            color = MaterialTheme.colorScheme.primary,
                            modifier = Modifier.size(44.dp)
                        ) {
                            Box(contentAlignment = Alignment.Center) {
                                Icon(
                                    imageVector = getStepIcon(activeStep.type),
                                    contentDescription = null,
                                    tint = MaterialTheme.colorScheme.onPrimary,
                                    modifier = Modifier.size(24.dp)
                                )
                            }
                        }

                        Spacer(modifier = Modifier.width(12.dp))

                        Column(modifier = Modifier.weight(1f)) {
                            Text(
                                text = "CURRENT STEP (${activeStepIndex + 1}/${routeSteps.size})",
                                style = MaterialTheme.typography.labelSmall,
                                color = MaterialTheme.colorScheme.primary,
                                fontWeight = FontWeight.Bold
                            )
                            Text(
                                text = activeStep.instructionText,
                                style = MaterialTheme.typography.titleMedium,
                                fontWeight = FontWeight.Bold,
                                color = MaterialTheme.colorScheme.onPrimaryContainer
                            )
                        }
                    }

                    Spacer(modifier = Modifier.height(12.dp))

                    val progress = if (routeSteps.isNotEmpty()) {
                        (activeStepIndex + 1).toFloat() / routeSteps.size.toFloat()
                    } else 0f

                    LinearProgressIndicator(
                        progress = { progress },
                        modifier = Modifier
                            .fillMaxWidth()
                            .height(6.dp)
                            .clip(CircleShape),
                        color = MaterialTheme.colorScheme.primary,
                        trackColor = MaterialTheme.colorScheme.onPrimaryContainer.copy(alpha = 0.2f)
                    )
                }
            }

            Spacer(modifier = Modifier.height(16.dp))

            // Overall Summary
            Row(
                modifier = Modifier.fillMaxWidth(),
                verticalAlignment = Alignment.CenterVertically
            ) {
                Icon(
                    imageVector = Icons.Default.Navigation,
                    contentDescription = null,
                    tint = MaterialTheme.colorScheme.primary,
                    modifier = Modifier.size(20.dp)
                )
                Spacer(modifier = Modifier.width(8.dp))
                Text(
                    text = "Total distance: ${String.format(Locale.US, "%.1f", totalDistance)} m (~$totalEstSteps steps)",
                    style = MaterialTheme.typography.labelLarge,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }

            HorizontalDivider(modifier = Modifier.padding(vertical = 12.dp))

            // Step Timeline List
            Text(
                text = "Detailed Route Instructions",
                style = MaterialTheme.typography.titleSmall,
                color = MaterialTheme.colorScheme.onSurface,
                fontWeight = FontWeight.SemiBold,
                modifier = Modifier.padding(bottom = 8.dp)
            )

            LazyColumn(
                modifier = Modifier
                    .fillMaxWidth()
                    .height(240.dp)
            ) {
                itemsIndexed(routeSteps) { index, step ->
                    val isCompleted = index < activeStepIndex
                    val isActive = index == activeStepIndex
                    val isLast = index == routeSteps.lastIndex

                    StepTimelineItem(
                        step = step,
                        isCompleted = isCompleted,
                        isActive = isActive,
                        isLast = isLast
                    )
                }
            }
        }
    }
}

@Composable
private fun StepTimelineItem(
    step: RouteStep,
    isCompleted: Boolean,
    isActive: Boolean,
    isLast: Boolean
) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .height(IntrinsicSize.Min)
            .padding(vertical = 2.dp)
    ) {
        // Timeline Indicator Column
        Column(
            horizontalAlignment = Alignment.CenterHorizontally,
            modifier = Modifier.width(36.dp)
        ) {
            Box(
                contentAlignment = Alignment.Center,
                modifier = Modifier.size(24.dp)
            ) {
                when {
                    isCompleted -> {
                        Icon(
                            imageVector = Icons.Default.CheckCircle,
                            contentDescription = null,
                            tint = MaterialTheme.colorScheme.primary,
                            modifier = Modifier.size(20.dp)
                        )
                    }
                    isActive -> {
                        Box(
                            modifier = Modifier
                                .size(22.dp)
                                .background(MaterialTheme.colorScheme.primaryContainer, CircleShape)
                                .border(2.dp, MaterialTheme.colorScheme.primary, CircleShape),
                            contentAlignment = Alignment.Center
                        ) {
                            Box(
                                modifier = Modifier
                                    .size(8.dp)
                                    .background(MaterialTheme.colorScheme.primary, CircleShape)
                            )
                        }
                    }
                    else -> {
                        Box(
                            modifier = Modifier
                                .size(16.dp)
                                .border(
                                    2.dp,
                                    MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.4f),
                                    CircleShape
                                )
                        )
                    }
                }
            }

            if (!isLast) {
                Box(
                    modifier = Modifier
                        .width(2.dp)
                        .fillMaxHeight()
                        .background(
                            if (isCompleted) MaterialTheme.colorScheme.primary
                            else MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.2f)
                        )
                )
            }
        }

        Spacer(modifier = Modifier.width(8.dp))

        // Step Content Card / Row
        Box(
            modifier = Modifier
                .fillMaxWidth()
                .padding(bottom = 8.dp)
                .clip(RoundedCornerShape(12.dp))
                .background(
                    when {
                        isActive -> MaterialTheme.colorScheme.primaryContainer.copy(alpha = 0.4f)
                        isCompleted -> Color.Transparent
                        else -> Color.Transparent
                    }
                )
                .padding(8.dp)
        ) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Icon(
                    imageVector = getStepIcon(step.type),
                    contentDescription = null,
                    tint = when {
                        isActive -> MaterialTheme.colorScheme.primary
                        isCompleted -> MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.6f)
                        else -> MaterialTheme.colorScheme.onSurface
                    },
                    modifier = Modifier.size(18.dp)
                )

                Spacer(modifier = Modifier.width(8.dp))

                Column {
                    Text(
                        text = step.instructionText,
                        style = if (isActive) MaterialTheme.typography.bodyMedium.copy(fontWeight = FontWeight.Bold)
                        else MaterialTheme.typography.bodyMedium,
                        color = when {
                            isActive -> MaterialTheme.colorScheme.onSurface
                            isCompleted -> MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.7f)
                            else -> MaterialTheme.colorScheme.onSurface
                        }
                    )
                }
            }
        }
    }
}

private fun getStepIcon(type: RouteStepType): ImageVector {
    return when (type) {
        RouteStepType.WALK -> Icons.AutoMirrored.Filled.DirectionsWalk
        RouteStepType.TURN_LEFT -> Icons.Default.TurnLeft
        RouteStepType.TURN_RIGHT -> Icons.Default.TurnRight
        RouteStepType.SLIGHT_LEFT -> Icons.Default.TurnSlightLeft
        RouteStepType.SLIGHT_RIGHT -> Icons.Default.TurnSlightRight
        RouteStepType.STAIRS_UP, RouteStepType.STAIRS_DOWN -> Icons.Default.Stairs
        RouteStepType.ARRIVE -> Icons.Default.Flag
    }
}
