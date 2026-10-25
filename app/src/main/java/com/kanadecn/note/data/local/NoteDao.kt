package com.kanadecn.note.data.local

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query

@Dao
interface NoteDao {

    @Query("SELECT COUNT(*) FROM notes")
    suspend fun count(): Int

    @Query("SELECT * FROM notes ORDER BY updatedAt DESC")
    suspend fun getAll(): List<Note>

    @Query(
        """
        SELECT * FROM notes
        WHERE title LIKE '%' || :query || '%'
           OR content LIKE '%' || :query || '%'
        ORDER BY updatedAt DESC
        LIMIT 50
        """,
    )
    suspend fun search(query: String): List<Note>

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertAll(notes: List<Note>)
}
