package io.apimeter.wear.providers

import io.apimeter.wear.core.CredentialField
import io.apimeter.wear.core.ProviderId

class ProviderRegistry(adapters: List<ProviderAdapter>) {

    private val byId: Map<ProviderId, ProviderAdapter> = adapters.associateBy { it.id }

    fun adapter(id: ProviderId): ProviderAdapter? = byId[id]

    fun adapterFor(providerKey: String): ProviderAdapter? =
        ProviderId.fromKey(providerKey)?.let { byId[it] }

    fun fieldsFor(providerKey: String): List<CredentialField> =
        adapterFor(providerKey)?.fields().orEmpty()

    fun require(providerKey: String): ProviderAdapter =
        adapterFor(providerKey) ?: throw ProviderException("未知的厂商：" + providerKey)

    val supported: List<ProviderId> get() = byId.keys.toList()
}
