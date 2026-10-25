package com.kanadecn.note.ui.theme

import android.os.Build
import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.darkColorScheme
import androidx.compose.material3.dynamicDarkColorScheme
import androidx.compose.material3.dynamicLightColorScheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.Immutable
import androidx.compose.runtime.ReadOnlyComposable
import androidx.compose.runtime.staticCompositionLocalOf
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext

private val LightColors = lightColorScheme(
    primary = PrimaryLight,
    secondary = SecondaryLight,
    tertiary = TertiaryLight,
)

private val DarkColors = darkColorScheme(
    primary = PrimaryDark,
    secondary = SecondaryDark,
    tertiary = TertiaryDark,
)

/**
 * 只放 Material 3 没有的语义色（warning / info / success）。
 * M3 已有的角色（primary / error / surface…）请继续用 MaterialTheme.colorScheme。
 *
 * 用时：MaterialTheme.appColors.warning
 */
@Immutable
data class AppColors(
    val warning: Color,
    val onWarning: Color,
    val info: Color,
    val onInfo: Color,
    val success: Color,
    val onSuccess: Color,
)

private val LightAppColors = AppColors(
    warning = WarningLight,
    onWarning = OnWarningLight,
    info = InfoLight,
    onInfo = OnInfoLight,
    success = SuccessLight,
    onSuccess = OnSuccessLight,
)

private val DarkAppColors = AppColors(
    warning = WarningDark,
    onWarning = OnWarningDark,
    info = InfoDark,
    onInfo = OnInfoDark,
    success = SuccessDark,
    onSuccess = OnSuccessDark,
)

private val LocalAppColors = staticCompositionLocalOf { LightAppColors }

val MaterialTheme.appColors: AppColors
    @Composable
    @ReadOnlyComposable
    get() = LocalAppColors.current

@Composable
fun KanaDeckTheme(
    // 默认跟随系统深色模式；系统切换时会触发重组，配色自动切换
    darkTheme: Boolean = isSystemInDarkTheme(),
    // Android 12+ 支持取系统壁纸色
    dynamicColor: Boolean = true,
    content: @Composable () -> Unit,
) {
    val context = LocalContext.current
    val useDynamicColor = dynamicColor && Build.VERSION.SDK_INT >= Build.VERSION_CODES.S

    val colorScheme = when {
        useDynamicColor ->
            if (darkTheme) dynamicDarkColorScheme(context) else dynamicLightColorScheme(context)

        darkTheme -> DarkColors
        else -> LightColors
    }

    // 自定义语义色与官方配色同步：浅色/深色各一套
    val appColors = if (darkTheme) DarkAppColors else LightAppColors

    CompositionLocalProvider(LocalAppColors provides appColors) {
        MaterialTheme(
            colorScheme = colorScheme,
            typography = Typography,
            content = content,
        )
    }
}
