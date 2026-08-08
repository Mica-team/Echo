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

/**
 * A Bluetooth Classic device that Echo can display/connect to.
 *
 * The MAC address is supplied by Android.
 * Nothing is hardcoded to a particular ESP32.
 */
data class EchoBluetoothDevice(
    val name: String,
    val address: String
)

class EchoBluetoothManager(context: Context) {

    private val appContext = context.applicationContext

    private val mainHandler = Handler(Looper.getMainLooper())

    private val adapter: BluetoothAdapter? =
        BluetoothAdapter.getDefaultAdapter()

    private var socket: BluetoothSocket? = null
    private var output: OutputStream? = null

    /**
     * Key = MAC address.
     *
     * This means different ESP32 units can all work.
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

                        if (device != null) {
                            addDevice(device)
                        }
                    }

                    BluetoothAdapter.ACTION_DISCOVERY_STARTED -> {
                        Log.d(TAG, "Classic Bluetooth discovery started")
                    }

                    BluetoothAdapter.ACTION_DISCOVERY_FINISHED -> {
                        Log.d(TAG, "Classic Bluetooth discovery finished")

                        /*
                         * Some Android/Bluetooth stacks don't reliably send
                         * ACTION_FOUND for devices that are already paired.
                         *
                         * Refresh paired devices after discovery.
                         */
                        addBondedDevices()
                    }

                    BluetoothDevice.ACTION_BOND_STATE_CHANGED -> {
                        Log.d(TAG, "Bluetooth bond state changed")
                        addBondedDevices()
                    }
                }
            } catch (e: SecurityException) {
                Log.e(
                    TAG,
                    "Bluetooth permission/security error in receiver",
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
            val filter = IntentFilter().apply {
                addAction(BluetoothDevice.ACTION_FOUND)
                addAction(BluetoothAdapter.ACTION_DISCOVERY_STARTED)
                addAction(BluetoothAdapter.ACTION_DISCOVERY_FINISHED)
                addAction(BluetoothDevice.ACTION_BOND_STATE_CHANGED)
            }

            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {

                /*
                 * Bluetooth system broadcasts are delivered by the
                 * Android system, so this receiver must be exported.
                 */
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

            Log.d(TAG, "Bluetooth receiver registered")

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
         * Immediately expose already-paired devices when listeners
         * are attached. This avoids depending on a discovery scan.
         */
        addBondedDevices()
    }

    /**
     * Scans Classic Bluetooth devices.
     *
     * IMPORTANT:
     * Paired devices are added BEFORE discovery starts.
     *
     * Therefore an already-paired old ESP32 does not depend on
     * ACTION_FOUND to appear in Echo.
     */
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
             * Do NOT filter by:
             * - name
             * - MAC address
             * - "Echo"
             *
             * Every paired Classic Bluetooth device is allowed to
             * appear. The user chooses which one to connect to.
             */
            devices.clear()

            Log.d(
                TAG,
                "Loading paired Classic Bluetooth devices..."
            )

            addBondedDevices()

            /*
             * Discovery can interfere with an RFCOMM connection,
             * so stop any previous scan first.
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

    /**
     * Reads Android's actual bonded-device list.
     *
     * This is the important path for your old paired ESP32.
     */
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

            val btAdapter = adapter ?: return

            val bonded = btAdapter.bondedDevices

            Log.d(
                TAG,
                "Bonded Classic Bluetooth devices: ${bonded.size}"
            )

            bonded.forEach { device ->

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
                "Failed to add discovered Bluetooth device",
                e
            )
        }
    }

    @SuppressLint("MissingPermission")
    private fun addDeviceInternal(
        device: BluetoothDevice
    ) {

        try {

            val address = device.address

            if (address.isNullOrBlank()) {
                Log.w(
                    TAG,
                    "Ignoring Bluetooth device with no address"
                )
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

    /**
     * Publishes all known Classic Bluetooth devices.
     *
     * There is deliberately NO "Echo" name filter here.
     */
    @SuppressLint("MissingPermission")
    private fun publishDevices() {

        val result =
            devices.values
                .mapNotNull { device ->

                    try {

                        val address = device.address

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
                .distinctBy { it.address }

        Log.d(
            TAG,
            "Publishing ${result.size} Bluetooth device(s)"
        )

        result.forEach {
            Log.d(
                TAG,
                "  ${it.name} [${it.address}]"
            )
        }

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

    /**
     * Connect using the actual Bluetooth MAC address.
     *
     * This works with different ESP32 units without hardcoding
     * any particular device.
     *
     * Uses Bluetooth Classic RFCOMM/SPP.
     */
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
                adapter ?: return@withContext false

            /*
             * Prefer the device we already discovered/loaded from
             * bondedDevices.
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
                        /*
                         * This does not require discovery.
                         * Android can create a BluetoothDevice object
                         * from a known address.
                         */
                        btAdapter.getRemoteDevice(address)
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
             * Discovery must be stopped before RFCOMM connection.
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

            disconnect(notify = false)

            /*
             * Standard Bluetooth Serial Port Profile UUID.
             *
             * This is the UUID commonly used by ESP32 BluetoothSerial.
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

                socket = secureSocket
                output = secureSocket.outputStream

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
             * Some Classic Bluetooth serial implementations work
             * better with an insecure RFCOMM socket.
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

                socket = insecureSocket
                output = insecureSocket.outputStream

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

    /**
     * Sends a text command through the Classic Bluetooth SPP stream.
     */
    suspend fun send(
        command: String
    ): Boolean = withContext(Dispatchers.IO) {

        try {

            val stream =
                output ?: run {
                    Log.w(
                        TAG,
                        "Cannot send: Bluetooth is not connected"
                    )
                    return@withContext false
                }

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
      
