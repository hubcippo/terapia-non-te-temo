package com.carletto.terapianontetemo.ui.home

import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.viewModelScope
import com.carletto.terapianontetemo.TerapiaApp
import com.carletto.terapianontetemo.allarme.AlarmScheduler
import com.carletto.terapianontetemo.data.entity.DoseEvent
import com.carletto.terapianontetemo.domain.CambioDose
import java.time.LocalDate
import java.time.ZoneId
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.shareIn
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch

/**
 * Stato della schermata Home: prossima dose, dosi di oggi e mappa
 * farmacoId -> nome per disaccoppiare la UI dal database.
 */
data class HomeUiState(
    val prossima: DoseEvent? = null,
    val oggi: List<DoseEvent> = emptyList(),
    val nomiFarmaci: Map<Long, String> = emptyMap(),
    /** Fase E: nomi dei farmaci la cui dose oggi cambia rispetto a ieri (badge scalare). */
    val farmaciCambioOggi: List<String> = emptyList()
)

class HomeViewModel(
    private val app: TerapiaApp
) : ViewModel() {

    private val repository = app.repository

    // Riferimento temporale fisso: quando una dose viene segnata PRESA, Room
    // ri-emette da solo e la query (stato = ATTESA) fa emergere la successiva.
    private val avvioMillis = System.currentTimeMillis()

    /** Coppia (prossima, dosi di oggi) condivisa tra stato UI e query dei nomi. */
    private val dosi = combine(
        repository.prossimaDose(avvioMillis),
        repository.doseDiOggi()
    ) { prossima, oggi -> prossima to oggi }
        .shareIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), replay = 1)

    /** Nomi dei farmaci delle dosi mostrate (una sola query IN, niente N+1). */
    private val nomiFarmaci = repository.nomiFarmaciOsservati(
        dosi.map { (prossima, oggi) ->
            buildSet {
                prossima?.let { add(it.farmacoId) }
                oggi.forEach { add(it.farmacoId) }
            }
        }
    )

    /**
     * Fase E: id dei farmaci la cui PRIMA dose di oggi e' diversa da ieri.
     * Le dosi di ieri arrivano con UNA query one-shot IN (niente N+1) e il
     * ricalcolo scatta solo se cambiano farmaci/orari/dosi di oggi, non
     * quando cambia soltanto lo stato (Fatto).
     */
    private val farmaciCambioOggi = dosi
        // doseDiOggi e' ordinata per ora: distinctBy tiene la prima dose per farmaco.
        .map { (_, oggi) -> oggi.distinctBy { it.farmacoId } }
        .distinctUntilChanged { vecchie, nuove ->
            vecchie.map { Triple(it.farmacoId, it.dataOraMillis, it.dose) } ==
                nuove.map { Triple(it.farmacoId, it.dataOraMillis, it.dose) }
        }
        .map { prime ->
            if (prime.isEmpty()) return@map emptyList<Long>()
            val zona = ZoneId.systemDefault()
            val oggiData = LocalDate.now(zona)
            val oggi00 = oggiData.atStartOfDay(zona).toInstant().toEpochMilli()
            val ieri00 = oggiData.minusDays(1).atStartOfDay(zona).toInstant().toEpochMilli()
            val ieriPerFarmaco = repository
                .dosiIeriDeiFarmaci(prime.map { it.farmacoId }, ieri00, oggi00)
                .groupBy { it.farmacoId }
            prime
                .filter { CambioDose.eCambio(it, ieriPerFarmaco[it.farmacoId].orEmpty()) }
                .map { it.farmacoId }
        }

    val uiState: StateFlow<HomeUiState> =
        combine(dosi, nomiFarmaci, farmaciCambioOggi) { (prossima, oggi), nomi, cambioIds ->
            HomeUiState(
                prossima = prossima,
                oggi = oggi,
                nomiFarmaci = nomi,
                farmaciCambioOggi = cambioIds.mapNotNull { nomi[it] }
            )
        }
            .stateIn(
                scope = viewModelScope,
                started = SharingStarted.WhileSubscribed(5_000),
                initialValue = HomeUiState()
            )

    /** Segna la dose come presa adesso e ripianifica il prossimo allarme. */
    fun segnaFatto(doseId: Long) {
        viewModelScope.launch {
            repository.segnaFatto(doseId, System.currentTimeMillis())
            AlarmScheduler.ripianifica(app)
        }
    }

    /** Factory che passa [TerapiaApp] al ViewModel. */
    class Factory(private val app: TerapiaApp) : ViewModelProvider.Factory {
        override fun <T : ViewModel> create(modelClass: Class<T>): T {
            require(modelClass.isAssignableFrom(HomeViewModel::class.java)) {
                "Classe ViewModel sconosciuta: ${modelClass.name}"
            }
            @Suppress("UNCHECKED_CAST")
            return HomeViewModel(app) as T
        }
    }
}
