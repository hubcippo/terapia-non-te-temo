package com.carletto.terapianontetemo.domain

import com.carletto.terapianontetemo.domain.model.PianoEstratto
import kotlinx.serialization.json.Json

/** Parser del JSON grezzo restituito dal modello di estrazione -> [PianoEstratto]. */
object EstrazioneParser {

    private val json = Json { ignoreUnknownKeys = true }

    /**
     * Converte la stringa JSON (eventualmente racchiusa in fence markdown ```json ... ```)
     * in un [PianoEstratto]. Lancia SerializationException se il JSON non è conforme.
     */
    fun parse(raw: String): PianoEstratto {
        val pulito = raw.trim()
            .removePrefix("```json")
            .removePrefix("```")
            .removeSuffix("```")
            .trim()
        return json.decodeFromString(PianoEstratto.serializer(), pulito)
    }
}
