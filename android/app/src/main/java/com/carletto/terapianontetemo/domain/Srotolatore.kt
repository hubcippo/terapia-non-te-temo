package com.carletto.terapianontetemo.domain

import com.carletto.terapianontetemo.data.AppDatabase
import com.carletto.terapianontetemo.data.TerapiaRepository
import com.carletto.terapianontetemo.data.entity.DoseEvent
import com.carletto.terapianontetemo.data.entity.Farmaco
import com.carletto.terapianontetemo.data.entity.Fascia
import com.carletto.terapianontetemo.data.entity.Fase
import com.carletto.terapianontetemo.data.entity.Forma
import com.carletto.terapianontetemo.data.entity.SchemaDose
import com.carletto.terapianontetemo.domain.model.FaseEstratta
import com.carletto.terapianontetemo.domain.model.FasciaEstratta
import com.carletto.terapianontetemo.domain.model.FormaEstratta
import com.carletto.terapianontetemo.domain.model.PianoEstratto
import com.carletto.terapianontetemo.domain.model.SchemaEstratto
import java.time.LocalDate
import java.time.LocalDateTime
import java.time.LocalTime
import java.time.ZoneId

/** PianoEstratto -> inserimenti Room + generazione DoseEvent concreti (CONTRACT sez. 4 e 6). */
object Srotolatore {

    /** Orizzonte default per fasi di mantenimento / durata indefinita. TODO reschedule periodico. */
    private const val ORIZZONTE_MANTENIMENTO_GIORNI = 30

    /** Inserisce farmaco+fasi e genera i DoseEvent concreti a partire da 'inizio'. */
    suspend fun applica(
        piano: PianoEstratto,
        inizio: LocalDate,
        repo: TerapiaRepository,
        db: AppDatabase
    ) {
        val zona = ZoneId.systemDefault()

        for (terapia in piano.terapie) {
            val forma = terapia.forma.toEntity()
            val farmacoId = db.farmacoDao().insert(
                Farmaco(nome = terapia.farmaco, forma = forma, note = terapia.note)
            )

            val fasiOrdinate = terapia.fasi.sortedBy { it.ordine }
            db.faseDao().insertAll(fasiOrdinate.map { it.toEntity(farmacoId) })

            db.doseEventDao().insertAll(generaEventi(fasiOrdinate, inizio, farmacoId, forma, zona))
        }
    }

    /**
     * Funzione PURA (testabile senza database): srotola le fasi in DoseEvent concreti
     * a partire da 'inizio' (CONTRACT sez. 6).
     */
    fun generaEventi(
        fasi: List<FaseEstratta>,
        inizio: LocalDate,
        farmacoId: Long,
        forma: Forma,
        zona: ZoneId
    ): List<DoseEvent> {
        val eventi = mutableListOf<DoseEvent>()
        // Cursore: primo giorno libero dopo la fine dell'ultima fase srotolata.
        var cursore = inizio

        for (faseEstratta in fasi) {
            // Fase sequenziale: inizia il giorno dopo la fine della precedente;
            // altrimenti parte da 'inizio'.
            val inizioFase = if (faseEstratta.dopoFasePrecedente) cursore else inizio
            val durata = faseEstratta.durataGiorni ?: ORIZZONTE_MANTENIMENTO_GIORNI
            val orari = orariPerFase(faseEstratta)

            for (giorno in 0 until durata) {
                val data = inizioFase.plusDays(giorno.toLong())
                for (orario in orari) {
                    eventi += DoseEvent(
                        farmacoId = farmacoId,
                        dataOraMillis = LocalDateTime.of(data, orario)
                            .atZone(zona).toInstant().toEpochMilli(),
                        dose = dosePerOrario(faseEstratta, orario),
                        forma = forma
                    )
                }
            }

            cursore = inizioFase.plusDays(durata.toLong())
        }

        return eventi
    }

    /** Orari del giorno per la fase, secondo lo schema (CONTRACT sez. 6). */
    private fun orariPerFase(fase: FaseEstratta): List<LocalTime> = when (fase.schema) {
        SchemaEstratto.INTERVALLO -> OrariFissi.orariPerIntervallo(fase.intervalloOre)
        SchemaEstratto.ORARI ->
            fase.quando.map { OrariFissi.orario(it.toEntity()) }
                .distinct()
                .sorted()
                .ifEmpty { OrariFissi.DEFAULT }
    }

    /**
     * Titolazione: se doseMattina/doseSera valorizzati, la dose dipende dalla fascia:
     * orari del mattino (< 12:00) -> doseMattina, altrimenti -> doseSera.
     */
    private fun dosePerOrario(fase: FaseEstratta, orario: LocalTime): String {
        val titolazione = fase.doseMattina != null || fase.doseSera != null
        return if (titolazione) {
            if (OrariFissi.eMattina(orario)) {
                fase.doseMattina ?: fase.dose ?: ""
            } else {
                fase.doseSera ?: fase.dose ?: ""
            }
        } else {
            fase.dose ?: ""
        }
    }

    // ---- mapping modello estrazione -> enum/entity Room ----

    private fun FormaEstratta.toEntity(): Forma = when (this) {
        FormaEstratta.COMPRESSA -> Forma.COMPRESSA
        FormaEstratta.INIEZIONE -> Forma.INIEZIONE
        FormaEstratta.ALTRO -> Forma.ALTRO
    }

    private fun SchemaEstratto.toEntity(): SchemaDose = when (this) {
        SchemaEstratto.INTERVALLO -> SchemaDose.INTERVALLO
        SchemaEstratto.ORARI -> SchemaDose.ORARI
    }

    private fun FasciaEstratta.toEntity(): Fascia = when (this) {
        FasciaEstratta.MATTINA -> Fascia.MATTINA
        FasciaEstratta.PRANZO -> Fascia.PRANZO
        FasciaEstratta.POMERIGGIO -> Fascia.POMERIGGIO
        FasciaEstratta.SERA -> Fascia.SERA
        FasciaEstratta.NOTTE -> Fascia.NOTTE
    }

    private fun FaseEstratta.toEntity(farmacoId: Long): Fase = Fase(
        farmacoId = farmacoId,
        ordine = ordine,
        dose = dose,
        doseMattina = doseMattina,
        doseSera = doseSera,
        schema = schema.toEntity(),
        intervalloOre = intervalloOre,
        quando = quando.map { it.toEntity() },
        durataGiorni = durataGiorni,
        mantenimento = mantenimento,
        dopoFasePrecedente = dopoFasePrecedente
    )
}
