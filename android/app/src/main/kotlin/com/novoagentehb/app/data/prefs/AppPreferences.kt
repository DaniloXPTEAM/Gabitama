package com.novoagentehb.app.data.prefs

import android.content.Context
import androidx.datastore.core.DataStore
import androidx.datastore.preferences.core.Preferences
import androidx.datastore.preferences.core.booleanPreferencesKey
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.floatPreferencesKey
import androidx.datastore.preferences.core.intPreferencesKey
import androidx.datastore.preferences.core.longPreferencesKey
import androidx.datastore.preferences.core.stringPreferencesKey
import androidx.datastore.preferences.preferencesDataStore
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map
import javax.inject.Inject
import javax.inject.Singleton

private val Context.appDataStore: DataStore<Preferences> by preferencesDataStore(name = "novo_agente_hb_settings")

enum class ProviderId(val id: String, val displayName: String) {
    GEMINI("gemini", "Google Gemini"),
    GROQ("groq", "Groq"),
    OPENROUTER("openrouter", "OpenRouter"),
    DEEPSEEK("deepseek", "DeepSeek"),
    ANTHROPIC("anthropic", "Anthropic Claude");

    val defaultTextModel: String get() = when (this) {
        GEMINI -> "gemini-2.5-flash"
        GROQ -> "llama-3.3-70b-versatile"
        OPENROUTER -> "openrouter/free"
        DEEPSEEK -> "deepseek-chat"
        ANTHROPIC -> "claude-sonnet-4-20250514"
    }

    /** Default for the text chat and task synthesis roles only. Vision and screen operation keep [defaultTextModel]. */
    val defaultChatModel: String get() = when (this) {
        GEMINI -> AppPreferences.DEFAULT_GEMINI_CHAT_MODEL
        else -> defaultTextModel
    }
}

enum class VoiceModeId(val id: String, val label: String) {
    GEMINI_LIVE("gemini_live", "Gemini Live (voz direta)"),
    GABI_24L_POCKET("gabi_24l_pocket", "Gabi 24L local (B2/C2)"),
    ELEVENLABS("elevenlabs", "ElevenLabs (opt-in)"),
    ANDROID_TTS("android_tts", "TTS do sistema (fallback)")
}

