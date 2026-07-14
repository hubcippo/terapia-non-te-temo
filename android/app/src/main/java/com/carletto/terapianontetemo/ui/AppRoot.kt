package com.carletto.terapianontetemo.ui

import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.platform.LocalContext
import androidx.lifecycle.viewmodel.compose.viewModel
import androidx.navigation.NavType
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import androidx.navigation.compose.rememberNavController
import androidx.navigation.navArgument
import com.carletto.terapianontetemo.TerapiaApp
import com.carletto.terapianontetemo.allarme.AlarmScheduler
import com.carletto.terapianontetemo.ui.aggiungi.AggiungiScreen
import com.carletto.terapianontetemo.ui.aggiungi.AggiungiViewModel
import com.carletto.terapianontetemo.ui.home.HomeScreen
import com.carletto.terapianontetemo.ui.home.HomeViewModel
import com.carletto.terapianontetemo.ui.onboarding.OnboardingScreen
import com.carletto.terapianontetemo.ui.storico.StoricoScreen
import com.carletto.terapianontetemo.ui.storico.StoricoViewModel
import com.carletto.terapianontetemo.ui.terapie.DettaglioTerapiaScreen
import com.carletto.terapianontetemo.ui.terapie.TerapieScreen
import com.carletto.terapianontetemo.ui.terapie.TerapieViewModel
import com.carletto.terapianontetemo.util.Preferenze

/**
 * Radice della navigazione dell'app: Onboarding (solo al primo avvio),
 * Home, Aggiungi da foto, Storico, Terapie e Dettaglio terapia
 * (CONTRACT sez. 14). La schermata di Conferma resta uno stato interno
 * di AggiungiScreen (il piano non è serializzabile in nav args).
 */
@Composable
fun AppRoot() {
    val navController = rememberNavController()
    val app = LocalContext.current.applicationContext as TerapiaApp

    // Valutato una sola volta: dopo la guida si naviga esplicitamente a home.
    val partenza = remember {
        if (Preferenze.onboardingFatto(app)) "home" else "onboarding"
    }

    NavHost(
        navController = navController,
        startDestination = partenza
    ) {
        composable("home") {
            val homeViewModel: HomeViewModel = viewModel(
                factory = HomeViewModel.Factory(app)
            )
            HomeScreen(
                viewModel = homeViewModel,
                onAggiungi = { navController.navigate("aggiungi") },
                onProvaAllarme = { AlarmScheduler.programmaProva(app) },
                onStorico = { navController.navigate("storico") },
                onTerapie = { navController.navigate("terapie") },
                onGuida = { navController.navigate("onboarding") }
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
        composable("onboarding") {
            OnboardingScreen(
                onFine = {
                    navController.navigate("home") {
                        popUpTo("onboarding") { inclusive = true }
                        launchSingleTop = true
                    }
                }
            )
        }
        composable("storico") {
            val storicoViewModel: StoricoViewModel = viewModel(
                factory = StoricoViewModel.Factory(app)
            )
            StoricoScreen(
                viewModel = storicoViewModel,
                onIndietro = { navController.popBackStack() }
            )
        }
        composable("terapie") {
            val terapieViewModel: TerapieViewModel = viewModel(
                factory = TerapieViewModel.Factory(app)
            )
            TerapieScreen(
                viewModel = terapieViewModel,
                onDettaglio = { farmacoId ->
                    navController.navigate("terapia/$farmacoId")
                },
                onIndietro = { navController.popBackStack() }
            )
        }
        composable(
            route = "terapia/{farmacoId}",
            arguments = listOf(navArgument("farmacoId") { type = NavType.LongType })
        ) { backStackEntry ->
            val farmacoId = backStackEntry.arguments?.getLong("farmacoId") ?: 0L
            val terapieViewModel: TerapieViewModel = viewModel(
                factory = TerapieViewModel.Factory(app)
            )
            DettaglioTerapiaScreen(
                viewModel = terapieViewModel,
                farmacoId = farmacoId,
                onIndietro = { navController.popBackStack() }
            )
        }
    }
}
