package com.carletto.terapianontetemo.ui.aggiungi

import android.content.Context
import android.net.Uri
import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.viewModelScope
import com.carletto.terapianontetemo.TerapiaApp
import com.carletto.terapianontetemo.ai.KeyStore
import com.carletto.terapianontetemo.allarme.AlarmScheduler
import com.carletto.terapianontetemo.ai.OpenAiVisionClient
import com.carletto.terapianontetemo.domain.EstrazioneParser
import com.carletto.terapianontetemo.domain.Srotolatore
import com.carletto.terapianontetemo.util.ImmagineIlleggibileException
import com.carletto.terapianontetemo.util.ImmaginePerVision
import java.io.IOException
import java.time.LocalDate
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import kotlinx.serialization.SerializationException

/** Stati della schermata "Aggiungi da foto" (CONTRACT sez. 9). */
sealed interface AggiungiStato {
    data object Idle : AggiungiStato
    data object ServeChiave : AggiungiStato
    data object InCorso : AggiungiStato
    data class Errore(val messaggio: String) : AggiungiStato
    data class Conferma(val piano: PianoEditabile, val fotoUri: Uri?) : AggiungiStato
}

/**
 * ViewModel del flusso foto -> estrazione -> Conferma.
 * Il piano entra in Room SOLO da [conferma] (nessuna auto-attivazione).
 */
class AggiungiViewModel(private val app: TerapiaApp) : ViewModel() {

    private val _stato = MutableStateFlow<AggiungiStato>(AggiungiStato.Idle)
    val stato: StateFlow<AggiungiStato> = _stato.asStateFlow()

    /** Ultima foto scelta: per ritentare dopo il salvataggio della chiave. */
    private var ultimaUri: Uri? = null

    /**
     * Avvia l'estrazione dalla foto: controlla la chiave API, prepara
     * l'immagine, chiama il modello vision e fa il parse del JSON.
     */
    fun avvia(context: Context, uri: Uri) {
        val appContext = context.applicationContext
        ultimaUri = uri

        val chiave = KeyStore.getApiKey(appContext)
        if (chiave.isNullOrBlank()) {
            _stato.value = AggiungiStato.ServeChiave
            return
        }

        _stato.value = AggiungiStato.InCorso
        viewModelScope.launch {
            try {
                val piano = withContext(Dispatchers.IO) {
                    val immagine = ImmaginePerVision.prepara(appContext, uri)
                    val grezzo = OpenAiVisionClient(chiave).estraiPiano(immagine)
                    EstrazioneParser.parse(grezzo)
                }
                if (!piano.eRicetta) {
                    _stato.value = AggiungiStato.Errore(
                        "Questa foto non sembra una prescrizione medica. " +
                            "Riprova con la ricetta del medico."
                    )
                } else {
                    _stato.value = AggiungiStato.Conferma(daEstratto(piano), uri)
                }
            } catch (e: CancellationException) {
                throw e
            } catch (e: SerializationException) {
                _stato.value = AggiungiStato.Errore(
                    "Non sono riuscito a capire la risposta del servizio. Riprova."
                )
            } catch (e: ImmagineIlleggibileException) {
                // Il messaggio è già in italiano e pensato per la UI.
                _stato.value = AggiungiStato.Errore(
                    e.message ?: "Non riesco a leggere la foto. Riprova."
                )
            } catch (e: IOException) {
                _stato.value = AggiungiStato.Errore(messaggioPerIo(e))
            } catch (e: Exception) {
                _stato.value = AggiungiStato.Errore(
                    "Qualcosa è andato storto. Riprova tra poco."
                )
            }
        }
    }

    /** Salva la chiave API in modo sicuro e ritenta l'estrazione sull'ultima foto. */
    fun salvaChiave(context: Context, chiave: String) {
        val appContext = context.applicationContext
        KeyStore.setApiKey(appContext, chiave.trim())
        val uri = ultimaUri
        if (uri != null) {
            avvia(appContext, uri)
        } else {
            _stato.value = AggiungiStato.Idle
        }
    }

    /** Aggiorna il piano in stato Conferma (campi editati, terapie escluse/ripristinate). */
    fun aggiorna(piano: PianoEditabile) {
        val attuale = _stato.value
        if (attuale is AggiungiStato.Conferma) {
            _stato.value = attuale.copy(piano = piano)
        }
    }

    /** Srotola il piano confermato in Room. Da chiamare solo dal bottone Conferma. */
    suspend fun conferma() {
        val attuale = _stato.value as? AggiungiStato.Conferma ?: return
        if (!attuale.piano.confermabile) return
        Srotolatore.applica(
            piano = attuale.piano.versoEstratto(),
            inizio = LocalDate.now(),
            repo = app.repository,
            db = app.database
        )
        // Le nuove dosi sono in Room: pianifica subito il prossimo allarme.
        AlarmScheduler.ripianifica(app)
    }

    /** Torna allo stato iniziale (scelta della foto). */
    fun reset() {
        ultimaUri = null
        _stato.value = AggiungiStato.Idle
    }

    /**
     * Messaggio italiano per gli errori di I/O: distingue la chiave rifiutata
     * (401 / invalid_api_key nel messaggio di OpenAiVisionClient, formato nostro
     * e stabile) dagli errori generici di rete. Mai loggare la chiave.
     */
    private fun messaggioPerIo(e: IOException): String {
        val dettaglio = e.message.orEmpty()
        return if (dettaglio.contains("401") || dettaglio.contains("invalid_api_key")) {
            "Chiave non valida: ricontrolla e incolla di nuovo."
        } else {
            "Errore di rete. Controlla la connessione e riprova."
        }
    }

    /** Factory che prende TerapiaApp, come HomeViewModel.Factory. */
    class Factory(private val app: TerapiaApp) : ViewModelProvider.Factory {
        override fun <T : ViewModel> create(modelClass: Class<T>): T {
            require(modelClass.isAssignableFrom(AggiungiViewModel::class.java)) {
                "Classe ViewModel sconosciuta: ${modelClass.name}"
            }
            @Suppress("UNCHECKED_CAST")
            return AggiungiViewModel(app) as T
        }
    }
}
