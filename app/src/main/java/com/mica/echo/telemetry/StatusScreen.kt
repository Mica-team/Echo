package com.mica.echo.telemetry

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
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
import com.mica.echo.ui.viewmodel.AppViewModel
import java.util.Locale

@Composable
fun StatusScreen(viewModel: AppViewModel) {
    val deviceState = viewModel.deviceState.collectAsState()
    val telemetryData = viewModel.telemetryData.collectAsState()

    Column(
        modifier = Modifier
            .fillMaxSize()
            .padding(20.dp),
        verticalArrangement = Arrangement.spacedBy(16.dp)
    ) {
        Text("Status", style = MaterialTheme.typography.headlineMedium)
        Text("Live telemetry and diagnostics appear here.", style = MaterialTheme.typography.bodyLarge)

        Button(onClick = { viewModel.updateTelemetry() }) {
            Text("Refresh data")
        }

        Card(
            colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
            modifier = Modifier.fillMaxWidth()
        ) {
            Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                Text("Telemetry snapshot", style = MaterialTheme.typography.titleMedium)
                Text("Status: ${if (deviceState.value.isConnected) "Connected" else "Offline"}")
                Text("Temperature: ${String.format(Locale.US, "%.1f", telemetryData.value.temperature)}°C")
                Text("Humidity: ${String.format(Locale.US, "%.1f", telemetryData.value.humidity)}%")
                Text("Signal: ${telemetryData.value.rssi} dBm")
                Text("Battery: ${deviceState.value.batteryLevel}%")
            }
        }
    }
}
