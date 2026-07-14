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
}
