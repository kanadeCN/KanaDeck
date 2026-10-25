package com.kanadecn.note.data.local

import androidx.room.Entity
import androidx.room.PrimaryKey

@Entity(tableName = "mistakes")
data class Mistake(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    val imagePath: String,
    val question: String,
    val myAnswer: String,
    val correctAnswer: String,
    val isCorrect: Boolean,
    val errorType: String,
    val knowledgePoint: String,
    val explanation: String,
    val createdAt: Long,
    val category: String = "未分类",
)
