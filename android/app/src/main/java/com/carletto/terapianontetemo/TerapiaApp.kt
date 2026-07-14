package com.carletto.terapianontetemo

import android.app.Application
import android.app.NotificationChannel
import android.app.NotificationManager
import android.media.AudioAttributes
import android.media.RingtoneManager
import com.carletto.terapianontetemo.data.AppDatabase
import com.carletto.terapianontetemo.data.TerapiaRepository

/**
 * Application dell'app "Terapia non te temo".
 *
 * Espone il database Room e il repository come singleton lazy,
 * usati da ViewModel e schermate (vedi CONTRACT sez. 4 e 7).
 * In onCreate crea il canale di notifica dell'allarme (CONTRACT sez. 11).
 */
class TerapiaApp : Application() {

    /** Database Room dell'app (definito in data/AppDatabase.kt). */
    val database: AppDatabase by lazy { AppDatabase.get(this) }

    /** Repository della terapia (definito in data/TerapiaRepository.kt). */
    val repository: TerapiaRepository by lazy { TerapiaRepository(database) }

    override fun onCreate() {
        super.onCreate()
        creaCanaleAllarme()
    }

    /**
     * Canale "allarme_terapia" con importanza alta, suono di allarme di
     * sistema su stream USAGE_ALARM e vibrazione: cosi' la notifica suona
     * forte anche quando il full-screen intent viene soppresso.
     */
    private fun creaCanaleAllarme() {
        val attributi = AudioAttributes.Builder()
            .setUsage(AudioAttributes.USAGE_ALARM)
            .setContentType(AudioAttributes.CONTENT_TYPE_SONIFICATION)
            .build()
        val canale = NotificationChannel(
            CANALE_ALLARME,
            "Allarme terapia",
            NotificationManager.IMPORTANCE_HIGH
        ).apply {
            description = "Promemoria insistente per le medicine da prendere"
            setSound(RingtoneManager.getDefaultUri(RingtoneManager.TYPE_ALARM), attributi)
            enableVibration(true)
            vibrationPattern = longArrayOf(0, 800, 400, 800)
        }
        getSystemService(NotificationManager::class.java)
            .createNotificationChannel(canale)
    }

    companion object {
        /** Id del canale di notifica degli allarmi (CONTRACT sez. 11). */
        const val CANALE_ALLARME = "allarme_terapia"
    }
}
