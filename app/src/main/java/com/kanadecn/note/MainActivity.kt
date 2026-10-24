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
import com.kanadecn.note.data.remote.AiClient
import com.kanadecn.note.data.settings.SecureSettingsStore
import com.kanadecn.note.data.settings.ThemeMode
import com.kanadecn.note.data.settings.ThemePreferences
import com.kanadecn.note.ui.home.HomeScreen
import com.kanadecn.note.ui.settings.SettingsScreen
import com.kanadecn.note.ui.theme.KanaDeckTheme

class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()

        val secureSettingsStore = SecureSettingsStore(applicationContext)
        val themePreferences = ThemePreferences(applicationContext)
        val aiClient = AiClient()

        setContent {
            var showSettings by rememberSaveable { mutableStateOf(false) }
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
                        targetState = showSettings,
                        transitionSpec = {
                            val duration = 300
                            if (targetState) {
                                // 进入设置页：从右滑入，主页稍微左移
                                (slideInHorizontally(tween(duration)) { width -> width } +
                                    fadeIn(tween(duration))) togetherWith
                                    (slideOutHorizontally(tween(duration)) { width -> -width / 4 } +
                                        fadeOut(tween(duration)))
                            } else {
                                // 返回主页：从左侧滑回，设置页向右退出
                                (slideInHorizontally(tween(duration)) { width -> -width / 4 } +
                                    fadeIn(tween(duration))) togetherWith
                                    (slideOutHorizontally(tween(duration)) { width -> width } +
                                        fadeOut(tween(duration)))
                            }
                        },
                        label = "screenTransition",
                    ) { isSettings ->
                        if (isSettings) {
                            SettingsScreen(
                                settingsStore = secureSettingsStore,
                                aiClient = aiClient,
                                selectedThemeMode = themeMode,
                                onThemeModeChange = { newMode ->
                                    themeMode = newMode
                                    themePreferences.save(newMode)
                                },
                                onBack = { showSettings = false },
                            )
                        } else {
                            HomeScreen(
                                onOpenSettings = { showSettings = true },
                            )
                        }
                    }
                }
            }
        }
    }
}
