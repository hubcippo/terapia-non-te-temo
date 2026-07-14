package com.carletto.terapianontetemo.allarme

import android.app.KeyguardManager
import android.content.Intent
import android.os.Build
import android.os.Bundle
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
import com.carletto.terapianontetemo.ui.theme.TerapiaTheme
import com.carletto.terapianontetemo.util.formattaOra
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

/**
 * Schermata di allarme a tutto schermo, SOLO UI (CONTRACT sez. 13):
 * si mostra sopra il blocco schermo con le medicine da prendere.
 * Suoneria, vibrazione e voce TTS vivono in SuonoAllarmeService;
 * qui su Fatto/Rimanda si manda ACTION_STOP al service.
 * Viene lanciata SOLO dal fullScreenIntent della notifica.
 */
class AlarmActivity : ComponentActivity() {

    private var fascia: Long = -1L
    private var prova: Boolean = false

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
        leggiExtras(intent)
        caricaContenuto()
    }

    private fun leggiExtras(intent: Intent) {
        fascia = intent.getLongExtra(AlarmScheduler.EXTRA_FASCIA, -1L)
        prova = intent.getBooleanExtra(AlarmScheduler.EXTRA_PROVA, false)
    }

    private fun oraTesto(): String =
        formattaOra(if (fascia > 0L) fascia else System.currentTimeMillis())

    private fun caricaContenuto() {
        if (prova || fascia <= 0L) {
            vociState.value = listOf(AlarmReceiver.VOCE_DI_PROVA)
            return
        }
        lifecycleScope.launch {
            val app = applicationContext as TerapiaApp
            vociState.value = withContext(Dispatchers.IO) {
                caricaVociAllarme(app, fascia)
            }
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
        // Prima di tutto: zittisce suoneria/vibrazione/TTS del service.
        SuonoAllarmeService.ferma(this)
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
