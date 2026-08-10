package com.mica.echo.settings

import android.Manifest
import android.os.Build
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import com.mica.echo.ui.viewmodel.AppViewModel

@Composable
fun SettingsScreen(viewModel: AppViewModel) {
    val settings by viewModel.settings.collectAsState()
    val wifiNetworks by viewModel.wifiNetworks.collectAsState()
    val currentWifi by viewModel.wifiCurrentSsid.collectAsState()
    val savedWifi by viewModel.wifiSavedSsid.collectAsState()
    val wifiPasswordRequest by viewModel.wifiPasswordRequest.collectAsState()
    val wifiStatus by viewModel.wifiStatus.collectAsState()

    var showWifiPicker by remember { mutableStateOf(false) }
    var password by remember { mutableStateOf("") }

    val wifiPermissionLauncher = rememberLauncherForActivityResult(
        ActivityResultContracts.RequestMultiplePermissions()
    ) {
        viewModel.scanWifiNetworks()
        showWifiPicker = true
    }

    fun openWifiPicker() {
        // Android 12+ requires ACCESS_FINE_LOCATION and ACCESS_COARSE_LOCATION
        // to be requested together. Fine location is needed for Wi-Fi scan results.
        val permissions = buildList {
            add(Manifest.permission.ACCESS_FINE_LOCATION)
            add(Manifest.permission.ACCESS_COARSE_LOCATION)
            if (Build.VERSION.SDK_INT >= 33) add(Manifest.permission.NEARBY_WIFI_DEVICES)
        }.toTypedArray()
        wifiPermissionLauncher.launch(permissions)
    }

    LazyColumn(
        modifier = Modifier.padding(20.dp),
        verticalArrangement = Arrangement.spacedBy(16.dp)
    ) {
        item {
            Text("Settings", style = MaterialTheme.typography.headlineMedium)
            Text(
                "Customize the app layout and connection behavior.",
                style = MaterialTheme.typography.bodyLarge,
                modifier = Modifier.padding(top = 4.dp)
            )
        }

        item {
            Card(
                colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
                modifier = Modifier.fillMaxWidth()
            ) {
                Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    Text("Appearance", style = MaterialTheme.typography.titleMedium)
                    val selectedTheme = settings["theme_mode"] ?: "dark"

                    Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        if (selectedTheme == "light") Button(onClick = { viewModel.setSetting("theme_mode", "light") }) { Text("Light") }
                        else OutlinedButton(onClick = { viewModel.setSetting("theme_mode", "light") }) { Text("Light") }

                        if (selectedTheme == "dark") Button(onClick = { viewModel.setSetting("theme_mode", "dark") }) { Text("Dark") }
                        else OutlinedButton(onClick = { viewModel.setSetting("theme_mode", "dark") }) { Text("Dark") }

                        if (selectedTheme == "system") Button(onClick = { viewModel.setSetting("theme_mode", "system") }) { Text("System") }
                        else OutlinedButton(onClick = { viewModel.setSetting("theme_mode", "system") }) { Text("System") }
                    }
                    Text("Current mode: ${selectedTheme.replaceFirstChar { it.uppercase() }}")
                }
            }
        }

        item {
            Card(
                colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
                modifier = Modifier.fillMaxWidth()
            ) {
                Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    Text("Wi-Fi", style = MaterialTheme.typography.titleMedium)
                    Text("Connected: ${currentWifi ?: "Not connected"}")
                    Text("Saved network: ${savedWifi ?: "None"}")
                    Button(onClick = { openWifiPicker() }) { Text(if (savedWifi == null) "Choose Wi-Fi" else "Switch network") }
                    if (savedWifi != null) OutlinedButton(onClick = { viewModel.forgetSavedWifi() }) { Text("Forget saved network") }
                    wifiStatus?.let { Text(it, style = MaterialTheme.typography.bodySmall) }
                }
            }
        }

        item {
            Text("Auto refresh: ${settings["auto_refresh"]}")
            Text("Log level: ${settings["log_level"]}")
        }
    }

    if (showWifiPicker) {
        AlertDialog(
            onDismissRequest = { showWifiPicker = false },
            title = { Text("Choose Wi-Fi") },
            text = {
                Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    Button(onClick = { viewModel.scanWifiNetworks() }) { Text("Rescan") }
                    if (wifiNetworks.isEmpty()) {
                        Text("No networks found. Make sure Wi-Fi is on and Location is enabled.")
                    } else {
                        wifiNetworks.forEach { network ->
                            Row(
                                modifier = Modifier.fillMaxWidth().clickable {
                                    showWifiPicker = false
                                    password = ""
                                    if (network.security == WifiSecurity.OPEN) viewModel.connectToWifi(network)
                                    else viewModel.requestWifiPassword(network)
                                }.padding(vertical = 10.dp),
                                horizontalArrangement = Arrangement.SpaceBetween
                            ) {
                                Column {
                                    Text(network.ssid)
                                    Text("${network.security.name} • signal ${network.signalLevel} dBm", style = MaterialTheme.typography.bodySmall)
                                }
                            }
                        }
                    }
                }
            },
            confirmButton = { OutlinedButton(onClick = { showWifiPicker = false }) { Text("Close") } }
        )
    }

    wifiPasswordRequest?.let { network ->
        AlertDialog(
            onDismissRequest = { viewModel.cancelWifiPasswordRequest() },
            title = { Text("Password for ${network.ssid}") },
            text = { OutlinedTextField(value = password, onValueChange = { password = it }, label = { Text("Wi-Fi password") }, singleLine = true) },
            confirmButton = {
                Button(enabled = password.isNotEmpty(), onClick = { viewModel.connectToWifi(network, password); password = "" }) { Text("Connect") }
            },
            dismissButton = { OutlinedButton(onClick = { viewModel.cancelWifiPasswordRequest() }) { Text("Cancel") } }
        )
    }
}
