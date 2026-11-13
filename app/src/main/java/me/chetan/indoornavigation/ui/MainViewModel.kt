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
import androidx.compose.runtime.mutableDoubleStateOf
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableLongStateOf
import androidx.compose.runtime.mutableStateMapOf
import androidx.compose.runtime.mutableStateOf
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import me.chetan.indoornavigation.ParticleFilter
import me.chetan.indoornavigation.ParticleFilterDebugInfo
import me.chetan.indoornavigation.PathFind
import me.chetan.indoornavigation.RSSIDistancePredictor
import me.chetan.indoornavigation.data.ANCHORS
import me.chetan.indoornavigation.data.ConfigurationParser
import me.chetan.indoornavigation.data.DEFAULT_BLE_TEXT
import me.chetan.indoornavigation.data.DEFAULT_GRAPH_TEXT
import me.chetan.indoornavigation.data.DeviceScanInfo
import me.chetan.indoornavigation.data.FilterEstimate
import me.chetan.indoornavigation.data.GeoLocation
import me.chetan.indoornavigation.data.Measurement
import me.chetan.indoornavigation.data.NAV_GRAPH
import me.chetan.indoornavigation.data.ParseResult
import me.chetan.indoornavigation.data.SCAN_TIMEOUT_MS
import kotlin.time.Duration.Companion.milliseconds

private const val PREFS_NAME = "indoor_nav_prefs"
private const val KEY_GRAPH_TEXT = "key_graph_text"
private const val KEY_BLE_TEXT = "key_ble_text"

