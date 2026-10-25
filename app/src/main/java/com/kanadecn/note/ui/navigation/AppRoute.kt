package com.kanadecn.note.ui.navigation

object AppRoute {
    const val NOTES = "notes"
    const val MISTAKES = "mistakes"
    const val SEARCH = "search"
    const val SETTINGS = "settings"

    val topLevel: Set<String> = setOf(NOTES, MISTAKES)
}
