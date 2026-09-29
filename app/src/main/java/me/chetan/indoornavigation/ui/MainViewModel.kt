package me.chetan.indoornavigation.ui

import android.annotation.SuppressLint
import android.app.Application
import android.bluetooth.BluetoothManager
import android.bluetooth.le.BluetoothLeScanner
import android.bluetooth.le.ScanCallback
import android.bluetooth.le.ScanResult
import android.content.Context
import android.hardware.Sensor
import android.hardware.SensorEvent
import android.hardware.SensorEventListener
import android.hardware.SensorManager
import android.util.Log
import androidx.compose.runtime.mutableStateMapOf
import androidx.compose.runtime.mutableStateOf
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import me.chetan.indoornavigation.ParticleFilter
import me.chetan.indoornavigation.ParticleFilterDebugInfo
import me.chetan.indoornavigation.RSSIDistancePredictor
import me.chetan.indoornavigation.data.ANCHORS
import me.chetan.indoornavigation.data.DeviceScanInfo
import me.chetan.indoornavigation.data.FilterEstimate
import me.chetan.indoornavigation.data.Measurement
import me.chetan.indoornavigation.data.SCAN_TIMEOUT_MS
import kotlin.time.Duration.Companion.milliseconds

class MainViewModel(application: Application) : AndroidViewModel(application), SensorEventListener {
    val devices = mutableStateMapOf<String, DeviceScanInfo>()
    val userLocation = mutableStateOf(FilterEstimate(0.0, 0.0, 0.0))
    
    // Debug & Telemetry State
    val isDebugModeEnabled = mutableStateOf(false)
    val particleFilterDebugInfo = mutableStateOf<ParticleFilterDebugInfo?>(null)
    val stepCount = mutableStateOf(0)
    val lastStepTimestamp = mutableStateOf(0L)
    val lastStepLength = mutableStateOf(0.7)
    val azimuthDegrees = mutableStateOf(0.0)
    val azimuthRadians = mutableStateOf(0.0)
    val isStepSensorAvailable = mutableStateOf(false)
    val isRotationSensorAvailable = mutableStateOf(false)
    val bleUpdateCount = mutableStateOf(0)
    val lastBleUpdateTimestamp = mutableStateOf(0L)

    private var scanner: BluetoothLeScanner? = null
    private var scanCallback: ScanCallback? = null
    private val particleFilter = ParticleFilter()
    private val measurementBuffer = mutableMapOf<String, Measurement>()
    private var lastUpdateTimestamp = 0L
    private val updateIntervalMs = 500L

    private val sensorManager = application.getSystemService(Context.SENSOR_SERVICE) as SensorManager
    private var stepSensor: Sensor? = sensorManager.getDefaultSensor(Sensor.TYPE_STEP_DETECTOR)
    private var rotationSensor: Sensor? = sensorManager.getDefaultSensor(Sensor.TYPE_ROTATION_VECTOR)
    
    private var currentDirection = 0.0

    init {
        val bluetoothManager = application.getSystemService(Context.BLUETOOTH_SERVICE) as BluetoothManager
        scanner = bluetoothManager.adapter?.bluetoothLeScanner
        
        isStepSensorAvailable.value = (stepSensor != null)
        isRotationSensorAvailable.value = (rotationSensor != null)

        // Initialize bounds based on anchors
        val xCoords = ANCHORS.values.map { it.long }
        val yCoords = ANCHORS.values.map { it.lat }
        val zCoords = ANCHORS.values.map { it.alt }
        
        particleFilter.setBounds(
            xMin = (xCoords.minOrNull() ?: 0.0) - 5.0,
            yMin = (yCoords.minOrNull() ?: 0.0) - 5.0,
            zMin = (zCoords.minOrNull() ?: 0.0) - 2.0,
            xMax = (xCoords.maxOrNull() ?: 15.39) + 5.0,
            yMax = (yCoords.maxOrNull() ?: 27.6) + 5.0,
            zMax = (zCoords.maxOrNull() ?: 0.0) + 2.0
        )

        particleFilterDebugInfo.value = particleFilter.getDebugInfo()
        startCleanupTask()
        registerSensors()
    }

    private fun registerSensors() {
        if (stepSensor == null) {
            Log.e("Sensor", "Step Detector sensor not available on this device")
        } else {
            sensorManager.registerListener(this, stepSensor, SensorManager.SENSOR_DELAY_FASTEST)
        }
        
        if (rotationSensor == null) {
            Log.e("Sensor", "Rotation Vector sensor not available on this device")
        } else {
            sensorManager.registerListener(this, rotationSensor, SensorManager.SENSOR_DELAY_FASTEST)
        }
    }

    fun toggleDebugMode() {
        isDebugModeEnabled.value = !isDebugModeEnabled.value
    }

    override fun onSensorChanged(event: SensorEvent) {
        when (event.sensor.type) {
            Sensor.TYPE_STEP_DETECTOR -> {
                // Step detected: predict movement
                stepCount.value++
                lastStepTimestamp.value = System.currentTimeMillis()
                particleFilter.predict(step = 0.7, phoneAzimuth = currentDirection, variance = 0.05)
                userLocation.value = particleFilter.estimate()
                particleFilterDebugInfo.value = particleFilter.getDebugInfo()
                Log.d("Sensor", "Step detected! Step count: ${stepCount.value}, Azimuth: $currentDirection")
            }
            Sensor.TYPE_ROTATION_VECTOR -> {
                val rotationMatrix = FloatArray(9)
                SensorManager.getRotationMatrixFromVector(rotationMatrix, event.values)
                val orientation = FloatArray(3)
                SensorManager.getOrientation(rotationMatrix, orientation)
                currentDirection = orientation[0].toDouble() // Azimuth (yaw) in radians
                azimuthRadians.value = currentDirection
                var deg = Math.toDegrees(currentDirection)
                if (deg < 0) deg += 360.0
                azimuthDegrees.value = deg
            }
        }
    }

    override fun onAccuracyChanged(sensor: Sensor?, accuracy: Int) {}

    private fun startCleanupTask() {
        viewModelScope.launch {
            while (true) {
                delay(1000.milliseconds)
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
                val dis = predictor.predict(result.rssi.toDouble())
                
                devices[address] = DeviceScanInfo(dis, predictor, System.currentTimeMillis())
                
                // Buffer measurement for batch update
                val anchor = ANCHORS[address]!!
                measurementBuffer[address] = Measurement(
                    x = anchor.long,
                    y = anchor.lat,
                    z = anchor.alt,
                    radius = dis,
                    variance = 5.0
                )

                val currentTime = System.currentTimeMillis()
                if (currentTime - lastUpdateTimestamp > updateIntervalMs) {
                    particleFilter.update(measurementBuffer.values.toList())
                    measurementBuffer.clear()
                    lastUpdateTimestamp = currentTime
                    bleUpdateCount.value++
                    lastBleUpdateTimestamp.value = currentTime
                    userLocation.value = particleFilter.estimate()
                    particleFilterDebugInfo.value = particleFilter.getDebugInfo()
                }
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
        stopBleScan()
        sensorManager.unregisterListener(this)
    }
}
