package com.kanadecn.note

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.compose.animation.*
import androidx.compose.animation.core.tween
import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.foundation.layout.*
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Surface
import androidx.compose.runtime.*
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.navigation.NavBackStackEntry
import androidx.navigation.NavGraph.Companion.findStartDestination
import androidx.navigation.NavHostController
import androidx.navigation.NavType
import androidx.navigation.navArgument
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import androidx.navigation.compose.currentBackStackEntryAsState
import androidx.navigation.compose.rememberNavController
import com.kanadecn.note.data.local.AppDatabase
import com.kanadecn.note.data.mistakes.MistakeRepository
import com.kanadecn.note.data.notes.NoteFolderImporter
import com.kanadecn.note.data.notes.NoteFolderStore
import com.kanadecn.note.data.notes.NoteRepository
import com.kanadecn.note.data.notes.ObsidianLauncher
import com.kanadecn.note.data.remote.AiClient
import com.kanadecn.note.data.settings.SecureSettingsStore
import com.kanadecn.note.data.settings.ThemeMode
import com.kanadecn.note.data.settings.ThemePreferences
import com.kanadecn.note.ui.components.DockOverlay
import com.kanadecn.note.ui.components.DockTab
import com.kanadecn.note.ui.home.HomeScreen
import com.kanadecn.note.ui.mistake.MistakeDetailScreen
import com.kanadecn.note.ui.mistake.MistakeScreen
import com.kanadecn.note.ui.navigation.AppRoute
import com.kanadecn.note.ui.search.SearchScreen
import com.kanadecn.note.ui.settings.SettingsScreen
import com.kanadecn.note.ui.theme.KanaDeckTheme

