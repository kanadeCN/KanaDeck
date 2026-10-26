package com.kanadecn.note.util

import android.content.Context
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.net.Uri
import android.os.Build
import java.io.ByteArrayOutputStream
import java.io.File

data class PreparedImage(
    val apiJpeg: ByteArray,
    val storedWebp: ByteArray,
)

object ImageUtils {

    /**
     * 解码相册图片，缩放后同时产出：
     * - apiJpeg：发给多模态模型（兼容性最好）
     * - storedWebp：存本地（体积小、Android 原生可解码）
     */
    fun prepare(
        context: Context,
        uri: Uri,
        maxDimension: Int = 1600,
        jpegQuality: Int = 80,
        webpQuality: Int = 70,
    ): PreparedImage? {
        // 只读尺寸，不把原始文件整个读进内存
        val bounds = BitmapFactory.Options().apply { inJustDecodeBounds = true }
        val hasBounds = runCatching {
            context.contentResolver.openInputStream(uri)?.use {
                BitmapFactory.decodeStream(it, null, bounds)
                true
            } ?: false
        }.getOrDefault(false)

        if (!hasBounds || bounds.outWidth <= 0 || bounds.outHeight <= 0) return null

        // 采样后仍不小于目标尺寸，避免解码出过大的临时位图
        var sampleSize = 1
        while (bounds.outWidth / (sampleSize * 2) >= maxDimension ||
            bounds.outHeight / (sampleSize * 2) >= maxDimension
        ) {
            sampleSize *= 2
        }

        val options = BitmapFactory.Options().apply { inSampleSize = sampleSize }
        val decoded = try {
            context.contentResolver.openInputStream(uri)?.use {
                BitmapFactory.decodeStream(it, null, options)
            }
        } catch (_: Exception) {
            null
        } ?: return null

        val scale = minOf(
            1f,
            maxDimension.toFloat() / decoded.width,
            maxDimension.toFloat() / decoded.height,
        )

        val scaled = if (scale < 1f) {
            Bitmap.createScaledBitmap(
                decoded,
                (decoded.width * scale).toInt().coerceAtLeast(1),
                (decoded.height * scale).toInt().coerceAtLeast(1),
                true,
            ).also {
                if (it !== decoded) decoded.recycle()
            }
        } else {
            decoded
        }

        val apiJpeg = ByteArrayOutputStream().use { out ->
            scaled.compress(Bitmap.CompressFormat.JPEG, jpegQuality, out)
            out.toByteArray()
        }

        val webpFormat = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.R) {
            Bitmap.CompressFormat.WEBP_LOSSY
        } else {
            @Suppress("DEPRECATION")
            Bitmap.CompressFormat.WEBP
        }

        val storedWebp = ByteArrayOutputStream().use { out ->
            scaled.compress(webpFormat, webpQuality, out)
            out.toByteArray()
        }

        scaled.recycle()
        return PreparedImage(apiJpeg = apiJpeg, storedWebp = storedWebp)
    }

    fun saveMistakeImage(context: Context, webp: ByteArray): String {
        // 外部私有目录：/storage/emulated/0/Android/data/<包名>/files/mistakes
        // 外部存储不可用时退回内部私有目录
        val dir = context.getExternalFilesDir("mistakes")
            ?: File(context.filesDir, "mistakes")
        dir.mkdirs()

        val file = File(dir, "mistake_${System.currentTimeMillis()}.webp")
        file.writeBytes(webp)
        return file.absolutePath
    }
}
