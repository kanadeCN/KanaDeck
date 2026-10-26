package com.kanadecn.note.data.local

import androidx.room.Entity
import androidx.room.Index
import androidx.room.PrimaryKey

@Entity(
    tableName = "notes",
    indices = [Index(value = ["path"], unique = true)],
)
data class Note(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    val path: String,
    val title: String,
    val content: String,
    val updatedAt: Long,
    // 文件大小，和 updatedAt 一起用于判断笔记是否需要重新读取
    val size: Long = 0,
    // 相对 vault 的路径，例如 "数学/二次函数.md"，用于拉起 Obsidian
    val relativePath: String = "",
)

/**
 * 增量同步只读这些元数据，避免把全部笔记正文加载进内存。
 */
data class NoteMetadata(
    val path: String,
    val updatedAt: Long,
    val size: Long,
)
