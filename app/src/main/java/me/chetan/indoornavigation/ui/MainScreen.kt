package me.chetan.indoornavigation.ui

import android.annotation.SuppressLint
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.LocationOn
import androidx.compose.material.icons.filled.Place
import androidx.compose.material.icons.filled.Search
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.ElevatedCard
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.ListItem
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.SearchBar
import androidx.compose.material3.SearchBarDefaults
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import android.util.Log
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import androidx.compose.material.icons.filled.QrCodeScanner
import androidx.compose.material3.Button
import com.google.mlkit.vision.codescanner.GmsBarcodeScanning
import me.chetan.indoornavigation.ParticleFilterDebugInfo
import me.chetan.indoornavigation.PathFind
import me.chetan.indoornavigation.data.DeviceScanInfo
import me.chetan.indoornavigation.data.FilterEstimate
import me.chetan.indoornavigation.data.GeoLocation
import me.chetan.indoornavigation.data.NAV_GRAPH
import me.chetan.indoornavigation.ui.components.BLEContainer
import me.chetan.indoornavigation.ui.components.DebugContainer
import me.chetan.indoornavigation.ui.components.RouteDisplay
import me.chetan.indoornavigation.ui.components.RouteGraph
import java.util.Locale

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun MainScreen(viewModel: MainViewModel) {
    MainScreen(
        devices = viewModel.devices,
        userEstimate = viewModel.userLocation.value,
        isDebugEnabled = viewModel.isDebugModeEnabled.value,
        onToggleDebug = { viewModel.toggleDebugMode() },
        particleFilterDebugInfo = viewModel.particleFilterDebugInfo.value,
        stepCount = viewModel.stepCount.value,
        lastStepTimestamp = viewModel.lastStepTimestamp.value,
        lastStepLength = viewModel.lastStepLength.value,
        isStepSensorAvailable = viewModel.isStepSensorAvailable.value,
        azimuthDegrees = viewModel.azimuthDegrees.value,
        azimuthRadians = viewModel.azimuthRadians.value,
        isRotationSensorAvailable = viewModel.isRotationSensorAvailable.value,
        bleUpdateCount = viewModel.bleUpdateCount.value,
        lastBleUpdateTimestamp = viewModel.lastBleUpdateTimestamp.value,
        onAbsUpdate = { x, y, z -> viewModel.absUpdate(x, y, z) }
    )
}

