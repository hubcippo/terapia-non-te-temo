package com.carletto.terapianontetemo.ui.onboarding

import android.Manifest
import android.content.Intent
import android.content.pm.PackageManager
import android.net.Uri
import android.os.Build
import android.provider.Settings
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.FilledTonalButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.key
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.core.content.ContextCompat
import com.carletto.terapianontetemo.ai.KeyStore
import com.carletto.terapianontetemo.allarme.AlarmScheduler
import com.carletto.terapianontetemo.util.Preferenze
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

private const val ULTIMO_PASSO = 5

/**
 * Guida iniziale a 5 passi (CONTRACT sez. 14): benvenuto, chiave API,
 * notifiche, batteria, allarme di prova. Stato locale (rememberSaveable),
 * NIENTE ViewModel. "Avanti" e "Salta" sempre presenti: Carlo non resta
 * mai bloccato. L'ordine dei passi e' vincolante (la prova sta DOPO le
 * notifiche). Alla fine salva il flag e naviga a home.
 */
@Composable
fun OnboardingScreen(onFine: () -> Unit) {
    val context = LocalContext.current
    var passo by rememberSaveable { mutableIntStateOf(1) }

    val avanti: () -> Unit = {
        if (passo >= ULTIMO_PASSO) {
            Preferenze.segnaOnboardingFatto(context)
            onFine()
        } else {
            passo++
        }
    }

    Scaffold { innerPadding ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(innerPadding)
                .padding(horizontal = 24.dp)
        ) {
            Spacer(modifier = Modifier.height(16.dp))
            Text(
                text = "Passo $passo di $ULTIMO_PASSO",
                style = MaterialTheme.typography.titleLarge,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
            Spacer(modifier = Modifier.height(16.dp))

            // key(passo): ogni passo riparte con il proprio scroll da cima.
            key(passo) {
                Column(
                    modifier = Modifier
                        .weight(1f)
                        .verticalScroll(rememberScrollState())
                ) {
                    when (passo) {
                        1 -> PassoBenvenuto()
                        2 -> PassoChiave()
                        3 -> PassoNotifiche()
                        4 -> PassoBatteria()
                        else -> PassoProva()
                    }
                    Spacer(modifier = Modifier.height(16.dp))
                }
            }

            Row(modifier = Modifier.fillMaxWidth()) {
                FilledTonalButton(
                    onClick = avanti,
                    modifier = Modifier
                        .weight(1f)
                        .height(72.dp)
                ) {
                    Text(
                        text = "Salta",
                        style = MaterialTheme.typography.headlineSmall
                    )
                }
                Spacer(modifier = Modifier.width(16.dp))
                Button(
                    onClick = avanti,
                    modifier = Modifier
                        .weight(1f)
                        .height(72.dp)
                ) {
                    Text(
                        text = if (passo >= ULTIMO_PASSO) "Fatto!" else "Avanti",
                        style = MaterialTheme.typography.headlineSmall,
                        fontWeight = FontWeight.Bold
                    )
                }
            }
            Spacer(modifier = Modifier.height(24.dp))
        }
    }
}

@Composable
private fun TitoloPasso(testo: String) {
    Text(
        text = testo,
        style = MaterialTheme.typography.headlineMedium,
        fontWeight = FontWeight.Bold
    )
    Spacer(modifier = Modifier.height(12.dp))
}

// --- Passo 1: benvenuto ---

@Composable
private fun PassoBenvenuto() {
    Column(
        modifier = Modifier.fillMaxWidth(),
        horizontalAlignment = Alignment.CenterHorizontally
    ) {
        Text(
            text = "💊",
            style = MaterialTheme.typography.displayLarge,
            textAlign = TextAlign.Center
        )
        Spacer(modifier = Modifier.height(16.dp))
        Text(
            text = "Ciao Carlo! 3 passi e siamo pronti.",
            style = MaterialTheme.typography.headlineMedium,
            fontWeight = FontWeight.Bold,
            textAlign = TextAlign.Center
        )
        Spacer(modifier = Modifier.height(12.dp))
        Text(
            text = "Prepariamo il telefono perché gli allarmi delle medicine suonino sempre.",
            style = MaterialTheme.typography.headlineSmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            textAlign = TextAlign.Center
        )
    }
}

