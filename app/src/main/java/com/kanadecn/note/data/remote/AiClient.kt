package com.kanadecn.note.data.remote

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
import java.net.SocketTimeoutException
import java.util.concurrent.TimeUnit

sealed interface ConnectionResult {
    data class Success(val latencyMs: Long) : ConnectionResult
    data class Failure(
        val reason: FailureReason,
        val detail: String? = null,
    ) : ConnectionResult
}

sealed interface ModelListResult {
    data class Success(val models: List<String>) : ModelListResult
    data class Failure(
        val reason: FailureReason,
        val detail: String? = null,
    ) : ModelListResult
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
 * 只负责和 OpenAI 兼容接口通信。
 * 安全约定：不打印请求头/请求体，key 永远不会进日志。
 */
class AiClient(
    private val client: OkHttpClient = defaultClient(),
) {

    /**
     * 拉取可用模型：GET {baseUrl}/models。
     * 同时验证 Base URL 和 Key 是否有效。
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
                    .header("Authorization", "Bearer ${apiKey.trim()}")
                    .get()
                    .build()
            } catch (e: IllegalArgumentException) {
                return@withContext ModelListResult.Failure(FailureReason.INVALID_URL)
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
            } catch (e: SocketTimeoutException) {
                ModelListResult.Failure(FailureReason.TIMEOUT)
            } catch (e: IOException) {
                ModelListResult.Failure(FailureReason.NETWORK)
            }
        }

    /**
     * 用一条最小的 chat 请求测试选中的模型是否可用。
     */
    suspend fun testConnection(settings: LlmSettings): ConnectionResult =
        withContext(Dispatchers.IO) {
            val baseUrl = settings.baseUrl.trim().trimEnd('/')
            if (baseUrl.isBlank() || !baseUrl.startsWith("https://", ignoreCase = true)) {
                return@withContext ConnectionResult.Failure(insecureOrInvalid(baseUrl))
            }

            val payload = JSONObject().apply {
                put("model", settings.model.trim())
                put(
                    "messages",
                    JSONArray().put(
                        JSONObject().apply {
                            put("role", "user")
                            put("content", "ping")
                        },
                    ),
                )
                put("max_tokens", 1)
                put("stream", false)
            }.toString()

            val request = try {
                Request.Builder()
                    .url("$baseUrl/chat/completions")
                    .header("Authorization", "Bearer ${settings.apiKey.trim()}")
                    .post(payload.toRequestBody(JSON_MEDIA_TYPE))
                    .build()
            } catch (e: IllegalArgumentException) {
                return@withContext ConnectionResult.Failure(FailureReason.INVALID_URL)
            }

            val start = System.currentTimeMillis()
            try {
                client.newCall(request).execute().use { response ->
                    val latency = System.currentTimeMillis() - start
                    if (response.isSuccessful) {
                        return@use ConnectionResult.Success(latency)
                    }

                    val body = runCatching { response.body?.string() }.getOrNull()
                    val (reason, detail) = mapFailure(response.code, body, settings.apiKey)
                    ConnectionResult.Failure(reason, detail)
                }
            } catch (e: SocketTimeoutException) {
                ConnectionResult.Failure(FailureReason.TIMEOUT)
            } catch (e: IOException) {
                ConnectionResult.Failure(FailureReason.NETWORK)
            }
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
            val root = JSONTokener(body).nextValue()
            val array: JSONArray? = when (root) {
                is JSONArray -> root
                is JSONObject -> root.optJSONArray("data") ?: root.optJSONArray("models")
                else -> null
            }
            if (array == null) return emptyList()

            buildList {
                for (i in 0 until array.length()) {
                    val item = array.optJSONObject(i) ?: continue
                    val id = item.optString("id").ifBlank { item.optString("name") }
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
            .readTimeout(20, TimeUnit.SECONDS)
            .callTimeout(30, TimeUnit.SECONDS)
            .build()
    }
}
