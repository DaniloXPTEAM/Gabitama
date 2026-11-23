package com.novoagentehb.app.data.providers

import com.google.gson.Gson
import com.google.gson.reflect.TypeToken
import com.novoagentehb.app.data.prefs.AppPreferences
import com.novoagentehb.app.data.security.SecureKeyVault
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class CustomProviderRepository @Inject constructor(private val prefs: AppPreferences, private val vault: SecureKeyVault) {
    private val gson = Gson()
    private val mutex = Mutex()
    val providers = prefs.customProvidersJson.map { parseStored(it) }
    val activeId = prefs.activeCustomProviderId
    private fun parseStored(json: String): List<CustomProvider> {
        val raw: List<CustomProvider> = gson.fromJson(json, object : TypeToken<List<CustomProvider>>() {}.type)
        return raw.map { CustomProviderFormat.validate(it.name, it.baseUrl, it.chatModel, it.screenModel, it.visionModel, it.id) }
    }

    suspend fun add(provider: CustomProvider, key: String): CustomProvider = mutex.withLock {
        val valid = CustomProviderFormat.validate(provider.name, provider.baseUrl, provider.chatModel, provider.screenModel, provider.visionModel, provider.id)
        require(key.isNotBlank() && key.length <= 4096 && key.all { it.code in 33..126 }) { "Informe uma credencial válida." }
        val list = providers.first()
        require(list.none { it.id == valid.id }) { "Provedor já cadastrado." }
        require(list.size < 30) { "Limite de provedores atingido." }
        vault.set("custom_provider_${valid.id}", key)
        prefs.saveCustomProvidersJson(gson.toJson(list + valid))
        valid
    }
    suspend fun update(provider: CustomProvider, key: String) = mutex.withLock {
        val valid = CustomProviderFormat.validate(provider.name, provider.baseUrl, provider.chatModel, provider.screenModel, provider.visionModel, provider.id)
        val list = providers.first()
        require(list.any { it.id == valid.id }) { "Provedor não encontrado." }
        // Any change needs renewed consent before sending future chat to the endpoint.
        if (activeId.first() == valid.id) prefs.activateCustomProvider("__needs_selection__")
        if (key.isNotBlank()) {
            require(key.length <= 4096 && key.all { it.code in 33..126 }) { "Credencial inválida." }
            vault.set("custom_provider_${valid.id}", key)
        }
        prefs.saveCustomProvidersJson(gson.toJson(list.map { if (it.id == valid.id) valid else it }))
    }
    suspend fun remove(id: String) = mutex.withLock {
        val list = providers.first()
        require(list.any { it.id == id }) { "Provedor não encontrado." }
        if (activeId.first() == id) prefs.activateCustomProvider("__needs_selection__")
        prefs.saveCustomProvidersJson(gson.toJson(list.filterNot { it.id == id }))
        vault.set("custom_provider_$id", "")
    }
    fun key(id: String) = vault.get("custom_provider_$id")
    suspend fun activate(id: String) {
        require(providers.first().any { it.id == id }) { "Provedor não encontrado." }
        prefs.activateCustomProvider(id)
    }
    suspend fun active(): CustomProvider? {
        val id = activeId.first()
        return providers.first().firstOrNull { it.id == id }
    }
}
