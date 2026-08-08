package com.mica.echo.ui.viewmodel

import android.content.Context
import android.util.Log
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.mica.echo.bluetooth.EchoBluetoothManager
import com.mica.echo.data.ControlCommand
import com.mica.echo.data.DeviceState
import com.mica.echo.data.TelemetryData
import kotlinx.coroutines.CoroutineExceptionHandler
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import kotlin.random.Random

class AppViewModel(context: Context) : ViewModel() {

    private val bluetooth = EchoBluetoothManager(context)

    private val bluetoothExceptionHandler = CoroutineExceptionHandler { _, throwable ->
        Log.e(TAG, "Unhandled Bluetooth coroutine failure", throwable)
        _deviceState.value = DeviceState()
    }

    private val _deviceState = MutableStateFlow(DeviceState())
    val deviceState: StateFlow<DeviceState> = _deviceState.asStateFlow()

    private val _telemetryData = MutableStateFlow(TelemetryData())
    val telemetryData: StateFlow<TelemetryData> = _telemetryData.asStateFlow()

    private val _availableDevices = MutableStateFlow<List<String>>(emptyList())
    val availableDevices: StateFlow<List<String>> = _availableDevices.asStateFlow()

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
        mapOf(
            "theme_mode" to "dark",
            "auto_refresh" to "true",
            "log_level" to "info"
        )
    )
    val settings: StateFlow<Map<String, String>> = _settings.asStateFlow()

    init {
        bluetooth.setListeners(
            devicesChanged = { devices ->
                try {
                    _availableDevices.value = devices
                } catch (e: Exception) {
                    Log.w(TAG, "Could not update Bluetooth device list", e)
                }
            },
            connectionChanged = { device, connected ->
                try {
                    if (connected && device != null) {
                        val name = try { device.name ?: "Echo" } catch (_: SecurityException) { "Echo" }
                        val address = try { device.address } catch (_: SecurityException) { "" }
                        _deviceState.value = DeviceState(
                            name = name,
                            address = address,
                            isConnected = true,
                            signalStrength = -45,
                            batteryLevel = 100
                        )
                    } else {
                        _deviceState.value = DeviceState()
                    }
                } catch (e: Exception) {
                    Log.e(TAG, "Bluetooth connection state callback failed", e)
                    _deviceState.value = DeviceState()
                }
            }
        )
    }

    fun scanDevices() {
        try {
            bluetooth.scan()
        } catch (e: Exception) {
            Log.e(TAG, "Bluetooth scan request failed", e)
            _availableDevices.value = emptyList()
        }
    }

    fun connectDevice(deviceName: String) {
        viewModelScope.launch(bluetoothExceptionHandler) {
            try {
                val connected = bluetooth.connect(deviceName)
                if (!connected) {
                    _deviceState.value = DeviceState()
                }
            } catch (e: Exception) {
                Log.e(TAG, "Bluetooth connect request failed", e)
                _deviceState.value = DeviceState()
            }
        }
    }

    fun disconnectDevice() {
        try {
            bluetooth.disconnect()
        } catch (e: Exception) {
            Log.w(TAG, "Bluetooth disconnect failed", e)
        }
        _telemetryData.value = TelemetryData()
    }

    fun updateTelemetry() {
        val currentState = _deviceState.value
        val nextSignal = if (currentState.isConnected) {
            -45 - Random.nextInt(20)
        } else {
            -90 - Random.nextInt(10)
        }
        val nextBattery = if (currentState.isConnected) {
            (currentState.batteryLevel - Random.nextInt(2)).coerceAtLeast(0)
        } else {
            currentState.batteryLevel
        }

        _telemetryData.value = TelemetryData(
            temperature = 18f + Random.nextFloat() * 18f,
            humidity = 35f + Random.nextFloat() * 30f,
            pressure = 1008f + Random.nextFloat() * 20f,
            rssi = nextSignal,
            timestamp = System.currentTimeMillis()
        )

        if (currentState.isConnected) {
            _deviceState.value = currentState.copy(
                signalStrength = nextSignal,
                batteryLevel = nextBattery,
                lastUpdate = System.currentTimeMillis()
            )
        }
    }

    fun executeCommand(commandId: String) {
        if (!_deviceState.value.isConnected) return

        val command = _controlCommands.value.firstOrNull { it.id == commandId } ?: return

        viewModelScope.launch(bluetoothExceptionHandler) {
            try {
                val sent = bluetooth.send(command.id)
                if (sent) {
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
        try {
            bluetooth.close()
        } catch (e: Exception) {
            Log.w(TAG, "Bluetooth cleanup failed", e)
        }
        super.onCleared()
    }

    companion object {
        private const val TAG = "EchoViewModel"
    }
}
