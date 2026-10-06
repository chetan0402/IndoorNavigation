package me.chetan.indoornavigation.ui.components

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.expandVertically
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.shrinkVertically
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Build
import androidx.compose.material.icons.filled.Info
import androidx.compose.material.icons.filled.Place
import androidx.compose.material.icons.filled.Refresh
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import me.chetan.indoornavigation.ParticleFilterDebugInfo
import me.chetan.indoornavigation.data.FilterEstimate
import me.chetan.indoornavigation.data.GeoLocation
import me.chetan.indoornavigation.data.NAV_GRAPH
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

@Composable
fun DebugContainer(
    isDebugEnabled: Boolean,
    onToggleDebug: (Boolean) -> Unit,
    particleFilterDebugInfo: ParticleFilterDebugInfo?,
    userEstimate: FilterEstimate,
    stepCount: Int,
    lastStepTimestamp: Long,
    lastStepLength: Double,
    isStepSensorAvailable: Boolean,
    bleUpdateCount: Int,
    lastBleUpdateTimestamp: Long,
    route: List<GeoLocation> = emptyList(),
    currentLocation: GeoLocation? = null
) {
    Card(
        modifier = Modifier
            .fillMaxWidth()
            .padding(vertical = 8.dp),
        colors = CardDefaults.cardColors(
            containerColor = MaterialTheme.colorScheme.surfaceVariant
        )
    ) {
        Column(modifier = Modifier.padding(16.dp)) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.SpaceBetween
            ) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Icon(
                        imageVector = Icons.Default.Build,
                        contentDescription = "Debug Mode",
                        tint = MaterialTheme.colorScheme.primary
                    )
                    Spacer(modifier = Modifier.width(8.dp))
                    Text(
                        text = "Debug Mode",
                        style = MaterialTheme.typography.titleMedium,
                        fontWeight = FontWeight.Bold
                    )
                }
                Switch(
                    checked = isDebugEnabled,
                    onCheckedChange = onToggleDebug
                )
            }

            AnimatedVisibility(
                visible = isDebugEnabled,
                enter = fadeIn() + expandVertically(),
                exit = fadeOut() + shrinkVertically()
            ) {
                Column {
                    HorizontalDivider(modifier = Modifier.padding(vertical = 12.dp))

                    // 0. Graph & Route Visualization
                    DebugSectionHeader(
                        title = "Raw Graph & Route Visualization",
                        icon = Icons.Default.Place
                    )
                    Spacer(modifier = Modifier.height(8.dp))
                    RouteGraph(
                        graph = NAV_GRAPH,
                        route = route,
                        currentLocation = currentLocation
                    )

                    Spacer(modifier = Modifier.height(12.dp))

                    // 1. Step Input & Motion Sensor Info
                    DebugSectionHeader(
                        title = "Step Input & Motion",
                        icon = Icons.Default.Refresh
                    )
                    DebugRow(
                        label = "Step Detector Sensor",
                        value = if (isStepSensorAvailable) "Active / Available" else "Unavailable"
                    )
                    DebugRow(
                        label = "Total Steps Detected",
                        value = "$stepCount"
                    )
                    DebugRow(
                        label = "Step Length",
                        value = String.format(Locale.US, "%.2f m", lastStepLength)
                    )
                    DebugRow(
                        label = "Last Step Time",
                        value = formatTimestamp(lastStepTimestamp)
                    )

                    Spacer(modifier = Modifier.height(12.dp))

                    // 3. Particle Filter Info
                    DebugSectionHeader(
                        title = "Particle Filter Telemetry",
                        icon = Icons.Default.Info
                    )
                    if (particleFilterDebugInfo != null) {
                        DebugRow(
                            label = "Particle Count",
                            value = "${particleFilterDebugInfo.numParticles} (Active: ${particleFilterDebugInfo.activeParticlesCount})"
                        )
                        DebugRow(
                            label = "Effective Particles (N_eff)",
                            value = String.format(Locale.US, "%.1f", particleFilterDebugInfo.effectiveParticles)
                        )
                        DebugRow(
                            label = "Estimated Location (X, Y, Z)",
                            value = String.format(
                                Locale.US,
                                "x: %.3f, y: %.3f, z: %.3f",
                                userEstimate.x,
                                userEstimate.y,
                                userEstimate.z
                            )
                        )
                        DebugRow(
                            label = "Particle Spread (Std Dev)",
                            value = String.format(
                                Locale.US,
                                "σX: ±%.2fm, σY: ±%.2fm, σZ: ±%.2fm",
                                particleFilterDebugInfo.stdDevX,
                                particleFilterDebugInfo.stdDevY,
                                particleFilterDebugInfo.stdDevZ
                            )
                        )
                        DebugRow(
                            label = "X Bounds",
                            value = String.format(Locale.US, "[%.1f .. %.1f] m", particleFilterDebugInfo.xMin, particleFilterDebugInfo.xMax)
                        )
                        DebugRow(
                            label = "Y Bounds",
                            value = String.format(Locale.US, "[%.1f .. %.1f] m", particleFilterDebugInfo.yMin, particleFilterDebugInfo.yMax)
                        )
                        DebugRow(
                            label = "Z Bounds",
                            value = String.format(Locale.US, "[%.1f .. %.1f] m", particleFilterDebugInfo.zMin, particleFilterDebugInfo.zMax)
                        )
                    } else {
                        Text(
                            text = "No filter statistics available",
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }

                    Spacer(modifier = Modifier.height(12.dp))

                    // 4. BLE Measurements Info
                    DebugSectionHeader(
                        title = "BLE Anchor Updates",
                        icon = Icons.Default.Info
                    )
                    DebugRow(
                        label = "BLE Updates Count",
                        value = "$bleUpdateCount"
                    )
                    DebugRow(
                        label = "Last BLE Update",
                        value = formatTimestamp(lastBleUpdateTimestamp)
                    )
                }
            }
        }
    }
}

@Composable
private fun DebugSectionHeader(title: String, icon: ImageVector) {
    Row(
        verticalAlignment = Alignment.CenterVertically,
        modifier = Modifier.padding(bottom = 4.dp)
    ) {
        Icon(
            imageVector = icon,
            contentDescription = null,
            modifier = Modifier.height(16.dp),
            tint = MaterialTheme.colorScheme.primary
        )
        Spacer(modifier = Modifier.width(6.dp))
        Text(
            text = title,
            style = MaterialTheme.typography.labelLarge,
            fontWeight = FontWeight.SemiBold,
            color = MaterialTheme.colorScheme.primary
        )
    }
}

@Composable
private fun DebugRow(label: String, value: String) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .padding(vertical = 2.dp),
        horizontalArrangement = Arrangement.SpaceBetween
    ) {
        Text(
            text = label,
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant
        )
        Text(
            text = value,
            style = MaterialTheme.typography.bodySmall,
            fontWeight = FontWeight.Medium
        )
    }
}

private fun formatTimestamp(timestamp: Long): String {
    if (timestamp == 0L) return "N/A"
    val sdf = SimpleDateFormat("HH:mm:ss.SSS", Locale.US)
    return sdf.format(Date(timestamp))
}
