package com.kanadecn.note.data.settings

/**
 * 预置服务商。Base URL 固定，避免手填出错。
 * Claude 走 Anthropic 自己的 /models 与鉴权头，其余走 OpenAI 兼容接口。
 */
enum class AiProvider(
    val label: String,
    val baseUrl: String,
) {
    DEEPSEEK("DeepSeek", "https://api.deepseek.com/v1"),
    QWEN("通义千问", "https://dashscope.aliyuncs.com/compatible-mode/v1"),
    ZHIPU("智谱 GLM", "https://open.bigmodel.cn/api/paas/v4"),
    KIMI("Kimi (Moonshot)", "https://api.moonshot.cn/v1"),
    OPENAI("GPT (OpenAI)", "https://api.openai.com/v1"),
    CLAUDE("Claude (Anthropic)", "https://api.anthropic.com/v1"),
    ;

    companion object {
        fun fromBaseUrl(url: String): AiProvider = when {
            url.contains("dashscope.aliyuncs.com", ignoreCase = true) -> QWEN
            url.contains("bigmodel.cn", ignoreCase = true) -> ZHIPU
            url.contains("anthropic.com", ignoreCase = true) -> CLAUDE
            url.contains("openai.com", ignoreCase = true) -> OPENAI
            url.contains("moonshot", ignoreCase = true) ||
                url.contains("kimi", ignoreCase = true) -> KIMI
            else -> DEEPSEEK
        }
    }
}
