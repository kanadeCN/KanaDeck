package com.kanadecn.note.data.remote

import android.util.Base64
import com.kanadecn.note.data.settings.LlmSettings
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.RequestBody.Companion.toRequestBody
import org.json.JSONArray
import org.json.JSONObject
import org.json.JSONTokener
import java.io.IOException
import java.io.InterruptedIOException
import java.util.concurrent.TimeUnit

sealed interface ModelListResult {
    data class Success(val models: List<String>) : ModelListResult
    data class Failure(
        val reason: FailureReason,
        val detail: String? = null,
    ) : ModelListResult
}

data class RecognizedMistake(
    val question: String,
    val myAnswer: String,
    val correctAnswer: String,
    val isCorrect: Boolean,
    val errorType: String,
    val knowledgePoint: String,
    val explanation: String,
    val category: String,
)

sealed interface MistakeRecognitionResult {
    data class Success(val data: RecognizedMistake) : MistakeRecognitionResult
    data class Failure(
        val reason: FailureReason,
        val detail: String? = null,
    ) : MistakeRecognitionResult
}

enum class FailureReason {
    INVALID_URL,
    INSECURE_URL,
    UNAUTHORIZED,
    NOT_FOUND,
    BAD_REQUEST,
    RATE_LIMIT,
    SERVER,
    TIMEOUT,
    NETWORK,
    UNKNOWN,
}

/**
 * 只负责和 AI 服务通信。
 * 安全约定：不打印请求头/请求体，key 永远不会进日志。
 */
