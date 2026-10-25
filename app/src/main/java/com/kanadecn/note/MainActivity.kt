package com.kanadecn.note

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.compose.animation.*
import androidx.compose.animation.core.tween
import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.runtime.*
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.ui.Modifier
import com.kanadecn.note.data.local.AppDatabase
import com.kanadecn.note.data.notes.NoteFolderImporter
import com.kanadecn.note.data.notes.NoteFolderStore
import com.kanadecn.note.data.notes.NoteRepository
import com.kanadecn.note.data.remote.AiClient
import com.kanadecn.note.data.settings.SecureSettingsStore
import com.kanadecn.note.data.settings.ThemeMode
import com.kanadecn.note.data.settings.ThemePreferences
import com.kanadecn.note.ui.home.HomeScreen
import com.kanadecn.note.ui.search.SearchScreen
import com.kanadecn.note.ui.settings.SettingsScreen
import com.kanadecn.note.ui.theme.KanaDeckTheme

private enum class AppScreen {
    HOME,
    SEARCH,
    SETTINGS,
}

class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()

        val secureSettingsStore = SecureSettingsStore(applicationContext)
        val themePreferences = ThemePreferences(applicationContext)
        val aiClient = AiClient()
        val database = AppDatabase.get(applicationContext)
        val noteRepository = NoteRepository(applicationContext, database.noteDao())
        val noteFolderStore = NoteFolderStore(applicationContext)
        val noteFolderImporter = NoteFolderImporter(
            context = applicationContext,
            noteRepository = noteRepository,
            noteFolderStore = noteFolderStore,
        )

        setContent {
            // 用字符串保存，避免 enum 的 Saveable 兼容问题
            var screenName by rememberSaveable { mutableStateOf(AppScreen.HOME.name) }
            val screen = AppScreen.valueOf(screenName)

            var themeMode by remember { mutableStateOf(themePreferences.load()) }

            val systemDark = isSystemInDarkTheme()
            val darkTheme = when (themeMode) {
                ThemeMode.SYSTEM -> systemDark
                ThemeMode.LIGHT -> false
                ThemeMode.DARK -> true
            }

            KanaDeckTheme(darkTheme = darkTheme) {
                // 给动画垫一层主题背景，避免滑动时露出窗口白底
                Surface(
                    modifier = Modifier.fillMaxSize(),
                    color = MaterialTheme.colorScheme.background,
                ) {
                    AnimatedContent(
                        targetState = screen,
                        transitionSpec = {
                            val forward = targetState.ordinal > initialState.ordinal
                            val duration = 300
                            if (forward) {
                                (slideInHorizontally(tween(duration)) { width -> width } +
                                    fadeIn(tween(duration))) togetherWith
                                    (slideOutHorizontally(tween(duration)) { width -> -width / 4 } +
                                        fadeOut(tween(duration)))
                            } else {
                                (slideInHorizontally(tween(duration)) { width -> -width / 4 } +
                                    fadeIn(tween(duration))) togetherWith
                                    (slideOutHorizontally(tween(duration)) { width -> width } +
                                        fadeOut(tween(duration)))
                            }
                        },
                        label = "screenTransition",
                    ) { target ->
                        when (target) {
                            AppScreen.HOME -> HomeScreen(
                                noteRepository = noteRepository,
                                noteFolderImporter = noteFolderImporter,
                                onOpenSearch = { screenName = AppScreen.SEARCH.name },
                                onOpenSettings = { screenName = AppScreen.SETTINGS.name },
                            )

                            AppScreen.SEARCH -> SearchScreen(
                                noteRepository = noteRepository,
                                onBack = { screenName = AppScreen.HOME.name },
                            )

                            AppScreen.SETTINGS -> SettingsScreen(
                                settingsStore = secureSettingsStore,
                                aiClient = aiClient,
                                noteRepository = noteRepository,
                                noteFolderImporter = noteFolderImporter,
                                selectedThemeMode = themeMode,
                                onThemeModeChange = { newMode ->
                                    themeMode = newMode
                                    themePreferences.save(newMode)
                                },
                                onBack = { screenName = AppScreen.HOME.name },
                            )
                        }
                    }
                }
            }
        }
    }
}
