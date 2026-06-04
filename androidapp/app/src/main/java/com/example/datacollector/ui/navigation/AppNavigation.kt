package com.example.datacollector.ui.navigation

import androidx.compose.runtime.Composable
import androidx.navigation.NavType
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import androidx.navigation.compose.rememberNavController
import androidx.navigation.navArgument
import com.example.datacollector.ui.screen.ChartScreen
import com.example.datacollector.ui.screen.ConfigScreen
import com.example.datacollector.ui.screen.DataScreen
import com.example.datacollector.ui.screen.DeviceScreen
import com.example.datacollector.ui.screen.ExportScreen
import com.example.datacollector.ui.screen.ScanScreen

object Routes {
    const val SCAN = "scan"
    const val DEVICE = "device/{macAddress}"
    const val CONFIG = "config/{macAddress}"
    const val DATA = "data/{macAddress}"
    const val CHART = "chart/{macAddress}"
    const val EXPORT = "export/{macAddress}"

    fun device(mac: String) = "device/$mac"
    fun config(mac: String) = "config/$mac"
    fun data(mac: String) = "data/$mac"
    fun chart(mac: String) = "chart/$mac"
    fun export(mac: String) = "export/$mac"
}

@Composable
fun AppNavigation() {
    val navController = rememberNavController()

    NavHost(navController = navController, startDestination = Routes.SCAN) {
        composable(Routes.SCAN) {
            ScanScreen(
                onDeviceClick = { mac -> navController.navigate(Routes.device(mac)) }
            )
        }

        composable(
            Routes.DEVICE,
            arguments = listOf(navArgument("macAddress") { type = NavType.StringType })
        ) { backStackEntry ->
            val mac = backStackEntry.arguments?.getString("macAddress") ?: return@composable
            DeviceScreen(
                macAddress = mac,
                onConfigClick = { navController.navigate(Routes.config(mac)) },
                onDataClick = { navController.navigate(Routes.data(mac)) },
                onDisconnect = { navController.popBackStack(Routes.SCAN, false) }
            )
        }

        composable(
            Routes.CONFIG,
            arguments = listOf(navArgument("macAddress") { type = NavType.StringType })
        ) { backStackEntry ->
            val mac = backStackEntry.arguments?.getString("macAddress") ?: return@composable
            ConfigScreen(macAddress = mac, onBack = { navController.popBackStack() })
        }

        composable(
            Routes.DATA,
            arguments = listOf(navArgument("macAddress") { type = NavType.StringType })
        ) { backStackEntry ->
            val mac = backStackEntry.arguments?.getString("macAddress") ?: return@composable
            DataScreen(
                macAddress = mac,
                onChartClick = { navController.navigate(Routes.chart(mac)) },
                onExportClick = { navController.navigate(Routes.export(mac)) },
                onBack = { navController.popBackStack() }
            )
        }

        composable(
            Routes.CHART,
            arguments = listOf(navArgument("macAddress") { type = NavType.StringType })
        ) { backStackEntry ->
            val mac = backStackEntry.arguments?.getString("macAddress") ?: return@composable
            ChartScreen(macAddress = mac, onBack = { navController.popBackStack() })
        }

        composable(
            Routes.EXPORT,
            arguments = listOf(navArgument("macAddress") { type = NavType.StringType })
        ) { backStackEntry ->
            val mac = backStackEntry.arguments?.getString("macAddress") ?: return@composable
            ExportScreen(macAddress = mac, onBack = { navController.popBackStack() })
        }
    }
}
