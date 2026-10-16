package com.soa.stocksecurity.ui.navigation

import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.slideInHorizontally
import androidx.compose.animation.slideOutHorizontally
import androidx.compose.runtime.Composable
import androidx.lifecycle.viewmodel.compose.viewModel
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import androidx.navigation.compose.rememberNavController
import com.soa.stocksecurity.ui.DeviceViewModel
import com.soa.stocksecurity.ui.screens.MainScreen
import com.soa.stocksecurity.ui.screens.SecurityScreen
import com.soa.stocksecurity.ui.screens.StockScreen

object Routes {
    const val MAIN = "main"
    const val STOCK = "stock"
    const val SECURITY = "security"
}

@Composable
fun AppNavigation() {
    val navController = rememberNavController()
    // ViewModel único compartido por las tres pantallas (scope de la Activity host).
    val viewModel: DeviceViewModel = viewModel()

    NavHost(
        navController = navController,
        startDestination = Routes.MAIN,
        enterTransition = { slideInHorizontally(tween(280)) { it / 4 } + fadeIn(tween(280)) },
        exitTransition = { fadeOut(tween(200)) },
        popEnterTransition = { fadeIn(tween(280)) },
        popExitTransition = { slideOutHorizontally(tween(280)) { it / 4 } + fadeOut(tween(200)) },
    ) {
        composable(Routes.MAIN) {
            MainScreen(
                viewModel = viewModel,
                onOpenStock = { navController.navigate(Routes.STOCK) },
                onOpenSecurity = { navController.navigate(Routes.SECURITY) },
            )
        }
        composable(Routes.STOCK) {
            StockScreen(
                viewModel = viewModel,
                onBack = { navController.popBackStack() },
            )
        }
        composable(Routes.SECURITY) {
            SecurityScreen(
                viewModel = viewModel,
                onBack = { navController.popBackStack() },
            )
        }
    }
}
