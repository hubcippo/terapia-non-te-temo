package com.carletto.terapianontetemo

import android.app.Application
import com.carletto.terapianontetemo.data.AppDatabase
import com.carletto.terapianontetemo.data.TerapiaRepository

/**
 * Application dell'app "Terapia non te temo".
 *
 * Espone il database Room e il repository come singleton lazy,
 * usati da ViewModel e schermate (vedi CONTRACT sez. 4 e 7).
 */
class TerapiaApp : Application() {

    /** Database Room dell'app (definito in data/AppDatabase.kt). */
    val database: AppDatabase by lazy { AppDatabase.get(this) }

    /** Repository della terapia (definito in data/TerapiaRepository.kt). */
    val repository: TerapiaRepository by lazy { TerapiaRepository(database) }
}
