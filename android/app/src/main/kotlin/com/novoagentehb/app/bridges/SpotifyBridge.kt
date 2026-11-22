package com.novoagentehb.app.bridges

import android.util.Base64
import com.google.gson.JsonParser
import com.novoagentehb.app.data.security.SecureKeyVault
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import okhttp3.FormBody
import okhttp3.OkHttpClient
import okhttp3.Request
import java.net.URLEncoder
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class SpotifyBridge @Inject constructor(
    private val okHttpClient: OkHttpClient,
    private val keyVault: SecureKeyVault
) {
    private var cachedToken: String? = null
    private var tokenExpiresAt: Long = 0L

    suspend fun resolveTrackUri(query: String): String? = withContext(Dispatchers.IO) {
        val clientId = keyVault.get(SecureKeyVault.KEY_SPOTIFY_CLIENT_ID)
        val clientSecret = keyVault.get(SecureKeyVault.KEY_SPOTIFY_CLIENT_SECRET)
        if (clientId.isBlank() || clientSecret.isBlank()) return@withContext null

        val token = getAccessToken(clientId, clientSecret) ?: return@withContext null
        val encoded = URLEncoder.encode(query.trim(), "UTF-8")
        val req = Request.Builder()
            .url("https://api.spotify.com/v1/search?q=$encoded&type=track&limit=1")
            .addHeader("Authorization", "Bearer $token")
            .get()
            .build()

        runCatching {
            okHttpClient.newCall(req).execute().use { resp ->
                if (!resp.isSuccessful) return@use null
                val root = JsonParser.parseString(resp.body?.string().orEmpty()).asJsonObject
                root.getAsJsonObject("tracks")
                    ?.getAsJsonArray("items")
                    ?.firstOrNull()?.asJsonObject
                    ?.get("uri")?.asString
            }
        }.getOrNull()
    }

    private fun getAccessToken(clientId: String, clientSecret: String): String? {
        val now = System.currentTimeMillis()
        cachedToken?.let { if (now < tokenExpiresAt) return it }

        val basic = Base64.encodeToString("$clientId:$clientSecret".toByteArray(), Base64.NO_WRAP)
        val req = Request.Builder()
            .url("https://accounts.spotify.com/api/token")
            .addHeader("Authorization", "Basic $basic")
            .post(FormBody.Builder().add("grant_type", "client_credentials").build())
            .build()

        return runCatching {
            okHttpClient.newCall(req).execute().use { resp ->
                if (!resp.isSuccessful) return@use null
                val obj = JsonParser.parseString(resp.body?.string().orEmpty()).asJsonObject
                val token = obj.get("access_token")?.asString ?: return@use null
                val expiresIn = obj.get("expires_in")?.asLong ?: 3600L
                cachedToken = token
                tokenExpiresAt = now + (expiresIn - 60L).coerceAtLeast(30L) * 1000L
                token
            }
        }.getOrNull()
    }
}
