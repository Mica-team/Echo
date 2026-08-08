package com.mica.echo.ui.viewmodel

import android.content.Context
import android.net.wifi.WifiManager
import android.util.Log
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.mica.echo.bluetooth.EchoBluetoothDevice
import com.mica.echo.bluetooth.EchoBluetoothManager
import com.mica.echo.data.ControlCommand
import com.mica.echo.data.DeviceState
import com.mica.echo.data.TelemetryData
import kotlinx.coroutines.CoroutineExceptionHandler
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch

class AppViewModel(context: Context) : ViewModel() {

    private val appContext = context.applicationContext
    private val bluetooth = EchoBluetoothManager(appContext)
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

    // null = no dialog. Empty string = dialog should be shown but Android could not
    // automatically read the SSID, so the user must enter it.
    private val _wifiPasswordRequest = MutableStateFlow<String?>(null)
    val wifiPasswordRequest: StateFlow<String?> = _wifiPasswordRequest.asStateFlow()

    private val _controlCommands = MutableStateFlow(
        listOf(
            ControlCommand("PING", "Ping", "Check if Echo is responding", false),
            ControlCommand("STATUS", "Status", "Request Echo system status", false),
            ControlCommand("WIFI", "Wi-Fi", "Check Echo Wi-Fi status", false),
            ControlCommand("OTA", "Check Update", "Check for a firmware update", false),
            ControlCommand("REBOOT", "Reboot", "Restart Echo", false)
        )
    )
    val controlCommands: StateFlow<List<ControlCommand>> = _controlCommands.asStateFlow()

    private val _settings = MutableStateFlow(
        mapOf("theme_mode" to "dark", "auto_refresh" to "true", "log_level" to "info")
    )
    val settings: StateFlow<Map<String, String>> = _settings.asStateFlow()

    init {
        bluetooth.setListeners(
            devicesChanged = { devices -> _availableDevices.value = devices },
            connectionChanged = { device, connected ->
                try {
                    if (connected && device != null) {
                        val name = try { device.name ?: "Echo" } catch (_: SecurityException) { "Echo" }
                        val address = try { device.address } catch (_: SecurityException) { "" }

                        _deviceState.value = DeviceState(
                            name = name,
                            address = address,
                            isConnected = true,
                            signalStrength = 0,
                            batteryLevel = 0
                        )

                        if (address.isNotBlank()) {
                            preferences.edit()
                                .putString(KEY_LAST_DEVICE_ADDRESS, address)
                                .apply()
                        }

                        // Always open the Wi-Fi setup prompt after a successful Echo connection.
                        requestWifiProvisioning()
                    } else {
                        _deviceState.value = DeviceState()
                        _wifiPasswordRequest.value = null
                    }
                } catch (e: Exception) {
                    Log.e(TAG, "Bluetooth connection state callback failed", e)
                    _deviceState.value = DeviceState()
                    _wifiPasswordRequest.value = null
                }
            }
        )

        bluetooth.setDataListener { line -> handleBluetoothData(line) }

        val lastAddress = preferences.getString(KEY_LAST_DEVICE_ADDRESS, null)
        if (!lastAddress.isNullOrBlank()) {
            viewModelScope.launch(bluetoothExceptionHandler) {
                bluetooth.connect(lastAddress)
            }
        }
    }

    fun requestWifiProvisioning() {
        if (!_deviceState.value.isConnected) return

        try {
            val wifiManager = appContext.getSystemService(Context.WIFI_SERVICE) as WifiManager

            @Suppress("DEPRECATION")
            val rawSsid = wifiManager.connectionInfo?.ssid

            val ssid = rawSsid
                ?.trim()
                ?.removePrefix("\"")
                ?.removeSuffix("\"")

            if (!ssid.isNullOrBlank() && ssid != "<unknown ssid>") {
                Log.d(TAG, "Detected phone Wi-Fi SSID: $ssid")
                _wifiPasswordRequest.value = ssid
            } else {
                // Still show the dialog. The user can enter the SSID manually if Android
                // does not allow the app to read it automatically.
                Log.w(TAG, "Could not automatically detect Wi-Fi SSID; asking user")
                _wifiPasswordRequest.value = ""
            }
        } catch (e: Exception) {
            Log.e(TAG, "Failed to detect Wi-Fi SSID", e)
            _wifiPasswordRequest.value = ""
        }
    }

