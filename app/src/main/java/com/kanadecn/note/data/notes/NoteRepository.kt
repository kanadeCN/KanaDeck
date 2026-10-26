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

        // 只读 path/updatedAt/size，避免把全部笔记正文加载进内存
        val existing = noteDao.getAllMetadata().associateBy { it.path }

        val discoveredPaths = mutableSetOf<String>()
        val pending = mutableListOf<Note>()
        var failed = 0

        suspend fun flushPending() {
            if (pending.isNotEmpty()) {
                noteDao.insertAll(pending)
                pending.clear()
            }
        }

        suspend fun visit(dir: DocumentFile, relativeDir: String) {
            for (file in dir.listFiles()) {
                val name = file.name.orEmpty()

                if (file.isDirectory) {
                    // 跳过 .obsidian 等隐藏目录
                    if (!name.startsWith(".")) {
                        val childRelative = if (relativeDir.isBlank()) {
                            name
                        } else {
                            "$relativeDir/$name"
                        }
                        visit(file, childRelative)
                    }
                    continue
                }

                if (!name.endsWith(".md", ignoreCase = true)) continue

                val path = file.uri.toString()
                discoveredPaths += path

                val lastModified = file.lastModified()
                val size = file.length()
                val meta = existing[path]

                // 修改时间和大小都没变：跳过读取正文
                if (meta != null &&
                    lastModified > 0 &&
                    meta.updatedAt == lastModified &&
                    meta.size == size
                ) {
                    continue
                }

                val relativePath = if (relativeDir.isBlank()) {
                    name
                } else {
                    "$relativeDir/$name"
                }

                val content = try {
                    context.contentResolver
                        .openInputStream(file.uri)
                        ?.bufferedReader()
                        ?.use { it.readText() }
                } catch (_: Exception) {
                    null
                }

                // 读取失败时仍保留旧记录（path 已计入 discoveredPaths，不会被清理）
                if (content == null) {
                    failed++
                    continue
                }

                pending += Note(
                    path = path,
                    title = extractTitle(name, content),
                    content = content,
                    updatedAt = lastModified,
                    size = size,
                    relativePath = relativePath,
                )

                if (pending.size >= INSERT_BATCH_SIZE) {
                    flushPending()
                }
            }
        }

        visit(root, "")
        flushPending()

        // 全量扫描到 0 篇时不动，避免权限异常导致清空；
        // 按具体路径分批删除，避免 SQLite 变量数量超限。
        if (discoveredPaths.isNotEmpty()) {
            (existing.keys - discoveredPaths)
                .chunked(DELETE_BATCH_SIZE)
                .forEach { noteDao.deleteByPaths(it) }
        }

        ImportResult(imported = discoveredPaths.size - failed, failed = failed)
    }

    private fun extractTitle(fileName: String, content: String): String {
        val heading = content.lineSequence()
            .firstOrNull { it.startsWith("# ") }
            ?.removePrefix("# ")
            ?.trim()
            .orEmpty()

        return heading.ifBlank { fileName.substringBeforeLast('.') }
    }

    companion object {
        private const val INSERT_BATCH_SIZE = 50
        private const val DELETE_BATCH_SIZE = 500
    }
}
