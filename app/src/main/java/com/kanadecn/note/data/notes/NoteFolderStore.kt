package com.kanadecn.note.data.notes

import android.content.Context
import android.net.Uri

/**
 * 记住用户选择的笔记文件夹（非敏感，用普通 SharedPreferences）。
 */
class NoteFolderStore(context: Context) {

    private val prefs = context.applicationContext
        .getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)

    fun savedUri(): Uri? = prefs.getString(KEY_URI, null)?.let(Uri::parse)

    fun savedName(): String? = prefs.getString(KEY_NAME, null)

    fun save(uri: Uri, name: String?) {
        prefs.edit()
            .putString(KEY_URI, uri.toString())
            .putString(KEY_NAME, name)
            .apply()
    }

    companion object {
        private const val PREFS_NAME = "note_folder"
        private const val KEY_URI = "uri"
        private const val KEY_NAME = "name"
    }
}
