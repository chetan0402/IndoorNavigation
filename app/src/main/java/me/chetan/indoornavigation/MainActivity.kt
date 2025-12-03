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
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import androidx.core.app.ActivityCompat
import kotlin.math.pow
import kotlin.math.roundToInt

class MainActivity : ComponentActivity() {
    private var devices by mutableStateOf(mapOf<String, Int>())
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
            setContent {
                BLEContainer(devices)
            }
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
            BLEContainer(devices)
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
                devices = devices.toMutableMap().apply { this[result.device.address] = result.rssi }
                // Log.d("BLE", "Device found: ${result.device.address} RSSI: ${result.rssi}")
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

fun calculateDistance(rssi: Int): Int {
    return 10.0.pow((-64.0 - rssi) / 20.0).roundToInt()
}

@Preview
@Composable
fun BLEContainerPreview() {
    BLEContainer(devices = emptyMap())
}

@Composable
fun BLEContainer(devices: Map<String, Int>) {
    LazyColumn (
        modifier = Modifier.padding(24.dp)
    ) {
        items(devices.toList()) { device ->
            Text(text = "Address: ${device.first}, RSSI: ${device.second}, Distance: ${calculateDistance(device.second)}",modifier = Modifier.background(
                color=if (device.first == "A8:A4:B8:87:69:A0") Color(0xFFFF0000) else Color(0xFFFFFFFF)
            ))
        }
    }
}