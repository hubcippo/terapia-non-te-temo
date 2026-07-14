package com.carletto.terapianontetemo.ui

import androidx.compose.runtime.Composable
import androidx.compose.ui.platform.LocalContext
import androidx.lifecycle.viewmodel.compose.viewModel
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import androidx.navigation.compose.rememberNavController
import com.carletto.terapianontetemo.TerapiaApp
import com.carletto.terapianontetemo.ui.home.HomeScreen
import com.carletto.terapianontetemo.ui.home.HomeViewModel

/**
 * Radice della navigazione dell'app. In Fase B esiste solo la Home.
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
            HomeScreen(viewModel = homeViewModel)
        }
    }
}
