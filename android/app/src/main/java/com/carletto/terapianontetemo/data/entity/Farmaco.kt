package com.carletto.terapianontetemo.data.entity

import androidx.room.Entity
import androidx.room.PrimaryKey

@Entity
data class Farmaco(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    val nome: String,
    val forma: Forma,
    val note: String? = null
)
