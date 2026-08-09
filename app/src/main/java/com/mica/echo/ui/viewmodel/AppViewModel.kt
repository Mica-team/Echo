package com.mica.echo.ui.viewmodel

import android.content.Context
import android.util.Log
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.mica.echo.bluetooth.EchoBluetoothDevice
import com.mica.echo.bluetooth.EchoBluetoothManager
import com.mica.echo.data.ControlCommand
import com.mica.echo.data.DeviceState
import com.mica.echo.data.TelemetryData
import com.mica.echo.settings.WifiNetwork
import com.mica.echo.settings.WifiSecurity
import com.mica.echo.settings.WifiManager as EchoWifiManager
import kotlinx.coroutines.CoroutineExceptionHandler
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch

class AppViewModel(context: Context) : ViewModel() {
    private val appContext = context.applicationContext
    private val bluetooth = EchoBluetoothManager(appContext)
    private val wifi = EchoWifiManager(appContext)
    private val preferences = appContext.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
    private val bluetoothExceptionHandler = CoroutineExceptionHandler { _, throwable ->
        Log.e(TAG, "Unhandled Bluetooth coroutine failure", throwable)
        _deviceState.value = DeviceState()
    }
    private val _deviceState = MutableStateFlow(DeviceState())
    val deviceState: StateFlow<DeviceState> = _deviceState.asStateFlow()
    private val _telemetryData = MutableStateFlow(TelemetryData())
    val telemetryData: StateFlow<TelemetryData> = _telemetryData.asStateFlow()
    private val _availableDevices = MutableStateFlow<List<EchoBluetoothDevice>>(emptyList())
    val availableDevices: StateFlow<List<EchoBluetoothDevice>> = _availableDevices.asStateFlow()
    private val _wifiNetworks = MutableStateFlow<List<WifiNetwork>>(emptyList())
    val wifiNetworks: StateFlow<List<WifiNetwork>> = _wifiNetworks.asStateFlow()
    private val _wifiCurrentSsid = MutableStateFlow<String?>(wifi.currentSsid())
    val wifiCurrentSsid: StateFlow<String?> = _wifiCurrentSsid.asStateFlow()
    private val _wifiSavedSsid = MutableStateFlow<String?>(wifi.savedSsid())
    val wifiSavedSsid: StateFlow<String?> = _wifiSavedSsid.asStateFlow()
    private val _wifiPasswordRequest = MutableStateFlow<WifiNetwork?>(null)
    val wifiPasswordRequest: StateFlow<WifiNetwork?> = _wifiPasswordRequest.asStateFlow()
    private val _espWifiPasswordRequest = MutableStateFlow<String?>(null)
    val espWifiPasswordRequest: StateFlow<String?> = _espWifiPasswordRequest.asStateFlow()
    private val _wifiStatus = MutableStateFlow<String?>(null)
    val wifiStatus: StateFlow<String?> = _wifiStatus.asStateFlow()
    private val _controlCommands = MutableStateFlow(listOf(
        ControlCommand("PING", "Ping", "Check if Echo is responding", false),
        ControlCommand("STATUS", "Status", "Request Echo system status", false),
        ControlCommand("WIFI", "Wi-Fi", "Check Echo Wi-Fi status", false),
        ControlCommand("OTA", "Check Update", "Check for a firmware update", false),
        ControlCommand("REBOOT", "Reboot", "Restart Echo", false)
    ))
    val controlCommands: StateFlow<List<ControlCommand>> = _controlCommands.asStateFlow()
    private val _settings = MutableStateFlow(mapOf(
        "theme_mode" to preferences.getString(KEY_THEME_MODE, "dark").orEmpty(),
        "auto_refresh" to "true",
        "log_level" to "info"
    ))
    val settings: StateFlow<Map<String, String>> = _settings.asStateFlow()

