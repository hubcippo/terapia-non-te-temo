package com.carletto.terapianontetemo.data

import com.carletto.terapianontetemo.data.entity.DoseEvent
import com.carletto.terapianontetemo.data.entity.Farmaco
import com.carletto.terapianontetemo.data.entity.StatoDose
import kotlinx.coroutines.flow.Flow
import java.time.LocalDate
import java.time.ZoneId

/** Repository usato dalla Home (CONTRACT sez. 4). */
class TerapiaRepository(db: AppDatabase) {

    private val farmacoDao = db.farmacoDao()
    private val doseEventDao = db.doseEventDao()

    /** Tutte le dosi di oggi (mezzanotte locale -> mezzanotte successiva), ordinate per ora. */
    fun doseDiOggi(): Flow<List<DoseEvent>> {
        val zona = ZoneId.systemDefault()
        val oggi = LocalDate.now(zona)
        val inizio = oggi.atStartOfDay(zona).toInstant().toEpochMilli()
        val fine = oggi.plusDays(1).atStartOfDay(zona).toInstant().toEpochMilli()
        return doseEventDao.inRange(inizio, fine)
    }

    /** Prossima dose in ATTESA con orario >= oraMillis. */
    fun prossimaDose(oraMillis: Long): Flow<DoseEvent?> =
        doseEventDao.prossima(oraMillis)

    /** Segna la dose come PRESO registrando il timestamp dell'azione. */
    suspend fun segnaFatto(doseId: Long, tsMillis: Long) {
        doseEventDao.aggiornaStato(doseId, StatoDose.PRESO, tsMillis)
    }

    /** Rimanda la dose a un nuovo orario (torna in ATTESA sul nuovo orario). */
    suspend fun rimanda(doseId: Long, nuovoOraMillis: Long) {
        doseEventDao.rimanda(doseId, nuovoOraMillis, System.currentTimeMillis())
    }

    fun farmaco(id: Long): Flow<Farmaco?> = farmacoDao.byId(id)

    /** Farmaci per id, in una sola query (evita N+1 dalla Home). */
    fun farmaci(ids: List<Long>): Flow<List<Farmaco>> = farmacoDao.byIds(ids)

    // --- Fase D: allarmi (operazioni per fascia, CONTRACT sez. 11) ---

    /** Dosi in ATTESA con orario esattamente uguale alla fascia. */
    suspend fun dosiAttesaAllaFascia(fascia: Long): List<DoseEvent> =
        doseEventDao.dosiAttesaAllaFascia(fascia)

    /** Segna PRESO tutte le dosi in ATTESA della fascia. */
    suspend fun segnaPresoFascia(fascia: Long, ts: Long) {
        doseEventDao.segnaPresoFascia(fascia, ts)
    }

    /** Rimanda tutte le dosi in ATTESA della fascia al nuovo orario. */
    suspend fun rimandaFascia(fascia: Long, nuovaOra: Long, ts: Long) {
        doseEventDao.rimandaFascia(fascia, nuovaOra, ts)
    }

    /** Mappa farmacoId -> nome in una sola query one-shot (niente observer). */
    suspend fun nomiFarmaci(ids: List<Long>): Map<Long, String> =
        farmacoDao.byIdsOneShot(ids).associate { it.id to it.nome }
}
