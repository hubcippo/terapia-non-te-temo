package com.carletto.terapianontetemo.util

import com.carletto.terapianontetemo.data.entity.Forma
import java.time.Instant
import java.time.ZoneId
import java.time.format.DateTimeFormatter

private val FORMATO_ORA: DateTimeFormatter = DateTimeFormatter.ofPattern("HH:mm")

/** Regola di presentazione unica: le iniezioni mostrano l'emoji 💉 davanti al nome. */
fun etichettaFarmaco(forma: Forma, nome: String): String =
    if (forma == Forma.INIEZIONE) "💉 $nome" else nome

/** Ora locale in formato HH:mm da epoch millis. */
fun formattaOra(millis: Long): String =
    Instant.ofEpochMilli(millis)
        .atZone(ZoneId.systemDefault())
        .toLocalTime()
        .format(FORMATO_ORA)
