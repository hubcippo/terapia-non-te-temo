package com.carletto.terapianontetemo.domain

import com.carletto.terapianontetemo.data.entity.Fascia
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test
import java.time.LocalTime

/** Test di OrariFissi: mapping intervalli/fasce sugli orari fissi (CONTRACT sez. 6). */
class OrariFissiTest {

    @Test
    fun intervallo24OreUnaSommministrazioneAlMattino() {
        assertEquals(listOf(LocalTime.of(7, 0)), OrariFissi.orariPerIntervallo(24))
    }

    @Test
    fun intervallo12OreMattinaESera() {
        assertEquals(
            listOf(LocalTime.of(7, 0), LocalTime.of(19, 0)),
            OrariFissi.orariPerIntervallo(12)
        )
    }

    @Test
    fun intervallo8OreTreSomministrazioni() {
        assertEquals(
            listOf(LocalTime.of(7, 0), LocalTime.of(15, 0), LocalTime.of(23, 0)),
            OrariFissi.orariPerIntervallo(8)
        )
    }

    @Test
    fun intervallo6OreQuattroSomministrazioni() {
        assertEquals(
            listOf(
                LocalTime.of(7, 0),
                LocalTime.of(13, 0),
                LocalTime.of(19, 0),
                LocalTime.of(23, 0)
            ),
            OrariFissi.orariPerIntervallo(6)
        )
    }

    @Test
    fun intervalloNullUsaLeFasceDefault() {
        assertEquals(
            listOf(
                LocalTime.of(7, 0),
                LocalTime.of(15, 0),
                LocalTime.of(19, 0),
                LocalTime.of(23, 0)
            ),
            OrariFissi.orariPerIntervallo(null)
        )
    }

    @Test
    fun intervalloNonStandardUsaLeFasceDefault() {
        assertEquals(OrariFissi.DEFAULT, OrariFissi.orariPerIntervallo(5))
        assertEquals(OrariFissi.DEFAULT, OrariFissi.orariPerIntervallo(0))
        assertEquals(OrariFissi.DEFAULT, OrariFissi.orariPerIntervallo(48))
    }

    @Test
    fun orarioPerOgniFascia() {
        assertEquals(LocalTime.of(7, 0), OrariFissi.orario(Fascia.MATTINA))
        assertEquals(LocalTime.of(13, 0), OrariFissi.orario(Fascia.PRANZO))
        assertEquals(LocalTime.of(15, 0), OrariFissi.orario(Fascia.POMERIGGIO))
        assertEquals(LocalTime.of(19, 0), OrariFissi.orario(Fascia.SERA))
        assertEquals(LocalTime.of(23, 0), OrariFissi.orario(Fascia.NOTTE))
    }

    @Test
    fun eMattinaSogliaMezzogiorno() {
        assertTrue(OrariFissi.eMattina(LocalTime.of(7, 0)))
        assertFalse(OrariFissi.eMattina(LocalTime.of(12, 0)))
        assertFalse(OrariFissi.eMattina(LocalTime.of(19, 0)))
    }
}
