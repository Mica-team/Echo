package com.echocontrol.app.bluetooth

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
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import com.echocontrol.app.ui.viewmodel.AppViewModel

@Composable
fun BluetoothScreen(viewModel: AppViewModel) {
    val deviceState = viewModel.deviceState.collectAsState()
    val availableDevices = viewModel.availableDevices.collectAsState()

    Column(
        modifier = Modifier
            .fillMaxSize()
            .padding(20.dp),
        verticalArrangement = Arrangement.spacedBy(16.dp)
    ) {
        Text("Bluetooth", style = MaterialTheme.typography.headlineMedium)
        Text("Manage nearby connections for Echo Control.", style = MaterialTheme.typography.bodyLarge)

        Button(onClick = { viewModel.scanDevices() }) {
            Text("Scan nearby")
        }

        Card(
            colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
            modifier = Modifier.fillMaxWidth()
        ) {
            Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                Text("Connection status", style = MaterialTheme.typography.titleMedium)
                Text(if (deviceState.value.isConnected) "Connected to ${deviceState.value.name}" else "Waiting for a device")
                if (deviceState.value.isConnected) {
                    Button(onClick = { viewModel.disconnectDevice() }) {
                        Text("Disconnect")
                    }
                }
            }
        }

        if (availableDevices.value.isEmpty()) {
            Text("No devices discovered yet. Tap scan to search.")
        } else {
            availableDevices.value.forEach { deviceName ->
                Card(
                    colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(16.dp),
                        horizontalArrangement = Arrangement.SpaceBetween
                    ) {
                        Text(deviceName)
                        Button(onClick = { viewModel.connectDevice(deviceName) }) {
                            Text("Connect")
                        }
                    }
                }
            }
        }
    }
}
