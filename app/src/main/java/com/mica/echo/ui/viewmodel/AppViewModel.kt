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
import kotlinx.coroutines.CoroutineExceptionHandler
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch

class AppViewModel(context: Context) : ViewModel() {

    private val appContext =
        context.applicationContext

    private val bluetooth =
        EchoBluetoothManager(appContext)

    private val preferences =
        appContext.getSharedPreferences(
            PREFS_NAME,
            Context.MODE_PRIVATE
        )

    private val bluetoothExceptionHandler =
        CoroutineExceptionHandler { _, throwable ->

            Log.e(
                TAG,
                "Unhandled Bluetooth coroutine failure",
                throwable
            )

            _deviceState.value =
                DeviceState()
        }

    private val _deviceState =
        MutableStateFlow(DeviceState())

    val deviceState:
        StateFlow<DeviceState> =
        _deviceState.asStateFlow()

    private val _telemetryData =
        MutableStateFlow(TelemetryData())

    val telemetryData:
        StateFlow<TelemetryData> =
        _telemetryData.asStateFlow()

    private val _availableDevices =
        MutableStateFlow<List<EchoBluetoothDevice>>(
            emptyList()
        )

    val availableDevices:
        StateFlow<List<EchoBluetoothDevice>> =
        _availableDevices.asStateFlow()

    private val _controlCommands =
        MutableStateFlow(
            listOf(
                ControlCommand(
                    "PING",
                    "Ping",
                    "Check if Echo is responding",
                    false
                ),
                ControlCommand(
                    "STATUS",
                    "Status",
                    "Request Echo system status",
                    false
                ),
                ControlCommand(
                    "WIFI",
                    "Wi-Fi",
                    "Check Echo Wi-Fi status",
                    false
                ),
                ControlCommand(
                    "OTA",
                    "Check Update",
                    "Check for a firmware update",
                    false
                ),
                ControlCommand(
                    "REBOOT",
                    "Reboot",
                    "Restart Echo",
                    false
                )
            )
        )

    val controlCommands:
        StateFlow<List<ControlCommand>> =
        _controlCommands.asStateFlow()

    private val _settings =
        MutableStateFlow(
            mapOf(
                "theme_mode" to "dark",
                "auto_refresh" to "true",
                "log_level" to "info"
            )
        )

    val settings:
        StateFlow<Map<String, String>> =
        _settings.asStateFlow()

    init {

        bluetooth.setListeners(

            devicesChanged = { devices ->
                _availableDevices.value =
                    devices
            },

            connectionChanged = {
                    device,
                    connected ->

                try {

                    if (
                        connected &&
                        device != null
                    ) {

                        val name =
                            try {
                                device.name
                                    ?: "Echo"
                            } catch (
                                _: SecurityException
                            ) {
                                "Echo"
                            }

                        val address =
                            try {
                                device.address
                            } catch (
                                _: SecurityException
                            ) {
                                ""
                            }

                        _deviceState.value =
                            DeviceState(
                                name = name,
                                address = address,
                                isConnected = true,
                                // No fake RSSI.
                                signalStrength = 0,
                                // No fake battery.
                                batteryLevel = 0
                            )

                        if (
                            address.isNotBlank()
                        ) {

                            preferences
                                .edit()
                                .putString(
                                    KEY_LAST_DEVICE_ADDRESS,
                                    address
                                )
                                .apply()
                        }

                    } else {

                        _deviceState.value =
                            DeviceState()
                    }

                } catch (e: Exception) {

                    Log.e(
                        TAG,
                        "Bluetooth connection state callback failed",
                        e
                    )

                    _deviceState.value =
                        DeviceState()
                }
            }
        )

        /*
         * Receive real data from ESP32.
         *
         * Current ESP32 firmware sends:
         *
         * TEMP:42.50
         */
        bluetooth.setDataListener { line ->

            handleBluetoothData(line)
        }

        /*
         * Automatically reconnect to the last
         * Bluetooth device.
         */
        val lastAddress =
            preferences.getString(
                KEY_LAST_DEVICE_ADDRESS,
                null
            )

        if (
            !lastAddress.isNullOrBlank()
        ) {

            viewModelScope.launch(
                bluetoothExceptionHandler
            ) {

                val connected =
                    bluetooth.connect(
                        lastAddress
                    )

                if (!connected) {

                    Log.d(
                        TAG,
                        "Previous Echo device could not be reconnected"
                    )
                }
            }
        }
    }

