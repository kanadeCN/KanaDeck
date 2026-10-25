package com.kanadecn.note.data.local

import androidx.room.Dao
import androidx.room.Delete
import androidx.room.Insert
import androidx.room.Query

@Dao
interface MistakeDao {

    @Query("SELECT * FROM mistakes ORDER BY createdAt DESC")
    suspend fun getAll(): List<Mistake>

    @Query("SELECT COUNT(*) FROM mistakes")
    suspend fun count(): Int

    @Query("SELECT * FROM mistakes WHERE id = :id LIMIT 1")
    suspend fun getById(id: Long): Mistake?

    @Insert
    suspend fun insert(mistake: Mistake): Long

    @Delete
    suspend fun delete(mistake: Mistake)
}
