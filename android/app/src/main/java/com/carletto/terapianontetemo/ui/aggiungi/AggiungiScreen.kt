package com.carletto.terapianontetemo.ui.aggiungi

import android.net.Uri
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.PickVisualMediaRequest
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.FilledTonalButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
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
import androidx.core.content.FileProvider
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import java.io.File
import kotlinx.coroutines.launch

/**
 * Schermata "Aggiungi da foto": stato-driven sull'AggiungiStato del ViewModel.
 * La Conferma è uno stato interno (nessuna route dedicata: il piano non è
 * serializzabile in nav args). Dopo conferma() chiama [onConfermato].
 */
@Composable
fun AggiungiScreen(
    viewModel: AggiungiViewModel,
    onConfermato: () -> Unit
) {
    val stato by viewModel.stato.collectAsStateWithLifecycle()
    val context = LocalContext.current
    val scope = rememberCoroutineScope()

    // Uri della foto scattata: rememberSaveable così il callback del launcher
    // ritrova l'Uri anche dopo ricreazione dell'Activity.
    var fotoUri by rememberSaveable { mutableStateOf<Uri?>(null) }
    var salvataggioInCorso by remember { mutableStateOf(false) }

    val scattaFoto = rememberLauncherForActivityResult(
        ActivityResultContracts.TakePicture()
    ) { riuscito ->
        val uri = fotoUri
        if (riuscito && uri != null) {
            viewModel.avvia(context, uri)
        }
    }

    val scegliDaGalleria = rememberLauncherForActivityResult(
        ActivityResultContracts.PickVisualMedia()
    ) { uri ->
        if (uri != null) {
            fotoUri = uri
            viewModel.avvia(context, uri)
        }
    }

    Scaffold { innerPadding ->
        Box(
            modifier = Modifier
                .fillMaxSize()
                .padding(innerPadding)
        ) {
            when (val s = stato) {
                AggiungiStato.Idle -> SceltaFotoContent(
                    onScattaFoto = {
                        val file = File(context.cacheDir, "foto_ricetta.jpg")
                        val uri = FileProvider.getUriForFile(
                            context,
                            "${context.packageName}.fileprovider",
                            file
                        )
                        fotoUri = uri
                        scattaFoto.launch(uri)
                    },
                    onScegliGalleria = {
                        scegliDaGalleria.launch(
                            PickVisualMediaRequest(
                                ActivityResultContracts.PickVisualMedia.ImageOnly
                            )
                        )
                    }
                )

                AggiungiStato.ServeChiave -> ChiaveApiCard(
                    onSalva = { chiave -> viewModel.salvaChiave(context, chiave) }
                )

                AggiungiStato.InCorso -> InCorsoContent()

                is AggiungiStato.Errore -> ErroreContent(
                    messaggio = s.messaggio,
                    onRiprova = viewModel::reset
                )

                is AggiungiStato.Conferma -> ConfermaContent(
                    piano = s.piano,
                    fotoUri = s.fotoUri,
                    onModifica = viewModel::aggiorna,
                    onToggleTerapia = { indice ->
                        viewModel.aggiorna(
                            s.piano.conTerapia(indice) { it.copy(inclusa = !it.inclusa) }
                        )
                    },
                    onConferma = {
                        if (!salvataggioInCorso) {
                            salvataggioInCorso = true
                            scope.launch {
                                try {
                                    viewModel.conferma()
                                    onConfermato()
                                } finally {
                                    salvataggioInCorso = false
                                }
                            }
                        }
                    }
                )
            }
        }
    }
}

