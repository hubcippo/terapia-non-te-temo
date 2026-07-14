package com.carletto.terapianontetemo.domain

import com.carletto.terapianontetemo.data.entity.Forma
import com.carletto.terapianontetemo.domain.model.FaseEstratta
import com.carletto.terapianontetemo.domain.model.FasciaEstratta
import com.carletto.terapianontetemo.domain.model.SchemaEstratto
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import java.time.LocalDate
import java.time.LocalDateTime
import java.time.LocalTime
import java.time.ZoneId

/**
 * Test della funzione pura Srotolatore.generaEventi (CONTRACT sez. 6),
 * su casi con la stessa struttura delle prescrizioni reali validate (schema a scalare, titolazione, mantenimento).
 */
class SrotolatoreTest {

    private val zona: ZoneId = ZoneId.of("Europe/Rome")
    private val inizio: LocalDate = LocalDate.of(2026, 7, 15)
    private val farmacoId = 1L

    private fun millis(anno: Int, mese: Int, giorno: Int, ora: Int, minuti: Int): Long =
        LocalDateTime.of(anno, mese, giorno, ora, minuti).atZone(zona).toInstant().toEpochMilli()

    private fun oraLocale(epochMillis: Long): LocalTime =
        java.time.Instant.ofEpochMilli(epochMillis).atZone(zona).toLocalTime()

    private fun fase(
        ordine: Int,
        dose: String? = null,
        doseMattina: String? = null,
        doseSera: String? = null,
        schema: SchemaEstratto,
        intervalloOre: Int? = null,
        quando: List<FasciaEstratta> = emptyList(),
        durataGiorni: Int? = null,
        mantenimento: Boolean = false,
        dopoFasePrecedente: Boolean = false
    ): FaseEstratta = FaseEstratta(
        ordine = ordine,
        dose = dose,
        doseMattina = doseMattina,
        doseSera = doseSera,
        schema = schema,
        intervalloOre = intervalloOre,
        quando = quando,
        durataGiorni = durataGiorni,
        mantenimento = mantenimento,
        dopoFasePrecedente = dopoFasePrecedente
    )

    @Test
    fun scalareDodiciEventiConCodaA24Ore() {
        val fasi = listOf(
            fase(
                ordine = 1, dose = "1",
                schema = SchemaEstratto.INTERVALLO, intervalloOre = 12, durataGiorni = 5
            ),
            fase(
                ordine = 2, dose = "1",
                schema = SchemaEstratto.INTERVALLO, intervalloOre = 24, durataGiorni = 2,
                dopoFasePrecedente = true
            )
        )

        val eventi = Srotolatore.generaEventi(fasi, inizio, farmacoId, Forma.INIEZIONE, zona)

        // 5 giorni x 2 somministrazioni + 2 giorni x 1 somministrazione = 12
        assertEquals(12, eventi.size)

        // I primi 10 eventi (fase 1) alle 07:00 e 19:00.
        val orariFase1 = eventi.take(10).map { oraLocale(it.dataOraMillis) }.toSet()
        assertEquals(setOf(LocalTime.of(7, 0), LocalTime.of(19, 0)), orariFase1)

        // Gli ultimi 2 eventi (fase 2, dopo la precedente): 20 e 21 luglio alle 07:00.
        assertEquals(millis(2026, 7, 20, 7, 0), eventi[10].dataOraMillis)
        assertEquals(millis(2026, 7, 21, 7, 0), eventi[11].dataOraMillis)

        // Tutti gli eventi: forma INIEZIONE e dose "1".
        assertTrue(eventi.all { it.forma == Forma.INIEZIONE })
        assertTrue(eventi.all { it.dose == "1" })
        assertTrue(eventi.all { it.farmacoId == farmacoId })
    }

