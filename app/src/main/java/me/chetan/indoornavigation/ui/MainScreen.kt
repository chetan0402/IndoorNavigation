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
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import me.chetan.indoornavigation.PathFind
import me.chetan.indoornavigation.data.ANCHORS
import me.chetan.indoornavigation.data.DeviceScanInfo
import me.chetan.indoornavigation.data.GeoLocation
import me.chetan.indoornavigation.data.NAV_GRAPH
import me.chetan.indoornavigation.ui.components.BLEContainer
import me.chetan.indoornavigation.ui.components.RouteDisplay
import me.chetan.indoornavigation.ui.components.RouteGraph
import java.util.Locale

@SuppressLint("DefaultLocale")
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun MainScreen(devices: Map<String, DeviceScanInfo>) {
    var query by remember { mutableStateOf("") }
    var active by remember { mutableStateOf(false) }
    var selectedDestination by remember { mutableStateOf<GeoLocation?>(null) }

    val currentLocation = remember(devices.size, devices.values.map { it.distance }) {
        val detectedAnchors = devices.mapNotNull { (address, info) ->
            ANCHORS[address]?.let { it to info.distance }
        }

        if (detectedAnchors.size >= 2) {
            val (anchors, distances) = detectedAnchors.unzip()
            val pathFinder = PathFind(NAV_GRAPH.mapValues { it.value.toMutableList() })
            pathFinder.trilaterate(anchors, distances)
        } else if (detectedAnchors.size == 1) {
            detectedAnchors.first().first
        } else null
    }

    val route = remember(currentLocation, selectedDestination) {
        if (currentLocation != null && selectedDestination != null) {
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
            currentLocation?.let {
                ElevatedCard(
                    modifier = Modifier.fillMaxWidth(),
                    colors = CardDefaults.elevatedCardColors(
                        containerColor = MaterialTheme.colorScheme.secondaryContainer
                    )
                ) {
                    Row(
                        modifier = Modifier.padding(16.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Icon(
                            Icons.Default.LocationOn,
                            contentDescription = null,
                            tint = MaterialTheme.colorScheme.primary
                        )
                        Spacer(modifier = Modifier.width(12.dp))
                        Column {
                            Text(
                                text = "Your Location",
                                style = MaterialTheme.typography.labelMedium
                            )
                            Text(
                                text = "(${String.format(Locale.US, "%.2f", it.long)}, ${String.format(Locale.US, "%.2f", it.lat)})",
                                style = MaterialTheme.typography.bodyLarge
                            )
                        }
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
            BLEContainer(devices)
            Spacer(modifier = Modifier.height(16.dp))
        }
    }
}