    fun submitWifiPassword(password: String, manualSsid: String = "") {
        val detectedSsid = _wifiPasswordRequest.value
        val ssid = if (!detectedSsid.isNullOrBlank()) detectedSsid else manualSsid.trim()

        if (ssid.isBlank() || password.isBlank() || !_deviceState.value.isConnected) return

        viewModelScope.launch(bluetoothExceptionHandler) {
            try {
                if (!bluetooth.send("WIFI_SSID=$ssid")) {
                    Log.e(TAG, "Failed to send Wi-Fi SSID")
                    return@launch
                }

                if (!bluetooth.send("WIFI_PASS=$password")) {
                    Log.e(TAG, "Failed to send Wi-Fi password")
                    return@launch
                }

                Log.d(TAG, "Wi-Fi credentials sent to Echo")
                _wifiPasswordRequest.value = null
            } catch (e: Exception) {
                Log.e(TAG, "Failed to provision Echo Wi-Fi", e)
            }
        }
    }

    fun cancelWifiProvisioning() {
        _wifiPasswordRequest.value = null
    }

    private fun handleBluetoothData(line: String) {
        val data = line.trim()
        Log.d(TAG, "ESP32 data: $data")

        if (data.startsWith("TEMP:", ignoreCase = true)) {
            val temperature = data.substringAfter(":").trim().toFloatOrNull() ?: return
            val now = System.currentTimeMillis()
            _telemetryData.value = _telemetryData.value.copy(temperature = temperature, timestamp = now)
            if (_deviceState.value.isConnected) {
                _deviceState.value = _deviceState.value.copy(lastUpdate = now)
            }
            Log.d(TAG, "Real ESP32 CPU temperature: $temperature °C")
        }
    }

    fun scanDevices() {
        try { bluetooth.scan() } catch (e: Exception) {
            Log.e(TAG, "Bluetooth scan request failed", e)
            _availableDevices.value = emptyList()
        }
    }

    fun connectDevice(device: EchoBluetoothDevice) {
        viewModelScope.launch(bluetoothExceptionHandler) {
            try {
                if (!bluetooth.connect(device.address)) _deviceState.value = DeviceState()
            } catch (e: Exception) {
                Log.e(TAG, "Bluetooth connect request failed", e)
                _deviceState.value = DeviceState()
            }
        }
    }

    fun disconnectDevice() {
        try { bluetooth.disconnect() } catch (e: Exception) { Log.w(TAG, "Bluetooth disconnect failed", e) }
        _telemetryData.value = TelemetryData()
        _wifiPasswordRequest.value = null
    }

    fun updateTelemetry() {
        if (_deviceState.value.isConnected) Log.d(TAG, "Waiting for real ESP32 telemetry")
    }

    fun executeCommand(commandId: String) {
        if (!_deviceState.value.isConnected) return
        val command = _controlCommands.value.firstOrNull { it.id == commandId } ?: return

        viewModelScope.launch(bluetoothExceptionHandler) {
            try {
                if (bluetooth.send(command.id)) {
                    _controlCommands.value = _controlCommands.value.map {
                        if (it.id == commandId) it.copy(isActive = !it.isActive) else it
                    }
                }
            } catch (e: Exception) {
                Log.e(TAG, "Bluetooth command failed", e)
            }
        }
    }

    fun getSetting(key: String): String = _settings.value[key].orEmpty()

    fun setSetting(key: String, value: String) {
        _settings.value = _settings.value + (key to value)
    }

    override fun onCleared() {
        try { bluetooth.close() } catch (e: Exception) { Log.w(TAG, "Bluetooth cleanup failed", e) }
        super.onCleared()
    }

    companion object {
        private const val TAG = "EchoViewModel"
        private const val PREFS_NAME = "echo_preferences"
        private const val KEY_LAST_DEVICE_ADDRESS = "last_bluetooth_device_address"
    }
}
