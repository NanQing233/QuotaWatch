package io.apimeter.wear.providers

import io.apimeter.wear.R
import io.apimeter.wear.core.ProviderCapability
import io.apimeter.wear.core.ProviderId

data class ProviderDescriptor(
    val id: ProviderId,
    val labelRes: Int,
    val shortLabelRes: Int,
    val noteRes: Int,
    val capabilities: Set<ProviderCapability>,
    val docsUrl: String,
    val defaultBaseUrl: String,
)

object ProviderCatalog {

    val all: List<ProviderDescriptor> = listOf(
        ProviderDescriptor(
            id = ProviderId.OPENAI,
            labelRes = R.string.provider_openai,
            shortLabelRes = R.string.provider_openai_short,
            noteRes = R.string.provider_openai_note,
            capabilities = setOf(ProviderCapability.USAGE_DETAIL, ProviderCapability.COST_DETAIL),
            docsUrl = "https://platform.openai.com/docs/api-reference/usage",
            defaultBaseUrl = "https://api.openai.com",
        ),
        ProviderDescriptor(
            id = ProviderId.ANTHROPIC,
            labelRes = R.string.provider_anthropic,
            shortLabelRes = R.string.provider_anthropic_short,
            noteRes = R.string.provider_anthropic_note,
            capabilities = setOf(ProviderCapability.USAGE_DETAIL),
            docsUrl = "https://docs.anthropic.com/en/api/usage-cost-api",
            defaultBaseUrl = "https://api.anthropic.com",
        ),
        ProviderDescriptor(
            id = ProviderId.VOLC_ARK,
            labelRes = R.string.provider_volc_ark,
            shortLabelRes = R.string.provider_volc_ark_short,
            noteRes = R.string.provider_volc_ark_note,
            capabilities = setOf(ProviderCapability.USAGE_DETAIL),
            docsUrl = "https://www.volcengine.com/docs/82379/1359411",
            defaultBaseUrl = "https://ark.cn-beijing.volcengineapi.com",
        ),
        ProviderDescriptor(
            id = ProviderId.TENCENT_TOKENHUB,
            labelRes = R.string.provider_tencent_tokenhub,
            shortLabelRes = R.string.provider_tencent_tokenhub_short,
            noteRes = R.string.provider_tencent_tokenhub_note,
            capabilities = setOf(ProviderCapability.USAGE_DETAIL),
            docsUrl = "https://cloud.tencent.com/document/product/1818",
            defaultBaseUrl = "https://tokenhub.tencentcloudapi.com",
        ),
        ProviderDescriptor(
            id = ProviderId.DEEPSEEK,
            labelRes = R.string.provider_deepseek,
            shortLabelRes = R.string.provider_deepseek_short,
            noteRes = R.string.provider_deepseek_note,
            capabilities = setOf(ProviderCapability.BALANCE),
            docsUrl = "https://api-docs.deepseek.com/api/get-user-balance",
            defaultBaseUrl = "https://api.deepseek.com",
        ),
        ProviderDescriptor(
            id = ProviderId.MOONSHOT,
            labelRes = R.string.provider_moonshot,
            shortLabelRes = R.string.provider_moonshot_short,
            noteRes = R.string.provider_moonshot_note,
            capabilities = setOf(ProviderCapability.BALANCE),
            docsUrl = "https://platform.moonshot.cn/docs/api/balance",
            defaultBaseUrl = "https://api.moonshot.ai",
        ),
    )

    fun descriptor(id: ProviderId): ProviderDescriptor = all.first { it.id == id }

    fun byKey(key: String): ProviderDescriptor? = all.firstOrNull { it.id.key == key }

    fun supportsUsageDetail(id: ProviderId): Boolean =
        descriptor(id).capabilities.contains(ProviderCapability.USAGE_DETAIL)
}
