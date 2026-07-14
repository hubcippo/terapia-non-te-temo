package com.carletto.terapianontetemo.ui.aggiungi

import com.carletto.terapianontetemo.domain.model.ConfidenzaEstratta
import com.carletto.terapianontetemo.domain.model.FaseEstratta
import com.carletto.terapianontetemo.domain.model.FasciaEstratta
import com.carletto.terapianontetemo.domain.model.FormaEstratta
import com.carletto.terapianontetemo.domain.model.PianoEstratto
import com.carletto.terapianontetemo.domain.model.SchemaEstratto
import com.carletto.terapianontetemo.domain.model.TerapiaEstratta

/**
 * Modello UI del piano estratto, adatto ai campi editabili della schermata
 * Conferma: dosi e durata come String (comode per OutlinedTextField), flag
 * [TerapiaEditabile.inclusa] per escludere una terapia senza perderla (undo).
 *
 * Funzioni PURE (nessuna dipendenza Android): testabili in src/test.
 */
data class PianoEditabile(
    val avvisi: List<String> = emptyList(),
    val terapie: List<TerapiaEditabile> = emptyList()
) {

    /** Confermabile se resta almeno una terapia inclusa. */
    val confermabile: Boolean get() = terapie.any { it.inclusa }

    /**
     * Riconverte nel modello di dominio da passare a Srotolatore.applica:
     * - solo le terapie con inclusa=true;
     * - stringhe vuote/blank -> null;
     * - durataGiorni: String -> Int? (toIntOrNull, blank o non numerica -> null).
     *
     * eRicetta=true: si arriva alla Conferma solo se l'estrazione era una ricetta.
     */
    fun versoEstratto(): PianoEstratto = PianoEstratto(
        eRicetta = true,
        avvisi = avvisi,
        terapie = terapie.filter { it.inclusa }.map { terapia ->
            TerapiaEstratta(
                farmaco = terapia.farmaco.trim(),
                forma = terapia.forma,
                note = terapia.note,
                confidenza = terapia.confidenza,
                illeggibile = terapia.illeggibile,
                fasi = terapia.fasi.map { fase ->
                    FaseEstratta(
                        ordine = fase.ordine,
                        dose = fase.dose.vuotaANull(),
                        doseMattina = fase.doseMattina.vuotaANull(),
                        doseSera = fase.doseSera.vuotaANull(),
                        schema = fase.schema,
                        intervalloOre = fase.intervalloOre,
                        quando = fase.quando,
                        durataGiorni = fase.durataGiorni.trim().toIntOrNull(),
                        mantenimento = fase.mantenimento,
                        dopoFasePrecedente = fase.dopoFasePrecedente
                    )
                }
            )
        }
    )
}

data class TerapiaEditabile(
    val farmaco: String,
    val forma: FormaEstratta,
    val note: String?,
    val confidenza: ConfidenzaEstratta,
    val illeggibile: Boolean,
    val inclusa: Boolean = true,
    val fasi: List<FaseEditabile> = emptyList()
) {

    /** Da evidenziare in UI (card gialla): confidenza non alta o testo illeggibile. */
    val daControllare: Boolean get() = confidenza != ConfidenzaEstratta.ALTA || illeggibile
}

data class FaseEditabile(
    val ordine: Int,
    val dose: String,
    val doseMattina: String,
    val doseSera: String,
    val schema: SchemaEstratto,
    val intervalloOre: Int?,
    val quando: List<FasciaEstratta>,
    val durataGiorni: String,
    val mantenimento: Boolean,
    val dopoFasePrecedente: Boolean
)

/** PianoEstratto -> modello editabile: i null diventano stringhe vuote. */
fun daEstratto(p: PianoEstratto): PianoEditabile = PianoEditabile(
    avvisi = p.avvisi,
    terapie = p.terapie.map { terapia ->
        TerapiaEditabile(
            farmaco = terapia.farmaco,
            forma = terapia.forma,
            note = terapia.note,
            confidenza = terapia.confidenza,
            illeggibile = terapia.illeggibile,
            fasi = terapia.fasi.map { fase ->
                FaseEditabile(
                    ordine = fase.ordine,
                    dose = fase.dose.orEmpty(),
                    doseMattina = fase.doseMattina.orEmpty(),
                    doseSera = fase.doseSera.orEmpty(),
                    schema = fase.schema,
                    intervalloOre = fase.intervalloOre,
                    quando = fase.quando,
                    durataGiorni = fase.durataGiorni?.toString().orEmpty(),
                    mantenimento = fase.mantenimento,
                    dopoFasePrecedente = fase.dopoFasePrecedente
                )
            }
        )
    }
)

/** Copia del piano con la terapia in posizione [indice] trasformata. */
fun PianoEditabile.conTerapia(
    indice: Int,
    trasforma: (TerapiaEditabile) -> TerapiaEditabile
): PianoEditabile = copy(
    terapie = terapie.mapIndexed { i, terapia ->
        if (i == indice) trasforma(terapia) else terapia
    }
)

/** Copia del piano con la fase [indiceFase] della terapia [indiceTerapia] trasformata. */
fun PianoEditabile.conFase(
    indiceTerapia: Int,
    indiceFase: Int,
    trasforma: (FaseEditabile) -> FaseEditabile
): PianoEditabile = conTerapia(indiceTerapia) { terapia ->
    terapia.copy(
        fasi = terapia.fasi.mapIndexed { j, fase ->
            if (j == indiceFase) trasforma(fase) else fase
        }
    )
}

private fun String.vuotaANull(): String? = trim().takeIf { it.isNotEmpty() }
