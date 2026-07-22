package com.mica.echo.navigation

import androidx.compose.foundation.layout.padding
import androidx.compose.material3.Icon
import androidx.compose.material3.NavigationBar
import androidx.compose.material3.NavigationBarItem
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
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
}
