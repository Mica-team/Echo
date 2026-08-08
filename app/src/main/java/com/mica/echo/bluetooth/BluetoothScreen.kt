package com.mica.echo.bluetooth

import android.Manifest
import android.content.pm.PackageManager
import android.os.Build
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import androidx.core.content.ContextCompat
import com.mica.echo.ui.viewmodel.AppViewModel

@Composable
fun BluetoothScreen(viewModel: AppViewModel) {
    val context = LocalContext.current
    val deviceState = viewModel.deviceState.collectAsState()
    val availableDevices = viewModel.availableDevices.collectAsState()

    val permissions = remember {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
            arrayOf(
                Manifest.permission.BLUETOOTH_SCAN,
                Manifest.permission.BLUETOOTH_CONNECT
            )
        } else {
            arrayOf(Manifest.permission.ACCESS_FINE_LOCATION)
        }
    }

    val permissionLauncher = rememberLauncherForActivityResult(
        ActivityResultContracts.RequestMultiplePermissions()
    ) { result ->
        if (result.values.all { it }) viewModel.scanDevices()
    }

    fun hasPermissions(): Boolean = permissions.all {
        ContextCompat.checkSelfPermission(context, it) == PackageManager.PERMISSION_GRANTED
    }

    Column(
        modifier = Modifier.fillMaxSize().padding(20.dp),
        verticalArrangement = Arrangement.spacedBy(16.dp)
    ) {
        Text("Bluetooth", style = MaterialTheme.typography.headlineMedium)
        Text(
            "Connect Echo Control to any compatible Echo ESP32.",
            style = MaterialTheme.typography.bodyLarge
        )

        Button(onClick = {
            if (hasPermissions()) viewModel.scanDevices()
            else permissionLauncher.launch(permissions)
        }) {
            Text(if (hasPermissions()) "Scan nearby" else "Allow Bluetooth & Scan")
        }

        Card(
            colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
            modifier = Modifier.fillMaxWidth()
        ) {
            Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                Text("Connection status", style = MaterialTheme.typography.titleMedium)
                Text(
                    if (deviceState.value.isConnected) {
                        "Connected to ${deviceState.value.name}"
                    } else {
                        "No Echo device connected"
                    }
                )
                if (deviceState.value.isConnected) {
                    Button(onClick = { viewModel.disconnectDevice() }) {
                        Text("Disconnect")
                    }
                }
            }
        }

        if (availableDevices.value.isEmpty()) {
            Text("No Bluetooth devices found yet. Turn on your Echo ESP32 and tap Scan nearby.")
        } else {
            availableDevices.value.forEach { device ->
                Card(
                    colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Row(
                        modifier = Modifier.fillMaxWidth().padding(16.dp),
                        horizontalArrangement = Arrangement.SpaceBetween
                    ) {
                        Column(modifier = Modifier.weight(1f)) {
                            Text(device.name)
                            Text(
                                device.address,
                                style = MaterialTheme.typography.bodySmall
                            )
                        }
                        Button(onClick = { viewModel.connectDevice(device) }) {
                            Text("Connect")
                        }
                    }
                }
            }
        }
    }
}
