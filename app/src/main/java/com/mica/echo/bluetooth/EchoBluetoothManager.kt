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

data class EchoBluetoothDevice(
    val name: String,
    val address: String
)

class EchoBluetoothManager(context: Context) {

    private val appContext = context.applicationContext

    private val mainHandler =
        Handler(Looper.getMainLooper())

    private val adapter: BluetoothAdapter? =
        BluetoothAdapter.getDefaultAdapter()

    private var socket: BluetoothSocket? = null
    private var output: OutputStream? = null

    /*
     * Key = Bluetooth MAC address.
     *
     * No ESP32 address is hardcoded.
     * Every compatible paired ESP32 can therefore be used.
     */
    private val devices =
        linkedMapOf<String, BluetoothDevice>()

    private var onDevicesChanged:
        ((List<EchoBluetoothDevice>) -> Unit)? = null

    private var onConnectionChanged:
        ((BluetoothDevice?, Boolean) -> Unit)? = null

    private val receiver = object : BroadcastReceiver() {

        @SuppressLint("MissingPermission")
        override fun onReceive(
            context: Context,
            intent: Intent
        ) {
            try {

                when (intent.action) {

                    BluetoothDevice.ACTION_FOUND -> {

                        val device =
                            if (
                                Build.VERSION.SDK_INT >=
                                Build.VERSION_CODES.TIRAMISU
                            ) {
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

                        if (device != null) {
                            addDevice(device)
                        }
                    }

                    BluetoothAdapter.ACTION_DISCOVERY_STARTED -> {

                        Log.d(
                            TAG,
                            "Classic Bluetooth discovery started"
                        )
                    }

                    BluetoothAdapter.ACTION_DISCOVERY_FINISHED -> {

                        Log.d(
                            TAG,
                            "Classic Bluetooth discovery finished"
                        )

                        /*
                         * Refresh bonded devices.
                         *
                         * This is important because the ESP32 may
                         * already be paired with the phone.
                         */
                        addBondedDevices()
                    }

                    BluetoothDevice.ACTION_BOND_STATE_CHANGED -> {

                        Log.d(
                            TAG,
                            "Bluetooth bond state changed"
                        )

                        addBondedDevices()
                    }
                }

            } catch (e: SecurityException) {

                Log.e(
                    TAG,
                    "Bluetooth security error in receiver",
                    e
                )

            } catch (e: Exception) {

                Log.e(
                    TAG,
                    "Bluetooth receiver error",
                    e
                )
            }
        }
    }

    init {
        registerBluetoothReceiver()
    }

    private fun registerBluetoothReceiver() {

        try {

            val filter =
                IntentFilter().apply {

                    addAction(
                        BluetoothDevice.ACTION_FOUND
                    )

                    addAction(
                        BluetoothAdapter.ACTION_DISCOVERY_STARTED
                    )

                    addAction(
                        BluetoothAdapter.ACTION_DISCOVERY_FINISHED
                    )

                    addAction(
                        BluetoothDevice.ACTION_BOND_STATE_CHANGED
                    )
                }

            if (
                Build.VERSION.SDK_INT >=
                Build.VERSION_CODES.TIRAMISU
            ) {

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

            Log.d(
                TAG,
                "Bluetooth receiver registered"
            )

        } catch (e: Exception) {

            Log.e(
                TAG,
                "Failed to register Bluetooth receiver",
                e
            )
        }
    }

    fun setListeners(
        devicesChanged:
            (List<EchoBluetoothDevice>) -> Unit,

        connectionChanged:
            (BluetoothDevice?, Boolean) -> Unit
    ) {

        onDevicesChanged = devicesChanged
        onConnectionChanged = connectionChanged

        /*
         * Immediately load already-paired devices.
         */
        addBondedDevices()
    }

    @SuppressLint("MissingPermission")
    fun scan() {

        if (!hasBluetoothPermission()) {

            Log.w(
                TAG,
                "Bluetooth permission is not available"
            )

            publishDevices()
            return
        }

        val btAdapter = adapter

        if (btAdapter == null) {

            Log.e(
                TAG,
                "Bluetooth adapter is unavailable"
            )

            publishDevices()
            return
        }

        try {

            if (!btAdapter.isEnabled) {

                Log.w(
                    TAG,
                    "Bluetooth is disabled"
                )

                publishDevices()
                return
            }

            /*
             * Don't filter by name or MAC address.
             *
             * This keeps the app usable with different ESP32 units.
             */
            devices.clear()

            /*
             * First load paired Classic Bluetooth devices.
             *
             * This is the important part for the old paired ESP32.
             */
            addBondedDevices()

            /*
             * Stop any previous discovery.
             */
            try {

                btAdapter.cancelDiscovery()

            } catch (e: Exception) {

                Log.w(
                    TAG,
                    "Could not cancel previous discovery",
                    e
                )
            }

            /*
             * Start Classic Bluetooth discovery.
             */
            val started =
                try {

                    btAdapter.startDiscovery()

                } catch (e: Exception) {

                    Log.e(
                        TAG,
                        "startDiscovery() failed",
                        e
                    )

                    false
                }

            Log.d(
                TAG,
                "Classic Bluetooth discovery requested: $started"
            )

            publishDevices()

        } catch (e: SecurityException) {

            Log.e(
                TAG,
                "Bluetooth security failure during scan",
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

        if (!hasBluetoothPermission()) {

            Log.w(
                TAG,
                "Cannot read bonded devices: permission missing"
            )

            return
        }

        try {

            val btAdapter =
                adapter ?: return

            val bondedDevices =
                btAdapter.bondedDevices

            Log.d(
                TAG,
                "Bonded Bluetooth devices: ${bondedDevices.size}"
            )

            bondedDevices.forEach { device ->

                try {

                    addDeviceInternal(device)

                } catch (e: Exception) {

                    Log.w(
                        TAG,
                        "Failed to process bonded device",
                        e
                    )
                }
            }

            publishDevices()

        } catch (e: SecurityException) {

            Log.e(
                TAG,
                "Cannot access bonded Bluetooth devices",
                e
            )

        } catch (e: Exception) {

            Log.e(
                TAG,
                "Failed to load bonded Bluetooth devices",
                e
            )
        }
    }

    @SuppressLint("MissingPermission")
    private fun addDevice(
        device: BluetoothDevice
    ) {

        try {

            addDeviceInternal(device)
            publishDevices()

        } catch (e: SecurityException) {

            Log.e(
                TAG,
                "Cannot access discovered Bluetooth device",
                e
            )

        } catch (e: Exception) {

            Log.e(
                TAG,
                "Failed to add Bluetooth device",
                e
            )
        }
    }

    @SuppressLint("MissingPermission")
    private fun addDeviceInternal(
        device: BluetoothDevice
    ) {

        try {

            val address =
                device.address

            if (address.isNullOrBlank()) {
                return
            }

            devices[address] = device

            Log.d(
                TAG,
                "Bluetooth device available: " +
                    "${safeName(device)} [$address]"
            )

        } catch (e: SecurityException) {

            Log.e(
                TAG,
                "Cannot read Bluetooth device address",
                e
            )
        }
    }

    @SuppressLint("MissingPermission")
    private fun publishDevices() {

        val result =
            devices.values
                .mapNotNull { device ->

                    try {

                        val address =
                            device.address

                        if (address.isNullOrBlank()) {
                            null
                        } else {

                            EchoBluetoothDevice(
                                name = safeName(device),
                                address = address
                            )
                        }

                    } catch (e: Exception) {

                        Log.w(
                            TAG,
                            "Could not create device entry",
                            e
                        )

                        null
                    }
                }
                .distinctBy {
                    it.address
                }

        Log.d(
            TAG,
            "Publishing ${result.size} Bluetooth device(s)"
        )

        mainHandler.post {

            try {

                onDevicesChanged?.invoke(result)

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
    suspend fun connect(
        address: String
    ): Boolean = withContext(Dispatchers.IO) {

        if (!hasBluetoothPermission()) {

            Log.w(
                TAG,
                "Bluetooth permission missing during connection"
            )

            return@withContext false
        }

        if (address.isBlank()) {

            Log.w(
                TAG,
                "Cannot connect: empty Bluetooth address"
            )

            return@withContext false
        }

        try {

            val btAdapter =
                adapter
                    ?: return@withContext false

            /*
             * Prefer the device already loaded from:
             *
             * 1. discovery
             * 2. bondedDevices
             *
             * If necessary, create a BluetoothDevice from the
             * supplied MAC address.
             */
            val device =
                devices[address]
                    ?: btAdapter.bondedDevices
                        .firstOrNull {
                            it.address.equals(
                                address,
                                ignoreCase = true
                            )
                        }
                    ?: try {

                        btAdapter.getRemoteDevice(
                            address
                        )

                    } catch (e: IllegalArgumentException) {

                        Log.e(
                            TAG,
                            "Invalid Bluetooth address: $address",
                            e
                        )

                        return@withContext false
                    }

            Log.d(
                TAG,
                "Preparing RFCOMM connection to " +
                    "${safeName(device)} [$address]"
            )

            /*
             * Discovery interferes with RFCOMM.
             */
            try {

                btAdapter.cancelDiscovery()

            } catch (e: Exception) {

                Log.w(
                    TAG,
                    "Could not cancel discovery before connection",
                    e
                )
            }

            disconnect(
                notify = false
            )

            /*
             * Standard Bluetooth Classic SPP UUID.
             *
             * This is compatible with ESP32 BluetoothSerial-style
             * firmware without changing the ESP32.
             */
            val secureSocket =
                device.createRfcommSocketToServiceRecord(
                    SPP_UUID
                )

            try {

                Log.d(
                    TAG,
                    "Trying secure RFCOMM/SPP..."
                )

                secureSocket.connect()

                socket =
                    secureSocket

                output =
                    secureSocket.outputStream

                notifyConnection(
                    device,
                    true
                )

                Log.d(
                    TAG,
                    "Secure RFCOMM/SPP connection successful"
                )

                return@withContext true

            } catch (e: IOException) {

                Log.w(
                    TAG,
                    "Secure RFCOMM/SPP failed",
                    e
                )

                try {
                    secureSocket.close()
                } catch (_: Exception) {
                }
            }

            /*
             * Fallback for Classic Bluetooth serial devices
             * that don't accept the secure socket.
             */
            val insecureSocket =
                device.createInsecureRfcommSocketToServiceRecord(
                    SPP_UUID
                )

            try {

                Log.d(
                    TAG,
                    "Trying insecure RFCOMM/SPP..."
                )

                insecureSocket.connect()

                socket =
                    insecureSocket

                output =
                    insecureSocket.outputStream

                notifyConnection(
                    device,
                    true
                )

                Log.d(
                    TAG,
                    "Insecure RFCOMM/SPP connection successful"
                )

                return@withContext true

            } catch (e: Exception) {

                Log.e(
                    TAG,
                    "Insecure RFCOMM/SPP failed",
                    e
                )

                try {
                    insecureSocket.close()
                } catch (_: Exception) {
                }

                notifyConnection(
                    null,
                    false
                )

                return@withContext false
            }

        } catch (e: SecurityException) {

            Log.e(
                TAG,
                "Bluetooth security exception during connection",
                e
            )

            notifyConnection(
                null,
                false
            )

            false

        } catch (e: Exception) {

            Log.e(
                TAG,
                "Echo Bluetooth connection failed",
                e
            )

            notifyConnection(
                null,
                false
            )

            false
        }
    }

    suspend fun send(
        command: String
    ): Boolean = withContext(Dispatchers.IO) {

        try {

            val stream =
                output
                    ?: return@withContext false

            val data =
                (
                    command.trim() + "\n"
                ).toByteArray(
                    Charsets.UTF_8
                )

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
        disconnect(
            notify = true
        )
    }

    private fun disconnect(
        notify: Boolean
    ) {

        try {

            socket?.close()

        } catch (e: Exception) {

            Log.w(
                TAG,
                "Error closing Bluetooth socket",
                e
            )
        }

        socket = null
        output = null

        if (notify) {

            notifyConnection(
                null,
                false
            )
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

                Log.e(
                    TAG,
                    "Connection callback failed",
                    e
                )
            }
        }
    }

    fun isConnected(): Boolean {
        return try {
            socket?.isConnected == true
        } catch (_: Exception) {
            false
        }
    }

    /*
   
