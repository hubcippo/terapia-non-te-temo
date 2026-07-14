package com.carletto.terapianontetemo.domain

import com.carletto.terapianontetemo.data.entity.DoseEvent
import com.carletto.terapianontetemo.data.entity.Forma
import com.carletto.terapianontetemo.data.entity.StatoDose
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

/** Test JVM della logica pura di aderenza (CONTRACT sez. 14). */
class AderenzaTest {

    private val ora = 1_750_000_000_000L

    private fun dose(
        id: Long,
        stato: StatoDose,
        farmacoId: Long = 1L
    ) = DoseEvent(
        id = id,
        farmacoId = farmacoId,
        dataOraMillis = ora + id,
        dose = "1 compressa",
        forma = Forma.COMPRESSA,
        stato = stato
    )

    @Test
    fun tuttePreseCento() {
        val dosi = listOf(
            dose(1, StatoDose.PRESO),
            dose(2, StatoDose.PRESO),
            dose(3, StatoDose.PRESO)
        )
        val riepilogo = Aderenza.complessiva(dosi)
        assertEquals(3, riepilogo.prese)
        assertEquals(0, riepilogo.saltate)
        assertEquals(3, riepilogo.totale)
        assertEquals(100, riepilogo.percento)
    }

    @Test
    fun tutteSaltateZero() {
        val dosi = listOf(
            dose(1, StatoDose.SALTATO),
            dose(2, StatoDose.SALTATO)
        )
        val riepilogo = Aderenza.complessiva(dosi)
        assertEquals(0, riepilogo.prese)
        assertEquals(2, riepilogo.saltate)
        assertEquals(0, riepilogo.percento)
    }

    @Test
    fun misteConArrotondamento() {
        // 2 su 3 = 66,67% -> arrotonda a 67.
        val dueSuTre = Aderenza.complessiva(
            listOf(
                dose(1, StatoDose.PRESO),
                dose(2, StatoDose.PRESO),
                dose(3, StatoDose.SALTATO)
            )
        )
        assertEquals(67, dueSuTre.percento)

        // 1 su 3 = 33,33% -> arrotonda a 33.
        val unaSuTre = Aderenza.complessiva(
            listOf(
                dose(1, StatoDose.PRESO),
                dose(2, StatoDose.SALTATO),
                dose(3, StatoDose.SALTATO)
            )
        )
        assertEquals(33, unaSuTre.percento)
    }

    @Test
    fun listaVuotaPercentoNull() {
        val riepilogo = Aderenza.complessiva(emptyList())
        assertEquals(0, riepilogo.totale)
        assertNull(riepilogo.percento)
    }

    @Test
    fun attesaERimandatoIgnorate() {
        val dosi = listOf(
            dose(1, StatoDose.PRESO),
            dose(2, StatoDose.ATTESA),
            dose(3, StatoDose.RIMANDATO),
            dose(4, StatoDose.SALTATO)
        )
        val riepilogo = Aderenza.complessiva(dosi)
        assertEquals(1, riepilogo.prese)
        assertEquals(1, riepilogo.saltate)
        assertEquals(2, riepilogo.totale)
        assertEquals(50, riepilogo.percento)
    }

    @Test
    fun perFarmacoRaggruppaGiusto() {
        val dosi = listOf(
            dose(1, StatoDose.PRESO, farmacoId = 10L),
            dose(2, StatoDose.PRESO, farmacoId = 10L),
            dose(3, StatoDose.SALTATO, farmacoId = 20L),
            dose(4, StatoDose.ATTESA, farmacoId = 30L)
        )
        val perFarmaco = Aderenza.perFarmaco(dosi)

        assertEquals(Aderenza.Riepilogo(prese = 2, saltate = 0), perFarmaco[10L])
        assertEquals(100, perFarmaco.getValue(10L).percento)

        assertEquals(Aderenza.Riepilogo(prese = 0, saltate = 1), perFarmaco[20L])
        assertEquals(0, perFarmaco.getValue(20L).percento)

        // Il farmaco con sole dosi in ATTESA ha totale 0 e percento null.
        assertEquals(Aderenza.Riepilogo(prese = 0, saltate = 0), perFarmaco[30L])
        assertNull(perFarmaco.getValue(30L).percento)
    }
}
