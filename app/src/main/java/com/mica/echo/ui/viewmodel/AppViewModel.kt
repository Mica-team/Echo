package com.mica.echo.ui.viewmodel

import android.content.Context
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

    /* =========================
       Device State
    ========================= */

    private val _deviceState = MutableStateFlow(DeviceState())
    val deviceState: StateFlow<DeviceState> =
        _deviceState.asStateFlow()

    /* =========================
       Telemetry
    ========================= */

    private val _telemetryData =
        MutableStateFlow(TelemetryData())

    val telemetryData: StateFlow<TelemetryData> =
        _telemetryData.asStateFlow()

    /* =========================
       Bluetooth Devices
    ========================= */

    private val _availableDevices =
        MutableStateFlow<List<String>>(emptyList())

    val availableDevices: StateFlow<List<String>> =
        _availableDevices.asStateFlow()

    /* =========================
       Echo Commands
    ========================= */

    private val _controlCommands =
        MutableStateFlow(
            listOf(

                ControlCommand(
                    id = "PING",
                    name = "Ping",
                    description = "Check if Echo is responding",
                    isActive = false
                ),

                ControlCommand(
                    id = "STATUS",
                    name = "Status",
                    description = "Request Echo system status",
                    isActive = false
                ),

                ControlCommand(
                    id = "WIFI",
                    name = "Wi-Fi",
                    description = "Check Echo Wi-Fi status",
                    isActive = false
                ),

                ControlCommand(
                    id = "OTA",
                    name = "Check Update",
                    description = "Check for a firmware update",
                    isActive = false
                ),

                ControlCommand(
                    id = "REBOOT",
                    name = "Reboot",
                    description = "Restart Echo",
                    isActive = false
                )
            )
        )

    val controlCommands: StateFlow<List<ControlCommand>> =
        _controlCommands.asStateFlow()

    /* =========================
       Settings
    ========================= */

    private val _settings =
        MutableStateFlow(
            mapOf(
                "theme_mode" to "dark",
                "auto_refresh" to "true",
                "log_level" to "info"
            )
        )

    val settings: StateFlow<Map<String, String>> =
        _settings.asStateFlow()

    /* =========================
       Initialization
    ========================= */

    init {

        bluetooth.setListeners(

            devicesChanged = { devices ->

                _availableDevices.value = devices
            },

            connectionChanged = { device, connected ->

                _deviceState.value =
                    if (connected && device != null) {

                        DeviceState(

                            name =
                                try {
                                    device.name ?: "Echo"
                                } catch (
                                    _: SecurityException
                                ) {
                                    "Echo"
                                },

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

    /* =========================
       Bluetooth
    ========================= */

    fun scanDevices() {

        bluetooth.scan()
    }

    fun connectDevice(deviceName: String) {

        viewModelScope.launch {

            bluetooth.connect(deviceName)
        }
    }

    fun disconnectDevice() {

        bluetooth.disconnect()

        _telemetryData.value =
            TelemetryData()
    }

    /* =========================
       Telemetry
    ========================= */

    fun updateTelemetry() {

        val currentState =
            _deviceState.value

        val nextSignal =
            if (currentState.isConnected) {

                -45 - Random.nextInt(20)

            } else {

                -90 - Random.nextInt(10)
            }

        val nextBattery =
            if (currentState.isConnected) {

                (
                    currentState.batteryLevel -
                        Random.nextInt(2)
                ).coerceAtLeast(0)

            } else {

                currentState.batteryLevel
            }

        /*
         * Temporary telemetry until
         * Echo firmware sends real sensors.
         */

        _telemetryData.value =
            TelemetryData(

                temperature =
                    18f +
                        Random.nextFloat() * 18f,

                humidity =
                    35f +
                        Random.nextFloat() * 30f,

                pressure =
                    1008f +
                        Random.nextFloat() * 20f,

                rssi = nextSignal,

                timestamp =
                    System.currentTimeMillis()
            )

        if (currentState.isConnected) {

            _deviceState.value =
                currentState.copy(

                    signalStrength =
                        nextSignal,

                    batteryLevel =
                        nextBattery,

                    lastUpdate =
                        System.currentTimeMillis()
                )
        }
    }

    /* =========================
       Execute Echo Command
    ========================= */

    fun executeCommand(commandId: String) {

        if (!_deviceState.value.isConnected) {

            return
        }

        val command =
            _controlCommands.value
                .firstOrNull {
                    it.id == commandId
                }
                ?: return

        viewModelScope.launch {

            try {

                val sent =
                    bluetooth.send(command.id)

                if (sent) {

                    _controlCommands.value =
                        _controlCommands.value.map {

                            if (
                                it.id == commandId
                            ) {

                                it.copy(
                                    isActive =
                                        !it.isActive
                                )

                            } else {

                                it
                            }
                        }
                }

            } catch (
                _: Exception
            ) {

                /*
                 * Bluetooth failure is ignored here
                 * so the app does not crash.
                 */
            }
        }
    }

    /* =========================
       Settings
    ========================= */

    fun getSetting(key: String): String {

        return _settings.value[key]
            .orEmpty()
    }

    fun setSetting(
        key: String,
        value: String
    ) {

        _settings.value =
            _settings.value +
                (key to value)
    }

    /* =========================
       Cleanup
    ========================= */

    override fun onCleared() {

        bluetooth.close()

        super.onCleared()
    }
}
