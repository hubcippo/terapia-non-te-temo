package com.carletto.terapianontetemo.domain

import com.carletto.terapianontetemo.data.entity.Fascia
import java.time.LocalTime

/** Normalizzazione fasce e intervalli sugli orari fissi 07/13/15/19/23 (CONTRACT sez. 6). */
object OrariFissi {

    val MATTINA: LocalTime = LocalTime.of(7, 0)
    val PRANZO: LocalTime = LocalTime.of(13, 0)
    val POMERIGGIO: LocalTime = LocalTime.of(15, 0)
    val SERA: LocalTime = LocalTime.of(19, 0)
    val NOTTE: LocalTime = LocalTime.of(23, 0)

    /** Le 4 fasce default 07/15/19/23. */
    val DEFAULT: List<LocalTime> = listOf(MATTINA, POMERIGGIO, SERA, NOTTE)

    /** Soglia di dominio mattina/pomeriggio (titolazione doseMattina/doseSera). */
    fun eMattina(orario: LocalTime): Boolean = orario.hour < 12

    /** Fascia -> orario fisso: MATTINA 07:00, PRANZO 13:00, POMERIGGIO 15:00, SERA 19:00, NOTTE 23:00. */
    fun orario(fascia: Fascia): LocalTime = when (fascia) {
        Fascia.MATTINA -> MATTINA
        Fascia.PRANZO -> PRANZO
        Fascia.POMERIGGIO -> POMERIGGIO
        Fascia.SERA -> SERA
        Fascia.NOTTE -> NOTTE
    }

    /**
     * intervalloOre -> orari fissi del giorno:
     * 24h -> [07]; 12h -> [07,19]; 8h -> [07,15,23]; 6h -> [07,13,19,23];
     * altrimenti distribuzione sulle 4 fasce default 07/15/19/23.
     */
    fun orariPerIntervallo(intervalloOre: Int?): List<LocalTime> = when (intervalloOre) {
        24 -> listOf(MATTINA)
        12 -> listOf(MATTINA, SERA)
        8 -> listOf(MATTINA, POMERIGGIO, NOTTE)
        6 -> listOf(MATTINA, PRANZO, SERA, NOTTE)
        else -> DEFAULT
    }
}
