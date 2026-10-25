package com.kanadecn.note.data.notes

import android.content.ActivityNotFoundException
import android.content.Context
import android.content.Intent
import android.net.Uri
import com.kanadecn.note.data.local.Note

/**
 * 通过 Obsidian URI 打开指定笔记：
 * obsidian://open?vault=<仓库名>&file=<相对路径>
 */
class ObsidianLauncher(
    private val context: Context,
    private val noteFolderStore: NoteFolderStore,
) {

    fun open(note: Note): Boolean {
        val vault = noteFolderStore.savedName()?.trim().orEmpty()
        if (vault.isBlank()) return false

        val file = note.relativePath
            .let { if (it.endsWith(".md", ignoreCase = true)) it.dropLast(3) else it }
            .ifBlank { note.title }
            .replace('\\', '/')

        val uri = Uri.Builder()
            .scheme("obsidian")
            .authority("open")
            .appendQueryParameter("vault", vault)
            .appendQueryParameter("file", file)
            .build()

        val intent = Intent(Intent.ACTION_VIEW, uri)
            .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)

        return try {
            context.startActivity(intent)
            true
        } catch (_: ActivityNotFoundException) {
            false
        }
    }
}
