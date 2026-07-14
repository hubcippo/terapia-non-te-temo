package com.carletto.terapianontetemo.domain

import com.carletto.terapianontetemo.data.entity.DoseEvent
import com.carletto.terapianontetemo.data.entity.Forma
import com.carletto.terapianontetemo.data.entity.StatoDose
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

/** Test JVM della regola del cambio dose (CONTRACT sez. 14). */
class CambioDoseTest {

    private val oraMillis = 60 * 60 * 1000L
    private val giornoMillis = 24 * oraMillis

    // Oggi alle 07:00 (millis arbitrari ma coerenti).
    private val oggiMattina = 1_750_000_000_000L
    private val oggiSera = oggiMattina + 12 * oraMillis
    private val ieriMattina = oggiMattina - giornoMillis
    private val ieriSera = oggiSera - giornoMillis

    private fun dose(
        ora: Long,
        doseTesto: String,
        farmacoId: Long = 1L
    ) = DoseEvent(
        id = 0,
        farmacoId = farmacoId,
        dataOraMillis = ora,
        dose = doseTesto,
        forma = Forma.COMPRESSA,
        stato = StatoDose.PRESO
    )

    @Test
    fun ieriStessaDoseNessunCambio() {
        val oggi = dose(oggiMattina, "1 compressa")
        val ieri = listOf(dose(ieriMattina, "1 compressa"))
        assertFalse(CambioDose.eCambio(oggi, ieri))
    }

    @Test
    fun ieriDoseDiversaCambio() {
        val oggi = dose(oggiMattina, "2 compresse")
        val ieri = listOf(dose(ieriMattina, "1 compressa"))
        assertTrue(CambioDose.eCambio(oggi, ieri))
    }

    @Test
    fun nessunEventoIeriNessunCambio() {
        val oggi = dose(oggiMattina, "1 compressa")
        assertFalse(CambioDose.eCambio(oggi, emptyList()))
        assertNull(CambioDose.riferimentoIeri(oggi, emptyList()))
    }

    @Test
    fun titolazioneMattinaConfrontaMattina() {
        // Ieri: mattina "50 mg", sera "100 mg". Oggi mattina sempre "50 mg":
        // il riferimento e' l'evento piu' vicino a -24h (ieri MATTINA),
        // quindi nessun cambio anche se ieri sera la dose era diversa.
        val ieri = listOf(
            dose(ieriMattina, "50 mg"),
            dose(ieriSera, "100 mg")
        )

        val oggiM = dose(oggiMattina, "50 mg")
        assertEquals(ieriMattina, CambioDose.riferimentoIeri(oggiM, ieri)?.dataOraMillis)
        assertFalse(CambioDose.eCambio(oggiM, ieri))

        // Oggi sera "150 mg": confronta con ieri SERA ("100 mg") -> cambio.
        val oggiS = dose(oggiSera, "150 mg")
        assertEquals(ieriSera, CambioDose.riferimentoIeri(oggiS, ieri)?.dataOraMillis)
        assertTrue(CambioDose.eCambio(oggiS, ieri))
    }
}