// --- Passo 2: chiave API ---

@Composable
private fun PassoChiave() {
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    var chiave by remember { mutableStateOf("") }
    var chiaveSalvata by rememberSaveable { mutableStateOf(false) }

    // Lettura della chiave su IO: EncryptedSharedPreferences apre in ritardo.
    LaunchedEffect(Unit) {
        val presente = withContext(Dispatchers.IO) { KeyStore.getApiKey(context) != null }
        if (presente) chiaveSalvata = true
    }

    TitoloPasso("La chiave per leggere le ricette")
    if (chiaveSalvata) {
        Text(
            text = "Chiave già salvata ✅",
            style = MaterialTheme.typography.headlineSmall,
            fontWeight = FontWeight.Bold
        )
    } else {
        Text(
            text = "Incolla la chiave OpenAI (inizia con \"sk-\"). " +
                "Viene salvata in modo sicuro solo su questo telefono.",
            style = MaterialTheme.typography.titleLarge,
            color = MaterialTheme.colorScheme.onSurfaceVariant
        )
        Spacer(modifier = Modifier.height(16.dp))
        OutlinedTextField(
            value = chiave,
            onValueChange = { chiave = it },
            modifier = Modifier.fillMaxWidth(),
            label = { Text("Chiave API") },
            singleLine = true,
            visualTransformation = PasswordVisualTransformation(),
            textStyle = MaterialTheme.typography.titleLarge
        )
        Spacer(modifier = Modifier.height(16.dp))
        Button(
            onClick = {
                val daSalvare = chiave
                scope.launch {
                    withContext(Dispatchers.IO) { KeyStore.setApiKey(context, daSalvare) }
                    chiaveSalvata = true
                }
            },
            enabled = chiave.isNotBlank(),
            modifier = Modifier
                .fillMaxWidth()
                .height(64.dp)
        ) {
            Text(
                text = "Salva",
                style = MaterialTheme.typography.headlineSmall,
                fontWeight = FontWeight.Bold
            )
        }
    }
}

// --- Passo 3: notifiche ---

@Composable
private fun PassoNotifiche() {
    val context = LocalContext.current
    var concesso by remember {
        mutableStateOf(
            Build.VERSION.SDK_INT < 33 ||
                ContextCompat.checkSelfPermission(
                    context,
                    Manifest.permission.POST_NOTIFICATIONS
                ) == PackageManager.PERMISSION_GRANTED
        )
    }
    val richiestaPermesso = rememberLauncherForActivityResult(
        ActivityResultContracts.RequestPermission()
    ) { ok -> if (ok) concesso = true }

    TitoloPasso("Le notifiche degli allarmi")
    Text(
        text = "Senza le notifiche l'allarme delle medicine non può comparire.",
        style = MaterialTheme.typography.titleLarge,
        color = MaterialTheme.colorScheme.onSurfaceVariant
    )
    Spacer(modifier = Modifier.height(16.dp))
    if (concesso) {
        Text(
            text = "Già a posto ✅",
            style = MaterialTheme.typography.headlineSmall,
            fontWeight = FontWeight.Bold
        )
    } else {
        Button(
            onClick = { richiestaPermesso.launch(Manifest.permission.POST_NOTIFICATIONS) },
            modifier = Modifier
                .fillMaxWidth()
                .height(72.dp)
        ) {
            Text(
                text = "Consenti notifiche",
                style = MaterialTheme.typography.headlineSmall,
                fontWeight = FontWeight.Bold
            )
        }
    }
}

// --- Passo 4: batteria ---

