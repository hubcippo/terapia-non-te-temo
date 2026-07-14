package com.carletto.terapianontetemo.data.dao

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.Query
import androidx.room.Update
import com.carletto.terapianontetemo.data.entity.Farmaco
import com.carletto.terapianontetemo.data.entity.StatoDose
import kotlinx.coroutines.flow.Flow

@Dao
interface FarmacoDao {

    @Insert
    suspend fun insert(farmaco: Farmaco): Long

    @Update
    suspend fun update(farmaco: Farmaco)

    @Query("SELECT * FROM Farmaco WHERE id = :id")
    fun byId(id: Long): Flow<Farmaco?>

    @Query("SELECT * FROM Farmaco WHERE id IN (:ids)")
    fun byIds(ids: List<Long>): Flow<List<Farmaco>>

    /** Lettura one-shot (senza observer Room): per receiver e schermata allarme. */
    @Query("SELECT * FROM Farmaco WHERE id IN (:ids)")
    suspend fun byIdsOneShot(ids: List<Long>): List<Farmaco>

    @Query("SELECT * FROM Farmaco ORDER BY nome")
    fun tutti(): Flow<List<Farmaco>>

    // --- Fase E (CONTRACT sez. 14) ---

    /** Farmaci con almeno una dose in ATTESA = terapie attive. */
    @Query(
        "SELECT * FROM Farmaco WHERE id IN " +
            "(SELECT DISTINCT farmacoId FROM DoseEvent WHERE stato = :stato) ORDER BY nome"
    )
    fun conDosiAttesa(stato: StatoDose = StatoDose.ATTESA): Flow<List<Farmaco>>
}
