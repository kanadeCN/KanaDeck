package com.kanadecn.note.data.mistakes

import android.content.Context

/**
 * 错题分类：默认分类 + 用户自定义分类。
 * 默认分类可以被重命名/删除（记录在 hiddenDefaults 里），也可以重新加回来。
 */
class CategoryStore(context: Context) {

    private val prefs = context.applicationContext
        .getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)

    fun customCategories(): List<String> =
        prefs.getStringSet(KEY_CUSTOM, emptySet())
            .orEmpty()
            .sorted()

    private fun hiddenDefaults(): Set<String> =
        prefs.getStringSet(KEY_HIDDEN, emptySet()).orEmpty()

    fun allCategories(): List<String> {
        val hidden = hiddenDefaults()
        return (
            DEFAULT_CATEGORIES.filterNot { it in hidden } +
                customCategories() +
                UNCATEGORIZED
            ).distinct()
    }

    /**
     * 添加自定义分类，返回最新分类列表。
     */
    fun add(name: String): List<String> {
        val clean = name.trim()
        if (clean.isBlank() || clean == UNCATEGORIZED) return allCategories()

        if (clean in DEFAULT_CATEGORIES) {
            // 如果是被隐藏的默认分类，重新显示
            prefs.edit()
                .putStringSet(KEY_HIDDEN, hiddenDefaults() - clean)
                .apply()
        } else {
            prefs.edit()
                .putStringSet(KEY_CUSTOM, customCategories().toSet() + clean)
                .apply()
        }
        return allCategories()
    }

    /**
     * 重命名分类。默认分类被重命名时隐藏原名字，新名字作为自定义分类。
     */
    fun rename(oldName: String, newName: String): List<String> {
        val old = oldName.trim()
        val new = newName.trim()
        if (old == UNCATEGORIZED || new.isBlank() || new == old) return allCategories()

        val hidden = hiddenDefaults().toMutableSet()
        val custom = customCategories().toMutableSet()

        if (old in DEFAULT_CATEGORIES) {
            hidden += old
        } else {
            custom -= old
        }

        if (new in DEFAULT_CATEGORIES) {
            hidden -= new
        } else if (new != UNCATEGORIZED) {
            custom += new
        }

        prefs.edit()
            .putStringSet(KEY_HIDDEN, hidden)
            .putStringSet(KEY_CUSTOM, custom)
            .apply()

        return allCategories()
    }

    /**
     * 删除分类（默认分类则隐藏）。分类下的错题由调用方移动到未分类。
     */
    fun delete(name: String): List<String> {
        val clean = name.trim()
        if (clean == UNCATEGORIZED) return allCategories()

        val hidden = hiddenDefaults().toMutableSet()
        val custom = customCategories().toMutableSet()

        if (clean in DEFAULT_CATEGORIES) {
            hidden += clean
        } else {
            custom -= clean
        }

        prefs.edit()
            .putStringSet(KEY_HIDDEN, hidden)
            .putStringSet(KEY_CUSTOM, custom)
            .apply()

        return allCategories()
    }

    companion object {
        val DEFAULT_CATEGORIES = listOf("高等数学", "线性代数", "编程算法")
        const val UNCATEGORIZED = "未分类"

        private const val PREFS_NAME = "mistake_categories"
        private const val KEY_CUSTOM = "custom"
        private const val KEY_HIDDEN = "hidden_defaults"
    }
}
