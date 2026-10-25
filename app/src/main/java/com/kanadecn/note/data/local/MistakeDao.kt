package com.kanadecn.note.data.local

import androidx.room.Dao
import androidx.room.Delete
import androidx.room.Insert
import androidx.room.Query
import androidx.room.Update

@Dao
interface MistakeDao {

    @Query("SELECT * FROM mistakes ORDER BY createdAt DESC")
    suspend fun getAll(): List<Mistake>

    @Query("SELECT * FROM mistakes WHERE category = :category ORDER BY createdAt DESC")
    suspend fun getByCategory(category: String): List<Mistake>

    @Query("SELECT category, COUNT(*) as count FROM mistakes GROUP BY category")
    suspend fun countByCategory(): List<CategoryCount>

    @Query("SELECT COUNT(*) FROM mistakes")
    suspend fun count(): Int

    @Query("SELECT * FROM mistakes WHERE id = :id LIMIT 1")
    suspend fun getById(id: Long): Mistake?

    @Insert
    suspend fun insert(mistake: Mistake): Long

    @Update
    suspend fun update(mistake: Mistake)

    @Delete
    suspend fun delete(mistake: Mistake)

    @Query("UPDATE mistakes SET category = :newCategory WHERE category = :oldCategory")
    suspend fun updateCategory(oldCategory: String, newCategory: String)
}
