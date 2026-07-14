package com.carletto.terapianontetemo.ui.aggiungi

import com.carletto.terapianontetemo.domain.EstrazioneParser
import com.carletto.terapianontetemo.domain.model.FasciaEstratta
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * Test delle funzioni pure daEstratto/versoEstratto (CONTRACT sez. 9):
 * round-trip sul caso reale di titolazione, esclusione terapia,
 * durata vuota -> null, dose blank -> null.
 */
class PianoEditabileTest {

    private fun risorsa(nome: String): String =
        javaClass.classLoader.getResource(nome)!!.readText()

    private fun pianoCaso2() = EstrazioneParser.parse(risorsa("caso2_titolazione.json"))

    @Test
    fun roundTripSuCaso2Titolazione() {
        val originale = pianoCaso2()
        val roundTrip = daEstratto(originale).versoEstratto()

        // Numero terapie e fasi.
        assertEquals(originale.terapie.size, roundTrip.terapie.size)
        assertEquals(
            originale.terapie.map { it.fasi.size },
            roundTrip.terapie.map { it.fasi.size }
        )

        // Terapia semplice: dose, quando e durata sopravvivono al giro.
        val analgenPrima = originale.terapie[0].fasi[0]
        val analgenDopo = roundTrip.terapie[0].fasi[0]
        assertEquals("1 cs", analgenDopo.dose)
        assertEquals(analgenPrima.quando, analgenDopo.quando)
        assertEquals(7, analgenDopo.durataGiorni)

        // Titolazione: doseMattina/doseSera, mantenimento e dopoFasePrecedente.
        val titolazionePrima = originale.terapie[1].fasi[2]
        val titolazioneDopo = roundTrip.terapie[1].fasi[2]
        assertNull(titolazioneDopo.dose)
        assertEquals("50 mg 1 cs", titolazioneDopo.doseMattina)
        assertEquals("75 mg 1 cs", titolazioneDopo.doseSera)
        assertNull(titolazioneDopo.durataGiorni)
        assertTrue(titolazioneDopo.mantenimento)
        assertTrue(titolazioneDopo.dopoFasePrecedente)
        assertEquals(
            listOf(FasciaEstratta.MATTINA, FasciaEstratta.SERA),
            titolazioneDopo.quando
        )
        assertEquals(titolazionePrima, titolazioneDopo)

        // Il round-trip completo è identico all'originale (nessuna modifica).
        assertEquals(originale, roundTrip)
    }

    @Test
    fun terapiaEsclusaNonCompareNelPianoEstratto() {
        val editabile = daEstratto(pianoCaso2())

        val conEsclusione = editabile.copy(
            terapie = editabile.terapie.mapIndexed { indice, terapia ->
                if (indice == 0) terapia.copy(inclusa = false) else terapia
            }
        )
        val estratto = conEsclusione.versoEstratto()

        assertEquals(1, estratto.terapie.size)
        assertEquals("Neurofase", estratto.terapie[0].farmaco)
    }

    @Test
    fun durataVuotaONonNumericaDiventaNull() {
        val editabile = daEstratto(pianoCaso2())

        fun conDurata(valore: String) = editabile.copy(
            terapie = editabile.terapie.mapIndexed { i, terapia ->
                if (i != 0) terapia
                else terapia.copy(fasi = terapia.fasi.map { it.copy(durataGiorni = valore) })
            }
        ).versoEstratto().terapie[0].fasi[0].durataGiorni

        assertNull(conDurata(""))
        assertNull(conDurata("   "))
        assertNull(conDurata("dieci"))
        assertEquals(10, conDurata(" 10 "))
    }

    @Test
    fun doseVuotaOBlankDiventaNull() {
        val editabile = daEstratto(pianoCaso2())

        val modificato = editabile.copy(
            terapie = editabile.terapie.mapIndexed { i, terapia ->
                if (i != 0) terapia
                else terapia.copy(
                    fasi = terapia.fasi.map {
                        it.copy(dose = "   ", doseMattina = "", doseSera = "\t")
                    }
                )
            }
        ).versoEstratto()

        val fase = modificato.terapie[0].fasi[0]
        assertNull(fase.dose)
        assertNull(fase.doseMattina)
        assertNull(fase.doseSera)
    }
}
