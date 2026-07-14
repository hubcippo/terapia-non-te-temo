package com.carletto.terapianontetemo.allarme

import android.app.Service
import android.content.Context
import android.content.Intent
import android.content.pm.ServiceInfo
import android.media.AudioAttributes
import android.media.Ringtone
import android.media.RingtoneManager
import android.os.Build
import android.os.Handler
import android.os.IBinder
import android.os.Looper
import android.os.VibrationEffect
import android.os.Vibrator
import android.os.VibratorManager
import android.speech.tts.TextToSpeech
import android.speech.tts.UtteranceProgressListener
import androidx.core.app.ServiceCompat
import com.carletto.terapianontetemo.TerapiaApp
import com.carletto.terapianontetemo.data.entity.Forma
import java.time.Instant
import java.time.ZoneId
import java.util.Locale
import kotlin.math.abs
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

private const val GIORNO_MILLIS = 24 * 60 * 60 * 1000L

/**
 * Carica le voci dell'allarme per una fascia: dosi in ATTESA, nomi dei
 * farmaci e confronto con la dose di ieri (avviso cambio dose).
 * Condivisa tra SuonoAllarmeService (righe notifica + voce TTS)
 * e AlarmActivity (UI).
 */
internal suspend fun caricaVociAllarme(app: TerapiaApp, fascia: Long): List<VoceAllarme> {
    val repository = app.repository
    val dao = app.database.doseEventDao()
    val dosi = repository.dosiAttesaAllaFascia(fascia)
    val nomi = repository.nomiFarmaci(dosi.map { it.farmacoId }.distinct())

    // Cambio dose: per ogni farmaco della fascia confronta la dose di oggi
    // con quella di ieri (evento di ieri piu' vicino alla stessa ora,
    // per non confondere mattina e sera nelle titolazioni).
    val zona = ZoneId.systemDefault()
    val giornoFascia = Instant.ofEpochMilli(fascia).atZone(zona).toLocalDate()
    val oggi00 = giornoFascia.atStartOfDay(zona).toInstant().toEpochMilli()
    val ieri00 = giornoFascia.minusDays(1).atStartOfDay(zona).toInstant().toEpochMilli()

    return dosi.map { dose ->
        val ieri = dao.doseDiFarmacoInRange(dose.farmacoId, ieri00, oggi00)
        val riferimento = ieri.minByOrNull {
            abs(it.dataOraMillis - (dose.dataOraMillis - GIORNO_MILLIS))
        }
        VoceAllarme(
            nome = nomi[dose.farmacoId] ?: "Farmaco",
            dose = dose.dose,
            iniezione = dose.forma == Forma.INIEZIONE,
            cambioDose = riferimento != null && riferimento.dose != dose.dose
        )
    }
}

/**
 * Foreground service (mediaPlayback) che POSSIEDE suoneria, vibrazione e
 * voce TTS dell'allarme (CONTRACT sez. 13). Il suono NON dipende piu' da
 * notifica o full-screen: su HyperOS/silenzioso la notifica viene mutata,
 * ma la suoneria del service su stream ALARM suona comunque.
 *
 * Avviato da AlarmReceiver con startForegroundService (esente dalle
 * restrizioni background perche' il fire di setAlarmClock whitelista l'app).
 * ACTION_STOP ferma tutto: la notifica resta (STOP_FOREGROUND_DETACH),
 * la cancellano i chiamanti (Fatto/Rimanda).
 */
