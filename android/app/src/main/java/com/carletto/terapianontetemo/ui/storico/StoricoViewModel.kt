package com.carletto.terapianontetemo.ui.storico

import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.viewModelScope
import com.carletto.terapianontetemo.TerapiaApp
import com.carletto.terapianontetemo.data.entity.DoseEvent
import com.carletto.terapianontetemo.domain.Aderenza
import java.time.Instant
import java.time.LocalDate
import java.time.ZoneId
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.shareIn
import kotlinx.coroutines.flow.stateIn

private fun giornoDi(millis: Long): LocalDate =
    Instant.ofEpochMilli(millis).atZone(ZoneId.systemDefault()).toLocalDate()

/**
 * Stato della schermata Storico: dosi raggruppate per giorno (più recenti
 * prima; il raggruppamento avviene QUI, una volta per emissione, non a
 * ogni ricomposizione), nomi dei farmaci e aderenza complessiva/per
 * farmaco (CONTRACT sez. 14).
 */
data class StoricoUiState(
    val dosiPerGiorno: Map<LocalDate, List<DoseEvent>> = emptyMap(),
    val nomiFarmaci: Map<Long, String> = emptyMap(),
    val complessiva: Aderenza.Riepilogo = Aderenza.Riepilogo(0, 0),
    val perFarmaco: Map<Long, Aderenza.Riepilogo> = emptyMap()
)

class StoricoViewModel(
    app: TerapiaApp
) : ViewModel() {

    private val repository = app.repository

    private val dosi = repository.tutteLeDosi()
        .shareIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), replay = 1)

    /** Nomi dei farmaci dello storico (una sola query IN, niente N+1). */
    private val nomiFarmaci = repository.nomiFarmaciOsservati(
        dosi.map { lista -> lista.map { it.farmacoId }.toSet() }
    )

    val uiState: StateFlow<StoricoUiState> =
        combine(dosi, nomiFarmaci) { lista, nomi ->
            // Aderenza SOLO sulle dosi passate: le future in ATTESA non contano.
            val passate = lista.filter { it.dataOraMillis < System.currentTimeMillis() }
            StoricoUiState(
                // La lista arriva ordinata DESC: groupBy (LinkedHashMap)
                // preserva l'ordine, giorno più recente per primo.
                dosiPerGiorno = lista.groupBy { giornoDi(it.dataOraMillis) },
                nomiFarmaci = nomi,
                complessiva = Aderenza.complessiva(passate),
                perFarmaco = Aderenza.perFarmaco(passate)
            )
        }
            .stateIn(
                scope = viewModelScope,
                started = SharingStarted.WhileSubscribed(5_000),
                initialValue = StoricoUiState()
            )

    /** Factory che passa [TerapiaApp] al ViewModel. */
    class Factory(private val app: TerapiaApp) : ViewModelProvider.Factory {
        override fun <T : ViewModel> create(modelClass: Class<T>): T {
            require(modelClass.isAssignableFrom(StoricoViewModel::class.java)) {
                "Classe ViewModel sconosciuta: ${modelClass.name}"
            }
            @Suppress("UNCHECKED_CAST")
            return StoricoViewModel(app) as T
        }
    }
}