class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()

        val secureSettingsStore = SecureSettingsStore(applicationContext)
        val themePreferences = ThemePreferences(applicationContext)
        val aiClient = AiClient()
        val database = AppDatabase.get(applicationContext)
        val noteRepository = NoteRepository(applicationContext, database.noteDao())
        val mistakeRepository = MistakeRepository(database.mistakeDao())
        val noteFolderStore = NoteFolderStore(applicationContext)
        val noteFolderImporter = NoteFolderImporter(
            context = applicationContext,
            noteRepository = noteRepository,
            noteFolderStore = noteFolderStore,
        )
        val obsidianLauncher = ObsidianLauncher(
            context = applicationContext,
            noteFolderStore = noteFolderStore,
        )

        setContent {
            var themeMode by remember { mutableStateOf(themePreferences.load()) }
            var selectedTabName by rememberSaveable { mutableStateOf(DockTab.NOTES.name) }
            val selectedTab = DockTab.valueOf(selectedTabName)

            val systemDark = isSystemInDarkTheme()
            val darkTheme = when (themeMode) {
                ThemeMode.SYSTEM -> systemDark
                ThemeMode.LIGHT -> false
                ThemeMode.DARK -> true
            }

            KanaDeckTheme(darkTheme = darkTheme) {
                Surface(
                    modifier = Modifier.fillMaxSize(),
                    color = MaterialTheme.colorScheme.background,
                ) {
                    val navController = rememberNavController()
                    val backStackEntry by navController.currentBackStackEntryAsState()
                    val currentRoute = backStackEntry?.destination?.route
                    val showDock = currentRoute == null || currentRoute in AppRoute.topLevel

                    Scaffold(
                        // 系统栏只处理底部导航栏；顶部交给各页面的 TopAppBar
                        contentWindowInsets = WindowInsets.navigationBars,
                    ) { innerPadding ->
                        Box(
                            modifier = Modifier
                                .fillMaxSize()
                                .padding(innerPadding),
                        ) {
                            NavHost(
                                navController = navController,
                                startDestination = AppRoute.NOTES,
                                modifier = Modifier.fillMaxSize(),
                                enterTransition = {
                                    if (isTabSwitch()) {
                                        fadeIn(tween(200))
                                    } else {
                                        slideInHorizontally(tween(300)) { width -> width } +
                                            fadeIn(tween(300))
                                    }
                                },
                                exitTransition = {
                                    if (isTabSwitch()) {
                                        fadeOut(tween(200))
                                    } else {
                                        slideOutHorizontally(tween(300)) { width -> -width / 4 } +
                                            fadeOut(tween(300))
                                    }
                                },
                                popEnterTransition = {
                                    if (isTabSwitch()) {
                                        fadeIn(tween(200))
                                    } else {
                                        slideInHorizontally(tween(300)) { width -> -width / 4 } +
                                            fadeIn(tween(300))
                                    }
                                },
                                popExitTransition = {
                                    if (isTabSwitch()) {
                                        fadeOut(tween(200))
                                    } else {
                                        slideOutHorizontally(tween(300)) { width -> width } +
                                            fadeOut(tween(300))
                                    }
                                },
                            ) {
                                composable(AppRoute.NOTES) {
                                    HomeScreen(
                                        noteRepository = noteRepository,
                                        noteFolderImporter = noteFolderImporter,
                                        onOpenSearch = { navController.navigate(AppRoute.SEARCH) },
                                        onOpenSettings = { navController.navigate(AppRoute.SETTINGS) },
                                    )
                                }

                                composable(AppRoute.MISTAKES) {
                                    MistakeScreen(
                                        mistakeRepository = mistakeRepository,
                                        aiClient = aiClient,
                                        settingsStore = secureSettingsStore,
                                        onOpenDetail = { mistake ->
                                            navController.navigate(AppRoute.mistakeDetail(mistake.id))
                                        },
                                        onOpenSettings = { navController.navigate(AppRoute.SETTINGS) },
                                    )
                                }

                                composable(
                                    route = AppRoute.MISTAKE_DETAIL,
                                    arguments = listOf(
                                        navArgument(AppRoute.MISTAKE_ID_ARG) {
                                            type = NavType.LongType
                                        },
                                    ),
                                ) { entry ->
                                    val mistakeId = entry.arguments
                                        ?.getLong(AppRoute.MISTAKE_ID_ARG)
                                        ?: return@composable

                                    MistakeDetailScreen(
                                        mistakeId = mistakeId,
                                        mistakeRepository = mistakeRepository,
                                        onBack = { navController.popBackStack() },
                                    )
                                }

                                composable(AppRoute.SEARCH) {
                                    SearchScreen(
                                        noteRepository = noteRepository,
                                        obsidianLauncher = obsidianLauncher,
                                        onBack = { navController.popBackStack() },
                                    )
                                }

                                composable(AppRoute.SETTINGS) {
                                    SettingsScreen(
                                        settingsStore = secureSettingsStore,
                                        aiClient = aiClient,
                                        noteRepository = noteRepository,
                                        noteFolderImporter = noteFolderImporter,
                                        selectedThemeMode = themeMode,
                                        onThemeModeChange = { newMode ->
                                            themeMode = newMode
                                            themePreferences.save(newMode)
                                        },
                                        onBack = { navController.popBackStack() },
                                    )
                                }
                            }

                            DockOverlay(
                                visible = showDock,
                                selected = selectedTab,
                                onSelect = { tab ->
                                    selectedTabName = tab.name
                                    navController.navigateToTab(tab.route)
                                },
                                modifier = Modifier.align(Alignment.BottomCenter),
                            )
                        }
                    }
                }
            }
        }
    }
}

/**
 * 底部 tab 的标准导航方式：保留起始页，切换时保存/恢复各 tab 状态。
 */
private fun NavHostController.navigateToTab(route: String) {
    navigate(route) {
        popUpTo(graph.findStartDestination().id) { saveState = true }
        launchSingleTop = true
        restoreState = true
    }
}

/**
 * 笔记 ↔ 错题 之间用淡入淡出，进入子页面才用滑动。
 */
private fun AnimatedContentTransitionScope<NavBackStackEntry>.isTabSwitch(): Boolean {
    val from = initialState.destination.route
    val to = targetState.destination.route
    return from != null && to != null &&
        from in AppRoute.topLevel && to in AppRoute.topLevel
}
