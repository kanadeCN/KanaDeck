package com.kanadecn.note.data.mistakes

import com.kanadecn.note.data.local.Mistake
import com.kanadecn.note.data.local.MistakeDao

class MistakeRepository(
    private val dao: MistakeDao,
) {
    suspend fun count(): Int = dao.count()

    suspend fun getAll(): List<Mistake> = dao.getAll()

    suspend fun getById(id: Long): Mistake? = dao.getById(id)

    suspend fun add(mistake: Mistake): Long = dao.insert(mistake)

    suspend fun delete(mistake: Mistake) = dao.delete(mistake)
}
