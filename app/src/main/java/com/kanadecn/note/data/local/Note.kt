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
    // 相对 vault 的路径，例如 "数学/二次函数.md"，用于拉起 Obsidian
    val relativePath: String = "",
)
