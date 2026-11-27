package com.novoagentehb.app.data.security

import android.content.Context
import android.content.SharedPreferences
import androidx.security.crypto.EncryptedSharedPreferences
import androidx.security.crypto.MasterKey
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.channels.awaitClose
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.callbackFlow
import kotlinx.coroutines.flow.distinctUntilChanged
import javax.inject.Inject
import javax.inject.Singleton

/**
 * Cofre de chaves isolado do novo produto via Android Keystore (AES256-GCM).
 * Nunca lê nem copia segredos ou bancos de outros aplicativos instalados.
 */
@Singleton
class SecureKeyVault @Inject constructor(
    @ApplicationContext context: Context
) {
    private val masterKey = MasterKey.Builder(context)
        .setKeyScheme(MasterKey.KeyScheme.AES256_GCM)
        .build()

    private val prefs: SharedPreferences = EncryptedSharedPreferences.create(
        context,
        PREFS_FILE,
        masterKey,
        EncryptedSharedPreferences.PrefKeyEncryptionScheme.AES256_SIV,
        EncryptedSharedPreferences.PrefValueEncryptionScheme.AES256_GCM
    )

    fun get(key: String): String = prefs.getString(key, "").orEmpty()

    fun set(key: String, value: String) {
        prefs.edit().putString(key, value.trim()).apply()
    }

    fun clearAll() {
        prefs.edit().clear().apply()
    }

    fun observe(key: String): Flow<String> = callbackFlow {
        trySend(get(key))
        val listener = SharedPreferences.OnSharedPreferenceChangeListener { sp, changed ->
            if (changed == key) trySend(sp.getString(key, "").orEmpty())
        }
        prefs.registerOnSharedPreferenceChangeListener(listener)
        awaitClose { prefs.unregisterOnSharedPreferenceChangeListener(listener) }
    }.distinctUntilChanged()

    companion object {
        const val PREFS_FILE = "novo_agente_hb_secure_prefs"
        const val KEY_GEMINI = "gemini_api_key"
        const val KEY_GROQ = "groq_api_key"
        const val KEY_OPENROUTER = "openrouter_api_key"
        const val KEY_DEEPSEEK = "deepseek_api_key"
        const val KEY_ANTHROPIC = "anthropic_api_key"
        const val KEY_ELEVENLABS = "elevenlabs_api_key"
        const val KEY_NVIDIA_API_KEY = "nvidia_api_key"
        const val KEY_SPOTIFY_CLIENT_ID = "spotify_client_id"
        const val KEY_SPOTIFY_CLIENT_SECRET = "spotify_client_secret"

        private val SECRET_PATTERNS = listOf(
            Regex("""AIza[0-9A-Za-z\-_]{20,}"""),
            Regex("""AQ\.[0-9A-Za-z\-_]{10,}"""),
            Regex("""sk-[0-9A-Za-z\-_]{16,}"""),
            Regex("""gsk_[0-9A-Za-z]{16,}"""),
            Regex("""nvapi-[0-9A-Za-z\-_]{16,}"""),
            Regex("""Bearer\s+[0-9A-Za-z\-_.]{12,}""", RegexOption.IGNORE_CASE)
        )

        fun redactSecrets(input: String): String {
            var out = input
            for (pattern in SECRET_PATTERNS) {
                out = out.replace(pattern, "[REDACTED]")
            }
            return out
        }
    }
}
