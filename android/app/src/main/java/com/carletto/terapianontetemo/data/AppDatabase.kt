package com.carletto.terapianontetemo.data

import android.content.Context
import androidx.room.Database
import androidx.room.Room
import androidx.room.RoomDatabase
import androidx.room.TypeConverters
import com.carletto.terapianontetemo.data.dao.DoseEventDao
import com.carletto.terapianontetemo.data.dao.FarmacoDao
import com.carletto.terapianontetemo.data.dao.FaseDao
import com.carletto.terapianontetemo.data.entity.DoseEvent
import com.carletto.terapianontetemo.data.entity.Farmaco
import com.carletto.terapianontetemo.data.entity.Fase

@Database(
    entities = [Farmaco::class, Fase::class, DoseEvent::class],
    version = 1,
    exportSchema = false
)
@TypeConverters(Converters::class)
abstract class AppDatabase : RoomDatabase() {

    abstract fun farmacoDao(): FarmacoDao
    abstract fun faseDao(): FaseDao
    abstract fun doseEventDao(): DoseEventDao

    companion object {
        @Volatile
        private var INSTANCE: AppDatabase? = null

        fun get(context: Context): AppDatabase =
            INSTANCE ?: synchronized(this) {
                INSTANCE ?: Room.databaseBuilder(
                    context.applicationContext,
                    AppDatabase::class.java,
                    "terapia.db"
                ).build().also { INSTANCE = it }
            }
    }
}
