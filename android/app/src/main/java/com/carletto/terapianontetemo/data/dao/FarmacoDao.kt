package com.carletto.terapianontetemo.data.dao

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.Query
import androidx.room.Update
import com.carletto.terapianontetemo.data.entity.Farmaco
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

    @Query("SELECT * FROM Farmaco ORDER BY nome")
    fun tutti(): Flow<List<Farmaco>>
}
