package com.datalens.app.ui.navigation

import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Home
import androidx.compose.material.icons.filled.List
import androidx.compose.material.icons.filled.Notifications
import androidx.compose.material.icons.filled.Settings
import androidx.compose.material.icons.filled.Warning
import androidx.compose.material3.Icon
import androidx.compose.material3.NavigationBar
import androidx.compose.material3.NavigationBarItem
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.navigation.NavGraph.Companion.findStartDestination
import androidx.navigation.NavHostController
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import androidx.navigation.compose.currentBackStackEntryAsState
import androidx.navigation.compose.rememberNavController
import com.datalens.app.domain.model.UsagePeriod
import com.datalens.app.ui.alerts.AlertsScreen
import com.datalens.app.ui.appdetail.AppDetailScreen
import com.datalens.app.ui.apps.AppsScreen
import com.datalens.app.ui.onboarding.OnboardingScreen
import com.datalens.app.ui.overview.OverviewScreen
import com.datalens.app.ui.settings.AppManagementScreen
import com.datalens.app.ui.settings.AppManagementMode
import com.datalens.app.ui.settings.SettingsScreen

private data class TopLevelDestination(
    val route: String,
    val label: String,
    val icon: ImageVector,
)

private val destinations = listOf(
    TopLevelDestination(Routes.OVERVIEW, "Overview", Icons.Filled.Home),
    TopLevelDestination(Routes.APPS, "Apps", Icons.Filled.List),
    TopLevelDestination(Routes.ALERTS, "Alerts", Icons.Filled.Warning),
    TopLevelDestination(Routes.SETTINGS, "Settings", Icons.Filled.Settings),
)

@Composable
fun DataLensNavHost(startDestination: String) {
    val navController = rememberNavController()
    val backStackEntry by navController.currentBackStackEntryAsState()
    val currentRoute = backStackEntry?.destination?.route
    val showBottomBar = currentRoute in Routes.topLevel

    Scaffold(
        bottomBar = {
            if (showBottomBar) {
                NavigationBar {
                    destinations.forEach { destination ->
                        NavigationBarItem(
                            selected = currentRoute == destination.route,
                            onClick = { navController.navigateToTab(destination.route) },
                            icon = {
                                Icon(destination.icon, contentDescription = null)
                            },
                            label = { Text(destination.label) },
                        )
                    }
                }
            }
        },
    ) { innerPadding ->
        NavHost(
            navController = navController,
            startDestination = startDestination,
            modifier = Modifier
                .fillMaxSize()
                .padding(innerPadding),
        ) {
            composable(Routes.ONBOARDING) {
                OnboardingScreen(
                    onGranted = {
                        navController.navigate(Routes.OVERVIEW) {
                            popUpTo(Routes.ONBOARDING) { inclusive = true }
                        }
                    },
                )
            }

            composable(Routes.OVERVIEW) {
                OverviewScreen(
                    onAppClick = { uid, packageName, period ->
                        navController.navigate(Routes.appDetail(uid, packageName, period))
                    },
                    onOpenAppsTab = { navController.navigateToTab(Routes.APPS) },
                    onOpenSettings = { navController.navigateToTab(Routes.SETTINGS) },
                )
            }

            composable(Routes.APPS) {
                AppsScreen(
                    onAppClick = { uid, packageName, period ->
                        navController.navigate(Routes.appDetail(uid, packageName, period))
                    },
                )
            }

            composable(Routes.ALERTS) {
                AlertsScreen(
                    onOpenSettings = { navController.navigateToTab(Routes.SETTINGS) },
                )
            }

            composable(Routes.SETTINGS) {
                SettingsScreen(
                    onManagePinned = { navController.navigate(Routes.SETTINGS_PINNED) },
                    onManageHidden = { navController.navigate(Routes.SETTINGS_HIDDEN) },
                )
            }

            composable(Routes.SETTINGS_PINNED) {
                AppManagementScreen(
                    mode = AppManagementMode.PINNED,
                    onBack = { navController.popBackStack() },
                )
            }

            composable(Routes.SETTINGS_HIDDEN) {
                AppManagementScreen(
                    mode = AppManagementMode.HIDDEN,
                    onBack = { navController.popBackStack() },
                )
            }

            composable(
                route = Routes.APP_DETAIL,
                arguments = Routes.appDetailArgs,
            ) { entry ->
                val uid = entry.arguments?.getInt("uid") ?: -1
                val pkg = entry.arguments?.getString("pkg").orEmpty()
                val periodId = entry.arguments?.getString("periodId") ?: UsagePeriod.Today.id
                val start = entry.arguments?.getLong("periodStart") ?: 0L
                val end = entry.arguments?.getLong("periodEnd") ?: 0L
                AppDetailScreen(
                    uid = uid,
                    packageName = pkg,
                    period = Routes.periodFromArgs(periodId, start, end),
                    onBack = { navController.popBackStack() },
                )
            }
        }
    }
}

private fun NavHostController.navigateToTab(route: String) {
    navigate(route) {
        popUpTo(graph.findStartDestination().id) { saveState = true }
        launchSingleTop = true
        restoreState = true
    }
}