class AiClient(
    private val client: OkHttpClient = defaultClient(),
) {

    // 识图比拉模型列表慢，单独用更长超时
    private val recognitionClient: OkHttpClient = client.newBuilder()
        .connectTimeout(15, TimeUnit.SECONDS)
        .readTimeout(90, TimeUnit.SECONDS)
        .callTimeout(90, TimeUnit.SECONDS)
        .build()

    /**
     * 拉取可用模型：GET {baseUrl}/models。
     * 同时验证服务商地址和 Key 是否有效。
     */
    suspend fun listModels(baseUrlRaw: String, apiKey: String): ModelListResult =
        withContext(Dispatchers.IO) {
            val baseUrl = baseUrlRaw.trim().trimEnd('/')
            if (baseUrl.isBlank() || !baseUrl.startsWith("https://", ignoreCase = true)) {
                return@withContext ModelListResult.Failure(insecureOrInvalid(baseUrl))
            }

            val request = try {
                Request.Builder()
                    .url("$baseUrl/models")
                    .withAuth(baseUrl, apiKey)
                    .get()
                    .build()
            } catch (e: IllegalArgumentException) {
                return@withContext ModelListResult.Failure(
                    FailureReason.INVALID_URL,
                    e.message?.sanitizeForDisplay(apiKey),
                )
            }

            try {
                client.newCall(request).execute().use { response ->
                    val body = runCatching { response.body?.string() }.getOrNull()
                    if (!response.isSuccessful) {
                        val (reason, detail) = mapFailure(response.code, body, apiKey)
                        return@use ModelListResult.Failure(reason, detail)
                    }

                    val models = parseModels(body)
                    if (models.isEmpty()) {
                        ModelListResult.Failure(
                            FailureReason.UNKNOWN,
                            "接口没有返回模型列表（可能不支持 /models）",
                        )
                    } else {
                        ModelListResult.Success(models)
                    }
                }
            } catch (e: InterruptedIOException) {
                // SocketTimeoutException 和 callTimeout 都走这里
                ModelListResult.Failure(
                    FailureReason.TIMEOUT,
                    e.message?.sanitizeForDisplay(apiKey),
                )
            } catch (e: IOException) {
                ModelListResult.Failure(
                    FailureReason.NETWORK,
                    e.message?.sanitizeForDisplay(apiKey),
                )
            }
        }

    /**
     * 把错题图片发给多模态模型，返回结构化结果。
     * OpenAI 兼容走 /chat/completions，Claude 走 /messages。
     */
    suspend fun recognizeMistake(
        settings: LlmSettings,
        imageJpeg: ByteArray,
        categories: List<String>,
    ): MistakeRecognitionResult = withContext(Dispatchers.IO) {
        val baseUrl = settings.baseUrl.trim().trimEnd('/')
        if (baseUrl.isBlank() || !baseUrl.startsWith("https://", ignoreCase = true)) {
            return@withContext MistakeRecognitionResult.Failure(insecureOrInvalid(baseUrl))
        }

        val isAnthropic = baseUrl.contains("anthropic.com", ignoreCase = true)
        val endpoint = if (isAnthropic) "$baseUrl/messages" else "$baseUrl/chat/completions"
        val systemPrompt = PromptTemplates.mistakeSystem(categories)
        val payload = if (isAnthropic) {
            anthropicPayload(settings.model, imageJpeg, systemPrompt)
        } else {
            openAiPayload(settings.model, imageJpeg, systemPrompt)
        }

        val request = try {
            Request.Builder()
                .url(endpoint)
                .withAuth(baseUrl, settings.apiKey)
                .post(payload.toRequestBody(JSON_MEDIA_TYPE))
                .build()
        } catch (_: IllegalArgumentException) {
            return@withContext MistakeRecognitionResult.Failure(FailureReason.INVALID_URL)
        }

        try {
            recognitionClient.newCall(request).execute().use { response ->
                val body = runCatching { response.body?.string() }.getOrNull()
                if (!response.isSuccessful) {
                    val (reason, detail) = mapFailure(response.code, body, settings.apiKey)
                    return@use MistakeRecognitionResult.Failure(reason, detail)
                }

                val contentText = extractContentText(body, isAnthropic)
                val reasoningText = if (isAnthropic) "" else extractReasoningText(body)
                val json = extractJsonObject(contentText)
                    ?: extractJsonObject(reasoningText)
                if (json == null) {
                    val finishReason = extractFinishReason(body)
                    val raw = contentText.ifBlank { reasoningText }
                        .ifBlank { body.orEmpty() }
                        .sanitizeForDisplay(settings.apiKey)

                    val detail = buildString {
                        append("模型没有返回可解析的 JSON")
                        if (!finishReason.isNullOrBlank()) {
                            append("（finish_reason=")
                            append(finishReason)
                            append("）")
                        }
                        if (contentText.isBlank()) append("；content 为空")
                        if (raw.isNotBlank()) {
                            append("；原始返回：")
                            append(raw)
                        }
                    }
                    MistakeRecognitionResult.Failure(FailureReason.UNKNOWN, detail)
                } else {
                    MistakeRecognitionResult.Success(parseRecognized(json, categories))
                }
            }
        } catch (e: InterruptedIOException) {
            MistakeRecognitionResult.Failure(
                FailureReason.TIMEOUT,
                e.message?.sanitizeForDisplay(settings.apiKey),
            )
        } catch (e: IOException) {
            MistakeRecognitionResult.Failure(
                FailureReason.NETWORK,
                e.message?.sanitizeForDisplay(settings.apiKey),
            )
        }
    }

    private fun openAiPayload(
        model: String,
        imageJpeg: ByteArray,
        systemPrompt: String,
    ): String {
        val dataUrl = "data:image/jpeg;base64," + Base64.encodeToString(imageJpeg, Base64.NO_WRAP)
        return JSONObject().apply {
            put("model", model.trim())
            put("max_tokens", 8192)
            put(
                "messages",
                JSONArray().apply {
                    put(
                        JSONObject().apply {
                            put("role", "system")
                            put("content", systemPrompt)
                        },
                    )
                    put(
                        JSONObject().apply {
                            put("role", "user")
                            put(
                                "content",
                                JSONArray().apply {
                                    put(
                                        JSONObject().apply {
                                            put("type", "text")
                                            put("text", PromptTemplates.MISTAKE_USER)
                                        },
                                    )
                                    put(
                                        JSONObject().apply {
                                            put("type", "image_url")
                                            put(
                                                "image_url",
                                                JSONObject().put("url", dataUrl),
                                            )
                                        },
                                    )
                                },
                            )
                        },
                    )
                },
            )
        }.toString()
    }

    private fun anthropicPayload(
        model: String,
        imageJpeg: ByteArray,
        systemPrompt: String,
    ): String {
        return JSONObject().apply {
            put("model", model.trim())
            put("max_tokens", 8192)
            put("system", systemPrompt)
            put(
                "messages",
                JSONArray().put(
                    JSONObject().apply {
                        put("role", "user")
                        put(
                            "content",
                            JSONArray().apply {
                                put(
                                    JSONObject().apply {
                                        put("type", "image")
                                        put(
                                            "source",
                                            JSONObject().apply {
                                                put("type", "base64")
                                                put("media_type", "image/jpeg")
                                                put(
                                                    "data",
                                                    Base64.encodeToString(imageJpeg, Base64.NO_WRAP),
                                                )
                                            },
                                        )
                                    },
                                )
                                put(
                                    JSONObject().apply {
                                        put("type", "text")
                                        put("text", PromptTemplates.MISTAKE_USER)
                                    },
                                )
                            },
                        )
                    },
                ),
            )
        }.toString()
    }

    private fun extractContentText(body: String?, isAnthropic: Boolean): String {
        if (body.isNullOrBlank()) return ""
        return runCatching {
            val root = JSONObject(body)
            if (isAnthropic) {
                root.optJSONArray("content")
                    ?.let { arr ->
                        buildString {
                            for (i in 0 until arr.length()) {
                                val part = arr.optJSONObject(i) ?: continue
                                if (part.optString("type") == "text") {
                                    append(part.optString("text"))
                                }
                            }
                        }
                    }
                    .orEmpty()
            } else {
                val content = root.optJSONArray("choices")
                    ?.optJSONObject(0)
                    ?.optJSONObject("message")
                    ?.opt("content")

                when (content) {
                    is String -> content
                    is JSONArray -> buildString {
                        for (i in 0 until content.length()) {
                            content.optJSONObject(i)?.optString("text")?.let(::append)
                        }
                    }
                    else -> ""
                }
            }
        }.getOrDefault("")
    }

    private fun extractReasoningText(body: String?): String {
        if (body.isNullOrBlank()) return ""
        return runCatching {
            JSONObject(body)
                .optJSONArray("choices")
                ?.optJSONObject(0)
                ?.optJSONObject("message")
                ?.optString("reasoning_content")
                .orEmpty()
        }.getOrDefault("")
    }

    private fun extractFinishReason(body: String?): String? {
        if (body.isNullOrBlank()) return null
        return runCatching {
            JSONObject(body)
                .optJSONArray("choices")
                ?.optJSONObject(0)
                ?.optString("finish_reason")
        }.getOrNull()
    }

    private fun extractJsonObject(text: String): JSONObject? {
        if (text.isBlank()) return null
        val cleaned = text
            .replace("```json", "")
            .replace("```", "")
            .trim()
        val start = cleaned.indexOf('{')
        val end = cleaned.lastIndexOf('}')
        if (start < 0 || end <= start) return null

        return runCatching {
            JSONObject(cleaned.substring(start, end + 1))
        }.getOrNull()
    }

    private fun parseRecognized(
        obj: JSONObject,
        categories: List<String>,
    ): RecognizedMistake {
        val isCorrect = when (val value = obj.opt("is_correct")) {
            is Boolean -> value
            is String -> value.equals("true", ignoreCase = true)
            else -> false
        }

        val recommendedCategory = obj.optString("category").trim()
        val category = if (recommendedCategory in categories) {
            recommendedCategory
        } else {
            com.kanadecn.note.data.mistakes.CategoryStore.UNCATEGORIZED
        }

        return RecognizedMistake(
            question = obj.optString("question").ifBlank { "（未识别到题目）" },
            myAnswer = obj.optString("my_answer"),
            correctAnswer = obj.optString("correct_answer"),
            isCorrect = isCorrect,
            errorType = obj.optString("error_type").ifBlank {
                if (isCorrect) "无错" else "未分类"
            },
            knowledgePoint = obj.optString("knowledge_point"),
            explanation = obj.optString("explanation"),
            category = category,
        )
    }

    /**
     * Claude 用 x-api-key + anthropic-version，其余走 Bearer。
     */
    private fun Request.Builder.withAuth(baseUrl: String, apiKey: String): Request.Builder =
        if (baseUrl.contains("anthropic.com", ignoreCase = true)) {
            header("x-api-key", apiKey.trim())
                .header("anthropic-version", "2023-06-01")
        } else {
            header("Authorization", "Bearer ${apiKey.trim()}")
        }

    private fun insecureOrInvalid(baseUrl: String): FailureReason =
        if (baseUrl.startsWith("http://", ignoreCase = true)) {
            FailureReason.INSECURE_URL
        } else {
            FailureReason.INVALID_URL
        }

    private fun mapFailure(
        code: Int,
        body: String?,
        apiKey: String,
    ): Pair<FailureReason, String?> {
        val reason = when {
            code == 401 || code == 403 -> FailureReason.UNAUTHORIZED
            code == 404 -> FailureReason.NOT_FOUND
            code == 400 -> FailureReason.BAD_REQUEST
            code == 429 -> FailureReason.RATE_LIMIT
            code >= 500 -> FailureReason.SERVER
            else -> FailureReason.UNKNOWN
        }
        return reason to body?.sanitizeForDisplay(apiKey)
    }

    /**
     * 兼容几种常见返回：
     * - {"data":[{"id":"..."}]}
     * - {"models":[...]}
     * - ["..."]
     */
    private fun parseModels(body: String?): List<String> {
        if (body.isNullOrBlank()) return emptyList()
        return runCatching {
            val array: JSONArray? = when (val root = JSONTokener(body).nextValue()) {
                is JSONArray -> root
                is JSONObject -> root.optJSONArray("data") ?: root.optJSONArray("models")
                else -> null
            }
            if (array == null) return emptyList()

            buildList {
                for (i in 0 until array.length()) {
                    val id = when (val item = array.opt(i)) {
                        is JSONObject -> item.optString("id").ifBlank { item.optString("name") }
                        is String -> item
                        else -> ""
                    }
                    if (id.isNotBlank()) add(id)
                }
            }.distinct().sorted()
        }.getOrDefault(emptyList())
    }

    private fun String.sanitizeForDisplay(apiKey: String): String {
        val redacted = if (apiKey.isBlank()) this else replace(apiKey, "***")
        return redacted.replace(Regex("\\s+"), " ").trim().take(300)
    }

    companion object {
        private val JSON_MEDIA_TYPE = "application/json; charset=utf-8".toMediaType()

        private fun defaultClient(): OkHttpClient = OkHttpClient.Builder()
            .connectTimeout(10, TimeUnit.SECONDS)
            .readTimeout(15, TimeUnit.SECONDS)
            // 整个请求最多 15 秒，避免获取模型时按钮一直转
            .callTimeout(15, TimeUnit.SECONDS)
            .build()
    }
}
