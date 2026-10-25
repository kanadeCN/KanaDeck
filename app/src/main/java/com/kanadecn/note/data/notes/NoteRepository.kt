package com.kanadecn.note.data.notes

import android.content.Context
import android.net.Uri
import androidx.documentfile.provider.DocumentFile
import com.kanadecn.note.data.local.Note
import com.kanadecn.note.data.local.NoteDao
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

data class ImportResult(
    val imported: Int,
    val failed: Int,
)

/**
 * 通过 SAF 从用户选的文件夹递归读取 .md，写进 Room。
 */
class NoteRepository(
    private val context: Context,
    private val noteDao: NoteDao,
) {

    suspend fun count(): Int = noteDao.count()

    suspend fun search(query: String): List<Note> = noteDao.search(query)

    suspend fun importFromTree(treeUri: Uri): ImportResult = withContext(Dispatchers.IO) {
        val root = DocumentFile.fromTreeUri(context, treeUri)
            ?: return@withContext ImportResult(imported = 0, failed = 0)

        val collected = mutableListOf<Note>()
        var failed = 0

        fun visit(dir: DocumentFile) {
            for (file in dir.listFiles()) {
                val name = file.name.orEmpty()

                if (file.isDirectory) {
                    // 跳过 .obsidian 等隐藏目录
                    if (!name.startsWith(".")) visit(file)
                    continue
                }

                if (!name.endsWith(".md", ignoreCase = true)) continue

                try {
                    val content = context.contentResolver
                        .openInputStream(file.uri)
                        ?.bufferedReader()
                        ?.use { it.readText() }
                        ?: continue

                    collected += Note(
                        path = file.uri.toString(),
                        title = extractTitle(name, content),
                        content = content,
                        updatedAt = file.lastModified(),
                    )
                } catch (e: Exception) {
                    failed++
                }
            }
        }

        visit(root)
        noteDao.insertAll(collected)
        ImportResult(imported = collected.size, failed = failed)
    }

    private fun extractTitle(fileName: String, content: String): String {
        val heading = content.lineSequence()
            .firstOrNull { it.startsWith("# ") }
            ?.removePrefix("# ")
            ?.trim()
            .orEmpty()

        return heading.ifBlank { fileName.substringBeforeLast('.') }
    }
}