    init {
        bluetooth.setListeners(
            devicesChanged = { devices -> _availableDevices.value = devices },
            connectionChanged = { device, connected ->
                try {
                    if (connected && device != null) {
                        val name = try { device.name ?: "Echo" } catch (_: SecurityException) { "Echo" }
                        val address = try { device.address } catch (_: SecurityException) { "" }
                        _deviceState.value = DeviceState(name = name, address = address, isConnected = true, signalStrength = 0, batteryLevel = 0)
                        if (address.isNotBlank()) preferences.edit().putString(KEY_LAST_DEVICE_ADDRESS, address).apply()
                        requestEspWifiProvisioning()
                    } else {
                        _deviceState.value = DeviceState()
                        _espWifiPasswordRequest.value = null
                        _wifiPasswordRequest.value = null
                    }
                } catch (e: Exception) {
                    Log.e(TAG, "Bluetooth connection state callback failed", e)
                    _deviceState.value = DeviceState()
                    _espWifiPasswordRequest.value = null
                    _wifiPasswordRequest.value = null
                }
            }
        )
        bluetooth.setDataListener { line -> handleBluetoothData(line) }
        val lastAddress = preferences.getString(KEY_LAST_DEVICE_ADDRESS, null)
        if (!lastAddress.isNullOrBlank()) viewModelScope.launch(bluetoothExceptionHandler) { bluetooth.connect(lastAddress) }
    }

    fun scanWifiNetworks() {
        if (!wifi.hasScanPermission()) {
            _wifiStatus.value = "Allow Echo to use precise location to scan Wi-Fi"
            _wifiNetworks.value = emptyList()
            return
        }
        if (!wifi.locationServicesEnabled()) {
            _wifiStatus.value = "Turn on Location services to scan nearby Wi-Fi"
            _wifiNetworks.value = emptyList()
            return
        }
        viewModelScope.launch {
            try {
                _wifiStatus.value = "Scanning for nearby Wi-Fi…"
                val networks = wifi.scan()
                _wifiNetworks.value = networks
                _wifiCurrentSsid.value = wifi.currentSsid()
                _wifiStatus.value = if (networks.isEmpty()) "No Wi-Fi networks found. Make sure Wi-Fi is on and Location is enabled." else "Found ${networks.size} Wi-Fi network${if (networks.size == 1) "" else "s"}"
            } catch (e: Exception) {
                Log.e(TAG, "Wi-Fi scan failed", e)
                _wifiNetworks.value = emptyList()
                _wifiStatus.value = "Unable to scan for Wi-Fi networks"
            }
        }
    }

    fun requestWifiPassword(network: WifiNetwork) { _wifiPasswordRequest.value = network; _wifiStatus.value = null }

    fun connectToWifi(network: WifiNetwork, password: String = "") {
        if (network.security != WifiSecurity.OPEN && password.isBlank()) return
        viewModelScope.launch {
            val result = wifi.connect(network, password)
            if (result.isSuccess) {
                _wifiSavedSsid.value = network.ssid
                _wifiPasswordRequest.value = null
                _wifiStatus.value = "Saved ${network.ssid}. Android will manage reconnection."
                _wifiCurrentSsid.value = network.ssid
            } else _wifiStatus.value = result.exceptionOrNull()?.message ?: "Could not save Wi-Fi network"
        }
    }

    fun cancelWifiPasswordRequest() { _wifiPasswordRequest.value = null }
    fun forgetSavedWifi() { wifi.forgetSavedNetwork(); _wifiSavedSsid.value = null; _wifiStatus.value = "Saved Wi-Fi network cleared" }

    fun requestEspWifiProvisioning() {
        if (!_deviceState.value.isConnected) return
        try {
            val androidWifi = appContext.getSystemService(Context.WIFI_SERVICE) as android.net.wifi.WifiManager
            @Suppress("DEPRECATION") val rawSsid = androidWifi.connectionInfo?.ssid
            val ssid = rawSsid?.trim()?.removePrefix("\"")?.removeSuffix("\"")
            _espWifiPasswordRequest.value = if (!ssid.isNullOrBlank() && ssid != "<unknown ssid>") ssid else ""
        } catch (e: Exception) { Log.e(TAG, "Failed to detect phone Wi-Fi SSID", e); _espWifiPasswordRequest.value = "" }
    }

