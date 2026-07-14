package com.carletto.terapianontetemo.allarme

import org.junit.Assert.assertEquals
import org.junit.Test

/** Test JVM dei testi dell'allarme (righe notifica/schermo e frase TTS). */
class TestiAllarmeTest {

    @Test
    fun rigaConIniezioneMostraLaSiringa() {
        assertEquals(
            "💉 Insulina — 10 unità",
            riga(VoceAllarme(nome = "Insulina", dose = "10 unità", iniezione = true))
        )
    }

    @Test
    fun rigaSenzaIniezioneSoloNomeEDose() {
        assertEquals(
            "Cardioaspirina — 1 compressa",
            riga(VoceAllarme(nome = "Cardioaspirina", dose = "1 compressa", iniezione = false))
        )
    }

    @Test
    fun fraseTtsSenzaCambioDose() {
        val voci = listOf(
            VoceAllarme(nome = "Eutirox", dose = "1 compressa", iniezione = false)
        )
        assertEquals("È ora delle medicine. Eutirox, 1 compressa.", fraseTts(voci))
    }

    @Test
    fun fraseTtsConAvvisoDiCambioDoseInCoda() {
        val voci = listOf(
            VoceAllarme(
                nome = "Eutirox",
                dose = "mezza compressa",
                iniezione = false,
                cambioDose = true
            ),
            VoceAllarme(nome = "Insulina", dose = "10 unità", iniezione = true)
        )
        assertEquals(
            "È ora delle medicine. Eutirox, mezza compressa. Insulina, 10 unità. " +
                "Attenzione: da oggi la dose di Eutirox cambia.",
            fraseTts(voci)
        )
    }
}