@Composable
private fun SceltaFotoContent(
    onScattaFoto: () -> Unit,
    onScegliGalleria: () -> Unit
) {
    Column(
        modifier = Modifier
            .fillMaxSize()
            .padding(horizontal = 24.dp),
        verticalArrangement = Arrangement.Center,
        horizontalAlignment = Alignment.CenterHorizontally
    ) {
        Text(
            text = "Aggiungi da foto",
            style = MaterialTheme.typography.headlineMedium,
            fontWeight = FontWeight.Bold,
            textAlign = TextAlign.Center
        )
        Spacer(modifier = Modifier.height(12.dp))
        Text(
            text = "Fotografa la ricetta del medico oppure scegli una foto già scattata.",
            style = MaterialTheme.typography.titleLarge,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            textAlign = TextAlign.Center
        )
        Spacer(modifier = Modifier.height(32.dp))
        Button(
            onClick = onScattaFoto,
            modifier = Modifier
                .fillMaxWidth()
                .height(72.dp)
        ) {
            Text(
                text = "📷 Scatta foto",
                style = MaterialTheme.typography.headlineSmall,
                fontWeight = FontWeight.Bold
            )
        }
        Spacer(modifier = Modifier.height(16.dp))
        FilledTonalButton(
            onClick = onScegliGalleria,
            modifier = Modifier
                .fillMaxWidth()
                .height(72.dp)
        ) {
            Text(
                text = "🖼 Scegli dalla galleria",
                style = MaterialTheme.typography.headlineSmall,
                fontWeight = FontWeight.Bold
            )
        }
    }
}

@Composable
private fun ChiaveApiCard(onSalva: (String) -> Unit) {
    // La chiave resta solo in questo stato locale e in EncryptedSharedPreferences:
    // mai loggarla, mai mostrarla in chiaro.
    var chiave by remember { mutableStateOf("") }

    Column(
        modifier = Modifier
            .fillMaxSize()
            .padding(horizontal = 24.dp),
        verticalArrangement = Arrangement.Center
    ) {
        Card(modifier = Modifier.fillMaxWidth()) {
            Column(modifier = Modifier.padding(20.dp)) {
                Text(
                    text = "Serve la chiave OpenAI",
                    style = MaterialTheme.typography.headlineSmall,
                    fontWeight = FontWeight.Bold
                )
                Spacer(modifier = Modifier.height(8.dp))
                Text(
                    text = "Incolla qui la chiave (inizia con \"sk-\"). " +
                        "Viene salvata in modo sicuro solo su questo telefono.",
                    style = MaterialTheme.typography.titleMedium,
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
                    onClick = { onSalva(chiave) },
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
    }
}

@Composable
private fun InCorsoContent() {
    Column(
        modifier = Modifier
            .fillMaxSize()
            .padding(horizontal = 24.dp),
        verticalArrangement = Arrangement.Center,
        horizontalAlignment = Alignment.CenterHorizontally
    ) {
        CircularProgressIndicator(modifier = Modifier.size(72.dp))
        Spacer(modifier = Modifier.height(24.dp))
        Text(
            text = "Sto leggendo la ricetta…",
            style = MaterialTheme.typography.headlineSmall,
            fontWeight = FontWeight.Bold,
            textAlign = TextAlign.Center
        )
        Spacer(modifier = Modifier.height(8.dp))
        Text(
            text = "Può volerci fino a un minuto.",
            style = MaterialTheme.typography.titleLarge,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            textAlign = TextAlign.Center
        )
    }
}

@Composable
private fun ErroreContent(
    messaggio: String,
    onRiprova: () -> Unit
) {
    Column(
        modifier = Modifier
            .fillMaxSize()
            .padding(horizontal = 24.dp),
        verticalArrangement = Arrangement.Center,
        horizontalAlignment = Alignment.CenterHorizontally
    ) {
        Text(
            text = messaggio,
            style = MaterialTheme.typography.headlineSmall,
            color = MaterialTheme.colorScheme.error,
            textAlign = TextAlign.Center
        )
        Spacer(modifier = Modifier.height(24.dp))
        Button(
            onClick = onRiprova,
            modifier = Modifier
                .fillMaxWidth()
                .height(72.dp)
        ) {
            Text(
                text = "Riprova",
                style = MaterialTheme.typography.headlineSmall,
                fontWeight = FontWeight.Bold
            )
        }
    }
}
