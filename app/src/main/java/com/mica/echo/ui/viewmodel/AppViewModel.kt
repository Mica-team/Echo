package com.mica.echo.ui.viewmodel

import android.content.Context
import android.os.Build
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.mica.echo.bluetooth.EchoBluetoothManager
import com.mica.echo.data.ControlCommand
import com.mica.echo.data.DeviceState
import com.mica.echo.data.TelemetryData
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import kotlin.random.Random

class AppViewModel(context: Context) : ViewModel() {
    private val bluetooth = EchoBluetoothManager(context)

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

    init {
        bluetooth.setListeners(
            devicesChanged = { devices -> _availableDevices.value = devices },
            connectionChanged = { device, connected ->
                _deviceState.value = if (connected && device != null) {
                    DeviceState(
                        name = try { device.name ?: "Echo" } catch (_: SecurityException) { "Echo" },
                        address = device.address,
                        isConnected = true,
                        signalStrength = -45,
                        batteryLevel = 100
                    )
                } else {
                    DeviceState()
                }
            }
        )
    }

    fun scanDevices() = bluetooth.scan()

    fun connectDevice(deviceName: String) {
        viewModelScope.launch {
            bluetooth.connect(deviceName)
        }
    }

    fun disconnectDevice() {
        bluetooth.disconnect()
        _telemetryData.value = TelemetryData()
    }

    fun updateTelemetry() {
        val currentState = _deviceState.value
        val nextSignal = if (currentState.isConnected) -45 - Random.nextInt(20) else -90 - Random.nextInt(10)
        val nextBattery = if (currentState.isConnected) {
            (currentState.batteryLevel - Random.nextInt(2)).coerceAtLeast(0)
        } else currentState.batteryLevel

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
        val command = _controlCommands.value.firstOrNull { it.id == commandId } ?: return
        viewModelScope.launch {
            val sent = bluetooth.send(command.id)
            if (sent) {
                _controlCommands.value = _controlCommands.value.map { cmd ->
                    if (cmd.id == commandId) cmd.copy(isActive = !cmd.isActive) else cmd
                }
            }
        }
    }

    fun getSetting(key: String): String = _settings.value[key].orEmpty()

    fun setSetting(key: String, value: String) {
        _settings.value = _settings.value + (key to value)
    }

    override fun onCleared() {
        bluetooth.close()
        super.onCleared()
    }
}
