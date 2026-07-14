package com.carletto.terapianontetemo.data.entity

/** Forma farmaceutica del farmaco. Definizione unica condivisa (CONTRACT sez. 3). */
enum class Forma { COMPRESSA, INIEZIONE, ALTRO }

/** Stato di un DoseEvent. Definizione unica condivisa (CONTRACT sez. 3). */
enum class StatoDose { ATTESA, PRESO, SALTATO, RIMANDATO }

/** Schema di somministrazione di una fase. Definizione unica condivisa (CONTRACT sez. 3). */
enum class SchemaDose { INTERVALLO, ORARI }

/** Fascia oraria del giorno. Definizione unica condivisa (CONTRACT sez. 3). */
enum class Fascia { MATTINA, PRANZO, POMERIGGIO, SERA, NOTTE }
