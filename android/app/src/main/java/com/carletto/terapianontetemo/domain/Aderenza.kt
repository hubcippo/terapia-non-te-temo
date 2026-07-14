package com.carletto.terapianontetemo.domain

import com.carletto.terapianontetemo.data.entity.DoseEvent
import com.carletto.terapianontetemo.data.entity.StatoDose
import kotlin.math.roundToInt

/**
 * Aderenza alla terapia — LOGICA PURA (CONTRACT sez. 14).
 * Conta SOLO le dosi PRESO e SALTATO: ATTESA e RIMANDATO non dicono
 * nulla sull'aderenza e vengono ignorate.
 */
object Aderenza {

    data class Riepilogo(val prese: Int, val saltate: Int) {
        val totale get() = prese + saltate

        /** Percentuale intera 0..100 di dosi prese, null se totale == 0. */
        val percento: Int?
            get() = if (totale == 0) null else (prese * 100.0 / totale).roundToInt()
    }

    /** Riepilogo complessivo su tutte le dosi passate. */
    fun complessiva(dosi: List<DoseEvent>): Riepilogo {
        var prese = 0
        var saltate = 0
        dosi.forEach { dose ->
            when (dose.stato) {
                StatoDose.PRESO -> prese++
                StatoDose.SALTATO -> saltate++
                StatoDose.ATTESA, StatoDose.RIMANDATO -> Unit
            }
        }
        return Riepilogo(prese, saltate)
    }

    /** Riepilogo per ogni farmaco (chiave = farmacoId). */
    fun perFarmaco(dosi: List<DoseEvent>): Map<Long, Riepilogo> =
        dosi.groupBy { it.farmacoId }
            .mapValues { (_, dosiFarmaco) -> complessiva(dosiFarmaco) }
}
