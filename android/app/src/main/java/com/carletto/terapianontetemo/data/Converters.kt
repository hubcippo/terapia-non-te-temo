package com.carletto.terapianontetemo.data

import androidx.room.TypeConverter
import com.carletto.terapianontetemo.data.entity.Fascia
import com.carletto.terapianontetemo.data.entity.Forma
import com.carletto.terapianontetemo.data.entity.SchemaDose
import com.carletto.terapianontetemo.data.entity.StatoDose
import java.time.LocalTime

/**
 * TypeConverters Room per liste ed enum (CONTRACT sez. 3).
 * Liste serializzate come stringhe separate da virgola; lista vuota = stringa vuota.
 */
class Converters {

    // ---- List<Fascia> ----

    @TypeConverter
    fun fromFasciaList(value: List<Fascia>): String =
        value.joinToString(",") { it.name }

    @TypeConverter
    fun toFasciaList(value: String): List<Fascia> =
        if (value.isBlank()) emptyList()
        else value.split(",").map { Fascia.valueOf(it.trim()) }

    // ---- List<LocalTime> ----

    @TypeConverter
    fun fromLocalTimeList(value: List<LocalTime>): String =
        value.joinToString(",") { it.toString() }

    @TypeConverter
    fun toLocalTimeList(value: String): List<LocalTime> =
        if (value.isBlank()) emptyList()
        else value.split(",").map { LocalTime.parse(it.trim()) }

    // ---- Enum via name ----

    @TypeConverter
    fun fromForma(value: Forma): String = value.name

    @TypeConverter
    fun toForma(value: String): Forma = Forma.valueOf(value)

    @TypeConverter
    fun fromStatoDose(value: StatoDose): String = value.name

    @TypeConverter
    fun toStatoDose(value: String): StatoDose = StatoDose.valueOf(value)

    @TypeConverter
    fun fromSchemaDose(value: SchemaDose): String = value.name

    @TypeConverter
    fun toSchemaDose(value: String): SchemaDose = SchemaDose.valueOf(value)
}