@Composable
private fun PassoBatteria() {
    val context = LocalContext.current

    TitoloPasso("Il risparmio batteria")
    Text(
        text = "Alcuni telefoni spengono gli allarmi per risparmiare batteria: " +
            "escludiamo l'app così suona sempre.",
        style = MaterialTheme.typography.titleLarge,
        color = MaterialTheme.colorScheme.onSurfaceVariant
    )
    Spacer(modifier = Modifier.height(16.dp))
    Button(
        onClick = {
            try {
                context.startActivity(
                    Intent(Settings.ACTION_REQUEST_IGNORE_BATTERY_OPTIMIZATIONS)
                        .setData(Uri.parse("package:${context.packageName}"))
                )
            } catch (e: Exception) {
                try {
                    context.startActivity(
                        Intent(Settings.ACTION_IGNORE_BATTERY_OPTIMIZATION_SETTINGS)
                    )
                } catch (e2: Exception) {
                    // Nessuna schermata batteria su questo device: pazienza.
                }
            }
        },
        modifier = Modifier
            .fillMaxWidth()
            .height(72.dp)
    ) {
        Text(
            text = "Escludi dal risparmio batteria",
            style = MaterialTheme.typography.titleLarge,
            fontWeight = FontWeight.Bold
        )
    }
    Spacer(modifier = Modifier.height(24.dp))

    val produttore = Build.MANUFACTURER.lowercase()
    GuidaProdutoreCard(
        titolo = "Samsung (One UI)",
        passi = listOf(
            "Impostazioni > Batteria > Limiti uso in background",
            "Metti l'app tra le \"mai sospese\"",
            "Controlla che le notifiche non siano silenziose"
        ),
        eIlTuoTelefono = produttore.contains("samsung")
    )
    Spacer(modifier = Modifier.height(12.dp))
    GuidaProdutoreCard(
        titolo = "Xiaomi/Redmi (HyperOS)",
        passi = listOf(
            "Consenti \"Avvio automatico\" all'app",
            "Nelle Autorizzazioni attiva \"Mostra sulla schermata di blocco\"",
            "Controlla che le notifiche non siano silenziose"
        ),
        eIlTuoTelefono = produttore.contains("xiaomi")
    )
}

@Composable
private fun GuidaProdutoreCard(
    titolo: String,
    passi: List<String>,
    eIlTuoTelefono: Boolean
) {
    Card(
        modifier = Modifier.fillMaxWidth(),
        border = if (eIlTuoTelefono) {
            BorderStroke(3.dp, MaterialTheme.colorScheme.primary)
        } else {
            null
        },
        colors = CardDefaults.cardColors(
            containerColor = MaterialTheme.colorScheme.surfaceVariant,
            contentColor = MaterialTheme.colorScheme.onSurfaceVariant
        )
    ) {
        Column(modifier = Modifier.padding(20.dp)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Text(
                    text = titolo,
                    style = MaterialTheme.typography.titleLarge,
                    fontWeight = FontWeight.Bold,
                    modifier = Modifier.weight(1f)
                )
                if (eIlTuoTelefono) {
                    Text(
                        text = "il tuo telefono",
                        style = MaterialTheme.typography.titleMedium,
                        fontWeight = FontWeight.Bold,
                        color = MaterialTheme.colorScheme.primary
                    )
                }
            }
            Spacer(modifier = Modifier.height(8.dp))
            passi.forEach { riga ->
                Text(
                    text = "• $riga",
                    style = MaterialTheme.typography.titleMedium,
                    modifier = Modifier.padding(vertical = 2.dp)
                )
            }
        }
    }
}

// --- Passo 5: allarme di prova (DOPO il passo notifiche, ordine vincolante) ---

@Composable
private fun PassoProva() {
    val context = LocalContext.current
    var programmata by rememberSaveable { mutableStateOf(false) }

    TitoloPasso("Proviamo l'allarme")
    Button(
        onClick = {
            AlarmScheduler.programmaProva(context)
            programmata = true
        },
        modifier = Modifier
            .fillMaxWidth()
            .height(72.dp)
    ) {
        Text(
            text = "🔔 Suona un allarme di prova tra 1 minuto",
            style = MaterialTheme.typography.titleLarge,
            fontWeight = FontWeight.Bold,
            textAlign = TextAlign.Center
        )
    }
    Spacer(modifier = Modifier.height(16.dp))
    Text(
        text = "Blocca lo schermo e aspetta: deve suonare e parlare.",
        style = MaterialTheme.typography.headlineSmall,
        color = MaterialTheme.colorScheme.onSurfaceVariant
    )
    if (programmata) {
        Spacer(modifier = Modifier.height(12.dp))
        Text(
            text = "Allarme programmato ✅",
            style = MaterialTheme.typography.titleLarge,
            fontWeight = FontWeight.Bold
        )
    }
}
