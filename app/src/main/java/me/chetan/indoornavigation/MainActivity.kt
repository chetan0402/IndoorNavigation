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
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.mutableStateMapOf
import androidx.compose.runtime.snapshots.SnapshotStateMap
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import androidx.core.app.ActivityCompat
import java.math.RoundingMode

class MainActivity : ComponentActivity() {
    private var devices = mutableStateMapOf<String, Pair<Double, RSSIDistancePredictor>>()
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

        setContent {
            Column {
                TrilaterateContainer(devices)
                DistanceContainer(devices)
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
                if(result.device.address != BLE1 && result.device.address != BLE2 && result.device.address != BLE3) return
                if (devices[result.device.address] == null) {
                    devices[result.device.address]= 0.0 to RSSIDistancePredictor()
                }
                devices[result.device.address]?.let {
                    val predictor = it.second
                    val (dis, _) = predictor.predict(result.rssi.toDouble())
                    devices[result.device.address] = dis to predictor
                }

                //if(result.device.address == "2D:7E:1A:02:3D:21") Log.d("BLE_READ", "Device found: ${result.device.address} RSSI: ${result.rssi}")
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

//@Preview
//@Composable
//fun BLEContainerPreview() {
//    BLEContainer(devices = emptyMap())
//}

const val BLE1="2D:7E:1A:02:3D:21"
const val BLE2="55:6D:EA:22:2C:71"
const val BLE3="68:1A:9E:8C:E2:CB"

@Composable
fun BLEContainer(devices: SnapshotStateMap<String, Pair<Double, RSSIDistancePredictor>>) {
    LazyColumn (
        modifier = Modifier.padding(24.dp)
    ) {
        items(devices.entries.toList()) { (address, pair) ->
            Text(text = "Address: $address Distance: ${pair.first}")
        }
    }
}

@Composable
fun DistanceContainer(devices: SnapshotStateMap<String, Pair<Double, RSSIDistancePredictor>>){
    Box(modifier = Modifier.padding(24.dp)) {
        val dis1=devices[BLE1]
        val dis2=devices[BLE2]
        if (dis1!=null && dis2!=null) {
            Text(
                text = "Distance from BLE A: ${
                    LineConstrainedTrilateration.estimate(
                        Vec2(0.0, 0.0),
                        Vec2(10.44, 0.0),
                        dis1.first.toBigDecimal(),
                        dis2.first.toBigDecimal()
                    ).x.toBigDecimal().setScale(2, RoundingMode.HALF_EVEN)
                }"
            )
        }
    }
}

@Composable
fun TrilaterateContainer(devices: SnapshotStateMap<String, Pair<Double, RSSIDistancePredictor>>){
    Box(modifier=Modifier.padding(24.dp)){
        val dis1=devices[BLE1]
        val dis2=devices[BLE2]
        val dis3=devices[BLE3]
        if (dis1!=null && dis2!=null && dis3!=null){
            Text(
                text = "${PathFind(emptyMap()).trilaterate(
                    listOf(
                        GeoLocation(0.0,0.0,0.0,"BLE1"),
                        GeoLocation(10.0,0.0,0.0,"BLE2"),
                        GeoLocation(0.0,11.0,0.0,"BLE3")
                    ),
                    listOf(
                        dis1.first,
                        dis2.first,
                        dis3.first
                    )
                )}"
            )
        }
    }
}