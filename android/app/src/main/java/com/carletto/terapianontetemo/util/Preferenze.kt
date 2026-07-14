package com.carletto.terapianontetemo.util

import android.content.Context

/**
 * Preferenze semplici dell'app (SharedPreferences normali, file "prefs").
 * Nessun dato sensibile: solo flag di stato come l'onboarding completato.
 */
object Preferenze {

    private const val NOME_FILE = "prefs"
    private const val KEY_ONBOARDING_FATTO = "onboarding_fatto"

    fun onboardingFatto(context: Context): Boolean =
        context.getSharedPreferences(NOME_FILE, Context.MODE_PRIVATE)
            .getBoolean(KEY_ONBOARDING_FATTO, false)

    fun segnaOnboardingFatto(context: Context) {
        context.getSharedPreferences(NOME_FILE, Context.MODE_PRIVATE)
            .edit().putBoolean(KEY_ONBOARDING_FATTO, true).apply()
    }
}
