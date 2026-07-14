package com.carletto.terapianontetemo.ai

import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.buildJsonArray
import kotlinx.serialization.json.buildJsonObject
import kotlinx.serialization.json.jsonArray
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive
import kotlinx.serialization.json.put
import java.io.IOException
import java.net.HttpURLConnection
import java.net.URL
import java.util.Base64

/**
 * Client OpenAI Vision per l'estrazione del piano terapeutico da una foto di ricetta.
 * Nessuna chiave hardcoded: la chiave arriva dal chiamante (vedi [KeyStore]).
 * Non wired nella Home in Fase B.
 */
class OpenAiVisionClient(private val apiKey: String) {

    private val json = Json { ignoreUnknownKeys = true }

    /** Ritorna il JSON grezzo (content del modello) conforme a schema.json. model = "gpt-5.4". */
    suspend fun estraiPiano(imageBytes: ByteArray): String = withContext(Dispatchers.IO) {
        val dataUri = "data:image/jpeg;base64," +
            Base64.getEncoder().encodeToString(imageBytes)

        val body = buildJsonObject {
            put("model", "gpt-5.4")
            put("reasoning_effort", "high")
            put("messages", buildJsonArray {
                add(buildJsonObject {
                    put("role", "system")
                    put("content", SYSTEM_PROMPT)
                })
                add(buildJsonObject {
                    put("role", "user")
                    put("content", buildJsonArray {
                        add(buildJsonObject {
                            put("type", "text")
                            put("text", USER_PROMPT)
                        })
                        add(buildJsonObject {
                            put("type", "image_url")
                            put("image_url", buildJsonObject {
                                put("url", dataUri)
                                put("detail", "high")
                            })
                        })
                    })
                })
            })
            put("response_format", buildJsonObject {
                put("type", "json_schema")
                put("json_schema", SCHEMA_ELEMENT)
            })
        }

        val connection = URL(ENDPOINT).openConnection() as HttpURLConnection
        try {
            connection.requestMethod = "POST"
            connection.doOutput = true
            connection.connectTimeout = 30_000
            connection.readTimeout = 180_000
            connection.setRequestProperty("Content-Type", "application/json")
            connection.setRequestProperty("Authorization", "Bearer $apiKey")

            connection.outputStream.use { it.write(body.toString().toByteArray(Charsets.UTF_8)) }

            val codice = connection.responseCode
            if (codice !in 200..299) {
                val errore = connection.errorStream?.bufferedReader()?.use { it.readText() }
                throw IOException("OpenAI HTTP $codice: ${errore ?: "nessun dettaglio"}")
            }

            val risposta = connection.inputStream.bufferedReader().use { it.readText() }
            estraiContent(risposta)
        } finally {
            connection.disconnect()
        }
    }

    /** Estrae choices[0].message.content dalla risposta chat/completions. */
    private fun estraiContent(risposta: String): String {
        val root: JsonObject = json.parseToJsonElement(risposta).jsonObject
        return root["choices"]?.jsonArray?.firstOrNull()?.jsonObject
            ?.get("message")?.jsonObject
            ?.get("content")?.jsonPrimitive?.content
            ?: throw IOException("Risposta OpenAI senza content: $risposta")
    }

