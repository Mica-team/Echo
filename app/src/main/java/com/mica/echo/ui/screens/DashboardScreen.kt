package com.mica.echo.ui.screens

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import com.mica.echo.ui.viewmodel.AppViewModel
import java.util.Locale

@Composable
fun DashboardScreen(viewModel: AppViewModel) {
    val deviceState = viewModel.deviceState.collectAsState()
    val telemetryData = viewModel.telemetryData.collectAsState()
    val commands = viewModel.controlCommands.collectAsState()

    Column(
        modifier = Modifier
            .fillMaxSize()
            .verticalScroll(rememberScrollState())
            .padding(20.dp),
        verticalArrangement = Arrangement.spacedBy(16.dp)
    ) {
        Text("Dashboard", style = MaterialTheme.typography.headlineMedium)
        Text("Overview of the connected Echo system.", style = MaterialTheme.typography.bodyLarge)

        Row(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
            Button(onClick = { viewModel.scanDevices(); viewModel.updateTelemetry() }) {
                Text("Refresh")
            }
            Button(onClick = { viewModel.connectDevice("Echo Device 1") }) {
                Text("Connect demo")
            }
        }

        Card(
            colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
            modifier = Modifier.fillMaxWidth()
        ) {
            Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                Text("System status", style = MaterialTheme.typography.titleMedium)
                Text(if (deviceState.value.isConnected) "Connected to ${deviceState.value.name}" else "No device connected")
                Text("Battery: ${deviceState.value.batteryLevel}%")
                Text("Signal: ${deviceState.value.signalStrength} dBm")
            }
        }

        Card(
            colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
            modifier = Modifier.fillMaxWidth()
        ) {
            Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                Text("Telemetry snapshot", style = MaterialTheme.typography.titleMedium)
                Text("Temperature: ${String.format(Locale.US, "%.1f", telemetryData.value.temperature)}°C")
                Text("Humidity: ${String.format(Locale.US, "%.1f", telemetryData.value.humidity)}%")
                Text("Pressure: ${String.format(Locale.US, "%.1f", telemetryData.value.pressure)} hPa")
                Text("Active commands: ${commands.value.count { it.isActive }}")
            }
        }
    }
}
