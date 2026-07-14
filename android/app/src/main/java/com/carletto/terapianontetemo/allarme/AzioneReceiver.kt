package com.carletto.terapianontetemo.allarme

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import androidx.core.app.NotificationManagerCompat
import com.carletto.terapianontetemo.TerapiaApp

/**
 * Azioni "Fatto" e "Rimanda 10 min" dalla notifica di allarme
 * (CONTRACT sez. 11). Agisce su TUTTE le dosi della fascia.
 */
class AzioneReceiver : BroadcastReceiver() {

    override fun onReceive(context: Context, intent: Intent) {
        val appContext = context.applicationContext
        val prova = intent.getBooleanExtra(AlarmScheduler.EXTRA_PROVA, false)
        val fascia = intent.getLongExtra(AlarmScheduler.EXTRA_FASCIA, -1L)
        val azione = intent.action

        goAsyncIO {
            NotificationManagerCompat.from(appContext).cancel(AlarmReceiver.NOTIFICA_ID)
            if (prova || fascia <= 0L) {
                // Prova: niente DB, la notifica e' gia' stata tolta.
                return@goAsyncIO
            }
            val repository = (appContext as TerapiaApp).repository
            val adesso = System.currentTimeMillis()
            when (azione) {
                ACTION_FATTO -> repository.segnaPresoFascia(fascia, adesso)
                ACTION_RIMANDA -> repository.rimandaFascia(
                    fascia = fascia,
                    nuovaOra = fascia + RIMANDO_MILLIS,
                    ts = adesso
                )
            }
            AlarmScheduler.ripianifica(appContext)
        }
    }

    companion object {
        const val ACTION_FATTO = "com.carletto.terapianontetemo.AZIONE_FATTO"
        const val ACTION_RIMANDA = "com.carletto.terapianontetemo.AZIONE_RIMANDA"

        /** Rimando fisso: 10 minuti. */
        const val RIMANDO_MILLIS = 10 * 60 * 1000L
    }
}
