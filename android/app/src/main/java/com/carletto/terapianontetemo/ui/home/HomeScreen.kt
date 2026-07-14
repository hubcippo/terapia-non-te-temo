package com.carletto.terapianontetemo.ui.home

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
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
import androidx.compose.material.icons.filled.Check
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.ElevatedCard
import androidx.compose.material3.ExtendedFloatingActionButton
import androidx.compose.material3.FilledTonalButton
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.carletto.terapianontetemo.data.entity.DoseEvent
import com.carletto.terapianontetemo.data.entity.Forma
import com.carletto.terapianontetemo.data.entity.StatoDose
import java.time.Instant
import java.time.ZoneId
import java.time.format.DateTimeFormatter

private val FORMATO_ORA: DateTimeFormatter = DateTimeFormatter.ofPattern("HH:mm")

private fun formattaOra(millis: Long): String =
    Instant.ofEpochMilli(millis)
        .atZone(ZoneId.systemDefault())
        .toLocalTime()
        .format(FORMATO_ORA)

private fun etichettaStato(stato: StatoDose): String = when (stato) {
    StatoDose.ATTESA -> "Attesa"
    StatoDose.PRESO -> "Preso"
    StatoDose.SALTATO -> "Saltato"
    StatoDose.RIMANDATO -> "Rimandato"
}

@Composable
fun HomeScreen(
    viewModel: HomeViewModel,
    onAggiungi: () -> Unit
) {
    val state by viewModel.uiState.collectAsStateWithLifecycle()
    HomeContent(
        state = state,
        onFatto = viewModel::segnaFatto,
        onAggiungi = onAggiungi
    )
}

@Composable
private fun HomeContent(
    state: HomeUiState,
    onFatto: (Long) -> Unit,
    onAggiungi: () -> Unit
) {
    Scaffold(
        floatingActionButton = {
            ExtendedFloatingActionButton(onClick = onAggiungi) {
                Text(
                    text = "➕ Aggiungi da foto",
                    style = MaterialTheme.typography.titleLarge,
                    fontWeight = FontWeight.Bold
                )
            }
        }
    ) { innerPadding ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(innerPadding)
                .padding(horizontal = 16.dp)
        ) {
            Spacer(modifier = Modifier.height(16.dp))

            if (state.prossima != null) {
                ProssimaDoseCard(
                    dose = state.prossima,
                    nomeFarmaco = nomeFarmaco(state, state.prossima),
                    onFatto = onFatto
                )
                Spacer(modifier = Modifier.height(24.dp))
            }

            Text(
                text = "Dosi di oggi",
                style = MaterialTheme.typography.headlineMedium,
                fontWeight = FontWeight.Bold,
                color = MaterialTheme.colorScheme.onBackground
            )
            Spacer(modifier = Modifier.height(12.dp))

            if (state.oggi.isEmpty()) {
                Box(
                    modifier = Modifier
                        .fillMaxWidth()
                        .weight(1f),
                    contentAlignment = Alignment.Center
                ) {
                    Text(
                        text = "Nessuna dose per oggi",
                        style = MaterialTheme.typography.headlineSmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        textAlign = TextAlign.Center
                    )
                }
            } else {
                LazyColumn(
                    modifier = Modifier.weight(1f),
                    verticalArrangement = Arrangement.spacedBy(12.dp),
                    // Spazio in fondo per non coprire l'ultima riga col FAB.
                    contentPadding = PaddingValues(bottom = 96.dp)
                ) {
                    items(state.oggi, key = { it.id }) { dose ->
                        DoseRiga(
                            dose = dose,
                            nomeFarmaco = nomeFarmaco(state, dose),
                            onFatto = onFatto
                        )
                    }
                }
                Spacer(modifier = Modifier.height(16.dp))
            }
        }
    }
}

private fun nomeFarmaco(state: HomeUiState, dose: DoseEvent): String =
    state.nomiFarmaci[dose.farmacoId] ?: "Farmaco"