    private fun handleBluetoothData(
        line: String
    ) {

        val data =
            line.trim()

        Log.d(
            TAG,
            "ESP32 data: $data"
        )

        /*
         * ESP32:
         *
         * TEMP:42.50
         */
        if (
            data.startsWith(
                "TEMP:",
                ignoreCase = true
            )
        ) {

            val valueText =
                data.substringAfter(
                    ":"
                ).trim()

            val temperature =
                valueText.toFloatOrNull()

            if (
                temperature == null
            ) {

                Log.w(
                    TAG,
                    "Invalid temperature received: $valueText"
                )

                return
            }

            val now =
                System.currentTimeMillis()

            val current =
                _telemetryData.value

            _telemetryData.value =
                current.copy(
                    temperature = temperature,
                    timestamp = now
                )

            /*
             * The temperature is real ESP32 data,
             * so update the device's last-update time.
             */
            val device =
                _deviceState.value

            if (
                device.isConnected
            ) {

                _deviceState.value =
                    device.copy(
                        lastUpdate = now
                    )
            }

            Log.d(
                TAG,
                "Real ESP32 CPU temperature: $temperature °C"
            )
        }
    }

    fun scanDevices() {

        try {

            bluetooth.scan()

        } catch (e: Exception) {

            Log.e(
                TAG,
                "Bluetooth scan request failed",
                e
            )

            _availableDevices.value =
                emptyList()
        }
    }

    fun connectDevice(
        device: EchoBluetoothDevice
    ) {

        viewModelScope.launch(
            bluetoothExceptionHandler
        ) {

            try {

                val connected =
                    bluetooth.connect(
                        device.address
                    )

                if (!connected) {

                    _deviceState.value =
                        DeviceState()
                }

            } catch (e: Exception) {

                Log.e(
                    TAG,
                    "Bluetooth connect request failed",
                    e
                )

                _deviceState.value =
                    DeviceState()
            }
        }
    }

    fun disconnectDevice() {

        try {

            bluetooth.disconnect()

        } catch (e: Exception) {

            Log.w(
                TAG,
                "Bluetooth disconnect failed",
                e
            )
        }

        _telemetryData.value =
            TelemetryData()
    }

    /*
     * Kept for compatibility with existing UI.
     *
     * IMPORTANT:
     * This no longer generates fake telemetry.
     *
     * Real telemetry arrives from the ESP32
     * through handleBluetoothData().
     */
    fun updateTelemetry() {

        if (
            !_deviceState.value.isConnected
        ) {
            return
        }

        Log.d(
            TAG,
            "Waiting for real ESP32 telemetry"
        )
    }

    fun executeCommand(
        commandId: String
    ) {

        if (
            !_deviceState.value.isConnected
        ) {
            return
        }

        val command =
            _controlCommands.value
                .firstOrNull {
                    it.id == commandId
                }
                ?: return

        viewModelScope.launch(
            bluetoothExceptionHandler
        ) {

            try {

                if (
                    bluetooth.send(
                        command.id
                    )
                ) {

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

            } catch (e: Exception) {

                Log.e(
                    TAG,
                    "Bluetooth command failed",
                    e
                )
            }
        }
    }

    fun getSetting(
        key: String
    ): String =
        _settings.value[key].orEmpty()

    fun setSetting(
        key: String,
        value: String
    ) {

        _settings.value =
            _settings.value +
            (key to value)
    }

    override fun onCleared() {

        try {

            bluetooth.close()

        } catch (e: Exception) {

            Log.w(
                TAG,
                "Bluetooth cleanup failed",
                e
            )
        }

        super.onCleared()
    }

    companion object {

        private const val TAG =
            "EchoViewModel"

        private const val PREFS_NAME =
            "echo_preferences"

        private const val KEY_LAST_DEVICE_ADDRESS =
            "last_bluetooth_device_address"
    }
}
