package com.kanadecn.note.data.settings

import android.content.Context
import android.content.SharedPreferences
import androidx.security.crypto.EncryptedSharedPreferences
import androidx.security.crypto.MasterKey

/**
 * 用户自己的 AI 服务配置（BYOK）。
 * Base URL 兼容 OpenAI 格式的厂商都能用。
 */
data class LlmSettings(
    val baseUrl: String = "",
    val apiKey: String = "",
    val model: String = "",
) {
    val isConfigured: Boolean
        get() = baseUrl.isNotBlank() && apiKey.isNotBlank() && model.isNotBlank()

    // 防止误打印整个对象时把 key 带进日志
    override fun toString(): String =
        "LlmSettings(baseUrl=$baseUrl, apiKey=***, model=$model)"
}

/**
 * 用 EncryptedSharedPreferences 加密保存 AI 配置。
 * 密钥由 Android Keystore 保护，不落明文。
 */
class SecureSettingsStore(context: Context) {

    private val appContext = context.applicationContext

    private val prefs: SharedPreferences by lazy {
        val masterKey = MasterKey.Builder(appContext)
            .setKeyScheme(MasterKey.KeyScheme.AES256_GCM)
            .build()

        EncryptedSharedPreferences.create(
            appContext,
            PREFS_NAME,
            masterKey,
            EncryptedSharedPreferences.PrefKeyEncryptionScheme.AES256_SIV,
            EncryptedSharedPreferences.PrefValueEncryptionScheme.AES256_GCM,
        )
    }

    fun load(): LlmSettings = LlmSettings(
        baseUrl = prefs.getString(KEY_BASE_URL, "").orEmpty(),
        apiKey = prefs.getString(KEY_API_KEY, "").orEmpty(),
        model = prefs.getString(KEY_MODEL, "").orEmpty(),
    )

    fun save(settings: LlmSettings) {
        prefs.edit()
            .putString(KEY_BASE_URL, settings.baseUrl.trim())
            .putString(KEY_API_KEY, settings.apiKey.trim())
            .putString(KEY_MODEL, settings.model.trim())
            .apply()
    }

    companion object {
        private const val PREFS_NAME = "secure_settings"
        private const val KEY_BASE_URL = "base_url"
        private const val KEY_API_KEY = "api_key"
        private const val KEY_MODEL = "model"
    }
}
