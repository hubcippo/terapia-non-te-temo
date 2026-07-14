package com.carletto.terapianontetemo.domain

import com.carletto.terapianontetemo.data.entity.DoseEvent
import kotlin.math.abs

/**
 * Regola del cambio dose — LOGICA PURA (CONTRACT sez. 14), estratta da
 * SuonoAllarmeService.caricaVociAllarme perche' viva in UN posto solo.
 *
 * Il riferimento e' l'evento di IERI dello stesso farmaco piu' vicino a
 * dataOraMillis - 24h: cosi' nelle titolazioni la dose della mattina si
 * confronta con quella di ieri mattina, non con quella di ieri sera.
 * C'e' cambio se la dose del riferimento e' diversa da quella di oggi.
 */
object CambioDose {

    private const val GIORNO_MILLIS = 24 * 60 * 60 * 1000L

    /** Evento di ieri piu' vicino alla stessa ora, null se ieri non c'era nulla. */
    fun riferimentoIeri(dose: DoseEvent, dosiIeriStessoFarmaco: List<DoseEvent>): DoseEvent? =
        dosiIeriStessoFarmaco.minByOrNull {
            abs(it.dataOraMillis - (dose.dataOraMillis - GIORNO_MILLIS))
        }

    /** True se ieri, alla stessa ora, la dose era diversa. */
    fun eCambio(dose: DoseEvent, dosiIeriStessoFarmaco: List<DoseEvent>): Boolean {
        val riferimento = riferimentoIeri(dose, dosiIeriStessoFarmaco) ?: return false
        return riferimento.dose != dose.dose
    }
}
