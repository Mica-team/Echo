package com.mica.echo.ui.viewmodel

import androidx.lifecycle.ViewModel
import com.mica.echo.data.ControlCommand
import com.mica.echo.data.DeviceState
import com.mica.echo.data.TelemetryData
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlin.random.Random

class AppViewModel : ViewModel() {
    private val _deviceState = MutableStateFlow(DeviceState())
    val deviceState: StateFlow<DeviceState> = _deviceState.asStateFlow()

    private val _telemetryData = MutableStateFlow(TelemetryData())
    val telemetryData: StateFlow<TelemetryData> = _telemetryData.asStateFlow()

    private val _availableDevices = MutableStateFlow<List<String>>(emptyList())
    val availableDevices: StateFlow<List<String>> = _availableDevices.asStateFlow()

    private val _controlCommands = MutableStateFlow(
        listOf(
            ControlCommand("cmd_1", "Power", "Toggle device power", false),
            ControlCommand("cmd_2", "Brightness", "Adjust brightness level", false),
            ControlCommand("cmd_3", "Mode", "Switch operation mode", false),
            ControlCommand("cmd_4", "Reset", "Reset device settings", false)
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

    fun scanDevices() {
        _availableDevices.value = listOf(
            "Echo Device 1",
            "Echo Device 2",
            "Echo Sensor Pro"
        )
    }

    fun connectDevice(deviceName: String) {
        _deviceState.value = DeviceState(
            name = deviceName,
            address = "00:1A:7D:DA:71:13",
            isConnected = true,
            signalStrength = -45,
            batteryLevel = 87
        )
        updateTelemetry()
    }

    fun disconnectDevice() {
        _deviceState.value = DeviceState()
        _telemetryData.value = TelemetryData()
    }

    fun updateTelemetry() {
        val currentState = _deviceState.value
        val nextSignal = if (currentState.isConnected) -45 - Random.nextInt(20) else -90 - Random.nextInt(10)
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
        _controlCommands.value = _controlCommands.value.map { cmd ->
            if (cmd.id == commandId) {
                cmd.copy(isActive = !cmd.isActive)
            } else {
                cmd
            }
        }
    }

    fun getSetting(key: String): String = _settings.value[key].orEmpty()

    fun setSetting(key: String, value: String) {
        _settings.value = _settings.value + (key to value)
    }
}
