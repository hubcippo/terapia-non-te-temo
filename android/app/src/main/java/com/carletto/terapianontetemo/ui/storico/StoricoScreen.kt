package com.carletto.terapianontetemo.ui.storico

import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.ElevatedCard
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.carletto.terapianontetemo.data.entity.DoseEvent
import com.carletto.terapianontetemo.data.entity.StatoDose
import com.carletto.terapianontetemo.util.etichettaFarmaco
import com.carletto.terapianontetemo.util.formattaOra
import java.time.format.DateTimeFormatter
import java.util.Locale

private val FORMATO_GIORNO: DateTimeFormatter =
    DateTimeFormatter.ofPattern("EEE d MMMM", Locale.ITALIAN)

private fun etichettaStato(stato: StatoDose): String = when (stato) {
    StatoDose.PRESO -> "✅ presa"
    StatoDose.SALTATO -> "❌ saltata"
    StatoDose.ATTESA, StatoDose.RIMANDATO -> "⏳ in attesa"
}

/**
 * Storico READ-ONLY (CONTRACT sez. 14): card aderenza in testa, poi
 * tutte le dosi (più recenti prima) raggruppate per giorno.
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun StoricoScreen(
    viewModel: StoricoViewModel,
    onIndietro: () -> Unit
) {
    val state by viewModel.uiState.collectAsStateWithLifecycle()

    Scaffold(
        topBar = {
            TopAppBar(
                title = {
                    Text(
                        text = "Storico",
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
            contentPadding = PaddingValues(top = 8.dp, bottom = 24.dp)
        ) {
            item {
                AderenzaCard(state = state)
                Spacer(modifier = Modifier.height(16.dp))
            }

            state.dosiPerGiorno.forEach { (giorno, dosiGiorno) ->
                item(key = "giorno_$giorno") {
                    Text(
                        text = giorno.format(FORMATO_GIORNO),
                        style = MaterialTheme.typography.titleLarge,
                        fontWeight = FontWeight.Bold,
                        color = MaterialTheme.colorScheme.primary,
                        modifier = Modifier.padding(top = 12.dp, bottom = 8.dp)
                    )
                }
                items(dosiGiorno, key = { it.id }) { dose ->
                    StoricoRiga(
                        dose = dose,
                        nomeFarmaco = state.nomiFarmaci[dose.farmacoId] ?: "Farmaco"
                    )
                    Spacer(modifier = Modifier.height(8.dp))
                }
            }
        }
    }
}

@Composable
private fun AderenzaCard(state: StoricoUiState) {
    ElevatedCard(
        modifier = Modifier.fillMaxWidth(),
        colors = CardDefaults.elevatedCardColors(
            containerColor = MaterialTheme.colorScheme.primaryContainer,
            contentColor = MaterialTheme.colorScheme.onPrimaryContainer
        )
    ) {
        Column(modifier = Modifier.padding(20.dp)) {
            Text(
                text = "Aderenza",
                style = MaterialTheme.typography.titleLarge,
                fontWeight = FontWeight.SemiBold
            )
            Spacer(modifier = Modifier.height(8.dp))

            val percento = state.complessiva.percento
            if (percento == null) {
                Text(
                    text = "Ancora nessuna dose passata",
                    style = MaterialTheme.typography.headlineSmall
                )
            } else {
                Text(
                    text = "$percento%",
                    style = MaterialTheme.typography.displayLarge,
                    fontWeight = FontWeight.Bold
                )
                Text(
                    text = "${state.complessiva.prese} su ${state.complessiva.totale}",
                    style = MaterialTheme.typography.headlineSmall
                )
                Spacer(modifier = Modifier.height(12.dp))
                state.perFarmaco.forEach { (farmacoId, riepilogo) ->
                    val nome = state.nomiFarmaci[farmacoId] ?: "Farmaco"
                    val perc = riepilogo.percento
                    if (perc != null) {
                        Text(
                            text = "$nome: $perc% (${riepilogo.prese}/${riepilogo.totale})",
                            style = MaterialTheme.typography.titleLarge
                        )
                    }
                }
            }
        }
    }
}

@Composable
private fun StoricoRiga(
    dose: DoseEvent,
    nomeFarmaco: String
) {
    Card(
        modifier = Modifier.fillMaxWidth(),
        colors = CardDefaults.cardColors(
            containerColor = MaterialTheme.colorScheme.surfaceVariant,
            contentColor = MaterialTheme.colorScheme.onSurfaceVariant
        )
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(16.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Text(
                text = formattaOra(dose.dataOraMillis),
                style = MaterialTheme.typography.titleLarge,
                fontWeight = FontWeight.Bold
            )
            Spacer(modifier = Modifier.width(16.dp))
            Column(modifier = Modifier.weight(1f)) {
                Text(
                    text = etichettaFarmaco(dose.forma, nomeFarmaco),
                    style = MaterialTheme.typography.titleLarge,
                    fontWeight = FontWeight.SemiBold
                )
                Text(
                    text = dose.dose,
                    style = MaterialTheme.typography.titleMedium
                )
            }
            Spacer(modifier = Modifier.width(12.dp))
            Text(
                text = etichettaStato(dose.stato),
                style = MaterialTheme.typography.titleMedium,
                fontWeight = FontWeight.SemiBold
            )
        }
    }
}
