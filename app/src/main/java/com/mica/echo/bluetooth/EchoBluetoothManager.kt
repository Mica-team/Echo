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
            if (BluetoothDevice.ACTION_FOUND == intent.action) {
                val device = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
                    intent.getParcelableExtra(BluetoothDevice.EXTRA_DEVICE, BluetoothDevice::class.java)
                } else {
                    @Suppress("DEPRECATION")
                    intent.getParcelableExtra(BluetoothDevice.EXTRA_DEVICE)
                } ?: return

                val name = try { device.name } catch (_: SecurityException) { null }
                if (!name.isNullOrBlank()) {
                    discovered[device.address] = device
                    onDevicesChanged?.invoke(discovered.values.map { safeName(it) })
                }
            }
        }
    }

    init {
        val filter = IntentFilter(BluetoothDevice.ACTION_FOUND)
        ContextCompat.registerReceiver(appContext, receiver, filter, ContextCompat.RECEIVER_NOT_EXPORTED)
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
        discovered.clear()
        adapter?.bondedDevices?.forEach { device ->
            discovered[device.address] = device
        }
        onDevicesChanged?.invoke(discovered.values.map { safeName(it) })
        adapter?.cancelDiscovery()
        adapter?.startDiscovery()
    }

    @SuppressLint("MissingPermission")
    suspend fun connect(name: String): Boolean = withContext(Dispatchers.IO) {
        if (!hasBluetoothPermission()) return@withContext false

        val device = discovered.values.firstOrNull { safeName(it) == name }
            ?: adapter?.bondedDevices?.firstOrNull { safeName(it) == name }
            ?: return@withContext false

        disconnect()
        adapter?.cancelDiscovery()

        try {
            val newSocket = device.createRfcommSocketToServiceRecord(SPP_UUID)
            newSocket.connect()
            socket = newSocket
            output = newSocket.outputStream
            onConnectionChanged?.invoke(device, true)
            true
        } catch (_: IOException) {
            disconnect()
            false
        } catch (_: SecurityException) {
            false
        }
    }

    suspend fun send(command: String): Boolean = withContext(Dispatchers.IO) {
        try {
            val stream = output ?: return@withContext false
            stream.write((command.trim() + "\n").toByteArray(Charsets.UTF_8))
            stream.flush()
            true
        } catch (_: IOException) {
            false
        }
    }

    @SuppressLint("MissingPermission")
    fun disconnect() {
        try { socket?.close() } catch (_: IOException) {}
        socket = null
        output = null
        onConnectionChanged?.invoke(null, false)
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
    private fun safeName(device: BluetoothDevice): String =
        device.name?.takeIf { it.isNotBlank() } ?: "Unknown Echo (${device.address})"

    fun close() {
        disconnect()
        try { appContext.unregisterReceiver(receiver) } catch (_: IllegalArgumentException) {}
    }

    companion object {
        private val SPP_UUID: UUID = UUID.fromString("00001101-0000-1000-8000-00805F9B34FB")
    }
}
