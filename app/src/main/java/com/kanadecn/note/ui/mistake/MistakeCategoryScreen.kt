package com.kanadecn.note.ui.mistake

import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.PickVisualMediaRequest
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.MoreVert
import androidx.compose.material.icons.filled.Warning
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import coil.compose.AsyncImage
import com.kanadecn.note.data.local.Mistake
import com.kanadecn.note.data.mistakes.CategoryStore
import com.kanadecn.note.data.mistakes.MistakeRecognizer
import com.kanadecn.note.data.mistakes.MistakeRepository
import com.kanadecn.note.data.mistakes.RecognizeOutcome
import com.kanadecn.note.data.remote.RecognizedMistake
import com.kanadecn.note.ui.components.failureMessage
import com.kanadecn.note.ui.theme.appColors
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.io.File
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun MistakeCategoryScreen(
    category: String,
    mistakeRepository: MistakeRepository,
    categoryStore: CategoryStore,
    mistakeRecognizer: MistakeRecognizer,
    onOpenDetail: (Mistake) -> Unit,
    onBack: () -> Unit,
) {
    val scope = rememberCoroutineScope()

    var mistakes by remember { mutableStateOf<List<Mistake>>(emptyList()) }
    var categories by remember { mutableStateOf(categoryStore.allCategories()) }
    var isLoading by remember { mutableStateOf(true) }
    var pendingDelete by remember { mutableStateOf<Mistake?>(null) }

    var draft by remember { mutableStateOf<RecognizedMistake?>(null) }
    var draftImagePath by remember { mutableStateOf<String?>(null) }
    var editingMistake by remember { mutableStateOf<Mistake?>(null) }
    var isRecognizing by remember { mutableStateOf(false) }
    var errorMessage by remember { mutableStateOf<String?>(null) }
    var errorDetail by remember { mutableStateOf<String?>(null) }

    LaunchedEffect(category) {
        categories = categoryStore.allCategories()
        mistakes = runCatching { mistakeRepository.getByCategory(category) }
            .getOrDefault(emptyList())
        isLoading = false
    }

    val imagePicker = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.PickVisualMedia(),
    ) { uri ->
        if (uri == null) return@rememberLauncherForActivityResult

        scope.launch {
            isRecognizing = true
            errorMessage = null
            errorDetail = null
            try {
                when (val outcome = mistakeRecognizer.recognize(uri)) {
                    is RecognizeOutcome.Success -> {
                        draft = outcome.draft.copy(category = category)
                        draftImagePath = outcome.imagePath
                        editingMistake = null
                    }

                    is RecognizeOutcome.Failure -> {
                        errorMessage = failureMessage(outcome.reason)
                        errorDetail = outcome.detail
                    }

                    RecognizeOutcome.NotConfigured -> {
                        errorMessage = "请先在设置里完成 AI 配置"
                        errorDetail = "需要 Base URL、API Key 和模型"
                    }
                }
            } catch (e: Exception) {
                errorMessage = "识别失败：${e.message ?: "未知错误"}"
            } finally {
                isRecognizing = false
            }
        }
    }

    Scaffold(
        contentWindowInsets = WindowInsets(0, 0, 0, 0),
        topBar = {
            TopAppBar(
                title = { Text(category) },
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
        val currentDraft = draft
        val currentImagePath = draftImagePath

        if (currentDraft != null && currentImagePath != null) {
            MistakeEditor(
                initial = currentDraft,
                imagePath = currentImagePath,
                categories = categories,
                onAddCategory = { name ->
                    val updated = categoryStore.add(name)
                    categories = updated
                    updated
                },
                onCancel = {
                    draft = null
                    draftImagePath = null
                    editingMistake = null
                },
                onSave = { edited, path ->
                    scope.launch {
                        val existing = editingMistake
                        if (existing != null) {
                            mistakeRepository.update(
                                existing.copy(
                                    question = edited.question,
                                    myAnswer = edited.myAnswer,
                                    correctAnswer = edited.correctAnswer,
                                    isCorrect = edited.isCorrect,
                                    errorType = edited.errorType,
                                    knowledgePoint = edited.knowledgePoint,
                                    explanation = edited.explanation,
                                    category = edited.category,
                                ),
                            )
                        } else {
                            mistakeRepository.add(
                                Mistake(
                                    imagePath = path,
                                    question = edited.question,
                                    myAnswer = edited.myAnswer,
                                    correctAnswer = edited.correctAnswer,
                                    isCorrect = edited.isCorrect,
                                    errorType = edited.errorType,
                                    knowledgePoint = edited.knowledgePoint,
                                    explanation = edited.explanation,
                                    createdAt = System.currentTimeMillis(),
                                    category = edited.category,
                                ),
                            )
                        }
                        mistakes = mistakeRepository.getByCategory(category)
                        draft = null
                        draftImagePath = null
                        editingMistake = null
                    }
                },
                modifier = Modifier.padding(innerPadding),
                bottomContentPadding = 0.dp,
            )
        } else {
            LazyColumn(
                modifier = Modifier
                    .fillMaxSize()
                    .padding(innerPadding),
                contentPadding = PaddingValues(16.dp),
                verticalArrangement = Arrangement.spacedBy(8.dp),
            ) {
                item {
                    Button(
                        onClick = {
                            imagePicker.launch(
                                PickVisualMediaRequest(
                                    ActivityResultContracts.PickVisualMedia.ImageOnly,
                                ),
                            )
                        },
                        enabled = !isRecognizing,
                        modifier = Modifier.fillMaxWidth(),
                    ) {
                        if (isRecognizing) {
                            CircularProgressIndicator(
                                modifier = Modifier.size(18.dp),
                                strokeWidth = 2.dp,
                            )
                            Spacer(Modifier.width(8.dp))
                        }
                        Text(if (isRecognizing) "识别中…" else "添加错题")
                    }
                }

                errorMessage?.let { message ->
                    item {
                        Text(
                            text = message,
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.error,
                        )
                    }
                }
                errorDetail?.let { detail ->
                    item {
                        Text(
                            text = detail,
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                        )
                    }
                }

                when {
                    isLoading -> item {
                        Box(
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(32.dp),
                            contentAlignment = Alignment.Center,
                        ) {
                            CircularProgressIndicator()
                        }
                    }

                    mistakes.isEmpty() -> item {
                        Column(
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(vertical = 32.dp),
                            horizontalAlignment = Alignment.CenterHorizontally,
                        ) {
                            Icon(
                                imageVector = Icons.Filled.Warning,
                                contentDescription = null,
                                tint = MaterialTheme.colorScheme.primary,
                                modifier = Modifier.size(40.dp),
                            )
                            Spacer(Modifier.height(8.dp))
                            Text(
                                text = "这个分类还没有错题",
                                style = MaterialTheme.typography.bodyMedium,
                                color = MaterialTheme.colorScheme.onSurfaceVariant,
                            )
                        }
                    }

                    else -> items(mistakes, key = { it.id }) { mistake ->
                        MistakeRow(
                            mistake = mistake,
                            onOpenDetail = { onOpenDetail(mistake) },
                            onEdit = {
                                editingMistake = mistake
                                draftImagePath = mistake.imagePath
                                draft = mistake.toRecognized()
                            },
                            onDelete = { pendingDelete = mistake },
                        )
                    }
                }
            }
        }
    }

    pendingDelete?.let { mistake ->
        AlertDialog(
            onDismissRequest = { pendingDelete = null },
            title = { Text("删除错题") },
            text = { Text("确定删除这道错题吗？") },
            confirmButton = {
                TextButton(
                    onClick = {
                        scope.launch {
                            withContext(Dispatchers.IO) {
                                runCatching { File(mistake.imagePath).delete() }
                            }
                            mistakeRepository.delete(mistake)
                            mistakes = mistakeRepository.getByCategory(category)
                            pendingDelete = null
                        }
                    },
                ) {
                    Text("删除")
                }
            },
            dismissButton = {
                TextButton(onClick = { pendingDelete = null }) {
                    Text("取消")
                }
            },
        )
    }
}

@Composable
private fun MistakeRow(
    mistake: Mistake,
    onOpenDetail: () -> Unit,
    onEdit: () -> Unit,
    onDelete: () -> Unit,
) {
    var menuExpanded by remember { mutableStateOf(false) }

    Card(modifier = Modifier.fillMaxWidth()) {
        Row(
            modifier = Modifier.padding(12.dp),
            horizontalArrangement = Arrangement.spacedBy(12.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Row(
                modifier = Modifier
                    .weight(1f)
                    .clickable(onClick = onOpenDetail),
                horizontalArrangement = Arrangement.spacedBy(12.dp),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                AsyncImage(
                    model = File(mistake.imagePath),
                    contentDescription = null,
                    contentScale = ContentScale.Crop,
                    modifier = Modifier
                        .size(56.dp)
                        .clip(MaterialTheme.shapes.small),
                )

                Column(
                    modifier = Modifier.weight(1f),
                    verticalArrangement = Arrangement.spacedBy(4.dp),
                ) {
                    Text(
                        text = mistake.question,
                        style = MaterialTheme.typography.bodyLarge,
                        maxLines = 2,
                        overflow = TextOverflow.Ellipsis,
                    )
                    Text(
                        text = if (mistake.isCorrect) "判定：正确" else "错因：${mistake.errorType}",
                        style = MaterialTheme.typography.bodySmall,
                        color = if (mistake.isCorrect) {
                            MaterialTheme.appColors.success
                        } else {
                            MaterialTheme.colorScheme.error
                        },
                    )
                    if (mistake.knowledgePoint.isNotBlank()) {
                        Text(
                            text = "知识点：${mistake.knowledgePoint}",
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis,
                        )
                    }
                    Text(
                        text = formatTime(mistake.createdAt),
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }
            }

            Box {
                IconButton(onClick = { menuExpanded = true }) {
                    Icon(
                        imageVector = Icons.Filled.MoreVert,
                        contentDescription = "更多",
                    )
                }
                DropdownMenu(
                    expanded = menuExpanded,
                    onDismissRequest = { menuExpanded = false },
                ) {
                    DropdownMenuItem(
                        text = { Text("编辑") },
                        onClick = {
                            menuExpanded = false
                            onEdit()
                        },
                    )
                    DropdownMenuItem(
                        text = { Text("删除") },
                        onClick = {
                            menuExpanded = false
                            onDelete()
                        },
                    )
                }
            }
        }
    }
}

private fun formatTime(millis: Long): String =
    SimpleDateFormat("yy-MM-dd HH:mm", Locale.getDefault()).format(Date(millis))