    @Test
    fun titolazioneSettantadueEventiConDosiPerFascia() {
        val fasi = listOf(
            fase(
                ordine = 1, dose = "50 mg 1 cs",
                schema = SchemaEstratto.ORARI, quando = listOf(FasciaEstratta.SERA),
                durataGiorni = 2
            ),
            fase(
                ordine = 2, dose = "50 mg 1 cs",
                schema = SchemaEstratto.ORARI,
                quando = listOf(FasciaEstratta.MATTINA, FasciaEstratta.SERA),
                durataGiorni = 5, dopoFasePrecedente = true
            ),
            fase(
                ordine = 3,
                doseMattina = "50 mg 1 cs", doseSera = "75 mg 1 cs",
                schema = SchemaEstratto.ORARI,
                quando = listOf(FasciaEstratta.MATTINA, FasciaEstratta.SERA),
                durataGiorni = null, mantenimento = true, dopoFasePrecedente = true
            )
        )

        val eventi = Srotolatore.generaEventi(fasi, inizio, farmacoId, Forma.ALTRO, zona)

        // 2 + 5*2 + 30*2 = 72
        assertEquals(72, eventi.size)

        // Fase 1: 2 eventi alle 19:00 (15 e 16 luglio), dose piena.
        val fase1 = eventi.take(2)
        assertEquals(millis(2026, 7, 15, 19, 0), fase1[0].dataOraMillis)
        assertEquals(millis(2026, 7, 16, 19, 0), fase1[1].dataOraMillis)
        assertTrue(fase1.all { it.dose == "50 mg 1 cs" })

        // Fase 2: 10 eventi, inizia il giorno dopo la fine di fase 1 -> 17 luglio 07:00.
        val fase2 = eventi.subList(2, 12)
        assertEquals(10, fase2.size)
        assertEquals(millis(2026, 7, 17, 7, 0), fase2[0].dataOraMillis)
        assertTrue(fase2.all { it.dose == "50 mg 1 cs" })

        // Fase 3 (mantenimento 30 giorni): 60 eventi, inizia il giorno dopo la fine di fase 2 -> 22 luglio 07:00.
        val fase3 = eventi.subList(12, 72)
        assertEquals(60, fase3.size)
        assertEquals(millis(2026, 7, 22, 7, 0), fase3[0].dataOraMillis)

        // Titolazione: 07:00 -> doseMattina, 19:00 -> doseSera.
        val mattina3 = fase3.filter { oraLocale(it.dataOraMillis) == LocalTime.of(7, 0) }
        val sera3 = fase3.filter { oraLocale(it.dataOraMillis) == LocalTime.of(19, 0) }
        assertEquals(30, mattina3.size)
        assertEquals(30, sera3.size)
        assertTrue(mattina3.all { it.dose == "50 mg 1 cs" })
        assertTrue(sera3.all { it.dose == "75 mg 1 cs" })
    }

    @Test
    fun mantenimentoTrentaEventiAlMattino() {
        val fasi = listOf(
            fase(
                ordine = 1, dose = "1",
                schema = SchemaEstratto.ORARI, quando = listOf(FasciaEstratta.MATTINA),
                durataGiorni = null, mantenimento = true
            )
        )

        val eventi = Srotolatore.generaEventi(fasi, inizio, farmacoId, Forma.COMPRESSA, zona)

        // Orizzonte default di mantenimento: 30 giorni, una dose al mattino.
        assertEquals(30, eventi.size)
        assertTrue(eventi.all { oraLocale(it.dataOraMillis) == LocalTime.of(7, 0) })
        assertEquals(millis(2026, 7, 15, 7, 0), eventi[0].dataOraMillis)
    }

    @Test
    fun primoEventoScalareIl15LuglioAlleSette() {
        val fasi = listOf(
            fase(
                ordine = 1, dose = "1",
                schema = SchemaEstratto.INTERVALLO, intervalloOre = 12, durataGiorni = 5
            )
        )

        val eventi = Srotolatore.generaEventi(fasi, inizio, farmacoId, Forma.INIEZIONE, zona)

        // Epoch millis coerente con Europe/Rome (UTC+2 in luglio).
        assertEquals(
            LocalDateTime.of(2026, 7, 15, 7, 0).atZone(zona).toInstant().toEpochMilli(),
            eventi.first().dataOraMillis
        )
        assertEquals(eventi.minOf { it.dataOraMillis }, eventi.first().dataOraMillis)
    }
}
