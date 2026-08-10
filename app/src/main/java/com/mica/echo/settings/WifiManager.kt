package com.mica.echo.settings

import android.Manifest
import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.content.IntentFilter
import android.content.pm.PackageManager
import android.location.LocationManager
import android.net.wifi.ScanResult
import android.net.wifi.WifiManager
import android.net.wifi.WifiNetworkSuggestion
import android.os.Build
import androidx.core.content.ContextCompat
import kotlinx.coroutines.delay

class WifiManager(private val context: Context) {
    private val appContext = context.applicationContext
    private val wifiManager = appContext.getSystemService(Context.WIFI_SERVICE) as WifiManager
    private val locationManager = appContext.getSystemService(Context.LOCATION_SERVICE) as LocationManager
    private val preferences = appContext.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)

    fun hasScanPermission(): Boolean =
        ContextCompat.checkSelfPermission(appContext, Manifest.permission.ACCESS_FINE_LOCATION) == PackageManager.PERMISSION_GRANTED

    fun hasSuggestionPermission(): Boolean =
        Build.VERSION.SDK_INT < 33 ||
            ContextCompat.checkSelfPermission(appContext, Manifest.permission.NEARBY_WIFI_DEVICES) == PackageManager.PERMISSION_GRANTED

    fun locationServicesEnabled(): Boolean =
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.P) {
            locationManager.isLocationEnabled
        } else {
            @Suppress("DEPRECATION")
            locationManager.isProviderEnabled(LocationManager.GPS_PROVIDER) ||
                locationManager.isProviderEnabled(LocationManager.NETWORK_PROVIDER)
        }

    /**
     * Scan nearby networks while retaining Android's cached results when an
     * active scan is throttled. The previous implementation also waited the
     * full timeout even after the scan completed.
     */
    suspend fun scan(): List<WifiNetwork> {
        if (!hasScanPermission()) return emptyList()
        if (!locationServicesEnabled()) return emptyList()
        if (!wifiManager.isWifiEnabled) return emptyList()

        val cached = getNetworksFromResults()
        var scanCompleted = false
        val scanReceiver = object : BroadcastReceiver() {
            override fun onReceive(context: Context?, intent: Intent?) {
                if (intent?.action == WifiManager.SCAN_RESULTS_AVAILABLE_ACTION) {
                    scanCompleted = true
                }
            }
        }

        return try {
            ContextCompat.registerReceiver(
                appContext,
                scanReceiver,
                IntentFilter(WifiManager.SCAN_RESULTS_AVAILABLE_ACTION),
                ContextCompat.RECEIVER_EXPORTED
            )

            @Suppress("DEPRECATION")
            val started = wifiManager.startScan()

            if (started) {
                repeat(14) {
                    if (scanCompleted) return@repeat
                    delay(500)
                }
            }

            val fresh = getNetworksFromResults()
            if (fresh.isNotEmpty()) fresh else cached
        } catch (_: SecurityException) {
            cached
        } finally {
            try {
                appContext.unregisterReceiver(scanReceiver)
            } catch (_: IllegalArgumentException) {
                // Already unregistered.
            }
        }
    }

    private fun getNetworksFromResults(): List<WifiNetwork> {
        @Suppress("DEPRECATION")
        return wifiManager.scanResults
            .asSequence()
            .filter { it.SSID.isNotBlank() }
            .distinctBy { it.SSID }
            .sortedByDescending { it.level }
            .map { it.toWifiNetwork() }
            .toList()
    }

    fun currentSsid(): String? {
        if (!hasScanPermission()) return null
        @Suppress("DEPRECATION")
        return wifiManager.connectionInfo?.ssid
            ?.removePrefix("\"")
            ?.removeSuffix("\"")
            ?.takeUnless { it.isBlank() || it == "<unknown ssid>" }
    }

    fun connect(network: WifiNetwork, password: String): Result<Unit> {
        if (!hasSuggestionPermission()) return Result.failure(IllegalStateException("Wi-Fi device permission is required"))
        if (network.security != WifiSecurity.OPEN && password.isBlank()) return Result.failure(IllegalArgumentException("Wi-Fi password is required"))
        if (network.security == WifiSecurity.WEP) return Result.failure(IllegalArgumentException("WEP networks are not supported"))

        return try {
            val previousSsid = preferences.getString(KEY_SAVED_SSID, null)
            if (!previousSsid.isNullOrBlank() && previousSsid != network.ssid) removeSuggestion(previousSsid)

            val builder = WifiNetworkSuggestion.Builder().setSsid(network.ssid)
            when (network.security) {
                WifiSecurity.OPEN -> Unit
                WifiSecurity.WPA3 -> {
                    if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.R) builder.setWpa3Passphrase(password)
                    else builder.setWpa2Passphrase(password)
                }
                WifiSecurity.WPA2 -> builder.setWpa2Passphrase(password)
                WifiSecurity.WEP -> error("WEP is handled above")
            }

            val status = wifiManager.addNetworkSuggestions(listOf(builder.build()))
            if (status != WifiManager.STATUS_NETWORK_SUGGESTIONS_SUCCESS) {
                return Result.failure(IllegalStateException("Android rejected the Wi-Fi suggestion: $status"))
            }

            preferences.edit().putString(KEY_SAVED_SSID, network.ssid).apply()
            Result.success(Unit)
        } catch (security: SecurityException) {
            Result.failure(security)
        }
    }

    fun forgetSavedNetwork() {
        val ssid = preferences.getString(KEY_SAVED_SSID, null) ?: return
        removeSuggestion(ssid)
        preferences.edit().remove(KEY_SAVED_SSID).apply()
    }

    fun savedSsid(): String? = preferences.getString(KEY_SAVED_SSID, null)

    private fun removeSuggestion(ssid: String) {
        if (!hasSuggestionPermission()) return
        try {
            wifiManager.removeNetworkSuggestions(listOf(WifiNetworkSuggestion.Builder().setSsid(ssid).build()))
        } catch (_: Exception) {
            // Android may already have removed the suggestion.
        }
    }

    private fun ScanResult.toWifiNetwork(): WifiNetwork {
        val capabilities = capabilities.uppercase()
        val security = when {
            capabilities.contains("WEP") -> WifiSecurity.WEP
            capabilities.contains("SAE") -> WifiSecurity.WPA3
            capabilities.contains("WPA") || capabilities.contains("PSK") -> WifiSecurity.WPA2
            else -> WifiSecurity.OPEN
        }
        return WifiNetwork(SSID, level, security)
    }

    companion object {
        private const val PREFS_NAME = "echo_wifi_preferences"
        private const val KEY_SAVED_SSID = "saved_wifi_ssid"
    }
}

data class WifiNetwork(
    val ssid: String,
    val signalLevel: Int,
    val security: WifiSecurity
)

enum class WifiSecurity {
    OPEN,
    WPA2,
    WPA3,
    WEP
}
