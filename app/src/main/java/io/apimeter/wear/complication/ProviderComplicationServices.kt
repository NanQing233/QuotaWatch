package io.apimeter.wear.complication

import io.apimeter.wear.core.ProviderId

/** 六家厂商各一个信息位；类名/FQCN 必须与 AndroidManifest.xml 声明一致，改动会导致系统无法绑定。 */
class OpenAiComplicationService : ProviderComplicationService(ProviderId.OPENAI.key)

class AnthropicComplicationService : ProviderComplicationService(ProviderId.ANTHROPIC.key)

class VolcArkComplicationService : ProviderComplicationService(ProviderId.VOLC_ARK.key)

class TencentTokenHubComplicationService :
    ProviderComplicationService(ProviderId.TENCENT_TOKENHUB.key)

class DeepSeekComplicationService : ProviderComplicationService(ProviderId.DEEPSEEK.key)

class MoonshotComplicationService : ProviderComplicationService(ProviderId.MOONSHOT.key)