    fun submitEspWifiPassword(password: String, manualSsid: String = "") {
        val detectedSsid = _espWifiPasswordRequest.value
        val ssid = if (!detectedSsid.isNullOrBlank()) detectedSsid else manualSsid.trim()
        if (ssid.isBlank() || password.isBlank() || !_deviceState.value.isConnected) return
        viewModelScope.launch(bluetoothExceptionHandler) {
            try {
                if (!bluetooth.send("WIFI_SSID=$ssid")) return@launch
                if (!bluetooth.send("WIFI_PASS=$password")) return@launch
                _espWifiPasswordRequest.value = null
            } catch (e: Exception) { Log.e(TAG, "Failed to provision Echo Wi-Fi", e) }
        }
    }
    fun cancelEspWifiProvisioning() { _espWifiPasswordRequest.value = null }

    private fun handleBluetoothData(line: String) {
        val data = line.trim()
        Log.d(TAG, "ESP32 data: $data")
        if (data.startsWith("TEMP:", ignoreCase = true)) {
            val temperature = data.substringAfter(":").trim().toFloatOrNull() ?: return
            val now = System.currentTimeMillis()
            _telemetryData.value = _telemetryData.value.copy(temperature = temperature, timestamp = now)
            if (_deviceState.value.isConnected) _deviceState.value = _deviceState.value.copy(lastUpdate = now)
        }
    }

    fun scanDevices() { try { bluetooth.scan() } catch (e: Exception) { Log.e(TAG, "Bluetooth scan request failed", e); _availableDevices.value = emptyList() } }
    fun connectDevice(device: EchoBluetoothDevice) { viewModelScope.launch(bluetoothExceptionHandler) { try { if (!bluetooth.connect(device.address)) _deviceState.value = DeviceState() } catch (e: Exception) { Log.e(TAG, "Bluetooth connect request failed", e); _deviceState.value = DeviceState() } } }
    fun disconnectDevice() { try { bluetooth.disconnect() } catch (e: Exception) { Log.w(TAG, "Bluetooth disconnect failed", e) }; _telemetryData.value = TelemetryData(); _wifiPasswordRequest.value = null; _espWifiPasswordRequest.value = null }
    fun updateTelemetry() { if (_deviceState.value.isConnected) Log.d(TAG, "Waiting for real ESP32 telemetry") }
    fun executeCommand(commandId: String) {
        if (!_deviceState.value.isConnected) return
        if (_controlCommands.value.none { it.id == commandId }) return
        viewModelScope.launch(bluetoothExceptionHandler) {
            try { if (bluetooth.send(commandId)) _controlCommands.value = _controlCommands.value.map { if (it.id == commandId) it.copy(isActive = !it.isActive) else it } }
            catch (e: Exception) { Log.e(TAG, "Bluetooth command failed", e) }
        }
    }
    fun getSetting(key: String): String = _settings.value[key].orEmpty()
    fun setSetting(key: String, value: String) { _settings.value = _settings.value + (key to value); if (key == KEY_THEME_MODE) preferences.edit().putString(KEY_THEME_MODE, value).apply() }
    override fun onCleared() { try { bluetooth.close() } catch (e: Exception) { Log.w(TAG, "Bluetooth cleanup failed", e) }; super.onCleared() }
    companion object { private const val TAG = "EchoViewModel"; private const val PREFS_NAME = "echo_preferences"; private const val KEY_LAST_DEVICE_ADDRESS = "last_bluetooth_device_address"; private const val KEY_THEME_MODE = "theme_mode" }
}
