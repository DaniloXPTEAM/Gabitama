package com.novoagentehb.app.gabi

data class VoiceProsodyProfile(
    val id: String,
    val eosThreshold: Float,
    val maxTokens: Int,
    val measuredDurationSec: Double,
    val measuredWpm: Double,
    val classification: String
)

data class FrozenVoiceIdentitySpec(
    val voiceId: String = "gabi_24l",
    val pocketModelId: String = "portuguese_24l",
    val sampleRateHz: Int = 24_000,
    val channels: Int = 1,
    val language: String = "pt-BR",
    val allowSilentSwapToSystemTts: Boolean = false,
    val profiles: List<VoiceProsodyProfile> = defaultProfiles
) {
    fun profileById(id: String): VoiceProsodyProfile =
        profiles.firstOrNull { it.id == id } ?: profiles.first()

    companion object {
        val defaultProfiles = listOf(
            VoiceProsodyProfile("B2_base", -4.0f, 200, 5.68, 179.6, "REQUESTED_B2"),
            VoiceProsodyProfile("B2_pausas", -4.0f, 220, 6.48, 157.4, "REQUESTED_B2_RESERVE"),
            VoiceProsodyProfile("C2_base", -4.0f, 220, 6.32, 170.9, "REQUESTED_C2"),
            VoiceProsodyProfile("C2_pausas", -4.0f, 240, 9.44, 114.4, "REQUESTED_C2_PAUSES"),
            VoiceProsodyProfile("B3", -4.0f, 260, 6.72, 151.8, "HISTORICAL_BASELINE_2026_10_13"),
            VoiceProsodyProfile("C3", -4.0f, 260, 6.72, 151.8, "HISTORICAL_BASELINE_2026_10_13")
        )
    }
}
