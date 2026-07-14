package com.carletto.terapianontetemo.domain

import com.carletto.terapianontetemo.domain.model.ConfidenzaEstratta
import com.carletto.terapianontetemo.domain.model.FasciaEstratta
import com.carletto.terapianontetemo.domain.model.FormaEstratta
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * Test di EstrazioneParser su output del modello vision con la stessa struttura
 * dei casi reali validati (dati sintetici/anonimizzati in src/test/resources).
 */
class EstrazioneParserTest {

    private fun risorsa(nome: String): String =
        javaClass.classLoader.getResource(nome)!!.readText()

    @Test
    fun caso1ScalareRicettaConTreTerapie() {
        val piano = EstrazioneParser.parse(risorsa("caso1_scalare.json"))

        assertTrue(piano.eRicetta)
        assertEquals(3, piano.terapie.size)

        val scalare = piano.terapie[0]
        assertEquals(FormaEstratta.INIEZIONE, scalare.forma)
        assertEquals(2, scalare.fasi.size)
        val faseScalare = scalare.fasi[1]
        assertTrue(faseScalare.dopoFasePrecedente)
        assertEquals(24, faseScalare.intervalloOre)
        assertEquals(2, faseScalare.durataGiorni)

        val ogni8h = piano.terapie[1]
        assertEquals(8, ogni8h.fasi[0].intervalloOre)
        assertEquals(7, ogni8h.fasi[0].durataGiorni)

        val digiuno = piano.terapie[2]
        assertEquals(listOf(FasciaEstratta.MATTINA), digiuno.fasi[0].quando)
        assertTrue(digiuno.fasi[0].mantenimento)
        assertNull(digiuno.fasi[0].durataGiorni)
        assertEquals("a digiuno", digiuno.note)
    }

    @Test
    fun caso2TitolazioneIlleggibile() {
        val piano = EstrazioneParser.parse(risorsa("caso2_titolazione.json"))

        val titolazione = piano.terapie[1]
        assertTrue(titolazione.illeggibile)
        assertEquals(ConfidenzaEstratta.BASSA, titolazione.confidenza)
        assertEquals(3, titolazione.fasi.size)

        val fase1 = titolazione.fasi[0]
        assertEquals(listOf(FasciaEstratta.SERA), fase1.quando)
        assertEquals(2, fase1.durataGiorni)

        val fase3 = titolazione.fasi[2]
        assertNull(fase3.dose)
        assertEquals("50 mg 1 cs", fase3.doseMattina)
        assertEquals("75 mg 1 cs", fase3.doseSera)
        assertTrue(fase3.mantenimento)

        assertTrue(piano.avvisi.isNotEmpty())
    }

    @Test
    fun caso3GrigliaNonERicetta() {
        val piano = EstrazioneParser.parse(risorsa("caso3_griglia.json"))

        assertFalse(piano.eRicetta)
        assertTrue(piano.terapie.isEmpty())
        assertTrue(piano.avvisi.isNotEmpty())
    }

    @Test
    fun parseTolleraFenceMarkdownIntornoAlJson() {
        val grezzo = risorsa("caso3_griglia.json")
        val conFence = "```json\n" + grezzo + "\n```"

        val piano = EstrazioneParser.parse(conFence)

        assertFalse(piano.eRicetta)
        assertTrue(piano.terapie.isEmpty())
    }
}