@Singleton
class AppPreferences @Inject constructor(
    @ApplicationContext private val context: Context
) {
    private val ds = context.appDataStore

    val geminiImageModel: Flow<String> = ds.data.map { it[KEY_GEMINI_IMAGE_MODEL] ?: "" }
    suspend fun setGeminiImageModel(model: String) = ds.edit { it[KEY_GEMINI_IMAGE_MODEL] = model }
    val customProvidersJson: Flow<String> = ds.data.map { it[KEY_CUSTOM_PROVIDERS] ?: "[]" }
    val activeCustomProviderId: Flow<String> = ds.data.map { it[KEY_ACTIVE_CUSTOM_PROVIDER] ?: "" }
    suspend fun saveCustomProvidersJson(json: String) = ds.edit { it[KEY_CUSTOM_PROVIDERS] = json }
    suspend fun activateCustomProvider(id: String) = ds.edit { it[KEY_ACTIVE_CUSTOM_PROVIDER] = id }

    val onboardingCompleted: Flow<Boolean> = ds.data.map { it[KEY_ONBOARDING] ?: false }
    val primaryProvider: Flow<ProviderId> = ds.data.map {
        runCatching { ProviderId.valueOf(it[KEY_PRIMARY_PROVIDER] ?: ProviderId.GEMINI.name) }
            .getOrDefault(ProviderId.GEMINI)
    }
    val chatModel: Flow<String> = ds.data.map { it[KEY_CHAT_MODEL] ?: DEFAULT_GEMINI_CHAT_MODEL }
    val screenOpModel: Flow<String> = ds.data.map { it[KEY_SCREEN_MODEL] ?: "gemini-2.5-flash" }
    val visionModel: Flow<String> = ds.data.map { it[KEY_VISION_MODEL] ?: "gemini-2.5-flash" }
    val liveVoiceModel: Flow<String> = ds.data.map {
        it[KEY_LIVE_MODEL] ?: DEFAULT_GEMINI_LIVE_MODEL
    }

    val voiceMode: Flow<VoiceModeId> = ds.data.map {
        runCatching { VoiceModeId.valueOf(it[KEY_VOICE_MODE] ?: VoiceModeId.GEMINI_LIVE.name) }
            .getOrDefault(VoiceModeId.GEMINI_LIVE)
    }
    val prosodyProfile: Flow<String> = ds.data.map { it[KEY_PROSODY_PROFILE] ?: "B2_base" }
    val speechRate: Flow<Float> = ds.data.map { it[KEY_SPEECH_RATE] ?: 1.0f }
    val speechPitch: Flow<Float> = ds.data.map { it[KEY_SPEECH_PITCH] ?: 1.05f }

    val defaultCity: Flow<String> = ds.data.map { it[KEY_DEFAULT_CITY] ?: "Fortaleza" }
    val proactiveAlertsEnabled: Flow<Boolean> = ds.data.map { it[KEY_PROACTIVE_ALERTS] ?: false }
    val allowPaidCallsWithoutConfirm: Flow<Boolean> = ds.data.map { it[KEY_ALLOW_PAID_AUTO] ?: false }
    val safTreeUri: Flow<String> = ds.data.map { it[KEY_SAF_TREE_URI] ?: "" }

    // Estado exclusivo do Quarto da Gabi (zero chaves de Pet/morte/memorial)
    val quartoLastCareAt: Flow<Long> = ds.data.map { it[KEY_QUARTO_LAST_CARE_AT] ?: 0L }
    val quartoFirstVisitAt: Flow<Long> = ds.data.map { it[KEY_QUARTO_FIRST_VISIT_AT] ?: 0L }
    val quartoUnlockIntervalDays: Flow<Int> = ds.data.map { it[KEY_QUARTO_UNLOCK_INTERVAL] ?: 0 }
    val quartoSelectedOutfitId: Flow<String> = ds.data.map { it[KEY_QUARTO_OUTFIT_ID] ?: "" }
    val quartoBackgroundId: Flow<String> = ds.data.map { it[KEY_QUARTO_BG_ID] ?: "room_webp" }
    val quartoCustomBackgroundUri: Flow<String> = ds.data.map { it[KEY_QUARTO_CUSTOM_BG_URI] ?: "" }
    val quartoIntroSeen: Flow<Boolean> = ds.data.map { it[KEY_QUARTO_INTRO_SEEN] ?: false }
    val quartoSelectedStageId: Flow<String> = ds.data.map { it[KEY_QUARTO_STAGE_ID] ?: "young_child" }

    suspend fun setOnboardingCompleted(done: Boolean) = ds.edit { it[KEY_ONBOARDING] = done }
    /** Preserve each provider's task models atomically; leave Live and credentials untouched. */
    suspend fun setPrimaryProvider(provider: ProviderId) = ds.edit { settings ->
        settings.remove(KEY_ACTIVE_CUSTOM_PROVIDER)
        val previous = runCatching { ProviderId.valueOf(settings[KEY_PRIMARY_PROVIDER] ?: ProviderId.GEMINI.name) }
            .getOrDefault(ProviderId.GEMINI)
        if (previous != provider) {
            val keys = listOf(KEY_CHAT_MODEL, KEY_SCREEN_MODEL, KEY_VISION_MODEL)
            val roles = listOf("chat", "screen", "vision")
            roles.zip(keys).forEach { (role, key) ->
                val previousFallback = if (role == "chat") previous.defaultChatModel else previous.defaultTextModel
                val nextFallback = if (role == "chat") provider.defaultChatModel else provider.defaultTextModel
                settings[providerModelKey(previous, role)] = settings[key] ?: previousFallback
                settings[key] = settings[providerModelKey(provider, role)] ?: nextFallback
            }
            settings[KEY_PRIMARY_PROVIDER] = provider.name
        }
    }
    suspend fun setModels(chat: String, screen: String, vision: String, live: String) = ds.edit {
        it[KEY_CHAT_MODEL] = chat
        it[KEY_SCREEN_MODEL] = screen
        it[KEY_VISION_MODEL] = vision
        it[KEY_LIVE_MODEL] = live
    }
    suspend fun setVoiceConfig(mode: VoiceModeId, prosody: String, rate: Float, pitch: Float) = ds.edit {
        it[KEY_VOICE_MODE] = mode.name
        it[KEY_PROSODY_PROFILE] = prosody
        it[KEY_SPEECH_RATE] = rate
        it[KEY_SPEECH_PITCH] = pitch
    }
    suspend fun setDefaultCity(city: String) = ds.edit { it[KEY_DEFAULT_CITY] = city.ifBlank { "Fortaleza" } }
    suspend fun setProactiveAlertsEnabled(enabled: Boolean) = ds.edit { it[KEY_PROACTIVE_ALERTS] = enabled }
    suspend fun setAllowPaidCallsWithoutConfirm(allow: Boolean) = ds.edit { it[KEY_ALLOW_PAID_AUTO] = allow }
    suspend fun setSafTreeUri(uri: String) = ds.edit { it[KEY_SAF_TREE_URI] = uri }

    suspend fun setQuartoLastCareAt(ts: Long) = ds.edit { it[KEY_QUARTO_LAST_CARE_AT] = ts }
    suspend fun setQuartoFirstVisitAt(ts: Long) = ds.edit { it[KEY_QUARTO_FIRST_VISIT_AT] = ts }
    suspend fun setQuartoUnlockIntervalDays(days: Int) = ds.edit { it[KEY_QUARTO_UNLOCK_INTERVAL] = days }
    suspend fun setQuartoSelectedOutfitId(id: String) = ds.edit { it[KEY_QUARTO_OUTFIT_ID] = id }
    suspend fun setQuartoBackgroundId(id: String) = ds.edit { it[KEY_QUARTO_BG_ID] = id }
    suspend fun setQuartoCustomBackgroundUri(uri: String) = ds.edit { it[KEY_QUARTO_CUSTOM_BG_URI] = uri }
    suspend fun setQuartoIntroSeen(seen: Boolean) = ds.edit { it[KEY_QUARTO_INTRO_SEEN] = seen }
    suspend fun setQuartoSelectedStageId(stageId: String) = ds.edit { it[KEY_QUARTO_STAGE_ID] = stageId }

    companion object {
        // Padrão histórico; disponibilidade e compatibilidade reais devem ser verificadas na conta.
        const val DEFAULT_GEMINI_LIVE_MODEL = "gemini-2.5-flash-native-audio-preview-09-2025"
        /** Text chat model for new installs (owner-approved 24/11/2026). Not used for Live voice. */
        const val DEFAULT_GEMINI_CHAT_MODEL = "gemini-3.5-flash-lite"

        private fun providerModelKey(provider: ProviderId, role: String) =
            stringPreferencesKey("provider_model_${provider.id}_$role")

        private val KEY_GEMINI_IMAGE_MODEL = stringPreferencesKey("gemini_image_model_v1")
        private val KEY_CUSTOM_PROVIDERS = stringPreferencesKey("custom_providers_v1")
        private val KEY_ACTIVE_CUSTOM_PROVIDER = stringPreferencesKey("active_custom_provider_v1")
        private val KEY_ONBOARDING = booleanPreferencesKey("onboarding_completed")
        private val KEY_PRIMARY_PROVIDER = stringPreferencesKey("primary_provider")
        private val KEY_CHAT_MODEL = stringPreferencesKey("chat_model")
        private val KEY_SCREEN_MODEL = stringPreferencesKey("screen_model")
        private val KEY_VISION_MODEL = stringPreferencesKey("vision_model")
        private val KEY_LIVE_MODEL = stringPreferencesKey("live_model")
        private val KEY_VOICE_MODE = stringPreferencesKey("voice_mode")
        private val KEY_PROSODY_PROFILE = stringPreferencesKey("prosody_profile")
        private val KEY_SPEECH_RATE = floatPreferencesKey("speech_rate")
        private val KEY_SPEECH_PITCH = floatPreferencesKey("speech_pitch")
        private val KEY_DEFAULT_CITY = stringPreferencesKey("default_city")
        private val KEY_PROACTIVE_ALERTS = booleanPreferencesKey("proactive_alerts_enabled")
        private val KEY_ALLOW_PAID_AUTO = booleanPreferencesKey("allow_paid_calls_without_confirm")
        private val KEY_SAF_TREE_URI = stringPreferencesKey("saf_tree_uri")
        private val KEY_QUARTO_LAST_CARE_AT = longPreferencesKey("quarto_last_care_at")
        private val KEY_QUARTO_FIRST_VISIT_AT = longPreferencesKey("quarto_first_visit_at")
        private val KEY_QUARTO_UNLOCK_INTERVAL = intPreferencesKey("quarto_unlock_interval_days")
        private val KEY_QUARTO_OUTFIT_ID = stringPreferencesKey("quarto_selected_outfit_id")
        private val KEY_QUARTO_BG_ID = stringPreferencesKey("quarto_background_id")
        private val KEY_QUARTO_CUSTOM_BG_URI = stringPreferencesKey("quarto_custom_bg_uri")
        private val KEY_QUARTO_INTRO_SEEN = booleanPreferencesKey("quarto_intro_seen")
        private val KEY_QUARTO_STAGE_ID = stringPreferencesKey("quarto_selected_stage_id")
    }
}
