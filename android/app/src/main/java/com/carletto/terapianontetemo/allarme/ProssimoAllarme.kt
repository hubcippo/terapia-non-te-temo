package com.carletto.terapianontetemo.allarme

import com.carletto.terapianontetemo.data.entity.DoseEvent
import com.carletto.terapianontetemo.data.entity.StatoDose

/**
 * Logica PURA di pianificazione degli allarmi (CONTRACT sez. 11).
 * Nessun import Android: testabile su JVM.
 *
 * Una "fascia" e' un valore esatto di dataOraMillis: tutte le dosi
 * con lo stesso orario suonano insieme con un solo allarme.
 */
object ProssimoAllarme {

    /** Dopo 2 ore dall'orario previsto una dose in ATTESA diventa SALTATO. */
    const val SCADENZA_MILLIS = 2 * 60 * 60 * 1000L

    /** Id delle dosi ATTESA con dataOra <= adesso - 2h (da marcare SALTATO). */
    fun scadute(dosi: List<DoseEvent>, adessoMillis: Long): List<Long> =
        dosi.filter {
            it.stato == StatoDose.ATTESA &&
                it.dataOraMillis <= adessoMillis - SCADENZA_MILLIS
        }.map { it.id }

    /**
     * Millis della prossima fascia: la MIN dataOraMillis tra le dosi ATTESA
     * non scadute. Puo' essere nel passato recente (< 2h): in quel caso
     * l'allarme scatta subito, ed e' voluto. null se non c'e' nulla.
     */
    fun prossimaFascia(dosi: List<DoseEvent>, adessoMillis: Long): Long? =
        dosi.filter {
            it.stato == StatoDose.ATTESA &&
                it.dataOraMillis > adessoMillis - SCADENZA_MILLIS
        }.minOfOrNull { it.dataOraMillis }
}
