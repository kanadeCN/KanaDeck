package com.kanadecn.note.ui.components

import androidx.compose.foundation.layout.Column
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable

/**
 * 统一的顶栏标题：KanaDeck + 小字副标题。
 */
@Composable
fun KanaDeckTitle(subtitle: String) {
    Column {
        Text(
            text = "KanaDeck",
            style = MaterialTheme.typography.titleMedium,
        )
        Text(
            text = subtitle,
            style = MaterialTheme.typography.labelSmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
    }
}
