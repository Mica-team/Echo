package com.mica.echo.data

import android.bluetooth.BluetoothDevice

data class DeviceState(
    val name: String = "No Device",
    val address: String = "",
    val isConnected: Boolean = false,
    val signalStrength: Int = 0,
    val batteryLevel: Int = 100,
    val lastUpdate: Long = System.currentTimeMillis()
)

data class TelemetryData(
    val temperature: Float = 0f,
    val humidity: Float = 0f,
    val pressure: Float = 0f,
    val rssi: Int = -100,
    val timestamp: Long = System.currentTimeMillis()
)

data class ControlCommand(
    val id: String,
    val name: String,
    val description: String,
    val isActive: Boolean = false
)
