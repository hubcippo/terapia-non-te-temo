package com.carletto.terapianontetemo.allarme

import com.carletto.terapianontetemo.data.entity.Forma
import com.carletto.terapianontetemo.util.etichettaFarmaco

/**
 * Testi dell'allarme (notifica, schermo, voce TTS) — LOGICA PURA testabile.
 * Le voci arrivano gia' risolte: nome farmaco, dose, forma e cambio dose.
 */
data class VoceAllarme(
    val nome: String,
    val dose: String,
    val iniezione: Boolean,
    val cambioDose: Boolean = false
)

/** Nome del farmaco con 💉 davanti se iniezione (unica regola, da etichettaFarmaco). */
fun etichetta(voce: VoceAllarme): String =
    etichettaFarmaco(if (voce.iniezione) Forma.INIEZIONE else Forma.ALTRO, voce.nome)

/** Riga per notifica e schermo allarme: "💉 Nome — dose". */
fun riga(voce: VoceAllarme): String = "${etichetta(voce)} — ${voce.dose}"

/** Frase per la voce TTS, con l'eventuale avviso di cambio dose in coda. */
fun fraseTts(voci: List<VoceAllarme>): String {
    val frase = StringBuilder("È ora delle medicine. ")
    voci.forEach { frase.append("${it.nome}, ${it.dose}. ") }
    voci.filter { it.cambioDose }.map { it.nome }.distinct().forEach { nome ->
        frase.append("Attenzione: da oggi la dose di $nome cambia. ")
    }
    return frase.toString().trim()
}
