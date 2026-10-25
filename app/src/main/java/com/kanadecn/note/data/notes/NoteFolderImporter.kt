package com.kanadecn.note.data.notes

import android.content.Context
import android.content.Intent
import android.net.Uri
import androidx.documentfile.provider.DocumentFile
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

data class ImportOutcome(
    val folderName: String,
    val imported: Int,
    val failed: Int,
)

/**
 * 统一处理：持久化 SAF 权限 → 记录文件夹 → 导入笔记。
 * 主页和设置页共用，避免逻辑重复。
 */
class NoteFolderImporter(
    private val context: Context,
    private val noteRepository: NoteRepository,
    private val noteFolderStore: NoteFolderStore,
) {

    fun savedFolderName(): String? = noteFolderStore.savedName()

    suspend fun import(uri: Uri): ImportOutcome = withContext(Dispatchers.IO) {
        runCatching {
            context.contentResolver.takePersistableUriPermission(
                uri,
                Intent.FLAG_GRANT_READ_URI_PERMISSION,
            )
        }

        val name = runCatching {
            DocumentFile.fromTreeUri(context, uri)?.name
        }.getOrNull()

        noteFolderStore.save(uri, name)

        val result = noteRepository.importFromTree(uri)
        ImportOutcome(
            folderName = name ?: "已选择",
            imported = result.imported,
            failed = result.failed,
        )
    }
}
