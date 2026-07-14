package com.carletto.terapianontetemo.ai

import android.content.Context
import android.content.SharedPreferences
import androidx.security.crypto.EncryptedSharedPreferences
import androidx.security.crypto.MasterKey

/**
 * Custodia della chiave API OpenAI in EncryptedSharedPreferences.
 * Il repo non contiene mai la chiave: la si imposta a runtime con [setApiKey].
 */
object KeyStore {

    private const val PREFS_FILE = "terapia_secure_prefs"
    private const val KEY_API = "openai_api_key"

    /** Init crypto (MasterKey + apertura file) fatta una sola volta per processo. */
    @Volatile
    private var cached: SharedPreferences? = null

    fun getApiKey(context: Context): String? =
        prefs(context).getString(KEY_API, null)

    fun setApiKey(context: Context, key: String) {
        prefs(context).edit().putString(KEY_API, key).apply()
    }

    private fun prefs(context: Context): SharedPreferences =
        cached ?: synchronized(this) {
            cached ?: create(context.applicationContext).also { cached = it }
        }

    private fun create(appContext: Context): SharedPreferences {
        val masterKey = MasterKey.Builder(appContext)
            .setKeyScheme(MasterKey.KeyScheme.AES256_GCM)
            .build()
        return EncryptedSharedPreferences.create(
            appContext,
            PREFS_FILE,
            masterKey,
            EncryptedSharedPreferences.PrefKeyEncryptionScheme.AES256_SIV,
            EncryptedSharedPreferences.PrefValueEncryptionScheme.AES256_GCM
        )
    }
}
