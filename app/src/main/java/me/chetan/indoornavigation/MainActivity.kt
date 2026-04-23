package me.chetan.indoornavigation

import android.Manifest
import android.annotation.SuppressLint
import android.bluetooth.BluetoothManager
import android.bluetooth.le.BluetoothLeScanner
import android.bluetooth.le.ScanCallback
import android.bluetooth.le.ScanResult
import android.content.pm.PackageManager
import android.location.LocationManager
import android.os.Build
import android.os.Bundle
import android.util.Log
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Info
import androidx.compose.material.icons.filled.LocationOn
import androidx.compose.material.icons.filled.Place
import androidx.compose.material.icons.filled.PlayArrow
import androidx.compose.material.icons.filled.Search
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.ElevatedCard
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.ListItem
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.SearchBar
import androidx.compose.material3.SearchBarDefaults
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateMapOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.runtime.snapshots.SnapshotStateMap
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import androidx.core.app.ActivityCompat
import androidx.lifecycle.lifecycleScope
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import java.util.Locale

data class DeviceScanInfo(
    val distance: Double,
    val predictor: RSSIDistancePredictor,
    val lastSeen: Long
)

const val SCAN_TIMEOUT_MS = 10_000L

class MainActivity : ComponentActivity() {
    private var devices = mutableStateMapOf<String, DeviceScanInfo>()
    private var scanner: BluetoothLeScanner? = null
    private var scanCallback: ScanCallback? = null

    // Only include permissions needed for scanning
    private val requiredPermissions = mutableListOf<String>().apply {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
            add(Manifest.permission.BLUETOOTH_SCAN)
        } else {
            add(Manifest.permission.BLUETOOTH)
        }
        add(Manifest.permission.ACCESS_FINE_LOCATION)
        add(Manifest.permission.ACCESS_COARSE_LOCATION)
    }.toTypedArray()

    private val permissionLauncher = registerForActivityResult(
        ActivityResultContracts.RequestMultiplePermissions()
    ) { permissions ->
        val denied = permissions.filter { !it.value }.keys
        if (denied.isEmpty()) {
            startBleScan()
        } else {
            Log.e("BLE", "Required permissions not granted: $denied")
        }
    }

    @OptIn(ExperimentalMaterial3Api::class)
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()

        val bluetoothManager: BluetoothManager = getSystemService(BLUETOOTH_SERVICE) as BluetoothManager
        val adapter = bluetoothManager.adapter
        if (adapter == null || !adapter.isEnabled) {
            Log.e("BLE", "Bluetooth not supported or not enabled")
            return
        }

        scanner = adapter.bluetoothLeScanner

        // Check permissions
        val missingPermissions = requiredPermissions.filter {
            ActivityCompat.checkSelfPermission(this, it) != PackageManager.PERMISSION_GRANTED
        }
        if (missingPermissions.isNotEmpty()) {
            Log.e("BLE", "Missing permissions: $missingPermissions")
            permissionLauncher.launch(requiredPermissions)
            return
        }

        // Check if location is enabled (required for BLE scan on Android)
        val locationManager = getSystemService(LOCATION_SERVICE) as LocationManager
        val isLocationEnabled = locationManager.isProviderEnabled(LocationManager.GPS_PROVIDER) ||
                locationManager.isProviderEnabled(LocationManager.NETWORK_PROVIDER)
        if (!isLocationEnabled) {
            Log.e("BLE", "Location services are not enabled. BLE scan may not work.")
        }

        startBleScan()

        // Cleanup old devices periodically
        lifecycleScope.launch {
            while (true) {
                delay(1000)
                val currentTime = System.currentTimeMillis()
                val toRemove = devices.filter { (_, info) ->
                    currentTime - info.lastSeen > SCAN_TIMEOUT_MS
                }.keys
                toRemove.forEach { devices.remove(it) }
            }
        }

        setContent {
            MaterialTheme {
                Scaffold(
                    topBar = {
                        TopAppBar(
                            title = { Text("Indoor Navigation") }
                        )
                    }
                ) { innerPadding ->
                    Column(
                        modifier = Modifier
                            .padding(innerPadding)
                            .fillMaxSize()
                    ) {
                        ShowRouteContainer(devices)
                    }
                }
            }
        }
    }

    private fun startBleScan() {
        val permissionsOk = requiredPermissions.all {
            ActivityCompat.checkSelfPermission(this, it) == PackageManager.PERMISSION_GRANTED
        }
        if (!permissionsOk) return

        Log.d("BLE", "Starting BLE scan")
        scanCallback = object : ScanCallback() {
            override fun onScanResult(callbackType: Int, result: ScanResult) {
                super.onScanResult(callbackType, result)
                val address = result.device.address
                if (address !in ANCHORS) return
                
                val currentInfo = devices[address]
                val predictor = currentInfo?.predictor ?: RSSIDistancePredictor()
                val (dis, _) = predictor.predict(result.rssi.toDouble())
                
                devices[address] = DeviceScanInfo(dis, predictor, System.currentTimeMillis())
            }
            override fun onScanFailed(errorCode: Int) {
                Log.e("BLE", "Scan failed with error: $errorCode")
            }
        }
        scanner?.startScan(scanCallback)
    }

    private fun stopBleScan() {
        Log.d("BLE", "Stopping BLE scan")
        if(ActivityCompat.checkSelfPermission(this, Manifest.permission.BLUETOOTH_SCAN) == PackageManager.PERMISSION_GRANTED) scanCallback?.let { scanner?.stopScan(it) }
        scanCallback = null
    }

    override fun onDestroy() {
        stopBleScan()
        super.onDestroy()
    }
}

