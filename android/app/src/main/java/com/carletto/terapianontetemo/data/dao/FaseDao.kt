package com.carletto.terapianontetemo.data.dao

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.Query
import androidx.room.Update
import com.carletto.terapianontetemo.data.entity.Fase
import kotlinx.coroutines.flow.Flow

@Dao
interface FaseDao {

    @Insert
    suspend fun insert(fase: Fase): Long

    @Insert
    suspend fun insertAll(fasi: List<Fase>): List<Long>

    @Update
    suspend fun update(fase: Fase)

    @Query("SELECT * FROM Fase WHERE farmacoId = :farmacoId ORDER BY ordine")
    fun perFarmaco(farmacoId: Long): Flow<List<Fase>>
}
