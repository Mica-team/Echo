package com.mica.echo.wifi

import android.Manifest
import android.annotation.SuppressLint
import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.content.IntentFilter
import android.content.pm.PackageManager
import android.net.wifi.ScanResult
import android.net.wifi.WifiManager
import android.os.Build
import androidx.core.content.ContextCompat

class PhoneWifiManager(context: Context) {
    private val appContext = context.applicationContext
    private val wifiManager =
        appContext.getSystemService(Context.WIFI_SERVICE) as WifiManager

    private var onNetworksChanged: ((List<String>) -> Unit)? = null

    private val receiver = object : BroadcastReceiver() {
        override fun onReceive(context: Context, intent: Intent) {
            if (WifiManager.SCAN_RESULTS_AVAILABLE_ACTION == intent.action) {
                publishResults()
            }
        }
    }

    init {
        ContextCompat.registerReceiver(
            appContext,
            receiver,
            IntentFilter(WifiManager.SCAN_RESULTS_AVAILABLE_ACTION),
            ContextCompat.RECEIVER_NOT_EXPORTED
        )
    }

    fun setListener(listener: (List<String>) -> Unit) {
        onNetworksChanged = listener
    }

    @SuppressLint("MissingPermission")
    fun scan() {
        if (!hasPermission()) return

        try {
            if (!wifiManager.startScan()) {
                publishResults()
            }
        } catch (_: SecurityException) {
            publishResults()
        }
    }

    @SuppressLint("MissingPermission")
    private fun publishResults() {
        if (!hasPermission()) return

        val networks = try {
            wifiManager.scanResults
                .mapNotNull { result: ScanResult ->
                    result.SSID.takeIf { it.isNotBlank() }
                }
                .distinct()
                .sorted()
        } catch (_: SecurityException) {
            emptyList()
        }

        onNetworksChanged?.invoke(networks)
    }

    private fun hasPermission(): Boolean {
        if (ContextCompat.checkSelfPermission(
                appContext,
                Manifest.permission.ACCESS_WIFI_STATE
            ) != PackageManager.PERMISSION_GRANTED
        ) return false

        return if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            ContextCompat.checkSelfPermission(
                appContext,
                Manifest.permission.NEARBY_WIFI_DEVICES
            ) == PackageManager.PERMISSION_GRANTED
        } else {
            ContextCompat.checkSelfPermission(
                appContext,
                Manifest.permission.ACCESS_FINE_LOCATION
            ) == PackageManager.PERMISSION_GRANTED
        }
    }

    fun close() {
        try {
            appContext.unregisterReceiver(receiver)
        } catch (_: IllegalArgumentException) {
        }
    }
}