@SuppressLint("DefaultLocale")
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun MainScreen(
    devices: Map<String, DeviceScanInfo>,
    userEstimate: FilterEstimate,
    isDebugEnabled: Boolean = false,
    onToggleDebug: (Boolean) -> Unit = {},
    particleFilterDebugInfo: ParticleFilterDebugInfo? = null,
    stepCount: Int = 0,
    lastStepTimestamp: Long = 0L,
    lastStepLength: Double = 0.7,
    isStepSensorAvailable: Boolean = false,
    azimuthDegrees: Double = 0.0,
    azimuthRadians: Double = 0.0,
    isRotationSensorAvailable: Boolean = false,
    bleUpdateCount: Int = 0,
    lastBleUpdateTimestamp: Long = 0L,
    onAbsUpdate: (Double, Double, Double) -> Unit = { _, _, _ -> }
) {
    var query by remember { mutableStateOf("") }
    var active by remember { mutableStateOf(false) }
    var selectedDestination by remember { mutableStateOf<GeoLocation?>(null) }

    val currentLocation = remember(userEstimate) {
        GeoLocation(userEstimate.x, userEstimate.y, userEstimate.z, "Current Location")
    }

    val route = remember(currentLocation, selectedDestination) {
        if (selectedDestination != null) {
            val pathFinder = PathFind(NAV_GRAPH.mapValues { it.value.toMutableList() })
            pathFinder.route(currentLocation, selectedDestination!!)
        } else null
    }

    val searchResults = remember(query) {
        NAV_GRAPH.keys.filter {
            it.name.contains(query, ignoreCase = true) && it.name.isNotEmpty()
        }
    }

    Column(modifier = Modifier.fillMaxSize()) {
        SearchBar(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 16.dp, vertical = 8.dp),
            inputField = {
                SearchBarDefaults.InputField(
                    query = query,
                    onQueryChange = { query = it },
                    onSearch = { active = false },
                    expanded = active,
                    onExpandedChange = { active = it },
                    placeholder = { Text("Where to?") },
                    leadingIcon = { Icon(Icons.Default.Search, contentDescription = null) }
                )
            },
            expanded = active,
            onExpandedChange = { active = it }
        ) {
            LazyColumn {
                items(searchResults) { location ->
                    ListItem(
                        headlineContent = { Text(location.name) },
                        supportingContent = { Text("${String.format(Locale.US, "%.2f", location.long)}, ${String.format(Locale.US, "%.2f", location.lat)}") },
                        leadingContent = { Icon(Icons.Default.Place, contentDescription = null) },
                        modifier = Modifier.clickable {
                            query = location.name
                            selectedDestination = location
                            active = false
                        }
                    )
                }
            }
        }

        Column(
            modifier = Modifier
                .weight(1f)
                .verticalScroll(rememberScrollState())
                .padding(horizontal = 16.dp)
        ) {
            ElevatedCard(
                modifier = Modifier.fillMaxWidth(),
                colors = CardDefaults.elevatedCardColors(
                    containerColor = MaterialTheme.colorScheme.secondaryContainer
                )
            ) {
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(16.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Icon(
                        Icons.Default.LocationOn,
                        contentDescription = null,
                        tint = MaterialTheme.colorScheme.primary
                    )
                    Spacer(modifier = Modifier.width(12.dp))
                    Column(modifier = Modifier.weight(1f)) {
                        Text(
                            text = "Your Location",
                            style = MaterialTheme.typography.labelMedium
                        )
                        Text(
                            text = "(${String.format(Locale.US, "%.2f", currentLocation.long)}, ${String.format(Locale.US, "%.2f", currentLocation.lat)})",
                            style = MaterialTheme.typography.bodyLarge
                        )
                    }

                    val context = LocalContext.current
                    val scanner = remember { GmsBarcodeScanning.getClient(context) }

                    Button(
                        onClick = {
                            scanner.startScan()
                                .addOnSuccessListener { barcode ->
                                    barcode.rawValue?.let { rawValue ->
                                        val parts = rawValue.split(",")
                                        if (parts.size >= 3) {
                                            val x = parts[0].trim().toDoubleOrNull()
                                            val y = parts[1].trim().toDoubleOrNull()
                                            val z = parts[2].trim().toDoubleOrNull()
                                            if (x != null && y != null && z != null) {
                                                onAbsUpdate(x, y, z)
                                            }
                                        }
                                    }
                                }
                                .addOnFailureListener { e ->
                                    Log.e("QRScan", "Barcode scanner error", e)
                                }
                        }
                    ) {
                        Icon(Icons.Default.QrCodeScanner, contentDescription = null)
                        Spacer(modifier = Modifier.width(4.dp))
                        Text("Scan")
                    }
                }
            }

            route?.let {
                Spacer(modifier = Modifier.height(16.dp))
                RouteDisplay(it)
                Spacer(modifier = Modifier.height(16.dp))
                RouteGraph(NAV_GRAPH, it, currentLocation)
            }

            Spacer(modifier = Modifier.height(16.dp))
            DebugContainer(
                isDebugEnabled = isDebugEnabled,
                onToggleDebug = onToggleDebug,
                particleFilterDebugInfo = particleFilterDebugInfo,
                userEstimate = userEstimate,
                stepCount = stepCount,
                lastStepTimestamp = lastStepTimestamp,
                lastStepLength = lastStepLength,
                isStepSensorAvailable = isStepSensorAvailable,
                azimuthDegrees = azimuthDegrees,
                azimuthRadians = azimuthRadians,
                isRotationSensorAvailable = isRotationSensorAvailable,
                bleUpdateCount = bleUpdateCount,
                lastBleUpdateTimestamp = lastBleUpdateTimestamp
            )

            Spacer(modifier = Modifier.height(16.dp))
            BLEContainer(devices)
            Spacer(modifier = Modifier.height(16.dp))
        }
    }
}
