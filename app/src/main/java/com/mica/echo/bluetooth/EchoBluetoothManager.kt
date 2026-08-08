package com.mica.echo.bluetooth

import android.Manifest
import android.annotation.SuppressLint
import android.bluetooth.BluetoothAdapter
import android.bluetooth.BluetoothDevice
import android.bluetooth.BluetoothSocket
import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.content.IntentFilter
import android.content.pm.PackageManager
import android.os.Build
import android.util.Log
import androidx.core.content.ContextCompat
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.io.IOException
import java.io.OutputStream
import java.util.UUID

class EchoBluetoothManager(context: Context) {
    private val appContext = context.applicationContext
    private val adapter: BluetoothAdapter? = BluetoothAdapter.getDefaultAdapter()
    private var socket: BluetoothSocket? = null
    private var output: OutputStream? = null

    private val discovered = linkedMapOf<String, BluetoothDevice>()
    private var onDevicesChanged: ((List<String>) -> Unit)? = null
    private var onConnectionChanged: ((BluetoothDevice?, Boolean) -> Unit)? = null

    private val receiver = object : BroadcastReceiver() {
        override fun onReceive(context: Context, intent: Intent) {
            if (BluetoothDevice.ACTION_FOUND != intent.action) return

            try {
                val device = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
                    intent.getParcelableExtra(BluetoothDevice.EXTRA_DEVICE, BluetoothDevice::class.java)
                } else {
                    @Suppress("DEPRECATION")
                    intent.getParcelableExtra(BluetoothDevice.EXTRA_DEVICE)
                } ?: return

                val name = safeName(device)
                if (name.isNotBlank()) {
                    discovered[device.address] = device
                    onDevicesChanged?.invoke(discovered.values.map { safeName(it) })
                }
            } catch (e: Exception) {
                Log.w(TAG, "Bluetooth discovery callback failed", e)
            }
        }
    }

    init {
        val filter = IntentFilter(BluetoothDevice.ACTION_FOUND)
        ContextCompat.registerReceiver(
            appContext,
            receiver,
            filter,
            ContextCompat.RECEIVER_NOT_EXPORTED
        )
    }

    fun setListeners(
        devicesChanged: (List<String>) -> Unit,
        connectionChanged: (BluetoothDevice?, Boolean) -> Unit
    ) {
        onDevicesChanged = devicesChanged
        onConnectionChanged = connectionChanged
    }

    @SuppressLint("MissingPermission")
    fun scan() {
        if (!hasBluetoothPermission()) return

        try {
            val btAdapter = adapter ?: return
            discovered.clear()

            btAdapter.bondedDevices.forEach { device ->
                discovered[device.address] = device
            }

            onDevicesChanged?.invoke(discovered.values.map { safeName(it) })
            btAdapter.cancelDiscovery()
            btAdapter.startDiscovery()
        } catch (e: Exception) {
            Log.w(TAG, "Bluetooth scan failed", e)
            onDevicesChanged?.invoke(emptyList())
        }
    }

    @SuppressLint("MissingPermission")
    suspend fun connect(name: String): Boolean = withContext(Dispatchers.IO) {
        if (!hasBluetoothPermission()) return@withContext false

        try {
            val btAdapter = adapter ?: return@withContext false

            val device = discovered.values.firstOrNull { safeName(it) == name }
                ?: btAdapter.bondedDevices.firstOrNull { safeName(it) == name }
                ?: return@withContext false

            disconnect()
            btAdapter.cancelDiscovery()

            val newSocket = device.createRfcommSocketToServiceRecord(SPP_UUID)
            try {
                newSocket.connect()
            } catch (firstError: IOException) {
                try {
                    newSocket.close()
                } catch (_: IOException) {
                }
                Log.w(TAG, "Secure RFCOMM connection failed; retrying insecure RFCOMM", firstError)

                val fallbackSocket = device.createInsecureRfcommSocketToServiceRecord(SPP_UUID)
                fallbackSocket.connect()
                socket = fallbackSocket
                output = fallbackSocket.outputStream
                onConnectionChanged?.invoke(device, true)
                return@withContext true
            }

            socket = newSocket
            output = newSocket.outputStream
            onConnectionChanged?.invoke(device, true)
            true
        } catch (e: Exception) {
            Log.e(TAG, "Echo Bluetooth connection failed", e)
            disconnect()
            false
        }
    }

    suspend fun send(command: String): Boolean = withContext(Dispatchers.IO) {
        try {
            val stream = output ?: return@withContext false
            stream.write((command.trim() + "\n").toByteArray(Charsets.UTF_8))
            stream.flush()
            true
        } catch (e: IOException) {
            Log.w(TAG, "Bluetooth command failed", e)
            false
        }
    }

    @SuppressLint("MissingPermission")
    fun disconnect() {
        try {
            socket?.close()
        } catch (_: IOException) {
        }
        socket = null
        output = null
        try {
            onConnectionChanged?.invoke(null, false)
        } catch (e: Exception) {
            Log.w(TAG, "Bluetooth disconnect callback failed", e)
        }
    }

    fun isConnected(): Boolean = socket?.isConnected == true

    private fun hasBluetoothPermission(): Boolean {
        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.S) {
            return ContextCompat.checkSelfPermission(
                appContext,
                Manifest.permission.ACCESS_FINE_LOCATION
            ) == PackageManager.PERMISSION_GRANTED
        }

        val connect = ContextCompat.checkSelfPermission(
            appContext,
            Manifest.permission.BLUETOOTH_CONNECT
        ) == PackageManager.PERMISSION_GRANTED
        val scan = ContextCompat.checkSelfPermission(
            appContext,
            Manifest.permission.BLUETOOTH_SCAN
        ) == PackageManager.PERMISSION_GRANTED
        return connect && scan
    }

    @SuppressLint("MissingPermission")
    private fun safeName(device: BluetoothDevice): String = try {
        device.name?.takeIf { it.isNotBlank() } ?: "Unknown Echo (${device.address})"
    } catch (_: SecurityException) {
        "Unknown Echo"
    }

    fun close() {
        disconnect()
        try {
            appContext.unregisterReceiver(receiver)
        } catch (_: IllegalArgumentException) {
        }
    }

    companion object {
        private const val TAG = "EchoBluetooth"
        private val SPP_UUID: UUID = UUID.fromString("00001101-0000-1000-8000-00805F9B34FB")
    }
}
