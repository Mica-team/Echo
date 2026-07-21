package com.echocontrol.app.ui.screens

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
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import com.echocontrol.app.ui.viewmodel.AppViewModel

@Composable
fun ControlScreen(viewModel: AppViewModel) {
    val commands = viewModel.controlCommands.collectAsState()
    val deviceState = viewModel.deviceState.collectAsState()

    Column(
        modifier = Modifier
            .fillMaxSize()
            .verticalScroll(rememberScrollState())
            .padding(20.dp),
        verticalArrangement = Arrangement.spacedBy(16.dp)
    ) {
        Text("Control", style = MaterialTheme.typography.headlineMedium)
        Text("Send commands and tune the device experience.", style = MaterialTheme.typography.bodyLarge)

        Card(
            colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
            modifier = Modifier.fillMaxWidth()
        ) {
            Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                Text("Manual controls", style = MaterialTheme.typography.titleMedium)
                Text(if (deviceState.value.isConnected) "Device is ready for control actions." else "Connect a device first to enable controls.")
                Button(onClick = { viewModel.updateTelemetry() }) {
                    Text("Refresh telemetry")
                }
            }
        }

        commands.value.forEach { command ->
            Card(
                colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
                modifier = Modifier.fillMaxWidth()
            ) {
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(16.dp),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
                        Text(command.name, style = MaterialTheme.typography.titleMedium)
                        Text(command.description)
                    }
                    Button(onClick = { viewModel.executeCommand(command.id) }) {
                        Text(if (command.isActive) "Active" else "Run")
                    }
                }
            }
        }
    }
}
