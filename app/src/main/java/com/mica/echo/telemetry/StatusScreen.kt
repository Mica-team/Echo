package com.mica.echo.telemetry

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import com.mica.echo.ui.viewmodel.AppViewModel
import kotlinx.coroutines.delay
import java.util.Locale

@Composable
fun StatusScreen(viewModel: AppViewModel) {
    val deviceState = viewModel.deviceState.collectAsState()
    val telemetryData = viewModel.telemetryData.collectAsState()
    val telemetry = telemetryData.value

    LaunchedEffect(deviceState.value.isConnected) {
        if (deviceState.value.isConnected) {
            while (true) {
                viewModel.updateTelemetry()
                delay(1500)
            }
        }
    }

    val cpuFraction = (telemetry.cpuMhz / 240f).coerceIn(0f, 1f)

    Column(
        modifier = Modifier
            .fillMaxSize()
            .padding(20.dp),
        verticalArrangement = Arrangement.spacedBy(16.dp)
    ) {
        Text("Status", style = MaterialTheme.typography.headlineMedium)
        Text("Live Echo diagnostics.", style = MaterialTheme.typography.bodyLarge)

        Card(
            colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
            modifier = Modifier.fillMaxWidth()
        ) {
            Column(
                Modifier.padding(16.dp),
                verticalArrangement = Arrangement.spacedBy(10.dp)
            ) {
                Text("CPU", style = MaterialTheme.typography.titleMedium)

                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween
                ) {
                    Text(
                        if (telemetry.cpuMhz > 0) "${telemetry.cpuMhz} MHz" else "Waiting…",
                        style = MaterialTheme.typography.headlineSmall
                    )
                    Text("ESP32", style = MaterialTheme.typography.bodyMedium)
                }

                LinearProgressIndicator(
                    progress = { cpuFraction },
                    modifier = Modifier.fillMaxWidth()
                )

                Text(
                    "Clock frequency • updates every 1.5 s while connected",
                    style = MaterialTheme.typography.bodySmall
                )
            }
        }

        Card(
            colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
            modifier = Modifier.fillMaxWidth()
        ) {
            Column(
                Modifier.padding(16.dp),
                verticalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                Text("System", style = MaterialTheme.typography.titleMedium)
                Text(
                    "Temperature: " + String.format(
                        Locale.US,
                        "%.1f",
                        telemetry.temperature
                    ) + "°C"
                )
                Text(
                    "Free heap: " + if (telemetry.freeHeapBytes > 0) {
                        String.format(
                            Locale.US,
                            "%.1f KB",
                            telemetry.freeHeapBytes / 1024.0
                        )
                    } else {
                        "Waiting…"
                    }
                )
                Text("Bluetooth: " + telemetry.bluetoothStatus)
                Text(
                    "Connection: " +
                        if (deviceState.value.isConnected) "Connected" else "Offline"
                )
            }
        }
    }
}
