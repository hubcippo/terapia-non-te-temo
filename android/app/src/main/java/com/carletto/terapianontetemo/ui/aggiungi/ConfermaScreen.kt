package com.carletto.terapianontetemo.ui.aggiungi

import android.graphics.Bitmap
import android.net.Uri
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.Image
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.FilterChip
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedCard
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.produceState
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.dp
import androidx.compose.ui.window.Dialog
import com.carletto.terapianontetemo.domain.model.FasciaEstratta
import com.carletto.terapianontetemo.domain.model.FormaEstratta
import com.carletto.terapianontetemo.domain.model.SchemaEstratto
import com.carletto.terapianontetemo.ui.theme.GialloBordo
import com.carletto.terapianontetemo.ui.theme.GialloSfondo
import com.carletto.terapianontetemo.ui.theme.GialloTesto
import com.carletto.terapianontetemo.util.ImmaginePerVision
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

/**
 * Contenuto della schermata di conferma del piano estratto (CONTRACT sez. 9):
 * avvisi in cima, card per terapia (gialla se da controllare), campi editabili,
 * FilterChip per le fasce, esclusione con undo (onToggleTerapia), miniatura foto,
 * bottone Conferma.
 */
@Composable
fun ConfermaContent(
    piano: PianoEditabile,
    fotoUri: Uri?,
    onModifica: (PianoEditabile) -> Unit,
    onToggleTerapia: (Int) -> Unit,
    onConferma: () -> Unit
) {
    Column(
        modifier = Modifier
            .fillMaxSize()
            .verticalScroll(rememberScrollState())
            .padding(horizontal = 16.dp, vertical = 16.dp)
    ) {
        Text(
            text = "Controlla il piano",
            style = MaterialTheme.typography.headlineMedium,
            fontWeight = FontWeight.Bold
        )
        Spacer(modifier = Modifier.height(4.dp))
        Text(
            text = "Correggi quello che non torna, poi conferma.",
            style = MaterialTheme.typography.titleLarge,
            color = MaterialTheme.colorScheme.onSurfaceVariant
        )
        Spacer(modifier = Modifier.height(16.dp))

        if (piano.avvisi.isNotEmpty()) {
            AvvisiCard(avvisi = piano.avvisi)
            Spacer(modifier = Modifier.height(16.dp))
        }

        if (fotoUri != null) {
            MiniaturaFoto(fotoUri = fotoUri)
            Spacer(modifier = Modifier.height(16.dp))
        }

        piano.terapie.forEachIndexed { indice, terapia ->
            if (terapia.inclusa) {
                TerapiaCard(
                    terapia = terapia,
                    onModificaTerapia = { nuova ->
                        onModifica(piano.conTerapia(indice) { nuova })
                    },
                    onModificaFase = { indiceFase, nuovaFase ->
                        onModifica(piano.conFase(indice, indiceFase) { nuovaFase })
                    },
                    onEscludi = { onToggleTerapia(indice) }
                )
            } else {
                TerapiaEsclusaCard(
                    terapia = terapia,
                    onRipristina = { onToggleTerapia(indice) }
                )
            }
            Spacer(modifier = Modifier.height(16.dp))
        }

        Button(
            onClick = onConferma,
            enabled = piano.confermabile,
            modifier = Modifier
                .fillMaxWidth()
                .height(72.dp)
        ) {
            Text(
                text = "✅ Conferma piano",
                style = MaterialTheme.typography.headlineSmall,
                fontWeight = FontWeight.Bold
            )
        }
        if (!piano.confermabile) {
            Spacer(modifier = Modifier.height(8.dp))
            Text(
                text = "Tutte le terapie sono escluse: ripristinane almeno una per confermare.",
                style = MaterialTheme.typography.titleMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
        }
        Spacer(modifier = Modifier.height(24.dp))
    }
}

/** Card gialla di attenzione: usata per gli avvisi e per le terapie da controllare. */
@Composable
private fun CardAttenzione(content: @Composable ColumnScope.() -> Unit) {
    Card(
        modifier = Modifier.fillMaxWidth(),
        colors = CardDefaults.cardColors(
            containerColor = GialloSfondo,
            contentColor = GialloTesto
        )
    ) {
        Column(modifier = Modifier.padding(16.dp), content = content)
    }
}

@Composable
private fun AvvisiCard(avvisi: List<String>) {
    CardAttenzione {
        Text(
            text = "⚠️ Da verificare",
            style = MaterialTheme.typography.titleLarge,
            fontWeight = FontWeight.Bold
        )
        avvisi.forEach { avviso ->
            Spacer(modifier = Modifier.height(8.dp))
            Text(
                text = "• $avviso",
                style = MaterialTheme.typography.titleMedium
            )
        }
    }
}

@Composable
private fun MiniaturaFoto(fotoUri: Uri) {
    val context = LocalContext.current

    // Decodifica fuori dal main thread; finché è null la miniatura non appare.
    val bitmap by produceState<Bitmap?>(initialValue = null, fotoUri) {
        value = withContext(Dispatchers.IO) {
            ImmaginePerVision.decodificaRidotta(context, fotoUri, 1280)
        }
    }
    val miniatura = bitmap ?: return

    var ingrandita by remember { mutableStateOf(false) }

    Image(
        bitmap = miniatura.asImageBitmap(),
        contentDescription = "Foto della ricetta: tocca per ingrandire",
        modifier = Modifier
            .fillMaxWidth()
            .height(160.dp)
            .clip(MaterialTheme.shapes.medium)
            .clickable { ingrandita = true },
        contentScale = ContentScale.Crop
    )

    if (ingrandita) {
        Dialog(onDismissRequest = { ingrandita = false }) {
            Image(
                bitmap = miniatura.asImageBitmap(),
                contentDescription = "Foto della ricetta ingrandita: tocca per chiudere",
                modifier = Modifier
                    .fillMaxWidth()
                    .clip(MaterialTheme.shapes.medium)
                    .clickable { ingrandita = false },
                contentScale = ContentScale.Fit
            )
        }
    }
}

@Composable
private fun TerapiaCard(
    terapia: TerapiaEditabile,
    onModificaTerapia: (TerapiaEditabile) -> Unit,
    onModificaFase: (Int, FaseEditabile) -> Unit,
    onEscludi: () -> Unit
) {
    OutlinedCard(
        modifier = Modifier.fillMaxWidth(),
        border = if (terapia.daControllare) {
            BorderStroke(3.dp, GialloBordo)
        } else {
            BorderStroke(1.dp, MaterialTheme.colorScheme.outline)
        }
    ) {
        Column(modifier = Modifier.padding(16.dp)) {
            if (terapia.daControllare) {
                CardAttenzione {
                    Text(
                        text = "⚠️ Letto male dalla foto: controlla bene questi dati.",
                        style = MaterialTheme.typography.titleMedium,
                        fontWeight = FontWeight.Bold
                    )
                }
                Spacer(modifier = Modifier.height(12.dp))
            }

            Row(verticalAlignment = Alignment.CenterVertically) {
                OutlinedTextField(
                    value = terapia.farmaco,
                    onValueChange = { onModificaTerapia(terapia.copy(farmaco = it)) },
                    modifier = Modifier.weight(1f),
                    label = { Text("Farmaco") },
                    singleLine = true,
                    textStyle = MaterialTheme.typography.titleLarge
                )
                Spacer(modifier = Modifier.width(8.dp))
                IconButton(onClick = onEscludi) {
                    Icon(
                        imageVector = Icons.Filled.Delete,
                        contentDescription = "Escludi ${terapia.farmaco}",
                        tint = MaterialTheme.colorScheme.error
                    )
                }
            }

            Spacer(modifier = Modifier.height(8.dp))
            Text(
                text = buildString {
                    append(etichettaForma(terapia.forma))
                    terapia.note?.takeIf { it.isNotBlank() }?.let { append(" — $it") }
                },
                style = MaterialTheme.typography.titleMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )

            terapia.fasi.forEachIndexed { indiceFase, fase ->
                Spacer(modifier = Modifier.height(12.dp))
                if (terapia.fasi.size > 1) {
                    HorizontalDivider()
                    Spacer(modifier = Modifier.height(12.dp))
                    Text(
                        text = "Fase ${fase.ordine}" +
                            if (fase.dopoFasePrecedente) " (dopo la precedente)" else "",
                        style = MaterialTheme.typography.titleLarge,
                        fontWeight = FontWeight.Bold
                    )
                    Spacer(modifier = Modifier.height(8.dp))
                }
                FaseEditor(
                    fase = fase,
                    onModifica = { nuova -> onModificaFase(indiceFase, nuova) }
                )
            }
        }
    }
}

@Composable
private fun FaseEditor(
    fase: FaseEditabile,
    onModifica: (FaseEditabile) -> Unit
) {
    Column {
        val titolazione = fase.doseMattina.isNotBlank() || fase.doseSera.isNotBlank()
        if (titolazione) {
            Row {
                OutlinedTextField(
                    value = fase.doseMattina,
                    onValueChange = { onModifica(fase.copy(doseMattina = it)) },
                    modifier = Modifier.weight(1f),
                    label = { Text("Dose mattina") },
                    singleLine = true,
                    textStyle = MaterialTheme.typography.titleLarge
                )
                Spacer(modifier = Modifier.width(12.dp))
                OutlinedTextField(
                    value = fase.doseSera,
                    onValueChange = { onModifica(fase.copy(doseSera = it)) },
                    modifier = Modifier.weight(1f),
                    label = { Text("Dose sera") },
                    singleLine = true,
                    textStyle = MaterialTheme.typography.titleLarge
                )
            }
        } else {
            OutlinedTextField(
                value = fase.dose,
                onValueChange = { onModifica(fase.copy(dose = it)) },
                modifier = Modifier.fillMaxWidth(),
                label = { Text("Dose") },
                singleLine = true,
                textStyle = MaterialTheme.typography.titleLarge
            )
        }

        Spacer(modifier = Modifier.height(12.dp))
        OutlinedTextField(
            value = fase.durataGiorni,
            onValueChange = { onModifica(fase.copy(durataGiorni = it)) },
            modifier = Modifier.fillMaxWidth(),
            label = { Text("Durata (giorni)") },
            placeholder = {
                if (fase.mantenimento) Text("Vuoto = senza scadenza")
            },
            singleLine = true,
            keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
            textStyle = MaterialTheme.typography.titleLarge
        )

        when (fase.schema) {
            SchemaEstratto.ORARI -> {
                Spacer(modifier = Modifier.height(12.dp))
                Text(
                    text = "Quando",
                    style = MaterialTheme.typography.titleMedium,
                    fontWeight = FontWeight.SemiBold
                )
                FasceChips(
                    selezionate = fase.quando,
                    onCambia = { nuove -> onModifica(fase.copy(quando = nuove)) }
                )
            }

            SchemaEstratto.INTERVALLO -> {
                Spacer(modifier = Modifier.height(8.dp))
                Text(
                    text = fase.intervalloOre?.let { "Ogni $it ore" } ?: "A intervalli",
                    style = MaterialTheme.typography.titleMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }
        }

        if (fase.mantenimento) {
            Spacer(modifier = Modifier.height(8.dp))
            Text(
                text = "Dose di mantenimento (continua senza scadenza).",
                style = MaterialTheme.typography.titleMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
        }
    }
}

@OptIn(ExperimentalLayoutApi::class)
@Composable
private fun FasceChips(
    selezionate: List<FasciaEstratta>,
    onCambia: (List<FasciaEstratta>) -> Unit
) {
    FlowRow(
        modifier = Modifier.fillMaxWidth(),
        horizontalArrangement = androidx.compose.foundation.layout.Arrangement.spacedBy(8.dp)
    ) {
        FasciaEstratta.entries.forEach { fascia ->
            val selezionata = fascia in selezionate
            FilterChip(
                selected = selezionata,
                onClick = {
                    val nuove = if (selezionata) {
                        selezionate - fascia
                    } else {
                        (selezionate + fascia).sortedBy { it.ordinal }
                    }
                    onCambia(nuove)
                },
                label = {
                    Text(
                        text = etichettaFascia(fascia),
                        style = MaterialTheme.typography.titleMedium
                    )
                }
            )
        }
    }
}

@Composable
private fun TerapiaEsclusaCard(
    terapia: TerapiaEditabile,
    onRipristina: () -> Unit
) {
    Card(
        modifier = Modifier.fillMaxWidth(),
        colors = CardDefaults.cardColors(
            containerColor = MaterialTheme.colorScheme.surfaceVariant,
            contentColor = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.7f)
        )
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(16.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Text(
                text = "${terapia.farmaco} — esclusa",
                style = MaterialTheme.typography.titleLarge,
                modifier = Modifier.weight(1f)
            )
            TextButton(onClick = onRipristina) {
                Text(
                    text = "Ripristina",
                    style = MaterialTheme.typography.titleLarge,
                    fontWeight = FontWeight.Bold
                )
            }
        }
    }
}

// ---- helper puri di presentazione ----

private fun etichettaForma(forma: FormaEstratta): String = when (forma) {
    FormaEstratta.COMPRESSA -> "Compressa"
    FormaEstratta.INIEZIONE -> "💉 Iniezione"
    FormaEstratta.ALTRO -> "Altro"
}

private fun etichettaFascia(fascia: FasciaEstratta): String = when (fascia) {
    FasciaEstratta.MATTINA -> "Mattina"
    FasciaEstratta.PRANZO -> "Pranzo"
    FasciaEstratta.POMERIGGIO -> "Pomeriggio"
    FasciaEstratta.SERA -> "Sera"
    FasciaEstratta.NOTTE -> "Notte"
}
