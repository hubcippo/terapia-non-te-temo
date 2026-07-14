package com.carletto.terapianontetemo.ui.terapie

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.carletto.terapianontetemo.data.entity.Fase
import com.carletto.terapianontetemo.data.entity.SchemaDose
import com.carletto.terapianontetemo.util.etichettaFarmaco
import com.carletto.terapianontetemo.util.etichettaFascia
import kotlinx.coroutines.launch

/**
 * Dettaglio di una terapia attiva (CONTRACT sez. 14): fasi leggibili e
 * bottone rosso "Ferma questa terapia" con conferma. Il DELETE tocca
 * SOLO le dosi in ATTESA: le dosi passate non si toccano MAI.
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun DettaglioTerapiaScreen(
    viewModel: TerapieViewModel,
    farmacoId: Long,
    onIndietro: () -> Unit
) {
    val terapie by viewModel.terapie.collectAsStateWithLifecycle()
    val fasiFlow = remember(farmacoId) { viewModel.fasi(farmacoId) }
    val fasi by fasiFlow.collectAsStateWithLifecycle(initialValue = emptyList())
    val scope = rememberCoroutineScope()

    var chiediConferma by remember { mutableStateOf(false) }
    var fermataInCorso by remember { mutableStateOf(false) }

    // Il nome resta visibile anche mentre la terapia sparisce dalle attive
    // (dopo Ferma il farmaco esce da terapieAttive prima del nav back).
    var titolo by remember { mutableStateOf("Farmaco") }
    terapie.firstOrNull { it.id == farmacoId }?.let {
        titolo = etichettaFarmaco(it.forma, it.nome)
    }

    Scaffold(
        topBar = {
            TopAppBar(
                title = {
                    Text(
                        text = titolo,
                        style = MaterialTheme.typography.headlineSmall,
                        fontWeight = FontWeight.Bold
                    )
                },
                navigationIcon = {
                    IconButton(onClick = onIndietro) {
                        Icon(
                            imageVector = Icons.AutoMirrored.Filled.ArrowBack,
                            contentDescription = "Indietro"
                        )
                    }
                }
            )
        }
    ) { innerPadding ->
        LazyColumn(
            modifier = Modifier
                .fillMaxSize()
                .padding(innerPadding)
                .padding(horizontal = 16.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp),
            contentPadding = PaddingValues(top = 8.dp, bottom = 24.dp)
        ) {
            items(fasi, key = { it.id }) { fase ->
                FaseCard(fase = fase)
            }
            item {
                Spacer(modifier = Modifier.height(12.dp))
                Button(
                    onClick = { chiediConferma = true },
                    enabled = !fermataInCorso,
                    colors = ButtonDefaults.buttonColors(
                        containerColor = MaterialTheme.colorScheme.error,
                        contentColor = MaterialTheme.colorScheme.onError
                    ),
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(72.dp)
                ) {
                    Text(
                        text = "🛑 Ferma questa terapia",
                        style = MaterialTheme.typography.headlineSmall,
                        fontWeight = FontWeight.Bold
                    )
                }
            }
        }
    }

    if (chiediConferma) {
        AlertDialog(
            onDismissRequest = { chiediConferma = false },
            title = {
                Text(
                    text = "Fermare la terapia?",
                    style = MaterialTheme.typography.headlineSmall,
                    fontWeight = FontWeight.Bold
                )
            },
            text = {
                Text(
                    text = "Le prossime dosi verranno cancellate. Lo storico resta.",
                    style = MaterialTheme.typography.titleLarge
                )
            },
            confirmButton = {
                TextButton(
                    onClick = {
                        chiediConferma = false
                        if (!fermataInCorso) {
                            fermataInCorso = true
                            scope.launch {
                                viewModel.ferma(farmacoId)
                                onIndietro()
                            }
                        }
                    }
                ) {
                    Text(
                        text = "Ferma",
                        style = MaterialTheme.typography.titleLarge,
                        fontWeight = FontWeight.Bold,
                        color = MaterialTheme.colorScheme.error
                    )
                }
            },
            dismissButton = {
                TextButton(onClick = { chiediConferma = false }) {
                    Text(
                        text = "Annulla",
                        style = MaterialTheme.typography.titleLarge
                    )
                }
            }
        )
    }
}

@Composable
private fun FaseCard(fase: Fase) {
    Card(
        modifier = Modifier.fillMaxWidth(),
        colors = CardDefaults.cardColors(
            containerColor = MaterialTheme.colorScheme.surfaceVariant,
            contentColor = MaterialTheme.colorScheme.onSurfaceVariant
        )
    ) {
        Column(modifier = Modifier.padding(20.dp)) {
            Text(
                text = "Fase ${fase.ordine}",
                style = MaterialTheme.typography.titleMedium,
                fontWeight = FontWeight.SemiBold,
                color = MaterialTheme.colorScheme.primary
            )
            Spacer(modifier = Modifier.height(8.dp))

            val doseTesto = when {
                fase.doseMattina != null || fase.doseSera != null ->
                    "Mattina: ${fase.doseMattina ?: "—"} · Sera: ${fase.doseSera ?: "—"}"
                else -> fase.dose ?: "—"
            }
            Text(
                text = doseTesto,
                style = MaterialTheme.typography.headlineSmall,
                fontWeight = FontWeight.Bold
            )
            Spacer(modifier = Modifier.height(8.dp))

            val schemaTesto = when (fase.schema) {
                SchemaDose.INTERVALLO ->
                    fase.intervalloOre?.let { "Ogni $it ore" } ?: "A intervalli"
                SchemaDose.ORARI ->
                    "Orari: " + fase.quando.joinToString(", ") { etichettaFascia(it) }
            }
            Text(
                text = schemaTesto,
                style = MaterialTheme.typography.titleLarge
            )
            Spacer(modifier = Modifier.height(4.dp))
            Text(
                text = "Durata: " + (fase.durataGiorni?.let { "$it giorni" } ?: "fino a stop"),
                style = MaterialTheme.typography.titleLarge
            )
        }
    }
}