class SuonoAllarmeService : Service() {

    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.Main)
    private val handler = Handler(Looper.getMainLooper())

    private var ringtone: Ringtone? = null
    private var vibrator: Vibrator? = null
    private var tts: TextToSpeech? = null

    private var fraseDaDire: String? = null
    private var parlaRichiesto = false
    private var ttsPronto = false
    private var ttsGiaParlato = false
    private var fermato = false

    override fun onBind(intent: Intent?): IBinder? = null

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        if (intent?.action == ACTION_STOP) {
            fermaTutto()
            // DETACH: la notifica resta; la cancellano i chiamanti.
            ServiceCompat.stopForeground(this, ServiceCompat.STOP_FOREGROUND_DETACH)
            stopSelf()
            return START_NOT_STICKY
        }

        val prova = intent?.getBooleanExtra(AlarmScheduler.EXTRA_PROVA, false) ?: false
        val fascia = intent?.getLongExtra(AlarmScheduler.EXTRA_FASCIA, -1L) ?: -1L
        val diProva = prova || fascia <= 0L

        // startForeground SUBITO (obbligatorio) con la stessa notifica
        // full-screen condivisa; le righe vere arrivano dopo la lettura DB.
        val righeIniziali =
            if (diProva) listOf(riga(AlarmReceiver.VOCE_DI_PROVA)) else emptyList()
        ServiceCompat.startForeground(
            this,
            AlarmReceiver.NOTIFICA_ID,
            AlarmReceiver.costruisciNotifica(this, fascia, prova, righeIniziali),
            if (Build.VERSION.SDK_INT >= 29) {
                ServiceInfo.FOREGROUND_SERVICE_TYPE_MEDIA_PLAYBACK
            } else {
                0
            }
        )

        // Riparte pulito anche se richiamato per una nuova fascia.
        fermato = false
        handler.removeCallbacksAndMessages(null)
        parlaRichiesto = false
        ttsGiaParlato = false
        fraseDaDire = null

        avviaSuono()
        avviaVibrazione()
        if (tts == null) avviaTts()

        // Timeout di sicurezza: dopo 10 minuti di suono continuo ferma tutto
        // (la notifica resta; la dose diventera' SALTATA a 2h dalla fascia).
        handler.postDelayed({
            fermaTutto()
            ServiceCompat.stopForeground(this, ServiceCompat.STOP_FOREGROUND_DETACH)
            stopSelf()
        }, TIMEOUT_SUONO_MILLIS)

        if (diProva) {
            preparaVoce(listOf(AlarmReceiver.VOCE_DI_PROVA))
        } else {
            scope.launch {
                val app = applicationContext as TerapiaApp
                val voci = withContext(Dispatchers.IO) { caricaVociAllarme(app, fascia) }
                if (fermato || voci.isEmpty()) return@launch
                // Aggiorna la notifica (stesso id) con le righe vere.
                AlarmReceiver.mostraNotifica(
                    context = this@SuonoAllarmeService,
                    fasciaMillis = fascia,
                    prova = false,
                    righe = voci.map { riga(it) }
                )
                preparaVoce(voci)
            }
        }
        return START_NOT_STICKY
    }

    override fun onDestroy() {
        fermaTutto()
        tts?.shutdown()
        tts = null
        scope.cancel()
        super.onDestroy()
    }

    private fun fermaTutto() {
        fermato = true
        handler.removeCallbacksAndMessages(null)
        ringtone?.stop()
        vibrator?.cancel()
        tts?.stop()
    }

    // --- Suono e vibrazione ---

    private fun avviaSuono() {
        if (ringtone?.isPlaying == true) return
        val uri = RingtoneManager.getDefaultUri(RingtoneManager.TYPE_ALARM) ?: return
        ringtone = RingtoneManager.getRingtone(this, uri)?.apply {
            audioAttributes = AudioAttributes.Builder()
                .setUsage(AudioAttributes.USAGE_ALARM)
                .setContentType(AudioAttributes.CONTENT_TYPE_SONIFICATION)
                .build()
            if (Build.VERSION.SDK_INT >= 28) {
                isLooping = true
            }
            // Sotto API 28 niente loop nativo: si accetta la riproduzione singola.
            play()
        }
    }

    private fun fermaSuono() {
        ringtone?.stop()
    }

    private fun avviaVibrazione() {
        val v = if (Build.VERSION.SDK_INT >= 31) {
            (getSystemService(Context.VIBRATOR_MANAGER_SERVICE) as VibratorManager)
                .defaultVibrator
        } else {
            @Suppress("DEPRECATION")
            getSystemService(Context.VIBRATOR_SERVICE) as Vibrator
        }
        vibrator = v
        val onda = VibrationEffect.createWaveform(longArrayOf(0, 800, 400, 800, 800), 0)
        v.vibrate(onda)
    }

    // --- Voce (TTS) ---

    private fun avviaTts() {
        tts = TextToSpeech(this) { status ->
            if (status != TextToSpeech.SUCCESS) return@TextToSpeech
            val motore = tts ?: return@TextToSpeech
            val lingua = motore.setLanguage(Locale.ITALIAN)
            if (lingua == TextToSpeech.LANG_MISSING_DATA ||
                lingua == TextToSpeech.LANG_NOT_SUPPORTED
            ) {
                // Italiano non disponibile: nessuna voce, nessun crash.
                return@TextToSpeech
            }
            motore.setAudioAttributes(
                AudioAttributes.Builder()
                    .setUsage(AudioAttributes.USAGE_ALARM)
                    .setContentType(AudioAttributes.CONTENT_TYPE_SPEECH)
                    .build()
            )
            motore.setOnUtteranceProgressListener(object : UtteranceProgressListener() {
                override fun onStart(utteranceId: String?) = Unit
                override fun onDone(utteranceId: String?) = riprendiSuono()
                @Deprecated("Deprecated in Java")
                override fun onError(utteranceId: String?) = riprendiSuono()
            })
            ttsPronto = true
            handler.post { parlaSePossibile() }
        }
    }

    private fun preparaVoce(voci: List<VoceAllarme>) {
        fraseDaDire = fraseTts(voci)
        handler.postDelayed({
            parlaRichiesto = true
            parlaSePossibile()
        }, RITARDO_TTS_MILLIS)
    }

    private fun parlaSePossibile() {
        val frase = fraseDaDire ?: return
        if (fermato || !ttsPronto || !parlaRichiesto || ttsGiaParlato) return
        ttsGiaParlato = true
        // Pausa la suoneria mentre parla, cosi' la voce si sente bene;
        // riparte in onDone/onError del listener.
        fermaSuono()
        tts?.speak(frase, TextToSpeech.QUEUE_FLUSH, null, "allarme_terapia_tts")
    }

    /** Chiamata dal listener TTS (thread in background): torna sul main. */
    private fun riprendiSuono() {
        handler.post {
            if (!fermato) avviaSuono()
        }
    }

    companion object {
        /** Ferma suono/vibrazione/TTS e chiude il service (notifica ai chiamanti). */
        const val ACTION_STOP = "com.carletto.terapianontetemo.SUONO_STOP"

        private const val RITARDO_TTS_MILLIS = 1_500L
        private const val TIMEOUT_SUONO_MILLIS = 10 * 60 * 1000L

        /** Manda ACTION_STOP al service, ignorando gli errori se e' gia' fermo. */
        fun ferma(context: Context) {
            try {
                context.startService(
                    Intent(context, SuonoAllarmeService::class.java)
                        .setAction(ACTION_STOP)
                )
            } catch (e: Exception) {
                // App in background senza esenzione o service gia' fermo: ignora.
            }
        }
    }
}