const val BLE1="2D:7E:1A:02:3D:21"
const val BLE2="55:6D:EA:22:2C:71"
const val BLE3="68:1A:9E:8C:E2:CB"
const val BLE4="25:B6:BE:A7:E6:47"

val ANCHORS = mapOf(
    BLE1 to GeoLocation(0.0, 0.0, 0.0, "BLE1"),
    BLE2 to GeoLocation(15.39, 0.0, 0.0, "BLE2"),
    BLE3 to GeoLocation(15.39, 13.8, 0.0, "BLE3"),
    BLE4 to GeoLocation(long=15.39,27.6,0.0,"BLE4")
)

val POINT_A = GeoLocation(0.0, 0.0, 0.0, "Prof. Manish Panday")
val POINT_B = GeoLocation(8.86, 0.0, 0.0, "Prof. Bholanath Roy")

val TURN= GeoLocation(15.39,0.0,0.0,"Turn")
val POINT_C = GeoLocation(15.39, 7.8,0.0, "Room 202")
val POINT_D= GeoLocation(15.39,24.0,0.0,"Programming lab 2")

val NAV_GRAPH = mapOf(
    POINT_A to mutableListOf(POINT_B),
    POINT_B to mutableListOf(POINT_A,TURN),
    TURN to mutableListOf(POINT_B,POINT_C),
    POINT_C to mutableListOf(TURN,POINT_D),
    POINT_D to mutableListOf(POINT_C)
)

@Preview(showBackground = true)
@Composable
fun ShowRouteContainerPreview(){
    val state = remember { mutableStateMapOf<String, DeviceScanInfo>() }
    ShowRouteContainer(state)
}

@Preview(showBackground = true)
@Composable
fun RouteGraphPreview() {
    RouteGraph(graph = NAV_GRAPH, route = listOf(POINT_A, POINT_B))
}

@SuppressLint("DefaultLocale")
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun ShowRouteContainer(devices: SnapshotStateMap<String, DeviceScanInfo>) {
    var query by remember { mutableStateOf("") }
    var active by remember { mutableStateOf(false) }
    var selectedDestination by remember { mutableStateOf<GeoLocation?>(null) }

    val currentLocation = remember(devices.size, devices.values.map { it.distance }) {
        val detectedAnchors = devices.mapNotNull { (address, info) ->
            ANCHORS[address]?.let { it to info.distance }
        }

        if (detectedAnchors.size >= 2) {
            val (anchors, distances) = detectedAnchors.unzip()
            val pathFinder = PathFind(NAV_GRAPH)
            pathFinder.trilaterate(anchors, distances)
        } else if (detectedAnchors.size == 1) {
            detectedAnchors.first().first
        } else null
    }

    val route = remember(currentLocation, selectedDestination) {
        if (currentLocation != null && selectedDestination != null) {
            val pathFinder = PathFind(NAV_GRAPH)
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

@Composable
fun RouteDisplay(route: List<GeoLocation>) {
    ElevatedCard(
        modifier = Modifier.fillMaxWidth()
    ) {
        Column(modifier = Modifier.padding(16.dp)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Icon(Icons.Default.PlayArrow, contentDescription = null, tint = MaterialTheme.colorScheme.primary)
                Spacer(modifier = Modifier.width(8.dp))
                Text(text = "Navigation Path", style = MaterialTheme.typography.titleMedium)
            }
            HorizontalDivider(modifier = Modifier.padding(vertical = 8.dp))
            LazyColumn(modifier = Modifier.height(120.dp)) {
                items(route) { geo ->
                    Row(
                        modifier = Modifier.padding(vertical = 4.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Box(
                            modifier = Modifier
                                .size(8.dp)
                                .background(MaterialTheme.colorScheme.primary, CircleShape)
                        )
                        Spacer(modifier = Modifier.width(12.dp))
                        Text(
                            text = if (geo.name.isNotEmpty()) geo.name else "(${String.format(Locale.US, "%.2f", geo.long)}, ${String.format(Locale.US, "%.2f", geo.lat)})",
                            style = MaterialTheme.typography.bodyMedium
                        )
                    }
                }
            }
        }
    }
}

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

@Composable
fun BLEContainer(devices: SnapshotStateMap<String, DeviceScanInfo>) {
    if (devices.isEmpty()) return

    Card(
        modifier = Modifier
            .padding(16.dp)
            .fillMaxWidth(),
        colors = CardDefaults.cardColors(
            containerColor = MaterialTheme.colorScheme.surfaceVariant
        )
    ) {
        Column(modifier = Modifier.padding(16.dp)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Icon(Icons.Default.Info, contentDescription = null)
                Spacer(modifier = Modifier.width(8.dp))
                Text(text = "Detected Anchors", style = MaterialTheme.typography.titleSmall)
            }
            HorizontalDivider(modifier = Modifier.padding(vertical = 8.dp))
            devices.forEach { (address, info) ->
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(vertical = 2.dp),
                    horizontalArrangement = Arrangement.SpaceBetween
                ) {
                    Text(
                        text = ANCHORS[address]?.name ?: address,
                        style = MaterialTheme.typography.bodySmall
                    )
                    Text(
                        text = "${String.format(Locale.US, "%.2f", info.distance)}m",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.primary
                    )
                }
            }
        }
    }
}
