package com.carletto.terapianontetemo.allarme

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent

/**
 * Al riavvio del telefono AlarmManager perde gli allarmi:
 * qui si ripianifica la prossima fascia (CONTRACT sez. 11).
 */
class BootReceiver : BroadcastReceiver() {

    override fun onReceive(context: Context, intent: Intent) {
        if (intent.action != Intent.ACTION_BOOT_COMPLETED) return
        val appContext = context.applicationContext
        goAsyncIO {
            AlarmScheduler.ripianifica(appContext)
        }
    }
}