/** Regola di presentazione unica: le iniezioni mostrano l'emoji 💉 davanti al nome. */
private fun etichettaFarmaco(forma: Forma, nome: String): String =
    if (forma == Forma.INIEZIONE) "💉 $nome" else nome

@Composable
private fun ProssimaDoseCard(
    dose: DoseEvent,
    nomeFarmaco: String,
    onFatto: (Long) -> Unit
) {
    ElevatedCard(
        modifier = Modifier.fillMaxWidth(),
        colors = CardDefaults.elevatedCardColors(
            containerColor = MaterialTheme.colorScheme.primaryContainer,
            contentColor = MaterialTheme.colorScheme.onPrimaryContainer
        )
    ) {
        Column(modifier = Modifier.padding(20.dp)) {
            Text(
                text = "Prossima dose",
                style = MaterialTheme.typography.titleLarge,
                fontWeight = FontWeight.SemiBold
            )
            Spacer(modifier = Modifier.height(8.dp))
            Text(
                text = etichettaFarmaco(dose.forma, nomeFarmaco),
                style = MaterialTheme.typography.displaySmall,
                fontWeight = FontWeight.Bold
            )
            Spacer(modifier = Modifier.height(4.dp))
            Text(
                text = dose.dose,
                style = MaterialTheme.typography.headlineSmall
            )
            Spacer(modifier = Modifier.height(4.dp))
            Text(
                text = "Ore ${formattaOra(dose.dataOraMillis)}",
                style = MaterialTheme.typography.headlineMedium,
                fontWeight = FontWeight.Bold
            )
            Spacer(modifier = Modifier.height(16.dp))
            Button(
                onClick = { onFatto(dose.id) },
                modifier = Modifier
                    .fillMaxWidth()
                    .height(64.dp)
            ) {
                Icon(
                    imageVector = Icons.Filled.Check,
                    contentDescription = null
                )
                Spacer(modifier = Modifier.width(8.dp))
                Text(
                    text = "Fatto",
                    style = MaterialTheme.typography.headlineSmall,
                    fontWeight = FontWeight.Bold
                )
            }
        }
    }
}

@Composable
private fun DoseRiga(
    dose: DoseEvent,
    nomeFarmaco: String,
    onFatto: (Long) -> Unit
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
                style = MaterialTheme.typography.headlineSmall,
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
                Spacer(modifier = Modifier.height(6.dp))
                StatoBadge(stato = dose.stato)
            }
            if (dose.stato == StatoDose.ATTESA) {
                Spacer(modifier = Modifier.width(12.dp))
                FilledTonalButton(
                    onClick = { onFatto(dose.id) },
                    modifier = Modifier.height(56.dp)
                ) {
                    Icon(
                        imageVector = Icons.Filled.Check,
                        contentDescription = "Segna come fatto"
                    )
                    Spacer(modifier = Modifier.width(6.dp))
                    Text(
                        text = "Fatto",
                        style = MaterialTheme.typography.titleMedium,
                        fontWeight = FontWeight.Bold
                    )
                }
            }
        }
    }
}

@Composable
private fun StatoBadge(stato: StatoDose) {
    val (contenitore, contenuto) = when (stato) {
        StatoDose.ATTESA ->
            MaterialTheme.colorScheme.secondaryContainer to
                MaterialTheme.colorScheme.onSecondaryContainer
        StatoDose.PRESO ->
            MaterialTheme.colorScheme.tertiaryContainer to
                MaterialTheme.colorScheme.onTertiaryContainer
        StatoDose.SALTATO ->
            MaterialTheme.colorScheme.errorContainer to
                MaterialTheme.colorScheme.onErrorContainer
        StatoDose.RIMANDATO ->
            MaterialTheme.colorScheme.surface to
                MaterialTheme.colorScheme.onSurface
    }
    Surface(
        color = contenitore,
        contentColor = contenuto,
        shape = MaterialTheme.shapes.small
    ) {
        Text(
            text = etichettaStato(stato),
            style = MaterialTheme.typography.titleMedium,
            fontWeight = FontWeight.SemiBold,
            modifier = Modifier.padding(horizontal = 12.dp, vertical = 4.dp)
        )
    }
}
