package com.carletto.terapianontetemo.allarme

import com.carletto.terapianontetemo.data.entity.DoseEvent
import com.carletto.terapianontetemo.data.entity.Forma
import com.carletto.terapianontetemo.data.entity.StatoDose
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

/** Test JVM della logica pura di pianificazione (CONTRACT sez. 11). */
class ProssimoAllarmeTest {

    private val adesso = 1_750_000_000_000L
    private val dueOre = ProssimoAllarme.SCADENZA_MILLIS
    private val unOra = 60 * 60 * 1000L

    private fun dose(
        id: Long,
        ora: Long,
        stato: StatoDose = StatoDose.ATTESA,
        farmacoId: Long = 1L
    ) = DoseEvent(
        id = id,
        farmacoId = farmacoId,
        dataOraMillis = ora,
        dose = "1 compressa",
        forma = Forma.COMPRESSA,
        stato = stato
    )

    // --- scadute ---

    @Test
    fun scadutaEsattamenteDueOreFaEInclusa() {
        val dosi = listOf(
            dose(1, adesso - dueOre), // limite esatto: scaduta
            dose(2, adesso - dueOre + 1) // un millisecondo dentro: ancora valida
        )
        assertEquals(listOf(1L), ProssimoAllarme.scadute(dosi, adesso))
    }

    @Test
    fun scaduteIgnoranoLeDosiPrese() {
        val dosi = listOf(
            dose(1, adesso - 3 * unOra, stato = StatoDose.PRESO),
            dose(2, adesso - 3 * unOra, stato = StatoDose.SALTATO),
            dose(3, adesso - 3 * unOra)
        )
        assertEquals(listOf(3L), ProssimoAllarme.scadute(dosi, adesso))
    }

    @Test
    fun scaduteVuoteSeNienteDiVecchio() {
        val dosi = listOf(dose(1, adesso + unOra), dose(2, adesso - unOra))
        assertTrue(ProssimoAllarme.scadute(dosi, adesso).isEmpty())
    }

    // --- prossimaFascia ---

    @Test
    fun prossimaFasciaMinimaTraLeFuture() {
        val dosi = listOf(
            dose(1, adesso + 5 * unOra),
            dose(2, adesso + unOra),
            dose(3, adesso + 3 * unOra)
        )
        assertEquals(adesso + unOra, ProssimoAllarme.prossimaFascia(dosi, adesso))
    }

    @Test
    fun prossimaFasciaIgnoraLeScaduteELePrese() {
        val dosi = listOf(
            dose(1, adesso - dueOre), // scaduta (limite esatto)
            dose(2, adesso + unOra, stato = StatoDose.PRESO),
            dose(3, adesso + 2 * unOra)
        )
        assertEquals(adesso + 2 * unOra, ProssimoAllarme.prossimaFascia(dosi, adesso))
    }

    @Test
    fun prossimaFasciaPassataDaMenoDiDueOreAncoraValida() {
        // Fascia di un'ora fa: l'allarme deve scattare subito, non essere persa.
        val dosi = listOf(dose(1, adesso - unOra), dose(2, adesso + 3 * unOra))
        assertEquals(adesso - unOra, ProssimoAllarme.prossimaFascia(dosi, adesso))
    }

    @Test
    fun prossimaFasciaNullSeNonCeNulla() {
        assertNull(ProssimoAllarme.prossimaFascia(emptyList(), adesso))
        val soloPreseOScadute = listOf(
            dose(1, adesso + unOra, stato = StatoDose.PRESO),
            dose(2, adesso - 3 * unOra)
        )
        assertNull(ProssimoAllarme.prossimaFascia(soloPreseOScadute, adesso))
    }
}
