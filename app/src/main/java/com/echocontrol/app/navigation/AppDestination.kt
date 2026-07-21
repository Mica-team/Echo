package com.echocontrol.app.navigation

import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Bluetooth
import androidx.compose.material.icons.filled.Dashboard
import androidx.compose.material.icons.filled.Memory
import androidx.compose.material.icons.filled.Settings
import androidx.compose.material.icons.filled.Tune
import androidx.compose.ui.graphics.vector.ImageVector

sealed class AppDestination(val route: String, val label: String, val icon: ImageVector) {
    data object Dashboard : AppDestination("dashboard", "Dashboard", Icons.Filled.Dashboard)
    data object Bluetooth : AppDestination("bluetooth", "Bluetooth", Icons.Filled.Bluetooth)
    data object Control : AppDestination("control", "Control", Icons.Filled.Tune)
    data object Status : AppDestination("status", "Status", Icons.Filled.Memory)
    data object Settings : AppDestination("settings", "Settings", Icons.Filled.Settings)
}
