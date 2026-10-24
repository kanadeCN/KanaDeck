package com.kanadecn.note.data.settings

import android.content.Context

enum class ThemeMode {
    SYSTEM,
    LIGHT,
    DARK,
}

/**
 * 主题偏好（不敏感，用普通 SharedPreferences 即可）。
 */
class ThemePreferences(context: Context) {

    private val prefs = context.applicationContext
        .getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)

    fun load(): ThemeMode {
        val name = prefs.getString(KEY_THEME_MODE, ThemeMode.SYSTEM.name)
        return runCatching { ThemeMode.valueOf(name.orEmpty()) }
            .getOrDefault(ThemeMode.SYSTEM)
    }

    fun save(mode: ThemeMode) {
        prefs.edit().putString(KEY_THEME_MODE, mode.name).apply()
    }

    companion object {
        private const val PREFS_NAME = "theme_settings"
        private const val KEY_THEME_MODE = "theme_mode"
    }
}
