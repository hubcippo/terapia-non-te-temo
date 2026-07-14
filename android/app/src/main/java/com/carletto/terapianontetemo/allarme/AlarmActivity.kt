package com.carletto.terapianontetemo.allarme

import android.app.KeyguardManager
import android.content.Context
import android.content.Intent
import android.media.AudioAttributes
import android.media.Ringtone
import android.media.RingtoneManager
import android.os.Build
import android.os.Bundle
import android.os.Handler
import android.os.Looper
import android.os.VibrationEffect
import android.os.Vibrator
import android.os.VibratorManager
import android.speech.tts.TextToSpeech
import android.speech.tts.UtteranceProgressListener
import android.view.WindowManager
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.mutableStateOf
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.core.app.NotificationManagerCompat
import androidx.lifecycle.lifecycleScope
import com.carletto.terapianontetemo.TerapiaApp
import com.carletto.terapianontetemo.data.TerapiaRepository
import com.carletto.terapianontetemo.data.entity.Forma
import com.carletto.terapianontetemo.ui.theme.TerapiaTheme
import com.carletto.terapianontetemo.util.formattaOra
import java.time.Instant
import java.time.ZoneId
import java.util.Locale
import kotlin.math.abs
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

/**
 * Schermata di allarme a tutto schermo (CONTRACT sez. 11): si mostra sopra
 * il blocco schermo, suona la suoneria di allarme in loop, vibra e dopo
 * ~1,5 s legge ad alta voce le medicine con il TTS in italiano.
 * Viene lanciata SOLO dal fullScreenIntent della notifica di AlarmReceiver.
 */
class AlarmActivity : ComponentActivity() {

    private var fascia: Long = -1L
    private var prova: Boolean = false

    private var ringtone: Ringtone? = null
    private var vibrator: Vibrator? = null
    private var tts: TextToSpeech? = null
    private val handler = Handler(Looper.getMainLooper())

    private var fraseDaDire: String? = null
    private var parlaRichiesto = false
    private var ttsPronto = false
    private var ttsGiaParlato = false
    private var inPrimoPiano = false

    private val vociState = mutableStateOf<List<VoceAllarme>>(emptyList())

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        if (Build.VERSION.SDK_INT >= 27) {
            setShowWhenLocked(true)
            setTurnScreenOn(true)
        } else {
            @Suppress("DEPRECATION")
            window.addFlags(
                WindowManager.LayoutParams.FLAG_SHOW_WHEN_LOCKED or
                    WindowManager.LayoutParams.FLAG_TURN_SCREEN_ON
            )
        }
        getSystemService(KeyguardManager::class.java)
            ?.requestDismissKeyguard(this, null)

        leggiExtras(intent)
        avviaTts()
        caricaContenuto()

