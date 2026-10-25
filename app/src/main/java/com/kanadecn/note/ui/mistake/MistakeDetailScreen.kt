package com.kanadecn.note.ui.mistake

import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.unit.dp
import coil.compose.AsyncImage
import com.kanadecn.note.data.local.Mistake
import com.kanadecn.note.data.mistakes.MistakeRepository
import com.kanadecn.note.ui.theme.appColors
import java.io.File
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun MistakeDetailScreen(
    mistakeId: Long,
    mistakeRepository: MistakeRepository,
    onBack: () -> Unit,
) {
    var mistake by remember { mutableStateOf<Mistake?>(null) }
    var loading by remember { mutableStateOf(true) }

    LaunchedEffect(mistakeId) {
        mistake = runCatching { mistakeRepository.getById(mistakeId) }.getOrNull()
        loading = false
    }

    Scaffold(
        contentWindowInsets = WindowInsets(0, 0, 0, 0),
        topBar = {
            TopAppBar(
                title = { Text("错题详情") },
                navigationIcon = {
                    IconButton(onClick = onBack) {
                        Icon(
                            imageVector = Icons.AutoMirrored.Filled.ArrowBack,
                            contentDescription = "返回",
                        )
                    }
                },
            )
        },
    ) { innerPadding ->
        when {
            loading -> Box(
                modifier = Modifier
                    .fillMaxSize()
                    .padding(innerPadding),
                contentAlignment = Alignment.Center,
            ) {
                CircularProgressIndicator()
            }

            mistake == null -> Box(
                modifier = Modifier
                    .fillMaxSize()
                    .padding(innerPadding),
                contentAlignment = Alignment.Center,
            ) {
                Text(
                    text = "错题不存在",
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }

            else -> {
                val item = mistake
                if (item != null) {
                    Column(
                        modifier = Modifier
                            .fillMaxSize()
                            .padding(innerPadding)
                            .verticalScroll(rememberScrollState())
                            .padding(16.dp),
                        verticalArrangement = Arrangement.spacedBy(16.dp),
                    ) {
                        AsyncImage(
                            model = File(item.imagePath),
                            contentDescription = null,
                            contentScale = ContentScale.Fit,
                            modifier = Modifier
                                .fillMaxWidth()
                                .heightIn(max = 320.dp)
                                .clip(MaterialTheme.shapes.medium),
                        )

                        Text(
                            text = item.question,
                            style = MaterialTheme.typography.titleLarge,
                        )

                        Text(
                            text = if (item.isCorrect) {
                                "判定：正确"
                            } else {
                                "错因：${item.errorType}"
                            },
                            style = MaterialTheme.typography.bodyMedium,
                            color = if (item.isCorrect) {
                                MaterialTheme.appColors.success
                            } else {
                                MaterialTheme.colorScheme.error
                            },
                        )

                        DetailSection("我的答案", item.myAnswer)
                        DetailSection("正确答案 / 正确解法", item.correctAnswer)
                        if (item.knowledgePoint.isNotBlank()) {
                            DetailSection("知识点", item.knowledgePoint)
                        }
                        DetailSection("讲解", item.explanation)

                        Text(
                            text = formatTime(item.createdAt),
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                        )
                    }
                }
            }
        }
    }
}

@Composable
private fun DetailSection(title: String, content: String) {
    if (content.isBlank()) return

    Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
        Text(
            text = title,
            style = MaterialTheme.typography.labelLarge,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
        Text(
            text = content,
            style = MaterialTheme.typography.bodyLarge,
        )
    }
}

private fun formatTime(millis: Long): String =
    SimpleDateFormat("MM-dd HH:mm", Locale.getDefault()).format(Date(millis))
