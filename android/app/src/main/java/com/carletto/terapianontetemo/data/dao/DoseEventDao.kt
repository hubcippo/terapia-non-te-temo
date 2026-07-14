package com.carletto.terapianontetemo.data.dao

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.Query
import androidx.room.Update
import com.carletto.terapianontetemo.data.entity.DoseEvent
import com.carletto.terapianontetemo.data.entity.StatoDose
import kotlinx.coroutines.flow.Flow

@Dao
interface DoseEventDao {

    @Insert
    suspend fun insert(evento: DoseEvent): Long

    @Insert
    suspend fun insertAll(eventi: List<DoseEvent>): List<Long>

    @Update
    suspend fun update(evento: DoseEvent)

    /** Dosi nel range [daMillis, aMillis) — tipicamente un giorno intero. */
    @Query(
        "SELECT * FROM DoseEvent WHERE dataOraMillis >= :daMillis AND dataOraMillis < :aMillis " +
            "ORDER BY dataOraMillis"
    )
    fun inRange(daMillis: Long, aMillis: Long): Flow<List<DoseEvent>>

    /** Prossima dose in ATTESA con orario >= oraMillis. */
    @Query(
        "SELECT * FROM DoseEvent WHERE stato = :stato AND dataOraMillis >= :oraMillis " +
            "ORDER BY dataOraMillis LIMIT 1"
    )
    fun prossima(oraMillis: Long, stato: StatoDose = StatoDose.ATTESA): Flow<DoseEvent?>

    /** Aggiorna stato e timestamp azione. */
    @Query("UPDATE DoseEvent SET stato = :stato, tsAzioneMillis = :tsMillis WHERE id = :doseId")
    suspend fun aggiornaStato(doseId: Long, stato: StatoDose, tsMillis: Long?)

    /** Rimanda: sposta l'orario e riporta in ATTESA, registrando il timestamp dell'azione. */
    @Query(
        "UPDATE DoseEvent SET dataOraMillis = :nuovoOraMillis, stato = :stato, " +
            "tsAzioneMillis = :tsMillis WHERE id = :doseId"
    )
    suspend fun rimanda(
        doseId: Long,
        nuovoOraMillis: Long,
        tsMillis: Long?,
        stato: StatoDose = StatoDose.ATTESA
    )

    // --- Fase D: allarmi (CONTRACT sez. 11) ---

    /** Tutte le dosi in ATTESA, ordinate per orario (per la ripianificazione allarmi). */
    @Query("SELECT * FROM DoseEvent WHERE stato = :stato ORDER BY dataOraMillis")
    suspend fun tutteAttesa(stato: StatoDose = StatoDose.ATTESA): List<DoseEvent>

    /** Marca SALTATO le dosi scadute (oltre 2h dall'orario previsto). */
    @Query(
        "UPDATE DoseEvent SET stato = :stato, tsAzioneMillis = :ts WHERE id IN (:ids)"
    )
    suspend fun marcaSaltate(ids: List<Long>, ts: Long, stato: StatoDose = StatoDose.SALTATO)

    /** Dosi di un farmaco nel range [da, a) — per il confronto dose di ieri vs oggi. */
    @Query(
        "SELECT * FROM DoseEvent WHERE farmacoId = :farmacoId AND dataOraMillis >= :da " +
            "AND dataOraMillis < :a ORDER BY dataOraMillis"
    )
    suspend fun doseDiFarmacoInRange(farmacoId: Long, da: Long, a: Long): List<DoseEvent>

    /** Dosi in ATTESA con orario esattamente uguale alla fascia (raggruppamento allarme). */
    @Query("SELECT * FROM DoseEvent WHERE stato = :stato AND dataOraMillis = :fascia")
    suspend fun dosiAttesaAllaFascia(
        fascia: Long,
        stato: StatoDose = StatoDose.ATTESA
    ): List<DoseEvent>

    /** Rimanda tutte le dosi in ATTESA della fascia al nuovo orario (restano ATTESA). */
    @Query(
        "UPDATE DoseEvent SET dataOraMillis = :nuovaOra, tsAzioneMillis = :ts " +
            "WHERE stato = :stato AND dataOraMillis = :fascia"
    )
    suspend fun rimandaFascia(
        fascia: Long,
        nuovaOra: Long,
        ts: Long,
        stato: StatoDose = StatoDose.ATTESA
    )

    /** Segna PRESO tutte le dosi in ATTESA della fascia. */
    @Query(
        "UPDATE DoseEvent SET stato = :nuovoStato, tsAzioneMillis = :ts " +
            "WHERE stato = :statoAttesa AND dataOraMillis = :fascia"
    )
    suspend fun segnaPresoFascia(
        fascia: Long,
        ts: Long,
        nuovoStato: StatoDose = StatoDose.PRESO,
        statoAttesa: StatoDose = StatoDose.ATTESA
    )

    // --- Fase E: storico, ferma terapia, badge cambio dose (CONTRACT sez. 14) ---

    /** Tutte le dosi, più recenti prima (Storico). */
    @Query("SELECT * FROM DoseEvent ORDER BY dataOraMillis DESC")
    fun tutte(): Flow<List<DoseEvent>>

    /** Ferma terapia: elimina SOLO le dosi in ATTESA del farmaco. */
    @Query("DELETE FROM DoseEvent WHERE farmacoId = :farmacoId AND stato = :stato")
    suspend fun eliminaAttesaDiFarmaco(farmacoId: Long, stato: StatoDose = StatoDose.ATTESA)

    /** Dosi ieri per più farmaci in un colpo (badge scalare, no N+1). */
    @Query(
        "SELECT * FROM DoseEvent WHERE farmacoId IN (:ids) AND dataOraMillis >= :da " +
            "AND dataOraMillis < :a"
    )
    suspend fun dosiDiFarmaciInRange(ids: List<Long>, da: Long, a: Long): List<DoseEvent>
}
