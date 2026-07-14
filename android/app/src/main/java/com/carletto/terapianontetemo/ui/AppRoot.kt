package com.carletto.terapianontetemo.ui

import androidx.compose.runtime.Composable
import androidx.compose.ui.platform.LocalContext
import androidx.lifecycle.viewmodel.compose.viewModel
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import androidx.navigation.compose.rememberNavController
import com.carletto.terapianontetemo.TerapiaApp
import com.carletto.terapianontetemo.ui.aggiungi.AggiungiScreen
import com.carletto.terapianontetemo.ui.aggiungi.AggiungiViewModel
import com.carletto.terapianontetemo.ui.home.HomeScreen
import com.carletto.terapianontetemo.ui.home.HomeViewModel

/**
 * Radice della navigazione dell'app: Home + Aggiungi da foto.
 * La schermata di Conferma è uno stato interno di AggiungiScreen
 * (il piano non è serializzabile in nav args, CONTRACT sez. 9).
 */
@Composable
fun AppRoot() {
    val navController = rememberNavController()
    val app = LocalContext.current.applicationContext as TerapiaApp

    NavHost(
        navController = navController,
        startDestination = "home"
    ) {
        composable("home") {
            val homeViewModel: HomeViewModel = viewModel(
                factory = HomeViewModel.Factory(app)
            )
            HomeScreen(
                viewModel = homeViewModel,
                onAggiungi = { navController.navigate("aggiungi") }
            )
        }
        composable("aggiungi") {
            val aggiungiViewModel: AggiungiViewModel = viewModel(
                factory = AggiungiViewModel.Factory(app)
            )
            AggiungiScreen(
                viewModel = aggiungiViewModel,
                onConfermato = {
                    navController.navigate("home") {
                        popUpTo("home") { inclusive = false }
                        launchSingleTop = true
                    }
                }
            )
        }
    }
}
