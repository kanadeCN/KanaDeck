package com.kanadecn.note.ui.navigation

import android.net.Uri

object AppRoute {
    const val NOTES = "notes"
    const val MISTAKES = "mistakes"
    const val SEARCH = "search"
    const val SETTINGS = "settings"

    const val MISTAKE_ID_ARG = "mistakeId"
    const val MISTAKE_DETAIL = "mistake/{mistakeId}"

    const val MISTAKE_CATEGORY_ARG = "category"
    const val MISTAKE_CATEGORY = "mistakeCategory/{category}"

    val topLevel: Set<String> = setOf(NOTES, MISTAKES)

    fun mistakeDetail(id: Long): String = "mistake/$id"

    fun mistakeCategory(category: String): String =
        "mistakeCategory/${Uri.encode(category)}"
}