    private companion object {
        const val ENDPOINT = "https://api.openai.com/v1/chat/completions"

        /** Schema parsato una sola volta (SCHEMA_JSON è costante). */
        val SCHEMA_ELEMENT: JsonObject by lazy {
            Json.parseToJsonElement(SCHEMA_JSON).jsonObject
        }

        /** Prompt validato in Fase A (mirror di prototipo-estrazione/prompt.md). */
        val SYSTEM_PROMPT = """
            Sei un assistente clinico che estrae il piano terapeutico da una foto di una prescrizione o di un referto medico italiano, per un'app di promemoria farmaci. La sicurezza viene prima di tutto: è meglio segnalare un dubbio che indovinare.

            Regole tassative:
            1. Non inventare nulla. Estrai solo ciò che leggi con certezza. Se un farmaco, una dose, una frequenza o una durata non sono leggibili con sicurezza, imposta illeggibile=true per quel farmaco (o confidenza bassa) e aggiungi una stringa chiara in avvisi che dica all'utente cosa deve verificare a mano. Mai riempire un buco con un valore plausibile.
            2. Immagine ruotata. L'immagine può essere ruotata di 90/180 gradi: leggila comunque.
            3. Terapia immersa nel rumore. La prescrizione può essere sepolta in un referto lungo (anamnesi, RMN, esame obiettivo...). Estrai solo la parte di terapia/prescrizione (spesso introdotta da "Terapia", "Si consiglia", "Prescrizione", o cerchiata). Ignora diagnosi ed esami.
            4. Forma. Distingui compressa da iniezione (fiale, fl, IM, EV, sottocute -> iniezione; abbreviazioni cpr, cp, cs, compresse, capsule -> compressa). Se non è chiaro, usa altro e segnala.
            5. Schemi a scalare / titolazione. "X per N giorni poi Y" -> più fasi sequenziali con dopoFasePrecedente=true sulle fasi successive. Dose che cresce/cala nel tempo -> fasi ordinate. Se mattina e sera hanno dosi diverse, usa doseMattina/doseSera (e lascia dose=null).
            6. Frequenze. "ogni 8/12/24 ore" -> schema "intervallo" con intervalloOre. "mattina/sera", "la sera", "a colazione" -> schema "orari" con quando. Dose di mantenimento senza fine -> mantenimento=true, durataGiorni=null.
            7. Vincoli ("a digiuno", "IM", "la mattina") -> nel campo note del farmaco.
            8. Non è una prescrizione. Se l'immagine non contiene una terapia da somministrare (es. una griglia/tabella disegnata a mano, un calendario, una foto non pertinente) -> e_ricetta=false, terapie=[], e spiega in avvisi. Non trattare una griglia-promemoria come una nuova prescrizione.
            9. Confidenza. Per ogni farmaco imposta confidenza (alta/media/bassa) in base a quanto è chiaro il testo.

            Restituisci esclusivamente il JSON conforme allo schema fornito. Nessun commento, nessun testo fuori dal JSON.
        """.trimIndent()

        const val USER_PROMPT =
            "Allegata: la foto della prescrizione. Estrai il piano terapeutico in JSON secondo lo schema."

        /** Mirror di prototipo-estrazione/schema.json (strict). */
        val SCHEMA_JSON = """
        {
          "name": "piano_terapeutico",
          "strict": true,
          "schema": {
            "type": "object",
            "additionalProperties": false,
            "required": ["e_ricetta", "terapie", "avvisi"],
            "properties": {
              "e_ricetta": { "type": "boolean" },
              "avvisi": { "type": "array", "items": { "type": "string" } },
              "terapie": {
                "type": "array",
                "items": {
                  "type": "object",
                  "additionalProperties": false,
                  "required": ["farmaco", "forma", "note", "fasi", "confidenza", "illeggibile"],
                  "properties": {
                    "farmaco": { "type": "string" },
                    "forma": { "type": "string", "enum": ["compressa", "iniezione", "altro"] },
                    "note": { "type": ["string", "null"] },
                    "confidenza": { "type": "string", "enum": ["alta", "media", "bassa"] },
                    "illeggibile": { "type": "boolean" },
                    "fasi": {
                      "type": "array",
                      "items": {
                        "type": "object",
                        "additionalProperties": false,
                        "required": ["ordine", "dose", "doseMattina", "doseSera", "schema", "intervalloOre", "quando", "durataGiorni", "mantenimento", "dopoFasePrecedente"],
                        "properties": {
                          "ordine": { "type": "integer" },
                          "dose": { "type": ["string", "null"] },
                          "doseMattina": { "type": ["string", "null"] },
                          "doseSera": { "type": ["string", "null"] },
                          "schema": { "type": "string", "enum": ["intervallo", "orari"] },
                          "intervalloOre": { "type": ["integer", "null"] },
                          "quando": {
                            "type": "array",
                            "items": { "type": "string", "enum": ["mattina", "pranzo", "pomeriggio", "sera", "notte"] }
                          },
                          "durataGiorni": { "type": ["integer", "null"] },
                          "mantenimento": { "type": "boolean" },
                          "dopoFasePrecedente": { "type": "boolean" }
                        }
                      }
                    }
                  }
                }
              }
            }
          }
        }
        """
    }
}
