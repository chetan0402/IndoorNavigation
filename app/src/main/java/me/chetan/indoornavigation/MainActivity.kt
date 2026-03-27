package me.chetan.indoornavigation

import android.Manifest
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
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.ListItem
import androidx.compose.material3.SearchBar
import androidx.compose.material3.SearchBarDefaults
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateMapOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.runtime.snapshots.SnapshotStateMap
import androidx.compose.ui.Modifier
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import androidx.core.app.ActivityCompat
import androidx.lifecycle.lifecycleScope
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch

data class DeviceScanInfo(
    val distance: Double,
    val predictor: RSSIDistancePredictor,
    val lastSeen: Long
)

const val SCAN_TIMEOUT_MS = 15_000L

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
            Column {
                ShowRouteContainer(devices)
                BLEContainer(devices)
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

val ANCHORS = mapOf(
    BLE1 to GeoLocation(0.0, 0.0, 0.0, "BLE1"),
    BLE2 to GeoLocation(10.0, 0.0, 0.0, "BLE2"),
    BLE3 to GeoLocation(0.0, 11.0, 0.0, "BLE3")
)

val POINT_A = GeoLocation(2.0, 2.0, 0.0, "Room 101")
val POINT_B = GeoLocation(8.0, 2.0, 0.0, "Room 102")
val POINT_C = GeoLocation(5.0, 8.0, 0.0, "Main Hall")
val POINT_D = GeoLocation(5.0, 5.0, 0.0, "Intersection")

val NAV_GRAPH = mapOf(
    POINT_A to mutableListOf(POINT_D),
    POINT_B to mutableListOf(POINT_D),
    POINT_C to mutableListOf(POINT_D),
    POINT_D to mutableListOf(POINT_A, POINT_B, POINT_C)
)

@Preview
@Composable
fun ShowRouteContainerPreview(){
    val state = remember { mutableStateMapOf<String, DeviceScanInfo>() }
    ShowRouteContainer(state)
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun ShowRouteContainer(devices: SnapshotStateMap<String, DeviceScanInfo>){
    var query by remember { mutableStateOf("") }
    var active by remember { mutableStateOf(false) }
    var route by remember { mutableStateOf<List<GeoLocation>?>(null) }

    val searchResults = remember(query) {
        NAV_GRAPH.keys.filter {
            it.name.contains(query, ignoreCase = true) && it.name.isNotEmpty()
        }
    }

    Column {
        Box(modifier = Modifier.padding(24.dp)) {
            SearchBar(
                inputField = {
                    SearchBarDefaults.InputField(
                        query = query,
                        onQueryChange = { query = it },
                        onSearch = { active = false },
                        expanded = active,
                        onExpandedChange = { active = it },
                        placeholder = { Text("Search location...") }
                    )
                },
                expanded = active,
                onExpandedChange = { active = it }
            ) {
                LazyColumn {
                    items(searchResults) { location ->
                        ListItem(
                            headlineContent = { Text(location.name) },
                            modifier = Modifier.clickable {
                                query = location.name
                                active = false
                                // Calculate route
                                val detectedAnchors = devices.mapNotNull { (address, info) ->
                                    ANCHORS[address]?.let { it to info.distance }
                                }

                                if (detectedAnchors.size >= 3) {
                                    val (anchors, distances) = detectedAnchors.unzip()
                                    val pathFinder = PathFind(NAV_GRAPH)
                                    val currentLocation = pathFinder.trilaterate(anchors, distances)
                                    route = pathFinder.route(currentLocation, location)
                                }
                            }
                        )
                    }
                }
            }
        }
        route?.let {
            RouteDisplay(it)
        }
    }
}

@Composable
fun RouteDisplay(route: List<GeoLocation>) {
    Column(modifier = Modifier.padding(24.dp)) {
        Text(text = "Path to Destination:")
        LazyColumn {
            items(route) { geo ->
                Text(
                    text = geo.name.ifEmpty { "(${geo.long}, ${geo.lat})" },
                    modifier = Modifier.padding(vertical = 4.dp)
                )
            }
        }
    }
}

@Composable
fun BLEContainer(devices: SnapshotStateMap<String, DeviceScanInfo>) {
    LazyColumn(
        modifier = Modifier.padding(24.dp)
    ) {
        items(devices.entries.toList()) { (address, info) ->
            Text(text = "Address: $address Distance: ${info.distance}")
        }
    }
}
