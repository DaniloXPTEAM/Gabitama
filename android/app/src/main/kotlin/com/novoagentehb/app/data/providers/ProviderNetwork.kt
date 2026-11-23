package com.novoagentehb.app.data.providers

import com.google.gson.JsonParser
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import okhttp3.Dns
import okhttp3.OkHttpClient
import okhttp3.Request
import java.net.Proxy
import java.net.UnknownHostException
import javax.inject.Inject
import javax.inject.Singleton

/** Do not forward credentials via redirects, proxies or private DNS results. */
@Singleton
class ProviderNetwork @Inject constructor(client: OkHttpClient) {
    val client: OkHttpClient = client.newBuilder().proxy(Proxy.NO_PROXY)
        .followRedirects(false).followSslRedirects(false).retryOnConnectionFailure(false)
        .dns(object : Dns {
            override fun lookup(hostname: String): List<java.net.InetAddress> {
                val addresses = Dns.SYSTEM.lookup(hostname)
                if (addresses.isEmpty() || addresses.any { !CustomProviderFormat.isPublicAddress(it) }) throw UnknownHostException("Endereço não público bloqueado")
                return addresses
            }
        }).build()

    suspend fun discover(provider: CustomProvider, key: String): List<String> = withContext(Dispatchers.IO) {
        require(key.isNotBlank()) { "Credencial ausente." }
        val request = Request.Builder().url("${provider.baseUrl}/models").header("Authorization", "Bearer $key").build()
        try {
            client.newCall(request).execute().use { response ->
                if (!response.isSuccessful) throw IllegalStateException(when (response.code) {
                    401 -> "Autenticação recusada (401)."
                    403 -> "Acesso negado (403)."
                    402, 429 -> "Cota, saldo ou limite atingido (HTTP ${response.code})."
                    404 -> "Endpoint de descoberta inexistente (404)."
                    else -> "Erro ao listar modelos (HTTP ${response.code})."
                })
                val body = response.body ?: throw IllegalStateException("Resposta vazia.")
                val stream = body.byteStream()
                val out = java.io.ByteArrayOutputStream()
                val buffer = ByteArray(8192)
                while (true) {
                    val n = stream.read(buffer)
                    if (n < 0) break
                    if (out.size() + n > 1024 * 1024) throw IllegalStateException("Lista de modelos excede o limite.")
                    out.write(buffer, 0, n)
                }
                val bytes = out.toByteArray()
                val root = try { JsonParser.parseString(bytes.toString(Charsets.UTF_8)).asJsonObject } catch (_: Exception) { throw IllegalStateException("Resposta de modelos inválida.") }
                val data = root.getAsJsonArray("data") ?: throw IllegalStateException("Resposta de modelos inválida.")
                data.mapNotNull { runCatching { it.asJsonObject.get("id").asString }.getOrNull() }
                    .filter { Regex("[A-Za-z0-9._:/-]{1,160}").matches(it) }.distinct().take(500)
            }
        } catch (e: java.io.IOException) {
            throw IllegalStateException("Falha de rede ou tempo limite na descoberta.")
        }
    }
}
