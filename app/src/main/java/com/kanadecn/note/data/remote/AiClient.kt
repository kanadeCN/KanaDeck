package com.kanadecn.note.data.remote

import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import okhttp3.OkHttpClient
import okhttp3.Request
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
        private fun defaultClient(): OkHttpClient = OkHttpClient.Builder()
            .connectTimeout(10, TimeUnit.SECONDS)
            .readTimeout(15, TimeUnit.SECONDS)
            // 整个请求最多 15 秒，避免获取模型时按钮一直转
            .callTimeout(15, TimeUnit.SECONDS)
            .build()
    }
}
