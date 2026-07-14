package com.carletto.terapianontetemo.domain.model

import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable

/**
 * Mirror di prototipo-estrazione/schema.json (CONTRACT sez. 5).
 * I nomi JSON sono quelli dello schema (snake_case dove differiscono, via @SerialName).
 */
@Serializable
data class PianoEstratto(
    @SerialName("e_ricetta") val eRicetta: Boolean,
    val avvisi: List<String>,
    val terapie: List<TerapiaEstratta>
)

@Serializable
data class TerapiaEstratta(
    val farmaco: String,
    val forma: FormaEstratta,
    val note: String?,
    val confidenza: ConfidenzaEstratta,
    val illeggibile: Boolean,
    val fasi: List<FaseEstratta>
)

@Serializable
data class FaseEstratta(
    val ordine: Int,
    val dose: String?,
    val doseMattina: String?,
    val doseSera: String?,
    val schema: SchemaEstratto,
    val intervalloOre: Int?,
    val quando: List<FasciaEstratta>,
    val durataGiorni: Int?,
    val mantenimento: Boolean,
    val dopoFasePrecedente: Boolean
)

/** Valori JSON minuscoli come nello schema; da mappare sugli enum Room in Srotolatore. */
@Serializable
enum class FormaEstratta {
    @SerialName("compressa") COMPRESSA,
    @SerialName("iniezione") INIEZIONE,
    @SerialName("altro") ALTRO
}

@Serializable
enum class ConfidenzaEstratta {
    @SerialName("alta") ALTA,
    @SerialName("media") MEDIA,
    @SerialName("bassa") BASSA
}

@Serializable
enum class SchemaEstratto {
    @SerialName("intervallo") INTERVALLO,
    @SerialName("orari") ORARI
}

@Serializable
enum class FasciaEstratta {
    @SerialName("mattina") MATTINA,
    @SerialName("pranzo") PRANZO,
    @SerialName("pomeriggio") POMERIGGIO,
    @SerialName("sera") SERA,
    @SerialName("notte") NOTTE
}
