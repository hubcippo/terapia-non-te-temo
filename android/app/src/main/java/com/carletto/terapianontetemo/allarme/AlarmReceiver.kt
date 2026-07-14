package com.carletto.terapianontetemo.allarme

import android.Manifest
import android.app.PendingIntent
import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.os.Build
import androidx.core.app.NotificationCompat
import androidx.core.app.NotificationManagerCompat
import androidx.core.content.ContextCompat
import com.carletto.terapianontetemo.TerapiaApp
import com.carletto.terapianontetemo.data.entity.Forma

/**
 * Riceve l'allarme di AlarmManager e mostra la notifica full-screen
 * (CONTRACT sez. 11). MAI startActivity diretto: su Android 15/16 il
 * full-screen ad app chiusa passa SOLO da fullScreenIntent della notifica.
 */
class AlarmReceiver : BroadcastReceiver() {

    override fun onReceive(context: Context, intent: Intent) {
        val appContext = context.applicationContext
        val prova = intent.getBooleanExtra(AlarmScheduler.EXTRA_PROVA, false)
        val fascia = intent.getLongExtra(AlarmScheduler.EXTRA_FASCIA, -1L)

        goAsyncIO {
            if (prova) {
                // Prova: solo la notifica, nessun accesso al DB.
                mostraNotifica(
                    context = appContext,
                    fasciaMillis = -1L,
                    prova = true,
                    righe = listOf(riga(VOCE_DI_PROVA))
                )
                return@goAsyncIO
            }

            val repository = (appContext as TerapiaApp).repository
            val dosi = repository.dosiAttesaAllaFascia(fascia)
            if (dosi.isEmpty()) {
                // Nulla da suonare (gia' prese/saltate): passa oltre.
                // Le dosi scadute le marca gia' ripianifica.
                AlarmScheduler.ripianifica(appContext, escludiFinoAMillis = fascia)
                return@goAsyncIO
            }

            val nomi = repository.nomiFarmaci(dosi.map { it.farmacoId }.distinct())
            val righe = dosi.map { dose ->
                riga(
                    VoceAllarme(
                        nome = nomi[dose.farmacoId] ?: "Farmaco",
                        dose = dose.dose,
                        iniezione = dose.forma == Forma.INIEZIONE
                    )
                )
            }
            mostraNotifica(appContext, fascia, prova = false, righe = righe)

            // Pianifica la fascia SUCCESSIVA: quella corrente resta in ATTESA
            // finche' Carlo non preme Fatto/Rimanda, e non va rischedulata.
            AlarmScheduler.ripianifica(appContext, escludiFinoAMillis = fascia)
        }
    }

    companion object {

        /** Id fisso della notifica di allarme (CONTRACT sez. 11). */
        const val NOTIFICA_ID = 2001

        /** Voce fittizia per l'allarme di prova (nessun DB). */
        val VOCE_DI_PROVA = VoceAllarme(
            nome = "Farmaco di prova",
            dose = "1 compressa",
            iniezione = false
        )

        private const val REQUEST_FULL_SCREEN = 2001
        private const val REQUEST_AZIONE_FATTO = 3001
        private const val REQUEST_AZIONE_RIMANDA = 3002

        /** Costruisce e pubblica la notifica di allarme con fullScreenIntent. */
        fun mostraNotifica(
            context: Context,
            fasciaMillis: Long,
            prova: Boolean,
            righe: List<String>
        ) {
            val fullScreenIntent = Intent(context, AlarmActivity::class.java)
                .putExtra(AlarmScheduler.EXTRA_FASCIA, fasciaMillis)
                .putExtra(AlarmScheduler.EXTRA_PROVA, prova)
                .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
            val fullScreenPending = PendingIntent.getActivity(
                context,
                REQUEST_FULL_SCREEN,
                fullScreenIntent,
                PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_UPDATE_CURRENT
            )

            val fattoPending = PendingIntent.getBroadcast(
                context,
                REQUEST_AZIONE_FATTO,
                Intent(context, AzioneReceiver::class.java)
                    .setAction(AzioneReceiver.ACTION_FATTO)
                    .putExtra(AlarmScheduler.EXTRA_FASCIA, fasciaMillis)
                    .putExtra(AlarmScheduler.EXTRA_PROVA, prova),
                PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_UPDATE_CURRENT
            )
            val rimandaPending = PendingIntent.getBroadcast(
                context,
                REQUEST_AZIONE_RIMANDA,
                Intent(context, AzioneReceiver::class.java)
                    .setAction(AzioneReceiver.ACTION_RIMANDA)
                    .putExtra(AlarmScheduler.EXTRA_FASCIA, fasciaMillis)
                    .putExtra(AlarmScheduler.EXTRA_PROVA, prova),
                PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_UPDATE_CURRENT
            )

            val testo = righe.joinToString(separator = "\n")
            val notifica = NotificationCompat.Builder(context, TerapiaApp.CANALE_ALLARME)
                .setSmallIcon(android.R.drawable.ic_lock_idle_alarm)
                .setContentTitle("È ora delle medicine")
                .setContentText(righe.joinToString(separator = " • "))
                .setStyle(NotificationCompat.BigTextStyle().bigText(testo))
                .setCategory(NotificationCompat.CATEGORY_ALARM)
                .setPriority(NotificationCompat.PRIORITY_MAX)
                .setOngoing(true)
                .setAutoCancel(false)
                .setContentIntent(fullScreenPending)
                .setFullScreenIntent(fullScreenPending, true)
                .addAction(0, "✅ Fatto", fattoPending)
                .addAction(0, "⏰ Rimanda 10 min", rimandaPending)
                .build()

            val permessa = Build.VERSION.SDK_INT < 33 ||
                ContextCompat.checkSelfPermission(
                    context,
                    Manifest.permission.POST_NOTIFICATIONS
                ) == PackageManager.PERMISSION_GRANTED
            if (permessa) {
                NotificationManagerCompat.from(context).notify(NOTIFICA_ID, notifica)
            }
        }
    }
}