        setContent {
            TerapiaTheme {
                AlarmContent(
                    ora = oraTesto(),
                    voci = vociState.value,
                    onFatto = ::onFatto,
                    onRimanda = ::onRimanda
                )
            }
        }
    }

    override fun onNewIntent(intent: Intent) {
        super.onNewIntent(intent)
        setIntent(intent)
        // Nuova fascia mentre l'allarme era gia' aperto: ricarica tutto.
        handler.removeCallbacksAndMessages(null)
        parlaRichiesto = false
        ttsGiaParlato = false
        leggiExtras(intent)
        caricaContenuto()
    }

    override fun onStart() {
        super.onStart()
        inPrimoPiano = true
        avviaSuono()
        avviaVibrazione()
    }

    override fun onStop() {
        inPrimoPiano = false
        fermaSuono()
        fermaVibrazione()
        super.onStop()
    }

    override fun onDestroy() {
        handler.removeCallbacksAndMessages(null)
        fermaSuono()
        fermaVibrazione()
        tts?.stop()
        tts?.shutdown()
        tts = null
        super.onDestroy()
    }

    private fun leggiExtras(intent: Intent) {
        fascia = intent.getLongExtra(AlarmScheduler.EXTRA_FASCIA, -1L)
        prova = intent.getBooleanExtra(AlarmScheduler.EXTRA_PROVA, false)
    }

    private fun oraTesto(): String =
        formattaOra(if (fascia > 0L) fascia else System.currentTimeMillis())

    // --- Contenuto ---

    private fun caricaContenuto() {
        if (prova || fascia <= 0L) {
            vociState.value = listOf(AlarmReceiver.VOCE_DI_PROVA)
            preparaVoce()
            return
        }
        lifecycleScope.launch {
            vociState.value = withContext(Dispatchers.IO) { leggiDalDb() }
            preparaVoce()
        }
    }

    private suspend fun leggiDalDb(): List<VoceAllarme> {
        val app = applicationContext as TerapiaApp
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

    private fun fermaVibrazione() {
        vibrator?.cancel()
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

    private fun preparaVoce() {
        fraseDaDire = fraseTts(vociState.value)
        handler.postDelayed({
            parlaRichiesto = true
            parlaSePossibile()
        }, RITARDO_TTS_MILLIS)
    }

    private fun parlaSePossibile() {
        val frase = fraseDaDire ?: return
        if (!ttsPronto || !parlaRichiesto || ttsGiaParlato) return
        ttsGiaParlato = true
        // Pausa la suoneria mentre parla, cosi' la voce si sente bene;
        // riparte in onDone/onError del listener.
        fermaSuono()
        tts?.speak(frase, TextToSpeech.QUEUE_FLUSH, null, "allarme_terapia_tts")
    }

    /** Chiamata dal listener TTS (thread in background): torna sul main. */
    private fun riprendiSuono() {
        handler.post {
            if (inPrimoPiano) avviaSuono()
        }
    }

    // --- Azioni ---

    private fun onFatto() {
        chiudi { repo -> repo.segnaPresoFascia(fascia, System.currentTimeMillis()) }
    }

    private fun onRimanda() {
        chiudi { repo ->
            repo.rimandaFascia(
                fascia = fascia,
                nuovaOra = fascia + AzioneReceiver.RIMANDO_MILLIS,
                ts = System.currentTimeMillis()
            )
        }
    }

    private fun chiudi(azione: suspend (TerapiaRepository) -> Unit) {
        handler.removeCallbacksAndMessages(null)
        tts?.stop()
        fermaSuono()
        fermaVibrazione()
        lifecycleScope.launch {
            NotificationManagerCompat.from(applicationContext)
                .cancel(AlarmReceiver.NOTIFICA_ID)
            if (!prova && fascia > 0L) {
                val app = applicationContext as TerapiaApp
                withContext(Dispatchers.IO) {
                    azione(app.repository)
                    AlarmScheduler.ripianifica(app)
                }
            }
            finish()
        }
    }

    private companion object {
        const val RITARDO_TTS_MILLIS = 1_500L
        const val GIORNO_MILLIS = 24 * 60 * 60 * 1000L
    }
}

@Composable
private fun AlarmContent(
    ora: String,
    voci: List<VoceAllarme>,
    onFatto: () -> Unit,
    onRimanda: () -> Unit
) {
    Surface(
        modifier = Modifier.fillMaxSize(),
        color = MaterialTheme.colorScheme.primary,
        contentColor = MaterialTheme.colorScheme.onPrimary
    ) {
        Column(
            modifier = Modifier
                .fillMaxSize()
                .verticalScroll(rememberScrollState())
                .padding(24.dp),
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.Center
        ) {
            Text(
                text = ora,
                style = MaterialTheme.typography.displayLarge,
                fontWeight = FontWeight.Bold,
                textAlign = TextAlign.Center
            )
            Spacer(modifier = Modifier.height(8.dp))
            Text(
                text = "È ora delle medicine",
                style = MaterialTheme.typography.headlineMedium,
                textAlign = TextAlign.Center
            )
            Spacer(modifier = Modifier.height(24.dp))

            voci.forEach { voce ->
                Text(
                    text = etichetta(voce),
                    style = MaterialTheme.typography.displayMedium,
                    fontWeight = FontWeight.Bold,
                    textAlign = TextAlign.Center
                )
                Text(
                    text = voce.dose,
                    style = MaterialTheme.typography.headlineLarge,
                    textAlign = TextAlign.Center
                )
                Spacer(modifier = Modifier.height(16.dp))
            }

            voci.filter { it.cambioDose }.forEach { voce ->
                Surface(
                    color = MaterialTheme.colorScheme.tertiaryContainer,
                    contentColor = MaterialTheme.colorScheme.onTertiaryContainer,
                    shape = MaterialTheme.shapes.medium
                ) {
                    Text(
                        text = "⚠️ Attenzione: da oggi la dose di ${voce.nome} cambia",
                        style = MaterialTheme.typography.headlineSmall,
                        fontWeight = FontWeight.Bold,
                        textAlign = TextAlign.Center,
                        modifier = Modifier.padding(16.dp)
                    )
                }
                Spacer(modifier = Modifier.height(16.dp))
            }

            Spacer(modifier = Modifier.height(16.dp))
            Button(
                onClick = onFatto,
                modifier = Modifier
                    .fillMaxWidth()
                    .heightIn(min = 88.dp),
                colors = ButtonDefaults.buttonColors(
                    containerColor = MaterialTheme.colorScheme.onPrimary,
                    contentColor = MaterialTheme.colorScheme.primary
                )
            ) {
                Text(
                    text = "✅ Fatto",
                    style = MaterialTheme.typography.headlineMedium,
                    fontWeight = FontWeight.Bold
                )
            }
            Spacer(modifier = Modifier.height(16.dp))
            Button(
                onClick = onRimanda,
                modifier = Modifier
                    .fillMaxWidth()
                    .heightIn(min = 88.dp),
                colors = ButtonDefaults.buttonColors(
                    containerColor = MaterialTheme.colorScheme.primaryContainer,
                    contentColor = MaterialTheme.colorScheme.onPrimaryContainer
                )
            ) {
                Text(
                    text = "⏰ Rimanda 10 minuti",
                    style = MaterialTheme.typography.headlineMedium,
                    fontWeight = FontWeight.Bold
                )
            }
        }
    }
}
