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
import android.os.Handler
import android.os.Looper
import android.util.Log
import androidx.core.content.ContextCompat
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.cancel
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.io.BufferedReader
import java.io.IOException
import java.io.InputStreamReader
import java.io.OutputStream
import java.util.UUID

data class EchoBluetoothDevice(
    val name: String,
    val address: String
)

class EchoBluetoothManager(context: Context) {

    private val appContext = context.applicationContext
    private val mainHandler = Handler(Looper.getMainLooper())
    private val adapter: BluetoothAdapter? = BluetoothAdapter.getDefaultAdapter()

    private var socket: BluetoothSocket? = null
    private var output: OutputStream? = null
    private var readerJob: Job? = null

    private val readerScope = CoroutineScope(Dispatchers.IO)
    private val devices = linkedMapOf<String, BluetoothDevice>()

    private var onDevicesChanged: ((List<EchoBluetoothDevice>) -> Unit)? = null
    private var onConnectionChanged: ((BluetoothDevice?, Boolean) -> Unit)? = null
    private var onDataReceived: ((String) -> Unit)? = null

    private val receiver = object : BroadcastReceiver() {
        @SuppressLint("MissingPermission")
        override fun onReceive(context: Context, intent: Intent) {
            try {
                when (intent.action) {
                    BluetoothDevice.ACTION_FOUND -> {
                        val device = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
                            intent.getParcelableExtra(
                                BluetoothDevice.EXTRA_DEVICE,
                                BluetoothDevice::class.java
                            )
                        } else {
                            @Suppress("DEPRECATION")
                            intent.getParcelableExtra(BluetoothDevice.EXTRA_DEVICE)
                        }

                        if (device != null) addDevice(device)
                    }

                    BluetoothAdapter.ACTION_DISCOVERY_STARTED ->
                        Log.d(TAG, "Classic Bluetooth discovery started")

                    BluetoothAdapter.ACTION_DISCOVERY_FINISHED -> {
                        Log.d(TAG, "Classic Bluetooth discovery finished")
                        addBondedDevices()
                    }

                    BluetoothDevice.ACTION_BOND_STATE_CHANGED -> {
                        Log.d(TAG, "Bluetooth bond state changed")
                        addBondedDevices()
                    }
                }
            } catch (e: Exception) {
                Log.e(TAG, "Bluetooth receiver error", e)
            }
        }
    }

    init {
        registerBluetoothReceiver()
    }

    private fun registerBluetoothReceiver() {
        try {
            val filter = IntentFilter().apply {
                addAction(BluetoothDevice.ACTION_FOUND)
                addAction(BluetoothAdapter.ACTION_DISCOVERY_STARTED)
                addAction(BluetoothAdapter.ACTION_DISCOVERY_FINISHED)
                addAction(BluetoothDevice.ACTION_BOND_STATE_CHANGED)
            }

            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
                ContextCompat.registerReceiver(
                    appContext,
                    receiver,
                    filter,
                    ContextCompat.RECEIVER_EXPORTED
                )
            } else {
                @Suppress("DEPRECATION")
                appContext.registerReceiver(receiver, filter)
            }

            Log.d(TAG, "Bluetooth receiver registered")
        } catch (e: Exception) {
            Log.e(TAG, "Failed to register Bluetooth receiver", e)
        }
    }

    fun setListeners(
        devicesChanged: (List<EchoBluetoothDevice>) -> Unit,
        connectionChanged: (BluetoothDevice?, Boolean) -> Unit
    ) {
        onDevicesChanged = devicesChanged
        onConnectionChanged = connectionChanged
        addBondedDevices()
    }

    fun setDataListener(dataReceived: (String) -> Unit) {
        onDataReceived = dataReceived
    }

    @SuppressLint("MissingPermission")
    fun scan() {
        if (!hasBluetoothPermission()) {
            Log.w(TAG, "Bluetooth permission missing")
            publishDevices()
            return
        }

        val btAdapter = adapter ?: run {
            Log.e(TAG, "Bluetooth adapter unavailable")
            publishDevices()
            return
        }

        try {
            if (!btAdapter.isEnabled) {
                Log.w(TAG, "Bluetooth is disabled")
                publishDevices()
                return
            }

            devices.clear()
            addBondedDevices()

            try {
                btAdapter.cancelDiscovery()
            } catch (e: Exception) {
                Log.w(TAG, "Could not cancel previous discovery", e)
            }

            val started = try {
                btAdapter.startDiscovery()
            } catch (e: Exception) {
                Log.e(TAG, "startDiscovery failed", e)
                false
            }

            Log.d(TAG, "Classic Bluetooth discovery requested: $started")
            publishDevices()
        } catch (e: Exception) {
            Log.e(TAG, "Bluetooth scan failed", e)
            publishDevices()
        }
    }

    @SuppressLint("MissingPermission")
    private fun addBondedDevices() {
        if (!hasBluetoothPermission()) return

        try {
            val bonded = adapter?.bondedDevices ?: emptySet()
            Log.d(TAG, "Bonded Classic Bluetooth devices: ${bonded.size}")
            bonded.forEach { addDeviceInternal(it) }
            publishDevices()
        } catch (e: Exception) {
            Log.e(TAG, "Failed to load bonded Bluetooth devices", e)
        }
    }

    @SuppressLint("MissingPermission")
    private fun addDevice(device: BluetoothDevice) {
        try {
            addDeviceInternal(device)
            publishDevices()
        } catch (e: Exception) {
            Log.e(TAG, "Failed to add Bluetooth device", e)
        }
    }

    @SuppressLint("MissingPermission")
    private fun addDeviceInternal(device: BluetoothDevice) {
        try {
            val address = device.address
            if (address.isNullOrBlank()) return

            devices[address] = device
            Log.d(TAG, "Bluetooth device: ${safeName(device)} [$address]")
        } catch (e: Exception) {
            Log.e(TAG, "Cannot read Bluetooth device identity", e)
        }
    }

    @SuppressLint("MissingPermission")
    private fun publishDevices() {
        val result = devices.values
            .mapNotNull { device ->
                try {
                    val address = device.address
                    if (address.isNullOrBlank()) {
                        null
                    } else {
                        EchoBluetoothDevice(safeName(device), address)
                    }
                } catch (_: Exception) {
                    null
                }
            }
            .distinctBy { it.address }

        mainHandler.post {
            try {
                onDevicesChanged?.invoke(result)
            } catch (e: Exception) {
                Log.e(TAG, "Device-list callback failed", e)
            }
        }
    }

    @SuppressLint("MissingPermission")
    suspend fun connect(address: String): Boolean = withContext(Dispatchers.IO) {
        if (!hasBluetoothPermission()) {
            Log.w(TAG, "Bluetooth permission missing during connection")
            return@withContext false
        }

        if (address.isBlank()) return@withContext false

        try {
            val btAdapter = adapter ?: return@withContext false

            val device = devices[address]
                ?: btAdapter.bondedDevices.firstOrNull {
                    it.address.equals(address, ignoreCase = true)
                }
                ?: try {
                    btAdapter.getRemoteDevice(address)
                } catch (e: IllegalArgumentException) {
                    Log.e(TAG, "Invalid Bluetooth address: $address", e)
                    return@withContext false
                }

            Log.d(TAG, "Connecting to ${safeName(device)} [$address]")

            try {
                btAdapter.cancelDiscovery()
            } catch (e: Exception) {
                Log.w(TAG, "Could not cancel discovery before connect", e)
            }

            disconnect(notify = false)

            val secureSocket = device.createRfcommSocketToServiceRecord(SPP_UUID)

            try {
                Log.d(TAG, "Trying secure RFCOMM/SPP...")
                secureSocket.connect()

                socket = secureSocket
                output = secureSocket.outputStream
                notifyConnection(device, true)
                startReader(secureSocket, device)

                Log.d(TAG, "Secure RFCOMM/SPP connection successful")
                return@withContext true
            } catch (e: IOException) {
                Log.w(TAG, "Secure RFCOMM/SPP failed", e)
                try {
                    secureSocket.close()
                } catch (_: Exception) {
                }
            }

            val insecureSocket = device.createInsecureRfcommSocketToServiceRecord(SPP_UUID)

            try {
                Log.d(TAG, "Trying insecure RFCOMM/SPP...")
                insecureSocket.connect()

                socket = insecureSocket
                output = insecureSocket.outputStream
                notifyConnection(device, true)
                startReader(insecureSocket, device)

                Log.d(TAG, "Insecure RFCOMM/SPP connection successful")
                return@withContext true
            } catch (e: Exception) {
                Log.e(TAG, "Insecure RFCOMM/SPP failed", e)
                try {
                    insecureSocket.close()
                } catch (_: Exception) {
                }
                notifyConnection(null, false)
                return@withContext false
            }
        } catch (e: Exception) {
            Log.e(TAG, "Echo Bluetooth connection failed", e)
            notifyConnection(null, false)
            false
        }
    }

    private fun startReader(
        connectedSocket: BluetoothSocket,
        device: BluetoothDevice
    ) {
        readerJob?.cancel()

        readerJob = readerScope.launch {
            try {
                val reader = BufferedReader(
                    InputStreamReader(
                        connectedSocket.inputStream,
                        Charsets.UTF_8
                    )
                )

                while (connectedSocket.isConnected) {
                    val line = reader.readLine() ?: break
                    val data = line.trim()

                    if (data.isNotEmpty()) {
                        Log.d(TAG, "Received from ESP32: $data")

                        mainHandler.post {
                            try {
                                onDataReceived?.invoke(data)
                            } catch (e: Exception) {
                                Log.e(TAG, "Bluetooth data callback failed", e)
                            }
                        }
                    }
                }
            } catch (e: IOException) {
                Log.w(TAG, "Bluetooth input stream closed", e)
            } catch (e: Exception) {
                Log.e(TAG, "Bluetooth reader failed", e)
            }

            if (socket === connectedSocket) {
                socket = null
                output = null
                notifyConnection(null, false)
            }
        }
    }

    suspend fun send(command: String): Boolean = withContext(Dispatchers.IO) {
        try {
            val stream = output ?: return@withContext false
            val cleanCommand = command.trim()

            stream.write((cleanCommand + "\n").toByteArray(Charsets.UTF_8))
            stream.flush()

            Log.d(TAG, "Sent command: $cleanCommand")
            true
        } catch (e: Exception) {
            Log.e(TAG, "Bluetooth command failed", e)
            false
        }
    }

    fun disconnect() {
        disconnect(notify = true)
    }

    private fun disconnect(notify: Boolean) {
        readerJob?.cancel()
        readerJob = null

        try {
            socket?.close()
        } catch (e: Exception) {
            Log.w(TAG, "Error closing Bluetooth socket", e)
        }

        socket = null
        output = null

        if (notify) notifyConnection(null, false)
    }

    private fun notifyConnection(
        device: BluetoothDevice?,
        connected: Boolean
    ) {
        mainHandler.post {
            try {
                onConnectionChanged?.invoke(device, connected)
            } catch (e: Exception) {
                Log.e(TAG, "Connection callback failed", e)
            }
        }
    }

    fun isConnected(): Boolean = socket?.isConnected == true

    private fun hasBluetoothPermission(): Boolean {
        return if (Build.VERSION.SDK_INT < Build.VERSION_CODES.S) {
            ContextCompat.checkSelfPermission(
                appContext,
                Manifest.permission.ACCESS_FINE_LOCATION
            ) == PackageManager.PERMISSION_GRANTED
        } else {
            ContextCompat.checkSelfPermission(
                appContext,
                Manifest.permission.BLUETOOTH_SCAN
            ) == PackageManager.PERMISSION_GRANTED &&
                ContextCompat.checkSelfPermission(
                    appContext,
                    Manifest.permission.BLUETOOTH_CONNECT
                ) == PackageManager.PERMISSION_GRANTED
        }
    }

    @SuppressLint("MissingPermission")
    private fun safeName(device: BluetoothDevice): String {
        return try {
            device.name?.trim()?.takeIf { it.isNotBlank() }
                ?: "Unknown Bluetooth Device"
        } catch (_: Exception) {
            "Unknown Bluetooth Device"
        }
    }

    fun close() {
        disconnect()

        try {
            readerScope.cancel()
        } catch (_: Exception) {
        }

        try {
            appContext.unregisterReceiver(receiver)
        } catch (_: IllegalArgumentException) {
        } catch (e: Exception) {
            Log.w(TAG, "Failed to unregister Bluetooth receiver", e)
        }
    }

    companion object {
        private const val TAG = "EchoBluetooth"

        private val SPP_UUID: UUID = UUID.fromString(
            "00001101-0000-1000-8000-00805F9B34FB"
        )
    }
}
