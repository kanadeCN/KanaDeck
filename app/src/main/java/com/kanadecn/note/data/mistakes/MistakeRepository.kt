package com.kanadecn.note.data.mistakes

import com.kanadecn.note.data.local.Mistake
import com.kanadecn.note.data.local.MistakeDao

class MistakeRepository(
    private val dao: MistakeDao,
) {
    suspend fun count(): Int = dao.count()

    suspend fun getAll(): List<Mistake> = dao.getAll()

    suspend fun getById(id: Long): Mistake? = dao.getById(id)

    suspend fun getByCategory(category: String): List<Mistake> = dao.getByCategory(category)

    suspend fun countByCategory(): Map<String, Int> =
        dao.countByCategory().associate { it.category to it.count }

    suspend fun add(mistake: Mistake): Long = dao.insert(mistake)

    suspend fun update(mistake: Mistake) = dao.update(mistake)

    suspend fun delete(mistake: Mistake) = dao.delete(mistake)

    suspend fun updateCategory(oldCategory: String, newCategory: String) =
        dao.updateCategory(oldCategory, newCategory)
}
