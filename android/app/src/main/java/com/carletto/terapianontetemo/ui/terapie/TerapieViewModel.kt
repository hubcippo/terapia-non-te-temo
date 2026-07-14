package com.carletto.terapianontetemo.ui.terapie

import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.viewModelScope
import com.carletto.terapianontetemo.TerapiaApp
import com.carletto.terapianontetemo.allarme.AlarmScheduler
import com.carletto.terapianontetemo.data.entity.Farmaco
import com.carletto.terapianontetemo.data.entity.Fase
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.stateIn

/**
 * ViewModel delle terapie attive (CONTRACT sez. 14): lista dei farmaci
 * con dosi in ATTESA, fasi per il dettaglio e Ferma terapia.
 */
class TerapieViewModel(
    private val app: TerapiaApp
) : ViewModel() {

    private val repository = app.repository

    /** Terapie attive = farmaci con almeno una dose in ATTESA. */
    val terapie: StateFlow<List<Farmaco>> = repository.terapieAttive()
        .stateIn(
            scope = viewModelScope,
            started = SharingStarted.WhileSubscribed(5_000),
            initialValue = emptyList()
        )

    /** Fasi del farmaco per la schermata di dettaglio. */
    fun fasi(farmacoId: Long): Flow<List<Fase>> = repository.fasiDiFarmaco(farmacoId)

    /**
     * Ferma la terapia: cancella SOLO le dosi in ATTESA (lo storico resta)
     * e ripianifica l'allarme sulla prossima fascia rimasta.
     */
    suspend fun ferma(farmacoId: Long) {
        repository.fermaTerapia(farmacoId)
        AlarmScheduler.ripianifica(app)
    }

    /** Factory che passa [TerapiaApp] al ViewModel. */
    class Factory(private val app: TerapiaApp) : ViewModelProvider.Factory {
        override fun <T : ViewModel> create(modelClass: Class<T>): T {
            require(modelClass.isAssignableFrom(TerapieViewModel::class.java)) {
                "Classe ViewModel sconosciuta: ${modelClass.name}"
            }
            @Suppress("UNCHECKED_CAST")
            return TerapieViewModel(app) as T
        }
    }
}
