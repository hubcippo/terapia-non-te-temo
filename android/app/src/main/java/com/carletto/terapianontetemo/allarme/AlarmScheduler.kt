package com.carletto.terapianontetemo.allarme

import android.app.AlarmManager
import android.app.PendingIntent
import android.content.Context
import android.content.Intent
import com.carletto.terapianontetemo.TerapiaApp

/**
 * Pianifica l'allarme sulla prossima fascia con AlarmManager.setAlarmClock
 * (CONTRACT sez. 11). Un solo PendingIntent per l'allarme reale (requestCode
 * 1001) e uno per la prova (1002). Niente WorkManager, niente foreground
 * service: la sveglia esatta funziona anche ad app chiusa e in Doze.
 */
object AlarmScheduler {

    const val EXTRA_FASCIA = "fasciaMillis"
    const val EXTRA_PROVA = "prova"

    private const val REQUEST_ALLARME = 1001
    private const val REQUEST_PROVA = 1002

    /** Finestra di fallback (10 min) se setAlarmClock viene negato. */
    private const val FINESTRA_FALLBACK_MILLIS = 10 * 60 * 1000L

    /**
     * Marca SALTATO le dosi scadute, poi programma l'allarme sulla prossima
     * fascia (o cancella l'allarme se non c'e' piu' nulla in attesa).
     *
     * @param escludiFinoAMillis se non null, considera solo le fasce con
     * dataOraMillis > questo valore: usato da AlarmReceiver per pianificare
     * la fascia SUCCESSIVA a quella appena suonata (che resta in ATTESA
     * finche' Carlo non preme Fatto/Rimanda) senza rientrare in loop.
     */
    suspend fun ripianifica(context: Context, escludiFinoAMillis: Long? = null) {
        val appContext = context.applicationContext
        val app = appContext as TerapiaApp
        val dao = app.database.doseEventDao()
        val adesso = System.currentTimeMillis()

        val tutte = dao.tutteAttesa()
        val scadute = ProssimoAllarme.scadute(tutte, adesso)
        if (scadute.isNotEmpty()) {
            dao.marcaSaltate(scadute, adesso)
        }
        // prossimaFascia esclude gia' le scadute per definizione: niente ri-filtro.
        val candidate = if (escludiFinoAMillis != null) {
            tutte.filter { it.dataOraMillis > escludiFinoAMillis }
        } else {
            tutte
        }

        val alarmManager = appContext.getSystemService(AlarmManager::class.java)
        val fascia = ProssimoAllarme.prossimaFascia(candidate, adesso)
        if (fascia == null) {
            alarmManager.cancel(pendingIntentAllarme(appContext))
            return
        }
        // FLAG_UPDATE_CURRENT aggiorna l'extra fascia sul PendingIntent 1001.
        programma(alarmManager, fascia, pendingIntentAllarme(appContext, fascia))
    }

    /**
     * Allarme di PROVA tra delaySecondi (default 60). Non tocca il DB:
     * AlarmReceiver mostrera' solo la notifica full-screen con fascia = -1.
     */
    fun programmaProva(context: Context, delaySecondi: Int = 60) {
        val appContext = context.applicationContext
        val alarmManager = appContext.getSystemService(AlarmManager::class.java)
        val trigger = System.currentTimeMillis() + delaySecondi * 1000L
        val intent = Intent(appContext, AlarmReceiver::class.java)
            .putExtra(EXTRA_PROVA, true)
            .putExtra(EXTRA_FASCIA, -1L)
        val pendingIntent = PendingIntent.getBroadcast(
            appContext,
            REQUEST_PROVA,
            intent,
            PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_UPDATE_CURRENT
        )
        programma(alarmManager, trigger, pendingIntent)
    }

    private fun pendingIntentAllarme(context: Context, fasciaMillis: Long = -1L): PendingIntent {
        val intent = Intent(context, AlarmReceiver::class.java)
            .putExtra(EXTRA_FASCIA, fasciaMillis)
            .putExtra(EXTRA_PROVA, false)
        return PendingIntent.getBroadcast(
            context,
            REQUEST_ALLARME,
            intent,
            PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_UPDATE_CURRENT
        )
    }

    private fun programma(
        alarmManager: AlarmManager,
        triggerMillis: Long,
        pendingIntent: PendingIntent
    ) {
        // Con USE_EXACT_ALARM (app sveglia/promemoria medico) canScheduleExactAlarms
        // e' sempre true: setAlarmClock viene tentato senza check preventivo e in
        // caso di SecurityException si ripiega su setWindow (CONTRACT sez. 11).
        try {
            alarmManager.setAlarmClock(
                AlarmManager.AlarmClockInfo(triggerMillis, null),
                pendingIntent
            )
        } catch (e: SecurityException) {
            alarmManager.setWindow(
                AlarmManager.RTC_WAKEUP,
                triggerMillis,
                FINESTRA_FALLBACK_MILLIS,
                pendingIntent
            )
        }
    }
}
