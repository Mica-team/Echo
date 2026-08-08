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
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.io.IOException
import java.io.OutputStream
import java.util.UUID

class EchoBluetoothManager(context: Context) {

    private val appContext = context.applicationContext
    private val mainHandler = Handler(Looper.getMainLooper())

    private val adapter: BluetoothAdapter? =
        BluetoothAdapter.getDefaultAdapter()

    private var socket: BluetoothSocket? = null
    private var output: OutputStream? = null

    private val discovered = linkedMapOf<String, BluetoothDevice>()

    private var onDevicesChanged: ((List<String>) -> Unit)? = null
    private var onConnectionChanged:
        ((BluetoothDevice?, Boolean) -> Unit)? = null

    private val receiver = object : BroadcastReceiver() {

        @SuppressLint("MissingPermission")
        override fun onReceive(
            context: Context,
            intent: Intent
        ) {
            when (intent.action) {

                BluetoothDevice.ACTION_FOUND -> {
                    try {
                        val device =
                            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
                                intent.getParcelableExtra(
                                    BluetoothDevice.EXTRA_DEVICE,
                                    BluetoothDevice::class.java
                                )
                            } else {
                                @Suppress("DEPRECATION")
                                intent.getParcelableExtra(
                                    BluetoothDevice.EXTRA_DEVICE
                                )
                            }

                        if (device == null) {
                            Log.w(TAG, "ACTION_FOUND contained no device")
                            return
                        }

                        addDevice(device)

                    } catch (e: Exception) {
                        Log.e(
                            TAG,
                            "Failed to process discovered Bluetooth device",
                            e
                        )
                    }
                }

                BluetoothAdapter.ACTION_DISCOVERY_STARTED -> {
                    Log.d(TAG, "Bluetooth discovery started")
                }

                BluetoothAdapter.ACTION_DISCOVERY_FINISHED -> {
                    Log.d(TAG, "Bluetooth discovery finished")

                    // Some older Bluetooth stacks don't report
                    // already-paired devices through ACTION_FOUND.
                    // Refresh the bonded list after discovery.
                    addBondedDevices()
                }
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
            }

            /*
             * Bluetooth discovery broadcasts come from the Android
             * Bluetooth system service. Using EXPORTED here avoids
             * Android versions where a NOT_EXPORTED dynamic receiver
             * does not receive the system Bluetooth broadcast.
             */
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
                ContextCompat.registerReceiver(
                    appContext,
                    receiver,
                    filter,
                    ContextCompat.RECEIVER_EXPORTED
                )
            } else {
                @Suppress("DEPRECATION")
                appContext.registerReceiver(
                    receiver,
                    filter
                )
            }

        } catch (e: Exception) {
            Log.e(TAG, "Failed to register Bluetooth receiver", e)
        }
    }

    fun setListeners(
        devicesChanged: (List<String>) -> Unit,
        connectionChanged:
            (BluetoothDevice?, Boolean) -> Unit
    ) {
        onDevicesChanged = devicesChanged
        onConnectionChanged = connectionChanged
    }

    @SuppressLint("MissingPermission")
    fun scan() {

        if (!hasBluetoothPermission()) {
            Log.w(TAG, "Bluetooth permission is not granted")
            publishDevices()
            return
        }

        try {
            val btAdapter =
                adapter ?: run {
                    Log.e(TAG, "Bluetooth adapter is unavailable")
                    publishDevices()
                    return
                }

            if (!btAdapter.isEnabled) {
                Log.w(TAG, "Bluetooth is disabled")
                publishDevices()
                return
            }

            discovered.clear()

            /*
             * IMPORTANT:
             * Add paired Classic Bluetooth devices first.
             *
             * This makes the old ESP32 visible even if Android's
             * discovery broadcast doesn't provide ACTION_FOUND for it.
             */
            addBondedDevices()

            try {
                btAdapter.cancelDiscovery()
            } catch (e: Exception) {
                Log.w(TAG, "Unable to cancel previous discovery", e)
            }

            val started = btAdapter.startDiscovery()

            Log.d(
                TAG,
                "Bluetooth discovery requested: $started"
            )

            if (!started) {
                Log.w(TAG, "Bluetooth discovery did not start")
            }

            publishDevices()

        } catch (e: SecurityException) {
            Log.e(
                TAG,
                "Bluetooth permission/security failure during scan",
                e
            )

            publishDevices()

        } catch (e: Exception) {
            Log.e(
                TAG,
                "Bluetooth scan failed",
                e
            )

            publishDevices()
        }
    }

    @SuppressLint("MissingPermission")
    private fun addBondedDevices() {

        try {
            val btAdapter = adapter ?: return

            btAdapter.bondedDevices.forEach { device ->
                addDeviceInternal(device)
            }

            publishDevices()

        } catch (e: SecurityException) {
            Log.e(
                TAG,
                "Unable to read bonded Bluetooth devices",
                e
            )
        } catch (e: Exception) {
            Log.e(
                TAG,
                "Failed to add bonded Bluetooth devices",
                e
            )
        }
    }

    @SuppressLint("MissingPermission")
    private fun addDevice(device: BluetoothDevice) {

        try {
            addDeviceInternal(device)
            publishDevices()

        } catch (e: Exception) {
            Log.e(
                TAG,
                "Failed to add Bluetooth device",
                e
            )
        }
    }

    @SuppressLint("MissingPermission")
    private fun addDeviceInternal(device: BluetoothDevice) {

        val address = try {
            device.address
        } catch (e: SecurityException) {
            return
        }

        if (address.isNullOrBlank()) {
            return
        }

        discovered[address] = device

        Log.d(
            TAG,
            "Bluetooth device found: ${safeName(device)} [$address]"
        )
    }

    private fun publishDevices() {

        val names = discovered.values
            .map { safeName(it) }
            .distinct()

        mainHandler.post {
            try {
                onDevicesChanged?.invoke(names)
            } catch (e: Exception) {
                Log.e(
                    TAG,
                    "Device-list callback failed",
                    e
                )
            }
        }
    }

    @SuppressLint("MissingPermission")
    suspend fun connect(name: String): Boolean =
        withContext(Dispatchers.IO) {

            if (!hasBluetoothPermission()) {
                Log.w(
                    TAG,
                    "Bluetooth permission missing during connection"
                )
                return@withContext false
            }

            try {

                val btAdapter =
                    adapter ?: return@withContext false

                /*
                 * Look in both discovered and bonded devices.
                 */
                val device =
                    discovered.values.firstOrNull {
                        safeName(it) == name
                    }
                        ?: btAdapter.bondedDevices.firstOrNull {
                            safeName(it) == name
                        }

                if (device == null) {
                    Log.w(
                        TAG,
                        "Could not find Bluetooth device named: $name"
                    )

                    notifyConnection(null, false)

                    return@withContext false
                }

                Log.d(
                    TAG,
                    "Connecting to ${safeName(device)} [${device.address}]"
                )

                /*
                 * Discovery interferes with RFCOMM connection.
                 */
                try {
                    btAdapter.cancelDiscovery()
                } catch (e: Exception) {
                    Log.w(
                        TAG,
                        "Could not cancel discovery before connect",
                        e
                    )
                }

                disconnect(notify = false)

                /*
                 * Standard Bluetooth Serial Port Profile UUID.
                 * This is the normal UUID used by ESP32 Classic
                 * BluetoothSerial implementations.
                 */
                val secureSocket =
                    device.createRfcommSocketToServiceRecord(
                        SPP_UUID
                    )

                try {

                    Log.d(
                        TAG,
                        "Trying secure RFCOMM connection"
                    )

                    secureSocket.connect()

                    socket = secureSocket
                    output = secureSocket.outputStream

                    notifyConnection(device, true)

                    Log.d(
                        TAG,
                        "Secure RFCOMM connection successful"
                    )

                    return@withContext true

                } catch (secureError: IOException) {

                    Log.w(
                        TAG,
                        "Secure RFCOMM failed; trying insecure RFCOMM",
                        secureError
                    )

                    try {
                        secureSocket.close()
                    } catch (_: IOException) {
                    }
                }

                /*
                 * Some ESP32 Classic Bluetooth firmware works better
                 * with the insecure RFCOMM socket.
                 */
                val insecureSocket =
                    device.createInsecureRfcommSocketToServiceRecord(
                        SPP_UUID
                    )

                try {

                    Log.d(
                        TAG,
                        "Trying insecure RFCOMM connection"
                    )

                    insecureSocket.connect()

                    socket = insecureSocket
                    output = insecureSocket.outputStream

                    notifyConnection(device, true)

                    Log.d(
                        TAG,
                        "Insecure RFCOMM connection successful"
                    )

                    true

                } catch (e: Exception) {

                    try {
                        insecureSocket.close()
                    } catch (_: IOException) {
                    }

                    Log.e(
                        TAG,
                        "Both RFCOMM connection methods failed",
                        e
                    )

                    notifyConnection(null, false)

                    false
                }

            } catch (e: SecurityException) {

                Log.e(
                    TAG,
                    "Bluetooth security exception during connect",
                    e
                )

                notifyConnection(null, false)

                false

            } catch (e: Exception) {

                Log.e(
                    TAG,
                    "Echo Bluetooth connection failed",
                    e
                )

                notifyConnection(null, false)

                false
            }
        }

    suspend fun send(command: String): Boolean =
        withContext(Dispatchers.IO) {

            try {

                val stream =
                    output ?: return@withContext false

                val data =
                    (command.trim() + "\n")
                        .toByteArray(Charsets.UTF_8)

                stream.write(data)
                stream.flush()

                Log.d(
                    TAG,
                    "Sent command: ${command.trim()}"
                )

                true

            } catch (e: IOException) {

                Log.w(
                    TAG,
                    "Bluetooth command failed",
                    e
                )

                false

            } catch (e: Exception) {

                Log.e(
                    TAG,
                    "Unexpected Bluetooth send failure",
                    e
                )

                false
            }
        }

    fun disconnect() {
        disconnect(notify = true)
    }

    @SuppressLint("MissingPermission")
    private fun disconnect(notify: Boolean) {

        try {
            socket?.close()
        } catch (e: IOException) {
            Log.w(
                TAG,
                "Error closing Bluetooth socket",
                e
            )
        } catch (e: Exception) {
            Log.w(
                TAG,
                "Unexpected error closing Bluetooth socket",
                e
            )
        }

        socket = null
        output = null

        if (notify) {
            notifyConnection(null, false)
        }
    }

    private fun notifyConnection(
        device: BluetoothDevice?,
        connected: Boolean
    ) {
        mainHandler.post {
            try {
                onConnectionChanged?.invoke(
                    device,
                    connected
                )
            } catch (e: Exception) {
                /*
                 * IMPORTANT:
                 * A UI callback must never be allowed to kill the
                 * Bluetooth coroutine/process.
                 */
                Log.e(
                    TAG,
                    "Connection callback failed",
                    e
                )
            }
        }
    }

    fun isConnected(): Boolean =
        socket?.isConnected == true

    private fun hasBluetoothPermission(): Boolean {

        return if (
            Build.VERSION.SDK_INT <
            Build.VERSION_CODES.S
        ) {

            ContextCompat.checkSelfPermission(
                appContext,
                Manifest.permission.ACCESS_FINE_LOCATION
            ) == PackageManager.PERMISSION_GRANTED

        } else {

            val connect =
                ContextCompat.checkSelfPermission(
                    appContext,
                    Manifest.permission.BLUETOOTH_CONNECT
                ) == PackageManager.PERMISSION_GRANTED

            val scan =
                ContextCompat.checkSelfPermission(
                    appContext,
                    Manifest.permission.BLUETOOTH_SCAN
                ) == PackageManager.PERMISSION_GRANTED

            connect && scan
        }
    }

    @SuppressLint("MissingPermission")
    private fun safeName(
        device: BluetoothDevice
    ): String {

        return try {

            val name =
                device.name?.trim()

            if (!name.isNullOrBlank()) {
                name
            } else {
                "Unknown Echo (${device.address})"
            }

        } catch (e: SecurityException) {

            "Unknown Bluetooth Device"

        } catch (e: Exception) {

            "Unknown Bluetooth Device"
        }
    }

    fun close() {

        disconnect()

        try {
            appContext.unregisterReceiver(receiver)
        } catch (_: IllegalArgumentException) {
        } catch (e: Exception) {
            Log.w(
                TAG,
                "Failed to unregister Bluetooth receiver",
                e
            )
        }
    }

    companion object {

        private const val TAG =
            "EchoBluetooth"

        private val SPP_UUID: UUID =
            UUID.fromString(
                "00001101-0000-1000-8000-00805F9B34FB"
            )
    }
}
