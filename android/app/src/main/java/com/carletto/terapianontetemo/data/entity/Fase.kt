package com.carletto.terapianontetemo.data.entity

import androidx.room.Entity
import androidx.room.PrimaryKey

@Entity
data class Fase(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    val farmacoId: Long,
    val ordine: Int,
    val dose: String?,
    val doseMattina: String?,
    val doseSera: String?,
    val schema: SchemaDose,
    val intervalloOre: Int?,
    val quando: List<Fascia>,
    val durataGiorni: Int?,
    val mantenimento: Boolean,
    val dopoFasePrecedente: Boolean
)
