package com.kanadecn.note.ui.navigation

object AppRoute {
    const val NOTES = "notes"
    const val MISTAKES = "mistakes"
    const val SEARCH = "search"
    const val SETTINGS = "settings"

    const val MISTAKE_ID_ARG = "mistakeId"
    const val MISTAKE_DETAIL = "mistake/{mistakeId}"

    val topLevel: Set<String> = setOf(NOTES, MISTAKES)

    fun mistakeDetail(id: Long): String = "mistake/$id"
}
