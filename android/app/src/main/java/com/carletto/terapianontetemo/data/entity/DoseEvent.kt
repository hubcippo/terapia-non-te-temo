package com.carletto.terapianontetemo.data.entity

import androidx.room.Entity
import androidx.room.PrimaryKey

@Entity
data class DoseEvent(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    val farmacoId: Long,
    val dataOraMillis: Long,
    val dose: String,
    val forma: Forma,
    val stato: StatoDose = StatoDose.ATTESA,
    val tsAzioneMillis: Long? = null
)
