package me.chetan.indoornavigation.ui

import android.annotation.SuppressLint
import android.app.Application
import android.bluetooth.BluetoothManager
import android.bluetooth.le.BluetoothLeScanner
import android.bluetooth.le.ScanCallback
import android.bluetooth.le.ScanResult
import android.content.Context
import android.util.Log
import androidx.compose.runtime.mutableStateMapOf
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import me.chetan.indoornavigation.RSSIDistancePredictor
import me.chetan.indoornavigation.data.ANCHORS
import me.chetan.indoornavigation.data.DeviceScanInfo
import me.chetan.indoornavigation.data.SCAN_TIMEOUT_MS

class MainViewModel(application: Application) : AndroidViewModel(application) {
    val devices = mutableStateMapOf<String, DeviceScanInfo>()
    
    private var scanner: BluetoothLeScanner? = null
    private var scanCallback: ScanCallback? = null

    init {
        val bluetoothManager = application.getSystemService(Context.BLUETOOTH_SERVICE) as BluetoothManager
        scanner = bluetoothManager.adapter?.bluetoothLeScanner
        
        startCleanupTask()
    }

    private fun startCleanupTask() {
        viewModelScope.launch {
            while (true) {
                delay(1000)
                val currentTime = System.currentTimeMillis()
                val toRemove = devices.filter { (_, info) ->
                    currentTime - info.lastSeen > SCAN_TIMEOUT_MS
                }.keys
                toRemove.forEach { devices.remove(it) }
            }
        }
    }

    @SuppressLint("MissingPermission")
    fun startBleScan() {
        if (scanCallback != null) return

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

    @SuppressLint("MissingPermission")
    fun stopBleScan() {
        Log.d("BLE", "Stopping BLE scan")
        scanCallback?.let { scanner?.stopScan(it) }
        scanCallback = null
    }

    override fun onCleared() {
        super.onCleared()
        stopBleScan()
    }
}
