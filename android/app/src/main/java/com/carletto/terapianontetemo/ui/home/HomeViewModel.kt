package com.carletto.terapianontetemo.ui.home

import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.viewModelScope
import com.carletto.terapianontetemo.TerapiaApp
import com.carletto.terapianontetemo.data.TerapiaRepository
import com.carletto.terapianontetemo.data.entity.DoseEvent
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.flatMapLatest
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch

/**
 * Stato della schermata Home: prossima dose, dosi di oggi e mappa
 * farmacoId -> nome per disaccoppiare la UI dal database.
 */
data class HomeUiState(
    val prossima: DoseEvent? = null,
    val oggi: List<DoseEvent> = emptyList(),
    val nomiFarmaci: Map<Long, String> = emptyMap()
)

@OptIn(ExperimentalCoroutinesApi::class)
class HomeViewModel(
    private val repository: TerapiaRepository
) : ViewModel() {

    // Riferimento temporale fisso: quando una dose viene segnata PRESA, Room
    // ri-emette da solo e la query (stato = ATTESA) fa emergere la successiva.
    private val avvioMillis = System.currentTimeMillis()

    val uiState: StateFlow<HomeUiState> =
        combine(
            repository.prossimaDose(avvioMillis),
            repository.doseDiOggi()
        ) { prossima, oggi -> prossima to oggi }
            .flatMapLatest { (prossima, oggi) ->
                val farmacoIds = buildSet {
                    prossima?.let { add(it.farmacoId) }
                    oggi.forEach { add(it.farmacoId) }
                }
                if (farmacoIds.isEmpty()) {
                    flowOf(HomeUiState(prossima = prossima, oggi = oggi))
                } else {
                    // Una sola query IN per tutti i nomi (niente N+1).
                    repository.farmaci(farmacoIds.toList()).map { farmaci ->
                        HomeUiState(
                            prossima = prossima,
                            oggi = oggi,
                            nomiFarmaci = farmaci.associate { it.id to it.nome }
                        )
                    }
                }
            }
            .stateIn(
                scope = viewModelScope,
                started = SharingStarted.WhileSubscribed(5_000),
                initialValue = HomeUiState()
            )

    /** Segna la dose come presa adesso. */
    fun segnaFatto(doseId: Long) {
        viewModelScope.launch {
            repository.segnaFatto(doseId, System.currentTimeMillis())
        }
    }

    /** Factory che prende il repository da [TerapiaApp]. */
    class Factory(private val app: TerapiaApp) : ViewModelProvider.Factory {
        override fun <T : ViewModel> create(modelClass: Class<T>): T {
            require(modelClass.isAssignableFrom(HomeViewModel::class.java)) {
                "Classe ViewModel sconosciuta: ${modelClass.name}"
            }
            @Suppress("UNCHECKED_CAST")
            return HomeViewModel(app.repository) as T
        }
    }
}
