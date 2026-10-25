package com.kanadecn.note.ui.components

import androidx.compose.animation.core.animateDpAsState
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.tween
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.List
import androidx.compose.material.icons.filled.Warning
import androidx.compose.material3.*
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.unit.dp
import com.kanadecn.note.ui.navigation.AppRoute

enum class DockTab(
    val route: String,
    val label: String,
    val icon: ImageVector,
) {
    NOTES(AppRoute.NOTES, "笔记", Icons.AutoMirrored.Filled.List),
    MISTAKES(AppRoute.MISTAKES, "错题", Icons.Filled.Warning),
}

/**
 * dock 的所有尺寸参数集中在这里，全局统一。
 */
object DockDefaults {
    val HorizontalPadding = 24.dp
    val VerticalPadding = 12.dp
    val CornerRadius = 28.dp
    val ItemCornerRadius = 22.dp
    val ItemHorizontalPadding = 18.dp
    val ItemVerticalPadding = 10.dp
    val IconSize = 20.dp
    val IconTextSpacing = 6.dp
    val ItemSpacing = 6.dp
    val SurfacePadding = 6.dp
}

/**
 * 作为 Scaffold 的 bottomBar 使用：
 * 高度由 Scaffold 自动测量，页面通过 innerPadding 统一让位。
 */
@Composable
fun DockBar(
    visible: Boolean,
    selected: DockTab,
    onSelect: (DockTab) -> Unit,
    modifier: Modifier = Modifier,
) {
    // 注意：这里只做视觉上的隐藏，不改变布局高度，避免页面上下跳动
    val alpha by animateFloatAsState(
        targetValue = if (visible) 1f else 0f,
        animationSpec = tween(200),
        label = "dockAlpha",
    )
    val offsetY by animateDpAsState(
        targetValue = if (visible) 0.dp else 16.dp,
        animationSpec = tween(200),
        label = "dockOffsetY",
    )

    Box(
        modifier = modifier
            .fillMaxWidth()
            .navigationBarsPadding()
            .padding(
                horizontal = DockDefaults.HorizontalPadding,
                vertical = DockDefaults.VerticalPadding,
            ),
        contentAlignment = Alignment.Center,
    ) {
        FloatingDock(
            selected = selected,
            onSelect = onSelect,
            enabled = visible,
            modifier = Modifier.graphicsLayer {
                this.alpha = alpha
                translationY = offsetY.toPx()
            },
        )
    }
}

@Composable
private fun FloatingDock(
    selected: DockTab,
    onSelect: (DockTab) -> Unit,
    enabled: Boolean,
    modifier: Modifier = Modifier,
) {
    Surface(
        modifier = modifier,
        shape = RoundedCornerShape(DockDefaults.CornerRadius),
        color = MaterialTheme.colorScheme.surfaceContainerHigh,
        tonalElevation = 3.dp,
        shadowElevation = 8.dp,
    ) {
        Row(
            modifier = Modifier.padding(DockDefaults.SurfacePadding),
            horizontalArrangement = Arrangement.spacedBy(DockDefaults.ItemSpacing),
        ) {
            DockTab.entries.forEach { tab ->
                DockItem(
                    tab = tab,
                    selected = selected == tab,
                    enabled = enabled,
                    onClick = { onSelect(tab) },
                )
            }
        }
    }
}

@Composable
private fun DockItem(
    tab: DockTab,
    selected: Boolean,
    enabled: Boolean,
    onClick: () -> Unit,
) {
    val containerColor = if (selected) {
        MaterialTheme.colorScheme.primaryContainer
    } else {
        Color.Transparent
    }
    val contentColor = if (selected) {
        MaterialTheme.colorScheme.onPrimaryContainer
    } else {
        MaterialTheme.colorScheme.onSurfaceVariant
    }

    Row(
        modifier = Modifier
            .clip(RoundedCornerShape(DockDefaults.ItemCornerRadius))
            .background(containerColor)
            .clickable(enabled = enabled, onClick = onClick)
            .padding(
                horizontal = DockDefaults.ItemHorizontalPadding,
                vertical = DockDefaults.ItemVerticalPadding,
            ),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Icon(
            imageVector = tab.icon,
            contentDescription = null,
            tint = contentColor,
            modifier = Modifier.size(DockDefaults.IconSize),
        )
        Spacer(Modifier.width(DockDefaults.IconTextSpacing))
        Text(
            text = tab.label,
            color = contentColor,
            style = MaterialTheme.typography.labelLarge,
        )
    }
}
