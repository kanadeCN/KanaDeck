package com.kanadecn.note.data.mistakes

import android.content.Context
import android.net.Uri
import com.kanadecn.note.data.remote.AiClient
import com.kanadecn.note.data.remote.FailureReason
import com.kanadecn.note.data.remote.MistakeRecognitionResult
import com.kanadecn.note.data.remote.RecognizedMistake
import com.kanadecn.note.data.settings.SecureSettingsStore
import com.kanadecn.note.util.ImageUtils
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

sealed interface RecognizeOutcome {
    data class Success(
        val draft: RecognizedMistake,
        val imagePath: String,
    ) : RecognizeOutcome

    data class Failure(
        val reason: FailureReason,
        val detail: String? = null,
    ) : RecognizeOutcome

    data object NotConfigured : RecognizeOutcome
}

/**
 * 统一的错题识别流程：压缩图片 → 调多模态模型 → 存本地 WebP。
 */
class MistakeRecognizer(
    private val context: Context,
    private val aiClient: AiClient,
    private val settingsStore: SecureSettingsStore,
    private val categoryStore: CategoryStore,
) {

    suspend fun recognize(uri: Uri): RecognizeOutcome = withContext(Dispatchers.IO) {
        val settings = settingsStore.load()
        if (!settings.isConfigured) {
            return@withContext RecognizeOutcome.NotConfigured
        }

        val prepared = ImageUtils.prepare(context, uri)
            ?: return@withContext RecognizeOutcome.Failure(
                FailureReason.UNKNOWN,
                "图片读取失败",
            )

        return@withContext when (
            val result = aiClient.recognizeMistake(
                settings = settings,
                imageJpeg = prepared.apiJpeg,
                categories = categoryStore.allCategories(),
            )
        ) {
            is MistakeRecognitionResult.Success -> {
                val path = ImageUtils.saveMistakeImage(context, prepared.storedWebp)
                RecognizeOutcome.Success(result.data, path)
            }

            is MistakeRecognitionResult.Failure -> {
                RecognizeOutcome.Failure(result.reason, result.detail)
            }
        }
    }
}
