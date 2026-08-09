package com.mica.echo.navigation

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.Icon
import androidx.compose.material3.NavigationBar
import androidx.compose.material3.NavigationBarItem
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import androidx.lifecycle.viewmodel.compose.viewModel
import androidx.navigation.NavHostController
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import androidx.navigation.compose.currentBackStackEntryAsState
import androidx.navigation.compose.rememberNavController
import com.mica.echo.bluetooth.BluetoothScreen
import com.mica.echo.settings.SettingsScreen
import com.mica.echo.telemetry.StatusScreen
import com.mica.echo.ui.screens.ControlScreen
import com.mica.echo.ui.screens.DashboardScreen
import com.mica.echo.ui.viewmodel.AppViewModel

@Composable
fun AppNavHost(
    navController: NavHostController = rememberNavController(),
    viewModel: AppViewModel = viewModel()
) {
    val items = listOf(
        AppDestination.Dashboard,
        AppDestination.Bluetooth,
        AppDestination.Control,
        AppDestination.Status,
        AppDestination.Settings
    )

    // This is the ESP32 provisioning request triggered after Bluetooth connects.
    // null = no setup pending, blank = SSID could not be detected automatically.
    val espWifiSsid by viewModel.espWifiPasswordRequest.collectAsState()
    var wifiPassword by remember { mutableStateOf("") }
    var manualSsid by remember { mutableStateOf("") }

    Scaffold(
        bottomBar = {
            NavigationBar {
                val currentBackStackEntry = navController.currentBackStackEntryAsState().value
                val currentRoute = currentBackStackEntry?.destination?.route

                items.forEach { destination ->
                    NavigationBarItem(
                        selected = currentRoute == destination.route,
                        onClick = {
                            navController.navigate(destination.route) {
                                popUpTo(navController.graph.startDestinationId) { saveState = true }
                                launchSingleTop = true
                                restoreState = true
                            }
                        },
                        icon = { Icon(destination.icon, contentDescription = destination.label) },
                        label = { Text(destination.label) }
                    )
                }
            }
        }
    ) { innerPadding ->
        NavHost(
            navController = navController,
            startDestination = AppDestination.Dashboard.route,
            modifier = Modifier.padding(innerPadding)
        ) {
            composable(AppDestination.Dashboard.route) { DashboardScreen(viewModel) }
            composable(AppDestination.Bluetooth.route) { BluetoothScreen(viewModel) }
            composable(AppDestination.Control.route) { ControlScreen(viewModel) }
            composable(AppDestination.Status.route) { StatusScreen(viewModel) }
            composable(AppDestination.Settings.route) { SettingsScreen(viewModel) }
        }
    }

    if (espWifiSsid != null) {
        AlertDialog(
            onDismissRequest = {
                wifiPassword = ""
                manualSsid = ""
                viewModel.cancelEspWifiProvisioning()
            },
            title = { Text("Connect Echo to Wi-Fi") },
            text = {
                Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
                    if (espWifiSsid!!.isBlank()) {
                        Text("Android could not read the current Wi-Fi network automatically.")
                        OutlinedTextField(
                            value = manualSsid,
                            onValueChange = { manualSsid = it },
                            label = { Text("Wi-Fi SSID") },
                            singleLine = true
                        )
                    } else {
                        Text("Wi-Fi network detected:")
                        Text(espWifiSsid!!)
                    }

                    Text("Enter the Wi-Fi password. It will be sent to Echo over Bluetooth.")
                    OutlinedTextField(
                        value = wifiPassword,
                        onValueChange = { wifiPassword = it },
                        label = { Text("Wi-Fi password") },
                        singleLine = true
                    )
                }
            },
            confirmButton = {
                Button(
                    enabled = wifiPassword.isNotEmpty() &&
                        (espWifiSsid!!.isNotBlank() || manualSsid.isNotBlank()),
                    onClick = {
                        val password = wifiPassword
                        val ssid = manualSsid
                        wifiPassword = ""
                        manualSsid = ""
                        viewModel.submitEspWifiPassword(password, ssid)
                    }
                ) {
                    Text("Send to Echo")
                }
            },
            dismissButton = {
                Button(onClick = {
                    wifiPassword = ""
                    manualSsid = ""
                    viewModel.cancelEspWifiProvisioning()
                }) {
                    Text("Cancel")
                }
            }
        )
    }
}
