package com.laniakea.ui

import androidx.compose.foundation.layout.*
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.AccountBox
import androidx.compose.material.icons.filled.Home
import androidx.compose.material.icons.filled.Info
import androidx.compose.material3.*
import androidx.compose.material3.adaptive.navigationsuite.NavigationSuiteScaffold
import androidx.compose.runtime.*
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.lifecycle.viewmodel.compose.viewModel
import com.laniakea.viewmodel.LaniakeaViewModel
import com.laniakea.ui.screens.*

// Define the destinations here or import them if they are in a separate file
enum class AppDestinations(
    val label: String,
    val icon: ImageVector,
) {
    HOME("Home", Icons.Default.Home),
    INSIGHTS("Insights", Icons.Default.Info),
    PROFILE("Profile", Icons.Default.AccountBox),
}

@Composable
fun LaniakeaApp(vm: LaniakeaViewModel = viewModel()) {
    // Explicitly specifying the type for rememberSaveable to fix inference errors
    var currentDestination by rememberSaveable { mutableStateOf(AppDestinations.HOME) }

    NavigationSuiteScaffold(
        navigationSuiteItems = {
            AppDestinations.entries.forEach { dest ->
                item(
                    icon = { Icon(dest.icon, contentDescription = dest.label) },
                    label = { Text(dest.label) },
                    selected = dest == currentDestination,
                    onClick = { currentDestination = dest }
                )
            }
        }
    ) {
        Scaffold(
            modifier = Modifier.fillMaxSize(),
        ) { paddingValues ->
            when (currentDestination) {
                AppDestinations.HOME -> HomeScreen(paddingValues, vm)
                AppDestinations.INSIGHTS -> InsightScreen(paddingValues, vm)
                AppDestinations.PROFILE -> ProfileScreen(paddingValues, vm)
            }
        }
    }
}