class MainViewModel(application: Application) : AndroidViewModel(application), SensorEventListener {
    private val prefs = application.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)

    val devices = mutableStateMapOf<String, DeviceScanInfo>()
    val userLocation = mutableStateOf(FilterEstimate(0.0, 0.0, 0.0))
    val isLocationInitialized = mutableStateOf(false)

    // Configuration & Settings State
    val isSettingsOpen = mutableStateOf(false)
    val graphText = mutableStateOf("")
    val bleText = mutableStateOf("")
    val graphError = mutableStateOf<String?>(null)
    val bleError = mutableStateOf<String?>(null)

    val navGraph = mutableStateOf<Map<GeoLocation, List<GeoLocation>>>(NAV_GRAPH)
    val anchors = mutableStateOf<Map<String, GeoLocation>>(ANCHORS)

    // Debug & Telemetry State
    val isDebugModeEnabled = mutableStateOf(false)
    val particleFilterDebugInfo = mutableStateOf<ParticleFilterDebugInfo?>(null)
    val stepCount = mutableIntStateOf(0)
    val lastStepTimestamp = mutableLongStateOf(0L)
    val lastStepLength = mutableDoubleStateOf(0.7)
    val isStepSensorAvailable = mutableStateOf(false)
    val bleUpdateCount = mutableIntStateOf(0)
    val lastBleUpdateTimestamp = mutableLongStateOf(0L)

    private var scanner: BluetoothLeScanner? = null
    private var scanCallback: ScanCallback? = null
    private val particleFilter = ParticleFilter()
    private val measurementBuffer = mutableMapOf<String, Measurement>()
    private var lastUpdateTimestamp = 0L
    private val updateIntervalMs = 500L

    private val sensorManager = application.getSystemService(Context.SENSOR_SERVICE) as SensorManager
    private var stepSensor: Sensor? = sensorManager.getDefaultSensor(Sensor.TYPE_STEP_DETECTOR)

    init {
        val bluetoothManager = application.getSystemService(Context.BLUETOOTH_SERVICE) as BluetoothManager
        scanner = bluetoothManager.adapter?.bluetoothLeScanner
        
        isStepSensorAvailable.value = (stepSensor != null)

        loadConfigurationFromPrefs()
        particleFilterDebugInfo.value = particleFilter.getDebugInfo()
        startCleanupTask()
        registerSensors()
    }

    private fun loadConfigurationFromPrefs() {
        val savedGraphText = prefs.getString(KEY_GRAPH_TEXT, DEFAULT_GRAPH_TEXT) ?: DEFAULT_GRAPH_TEXT
        val savedBleText = prefs.getString(KEY_BLE_TEXT, DEFAULT_BLE_TEXT) ?: DEFAULT_BLE_TEXT

        graphText.value = savedGraphText
        bleText.value = savedBleText

        val graphParse = ConfigurationParser.parseGraphText(savedGraphText)
        if (graphParse is ParseResult.Success) {
            navGraph.value = graphParse.data
        }

        val bleParse = ConfigurationParser.parseAnchorsText(savedBleText)
        if (bleParse is ParseResult.Success) {
            anchors.value = bleParse.data
        }
    }

    fun openSettings() {
        isSettingsOpen.value = true
    }

    fun closeSettings() {
        isSettingsOpen.value = false
    }

    fun updateGraphText(text: String) {
        graphText.value = text
        graphError.value = null
    }

    fun updateBleText(text: String) {
        bleText.value = text
        bleError.value = null
    }

    fun saveConfiguration(): Boolean {
        var hasError = false

        val graphParse = ConfigurationParser.parseGraphText(graphText.value)
        val parsedGraph = when (graphParse) {
            is ParseResult.Success -> {
                graphError.value = null
                graphParse.data
            }
            is ParseResult.Error -> {
                graphError.value = graphParse.message
                hasError = true
                null
            }
        }

        val bleParse = ConfigurationParser.parseAnchorsText(bleText.value)
        val parsedAnchors = when (bleParse) {
            is ParseResult.Success -> {
                bleError.value = null
                bleParse.data
            }
            is ParseResult.Error -> {
                bleError.value = bleParse.message
                hasError = true
                null
            }
        }

        if (hasError || parsedGraph == null || parsedAnchors == null) {
            return false
        }

        prefs.edit()
            .putString(KEY_GRAPH_TEXT, graphText.value)
            .putString(KEY_BLE_TEXT, bleText.value)
            .apply()

        navGraph.value = parsedGraph
        anchors.value = parsedAnchors
        userLocation.value = particleFilter.estimate(parsedAnchors)
        particleFilterDebugInfo.value = particleFilter.getDebugInfo()

        closeSettings()
        return true
    }

    fun resetConfigurationToDefaults() {
        graphText.value = DEFAULT_GRAPH_TEXT
        bleText.value = DEFAULT_BLE_TEXT
        graphError.value = null
        bleError.value = null

        prefs.edit()
            .remove(KEY_GRAPH_TEXT)
            .remove(KEY_BLE_TEXT)
            .apply()

        val graphParse = ConfigurationParser.parseGraphText(DEFAULT_GRAPH_TEXT)
        if (graphParse is ParseResult.Success) {
            navGraph.value = graphParse.data
        }

        val bleParse = ConfigurationParser.parseAnchorsText(DEFAULT_BLE_TEXT)
        if (bleParse is ParseResult.Success) {
            anchors.value = bleParse.data
            userLocation.value = particleFilter.estimate(bleParse.data)
        }
    }

    private fun registerSensors() {
        if (stepSensor == null) {
            Log.e("Sensor", "Step Detector sensor not available on this device")
        } else {
            sensorManager.registerListener(this, stepSensor, SensorManager.SENSOR_DELAY_FASTEST)
        }
    }

    fun toggleDebugMode() {
        isDebugModeEnabled.value = !isDebugModeEnabled.value
    }

    fun absUpdate(x: Double, y: Double, z: Double) {
        particleFilter.absUpdate(x, y, z)
        isLocationInitialized.value = true
        userLocation.value = particleFilter.estimate(anchors.value)
        particleFilterDebugInfo.value = particleFilter.getDebugInfo()
        Log.d("ParticleFilter", "Absolute update from QR code: x=$x, y=$y, z=$z")
    }

    override fun onSensorChanged(event: SensorEvent) {
        when (event.sensor.type) {
            Sensor.TYPE_STEP_DETECTOR -> {
                // Step detected: predict movement
                stepCount.intValue++
                lastStepTimestamp.longValue = System.currentTimeMillis()
                particleFilter.predict(step = 0.7, variance = 0.05)
                userLocation.value = particleFilter.estimate(anchors.value)
                particleFilterDebugInfo.value = particleFilter.getDebugInfo()
                Log.d("Sensor", "Step detected! Step count: ${stepCount.intValue}")
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
                val activeAnchors = anchors.value
                if (address !in activeAnchors) return
                
                val currentInfo = devices[address]
                val predictor = currentInfo?.predictor ?: RSSIDistancePredictor()
                val dis = predictor.predict(result.rssi.toDouble())
                
                devices[address] = DeviceScanInfo(dis, predictor, System.currentTimeMillis())
                
                // Buffer measurement for batch update
                val anchor = activeAnchors[address]!!
                measurementBuffer[address] = Measurement(
                    x = anchor.long,
                    y = anchor.lat,
                    z = anchor.alt,
                    radius = dis,
                    variance = 5.0
                )

                val currentTime = System.currentTimeMillis()
                if (currentTime - lastUpdateTimestamp > updateIntervalMs) {
                    val activeBleDevices = devices.filter { (addr, info) ->
                        addr in activeAnchors && currentTime - info.lastSeen <= SCAN_TIMEOUT_MS
                    }

                    if (!particleFilter.isInitialized) {
                        if (activeBleDevices.size >= 2) {
                            val anchorList = mutableListOf<GeoLocation>()
                            val distanceList = mutableListOf<Double>()
                            for ((addr, info) in activeBleDevices) {
                                val anchorGeo = activeAnchors[addr]
                                if (anchorGeo != null) {
                                    anchorList.add(anchorGeo)
                                    distanceList.add(info.distance)
                                }
                            }
                            if (anchorList.size >= 2) {
                                val pathFind = PathFind(navGraph.value.mapValues { it.value.toMutableList() })
                                val initialLocation = pathFind.trilaterate(anchorList, distanceList)
                                particleFilter.absUpdate(initialLocation.long, initialLocation.lat, initialLocation.alt)
                                isLocationInitialized.value = true
                                Log.d("ParticleFilter", "Initialized via trilateration from ${anchorList.size} BLE signals: $initialLocation")
                            }
                        }
                    }

                    if (particleFilter.isInitialized) {
                        val closestMeasurements = measurementBuffer.values.sortedBy { it.radius }.take(2)
                        if (closestMeasurements.isNotEmpty()) {
                            particleFilter.update(closestMeasurements)
                        }
                    }

                    measurementBuffer.clear()
                    lastUpdateTimestamp = currentTime
                    bleUpdateCount.intValue++
                    lastBleUpdateTimestamp.longValue = currentTime
                    userLocation.value = particleFilter.estimate(activeAnchors)
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
